package com.example.DumblePayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * The frontend has already tokenised with Paymob (Decision 10.1) and just
 * registers the opaque handle here for future reuse. Payment never sees
 * raw card numbers.
 */
@Data
public class TokenizeRequest {
    @NotNull
    private UUID userId;

    @NotBlank
    private String token;

    /** CARD | WALLET | OTHER. */
    @NotBlank
    private String methodType;

    private String label;
    private String cardBrand;
    private String last4;
}
