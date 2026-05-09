package com.example.DumbleSubscription.client.dto;

import lombok.Data;

@Data
public class WalletSummaryResponse {
    private long availableCents;
    private long pendingCents;
    private String currency;
}
