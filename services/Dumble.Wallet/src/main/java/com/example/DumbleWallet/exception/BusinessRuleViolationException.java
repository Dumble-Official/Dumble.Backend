package com.example.DumbleWallet.exception;

/** Maps to HTTP 422 — the request was syntactically valid but violates a domain rule. */
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
