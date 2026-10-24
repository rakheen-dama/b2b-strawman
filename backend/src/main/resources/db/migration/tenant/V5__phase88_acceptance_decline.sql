-- =====================================================================================
-- Phase 88 — demo-ready root-cause closure, §6 U23 (acceptance decline).
-- V4 was applied to a persistent stack (Keycloak dev DB, 2026-10-24 09:01:53 SAST), so this
-- section takes a new file (OQ #9). Every DDL guard is scoped to current_schema(); every
-- statement is idempotent.
-- =====================================================================================

-- ---------- §6 U23: acceptance decline -------------------------------------------------
ALTER TABLE acceptance_requests
  ADD COLUMN IF NOT EXISTS declined_at timestamp with time zone,
  ADD COLUMN IF NOT EXISTS decline_reason text;
DO $$
BEGIN
  -- acceptance_requests never had a status CHECK (status was enforced in Java only); add one
  -- now that the vocabulary grows, scoped to this schema.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE c.conname = 'chk_acceptance_requests_status' AND n.nspname = current_schema()) THEN
    ALTER TABLE acceptance_requests ADD CONSTRAINT chk_acceptance_requests_status
      CHECK (status IN ('PENDING','SENT','VIEWED','ACCEPTED','DECLINED','EXPIRED','REVOKED'));
  END IF;
END $$;
-- idx_acceptance_requests_active_unique and _expiry are partial on (PENDING, SENT, VIEWED):
-- DECLINED falls outside them by construction, so a declined document can be re-sent.
