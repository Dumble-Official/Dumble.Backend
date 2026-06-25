package com.example.DumblePayment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Initiate an interactive Paymob hosted-checkout (iframe) session. Unlike
 * {@link ChargeRequest} there is no payment-method token — the user enters
 * their card on Paymob's hosted page in the app's WebView, and the outcome
 * arrives via webhook.
 *
 * <p>{@code callerReference} carries the PURPOSE (e.g. {@code "topup:<userId>"},
 * {@code "platform-sub:<userId>"}, {@code "bundle:<id>"}); it is emitted on the
 * resulting ChargeSucceeded/ChargeFailed event so the right consumer (Wallet
 * top-up credit, Subscription activation) can act. Billing fields populate
 * Paymob's mandatory payment_keys billing_data.
 */
@Data
public class CheckoutRequest {
    @NotNull
    private UUID userId;

    @Positive
    private long amountCents;

    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    private String currency;

    @Size(max = 500)
    private String description;

    @Size(max = 255)
    private String callerReference;

    // Billing data for Paymob's payment_keys (optional — placeholders filled in).
    @Size(max = 255)
    private String email;
    @Size(max = 100)
    private String firstName;
    @Size(max = 100)
    private String lastName;
    @Size(max = 40)
    private String phone;
}
