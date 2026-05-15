package com.example.DumblePayment.repository;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.domain.enums.ChargeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    Optional<Charge> findByProviderRef(String providerRef);

    List<Charge> findByCallerReference(String callerReference);

    /**
     * Pessimistic-locked variant — used by the webhook async processor so a
     * concurrent retry can't race the lifecycle update into an optimistic
     * @Version conflict and lose the state transition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Charge c WHERE c.id = :id")
    Optional<Charge> findByIdForUpdate(@Param("id") UUID id);

    /** Reconciliation window query — Decision 7.1. */
    List<Charge> findByCreatedAtBetween(Instant from, Instant to);

    /**
     * Stuck-PENDING detector for reconciliation: a charge that's still in
     * PENDING long after it was issued either lost its webhook or the
     * provider call never returned. Surface to the recon job for repair.
     */
    @Query("""
        SELECT c FROM Charge c
        WHERE c.status = com.example.DumblePayment.domain.enums.ChargeStatus.PENDING
          AND c.createdAt < :cutoff
        """)
    List<Charge> findStuckPending(@Param("cutoff") Instant cutoff);
}
