package com.example.DumbleWallet.scheduler;

import com.example.DumbleWallet.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Nightly purge of expired idempotency keys (Wallet PDF Decision 3.1 — 24h TTL). */
@Component
@ConditionalOnProperty(name = "wallet.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyKeyRepository repository;

    public IdempotencyCleanupJob(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 15 2 * * *")        // 02:15 daily
    @Transactional
    public void purge() {
        long deleted = repository.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency keys", deleted);
        }
    }
}
