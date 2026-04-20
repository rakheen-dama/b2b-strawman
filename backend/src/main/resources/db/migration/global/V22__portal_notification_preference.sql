-- V22__portal_notification_preference.sql
-- Epic 498A: per-portal-contact opt-outs for the five notification channels described in
-- requirements §5.2 / §5.3 (ADR-258). Cadence is firm-level (org_settings.portal_digest_cadence,
-- V107 tenant); per-contact opt-out lives here in the shared portal schema. No FK on
-- portal_contact_id because portal_contacts lives in per-tenant schemas — the same
-- cross-schema pattern as portal.portal_retainer_summary (V20).
CREATE TABLE IF NOT EXISTS portal.portal_notification_preference (
    portal_contact_id            UUID         PRIMARY KEY,
    digest_enabled               BOOLEAN      NOT NULL DEFAULT true,
    trust_activity_enabled       BOOLEAN      NOT NULL DEFAULT true,
    retainer_updates_enabled     BOOLEAN      NOT NULL DEFAULT true,
    deadline_reminders_enabled   BOOLEAN      NOT NULL DEFAULT true,
    action_required_enabled      BOOLEAN      NOT NULL DEFAULT true,
    last_updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
