package com.example.DumbleWallet.client.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PaymentWithdrawalRequest {
    private UUID userId;
    private long amountCents;
    private String currency;
    private JsonNode destination;
    private String callerReference;     // Wallet's withdrawal id
}
