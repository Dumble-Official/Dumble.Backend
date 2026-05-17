-- ============================================================================
-- Dumble Wallet Service — V2 widen currency to VARCHAR(3)
-- ----------------------------------------------------------------------------
-- Same shape as Payment's V2 migration. V1 declared currency as CHAR(3);
-- Hibernate's `String currency` default maps to VARCHAR. With ddl-auto: validate
-- the entity vs. schema check refused to start ("found bpchar, expecting
-- varchar(3)"). Widening to VARCHAR(3) aligns with JPA's mapping. Behaviour
-- is identical for 3-character ISO codes; PostgreSQL alters in place.
-- ============================================================================

ALTER TABLE wallets             ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE withdrawal_requests ALTER COLUMN currency TYPE VARCHAR(3);
