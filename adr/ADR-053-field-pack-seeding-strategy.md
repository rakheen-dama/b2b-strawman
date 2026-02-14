# ADR-053: Field Pack Seeding Strategy

**Status**: Accepted

**Context**: Phase 11 introduces "field packs" — bundled sets of field definitions that bootstrap new tenants with useful custom fields (e.g., "Common Customer Fields" includes address, phone, tax number). The seeding strategy must answer three questions: (1) When are packs seeded? (2) Are the seeded definitions shared or copied? (3) How are pack updates applied to existing tenants?

The key tension is between **consistency** (all tenants see the same platform-standard fields) and **customizability** (each tenant can rename, reorder, or deactivate fields without affecting others). The strategy also affects vertical forks, which add domain-specific packs (e.g., "Litigation Fields") as classpath resources.

**Options Considered**:

1. **Per-tenant copies seeded at provisioning** — During tenant provisioning, read all pack JSON files from the classpath and create tenant-scoped `FieldDefinition` and `FieldGroup` records. Each tenant gets independent copies that they can modify freely. Pack origin is tracked via `pack_id` and `pack_field_key` columns on the field definition.
   - Pros:
     - Full tenant independence: renaming, reordering, or deactivating a field affects only that tenant.
     - No cross-tenant data dependencies — each tenant's field definitions are self-contained in their schema.
     - Works identically for Pro (dedicated schema) and Starter (shared schema with `tenant_id` filter) tenants.
     - Pack identity tracking (`pack_id`, `pack_field_key`) enables future update detection without coupling to shared records.
     - Vertical forks simply add JSON files to `classpath:field-packs/` — the seeder discovers and applies them automatically.
   - Cons:
     - Data duplication: if 1,000 tenants each have 20 pack-origin fields, that's 20,000 field definition rows (vs. 20 shared rows).
     - Pack updates require tenant-by-tenant application — cannot update a shared definition to affect all tenants at once.
     - No guarantee of consistency across tenants (by design — this is also a pro).

2. **Global shared definitions (read-only) + tenant overrides** — Store pack field definitions in a global `field_packs` table (not tenant-scoped). Tenants reference these definitions by ID. When a tenant customizes a field, a tenant-scoped "override" record is created that shadows the global definition.
   - Pros:
     - Single source of truth for platform-standard fields — updating a global definition immediately affects all tenants that haven't overridden it.
     - Less data duplication (20 shared rows + override rows only for customized fields).
   - Cons:
     - Cross-schema queries: tenant queries must join against the global schema for pack-origin fields. This breaks the isolation model — Hibernate `@Filter` on tenant-scoped tables does not apply to global tables.
     - The override/shadow mechanism is complex: when loading fields for an entity type, the query must COALESCE tenant overrides with global defaults, handle deleted overrides (tenant deactivated a field but the global definition still exists), and merge lists.
     - Starter tenants (shared schema) would need to distinguish between "this field is from the global table" and "this field is a tenant override" in the same query context.
     - Vertical forks that add domain packs would need to insert into the global schema — a migration step that doesn't fit the "just add a JSON file" model.
     - Global definition deletion cascades unpredictably — what happens to tenant values that reference a deleted global field?

3. **Lazy seeding on first access** — No seeding at provisioning time. When a tenant first accesses the custom fields settings page, the system detects that no pack-origin fields exist and offers to apply packs at that point. Packs are presented as "templates" the admin can choose to adopt.
   - Pros:
     - No provisioning overhead — new tenants start fast.
     - Explicit admin consent: tenants only get fields they choose.
     - Reduces data for tenants that never use custom fields.
   - Cons:
     - First-access latency: the initial settings page load triggers a seeding operation (pack reading + record creation), which may be slow.
     - All tenants start with zero custom fields — the "out of the box" experience is empty. Entity detail views show no custom fields section until an admin manually adopts a pack.
     - The settings page must handle the "no packs applied" state with a discovery/adoption UI, which is additional frontend complexity.
     - Vertical forks (e.g., legal SaaS) expect domain-specific fields to be available immediately — requiring an admin to manually adopt "Litigation Fields" on first use defeats the purpose of a vertical fork.

**Decision**: Per-tenant copies seeded at provisioning (Option 1).

**Rationale**: Per-tenant copies align with the existing tenant isolation model. Every tenant-scoped entity in DocTeams is fully independent — there are no cross-tenant shared records for projects, customers, tasks, or any other entity. Introducing a global shared table for field definitions would break this invariant and create cross-schema query complexity that the Hibernate `@Filter` and RLS infrastructure was not designed for.

The data duplication concern (Option 1's main con) is negligible at the expected scale. A pack with 8 fields produces 8 `FieldDefinition` rows + 1 `FieldGroup` row + 8 `FieldGroupMember` rows = 17 rows per tenant. Even at 10,000 tenants with 3 packs each, this is ~510,000 rows — trivial for PostgreSQL.

Per-tenant copies also make pack updates safe. The `pack_id` and `pack_field_key` columns track which definitions originated from which pack. A future "apply pack update" feature can compare the pack's current field list against the tenant's existing pack-origin fields (by `pack_field_key`), add new fields, and skip fields that the tenant has modified. This is a simple set difference operation with no risk of clobbering customizations.

Lazy seeding (Option 3) was rejected primarily because vertical forks need domain fields available immediately. A legal SaaS deployment should provision tenants with "Litigation Fields" and "Conveyancing Fields" pre-loaded — requiring admin action undermines the fork's value proposition.

**Consequences**:
- `FieldPackSeeder` service reads `classpath:field-packs/*.json` and creates per-tenant records during provisioning.
- Each `FieldDefinition` from a pack has `pack_id` (e.g., `"common-customer"`) and `pack_field_key` (e.g., `"address_line1"`) set.
- `OrgSettings.fieldPackStatus` (JSONB) tracks applied packs with version: `[{ "packId": "common-customer", "version": 1, "appliedAt": "2026-..." }]`.
- Vertical forks add domain packs by placing JSON files in `src/main/resources/field-packs/` — no code changes to the seeding service.
- Platform-shipped packs (v1): `common-customer.json`, `common-project.json`.
- Future pack update feature uses `pack_field_key` comparison to add new fields without overwriting tenant customizations.
- Provisioning time increases slightly (read JSON files + insert records), but this is a one-time cost per tenant and takes milliseconds.
