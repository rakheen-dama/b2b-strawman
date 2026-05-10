# Packs

**Bounded context:** see [`10-bounded-contexts.md` § packs](../10-bounded-contexts.md). **Sibling mechanism:** [`vertical-profiles.md`](vertical-profiles.md). **Glossary:** Pack, PackInstaller, PackInstall, PackType, FieldPack, CompliancePack, TemplatePack, ClausePack, ProjectTemplatePack, RequestTemplatePack, RatePack — all in [`../glossary.md`](../glossary.md).

## 1. Purpose

Extensible content seeding via SPI + per-tenant install tracking. Packs ship vertical and universal *content* (document templates, automation rules, custom fields, compliance checklists, clauses, project blueprints, request templates, rate cards) as installable units rather than baked into one-shot seeders. They are the foundation of multi-vertical content delivery — adding a new vertical means adding profile JSON + classpath pack files, no code change (per [`vertical-profiles.md`](vertical-profiles.md) and [`ADR-244`](../../adr/ADR-244-pack-only-vertical-profiles.md)).

The system is **add-only** ([`ADR-241`](../../adr/ADR-241-add-only-pack-semantics.md)) — a new version of a pack ships as a new catalog entry with a distinct pack ID; there is no diff/merge/upgrade flow. Re-running an install is a no-op (`PackInstaller.install` Javadoc, `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstaller.java:13`). Catalog is built at runtime from classpath-scanned JSON; there is no global `pack_catalog` table ([`ADR-240`](../../adr/ADR-240-unified-pack-catalog-install-pipeline.md) Option 1 rejected).

**Scope reality (Phase 65 / [`ADR-243`](../../adr/ADR-243-scope-two-pack-types-for-v1.md)):** The unified `PackInstaller` pipeline ships with **two** pack types live — `DOCUMENT_TEMPLATE` and `AUTOMATION_TEMPLATE` (`→ packs/PackType.java:7`). The other 11 historical pack flavours (field, compliance, clause, checklist, request, rate, project-template, schedule, etc.) still ship via their own direct `AbstractPackSeeder` subclasses called from `TenantProvisioningService`; they are *not* in the catalog API and have *no* uninstall path. This split is deliberate transitional state — the SPI was designed to extend, but only two types have been migrated.

## 2. Entities owned

`PackInstall` is the single anchored entity (tenant-scoped, table `pack_install`).

| Entity | Path | Notable fields |
|---|---|---|
| `PackInstall` | `→ backend/.../packs/PackInstall.java:17` | `packId`, `packType`, `packVersion`, `packName`, `installedAt`, `installedByMemberId` (nullable for system installs), `itemCount` |

DTO/value records (not entities):
- `PackCatalogEntry` `→ packs/PackCatalogEntry.java` — runtime catalog row (packId, name, description, version, type, verticalProfile, itemCount, installed, installedAt).
- `PackInstallResponse` `→ packs/PackInstallResponse.java`.
- `UninstallCheck` `→ packs/UninstallCheck.java` — `canUninstall` flag + blocking reason.

SPI interface:
- `PackInstaller` `→ packs/PackInstaller.java:20` — `type()`, `availablePacks()`, `install(packId, tenantId, memberId)`, `checkUninstallable(...)`, `uninstall(...)`. Idempotent install + all-or-nothing uninstall (Javadoc lines 13–17).

Database constraints (`pack_install` per Phase 65 doc):
- `UNIQUE (pack_id)` — one install per pack per tenant schema.
- `pack_type` is `VARCHAR(64)` with `@Enumerated(EnumType.STRING)` — additive enum.
- Content tables (`document_template`, `automation_rule`) gained `source_pack_install_id UUID FK` and `content_hash VARCHAR(64)` columns to enable uninstall reference checks and edit detection (per phase65 §65.2).

## 3. REST surface

All endpoints under `/api/packs/*`, gated by `@RequiresCapability("TEAM_OVERSIGHT")` (`→ packs/PackCatalogController.java:18`):

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/packs/catalog?all={bool}` | List available packs, optionally filtered by tenant's vertical profile (default `false` = profile + universal only) |
| `GET` | `/api/packs/installed` | List packs currently installed in this tenant |
| `GET` | `/api/packs/{packId}/uninstall-check` | Returns `UninstallCheck { canUninstall, blockingReason }` |
| `POST` | `/api/packs/{packId}/install` | Installs the pack into the current tenant schema |
| `DELETE` | `/api/packs/{packId}` | Uninstalls (calls gate first; 409 `ResourceConflictException` if blocked) |

The catalog filter logic lives in `PackCatalogService.listCatalog` (`→ packs/PackCatalogService.java:62`) and applies `entry.verticalProfile() == null || equals(tenantProfile)` ([`ADR-184`](../../adr/ADR-184-vertical-scoped-pack-filtering.md)).

Profile-affinity is enforced on install for non-universal packs (`PackInstallService.enforceProfileAffinity`, `→ packs/PackInstallService.java:235`) — a `legal-za` pack cannot be installed into a `consulting-generic` tenant; throws `InvalidStateException`.

## 4. Frontend pages / components

Pack catalog UI lives in the Settings sidebar:

- `frontend/app/(app)/org/[slug]/settings/packs/page.tsx` — server component entry.
- `frontend/app/(app)/org/[slug]/settings/packs/packs-page-client.tsx` — Available + Installed tabs, install/uninstall actions.
- `frontend/app/(app)/org/[slug]/settings/packs/actions.ts` — server actions for install/uninstall.
- `frontend/lib/api/packs.ts` — typed REST client.
- E2E coverage: `frontend/e2e/tests/settings/packs.spec.ts`; unit tests: `frontend/__tests__/settings/packs-page.test.tsx`.

Page is gated to members holding the `TEAM_OVERSIGHT` capability (controller annotation; rendered nav item under settings).

## 5. Domain events

**Packs do NOT emit on the sealed `DomainEvent` bus** (the bus is described in [`10-bounded-contexts.md` § domain-events`](../10-bounded-contexts.md)). Instead, `PackInstallService` writes audit events directly via `AuditService` and notifications via `NotificationService` (per `→ packs/PackInstallService.java:288-352`):

| Event type | When | Where |
|---|---|---|
| `pack.installed` | After successful install (HTTP path or internal-provision path) | `AuditEventBuilder` → `audit_events` table |
| `pack.uninstalled` | After successful uninstall | same |
| `pack.uninstall_blocked` | When `checkUninstallable` returns `canUninstall=false` and the request was an uninstall | same; details include `blockingReason` |
| `PACK_INSTALLED` notification | After successful install on HTTP path (memberId present) | `NotificationService.createNotification` |
| `PACK_UNINSTALLED` notification | After successful uninstall | same |

Internal/system installs (provisioning path) emit audit events with `actorType=SYSTEM`/`source=INTERNAL` and skip notifications.

## 6. Cross-cutting touchpoints

### SPI registration & catalog assembly

`PackCatalogService` constructor (`→ packs/PackCatalogService.java:28-52`) consumes `List<PackInstaller>` from the Spring context and builds a `Map<PackType, PackInstaller>`. **It fails fast at boot** on duplicate registrations (line 36 in service / per ADR-240): an `IllegalStateException` is thrown if two installers register the same `PackType`. Anchored explicitly in [`_discovery/A6-cross-cutting.md`](../_discovery/A6-cross-cutting.md) §4.

Live `PackInstaller` implementations (the unified path):
- `TemplatePackInstaller` `→ packs/TemplatePackInstaller.java` (wraps `TemplatePackSeeder.applyPackContent`, manages `PackInstall` rows + content hashes for `document_template`).
- `AutomationPackInstaller` `→ packs/AutomationPackInstaller.java` (wraps `AutomationTemplateSeeder.applyPackContent` for `automation_rule` + `automation_action`).

Direct seeders **outside** the unified SPI (still called from `TenantProvisioningService` at provisioning, no catalog/uninstall):
- `fielddefinition/FieldPackSeeder.java` — Field & FieldGroup packs.
- `compliance/CompliancePackSeeder.java` — checklist templates per jurisdiction.
- `clause/ClausePackSeeder.java` — DocumentClause packs.
- `informationrequest/RequestPackSeeder.java` — RequestTemplate packs.
- `seeder/RatePackSeeder.java` — rate card packs.
- `seeder/ProjectTemplatePackSeeder.java` — project blueprint packs.
- `seeder/SchedulePackSeeder.java` — recurring schedule packs.
- `template/TemplatePackSeeder.java` — *also* invoked directly (the unified `TemplatePackInstaller` wraps this same seeder; both call sites coexist during the transition).
- `automation/template/AutomationTemplateSeeder.java` — wrapped by `AutomationPackInstaller`.
- `datarequest/ComplianceTemplatePackSeeder.java` — DSAR templates.

(Confirmed via `grep -r "implements PackInstaller\|extends AbstractPackSeeder"`.)

### Idempotency

- `PackInstaller.install` is contractually idempotent (Javadoc); duplicate install returns the existing `PackInstall`. The `UNIQUE(pack_id)` constraint enforces this at the row level. `PackInstallService.install` performs an explicit `findByPackId` short-circuit (`→ PackInstallService.java:76`).
- Each underlying seeder uses item-level UPSERT semantics (`AbstractPackSeeder` infrastructure) so re-running is safe.

### Internal provisioning path

`PackInstallService.internalInstall(packId, tenantId)` (`→ PackInstallService.java:123`) is the system-actor entry. It binds `RequestScopes.runForTenant(tenantId, null, ...)` so JPA resolves to the right tenant schema from background/provisioning threads, runs inside an explicit `TransactionTemplate.executeWithoutResult` (avoiding self-invocation issues), skips profile affinity, and skips notifications. This is the seam used by `TenantProvisioningService.installPacksViaPipeline(...)` per [`_discovery/A6-cross-cutting.md`](../_discovery/A6-cross-cutting.md) §4 (lines 256, 262).

### Vertical onboarding wiring

At `provisionTenant(orgId, name, "legal-za", "ZA")` (`→ provisioning/TenantProvisioningService.java:140`, lines 170–189), the sequence is: resolve currency → set OrgSettings.verticalProfile + enabledModules + terminologyNamespace → install **universal** template packs (`packCatalogService.getUniversalPackIds(DOCUMENT_TEMPLATE)`) → install **profile-specific** packs (`packCatalogService.getPackIdsForProfile("legal-za", DOCUMENT_TEMPLATE)`) → run `VerticalProfileReconciliationSeeder`. The split between universal and profile-scoped is the `PackCatalogEntry.verticalProfile == null` discriminator.

### Pack-seeded vs user-created tracking

Content tables that come from packs carry a `source` discriminator and a `packId` reference (anchored fields per `→ _discovery/A1-backend-map.md` §entity tables): `DocumentTemplate.source` (`SYSTEM|PACK|CUSTOM` enum, `template/TemplateSource.java`), `ChecklistTemplate.source`, `RequestTemplate.source`, `FieldDefinition.packId`, `FieldGroup.packId`. Two new columns from Phase 65 — `source_pack_install_id` + `content_hash` — apply only to the migrated tables (`document_template`, `automation_rule`) and underpin the uninstall gate.

### Uninstall gate

`PackInstaller.checkUninstallable` returns `UninstallCheck.canUninstall=false` if any of: content was edited (content hash mismatch), content is referenced (e.g. `generated_documents` row pointing at a pack template, `automation_executions` referring to a pack rule), or content was cloned (`source_template_id` chain). All-or-nothing — no partial uninstall (Javadoc line 16–17). `PackInstallService.uninstall` catches `ResourceConflictException` and emits the `pack.uninstall_blocked` audit event before re-throwing.

### Verification approach

Pack correctness in QA is asserted via the **UI**, not API/DB ([`ADR-208`](../../adr/ADR-208-pack-verification-approach.md)) — Playwright navigates Settings pages and asserts visible items. This catches seeder, API, and frontend rendering regressions in one step.

## 7. Vertical specifics

This module **is** the verticalisation content channel — `vertical-profiles` selects *which* packs install at provisioning ([`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md) catalogues each profile's pack list). Per [`ADR-244`](../../adr/ADR-244-pack-only-vertical-profiles.md), some verticals (consulting-za) ship as **pack-only** — no backend module required, just JSON content + profile registry entry. Legal-za and accounting-za ship packs *plus* backend modules (trust-accounting, regulatory calendars).

`PackCatalogEntry.verticalProfile` (nullable string) is the discriminator. Universal packs (`null`) install for everyone; profile-scoped packs install only when the tenant's `OrgSettings.verticalProfile` matches. The catalog endpoint default-filters to the tenant's profile, so a `legal-za` tenant doesn't see `accounting-za` packs in the install UI.

Cross-link: [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md) for per-vertical pack inventory; [`vertical-profiles.md`](vertical-profiles.md) for the profile-registry side; [`60-verticals/legal-za.md`](../60-verticals/legal-za.md), [`60-verticals/accounting-za.md`](../60-verticals/accounting-za.md), [`60-verticals/consulting-za.md`](../60-verticals/consulting-za.md) for vertical-side detail.

## 8. Active ADRs

| ADR | Title | Status | Relevance |
|---|---|---|---|
| [`ADR-053`](../../adr/ADR-053-field-pack-seeding-strategy.md) | field-pack-seeding-strategy | Active | Original FieldPack pattern; reinforced by ADR-240 (per [`A4`](../_discovery/A4-adr-triage.md):62, :414). |
| [`ADR-063`](../../adr/ADR-063-compliance-packs-bundled-seed-data.md) | compliance-packs-bundled-seed-data | **Superseded-by ADR-240** in spirit (per [`A4`](../_discovery/A4-adr-triage.md):72, :415). |
| [`ADR-184`](../../adr/ADR-184-vertical-scoped-pack-filtering.md) | vertical-scoped-pack-filtering | Active | Profile filter = `verticalProfile == null \|\| equals(tenantProfile)`. |
| [`ADR-208`](../../adr/ADR-208-pack-verification-approach.md) | pack-verification-approach | Active | Verification via UI, not API/DB. |
| [`ADR-240`](../../adr/ADR-240-unified-pack-catalog-install-pipeline.md) | unified-pack-catalog-install-pipeline | Active | **Canonical for packs.** Defines `PackInstaller` SPI + `PackInstall` entity + classpath catalog. |
| [`ADR-241`](../../adr/ADR-241-add-only-pack-semantics.md) | add-only-pack-semantics | Active | No update/diff/merge — new version = new pack ID. |
| [`ADR-243`](../../adr/ADR-243-scope-two-pack-types-for-v1.md) | scope-two-pack-types-for-v1 | Active | Only `DOCUMENT_TEMPLATE` + `AUTOMATION_TEMPLATE` migrated to the unified pipeline. |
| [`ADR-244`](../../adr/ADR-244-pack-only-vertical-profiles.md) | pack-only-vertical-profiles | Active | Pack-only verticalisation path (consulting-za is the worked example). |

## 9. Key flows

- **Pack install + vertical onboarding** → [`50-flows/pack-install-and-vertical-onboarding.md`](../50-flows/pack-install-and-vertical-onboarding.md). Covers both the provisioning sequence (`TenantProvisioningService.installPacksViaPipeline` → `internalInstall`) and the post-provisioning catalog-driven path (`POST /api/packs/{packId}/install`).
- Phase doc context: [`architecture/phase65-packs-catalog-install-pipeline.md`](../../architecture/phase65-packs-catalog-install-pipeline.md) §65.1–65.2 (cited above for entity shape and scope-boundary).

## 10. Open questions / known fragility

1. **Profile switching does not uninstall.** `VerticalProfileReconciliationSeeder.reconcile(...)` is add-only — moving a tenant from `legal-za` to `consulting-generic` leaves legal packs installed and module-gated, not uninstalled (per [`A6`](../_discovery/A6-cross-cutting.md):262, [`10-bounded-contexts.md`](../10-bounded-contexts.md) §4 "Known fragility"). Profile change is one-way-safe but not reversible-clean. Tracked.
2. **Pack versioning.** No upgrade flow exists ([`ADR-241`](../../adr/ADR-241-add-only-pack-semantics.md)). Authoring a new version means publishing a new `packId`. What happens to user customisations layered on the old pack's templates is undefined — they remain attached to the old `PackInstall`. Multi-version coexistence is *legal* (additive) but the UX of "you have v1 and v2 installed simultaneously" is not designed.
3. **Conflict resolution.** Two packs declaring the same `field_definition.slug` or `document_template.slug` is not formally specified. The `UNIQUE` constraints will surface a `DataIntegrityViolationException` at install time, but there is no pre-check, no diagnostic, and no priority rule.
4. **Pack-content authoring workflow.** Where do new packs come from? Today: hand-authored JSON files in `backend/src/main/resources/{document-template-packs,automation-template-packs,field-packs,...}/*.json`, shipped with the JAR. There is no admin UI to author packs, no DB-backed catalog, and no third-party authoring story. ADR-240 considered and rejected a database-backed mutable catalog (Option 1) for v1.
5. **Two coexisting installation paths.** `TemplatePackSeeder` and `AutomationTemplateSeeder` are called *both* by their direct seeder paths and (via wrapper) by the unified `PackInstaller`. The 11 other pack types are direct-only. The transitional split is documented but is itself a fragility — agents must know which path is which when reasoning about a pack type.
6. **`PackInstall.itemCount` drift.** Captured at install time; not maintained if underlying content rows are individually deleted by a user. Not a defect today (uninstall gate uses FK queries, not the count) but the field will lie if read for "current item count".
7. **No bulk operations.** Install/uninstall are per-pack. No "install all `legal-za` packs" UI action; provisioning is the only place that batches.
8. **`OrgSettings` legacy shim.** `PackInstallService.updateOrgSettingsOnInstall/Uninstall` (`→ PackInstallService.java:261-278`) still maintains `OrgSettings.templatePackStatus` / `automationPackStatus` JSONB fields for back-compat. Phase 65 explicitly defers cleanup of these legacy fields (out of scope per phase65 §65.1). They will diverge from `PackInstall` truth if anything writes to one without the other.
