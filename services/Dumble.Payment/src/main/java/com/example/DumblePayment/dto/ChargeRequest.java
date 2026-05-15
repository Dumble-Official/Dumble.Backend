package com.example.DumblePayment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class ChargeRequest {
    @NotNull
    private UUID userId;

    @Positive
    private long amountCents;

    /** EGP only in v1 — Decision 2.1. */
    private String currency;

    private String paymentMethodToken;

    private String description;

    /** Caller's stable reference — Decision 3.1. */
    private String callerReference;
}
