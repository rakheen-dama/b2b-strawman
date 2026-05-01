-- V119__add_portal_new_proposal_and_proposal_expired_reference_types.sql
-- OBS-AUDIT-N1: register two missing portal-email reference types in the
-- email_delivery_log CHECK constraint.
--
-- 1. PORTAL_NEW_PROPOSAL — added to PortalEmailService constants in PR #1233
--    (OBS-703) but never appended to the V117 constraint. Schema-code
--    consistency: the constraint should accept all reference types the
--    application declares. (Empirically the delivery-log path is not firing
--    for portal proposal sends in our integration test, so this is not
--    closing an active production bug — it's defensive correctness for
--    when/if the log path is wired up.)
--
-- 2. PORTAL_PROPOSAL_EXPIRED — new reference type for the portal-proposal-expired
--    email handler wired in this PR (OBS-AUDIT-N1).
--
-- Mirrors the V117 + V109 + V58 + V46 pattern: drop the old constraint and
-- re-add it with the additional values. Atomic within the migration — no
-- window where the table is unconstrained.
--
-- Both values added in one migration because they share a bug class
-- ("portal-email reference type registered in Java but missing from the SQL
-- CHECK constraint"); per Quality Gate rule #7 same-class-cluster exception.

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
        'PORTAL_PROPOSAL_EXPIRED'
    ));
