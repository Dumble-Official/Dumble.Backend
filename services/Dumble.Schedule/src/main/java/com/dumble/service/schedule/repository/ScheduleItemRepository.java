package com.dumble.service.schedule.repository;

import com.dumble.service.schedule.domain.ScheduleItem;
import com.dumble.service.schedule.domain.TableType;
import com.dumble.service.schedule.domain.Weekday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, UUID> {

    List<ScheduleItem> findByScheduleIdOrderByTableTypeAscWeekdayAscPositionAsc(UUID scheduleId);

    int countByScheduleIdAndTableTypeAndWeekday(UUID scheduleId, TableType tableType, Weekday weekday);
}
