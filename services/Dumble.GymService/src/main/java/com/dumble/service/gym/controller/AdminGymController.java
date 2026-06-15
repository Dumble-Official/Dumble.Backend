package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.GymResponse;
import com.dumble.service.gym.domain.enumuration.GymStatus;
import com.dumble.service.gym.service.GymService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin/Moderator gym moderation. Auth (ADMIN/MODERATOR) is enforced in the service from the
 * forwarded token. Verify takes a self-created gym live; status toggles ACTIVE/SUSPENDED.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/gyms")
@Tag(name = "gym admin")
public class AdminGymController {

    private final GymService gymService;

    @PostMapping("/{gymId}/verify")
    public ResponseEntity<GymResponse> verify(
            @PathVariable UUID gymId, @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymService.verifyGym(gymId, token));
    }

    @PatchMapping("/{gymId}/status")
    public ResponseEntity<GymResponse> setStatus(
            @PathVariable UUID gymId,
            @RequestParam GymStatus status,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymService.setGymStatus(gymId, status, token));
    }
}
