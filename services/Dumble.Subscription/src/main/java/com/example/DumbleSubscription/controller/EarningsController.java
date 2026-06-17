package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.SellerBankAccount;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.dto.EarningsCohort;
import com.example.DumbleSubscription.dto.EarningsSummary;
import com.example.DumbleSubscription.dto.PayoutItem;
import com.example.DumbleSubscription.dto.SellerBankAccountRequest;
import com.example.DumbleSubscription.dto.SubscriberView;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.SellerBankAccountRepository;
import com.example.DumbleSubscription.service.EarningsService;
import com.example.DumbleSubscription.service.SubscriberEnrichmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class EarningsController {

    private final EarningsService earningsService;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final SellerBankAccountRepository bankAccountRepository;
    private final SubscriberEnrichmentService subscriberEnrichmentService;

    public EarningsController(EarningsService earningsService,
                              BundleSubscriptionRepository bundleSubscriptionRepository,
                              SellerBankAccountRepository bankAccountRepository,
                              SubscriberEnrichmentService subscriberEnrichmentService) {
        this.earningsService = earningsService;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.subscriberEnrichmentService = subscriberEnrichmentService;
    }

    @GetMapping("/me/earnings/summary")
    public EarningsSummary summary(@AuthenticationPrincipal CurrentUser user) {
        return earningsService.summary(user.getId());
    }

    @GetMapping("/me/earnings/cohorts")
    public List<EarningsCohort> cohorts(@AuthenticationPrincipal CurrentUser user) {
        return earningsService.cohorts(user.getId());
    }

    @GetMapping("/me/earnings/payouts")
    public List<PayoutItem> payouts(@AuthenticationPrincipal CurrentUser user) {
        return earningsService.payouts(user.getId());
    }

    /** Decision 15.1 — enriched subscriber list. Display name + photo from Authentication; no PII. */
    @GetMapping("/me/subscribers")
    public List<SubscriberView> mySubscribers(@AuthenticationPrincipal CurrentUser user,
                                              @RequestParam(value = "status", defaultValue = "ACTIVE") String status) {
        SubscriptionStatus s = SubscriptionStatus.valueOf(status.toUpperCase());
        List<BundleSubscription> subs = bundleSubscriptionRepository.findBySellerIdAndStatus(user.getId(), s);
        return subscriberEnrichmentService.enrich(subs);
    }

    @GetMapping("/me/subscribers/stats")
    public Map<String, Object> subscriberStats(@AuthenticationPrincipal CurrentUser user) {
        List<BundleSubscription> active = bundleSubscriptionRepository
                .findBySellerIdAndStatusIn(user.getId(), SubscriptionStatus.ENTITLED);
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        long newThisMonth = active.stream().filter(s -> s.getStartedAt().isAfter(cutoff)).count();

        Map<UUID, Long> perBundle = new HashMap<>();
        active.forEach(s -> perBundle.merge(s.getBundleId(), 1L, Long::sum));

        Map<String, Object> result = new HashMap<>();
        result.put("active", active.size());
        result.put("newLast30Days", newThisMonth);
        result.put("perBundle", perBundle);
        return result;
    }

    @GetMapping("/me/revenue")
    public Map<String, Object> revenue(@AuthenticationPrincipal CurrentUser user,
                                       @RequestParam(value = "period", defaultValue = "30d") String period) {
        Instant cutoff = switch (period) {
            case "7d"  -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            case "all" -> Instant.EPOCH;
            default    -> Instant.now().minus(30, ChronoUnit.DAYS);
        };
        long revenueCents = bundleSubscriptionRepository.findAll().stream()
                .filter(s -> s.getSellerId().equals(user.getId()))
                .filter(s -> s.getStartedAt().isAfter(cutoff))
                .mapToLong(BundleSubscription::getPricePaidCents)
                .sum();
        return Map.of(
                "period", period,
                "revenueCents", revenueCents
        );
    }

    @GetMapping("/me/retention")
    public Map<String, Object> retention(@AuthenticationPrincipal CurrentUser user) {
        List<BundleSubscription> all = bundleSubscriptionRepository.findAll().stream()
                .filter(s -> s.getSellerId().equals(user.getId()))
                .toList();
        long total = all.size();
        long expired = all.stream().filter(s -> s.getStatus() == SubscriptionStatus.EXPIRED).count();
        long renewedAtLeastOnce = all.stream()
                .filter(s -> s.getEndsAt() != null && s.getEndsAt().isAfter(s.getStartedAt().plus(35, ChronoUnit.DAYS)))
                .count();
        double avgDurationDays = all.isEmpty() ? 0
                : all.stream().mapToLong(BundleSubscription::getDurationDays).average().orElse(0);
        return Map.of(
                "totalSubscriptions", total,
                "expired", expired,
                "renewedAtLeastOnce", renewedAtLeastOnce,
                "averageDurationDays", avgDurationDays
        );
    }

    @GetMapping("/me/bank-account")
    public SellerBankAccount getBankAccount(@AuthenticationPrincipal CurrentUser user) {
        return bankAccountRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No bank account on file"));
    }

    @PostMapping("/me/bank-account")
    public ResponseEntity<SellerBankAccount> setBankAccount(@AuthenticationPrincipal CurrentUser user,
                                                            @Valid @RequestBody SellerBankAccountRequest req) {
        SellerBankAccount account = bankAccountRepository.findById(user.getId())
                .orElseGet(() -> {
                    SellerBankAccount fresh = new SellerBankAccount();
                    fresh.setSellerId(user.getId());
                    fresh.setCreatedAt(Instant.now());
                    return fresh;
                });
        account.setAccountHolderName(req.getAccountHolderName());
        account.setDestination(req.getDestination());
        account.setDestinationType(req.getDestinationType());
        account.setUpdatedAt(Instant.now());
        bankAccountRepository.save(account);
        return ResponseEntity.ok(account);
    }
}
