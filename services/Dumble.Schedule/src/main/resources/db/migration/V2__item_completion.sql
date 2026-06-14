-- ============================================================================
-- V2 — per-date completion overlay.
-- The plan (items/targets) is a standing weekly template and persists; whether
-- a client did an item is per calendar date, so each day starts unmarked while
-- the plan itself never resets. One row = "this item was done on this date".
-- ============================================================================

CREATE TABLE item_completion (
    id            UUID PRIMARY KEY,
    item_id       UUID NOT NULL REFERENCES schedule_item(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL,
    completed_on  DATE NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_item_completion UNIQUE (item_id, completed_on)
);
CREATE INDEX ix_item_completion_lookup ON item_completion (item_id, completed_on);
