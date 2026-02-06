# Epic 5: Tenant Provisioning — Learnings & Notes

Failed attempt notes for future reference.

---

## What Was Implemented (Before Abort)

All code was written and compiling, but tests were not passing due to the DataSource issue.

### New Files Created

| File | Purpose |
|------|---------|
| `db/migration/global/V2__create_organizations_and_processed_webhooks.sql` | `organizations` table (with `provisioning_status`) + `processed_webhooks` table in public schema |
| `db/migration/tenant/V1__create_projects_and_documents.sql` | `projects` and `documents` tables for tenant schemas |
| `provisioning/SchemaNameGenerator.java` | Deterministic `tenant_<12-hex-chars>` from Clerk org ID using UUID v5 (SHA-1, DNS namespace) |
| `provisioning/Organization.java` | JPA entity for `public.organizations` |
| `provisioning/ProvisioningStatus.java` | Enum: PENDING, IN_PROGRESS, COMPLETED, FAILED |
| `provisioning/OrganizationRepository.java` | JpaRepository with `findByClerkOrgId()` |
| `provisioning/ProvisioningService.java` | Core 6-step idempotent provisioning flow with retry |
| `provisioning/ProvisioningController.java` | `POST /internal/orgs/provision` — returns 201/409 |
| `provisioning/ProvisioningException.java` | RuntimeException wrapper |
| `provisioning/TenantMigrationRunner.java` | `ApplicationRunner` — runs Flyway on all existing tenant schemas at startup |
| `config/DataSourceConfig.java` | Factory component for migration DataSource (see below) |
| `config/RetryConfig.java` | `@EnableResilientMethods` config class |

### Modified Files

- `pom.xml` — added `spring-boot-starter-validation`
- `application-dev.yml` / `application-prod.yml` — added `spring.migration-datasource.url` + HikariCP tuning
- `application-local.yml` — no migration-datasource (falls back to primary)

### Test Files

- `SchemaNameGeneratorTest.java` — 6 unit tests (all passing)
- `ProvisioningIntegrationTest.java` — 6 integration tests (NOT passing, needs DataSourceConfig fix)
- `ProvisioningControllerTest.java` — 4 tests (MockMvc + API key auth)

---

## The Blocking Issue: Circular DataSource Dependency

### Problem

ADR-006 requires dual data sources: pooled (HikariCP via PgBouncer) for app traffic, direct for DDL/Flyway migrations. Spring Boot 4's Flyway auto-configuration eagerly discovers **all** `DataSource` beans. Registering a second `DataSource` bean creates a circular dependency: Flyway needs the bean → bean creation needs the primary DataSource → primary DataSource needs Flyway to finish.

### Attempted Solutions (All Failed)

| # | Approach | Failure |
|---|----------|---------|
| 1 | Two `@ConfigurationProperties` DataSource beans | Testcontainers `@ServiceConnection` didn't populate the second bean's properties |
| 2 | Single `@Bean` returning primary DataSource | `Requested bean is currently in creation: circular reference` |
| 3 | `@Lazy` on DataSource constructor parameter | `StackOverflowError` — infinite proxy recursion |
| 4 | Renamed bean to `tenantMigrationDataSource` | Flyway auto-config still discovered and tried to use it |
| 5 | `@Qualifier("dataSource")` on primary | No bean named `dataSource` exists in Spring Boot 4 |
| 6 | Inject `HikariDataSource` concrete type | No qualifying bean of that type available |
| 7 | `ObjectProvider<DataSource>` + `@Lazy` | Still circular |
| 8 | `@Component` factory (no `@Bean` at all) | **Implemented but never tested** — most promising approach |

### Recommended Approach for Next Attempt

**Use the `@Component` factory pattern (attempt #8)**. Instead of registering a second `DataSource` bean, create a `@Component` with a `getMigrationDataSource()` method:

```java
@Component
public class DataSourceConfig {
    private final DataSource primaryDataSource;
    private final String migrationUrl;
    private volatile DataSource cachedMigrationDataSource;

    public DataSourceConfig(
            DataSource primaryDataSource,
            @Value("${spring.migration-datasource.url:}") String migrationUrl) {
        this.primaryDataSource = primaryDataSource;
        this.migrationUrl = migrationUrl;
    }

    public DataSource getMigrationDataSource() {
        // Double-checked locking
        // Returns primaryDataSource if migrationUrl is blank
        // Otherwise creates new DataSource from migrationUrl
    }
}
```

Services inject `DataSourceConfig` and call `getMigrationDataSource()` — no second bean, no Flyway interference.

**Alternative to investigate**: Spring Boot 4 may have a way to exclude specific beans from Flyway auto-config. Check `FlywayAutoConfiguration` source in `org.springframework.boot.flyway.autoconfigure`.

---

## Spring Boot 4 / Spring Framework 7 Gotchas

### Package Relocations

| Class | Old Package (Boot 3) | New Package (Boot 4) |
|-------|---------------------|---------------------|
| `AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `HibernatePropertiesCustomizer` | `org.springframework.boot.orm.jpa` | `org.springframework.boot.hibernate.autoconfigure` |
| `FlywayConfigurationCustomizer` | `org.springframework.boot.autoconfigure.flyway` | `org.springframework.boot.flyway.autoconfigure` |
| `DataSourceProperties` | `org.springframework.boot.autoconfigure.jdbc` | `org.springframework.boot.jdbc.autoconfigure` |

### Native Retry (Replaces spring-retry)

- spring-retry is **not** in the Spring Boot 4 BOM — don't add it
- Use Spring Framework 7's native retry: `org.springframework.resilience.annotation`
- `@Retryable` uses `maxRetries` (not `maxAttempts`)
- Enable with `@EnableResilientMethods` (not `@EnableRetry`)
- No need for `spring-boot-starter-aop` dependency

### Hibernate 7.2 Multitenancy

- `MultiTenantConnectionProvider<String>` requires `getReadOnlyConnection()` / `releaseReadOnlyConnection()`
- Use `MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER` and `MULTI_TENANT_IDENTIFIER_RESOLVER`
- No `hibernate.multiTenancy` property needed — auto-detects from registered provider

### Testing

- `TestcontainersConfiguration` must be `public` if tests in subpackages `@Import` it
- `@ServiceConnection` on Testcontainers auto-configures the primary DataSource only

---

## Implementation Order for Next Attempt

1. **Solve the DataSource wiring first** — create a minimal spike with just `DataSourceConfig` + a test that creates a schema. Verify no circular dependency before writing anything else.
2. Migrations (V2 global, V1 tenant)
3. Entities and repositories (Organization, OrganizationRepository)
4. SchemaNameGenerator
5. ProvisioningService + ProvisioningException
6. ProvisioningController
7. TenantMigrationRunner
8. RetryConfig
9. Integration tests
10. Format, commit, PR
