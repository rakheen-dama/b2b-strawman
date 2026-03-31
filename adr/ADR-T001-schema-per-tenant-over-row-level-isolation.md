# ADR-T001: Schema-Per-Tenant Over Row-Level Isolation

> Template ADR for `java-keycloak-multitenant-saas` starter template

**Status:** Accepted

**Context:** Multitenant SaaS applications must isolate tenant data. The three common strategies are: row-level isolation (shared tables with `tenant_id` columns), schema-per-tenant (dedicated schema per tenant, shared database), and database-per-tenant (dedicated database per tenant). The choice affects security posture, operational complexity, query design, and compliance certification.

**Options Considered:**

1. **Row-level isolation** — Shared tables with a `tenant_id` column and Hibernate `@Filter`/RLS policies
   - Pros: Single migration path, simplest connection pool, lowest per-tenant overhead
   - Cons: Every query must include tenant filter (risk of data leaks if forgotten), proving isolation to auditors requires code-level review, no granular backup/restore per tenant

2. **Schema-per-tenant** — Dedicated PostgreSQL schema per tenant, single database instance
   - Pros: Physical isolation without dedicated databases, no per-query tenant filtering, compliance auditing is trivial (show schema boundary), per-tenant backup/restore possible
   - Cons: DDL migrations run per-schema, schema count grows linearly with tenants, connection pool is shared

3. **Database-per-tenant** — Dedicated PostgreSQL database per tenant
   - Pros: Strongest isolation, per-tenant connection limits, trivial data export/import
   - Cons: Highest operational cost, separate connection pools per tenant, migration orchestration complexity, impractical beyond ~100 tenants

**Decision:** Use schema-per-tenant isolation.

**Rationale:**
1. **Physical isolation without operational overhead:** Each tenant's data is in a separate schema — no `WHERE tenant_id = ?` can be forgotten. Unlike database-per-tenant, all schemas share a single PostgreSQL connection pool.
2. **Compliance simplicity:** Proving data isolation to auditors is trivial — show the schema boundary. Row-level isolation requires proving that every query includes the tenant filter.
3. **Backup/restore granularity:** Individual tenant schemas can be backed up or restored without affecting others.
4. **Migration cost is acceptable:** Running migrations per-schema is automated and parallelizable. For a starter template with 5 tenant tables, this is negligible.
5. **PostgreSQL scalability:** PostgreSQL handles thousands of schemas comfortably. The practical limit is well beyond what a typical B2B SaaS with hundreds of tenants would encounter.

**Consequences:**
- Positive: Strongest isolation without dedicated databases
- Positive: No per-query tenant filtering logic — bugs in query construction cannot leak data
- Positive: New entities need zero multitenancy boilerplate — just standard `@Entity` + `JpaRepository`
- Negative: Schema count grows linearly with tenants (mitigated by PostgreSQL's schema scalability)
- Negative: DDL migrations run per-tenant (automated by Flyway runner in `TenantProvisioningService`)
- Negative: Cross-tenant queries (e.g., platform analytics) require explicit `SET search_path` or public-schema aggregation tables
