package com.example.DumbleSubscription.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MyPlanResponse {
    private String planCode;
    private String status;
    private Instant startedAt;
    private Instant currentPeriodEnd;
    private Instant cancelScheduledAt;
}
