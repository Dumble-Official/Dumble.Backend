package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.WalletServiceClient;
import com.example.DumbleSubscription.client.dto.WalletCreditRequest;
import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per Subscription PDF Decisions 6.2, 16.3 — when a seller is banned, all
 * unreleased escrow is refunded to the affected participants' wallets,
 * computed PER affected subscription so each participant gets their own
 * unreleased portion (not a lump sum).
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final WalletServiceClient walletServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;

    public RefundService(BundleSubscriptionRepository bundleSubscriptionRepository,
                         EscrowEntryRepository escrowEntryRepository,
                         WalletServiceClient walletServiceClient,
                         OutboxWriter outboxWriter,
                         AuditLogger auditLogger) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.escrowEntryRepository = escrowEntryRepository;
        this.walletServiceClient = walletServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public void refundOnSellerBan(UUID sellerId, String reason) {
        Instant now = Instant.now();
        List<BundleSubscription> subs = bundleSubscriptionRepository
                .findBySellerIdAndStatus(sellerId, SubscriptionStatus.ACTIVE);

        log.info("Ban refund flow: seller={} affected_subs={} reason={}", sellerId, subs.size(), reason);

        for (BundleSubscription sub : subs) {
            // Skip subs already refunded — defends against double-fire of the
            // ban webhook (Decision 8.4 idempotency at the application layer).
            if (sub.getStatus() == SubscriptionStatus.REFUNDED) {
                log.debug("Subscription {} already refunded — skipping", sub.getId());
                continue;
            }

            List<EscrowEntry> entries = escrowEntryRepository.findByBundleSubscriptionId(sub.getId());
            long unreleased = entries.stream()
                    .filter(e -> e.getStatus() == EscrowStatus.HELD || e.getStatus() == EscrowStatus.AVAILABLE)
                    .mapToLong(EscrowEntry::getAmountCents)
                    .sum();

            if (unreleased > 0) {
                // Idempotency-Key uses sub.id so a retried ban call doesn't double-credit.
                walletServiceClient.credit(
                        "ban-refund-" + sub.getId(),
                        WalletCreditRequest.builder()
                                .userId(sub.getParticipantId())
                                .amountCents(unreleased)
                                .source("BanRefund")
                                .externalRef(sub.getId().toString())
                                .memo("Refund — seller suspended/banned")
                                .build());
            }

            for (EscrowEntry entry : entries) {
                if (entry.getStatus() == EscrowStatus.HELD || entry.getStatus() == EscrowStatus.AVAILABLE) {
                    entry.setStatus(EscrowStatus.REFUNDED);
                    entry.setUpdatedAt(now);
                }
            }

            sub.setStatus(SubscriptionStatus.REFUNDED);
            sub.setCancelledAt(now);
            sub.setUpdatedAt(now);

            auditLogger.log(sub.getId(), "RefundIssued", "SYSTEM", "ban-refund-flow",
                    reason, Map.of("unreleasedCents", unreleased));
            outboxWriter.write("RefundIssued", "subscription.refund.issued",
                    Map.of("subscriptionId", sub.getId(),
                           "participantId", sub.getParticipantId(),
                           "amountCents", unreleased));
        }
    }
}
