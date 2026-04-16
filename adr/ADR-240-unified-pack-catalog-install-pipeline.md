# ADR-240: Unified Pack Catalog & Install Pipeline

**Status**: Accepted

**Context**:

Kazi has 13 distinct pack seeders, each with its own classpath scanner, OrgSettings JSONB tracking field, and provisioning call site. Content rows created by these seeders have weak attribution back to their source pack: `document_template` has a loose `pack_id VARCHAR(100)` column that is not a foreign key, and `automation_rule` has no pack attribution column at all. This fragmentation creates four gaps: (1) no unified catalog API or UI that shows "here is everything available on Kazi", (2) no post-provisioning install path — packs are applied at provisioning and startup only, (3) no uninstall path — once applied, content lives forever, and (4) per-type tracking via OrgSettings JSONB fields with no relational linkage to the content rows they created.

The goal is to unify pack installation behind a single pipeline that supports both programmatic (provisioning, reconciliation) and interactive (Settings UI) installation, tracks every install as a first-class entity with FK linkage to content rows, and provides a foundation for future uninstall, upgrade, and catalog browsing capabilities.

The design must coexist with the existing `AbstractPackSeeder` infrastructure, which provides classpath scanning, JSON deserialization, idempotency tracking, and tenant transaction scoping. Replacing `AbstractPackSeeder` entirely would require rewriting all 13 seeders at once, which is impractical. The new pipeline must wrap the existing infrastructure, not replace it.

**Options Considered**:

1. **Database-backed mutable catalog** -- Store pack metadata in a global `pack_catalog` table. Admin UI to publish, retract, and version packs. Install pipeline reads from this table.
   - Pros:
     - Supports future third-party pack authoring and marketplace scenarios
     - Catalog contents can be managed without redeployment
     - Standard CRUD pattern — familiar to developers
   - Cons:
     - Duplicates metadata already present in classpath JSON files. Pack content (templates, rules) lives in version-controlled JSON — the catalog metadata would diverge unless actively synced
     - Introduces a global migration and a new global entity for metadata that is static in practice (pack files change only at deploy time)
     - Sync between classpath files and DB catalog is a new failure mode: stale entries, orphaned entries, version mismatches
     - Over-engineers the problem for a system where all packs are first-party and shipped with the application

2. **Per-type install tracking in OrgSettings (extend existing pattern)** -- Add more structured JSONB fields to OrgSettings. Keep each pack type's seeder as the install path. Add uninstall logic per seeder.
   - Pros:
     - No new entities or tables — extends the existing pattern
     - Minimal code change for existing seeders
     - OrgSettings is already the idempotency source for all 13 pack types
   - Cons:
     - JSONB fields provide no relational linkage to content rows. Uninstall requires a reverse lookup: "which content rows came from this pack?" Without a FK, this requires fragile heuristics (pack_id column for templates, timestamp heuristic for automation rules)
     - No unified catalog — each seeder exposes its own pack list independently. A catalog API would need to aggregate from 13 sources, each with different structures
     - Uninstall logic duplicated across seeders — each seeder would implement its own delete logic, edit detection, reference checks
     - Does not scale to a Settings UI — the UI would need type-specific adapters for each pack type's JSONB structure

3. **Unified PackInstall entity + PackInstaller interface (classpath-scanned catalog)** -- Introduce a `PackInstall` entity as the single source of truth for install tracking. Define a `PackInstaller` interface that each pack type implements. The catalog is built at runtime from classpath scans via the existing `AbstractPackSeeder` infrastructure. `PackInstallService` provides the single install/uninstall entry point. Content rows gain a `source_pack_install_id` FK to `PackInstall`.
   - Pros:
     - Single source of truth for "what is installed in this tenant" — one entity, one table, one repository, one service
     - FK linkage from content rows to `PackInstall` enables clean uninstall (query all rows with this install ID), edit detection (compare content hashes), and reference checks (check for generated documents, executions, clones)
     - Unified catalog API — `PackCatalogService` aggregates entries from all registered `PackInstaller` beans. One endpoint, one response shape, regardless of pack type
     - Classpath-scanned catalog preserves the existing source of truth (JSON files in git). No sync, no duplication, no drift
     - `PackInstaller` interface is extensible — future phases add implementations for other pack types without changing the service or controller
     - `AbstractPackSeeder` infrastructure is reused, not replaced — installers wrap seeder logic, not duplicate it
   - Cons:
     - Requires a new tenant migration (new table + FK columns). Acceptable because the backfill migration runs once per tenant at deploy time
     - Backfill for existing tenants is imperfect for automation rules (timestamp heuristic). Acceptable because the only consequence is that legacy automation packs cannot be uninstalled — a safe default
     - Two sources of truth during the transition period: `PackInstall` rows (new) and OrgSettings JSONB fields (legacy, retained for compatibility). Acceptable because the OrgSettings fields are explicitly marked as compatibility shims

**Decision**: Option 3 -- Unified `PackInstall` entity + `PackInstaller` interface with classpath-scanned catalog.

**Rationale**:

The decisive factor is the FK linkage between content rows and their source pack install. Without this linkage, uninstall, edit detection, and reference checks require fragile reverse lookups — the exact problem that makes uninstall impossible today. A `source_pack_install_id` FK on `document_templates` and `automation_rules` provides a direct, indexed path from "this pack" to "all content it created." Every uninstall check, every edit detection query, and every reference check reduces to a simple indexed lookup against this FK.

The classpath-scanned catalog is the right choice because pack content is first-party and version-controlled. The JSON files in `src/main/resources/` are the source of truth — they ship with the application binary and change only at deploy time. A database-backed catalog would duplicate this metadata and introduce sync complexity for zero practical benefit. If Kazi ever supports third-party packs or a marketplace, a database catalog can be introduced at that point. Until then, the classpath scan is simpler, faster, and eliminates an entire category of bugs (stale metadata, orphaned entries, version drift).

The `PackInstaller` interface provides the extensibility seam. This phase implements two installers (`TemplatePackInstaller`, `AutomationPackInstaller`). Future phases add installers for clauses, fields, compliance checklists, etc. The service and controller code is type-agnostic — it delegates to the registered installer for the pack's type. No switch statements, no type-specific branches in shared code.

The transition-period dual source of truth (PackInstall rows + OrgSettings JSONB fields) is an acceptable trade-off. The OrgSettings fields are read by `PackReconciliationRunner` and the existing seeders for the 11 pack types not yet migrated. Removing them requires migrating all 13 pack types to the new pipeline, which is explicitly out of scope for this phase. The `PackInstallService` populates both sources on install and removes from both on uninstall, keeping them consistent during the transition.

**Consequences**:

- New `packs/` package with `PackInstall`, `PackInstallRepository`, `PackInstaller`, `PackCatalogService`, `PackInstallService`, `PackCatalogController`
- New `pack_install` table in every tenant schema (V94 migration)
- `document_templates` and `automation_rules` gain `source_pack_install_id UUID FK` and `content_hash VARCHAR(64)` columns
- Backfill migration (V95) creates synthetic `PackInstall` rows for existing tenants
- `TenantProvisioningService` and `PackReconciliationRunner` route document template and automation template packs through `PackInstallService` instead of direct seeder calls
- Legacy `OrgSettings` JSONB fields and `document_template.pack_id` column remain populated as compatibility shims
- Future phases migrate additional pack types by adding `PackInstaller` implementations — no changes to the service or controller layer
- Related: [ADR-241](ADR-241-add-only-pack-semantics.md), [ADR-242](ADR-242-never-used-uninstall-rule.md), [ADR-243](ADR-243-scope-two-pack-types-for-v1.md)
