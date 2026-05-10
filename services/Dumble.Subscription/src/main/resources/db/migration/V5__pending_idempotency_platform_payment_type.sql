-- ============================================================================
-- V5 — addresses ultrareview-pr4 findings:
--   * idempotency_keys.state — PENDING/COMPLETED gate so the PK can serialize
--     concurrent peers (closes the executeOrFetch TOCTOU race).
--   * platform_subscriptions.payment_method_type — symmetric with
--     bundle_subscriptions; lets PRO renewal honour Decision 7.2 (no silent
--     wallet auto-charge).
--   * bundle_subscriptions.renewal_prompted_at — sentinel for the
--     wallet/no-token renewal-prompt path so a single sub doesn't
--     re-prompt every hour forever.
-- ============================================================================

ALTER TABLE idempotency_keys
    ADD COLUMN state VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE idempotency_keys
    ALTER COLUMN response_json DROP NOT NULL;

ALTER TABLE platform_subscriptions
    ADD COLUMN payment_method_type VARCHAR(20);            -- CARD | WALLET | OTHER

ALTER TABLE bundle_subscriptions
    ADD COLUMN renewal_prompted_at TIMESTAMPTZ;
