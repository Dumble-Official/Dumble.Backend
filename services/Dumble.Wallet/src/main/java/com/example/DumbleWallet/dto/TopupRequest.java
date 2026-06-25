package com.example.DumbleWallet.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Body for POST /wallet/me/topups — the amount (in cents) to add to the wallet. */
@Data
public class TopupRequest {
    @Positive
    private long amountCents;
}
