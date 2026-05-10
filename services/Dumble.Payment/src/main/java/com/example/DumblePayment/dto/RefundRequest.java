package com.example.DumblePayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class RefundRequest {
    @NotNull
    private UUID chargeId;

    @Positive
    private long amountCents;

    /** WALLET (default v1 path) | ORIGINAL_METHOD — Decision 5.2. */
    @NotBlank
    private String destination;

    private String reason;
}
