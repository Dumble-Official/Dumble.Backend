package com.example.DumbleSubscription.domain.enums;

/** Per Subscription PDF Decision 21.6 — fixed enum for precise gym-app UX. */
public enum EntryDenialReason {
    EXPIRED,
    NOT_STARTED,
    WRONG_GYM,
    SUSPENDED,
    BANNED,
    TOKEN_EXPIRED,
    TOKEN_USED,
    TOKEN_INVALID,
    NO_SUBSCRIPTION
}
