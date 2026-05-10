package com.example.DumblePayment.domain.enums;

/**
 * Payment PDF Decision 3.1 + 3.3. The row exists in {@code PENDING} the
 * moment we accept the request; the provider call decides which terminal
 * state it ends in. {@code REVERSED} is reached only via a chargeback
 * webhook (Decision 5.1).
 */
public enum ChargeStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REVERSED
}
