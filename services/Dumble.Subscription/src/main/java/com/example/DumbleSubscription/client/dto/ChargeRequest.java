package com.example.DumbleSubscription.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ChargeRequest {
    private UUID userId;
    private long amountCents;
    private String currency;
    private String paymentMethodToken;
    private String description;
    private String callerReference;     // Subscription's row id for matching events back
}
