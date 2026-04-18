-- V101 (originally planned V97 in architecture §67.8.2) — Matter Closure foundation +
-- invoice_lines.disbursement_id. Epic 489A (Phase 67). Renumbered to next free slot after V100.
-- References: architecture/phase67-legal-depth-ii.md §67.8.2, ADR-248, ADR-249.
--
-- This migration is the DB foundation for the Matter Closure workflow:
--   * Adds CLOSED as a valid projects.status + projects.closed_at timestamp.
--   * Creates matter_closure_log audit table (per ADR-248).
--   * Adds invoice_lines.disbursement_id FK + line_source CHECK including DISBURSEMENT
--     (needed by slice 487B which maps LegalDisbursement -> InvoiceLine).
--   * Adds retention_policies.cancelled_at (per ADR-249 reopen path — soft-cancel).
--   * Adds document_templates.acceptance_eligible (per ADR-251, wired in 489B/492).
--   * Adds org_settings.legal_matter_retention_years (per ADR-249 — default 5).
--
-- Each statement is idempotent (IF NOT EXISTS / IF EXISTS + DROP-then-ADD for CHECKs).

-- ============================================================
-- 1. projects.status: allow CLOSED; add closed_at.
-- ============================================================
ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_status_check;
ALTER TABLE projects ADD CONSTRAINT projects_status_check
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED', 'CLOSED'));
ALTER TABLE projects ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

-- ============================================================
-- 2. matter_closure_log
-- ============================================================
CREATE TABLE IF NOT EXISTS matter_closure_log (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id                  UUID NOT NULL,
    closed_by                   UUID NOT NULL,
    closed_at                   TIMESTAMPTZ NOT NULL,
    reason                      VARCHAR(40) NOT NULL,
    notes                       TEXT,
    gate_report                 JSONB NOT NULL,
    override_used               BOOLEAN NOT NULL DEFAULT false,
    override_justification      TEXT,
    closure_letter_document_id  UUID,
    reopened_at                 TIMESTAMPTZ,
    reopened_by                 UUID,
    reopen_notes                TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_matter_closure_log_reason
        CHECK (reason IN ('CONCLUDED','CLIENT_TERMINATED','REFERRED_OUT','OTHER')),
    CONSTRAINT ck_matter_closure_log_override_justification
        CHECK (override_used = false
               OR (override_justification IS NOT NULL
                   AND length(trim(override_justification)) >= 20)),
    CONSTRAINT ck_matter_closure_log_reopen_consistent
        CHECK ((reopened_at IS NULL AND reopened_by IS NULL AND reopen_notes IS NULL)
               OR (reopened_at IS NOT NULL AND reopened_by IS NOT NULL
                   AND reopen_notes IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS ix_matter_closure_log_project_closed
    ON matter_closure_log (project_id, closed_at DESC);
CREATE INDEX IF NOT EXISTS ix_matter_closure_log_override_used
    ON matter_closure_log (project_id)
    WHERE override_used = true;

-- ============================================================
-- 3. invoice_lines: line_source CHECK + disbursement_id FK
-- ============================================================
-- NOTE: no existing line_source CHECK today (V83 added the column only).
-- We guard the DROP for idempotence.
ALTER TABLE invoice_lines DROP CONSTRAINT IF EXISTS invoice_lines_line_source_check;
ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_line_source_check
    CHECK (line_source IS NULL OR line_source IN
        ('TIME','RETAINER','EXPENSE','MANUAL','FIXED_FEE','TARIFF','DISBURSEMENT'));

ALTER TABLE invoice_lines
    ADD COLUMN IF NOT EXISTS disbursement_id UUID
    REFERENCES legal_disbursements(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS ix_invoice_lines_disbursement
    ON invoice_lines (disbursement_id)
    WHERE disbursement_id IS NOT NULL;

-- ============================================================
-- 4. retention_policies.cancelled_at (per ADR-249)
-- ============================================================
ALTER TABLE retention_policies
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

-- ============================================================
-- 5. document_templates.acceptance_eligible (per ADR-251, wired in 489B/492)
-- ============================================================
ALTER TABLE document_templates
    ADD COLUMN IF NOT EXISTS acceptance_eligible BOOLEAN NOT NULL DEFAULT false;

-- ============================================================
-- 6. org_settings.legal_matter_retention_years (per ADR-249)
-- ============================================================
ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS legal_matter_retention_years INTEGER;
