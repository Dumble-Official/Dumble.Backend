package com.example.DumbleSubscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlanUpgradeRequest {
    @NotBlank
    private String paymentMethodToken;

    /**
     * CARD | WALLET | OTHER. Mirrors BundleCheckoutRequest so the renewal
     * path can honour Decision 7.2 (no silent wallet auto-charge). Optional
     * — falls back to OTHER, which forces a renewal-prompt rather than a
     * silent re-charge.
     */
    private String paymentMethodType;
}
