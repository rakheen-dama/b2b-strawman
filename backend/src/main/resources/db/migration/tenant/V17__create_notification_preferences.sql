-- V17: Create notification_preferences table
-- Epic 61B — Per-member, per-type notification channel toggles (opt-out model)

CREATE TABLE IF NOT EXISTS notification_preferences (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           UUID         NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    notification_type   VARCHAR(50)  NOT NULL,
    in_app_enabled      BOOLEAN      NOT NULL DEFAULT true,
    email_enabled       BOOLEAN      NOT NULL DEFAULT false,
    tenant_id           VARCHAR(255),
    CONSTRAINT uq_notif_prefs_member_type_tenant UNIQUE (member_id, notification_type, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_notif_prefs_member
    ON notification_preferences (member_id);

CREATE INDEX IF NOT EXISTS idx_notif_prefs_tenant
    ON notification_preferences (tenant_id) WHERE tenant_id IS NOT NULL;

ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'notification_preferences_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY notification_preferences_tenant_isolation ON notification_preferences
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
