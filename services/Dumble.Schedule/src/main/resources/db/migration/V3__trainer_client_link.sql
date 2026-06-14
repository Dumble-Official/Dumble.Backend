-- ============================================================================
-- V3 — trainer ↔ client access read-model.
-- A local projection of "this trainer may coach this client", driven by the
-- Subscription service's bundle-subscription lifecycle (active sub => active
-- link; cancelled/expired => inactive). The RabbitMQ consumer that keeps it in
-- sync is wired in a later slice; the upsert is exposed on an internal endpoint.
-- The schedule trainer-write gate reads this table — no synchronous call to
-- Subscription on the hot path.
-- ============================================================================

CREATE TABLE trainer_client_link (
    id          UUID PRIMARY KEY,
    trainer_id  UUID NOT NULL,
    client_id   UUID NOT NULL,
    active      BOOLEAN NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_trainer_client UNIQUE (trainer_id, client_id)
);
CREATE INDEX ix_trainer_client_active ON trainer_client_link (trainer_id, client_id, active);
