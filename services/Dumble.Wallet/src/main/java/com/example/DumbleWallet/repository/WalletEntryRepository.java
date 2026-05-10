package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.WalletEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {

    Page<WalletEntry> findByWalletUserIdOrderByCreatedAtDesc(UUID walletUserId, Pageable pageable);

    /** Used by the user-facing "recent activity" view (Decision 7.1) — last 30 days. */
    List<WalletEntry> findByWalletUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID walletUserId, Instant after);

    /**
     * Reconciliation job — Decision 5.2: sum the ledger and compare against
     * the cached {@code Wallet.availableCents}. Returns net (credits − debits).
     */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.type = com.example.DumbleWallet.domain.enums.EntryType.CREDIT
                                  THEN e.amountCents ELSE -e.amountCents END), 0)
        FROM WalletEntry e
        WHERE e.walletUserId = :userId
        """)
    long sumNetForUser(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT e.walletUserId FROM WalletEntry e")
    List<UUID> findAllUserIdsWithEntries();
}
