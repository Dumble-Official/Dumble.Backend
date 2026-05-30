package com.example.DumbleWallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Size(max = 40)
    private String source;

    /** Subscription id (or whatever drove the debit) — for audit only. */
    @Size(max = 255)
    private String externalRef;
}
