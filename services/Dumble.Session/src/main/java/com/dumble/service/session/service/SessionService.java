package com.dumble.service.session.service;

import com.dumble.service.session.domain.dto.request.SessionCreateRequest;
import com.dumble.service.session.domain.dto.request.SessionUpdateRequest;
import com.dumble.service.session.domain.dto.response.SessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SessionService {
    SessionResponse createSession(SessionCreateRequest request);
    SessionResponse updateSession(UUID id, SessionUpdateRequest request);
    SessionResponse getSessionById(UUID id);
    Page<SessionResponse> getAllSessions(Pageable pageable);
    Page<SessionResponse> searchByTitle(String title, Pageable pageable);
    void deleteSession(UUID id);
    SessionResponse updateSessionSecure(UUID id, SessionUpdateRequest request, UUID callerId, boolean isAdmin);
    void deleteSessionSecure(UUID id, UUID callerId, boolean isAdmin);
}
