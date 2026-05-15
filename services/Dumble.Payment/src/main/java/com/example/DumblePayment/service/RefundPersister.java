package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.domain.Refund;
import com.example.DumblePayment.domain.enums.ChargeStatus;
import com.example.DumblePayment.domain.enums.RefundDestination;
import com.example.DumblePayment.domain.enums.RefundStatus;
import com.example.DumblePayment.event.OutboxWriter;
import com.example.DumblePayment.exception.BusinessRuleViolationException;
import com.example.DumblePayment.exception.ResourceNotFoundException;
import com.example.DumblePayment.repository.ChargeRepository;
import com.example.DumblePayment.repository.RefundRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DB-only mutations for the refund flow. Mirrors {@link ChargePersister} and
 * {@link PayoutPersister}: each method commits in its own short tx so the
 * orchestrator can keep the provider HTTP call outside any JPA tx.
 */
@Component
public class RefundPersister {

    private final RefundRepository refundRepository;
    private final ChargeRepository chargeRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;

    public RefundPersister(RefundRepository refundRepository,
                           ChargeRepository chargeRepository,
                           OutboxWriter outboxWriter,
                           AuditLogger auditLogger) {
        this.refundRepository = refundRepository;
        this.chargeRepository = chargeRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
    }

    /** Resolves the parent charge + persists the refund row in PENDING. */
    @Transactional
    public Refund persistPending(UUID chargeId, long amountCents, RefundDestination destination,
                                 String reason, String actor) {
        // Lock the parent charge for the duration of the validation so two
        // concurrent POSTs with distinct Idempotency-Keys can't each pass the
        // per-row check independently and end up over-refunding the wallet
        // (Decision 5.2 WALLET path skips Paymob, so there's no provider-side
        // dedup to catch the over-refund).
        Charge charge = chargeRepository.findByIdForUpdate(chargeId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
        if (charge.getStatus() != ChargeStatus.SUCCEEDED && charge.getStatus() != ChargeStatus.REVERSED) {
            throw new BusinessRuleViolationException(
                    "Cannot refund a charge in status " + charge.getStatus());
        }
        if (amountCents <= 0) {
            throw new BusinessRuleViolationException("Refund amount must be positive");
        }
        long alreadyRefunded = refundRepository.findByChargeId(charge.getId()).stream()
                .filter(r -> r.getStatus() != RefundStatus.FAILED)
                .mapToLong(Refund::getAmountCents)
                .sum();
        if (alreadyRefunded + amountCents > charge.getAmountCents()) {
            throw new BusinessRuleViolationException(
                    "Refund exceeds remaining refundable amount");
        }

        Instant now = Instant.now();
        Refund r = new Refund();
        r.setChargeId(charge.getId());
        r.setAmountCents(amountCents);
        r.setDestination(destination);
        r.setStatus(RefundStatus.PENDING);
        r.setInitiatedBy(actor);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        Refund saved = refundRepository.saveAndFlush(r);
        auditLogger.log("REFUND", saved.getId().toString(), "RefundAccepted",
                "SYSTEM", actor, reason,
                Map.of("chargeId", charge.getId().toString(),
                        "amountCents", saved.getAmountCents(),
                        "destination", destination.name()));
        return saved;
    }

    @Transactional
    public Refund markSucceeded(UUID id, String providerRef) {
        Refund r = refundRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));
        if (r.getStatus() != RefundStatus.PENDING) {
            return r;
        }
        Instant now = Instant.now();
        r.setStatus(RefundStatus.SUCCEEDED);
        r.setProviderRef(providerRef);
        r.setUpdatedAt(now);
        refundRepository.save(r);
        outboxWriter.write("RefundSucceeded", "payment.refund.succeeded",
                Map.of("refundId", r.getId(),
                        "chargeId", r.getChargeId(),
                        "amountCents", r.getAmountCents(),
                        "destination", r.getDestination().name()));
        return r;
    }

    @Transactional
    public Refund markFailed(UUID id, String reason) {
        Refund r = refundRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));
        if (r.getStatus() != RefundStatus.PENDING) {
            return r;
        }
        Instant now = Instant.now();
        r.setStatus(RefundStatus.FAILED);
        r.setFailureReason(reason);
        r.setUpdatedAt(now);
        refundRepository.save(r);
        outboxWriter.write("RefundFailed", "payment.refund.failed",
                Map.of("refundId", r.getId(),
                        "chargeId", r.getChargeId(),
                        "amountCents", r.getAmountCents(),
                        "reason", reason));
        return r;
    }

    /**
     * Provider returned PENDING — persist the providerRef so the eventual
     * webhook can resolve. No state transition; row stays PENDING.
     */
    @Transactional
    public Refund attachProviderRef(UUID id, String providerRef) {
        if (providerRef == null || providerRef.isBlank()) {
            return refundRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));
        }
        Refund r = refundRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));
        if (r.getStatus() != RefundStatus.PENDING) return r;
        r.setProviderRef(providerRef);
        r.setUpdatedAt(Instant.now());
        refundRepository.save(r);
        return r;
    }

    public Charge requireCharge(UUID id) {
        return chargeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
    }
}
