package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.PaymentEventLog;
import com.example.DumblePayment.repository.PaymentEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Append-only audit log for forensic reconstruction. Distinct from the
 * outbox — this is for "what happened to this charge / payout / webhook"
 * during disputes, not for downstream consumers.
 *
 * <p>Two write modes:
 *
 * <ul>
 *   <li>{@link #log} — joins the caller's transaction (Propagation.REQUIRED).
 *     The audit row commits or rolls back atomically with the business
 *     change it describes, so the audit trail never claims an event that
 *     didn't actually happen. Use this from business write paths (charge
 *     persist, refund persist, payout persist) where the audit is part of
 *     the same logical transaction.</li>
 *
 *   <li>{@link #logIndependent} — REQUIRES_NEW. Commits in its own
 *     transaction regardless of the caller's tx state. Use this from
 *     observation-only contexts where the parent transaction is read-only,
 *     or where the audit must survive a rollback of the parent (e.g.
 *     "received webhook" — even if processing fails, the receipt is
 *     auditable). Without this, an audit write inside a read-only parent
 *     gets silently dropped on flush and the audit row never lands.</li>
 * </ul>
 *
 * Default {@code log} MUST stay the REQUIRED variant — REQUIRES_NEW doubles
 * Hikari connection demand on every business operation (audit + business
 * write each holding their own connection at peak load), which surfaces as
 * pool-exhaustion 5xx under modest concurrency.
 */
@Component
public class AuditLogger {

    private final PaymentEventLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogger(PaymentEventLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String subjectType,
                    String subjectId,
                    String eventType,
                    String actor,
                    String actorId,
                    String reason,
                    Object payload) {
        persist(subjectType, subjectId, eventType, actor, actorId, reason, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIndependent(String subjectType,
                               String subjectId,
                               String eventType,
                               String actor,
                               String actorId,
                               String reason,
                               Object payload) {
        persist(subjectType, subjectId, eventType, actor, actorId, reason, payload);
    }

    private void persist(String subjectType,
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
