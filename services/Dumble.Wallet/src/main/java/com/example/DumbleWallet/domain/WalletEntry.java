package com.example.DumbleWallet.domain;

import com.example.DumbleWallet.domain.enums.EntrySource;
import com.example.DumbleWallet.domain.enums.EntryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet PDF Decision 2.2 + 5.1 — append-only ledger. UPDATE / DELETE are
 * blocked at the database level by a trigger on {@code wallet_entries}.
 * Corrections happen by adding a compensating entry with
 * {@link EntrySource#ADMIN_ADJUSTMENT}.
 *
 * The entry is the system of record; the Wallet row's cached
 * {@code availableCents} / {@code pendingCents} are reconciled against the
 * ledger sum daily (Decision 5.2).
 */
@Entity
@Table(name = "wallet_entries",
       indexes = {
           @Index(name = "ix_wallet_entry_user_created", columnList = "wallet_user_id, created_at DESC"),
           @Index(name = "ix_wallet_entry_external_ref", columnList = "external_ref")
       })
@Getter
@Setter
@NoArgsConstructor
public class WalletEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "wallet_user_id", nullable = false)
    private UUID walletUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryType type;

    /** Always positive; sign is conveyed by {@link #type}. */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EntrySource source;

    /**
     * Wallet PDF Decision 2.2 — points back at whatever caused the movement
     * (refund-id, subscription-id, withdrawal-id). Wallet doesn't validate the
     * value (Decision 1.4) — it's just an audit-trail breadcrumb.
     */
    @Column(name = "external_ref", length = 255)
    private String externalRef;

    @Column(length = 500)
    private String memo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
