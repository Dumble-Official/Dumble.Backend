package com.example.DumblePayment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

/**
 * Wire shape Subscription's PaymentServiceClient sends to
 * {@code POST /payment/payouts}. Decision 6.2.
 */
@Data
public class PayoutRequest {

    @NotNull
    private UUID sellerId;

    @Positive
    private long amountCents;

    private String currency;

    @NotNull
    private JsonNode destination;

    private String destinationType;     // BANK_ACCOUNT | VODAFONE_CASH | ...

    @NotBlank
    private String callerReference;     // Subscription's batch ref

    private String cohortKey;           // Decision 6.2 — for the bank statement memo

    private String notes;
}
