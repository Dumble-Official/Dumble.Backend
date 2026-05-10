package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.dto.EntitlementsResponse;
import com.example.DumbleSubscription.service.EntitlementsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/entitlements")
public class EntitlementsController {

    private final EntitlementsService entitlementsService;

    public EntitlementsController(EntitlementsService entitlementsService) {
        this.entitlementsService = entitlementsService;
    }

    @GetMapping
    public ResponseEntity<EntitlementsResponse> getMyEntitlements(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(entitlementsService.forUser(user.getId()));
    }
}
