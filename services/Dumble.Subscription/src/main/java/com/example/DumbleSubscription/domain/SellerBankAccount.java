package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Decisions 5.4 + 15.1. The seller-side bank destination
 * cohort payouts target. Without one, payouts are deferred indefinitely.
 *
 * Stored in Subscription (rather than Authentication) because payout-firing
 * decisions and dashboards live here.
 */
@Entity
@Table(name = "seller_bank_accounts")
@Getter
@Setter
@NoArgsConstructor
public class SellerBankAccount {

    @Id
    @Column(name = "seller_id")
    private UUID sellerId;

    @Column(nullable = false, length = 100)
    private String accountHolderName;

    /** Free-form per Paymob's payout-destination contract — bank account, VC number, etc. */
    @Column(nullable = false, length = 255)
    private String destination;

    @Column(nullable = false, length = 30)
    private String destinationType;     // BANK_ACCOUNT | VODAFONE_CASH | etc.

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
