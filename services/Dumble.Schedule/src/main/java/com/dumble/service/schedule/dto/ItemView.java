package com.dumble.service.schedule.dto;

import com.dumble.service.schedule.domain.AuthorType;
import com.dumble.service.schedule.domain.ScheduleItem;

import java.util.UUID;

public record ItemView(
        UUID id,
        int position,
        String content,
        String youtubeVideoId,
        AuthorType authorType,
        UUID authorId) {

    public static ItemView from(ScheduleItem i) {
        return new ItemView(i.getId(), i.getPosition(), i.getContent(),
                i.getYoutubeVideoId(), i.getAuthorType(), i.getAuthorId());
    }
}
