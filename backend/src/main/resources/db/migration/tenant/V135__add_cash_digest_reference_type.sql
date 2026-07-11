-- V135__add_cash_digest_reference_type.sql
-- Phase 83 (Epic 593B, ADR-328): register the CASH_DIGEST email reference type in
-- the email_delivery_log CHECK constraint.
--
-- CashDigestService records every weekly-digest send with referenceType =
-- 'CASH_DIGEST' / referenceId = OrgSettings id (architecture §3.5). Without this
-- value the insert fails chk_email_delivery_reference_type — reproduced empirically
-- by CashDigestServiceTest before this migration existed. Same bug class as V134
-- (COLLECTION_REMINDER) and V119 ("reference type registered in Java but missing
-- from the SQL CHECK constraint").
--
-- Mirrors the V134 pattern: drop the old constraint and re-add it with the extra
-- value. Atomic within the migration — no window where the table is unconstrained.

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
        'PORTAL_RETAINER',
        'PORTAL_DOCUMENT_READY',
        'PORTAL_NEW_PROPOSAL',
        'PORTAL_PROPOSAL_EXPIRED',
        'COLLECTION_REMINDER',
        'CASH_DIGEST'
    ));
