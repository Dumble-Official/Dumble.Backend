-- ============================================================================
-- Dumble Payment Service — V4 strict-idempotency request-body hash
-- ----------------------------------------------------------------------------
-- Adds a stored SHA-256 hash of the request payload on each idempotency_keys
-- row. On a replay with the SAME key but a DIFFERENT body the service now
-- returns 409 instead of silently replaying the cached response — closes the
-- "client retries with a corrected amount silently no-ops" foot-gun that the
-- pre-production QA pass surfaced. Existing rows (none in production yet,
-- there's no live Payment data) get NULL, and the service treats NULL as
-- "no recorded hash → skip comparison" to keep the migration backward-safe.
-- ============================================================================

-- VARCHAR(64) rather than CHAR(64): a SHA-256 hex is exactly 64 chars so the
-- distinction is cosmetic, and VARCHAR matches the column type Hibernate's
-- strict schema validator expects from a @Column(length = 64) field. CHAR
-- would force us to pin `columnDefinition = "char(64)"` on the entity, which
-- breaks portability of the JPA class.
ALTER TABLE idempotency_keys
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

COMMENT ON COLUMN idempotency_keys.request_hash IS
    'SHA-256 hex of the request body serialized as Jackson JSON. Used to detect Idempotency-Key reuse with a mismatched payload (Decision 3.2 — strict variant).';
