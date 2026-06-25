package com.dumble.service.schedule.controller;

import com.dumble.service.schedule.dto.ChatbotApplyRequest;
import com.dumble.service.schedule.dto.ItemResponse;
import com.dumble.service.schedule.dto.ScheduleResponse;
import com.dumble.service.schedule.security.InternalSecret;
import com.dumble.service.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service entry for the FitCoach chatbot to write a pro client's
 * schedule. Gated by the shared X-Internal-Secret (never exposed via the
 * gateway). The pro-tier check is enforced upstream by FitCoach/gateway; here we
 * trust the internal caller and stamp the items CHATBOT.
 */
@RestController
@RequestMapping("/internal/clients/{clientId}/chatbot")
public class InternalChatbotController {

    private final ScheduleService scheduleService;
    private final InternalSecret internalSecret;

    public InternalChatbotController(ScheduleService scheduleService, InternalSecret internalSecret) {
        this.scheduleService = scheduleService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/items")
    public List<ItemResponse> apply(@RequestHeader(value = "X-Internal-Secret", required = false) String secret,
                                    @PathVariable UUID clientId,
                                    @Valid @RequestBody ChatbotApplyRequest req) {
        internalSecret.require(secret);
        return scheduleService.applyChatbotItems(clientId, req.replace(), req.items());
    }

    /** Read-only: lets the FitCoach read a client's current schedule (e.g. to answer
     *  "what do I have today") without regenerating it. */
    @GetMapping("/items")
    public ScheduleResponse read(@RequestHeader(value = "X-Internal-Secret", required = false) String secret,
                                 @PathVariable UUID clientId) {
        internalSecret.require(secret);
        return scheduleService.getMySchedule(clientId, null, null);
    }
}
