package com.example.DumblePayment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * String length caps mirror the underlying {@code charges} columns so a
 * payload that's too long surfaces as a 400 from the validator rather than
 * a 500 from Hibernate's "value too long for column" exception at flush.
 */
@Data
public class ChargeRequest {
    @NotNull
    private UUID userId;

    @Positive
    private long amountCents;

    /** EGP only in v1 — Decision 2.1. */
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    private String currency;

    @Size(max = 255)
    private String paymentMethodToken;

    @Size(max = 500)
    private String description;

    /** Caller's stable reference — Decision 3.1. */
    @Size(max = 255)
    private String callerReference;
}
