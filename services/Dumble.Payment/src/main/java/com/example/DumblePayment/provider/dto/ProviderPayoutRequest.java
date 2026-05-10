package com.example.DumblePayment.provider.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ProviderPayoutRequest {
    private UUID payoutId;          // our local row id
    private long amountCents;
    private String currency;
    private String destinationJson;
    private String destinationType;
    private String memo;            // shows up on the bank statement (Decision 6.2)
}
