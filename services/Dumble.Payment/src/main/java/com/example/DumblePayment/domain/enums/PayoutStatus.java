package com.example.DumblePayment.domain.enums;

/**
 * Payment PDF Decision 6.3 — Paymob payouts are async. Payment returns
 * {@code PENDING} immediately, the actual {@code SENT} / {@code COMPLETED} /
 * {@code FAILED} state arrives via webhook hours later.
 */
public enum PayoutStatus {
    PENDING,
    SENT,
    COMPLETED,
    FAILED
}
