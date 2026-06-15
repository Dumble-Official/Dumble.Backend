package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.PaymentMethodToken;

import java.time.Instant;
import java.util.UUID;

/** A saved payment method, without the raw token handle (which is never returned). */
public record PaymentMethodResponse(
        UUID id,
        String methodType,
        String label,
        String cardBrand,
        String last4,
        Instant createdAt) {

    public static PaymentMethodResponse from(PaymentMethodToken t) {
        return new PaymentMethodResponse(
                t.getId(),
                t.getMethodType() == null ? null : t.getMethodType().name(),
                t.getLabel(),
                t.getCardBrand(),
                t.getLast4(),
                t.getCreatedAt());
    }
}
