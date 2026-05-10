# Multi-Vertical

**Status:** filled (Phase D part 1).
**See also:** module page [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) (the implementation: loader, registry, reconciler), [`30-modules/packs.md`](../30-modules/packs.md) (pack SPI), [`30-modules/trust-accounting.md`](../30-modules/trust-accounting.md) (the worked example), [`60-verticals/legal-za.md`](../60-verticals/legal-za.md) / [`60-verticals/accounting-za.md`](../60-verticals/accounting-za.md) / [`60-verticals/consulting-za.md`](../60-verticals/consulting-za.md) / [`60-verticals/base.md`](../60-verticals/base.md) (per-vertical content inventory).

---

## 1. What this concern covers

Multi-vertical is the **load-bearing concern** of Kazi. The same JAR/binary serves every tenant; verticality is a runtime configuration of **four orthogonal mechanisms** composed over a universal core (`→ _discovery/A6-cross-cutting.md` § 4). There is **no codebase fork** per vertical — `legal-za`, `accounting-za`, `consulting-za`, and `consulting-generic` (the no-vertical fallback) all run the same code path; the differences are entirely in *seed data* (JSON profiles, classpath pack content) and *runtime gates* (slug-keyed `requireModule(...)` calls and `<ModuleGate>` wrappers).

Adding a new vertical requires **no core code branches on `verticalProfile`**. The recipe:

1. Drop a `vertical-profiles/<id>.json` on the classpath (`→ backend/src/main/resources/vertical-profiles/`).
2. Author classpath pack content for `DOCUMENT_TEMPLATE` / `AUTOMATION_TEMPLATE` (and the 11 legacy direct-seeder pack types — see [`packs.md`](../30-modules/packs.md) §1 for the transitional split).
3. Reference any new module slugs from `VerticalModuleRegistry` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java:24`).
4. Add a terminology key to `frontend/lib/terminology-map.ts` and `portal/lib/terminology-map.ts`.

Per-tenant verticality state is one row, three columns on `OrgSettings` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:177,181,184`): `verticalProfile`, `enabledModules`, `terminologyNamespace`. Detailed per-module impact lives under each `30-modules/<slug>.md` "Vertical specifics" section; this page is the cross-cutting mechanism overview, the worked example, and the canonical statement of the dominant fragility (§5).

---

## 2. The four mechanisms

### 2a. Vertical profiles (the seed)

Profiles are classpath-resident JSON files. Four ship today (`→ backend/src/main/resources/vertical-profiles/{legal-za,accounting-za,consulting-za,consulting-generic}.json`).

`VerticalProfileRegistry` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java:24`) is a `@Component` that, at boot, reads every `classpath:vertical-profiles/*.json` via `PathMatchingResourcePatternResolver` (line 62), parses each into a `ProfileDefinition` record (line 49), and stores them in an immutable `Map<String, ProfileDefinition>` (line 128). The record carries `profileId, name, description, enabledModules, terminologyNamespace, currency, packs, rateCardDefaults, taxDefaults`.

Per-tenant *state* lives on `OrgSettings`:
- `OrgSettings.verticalProfile` — the selected profile id (`→ OrgSettings.java:177`).
- `OrgSettings.enabledModules` — JSONB list of module slugs (`→ OrgSettings.java:181`). Per ADR-192 (`enabled-modules-authority`), this is the **runtime authority**; the JSON file is *seed only*.
- `OrgSettings.terminologyNamespace` — string key (`→ OrgSettings.java:184`).

The `VerticalProfileReconciliationSeeder` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileReconciliationSeeder.java:37`) runs at two points (per [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §6.4):

1. **At provisioning** — step 5 of `TenantProvisioningService.provisionTenant(...)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:189`).
2. **On every backend boot** — `PackReconciliationRunner` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunner.java:40`, `@Order(100)` `ApplicationRunner`) iterates every row in `org_schema_mapping` and re-runs the full pack-and-profile pipeline — step 13 is the profile reconciler (line 126).

The seeder is **adds-only / idempotent** (per ADR-192, ADR-244): every module the new profile declares is appended to `OrgSettings.enabledModules` if missing; nothing is ever removed. This is the mechanism that lets a tenant provisioned before a module existed pick it up at next boot — and it is the source of the dominant architectural risk of the codebase (§5).

ADRs: **ADR-181** (vertical-profile-structure), **ADR-189** (vertical-profile-storage), **ADR-192** (enabled-modules-authority), **ADR-244** (pack-only-vertical-profiles), **ADR-245** (localized-profile-derivatives).

### 2b. Packs (the SPI)

The pack SPI is the *content* channel — vertical-specific document templates, automation rules, custom fields, compliance checklists, clauses, project blueprints, request templates, and rate cards ship as installable units rather than baked into one-shot seeders. Per ADR-244, some verticals (consulting-za) ship as **pack-only** — no backend module needed; just JSON content + profile registry entry.

The interface: `PackInstaller` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstaller.java:20`) — `type()`, `availablePacks()`, `install(packId, tenantId, memberId)`, `checkUninstallable(...)`, `uninstall(...)`. Idempotent install + all-or-nothing uninstall (Javadoc lines 13–17).

Aggregation: `PackCatalogService` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogService.java:23`) consumes `List<PackInstaller>` from the Spring context and builds `Map<PackType, PackInstaller>`. Boot-time fail-fast on duplicate registration (line 36).

Tracking: `PackInstall` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstall.java:17`) is the per-tenant durable record (`UNIQUE(pack_id)`); `PackType` enum (`→ packs/PackType.java:7`).

**Scope reality** (per ADR-243): the unified `PackInstaller` pipeline ships with two pack types live (`DOCUMENT_TEMPLATE`, `AUTOMATION_TEMPLATE`); the other 11 historical pack flavours (field, compliance, clause, checklist, request, rate, project-template, schedule, etc.) still ship via direct `AbstractPackSeeder` subclasses called from `TenantProvisioningService` and have no uninstall path. See [`packs.md`](../30-modules/packs.md) §1, §6.

Profile-affinity is enforced on install for non-universal packs (`PackInstallService.enforceProfileAffinity` `→ packs/PackInstallService.java:235`) — a `legal-za` pack cannot be installed into a `consulting-generic` tenant.

ADRs: **ADR-184** (vertical-scoped-pack-filtering), **ADR-208** (pack-verification-approach), **ADR-240** (unified-pack-catalog-install-pipeline — canonical), **ADR-241** (add-only-pack-semantics), **ADR-243** (scope-two-pack-types-for-v1), **ADR-244** (pack-only-vertical-profiles).

### 2c. Terminology overrides (the UI overlay)

Terminology branching has a rare clean separation: backend stores only a *namespace key* (`OrgSettings.terminologyNamespace`); the frontend owns the actual word map.

- Backend: `OrgSettings.terminologyNamespace` (`→ OrgSettings.java:184`). Set on profile change via `OrgSettingsService.updateVerticalProfile(...)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java:818`).
- Frontend: `frontend/lib/terminology-map.ts` carries the `TERMINOLOGY` map keyed by namespace (`legal-za`, `accounting-za`, `consulting-za`); `frontend/lib/terminology.tsx:24` is the `TerminologyProvider` consumed via `t("invoices") → "Fee Notes"`.
- Portal: `portal/lib/terminology-map.ts` is a duplicated copy (separate JS bundle).

The three current per-vertical override sets (per `frontend/lib/terminology-map.ts` and the glossary):

- **legal-za** — Customer→**Client**, Project→**Matter**, Invoice→**Fee Note**, Expense→**Disbursement**, Retainer→**Mandate**, Task→**Action Item**, Audit Log→**Audit Trail**, Budget→**Fee Estimate**, Billing Rate→**Tariff Schedule**.
- **accounting-za** — Customer→**Client**, Project→**Engagement**, Proposal→**Engagement Letter**, Billing Rate→**Fee Schedule**.
- **consulting-za** — Customer→**Client**, Time Entry→**Time Log**, Billing Rate→**Billing Rates**.

There is no `GET /api/settings/terminology` endpoint that returns the *resolved* map — the frontend has the map baked in; the backend just tells it which key to use. This is elegant (no backend round-trip to retitle a UI label) but fragile: renaming a profile key requires changes in both `frontend/lib/terminology-map.ts` and `portal/lib/terminology-map.ts` (`→ _discovery/A6-cross-cutting.md` § 4).

ADRs: **ADR-185** (terminology — canonical, per `90-adr-index.md:353`).

### 2d. Module gates (the runtime fail-closed surface)

`enabledModules` is a string-slug set on `OrgSettings`. Two layers of feature gating cohabit:

**Coarse-grained, domain-level (integration ports)** — `IntegrationGuardService.requireEnabled(IntegrationDomain)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java:25`). PAYMENT/EMAIL/KYC_VERIFICATION are foundational, always-on (line 26). ACCOUNTING/AI/DOCUMENT_SIGNING are checked against `OrgSettings.isAccountingEnabled() / isAiEnabled() / isDocumentSigningEnabled()` (lines 38–44); `IntegrationDisabledException` → HTTP 403. See [`integration-ports.md`](../30-modules/integration-ports.md) §6.

**Fine-grained, slug-level (vertical modules)** — `VerticalModuleGuard.requireModule(String moduleId)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleGuard.java:23`) reads `OrgSettings.enabledModules` and throws `ModuleNotEnabledException` (HTTP 403) on miss. Per ADR-190 (`module-guard-granularity`), every conditionally-present service entry point self-defends — no shared umbrella gate.

`VerticalModuleRegistry` (`→ verticals/VerticalModuleRegistry.java:24`) is the in-code registry of every known module. Each `ModuleDefinition` carries `id, name, description, status, category (VERTICAL|HORIZONTAL), defaultEnabledFor, navItems`. Per ADR-239 (`horizontal-vs-vertical-module-gating`):

- **VERTICAL** modules are auto-assigned by profile selection and hidden from Settings → Features. Examples: `trust_accounting`, `court_calendar`, `conflict_check`, `lssa_tariff`, `regulatory_deadlines`, `disbursements`, `matter_closure`, `retainer_agreements`.
- **HORIZONTAL** modules are admin-toggled, profile-independent. Examples: `resource_planning`, `bulk_billing`, `automation_builder`, `information_requests`. Toggled via `PUT /api/settings/modules` (per phase62 §"Module toggle API").

Both categories live in the **same** `OrgSettings.enabledModules` JSONB list. The toggle API merges horizontal toggles with the existing vertical-driven entries — a naïve `PUT` cannot strip vertical modules.

**Frontend mirror** has *three* gates:

1. **Nav gates** — `frontend/lib/nav-items.ts:32` declares `requiredModule` per entry; `filterNavItems()` removes inapplicable entries.
2. **Page-level server gate** — `frontend/lib/api/settings.ts:21` exposes `isModuleEnabledServer(slug)`; pages call this in their RSC body and short-circuit with a "feature off" placeholder.
3. **Component gate** — `<ModuleGate moduleId="...">` (`frontend/components/module-gate.tsx:11`) reads from `OrgProfileProvider` (`frontend/lib/org-profile.tsx:27`).

**Portal mirror** (separate JS bundle, separate auth filter chain):
- `portal/lib/nav-items.ts:43` declares both `profiles: [...]` and `modules: [...]` per entry.
- Portal pages do client-side `ctx.enabledModules` checks and `router.replace("/home")` on miss.
- Portal data services check the slug too — e.g. `customerbackend/service/PortalTrustLedgerService.java:31` returns **404** (not 403) so the module's existence is hidden from the portal.

ADRs: **ADR-076** (module-identity-contract), **ADR-091** (feature-flag-scope), **ADR-190** (module-guard-granularity), **ADR-191** (schema-uniformity-module-tables — module tables exist in every schema regardless of profile, no conditional DDL), **ADR-212** (module-capability-mapping — modules and capabilities stack, see §3 layer 6), **ADR-239** (horizontal-vs-vertical-module-gating), **ADR-246** (profile-gated-dashboard-widgets).

---

## 3. The worked example: trust accounting defended at nine layers

A6 §4 documents trust accounting as the canonical multi-layered verticalisation example. Trust money is a Legal Practice Act §86 statutory obligation in South Africa — a violation is a criminal/regulatory event, not a bug. The system therefore stacks **nine** fail-closed gates so that *any* single layer being passed through still leaves eight more to refuse. Quoting A6 §238–246 with anchors:

1. **Profile registry** — `legal-za.json` is the **only** profile whose `enabledModules` array contains `"trust_accounting"`. Every other profile leaves the slug absent (`→ backend/src/main/resources/vertical-profiles/legal-za.json`; A6 §237).
2. **Backend service gates** — every trust service self-checks via `verticalModuleGuard.requireModule("trust_accounting")` at the top of every public method (A6 §238). Six entry-point classes:
   - `→ verticals/legal/trustaccounting/TrustAccountService.java:26`
   - `→ verticals/legal/trustaccounting/transaction/TrustTransactionService.java:45`
   - `→ verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java:43`
   - `→ verticals/legal/trustaccounting/ledger/ClientLedgerService.java:28`
   - `→ verticals/legal/trustaccounting/interest/InterestService.java:41`
   - `→ verticals/legal/trustaccounting/investment/TrustInvestmentService.java:36`
3. **Backend invoice export hard guard (ADR-276)** — independent of the module gate, `TrustBoundaryGuard.evaluate(invoice)` runs **inside** the Phase 71 `AccountingSyncService` before any push to Xero. Three refusal conditions (`→ architecture/phase71-xero-accounting-integration.md:793-795`): invoice flagged trust, any line item from a trust account, customer has active trust balances. Fail-closed on DB error (line 799). On refusal, sync entry `state=BLOCKED_TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust` (line 296). "Deterministic Java code -- no LLM, no AI, no human bypass" (line 785).
4. **Frontend nav gate** — `frontend/lib/nav-items.ts` declares the trust nav group with `requiredModule: "trust_accounting"`; `filterNavItems()` hides it on non-legal tenants (A6 §240).
5. **Frontend page server gate** — `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` calls `isModuleEnabledServer("trust_accounting")` and short-circuits with a placeholder if disabled (A6 §241).
6. **Frontend capability gate** — orthogonal to the module gate. Three module-exclusive capabilities (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:7`): `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` (last is `OWNER_ONLY`). Pages wrapped in `<RequiresCapability cap="VIEW_TRUST">` (A6 §242).
7. **Portal nav gate** — `portal/lib/nav-items.ts:43` declares trust nav with **both** `profiles: ["legal-za"]` AND `modules: ["trust_accounting"]` (A6 §243).
8. **Portal page redirect** — `portal/app/(authenticated)/trust/page.tsx` checks `ctx.enabledModules` and `router.replace("/home")` if the module is off (A6 §244).
9. **Portal data endpoints** — `customerbackend/service/PortalTrustLedgerService.java:31` checks `MODULE_ID = "trust_accounting"` and returns **404** (not 403) — the module's *existence* is hidden from portal callers (A6 §245).

A6 §247 verdict, quoted: *"Nine layers. Most are belt-and-braces UX defenses; the load-bearing ones are #2 (backend service gate) and #3 (the export guard). The gateway, notably, has zero awareness of trust-accounting — it transparently proxies whatever the backend chooses to expose."*

Over-engineered for a single feature in isolation. **Justified** because the regulator (Legal Practice Council) audits these books; a non-legal tenant must not be able to reach the surface, and a legal tenant must not be able to *export* trust money to a general-ledger system that has no concept of statutory segregation. The line is drawn at the integration boundary, not the domain boundary — trust invoices can be *created* in Kazi (legal vertical's whole point), they just cannot *leave*. See [`trust-accounting.md`](../30-modules/trust-accounting.md) §6 and [`integration-ports.md`](../30-modules/integration-ports.md) §6 for the deeper write-up.

---

## 4. Per-vertical impact map

Cross-link table — for each vertical, which modules and concerns are touched. Detailed module-by-module impact lives under `30-modules/<slug>.md` "Vertical specifics"; per-vertical content inventory lives under `60-verticals/<id>.md`.

### legal-za (`→ vertical-profiles/legal-za.json`)

| Module | Touchpoint |
|---|---|
| [`trust-accounting.md`](../30-modules/trust-accounting.md) | **Vertical-only** module. Six service-level guards; nine-layer defence. Section 86 investments, LPFF interest runs, client ledgers. |
| [`customer-lifecycle.md`](../30-modules/customer-lifecycle.md) | FICA fields auto-applied via field pack; FICA checklist via compliance pack. |
| [`checklists.md`](../30-modules/checklists.md) | Legal compliance pack (FICA + LSSA). |
| [`expenses.md`](../30-modules/expenses.md) | `LegalDisbursement` sibling entity (ADR-247) — keeps trust commingling out of universal expenses. UI label "Disbursement". |
| [`proposals-acceptance.md`](../30-modules/proposals-acceptance.md) | UI label "Engagement Letter". |
| [`tasks.md`](../30-modules/tasks.md) | UI label "Action Item". |
| [`retainers.md`](../30-modules/retainers.md) | UI label "Mandate". |
| [`invoicing.md`](../30-modules/invoicing.md) | UI label "Fee Note". `Invoice.customFields["is_trust_invoice"]` triggers TrustBoundaryGuard on export. Tariff Schedule (LSSA) drives line-item rates. |
| Legal-vertical entities (under `verticals/legal/`) | `TariffSchedule`, `TariffItem` (LSSA), `AdverseParty`, `AdversePartyLink`, `ConflictCheck`, `CourtDate`, `Prescription`, `LegalDisbursement`, the trust hierarchy. |
| Modules enabled | `trust_accounting`, `court_calendar`, `conflict_check`, `lssa_tariff`, `regulatory_deadlines`, `disbursements`, `matter_closure`, `retainer_agreements`, `information_requests` (also defaultEnabledFor others). |

See [`60-verticals/legal-za.md`](../60-verticals/legal-za.md).

### accounting-za (`→ vertical-profiles/accounting-za.json`)

| Module | Touchpoint |
|---|---|
| [`projects.md`](../30-modules/projects.md) | UI label "Engagement". |
| [`proposals-acceptance.md`](../30-modules/proposals-acceptance.md) | UI label "Engagement Letter". |
| [`invoicing.md`](../30-modules/invoicing.md) | UI label for billing-rate "Fee Schedule". |
| [`retainers.md`](../30-modules/retainers.md) | Period close (existing universal flow). |
| Modules enabled | `regulatory_deadlines` (SARS / CIPC), plus horizontals like `bulk_billing`. |

See [`60-verticals/accounting-za.md`](../60-verticals/accounting-za.md).

### consulting-za (`→ vertical-profiles/consulting-za.json`)

| Module | Touchpoint |
|---|---|
| [`customer-lifecycle.md`](../30-modules/customer-lifecycle.md) | UI label "Client". |
| [`time-entry.md`](../30-modules/time-entry.md) | UI label "Time Log". |
| [`capacity-planning.md`](../30-modules/capacity-planning.md) | `resource_planning` module enabled by default. `TeamUtilizationWidget` self-gates per ADR-246 (profile-gated-dashboard-widgets). |
| Modules enabled | `resource_planning`, plus standard horizontals. |

See [`60-verticals/consulting-za.md`](../60-verticals/consulting-za.md).

### base / consulting-generic (`→ vertical-profiles/consulting-generic.json`)

The "no-vertical" fallback — empty `enabledModules`, generic terminology, ZAR rate-card defaults. Tenants get the universal core only: customers, projects, tasks, time, expenses, invoices, proposals, retainers, documents, automations. No vertical-specific overlays. See [`60-verticals/base.md`](../60-verticals/base.md).

---

## 5. The dominant fragility: profile-switch orphans

> **The single most important architectural risk in the codebase.** Read this before changing the reconciler, before authorising any "switch profile" UI flow, and before claiming legal→consulting tenant migration is supported.

`VerticalProfileReconciliationSeeder.reconcile(...)` is **adds-only by design** — every module declared by the new profile is appended to `OrgSettings.enabledModules`; nothing is ever removed (per ADR-192 / ADR-244, the idempotency invariant — see [`tenancy-provisioning.md`](../30-modules/tenancy-provisioning.md) and [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.1).

Switching a tenant `legal-za → consulting-generic` via `PATCH /api/settings/vertical-profile`:

- Sets `OrgSettings.verticalProfile = "consulting-generic"`. ✓
- Switches `terminologyNamespace`. UI renders "Customer" again (the data still says Matter under the hood). ✓
- *Reconciler runs*: `consulting-generic.enabledModules` is `[]`, so **no modules are added and none are removed**. `trust_accounting` remains in `enabledModules`.
- `TrustAccountService.requireModule("trust_accounting")` therefore still **passes**. The API is reachable from any caller that knows the path.
- `trust_accounts` table rows remain. `trust_transactions` rows remain. `client_ledger_card` rows remain. `interest_run` / `interest_allocation` / `trust_investment` rows remain (the tables exist in every schema per ADR-191).
- Legal document templates (`legal-za` template pack) remain installed. Conveyancing clauses remain. FICA `field_definition` rows remain. `ChecklistInstance` rows remain.
- `pack_install` table still has rows for every legal pack.

The data does not appear in the UI (the nav gate, page gate, and `<ModuleGate>` all key off `verticalProfile` *or* `enabledModules` and hide the surface), but it continues to occupy storage and could re-surface if the profile flips back. The boot reconciler will *re-add* legal modules every time the JVM restarts if the profile string is `"legal-za"` again — switching *back* re-merges cleanly; switching *away* leaves a zombie install.

**Why this matters specifically for legal-za:** trust ledger data is regulatory. A profile switch that orphans trust tables loses LPFF/LPC audit-trail accessibility through the canonical UI; the data is intact but no longer surfaceable through the controllers, and the export hard guard (§3 layer 3) still runs on every sync attempt forever. Switching `legal-za` *out* with non-zero trust balances is therefore not merely awkward — it is a fiduciary problem.

**Mitigation options** (none implemented; tracked as known fragility, no ADR yet):

- (a) A `VerticalProfileDrainSeeder` that runs **only on profile change** (not on boot) and computes the symmetric difference between old-profile and new-profile module/pack sets, soft-disabling or fully uninstalling orphans.
- (b) A *trapdoor* on profile switch when any `TrustAccount` exists: refuse the change and require a guided uninstall flow that exports the trust audit before disabling the module.
- (c) Treat profile switches as create-a-new-tenant operations — i.e. don't advertise the switch as supported.

Until one of (a)–(c) ships, the operational stance is **(c)**: the system is **one-way safe** (consulting → legal lights everything up via the boot reconciler) but **reversible-dirty**.

This is the dominant risk because the system is *advertised* as profile-switchable through `OrgSettingsService.updateVerticalProfile(...)` (`→ OrgSettingsService.java:818`), and the seeder's *add-only* invariant is correct in isolation but wrong as a switch primitive.

---

## 6. Modules affected

Every module is *verticalisable* in some form — terminology, packs, module-gating, capability-stacking, or vertical-specific entity siblings (legal-disbursement / trust-payment-source). Cross-link to the per-module pages for the precise touchpoint:

- [`vertical-profiles.md`](../30-modules/vertical-profiles.md) — the implementation: loader, registry, reconciler, gate component.
- [`packs.md`](../30-modules/packs.md) — the SPI and the unified-vs-direct-seeder split.
- [`integration-ports.md`](../30-modules/integration-ports.md) — domain-level feature gating + the trust-export hard guard (§3 layer 3).
- [`trust-accounting.md`](../30-modules/trust-accounting.md) — the worked example (§3).
- [`settings-navigation.md`](../30-modules/settings-navigation.md) — owns `OrgSettings.verticalProfile / enabledModules / terminologyNamespace`; exposes `PATCH /api/settings/vertical-profile` and `PUT /api/settings/modules`.
- [`identity-access.md`](../30-modules/identity-access.md) — capability stacking (§3 layer 6); `VIEW_TRUST` etc. are orthogonal to module slugs.
- [`customer-lifecycle.md`](../30-modules/customer-lifecycle.md) — FICA via field/compliance packs.
- [`invoicing.md`](../30-modules/invoicing.md) — `is_trust_invoice` flag; "Fee Note" / "Tariff Schedule" terminology; export hard guard.
- [`expenses.md`](../30-modules/expenses.md) — `LegalDisbursement` sibling entity (ADR-247) and `DisbursementPaymentSource` sibling port (ADR-279).
- [`projects.md`](../30-modules/projects.md), [`tasks.md`](../30-modules/tasks.md), [`time-entry.md`](../30-modules/time-entry.md), [`retainers.md`](../30-modules/retainers.md), [`proposals-acceptance.md`](../30-modules/proposals-acceptance.md) — terminology overrides only (no structural difference).
- [`capacity-planning.md`](../30-modules/capacity-planning.md) — `resource_planning` horizontal module; consulting-default.
- [`customer-portal.md`](../30-modules/customer-portal.md) — portal-side mirroring (§3 layers 7–9).
- [`reporting.md`](../30-modules/reporting.md) — vertical-specific report packs (e.g. `TrustReportPackSeeder` → 7 trust report definitions).

---

## 7. Active ADRs

The vertical / packs / module-gating ADR cluster, from `90-adr-index.md` (lines 351–366) — all **Active**:

| ADR | Title | Bearing |
|---|---|---|
| **ADR-076** | module-identity-contract | Module slugs are the contract; every gate uses the same slug; fail-closed when missing. |
| **ADR-091** | feature-flag-scope | Feature flags are per-tenant on `OrgSettings`, not per-user, not global. |
| **ADR-181** | vertical-profile-structure | What a profile JSON declares. |
| **ADR-184** | vertical-scoped-pack-filtering | `entry.verticalProfile == null \|\| equals(tenantProfile)`. |
| **ADR-185** | terminology-namespace | Backend stores key; frontend owns map. (Per `90-adr-index.md:353`.) |
| **ADR-189** | vertical-profile-storage | Why `verticalProfile` lives on `OrgSettings` — one row, one source of truth. |
| **ADR-190** | module-guard-granularity | Every entry point self-defends with `requireModule(...)`. |
| **ADR-191** | schema-uniformity-module-tables | Module tables exist in every schema regardless of profile. |
| **ADR-192** | enabled-modules-authority | `OrgSettings.enabledModules` is the runtime authority; JSON is seed only. |
| **ADR-208** | pack-verification-approach | QA via UI, not API/DB. |
| **ADR-212** | module-capability-mapping | Modules and capabilities are orthogonal — both gates stack. |
| **ADR-239** | horizontal-vs-vertical-module-gating | The `ModuleCategory` split. |
| **ADR-240** | unified-pack-catalog-install-pipeline | Canonical for packs. |
| **ADR-241** | add-only-pack-semantics | New version = new pack ID. No diff/merge/upgrade flow. |
| **ADR-243** | scope-two-pack-types-for-v1 | Only DOCUMENT_TEMPLATE + AUTOMATION_TEMPLATE migrated. |
| **ADR-244** | pack-only-vertical-profiles | Profiles do not own DDL; consulting-za is pack-only. |
| **ADR-245** | localized-profile-derivatives | Profiles may localise (`legal-za` vs hypothetical `legal-uk`). |
| **ADR-246** | profile-gated-dashboard-widgets | Widgets opt in by profile (e.g. `TeamUtilizationWidget`). |
| **ADR-247** | legal-disbursement-sibling-entity | `LegalDisbursement` is sibling of `Expense`, not subtype. |
| **ADR-276** | trust-accounting-hard-guard-export | The fail-closed export guard (§3 layer 3). |
| **ADR-279** | sibling-payment-source-port | `DisbursementPaymentSource` sibling port pattern. |

Foundational/related ADRs cross-linked from elsewhere in the index: **ADR-053** (field-pack-seeding-strategy), **ADR-063** (compliance-packs-bundled-seed-data — superseded-in-spirit by ADR-240), **ADR-092** (auto-apply-strategy), **ADR-183** (qa-methodology-vertical-readiness — informational).

---

## 8. Known fragilities / open questions

1. **Profile-switch orphans** — §5 above. The single most important architectural risk in the codebase. Adds-only reconciler is correct in isolation, wrong as a switch primitive. No ADR yet.

2. **Reconciler vs admin-toggle precedence** — when a horizontal module (e.g. `information_requests`) is *also* declared in a profile's `enabledModules`, the reconciler always wins on boot. An admin's `PUT /api/settings/modules` "disable" of a profile-declared horizontal module is therefore *non-durable* — survives until the next deploy/restart. This is the design per ADR-192 (JSON is the floor, admin can add but not subtract from a profile-declared set), but it is poorly documented and discoverable only by reading the seeder. See [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.2 for the three resolution paths (none decided): (a) document the floor, (b) add a `disabledModules` overlay column, (c) forbid horizontal modules in profile JSON's `enabledModules` and use `defaultEnabledFor` in the registry instead.

3. **Pack uninstall not implemented** — the `PackInstaller.uninstall(...)` API exists (`→ packs/PackInstaller.java`) and is wired through `DELETE /api/packs/{packId}` for the two unified pack types (`DOCUMENT_TEMPLATE`, `AUTOMATION_TEMPLATE`), but the 11 legacy direct-seeder pack types have *no uninstall path*. Per ADR-243, this is deliberate transitional state. Combined with §1, this means a profile change cannot drain orphaned packs even in principle for most pack types.

4. **JSON profile format leniency** — `VerticalProfileRegistry` is **string-keyed and lenient** (per [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.4): missing `profileId` → file silently skipped (line 73–76); malformed JSON → silently skipped (line 121–125); unknown top-level keys silently ignored; wrong types in known fields → bad entry skipped past. No JSON Schema, no `@Valid` DTO, no boot-time fail-fast. A typo'd module slug (`"trust_acccounting"` with three c's) merges into `OrgSettings.enabledModules` and silently matches nothing. Inconsistent with the pack catalog, which boot-time fail-fasts on duplicate `(PackType, packId)`.

5. **Lenient-skip on malformed JSON (§10.5)** — sub-case worth highlighting: if a deployment ships with a corrupted `legal-za.json`, the profile *disappears from the registry*, every existing legal-za tenant boots successfully but the reconciler silently no-ops (`VerticalProfileReconciliationSeeder.java:81-87`), and there is no health-check / metric that surfaces "tenant N has profile X but profile X is no longer in the registry." The tenant continues on whatever `enabledModules` it had at last successful reconcile.

6. **Profile evolution backfill semantics** — adding a new module to `legal-za.json`'s `enabledModules` (e.g. `lssa_tariff` shipped in a release): existing legal-za tenants pick it up on next backend boot via `PackReconciliationRunner` → reconciler. **By design.** But three classes have undefined behaviour:
   - Pack-list changes — adding a pack id to `packs.template` *does* install on existing tenants; *removing* a pack id from the JSON does **not** uninstall (same as §1).
   - `taxDefaults` changes — the seeder's `applyTaxDefaults` only renames the *legacy* `"Standard"` tier; profile switching `VAT 15% → VAT 16%` mid-life does **not** update existing default tier rates.
   - `terminologyNamespace` changes — purely a frontend lookup; takes effect immediately; safe to evolve.

7. **Conflict resolution: two packs declaring same field/template slug** — not formally specified. The `UNIQUE` constraints surface a `DataIntegrityViolationException` at install time, but there is no pre-check, no diagnostic, and no priority rule. Per [`packs.md`](../30-modules/packs.md) §10.

8. **Pack-content authoring workflow** — today: hand-authored JSON files in `backend/src/main/resources/{document-template-packs,automation-template-packs,field-packs,...}/*.json` shipped with the JAR. No admin UI to author packs, no DB-backed catalog, no third-party authoring story. ADR-240 considered and rejected a database-backed mutable catalog (Option 1) for v1.
