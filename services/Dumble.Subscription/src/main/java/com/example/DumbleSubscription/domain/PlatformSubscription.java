package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Decision 1.2: keyed by UserId alone — one tier per user
 * regardless of audience. PRO users have a row with PRO; FREE users either
 * have no row or a row with code FREE (we'll use the latter so we can record
 * historical PRO → FREE downgrades).
 */
@Entity
@Table(name = "platform_subscriptions",
       indexes = {
           @Index(name = "ix_platform_sub_user", columnList = "user_id", unique = true)
       })
@Getter
@Setter
@NoArgsConstructor
public class PlatformSubscription {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformPlanCode planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    private Instant startedAt;
    private Instant currentPeriodEnd;
    private Instant cancelScheduledAt;     // when downgrade Pro→Free was requested
    private Instant cancelledAt;           // actually transitioned

    private String providerRef;            // Paymob subscription/payment ref

    /* ----- Renewal + dunning (Decision 7.3) ----- */
    private String paymentMethodToken;

    /** CARD | WALLET | OTHER — gates whether renewals can auto-charge (Decision 7.2). */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type", length = 20)
    private com.example.DumbleSubscription.domain.enums.PaymentMethodType paymentMethodType;

    private Instant pastDueAt;

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
