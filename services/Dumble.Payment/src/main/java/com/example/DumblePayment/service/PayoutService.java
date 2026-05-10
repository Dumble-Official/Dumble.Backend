package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.Payout;
import com.example.DumblePayment.domain.enums.PayoutType;
import com.example.DumblePayment.dto.PayoutLookupResponse;
import com.example.DumblePayment.dto.PayoutRequest;
import com.example.DumblePayment.dto.PayoutResponse;
import com.example.DumblePayment.dto.WithdrawalRequest;
import com.example.DumblePayment.exception.ProviderException;
import com.example.DumblePayment.exception.ResourceNotFoundException;
import com.example.DumblePayment.provider.IPaymentProvider;
import com.example.DumblePayment.provider.dto.ProviderPayoutRequest;
import com.example.DumblePayment.provider.dto.ProviderPayoutResponse;
import com.example.DumblePayment.repository.PayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decision 6.1 + 6.2 — both money-out endpoints share this service. The
 * lifecycle is identical (PENDING → SENT → COMPLETED|FAILED, finalized by
 * webhook); only the eventual outbound event differs (WithdrawalCompleted
 * vs PayoutCompleted), which the persister resolves from {@link PayoutType}.
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);

    private final PayoutPersister persister;
    private final PayoutRepository payoutRepository;
    private final IPaymentProvider provider;

    public PayoutService(PayoutPersister persister,
                         PayoutRepository payoutRepository,
                         IPaymentProvider provider) {
        this.persister = persister;
        this.payoutRepository = payoutRepository;
        this.provider = provider;
    }

    public PayoutResponse requestWithdrawal(WithdrawalRequest req, String actor) {
        return dispatch(PayoutType.USER_WITHDRAWAL,
                req.getUserId(),
                req.getAmountCents(),
                req.getCurrency(),
                req.getDestination(),
                null,
                req.getCallerReference(),
                null,
                null,
                actor);
    }

    public PayoutResponse requestCohortPayout(PayoutRequest req, String actor) {
        return dispatch(PayoutType.COHORT_PAYOUT,
                req.getSellerId(),
                req.getAmountCents(),
                req.getCurrency(),
                req.getDestination(),
                req.getDestinationType(),
                req.getCallerReference(),
                req.getCohortKey(),
                req.getNotes(),
                actor);
    }

    public PayoutLookupResponse lookupByCallerReference(PayoutType type, String callerReference) {
        Payout p = payoutRepository.findByTypeAndCallerReference(type, callerReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
        return PayoutLookupResponse.from(p);
    }

    private PayoutResponse dispatch(PayoutType type,
                                    java.util.UUID subjectId,
                                    long amountCents,
                                    String currency,
                                    com.fasterxml.jackson.databind.JsonNode destination,
                                    String destinationType,
                                    String callerReference,
                                    String cohortKey,
                                    String notes,
                                    String actor) {
        Payout claimed = persister.persistPending(type, subjectId, amountCents,
                currency, destination, destinationType, callerReference, cohortKey, notes, actor);

        ProviderPayoutResponse providerResp;
        try {
            providerResp = provider.payout(ProviderPayoutRequest.builder()
                    .payoutId(claimed.getId())
                    .amountCents(amountCents)
                    .currency(claimed.getCurrency())
                    .destinationJson(claimed.getDestinationJson())
                    .destinationType(destinationType)
                    .memo(notes != null ? notes : (cohortKey != null ? "Dumble cohort " + cohortKey : null))
                    .build());
        } catch (ProviderException ex) {
            log.warn("Provider payout raised exception for {}: {}", claimed.getId(), ex.getMessage());
            return PayoutResponse.from(persister.markFailed(claimed.getId(), "provider_error"));
        }

        if (providerResp == null || providerResp.getOutcome() == null) {
            return PayoutResponse.from(persister.markFailed(claimed.getId(), "provider_no_response"));
        }
        return switch (providerResp.getOutcome()) {
            case PENDING -> {
                // Decision 6.3 — typical case: Paymob queued it, the actual
                // SENT/COMPLETED/FAILED arrives via webhook later. Persist
                // the provider's reference (when supplied) on the still-
                // PENDING row so the webhook can resolve via providerRef
                // even when callerReference lookup is ambiguous.
                Payout updated = providerResp.getProviderRef() == null
                        ? claimed
                        : persister.attachProviderRef(claimed.getId(), providerResp.getProviderRef());
                yield PayoutResponse.from(updated);
            }
            case SENT -> PayoutResponse.from(persister.markSent(claimed.getId(), providerResp.getProviderRef()));
            case FAILED -> PayoutResponse.from(persister.markFailed(claimed.getId(),
                    providerResp.getFailureReason() == null ? "provider_failed" : providerResp.getFailureReason()));
        };
    }
}
