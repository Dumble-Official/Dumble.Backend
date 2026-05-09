package com.example.DumbleSubscription.scheduler;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Per Subscription PDF Decision 11.2 — emits expiry-warning events for
 * non-renewing bundle subs at 7 days, 1 day, and on the day-of expiry.
 *
 * NotificationService consumes the events and renders the actual user-facing
 * notifications. This job's sole responsibility is to fire the right event
 * once per subscription per beat.
 *
 * Run hourly. Each beat is a 1-hour window so we don't double-fire — the
 * job only emits for subs whose endsAt falls within the current hour offset.
 */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiryNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryNotificationJob.class);

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final OutboxWriter outboxWriter;

    public ExpiryNotificationJob(BundleSubscriptionRepository bundleSubscriptionRepository,
                                 OutboxWriter outboxWriter) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.outboxWriter = outboxWriter;
    }

    @Scheduled(cron = "0 35 * * * *")        // 35 past the hour
    public void run() {
        Instant now = Instant.now();
        emitForBeat(now, 7 * 24, "subscription.bundle.expiring-7d");
        emitForBeat(now, 24, "subscription.bundle.expiring-1d");
        emitForBeat(now, 0, "subscription.bundle.expiring-today");
    }

    private void emitForBeat(Instant now, int hoursAhead, String routingKey) {
        Instant from = now.plus(hoursAhead, ChronoUnit.HOURS);
        Instant to = from.plus(1, ChronoUnit.HOURS);
        List<BundleSubscription> subs = bundleSubscriptionRepository.findExpiringBetween(from, to);
        for (BundleSubscription sub : subs) {
            outboxWriter.write("BundleSubscriptionExpiring", routingKey,
                    Map.of("subscriptionId", sub.getId(),
                            "participantId", sub.getParticipantId(),
                            "endsAt", sub.getEndsAt(),
                            "bundleName", sub.getBundleName()));
        }
        if (!subs.isEmpty()) {
            log.info("ExpiryNotificationJob beat={}h subs={}", hoursAhead, subs.size());
        }
    }
}
