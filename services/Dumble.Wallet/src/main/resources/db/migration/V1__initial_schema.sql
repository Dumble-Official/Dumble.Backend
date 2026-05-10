-- ============================================================================
-- Dumble Wallet Service — V1 initial schema
-- Source of truth: Wallet-Service-Decisions.pdf (sections 1-8)
-- ============================================================================

-- pgcrypto provides gen_random_uuid(); ships with Postgres 13+ but isn't
-- always pre-installed in fresh databases. Idempotent.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- Wallet (Decision 2.1, 2.2) — one row per user. user_id is the PK so we never
-- have two wallets for the same user. AvailableCents is the cached balance
-- updated atomically alongside every WalletEntry insert (Decision 2.3).
-- ----------------------------------------------------------------------------
CREATE TABLE wallets (
    user_id           UUID PRIMARY KEY,
    currency          CHAR(3) NOT NULL DEFAULT 'EGP',
    available_cents   BIGINT NOT NULL DEFAULT 0,
    pending_cents     BIGINT NOT NULL DEFAULT 0,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_wallet_available_nonneg CHECK (available_cents >= 0),
    CONSTRAINT chk_wallet_pending_nonneg   CHECK (pending_cents   >= 0)
);

-- ----------------------------------------------------------------------------
-- WalletEntry (Decisions 2.2, 5.1) — append-only ledger. Database-level
-- enforcement of write-only via a trigger that blocks UPDATE/DELETE.
-- ----------------------------------------------------------------------------
CREATE TABLE wallet_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_user_id    UUID NOT NULL REFERENCES wallets (user_id),
    type              VARCHAR(10) NOT NULL,         -- CREDIT | DEBIT
    amount_cents      BIGINT NOT NULL,              -- always positive
    source            VARCHAR(30) NOT NULL,         -- BanRefund | Chargeback | AdminAdjustment
                                                    -- | InAppSpend | WithdrawalRequested | WithdrawalReversed
    external_ref      VARCHAR(255),                 -- refund-id / subscription-id / withdrawal-id
    memo              VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_wallet_entry_amount_pos CHECK (amount_cents > 0),
    CONSTRAINT chk_wallet_entry_type       CHECK (type IN ('CREDIT', 'DEBIT'))
);

CREATE INDEX ix_wallet_entry_user_created ON wallet_entries (wallet_user_id, created_at DESC);
CREATE INDEX ix_wallet_entry_external_ref ON wallet_entries (external_ref);

-- Decision 5.1 — append-only enforcement at the database layer.
CREATE OR REPLACE FUNCTION wallet_entries_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'wallet_entries is append-only (Wallet PDF Decision 5.1)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallet_entries_no_update
    BEFORE UPDATE OR DELETE ON wallet_entries
    FOR EACH ROW EXECUTE FUNCTION wallet_entries_block_mutation();

-- ----------------------------------------------------------------------------
-- WithdrawalRequest (Decision 4.2) — Pending → Sent → Completed | Failed | Cancelled.
-- destination is JSON so different rails (bank account, mobile-wallet number, etc.)
-- can carry their own shape without a schema change per rail.
-- ----------------------------------------------------------------------------
CREATE TABLE withdrawal_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_user_id    UUID NOT NULL REFERENCES wallets (user_id),
    amount_cents      BIGINT NOT NULL,
    currency          CHAR(3) NOT NULL DEFAULT 'EGP',
    destination_json  TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL,        -- PENDING | SUBMITTING | SENT | COMPLETED | FAILED | CANCELLED
    payment_ref       VARCHAR(255),                -- Payment service withdrawal id
    failure_reason    VARCHAR(500),
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,

    CONSTRAINT chk_withdrawal_amount_pos CHECK (amount_cents > 0),
    CONSTRAINT chk_withdrawal_status     CHECK (status IN ('PENDING', 'SUBMITTING', 'SENT', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX ix_withdrawal_user_created ON withdrawal_requests (wallet_user_id, created_at DESC);
CREATE INDEX ix_withdrawal_status        ON withdrawal_requests (status);
CREATE INDEX ix_withdrawal_payment_ref   ON withdrawal_requests (payment_ref);

-- ----------------------------------------------------------------------------
-- Idempotency keys (Decisions 3.1, 4.1, 4.3) — caller-supplied UUIDs deduplicate
-- credit / debit / withdrawal-request POSTs over a 24h window.
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    key               VARCHAR(128) PRIMARY KEY,
    endpoint          VARCHAR(120) NOT NULL,
    user_id           UUID NOT NULL,
    state             VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',   -- PENDING | COMPLETED
    response_json     TEXT,
    http_status       INTEGER NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_idempotency_expires ON idempotency_keys (expires_at);

-- ----------------------------------------------------------------------------
-- Outbox (Decision 6.5) — domain events written in the same transaction as the
-- ledger change. Background worker drains to RabbitMQ.
-- ----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type        VARCHAR(100) NOT NULL,       -- WalletCredited | WalletDebited | WithdrawalRequested | etc.
    routing_key       VARCHAR(120) NOT NULL,
    payload_json      TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',     -- PENDING | PUBLISHED | FAILED
    attempts          INTEGER NOT NULL DEFAULT 0,
    last_error        VARCHAR(2000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at      TIMESTAMPTZ
);

CREATE INDEX ix_outbox_status_created ON outbox_events (status, created_at);

-- ----------------------------------------------------------------------------
-- Inbound listener events — AMQP-side dedup for events Wallet consumes
-- (WithdrawalCompleted / WithdrawalFailed). Mirrors webhook_events_inbound from
-- Subscription so a redelivered event can't double-process.
-- ----------------------------------------------------------------------------
CREATE TABLE inbound_listener_events (
    event_id          VARCHAR(255) PRIMARY KEY,
    routing_key       VARCHAR(120) NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload_summary   VARCHAR(2000)
);

-- ----------------------------------------------------------------------------
-- Append-only audit log (Decision 5.3) — admin reads / adjustments / lifecycle
-- transitions. Distinct from the ledger; lives for forensic reconstruction.
-- ----------------------------------------------------------------------------
CREATE TABLE wallet_event_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_user_id    UUID NOT NULL,
    event_type        VARCHAR(60) NOT NULL,
    timestamp         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor             VARCHAR(20) NOT NULL,        -- USER | ADMIN | SYSTEM | WEBHOOK
    actor_id          VARCHAR(100),
    reason            VARCHAR(500),
    payload_json      TEXT
);

CREATE INDEX ix_wallet_log_user_ts ON wallet_event_log (wallet_user_id, timestamp DESC);

-- ============================================================================
-- End V1 schema. Future schema changes land as V2__*.sql, V3__*.sql, etc.
-- Never UPDATE or DELETE rows in wallet_entries or wallet_event_log;
-- corrections happen via compensating entries (WalletEntry source = AdminAdjustment).
-- ============================================================================
