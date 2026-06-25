package com.example.DumblePayment.provider.dto;

/**
 * Result of creating a Paymob hosted-checkout session: the URL the app loads in
 * a WebView, the payment token it is keyed by, and Paymob's order id (stored as
 * the charge's providerRef so the webhook can be reconciled back to the charge).
 */
public record ProviderHostedCheckoutResponse(
        String iframeUrl,
        String paymentToken,
        String paymobOrderId
) {
}
