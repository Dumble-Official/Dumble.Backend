package com.example.DumbleWallet.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors Payment's {@code CheckoutResponse}: the iframe URL + the charge id to poll. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCheckoutResponse(
        String chargeId,
        String status,
        String iframeUrl,
        String providerRef
) {
}
