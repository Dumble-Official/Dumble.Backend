package com.example.DumblePayment.controller;

import com.example.DumblePayment.dto.ChargeRequest;
import com.example.DumblePayment.dto.ChargeResponse;
import com.example.DumblePayment.dto.CheckoutRequest;
import com.example.DumblePayment.dto.CheckoutResponse;
import com.example.DumblePayment.service.ChargeService;
import com.example.DumblePayment.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payment")
public class ChargeController {

    private final ChargeService chargeService;
    private final IdempotencyService idempotencyService;

    public ChargeController(ChargeService chargeService, IdempotencyService idempotencyService) {
        this.chargeService = chargeService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/charges")
    public ResponseEntity<ChargeResponse> charge(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody ChargeRequest req,
                                                 Authentication auth) {
        String actor = auth == null ? "unknown" : String.valueOf(auth.getPrincipal());
        // Orchestrated — persistPending commits a Charge row BEFORE the Paymob
        // HTTP call. Releasing the dedup claim on a non-ProviderException would
        // let a retry dispatch a SECOND charge to Paymob.
        var cached = idempotencyService.executeOrchestrated(
                idempotencyKey,
                "POST /payment/charges",
                req.getUserId(),
                201,
                req,
                ChargeResponse.class,
                () -> chargeService.charge(req, actor));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(cached.value());
    }

    /**
     * Initiate an interactive Paymob hosted-checkout (iframe) session. Brokered by
     * a user-facing service (Wallet top-up, Subscription upgrade/bundle) that mints
     * a system JWT; returns the iframe URL the app loads in a WebView. Idempotent
     * on the key so a retry replays the same session instead of creating a new one.
     */
    @PostMapping("/checkouts")
    public ResponseEntity<CheckoutResponse> checkout(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody CheckoutRequest req,
                                                     Authentication auth) {
        String actor = auth == null ? "unknown" : String.valueOf(auth.getPrincipal());
        var cached = idempotencyService.executeOrchestrated(
                idempotencyKey,
                "POST /payment/checkouts",
                req.getUserId(),
                201,
                req,
                CheckoutResponse.class,
                () -> chargeService.createCheckout(req, actor));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(cached.value());
    }

    @GetMapping("/charges/{id}")
    public ChargeResponse get(@PathVariable UUID id) {
        return chargeService.get(id);
    }
}
