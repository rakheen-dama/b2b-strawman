-- V91: Deactivate legacy legal-za-onboarding compliance pack + backfill project.customer_id.
--
-- Follow-up to PR #996 (GAP-S4-02) which renamed the monolithic `legal-za-onboarding`
-- compliance pack into two customer-type-specific variants:
--   - legal-za-individual-onboarding (customer_type = INDIVIDUAL)
--   - legal-za-trust-onboarding      (customer_type = TRUST)
--
-- AbstractPackSeeder.isPackAlreadyApplied() only keys off packId, so existing legal-za
-- tenants would:
--   1. Retain the legacy `pack_id = 'legal-za-onboarding'` checklist template with
--      customer_type = 'ANY' (active) left over from before the rename.
--   2. Also get both new packs applied on next startup, because the new packIds are
--      absent from compliance_pack_status.
-- The net effect is that every new customer on a legacy legal-za tenant would end up with
-- BOTH the legacy "ANY" checklist AND the new INDIVIDUAL/TRUST checklist — duplicates.
--
-- This migration is idempotent (safe to run multiple times).
-- It also backfills projects.customer_id for rows created before the schema gained the FK
-- (GAP-S5-03 aftermath), using the oldest customer_projects link as the source of truth.

-- 1. Deactivate legacy legal-za-onboarding checklist templates.
--    Templates are soft-deleted via the `active` flag; we leave the row in place so any
--    existing checklist_instances still have a valid foreign key target.
UPDATE checklist_templates
SET active = false,
    updated_at = now()
WHERE pack_id = 'legal-za-onboarding'
  AND active = true;

-- 2. Remove the legacy pack entry from org_settings.compliance_pack_status.
--    The column is a JSONB array of objects like {"packId":"...","version":"...","appliedAt":"..."}.
--    We use jsonb_path_query_array to filter out entries matching packId 'legal-za-onboarding'.
--    Idempotent: if the column is NULL or the entry is already absent, this is a no-op.
UPDATE org_settings
SET compliance_pack_status = (
      SELECT COALESCE(
        jsonb_agg(elem),
        '[]'::jsonb
      )
      FROM jsonb_array_elements(compliance_pack_status) AS elem
      WHERE elem->>'packId' IS DISTINCT FROM 'legal-za-onboarding'
    ),
    updated_at = now()
WHERE compliance_pack_status IS NOT NULL
  AND compliance_pack_status @> '[{"packId": "legal-za-onboarding"}]'::jsonb;

-- 3. Backfill projects.customer_id for rows created before the structural FK was added
--    (GAP-S5-03). Pick the oldest customer_projects link deterministically so behaviour
--    is reproducible even in the (degenerate) many-customers-per-project case.
UPDATE projects p
SET customer_id = sub.customer_id
FROM (
    SELECT DISTINCT ON (project_id) project_id, customer_id
    FROM customer_projects
    ORDER BY project_id, created_at ASC, id ASC
) AS sub
WHERE p.customer_id IS NULL
  AND p.id = sub.project_id;
