package com.example.DumbleWallet.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class WithdrawalRequestBody {

    @Positive
    private long amountCents;

    /**
     * Wallet PDF Decision 2.2 — destination is JSON so different rails (bank
     * account, mobile-wallet number, ...) can carry their own shape without a
     * schema change per rail. Payment service interprets the contents.
     */
    @NotNull
    private JsonNode destination;
}
