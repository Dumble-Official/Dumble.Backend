package com.example.DumbleSubscription.scheduler;

import com.example.DumbleSubscription.domain.SellerLifecycle;
import com.example.DumbleSubscription.domain.enums.SellerStatus;
import com.example.DumbleSubscription.repository.SellerLifecycleRepository;
import com.example.DumbleSubscription.service.SellerLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Per Subscription PDF Decision 16.2 — sellers in FROZEN whose 7-day window
 * has elapsed are auto-banned, which triggers the refund flow.
 *
 * Runs every 30 minutes — shorter than 1h so the auto-ban can fire promptly
 * after the 7-day deadline.
 */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class FrozenAutoBanJob {

    private static final Logger log = LoggerFactory.getLogger(FrozenAutoBanJob.class);

    private final SellerLifecycleRepository repository;
    private final SellerLifecycleService sellerLifecycleService;

    public FrozenAutoBanJob(SellerLifecycleRepository repository,
                            SellerLifecycleService sellerLifecycleService) {
        this.repository = repository;
        this.sellerLifecycleService = sellerLifecycleService;
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void run() {
        Instant now = Instant.now();
        List<SellerLifecycle> due = repository.findByStatusAndFrozenUntilLessThanEqual(SellerStatus.FROZEN, now);
        for (SellerLifecycle lifecycle : due) {
            log.info("FrozenAutoBanJob: auto-banning seller {} (frozen since {})",
                    lifecycle.getSellerId(), lifecycle.getFrozenAt());
            try {
                sellerLifecycleService.ban(lifecycle.getSellerId(),
                        "Auto-ban after 7-day freeze window — original reason: " + lifecycle.getFrozenReason());
            } catch (Exception ex) {
                log.error("Auto-ban failed for seller {}", lifecycle.getSellerId(), ex);
            }
        }

        // Decision 19.2 — close any wind-down sellers whose subs all drained.
        sellerLifecycleService.closeWindingDownIfDrained();
    }
}
