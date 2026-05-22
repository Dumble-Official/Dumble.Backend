package com.example.DumblePayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @Size(max = 100)
    private String label;

    @Size(max = 32)
    private String cardBrand;

    /**
     * Last 4 digits of the card. Must be exactly 4 digits when present.
     * Validated up-front so an over-length value (e.g. 5 chars) fails as a
     * 400 instead of crashing the JDBC VARCHAR(4) insert with a 500.
     */
    @Size(min = 4, max = 4, message = "last4 must be exactly 4 characters when present")
    @Pattern(regexp = "^[0-9]{4}$", message = "last4 must be 4 digits")
    private String last4;
}
