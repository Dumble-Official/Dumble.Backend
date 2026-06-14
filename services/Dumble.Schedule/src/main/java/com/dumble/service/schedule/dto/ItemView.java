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
        UUID authorId,
        boolean done) {

    public static ItemView from(ScheduleItem i, boolean done) {
        return new ItemView(i.getId(), i.getPosition(), i.getContent(),
                i.getYoutubeVideoId(), i.getAuthorType(), i.getAuthorId(), done);
    }
}
