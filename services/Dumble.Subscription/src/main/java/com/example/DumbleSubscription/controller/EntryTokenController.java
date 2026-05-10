package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.dto.EntryTokenResponse;
import com.example.DumbleSubscription.dto.ScanRequest;
import com.example.DumbleSubscription.dto.ScanResponse;
import com.example.DumbleSubscription.service.EntryTokenService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class EntryTokenController {

    private final EntryTokenService service;

    public EntryTokenController(EntryTokenService service) {
        this.service = service;
    }

    /** Decision 21.2 — generates a fresh token; supersedes prior. */
    @PostMapping("/me/bundle-subscriptions/{id}/entry-token/generate")
    public EntryTokenResponse generate(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.generate(user.getId(), id);
    }

    /** Decision 21.4 + 21.6 — gym staff scans, gets full participant + plan details. */
    @PostMapping("/entry-tokens/scan")
    public ScanResponse scan(@AuthenticationPrincipal CurrentUser staff, @Valid @RequestBody ScanRequest req) {
        return service.scan(staff.getId(), req);
    }
}
