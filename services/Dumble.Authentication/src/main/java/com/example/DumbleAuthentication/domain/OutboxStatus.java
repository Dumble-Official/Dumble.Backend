package com.example.DumbleAuthentication.domain;

public enum OutboxStatus {
    /** Persisted in the same transaction as the domain change; awaiting publish. */
    PENDING,
    /** Handed to the broker. */
    PUBLISHED,
    /** Permanently failed after exhausting the retry budget. */
    FAILED
}
