# HikariCP Connection Pools — Architecture & Lifecycle

## 1. Pool Architecture Overview

```mermaid
graph TB
    subgraph "Spring Boot Application"
        subgraph "DataSourceConfig.java"
            APP["appDataSource<br/><b>HikariPool-1</b><br/>@Primary"]
            MIG["migrationDataSource<br/><b>HikariPool-2</b>"]
        end
        subgraph "PortalDataSourceConfig.java"
            POR["portalDataSource<br/><b>HikariPool-3</b>"]
        end
    end

    subgraph "Consumers"
        subgraph "App Pool Consumers"
            HBN["Hibernate/JPA<br/>(via SchemaMultiTenantConnectionProvider)"]
            TFT["TenantFilterTransactionManager<br/>(enables @Filter on doBegin)"]
            SVC["All @Service classes<br/>(@Transactional boundaries)"]
        end
        subgraph "Migration Pool Consumers"
            FLY["FlywayConfig.globalFlyway()<br/>(public schema migrations)"]
            TMR["TenantMigrationRunner<br/>(tenant_shared + per-tenant)"]
            TPS["TenantProvisioningService<br/>(CREATE SCHEMA + Flyway)"]
        end
        subgraph "Portal Pool Consumers"
            PJC["portalJdbcClient<br/>(JdbcClient.create)"]
            PTM["portalTransactionManager<br/>(DataSourceTransactionManager)"]
            PSV["Portal services<br/>(@Transactional('portalTransactionManager'))"]
        end
    end

    subgraph "PostgreSQL Database"
        PUB[("public schema<br/>organizations, org_schema_mapping,<br/>processed_webhooks")]
        SHR[("tenant_shared schema<br/>Starter-tier data<br/>(projects, documents, tasks...)")]
        DED[("tenant_a1b2c3d4e5f6<br/>Pro-tier dedicated schema")]
        PTL[("portal schema<br/>portal_contacts,<br/>portal_sessions")]
    end

    APP --> HBN
    APP --> TFT
    APP --> SVC
    HBN --> PUB
    HBN --> SHR
    HBN --> DED

    MIG --> FLY
    MIG --> TMR
    MIG --> TPS
    FLY --> PUB
    TMR --> SHR
    TMR --> DED
    TPS --> DED

    POR --> PJC
    POR --> PTM
    POR --> PSV
    PJC --> PTL

    style APP fill:#4a9eff,color:#fff
    style MIG fill:#ff9f43,color:#fff
    style POR fill:#26de81,color:#fff
```

## 2. Connection Lifecycle — App Pool (Multitenancy Flow)

```mermaid
sequenceDiagram
    participant Client
    participant Filter as TenantFilter
    participant SV as ScopedValue<br/>(RequestScopes)
    participant TX as TenantFilter<br/>TransactionManager
    participant CP as SchemaMultiTenant<br/>ConnectionProvider
    participant Pool as HikariPool-1<br/>(appDataSource)
    participant PG as PostgreSQL

    Client->>Filter: HTTP request with JWT
    Note over Filter: Extract o.id from JWT v2<br/>Lookup org_schema_mapping<br/>(Caffeine cache, 1h TTL)

    Filter->>SV: ScopedValue.where(<br/>TENANT_ID, "tenant_abc...")<br/>.where(ORG_ID, "org_xyz")

    Note over SV: Values immutable within scope<br/>Auto-unbound when lambda exits

    Client->>TX: @Transactional method invoked
    TX->>TX: doBegin() — super starts TX

    alt Shared Schema (Starter tier)
        TX->>TX: Enable Hibernate @Filter<br/>setParameter("tenantId", orgId)
    end

    TX->>CP: getConnection(tenantIdentifier)
    CP->>Pool: getConnection()
    Pool->>PG: Borrow from pool (or create)
    PG-->>Pool: JDBC Connection

    CP->>PG: SET search_path TO tenant_abc...
    CP->>PG: SELECT set_config('app.current_tenant', orgId, false)
    Note over PG: search_path = schema isolation<br/>set_config = RLS defense-in-depth

    CP-->>TX: Connection ready

    TX->>PG: SQL queries execute against tenant schema

    TX->>CP: releaseConnection(tenantId, conn)
    CP->>PG: RESET app.current_tenant
    CP->>PG: SET search_path TO public
    CP->>Pool: Return connection to pool
    Note over Pool: Connection is clean<br/>Ready for any tenant
```

## 3. Pool Settings Comparison

### Per-Profile Configuration

| Setting | App Pool (local) | App Pool (test) | Migration Pool (local) | Migration Pool (test) | Portal Pool (local) | Portal Pool (default) |
|---------|-----------------|-----------------|----------------------|---------------------|--------------------|--------------------|
| `maximum-pool-size` | **5** | **5** | **2** | **2** | **3** | **5** |
| `minimum-idle` | 2 | 1 | _(default: 5)_ | _(default)_ | 1 | 1 |
| `connection-timeout` | 10,000ms | 10,000ms | 30,000ms | 30,000ms | 10,000ms | 10,000ms |
| `validation-timeout` | 5,000ms | _(default)_ | _(default)_ | _(default)_ | _(default)_ | _(default)_ |
| `idle-timeout` | 300,000ms (5m) | _(default)_ | _(default)_ | _(default)_ | _(default)_ | _(default)_ |
| `max-lifetime` | 1,200,000ms (20m) | _(default)_ | _(default)_ | _(default)_ | _(default)_ | _(default)_ |
| `leak-detection-threshold` | 30,000ms (30s) | **10,000ms (10s)** | _(none)_ | **15,000ms (15s)** | _(none)_ | **10,000ms (10s)** |
| `connection-init-sql` | `SET search_path TO public` | _(none)_ | _(none)_ | _(none)_ | `SET search_path TO portal, public` | `SET search_path TO portal, public` |
| `driver-class-name` | `org.postgresql.Driver` | _(from Testcontainers)_ | `org.postgresql.Driver` | _(from Testcontainers)_ | `org.postgresql.Driver` | `org.postgresql.Driver` |

**Source files:**
- `backend/src/main/resources/application.yml` (defaults)
- `backend/src/main/resources/application-local.yml` (local dev overrides)
- `backend/src/test/resources/application-test.yml` (test overrides)

### HikariCP Defaults (when not explicitly set)

| Setting | HikariCP Default | Notes |
|---------|-----------------|-------|
| `maximum-pool-size` | 10 | Only dangerous if migration pool inherits this in test profile |
| `minimum-idle` | Same as max | Eager pool fill — wastes connections for migration pool |
| `connection-timeout` | 30,000ms | Acceptable for migrations, too slow for app queries |
| `idle-timeout` | 600,000ms (10m) | Connections idle >10m are evicted |
| `max-lifetime` | 1,800,000ms (30m) | Must be under PgBouncer/Neon timeout |
| `leak-detection-threshold` | 0 (disabled) | **Critical gap** — leaks go undetected |

## 4. What Went Wrong: The Zombie Accumulation (Epic 98B)

```mermaid
graph TB
    subgraph "Build 1 — 03:44"
        B1[Maven Surefire Fork 1]
        B1C1["Testcontainers<br/>PostgreSQL Container 1"]
        B1P["3 HikariPools<br/>12 connections"]
        B1 --> B1C1
        B1 --> B1P
    end

    subgraph "Build 2 — 05:36"
        B2[Maven Surefire Fork 2]
        B2C2["Testcontainers<br/>PostgreSQL Container 2"]
        B2P["3 HikariPools<br/>12 connections"]
        B2 --> B2C2
        B2 --> B2P
    end

    subgraph "Build 3 — 06:52"
        B3[Maven Surefire Fork 3]
        B3C3["Testcontainers<br/>PostgreSQL Container 3"]
        B3P["3 HikariPools<br/>12 connections"]
        B3 --> B3C3
        B3 --> B3P
    end

    subgraph "Build 4 — 07:54"
        B4[Maven Surefire Fork 4]
        B4C4["Testcontainers<br/>PostgreSQL Container 4"]
        B4P["3 HikariPools<br/>12 connections"]
        B4 --> B4C4
        B4 --> B4P
    end

    subgraph "Build 5 — 09:07"
        B5[Maven Surefire Fork 5]
        B5C5["Testcontainers<br/>PostgreSQL Container 5"]
        B5P["3 HikariPools<br/>12 connections"]
        B5 --> B5C5
        B5 --> B5P
    end

    subgraph "Docker Host Resources"
        MEM["Host Memory<br/>5 x ~100MB PG = 500MB+"]
        PORTS["Port Allocation<br/>5 random high ports"]
        RYUK["Testcontainers Ryuk<br/>Can't reap — JVMs still 'alive'"]
    end

    B1 -.->|ZOMBIE: never exited| MEM
    B2 -.->|ZOMBIE: never exited| MEM
    B3 -.->|ZOMBIE: never exited| MEM
    B4 -.->|ZOMBIE: never exited| MEM

    B5 -->|New build starts| B5C5
    B5C5 -.->|Container starts but<br/>PG under memory pressure| MEM

    B5P -.->|connection-timeout: 10s<br/>expires before PG ready| FAIL

    FAIL["HikariPool-2 — total=0<br/>active=0, idle=0, waiting=0<br/><b>Pool never initialized</b>"]

    style FAIL fill:#ff4757,color:#fff
    style B1 fill:#999,color:#fff
    style B2 fill:#999,color:#fff
    style B3 fill:#999,color:#fff
    style B4 fill:#999,color:#fff
```

### The Cascade

```mermaid
flowchart LR
    A["Builder retries<br/>Maven build"] --> B["Previous Surefire<br/>JVM doesn't exit"]
    B --> C["Docker containers<br/>pile up (Ryuk<br/>can't reap)"]
    C --> D["Host memory<br/>pressure"]
    D --> E["New PG container<br/>slow to accept<br/>connections"]
    E --> F["HikariPool-2<br/>connection-timeout<br/>expires (10s)"]
    F --> G["total=0<br/>Pool never inits"]
    G --> H["TenantMigrationRunner<br/>fails at startup"]
    H --> I["Spring context<br/>corrupted"]
    I --> J["ALL tests fail<br/>same error"]
    J --> A

    style A fill:#ff9f43,color:#fff
    style G fill:#ff4757,color:#fff
    style J fill:#ff4757,color:#fff
```

## 5. Educational Annotations

### Why Three Separate Pools Instead of One?

```mermaid
graph LR
    subgraph "If Single Pool"
        SP["1 Pool, 10 connections"]
        SP --> P1["Flyway DDL<br/>(CREATE TABLE, ALTER)"]
        SP --> P2["Hibernate queries<br/>(SET search_path per-tenant)"]
        SP --> P3["Portal queries<br/>(search_path = portal)"]
        SP --> PROBLEM["Conflicts:<br/>- DDL locks block queries<br/>- search_path fights<br/>- init-sql can't differ"]
    end

    subgraph "With Three Pools"
        APP2["App Pool (5)<br/>init: search_path=public<br/>Runtime: SET per-tenant"]
        MIG2["Migration Pool (2)<br/>No init-sql<br/>Direct DDL access"]
        POR2["Portal Pool (3)<br/>init: search_path=portal<br/>Never changes"]
    end

    style PROBLEM fill:#ff4757,color:#fff
    style APP2 fill:#4a9eff,color:#fff
    style MIG2 fill:#ff9f43,color:#fff
    style POR2 fill:#26de81,color:#fff
```

**App Pool** (`appDataSource`): The primary pool for all Hibernate/JPA operations. The `SchemaMultiTenantConnectionProvider` dynamically sets `search_path` on checkout to the tenant's schema and resets it to `public` on release. Every `@Transactional` service method borrows from this pool. The `connection-init-sql: SET search_path TO public` ensures a clean default even if a connection is borrowed before tenant context is bound (e.g., public-schema lookups like `org_schema_mapping`).

**Migration Pool** (`migrationDataSource`): Dedicated to Flyway DDL operations (CREATE SCHEMA, CREATE TABLE, ALTER TABLE). These operations need direct access to PostgreSQL (not through a connection pool that sets `search_path` via init-sql). Flyway manages its own schema targeting via `.schemas()`. Keeping this pool small (2 connections) prevents DDL from competing with query connections. In production, this pool connects to the direct Neon endpoint (not PgBouncer), because PgBouncer's transaction-mode pooling does not support DDL statements that span multiple transactions.

**Portal Pool** (`portalDataSource`): Permanently locked to `SET search_path TO portal, public` via `connection-init-sql`. Used by the customer portal backend (read-model queries for portal contacts, sessions, documents). Has its own `JdbcClient` and `DataSourceTransactionManager` so portal operations never touch tenant schemas and never interfere with the app pool's dynamic `search_path` switching. The `, public` suffix allows portal queries to also reference public-schema tables (e.g., organizations).

### Why `leak-detection-threshold` Matters

HikariCP's leak detection monitors how long a connection has been borrowed without being returned. If a connection is held longer than the threshold, HikariCP logs a warning with the stack trace of where the connection was acquired. This is critical because:

- **OSIV is disabled** (`spring.jpa.open-in-view: false`) — there is no request-scoped EntityManager holding a connection for the entire HTTP request lifecycle. Connections should be acquired at transaction start and released at transaction end. Any connection held longer than 10-30 seconds is suspect.
- **Without leak detection**, a service method that calls a repository outside `@Transactional` will silently borrow a connection that may never be returned to the pool. This connection is "lost" — the pool doesn't know it's leaked until pool exhaustion causes a `connection-timeout` error minutes or hours later.
- **Test profile uses aggressive thresholds** (10s for app/portal, 15s for migration) to catch leaks early during CI, before they manifest as the `total=0` failure cascade seen in Epic 98B.

### Why `forkedProcessExitTimeoutInSeconds` Prevents Zombies

Maven Surefire forks a new JVM to run tests. If the test JVM hangs (e.g., a deadlocked connection pool, a Spring context that won't shut down), Surefire waits indefinitely by default. Each forked JVM holds:

- 3 HikariCP pools (up to 12 connections)
- 1 Testcontainers PostgreSQL container (~100MB RAM)
- 1 Testcontainers Ryuk sidecar container

With `forkedProcessExitTimeoutInSeconds: 60` in `pom.xml`, Surefire force-kills the forked JVM after 60 seconds of inactivity. This triggers JVM shutdown hooks, which let HikariCP close its pools and Testcontainers Ryuk reap the PostgreSQL container. Without this setting, zombie JVMs accumulate across retries, each consuming host resources until Docker itself runs out of capacity.

### Why `connection-init-sql` Differs Between Pools

| Pool | `connection-init-sql` | Why |
|------|----------------------|-----|
| App | `SET search_path TO public` | Safe default — `SchemaMultiTenantConnectionProvider` overrides this dynamically per-request. Without it, a fresh connection might have PostgreSQL's default search_path (`"$user", public`) which could resolve to a non-existent user schema. |
| Migration | _(none)_ | Flyway sets the schema explicitly via `.schemas()` on each migration run. An init-sql would be overridden immediately anyway. |
| Portal | `SET search_path TO portal, public` | Permanently fixed — portal queries always target the `portal` schema. The `, public` fallback allows joins with public-schema reference tables. This is set once on connection creation and never changed. |

### Why Virtual Threads + Small Pools Is Dangerous

```mermaid
graph TB
    VT["spring.threads.virtual.enabled: true"]
    VT --> SPAWN["Tomcat spawns virtual thread<br/>per HTTP request"]
    SPAWN --> T1["VThread 1 — @Transactional"]
    SPAWN --> T2["VThread 2 — @Transactional"]
    SPAWN --> T3["VThread 3 — @Transactional"]
    SPAWN --> TN["VThread N — @Transactional"]
    SPAWN --> DOTS["...thousands possible"]

    T1 --> POOL["HikariPool-1<br/>maximum-pool-size: 5"]
    T2 --> POOL
    T3 --> POOL
    TN --> POOL

    POOL --> WAIT["VThreads 6+ park<br/>waiting for connection<br/>(up to connection-timeout)"]

    WAIT --> TIMEOUT["connection-timeout: 10s<br/>SQLTransientConnectionException"]

    style POOL fill:#ff9f43,color:#fff
    style WAIT fill:#ff4757,color:#fff
    style TIMEOUT fill:#ff4757,color:#fff
```

With virtual threads enabled, Tomcat can accept thousands of concurrent requests (virtual threads are cheap). But each `@Transactional` method still needs a real JDBC connection from HikariCP. With `maximum-pool-size: 5`, only 5 requests can execute SQL concurrently. The remaining virtual threads park (block) on `HikariPool.getConnection()`, waiting for a connection to be returned. This is safe if requests are fast (sub-second) but dangerous if:

- A query is slow (long-running report, unindexed scan)
- A transaction holds a connection during an external call (HTTP to Clerk, S3 upload)
- The pool is already partially consumed by background tasks (seeders, migration runner)

The mitigation in this codebase: keep transactions short, never call external services inside a `@Transactional` boundary, and let HikariCP's `connection-timeout` (10s) fail fast rather than queue indefinitely.

### Why OSIV Being Disabled Changes Connection Lifecycle

```mermaid
sequenceDiagram
    participant Req as HTTP Request
    participant Ctrl as Controller
    participant Svc as Service (@Transactional)
    participant EM as EntityManager
    participant Pool as HikariPool

    Note over Req,Pool: OSIV=true (DISABLED in this app)
    rect rgb(255, 200, 200)
        Req->>EM: OpenEntityManagerInViewInterceptor<br/>creates EntityManager
        EM->>Pool: Borrows connection IMMEDIATELY
        Note over Pool: Connection held for<br/>ENTIRE request lifecycle
        Req->>Ctrl: Controller executes
        Ctrl->>Svc: Calls service
        Svc->>EM: Queries (uses held connection)
        Svc-->>Ctrl: Returns
        Ctrl->>Req: Renders response<br/>(lazy loads still work)
        Req->>EM: Close EntityManager
        EM->>Pool: Returns connection
    end

    Note over Req,Pool: OSIV=false (CURRENT behavior)
    rect rgb(200, 255, 200)
        Req->>Ctrl: Controller executes
        Ctrl->>Svc: Calls service
        Svc->>EM: @Transactional begins
        EM->>Pool: Borrows connection NOW
        Note over Pool: Connection held ONLY<br/>during transaction
        Svc->>EM: Queries execute
        Svc->>EM: @Transactional commits
        EM->>Pool: Returns connection IMMEDIATELY
        Svc-->>Ctrl: Returns DTO (not entity)
        Ctrl->>Req: Renders response<br/>(no lazy loading possible)
    end
```

With OSIV disabled:
- **Connection hold time is minimal** — only during `@Transactional` boundaries, typically milliseconds
- **Pool utilization is efficient** — 5 connections can serve dozens of concurrent requests if transactions are fast
- **Trade-off**: Lazy loading outside `@Transactional` throws `LazyInitializationException`. This codebase avoids this by using UUID FK fields (no JPA relationships) and projections/DTOs instead of returning entities from services.
- **Required for multitenancy**: OSIV creates an EntityManager at request start, which pins to whatever tenant schema is active at that moment. If the tenant context isn't bound yet (filter chain hasn't run), the EntityManager pins to `public` — and all queries for that request go to the wrong schema. Disabling OSIV ensures the EntityManager is only created inside `@Transactional`, after the filter chain has bound the tenant.

## 6. Bean Wiring Summary

```mermaid
graph TB
    subgraph "Configuration Classes"
        DSC["DataSourceConfig.java"]
        PDSC["PortalDataSourceConfig.java"]
        HMTC["HibernateMultiTenancyConfig.java"]
        FC["FlywayConfig.java"]
    end

    subgraph "Beans"
        DSC --> |"@Bean @Primary"| APP_DS["appDataSource<br/>(HikariDataSource)"]
        DSC --> |"@Bean"| MIG_DS["migrationDataSource<br/>(HikariDataSource)"]
        PDSC --> |"@Bean"| POR_DS["portalDataSource<br/>(HikariDataSource)"]
        PDSC --> |"@Bean"| POR_JC["portalJdbcClient<br/>(JdbcClient)"]
        PDSC --> |"@Bean"| POR_TM["portalTransactionManager<br/>(DataSourceTransactionManager)"]
        HMTC --> |"@Bean @Primary"| TX_MGR["transactionManager<br/>(TenantFilterTransactionManager)"]
        FC --> |"@Bean(initMethod='migrate')"| GLB_FLY["globalFlyway<br/>(Flyway)"]
    end

    subgraph "Hibernate Integration"
        SMTCP["SchemaMultiTenantConnectionProvider<br/>(@Component)"]
        TIR["TenantIdentifierResolver<br/>(@Component)"]
    end

    APP_DS -->|"injected as DataSource<br/>(constructor, @Primary)"| SMTCP
    SMTCP -->|"registered via<br/>HibernatePropertiesCustomizer"| HMTC
    TIR -->|"registered via<br/>HibernatePropertiesCustomizer"| HMTC
    MIG_DS -->|"@Qualifier('migrationDataSource')"| GLB_FLY
    MIG_DS -->|"@Qualifier('migrationDataSource')"| TMR_BEAN["TenantMigrationRunner"]
    MIG_DS -->|"@Qualifier('migrationDataSource')"| TPS_BEAN["TenantProvisioningService"]
    POR_DS --> POR_JC
    POR_DS --> POR_TM

    style APP_DS fill:#4a9eff,color:#fff
    style MIG_DS fill:#ff9f43,color:#fff
    style POR_DS fill:#26de81,color:#fff
```

## 7. Testcontainers Wiring

In tests, all three pools point to the **same** Testcontainers PostgreSQL container, configured via `DynamicPropertyRegistrar` in `TestcontainersConfiguration.java`:

```mermaid
graph TB
    TC["TestcontainersConfiguration.java"]
    TC --> PG["PostgreSQLContainer<br/>(single instance, @ServiceConnection)"]

    PG --> |"jdbc-url, username, password"| APP_T["spring.datasource.app.*"]
    PG --> |"jdbc-url, username, password"| MIG_T["spring.datasource.migration.*"]
    PG --> |"jdbc-url, username, password<br/>+ connection-init-sql"| POR_T["spring.datasource.portal.*"]

    APP_T --> APP_POOL["HikariPool-1 (5 conn)"]
    MIG_T --> MIG_POOL["HikariPool-2 (2 conn)"]
    POR_T --> POR_POOL["HikariPool-3 (3 conn)"]

    APP_POOL --> SINGLE_PG[("Single PostgreSQL<br/>max_connections=100<br/>Total demand: 10 connections")]
    MIG_POOL --> SINGLE_PG
    POR_POOL --> SINGLE_PG

    style SINGLE_PG fill:#6c5ce7,color:#fff
```

**Key difference from production**: In production, `appDataSource` connects through **PgBouncer** (transaction-mode pooling) while `migrationDataSource` connects **directly** to PostgreSQL (DDL requires direct connections). In tests, all three pools connect directly to the same Testcontainers PostgreSQL instance.
