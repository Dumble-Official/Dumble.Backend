-- ============================================================================
-- V2 — seller bank accounts (PDF Decisions 5.4 + 15.1)
-- ============================================================================
CREATE TABLE seller_bank_accounts (
    seller_id              UUID PRIMARY KEY,
    account_holder_name    VARCHAR(100) NOT NULL,
    destination            VARCHAR(255) NOT NULL,
    destination_type       VARCHAR(30)  NOT NULL,    -- BANK_ACCOUNT | VODAFONE_CASH | etc.
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
