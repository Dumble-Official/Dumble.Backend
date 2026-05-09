package com.example.DumbleSubscription.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class WalletCreditRequest {
    private UUID userId;
    private long amountCents;
    private String source;          // BanRefund | Chargeback | AdminAdjustment
    private String externalRef;
    private String memo;
}
