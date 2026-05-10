-- ============================================================================
-- Dumble Subscription Service — V1 initial schema
-- Source of truth: Subscription-Service-Decisions.pdf (sections 1-22)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Plans (Decision 3.1) — seed FREE and PRO. Uniform across audiences.
-- ----------------------------------------------------------------------------
CREATE TABLE plans (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                        VARCHAR(20) NOT NULL UNIQUE,
    name                        VARCHAR(100) NOT NULL,
    price_cents                 BIGINT NOT NULL,
    currency                    CHAR(3) NOT NULL,
    can_use_chatbot             BOOLEAN NOT NULL,
    chatbot_messages_per_day    INTEGER,                  -- null = unlimited
    can_dm_anyone               BOOLEAN NOT NULL,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO plans (code, name, price_cents, currency, can_use_chatbot, chatbot_messages_per_day, can_dm_anyone, active)
VALUES
    ('FREE', 'Free',  0,    'EGP', FALSE, 0,    FALSE, TRUE),
    ('PRO',  'Pro',   1000, 'EGP', TRUE,  NULL, TRUE,  TRUE);

-- ----------------------------------------------------------------------------
-- Platform subscriptions (Decision 1.2) — keyed by user_id alone.
-- ----------------------------------------------------------------------------
CREATE TABLE platform_subscriptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL,
    plan_code                   VARCHAR(20) NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    started_at                  TIMESTAMPTZ,
    current_period_end          TIMESTAMPTZ,
    cancel_scheduled_at         TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    provider_ref                VARCHAR(255),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ix_platform_sub_user ON platform_subscriptions (user_id);

-- ----------------------------------------------------------------------------
-- Bundle subscriptions (Decisions 12.1 + 12.2)
-- Snapshot fields frozen at creation; unique active sub per (participant, bundle).
-- ----------------------------------------------------------------------------
CREATE TABLE bundle_subscriptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id              UUID NOT NULL,
    seller_id                   UUID NOT NULL,
    seller_type                 VARCHAR(20) NOT NULL,    -- GYM | TRAINER
    bundle_id                   UUID NOT NULL,

    -- Snapshot (Decision 12.1)
    bundle_name                 VARCHAR(255) NOT NULL,
    price_paid_cents            BIGINT NOT NULL,
    currency                    CHAR(3) NOT NULL,
    duration_days               INTEGER NOT NULL,
    bundle_expires_on_snapshot  TIMESTAMPTZ,             -- null = evergreen

    -- Lifecycle
    status                      VARCHAR(20) NOT NULL,
    started_at                  TIMESTAMPTZ NOT NULL,
    ends_at                     TIMESTAMPTZ NOT NULL,
    cancelled_at                TIMESTAMPTZ,
    auto_renew                  BOOLEAN NOT NULL,
    provider_ref                VARCHAR(255),

    -- Promo (Decision 9.x)
    promo_code                  VARCHAR(60),
    promo_discount_cents        BIGINT,

    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_bundle_sub_participant ON bundle_subscriptions (participant_id);
CREATE INDEX ix_bundle_sub_seller      ON bundle_subscriptions (seller_id);
CREATE INDEX ix_bundle_sub_bundle      ON bundle_subscriptions (bundle_id);
CREATE INDEX ix_bundle_sub_status      ON bundle_subscriptions (status);

-- Decision 12.2 — single ACTIVE sub per (participant, bundle).
-- Partial unique index: only ACTIVE rows are constrained; PENDING/EXPIRED/etc may repeat.
CREATE UNIQUE INDEX ix_bundle_sub_active_unique
    ON bundle_subscriptions (participant_id, bundle_id)
    WHERE status = 'ACTIVE';

-- ----------------------------------------------------------------------------
-- Escrow entries (Decisions 4.x, 5.x)
-- ----------------------------------------------------------------------------
CREATE TABLE escrow_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bundle_subscription_id      UUID NOT NULL,
    seller_id                   UUID NOT NULL,
    amount_cents                BIGINT NOT NULL,
    currency                    CHAR(3) NOT NULL,
    status                      VARCHAR(20) NOT NULL,    -- HELD | AVAILABLE | PAID_OUT | REFUNDED
    cohort_key                  VARCHAR(20) NOT NULL,    -- e.g. "2026-W18"
    original_scheduled_at       TIMESTAMPTZ NOT NULL,
    defer_reason                VARCHAR(60),
    deferred_count              INTEGER NOT NULL DEFAULT 0,
    released_at                 TIMESTAMPTZ,
    paid_out_at                 TIMESTAMPTZ,
    payout_ref                  VARCHAR(255),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_escrow_seller             ON escrow_entries (seller_id);
CREATE INDEX ix_escrow_status_scheduled   ON escrow_entries (status, original_scheduled_at);
CREATE INDEX ix_escrow_subscription       ON escrow_entries (bundle_subscription_id);

-- ----------------------------------------------------------------------------
-- Entry tokens + entry logs (Section 21 — gym entry QR codes)
-- ----------------------------------------------------------------------------
CREATE TABLE entry_tokens (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bundle_subscription_id      UUID NOT NULL,
    participant_id              UUID NOT NULL,
    gym_id                      UUID NOT NULL,
    token_secret                VARCHAR(64) NOT NULL UNIQUE,
    generated_at                TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    used_at                     TIMESTAMPTZ,
    status                      VARCHAR(20) NOT NULL     -- ACTIVE | USED | EXPIRED | SUPERSEDED
);

CREATE INDEX ix_entry_token_subscription ON entry_tokens (bundle_subscription_id);
CREATE INDEX ix_entry_token_status       ON entry_tokens (status);

CREATE TABLE entry_logs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_token_id              UUID,                    -- null if invalid token
    gym_id                      UUID NOT NULL,
    participant_id              UUID,                    -- null if scan failed to identify
    staff_user_id               UUID NOT NULL,
    result                      VARCHAR(10) NOT NULL,    -- GRANTED | DENIED
    denial_reason               VARCHAR(30),
    scanned_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_entry_log_gym_scanned    ON entry_logs (gym_id, scanned_at);
CREATE INDEX ix_entry_log_participant    ON entry_logs (participant_id);

-- ----------------------------------------------------------------------------
-- Participant gym notes (Decision 21.7)
-- ----------------------------------------------------------------------------
CREATE TABLE participant_gym_notes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gym_id                      UUID NOT NULL,
    participant_id              UUID NOT NULL,
    note                        VARCHAR(2000) NOT NULL,
    author_staff_user_id        UUID NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_pgn_gym_participant ON participant_gym_notes (gym_id, participant_id);

-- ----------------------------------------------------------------------------
-- Receipts (Decisions 11.5 + 11.6)
-- ----------------------------------------------------------------------------
CREATE TABLE receipts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL,
    transaction_id              VARCHAR(255) NOT NULL,
    amount_cents                BIGINT NOT NULL,
    currency                    CHAR(3) NOT NULL,
    items_json                  TEXT NOT NULL,
    issued_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_receipt_user ON receipts (user_id, issued_at);

-- ----------------------------------------------------------------------------
-- Promo redemptions (Section 9 — Subscription only RECORDS)
-- ----------------------------------------------------------------------------
CREATE TABLE promo_code_redemptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bundle_subscription_id      UUID NOT NULL,
    gym_id                      UUID NOT NULL,
    code                        VARCHAR(60) NOT NULL,
    discount_applied_cents      BIGINT NOT NULL,
    redeemed_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_promo_redemption_subscription ON promo_code_redemptions (bundle_subscription_id);
CREATE INDEX ix_promo_redemption_code         ON promo_code_redemptions (code);

-- ----------------------------------------------------------------------------
-- Idempotency keys (Decision 12.3 — 24h TTL, nightly cleanup)
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    key                         VARCHAR(128) PRIMARY KEY,
    endpoint                    VARCHAR(120) NOT NULL,
    user_id                     UUID NOT NULL,
    response_json               TEXT NOT NULL,
    http_status                 INTEGER NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_idempotency_expires ON idempotency_keys (expires_at);

-- ----------------------------------------------------------------------------
-- Outbox events (Decision 8.5)
-- ----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type                  VARCHAR(100) NOT NULL,
    routing_key                 VARCHAR(100) NOT NULL,
    payload_json                TEXT NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED | FAILED
    attempts                    INTEGER NOT NULL DEFAULT 0,
    last_error                  VARCHAR(2000),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at                TIMESTAMPTZ
);

CREATE INDEX ix_outbox_status_created ON outbox_events (status, created_at);

-- ----------------------------------------------------------------------------
-- Subscription event log (Section 14 — append-only audit history)
-- ----------------------------------------------------------------------------
CREATE TABLE subscription_event_log (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id             UUID NOT NULL,
    event_type                  VARCHAR(60) NOT NULL,
    timestamp                   TIMESTAMPTZ NOT NULL,
    actor                       VARCHAR(20) NOT NULL,    -- USER | ADMIN | SYSTEM | WEBHOOK
    actor_id                    VARCHAR(100),
    reason                      VARCHAR(500),
    payload_json                TEXT
);

CREATE INDEX ix_sublog_subscription_ts ON subscription_event_log (subscription_id, timestamp);

-- ============================================================================
-- End V1 schema. Future schema changes should land as V2__*.sql, V3__*.sql, etc.
-- Never UPDATE or DELETE rows in subscription_event_log or outbox_events;
-- corrections happen via compensating entries.
-- ============================================================================
