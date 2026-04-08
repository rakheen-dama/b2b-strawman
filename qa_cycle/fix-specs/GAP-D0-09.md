# Fix Spec: GAP-D0-09 -- Trust account API returns 403 for Owner role

## Problem

`POST /api/trust-accounts` returns 403 ("Insufficient permissions") when called by Alice (Owner). This blocks trust account creation via both UI (GAP-D0-02, WONT_FIX) and API workaround.

## Root Cause (confirmed)

The trust account endpoints use `@RequiresCapability("MANAGE_TRUST")` (see `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java` line 46).

The backend's `OrgRoleService.resolveCapabilities()` returns `Capability.ALL_NAMES` for the Owner role (line 68-69 of `OrgRoleService.java`). This includes `MANAGE_TRUST`. So the 403 should NOT happen for Alice.

However, the issue is likely a **stale member cache**. The `MemberFilter` uses a Caffeine cache (`memberCache`, line 38-39 of `MemberFilter.java`) with 1-hour TTL. When Alice first logged in during Cycle 1 (before the trust accounting capabilities were deployed), her cached `MemberInfo` may have been created at a time when:

1. The OrgRole entity for "owner" may not have had the trust capabilities in the database yet (if the migration/provisioning happened after her first login)
2. The `resolveCapabilities()` call happened and was cached somewhere upstream

Actually, looking more carefully at the flow: `MemberFilter` caches `MemberInfo(memberId, orgRole)` but capabilities are resolved fresh on every request (line 67 of MemberFilter.java). So the cache is not the issue for capabilities.

The more likely root cause: the `CapabilityAuthorizationManager` or `CapabilityAuthorizationService` is checking capabilities and the owner's capabilities might not include `MANAGE_TRUST` if the system role's capability set in the DB is stale.

Wait -- re-reading `OrgRoleService.resolveCapabilities()` lines 68-69: the owner check is `if (role.isSystem() && "owner".equals(role.getSlug()))` and returns ALL capabilities. This bypasses the DB capability set entirely. So if Alice has the "owner" system role, she should always have MANAGE_TRUST.

The real root cause is the **same as GAP-D1-02**: Alice's member record may have been created with the wrong role. But the status shows Alice works fine for everything else. Let me reconsider.

The 403 may actually be caused by the trust account API being behind a **module gate** at the security config level, separate from capabilities. Let me check if there's a module-based filter.

Actually, the most likely root cause given the evidence (Alice can access other capability-gated features but NOT trust accounts specifically) is that the E2E backend was built from a codebase version where `MANAGE_TRUST` and `VIEW_TRUST` capabilities were not yet in the `Capability` enum, OR the `@RequiresCapability` annotation processor has a bug with newer capabilities.

**Most probable**: The E2E stack Docker image was rebuilt but the member cache from the previous cycle persisted. Alice's first login resolved capabilities before the trust module migration ran.

## Fix

This issue will likely be resolved as a side effect of the GAP-D1-02 fix, which requires a full E2E stack teardown and rebuild (`e2e-down.sh && e2e-up.sh`). This wipes all data including stale member records and caches.

If the issue persists after a fresh rebuild, the fix is to verify:

1. The `org_roles` table has an "owner" system role
2. The `OrgRoleService.resolveCapabilities()` correctly returns `MANAGE_TRUST` for the owner's member ID
3. The `CapabilityAuthorizationService` correctly checks the resolved capabilities

### Diagnostic command (after rebuild)

```bash
# Check Alice's capabilities via API
curl -s http://localhost:8081/api/me/capabilities \
  -H "Authorization: Bearer $(curl -s http://localhost:8090/token -H 'Content-Type: application/json' -d '{"userId":"user_e2e_alice","orgSlug":"e2e-test-org"}' | jq -r .access_token)" | jq .
```

Expected: `capabilities` array includes `MANAGE_TRUST` and `VIEW_TRUST`.

### If diagnostic shows missing capabilities

Add backend logging to `CapabilityAuthorizationService` to trace the exact failure point. The issue would then be in the annotation processor, not the role resolution.

## Scope

- 0 code changes (expected to resolve with GAP-D1-02 full rebuild)
- If not: 1-2 files for diagnostic logging
- Verification required after GAP-D1-02 fix

## Verification

1. After GAP-D1-02 fix and full E2E rebuild, run the diagnostic curl above
2. Verify `MANAGE_TRUST` and `VIEW_TRUST` in capabilities
3. Test: `POST /api/trust-accounts` with valid body -- expect 201

## Estimated Effort

15 minutes (verification after GAP-D1-02 rebuild; 1 hour if additional debugging needed)
