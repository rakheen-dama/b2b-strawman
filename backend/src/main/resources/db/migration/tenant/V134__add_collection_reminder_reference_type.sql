-- V134__add_collection_reminder_reference_type.sql
-- Phase 83 (Epic 590B, ADR-326): register the COLLECTION_REMINDER email reference
-- type in the email_delivery_log CHECK constraint.
--
-- CollectionReminderSendService records every reminder send attempt with
-- referenceType = 'COLLECTION_REMINDER' / referenceId = collection_activity id
-- (architecture §3.2). Without this value the insert fails the constraint —
-- reproduced empirically by SendCollectionReminderExecutorTest before this
-- migration existed. Same bug class as V119 ("reference type registered in Java
-- but missing from the SQL CHECK constraint").
--
-- Mirrors the V119 + V117 + V109 + V58 + V46 pattern: drop the old constraint and
-- re-add it with the additional value. Atomic within the migration — no window
-- where the table is unconstrained.

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
        'COLLECTION_REMINDER'
    ));
