-- V74: Add DELETE trigger to audit_events for full append-only enforcement
-- Companion to V12's prevent_audit_update() — blocks DELETE as well as UPDATE

CREATE OR REPLACE FUNCTION prevent_audit_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events rows cannot be deleted';
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON t.tgrelid = c.oid
    JOIN pg_namespace n ON c.relnamespace = n.oid
    WHERE t.tgname = 'audit_events_no_delete'
      AND n.nspname = current_schema()
  ) THEN
    EXECUTE 'CREATE TRIGGER audit_events_no_delete
        BEFORE DELETE ON audit_events
        FOR EACH ROW EXECUTE FUNCTION prevent_audit_delete()';
  END IF;
END $$;
