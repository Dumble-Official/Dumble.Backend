package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.dto.request.OnboardingRequest;
import com.example.DumbleAuthentication.dto.request.UpdateProfileRequest;
import com.example.DumbleAuthentication.dto.response.PublicProfileResponse;
import com.example.DumbleAuthentication.dto.response.UserResponse;
import com.example.DumbleAuthentication.dto.response.UserSummaryResponse;
import com.example.DumbleAuthentication.repository.UserRepository;
import com.example.DumbleAuthentication.service.AccountDeletionService;
import com.example.DumbleAuthentication.service.CloudinaryService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;
    private final CloudinaryService cloudinaryService;

    public UserController(UserRepository userRepository,
                          AccountDeletionService accountDeletionService,
                          CloudinaryService cloudinaryService) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
        this.cloudinaryService = cloudinaryService;
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

    /**
     * Public, PII-free summary of any user by id — for profile screens that need
     * the viewed user's role/handle/avatar (e.g. to decide whether to show a
     * "Bundles" tab). Any authenticated user; distinct from the admin getById.
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<UserSummaryResponse> summary(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(UserSummaryResponse.from(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Another user's profile, privacy-filtered — any authenticated user. Returns
     * identity always and the rest of the fields only when the owner hasn't
     * hidden them. (The owner reads their own full profile via {@code /me}.)
     */
    @GetMapping("/{id}/profile")
    public ResponseEntity<PublicProfileResponse> publicProfile(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(PublicProfileResponse.from(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
        if (request.getHiddenFields() != null) {
            // Keep only recognised, controllable keys so a client can't stash junk.
            user.setHiddenFields(request.getHiddenFields().stream()
                    .filter(PublicProfileResponse.CONTROLLABLE_FIELDS::contains)
                    .distinct()
                    .toList());
        }
        user = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Upload (or replace) the current user's profile picture. The client sends
     * the raw image; we upload it to Cloudinary server-side and store the URL.
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadAvatar(@RequestPart("file") MultipartFile file) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        user.setPfp(cloudinaryService.uploadFile(file));
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
