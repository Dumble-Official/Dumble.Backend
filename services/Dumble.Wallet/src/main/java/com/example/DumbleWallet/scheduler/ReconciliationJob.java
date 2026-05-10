package com.example.DumbleWallet.scheduler;

import com.example.DumbleWallet.domain.Wallet;
import com.example.DumbleWallet.repository.WalletEntryRepository;
import com.example.DumbleWallet.repository.WalletRepository;
import com.example.DumbleWallet.service.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Wallet PDF Decision 5.2 — daily reconciliation against the cached balance.
 *
 * Sums the immutable {@code wallet_entries} ledger per user and compares to
 * the cached {@code Wallet.availableCents}. Discrepancies should be impossible
 * (transactional updates), but the job verifies it daily anyway and logs an
 * audit row + ERROR-level log per mismatch so ops can investigate.
 *
 * The job does NOT auto-correct — Decision 5.1 forbids modifying ledger rows.
 * Corrections happen via {@code AdminAdjustment} entries with documented memo.
 */
@Component
@ConditionalOnProperty(name = "wallet.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final WalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;
    private final AuditLogger auditLogger;

    public ReconciliationJob(WalletRepository walletRepository,
                             WalletEntryRepository walletEntryRepository,
                             AuditLogger auditLogger) {
        this.walletRepository = walletRepository;
        this.walletEntryRepository = walletEntryRepository;
        this.auditLogger = auditLogger;
    }

    @Scheduled(cron = "${wallet.reconciliation.cron:0 30 2 * * *}")
    @Transactional(readOnly = true)
    public void run() {
        int checked = 0;
        int mismatched = 0;
        for (Wallet wallet : walletRepository.findAll()) {
            checked++;
            long ledgerNet = walletEntryRepository.sumNetForUser(wallet.getUserId());
            // The ledger reflects net Available — the WITHDRAWAL_REQUESTED debit
            // logged at request time stays even while the funds sit in Pending,
            // and the WITHDRAWAL_REVERSED credit on a cancelled / failed
            // withdrawal restores them. So ledgerNet == availableCents is the
            // invariant we check.
            if (ledgerNet != wallet.getAvailableCents()) {
                mismatched++;
                log.error("Wallet {} balance mismatch: ledgerNet={} availableCents={}",
                        wallet.getUserId(), ledgerNet, wallet.getAvailableCents());
                auditLogger.log(wallet.getUserId(), "ReconciliationMismatch", "SYSTEM",
                        "reconciliation-job", "ledger != cached balance",
                        Map.of("ledgerNet", ledgerNet,
                               "availableCents", wallet.getAvailableCents(),
                               "pendingCents", wallet.getPendingCents()));
            }
        }
        log.info("Reconciliation: checked={} mismatched={}", checked, mismatched);
    }

    /**
     * Detects orphaned entries — ledger rows for users whose Wallet row has
     * vanished (should be impossible given the FK; included for paranoia).
     */
    public void verifyOrphans() {
        for (UUID userId : walletEntryRepository.findAllUserIdsWithEntries()) {
            if (walletRepository.findById(userId).isEmpty()) {
                log.error("Orphan ledger entries: wallet row missing for user {}", userId);
                auditLogger.log(userId, "OrphanLedgerEntries", "SYSTEM",
                        "reconciliation-job", "wallet row missing", null);
            }
        }
    }
}
