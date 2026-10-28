-- =====================================================================================
-- Phase 88 — demo-ready root-cause closure, §5 U17 (per-matter retention end), Slice 627B,
-- ADR-354. V4 is closed (applied to the Keycloak dev DB 2026-10-24); V5 (624F), V6 (625C) and
-- V7 (626A) are taken, so this section takes the next free tenant file (OQ #9). 627H appends
-- §7 (comment attachments) to this file while it is unapplied to any persistent stack.
-- Idempotent: the seed and backfill only touch NULL rows; ADD COLUMN / CREATE INDEX IF NOT
-- EXISTS resolve in current_schema().
-- =====================================================================================

-- ---------- §5 U17: per-matter retention end -------------------------------------------
-- The data-protection years column is NULL on every tenant because the entity insert bypassed
-- DEFAULT 5; seed it so closure and the read side agree (the setting becomes firm-editable).
UPDATE org_settings SET legal_matter_retention_years = 5 WHERE legal_matter_retention_years IS NULL;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS retention_ends_on date;
-- Backfill CLOSED matters from their anchor + the (now seeded) firm years. ADR-249: anchor = closure.
-- COALESCE sits OUTSIDE the scalar subquery (authorised deviation from §13.7.1, as V4 §2): a schema
-- with no org_settings row makes the subquery yield NULL, and date + NULL would leave the end date
-- NULL; the outer COALESCE still backfills with 5 there. Identical result whenever the row exists.
-- closed_at is timestamptz but retention_clock_started_at is timestamp WITHOUT time zone, and the
-- application writes the clock as a UTC wall-clock. A bare COALESCE would cast the clock to
-- timestamptz in the session time zone (a non-UTC migration session would shift the date), so the
-- clock is read AT TIME ZONE 'UTC' first; the outer AT TIME ZONE 'UTC' then yields the UTC day.
UPDATE projects p
SET retention_ends_on = (COALESCE(p.closed_at, p.retention_clock_started_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')::date
                        + make_interval(years => COALESCE((SELECT s.legal_matter_retention_years FROM org_settings s LIMIT 1), 5))
WHERE p.status = 'CLOSED' AND p.retention_ends_on IS NULL
  AND (p.closed_at IS NOT NULL OR p.retention_clock_started_at IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_projects_retention_ends_on ON projects (retention_ends_on)
  WHERE retention_ends_on IS NOT NULL;   -- the retention evaluator scans "due for action by date"
