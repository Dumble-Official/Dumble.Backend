package com.example.DumblePayment.provider.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderRefundRequest {
    private String chargeProviderRef;
    private long amountCents;
    private String reason;
}
