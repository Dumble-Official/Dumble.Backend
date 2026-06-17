package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.client.PaymentServiceClient;
import com.example.DumbleSubscription.client.dto.TokenizeRequest;
import com.example.DumbleSubscription.client.dto.TokenizeResponse;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.dto.PaymentMethodRegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * #5 — user-facing card tokenize passthrough. Payment's tokenize endpoint is
 * gated to ROLE_SERVICE and trusts the userId in its body, so a frontend cannot
 * call it directly with a user JWT. This endpoint validates the user's JWT, sets
 * userId from the AUTHENTICATED principal (never from client input), and calls
 * Payment with a minted system token — so a user can only ever register a card
 * against their own account.
 */
@RestController
public class PaymentMethodController {

    private final PaymentServiceClient paymentServiceClient;

    public PaymentMethodController(PaymentServiceClient paymentServiceClient) {
        this.paymentServiceClient = paymentServiceClient;
    }

    @PostMapping("/me/payment-methods")
    public ResponseEntity<TokenizeResponse> register(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody PaymentMethodRegisterRequest req) {
        TokenizeResponse resp = paymentServiceClient.tokenize(new TokenizeRequest(
                user.getId(), req.token(), req.methodType(), req.label(), req.cardBrand(), req.last4()));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
