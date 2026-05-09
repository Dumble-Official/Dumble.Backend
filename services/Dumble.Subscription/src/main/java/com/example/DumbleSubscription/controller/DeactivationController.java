package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.SellerLifecycle;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.service.SellerLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Section 18 — self-deactivate. Blocks if the seller has any active subs and
 * directs them to support to enter WindingDown instead.
 */
@RestController
public class DeactivationController {

    private final SellerLifecycleService service;

    public DeactivationController(SellerLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/me/deactivate")
    public ResponseEntity<SellerLifecycle> deactivate(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(service.close(user.getId()));
    }
}
