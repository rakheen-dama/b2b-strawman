# Seeds and Packs Catalogue

**Status:** filled (Phase D part 2).
**Sibling pages:** [`30-modules/packs.md`](../30-modules/packs.md) (SPI + lifecycle), [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) (profile loader + reconciler), [`60-verticals/{base,legal-za,accounting-za,consulting-za}.md`](.) (per-vertical content lists), [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) (orphaned-data fragility).

---

## 1. Purpose

Single-source-of-truth catalogue of **every pack** that exists in the codebase: the SPI implementation or seeder that ships it, the JSON file that defines its content, and the vertical profile(s) that auto-install it at provisioning vs the ones that allow it as an admin-installable optional pack.

Module pages cite this page when listing pack-installs. Vertical pages cite this page when listing their packs. The authoritative *runtime* truth is the profile JSON files (`backend/src/main/resources/vertical-profiles/*.json`) plus the on-classpath pack JSON files; this page mirrors them and is updated in the same PR per [`99-conventions.md` § Update cadence](../99-conventions.md):79–82.

---

## 2. Pack-type taxonomy

Per [`packs.md` § 1](../30-modules/packs.md):11 there are **2 live `PackInstaller` SPI types** and **9 historical flavours via direct `AbstractPackSeeder` subclasses** that have not migrated to the unified pipeline (ADR-243 deferred them). The taxonomy below mirrors the source — it is **the SPI/seeder, not the file naming**, that determines whether a pack is on the catalog API or provision-only.

| PackType / category | SPI / Seeder | Implementation anchor | What it ships | Catalog API? |
|---|---|---|---|---|
| `DOCUMENT_TEMPLATE` | `TemplatePackInstaller` (wraps `TemplatePackSeeder`) | `→ packs/TemplatePackInstaller.java:27` + `→ template/TemplatePackSeeder.java:22` | `DocumentTemplate` rows + Tiptap content | **YES** (install/uninstall, gated) |
| `AUTOMATION_TEMPLATE` | `AutomationPackInstaller` (wraps `AutomationTemplateSeeder`) | `→ packs/AutomationPackInstaller.java:32` + `→ automation/template/AutomationTemplateSeeder.java:21` | `AutomationRule` + `AutomationAction` rows | **YES** (install/uninstall, gated) |
| FIELD | `FieldPackSeeder` (direct, not in SPI) | `→ fielddefinition/FieldPackSeeder.java:19` | `FieldDefinition` + `FieldGroup` rows | NO — provisioning only |
| COMPLIANCE / CHECKLIST | `CompliancePackSeeder` (direct) | `→ compliance/CompliancePackSeeder.java:41` | `ChecklistTemplate` + items per jurisdiction | NO — provisioning only |
| CLAUSE | `ClausePackSeeder` (direct) | `→ clause/ClausePackSeeder.java:18` | `DocumentClause` rows | NO — provisioning only |
| REQUEST_TEMPLATE | `RequestPackSeeder` (direct) | `→ informationrequest/RequestPackSeeder.java:13` | `RequestTemplate` + items | NO — provisioning only |
| RATE | `RatePackSeeder` (direct) | `→ seeder/RatePackSeeder.java:20` | `BillingRate` snapshot rows | NO — provisioning only |
| PROJECT_TEMPLATE | `ProjectTemplatePackSeeder` (direct) | `→ seeder/ProjectTemplatePackSeeder.java:21` | `ProjectTemplate` + step rows | NO — provisioning only |
| SCHEDULE | `SchedulePackSeeder` (direct) | `→ seeder/SchedulePackSeeder.java:27` | recurring `Schedule` rows | NO — provisioning only |
| DSAR / data-request templates | `ComplianceTemplatePackSeeder` (direct, calls `TemplatePackSeeder` infra) | `→ datarequest/ComplianceTemplatePackSeeder.java:31` | DSAR `DocumentTemplate` rows under namespace `compliance-za` | NO — provisioning only |

(Verified by `grep -rn "implements PackInstaller\|extends AbstractPackSeeder" backend/src/main/java` — 2 + 10 hits; the `ComplianceTemplatePackSeeder` reuses the `TemplatePackDefinition` shape so its catalogue row is folded into "compliance-za" template pack below.)

> Tariff seeds (`backend/src/main/resources/tariff-seed/*.json`) are **not packs** — they are loaded by `LssaTariffSeeder` for the `lssa_tariff` module; rows live in `tariff_table` not `pack_install`. Listed at the bottom of §3 for completeness because vertical pages reference them.

---

## 3. Pack catalogue (every pack on the classpath)

Each row: `Pack ID` (the `packId` field in the JSON; this is what `pack_install.pack_id` stores), `Type`, `Ships in` (which profile JSON's `packs.<category>` array references it; "universal" if `verticalProfile == null`), `Resource path`, brief content summary. Versions reflect the JSON `"version"` field at time of writing — they are advisory, not the merge bar.

### 3.1 FIELD packs (`backend/src/main/resources/field-packs/*.json`, seeded by `FieldPackSeeder`)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `common-project` | universal (auto-applied by `entityType: PROJECT`) | `field-packs/common-project.json` | Generic project field group (status, priority etc.). v2. |
| `common-task` | universal | `field-packs/common-task.json` | Generic task fields. v2. |
| `legal-za-customer` | legal-za | `field-packs/legal-za-customer.json` | FICA risk band, ID/passport, beneficial-owner field group on `CUSTOMER`. v2. |
| `legal-za-project` | legal-za | `field-packs/legal-za-project.json` | Matter type, court, opposing party, judge fields on `PROJECT`. v2. |
| `conveyancing-za-project` | legal-za | `field-packs/conveyancing-za-project.json` | Erf number, deed office, transfer-duty fields. v1. |
| `accounting-za-customer` | accounting-za | `field-packs/accounting-za-customer.json` | AML risk band, VAT/tax number, entity type. v3. |
| `accounting-za-project` | accounting-za | `field-packs/accounting-za-project.json` | Period, financial year-end, deliverable type. v2. |
| `accounting-za-customer-trust` | accounting-za | `field-packs/accounting-za-customer-trust.json` | Trust deed, Master's Office reference, trustee fields. v1. |
| `consulting-za-customer` | consulting-za | `field-packs/consulting-za-customer.json` | Industry, account-manager, billing model. v1. |
| `consulting-za-project` | consulting-za | `field-packs/consulting-za-project.json` | Campaign type, channel, KPI fields. v1. |

### 3.2 COMPLIANCE / CHECKLIST packs (`backend/src/main/resources/compliance-packs/<id>/pack.json`, seeded by `CompliancePackSeeder`)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `fica-kyc-za` | accounting-za, legal-za (both profiles' `packs.compliance`) | `compliance-packs/fica-kyc-za/pack.json` | FICA KYC checklist — identity, company reg, tax clearance, beneficial ownership, source of funds. v1.1.0. |
| `sa-fica-individual` | universal (admin-installable) | `compliance-packs/sa-fica-individual/pack.json` | Individual-client FICA checklist. v1.0.0. |
| `sa-fica-company` | universal (admin-installable) | `compliance-packs/sa-fica-company/pack.json` | Company-client FICA checklist (directors, registration). v1.0.0. |
| `legal-za-individual-onboarding` | legal-za (admin-installable; not in `legal-za.json` `packs.compliance` today) | `compliance-packs/legal-za-individual-onboarding/pack.json` | FIC Act s21A individual checklist. v1.0.0. |
| `legal-za-trust-onboarding` | legal-za (admin-installable; not auto-installed) | `compliance-packs/legal-za-trust-onboarding/pack.json` | FIC Act s21A trust-client checklist (Master's Office Letters of Authority, trust deed). v1.0.0. |
| `generic-onboarding` | consulting-za (`packs.compliance: ["generic-onboarding"]`); admin-installable elsewhere | `compliance-packs/generic-onboarding/pack.json` | Standard onboarding (engagement confirmation, billing arrangements). v1.0.0. |

> The `*-onboarding` siblings are present on the classpath but **not referenced** by any profile JSON's `packs.compliance` array. They are catalog-only — admins install them on demand via `POST /api/packs/{packId}/install`.

### 3.3 CLAUSE packs (`backend/src/main/resources/clause-packs/<id>/pack.json`, seeded by `ClausePackSeeder`)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `standard-clauses` | universal (auto for every profile) | `clause-packs/standard-clauses/pack.json` | Generic legal-light clauses (governing law, severability, notice). v1. |
| `legal-za-clauses` | legal-za (admin-installable; not in `legal-za.json` `packs.clause` — that profile uses conveyancing-za-clauses) | `clause-packs/legal-za-clauses/pack.json` | SA litigation/general legal clauses. v1. |
| `conveyancing-za-clauses` | legal-za (auto: `packs.clause: ["conveyancing-za-clauses"]`) | `clause-packs/conveyancing-za-clauses/pack.json` | SA conveyancing clauses (deed of sale, transfer suspensive conditions). v1. |
| `accounting-za-clauses` | accounting-za | `clause-packs/accounting-za-clauses/pack.json` | SAICA-aligned engagement clauses (scope, limits, indemnity). v1. |
| `consulting-za-clauses` | consulting-za | `clause-packs/consulting-za-clauses/pack.json` | Agency/SOW clauses (IP assignment, change-control, kill-fee). v1. |

### 3.4 DOCUMENT_TEMPLATE packs (`backend/src/main/resources/template-packs/<id>/pack.json`, installed by `TemplatePackInstaller` SPI)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `common` | universal (auto for every profile via `getUniversalPackIds(DOCUMENT_TEMPLATE)`) | `template-packs/common/pack.json` | Generic engagement letter, project summary, invoice cover letter. v1. |
| `compliance-za` | universal (`verticalProfile: null`; admin-installable, not in any `packs.template` today) | `template-packs/compliance-za/pack.json` | PAIA Section 51 manual + DSAR templates (seeded by `ComplianceTemplatePackSeeder` path). v1. |
| `legal-za` | legal-za | `template-packs/legal-za/pack.json` | 16 templates: engagement letters (general/litigation/conveyancing), POA, deed of transfer, founding affidavit, notice of motion, S35 cover letter, bond cancellation, OTP, trust receipt, client trust statement, statement of account, letter of demand, matter closure. v5. |
| `accounting-za` | accounting-za | `template-packs/accounting-za/pack.json` | 7 templates: engagement letters (advisory/bookkeeping/tax), monthly report cover, FICA confirmation, statement of account, ZA invoice. v1. |
| `consulting-za` | consulting-za | `template-packs/consulting-za/pack.json` | 4 templates: creative brief, statement of work, engagement letter, monthly retainer report. v1. |

### 3.5 REQUEST_TEMPLATE packs (`backend/src/main/resources/request-packs/*.json`, seeded by `RequestPackSeeder`)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `annual-audit` | universal (admin-installable) | `request-packs/annual-audit.json` | Documents required for annual audit engagements. v1. |
| `monthly-bookkeeping` | universal (admin-installable) | `request-packs/monthly-bookkeeping.json` | Monthly bookkeeping document checklist. v1. |
| `tax-return` | universal (admin-installable) | `request-packs/tax-return.json` | Tax-return supporting docs. v1. |
| `company-registration` | universal (admin-installable) | `request-packs/company-registration.json` | New company registration documents. v1. |
| `year-end-info-request-za` | accounting-za (`packs.request`) | `request-packs/year-end-info-request-za.json` | SA year-end information request. v1. |
| `fica-onboarding-pack` | legal-za (`packs.request`) | `request-packs/fica-onboarding-pack.json` | FICA onboarding document pack. v1. |
| `conveyancing-intake-za` | legal-za (`packs.request`) | `request-packs/conveyancing-intake-za.json` | Conveyancing intake document request. v1. |
| `legal-za-liquidation-distribution` | legal-za (`packs.request`) | `request-packs/legal-za-liquidation-distribution.json` | Liquidation & distribution account pack. v1. |
| `consulting-za-creative-brief` | consulting-za (`packs.request`) | `request-packs/consulting-za-creative-brief.json` | Creative brief document request. v1. |

### 3.6 RATE packs (`backend/src/main/resources/rate-packs/*.json`, seeded by `RatePackSeeder`)

Note: `legal-za.json` does **not** declare a rate pack today — the LSSA tariff is loaded separately as a non-pack tariff seed (§3.10). Profile defaults in `legal-za.json` `rateCardDefaults` are themselves absent (the profile loader provides hard-coded defaults).

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `rate-pack-accounting-za` | accounting-za (admin-installable; not in profile `packs`) | `rate-packs/accounting-za.json` | Partner / Manager / Senior / Bookkeeper hourly rates ZAR. v1. |
| `rate-pack-consulting-za` | consulting-za (admin-installable) | `rate-packs/consulting-za.json` | Creative Director / Strategist / Designer rates ZAR. v1. |
| `rate-pack-consulting-generic` | consulting-generic (admin-installable) | `rate-packs/consulting-generic.json` | Principal / Senior / Junior consultant rates ZAR. v1. |

### 3.7 PROJECT_TEMPLATE packs (`backend/src/main/resources/project-template-packs/*.json`, seeded by `ProjectTemplatePackSeeder`)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `legal-za-project-templates` | legal-za (admin-installable) | `project-template-packs/legal-za.json` | RAF litigation, conveyancing, deceased estate, divorce blueprints. v3. |
| `accounting-za-project-templates` | accounting-za (admin-installable) | `project-template-packs/accounting-za.json` | Year-end pack (AFS), monthly bookkeeping, tax return blueprints. v1. |
| `consulting-za-project-templates` | consulting-za (admin-installable) | `project-template-packs/consulting-za.json` | Website design & build, brand identity, retainer blueprints. v1. |

### 3.8 SCHEDULE packs (`backend/src/main/resources/schedule-packs/*.json`, seeded by `SchedulePackSeeder`)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `schedule-pack-accounting-za` | accounting-za | `schedule-packs/accounting-za.json` | Annual tax return, provisional tax, VAT/EMP201 recurring schedules. v1. |

(No legal-za / consulting-za schedule packs ship today.)

### 3.9 AUTOMATION_TEMPLATE packs (`backend/src/main/resources/automation-templates/*.json`, installed by `AutomationPackInstaller` SPI)

| Pack ID | Ships in | Resource | Content |
|---|---|---|---|
| `automation-common` | universal (auto via `getUniversalPackIds(AUTOMATION_TEMPLATE)`) | `automation-templates/common.json` | Task-completion chain, project-status nudges. v2. |
| `ai-specialist-common` | universal (admin-installable) | `automation-templates/ai-specialist-common.json` | AI-assisted polish-invoice-on-send etc. v1. |
| `automation-legal-za` | legal-za (admin-installable; **not** in `legal-za.json` `packs.automation` today — gap, see §8) | `automation-templates/legal-za.json` | Matter onboarding reminder, court-deadline nudges. v2. |
| `ai-specialist-legal-za` | legal-za (admin-installable) | `automation-templates/ai-specialist-legal-za.json` | Weekly matter activity summary, AI clause-check. v1. |
| `automation-accounting-za` | accounting-za (`packs.automation`) | `automation-templates/accounting-za.json` | FICA reminder, year-end nudges. v3. |
| `automation-consulting-za` | consulting-za (`packs.automation`) | `automation-templates/consulting-za.json` | Budget-80% alert, retainer-burn notifier. v1. |
| `ai-specialist-consulting-za` | consulting-za (admin-installable) | `automation-templates/ai-specialist-consulting-za.json` | AI weekly matter summary. v1. |

### 3.10 Non-pack seed data (listed for completeness)

| Seed | Loader | Resource | Used by |
|---|---|---|---|
| LSSA 2024/2025 High Court Party-and-Party tariff | `LssaTariffSeeder` (not a pack) | `tariff-seed/lssa-2024-2025-hc-pp.json` | `lssa_tariff` module on legal-za |

---

## 4. Provisioning vs on-demand

Two install pathways exist, both idempotent:

**(a) Auto-install at provisioning** — packs declared in a profile JSON's `packs.<category>` arrays are installed when `TenantProvisioningService.provisionTenant(...)` runs (per `→ TenantProvisioningService.java:170-189` and the five-step sequence in [`vertical-profiles.md` § 6.5](../30-modules/vertical-profiles.md):149–157). This pathway also re-runs **on every backend boot** via `PackReconciliationRunner` (`@Order(100) ApplicationRunner`, `→ provisioning/PackReconciliationRunner.java:40`) iterating `org_schema_mapping`. Every "auto" entry in §3 is an idempotent reconcile target — adding a pack to a profile's `packs` array on the next deploy backfills it to all existing tenants on the next JVM start.

**(b) Admin-installable on demand** — every pack with a `packId` on the classpath that is registered with a `PackInstaller` SPI (live: `DOCUMENT_TEMPLATE`, `AUTOMATION_TEMPLATE` only — see [`packs.md` §1](../30-modules/packs.md):11) is reachable via `POST /api/packs/{packId}/install` from `Settings → Packs`. Profile-affinity is enforced (`PackInstallService.enforceProfileAffinity`, `→ packs/PackInstallService.java:235`): a `legal-za` template pack cannot be installed into a `consulting-generic` tenant. Universal packs (`verticalProfile == null`) are installable everywhere.

The 9 non-SPI flavours (FIELD, COMPLIANCE, CLAUSE, REQUEST_TEMPLATE, RATE, PROJECT_TEMPLATE, SCHEDULE, DSAR templates) are **provision-time only** — they are not in the catalog API, have no install/uninstall endpoint, and the only way to "install" one outside provisioning is to add it to a profile JSON's `packs` array (so the next boot reconciles) or wait for ADR-243 follow-up migration to the SPI.

The "Ships in" column above uses these conventions:
- `legal-za` / `accounting-za` / `consulting-za` / universal — pack is referenced in that profile's `packs.<category>` array (auto-installed on boot for tenants on that profile, plus universal packs for *everyone*).
- `legal-za (admin-installable; not in profile)` — pack JSON has `verticalProfile: legal-za` but no profile references it; only reachable via the catalog UI for legal-za tenants (and only if its type is one of the 2 SPI types).

---

## 5. Add-only nature and the orphaned-data fragility

Per [`ADR-241`](../../adr/ADR-241-add-only-pack-semantics.md) and [`vertical-profiles.md` § 10.1](../30-modules/vertical-profiles.md):216–229: the reconciler only **adds**. This page is canonical for what gets ADDED to a tenant; **nothing in the system catalogues "what to remove on profile switch"** because removal is unimplemented (no `VerticalProfileDrainSeeder`, no per-pack uninstall for the 9 non-SPI flavours).

Concrete consequence: if a tenant moves from `legal-za` to `consulting-generic`, every legal-za row in this catalogue stays installed — `legal-za-customer` field group rows stay on `field_definition`, `conveyancing-za-clauses` stay in `document_clause`, all 16 `legal-za` document templates stay in `document_template`, FICA checklist templates stay, project templates stay. Only the `enabledModules` differ (and even those are not pruned — they are just not appended to). The profile change is **one-way safe** (towards more content) and **reversible-dirty** (away from content). Treat profile downgrades as "create a new tenant" operations until a drain seeder ships.

The two SPI-managed types (`DOCUMENT_TEMPLATE`, `AUTOMATION_TEMPLATE`) **do** have an uninstall path through the catalog API — `DELETE /api/packs/{packId}` with the `UninstallCheck` gate ([`packs.md` §6 "Uninstall gate"](../30-modules/packs.md):117–119). The other 9 flavours have no uninstall path at all.

---

## 6. Pack lifecycle

1. **Author content** — write/edit a JSON file under `backend/src/main/resources/{field-packs|template-packs|clause-packs|compliance-packs|request-packs|rate-packs|project-template-packs|schedule-packs|automation-templates}/`. The file's `packId` is the catalogue key.
2. **Update profile JSON** (only if auto-install on a profile) — add the pack id to `vertical-profiles/<profile>.json` `packs.<category>`.
3. **Update this catalogue** — add a row in the right §3 sub-table in the *same PR* per [`99-conventions.md`](../99-conventions.md):79–82.
4. **Commit + deploy** — the JAR ships the new resources.
5. **Boot reconcile** — `PackReconciliationRunner` iterates every tenant; `installPacksViaPipeline` (for SPI types) and the direct seeders (for the 9 flavours) UPSERT pack content. New tenants pick up the pack at provisioning; existing tenants pick it up on the next backend boot.
6. **Tenants drift recovery** — admins can re-trigger reconcile for SPI-managed types via `POST /api/packs/{packId}/install` (idempotent — duplicates are no-ops per `PackInstaller.install` Javadoc, `→ packs/PackInstaller.java:13`). The 9 non-SPI flavours have no drift-recovery UI; restart the backend.
7. **Versioning** — bump `version` in the JSON if the content changes meaningfully. Per ADR-241 add-only semantics, a *new version* of the same pack that meaningfully diverges should ship as a *new packId* (e.g. `legal-za-v2`) rather than mutating `legal-za` v5 → v6 — but the codebase today bumps `version` in place (§8.2).

---

## 7. Source material

- [`30-modules/packs.md`](../30-modules/packs.md) — pack SPI contract, two-type scope, `PackCatalogService` boot validation.
- [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) §6.5 — five-step provisioning sequence; §10.1 — orphan fragility.
- [`_discovery/A1-backend-map.md`](../_discovery/A1-backend-map.md) — `verticals/`, `packs/`, `seeder/` package map.
- [`_discovery/A6-cross-cutting.md`](../_discovery/A6-cross-cutting.md) §4 — provisioning sequence + reconcile pipeline anchors.
- [`glossary.md`](../glossary.md) — `Pack`, `PackInstaller`, `PackInstall`, `PackType`, `FieldPack`, `CompliancePack`, `TemplatePack`, `ClausePack`, `ProjectTemplatePack`, `RequestTemplatePack`, `RatePack`.
- Profile JSONs (the runtime truth): `backend/src/main/resources/vertical-profiles/{legal-za,accounting-za,consulting-za,consulting-generic}.json`.
- `grep -rn "implements PackInstaller\|extends AbstractPackSeeder" backend/src/main/java` (12 hits — confirmed §2 row count).
- `find backend/src/main/resources -path '*pack*' -o -path '*seed*'` (78 files — every row in §3 is anchored to one of them).

---

## 8. Open questions

1. **Pack content authoring workflow.** Today: hand-authored JSON files on the classpath, shipped with the JAR. There is no admin UI to author packs, no DB-backed catalog, no third-party authoring story. ADR-240 considered and rejected a database-backed mutable catalog (Option 1) for v1. New verticals = new JSON files committed by Kazi engineers.
2. **Pack versioning when content evolves.** ADR-241 says "new version = new pack ID" but the live behaviour is "bump `version` field in place"; the in-place path lacks a forward-compat story for tenants on the older version. Specifically: `legal-za` template pack is at v5 — what happened to tenants installed at v1–v4? Today: each boot reconcile re-applies the latest definitions through the seeder's UPSERT, silently overwriting any tenant data with `source=PACK`; user-cloned `source=CUSTOM` rows are untouched. The "pack ID per version" discipline ADR-241 promises is not enforced.
3. **Conflict resolution.** Two packs declaring the same `field_definition.slug` or `document_template.slug` is not formally specified. The `UNIQUE` constraints surface a `DataIntegrityViolationException` at install time, but there is no pre-check, no diagnostic, no priority rule — order of seeding wins. (Confirmed example: `engagement-letter` slug appears in `template-packs/common/`, `template-packs/legal-za/engagement-letter-general.json`, `template-packs/accounting-za/engagement-letter-advisory.json`, `template-packs/consulting-za/engagement-letter.json` — they are namespaced by per-pack slug prefixes today, but nothing enforces that.)
4. **Catalogue completeness drift.** This page must be updated *in the same PR* as a new pack's JSON file, per [`99-conventions.md`](../99-conventions.md):79–82. There is no automated check; drift is discovered the next time someone reads this page. A future ArchUnit test could enumerate `backend/src/main/resources/{*-packs,automation-templates}/**/*.json`, parse each `packId`, and assert this page references it.
5. **Profile-pack mismatch — `automation-legal-za` not auto-installed on legal-za.** `automation-templates/legal-za.json` has `verticalProfile: legal-za` and contents (matter-onboarding reminder, court-deadline nudges) clearly intended for legal-za tenants. But `vertical-profiles/legal-za.json` has **no `packs.automation` key at all** — so legal-za tenants don't get this pack at provisioning. Verified by inspection. Either (a) deliberate ("admin opts in to automations"), or (b) regression. Same applies to several `legal-za-*` compliance and rate packs that ship with `verticalProfile: legal-za` but are not referenced by the profile. Tracked here as a profile-completeness audit task.
6. **DSAR / `compliance-za` template pack is not auto-installed anywhere.** `template-packs/compliance-za/pack.json` (`verticalProfile: null`, contains PAIA Section 51 manual) ships universal but no profile JSON's `packs.template` references it; `getUniversalPackIds(DOCUMENT_TEMPLATE)` filter logic in `PackCatalogService` (`→ packs/PackCatalogService.java:62`) does pick up `verticalProfile == null` packs at provisioning — so all tenants do get `common` and `compliance-za`. Confirm by checking `pack_install` rows on a fresh tenant. Documented here so the next reader doesn't conclude it's dead code.
