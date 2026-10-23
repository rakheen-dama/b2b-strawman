-- =====================================================================================
-- Phase 88 — demo-ready root-cause closure. One section per unit.
-- Sections are appended to V4 only while no persistent stack has applied it; after that
-- (Keycloak dev DB, E6 re-walk tenant, VPS) later sections take V5, V6, … (§13.7 rule).
-- Every DDL guard is scoped to current_schema(); every DML is idempotent.
-- =====================================================================================

-- ---------- §1 U1: canonical project roles ------------------------------------------
-- project_role was a free varchar; template instantiation wrote 'LEAD' and the demo seeder
-- 'CONTRIBUTOR' while thirteen readers and the one-lead partial index expect 'lead'/'member'.
-- Order matters (ADR-345 rationale 4): the partial unique index is lowercase-only, so a matter
-- may hold a 'lead' (ProjectService) and a 'LEAD' (template) at once. Lower-casing first would
-- violate the index. So: (a) map strays, (b) demote duplicate leads case-insensitively keeping
-- the earliest (created_at, id) — the creator or chosen lead, (c) only then lower-case, (d) CHECK.

-- (a) anything outside lead/member (e.g. the demo seeder's 'CONTRIBUTOR') becomes member.
UPDATE project_members
SET project_role = 'member'
WHERE lower(project_role) NOT IN ('lead', 'member');

-- (b) demote duplicate leads, comparing case-insensitively, before any row is lower-cased.
UPDATE project_members pm
SET project_role = 'member'
WHERE upper(pm.project_role) = 'LEAD'
  AND EXISTS (
    SELECT 1 FROM project_members o
    WHERE o.project_id = pm.project_id
      AND o.id <> pm.id
      AND upper(o.project_role) = 'LEAD'
      AND (o.created_at, o.id) < (pm.created_at, pm.id));

-- (c) now at most one lead per matter remains in any casing; lower-case cannot collide.
UPDATE project_members
SET project_role = lower(project_role)
WHERE project_role <> lower(project_role);

-- (d) the CHECK.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
    WHERE c.conname = 'chk_project_members_role' AND n.nspname = current_schema()) THEN
    ALTER TABLE project_members
      ADD CONSTRAINT chk_project_members_role CHECK (project_role IN ('lead', 'member'));
  END IF;
END $$;
