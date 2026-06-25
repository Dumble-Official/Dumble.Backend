package com.example.DumbleWallet.controller;

import com.example.DumbleWallet.client.PaymentServiceClient;
import com.example.DumbleWallet.client.dto.PaymentChargeStatusResponse;
import com.example.DumbleWallet.client.dto.PaymentCheckoutRequest;
import com.example.DumbleWallet.client.dto.PaymentCheckoutResponse;
import com.example.DumbleWallet.dto.CurrentUser;
import com.example.DumbleWallet.dto.TopupRequest;
import com.example.DumbleWallet.dto.WalletCreditRequest;
import com.example.DumbleWallet.dto.WalletDebitRequest;
import com.example.DumbleWallet.dto.WalletEntryResponse;
import com.example.DumbleWallet.dto.WalletSummaryResponse;
import com.example.DumbleWallet.dto.WalletWriteResponse;
import com.example.DumbleWallet.repository.WalletEntryRepository;
import com.example.DumbleWallet.security.SystemTokenVerifier;
import com.example.DumbleWallet.service.IdempotencyService;
import com.example.DumbleWallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Wallet PDF Section 6.1 endpoints, plus the inter-service summary lookup
 * Subscription needs ({@code GET /wallet/{userId}/summary}). The system /
 * user split is enforced by Spring Security (see {@code SecurityConfig}) plus
 * inline {@link SystemTokenVerifier} checks on the system endpoints.
 */
@RestController
public class WalletController {

    private final WalletService walletService;
    private final WalletEntryRepository walletEntryRepository;
    private final IdempotencyService idempotencyService;
    private final SystemTokenVerifier systemTokenVerifier;
    private final PaymentServiceClient paymentServiceClient;

    public WalletController(WalletService walletService,
                            WalletEntryRepository walletEntryRepository,
                            IdempotencyService idempotencyService,
                            SystemTokenVerifier systemTokenVerifier,
                            PaymentServiceClient paymentServiceClient) {
        this.walletService = walletService;
        this.walletEntryRepository = walletEntryRepository;
        this.idempotencyService = idempotencyService;
        this.systemTokenVerifier = systemTokenVerifier;
        this.paymentServiceClient = paymentServiceClient;
    }

    /**
     * Wallet PDF Decision 3.1 — system-context credit (refund-only). Caller
     * must present a signed system JWT (Class B per Decision 6.4).
     */
    @PostMapping("/wallet/credit")
    public ResponseEntity<WalletWriteResponse> credit(
            @RequestHeader("Authorization") String auth,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WalletCreditRequest req) {
        systemTokenVerifier.verify(auth);
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /wallet/credit",
                req.getUserId(),
                201,
                req,
                WalletWriteResponse.class,
                () -> walletService.credit(req));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(cached.value());
    }

    /**
     * Wallet PDF Decision 4.1 — in-app spend. User-context: caller forwards
     * the user JWT. The body's {@code userId} must match the JWT user (or
     * the caller must be ADMIN) to prevent debiting someone else's wallet.
     */
    @PostMapping("/wallet/debit")
    public ResponseEntity<WalletWriteResponse> debit(
            @AuthenticationPrincipal CurrentUser principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WalletDebitRequest req) {
        if (principal == null || (!principal.getId().equals(req.getUserId()) && !"ADMIN".equals(principal.getUserType()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /wallet/debit",
                req.getUserId(),
                201,
                req,
                WalletWriteResponse.class,
                () -> walletService.debit(req));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(cached.value());
    }

    /** Wallet PDF Decision 7.1 — user-facing summary. */
    @GetMapping("/wallet/me/summary")
    public WalletSummaryResponse meSummary(@AuthenticationPrincipal CurrentUser user) {
        return walletService.summary(user.getId());
    }

    /**
     * Top-up: starts a Paymob hosted-checkout session for the authenticated user.
     * Returns the iframe URL the app loads in a WebView; the wallet is credited
     * asynchronously when Payment's charge.succeeded webhook lands (callerReference
     * {@code topup:<userId>}). Idempotency-Key dedupes the underlying charge.
     */
    @PostMapping("/wallet/me/topups")
    public ResponseEntity<PaymentCheckoutResponse> initTopup(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TopupRequest req) {
        PaymentCheckoutResponse resp = paymentServiceClient.createCheckout(
                idempotencyKey,
                new PaymentCheckoutRequest(
                        user.getId(),
                        req.getAmountCents(),
                        "EGP",
                        "Wallet top-up",
                        "topup:" + user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        null,
                        null));
        return ResponseEntity.ok(resp);
    }

    /** Poll a top-up's charge status (Pending / Succeeded / Failed) after checkout. */
    @GetMapping("/wallet/me/topups/{chargeId}")
    public PaymentChargeStatusResponse topupStatus(@AuthenticationPrincipal CurrentUser user,
                                                   @PathVariable String chargeId) {
        return paymentServiceClient.getChargeStatus(chargeId);
    }

    /**
     * Inter-service summary lookup. Subscription's pre-checkout balance check
     * uses this. Authentication is enforced by the system-token whitelist in
     * SecurityConfig + the per-call check below.
     */
    @GetMapping("/wallet/{userId}/summary")
    public WalletSummaryResponse summaryByUser(@PathVariable UUID userId,
                                               @RequestHeader("Authorization") String auth) {
        systemTokenVerifier.verify(auth);
        return walletService.summary(userId);
    }

    /** Wallet PDF Decision 6.1 / 7.1 — paginated full ledger view. */
    @GetMapping("/wallet/me/entries")
    public Page<WalletEntryResponse> meEntries(@AuthenticationPrincipal CurrentUser user,
                                               @RequestParam(value = "page", defaultValue = "0") int page,
                                               @RequestParam(value = "size", defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        return walletEntryRepository
                .findByWalletUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(page, safeSize))
                .map(WalletEntryResponse::from);
    }
}
