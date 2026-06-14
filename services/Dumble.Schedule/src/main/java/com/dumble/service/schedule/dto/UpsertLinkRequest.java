package com.dumble.service.schedule.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Internal: set a trainer↔client coaching link active/inactive (fed by Subscription). */
public record UpsertLinkRequest(
        @NotNull(message = "is required") UUID trainerId,
        @NotNull(message = "is required") UUID clientId,
        @NotNull(message = "is required") Boolean active) {
}
