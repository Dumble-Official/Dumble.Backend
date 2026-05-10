package com.example.DumbleWallet.repository.projection;

import java.util.UUID;

/**
 * Spring Data interface projection — one row per user with the net ledger
 * sum (credits − debits). Used by the reconciliation job's GROUP BY query
 * so the consumer gets typed accessors instead of casting from Object[].
 */
public interface LedgerSum {
    UUID getWalletUserId();
    long getNetCents();
}
