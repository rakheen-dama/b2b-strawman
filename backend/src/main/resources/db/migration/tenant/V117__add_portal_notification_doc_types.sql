-- V117__add_portal_notification_doc_types.sql
-- GAP-L-72 (E5.1): per-tenant allowlist of generated-document template names
-- whose DocumentGeneratedEvent should trigger an immediate portal-contact email
-- (in addition to the weekly digest). Default covers the closure-pack letter and
-- the Statement of Account — the two document types currently surfaced on the
-- portal that warrant per-event notification.
--
-- Stored as JSONB list of strings to mirror the existing enabled_modules pattern
-- (OrgSettings.java:172). Empty list disables per-event sends entirely for the
-- tenant; the weekly digest (Epic 498B) continues to operate independently.
--
-- Also extend the email_delivery_log reference_type check constraint with the
-- new PORTAL_DOCUMENT_READY reference type emitted by
-- PortalEmailService#sendDocumentReadyEmail (mirrors V109 for the four
-- pre-existing PORTAL_* reference types).

ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS portal_notification_doc_types JSONB
        NOT NULL DEFAULT '["matter-closure-letter", "statement-of-account"]'::jsonb;

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
        'PORTAL_DOCUMENT_READY'
    ));
