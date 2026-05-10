package com.example.DumbleWallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet PDF Decision 2.1 — one row per user. The same wallet covers
 * participant + seller use; bundle earnings do NOT enter here (Decision 1.2)
 * — they live in Subscription's escrow and pay out directly to the seller's
 * bank via Payment.
 *
 * AvailableCents + PendingCents are CACHED projections of the immutable
 * {@link WalletEntry} ledger (Decision 2.3). Every credit/debit updates them
 * atomically in the same transaction; a daily reconciliation job verifies
 * cache vs ledger sum (Decision 5.2).
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    /**
     * Wallet PDF Decision 2.2 — keyed by userId so we can never have two
     * wallets for the same user.
     */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "available_cents", nullable = false)
    private long availableCents;

    /** Decision 4.2 — sum of in-flight withdrawals (Pending + Sent). */
    @Column(name = "pending_cents", nullable = false)
    private long pendingCents;

    /** Decision 3.4 — optimistic concurrency for racing credit/debit operations. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
