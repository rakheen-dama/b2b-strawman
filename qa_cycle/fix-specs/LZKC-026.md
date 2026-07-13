# Fix Spec: LZKC-026 — Features-page save silently drops profile-owned module ids (`deadlines` clobber)

## Problem
Carried forward (Medium): enabling Automation Rule Builder on Settings → Features silently removed the `deadlines` module from the tenant's enabled list — the portal Deadlines surface went dark as a side effect of an unrelated toggle. Any Features-page save reproduces it on a legal-za tenant.

## Root Cause (confirmed) — it is a BACKEND bug, not the frontend hypothesis
The carried-forward tracker text ("page reconstructs module list from its 4 known toggles → stale list posted") is **wrong about the layer**. The frontend contract is correct: the page fetches horizontal modules (`GET /api/settings/modules`, `frontend/lib/actions/module-settings.ts:32-34`), posts only the horizontal subset (`frontend/components/settings/features-settings-form.tsx:26-29`), and the API is documented as "fully replaces the horizontal subset; vertical modules are preserved" (`backend/.../settings/ModuleSettingsController.java:40-42`).

The real defect is the preserve-filter in `OrgSettingsService.updateHorizontalModules` — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java:471-486`:

```java
List<String> verticalIds =
    before.stream()
        .filter(id -> moduleRegistry.getModule(id)
            .map(m -> m.category() == ModuleCategory.VERTICAL)
            .orElse(false))   // ← unknown ids are DROPPED
        .toList();
```

It preserves only ids the registry classifies VERTICAL; ids **unknown to the registry fall through `.orElse(false)` and are silently dropped**. And `deadlines` is exactly such an id:
- `backend/src/main/resources/vertical-profiles/legal-za.json:5` seeds `enabledModules: [..., "deadlines", ...]` (accounting-za.json seeds it too).
- `VerticalModuleRegistry` (`backend/.../verticals/VerticalModuleRegistry.java:41-195`) has NO `deadlines` entry (only `regulatory_deadlines`).
- The slug is deliberately owned elsewhere: `backend/.../customerbackend/service/PortalDeadlineService.java:30-33` — `MODULE_ID = "deadlines"` with a comment stating "this slug is owned here, not in the registry".

So the architecture explicitly allows enabled ids outside the registry, but `updateHorizontalModules` assumes registry completeness. Every Features-page save on a legal-za or accounting-za tenant clobbers `deadlines`.

## Fix
Invert the preserve-filter from allowlist-VERTICAL to "preserve everything that is not a known HORIZONTAL module" (`OrgSettingsService.java:472-480`):

```java
List<String> preservedIds =
    before.stream()
        .filter(id -> moduleRegistry.getModule(id)
            .map(m -> m.category() != ModuleCategory.HORIZONTAL)
            .orElse(true))   // unknown ids are profile-owned — keep them
        .toList();
```

(Rename the local `verticalIds` → `preservedIds`; downstream merge logic at `:481-486` is unchanged.) This is the right layer: only the horizontal subset is replaceable via this endpoint, everything else — vertical AND registry-external profile-owned ids — is preserved. Registering `deadlines` in the registry instead would contradict the documented ownership comment in `PortalDeadlineService` and would still leave the endpoint fragile against the next registry-external slug.

Also repair the current tenant's data as part of verification (re-enable `deadlines` via the vertical-profile reconciliation path or a Features-independent API/UI action — NOT raw SQL; the reconciliation seeder `VerticalProfileReconciliationSeeder` restores profile modules on startup — confirm it does, and note the result).

## Scope
Backend only.
Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`.
Test: extend `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/ModuleSettingsIntegrationTest.java` — seed enabled modules containing a registry-unknown id (`deadlines`) + a vertical id + a horizontal id; PUT a changed horizontal set; assert the unknown id AND the vertical id survive, horizontal set replaced. Red-first on current main (reproduce-before-fix).
Migration needed: no.

## Verification
- New integration test red → green.
- Full `bash scripts/verify.sh`.
- Live: on the legal-za dev tenant, toggle Automation Rule Builder on/off on `/org/{slug}/settings/features` → `org_settings.enabled_modules` still contains `deadlines` (read-only DB check) and portal `/deadlines` stays reachable. Confirm restart-time reconciliation restored the currently-missing `deadlines` on the QA tenant, or restore via authorized product path.

## Estimated Effort
M (30 min – 2 hr)
