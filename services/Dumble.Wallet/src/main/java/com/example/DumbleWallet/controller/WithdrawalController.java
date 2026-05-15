package com.example.DumbleWallet.controller;

import com.example.DumbleWallet.dto.CurrentUser;
import com.example.DumbleWallet.dto.WithdrawalRequestBody;
import com.example.DumbleWallet.dto.WithdrawalResponse;
import com.example.DumbleWallet.service.IdempotencyService;
import com.example.DumbleWallet.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Wallet PDF Decisions 4.3 + 6.1 — user-initiated withdrawal endpoints.
 */
@RestController
@RequestMapping("/wallet/me/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final IdempotencyService idempotencyService;

    public WithdrawalController(WithdrawalService withdrawalService,
                                IdempotencyService idempotencyService) {
        this.withdrawalService = withdrawalService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<WithdrawalResponse> create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawalRequestBody body) {
        // Orchestrated flow — Phase 1 commits a wallet debit + WithdrawalRequest
        // PENDING row BEFORE the Payment HTTP call. A failure after that point
        // must NOT release the idempotency claim, otherwise a retry under the
        // same key would re-debit the user.
        var cached = idempotencyService.executeOrchestrated(
                idempotencyKey,
                "POST /wallet/me/withdrawals",
                user.getId(),
                201,
                WithdrawalResponse.class,
                () -> withdrawalService.requestWithdrawal(user.getId(), body, idempotencyKey));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(cached.value());
    }

    @PostMapping("/{id}/cancel")
    public WithdrawalResponse cancel(@AuthenticationPrincipal CurrentUser user,
                                     @PathVariable("id") UUID id) {
        return withdrawalService.cancel(user.getId(), id);
    }

    @GetMapping
    public List<WithdrawalResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return withdrawalService.listForUser(user.getId());
    }
}
