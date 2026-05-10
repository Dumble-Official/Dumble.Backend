package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.Plan;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.enums.PaymentMethodType;
import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.MyPlanResponse;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.exception.BusinessRuleViolationException;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * DB-only transactions for the PRO upgrade flow. Lets PlatformPlanService
 * keep its blocking Payment.charge HTTP call outside of any JPA tx
 * (mirrors BundleCheckoutPersister).
 */
@Component
public class PlatformUpgradePersister {

    private final PlatformSubscriptionRepository repository;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final ReceiptService receiptService;

    public PlatformUpgradePersister(PlatformSubscriptionRepository repository,
                                    OutboxWriter outboxWriter,
                                    AuditLogger auditLogger,
                                    ReceiptService receiptService) {
        this.repository = repository;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.receiptService = receiptService;
    }

    /**
     * Claim a PENDING PlatformSubscription row up-front so the Payment.charge
     * Idempotency-Key can be salted with the row's id (bug_003-run2).
     * Re-purposes any existing FREE/EXPIRED row; rejects if already on PRO.
     */
    @Transactional
    public PlatformSubscription claimPending(UUID userId,
                                             String paymentMethodToken,
                                             PaymentMethodType paymentMethodType) {
        Instant now = Instant.now();
        PlatformSubscription sub = repository.findByUserId(userId).orElseGet(() -> {
            PlatformSubscription fresh = new PlatformSubscription();
            fresh.setUserId(userId);
            fresh.setCreatedAt(now);
            return fresh;
        });
        if (sub.getPlanCode() == PlatformPlanCode.PRO && sub.getStatus() == SubscriptionStatus.ACTIVE
                && (sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isAfter(now))) {
            throw new BusinessRuleViolationException("Already on PRO");
        }
        // Surface in-flight upgrade — same-user retry returns the existing
        // PENDING state instead of double-charging.
        if (sub.getStatus() == SubscriptionStatus.PENDING && sub.getPlanCode() == PlatformPlanCode.PRO) {
            return sub;
        }
        sub.setPlanCode(PlatformPlanCode.PRO);
        sub.setStatus(SubscriptionStatus.PENDING);
        sub.setStartedAt(now);
        sub.setCurrentPeriodEnd(null);
        sub.setCancelScheduledAt(null);
        sub.setPaymentMethodToken(paymentMethodToken);
        sub.setPaymentMethodType(paymentMethodType);
        sub.setRetryAttempts(0);
        sub.setNextRetryAt(null);
        sub.setUpdatedAt(now);
        if (sub.getCreatedAt() == null) sub.setCreatedAt(now);
        return repository.saveAndFlush(sub);
    }

    @Transactional
    public MyPlanResponse activate(UUID subscriptionId, String providerRef, Plan pro) {
        PlatformSubscription sub = repository.findById(subscriptionId).orElseThrow();
        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            return toResponse(sub);
        }
        Instant now = Instant.now();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodEnd(now.plus(30, ChronoUnit.DAYS));
        sub.setProviderRef(providerRef);
        sub.setUpdatedAt(now);
        repository.save(sub);

        receiptService.issueForPlatformSubscription(sub.getUserId(), sub.getId(),
                providerRef == null ? sub.getId().toString() : providerRef,
                pro.getPriceCents(), pro.getCurrency());

        auditLogger.log(sub.getId(), "PlanChanged", "USER", sub.getUserId().toString(), "upgrade FREE→PRO", sub);
        outboxWriter.write("PlatformSubscriptionActivated", "subscription.platform.activated", toResponse(sub));
        outboxWriter.write("PlanChanged", "subscription.plan.changed", toResponse(sub));
        return toResponse(sub);
    }

    @Transactional
    public MyPlanResponse markPending(UUID subscriptionId, String providerRef) {
        PlatformSubscription sub = repository.findById(subscriptionId).orElseThrow();
        Instant now = Instant.now();
        sub.setProviderRef(providerRef);
        sub.setUpdatedAt(now);
        repository.save(sub);
        auditLogger.log(sub.getId(), "UpgradePending", "USER", sub.getUserId().toString(),
                "awaiting_payment_confirmation",
                Map.of("providerRef", providerRef == null ? "" : providerRef));
        outboxWriter.write("PlatformSubscriptionPending", "subscription.platform.pending",
                Map.of("userId", sub.getUserId(),
                       "providerRef", providerRef == null ? "" : providerRef));
        return toResponse(sub);
    }

    /**
     * Roll the row back to FREE when the HTTP charge ultimately failed and
     * we want to free the slot for an immediate retry. Idempotent.
     */
    @Transactional
    public void releasePending(UUID subscriptionId) {
        repository.findById(subscriptionId).ifPresent(sub -> {
            if (sub.getStatus() == SubscriptionStatus.PENDING) {
                Instant now = Instant.now();
                sub.setPlanCode(PlatformPlanCode.FREE);
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setCurrentPeriodEnd(null);
                sub.setProviderRef(null);
                sub.setPaymentMethodToken(null);
                sub.setPaymentMethodType(null);
                sub.setUpdatedAt(now);
                repository.save(sub);
            }
        });
    }

    private MyPlanResponse toResponse(PlatformSubscription sub) {
        return MyPlanResponse.builder()
                .planCode(sub.getPlanCode().name())
                .status(sub.getStatus().name())
                .startedAt(sub.getStartedAt())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .cancelScheduledAt(sub.getCancelScheduledAt())
                .build();
    }
}
