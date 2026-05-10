package com.example.DumbleSubscription.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class WalletDebitRequest {
    private UUID userId;
    private long amountCents;
    private String source;          // InAppSpend
    private String externalRef;     // subscription id
}
