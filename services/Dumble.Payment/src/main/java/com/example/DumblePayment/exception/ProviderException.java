package com.example.DumblePayment.exception;

/**
 * Wraps any failure thrown by an {@link com.example.DumblePayment.provider.IPaymentProvider}
 * implementation — Paymob HTTP error, signature mismatch on inbound webhook,
 * SDK exception, etc. Lets the service layer translate to a Failed terminal
 * state without leaking provider-specific exception types.
 */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
