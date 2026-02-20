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
├── exception/        # Shared semantic exceptions (ResourceNotFoundException, ForbiddenException, etc.)
├── multitenancy/     # RequestScopes (ScopedValue), identifier resolver, connection provider, filters
├── security/         # JWT auth filter, API key filter, role converter
├── provisioning/     # Tenant provisioning controller, service, schema name generator
├── project/          # Project entity, repository, service, controller
├── document/         # Document entity, repository, service, controller
├── member/           # Member sync, project members — entities, repositories, services, controllers
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
- Never use `@ActiveProfiles("local")` in tests — use `@ActiveProfiles("test")`. The "local" profile connects to Docker Compose Postgres. Tests must run against ephemeral Testcontainers only.
- Never use flat JWT claims (`org_id`, `org_role`) — Clerk JWT v2 nests org claims under `"o"`: `jwt.getClaim("o")` returns `Map<String, Object>` with keys `id`, `rol`, `slg`
- Never use `ThreadLocal` for request-scoped context — use `ScopedValue` via `RequestScopes` (guaranteed cleanup, virtual thread safe)
- Never call `RequestScopes.TENANT_ID.get()` without checking `isBound()` first or accepting `NoSuchElementException`
- Never build `ProblemDetail` directly in controllers or services — throw semantic exceptions from `exception/` package instead
- Never return `Optional` from services for "not found" or "access denied" — throw `ResourceNotFoundException`
- Never duplicate error helper methods in controllers — use `RequestScopes.requireMemberId()` and the shared exception classes
- Never return `Page<T>` from controllers without `@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)` on the application class — raw `PageImpl` serialization produces an unstable JSON structure. The `VIA_DTO` mode nests pagination metadata under a `page` object: `{ content: [...], page: { totalElements, totalPages, size, number } }`
- Never create `Customer` objects in tests using the raw constructor without explicit `LifecycleStatus` — use `TestCustomerFactory` instead. Customers default to `PROSPECT`, so tests that need ACTIVE customers will fail without it.

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

- All profiles (including `local`) use `SecurityConfig` with full JWT + RBAC filter chain.
- The `local` profile uses Clerk dev instance URLs in `application-local.yml` for JWT validation.

### Spring Profiles
- `local` — Docker Compose Postgres, LocalStack S3
- `dev` — Neon dev branch, AWS S3
- `prod` — Neon main, AWS S3, full observability

### Architecture Conventions
- Never put business logic in controllers. Controllers delegate to services only.
- Avoid premature abstractions — do not create provider/adapter patterns until there are two concrete implementations (YAGNI).
- When implementing new features, read existing controller and service patterns in the codebase FIRST before writing code.

## Multitenancy

Schema-per-tenant isolation within a single Postgres database. Every tenant — regardless of billing tier — gets a dedicated `tenant_<hash>` schema. There is no shared schema.

- **Schema naming**: `tenant_<12-hex-chars>` — deterministic hash of Clerk org ID
- **Global tables** (`public` schema): `organizations`, `org_schema_mapping`, `processed_webhooks`
- **Tenant tables** (per `tenant_*` schema): `projects`, `documents`, and all other domain entities
- **Tenant resolution**: JWT `o.id` claim → `org_schema_mapping` lookup → `RequestScopes.TENANT_ID` ScopedValue → Hibernate `search_path`
- **Connection provider** sets `search_path` on checkout, resets to `public` on release
- **Isolation model**: Pure schema boundary — no `@Filter`, no RLS policies, no `tenant_id` columns. Standard `JpaRepository.findById()` works correctly.
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
- **Clerk JWT v2 format** (`"v": 2`): org claims nested under `"o"` object
  - `o.id` — Clerk org ID (e.g., `org_abc123`)
  - `o.rol` — short role name: `owner`, `admin`, `member`
  - `o.slg` — org slug
  - Access via: `Map<String, Object> org = jwt.getClaim("o"); org.get("id");`
- Top-level claims: `sub` (user ID), `sid` (session ID), `azp` (authorized party)

### Role Mapping
| Clerk JWT `o.rol` | Spring Authority | Access Level |
|--------------------|------------------|--------------|
| `owner` | `ROLE_ORG_OWNER` | Full access including delete |
| `admin` | `ROLE_ORG_ADMIN` | CRUD on projects and settings |
| `member` | `ROLE_ORG_MEMBER` | Read projects, upload documents |

### Filter Chain Order
1. `ApiKeyAuthFilter` — API key validation for `/internal/*` endpoints
2. `BearerTokenAuthenticationFilter` + `ClerkJwtAuthenticationConverter` — JWT validation, role extraction from `o.rol`
3. `TenantFilter` — binds `RequestScopes.TENANT_ID` ScopedValue from JWT `o.id`
4. `MemberFilter` — binds `RequestScopes.MEMBER_ID` and `RequestScopes.ORG_ROLE` ScopedValues
5. `TenantLoggingFilter` — MDC setup (tenantId, userId, memberId, requestId)

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

### Test Factories
Always use `TestCustomerFactory` (in `testutil/`) to create customers in tests:
```java
// For tests that need an ACTIVE customer (most common)
var customer = TestCustomerFactory.createActiveCustomer("Name", "email@test.com", memberId);

// For tests that need a specific lifecycle status
var customer = TestCustomerFactory.createCustomerWithStatus("Name", "email@test.com", memberId, LifecycleStatus.PROSPECT);
```
Never use the raw `new Customer(...)` constructor without explicit `LifecycleStatus.ACTIVE` — the default is `PROSPECT`, which blocks operations guarded by `CustomerLifecycleGuard` (project creation, invoicing, time entries, etc.).

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
ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test123").run(() -> {
    // perform operations — auto-cleans up when lambda exits
});
```

### JWT Mocks in Tests (Clerk v2 format)
```java
// Mock JWT with v2 nested org claims
private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
}
```
Note: Spring Security Test `jwt()` mock does NOT invoke `ClerkJwtAuthenticationConverter` — set `.authorities()` explicitly.

## Error Handling & Resilience

### Exception Pattern
Services throw semantic exceptions from the `exception/` package — Spring auto-renders them as RFC 9457 ProblemDetail responses. Controllers should be pure delegation with no error mapping.

| Exception | HTTP Status | When to use |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 | Resource not found **or** access denied (security-by-obscurity) |
| `ResourceConflictException` | 409 | Duplicate or state conflict |
| `ForbiddenException` | 403 | Authenticated but insufficient permissions |
| `InvalidStateException` | 400 | Invalid state transition or bad request |
| `MissingOrganizationContextException` | 401 | JWT missing org claim |

Use `RequestScopes.requireMemberId()` and `RequestScopes.getOrgRole()` in controllers instead of manual `MEMBER_ID.isBound()` checks.

### Resilience
- Tenant provisioning uses `@Retry` (maxAttempts=3, exponential backoff)
- Provisioning status tracked in DB: `PENDING` → `IN_PROGRESS` → `COMPLETED` / `FAILED`
- All provisioning steps are idempotent (safe to retry)

## Observability

- Structured JSON logging with MDC fields: `tenantId`, `userId`, `requestId`
- MDC set by `TenantLoggingFilter`, cleared on request completion
- Spring Boot structured logging format: ECS (Elastic Common Schema)
- CloudWatch Logs in production via Fargate `awslogs` driver

## Dev Portal Harness (local/dev only)

A Thymeleaf-based test harness for exercising the full customer portal flow. Only available when
running with `local` or `dev` Spring profile (`@Profile({"local", "dev"})` per ADR-033).

### Starting the Harness

```bash
./mvnw spring-boot:run  # Uses local profile by default
```

### URLs

- **Generate Magic Link**: `http://localhost:8080/portal/dev/generate-link`
- **Dashboard**: `http://localhost:8080/portal/dev/dashboard?token={portalJwt}` (auto-redirected after exchange)
- **Project Detail**: `http://localhost:8080/portal/dev/project/{id}?token={portalJwt}`

### Flow

1. Navigate to `/portal/dev/generate-link`
2. Select an organization and enter a customer email address
3. Click "Generate Magic Link" -- a clickable link appears
4. Click the magic link -- the token is exchanged for a portal JWT and you're redirected to the dashboard
5. Browse projects, documents, comments, and summary from the dashboard

### Limitations

- **Dev/local only** -- profile-gated, never exposed in production
- **Minimal styling** -- inline CSS, not a production UI
- **Self-service contacts** -- auto-creates PortalContact (GENERAL role) if one doesn't exist for the email
- **JWT as query param** -- acceptable for dev tooling, not suitable for production

### Source Files

- Controller: `dev/DevPortalController.java`
- Config: `config/DevPortalConfig.java`
- Templates: `src/main/resources/templates/portal/` (generate-link, dashboard, project-detail)

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
