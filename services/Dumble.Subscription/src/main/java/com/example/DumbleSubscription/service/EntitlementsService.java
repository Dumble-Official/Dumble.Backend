package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.Plan;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.EntitlementsResponse;
import com.example.DumbleSubscription.repository.PlanRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The single source of truth for "what is this user allowed to do." Consumed
 * by ChatService and any other service gating features on tier.
 *
 * Logic per Subscription PDF Decision 3.1 (uniform FREE/PRO across audiences)
 * + Decision 7.3 / runtime-trust-of-expiresAt: a subscription past its
 * currentPeriodEnd is treated as FREE regardless of the cached plan code, so
 * stale data can never silently grant entitlements after expiry.
 */
@Service
public class EntitlementsService {

    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final PlanRepository planRepository;

    public EntitlementsService(PlatformSubscriptionRepository platformSubscriptionRepository,
                               PlanRepository planRepository) {
        this.platformSubscriptionRepository = platformSubscriptionRepository;
        this.planRepository = planRepository;
    }

    public EntitlementsResponse forUser(UUID userId) {
        PlatformPlanCode code = resolveActivePlanCode(userId);
        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Plan " + code + " not found in catalog — seeding broken?"));

        Instant expiresAt = platformSubscriptionRepository.findByUserId(userId)
                .filter(this::isActive)
                .map(PlatformSubscription::getCurrentPeriodEnd)
                .orElse(null);

        return EntitlementsResponse.builder()
                .planCode(plan.getCode().name())
                .expiresAt(expiresAt)
                .canUseChatbot(plan.isCanUseChatbot())
                .chatbotMessagesPerDay(plan.getChatbotMessagesPerDay())
                .canDmAnyone(plan.isCanDmAnyone())
                .build();
    }

    private PlatformPlanCode resolveActivePlanCode(UUID userId) {
        return platformSubscriptionRepository.findByUserId(userId)
                .filter(this::isActive)
                .map(PlatformSubscription::getPlanCode)
                .orElse(PlatformPlanCode.FREE);
    }

    private boolean isActive(PlatformSubscription sub) {
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            return false;
        }
        // Trust expiresAt over the cached plan code — stale ACTIVE rows past
        // their period end are NOT entitled.
        return sub.getCurrentPeriodEnd() == null
                || sub.getCurrentPeriodEnd().isAfter(Instant.now());
    }
}
