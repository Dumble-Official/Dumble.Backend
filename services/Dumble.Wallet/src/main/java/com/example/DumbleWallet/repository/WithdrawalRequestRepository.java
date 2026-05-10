package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.WithdrawalRequest;
import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    List<WithdrawalRequest> findByWalletUserIdOrderByCreatedAtDesc(UUID walletUserId);

    /** Used by the Payment-side webhooks to resolve which request just completed/failed. */
    Optional<WithdrawalRequest> findByPaymentRef(String paymentRef);

    /**
     * Pessimistic-locked variant — used by the cancel + markSubmitting paths
     * so they serialize on the row instead of racing into an optimistic
     * @Version conflict and surfacing as a 500 to one user.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT w FROM WithdrawalRequest w WHERE w.id = :id")
    Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") UUID id);

    /** Sum of in-flight withdrawals (PENDING + SUBMITTING + SENT) — used by the reconciliation job. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(w.amountCents), 0)
        FROM WithdrawalRequest w
        WHERE w.walletUserId = :userId
          AND w.status IN (
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.PENDING,
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.SUBMITTING,
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.SENT
          )
        """)
    long sumInFlightForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);

    List<WithdrawalRequest> findByStatus(WithdrawalStatus status);

    /**
     * No-op state mutation for the reaper's idle branches — bumps
     * {@code updatedAt} so the row drops out of {@code findStuckBefore}
     * for another grace window. Issued as a direct UPDATE rather than a
     * JPA dirty-write so it bypasses {@code @Version}: the reaper races
     * concurrent {@code markSent} / webhook handlers on the same row, and
     * an optimistic-lock conflict on a logically-no-op write would just
     * be noise. Since this method doesn't change logical state, skipping
     * the version check doesn't relax any real invariant.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE WithdrawalRequest w SET w.updatedAt = :now WHERE w.id = :id
        """)
    int bumpUpdatedAt(@Param("id") UUID id, @Param("now") java.time.Instant now);

    /**
     * Used by {@link com.example.DumbleWallet.scheduler.WithdrawalReaperJob}
     * to find rows whose forward-progress event from Payment was lost.
     * Three cases need recovery:
     *   PENDING    — Phase 2 HTTP never started (process crash mid-claim)
     *   SUBMITTING — Phase 2 HTTP didn't return (Wallet crash, Payment slow)
     *   SENT       — WithdrawalCompleted / WithdrawalFailed webhook never
     *                arrived (broker outage, consumer crash)
     *
     * The reaper queries Payment by callerReference for each and either
     * advances or reverses based on Payment's authoritative state. The grace
     * window prevents picking up a row mid-roundtrip, and the reaper bumps
     * {@code updatedAt} on no-op branches so the next tick doesn't poll
     * Payment for the same row repeatedly.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT w FROM WithdrawalRequest w
        WHERE w.status IN (
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.PENDING,
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.SUBMITTING,
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.SENT
          )
          AND w.updatedAt < :cutoff
        """)
    List<WithdrawalRequest> findStuckBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
