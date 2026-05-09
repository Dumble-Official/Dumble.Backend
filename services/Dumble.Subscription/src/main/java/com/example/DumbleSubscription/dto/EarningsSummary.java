package com.example.DumbleSubscription.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EarningsSummary {
    private long pendingCents;
    private long paidCents;
    private long lifetimeCents;
}
