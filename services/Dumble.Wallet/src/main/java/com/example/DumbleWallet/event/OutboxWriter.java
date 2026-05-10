package com.example.DumbleWallet.event;

import com.example.DumbleWallet.domain.OutboxEvent;
import com.example.DumbleWallet.domain.enums.OutboxStatus;
import com.example.DumbleWallet.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Wallet PDF Decision 6.5 — writes a domain event to the outbox table inside
 * the calling transaction. The actual publish to RabbitMQ happens in
 * {@link OutboxPublisher} so a wallet movement that commits but fails to
 * publish doesn't go silent.
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
