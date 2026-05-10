package com.example.DumbleSubscription.client.dto;

import lombok.Data;

import java.util.UUID;

/** Per Decision 10.2 — only the non-PII fields. No email, no phone. */
@Data
public class UserPublicProfile {
    private UUID id;
    private String displayName;
    private String profileImageUrl;
    private boolean isDeleted;
}
