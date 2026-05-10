package com.example.DumblePayment.provider.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderRefundResponse {
    public enum Outcome { PENDING, SUCCEEDED, FAILED }

    private Outcome outcome;
    private String providerRef;
    private String failureReason;
}
