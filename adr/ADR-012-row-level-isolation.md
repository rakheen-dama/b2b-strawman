# ADR-012: Row-Level Isolation for Starter Tier

**Status**: Accepted

**Note**: Fully superseded by [ADR-064](ADR-064-dedicated-schema-only.md) — all tenants use dedicated schemas, no RLS needed.

**Context**: Starter-tier organizations share the `tenant_shared` schema. Data isolation between orgs within this schema must be enforced reliably. A failure in isolation would expose one organization's data to another — a critical security breach in a multi-tenant system. The isolation mechanism must work with the existing Hibernate/JPA data access layer (Spring Data repositories, JPQL, Criteria API) without requiring every query to be manually rewritten.

**Options Considered**:

1. **Application-level filtering only (Hibernate `@Filter`)** — Use Hibernate's `@FilterDef`/`@Filter` annotations to automatically append `WHERE tenant_id = :tenantId` to all queries against shared-schema entities.
   - Pros: Integrates transparently with Spring Data repositories, JPQL, and Criteria API queries; no database-level configuration needed; easy to enable/disable per-tenant.
   - Cons: Does not protect against raw SQL queries, native queries, or `JdbcTemplate` usage; a developer forgetting to enable the filter = data leak; no database-level safety net.

2. **Postgres Row-Level Security (RLS) only** — Enable RLS on `tenant_shared` tables with policies based on a PostgreSQL session variable (`app.current_tenant`).
   - Pros: Database-level enforcement — even raw SQL queries are filtered; defense-in-depth independent of application code.
   - Cons: Requires setting `app.current_tenant` on every connection checkout for shared-schema tenants; RLS adds overhead to query planning; policies must be carefully managed to avoid applying to dedicated schemas; does not integrate with Hibernate query planning (Hibernate doesn't know about RLS, so query plans may be suboptimal).

3. **Both: Hibernate `@Filter` (primary) + Postgres RLS (defense-in-depth)** — Hibernate filter handles all JPA/JPQL/Criteria queries; RLS catches anything that bypasses Hibernate.
   - Pros: Belt-and-suspenders approach; application layer handles 99% of queries efficiently; database layer catches the remaining edge cases (native SQL, `JdbcTemplate`, future code paths); minimal overhead for the RLS layer since most queries are already filtered by Hibernate.
   - Cons: Two systems to maintain; slight complexity in connection provider (must set `app.current_tenant` for shared schema connections).

**Decision**: Dual-layer isolation — Hibernate `@Filter` as the primary mechanism, Postgres RLS as defense-in-depth.

**Rationale**: In a multi-tenant system, data isolation is the highest-priority security property. A single-layer approach creates a single point of failure: if the Hibernate filter is accidentally disabled (e.g., missing aspect, new code path, native query), data leaks silently. The dual-layer approach ensures that even if the application layer fails, the database layer prevents cross-tenant data access.

The implementation is cleanly separated:
- **Hibernate `@Filter`**: All tenant entities annotated with `@FilterDef(name = "tenantFilter", ...)` and `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")`. A `SharedTenantFilterAspect` (Spring AOP) enables the filter at transaction entry when `RequestScopes.TENANT_ID` equals `"tenant_shared"`. The filter parameter is set to `RequestScopes.ORG_ID.get()` (the Clerk org ID for the current request).
- **Postgres RLS**: Enabled only on `tenant_shared` schema tables. Policies use `current_setting('app.current_tenant', true)` — the second parameter (`true`) means "return NULL if not set" rather than throwing an error, so dedicated-schema connections (which don't set this variable) are unaffected. The `SchemaMultiTenantConnectionProvider` sets `SET app.current_tenant = '<clerkOrgId>'` when `schema = "tenant_shared"`.

RLS is not enabled on dedicated tenant schemas because:
- The `tenant_id` column is NULL in dedicated schemas (unused).
- Schema isolation already prevents cross-tenant access.
- Applying RLS policies to schemas where `tenant_id` is NULL would require complex policy exclusions.

**Consequences**:
- All 4 tenant entities (`Project`, `Document`, `Member`, `ProjectMember`) annotated with `@FilterDef` and `@Filter`.
- `SharedTenantFilterAspect` component created in `multitenancy` package.
- `RequestScopes` gains `ORG_ID` ScopedValue (Clerk org ID for row-level filtering).
- `SchemaMultiTenantConnectionProvider.getConnection()` sets `app.current_tenant` when schema is `"tenant_shared"`.
- RLS policies in `V7__add_tenant_id_for_shared.sql` migration (applied to all schemas but only effective on `tenant_shared`).
- Native SQL queries against `tenant_shared` are protected by RLS even without Hibernate filter.
- Integration tests must verify: two Starter orgs in same schema cannot see each other's data.
