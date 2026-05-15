package com.example.DumbleWallet.domain.enums;

/**
 * Wallet PDF Decision 2.2 — every ledger entry is tagged with the reason the
 * money moved. Wallet doesn't interpret the value (Decision 1.4); it persists
 * it for reporting and audit.
 *
 * Note: there is no {@code CohortPayout} source — cohort payouts never touch
 * the wallet (Decision 1.2). Bundle revenue lives in Subscription's escrow and
 * goes straight to the seller's bank via Payment.
 */
public enum EntrySource {
    /** Decision 6.2 — Subscription credits a participant after a banned-seller refund. */
    BAN_REFUND,
    /** Subscription credits after a card chargeback reversal. */
    CHARGEBACK,
    /** Decision 5.3 — admin manual adjustment. Memo is mandatory at the controller. */
    ADMIN_ADJUSTMENT,
    /** Decision 4.1 — debit when a user spends wallet balance at checkout. */
    IN_APP_SPEND,
    /** Decision 4.2 — debit when a withdrawal is requested (moves Available → Pending). */
    WITHDRAWAL_REQUESTED,
    /** Decision 4.4 — credit reversal when Payment reports the withdrawal failed. */
    WITHDRAWAL_REVERSED
}
