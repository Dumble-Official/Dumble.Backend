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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-sub refund in a brand-new transaction. Sibling bean (rather than a
 * method on RefundService) so Spring's AOP proxy actually intercepts the
 * REQUIRES_NEW boundary — same pattern IdempotencyKeyStore uses.
 *
 * Each call commits independently; a failure on sub N can't roll back the
 * already-applied credits + escrow flips for subs 1..N-1 (bug_015-run2).
 */
@Component
public class SingleSubRefundExecutor {

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final WalletServiceClient walletServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;

    public SingleSubRefundExecutor(BundleSubscriptionRepository bundleSubscriptionRepository,
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundOne(UUID subId, String reason) {
        BundleSubscription sub = bundleSubscriptionRepository.findById(subId).orElse(null);
        if (sub == null || sub.getStatus() == SubscriptionStatus.REFUNDED) {
            return;
        }
        Instant now = Instant.now();
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
