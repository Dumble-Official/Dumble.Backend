package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.Refund;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RefundResponse {
    private UUID refundId;
    private UUID chargeId;
    private long amountCents;
    private String destination;
    private String status;          // Pending | Succeeded | Failed
    private String providerRef;

    public static RefundResponse from(Refund r) {
        String status = r.getStatus().name();
        return RefundResponse.builder()
                .refundId(r.getId())
                .chargeId(r.getChargeId())
                .amountCents(r.getAmountCents())
                .destination(r.getDestination().name())
                .status(status.charAt(0) + status.substring(1).toLowerCase())
                .providerRef(r.getProviderRef())
                .build();
    }
}
