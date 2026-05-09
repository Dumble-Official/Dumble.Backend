package com.example.DumbleSubscription.scheduler;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
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

/**
 * Hourly job that:
 *   1. Marks non-renewing bundle subs whose endsAt has passed → EXPIRED.
 *   2. Drops PRO platform subs to FREE when cancelScheduledAt has passed
 *      currentPeriodEnd (Decision 13.2 cancel-at-period-end).
 */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirationJob.class);

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;

    public ExpirationJob(BundleSubscriptionRepository bundleSubscriptionRepository,
                         PlatformSubscriptionRepository platformSubscriptionRepository,
                         OutboxWriter outboxWriter,
                         AuditLogger auditLogger) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.platformSubscriptionRepository = platformSubscriptionRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
    }

    @Scheduled(cron = "0 15 * * * *")        // 15 past the hour
    @Transactional
    public void run() {
        Instant now = Instant.now();

        List<BundleSubscription> bundleExpiring = bundleSubscriptionRepository.findExpirationsDue(now);
        for (BundleSubscription sub : bundleExpiring) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setUpdatedAt(now);
            outboxWriter.write("BundleSubscriptionExpired", "subscription.bundle.expired",
                    Map.of("subscriptionId", sub.getId(), "reason", "natural_end"));
            auditLogger.log(sub.getId(), "Expired", "SYSTEM", "expiration-job", "natural_end", null);
        }
        if (!bundleExpiring.isEmpty()) {
            log.info("ExpirationJob: {} bundle subs marked EXPIRED", bundleExpiring.size());
        }

        // Decision 13.2 — Pro→Free transitions for cancelled subs whose period ended.
        List<PlatformSubscription> dueForDowngrade = platformSubscriptionRepository
                .findActiveWithExpiredPeriod(now).stream()
                .filter(s -> s.getCancelScheduledAt() != null)
                .toList();
        for (PlatformSubscription sub : dueForDowngrade) {
            sub.setPlanCode(PlatformPlanCode.FREE);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setCurrentPeriodEnd(null);
            sub.setCancelledAt(now);
            sub.setUpdatedAt(now);
            outboxWriter.write("PlatformSubscriptionCancelled", "subscription.platform.cancelled",
                    Map.of("userId", sub.getUserId()));
            outboxWriter.write("PlanChanged", "subscription.plan.changed",
                    Map.of("userId", sub.getUserId(), "newPlan", "FREE"));
            auditLogger.log(sub.getId(), "PlanChanged", "SYSTEM", "expiration-job",
                    "scheduled cancellation took effect", null);
        }
        if (!dueForDowngrade.isEmpty()) {
            log.info("ExpirationJob: {} platform subs downgraded PRO→FREE", dueForDowngrade.size());
        }
    }
}
