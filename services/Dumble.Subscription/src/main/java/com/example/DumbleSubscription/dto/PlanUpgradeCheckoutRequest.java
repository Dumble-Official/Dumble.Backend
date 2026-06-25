package com.example.DumbleSubscription.dto;

import lombok.Data;

/**
 * Body for POST /me/plan/upgrade/checkout — optional billing details forwarded
 * to Paymob's payment_keys. All fields optional; the provider fills placeholders.
 */
@Data
public class PlanUpgradeCheckoutRequest {
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
}
