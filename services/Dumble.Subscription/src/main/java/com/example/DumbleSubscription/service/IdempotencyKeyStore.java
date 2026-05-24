package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.IdempotencyKey;
import com.example.DumbleSubscription.repository.IdempotencyKeyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Carries the per-step database mutations for {@link IdempotencyService} in
 * their own REQUIRES_NEW transactions. A separate bean (rather than self-call
 * inside IdempotencyService) is required so Spring's AOP proxy actually
 * intercepts these calls — self-invocation bypasses the proxy and would leave
 * the rows under the caller's transaction, defeating the point of inserting
 * the PENDING row up-front.
 */
@Component
public class IdempotencyKeyStore {

    static final String STATE_PENDING = "PENDING";
    static final String STATE_COMPLETED = "COMPLETED";

    private final IdempotencyKeyRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public IdempotencyKeyStore(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Inserts a PENDING row for {@code key}. Returns true on success. Throws
     * {@link org.springframework.dao.DataIntegrityViolationException} if a
     * concurrent peer raced us to the same PK.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(String key, String endpoint, UUID userId, int httpStatus, long ttlHours) {
        Optional<IdempotencyKey> existing = repository.findById(key);
        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();
            if (row.getExpiresAt().isBefore(Instant.now())) {
                repository.delete(row);
                repository.flush();
            } else {
                return false;
            }
        }
        IdempotencyKey row = new IdempotencyKey();
        row.setKey(key);
        row.setEndpoint(endpoint);
        row.setUserId(userId);
        row.setState(STATE_PENDING);
        row.setHttpStatus(httpStatus);
        row.setResponseJson(null);
        row.setCreatedAt(Instant.now());
        row.setExpiresAt(Instant.now().plus(ttlHours, ChronoUnit.HOURS));
        // entityManager.persist forces a bare INSERT and hard-fails on a PK
        // collision. repository.save() / saveAndFlush() for a manually-@Id
        // entity routes through merge() — Spring Data first SELECTs by id, and
        // if the row already exists it UPDATEs instead of failing. Two parallel
        // tryClaim calls under that semantic could both win (one INSERT, one
        // UPDATE) → double-spend. persist() closes the race; the second caller
        // gets a PersistenceException → DataIntegrityViolationException that
        // IdempotencyService's outer handler translates to "already in flight".
        try {
            entityManager.persist(row);
            entityManager.flush();
        } catch (PersistenceException ex) {
            throw new DataIntegrityViolationException(
                    "Idempotency key already claimed concurrently: " + key, ex);
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(String key, String responseJson, int httpStatus) {
        IdempotencyKey row = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency row vanished mid-flight: " + key));
        row.setState(STATE_COMPLETED);
        row.setResponseJson(responseJson);
        row.setHttpStatus(httpStatus);
        repository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(String key) {
        repository.findById(key).ifPresent(row -> {
            if (STATE_PENDING.equals(row.getState())) {
                repository.delete(row);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKey> find(String key) {
        return repository.findById(key);
    }
}
