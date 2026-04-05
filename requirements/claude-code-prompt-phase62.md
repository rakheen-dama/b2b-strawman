# Phase 62 — Feature Module Gating (Progressive Disclosure)

## System Context

After 61 phases of feature depth, the product surface area is large. New orgs — especially small 3-5 person firms — see every feature at once: resource planning, bulk billing runs, automation rule builders, alongside the core workflow they actually need. This is an onboarding risk. Complex power-user features where incorrect configuration can lead to bad outcomes (wrong invoices, wrong utilization numbers, runaway automations) should be hidden by default and one toggle away in Settings.

**The existing infrastructure** (Phase 49):

- **`VerticalModuleRegistry`** (`verticals/VerticalModuleRegistry.java`) — Static registry of module definitions. Each module has: `id`, `name`, `description`, `status`, `defaultEnabledFor` (list of vertical profiles), `navItems`.
- **`VerticalModuleGuard`** (`verticals/VerticalModuleGuard.java`) — Backend guard. `requireModule(moduleId)` throws `ModuleNotEnabledException` if module not in `OrgSettings.enabled_modules`. Used in controllers.
- **`OrgSettings.enabledModules`** — JSONB `List<String>` on `org_settings` table. Updated via `OrgSettingsService.updateVerticalProfile()` during provisioning.
- **`VerticalProfileRegistry`** — Loads profiles from `vertical-profiles/*.json`. Each profile declares `enabledModules` list.
- **`ModuleGate`** (`components/module-gate.tsx`) — Frontend gating component. Wraps children; renders nothing (or fallback) if module not enabled.
- **`useOrgProfile()`** (`lib/org-profile.tsx`) — React hook exposing `isModuleEnabled(moduleId)`.
- **`OrgProfileProvider`** — Wraps org layout, fetches `enabledModules` from `OrgSettings`.

**Currently registered modules** (all vertical-specific):
- `trust_accounting` — legal-za
- `court_calendar` — legal-za
- `conflict_check` — legal-za
- `lssa_tariff` — legal-za
- `regulatory_deadlines` — accounting-za

Phase 49's module system was built for vertical features, but the infrastructure is generic. This phase extends it to gate horizontal power-user features.

## Objective

Register 3 new horizontal feature modules, all disabled by default for every vertical profile. Add a "Features" section in the existing Settings layout where org admins can toggle these on/off. Wrap the corresponding frontend pages, nav items, and dashboard widgets in `ModuleGate`. Guard the corresponding backend controllers with `VerticalModuleGuard`.

**Design principle**: Hide power-user features where a new user clicking around could misconfigure something with real consequences. Core workflow (projects, customers, tasks, time, invoicing, documents, rate cards, retainers, budgets, profitability, reporting) stays always visible.

## Constraints & Assumptions

- **No new entities or migrations** — `OrgSettings.enabled_modules` already supports arbitrary module IDs. No schema changes needed.
- **No nav restructuring** — Features don't move locations. They're simply shown or hidden in place.
- **Seeded automations keep running** — The `automation_builder` module gates only the Rule Builder UI and CRUD API. The automation execution engine (`AutomationExecutionService`, trigger matching, action executors) continues to run seeded automation packs for all orgs regardless of module status. This is critical — orgs benefit from seeded automations (e.g., "task assigned → notify assignee") without needing to see or understand the builder.
- **No per-profile defaults for these modules** — All 3 are OFF for every profile (legal-za, accounting-za, consulting-generic). Unlike vertical modules which auto-enable per profile, these are purely opt-in via Settings.
- **Existing orgs** — Orgs that were provisioned before this phase won't have these module IDs in their `enabled_modules` array, which means they'll be OFF by default. This is the correct behavior — existing orgs discover the toggles in Settings when they're ready.

---

## Section 1 — Module Definitions

### 1.1 New Module IDs

Register these 3 modules in `VerticalModuleRegistry`:

| Module ID | Display Name | Description | Default Enabled For |
|-----------|-------------|-------------|---------------------|
| `resource_planning` | Resource Planning | Team allocation grid, capacity forecasting, and utilization tracking. Best for firms with 10+ team members managing multiple concurrent projects. | None (empty list) |
| `bulk_billing` | Bulk Billing Runs | Batch invoice generation across multiple customers in a single run. Best for firms billing 10+ clients per cycle. | None (empty list) |
| `automation_builder` | Automation Rule Builder | Create custom workflow automations with triggers, conditions, and actions. Standard automations run automatically — enable this to customize or create new rules. | None (empty list) |

Each module definition should include its associated nav items so the registry is the single source of truth for what gets hidden/shown.

### 1.2 Module Category Distinction

The module registry should distinguish between **vertical modules** (auto-assigned by profile, not shown in the Features settings section) and **horizontal modules** (manually toggled by admin, shown in Features settings). Add a `category` field to `ModuleDefinition`:

- `VERTICAL` — Existing modules (trust_accounting, court_calendar, conflict_check, lssa_tariff, regulatory_deadlines). Managed by profile selection. NOT shown in Features settings.
- `HORIZONTAL` — New modules (resource_planning, bulk_billing, automation_builder). Manually toggled. Shown in Features settings.

This prevents the Settings UI from showing vertical-specific modules (e.g., "Trust Accounting" toggle for an accounting firm).

---

## Section 2 — Backend Changes

### 2.1 Module Registry Update

Add the 3 new `ModuleDefinition` entries to `VerticalModuleRegistry` with `category: HORIZONTAL`.

Add `ModuleCategory` enum: `VERTICAL`, `HORIZONTAL`.

Extend `ModuleDefinition` record with a `category` field. Existing modules get `VERTICAL`.

### 2.2 Module CRUD Endpoint

Add an endpoint for toggling horizontal modules:

**`PUT /api/settings/modules`** — Update enabled horizontal modules for the current org.

Request body:
```json
{
  "enabledModules": ["resource_planning", "bulk_billing"]
}
```

Validation:
- Only accept module IDs that exist in the registry with `category: HORIZONTAL`
- Merge with existing vertical module IDs (don't overwrite them)
- Require `MANAGE_SETTINGS` capability (or equivalent org admin check)

Response: Updated `OrgSettings` with full `enabledModules` list.

Audit event: `MODULES_UPDATED` with before/after diff.

**`GET /api/settings/modules`** — Return available horizontal modules and their enabled status.

Response:
```json
{
  "modules": [
    {
      "id": "resource_planning",
      "name": "Resource Planning",
      "description": "Team allocation grid...",
      "enabled": false
    },
    ...
  ]
}
```

### 2.3 Controller Guards

Add `verticalModuleGuard.requireModule("module_id")` to the entry point of each gated controller. If the module is not enabled, the guard returns 403 with a clear error message.

**`resource_planning` guard on:**
- `ResourceAllocationController` — all endpoints
- `CapacityController` — all endpoints  
- `UtilizationController` — all endpoints (if separate from profitability)

**`bulk_billing` guard on:**
- `BillingRunController` — all endpoints

**`automation_builder` guard on:**
- `AutomationRuleController` — CRUD endpoints only (list, create, update, delete rules)
- `AutomationTemplateController` — template gallery endpoints (if separate)
- `AutomationExecutionLogController` — execution log read endpoints
- **NOT** on `AutomationExecutionService` or any internal execution/trigger code — seeded automations must keep running

### 2.4 Profile JSON Updates

Update all vertical profile JSON files to explicitly NOT include the 3 new module IDs. This is already the default behavior (they're not listed), but document it clearly:

- `legal-za.json`: `enabledModules` stays `["court_calendar", "conflict_check", "lssa_tariff", "trust_accounting"]`
- `accounting-za.json`: `enabledModules` stays `[]`
- `consulting-generic.json`: `enabledModules` stays `[]`

---

## Section 3 — Frontend Changes

### 3.1 Nav Item Gating

Wrap the following sidebar nav items in `ModuleGate`:

**`resource_planning`:**
- Resource Planning nav item (allocation grid page)
- Utilization nav item (if separate from profitability)
- Any sub-nav items under resource planning

**`bulk_billing`:**
- Billing Runs nav item

**`automation_builder`:**
- Automations nav item (rule list / template gallery)

When a module is disabled, its nav items simply don't render — no "locked" or "upgrade" indicators. Clean absence.

### 3.2 Page-Level Gating

Wrap the following page components in `ModuleGate` with a fallback that redirects to the dashboard or shows a "feature not enabled" message:

**`resource_planning`:**
- Allocation grid page
- Capacity/utilization page
- Project detail → Staffing tab (hide the tab entirely)

**`bulk_billing`:**
- Billing run list page
- Billing run wizard page
- Billing run detail page

**`automation_builder`:**
- Automation rule list page
- Automation rule create/edit wizard
- Automation execution log page
- Automation template gallery page

### 3.3 Dashboard Widget Gating

Wrap the following dashboard widgets in `ModuleGate`:

**`resource_planning`:**
- Utilization widget on company dashboard
- Capacity widget (if any)

**`bulk_billing`:**
- Recent billing runs widget (if any)

**`automation_builder`:**
- Recent automation executions widget (if any)

When gated, widgets simply don't render — dashboard grid adjusts naturally.

### 3.4 Settings — Features Section

Add a "Features" section to the existing Settings layout. This section displays only `HORIZONTAL` modules as toggleable cards.

**UI design:**
- Section header: "Features" with subtitle: "Enable additional features for your organization. These can be turned on or off at any time."
- Each module renders as a card with: module name, description, and a toggle switch.
- Toggle calls `PUT /api/settings/modules` with the updated set.
- No confirmation dialog — toggles are instantly reversible.
- No data loss warning — disabling a module hides the UI but doesn't delete data. Re-enabling restores everything.

**Settings nav placement:** Add "Features" as a nav item in the Settings sidebar, grouped with other org configuration items (not under a vertical-specific section).

### 3.5 API Client

Add API client functions for the new endpoints:
- `getModuleSettings()` → `GET /api/settings/modules`
- `updateModuleSettings(enabledModules: string[])` → `PUT /api/settings/modules`

---

## Section 4 — Testing

### 4.1 Backend Tests

- **Module guard tests**: Verify each gated controller returns 403 when module is disabled and 200 when enabled.
- **Module CRUD tests**: Verify `PUT /api/settings/modules` correctly merges horizontal modules with existing vertical modules. Verify it rejects unknown module IDs and vertical module IDs.
- **Automation execution isolation**: Verify that disabling `automation_builder` does NOT stop seeded automation rules from executing. Create a seeded rule, disable the module, trigger the rule's condition, verify the action executes.
- **Audit event test**: Verify `MODULES_UPDATED` audit event is created with correct before/after.

### 4.2 Frontend Tests

- **Nav item visibility**: Verify nav items appear/disappear based on module state.
- **Page redirect**: Verify direct URL navigation to a gated page shows appropriate fallback when module is disabled.
- **Settings toggle**: Verify the Features section renders all 3 modules, toggles update state, and gated pages respond immediately.
- **Dashboard widget gating**: Verify widgets don't render when module is disabled.

---

## Out of Scope

- **Vertical module toggles in Settings** — Vertical modules (trust_accounting, court_calendar, etc.) are managed by profile selection, not individual toggles. Not shown in the Features section.
- **Per-user module visibility** — Modules are org-wide. No per-member overrides.
- **Usage analytics** — No tracking of which modules are enabled/disabled across tenants.
- **Module dependencies** — No "enable X requires Y" logic. Each module is independent.
- **Onboarding prompts** — No "did you know you can enable Resource Planning?" suggestions. Clean absence.
- **Nav restructuring or demotion** — Features don't change location. They're shown or hidden in their existing positions.

## ADR Topics

- **ADR: Horizontal vs. Vertical Module Gating** — Document the distinction between vertical modules (auto-assigned by profile, not user-toggleable) and horizontal modules (opt-in via Settings, profile-independent). Explain why the same `enabled_modules` JSONB array serves both: simplicity, single guard path, no new infrastructure.

## Style & Boundaries

- Reuse Phase 49 infrastructure entirely. No new gating mechanisms.
- Module IDs are lowercase_snake_case, consistent with existing IDs.
- The `ModuleCategory` enum lives in the `verticals` package alongside existing module infrastructure.
- Frontend Settings section uses existing Shadcn Switch component for toggles and Card component for module display.
- Error messages from `VerticalModuleGuard` should be user-friendly: "This feature is not enabled for your organization. An admin can enable it in Settings → Features."
- Keep the implementation small — this is a wiring phase, not a feature phase. No new UI components beyond the Settings section.
