package com.example.DumbleWallet.client.dto;

import lombok.Data;

/**
 * Status snapshot returned by Payment for a withdrawal lookup keyed by our
 * {@code callerReference}. Used by {@link com.example.DumbleWallet.scheduler.WithdrawalReaperJob}
 * to decide what to do with a stuck PENDING / SUBMITTING row.
 *
 * Possible {@code status} values mirror Payment's own withdrawal lifecycle:
 *   "Pending"   — Paymob hasn't confirmed yet; leave the row alone
 *   "Sent"      — Payment dispatched to Paymob; we should be at SENT, advance via markSent
 *   "Completed" — Paymob confirmed; advance via completeFromWebhook
 *   "Failed"    — Paymob rejected; reverse via reverseAndFail
 *   "NotFound"  — Payment has no record (Wallet's HTTP call to Payment never landed)
 */
@Data
public class PaymentWithdrawalLookupResponse {
    private String withdrawalId;       // Payment's id, populated when known
    private String status;             // Pending | Sent | Completed | Failed | NotFound
    private String failureReason;
}
