package com.dumble.service.schedule.service;

import com.dumble.service.schedule.domain.*;
import com.dumble.service.schedule.dto.*;
import com.dumble.service.schedule.exception.BadRequestException;
import com.dumble.service.schedule.exception.NotFoundException;
import com.dumble.service.schedule.repository.ItemCompletionRepository;
import com.dumble.service.schedule.repository.MealDayTargetRepository;
import com.dumble.service.schedule.repository.ScheduleItemRepository;
import com.dumble.service.schedule.repository.ScheduleRepository;
import com.dumble.service.schedule.util.YouTubeLinks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository itemRepository;
    private final MealDayTargetRepository targetRepository;
    private final ItemCompletionRepository completionRepository;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ScheduleItemRepository itemRepository,
                           MealDayTargetRepository targetRepository,
                           ItemCompletionRepository completionRepository) {
        this.scheduleRepository = scheduleRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.completionRepository = completionRepository;
    }

    /**
     * The caller's full schedule. {@code author} optionally filters items
     * (all | me | chatbot | a coach uuid); {@code done} flags are computed for
     * {@code date} (defaults to today, UTC for now).
     */
    @Transactional
    public ScheduleResponse getMySchedule(UUID userId, String author, LocalDate date) {
        Schedule schedule = getOrCreate(userId);
        LocalDate on = (date != null) ? date : LocalDate.now(ZoneOffset.UTC);
        Predicate<ScheduleItem> authorFilter = authorFilter(author, userId);

        List<ScheduleItem> items =
                itemRepository.findByScheduleIdOrderByTableTypeAscWeekdayAscPositionAsc(schedule.getId()).stream()
                        .filter(authorFilter)
                        .toList();

        Set<UUID> doneIds = items.isEmpty() ? Set.of()
                : completionRepository.findByItemIdInAndCompletedOn(
                        items.stream().map(ScheduleItem::getId).toList(), on).stream()
                .map(ItemCompletion::getItemId).collect(Collectors.toSet());

        Map<Weekday, MealDayTarget> targets = targetRepository.findByScheduleId(schedule.getId()).stream()
                .collect(Collectors.toMap(MealDayTarget::getWeekday, t -> t));

        List<DayView> exercises = buildDays(items, TableType.EXERCISE, null, doneIds);
        List<DayView> meals = buildDays(items, TableType.MEAL, targets, doneIds);

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

    /** Mark an item done / not done for a date (defaults to today). */
    @Transactional
    public CompletionView setCompletion(UUID userId, UUID itemId, LocalDate date, boolean done) {
        ownedItemOrThrow(userId, itemId);
        LocalDate on = (date != null) ? date : LocalDate.now(ZoneOffset.UTC);
        if (done) {
            if (!completionRepository.existsByItemIdAndCompletedOn(itemId, on)) {
                ItemCompletion c = new ItemCompletion();
                c.setItemId(itemId);
                c.setUserId(userId);
                c.setCompletedOn(on);
                completionRepository.save(c);
            }
        } else {
            completionRepository.deleteForDate(itemId, on);
        }
        return new CompletionView(itemId, on, done);
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

    /** all|null → everything; me → caller's CLIENT items; chatbot → CHATBOT; uuid → that coach's TRAINER items. */
    private Predicate<ScheduleItem> authorFilter(String author, UUID userId) {
        if (author == null || author.isBlank() || author.equalsIgnoreCase("all")) {
            return i -> true;
        }
        if (author.equalsIgnoreCase("me")) {
            return i -> i.getAuthorType() == AuthorType.CLIENT && userId.equals(i.getAuthorId());
        }
        if (author.equalsIgnoreCase("chatbot")) {
            return i -> i.getAuthorType() == AuthorType.CHATBOT;
        }
        final UUID coachId;
        try {
            coachId = UUID.fromString(author);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid author filter: " + author);
        }
        return i -> i.getAuthorType() == AuthorType.TRAINER && coachId.equals(i.getAuthorId());
    }

    private List<DayView> buildDays(List<ScheduleItem> all, TableType type,
                                    Map<Weekday, MealDayTarget> targets, Set<UUID> doneIds) {
        return Arrays.stream(Weekday.values())
                .map(day -> {
                    List<ItemView> items = all.stream()
                            .filter(i -> i.getTableType() == type && i.getWeekday() == day)
                            .sorted(Comparator.comparingInt(ScheduleItem::getPosition))
                            .map(i -> ItemView.from(i, doneIds.contains(i.getId())))
                            .toList();
                    MealTargetView target = (targets == null) ? null : MealTargetView.from(targets.get(day));
                    return new DayView(day, items, target);
                })
                .toList();
    }
}
