package com.dumble.service.schedule.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Mark an item done (or not) for a given date; date defaults to today if null. */
public record SetCompletionRequest(
        LocalDate date,
        @NotNull(message = "is required") Boolean done) {
}
