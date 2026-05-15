package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.WebhookEvent;
import com.example.DumblePayment.domain.enums.WebhookProcessingStatus;
import com.example.DumblePayment.repository.WebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Decision 4.2 — Paymob event-id dedup INSERT in its own
 * {@code REQUIRES_NEW} transaction so a PK violation on a redelivered event
 * doesn't poison the surrounding tx.
 *
 * Catching {@link DataIntegrityViolationException} inside the SAME
 * {@code @Transactional} that flushed the failing INSERT is a known JPA
 * gotcha: Hibernate marks the session rollback-only on a flush-time
 * constraint violation, and the surrounding {@code @Transactional} commit
 * later throws {@code UnexpectedRollbackException}. Wallet's
 * {@code WebhookEventRecorder} hit exactly this pattern; same fix here.
 */
@Component
public class WebhookEventRecorder {

    private final WebhookEventRepository repository;

    public WebhookEventRecorder(WebhookEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns {@code true} if this is the first delivery of {@code eventId}
     * (newly inserted), {@code false} if Paymob already delivered it before
     * (PK violation caught here, in this REQUIRES_NEW tx, so the caller's
     * tx is unaffected).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryRecord(String eventId, String eventType, String rawBody) {
        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setEventType(eventType == null ? "unknown" : eventType);
        event.setPayloadJson(rawBody);
        event.setReceivedAt(Instant.now());
        event.setProcessingStatus(WebhookProcessingStatus.PENDING);
        event.setAttempts(0);
        try {
            repository.saveAndFlush(event);
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
    }
}
