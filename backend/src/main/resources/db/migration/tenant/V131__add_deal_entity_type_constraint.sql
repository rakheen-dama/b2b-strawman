-- ============================================================
-- V131__add_deal_entity_type_constraint.sql  (Phase 80)
-- Make Deal a field-able entity. Mirrors V126's idempotent ALTER pattern.
-- EntityType is enforced at the Java enum boundary (fielddefinition/EntityType.java);
-- DEAL is added to the enum in 577A. No DB-level CHECK on field_groups.applicable_entity
-- exists in the tenant baseline (verified), so this migration is the column-existence
-- guard only — a no-op-safe ALTER keeping the field-group machinery consistent.
-- ============================================================

ALTER TABLE field_groups
    ADD COLUMN IF NOT EXISTS applicable_entity_values jsonb;

-- No named CHECK on field_groups.applicable_entity exists in the baseline; the
-- drop-and-recreate widening below is intentionally left commented (verified — do NOT uncomment):
-- ALTER TABLE field_groups DROP CONSTRAINT IF EXISTS ck_field_groups_applicable_entity;
-- ALTER TABLE field_groups ADD  CONSTRAINT ck_field_groups_applicable_entity
--     CHECK (applicable_entity IN ('PROJECT','TASK','CUSTOMER','INVOICE','DEAL'));
