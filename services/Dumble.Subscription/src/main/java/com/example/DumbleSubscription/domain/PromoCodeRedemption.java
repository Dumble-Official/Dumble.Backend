package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Section 9 — Subscription only RECORDS that a redemption
 * happened. Code definitions, usage limits, expiry validation all live in
 * GymService.
 */
@Entity
@Table(name = "promo_code_redemptions",
       indexes = {
           @Index(name = "ix_promo_redemption_subscription", columnList = "bundle_subscription_id"),
           @Index(name = "ix_promo_redemption_code", columnList = "code")
       })
@Getter
@Setter
@NoArgsConstructor
public class PromoCodeRedemption {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bundle_subscription_id", nullable = false)
    private UUID bundleSubscriptionId;

    @Column(name = "gym_id", nullable = false)
    private UUID gymId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private long discountAppliedCents;

    /** PERCENT | FIXED — captured for audit so we don't have to re-call GymService. */
    @Column(length = 20)
    private String discountType;

    @Column(nullable = false)
    private Instant redeemedAt;
}
