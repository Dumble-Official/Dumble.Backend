-- ============================================================================
-- Dumble Schedule Service — V1 initial schema
-- One schedule per client (the canvas). Two tables (EXERCISE / MEAL), each a
-- standing Sun..Sat week of free-text items. Data persists until edited — it is
-- NOT date-keyed and never resets per day.
-- ============================================================================

CREATE TABLE schedule (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    timezone    VARCHAR(64),
    version     BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_schedule_user ON schedule (user_id);

-- A single free-text item in a day's list. Content + an optional embedded
-- YouTube video id. Stamped with who authored it (CLIENT for now; TRAINER /
-- CHATBOT added in later slices).
CREATE TABLE schedule_item (
    id                UUID PRIMARY KEY,
    schedule_id       UUID NOT NULL REFERENCES schedule(id) ON DELETE CASCADE,
    table_type        VARCHAR(16) NOT NULL,   -- EXERCISE | MEAL
    weekday           VARCHAR(8)  NOT NULL,   -- SUN..SAT
    position          INTEGER NOT NULL,       -- order within (table, weekday)
    content           TEXT NOT NULL,
    youtube_video_id  VARCHAR(32),
    author_type       VARCHAR(16) NOT NULL,   -- CLIENT | TRAINER | CHATBOT
    author_id         UUID,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_schedule_item_lookup ON schedule_item (schedule_id, table_type, weekday, position);

-- Per-day (Sun..Sat) nutrition target for the Meals table. Persists until edited.
CREATE TABLE meal_day_target (
    id          UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES schedule(id) ON DELETE CASCADE,
    weekday     VARCHAR(8) NOT NULL,
    calories    INTEGER,
    protein_g   INTEGER,
    carbs_g     INTEGER,
    fat_g       INTEGER,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_meal_target UNIQUE (schedule_id, weekday)
);
