package com.dumble.service.schedule.repository;

import com.dumble.service.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    Optional<Schedule> findByUserId(UUID userId);
}
