-- ============================================================================
-- Dumble Subscription Service — V2 widen all currency columns to VARCHAR(3)
-- ----------------------------------------------------------------------------
-- V1 declared every currency column as CHAR(3). Hibernate's `String currency`
-- maps to VARCHAR by default; with ddl-auto: validate the schema check refused
-- to start ("found bpchar, expecting varchar(3)"). Widening to VARCHAR(3) on
-- every table that has a currency column aligns the schema with the entity
-- mapping. Behaviour is identical for 3-character ISO codes; PostgreSQL alters
-- in place without data conversion.
-- ============================================================================

ALTER TABLE plans                 ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE bundle_subscriptions  ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE escrow_entries        ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE receipts              ALTER COLUMN currency TYPE VARCHAR(3);
