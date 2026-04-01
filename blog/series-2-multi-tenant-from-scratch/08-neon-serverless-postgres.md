# Neon Serverless Postgres for Multi-Tenant SaaS: What Works and What Doesn't

*Part 8 of "Multi-Tenant from Scratch" — a series on building production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7.*

---

Choosing a database provider for a multi-tenant SaaS feels like it should be simple. It's PostgreSQL. Run it somewhere. Connect to it.

In practice, schema-per-tenant adds requirements that eliminate most managed Postgres options. You need DDL support for `CREATE SCHEMA`. You need connection pooling that works with `SET search_path`. You need the ability to run Flyway migrations through a direct (non-pooled) connection while serving app traffic through a pooled one. You need predictable pricing when schema count grows.

I chose Neon. Here's what works, what doesn't, and what I'd do differently.

## Why Neon

**Scale to zero.** For a B2B SaaS that's pre-revenue, paying for an always-on RDS instance is wasteful. Neon's compute scales to zero after 5 minutes of inactivity and spins up in ~500ms on the next request. During development, this saves real money — the database isn't running while I'm writing architecture docs or blog posts.

**Branching.** Neon can create database branches (copy-on-write snapshots) for preview environments, PR testing, and migration validation. I haven't used this extensively yet, but the capability exists.

**Standard PostgreSQL.** Neon is wire-compatible with PostgreSQL. No proprietary extensions, no SQL dialect differences, no driver changes. The `@neondatabase/serverless` package is a convenience, not a requirement — you can use any Postgres driver.

**PgBouncer built in.** Neon includes PgBouncer for connection pooling. You get a pooled connection string and a direct connection string, both pointing to the same database.

## The Dual-Connection Architecture

This is the critical setup for schema-per-tenant:

```yaml
spring:
  datasource:
    app:
      # Pooled connection (through PgBouncer)
      jdbc-url: jdbc:postgresql://ep-xxx.us-east-2.aws.neon.tech/app?sslmode=require
      # Connection string uses the -pooler suffix for PgBouncer
      maximum-pool-size: 10
      connection-timeout: 10000
      connection-init-sql: SET search_path TO public
    migration:
      # Direct connection (bypasses PgBouncer)
      jdbc-url: jdbc:postgresql://ep-xxx.us-east-2.aws.neon.tech/app?sslmode=require
      # Connection string without -pooler suffix
      maximum-pool-size: 2
```

**Why two connections?**

PgBouncer in transaction mode (which Neon uses) multiplexes connections. When a transaction completes, the physical PostgreSQL connection returns to the pool and may be assigned to a different client. Session-level state — including `SET search_path` — doesn't persist between transactions.

For application traffic, this is fine. `SchemaMultiTenantConnectionProvider` sets `search_path` on every connection checkout and resets it on release:

```java
@Override
public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection connection = getAnyConnection();
    try {
        setSearchPath(connection, tenantIdentifier);
    } catch (SQLException e) {
        releaseAnyConnection(connection);
        throw e;
    }
    return connection;
}

private void setSearchPath(Connection connection, String schema) throws SQLException {
    try (var stmt = connection.createStatement()) {
        stmt.execute("SET search_path TO " + sanitizeSchema(schema));
    }
}
```

Each `@Transactional` service method gets a connection with the correct `search_path`. After the method, the connection is released, `search_path` is reset, and PgBouncer can reuse the physical connection.

For **migrations**, however, PgBouncer doesn't work. `CREATE SCHEMA`, `CREATE TABLE`, and `ALTER TABLE` are DDL statements that need session-level state. Flyway also needs to read and write to `flyway_schema_history`, which requires a consistent connection for the duration of the migration run.

The migration datasource connects directly to Neon (bypassing PgBouncer), has a small pool (2 connections), and is used only by `FlywayConfig` and `TenantProvisioningService`.

## HikariCP Tuning

Neon's serverless architecture has quirks that affect connection pooling:

```yaml
spring:
  datasource:
    app:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 10000       # 10s — Neon cold start can take 500ms-2s
      idle-timeout: 300000            # 5 min — match Neon's idle timeout
      max-lifetime: 1740000           # 29 min — under Neon's 30 min limit
      validation-timeout: 5000
      connection-test-query: SELECT 1
      connection-init-sql: SET search_path TO public
```

**`max-lifetime: 29 min`** is critical. Neon terminates idle connections after 30 minutes. If HikariCP keeps a connection beyond this, the next use gets a stale connection error. Setting `max-lifetime` to 29 minutes ensures HikariCP refreshes connections before Neon kills them.

**`connection-init-sql: SET search_path TO public`** ensures fresh connections from the pool start with a known `search_path`. Without this, a connection recycled from a previous request might still have another tenant's `search_path` — though `SchemaMultiTenantConnectionProvider` sets it explicitly, the `init-sql` is belt-and-suspenders.

**`minimum-idle: 2`** keeps a couple of warm connections ready. Neon's cold start is fast (~500ms) but non-zero. For the first request after a period of inactivity, the minimum idle connections absorb the latency.

## What Works Well

**`SET search_path` through PgBouncer.** In transaction mode, PgBouncer forwards `SET search_path` to the underlying connection for the duration of the transaction. Since `SchemaMultiTenantConnectionProvider` sets and resets on every checkout/release, this aligns perfectly with PgBouncer's transaction-scoped lifecycle.

**Scale-to-zero economics.** During development, Neon charges only for active compute time. A database that runs 4 hours a day costs a fraction of an always-on RDS instance. In production with real traffic, the scaling is automatic — compute scales up under load and back down during quiet hours.

**Pg_catalog handles many schemas.** I've tested with 100+ tenant schemas (100 × 30 tables = 3,000 tables). PostgreSQL's catalog system handles this without measurable query planning overhead. `pg_dump` takes longer (it has to enumerate all schemas), but that's a backup concern, not a runtime concern.

**Standard PostgreSQL features.** All the PostgreSQL features I rely on work without modification: UUID generation (`gen_random_uuid()`), JSONB columns, partial indexes, `IF NOT EXISTS` DDL, `ON CONFLICT DO NOTHING`, triggers, and `SET search_path`. Neon is PostgreSQL, not a PostgreSQL-compatible database.

## The Gotchas

**Cold start on first request.** After scale-to-zero, the first request triggers a ~500ms-2s cold start. For a B2B product where users open the app in the morning, this manifests as a slightly slow first page load. Mitigation: the frontend shows a loading state, and subsequent requests are fast.

**Connection limits.** Neon's free tier allows 100 concurrent connections. The paid tier is higher but still finite. With `maximum-pool-size: 10` and PgBouncer multiplexing, this supports ~50-100 concurrent users comfortably. If you're serving thousands of concurrent requests, you need to carefully tune the pool.

**Migration datasource bypasses PgBouncer.** Direct connections count against Neon's connection limit and don't benefit from pooling. With `maximum-pool-size: 2` on the migration datasource, this is minimal. But during startup migration runs (iterating over all tenant schemas), those 2 connections are busy for the duration. Don't overlap startup with heavy app traffic.

**No `LISTEN/NOTIFY` through PgBouncer.** If you're using PostgreSQL's pub/sub for real-time notifications (I'm not — I use Spring's `ApplicationEventPublisher`), PgBouncer in transaction mode drops `LISTEN`/`NOTIFY` messages. Use the direct connection for pub/sub, or use a dedicated notification system.

## What I'd Do Differently

**Start with Neon from day one.** I developed locally against Docker Postgres and deployed to Neon. The transition was smooth because Neon is standard PostgreSQL, but I wish I'd used Neon's branching feature earlier for testing migration safety.

**Profile the connection pool earlier.** I spent too long with default HikariCP settings. The `max-lifetime` mismatch with Neon's 30-minute timeout caused intermittent "connection refused" errors in production that were hard to diagnose. Set `max-lifetime` to `(provider_timeout - 1_minute)` from the start.

**Monitor schema count.** At 83 entities × 100 tenants = 8,300 tables, `pg_dump` takes ~30 seconds. At 1,000 tenants, it would take ~5 minutes. If I were targeting a higher-volume market, I'd set up monitoring for schema count and plan migration to a pool-per-tenant or shard-based approach before hitting 500 schemas.

## The Recommendation

For B2B SaaS targeting professional services firms (accounting, law, consulting):
- **Tens to low hundreds of tenants**: Neon is an excellent fit. Scale-to-zero saves money pre-revenue, PgBouncer handles connection pooling, and `SET search_path` works cleanly through the pooler.
- **Hundreds to low thousands of tenants**: Still works, but monitor Flyway startup time and connection limits. Consider parallel migration runs.
- **Thousands+**: You'll want a different approach — either pool-per-tenant with tenant-aware routing, or a move to shared-table with RLS (accepting the tradeoffs from Post 1).

Neon's sweet spot is exactly where B2B SaaS lives: enough tenants to need multi-tenancy, few enough that schema-per-tenant scales comfortably. And the serverless economics mean you're not paying for capacity you don't use.

---

*This is the final post in "Multi-Tenant from Scratch." The series covered:*

1. *[Schema-Per-Tenant vs. RLS](01-schema-per-tenant-vs-rls.md) — the tradeoff and the reversal*
2. *[ScopedValue Over ThreadLocal](02-scopedvalue-over-threadlocal.md) — modern Java request context*
3. *[Tenant Provisioning](03-tenant-provisioning.md) — webhook to working schema in 2 seconds*
4. *[83 Entities, Zero Tenant Columns](04-zero-tenant-columns.md) — the developer experience win*
5. *[Flyway Dual-Path Migrations](05-flyway-dual-path-migrations.md) — global vs. tenant, the trigger bug*
6. *[Keycloak Identity Layer](06-keycloak-identity-layer.md) — orgs, RBAC, and the mock IDP*
7. *[Customer Lifecycle State Machine](07-customer-lifecycle-state-machine.md) — PROSPECT to ACTIVE*
8. *[Neon Serverless Postgres](08-neon-serverless-postgres.md) — what works and what doesn't*

*I'm extracting this entire architecture into an open-source template: `java-keycloak-multitenant-saas`. [Subscribe](#) for updates when it's ready.*
