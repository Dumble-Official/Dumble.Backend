package com.example.DumbleSubscription.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Mirrors Payment's CheckoutRequest — initiate a Paymob hosted-checkout (iframe) session. */
@Data
@Builder
@AllArgsConstructor
public class CheckoutRequest {
    private UUID userId;
    private long amountCents;
    private String currency;
    private String description;
    private String callerReference;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
}
