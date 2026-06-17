package com.example.DumbleWallet.service;

import com.example.DumbleWallet.client.PaymentServiceClient;
import com.example.DumbleWallet.domain.Wallet;
import com.example.DumbleWallet.domain.WalletEntry;
import com.example.DumbleWallet.domain.enums.EntrySource;
import com.example.DumbleWallet.domain.enums.EntryType;
import com.example.DumbleWallet.domain.WithdrawalRequest;
import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import com.example.DumbleWallet.dto.AdminAdjustmentRequest;
import com.example.DumbleWallet.dto.WalletWriteResponse;
import com.example.DumbleWallet.dto.WithdrawalResponse;
import com.example.DumbleWallet.event.OutboxWriter;
import com.example.DumbleWallet.exception.BusinessRuleViolationException;
import com.example.DumbleWallet.exception.InsufficientBalanceException;
import com.example.DumbleWallet.exception.ResourceNotFoundException;
import com.example.DumbleWallet.repository.WalletRepository;
import com.example.DumbleWallet.repository.WithdrawalRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet PDF Decisions 5.1 + 5.3 — admin manual adjustment with mandatory
 * memo. Distinct path from {@code POST /wallet/credit} so the audit log
 * captures the actor as ADMIN. Memo is required (validated at the DTO layer).
 */
@Service
public class AdminWalletService {

    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final WithdrawalRequestRepository withdrawalRepository;
    private final WithdrawalPersister withdrawalPersister;
    private final PaymentServiceClient paymentServiceClient;
    private final String currency;

    public AdminWalletService(WalletRepository walletRepository,
                              WalletService walletService,
                              OutboxWriter outboxWriter,
                              AuditLogger auditLogger,
                              WithdrawalRequestRepository withdrawalRepository,
                              WithdrawalPersister withdrawalPersister,
                              PaymentServiceClient paymentServiceClient,
                              @Value("${wallet.currency:EGP}") String currency) {
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.withdrawalRepository = withdrawalRepository;
        this.withdrawalPersister = withdrawalPersister;
        this.paymentServiceClient = paymentServiceClient;
        this.currency = currency;
    }

    /** Admin forensic read: withdrawals filtered by status and/or user. */
    @Transactional(readOnly = true)
    public List<WithdrawalResponse> listWithdrawals(WithdrawalStatus status, UUID userId) {
        List<WithdrawalRequest> rows;
        if (userId != null) {
            rows = withdrawalRepository.findByWalletUserIdOrderByCreatedAtDesc(userId);
            if (status != null) {
                rows = rows.stream().filter(w -> w.getStatus() == status).toList();
            }
        } else if (status != null) {
            rows = withdrawalRepository.findByStatus(status);
        } else {
            rows = withdrawalRepository.findAll();
        }
        return rows.stream().map(WithdrawalResponse::from).toList();
    }

    /**
     * W1 — admin force-cancel of a withdrawal, including ones already handed to
     * Payment (SUBMITTING/SENT). Deliberately NOT {@code @Transactional}: the
     * Payment HTTP round-trip must sit outside any JPA tx, and the wallet
     * reversal is only applied AFTER Payment acks the cancel, so we never
     * credit the user for a payout that actually went out.
     */
    public WithdrawalResponse cancelWithdrawal(UUID withdrawalId, UUID adminId) {
        WithdrawalRequest w = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found"));
        WithdrawalStatus status = w.getStatus();
        switch (status) {
            case PENDING -> {
                // Not yet handed to Payment — reverse locally and mark CANCELLED.
                return WithdrawalResponse.from(withdrawalPersister.cancel(w.getWalletUserId(), withdrawalId));
            }
            case SUBMITTING, SENT -> {
                String paymentRef = w.getPaymentRef();
                if (paymentRef == null || paymentRef.isBlank()) {
                    // Mid-submission with no provider reference yet — we can't tell
                    // Payment which payout to abort, so reversing could double-pay.
                    // Leave it for the reaper / a retry once it settles.
                    throw new BusinessRuleViolationException(
                            "Withdrawal is mid-submission without a payment reference; cannot safely cancel yet");
                }
                // Ask Payment to abort the Paymob payout. If Payment refuses
                // (e.g. already paid out), this throws and we do NOT reverse.
                paymentServiceClient.cancelWithdrawal(paymentRef);
                // Payment acked the cancel — reverse the wallet movement + mark terminal.
                WithdrawalRequest reversed = withdrawalPersister.reverseAndFail(withdrawalId, "admin_cancelled");
                auditLogger.log(w.getWalletUserId(), "WithdrawalAdminCancelled", "ADMIN",
                        adminId == null ? "" : adminId.toString(), "admin_force_cancel",
                        Map.of("withdrawalId", withdrawalId, "paymentRef", paymentRef));
                return WithdrawalResponse.from(reversed);
            }
            default -> throw new BusinessRuleViolationException(
                    "Cannot cancel a withdrawal in status " + status);
        }
    }

    @Transactional
    public WalletWriteResponse adjust(UUID userId, UUID adminId, AdminAdjustmentRequest req) {
        EntryType direction = parseDirection(req.getDirection());
        long amount = req.getAmountCents();
        if (amount <= 0) {
            throw new BusinessRuleViolationException("amountCents must be positive");
        }
        Instant now = Instant.now();

        Wallet wallet = walletRepository.findByIdForUpdate(userId)
                .orElseGet(() -> {
                    if (direction == EntryType.DEBIT) {
                        throw new ResourceNotFoundException("Wallet not found");
                    }
                    Wallet fresh = new Wallet();
                    fresh.setUserId(userId);
                    fresh.setCurrency(currency);
                    fresh.setAvailableCents(0);
                    fresh.setPendingCents(0);
                    fresh.setCreatedAt(now);
                    fresh.setUpdatedAt(now);
                    return walletRepository.save(fresh);
                });

        if (direction == EntryType.DEBIT && wallet.getAvailableCents() < amount) {
            throw new InsufficientBalanceException(
                    "available=" + wallet.getAvailableCents() + " requested=" + amount);
        }

        WalletEntry entry = walletService.appendEntry(wallet, direction, amount,
                EntrySource.ADMIN_ADJUSTMENT, "admin:" + adminId, req.getMemo(), now);

        if (direction == EntryType.CREDIT) {
            wallet.setAvailableCents(wallet.getAvailableCents() + amount);
        } else {
            wallet.setAvailableCents(wallet.getAvailableCents() - amount);
        }
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        auditLogger.log(userId, "AdminAdjustment", "ADMIN", adminId == null ? "" : adminId.toString(),
                req.getMemo(),
                Map.of("direction", direction.name(),
                       "amountCents", amount,
                       "walletEntryId", entry.getId()));

        // Decision 6.3 — surface the same WalletCredited / WalletDebited
        // events so NotificationService can pick them up.
        if (direction == EntryType.CREDIT) {
            outboxWriter.write("WalletCredited", "wallet.credited",
                    Map.of("walletEntryId", entry.getId(),
                           "userId", userId,
                           "amountCents", amount,
                           "source", EntrySource.ADMIN_ADJUSTMENT.name(),
                           "memo", req.getMemo(),
                           "newBalanceCents", wallet.getAvailableCents()));
        } else {
            outboxWriter.write("WalletDebited", "wallet.debited",
                    Map.of("walletEntryId", entry.getId(),
                           "userId", userId,
                           "amountCents", amount,
                           "source", EntrySource.ADMIN_ADJUSTMENT.name(),
                           "memo", req.getMemo(),
                           "newBalanceCents", wallet.getAvailableCents()));
        }

        return WalletWriteResponse.builder()
                .walletEntryId(entry.getId())
                .newBalanceCents(wallet.getAvailableCents())
                .pendingCents(wallet.getPendingCents())
                .currency(wallet.getCurrency())
                .build();
    }

    private static EntryType parseDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleViolationException("direction is required (CREDIT | DEBIT)");
        }
        try {
            return EntryType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("direction must be CREDIT or DEBIT");
        }
    }
}
