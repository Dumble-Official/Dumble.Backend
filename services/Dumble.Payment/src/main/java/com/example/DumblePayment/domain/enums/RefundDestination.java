package com.example.DumblePayment.domain.enums;

/**
 * Payment PDF Decision 5.2. Default path for v1 callers is {@code WALLET}:
 * Payment skips Paymob and the caller is expected to credit the user's
 * wallet separately. {@code ORIGINAL_METHOD} is reserved for chargebacks
 * and admin force-refunds.
 */
public enum RefundDestination {
    WALLET,
    ORIGINAL_METHOD
}
