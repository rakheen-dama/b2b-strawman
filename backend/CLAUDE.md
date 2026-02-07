# Backend CLAUDE.md

Spring Boot 4.0.2 / Java 25 / Maven backend for a multi-tenant B2B SaaS platform with schema-per-tenant isolation.

## Build & Run

```bash
./mvnw spring-boot:run                    # Dev server on port 8080
./mvnw clean package                      # Build JAR
./mvnw test                               # Run tests
./mvnw spring-boot:test-run               # Run with Testcontainers (local Postgres)
```

Requires Docker running for Testcontainers (tests and local dev). Postgres available via `../compose/` — run `docker compose up -d` from there first for manual dev.

## Project Structure

```
src/main/java/io/b2mash/b2b/b2bstrawman/
├── config/           # Spring configuration beans (Security, Hibernate, S3, Resilience4j)
├── multitenancy/     # TenantContext, identifier resolver, connection provider, filters
├── security/         # JWT auth filter, API key filter, role converter
├── provisioning/     # Tenant provisioning controller, service, schema name generator
├── project/          # Project entity, repository, service, controller
├── document/         # Document entity, repository, service, controller
├── s3/               # S3 presigned URL service
└── BackendApplication.java
```

Organize by **feature**, not by layer. Each feature package contains its entity, repository, service, and controller.

## Key Conventions

### Naming
- Base package: `io.b2mash.b2b.b2bstrawman`
- Entities: domain name (e.g., `Project`, `Document`) — no `Entity` suffix
- Repositories: `*Repository` extending `JpaRepository`
- Services: `*Service`
- Controllers: `*Controller`
- Filters: `*Filter` (e.g., `ClerkJwtAuthFilter`, `TenantFilter`)
- Configuration: `*Config` or `*Configuration`
- DTOs: nested records inside the controller or in a `dto` sub-package if shared

### Code Style
- Use Java records for DTOs, request/response objects, and value objects
- Prefer constructor injection (no `@Autowired` on fields)
- Use `@PreAuthorize` annotations for role-based access control
- Return `ResponseEntity` from controllers for explicit status codes
- Use Spring's `ProblemDetail` (RFC 9457) for error responses
- No Lombok — Java 25 records and pattern matching cover most use cases

## Anti-Patterns — Never Do This

- Never use `@Autowired` on fields — use constructor injection
- Never use Lombok — Java 25 records and pattern matching cover all cases
- Never trust client headers for tenant resolution — always derive from validated JWT
- Never use `org.springframework.boot.orm.jpa.HibernatePropertiesCustomizer` — it moved to `boot.hibernate.autoconfigure` in Spring Boot 4
- Never set `hibernate.multiTenancy` property — Hibernate 7 auto-detects from registered provider
- Never use `java -jar` for the Docker entry point — use `org.springframework.boot.loader.launch.JarLauncher`
- Never make `TestcontainersConfiguration` package-private — it must be `public` for `@Import` from subpackages
- Never use `@ActiveProfiles("local")` in tests — use `@ActiveProfiles("test")`. The "local" profile connects to Docker Compose Postgres and activates `LocalSecurityConfig` (permit-all). Tests must run against ephemeral Testcontainers only.

## Spring Boot 4 / Hibernate 7 Gotchas

### Package Migrations (Breaking)

| Class | Old Package (Boot 3) | New Package (Boot 4) |
|-------|---------------------|---------------------|
| `HibernatePropertiesCustomizer` | `boot.orm.jpa` | `boot.hibernate.autoconfigure` |
| `AutoConfigureMockMvc` | `boot.test.autoconfigure.web.servlet` | `boot.webmvc.test.autoconfigure` |

### Hibernate 7 Multitenancy
- No `hibernate.multiTenancy` property — auto-detects from registered provider
- `MultiTenantConnectionProvider<String>` requires `getReadOnlyConnection()` and `releaseReadOnlyConnection()`
- Use `MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER` and `MULTI_TENANT_IDENTIFIER_RESOLVER`

## Profile-Specific Security

- **`local` profile**: `LocalSecurityConfig` permits all requests (no JWT validation). For fast local dev.
- **`dev`/`prod` profiles**: `SecurityConfig` enforces full JWT + RBAC filter chain.
- When debugging auth issues locally, verify which profile is active.

### Spring Profiles
- `local` — Docker Compose Postgres, LocalStack S3
- `dev` — Neon dev branch, AWS S3
- `prod` — Neon main, AWS S3, full observability

## Multitenancy

Schema-per-tenant isolation within a single Postgres database.

- **Schema naming**: `tenant_<12-hex-chars>` — deterministic hash of Clerk org ID
- **Global tables** (`public` schema): `organizations`, `org_schema_mapping`, `processed_webhooks`
- **Tenant tables** (per `tenant_*` schema): `projects`, `documents`
- **Tenant resolution**: JWT `org_id` claim → `org_schema_mapping` lookup → `TenantContext` ThreadLocal → Hibernate `search_path`
- **Connection provider** sets `search_path` on checkout, resets to `public` on release
- Never trust client-supplied headers for tenant resolution — always derive from validated JWT

## Database & Migrations

### Dual Data Sources
- `appDataSource`: HikariCP → Neon PgBouncer (transaction mode) for app traffic
- `migrationDataSource`: Direct Neon connection for Flyway DDL

### Flyway Layout
```
src/main/resources/db/migration/
├── global/     # V1__*.sql — runs once against public schema at startup
└── tenant/     # V1__*.sql — runs per tenant schema at provisioning + startup
```

- Global migrations run first, then all tenant schemas are migrated
- Each migration step must be idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`)
- Name files: `V{number}__{description}.sql` (double underscore)

### HikariCP Settings (production)
- `maximum-pool-size`: 10 (Neon PgBouncer multiplexes upstream)
- `max-lifetime`: 1,680,000ms (under Neon's 30-min timeout)
- `connection-timeout`: 10,000ms (accommodates Neon cold starts)
- `connection-init-sql`: `SET search_path TO public`

## Security

### Authentication
- Clerk JWTs validated via Spring Security OAuth2 Resource Server
- JWKS endpoint for signature verification
- Claims extracted: `sub` (user ID), `org_id`, `org_role`

### Role Mapping
| Clerk Role | Spring Authority | Access Level |
|------------|------------------|--------------|
| `org:owner` | `ROLE_ORG_OWNER` | Full access including delete |
| `org:admin` | `ROLE_ORG_ADMIN` | CRUD on projects and settings |
| `org:member` | `ROLE_ORG_MEMBER` | Read projects, upload documents |

### Filter Chain Order
1. `ClerkJwtAuthFilter` — JWT validation, claim extraction
2. `TenantFilter` — tenant context resolution from JWT org_id
3. `TenantLoggingFilter` — MDC setup (tenantId, userId, requestId)

### Internal API (`/internal/*`)
- Secured by `ApiKeyAuthFilter` validating `X-API-KEY` header
- Never exposed through public ALB — VPC-only access
- Used by Next.js webhook handler to trigger tenant provisioning

## API Structure

### Public Endpoints (JWT-authenticated)
- `GET /api/projects` — List projects (MEMBER+)
- `POST /api/projects` — Create project (ADMIN+)
- `GET /api/projects/{id}` — Get project (MEMBER+)
- `PUT /api/projects/{id}` — Update project (ADMIN+)
- `DELETE /api/projects/{id}` — Delete project (OWNER only)
- `POST /api/projects/{id}/documents/upload-init` — Get presigned upload URL (MEMBER+)
- `POST /api/documents/{id}/confirm` — Confirm upload (MEMBER+)
- `GET /api/documents/{id}/presign-download` — Get presigned download URL (MEMBER+)

### Internal Endpoints (API key)
- `POST /internal/orgs/provision` — Provision new tenant schema

### Health
- `GET /actuator/health` — Health check

## Testing

- **Integration tests**: Testcontainers with PostgreSQL — tests spin up real Postgres in Docker
- **REST tests**: MockMvc + Spring REST Docs (generates API documentation from tests)
- **Test config**: `TestcontainersConfiguration.java` provides `@ServiceConnection` PostgreSQL container
- Run `TestBackendApplication.main()` for local dev with Testcontainers (no Docker Compose needed)

### Integration Test Setup
```java
@SpringBootTest
@AutoConfigureMockMvc  // from boot.webmvc.test.autoconfigure
@Import(TestcontainersConfiguration.class)
class MyIntegrationTest {
    @Autowired MockMvc mockMvc;
    // Postgres auto-started, Flyway auto-applied
}
```

### Multitenancy in Tests
```java
try {
    TenantContext.setTenantId("tenant_test123");
    // perform operations
} finally {
    TenantContext.clear();
}
```

## Error Handling & Resilience

- Tenant provisioning uses Resilience4j `@Retry` (maxAttempts=3, exponential backoff)
- Provisioning status tracked in DB: `PENDING` → `IN_PROGRESS` → `COMPLETED` / `FAILED`
- All provisioning steps are idempotent (safe to retry)
- Use Spring's `ProblemDetail` for structured error responses

## Observability

- Structured JSON logging with MDC fields: `tenantId`, `userId`, `requestId`
- MDC set by `TenantLoggingFilter`, cleared on request completion
- Spring Boot structured logging format: ECS (Elastic Common Schema)
- CloudWatch Logs in production via Fargate `awslogs` driver

## Environment Variables

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | Neon pooled connection string |
| `DATABASE_MIGRATION_URL` | Neon direct connection string (Flyway) |
| `CLERK_ISSUER` | Clerk JWT issuer URL |
| `CLERK_JWKS_URI` | Clerk JWKS endpoint |
| `AWS_S3_BUCKET` | S3 bucket name |
| `AWS_REGION` | AWS region |
| `INTERNAL_API_KEY` | Shared API key for `/internal/*` |
