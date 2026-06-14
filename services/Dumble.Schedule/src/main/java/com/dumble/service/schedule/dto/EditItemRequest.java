package com.dumble.service.schedule.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Edit an item's text and/or its video.
 * <ul>
 *   <li>{@code youtubeLink} present → set the video to it.</li>
 *   <li>{@code youtubeLink} omitted/blank and {@code clearYoutube=false} → keep the existing video (edit content only).</li>
 *   <li>{@code clearYoutube=true} → remove the video.</li>
 * </ul>
 * This distinguishes "edit content only" from "remove the video", which a plain
 * nullable field cannot (Jackson maps both absent and null to null).
 */
public record EditItemRequest(
        @NotBlank(message = "is required") String content,
        String youtubeLink,
        boolean clearYoutube) {
}
