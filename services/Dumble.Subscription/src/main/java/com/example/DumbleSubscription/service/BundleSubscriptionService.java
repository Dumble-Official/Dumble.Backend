package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.BundleManagementClient;
import com.example.DumbleSubscription.client.GymServiceClient;
import com.example.DumbleSubscription.client.PaymentServiceClient;
import com.example.DumbleSubscription.client.WalletServiceClient;
import com.example.DumbleSubscription.client.dto.BundleSnapshot;
import com.example.DumbleSubscription.client.dto.ChargeRequest;
import com.example.DumbleSubscription.client.dto.ChargeResponse;
import com.example.DumbleSubscription.client.dto.CheckoutRequest;
import com.example.DumbleSubscription.client.dto.CheckoutResponse;
import com.example.DumbleSubscription.client.dto.PromoValidationResponse;
import com.example.DumbleSubscription.client.dto.WalletDebitRequest;
import com.example.DumbleSubscription.client.dto.WalletSummaryResponse;
import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.PaymentMethodType;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.BundleCheckoutRequest;
import com.example.DumbleSubscription.dto.BundleSubscriptionResponse;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.exception.BusinessRuleViolationException;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.util.CohortKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bundle subscription checkout — the flagship participant flow.
 *
 * Orchestration shape (bug_014):
 *   1. Pre-tx HTTP + read-only validation: bundle snapshot, seller-lifecycle
 *      guard, dup-active/pending check, promo validation.
 *   2. Phase-1 short tx (BundleCheckoutPersister.claimPending) — insert a
 *      PENDING row to (a) hold the unique-active partial-index slot and
 *      (b) provide a stable sub.id to salt the downstream Idempotency-Key.
 *   3. Pre-tx HTTP: Wallet.debit OR Payment.charge with the salted key.
 *   4. Phase-2 short tx (activate / markPending / releasePending) — flip
 *      the row state, mint escrow + receipt + audit + outbox.
 *
 * The HTTP calls never run inside a JPA transaction, so the Hikari pool
 * isn't pinned for the WebClient round-trip budget.
 */
@Service
public class BundleSubscriptionService {

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final BundleManagementClient bundleManagementClient;
    private final GymServiceClient gymServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final ReceiptService receiptService;
    private final SellerLifecycleService sellerLifecycleService;
    private final BundleCheckoutPersister persister;
    private final ObjectMapper objectMapper;

    public BundleSubscriptionService(BundleSubscriptionRepository bundleSubscriptionRepository,
                                     EscrowEntryRepository escrowEntryRepository,
                                     BundleManagementClient bundleManagementClient,
                                     GymServiceClient gymServiceClient,
                                     WalletServiceClient walletServiceClient,
                                     PaymentServiceClient paymentServiceClient,
                                     OutboxWriter outboxWriter,
                                     AuditLogger auditLogger,
                                     ReceiptService receiptService,
                                     SellerLifecycleService sellerLifecycleService,
                                     BundleCheckoutPersister persister,
                                     ObjectMapper objectMapper) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.escrowEntryRepository = escrowEntryRepository;
        this.bundleManagementClient = bundleManagementClient;
        this.gymServiceClient = gymServiceClient;
        this.walletServiceClient = walletServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.receiptService = receiptService;
        this.sellerLifecycleService = sellerLifecycleService;
        this.persister = persister;
        this.objectMapper = objectMapper;
    }

    /**
     * Hosted-checkout (Paymob iframe) bundle purchase. Same claim-PENDING shape as
     * {@link #checkout} but instead of debiting the wallet or charging a saved
     * token, it creates a Paymob hosted-checkout session and returns the iframe
     * URL for the app's WebView. The card is entered on Paymob's page; the
     * charge.succeeded webhook (callerReference {@code bundle-sub:<subId>}) then
     * activates the subscription via {@link #confirmPendingCharge}. The
     * deterministic idempotency key means a retry replays the same session; an
     * in-flight PENDING sub is reused rather than duplicated.
     */
    public CheckoutResponse createBundleCheckout(UUID participantId, UUID bundleId) {
        BundleSnapshot bundle = bundleManagementClient.getBundle(bundleId);
        if (bundle == null || !bundle.isActive()) {
            throw new ResourceNotFoundException("Bundle not available");
        }
        if (!sellerLifecycleService.canAcceptNewSubscriptions(bundle.getSellerId())) {
            throw new BusinessRuleViolationException("This seller is not currently accepting new subscriptions");
        }

        Optional<BundleSubscription> existing =
                bundleSubscriptionRepository.findActiveOrPending(participantId, bundle.getId());
        if (existing.isPresent() && existing.get().getStatus() == SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("You already have an active subscription to this bundle");
        }

        long amountToCharge = bundle.getPriceCents();
        // Reuse an in-flight PENDING (e.g. the user abandoned an earlier attempt)
        // so we don't trip the unique active/pending index; the deterministic key
        // then replays the same Paymob session.
        BundleSubscription claimed = (existing.isPresent()
                && existing.get().getStatus() == SubscriptionStatus.PENDING)
                ? existing.get()
                : persister.claimPending(participantId, bundle, amountToCharge,
                        PaymentMethodType.CARD, null,
                        serializeAmenities(bundle.getAmenities()), null, null);

        String key = stableCheckoutKey(participantId, bundle.getId(), amountToCharge, claimed.getId());
        CheckoutResponse checkout;
        try {
            checkout = paymentServiceClient.createCheckout(key, CheckoutRequest.builder()
                    .userId(participantId)
                    .amountCents(amountToCharge)
                    .currency(bundle.getCurrency())
                    .description("Subscribe to " + bundle.getName())
                    .callerReference("bundle-sub:" + claimed.getId())
                    .build());
        } catch (RuntimeException ex) {
            persister.releasePending(claimed.getId());
            throw ex;
        }
        if (checkout == null || checkout.getIframeUrl() == null || checkout.getIframeUrl().isBlank()) {
            persister.releasePending(claimed.getId());
            throw new BusinessRuleViolationException("Checkout initialization failed");
        }
        persister.markPending(claimed.getId(), checkout.getProviderRef());
        return checkout;
    }

    public BundleSubscriptionResponse checkout(UUID participantId, BundleCheckoutRequest req) {
        // 1. Pre-tx HTTP — bundle snapshot
        BundleSnapshot bundle = bundleManagementClient.getBundle(req.getBundleId());
        if (bundle == null || !bundle.isActive()) {
            throw new ResourceNotFoundException("Bundle not available");
        }
        // Sections 16, 17 — block new subs when seller is Frozen / WindingDown / Banned / Closed.
        if (!sellerLifecycleService.canAcceptNewSubscriptions(bundle.getSellerId())) {
            throw new BusinessRuleViolationException("This seller is not currently accepting new subscriptions");
        }

        // 2. Pre-check — dup over ACTIVE OR PENDING (bug_029-run2). PENDING
        //    means a previous attempt is awaiting Paymob OTP/3DS; surface the
        //    in-flight row instead of starting a second charge.
        Optional<BundleSubscription> existing =
                bundleSubscriptionRepository.findActiveOrPending(participantId, bundle.getId());
        if (existing.isPresent()) {
            BundleSubscription s = existing.get();
            if (s.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new BusinessRuleViolationException("You already have an active subscription to this bundle");
            }
            // PENDING — return its current state. The original charge is the
            // one still in flight; client should resolve OTP for it.
            return BundleSubscriptionResponse.from(s);
        }

        // 3. Pre-tx HTTP — promo validation
        long priceCents = bundle.getPriceCents();
        long discountCents = 0;
        PromoValidationResponse promo = null;
        if (req.getPromoCode() != null && !req.getPromoCode().isBlank()) {
            promo = gymServiceClient.validatePromoCode(bundle.getSellerId(), req.getPromoCode());
            if (promo != null) {
                if (!promo.isValid()) {
                    throw new BusinessRuleViolationException("Promo code: " + promo.getReason());
                }
                discountCents = Math.min(promo.getDiscountCents(), priceCents);
            }
        }
        long amountToCharge = priceCents - discountCents;
        boolean useWallet = req.isUseWalletBalance();

        // 4. Phase 1 — claim a PENDING row in its own short tx. The unique
        //    partial index over (participant, bundle) WHERE status IN
        //    ('ACTIVE','PENDING') is the actual race gate; on conflict we
        //    surface the row that won the race.
        boolean paidFromWallet = false;
        BundleSubscription claimed;
        try {
            claimed = persister.claimPending(participantId, bundle, amountToCharge,
                    resolvePaymentMethodType(req, useWallet),
                    useWallet ? null : req.getPaymentMethodToken(),
                    serializeAmenities(bundle.getAmenities()),
                    req.getPromoCode(),
                    discountCents > 0 ? discountCents : null);
        } catch (DataIntegrityViolationException dup) {
            // Lost the race — return whatever now sits in the slot.
            BundleSubscription racingWinner = bundleSubscriptionRepository
                    .findActiveOrPending(participantId, bundle.getId())
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            "Conflict during checkout; please retry"));
            if (racingWinner.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new BusinessRuleViolationException("You already have an active subscription to this bundle");
            }
            return BundleSubscriptionResponse.from(racingWinner);
        }

        // 5. Pre-tx HTTP — Wallet (if balance covers) OR Payment.
        //    Idempotency key (bug_003-run2): salted with claimed.getId() so
        //    legitimate fresh purchases (after refund/expiry/etc) get fresh
        //    keys, while same-attempt retries reuse the same key.
        String checkoutIntentId = stableCheckoutKey(participantId, bundle.getId(), amountToCharge, claimed.getId());
        String providerRef = null;
        boolean paymentPending = false;
        String pendingProviderRef = null;
        try {
            if (useWallet) {
                WalletSummaryResponse summary = walletServiceClient.summary(participantId);
                long available = summary != null ? summary.getAvailableCents() : 0L;
                if (available < amountToCharge) {
                    // The wallet path carries no card token, so there's nothing to
                    // fall through to — fail cleanly instead of attempting a
                    // token-less charge (which previously 500'd).
                    persister.releasePending(claimed.getId());
                    throw new BusinessRuleViolationException("Insufficient wallet balance");
                }
                walletServiceClient.debit(checkoutIntentId,
                        new WalletDebitRequest(participantId, amountToCharge, "InAppSpend", checkoutIntentId));
                paidFromWallet = true;
            }
            if (!paidFromWallet) {
                ChargeResponse charge = paymentServiceClient.charge(checkoutIntentId, ChargeRequest.builder()
                        .userId(participantId)
                        .amountCents(amountToCharge)
                        .currency(bundle.getCurrency())
                        .paymentMethodToken(req.getPaymentMethodToken())
                        .description("Subscribe to " + bundle.getName())
                        .callerReference(checkoutIntentId)
                        .build());
                if (charge == null) {
                    persister.releasePending(claimed.getId());
                    throw new BusinessRuleViolationException("Payment failed");
                }
                String chargeStatus = charge.getStatus();
                if ("Succeeded".equalsIgnoreCase(chargeStatus)) {
                    providerRef = charge.getProviderRef();
                } else if ("Pending".equalsIgnoreCase(chargeStatus)) {
                    paymentPending = true;
                    pendingProviderRef = charge.getProviderRef();
                } else {
                    persister.releasePending(claimed.getId());
                    throw new BusinessRuleViolationException("Payment failed");
                }
            }
        } catch (BusinessRuleViolationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            persister.releasePending(claimed.getId());
            throw ex;
        }

        // 6. Phase 2 — finalize row state in a short tx.
        if (paymentPending) {
            return persister.markPending(claimed.getId(), pendingProviderRef);
        }
        return persister.activate(claimed.getId(), providerRef, paidFromWallet,
                checkoutIntentId, promo, req.getPromoCode(), discountCents);
    }

    /**
     * bug_029 — completes a sub that was left PENDING after a Paymob OTP/3DS
     * confirmation. Idempotent on subscriptionId + status; safe to call from
     * the payment.charge.completed webhook even on retried delivery.
     */
    @Transactional
    public void confirmPendingCharge(UUID subscriptionId, String providerRef) {
        BundleSubscription sub = bundleSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.PENDING) {
            return;
        }
        Instant now = Instant.now();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        if (providerRef != null && !providerRef.isBlank()) {
            sub.setProviderRef(providerRef);
        }
        sub.setUpdatedAt(now);
        bundleSubscriptionRepository.save(sub);

        createEscrowEntries(sub, sub.getPricePaidCents());

        String checkoutIntentId = stableCheckoutKey(
                sub.getParticipantId(), sub.getBundleId(), sub.getPricePaidCents(), sub.getId());
        receiptService.issueForBundleSubscription(sub.getParticipantId(), sub.getId(), checkoutIntentId,
                sub.getPricePaidCents(), sub.getCurrency(), sub.getBundleName(), sub.getDurationDays());

        auditLogger.log(sub.getId(), "Activated", "WEBHOOK", "payment.charge.completed",
                "pending_charge_confirmed", sub);
        outboxWriter.write("BundleSubscriptionActivated", "subscription.bundle.activated",
                BundleSubscriptionResponse.from(sub));
    }

    /**
     * bug_029 — terminates a sub that was left PENDING when the deferred
     * Paymob confirmation ultimately failed (OTP wrong, card rejected, etc.).
     */
    @Transactional
    public void failPendingCharge(UUID subscriptionId, String reason) {
        BundleSubscription sub = bundleSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.PENDING) {
            return;
        }
        Instant now = Instant.now();
        sub.setStatus(SubscriptionStatus.EXPIRED);
        sub.setUpdatedAt(now);
        bundleSubscriptionRepository.save(sub);
        auditLogger.log(sub.getId(), "Expired", "WEBHOOK", "payment.charge.failed",
                reason == null ? "pending_charge_failed" : reason, null);
        outboxWriter.write("BundleSubscriptionExpired", "subscription.bundle.expired",
                Map.of("subscriptionId", sub.getId(), "reason", "pending_charge_failed"));
    }

    /**
     * bug_003 / bug_003-run2 — derive a stable Idempotency-Key for the
     * downstream Wallet/Payment calls. Salting with sub.id (allocated up-front
     * by claimPending) ensures legitimate fresh purchase intents (re-buy after
     * refund/chargeback/expiry) get distinct keys, while same-attempt retries
     * still collapse at Payment.
     */
    static String stableCheckoutKey(UUID participantId, UUID bundleId, long amountCents, UUID subId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = participantId + "|" + bundleId + "|" + amountCents + "|" + subId;
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return "co-" + HexFormat.of().formatHex(hash, 0, 16);   // 32 hex chars + prefix
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private PaymentMethodType resolvePaymentMethodType(BundleCheckoutRequest req, boolean useWallet) {
        if (useWallet) {
            return PaymentMethodType.OTHER;     // funded internally; not eligible for auto-renew
        }
        if (req.getPaymentMethodType() == null || req.getPaymentMethodType().isBlank()) {
            // Default to OTHER so renewal-prompt is used (safer than silently auto-charging).
            return PaymentMethodType.OTHER;
        }
        try {
            return PaymentMethodType.valueOf(req.getPaymentMethodType().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PaymentMethodType.OTHER;
        }
    }

    private String serializeAmenities(List<String> amenities) {
        if (amenities == null || amenities.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(amenities);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void createEscrowEntries(BundleSubscription sub, long totalCents) {
        // Decision 4.2 — split duration into approximately monthly tranches,
        // capped at 12 (annual). Sub-monthly durations release as a single
        // tranche at the end of the cycle.
        int tranches = Math.max(1, Math.min(12, sub.getDurationDays() / 30));
        long perTranche = totalCents / tranches;
        long remainder  = totalCents - (perTranche * tranches);
        long perTrancheDays = sub.getDurationDays() / tranches;
        Instant cursor = sub.getStartedAt();
        for (int i = 1; i <= tranches; i++) {
            EscrowEntry entry = new EscrowEntry();
            entry.setBundleSubscriptionId(sub.getId());
            entry.setSellerId(sub.getSellerId());
            entry.setAmountCents(perTranche + (i == tranches ? remainder : 0));
            entry.setCurrency(sub.getCurrency());
            entry.setStatus(EscrowStatus.HELD);
            // Cycle ends when we cross from tranche i to i+1; payout fires
            // 1 calendar week after cycle end (PDF Decision 5.1 buffer).
            Instant cycleEnd = cursor.plus(perTrancheDays, ChronoUnit.DAYS);
            entry.setOriginalScheduledAt(cycleEnd.plus(7, ChronoUnit.DAYS));
            entry.setCohortKey(CohortKey.fromInstant(sub.getStartedAt()));
            entry.setDeferredCount(0);
            entry.setCreatedAt(sub.getStartedAt());
            entry.setUpdatedAt(sub.getStartedAt());
            escrowEntryRepository.save(entry);
            cursor = cycleEnd;
        }
    }
}
