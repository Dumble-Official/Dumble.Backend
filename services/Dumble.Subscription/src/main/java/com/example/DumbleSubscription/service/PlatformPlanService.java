package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.PaymentServiceClient;
import com.example.DumbleSubscription.client.dto.ChargeRequest;
import com.example.DumbleSubscription.client.dto.ChargeResponse;
import com.example.DumbleSubscription.domain.Plan;
import com.example.DumbleSubscription.domain.PlatformSubscription;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Platform-tier upgrade/downgrade per PDF Section 13.
 *
 * Decision 13.1 — upgrade FREE → PRO: immediate effect, full charge today.
 * Decision 13.2 — downgrade PRO → FREE: takes effect at period end.
 * Decision 13.3 — no proration anywhere.
 */
@Service
public class PlatformPlanService {

    private final PlatformSubscriptionRepository repository;
    private final PlanRepository planRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;
    private final ReceiptService receiptService;

    public PlatformPlanService(PlatformSubscriptionRepository repository,
                               PlanRepository planRepository,
                               PaymentServiceClient paymentServiceClient,
                               OutboxWriter outboxWriter,
                               AuditLogger auditLogger,
                               ReceiptService receiptService) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.paymentServiceClient = paymentServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
        this.receiptService = receiptService;
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

    @Transactional
    public MyPlanResponse upgradeToPro(UUID userId, PlanUpgradeRequest req) {
        Plan pro = planRepository.findByCode(PlatformPlanCode.PRO)
                .orElseThrow(() -> new IllegalStateException("PRO plan not seeded"));

        PlatformSubscription sub = repository.findByUserId(userId).orElseGet(() -> {
            PlatformSubscription fresh = new PlatformSubscription();
            fresh.setUserId(userId);
            fresh.setPlanCode(PlatformPlanCode.FREE);
            fresh.setStatus(SubscriptionStatus.ACTIVE);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });

        if (sub.getPlanCode() == PlatformPlanCode.PRO && sub.getStatus() == SubscriptionStatus.ACTIVE
                && (sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isAfter(Instant.now()))) {
            throw new BusinessRuleViolationException("Already on PRO");
        }

        ChargeResponse charge = paymentServiceClient.charge(UUID.randomUUID().toString(),
                ChargeRequest.builder()
                        .userId(userId)
                        .amountCents(pro.getPriceCents())
                        .currency(pro.getCurrency())
                        .paymentMethodToken(req.getPaymentMethodToken())
                        .description("Upgrade to PRO")
                        .callerReference("platform-sub:" + userId)
                        .build());
        if (charge == null || !"Succeeded".equalsIgnoreCase(charge.getStatus())) {
            throw new BusinessRuleViolationException("Payment failed");
        }

        Instant now = Instant.now();
        sub.setPlanCode(PlatformPlanCode.PRO);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartedAt(now);
        sub.setCurrentPeriodEnd(now.plus(30, ChronoUnit.DAYS));
        sub.setCancelScheduledAt(null);
        sub.setProviderRef(charge.getProviderRef());
        // Stash the token so RenewalJob can re-charge in 30 days.
        sub.setPaymentMethodToken(req.getPaymentMethodToken());
        sub.setRetryAttempts(0);
        sub.setNextRetryAt(null);
        sub.setUpdatedAt(now);
        if (sub.getCreatedAt() == null) sub.setCreatedAt(now);
        repository.save(sub);

        // Receipt — Decision 11.5
        receiptService.issueForPlatformSubscription(userId, sub.getId(),
                charge.getProviderRef() == null ? sub.getId().toString() : charge.getProviderRef(),
                pro.getPriceCents(), pro.getCurrency());

        auditLogger.log(sub.getId(), "PlanChanged", "USER", userId.toString(), "upgrade FREE→PRO", sub);
        outboxWriter.write("PlatformSubscriptionActivated", "subscription.platform.activated", getMyPlan(userId));
        outboxWriter.write("PlanChanged", "subscription.plan.changed", getMyPlan(userId));

        return getMyPlan(userId);
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
}
