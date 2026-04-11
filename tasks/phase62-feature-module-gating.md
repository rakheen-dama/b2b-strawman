# Phase 62 — Feature Module Gating (Progressive Disclosure)

This phase extends the Phase 49 vertical module gating infrastructure to cover three horizontal (cross-vertical) power-user features: Resource Planning, Bulk Billing, and Automation Rule Builder. It adds a `ModuleCategory` enum, registers three new horizontal modules in the registry, exposes a toggle API (`GET/PUT /api/settings/modules`), adds service-layer guards to six services + one controller, wraps frontend nav items/pages/widgets in module gates, and builds a new Settings Features page for admin toggling.

**Architecture doc**: `architecture/phase62-feature-module-gating.md`

**ADRs**: [ADR-239](../adr/ADR-239-horizontal-vs-vertical-module-gating.md) (Horizontal vs. Vertical Module Gating — unified `enabled_modules` with category metadata)

**Dependencies on prior phases**:
- Phase 49 (Vertical Module System): `VerticalModuleRegistry`, `VerticalModuleGuard`, `ModuleGate`, `useOrgProfile()`, `OrgSettings.enabledModules`. This phase extends all of these.
- Phase 6 (Audit & Compliance): `AuditService` and `AuditEventBuilder`. Module toggle mutations publish audit events.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 470 | Module Registry Extension + Toggle API | Backend | -- | M | 470A, 470B | **Done** (PR #990) |
| 471 | Service-Layer Guards for Horizontal Modules | Backend | 470 | M | 471A | |
| 472 | Settings Features Page + Nav Gating | Frontend | 470 | M | 472A, 472B | |

---

## Dependency Graph

```
MODULE REGISTRY + TOGGLE API          SERVICE-LAYER GUARDS
(backend foundation)                  (backend enforcement)
──────────────────────                ──────────────────────

[E470A                                      
 ModuleCategory enum,                        
 extended ModuleDefinition,                  
 3 new horizontal modules,                   
 registry helper methods,                    
 unit tests]                                 
        |                                     
[E470B                                       
 OrgSettingsService.updateHorizontalModules()
 + getHorizontalModuleSettings(),            
 updateVerticalProfile() merge fix,          
 ModuleSettingsController + DTOs,            
 ModuleNotEnabledException message update,   
 MODULES_UPDATED audit event,               
 integration tests]                          
        |                                     
        +─────────────────────+               
        |                     |               
[E471A                 [E472A                 
 Service-layer guards   Settings Features page,
 on 6 services +        ModuleDisabledFallback,
 controller guard on    API client functions,  
 AutomationExecution,   settings-nav-groups    
 integration tests]     update, tests]         
                              |               
                       [E472B                 
                        Nav item gating       
                        (requiredModule on    
                        sidebar items),       
                        page-level ModuleGate 
                        wrappers, dashboard   
                        widget gating, tests] 
```

**Parallel opportunities**:
- E470A and E470B are sequential (470B depends on 470A for the extended registry).
- E471A depends on E470B (needs toggle API to set module state in tests).
- E472A depends on E470B (needs `GET/PUT /api/settings/modules` endpoints).
- E471A and E472A are independent and can run in parallel (Stage 1).
- E472B depends on E472A (needs `ModuleDisabledFallback` component).

---

## Implementation Order

### Stage 0: Backend Foundation (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 470 | 470A | `ModuleCategory` enum, extended `ModuleDefinition` with `category` field, 3 horizontal module registrations, registry helper methods, unit tests. Backend only. | **Done** (PR #990) |
| 0b | 470 | 470B | `updateHorizontalModules()` + `getHorizontalModuleSettings()` on `OrgSettingsService`, `updateVerticalProfile()` merge fix, `ModuleSettingsController` + DTOs, `ModuleNotEnabledException` message update, `MODULES_UPDATED` audit event, integration tests. Backend only. | **Done** (PR #990) |

### Stage 1: Guards + Settings Page (2 parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 471 | 471A | Service-layer guards on `ResourceAllocationService`, `CapacityService`, `UtilizationService`, `BillingRunService`, `AutomationRuleService` (CRUD only), `AutomationTemplateService` + controller-level guard on `AutomationExecutionController`. Integration tests. Backend only. | |
| 1b (parallel) | 472 | 472A | Settings Features page (`settings/features/page.tsx`), `ModuleDisabledFallback` component, API client functions (`module-settings.ts`), `settings-nav-groups.ts` "Features" group, tests. Frontend only. | |

### Stage 2: Frontend Gating (after 472A)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 472 | 472B | `requiredModule` additions to `nav-items.ts` for sidebar gating, page-level `ModuleGate` wrappers on ~10 pages, dashboard widget gating, tests. Frontend only. | |

---

## Epic 470: Module Registry Extension + Toggle API

**Goal**: Extend `VerticalModuleRegistry` with a `ModuleCategory` enum and three horizontal module definitions. Create the `GET/PUT /api/settings/modules` API with merge logic that preserves vertical modules during horizontal toggles and preserves horizontal modules during profile changes.

**References**: Architecture doc Sections 2 (Domain Model), 3 (Core Flows), 4 (API Surface), 7.1 (updateHorizontalModules), 7.3 (Audit Event), 7.4 (ModuleSettingsController). ADR-239 (merge rationale).

**Dependencies**: None (extends existing Phase 49 infrastructure).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **470A** | 470.1--470.5 | `ModuleCategory` enum, extended `ModuleDefinition` record, 3 horizontal module registrations, `getHorizontalModules()` + `getModulesByCategory()` helpers, updated unit tests. Backend only. ~5 files. | **Done** (PR #990) |
| **470B** | 470.6--470.14 | `ModuleSettingsController`, request/response DTOs, `OrgSettingsService.updateHorizontalModules()` + `getHorizontalModuleSettings()`, `updateVerticalProfile()` merge fix, `ModuleNotEnabledException` message update, `MODULES_UPDATED` audit event, integration tests. Backend only. ~10 files. | **Done** (PR #990) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 470.1 | Create `ModuleCategory` enum | 470A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/ModuleCategory.java`. Two values: `VERTICAL`, `HORIZONTAL`. Javadoc per architecture doc Section 2.1. |
| 470.2 | Add `category` field to `ModuleDefinition` record | 470A | 470.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java`. Add `ModuleCategory category` parameter to `ModuleDefinition` record (after `status`). All 5 existing modules receive `ModuleCategory.VERTICAL`. |
| 470.3 | Register 3 horizontal modules in `VerticalModuleRegistry` | 470A | 470.2 | Modify: `VerticalModuleRegistry.java`. Constructor must switch from `Map.of()` (max 10 entries) to a `HashMap` populated via `put()` calls — 8 entries exceeds the `Map.of` overload with 10 args. Add `resource_planning`, `bulk_billing`, `automation_builder` per architecture doc Section 2.3 table. All three get `ModuleCategory.HORIZONTAL` and empty `defaultEnabledFor`. |
| 470.4 | Add `getHorizontalModules()` and `getModulesByCategory()` helpers | 470A | 470.2 | Modify: `VerticalModuleRegistry.java`. Add `List<ModuleDefinition> getHorizontalModules()` filtering by `HORIZONTAL`. Add `List<ModuleDefinition> getModulesByCategory(ModuleCategory category)` as a general helper. |
| 470.5 | Update unit tests for registry | 470A | 470.3, 470.4 | Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistryTest.java`. Update `getAllModules_returnsFiveModulesWithCorrectIds` to expect 8 modules with all 8 IDs. Add test: `getHorizontalModules_returnsThreeHorizontalModules`. Add test: `getModule_resourcePlanningIsHorizontalWithCorrectNavItems`. Add test: `getModule_allExistingModulesAreVertical` (verifies the 5 original modules have `VERTICAL` category). ~4 test changes/additions. |
| 470.6 | Create `ModuleSettingsResponse` DTO | 470B | 470A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/dto/ModuleSettingsResponse.java`. Record with `List<ModuleStatus> modules` where `ModuleStatus` is an inner record: `String id, String name, String description, boolean enabled`. Pattern: existing response records in `settings/dto/`. |
| 470.7 | Create `UpdateModulesRequest` DTO | 470B | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/dto/UpdateModulesRequest.java`. Record with `@NotNull List<String> enabledModules`. Pattern: `CreateRuleRequest` in `automation/dto/`. |
| 470.8 | Implement `OrgSettingsService.getHorizontalModuleSettings()` | 470B | 470.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`. New method: reads `VerticalModuleRegistry.getHorizontalModules()`, cross-references with `getEnabledModulesForCurrentTenant()`, returns `ModuleSettingsResponse` with `enabled` boolean per module. Inject `VerticalModuleRegistry` into constructor. |
| 470.9 | Implement `OrgSettingsService.updateHorizontalModules()` | 470B | 470.7, 470.8 | Modify: `OrgSettingsService.java`. New transactional method per architecture doc Section 7.1. Calls `RequestScopes.requireAdminOrOwner()`. Validates all IDs exist in registry and are `HORIZONTAL`. Partitions current `enabledModules` into vertical/horizontal sets. Replaces horizontal set with request payload. Merges and persists. Publishes `MODULES_UPDATED` audit event with before/after/added/removed details. Returns `SettingsResponse`. |
| 470.10 | Fix `updateVerticalProfile()` to preserve horizontal modules | 470B | 470.9 | Modify: `OrgSettingsService.java`. In `updateVerticalProfile()` method (~line 660): before calling `settings.updateVerticalProfile()`, read current `enabledModules`, filter to horizontal IDs using `moduleRegistry.getModule(id).category == HORIZONTAL`, then concatenate profile's vertical modules with preserved horizontal modules. Pass the merged list to `settings.updateVerticalProfile()`. This is the highest-risk change per ADR-239 — must be covered by test 470.14. |
| 470.11 | Update `ModuleNotEnabledException` message | 470B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ModuleNotEnabledException.java`. Update message to: "This feature is not enabled for your organization. An admin can enable it in Settings -> Features." per architecture doc Section 3.2. |
| 470.12 | Create `ModuleSettingsController` | 470B | 470.8, 470.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/ModuleSettingsController.java`. `@RestController` with `@RequestMapping("/api/settings/modules")`. Two endpoints: `GET` (delegates to `getHorizontalModuleSettings()`), `PUT` with `@RequiresCapability("MANAGE_SETTINGS")` (delegates to `updateHorizontalModules()`). Each method is a one-liner per thin-controller rule. Pattern: `VerticalProfileController.java`. |
| 470.13 | Write integration tests: toggle API | 470B | 470.12 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/ModuleSettingsIntegrationTest.java`. Provision tenant, sync owner + member. Tests: (1) GET returns 3 horizontal modules with `enabled: false`; (2) PUT with valid horizontal IDs returns 200 + updated settings; (3) GET after PUT shows correct enabled state; (4) PUT with unknown module ID returns 400; (5) PUT with vertical module ID (e.g., `trust_accounting`) returns 400; (6) PUT with empty array disables all horizontal modules; (7) PUT verifies merge: enable horizontal, check vertical modules still present; (8) PUT as member (non-admin) returns 403; (9) MODULES_UPDATED audit event verified. ~9 tests. Uses `TestJwtFactory`, `TestMemberHelper`. |
| 470.14 | Write integration test: profile change preserves horizontal modules | 470B | 470.10 | In same test file `ModuleSettingsIntegrationTest.java`: (1) Enable `resource_planning` via PUT; (2) Change vertical profile to `legal-za` via existing profile API; (3) GET modules — assert `resource_planning` is still enabled; (4) GET settings — assert `enabledModules` contains both legal vertical modules AND `resource_planning`. This is the critical merge test per ADR-239. |

### Key Files

**Create:** `verticals/ModuleCategory.java`, `settings/dto/ModuleSettingsResponse.java`, `settings/dto/UpdateModulesRequest.java`, `settings/ModuleSettingsController.java`, `settings/ModuleSettingsIntegrationTest.java`

**Modify:** `verticals/VerticalModuleRegistry.java`, `settings/OrgSettingsService.java`, `exception/ModuleNotEnabledException.java`, `verticals/VerticalModuleRegistryTest.java`

### Architecture Decisions

- **ModuleCategory as enum, not boolean**: The category is a domain concept with distinct behaviors (vertical = profile-driven, hidden from Settings; horizontal = admin-driven, shown in Settings). An enum is explicit and extensible. See architecture doc Section 2.1.
- **Unified `enabled_modules` storage**: Per ADR-239, both vertical and horizontal module IDs are stored in the same JSONB column. The registry provides category metadata. Zero migrations needed.
- **Merge logic in two directions**: `updateHorizontalModules()` preserves vertical modules when replacing horizontal ones. `updateVerticalProfile()` is updated to preserve horizontal modules when replacing vertical ones. Both use the registry as the authoritative category boundary.
- **Constructor change to HashMap**: `VerticalModuleRegistry` currently uses `Map.of()` with 5 entries. With 8 entries, the `Map.of` factory exceeds its 10-parameter overload limit. Switch to `HashMap` with explicit `put()` calls.
- **Separate controller**: `ModuleSettingsController` is a new controller rather than adding endpoints to the existing `OrgSettingsController`, which is already a known violator of the thin-controller rule (see backend CLAUDE.md).

---

## Epic 471: Service-Layer Guards for Horizontal Modules

**Goal**: Add `moduleGuard.requireModule()` calls to the six services that back gated features, plus a controller-level guard on `AutomationExecutionController` for log-read endpoints. Validate that gated endpoints return 403 when the module is disabled and that automation execution continues running when the builder is disabled.

**References**: Architecture doc Section 7.2 (Service-Layer Guard Additions), Section 3.3 (Automation Execution Isolation), Section 8.3 (Code Patterns).

**Dependencies**: Epic 470 (needs registry with horizontal modules + toggle API for test setup).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **471A** | 471.1--471.9 | Service-layer guards on `ResourceAllocationService`, `CapacityService`, `UtilizationService`, `BillingRunService`, `AutomationRuleService` (CRUD only), `AutomationTemplateService` + controller-level guard on `AutomationExecutionController`. Integration tests. Backend only. ~10 files. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 471.1 | Add guard to `ResourceAllocationService` | 471A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationService.java`. Add `private static final String MODULE_ID = "resource_planning";` and inject `VerticalModuleGuard`. Add `moduleGuard.requireModule(MODULE_ID);` as first line of all public methods. Pattern: `TrustAccountService.java` (existing guard pattern). |
| 471.2 | Add guard to `CapacityService` | 471A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityService.java`. Same pattern as 471.1 with `MODULE_ID = "resource_planning"`. All public methods. |
| 471.3 | Add guard to `UtilizationService` | 471A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/UtilizationService.java`. Same pattern with `MODULE_ID = "resource_planning"`. All public methods. |
| 471.4 | Add guard to `BillingRunService` | 471A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java`. Add `private static final String MODULE_ID = "bulk_billing";` and inject `VerticalModuleGuard`. Add guard to all public methods. |
| 471.5 | Add guard to `AutomationRuleService` (CRUD only) | 471A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleService.java`. Add `private static final String MODULE_ID = "automation_builder";` and inject `VerticalModuleGuard`. Add guard to CRUD methods only: `createRule`, `updateRule`, `deleteRule`, `getRule`, `listRules`, `testRule`. Do NOT add guard to `executeRule`, `processTriggeredRules`, or any execution-engine methods. |
| 471.6 | Add guard to `AutomationTemplateService` | 471A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateService.java`. Add `private static final String MODULE_ID = "automation_builder";` and inject `VerticalModuleGuard`. Add guard to all public methods. |
| 471.7 | Add controller-level guard to `AutomationExecutionController` | 471A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecutionController.java`. Inject `VerticalModuleGuard`. Add `moduleGuard.requireModule("automation_builder");` as first line of all three endpoints (`list`, `get`, `listForRule`). Named exception to service-layer convention — `AutomationExecutionService` handles both log reads AND execution triggering. |
| 471.8 | Write integration tests: resource planning + billing run guards | 471A | 471.1, 471.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/ResourcePlanningModuleGuardTest.java`. Provision tenant. Tests: (1) GET `/api/resource-allocations` returns 403 when `resource_planning` disabled; (2) returns 200 when enabled; (3) GET `/api/utilization/team` returns 403 when disabled; (4) GET `/api/billing-runs` returns 403 when `bulk_billing` disabled; (5) returns 200 when enabled. Use toggle API from Epic 470 to set module state. ~5 tests. |
| 471.9 | Write integration tests: automation guards + execution isolation | 471A | 471.5, 471.6, 471.7 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationModuleGuardTest.java`. Provision tenant. Tests: (1) POST `/api/automation-rules` returns 403 when `automation_builder` disabled; (2) returns 200 when enabled; (3) GET `/api/automation-executions` returns 403 when disabled (controller guard); (4) GET `/api/automation-templates` returns 403 when disabled; (5) Automation execution engine continues running when `automation_builder` disabled — trigger a seeded rule event and verify execution record is created; (6) Returns 200 for rule CRUD when enabled. ~6 tests. |

### Key Files

**Modify:** `capacity/ResourceAllocationService.java`, `capacity/CapacityService.java`, `capacity/UtilizationService.java`, `billingrun/BillingRunService.java`, `automation/AutomationRuleService.java`, `automation/template/AutomationTemplateService.java`, `automation/AutomationExecutionController.java`

**Create:** `capacity/ResourcePlanningModuleGuardTest.java`, `automation/AutomationModuleGuardTest.java`

### Architecture Decisions

- **Service-layer guards**: All guards follow the established pattern from vertical modules (`TrustAccountService`, `ConflictCheckService`, etc.) — `MODULE_ID` constant + `moduleGuard.requireModule()` as first line of every public method. Guard is evaluated before any business logic or capability checks.
- **AutomationExecutionController exception**: Named exception to the service-layer rule. `AutomationExecutionService` handles both log reads AND execution triggering. Guard placed at controller level for log-read endpoints only. See architecture doc Section 3.3.
- **CRUD-only guard on AutomationRuleService**: Guard on `createRule`, `updateRule`, `deleteRule`, `getRule`, `listRules`, `testRule` but NOT on `executeRule`, `processTriggeredRules`, or execution log queries. Preserves seeded automation execution for all orgs.

---

## Epic 472: Settings Features Page + Nav/Page/Widget Gating

**Goal**: Build the Settings Features page where admins toggle horizontal modules, create the `ModuleDisabledFallback` component, add `requiredModule` declarations to sidebar nav items, wrap gated pages in `ModuleGate`, and gate dashboard widgets.

**References**: Architecture doc Section 6 (Frontend Changes), Section 8.2 (Frontend file list), Section 8.3 (Code Patterns). ADR-239 (no changes to `ModuleGate` component).

**Dependencies**: Epic 470 (needs `GET/PUT /api/settings/modules` endpoints).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **472A** | 472.1--472.7 | Settings Features page, `ModuleDisabledFallback` component, API client functions, `settings-nav-groups.ts` update, Vitest tests. Frontend only. ~8 files. | |
| **472B** | 472.8--472.14 | `requiredModule` additions on sidebar nav items, page-level `ModuleGate` wrappers on ~10 pages, dashboard widget gating, Vitest tests. Frontend only. ~12 files. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 472.1 | Create API client functions for module settings | 472A | -- | New file: `frontend/lib/actions/module-settings.ts`. Export `getModuleSettings(): Promise<ModuleSettingsResponse>` calling `apiGet("/api/settings/modules")`. Export `updateModuleSettings(enabledModules: string[]): Promise<OrgSettingsResponse>` calling `apiPut("/api/settings/modules", { enabledModules })`. Define `ModuleSettingsResponse` and `ModuleStatus` TypeScript interfaces. Pattern: `frontend/lib/actions/acceptance-actions.ts`. |
| 472.2 | Add "Features" group to `settings-nav-groups.ts` | 472A | -- | Modify: `frontend/components/settings/settings-nav-groups.ts`. Add a new group before the "Access & Integrations" group (position index 5, pushing "access" to index 6): `{ id: "features", label: "Features", items: [{ label: "Features", href: "features" }] }`. The item is NOT `adminOnly` — all members see the page. Non-admins see read-only state. |
| 472.3 | Create `ModuleDisabledFallback` component | 472A | -- | New file: `frontend/components/module-disabled-fallback.tsx`. Server component (no `"use client"`). Props: `{ moduleName: string }`. Renders: a centered card with message "This feature is not enabled for your organization." and a `Link` to Settings Features page (`/org/${slug}/settings/features`). Uses Shadcn `Card`, `CardContent`. Pattern: existing empty-state components. |
| 472.4 | Create Settings Features page | 472A | 472.1, 472.3 | New file: `frontend/app/(app)/org/[slug]/settings/features/page.tsx`. `"use client"` page. Fetches horizontal modules via `getModuleSettings()` using SWR. Renders each module as a Shadcn `Card` with: module `name` (bold), `description` (muted text), and a Shadcn `Switch` on the right. Toggle calls `updateModuleSettings()` with updated enabled list, then calls `router.refresh()` to revalidate `OrgProfileProvider`. Non-admin users see cards without the Switch (check `isAdmin` from `useAuth()`). Section header: "Features" with subtitle per architecture doc Section 6.4. Pattern: `frontend/app/(app)/org/[slug]/settings/batch-billing/page.tsx`. |
| 472.5 | Add "Features" entry to `SETTINGS_ITEMS` in `nav-items.ts` | 472A | -- | Modify: `frontend/lib/nav-items.ts`. Add to `SETTINGS_ITEMS` array: `{ title: "Features", description: "Enable additional features for your organization", href: (slug) => `/org/${slug}/settings/features` }`. Enables command palette search for the Features page. |
| 472.6 | Write Vitest test: Settings Features page | 472A | 472.4 | New file: `frontend/__tests__/settings-features.test.tsx`. Mock `getModuleSettings` to return 3 modules. Tests: (1) All 3 module cards render with correct names and descriptions; (2) Switch toggles call `updateModuleSettings` with correct payload; (3) Non-admin users see cards without Switch components; (4) After toggle, `router.refresh()` is called. ~4 tests. |
| 472.7 | Write Vitest test: ModuleDisabledFallback | 472A | 472.3 | New file: `frontend/__tests__/module-disabled-fallback.test.tsx`. Tests: (1) Renders module name in message; (2) Contains link to settings/features. ~2 tests. |
| 472.8 | Add `requiredModule` to resource planning nav items | 472B | -- | Modify: `frontend/lib/nav-items.ts`. In the "team" `NavGroup`, add `requiredModule: "resource_planning"` to the "Resources" item. Also add a "Utilization" nav item with `requiredModule: "resource_planning"`. The existing `NavZone` component already filters by `requiredModule` — no sidebar component changes needed. |
| 472.9 | Add `requiredModule` to billing runs nav item | 472B | -- | Modify: `frontend/lib/nav-items.ts`. In the "finance" `NavGroup`, add `requiredModule: "bulk_billing"` to the "Billing Runs" item. Also add `requiredModule: "bulk_billing"` to the "Batch Billing" entry in `SETTINGS_ITEMS`. |
| 472.10 | Add `requiredModule` to automations settings items | 472B | -- | Modify: `frontend/lib/nav-items.ts`. Add `requiredModule: "automation_builder"` to the "Automations" entry in `SETTINGS_ITEMS`. Modify: `frontend/components/settings/settings-nav-groups.ts`. Add `requiredModule: "automation_builder"` to the "Automations" item in the "work" group. Extend `SettingsNavItem` interface with optional `requiredModule?: string` field and update settings sidebar rendering to check `isModuleEnabled()`. |
| 472.11 | Wrap resource planning pages in `ModuleGate` | 472B | 472.3 | Modify: `frontend/app/(app)/org/[slug]/resources/page.tsx` — wrap page content in `<ModuleGate module="resource_planning" fallback={<ModuleDisabledFallback moduleName="Resource Planning" />}>`. Modify: `frontend/app/(app)/org/[slug]/resources/utilization/page.tsx` — same wrapping. Pattern: architecture doc Section 8.3 page-level gating pattern. |
| 472.12 | Wrap billing run and automation pages in `ModuleGate` | 472B | 472.3 | Modify: `frontend/app/(app)/org/[slug]/invoices/billing-runs/page.tsx`, `billing-runs/[id]/page.tsx`, `billing-runs/new/page.tsx` — wrap each in `<ModuleGate module="bulk_billing" fallback={<ModuleDisabledFallback moduleName="Bulk Billing Runs" />}>`. Modify: `frontend/app/(app)/org/[slug]/settings/automations/page.tsx`, `automations/[id]/page.tsx`, `automations/new/page.tsx`, `automations/executions/page.tsx` — wrap each in `<ModuleGate module="automation_builder" fallback={<ModuleDisabledFallback moduleName="Automation Rule Builder" />}>`. |
| 472.13 | Wrap dashboard widgets in `ModuleGate` | 472B | -- | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. If utilization, billing run, or automation execution widgets exist, wrap them in `<ModuleGate module="...">` with no fallback (widget silently disappears). Dashboard already uses `ModuleGate` for `regulatory_deadlines` and `court_calendar` widgets — follow the same pattern. |
| 472.14 | Write Vitest tests: nav gating + page gating | 472B | 472.8, 472.11 | New file: `frontend/__tests__/module-gating.test.tsx`. Tests: (1) Resources nav item hidden when `resource_planning` disabled; (2) Resources nav item visible when enabled; (3) Billing Runs nav item hidden when `bulk_billing` disabled; (4) Automations settings item hidden when `automation_builder` disabled; (5) Resource planning page shows fallback when disabled; (6) Resource planning page shows content when enabled. ~6 tests. Mock `useOrgProfile` with controlled `isModuleEnabled`. |

### Key Files

**Create:** `lib/actions/module-settings.ts`, `components/module-disabled-fallback.tsx`, `app/(app)/org/[slug]/settings/features/page.tsx`, `__tests__/settings-features.test.tsx`, `__tests__/module-disabled-fallback.test.tsx`, `__tests__/module-gating.test.tsx`

**Modify:** `components/settings/settings-nav-groups.ts`, `lib/nav-items.ts`, `app/(app)/org/[slug]/resources/page.tsx`, `app/(app)/org/[slug]/resources/utilization/page.tsx`, `app/(app)/org/[slug]/invoices/billing-runs/page.tsx`, `app/(app)/org/[slug]/invoices/billing-runs/[id]/page.tsx`, `app/(app)/org/[slug]/invoices/billing-runs/new/page.tsx`, `app/(app)/org/[slug]/settings/automations/page.tsx`, `app/(app)/org/[slug]/settings/automations/[id]/page.tsx`, `app/(app)/org/[slug]/settings/automations/new/page.tsx`, `app/(app)/org/[slug]/settings/automations/executions/page.tsx`, `app/(app)/org/[slug]/dashboard/page.tsx`

### Architecture Decisions

- **`requiredModule` on nav items, not `ModuleGate` wrapper**: The `NavItem` interface already supports `requiredModule` (added in Phase 49), and `NavZone` already filters by it. Data-driven approach — add the property to the nav item definition and the sidebar automatically hides it.
- **`ModuleDisabledFallback` is a Server Component**: No `"use client"` needed — renders static content with a Link. The `ModuleGate` wrapper (client component) handles conditional rendering.
- **Settings Features page is `"use client"`**: Needs SWR for data fetching, `useAuth()` for role-based Switch visibility, and event handlers for toggle interactions.
- **No changes to `ModuleGate` component**: Per ADR-239, the existing `ModuleGate` component works unchanged. It checks `isModuleEnabled()` against the same `enabledModules` array, which now contains both vertical and horizontal module IDs.
- **Settings sidebar `requiredModule` support**: The `SettingsNavItem` interface needs extending to support `requiredModule` filtering, matching what `NavItem`/`NavZone` already does for the main sidebar.
- **Dashboard widget gating pattern**: Matches existing `ModuleGate` usage on the dashboard (e.g., `<ModuleGate module="regulatory_deadlines"><DeadlineWidget /></ModuleGate>`). No fallback — widgets silently disappear, CSS grid adjusts naturally.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `updateVerticalProfile()` merge fix wipes horizontal modules | Medium | High | Integration test 470.14 explicitly covers this path. |
| `VerticalModuleRegistry` constructor exceeds `Map.of()` limit | Certain | Medium | Task 470.3 explicitly notes the switch to `HashMap`. |
| `AutomationRuleService` guard blocks execution engine | Medium | High | Task 471.5 lists which methods get guarded (CRUD only). Test 471.9 verifies execution isolation. |
| Settings sidebar does not support `requiredModule` filtering | Low | Medium | Task 472.10 extends `SettingsNavItem` interface and rendering. |
| Existing tests break due to `ModuleDefinition` record change | Medium | Low | Task 470.5 updates `VerticalModuleRegistryTest.java`. |
| SWR cache on Features page shows stale toggle state | Low | Low | Task 472.4 calls `router.refresh()` + SWR `mutate()` for immediate UI update. |
| Non-admin users can call PUT module settings | Low | High | `@RequiresCapability("MANAGE_SETTINGS")` on controller + `RequestScopes.requireAdminOrOwner()` in service. Test 470.13 case 8 verifies 403. |

---

## Test Summary

| Epic | Slice | New Tests | Coverage |
|------|-------|-----------|----------|
| 470 | 470A | ~4 | Registry: 8 modules count, 3 horizontal modules, resource_planning metadata, existing modules remain VERTICAL |
| 470 | 470B | ~10 | Toggle API: GET returns 3 modules, PUT valid/invalid/vertical/empty, merge correctness, non-admin 403, audit event, profile change preserves horizontal |
| 471 | 471A | ~11 | Guards: resource_planning 403/200, utilization 403, bulk_billing 403/200, automation_builder rule CRUD 403/200, execution log 403, execution engine isolation |
| 472 | 472A | ~6 | Features page: 3 cards render, toggle calls API, non-admin read-only, router refresh. Fallback: renders name, has link |
| 472 | 472B | ~6 | Nav gating: resources hidden/visible, billing runs hidden, automations hidden. Page gating: fallback/content rendering |
| **Total** | | **~37 tests** | Full module registry, toggle API, service guards, execution isolation, frontend settings page, nav/page/widget gating |
