package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.IdempotencyKey;
import com.example.DumblePayment.repository.IdempotencyKeyRepository;
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
 * Per-step DB mutations for {@link IdempotencyService} carried in their own
 * {@code REQUIRES_NEW} transactions. A separate bean (rather than self-call
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(String key,
                            String endpoint,
                            UUID userId,
                            int httpStatus,
                            String requestHash,
                            long ttlHours) {
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
        row.setRequestHash(requestHash);
        row.setResponseJson(null);
        row.setCreatedAt(Instant.now());
        row.setExpiresAt(Instant.now().plus(ttlHours, ChronoUnit.HOURS));
        // entityManager.persist forces a bare INSERT that hard-fails on a PK
        // collision — unlike repository.save(), which Spring Data routes
        // through merge() for manually-@Id entities and quietly UPDATEs the
        // existing row when a concurrent peer has already inserted it. With
        // merge() semantics two racing tryClaim calls can both return true
        // (one INSERT, one UPDATE) and a double-spend slips through; persist
        // closes that race by guaranteeing exactly one winner.
        //
        // PersistenceException from the flush() is the unique-violation case;
        // re-throw as Spring's DataIntegrityViolationException so the caller's
        // existing race handling (catch in IdempotencyService.run) still works
        // — @Component beans don't get the @Repository exception-translation
        // proxy, so we translate manually here.
        try {
            entityManager.persist(row);
            entityManager.flush();
        } catch (PersistenceException ex) {
            throw new DataIntegrityViolationException(
                    "Idempotency-Key already claimed by a concurrent peer", ex);
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
