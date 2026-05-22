package com.example.DumblePayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Size(max = 32)
    private String destination;

    @Size(max = 500)
    private String reason;
}
