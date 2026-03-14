-- Backfill org_role_id for any members with NULL org_role_id
UPDATE members m
SET org_role_id = (SELECT id FROM org_roles WHERE slug = m.org_role AND is_system = true)
WHERE m.org_role_id IS NULL;

-- Make org_role_id NOT NULL
ALTER TABLE members ALTER COLUMN org_role_id SET NOT NULL;

-- Drop the legacy VARCHAR column
ALTER TABLE members DROP COLUMN org_role;
