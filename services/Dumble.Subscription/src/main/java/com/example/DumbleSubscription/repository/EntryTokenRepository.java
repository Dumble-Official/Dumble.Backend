package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.EntryToken;
import com.example.DumbleSubscription.domain.enums.EntryTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntryTokenRepository extends JpaRepository<EntryToken, UUID> {
    Optional<EntryToken> findByTokenSecret(String tokenSecret);
    List<EntryToken> findByBundleSubscriptionIdAndStatus(UUID bundleSubscriptionId, EntryTokenStatus status);

    /** Used by EntryTokenCleanupJob — avoids the previous findAll() pattern. */
    List<EntryToken> findByStatusAndExpiresAtBefore(EntryTokenStatus status, Instant cutoff);
}
