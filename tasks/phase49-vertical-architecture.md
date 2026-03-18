# Phase 49 — Vertical Architecture: Module Guard, Profile System & First Vertical Profiles

This phase introduces a formal vertical architecture to DocTeams. It creates a `VerticalModuleGuard` component (modeled on `CustomerLifecycleGuard`) for backend module gating, an `OrgProfileProvider` frontend context (following the `TerminologyProvider` pattern) for conditional rendering, three concrete vertical profiles (IT Consulting, SA Accounting, SA Legal), and stub controllers for the legal modules (trust accounting, court calendar, conflict check). The legal profile is the most architecturally interesting: it declares three modules that do not exist yet, with stub endpoints gated by the guard. Future phases fill in the actual domain logic.

**Architecture doc**: `architecture/phase49-vertical-architecture.md`

**ADRs**:
- [ADR-189](adr/ADR-189-vertical-profile-storage.md) — Vertical profile storage (hybrid: Java registry + classpath JSON)
- [ADR-190](adr/ADR-190-module-guard-granularity.md) — Module guard granularity (controller-level explicit calls)
- [ADR-191](adr/ADR-191-schema-uniformity-module-tables.md) — Schema uniformity for module tables (all schemas get all tables)
- [ADR-192](adr/ADR-192-enabled-modules-authority.md) — Enabled modules authority (platform admin only, profile-driven defaults)

**Dependencies on prior phases**:
- Phase 47: `OrgSettings.verticalProfile` column (V70 migration), `AbstractPackSeeder` vertical filtering, `vertical-profiles/accounting-za.json`, `INDUSTRY_TO_PROFILE` map in `AccessRequestApprovalService`
- Phase 13: Schema-per-tenant isolation (ADR-064), `OrgSettings` singleton-per-tenant entity
- Phase 6: `AuditService` and `AuditEventBuilder` for audit logging of profile changes
- Phase 8: `OrgSettingsService.toSettingsResponse()` pattern, `SettingsResponse` record
- Phase 20: E2E stack (mock auth, mock IDP) for integration testing
- Phase 39: `AccessRequestApprovalService.approve()` provisioning flow, `TenantProvisioningService`

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 367 | OrgSettings Extension + Module Guard + V75 Migration | Backend | — | S | 367A | **Done** (PR #759) |
| 368 | Profile Registry, Module Registry, Controllers & Provisioning | Backend | 367 | M | 368A, 368B | |
| 369 | Legal Module Stub Controllers | Backend | 367 | S | 369A | |
| 370 | Frontend OrgProfileProvider + ModuleGate + Sidebar | Frontend | 367 | S | 370A | |
| 371 | Legal Stub Pages + Conditional Sections + Settings Profile Switching | Frontend | 368, 370 | M | 371A, 371B | |
| 372 | Legal Terminology + End-to-End Integration Tests | Backend + Frontend | 368, 369, 370 | S | 372A, 372B | |

---

## Dependency Graph

```
BACKEND FOUNDATION (sequential)
───────────────────────────────────────────────────────────────

[E367A V75 migration,                 
 OrgSettings extension,              
 ModuleNotEnabledException,          
 VerticalModuleGuard,                
 SettingsResponse extension,         
 + integration tests]                
        |                             
        +─────────────────+─────────────────+
        |                 |                 |
BACKEND REGISTRIES        |          BACKEND STUBS
+ PROVISIONING            |          (parallel w/ 368)
(sequential)              |          ──────────────────
        |                 |                 |
[E368A Profile/Module     |          [E369A Trust accounting,
 registries,              |           court calendar,
 VerticalProfileController|           conflict check
 GET /api/profiles,       |           stub controllers
 GET /api/modules,        |           + guard integration
 + integration tests]     |           + integration tests]
        |                 |                 
[E368B Profile switching, |                 
 PATCH /api/settings/     |                 
 vertical-profile,        |                 
 provisioning extension,  |                 
 AccessRequest fix,       |                 
 profile JSON files,      |                 
 + integration tests]     |
        |                 |
        +────────+────────+
                 |
FRONTEND FOUNDATION
(requires 367A backend deployed)
──────────────────────────────────────────────────────

[E370A OrgProfileProvider,
 ModuleGate, types update,
 layout integration,
 NavItem requiredModule,
 NavZone filtering,
 + frontend tests]
        |
        +─────────────────+
        |                 |
FRONTEND PAGES      TERMINOLOGY + E2E
(requires 368B, 370A)  (requires 368, 369, 370)
──────────────────  ──────────────────

[E371A Stub pages:       [E372A Backend integration
 trust-accounting,        tests: profile switching
 court-calendar,          + provisioning + guard
 conflict-check,          E2E tests]
 conditional sections,         |
 + frontend tests]       [E372B Frontend integration
        |                 tests: terminology map,
[E371B Settings profile   sidebar gating,
 switching UI:            stub page rendering]
 dropdown, confirm dialog,
 PATCH call,
 + frontend tests]
```

**Parallel opportunities**:
- E368A and E369A can run in parallel (both depend only on E367A). E369A needs only the `VerticalModuleGuard` from E367A, not the registries from E368A.
- E371A and E372A/372B can run in parallel once their prerequisites are met.
- E368A and E368B are sequential (368B depends on the registries from 368A).

---

## Implementation Order

### Stage 0: Backend Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 367 | 367A | V75 migration, `OrgSettings` extension (2 new fields), `ModuleNotEnabledException`, `VerticalModuleGuard`, `SettingsResponse` extension (+3 fields incl. existing `verticalProfile`), `OrgSettingsService.getEnabledModulesForCurrentTenant()`, integration tests (~8). Backend only. | **Done** (PR #759) |

### Stage 1: Backend Registries + Stubs (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 368 | 368A | `VerticalProfileRegistry`, `VerticalModuleRegistry`, `VerticalProfileController` (`GET /api/profiles`, `GET /api/modules`), integration tests (~6). Backend only. | **Done** (PR #760) |
| 1b (parallel) | 369 | 369A | 3 stub controllers (`TrustAccountingController`, `CourtCalendarController`, `ConflictCheckController`), guard integration, integration tests (~6). Backend only. | |

### Stage 2: Backend Profile Switching + Provisioning

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 368 | 368B | `OrgSettingsService.updateVerticalProfile()`, `PATCH /api/settings/vertical-profile` endpoint, provisioning extension (`setVerticalProfile` sets `enabledModules`+`terminologyNamespace`), profile JSON files (`consulting-generic.json`, `legal-za.json`, extend `accounting-za.json`), fix `INDUSTRY_TO_PROFILE`, integration tests (~8). Backend only. | |

### Stage 3: Frontend Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 370 | 370A | `OrgProfileProvider` context + `useOrgProfile()` hook, `ModuleGate` component, types update, layout integration, `NavItem.requiredModule`, `NavZone` filtering, frontend tests (~6). Frontend only. | |

### Stage 4: Frontend Pages + Settings (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 371 | 371A | 3 stub pages (`trust-accounting`, `court-calendar`, `conflict-check`), conditional trust balance card on customer detail, conditional conflict check section on new project dialog, frontend tests (~6). Frontend only. | |
| 4b (parallel) | 371 | 371B | Settings profile switching UI: profile dropdown, confirmation dialog, `PATCH` call, re-fetch, frontend tests (~4). Frontend only. | |

### Stage 5: Terminology + Integration Tests

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 372 | 372A | Backend integration tests: full profile switching flow, provisioning with legal-za, guard + module status E2E, (~6 tests). Backend only. | |
| 5b (parallel) | 372 | 372B | Frontend: legal terminology map + integration tests for sidebar gating, stub page rendering, terminology overrides (~6 tests). Frontend only. | |

---

## Epic 367: OrgSettings Extension + Module Guard + V75 Migration

**Goal**: Extend the `OrgSettings` entity with `enabled_modules` (JSONB) and `terminology_namespace` (VARCHAR) columns, create the `VerticalModuleGuard` component and `ModuleNotEnabledException`, add `verticalProfile`/`enabledModules`/`terminologyNamespace` to the `SettingsResponse` record, and add `getEnabledModulesForCurrentTenant()` to `OrgSettingsService`. This is the foundation slice that all other epics depend on.

**References**: Architecture doc Sections 2.1, 3.1, 3.2, 3.3, 4.1, 7.1, 8.1, 8.3, 8.4. ADR-189, ADR-190.

**Dependencies**: None (first slice).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **367A** | 367.1--367.9 | V75 migration, `OrgSettings` entity extension (2 new fields + `updateVerticalProfile()` method), `ModuleNotEnabledException`, `VerticalModuleGuard`, `OrgSettingsService.getEnabledModulesForCurrentTenant()`, `SettingsResponse` extension (+3 fields), `toSettingsResponse()` update, integration tests (~8). Backend only. | **Done** (PR #759) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 367.1 | Create V75 tenant migration | 367A | — | New file: `backend/src/main/resources/db/migration/tenant/V75__add_vertical_modules.sql`. DDL: `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS enabled_modules JSONB DEFAULT '[]'::jsonb; ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS terminology_namespace VARCHAR(100); CREATE INDEX IF NOT EXISTS idx_org_settings_enabled_modules ON org_settings USING GIN (enabled_modules);`. Pattern: `V74__prevent_audit_delete.sql`. |
| 367.2 | Add `enabledModules` and `terminologyNamespace` fields to `OrgSettings` entity | 367A | 367.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "enabled_modules", columnDefinition = "jsonb") private List<String> enabledModules = new ArrayList<>();` and `@Column(name = "terminology_namespace", length = 100) private String terminologyNamespace;`. Add getters/setters with `updatedAt` mutation. Add `updateVerticalProfile(String, List<String>, String)` domain method. Pattern: existing field/getter/setter pattern in same file. |
| 367.3 | Create `ModuleNotEnabledException` | 367A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ModuleNotEnabledException.java`. Extends `ErrorResponseException` with HTTP 403. Generates human-friendly module name from snake_case ID. Pattern: `ForbiddenException.java`. |
| 367.4 | Create `VerticalModuleGuard` component | 367A | 367.2, 367.3 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleGuard.java`. `@Component` with constructor injection of `OrgSettingsService`. Methods: `requireModule(String)` (throwing), `isModuleEnabled(String)` (boolean), `getEnabledModules()` (Set). Pattern: `compliance/CustomerLifecycleGuard.java`. |
| 367.5 | Add `getEnabledModulesForCurrentTenant()` to `OrgSettingsService` | 367A | 367.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`. New `@Transactional(readOnly = true)` method that reads `OrgSettings.getEnabledModules()` via `orgSettingsRepository.findForCurrentTenant()`. Returns `List<String>`, empty list if no settings row exists. Hibernate L1 cache provides per-request caching. |
| 367.6 | Extend `SettingsResponse` record with `verticalProfile`, `enabledModules`, `terminologyNamespace` | 367A | 367.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`. Add 3 new fields to `SettingsResponse` record: `String verticalProfile`, `List<String> enabledModules`, `String terminologyNamespace`. Note: `verticalProfile` is already on `OrgSettings` entity but was never added to the DTO — add it now. |
| 367.7 | Update `toSettingsResponse()` to include new fields | 367A | 367.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`. Add `settings.getVerticalProfile()`, `settings.getEnabledModules()`, `settings.getTerminologyNamespace()` to the `SettingsResponse` constructor call. Also update the `orElse` fallback to include `null`, `List.of()`, `null` for the 3 new fields. |
| 367.8 | Write unit tests for `VerticalModuleGuard` and `ModuleNotEnabledException` | 367A | 367.3, 367.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleGuardTest.java`. 4 tests: (1) `requireModule` throws `ModuleNotEnabledException` when module not in list, (2) `requireModule` does not throw when module is enabled, (3) `isModuleEnabled` returns correct boolean, (4) `getEnabledModules` returns correct set. Mock `OrgSettingsService`. New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/exception/ModuleNotEnabledExceptionTest.java`. 2 tests: correct HTTP 403 status, human-readable module name in detail. |
| 367.9 | Write integration test for settings response extension | 367A | 367.7 | Modify or extend existing `OrgSettingsControllerTest`. 2 tests: (1) `GET /api/settings` response includes `verticalProfile`, `enabledModules`, `terminologyNamespace` fields, (2) values are correct after setting `enabledModules` on `OrgSettings`. Use `ScopedValue.where(RequestScopes.TENANT_ID, ...).run(...)` pattern. |

### Key Files

**Create:** `V75__add_vertical_modules.sql`, `ModuleNotEnabledException.java`, `VerticalModuleGuard.java`, `VerticalModuleGuardTest.java`, `ModuleNotEnabledExceptionTest.java`

**Modify:** `OrgSettings.java` (+2 fields, +1 domain method), `OrgSettingsService.java` (+1 method, update `toSettingsResponse()`), `OrgSettingsController.java` (extend `SettingsResponse` record)

### Architecture Decisions

- **V75 migration**: Two `ALTER TABLE` + one `CREATE INDEX`. GIN index on `enabled_modules` for future analytics queries. `enabled_modules` defaults to `'[]'::jsonb`, `terminology_namespace` defaults to `null`. No backfill needed.
- **`verticalProfile` added to SettingsResponse**: The field has existed on `OrgSettings` since V70 but was never exposed in the DTO. Phase 49 adds it alongside the two new fields.
- **Guard uses Hibernate L1 cache**: `getEnabledModulesForCurrentTenant()` reads via `findForCurrentTenant()` which hits the persistence context cache on repeat calls within the same request. No additional `ScopedValue` or `RequestScope` bean needed.
- **`ModuleNotEnabledException` returns 403**: Consistent with `ForbiddenException` pattern. Module exists conceptually but tenant doesn't have access.

---

## Epic 368: Profile Registry, Module Registry, Controllers & Provisioning

**Goal**: Create the `VerticalProfileRegistry` (reads classpath JSON profiles) and `VerticalModuleRegistry` (defines known modules with metadata), expose them via `GET /api/profiles` and `GET /api/modules` endpoints, implement profile switching (`PATCH /api/settings/vertical-profile`), extend provisioning to set `enabledModules` and `terminologyNamespace`, and create/update profile JSON files.

**References**: Architecture doc Sections 2.3, 2.4, 3.4, 3.5, 3.6, 3.7, 4.2, 4.3, 4.4, 8.1. ADR-189, ADR-192.

**Dependencies**: Epic 367 (needs OrgSettings extension, SettingsResponse, guard).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **368A** | 368.1--368.7 | `VerticalProfileRegistry`, `VerticalModuleRegistry`, `VerticalProfileController` (`GET /api/profiles`, `GET /api/modules`), unit + integration tests (~6). Backend only. | **Done** (PR #760) |
| **368B** | 368.8--368.16 | `OrgSettingsService.updateVerticalProfile()`, `PATCH /api/settings/vertical-profile` endpoint, provisioning extension, profile JSON files (`consulting-generic.json`, `legal-za.json`), extend `accounting-za.json`, fix `INDUSTRY_TO_PROFILE`, integration tests (~8). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 368.1 | Create `VerticalModuleRegistry` | 368A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java`. Static registry defining 3 known modules: `trust_accounting` (name: "Trust Accounting", description: "LSSA-compliant trust account management for client funds", status: `stub`), `court_calendar` (name: "Court Calendar", description: "Court date tracking and deadline management", status: `stub`), `conflict_check` (name: "Conflict Check", description: "Matter conflict of interest checks", status: `stub`). Use a Java record `ModuleDefinition(String id, String name, String description, String status)`. `@Component` with methods: `getAllModules()`, `getModule(String id)`. |
| 368.2 | Create `VerticalProfileRegistry` | 368A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java`. `@Component` that reads `classpath:vertical-profiles/*.json` at construction. Record: `ProfileDefinition(String profileId, String name, String description, List<String> enabledModules, String terminologyNamespace, String currency, Map<String, Object> packs)`. Methods: `getProfile(String id)`, `getAllProfiles()`, `exists(String id)`. Pattern: reads JSON with `ObjectMapper`, stores in `Map<String, ProfileDefinition>`. |
| 368.3 | Create `VerticalProfileController` | 368A | 368.1, 368.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileController.java`. `@RestController @RequestMapping("/api")`. Two endpoints: `GET /api/profiles` returns list of profile summaries (id, name, description, modules list) from registry; `GET /api/modules` returns list of modules with `enabled` status (cross-reference with `OrgSettings.enabledModules`). Both require `@RequiresCapability("TEAM_OVERSIGHT")`. Response DTOs as nested records. Pattern: thin controller — delegates to registry + `OrgSettingsService`. |
| 368.4 | Write unit tests for `VerticalModuleRegistry` | 368A | 368.1 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistryTest.java`. 2 tests: (1) `getAllModules` returns 3 modules with correct IDs, (2) `getModule("trust_accounting")` returns correct metadata. |
| 368.5 | Write unit tests for `VerticalProfileRegistry` | 368A | 368.2 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistryTest.java`. 2 tests: (1) loads `accounting-za` profile from classpath JSON with correct fields, (2) `exists("nonexistent")` returns false. Requires `accounting-za.json` on test classpath (already exists). |
| 368.6 | Write integration test for `GET /api/profiles` | 368A | 368.3 | New file or extend: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileControllerTest.java`. 1 test: returns list of profiles with correct structure (id, name, description, modules). Use MockMvc with JWT. |
| 368.7 | Write integration test for `GET /api/modules` | 368A | 368.3 | Extend same test file. 1 test: returns modules list with `enabled` status reflecting tenant's `enabledModules`. Set up tenant with `enabled_modules = ["trust_accounting"]`, verify trust_accounting shows `enabled: true`, others `enabled: false`. |
| 368.8 | Create `consulting-generic.json` profile | 368B | — | New file: `backend/src/main/resources/vertical-profiles/consulting-generic.json`. Minimal profile: `profileId: "consulting-generic"`, `name: "IT Consulting"`, `description: "General consulting and professional services"`, `enabledModules: []`, no packs, no currency override, no terminology. |
| 368.9 | Create `legal-za.json` profile | 368B | — | New file: `backend/src/main/resources/vertical-profiles/legal-za.json`. Profile: `profileId: "legal-za"`, `name: "Legal (South Africa)"`, `enabledModules: ["trust_accounting", "court_calendar", "conflict_check"]`, `terminologyOverrides: "en-ZA-legal"`, `currency: "ZAR"`, `taxDefaults: [{ name: "VAT", rate: 15.00, default: true }]`, `packs: { field: ["legal-za-customer", "legal-za-project"], compliance: ["fica-kyc-za"], template: ["legal-za"] }`. Pattern: `accounting-za.json`. |
| 368.10 | Extend `accounting-za.json` with `enabledModules` field | 368B | — | Modify: `backend/src/main/resources/vertical-profiles/accounting-za.json`. Add `"enabledModules": []` field (accounting profile has no vertical-specific modules). The `terminologyOverrides` field already exists. |
| 368.11 | Implement `OrgSettingsService.updateVerticalProfile()` | 368B | 368.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`. New method: validates profile ID via `VerticalProfileRegistry.exists()`, requires owner role, reads profile definition, calls `OrgSettings.updateVerticalProfile(profileId, enabledModules, terminologyNamespace)`, triggers additive pack application (call existing seeders), logs audit event `org_settings.vertical_profile_changed` with old/new profile. Returns `SettingsResponse`. |
| 368.12 | Add `PATCH /api/settings/vertical-profile` endpoint | 368B | 368.11 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`. New endpoint: `@PatchMapping("/settings/vertical-profile")`, requires `@RequiresCapability("TEAM_OVERSIGHT")`. Request DTO: `UpdateVerticalProfileRequest(String verticalProfile)`. Delegates to `orgSettingsService.updateVerticalProfile()`. Owner-only check inside the service. |
| 368.13 | Extend `TenantProvisioningService.setVerticalProfile()` | 368B | 368.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`. In `setVerticalProfile()`, inject `VerticalProfileRegistry`, read profile definition, set `enabledModules` and `terminologyNamespace` on `OrgSettings` in addition to existing `verticalProfile` and `currency`. |
| 368.14 | Fix `INDUSTRY_TO_PROFILE` mapping | 368B | — | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java`. Change `"Legal", "law-za"` to `"Legal", "legal-za"`. |
| 368.15 | Write integration test for profile switching | 368B | 368.11, 368.12 | Extend `VerticalProfileControllerTest.java` or new file. 3 tests: (1) `PATCH /api/settings/vertical-profile` with `"legal-za"` sets correct `enabledModules`, `terminologyNamespace`, `verticalProfile` on OrgSettings, (2) rejects invalid profile ID with 400, (3) rejects non-owner with 403. |
| 368.16 | Write integration test for provisioning extension | 368B | 368.13 | Extend existing provisioning tests. 2 tests: (1) provisioning with `"legal-za"` profile sets `enabledModules = ["trust_accounting", "court_calendar", "conflict_check"]` and `terminologyNamespace = "en-ZA-legal"`, (2) provisioning with `null` profile leaves `enabledModules = []`. |

### Key Files

**Create:** `VerticalProfileRegistry.java`, `VerticalModuleRegistry.java`, `VerticalProfileController.java`, `consulting-generic.json`, `legal-za.json`, `VerticalModuleRegistryTest.java`, `VerticalProfileRegistryTest.java`, `VerticalProfileControllerTest.java`

**Modify:** `OrgSettingsService.java` (+`updateVerticalProfile()` method), `OrgSettingsController.java` (+`PATCH` endpoint, +`UpdateVerticalProfileRequest` DTO), `TenantProvisioningService.java` (extend `setVerticalProfile()`), `AccessRequestApprovalService.java` (fix mapping), `accounting-za.json` (+`enabledModules` field)

### Architecture Decisions

- **Hybrid registry**: `VerticalProfileRegistry` reads classpath JSON for operational data (packs, rates, terminology). `VerticalModuleRegistry` defines module metadata in Java code (type-safe, shared constants with guard). See ADR-189.
- **Profile switching is owner-only**: `updateVerticalProfile()` checks org role inside the service method. The controller uses `@RequiresCapability("TEAM_OVERSIGHT")` as a baseline, then the service adds the owner check. See architecture doc Section 3.7.
- **`enabled_modules` set by profile, not user-supplied**: The `UpdateVerticalProfileRequest` contains only `verticalProfile` string. The service reads `enabledModules` from the registry. Direct modification of `enabled_modules` is platform-admin-only (ADR-192).
- **Fix `law-za` to `legal-za`**: The `INDUSTRY_TO_PROFILE` map currently has `"Legal" -> "law-za"` which doesn't match the profile ID convention.
- **Pack application is additive**: Profile switching triggers seeders but existing data is untouched. Seeders track applied packs via `*_pack_status` columns.

---

## Epic 369: Legal Module Stub Controllers

**Goal**: Create stub controllers for the three legal modules (trust accounting, court calendar, conflict check) with `VerticalModuleGuard` integration. Each controller has a single `GET /api/{module}/status` endpoint that returns a stub JSON response when the module is enabled and 403 when it is not.

**References**: Architecture doc Sections 2.2, 4.5, 8.4, 8.6. ADR-190.

**Dependencies**: Epic 367 (needs `VerticalModuleGuard`, `ModuleNotEnabledException`).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **369A** | 369.1--369.5 | 3 stub controllers in `verticals/legal/` sub-packages, guard integration, integration tests (~6). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 369.1 | Create `TrustAccountingController` | 369A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java`. `@RestController @RequestMapping("/api/trust-accounting")`. Single endpoint: `GET /status`. Inject `VerticalModuleGuard`, call `moduleGuard.requireModule("trust_accounting")` at top of method. Return `ModuleStatusResponse("trust_accounting", "stub", "Trust Accounting is not yet implemented. It will be available in a future release.")`. `@RequiresCapability("FINANCIAL_VISIBILITY")`. |
| 369.2 | Create `CourtCalendarController` | 369A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarController.java`. Same pattern as 369.1 but with `moduleGuard.requireModule("court_calendar")`, `@RequiresCapability("PROJECT_MANAGEMENT")`, and route `/api/court-calendar/status`. |
| 369.3 | Create `ConflictCheckController` | 369A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckController.java`. Same pattern with `moduleGuard.requireModule("conflict_check")`, `@RequiresCapability("PROJECT_MANAGEMENT")`, and route `/api/conflict-check/status`. |
| 369.4 | Create shared `ModuleStatusResponse` record | 369A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/ModuleStatusResponse.java`. Record: `ModuleStatusResponse(String module, String status, String message)`. Shared by all 3 stub controllers. |
| 369.5 | Write integration tests for stub controllers | 369A | 369.1, 369.2, 369.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/LegalModuleStubControllerTest.java`. 6 tests (2 per controller): (1) returns 403 when module not in `enabled_modules`, (2) returns 200 with stub JSON when module is enabled. Setup: create tenant with `OrgSettings.enabledModules = ["trust_accounting", "court_calendar", "conflict_check"]` for success tests, empty list for failure tests. Use MockMvc + JWT mocking. |

### Key Files

**Create:** `TrustAccountingController.java`, `CourtCalendarController.java`, `ConflictCheckController.java`, `ModuleStatusResponse.java`, `LegalModuleStubControllerTest.java`

### Architecture Decisions

- **One endpoint per stub**: Each module gets only `GET /status` in this phase. Future phases add the full CRUD endpoints.
- **Guard call at top of method**: Matches `CustomerLifecycleGuard` pattern. Visible to code reviewers. See ADR-190.
- **Package structure**: `verticals/legal/trustaccounting/`, `verticals/legal/courtcalendar/`, `verticals/legal/conflictcheck/`. Establishes the package hierarchy for future module development.
- **Shared response record**: `ModuleStatusResponse` lives in the `verticals/` package (not in per-module sub-packages) since all stubs return the same shape.
- **Capability requirements**: Trust accounting requires `FINANCIAL_VISIBILITY` (financial data). Court calendar and conflict check require `PROJECT_MANAGEMENT` (project/matter workflow). Guard check composes with capability check (both must pass).

---

## Epic 370: Frontend OrgProfileProvider + ModuleGate + Sidebar

**Goal**: Create the `OrgProfileProvider` context and `useOrgProfile()` hook (following `TerminologyProvider` pattern), the `ModuleGate` component (following `RequiresCapability` pattern), extend the `OrgSettings` TypeScript interface with new fields, integrate the provider into the layout, and add module-based filtering to sidebar navigation.

**References**: Architecture doc Sections 6.1, 6.2, 6.3, 6.4, 8.2, 8.5. ADR-190.

**Dependencies**: Epic 367 (backend must return `enabledModules` and `terminologyNamespace` in settings response).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **370A** | 370.1--370.8 | `OrgProfileProvider`, `ModuleGate`, types update, layout integration, `NavItem.requiredModule`, `NavZone` filtering, frontend tests (~6). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 370.1 | Create `OrgProfileProvider` context | 370A | — | New file: `frontend/lib/org-profile.tsx`. `"use client"` context with `OrgProfileContextValue { verticalProfile, enabledModules, terminologyNamespace, isModuleEnabled }`. Provider props are serializable (string, string[], string). `isModuleEnabled` uses `Set.has()` for O(1) lookup. `useMemo` with `JSON.stringify(enabledModules)` as key (matches `CapabilityProvider` pattern). Hook: `useOrgProfile()`. Pattern: `lib/terminology.tsx`, `lib/capabilities.tsx`. |
| 370.2 | Create `ModuleGate` component | 370A | 370.1 | New file: `frontend/components/module-gate.tsx`. `"use client"` component. Props: `module: string`, `fallback?: ReactNode`, `children: ReactNode`. Uses `useOrgProfile().isModuleEnabled(module)`. Renders children when enabled, fallback when not. Pattern: `RequiresCapability` in `lib/capabilities.tsx`. |
| 370.3 | Extend `OrgSettings` TypeScript interface | 370A | — | Modify: `frontend/lib/types/settings.ts`. Add `enabledModules?: string[]` and `terminologyNamespace?: string | null` to `OrgSettings` interface. The `verticalProfile` field already exists. |
| 370.4 | Integrate `OrgProfileProvider` into layout | 370A | 370.1, 370.3 | Modify: `frontend/app/(app)/org/[slug]/layout.tsx`. Extract `enabledModules` and `terminologyNamespace` from `settingsResult` alongside existing `verticalProfile`. Add `OrgProfileProvider` between `CapabilityProvider` and `TerminologyProvider` in the provider chain. Pass `verticalProfile`, `enabledModules` (default `[]`), `terminologyNamespace` (default `null`) as props. |
| 370.5 | Add `requiredModule` to `NavItem` interface | 370A | — | Modify: `frontend/lib/nav-items.ts`. Add `requiredModule?: string` to `NavItem` interface. Add 2 new nav items to `NAV_GROUPS`: Trust Accounting in "finance" zone (`requiredModule: "trust_accounting"`, `requiredCapability: "FINANCIAL_VISIBILITY"`, icon: `Scale` from lucide-react, route: `/org/[slug]/trust-accounting`), Court Calendar in "work" zone (`requiredModule: "court_calendar"`, `requiredCapability: "PROJECT_MANAGEMENT"`, icon: `Gavel` from lucide-react, route: `/org/[slug]/court-calendar`). |
| 370.6 | Extend `NavZone` to filter by `requiredModule` | 370A | 370.1, 370.5 | Modify: `frontend/components/nav-zone.tsx`. Import `useOrgProfile`. Add `isModuleEnabled` to destructured context. Extend `visibleItems` filter: `(!item.requiredModule \|\| isModuleEnabled(item.requiredModule))`. |
| 370.7 | Write frontend test for `OrgProfileProvider` and `ModuleGate` | 370A | 370.1, 370.2 | New file: `frontend/__tests__/org-profile.test.tsx`. 4 tests: (1) `useOrgProfile` provides correct verticalProfile, (2) `isModuleEnabled` returns true for enabled module, (3) `isModuleEnabled` returns false for disabled module, (4) `ModuleGate` renders children when enabled, renders fallback when disabled. Wrap in `OrgProfileProvider` with known props. Pattern: `__tests__/terminology.test.tsx`. |
| 370.8 | Write frontend test for sidebar module filtering | 370A | 370.6 | Modify or extend: `frontend/components/__tests__/nav-zone.test.tsx`. 2 tests: (1) nav item with `requiredModule: "trust_accounting"` is visible when module is enabled, (2) hidden when module not enabled. Wrap NavZone in both `CapabilityProvider`, `OrgProfileProvider`, and `TerminologyProvider`. |

### Key Files

**Create:** `lib/org-profile.tsx`, `components/module-gate.tsx`, `__tests__/org-profile.test.tsx`

**Modify:** `lib/types/settings.ts` (+2 fields), `app/(app)/org/[slug]/layout.tsx` (+`OrgProfileProvider` in chain), `lib/nav-items.ts` (+`requiredModule` field, +2 nav items), `components/nav-zone.tsx` (+module filtering), `components/__tests__/nav-zone.test.tsx` (+2 tests)

### Architecture Decisions

- **Provider nesting order**: `CapabilityProvider > OrgProfileProvider > TerminologyProvider`. The `OrgProfileProvider` needs to be above `TerminologyProvider` so that the terminology provider can theoretically read from it in the future. Both are independent contexts for now.
- **Serializable props**: The layout (Server Component) passes only strings and arrays to the provider (Client Component). No functions or component references cross the RSC boundary.
- **No `"use client"` on NavZone test**: NavZone is already `"use client"`, so the test renders it as a client component naturally.
- **Icon choices**: `Scale` for Trust Accounting (legal/financial), `Gavel` for Court Calendar (legal proceedings). Both from lucide-react. Conflict Check does not get a top-level nav item per architecture doc.

---

## Epic 371: Legal Stub Pages + Conditional Sections + Settings Profile Switching

**Goal**: Create the three legal module stub pages (trust accounting, court calendar, conflict check), add conditional sections to existing pages (trust balance card on customer detail, conflict check section on new project dialog), and build the settings profile switching UI.

**References**: Architecture doc Sections 6.5, 6.6, 6.8, 8.2, 8.5.

**Dependencies**: Epic 368 (backend profile switching endpoint, `GET /api/profiles`), Epic 370 (frontend `OrgProfileProvider`, `ModuleGate`).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **371A** | 371.1--371.6 | 3 stub pages, conditional trust balance card on customer detail, conditional conflict check section on new project dialog, frontend tests (~6). Frontend only. | |
| **371B** | 371.7--371.11 | Settings profile switching: profile dropdown, confirmation dialog, `PATCH` call, re-fetch, frontend tests (~4). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 371.1 | Create trust accounting stub page | 371A | — | New file: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`. Server Component. Fetch settings, check if `trust_accounting` module enabled (redirect to 404 or show access denied if not). Render polished placeholder with page layout, breadcrumbs link, `Card` with "Trust Accounting" heading, "Coming Soon" `Badge` (variant `neutral`), description text. Use `useTerminology` for "Client" vs "Customer". Pattern: existing stub pages structure. |
| 371.2 | Create court calendar stub page | 371A | — | New file: `frontend/app/(app)/org/[slug]/court-calendar/page.tsx`. Same pattern as 371.1 but for `court_calendar` module. Description: "Court date tracking and deadline management." Use `Gavel` icon. |
| 371.3 | Create conflict check stub page | 371A | — | New file: `frontend/app/(app)/org/[slug]/conflict-check/page.tsx`. Same pattern for `conflict_check` module. Description: "Matter conflict of interest checks." This page is accessed from customer/project detail (not sidebar nav). |
| 371.4 | Add conditional trust balance card to customer detail | 371A | — | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`. Add `ModuleGate` wrapping a `Card` with `CardHeader` ("Trust Balance" + "Coming Soon" Badge) and `CardContent` (placeholder text: "Trust Accounting module will display client trust balances here."). Gate on `module="trust_accounting"`. The page must be converted to include a `"use client"` wrapper component for the ModuleGate section (or extract a client component). |
| 371.5 | Add conditional conflict check section to new project dialog | 371A | — | Find the new project dialog component (likely `frontend/components/projects/create-project-dialog.tsx`). Add `ModuleGate` wrapping a disabled "Run Conflict Check" button with explanatory text. Gate on `module="conflict_check"`. |
| 371.6 | Write frontend tests for stub pages | 371A | 371.1, 371.2, 371.3 | New file: `frontend/__tests__/stub-pages.test.tsx`. 4 tests: (1) trust accounting page renders "Coming Soon" badge and description, (2) court calendar page renders correctly, (3) conditional trust balance card renders inside `ModuleGate` when enabled, (4) conditional trust balance card hidden when module disabled. Need to wrap in `OrgProfileProvider` and `TerminologyProvider`. 2 additional tests for conflict check conditional section. |
| 371.7 | Create profile switching server action | 371B | — | New file: `frontend/app/(app)/org/[slug]/settings/general/profile-actions.ts`. Server action `updateVerticalProfile(slug: string, verticalProfile: string | null)` calls `PATCH /api/settings/vertical-profile`. Server action `fetchProfiles()` calls `GET /api/profiles`. |
| 371.8 | Create `VerticalProfileSection` client component | 371B | 371.7 | New file: `frontend/components/settings/vertical-profile-section.tsx`. `"use client"` component. Props: `slug: string`, `currentProfile: string | null`, `isOwner: boolean`. If not owner, show read-only profile name. If owner: dropdown populated from `GET /api/profiles` (fetch via SWR), confirmation dialog with warning ("Changing your vertical profile will add new field definitions, templates, and enable additional modules. Your existing data will not be affected."), submit calls server action. On success, call `router.refresh()` to re-fetch layout data and update `OrgProfileProvider`. |
| 371.9 | Integrate `VerticalProfileSection` into General settings page | 371B | 371.8 | Modify: `frontend/app/(app)/org/[slug]/settings/general/page.tsx`. Pass `currentProfile={settings.verticalProfile ?? null}` and `isOwner={caps.isOwner}` to `VerticalProfileSection`. Place between page heading and `GeneralSettingsForm`. |
| 371.10 | Write frontend test for profile section (owner view) | 371B | 371.8 | New file: `frontend/__tests__/settings/vertical-profile-section.test.tsx`. 2 tests: (1) renders dropdown for owner, (2) shows read-only text for non-owner. Mock `fetchProfiles` to return profile list. |
| 371.11 | Write frontend test for confirmation dialog | 371B | 371.8 | Extend same test file. 2 tests: (1) confirmation dialog appears on profile change, (2) dialog dismisses on cancel without API call. |

### Key Files

**Create:** `trust-accounting/page.tsx`, `court-calendar/page.tsx`, `conflict-check/page.tsx`, `settings/general/profile-actions.ts`, `components/settings/vertical-profile-section.tsx`, `__tests__/stub-pages.test.tsx`, `__tests__/settings/vertical-profile-section.test.tsx`

**Modify:** `customers/[id]/page.tsx` (+conditional trust balance card), `components/projects/create-project-dialog.tsx` (+conditional conflict check section), `settings/general/page.tsx` (+`VerticalProfileSection`)

### Architecture Decisions

- **Stub pages are Server Components**: They fetch settings on the server and redirect if the module is not enabled. The "Coming Soon" content is static -- no client interactivity needed.
- **Conditional sections use `ModuleGate`**: The trust balance card and conflict check button are wrapped in `ModuleGate` (client component). This may require extracting a client component wrapper if the parent page is a Server Component.
- **Profile switching uses `router.refresh()`**: After a successful profile change, the layout re-fetches settings, updating `OrgProfileProvider` with the new `enabledModules` and `terminologyNamespace`. This triggers a full re-render of the provider tree.
- **SWR for profile list**: The dropdown fetches profiles via SWR (conditional fetch when dropdown is open). This avoids blocking the settings page load with an additional API call.

---

## Epic 372: Legal Terminology + End-to-End Integration Tests

**Goal**: Add the `legal-za` terminology mapping and write comprehensive integration tests that verify the full vertical architecture: profile switching, provisioning with legal profile, guard behavior, sidebar gating, and terminology overrides.

**References**: Architecture doc Sections 6.7, 8.6.

**Dependencies**: Epics 367, 368, 369, 370 (all backend and frontend infrastructure must be in place).

**Scope**: Backend (372A) + Frontend (372B)

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **372A** | 372.1--372.4 | Backend integration tests: full profile switching flow, provisioning with legal-za profile, guard + module status cross-verification (~6 tests). Backend only. | |
| **372B** | 372.5--372.9 | Frontend: legal terminology map, integration tests for sidebar gating, terminology overrides, stub page rendering (~6 tests). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 372.1 | Write E2E integration test: profile switching lifecycle | 372A | — | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileIntegrationTest.java`. 2 tests: (1) Switch from `null` to `legal-za` profile: verify `enabledModules` set to 3 legal modules, `terminologyNamespace` set to `en-ZA-legal`, audit event logged, settings response includes all new fields. (2) Switch from `legal-za` to `consulting-generic`: verify `enabledModules` set to `[]`, `terminologyNamespace` set to `null`. |
| 372.2 | Write E2E integration test: provisioning with legal profile | 372A | — | Extend or add to `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningServiceIntegrationTest.java`. 2 tests: (1) Provision tenant with `"legal-za"` profile: verify `OrgSettings.enabledModules = ["trust_accounting", "court_calendar", "conflict_check"]`, `terminologyNamespace = "en-ZA-legal"`, `currency = "ZAR"`. (2) Verify trust accounting stub endpoint returns 200 for this tenant. |
| 372.3 | Write E2E integration test: guard denies unprovisioned module access | 372A | — | Extend `VerticalProfileIntegrationTest.java`. 1 test: Tenant provisioned with `consulting-generic` (no modules). Call `GET /api/trust-accounting/status` — expect 403. Switch to `legal-za` profile. Call same endpoint — expect 200. Verifies the full guard + profile switching + stub controller chain. |
| 372.4 | Write integration test: `GET /api/modules` reflects profile changes | 372A | — | Extend `VerticalProfileControllerTest.java`. 1 test: Initial modules response shows all `enabled: false`. Switch to `legal-za`. Modules response shows 3 modules as `enabled: true`. |
| 372.5 | Add `legal-za` entry to terminology map | 372B | — | Modify: `frontend/lib/terminology-map.ts`. Add `"legal-za": { Project: "Matter", Projects: "Matters", project: "matter", projects: "matters", Task: "Work Item", Tasks: "Work Items", task: "work item", tasks: "work items", Customer: "Client", Customers: "Clients", customer: "client", customers: "clients", Proposal: "Engagement Letter", Proposals: "Engagement Letters", proposal: "engagement letter", proposals: "engagement letters", "Time Entry": "Fee Note", "Time Entries": "Fee Notes", "Rate Card": "Tariff Schedule", "Rate Cards": "Tariff Schedules", Document: "Pleading", Documents: "Pleadings" }`. Pattern: existing `"accounting-za"` entry. |
| 372.6 | Write frontend test: legal terminology overrides | 372B | 372.5 | Extend: `frontend/__tests__/terminology-integration.test.tsx`. 3 tests: (1) `t("Project")` returns "Matter" for `legal-za` profile, (2) `t("Customer")` returns "Client" for `legal-za`, (3) `t("Document")` returns "Pleading" for `legal-za`. |
| 372.7 | Write frontend test: sidebar shows legal nav items | 372B | — | Extend: `frontend/components/__tests__/nav-zone.test.tsx` or `__tests__/desktop-sidebar.test.tsx`. 2 tests: (1) "Trust Accounting" nav item visible when `enabledModules` includes `trust_accounting` and user has `FINANCIAL_VISIBILITY` capability, (2) "Trust Accounting" nav item hidden when `enabledModules` is empty. Wrap in `OrgProfileProvider` + `CapabilityProvider` + `TerminologyProvider`. |
| 372.8 | Write frontend test: trust accounting stub page with legal terminology | 372B | 372.5 | New test or extend `__tests__/stub-pages.test.tsx`. 1 test: Trust accounting stub page wrapped in `TerminologyProvider` with `verticalProfile="legal-za"` shows "Client" (not "Customer") in description text. |
| 372.9 | Verify `afterEach` cleanup in all new test files | 372B | — | All test files that render Radix UI components (Dialog in profile section, AlertDialog in confirmation) must have `afterEach(() => cleanup())`. Review and add where missing. Not a separate task — checklist item for 372.6-372.8. |

### Key Files

**Create:** `VerticalProfileIntegrationTest.java`

**Modify:** `frontend/lib/terminology-map.ts` (+`legal-za` entry), `frontend/__tests__/terminology-integration.test.tsx` (+3 tests), `frontend/components/__tests__/nav-zone.test.tsx` (+2 tests), `frontend/__tests__/stub-pages.test.tsx` (+1 test), `TenantProvisioningServiceIntegrationTest.java` (+2 tests), `VerticalProfileControllerTest.java` (+1 test)

### Architecture Decisions

- **Legal terminology follows accounting-za pattern**: Same map structure in `terminology-map.ts`, same key conventions (both case variants: "Project" and "project").
- **Integration tests verify the full chain**: Profile switching -> OrgSettings update -> guard behavior -> module status API. This catches regressions in the profile -> module -> guard pipeline.
- **`afterEach(() => cleanup())` required**: Radix UI components in confirmation dialogs leak DOM between tests. All new test files with Dialog/AlertDialog must include cleanup. Per frontend CLAUDE.md.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `SettingsResponse` record extension breaks existing frontend parsing | Low | Medium | TypeScript `OrgSettings` interface already has `verticalProfile` as optional. New fields are also optional. No breaking change. |
| `VerticalProfileRegistry` fails to load JSON files on classpath | Medium | High | Integration test in 368A verifies all registered profiles have valid JSON files. Startup failure is immediate and visible. |
| `OrgSettings` JSONB `enabledModules` column not compatible with Hibernate JSONB mapping | Low | High | Use `@JdbcTypeCode(SqlTypes.JSON)` which is the established pattern in the codebase (other JSONB columns on `OrgSettings` use the same mapping). |
| `NavZone` module filtering breaks existing sidebar for tenants without modules | Low | High | `requiredModule` is optional on `NavItem`. The filter `(!item.requiredModule \|\| isModuleEnabled(item.requiredModule))` passes when `requiredModule` is undefined. Verified by existing nav-zone tests. |
| Profile switching accidentally removes existing data | Medium | High | Pack application is additive (seeders track applied packs). OrgSettings fields are overwritten with profile values, not cleared. Test 372.1 verifies round-trip switching. |
| Guard caching stale `enabledModules` after profile switch | Low | Medium | Hibernate L1 cache is per-persistence-context (per-request). After profile switch, next request gets fresh data. No cross-request caching concern. |
| `INDUSTRY_TO_PROFILE` fix breaks existing provisioning tests | Low | Low | No existing tests reference `"law-za"` as a valid profile. The fix changes an unused mapping. |

---

## Test Summary

| Epic | Slice | New Tests | Coverage |
|------|-------|-----------|----------|
| 367 | 367A | ~8 | Guard unit (4), exception unit (2), settings response integration (2) |
| 368 | 368A | ~6 | Module registry unit (2), profile registry unit (2), controller integration (2) |
| 368 | 368B | ~8 | Profile switching integration (3), provisioning integration (2), access request mapping (1), audit event (1), idempotency (1) |
| 369 | 369A | ~6 | Stub controller integration: 403 when disabled (3), 200 when enabled (3) |
| 370 | 370A | ~6 | OrgProfileProvider (2), ModuleGate (2), sidebar filtering (2) |
| 371 | 371A | ~6 | Stub pages (3), conditional sections (3) |
| 371 | 371B | ~4 | Profile section owner/non-owner (2), confirmation dialog (2) |
| 372 | 372A | ~6 | Profile switching lifecycle (2), provisioning E2E (2), guard chain (1), modules response (1) |
| 372 | 372B | ~6 | Terminology overrides (3), sidebar gating (2), stub page terminology (1) |
| **Total** | | **~56** | Full vertical architecture: guard, profiles, provisioning, switching, frontend context, sidebar, stub pages, terminology |

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` - Core entity to extend with `enabledModules` and `terminologyNamespace` fields
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` - Must add `getEnabledModulesForCurrentTenant()`, `updateVerticalProfile()`, and extend `toSettingsResponse()`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/layout.tsx` - Provider chain must be extended with `OrgProfileProvider`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/nav-zone.tsx` - Must add module-based filtering alongside existing capability filtering
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java` - Pattern reference for `VerticalModuleGuard` implementation