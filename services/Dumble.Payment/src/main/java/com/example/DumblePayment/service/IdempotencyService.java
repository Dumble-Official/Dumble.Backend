package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.IdempotencyKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Decision 3.2 — caller-supplied {@code Idempotency-Key} required on charge /
 * refund / withdrawal / payout. 24h TTL.
 *
 * Concurrency model: insert-first-with-PENDING. The PK on
 * {@code idempotency_keys} is the serialization gate — racing peers fail PK
 * INSERT, get translated to a 409 (mid-flight) or replay (already completed).
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyStore store;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public IdempotencyService(IdempotencyKeyStore store,
                              ObjectMapper objectMapper,
                              @Value("${payment.idempotency.ttl-hours:24}") long ttlHours) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    /**
     * Atomic-action variant: the wrapped action is a single
     * {@code @Transactional} method whose rollback is total. Releases the
     * dedup claim on failure so the user can retry under the same key.
     */
    public <T> CachedResult<T> executeOrFetch(String key,
                                              String endpoint,
                                              UUID userId,
                                              int httpStatus,
                                              Class<T> resultType,
                                              Supplier<T> action) {
        return run(key, endpoint, userId, httpStatus, resultType, action, true);
    }

    /**
     * Orchestrated-action variant: the wrapped action commits a Phase-1 row
     * (charge / refund / payout in PENDING) BEFORE the provider HTTP call.
     * On {@code RuntimeException} we keep the dedup row PENDING — a
     * concurrent retry gets {@link IdempotencyConflictException} (409
     * in-flight) and the TTL job reaps it at expiry. Releasing here would
     * let a retry bypass dedup and dispatch a SECOND charge to Paymob.
     */
    public <T> CachedResult<T> executeOrchestrated(String key,
                                                   String endpoint,
                                                   UUID userId,
                                                   int httpStatus,
                                                   Class<T> resultType,
                                                   Supplier<T> action) {
        return run(key, endpoint, userId, httpStatus, resultType, action, false);
    }

    private <T> CachedResult<T> run(String key,
                                    String endpoint,
                                    UUID userId,
                                    int httpStatus,
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

        boolean claimed;
        try {
            claimed = store.tryClaim(key, endpoint, userId, httpStatus, ttlHours);
        } catch (DataIntegrityViolationException dup) {
            claimed = false;
        }

        if (!claimed) {
            return resolveExisting(key, endpoint, userId, httpStatus, resultType, action, releaseOnFailure);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            if (releaseOnFailure) {
                try {
                    store.releaseClaim(key);
                } catch (RuntimeException ignored) {
                    // best-effort cleanup; the row will be reaped by the TTL job
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
                                                Class<T> resultType,
                                                Supplier<T> action,
                                                boolean releaseOnFailure) {
        Optional<IdempotencyKey> rowOpt = store.find(key);
        if (rowOpt.isEmpty()) {
            try {
                if (store.tryClaim(key, endpoint, userId, httpStatus, ttlHours)) {
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
        if (IdempotencyKeyStore.STATE_PENDING.equals(row.getState())) {
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
