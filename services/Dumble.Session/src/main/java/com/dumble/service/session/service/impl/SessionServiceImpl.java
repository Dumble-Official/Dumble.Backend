package com.dumble.service.session.service.impl;

import com.dumble.service.session.domain.dto.request.SessionCreateRequest;
import com.dumble.service.session.domain.dto.request.SessionUpdateRequest;
import com.dumble.service.session.domain.dto.response.SessionResponse;
import com.dumble.service.session.domain.entity.Session;
import com.dumble.service.session.domain.mapper.SessionMapper;
import com.dumble.service.session.exception.BadRequestException;
import com.dumble.service.session.exception.ResourceNotFoundException;
import com.dumble.service.session.repository.SessionRepository;
import com.dumble.service.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionServiceImpl implements SessionService {
    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;



    @Override
    public SessionResponse createSession(SessionCreateRequest request) {

//        LocalDateTime weekStart = request.getStartTime()
//                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
//                .withHour(0).withMinute(0).withSecond(0);
//        LocalDateTime weekEnd = weekStart.plusDays(6)
//                .withHour(23).withMinute(59).withSecond(59);
//
//        long sessionCount = sessionRepository.countSessionsInWeek(request.getGymId(), request.getTrainerId(), weekStart, weekEnd);
//        int freeLimit = 4;
//        if(sessionCount >= freeLimit) {
//            throw new RuntimeException("Maximum weekly limit reached (4 sessions).");
//        }

        if(request.getGymId() != null) {
            long overlapping = sessionRepository.countConcurrentSessions(request.getGymId(), request.getStartTime(), request.getEndTime());
            if(overlapping >= 3) {
                throw new BadRequestException("Gym is full! All rooms are booked for this time slot.");
            }
        }
        Session session = sessionMapper.toEntity(request);
        return sessionMapper.toResponse(sessionRepository.save(session));
    }

    @Override
    public SessionResponse updateSession(UUID id, SessionUpdateRequest request) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));

        sessionMapper.updateEntityFromDto(request, session);
        return sessionMapper.toResponse(sessionRepository.save(session));
    }

    @Override
    public SessionResponse getSessionById(UUID id) {
        return sessionRepository.findById(id)
                .map(sessionMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
    }

    @Override
    public Page<SessionResponse> getAllSessions(Pageable pageable) {
        return sessionRepository.findAll(pageable).map(sessionMapper::toResponse);
    }

    @Override
    public Page<SessionResponse> searchByTitle(String title, Pageable pageable) {
        return sessionRepository.findByTitleContainingIgnoreCase(title, pageable)
                .map(sessionMapper::toResponse);
    }

    @Override
    public void deleteSession(UUID id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
        sessionRepository.delete(session);
    }
}
