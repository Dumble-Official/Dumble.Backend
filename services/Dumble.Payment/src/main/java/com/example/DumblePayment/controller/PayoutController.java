package com.example.DumblePayment.controller;

import com.example.DumblePayment.domain.enums.PayoutType;
import com.example.DumblePayment.dto.PayoutLookupResponse;
import com.example.DumblePayment.dto.PayoutRequest;
import com.example.DumblePayment.dto.PayoutResponse;
import com.example.DumblePayment.service.IdempotencyService;
import com.example.DumblePayment.service.PayoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Decision 6.2 — system-initiated cohort payouts driven by Subscription's
 * weekly job. Same lifecycle as withdrawals but distinct ownership; events
 * land on {@code payment.payout.*} routing keys (Subscription listens) vs
 * {@code payment.withdrawal.*} (Wallet listens).
 */
@RestController
@RequestMapping("/payment")
public class PayoutController {

    private final PayoutService payoutService;
    private final IdempotencyService idempotencyService;

    public PayoutController(PayoutService payoutService, IdempotencyService idempotencyService) {
        this.payoutService = payoutService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/payouts")
    public ResponseEntity<PayoutResponse> payout(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody PayoutRequest req,
                                                 Authentication auth) {
        String actor = auth == null ? "subscription" : String.valueOf(auth.getPrincipal());
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /payment/payouts",
                req.getSellerId(),
                201,
                PayoutResponse.class,
                () -> payoutService.requestCohortPayout(req, actor));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(cached.value());
    }

    /** Symmetric to the withdrawal lookup — used by Subscription's recovery paths. */
    @GetMapping("/payouts/by-caller-ref/{ref}")
    public PayoutLookupResponse lookup(@PathVariable("ref") String callerReference) {
        return payoutService.lookupByCallerReference(PayoutType.COHORT_PAYOUT, callerReference);
    }
}
