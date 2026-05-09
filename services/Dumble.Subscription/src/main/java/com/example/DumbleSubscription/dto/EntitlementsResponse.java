package com.example.DumbleSubscription.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Returned by {@code GET /me/entitlements}. Consumed by ChatService and any
 * other service that gates features on the user's tier. Single source of truth
 * for "what is this user allowed to do."
 *
 * Per Subscription PDF Decision 3.1 — only two plans (FREE, PRO) and the unlocks
 * are uniform across audiences.
 */
@Data
@Builder
public class EntitlementsResponse {
    private String planCode;                // "FREE" | "PRO"
    private Instant expiresAt;              // null when FREE
    private boolean canUseChatbot;
    private Integer chatbotMessagesPerDay;  // 0 for FREE; null = unlimited for PRO
    private boolean canDmAnyone;
}
