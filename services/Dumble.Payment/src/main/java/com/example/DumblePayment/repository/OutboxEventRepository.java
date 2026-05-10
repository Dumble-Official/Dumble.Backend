package com.example.DumblePayment.repository;

import com.example.DumblePayment.domain.OutboxEvent;
import com.example.DumblePayment.domain.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    /**
     * Used by the publisher's stuck-IN_FLIGHT recovery sweep — rows whose
     * publish-attempt happened before {@code cutoff} but never received a
     * confirm callback (broker connection died, app crash mid-confirm, etc.).
     */
    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = com.example.DumblePayment.domain.enums.OutboxStatus.IN_FLIGHT
          AND o.publishedAt IS NOT NULL
          AND o.publishedAt < :cutoff
        """)
    List<OutboxEvent> findStuckInFlight(@Param("cutoff") Instant cutoff);
}
