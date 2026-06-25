package com.example.DumblePayment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Hosted-checkout session: the charge we created (PENDING), the iframe URL the
 * app loads in a WebView, and Paymob's order id. The app polls
 * {@code GET /payment/charges/{chargeId}} (via its broker) to learn the final
 * status after the webhook lands. No-arg + all-args ctors keep the idempotency
 * layer's JSON replay round-trip working.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private UUID chargeId;
    private String status;
    private String iframeUrl;
    private String providerRef;
}
