package com.dumble.service.schedule.service;

import com.dumble.service.schedule.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Anti-disintermediation: trainer-authored free text must not carry a way to
 * pull the client off-platform. Scans for phone numbers, emails, social handles,
 * WhatsApp/Telegram, external links, and "contact me" lures, and rejects with
 * what to remove. Applied to TRAINER writes only — clients writing their own
 * schedule and the chatbot's generated content are not contact-filtered.
 *
 * Tuned to avoid false positives on training/nutrition text (rep schemes like
 * "3x8, 12, 15", gram amounts like "200g", vitamins like "B12"): a phone needs
 * a +country prefix, an Egyptian-mobile shape, a grouped xxx-xxx-xxxx shape, or
 * a 10+ digit run — none of which normal exercise/meal text produces.
 */
@Component
public class ContactLeakFilter {

    private static final Pattern EMAIL =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

    // External link: a scheme/www, or a bare domain with a known TLD (catches wa.me, t.me, instagram.com…).
    private static final Pattern URL = Pattern.compile(
            "(?i)(https?://|www\\.)\\S+"
            + "|\\b[\\w-]+\\.(com|net|org|io|me|app|link|co|gg|tv|info|biz|xyz|live|page|site|online|to)\\b");

    private static final Pattern PHONE_INTL = Pattern.compile("\\+\\d[\\d\\s().-]{6,}\\d");
    private static final Pattern PHONE_LOCAL = Pattern.compile("\\b0\\d{2}[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}\\b");
    private static final Pattern PHONE_GROUPED = Pattern.compile("\\b\\d{3}[\\s.-]\\d{3,4}[\\s.-]\\d{4}\\b");
    private static final Pattern PHONE_RUN = Pattern.compile("\\d{10,}");

    // Bare social handle: @name (not part of an email — emails are caught above).
    private static final Pattern HANDLE = Pattern.compile("(?<![\\w@])@[A-Za-z][\\w.]{2,}");

    private static final Pattern OFF_PLATFORM = Pattern.compile(
            "(?i)\\b(whats?app|wa\\.me|telegram|t\\.me|instagram|insta|tiktok|snapchat|snap|messenger|viber|signal"
            + "|dm me|contact me|call me|text me|reach me|message me|find me|my number|my whats?app|my channel|my page|follow me)\\b");

    /** Throw if the trainer text carries any off-platform contact info. */
    public void check(String text) {
        if (text == null || text.isBlank()) return;
        Set<String> reasons = new LinkedHashSet<>();
        if (EMAIL.matcher(text).find()) reasons.add("an email address");
        if (PHONE_INTL.matcher(text).find() || PHONE_LOCAL.matcher(text).find()
                || PHONE_GROUPED.matcher(text).find() || PHONE_RUN.matcher(text).find()) reasons.add("a phone number");
        if (URL.matcher(text).find()) reasons.add("an external link");
        if (HANDLE.matcher(text).find()) reasons.add("a social handle");
        if (OFF_PLATFORM.matcher(text).find()) reasons.add("off-platform contact (e.g. WhatsApp/social)");

        if (!reasons.isEmpty()) {
            throw new BadRequestException(
                    "Remove " + String.join(", ", reasons)
                            + " — clients must stay in the app. Use the video field for a YouTube link.");
        }
    }
}
