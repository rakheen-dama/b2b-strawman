-- V140__drop_capacity.sql
-- Phase 86 (Epic 599A, ADR-331 / ADR-332): remove Phase 38 resource planning — member capacities,
-- resource allocations, leave blocks, the firm-wide default weekly capacity column, the seeded
-- RESOURCE_PLANNING capability grants and the resource_planning module id. Throwaway migration —
-- squashed into tenant V1 at zero tenants (ADR-331); no production data exists.

-- 1. Enum-string row cleanup BEFORE the Java constant disappears (595B §4).
--    org_role_capabilities.capability is @Enumerated(STRING) Set<Capability> (OrgRole.java:41-45).
--    V67:57,66 seed RESOURCE_PLANNING for the owner and admin system roles. MANDATORY: without
--    this DELETE, OrgRoleService.resolveCapabilities throws inside MemberFilter (which then grants
--    an empty capability set → 403 on every capability-gated endpoint) and GET /api/org-roles fails.
DELETE FROM org_role_capabilities WHERE capability = 'RESOURCE_PLANNING';

-- 2. Tables (V62). Nothing references them — their only FKs point outward to members/projects
--    (ON DELETE CASCADE). Indexes, inline CHECKs and uq_allocation_member_project_week go with them.
DROP TABLE IF EXISTS resource_allocations;
DROP TABLE IF EXISTS leave_blocks;
DROP TABLE IF EXISTS member_capacities;

-- 3. org_settings.default_weekly_capacity_hours (V62:76; CapacitySettings embeddable removed in
--    599A.3). OrgSettingsSchemaSnapshotTest pin updated in the same PR (§11.7(f)).
ALTER TABLE org_settings DROP COLUMN IF EXISTS default_weekly_capacity_hours;

-- 4. Defensive: the resource_planning module id leaves VerticalModuleRegistry in the same PR.
--    V97 wrote it into enabled_modules for consulting-za tenants. enabled_modules is a jsonb string
--    list; unknown ids are tolerated at runtime but would keep surfacing in the session context.
--    (@> rather than the ? operator, which JDBC can mistake for a bind placeholder — V137/V139.)
UPDATE org_settings
   SET enabled_modules = enabled_modules - 'resource_planning'
 WHERE enabled_modules @> '["resource_planning"]'::jsonb;
