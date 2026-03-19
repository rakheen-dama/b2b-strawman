-- =============================================================================
-- V77: Add MANAGE_COMPLIANCE capability to owner and admin system roles
-- =============================================================================

-- Owner: add MANAGE_COMPLIANCE
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_COMPLIANCE'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_COMPLIANCE'
  );

-- Admin: add MANAGE_COMPLIANCE
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_COMPLIANCE'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_COMPLIANCE'
  );
