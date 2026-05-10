package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.PaymentServiceClient;
import com.example.DumbleSubscription.client.dto.ChargeRequest;
import com.example.DumbleSubscription.client.dto.ChargeResponse;
import com.example.DumbleSubscription.domain.Plan;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.enums.PaymentMethodType;
import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.MyPlanResponse;
import com.example.DumbleSubscription.dto.PlanUpgradeRequest;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.exception.BusinessRuleViolationException;
import com.example.DumbleSubscription.repository.PlanRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Platform-tier upgrade/downgrade per PDF Section 13.
 *
 * Decision 13.1 — upgrade FREE → PRO: immediate effect, full charge today.
 * Decision 13.2 — downgrade PRO → FREE: takes effect at period end.
 * Decision 13.3 — no proration anywhere.
 *
 * Orchestration shape (bug_014, bug_003-run2): claim PENDING in a short tx,
 * call Payment.charge with sub.id-salted Idempotency-Key outside any tx,
 * then finalize in a second short tx.
 */
@Service
public class PlatformPlanService {

    private final PlatformSubscriptionRepository repository;
    private final PlanRepository planRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final ReceiptService receiptService;
    private final PlatformUpgradePersister persister;

    public PlatformPlanService(PlatformSubscriptionRepository repository,
                               PlanRepository planRepository,
                               PaymentServiceClient paymentServiceClient,
                               OutboxWriter outboxWriter,
                               AuditLogger auditLogger,
                               ReceiptService receiptService,
                               PlatformUpgradePersister persister) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.paymentServiceClient = paymentServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.receiptService = receiptService;
        this.persister = persister;
    }

    public MyPlanResponse getMyPlan(UUID userId) {
        PlatformSubscription sub = repository.findByUserId(userId).orElse(null);
        if (sub == null) {
            return MyPlanResponse.builder()
                    .planCode("FREE")
                    .status("ACTIVE")
                    .build();
        }
        return MyPlanResponse.builder()
                .planCode(sub.getPlanCode().name())
                .status(sub.getStatus().name())
                .startedAt(sub.getStartedAt())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .cancelScheduledAt(sub.getCancelScheduledAt())
                .build();
    }

    public MyPlanResponse upgradeToPro(UUID userId, PlanUpgradeRequest req) {
        Plan pro = planRepository.findByCode(PlatformPlanCode.PRO)
                .orElseThrow(() -> new IllegalStateException("PRO plan not seeded"));

        // bug_011 — record the payment-method type alongside the token so the
        // renewal path can honour Decision 7.2 (no silent wallet auto-charge).
        PaymentMethodType resolvedType = resolvePaymentMethodType(req.getPaymentMethodType());

        // Phase 1 — claim PENDING in its own short tx; on existing PENDING,
        // surface the in-flight row instead of double-charging.
        PlatformSubscription claimed = persister.claimPending(userId, req.getPaymentMethodToken(), resolvedType);
        if (claimed.getStatus() == SubscriptionStatus.PENDING && claimed.getProviderRef() != null) {
            // Already in flight — return without re-charging.
            return getMyPlan(userId);
        }

        // Phase 2 — Payment.charge outside any tx, salted with claimed.getId()
        // so distinct purchase intents (re-buy after expiry/refund) get
        // distinct downstream keys.
        String upgradeIntentId = stableUpgradeKey(userId, pro.getPriceCents(), claimed.getId());

        ChargeResponse charge;
        try {
            charge = paymentServiceClient.charge(upgradeIntentId,
                    ChargeRequest.builder()
                            .userId(userId)
                            .amountCents(pro.getPriceCents())
                            .currency(pro.getCurrency())
                            .paymentMethodToken(req.getPaymentMethodToken())
                            .description("Upgrade to PRO")
                            .callerReference("platform-sub:" + userId)
                            .build());
        } catch (RuntimeException ex) {
            persister.releasePending(claimed.getId());
            throw ex;
        }
        if (charge == null) {
            persister.releasePending(claimed.getId());
            throw new BusinessRuleViolationException("Payment failed");
        }

        String chargeStatus = charge.getStatus();
        if ("Succeeded".equalsIgnoreCase(chargeStatus)) {
            return persister.activate(claimed.getId(), charge.getProviderRef(), pro);
        }
        if ("Pending".equalsIgnoreCase(chargeStatus)) {
            return persister.markPending(claimed.getId(), charge.getProviderRef());
        }
        persister.releasePending(claimed.getId());
        throw new BusinessRuleViolationException("Payment failed");
    }

    /**
     * bug_029 — flips a PENDING PRO upgrade to ACTIVE when the deferred
     * Paymob confirmation arrives via webhook. Idempotent on userId + status.
     */
    @Transactional
    public void confirmPendingUpgrade(UUID userId, String providerRef) {
        PlatformSubscription sub = repository.findByUserId(userId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.PENDING
                || sub.getPlanCode() != PlatformPlanCode.PRO) {
            return;
        }
        Plan pro = planRepository.findByCode(PlatformPlanCode.PRO).orElseThrow();
        Instant now = Instant.now();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodEnd(now.plus(30, ChronoUnit.DAYS));
        if (providerRef != null && !providerRef.isBlank()) {
            sub.setProviderRef(providerRef);
        }
        sub.setUpdatedAt(now);
        repository.save(sub);

        receiptService.issueForPlatformSubscription(userId, sub.getId(),
                sub.getProviderRef() == null ? sub.getId().toString() : sub.getProviderRef(),
                pro.getPriceCents(), pro.getCurrency());

        auditLogger.log(sub.getId(), "PlanChanged", "WEBHOOK", "payment.charge.completed",
                "pending_upgrade_confirmed", sub);
        outboxWriter.write("PlatformSubscriptionActivated", "subscription.platform.activated", getMyPlan(userId));
        outboxWriter.write("PlanChanged", "subscription.plan.changed", getMyPlan(userId));
    }

    /**
     * bug_029 — reverts a PENDING PRO upgrade to FREE when Paymob ultimately
     * declines the deferred confirmation.
     */
    @Transactional
    public void failPendingUpgrade(UUID userId, String reason) {
        PlatformSubscription sub = repository.findByUserId(userId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.PENDING) {
            return;
        }
        Instant now = Instant.now();
        sub.setStatus(SubscriptionStatus.EXPIRED);
        sub.setPlanCode(PlatformPlanCode.FREE);
        sub.setCurrentPeriodEnd(null);
        sub.setUpdatedAt(now);
        repository.save(sub);
        auditLogger.log(sub.getId(), "Expired", "WEBHOOK", "payment.charge.failed",
                reason == null ? "pending_upgrade_failed" : reason, null);
        outboxWriter.write("PlatformSubscriptionExpired", "subscription.platform.expired",
                Map.of("userId", userId, "reason", "pending_upgrade_failed"));
        outboxWriter.write("PlanChanged", "subscription.plan.changed", getMyPlan(userId));
    }

    @Transactional
    public MyPlanResponse cancelPro(UUID userId) {
        PlatformSubscription sub = repository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("No active PRO subscription"));
        if (sub.getPlanCode() != PlatformPlanCode.PRO) {
            throw new BusinessRuleViolationException("Already on FREE");
        }
        Instant now = Instant.now();
        sub.setCancelScheduledAt(now);
        sub.setUpdatedAt(now);
        repository.save(sub);
        auditLogger.log(sub.getId(), "Cancelled", "USER", userId.toString(),
                "downgrade scheduled at period end", null);
        return getMyPlan(userId);
    }

    private PaymentMethodType resolvePaymentMethodType(String raw) {
        if (raw == null || raw.isBlank()) {
            return PaymentMethodType.OTHER;
        }
        try {
            return PaymentMethodType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PaymentMethodType.OTHER;
        }
    }

    /**
     * bug_003 / bug_003-run2 — salt with subId so distinct intents (re-buy
     * after FREE→PRO→FREE→PRO again) get distinct keys.
     */
    static String stableUpgradeKey(UUID userId, long amountCents, UUID subId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = "platform-pro|" + userId + "|" + amountCents + "|" + subId;
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return "pro-" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
