package com.example.DumblePayment.provider.dto;

/**
 * Request to create a Paymob hosted-checkout (iframe) session. Unlike
 * {@link ProviderChargeRequest} — which charges a previously-tokenized payment
 * method server-side — this drives the interactive flow where the user enters
 * their card on Paymob's hosted page inside an in-app WebView.
 *
 * <p>{@code merchantOrderId} must be unique per attempt (Paymob rejects a
 * duplicate); callers pass the charge id. Billing fields are required by
 * Paymob's payment_keys API — callers supply what they know and the provider
 * fills safe placeholders for the rest.
 */
public record ProviderHostedCheckoutRequest(
        long amountCents,
        String currency,
        String merchantOrderId,
        String email,
        String firstName,
        String lastName,
        String phone
) {
}
