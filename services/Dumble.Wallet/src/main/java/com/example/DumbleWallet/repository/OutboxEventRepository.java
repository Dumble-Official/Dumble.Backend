package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.OutboxEvent;
import com.example.DumbleWallet.domain.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    /**
     * Pessimistic-write fetch used by {@link com.example.DumbleWallet.event.OutboxPublishingPersister#claim}.
     * Serialises concurrent claim() calls for the same id across replicas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxEvent o WHERE o.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Used by the publisher's stuck-IN_FLIGHT recovery sweep — rows whose
     * publish-attempt happened before {@code cutoff} but never received a
     * confirm callback (broker connection died, app crash mid-confirm, etc.).
     */
    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = com.example.DumbleWallet.domain.enums.OutboxStatus.IN_FLIGHT
          AND o.publishedAt IS NOT NULL
          AND o.publishedAt < :cutoff
        """)
    List<OutboxEvent> findStuckInFlight(@Param("cutoff") Instant cutoff);
}
