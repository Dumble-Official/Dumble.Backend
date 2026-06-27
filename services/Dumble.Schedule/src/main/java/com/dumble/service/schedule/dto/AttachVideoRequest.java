package com.dumble.service.schedule.dto;

import com.dumble.service.schedule.domain.TableType;
import com.dumble.service.schedule.domain.Weekday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Internal: the chatbot attaches a YouTube video it found to an existing CHATBOT
 * item. The chatbot has no item ids, so the item is located by weekday + a
 * case-insensitive content substring. tableType is optional — null searches both
 * the exercise and meal lists.
 */
public record AttachVideoRequest(
        TableType tableType,
        @NotNull(message = "is required") Weekday weekday,
        @NotBlank(message = "is required") String contentQuery,
        @NotBlank(message = "is required") String youtubeLink) {
}
