package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.IdempotencyKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implements PDF Decision 12.3 — checkout-style endpoints carry an
 * Idempotency-Key header. Repeated calls with the same key within 24h return
 * the cached result instead of re-running the operation.
 *
 * Concurrency model: insert-first-with-PENDING. The PK on idempotency_keys is
 * the serialization gate — the racing peer's INSERT fails with
 * DataIntegrityViolationException, which we catch and translate into either a
 * replay (if the winner has finished) or a 409 (if it hasn't yet). This closes
 * the TOCTOU race between findById and save() that the original check-then-act
 * version had.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyStore store;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public IdempotencyService(IdempotencyKeyStore store,
                              ObjectMapper objectMapper,
                              @Value("${subscription.idempotency.ttl-hours:24}") long ttlHours) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    /**
     * Returns the cached response if a row exists for this key; otherwise
     * runs the supplier, persists the result, and returns it. Designed for
     * write endpoints — read endpoints don't need this.
     */
    public <T> CachedResult<T> executeOrFetch(String key,
                                              String endpoint,
                                              UUID userId,
                                              int httpStatus,
                                              Class<T> resultType,
                                              Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header required");
        }
        // The dedup column is VARCHAR(128). Without an explicit length cap two
        // distinct keys that differ only past char 128 collide silently after
        // Postgres truncation, bypassing dedup. The charset restriction blocks
        // a multi-MB header (or one with control chars / spaces) from being
        // shoved into log lines and the DB column. Mirrors Wallet + Payment.
        if (key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 128 characters");
        }
        if (!key.matches("^[A-Za-z0-9._:\\-]+$")) {
            throw new IllegalArgumentException(
                    "Idempotency-Key may only contain letters, digits, '.', '_', ':', '-'");
        }

        // 1. Try to claim the key by inserting a PENDING row in its own short tx.
        //    If this succeeds, we are the winner — proceed to run the action.
        //    If it fails on PK violation, a concurrent peer already claimed it.
        boolean claimed;
        try {
            claimed = store.tryClaim(key, endpoint, userId, httpStatus, ttlHours);
        } catch (DataIntegrityViolationException dup) {
            claimed = false;
        }

        if (!claimed) {
            return resolveExisting(key, endpoint, userId, httpStatus, resultType, action);
        }

        // 2. We claimed the key. Run the action OUTSIDE the claim tx so the
        //    PENDING row is visible to concurrent peers. On success, persist
        //    the response under COMPLETED. On failure, drop the PENDING row
        //    so a fresh attempt can re-claim.
        T result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            try {
                store.releaseClaim(key);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup; the row will be reaped by the TTL job.
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
                                                Class<T> resultType,
                                                Supplier<T> action) {
        Optional<IdempotencyKey> rowOpt = store.find(key);
        if (rowOpt.isEmpty()) {
            // Race: peer rolled back between our claim attempt and this read.
            // Re-attempt the claim once.
            try {
                if (store.tryClaim(key, endpoint, userId, httpStatus, ttlHours)) {
                    T result;
                    try {
                        result = action.get();
                    } catch (RuntimeException ex) {
                        try { store.releaseClaim(key); } catch (RuntimeException ignored) {}
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
        // cached response (a cross-user IDOR; the level-up review caught
        // exactly this on Subscription). Treat key collision across users
        // as a conflict so the second caller has to pick a fresh key.
        if (!Objects.equals(row.getUserId(), userId)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key already used by a different caller; pick a fresh key");
        }
        if (IdempotencyKeyStore.STATE_PENDING.equals(row.getState())) {
            // A concurrent peer is still running the action. Don't replay an
            // empty body — make the client back off and retry.
            throw new IdempotencyConflictException("Request with this Idempotency-Key is in flight");
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new IdempotencyConflictException("Idempotency-Key has expired; use a fresh key");
        }
        T cached = deserialize(row.getResponseJson(), resultType);
        return new CachedResult<>(cached, row.getHttpStatus(), true);
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
