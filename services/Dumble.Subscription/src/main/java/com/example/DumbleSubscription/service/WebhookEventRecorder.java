package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.InboundWebhookEvent;
import com.example.DumbleSubscription.repository.InboundWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Carries the inbound-webhook dedup INSERT/DELETE in their own REQUIRES_NEW
 * transactions so the controller can use a saga-style atomic-or-compensate
 * pattern (merged_bug_001-run2):
 *
 *   1. tryClaim(eventId, ...) — inserts the dedup row in its own short tx.
 *      The PK on webhook_events_inbound serializes truly-concurrent peers;
 *      the loser sees DataIntegrityViolationException and gets back false.
 *   2. action runs in the controller's tx.
 *   3. If the action throws, releaseClaim(eventId) deletes the dedup row in
 *      ANOTHER short tx so Payment's retry can re-process.
 *
 * Doing the INSERT in REQUIRES_NEW (rather than saveAndFlush in the same tx)
 * avoids the JPA gotcha where a flush-time constraint violation marks the
 * EntityManager rollback-only and breaks the surrounding tx's commit.
 */
@Component
public class WebhookEventRecorder {

    private final InboundWebhookEventRepository repository;

    public WebhookEventRecorder(InboundWebhookEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(String eventId, String source, String type, String payloadSummary) {
        InboundWebhookEvent record = new InboundWebhookEvent();
        record.setEventId(eventId);
        record.setSource(source == null ? "unknown" : source);
        record.setEventType(type);
        record.setReceivedAt(Instant.now());
        record.setPayloadSummary(payloadSummary);
        try {
            repository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(String eventId) {
        repository.findById(eventId).ifPresent(repository::delete);
    }
}
