package com.example.DumblePayment.provider.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderPayoutResponse {
    public enum Outcome { PENDING, SENT, FAILED }

    private Outcome outcome;
    private String providerRef;
    private String failureReason;
}
