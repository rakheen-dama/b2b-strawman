# Vertical Profiles

**Status:** filled (Phase C).
**Bounded context:** see [`10-bounded-contexts.md` § vertical-profiles](../10-bounded-contexts.md).
**See also:** the cross-cutting page [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) covers *how* verticality threads through every module (terminology overlay, the nine-layer trust defence, profile-gated nav, portal gates). This module page covers the *implementation* — the JSON profile loader, the in-memory module registry, the reconciliation seeder, and the gate component.

---

## 1. Purpose

Profile-driven verticalisation. Kazi runs **one JAR for every tenant**; verticality is a runtime configuration of three orthogonal knobs stored on `OrgSettings`:

1. `verticalProfile` — string identifier (e.g. `"legal-za"`) loaded from a classpath JSON file `→ backend/src/main/resources/vertical-profiles/legal-za.json`.
2. `enabledModules` — list of module slugs (`["trust_accounting", "court_calendar", …]`) backing every `requireModule(slug)` self-defence call site.
3. `terminologyNamespace` — string (`"en-ZA-legal"`) consumed by the frontend `TerminologyProvider` to render Customer→Client, Project→Matter, Invoice→Fee Note.

Profiles live as JSON resources on the classpath; profile *state* lives on `OrgSettings`; module identity lives in code (`VerticalModuleRegistry`). The reconciliation seeder runs at provisioning **and on every backend boot** (per [`tenancy-provisioning.md`](tenancy-provisioning.md) idempotent-on-boot design) — it merges the profile's declared `enabledModules` into the tenant row and reconciles the tax label. Adding a new vertical means adding one JSON file plus any new module IDs to the registry — no code change to the loader.

> The whole module IS the verticalisation infrastructure. Every other context defers verticality to one of the three knobs. The single most important architectural risk in the entire codebase lives in §10 below: **the reconciler only adds, it never removes.** Read §10 before changing anything.

---

## 2. Entities owned

This module owns **no JPA entities**. Profile *definitions* are immutable JSON resources loaded into an in-memory `Map<String, ProfileDefinition>` at startup; module *definitions* are hard-coded Java records in `VerticalModuleRegistry`; the *per-tenant state* (selected profile, enabled-module list, terminology namespace) lives on `OrgSettings` and is owned by `settings-navigation`.

| Concept | Where it lives | Owning context |
|---|---|---|
| `ProfileDefinition` (record) | in-memory, parsed from JSON | this module |
| `ModuleDefinition` (record) | in-memory, hard-coded | this module |
| `OrgSettings.verticalProfile` (column `vertical_profile`) | tenant DB row `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:177` | settings-navigation |
| `OrgSettings.enabledModules` (column `enabled_modules`, JSONB list) `→ OrgSettings.java:181` | tenant DB row | settings-navigation |
| `OrgSettings.terminologyNamespace` (column `terminology_namespace`) `→ OrgSettings.java:184` | tenant DB row | settings-navigation |

**Schema migrations** (owned by tenancy-provisioning, listed for traceability):
- `V70__add_vertical_profile.sql` — adds `vertical_profile`, `terminology_namespace` columns.
- `V75__add_vertical_modules.sql` — adds `enabled_modules` JSONB column.

The fact that this module owns no entities is load-bearing: it means *every* per-tenant verticality decision is queryable through one row (`OrgSettings`) — there is no second source of truth. Per ADR-192 (`enabled_modules` authority), `OrgSettings.enabledModules` is the *single* runtime authority — the JSON file is the *seed*, not the live truth.

---

## 3. REST surface

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileController.java:14`

| Method | Path | Capability | Returns |
|---|---|---|---|
| `GET` | `/api/profiles` | `TEAM_OVERSIGHT` | `List<ProfileSummary>` — all loaded profiles (id, name, description, declared modules) |
| `GET` | `/api/modules` | `TEAM_OVERSIGHT` | `List<ModuleWithStatus>` — every known module with `enabled: true|false` for the current tenant |

Notable absences from this controller:

- **There is no `PUT /api/vertical-profiles/{id}`.** Profile change is performed through `settings-navigation`'s `OrgSettingsService.updateVerticalProfile(...)` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java:818` and exposed at `PATCH /api/settings/vertical-profile`. This module is read-only via REST.
- **There is no `PUT /api/modules`.** Horizontal-module toggles route through `PUT /api/settings/modules` per phase62 §"Module toggle API" — also owned by `settings-navigation`. This module *defines* what modules exist; it does not *toggle* them.
- The path prefix is `/api`, not `/api/vertical-profiles/*` — both endpoints sit at the top level of `/api`.

---

## 4. Frontend pages / components

| Surface | File | Purpose |
|---|---|---|
| Settings → General → Vertical Profile section | `frontend/app/[org]/[slug]/settings/general/page.tsx` (`VerticalProfileSection` component) | Profile dropdown + change confirmation. Calls `PATCH /api/settings/vertical-profile`. |
| Settings → Features | `frontend/app/[org]/[slug]/settings/features/page.tsx` | Card-based toggles for **horizontal** modules only (vertical modules are not shown — they're profile-driven). See phase62 §"Module categories". |
| `OrgProfileProvider` | `frontend/lib/org-profile.tsx:27` | React context carrying `verticalProfile`, `enabledModules`, `terminologyNamespace`. Built once per request from `GET /api/settings`. |
| `<ModuleGate moduleId="trust_accounting">` | `frontend/components/module-gate.tsx:11` | Conditionally renders children when the module is in `enabledModules`. Layer #6 of the nine-layer trust defence. |
| `TerminologyProvider` + `t("invoices")` | `frontend/lib/terminology.tsx:24` + `frontend/lib/terminology-map.ts` | Reads `terminologyNamespace` from `OrgProfileProvider`; renders e.g. "Fee Notes" instead of "Invoices" under `en-ZA-legal`. |

The portal has its own equivalents (`portal/hooks/use-portal-context.ts` — `tenantProfile` is the portal-side projection of `verticalProfile`).

---

## 5. Domain events

**None.** This module does not emit any `DomainEvent`. Profile changes flow through `OrgSettingsService.updateVerticalProfile(...)` which writes the new value to `OrgSettings` and (synchronously, in the same transaction) re-runs `VerticalProfileReconciliationSeeder.reconcile(...)`. There is no `VerticalProfileChangedEvent` on the bus — downstream code reads `OrgSettings.verticalProfile` directly when it needs the current value.

This is intentional: the profile is queried on every request via `OrgProfileProvider` / `VerticalModuleGuard.requireModule(...)`, so an event-sourced projection would be redundant. Audit of profile changes is captured by `OrgSettingsService` via `AuditDeltaBuilder` (per `settings-navigation`'s general pattern), not via domain events.

---

## 6. Cross-cutting touchpoints

### 6.1 Profile JSON loader

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java:24` — `@Component`, constructor reads `classpath:vertical-profiles/*.json` via `PathMatchingResourcePatternResolver` (line 63–64), parses each file with Jackson, stores into `Collections.unmodifiableMap(...)` (line 128).

The `ProfileDefinition` record (`VerticalProfileRegistry.java:49`) carries:

```java
record ProfileDefinition(
    String profileId,             // "legal-za"
    String name,                  // "Legal (South Africa)"
    String description,
    List<String> enabledModules,  // ["trust_accounting", "court_calendar", ...]
    String terminologyNamespace,  // "en-ZA-legal"  (parsed from JSON key "terminologyOverrides")
    String currency,              // "ZAR"
    Map<String, Object> packs,    // heterogeneous: { field: [...], compliance: [...], template: [...], clause: [...], request: [...] }
    RateCardDefaults rateCardDefaults,
    List<TaxDefault> taxDefaults  // [{ name: "VAT", rate: 15.00, default: true }]
);
```

Adding a new vertical = adding one JSON file. **Zero code changes to the registry.** Malformed JSON files are *skipped with a warning* (line 121–125), not fatal — meaning a typo'd file silently disappears from the registry. (Open question §10.5.)

### 6.2 Module identity registry

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java:24` — hard-coded in-memory map of every known module. Each `ModuleDefinition` carries `id, name, description, status, category (VERTICAL|HORIZONTAL), defaultEnabledFor, navItems`.

Two categories per ADR-239 (`adr/ADR-239-horizontal-vs-vertical-module-gating.md`):

- **VERTICAL** modules — auto-assigned by profile selection. Hidden from Settings → Features. Examples: `trust_accounting`, `court_calendar`, `conflict_check`, `lssa_tariff`, `regulatory_deadlines`, `disbursements`, `matter_closure`, `retainer_agreements`.
- **HORIZONTAL** modules — manually toggled by org admins, profile-independent. Examples: `resource_planning`, `bulk_billing`, `automation_builder`, `information_requests`.

Both categories live in the **same** `OrgSettings.enabledModules` JSONB list. The toggle API at `PUT /api/settings/modules` *merges* horizontal toggles with the existing vertical-driven entries (phase62 §"Why merge instead of replace") so a naïve PUT cannot strip vertical modules.

### 6.3 Module gate (the universal self-defence)

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleGuard.java:23` — `requireModule(String moduleId)` reads `OrgSettings.enabledModules` (cached per-request via `OrgSettingsService` / Hibernate L1) and throws `ModuleNotEnabledException` (HTTP 403) if absent.

Per ADR-190 (module-guard granularity), every conditionally-present service entry point self-defends:

```java
public TrustTransaction record(...) {
  verticalModuleGuard.requireModule("trust_accounting");
  ...
}
```

The duplication (`TrustReconciliationService:43`, `TrustTransactionService:45`, `ClientLedgerService:28`, etc.) is intentional — each is reachable independently, so each one fail-closes (A6 §4). This is **layer #2 of the nine-layer trust defence** (see [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) for the full layer list).

### 6.4 Reconciliation seeder

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileReconciliationSeeder.java:37`

Two concerns live here, both **adds-only / idempotent**:

- **GAP-L-44 — `enabledModules` merge** (`mergeEnabledModules`, line 96). For the tenant's selected profile, append every declared module that is missing from `OrgSettings.enabledModules`. Uses `LinkedHashSet` to preserve insertion order for stable audit diffs (line 104).
- **GAP-L-27 — tax-default reconciliation** (`applyTaxDefaults`, line 125). Set `OrgSettings.taxLabel` to the profile's primary tax-default `name` (e.g. `"VAT"`); rename the legacy `"Standard"` tier seeded by V43 to `"VAT — Standard"` *only if it still has the legacy name* — does not clobber owner-edited names (line 151).

Two invocation paths:

1. **At provisioning** — `TenantProvisioningService.provisionTenant(...)` calls `verticalProfileReconciliationSeeder.reconcile(schemaName, orgId)` as step 5 of the provisioning sequence (`TenantProvisioningService.java:189`).
2. **On every backend boot** — `PackReconciliationRunner` (`provisioning/PackReconciliationRunner.java:40`, `@Order(100)`, `ApplicationRunner`) iterates every row in `org_schema_mapping` and re-runs the full pack-and-profile reconciliation pipeline (`PackReconciliationRunner.java:108–129`). Step 13 of that pipeline is `verticalProfileReconciliationSeeder.reconcile(...)` (line 126). All steps are idempotent; tenants provisioned before a module was added to a profile pick up the new module on the next boot.

Per ADR-244 (pack-only-vertical-profiles), profile changes do **not** call the pack uninstaller — see §10.1.

### 6.5 Provisioning entry point sequence

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:170-189` — five-step sequence (per A6 §4):

1. Resolve `currency` from `country` parameter.
2. `setVerticalProfile(...)` (line 241) — writes `OrgSettings.verticalProfile`, `enabledModules`, `terminologyNamespace`.
3. `installPacksViaPipeline(schemaName, "legal-za", PackType.DOCUMENT_TEMPLATE)` — universal packs first (line 308), then profile-specific (line 313).
4. `installPacksViaPipeline(schemaName, "legal-za", PackType.AUTOMATION_TEMPLATE)`.
5. `verticalProfileReconciliationSeeder.reconcile(schemaName, orgId)` (line 189).

### 6.6 Frontend projection

The `OrgProfileProvider` (`frontend/lib/org-profile.tsx:27`) is built once in `org/[slug]/layout.tsx` from the result of `GET /api/settings`. It carries `verticalProfile, enabledModules, terminologyNamespace` and is the single source of truth for client-side gates (`<ModuleGate>`, `t(...)`). The portal uses an equivalent provider seeded from the portal session context (A6 §4).

---

## 7. Vertical specifics

This module IS the verticalisation infrastructure. Per-vertical content lives elsewhere:

- [`60-verticals/base.md`](../60-verticals/base.md) — concepts that apply to every profile (terminology defaults, universal packs).
- [`60-verticals/legal-za.md`](../60-verticals/legal-za.md) — legal-za enabled modules, packs, terminology overrides, the nine-layer trust defence inventory.
- [`60-verticals/accounting-za.md`](../60-verticals/accounting-za.md) — accounting-za module list, deadlines module, terminology.
- [`60-verticals/consulting-za.md`](../60-verticals/consulting-za.md) — consulting-za / consulting-generic profile contents.
- [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md) — per-vertical pack inventory.

Profile JSON files (the seed data this module loads):

- `→ backend/src/main/resources/vertical-profiles/legal-za.json` — 9 enabled modules including `trust_accounting`; FICA + LSSA tariff packs; `terminologyOverrides: "en-ZA-legal"`.
- `→ backend/src/main/resources/vertical-profiles/accounting-za.json` — accounting-vertical defaults.
- `→ backend/src/main/resources/vertical-profiles/consulting-za.json` — consulting + ZA tax defaults.
- `→ backend/src/main/resources/vertical-profiles/consulting-generic.json` — empty `enabledModules`, generic terminology, ZAR rate-card defaults; the "no-vertical" fallback.

---

## 8. Active ADRs

| ADR | Title | What it pins down |
|---|---|---|
| ADR-076 | module-identity-contract (per `10-bounded-contexts.md`; the index file shows ADR-076 as `separate-portal-app` — that's a stale index entry, the load-bearing reference is the bounded-contexts entry) | Module slugs are the contract; every gate uses the same slug; fail-closed when missing. |
| ADR-091 | feature-flag-scope `→ adr/ADR-091-feature-flag-scope.md` | Feature flags are per-tenant (on `OrgSettings`), not per-user, not global. |
| ADR-181 | vertical-profile-structure | The fields a profile JSON declares. |
| ADR-184 | vertical-scoped-pack-filtering | Packs declare which profiles they belong to; the pack catalog filters by profile. |
| ADR-189 | vertical-profile-storage | Why `verticalProfile` lives on `OrgSettings` (one row, one source of truth). |
| ADR-190 | module-guard-granularity | Every entry point self-defends with `requireModule(...)` — no shared umbrella gate. |
| ADR-191 | schema-uniformity-module-tables | Module tables exist in every schema regardless of profile (no conditional DDL). |
| ADR-192 | enabled-modules-authority | `OrgSettings.enabledModules` is the runtime authority; the JSON file is seed only. |
| ADR-212 | module-capability-mapping | Modules and capabilities are orthogonal — `MANAGE_TRUST` is a capability, `trust_accounting` is a module; both gates stack. |
| ADR-239 | horizontal-vs-vertical-module-gating `→ adr/ADR-239-horizontal-vs-vertical-module-gating.md` | The `ModuleCategory` split (VERTICAL auto by profile, HORIZONTAL admin-toggled). |
| ADR-244 | pack-only-vertical-profiles | Profiles do not own DDL — they only declare which packs to install. |
| ADR-245 | localized-profile-derivatives | A profile may localise (e.g. `legal-za` vs a hypothetical `legal-uk`); locale is part of the profile. |
| ADR-246 | profile-gated-dashboard-widgets | Dashboard widgets opt in by profile (e.g. `TeamUtilizationWidget` only on `consulting-za`). |
| ADR-092 | auto-apply-strategy `→ adr/ADR-092-auto-apply-strategy.md` | Field groups auto-apply by entity type — relevant because field-pack auto-apply rides on the same profile→pack pipeline. |
| ADR-183 | qa-methodology-vertical-readiness | Per-vertical QA gates before claiming a profile is production-ready. |

---

## 9. Key flows

- **Pack install + vertical onboarding** — [`50-flows/pack-install-and-vertical-onboarding.md`](../50-flows/pack-install-and-vertical-onboarding.md). Covers the five-step provisioning sequence, the seeder pipeline, and post-hoc profile change.
- **The nine-layer trust defence flow** — lives in [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §"Trust accounting nine-layer defense". Not duplicated here. Layers #1 (profile registry), #2 (backend service gate), #5 (`<ModuleGate>` component), #6 (page-level server gate), #8 (portal nav gate), #9 (portal page redirect) all read state owned/projected by this module.

---

## 10. Open questions / known fragility

> The four items in this section are the single most important architectural risks in the codebase. Read them before changing the reconciler, the loader, or `ModuleCategory`.

### 10.1 The reconciler only adds — switching profile orphans data **(critical, known fragility, A6 §4 + ADR-244)**

`VerticalProfileReconciliationSeeder.reconcile(...)` performs an **adds-only merge**: every module declared by the new profile is appended to `OrgSettings.enabledModules`; nothing is ever removed. There is no inverse "drainSeeder" that uninstalls packs, removes module slugs, drops field definitions, or hides terminology overrides when a profile is changed.

**Concrete failure modes** when a tenant is moved `legal-za → consulting-generic`:

- `trust_accounting` remains in `enabledModules` (because `consulting-generic.enabledModules` is `[]`, nothing gets *removed*). The trust-accounting service entry points still pass their `requireModule(...)` checks. UI is hidden by nav-tree filters that key off `verticalProfile`, but the API is reachable.
- Trust account rows, client-ledger rows, LPFF interest runs, Section 86 investment records — all remain in the tenant schema. The `verticals/legal/trustaccounting/*` tables exist in every schema regardless of profile (per ADR-191), so the data is queryable.
- Legal document templates (`legal-za` template pack) remain installed. Conveyancing clauses remain. FICA checklist templates remain. The `pack_install` table still has rows for them.
- Terminology namespace switches (`en-ZA-legal` → null/default), so the UI says "Customers" and "Projects" again — but matters that were created with `Project.matter_*` legal-only column data still carry that data.

**Why this is the dominant risk:** the system is *advertised* as profile-switchable. It is **one-way safe** (consulting → legal lights everything up correctly via the boot reconciler) but **reversible-dirty**. The boot reconciler will *re-add* legal modules every time the JVM restarts if the profile string is still `"legal-za"`, but a profile **change** to a different string just stops adding — it never subtracts. Switching back to legal therefore re-merges legal modules cleanly; switching *away* leaves a zombie legal install.

**Work required to fix** (not yet planned): a `VerticalProfileDrainSeeder` that, on profile change only (not on boot), computes the symmetric difference between old-profile and new-profile module/pack sets and either soft-disables or fully uninstalls the orphaned items. Trust accounting in particular must never be auto-uninstalled if there is non-zero trust money — that's a regulatory question (LPA s.86), not a software question. For now: **do not advertise legal→consulting profile switches as supported; treat them as "create a new tenant" operations.**

### 10.2 Conflict between profile-declared and admin-toggled modules — who wins?

When a horizontal module (e.g. `information_requests`) is *also* declared in a profile's `enabledModules` (legal-za declares `information_requests`), the situation is:

- The reconciler runs at boot and **adds** `information_requests` to `enabledModules` if missing.
- An admin can disable it via `PUT /api/settings/modules`.
- On the next backend boot, the reconciler runs again and **re-adds** it.

**The reconciler always wins on boot.** An admin's "disable" of a profile-declared horizontal module is therefore *non-durable* — it survives until the next deploy/restart. There is no `disabledModules` overlay to record an explicit opt-out.

This is the design per ADR-192 (the JSON is the floor, the admin can add but not subtract from a profile-declared set), but it is poorly documented and discoverable only by reading the seeder. Phase 62 introduced horizontal toggles without resolving this overlap. **Tracked here as the canonical statement of the rule.**

Recommended resolution paths (not yet decided):
- (a) Treat profile-declared horizontal modules as floors that admins cannot turn off (current behaviour — document it).
- (b) Add a `disabledModules` JSONB column to `OrgSettings` so admins can explicitly opt out, and have the reconciler respect the opt-out.
- (c) Forbid horizontal modules from appearing in any profile JSON's `enabledModules` (use `defaultEnabledFor` in `ModuleDefinition` instead, which the registry already supports — see line 156 of the registry where `information_requests` lists `["legal-za", "accounting-za", "consulting-za"]` in `defaultEnabledFor`).

### 10.3 Profile evolution — do existing tenants backfill new modules?

When a new module is added to `legal-za.json`'s `enabledModules` (e.g. `lssa_tariff` is added in a release), every existing legal-za tenant picks it up on the next backend boot via `PackReconciliationRunner` → `verticalProfileReconciliationSeeder.reconcile(...)`. **This is by design and is GAP-L-44's whole purpose.**

**However**, three classes of profile evolution have undefined behaviour:

- **Pack-list changes** — adding a new pack id to `packs.template` in `legal-za.json` does install on existing tenants (because `PackReconciliationRunner` calls `installPacksViaPipeline` first, before the profile reconciler). Removing a pack id from the JSON does **not** uninstall it (same orphan problem as §10.1).
- **`taxDefaults` changes** — the seeder's `applyTaxDefaults` only renames the *legacy* `"Standard"` tier seeded by V43 (line 151–153). A profile switching from `VAT 15%` to `VAT 16%` mid-life *does not* update existing default tier rates — the rename guard explicitly skips owner-edited names, and "VAT — Standard" is no longer the legacy name after the first reconcile.
- **`terminologyNamespace` changes** — purely a frontend lookup; takes effect immediately on next page load. Safe to evolve.

### 10.4 JSON profile format — schema-validated or string-keyed?

The current loader (`VerticalProfileRegistry.java:62-130`) is **string-keyed and lenient**:

- Missing `profileId` → file is skipped with `log.warn` (line 73–76).
- Malformed JSON → file is skipped with `log.warn` (line 121–125).
- Unknown top-level keys → silently ignored.
- Wrong types in known fields → tax/rate-card parsers `continue` past the bad entry (line 184, 209).

There is **no JSON Schema**, no `@Valid` DTO, no boot-time fail-fast. A typo'd module slug in `enabledModules` (e.g. `"trust_acccounting"` with three c's) will be merged into `OrgSettings.enabledModules` and silently match nothing; every `requireModule("trust_accounting")` will fail-closed. A typo in `terminologyOverrides` will quietly fall back to default copy. A typo in `profileId` will skip the entire file.

**Open decision:**
- (a) Add JSON Schema validation at boot (`VerticalProfileSchema.json`, fail-fast on parse errors so misconfiguration shows up at deploy time, not in a customer's tenant).
- (b) Add an ArchUnit-style "every slug in any profile JSON must exist in `VerticalModuleRegistry`" boot check.
- (c) Leave it lenient (current state) — minimises blast radius of a single bad profile file but maximises silent-failure surface.

The pack catalog already does (b)-style validation for packs (boot-time fail-fast on duplicate `(PackType, packId)` per `30-modules/packs.md`); profiles do not. Inconsistent.

### 10.5 Lenient-skip on malformed JSON

Sub-case of §10.4 worth calling out separately: if a deployment ships with a corrupted or malformed `legal-za.json`, the profile *disappears from the registry*. Every existing legal-za tenant boots successfully but `VerticalProfileRegistry.exists("legal-za")` returns `false` and the reconciler silently no-ops (`VerticalProfileReconciliationSeeder.java:81-87` — `"profile '{}' not in registry"` → return). The tenant continues to function on whatever `enabledModules` it had at last successful reconcile, but newly-declared modules never propagate. There is **no health-check / metric** that surfaces "tenant N has profile X but profile X is no longer in the registry."

---

## 11. Anchor list (quick reference)

- `VerticalProfileRegistry` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java:24`
- `VerticalProfileReconciliationSeeder` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileReconciliationSeeder.java:37`
- `VerticalModuleRegistry` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java:24`
- `VerticalModuleGuard` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleGuard.java:11`
- `VerticalProfileService` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileService.java:12`
- `VerticalProfileController` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileController.java:14`
- `ModuleCategory` enum `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/ModuleCategory.java:8`
- Boot-time runner `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunner.java:40` (`@Order(100)`, ApplicationRunner)
- Provisioning entry `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:170-189`
- `OrgSettings` profile fields `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:177,181,184`
- `OrgSettingsService.updateVerticalProfile` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java:818`
- Profile JSON resources `→ backend/src/main/resources/vertical-profiles/{legal-za,accounting-za,consulting-za,consulting-generic}.json`
- Frontend `OrgProfileProvider` `→ frontend/lib/org-profile.tsx:27`
- Frontend `<ModuleGate>` `→ frontend/components/module-gate.tsx:11`
- Frontend `TerminologyProvider` `→ frontend/lib/terminology.tsx:24`
