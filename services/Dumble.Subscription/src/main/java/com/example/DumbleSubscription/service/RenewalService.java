package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.PaymentServiceClient;
import com.example.DumbleSubscription.client.dto.ChargeRequest;
import com.example.DumbleSubscription.client.dto.ChargeResponse;
import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.Plan;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.repository.PlanRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import com.example.DumbleSubscription.util.CohortKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Handles renewal charges for both BundleSubscription (evergreen, Decision 7.1)
 * and PlatformSubscription (PRO every 30 days). Wired by RenewalJob which
 * loops over due subs and calls the appropriate method here.
 *
 * Failed charges transition the sub to PAST_DUE and schedule the first dunning
 * retry in 3 days (Decision 7.3). DunningRetryJob picks up from there.
 */
@Service
public class RenewalService {

    private static final Logger log = LoggerFactory.getLogger(RenewalService.class);

    private static final int FIRST_RETRY_DELAY_DAYS = 3;
    private static final int SECOND_RETRY_DELAY_DAYS = 4;       // day 7 total
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final PlanRepository planRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final SellerLifecycleService sellerLifecycleService;

    public RenewalService(BundleSubscriptionRepository bundleSubscriptionRepository,
                          PlatformSubscriptionRepository platformSubscriptionRepository,
                          PlanRepository planRepository,
                          EscrowEntryRepository escrowEntryRepository,
                          PaymentServiceClient paymentServiceClient,
                          OutboxWriter outboxWriter,
                          AuditLogger auditLogger,
                          SellerLifecycleService sellerLifecycleService) {
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.platformSubscriptionRepository = platformSubscriptionRepository;
        this.planRepository = planRepository;
        this.escrowEntryRepository = escrowEntryRepository;
        this.paymentServiceClient = paymentServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.sellerLifecycleService = sellerLifecycleService;
    }

    @Transactional
    public void renewBundle(BundleSubscription sub) {
        // Block renewals for subs whose seller has gone WindingDown / Frozen / Banned (Section 17).
        if (!sellerLifecycleService.canAcceptNewSubscriptions(sub.getSellerId())) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setUpdatedAt(Instant.now());
            outboxWriter.write("BundleSubscriptionExpired", "subscription.bundle.expired",
                    Map.of("subscriptionId", sub.getId(), "reason", "seller_unavailable"));
            return;
        }
        // Decision 7.2 — only cards auto-renew silently. Wallets get a prompt.
        // bug_019 — after emitting the prompt, drop the row out of the renewal
        // pool by clearing autoRenew. ExpirationJob will reap it cleanly; the
        // user re-subscribes via the normal checkout flow if they want to.
        if (sub.getPaymentMethodType() == com.example.DumbleSubscription.domain.enums.PaymentMethodType.WALLET) {
            emitRenewalPromptAndExit(sub, "wallet_requires_authorization");
            return;
        }
        if (sub.getPaymentMethodToken() == null || sub.getPaymentMethodToken().isBlank()) {
            emitRenewalPromptAndExit(sub, "no_token");
            return;
        }
        attemptCharge(sub, sub.getPaymentMethodToken(), false);
    }

    private void emitRenewalPromptAndExit(BundleSubscription sub, String reason) {
        Instant now = Instant.now();
        outboxWriter.write("RenewalPromptNeeded", "subscription.bundle.renewal-prompt",
                Map.of("subscriptionId", sub.getId(),
                        "participantId", sub.getParticipantId(),
                        "amountCents", sub.getPricePaidCents(),
                        "currency", sub.getCurrency(),
                        "reason", reason));
        sub.setAutoRenew(false);
        sub.setRenewalPromptedAt(now);
        sub.setUpdatedAt(now);
        auditLogger.log(sub.getId(), "RenewalPromptEmitted", "SYSTEM", "renewal-job",
                reason, null);
    }

    @Transactional
    public void retryBundleDunning(BundleSubscription sub) {
        if (sub.getPaymentMethodToken() == null || sub.getPaymentMethodToken().isBlank()) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setUpdatedAt(Instant.now());
            sub.setNextRetryAt(null);
            outboxWriter.write("BundleSubscriptionExpired", "subscription.bundle.expired",
                    Map.of("subscriptionId", sub.getId(), "reason", "no_payment_method"));
            return;
        }
        attemptCharge(sub, sub.getPaymentMethodToken(), true);
    }

    private void attemptCharge(BundleSubscription sub, String paymentMethodToken, boolean isRetry) {
        Instant now = Instant.now();
        // bug_003 — deterministic idempotency key per (sub, attempt) so the
        // *same* attempt re-issued (e.g. job restart) collapses at Payment.
        // We deliberately keep retryAttempts in the key so each *new* dunning
        // attempt is a fresh charge.
        String idempotencyKey = "renewal-" + sub.getId() + "-" + sub.getRetryAttempts();

        try {
            ChargeResponse response = paymentServiceClient.charge(idempotencyKey,
                    ChargeRequest.builder()
                            .userId(sub.getParticipantId())
                            .amountCents(sub.getPricePaidCents())
                            .currency(sub.getCurrency())
                            .paymentMethodToken(paymentMethodToken)
                            .description("Renewal: " + sub.getBundleName())
                            .callerReference(sub.getId().toString())
                            .build());

            if (response != null) {
                if ("Succeeded".equalsIgnoreCase(response.getStatus())) {
                    onChargeSuccess(sub, now);
                    return;
                }
                if ("Pending".equalsIgnoreCase(response.getStatus())) {
                    // bug_029 — Paymob returned Pending (OTP/3DS in flight).
                    // DO NOT bump retryAttempts or move to PAST_DUE — leave
                    // the sub as-is and trust the payment.charge.completed
                    // webhook to either flip it to ACTIVE or call
                    // onChargeFailure-equivalent on a Failed completion.
                    log.info("Renewal charge for sub {} returned Pending — awaiting webhook confirmation",
                            sub.getId());
                    sub.setUpdatedAt(now);
                    return;
                }
            }
        } catch (Exception ex) {
            log.warn("Renewal charge raised exception for sub {}: {}", sub.getId(), ex.getMessage());
        }
        onChargeFailure(sub, now, isRetry);
    }

    private void onChargeSuccess(BundleSubscription sub, Instant now) {
        // Extend the period and create the next round of escrow tranches.
        Instant newEndsAt = sub.getEndsAt().plus(sub.getDurationDays(), ChronoUnit.DAYS);
        sub.setStartedAt(sub.getEndsAt());
        sub.setEndsAt(newEndsAt);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setRetryAttempts(0);
        sub.setNextRetryAt(null);
        sub.setPastDueAt(null);
        sub.setUpdatedAt(now);
        // Renewals always charge full price — no promo on renewal (Decision 9.2).
        sub.setPromoCode(null);
        sub.setPromoDiscountCents(null);

        createNextEscrowTranches(sub);

        auditLogger.log(sub.getId(), "Renewed", "SYSTEM", "renewal-job",
                "auto-renewal succeeded", Map.of("amount", sub.getPricePaidCents()));
        outboxWriter.write("BundleSubscriptionRenewed", "subscription.bundle.renewed",
                Map.of("subscriptionId", sub.getId(), "newEndsAt", newEndsAt));
    }

    private void onChargeFailure(BundleSubscription sub, Instant now, boolean isRetry) {
        sub.setRetryAttempts(sub.getRetryAttempts() + 1);
        sub.setUpdatedAt(now);

        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            sub.setPastDueAt(now);
        }

        if (sub.getRetryAttempts() > MAX_RETRY_ATTEMPTS) {
            // Decision 7.3 — exhausted retries, expire the sub.
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setNextRetryAt(null);
            outboxWriter.write("PaymentFailedFinal", "subscription.payment.failed-final",
                    Map.of("subscriptionId", sub.getId(), "attempts", sub.getRetryAttempts() - 1));
            outboxWriter.write("BundleSubscriptionExpired", "subscription.bundle.expired",
                    Map.of("subscriptionId", sub.getId(), "reason", "dunning_exhausted"));
            auditLogger.log(sub.getId(), "Expired", "SYSTEM", "dunning-job",
                    "dunning exhausted", null);
            return;
        }

        // Schedule next retry per Decision 7.3 (3 retries over 7 days).
        Instant nextRetry = switch (sub.getRetryAttempts()) {
            case 1 -> now.plus(FIRST_RETRY_DELAY_DAYS, ChronoUnit.DAYS);
            case 2 -> now.plus(SECOND_RETRY_DELAY_DAYS, ChronoUnit.DAYS);
            default -> now.plus(1, ChronoUnit.DAYS);
        };
        sub.setNextRetryAt(nextRetry);

        outboxWriter.write("PaymentFailed", "subscription.payment.failed",
                Map.of("subscriptionId", sub.getId(),
                        "attempt", sub.getRetryAttempts(),
                        "nextRetryAt", nextRetry));
        auditLogger.log(sub.getId(), "PaymentFailed", "SYSTEM",
                isRetry ? "dunning-job" : "renewal-job",
                "attempt " + sub.getRetryAttempts(), null);
    }

    private void createNextEscrowTranches(BundleSubscription sub) {
        // Reuse same logic as initial checkout — create monthly-ish tranches
        // for the new period. This mirrors BundleSubscriptionService.createEscrowEntries.
        long total = sub.getPricePaidCents();
        int tranches = Math.max(1, Math.min(12, sub.getDurationDays() / 30));
        long perTranche = total / tranches;
        long remainder = total - (perTranche * tranches);
        long perTrancheDays = sub.getDurationDays() / tranches;
        Instant cursor = sub.getStartedAt();
        for (int i = 1; i <= tranches; i++) {
            EscrowEntry entry = new EscrowEntry();
            entry.setBundleSubscriptionId(sub.getId());
            entry.setSellerId(sub.getSellerId());
            entry.setAmountCents(perTranche + (i == tranches ? remainder : 0));
            entry.setCurrency(sub.getCurrency());
            entry.setStatus(EscrowStatus.HELD);
            Instant cycleEnd = cursor.plus(perTrancheDays, ChronoUnit.DAYS);
            entry.setOriginalScheduledAt(cycleEnd.plus(7, ChronoUnit.DAYS));
            entry.setCohortKey(CohortKey.fromInstant(sub.getStartedAt()));
            entry.setCreatedAt(sub.getStartedAt());
            entry.setUpdatedAt(sub.getStartedAt());
            escrowEntryRepository.save(entry);
            cursor = cycleEnd;
        }
    }

    @Transactional
    public void renewPlatformPro(PlatformSubscription sub) {
        Plan pro = planRepository.findByCode(PlatformPlanCode.PRO).orElseThrow();
        Instant now = Instant.now();

        // bug_011 — Decision 7.2: wallet-funded PRO upgrades cannot be silently
        // re-charged. Mirror the bundle-renewal guard here.
        if (sub.getPaymentMethodType() == com.example.DumbleSubscription.domain.enums.PaymentMethodType.WALLET) {
            outboxWriter.write("RenewalPromptNeeded", "subscription.platform.renewal-prompt",
                    Map.of("userId", sub.getUserId(),
                            "subscriptionId", sub.getId(),
                            "amountCents", pro.getPriceCents(),
                            "currency", pro.getCurrency()));
            // Drop out of the renewal pool — Decision 7.2.
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setPlanCode(PlatformPlanCode.FREE);
            sub.setUpdatedAt(now);
            outboxWriter.write("PlatformSubscriptionExpired", "subscription.platform.expired",
                    Map.of("userId", sub.getUserId(), "reason", "wallet_renewal_prompt"));
            outboxWriter.write("PlanChanged", "subscription.plan.changed",
                    Map.of("userId", sub.getUserId(), "newPlan", "FREE"));
            return;
        }
        if (sub.getPaymentMethodToken() == null || sub.getPaymentMethodToken().isBlank()) {
            outboxWriter.write("RenewalPromptNeeded", "subscription.platform.renewal-prompt",
                    Map.of("userId", sub.getUserId(),
                            "subscriptionId", sub.getId(),
                            "reason", "no_token"));
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setPlanCode(PlatformPlanCode.FREE);
            sub.setUpdatedAt(now);
            outboxWriter.write("PlatformSubscriptionExpired", "subscription.platform.expired",
                    Map.of("userId", sub.getUserId(), "reason", "no_payment_method"));
            outboxWriter.write("PlanChanged", "subscription.plan.changed",
                    Map.of("userId", sub.getUserId(), "newPlan", "FREE"));
            return;
        }

        String idempotencyKey = "platform-renewal-" + sub.getId() + "-" + sub.getRetryAttempts() + "-" + now.toEpochMilli();

        try {
            ChargeResponse response = paymentServiceClient.charge(idempotencyKey,
                    ChargeRequest.builder()
                            .userId(sub.getUserId())
                            .amountCents(pro.getPriceCents())
                            .currency(pro.getCurrency())
                            .paymentMethodToken(sub.getPaymentMethodToken())
                            .description("Pro renewal")
                            .callerReference(sub.getId().toString())
                            .build());

            if (response != null) {
                if ("Succeeded".equalsIgnoreCase(response.getStatus())) {
                    sub.setCurrentPeriodEnd(sub.getCurrentPeriodEnd().plus(30, ChronoUnit.DAYS));
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    sub.setRetryAttempts(0);
                    sub.setNextRetryAt(null);
                    sub.setPastDueAt(null);
                    sub.setUpdatedAt(now);
                    outboxWriter.write("PlatformSubscriptionRenewed", "subscription.platform.renewed",
                            Map.of("userId", sub.getUserId()));
                    auditLogger.log(sub.getId(), "Renewed", "SYSTEM", "renewal-job",
                            "PRO auto-renewal succeeded", null);
                    return;
                }
                if ("Pending".equalsIgnoreCase(response.getStatus())) {
                    // bug_029 — Paymob OTP/3DS deferred. Wait for the webhook
                    // to either succeed or fail; don't bump retryAttempts.
                    log.info("PRO renewal for user {} returned Pending — awaiting webhook confirmation",
                            sub.getUserId());
                    sub.setUpdatedAt(now);
                    return;
                }
            }
        } catch (Exception ex) {
            log.warn("PRO renewal failed for user {}: {}", sub.getUserId(), ex.getMessage());
        }

        // Failure path
        sub.setRetryAttempts(sub.getRetryAttempts() + 1);
        sub.setUpdatedAt(now);
        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            sub.setPastDueAt(now);
        }
        if (sub.getRetryAttempts() > MAX_RETRY_ATTEMPTS) {
            // Decision 7.3 — exhausted, drop to FREE.
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setPlanCode(PlatformPlanCode.FREE);
            sub.setNextRetryAt(null);
            outboxWriter.write("PlatformSubscriptionExpired", "subscription.platform.expired",
                    Map.of("userId", sub.getUserId(), "reason", "dunning_exhausted"));
            outboxWriter.write("PlanChanged", "subscription.plan.changed",
                    Map.of("userId", sub.getUserId(), "newPlan", "FREE"));
            return;
        }
        Instant nextRetry = switch (sub.getRetryAttempts()) {
            case 1 -> now.plus(FIRST_RETRY_DELAY_DAYS, ChronoUnit.DAYS);
            case 2 -> now.plus(SECOND_RETRY_DELAY_DAYS, ChronoUnit.DAYS);
            default -> now.plus(1, ChronoUnit.DAYS);
        };
        sub.setNextRetryAt(nextRetry);
        outboxWriter.write("PaymentFailed", "subscription.payment.failed",
                Map.of("userId", sub.getUserId(), "attempt", sub.getRetryAttempts()));
    }
}
