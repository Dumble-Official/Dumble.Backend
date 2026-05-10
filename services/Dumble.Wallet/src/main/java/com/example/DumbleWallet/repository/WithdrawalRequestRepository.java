package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.WithdrawalRequest;
import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    List<WithdrawalRequest> findByWalletUserIdOrderByCreatedAtDesc(UUID walletUserId);

    /** Used by the Payment-side webhooks to resolve which request just completed/failed. */
    Optional<WithdrawalRequest> findByPaymentRef(String paymentRef);

    /** Sum of in-flight withdrawals (PENDING + SENT) — used by the reconciliation job. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(w.amountCents), 0)
        FROM WithdrawalRequest w
        WHERE w.walletUserId = :userId
          AND w.status IN (
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.PENDING,
              com.example.DumbleWallet.domain.enums.WithdrawalStatus.SENT
          )
        """)
    long sumInFlightForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);

    List<WithdrawalRequest> findByStatus(WithdrawalStatus status);
}
