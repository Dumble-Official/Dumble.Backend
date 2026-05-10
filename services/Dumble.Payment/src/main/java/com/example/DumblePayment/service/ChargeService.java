package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.dto.ChargeRequest;
import com.example.DumblePayment.dto.ChargeResponse;
import com.example.DumblePayment.exception.ProviderException;
import com.example.DumblePayment.exception.ResourceNotFoundException;
import com.example.DumblePayment.provider.IPaymentProvider;
import com.example.DumblePayment.provider.dto.ProviderChargeRequest;
import com.example.DumblePayment.provider.dto.ProviderChargeResponse;
import com.example.DumblePayment.repository.ChargeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Decision 3.1 + 3.3 orchestration:
 *   1. Persist Charge in PENDING (Phase 1, short tx).
 *   2. Call IPaymentProvider OUTSIDE any tx (so the JPA connection isn't
 *      pinned for the round-trip — same shape Wallet uses for its
 *      Phase-1/Phase-2 split).
 *   3. Persist terminal state (Phase 3, short tx).
 *
 * The whole flow is wrapped by IdempotencyService at the controller layer,
 * so a retry with the same Idempotency-Key replays the cached response
 * without rerunning Phases 1-3.
 */
@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private final ChargePersister persister;
    private final ChargeRepository chargeRepository;
    private final IPaymentProvider provider;

    public ChargeService(ChargePersister persister,
                         ChargeRepository chargeRepository,
                         IPaymentProvider provider) {
        this.persister = persister;
        this.chargeRepository = chargeRepository;
        this.provider = provider;
    }

    public ChargeResponse charge(ChargeRequest req, String actor) {
        Charge claimed = persister.persistPending(req, actor);

        ProviderChargeResponse providerResp;
        try {
            providerResp = provider.charge(ProviderChargeRequest.builder()
                    .chargeId(claimed.getId())
                    .userId(req.getUserId())
                    .amountCents(req.getAmountCents())
                    .currency(claimed.getCurrency())
                    .paymentMethodToken(req.getPaymentMethodToken())
                    .description(req.getDescription())
                    .build());
        } catch (ProviderException ex) {
            log.warn("Provider charge raised exception for {}: {}", claimed.getId(), ex.getMessage());
            return ChargeResponse.from(persister.markFailed(claimed.getId(), "provider_error", null));
        }

        if (providerResp == null || providerResp.getOutcome() == null) {
            return ChargeResponse.from(persister.markFailed(claimed.getId(), "provider_no_response", null));
        }
        switch (providerResp.getOutcome()) {
            case SUCCEEDED -> {
                return ChargeResponse.from(persister.markSucceeded(claimed.getId(), providerResp.getProviderRef()));
            }
            case PENDING -> {
                // Provider needs more time (3DS/OTP, async confirmation). Persist
                // the providerRef so the inbound webhook can resolve us, and
                // return Pending — Subscription's Pending-handling path picks
                // it up.
                return ChargeResponse.from(
                        persister.markPendingProviderRef(claimed.getId(), providerResp.getProviderRef()));
            }
            case FAILED -> {
                return ChargeResponse.from(persister.markFailed(
                        claimed.getId(),
                        providerResp.getFailureReason() == null ? "provider_failed" : providerResp.getFailureReason(),
                        providerResp.getProviderRef()));
            }
            default -> {
                return ChargeResponse.from(persister.markFailed(claimed.getId(), "provider_unknown_outcome", null));
            }
        }
    }

    public ChargeResponse get(UUID id) {
        Charge c = chargeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
        return ChargeResponse.from(c);
    }
}
