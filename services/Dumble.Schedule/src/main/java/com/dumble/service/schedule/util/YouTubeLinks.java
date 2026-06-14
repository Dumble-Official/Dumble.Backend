package com.dumble.service.schedule.util;

import com.dumble.service.schedule.exception.BadRequestException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only YouTube links are accepted (played embedded in-app). We store the video
 * id, not the raw link — so no external page/description/channel is reachable
 * beyond the embed, and any non-YouTube link is rejected.
 */
public final class YouTubeLinks {

    private YouTubeLinks() {}

    // 11-char YouTube video id.
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    // youtu.be/<id>, youtube.com/watch?v=<id>, /embed/<id>, /shorts/<id>, /v/<id>
    private static final Pattern URL = Pattern.compile(
            "^(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|v/)|youtu\\.be/)"
                    + "([A-Za-z0-9_-]{11})(?:[?&#].*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Parse a YouTube link (or a bare 11-char id) to its video id.
     * @return the video id, or null if the input is null/blank.
     * @throws BadRequestException if a non-null value isn't a valid YouTube link/id.
     */
    public static String toVideoId(String linkOrId) {
        if (linkOrId == null) return null;
        String v = linkOrId.trim();
        if (v.isEmpty()) return null;

        if (ID.matcher(v).matches()) return v;

        Matcher m = URL.matcher(v);
        if (m.matches()) return m.group(1);

        throw new BadRequestException("Only YouTube links are allowed for the video field");
    }
}
