-- ============================================================================
-- Dumble Payment Service — V1 initial schema
-- Source of truth: Payment-Service-Decisions.pdf (sections 1-11)
-- ============================================================================

-- pgcrypto provides gen_random_uuid().
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- charges (Decisions 3.1, 3.3) — every charge persisted in PENDING BEFORE the
-- Paymob call so a lost response is recoverable via reconciliation.
-- ----------------------------------------------------------------------------
CREATE TABLE charges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL,
    amount_cents        BIGINT NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'EGP',
    payment_method_token VARCHAR(255),
    description         VARCHAR(500),
    caller_reference    VARCHAR(255),         -- subscription id, etc.
    status              VARCHAR(20) NOT NULL, -- PENDING | SUCCEEDED | FAILED | REVERSED
    provider_ref        VARCHAR(255),         -- Paymob transaction id
    failure_reason      VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_charge_amount_pos CHECK (amount_cents > 0),
    CONSTRAINT chk_charge_status     CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REVERSED'))
);

CREATE INDEX ix_charge_user             ON charges (user_id, created_at DESC);
CREATE INDEX ix_charge_caller_ref       ON charges (caller_reference);
CREATE INDEX ix_charge_provider_ref     ON charges (provider_ref);
CREATE INDEX ix_charge_status_created   ON charges (status, created_at);

-- ----------------------------------------------------------------------------
-- refunds (Decisions 5.1, 5.2)
-- ----------------------------------------------------------------------------
CREATE TABLE refunds (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    charge_id           UUID NOT NULL REFERENCES charges (id),
    amount_cents        BIGINT NOT NULL,
    destination         VARCHAR(20) NOT NULL,    -- WALLET | ORIGINAL_METHOD
    status              VARCHAR(20) NOT NULL,    -- PENDING | SUCCEEDED | FAILED
    provider_ref        VARCHAR(255),
    failure_reason      VARCHAR(500),
    initiated_by        VARCHAR(60),             -- caller name (subscription, admin, etc.)
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_refund_amount_pos CHECK (amount_cents > 0),
    CONSTRAINT chk_refund_destination CHECK (destination IN ('WALLET', 'ORIGINAL_METHOD')),
    CONSTRAINT chk_refund_status     CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_refund_charge       ON refunds (charge_id);
CREATE INDEX ix_refund_provider_ref ON refunds (provider_ref);

-- ----------------------------------------------------------------------------
-- payouts (Decisions 6.1, 6.2) — single table for both kinds of money-out:
--   USER_WITHDRAWAL — Wallet-driven; user clicked "Withdraw"
--   COHORT_PAYOUT   — Subscription-driven; weekly seller batch
-- The lifecycle (PENDING → SENT → COMPLETED|FAILED) is identical; type is just
-- a tag so events and reconciliation can route correctly.
-- ----------------------------------------------------------------------------
CREATE TABLE payouts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                VARCHAR(20) NOT NULL,        -- USER_WITHDRAWAL | COHORT_PAYOUT
    subject_id          UUID NOT NULL,               -- userId for withdrawals, sellerId for cohort
    amount_cents        BIGINT NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'EGP',
    destination_json    TEXT NOT NULL,
    destination_type    VARCHAR(30),                 -- BANK_ACCOUNT | VODAFONE_CASH | ...
    caller_reference    VARCHAR(255) NOT NULL,       -- Wallet's withdrawal id OR Subscription's batch ref
    cohort_key          VARCHAR(20),                 -- Decision 6.2 — only for cohort payouts
    notes               VARCHAR(500),
    status              VARCHAR(20) NOT NULL,        -- PENDING | SENT | COMPLETED | FAILED
    provider_ref        VARCHAR(255),
    failure_reason      VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT chk_payout_amount_pos CHECK (amount_cents > 0),
    CONSTRAINT chk_payout_type       CHECK (type IN ('USER_WITHDRAWAL', 'COHORT_PAYOUT')),
    CONSTRAINT chk_payout_status     CHECK (status IN ('PENDING', 'SENT', 'COMPLETED', 'FAILED'))
);

CREATE INDEX ix_payout_caller_ref     ON payouts (caller_reference);
CREATE INDEX ix_payout_subject        ON payouts (subject_id, created_at DESC);
CREATE INDEX ix_payout_provider_ref   ON payouts (provider_ref);
CREATE INDEX ix_payout_status_created ON payouts (status, created_at);

-- ----------------------------------------------------------------------------
-- payment_method_tokens (Decision 10.1) — Payment never sees raw card numbers;
-- frontend tokenises with Paymob, we record the opaque handle for reuse.
-- ----------------------------------------------------------------------------
CREATE TABLE payment_method_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    token           VARCHAR(255) NOT NULL,        -- opaque Paymob token
    method_type     VARCHAR(20) NOT NULL,         -- CARD | WALLET | OTHER
    label           VARCHAR(120),                 -- "Visa ending 4242" — display only
    card_brand      VARCHAR(20),
    last4           CHAR(4),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT chk_pmt_method CHECK (method_type IN ('CARD', 'WALLET', 'OTHER'))
);

CREATE INDEX ix_pmt_user_active ON payment_method_tokens (user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ix_pmt_token ON payment_method_tokens (token);

-- ----------------------------------------------------------------------------
-- webhook_events (Decisions 4.1, 4.2, 4.3) — Paymob inbound. PK is the Paymob
-- event id so a redelivered event is rejected at the door. Two-phase: row is
-- inserted at receive time, processed_at is stamped when the async worker
-- mutates Charge / Refund / Payout state.
-- ----------------------------------------------------------------------------
CREATE TABLE webhook_events (
    event_id          VARCHAR(255) PRIMARY KEY,
    event_type        VARCHAR(80) NOT NULL,
    payload_json      TEXT NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
    attempts          INTEGER NOT NULL DEFAULT 0,
    last_error        VARCHAR(2000),
    processed_at      TIMESTAMPTZ,

    CONSTRAINT chk_webhook_status CHECK (processing_status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX ix_webhook_status_received ON webhook_events (processing_status, received_at);

-- ----------------------------------------------------------------------------
-- idempotency_keys (Decision 3.2) — 24h TTL; nightly cleanup. Same shape as
-- Subscription / Wallet so the platform's idempotency story is uniform.
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    key             VARCHAR(128) PRIMARY KEY,
    endpoint        VARCHAR(120) NOT NULL,
    user_id         UUID,
    state           VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',  -- PENDING | COMPLETED
    response_json   TEXT,
    http_status     INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_idempotency_expires ON idempotency_keys (expires_at);

-- ----------------------------------------------------------------------------
-- outbox_events (Decision 9.4) — domain events written in the same DB tx as
-- the state change; background worker drains to RabbitMQ.
-- ----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,
    routing_key     VARCHAR(120) NOT NULL,
    payload_json    TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',     -- PENDING | PUBLISHED | FAILED
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      VARCHAR(2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX ix_outbox_status_created ON outbox_events (status, created_at);

-- ----------------------------------------------------------------------------
-- reconciliation_runs (Decisions 7.1, 7.2, 7.3) — one row per nightly job run;
-- the rendered report is plumbed into the admin dashboard.
-- ----------------------------------------------------------------------------
CREATE TABLE reconciliation_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    window_from         TIMESTAMPTZ NOT NULL,
    window_to           TIMESTAMPTZ NOT NULL,
    total_local         INTEGER NOT NULL DEFAULT 0,
    total_provider      INTEGER NOT NULL DEFAULT 0,
    auto_resolved       INTEGER NOT NULL DEFAULT 0,
    alerts              INTEGER NOT NULL DEFAULT 0,
    notes               TEXT
);

CREATE INDEX ix_recon_started ON reconciliation_runs (started_at DESC);

-- Append-only event log for forensics — admin reads, manual interventions,
-- charge state transitions outside the normal flow.
CREATE TABLE payment_event_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type    VARCHAR(20) NOT NULL,        -- CHARGE | REFUND | PAYOUT | WEBHOOK | RECON
    subject_id      VARCHAR(64) NOT NULL,
    event_type      VARCHAR(60) NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor           VARCHAR(20) NOT NULL,        -- USER | ADMIN | SYSTEM | WEBHOOK | PROVIDER
    actor_id        VARCHAR(100),
    reason          VARCHAR(500),
    payload_json    TEXT
);

CREATE INDEX ix_payment_log_subject_ts ON payment_event_log (subject_type, subject_id, timestamp DESC);

-- ============================================================================
-- End V1 schema. Future schema changes land as V2__*.sql, V3__*.sql, etc.
-- payment_event_log is append-only; never UPDATE or DELETE.
-- ============================================================================
