package com.dumble.service.schedule.repository;

import com.dumble.service.schedule.domain.MealDayTarget;
import com.dumble.service.schedule.domain.Weekday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealDayTargetRepository extends JpaRepository<MealDayTarget, UUID> {

    List<MealDayTarget> findByScheduleId(UUID scheduleId);

    Optional<MealDayTarget> findByScheduleIdAndWeekday(UUID scheduleId, Weekday weekday);
}
