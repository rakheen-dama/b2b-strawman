# Base SaaS

**Status:** filled (Phase D part 2).
**See also:** [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) (the loader / registry / reconciler), [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) (the four mechanisms + nine-layer trust defence), [`60-verticals/seeds-and-packs.md`](seeds-and-packs.md) (universal pack inventory).

> Purpose of this page: document the *floor* — what every Kazi tenant gets regardless of vertical choice. The "no-overlay" state. Useful for generic professional-services SaaS without legal / accounting / consulting specifics.

---

## 1. Profile ID and scope

> **The `"base"` slug does not exist in the codebase.** A search of `backend/src/main/resources/vertical-profiles/` (`→ backend/src/main/resources/vertical-profiles/{legal-za,accounting-za,consulting-za,consulting-generic}.json` — four files, none named `base.json`) and a `grep '"base"'` across the verticals package both return zero hits for a profile id. The closest equivalent is **`consulting-generic`** — explicitly identified in [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §4 as `"base / consulting-generic — the no-vertical fallback"`.

| Attribute | Value |
|---|---|
| Profile id (intended) | `base` — **not present in code** |
| Profile id (de-facto) | `consulting-generic` `→ backend/src/main/resources/vertical-profiles/consulting-generic.json:2` |
| Region | jurisdiction-agnostic. The de-facto file ships ZAR / `en-ZA` defaults `→ consulting-generic.json:6-7` — i.e. the floor today is *South-African-flavoured generic*, not strictly locale-neutral. |
| Vertical type | core — the universal foundation every other profile builds on. |
| Targeted firm | generic professional services / agencies / consulting outfits that do not need trust accounting, court calendars, regulatory deadlines, or LSSA tariffs. The two-line description in JSON: `"General consulting, agencies, and professional services firms. ZAR defaults for South African practices."` `→ consulting-generic.json:4`. |

Every other vertical profile is an *additive overlay* on this floor — `legal-za` adds nine modules, `accounting-za` adds regulatory deadlines + bulk billing, `consulting-za` adds `resource_planning`. Base adds nothing.

---

## 2. Packs installed by this profile

Two install paths run for every tenant during `TenantProvisioningService.provisionTenant(...)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:177-182`):

1. **Universal packs** — `installPacksViaPipeline` first calls `packCatalogService.getUniversalPackIds(packType)` and installs every pack whose metadata declares `verticalProfile == null` (`→ TenantProvisioningService.java:306-309`). These run on the base profile.
2. **Profile-specific packs** — `getPackIdsForProfile(verticalProfile, packType)` runs only when `verticalProfile != null` (`→ TenantProvisioningService.java:313-315`). For a base / null-profile tenant this branch is **skipped**.

Per-pack-type result for a base tenant (driven by the unified `PackInstaller` SPI per ADR-240, two pack types live per ADR-243 — see [`30-modules/packs.md`](../30-modules/packs.md) §1):

| Pack type | Universal packs installed | Vertical-specific packs installed |
|---|---|---|
| `DOCUMENT_TEMPLATE` | Universal templates (proposal, invoice, generic engagement letter) — exact id list lives in [`60-verticals/seeds-and-packs.md`](seeds-and-packs.md). | _None._ |
| `AUTOMATION_TEMPLATE` | Universal automations (e.g. invoice-overdue reminder). | _None._ |

Plus the 11 legacy direct-seeder pack types (field, compliance, clause, checklist, request, rate, project-template, schedule, etc.) which still run via `AbstractPackSeeder` subclasses called from `TenantProvisioningService` (per [`packs.md`](../30-modules/packs.md) §1, §6). Their universal-vs-vertical split is per-seeder; the base tenant gets the *generic* row of each (no FICA, no LSSA tariff, no SARS deadlines, no conveyancing clauses).

The `consulting-generic.json` file itself declares **no `packs` map** at all `→ consulting-generic.json:1-24` (24-line file, ends after `taxDefaults`) — meaning: even the per-profile pack overlay is empty. Base = universal-only.

---

## 3. Terminology overrides

**None.** The de-facto base profile (`consulting-generic.json`) declares **no** `terminologyNamespace` field `→ consulting-generic.json:1-24`. Per `OrgSettingsService.updateVerticalProfile(...)` semantics (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java:818`), an absent namespace leaves `OrgSettings.terminologyNamespace` null, and the frontend `TerminologyProvider` (`→ frontend/lib/terminology.tsx:24`) falls back to canonical glossary terms.

Canonical terms used (all from [`glossary.md`](../glossary.md)): **Customer**, **Project**, **Invoice**, **Task**, **Expense**, **Retainer**, **Proposal**, **Time Entry**, **Billing Rate**, **Audit Log**, **Budget**.

The portal (`portal/lib/terminology-map.ts`) likewise has no entry for the base / null namespace and renders canonical terms.

> Cross-reference: vertical-specific overrides for the three real verticals are inventoried in [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §2c — base is the absence of all of them.

---

## 4. Modules gated on / off

The `consulting-generic.json` profile declares **`enabledModules: []`** `→ consulting-generic.json:5` — an empty list. Combined with the adds-only reconciler invariant (per [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §6.4 / ADR-192), this means: a freshly-provisioned base tenant has **zero** vertical-module slugs in its `OrgSettings.enabledModules` JSONB column.

| Module slug | Category | Default state on base | Notes |
|---|---|---|---|
| `trust_accounting` | VERTICAL | **OFF** | Legal-only; never auto-enabled outside `legal-za`. |
| `court_calendar` | VERTICAL | **OFF** | Legal-only. |
| `conflict_check` | VERTICAL | **OFF** | Legal-only. |
| `lssa_tariff` | VERTICAL | **OFF** | Legal-only. |
| `regulatory_deadlines` | VERTICAL | **OFF** | SARS / CIPC; accounting-only. |
| `disbursements` | VERTICAL | **OFF** | Legal-only (see ADR-247 sibling-entity for `LegalDisbursement`). |
| `matter_closure` | VERTICAL | **OFF** | Legal-only. |
| `retainer_agreements` | VERTICAL | **OFF** in profile JSON. The universal `Retainer` flow lives in the *core* — this module slug gates the *legal-vertical-specific* mandate flavour. The generic retainer module page applies even on base — see [`30-modules/retainers.md`](../30-modules/retainers.md). |
| `resource_planning` | HORIZONTAL | OFF on base (admin-toggle; default-on for `consulting-za` per `defaultEnabledFor` in `VerticalModuleRegistry`). |
| `bulk_billing` | HORIZONTAL | OFF on base; admin-toggle. |
| `automation_builder` | HORIZONTAL | OFF on base; admin-toggle. |
| `information_requests` | HORIZONTAL | OFF on base; admin-toggle. |

Everything that is **core** (not module-gated) is ON regardless: customers, projects, tasks, time, expenses, invoices, proposals, retainers, documents, basic automations, settings, members, RBAC. The module table itself exists in every schema regardless of profile (per ADR-191 schema-uniformity-module-tables) — it is the slug-membership in `OrgSettings.enabledModules` that gates the API surface, not the DDL.

`IntegrationGuardService` foundational integrations (PAYMENT, EMAIL, KYC_VERIFICATION) are always-on (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java:25-26`); ACCOUNTING / AI / DOCUMENT_SIGNING follow the per-tenant `OrgSettings.isAccountingEnabled / isAiEnabled / isDocumentSigningEnabled` flags (lines 38–44) — those are independent of the vertical profile.

---

## 5. Vertical-specific entities or extensions

_None._ Base uses only the core entities catalogued in `10-bounded-contexts.md`. None of the legal-vertical sibling tables (`TrustAccount`, `ClientLedgerCard`, `TariffSchedule`, `AdverseParty`, `ConflictCheck`, `CourtDate`, `Prescription`, `LegalDisbursement`) carry rows on a base tenant — the tables exist (per ADR-191) but are unreferenced via core controllers because every vertical-only controller self-defends with `requireModule(...)` (per ADR-190).

---

## 6. Source material

- Discovery: `_discovery/A6-cross-cutting.md` §4 *Multi-vertical mechanism* — the four-knob model and the universal-vs-overlay split.
- Phase docs:
  - `→ architecture/phase49-vertical-architecture.md` — original vertical-profile architecture (introduces `verticalProfile` column).
  - `→ architecture/phase62-feature-module-gating.md` — the module-gate split (vertical vs horizontal, the merge-not-replace toggle API rule).
- ADRs (all **Active**, from `90-adr-index.md`):
  - **ADR-091** `→ adr/ADR-091-feature-flag-scope.md` — flags are per-tenant on `OrgSettings`, not per-user, not global. Base profile honours this scope.
  - **ADR-092** `→ adr/ADR-092-auto-apply-strategy.md` — field groups auto-apply by entity type. Base profile triggers no auto-apply rules (no field packs declared).
  - **ADR-181** vertical-profile-structure — what a profile JSON declares. Base / `consulting-generic` exercises the *minimal* shape.
  - **ADR-189** vertical-profile-storage — `verticalProfile` lives on `OrgSettings`. Base = the value `"consulting-generic"` (or null pre-Phase 49).
  - **ADR-190** module-guard-granularity — every vertical-only entry point self-defends with `requireModule(...)`. Base passes through the core surface; every vertical surface fail-closes.
  - **ADR-191** schema-uniformity-module-tables — module tables exist in every schema regardless of profile. Base tenants therefore have empty `trust_accounts`, `tariff_schedules`, etc., even though the API is gated off.
  - **ADR-192** enabled-modules-authority — `OrgSettings.enabledModules` is the runtime authority; the JSON `[]` is the seed. The reconciler will never *remove* a module if an admin manually adds one to the list.
  - **ADR-244** pack-only-vertical-profiles — profiles do not own DDL; they only declare packs. Base profile declares zero packs (the `packs` map is absent from `consulting-generic.json`).
  - **ADR-245** localized-profile-derivatives — profiles may localise. Implication for base: a future `base-uk` / `base-eu` is a new JSON file, not a code change.

---

## 7. What "base" implies for new modules

Convention guidance (anchored to [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §6.2 + ADR-239 horizontal-vs-vertical-module-gating):

1. **Universal core code goes in the core**, not behind a module gate. Customers, projects, tasks, time, expenses — no `requireModule(...)` calls. If a new feature applies to *every* tenant, do not add a slug at all.
2. **A new HORIZONTAL module** (admin-toggleable, profile-independent — `automation_builder`, `bulk_billing`, `information_requests`, `resource_planning` are the existing shape) is **OFF by default on the base profile**. Admins flip it on via `PUT /api/settings/modules`. The `defaultEnabledFor` array in `VerticalModuleRegistry.java` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java:24`) declares which *non-base* profiles auto-enable it (e.g. `information_requests` lists `["legal-za", "accounting-za", "consulting-za"]`).
3. **A new VERTICAL module** (auto-assigned by profile, hidden from Settings → Features) is **OFF on base** and ON only for the profiles whose JSON `enabledModules` lists it. Adding a vertical module = add the slug to the registry + add it to one or more `vertical-profiles/*.json` `enabledModules` array. The base profile JSON should remain empty.
4. **Backfill semantics on existing tenants**: when a new module is added to a non-base profile JSON, every existing tenant on that profile picks it up on next backend boot via `PackReconciliationRunner` → `VerticalProfileReconciliationSeeder.reconcile(...)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunner.java:40`). Base tenants are unaffected.

> Anti-pattern: adding a slug to `consulting-generic.json`'s `enabledModules` to "default-on for everyone." The empty list is load-bearing — it is what makes base the floor.

---

## 8. Open questions

### 8.1 Is `base` actually `consulting-generic`, or are they meant to be separate profiles?

The discovery doc and the multi-vertical page consistently treat them as one (`base / consulting-generic`). The codebase has only `consulting-generic.json`. This page captures the *intent* of `base` and points at `consulting-generic.json` as the seed. **Open decision:** rename `consulting-generic` → `base` (semantic clarity, reflects the no-overlay reality), or keep `consulting-generic` (avoids a seed/migration churn). No ADR yet; tracked here.

### 8.2 Is base used in production?

Most demo orgs and the documented onboarding paths choose a real vertical (`legal-za` for the legal demo, `accounting-za` for the accounting demo, `consulting-za` for consulting). `consulting-generic` shows up in `DemoDataSeeder.java` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java:35` as a documented option, and `PortalSessionContextService` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalSessionContextService.java:105` treats the slug `"consulting-generic"` and the empty/blank string equivalently for portal context fallback. **Whether any production tenant runs on it is unconfirmed** — may be test-fixture-only.

### 8.3 Profile evolution: do existing tenants on real verticals backfill modules added to base?

The reconciler reads the *tenant's* profile (`OrgSettings.verticalProfile`), then merges *that profile's* declared modules. Modules added to `consulting-generic.json` therefore propagate **only** to tenants whose `verticalProfile == "consulting-generic"`. They do **not** propagate to `legal-za` / `accounting-za` / `consulting-za` tenants — those tenants reconcile against their own profile JSON.

If the convention is "base modules are a *floor* every profile inherits", that is **not what the loader does today**. There is no profile-inheritance / profile-extends mechanism. Each profile JSON is self-contained; if a slug needs to be on every tenant, it must be added to every profile JSON (or made part of the universal core, no module slug). **Open decision:** introduce a `parentProfile: "base"` field in the JSON shape and have the loader merge upwards, or keep self-contained (current state).

### 8.4 Cosmetic: `consulting-generic` ships ZAR + `en-ZA` defaults

`consulting-generic.json:6-7` (`"locale": "en-ZA"`, `"currency": "ZAR"`) means the de-facto base is South-African-flavoured, not jurisdiction-neutral. If `base` is intended as a true locale-agnostic floor, the locale/currency defaults need a configurable derivative (per ADR-245 localized-profile-derivatives) — `base-za`, `base-uk`, `base-eu`. Not done.
