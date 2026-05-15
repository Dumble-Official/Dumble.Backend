package com.example.DumbleWallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet PDF Decision 5.3 — append-only audit log distinct from the ledger.
 * Captures admin reads / adjustments / withdrawal-lifecycle transitions for
 * forensic reconstruction during disputes.
 *
 * Like {@link WalletEntry}, this is write-only — corrections happen by adding
 * a new row, never by modifying history.
 */
@Entity
@Table(name = "wallet_event_log",
       indexes = @Index(name = "ix_wallet_log_user_ts", columnList = "wallet_user_id, timestamp DESC"))
@Getter
@Setter
@NoArgsConstructor
public class WalletEventLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "wallet_user_id", nullable = false)
    private UUID walletUserId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false)
    private Instant timestamp;

    /** USER | ADMIN | SYSTEM | WEBHOOK. */
    @Column(nullable = false, length = 20)
    private String actor;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(length = 500)
    private String reason;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
}
