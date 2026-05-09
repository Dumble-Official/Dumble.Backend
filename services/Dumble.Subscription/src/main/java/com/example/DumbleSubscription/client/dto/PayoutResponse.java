package com.example.DumbleSubscription.client.dto;

import lombok.Data;

@Data
public class PayoutResponse {
    private String payoutId;
    private String status;
    private String providerRef;
}
