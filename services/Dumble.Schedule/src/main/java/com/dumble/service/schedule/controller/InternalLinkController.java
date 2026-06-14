package com.dumble.service.schedule.controller;

import com.dumble.service.schedule.dto.UpsertLinkRequest;
import com.dumble.service.schedule.security.InternalSecret;
import com.dumble.service.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service ingestion of the trainer↔client access read-model. Driven
 * by the Subscription service (bundle-subscription activated/cancelled/expired).
 * Gated by the shared X-Internal-Secret, never exposed through the gateway.
 */
@RestController
@RequestMapping("/internal/trainer-links")
public class InternalLinkController {

    private final ScheduleService scheduleService;
    private final InternalSecret internalSecret;

    public InternalLinkController(ScheduleService scheduleService, InternalSecret internalSecret) {
        this.scheduleService = scheduleService;
        this.internalSecret = internalSecret;
    }

    @PostMapping
    public ResponseEntity<Void> upsert(@RequestHeader(value = "X-Internal-Secret", required = false) String secret,
                                       @Valid @RequestBody UpsertLinkRequest req) {
        internalSecret.require(secret);
        scheduleService.upsertTrainerLink(req.trainerId(), req.clientId(), req.active());
        return ResponseEntity.noContent().build();
    }
}
