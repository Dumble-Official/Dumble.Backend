-- ============================================================================
-- V6 — addresses ultrareview-pr4 run-2 findings:
--   * Extend the active-sub partial unique index to also cover PENDING so
--     a fresh client-supplied Idempotency-Key for the same logical purchase
--     can no longer create a second PENDING row pointing at the same
--     providerRef (bug_029-run2).
--   * Add inbound_listener_events for AMQP idempotency — the @RabbitListener
--     path on subscription.inbound currently has no equivalent of
--     webhook_events_inbound, which lets redelivered chargeback messages
--     re-enter the partial-chargeback branch and lock another tranche each
--     time (merged_bug_011, bug 2).
-- ============================================================================

DROP INDEX IF EXISTS ix_bundle_sub_active_unique;

-- Decision 12.2 — single ACTIVE/PENDING sub per (participant, bundle).
-- PENDING covers the "Paymob OTP/3DS in flight" case so two checkout calls
-- under different controller-level Idempotency-Keys cannot strand two
-- subscriptions on the same providerRef.
CREATE UNIQUE INDEX ix_bundle_sub_active_unique
    ON bundle_subscriptions (participant_id, bundle_id)
    WHERE status IN ('ACTIVE', 'PENDING');

-- AMQP-side dedup. Keyed by caller-supplied event id (chargebackId, etc.);
-- mirrors webhook_events_inbound but for the broker path.
CREATE TABLE inbound_listener_events (
    event_id            VARCHAR(255) PRIMARY KEY,
    routing_key         VARCHAR(120) NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload_summary     VARCHAR(2000)
);
