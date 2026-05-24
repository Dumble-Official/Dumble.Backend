package com.example.DumbleWallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * String size caps mirror the underlying wallet_entries columns so an
 * over-length value 400s at the validator instead of 500 on the JDBC insert.
 */
@Data
public class WalletCreditRequest {

    @NotNull
    private UUID userId;

    @Positive
    private long amountCents;

    /** BAN_REFUND | CHARGEBACK | ADMIN_ADJUSTMENT | WITHDRAWAL_REVERSED. */
    @NotBlank
    @Size(max = 40)
    private String source;

    @Size(max = 255)
    private String externalRef;

    @Size(max = 500)
    private String memo;
}
