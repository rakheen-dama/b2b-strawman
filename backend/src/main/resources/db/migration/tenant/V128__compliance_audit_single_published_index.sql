-- V128__compliance_audit_single_published_index.sql
-- AIVERIFY-004: enforce the "exactly one PUBLISHED compliance audit report" invariant
-- in the schema, closing an unsynchronised archive-then-insert window in
-- ComplianceAuditReportService.publishReport.
--
-- Two concurrent approvals of DISTINCT compliance gates each read the PUBLISHED set
-- (READ COMMITTED, no lock), archive the shared previous report, and insert a new
-- PUBLISHED row. Because the only prior uniqueness is on execution_id, both inserts
-- succeed -> two PUBLISHED reports, violating the singleton invariant.
--
-- A partial unique index makes the second concurrent insert fail with a unique
-- violation; that transaction rolls back and the gate-approve surfaces a 409 the
-- reviewer can retry cleanly. (Precedent: V43 idx_tax_rates_single_default.)

CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_audit_reports_single_published
    ON compliance_audit_reports ((status)) WHERE status = 'PUBLISHED';
