package com.example.DumbleAuthentication.event;

import com.example.DumbleAuthentication.domain.OutboxEvent;
import com.example.DumbleAuthentication.domain.OutboxStatus;
import com.example.DumbleAuthentication.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Writes a domain event to the outbox table in the caller's transaction. The publish-to-broker step
 * happens later in {@link OutboxPublisher}, so a rolled-back transaction emits nothing and a
 * committed one cannot lose its event to a broker hiccup.
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
