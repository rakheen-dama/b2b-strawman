# ADR-053: Field Pack Seeding Strategy

**Status**: Accepted

**Context**: Phase 11 introduces "field packs" — platform-provided bundles of field definitions that organizations can adopt when they provision a tenant. Packs serve two purposes: (1) bootstrap common fields so new orgs don't start with a blank slate, and (2) provide the extensibility seam for vertical forks (e.g., a legal vertical adds a "Litigation Fields" pack).

The design question is *where* pack definitions live at runtime — are they shared global records that all tenants reference, or are they copied into each tenant's schema during provisioning?

Key constraints:
- Field definitions are tenant-scoped (they have `tenant_id` and follow the standard `@Filter`/RLS isolation).
- Organizations must be able to customize any field — rename, change type, add validation, deactivate — without affecting other organizations.
- Platform updates may add new fields to existing packs. These additions should be deployable to existing tenants without overwriting org customizations.
- Vertical forks add domain-specific packs by dropping JSON files on the classpath — no code changes to the core field system.
- Starter-tier orgs (shared schema) and Pro-tier orgs (dedicated schema) must both work.

**Options Considered**:

1. **Per-tenant copy at provisioning** — Pack definitions are JSON files on the classpath. During tenant provisioning, the `FieldPackSeeder` reads all pack files, creates tenant-scoped `FieldDefinition` and `FieldGroup` records in the tenant's schema, and tracks which packs/versions were applied in `OrgSettings.fieldPackStatus`. Each tenant owns its copies and can customize freely.
   - Pros:
     - **Full independence**: Each tenant has its own copies. Renaming "Case Number" to "Matter Reference" in Tenant A has zero impact on Tenant B.
     - **No cross-tenant reads**: Field definitions are tenant-scoped records accessed through the standard `@Filter`/RLS path. No special query pattern needed.
     - **Customization is natural**: Editing a pack-origin field is identical to editing an org-created field — same service, same API, same permissions.
     - **Vertical fork simplicity**: Forks add JSON files to `src/main/resources/field-packs/`. The seeder discovers them automatically. No fork-specific code.
     - **Update-safe**: New fields in a pack update are detected by comparing `pack_field_key` values. Only genuinely new fields are added. Org-modified fields are untouched.
   - Cons:
     - **Data duplication**: If the platform ships 50 field definitions across 5 packs, every tenant gets 50 copies. With 1,000 tenants, that's 50,000 rows. Acceptable at this scale but not elegant.
     - **Update propagation is opt-in**: Adding a new field to a pack doesn't automatically appear in existing tenants. An explicit update step (API call or admin action) is needed.
     - **No global consistency**: There's no way to enforce "all tenants must have field X" — each tenant's copy is independent.

2. **Shared global definitions with tenant overrides** — Pack field definitions live in a global `public.field_pack_definitions` table. Tenants reference these definitions. If a tenant wants to customize a field, a tenant-scoped override record is created. At read time, the service merges global + overrides.
   - Pros:
     - **No duplication**: Global definitions are stored once.
     - **Instant updates**: Adding a field to a global pack is immediately visible to all tenants (no propagation step).
     - **Global consistency**: Platform-required fields can be enforced globally.
   - Cons:
     - **Cross-schema reads**: Tenant queries must join against `public.field_pack_definitions`. This breaks the clean tenant isolation model — every field definition read touches the public schema.
     - **Override merging complexity**: The service must merge global definitions with tenant overrides on every read. Edge cases: what if a tenant deactivates a global field? What if a global field changes type but a tenant has existing values?
     - **Starter-schema complication**: In `tenant_shared`, tenant-scoped overrides need `tenant_id` + `pack_field_key` composite keys. The merge logic must also handle `@Filter` — reading global definitions bypasses the filter, which is architecturally inconsistent.
     - **Migration coupling**: Changing global pack definitions is a global migration (affects all tenants simultaneously). Rollback is complex.
     - **Vertical fork complexity**: Fork-specific packs would need a deployment mechanism for the global table — seed data in a global migration, not just a classpath JSON file.

3. **Lazy materialization** — Global pack definitions on the classpath (JSON files, not database tables). When a tenant first accesses a pack's fields, the system materializes them into tenant-scoped records. No provisioning-time seeding.
   - Pros:
     - **No provisioning overhead**: Tenant creation is fast — no pack seeding step.
     - **On-demand**: Only packs that a tenant actually uses are materialized.
     - **Simple global definition management**: JSON files on classpath, no global database tables.
   - Cons:
     - **First-access latency**: The first time a user opens the custom fields settings, the system materializes pack definitions. This is a surprising delay.
     - **Race conditions**: Two simultaneous requests could both trigger materialization. Requires pessimistic locking or idempotency checks.
     - **Unpredictable state**: Some tenants have materialized packs, others don't. Support and debugging become harder.
     - **No pre-applied groups**: New tenants wouldn't have any field groups until someone visits the settings page.

**Decision**: Per-tenant copy at provisioning (Option 1).

**Rationale**: Per-tenant copying is the simplest approach that satisfies all constraints. The data duplication concern is negligible — 50 field definitions per tenant at ~500 bytes per row is ~25KB per tenant. Even at 10,000 tenants, that's 250MB — trivial for PostgreSQL.

The key advantage is architectural consistency: field definitions are tenant-scoped records, period. No special cross-schema reads, no override merging, no global migration coupling. The existing `@Filter`/RLS model applies without modification. Vertical forks drop a JSON file on the classpath and the seeder picks it up — no database changes, no deployment coordination.

The update propagation concern (new pack fields don't auto-appear in existing tenants) is a feature, not a bug. Auto-propagation would risk disrupting tenant workflows ("why did a new field appear in my form?"). The tracked version in `OrgSettings.fieldPackStatus` provides the infrastructure for a future "updates available" notification, letting admins opt into pack updates.

**Consequences**:
- `FieldPackSeeder` service reads JSON files from `src/main/resources/field-packs/` during tenant provisioning.
- Each `FieldDefinition` from a pack has `pack_id` and `pack_field_key` set — linking it to its origin for update tracking.
- `OrgSettings.fieldPackStatus` (JSONB) stores `[{ packId, version, appliedAt }]` per tenant.
- Platform ships `common-customer.json` and `common-project.json` in v1. Vertical forks add their own JSON files.
- Pack update detection: compare JSON file field keys against existing `pack_field_key` values in the tenant. New keys = new fields to add. Changed definitions are not overwritten.
- Future: admin-facing "Pack Updates" UI that shows available updates and lets admins apply them selectively.
- Duplication is bounded: ~50 rows per tenant at pack-level. Monitor table size if pack count grows significantly.
