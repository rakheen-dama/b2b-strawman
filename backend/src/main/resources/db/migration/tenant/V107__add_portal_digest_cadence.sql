-- V107__add_portal_digest_cadence.sql
-- Epic 498A: firm-wide cadence for portal digest emails (ADR-258). WEEKLY by default;
-- firms can switch to BIWEEKLY or disable entirely. Per-contact opt-out lives in the
-- portal.portal_notification_preference table (V22 global) — cadence is firm-level,
-- opt-out is contact-level.
ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS portal_digest_cadence VARCHAR(12)
        DEFAULT 'WEEKLY'
        CHECK (portal_digest_cadence IN ('WEEKLY','BIWEEKLY','OFF'));
