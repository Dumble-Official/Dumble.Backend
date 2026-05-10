package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.domain.Refund;
import com.example.DumblePayment.domain.enums.RefundDestination;
import com.example.DumblePayment.dto.RefundRequest;
import com.example.DumblePayment.dto.RefundResponse;
import com.example.DumblePayment.exception.BusinessRuleViolationException;
import com.example.DumblePayment.exception.ProviderException;
import com.example.DumblePayment.provider.IPaymentProvider;
import com.example.DumblePayment.provider.dto.ProviderRefundRequest;
import com.example.DumblePayment.provider.dto.ProviderRefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decision 5.2 — refund endpoint defaults to the WALLET path: Payment skips
 * Paymob, returns success, and the caller is expected to credit the user's
 * wallet separately. The {@code ORIGINAL_METHOD} path calls the provider's
 * refund API (Decision 5.1, used for chargebacks + admin force-refunds).
 *
 * Orchestration shape mirrors {@link ChargeService} — no {@code @Transactional}
 * on the public method, the HTTP call sits between two short DB transactions
 * carried by {@link RefundPersister}.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundPersister persister;
    private final IPaymentProvider provider;

    public RefundService(RefundPersister persister, IPaymentProvider provider) {
        this.persister = persister;
        this.provider = provider;
    }

    public RefundResponse refund(RefundRequest req, String actor) {
        RefundDestination destination = parseDestination(req.getDestination());

        // Phase 1 — persist PENDING + validate against the parent charge.
        Refund refund = persister.persistPending(
                req.getChargeId(), req.getAmountCents(), destination, req.getReason(), actor);

        if (destination == RefundDestination.WALLET) {
            // Decision 5.2 — Payment skips Paymob, mark Succeeded; the caller
            // credits Wallet separately. No HTTP, so this stays one phase.
            return RefundResponse.from(persister.markSucceeded(refund.getId(), null));
        }

        // ORIGINAL_METHOD — Decision 5.1: call the provider OUTSIDE any tx so
        // the JPA connection isn't pinned for the round-trip.
        Charge charge = persister.requireCharge(req.getChargeId());
        ProviderRefundResponse pr;
        try {
            pr = provider.refund(ProviderRefundRequest.builder()
                    .chargeProviderRef(charge.getProviderRef())
                    .amountCents(req.getAmountCents())
                    .reason(req.getReason())
                    .build());
        } catch (ProviderException ex) {
            log.warn("Provider refund raised exception for {}: {}", refund.getId(), ex.getMessage());
            return RefundResponse.from(persister.markFailed(refund.getId(), "provider_error"));
        }

        if (pr == null || pr.getOutcome() == null) {
            return RefundResponse.from(persister.markFailed(refund.getId(), "provider_no_response"));
        }
        return switch (pr.getOutcome()) {
            case SUCCEEDED -> RefundResponse.from(persister.markSucceeded(refund.getId(), pr.getProviderRef()));
            case PENDING -> {
                // Persist providerRef so the eventual webhook can resolve.
                Refund updated = pr.getProviderRef() == null
                        ? refund
                        : persister.attachProviderRef(refund.getId(), pr.getProviderRef());
                yield RefundResponse.from(updated);
            }
            case FAILED -> RefundResponse.from(persister.markFailed(refund.getId(),
                    pr.getFailureReason() == null ? "provider_failed" : pr.getFailureReason()));
        };
    }

    private RefundDestination parseDestination(String raw) {
        if (raw == null) throw new BusinessRuleViolationException("destination is required");
        try {
            return RefundDestination.valueOf(raw.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Unknown refund destination: " + raw);
        }
    }
}
