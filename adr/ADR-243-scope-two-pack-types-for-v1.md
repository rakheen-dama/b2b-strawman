# ADR-243: Scope: Two Pack Types for v1

**Status**: Accepted

**Context**:

The Kazi Packs Catalog & Install Pipeline (Phase 65) introduces a `PackInstaller` interface designed to be type-agnostic, but the phase must choose which of the 13 existing pack types to migrate first. Migrating all 13 in a single phase is impractical — each type has different content structures, different reference patterns, and different uninstall semantics. The question is: which pack types provide the best combination of demonstration value, implementation confidence, and structural diversity for validating the pipeline design?

The 13 pack types fall into three structural categories:

**Simple entity packs** (content rows with no downstream references): Field definitions, rate cards, schedules, LSSA tariff items. These are configuration data — they are read but not "used" in a way that creates tracked references (no generated documents, no executions, no clone chains). Uninstall would be straightforward but would not exercise the reference-checking logic that is the most complex part of the uninstall gate.

**Content packs with downstream references**: Document templates (referenced by `generated_documents`, cloned via `source_template_id`), automation templates (referenced by `automation_executions`), compliance checklists (instantiated as `checklist_instances`), request templates (instantiated as `information_requests`). These exercise the full uninstall gate logic.

**Standalone seeders** (not extending `AbstractPackSeeder`): Standard reports, legal tariff, trust reports. These have bespoke seeding logic and would require the most adaptation work to conform to the `PackInstaller` interface.

The selection criteria are: (1) **isolation** — the pack type can be migrated without cascading changes to other pack types, (2) **demo value** — the pack type is user-visible in the Settings UI and demonstrates the install/uninstall flow compellingly, (3) **structural diversity** — the selected types exercise different aspects of the pipeline (different content structures, different reference patterns, different uninstall gate logic), and (4) **existing infrastructure fit** — the pack type extends `AbstractPackSeeder` and has existing classpath-scan infrastructure that the `PackInstaller` can wrap.

**Options Considered**:

1. **All 13 pack types at once** -- Migrate every pack seeder to the new pipeline in a single phase. Every pack type gets a `PackInstaller` implementation, every content table gets `source_pack_install_id` and `content_hash` columns, every seeder is refactored.
   - Pros:
     - Single migration. No transition period with mixed old/new paths. `PackReconciliationRunner` can drop all direct seeder calls
     - No legacy compatibility shims needed — `OrgSettings` pack status fields can be removed immediately
     - Complete catalog from day one — the Settings UI shows all 13 pack types
   - Cons:
     - Massive scope. 13 `PackInstaller` implementations, each with type-specific uninstall gate logic. 13 entity modifications (add FK + hash columns). 13 backfill strategies. Estimated 20+ slices, 4-6 weeks
     - Risk concentration. A bug in the pipeline affects all pack types simultaneously. No opportunity to validate the design on a subset before scaling
     - Three pack types (`StandardReportPackSeeder`, `LegalTariffSeeder`, `TrustReportPackSeeder`) do not extend `AbstractPackSeeder`. Migrating them requires significant adapter work
     - Blocks the entire phase on the hardest pack types (compliance checklists with nested items, trust reports with legal-specific semantics)

2. **Document Templates only** -- Migrate only the template pack seeder. Automation templates, clauses, and all other types stay on their existing paths.
   - Pros:
     - Smallest possible scope — one `PackInstaller`, one entity modification, one backfill strategy
     - Document templates have the richest reference structure (generated documents, clone chains), which exercises the uninstall gate thoroughly
     - Existing `document_template.pack_id` column provides clean backfill attribution
   - Cons:
     - Only one pack type does not validate the `PackInstaller` interface's generality. A single implementation might inadvertently couple the interface to template-specific concepts
     - The Settings UI shows only one pack type (document templates), which makes the "Kazi Packs" page feel incomplete
     - Does not exercise the pipeline with a structurally different content type — all validation is on Tiptap JSON content with the same hash/reference/clone checks

3. **Document Templates + Automation Templates** -- Migrate both template-based pack types. The remaining 11 types stay on their existing paths.
   - Pros:
     - Two structurally different content types validate the `PackInstaller` interface's generality. Document templates have Tiptap JSON content, clone tracking (`source_template_id`), and generated document references. Automation rules have trigger_config + conditions JSON, execution tracking (`automation_executions`), and no clone column. The uninstall gate logic differs between the two, exercising different code paths
     - Both extend `AbstractPackSeeder` — the installer implementations can reuse existing seeder logic with minimal adaptation
     - Document template backfill is high-confidence (uses existing `pack_id` column). Automation template backfill is best-effort (timestamp heuristic). This exercises both backfill strategies, providing confidence for future migrations
     - Two pack types make the Settings UI feel substantive without being overwhelming
     - Scope is bounded: 2 `PackInstaller` implementations, 2 entity modifications, 2 backfill strategies. Estimated 7 slices, 1-2 weeks
   - Cons:
     - Still requires a transition period with mixed old/new paths. 11 pack types remain on direct seeders. `PackReconciliationRunner` still calls direct seeders for those types
     - Legacy `OrgSettings` fields must be retained and populated for compatibility
     - The catalog shows only 2 of 13 pack types — users may wonder where the other packs are. Mitigated by the profile filter (most tenants only see a handful of packs anyway) and by the "Available" tab wording ("extend your workspace with pre-built content" — it does not promise completeness)

4. **Document Templates + Clauses** -- Migrate document templates and clause packs. Clauses are closely related to templates (used together in document generation).
   - Pros:
     - Thematically coherent — templates and clauses are used together in document workflows
     - Both have clean backfill paths (both have `pack_id` columns or equivalent tracking)
   - Cons:
     - Clauses have a different reference structure (linked to templates via `template_clauses` join table) that complicates the uninstall gate. Uninstalling a clause pack would need to check whether any clause is referenced by any template — a cross-entity check that is more complex than the within-entity checks for templates and automation rules
     - Clauses and templates are in the same "document" domain. This does not validate structural diversity the way templates + automation rules do (which span different domains with different reference patterns)
     - Clause packs are smaller and less user-visible than automation packs, providing lower demo value in the Settings UI

**Decision**: Option 3 -- Document Templates + Automation Templates.

**Rationale**:

The two pack types were chosen because they provide the strongest validation of the `PackInstaller` interface's generality while keeping scope bounded to a single phase.

**Structural diversity**: Document templates have Tiptap JSON content, SHA-256 content hashing over nested JSON, clone tracking via `source_template_id` (Phase 12), and generated document reference checking via the `generated_documents` table. Automation rules have a completely different content structure (trigger_config + conditions JSONB), no clone tracking column, and execution reference checking via the `automation_executions` table. The `checkUninstallable()` logic for the two types diverges in meaningful ways — if the `PackInstaller` interface can cleanly accommodate both, it will accommodate the remaining types.

**Backfill diversity**: Document templates have a high-confidence backfill path (existing `pack_id VARCHAR(100)` column provides direct attribution). Automation rules have a best-effort backfill path (timestamp heuristic from `OrgSettings.automationPackStatus`). Exercising both strategies in v1 provides confidence for future migrations, which may face similar attribution challenges (e.g., rate cards have no pack attribution column).

**Demo value**: Both pack types are user-visible and impactful. Document templates appear in the Templates management page. Automation rules appear in the Automations settings page. Installing a template pack immediately gives the tenant new templates to use. Installing an automation pack immediately gives them new workflow rules. This makes the "Kazi Packs" install flow feel tangible in demos and onboarding.

**`AbstractPackSeeder` fit**: Both `TemplatePackSeeder` and `AutomationTemplateSeeder` extend `AbstractPackSeeder<D>` and follow the same classpath-scan/JSON-deserialize/OrgSettings-track pattern. The `PackInstaller` implementations can wrap their existing `applyPack()` logic without duplication.

The all-at-once approach (Option 1) was rejected because of risk concentration and scope. A bug in the pipeline discovered during the first deployment would affect all 13 pack types. By migrating two types first, the pipeline is validated in production before being extended to the remaining types in subsequent phases.

**Consequences**:

- This phase ships with `TemplatePackInstaller` and `AutomationPackInstaller` only
- The `PackType` enum has two values: `DOCUMENT_TEMPLATE` and `AUTOMATION_TEMPLATE`. Future phases extend it
- The remaining 11 pack types (`FieldPackSeeder`, `ClausePackSeeder`, `CompliancePackSeeder`, `RequestPackSeeder`, `RatePackSeeder`, `SchedulePackSeeder`, `ProjectTemplatePackSeeder`, `StandardReportPackSeeder`, `LegalTariffSeeder`, `TrustReportPackSeeder`, `ComplianceTemplatePackSeeder`) keep their current direct-seeder paths
- `PackReconciliationRunner` calls `PackInstallService.internalInstall()` for document template and automation template packs, and direct seeder calls for the other 11 types
- The catalog API returns entries for document templates and automation templates only. As future phases add `PackInstaller` implementations, the catalog automatically includes those types
- `OrgSettings.templatePackStatus` and `automationPackStatus` remain populated as compatibility shims. The other 9 pack status fields are untouched
- Future pack type migration order (suggested, not committed): clauses and compliance checklists next (similar reference structures), then rate cards and schedules (simple entity packs), then standalone seeders last (require the most adaptation)
- Related: [ADR-240](ADR-240-unified-pack-catalog-install-pipeline.md), [ADR-241](ADR-241-add-only-pack-semantics.md), [ADR-242](ADR-242-never-used-uninstall-rule.md)
