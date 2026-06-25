package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.client.dto.CheckoutResponse;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.dto.MyPlanResponse;
import com.example.DumbleSubscription.dto.PlanResponse;
import com.example.DumbleSubscription.dto.PlanUpgradeCheckoutRequest;
import com.example.DumbleSubscription.dto.PlanUpgradeRequest;
import com.example.DumbleSubscription.repository.PlanRepository;
import com.example.DumbleSubscription.service.IdempotencyService;
import com.example.DumbleSubscription.service.PlatformPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PlanController {

    private final PlanRepository planRepository;
    private final PlatformPlanService platformPlanService;
    private final IdempotencyService idempotencyService;

    public PlanController(PlanRepository planRepository,
                          PlatformPlanService platformPlanService,
                          IdempotencyService idempotencyService) {
        this.planRepository = planRepository;
        this.platformPlanService = platformPlanService;
        this.idempotencyService = idempotencyService;
    }

    /** Public — pricing page. */
    @GetMapping("/plans")
    public List<PlanResponse> listPlans() {
        return planRepository.findByActiveTrue().stream().map(PlanResponse::from).toList();
    }

    @GetMapping("/me/plan")
    public MyPlanResponse myPlan(@AuthenticationPrincipal CurrentUser user) {
        return platformPlanService.getMyPlan(user.getId());
    }

    @PostMapping("/me/plan/upgrade")
    public ResponseEntity<MyPlanResponse> upgrade(@AuthenticationPrincipal CurrentUser user,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody PlanUpgradeRequest req) {
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /me/plan/upgrade",
                user.getId(),
                200,
                MyPlanResponse.class,
                () -> platformPlanService.upgradeToPro(user.getId(), req));
        return ResponseEntity.status(HttpStatus.OK).body(cached.value());
    }

    /**
     * Hosted-checkout Pro upgrade: returns a Paymob iframe URL the app opens in a
     * WebView. The subscription is claimed PENDING and activated by the
     * charge.succeeded webhook once the card payment clears. No Idempotency-Key
     * header needed — the underlying charge key is derived from the subscription.
     */
    @PostMapping("/me/plan/upgrade/checkout")
    public ResponseEntity<CheckoutResponse> upgradeCheckout(
            @AuthenticationPrincipal CurrentUser user,
            @RequestBody(required = false) PlanUpgradeCheckoutRequest req) {
        PlanUpgradeCheckoutRequest body = req == null ? new PlanUpgradeCheckoutRequest() : req;
        return ResponseEntity.ok(platformPlanService.createUpgradeCheckout(user.getId(), body));
    }

    @PostMapping("/me/plan/cancel")
    public ResponseEntity<MyPlanResponse> cancel(@AuthenticationPrincipal CurrentUser user,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var cached = idempotencyService.executeOrFetch(
                idempotencyKey,
                "POST /me/plan/cancel",
                user.getId(),
                200,
                MyPlanResponse.class,
                () -> platformPlanService.cancelPro(user.getId()));
        return ResponseEntity.status(HttpStatus.OK).body(cached.value());
    }
}
