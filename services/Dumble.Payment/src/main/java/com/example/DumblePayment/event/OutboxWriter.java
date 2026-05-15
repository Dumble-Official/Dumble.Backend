package com.example.DumblePayment.event;

import com.example.DumblePayment.domain.OutboxEvent;
import com.example.DumblePayment.domain.enums.OutboxStatus;
import com.example.DumblePayment.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Writes a domain event to the outbox table in the calling transaction
 * (Decision 9.4). Background worker publishes; mark-as-sent only on broker
 * ACK. Without it, a successful charge could commit to DB but the
 * {@code ChargeSucceeded} event silently fail to publish — Subscription
 * never activates the sub, user paid but nothing happened.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void write(String eventType, String routingKey, Object payload) {
        OutboxEvent e = new OutboxEvent();
        e.setEventType(eventType);
        e.setRoutingKey(routingKey);
        e.setPayloadJson(toJson(payload));
        e.setStatus(OutboxStatus.PENDING);
        e.setAttempts(0);
        e.setCreatedAt(Instant.now());
        repository.save(e);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
