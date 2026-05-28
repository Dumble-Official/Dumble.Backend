package com.dumble.service.session.service.impl;

import com.dumble.service.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionStatusScheduler {

    private final SessionRepository sessionRepository;

    @Scheduled(fixedRate = 3600000)
    public void autoCompleteSessions() {
        log.info("Running auto-complete scheduler for ended sessions...");
        int updatedCount = sessionRepository.updateExpiredSessionsToCompleted(LocalDateTime.now());
        if (updatedCount > 0) {
            log.info("Successfully moved {} sessions to COMPLETED status.", updatedCount);
        }
    }
}
