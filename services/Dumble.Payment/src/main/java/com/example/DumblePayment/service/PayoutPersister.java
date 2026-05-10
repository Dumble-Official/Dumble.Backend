package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.Payout;
import com.example.DumblePayment.domain.enums.PayoutStatus;
import com.example.DumblePayment.domain.enums.PayoutType;
import com.example.DumblePayment.event.OutboxWriter;
import com.example.DumblePayment.exception.ResourceNotFoundException;
import com.example.DumblePayment.repository.PayoutRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DB-only mutations for both money-out flows. Same shape as ChargePersister:
 * each method commits in its own short tx so the orchestrator can keep the
 * provider HTTP call OUTSIDE any JPA tx.
 */
@Component
public class PayoutPersister {

    private final PayoutRepository payoutRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public PayoutPersister(PayoutRepository payoutRepository,
                           OutboxWriter outboxWriter,
                           AuditLogger auditLogger,
                           ObjectMapper objectMapper) {
        this.payoutRepository = payoutRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Payout persistPending(PayoutType type,
                                 UUID subjectId,
                                 long amountCents,
                                 String currency,
                                 JsonNode destination,
                                 String destinationType,
                                 String callerReference,
                                 String cohortKey,
                                 String notes,
                                 String actor) {
        Instant now = Instant.now();
        Payout p = new Payout();
        p.setType(type);
        p.setSubjectId(subjectId);
        p.setAmountCents(amountCents);
        p.setCurrency(currency == null ? "EGP" : currency);
        p.setDestinationJson(serialize(destination));
        p.setDestinationType(destinationType);
        p.setCallerReference(callerReference);
        p.setCohortKey(cohortKey);
        p.setNotes(notes);
        p.setStatus(PayoutStatus.PENDING);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        Payout saved = payoutRepository.saveAndFlush(p);
        auditLogger.log("PAYOUT", saved.getId().toString(), "PayoutAccepted",
                "SYSTEM", actor, null,
                Map.of("type", type.name(),
                        "amountCents", amountCents,
                        "callerReference", callerReference));
        return saved;
    }

    /**
     * Provider returned PENDING but supplied a providerRef — persist it on
     * the row so the eventual webhook can resolve via providerRef even if
     * callerReference lookup is ambiguous. No state transition.
     */
    @Transactional
    public Payout attachProviderRef(UUID id, String providerRef) {
        if (providerRef == null || providerRef.isBlank()) {
            return payoutRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
        }
        Payout p = lock(id);
        if (p.getStatus() != PayoutStatus.PENDING) return p;
        p.setProviderRef(providerRef);
        p.setUpdatedAt(Instant.now());
        payoutRepository.save(p);
        return p;
    }

    @Transactional
    public Payout markSent(UUID id, String providerRef) {
        Payout p = lock(id);
        if (p.getStatus() != PayoutStatus.PENDING) return p;
        p.setStatus(PayoutStatus.SENT);
        p.setProviderRef(providerRef);
        p.setUpdatedAt(Instant.now());
        payoutRepository.save(p);
        return p;
    }

    @Transactional
    public Payout markCompleted(UUID id, String providerRef) {
        Payout p = lock(id);
        if (p.getStatus() == PayoutStatus.COMPLETED) return p;
        Instant now = Instant.now();
        p.setStatus(PayoutStatus.COMPLETED);
        if (providerRef != null && !providerRef.isBlank()) {
            p.setProviderRef(providerRef);
        }
        p.setCompletedAt(now);
        p.setUpdatedAt(now);
        payoutRepository.save(p);

        String evt = p.getType() == PayoutType.USER_WITHDRAWAL ? "WithdrawalCompleted" : "PayoutCompleted";
        String rk  = p.getType() == PayoutType.USER_WITHDRAWAL ? "payment.withdrawal.completed" : "payment.payout.completed";
        outboxWriter.write(evt, rk,
                Map.of("payoutId", p.getId(),
                        "type", p.getType().name(),
                        "subjectId", p.getSubjectId(),
                        "amountCents", p.getAmountCents(),
                        "callerReference", p.getCallerReference(),
                        "providerRef", p.getProviderRef() == null ? "" : p.getProviderRef()));
        auditLogger.log("PAYOUT", p.getId().toString(), evt,
                "PROVIDER", providerRef, null,
                Map.of("type", p.getType().name(), "amountCents", p.getAmountCents()));
        return p;
    }

    @Transactional
    public Payout markFailed(UUID id, String reason) {
        Payout p = lock(id);
        if (p.getStatus() == PayoutStatus.FAILED || p.getStatus() == PayoutStatus.COMPLETED) return p;
        Instant now = Instant.now();
        p.setStatus(PayoutStatus.FAILED);
        p.setFailureReason(reason);
        p.setCompletedAt(now);
        p.setUpdatedAt(now);
        payoutRepository.save(p);

        String evt = p.getType() == PayoutType.USER_WITHDRAWAL ? "WithdrawalFailed" : "PayoutFailed";
        String rk  = p.getType() == PayoutType.USER_WITHDRAWAL ? "payment.withdrawal.failed" : "payment.payout.failed";
        outboxWriter.write(evt, rk,
                Map.of("payoutId", p.getId(),
                        "type", p.getType().name(),
                        "subjectId", p.getSubjectId(),
                        "amountCents", p.getAmountCents(),
                        "callerReference", p.getCallerReference(),
                        "reason", reason == null ? "unknown" : reason));
        auditLogger.log("PAYOUT", p.getId().toString(), evt,
                "PROVIDER", null, reason,
                Map.of("type", p.getType().name(), "amountCents", p.getAmountCents()));
        return p;
    }

    private Payout lock(UUID id) {
        return payoutRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
    }

    private String serialize(JsonNode destination) {
        try {
            return objectMapper.writeValueAsString(destination);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize destination", ex);
        }
    }
}
