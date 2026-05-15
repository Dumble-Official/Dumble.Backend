package com.example.DumbleWallet.service;

import com.example.DumbleWallet.domain.WalletEventLog;
import com.example.DumbleWallet.repository.WalletEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet PDF Decision 5.3 — append-only audit log distinct from the ledger.
 * Used for "who did what" forensic reconstruction during disputes (especially
 * admin reads / adjustments).
 */
@Component
public class AuditLogger {

    private final WalletEventLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogger(WalletEventLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(UUID walletUserId,
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
