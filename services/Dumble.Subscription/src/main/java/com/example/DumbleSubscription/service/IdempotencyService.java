package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.IdempotencyKey;
import com.example.DumbleSubscription.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implements PDF Decision 12.3 — checkout-style endpoints carry an
 * Idempotency-Key header. Repeated calls with the same key within 24h return
 * the cached result instead of re-running the operation.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public IdempotencyService(IdempotencyKeyRepository repository,
                              ObjectMapper objectMapper,
                              @Value("${subscription.idempotency.ttl-hours:24}") long ttlHours) {
        this.repository = repository;
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
        Optional<IdempotencyKey> existing = repository.findById(key);
        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();
            if (row.getExpiresAt().isAfter(Instant.now())) {
                T cached = deserialize(row.getResponseJson(), resultType);
                return new CachedResult<>(cached, row.getHttpStatus(), true);
            }
            repository.delete(row);
        }
        T result = action.get();
        IdempotencyKey row = new IdempotencyKey();
        row.setKey(key);
        row.setEndpoint(endpoint);
        row.setUserId(userId);
        row.setHttpStatus(httpStatus);
        row.setResponseJson(serialize(result));
        row.setCreatedAt(Instant.now());
        row.setExpiresAt(Instant.now().plus(ttlHours, ChronoUnit.HOURS));
        repository.save(row);
        return new CachedResult<>(result, httpStatus, false);
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
}
