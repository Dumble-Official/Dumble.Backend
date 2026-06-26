package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.domain.enums.ChargeStatus;
import com.example.DumblePayment.dto.ChargeRequest;
import com.example.DumblePayment.dto.ChargeResponse;
import com.example.DumblePayment.dto.CheckoutRequest;
import com.example.DumblePayment.dto.CheckoutResponse;
import com.example.DumblePayment.exception.ProviderException;
import com.example.DumblePayment.exception.ResourceNotFoundException;
import com.example.DumblePayment.provider.IPaymentProvider;
import com.example.DumblePayment.provider.dto.ProviderChargeRequest;
import com.example.DumblePayment.provider.dto.ProviderChargeResponse;
import com.example.DumblePayment.provider.dto.ProviderHostedCheckoutRequest;
import com.example.DumblePayment.provider.dto.ProviderHostedCheckoutResponse;
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

    /**
     * Create an interactive hosted-checkout (iframe) session. Same Phase-1/Phase-2
     * shape as {@link #charge}: persist a PENDING charge first (so a lost response
     * is still reconcilable), then ask Paymob for the iframe. The Paymob order is
     * keyed by merchant_order_id = charge id, which the webhook uses to reconcile
     * back to this charge; callerReference carries the purpose for downstream
     * consumers. The charge stays PENDING until the webhook flips it.
     */
    public CheckoutResponse createCheckout(CheckoutRequest req, String actor) {
        ChargeRequest pendingReq = new ChargeRequest();
        pendingReq.setUserId(req.getUserId());
        pendingReq.setAmountCents(req.getAmountCents());
        pendingReq.setCurrency(req.getCurrency() == null ? "EGP" : req.getCurrency());
        pendingReq.setDescription(req.getDescription());
        pendingReq.setCallerReference(req.getCallerReference());

        Charge claimed = persister.persistPending(pendingReq, actor);

        try {
            ProviderHostedCheckoutResponse resp = provider.createHostedCheckout(
                    new ProviderHostedCheckoutRequest(
                            claimed.getAmountCents(),
                            claimed.getCurrency(),
                            claimed.getId().toString(),
                            req.getEmail(),
                            req.getFirstName(),
                            req.getLastName(),
                            req.getPhone()));
            // Store Paymob's order id as the provider ref for traceability; the
            // webhook still reconciles via merchant_order_id = charge id, and
            // markSucceeded overwrites this with the transaction id on success.
            persister.markPendingProviderRef(claimed.getId(), resp.paymobOrderId());
            return new CheckoutResponse(claimed.getId(), ChargeStatus.PENDING.name(),
                    resp.iframeUrl(), resp.paymobOrderId());
        } catch (ProviderException ex) {
            log.warn("Hosted checkout failed for charge {}: {}", claimed.getId(), ex.getMessage());
            // Mark the abandoned charge FAILED, then rethrow so the idempotency
            // layer RELEASES the claim (releaseOnFailure) — otherwise a stable
            // idempotency key would cache this failure and every retry would
            // replay it instead of re-attempting the (now working) provider call.
            persister.markFailed(claimed.getId(), "checkout_init_failed", null);
            throw ex;
        }
    }

    public ChargeResponse get(UUID id) {
        Charge c = chargeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
        return ChargeResponse.from(c);
    }
}
