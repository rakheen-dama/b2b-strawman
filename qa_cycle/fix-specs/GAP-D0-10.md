# Fix Spec: GAP-D0-10 — Members see generic terminology (403 on /api/settings)

## Problem
Member-role users (e.g., Carol) see generic terminology throughout the app -- sidebar shows "Projects" instead of "Matters", org slug instead of display name, and legal nav items are missing. The browser console logs: `Failed to fetch org settings for terminology: Insufficient permissions for this operation`. The org layout (`layout.tsx` line 49-51) calls `getOrgSettings()` via `Promise.allSettled`, and when it rejects with 403, the fallback at lines 69-81 leaves `verticalProfile`, `enabledModules`, `terminologyNamespace`, and `orgName` all null/empty.

## Root Cause (confirmed)

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`, line 38-41:

```java
@GetMapping
@RequiresCapability("TEAM_OVERSIGHT")
public ResponseEntity<SettingsResponse> getSettings() {
    return ResponseEntity.ok(orgSettingsService.getSettingsWithBranding());
}
```

The `@RequiresCapability("TEAM_OVERSIGHT")` annotation on the GET endpoint blocks all member-role users. Per the migration `V67__create_org_role_tables.sql` (lines 70), the `member` system role has **zero capabilities** -- only `owner` and `admin` roles receive `TEAM_OVERSIGHT` (lines 53-68).

The `CapabilityAuthorizationManager` (line 40) calls `RequestScopes.hasCapability(annotation.value())`, which checks the `CAPABILITIES` ScopedValue bound by `MemberFilter`. For members, this set is empty, so the check fails and Spring returns 403.

The frontend layout at `frontend/app/(app)/org/[slug]/layout.tsx` lines 49-81 calls `getOrgSettings()` as part of `Promise.allSettled`. When the 403 response comes back, the `settingsResult.status` is `"rejected"`, so `verticalProfile`, `enabledModules`, `terminologyNamespace`, and `orgName` all fall back to null/empty. This cascades to:
- `TerminologyProvider` receives `verticalProfile: null` -- identity translation, no term mapping
- `OrgProfileProvider` receives empty `enabledModules` -- legal nav items hidden
- `DesktopSidebar` receives `orgName: null` -- falls back to slug

## Fix

The GET endpoint returns **read-only** configuration data (org name, currency, branding, terminology, modules). There is no security reason to restrict read access -- all org members need this data for correct UI rendering. Write operations (PUT, PATCH, POST, DELETE) should remain restricted to `TEAM_OVERSIGHT`.

### Step 1: Remove `@RequiresCapability` from the GET method

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`

Change lines 38-41 from:
```java
@GetMapping
@RequiresCapability("TEAM_OVERSIGHT")
public ResponseEntity<SettingsResponse> getSettings() {
```
To:
```java
@GetMapping
public ResponseEntity<SettingsResponse> getSettings() {
```

This is safe because:
1. The endpoint is already behind the JWT auth filter chain (unauthenticated users get 401).
2. The `TenantFilter` ensures the request is scoped to the correct tenant schema.
3. The `MemberFilter` ensures only synced org members reach this point.
4. All write endpoints (PUT, PATCH, POST logo, DELETE logo) retain `@RequiresCapability("TEAM_OVERSIGHT")`.
5. The `SettingsResponse` DTO contains no secrets -- it's org configuration data that every member needs to render the UI correctly.

### Step 2: Verify no sensitive data leaks

Review the `SettingsResponse` fields (file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/dto/SettingsResponse.java`). All 30+ fields are org-level configuration (currency, branding, modules, compliance thresholds, tax settings, etc.). None contain secrets, API keys, or PII beyond the org's own settings. The information officer name/email is an org role, not personal data in a privacy sense. **No field filtering needed.**

### Step 3 (no frontend changes needed)

The frontend layout already handles the settings response correctly when it succeeds. When the 403 goes away, the `settingsResult.status` will be `"fulfilled"`, and the existing code at lines 69-81 will extract `verticalProfile`, `enabledModules`, `terminologyNamespace`, and `orgName` correctly.

## Scope

- **Backend only**: 1 line change (remove annotation)
- No migration needed
- No frontend changes needed
- No new tests needed (existing `OrgSettingsIntegrationTest` should gain a member-access test case for regression)

## Files to Modify

| File | Change |
|------|--------|
| `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` | Remove `@RequiresCapability("TEAM_OVERSIGHT")` from line 39 (GET method only) |

## Optional: Add regression test

File: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsIntegrationTest.java`

Add a test that a member-role JWT can successfully GET `/api/settings` and receives a 200 with the expected fields. This prevents future regressions if someone re-adds the annotation.

```java
@Test
void getSettings_memberRole_returns200() throws Exception {
    mockMvc.perform(get("/api/settings")
            .with(jwt().jwt(TestJwtFactory.memberJwt(ORG_ID, "user_member"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").exists());
}
```

## Verification

1. Restart backend after the change.
2. Log in as Carol (member role) in the Keycloak dev stack.
3. **Expected**: Sidebar shows "Matters" (not "Projects"), legal nav items (Court Calendar, Conflict Check, Trust Accounting, Tariffs) are visible, org display name "Mathebula & Partners" appears instead of slug.
4. **Expected**: No console errors about "Insufficient permissions" for settings fetch.
5. Log in as Thandi (owner) -- confirm no regression, all settings pages still work.
6. Attempt to PUT `/api/settings` as Carol -- confirm 403 (write operations still restricted).

## Estimated Effort

XS (< 15 min). Single annotation removal + backend restart. No migration, no frontend changes.
