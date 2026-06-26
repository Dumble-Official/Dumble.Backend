package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.dto.BundleSnapshot;
import com.example.DumbleSubscription.client.dto.PromoValidationResponse;
import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.PromoCodeRedemption;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.PaymentMethodType;
import com.example.DumbleSubscription.domain.enums.SellerType;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.BundleSubscriptionResponse;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.repository.PromoCodeRedemptionRepository;
import com.example.DumbleSubscription.util.CohortKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the small DB-only transactions for the bundle-checkout flow so the
 * service-level orchestration can keep its blocking HTTP calls *outside* a
 * @Transactional boundary (bug_014). Each method here is its own
 * REQUIRES_NEW transaction, keeping JPA sessions / Hikari connections held
 * for milliseconds rather than the full 10-30 s WebClient round-trip budget.
 */
@Component
public class BundleCheckoutPersister {

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final ReceiptService receiptService;

    public BundleCheckoutPersister(BundleSubscriptionRepository bundleSubscriptionRepository,
                                   EscrowEntryRepository escrowEntryRepository,
                                   PromoCodeRedemptionRepository promoCodeRedemptionRepository,
                                   OutboxWriter outboxWriter,
                                   AuditLogger auditLogger,
                                   ReceiptService receiptService) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.escrowEntryRepository = escrowEntryRepository;
        this.promoCodeRedemptionRepository = promoCodeRedemptionRepository;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.receiptService = receiptService;
    }

    /**
     * Claim a PENDING row up-front so the rest of the flow has a stable
     * sub.id for the Idempotency-Key salt (bug_003-run2). The unique partial
     * index over (participant, bundle) WHERE status IN ('ACTIVE','PENDING')
     * (bug_029-run2, V6) is the actual race gate — concurrent peers either
     * see this committed row or get a DataIntegrityViolationException at
     * commit.
     */
    @Transactional
    public BundleSubscription claimPending(UUID participantId,
                                           BundleSnapshot bundle,
                                           long amountToCharge,
                                           PaymentMethodType paymentMethodType,
                                           String paymentMethodToken,
                                           String amenitiesJson,
                                           String promoCode,
                                           Long promoDiscountCents) {
        Instant now = Instant.now();
        BundleSubscription sub = new BundleSubscription();
        sub.setParticipantId(participantId);
        // Key the subscription (and its escrow rows) on the seller's real auth
        // user id so the trainer's earnings/clients/insights and payouts find it.
        // Fall back to the legacy account-guid only for bundles minted before the
        // catalog carried sellerUserId.
        sub.setSellerId(bundle.getSellerUserId() != null
                ? bundle.getSellerUserId()
                : bundle.getSellerId());
        sub.setSellerType(SellerType.valueOf(bundle.getSellerType()));
        sub.setBundleId(bundle.getId());
        sub.setBundleName(bundle.getName());
        sub.setPricePaidCents(amountToCharge);
        sub.setCurrency(bundle.getCurrency());
        sub.setDurationDays(bundle.getDurationDays());
        sub.setBundleExpiresOnSnapshot(bundle.getExpiresOn());
        sub.setStatus(SubscriptionStatus.PENDING);
        sub.setStartedAt(now);
        sub.setEndsAt(now.plus(bundle.getDurationDays(), ChronoUnit.DAYS));
        // Decision 2.3 — bundles with ExpiresOn never auto-renew
        sub.setAutoRenew(bundle.getExpiresOn() == null);
        sub.setPaymentMethodType(paymentMethodType);
        sub.setPaymentMethodToken(paymentMethodToken);
        sub.setAmenitiesJson(amenitiesJson);
        sub.setPromoCode(promoCode);
        sub.setPromoDiscountCents(promoDiscountCents);
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);
        return bundleSubscriptionRepository.saveAndFlush(sub);
    }

    /**
     * Finalize a successful (or wallet-paid) checkout: flip PENDING → ACTIVE,
     * mint escrow tranches, record promo redemption + receipt, audit, outbox.
     */
    @Transactional
    public BundleSubscriptionResponse activate(UUID subscriptionId,
                                               String providerRef,
                                               boolean paidFromWallet,
                                               String checkoutIntentId,
                                               PromoValidationResponse promo,
                                               String promoCode,
                                               long discountCents) {
        BundleSubscription sub = bundleSubscriptionRepository.findById(subscriptionId).orElseThrow();
        if (sub.getStatus() != SubscriptionStatus.PENDING) {
            // Webhook beat us, or already activated. Idempotent return.
            return BundleSubscriptionResponse.from(sub);
        }
        Instant now = Instant.now();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setProviderRef(providerRef);
        sub.setUpdatedAt(now);
        bundleSubscriptionRepository.save(sub);

        createEscrowEntries(sub);

        if (promo != null && promo.isValid() && discountCents > 0) {
            PromoCodeRedemption redemption = new PromoCodeRedemption();
            redemption.setBundleSubscriptionId(sub.getId());
            redemption.setGymId(sub.getSellerId());
            redemption.setCode(promoCode);
            redemption.setDiscountAppliedCents(discountCents);
            redemption.setDiscountType(promo.getDiscountType());
            redemption.setRedeemedAt(now);
            promoCodeRedemptionRepository.save(redemption);
        }

        receiptService.issueForBundleSubscription(sub.getParticipantId(), sub.getId(), checkoutIntentId,
                sub.getPricePaidCents(), sub.getCurrency(), sub.getBundleName(), sub.getDurationDays());

        auditLogger.log(sub.getId(), "Created", "USER", sub.getParticipantId().toString(),
                paidFromWallet ? "checkout_from_wallet" : "checkout_via_payment", sub);
        outboxWriter.write("BundleSubscriptionActivated", "subscription.bundle.activated",
                BundleSubscriptionResponse.from(sub));

        return BundleSubscriptionResponse.from(sub);
    }

    /**
     * Leave the sub PENDING with the provider reference attached so the
     * payment.charge.completed webhook can resolve it. Used for Paymob
     * 3DS/OTP responses.
     */
    @Transactional
    public BundleSubscriptionResponse markPending(UUID subscriptionId, String providerRef) {
        BundleSubscription sub = bundleSubscriptionRepository.findById(subscriptionId).orElseThrow();
        if (sub.getStatus() != SubscriptionStatus.PENDING) {
            return BundleSubscriptionResponse.from(sub);
        }
        Instant now = Instant.now();
        sub.setProviderRef(providerRef);
        sub.setUpdatedAt(now);
        bundleSubscriptionRepository.save(sub);
        auditLogger.log(sub.getId(), "CheckoutPending", "USER", sub.getParticipantId().toString(),
                "awaiting_payment_confirmation",
                Map.of("providerRef", providerRef == null ? "" : providerRef));
        outboxWriter.write("BundleSubscriptionPending", "subscription.bundle.pending",
                Map.of("subscriptionId", sub.getId(),
                        "participantId", sub.getParticipantId(),
                        "providerRef", providerRef == null ? "" : providerRef));
        return BundleSubscriptionResponse.from(sub);
    }

    /**
     * Drop the PENDING claim row when the HTTP charge ultimately failed and
     * we want to free the (participant, bundle) slot for an immediate retry
     * with a different payment method. Idempotent.
     */
    @Transactional
    public void releasePending(UUID subscriptionId) {
        bundleSubscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
            if (sub.getStatus() == SubscriptionStatus.PENDING) {
                bundleSubscriptionRepository.delete(sub);
            }
        });
    }

    private void createEscrowEntries(BundleSubscription sub) {
        long totalCents = sub.getPricePaidCents();
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
