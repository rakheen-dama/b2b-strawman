# Fix Spec: GAP-D0-09 — Fix industry-to-profile key mismatch (v2)

## Problem
QA Turn 3 clean re-run (0 stale KC orgs, pristine DB) proved: after admin approves an access request with `industry = "Legal Services"`, the provisioned tenant has empty `vertical_profile`, `default_currency = USD`, empty `terminology_namespace`, `enabled_modules = []`. Dashboard shows generic "Projects" instead of "Matters". No legal-za field packs, template packs, compliance packs, or modules. Only common/generic packs are seeded.

## Root Cause (confirmed)

The vertical profile wiring DOES exist end-to-end:

1. `AccessRequestApprovalService.approve()` (line 105) resolves the vertical profile:
   ```java
   String verticalProfile = INDUSTRY_TO_PROFILE.get(request.getIndustry());
   ```
2. Passes it to `TenantProvisioningService.provisionTenant(slug, orgName, verticalProfile)` (line 106).
3. `TenantProvisioningService.provisionTenant()` (line 129) calls `setVerticalProfile(schemaName, clerkOrgId, verticalProfile)` when non-null.
4. `setVerticalProfile()` (lines 193-224) correctly reads the `VerticalProfileRegistry`, sets `vertical_profile`, `enabled_modules`, `terminology_namespace`, and `currency` on `OrgSettings`.
5. All pack seeders (`AbstractPackSeeder.doSeedPacks()`, lines 119-129) filter packs by comparing `packProfile` against `settings.getVerticalProfile()` — if vertical profile is null, vertical-specific packs are skipped.

**The bug is a key mismatch.** GAP-D0-05 (PR #1016, commit `cab293bd`) renamed the frontend industry label from `"Legal"` to `"Legal Services"` in `frontend/lib/access-request-data.ts`. This value is stored as-is in `access_requests.industry`. But the backend map was not updated:

```java
// AccessRequestApprovalService.java lines 29-32
private static final Map<String, String> INDUSTRY_TO_PROFILE =
    Map.of(
        "Accounting", "accounting-za",    // Frontend sends "Accounting" — matches
        "Legal", "legal-za");             // Frontend sends "Legal Services" — NO MATCH
```

`INDUSTRY_TO_PROFILE.get("Legal Services")` returns `null`. So `verticalProfile` is `null`, `setVerticalProfile()` is never called, and all vertical-specific packs are skipped by the `AbstractPackSeeder` profile filter.

## Fix

### Step 1: Update `INDUSTRY_TO_PROFILE` map key

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java`
**Line**: 31

Change:
```java
"Legal", "legal-za"
```
To:
```java
"Legal Services", "legal-za"
```

This aligns the backend key with the frontend industry label that gets stored in the database.

### That's it.

The entire vertical profile wiring chain is already correct:
- `AccessRequestApprovalService.approve()` line 105-106: resolves profile and passes to provisioning
- `TenantProvisioningService.provisionTenant()` line 129-131: calls `setVerticalProfile()` when non-null
- `TenantProvisioningService.setVerticalProfile()` lines 193-224: reads `VerticalProfileRegistry`, applies `enabledModules`, `terminologyNamespace`, `currency`
- `VerticalProfileRegistry` loads `legal-za.json` from `classpath:vertical-profiles/legal-za.json`
- `AbstractPackSeeder.doSeedPacks()` lines 119-129: filters packs by `settings.getVerticalProfile()`

The only thing broken is the single map key.

## Scope
- Backend only
- **Files to modify**: 1
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java` (line 31, one word change)
- **Files to create**: none
- **Migration needed**: no

## Verification
Re-run QA Day 0 from CP 0.14 (approve access request with `industry = "Legal Services"`). After approval + registration + first login:
- `org_settings.vertical_profile = "legal-za"`
- `org_settings.default_currency = "ZAR"`
- `org_settings.terminology_namespace = "en-ZA-legal"`
- `org_settings.enabled_modules = ["court_calendar", "conflict_check", "lssa_tariff", "trust_accounting"]`
- Dashboard shows "Matters" (not "Projects")
- Sidebar has Court Calendar, Trust Accounting, Conflict Checks, LSSA Tariff modules
- Vertical-specific packs seeded: `legal-za-customer` field pack, `legal-za-project` field pack, `fica-kyc-za` compliance pack, `legal-za` template pack

## Estimated Effort
**S** (Small) — one word change in one file. 5 minutes including test verification.

## Priority
HIGH blocker — QA cannot advance past Day 0 Phase B without this. Every subsequent checkpoint depends on the legal-za vertical profile being active.

## Post-Mortem Note
This is a classic case of a "cosmetic" fix (GAP-D0-05) having an invisible downstream dependency. The industry label serves double duty: UI display AND backend lookup key. The GAP-D0-05 fix changed the display value without realizing the backend depended on the exact string. A future hardening epic should decouple the display label from the lookup key (e.g., use a stable enum/slug like `LEGAL` as the stored value, with the display label living only in the frontend).
