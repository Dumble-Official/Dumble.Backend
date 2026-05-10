package com.example.DumbleSubscription.scheduler;

import com.example.DumbleSubscription.client.PaymentServiceClient;
import com.example.DumbleSubscription.client.dto.PayoutRequest;
import com.example.DumbleSubscription.client.dto.PayoutResponse;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.SellerBankAccount;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.repository.SellerBankAccountRepository;
import com.example.DumbleSubscription.service.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per Subscription PDF Decisions 5.1, 5.2, 5.4. Runs weekly per
 * {@code subscription.cohort-payout.cron} (default Mondays 03:00 UTC).
 *
 * For each EscrowEntry whose original-scheduled-at is past:
 *   1. Group by sellerId.
 *   2. If seller has a connected bank account → batch into a single payout
 *      via Payment service. Mark entries AVAILABLE; when PayoutCompleted
 *      arrives via webhook, transition to PAID_OUT.
 *   3. If not → defer (Decision 5.4): increment deferredCount, set
 *      deferReason, leave OriginalScheduledAt untouched.
 */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CohortPayoutJob {

    private static final Logger log = LoggerFactory.getLogger(CohortPayoutJob.class);

    private final EscrowEntryRepository escrowEntryRepository;
    private final SellerBankAccountRepository bankAccountRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final com.example.DumbleSubscription.service.SellerLifecycleService sellerLifecycleService;

    public CohortPayoutJob(EscrowEntryRepository escrowEntryRepository,
                           SellerBankAccountRepository bankAccountRepository,
                           PaymentServiceClient paymentServiceClient,
                           OutboxWriter outboxWriter,
                           AuditLogger auditLogger,
                           com.example.DumbleSubscription.service.SellerLifecycleService sellerLifecycleService) {
        this.escrowEntryRepository = escrowEntryRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.paymentServiceClient = paymentServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.sellerLifecycleService = sellerLifecycleService;
    }

    @Scheduled(cron = "${subscription.cohort-payout.cron:0 0 3 ? * MON}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        List<EscrowEntry> due = escrowEntryRepository
                .findByStatusAndOriginalScheduledAtLessThanEqual(EscrowStatus.HELD, now);
        if (due.isEmpty()) return;

        // bug_028 — group by (sellerId, cohortKey) so each cohort is its own
        // payout batch. The previous group-by-sellerId-only collapsed multiple
        // cohorts under a single PayoutRequest.cohortKey, mis-attributing
        // funds in audit + downstream reconciliation.
        Map<CohortBucket, List<EscrowEntry>> byCohort = due.stream()
                .collect(Collectors.groupingBy(e -> new CohortBucket(e.getSellerId(), e.getCohortKey())));

        log.info("CohortPayoutJob: {} entries due across {} (seller, cohort) buckets", due.size(), byCohort.size());

        for (Map.Entry<CohortBucket, List<EscrowEntry>> bucketEntry : byCohort.entrySet()) {
            CohortBucket bucket = bucketEntry.getKey();
            UUID sellerId = bucket.sellerId();
            String cohortKey = bucket.cohortKey();
            List<EscrowEntry> entries = bucketEntry.getValue();

            // Banned / Frozen sellers don't get payouts. RefundService will handle
            // their escrow separately on the ban path.
            if (!sellerLifecycleService.canFirePayouts(sellerId)) {
                log.debug("CohortPayoutJob: skipping seller {} cohort {} (lifecycle blocks payouts)",
                        sellerId, cohortKey);
                continue;
            }

            SellerBankAccount account = bankAccountRepository.findById(sellerId).orElse(null);

            if (account == null) {
                // Decision 5.4 — defer
                for (EscrowEntry entry : entries) {
                    entry.setDeferredCount(entry.getDeferredCount() + 1);
                    entry.setDeferReason("NO_BANK_ACCOUNT_CONNECTED");
                    entry.setUpdatedAt(now);
                }
                outboxWriter.write("PayoutDeferred", "subscription.payout.deferred",
                        Map.of("sellerId", sellerId,
                               "cohortKey", cohortKey,
                               "deferred", entries.size(),
                               "reason", "NO_BANK_ACCOUNT_CONNECTED"));
                continue;
            }

            long total = entries.stream().mapToLong(EscrowEntry::getAmountCents).sum();
            String currency = entries.get(0).getCurrency();
            // bug_016 — hash the sorted entry IDs into the batch ref instead
            // of now.toEpochMilli(). Same set of tranches → same key (so a
            // post-Payment commit failure recovers cleanly on the next run
            // without double-paying the seller); membership change (a
            // previously-deferred tranche rejoins) → fresh key.
            String batchRef = "cohort-" + sellerId + "-" + cohortKey + "-" + entriesHash(entries);

            try {
                PayoutResponse response = paymentServiceClient.payout(batchRef, PayoutRequest.builder()
                        .sellerId(sellerId)
                        .amountCents(total)
                        .currency(currency)
                        .destination(account.getDestination())
                        .destinationType(account.getDestinationType())
                        .callerReference(batchRef)
                        .cohortKey(cohortKey)
                        .notes("Dumble cohort " + cohortKey)
                        .build());

                for (EscrowEntry entry : entries) {
                    entry.setStatus(EscrowStatus.AVAILABLE);
                    entry.setReleasedAt(now);
                    entry.setPayoutRef(response == null ? null : response.getPayoutId());
                    entry.setUpdatedAt(now);
                    // Decision 8.3 — publish per-tranche EscrowReleased event.
                    outboxWriter.write("EscrowReleased", "subscription.escrow.released",
                            Map.of("escrowEntryId", entry.getId(),
                                    "sellerId", sellerId,
                                    "subscriptionId", entry.getBundleSubscriptionId(),
                                    "amountCents", entry.getAmountCents(),
                                    "cohortKey", entry.getCohortKey()));
                }
                auditLogger.log(sellerId, "PayoutInitiated", "SYSTEM", "cohort-payout-job",
                        "Initiated payout for cohort " + cohortKey,
                        Map.of("amount", total, "entries", entries.size(), "cohortKey", cohortKey));
            } catch (Exception ex) {
                log.error("Cohort payout failed for seller {} cohort {} — entries left HELD for retry next run",
                        sellerId, cohortKey, ex);
                // Leave entries HELD; next run will retry
            }
        }
    }

    /** Composite grouping key — one payout batch per (seller, cohort). */
    private record CohortBucket(UUID sellerId, String cohortKey) {}

    /**
     * bug_016 — short SHA-256 of the sorted entry IDs. Same membership →
     * same key (Payment dedupes a re-issued payout after a post-charge
     * commit failure). Membership change (deferred tranche rejoins, new
     * cycle adds tranches) → fresh key.
     */
    private static String entriesHash(List<EscrowEntry> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = entries.stream()
                    .map(EscrowEntry::getId)
                    .map(UUID::toString)
                    .sorted()
                    .collect(Collectors.joining("|"));
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);   // 24 hex chars
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
