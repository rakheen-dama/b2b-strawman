# Schema-Per-Tenant in 2026: Why I Chose It Over RLS (And Then Removed RLS)

*This is Part 1 of "Multi-Tenant from Scratch" — a series on building production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7.*

---

Every multi-tenant SaaS architect faces the same question on day one: how do you isolate tenant data?

The options haven't changed in a decade. Database-per-tenant (expensive, hard to manage). Shared tables with a `tenant_id` column (cheap, risky). Row-Level Security (elegant on paper, surprising in practice). Schema-per-tenant (the middle ground nobody talks about).

I tried two of these. Built production code for both. Then ripped one out entirely. Here's what I learned.

## The Three Strategies

Let me be precise about what each approach actually means in PostgreSQL:

**Shared tables + `tenant_id` column.** Every table has a `tenant_id` column. Every query includes `WHERE tenant_id = ?`. Every developer on your team must remember this, every ORM mapping must enforce it, and every code review must verify it. One missed WHERE clause and you've got a data breach.

**Row-Level Security (RLS).** PostgreSQL enforces isolation at the database level. You create a policy: `CREATE POLICY tenant_isolation ON projects USING (tenant_id = current_setting('app.current_tenant'))`. Set a session variable before each request, and the database filters rows automatically. Sounds perfect — the database does the work, application code can't accidentally bypass it.

**Schema-per-tenant.** Each tenant gets their own PostgreSQL schema: `tenant_a1b2c3d4e5f6`. Tables exist only within that schema. When a request comes in, you `SET search_path TO tenant_abc123` and every query automatically hits the right schema. No `tenant_id` columns. No RLS policies. The isolation is structural, not policy-based.

## Phase 2: The Tiered Experiment

In the early phases of DocTeams, I designed a tiered model:

- **Starter tenants**: Shared schema with RLS. Cheap to provision, minimal resource overhead.
- **Pro tenants**: Dedicated schemas. Full isolation, better performance.

The idea was elegant. Small firms get a free/cheap tier that's easy to scale. As they grow, they upgrade to a dedicated schema, and we migrate their data.

I built the whole thing. Here's what it required:

**A `TenantAware` interface** on every entity:

```java
// Every entity had to implement this
public interface TenantAware {
    String getTenantId();
    void setTenantId(String tenantId);
}
```

**Hibernate `@Filter` annotations** on all 27 entities:

```java
@Entity
@Table(name = "projects")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Project implements TenantAware {

    @Column(name = "tenant_id")
    private String tenantId;

    // ... 15 other fields
}
```

**A custom `TenantFilterTransactionManager`** that enabled the Hibernate filter before every transaction:

```java
// Custom JpaTransactionManager that enables tenant filtering
// Had to use TransactionSynchronizationManager.getResource() to get the Session
// because adding a SessionFactory field SHADOWS the parent's internal field
// (learned that the hard way — see Post 1 of Series 1)
```

**Custom repository methods** because `JpaRepository.findById()` bypasses Hibernate `@Filter`:

```java
// Standard findById() uses Session.get() which ignores @Filter
// Had to add findOneById() to all 22 repositories
@Query("SELECT p FROM Project p WHERE p.id = :id")
Optional<Project> findOneById(@Param("id") UUID id);
```

**RLS policies** in the shared-schema migrations:

```sql
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON projects
    USING (tenant_id = current_setting('app.current_tenant'));
```

**A `TenantUpgradeService`** for migrating from shared to dedicated schema.

It worked. Tests passed. Reviews approved. Ship it.

## The Problems

They were slow and cumulative.

### Problem 1: Two Code Paths Everywhere

Every service method had implicit branching. Is this tenant on shared or dedicated schema? The answer affected:

- How the Hibernate session was configured
- Whether `@Filter` needed to be enabled
- Whether `tenant_id` was set on new entities
- How migrations ran (shared schema gets RLS policies, dedicated schemas don't)
- How tests were structured (two sets of assertions)

The code didn't have explicit `if (isShared)` checks — that was the whole point of the abstraction. But the *complexity* leaked through. Every time I added a new entity, I had to remember: implement `TenantAware`, add `@FilterDef` and `@Filter`, add `tenant_id` column, add `findOneById()`, add RLS policy to shared migrations.

For 27 entities, that's 27 `@Filter` annotations, 22 custom `findOneById()` queries, 27 `tenant_id` columns, and an RLS policy for every table. Each one is a place where a mistake creates a data leak.

### Problem 2: `findById()` Bypass

This was the most dangerous one. JPA's `findById()` uses Hibernate's `Session.get()`, which loads an entity by primary key *without applying `@Filter`*. In a shared schema, this means `projectRepository.findById(someUUID)` could return a project from *any* tenant.

We caught this in code review for most repositories and added `findOneById()` (which uses JPQL, where `@Filter` applies). But the surface area was huge — any developer (or AI agent) calling `findById()` instead of `findOneById()` created a tenant isolation vulnerability.

This is the fundamental problem with opt-in isolation: you have to remember to opt in everywhere. Every new query, every new repository method, every new test. Miss one and you've got a breach.

### Problem 3: Native Query Blindness

Hibernate `@Filter` only applies to JPQL queries, not native SQL. For the profitability reports (Phase 8), I needed native queries with aggregations and joins. These bypassed `@Filter` entirely.

The workaround was that `TenantFilterTransactionManager` also set a PostgreSQL session variable via `SELECT set_config('app.current_tenant', ?, false)`, so RLS policies would catch native queries. But this meant isolation depended on *both* Hibernate `@Filter` (for JPQL) and PostgreSQL RLS (for native SQL). Two mechanisms, both of which had to work correctly, with different failure modes.

### Problem 4: The Upgrade Path Was Scary

Migrating a tenant from shared schema to dedicated schema meant:
1. Lock the tenant (no writes)
2. Copy all rows with `tenant_id = X` to a new schema
3. Delete the copied rows from the shared schema
4. Update `org_schema_mapping` to point to the new schema
5. Unlock

In production, with live users, this is a data migration that must be atomic. If it fails mid-way, you've got data in two places. I built `TenantUpgradeService` for this, but never ran it in production. The risk wasn't worth it.

## Phase 13: The Reversal

Eleven phases later, I ripped it all out. ADR-064 explains the decision:

> All tenants get dedicated `tenant_<hash>` schemas. No shared schema. No RLS.

The delete was cathartic:

- **Deleted**: `TenantAware` interface, `TenantAwareEntityListener`, `TenantFilterTransactionManager`, `TenantInfo`, `TenantUpgradeService`, and 7 test files
- **Stripped from 27 entities**: `@FilterDef`, `@Filter`, `tenant_id` columns
- **Removed from 22 repositories**: custom `findOneById()` methods
- **Simplified 17 migrations**: removed RLS policies
- **~500 lines of infrastructure code**: gone

Here's what a typical entity looks like now:

```java
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status;

    // ... no tenant_id, no @Filter, no TenantAware
}
```

And repository usage:

```java
// This just works. Schema boundary handles isolation.
var project = projectRepository.findById(projectId)
    .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
```

Standard `findById()`. No custom query. No filter to forget. The isolation comes from `SET search_path TO tenant_abc123` at the connection level — the query physically cannot access another tenant's tables because they exist in a different schema.

## When Schema-Per-Tenant Breaks Down

I'm not going to pretend it's perfect. Here's where it gets hard:

**Cross-tenant reporting.** A platform admin needs to see aggregate stats across all tenants. With a shared schema, this is `SELECT count(*) FROM projects` (with RLS disabled for the admin). With schema-per-tenant, you need to iterate over schemas or maintain a separate reporting materialized view. I handle this with a global `organizations` table and per-request schema queries when needed. It's not elegant, but it's rare.

**Schema count at scale.** A thousand tenants means a thousand schemas, each with 30+ tables. PostgreSQL handles this fine — `pg_catalog` is designed for it. But `pg_dump` gets slower, migration startup scales linearly, and monitoring tools sometimes choke on the schema count.

**Provisioning overhead.** Creating a schema, running 28 migrations, and seeding data packs takes 2-3 seconds. For a shared schema, it's an INSERT into `organizations` and you're done. For my target market (B2B firms onboarding a few tenants per week), 3 seconds is fine. For a self-service product with thousands of sign-ups per day, you'd need a provisioning queue.

**Connection pool efficiency.** Each connection checkout does a `SET search_path`. With a shared schema and RLS, you'd do a `SET app.current_tenant` instead — roughly the same cost. In practice, HikariCP handles this fine with 10-20 connections.

## The Decision Framework

After living with both approaches, here's how I'd decide:

**Choose schema-per-tenant when:**
- Tenant isolation is a hard requirement (compliance, regulated industries, enterprise customers)
- You have < 10,000 tenants
- You want zero-boilerplate entities (no `tenant_id`, no filters, just plain JPA)
- You want `findById()` to be safe by default
- Your team includes junior developers or AI agents that might forget `WHERE tenant_id = ?`

**Choose RLS when:**
- You have > 100,000 tenants and need shared-table efficiency
- Cross-tenant analytics are a core feature
- You trust your team to never bypass the session variable
- You're comfortable with the `findById()` bypass risk

**Choose both when:**
- Don't. I tried. The complexity isn't worth it for any target market.

The simplest system that provides the isolation guarantee you need is the right answer. For B2B SaaS targeting professional services firms, schema-per-tenant is that answer.

---

*Next in this series: [ScopedValue Over ThreadLocal: Preparing for Virtual Threads in Spring Boot 4](02-scopedvalue-over-threadlocal.md)*

*If you're building multi-tenant SaaS, I'm extracting this entire architecture into a reusable open-source template. [Subscribe](#) for updates.*
