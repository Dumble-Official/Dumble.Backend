package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Decision 12.3 — checkout requests carry an
 * Idempotency-Key header. 24-hour TTL; nightly cleanup purges expired rows.
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

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Lifecycle gate. Inserted PENDING up-front so the PK serializes concurrent
     * peers; flipped to COMPLETED once the action has run and its response is
     * cached. See IdempotencyService.executeOrFetch.
     */
    @Column(nullable = false, length = 20)
    private String state;

    /** Cached response body the original call returned (null while PENDING). */
    @Column(columnDefinition = "TEXT")
    private String responseJson;

    @Column(nullable = false)
    private int httpStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
