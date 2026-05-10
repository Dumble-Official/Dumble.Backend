package com.example.DumbleSubscription.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PayoutItem {
    private UUID escrowEntryId;
    private long amountCents;
    private String currency;
    private Instant paidOutAt;
    private String payoutRef;
}
