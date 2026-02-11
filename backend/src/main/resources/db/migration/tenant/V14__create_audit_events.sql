-- V14: Create audit_events table
-- Epic 50A — Audit infrastructure for compliance and traceability

CREATE TABLE IF NOT EXISTS audit_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type    VARCHAR(100) NOT NULL,
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     UUID NOT NULL,
    actor_id      UUID,
    actor_type    VARCHAR(20) NOT NULL DEFAULT 'USER',
    source        VARCHAR(30) NOT NULL,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500),
    details       JSONB,
    tenant_id     VARCHAR(255),
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON audit_events (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_actor
    ON audit_events (actor_id) WHERE actor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_occurred
    ON audit_events (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_type_time
    ON audit_events (event_type, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_time
    ON audit_events (tenant_id, occurred_at DESC);

-- Append-only trigger: prevent updates to audit_events
CREATE OR REPLACE FUNCTION prevent_audit_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_events table is append-only — updates are not permitted';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_events_no_update ON audit_events;
CREATE TRIGGER audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_update();

-- Row-Level Security
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'audit_events_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY audit_events_tenant_isolation ON audit_events
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
