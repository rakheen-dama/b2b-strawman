-- ============================================================
-- V112__add_field_group_applicable_work_types.sql
-- GAP-L-37-regression-2026-04-25: scope auto-apply to project.work_type
-- when a field group declares applicable_work_types. NULL/empty = unscoped.
--
-- Without this column, every legal-za tenant auto-attaches BOTH the
-- legal-za-project group AND the conveyancing-za-project group to every
-- matter regardless of work_type, because the legal-za vertical profile
-- bundles both packs and FieldGroupService.resolveAutoApplyGroupIds(EntityType)
-- has no per-project predicate.
-- ============================================================

ALTER TABLE field_groups
    ADD COLUMN IF NOT EXISTS applicable_work_types jsonb;

-- Backfill: scope the conveyancing project field group to CONVEYANCING work_type
-- so existing legal-za tenants (provisioned before this migration) stop
-- auto-attaching conveyancing fields to non-conveyancing matters created after
-- this migration. (Existing matters created before the migration retain their
-- already-attached groups in projects.applied_field_groups — this is by design;
-- backfilling those per-row would require a separate manual sweep.)
UPDATE field_groups
SET applicable_work_types = '["CONVEYANCING"]'::jsonb,
    updated_at = NOW()
WHERE pack_id = 'conveyancing-za-project'
  AND applicable_work_types IS NULL;
