package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.CreateGymRegistrationRequest;
import com.dumble.service.gym.domain.dto.GymRegistrationResponse;
import com.dumble.service.gym.service.GymRegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Applicant-facing gym-owner registration. A participant submits and tracks
 * their application; admin review lives in {@code AdminGymRegistrationController}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/gym-registrations")
@Tag(name = "gym registration")
public class GymRegistrationController {

    private final GymRegistrationService gymRegistrationService;

    @PostMapping
    public ResponseEntity<GymRegistrationResponse> submit(
            @Valid @RequestBody CreateGymRegistrationRequest request,
            @RequestHeader("Authorization") String token) {
        return new ResponseEntity<>(gymRegistrationService.submit(request, token), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<GymRegistrationResponse>> listMine(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymRegistrationService.listMine(token));
    }

    /** Edit a registration the admin sent back for changes, then resubmit (same id). */
    @PatchMapping("/{id}")
    public ResponseEntity<GymRegistrationResponse> edit(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody CreateGymRegistrationRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymRegistrationService.editMine(token, id, request));
    }
}
