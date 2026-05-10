package com.example.DumbleSubscription.exception;

/**
 * Thrown when a request is structurally valid but violates a domain rule —
 * e.g. trying to create a duplicate active subscription, withdraw below
 * minimum, exceed quota. Returns HTTP 422.
 */
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
