-- V109__add_portal_notification_reference_types.sql
-- Epic 498B / Phase 68: extend the email_delivery_log reference_type check constraint
-- with the four PORTAL_* reference types emitted by PortalEmailService:
--   * PORTAL_DIGEST          — weekly/biweekly digest (PortalDigestScheduler)
--   * PORTAL_TRUST_ACTIVITY  — per-event trust transaction approval
--   * PORTAL_DEADLINE        — per-event field-date approaching
--   * PORTAL_RETAINER        — per-event retainer period rollover
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
        'PORTAL_RETAINER'
    ));
