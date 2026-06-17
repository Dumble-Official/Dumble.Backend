package com.example.DumbleWallet.controller;

import com.example.DumbleWallet.dto.AdminAdjustmentRequest;
import com.example.DumbleWallet.dto.CurrentUser;
import com.example.DumbleWallet.dto.WalletSummaryResponse;
import com.example.DumbleWallet.dto.WalletWriteResponse;
import com.example.DumbleWallet.service.AdminWalletService;
import com.example.DumbleWallet.service.AuditLogger;
import com.example.DumbleWallet.service.IdempotencyService;
import com.example.DumbleWallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Wallet PDF Decisions 5.3 + 6.1 — admin read + manual adjustment paths.
 * Method-level {@code @PreAuthorize} gates these to ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/wallet")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletController {

    private final WalletService walletService;
    private final AdminWalletService adminWalletService;
    private final IdempotencyService idempotencyService;
    private final AuditLogger auditLogger;

    public AdminWalletController(WalletService walletService,
                                 AdminWalletService adminWalletService,
                                 IdempotencyService idempotencyService,
                                 AuditLogger auditLogger) {
        this.walletService = walletService;
        this.adminWalletService = adminWalletService;
        this.idempotencyService = idempotencyService;
        this.auditLogger = auditLogger;
    }

    @GetMapping("/withdrawals")
    public java.util.List<com.example.DumbleWallet.dto.WithdrawalResponse> listWithdrawals(
            @RequestParam(required = false) com.example.DumbleWallet.domain.enums.WithdrawalStatus status,
            @RequestParam(required = false) UUID userId) {
        // W2 — admin forensic list of withdrawals by status and/or user.
        return adminWalletService.listWithdrawals(status, userId);
    }

    @PostMapping("/withdrawals/{id}/cancel")
    public com.example.DumbleWallet.dto.WithdrawalResponse cancelWithdrawal(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable UUID id) {
        // W1 — admin force-cancel, incl. SUBMITTING/SENT (asks Payment to abort
        // the payout, then reverses the wallet movement only on Payment's ack).
        return adminWalletService.cancelWithdrawal(id, admin == null ? null : admin.getId());
    }

    @GetMapping("/{userId}")
    public WalletSummaryResponse get(@AuthenticationPrincipal CurrentUser admin,
                                     @PathVariable UUID userId) {
        // Decision 5.3 — admin reads are themselves auditable.
        auditLogger.log(userId, "AdminWalletRead", "ADMIN",
                admin == null ? "" : admin.getId().toString(), null, null);
        return walletService.summary(userId);
    }

    @PostMapping("/{userId}/adjust")
    public ResponseEntity<WalletWriteResponse> adjust(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AdminAdjustmentRequest req) {
        UUID adminId = admin == null ? null : admin.getId();
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /admin/wallet/{userId}/adjust",
                userId,
                201,
                req,
                WalletWriteResponse.class,
                () -> adminWalletService.adjust(userId, adminId, req));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(cached.value());
    }
}
