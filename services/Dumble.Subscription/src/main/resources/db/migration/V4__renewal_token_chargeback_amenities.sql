-- ============================================================================
-- V4 — closes review gaps:
--   * payment_method_token + payment_method_type on bundle_subscriptions
--     (bundle renewals were reaching for provider_ref, which is the wrong id)
--   * amenities_json snapshot on bundle_subscriptions (Decision 21.4 scan
--     response needs this)
--   * discount_type on promo_code_redemptions (auditability)
-- ============================================================================

ALTER TABLE bundle_subscriptions
    ADD COLUMN payment_method_token VARCHAR(255),
    ADD COLUMN payment_method_type  VARCHAR(20),     -- CARD | WALLET | OTHER
    ADD COLUMN amenities_json       TEXT;

ALTER TABLE promo_code_redemptions
    ADD COLUMN discount_type VARCHAR(20);            -- PERCENT | FIXED
