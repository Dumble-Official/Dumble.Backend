package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.dto.request.OnboardingRequest;
import com.example.DumbleAuthentication.dto.request.UpdateProfileRequest;
import com.example.DumbleAuthentication.dto.response.UserResponse;
import com.example.DumbleAuthentication.dto.response.UserSummaryResponse;
import com.example.DumbleAuthentication.repository.UserRepository;
import com.example.DumbleAuthentication.service.AccountDeletionService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    public UserController(UserRepository userRepository, AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
    }

    /** A1 — Admin/Moderator user search (by email or name). Gated in SecurityConfig. */
    @GetMapping
    public Page<UserResponse> search(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userRepository.search(query, PageRequest.of(page, Math.min(Math.max(size, 1), 100)))
                .map(UserResponse::from);
    }

    /**
     * Public people-search for follow/discovery cards — any authenticated user.
     * Returns a lightweight, PII-free {@link UserSummaryResponse} projection and
     * is paginated. Short/blank queries return an empty page so a single
     * keystroke never triggers a full-table scan. Open to all in SecurityConfig
     * (declared before the admin {@code /api/users/*} rule).
     */
    @GetMapping("/search")
    public Page<UserSummaryResponse> searchPublic(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.length() < 2) {
            return Page.empty(pageable);
        }
        return userRepository.searchPublic(q, pageable);
    }

    /** A1 — Admin/Moderator fetch a single user by id. Gated in SecurityConfig. */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnAccount() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        // Right-to-be-forgotten: hard-delete and publish AccountDeleted so other services purge.
        accountDeletionService.deleteOwnAccount(user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/onboarding")
    public ResponseEntity<UserResponse> onboarding(@Valid @RequestBody OnboardingRequest request) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getWeight() != null) user.setWeight(request.getWeight());
        if (request.getHeight() != null) user.setHeight(request.getHeight());
        if (request.getFitnessGoals() != null) user.setFitnessGoals(request.getFitnessGoals());
        user = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (request.getWeight() != null) user.setWeight(request.getWeight());
        if (request.getInjuries() != null) user.setInjuries(request.getInjuries());
        if (request.getFitnessGoals() != null) user.setFitnessGoals(request.getFitnessGoals());
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getPfp() != null) user.setPfp(request.getPfp());
        if (request.getBio() != null) user.setBio(request.getBio());
        user = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email).orElse(null);
    }
}
