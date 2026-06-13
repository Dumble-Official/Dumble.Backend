package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.dto.request.CreateRoleRequestRequest;
import com.example.DumbleAuthentication.dto.response.RoleRequestResponse;
import com.example.DumbleAuthentication.repository.UserRepository;
import com.example.DumbleAuthentication.service.RoleRequestService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Applicant-facing role-promotion endpoints. A PARTICIPANT submits and tracks
 * their request to become TRAINER or GYM_OWNER. Admin review lives in
 * {@code AdminRoleRequestController}.
 */
@RestController
@RequestMapping("/api/users/me/role-requests")
public class RoleRequestController {

    private final RoleRequestService roleRequestService;
    private final UserRepository userRepository;

    public RoleRequestController(RoleRequestService roleRequestService, UserRepository userRepository) {
        this.roleRequestService = roleRequestService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<RoleRequestResponse> submit(@Valid @RequestBody CreateRoleRequestRequest request) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleRequestService.submit(user, request));
    }

    @GetMapping
    public ResponseEntity<List<RoleRequestResponse>> listMine() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(roleRequestService.listMine(user.getId()));
    }

    /** Edit a request the admin sent back for changes, then resubmit it (same id). */
    @PatchMapping("/{id}")
    public ResponseEntity<RoleRequestResponse> edit(
            @PathVariable java.util.UUID id, @Valid @RequestBody CreateRoleRequestRequest request) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(roleRequestService.editMine(user.getId(), id, request));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
