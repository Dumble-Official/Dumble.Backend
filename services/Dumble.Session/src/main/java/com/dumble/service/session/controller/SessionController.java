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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/session/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;


    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody SessionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SessionResponse> updateSession(@PathVariable UUID id, @Valid @RequestBody SessionUpdateRequest request) {
        return ResponseEntity.ok(sessionService.updateSession(id, request));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}