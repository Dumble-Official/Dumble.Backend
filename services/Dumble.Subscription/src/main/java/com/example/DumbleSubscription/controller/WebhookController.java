package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.InboundWebhookEvent;
import com.example.DumbleSubscription.repository.InboundWebhookEventRepository;
import com.example.DumbleSubscription.security.SystemTokenVerifier;
import com.example.DumbleSubscription.service.SellerLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound HTTP webhooks from sibling services. Whitelisted in SecurityConfig
 * (no user JWT required) — instead each request must carry a signed system
 * JWT (Decision 8.4 Class B). Idempotency on caller-supplied event ID prevents
 * double-processing on retried delivery.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final SellerLifecycleService sellerLifecycleService;
    private final SystemTokenVerifier systemTokenVerifier;
    private final InboundWebhookEventRepository inboundWebhookEventRepository;

    public WebhookController(SellerLifecycleService sellerLifecycleService,
                             SystemTokenVerifier systemTokenVerifier,
                             InboundWebhookEventRepository inboundWebhookEventRepository) {
        this.sellerLifecycleService = sellerLifecycleService;
        this.systemTokenVerifier = systemTokenVerifier;
        this.inboundWebhookEventRepository = inboundWebhookEventRepository;
    }

    @PostMapping("/system/seller-frozen")
    public ResponseEntity<Void> onSellerFrozen(@RequestHeader("Authorization") String auth,
                                               @RequestHeader("X-Webhook-Event-Id") String eventId,
                                               @RequestBody JsonNode body) {
        Claims claims = systemTokenVerifier.verify(auth);
        if (recordOrSkip(eventId, claims, "seller-frozen", body)) {
            UUID sellerId = UUID.fromString(body.get("sellerId").asText());
            String reason = body.path("reason").asText("under_review");
            sellerLifecycleService.freeze(sellerId, reason);
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/system/seller-unfrozen")
    public ResponseEntity<Void> onSellerUnfrozen(@RequestHeader("Authorization") String auth,
                                                 @RequestHeader("X-Webhook-Event-Id") String eventId,
                                                 @RequestBody JsonNode body) {
        Claims claims = systemTokenVerifier.verify(auth);
        if (recordOrSkip(eventId, claims, "seller-unfrozen", body)) {
            UUID sellerId = UUID.fromString(body.get("sellerId").asText());
            String adminNote = body.path("adminNote").asText("");
            sellerLifecycleService.unfreeze(sellerId, adminNote);
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/system/seller-banned")
    public ResponseEntity<Void> onSellerBanned(@RequestHeader("Authorization") String auth,
                                               @RequestHeader("X-Webhook-Event-Id") String eventId,
                                               @RequestBody JsonNode body) {
        Claims claims = systemTokenVerifier.verify(auth);
        if (recordOrSkip(eventId, claims, "seller-banned", body)) {
            UUID sellerId = UUID.fromString(body.get("sellerId").asText());
            String reason = body.path("reason").asText("banned");
            sellerLifecycleService.ban(sellerId, reason);
        }
        return ResponseEntity.accepted().build();
    }

    /** Returns false if this eventId was already processed → caller should skip the action. */
    private boolean recordOrSkip(String eventId, Claims claims, String type, JsonNode body) {
        if (inboundWebhookEventRepository.existsById(eventId)) {
            return false;
        }
        InboundWebhookEvent record = new InboundWebhookEvent();
        record.setEventId(eventId);
        record.setSource(claims.getIssuer() == null ? "unknown" : claims.getIssuer());
        record.setEventType(type);
        record.setReceivedAt(Instant.now());
        record.setPayloadSummary(truncate(body == null ? null : body.toString(), 1900));
        inboundWebhookEventRepository.save(record);
        return true;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
