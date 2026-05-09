package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.SellerType;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Participant ↔ seller relationship. Per PDF Decision 12.1, the bundle's
 * name / price / duration / sellerId are SNAPSHOTTED onto this row at creation.
 * The live Bundle row (in BundleManagement) can be deleted, repriced, renamed —
 * this row stays correct forever.
 *
 * Per PDF Decision 12.2, unique on (participant_id, bundle_id) where status =
 * ACTIVE — enforced by a partial unique index in V1 migration.
 */
@Entity
@Table(name = "bundle_subscriptions",
       indexes = {
           @Index(name = "ix_bundle_sub_participant", columnList = "participant_id"),
           @Index(name = "ix_bundle_sub_seller", columnList = "seller_id"),
           @Index(name = "ix_bundle_sub_bundle", columnList = "bundle_id"),
           @Index(name = "ix_bundle_sub_status", columnList = "status")
       })
@Getter
@Setter
@NoArgsConstructor
public class BundleSubscription {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_type", nullable = false)
    private SellerType sellerType;

    /** Reference to the live BundleManagement Bundle row. May be deleted later. */
    @Column(name = "bundle_id", nullable = false)
    private UUID bundleId;

    /* ----- Snapshot fields (frozen at creation, per Decision 12.1) ----- */
    @Column(nullable = false)
    private String bundleName;

    @Column(nullable = false)
    private long pricePaidCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int durationDays;

    /** Bundle.ExpiresOn at the moment of subscribing — null = evergreen. */
    private Instant bundleExpiresOnSnapshot;

    /* ----- Lifecycle ----- */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant endsAt;

    private Instant cancelledAt;

    /** Whether this sub auto-renews at endsAt. False if bundle had ExpiresOn (Decision 2.3). */
    @Column(nullable = false)
    private boolean autoRenew;

    private String providerRef;

    /** Tokenized card / wallet handle from Paymob — used by RenewalService at next cycle. */
    private String paymentMethodToken;

    /** CARD | WALLET | OTHER — gates whether renewals can auto-charge (Decision 7.2). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private com.example.DumbleSubscription.domain.enums.PaymentMethodType paymentMethodType;

    /** JSON snapshot of the bundle's amenities/permissions at sub time (Decision 21.4). */
    @Column(columnDefinition = "TEXT")
    private String amenitiesJson;

    /* ----- Promo (per Decision 9.x) ----- */
    private String promoCode;
    private Long promoDiscountCents;

    /* ----- Dunning state (Decision 7.3) ----- */
    private Instant pastDueAt;

    /**
     * Timestamp of the last RenewalPromptNeeded emission for this sub. Used by
     * RenewalService to drop the row out of the renewal pool after the prompt
     * fires, so wallet/no-token subs don't re-prompt every hour forever
     * (bug_019).
     */
    @Column(name = "renewal_prompted_at")
    private Instant renewalPromptedAt;

    @Column(nullable = false)
    private int retryAttempts;

    private Instant nextRetryAt;

    @Column(nullable = false)
    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
