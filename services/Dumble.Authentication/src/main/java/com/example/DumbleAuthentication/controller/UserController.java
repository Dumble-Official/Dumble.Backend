package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.dto.request.OnboardingRequest;
import com.example.DumbleAuthentication.dto.request.UpdateProfileRequest;
import com.example.DumbleAuthentication.dto.response.UserResponse;
import com.example.DumbleAuthentication.repository.UserRepository;
import com.example.DumbleAuthentication.service.AccountDeletionService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    public UserController(UserRepository userRepository, AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
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
