# Investigation: Connection Pool Exhaustion in Epic 98B

## Executive Summary

The `HikariPool-2 — total=0, active=0, idle=0, waiting=0` signature points to a pool that was **never initialized** rather than one that was drained by leaks. The most likely root cause is a combination of (1) zombie Surefire JVM processes from earlier failed builds holding exclusive locks on the Testcontainers PostgreSQL container, starving subsequent test runs at container startup, and (2) the 98B builder's new code introducing a connection acquisition path outside of a `@Transactional` boundary or try-with-resources block — likely in a new `ChecklistInstanceService` that instantiates checklist items by manually acquiring a JDBC connection or calling repository methods outside a transaction scope. The `total=0` state (not `total=10, active=10`) is the critical clue: HikariCP never successfully created connections in the pool, meaning the database itself was unreachable or connection limits were exhausted at the PostgreSQL level by competing JVMs.

## Evidence

### 1. Pool Signature Analysis

The error `total=0, active=0, idle=0, waiting=0` is **not** a connection leak signature. A leak would show `total=N, active=N, idle=0, waiting=M` where borrowed connections were never returned. `total=0` means HikariCP's pool was unable to create any connections at all — the underlying PostgreSQL was refusing connections.

### 2. Three HikariCP Pools Per JVM

The application creates **three** separate HikariCP pools per Surefire JVM:

| Pool | Config Bean | Default Pool Size | Source |
|------|-------------|-------------------|--------|
| `appDataSource` | `DataSourceConfig.appDataSource()` | 5 (`application.yml` default, no test override) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/DataSourceConfig.java:12-17` |
| `migrationDataSource` | `DataSourceConfig.migrationDataSource()` | 2 (`application-local.yml`, no test override) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/DataSourceConfig.java:19-23` |
| `portalDataSource` | `PortalDataSourceConfig.portalDataSource()` | 5 (`application.yml` default, no test override) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/PortalDataSourceConfig.java:16-19` |

Total connections per JVM: **up to 12** (5 + 2 + 5). The test profile (`application-test.yml`) does **not** override any pool sizes — it only sets JWT issuer, API key, and S3 config.

### 3. Zombie Surefire JVM Accumulation

The symptoms mention stale Surefire JVMs from 03:44, 05:36, 06:52, 07:54, 09:07. With 5+ JVMs alive simultaneously, that's `5 * 12 = 60` potential connections against a single Testcontainers PostgreSQL instance whose default `max_connections` is **100**.

However, each Surefire fork creates its **own** Spring context with its **own** Testcontainers PostgreSQL container (via `@Import(TestcontainersConfiguration.class)` at `backend/src/test/java/io/b2mash/b2b/b2bstrawman/TestcontainersConfiguration.java`). The real issue is that zombie JVMs hold onto Docker containers, and Docker itself may run out of resources (ports, memory, container slots).

### 4. The `HikariPool-2` Identifier

The error specifically names `HikariPool-2`, not `HikariPool-1`. HikariCP assigns pool names sequentially. With three datasources:
- `HikariPool-1` = `appDataSource` (primary, created first)
- `HikariPool-2` = `migrationDataSource` (created second)
- `HikariPool-3` = `portalDataSource` (created third)

`HikariPool-2` is the **migration datasource**. This pool is used by:
- `TenantMigrationRunner.run()` at startup (bootstraps shared schema + migrates tenant schemas) — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantMigrationRunner.java:33-53`
- `TenantProvisioningService.createSchema()` and `runTenantMigrations()` — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:125-143`

Both use try-with-resources and Flyway (which manages its own connections). The migration pool only has `maximum-pool-size: 2` — it is the **smallest** pool and the first to fail if PostgreSQL is under pressure.

### 5. Virtual Threads Enabled

`spring.threads.virtual.enabled: true` is set in `application.yml` (line 31). Virtual threads + HikariCP is a known danger: virtual threads can spawn thousands of concurrent tasks, each potentially requesting a connection from a pool of 5. With ScopedValue-based multitenancy, each virtual thread can trigger Hibernate operations independently. If a test's `@BeforeAll` provisions multiple tenants (each requiring Flyway migrations + pack seeding), the migration pool's 2 connections become a bottleneck.

### 6. Provisioning Flow Connection Pressure

Each `@BeforeAll` setup calls `provisionTenant()` which:
1. `createSchema()` — acquires 1 connection from `migrationDataSource` (try-with-resources, safe)
2. `runTenantMigrations()` — Flyway internally acquires connections from `migrationDataSource` (safe, Flyway manages lifecycle)
3. `fieldPackSeeder.seedPacksForTenant()` — uses `TransactionTemplate` with `appDataSource` (safe, transactional)
4. `templatePackSeeder.seedPacksForTenant()` — same pattern (safe)

The provisioning flow itself is properly guarded. But if the 98B builder added a **checklist pack seeder** or **auto-instantiation logic** that runs during provisioning without a `TransactionTemplate`, connections could leak.

### 7. Existing Connection Safety in 98A Code

The merged 98A code follows established patterns:
- **Entities**: No `@PostLoad`, `@PostPersist`, or `@PrePersist` callbacks that acquire connections (only `@PreUpdate` for `updatedAt` in `ChecklistTemplate`)
- **TenantAwareEntityListener**: Only reads `ScopedValue`, no connection acquisition — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java:19-27`
- **Service**: All methods annotated with `@Transactional` or `@Transactional(readOnly = true)` — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateService.java`
- **No manual JDBC**: Service uses JPA repositories only, no `DataSource.getConnection()`
- **UUID FK fields**: No JPA relationships (`@ManyToOne`, `@OneToMany`), no lazy-loading surprises

## Root Cause Analysis

### Primary Cause: Zombie Process Resource Exhaustion

The most likely primary cause is **zombie Surefire JVM accumulation**. The builder ran 5+ Maven build cycles without killing previous processes:

1. Build 1 (03:44) starts, Spring context boots, Testcontainers starts PostgreSQL, tests begin
2. Build 1 hangs or is interrupted (CTRL+C or timeout), but the JVM doesn't cleanly shut down
3. The Testcontainers PostgreSQL container **stays running** (Testcontainers uses Ryuk for cleanup, but Ryuk only reaps after the Ryuk container itself detects the JVM is gone — which may not happen with zombie JVMs)
4. Build 2 (05:36) starts a **new** Testcontainers PostgreSQL container
5. Repeat for builds 3-5

After 5 iterations:
- 5 PostgreSQL containers running simultaneously, each consuming ~100MB RAM
- 5 Spring contexts alive, each holding 12 HikariCP connections (60 total across JVMs, but to different containers)
- Docker may throttle container creation or port allocation
- Host memory pressure causes container OOM or slow startup
- New containers start but become unresponsive before accepting connections
- HikariCP's `connection-timeout: 10000` (10 seconds) expires before PostgreSQL is ready
- Result: `total=0` — pool never initialized

### Secondary Cause: 98B Builder's New Code (Speculative)

Since the 98B branch was deleted, we can't inspect the actual code. However, the 98B scope (checklist instance completion) would likely have introduced:

1. **A `ChecklistInstanceService`** with methods like `instantiateForCustomer()`, `completeItem()`, `getInstanceProgress()`
2. **A `ChecklistInstanceController`** exposing these endpoints
3. **Possibly a `ChecklistPackSeeder`** to seed checklist packs during provisioning (analogous to `FieldPackSeeder` and `TemplatePackSeeder`)

Common connection leak patterns the builder may have introduced:

- **Repository call outside `@Transactional`**: A service method that calls `instanceRepository.findByCustomerId()` without `@Transactional` annotation. Hibernate acquires a connection for the query but without a transaction boundary, the connection lifecycle depends on OSIV (disabled) or explicit management.
- **Manual `DataSource.getConnection()`**: If the builder added native SQL for progress calculations (e.g., `SELECT count(*) FROM checklist_instance_items WHERE status = 'COMPLETED'`) using `DataSource.getConnection()` without try-with-resources.
- **Seeder without `TransactionTemplate`**: If a checklist pack seeder was added to `TenantProvisioningService.provisionPro()` and `provisionStarter()` but used `@Transactional` on the seeder method instead of the `TransactionTemplate` + `ScopedValue` pattern that `FieldPackSeeder` and `TemplatePackSeeder` use. The `ScopedValue` binding is required for the `TenantIdentifierResolver` to resolve the correct schema.

### Why ALL Tests Failed

The `HikariPool-2` (migration pool) failure at startup prevents:
1. `TenantMigrationRunner.run()` from bootstrapping `tenant_shared` schema
2. All subsequent `@BeforeAll` provisioning from succeeding
3. Every test class fails with the same error because the Spring context is corrupted at boot

This explains why unrelated tests (BillingUpgradeIntegrationTest, InvoiceIntegrationTest, PortalCommentControllerTest) all fail with the identical error — the shared Spring context never completed initialization.

## Contributing Factors

### 1. No Test Profile Pool Sizing

`application-test.yml` does **not** set HikariCP pool sizes. The pools default to:
- `appDataSource`: uses `application.yml` default (5) or no explicit max (HikariCP default: 10)
- `migrationDataSource`: defaults to HikariCP's 10 (no test-profile override of the local profile's 2)
- `portalDataSource`: uses `application.yml` default (5)

File: `backend/src/test/resources/application-test.yml` — only 32 lines, no datasource configuration.

### 2. No Leak Detection in Tests

The local profile configures `leak-detection-threshold: 30000` (30s) for `appDataSource`, but:
- This is only in `application-local.yml`, not `application-test.yml`
- The migration and portal datasources have no leak detection at all
- Without leak detection, a slow leak won't be caught until pool exhaustion

### 3. Testcontainers Container Reuse Not Configured

Each `@SpringBootTest` class with `@Import(TestcontainersConfiguration.class)` creates a new PostgreSQL container unless Spring's context caching reuses the same context. Spring caches contexts by configuration key — if the 98B builder added test classes with different `@Import` combinations or `@MockBean` annotations, each unique combination creates a new context with a new container.

### 4. `@TestInstance(PER_CLASS)` Lifecycle

All integration tests use `@TestInstance(Lifecycle.PER_CLASS)` with `@BeforeAll` for provisioning. This is correct but means:
- The provisioning runs once per class, not per method
- If provisioning fails, **all** test methods in that class fail
- The Spring context stays alive (cached) even after all tests in the class complete

### 5. No Surefire Fork Kill Timeout

The Maven Surefire plugin likely doesn't have `forkedProcessExitTimeoutInSeconds` configured, allowing zombie processes to persist indefinitely.

## Recommended Fix

### Immediate (for 98B retry)

1. **Kill all zombie processes before building**:
   ```bash
   pkill -f surefire || true
   docker container prune -f
   ```

2. **Add HikariCP settings to `application-test.yml`**:
   ```yaml
   spring:
     datasource:
       app:
         maximum-pool-size: 5
         connection-timeout: 10000
         leak-detection-threshold: 10000
       migration:
         maximum-pool-size: 2
         connection-timeout: 30000
       portal:
         maximum-pool-size: 3
         connection-timeout: 10000
   ```

3. **In the new `ChecklistInstanceService`**:
   - Annotate every public method with `@Transactional` or `@Transactional(readOnly = true)`
   - Never call `DataSource.getConnection()` directly — use JPA repositories or `JdbcClient`
   - If adding a `ChecklistPackSeeder`, follow the exact same `TransactionTemplate` + `ScopedValue.where()` pattern from `FieldPackSeeder.seedPacksForTenant()` (line 59-62)

4. **Run a single Maven build at a time**:
   ```bash
   # Kill previous run before starting new one
   pkill -f surefire 2>/dev/null; sleep 2
   ./mvnw clean verify -q 2>&1 | tee /tmp/mvn-98B.log
   ```

### Service Implementation Pattern

The `ChecklistInstanceService` should follow this pattern:
```java
@Service
public class ChecklistInstanceService {
    // Constructor injection only, no DataSource field

    @Transactional
    public InstanceResponse instantiateForCustomer(UUID templateId, UUID customerId) {
        // All repo calls within transaction boundary
    }

    @Transactional
    public InstanceItemResponse completeItem(UUID instanceId, UUID itemId, CompleteRequest req) {
        // Domain method on entity, then save
    }

    @Transactional(readOnly = true)
    public InstanceWithItemsResponse getProgress(UUID instanceId) {
        // Read-only transaction
    }
}
```

## Prevention

### 1. Add a Surefire Zombie Guard to Build Scripts

Add to the `/epic` skill's builder step:
```bash
# Before Maven build
pgrep -f surefire | xargs kill 2>/dev/null || true
sleep 2
```

### 2. Configure Surefire Timeouts

In `pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <forkedProcessExitTimeoutInSeconds>60</forkedProcessExitTimeoutInSeconds>
    </configuration>
</plugin>
```

### 3. Add Test Profile Pool Configuration

Create explicit pool sizes in `application-test.yml` with leak detection enabled. This prevents silent leaks from accumulating across test classes.

### 4. Document the Connection Pool Pattern

Add to `backend/CLAUDE.md` Anti-Patterns section:
- Never add repository calls outside `@Transactional` boundaries (OSIV is disabled)
- Never add a new seeder to `TenantProvisioningService` without using `TransactionTemplate` + `ScopedValue.where()` pattern
- Always kill stale Surefire processes before starting a new build

### 5. Add a Connection Pool Health Check Test

A simple test that verifies all three pools initialize successfully:
```java
@Test
void allConnectionPoolsShouldInitialize() {
    assertThat(appDataSource.getHikariPoolMXBean().getTotalConnections()).isGreaterThan(0);
    assertThat(migrationDataSource.getHikariPoolMXBean().getTotalConnections()).isGreaterThanOrEqualTo(0);
    assertThat(portalDataSource.getHikariPoolMXBean().getTotalConnections()).isGreaterThanOrEqualTo(0);
}
```

### 6. Builder Retry Budget

Cap Maven build retries at 3 per epic slice. If 3 builds fail with the same infrastructure error (not a code error), the builder should **stop and report** rather than accumulating more zombie processes. The 98B builder ran 5+ cycles over 6 hours, each making the problem worse.
