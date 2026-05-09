package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.SubscriptionEventLog;
import com.example.DumbleSubscription.repository.SubscriptionEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log per Subscription PDF Section 14. Distinct from the
 * outbox — this is for forensic reconstruction of "what happened to this
 * subscription" during disputes.
 */
@Component
public class AuditLogger {

    private final SubscriptionEventLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogger(SubscriptionEventLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(UUID subscriptionId,
                    String eventType,
                    String actor,
                    String actorId,
                    String reason,
                    Object payload) {
        SubscriptionEventLog log = new SubscriptionEventLog();
        log.setSubscriptionId(subscriptionId);
        log.setEventType(eventType);
        log.setTimestamp(Instant.now());
        log.setActor(actor);
        log.setActorId(actorId);
        log.setReason(reason);
        log.setPayloadJson(payload == null ? null : toJson(payload));
        repository.save(log);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"_error\":\"serialize_failed\"}";
        }
    }
}
