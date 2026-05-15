package com.example.DumbleWallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet PDF Decisions 3.1, 4.1, 4.3 — caller-supplied {@code Idempotency-Key}
 * dedupes credit / debit / withdrawal-request POSTs over a 24h window.
 *
 * Lifecycle gate (PENDING / COMPLETED) lets the PK on {@code idempotency_keys}
 * serialize concurrent peers: insert PENDING up-front, run the action, then
 * flip to COMPLETED. Concurrent racers either replay the cached body or get
 * a 409 if the winner is still in flight.
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

    @Column(name = "user_id", nullable = false)
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
