package com.example.DumbleSubscription.client.dto;

import lombok.Data;

@Data
public class PromoValidationResponse {
    private boolean valid;
    private String reason;              // populated when !valid
    private long discountCents;         // applied amount when valid
    private String discountType;        // PERCENT | FIXED
}
