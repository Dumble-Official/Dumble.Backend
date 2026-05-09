package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EscrowEntryRepository extends JpaRepository<EscrowEntry, UUID> {

    List<EscrowEntry> findByStatusAndOriginalScheduledAtLessThanEqual(EscrowStatus status, Instant cutoff);

    List<EscrowEntry> findBySellerIdAndStatus(UUID sellerId, EscrowStatus status);

    List<EscrowEntry> findByBundleSubscriptionId(UUID bundleSubscriptionId);

    /** Used by PaymentEventListener to map payout webhooks to escrow entries. */
    List<EscrowEntry> findByPayoutRefAndStatus(String payoutRef, EscrowStatus status);

    /** Decision 19.2 — does this seller still have unsettled escrow? */
    long countBySellerIdAndStatusIn(UUID sellerId, Collection<EscrowStatus> statuses);
}
