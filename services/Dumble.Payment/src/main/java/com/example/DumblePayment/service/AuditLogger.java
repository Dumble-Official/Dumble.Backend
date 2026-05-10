package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.PaymentEventLog;
import com.example.DumblePayment.repository.PaymentEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Append-only audit log for forensic reconstruction. Distinct from the
 * outbox — this is for "what happened to this charge / payout / webhook"
 * during disputes, not for downstream consumers.
 */
@Component
public class AuditLogger {

    private final PaymentEventLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogger(PaymentEventLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(String subjectType,
                    String subjectId,
                    String eventType,
                    String actor,
                    String actorId,
                    String reason,
                    Object payload) {
        PaymentEventLog row = new PaymentEventLog();
        row.setSubjectType(subjectType);
        row.setSubjectId(subjectId);
        row.setEventType(eventType);
        row.setTimestamp(Instant.now());
        row.setActor(actor);
        row.setActorId(actorId);
        row.setReason(reason);
        row.setPayloadJson(payload == null ? null : toJson(payload));
        repository.save(row);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"_error\":\"serialize_failed\"}";
        }
    }
}
