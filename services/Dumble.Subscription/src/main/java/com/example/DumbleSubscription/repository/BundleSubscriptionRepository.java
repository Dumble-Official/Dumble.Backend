package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BundleSubscriptionRepository extends JpaRepository<BundleSubscription, UUID> {

    List<BundleSubscription> findByParticipantIdAndStatus(UUID participantId, SubscriptionStatus status);

    List<BundleSubscription> findBySellerIdAndStatus(UUID sellerId, SubscriptionStatus status);

    /**
     * bug_031 — used by RefundService.refundOnSellerBan to find every sub
     * with potentially-unreleased escrow on a banned seller, not just ACTIVE.
     */
    List<BundleSubscription> findBySellerIdAndStatusIn(UUID sellerId, java.util.Collection<SubscriptionStatus> statuses);

    /** bug_029 — used by the charge.completed webhook to confirm a PENDING sub. */
    Optional<BundleSubscription> findByProviderRef(String providerRef);

    /** Active dup check — supports the unique constraint enforcement at app level. */
    Optional<BundleSubscription> findByParticipantIdAndBundleIdAndStatus(UUID participantId, UUID bundleId, SubscriptionStatus status);

    /**
     * bug_029-run2 — the partial unique index now covers ACTIVE OR PENDING,
     * so the application-level dup check must too. Otherwise a retried
     * checkout under a fresh controller-level Idempotency-Key can land a
     * second PENDING row pointing at the same providerRef and poison the
     * webhook resolution path.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT s FROM BundleSubscription s
        WHERE s.participantId = :participantId
          AND s.bundleId = :bundleId
          AND s.status IN (
              com.example.DumbleSubscription.domain.enums.SubscriptionStatus.ACTIVE,
              com.example.DumbleSubscription.domain.enums.SubscriptionStatus.PENDING
          )
        """)
    Optional<BundleSubscription> findActiveOrPending(
            @org.springframework.data.repository.query.Param("participantId") UUID participantId,
            @org.springframework.data.repository.query.Param("bundleId") UUID bundleId);

    /** Auto-renewal scheduler — Decision 7.1. */
    @Query("""
        SELECT s FROM BundleSubscription s
        WHERE s.status = com.example.DumbleSubscription.domain.enums.SubscriptionStatus.ACTIVE
          AND s.autoRenew = TRUE
          AND s.endsAt <= :now
        """)
    List<BundleSubscription> findRenewalsDue(@Param("now") Instant now);

    /** Expiration scheduler — non-renewing subs whose endsAt has passed. */
    @Query("""
        SELECT s FROM BundleSubscription s
        WHERE s.status = com.example.DumbleSubscription.domain.enums.SubscriptionStatus.ACTIVE
          AND s.autoRenew = FALSE
          AND s.endsAt <= :now
        """)
    List<BundleSubscription> findExpirationsDue(@Param("now") Instant now);

    /** Dunning retry scheduler — Decision 7.3. */
    @Query("""
        SELECT s FROM BundleSubscription s
        WHERE s.status = com.example.DumbleSubscription.domain.enums.SubscriptionStatus.PAST_DUE
          AND s.nextRetryAt <= :now
        """)
    List<BundleSubscription> findDunningRetriesDue(@Param("now") Instant now);

    /**
     * Sub-expiring notifications — Decision 11.2. Returns ACTIVE non-renewing
     * subs whose endsAt falls inside the [from, to] window. Caller picks the
     * window per notification beat (7d, 1d, day-of).
     */
    @Query("""
        SELECT s FROM BundleSubscription s
        WHERE s.status = com.example.DumbleSubscription.domain.enums.SubscriptionStatus.ACTIVE
          AND s.autoRenew = FALSE
          AND s.endsAt BETWEEN :from AND :to
        """)
    List<BundleSubscription> findExpiringBetween(@Param("from") Instant from, @Param("to") Instant to);
}
