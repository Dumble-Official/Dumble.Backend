-- ============================================================================
-- Dumble Payment Service — V3 widen payment_method_tokens.last4 to VARCHAR(4)
-- ----------------------------------------------------------------------------
-- V1 declared last4 as CHAR(4). The entity maps as `String last4` which
-- Hibernate validates as VARCHAR. V2 covered the currency columns; this
-- closes out the remaining CHAR-vs-VARCHAR mismatch in payment_method_tokens.
-- ============================================================================

ALTER TABLE payment_method_tokens ALTER COLUMN last4 TYPE VARCHAR(4);
