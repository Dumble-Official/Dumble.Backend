package com.example.DumbleWallet.service;

import com.example.DumbleWallet.domain.IdempotencyKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wallet PDF Decisions 3.1, 4.1, 4.3 — caller-supplied {@code Idempotency-Key}
 * dedupes credit / debit / withdrawal-request POSTs over a 24h window.
 *
 * Concurrency model: insert-first-with-PENDING. The PK on
 * {@code idempotency_keys} is the serialization gate — racing peers either
 * see a COMPLETED row and replay the cached body, or a PENDING row and get
 * a 409 to retry once the winner finishes.
 *
 * Strict variant of Decision 3.2: every request body is hashed (SHA-256 over
 * the Jackson JSON serialization with sorted map keys) and the hash is stored
 * on the idempotency row. A replay under the same key with a DIFFERENT body
 * is rejected with 409 "different request payload" instead of silently
 * returning the cached response — closes the
 * "client retries with a corrected amount silently no-ops" foot-gun.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyStore store;
    private final ObjectMapper objectMapper;
    // Dedicated mapper for body hashing — same modules as the API mapper but
    // with map keys sorted alphabetically so {"a":1,"b":2} and {"b":2,"a":1}
    // hash identically. copy() so the API's normal serialization isn't
    // affected by the sort feature.
    private final ObjectMapper hashMapper;
    private final long ttlHours;

    public IdempotencyService(IdempotencyKeyStore store,
                              ObjectMapper objectMapper,
                              @Value("${wallet.idempotency.ttl-hours:24}") long ttlHours) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.hashMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.ttlHours = ttlHours;
    }

    /**
     * Atomic-action variant: the wrapped action is a single
     * {@code @Transactional} method whose rollback on {@code RuntimeException}
     * is total (no committed side effects). Releases the dedup claim on
     * failure so the user can retry under the same key.
     */
    public <T> CachedResult<T> executeOrFetch(String key,
                                              String endpoint,
                                              UUID userId,
                                              int httpStatus,
                                              Object requestBody,
                                              Class<T> resultType,
                                              Supplier<T> action) {
        return run(key, endpoint, userId, httpStatus, requestBody, resultType, action, true);
    }

    /**
     * Orchestrated-action variant: the wrapped action commits one or more
     * sibling transactions (a Phase-1 row, a money debit) BEFORE its failure
     * window. On {@code RuntimeException} we keep the dedup row PENDING — a
     * concurrent retry with the same key gets {@link IdempotencyConflictException}
     * (409 in-flight) and the TTL job reaps the row at expiry. Releasing the
     * claim here would let a retry bypass dedup and re-debit / re-dispatch.
     */
    public <T> CachedResult<T> executeOrchestrated(String key,
                                                   String endpoint,
                                                   UUID userId,
                                                   int httpStatus,
                                                   Object requestBody,
                                                   Class<T> resultType,
                                                   Supplier<T> action) {
        return run(key, endpoint, userId, httpStatus, requestBody, resultType, action, false);
    }

    private <T> CachedResult<T> run(String key,
                                    String endpoint,
                                    UUID userId,
                                    int httpStatus,
                                    Object requestBody,
                                    Class<T> resultType,
                                    Supplier<T> action,
                                    boolean releaseOnFailure) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header required");
        }
        // The dedup column is VARCHAR(128). Without an explicit length cap two
        // distinct keys that differ only past char 128 collide silently after
        // Postgres truncation, bypassing dedup. Charset restriction prevents
        // a multi-MB header from being shoved into our log lines / DB column.
        if (key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 128 characters");
        }
        if (!key.matches("^[A-Za-z0-9._:\\-]+$")) {
            throw new IllegalArgumentException(
                    "Idempotency-Key may only contain letters, digits, '.', '_', ':', '-'");
        }

        String requestHash = hashRequest(requestBody);

        boolean claimed;
        try {
            claimed = store.tryClaim(key, endpoint, userId, httpStatus, requestHash, ttlHours);
        } catch (DataIntegrityViolationException dup) {
            claimed = false;
        }

        if (!claimed) {
            return resolveExisting(key, endpoint, userId, httpStatus, requestHash, resultType, action, releaseOnFailure);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            if (releaseOnFailure) {
                try {
                    store.releaseClaim(key);
                } catch (RuntimeException ignored) {
                    // Best-effort cleanup; the row will be reaped by the TTL job.
                }
            }
            throw ex;
        }
        store.completeClaim(key, serialize(result), httpStatus);
        return new CachedResult<>(result, httpStatus, false);
    }

    private <T> CachedResult<T> resolveExisting(String key,
                                                String endpoint,
                                                UUID userId,
                                                int httpStatus,
                                                String currentRequestHash,
                                                Class<T> resultType,
                                                Supplier<T> action,
                                                boolean releaseOnFailure) {
        Optional<IdempotencyKey> rowOpt = store.find(key);
        if (rowOpt.isEmpty()) {
            try {
                if (store.tryClaim(key, endpoint, userId, httpStatus, currentRequestHash, ttlHours)) {
                    T result;
                    try {
                        result = action.get();
                    } catch (RuntimeException ex) {
                        if (releaseOnFailure) {
                            try { store.releaseClaim(key); } catch (RuntimeException ignored) {}
                        }
                        throw ex;
                    }
                    store.completeClaim(key, serialize(result), httpStatus);
                    return new CachedResult<>(result, httpStatus, false);
                }
            } catch (DataIntegrityViolationException ignored) {
                // fall through
            }
            throw new IdempotencyConflictException("Idempotency-Key in flight; please retry");
        }
        IdempotencyKey row = rowOpt.get();
        // The PK on idempotency_keys is `key` alone — a second user reusing
        // the same key would otherwise be served the original caller's
        // cached response (cross-user IDOR). The body-hash check below ONLY
        // protects when request bodies differ; identical-body endpoints
        // (POST /wallet/me/withdrawals has no userId in the body, so two
        // users posting the same amount + destination collide on hash too)
        // still leak. Treat any key-collision across users as a 409 — same
        // fix shipped on Subscription's IdempotencyService.
        if (!Objects.equals(row.getUserId(), userId)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key already used by a different caller; pick a fresh key");
        }
        // Strict variant of Decision 3.2: if the stored body hash differs from
        // the new request's hash, refuse the replay. Rows written before V3
        // (or with a null incoming body) skip the comparison so the migration
        // stays backward-safe.
        String storedHash = row.getRequestHash();
        if (storedHash != null && currentRequestHash != null && !storedHash.equals(currentRequestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was used with a different request payload; refusing replay");
        }
        if (IdempotencyKeyStore.STATE_PENDING.equals(row.getState())) {
            throw new IdempotencyConflictException("Request with this Idempotency-Key is in flight");
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new IdempotencyConflictException("Idempotency-Key has expired; use a fresh key");
        }
        T cached = deserialize(row.getResponseJson(), resultType);
        return new CachedResult<>(cached, row.getHttpStatus(), true);
    }

    /**
     * SHA-256 hex of the request body serialized with sorted map keys, so
     * semantically-equivalent JSON ({@code {"a":1,"b":2}} vs
     * {@code {"b":2,"a":1}}) hashes identically. Returns {@code null} when the
     * body is {@code null} — the caller treats that as "no comparison".
     */
    private String hashRequest(Object body) {
        if (body == null) {
            return null;
        }
        try {
            byte[] payload = hashMapper.writeValueAsBytes(body);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotency request hash serialization failed", ex);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by every JRE — this branch is defensive.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotency cache serialize failed", ex);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotency cache deserialize failed", ex);
        }
    }

    public record CachedResult<T>(T value, int httpStatus, boolean replayed) {}

    /** 409 Conflict — concurrent request with the same key is mid-flight. */
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }
}
