package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.BundleCheckoutRequest;
import com.example.DumbleSubscription.dto.BundleSubscriptionResponse;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.service.BundleSubscriptionService;
import com.example.DumbleSubscription.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class BundleSubscriptionController {

    private final BundleSubscriptionService service;
    private final BundleSubscriptionRepository repository;
    private final IdempotencyService idempotencyService;

    public BundleSubscriptionController(BundleSubscriptionService service,
                                        BundleSubscriptionRepository repository,
                                        IdempotencyService idempotencyService) {
        this.service = service;
        this.repository = repository;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/bundle-subscriptions/checkout")
    public ResponseEntity<BundleSubscriptionResponse> checkout(@AuthenticationPrincipal CurrentUser user,
                                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                               @Valid @RequestBody BundleCheckoutRequest req) {
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /bundle-subscriptions/checkout",
                user.getId(),
                201,
                BundleSubscriptionResponse.class,
                () -> service.checkout(user.getId(), req));
        return ResponseEntity.status(cached.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(cached.value());
    }

    @GetMapping("/me/bundle-subscriptions")
    public List<BundleSubscriptionResponse> myBundleSubscriptions(@AuthenticationPrincipal CurrentUser user,
                                                                  @RequestParam(value = "status", required = false) String statusFilter) {
        SubscriptionStatus status = statusFilter == null ? null : SubscriptionStatus.valueOf(statusFilter.toUpperCase());
        List<BundleSubscription> subs;
        if (status == null) {
            subs = repository.findByParticipantIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
            subs.addAll(repository.findByParticipantIdAndStatus(user.getId(), SubscriptionStatus.CANCELLED));
            subs.addAll(repository.findByParticipantIdAndStatus(user.getId(), SubscriptionStatus.EXPIRED));
        } else {
            subs = repository.findByParticipantIdAndStatus(user.getId(), status);
        }
        return subs.stream().map(BundleSubscriptionResponse::from).collect(Collectors.toList());
    }

    @GetMapping("/bundle-subscriptions/{id}")
    public BundleSubscriptionResponse getById(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) {
        BundleSubscription sub = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        if (!sub.getParticipantId().equals(user.getId()) && !"ADMIN".equals(user.getUserType())) {
            throw new ResourceNotFoundException("Subscription not found");      // hide existence from non-owners
        }
        return BundleSubscriptionResponse.from(sub);
    }

    @PostMapping("/me/bundle-subscriptions/{id}/cancel")
    public BundleSubscriptionResponse cancelAutoRenew(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) {
        BundleSubscription sub = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        if (!sub.getParticipantId().equals(user.getId())) {
            throw new ResourceNotFoundException("Subscription not found");
        }
        // Cancel-at-period-end (Decision 6.1): keep access until endsAt, stop renewing.
        // Only an ACTIVE sub enters the CANCELLED interim state — never resurrect an
        // EXPIRED/REFUNDED/PENDING sub into an entitled (isEntitled) state.
        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
        }
        sub.setAutoRenew(false);
        sub.setCancelledAt(Instant.now());
        sub.setUpdatedAt(Instant.now());
        repository.save(sub);
        return BundleSubscriptionResponse.from(sub);
    }
}
