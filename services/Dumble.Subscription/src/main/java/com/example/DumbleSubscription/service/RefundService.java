package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per Subscription PDF Decisions 6.2, 16.3 — when a seller is banned, all
 * unreleased escrow is refunded to the affected participants' wallets,
 * computed PER affected subscription so each participant gets their own
 * unreleased portion (not a lump sum).
 *
 * bug_015-run2 — each per-sub refund runs in its own REQUIRES_NEW transaction
 * (see {@link SingleSubRefundExecutor}). A transient WalletServiceClient
 * failure on sub N can't roll back the already-applied credits + escrow
 * flips for subs 1..N-1. Wallet's idempotency-key dedupe protects re-credits
 * on retry; this method protects the local state from divergence with
 * Wallet's ledger.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    /** Statuses whose escrow may still be unreleased and therefore must be inspected on ban. */
    private static final Set<SubscriptionStatus> REFUNDABLE_STATUSES = Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.CANCELLED,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.PENDING
    );

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final SingleSubRefundExecutor singleRefundExecutor;

    public RefundService(BundleSubscriptionRepository bundleSubscriptionRepository,
                         SingleSubRefundExecutor singleRefundExecutor) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.singleRefundExecutor = singleRefundExecutor;
    }

    /**
     * Top-level loop is intentionally NOT @Transactional — each iteration
     * commits independently via SingleSubRefundExecutor. A failure on sub N
     * leaves subs 1..N-1 durably refunded in BOTH Wallet and our DB.
     */
    public void refundOnSellerBan(UUID sellerId, String reason) {
        // bug_031 — Decision 16.3 says "all unreleased escrow" must be
        // refunded on ban. The previous query only matched ACTIVE subs, so
        // PAST_DUE / CANCELLED / EXPIRED / PENDING subs with HELD or AVAILABLE
        // escrow had their funds stranded forever.
        List<UUID> subIds = bundleSubscriptionRepository
                .findBySellerIdAndStatusIn(sellerId, REFUNDABLE_STATUSES)
                .stream()
                .filter(s -> s.getStatus() != SubscriptionStatus.REFUNDED)
                .map(BundleSubscription::getId)
                .toList();

        log.info("Ban refund flow: seller={} affected_subs={} reason={}", sellerId, subIds.size(), reason);

        int failed = 0;
        for (UUID subId : subIds) {
            try {
                singleRefundExecutor.refundOne(subId, reason);
            } catch (RuntimeException ex) {
                failed++;
                log.error("Ban refund for sub {} failed; other subs unaffected", subId, ex);
            }
        }
        if (failed > 0) {
            log.warn("Ban refund flow: seller={} {}/{} subs failed and need manual reconciliation",
                    sellerId, failed, subIds.size());
        }
    }
}
