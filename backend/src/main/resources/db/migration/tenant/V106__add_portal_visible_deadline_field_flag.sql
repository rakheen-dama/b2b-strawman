-- V106__add_portal_visible_deadline_field_flag.sql
-- Epic 497A: per-FieldDefinition opt-in toggle for surfacing a custom date field on the
-- customer portal's deadlines feed (ADR-257). Default false — clients see only firm-selected
-- dates, preventing the portal from filling with internal reminders.
ALTER TABLE field_definitions
    ADD COLUMN IF NOT EXISTS portal_visible_deadline BOOLEAN NOT NULL DEFAULT false;

-- Partial index supports Phase 48 scanner pre-filter on portal-visible date fields.
CREATE INDEX IF NOT EXISTS idx_field_definitions_portal_visible_deadline
    ON field_definitions (id) WHERE portal_visible_deadline = true;
