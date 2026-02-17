# ADR-064: Dedicated Schema for All Tenants

**Status**: Accepted
**Supersedes**: [ADR-012](ADR-012-row-level-isolation.md), partially supersedes [ADR-015](ADR-015-provisioning-per-tier.md)

**Context**:

ADR-011 introduced a tiered tenancy model where Starter (free) tenants share a single `tenant_shared` schema with row-level isolation, while Pro tenants receive dedicated `tenant_<hash>` schemas. ADR-012 then established a dual-layer isolation strategy for the shared schema: Hibernate `@Filter` as the primary mechanism (appending `WHERE tenant_id = :tenantId` to all JPA queries) with PostgreSQL Row-Level Security as defense-in-depth. ADR-015 codified the provisioning flow where all organizations start as Starter (mapped to `tenant_shared`) and upgrade to dedicated schemas upon Pro subscription. This architecture was sound in theory but imposed a significant and compounding maintenance tax in practice.

The shared-schema path requires every tenant-scoped entity to implement the `TenantAware` interface, carry a nullable `tenant_id` column, and be annotated with `@FilterDef` and `@Filter`. Today this applies to 28 entity classes across 17 packages. Because `JpaRepository.findById()` uses `EntityManager.find()` which bypasses Hibernate `@Filter`, every repository that needs single-entity lookup requires a custom `findOneById()` method using JPQL `@Query` — currently 22 repositories, referenced from 74 call sites across services, controllers, and tests. The infrastructure layer includes `TenantFilterTransactionManager` (custom `JpaTransactionManager` that enables the Hibernate filter and sets `app.current_tenant` via `set_config()` at transaction begin), `TenantAwareEntityListener` (auto-sets `tenant_id` on insert for shared-schema tenants), and the `TenantAware` interface itself. Across 17 Flyway migrations (V7 through V29), every new table includes `tenant_id` column definitions, indexes, and RLS policy statements that exist solely to support the shared-schema path.

Multiple bug categories have originated directly from this dual-path complexity. PR #51 fixed a critical issue where `findById()` bypassed `@Filter`, allowing cross-tenant data access in the shared schema. The OSIV (Open Session in View) interaction was discovered to pin an `EntityManager` to the wrong schema for internal endpoints, requiring a global `spring.jpa.open-in-view: false` setting. RLS session variable races required careful `set_config()` orchestration in `TenantFilterTransactionManager`. A connection provider leak was traced to a failure path after `getAnyConnection()` that did not release the connection. Custom `JpaTransactionManager` field shadowing caused subtle session resolution failures. Each of these bugs required deep investigation and careful fixes — all of them exist exclusively because of the shared-schema path. With no production data and no live tenants, there is no migration burden: this is a clean-slate opportunity to eliminate the entire complexity category.

**Options Considered**:

1. **Keep dual-path (status quo)** — Maintain shared + dedicated schemas with all infrastructure.
   - Pros: No work needed; proven to work after multiple rounds of bug fixes; Starter tenants share database resources (one schema for all free orgs).
   - Cons: Permanent maintenance tax on every new entity (add `TenantAware`, `@FilterDef`, `@Filter`, `tenant_id` column, `findOneById`, RLS policy — 7 touchpoints per entity); ongoing bug surface area from dual-path isolation; 3 infrastructure classes (`TenantFilterTransactionManager`, `TenantAwareEntityListener`, `TenantAware`) that exist solely for this path; every integration test suite needs shared-schema variants to verify filter behavior; cognitive overhead for new contributors understanding two isolation models.

2. **Dedicated schema for all tenants (chosen)** — Every tenant gets `tenant_<hash>` regardless of tier.
   - Pros: Eliminates the entire `findById` bypass bug category; removes 3 infrastructure classes and their associated complexity; removes `tenant_id` from 28 entities and `findOneById` from 22 repositories; removes RLS policies from 17 migrations; vastly simpler mental model (one isolation strategy: schema boundary); new entities need zero shared-schema boilerplate.
   - Cons: Slightly more schemas for Starter tenants — each free org gets its own schema instead of sharing one (minimal PostgreSQL cost; schema creation is ~50ms + Flyway migration time); requires a clean-slate database reset (acceptable pre-launch); Flyway startup migration time scales linearly with total tenant count.

3. **Row-level security only (no Hibernate @Filter)** — Keep `tenant_shared` but use PostgreSQL RLS exclusively, removing Hibernate-level filtering.
   - Pros: Simpler Java code (no `@Filter`/`@FilterDef` annotations, no `TenantFilterTransactionManager`); database enforces isolation uniformly for all query types including native SQL.
   - Cons: Still need `tenant_id` columns on all entities; still need RLS policies in every migration; still need `set_config('app.current_tenant', ...)` on every connection; does not eliminate the dual-path provisioning or the mental model split; Hibernate query plans cannot account for RLS, potentially degrading performance.

4. **Shared schema with discriminator column (standard JPA approach)** — Use Hibernate 6+ `@TenantId` annotation instead of `@Filter`, letting Hibernate handle filtering automatically.
   - Pros: Hibernate handles filtering transparently — no `findOneById` workaround needed; standard JPA multitenancy support.
   - Cons: Still requires `tenant_id` columns on all entities; still dual-path (shared vs. dedicated); Hibernate `@TenantId` has not been tested with the project's `ScopedValue`-based `TenantIdentifierResolver`; still requires the provisioning split and upgrade migration flow; does not eliminate RLS policies or the dual mental model.

**Decision**: Option 2 — dedicated schema for all tenants.

**Rationale**:

The shared-schema path was originally chosen (ADR-012) to minimize resource usage for Starter tenants, based on the assumption that free-tier orgs would be the majority (80-90% of tenants) and that per-schema overhead would be significant. In practice, the cost of a dedicated schema — one Flyway migration run per tenant at provisioning time, one `SET search_path` per database connection — is negligible compared to the engineering cost of maintaining dual-path isolation. PostgreSQL handles thousands of schemas without meaningful catalog bloat, and Flyway migration runs take under 2 seconds per tenant for the current 29-version migration set.

Multiple production-grade bugs have originated exclusively from the shared-schema path, each requiring deep investigation: the `findById()` filter bypass (PR #51), OSIV schema pinning, RLS session variable races, connection provider leaks, and `JpaTransactionManager` field shadowing. The `findOneById` workaround alone is present in 22 repositories and referenced from 74 files. Every new entity added to the system requires 7 additional annotations, fields, and methods for `TenantAware` compliance — a tax that compounds with each phase. Phases 4 through 12 have added 24 new tenant-scoped entities, each carrying this overhead.

With no production data to migrate, this is a clean-slate opportunity that will not recur once the system launches. The `Tier` enum (`STARTER`, `PRO`) remains for billing and feature gating — it simply no longer determines schema topology. All tenants follow the same provisioning path: create `tenant_<hash>` schema, run Flyway migrations, map in `org_schema_mapping`. The upgrade flow (ADR-016) becomes a no-op for schema topology, reducing to a simple tier field update. This decision aligns with the project's core principle of simplicity first: one isolation model, one provisioning path, one mental model.

**Consequences**:

- Positive:
  - Eliminates the entire class of `@Filter` bypass bugs (`findById`, native queries, `JdbcTemplate`)
  - Removes `TenantFilterTransactionManager`, `TenantAwareEntityListener`, and `TenantAware` interface — 3 infrastructure classes totaling ~200 lines
  - Removes `@FilterDef`, `@Filter`, and `tenant_id` field from 28 entity classes
  - Removes `findOneById()` JPQL workaround from 22 repositories and ~74 dependent call sites
  - Removes RLS policies (`CREATE POLICY`, `ALTER TABLE ENABLE ROW LEVEL SECURITY`, `set_config`) from 17 migrations
  - New entities require zero shared-schema boilerplate — just the entity class and standard repository

- Negative:
  - Starter tenants now each get a dedicated schema, increasing total schema count (bounded by tenant count; PostgreSQL handles this well)
  - Flyway startup migration runner iterates over more schemas (mitigated by async provisioning — migrations run at tenant creation, not application startup)
  - Requires a clean-slate database reset (no production data exists, so this is a one-time non-issue)

- Neutral:
  - `Tier` enum and tier-based feature gating remain unchanged — billing, member limits, and plan enforcement are unaffected
  - `org_schema_mapping` table structure is unchanged — all rows now map to `tenant_<hash>` patterns instead of some mapping to `tenant_shared`
  - `SchemaMultiTenantConnectionProvider` simplifies (removes `set_config` branch for shared schema) but retains `SET search_path` for all connections
