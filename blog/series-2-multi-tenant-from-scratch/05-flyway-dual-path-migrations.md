# Flyway Dual-Path Migrations: Global vs. Tenant Schemas

*Part 5 of "Multi-Tenant from Scratch" — a series on building production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7.*

---

In a single-tenant application, database migrations are simple: a directory of SQL files, Flyway runs them in order, done.

In a multi-tenant application with schema-per-tenant, migrations get interesting. You need two kinds:

1. **Global migrations** that run once in the `public` schema — org registry, schema mappings, webhooks, subscriptions.
2. **Tenant migrations** that run in *every* tenant schema — all the business entities.

And you need them to run at different times: global at startup, tenant at provisioning *and* at startup (for existing tenants getting new migrations).

Here's how I set this up with Flyway, and the gotchas I found along the way.

## The Directory Structure

```
src/main/resources/db/migration/
├── global/                              # public schema, runs at startup
│   ├── V1__create_org_schema_mapping.sql
│   ├── V2__create_organizations.sql
│   ├── V3__create_processed_webhooks.sql
│   └── V4__create_subscriptions.sql
└── tenant/                              # per-tenant schema, runs at provisioning + startup
    ├── V1__create_members.sql
    ├── V2__create_org_roles.sql
    ├── V3__create_projects.sql
    ├── V4__create_documents.sql
    ├── V5__create_project_members.sql
    ├── V6__create_invitations.sql
    ├── V7__create_customers.sql
    ├── ...
    └── V28__create_generated_documents.sql
```

Two directories, two version sequences. Global migrations are V1–V4 in the `global/` path. Tenant migrations are V1–V28 in the `tenant/` path. The version numbers are independent — global V1 and tenant V1 are completely unrelated.

Each tenant schema gets its own `flyway_schema_history` table, tracking which tenant migrations have been applied to that schema. This means migration state is per-tenant — you can add a migration, deploy, and have it roll out incrementally as tenants are accessed.

## Global Migrations: Application Startup

Global migrations run once, in the `public` schema, when the application starts:

```java
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway globalFlyway(
            @Qualifier("migrationDataSource") DataSource migrationDataSource) {
        return Flyway.configure()
            .dataSource(migrationDataSource)
            .locations("classpath:db/migration/global")
            .schemas("public")
            .baselineOnMigrate(true)
            .load();
    }
}
```

`baselineOnMigrate(true)` handles the case where the `flyway_schema_history` table doesn't exist yet — common on first deploy.

Spring Boot's auto-configured Flyway is disabled (`spring.flyway.enabled: false`) because we need manual control over which migrations run where. The `@Bean(initMethod = "migrate")` ensures global migrations complete before the application accepts requests.

A typical global migration creates tables that live in the `public` schema — accessible from any request context:

```sql
-- V1__create_org_schema_mapping.sql
CREATE TABLE IF NOT EXISTS org_schema_mapping (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_org_id  VARCHAR(255) NOT NULL UNIQUE,
    schema_name   VARCHAR(255) NOT NULL UNIQUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_org_schema_mapping_clerk_org_id
    ON org_schema_mapping (clerk_org_id);
```

## Tenant Migrations: At Provisioning

When a new tenant is provisioned, Flyway runs the full tenant migration set against the new schema:

```java
void runTenantMigrations(String schemaName) {
    Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(schemaName)                          // e.g., "tenant_a1b2c3d4e5f6"
        .baselineOnMigrate(true)
        .load()
        .migrate();
}
```

The `.schemas(schemaName)` parameter tells Flyway to create the `flyway_schema_history` table *inside that schema* and run migrations with `search_path` set to it. Each `CREATE TABLE` lands in the right place without schema qualification in the SQL.

## Tenant Migrations: At Startup (Existing Tenants)

Here's the part that's easy to forget: when you add a new migration (say, V29 for a new feature), existing tenants need it too. New tenants get it automatically at provisioning. But the 50 tenants provisioned last month need it on the next deploy.

```java
@Component
public class TenantMigrationRunner implements ApplicationRunner {

    private final OrgSchemaMappingRepository mappingRepository;
    private final DataSource migrationDataSource;

    @Override
    public void run(ApplicationArguments args) {
        List<OrgSchemaMapping> allMappings = mappingRepository.findAll();
        log.info("Running tenant migrations for {} existing tenants", allMappings.size());

        for (var mapping : allMappings) {
            try {
                runTenantMigrations(mapping.getSchemaName());
            } catch (Exception e) {
                log.error("Migration failed for schema {}: {}",
                    mapping.getSchemaName(), e.getMessage());
                // Continue with other tenants — don't block startup
            }
        }
    }
}
```

At startup, this iterates over every tenant schema and runs Flyway. For schemas that are already up to date, Flyway checks `flyway_schema_history`, finds nothing to run, and returns in ~50ms. For schemas that need V29, Flyway runs it.

**Performance characteristics:**
- 10 tenants, no pending migrations: ~500ms
- 100 tenants, no pending migrations: ~5s
- 100 tenants, 1 pending migration each: ~15s
- 1,000 tenants, 1 pending migration each: ~2.5 min

The linear scaling is fine for B2B SaaS (you typically have dozens to low hundreds of tenants). For consumer SaaS with thousands of tenants, you'd want parallel migration or a queue-based approach.

## Writing Idempotent Migrations

Every migration must be idempotent. This isn't just a best practice — it's required for retry safety. If provisioning fails after migration V15 and retries, Flyway skips V1-V15 (they're in the history), but V15 might have partially completed.

My rules:

```sql
-- Tables: always IF NOT EXISTS
CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Indexes: always IF NOT EXISTS
CREATE INDEX IF NOT EXISTS idx_customers_status ON customers (status);

-- Unique constraints in migration: use IF NOT EXISTS
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_email ON customers (email);

-- Seed data: always ON CONFLICT
INSERT INTO org_roles (slug, name, system_role)
VALUES ('owner', 'Owner', true),
       ('admin', 'Admin', true),
       ('member', 'Member', true)
ON CONFLICT (slug) DO NOTHING;

-- Columns: use a DO block to check existence
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'customers' AND column_name = 'phone'
    ) THEN
        ALTER TABLE customers ADD COLUMN phone VARCHAR(50);
    END IF;
END $$;
```

## The Renumbering Story

During Phase 13 (removing shared schema), I renumbered all tenant migrations from V9-V30 to V7-V28. Why?

The shared-schema era had migrations that created RLS policies and `tenant_id` columns. These were now dead code. Rather than leaving them as no-ops (confusing for future developers), I consolidated:

**Before Phase 13:**
```
V7__create_customers.sql                  # creates table + tenant_id + RLS
V8__create_customer_projects.sql
V9__enable_rls_on_projects.sql            # dead after Phase 13
V10__add_tenant_id_to_documents.sql       # dead after Phase 13
...
V30__create_retainers.sql
```

**After Phase 13:**
```
V7__create_customers.sql                  # creates table, no tenant_id, no RLS
V8__create_customer_projects.sql
V9__create_tasks.sql                      # previously V11
...
V28__create_generated_documents.sql       # previously V30
```

This required a clean-slate database reset (drop all schemas, re-provision). Acceptable pre-launch, but you'd never do this with production data. In production, you'd leave the old migration files in place and add a cleanup migration that drops the RLS policies and `tenant_id` columns.

## The Trigger Bug

Phase 13 also exposed a subtle bug in `V12__create_audit_events.sql`. The original migration had:

```sql
-- Create trigger function
CREATE OR REPLACE FUNCTION set_audit_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.occurred_at = COALESCE(NEW.occurred_at, now());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger (using IF NOT EXISTS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'audit_timestamp_trigger') THEN
        CREATE TRIGGER audit_timestamp_trigger
            BEFORE INSERT ON audit_events
            FOR EACH ROW EXECUTE FUNCTION set_audit_timestamp();
    END IF;
END $$;
```

The problem: `pg_trigger` is a *database-wide* catalog. The `IF NOT EXISTS` check queries all triggers across all schemas. When the first tenant's migration runs, it creates the trigger. When the second tenant's migration runs, `pg_trigger` already contains `audit_timestamp_trigger` (from the first tenant's schema), so the `IF NOT EXISTS` check passes, and the trigger isn't created for the second tenant.

Result: only the first tenant gets audit timestamps. Every subsequent tenant's audit events have null `occurred_at`.

The fix: remove the `IF NOT EXISTS` check. In a schema-per-tenant model, each schema's trigger is independent — `CREATE TRIGGER` in `tenant_abc` doesn't conflict with a trigger of the same name in `tenant_def`. The `pg_trigger` catalog distinguishes by schema.

```sql
-- Fixed: just create the trigger unconditionally
CREATE TRIGGER audit_timestamp_trigger
    BEFORE INSERT ON audit_events
    FOR EACH ROW EXECUTE FUNCTION set_audit_timestamp();
```

This is the kind of bug that only manifests in multi-schema environments. Single-tenant tests pass because there's only one schema. Multi-tenant integration tests caught it — but only because I tested with 2+ tenants in the same test run.

## Practical Tips

**Number migrations sequentially within each path.** Don't try to coordinate version numbers across global and tenant paths. They're independent sequences.

**Use the migration datasource for DDL, not the app datasource.** HikariCP connections through PgBouncer (transaction mode) can't run DDL safely. Use a direct connection.

**Test migrations with multiple tenants.** The trigger bug above only appears when you run the same migration in two different schemas within the same database. Your integration test setup should provision at least 2 test tenants.

**Keep migrations small.** One table per migration, or one set of related changes. This makes partial-failure recovery easier — Flyway retries from the exact migration that failed, not from the beginning.

**Never modify a deployed migration.** If V15 has a bug, don't edit V15. Add V16 that fixes it. Flyway checksums the migration content and will refuse to run if the file changes after it's been applied.

---

*Next in this series: [Keycloak as Your Identity Layer: Orgs, RBAC, and JWT v2 Claims](06-keycloak-identity-layer.md)*

*Previous: [83 Entities, Zero Tenant Columns](04-zero-tenant-columns.md)*
