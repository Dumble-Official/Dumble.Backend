package com.example.DumbleSubscription.event;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.domain.InboundListenerEvent;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.repository.InboundListenerEventRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import com.example.DumbleSubscription.service.AuditLogger;
import com.example.DumbleSubscription.service.BundleSubscriptionService;
import com.example.DumbleSubscription.service.PlatformPlanService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listens on the {@code subscription.inbound} queue for events Subscription
 * cares about — payout confirmations + chargeback notifications from Payment.
 *
 * Routing keys:
 *   payment.payout.completed     → mark escrow PAID_OUT
 *   payment.payout.failed        → roll escrow back to HELD
 *   payment.charge.succeeded     → flip PENDING sub to ACTIVE (Decision: Paymob 3DS/OTP confirm)
 *   payment.charge.failed        → flip PENDING sub to EXPIRED
 *   payment.chargeback.filed     → lock related escrow + mark sub REFUNDED (Decision 6.2)
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final EscrowEntryRepository escrowEntryRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final InboundListenerEventRepository inboundListenerEventRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogger auditLogger;
    private final OutboxWriter outboxWriter;
    private final BundleSubscriptionService bundleSubscriptionService;
    private final PlatformPlanService platformPlanService;

    public PaymentEventListener(EscrowEntryRepository escrowEntryRepository,
                                BundleSubscriptionRepository bundleSubscriptionRepository,
                                PlatformSubscriptionRepository platformSubscriptionRepository,
                                InboundListenerEventRepository inboundListenerEventRepository,
                                ObjectMapper objectMapper,
                                AuditLogger auditLogger,
                                OutboxWriter outboxWriter,
                                @Lazy BundleSubscriptionService bundleSubscriptionService,
                                @Lazy PlatformPlanService platformPlanService) {
        this.escrowEntryRepository = escrowEntryRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.platformSubscriptionRepository = platformSubscriptionRepository;
        this.inboundListenerEventRepository = inboundListenerEventRepository;
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
        this.outboxWriter = outboxWriter;
        this.bundleSubscriptionService = bundleSubscriptionService;
        this.platformPlanService = platformPlanService;
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
                // Payment publishes `.succeeded` per payment-service-decisions
                // (the internal status is SUCCEEDED). We accept the legacy
                // `.completed` spelling too so any in-flight broker rows
                // queued before the rename still resolve.
                case "payment.charge.succeeded", "payment.charge.completed"
                        -> handleChargeSucceeded(node);
                case "payment.charge.failed"      -> handleChargeFailed(node);
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
     * bug_029 — Paymob's Egyptian card flow returns Pending on the first call
     * (3DS/OTP required). When the participant confirms the OTP, Payment emits
     * payment.charge.succeeded; we use that to flip the PENDING sub to ACTIVE.
     *
     * Expects payload: { "providerRef": "...", "subscriptionId": "...", "userId": "..." }
     * subscriptionId/userId are accepted as alternate lookup keys when
     * providerRef isn't carried.
     */
    private void handleChargeSucceeded(JsonNode node) {
        String providerRef = node.path("providerRef").asText("");
        String subIdStr = node.path("subscriptionId").asText("");
        String userIdStr = node.path("userId").asText("");
        String callerRef = node.path("callerReference").asText("");

        if (!subIdStr.isEmpty()) {
            UUID subId = parseUuid(subIdStr, "charge.completed.subscriptionId");
            if (subId != null) {
                bundleSubscriptionService.confirmPendingCharge(subId, providerRef);
                return;
            }
        }
        if (!providerRef.isEmpty()) {
            BundleSubscription sub = bundleSubscriptionRepository.findByProviderRef(providerRef).orElse(null);
            if (sub != null) {
                bundleSubscriptionService.confirmPendingCharge(sub.getId(), providerRef);
                return;
            }
            PlatformSubscription pSub = platformSubscriptionRepository.findByProviderRef(providerRef).orElse(null);
            if (pSub != null) {
                platformPlanService.confirmPendingUpgrade(pSub.getUserId(), providerRef);
                return;
            }
        }
        // userId fallback is ONLY for platform Pro upgrades. Gate it on the
        // platform-sub: callerReference so an unrelated charge for the same user
        // (e.g. a wallet top-up: callerReference "topup:<userId>") can't wrongly
        // activate a still-pending Pro upgrade without its own payment.
        if (!userIdStr.isEmpty() && callerRef.startsWith("platform-sub:")) {
            UUID userId = parseUuid(userIdStr, "charge.completed.userId");
            if (userId != null) {
                platformPlanService.confirmPendingUpgrade(userId, providerRef);
                return;
            }
        }
        log.warn("charge.completed event matched no PENDING subscription (providerRef={}, sub={}, user={}, caller={})",
                providerRef, subIdStr, userIdStr, callerRef);
    }

    private void handleChargeFailed(JsonNode node) {
        String providerRef = node.path("providerRef").asText("");
        String subIdStr = node.path("subscriptionId").asText("");
        String userIdStr = node.path("userId").asText("");
        String reason = node.path("reason").asText("payment_declined");

        if (!subIdStr.isEmpty()) {
            UUID subId = parseUuid(subIdStr, "charge.failed.subscriptionId");
            if (subId != null) {
                bundleSubscriptionService.failPendingCharge(subId, reason);
                return;
            }
        }
        if (!providerRef.isEmpty()) {
            BundleSubscription sub = bundleSubscriptionRepository.findByProviderRef(providerRef).orElse(null);
            if (sub != null) {
                bundleSubscriptionService.failPendingCharge(sub.getId(), reason);
                return;
            }
            PlatformSubscription pSub = platformSubscriptionRepository.findByProviderRef(providerRef).orElse(null);
            if (pSub != null) {
                platformPlanService.failPendingUpgrade(pSub.getUserId(), reason);
                return;
            }
        }
        if (!userIdStr.isEmpty()) {
            UUID userId = parseUuid(userIdStr, "charge.failed.userId");
            if (userId != null) {
                platformPlanService.failPendingUpgrade(userId, reason);
                return;
            }
        }
        log.warn("charge.failed event matched no PENDING subscription (providerRef={}, sub={}, user={})",
                providerRef, subIdStr, userIdStr);
    }

    /**
     * Decision 6.2 — when Paymob processes a chargeback, the participant has
     * already been credited externally. We need to lock related escrow and
     * mark the sub as refunded so cohort payouts don't fire to the seller.
     *
     * Expects payload: { "chargebackId": "...", "subscriptionId": "...", "amountCents": 12345 }
     *
     * Three correctness fixes here (merged_bug_011 run-2):
     *   bug 1 — when a partial chargeback's remaining amount is smaller than
     *     the next tranche, split that tranche so we lock exactly what the
     *     bank pulled rather than the whole tranche (the old loop overlocked
     *     by up to (tranche_amount - 1) cents).
     *   bug 2 — the @RabbitListener path had no idempotency table; a redelivered
     *     message would re-enter the partial branch (which leaves status
     *     unchanged) and lock another chargebackCents worth of tranches each
     *     time. Persist the chargebackId in inbound_listener_events first.
     *   bug 3 — full-vs-partial threshold compared chargebackCents to
     *     "unreleased" rather than "pricePaidCents", so once enough tranches
     *     paid out, a partial dispute crossing the shrinking unreleased
     *     threshold flipped the sub to REFUNDED. Compare to pricePaidCents.
     */
    private void handleChargebackFiled(JsonNode node) {
        String chargebackId = node.path("chargebackId").asText("");
        String subId = node.path("subscriptionId").asText("");
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

        // bug_011-bug2 — listener idempotency. Persist the dedup row up-front
        // (PK serializes concurrent redeliveries) so a re-played message
        // can't re-lock another chargebackCents worth of escrow. Fall back to
        // a synthetic key when chargebackId isn't supplied — better than
        // silently allowing duplicate processing.
        String dedupKey = chargebackId.isBlank()
                ? "chargeback:" + subId + ":" + node.path("amountCents").asLong(0)
                : "chargeback:" + chargebackId;
        try {
            InboundListenerEvent dedup = new InboundListenerEvent();
            dedup.setEventId(dedupKey);
            dedup.setRoutingKey("payment.chargeback.filed");
            dedup.setReceivedAt(Instant.now());
            dedup.setPayloadSummary(node.toString().length() > 1900
                    ? node.toString().substring(0, 1900) : node.toString());
            inboundListenerEventRepository.saveAndFlush(dedup);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            log.debug("Chargeback {} already processed — skipping", dedupKey);
            return;
        }

        BundleSubscription sub = bundleSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("Chargeback event references unknown subscription {}", subscriptionId);
            return;
        }
        if (sub.getStatus() == SubscriptionStatus.REFUNDED) {
            return;     // already handled
        }

        // Negative payload values are nonsense; <=0 means "unspecified".
        long chargebackCents = node.has("amountCents") ? node.path("amountCents").asLong(0) : 0;

        Instant now = Instant.now();
        List<EscrowEntry> entries = escrowEntryRepository.findByBundleSubscriptionId(subscriptionId);

        long unreleased = entries.stream()
                .filter(e -> e.getStatus() == EscrowStatus.HELD || e.getStatus() == EscrowStatus.AVAILABLE)
                .mapToLong(EscrowEntry::getAmountCents)
                .sum();

        // bug_011-bug3 — compare to pricePaidCents. "Covers everything paid"
        // means full chargeback; "covers all currently-unreleased" does NOT
        // — that's a partial dispute that happened to land late in the cycle.
        boolean isFullChargeback = chargebackCents <= 0 || chargebackCents >= sub.getPricePaidCents();
        long lockedCents;

        if (isFullChargeback) {
            lockedCents = lockAllUnreleased(entries, now);
            sub.setStatus(SubscriptionStatus.REFUNDED);
            sub.setCancelledAt(now);
        } else {
            lockedCents = lockPartialOldestFirst(sub, entries, chargebackCents, unreleased, now);
            // Sub stays in its prior status — partial dispute does not
            // invalidate the participant's remaining service period.
        }
        sub.setUpdatedAt(now);

        auditLogger.log(sub.getId(), "ChargebackFiled", "WEBHOOK", subId,
                isFullChargeback ? "Full chargeback — locking escrow" : "Partial chargeback — locking matching escrow",
                Map.of("lockedCents", lockedCents,
                       "chargebackCents", chargebackCents,
                       "unreleasedCents", unreleased,
                       "pricePaidCents", sub.getPricePaidCents(),
                       "partial", !isFullChargeback));
        outboxWriter.write("ChargebackProcessed", "subscription.chargeback.processed",
                Map.of("subscriptionId", sub.getId(),
                        "participantId", sub.getParticipantId(),
                        "lockedCents", lockedCents,
                        "chargebackCents", chargebackCents,
                        "partial", !isFullChargeback));
    }

    private long lockAllUnreleased(List<EscrowEntry> entries, Instant now) {
        long locked = 0;
        for (EscrowEntry entry : entries) {
            if (entry.getStatus() == EscrowStatus.HELD || entry.getStatus() == EscrowStatus.AVAILABLE) {
                entry.setStatus(EscrowStatus.REFUNDED);
                entry.setUpdatedAt(now);
                locked += entry.getAmountCents();
            }
        }
        return locked;
    }

    /**
     * bug_011-bug1 — partial chargeback split-tranche. Lock entries oldest
     * first until the disputed amount is covered; if the boundary tranche is
     * larger than what's left, split it: shrink the original entry to the
     * portion the seller still earns, mint a new REFUNDED entry for the
     * disputed slice. Truncate {@code sub.endsAt} when the chargeback
     * exceeds {@code unreleased} (would otherwise leave the sub
     * "phantom-active" for months with no escrow backing).
     */
    private long lockPartialOldestFirst(BundleSubscription sub,
                                        List<EscrowEntry> entries,
                                        long chargebackCents,
                                        long unreleased,
                                        Instant now) {
        List<EscrowEntry> lockable = entries.stream()
                .filter(e -> e.getStatus() == EscrowStatus.HELD || e.getStatus() == EscrowStatus.AVAILABLE)
                .sorted(Comparator.comparing(EscrowEntry::getOriginalScheduledAt))
                .toList();

        long target = Math.min(chargebackCents, unreleased);
        long remaining = target;
        long locked = 0;
        for (EscrowEntry entry : lockable) {
            if (remaining <= 0) break;
            if (entry.getAmountCents() <= remaining) {
                entry.setStatus(EscrowStatus.REFUNDED);
                entry.setUpdatedAt(now);
                locked += entry.getAmountCents();
                remaining -= entry.getAmountCents();
            } else {
                // Split: the seller keeps (amount - remaining), the bank takes
                // remaining. The original row carries the remainder so its
                // payoutRef/cohort lineage stays intact; a new sibling row
                // records the refunded slice.
                long refundSlice = remaining;
                long sellerSlice = entry.getAmountCents() - refundSlice;

                EscrowEntry refunded = new EscrowEntry();
                refunded.setBundleSubscriptionId(entry.getBundleSubscriptionId());
                refunded.setSellerId(entry.getSellerId());
                refunded.setAmountCents(refundSlice);
                refunded.setCurrency(entry.getCurrency());
                refunded.setStatus(EscrowStatus.REFUNDED);
                refunded.setCohortKey(entry.getCohortKey());
                refunded.setOriginalScheduledAt(entry.getOriginalScheduledAt());
                refunded.setDeferredCount(0);
                refunded.setCreatedAt(now);
                refunded.setUpdatedAt(now);
                escrowEntryRepository.save(refunded);

                entry.setAmountCents(sellerSlice);
                entry.setUpdatedAt(now);

                locked += refundSlice;
                remaining = 0;
            }
        }

        // When the chargeback exceeds what's left in escrow, every future
        // service period has been locked — the sub has nothing backing it.
        // End it now (autoRenew off) rather than leaving a phantom-active
        // sub the participant could keep using.
        if (chargebackCents > unreleased) {
            if (sub.getEndsAt() == null || sub.getEndsAt().isAfter(now)) {
                sub.setEndsAt(now);
            }
            sub.setAutoRenew(false);
        }
        return locked;
    }

    private UUID parseUuid(String raw, String label) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Malformed UUID for {}: {}", label, raw);
            return null;
        }
    }
}
