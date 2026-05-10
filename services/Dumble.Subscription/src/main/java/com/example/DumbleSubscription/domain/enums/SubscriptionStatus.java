package com.example.DumbleSubscription.domain.enums;

public enum SubscriptionStatus {
    PENDING,    // checkout completed but Payment hasn't confirmed yet
    ACTIVE,
    PAST_DUE,   // renewal charge failed; dunning retries pending (Decision 7.3)
    CANCELLED,  // scheduled for end-of-period cancellation
    EXPIRED,    // ran past its end date or dunning exhausted
    REFUNDED    // forced cancel via ban refund
}
