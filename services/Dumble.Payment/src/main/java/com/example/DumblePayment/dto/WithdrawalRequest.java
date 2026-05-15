package com.example.DumblePayment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

/**
 * Wire shape Wallet's WalletServiceClient sends to {@code POST /payment/withdrawals}.
 * Decision 6.1.
 */
@Data
public class WithdrawalRequest {

    @NotNull
    private UUID userId;

    @Positive
    private long amountCents;

    private String currency;

    @NotNull
    private JsonNode destination;

    /** Wallet's withdrawal-request id — used to look up status by caller-ref. */
    @NotBlank
    private String callerReference;
}
