package com.example.DumblePayment.scheduler;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.domain.Payout;
import com.example.DumblePayment.domain.Refund;
import com.example.DumblePayment.domain.WebhookEvent;
import com.example.DumblePayment.domain.enums.WebhookProcessingStatus;
import com.example.DumblePayment.repository.ChargeRepository;
import com.example.DumblePayment.repository.PayoutRepository;
import com.example.DumblePayment.repository.RefundRepository;
import com.example.DumblePayment.repository.WebhookEventRepository;
import com.example.DumblePayment.service.ChargePersister;
import com.example.DumblePayment.service.PayoutPersister;
import com.example.DumblePayment.service.RefundPersister;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Decision 4.3 — Phase 2 of webhook handling. Reads PENDING webhook_events
 * rows and applies Charge / Payout state transitions + emits the
 * corresponding {@code ChargeSucceeded / ChargeFailed / ChargebackFiled /
 * PayoutCompleted / PayoutFailed / WithdrawalCompleted / WithdrawalFailed}
 * events.
 *
 * Mapping uses Paymob's standard "transaction" / "payout" event shapes —
 * the persister layer handles all the lifecycle invariants (idempotent on
 * already-terminal rows, locks on update). Worst case for an unknown event
 * type is "PROCESSED with no state change", which is fine.
 */
@Component
@ConditionalOnProperty(name = "payment.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookProcessingJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingJob.class);
    private static final int BATCH_SIZE = 50;

    private final WebhookEventRepository webhookEventRepository;
    private final ChargePersister chargePersister;
    private final PayoutPersister payoutPersister;
    private final RefundPersister refundPersister;
    private final ChargeRepository chargeRepository;
    private final PayoutRepository payoutRepository;
    private final RefundRepository refundRepository;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public WebhookProcessingJob(WebhookEventRepository webhookEventRepository,
                                ChargePersister chargePersister,
                                PayoutPersister payoutPersister,
                                RefundPersister refundPersister,
                                ChargeRepository chargeRepository,
                                PayoutRepository payoutRepository,
                                RefundRepository refundRepository,
                                ObjectMapper objectMapper,
                                @Value("${payment.webhook.max-attempts:10}") int maxAttempts) {
        this.webhookEventRepository = webhookEventRepository;
        this.chargePersister = chargePersister;
        this.payoutPersister = payoutPersister;
        this.refundPersister = refundPersister;
        this.chargeRepository = chargeRepository;
        this.payoutRepository = payoutRepository;
        this.refundRepository = refundRepository;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${payment.webhook.process-delay-ms:1000}")
    public void run() {
        List<WebhookEvent> batch = webhookEventRepository.findByProcessingStatusOrderByReceivedAtAsc(
                WebhookProcessingStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;
        log.debug("WebhookProcessingJob: {} pending event(s)", batch.size());
        for (WebhookEvent ev : batch) {
            try {
                processOne(ev);
            } catch (RuntimeException ex) {
                bumpAttempts(ev, ex);
            }
        }
    }

    /**
     * Not {@code @Transactional} — self-invocation from {@link #run()}
     * bypasses the AOP proxy, so the annotation would be a no-op anyway.
     * Each per-step persister call ({@code chargePersister.*},
     * {@code payoutPersister.*}, {@code webhookEventRepository.save}) opens
     * its own short tx via Spring Data, which gives us per-step atomicity
     * without needing this method to be a tx itself.
     */
    private void processOne(WebhookEvent ev) {
        JsonNode body;
        try {
            body = objectMapper.readTree(ev.getPayloadJson());
        } catch (Exception ex) {
            markFailedTerminal(ev, "malformed_body: " + ex.getMessage());
            return;
        }
        String type = ev.getEventType() == null ? "" : ev.getEventType().toLowerCase();

        // Paymob's event names vary by integration; we accept the common
        // shapes. Anything unknown is recorded as PROCESSED with no state
        // change so it doesn't loop forever.
        //
        // Order matters: "chargeback" contains the substring "charge", so the
        // chargeback branch MUST be checked before the transaction branch —
        // otherwise a chargeback event gets misrouted through
        // applyTransactionEvent (and a SUCCEEDED parent silently stays
        // SUCCEEDED instead of flipping to REVERSED).
        if (type.contains("chargeback") || type.contains("dispute")) {
            applyChargebackEvent(body);
        } else if (type.contains("refund")) {
            // Before the transaction branch: a refund event name can contain
            // "charge", which would otherwise misroute it to applyTransactionEvent.
            applyRefundEvent(body);
        } else if (type.contains("transaction") || type.contains("charge")) {
            applyTransactionEvent(body);
        } else if (type.contains("payout") || type.contains("withdrawal") || type.contains("disbursement")) {
            applyPayoutEvent(body);
        } else {
            log.info("Webhook {} type={} — unknown class, marking processed with no state change",
                    ev.getEventId(), ev.getEventType());
        }

        ev.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
        ev.setProcessedAt(Instant.now());
        ev.setLastError(null);
        webhookEventRepository.save(ev);
    }

    private void applyTransactionEvent(JsonNode body) {
        // Standard fields under the "obj" wrapper or top level.
        JsonNode root = body.has("obj") ? body.path("obj") : body;
        boolean success = root.path("success").asBoolean(false);
        String providerRef = stringOrNull(root.path("id"));
        String callerRef = firstNonBlank(
                root.path("payment_key_claims").path("extra").path("caller_reference").asText(null),
                root.path("merchant_order_id").asText(null),
                root.path("order").path("merchant_order_id").asText(null));

        Charge c = locateCharge(providerRef, callerRef);
        if (c == null) {
            log.warn("Webhook transaction matched no Charge (providerRef={}, callerRef={})",
                    providerRef, callerRef);
            return;
        }
        if (success) {
            chargePersister.markSucceeded(c.getId(), providerRef);
        } else {
            String reason = root.path("data").path("message").asText(
                    root.path("error_occured").asText("provider_failed"));
            chargePersister.markFailed(c.getId(), reason, providerRef);
        }
    }

    private void applyPayoutEvent(JsonNode body) {
        JsonNode root = body.has("obj") ? body.path("obj") : body;
        String providerRef = stringOrNull(root.path("id"));
        String callerRef = firstNonBlank(
                root.path("merchant_payout_id").asText(null),
                root.path("caller_reference").asText(null));
        String state = root.path("status").asText(root.path("state").asText(""));

        Payout p = locatePayout(providerRef, callerRef);
        if (p == null) {
            log.warn("Webhook payout matched no Payout (providerRef={}, callerRef={})",
                    providerRef, callerRef);
            return;
        }
        String s = state.toLowerCase();
        if (s.contains("complete") || s.contains("success") || s.contains("paid")) {
            payoutPersister.markCompleted(p.getId(), providerRef);
        } else if (s.contains("fail") || s.contains("reject")) {
            String reason = firstNonBlank(
                    root.path("failure_reason").asText(null),
                    root.path("data").path("message").asText(null),
                    "provider_failed");
            payoutPersister.markFailed(p.getId(), reason);
        } else if (s.contains("sent") || s.contains("dispatch")) {
            payoutPersister.markSent(p.getId(), providerRef);
        } else {
            log.info("Webhook payout status='{}' — leaving in current state", state);
        }
    }

    private void applyChargebackEvent(JsonNode body) {
        JsonNode root = body.has("obj") ? body.path("obj") : body;
        String providerRef = stringOrNull(root.path("transaction_id"));
        if (providerRef == null) providerRef = stringOrNull(root.path("id"));
        String reason = root.path("reason").asText("chargeback_filed");

        Charge c = providerRef == null ? null
                : chargeRepository.findByProviderRef(providerRef).orElse(null);
        if (c == null) {
            log.warn("Webhook chargeback matched no Charge (providerRef={})", providerRef);
            return;
        }
        chargePersister.markReversed(c.getId(), reason);
    }

    private void applyRefundEvent(JsonNode body) {
        // An ORIGINAL_METHOD refund is async at Paymob: RefundService persisted
        // it PENDING with a providerRef and is waiting for this webhook to flip
        // it to SUCCEEDED/FAILED (which emits payment.refund.succeeded/failed).
        JsonNode root = body.has("obj") ? body.path("obj") : body;
        boolean success = root.path("success").asBoolean(false);
        String providerRef = stringOrNull(root.path("id"));

        Refund r = providerRef == null ? null
                : refundRepository.findByProviderRef(providerRef).orElse(null);
        if (r == null) {
            log.warn("Webhook refund matched no Refund (providerRef={})", providerRef);
            return;
        }
        // markSucceeded/markFailed are idempotent — they no-op unless still PENDING.
        if (success) {
            refundPersister.markSucceeded(r.getId(), providerRef);
        } else {
            String reason = firstNonBlank(
                    root.path("data").path("message").asText(null),
                    root.path("error_occured").asText(null),
                    "provider_failed");
            refundPersister.markFailed(r.getId(), reason);
        }
    }

    private Charge locateCharge(String providerRef, String callerRef) {
        if (providerRef != null) {
            Charge c = chargeRepository.findByProviderRef(providerRef).orElse(null);
            if (c != null) return c;
        }
        if (callerRef != null) {
            // ChargeService passes the local id as caller_reference at the
            // provider — try parsing as UUID first.
            try {
                UUID id = UUID.fromString(callerRef);
                return chargeRepository.findById(id).orElse(null);
            } catch (IllegalArgumentException ignored) {
                List<Charge> candidates = chargeRepository.findByCallerReference(callerRef);
                if (!candidates.isEmpty()) return candidates.get(0);
            }
        }
        return null;
    }

    private Payout locatePayout(String providerRef, String callerRef) {
        if (providerRef != null) {
            Payout p = payoutRepository.findByProviderRef(providerRef).orElse(null);
            if (p != null) return p;
        }
        if (callerRef != null) {
            try {
                UUID id = UUID.fromString(callerRef);
                Payout p = payoutRepository.findById(id).orElse(null);
                if (p != null) return p;
            } catch (IllegalArgumentException ignored) {}
            return payoutRepository.findByCallerReference(callerRef).orElse(null);
        }
        return null;
    }

    private void bumpAttempts(WebhookEvent ev, RuntimeException cause) {
        log.error("WebhookProcessing failed for {} (attempt {}): {}",
                ev.getEventId(), ev.getAttempts() + 1, cause.getMessage());
        // Re-fetch so we don't write with a stale @Version. saveAndFlush
        // commits in its own internal Spring Data tx — no @Transactional
        // needed (and self-invoke wouldn't get one anyway).
        WebhookEvent fresh = webhookEventRepository.findById(ev.getEventId()).orElse(ev);
        fresh.setAttempts(fresh.getAttempts() + 1);
        fresh.setLastError(cause.getMessage());
        if (fresh.getAttempts() >= maxAttempts) {
            fresh.setProcessingStatus(WebhookProcessingStatus.FAILED);
        }
        webhookEventRepository.save(fresh);
    }

    private void markFailedTerminal(WebhookEvent ev, String reason) {
        WebhookEvent fresh = webhookEventRepository.findById(ev.getEventId()).orElse(ev);
        fresh.setProcessingStatus(WebhookProcessingStatus.FAILED);
        fresh.setAttempts(fresh.getAttempts() + 1);
        fresh.setLastError(reason);
        webhookEventRepository.save(fresh);
    }

    private static String stringOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String s = node.asText("");
        return s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
