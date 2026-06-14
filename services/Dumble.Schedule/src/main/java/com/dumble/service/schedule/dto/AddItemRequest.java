package com.dumble.service.schedule.dto;

import com.dumble.service.schedule.domain.TableType;
import com.dumble.service.schedule.domain.Weekday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Add one item to a day's list. youtubeLink is optional (a YouTube link or id). */
public record AddItemRequest(
        @NotNull(message = "is required") TableType tableType,
        @NotNull(message = "is required") Weekday weekday,
        @NotBlank(message = "is required") String content,
        String youtubeLink) {
}
