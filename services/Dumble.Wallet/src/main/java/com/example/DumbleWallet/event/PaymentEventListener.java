package com.example.DumbleWallet.event;

import com.example.DumbleWallet.domain.InboundListenerEvent;
import com.example.DumbleWallet.repository.InboundListenerEventRepository;
import com.example.DumbleWallet.service.WithdrawalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
 * AMQP-side dedup uses {@code inbound_listener_events} (PK on event id).
 * Without it, a redelivered message could double-decrement Pending or
 * double-credit on a reversal.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final ObjectMapper objectMapper;
    private final InboundListenerEventRepository inboundListenerEventRepository;
    private final WithdrawalService withdrawalService;

    public PaymentEventListener(ObjectMapper objectMapper,
                                InboundListenerEventRepository inboundListenerEventRepository,
                                @Lazy WithdrawalService withdrawalService) {
        this.objectMapper = objectMapper;
        this.inboundListenerEventRepository = inboundListenerEventRepository;
        this.withdrawalService = withdrawalService;
    }

    @RabbitListener(queues = "wallet.inbound")
    @Transactional
    public void onMessage(String body,
                          @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String type = routingKey != null ? routingKey : node.path("type").asText();

            switch (type) {
                case "payment.withdrawal.completed" -> handleCompleted(node);
                case "payment.withdrawal.failed"    -> handleFailed(node);
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
        UUID withdrawalId = parseUuid(node.path("withdrawalId").asText(""));
        String paymentRef = node.path("paymentRef").asText("");
        if (paymentRef.isBlank()) paymentRef = node.path("withdrawalId").asText("");
        withdrawalService.onWithdrawalCompleted(withdrawalId, paymentRef);
    }

    private void handleFailed(JsonNode node) {
        if (!claimDedup(node, "payment.withdrawal.failed")) {
            return;
        }
        UUID withdrawalId = parseUuid(node.path("withdrawalId").asText(""));
        String paymentRef = node.path("paymentRef").asText("");
        String reason = node.path("reason").asText("payment_failed");
        withdrawalService.onWithdrawalFailed(withdrawalId, paymentRef, reason);
    }

    private boolean claimDedup(JsonNode node, String routingKey) {
        String eventId = node.path("eventId").asText("");
        if (eventId.isBlank()) {
            // Synthesize a key from (withdrawalId | paymentRef) so a redelivery
            // of the same logical event still dedupes.
            String fallback = node.path("withdrawalId").asText("");
            if (fallback.isBlank()) fallback = node.path("paymentRef").asText("");
            if (fallback.isBlank()) {
                log.warn("Inbound {} missing eventId / withdrawalId / paymentRef — refusing to process", routingKey);
                return false;
            }
            eventId = routingKey + ":" + fallback;
        }
        try {
            InboundListenerEvent dedup = new InboundListenerEvent();
            dedup.setEventId(eventId);
            dedup.setRoutingKey(routingKey);
            dedup.setReceivedAt(Instant.now());
            String summary = node.toString();
            dedup.setPayloadSummary(summary.length() > 1900 ? summary.substring(0, 1900) : summary);
            inboundListenerEventRepository.saveAndFlush(dedup);
            return true;
        } catch (DataIntegrityViolationException dup) {
            log.debug("Inbound {} {} already processed — skipping", routingKey, eventId);
            return false;
        }
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
