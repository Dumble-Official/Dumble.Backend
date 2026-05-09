package com.example.DumbleSubscription.scheduler;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import com.example.DumbleSubscription.service.RenewalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Hourly job that fires renewal charges for due bundle + platform subs. */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class RenewalJob {

    private static final Logger log = LoggerFactory.getLogger(RenewalJob.class);

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final RenewalService renewalService;

    public RenewalJob(BundleSubscriptionRepository bundleSubscriptionRepository,
                      PlatformSubscriptionRepository platformSubscriptionRepository,
                      RenewalService renewalService) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.platformSubscriptionRepository = platformSubscriptionRepository;
        this.renewalService = renewalService;
    }

    @Scheduled(cron = "0 5 * * * *")        // 5 minutes past the top of every hour
    public void run() {
        Instant now = Instant.now();

        List<BundleSubscription> bundles = bundleSubscriptionRepository.findRenewalsDue(now);
        if (!bundles.isEmpty()) {
            log.info("RenewalJob: {} bundle subscriptions due", bundles.size());
        }
        for (BundleSubscription sub : bundles) {
            try {
                renewalService.renewBundle(sub);
            } catch (Exception ex) {
                log.error("Renewal failed for bundle sub {}", sub.getId(), ex);
            }
        }

        List<PlatformSubscription> platform = platformSubscriptionRepository.findActiveWithExpiredPeriod(now)
                .stream()
                .filter(s -> s.getCancelScheduledAt() == null)
                .toList();
        if (!platform.isEmpty()) {
            log.info("RenewalJob: {} platform subscriptions due", platform.size());
        }
        for (PlatformSubscription sub : platform) {
            try {
                renewalService.renewPlatformPro(sub);
            } catch (Exception ex) {
                log.error("Renewal failed for platform sub {}", sub.getId(), ex);
            }
        }
    }
}
