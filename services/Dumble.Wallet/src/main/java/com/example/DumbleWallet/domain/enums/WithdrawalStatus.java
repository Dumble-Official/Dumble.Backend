package com.example.DumbleWallet.domain.enums;

/**
 * Wallet PDF Decision 4.2 — four-state lifecycle plus CANCELLED for the
 * user-initiated cancel allowed only while still in PENDING (Decision 4.3).
 */
public enum WithdrawalStatus {
    /** Created, balance moved Available → Pending, awaiting Payment ACK. */
    PENDING,
    /** Payment service confirmed it dispatched to Paymob. */
    SENT,
    /** Paymob confirmed money arrived; permanent debit is logged. */
    COMPLETED,
    /** Paymob rejected; balance was returned to Available via WITHDRAWAL_REVERSED entry. */
    FAILED,
    /** User cancelled while still PENDING (Decision 4.3). */
    CANCELLED
}
