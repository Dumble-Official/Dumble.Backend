package com.example.DumblePayment.provider.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ProviderChargeRequest {
    private UUID chargeId;          // our local row id, used as caller_reference at the provider
    private UUID userId;
    private long amountCents;
    private String currency;
    private String paymentMethodToken;
    private String description;
}
