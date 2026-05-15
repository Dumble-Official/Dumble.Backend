package com.example.DumblePayment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Decision 3.2 — caller-supplied {@code Idempotency-Key} dedupes charge /
 * refund / withdrawal / payout POSTs over a 24h window. Lifecycle gate
 * (PENDING / COMPLETED) lets the PK serialize concurrent peers: insert
 * PENDING up-front, run the action, flip to COMPLETED with cached body.
 * Pattern matches Subscription / Wallet for uniformity.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(length = 128)
    private String key;

    @Column(nullable = false, length = 120)
    private String endpoint;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
