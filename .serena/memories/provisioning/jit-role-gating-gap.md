# JIT Provisioning Role-Gating — Potential Keycloak JWT Gap

## Context (Epic 271, PR #527)

JIT tenant provisioning in `TenantFilter` is **role-gated to `owner` and `admin`** only.
The check uses `ClerkJwtUtils.extractOrgRole(jwt)` which reads the Clerk-format `o.role` claim.

## The Gap

Keycloak JWT org role claims may use a **different claim structure** than Clerk:
- **Clerk format**: `o.role` → `"org:owner"`, `"org:admin"`, `"org:member"`
- **Keycloak format (ADR-138)**: Uses `organization` scope with roles in `organization.roles[]` or similar

When the Keycloak auth profile is active, `ClerkJwtUtils.extractOrgRole()` will read the wrong claim path. The role-gate will fail to extract the role, and JIT provisioning may:
1. **Silently skip** (if null role ≠ owner/admin) → members AND admins get 403
2. **Fail with NPE** → 500 for all requests to unprovisioned orgs

## What to Verify (in Epic 274 or 279)

1. When `JwtClaimExtractor` interface is introduced (from rolled-back Phase 35 ADR-142), the JIT role check must use it instead of `ClerkJwtUtils`
2. Keycloak org role format: confirm `organization.roles` claim structure in Keycloak 26.x with org scope
3. Role normalization: Clerk uses `org:owner`, Keycloak may use just `owner` — the comparison in TenantFilter uses `Roles.ORG_OWNER` (`"org:owner"`) which won't match raw Keycloak roles
4. Test with Keycloak JWTs: the `TenantFilterJitProvisioningTest` currently mocks Clerk-format claims

## Files Affected
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` — role extraction call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` — `extractOrgRole()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/Roles.java` — role constants

## Resolution Path
When `JwtClaimExtractor` strategy pattern is reintroduced (Phase 35 was rolled back), both `extractOrgRole()` and the role constants need profile-aware implementations. The JIT role-gate in TenantFilter should call the abstracted extractor, not `ClerkJwtUtils` directly.
