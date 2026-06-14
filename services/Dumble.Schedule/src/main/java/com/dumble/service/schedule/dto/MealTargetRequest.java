package com.dumble.service.schedule.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** Per-day meal target. Any field may be null (unset); negatives rejected. */
public record MealTargetRequest(
        @PositiveOrZero(message = "must be >= 0") Integer calories,
        @PositiveOrZero(message = "must be >= 0") Integer proteinG,
        @PositiveOrZero(message = "must be >= 0") Integer carbsG,
        @PositiveOrZero(message = "must be >= 0") Integer fatG) {
}
