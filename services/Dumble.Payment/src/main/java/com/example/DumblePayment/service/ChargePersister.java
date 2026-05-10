package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.domain.enums.ChargeStatus;
import com.example.DumblePayment.dto.ChargeRequest;
import com.example.DumblePayment.event.OutboxWriter;
import com.example.DumblePayment.exception.ResourceNotFoundException;
import com.example.DumblePayment.repository.ChargeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DB-only mutations for the charge flow (Decision 3.3 + the same shape Wallet
 * / Subscription use). Each method commits in its own short tx so the
 * controller-layer flow can keep the provider HTTP call OUTSIDE any JPA tx.
 */
@Component
public class ChargePersister {

    private final ChargeRepository chargeRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;

    public ChargePersister(ChargeRepository chargeRepository,
                           OutboxWriter outboxWriter,
                           AuditLogger auditLogger) {
        this.chargeRepository = chargeRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
    }

    /** Decision 3.3 — record the row in PENDING BEFORE the provider call. */
    @Transactional
    public Charge persistPending(ChargeRequest req, String actor) {
        Instant now = Instant.now();
        Charge c = new Charge();
        c.setUserId(req.getUserId());
        c.setAmountCents(req.getAmountCents());
        c.setCurrency(req.getCurrency() == null ? "EGP" : req.getCurrency());
        c.setPaymentMethodToken(req.getPaymentMethodToken());
        c.setDescription(req.getDescription());
        c.setCallerReference(req.getCallerReference());
        c.setStatus(ChargeStatus.PENDING);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        Charge saved = chargeRepository.saveAndFlush(c);
        auditLogger.log("CHARGE", saved.getId().toString(), "ChargeAccepted",
                "SYSTEM", actor, "persisted before provider call",
                Map.of("amountCents", saved.getAmountCents(),
                        "callerReference", saved.getCallerReference() == null ? "" : saved.getCallerReference()));
        return saved;
    }

    @Transactional
    public Charge markSucceeded(UUID id, String providerRef) {
        Charge c = lock(id);
        if (c.getStatus() != ChargeStatus.PENDING) {
            return c;
        }
        Instant now = Instant.now();
        c.setStatus(ChargeStatus.SUCCEEDED);
        c.setProviderRef(providerRef);
        c.setUpdatedAt(now);
        chargeRepository.save(c);
        auditLogger.log("CHARGE", c.getId().toString(), "ChargeSucceeded",
                "PROVIDER", providerRef, null,
                Map.of("amountCents", c.getAmountCents(), "providerRef", providerRef));
        outboxWriter.write("ChargeSucceeded", "payment.charge.succeeded",
                Map.of("chargeId", c.getId(),
                        "userId", c.getUserId(),
                        "amountCents", c.getAmountCents(),
                        "providerRef", providerRef,
                        "callerReference", c.getCallerReference() == null ? "" : c.getCallerReference()));
        return c;
    }

    @Transactional
    public Charge markFailed(UUID id, String reason, String providerRef) {
        Charge c = lock(id);
        if (c.getStatus() != ChargeStatus.PENDING) {
            return c;
        }
        Instant now = Instant.now();
        c.setStatus(ChargeStatus.FAILED);
        c.setFailureReason(reason);
        if (providerRef != null && !providerRef.isBlank()) {
            c.setProviderRef(providerRef);
        }
        c.setUpdatedAt(now);
        chargeRepository.save(c);
        auditLogger.log("CHARGE", c.getId().toString(), "ChargeFailed",
                "PROVIDER", providerRef, reason,
                Map.of("amountCents", c.getAmountCents()));
        outboxWriter.write("ChargeFailed", "payment.charge.failed",
                Map.of("chargeId", c.getId(),
                        "userId", c.getUserId(),
                        "amountCents", c.getAmountCents(),
                        "reason", reason == null ? "unknown" : reason,
                        "callerReference", c.getCallerReference() == null ? "" : c.getCallerReference()));
        return c;
    }

    @Transactional
    public Charge markPendingProviderRef(UUID id, String providerRef) {
        Charge c = lock(id);
        if (c.getStatus() != ChargeStatus.PENDING) {
            return c;
        }
        c.setProviderRef(providerRef);
        c.setUpdatedAt(Instant.now());
        chargeRepository.save(c);
        return c;
    }

    @Transactional
    public Charge markReversed(UUID id, String reason) {
        Charge c = lock(id);
        if (c.getStatus() == ChargeStatus.REVERSED) {
            return c;
        }
        c.setStatus(ChargeStatus.REVERSED);
        c.setFailureReason(reason);
        c.setUpdatedAt(Instant.now());
        chargeRepository.save(c);
        auditLogger.log("CHARGE", c.getId().toString(), "ChargebackFiled",
                "PROVIDER", c.getProviderRef(), reason, null);
        outboxWriter.write("ChargebackFiled", "payment.chargeback.filed",
                Map.of("chargeId", c.getId(),
                        "userId", c.getUserId(),
                        "amountCents", c.getAmountCents(),
                        "callerReference", c.getCallerReference() == null ? "" : c.getCallerReference(),
                        "reason", reason == null ? "" : reason));
        return c;
    }

    private Charge lock(UUID id) {
        return chargeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
    }
}
