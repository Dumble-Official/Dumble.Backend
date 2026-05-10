package com.example.DumblePayment.domain.enums;

/**
 * Payment PDF Decision 6.1 + 6.2 — same lifecycle, different ownership:
 *
 * <ul>
 *   <li>{@code USER_WITHDRAWAL} — driven by Wallet, user clicked "Withdraw".
 *   <li>{@code COHORT_PAYOUT} — driven by Subscription, weekly seller batch.
 * </ul>
 *
 * The tag determines which event Payment publishes when the lifecycle
 * advances ({@code WithdrawalCompleted}/{@code WithdrawalFailed} vs
 * {@code PayoutCompleted}/{@code PayoutFailed}) and which service consumes it.
 */
public enum PayoutType {
    USER_WITHDRAWAL,
    COHORT_PAYOUT
}
