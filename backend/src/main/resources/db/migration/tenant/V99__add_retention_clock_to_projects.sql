-- V99: Add retention-clock field to projects (minimal slice of ADR-249).
--
-- Purpose: record the moment the retention clock starts for a project. Per ADR-249 the
-- canonical anchor is Matter Closure (ADR-248 CLOSED state — Phase 67). Until CLOSED lands,
-- this slice anchors to the existing ACTIVE -> COMPLETED transition so legal tenants have a
-- retention-clock timestamp in place. When Phase 67 wires up a distinct CLOSED state, the
-- service hook moves from `project.complete(...)` to `project.close(...)`; the column does
-- not need to change.
--
-- Column semantics:
--   retention_clock_started_at TIMESTAMP (nullable) — set exactly once, on the first transition
--   that triggers retention. Never overwritten (re-completion keeps the original timestamp so
--   the retention sweep evaluates against the earliest trigger).
--
-- Out of scope for this slice (tracked as followups in the Phase 67 work):
--   - retention_period_days (per-project override; currently derived from per-org OrgSettings)
--   - UI exposure of the timestamp on the Matter detail page
--   - purge / anonymisation sweep consuming this field
--   - soft-cancel on matter reopen (ADR-249 §Consequences bullet 2)

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS retention_clock_started_at TIMESTAMP;

-- Partial index to support future retention-sweep queries; tiny because most rows are NULL.
CREATE INDEX IF NOT EXISTS ix_projects_retention_clock_started_at
    ON projects (retention_clock_started_at)
    WHERE retention_clock_started_at IS NOT NULL;
