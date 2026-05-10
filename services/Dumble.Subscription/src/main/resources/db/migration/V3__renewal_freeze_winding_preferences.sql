-- ============================================================================
-- V3 — closes gaps from feature-completeness review:
--   * dunning state on subscriptions
--   * seller lifecycle table (Active | Frozen | WindingDown | Banned | Closed)
--   * participant preferences (gym-list opt-out)
--   * webhook idempotency table
--   * supporting indexes
-- ============================================================================

-- BundleSubscription gains dunning fields ----------------------------------
ALTER TABLE bundle_subscriptions
    ADD COLUMN past_due_at      TIMESTAMPTZ,
    ADD COLUMN retry_attempts   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at    TIMESTAMPTZ;

CREATE INDEX ix_bundle_sub_renewal_due ON bundle_subscriptions (status, ends_at)
    WHERE status = 'ACTIVE' AND auto_renew = TRUE;

CREATE INDEX ix_bundle_sub_dunning_due ON bundle_subscriptions (status, next_retry_at)
    WHERE status = 'PAST_DUE';

-- PlatformSubscription gains a payment method token (for renewal) ---------
ALTER TABLE platform_subscriptions
    ADD COLUMN payment_method_token VARCHAR(255),
    ADD COLUMN past_due_at          TIMESTAMPTZ,
    ADD COLUMN retry_attempts       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at        TIMESTAMPTZ;

CREATE INDEX ix_platform_sub_period_end ON platform_subscriptions (status, current_period_end);

-- Seller lifecycle (Section 16, 17, 18, 19) -------------------------------
CREATE TABLE seller_lifecycle (
    seller_id            UUID PRIMARY KEY,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                                        -- ACTIVE | FROZEN | WINDING_DOWN | BANNED | CLOSED
    frozen_at            TIMESTAMPTZ,
    frozen_reason        VARCHAR(500),
    frozen_until         TIMESTAMPTZ,                   -- 7-day auto-ban deadline (Decision 16.2)
    winding_down_at      TIMESTAMPTZ,
    winding_down_reason  VARCHAR(500),
    banned_at            TIMESTAMPTZ,
    ban_reason           VARCHAR(500),
    closed_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_seller_lifecycle_status_until ON seller_lifecycle (status, frozen_until);

-- Participant preferences (Decision 10.1 opt-out) -------------------------
CREATE TABLE participant_preferences (
    participant_id        UUID PRIMARY KEY,
    hide_from_gym_lists   BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Webhook idempotency (Subscription's inbound HTTP webhooks) --------------
-- Keyed by provider event id or caller-supplied key.
CREATE TABLE webhook_events_inbound (
    event_id            VARCHAR(255) PRIMARY KEY,
    source              VARCHAR(50) NOT NULL,           -- payment | authentication | gym | etc.
    event_type          VARCHAR(80) NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload_summary     VARCHAR(2000)
);

-- Receipt enhancements: explicit user-facing item rows for bilingual rendering
-- Future-proof; the items_json field already covers data needs.
ALTER TABLE receipts
    ADD COLUMN subject_subscription_id UUID,
    ADD COLUMN subject_type            VARCHAR(20);     -- BUNDLE | PLATFORM

CREATE INDEX ix_receipts_subject ON receipts (subject_subscription_id);
