-- OBS-6002: Grant APPROVE_TRUST_PAYMENT to Admin role for Section 86 dual-approval
-- This INSERT targets *custom* (non-system) admin roles that read capabilities from
-- org_role_capabilities at runtime. System admin/owner roles resolve capabilities via
-- OrgRoleService.resolveCapabilities() in Java, governed by the Capability.OWNER_ONLY
-- set — the companion code change removes APPROVE_TRUST_PAYMENT from OWNER_ONLY.
-- Without BOTH changes, custom admin roles lack the capability in the DB, and system
-- admin roles lack it in code, creating a deadlock when the Owner is the recorder
-- (self-approval is blocked by design).
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'APPROVE_TRUST_PAYMENT'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'APPROVE_TRUST_PAYMENT'
  );
