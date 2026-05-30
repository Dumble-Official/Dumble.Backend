package com.example.DumbleAuthentication.event;

import com.example.DumbleAuthentication.domain.OutboxEvent;
import com.example.DumbleAuthentication.domain.OutboxStatus;
import com.example.DumbleAuthentication.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Writes a domain event to the outbox table in the caller's transaction. The caller passes the
 * already-serialized JSON body; the publish-to-broker step happens later in {@link OutboxPublisher},
 * so a rolled-back transaction emits nothing and a committed one cannot lose its event to a broker
 * hiccup.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;

    public OutboxWriter(OutboxEventRepository repository) {
        this.repository = repository;
    }

    public void write(String eventType, String routingKey, String payloadJson) {
        OutboxEvent e = new OutboxEvent();
        e.setEventType(eventType);
        e.setRoutingKey(routingKey);
        e.setPayloadJson(payloadJson);
        e.setStatus(OutboxStatus.PENDING);
        e.setAttempts(0);
        e.setCreatedAt(Instant.now());
        repository.save(e);
    }
}
