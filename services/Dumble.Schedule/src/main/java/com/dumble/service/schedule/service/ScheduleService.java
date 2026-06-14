package com.dumble.service.schedule.service;

import com.dumble.service.schedule.domain.*;
import com.dumble.service.schedule.dto.*;
import com.dumble.service.schedule.exception.NotFoundException;
import com.dumble.service.schedule.repository.MealDayTargetRepository;
import com.dumble.service.schedule.repository.ScheduleItemRepository;
import com.dumble.service.schedule.repository.ScheduleRepository;
import com.dumble.service.schedule.util.YouTubeLinks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository itemRepository;
    private final MealDayTargetRepository targetRepository;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ScheduleItemRepository itemRepository,
                           MealDayTargetRepository targetRepository) {
        this.scheduleRepository = scheduleRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
    }

    /** The caller's full schedule (both 7-day tables). Creates an empty one on first access. */
    @Transactional
    public ScheduleResponse getMySchedule(UUID userId) {
        Schedule schedule = getOrCreate(userId);

        List<ScheduleItem> items =
                itemRepository.findByScheduleIdOrderByTableTypeAscWeekdayAscPositionAsc(schedule.getId());
        Map<Weekday, MealDayTarget> targets = targetRepository.findByScheduleId(schedule.getId()).stream()
                .collect(Collectors.toMap(MealDayTarget::getWeekday, t -> t));

        List<DayView> exercises = buildDays(items, TableType.EXERCISE, null);
        List<DayView> meals = buildDays(items, TableType.MEAL, targets);

        return new ScheduleResponse(schedule.getId(), schedule.getTimezone(), exercises, meals);
    }

    @Transactional
    public ItemResponse addItem(UUID userId, AddItemRequest req) {
        Schedule schedule = getOrCreate(userId);
        String videoId = YouTubeLinks.toVideoId(req.youtubeLink());

        ScheduleItem item = new ScheduleItem();
        item.setScheduleId(schedule.getId());
        item.setTableType(req.tableType());
        item.setWeekday(req.weekday());
        item.setPosition(itemRepository.countByScheduleIdAndTableTypeAndWeekday(
                schedule.getId(), req.tableType(), req.weekday()));
        item.setContent(req.content());
        item.setYoutubeVideoId(videoId);
        item.setAuthorType(AuthorType.CLIENT);
        item.setAuthorId(userId);
        return ItemResponse.from(itemRepository.save(item));
    }

    /** Clients edit their own schedule freely (any item on it, regardless of author). */
    @Transactional
    public ItemResponse editItem(UUID userId, UUID itemId, EditItemRequest req) {
        ScheduleItem item = ownedItemOrThrow(userId, itemId);
        item.setContent(req.content());
        item.setYoutubeVideoId(YouTubeLinks.toVideoId(req.youtubeLink()));
        return ItemResponse.from(itemRepository.save(item));
    }

    @Transactional
    public void deleteItem(UUID userId, UUID itemId) {
        ScheduleItem item = ownedItemOrThrow(userId, itemId);
        itemRepository.delete(item);
    }

    @Transactional
    public MealTargetView setMealTarget(UUID userId, Weekday weekday, MealTargetRequest req) {
        Schedule schedule = getOrCreate(userId);
        MealDayTarget target = targetRepository.findByScheduleIdAndWeekday(schedule.getId(), weekday)
                .orElseGet(() -> {
                    MealDayTarget t = new MealDayTarget();
                    t.setScheduleId(schedule.getId());
                    t.setWeekday(weekday);
                    return t;
                });
        target.setCalories(req.calories());
        target.setProteinG(req.proteinG());
        target.setCarbsG(req.carbsG());
        target.setFatG(req.fatG());
        return MealTargetView.from(targetRepository.save(target));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Schedule getOrCreate(UUID userId) {
        return scheduleRepository.findByUserId(userId).orElseGet(() -> {
            Schedule s = new Schedule();
            s.setUserId(userId);
            return scheduleRepository.save(s);
        });
    }

    /** Load an item only if it belongs to the caller's own schedule (anti-IDOR). */
    private ScheduleItem ownedItemOrThrow(UUID userId, UUID itemId) {
        Schedule schedule = scheduleRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
        ScheduleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
        if (!item.getScheduleId().equals(schedule.getId())) {
            throw new NotFoundException("Item not found: " + itemId);
        }
        return item;
    }

    private List<DayView> buildDays(List<ScheduleItem> all, TableType type, Map<Weekday, MealDayTarget> targets) {
        return java.util.Arrays.stream(Weekday.values())
                .map(day -> {
                    List<ItemView> items = all.stream()
                            .filter(i -> i.getTableType() == type && i.getWeekday() == day)
                            .sorted(java.util.Comparator.comparingInt(ScheduleItem::getPosition))
                            .map(ItemView::from)
                            .toList();
                    MealTargetView target = (targets == null) ? null : MealTargetView.from(targets.get(day));
                    return new DayView(day, items, target);
                })
                .toList();
    }
}
