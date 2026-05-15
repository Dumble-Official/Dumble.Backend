package com.example.DumblePayment.controller;

import com.example.DumblePayment.dto.RefundRequest;
import com.example.DumblePayment.dto.RefundResponse;
import com.example.DumblePayment.service.IdempotencyService;
import com.example.DumblePayment.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
public class RefundController {

    private final RefundService refundService;
    private final IdempotencyService idempotencyService;

    public RefundController(RefundService refundService, IdempotencyService idempotencyService) {
        this.refundService = refundService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponse> refund(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody RefundRequest req,
                                                 Authentication auth) {
        String actor = auth == null ? "unknown" : String.valueOf(auth.getPrincipal());
        // Orchestrated — persistPending commits a Refund row BEFORE the
        // ORIGINAL_METHOD provider call. Don't release the dedup claim on
        // non-ProviderException failure, otherwise a retry creates a second
        // refund row.
        var cached = idempotencyService.executeOrchestrated(
                idempotencyKey,
                "POST /payment/refunds",
                null,
                201,
                RefundResponse.class,
                () -> refundService.refund(req, actor));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(cached.value());
    }
}
