package com.example.DumbleSubscription.domain.enums;

import java.util.Set;

public enum SubscriptionStatus {
    PENDING,    // checkout completed but Payment hasn't confirmed yet
    ACTIVE,
    PAST_DUE,   // renewal charge failed; dunning retries pending (Decision 7.3)
    CANCELLED,  // cancel-at-period-end: still entitled until endsAt, then EXPIRED (bundle subs)
    EXPIRED,    // ran past its end date or dunning exhausted
    REFUNDED;   // forced cancel via ban refund

    /** Statuses in which a bundle subscriber still has access (paid through the
     *  current period): ACTIVE, or CANCELLED (cancel-at-period-end). */
    public static final Set<SubscriptionStatus> ENTITLED = Set.of(ACTIVE, CANCELLED);

    public boolean isEntitled() {
        return this == ACTIVE || this == CANCELLED;
    }
}
