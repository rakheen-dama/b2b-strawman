-- =============================================================================
-- V69: Member org_role column cleanup
-- Completes the migration from string org_role to org_role_id FK
-- =============================================================================

-- 1. Backfill any remaining NULL org_role_id values
UPDATE members
SET org_role_id = (
    SELECT id FROM org_roles
    WHERE slug = members.org_role AND is_system = true
    LIMIT 1
)
WHERE org_role_id IS NULL;

-- 2. Make org_role_id NOT NULL
ALTER TABLE members ALTER COLUMN org_role_id SET NOT NULL;

-- 3. Drop the legacy string column
ALTER TABLE members DROP COLUMN org_role;
