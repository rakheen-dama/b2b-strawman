-- V138__drop_retainers.sql
-- Phase 86 (Epic 598A, ADR-331 / ADR-336): remove the Phase 17 retainer agreements/periods
-- (hour-bank "Mandate"), their invoice-line link, the billing-run retainer leg flag, and the
-- PORTAL_RETAINER email reference type. Throwaway migration — squashed into tenant V1 at zero
-- tenants (ADR-331); no production data exists. "Mandate" is reserved for the engagement-letter
-- flow (ADR-336 B1). org_settings.portal_retainer_member_display is dropped by V139 (Epic 598B).

-- 1. Enum-string row cleanup BEFORE the Java constant disappears (595B §4).
--    invoice_lines.line_type is @Enumerated(STRING) InvoiceLineType (InvoiceLine.java);
--    RETAINER rows were written by RetainerPeriodService (deleted) and back-filled by V50:63.
--    Decided: DELETE, not re-type (ADR-331 zero-tenant squash). legal_disbursements.invoice_line_id
--    is ON DELETE SET NULL (V100:27) — the only FK into invoice_lines.
DELETE FROM invoice_lines WHERE line_type = 'RETAINER';

-- 2. invoice_lines.retainer_period_id + its partial index (V31:63-68). The FK to
--    retainer_periods(id) goes with the column, so the tables below can then be dropped.
DROP INDEX IF EXISTS idx_invoice_lines_retainer_period_id;
ALTER TABLE invoice_lines DROP COLUMN IF EXISTS retainer_period_id;

--    Latest definition: V104:9-11, minus 'RETAINER' (order preserved).
ALTER TABLE invoice_lines DROP CONSTRAINT IF EXISTS invoice_lines_line_type_check;
ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_line_type_check
    CHECK (line_type IN ('TIME', 'EXPENSE', 'MANUAL', 'FIXED_FEE', 'TARIFF', 'DISBURSEMENT'));

-- 3. Retainer tables (child first; V31). retainer_agreements.schedule_id -> recurring_schedules is
--    why retainer is removed before schedule (Epic 600A). chk_retainer_* / chk_rollover_policy /
--    chk_period_status / uq_retainer_period_start and all idx_retainer_* go with the tables.
DROP TABLE IF EXISTS retainer_periods;
DROP TABLE IF EXISTS retainer_agreements;

-- 4. billing_runs.include_retainers (V63:16; BillingRun.includeRetainers removed in 598A.3).
ALTER TABLE billing_runs DROP COLUMN IF EXISTS include_retainers;

-- 5. email_delivery_log.reference_type: PortalEmailService's PORTAL_RETAINER sender is removed.
--    Delete its log rows first, otherwise ADD CONSTRAINT fails on any volume that ever sent a
--    portal retainer-period-closed email (same pattern as V137 §4).
DELETE FROM email_delivery_log WHERE reference_type = 'PORTAL_RETAINER';

--    Latest definition: V137:63-81, minus 'PORTAL_RETAINER' (order preserved).
ALTER TABLE email_delivery_log
    DROP CONSTRAINT IF EXISTS chk_email_delivery_reference_type;
ALTER TABLE email_delivery_log
    ADD CONSTRAINT chk_email_delivery_reference_type
    CHECK (reference_type IN (
        'NOTIFICATION',
        'INVOICE',
        'MAGIC_LINK',
        'TEST',
        'ACCEPTANCE_REQUEST',
        'INFORMATION_REQUEST',
        'PORTAL_DIGEST',
        'PORTAL_TRUST_ACTIVITY',
        'PORTAL_DEADLINE',
        'PORTAL_DOCUMENT_READY',
        'COLLECTION_REMINDER',
        'CASH_DIGEST'
    ));
