package com.example.DumbleWallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Wallet PDF Decision 5.1 + 5.3 — admin manual adjustment with a mandatory
 * memo. Use a positive amount with {@code direction = CREDIT|DEBIT} rather
 * than a signed long so the audit log is unambiguous.
 */
@Data
public class AdminAdjustmentRequest {

    @NotNull
    @Positive(message = "amountCents must be positive; use direction=DEBIT to deduct")
    private Long amountCents;       // boxed so @NotNull rejects missing field

    @NotBlank
    @Pattern(regexp = "^(CREDIT|DEBIT)$", message = "direction must be CREDIT or DEBIT")
    private String direction;       // CREDIT | DEBIT

    /** Mandatory — every admin adjustment needs a documented reason (Decision 5.1). */
    @NotBlank
    @Size(max = 500)
    private String memo;
}
