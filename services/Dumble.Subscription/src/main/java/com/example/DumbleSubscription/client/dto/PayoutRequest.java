package com.example.DumbleSubscription.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class PayoutRequest {
    private UUID sellerId;
    private long amountCents;
    private String currency;
    private String destination;
    private String destinationType;
    private String callerReference;     // batch id (e.g. concatenated escrow ids)
    private String cohortKey;
    private String notes;
}
