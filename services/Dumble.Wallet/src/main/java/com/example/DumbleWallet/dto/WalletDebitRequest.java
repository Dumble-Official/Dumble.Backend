package com.example.DumbleWallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class WalletDebitRequest {

    @NotNull
    private UUID userId;

    @Positive
    private long amountCents;

    /** Currently only IN_APP_SPEND — Decision 4.1. */
    @NotBlank
    private String source;

    /** Subscription id (or whatever drove the debit) — for audit only. */
    private String externalRef;
}
