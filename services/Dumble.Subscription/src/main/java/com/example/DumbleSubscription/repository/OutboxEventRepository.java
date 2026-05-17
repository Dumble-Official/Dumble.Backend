package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.OutboxEvent;
import com.example.DumbleSubscription.domain.enums.OutboxStatus;
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

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable page);

    /**
     * Pessimistic-write fetch used by {@link com.example.DumbleSubscription.event.OutboxPublishingPersister#claim}.
     * Serialises concurrent claim() calls for the same id across replicas — without it
     * two dispatcher ticks can both read the same PENDING row and both publish.
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
        WHERE o.status = com.example.DumbleSubscription.domain.enums.OutboxStatus.IN_FLIGHT
          AND o.publishedAt IS NOT NULL
          AND o.publishedAt < :cutoff
        """)
    List<OutboxEvent> findStuckInFlight(@Param("cutoff") Instant cutoff);
}
