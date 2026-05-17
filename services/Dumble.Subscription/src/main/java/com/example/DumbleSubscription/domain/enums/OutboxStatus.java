package com.example.DumbleSubscription.domain.enums;

public enum OutboxStatus {
    /** Persisted in the same tx as the domain change; awaiting publish. */
    PENDING,
    /**
     * Drain has handed the message to the AMQP client and is awaiting the
     * broker's publisher confirm. A separate recovery sweep resets stuck
     * IN_FLIGHT rows back to PENDING after a grace window so a lost confirm
     * (broker connection died, process crashed mid-confirm) doesn't leak.
     */
    IN_FLIGHT,
    /** Broker acknowledged. */
    PUBLISHED,
    /** Permanently failed (broker nack after retry budget, unroutable, etc.) */
    FAILED
}
