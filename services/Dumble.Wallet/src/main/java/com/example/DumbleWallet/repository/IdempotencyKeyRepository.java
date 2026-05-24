package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    /**
     * Bulk-delete every idempotency row whose TTL has elapsed.
     *
     * Explicit {@code @Modifying @Query} instead of a derived
     * {@code deleteByExpiresAtBefore(...)} method: the derived form is
     * interpreted by Spring Data JPA as a SELECT-then-delete bound to a
     * single-result return type and throws
     * {@code IncorrectResultSizeDataAccessException("Delete query returned
     * more than one element: expected 1, actual N")} whenever more than one
     * row is expired — i.e. always, in steady state. The result is that
     * {@code IdempotencyCleanupJob} blew up on every nightly run and the
     * {@code idempotency_keys} table grew without bound.
     *
     * The JPQL variant runs as a real bulk DELETE and returns the count.
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
