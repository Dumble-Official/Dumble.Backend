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
                WalletWriteResponse.class,
                () -> adminWalletService.adjust(userId, adminId, req));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(cached.value());
    }
}
