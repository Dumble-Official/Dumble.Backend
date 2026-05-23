package com.dumble.service.session.repository;

import com.dumble.service.session.domain.entity.Session;
import com.dumble.service.session.domain.enumuration.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    Page<Session> findByGymId(UUID gymId, Pageable pageable);

    Page<Session> findByTrainerId(UUID trainerId, Pageable pageable);

    Page<Session> findByStatus(SessionStatus status, Pageable pageable);

    @Query("SELECT s FROM Session s WHERE s.status IN ('DRAFT', 'PUBLISHED') ORDER BY s.startTime ASC")
    List<Session> findActiveSessions();

    Page<Session> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Query("SELECT s FROM Session s WHERE s.currentParticipants < s.maxCapacity AND s.status = 'PUBLISHED'")
    Page<Session> findSessionsWithAvailableSpots(Pageable pageable);

    // مسموح للجيم يكون شغال كام سيشن في نفس الوقت (Overlapping Sessions)
    @Query("SELECT COUNT(s) FROM Session s WHERE s.gymId = :gymId " +
            "AND s.status NOT IN ('CANCELLED') " +
            "AND (s.startTime < :endTime AND s.endTime > :startTime)")
    long countConcurrentSessions(@Param("gymId") UUID gymId,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    // الجيم او الترينر يكون مسموحله يعمل عدد سيشنز معين في الاسبوع
    @Query("SELECT COUNT(s) FROM Session s WHERE " +
            "(:gymId IS NULL OR s.gymId = :gymId) AND " +
            "(:trainerId IS NULL OR s.trainerId = :trainerId) AND " +
            "s.status NOT IN ('CANCELLED') AND " +
            "s.startTime >= :weekStart AND s.startTime <= :weekEnd")
    long countSessionsInWeek(@Param("gymId") UUID gymId,
                             @Param("trainerId") UUID trainerId,
                             @Param("weekStart") LocalDateTime weekStart,
                             @Param("weekEnd") LocalDateTime weekEnd);
}
