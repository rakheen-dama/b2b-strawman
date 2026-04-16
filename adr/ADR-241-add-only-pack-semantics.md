# ADR-241: Add-Only Pack Semantics

**Status**: Accepted

**Context**:

The Kazi Packs system introduces a unified catalog of pre-built content packs that tenants can install and uninstall. A natural follow-up question is: what happens when a pack's content changes between application releases? If `litigation-templates-2026-v1` ships with 6 templates in release 1.0 and 8 templates in release 1.1, should the system detect the diff and offer an "update" flow? Should it merge new templates into the tenant's schema while preserving edits to existing ones? Should it version-track which release of the pack is installed?

The existing `AbstractPackSeeder` infrastructure has a simple answer: packs are idempotent. If a pack is already applied (tracked via OrgSettings), the seeder skips it entirely. There is no diff, no merge, no update. A new pack version must ship as a new pack entry with a distinct ID. This is the behavior today, and the question is whether the new `PackInstallService` should introduce update/diff/merge semantics.

The design space is constrained by three realities: (1) pack content is installed into tenant schemas as regular entity rows — templates, rules — that tenants can edit, clone, and reference. An update flow must reason about these mutations. (2) There is no schema for pack content beyond the JSON files — no manifest that declares "these templates belong together and this is version N." (3) The first implementation targets two pack types with different content structures and edit patterns. A versioning engine that works for both would need to be content-type-agnostic.

**Options Considered**:

1. **Update-in-place with diff/merge** -- When a new release ships an updated pack (same pack ID, different content), detect the diff, present it to the admin, and offer to merge changes into the tenant's schema. Preserve tenant edits where possible; flag conflicts for manual resolution.
   - Pros:
     - Provides a smooth upgrade path — tenants always have the latest content
     - Familiar to developers (git merge semantics)
     - Single pack ID per concept, no version proliferation
   - Cons:
     - Content rows are mutable entity data, not source files. "Merging" a template update into a tenant-edited template requires content-type-aware diff logic: Tiptap JSON for document templates, trigger_config + conditions for automation rules. Two different merge implementations for two different data shapes, each with edge cases (structural changes to JSON trees, field additions, field removals)
     - Conflict resolution UI is a full product feature: showing diffs, allowing accept/reject per field, tracking resolution state. This is a multi-sprint effort for a feature that may never be used if most tenants do not edit pack content
     - No existing infrastructure for content versioning. `AbstractPackSeeder` has no concept of "which version of this pack is installed." The OrgSettings tracking stores `version` as a display string, not as a comparable semver
     - Destroys the idempotency guarantee. Today, re-running the seeder is safe — it skips applied packs. With update-in-place, re-running the seeder must detect diffs and trigger merges, turning a safe boot-time operation into a potentially destructive one
     - Risk of data loss. A merge bug that overwrites a tenant's carefully customized template is worse than not offering updates at all

2. **Versioned pack with explicit upgrade action** -- Each pack has a single ID. The catalog tracks which version is installed. When a new version ships, the UI shows "Update available" with a description of changes. Clicking "Update" replaces all content rows from the old version with the new version's content, after confirming that no edits have been made. If edits are detected, the update is blocked (same gate as uninstall).
   - Pros:
     - Cleaner UX than diff/merge — one action, clear outcome
     - Reuses the uninstall gate logic (edit detection, reference checks) to ensure no data loss
     - Single pack ID per concept — no version proliferation
   - Cons:
     - Still requires a versioning engine: compare installed version to catalog version, present the delta, execute the replacement. This is simpler than diff/merge but still non-trivial
     - "Replace all content" is destructive if the gate has false negatives (e.g., a new form of edit that the hash does not capture)
     - Tenants who have edited even one template are permanently stuck on the old version unless they manually revert their edits. This creates a support burden
     - Requires tracking "installed version" as a comparable value, not just a display string. Pack JSON files currently use arbitrary version strings ("1.0", "2026-v1")

3. **Add-only semantics: new version = new catalog entry** -- There is no update flow. A new version of a pack ships as a new catalog entry with a distinct pack ID (e.g., `litigation-templates-2026-v1` and `litigation-templates-2026-v2`). Installing v2 alongside v1 is legal and additive — it creates new content rows without modifying existing ones. Tenants can uninstall v1 if its content is unused.
   - Pros:
     - Zero complexity. No diff, no merge, no version comparison, no upgrade engine. The install and uninstall flows are the only operations
     - Impossible to cause data loss. New content is additive; old content is untouched. The tenant decides when (and whether) to uninstall the old pack
     - Preserves the idempotency guarantee. A pack with a given ID is either installed or not. Re-running the seeder is always safe
     - Consistent with prior art: VSCode extensions, Sketch symbol libraries, and Figma component libraries all use additive versioning — new versions are separate entries that coexist with old ones
     - Content duplication (v1 and v2 templates with similar names) is visible to the tenant, giving them explicit control over the transition
   - Cons:
     - Content duplication. If v2 ships 8 templates and v1 had 6 (with 5 overlapping), the tenant temporarily has 11 templates with similar names. This is confusing but not destructive
     - Pack ID proliferation. Over many releases, the catalog accumulates stale versions. Mitigated by profile filtering (old versions can be excluded from new profiles) and by the fact that most packs are stable (legal templates rarely change between releases)
     - No "recommended version" indicator. The tenant sees v1 and v2 side by side with no guidance on which is current. A future enhancement could add a `supersededBy` field to the pack JSON, but this phase does not implement it

**Decision**: Option 3 -- Add-only semantics. New pack version = new catalog entry.

**Rationale**:

The core insight is that pack content becomes tenant-owned data the moment it is installed. Templates are edited, cloned, and used to generate documents. Automation rules are configured, executed, and tuned. Any update mechanism must reason about these mutations — and reasoning about mutations in content-type-specific JSON structures is a substantial engineering investment that delivers value only when (a) pack content changes frequently and (b) tenants want to track upstream changes. Neither condition is true today. Pack content is stable across releases (legal templates do not change quarter-to-quarter), and tenants who customize pack content are explicitly choosing to diverge from the baseline.

Add-only semantics eliminate an entire category of bugs (merge conflicts, data loss on update, version comparison errors) at the cost of occasional content duplication. The duplication is visible and manageable — the tenant sees both versions in their template list and can uninstall the old one when ready. This is a better UX failure mode than a silent merge that overwrites customizations.

The idempotency preservation is also significant. `AbstractPackSeeder` and `PackReconciliationRunner` run on every application startup. With add-only semantics, these startup operations remain safe no-ops for already-installed packs. Update-in-place (Option 1) or versioned upgrade (Option 2) would turn startup reconciliation into a potentially destructive operation that requires diff logic, conflict detection, and possibly admin approval — all at boot time, before the application is ready to serve requests.

If the need for a true upgrade flow emerges in the future (e.g., regulatory template changes that must propagate to all tenants), it can be added as a new operation alongside install and uninstall. The `PackInstaller` interface is open for extension. But building it speculatively — before there is evidence that pack content changes frequently enough to justify the complexity — violates the project's simplicity-first principle.

**Consequences**:

- Pack IDs include a version component by convention (e.g., `litigation-templates-2026-v1`). This is a naming convention, not enforced by code
- Installing multiple versions of the same conceptual pack is legal. The catalog lists each version as a separate entry
- No `upgradeAvailable`, `latestVersion`, or `supersededBy` fields on `PackCatalogEntry` in this phase
- No diff, merge, or version comparison logic anywhere in the codebase
- Content duplication is the expected behavior when a tenant installs v2 alongside v1. The Templates and Automations pages show all content without version grouping
- Future enhancement: a `supersededBy` field in pack JSON files could allow the catalog to show "v2 is available" annotations, but this is out of scope
- `PackReconciliationRunner` startup behavior remains a safe no-op for already-installed packs
- Related: [ADR-240](ADR-240-unified-pack-catalog-install-pipeline.md)
