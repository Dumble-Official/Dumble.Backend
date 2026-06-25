package com.example.DumbleWallet.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of Payment's {@code ChargeResponse} the app needs to poll a top-up's
 * outcome. status is one of Pending / Succeeded / Failed / Reversed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentChargeStatusResponse(
        String chargeId,
        String status,
        long amountCents,
        String currency,
        String failureReason
) {
}
