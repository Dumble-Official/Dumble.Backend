package com.example.DumbleSubscription.domain.enums;

/**
 * Per Subscription PDF Decision 1.2, PlatformSubscription is keyed by UserId
 * alone (audience-agnostic). This enum is kept for context — sourced from
 * Authentication's user record — but does not gate which plans a user can buy.
 */
public enum Audience {
    PARTICIPANT,
    TRAINER,
    GYM_OWNER,
    GYM,
    ADMIN
}
