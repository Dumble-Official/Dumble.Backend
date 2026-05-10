package com.example.DumbleSubscription.scheduler;

import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import com.example.DumbleSubscription.service.RenewalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Hourly job that re-attempts charges for PAST_DUE subs whose nextRetryAt has
 * arrived. Decision 7.3 — three retries over seven days.
 */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DunningRetryJob {

    private static final Logger log = LoggerFactory.getLogger(DunningRetryJob.class);

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final RenewalService renewalService;

    public DunningRetryJob(BundleSubscriptionRepository bundleSubscriptionRepository,
                           PlatformSubscriptionRepository platformSubscriptionRepository,
                           RenewalService renewalService) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.platformSubscriptionRepository = platformSubscriptionRepository;
        this.renewalService = renewalService;
    }

    @Scheduled(cron = "0 25 * * * *")        // 25 minutes past the hour, offset from RenewalJob
    public void run() {
        Instant now = Instant.now();

        bundleSubscriptionRepository.findDunningRetriesDue(now).forEach(sub -> {
            try {
                renewalService.retryBundleDunning(sub);
            } catch (Exception ex) {
                log.error("Dunning retry failed for bundle sub {}", sub.getId(), ex);
            }
        });

        platformSubscriptionRepository.findDunningRetriesDue(now).forEach(sub -> {
            try {
                renewalService.renewPlatformPro(sub);
            } catch (Exception ex) {
                log.error("Dunning retry failed for platform sub {}", sub.getId(), ex);
            }
        });
    }
}
