# Phase 49 — Vertical Architecture: Module Guard, Profile System & First Vertical Profiles

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 48 phases of functionality: projects, customers, tasks, time tracking, invoicing (with tax, payments, bulk billing), rate cards, budgets, profitability dashboards, document templates (Tiptap + Word), proposals, document acceptance, retainer agreements, recurring tasks/schedules, expenses, custom fields, tags, saved views, workflow automations, resource planning, comments, notifications, activity feeds, audit trails, client information requests, customer portal, reporting/export, email delivery, command palette, RBAC with custom roles, Keycloak auth with Gateway BFF, admin-approved org provisioning, in-app AI assistant (BYOAK), and a vertical QA process that validated the platform against SA accounting firm workflows.

**The existing infrastructure**: `OrgSettings` already has a `vertical_profile` column (unused), boolean feature flags (`accounting_enabled`, `ai_enabled`, `document_signing_enabled`), and pack status tracking (field packs, template packs, compliance packs, clause packs, automation packs). `CustomerLifecycleGuard` provides the pattern for state-based access gating. The i18n message catalog (Phase 43) supports string overrides. Phase 47 validated that an accounting firm can operate on the platform with configuration alone (packs + feature flags).

**The problem**: The platform has the pieces for vertical support — feature flags, packs, terminology overrides — but no cohesive vertical architecture. There's no way to:
1. Define what a "vertical" is (which modules, packs, terminology, and UI components it includes)
2. Gate access to vertical-specific backend modules per tenant at runtime
3. Conditionally render UI sections based on a tenant's vertical profile
4. Provision a new tenant with the correct vertical profile and all its associated packs
5. Build vertical-specific domain modules (like trust accounting) that coexist safely with core code

**The fix**: Build a vertical profile system that formalises the relationship between tenants and verticals. Introduce a module guard pattern (modeled on `CustomerLifecycleGuard`) that gates backend access per tenant. Add a frontend org context provider that makes the tenant's profile available for conditional UI rendering. Define three concrete vertical profiles (IT consulting, SA accounting, SA legal) and wire up the first two as proof-of-concept. The legal profile defines which modules it will need (trust accounting, court calendar, conflict check) without building them — establishing the extension points.

## Objective

1. **Formalise the vertical profile model** — define what a vertical profile contains (enabled modules, pack selections, terminology namespace, UI zone configuration, default settings) and how it's stored.
2. **Build the module guard** — a `VerticalModuleGuard` component that checks the current tenant's `enabled_modules` before allowing access to vertical-specific endpoints. Follows the `CustomerLifecycleGuard` pattern exactly.
3. **Create a frontend org context provider** — a `useOrgProfile()` hook that caches the tenant's vertical profile and enabled modules, making conditional rendering trivial across all pages.
4. **Define three concrete profiles**: IT Consulting (pure config), SA Accounting (config + deadline tracking flag), SA Legal (config + module stubs for trust accounting, court calendar, conflict check).
5. **Wire profile application at tenant provisioning** — when a new org is approved, the admin selects a vertical, and the correct profile (packs, settings, feature flags, terminology) is applied automatically.
6. **Conditionally render UI** — sidebar navigation, detail page sections, and settings pages adapt based on the tenant's enabled modules and vertical profile.
7. **Establish module extension points** — define the backend package structure and guard pattern for future vertical modules (e.g., `verticals/legal/trustaccounting/`) without building the modules themselves.

## Constraints & Assumptions

- **No new domain modules are built in this phase.** Trust accounting, court calendar, and conflict check are defined as module stubs with placeholder endpoints — but the actual domain logic is future work. This phase builds the infrastructure that those modules plug into.
- **The guard pattern must be lightweight.** A single method call (`moduleGuard.requireModule("trust_accounting")`) at the service or controller layer. No AOP, no interceptor chains, no framework abstractions. Match the simplicity of `CustomerLifecycleGuard`.
- **`OrgSettings.verticalProfile` column already exists.** Extend it — don't replace the existing feature flags. The vertical profile is a higher-level concept that sets sensible defaults for the individual flags.
- **Packs are already seeded.** Phase 47 created accounting packs (field, template, compliance, clause, automation). This phase wires them to the vertical profile so they're auto-applied at provisioning. It does NOT create new pack content.
- **Terminology switching may have gaps.** The i18n message catalog (Phase 43) built the infrastructure but may not support per-vertical namespace loading. If the mechanism isn't ready, document what's needed and use the vertical profile to flag the desired namespace — the actual switching can happen incrementally.
- **Frontend conditional rendering must be performant.** The org profile should be fetched once and cached — not re-fetched on every page navigation. A React context provider at the layout level is the expected pattern.
- **Backward compatibility.** Existing tenants with no vertical profile should continue working unchanged. `verticalProfile = null` means "generic" — all core features available, no vertical-specific modules.
- **This is a solo-founder project.** Keep the architecture simple. No microservices, no plugin registries, no OSGi-style module loading. Just packages, guards, and configuration.

---

## Section 1 — Vertical Profile Model

### 1.1 Profile Definition

A vertical profile is a named configuration bundle that determines:

| Aspect | What it controls | Storage |
|--------|-----------------|---------|
| **Identity** | Profile name, display label, description | `vertical_profile` column on OrgSettings (e.g., "accounting-za", "legal-za", "consulting-generic") |
| **Enabled modules** | Which vertical-specific backend modules are accessible | `enabled_modules` JSONB column on OrgSettings (e.g., `["trust_accounting", "court_calendar"]`) |
| **Pack selections** | Which field/template/compliance/clause/automation packs to apply | Existing `*_pack_status` columns — profile sets defaults |
| **Terminology namespace** | Which i18n override namespace to load | `terminology_namespace` column on OrgSettings (e.g., "en-ZA-accounting") |
| **UI configuration** | Which sidebar zones/items to show/hide, which detail page sections to render | Derived from `enabled_modules` + `vertical_profile` — frontend logic, not stored per-setting |
| **Default settings** | Default currency, tax rate, rate card structure | Applied at provisioning time via the profile's defaults |

### 1.2 Data Model Changes

**OrgSettings entity — new/modified columns:**

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `vertical_profile` | `VARCHAR(50)` | `null` | Already exists. Values: "accounting-za", "legal-za", "consulting-generic", "agency-generic", or null (generic). |
| `enabled_modules` | `JSONB` | `[]` | New. Array of module identifiers the tenant can access. E.g., `["trust_accounting", "court_calendar", "conflict_check"]`. Empty array means no vertical-specific modules. |
| `terminology_namespace` | `VARCHAR(100)` | `null` | New. i18n namespace override. E.g., "en-ZA-accounting". Null means use default "en" namespace. |

**Flyway migration**: Add `enabled_modules` and `terminology_namespace` columns. Backfill: existing tenants get `enabled_modules = '[]'::jsonb` and `terminology_namespace = null`.

### 1.3 Profile Registry (Seed Data)

Profiles are defined as seed data — not a separate entity. A profile is a function that applies a set of defaults to OrgSettings + triggers pack application.

**Profile definitions** (in the seeder, not a database table):

```
CONSULTING_GENERIC:
  vertical_profile: "consulting-generic"
  enabled_modules: []
  terminology_namespace: null  (use defaults — "projects", "tasks", "customers")
  packs: []  (no special packs — core fields suffice)
  defaults: { currency: null }  (tenant sets their own)

ACCOUNTING_ZA:
  vertical_profile: "accounting-za"
  enabled_modules: []  (no vertical-specific modules yet — everything is config)
  terminology_namespace: "en-ZA-accounting"
  packs: ["accounting-za"]  (field pack, template pack, compliance pack, clause pack)
  defaults: { currency: "ZAR", taxRate: 15.0, taxLabel: "VAT" }

LEGAL_ZA:
  vertical_profile: "legal-za"
  enabled_modules: ["trust_accounting", "court_calendar", "conflict_check"]
  terminology_namespace: "en-ZA-legal"
  packs: ["legal-za"]  (field pack, template pack, compliance pack)
  defaults: { currency: "ZAR", taxRate: 15.0, taxLabel: "VAT" }
```

**Important**: The legal profile declares `enabled_modules` but the actual module code is placeholder/stub in this phase. The guard will allow access to the endpoints, but the endpoints return stub responses until the modules are built.

---

## Section 2 — Module Guard

### 2.1 VerticalModuleGuard Component

Follows the exact same pattern as `CustomerLifecycleGuard`:

```
@Component
VerticalModuleGuard
  + requireModule(String moduleId) → void | throws ModuleNotEnabledException
  + isModuleEnabled(String moduleId) → boolean
  + getEnabledModules() → Set<String>
```

**Behavior**:
- Reads the current tenant's `enabled_modules` from OrgSettings (via `OrgSettingsService`)
- `requireModule("trust_accounting")` throws `ModuleNotEnabledException` if the module isn't in the tenant's enabled list
- `isModuleEnabled("trust_accounting")` returns boolean for conditional logic in shared services
- Caches the enabled modules per-request to avoid repeated DB lookups (use `RequestScopes` or a request-scoped bean)

**Exception handling**: `ModuleNotEnabledException` should return HTTP 403 with a clear message: "This feature requires the Trust Accounting module. Contact your administrator to enable it."

### 2.2 Module Identifier Convention

Module IDs are snake_case strings. They map to backend packages:

| Module ID | Backend Package | Status |
|-----------|----------------|--------|
| `trust_accounting` | `verticals.legal.trustaccounting` | Stub (this phase) |
| `court_calendar` | `verticals.legal.courtcalendar` | Stub (this phase) |
| `conflict_check` | `verticals.legal.conflictcheck` | Stub (this phase) |

Future modules follow the same convention: `module_name` → `verticals.{vertical}.{modulename}`.

### 2.3 Usage Pattern

Controllers in vertical-specific packages gate access at the top of each endpoint:

```java
@RestController
@RequestMapping("/api/trust-accounting")
public class TrustAccountingController {

    private final VerticalModuleGuard moduleGuard;

    @GetMapping("/ledger")
    public ResponseEntity<?> getLedger() {
        moduleGuard.requireModule("trust_accounting");
        // ... module logic
    }
}
```

Shared services that have module-conditional behavior use `isModuleEnabled()`:

```java
// In InvoiceService — add LSSA tariff option if legal module is enabled
if (moduleGuard.isModuleEnabled("lssa_tariff")) {
    // include tariff rate options
}
```

---

## Section 3 — Module Stubs (Legal Vertical)

### 3.1 Package Structure

Create the package structure for legal modules. Each module gets a stub controller with a single health/status endpoint:

```
backend/src/main/java/io/b2mash/b2b/b2bstrawman/
└── verticals/
    └── legal/
        ├── trustaccounting/
        │   └── TrustAccountingController.java   (stub)
        ├── courtcalendar/
        │   └── CourtCalendarController.java     (stub)
        └── conflictcheck/
            └── ConflictCheckController.java     (stub)
```

**Stub controller pattern** (same for all three):

Each stub provides:
- `GET /api/{module}/status` → returns `{ "module": "trust_accounting", "status": "stub", "message": "This module is not yet implemented. It will be available in a future release." }`
- Module guard check on every endpoint
- No entities, no services, no migrations — just the controller with the guard

### 3.2 Legal Terminology Pack (Seed Data)

Create a basic legal terminology mapping as seed data. This does NOT require the terminology switching mechanism to be built — it defines what the mapping will be when it's ready:

| Platform Term | Legal Term |
|---------------|-----------|
| Projects | Matters |
| Tasks | Work Items |
| Customers | Clients |
| Proposals | Engagement Letters |
| Time Entries | Fee Notes |
| Rate Cards | Tariff Schedule |
| Documents | Pleadings & Correspondence |

Store as a message override file: `frontend/src/messages/en-ZA-legal/` (may not be loaded automatically yet — that's fine, the file exists for when the switching mechanism is built).

---

## Section 4 — Frontend Profile-Aware UI

### 4.1 Org Profile Context Provider

Create a context provider that makes the tenant's vertical profile available throughout the app:

```
OrgProfileProvider (at layout level)
  ├── Fetches org settings including vertical_profile and enabled_modules
  ├── Caches in React context (no re-fetch per page)
  ├── Provides: useOrgProfile() hook
  └── Returns: { verticalProfile, enabledModules, terminologyNamespace, isModuleEnabled(id) }
```

**Hook API**:
- `useOrgProfile()` → `{ verticalProfile: string | null, enabledModules: string[], isModuleEnabled: (id: string) => boolean }`
- `isModuleEnabled("trust_accounting")` → boolean check against the `enabledModules` array

### 4.2 Conditional Sidebar Navigation

The sidebar already has navigation zones (Phase 44). Vertical-specific nav items are added conditionally:

**Legal vertical additions** (only shown when modules are enabled):

| Zone | Item | Module Required | Route |
|------|------|----------------|-------|
| Finance | Trust Accounting | `trust_accounting` | `/org/[slug]/trust-accounting` |
| Work | Court Calendar | `court_calendar` | `/org/[slug]/court-calendar` |

**Pattern**: Wrap nav items in a `<ModuleGate module="trust_accounting">` component that reads from `useOrgProfile()` and renders children only if the module is enabled.

```tsx
// ModuleGate component
function ModuleGate({ module, children }: { module: string; children: ReactNode }) {
    const { isModuleEnabled } = useOrgProfile();
    if (!isModuleEnabled(module)) return null;
    return <>{children}</>;
}

// Usage in sidebar
<ModuleGate module="trust_accounting">
    <NavItem href={`/org/${slug}/trust-accounting`} icon={Shield} label="Trust Accounting" />
</ModuleGate>
```

### 4.3 Conditional Detail Page Sections

Some existing pages gain additional sections when modules are enabled:

| Page | Section | Module Required | Content |
|------|---------|----------------|---------|
| Customer detail | Trust Balance | `trust_accounting` | Shows trust account balance for this client (stub: "Trust Accounting module coming soon") |
| New matter/project dialog | Conflict Check | `conflict_check` | Button to run conflict check before creating (stub: "Conflict Check module coming soon") |
| Invoice creation | Tariff Rates | `lssa_tariff` | Option to use LSSA tariff rates (stub: placeholder) |

**Pattern**: Same `<ModuleGate>` wrapper. Stub content shows a clear message that the module isn't built yet.

### 4.4 Stub Pages for Legal Modules

Create minimal pages at the routes for trust accounting, court calendar, and conflict check. Each page:
- Is gated by `<ModuleGate>` (redirect to 404 if module not enabled)
- Shows a placeholder UI: module name, brief description of what it will do, "Coming soon" badge
- Has the correct layout (sidebar, header, breadcrumbs) so it feels like a real part of the app

---

## Section 5 — Profile Application at Provisioning

### 5.1 Admin Provisioning Flow

The admin-approved org provisioning flow (Phase 39) currently creates a tenant with default settings. Extend it:

1. **Registration form** — add a "Vertical" dropdown (optional): Generic, IT Consulting, Accounting (SA), Legal (SA)
2. **Admin approval screen** — show the selected vertical. Admin can change it before approving.
3. **On approval** — after schema creation and default seeding, apply the vertical profile:
   - Set `vertical_profile` and `enabled_modules` on OrgSettings
   - Apply relevant packs (field, template, compliance, clause, automation)
   - Set default currency and tax rate
   - Set terminology namespace

### 5.2 Profile Switching (Admin)

Allow org owners to request a vertical profile change (e.g., "we started as generic but want to switch to accounting"):

- **Settings → General → Vertical Profile**: dropdown showing available profiles
- Changing profile:
  - Updates `vertical_profile`, `enabled_modules`, `terminology_namespace`
  - Triggers pack application (additive — doesn't remove existing data)
  - Does NOT delete any existing data or customizations
- **Warning**: "Changing your vertical profile will add new field definitions and templates. Your existing data will not be affected."
- **Audit**: Log profile changes as audit events

---

## Section 6 — API Changes

### 6.1 OrgSettings Endpoint Extensions

**`GET /api/settings`** — extend the response to include:
- `verticalProfile: string | null`
- `enabledModules: string[]`
- `terminologyNamespace: string | null`

**`PUT /api/settings`** — allow updating `verticalProfile` (which triggers the profile application logic). Direct modification of `enabledModules` is admin-only (platform admin, not org owner) to prevent tenants from self-enabling paid modules.

### 6.2 Module Status Endpoint

**`GET /api/modules`** — returns the list of all known modules with their status for the current tenant:

```json
[
  { "id": "trust_accounting", "name": "Trust Accounting", "enabled": true, "status": "stub" },
  { "id": "court_calendar", "name": "Court Calendar", "enabled": true, "status": "stub" },
  { "id": "conflict_check", "name": "Conflict Check", "enabled": false, "status": "stub" }
]
```

This endpoint is used by the frontend to render module status indicators and by the admin provisioning UI.

### 6.3 Profile Registry Endpoint

**`GET /api/profiles`** — returns available vertical profiles (for the provisioning dropdown):

```json
[
  { "id": "consulting-generic", "name": "IT Consulting", "description": "General consulting and professional services", "modules": [] },
  { "id": "accounting-za", "name": "Accounting (South Africa)", "description": "SA accounting practice with FICA compliance", "modules": [] },
  { "id": "legal-za", "name": "Legal (South Africa)", "description": "SA law firm with trust accounting, court calendar", "modules": ["trust_accounting", "court_calendar", "conflict_check"] }
]
```

---

## Section 7 — Flyway Migration

### 7.1 Schema Changes

Single migration adding the new columns to `org_settings`:

```sql
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS enabled_modules JSONB DEFAULT '[]'::jsonb;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS terminology_namespace VARCHAR(100);
```

**Note**: `vertical_profile` column already exists — no migration needed for it.

### 7.2 Conditional Table Creation for Modules

Future vertical modules will need their own tables (e.g., `trust_ledger`, `trust_transaction`). These tables should only be created in schemas for tenants that have the module enabled.

**This phase does NOT create any module-specific tables.** But the migration pattern should be established:

- Module-specific migrations are in separate files (e.g., `V30__create_trust_accounting_tables.sql`)
- They run for ALL tenant schemas (Flyway doesn't support conditional per-schema execution)
- The tables exist in all schemas but are only used by tenants with the module enabled
- This is acceptable because empty tables have negligible cost, and the guard prevents access

**Alternative considered and rejected**: Conditional migration execution (only run for tenants with a certain profile). This adds complexity and makes schema management fragile. All schemas should be identical — the guard handles access control, not the schema.

---

## Section 8 — Testing

### 8.1 Backend Tests

| Test | What it verifies |
|------|-----------------|
| `VerticalModuleGuardTest` | `requireModule()` throws when module not enabled, succeeds when enabled |
| `VerticalModuleGuardTest` | `isModuleEnabled()` returns correct boolean |
| `VerticalModuleGuardTest` | Reads from current tenant's OrgSettings (multi-tenant isolation) |
| `TrustAccountingControllerTest` | Returns 403 when `trust_accounting` not in tenant's enabled_modules |
| `TrustAccountingControllerTest` | Returns stub response when module is enabled |
| `OrgSettingsServiceTest` | Profile application sets correct enabled_modules, packs, and defaults |
| `OrgSettingsControllerTest` | Settings response includes vertical profile fields |
| `ModuleStatusControllerTest` | Returns correct module list with enabled/disabled status |
| `ProfileRegistryControllerTest` | Returns available profiles |

### 8.2 Frontend Tests

| Test | What it verifies |
|------|-----------------|
| `OrgProfileProvider` | Provides correct profile data to children |
| `ModuleGate` | Renders children when module enabled, renders nothing when disabled |
| `Sidebar` | Shows trust accounting nav item for legal profile, hides for accounting profile |
| `TrustAccountingPage` | Renders stub content when module enabled |

---

## Out of Scope

- **Building actual domain modules.** Trust accounting, court calendar, and conflict check are stubs only. The actual domain logic (entities, services, business rules) is future phases.
- **Terminology switching implementation.** The namespace is stored and the override files are created, but the runtime switching mechanism (loading the correct i18n namespace per tenant) may need additional work. Document gaps.
- **Pack content creation.** Phase 47 already created accounting packs. Legal packs are NOT created in this phase — only the profile definition that references them.
- **Billing/pricing for modules.** Module-level pricing (charging per enabled module) is a billing system concern, not a vertical architecture concern.
- **Multi-vertical tenants.** A tenant has one vertical profile. Mixing profiles (e.g., an accounting firm that also does legal work) is out of scope. If needed later, `enabled_modules` already supports arbitrary combinations independently of the profile.

## ADR Topics

- **ADR: Vertical profile storage** — profile as seed data (functions) vs. database entity (VerticalProfile table). Seed data is simpler and sufficient for 3-5 profiles. A table adds CRUD overhead with minimal benefit at this scale. Recommend seed data with a registry class.
- **ADR: Module guard granularity** — guard at controller level (per-endpoint) vs. service level (per-operation) vs. Spring Security filter (per-URL-pattern). Recommend controller level for consistency with existing guard patterns.
- **ADR: Schema uniformity** — all tenant schemas get all tables (including module-specific ones) vs. conditional migrations. Recommend uniform schemas for simplicity — empty tables are cheap, conditional migrations are fragile.
- **ADR: Enabled modules authority** — who can modify `enabled_modules`? Org owner (self-service) vs. platform admin only. Recommend platform admin only to support paid module tiers.

## Style & Boundaries

- Follow existing patterns exactly. `VerticalModuleGuard` should look and feel like `CustomerLifecycleGuard`. The frontend `ModuleGate` should feel like any other conditional rendering pattern in the codebase.
- No over-engineering. The guard is a simple method call. The profile is a set of defaults applied at provisioning. The frontend hook is a context provider. No plugin systems, no registries with reflection, no event-driven module loading.
- Stub pages should look polished — not placeholder divs. Use the existing page layout, breadcrumbs, and card components. A "Coming Soon" badge on a well-laid-out page feels intentional. A blank div feels broken.
- Test the guard thoroughly — this is a security boundary. A tenant without `trust_accounting` enabled must NOT be able to access trust accounting endpoints, even by calling the API directly.
- Profile application at provisioning should be idempotent — applying the same profile twice should produce the same result.
