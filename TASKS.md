# Multi-tenant SaaS Starter — Technical Task Breakdown

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices   | Status  |
|------|------|-------|------|--------|----------|---------|
| 1 | Scaffolding & Local Dev | Both | — | M | —        | **Done** |
| 2 | Auth & Clerk Integration | Frontend | 1 | M | —        | **Done** |
| 3 | Organization Management | Frontend | 2 | S | —        | **Done** |
| 4 | Webhook Infrastructure | Frontend | 1, 2 | M | 4A, 4B   | **Done** |
| 5 | Tenant Provisioning | Backend | 1, 6 | L | 5A, 5B, 5C | **Done** |
| 6 | Multitenancy Backend | Backend | 1 | L | —        | **Done** |
| 7 | Core API — Projects | Backend | 6 | M | 7A, 7B   | **Done** |
| 8 | Core API — Documents | Backend | 7, 9 | M | 8A, 8B   | **Done** |
| 9 | S3 Integration | Backend | 1 | S | —        | **Done** |
| 10 | Dashboard & Projects UI | Frontend | 3, 7 | M | 10A, 10B, 10C | **Done** |
| 11 | Documents UI | Frontend | 10, 8 | M | 11A, 11B | **Done** |
| 12 | Team Management UI | Frontend | 3 | S | —        | **Done** |
| 13 | Containerization | Both | 1 | S | —        | **Done** |
| 14 | AWS Infrastructure | Infra | 13 | XL | 14A–14D  | **Done** |
| 15 | Deployment Pipeline | Infra | 13, 14 | L | 15A, 15B | **Done** |
| 16 | Testing & Quality | Both | 7, 8, 10, 11 | L | 16A–16C  |         |
| 17 | Members Table + Webhook Sync | Both | 4, 5 | M | 17A, 17B | **Done** |
| 18 | MemberFilter + MemberContext | Backend | 17 | M | 18A, 18B | **Done** |
| 19 | Project Members Table + API | Backend | 18 | M | 19A, 19B | **Done**|
| 20 | Project Access Control | Backend | 19 | L | 20A, 20B |         |
| 21 | Frontend — Project Members Panel | Frontend | 19, 20 | M | 21A, 21B |         |
| 22 | Frontend — Filtered Project List | Frontend | 20, 21 | S | —        |         |

---

## Epic 1: Project Scaffolding & Local Development

**Goal**: Establish monorepo structure, initialize both projects with correct tooling, and set up Docker Compose for local development.

**Dependencies**: None

**Estimated Effort**: M

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 1.1 | Initialize monorepo structure | **Done** | Root `.gitignore`, `.editorconfig`, `README.md`. Directories: `frontend/`, `backend/`, `compose/`, `infra/`, `.github/`. |
| 1.2 | Initialize Next.js project | **Done** | Next.js 16.1.6, TypeScript 5, Tailwind CSS v4, App Router. Path alias `@/*`. Package manager: pnpm. |
| 1.3 | Install and configure Shadcn UI | **Done** | new-york style, 8 components: Button, Card, Input, Dialog, Table, DropdownMenu, Separator, Badge. |
| 1.4 | Initialize Spring Boot project | **Done** | Spring Boot 4.0.2, Java 25, Maven (not Gradle). Profiles: `application.yml` + `-local.yml`, `-dev.yml`, `-prod.yml`. `LocalSecurityConfig.java` permits all requests in local profile (replaced in Epic 6). |
| 1.5 | Create Docker Compose for local services | **Done** | `compose/docker-compose.yml` with Postgres 16 + LocalStack. Healthchecks configured. |
| 1.6 | Configure environment files | **Done** | `frontend/.env.local.example` (Clerk keys, backend URL, API key). Backend config in `application-local.yml` (Postgres at `b2mash.local:5432`, LocalStack S3). |
| 1.7 | Set up frontend linting and formatting | **Done** | ESLint (flat config), Prettier + `prettier-plugin-tailwindcss`. Scripts: `pnpm run lint`, `pnpm run format`, `pnpm run format:check`. |
| 1.8 | Set up backend linting and formatting | **Done** | Spotless 3.2.1 + Google Java Format 1.28.0 (Java 25 compatible). `./mvnw spotless:check` and `./mvnw spotless:apply`. |
| 1.9 | Create basic CI workflow | **Done** | `.github/workflows/ci.yml` — parallel frontend (pnpm lint, format:check, build) and backend (spotless:check, test, package) jobs. Triggers on PR and push to main. |
| 1.10 | Add LocalStack S3 init script | **Done** | `compose/data/s3/init-s3.sh` creates `docteams-dev` bucket on startup. |

### Deviations from Original Plan
- **Maven instead of Gradle** — Spring Boot project was generated with Maven, not Gradle. Commands use `./mvnw` instead of `./gradlew`.
- **pnpm instead of npm** — Standardized on pnpm for faster installs and strict dependency resolution.
- **Docker Compose in `compose/`** — Lives in a subdirectory rather than repo root, with its own `.env`.
- **Spotless 3.2.1** — Required for Java 25 compatibility (2.x has `NoSuchMethodError` on `DeferredDiagnosticHandler`).
- **google-java-format 1.28.0** — Required for Java 25 (1.19.x incompatible).

---

## Epic 2: Authentication & Clerk Integration

**Goal**: Integrate Clerk for authentication in Next.js — sign-up, sign-in, session management, and JWT extraction for API calls.

**Dependencies**: Epic 1

**Estimated Effort**: M

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 2.1 | Create Clerk development instance | **Done** | Clerk dev instance created with Organizations enabled. Keys in `.env.local`. |
| 2.2 | Install Clerk Next.js SDK | **Done** | `@clerk/nextjs` ^6.37.2 already installed. `<ClerkProvider>` wraps root layout with `cssLayerName: "clerk"` for Tailwind v4 compatibility. |
| 2.3 | Configure Clerk middleware | **Done** | `middleware.ts` with `clerkMiddleware()`. Public routes: `/`, `/sign-in(.*)`, `/sign-up(.*)`, `/api/webhooks(.*)`. `organizationSyncOptions` pre-configured for Epic 3. |
| 2.4 | Create sign-up page | **Done** | `app/(auth)/sign-up/[[...sign-up]]/page.tsx` with Clerk `<SignUp />`. Centered auth layout. |
| 2.5 | Create sign-in page | **Done** | `app/(auth)/sign-in/[[...sign-in]]/page.tsx` with Clerk `<SignIn />`. |
| 2.6 | Implement JWT extraction utility | **Done** | `lib/api.ts` with typed `apiClient<T>()` — attaches Bearer JWT via `auth().getToken()`. Custom `ApiError` class. |
| 2.7 | Create authenticated layout shell | **Done** | `app/(app)/layout.tsx` with sidebar (Dashboard, Projects, Team nav links), header with `<UserButton />`. Placeholder dashboard at `/dashboard`. |

### Additional Items
- **Webhook handler stub**: `app/api/webhooks/clerk/route.ts` using `verifyWebhook` from `@clerk/nextjs/webhooks`. Stubs for `organization.created` and `organization.updated` events (full implementation in Epic 4).
- **Landing page fix**: Replaced `<a>` tags with Next.js `<Link>` to fix ESLint `no-html-link-for-pages` errors.
- **Env vars**: Added `NEXT_PUBLIC_CLERK_SIGN_IN_URL`, `NEXT_PUBLIC_CLERK_SIGN_UP_URL`, and fallback redirect URLs to `.env.local` and `.env.local.example`.

### Deviations from Original Plan
- **Temporary `/dashboard` route**: Post-auth redirect goes to `/dashboard` (not org-scoped) since Epic 3 hasn't been implemented yet. Will be replaced with `/org/[slug]/dashboard` in Epic 3.
- **Next.js 16 middleware deprecation**: Next.js 16 shows a warning that `middleware.ts` is deprecated in favor of `proxy.ts`. The middleware still works correctly but may need migration in a future update.

---

## Epic 3: Organization Management

**Goal**: Enable org creation, switching, and URL-based org context with Clerk's organization features.

**Dependencies**: Epic 2

**Estimated Effort**: S

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 3.1 | Create org creation page | **Done** | `app/(app)/create-org/page.tsx` with `<CreateOrganization />`. `afterCreateOrganizationUrl="/org/:slug/dashboard"`. |
| 3.2 | Configure org-based URL routing | **Done** | `organizationSyncOptions` already configured in middleware (from Epic 2). Created `app/(app)/org/[slug]/layout.tsx` with sidebar, validates slug against active org, redirects on mismatch. |
| 3.3 | Implement org switcher | **Done** | `<OrganizationSwitcher />` in org layout header with `afterSelectOrganizationUrl="/org/:slug/dashboard"` and `hidePersonal`. |
| 3.4 | Create org-scoped dashboard page | **Done** | `app/(app)/org/[slug]/dashboard/page.tsx` showing org name. Placeholder pages added for `/projects` and `/team`. |
| 3.5 | Handle no-org state | **Done** | `/dashboard` page acts as redirect: active org → `/org/[slug]/dashboard`, no active org but has memberships → first org's dashboard, no orgs → `/create-org`. |

### Architecture Decisions
- **Sidebar moved to org layout**: `(app)/layout.tsx` simplified to pass-through. Sidebar with org-scoped nav links lives in `org/[slug]/layout.tsx`. This means create-org and redirect pages render without the sidebar.
- **Org switcher placement**: Header bar with `OrganizationSwitcher` (left) and `UserButton` (right).
- **Redirect flow**: Post-auth fallback URL remains `/dashboard` which handles all redirect logic server-side using `clerkClient().users.getOrganizationMembershipList()`.

---

## Epic 4: Webhook Infrastructure

**Goal**: Receive, verify, and process Clerk webhook events with idempotency and error handling.

**Dependencies**: Epic 1, Epic 2

**Scope**: Frontend

**Estimated Effort**: M

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 4.1 | Create webhook route handler | **Done** | `app/api/webhooks/clerk/route.ts` — full implementation with `verifyWebhook()`, svix-id extraction, event routing. |
| 4.2 | Configure Clerk webhook endpoint | **Done** | Documented in `frontend/docs/webhook-setup.md`. Manual Clerk Dashboard step. |
| 4.3 | Implement event router | **Done** | `lib/webhook-handlers.ts` — `routeWebhookEvent()` dispatches 9 event types (org CRUD, membership CRUD, invitation events). |
| 4.4 | Implement organization.created handler | **Done** | Calls `POST /internal/orgs/provision` via `lib/internal-api.ts` with X-API-KEY. Treats 409 as success (idempotent). Fire-and-forget error handling. |
| 4.5 | Implement organization.updated handler | **Done** | Calls `PUT /internal/orgs/update` with `updatedAt` for out-of-order event detection. |
| 4.6 | Implement idempotency layer | **Done** | Frontend extracts `svix-id` from headers and passes through. Backend-side dedup via `processed_webhooks` table deferred to Epic 5. Frontend handles 409 Conflict as implicit idempotency. |
| 4.7 | Exclude webhook route from Clerk auth | **Done** | Already configured in `middleware.ts` (Epic 2) — `/api/webhooks(.*)` is a public route. |
| 4.8 | Add webhook handler tests | **Done** | 13 tests across 2 files using vitest. Covers: signature verification, event routing, provisioning calls, 409 handling, error resilience, unknown events. |

### Architecture

```
Clerk → POST /api/webhooks/clerk (Next.js)
         ↓ verifyWebhook() signature check (400 on failure)
         ↓ Extract svix-id from headers
         ↓ routeWebhookEvent() dispatches by event type
         ↓ organization.created →
    POST /internal/orgs/provision (Spring Boot, Epic 5)
         ↓ X-API-KEY header authentication
         ↓ Return 201 Created / 409 Conflict
```

### Key Files
- `frontend/lib/internal-api.ts` — Server-only API client with `X-API-KEY` auth and `server-only` guard
- `frontend/lib/webhook-handlers.ts` — Event router and per-event handler functions
- `frontend/app/api/webhooks/clerk/route.ts` — Webhook route handler
- `frontend/vitest.config.ts` — Test configuration with `@/*` path alias
- `frontend/docs/webhook-setup.md` — Clerk Dashboard configuration guide

### Deviations from Original Plan
- **Frontend-only scope**: Previous PR (#4) was closed because it incorrectly included backend changes (entities, migrations, controllers). Re-implemented as frontend-only.
- **Idempotency scoped to frontend role**: The `processed_webhooks` table is a backend concern (Epic 5). Frontend's idempotency is: extract `svix-id`, pass to backend, handle 409 Conflict as success.
- **Local type interfaces**: Used local `OrgEventData`/`OrgDeletedEventData` interfaces instead of Clerk's `Extract<WebhookEvent, ...>` because TypeScript's `Extract` utility resolves to `never` when the discriminated union has combined string literal types (e.g., `'organization.created' | 'organization.updated'`).
- **vitest added**: Test infrastructure set up as part of this epic (originally planned for Epic 16).

---

## Epic 5: Tenant Provisioning

**Goal**: Implement the internal provisioning API that creates tenant schemas, runs Flyway migrations, and maintains org-schema mappings. Include startup migration runner for existing tenants.

**Dependencies**: Epic 1, Epic 6

**Scope**: Backend

**Estimated Effort**: L

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 5.1 | Create global schema migrations | **Done** | `V2__create_organizations.sql` (with provisioning_status tracking), `V3__create_processed_webhooks.sql` (webhook idempotency). `V1__create_org_schema_mapping.sql` already existed from Epic 6. |
| 5.2 | Create tenant schema migrations | **Done** | `V1__create_projects.sql` and `V2__create_documents.sql` in `db/migration/tenant/`. Documents has FK to projects, status tracking, S3 key. |
| 5.3 | Implement schema name generator | **Done** | `SchemaNameGenerator` uses UUID v3 (`nameUUIDFromBytes`) with DNS namespace salt. Deterministic `tenant_<12hex>` from Clerk org ID. Unit tests cover determinism, format, uniqueness. |
| 5.4 | Implement provisioning service | **Done** | `TenantProvisioningService` with idempotent steps: check existing → create org → mark IN_PROGRESS → generate schema → CREATE SCHEMA → insert mapping → Flyway tenant migrate → mark COMPLETED. No `@Transactional` (mixed datasource operations). |
| 5.5 | Configure dual data sources | **Done** | `DataSourceConfig` with two `HikariDataSource` beans via `@ConfigurationProperties` (Spring Boot 4 removed `DataSourceProperties`). `appDataSource` (primary, pooled) + `migrationDataSource` (for Flyway DDL). Uses `jdbc-url` (HikariCP native property). |
| 5.6 | Implement provisioning controller | **Done** | `POST /internal/orgs/provision` — 201 Created / 409 Conflict. Request validation with `@Valid` + `@NotBlank`. |
| 5.7 | Implement API key auth filter | **Done** | Already implemented in Epic 6 (`ApiKeyAuthFilter`). No changes needed. |
| 5.8 | Implement startup migration runner | **Done** | `TenantMigrationRunner` (`ApplicationRunner`) iterates all `OrgSchemaMapping` entries, runs tenant Flyway per schema. Continues on individual failure (doesn't block startup). |
| 5.9 | Add retry to provisioning | **Done** | Spring Retry (`@Retryable`) instead of Resilience4j (no Spring Boot 4 support). `maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2)`, retries on `ProvisioningException`. |
| 5.10 | Add provisioning integration tests | **Done** | Integration tests: full provisioning flow, idempotency (second provision returns same schema), different orgs get different schemas. Controller tests: 201, 409, 401, 400. All 29 tests pass (8 new + 21 existing). |

### Architecture Decisions
- **Dual DataSource**: `appDataSource` for Hibernate/JPA (pooled), `migrationDataSource` for Flyway DDL. Spring Boot 4 removed `DataSourceProperties` from `boot.autoconfigure.jdbc` — used `@ConfigurationProperties` directly on `HikariDataSource` beans with `jdbc-url` (not `url`).
- **Manual Flyway management**: Disabled Spring auto-Flyway (`spring.flyway.enabled: false`). `FlywayConfig` runs global migrations at startup via `@Bean(initMethod = "migrate")`. `TenantMigrationRunner` runs tenant migrations per schema.
- **Spring Retry over Resilience4j**: Resilience4j has no Spring Boot 4 support. Used `spring-retry` 2.0.12 with `@Retryable`.
- **No @Transactional on provisioning**: Service mixes two datasources (migrationDataSource for DDL, entityManager for JPA). Transaction management is per-step instead.
- **Identifier quoting for schema DDL**: Schema creation uses `"CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\""` to prevent SQL injection.
- **Testcontainers DynamicPropertyRegistrar**: `@ServiceConnection` only auto-configures default `spring.datasource.*`. Custom datasource beans need `DynamicPropertyRegistrar` to bridge container properties.

### Key Files
- `backend/src/main/java/.../config/DataSourceConfig.java` — Dual HikariDataSource beans
- `backend/src/main/java/.../config/FlywayConfig.java` — Manual global Flyway on startup
- `backend/src/main/java/.../provisioning/TenantProvisioningService.java` — Core provisioning logic
- `backend/src/main/java/.../provisioning/ProvisioningController.java` — Internal API endpoint
- `backend/src/main/java/.../provisioning/TenantMigrationRunner.java` — Startup migration runner
- `backend/src/main/java/.../provisioning/SchemaNameGenerator.java` — Deterministic schema naming

### Deviations from Original Plan
- **Spring Retry instead of Resilience4j** (task 5.9): Resilience4j lacks Spring Boot 4 support. Used `spring-retry` 2.0.12 with `@Retryable` annotation.
- **`@ConfigurationProperties` on HikariDataSource** (task 5.5): Spring Boot 4 removed `DataSourceProperties` from `boot.autoconfigure.jdbc`. Bound directly to `HikariDataSource` using HikariCP native property names (`jdbc-url`).
- **No separate `organizations` table migration**: Merged into existing global migrations as `V2__create_organizations.sql` (V1 was already taken by `org_schema_mapping` from Epic 6).
- **DynamicPropertyRegistrar for tests**: Testcontainers `@ServiceConnection` doesn't auto-configure custom datasource beans — added `DynamicPropertyRegistrar` in `TestcontainersConfiguration`.

---

## Epic 6: Multitenancy Backend

**Goal**: Configure Hibernate schema-per-tenant multitenancy, tenant context resolution from JWT, Spring Security with Clerk JWT validation, and RBAC role mapping.

**Dependencies**: Epic 1

**Estimated Effort**: L

**Status**: **Complete**

### Tasks

| ID | Task | Description | Acceptance Criteria | Estimate | Dependencies |
|----|------|-------------|---------------------|----------|--------------|
| 6.1 | Implement TenantContext | Create `TenantContext` class using `ThreadLocal<String>` to store/retrieve current tenant schema name. Include `clear()` method. | `TenantContext.setTenantId("tenant_xxx")` → `TenantContext.getTenantId()` returns `"tenant_xxx"` on the same thread; `clear()` removes it. | 1h | 1.4 |
| 6.2 | Implement CurrentTenantIdentifierResolver | Create `TenantIdentifierResolver` implementing `CurrentTenantIdentifierResolver<String>` and `HibernatePropertiesCustomizer`. Returns tenant ID from `TenantContext` or `"public"` as default. | Hibernate calls `resolveCurrentTenantIdentifier()` and gets the schema name from `TenantContext`. | 2h | 6.1 |
| 6.3 | Implement MultiTenantConnectionProvider | Create `SchemaMultiTenantConnectionProvider` implementing `MultiTenantConnectionProvider<String>` and `HibernatePropertiesCustomizer`. `getConnection()` sets `search_path`; `releaseConnection()` resets to `public`. | Connection checkout sets `search_path` to tenant schema; connection release resets to `public`; verified with integration test. | 3h | 6.1 |
| 6.4 | Configure Hibernate for multitenancy | Register both resolver and connection provider via `HibernatePropertiesCustomizer`. Ensure Hibernate auto-detects schema multitenancy mode. Disable `ddl-auto`. | Hibernate initializes in multi-tenant mode; queries execute against the correct tenant schema. | 2h | 6.2, 6.3 |
| 6.5 | Implement Clerk JWT validation filter | Create `ClerkJwtAuthFilter` (Spring Security filter) that: extracts `Authorization: Bearer <jwt>`, validates signature against Clerk JWKS endpoint, checks expiry and issuer, extracts `sub`, `org_id`, `org_role`. Use Spring Security's `JwtDecoder` with Clerk's JWKS URI. | Valid Clerk JWT → authentication set in SecurityContext; expired/invalid JWT → 401; missing JWT → 401. | 4h | 1.4 |
| 6.6 | Implement Clerk role converter | Create `ClerkRoleConverter` that maps Clerk org roles to Spring authorities: `org:owner` → `ROLE_ORG_OWNER`, `org:admin` → `ROLE_ORG_ADMIN`, `org:member` → `ROLE_ORG_MEMBER`. Wire into JWT auth filter. | JWT with `org_role: "org:admin"` → SecurityContext contains `ROLE_ORG_ADMIN` authority. | 2h | 6.5 |
| 6.7 | Implement tenant context filter | Create `TenantFilter` (servlet filter, ordered after JWT filter) that extracts `org_id` from the authenticated principal, looks up schema name from `org_schema_mapping` (with caching), and sets `TenantContext`. | After filter chain: `TenantContext.getTenantId()` returns the correct `tenant_xxx` schema name for the authenticated org. | 3h | 6.1, 6.5 |
| 6.8 | Configure Spring Security filter chain | Wire filter order: `ClerkJwtAuthFilter` → `TenantFilter` → `TenantLoggingFilter` → controller. Configure `SecurityFilterChain` with endpoint authorization rules. Exclude `/internal/**` from JWT auth (uses API key instead). | `/api/**` requires valid JWT; `/internal/**` requires valid API key; role-based access enforced per endpoint. | 3h | 6.5, 6.6, 6.7 |
| 6.9 | Implement tenant logging filter | Create `TenantLoggingFilter` (ordered after tenant filter) that sets MDC fields: `tenantId`, `userId`, `requestId`. Clear MDC on response. | Every log line within a request includes tenantId, userId, and requestId. MDC cleared after request completes. | 2h | 6.7 |
| 6.10 | Configure structured JSON logging | Set `logging.structured.format.console=ecs` in `application.yml`. Verify JSON output includes MDC fields. | Log output is structured JSON (ECS format); MDC fields (`tenantId`, `userId`, `requestId`) appear in every log entry. | 1h | 6.9 |
| 6.11 | Add multitenancy integration tests | Test: set TenantContext to schema A, query → get schema A data; set to schema B, query → get schema B data; verify no cross-schema leakage. | Tests prove tenant isolation: data written in schema A is not visible in schema B and vice versa. | 3h | 6.4, 6.7 |

---

## Epic 7: Core API — Projects

**Goal**: Implement project CRUD endpoints with tenant-scoped data access and role-based authorization.

**Dependencies**: Epic 6

**Scope**: Backend

**Estimated Effort**: M

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 7.1 | Create Project entity | **Done** | `Project.java` — JPA entity mapping to tenant `projects` table. UUID id, name, description, createdBy, createdAt/updatedAt (Instant). No `@Table(schema=...)` — Hibernate resolves tenant schema dynamically. |
| 7.2 | Create ProjectRepository | **Done** | `ProjectRepository.java` — extends `JpaRepository<Project, UUID>`. No custom queries needed. |
| 7.3 | Implement ProjectService | **Done** | `ProjectService.java` — thin service layer with `@Transactional` (readOnly for reads). CRUD methods delegate to repository. `createdBy` passed from controller (extracted from JWT `sub`). |
| 7.4 | Implement ProjectController | **Done** | `ProjectController.java` — 5 REST endpoints. Nested record DTOs (`CreateProjectRequest`, `UpdateProjectRequest`, `ProjectResponse`). Returns `ResponseEntity` with explicit status codes. `ProblemDetail` (RFC 9457) for 404 errors. |
| 7.5 | Add input validation | **Done** | `@NotBlank` + `@Size(max=255)` on name, `@Size(max=2000)` on description. `@Valid @RequestBody` triggers Bean Validation. Returns 400 with field-level errors. |
| 7.6 | Add role-based authorization | **Done** | `@PreAuthorize` annotations: MEMBER+ for GET, ADMIN+ for POST/PUT, OWNER for DELETE. No changes to `SecurityConfig` — `@EnableMethodSecurity` already configured. |
| 7.7 | Add project endpoint tests | **Done** | 19 integration tests (MockMvc + Testcontainers, `@ActiveProfiles("test")`). Covers: CRUD happy path, validation errors, 404 not found, RBAC (member/admin/owner), tenant isolation (cross-tenant invisible), unauthenticated access. 48 total tests pass. |

### Key Files
- `backend/src/main/java/.../project/Project.java` — JPA entity
- `backend/src/main/java/.../project/ProjectRepository.java` — Spring Data JPA repository
- `backend/src/main/java/.../project/ProjectService.java` — Service layer
- `backend/src/main/java/.../project/ProjectController.java` — REST controller with DTOs
- `backend/src/test/java/.../project/ProjectIntegrationTest.java` — Integration tests

### Deviations from Original Plan
- **Integration tests only (no unit tests)**: Used MockMvc + Testcontainers integration tests instead of separate unit tests for controller and service. Integration tests exercise the full stack (security filters → tenant resolution → CRUD → DB) providing higher confidence than isolated unit tests.
- **`@ActiveProfiles("test")` for RBAC testing**: Required to activate `SecurityConfig` (which is `@Profile("!local")`). Mock JWT `jwt()` post-processor bypasses `JwtAuthenticationConverter`, so authorities must be set explicitly via `.authorities()`.
- **`Location` header for ID extraction in tests**: `ObjectMapper` bean unavailable in test context. Used `Location` header from 201 responses and `jsonPath()` assertions instead.

---

## Epic 8: Core API — Documents

**Goal**: Implement document metadata endpoints, presigned URL generation for uploads/downloads, and upload confirmation flow.

**Dependencies**: Epic 7, Epic 9

**Scope**: Backend

**Estimated Effort**: M

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 8.1 | Create Document entity | **Done** | JPA entity with UUID id, projectId (FK), fileName, contentType, size, s3Key, status enum (PENDING/UPLOADED/FAILED), uploadedBy, uploadedAt, createdAt. `assignS3Key()` method for post-save S3 key assignment. |
| 8.2 | Create DocumentRepository | **Done** | `JpaRepository<Document, UUID>` with `findByProjectId(UUID projectId)`. |
| 8.3 | Implement DocumentService | **Done** | `listDocuments`, `initiateUpload`, `confirmUpload`, `getPresignedDownloadUrl`. Validates project exists in current tenant schema. Uses `S3PresignedUrlService` for presigned URL generation. |
| 8.4 | Implement upload-init endpoint | **Done** | `POST /api/projects/{projectId}/documents/upload-init` — returns 201 with `{documentId, presignedUrl, expiresInSeconds}`. S3 key format: `org/{orgId}/project/{projId}/{docId}`. |
| 8.5 | Implement upload-confirm endpoint | **Done** | `POST /api/documents/{documentId}/confirm` — transitions PENDING → UPLOADED. Idempotent (re-confirm on UPLOADED returns 200). 404 on non-existent. |
| 8.6 | Implement document listing endpoint | **Done** | `GET /api/projects/{projectId}/documents` — returns array of document metadata. 404 if project not found in tenant. |
| 8.7 | Implement presigned download endpoint | **Done** | `GET /api/documents/{documentId}/presign-download` — returns presigned GET URL with 1hr expiry. 400 if not UPLOADED, 404 if not found. |
| 8.8 | Add authorization to document endpoints | **Done** | All endpoints require `ROLE_ORG_MEMBER+` via `@PreAuthorize`. Cross-tenant project IDs return 404 (Hibernate schema isolation). |
| 8.9 | Add document endpoint tests | **Done** | 17 integration tests: upload flow (init → confirm), idempotent confirm, listing, presigned download, 400 for pending, RBAC, validation (fileName, contentType, size), cross-tenant isolation. 70 total tests pass. |

### Key Files
- `backend/src/main/java/.../document/Document.java` — JPA entity with status enum
- `backend/src/main/java/.../document/DocumentRepository.java` — Spring Data JPA repository
- `backend/src/main/java/.../document/DocumentService.java` — Business logic + S3 presigned URL coordination
- `backend/src/main/java/.../document/DocumentController.java` — REST endpoints with RBAC
- `backend/src/test/java/.../document/DocumentIntegrationTest.java` — 17 integration tests

---

## Epic 9: S3 Integration

**Goal**: Configure AWS SDK for S3 presigned URL generation, with LocalStack support for local development.

**Dependencies**: Epic 1

**Scope**: Backend

**Estimated Effort**: S (single slice — 4 tasks)

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 9.1 | Add AWS SDK dependencies | **Done** | AWS SDK v2 BOM 2.31.23, `software.amazon.awssdk:s3` (compile), `testcontainers-localstack` (test). Maven, not Gradle. No `sts` or `s3-transfer-manager` needed for presigned URLs. |
| 9.2 | Create S3 configuration | **Done** | `S3Config.java` with `@ConfigurationProperties` records (`S3Properties`, `AwsCredentialsProperties`). `S3Client` + `S3Presigner` beans with `destroyMethod="close"`. Conditional LocalStack endpoint override when `aws.s3.endpoint` is set. |
| 9.3 | Implement S3PresignedUrlService | **Done** | `generateUploadUrl(orgId, projectId, documentId, contentType)` → presigned PUT with 1hr expiry and content-type constraint. `generateDownloadUrl(s3Key)` → presigned GET with 1hr expiry and key format validation. Key format: `org/{orgId}/project/{projectId}/{documentId}`. |
| 9.4 | Add S3 integration tests | **Done** | 5 integration tests with Testcontainers LocalStack. End-to-end upload/download via presigned URLs, key format verification, org isolation, key validation rejection. 53 total tests pass (48 existing + 5 new). |

### Key Files
- `backend/src/main/java/.../config/S3Config.java` — S3Client + S3Presigner beans with `@ConfigurationProperties` records
- `backend/src/main/java/.../s3/S3PresignedUrlService.java` — Presigned URL generation service
- `backend/src/test/java/.../s3/S3PresignedUrlServiceTest.java` — Integration tests with LocalStack

### Deviations from Original Plan
- **Maven instead of Gradle**: Task 9.1 description referenced Gradle, but project uses Maven. Added AWS SDK v2 BOM in `<dependencyManagement>`.
- **No `sts` or `s3-transfer-manager`**: Not needed for presigned URL generation. `s3` module includes the presigner.
- **S3 key format validation**: Added regex validation (`^org/[^/]+/project/[^/]+/[^/]+$`) on `generateDownloadUrl` to prevent path traversal — not in original spec but added during code review.

---

## Epic 10: Frontend — Dashboard & Projects

**Goal**: Build the dashboard layout and project management UI with org-scoped data fetching from Spring Boot.

**Dependencies**: Epic 3, Epic 7

**Scope**: Frontend

**Estimated Effort**: M

**Status**: **Complete**

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **10A** | 10.1, 10.2 | App shell layout (sidebar, header, responsive), typed API client | **Done** |
| **10B** | 10.3, 10.4, 10.8 | Projects list page, create dialog, dashboard page | **Done** |
| **10C** | 10.5, 10.6, 10.7 | Project detail page, edit functionality, delete with confirmation | **Done** |

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 10.1 | Build app shell layout | **Done** | Extracted sidebar into `DesktopSidebar` (desktop) and `MobileSidebar` (Sheet drawer) client components. Active route highlighting via `usePathname()`. 4 nav items (Dashboard, Projects, Team, Settings). Shadcn sidebar CSS variables. Settings placeholder page. |
| 10.2 | Implement API client | **Done** | Enhanced `lib/api.ts` with `server-only` guard, RFC 9457 ProblemDetail parsing, `handleApiError()` utility (401→redirect, 404→notFound), `api.*` convenience methods. Created `lib/types.ts` with Project, Document, ProblemDetail DTO interfaces. |
| 10.3 | Build projects list page | **Done** | Server component fetching `GET /api/projects`. Responsive card grid (`1/2/3` columns). Empty state with CTA. Role-based "New Project" button (admin/owner). `handleApiError()` for 401/404. |
| 10.4 | Build project create dialog | **Done** | Shadcn Dialog with name (Input) + description (Textarea) fields. Server Action calling `POST /api/projects`. Inline validation errors. `revalidatePath` refreshes projects list and dashboard. Form/error state reset on reopen. |
| 10.5 | Build project detail page | **Done** | Server component at `projects/[id]/page.tsx`. Fetches `GET /api/projects/{id}`. Shows name, description, created date. Back link to projects list. Role-based Edit/Delete buttons. Documents placeholder for Epic 11. |
| 10.6 | Build project edit functionality | **Done** | `EditProjectDialog` client component with form pre-population. `updateProject` server action with `auth()` role check (admin+). Revalidates projects list, detail, and dashboard. |
| 10.7 | Build project delete functionality | **Done** | `DeleteProjectDialog` using Shadcn AlertDialog for destructive confirmation. `deleteProject` server action with `auth()` role check (owner only). Redirects to projects list after deletion. |
| 10.8 | Implement dashboard page | **Done** | Org name, project count stat card, recent projects list (top 5 sorted by `createdAt` desc), quick-action buttons (New Project, View Projects, Manage Team). Role-based "New Project" visibility. |

### Key Files (10A)
- `frontend/components/desktop-sidebar.tsx` — Desktop sidebar with active route highlighting
- `frontend/components/mobile-sidebar.tsx` — Mobile Sheet-based sidebar with hamburger trigger
- `frontend/lib/nav-items.ts` — Shared navigation item config (4 items)
- `frontend/components/ui/sheet.tsx` — Shadcn Sheet component (Radix Dialog drawer)
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — Settings placeholder page
- `frontend/lib/types.ts` — TypeScript interfaces for Project, Document, ProblemDetail DTOs
- `frontend/lib/api.ts` — Enhanced server-side API client with ProblemDetail parsing and error utilities

### Key Files (10B)
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Projects list page (server component, card grid)
- `frontend/app/(app)/org/[slug]/projects/actions.ts` — Server Action for `POST /api/projects`
- `frontend/components/projects/create-project-dialog.tsx` — Client dialog with form, validation, state reset
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — Dashboard with stats, recent projects, quick actions
- `frontend/components/ui/label.tsx` — Shadcn Label component
- `frontend/components/ui/textarea.tsx` — Shadcn Textarea component

### Key Files (10C)
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Project detail page (server component, role-based actions)
- `frontend/components/projects/edit-project-dialog.tsx` — Edit dialog with form pre-population
- `frontend/components/projects/delete-project-dialog.tsx` — AlertDialog destructive confirmation
- `frontend/components/ui/alert-dialog.tsx` — Shadcn AlertDialog component

### Architecture Decisions (10C)
- **AlertDialog for destructive actions**: Used Shadcn AlertDialog (not Dialog) for delete confirmation — prevents accidental dismissal via outside click or Escape key.
- **Server Action auth checks**: `updateProject` and `deleteProject` call `auth()` to verify role before API call — defense-in-depth since Server Actions are HTTP POST endpoints that can be invoked directly.
- **Trigger-as-children pattern**: Edit/Delete dialogs accept `children` as trigger buttons via `asChild`, keeping layout control in the server component while client components handle interactivity.
- **Redirect after delete**: `deleteProject` calls `revalidatePath` then `redirect` — cache is invalidated before navigation so the projects list shows fresh data.

### Architecture Decisions (10B)
- **Server Actions for mutations**: `api.ts` is `server-only`, so client components call server actions which internally call `api.post()`. `revalidatePath` invalidates both `/projects` and `/dashboard` after creation.
- **Card grid over table**: Projects use responsive card grid (`grid-cols-1 sm:2 lg:3`) — more visual and mobile-friendly than table layout used for team members.
- **Role-based UI gating**: "New Project" button visible only to admin/owner. Backend still enforces RBAC (403 for members) — defense in depth.
- **Form state management**: `useRef` for native form reset + `handleOpenChange` clears error state on dialog reopen — prevents stale form values and error messages.

### Architecture Decisions (10A)
- **Server/client split**: Layout stays a server component (calls `await auth()` and `await params`). Sidebar extracted into two client components (`DesktopSidebar`, `MobileSidebar`) that use `usePathname()` for active route detection and `useState()` for mobile toggle.
- **Shadcn Sheet for mobile**: Uses Radix Dialog-based Sheet component instead of custom drawer — provides accessibility (focus trapping, keyboard dismiss), animations, and overlay behavior for free.
- **Sidebar CSS variables**: Replaced hard-coded `bg-neutral-50 dark:bg-neutral-950` with Shadcn sidebar tokens (`bg-sidebar`, `bg-sidebar-accent`, etc.) already defined in `globals.css`.
- **No client-side API wrapper**: Client components use Server Actions that call the server-side `api` internally. Standard Next.js 16 pattern.
- **Backward-compatible API alias**: `apiClient` kept as alias for `apiRequest` so existing webhook handler code is not broken.

---

## Epic 11: Frontend — Documents

**Goal**: Build document list, file upload with progress tracking, and download functionality.

**Dependencies**: Epic 10, Epic 8

**Scope**: Frontend

**Estimated Effort**: M

**Status**: **Complete**

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **11A** | 11.1, 11.2 | Document list component, drag-and-drop upload with presigned URL flow | **Done** |
| **11B** | 11.3, 11.4, 11.5, 11.6 | Upload progress tracking, download functionality, error handling, file validation | **Done** |

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 11.1 | Build document list component | **Done** | `DocumentsPanel` client component with Shadcn Table. File-type icons (lucide), size formatting, status badges (UPLOADED/PENDING/FAILED), upload date. Empty state with upload CTA. Server component fetches `GET /api/projects/{id}/documents`. |
| 11.2 | Build file upload component | **Done** | `FileUploadZone` drag-and-drop + click-to-browse. 3-phase upload: server action `initiateUpload` → XHR PUT to presigned S3 URL → server action `confirmUpload`. Upload state managed via `useReducer`. Multiple concurrent uploads supported. |
| 11.3 | Add upload progress tracking | **Done** | `XMLHttpRequest.upload.onprogress` for real-time percentage. Shadcn `<Progress>` bar. `UploadProgressItem` component with state machine: validating → initiating → uploading → confirming → complete / error. Indeterminate progress for server-side phases. |
| 11.4 | Implement download functionality | **Done** | `DownloadButton` per document (UPLOADED only). Server action `getDownloadUrl` calls presign-download API. Anchor element triggers browser download. Loading spinner during fetch, auto-clearing error on failure. |
| 11.5 | Handle upload errors | **Done** | Error display per upload item with `text-destructive`. Retry button re-initiates from scratch (new presigned URL). Validation errors (wrong type, too large) show dismiss only. XHR abort handler prevents memory leak. `useEffect` cleanup aborts in-flight uploads on unmount. |
| 11.6 | Add file type and size validation | **Done** | `validateFile()` in `lib/upload-validation.ts`. Max 100MB, 17 allowed MIME types (PDF, Office, images, text, CSV, archives). Extension-to-MIME fallback when `file.type` is empty. Synchronous validation before any server call. |

### Key Files
- `frontend/components/documents/documents-panel.tsx` — Main documents client component with upload state management (`useReducer`), document table, and upload orchestration
- `frontend/components/documents/file-upload-zone.tsx` — Drag-and-drop zone with visual feedback and accessibility
- `frontend/components/documents/upload-progress-item.tsx` — Per-file progress bar, status indicators, retry/remove
- `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` — Server actions: `initiateUpload`, `confirmUpload`, `getDownloadUrl`
- `frontend/lib/upload-validation.ts` — File validation with MIME type and extension checking
- `frontend/lib/format.ts` — `formatFileSize()` utility

### Architecture Decisions
- **Single client component for documents panel**: Upload zone and document list are tightly coupled — uploading adds items to the list, in-progress uploads display alongside completed ones. A single `useReducer` manages the upload queue.
- **XMLHttpRequest for S3 upload**: `fetch()` does not expose upload progress. `XMLHttpRequest.upload.onprogress` is the only browser API for upload progress tracking. XHR is used only for the S3 PUT; all other calls go through server actions.
- **Server Actions as API gateway**: `initiateUpload` returns the presigned URL to the client. The Next.js server never touches file bytes — browser uploads directly to S3.
- **Retry re-initiates from scratch**: On retry, a new `documentId` and presigned URL are obtained. Old PENDING records are harmless orphans. Avoids complex bookkeeping about expired URLs.
- **Non-fatal document fetch**: If project loads but document listing fails, the page shows an empty documents section rather than erroring out.
- **fileMapRef for stale-closure-free retry**: File objects stored in a `useRef` map so `handleRetry` doesn't depend on `uploads` reducer state, avoiding stale closures and unnecessary re-renders.

### Deviations from Original Plan
- **No separate `DocumentDownloadButton` file**: Download button implemented as an inline function component within `documents-panel.tsx` since it's small and tightly coupled to the panel's server action imports.
- **`mimeType` stored in UploadItem**: Added `mimeType` field to carry the resolved MIME type from validation through the upload flow, since `file.type` can be empty for some file extensions.
- **`useEffect` cleanup for XHR abort**: Added after code review — aborts all in-flight XMLHttpRequest objects when the component unmounts to prevent React state updates on unmounted components.

---

## Epic 12: Frontend — Team Management

**Goal**: Build team management UI using Clerk's organization components — member list, invitations, and role display.

**Dependencies**: Epic 3

**Scope**: Frontend

**Estimated Effort**: S (single slice — 5 tasks, mostly Clerk components)

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 12.1 | Build team management page | **Done** | `app/(app)/org/[slug]/team/page.tsx` — server component with Shadcn Tabs (Members, Invitations). Reads `orgRole` via `auth()` for role-based visibility. |
| 12.2 | Build member list | **Done** | `components/team/member-list.tsx` — client component using `useOrganization({ memberships: { infinite: true } })`. Shadcn Table with role badges (Owner/Admin/Member). Load-more pagination. |
| 12.3 | Build invitation form | **Done** | `components/team/invite-member-form.tsx` — client component with email input + role selector (Member/Admin). Uses `organization.inviteMember()`. Only rendered for admin+. Success/error feedback. |
| 12.4 | Build pending invitations list | **Done** | `components/team/pending-invitations.tsx` — client component using `useOrganization({ invitations })`. Revoke via `invitation.revoke()` with revalidation. Pagination (Previous/Next). |
| 12.5 | Add role-based visibility | **Done** | Server component passes `isAdmin` prop. Invite form hidden for `org:member`. Revoke buttons hidden for `org:member`. Member list always visible (read-only for members). |

### Architecture Decisions
- **Custom components over Clerk pre-built**: Used Clerk's `useOrganization()` hook with custom Shadcn-styled components instead of `<OrganizationMembershipList />`. Gives full styling control while Clerk remains source of truth for data.
- **Server/client split**: Team page is a server component (reads `orgRole` via `auth()`). Interactive children (member list, invite form, invitations) are client components using `useOrganization()` hook.
- **No backend integration**: Clerk handles all membership/invitation CRUD directly — no Spring Boot API calls needed.

### Key Files
- `frontend/app/(app)/org/[slug]/team/page.tsx` — Team page with tabs and role-based rendering
- `frontend/components/team/member-list.tsx` — Organization member table
- `frontend/components/team/invite-member-form.tsx` — Invitation form (admin+ only)
- `frontend/components/team/pending-invitations.tsx` — Pending invitation list with revoke

---

## Epic 13: Containerization

**Goal**: Create production-ready Docker images for both services and a full Docker Compose stack for local development.

**Dependencies**: Epic 1

**Estimated Effort**: S

**Status**: **Complete**

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 13.1 | Create Next.js Dockerfile | **Done** | Multi-stage build: `node:20-alpine`, pnpm, standalone output. Non-root `nextjs` user. `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` passed as build arg. Image ~208MB. |
| 13.2 | Create Spring Boot Dockerfile | **Done** | Multi-stage build: `eclipse-temurin:25-jdk` for build, `eclipse-temurin:25-jre-alpine` for runtime. Spring Boot 4 layered JAR extraction (`java -Djarmode=tools extract --layers`). Non-root `spring` user. Image ~289MB. |
| 13.3 | Create full Docker Compose stack | **Done** | Extended `compose/docker-compose.yml` with `backend` and `frontend` services. Backend depends on postgres + localstack (healthchecks), frontend depends on backend (healthcheck). Environment variables configured for inter-service communication. |
| 13.4 | Optimize Docker images | **Done** | `.dockerignore` files for both `frontend/` and `backend/`. Dependency install cached separately from code changes. |
| 13.5 | Document local Docker setup | **Done** | README.md updated with "Quick Start (Full Docker Stack)" section including setup, build, verify, and troubleshooting steps. |

### Architecture Decisions
- **Standalone output for Next.js**: `output: "standalone"` in `next.config.ts` produces a minimal server (`server.js`) without requiring full `node_modules` at runtime.
- **Clerk build arg**: `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` must be passed as `--build-arg` because Next.js inlines `NEXT_PUBLIC_*` variables at build time. Clerk validates key format during static page generation.
- **Spring Boot layered JAR**: Spring Boot 4 uses `java -Djarmode=tools -jar app.jar extract --layers` (changed from `layertools` in Spring Boot 3). Layers: `dependencies/`, `spring-boot-loader/`, `snapshot-dependencies/`, `application/`. Entry point: `org.springframework.boot.loader.launch.JarLauncher`.
- **Docker Compose env**: `.env.example` updated with Clerk keys and `INTERNAL_API_KEY`. Users copy to `.env` and fill in their Clerk keys before running `docker compose up`.

---

## Epic 14: AWS Infrastructure

**Goal**: Define all AWS infrastructure in Terraform — VPC, ECS, ALB, ECR, Secrets Manager, S3, IAM.

**Dependencies**: Epic 13

**Scope**: Infra

**Estimated Effort**: XL

**Status**: **Complete**

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **14A** | 14.1, 14.2, 14.3 | Terraform project structure, VPC (2 public + 2 private subnets), security groups |
| **14B** | 14.4, 14.5, 14.6 | ALB (public + internal), ECR repositories, ECS cluster + Fargate services |
| **14C** | 14.7, 14.8, 14.9 | S3 bucket, Secrets Manager, IAM roles (task execution + task) |
| **14D** | 14.10, 14.11, 14.12 | Route 53 + ACM certificate, auto-scaling policies, deploy dev environment |

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 14.1 | Set up Terraform project structure | **Done** | 11 module dirs (`vpc`, `security-groups`, `ecs`, `alb`, `ecr`, `s3`, `secrets`, `iam`, `monitoring`, `dns`, `autoscaling`), 3 environment configs (`dev/`, `staging/`, `prod/`). S3 backend with DynamoDB locking. |
| 14.2 | Create VPC module | **Done** | VPC with non-overlapping CIDRs per env (dev=`10.0.0.0/16`, staging=`10.1.0.0/16`, prod=`10.2.0.0/16`). 2 public + 2 private subnets across 2 AZs, IGW, 2 NAT gateways, route tables. All resources tagged. |
| 14.3 | Create security groups | **Done** | `sg-public-alb` (80+443 from internet), `sg-internal-alb` (8080 from frontend SG), `sg-frontend` (3000 from public ALB), `sg-backend` (8080 from both ALBs). Uses standalone `aws_vpc_security_group_ingress_rule` resources. |
| 14.4 | Create ALB module | **Done** | Public ALB with conditional HTTPS:443 (when ACM cert provided) + HTTP:80 (redirect or forward). Internal ALB on HTTP:8080. Path-based routing: `/api/*` → backend, `/*` → frontend, `/internal/*` → backend (internal). 3 target groups (IP type for Fargate). Health checks on `/` and `/actuator/health`. |
| 14.5 | Create ECR module | **Done** | `docteams-{env}-frontend` and `docteams-{env}-backend` repos. Immutable tags, scan on push, AES256 encryption. Lifecycle: keep last 10 images, expire untagged after 1 day. |
| 14.6 | Create ECS module | **Done** | Fargate cluster with Container Insights. Frontend task def (512/1024, port 3000), backend task def (1024/2048, port 8080). Services: desired=2, circuit breaker with rollback, rolling deploy (min 100%, max 200%). Backend registers with both public and internal target groups. CloudWatch log groups in separate monitoring module (configurable retention: dev=7d, staging=14d, prod=30d). |
| 14.7 | Create S3 module | **Done** | Bucket `docteams-{env}` with versioning, all public access blocked, AES256 SSE, CORS for presigned PUT/GET, lifecycle to abort incomplete multipart uploads after 7 days. |
| 14.8 | Create Secrets Manager module | **Done** | 6 secrets via `for_each`: database-url, database-migration-url, clerk-secret-key, clerk-webhook-secret, clerk-publishable-key, internal-api-key. Named `docteams/{env}/{key}`. Placeholder values with `ignore_changes = [secret_string]` lifecycle. Recovery window: dev=0, staging=7, prod=30 days. |
| 14.9 | Create IAM module | **Done** | Execution role: ECR pull, CloudWatch logs, Secrets Manager read — all scoped to specific ARNs (except `ecr:GetAuthorizationToken` which requires `*`). Backend task role: S3 GetObject/PutObject/DeleteObject + ListBucket/GetBucketLocation. Frontend task role: minimal (ECS requires one). |
| 14.10 | Create Route 53 and ACM resources | **Done** | Fully conditional via `create_dns` boolean (default: false). ACM cert with DNS validation, Route 53 alias record → public ALB. Usable without a domain. |
| 14.11 | Configure auto-scaling | **Done** | `for_each` over frontend + backend services. CPU target tracking (70%), memory target tracking (80%). Min 2, max configurable per env (dev=4, staging=6, prod=10). Cooldowns: scale-in 300s, scale-out 60s. |
| 14.12 | Wire dev environment | **Done** | All modules wired in dev/staging/prod `main.tf`. Variables with env-specific defaults. `terraform validate` passes for all 3 environments. `terraform.tfvars.example` updated. Actual `terraform apply` is manual (requires AWS credentials). |

### Architecture Decisions
- **Non-overlapping VPC CIDRs**: Each environment gets a distinct `/16` block (dev=`10.0`, staging=`10.1`, prod=`10.2`) to enable future VPC peering without CIDR conflicts.
- **Standalone SG ingress rules**: Uses `aws_vpc_security_group_ingress_rule` resources instead of inline `ingress {}` blocks — the AWS provider v5.x recommended pattern to avoid perpetual diffs.
- **HTTP:80 on public ALB**: SG permits both 80 and 443 inbound so the ALB listener can redirect HTTP→HTTPS.
- **Provider default_tags**: Tags (`Project`, `Environment`, `ManagedBy`) applied at the provider level via `default_tags` block, so every resource inherits them automatically.
- **Conditional HTTPS**: ALB supports both HTTPS (with ACM cert from DNS module) and HTTP-only (without cert) for dev flexibility.
- **Secrets `ignore_changes`**: Terraform creates secrets with placeholders; operators update values manually. Terraform never overwrites real values.
- **Separate auto-scaling module**: Keeps ECS module focused on compute; scaling policies are a distinct concern.
- **DNS module fully conditional**: `create_dns = false` by default — infrastructure is usable without a registered domain.
- **Backend dual target groups**: ECS backend service registers with both public ALB (for `/api/*`) and internal ALB (for `/internal/*`).

### Key Files
- `infra/modules/vpc/main.tf` — VPC, subnets, IGW, NAT gateways, route tables
- `infra/modules/security-groups/main.tf` — 4 security groups with least-privilege rules
- `infra/modules/alb/main.tf` — Public + internal ALBs, conditional HTTPS, path-based routing
- `infra/modules/ecr/main.tf` — Frontend + backend repos with lifecycle policies
- `infra/modules/ecs/main.tf` — Fargate cluster, task definitions, services
- `infra/modules/s3/main.tf` — Document bucket with versioning, CORS, lifecycle
- `infra/modules/secrets/main.tf` — 6 Secrets Manager entries with `for_each`
- `infra/modules/iam/main.tf` — Execution role + task roles with least-privilege
- `infra/modules/monitoring/main.tf` — CloudWatch log groups
- `infra/modules/dns/main.tf` — Conditional ACM + Route 53
- `infra/modules/autoscaling/main.tf` — CPU/memory target tracking policies
- `infra/environments/{dev,staging,prod}/main.tf` — Environment root configs wiring all modules

### Deviations from Original Plan
- **Added `security-groups`, `monitoring`, `dns`, `autoscaling` as separate modules**: Original spec embedded some in VPC/ECS. Separate modules give better separation of concerns.
- **Non-overlapping CIDRs**: Original spec used `10.0.0.0/16` for all environments. Changed to unique CIDRs per env after code review.
- **Per-environment ECR repos**: Named `docteams-{env}-frontend` instead of shared repos, consistent with per-environment Terraform state pattern.
- **Added `clerk-publishable-key` secret**: Not in original spec but needed by frontend ECS task definition.
- **Task 14.12 scoped to validation**: Actual `terraform apply` requires AWS credentials and manual review — not run as part of the PR.

---

## Epic 15: Deployment Pipeline

**Goal**: Automate build, test, image push, and ECS deployment via GitHub Actions with environment promotion.

**Dependencies**: Epic 13, Epic 14

**Scope**: Infra

**Estimated Effort**: L

**Status**: **Complete**

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **15A** | 15.1, 15.2, 15.3, 15.6 | Docker build/push workflow, ECS deploy action, dev deploy, GitHub secrets + environments | **Done** |
| **15B** | 15.4, 15.5, 15.7 | Staging/prod deployment workflows, rollback procedure | **Done** |

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 15.1 | Create Docker build and push workflow | **Done** | `build-and-push.yml` — path-filtered builds (`dorny/paths-filter`), Docker Buildx with GHA layer caching, SHA + latest tags, parallel frontend/backend builds. Triggers on push to main. |
| 15.2 | Create ECS deploy action | **Done** | `.github/actions/ecs-deploy/action.yml` — composite action: describe task def → update image via `jq` → strip metadata → register new revision → update service → wait for stability with error diagnostics. |
| 15.3 | Create dev deployment workflow | **Done** | `deploy-dev.yml` — triggered by `workflow_run` on Build & Push success. Uses `head_sha` for image tag consistency. Parallel frontend/backend deploy, then ALB health check smoke tests (graceful skip if ALB not found). |
| 15.4 | Create staging deployment workflow | **Done** | `deploy-staging.yml` — manual `workflow_dispatch` with git ref input. Builds images for staging ECR, deploys to `docteams-staging` ECS via reusable composite action. GitHub environment `staging` (1 approval). Smoke tests on ALB. |
| 15.5 | Create production deployment workflow | **Done** | `deploy-prod.yml` — manual `workflow_dispatch` with git ref + `deploy-prod` confirmation. Builds images for prod ECR, deploys to `docteams-prod` ECS. GitHub environment `prod` (2 approvals + 5-min wait). Smoke tests on ALB. |
| 15.6 | Configure GitHub secrets and environments | **Done** | `docs/github-environments-setup.md` — documents required secrets (AWS creds, Clerk key), GitHub environments (dev/staging/prod) with protection rules, IAM permissions, OIDC migration guide. Updated with staging/prod workflow details. |
| 15.7 | Add rollback procedure | **Done** | `rollback.yml` — manual `workflow_dispatch` with environment/service/confirmation inputs. Reverts ECS service to previous task definition revision. `docs/rollback-procedure.md` — operational runbook with CLI fallback and post-rollback checklist. |

### Key Files (15A)
- `.github/workflows/build-and-push.yml` — Docker build & push to ECR on merge to main
- `.github/actions/ecs-deploy/action.yml` — Reusable ECS deploy composite action
- `.github/workflows/deploy-dev.yml` — Auto-deploy to dev with smoke tests
- `docs/github-environments-setup.md` — GitHub environments and secrets setup guide

### Architecture (15A)
```
Push to main
  ├─→ CI (ci.yml) — lint, test, build
  └─→ Build & Push (build-and-push.yml)
        ├─ Detect changes (dorny/paths-filter)
        ├─ Build frontend → ECR (SHA + latest tags, Buildx cache)
        └─ Build backend  → ECR (SHA + latest tags, Buildx cache)
              └─→ Deploy Dev (deploy-dev.yml, workflow_run trigger)
                    ├─ Deploy frontend → ECS update + wait
                    ├─ Deploy backend  → ECS update + wait
                    └─ Smoke test → ALB health checks
```

### Deviations from Original Plan
- **Task 15.3 pulled into 15A**: Dev deployment workflow is the natural consumer of 15.1 + 15.2. Including it completes the end-to-end pipeline. 15B now only covers staging/prod/rollback.
- **Composite action over reusable workflow**: Used a composite action (`.github/actions/ecs-deploy/`) instead of a reusable workflow (`workflow_call`). Composite actions run as steps within a job — more flexible and lower overhead.
- **AWS CLI over `aws-actions/amazon-ecs-deploy-task-definition`**: Direct `aws ecs` CLI gives full transparency over task definition manipulation. The action strips metadata fields (`taskDefinitionArn`, `revision`, `status`, etc.) with `jq` before re-registering.
- **`id-token: write` permission**: Added proactively for future OIDC authentication migration (documented in setup guide).

### Key Files (15B)
- `.github/workflows/deploy-staging.yml` — Manual staging deploy (build → deploy → smoke test)
- `.github/workflows/deploy-prod.yml` — Manual prod deploy with confirmation + approval gate
- `.github/workflows/rollback.yml` — Manual rollback to previous ECS task definition revision
- `docs/rollback-procedure.md` — Operational runbook for rollbacks (workflow + CLI fallback)

### Architecture (15B)
```
Manual trigger (workflow_dispatch)
  ├─→ Deploy Staging (deploy-staging.yml)
  │     ├─ Resolve git ref → SHA
  │     ├─ Build frontend + backend → staging ECR
  │     ├─ [1 reviewer approval — GitHub environment]
  │     ├─ Deploy to docteams-staging ECS
  │     └─ Smoke tests (ALB health checks)
  │
  └─→ Deploy Production (deploy-prod.yml)
        ├─ Require "deploy-prod" confirmation input
        ├─ Resolve git ref → SHA
        ├─ Build frontend + backend → prod ECR
        ├─ [2 reviewer approvals + 5-min wait — GitHub environment]
        ├─ Deploy to docteams-prod ECS
        └─ Smoke tests (ALB health checks)

Manual trigger (workflow_dispatch)
  └─→ Rollback (rollback.yml)
        ├─ Select environment (dev/staging/prod) + service (frontend/backend/both)
        ├─ Require "rollback" confirmation input
        ├─ Find previous task definition revision (N-1)
        ├─ Update ECS service to previous revision
        └─ Wait for stability
```

### Deviations from Original Plan (15B)
- **Self-contained workflows over reusable build**: Staging and prod workflows each include their own build jobs rather than calling `build-and-push.yml` via `workflow_call`. This keeps each environment's pipeline independent and avoids risk of breaking the existing dev pipeline. The frontend Docker image bakes in `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` at build time — different Clerk instances per env require separate builds.
- **Confirmation input for prod**: Added `confirm: "deploy-prod"` text input as an additional safety layer beyond GitHub environment protection rules.
- **Rollback uses task definition history**: Instead of rebuilding old images, rollback simply points the ECS service at the previous task definition revision (N-1). This is fast (~2-3 min) and doesn't require Docker builds.
- **Per-environment GHA cache scopes**: Each environment uses its own Buildx cache scope (e.g., `frontend-staging`, `backend-prod`) to avoid cache pollution across environments.

---

## Epic 16: Testing & Quality

**Goal**: Establish testing infrastructure across both services — unit tests, integration tests, tenant isolation tests, and E2E framework.

**Dependencies**: Epic 7, Epic 8, Epic 10, Epic 11

**Scope**: Both (frontend + backend)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **16A** | 16.1, 16.2 | Frontend unit test setup (Vitest), backend unit test setup (JUnit 5 + Mockito) |
| **16B** | 16.3, 16.4, 16.5 | Testcontainers integration infra, tenant isolation tests, provisioning tests |
| **16C** | 16.6, 16.7, 16.8, 16.9 | API endpoint tests, Playwright E2E framework, E2E user journeys, CI integration |

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 16.1 | Set up frontend unit testing | **Done** | Vitest + React Testing Library configured. `pnpm run test` runs tests. Coverage via `vitest --coverage`. |
| 16.2 | Set up backend unit testing | **Done** | JUnit 5 + Mockito configured. `./mvnw test` runs tests. Unit tests for ProjectService, DocumentService, TenantProvisioningService, SchemaNameGenerator. |
| 16.3 | Set up backend integration testing | **Done** | Completed in Epic 5. `TestcontainersConfiguration` with `@ServiceConnection` + `DynamicPropertyRegistrar` for dual datasources. `application-test.yml` for test profile. All integration tests use `@Import(TestcontainersConfiguration.class)`. |
| 16.4 | Write tenant isolation tests | **Done** | Completed in Epics 6–8. `MultitenancyIntegrationTest` (schema isolation, connection provider), `ProjectIntegrationTest` (cross-tenant project invisibility via API), `DocumentIntegrationTest` (cross-tenant document isolation via API). |
| 16.5 | Write provisioning tests | **Done** | Completed in Epic 5. `ProvisioningIntegrationTest` (end-to-end flow, idempotency, schema verification), `ProvisioningControllerTest` (REST API 201/409/401/400), `TenantProvisioningServiceTest` (failure handling, error recovery). |
| 16.6 | Write API endpoint tests | | Integration tests for all project and document endpoints: CRUD, authorization, validation, error cases. |
| 16.7 | Set up E2E test framework | | Configure Playwright for E2E tests. Create auth helpers. Write smoke test. |
| 16.8 | Write key user journey E2E tests | | E2E tests for onboarding, project CRUD, document upload/download, org switching, team invitation. |
| 16.9 | Add CI test integration | | Update CI workflow to run all test suites. E2E in staging deploy workflow. |

---

## Epic 17: Members Table + Webhook Sync

**Goal**: Create the `members` table in each tenant schema, implement internal API endpoints for member sync, and wire up the `organizationMembership.*` webhook handlers (currently no-ops).

**Dependencies**: Epic 4 (Webhooks), Epic 5 (Provisioning)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **17A** | 17.1–17.5 | Backend: migration, entity, repository, service, controller | **Done** |
| **17B** | 17.6–17.9 | Frontend: webhook handlers, types, tests | **Done** |

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 17.1 | Create V3 tenant migration for members table | **Done** | `V3__create_members.sql` in `db/migration/tenant/`. Columns: `id` (UUID PK DEFAULT gen_random_uuid()), `clerk_user_id` (VARCHAR(255) UNIQUE NOT NULL), `email` (VARCHAR(255) NOT NULL), `name` (VARCHAR(255)), `avatar_url` (VARCHAR(1000)), `org_role` (VARCHAR(50) NOT NULL), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()), `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). Index on `clerk_user_id`. |
| 17.2 | Create Member entity | **Done** | `member/Member.java` — JPA entity mapped to `members` table. UUID id, clerkUserId, email, name, avatarUrl, orgRole, createdAt, updatedAt. No `@Table(schema=...)` (Hibernate resolves tenant schema). `updateFrom(email, name, avatarUrl, orgRole)` method for upsert. |
| 17.3 | Create MemberRepository | **Done** | `member/MemberRepository.java` — `JpaRepository<Member, UUID>`. Methods: `Optional<Member> findByClerkUserId(String)`, `void deleteByClerkUserId(String)`, `boolean existsByClerkUserId(String)`. |
| 17.4 | Create MemberSyncService | **Done** | `member/MemberSyncService.java` — `syncMember(clerkOrgId, clerkUserId, email, name, avatarUrl, orgRole)`: resolves tenant via `OrgSchemaMappingRepository.findByClerkOrgId()`, manually sets/clears `TenantContext` in try/finally (internal endpoints skip TenantFilter), upserts member. `deleteMember(clerkOrgId, clerkUserId)`: resolves tenant, deletes by clerkUserId. `@Transactional` per operation. |
| 17.5 | Create MemberSyncController | **Done** | `member/MemberSyncController.java` — `POST /internal/members/sync` with `SyncMemberRequest(clerkOrgId, clerkUserId, email, name, avatarUrl, orgRole)`, returns 201/200. `DELETE /internal/members/{clerkUserId}?clerkOrgId={orgId}`, returns 204/404. All `@Valid` with `@NotBlank` on required fields. |
| 17.6 | Implement organizationMembership.created handler | **Done** | Replace no-op in `webhook-handlers.ts`. Extract `organization.id` and `public_user_data.user_id` from event. Call `clerkClient.users.getUser(userId)` for name/email/avatar. Map role (`org:admin` → `admin`). Call `POST /internal/members/sync` via `internalApiClient`. |
| 17.7 | Implement organizationMembership.updated handler | **Done** | Same extraction pattern. Call `POST /internal/members/sync` (upsert) with updated role. |
| 17.8 | Implement organizationMembership.deleted handler | **Done** | Call `DELETE /internal/members/{clerkUserId}?clerkOrgId={orgId}`. Handle 404 gracefully (member may already be deleted). |
| 17.9 | Add tests | **Done** | Backend: integration tests for MemberSyncController (sync creates member, re-sync updates, delete removes, tenant isolation). Frontend: update webhook handler tests for membership events. Add `SyncMemberRequest` type to `lib/internal-api.ts`. |

### Key Files

**Backend (create):**
- `backend/src/main/resources/db/migration/tenant/V3__create_members.sql`
- `backend/src/main/java/.../member/Member.java`
- `backend/src/main/java/.../member/MemberRepository.java`
- `backend/src/main/java/.../member/MemberSyncService.java`
- `backend/src/main/java/.../member/MemberSyncController.java`
- `backend/src/test/java/.../member/MemberSyncIntegrationTest.java`

**Frontend (modify):**
- `frontend/lib/webhook-handlers.ts` — Replace no-op stubs (lines 106-109)
- `frontend/lib/internal-api.ts` — Add SyncMemberRequest type

### Architecture Decisions

- **Upsert via single POST endpoint**: A single `POST /internal/members/sync` handles both create and update. The service does `findByClerkUserId` — if found, updates; if not, creates. Simpler than separate POST/PUT and naturally idempotent.
- **clerkOrgId in request body**: Internal endpoints use API key auth (not JWT), so TenantFilter skips them. `clerkOrgId` is passed in the request body, consistent with `ProvisioningController` pattern. Service manually resolves tenant.
- **Clerk user details via clerkClient**: Membership events provide `public_user_data` with limited info. Handler calls `clerkClient.users.getUser()` for full details. Single API call per event — acceptable since membership events are infrequent.

---

## Epic 18: MemberFilter + MemberContext

**Goal**: Add request-level member resolution so every authenticated API request knows the current member's UUID. Migrate `projects.created_by` and `documents.uploaded_by` to UUID FKs referencing `members(id)`.

**Dependencies**: Epic 17

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **18A** | 18.1–18.3, 18.8, 18.9a | MemberContext, MemberFilter, SecurityConfig wiring, MDC, filter tests | Done |
| **18B** | 18.4–18.7, 18.9b | V4 FK migration, entity type changes, DTO updates, existing test updates | Done |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 18.1 | Create MemberContext | 18A | Done | `member/MemberContext.java` — Static ThreadLocal with `setCurrentMemberId(UUID)`, `getCurrentMemberId()`, `setOrgRole(String)`, `getOrgRole()`, `clear()`. Same pattern as `TenantContext`. |
| 18.2 | Create MemberFilter | 18A | Done | `member/MemberFilter.java` — `OncePerRequestFilter`, after TenantFilter. Extracts JWT `sub` (Clerk user ID), queries `MemberRepository.findByClerkUserId()` in current tenant. If found: sets `MemberContext`. If NOT found: **lazy-creates** a minimal member (clerkUserId from `sub`, orgRole from `o.rol`, placeholder name/email — webhook will upsert full data later). Cache: `ConcurrentHashMap<String, UUID>` keyed by `tenantId:clerkUserId`. `shouldNotFilter` for `/internal/*` and `/actuator/*`. |
| 18.3 | Wire MemberFilter into SecurityConfig | 18A | Done | Add to filter chain: `.addFilterAfter(memberFilter, TenantFilter.class)` and move `.addFilterAfter(tenantLoggingFilter, MemberFilter.class)`. New order: ApiKeyAuthFilter → BearerTokenAuth → TenantFilter → MemberFilter → TenantLoggingFilter. |
| 18.4 | Create V4 tenant migration for FK changes | 18B | Done | `V4__migrate_ownership_to_members.sql`: (1) Backfill: INSERT INTO members from DISTINCT created_by/uploaded_by with placeholder email/name, ON CONFLICT DO NOTHING. (2) Add temp columns `created_by_member_id UUID` / `uploaded_by_member_id UUID`. (3) Populate via JOIN on members.clerk_user_id. (4) Drop old VARCHAR columns, rename temp columns, add FK constraints → `members(id)`. |
| 18.5 | Update Project entity | 18B | Done | Change `createdBy` from `String` to `UUID`. Update `ProjectController` to use `MemberContext.getCurrentMemberId()` instead of `auth.getName()`. |
| 18.6 | Update Document entity | 18B | Done | Change `uploadedBy` from `String` to `UUID`. Update `DocumentController` to use `MemberContext.getCurrentMemberId()`. |
| 18.7 | Update response DTOs | 18B | Done | `ProjectResponse.createdBy` and document DTOs change from String to UUID (serialized as string in JSON — backward compatible). |
| 18.8 | Update TenantLoggingFilter | 18A | Done | Add `MDC.put("memberId", MemberContext.getCurrentMemberId())` alongside existing userId entry. Null-guard for `/internal/*` requests where MemberContext is unset. |
| 18.9a | Add MemberFilter integration tests | 18A | Done | `MemberFilterIntegrationTest.java`: valid member → context set, unknown user → lazy-created, `/internal/*` skipped, cache hit on second request. Existing tests should pass unchanged (lazy-create handles missing members). |
| 18.9b | Update existing integration tests | 18B | Done | **Breaking change**: `ProjectIntegrationTest` and `DocumentIntegrationTest` must seed member records in `@BeforeAll`. Assertions change: `$.createdBy` returns UUID string (not Clerk user ID). |

### Key Files

**Slice 18A — Create:**
- `backend/src/main/java/.../member/MemberContext.java`
- `backend/src/main/java/.../member/MemberFilter.java`
- `backend/src/test/java/.../member/MemberFilterIntegrationTest.java`

**Slice 18A — Modify:**
- `backend/src/main/java/.../security/SecurityConfig.java` — Add MemberFilter to chain
- `backend/src/main/java/.../multitenancy/TenantLoggingFilter.java` — Add memberId MDC

**Slice 18B — Create:**
- `backend/src/main/resources/db/migration/tenant/V4__migrate_ownership_to_members.sql`

**Slice 18B — Modify:**
- `backend/src/main/java/.../project/Project.java` — createdBy type change
- `backend/src/main/java/.../project/ProjectController.java` — Use MemberContext
- `backend/src/main/java/.../project/ProjectService.java` — Parameter type change
- `backend/src/main/java/.../document/Document.java` — uploadedBy type change
- `backend/src/main/java/.../document/DocumentController.java` — Use MemberContext
- `backend/src/main/java/.../document/DocumentService.java` — Parameter type change
- `backend/src/test/java/.../project/ProjectIntegrationTest.java` — Seed members, UUID assertions
- `backend/src/test/java/.../document/DocumentIntegrationTest.java` — Seed members, UUID assertions

### Architecture Decisions

- **Lazy-create in MemberFilter**: When JWT user not found in members table, creates a minimal record (clerkUserId, orgRole from JWT). Webhook handler upserts full data later. Solves the deployment window where users hit the API before their membership webhook is processed.
- **ConcurrentHashMap cache**: Keyed by `tenantId:clerkUserId`, consistent with TenantFilter's cache pattern. No TTL needed — member IDs are immutable. `MemberSyncService.deleteMember()` clears the entry.
- **Multi-step migration**: V4 uses temp columns → populate → drop → rename pattern rather than in-place ALTER. Safer for data preservation and works with empty tables too.
- **Slice boundary**: 18A is purely additive (no existing behavior changes). 18B is a breaking data model change. This boundary allows 18A to be merged and validated independently before the riskier migration in 18B.

---

## Epic 19: Project Members Table + API

**Goal**: Create the `project_members` junction table, implement CRUD endpoints for project membership, add an org member list endpoint, and backfill existing projects (creator becomes lead).

**Dependencies**: Epic 18

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **19A** | 19.1–19.4 | Migration, entity, repository, service | **Done** |
| **19B** | 19.5–19.8 | Controllers, backfill, tests | **Done** |

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 19.1 | Create V5 tenant migration for project_members | **Done** | `V5__create_project_members.sql`: Table with `id` (UUID PK), `project_id` (UUID FK → projects ON DELETE CASCADE), `member_id` (UUID FK → members ON DELETE CASCADE), `project_role` (VARCHAR(50) NOT NULL — `lead` or `member`), `added_by` (UUID FK → members, nullable), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). UNIQUE(project_id, member_id). Backfill: INSERT from projects — creator becomes lead. |
| 19.2 | Create ProjectMember entity | **Done** | `member/ProjectMember.java` — UUID id, projectId (UUID), memberId (UUID), projectRole (String), addedBy (UUID nullable), createdAt. No bidirectional JPA relationships — use explicit queries. |
| 19.3 | Create ProjectMemberRepository | **Done** | `member/ProjectMemberRepository.java` — Methods: `findByProjectId`, `findByProjectIdAndMemberId`, `existsByProjectIdAndMemberId`, `findByProjectIdAndProjectRole`, `findByMemberId`, `deleteByProjectIdAndMemberId`. |
| 19.4 | Create ProjectMemberService | **Done** | Methods: `listProjectMembers(projectId)` — joins with members for display data. `addMember(projectId, memberId, addedBy)` — validates member exists, not already on project (409). `removeMember(projectId, memberId, requestedBy, orgRole)` — cannot remove lead. `transferLead(projectId, currentLeadId, newLeadId)` — atomic `@Transactional`: demote old lead, promote new. `isProjectMember(projectId, memberId)` — boolean. |
| 19.5 | Create ProjectMemberController | **Done** | `@RequestMapping("/api/projects/{projectId}/members")`. GET (list, MEMBER+), POST `{memberId}` (add, MEMBER+ with service permission check), DELETE `/{memberId}` (remove), PUT `/{memberId}/role` `{role: "lead"}` (transfer). DTOs: `ProjectMemberResponse(id, memberId, name, email, avatarUrl, projectRole, createdAt)`. |
| 19.6 | Create OrgMemberController | **Done** | `GET /api/members` — returns all org members (from tenant's members table). Response: `OrgMemberResponse(id, name, email, avatarUrl, orgRole)`. Purpose: populate "add member to project" picker. |
| 19.7 | Auto-create lead on project creation | **Done** | Modify `ProjectService.createProject()`: after saving project, insert `ProjectMember(projectRole="lead", memberId=MemberContext.getCurrentMemberId())`. Inject `ProjectMemberRepository`. |
| 19.8 | Add integration tests | **Done** | Creator becomes lead, add/remove member, duplicate 409, cannot remove lead, lead transfer atomicity, admin/owner bypass, GET /api/members, org member deletion cascades to project_members. |

### Key Files

**Create:**
- `backend/src/main/resources/db/migration/tenant/V5__create_project_members.sql`
- `backend/src/main/java/.../member/ProjectMember.java`
- `backend/src/main/java/.../member/ProjectMemberRepository.java`
- `backend/src/main/java/.../member/ProjectMemberService.java`
- `backend/src/main/java/.../member/ProjectMemberController.java`
- `backend/src/main/java/.../member/OrgMemberController.java`
- `backend/src/test/java/.../member/ProjectMemberIntegrationTest.java`

**Modify:**
- `backend/src/main/java/.../project/ProjectService.java` — Auto-create project lead

### Architecture Decisions

- **All member classes in `member/` package**: Groups the bounded context (Member, ProjectMember, services, controllers). `project/` package stays focused on core CRUD.
- **No bidirectional JPA relationships**: ProjectMember uses plain UUID references. Joins done via JPQL when display data needed. Avoids lazy-loading pitfalls and N+1.
- **Permission checks in service layer**: `@PreAuthorize` does org-level checks only. Project-level checks (is user the lead?) happen in `ProjectMemberService` — testable, consolidated.
- **Lead transfer as atomic transaction**: Single `@Transactional` ensures a project never has zero or two leads.

---

## Epic 20: Project Access Control

**Goal**: Modify existing project and document endpoints to enforce project membership. Members see only their projects; admins/owners see all. Allow all members to create projects.

**Dependencies**: Epic 19

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **20A** | 20.1–20.5, 20.7, 20.8a | ProjectAccessService + project endpoint access control + tests | **Done** |
| **20B** | 20.6, 20.8b | Document endpoint access control + tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 20.1 | Create ProjectAccessService | 20A | **Done** | `member/ProjectAccessService.java` — `checkAccess(projectId, memberId, orgRole)` returns `ProjectAccess(canView, canEdit, canManageMembers, canDelete, projectRole)`. Owner/Admin → all true (canDelete owner-only). Member+lead → view, edit, manageMembers. Member+member → view only. Member+not-on-project → all false. |
| 20.2 | Modify GET /api/projects (filtered listing) | 20A | **Done** | Admin/owner: return all projects (unchanged). Member: query only projects where user has a `project_members` row. New `ProjectRepository` method with JPQL join. Response includes `projectRole` field. |
| 20.3 | Modify GET /api/projects/{id} | 20A | **Done** | Call `ProjectAccessService.checkAccess()`. If `!canView`, return 404 (not 403 — prevents info leakage). Add `projectRole` to response. |
| 20.4 | Modify POST /api/projects | 20A | **Done** | Change `@PreAuthorize` from `ADMIN+` to `MEMBER+`. All org members can create projects. Creator auto-becomes lead (Epic 19). |
| 20.5 | Modify PUT /api/projects/{id} | 20A | **Done** | Change `@PreAuthorize` to `MEMBER+`. Service checks `canEdit` via ProjectAccessService — allows project leads to edit (previously admin+ only). |
| 20.6 | Modify document endpoints | 20B | | All document endpoints check project membership via ProjectAccessService. Upload-init/list (take projectId): check `canView`. Confirm/presign-download (take documentId): look up projectId first, then check. Non-member → 404. |
| 20.7 | Update ProjectResponse DTO | 20A | **Done** | Add `projectRole` field (String, nullable). Non-null when user is a project member. Null for admin/owner viewing non-member projects. |
| 20.8a | Project access control tests | 20A | **Done** | Member without project membership → 404 on GET single. Member creates project → lead → can GET/PUT. Regular member → view only (403 on PUT). Admin/owner → full access regardless. GET /api/projects filtered for members. Update existing ProjectIntegrationTest for new RBAC rules. |
| 20.8b | Document access control tests | 20B | | Document upload-init/list check project membership. Confirm/presign-download/cancel look up projectId first, then check. Non-member → 404. Update existing DocumentIntegrationTest for new RBAC rules. |

### Slice 20A: ProjectAccessService + Project Endpoint Access Control

**Goal**: Introduce the central access control service and apply it to all project CRUD endpoints. After this slice, project listing is filtered by membership for regular members, single-project access is gated, and create/update permissions are widened to all org members (with project-level role checks for edit).

**Key Files:**

*Create:*
- `backend/src/main/java/.../member/ProjectAccessService.java`
- `backend/src/main/java/.../member/ProjectAccess.java` (record)
- `backend/src/main/java/.../project/ProjectWithRole.java` (record)
- `backend/src/test/java/.../project/ProjectAccessIntegrationTest.java`

*Modify:*
- `backend/src/main/java/.../project/ProjectController.java` — Inject ProjectAccessService, change @PreAuthorize, add projectRole to response
- `backend/src/main/java/.../project/ProjectService.java` — Add filtered listing method for members
- `backend/src/main/java/.../project/ProjectRepository.java` — JPQL join query for member-visible projects
- `backend/src/test/java/.../project/ProjectIntegrationTest.java` — Major test updates for new RBAC
- `backend/src/test/java/.../project/ProjectServiceTest.java` — Updated unit tests

### Slice 20B: Document Endpoint Access Control

**Goal**: Extend project access control to all document endpoints. After this slice, uploading, listing, confirming, downloading, and cancelling documents all require project membership (or admin/owner org role).

**Key Files:**

*Modify:*
- `backend/src/main/java/.../document/DocumentController.java` — Inject ProjectAccessService, gate all endpoints
- `backend/src/main/java/.../document/DocumentService.java` — Add projectId lookup for document-level endpoints
- `backend/src/test/java/.../document/DocumentIntegrationTest.java` — Major test updates for new RBAC

### Architecture Decisions

- **ProjectAccessService as central authority**: Single service encapsulates all access logic. Controllers/services call `checkAccess()` — makes authorization explicit, testable, auditable.
- **404 over 403 for non-members**: Prevents information leakage. A member who isn't on a project shouldn't know it exists.
- **Service-layer checks over @PreAuthorize for project-level**: Project-level checks require DB lookups too complex for SpEL. `@PreAuthorize` remains for org-level only.
- **JPQL join for filtered listing**: Efficient single query joining `projects` + `project_members` for member visibility. Avoids N+1.
- **Two-slice decomposition**: Slice A (projects) is independently deployable — documents temporarily keep org-level auth until Slice B lands. This minimizes blast radius per PR.

---

## Epic 21: Frontend — Project Members Panel

**Goal**: Add a members panel on the project detail page with add/remove member, lead transfer, and role badges.

**Dependencies**: Epic 19, Epic 20

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **21A** | 21.1–21.3, 21.6–21.7 | Types, server actions, read-only panel, page integration, role badges | |
| **21B** | 21.4–21.5, 21.3 (wiring), 21.8 | AddMemberDialog, TransferLeadDialog, action buttons, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 21.1 | Add TypeScript types | A | | In `lib/types.ts`: `Member { id, name, email, avatarUrl, orgRole }`, `ProjectMember { id, memberId, name, email, avatarUrl, projectRole, createdAt }`, `ProjectRole = "lead" \| "member"`. Update `Project` to include `projectRole: string \| null`. |
| 21.2 | Create project members server actions | A | | `projects/[id]/member-actions.ts` — `fetchProjectMembers(projectId)`, `fetchOrgMembers()`, `addProjectMember(slug, projectId, memberId)`, `removeProjectMember(slug, projectId, memberId)`, `transferLead(slug, projectId, memberId)`. Standard ActionResult pattern with revalidatePath. |
| 21.3 | Build ProjectMembersPanel | A+B | | **Slice A**: Read-only client component — member table with avatar, name, email, role badge (Lead/Member via Shadcn Badge). No action buttons yet. **Slice B**: Wire up "Add Member" button (lead/admin/owner), "Remove" button per row (not on lead's row), "Transfer Lead" per row (current lead only). `useTransition()` for loading states. |
| 21.4 | Build AddMemberDialog | B | | `components/projects/add-member-dialog.tsx` — Shadcn Dialog with searchable org member list via Shadcn Command (cmdk). Fetches `GET /api/members`, filters out existing project members. Shows name + email + avatar. Install `command` Shadcn component. |
| 21.5 | Build TransferLeadDialog | B | | `components/projects/transfer-lead-dialog.tsx` — Shadcn AlertDialog (destructive action). "Transfer lead role to {name}? You will become a regular member." |
| 21.6 | Update project detail page | A | | Add `ProjectMembersPanel` below DocumentsPanel. Pass `projectRole` from updated project response. Fetch project members on server side. Update edit button: visible for lead + admin/owner (was admin+ only). |
| 21.7 | Add role badge to project detail header | A | | Badge next to project name showing user's project role (Lead/Member). Nothing for admin/owner viewing non-member projects. |
| 21.8 | Add frontend tests | B | | Member list renders with roles. AddMemberDialog filters existing members. Action button visibility by role. Remove and transfer actions. |

### Key Files

**Create:**
- `frontend/app/(app)/org/[slug]/projects/[id]/member-actions.ts`
- `frontend/components/projects/project-members-panel.tsx`
- `frontend/components/projects/add-member-dialog.tsx` *(Slice B)*
- `frontend/components/projects/transfer-lead-dialog.tsx` *(Slice B)*

**Modify:**
- `frontend/lib/types.ts` — New interfaces
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add members panel, role badges, edit visibility

### Architecture Decisions

- **Two-slice decomposition**: Slice A (display) is independently deployable — members panel renders as a read-only table showing who's on the project and their roles. Slice B adds all interactive CRUD (add/remove/transfer) via dialogs. This minimizes blast radius per PR and allows early visual verification.
- **Client component for members panel**: Interactive state (add/remove, loading, transitions) requires client component. Server component fetches initial data and passes props — same pattern as DocumentsPanel.
- **Shadcn Command for member picker**: Keyboard-navigable, searchable list. Standard Shadcn pattern for combobox/search UIs.
- **AlertDialog for lead transfer**: Destructive/irreversible action — prevents accidental clicks. Consistent with DeleteProjectDialog pattern.

---

## Epic 22: Frontend — Filtered Project List

**Goal**: Update projects list and dashboard to show only user's projects (members) or all (admin/owner). Make "New Project" visible to all org members.

**Dependencies**: Epic 20, Epic 21

**Scope**: Frontend

**Estimated Effort**: S

### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 22.1 | Update projects list page | | Backend already filters by membership. Show "New Project" for ALL org roles (was admin+ only). Show `projectRole` badge on project cards. Update empty state for members: "You're not on any projects yet." |
| 22.2 | Update dashboard page | | Backend returns filtered projects so stats are correct. Show "New Project" quick action for all roles. Label: "Your Projects" for members, "All Projects" for admin/owner. |
| 22.3 | Update createProject server action | | Remove admin-only role check in `projects/actions.ts` (was checking `orgRole !== "org:admin" && orgRole !== "org:owner"`). All members can create. Backend enforces auth. |
| 22.4 | Update project detail edit/delete visibility | | Edit button: `projectRole === "lead"` OR admin+. Delete: owner only (unchanged). Update `updateProject` server action role check to allow leads. |
| 22.5 | Add frontend tests | | "New Project" renders for all roles. Project cards show role badges. Edit visible for lead. |

### Key Files

**Modify:**
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — New Project for all, role badges
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — New Project for all, label change
- `frontend/app/(app)/org/[slug]/projects/actions.ts` — Remove admin-only check
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Edit visibility for leads

---

## Epic Dependency Graph

```mermaid
flowchart LR
    E1["Epic 1<br/>Scaffolding"] --> E2["Epic 2<br/>Auth"]
    E1 --> E6["Epic 6<br/>Multitenancy"]
    E1 --> E9["Epic 9<br/>S3 Integration"]
    E1 --> E13["Epic 13<br/>Containerization"]

    E2 --> E3["Epic 3<br/>Org Mgmt"]
    E2 --> E4["Epic 4<br/>Webhooks"]

    E6 --> E5["Epic 5<br/>Provisioning"]
    E6 --> E7["Epic 7<br/>Projects API"]

    E7 --> E8["Epic 8<br/>Documents API"]
    E9 --> E8

    E3 --> E10["Epic 10<br/>Dashboard & Projects UI"]
    E7 --> E10

    E10 --> E11["Epic 11<br/>Documents UI"]
    E8 --> E11

    E3 --> E12["Epic 12<br/>Team UI"]

    E13 --> E14["Epic 14<br/>AWS Infra"]
    E13 --> E15["Epic 15<br/>Deploy Pipeline"]
    E14 --> E15

    E7 --> E16["Epic 16<br/>Testing"]
    E8 --> E16
    E10 --> E16
    E11 --> E16

    E4 --> E17["Epic 17<br/>Members + Sync"]
    E5 --> E17
    E17 --> E18["Epic 18<br/>MemberFilter"]
    E18 --> E19["Epic 19<br/>Project Members"]
    E19 --> E20["Epic 20<br/>Access Control"]
    E19 --> E21["Epic 21<br/>Members Panel UI"]
    E20 --> E21
    E20 --> E22["Epic 22<br/>Filtered Lists UI"]
    E21 --> E22
```

---

## Implementation Order

### Phase 1: Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 1: Scaffolding | Everything depends on this. Establishes build tooling, local dev stack, CI. |

### Phase 2: Auth & Backend Core (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 2: Auth + Clerk | Frontend auth is the prerequisite for all user-facing features. |
| 2b | Epic 6: Multitenancy Backend | Backend multitenancy is the prerequisite for all data APIs. Highest technical risk — tackle early. |
| 2c | Epic 9: S3 Integration | Independent, low-risk. Can run in parallel with auth and multitenancy. |

**Rationale**: Epics 2, 6, and 9 are independent of each other and can be developed in parallel. Epic 6 (multitenancy) carries the highest technical risk (Hibernate 7 schema-per-tenant, tenant context, connection pooling), so starting it early maximizes time for course correction.

### Phase 3: Tenant Provisioning & Org Management

| Order | Epic | Rationale |
|-------|------|-----------|
| 3a | Epic 5: Tenant Provisioning | Depends on Epic 6 (multitenancy). Must work before any tenant data can be created. |
| 3b | Epic 3: Org Management | Depends on Epic 2 (auth). Can run in parallel with provisioning. |
| 3c | Epic 4: Webhooks | Depends on Epic 2. Wires org creation to provisioning. |

**Rationale**: Provisioning and org management form the bridge between auth and data. Webhooks connect Clerk org creation to backend provisioning. These three epics complete the onboarding flow.

### Phase 4: Core Features

| Order | Epic | Rationale |
|-------|------|-----------|
| 4a | Epic 7: Projects API | First domain API; validates multitenancy end-to-end. |
| 4b | Epic 8: Documents API | Depends on Projects API and S3. Completes the backend API surface. |

**Rationale**: Projects are the simpler domain entity — validating the full multitenancy stack (JWT → tenant resolution → schema isolation → CRUD) before adding S3 complexity in documents.

### Phase 5: Frontend Features

| Order | Epic | Rationale |
|-------|------|-----------|
| 5a | Epic 10: Dashboard & Projects UI | Primary user interface; depends on Org Management and Projects API. |
| 5b | Epic 11: Documents UI | Depends on Dashboard and Documents API. |
| 5c | Epic 12: Team Management UI | Depends on Org Management. Independent of documents, can run in parallel with 5a/5b. |

**Rationale**: Frontend development starts after backend APIs are available. Team management uses Clerk components directly and can be developed in parallel with dashboard/document UI.

### Phase 6: Infrastructure & Deployment

| Order | Epic | Rationale |
|-------|------|-----------|
| 6a | Epic 13: Containerization | Docker images needed before infrastructure and deployment. |
| 6b | Epic 14: AWS Infrastructure | Terraform modules for all AWS resources. |
| 6c | Epic 15: Deployment Pipeline | GitHub Actions CI/CD depends on ECR and ECS being provisioned. |

**Rationale**: Infrastructure work can begin in parallel with Phase 4–5 once container images are ready. It is intentionally later because local development is sufficient for feature work, and infrastructure changes iterate more slowly.

### Phase 7: Quality & Hardening

| Order | Epic | Rationale |
|-------|------|-----------|
| 7 | Epic 16: Testing & Quality | Integration tests, tenant isolation tests, and E2E tests run after features are built. |

**Rationale**: While unit tests are written alongside each epic, the dedicated testing epic sets up integration test infrastructure (Testcontainers), writes cross-cutting tests (tenant isolation), and establishes E2E coverage. This runs last because it tests the full assembled system.

### Phase 8: Members & Project Ownership

| Order | Epic | Rationale |
|-------|------|-----------|
| 8a | Epic 17: Members + Webhook Sync | Foundation — creates the members table and sync infrastructure. Must exist before MemberFilter can resolve users. |
| 8b | Epic 18: MemberFilter + MemberContext | Request-level member resolution. Modifies existing entities (breaking change to created_by/uploaded_by types). Lazy-create handles the gap between deployment and webhook sync. |
| 8c | Epic 19: Project Members + API | Creates project_members table and CRUD endpoints. Must come after MemberFilter since it uses MemberContext. |
| 8d | Epic 20: Project Access Control | Modifies existing endpoints to enforce project membership. Highest behavioral risk — changes what data users see. |
| 8e | Epic 21: Project Members Panel UI | New UI components for project membership management. Requires backend APIs from Epics 19 and 20. |
| 8f | Epic 22: Filtered Project List | Updates existing pages to reflect the new access model. Depends on both backend access control and new types from Epic 21. |

**Rationale**: All epics are sequential — each depends on its predecessor. Backend foundation (17-18) must land before features (19-20), and backend APIs must exist before frontend (21-22). Each epic is independently deployable and testable.

### Summary Timeline

```
Phase 1:  [E1]
Phase 2:  [E2] [E6] [E9]      <- parallel
Phase 3:  [E5] [E3] [E4]      <- parallel
Phase 4:  [E7] -> [E8]         <- sequential
Phase 5:  [E10] -> [E11] [E12] <- 11 after 10; 12 parallel
Phase 6:  [E13] -> [E14] [E15] <- 14/15 after 13
Phase 7:  [E16]
Phase 8:  [E17] -> [E18] -> [E19] -> [E20] -> [E21] -> [E22]  <- sequential
```

---

## Risk Register

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| R1 | Hibernate 7 schema-per-tenant multitenancy has undocumented breaking changes from Hibernate 6 | H | M | Start Epic 6 early (Phase 2). Build a minimal proof-of-concept with `CurrentTenantIdentifierResolver` + `MultiTenantConnectionProvider` before building features. Hibernate 7 removes `multiTenancy` property but auto-detects — verify this works with Spring Boot 4. |
| R2 | Neon PgBouncer (transaction mode) interferes with `SET search_path` | H | L | Research confirms `search_path` set per connection checkout works in transaction mode because Hibernate wraps ops in transactions. Validate with integration test against Neon early. Fallback: use direct connection for all traffic (reduced concurrency). |
| R3 | Clerk webhook delivery unreliability causes missed org provisioning | H | L | Idempotent provisioning allows manual retrigger. Monitor `provisioning_status = PENDING` for stale entries. Add admin endpoint to retrigger provisioning. Svix retries 8 times over 32 hours. |
| R4 | Spring Boot 4 / Java 25 compatibility issues with dependencies (Resilience4j, AWS SDK, etc.) | M | M | Pin dependency versions that are verified compatible. Check release notes for Spring Boot 4 + Hibernate 7 breaking changes. Have a downgrade path to Spring Boot 3.4 + Java 21 if blockers emerge. |
| R5 | Flyway tenant migration at startup becomes slow with many tenants | M | L | Sequential migration is acceptable for MVP (<100 tenants). For scale: parallelize migrations with thread pool, add startup readiness probe delay, consider running migrations as a separate job before deployment. |
| R6 | Clerk JWT claims structure changes between SDK versions | M | L | Pin `@clerk/nextjs` version. Use Clerk's documented JWT template. Validate claim names (`org_id`, `org_role`) against Clerk docs when upgrading. Add integration test that verifies JWT claim parsing. |
| R7 | Presigned URL expiry causes poor UX for large file uploads | M | M | Set generous expiry (1 hour for uploads). Client-side: check URL expiry before starting upload; if expired, re-request. Show clear error if upload fails due to expiry. Consider multipart upload for files > 100MB (future). |
| R8 | Terraform state management conflicts in team development | M | M | Use S3 backend with DynamoDB state locking from day one. One environment per developer is overkill for MVP — use shared dev environment with coordinated applies. Document Terraform workflow. |
| R9 | LocalStack S3 behavior diverges from real AWS S3 | L | M | LocalStack S3 is mature and reliable for presigned URLs. Run integration tests against real S3 in CI staging environment. Document known LocalStack limitations. |
| R10 | Clerk rate limits hit during bulk webhook processing | L | L | Clerk webhook rate limits are generous. Our system doesn't call Clerk API during webhook processing (we only receive events). Risk is theoretical — monitor if org creation volume is unusually high. |
| R11 | Lazy-create in MemberFilter produces incomplete member records (no name/email) | L | M | Webhook handler upserts full data shortly after. Frontend displays placeholder for incomplete records. Add admin endpoint to re-sync if needed. |
| R12 | V4 migration (created_by/uploaded_by type change) fails on existing data | H | L | Multi-step migration with temp columns and backfill. Test with production-like data before deploying. `ON CONFLICT DO NOTHING` makes backfill idempotent. Add rollback migration. |
| R13 | Existing integration tests break after MemberFilter introduction (Epic 18) | M | H | Expected and planned for — task 18.9 explicitly addresses updating all existing tests to provision member records in `@BeforeAll`. Run full test suite before merging. |
| R14 | Membership webhook arrives before tenant is provisioned (org.created not processed yet) | M | L | `MemberSyncService` returns 404 if tenant not found. Svix retries 8 times over 32 hours — will succeed once provisioning completes. Log the failure for monitoring. |
| R15 | ProjectAccessService database lookups add latency to every project request | M | L | Single indexed query on `project_members(project_id, member_id)` covered by UNIQUE constraint index. For listing, JPQL join is a single query. No N+1 risk. Monitor query performance. |
