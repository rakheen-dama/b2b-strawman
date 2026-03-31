# Template: java-keycloak-multitenant-saas — Task Breakdown

A standalone multitenant SaaS starter template built with Java 25, Spring Boot 4, Keycloak 26.5, Spring Cloud Gateway (BFF), and Next.js 16. The template provides schema-per-tenant isolation, gated registration with OTP, platform admin approval, member invitation/sync, tenant-scoped domain CRUD (Projects, Customers, Comments), a customer portal with magic link authentication, and a companion 10-part blog series. Everything is new -- there is no existing codebase.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| T1 | Infrastructure + Multitenancy Core | Backend + Compose | -- | L | T1A, T1B, T1C | |
| T2 | Gateway + Auth | Gateway + Backend | T1 | L | T2A, T2B, T2C | |
| T3 | Access Request + Provisioning | Backend + Frontend | T2 | L | T3A, T3B, T3C, T3D | |
| T4 | Members + Invitations | Backend + Frontend | T3 | M | T4A, T4B | |
| T5 | Domain CRUD (Customer, Project, Comment) | Backend + Frontend | T4 | L | T5A, T5B, T5C, T5D | |
| T6 | Portal + Magic Links | Backend + Frontend | T5 | L | T6A, T6B, T6C | |
| T7 | Blog Series Drafts | Docs | T1-T6 | M | T7A, T7B | |

## Dependency Graph

```
[T1 Infra + MT Core] ──► [T2 Gateway + Auth] ──► [T3 Access Request + Provisioning] ──► [T4 Members + Invitations]
                                                                                              │
                                                                                              ▼
                                                                                      [T5 Domain CRUD]
                                                                                              │
                                                                                              ▼
                                                                                      [T6 Portal + Magic Links]
                                                                                              │
                                                                                              ▼
                                                                                      [T7 Blog Series Drafts]
```

All epics are strictly sequential. T7 can technically start once T6 is complete, but individual blog posts can be drafted as soon as their corresponding slice lands (optional early-start).

## Implementation Order

| Stage | Epics | Can Parallel? | Notes |
|-------|-------|---------------|-------|
| 1 | T1 (Infrastructure + Multitenancy Core) | No | Foundation -- Docker Compose, Maven projects, Hibernate schema routing, Flyway |
| 2 | T2 (Gateway + Auth) | No | Depends on Postgres + Keycloak from T1 |
| 3 | T3 (Access Request + Provisioning) | No | Depends on Gateway auth + JWT validation from T2 |
| 4 | T4 (Members + Invitations) | No | Depends on provisioned tenant from T3 |
| 5 | T5 (Domain CRUD) | No | Depends on authenticated members from T4 |
| 6 | T6 (Portal + Magic Links) | No | Depends on customers + projects from T5 |
| 7 | T7 (Blog Series Drafts) | No | Depends on all code slices T1-T6 being complete |

---

## Epic T1 -- Infrastructure + Multitenancy Core

**Goal**: Establish the complete development infrastructure (Docker Compose with Postgres, Keycloak, Mailpit), scaffold the backend and gateway Maven projects, implement the multitenancy core (ScopedValues, Hibernate schema routing, Flyway dual migrations), and create the foundational public and tenant schema migrations.

**Dependencies**: None (this is the foundation).

**Estimated Effort**: L (3 slices)

### Slices

| Slice | Scope | Description | Effort |
|-------|-------|-------------|--------|
| T1A | Compose + Repo scaffold | Docker Compose (Postgres, Keycloak, Mailpit), dev scripts, init SQL, repo root files (README, CLAUDE.md, LICENSE, .gitignore), Maven parent POM | M |
| T1B | Backend skeleton + multitenancy core | Spring Boot 4 backend Maven project, application entry point, ScopedValues (RequestScopes, ScopedFilterChain), Hibernate multi-tenancy config (SchemaMultiTenantConnectionProvider, TenantIdentifierResolver, HibernateMultiTenancyConfig), OrgSchemaMapping entity + repo, SchemaNameGenerator, FlywayConfig, exception handlers | M |
| T1C | Migrations + integration tests | Public schema migrations (V1 access_requests, V2 org_schema_mappings), tenant schema migrations (V1 organizations, V2 members), Testcontainers setup, ScopedValue unit tests, Flyway integration tests | M |

### Tasks

| ID | Task | Slice | Notes |
|----|------|-------|-------|
| T1.1 | Create repository root structure | T1A | Create `README.md` (quick-start placeholder), `CLAUDE.md` (AI dev guide for new repo), `LICENSE` (MIT), `.gitignore` (Java + Node + Docker patterns). ~4 files. |
| T1.2 | Create Docker Compose infrastructure | T1A | `compose/docker-compose.yml` with postgres (16-alpine, port 5432, healthcheck, volume), keycloak (26.5, port 8180, `start-dev --import-realm`, org feature enabled, depends on postgres, healthcheck), mailpit (ports 1025/8025). `compose/data/postgres/init.sql` (creates `keycloak` database). ~2 files. |
| T1.3 | Create dev lifecycle scripts | T1A | `compose/scripts/dev-up.sh` (docker compose up -d, wait for healthchecks, print summary), `compose/scripts/dev-down.sh` (docker compose down, --clean flag for volume wipe). Both executable. ~2 files. |
| T1.4 | Create Keycloak realm export | T1A | `compose/keycloak/realm-export.json` -- `starter` realm with organizations enabled, `starter-gateway` confidential client (authorization code, redirect URI localhost:8443), `starter-admin-cli` service account client (manage-users, manage-clients, manage-realm), SMTP config pointing to mailpit:1025, groups claim mapper, organization claim mapper. ~1 file. |
| T1.5 | Create Maven parent POM | T1A | Root `pom.xml` as parent with `<modules>` for backend and gateway. Java 25, Spring Boot 4 parent, dependency management for shared deps (spring-cloud, testcontainers, caffeine). ~1 file. |
| T1.6 | Create backend Maven project skeleton | T1B | `backend/pom.xml` (spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, postgresql driver, flyway-core, caffeine, spring-boot-starter-oauth2-resource-server, spring-boot-starter-test, testcontainers-postgresql). `backend/src/main/java/io/github/rakheendama/starter/StarterApplication.java` (@SpringBootApplication, @EnableSpringDataWebSupport VIA_DTO, virtual threads enabled). `backend/src/main/resources/application.yml` (datasource, JPA, flyway, virtual threads). `backend/src/main/resources/application-local.yml` (local Postgres + Keycloak URLs). ~4 files. |
| T1.7 | Implement RequestScopes and ScopedFilterChain | T1B | `multitenancy/RequestScopes.java` -- ScopedValue declarations: TENANT_ID (String), ORG_ID (String), MEMBER_ID (UUID), ORG_ROLE (String), CUSTOMER_ID (UUID). Convenience methods: requireMemberId(), requireTenantId(), requireOwner(), isPlatformAdmin(). `multitenancy/ScopedFilterChain.java` -- bridges ScopedValue.where().run() with FilterChain.doFilter() checked exceptions (wraps IOException/ServletException). ~2 files. |
| T1.8 | Implement Hibernate multitenancy components | T1B | `multitenancy/SchemaMultiTenantConnectionProvider.java` -- implements MultiTenantConnectionProvider<String>, sets `SET search_path TO <schema>` on getConnection(), resets to public on releaseConnection(), validates schema name against `^tenant_[0-9a-f]{12}$` regex. `multitenancy/TenantIdentifierResolver.java` -- implements CurrentTenantIdentifierResolver<String>, reads RequestScopes.TENANT_ID, returns "public" when unbound. `config/HibernateMultiTenancyConfig.java` -- registers provider + resolver via HibernatePropertiesCustomizer (boot.hibernate.autoconfigure package in Boot 4). ~3 files. |
| T1.9 | Implement OrgSchemaMapping and SchemaNameGenerator | T1B | `multitenancy/OrgSchemaMapping.java` -- @Entity @Table(name="org_schema_mappings", schema="public"), fields: id (UUID PK), orgId (String, unique), schemaName (String, unique), createdAt (Instant). Protected no-arg constructor. `multitenancy/OrgSchemaMappingRepository.java` -- JpaRepository, findByOrgId(String). `multitenancy/SchemaNameGenerator.java` -- static method generate(orgSlug) returns "tenant_" + first 12 hex chars of SHA-256(orgSlug). ~3 files. |
| T1.10 | Implement FlywayConfig and exception handlers | T1B | `config/FlywayConfig.java` -- configures dual Flyway: public migrations (db/migration/public/) run at startup, tenant migrations (db/migration/tenant/) run per-schema at startup for existing tenants. `exception/ResourceNotFoundException.java`, `exception/ForbiddenException.java`, `exception/InvalidStateException.java`, `exception/GlobalExceptionHandler.java` (@RestControllerAdvice, maps to RFC 9457 ProblemDetail). ~5 files. |
| T1.11 | Create public schema migrations | T1C | `db/migration/public/V1__create_access_requests.sql` -- table with all fields from Section 2.3, indexes idx_access_requests_status and idx_access_requests_email. `db/migration/public/V2__create_org_schema_mappings.sql` -- table with unique constraints on org_id and schema_name. ~2 files. |
| T1.12 | Create tenant schema migrations (V1-V2) | T1C | `db/migration/tenant/V1__create_organizations.sql` -- organizations table with keycloak_org_id unique constraint. `db/migration/tenant/V2__create_members.sql` -- members table with keycloak_user_id unique constraint, indexes on status and email. ~2 files. |
| T1.13 | Create Testcontainers configuration | T1C | `backend/src/test/java/io/github/rakheendama/starter/TestcontainersConfiguration.java` (public class, @TestConfiguration, @ServiceConnection PostgreSQLContainer). `backend/src/test/java/io/github/rakheendama/starter/TestStarterApplication.java` (test entry point with Testcontainers). ~2 files. |
| T1.14 | Write ScopedValue unit tests | T1C | `backend/src/test/java/io/github/rakheendama/starter/multitenancy/RequestScopesTest.java` -- ~6 tests: TENANT_ID binds and resolves, MEMBER_ID binds UUID correctly, requireMemberId() throws when unbound, requireOwner() throws when role is "member", nested ScopedValue bindings work, values automatically unbound after lambda exits. |
| T1.15 | Write Flyway + schema routing integration tests | T1C | `backend/src/test/java/io/github/rakheendama/starter/multitenancy/SchemaRoutingIntegrationTest.java` -- @SpringBootTest + Testcontainers. ~5 tests: public migrations run on startup, can create schema manually and run tenant migrations, OrgSchemaMapping persists and retrieves, SchemaNameGenerator produces deterministic names, SchemaMultiTenantConnectionProvider sets search_path correctly. |

### Key Files

**Slice T1A:**
- `compose/docker-compose.yml`
- `compose/data/postgres/init.sql`
- `compose/scripts/dev-up.sh`
- `compose/scripts/dev-down.sh`
- `compose/keycloak/realm-export.json`
- `pom.xml` (root parent)
- `README.md`, `CLAUDE.md`, `LICENSE`, `.gitignore`

**Slice T1B:**
- `backend/pom.xml`
- `backend/src/main/java/io/github/rakheendama/starter/StarterApplication.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/RequestScopes.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/ScopedFilterChain.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/SchemaMultiTenantConnectionProvider.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/TenantIdentifierResolver.java`
- `backend/src/main/java/io/github/rakheendama/starter/config/HibernateMultiTenancyConfig.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/OrgSchemaMapping.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/OrgSchemaMappingRepository.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/SchemaNameGenerator.java`
- `backend/src/main/java/io/github/rakheendama/starter/config/FlywayConfig.java`
- `backend/src/main/java/io/github/rakheendama/starter/exception/ResourceNotFoundException.java`
- `backend/src/main/java/io/github/rakheendama/starter/exception/ForbiddenException.java`
- `backend/src/main/java/io/github/rakheendama/starter/exception/InvalidStateException.java`
- `backend/src/main/java/io/github/rakheendama/starter/exception/GlobalExceptionHandler.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`

**Slice T1C:**
- `backend/src/main/resources/db/migration/public/V1__create_access_requests.sql`
- `backend/src/main/resources/db/migration/public/V2__create_org_schema_mappings.sql`
- `backend/src/main/resources/db/migration/tenant/V1__create_organizations.sql`
- `backend/src/main/resources/db/migration/tenant/V2__create_members.sql`
- `backend/src/test/java/io/github/rakheendama/starter/TestcontainersConfiguration.java`
- `backend/src/test/java/io/github/rakheendama/starter/TestStarterApplication.java`
- `backend/src/test/java/io/github/rakheendama/starter/multitenancy/RequestScopesTest.java`
- `backend/src/test/java/io/github/rakheendama/starter/multitenancy/SchemaRoutingIntegrationTest.java`

### Architecture Decisions

- **ADR-T001**: Schema-per-tenant over row-level isolation -- all tenant data in dedicated schemas, no `tenant_id` columns
- **ADR-T002**: ScopedValues over ThreadLocal -- immutable, auto-cleanup, virtual-thread-safe context propagation

---

## Epic T2 -- Gateway + Auth

**Goal**: Create the Spring Cloud Gateway as a BFF (Backend-for-Frontend) with OAuth2 login via Keycloak, server-side session management, CSRF protection, and token relay. Configure the backend as a JWT resource server. Implement TenantFilter and MemberFilter for request-scoped context binding. Create the Keycloak bootstrap script.

**Dependencies**: T1 (Postgres, schema infrastructure, multitenancy core)

**Estimated Effort**: L (3 slices)

### Slices

| Slice | Scope | Description | Effort |
|-------|-------|-------------|--------|
| T2A | Gateway Maven project + security config | Gateway skeleton, GatewaySecurityConfig (OAuth2 login, CSRF, session management, route config with TokenRelay), BFF endpoints (/bff/me, /bff/csrf, /bff/logout), Spring Session JDBC, application.yml | M |
| T2B | Backend security + filters | SecurityConfig (JWT resource server), JwtUtils, PlatformSecurityService, TenantFilter (JWT org claim -> schema lookup -> ScopedValue binding), MemberFilter (user -> member lookup -> ScopedValue binding), Caffeine cache for OrgSchemaMapping | M |
| T2C | Keycloak bootstrap + integration tests | keycloak-bootstrap.sh script, gateway integration tests (OAuth2 flow, BFF endpoints), backend filter tests (JWT validation, tenant resolution, 401 on unauthenticated) | M |

### Tasks

| ID | Task | Slice | Notes |
|----|------|-------|-------|
| T2.1 | Create gateway Maven project skeleton | T2A | `gateway/pom.xml` (spring-cloud-gateway-mvc, spring-boot-starter-oauth2-client, spring-session-jdbc, spring-boot-starter-test). `gateway/src/main/java/io/github/rakheendama/starter/gateway/GatewayApplication.java` (@SpringBootApplication). `gateway/src/main/resources/application.yml` (route config, session, OAuth2 client registration for Keycloak). ~4 files. |
| T2.2 | Implement GatewaySecurityConfig | T2A | `gateway/src/main/java/io/github/rakheendama/starter/gateway/config/GatewaySecurityConfig.java` -- SecurityFilterChain with: permitAll for /, /error, /actuator/health, /bff/me, /bff/csrf, /api/access-requests, /api/access-requests/verify, /api/portal/**; denyAll for /internal/**; authenticated for /api/** and everything else. OAuth2 login with defaultSuccessUrl to /dashboard. Logout with OIDC back-channel, session invalidation, cookie deletion. CSRF with CookieCsrfTokenRepository (HttpOnly=false), ignoring /bff/**. Session creation IF_REQUIRED, session fixation via changeSessionId(). ~1 file, ~80 lines. |
| T2.3 | Implement BFF endpoints | T2A | `gateway/src/main/java/io/github/rakheendama/starter/gateway/BffController.java` -- GET /bff/me (returns authenticated status, email, name, orgId from OAuth2 session), GET /bff/csrf (returns CSRF token), POST /bff/logout (invalidates session, OIDC logout). DTOs as nested records. ~1 file, ~60 lines. |
| T2.4 | Configure gateway routes with TokenRelay | T2A | In `gateway/src/main/resources/application.yml` -- route: id=backend-api, uri=${BACKEND_URL:http://localhost:8080}, predicate=Path=/api/**, filter=TokenRelay. Spring Session JDBC config (same Postgres instance, public schema). Session timeout 8 hours. ~1 file (modify application.yml from T2.1). |
| T2.5 | Implement backend SecurityConfig | T2B | `backend/src/main/java/io/github/rakheendama/starter/config/SecurityConfig.java` -- JWT resource server with Keycloak JWKS URI. Authorization rules: permitAll for /actuator/health, /api/access-requests, /api/access-requests/verify, /api/portal/auth/**; authenticated for /api/**. No CSRF (stateless backend -- gateway handles CSRF). ~1 file. |
| T2.6 | Implement JwtUtils | T2B | `backend/src/main/java/io/github/rakheendama/starter/security/JwtUtils.java` -- static utility methods: extractOrgId(Jwt) reads "organization" claim (Keycloak org alias), extractSub(Jwt) reads "sub" claim, extractEmail(Jwt) reads "email" claim, extractName(Jwt) reads "name" claim, extractGroups(Jwt) reads "groups" claim as List<String>. ~1 file. |
| T2.7 | Implement PlatformSecurityService | T2B | `backend/src/main/java/io/github/rakheendama/starter/security/PlatformSecurityService.java` -- @Service, isPlatformAdmin(Jwt): checks if "groups" claim contains "platform-admins". requirePlatformAdmin(Jwt): calls isPlatformAdmin, throws ForbiddenException if false. ~1 file. |
| T2.8 | Implement TenantFilter | T2B | `backend/src/main/java/io/github/rakheendama/starter/multitenancy/TenantFilter.java` -- OncePerRequestFilter. Skips public paths (/api/access-requests, /api/portal/auth/**). Extracts JWT from SecurityContextHolder, reads org claim via JwtUtils.extractOrgId(), looks up OrgSchemaMapping (with Caffeine cache, 5-min TTL, 10K max entries), returns 403 if mapping not found. Binds TENANT_ID and ORG_ID via ScopedFilterChain. Order: after Spring Security filter. ~1 file, ~80 lines. |
| T2.9 | Implement MemberFilter | T2B | `backend/src/main/java/io/github/rakheendama/starter/multitenancy/MemberFilter.java` -- OncePerRequestFilter, runs after TenantFilter. Reads JWT sub claim, queries MemberRepository.findByKeycloakUserId() (within tenant schema via TENANT_ID already bound). If no member found, delegates to MemberSyncService (created in T4, for now just skips binding). Binds MEMBER_ID and ORG_ROLE via ScopedFilterChain. ~1 file, ~60 lines. |
| T2.10 | Add Caffeine cache for OrgSchemaMapping | T2B | `backend/src/main/java/io/github/rakheendama/starter/config/CacheConfig.java` -- @Configuration, defines Caffeine cache bean for OrgSchemaMapping lookups (5-min TTL, 10K max entries). Integrate into TenantFilter. Also add `config/WebConfig.java` for CORS and JSON serialization (Jackson with Instant as ISO-8601). ~2 files. |
| T2.11 | Create keycloak-bootstrap.sh | T2C | `compose/scripts/keycloak-bootstrap.sh` -- authenticates to Keycloak Admin API, creates `platform-admins` group (if not exists), creates initial admin user (configurable email/password via env vars), adds user to group. Uses `kcadm.sh` via Docker exec. Idempotent. ~1 file. Update `dev-up.sh` to call this script. |
| T2.12 | Write gateway BFF integration tests | T2C | `gateway/src/test/java/io/github/rakheendama/starter/gateway/BffControllerTest.java` -- ~4 tests: /bff/me returns unauthenticated when no session, /bff/csrf returns token, /bff/me returns user info when authenticated (mock OAuth2), authenticated request includes session cookie. Uses @WebMvcTest with mocked OAuth2. |
| T2.13 | Write backend filter integration tests | T2C | `backend/src/test/java/io/github/rakheendama/starter/security/SecurityIntegrationTest.java` -- @SpringBootTest + Testcontainers + MockMvc. ~6 tests: unauthenticated GET /api/projects returns 401, authenticated with valid JWT and existing OrgSchemaMapping resolves tenant, authenticated without OrgSchemaMapping returns 403, /api/access-requests is public (no auth required), /actuator/health is public, JwtUtils extracts claims correctly. Seed OrgSchemaMapping + tenant schema for positive tests. |

### Key Files

**Slice T2A:**
- `gateway/pom.xml`
- `gateway/src/main/java/io/github/rakheendama/starter/gateway/GatewayApplication.java`
- `gateway/src/main/java/io/github/rakheendama/starter/gateway/config/GatewaySecurityConfig.java`
- `gateway/src/main/java/io/github/rakheendama/starter/gateway/BffController.java`
- `gateway/src/main/resources/application.yml`

**Slice T2B:**
- `backend/src/main/java/io/github/rakheendama/starter/config/SecurityConfig.java`
- `backend/src/main/java/io/github/rakheendama/starter/security/JwtUtils.java`
- `backend/src/main/java/io/github/rakheendama/starter/security/PlatformSecurityService.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/TenantFilter.java`
- `backend/src/main/java/io/github/rakheendama/starter/multitenancy/MemberFilter.java`
- `backend/src/main/java/io/github/rakheendama/starter/config/CacheConfig.java`
- `backend/src/main/java/io/github/rakheendama/starter/config/WebConfig.java`

**Slice T2C:**
- `compose/scripts/keycloak-bootstrap.sh`
- `gateway/src/test/java/io/github/rakheendama/starter/gateway/BffControllerTest.java`
- `backend/src/test/java/io/github/rakheendama/starter/security/SecurityIntegrationTest.java`

### Architecture Decisions

- **ADR-T004**: Gateway BFF over direct API access -- tokens never reach the browser, HttpOnly session cookies, transparent token refresh, CSRF enforcement at the gateway layer

---

## Epic T3 -- Access Request + Provisioning

**Goal**: Implement the complete tenant registration pipeline: public access request submission with OTP email verification, platform admin review queue (approve/reject), and the full idempotent provisioning pipeline (Keycloak org creation, PostgreSQL schema creation, Flyway tenant migrations, OrgSchemaMapping commit marker, Keycloak user invitation). Build the frontend request-access wizard and platform admin queue page.

**Dependencies**: T2 (Gateway auth, Keycloak configured, JWT validation)

**Estimated Effort**: L (4 slices)

### Slices

| Slice | Scope | Description | Effort |
|-------|-------|-------------|--------|
| T3A | Backend: AccessRequest entity + OTP + public endpoints | AccessRequest entity, repo, service (submit + OTP verify), OtpService, AccessRequestController (public endpoints), unit + integration tests | M |
| T3B | Backend: Platform admin + provisioning pipeline | PlatformAdminController, AccessRequestApprovalService, TenantProvisioningService (7-step idempotent), KeycloakProvisioningClient, integration tests | M |
| T3C | Frontend: Request Access wizard | Next.js frontend scaffold (package.json, next.config.ts, layout, globals.css), landing page, multi-step request-access form (form -> OTP -> confirmation), Shadcn UI setup, lib/api.ts, Zod schemas | M |
| T3D | Frontend: Platform Admin queue | Platform admin layout + access-requests page, tabbed queue (PENDING/APPROVED/REJECTED), approve/reject actions with confirmation dialogs | S |

### Tasks

| ID | Task | Slice | Notes |
|----|------|-------|-------|
| T3.1 | Create AccessRequest entity | T3A | `accessrequest/AccessRequest.java` -- @Entity @Table(name="access_requests", schema="public"). All fields from Section 2.3: id (UUID PK), email, fullName, organizationName, country, industry, status (default PENDING_VERIFICATION), otpHash, otpExpiresAt, otpAttempts, otpVerifiedAt, reviewedBy, reviewedAt, keycloakOrgId, provisioningError, createdAt, updatedAt. Mutation methods: verifyOtp(), approve(reviewedBy), reject(reviewedBy), setProvisioningError(). Protected no-arg constructor. ~1 file. |
| T3.2 | Create AccessRequestRepository | T3A | `accessrequest/AccessRequestRepository.java` -- JpaRepository. Methods: findByEmailAndStatus(String email, String status), findByStatus(String status), findAllByOrderByCreatedAtDesc(). ~1 file. |
| T3.3 | Implement OtpService | T3A | `accessrequest/OtpService.java` -- @Service. Methods: generateOtp() returns 6-digit string (SecureRandom), hashOtp(String) returns BCrypt hash, verifyOtp(String raw, String hash) returns boolean via BCrypt.matches(). Configurable expiry (default 10 min). ~1 file. |
| T3.4 | Implement AccessRequestService | T3A | `accessrequest/AccessRequestService.java` -- @Service. Methods: submitRequest(dto) creates AccessRequest, generates OTP, hashes it, stores on entity, sends OTP email (via JavaMailSender or simple SMTP), returns response. verifyOtp(email, otp) loads request by email+status, checks expiry, checks attempts (max 5), verifies OTP hash, transitions to PENDING. listRequests(status filter). ~1 file. |
| T3.5 | Implement AccessRequestController | T3A | `accessrequest/AccessRequestController.java` -- @RestController @RequestMapping("/api/access-requests"). POST / (submit request, public), POST /verify (verify OTP, public). DTOs: SubmitAccessRequestRequest (email, fullName, organizationName, country, industry), VerifyOtpRequest (email, otp). ~1 file. |
| T3.6 | Write AccessRequest unit + integration tests | T3A | `accessrequest/AccessRequestServiceTest.java` -- ~5 unit tests: submit creates with PENDING_VERIFICATION, OTP generates 6 digits, BCrypt hash/verify works, max attempts enforcement, expired OTP rejection. `accessrequest/AccessRequestIntegrationTest.java` -- @SpringBootTest + Testcontainers. ~6 tests: POST /api/access-requests returns 200, POST /api/access-requests/verify with correct OTP transitions to PENDING, incorrect OTP increments attempts, expired OTP returns 400, exceed max attempts returns 400, duplicate email handling. ~2 files. |
| T3.7 | Implement PlatformAdminController | T3B | `accessrequest/PlatformAdminController.java` -- @RestController @RequestMapping("/api/platform-admin/access-requests"). GET / (list, optional status filter), POST /{id}/approve, POST /{id}/reject. Injects PlatformSecurityService for admin check on each endpoint. DTOs: AccessRequestResponse. ~1 file. |
| T3.8 | Implement AccessRequestApprovalService | T3B | `accessrequest/AccessRequestApprovalService.java` -- @Service, non-@Transactional (each step manages own TX). approve(requestId, adminEmail): (1) load AccessRequest, validate status=PENDING, (2) call KeycloakProvisioningClient.createOrganization, persist keycloakOrgId, (3) call TenantProvisioningService.provisionTenant, (4) call KeycloakProvisioningClient.inviteUser + setOrgCreator, (5) mark APPROVED. reject(requestId, adminEmail): load, validate PENDING, mark REJECTED. Each step wrapped in try/catch, errors stored in provisioningError. ~1 file, ~100 lines. |
| T3.9 | Implement TenantProvisioningService | T3B | `provisioning/TenantProvisioningService.java` -- @Service. provisionTenant(orgSlug, orgName, keycloakOrgId): 7-step idempotent pipeline per Section 3.5. Step 1: check OrgSchemaMapping exists (early return). Step 2: find-or-create Organization record (IN_PROGRESS). Step 3: generate schema name (SchemaNameGenerator). Step 4: CREATE SCHEMA IF NOT EXISTS (JdbcTemplate). Step 5: run Flyway tenant migrations (Flyway programmatic API with schema-specific DataSource). Step 6: create OrgSchemaMapping (commit marker). Step 7: mark Organization COMPLETED. Uses TransactionTemplate for each step. `provisioning/ProvisioningResult.java` -- record (success/alreadyProvisioned). `provisioning/ProvisioningException.java`. ~3 files. |
| T3.10 | Implement KeycloakProvisioningClient | T3B | `provisioning/KeycloakProvisioningClient.java` -- @Service. Uses RestClient to call Keycloak Admin REST API. Methods: createOrganization(name, slug) -- POST /admin/realms/starter/organizations (check-then-create), returns orgId. inviteUser(keycloakOrgId, email) -- POST /admin/realms/starter/organizations/{id}/members/invite-user. setOrgCreator(keycloakOrgId, email) -- sets user as org creator attribute. Configurable base URL, client credentials. ~1 file, ~80 lines. |
| T3.11 | Write provisioning integration tests | T3B | `provisioning/TenantProvisioningIntegrationTest.java` -- @SpringBootTest + Testcontainers. ~6 tests: successful provisioning creates schema + runs migrations + creates OrgSchemaMapping, idempotent retry (run twice, no errors), partial failure recovery (mock Flyway failure, retry succeeds), schema name generation is deterministic, OrgSchemaMapping is commit marker (absent = not provisioned), Organization transitions to COMPLETED. `accessrequest/PlatformAdminIntegrationTest.java` -- ~4 tests: non-admin cannot access platform admin endpoints (403), admin can list requests, admin can approve (full pipeline), admin can reject. ~2 files. |
| T3.12 | Create Next.js frontend scaffold | T3C | `frontend/package.json` (next 16, react 19, tailwindcss v4, shadcn dependencies, zod, react-hook-form, @hookform/resolvers, lucide-react, swr). `frontend/next.config.ts` (rewrites for /api/**, /bff/**, /oauth2/**, /login/**, /logout to gateway). `frontend/tsconfig.json` (@/* path alias). `frontend/postcss.config.mjs` (tailwindcss). `frontend/eslint.config.mjs`. `frontend/app/layout.tsx` (root layout with fonts: Sora display, IBM Plex Sans body, JetBrains Mono code). `frontend/app/globals.css` (Tailwind v4 CSS-first config, slate OKLCH scale, teal accent, semantic tokens). `frontend/lib/utils.ts` (cn() helper). ~8 files. |
| T3.13 | Set up Shadcn UI components | T3C | Initialize Shadcn (components.json with new-york style, RSC enabled, lucide icons). Install base components: Button, Input, Card, Dialog, AlertDialog, Badge, Table, Tabs, Form, Label, Textarea, Select, Toast/Sonner, DropdownMenu. Customize for slate palette + teal accent per design system. Files in `frontend/components/ui/`. ~15 component files. |
| T3.14 | Create lib/api.ts and lib/auth.ts | T3C | `frontend/lib/api.ts` -- apiGet<T>(path), apiPost<T>(path, body), apiPut, apiPatch, apiDelete. Uses fetch with credentials: 'include', handles errors. `frontend/lib/auth.ts` -- getSession() fetches /bff/me, returns { authenticated, email, name, orgId }. ~2 files. |
| T3.15 | Create landing page | T3C | `frontend/app/(public)/page.tsx` -- landing page with "Request Access" CTA button. Brief product description. Links to /request-access. Server Component. ~1 file + minimal components. |
| T3.16 | Create Request Access wizard | T3C | `frontend/app/(public)/request-access/page.tsx` -- multi-step wizard (Client Component). Step 1 (Form): email, fullName, organizationName, country (Select), industry (Select). Zod schema validation. Calls POST /api/access-requests. Step 2 (OTP): 6-digit input, countdown timer, resend button. Calls POST /api/access-requests/verify. Step 3 (Confirmation): success message. `frontend/lib/schemas/access-request.ts` -- Zod schemas for form + OTP. ~2-3 files. |
| T3.17 | Create platform admin layout and guard | T3D | `frontend/app/(platform-admin)/layout.tsx` -- checks getSession(), verifies isPlatformAdmin flag from /bff/me response, redirects to /dashboard if not admin. ~1 file. |
| T3.18 | Create platform admin access requests page | T3D | `frontend/app/(platform-admin)/platform-admin/access-requests/page.tsx` -- tabbed queue (ALL / PENDING / APPROVED / REJECTED). Fetches GET /api/platform-admin/access-requests. Table with columns: email, org name, country, industry, status, created_at, actions. Approve button opens confirmation dialog listing side effects. Reject button opens simple confirmation. Actions call POST endpoints. `frontend/components/platform-admin/access-request-table.tsx`, `frontend/components/platform-admin/approve-dialog.tsx`. ~3-4 files. |

### Key Files

**Slice T3A:**
- `backend/src/main/java/io/github/rakheendama/starter/accessrequest/AccessRequest.java`
- `backend/src/main/java/io/github/rakheendama/starter/accessrequest/AccessRequestRepository.java`
- `backend/src/main/java/io/github/rakheendama/starter/accessrequest/AccessRequestService.java`
- `backend/src/main/java/io/github/rakheendama/starter/accessrequest/AccessRequestController.java`
- `backend/src/main/java/io/github/rakheendama/starter/accessrequest/OtpService.java`
- `backend/src/test/java/io/github/rakheendama/starter/accessrequest/AccessRequestServiceTest.java`
- `backend/src/test/java/io/github/rakheendama/starter/accessrequest/AccessRequestIntegrationTest.java`

**Slice T3B:**
- `backend/src/main/java/io/github/rakheendama/starter/accessrequest/PlatformAdminController.java`
- `backend/src/main/java/io/github/rakheendama/starter/accessrequest/AccessRequestApprovalService.java`
- `backend/src/main/java/io/github/rakheendama/starter/provisioning/TenantProvisioningService.java`
- `backend/src/main/java/io/github/rakheendama/starter/provisioning/ProvisioningResult.java`
- `backend/src/main/java/io/github/rakheendama/starter/provisioning/ProvisioningException.java`
- `backend/src/main/java/io/github/rakheendama/starter/provisioning/KeycloakProvisioningClient.java`
- `backend/src/test/java/io/github/rakheendama/starter/provisioning/TenantProvisioningIntegrationTest.java`
- `backend/src/test/java/io/github/rakheendama/starter/accessrequest/PlatformAdminIntegrationTest.java`

**Slice T3C:**
- `frontend/package.json`
- `frontend/next.config.ts`
- `frontend/app/layout.tsx`
- `frontend/app/globals.css`
- `frontend/lib/api.ts`
- `frontend/lib/auth.ts`
- `frontend/app/(public)/page.tsx`
- `frontend/app/(public)/request-access/page.tsx`
- `frontend/lib/schemas/access-request.ts`
- `frontend/components/ui/` (Shadcn components)

**Slice T3D:**
- `frontend/app/(platform-admin)/layout.tsx`
- `frontend/app/(platform-admin)/platform-admin/access-requests/page.tsx`
- `frontend/components/platform-admin/access-request-table.tsx`
- `frontend/components/platform-admin/approve-dialog.tsx`

### Architecture Decisions

- **ADR-T007**: Idempotent provisioning pipeline -- every step uses IF NOT EXISTS / upsert semantics, OrgSchemaMapping is the commit marker, safe to retry from any failure point
- **ADR-T001**: Schema-per-tenant -- provisioning creates a dedicated PostgreSQL schema per tenant

---

## Epic T4 -- Members + Invitations

**Goal**: Implement member sync on first login (creating a member record from JWT claims), member CRUD (list, invite, remove, role change), Keycloak invitation integration, and the frontend members page with invitation dialog. Establish the owner/member RBAC model.

**Dependencies**: T3 (provisioned tenant with owner member ready to sync)

**Estimated Effort**: M (2 slices)

### Slices

| Slice | Scope | Description | Effort |
|-------|-------|-------------|--------|
| T4A | Backend: Member entity + sync + CRUD + tests | Member entity, repo, MemberSyncService, MemberService (invite, remove, role change), MemberController, complete MemberFilter integration, integration tests | M |
| T4B | Frontend: Members page + invite dialog | App layout with sidebar, Members page with member list, invite dialog (owner only), role change and remove actions | M |

### Tasks

| ID | Task | Slice | Notes |
|----|------|-------|-------|
| T4.1 | Create Member entity and repository | T4A | `member/Member.java` -- @Entity @Table(name="members"), fields from Section 2.3: id (UUID PK), keycloakUserId (String, unique), email, displayName, role ("owner" or "member"), status ("ACTIVE" or "SUSPENDED"), firstLoginAt, lastLoginAt, createdAt, updatedAt. Mutation methods: syncProfile(email, displayName), changeRole(newRole), suspend(), activate(). `member/MemberRepository.java` -- findByKeycloakUserId(String), findByEmail(String), findByStatus(String). ~2 files. |
| T4.2 | Implement MemberSyncService | T4A | `member/MemberSyncService.java` -- @Service. syncOrCreate(Jwt jwt, String keycloakOrgId): looks up by keycloakUserId. If not found: creates new member, assigns role ("owner" if org creator detected via Keycloak API check or first member, "member" otherwise), sets firstLoginAt. If found: syncs email + displayName from JWT claims (not role), updates lastLoginAt. Returns Member. ~1 file, ~60 lines. |
| T4.3 | Complete MemberFilter integration | T4A | Update `multitenancy/MemberFilter.java` (from T2.9) to call MemberSyncService.syncOrCreate() when no member found for the JWT sub. After sync/lookup, bind MEMBER_ID and ORG_ROLE via ScopedFilterChain. Handles the "first login" case where the member record does not yet exist. ~1 file (modify existing). |
| T4.4 | Implement MemberService | T4A | `member/MemberService.java` -- @Service. listMembers(): findAll(). getCurrentMember(): findById(RequestScopes.requireMemberId()). inviteMember(email): calls KeycloakProvisioningClient.inviteUser(), returns acknowledgment (member record created on their first login). removeMember(memberId): validates caller is owner (RequestScopes.requireOwner()), cannot remove self, deletes member. changeRole(memberId, newRole): validates caller is owner, validates newRole is "owner" or "member", updates role. ~1 file, ~80 lines. |
| T4.5 | Implement MemberController | T4A | `member/MemberController.java` -- @RestController @RequestMapping("/api/members"). GET / (list members), GET /me (current member profile), POST /invite (owner only, sends Keycloak invite), DELETE /{id} (owner only, remove member), PATCH /{id}/role (owner only, change role). DTOs: MemberResponse, InviteMemberRequest (email), ChangeRoleRequest (role). Pure delegation to MemberService. ~1 file. |
| T4.6 | Write Member integration tests | T4A | `member/MemberIntegrationTest.java` -- @SpringBootTest + Testcontainers + MockMvc. ~10 tests: first login creates member with owner role, subsequent login syncs identity but not role, list members returns all in tenant, GET /me returns current member, invite sends Keycloak API call (mock Keycloak), owner can change role, member cannot change role (403), owner can remove member, member cannot remove (403), cannot remove self (400). Seed: provision a test tenant schema, create initial owner member. ~1 file. |
| T4.7 | Create Organization entity and repository | T4A | `organization/Organization.java` -- @Entity @Table(name="organizations"), fields: id (UUID PK), keycloakOrgId (unique), name, slug, status (IN_PROGRESS/COMPLETED/FAILED), createdAt, updatedAt. Mutation methods: markCompleted(), markFailed(error). `organization/OrganizationRepository.java` -- findByKeycloakOrgId(String). ~2 files. |
| T4.8 | Create authenticated app layout with sidebar | T4B | `frontend/app/(app)/layout.tsx` -- calls getSession(), redirects to OAuth2 login if unauthenticated. Renders sidebar (navigation items: Dashboard, Projects, Customers, Members, Settings) + header (org name, user menu with logout). `frontend/components/app-sidebar.tsx` (sidebar nav component using Shadcn). `frontend/components/app-header.tsx` (header with user dropdown). `frontend/lib/nav-items.ts` (navigation item definitions). ~4 files. |
| T4.9 | Create Members page | T4B | `frontend/app/(app)/members/page.tsx` -- Server Component, fetches GET /api/members. Renders member table (name, email, role badge, status, joined date). Invite button visible only for owners (check from /bff/me or /api/members/me). Remove and role change actions in row dropdown (owner only). `frontend/components/members/member-table.tsx` (table component with action dropdown). ~2 files. |
| T4.10 | Create Invite Member dialog | T4B | `frontend/components/members/invite-dialog.tsx` -- Client Component, Shadcn Dialog with email input. Zod schema validation. On submit: POST /api/members/invite. Success toast notification. `frontend/lib/schemas/member.ts` -- Zod schema for invite form. ~2 files. |
| T4.11 | Create role change and remove dialogs | T4B | `frontend/components/members/change-role-dialog.tsx` -- Select between "owner" and "member", confirm with AlertDialog. PATCH /api/members/{id}/role. `frontend/components/members/remove-member-dialog.tsx` -- AlertDialog confirmation, DELETE /api/members/{id}. ~2 files. |

### Key Files

**Slice T4A:**
- `backend/src/main/java/io/github/rakheendama/starter/member/Member.java`
- `backend/src/main/java/io/github/rakheendama/starter/member/MemberRepository.java`
- `backend/src/main/java/io/github/rakheendama/starter/member/MemberSyncService.java`
- `backend/src/main/java/io/github/rakheendama/starter/member/MemberService.java`
- `backend/src/main/java/io/github/rakheendama/starter/member/MemberController.java`
- `backend/src/main/java/io/github/rakheendama/starter/organization/Organization.java`
- `backend/src/main/java/io/github/rakheendama/starter/organization/OrganizationRepository.java`
- `backend/src/test/java/io/github/rakheendama/starter/member/MemberIntegrationTest.java`

**Slice T4B:**
- `frontend/app/(app)/layout.tsx`
- `frontend/app/(app)/members/page.tsx`
- `frontend/components/app-sidebar.tsx`
- `frontend/components/app-header.tsx`
- `frontend/components/members/member-table.tsx`
- `frontend/components/members/invite-dialog.tsx`
- `frontend/components/members/change-role-dialog.tsx`
- `frontend/components/members/remove-member-dialog.tsx`

### Architecture Decisions

- **ADR-T003**: Product-layer roles over Keycloak org roles -- roles stored in members.role column, not in Keycloak, enabling simple JPA queries and independence from the identity provider

---

## Epic T5 -- Domain CRUD (Customer, Project, Comment)

**Goal**: Implement the core domain entities (Customer, Project, Comment) with full CRUD operations, tenant-scoped data access, entity relationships (projects linked to customers, comments linked to projects), member-authored comments, and the frontend pages (dashboard, projects list/detail, customers list/detail, comment section).

**Dependencies**: T4 (authenticated members in a provisioned tenant)

**Estimated Effort**: L (4 slices)

### Slices

| Slice | Scope | Description | Effort |
|-------|-------|-------------|--------|
| T5A | Backend: Customer + Project entities + CRUD | Tenant migrations V3 (customers) + V4 (projects), Customer entity/repo/service/controller, Project entity/repo/service/controller, integration tests | M |
| T5B | Backend: Comment entity + member-authored CRUD | Tenant migration V5 (comments), Comment entity/repo/service/controller (member auth path), tenant isolation tests, comment integration tests | M |
| T5C | Frontend: Dashboard + Projects pages | Dashboard page (summary cards, recent projects), Projects list page, Project detail page (info card, status management, comment section) | M |
| T5D | Frontend: Customers pages | Customers list page, Customer detail page (info card, linked projects list), create/edit customer dialogs | S |

### Tasks

| ID | Task | Slice | Notes |
|----|------|-------|-------|
| T5.1 | Create tenant migration V3 (customers) | T5A | `db/migration/tenant/V3__create_customers.sql` -- table with fields from Section 2.3: id (UUID PK), name, email, company, status (default ACTIVE), created_by (FK members.id), created_at, updated_at. Indexes: idx_customers_status, idx_customers_email. ~1 file. |
| T5.2 | Create tenant migration V4 (projects) | T5A | `db/migration/tenant/V4__create_projects.sql` -- table: id (UUID PK), title, description (TEXT), status (default ACTIVE), customer_id (FK customers.id NOT NULL), created_by (FK members.id), created_at, updated_at. Indexes: idx_projects_customer_id, idx_projects_status. ~1 file. |
| T5.3 | Create Customer entity + repository + service + controller | T5A | `customer/Customer.java` -- @Entity @Table(name="customers"). Fields per schema. Mutation methods: updateDetails(name, email, company), archive(). No Lombok, protected no-arg constructor. `customer/CustomerRepository.java` -- findByStatus(String), findByEmail(String). `customer/CustomerService.java` -- listCustomers(), getCustomer(id), createCustomer(name, email, company), updateCustomer(id, name, email, company), archiveCustomer(id). Uses RequestScopes.requireMemberId() for created_by. `customer/CustomerController.java` -- GET /, GET /{id}, POST /, PUT /{id}, DELETE /{id} (archive). DTOs as nested records. ~4 files. |
| T5.4 | Create Project entity + repository + service + controller | T5A | `project/Project.java` -- @Entity @Table(name="projects"). Fields per schema. @ManyToOne(LAZY) to Customer and Member. Mutation methods: updateDetails(title, description), changeStatus(newStatus). `project/ProjectRepository.java` -- findByCustomerId(UUID), findByStatus(String), findByCustomerIdAndStatus(UUID, String). `project/ProjectService.java` -- listProjects(), getProject(id), createProject(title, description, customerId), updateProject(id, title, description), changeProjectStatus(id, status), archiveProject(id). `project/ProjectController.java` -- GET /, GET /{id}, POST /, PUT /{id}, PATCH /{id}/status, DELETE /{id}. ~4 files. |
| T5.5 | Write Customer + Project integration tests | T5A | `customer/CustomerIntegrationTest.java` -- ~6 tests: create customer (201), list customers, get customer by id, update customer, archive customer (soft delete), tenant isolation (customer in tenant A invisible from tenant B). `project/ProjectIntegrationTest.java` -- ~7 tests: create project linked to customer (201), project must have customer_id (400 without), list projects, get project detail includes customer info, update project, change status (ACTIVE -> COMPLETED -> ARCHIVED), tenant isolation. ~2 files. |
| T5.6 | Create tenant migration V5 (comments) | T5B | `db/migration/tenant/V5__create_comments.sql` -- table: id (UUID PK), project_id (FK projects.id ON DELETE CASCADE), content (TEXT NOT NULL), author_type (VARCHAR(20) NOT NULL, CHECK IN ('MEMBER','CUSTOMER')), author_id (UUID NOT NULL), author_name (VARCHAR(255)), created_at, updated_at. Indexes: idx_comments_project_id (project_id, created_at ASC), idx_comments_author (author_type, author_id). ~1 file. |
| T5.7 | Create Comment entity + repository | T5B | `comment/Comment.java` -- @Entity @Table(name="comments"). Fields per schema. author_type as String (MEMBER or CUSTOMER), author_id as UUID (polymorphic FK), author_name as denormalized String. Constructor for member comments: Comment(projectId, content, memberId, memberName) sets author_type=MEMBER. Separate constructor or factory method for customer comments. `comment/CommentRepository.java` -- findByProjectIdOrderByCreatedAtAsc(UUID), findByAuthorTypeAndAuthorId(String, UUID). ~2 files. |
| T5.8 | Create CommentService + CommentController (member path) | T5B | `comment/CommentService.java` -- listComments(projectId), addMemberComment(projectId, content) uses RequestScopes.requireMemberId() to resolve member and populate author_type=MEMBER. deleteMemberComment(commentId) verifies the caller is the comment author (author_type=MEMBER and author_id matches). `comment/CommentController.java` -- @RequestMapping("/api/projects/{projectId}/comments"): GET / (list), POST / (add member comment). @RequestMapping("/api/comments"): DELETE /{id} (delete own comment). ~2 files. |
| T5.9 | Write Comment integration tests | T5B | `comment/CommentIntegrationTest.java` -- ~8 tests: add member comment (201) stores author_type=MEMBER, list comments for project (chronological order), delete own comment (204), cannot delete another member's comment (403), comments linked to non-existent project (404), comment author_name denormalized correctly, tenant isolation (comments in tenant A invisible from tenant B), cascading delete (delete project removes its comments). ~1 file. |
| T5.10 | Create Dashboard page | T5C | `frontend/app/(app)/dashboard/page.tsx` -- Server Component. Fetches summary data (total projects by status, total customers, total members via respective GET endpoints). Renders summary cards (Card components with stat numbers). Recent projects list (last 5 with customer name). `frontend/components/dashboard/summary-cards.tsx`, `frontend/components/dashboard/recent-projects.tsx`. ~3 files. |
| T5.11 | Create Projects list page | T5C | `frontend/app/(app)/projects/page.tsx` -- Server Component, fetches GET /api/projects. Project table (title, customer, status badge, created date). Create project button opens dialog. `frontend/components/projects/project-list.tsx` (table component). `frontend/components/projects/create-project-dialog.tsx` (Client Component, form with title, description, customer select). `frontend/lib/schemas/project.ts` (Zod schema). ~4 files. |
| T5.12 | Create Project detail page | T5C | `frontend/app/(app)/projects/[id]/page.tsx` -- Server Component, fetches GET /api/projects/{id} and GET /api/projects/{id}/comments. Project info card (title, description, status, linked customer). Status change buttons (Active -> Completed -> Archived). Comments section: chronological list with MEMBER badge, add comment form. "Share Portal Link" button (placeholder, implemented in T6). `frontend/components/projects/project-detail.tsx`, `frontend/components/projects/comment-section.tsx`, `frontend/components/projects/comment-form.tsx`. ~4 files. |
| T5.13 | Create Customers list page | T5D | `frontend/app/(app)/customers/page.tsx` -- Server Component, fetches GET /api/customers. Customer table (name, email, company, status badge, created date). Create customer button opens dialog. `frontend/components/customers/customer-list.tsx` (table component). `frontend/components/customers/create-customer-dialog.tsx` (Client Component, form with name, email, company). `frontend/lib/schemas/customer.ts` (Zod schema). ~4 files. |
| T5.14 | Create Customer detail page | T5D | `frontend/app/(app)/customers/[id]/page.tsx` -- Server Component, fetches GET /api/customers/{id}. Customer info card (name, email, company, status). Edit button opens dialog. Linked projects list (fetches GET /api/projects?customerId={id}). `frontend/components/customers/customer-detail.tsx`, `frontend/components/customers/edit-customer-dialog.tsx`. ~3 files. |

### Key Files

**Slice T5A:**
- `backend/src/main/resources/db/migration/tenant/V3__create_customers.sql`
- `backend/src/main/resources/db/migration/tenant/V4__create_projects.sql`
- `backend/src/main/java/io/github/rakheendama/starter/customer/Customer.java`
- `backend/src/main/java/io/github/rakheendama/starter/customer/CustomerRepository.java`
- `backend/src/main/java/io/github/rakheendama/starter/customer/CustomerService.java`
- `backend/src/main/java/io/github/rakheendama/starter/customer/CustomerController.java`
- `backend/src/main/java/io/github/rakheendama/starter/project/Project.java`
- `backend/src/main/java/io/github/rakheendama/starter/project/ProjectRepository.java`
- `backend/src/main/java/io/github/rakheendama/starter/project/ProjectService.java`
- `backend/src/main/java/io/github/rakheendama/starter/project/ProjectController.java`
- `backend/src/test/java/io/github/rakheendama/starter/customer/CustomerIntegrationTest.java`
- `backend/src/test/java/io/github/rakheendama/starter/project/ProjectIntegrationTest.java`

**Slice T5B:**
- `backend/src/main/resources/db/migration/tenant/V5__create_comments.sql`
- `backend/src/main/java/io/github/rakheendama/starter/comment/Comment.java`
- `backend/src/main/java/io/github/rakheendama/starter/comment/CommentRepository.java`
- `backend/src/main/java/io/github/rakheendama/starter/comment/CommentService.java`
- `backend/src/main/java/io/github/rakheendama/starter/comment/CommentController.java`
- `backend/src/test/java/io/github/rakheendama/starter/comment/CommentIntegrationTest.java`

**Slice T5C:**
- `frontend/app/(app)/dashboard/page.tsx`
- `frontend/app/(app)/projects/page.tsx`
- `frontend/app/(app)/projects/[id]/page.tsx`
- `frontend/components/dashboard/summary-cards.tsx`
- `frontend/components/projects/project-list.tsx`
- `frontend/components/projects/create-project-dialog.tsx`
- `frontend/components/projects/comment-section.tsx`

**Slice T5D:**
- `frontend/app/(app)/customers/page.tsx`
- `frontend/app/(app)/customers/[id]/page.tsx`
- `frontend/components/customers/customer-list.tsx`
- `frontend/components/customers/create-customer-dialog.tsx`
- `frontend/components/customers/customer-detail.tsx`

### Architecture Decisions

- **ADR-T006**: Dual-author comments via discriminator -- single `comments` table with `author_type` (MEMBER/CUSTOMER) and polymorphic `author_id`, enabling unified timeline without UNION queries
- **ADR-T001**: Schema-per-tenant -- all domain CRUD is automatically tenant-scoped via Hibernate search_path, no WHERE tenant_id needed

---

## Epic T6 -- Portal + Magic Links

**Goal**: Implement the customer portal with magic link authentication -- magic link generation and email sending, token exchange for portal JWT (HS256), portal auth filter, portal endpoints (list projects, project detail, add customer comment), and the frontend portal pages (login, project list, project detail with comments). Wire the "Share Portal Link" button in the tenant app.

**Dependencies**: T5 (customers, projects, and comments exist in tenant schemas)

**Estimated Effort**: L (3 slices)

### Slices

| Slice | Scope | Description | Effort |
|-------|-------|-------------|--------|
| T6A | Backend: Magic link + portal JWT + auth filter | MagicLinkToken entity + repo, MagicLinkService (generate, hash, verify, consume, rate limit), PortalJwtService (HS256 issue + validate), PortalAuthController (request-link, exchange), PortalAuthFilter, public migration V3 (magic_link_tokens), integration tests | M |
| T6B | Backend: Portal endpoints + customer comments | PortalController (list projects, project detail, add comment), customer comment creation in CommentService, portal endpoint integration tests, cross-customer isolation tests | M |
| T6C | Frontend: Portal pages + Share Portal Link | Portal login page, portal layout, portal project list, portal project detail with comments, add comment form (customer path), "Share Portal Link" button on tenant app project detail | M |

### Tasks

| ID | Task | Slice | Notes |
|----|------|-------|-------|
| T6.1 | Create public migration V3 (magic_link_tokens) | T6A | `db/migration/public/V3__create_magic_link_tokens.sql` -- table: id (UUID PK), token_hash (VARCHAR(255) UNIQUE NOT NULL), customer_id (UUID NOT NULL), org_id (VARCHAR(255) NOT NULL), expires_at (TIMESTAMPTZ NOT NULL), used_at (TIMESTAMPTZ), created_ip (VARCHAR(45)), created_at (TIMESTAMPTZ NOT NULL). Indexes: idx_magic_link_tokens_customer_created (customer_id, created_at DESC), idx_magic_link_tokens_expires (partial, WHERE used_at IS NULL). ~1 file. |
| T6.2 | Create MagicLinkToken entity + repository | T6A | `portal/MagicLinkToken.java` -- @Entity @Table(name="magic_link_tokens", schema="public"). Fields per Section 2.3. Mutation method: markUsed(). `portal/MagicLinkTokenRepository.java` -- findByTokenHash(String), countByCustomerIdAndCreatedAtAfter(UUID, Instant) (for rate limiting). ~2 files. |
| T6.3 | Implement MagicLinkService | T6A | `portal/MagicLinkService.java` -- @Service. generateToken(customerId, orgId, clientIp): (1) rate limit check (3 per customer per 5 min via countByCustomerIdAndCreatedAtAfter), (2) generate 32 bytes SecureRandom, Base64-URL encode, (3) SHA-256 hash for storage, (4) persist MagicLinkToken with 15-min expiry, (5) return raw token (only time it exists in memory). exchangeToken(rawToken): (1) SHA-256 hash, (2) find by hash, (3) verify not expired, (4) verify not used (used_at is null), (5) mark used (SELECT FOR UPDATE to prevent race), (6) return customer_id + org_id. ~1 file, ~80 lines. |
| T6.4 | Implement PortalJwtService | T6A | `portal/PortalJwtService.java` -- @Service. Uses javax.crypto HS256 signing. Configurable secret via PORTAL_JWT_SECRET env var. issueToken(customerId, orgId): creates JWT with sub=customerId, org_id=orgId, type="customer", exp=1 hour, iat=now. validateToken(String jwt): verifies signature, expiry, returns claims (customerId UUID, orgId String). ~1 file, ~60 lines. |
| T6.5 | Implement PortalAuthController | T6A | `portal/PortalAuthController.java` -- @RestController @RequestMapping("/api/portal/auth"). POST /request-link: receives {email, orgId}, resolves org -> schema, finds customer by email in tenant schema, calls MagicLinkService.generateToken(), sends magic link email, returns generic response ("If an account exists, a link has been sent" -- anti-enumeration). POST /exchange: receives {token}, calls MagicLinkService.exchangeToken(), loads customer, calls PortalJwtService.issueToken(), returns {token, customerId, customerName}. Both endpoints are public (no auth required). ~1 file. |
| T6.6 | Implement PortalAuthFilter | T6A | `portal/PortalAuthFilter.java` -- OncePerRequestFilter, matches /api/portal/** (but NOT /api/portal/auth/**). Reads Authorization: Bearer <token> header. Calls PortalJwtService.validateToken(). Resolves org_id -> schema name via OrgSchemaMapping. Binds TENANT_ID, ORG_ID, CUSTOMER_ID via ScopedFilterChain. Returns 401 on invalid/missing token. ~1 file, ~60 lines. |
| T6.7 | Write magic link + portal JWT integration tests | T6A | `portal/MagicLinkIntegrationTest.java` -- @SpringBootTest + Testcontainers. ~10 tests: request-link sends email (mock SMTP), request-link for non-existent email returns same response (anti-enumeration), exchange valid token returns portal JWT, exchange expired token returns 401, exchange used token returns 401 (single-use), rate limit exceeded returns 429 (3 per 5 min), portal JWT contains correct claims (sub, org_id, type), portal JWT validates after issuance, expired portal JWT rejected, PortalAuthFilter binds ScopedValues correctly. Seed: provision tenant, create customer in tenant schema. ~1 file. |
| T6.8 | Implement PortalController | T6B | `portal/PortalController.java` -- @RestController @RequestMapping("/api/portal"). All endpoints require portal JWT (PortalAuthFilter). GET /projects: list projects WHERE customer_id = CUSTOMER_ID ScopedValue. GET /projects/{id}: project detail, verify project.customer.id matches CUSTOMER_ID (security). GET /projects/{id}/comments: list comments for project (same customer check). POST /projects/{id}/comments: add comment with author_type=CUSTOMER. ~1 file. |
| T6.9 | Add customer comment support to CommentService | T6B | Update `comment/CommentService.java` -- add addCustomerComment(projectId, content, customerId, customerName): creates Comment with author_type=CUSTOMER, author_id=customerId, author_name=customerName. PortalController calls this method. ~1 file (modify existing). |
| T6.10 | Write portal endpoint integration tests | T6B | `portal/PortalControllerIntegrationTest.java` -- ~8 tests: list projects scoped to portal JWT customer (only sees own projects), project detail for own project succeeds, project detail for another customer's project returns 404, list comments for own project, add customer comment (author_type=CUSTOMER), customer comment appears in unified timeline alongside member comments, both MEMBER and CUSTOMER badges present in response, portal JWT for customer A cannot see customer B's projects. Seed: provision tenant, create 2 customers, create projects for each. ~1 file. |
| T6.11 | Create portal login page | T6C | `frontend/app/portal/page.tsx` -- Client Component. Email input + org identifier input. Calls POST /api/portal/auth/request-link. On success: shows "Check your email" message. `frontend/app/portal/auth/exchange/page.tsx` -- reads token from URL query param, calls POST /api/portal/auth/exchange, stores portal JWT in localStorage, redirects to /portal/projects. `frontend/lib/portal-auth.ts` -- getPortalToken() reads from localStorage, isPortalAuthenticated(). `frontend/lib/schemas/portal.ts` -- Zod schemas. ~4 files. |
| T6.12 | Create portal layout | T6C | `frontend/app/portal/layout.tsx` -- checks for portal JWT in localStorage (via client-side check). Minimal layout with top navigation bar (customer name, logout). No sidebar. If no token, redirects to portal login page. `frontend/components/portal/portal-header.tsx` -- top nav with customer name and logout link. ~2 files. |
| T6.13 | Create portal project list page | T6C | `frontend/app/portal/projects/page.tsx` -- fetches GET /api/portal/projects with portal JWT in Authorization header. Card grid of projects (title, status badge, description preview). Click navigates to project detail. `frontend/components/portal/portal-project-card.tsx`. `frontend/lib/portal-api.ts` -- fetch wrapper that attaches portal JWT from localStorage as Bearer token. ~3 files. |
| T6.14 | Create portal project detail page | T6C | `frontend/app/portal/projects/[id]/page.tsx` -- fetches GET /api/portal/projects/{id} and GET /api/portal/projects/{id}/comments. Project info card. Comments section with MEMBER/CUSTOMER badges. Add comment form (customer path). `frontend/components/portal/portal-comment-section.tsx` (renders both member and customer comments with distinct badges), `frontend/components/portal/portal-comment-form.tsx` (textarea + submit). ~3 files. |
| T6.15 | Wire "Share Portal Link" button | T6C | Update `frontend/app/(app)/projects/[id]/page.tsx` (from T5.12) -- add "Share Portal Link" button that opens a dialog. Dialog shows customer email, calls POST /api/portal/auth/request-link to trigger magic link email. Confirmation toast on success. `frontend/components/projects/share-portal-dialog.tsx`. ~1-2 files. |

### Key Files

**Slice T6A:**
- `backend/src/main/resources/db/migration/public/V3__create_magic_link_tokens.sql`
- `backend/src/main/java/io/github/rakheendama/starter/portal/MagicLinkToken.java`
- `backend/src/main/java/io/github/rakheendama/starter/portal/MagicLinkTokenRepository.java`
- `backend/src/main/java/io/github/rakheendama/starter/portal/MagicLinkService.java`
- `backend/src/main/java/io/github/rakheendama/starter/portal/PortalJwtService.java`
- `backend/src/main/java/io/github/rakheendama/starter/portal/PortalAuthController.java`
- `backend/src/main/java/io/github/rakheendama/starter/portal/PortalAuthFilter.java`
- `backend/src/test/java/io/github/rakheendama/starter/portal/MagicLinkIntegrationTest.java`

**Slice T6B:**
- `backend/src/main/java/io/github/rakheendama/starter/portal/PortalController.java`
- `backend/src/test/java/io/github/rakheendama/starter/portal/PortalControllerIntegrationTest.java`

**Slice T6C:**
- `frontend/app/portal/page.tsx`
- `frontend/app/portal/layout.tsx`
- `frontend/app/portal/auth/exchange/page.tsx`
- `frontend/app/portal/projects/page.tsx`
- `frontend/app/portal/projects/[id]/page.tsx`
- `frontend/lib/portal-auth.ts`
- `frontend/lib/portal-api.ts`
- `frontend/components/portal/portal-header.tsx`
- `frontend/components/portal/portal-comment-section.tsx`
- `frontend/components/projects/share-portal-dialog.tsx`

### Architecture Decisions

- **ADR-T005**: Magic links over customer accounts -- zero-friction customer access, no Keycloak account bloat, scoped portal JWTs
- **ADR-T006**: Dual-author comments via discriminator -- both member and customer comments in same table, unified timeline

---

## Epic T7 -- Blog Series Drafts

**Goal**: Create the 10-part "Zero to Prod" blog series as Markdown drafts in the repository. Each post maps to a capability slice and references specific files in the codebase with GitHub permalink format. The blog tells a coherent narrative from infrastructure through security to portal.

**Dependencies**: T1-T6 (all code exists to reference)

**Estimated Effort**: M (2 slices)

### Slices

| Slice | Scope | Description | Effort |
|-------|-------|-------------|--------|
| T7A | Blog posts 01-05 (Foundation through Registration) | Posts covering architecture overview, dev environment, multitenancy core, gateway BFF, and tenant registration pipeline | M |
| T7B | Blog posts 06-10 (Members through Portal Comments) | Posts covering member sync, first domain entity, security hardening, magic link portal, and dual-auth comment writes | M |

### Tasks

| ID | Task | Slice | Notes |
|----|------|-------|-------|
| T7.1 | Create blog post 01: Architecture & Why Schema-Per-Tenant | T7A | `docs/blog/01-architecture-and-why-schema-per-tenant.md` -- system context Mermaid diagram, ER diagram, schema-per-tenant vs row-level comparison table, "what's in the box" feature list, tech stack table. References: architecture doc Sections 1, 2, 3.1. ~1 file, ~300 lines. |
| T7.2 | Create blog post 02: One-Command Dev Environment | T7A | `docs/blog/02-one-command-dev-environment.md` -- docker-compose.yml walkthrough, init.sql explanation, dev-up.sh and dev-down.sh, Keycloak realm import, keycloak-bootstrap.sh, Mailpit for email capture. References: compose/ directory files. ~1 file, ~250 lines. |
| T7.3 | Create blog post 03: The Multitenancy Core | T7A | `docs/blog/03-the-multitenancy-core.md` -- full RequestScopes.java (short, self-documenting), ScopedValue.where().run() pattern from TenantFilter, SET search_path in SchemaMultiTenantConnectionProvider, OrgSchemaMapping as registry, request lifecycle sequence diagram. References: multitenancy/ package files. ~1 file, ~350 lines. |
| T7.4 | Create blog post 04: Spring Cloud Gateway as BFF | T7A | `docs/blog/04-spring-cloud-gateway-as-bff.md` -- why BFF (security argument), GatewaySecurityConfig walkthrough, route config YAML, BFF controller endpoints, session management (Spring Session JDBC), CSRF token flow, OAuth2 login sequence diagram. References: gateway/ files. ~1 file, ~300 lines. |
| T7.5 | Create blog post 05: Tenant Registration Pipeline | T7A | `docs/blog/05-tenant-registration-pipeline.md` -- AccessRequest state machine diagram, OTP flow, provisioning pipeline step table (with idempotency column), approval flow sequence diagram, AccessRequestApprovalService walkthrough, error handling and retry. References: accessrequest/ and provisioning/ files. ~1 file, ~350 lines. |
| T7.6 | Create blog post 06: Members, Roles & Profile Sync | T7B | `docs/blog/06-members-roles-and-profile-sync.md` -- MemberSyncService first-login flow, profile sync on subsequent logins, invitation via Keycloak API, owner vs member RBAC table, why roles are in the product DB (not Keycloak). References: member/ package files, ADR-T003. ~1 file, ~250 lines. |
| T7.7 | Create blog post 07: Your First Domain Entity | T7B | `docs/blog/07-your-first-domain-entity.md` -- Project entity code walkthrough (the pattern every future entity follows), repository interface, service layer, controller with DTOs, migration SQL, integration test example. "Copy this pattern for your own entities." References: project/ package files. ~1 file, ~300 lines. |
| T7.8 | Create blog post 08: Security Hardening | T7B | `docs/blog/08-security-hardening.md` -- schema name validation regex, "what happens if you tamper with org_id" explanation, OTP security properties (BCrypt, attempt limiting, time limiting, brute-force math), magic link security properties (entropy, hashing, single-use, rate limit), CSRF protection, JWT validation layers (gateway + backend), session management. Cross-cutting security review. ~1 file, ~350 lines. |
| T7.9 | Create blog post 09: The Magic Link Portal | T7B | `docs/blog/09-the-magic-link-portal.md` -- MagicLinkService token generation, SHA-256 hash-only storage, PortalJwtService (HS256, separate from Keycloak), PortalAuthController flow, PortalAuthFilter, portal endpoints scoped to customer. Full sequence diagram from email click to portal access. References: portal/ package files, ADR-T005. ~1 file, ~300 lines. |
| T7.10 | Create blog post 10: Portal Comments -- Dual-Auth Writes | T7B | `docs/blog/10-portal-comments-dual-auth-writes.md` -- Comment entity with author_type discriminator, side-by-side: member comment creation vs customer comment creation, UI rendering pattern (MEMBER badge vs CUSTOMER badge), why a single table (not two), extensibility for future author types. References: comment/ files, portal/PortalController, ADR-T006. ~1 file, ~250 lines. |

### Key Files

**Slice T7A:**
- `docs/blog/01-architecture-and-why-schema-per-tenant.md`
- `docs/blog/02-one-command-dev-environment.md`
- `docs/blog/03-the-multitenancy-core.md`
- `docs/blog/04-spring-cloud-gateway-as-bff.md`
- `docs/blog/05-tenant-registration-pipeline.md`

**Slice T7B:**
- `docs/blog/06-members-roles-and-profile-sync.md`
- `docs/blog/07-your-first-domain-entity.md`
- `docs/blog/08-security-hardening.md`
- `docs/blog/09-the-magic-link-portal.md`
- `docs/blog/10-portal-comments-dual-auth-writes.md`

### Architecture Decisions

- All 7 ADRs are referenced across the blog series, with each post covering the ADRs relevant to its capability slice.

---

## Summary Statistics

| Metric | Count |
|--------|-------|
| Epics | 7 |
| Slices | 19 |
| Tasks | 72 |
| Backend Java files (new) | ~55 |
| Frontend TypeScript files (new) | ~45 |
| Migration SQL files (new) | 8 |
| Infrastructure files (new) | ~12 |
| Blog posts (new) | 10 |
| Integration test classes | ~12 |
| Estimated total new files | ~135 |

### Reference Files (in DocTeams repo — patterns to adapt, not copy)
- `architecture/template-multitenant-saas-starter.md` — Architecture document
- `adr/ADR-T001` through `ADR-T007` — Architecture Decision Records
- `requirements/claude-code-prompt-template-extraction.md` — Full requirements
- DocTeams `backend/src/main/java/.../multitenancy/` — Reference multitenancy patterns
- DocTeams `backend/src/main/java/.../accessrequest/` — Reference access request flow
- DocTeams `backend/src/main/java/.../portal/` — Reference portal/magic link patterns
- DocTeams `gateway/src/main/java/.../config/` — Reference gateway security config
