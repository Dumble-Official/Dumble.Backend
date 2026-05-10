package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.Payout;
import com.example.DumblePayment.domain.enums.PayoutType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayoutResponse {

    /** Payment's payout id; named differently on the wire depending on type. */
    private String withdrawalId;        // populated for USER_WITHDRAWAL
    private String payoutId;            // populated for COHORT_PAYOUT
    private String status;              // Pending | Sent | Completed | Failed
    private String providerRef;
    private String failureReason;

    public static PayoutResponse from(Payout p) {
        String enumName = p.getStatus().name();
        String wireStatus = enumName.charAt(0) + enumName.substring(1).toLowerCase();
        var b = PayoutResponse.builder()
                .status(wireStatus)
                .providerRef(p.getProviderRef())
                .failureReason(p.getFailureReason());
        if (p.getType() == PayoutType.USER_WITHDRAWAL) {
            b.withdrawalId(p.getId().toString());
        } else {
            b.payoutId(p.getId().toString());
        }
        return b.build();
    }
}
