package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformSubscriptionRepository extends JpaRepository<PlatformSubscription, UUID> {
    Optional<PlatformSubscription> findByUserId(UUID userId);

    /**
     * Period-end transitions:
     *   1. Cancelled-PRO whose currentPeriodEnd has passed → drop to FREE
     *   2. Active-PRO whose currentPeriodEnd has passed and has no cancelScheduledAt
     *      → renewal attempt
     */
    @Query("""
        SELECT s FROM PlatformSubscription s
        WHERE s.status = com.example.DumbleSubscription.domain.enums.SubscriptionStatus.ACTIVE
          AND s.currentPeriodEnd <= :now
        """)
    List<PlatformSubscription> findActiveWithExpiredPeriod(@Param("now") Instant now);

    @Query("""
        SELECT s FROM PlatformSubscription s
        WHERE s.status = com.example.DumbleSubscription.domain.enums.SubscriptionStatus.PAST_DUE
          AND s.nextRetryAt <= :now
        """)
    List<PlatformSubscription> findDunningRetriesDue(@Param("now") Instant now);

    long countByPlanCodeAndStatus(com.example.DumbleSubscription.domain.enums.PlatformPlanCode code, SubscriptionStatus status);
}
