package com.example.DumbleSubscription.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirrors Payment's CheckoutResponse: the iframe URL + the charge id to poll. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckoutResponse {
    private String chargeId;
    private String status;
    private String iframeUrl;
    private String providerRef;
}
