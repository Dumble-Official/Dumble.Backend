package com.example.DumblePayment.controller;

import com.example.DumblePayment.dto.ChargeRequest;
import com.example.DumblePayment.dto.ChargeResponse;
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
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /payment/charges",
                req.getUserId(),
                201,
                ChargeResponse.class,
                () -> chargeService.charge(req, actor));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(cached.value());
    }

    @GetMapping("/charges/{id}")
    public ChargeResponse get(@PathVariable UUID id) {
        return chargeService.get(id);
    }
}
