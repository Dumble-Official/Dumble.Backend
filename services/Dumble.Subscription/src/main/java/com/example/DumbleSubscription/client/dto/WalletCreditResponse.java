package com.example.DumbleSubscription.client.dto;

import lombok.Data;

@Data
public class WalletCreditResponse {
    private String walletEntryId;
    private long newBalanceCents;
}
