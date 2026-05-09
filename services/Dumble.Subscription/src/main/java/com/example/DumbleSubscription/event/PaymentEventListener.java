package com.example.DumbleSubscription.event;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.service.AuditLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listens on the {@code subscription.inbound} queue for events Subscription
 * cares about — payout confirmations + chargeback notifications from Payment.
 *
 * Routing keys:
 *   payment.payout.completed   → mark escrow PAID_OUT
 *   payment.payout.failed      → roll escrow back to HELD
 *   payment.chargeback.filed   → lock related escrow + mark sub REFUNDED (Decision 6.2)
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final EscrowEntryRepository escrowEntryRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogger auditLogger;
    private final OutboxWriter outboxWriter;

    public PaymentEventListener(EscrowEntryRepository escrowEntryRepository,
                                BundleSubscriptionRepository bundleSubscriptionRepository,
                                ObjectMapper objectMapper,
                                AuditLogger auditLogger,
                                OutboxWriter outboxWriter) {
        this.escrowEntryRepository = escrowEntryRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
        this.outboxWriter = outboxWriter;
    }

    @RabbitListener(queues = "subscription.inbound")
    @Transactional
    public void onMessage(String body, @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String type = routingKey != null ? routingKey : node.path("type").asText();

            switch (type) {
                case "payment.payout.completed"   -> handlePayoutCompleted(node);
                case "payment.payout.failed"      -> handlePayoutFailed(node);
                case "payment.chargeback.filed"   -> handleChargebackFiled(node);
                default -> log.debug("Ignoring event type {}", type);
            }
        } catch (Exception ex) {
            log.error("Failed to process inbound event: {}", body, ex);
        }
    }

    private void handlePayoutCompleted(JsonNode node) {
        String payoutId = node.path("payoutId").asText();
        if (payoutId.isEmpty()) return;
        Instant now = Instant.now();
        List<EscrowEntry> entries = escrowEntryRepository.findByPayoutRefAndStatus(payoutId, EscrowStatus.AVAILABLE);
        for (EscrowEntry entry : entries) {
            entry.setStatus(EscrowStatus.PAID_OUT);
            entry.setPaidOutAt(now);
            entry.setUpdatedAt(now);
            auditLogger.log(entry.getId(), "PayoutCompleted", "WEBHOOK", payoutId,
                    "Payment confirmed transfer", Map.of("amount", entry.getAmountCents()));
        }
    }

    private void handlePayoutFailed(JsonNode node) {
        String payoutId = node.path("payoutId").asText();
        if (payoutId.isEmpty()) return;
        Instant now = Instant.now();
        List<EscrowEntry> entries = escrowEntryRepository.findByPayoutRefAndStatus(payoutId, EscrowStatus.AVAILABLE);
        for (EscrowEntry entry : entries) {
            // Roll back to HELD — next cohort run will retry.
            entry.setStatus(EscrowStatus.HELD);
            entry.setReleasedAt(null);
            entry.setPayoutRef(null);
            entry.setDeferredCount(entry.getDeferredCount() + 1);
            entry.setDeferReason("PAYOUT_FAILED");
            entry.setUpdatedAt(now);
            auditLogger.log(entry.getId(), "PayoutFailed", "WEBHOOK", payoutId,
                    "Payment reported transfer failure", null);
        }
    }

    /**
     * Decision 6.2 — when Paymob processes a chargeback, the participant has
     * already been credited externally. We need to lock related escrow and
     * mark the sub as refunded so cohort payouts don't fire to the seller.
     *
     * Expects payload: { "subscriptionId": "...", "amountCents": 12345 }
     */
    private void handleChargebackFiled(JsonNode node) {
        String subId = node.path("subscriptionId").asText();
        if (subId.isEmpty()) {
            log.warn("Chargeback event missing subscriptionId");
            return;
        }
        UUID subscriptionId;
        try {
            subscriptionId = UUID.fromString(subId);
        } catch (IllegalArgumentException ex) {
            log.warn("Chargeback event has malformed subscriptionId: {}", subId);
            return;
        }

        BundleSubscription sub = bundleSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("Chargeback event references unknown subscription {}", subscriptionId);
            return;
        }
        if (sub.getStatus() == SubscriptionStatus.REFUNDED) {
            return;     // already handled (idempotent against retried webhooks)
        }

        Instant now = Instant.now();

        // Lock all unreleased escrow — money has been pulled back externally,
        // so we must not pay it out to the seller.
        List<EscrowEntry> entries = escrowEntryRepository.findByBundleSubscriptionId(subscriptionId);
        long lockedCents = 0;
        for (EscrowEntry entry : entries) {
            if (entry.getStatus() == EscrowStatus.HELD || entry.getStatus() == EscrowStatus.AVAILABLE) {
                entry.setStatus(EscrowStatus.REFUNDED);
                entry.setUpdatedAt(now);
                lockedCents += entry.getAmountCents();
            }
        }

        sub.setStatus(SubscriptionStatus.REFUNDED);
        sub.setCancelledAt(now);
        sub.setUpdatedAt(now);

        auditLogger.log(sub.getId(), "ChargebackFiled", "WEBHOOK", subId,
                "Bank chargeback — locking escrow", Map.of("lockedCents", lockedCents));
        outboxWriter.write("ChargebackProcessed", "subscription.chargeback.processed",
                Map.of("subscriptionId", sub.getId(),
                        "participantId", sub.getParticipantId(),
                        "lockedCents", lockedCents));
    }
}
