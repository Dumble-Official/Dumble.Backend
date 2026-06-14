package com.dumble.service.schedule.reminder;

import com.dumble.service.schedule.domain.Schedule;
import com.dumble.service.schedule.domain.ScheduleItem;
import com.dumble.service.schedule.domain.Weekday;
import com.dumble.service.schedule.messaging.EventPublisher;
import com.dumble.service.schedule.messaging.ReminderEvent;
import com.dumble.service.schedule.repository.ItemCompletionRepository;
import com.dumble.service.schedule.repository.ScheduleItemRepository;
import com.dumble.service.schedule.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Once per local day, when a client's local time has reached {@code from-hour}
 * and they still have unmarked items for today, emit a reminder event. The
 * plan is a standing week; "today" is resolved in the client's timezone (UTC if
 * unset) and completion is per calendar date — so each day is evaluated fresh
 * without the plan resetting.
 */
@Component
@ConditionalOnProperty(name = "schedule.reminder.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderSweeper {

    private static final Logger log = LoggerFactory.getLogger(ReminderSweeper.class);

    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository itemRepository;
    private final ItemCompletionRepository completionRepository;
    private final EventPublisher eventPublisher;
    private final int fromHour;

    public ReminderSweeper(ScheduleRepository scheduleRepository,
                           ScheduleItemRepository itemRepository,
                           ItemCompletionRepository completionRepository,
                           EventPublisher eventPublisher,
                           @Value("${schedule.reminder.from-hour:20}") int fromHour) {
        this.scheduleRepository = scheduleRepository;
        this.itemRepository = itemRepository;
        this.completionRepository = completionRepository;
        this.eventPublisher = eventPublisher;
        this.fromHour = fromHour;
    }

    @Scheduled(fixedDelayString = "${schedule.reminder.sweep-ms:900000}")
    @Transactional
    public void sweep() {
        for (Schedule s : scheduleRepository.findAll()) {
            try {
                maybeRemind(s);
            } catch (Exception e) {
                log.error("Reminder sweep failed for schedule {}", s.getId(), e);
            }
        }
    }

    private void maybeRemind(Schedule s) {
        ZoneId zone = parseZone(s.getTimezone());
        ZonedDateTime nowLocal = ZonedDateTime.now(zone);
        if (nowLocal.getHour() < fromHour) return;                      // not end-of-day yet
        LocalDate today = nowLocal.toLocalDate();
        if (today.equals(s.getLastRemindedOn())) return;               // already reminded today

        Weekday wd = toWeekday(nowLocal.getDayOfWeek());
        List<ScheduleItem> todays = itemRepository
                .findByScheduleIdOrderByTableTypeAscWeekdayAscPositionAsc(s.getId()).stream()
                .filter(i -> i.getWeekday() == wd)
                .toList();
        if (todays.isEmpty()) return;

        Set<UUID> done = completionRepository
                .findByItemIdInAndCompletedOn(todays.stream().map(ScheduleItem::getId).toList(), today).stream()
                .map(c -> c.getItemId()).collect(Collectors.toSet());
        long pending = todays.stream().filter(i -> !done.contains(i.getId())).count();
        if (pending == 0) return;                                      // all done — no nudge

        eventPublisher.publish("schedule.reminder.due",
                new ReminderEvent(s.getUserId(), today.toString(), (int) pending, zone.getId()));
        s.setLastRemindedOn(today);
        scheduleRepository.save(s);
    }

    private ZoneId parseZone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private Weekday toWeekday(DayOfWeek d) {
        return switch (d) {
            case SUNDAY -> Weekday.SUN;
            case MONDAY -> Weekday.MON;
            case TUESDAY -> Weekday.TUE;
            case WEDNESDAY -> Weekday.WED;
            case THURSDAY -> Weekday.THU;
            case FRIDAY -> Weekday.FRI;
            case SATURDAY -> Weekday.SAT;
        };
    }
}
