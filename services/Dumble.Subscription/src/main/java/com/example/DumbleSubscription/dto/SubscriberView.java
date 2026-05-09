package com.example.DumbleSubscription.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Per Decision 10.2 — public profile + active-since, no PII. */
@Data
@Builder
public class SubscriberView {
    private UUID participantId;
    private String displayName;
    private String profileImageUrl;
    private String bundleName;
    private Instant activeSince;
    private Instant endsAt;
    /** True when the upstream user has been soft-deleted (Decision 10.4). */
    private boolean deleted;
    /** True when the participant has opted out of public listing (Decision 10.1). */
    private boolean hidden;
}
