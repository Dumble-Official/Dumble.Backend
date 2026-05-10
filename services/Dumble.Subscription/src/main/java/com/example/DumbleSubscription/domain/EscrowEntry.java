package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per service-period of a BundleSubscription's earnings, held until
 * the cycle elapses then released as a cohort payout (PDF Decisions 4.x, 5.x).
 *
 * Annual subs split into 12 entries (Decision 4.2). Each entry records its
 * cohort identity (start week of the originating subscription) so the weekly
 * payout job can batch them.
 *
 * If the seller has no connected bank when payout fires, the entry is deferred
 * (Decision 5.4): {@code originalScheduledAt} stays immutable, {@code reason}
 * records why, and {@code deferredCount} increments each cycle until the seller
 * adds an account.
 */
@Entity
@Table(name = "escrow_entries",
       indexes = {
           @Index(name = "ix_escrow_seller", columnList = "seller_id"),
           @Index(name = "ix_escrow_status_scheduled", columnList = "status,original_scheduled_at"),
           @Index(name = "ix_escrow_subscription", columnList = "bundle_subscription_id")
       })
@Getter
@Setter
@NoArgsConstructor
public class EscrowEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bundle_subscription_id", nullable = false)
    private UUID bundleSubscriptionId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowStatus status;

    /** Calendar week-of-month label, e.g. "2026-W18". Used for cohort batching. */
    @Column(nullable = false)
    private String cohortKey;

    @Column(nullable = false)
    private Instant originalScheduledAt;

    /** Free-form code if scheduling was skipped — e.g. NO_BANK_ACCOUNT_CONNECTED. */
    private String deferReason;

    @Column(nullable = false)
    private int deferredCount;

    private Instant releasedAt;       // Held → Available transition timestamp
    private Instant paidOutAt;        // Available → PaidOut transition timestamp
    private String payoutRef;         // Payment service Payout id

    @Column(nullable = false)
    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
