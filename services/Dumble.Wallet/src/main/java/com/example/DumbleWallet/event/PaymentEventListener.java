package com.example.DumbleWallet.event;

import com.example.DumbleWallet.dto.WalletCreditRequest;
import com.example.DumbleWallet.repository.WithdrawalRequestRepository;
import com.example.DumbleWallet.service.WalletService;
import com.example.DumbleWallet.service.WithdrawalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Wallet PDF Decision 6.2 — Wallet consumes events from Payment to finalise
 * withdrawals.
 *
 * Routing keys:
 *   payment.withdrawal.completed   → flip SENT → COMPLETED, decrement Pending
 *   payment.withdrawal.failed      → reverse the wallet movement, log
 *                                    WITHDRAWAL_REVERSED credit, notify user
 *
 * AMQP-side dedup uses {@code inbound_listener_events} (PK on event id) via
 * {@link InboundListenerEventRecorder} — the INSERT must run in a separate
 * tx (REQUIRES_NEW) or a duplicate redelivery would mark the outer session
 * rollback-only and trigger an infinite NACK/redeliver loop on the queue.
 *
 * <p>Payload contract (matches what {@code PayoutPersister.markCompleted} /
 * {@code markFailed} actually write to the outbox):
 * <pre>
 *   { payoutId, type, subjectId, amountCents, callerReference, providerRef? , reason? }
 * </pre>
 * {@code callerReference} is the local Wallet withdrawal id Wallet handed
 * Payment on creation, so it's the canonical lookup key on the consumer side.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final ObjectMapper objectMapper;
    private final InboundListenerEventRecorder recorder;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalService withdrawalService;
    private final WalletService walletService;

    public PaymentEventListener(ObjectMapper objectMapper,
                                InboundListenerEventRecorder recorder,
                                WithdrawalRequestRepository withdrawalRequestRepository,
                                @Lazy WithdrawalService withdrawalService,
                                @Lazy WalletService walletService) {
        this.objectMapper = objectMapper;
        this.recorder = recorder;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.withdrawalService = withdrawalService;
        this.walletService = walletService;
    }

    @RabbitListener(queues = "wallet.inbound")
    public void onMessage(String body,
                          @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String type = routingKey != null ? routingKey : node.path("type").asText();

            switch (type) {
                case "payment.withdrawal.completed" -> handleCompleted(node);
                case "payment.withdrawal.failed"    -> handleFailed(node);
                case "payment.charge.succeeded"     -> handleChargeSucceeded(node);
                default -> log.debug("Ignoring inbound event type {}", type);
            }
        } catch (Exception ex) {
            log.error("Failed to process inbound event: {}", body, ex);
        }
    }

    private void handleCompleted(JsonNode node) {
        if (!claimDedup(node, "payment.withdrawal.completed")) {
            return;
        }
        UUID localId = resolveLocalWithdrawalId(node);
        String providerRef = nonBlank(node.path("providerRef").asText(""));
        if (localId == null) {
            log.warn("WithdrawalCompleted missing usable callerReference/payoutId — dropping");
            return;
        }
        withdrawalService.onWithdrawalCompleted(localId, providerRef);
    }

    private void handleFailed(JsonNode node) {
        if (!claimDedup(node, "payment.withdrawal.failed")) {
            return;
        }
        UUID localId = resolveLocalWithdrawalId(node);
        String providerRef = nonBlank(node.path("providerRef").asText(""));
        String reason = node.path("reason").asText("payment_failed");
        if (localId == null) {
            log.warn("WithdrawalFailed missing usable callerReference/payoutId — dropping");
            return;
        }
        withdrawalService.onWithdrawalFailed(localId, providerRef, reason);
    }

    /**
     * Wallet top-up: a hosted-checkout charge cleared. Credit the wallet only for
     * charges whose callerReference is a {@code topup:*} (subscription/bundle
     * charges ride the same routing key but are owned by Subscription). Dedup on
     * the unique chargeId — NOT callerReference, which repeats per user across
     * top-ups — so a redelivery can't double-credit.
     */
    private void handleChargeSucceeded(JsonNode node) {
        String chargeId = node.path("chargeId").asText("");
        String callerRef = node.path("callerReference").asText("");
        if (chargeId.isBlank() || !callerRef.startsWith("topup:")) {
            return; // not a wallet top-up
        }
        String summary = node.toString();
        if (summary.length() > 1900) summary = summary.substring(0, 1900);
        if (!recorder.tryRecord("payment.charge.succeeded:" + chargeId,
                "payment.charge.succeeded", summary)) {
            return; // already processed
        }
        UUID userId = parseUuid(node.path("userId").asText(""));
        long amountCents = node.path("amountCents").asLong(0L);
        if (userId == null || amountCents <= 0) {
            log.warn("topup charge.succeeded missing userId/amount — dropping (chargeId={})", chargeId);
            return;
        }
        WalletCreditRequest req = new WalletCreditRequest();
        req.setUserId(userId);
        req.setAmountCents(amountCents);
        req.setSource("TOPUP");
        req.setExternalRef(chargeId);
        req.setMemo("Wallet top-up");
        walletService.credit(req);
    }

    /**
     * Resolves the local Wallet withdrawal id from Payment's payload.
     * Preference order:
     *   1. {@code callerReference} — Wallet's local id passed to Payment on creation
     *   2. {@code payoutId} → Payment's row id, stored as {@code paymentRef} on Wallet's row
     */
    private UUID resolveLocalWithdrawalId(JsonNode node) {
        String callerRef = nonBlank(node.path("callerReference").asText(""));
        if (callerRef != null) {
            UUID parsed = parseUuid(callerRef);
            if (parsed != null) return parsed;
        }
        String payoutId = nonBlank(node.path("payoutId").asText(""));
        if (payoutId != null) {
            return withdrawalRequestRepository.findByPaymentRef(payoutId)
                    .map(w -> w.getId())
                    .orElse(null);
        }
        return null;
    }

    private boolean claimDedup(JsonNode node, String routingKey) {
        String eventId = node.path("eventId").asText("");
        if (eventId.isBlank()) {
            // Synthesize a key from (callerReference | payoutId) so a redelivery
            // of the same logical event still dedupes.
            String fallback = node.path("callerReference").asText("");
            if (fallback.isBlank()) fallback = node.path("payoutId").asText("");
            if (fallback.isBlank()) {
                log.warn("Inbound {} missing eventId / callerReference / payoutId — refusing to process", routingKey);
                return false;
            }
            eventId = routingKey + ":" + fallback;
        }
        String summary = node.toString();
        if (summary.length() > 1900) summary = summary.substring(0, 1900);
        return recorder.tryRecord(eventId, routingKey, summary);
    }

    private static String nonBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
