package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.Charge;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape used by callers (Subscription's Pending-charge handling reads
 * status as the title-cased "Pending"/"Succeeded"/"Failed" string per
 * Decision 3.1, so we map the enum to that exact form here).
 */
@Data
@Builder
public class ChargeResponse {

    private UUID chargeId;
    private String status;          // "Pending" | "Succeeded" | "Failed" | "Reversed"
    private String providerRef;
    private long amountCents;
    private String currency;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static ChargeResponse from(Charge c) {
        return ChargeResponse.builder()
                .chargeId(c.getId())
                .status(toWireStatus(c.getStatus().name()))
                .providerRef(c.getProviderRef())
                .amountCents(c.getAmountCents())
                .currency(c.getCurrency())
                .failureReason(c.getFailureReason())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private static String toWireStatus(String enumName) {
        // PENDING → Pending, SUCCEEDED → Succeeded, etc.
        return enumName.charAt(0) + enumName.substring(1).toLowerCase();
    }
}
