package com.example.DumbleWallet.dto;

import com.example.DumbleWallet.domain.WithdrawalRequest;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WithdrawalResponse {

    private UUID id;
    private long amountCents;
    private String currency;
    private String status;
    private String paymentRef;
    private String failureReason;
    private Instant createdAt;
    private Instant completedAt;

    public static WithdrawalResponse from(WithdrawalRequest w) {
        return WithdrawalResponse.builder()
                .id(w.getId())
                .amountCents(w.getAmountCents())
                .currency(w.getCurrency())
                .status(w.getStatus().name())
                .paymentRef(w.getPaymentRef())
                .failureReason(w.getFailureReason())
                .createdAt(w.getCreatedAt())
                .completedAt(w.getCompletedAt())
                .build();
    }
}
