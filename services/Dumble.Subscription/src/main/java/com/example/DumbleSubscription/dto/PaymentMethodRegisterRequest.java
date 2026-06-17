package com.example.DumbleSubscription.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * What the frontend sends to POST /api/me/payment-methods. There is deliberately
 * NO userId field — it is taken from the caller's JWT so a user can only register
 * a card against their own account.
 */
public record PaymentMethodRegisterRequest(
        @NotBlank String token,
        @NotBlank String methodType,
        String label,
        String cardBrand,
        String last4) {
}
