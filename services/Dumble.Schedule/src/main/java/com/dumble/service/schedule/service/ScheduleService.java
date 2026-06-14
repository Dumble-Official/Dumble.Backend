package com.dumble.service.schedule.service;

import com.dumble.service.schedule.domain.*;
import com.dumble.service.schedule.dto.*;
import com.dumble.service.schedule.exception.BadRequestException;
import com.dumble.service.schedule.exception.ForbiddenException;
import com.dumble.service.schedule.exception.NotFoundException;
import com.dumble.service.schedule.repository.*;
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
    private final TrainerClientLinkRepository linkRepository;
    private final ContactLeakFilter contactLeakFilter;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ScheduleItemRepository itemRepository,
                           MealDayTargetRepository targetRepository,
                           ItemCompletionRepository completionRepository,
                           TrainerClientLinkRepository linkRepository,
                           ContactLeakFilter contactLeakFilter) {
        this.scheduleRepository = scheduleRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.completionRepository = completionRepository;
        this.linkRepository = linkRepository;
        this.contactLeakFilter = contactLeakFilter;
    }

    // ── Client (owner) side ───────────────────────────────────────────────

    @Transactional
    public ScheduleResponse getMySchedule(UUID userId, String author, LocalDate date) {
        Schedule schedule = getOrCreate(userId);
        List<ScheduleItem> items = items(schedule.getId()).stream()
                .filter(authorFilter(author, userId))
                .toList();
        return assemble(schedule, items, date);
    }

    @Transactional
    public ItemResponse addItem(UUID userId, AddItemRequest req) {
        Schedule schedule = getOrCreate(userId);
        return ItemResponse.from(itemRepository.save(
                newItem(schedule.getId(), req.tableType(), req.weekday(), req.content(),
                        req.youtubeLink(), AuthorType.CLIENT, userId)));
    }

    /** Clients edit their own schedule freely (any item on it, regardless of author). */
    @Transactional
    public ItemResponse editItem(UUID userId, UUID itemId, EditItemRequest req) {
        ScheduleItem item = ownedItemOrThrow(userId, itemId);
        return applyEdit(item, req);
    }

    @Transactional
    public void deleteItem(UUID userId, UUID itemId) {
        itemRepository.delete(ownedItemOrThrow(userId, itemId));
    }

    @Transactional
    public CompletionView setCompletion(UUID userId, UUID itemId, LocalDate date, boolean done) {
        ownedItemOrThrow(userId, itemId);
        return applyCompletion(userId, itemId, date, done);
    }

    @Transactional
    public MealTargetView setMealTarget(UUID userId, Weekday weekday, MealTargetRequest req) {
        return upsertTarget(getOrCreate(userId).getId(), weekday, req);
    }

    // ── Trainer side (gated by an active link) ─────────────────────────────

    /** A trainer reads a client's schedule: only their own items + the client's own items. */
    @Transactional
    public ScheduleResponse getClientSchedule(UUID trainerId, UUID clientId, LocalDate date) {
        requireActiveLink(trainerId, clientId);
        Schedule schedule = getOrCreate(clientId);
        List<ScheduleItem> items = items(schedule.getId()).stream()
                .filter(coachVisible(trainerId))
                .toList();
        return assemble(schedule, items, date);
    }

    @Transactional
    public ItemResponse addItemForClient(UUID trainerId, UUID clientId, AddItemRequest req) {
        requireActiveLink(trainerId, clientId);
        contactLeakFilter.check(req.content());
        Schedule schedule = getOrCreate(clientId);
        return ItemResponse.from(itemRepository.save(
                newItem(schedule.getId(), req.tableType(), req.weekday(), req.content(),
                        req.youtubeLink(), AuthorType.TRAINER, trainerId)));
    }

    /** A trainer may only edit items they authored on that client (not the client's, not another coach's). */
    @Transactional
    public ItemResponse editItemForClient(UUID trainerId, UUID clientId, UUID itemId, EditItemRequest req) {
        ScheduleItem item = coachOwnedItemOrThrow(trainerId, clientId, itemId);
        contactLeakFilter.check(req.content());
        return applyEdit(item, req);
    }

    @Transactional
    public void deleteItemForClient(UUID trainerId, UUID clientId, UUID itemId) {
        itemRepository.delete(coachOwnedItemOrThrow(trainerId, clientId, itemId));
    }

    @Transactional
    public MealTargetView setMealTargetForClient(UUID trainerId, UUID clientId, Weekday weekday, MealTargetRequest req) {
        requireActiveLink(trainerId, clientId);
        return upsertTarget(getOrCreate(clientId).getId(), weekday, req);
    }

    // ── Chatbot side (internal; pro-gating enforced upstream in FitCoach) ──

    /**
     * The chatbot writes generated items for a pro client. Stamped CHATBOT, not
     * contact-filtered (platform-generated). replace=true clears the chatbot's
     * own prior items first; trainer and client items are never touched.
     */
    @Transactional
    public List<ItemResponse> applyChatbotItems(UUID clientId, boolean replace, List<AddItemRequest> items) {
        Schedule schedule = getOrCreate(clientId);
        if (replace) {
            itemRepository.deleteByScheduleAndAuthor(schedule.getId(), AuthorType.CHATBOT);
        }
        List<ItemResponse> created = new ArrayList<>();
        for (AddItemRequest req : items) {
            created.add(ItemResponse.from(itemRepository.save(
                    newItem(schedule.getId(), req.tableType(), req.weekday(), req.content(),
                            req.youtubeLink(), AuthorType.CHATBOT, null))));
        }
        return created;
    }

    // ── Internal: trainer↔client link read-model ───────────────────────────

    @Transactional
    public void upsertTrainerLink(UUID trainerId, UUID clientId, boolean active) {
        TrainerClientLink link = linkRepository.findByTrainerIdAndClientId(trainerId, clientId)
                .orElseGet(() -> {
                    TrainerClientLink l = new TrainerClientLink();
                    l.setTrainerId(trainerId);
                    l.setClientId(clientId);
                    return l;
                });
        link.setActive(active);
        linkRepository.save(link);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void requireActiveLink(UUID trainerId, UUID clientId) {
        if (!linkRepository.existsByTrainerIdAndClientIdAndActiveTrue(trainerId, clientId)) {
            throw new ForbiddenException("No active subscription with this client");
        }
    }

    private List<ScheduleItem> items(UUID scheduleId) {
        return itemRepository.findByScheduleIdOrderByTableTypeAscWeekdayAscPositionAsc(scheduleId);
    }

    private ScheduleItem newItem(UUID scheduleId, TableType type, Weekday day, String content,
                                 String youtubeLink, AuthorType authorType, UUID authorId) {
        ScheduleItem item = new ScheduleItem();
        item.setScheduleId(scheduleId);
        item.setTableType(type);
        item.setWeekday(day);
        item.setPosition(itemRepository.nextPosition(scheduleId, type, day));
        item.setContent(content);
        item.setYoutubeVideoId(YouTubeLinks.toVideoId(youtubeLink));
        item.setAuthorType(authorType);
        item.setAuthorId(authorId);
        return item;
    }

    private ItemResponse applyEdit(ScheduleItem item, EditItemRequest req) {
        item.setContent(req.content());
        item.setYoutubeVideoId(YouTubeLinks.toVideoId(req.youtubeLink()));
        return ItemResponse.from(itemRepository.save(item));
    }

    private CompletionView applyCompletion(UUID userId, UUID itemId, LocalDate date, boolean done) {
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

    private MealTargetView upsertTarget(UUID scheduleId, Weekday weekday, MealTargetRequest req) {
        MealDayTarget target = targetRepository.findByScheduleIdAndWeekday(scheduleId, weekday)
                .orElseGet(() -> {
                    MealDayTarget t = new MealDayTarget();
                    t.setScheduleId(scheduleId);
                    t.setWeekday(weekday);
                    return t;
                });
        target.setCalories(req.calories());
        target.setProteinG(req.proteinG());
        target.setCarbsG(req.carbsG());
        target.setFatG(req.fatG());
        return MealTargetView.from(targetRepository.save(target));
    }

    private Schedule getOrCreate(UUID userId) {
        return scheduleRepository.findByUserId(userId).orElseGet(() -> {
            Schedule s = new Schedule();
            s.setUserId(userId);
            return scheduleRepository.save(s);
        });
    }

    /** An item on the caller's OWN schedule (any author). */
    private ScheduleItem ownedItemOrThrow(UUID userId, UUID itemId) {
        Schedule schedule = scheduleRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
        return itemOnScheduleOrThrow(itemId, schedule.getId());
    }

    /** A TRAINER item the trainer authored on the given client's schedule (active link required). */
    private ScheduleItem coachOwnedItemOrThrow(UUID trainerId, UUID clientId, UUID itemId) {
        requireActiveLink(trainerId, clientId);
        Schedule schedule = getOrCreate(clientId);
        ScheduleItem item = itemOnScheduleOrThrow(itemId, schedule.getId());
        if (item.getAuthorType() != AuthorType.TRAINER || !trainerId.equals(item.getAuthorId())) {
            throw new NotFoundException("Item not found: " + itemId);
        }
        return item;
    }

    private ScheduleItem itemOnScheduleOrThrow(UUID itemId, UUID scheduleId) {
        ScheduleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
        if (!item.getScheduleId().equals(scheduleId)) {
            throw new NotFoundException("Item not found: " + itemId);
        }
        return item;
    }

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

    /** A coach sees only their own items + the client's own items — never another coach's or the chatbot's. */
    private Predicate<ScheduleItem> coachVisible(UUID trainerId) {
        return i -> i.getAuthorType() == AuthorType.CLIENT
                || (i.getAuthorType() == AuthorType.TRAINER && trainerId.equals(i.getAuthorId()));
    }

    private ScheduleResponse assemble(Schedule schedule, List<ScheduleItem> items, LocalDate date) {
        LocalDate on = (date != null) ? date : LocalDate.now(ZoneOffset.UTC);
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

    private List<DayView> buildDays(List<ScheduleItem> all, TableType type,
                                    Map<Weekday, MealDayTarget> targets, Set<UUID> doneIds) {
        return Arrays.stream(Weekday.values())
                .map(day -> {
                    List<ItemView> views = all.stream()
                            .filter(i -> i.getTableType() == type && i.getWeekday() == day)
                            .sorted(Comparator.comparingInt(ScheduleItem::getPosition))
                            .map(i -> ItemView.from(i, doneIds.contains(i.getId())))
                            .toList();
                    MealTargetView target = (targets == null) ? null : MealTargetView.from(targets.get(day));
                    return new DayView(day, views, target);
                })
                .toList();
    }
}
