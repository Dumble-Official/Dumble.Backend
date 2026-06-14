package com.dumble.service.schedule.dto;

import java.util.List;
import java.util.UUID;

/** The full schedule: both 7-day tables (always Sun..Sat), meals carry targets. */
public record ScheduleResponse(
        UUID scheduleId,
        String timezone,
        List<DayView> exercises,
        List<DayView> meals) {
}
