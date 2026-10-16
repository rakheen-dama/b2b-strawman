-- V139__drop_portal_retainer_member_display.sql
-- Phase 86 (Epic 598B, ADR-331): drop the portal retainer member-display privacy toggle (V105,
-- ADR-255) — its only reader (customerbackend PortalRetainerMemberDisplayResolver) was deleted by
-- 598A. The inline CHECK added by V105 goes with the column. Throwaway migration — squashed into
-- tenant V1 (ADR-331). OrgSettingsSchemaSnapshotTest pin updated in the same PR (§11.7(f)).
ALTER TABLE org_settings DROP COLUMN IF EXISTS portal_retainer_member_display;

-- Defensive: the retainer_agreements module id leaves VerticalModuleRegistry in the same PR.
-- enabled_modules is a jsonb string list; unknown ids are tolerated at runtime but would keep
-- surfacing in the portal session context. (@> rather than the ? operator, which JDBC can
-- mistake for a bind placeholder — V137 precedent.)
UPDATE org_settings
   SET enabled_modules = enabled_modules - 'retainer_agreements'
 WHERE enabled_modules @> '["retainer_agreements"]'::jsonb;
