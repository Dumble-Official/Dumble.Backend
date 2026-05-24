package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.SubscriptionEventLog;
import com.example.DumbleSubscription.repository.SubscriptionEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log per Subscription PDF Section 14. Distinct from the
 * outbox — this is for forensic reconstruction of "what happened to this
 * subscription" during disputes.
 *
 * <p>Two write modes:
 *
 * <ul>
 *   <li>{@link #log} — joins the caller's transaction (REQUIRED, the JPA
 *     default). The audit row commits or rolls back atomically with the
 *     business change it describes, so the audit trail never claims an
 *     event that didn't actually happen. Use this from business write paths
 *     (checkout, cancel, renewal, escrow release) where the audit is part
 *     of the same logical transaction.</li>
 *
 *   <li>{@link #logIndependent} — REQUIRES_NEW. Commits in its own
 *     transaction regardless of the caller's tx state. Use this from
 *     observation-only contexts where the parent transaction is read-only
 *     (reconciliation-style sweeps) or where the audit must survive a
 *     rollback of the parent. Without this, an audit write inside a
 *     read-only parent gets silently dropped on flush.</li>
 * </ul>
 *
 * Default {@code log} stays the cheap REQUIRED variant. A blanket
 * REQUIRES_NEW doubles per-operation Hikari connection demand AND orphans
 * audit rows referring to rolled-back business writes — both surfaced
 * during the Wallet level-up QA and are documented antipatterns here.
 */
@Component
public class AuditLogger {

    private final SubscriptionEventLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogger(SubscriptionEventLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(UUID subscriptionId,
                    String eventType,
                    String actor,
                    String actorId,
                    String reason,
                    Object payload) {
        persist(subscriptionId, eventType, actor, actorId, reason, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIndependent(UUID subscriptionId,
                               String eventType,
                               String actor,
                               String actorId,
                               String reason,
                               Object payload) {
        persist(subscriptionId, eventType, actor, actorId, reason, payload);
    }

    private void persist(UUID subscriptionId,
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
