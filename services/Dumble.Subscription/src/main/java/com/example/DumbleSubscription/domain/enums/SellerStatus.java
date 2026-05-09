package com.example.DumbleSubscription.domain.enums;

/**
 * Per Subscription PDF Sections 16, 17, 18, 19. Drives every "is this seller
 * accepting subscriptions / earning payouts" decision.
 */
public enum SellerStatus {
    ACTIVE,
    FROZEN,                  // 7-day review window per Decision 16.2
    WINDING_DOWN,            // voluntary leave with active subs (Section 17)
    BANNED,                  // forfeited, escrow refunded
    CLOSED_ESCROW_PENDING,   // self-deactivated but cohort payouts still draining (Decision 19.2)
    CLOSED                   // fully drained — final state
}
