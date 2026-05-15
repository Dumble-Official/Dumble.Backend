package com.example.DumbleWallet.service;

import com.example.DumbleWallet.domain.Wallet;
import com.example.DumbleWallet.domain.WalletEntry;
import com.example.DumbleWallet.domain.enums.EntrySource;
import com.example.DumbleWallet.domain.enums.EntryType;
import com.example.DumbleWallet.dto.WalletCreditRequest;
import com.example.DumbleWallet.dto.WalletDebitRequest;
import com.example.DumbleWallet.dto.WalletEntryResponse;
import com.example.DumbleWallet.dto.WalletSummaryResponse;
import com.example.DumbleWallet.dto.WalletWriteResponse;
import com.example.DumbleWallet.event.OutboxWriter;
import com.example.DumbleWallet.exception.BusinessRuleViolationException;
import com.example.DumbleWallet.exception.InsufficientBalanceException;
import com.example.DumbleWallet.exception.ResourceNotFoundException;
import com.example.DumbleWallet.repository.WalletEntryRepository;
import com.example.DumbleWallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core ledger operations — credit, debit, summary.
 *
 * Per Wallet PDF Decision 2.3, every {@link WalletEntry} insert atomically
 * updates the cached {@code Wallet.availableCents} in the same transaction.
 * A daily reconciliation job (Decision 5.2) verifies cache vs ledger sum.
 *
 * Credits never fail (Decision 3.3) — if the user has no wallet yet, we
 * auto-create one and credit it. Debits fail with 400 InsufficientBalance
 * when the wallet doesn't cover the amount (Decision 4.1).
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    /** Credit sources accepted on POST /wallet/credit (Decisions 1.4, 2.2). */
    private static final java.util.Set<EntrySource> CREDIT_SOURCES = java.util.Set.of(
            EntrySource.BAN_REFUND,
            EntrySource.CHARGEBACK,
            EntrySource.ADMIN_ADJUSTMENT,
            EntrySource.WITHDRAWAL_REVERSED
    );

    private final WalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final String currency;

    public WalletService(WalletRepository walletRepository,
                         WalletEntryRepository walletEntryRepository,
                         OutboxWriter outboxWriter,
                         AuditLogger auditLogger,
                         @Value("${wallet.currency:EGP}") String currency) {
        this.walletRepository = walletRepository;
        this.walletEntryRepository = walletEntryRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.currency = currency;
    }

    /**
     * Wallet PDF Decision 3.1, 3.3, 3.4 — credit a user's wallet. Never fails
     * for "valid" requests. Idempotency is enforced by the controller via
     * {@link IdempotencyService}, so this method assumes the caller's key
     * has already gated duplicate POSTs.
     */
    @Transactional
    public WalletWriteResponse credit(WalletCreditRequest req) {
        EntrySource source = parseSource(req.getSource(), CREDIT_SOURCES, "credit");
        Instant now = Instant.now();

        Wallet wallet = walletRepository.findByIdForUpdate(req.getUserId())
                .orElseGet(() -> createWallet(req.getUserId(), now));

        WalletEntry entry = appendEntry(wallet, EntryType.CREDIT, req.getAmountCents(),
                source, req.getExternalRef(), req.getMemo(), now);

        wallet.setAvailableCents(wallet.getAvailableCents() + req.getAmountCents());
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        auditLogger.log(wallet.getUserId(), "WalletCredited", "SYSTEM",
                source.name(), req.getMemo(),
                Map.of("amountCents", req.getAmountCents(),
                       "externalRef", req.getExternalRef() == null ? "" : req.getExternalRef()));

        outboxWriter.write("WalletCredited", "wallet.credited",
                Map.of("walletEntryId", entry.getId(),
                       "userId", wallet.getUserId(),
                       "amountCents", req.getAmountCents(),
                       "source", source.name(),
                       "externalRef", req.getExternalRef() == null ? "" : req.getExternalRef(),
                       "newBalanceCents", wallet.getAvailableCents()));

        return toResponse(entry, wallet);
    }

    /**
     * Wallet PDF Decision 4.1 — in-app spend. Throws
     * {@link InsufficientBalanceException} when the wallet doesn't cover
     * the amount; Subscription falls back to Payment in that case.
     */
    @Transactional
    public WalletWriteResponse debit(WalletDebitRequest req) {
        EntrySource source = parseSource(req.getSource(), java.util.Set.of(EntrySource.IN_APP_SPEND), "debit");
        Instant now = Instant.now();

        Wallet wallet = walletRepository.findByIdForUpdate(req.getUserId())
                .orElseThrow(() -> new InsufficientBalanceException("Wallet not found"));

        if (wallet.getAvailableCents() < req.getAmountCents()) {
            throw new InsufficientBalanceException(
                    "available=" + wallet.getAvailableCents() + " needed=" + req.getAmountCents());
        }

        WalletEntry entry = appendEntry(wallet, EntryType.DEBIT, req.getAmountCents(),
                source, req.getExternalRef(), null, now);

        wallet.setAvailableCents(wallet.getAvailableCents() - req.getAmountCents());
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        auditLogger.log(wallet.getUserId(), "WalletDebited", "USER",
                wallet.getUserId().toString(), source.name(),
                Map.of("amountCents", req.getAmountCents(),
                       "externalRef", req.getExternalRef() == null ? "" : req.getExternalRef()));

        outboxWriter.write("WalletDebited", "wallet.debited",
                Map.of("walletEntryId", entry.getId(),
                       "userId", wallet.getUserId(),
                       "amountCents", req.getAmountCents(),
                       "source", source.name(),
                       "externalRef", req.getExternalRef() == null ? "" : req.getExternalRef(),
                       "newBalanceCents", wallet.getAvailableCents()));

        return toResponse(entry, wallet);
    }

    /** Read-only summary used by the dashboard + Subscription's pre-checkout balance check. */
    @Transactional(readOnly = true)
    public WalletSummaryResponse summary(UUID userId) {
        Wallet wallet = walletRepository.findById(userId)
                .orElseGet(() -> emptyWalletView(userId));
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<WalletEntryResponse> recent = walletEntryRepository
                .findByWalletUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, cutoff)
                .stream()
                .map(WalletEntryResponse::from)
                .toList();
        return WalletSummaryResponse.from(wallet, recent);
    }

    /**
     * Used by withdrawal create / cancel / fail handlers. Returns the wallet
     * reference under a pessimistic lock so the caller can adjust available /
     * pending atomically.
     */
    @Transactional
    public Wallet loadForUpdate(UUID userId) {
        return walletRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }

    /** Inserts a ledger entry without touching the wallet cache — caller is responsible. */
    public WalletEntry appendEntry(Wallet wallet,
                                   EntryType type,
                                   long amountCents,
                                   EntrySource source,
                                   String externalRef,
                                   String memo,
                                   Instant now) {
        WalletEntry entry = new WalletEntry();
        entry.setWalletUserId(wallet.getUserId());
        entry.setType(type);
        entry.setAmountCents(amountCents);
        entry.setSource(source);
        entry.setExternalRef(externalRef);
        entry.setMemo(memo);
        entry.setCreatedAt(now);
        return walletEntryRepository.save(entry);
    }

    /** Auto-create per Decision 3.3 — credits never fail because of a missing wallet. */
    private Wallet createWallet(UUID userId, Instant now) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setCurrency(currency);
        wallet.setAvailableCents(0);
        wallet.setPendingCents(0);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        Wallet saved = walletRepository.save(wallet);
        log.info("Auto-created wallet for user {}", userId);
        return saved;
    }

    private Wallet emptyWalletView(UUID userId) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setCurrency(currency);
        wallet.setAvailableCents(0);
        wallet.setPendingCents(0);
        Instant now = Instant.now();
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        return wallet;
    }

    private static EntrySource parseSource(String raw, java.util.Set<EntrySource> allowed, String op) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleViolationException("source is required for " + op);
        }
        // Accept both SNAKE_CASE (e.g. "BAN_REFUND") and PascalCase (e.g.
        // "BanRefund") since Subscription's WalletServiceClient sends the
        // latter and the enum prefers the former.
        String normalised = raw.contains("_")
                ? raw.toUpperCase()
                : raw.replaceAll("(?<!^)([A-Z])", "_$1").toUpperCase();
        EntrySource source;
        try {
            source = EntrySource.valueOf(normalised);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Unknown source: " + raw);
        }
        if (!allowed.contains(source)) {
            throw new BusinessRuleViolationException("source " + source + " is not valid for " + op);
        }
        return source;
    }

    private WalletWriteResponse toResponse(WalletEntry entry, Wallet wallet) {
        return WalletWriteResponse.builder()
                .walletEntryId(entry.getId())
                .newBalanceCents(wallet.getAvailableCents())
                .pendingCents(wallet.getPendingCents())
                .currency(wallet.getCurrency())
                .build();
    }
}
