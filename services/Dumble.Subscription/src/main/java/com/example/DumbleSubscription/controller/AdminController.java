package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.PlatformSubscription;
import com.example.DumbleSubscription.domain.SellerLifecycle;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
// EscrowEntry already imported above
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.repository.PlatformSubscriptionRepository;
import com.example.DumbleSubscription.service.SellerLifecycleService;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin endpoints — Decision 15.2 + Sections 16, 17.
 * Restricted to ROLE_ADMIN via @PreAuthorize.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final SellerLifecycleService sellerLifecycleService;

    public AdminController(PlatformSubscriptionRepository platformSubscriptionRepository,
                           BundleSubscriptionRepository bundleSubscriptionRepository,
                           EscrowEntryRepository escrowEntryRepository,
                           SellerLifecycleService sellerLifecycleService) {
        this.platformSubscriptionRepository = platformSubscriptionRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.escrowEntryRepository = escrowEntryRepository;
        this.sellerLifecycleService = sellerLifecycleService;
    }

    /* ----- Platform stats ----- */

    @GetMapping("/platform/subscriptions")
    public Map<String, Object> subscriptionStats() {
        long activePro = platformSubscriptionRepository.countByPlanCodeAndStatus(
                PlatformPlanCode.PRO, SubscriptionStatus.ACTIVE);
        long pastDuePro = platformSubscriptionRepository.countByPlanCodeAndStatus(
                PlatformPlanCode.PRO, SubscriptionStatus.PAST_DUE);
        long activeBundle = bundleSubscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("activeProSubscriptions", activePro);
        result.put("pastDueProSubscriptions", pastDuePro);
        result.put("activeBundleSubscriptions", activeBundle);
        return result;
    }

    @GetMapping("/platform/escrow")
    public Map<String, Object> escrowStats() {
        Map<String, Long> totals = new HashMap<>();
        for (EscrowStatus status : EscrowStatus.values()) {
            totals.put(status.name().toLowerCase() + "Cents", 0L);
        }
        for (EscrowEntry e : escrowEntryRepository.findAll()) {
            String key = e.getStatus().name().toLowerCase() + "Cents";
            totals.merge(key, e.getAmountCents(), Long::sum);
        }
        return Map.of(
                "heldCents",      totals.getOrDefault("heldCents", 0L),
                "availableCents", totals.getOrDefault("availableCents", 0L),
                "paidOutCents",   totals.getOrDefault("paid_outCents", 0L),
                "refundedCents",  totals.getOrDefault("refundedCents", 0L)
        );
    }

    @GetMapping("/platform/refunds")
    public Map<String, Object> refundStats() {
        List<BundleSubscription> refunded = bundleSubscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.REFUNDED)
                .toList();
        long totalCents = refunded.stream().mapToLong(BundleSubscription::getPricePaidCents).sum();
        Map<String, Object> result = new HashMap<>();
        result.put("refundedSubscriptionCount", refunded.size());
        result.put("refundedTotalCents", totalCents);
        return result;
    }

    @GetMapping("/platform/dunning")
    public Map<String, Object> dunningStats() {
        long bundlePastDue = bundleSubscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.PAST_DUE)
                .count();
        long platformPastDue = platformSubscriptionRepository.countByPlanCodeAndStatus(
                PlatformPlanCode.PRO, SubscriptionStatus.PAST_DUE);
        Map<String, Object> result = new HashMap<>();
        result.put("bundleSubscriptionsPastDue", bundlePastDue);
        result.put("platformSubscriptionsPastDue", platformPastDue);
        return result;
    }

    @GetMapping("/platform/revenue")
    public Map<String, Object> revenue() {
        // Bundle revenue settled = sum of PAID_OUT escrow entries.
        // HELD / AVAILABLE entries are still the participants' money under the
        // escrow model and aren't recognised as platform-side revenue yet.
        long bundleSettledCents = 0;
        long bundleInEscrowCents = 0;
        for (EscrowEntry e : escrowEntryRepository.findAll()) {
            if (e.getStatus() == EscrowStatus.PAID_OUT) {
                bundleSettledCents += e.getAmountCents();
            } else if (e.getStatus() == EscrowStatus.HELD || e.getStatus() == EscrowStatus.AVAILABLE) {
                bundleInEscrowCents += e.getAmountCents();
            }
        }

        long activePro = platformSubscriptionRepository.countByPlanCodeAndStatus(
                PlatformPlanCode.PRO, SubscriptionStatus.ACTIVE);

        Map<String, Object> result = new HashMap<>();
        result.put("bundleSettledCents", bundleSettledCents);
        result.put("bundleInEscrowCents", bundleInEscrowCents);
        result.put("activeProSubscribers", activePro);
        result.put("note", "platform fee % not yet modeled (Section 22 deferred); these are gross figures");
        return result;
    }

    @GetMapping("/sellers/top")
    public List<Map<String, Object>> topSellers() {
        return bundleSubscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .collect(Collectors.groupingBy(
                        BundleSubscription::getSellerId,
                        Collectors.summingLong(BundleSubscription::getPricePaidCents)))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("sellerId", e.getKey());
                    row.put("revenueCents", e.getValue());
                    return row;
                })
                .toList();
    }

    /* ----- Seller lifecycle (Sections 16, 17) ----- */

    @PostMapping("/sellers/{sellerId}/freeze")
    public SellerLifecycle freeze(@PathVariable UUID sellerId, @RequestBody LifecycleRequest body) {
        return sellerLifecycleService.freeze(sellerId, body.getReason());
    }

    @PostMapping("/sellers/{sellerId}/unfreeze")
    public SellerLifecycle unfreeze(@PathVariable UUID sellerId, @RequestBody LifecycleRequest body) {
        return sellerLifecycleService.unfreeze(sellerId, body.getReason());
    }

    @PostMapping("/sellers/{sellerId}/ban")
    public SellerLifecycle ban(@PathVariable UUID sellerId, @RequestBody LifecycleRequest body) {
        return sellerLifecycleService.ban(sellerId, body.getReason());
    }

    @PostMapping("/sellers/{sellerId}/winding-down")
    public SellerLifecycle startWindingDown(@PathVariable UUID sellerId, @RequestBody LifecycleRequest body) {
        return sellerLifecycleService.startWindingDown(sellerId, body.getReason());
    }

    @PostMapping("/sellers/{sellerId}/winding-down/revert")
    public SellerLifecycle revertWindingDown(@PathVariable UUID sellerId, @RequestBody LifecycleRequest body) {
        return sellerLifecycleService.revertWindingDown(sellerId, body.getReason());
    }

    @Data
    public static class LifecycleRequest {
        private String reason;
    }
}
