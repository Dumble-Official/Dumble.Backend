package com.example.DumbleSubscription.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BundleCheckoutRequest {
    @NotNull
    private UUID bundleId;

    private String paymentMethodToken;     // null when paying from wallet
    /** CARD | WALLET | OTHER — gates whether renewals can auto-charge per Decision 7.2. */
    private String paymentMethodType;
    private boolean useWalletBalance;
    private String promoCode;
}
