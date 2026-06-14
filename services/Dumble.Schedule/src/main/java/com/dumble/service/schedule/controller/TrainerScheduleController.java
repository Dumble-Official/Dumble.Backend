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
 * Trainer-facing authoring of a client's schedule. Every operation requires an
 * active coaching link with that client (else 403), items are stamped TRAINER,
 * and a coach only ever sees/edits their own items plus the client's own —
 * never another coach's.
 */
@RestController
@RequestMapping("/schedule/clients/{clientId}")
public class TrainerScheduleController {

    private final ScheduleService scheduleService;

    public TrainerScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ScheduleResponse getClientSchedule(
            @PathVariable UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduleService.getClientSchedule(me().userId(), clientId, date);
    }

    @PostMapping("/items")
    public ResponseEntity<ItemResponse> addItem(@PathVariable UUID clientId, @Valid @RequestBody AddItemRequest req) {
        return new ResponseEntity<>(scheduleService.addItemForClient(me().userId(), clientId, req), HttpStatus.CREATED);
    }

    @PatchMapping("/items/{itemId}")
    public ItemResponse editItem(@PathVariable UUID clientId, @PathVariable UUID itemId,
                                 @Valid @RequestBody EditItemRequest req) {
        return scheduleService.editItemForClient(me().userId(), clientId, itemId, req);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID clientId, @PathVariable UUID itemId) {
        scheduleService.deleteItemForClient(me().userId(), clientId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/meal-targets/{weekday}")
    public MealTargetView setMealTarget(@PathVariable UUID clientId, @PathVariable Weekday weekday,
                                        @Valid @RequestBody MealTargetRequest req) {
        return scheduleService.setMealTargetForClient(me().userId(), clientId, weekday, req);
    }

    private AuthPrincipal me() {
        return CurrentUser.require();
    }
}
