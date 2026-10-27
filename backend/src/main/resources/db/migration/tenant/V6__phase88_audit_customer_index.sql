-- =====================================================================================
-- Phase 88 — 625C (U6): client Activity feed correlation key. ADR-349 makes customer_id a
-- publisher obligation alongside project_id; index it like idx_audit_project so the client
-- feed's three-branch OR (entity / child entities / details->>'customer_id') can BitmapOr.
-- Idempotent; the name is schema-local (CREATE INDEX IF NOT EXISTS resolves in current_schema()).
-- =====================================================================================
CREATE INDEX IF NOT EXISTS idx_audit_customer ON audit_events USING btree (((details ->> 'customer_id'::text)));
