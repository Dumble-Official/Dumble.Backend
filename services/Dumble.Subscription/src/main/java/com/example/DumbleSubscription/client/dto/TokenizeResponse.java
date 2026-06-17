package com.example.DumbleSubscription.client.dto;

import java.util.UUID;

/** Payment's tokenize response — the saved payment-method id + echoed details. */
public record TokenizeResponse(UUID id, String token, String methodType,
                               String label, String cardBrand, String last4) {
}
