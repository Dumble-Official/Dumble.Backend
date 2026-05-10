package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.Payout;
import lombok.Builder;
import lombok.Data;

/**
 * Wire shape returned by {@code GET /payment/withdrawals/by-caller-ref/{ref}}.
 * Wallet's reaper relies on the {@code status} string being one of:
 *   "Pending" | "Sent" | "Completed" | "Failed" | "NotFound"
 *
 * The endpoint returns a 404 when no row matches; Wallet's client maps that
 * to {@code null}, which the reaper treats as "NotFound".
 */
@Data
@Builder
public class PayoutLookupResponse {
    private String withdrawalId;
    private String status;
    private String failureReason;

    public static PayoutLookupResponse from(Payout p) {
        String enumName = p.getStatus().name();
        return PayoutLookupResponse.builder()
                .withdrawalId(p.getId().toString())
                .status(enumName.charAt(0) + enumName.substring(1).toLowerCase())
                .failureReason(p.getFailureReason())
                .build();
    }
}
