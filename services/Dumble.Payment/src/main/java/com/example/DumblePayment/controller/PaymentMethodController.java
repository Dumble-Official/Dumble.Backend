package com.example.DumblePayment.controller;

import com.example.DumblePayment.dto.TokenizeRequest;
import com.example.DumblePayment.dto.TokenizeResponse;
import com.example.DumblePayment.service.PaymentMethodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService service;

    public PaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    /**
     * Decision 10.1 — frontend (or Subscription) registers an already-
     * tokenised handle. Whitelisted in {@code SecurityConfig} since the
     * frontend doesn't carry a system JWT and the value persisted is
     * already opaque to us.
     */
    @PostMapping("/tokenize")
    public ResponseEntity<TokenizeResponse> tokenize(@Valid @RequestBody TokenizeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(req));
    }
}
