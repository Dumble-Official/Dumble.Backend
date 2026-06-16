package com.example.DumblePayment.controller;

import com.example.DumblePayment.dto.PaymentMethodResponse;
import com.example.DumblePayment.dto.TokenizeRequest;
import com.example.DumblePayment.dto.TokenizeResponse;
import com.example.DumblePayment.service.PaymentMethodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payment/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService service;

    public PaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    /**
     * Decision 10.1 — frontend (or Subscription) registers an already-
     * tokenised handle. Now gated by ROLE_SERVICE in {@link
     * com.example.DumblePayment.config.SecurityConfig}: the userId/token
     * binding is the security-relevant claim, so frontends must call through
     * a gateway that mints a system JWT vouching for the userId. Without this
     * gate, an unauthenticated attacker could re-bind their own Paymob token
     * to any victim's userId (auto-renewal then bills the attacker's card
     * while granting service to the victim's account).
     */
    @PostMapping("/tokenize")
    public ResponseEntity<TokenizeResponse> tokenize(@Valid @RequestBody TokenizeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(req));
    }

    /** List a user's saved (non-deleted) payment methods. */
    @GetMapping
    public List<PaymentMethodResponse> list(@RequestParam UUID userId) {
        return service.listActive(userId);
    }

    /** Soft-delete a saved payment method. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
