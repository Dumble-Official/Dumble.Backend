-- ============================================================================
-- V4 — end-of-day reminder dedup marker.
-- At most one "you have unfinished items today" reminder per client per local
-- day; this records the last local date a reminder was emitted.
-- ============================================================================

ALTER TABLE schedule ADD COLUMN last_reminded_on DATE;
