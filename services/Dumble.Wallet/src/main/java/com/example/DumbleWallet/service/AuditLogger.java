package com.example.DumbleWallet.service;

import com.example.DumbleWallet.domain.WalletEventLog;
import com.example.DumbleWallet.repository.WalletEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet PDF Decision 5.3 — append-only audit log distinct from the ledger.
 * Used for "who did what" forensic reconstruction during disputes (especially
 * admin reads / adjustments).
 *
 * <p>Two write modes:
 *
 * <ul>
 *   <li>{@link #log} — joins the caller's transaction (Propagation.REQUIRED,
 *     the JPA default). The audit row commits or rolls back atomically with
 *     the business change it describes, so the audit trail never claims an
 *     event that didn't actually happen. Use this from business write paths
 *     (credit / debit / withdrawal / admin adjust) where the audit is part
 *     of the same logical transaction.</li>
 *
 *   <li>{@link #logIndependent} — REQUIRES_NEW. Commits in its own
 *     transaction regardless of the caller's tx state. Use this from
 *     observation-only contexts (read-only parents like
 *     {@code ReconciliationJob}, or "we received X" notices where the
 *     event of receiving is auditable even if subsequent processing
 *     rolls back). Without this, an audit write inside a read-only parent
 *     gets silently dropped on flush and the audit row never lands.</li>
 * </ul>
 *
 * The default {@code log} method MUST stay the cheap REQUIRED variant to
 * avoid doubling per-operation connection demand (audit + business write
 * each holding a Hikari connection at peak load), which was the regression
 * caught during the level-up QA pass.
 */
@Component
public class AuditLogger {

    private final WalletEventLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogger(WalletEventLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(UUID walletUserId,
                    String eventType,
                    String actor,
                    String actorId,
                    String reason,
                    Object payload) {
        persist(walletUserId, eventType, actor, actorId, reason, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIndependent(UUID walletUserId,
                               String eventType,
                               String actor,
                               String actorId,
                               String reason,
                               Object payload) {
        persist(walletUserId, eventType, actor, actorId, reason, payload);
    }

    private void persist(UUID walletUserId,
                         String eventType,
                         String actor,
                         String actorId,
                         String reason,
                         Object payload) {
        WalletEventLog row = new WalletEventLog();
        row.setWalletUserId(walletUserId);
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
