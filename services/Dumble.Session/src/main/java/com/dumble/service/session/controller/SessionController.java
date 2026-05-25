package com.dumble.service.session.controller;

import com.dumble.service.session.domain.dto.request.SessionCreateRequest;
import com.dumble.service.session.domain.dto.request.SessionUpdateRequest;
import com.dumble.service.session.domain.dto.response.SessionResponse;
import com.dumble.service.session.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TRAINER', 'GYM_OWNER', 'ADMIN')")
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody SessionCreateRequest request,
            Authentication authentication) {

        UUID creatorId = UUID.fromString(authentication.getName());
        boolean isTrainer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TRAINER"));

        if (isTrainer) {
            request.setTrainerId(creatorId);
        } else {
            request.setGymId(creatorId);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(request));
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyRole('TRAINER', 'GYM_OWNER')")
    public ResponseEntity<SessionResponse> updateSession(
            @PathVariable UUID id,
            @Valid @RequestBody SessionUpdateRequest request,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(sessionService.updateSessionSecure(id, request, callerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSessionById(@PathVariable UUID id) {
        return ResponseEntity.ok(sessionService.getSessionById(id));
    }

    @GetMapping
    public ResponseEntity<Page<SessionResponse>> getAllSessions(@PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(sessionService.getAllSessions(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SessionResponse>> searchByTitle(
            @RequestParam String title,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(sessionService.searchByTitle(title, pageable));
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('TRAINER', 'GYM_OWNER')")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id, Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        sessionService.deleteSessionSecure(id, callerId);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}