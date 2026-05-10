package com.example.DumblePayment.controller;

import com.example.DumblePayment.domain.enums.PayoutType;
import com.example.DumblePayment.dto.PayoutLookupResponse;
import com.example.DumblePayment.dto.PayoutResponse;
import com.example.DumblePayment.dto.WithdrawalRequest;
import com.example.DumblePayment.service.IdempotencyService;
import com.example.DumblePayment.service.PayoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
public class WithdrawalController {

    private final PayoutService payoutService;
    private final IdempotencyService idempotencyService;

    public WithdrawalController(PayoutService payoutService, IdempotencyService idempotencyService) {
        this.payoutService = payoutService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<PayoutResponse> withdraw(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @Valid @RequestBody WithdrawalRequest req,
                                                   Authentication auth) {
        String actor = auth == null ? "wallet" : String.valueOf(auth.getPrincipal());
        // Orchestrated — persistPending commits a Payout row BEFORE the Paymob
        // call. Releasing on non-ProviderException would let a retry dispatch
        // a SECOND payout to Paymob.
        var cached = idempotencyService.executeOrchestrated(
                idempotencyKey,
                "POST /payment/withdrawals",
                req.getUserId(),
                201,
                PayoutResponse.class,
                () -> payoutService.requestWithdrawal(req, actor));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(cached.value());
    }

    /**
     * Backs Wallet's reaper (Wallet PDF Decision 4.4 + reaper job). Returns
     * 404 when no row matches; Wallet's client maps that to a "NotFound"
     * status string.
     */
    @GetMapping("/withdrawals/by-caller-ref/{ref}")
    public PayoutLookupResponse lookup(@PathVariable("ref") String callerReference) {
        return payoutService.lookupByCallerReference(PayoutType.USER_WITHDRAWAL, callerReference);
    }
}
