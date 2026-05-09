package com.example.DumbleSubscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlanUpgradeRequest {
    @NotBlank
    private String paymentMethodToken;
}
