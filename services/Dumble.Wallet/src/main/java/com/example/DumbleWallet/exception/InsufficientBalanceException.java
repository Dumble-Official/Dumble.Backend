package com.example.DumbleWallet.exception;

/**
 * Wallet PDF Decision 4.1 — debit request exceeds available balance. Mapped to
 * HTTP 400 InsufficientBalance so Subscription can fall back to a Payment
 * charge ("Subscription should fall back to Payment charge").
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
