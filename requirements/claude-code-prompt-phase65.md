# Phase 65 — Kazi Packs Catalog & Install Pipeline

## System Context

Over the last ~30 phases, HeyKazi has accumulated a rich collection of content "packs" — pre-built sets of domain content that get seeded into a new tenant's schema at provisioning time based on its vertical profile (`legal-za`, `accounting-za`, `consulting-generic`). Today there are 13 distinct pack seeders, each with its own type:

| Pack Type | Seeder | Content |
|---|---|---|
| Document templates | `template/TemplatePackSeeder.java` | Letterheads, engagement letters, matter templates |
| Clauses | `clause/ClausePackSeeder.java` | Reusable template clauses |
| Automation templates | `automation/template/AutomationTemplateSeeder.java` | Pre-built workflow rules |
| Field definitions | `fielddefinition/FieldPackSeeder.java` | Custom field groups per vertical |
| Compliance checklists | `compliance/CompliancePackSeeder.java` | Customer lifecycle checklists |
| Request templates | `informationrequest/RequestPackSeeder.java` | Client information requests |
| Rate cards | `seeder/RatePackSeeder.java` | Initial billing/cost rates |
| Schedules | `seeder/SchedulePackSeeder.java` | Recurring deadline schedules |
| Project templates | `seeder/ProjectTemplatePackSeeder.java` | Matter/engagement kickoff templates |
| LSSA tariff | `verticals/legal/tariff/LegalTariffSeeder.java` | Legal Practice Act tariff rates |
| Trust reports | `verticals/legal/trustaccounting/report/TrustReportPackSeeder.java` | Section 35 report definitions |
| Standard reports | `reporting/StandardReportPackSeeder.java` | Report library |
| Data request templates | `datarequest/ComplianceTemplatePackSeeder.java` | DSAR/POPIA request templates |

All extend a common base class `seeder/AbstractPackSeeder.java<D>`, which handles classpath scanning, JSON deserialization, idempotency tracking via `OrgSettings` JSONB fields, vertical profile filtering, and tenant transaction scoping. Packs live as JSON in the classpath; each pack declares an `id`, `version`, `name`, and `verticalProfile` (or null for universal packs).

**Current reality — what's missing:**

1. **No unified catalog.** Each pack type is discovered independently via its own classpath scan. There is no single API or UI that says "here is everything available on HeyKazi."
2. **No post-provisioning install path.** Packs are seeded by `TenantProvisioningService` at provisioning time and by `PackReconciliationRunner` at startup. Tenants cannot opt into a new pack through the UI. If a firm wants a pack not included in their profile default, the only options today are (a) ship a new Flyway migration (noisy, applies to everyone) or (b) edit `OrgSettings` manually.
3. **No uninstall path.** Once a pack is applied, its content lives forever in the tenant schema. There is no way to remove it, even if it was never used.
4. **Per-type tracking.** Each seeder writes its own status field to `OrgSettings` (`templatePackStatus`, `automationPackStatus`, etc. — JSONB lists keyed by pack id). There is no single source of truth for "what is installed in this tenant."
5. **Weak attribution on installed content.** `document_template` has a loose `pack_id VARCHAR(100)` column set by the seeder but it is not a foreign key and does not survive cleanly through clone flows. `automation_rule` has no pack attribution column at all. This prevents both clean uninstall and upgrade-to-new-pack paths.

This phase introduces the **Kazi Packs** concept as a first-class product surface: a unified catalog, a tenant-visible install/uninstall flow exposed in Settings, and a refactor of the existing profile-provisioning path so that all pack application — whether at provisioning or post-hoc — flows through the same pipeline. To keep scope bounded, we apply the new pipeline to **two pack types only** in this phase: **Document Templates** and **Automation Templates**. The remaining pack types keep their current direct-seeder paths; subsequent phases will migrate them.

## Objective

Introduce a unified **Pack Catalog** concept (type-agnostic metadata + install/uninstall API + Settings UI) and apply it end-to-end to Document Templates and Automation Templates. Refactor profile provisioning to route those two pack types through the new `PackInstallService`. Track every installed pack as a first-class `PackInstall` row, tag every content row that came from a pack with a foreign key to its install, and use that linkage to enforce a strict "only uninstall if never used" rule.

**Design principles:**

1. **Add-only semantics.** There is no update, diff, or merge flow. A new version of a pack ships as a new pack entry in the catalog (e.g., `litigation-templates-2026-v1` and `litigation-templates-2026-v2` are distinct). Installing v2 alongside v1 is legal and additive. Tenants can uninstall v1 if unused.
2. **Never-used-only uninstall.** A pack can only be uninstalled if every row it created is (a) unedited since install and (b) not referenced by any downstream entity. If any condition fails, the API returns 409 with a precise reason. No soft delete, no archive.
3. **Single install pipe.** After this phase, there must be only one code path that installs a document template pack or an automation template pack: `PackInstallService.install(packId)`. Profile provisioning, startup reconciliation, and the new UI all go through it.
4. **Source of truth stays in git.** Pack JSON files remain in `src/main/resources/*-packs/`. The DB catalog is a runtime index built from classpath scans at startup, not a mutable store.

## Constraints & Assumptions

- **Two pack types only.** Document Templates and Automation Templates. All other pack types (clauses, fields, compliance, request, rate, schedule, project, tariff, trust report, standard report, data request) keep their current seeders unchanged. The `PackInstaller` interface must be general enough to accommodate them in future phases, but this phase does not migrate them.
- **Tenant-scoped install tracking.** `PackInstall` is a per-tenant entity, migrated into every tenant schema. There is no global "which tenants have this pack" view in this phase.
- **Profile provisioning refactor is required.** Profile provisioning for Document Templates and Automation Templates MUST call `PackInstallService.install(...)`. No parallel paths.
- **Backfill existing tenants.** Tenants provisioned before this phase have pre-existing rows in `document_template` and `automation_rule` that came from pack seeding but have no `source_pack_install_id` and no corresponding `PackInstall` row. For document templates, the existing loose `pack_id VARCHAR(100)` column can be used as a backfill hint (rows with a non-null `pack_id` belong to that pack). For automation rules, no attribution column exists, so backfill relies on the pack ids stored in `OrgSettings.automationPackStatus` and a creation-timestamp heuristic. A one-time Flyway migration must backfill synthetic `PackInstall` rows from `OrgSettings.templatePackStatus` / `automationPackStatus` and update existing content rows to reference them. Content that cannot be attributed to a pack (user-created templates, user-created automations) stays with `source_pack_install_id = NULL`.
- **Legacy `OrgSettings` applied-pack fields must stay populated.** Until a future phase removes them, `PackInstallService.install()` must continue to update the legacy `templatePackStatus` and `automationPackStatus` JSONB fields in `OrgSettings` so that `PackReconciliationRunner` and any existing gap-detection tooling keeps working. The loose `document_template.pack_id` column must also continue to be populated at install time (read path compatibility). The `PackInstall` entity + `source_pack_install_id` FK is the new source of truth; the `OrgSettings` fields and the loose `pack_id` column are compatibility shims retained for this phase.
- **No versioning engine.** The catalog exposes each pack's version as a display string. There is no migration, upgrade, or compatibility check.
- **Profile affinity is a hard filter by default.** Catalog API and UI filter out packs whose `verticalProfile` does not match the current tenant's profile. A `?all=true` query parameter (backend) and a "Show all packs" toggle (frontend) reveal cross-profile packs for power users.
- **No new pack content ships with this phase.** This is infrastructure work. The existing JSON packs in `src/main/resources/template-packs/` and `src/main/resources/automation-template-packs/` (or wherever they currently live — inspect the seeders for the actual paths) are the catalog's initial entries.

---

## Section 1 — Pack Catalog Concept

### 1.1 Shared Types

Create a new package `io.b2mash.b2b.b2bstrawman.packs`.

Define:

- **`PackType` enum** — `DOCUMENT_TEMPLATE`, `AUTOMATION_TEMPLATE`. (Extensible; future phases add more.)
- **`PackCatalogEntry`** — immutable record returned by the catalog API:
  ```
  record PackCatalogEntry(
      String packId,           // e.g. "litigation-templates-2026-v1"
      String name,             // Display name
      String description,      // Short description
      String version,          // Display-only version string
      PackType type,
      String verticalProfile,  // nullable — null = universal
      int itemCount,           // e.g. "6 templates", "3 automation rules"
      boolean installed,       // computed for the current tenant
      String installedAt       // ISO-8601 timestamp, null if not installed
  )
  ```
- **`PackCatalogService`** — aggregates entries from every registered `PackInstaller`. Each installer exposes its catalog entries (loaded from classpath scans via the existing `AbstractPackSeeder` infrastructure). The service computes `installed` / `installedAt` from the `PackInstall` repository for the current tenant.

### 1.2 `PackInstaller` Interface

```
public interface PackInstaller {
    PackType type();
    List<PackCatalogEntry> availablePacks();  // scanned from classpath
    void install(String packId, String tenantId, String memberId);
    UninstallCheck checkUninstallable(String packId, String tenantId);
    void uninstall(String packId, String tenantId, String memberId);
}
```

- `availablePacks()` returns the full list regardless of tenant — the catalog service handles profile filtering and install-state computation.
- `install` is idempotent. Installing a pack that is already installed is a no-op and returns success.
- `checkUninstallable` returns an `UninstallCheck` record: `new UninstallCheck(boolean canUninstall, String blockingReason)`. The `blockingReason` must be specific enough for the UI to show: e.g., "3 templates have been edited" or "2 rules have matched events in the last 30 days."
- `uninstall` MUST call `checkUninstallable` first and throw `InvalidStateException` if blocked.

Concrete implementations for this phase:

- **`TemplatePackInstaller`** — delegates install to the existing `TemplatePackSeeder` logic but wraps it with `PackInstall` row creation and `source_pack_install_id` tagging.
- **`AutomationPackInstaller`** — same pattern for the existing `AutomationTemplateSeeder` logic.

Existing seeders should be refactored to expose their "apply one pack" logic as a package-private method that both the installer and any legacy reconciliation code can call. Do not duplicate the classpath-scanning and JSON-parsing logic — reuse what `AbstractPackSeeder` already provides.

---

## Section 2 — `PackInstall` Entity

### 2.1 Entity

New entity `packs/PackInstall.java`, tenant-scoped, mapped to `pack_install` table:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `pack_id` | varchar(128) | e.g., `litigation-templates-2026-v1` |
| `pack_type` | varchar(64) | `PackType` enum value |
| `pack_version` | varchar(32) | display-only |
| `pack_name` | varchar(256) | captured at install time |
| `installed_at` | timestamptz | |
| `installed_by_member_id` | uuid | nullable for system installs (provisioning) |
| `item_count` | int | number of content rows created at install time |

Unique constraint on `(pack_id)` within a tenant schema.

Repository: standard `JpaRepository<PackInstall, UUID>` with `findByPackId(String)` and `findAll()`. No multitenancy boilerplate — schema isolation handles it (Phase 13 convention).

### 2.2 Migration

New Flyway tenant migration `V{next}__create_pack_install.sql`:

- Create `pack_install` table
- Add `source_pack_install_id uuid` column (nullable, FK to `pack_install.id`) to:
  - `document_template`
  - `automation_rule`
- Both columns are nullable — user-created rows have `NULL`.
- Index on each `source_pack_install_id` column for the uninstall lookup.

### 2.3 Backfill Migration

New Flyway tenant migration `V{next+1}__backfill_pack_installs.sql` (raw SQL — no Java code):

For each tenant schema, for each pack ID present in `OrgSettings.template_pack_status` and `OrgSettings.automation_pack_status` JSONB fields:

1. Insert a synthetic `PackInstall` row with a generated UUID, the known pack ID, the pack type, `installed_at = now()`, `installed_by_member_id = NULL`, and a placeholder name (e.g., the pack ID itself — the catalog will re-resolve display fields from classpath at read time).
2. For **document templates**: `UPDATE document_template SET source_pack_install_id = <backfilled install id> WHERE pack_id = <packId>`. This uses the existing loose `pack_id VARCHAR(100)` column as the attribution source. Rows with `pack_id IS NULL` (user-created) stay with `source_pack_install_id = NULL`.
3. For **automation rules**: there is no attribution column. Use a creation-timestamp heuristic: for each pack id in `automation_pack_status`, update all `automation_rule` rows created within a window around the pack's recorded `appliedAt` timestamp to reference the backfilled install. Accept that this is a coarse backfill — the only acceptance criterion is that `checkUninstallable` returns a sensible answer on a legacy tenant. Rules that cannot be attributed with reasonable confidence (outside the window, or overlapping windows) stay with `source_pack_install_id = NULL`.

Document this tradeoff clearly in the migration's leading comment block. Explicitly note that the automation-rule backfill is best-effort and that legacy tenants may see some rules marked as "unattributed" (uninstall not possible, but safe default).

---

## Section 3 — `PackInstallService`

New class `packs/PackInstallService.java`. Single public surface for installing and uninstalling packs.

```
public PackInstall install(String packId, String memberId);       // throws ResourceNotFoundException if packId not in catalog
public void uninstall(String packId, String memberId);            // throws InvalidStateException if blocked
public UninstallCheck checkUninstallable(String packId);          // non-destructive precheck for UI
```

Responsibilities:

1. Resolve the pack ID → catalog entry → `PackInstaller` via the registry of installers (Spring injects `List<PackInstaller>`; the service builds a map by `PackType`).
2. Enforce profile affinity: if the pack's `verticalProfile` is not null and does not match the current tenant's profile, throw `InvalidStateException` (unless called from internal provisioning — see below).
3. Enforce idempotency: if a `PackInstall` for this pack ID already exists, return it as a no-op.
4. Delegate to `PackInstaller.install(...)`. The installer MUST:
   - Create the `PackInstall` row first.
   - Create all content rows with `source_pack_install_id` set to the new install's ID.
   - Populate `item_count` on the install row.
5. Update legacy `OrgSettings.templatePackStatus` / `automationPackStatus` for compatibility.
6. Emit `PACK_INSTALLED` audit event.
7. Emit notification (info-level) to the installing member.

For internal provisioning callers (profile-manifest seeding), expose a second method `internalInstall(String packId, List<String> manifestProfileIds)` that bypasses the profile-affinity check (provisioning knows what it's doing) but still goes through the same install pipe. This prevents the need for a parallel code path.

### 3.1 Profile Provisioning Refactor

`TenantProvisioningService` and `PackReconciliationRunner` currently call `TemplatePackSeeder.seedPacksForTenant(tenantId, orgId)` and `AutomationTemplateSeeder.seedPacksForTenant(tenantId, orgId)` directly.

Change them to call `PackInstallService.internalInstall(packId)` for each pack in the profile manifest for those two pack types. Leave the direct seeder calls intact for the other 11+ pack types.

Acceptance: a newly provisioned tenant ends up with the same content it has today, but now every document template and automation rule has a non-null `source_pack_install_id` and a corresponding `PackInstall` row.

---

## Section 4 — Uninstall Semantics (Precise)

### 4.1 Document Templates — `TemplatePackInstaller.checkUninstallable`

A document template pack is uninstallable if and only if ALL templates with `source_pack_install_id = <install.id>` satisfy:

1. **Unedited.** The template's current content hash matches the hash captured at install time. To support this, add a `content_hash` column to `document_template` in the pack_install migration and populate it when a row is created from a pack install. Hash algorithm: SHA-256 over the canonical JSON of the Tiptap content field.
2. **Not referenced by any `generated_document` row.** A template that has ever been used to generate a document is not removable.
3. **Not cloned.** `document_template` has a `source_template_id` column (Phase 12) that tracks clone lineage. If any other template references this one via `source_template_id`, the pack cannot be uninstalled.

Blocking reason format: `"N of M templates have been edited"`, `"N templates have been used to generate documents"`, `"N templates have been cloned"`. Concatenate reasons with `; ` if multiple apply.

### 4.2 Automation Templates — `AutomationPackInstaller.checkUninstallable`

An automation pack is uninstallable if and only if ALL automation rules with `source_pack_install_id = <install.id>` satisfy:

1. **Unedited.** `content_hash` match over the canonical JSON of the trigger + conditions + actions payload. Add a `content_hash` column to `automation_rule` in the pack_install migration and populate it when a row is created from a pack install.
2. **Never executed.** No row in the `automation_executions` table references the rule. If the executions table is time-bounded by retention, the check is strict on what's currently in the table — "never executed in currently-retained history."
3. **Not cloned.** `automation_rule` has no clone/lineage column today, so the "not cloned" check does not apply to automation packs in this phase. (If clone tracking is added in a future phase, this rule activates automatically.)

Blocking reason format: parallel to templates.

### 4.3 Uninstall Execution

If `checkUninstallable` passes:

1. `DELETE` all content rows with this `source_pack_install_id`.
2. `DELETE` the `PackInstall` row.
3. Remove the pack ID from the legacy `OrgSettings.templatePackStatus` / `automationPackStatus` JSONB field.
4. Emit `PACK_UNINSTALLED` audit event.

If blocked:

1. Throw `InvalidStateException` with the blocking reason as the detail.
2. Emit `PACK_UNINSTALL_BLOCKED` audit event with the same reason (for admin visibility).

No rows are modified on a blocked uninstall.

---

## Section 5 — REST API

New controller `packs/PackCatalogController.java`. Thin controller discipline — delegate to `PackCatalogService` and `PackInstallService`.

| Method | Path | Capability | Description |
|---|---|---|---|
| `GET` | `/api/packs/catalog` | `TEAM_OVERSIGHT` | List pack entries for the current tenant. Query param `?all=true` to show cross-profile packs. Default filters by current `OrgSettings.verticalProfile`. |
| `GET` | `/api/packs/installed` | `TEAM_OVERSIGHT` | List `PackInstall` rows for the current tenant (projected to catalog entries with `installed=true`). |
| `GET` | `/api/packs/{packId}/uninstall-check` | `TEAM_OVERSIGHT` | Returns `{canUninstall: bool, blockingReason: string?}`. Used by the UI to enable/disable the uninstall button. |
| `POST` | `/api/packs/{packId}/install` | `TEAM_OVERSIGHT` | Install. Returns 200 + the `PackInstall` row. Idempotent. |
| `DELETE` | `/api/packs/{packId}` | `TEAM_OVERSIGHT` | Uninstall. Returns 204 on success, 409 with `ProblemDetail` on block. |

Audit events (lowercase dotted convention, matching existing audit catalog): `pack.installed`, `pack.uninstalled`, `pack.uninstall_blocked`. Each includes `packId`, `packType`, `packVersion`, and (on block) `blockingReason`.

---

## Section 6 — Frontend

### 6.1 Settings → Packs Page

Add a new Settings nav item **"Packs"** (new top-level entry in the Settings sidebar, grouped with org-level configuration — not under any vertical-specific section).

Page location: `frontend/app/(app)/org/[slug]/settings/packs/page.tsx` (org-scoped, matching the Phase 44 settings layout).

### 6.2 Page Layout

Two tabs: **Available** and **Installed**.

**Available tab:**
- Header: "Kazi Packs — extend your workspace with pre-built content"
- Toggle: "Show all packs" (off by default; when off, only packs matching the current profile are visible)
- Grid of pack cards. Each card displays:
  - Pack name + version
  - Short description
  - Item count badge (e.g., "6 templates", "3 rules")
  - Pack type badge (Document Templates / Automation Templates)
  - Profile affinity badge (e.g., "Legal · ZA", "Universal")
  - Primary action: **Install** button (or **Installed ✓** disabled state if already installed)

**Installed tab:**
- Header: "Installed Packs"
- Grid of cards for currently-installed packs. Each card displays:
  - Same metadata as the Available tab
  - `Installed on <date>` + `by <member name>` (or "by system" for backfilled / provisioning installs)
  - Primary action: **Uninstall** button. If `uninstall-check` returns blocked, the button is disabled with a tooltip showing the `blockingReason`.
  - Uninstall flow: confirmation dialog ("This will remove <item_count> items. Only allowed because none have been used. Continue?")

### 6.3 API Client

Add API client functions in the existing frontend API layer:
- `listPackCatalog(opts?: { all?: boolean })`
- `listInstalledPacks()`
- `checkPackUninstallable(packId)`
- `installPack(packId)`
- `uninstallPack(packId)`

### 6.4 Empty States

- **Available tab, no packs for profile**: "No Kazi Packs available for your current profile. Toggle 'Show all packs' to browse everything."
- **Installed tab, none installed**: "No packs installed yet. Browse the Available tab to add templates and workflow automations to your workspace."

### 6.5 UI Components

Reuse existing Shadcn `Card`, `Button`, `Badge`, `Tabs`, `Tooltip`, `Switch`, and `AlertDialog` primitives. No new component library work. Match the visual language of the Settings hub (Phase 63 / Phase 44).

---

## Section 7 — Testing

### 7.1 Backend

- **Catalog listing**: catalog returns the expected packs for a `legal-za` tenant (filtered) and for `?all=true` (unfiltered).
- **Install idempotency**: installing the same pack twice is a no-op and creates only one `PackInstall` row.
- **Profile affinity enforcement**: a `legal-za` pack cannot be installed on an `accounting-za` tenant via the public API; `internalInstall` bypasses the check.
- **Content attribution**: after installing the legal litigation template pack, every newly created `document_template` row has `source_pack_install_id` set to the install's ID.
- **Uninstall clean path**: install a pack, immediately uninstall, verify all content rows and the `PackInstall` row are gone, verify the `OrgSettings` legacy field no longer contains the pack ID.
- **Uninstall blocked — edited content**: install a pack, edit one template's content, attempt uninstall, verify 409 with `"1 of N templates have been edited"` in the detail.
- **Uninstall blocked — generated document reference**: install a pack, generate a document from one of its templates, attempt uninstall, verify 409 with the reference-based reason.
- **Uninstall blocked — automation has matched**: install an automation pack, trigger a matching event, attempt uninstall, verify 409.
- **Provisioning refactor**: a newly-provisioned tenant with profile `legal-za` ends up with non-null `source_pack_install_id` on every template/rule that came from a pack, and a corresponding `PackInstall` row exists for each pack in the profile manifest.
- **Backfill migration**: after the backfill migration runs against a tenant with pre-existing pack-sourced content, every pack ID from `OrgSettings.templatePackStatus` has a corresponding `PackInstall` row and the uninstall-check returns a sensible answer.
- **Legacy field compatibility**: installing a pack via `PackInstallService` updates `OrgSettings.templatePackStatus`. `PackReconciliationRunner` still skips already-applied packs.
- **Audit events**: `pack.installed` and `pack.uninstalled` audit events are emitted with the correct fields.

### 7.2 Frontend

- **Catalog renders**: Available tab lists the expected packs; profile filter works; "Show all packs" toggle reveals cross-profile packs.
- **Install flow**: clicking Install triggers the API call, moves the pack to the Installed tab, and shows a success toast.
- **Uninstall-check integration**: the uninstall button is disabled and shows the blocking reason in a tooltip when the pack is in use.
- **Uninstall confirmation**: the confirmation dialog shows the correct item count.
- **Empty states**: rendered when no packs match the current filter.

### 7.3 End-to-End

One Playwright test that walks through: login → navigate to Settings → Packs → install a pack → verify content appears in the Templates page → edit one template → attempt uninstall → see blocked state → revert the edit → uninstall succeeds. Use the mock-auth E2E stack.

---

## Out of Scope

- **Other pack types.** Clauses, field definitions, compliance checklists, request templates, rate cards, schedules, project templates, tariff, trust reports, standard reports, data request templates — unchanged in this phase. Their seeders continue to run as today. A future phase migrates them one by one.
- **Pack versioning / updates.** No diff, no merge, no "update available" prompt. New version = new catalog entry.
- **Paid or entitlement-gated packs.** No Stripe/PayFast integration. Every pack is free and installable by any org admin.
- **Third-party pack authoring.** No SDK, no publishing flow, no manifest validation beyond what `AbstractPackSeeder` already does.
- **Pack dependencies.** Packs are independent. No "pack A requires pack B" logic.
- **Partial uninstall.** A pack is all-or-nothing. No cherry-picking which items within a pack to keep.
- **Global / cross-tenant catalog management UI.** No platform-admin interface to publish or retract catalog entries — catalog contents are whatever is in the classpath.
- **Pack preview.** No "show me the templates before I install" view. The item count and description are the only previews. Post-install, the existing Templates and Automations pages show the content.
- **Soft delete or archive on uninstall.** Uninstall is a hard delete or nothing.
- **Cleanup of legacy `OrgSettings.templatePackStatus` / `automationPackStatus` fields.** Kept for compatibility; a future phase removes them once all consumers are migrated.

## ADR Topics

- **ADR — Unified Pack Catalog & Install Pipeline.** Document the decision to unify pack installation behind `PackInstallService` and `PackInstaller`, the rationale for tagging content rows with `source_pack_install_id`, and why the catalog is built from classpath scans rather than a DB-authored store.
- **ADR — Add-Only Pack Semantics.** Document why v1 explicitly rejects update/diff/merge flows. New pack version = new catalog entry. Cite VSCode extensions and Sketch libraries as prior art.
- **ADR — "Never Used" Uninstall Rule.** Define the precise semantics of the uninstall gate (unedited + not referenced + not cloned) for each pack type, and why we chose a hard gate over soft-delete or cascading archive.
- **ADR — Scope: Two Pack Types for v1.** Document why Document Templates and Automation Templates were chosen as the first pack types, and the criteria (isolation, demo value, structural diversity) for deciding which pack types to migrate in which order.

## Style & Boundaries

- **Reuse `AbstractPackSeeder` infrastructure.** Do not reinvent classpath scanning, JSON parsing, or tenant transaction scoping. The new `PackInstaller` implementations wrap existing seeder logic.
- **Thin controller.** `PackCatalogController` is pure delegation. All logic in `PackCatalogService` / `PackInstallService`.
- **No Lombok, no field injection.** Constructor injection only. Java 25 records for DTOs.
- **Standard `JpaRepository`.** `PackInstall` is a plain entity — no `@Filter`, no `tenant_id` column, schema isolation handles it (Phase 13 convention).
- **Error handling.** Use semantic exceptions (`ResourceNotFoundException`, `InvalidStateException`, `ForbiddenException`) from the `exception/` package. Never build `ProblemDetail` directly.
- **Capability checks.** Use `@RequiresCapability("TEAM_OVERSIGHT")` on controller methods, consistent with every other settings controller in the codebase (Phase 41 capability model).
- **Scope.** This is an infrastructure + UX phase, not a new-feature phase. No new content, no new pack types, no new vertical modules. Keep the implementation tight: ~6 epics, ~10–14 slices.
- **Naming.** Front-facing name is "Kazi Packs". Internal class names use `Pack*` prefix (`PackInstall`, `PackInstaller`, `PackCatalogEntry`, `PackInstallService`, `PackCatalogController`, `PackType`). Frontend route is `/settings/packs`. User-visible strings say "Kazi Packs" on the page header but "packs" in body copy.
- **Legacy coexistence.** Every change must preserve today's behavior for the 11 pack types not being migrated in this phase. `PackReconciliationRunner` must continue to call their seeders directly. The `document_template.pack_id` column and `OrgSettings.templatePackStatus` / `automationPackStatus` JSONB fields remain populated by the new install pipe as compatibility shims.
