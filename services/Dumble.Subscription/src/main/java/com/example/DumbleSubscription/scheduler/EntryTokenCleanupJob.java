package com.example.DumbleSubscription.scheduler;

import com.example.DumbleSubscription.domain.EntryToken;
import com.example.DumbleSubscription.domain.enums.EntryTokenStatus;
import com.example.DumbleSubscription.repository.EntryTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Marks ACTIVE entry tokens whose expiresAt has passed as EXPIRED so a
 * subsequent scan reports {@code TOKEN_EXPIRED} instead of {@code TOKEN_INVALID}.
 * Runs every 2 minutes.
 */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class EntryTokenCleanupJob {

    private final EntryTokenRepository repository;

    public EntryTokenCleanupJob(EntryTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void expireStale() {
        Instant now = Instant.now();
        List<EntryToken> stale = repository.findByStatusAndExpiresAtBefore(EntryTokenStatus.ACTIVE, now);
        for (EntryToken t : stale) {
            t.setStatus(EntryTokenStatus.EXPIRED);
        }
    }
}
