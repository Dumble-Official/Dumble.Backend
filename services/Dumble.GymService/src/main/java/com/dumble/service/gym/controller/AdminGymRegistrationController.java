package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.GymRegistrationResponse;
import com.dumble.service.gym.domain.dto.ReviewMessageRequest;
import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import com.dumble.service.gym.service.GymRegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin review of gym-owner registrations. ADMIN-only (enforced in the service).
 * Approving creates an ACTIVE/verified Gym per branch and promotes the applicant.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/gym-registrations")
@Tag(name = "gym registration admin")
public class AdminGymRegistrationController {

    private final GymRegistrationService gymRegistrationService;

    @GetMapping
    public ResponseEntity<Page<GymRegistrationResponse>> list(
            @RequestParam(required = false) RegistrationStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymRegistrationService.listForAdmin(token, status, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<GymRegistrationResponse> approve(
            @PathVariable UUID id, @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymRegistrationService.approve(token, id));
    }

    @PostMapping("/{id}/request-changes")
    public ResponseEntity<GymRegistrationResponse> requestChanges(
            @PathVariable UUID id, @Valid @RequestBody ReviewMessageRequest body,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymRegistrationService.requestChanges(token, id, body.getMessage()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<GymRegistrationResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody ReviewMessageRequest body,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymRegistrationService.reject(token, id, body.getMessage()));
    }
}
