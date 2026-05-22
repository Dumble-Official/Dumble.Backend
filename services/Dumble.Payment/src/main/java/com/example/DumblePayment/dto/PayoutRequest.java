package com.example.DumblePayment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Wire shape Subscription's PaymentServiceClient sends to
 * {@code POST /payment/payouts}. Decision 6.2.
 *
 * <p>String size caps mirror the underlying {@code payouts} columns so an
 * over-length value 400s at validation rather than 500s on the JDBC insert.
 */
@Data
public class PayoutRequest {

    @NotNull
    private UUID sellerId;

    @Positive
    private long amountCents;

    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    private String currency;

    @NotNull
    private JsonNode destination;

    @Size(max = 64)
    private String destinationType;     // BANK_ACCOUNT | VODAFONE_CASH | ...

    @NotBlank
    @Size(max = 255)
    private String callerReference;     // Subscription's batch ref

    @Size(max = 64)
    private String cohortKey;           // Decision 6.2 — for the bank statement memo

    @Size(max = 1000)
    private String notes;
}
