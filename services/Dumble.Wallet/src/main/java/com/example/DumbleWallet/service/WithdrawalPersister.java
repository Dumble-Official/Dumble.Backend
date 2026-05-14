package com.example.DumbleWallet.service;

import com.example.DumbleWallet.domain.Wallet;
import com.example.DumbleWallet.domain.WalletEntry;
import com.example.DumbleWallet.domain.WithdrawalRequest;
import com.example.DumbleWallet.domain.enums.EntrySource;
import com.example.DumbleWallet.domain.enums.EntryType;
import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import com.example.DumbleWallet.event.OutboxWriter;
import com.example.DumbleWallet.exception.InsufficientBalanceException;
import com.example.DumbleWallet.exception.ResourceNotFoundException;
import com.example.DumbleWallet.repository.WalletRepository;
import com.example.DumbleWallet.repository.WithdrawalRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DB-only transactions for the withdrawal flow. Sibling bean so Spring's AOP
 * proxy intercepts the {@code @Transactional} boundaries — self-invocation
 * inside a single class is bypassed by the proxy.
 *
 * Lets {@link WithdrawalService} keep its blocking HTTP call to Payment
 * outside any JPA tx (the connection isn't pinned for the WebClient round-
 * trip budget).
 */
@Component
public class WithdrawalPersister {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalPersister.class);

    private final WalletRepository walletRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletService walletService;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final String currency;

    public WithdrawalPersister(WalletRepository walletRepository,
                               WithdrawalRequestRepository withdrawalRequestRepository,
                               WalletService walletService,
                               OutboxWriter outboxWriter,
                               AuditLogger auditLogger,
                               @Value("${wallet.currency:EGP}") String currency) {
        this.walletRepository = walletRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.walletService = walletService;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.currency = currency;
    }

    @Transactional
    public WithdrawalRequest claimBalanceAndPersist(UUID userId, long amountCents, String destinationJson) {
        Wallet wallet = walletRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InsufficientBalanceException("Wallet not found"));

        if (wallet.getAvailableCents() < amountCents) {
            throw new InsufficientBalanceException(
                    "available=" + wallet.getAvailableCents() + " requested=" + amountCents);
        }
        Instant now = Instant.now();

        WithdrawalRequest withdrawal = new WithdrawalRequest();
        withdrawal.setWalletUserId(userId);
        withdrawal.setAmountCents(amountCents);
        withdrawal.setCurrency(wallet.getCurrency() == null ? currency : wallet.getCurrency());
        withdrawal.setDestinationJson(destinationJson);
        withdrawal.setStatus(WithdrawalStatus.PENDING);
        withdrawal.setCreatedAt(now);
        withdrawal.setUpdatedAt(now);
        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);

        WalletEntry entry = walletService.appendEntry(wallet, EntryType.DEBIT, amountCents,
                EntrySource.WITHDRAWAL_REQUESTED, saved.getId().toString(),
                "Withdrawal requested", now);

        wallet.setAvailableCents(wallet.getAvailableCents() - amountCents);
        wallet.setPendingCents(wallet.getPendingCents() + amountCents);
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        auditLogger.log(userId, "WithdrawalRequested", "USER", userId.toString(),
                "user_initiated_withdrawal",
                Map.of("withdrawalId", saved.getId(),
                       "amountCents", amountCents,
                       "walletEntryId", entry.getId()));

        outboxWriter.write("WithdrawalRequested", "wallet.withdrawal.requested",
                Map.of("withdrawalId", saved.getId(),
                       "userId", userId,
                       "amountCents", amountCents,
                       "currency", saved.getCurrency()));

        return saved;
    }

    /**
     * No-op state mutation — bumps {@code updatedAt} so the reaper's
     * grace-window query (`findStuckBefore`) drops the row for another tick.
     * Used on Pending / unknown-status / already-correct branches to stop
     * the reaper from polling Payment for the same row every minute.
     *
     * Implemented as a direct UPDATE that bypasses {@code @Version} — the
     * reaper races concurrent {@code markSent} / webhook handlers on the
     * same row, and an optimistic-lock conflict on a logically-no-op write
     * would just be noise. Since {@code touch} doesn't change logical
     * state, skipping the version check doesn't relax any real invariant.
     */
    @Transactional
    public void touch(UUID withdrawalId) {
        withdrawalRequestRepository.bumpUpdatedAt(withdrawalId, Instant.now());
    }

    /**
     * Closes the cancel race by flipping PENDING → SUBMITTING in its own
     * tx BEFORE the Payment HTTP call. Once SUBMITTING, the cancel endpoint
     * refuses and only the Payment lifecycle event can advance state. Returns
     * false if the withdrawal isn't in PENDING (e.g. already cancelled).
     *
     * Uses a pessimistic row lock so a concurrent cancel serialises behind
     * us instead of racing into an optimistic @Version conflict.
     */
    @Transactional
    public boolean tryMarkSubmitting(UUID withdrawalId) {
        WithdrawalRequest w = withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found"));
        if (w.getStatus() != WithdrawalStatus.PENDING) {
            return false;
        }
        w.setStatus(WithdrawalStatus.SUBMITTING);
        w.setUpdatedAt(Instant.now());
        withdrawalRequestRepository.save(w);
        return true;
    }

    @Transactional
    public WithdrawalRequest markSent(UUID withdrawalId, String paymentRef) {
        // Pessimistic lock — when the reaper running on two replicas both
        // hit the same stuck SUBMITTING row, both call markSent. Without
        // the lock, the optimistic @Version blocks the second update but
        // the duplicate Payment lookup HTTP call already happened.
        WithdrawalRequest w = withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found"));
        // Accept either PENDING (no markSubmitting yet — defensive) or
        // SUBMITTING (the normal path). Anything else is already terminal.
        if (w.getStatus() == WithdrawalStatus.PENDING || w.getStatus() == WithdrawalStatus.SUBMITTING) {
            w.setStatus(WithdrawalStatus.SENT);
            w.setPaymentRef(paymentRef);
            w.setUpdatedAt(Instant.now());
            withdrawalRequestRepository.save(w);
            auditLogger.log(w.getWalletUserId(), "WithdrawalSent", "SYSTEM", "payment-ack",
                    null, Map.of("withdrawalId", w.getId(), "paymentRef", paymentRef));
        }
        return w;
    }

    /**
     * Reverses the wallet movement and flips the withdrawal to FAILED. Used
     * both as the Phase-2 fallback (when our HTTP call to Payment failed) and
     * by the {@code WithdrawalFailed} listener (Decision 6.2).
     */
    @Transactional
    public WithdrawalRequest reverseAndFail(UUID withdrawalId, String reason) {
        // Pessimistic lock for the same reason as markSent — the reaper can
        // call this concurrently across replicas. Locking the withdrawal row
        // first prevents two parallel reverseAndFail calls from both crediting
        // the wallet, which would otherwise leak the locked wallet behind the
        // optimistic-version check.
        WithdrawalRequest w = withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found"));
        if (w.getStatus() == WithdrawalStatus.FAILED || w.getStatus() == WithdrawalStatus.CANCELLED) {
            return w;
        }
        if (w.getStatus() == WithdrawalStatus.COMPLETED) {
            log.warn("Refusing to reverse already-completed withdrawal {}", w.getId());
            return w;
        }
        Instant now = Instant.now();

        Wallet wallet = walletRepository.findByIdForUpdate(w.getWalletUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        walletService.appendEntry(wallet, EntryType.CREDIT, w.getAmountCents(),
                EntrySource.WITHDRAWAL_REVERSED, w.getId().toString(),
                "Withdrawal failed: " + reason, now);
        wallet.setAvailableCents(wallet.getAvailableCents() + w.getAmountCents());
        wallet.setPendingCents(Math.max(0, wallet.getPendingCents() - w.getAmountCents()));
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        w.setStatus(WithdrawalStatus.FAILED);
        w.setFailureReason(reason);
        w.setCompletedAt(now);
        w.setUpdatedAt(now);
        withdrawalRequestRepository.save(w);

        auditLogger.log(w.getWalletUserId(), "WithdrawalFailed", "WEBHOOK", null, reason,
                Map.of("withdrawalId", w.getId(), "amountCents", w.getAmountCents()));

        outboxWriter.write("WithdrawalFailed", "wallet.withdrawal.failed",
                Map.of("withdrawalId", w.getId(),
                       "userId", w.getWalletUserId(),
                       "amountCents", w.getAmountCents(),
                       "reason", reason));
        return w;
    }

    /** Decision 6.2 — Payment confirmed Paymob delivered the funds. */
    @Transactional
    public void completeFromWebhook(UUID withdrawalId, String paymentRef, WithdrawalRequest existing) {
        WithdrawalRequest w = existing;
        if (w.getStatus() == WithdrawalStatus.COMPLETED) {
            return;
        }
        Instant now = Instant.now();
        Wallet wallet = walletRepository.findByIdForUpdate(w.getWalletUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        // Decision 4.2 — Pending decremented; the WITHDRAWAL_REQUESTED debit
        // logged at request time is now permanent (no additional ledger entry).
        wallet.setPendingCents(Math.max(0, wallet.getPendingCents() - w.getAmountCents()));
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        w.setStatus(WithdrawalStatus.COMPLETED);
        if (paymentRef != null && !paymentRef.isBlank()) {
            w.setPaymentRef(paymentRef);
        }
        w.setCompletedAt(now);
        w.setUpdatedAt(now);
        withdrawalRequestRepository.save(w);

        auditLogger.log(w.getWalletUserId(), "WithdrawalCompleted", "WEBHOOK",
                paymentRef == null ? "" : paymentRef, null,
                Map.of("withdrawalId", w.getId(), "amountCents", w.getAmountCents()));

        outboxWriter.write("WithdrawalCompleted", "wallet.withdrawal.completed",
                Map.of("withdrawalId", w.getId(),
                       "userId", w.getWalletUserId(),
                       "amountCents", w.getAmountCents()));
    }

    @Transactional
    public WithdrawalRequest cancel(UUID userId, UUID withdrawalId) {
        // Pessimistic lock so we either win the race against tryMarkSubmitting
        // (and see PENDING here) or lose it (and see SUBMITTING and reject).
        WithdrawalRequest w = withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found"));
        if (!w.getWalletUserId().equals(userId)) {
            throw new ResourceNotFoundException("Withdrawal not found");
        }
        if (w.getStatus() != WithdrawalStatus.PENDING) {
            throw new com.example.DumbleWallet.exception.BusinessRuleViolationException(
                    "Cannot cancel a withdrawal in status " + w.getStatus());
        }
        Instant now = Instant.now();

        Wallet wallet = walletRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        walletService.appendEntry(wallet, EntryType.CREDIT, w.getAmountCents(),
                EntrySource.WITHDRAWAL_REVERSED, w.getId().toString(),
                "Withdrawal cancelled by user", now);
        wallet.setAvailableCents(wallet.getAvailableCents() + w.getAmountCents());
        wallet.setPendingCents(Math.max(0, wallet.getPendingCents() - w.getAmountCents()));
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        w.setStatus(WithdrawalStatus.CANCELLED);
        w.setCompletedAt(now);
        w.setUpdatedAt(now);
        withdrawalRequestRepository.save(w);

        auditLogger.log(userId, "WithdrawalCancelled", "USER", userId.toString(),
                "user_initiated_cancel", Map.of("withdrawalId", w.getId()));
        return w;
    }
}
