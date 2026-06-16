package com.dumble.service.schedule.controller;

import com.dumble.service.schedule.domain.Weekday;
import com.dumble.service.schedule.dto.*;
import com.dumble.service.schedule.security.AuthPrincipal;
import com.dumble.service.schedule.security.CurrentUser;
import com.dumble.service.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The client's own schedule. Every endpoint is scoped to the authenticated
 * caller (the JWT subject) — there is no path for another user's schedule, and
 * item operations only touch items on the caller's own schedule.
 */
@RestController
@RequestMapping("/schedule/me")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ScheduleResponse getMySchedule(
            @RequestParam(required = false) String author,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduleService.getMySchedule(me().userId(), author, date);
    }

    @PostMapping("/items")
    public ResponseEntity<ItemResponse> addItem(@Valid @RequestBody AddItemRequest req) {
        return new ResponseEntity<>(scheduleService.addItem(me().userId(), req), HttpStatus.CREATED);
    }

    @PatchMapping("/items/{itemId}")
    public ItemResponse editItem(@PathVariable UUID itemId, @Valid @RequestBody EditItemRequest req) {
        return scheduleService.editItem(me().userId(), itemId, req);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID itemId) {
        scheduleService.deleteItem(me().userId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/items/{itemId}/completion")
    public CompletionView setCompletion(@PathVariable UUID itemId, @Valid @RequestBody SetCompletionRequest req) {
        return scheduleService.setCompletion(me().userId(), itemId, req.date(), req.done());
    }

    @PutMapping("/meal-targets/{weekday}")
    public MealTargetView setMealTarget(@PathVariable Weekday weekday, @Valid @RequestBody MealTargetRequest req) {
        return scheduleService.setMealTarget(me().userId(), weekday, req);
    }

    @PutMapping("/timezone")
    public ResponseEntity<Void> setTimezone(@Valid @RequestBody SetTimezoneRequest req) {
        scheduleService.setTimezone(me().userId(), req.timezone());
        return ResponseEntity.noContent().build();
    }

    private AuthPrincipal me() {
        return CurrentUser.require();
    }
}
