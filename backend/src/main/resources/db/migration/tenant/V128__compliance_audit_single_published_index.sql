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
--
-- Existing-data note: CREATE UNIQUE INDEX validates current rows. If a tenant already
-- holds >1 PUBLISHED report (only reachable via the rare race this index closes), a bare
-- index creation would fail. compliance_audit_reports is a new table (introduced in V127)
-- and the race is Low/edge-case, so duplicates are not expected -- but to keep the
-- migration safe to apply unconditionally, first archive any duplicate PUBLISHED rows,
-- keeping the most recent one, then build the index.
UPDATE compliance_audit_reports
   SET status = 'ARCHIVED', updated_at = now()
 WHERE status = 'PUBLISHED'
   AND id <> (
       SELECT id
         FROM compliance_audit_reports
        WHERE status = 'PUBLISHED'
        ORDER BY created_at DESC, id DESC
        LIMIT 1
   );

CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_audit_reports_single_published
    ON compliance_audit_reports ((status)) WHERE status = 'PUBLISHED';
