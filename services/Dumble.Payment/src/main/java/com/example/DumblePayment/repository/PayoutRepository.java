package com.example.DumblePayment.repository;

import com.example.DumblePayment.domain.Payout;
import com.example.DumblePayment.domain.enums.PayoutStatus;
import com.example.DumblePayment.domain.enums.PayoutType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    /**
     * Used by the {@code /by-caller-ref} lookup that backs Wallet's reaper
     * (and Subscription's recovery for stuck cohort batches).
     */
    Optional<Payout> findByTypeAndCallerReference(PayoutType type, String callerReference);

    Optional<Payout> findByCallerReference(String callerReference);

    Optional<Payout> findByProviderRef(String providerRef);

    /** Pessimistic lock for the webhook lifecycle path. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payout p WHERE p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") UUID id);

    List<Payout> findByCreatedAtBetween(Instant from, Instant to);

    List<Payout> findByStatus(PayoutStatus status);
}
