# Tenant Provisioning: From Webhook to Working Schema in Under 2 Seconds

*Part 3 of "Multi-Tenant from Scratch" — a series on building production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7.*

---

When a new organization signs up for DocTeams, something needs to happen in the background: their database schema needs to exist before they can do anything.

In a schema-per-tenant model, provisioning means creating a PostgreSQL schema, running 28 migration files against it, seeding compliance packs and document templates, and creating the mapping that tells your application which schema belongs to which org. All of this needs to happen fast enough that the user doesn't notice — and safely enough that a failure mid-way doesn't leave data in an inconsistent state.

Here's how I built it.

## The Full Flow

```
Keycloak Event: organization.created
    │
    ▼
Webhook Handler (Next.js or direct)
    │
    ▼
POST /internal/provisioning/organizations
    │
    ▼
TenantProvisioningService.provisionTenant()
    ├── Idempotency check (already provisioned?)
    ├── Create/find Organization in public schema
    ├── Mark organization IN_PROGRESS
    ├── SchemaNameGenerator.generateSchemaName(orgId)
    ├── CREATE SCHEMA IF NOT EXISTS "tenant_a1b2c3d4e5f6"
    ├── Flyway.migrate() with tenant migrations
    ├── Seed: field packs, template packs, clause packs
    ├── Seed: compliance checklists, request templates
    ├── Seed: automation templates, rate packs, schedule packs
    ├── Create org_schema_mapping entry (LAST)
    ├── Create subscription record
    └── Mark organization COMPLETED
```

Total time: 1.5–2.5 seconds. Most of that is Flyway running 28 migrations.

## Schema Name Generation

Tenant schema names are deterministic. Given the same org ID, you always get the same schema name:

```java
public class SchemaNameGenerator {
    public static String generateSchemaName(String externalOrgId) {
        // SHA-256 hash, truncated to 12 hex chars
        // "org_abc123" → "tenant_a1b2c3d4e5f6"
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(externalOrgId.getBytes(StandardCharsets.UTF_8));
        String hex = HexFormat.of().formatHex(hash);
        return "tenant_" + hex.substring(0, 12);
    }
}
```

Why hashing instead of using the org ID directly?

- **Consistent length.** Schema names are always `tenant_` + 12 hex chars = 19 characters. No surprises from variable-length org IDs.
- **Safe characters.** Hex chars are always valid in PostgreSQL identifiers. No escaping needed.
- **Deterministic.** Re-provisioning the same org ID always produces the same schema name. This makes idempotency trivial.
- **Non-enumerable.** You can't guess another tenant's schema name from your own org ID. Minor security benefit, but worthwhile.

## The Provisioning Service

Here's the core of `TenantProvisioningService`:

```java
@Retryable(
    retryFor = ProvisioningException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2))
public ProvisioningResult provisionTenant(
        String clerkOrgId, String orgName, String verticalProfile) {

    // Idempotency: if already provisioned, return existing
    var existing = mappingRepository.findByClerkOrgId(clerkOrgId);
    if (existing.isPresent()) {
        return ProvisioningResult.alreadyProvisioned(existing.get().getSchemaName());
    }

    // Create or find organization in public schema
    var org = organizationRepository
        .findByClerkOrgId(clerkOrgId)
        .orElseGet(() -> organizationRepository.save(
            new Organization(clerkOrgId, orgName)));
    org.markInProgress();
    organizationRepository.save(org);

    try {
        String schema = SchemaNameGenerator.generateSchemaName(clerkOrgId);

        createSchema(schema);              // CREATE SCHEMA IF NOT EXISTS
        runTenantMigrations(schema);       // Flyway: 28 migration files

        // Seed vertical-specific data packs
        fieldPackSeeder.seedPacksForTenant(schema, clerkOrgId);
        templatePackSeeder.seedPacksForTenant(schema, clerkOrgId);
        clausePackSeeder.seedPacksForTenant(schema, clerkOrgId);
        compliancePackSeeder.seedPacksForTenant(schema, clerkOrgId);
        requestPackSeeder.seedPacksForTenant(schema, clerkOrgId);
        automationTemplateSeeder.seedPacksForTenant(schema, clerkOrgId);
        ratePackSeeder.seedPacksForTenant(schema, clerkOrgId);
        schedulePackSeeder.seedPacksForTenant(schema, clerkOrgId);

        // Mapping created LAST — TenantFilter only sees schema
        // after all tables and seed data exist
        createMapping(clerkOrgId, schema);

        org.markCompleted();
        organizationRepository.save(org);
        return ProvisioningResult.success(schema);

    } catch (Exception e) {
        org.markFailed();
        organizationRepository.save(org);
        throw new ProvisioningException(
            "Provisioning failed for org " + clerkOrgId, e);
    }
}
```

Three design decisions worth explaining:

### 1. Mapping Created Last

The `org_schema_mapping` entry is created after everything else — after schema creation, after migrations, after seeding. This is critical because `TenantFilter` queries this mapping on every request. If the mapping exists but the schema isn't fully provisioned, the user's first request will hit missing tables and get a 500 error.

By creating the mapping last, the TenantFilter only "sees" tenants whose schemas are complete and seeded. The user's first request hits a fully functioning schema.

### 2. Every Step Is Idempotent

```sql
-- Schema creation
CREATE SCHEMA IF NOT EXISTS "tenant_a1b2c3d4e5f6";

-- Table creation (inside migration)
CREATE TABLE IF NOT EXISTS projects (...);

-- Index creation
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects (status);

-- Seed data
INSERT INTO document_templates (name, slug, ...) VALUES (...)
ON CONFLICT (slug) DO NOTHING;
```

If provisioning fails at step 7 of 12 and retries, steps 1-6 are no-ops. Flyway tracks which migrations have run. Seed data uses `ON CONFLICT DO NOTHING`. The retry picks up exactly where it left off.

### 3. Automatic Retry with Backoff

The `@Retryable` annotation gives us 3 attempts with exponential backoff (1s, 2s, 4s). Transient failures — a brief database connection hiccup, a lock timeout during schema creation — resolve themselves on retry. Persistent failures (invalid org ID, corrupt migration file) throw `IllegalArgumentException`, which is excluded from retry via `noRetryFor`.

## Flyway Dual-Path Configuration

DocTeams has two sets of migrations:

```
src/main/resources/db/migration/
├── global/          # Runs once at startup, in 'public' schema
│   ├── V1__create_org_schema_mapping.sql
│   ├── V2__create_organizations.sql
│   ├── V3__create_processed_webhooks.sql
│   └── V4__create_subscriptions.sql
└── tenant/          # Runs per-tenant at provisioning AND startup
    ├── V1__create_members.sql
    ├── V2__create_org_roles.sql
    ├── V3__create_projects.sql
    ├── V4__create_documents.sql
    ├── ... (28 files total)
    └── V28__create_generated_documents.sql
```

Global migrations run at application startup via Spring configuration:

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

Tenant migrations run programmatically in `TenantProvisioningService`:

```java
void runTenantMigrations(String schemaName) {
    Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(schemaName)
        .baselineOnMigrate(true)
        .load()
        .migrate();
}
```

Note: `spring.flyway.enabled: false` in the application config — Flyway's auto-configuration is disabled because we manage migrations manually through `FlywayConfig` (global) and `TenantProvisioningService` (tenant). This gives us full control over when and where migrations run.

### Startup Migration for Existing Tenants

When the application starts, it also runs tenant migrations against all *existing* schemas. This ensures that when you add V29 to the tenant migration path, all existing tenants get it on the next deploy — not just newly provisioned ones:

```java
@Component
public class TenantMigrationRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        List<OrgSchemaMapping> allMappings = mappingRepository.findAll();
        for (var mapping : allMappings) {
            runTenantMigrations(mapping.getSchemaName());
        }
    }
}
```

This scales linearly with tenant count. For 100 tenants with no pending migrations, it takes ~5 seconds (Flyway checks the `flyway_schema_history` table, finds nothing to run, moves on). For 100 tenants with a new migration, ~15 seconds. Acceptable for B2B SaaS, but you'd want a parallel or queue-based approach for thousands of tenants.

## The Dual Datasource Strategy

DocTeams uses two PostgreSQL connections:

```yaml
spring:
  datasource:
    app:
      jdbc-url: jdbc:postgresql://localhost:5432/app
      maximum-pool-size: 10
      connection-init-sql: SET search_path TO public
    migration:
      jdbc-url: jdbc:postgresql://localhost:5432/app
      maximum-pool-size: 2
```

**Why two?** The app datasource goes through HikariCP and is tuned for request traffic — connection pooling, timeouts, batch sizes. The migration datasource is a direct connection used for DDL operations (CREATE SCHEMA, CREATE TABLE, Flyway metadata).

In production with Neon serverless Postgres, this distinction matters more: the app datasource connects through PgBouncer (transaction mode, pooled connections), while the migration datasource connects directly to Neon (DDL operations don't work through PgBouncer in transaction mode because they require session-level state).

```java
@Configuration
public class DataSourceConfig {

    @Bean(name = "appDataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.app")
    public HikariDataSource appDataSource() {
        return new HikariDataSource();
    }

    @Bean(name = "migrationDataSource")
    @ConfigurationProperties("spring.datasource.migration")
    public HikariDataSource migrationDataSource() {
        return new HikariDataSource();
    }
}
```

## Data Pack Seeding

After migrations create the tables, seeders populate them with industry-specific starter data. Each seeder checks a `verticalProfile` parameter to decide which packs to seed:

```
Generic packs (all tenants):
  - 3 document templates (Project Summary, Customer Overview, Invoice)
  - 5 automation rules (task reminders, overdue invoices)
  - Standard org roles (owner, admin, member)

Accounting-ZA packs (verticalProfile = "accounting-za"):
  - Custom field packs (FICA details, VAT numbers, SARS references)
  - Compliance checklists (FICA/KYC with entity-type-specific items)
  - Request templates (Tax Return Docs, Monthly Bookkeeping, Annual Audit)
  - 7 accounting-specific document templates

Law-ZA packs (verticalProfile = "law-za"):
  - Litigation document templates
  - Court calendar compliance checklists
  - Case management custom field packs
```

Seeders are idempotent — they use `ON CONFLICT DO NOTHING` or check for existing records before inserting. Running them twice produces the same result.

## Failure Scenarios

### What happens if provisioning fails at step 5 of 12?

The schema exists, some tables exist, but seeding didn't complete and no mapping was created. The user can't access the tenant (no mapping = TenantFilter returns 403). On retry, `CREATE SCHEMA IF NOT EXISTS` is a no-op, Flyway skips already-run migrations, and seeding continues from where it left off.

### What happens if two webhooks arrive for the same org?

The idempotency check at the top handles this: `mappingRepository.findByClerkOrgId(orgId)` returns the existing mapping, and we return `alreadyProvisioned()`. If both webhooks arrive simultaneously before either creates the mapping, the `UNIQUE` constraint on `org_schema_mapping.clerk_org_id` prevents duplicates — one succeeds, the other gets a constraint violation and retries (finding the mapping on retry).

### What happens if the database goes down mid-provisioning?

`@Retryable` retries 3 times with backoff. If all retries fail, the organization is marked `FAILED` in the global table. An admin can trigger re-provisioning manually. Because every step is idempotent, re-provisioning from a failed state is safe.

### What about Flyway metadata?

Each tenant schema has its own `flyway_schema_history` table. This means Flyway tracks migration state per-tenant. If a migration fails for one tenant (say, a data migration with a constraint violation), other tenants are unaffected — their `flyway_schema_history` shows the migration as successful.

## Performance

Benchmarked on a local PostgreSQL 16 instance:

| Step | Time |
|------|------|
| Schema creation | ~50ms |
| Flyway (28 migrations, empty schema) | ~1,200ms |
| Pack seeding (generic + accounting-za) | ~300ms |
| Mapping creation | ~10ms |
| **Total** | **~1,600ms** |

On Neon serverless (with network latency):

| Step | Time |
|------|------|
| Schema creation | ~100ms |
| Flyway (28 migrations) | ~1,800ms |
| Pack seeding | ~500ms |
| Mapping creation | ~50ms |
| **Total** | **~2,500ms** |

For a B2B product where tenants sign up a few times per week, 2.5 seconds is invisible. The user completes their org setup form, and by the time the page redirects to the dashboard, the schema is ready.

---

*Next in this series: [27 Entities, Zero Tenant Columns: How Schema Isolation Simplifies Everything](04-zero-tenant-columns.md)*

*Previous: [ScopedValue Over ThreadLocal](02-scopedvalue-over-threadlocal.md)*
