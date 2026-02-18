-- V14: Create audit_events table
-- Epic 50A — Audit event log for domain and security events (append-only)

CREATE TABLE IF NOT EXISTS audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       UUID         NOT NULL,
    actor_id        UUID,
    actor_type      VARCHAR(20)  NOT NULL DEFAULT 'USER',
    source          VARCHAR(30)  NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    details         JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL
);

-- Indexes for primary query patterns
CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON audit_events (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_actor
    ON audit_events (actor_id) WHERE actor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_occurred
    ON audit_events (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_type_time
    ON audit_events (event_type, occurred_at DESC);

-- Prevent updates (append-only enforcement)
CREATE OR REPLACE FUNCTION prevent_audit_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events rows cannot be updated';
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'audit_events_no_update') THEN
    EXECUTE 'CREATE TRIGGER audit_events_no_update
        BEFORE UPDATE ON audit_events
        FOR EACH ROW EXECUTE FUNCTION prevent_audit_update()';
  END IF;
END $$;
