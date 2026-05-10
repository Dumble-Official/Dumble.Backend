package com.example.DumbleWallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class WalletCreditRequest {

    @NotNull
    private UUID userId;

    @Positive
    private long amountCents;

    /** BAN_REFUND | CHARGEBACK | ADMIN_ADJUSTMENT | WITHDRAWAL_REVERSED. */
    @NotBlank
    private String source;

    private String externalRef;

    private String memo;
}
