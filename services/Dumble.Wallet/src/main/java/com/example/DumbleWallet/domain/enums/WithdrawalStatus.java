package com.example.DumbleWallet.domain.enums;

/**
 * Wallet PDF Decision 4.2 — withdrawal lifecycle plus CANCELLED for the
 * user-initiated cancel allowed only while still in PENDING (Decision 4.3).
 *
 * SUBMITTING is an internal sub-state of PENDING that closes the cancel race:
 * once we hand off to Payment we can't safely reverse without coordinating
 * with Paymob, so cancel is rejected as soon as the row is SUBMITTING and
 * only the lifecycle event (Completed / Failed) flips it forward.
 */
public enum WithdrawalStatus {
    /**
     * Row created, balance moved Available → Pending, but the Payment HTTP
     * call hasn't been issued yet. Cancel is allowed in this state. Normally
     * only reachable on crash recovery — the synchronous request path
     * advances to SUBMITTING before the HTTP call.
     */
    PENDING,
    /**
     * About to call (or actively calling) Payment. Cancel is REJECTED because
     * Payment may have already received the request even if we haven't seen
     * the ACK yet — reversing locally without telling Payment risks
     * double-pay.
     */
    SUBMITTING,
    /** Payment service confirmed it dispatched to Paymob. */
    SENT,
    /** Paymob confirmed money arrived; permanent debit is logged. */
    COMPLETED,
    /** Paymob rejected; balance was returned to Available via WITHDRAWAL_REVERSED entry. */
    FAILED,
    /** User cancelled while still PENDING (Decision 4.3). */
    CANCELLED
}
