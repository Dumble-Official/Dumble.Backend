package com.example.DumbleSubscription.client.dto;

import lombok.Data;

@Data
public class WalletDebitResponse {
    private String walletEntryId;
    private long newBalanceCents;
}
