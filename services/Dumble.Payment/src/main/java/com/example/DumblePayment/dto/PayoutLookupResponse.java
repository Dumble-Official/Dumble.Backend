package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.Payout;
import com.example.DumblePayment.domain.enums.PayoutType;
import lombok.Builder;
import lombok.Data;

/**
 * Wire shape returned by {@code GET /payment/withdrawals/by-caller-ref/{ref}}
 * AND {@code GET /payment/payouts/by-caller-ref/{ref}}. Wallet's reaper relies
 * on the {@code status} string being one of:
 *   "Pending" | "Sent" | "Completed" | "Failed" | "NotFound"
 *
 * <p>The endpoint returns a 404 when no row matches; Wallet's client maps that
 * to {@code null}, which the reaper treats as "NotFound".
 *
 * <p>Both {@code withdrawalId} and {@code payoutId} fields are populated for
 * symmetry with {@link PayoutResponse} (which has the same dual shape). A
 * cohort-payout lookup populates {@code payoutId} and leaves {@code withdrawalId}
 * null; a user-withdrawal lookup does the reverse. Callers consuming only one
 * of the two names continue to work because the OTHER field is just null —
 * but new callers can pick the field that matches their domain without
 * having to know about the historical shared DTO.
 */
@Data
@Builder
public class PayoutLookupResponse {
    /** Populated for USER_WITHDRAWAL; null for COHORT_PAYOUT. */
    private String withdrawalId;
    /** Populated for COHORT_PAYOUT; null for USER_WITHDRAWAL. */
    private String payoutId;
    private String status;
    private String failureReason;

    public static PayoutLookupResponse from(Payout p) {
        String enumName = p.getStatus().name();
        String wireStatus = enumName.charAt(0) + enumName.substring(1).toLowerCase();
        var builder = PayoutLookupResponse.builder()
                .status(wireStatus)
                .failureReason(p.getFailureReason());
        if (p.getType() == PayoutType.USER_WITHDRAWAL) {
            builder.withdrawalId(p.getId().toString());
        } else {
            builder.payoutId(p.getId().toString());
        }
        return builder.build();
    }
}
