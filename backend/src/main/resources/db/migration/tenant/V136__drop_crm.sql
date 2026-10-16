-- V136__drop_crm.sql
-- Phase 86 (Epic 596A, ADR-331): remove the Phase 80 CRM / sales-pipeline tables and the
-- deal-pipeline pack-status column. Throwaway migration — squashed into tenant V1 at
-- zero tenants (ADR-331); no production data exists.
--
-- Order matters only for the first two drops: deals.stage_id → pipeline_stages(id)
-- (fk_deal_stage, V130:46). deal_counters is standalone. All deals indexes go with the
-- table. proposals.deal_id (bare UUID, no FK — V130:64) and idx_proposals_deal stay until
-- V137 drops the proposals table (Epic 597A).

-- 1. Enum-string row cleanup BEFORE the Java constants disappear (595B §4).
--    org_role_capabilities.capability is @Enumerated(STRING) Set<Capability> (OrgRole.java:41-45).
--    V67 seeds no deal capability (only RESOURCE_PLANNING); this is defensive for dev volumes
--    whose custom roles carry one — without it that role's load would fail in MemberFilter.
DELETE FROM org_role_capabilities
 WHERE capability IN ('VIEW_DEALS', 'MANAGE_DEALS', 'CLOSE_DEALS', 'MANAGE_PIPELINE');

--    field_definitions.entity_type / field_groups.entity_type are @Enumerated(STRING) EntityType
--    (FieldDefinition.java:28-30, FieldGroup.java:25-27). No pack seeds DEAL; user-created only.
--    field_group_members FKs cascade (V22:57-58) — deleted explicitly first anyway.
DELETE FROM field_group_members
 WHERE field_group_id IN (SELECT id FROM field_groups WHERE entity_type = 'DEAL')
    OR field_definition_id IN (SELECT id FROM field_definitions WHERE entity_type = 'DEAL');
DELETE FROM field_definitions WHERE entity_type = 'DEAL';
DELETE FROM field_groups      WHERE entity_type = 'DEAL';

--    Free-string planes (not enum-mapped; V23:25, V26:10). Orphans once the deals table is
--    gone (ViewFilterService drops "deals" from ALLOWED_TABLES in the same PR), so clean them.
DELETE FROM entity_tags WHERE entity_type = 'DEAL';
DELETE FROM saved_views WHERE entity_type = 'DEAL';

-- 2. Tables (child first).
DROP TABLE IF EXISTS deals;
DROP TABLE IF EXISTS pipeline_stages;
DROP TABLE IF EXISTS deal_counters;

-- 3. Pack-status column (V130:69-70; PackStatusSettings.dealPipelinePackStatus removed in 596A.3).
ALTER TABLE org_settings DROP COLUMN IF EXISTS deal_pipeline_pack_status;
