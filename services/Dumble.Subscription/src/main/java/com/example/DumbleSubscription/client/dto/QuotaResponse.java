package com.example.DumbleSubscription.client.dto;

import lombok.Data;

@Data
public class QuotaResponse {
    private int activeBundleCount;
    private Integer maxAllowed;     // null = unlimited; configured uniformly per Decision 3.1
    private boolean canCreateMore;
}
