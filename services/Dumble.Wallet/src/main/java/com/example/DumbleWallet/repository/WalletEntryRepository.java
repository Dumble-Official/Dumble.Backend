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

    /**
     * Single-query variant for the reconciliation job (Decision 5.2). Returns
     * one row per user with a ledger entry — net = SUM(credits) − SUM(debits).
     * Replaces the N+1 per-user lookup so the job scales linearly in DB
     * roundtrips, not in user count.
     */
    @Query("""
        SELECT e.walletUserId AS walletUserId,
               COALESCE(SUM(CASE WHEN e.type = com.example.DumbleWallet.domain.enums.EntryType.CREDIT
                                  THEN e.amountCents ELSE -e.amountCents END), 0) AS netCents
        FROM WalletEntry e
        GROUP BY e.walletUserId
        """)
    List<com.example.DumbleWallet.repository.projection.LedgerSum> sumNetByUser();

    @Query("SELECT DISTINCT e.walletUserId FROM WalletEntry e")
    List<UUID> findAllUserIdsWithEntries();
}
