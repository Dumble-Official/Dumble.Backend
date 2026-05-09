package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.BundleManagementClient;
import com.example.DumbleSubscription.client.GymServiceClient;
import com.example.DumbleSubscription.client.PaymentServiceClient;
import com.example.DumbleSubscription.client.WalletServiceClient;
import com.example.DumbleSubscription.client.dto.BundleSnapshot;
import com.example.DumbleSubscription.client.dto.ChargeRequest;
import com.example.DumbleSubscription.client.dto.ChargeResponse;
import com.example.DumbleSubscription.client.dto.PromoValidationResponse;
import com.example.DumbleSubscription.client.dto.WalletDebitRequest;
import com.example.DumbleSubscription.client.dto.WalletSummaryResponse;
import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.PromoCodeRedemption;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.PaymentMethodType;
import com.example.DumbleSubscription.domain.enums.SellerType;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.BundleCheckoutRequest;
import com.example.DumbleSubscription.dto.BundleSubscriptionResponse;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.exception.BusinessRuleViolationException;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.repository.PromoCodeRedemptionRepository;
import com.example.DumbleSubscription.util.CohortKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

/**
 * Bundle subscription checkout — the flagship participant flow.
 *
 * Orchestrates: bundle snapshot from BundleManagement → promo validation
 * via Gym → payment via Wallet OR Payment → BundleSubscription row +
 * escrow entries + audit + outbox. All in one transaction (so Payment-side
 * effects must run BEFORE the @Transactional method or be deferred until
 * webhook ack — see comment below).
 */
@Service
public class BundleSubscriptionService {

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    private final BundleManagementClient bundleManagementClient;
    private final GymServiceClient gymServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final ReceiptService receiptService;
    private final SellerLifecycleService sellerLifecycleService;
    private final ObjectMapper objectMapper;

    public BundleSubscriptionService(BundleSubscriptionRepository bundleSubscriptionRepository,
                                     EscrowEntryRepository escrowEntryRepository,
                                     PromoCodeRedemptionRepository promoCodeRedemptionRepository,
                                     BundleManagementClient bundleManagementClient,
                                     GymServiceClient gymServiceClient,
                                     WalletServiceClient walletServiceClient,
                                     PaymentServiceClient paymentServiceClient,
                                     OutboxWriter outboxWriter,
                                     AuditLogger auditLogger,
                                     ReceiptService receiptService,
                                     SellerLifecycleService sellerLifecycleService,
                                     ObjectMapper objectMapper) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.escrowEntryRepository = escrowEntryRepository;
        this.promoCodeRedemptionRepository = promoCodeRedemptionRepository;
        this.bundleManagementClient = bundleManagementClient;
        this.gymServiceClient = gymServiceClient;
        this.walletServiceClient = walletServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.receiptService = receiptService;
        this.sellerLifecycleService = sellerLifecycleService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BundleSubscriptionResponse checkout(UUID participantId, BundleCheckoutRequest req) {
        BundleSnapshot bundle = bundleManagementClient.getBundle(req.getBundleId());
        if (bundle == null || !bundle.isActive()) {
            throw new ResourceNotFoundException("Bundle not available");
        }

        // Sections 16, 17 — block new subs when seller is Frozen / WindingDown / Banned / Closed.
        if (!sellerLifecycleService.canAcceptNewSubscriptions(bundle.getSellerId())) {
            throw new BusinessRuleViolationException("This seller is not currently accepting new subscriptions");
        }

        // Decision 12.2 — single ACTIVE sub per (participant, bundle)
        bundleSubscriptionRepository
                .findByParticipantIdAndBundleIdAndStatus(participantId, bundle.getId(), SubscriptionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new BusinessRuleViolationException("You already have an active subscription to this bundle");
                });

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

        // Pay either entirely from wallet (if balance covers) or entirely via Payment.
        // No partial wallet+card splits in v1 — see Wallet PDF Decision 4.1.
        //
        // Idempotency key derivation (Decision 12.3 + bug_003): use a hash of
        // the stable purchase tuple — (participant, bundle, amount) — instead
        // of UUID.randomUUID(). Two retries of the same purchase produce the
        // same downstream key, so Payment / Wallet collapse them into a single
        // charge even if the controller-level Idempotency-Key headers differ
        // (e.g. mobile retry without client-side dedup).
        boolean paidFromWallet = false;
        String providerRef = null;
        boolean paymentPending = false;
        String pendingProviderRef = null;
        String checkoutIntentId = stableCheckoutKey(participantId, bundle.getId(), amountToCharge);

        if (req.isUseWalletBalance()) {
            WalletSummaryResponse summary = walletServiceClient.summary(participantId);
            if (summary != null && summary.getAvailableCents() >= amountToCharge) {
                walletServiceClient.debit(checkoutIntentId,
                        new WalletDebitRequest(participantId, amountToCharge, "InAppSpend", checkoutIntentId));
                paidFromWallet = true;
            }
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
                throw new BusinessRuleViolationException("Payment failed");
            }
            String chargeStatus = charge.getStatus();
            if ("Succeeded".equalsIgnoreCase(chargeStatus)) {
                providerRef = charge.getProviderRef();
            } else if ("Pending".equalsIgnoreCase(chargeStatus)) {
                // Decision: Paymob's Egyptian card flow returns Pending when the
                // bank requires async OTP / 3DS confirmation. Don't fail the
                // sub — leave it PENDING, persist the providerRef, and let the
                // payment.charge.completed webhook flip it to ACTIVE.
                paymentPending = true;
                pendingProviderRef = charge.getProviderRef();
            } else {
                throw new BusinessRuleViolationException("Payment failed");
            }
        }

        // Create the subscription with bundle data SNAPSHOTTED (Decision 12.1)
        Instant now = Instant.now();
        BundleSubscription sub = new BundleSubscription();
        sub.setParticipantId(participantId);
        sub.setSellerId(bundle.getSellerId());
        sub.setSellerType(SellerType.valueOf(bundle.getSellerType()));
        sub.setBundleId(bundle.getId());
        sub.setBundleName(bundle.getName());
        sub.setPricePaidCents(amountToCharge);
        sub.setCurrency(bundle.getCurrency());
        sub.setDurationDays(bundle.getDurationDays());
        sub.setBundleExpiresOnSnapshot(bundle.getExpiresOn());
        // bug_029 — Pending charges leave the sub PENDING until the
        // payment.charge.completed webhook arrives (handled by
        // PaymentEventListener). Wallet payments and Succeeded card charges
        // go straight to ACTIVE.
        sub.setStatus(paymentPending ? SubscriptionStatus.PENDING : SubscriptionStatus.ACTIVE);
        sub.setStartedAt(now);
        sub.setEndsAt(now.plus(bundle.getDurationDays(), ChronoUnit.DAYS));
        // Decision 2.3 — bundles with ExpiresOn never auto-renew
        sub.setAutoRenew(bundle.getExpiresOn() == null);
        sub.setProviderRef(paymentPending ? pendingProviderRef : providerRef);
        // Capture payment method token + type so RenewalService can re-charge correctly
        // (Decision 7.2 + fix for the prior providerRef-as-token bug).
        sub.setPaymentMethodToken(paidFromWallet ? null : req.getPaymentMethodToken());
        sub.setPaymentMethodType(resolvePaymentMethodType(req, paidFromWallet));
        // Snapshot amenities for the entry-scan response (Decision 21.4).
        sub.setAmenitiesJson(serializeAmenities(bundle.getAmenities()));
        sub.setPromoCode(req.getPromoCode());
        sub.setPromoDiscountCents(discountCents > 0 ? discountCents : null);
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);
        bundleSubscriptionRepository.save(sub);

        // Promo redemption is recorded regardless of pending — the discount
        // was applied in the charge amount, and the sub keeps the promo code
        // even while PENDING. If the charge ultimately fails, the redemption
        // row remains as an audit trail of what was attempted, which matches
        // the wider append-only audit policy (Section 14).
        if (promo != null && promo.isValid() && discountCents > 0) {
            PromoCodeRedemption redemption = new PromoCodeRedemption();
            redemption.setBundleSubscriptionId(sub.getId());
            redemption.setGymId(bundle.getSellerId());
            redemption.setCode(req.getPromoCode());
            redemption.setDiscountAppliedCents(discountCents);
            redemption.setDiscountType(promo.getDiscountType());
            redemption.setRedeemedAt(now);
            promoCodeRedemptionRepository.save(redemption);
        }

        // Defer escrow + receipt + activation outbox until the charge confirms
        // (bug_029). For PENDING subs, the payment.charge.completed handler
        // will create escrow tranches and emit the activation event.
        if (paymentPending) {
            auditLogger.log(sub.getId(), "CheckoutPending", "USER", participantId.toString(),
                    "awaiting_payment_confirmation",
                    Map.of("providerRef", pendingProviderRef == null ? "" : pendingProviderRef));
            outboxWriter.write("BundleSubscriptionPending", "subscription.bundle.pending",
                    Map.of("subscriptionId", sub.getId(),
                            "participantId", sub.getParticipantId(),
                            "providerRef", pendingProviderRef == null ? "" : pendingProviderRef));
            return BundleSubscriptionResponse.from(sub);
        }

        // Decision 4.2 — annual subs split into 12 monthly tranches; others use the duration directly.
        createEscrowEntries(sub, amountToCharge);

        // Receipt — Decision 11.5
        receiptService.issueForBundleSubscription(participantId, sub.getId(), checkoutIntentId,
                amountToCharge, sub.getCurrency(), sub.getBundleName(), sub.getDurationDays());

        auditLogger.log(sub.getId(), "Created", "USER", participantId.toString(),
                paidFromWallet ? "checkout_from_wallet" : "checkout_via_payment", sub);
        outboxWriter.write("BundleSubscriptionActivated", "subscription.bundle.activated",
                BundleSubscriptionResponse.from(sub));

        return BundleSubscriptionResponse.from(sub);
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

        String checkoutIntentId = stableCheckoutKey(sub.getParticipantId(), sub.getBundleId(), sub.getPricePaidCents());
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
     * bug_003 — derive a stable, deterministic Idempotency-Key for the
     * downstream Wallet/Payment calls. Two retries of the same purchase intent
     * (same participant, bundle, amount) collapse into the same downstream
     * key, so Payment dedupes them even if the controller-level
     * Idempotency-Key headers differ.
     */
    static String stableCheckoutKey(UUID participantId, UUID bundleId, long amountCents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = participantId + "|" + bundleId + "|" + amountCents;
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return "co-" + HexFormat.of().formatHex(hash, 0, 16);   // 32 hex chars + prefix
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private PaymentMethodType resolvePaymentMethodType(BundleCheckoutRequest req, boolean paidFromWallet) {
        if (paidFromWallet) {
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
