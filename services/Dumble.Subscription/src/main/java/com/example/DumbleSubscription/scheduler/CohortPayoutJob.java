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

import java.time.Instant;
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

        Map<UUID, List<EscrowEntry>> bySeller = due.stream()
                .collect(Collectors.groupingBy(EscrowEntry::getSellerId));

        log.info("CohortPayoutJob: {} entries due across {} sellers", due.size(), bySeller.size());

        for (Map.Entry<UUID, List<EscrowEntry>> e : bySeller.entrySet()) {
            UUID sellerId = e.getKey();
            List<EscrowEntry> entries = e.getValue();

            // Banned / Frozen sellers don't get payouts. RefundService will handle
            // their escrow separately on the ban path.
            if (!sellerLifecycleService.canFirePayouts(sellerId)) {
                log.debug("CohortPayoutJob: skipping seller {} (lifecycle blocks payouts)", sellerId);
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
                        Map.of("sellerId", sellerId, "deferred", entries.size(),
                               "reason", "NO_BANK_ACCOUNT_CONNECTED"));
                continue;
            }

            long total = entries.stream().mapToLong(EscrowEntry::getAmountCents).sum();
            String currency = entries.get(0).getCurrency();
            String batchRef = "cohort-" + sellerId + "-" + now.toEpochMilli();
            String cohortKey = entries.get(0).getCohortKey();

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
                        Map.of("amount", total, "entries", entries.size()));
            } catch (Exception ex) {
                log.error("Cohort payout failed for seller {} — entries left HELD for retry next run", sellerId, ex);
                // Leave entries HELD; next run will retry
            }
        }
    }
}
