package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.security.SystemTokenVerifier;
import com.example.DumbleSubscription.service.SellerLifecycleService;
import com.example.DumbleSubscription.service.WebhookEventRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Inbound HTTP webhooks from sibling services. Whitelisted in SecurityConfig
 * (no user JWT required) — instead each request must carry a signed system
 * JWT (Decision 8.4 Class B). Idempotency on caller-supplied event ID prevents
 * double-processing on retried delivery.
 *
 * merged_bug_001-run2 — saga pattern: claim the dedup row in its own short
 * tx, then run the lifecycle action; if the action throws, release the
 * claim so Payment's retry can re-process. Without this, a transient
 * downstream failure (e.g. Wallet 503 in RefundService.refundOnSellerBan)
 * left a permanent dedup record and the action was never re-attempted.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final SellerLifecycleService sellerLifecycleService;
    private final SystemTokenVerifier systemTokenVerifier;
    private final WebhookEventRecorder webhookEventRecorder;

    public WebhookController(SellerLifecycleService sellerLifecycleService,
                             SystemTokenVerifier systemTokenVerifier,
                             WebhookEventRecorder webhookEventRecorder) {
        this.sellerLifecycleService = sellerLifecycleService;
        this.systemTokenVerifier = systemTokenVerifier;
        this.webhookEventRecorder = webhookEventRecorder;
    }

    @PostMapping("/system/seller-frozen")
    public ResponseEntity<Void> onSellerFrozen(@RequestHeader("Authorization") String auth,
                                               @RequestHeader("X-Webhook-Event-Id") String eventId,
                                               @RequestBody JsonNode body) {
        Claims claims = systemTokenVerifier.verify(auth);
        UUID sellerId = requireUuid(body, "sellerId");
        String reason = body.path("reason").asText("under_review");
        runWithDedup(eventId, claims, "seller-frozen", body,
                () -> sellerLifecycleService.freeze(sellerId, reason));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/system/seller-unfrozen")
    public ResponseEntity<Void> onSellerUnfrozen(@RequestHeader("Authorization") String auth,
                                                 @RequestHeader("X-Webhook-Event-Id") String eventId,
                                                 @RequestBody JsonNode body) {
        Claims claims = systemTokenVerifier.verify(auth);
        UUID sellerId = requireUuid(body, "sellerId");
        String adminNote = body.path("adminNote").asText("");
        runWithDedup(eventId, claims, "seller-unfrozen", body,
                () -> sellerLifecycleService.unfreeze(sellerId, adminNote));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/system/seller-banned")
    public ResponseEntity<Void> onSellerBanned(@RequestHeader("Authorization") String auth,
                                               @RequestHeader("X-Webhook-Event-Id") String eventId,
                                               @RequestBody JsonNode body) {
        Claims claims = systemTokenVerifier.verify(auth);
        UUID sellerId = requireUuid(body, "sellerId");
        String reason = body.path("reason").asText("banned");
        runWithDedup(eventId, claims, "seller-banned", body,
                () -> sellerLifecycleService.ban(sellerId, reason));
        return ResponseEntity.accepted().build();
    }

    /**
     * Saga: claim dedup row → run action → on action failure, release the
     * claim so Payment's retry sees the eventId as fresh again.
     */
    private void runWithDedup(String eventId, Claims claims, String type, JsonNode body, Runnable action) {
        String source = claims.getIssuer();
        String summary = truncate(body == null ? null : body.toString(), 1900);
        boolean claimed = webhookEventRecorder.tryClaim(eventId, source, type, summary);
        if (!claimed) {
            return;     // already processed (winner committed) or in-flight peer
        }
        try {
            action.run();
        } catch (RuntimeException ex) {
            try {
                webhookEventRecorder.releaseClaim(eventId);
            } catch (RuntimeException compensateError) {
                // Best-effort; the dedup row will linger and require manual
                // cleanup before Payment can replay this eventId. Surface
                // BOTH causes so ops can see what happened.
                ex.addSuppressed(compensateError);
            }
            throw ex;
        }
    }

    /**
     * merged_bug_001-run2 — Jackson's {@code .get(String)} returns null on
     * missing fields, and {@code .asText()} on null NPEs into a 500. Use
     * {@code .path()} + an explicit blank check so a malformed payload fails
     * fast with a 400 BEFORE recordOrSkip claims an eventId.
     */
    private UUID requireUuid(JsonNode body, String field) {
        String raw = body == null ? "" : body.path(field).asText("");
        if (raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " is not a valid UUID");
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
