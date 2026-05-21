-- OBS-6002: Grant APPROVE_TRUST_PAYMENT to Admin role for Section 86 dual-approval
-- Without this, only Owners can approve trust payments — creating a deadlock when
-- the Owner is also the recorder (self-approval is blocked by design).
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'APPROVE_TRUST_PAYMENT'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'APPROVE_TRUST_PAYMENT'
  );
