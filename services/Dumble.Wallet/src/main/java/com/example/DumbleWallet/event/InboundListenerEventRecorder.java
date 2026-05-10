package com.example.DumbleWallet.event;

import com.example.DumbleWallet.domain.InboundListenerEvent;
import com.example.DumbleWallet.repository.InboundListenerEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Inbound dedup INSERT in its OWN transaction so the duplicate-key violation
 * lives in a sub-tx whose rollback-only marker is local. Catching
 * {@link DataIntegrityViolationException} inside the SAME {@code @Transactional}
 * that flushed the failing INSERT is a known JPA gotcha: Hibernate marks the
 * session rollback-only on a flush-time constraint violation, and the
 * surrounding {@code @Transactional} commit later throws
 * {@code UnexpectedRollbackException}. Payment's {@code WebhookEventRecorder}
 * works around exactly this — same fix mirrored here.
 */
@Component
public class InboundListenerEventRecorder {

    private final InboundListenerEventRepository repository;

    public InboundListenerEventRecorder(InboundListenerEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryRecord(String eventId, String routingKey, String payloadSummary) {
        InboundListenerEvent dedup = new InboundListenerEvent();
        dedup.setEventId(eventId);
        dedup.setRoutingKey(routingKey);
        dedup.setReceivedAt(Instant.now());
        dedup.setPayloadSummary(payloadSummary);
        try {
            repository.saveAndFlush(dedup);
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
    }
}
