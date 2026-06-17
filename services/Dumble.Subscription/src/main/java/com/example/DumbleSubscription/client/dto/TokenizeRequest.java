package com.example.DumbleSubscription.client.dto;

import java.util.UUID;

/**
 * Body for Payment's POST /api/payment/payment-methods/tokenize. Payment reads
 * userId from here, so the passthrough MUST set it from the authenticated
 * principal — never from client input.
 */
public record TokenizeRequest(UUID userId, String token, String methodType,
                              String label, String cardBrand, String last4) {
}
