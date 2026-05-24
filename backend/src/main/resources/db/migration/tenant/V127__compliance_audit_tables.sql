-- V127__compliance_audit_tables.sql
-- Phase 74 -- AI Intelligence Suite
-- Schema-per-tenant (ADR-T001): no tenant_id column, tenant migration only.

-- ============================================================================
-- 1. AI provenance columns on existing documents table (ADR-292)
-- ============================================================================

ALTER TABLE documents ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS ai_execution_id UUID REFERENCES ai_executions(id);
CREATE INDEX IF NOT EXISTS idx_documents_source ON documents(source) WHERE source != 'MANUAL';
CREATE INDEX IF NOT EXISTS idx_documents_ai_execution ON documents(ai_execution_id) WHERE ai_execution_id IS NOT NULL;

-- Ensure provenance consistency: AI_GENERATED must have execution_id, others must not.
DO $$
BEGIN
  IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint
      WHERE conname = 'chk_documents_provenance'
  ) THEN
    ALTER TABLE documents
      ADD CONSTRAINT chk_documents_provenance
      CHECK (
        (source = 'AI_GENERATED' AND ai_execution_id IS NOT NULL)
        OR (source <> 'AI_GENERATED' AND ai_execution_id IS NULL)
      );
  END IF;
END $$;

-- ============================================================================
-- 2. Compliance Audit Reports
-- ============================================================================

CREATE TABLE IF NOT EXISTS compliance_audit_reports (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID            NOT NULL REFERENCES ai_executions(id),
    overall_grade       VARCHAR(5)      NOT NULL,
    overall_assessment  TEXT            NOT NULL,
    category_scores     JSONB           NOT NULL DEFAULT '{}',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    published_by        UUID,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          UUID            NOT NULL,
    updated_by          UUID            NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_compliance_audit_reports_status ON compliance_audit_reports(status);
CREATE INDEX IF NOT EXISTS idx_compliance_audit_reports_created_at ON compliance_audit_reports(created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_compliance_audit_reports_execution ON compliance_audit_reports(execution_id);

-- ============================================================================
-- 3. Compliance Audit Findings
-- ============================================================================

CREATE TABLE IF NOT EXISTS compliance_audit_findings (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id           UUID            NOT NULL REFERENCES compliance_audit_reports(id) ON DELETE CASCADE,
    finding_id          VARCHAR(10)     NOT NULL,
    severity            VARCHAR(10)     NOT NULL,
    category            VARCHAR(30)     NOT NULL,
    title               VARCHAR(200)    NOT NULL,
    description         TEXT            NOT NULL,
    regulatory_basis    TEXT,
    remediation         TEXT,
    entity_type         VARCHAR(30),
    entity_id           UUID,
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    resolved_by         UUID,
    resolved_at         TIMESTAMPTZ,
    resolution_notes    TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          UUID            NOT NULL,
    updated_by          UUID            NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_compliance_audit_findings_report ON compliance_audit_findings(report_id);
CREATE INDEX IF NOT EXISTS idx_compliance_audit_findings_severity ON compliance_audit_findings(severity);
CREATE INDEX IF NOT EXISTS idx_compliance_audit_findings_status ON compliance_audit_findings(status);
CREATE INDEX IF NOT EXISTS idx_compliance_audit_findings_category ON compliance_audit_findings(category);
CREATE UNIQUE INDEX IF NOT EXISTS idx_compliance_audit_findings_report_finding
    ON compliance_audit_findings(report_id, finding_id);
