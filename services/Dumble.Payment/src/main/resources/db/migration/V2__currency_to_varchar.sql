-- ============================================================================
-- Dumble Payment Service — V2 widen currency to VARCHAR(3)
-- ----------------------------------------------------------------------------
-- The V1 baseline declared currency as CHAR(3). Hibernate maps `String currency`
-- to VARCHAR by default; with ddl-auto: validate the entity vs. schema check
-- refused to start ("found bpchar, expecting varchar(3)"). Widening to
-- VARCHAR(3) aligns with the JPA mapping. Behaviour is identical for the
-- 3-character ISO codes Payment uses; no data conversion needed beyond the
-- column-type swap, which PostgreSQL handles in place.
-- ============================================================================

ALTER TABLE charges ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE payouts ALTER COLUMN currency TYPE VARCHAR(3);
