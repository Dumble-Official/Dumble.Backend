package com.example.DumbleWallet.client.dto;

import lombok.Data;

@Data
public class PaymentWithdrawalResponse {
    private String withdrawalId;        // Payment service's id, stored on WithdrawalRequest.payment_ref
    private String status;              // Pending | Sent | Failed (pre-Paymob ack)
    private String failureReason;
}
