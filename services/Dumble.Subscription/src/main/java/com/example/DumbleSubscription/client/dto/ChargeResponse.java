package com.example.DumbleSubscription.client.dto;

import lombok.Data;

@Data
public class ChargeResponse {
    private String chargeId;
    private String status;       // Pending | Succeeded | Failed
    private String providerRef;
}
