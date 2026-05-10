package com.example.DumblePayment.service;

import com.example.DumblePayment.exception.UnauthorizedAccessException;
import com.example.DumblePayment.provider.IPaymentProvider;
import com.example.DumblePayment.provider.dto.ProviderWebhookVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decisions 4.1 / 4.2 / 4.3 — Phase 1 of webhook handling: verify signature
 * (constant-time), dedup on Paymob event id (PK insert), ACK 200. Phase 2
 * (mutate Charge/Refund/Payout state, emit domain events) runs async via
 * {@link com.example.DumblePayment.scheduler.WebhookProcessingJob} so the
 * sync ACK to Paymob stays under their timeout budget.
 *
 * The dedup INSERT is delegated to {@link WebhookEventRecorder} (a
 * {@code REQUIRES_NEW} tx) so a PK violation on a redelivered event doesn't
 * corrupt the surrounding tx via Hibernate's rollback-only marker.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final IPaymentProvider provider;
    private final WebhookEventRecorder recorder;

    public WebhookService(IPaymentProvider provider, WebhookEventRecorder recorder) {
        this.provider = provider;
        this.recorder = recorder;
    }

    /**
     * Returns true on accept (new or already-recorded duplicate). Throws
     * {@link UnauthorizedAccessException} on a bad signature or missing
     * event id. Always idempotent on the eventId.
     */
    public boolean receive(String rawBody, String signatureHeader) {
        ProviderWebhookVerification verified = provider.verifyWebhookSignature(rawBody, signatureHeader);
        if (!verified.isValid()) {
            log.warn("Webhook rejected: {}", verified.getReason());
            throw new UnauthorizedAccessException("Webhook signature invalid");
        }
        if (verified.getEventId() == null || verified.getEventId().isBlank()) {
            log.warn("Webhook missing event id — refusing to dedup");
            throw new UnauthorizedAccessException("Webhook missing event id");
        }

        boolean firstDelivery = recorder.tryRecord(
                verified.getEventId(), verified.getEventType(), rawBody);
        if (!firstDelivery) {
            // Decision 4.2 — duplicate event id; already recorded. Return
            // success so Paymob stops retrying; the original processing is
            // still in flight (or already done).
            log.debug("Webhook duplicate dedup'd: {}", verified.getEventId());
        }
        return true;
    }
}
