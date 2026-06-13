package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;
import com.example.DumbleAuthentication.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Service-to-service endpoints, authenticated by a shared secret header
 * ({@code X-Internal-Secret}) rather than a user JWT — they are called by other
 * backend services, not by end users. Reachable only inside the cluster; the
 * path is permitted in SecurityConfig and gated here by the secret.
 *
 * Used by the gym service to promote an applicant to GYM_OWNER once their
 * gym registration is approved (auth owns userType — "the gym service only
 * verifies, never mints").
 */
@RestController
@RequestMapping("/api/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;
    private final String internalSecret;

    public InternalUserController(UserRepository userRepository,
                                  @Value("${internal.api-secret:}") String internalSecret) {
        this.userRepository = userRepository;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/{id}/promote-gym-owner")
    public ResponseEntity<Void> promoteToGymOwner(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {

        if (!secretOk(secret)) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        user.setUserType(UserType.GYM_OWNER);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    /** Constant-time comparison; fail closed if the secret isn't configured. */
    private boolean secretOk(String provided) {
        if (internalSecret == null || internalSecret.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                internalSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
