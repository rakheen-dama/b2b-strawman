-- ============================================================
-- V126__add_field_group_applicable_entity_values.sql
-- OBS-5004: scope auto-apply to customer entity type value
-- when a field group declares applicable_entity_values. NULL/empty = unscoped.
--
-- Without this column, every accounting-za tenant auto-attaches BOTH the
-- accounting-za-customer-details group AND the accounting-za-customer-trust
-- group to every customer regardless of entity type, because
-- FieldGroupService.resolveAutoApplyGroupIds(EntityType) has no per-customer
-- predicate for entity type values.
-- ============================================================

ALTER TABLE field_groups
    ADD COLUMN IF NOT EXISTS applicable_entity_values jsonb;

-- Backfill: scope the trust customer field group to TRUST entity type
-- so existing accounting-za tenants stop auto-attaching trust fields to
-- non-trust customers created after this migration.
UPDATE field_groups
SET applicable_entity_values = '["TRUST"]'::jsonb,
    updated_at = NOW()
WHERE pack_id = 'accounting-za-customer-trust'
  AND applicable_entity_values IS NULL;
