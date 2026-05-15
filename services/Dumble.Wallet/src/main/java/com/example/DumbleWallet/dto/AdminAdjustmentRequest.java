package com.example.DumbleWallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Wallet PDF Decision 5.1 + 5.3 — admin manual adjustment with a mandatory
 * memo. Use a positive amount with {@code direction = CREDIT|DEBIT} rather
 * than a signed long so the audit log is unambiguous.
 */
@Data
public class AdminAdjustmentRequest {

    @NotNull
    private Long amountCents;       // boxed so @NotNull rejects missing field

    @NotBlank
    private String direction;       // CREDIT | DEBIT

    /** Mandatory — every admin adjustment needs a documented reason (Decision 5.1). */
    @NotBlank
    private String memo;
}
