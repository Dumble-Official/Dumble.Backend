package com.example.DumbleSubscription.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Cohort identity = the calendar week a subscription started in. Used by
 * weekly cohort batching (PDF Decision 5.1). Format: "YYYY-Www" (ISO week).
 */
public final class CohortKey {

    private CohortKey() {}

    public static String fromInstant(Instant instant) {
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        return String.format(Locale.ROOT, "%d-W%02d", weekYear, week);
    }
}
