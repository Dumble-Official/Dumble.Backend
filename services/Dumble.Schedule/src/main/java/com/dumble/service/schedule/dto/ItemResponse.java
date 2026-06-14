package com.dumble.service.schedule.dto;

import com.dumble.service.schedule.domain.AuthorType;
import com.dumble.service.schedule.domain.ScheduleItem;
import com.dumble.service.schedule.domain.TableType;
import com.dumble.service.schedule.domain.Weekday;

import java.util.UUID;

/** Returned when a single item is created or edited. */
public record ItemResponse(
        UUID id,
        TableType tableType,
        Weekday weekday,
        int position,
        String content,
        String youtubeVideoId,
        AuthorType authorType,
        UUID authorId) {

    public static ItemResponse from(ScheduleItem i) {
        return new ItemResponse(i.getId(), i.getTableType(), i.getWeekday(), i.getPosition(),
                i.getContent(), i.getYoutubeVideoId(), i.getAuthorType(), i.getAuthorId());
    }
}
