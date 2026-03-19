-- =============================================================================
-- V78: Add MANAGE_COMPLIANCE_DESTRUCTIVE capability to owner system role only
-- Anonymization is irreversible — restricted to owner, not admin
-- =============================================================================

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_COMPLIANCE_DESTRUCTIVE'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_COMPLIANCE_DESTRUCTIVE'
  );
