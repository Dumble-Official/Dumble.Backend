package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.domain.RoleRequestStatus;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.dto.request.ReviewMessageRequest;
import com.example.DumbleAuthentication.dto.response.RoleRequestResponse;
import com.example.DumbleAuthentication.repository.UserRepository;
import com.example.DumbleAuthentication.service.RoleRequestService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin review of role-promotion requests. Gated to ADMIN in SecurityConfig.
 * The reviewer's id is recorded on each decision.
 */
@RestController
@RequestMapping("/api/admin/role-requests")
public class AdminRoleRequestController {

    private final RoleRequestService roleRequestService;
    private final UserRepository userRepository;

    public AdminRoleRequestController(RoleRequestService roleRequestService, UserRepository userRepository) {
        this.roleRequestService = roleRequestService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Page<RoleRequestResponse>> list(
            @RequestParam(required = false) RoleRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(roleRequestService.listForAdmin(status, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<RoleRequestResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(roleRequestService.approve(id, adminId()));
    }

    @PostMapping("/{id}/request-changes")
    public ResponseEntity<RoleRequestResponse> requestChanges(
            @PathVariable UUID id, @Valid @RequestBody ReviewMessageRequest body) {
        return ResponseEntity.ok(roleRequestService.requestChanges(id, adminId(), body.getMessage()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RoleRequestResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody ReviewMessageRequest body) {
        return ResponseEntity.ok(roleRequestService.reject(id, adminId(), body.getMessage()));
    }

    private UUID adminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userRepository.findByEmail(auth.getName()).orElseThrow();
        return admin.getId();
    }
}
