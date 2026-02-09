# ADR-011: Tier-Dependent Tenancy Model

**Status**: Accepted

**Context**: Phase 2 introduces two subscription tiers with different isolation requirements. The Starter (free) tier must be cost-effective for small organizations (1-2 members, minimal data), while the Pro tier must provide the same strong schema-level isolation already implemented in Phase 1. The challenge is supporting both models within the same application without duplicating the data access layer.

**Options Considered**:

1. **Schema-per-tenant for all tiers** — Every organization (including free Starter orgs) gets its own dedicated schema.
   - Pros: Uniform isolation model; no code changes to data access layer; simplest mental model.
   - Cons: Hundreds or thousands of free-tier schemas create operational overhead (Flyway startup migration time grows linearly with tenant count; `pg_catalog` bloat; monitoring complexity). Neon Postgres has practical limits on schema count. Disproportionate cost: free-tier orgs with 1-2 users and near-zero data each get full schema infrastructure.

2. **Shared schema for all tiers** — All organizations use a single schema with row-level filtering by `tenant_id`.
   - Pros: Simplest to operate; single set of tables to monitor and index.
   - Cons: Weaker isolation for paying customers (Pro orgs); a bug in row-level filtering could leak data across organizations; noisy-neighbor risk on shared indexes; loses the Phase 1 architecture's primary differentiator (schema isolation).

3. **Tiered model: shared schema for Starter, dedicated schema for Pro** — Starter orgs share `tenant_shared` with row-level isolation; Pro orgs get `tenant_<hash>` schemas as in Phase 1.
   - Pros: Cost-effective for free tier (one schema for all Starter orgs); strong isolation for paying customers; natural upgrade path (move data from shared to dedicated on plan upgrade); `org_schema_mapping` table naturally supports this (Starter maps to `"tenant_shared"`, Pro maps to `"tenant_<hash>"`).
   - Cons: Two code paths for data access (Hibernate @Filter for shared, schema isolation for dedicated); shared schema requires row-level filtering and defense-in-depth; upgrade flow involves data migration.

**Decision**: Tiered model — Starter uses shared schema (`tenant_shared`) with row-level isolation; Pro uses the existing schema-per-tenant model.

**Rationale**: Free-tier organizations are expected to be the majority of tenants (typical freemium funnel: 80-90% free, 10-20% paid). Creating dedicated schemas for each would make Flyway startup migration time and Postgres schema catalog grow proportionally. The shared schema approach bounds the operational cost of free-tier tenants to a single schema regardless of count. The `org_schema_mapping` table already maps `clerk_org_id` → `schema_name`; for Starter orgs, this simply maps to `"tenant_shared"` instead of `"tenant_<hash>"`. The `TenantFilter` resolves the schema identically for both tiers — the branching happens downstream (Hibernate @Filter for shared, schema isolation for dedicated).

The same entity classes (`Project`, `Document`, `Member`, `ProjectMember`) are used for both tiers. A `tenant_id` column is added to all tenant-schema tables (including dedicated schemas, where it remains NULL and unused). This avoids entity class duplication and allows Hibernate mappings to work uniformly. The `@Filter` is only activated when the resolved schema is `"tenant_shared"`.

**Consequences**:
- `tenant_shared` schema is pre-created at application startup (not per-org).
- All tenant-schema entities gain a nullable `tenant_id` column (V7 migration applied to all schemas).
- Hibernate `@FilterDef`/`@Filter` annotations added to all tenant entities.
- `SharedTenantFilterAspect` activates the filter for shared-schema transactions.
- `TenantAwareEntityListener` auto-sets `tenant_id` on insert for shared-schema tenants.
- Pro schemas have an unused nullable `tenant_id` column (negligible overhead).
- Upgrade from Starter to Pro involves data migration (see ADR-016).
- Future third tier (e.g., Enterprise with database-per-tenant) would follow the same mapping pattern.
