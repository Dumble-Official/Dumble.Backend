package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /**
     * Pessimistic lock variant — used by debit + withdrawal-create where we
     * need to read the current balance, decide whether to proceed, and update
     * atomically without a race against a concurrent credit/debit on the same
     * wallet. The {@code @Version} on {@link Wallet} provides optimistic
     * fallback (Decision 3.4); the explicit lock is for the high-contention
     * paths where retry-on-conflict would be wasteful.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByIdForUpdate(@Param("userId") UUID userId);
}
