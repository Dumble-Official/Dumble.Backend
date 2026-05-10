package com.example.DumbleSubscription.event;

import com.example.DumbleSubscription.domain.OutboxEvent;
import com.example.DumbleSubscription.domain.enums.OutboxStatus;
import com.example.DumbleSubscription.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Writes a domain event to the outbox table in the calling transaction.
 * Per PDF Decision 8.5 — the publish-to-broker step happens later in the
 * background worker; the in-transaction write guarantees no event is published
 * for a rolled-back transaction and no committed transaction loses its event.
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
