package com.dumble.service.schedule.dto;

import jakarta.validation.constraints.NotBlank;

/** Edit an item's text and/or its YouTube video (null youtubeLink clears it). */
public record EditItemRequest(
        @NotBlank(message = "is required") String content,
        String youtubeLink) {
}
