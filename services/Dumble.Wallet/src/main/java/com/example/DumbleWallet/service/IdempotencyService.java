package com.example.DumbleWallet.service;

import com.example.DumbleWallet.domain.IdempotencyKey;
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
 * Wallet PDF Decisions 3.1, 4.1, 4.3 — caller-supplied {@code Idempotency-Key}
 * dedupes credit / debit / withdrawal-request POSTs over a 24h window.
 *
 * Concurrency model: insert-first-with-PENDING. The PK on
 * {@code idempotency_keys} is the serialization gate — racing peers either
 * see a COMPLETED row and replay the cached body, or a PENDING row and get
 * a 409 to retry once the winner finishes.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyStore store;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public IdempotencyService(IdempotencyKeyStore store,
                              ObjectMapper objectMapper,
                              @Value("${wallet.idempotency.ttl-hours:24}") long ttlHours) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    public <T> CachedResult<T> executeOrFetch(String key,
                                              String endpoint,
                                              UUID userId,
                                              int httpStatus,
                                              Class<T> resultType,
                                              Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header required");
        }

        boolean claimed;
        try {
            claimed = store.tryClaim(key, endpoint, userId, httpStatus, ttlHours);
        } catch (DataIntegrityViolationException dup) {
            claimed = false;
        }

        if (!claimed) {
            return resolveExisting(key, endpoint, userId, httpStatus, resultType, action);
        }

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
