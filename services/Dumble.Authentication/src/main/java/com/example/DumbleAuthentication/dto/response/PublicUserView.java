package com.example.DumbleAuthentication.dto.response;

import java.util.UUID;

/**
 * Minimal, non-PII identity used by other services (e.g. Subscription's
 * subscriber-enrichment) to render a user. Served from GET
 * /api/users/{id}/public-profile. Shape matches the callers' UserPublicProfile
 * DTO: id, displayName, profileImageUrl, deleted.
 */
public record PublicUserView(
        UUID id,
        String displayName,
        String profileImageUrl,
        boolean deleted) {
}
