# Fix Spec: GAP-D1-02 -- Bob (Admin) has degraded sidebar and page crashes

## Problem

When logged in as Bob (Admin role), the sidebar is missing most navigation groups: Clients, Finance, Court Calendar, Conflict Check, Adverse Parties, Resources, Recurring Schedules. Only Dashboard, My Work, Calendar, Projects (not "Matters"), and Team appear. The Conflict Check page crashes with "Something went wrong" and a 404 console error.

This means multi-user testing is impossible -- only Alice (Owner) has a functional legal-za experience.

## Root Cause (confirmed)

The sidebar filters items using `hasCapability()` from the CapabilityProvider (see `frontend/components/nav-zone.tsx` line 27-31). This calls `fetchMyCapabilities()` in the org layout (`frontend/app/(app)/org/[slug]/layout.tsx` line 49-50), which calls `GET /api/me/capabilities` on the backend.

The backend's `OrgRoleService.resolveCapabilities()` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` lines 57-95) resolves capabilities based on the member's `OrgRole` entity:
- **Owner** (line 68-69): Returns ALL capabilities (`Capability.ALL_NAMES`) -- this is why Alice works.
- **Admin** (line 71-74): Returns all capabilities EXCEPT `Capability.OWNER_ONLY` -- this should also work.
- **Custom roles / Member** (line 78-94): Returns only the capabilities explicitly assigned to the role entity.

The issue is in `MemberFilter.lazyCreateMember()` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` lines 139-218). When Bob first logs in:

1. Line 151: `effectiveRole` defaults to `Roles.ORG_MEMBER` (not "admin")
2. Line 154-166: Checks for a pending invitation. If no invitation exists for Bob's email, `effectiveRole` stays as "member"
3. Line 169-173: Only the FIRST member gets promoted to "owner"
4. Line 180-186: Looks up the system role by slug. Bob gets the "member" system role instead of "admin"

The mock IDP token does NOT include a role claim. The JWT payload (`compose/mock-idp/src/index.ts` lines 46-55) has `sub`, `iss`, `aud`, `iat`, `exp`, `organization`, `groups`, `email` -- but no `role` or equivalent claim. The backend has no way to know Bob should be "admin" from the JWT alone.

The E2E seed script (`compose/scripts/seed.sh` or equivalent) should sync members with their correct roles via the `/internal/members/sync` endpoint, but based on the status log, the seed Step 2 fails with a 404 on `/internal/orgs/plan-sync`, causing the script to exit early due to `set -eu`. This means Bob and Carol are never synced with their correct roles -- they get lazy-created as "member" on first login.

## Fix

Add a `role` claim to the mock IDP JWT and update the `MemberFilter.lazyCreateMember()` to read the role from the JWT when no invitation is found. This is the cleanest fix because:
- It matches what Keycloak does (includes role in token)
- It doesn't require the seed script to work
- It handles the case where the first login IS the member creation

### Changes

**File 1:** `compose/mock-idp/src/index.ts` -- Add role claim to JWT payload

In the `/token` endpoint (line 46), add the user's `defaultRole` to the JWT:

```typescript
const payload = {
  sub: userId,
  iss: "http://mock-idp:8090",
  aud: "docteams-e2e",
  iat: now,
  exp: now + 86400,
  organization: [orgSlug],
  groups: USER_GROUPS[userId] || [],
  email: user?.email || `${userId}@unknown.local`,
  name: user ? `${user.firstName} ${user.lastName}` : null,  // also fixes GAP-D0-08
  role: user?.defaultRole || "member",
};
```

**File 2:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` -- Read role from JWT

In `lazyCreateMember()`, after the invitation check (around line 166), add a fallback to read the role from the JWT:

```java
// If no invitation, check JWT for explicit role claim (mock-auth / Keycloak)
if (Roles.ORG_MEMBER.equals(effectiveRole)) {
  String jwtRole = jwt.getClaimAsString("role");
  if (jwtRole != null && !jwtRole.isBlank()) {
    effectiveRole = jwtRole;
    log.info("Using JWT role claim '{}' for user {}", jwtRole, clerkUserId);
  }
}
```

This must go BEFORE the "first member becomes owner" check (line 169) so that if Bob's JWT says "admin", he gets "admin", and the first-member-owner logic only fires when no role is specified.

## Scope

- 2 files: `compose/mock-idp/src/index.ts`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`
- ~10 lines total
- Requires rebuilding both mock-idp and backend Docker images
- No database migration needed (but stale member records in the E2E DB will have the wrong role; a full `e2e-down.sh && e2e-up.sh` is safest)

## Verification

1. Full E2E stack rebuild: `bash compose/scripts/e2e-down.sh && VERTICAL_PROFILE=legal-za bash compose/scripts/e2e-up.sh`
2. Login as Bob (Admin) at http://localhost:3001/mock-login
3. Sidebar should show ALL groups: Work (with Court Calendar), Projects, Clients (with Conflict Check, Adverse Parties), Finance (with Trust Accounting), Team (with Resources)
4. Navigate to Conflict Check page -- should load without crash
5. Navigate to Customers page -- should be accessible
6. Login as Carol (Member) -- should have appropriately limited navigation (based on member role capabilities)

## Estimated Effort

45 minutes (mock-idp change + MemberFilter change + full E2E rebuild + multi-user verification)
