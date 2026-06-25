package com.example.DumbleWallet.client.dto;

import java.util.UUID;

/**
 * Mirrors Payment's {@code CheckoutRequest} — initiates a Paymob hosted-checkout
 * (iframe) session. {@code callerReference} carries the purpose
 * ({@code "topup:<userId>"}) so the resulting charge.succeeded event credits the
 * right wallet.
 */
public record PaymentCheckoutRequest(
        UUID userId,
        long amountCents,
        String currency,
        String description,
        String callerReference,
        String email,
        String firstName,
        String lastName,
        String phone
) {
}
