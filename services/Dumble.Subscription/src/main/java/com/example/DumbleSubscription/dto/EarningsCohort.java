package com.example.DumbleSubscription.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class EarningsCohort {
    private String cohortKey;
    private long amountCents;
    private Instant scheduledAt;
    private int deferredCount;
    private String reason;
}
