-- V17: Create notification_preferences table
-- Epic 61B — Per-member, per-type notification channel toggles (opt-out model)

CREATE TABLE IF NOT EXISTS notification_preferences (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           UUID         NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    notification_type   VARCHAR(50)  NOT NULL,
    in_app_enabled      BOOLEAN      NOT NULL DEFAULT true,
    email_enabled       BOOLEAN      NOT NULL DEFAULT false,
    CONSTRAINT uq_notif_prefs_member_type UNIQUE (member_id, notification_type)
);

CREATE INDEX IF NOT EXISTS idx_notif_prefs_member
    ON notification_preferences (member_id);
