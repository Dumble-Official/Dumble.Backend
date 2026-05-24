-- VARCHAR(64) rather than CHAR(64): a SHA-256 hex is exactly 64 chars so the
-- distinction is cosmetic, and VARCHAR matches the column type Hibernate's
-- strict schema validator expects from a @Column(length = 64) field. CHAR
-- would force us to pin columnDefinition = "char(64)" on the entity, which
-- breaks portability of the JPA class.
ALTER TABLE idempotency_keys
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

COMMENT ON COLUMN idempotency_keys.request_hash IS
    'SHA-256 hex of the request body serialized as Jackson JSON with sorted map keys. Used to detect Idempotency-Key reuse with a mismatched payload (Decision 3.2 strict variant — mirrors Payment service).';
