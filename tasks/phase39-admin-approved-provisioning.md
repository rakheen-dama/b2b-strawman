# Admin-Approved Tenant Provisioning -- Task Breakdown

Replaces Clerk-driven self-registration with an admin-approved provisioning flow. A visitor submits an access request via a public form (no auth). A product admin reviews and approves/rejects requests from a dashboard. On approval, the backend orchestrates Keycloak org creation, tenant schema provisioning, and owner invitation in a single synchronous sequence. This feature depends on Phase 36 (Keycloak + Gateway BFF Migration) and Phase 20 (auth abstraction layer).

**Architecture doc**: `architecture/admin-approved-provisioning.md`

**ADRs**: [ADR-154](adr/ADR-154-admin-approved-provisioning-flow.md) (admin-approved flow), [ADR-155](adr/ADR-155-access-request-lifecycle-model.md) (access request lifecycle), [ADR-156](adr/ADR-156-invitation-role-gap-mitigation.md) (invitation role gap mitigation)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 295 | Access Request Entity & Public API | Backend | -- | M | 295A, 295B | |
| 296 | Keycloak Admin Client & Provisioning Orchestration | Backend | 295 | L | 296A, 296B | |
| 297 | JWT Refactor & Security Config | Backend | 295 | M | 297A, 297B | |
| 298 | MemberFilter JIT Role Assignment | Backend | 295, 297 | S | 298A | |
| 299 | Frontend -- Public Access Request Form | Frontend | 295 | S | 299A | |
| 300 | Frontend -- Admin Access Request Dashboard | Frontend | 296, 297 | M | 300A, 300B | |
| 301 | Cleanup -- Remove Clerk-Specific Code | Backend + Frontend | 296, 297, 298, 300 | M | 301A, 301B | |

## Dependency Graph

```
[E295A Migration + Entity]──>[E295B Public API + Service]
       (Backend)                    (Backend)
            │                            │
            ├────────────────────────────┤
            │                            │
            ▼                            ▼
[E296A KC Admin Client Config]   [E297A JwtClaimExtractor Refactor]
       (Backend)                       (Backend)
            │                            │
            ▼                            ▼
[E296B Provisioning Service]     [E297B SecurityConfig + PRODUCT_ADMIN]
       (Backend)                       (Backend)
            │                            │
            └──────────┬─────────────────┤
                       │                 │
                       ▼                 │
            [E298A MemberFilter JIT]     │
                 (Backend)               │
                       │                 │
                       │                 ▼
                       │        [E300A Admin Dashboard List]
                       │              (Frontend)
                       │                 │
                       │                 ▼
                       │        [E300B Admin Actions]
                       │              (Frontend)
                       │                 │
                       └────────┬────────┘
                                │
                                ▼
         ┌──────────────────────┴──────────────────────┐
         ▼                                             ▼
[E301A Backend Clerk Cleanup]              [E301B Frontend Clerk Cleanup]
       (Backend)                                  (Frontend)

         PARALLEL: E299A (Public Form) can start after E295B, independent of all others.
         PARALLEL: E296A/296B and E297A/297B can run concurrently (both depend on E295 only).
         PARALLEL: E301A and E301B can run concurrently.
```

**Parallel opportunities**:
- After Epic 295 completes, Epic 296 (KC integration) and Epic 297 (JWT refactor) can run in parallel
- Epic 299 (Public Form) can start as soon as Epic 295B is complete, independent of all other epics
- Epic 301A (backend cleanup) and 301B (frontend cleanup) can run in parallel after their dependencies

## Implementation Order

### Stage 1: Foundation -- Migration & Entity (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 295 | 295A | **V15 global migration** (access_requests table). AccessRequest entity in public schema. AccessRequestStatus enum. AccessRequestRepository with JPQL queries. Foundation for all access request work. |
| 1b | Epic 295 | 295B | AccessRequestService (CRUD + validation + duplicate email check). AccessRequestController (public POST endpoint). Rate limiting (per-email DB check). Integration tests for entity, service, and controller. |

### Stage 2: Keycloak Integration & JWT Refactor (Parallel Tracks)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 296 | 296A | Keycloak admin-client Maven dependency. KeycloakAdminConfig bean. KeycloakOrgService (create org, send invitation). Unit/integration tests with mock KC. |
| 2b | Epic 296 | 296B | AdminProvisioningService (orchestrates KC org + schema + invite). Admin controller endpoints (approve, reject, retry). Integration tests for orchestration flow. Depends on 296A. |
| 2a' | Epic 297 | 297A | Rename ClerkJwtUtils to JwtClaimExtractor. Remove Clerk v2 format detection. Keycloak-only extraction. Update all 4 consumers (TenantFilter, MemberFilter, ClerkJwtAuthenticationConverter, TenantLoggingFilter if applicable). Unit tests. |
| 2b' | Epic 297 | 297B | Add PRODUCT_ADMIN realm role extraction to JWT converter. SecurityConfig: add /api/access-requests permitAll, /admin/** PRODUCT_ADMIN authority. Rename ClerkJwtAuthenticationConverter to KeycloakJwtAuthConverter. Update Roles.java constants. Integration tests for security rules. |

### Stage 3: JIT Role & Public Form (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 298 | 298A | Enhance MemberFilter to check access_requests table for intended role on first login. AccessRequestRepository.findByKeycloakOrgIdAndContactEmail query. Integration tests for JIT owner assignment. |
| 3b | Epic 299 | 299A | Public /request-access page. Server Action calling POST /api/access-requests. Form validation (Zod). Success/error states. Frontend tests. |

### Stage 4: Admin Dashboard (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 300 | 300A | Admin layout at /admin. Access request list page with status filter. API client functions for GET /admin/access-requests. Data table component. Frontend tests. |
| 4b | Epic 300 | 300B | Approve, reject, retry actions. Reject dialog with reason input. Status badges. Loading/error states. Frontend tests. |

### Stage 5: Cleanup (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a | Epic 301 | 301A | Backend: remove ProcessedWebhook entity/repo. Remove webhook-related code from ProvisioningController. Remove Clerk-specific JIT provisioning TODO comments. Update test JWT mocks to Keycloak format. |
| 5b | Epic 301 | 301B | Frontend: remove Clerk webhook route. Remove sign-up page. Remove/redirect create-org page. Remove Clerk auth provider. Update root layout to remove ClerkProvider. |

### Timeline

```
Stage 1:  [295A] ──> [295B]                                   <- foundation (sequential)
Stage 2:  [296A] ──> [296B]  //  [297A] ──> [297B]            <- KC + JWT (parallel tracks)
Stage 3:  [298A]  //  [299A]                                   <- JIT role + form (parallel)
Stage 4:  [300A] ──> [300B]                                    <- admin dashboard (sequential)
Stage 5:  [301A]  //  [301B]                                   <- cleanup (parallel)
```

**Critical path**: 295A -> 295B -> 296A -> 296B -> 300A -> 300B -> 301B
**Parallelizable**: 296/297 run concurrently; 298A/299A run concurrently; 301A/301B run concurrently

---

## Epic 295: Access Request Entity & Public API

**Goal**: Establish the AccessRequest entity in the public schema with its five-state lifecycle (PENDING, PROVISIONING, APPROVED, REJECTED, FAILED). Provide the public submission endpoint that visitors use to request access (no auth required), with per-email rate limiting.

**References**: Architecture doc Sections 2.1-2.5 (entity model, status lifecycle, migration, API design, rate limiting). [ADR-155](adr/ADR-155-access-request-lifecycle-model.md).

**Dependencies**: None (new table in global schema, new package)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **295A** | 295.1-295.5 | V15 global migration (access_requests table, indexes). AccessRequest entity (@Entity, @Table(schema = "public"), 12 fields). AccessRequestStatus enum (5 states). AccessRequestRepository (findById, findByStatus, findByContactEmail). Entity persistence tests (~5 tests). | |
| **295B** | 295.6-295.12 | AccessRequestService (submit, getById, list with status filter, per-email duplicate/rate check). AccessRequestController (public POST /api/access-requests). DTO records (SubmitAccessRequestRequest, AccessRequestResponse). Input validation (@Valid, @NotBlank, @Email). Integration tests for service + controller (~8 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 295.1 | Create V15 global migration file | 295A | | `db/migration/global/V15__create_access_requests.sql`. Create access_requests table (12 columns per Section 2.3: id UUID PK, company_name VARCHAR(200) NOT NULL, contact_name VARCHAR(100) NOT NULL, contact_email VARCHAR(320) NOT NULL, reason VARCHAR(1000), status VARCHAR(20) NOT NULL DEFAULT 'PENDING', rejection_reason VARCHAR(500), keycloak_org_id VARCHAR(100), tenant_schema VARCHAR(50), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), reviewed_at TIMESTAMPTZ, reviewed_by VARCHAR(100)). Indexes: idx_access_requests_status, idx_access_requests_email. Pattern: follow existing global migrations V1-V14 in `db/migration/global/`. |
| 295.2 | Create AccessRequestStatus enum | 295A | | `accessrequest/AccessRequestStatus.java` in new `accessrequest` package. Enum values: PENDING, PROVISIONING, APPROVED, REJECTED, FAILED. No methods needed -- simple enum. Pattern: follow existing enums (e.g., `provisioning/Organization.java` ProvisioningStatus). |
| 295.3 | Create AccessRequest entity | 295A | | `accessrequest/AccessRequest.java`. @Entity, @Table(name = "access_requests", schema = "public"). 12 fields per Section 2.1. Protected no-arg constructor. Public constructor taking (companyName, contactName, contactEmail, reason). Domain transition methods: `markProvisioning()`, `markApproved(String reviewedBy)`, `markRejected(String reviewedBy, String rejectionReason)`, `markFailed()`. @PrePersist sets createdAt. Status transitions enforce valid source states (e.g., markProvisioning only from PENDING or FAILED). Pattern: follow `provisioning/Organization.java` entity. |
| 295.4 | Create AccessRequestRepository | 295A | | `accessrequest/AccessRequestRepository.java`. JpaRepository<AccessRequest, UUID>. Methods: `findByStatus(AccessRequestStatus status, Pageable pageable)` returning Page, `findByContactEmailOrderByCreatedAtDesc(String email)` returning List, `countByContactEmailAndCreatedAtAfter(String email, Instant since)`. Pattern: follow OrganizationRepository. |
| 295.5 | Add AccessRequest entity persistence tests | 295A | | `accessrequest/AccessRequestTest.java` (~5 tests): save and retrieve, status transitions (PENDING->PROVISIONING, PENDING->REJECTED, PROVISIONING->APPROVED, PROVISIONING->FAILED), invalid transition throws IllegalStateException, find by status. Tests run against public schema (no tenant scope needed). Pattern: follow Organization entity tests. Use `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. |
| 295.6 | Create DTO records | 295B | | Nested records in controller or `accessrequest/dto/` sub-package. `SubmitAccessRequestRequest(String companyName, String contactName, String contactEmail, String reason)` with @NotBlank on required fields, @Email on contactEmail, @Size(max=200) etc. `AccessRequestResponse(UUID id, String companyName, String contactName, String contactEmail, String reason, String status, Instant createdAt)`. `AccessRequestDetailResponse` extends with rejectionReason, keycloakOrgId, tenantSchema, reviewedAt, reviewedBy. |
| 295.7 | Create AccessRequestService | 295B | | `accessrequest/AccessRequestService.java`. @Service. Methods: `submit(SubmitAccessRequestRequest)` -- validate per-email rate (max 3 per 24h via countByContactEmailAndCreatedAtAfter), create entity, save, return response. `getById(UUID id)` -- return entity or throw ResourceNotFoundException. `listByStatus(AccessRequestStatus status, Pageable pageable)` -- return Page<AccessRequestResponse>. `listAll(Pageable pageable)` -- return Page. No provisioning logic here -- that belongs in Epic 296. Pattern: follow thin service pattern, throw exceptions from `exception/` package. |
| 295.8 | Create AccessRequestController (public endpoint) | 295B | | `accessrequest/AccessRequestController.java`. @RestController. Single endpoint: `POST /api/access-requests` -- accepts @Valid @RequestBody SubmitAccessRequestRequest, delegates to service.submit(), returns ResponseEntity.status(201).body(response). No auth required (endpoint will be configured as permitAll in Epic 297). For now, add a comment noting SecurityConfig change is in 297B. Pattern: follow controller discipline -- one-liner delegation. |
| 295.9 | Create AccessRequestAdminController (read-only admin endpoints) | 295B | | `accessrequest/AccessRequestAdminController.java`. @RestController, @RequestMapping("/admin/access-requests"). Endpoints: `GET /` -- list all with optional status query param, paginated. `GET /{id}` -- get detail. These are read-only; mutation endpoints (approve/reject/retry) added in Epic 296B. @PreAuthorize("hasAuthority('ROLE_PRODUCT_ADMIN')") on class. Pattern: separate controller for admin namespace. |
| 295.10 | Add AccessRequestService integration tests | 295B | | `accessrequest/AccessRequestServiceTest.java` (~4 tests): submit creates PENDING request, submit rate-limits per email (4th request in 24h rejected with 429-equivalent exception), getById throws on missing, listByStatus filters correctly. Use @SpringBootTest + @Import(TestcontainersConfiguration.class). |
| 295.11 | Add AccessRequestController integration tests | 295B | | `accessrequest/AccessRequestControllerTest.java` (~4 tests): POST creates request and returns 201, POST with invalid email returns 400, POST with missing companyName returns 400, GET admin list returns 200. Use MockMvc. Note: SecurityConfig permitAll for POST not yet configured (test with security disabled or mock). |
| 295.12 | Verify V15 migration runs cleanly | 295B | | Run `./mvnw clean test -q` and verify no migration errors. Confirm access_requests table created in public schema with correct indexes. |

### Key Files

| File | Purpose |
|------|---------|
| `db/migration/global/V15__create_access_requests.sql` | DDL migration for access_requests table |
| `accessrequest/AccessRequest.java` | Entity with status lifecycle methods |
| `accessrequest/AccessRequestStatus.java` | Five-state enum |
| `accessrequest/AccessRequestRepository.java` | JPA repository with status/email queries |
| `accessrequest/AccessRequestService.java` | Submit + list + rate limiting logic |
| `accessrequest/AccessRequestController.java` | Public POST endpoint |
| `accessrequest/AccessRequestAdminController.java` | Admin GET endpoints |

### Architecture Decisions

- Entity lives in **public schema** (not tenant-scoped) because it exists before any tenant does. Consistent with `organizations`, `org_schema_mapping` (ADR-155).
- Five-state lifecycle: PROVISIONING state prevents double-click race; FAILED enables intelligent retry (ADR-155).
- Per-email rate limiting via DB count (max 3 per 24h) rather than external rate limiter -- simple, sufficient for B2B volume.

---

## Epic 296: Keycloak Admin Client & Provisioning Orchestration

**Goal**: Integrate the Keycloak Admin Client to create organizations and send invitations. Build the `AdminProvisioningService` that orchestrates the full approval flow: KC org creation, tenant schema provisioning (via existing `TenantProvisioningService`), and owner invitation. Provide admin endpoints for approve, reject, and retry actions.

**References**: Architecture doc Sections 3.1-3.5 (service architecture, KC admin client, org creation, invitation, failure handling). [ADR-154](adr/ADR-154-admin-approved-provisioning-flow.md), [ADR-156](adr/ADR-156-invitation-role-gap-mitigation.md).

**Dependencies**: Epic 295 (AccessRequest entity and repository)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **296A** | 296.1-296.7 | Maven keycloak-admin-client dependency. KeycloakAdminConfig (@Configuration, admin client bean). KeycloakAdminProperties (@ConfigurationProperties). KeycloakOrgService (createOrg, sendInvitation helper methods). Application.yml KC admin properties. Unit tests with mocked KC client (~6 tests). | |
| **296B** | 296.8-296.14 | AdminProvisioningService (approve, reject, retry orchestration). ProvisioningException extension. AccessRequestAdminController mutation endpoints (POST approve/reject/retry). Slug generator for org names. Integration tests for approve flow, reject, retry, failure scenarios (~8 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 296.1 | Add keycloak-admin-client Maven dependency | 296A | | `backend/pom.xml`. Add `org.keycloak:keycloak-admin-client:26.0.8`. Verify no dependency conflicts with existing Spring Boot 4 dependencies. Pattern: check existing `pom.xml` for dependency management conventions. |
| 296.2 | Create KeycloakAdminProperties | 296A | | `config/KeycloakAdminProperties.java`. @ConfigurationProperties(prefix = "keycloak.admin"). Fields: serverUrl, realm, clientId, clientSecret. Pattern: follow existing @ConfigurationProperties in config/ package (if any), or use @Value injection via KeycloakAdminConfig. |
| 296.3 | Create KeycloakAdminConfig | 296A | | `config/KeycloakAdminConfig.java`. @Configuration. @Bean Keycloak keycloakAdminClient() -- builds Keycloak instance using KeycloakBuilder with CLIENT_CREDENTIALS grant type. Per Section 3.2. Pattern: follow existing config beans in `config/` package. |
| 296.4 | Add KC admin properties to application.yml | 296A | | `application.yml` and `application-local.yml`: add keycloak.admin.server-url, realm, client-id, client-secret with defaults for local dev. Also add `application-test.yml` with test values. |
| 296.5 | Create KeycloakOrgService | 296A | | `provisioning/KeycloakOrgService.java`. @Service. Methods: `createOrganization(String companyName)` returns String (KC org ID) -- creates OrganizationRepresentation, sets name=toSlug(companyName), enabled=true, attributes (displayName, tier). Parses Location header for org ID. `sendInvitation(String kcOrgId, String email, String firstName, String lastName)` -- calls OrganizationMembersResource.inviteUser(). Private `toSlug(String name)` helper. Pattern: follow existing service conventions. |
| 296.6 | Add KeycloakOrgService unit tests | 296A | | `provisioning/KeycloakOrgServiceTest.java` (~4 tests): createOrg builds correct representation, createOrg parses Location header, sendInvitation calls correct KC API, toSlug normalizes names ("Acme Corp" -> "acme-corp"). Mock the Keycloak admin client. Pattern: Mockito unit test. |
| 296.7 | Add KC connectivity integration test | 296A | | `provisioning/KeycloakAdminConfigTest.java` (~2 tests): bean loads correctly with test properties, keycloak client can be created. Use @SpringBootTest with test profile. Keycloak not actually running -- just verify config wiring. |
| 296.8 | Create AdminProvisioningService | 296B | | `provisioning/AdminProvisioningService.java`. @Service. Method: `approve(UUID accessRequestId)` -- implements the 6-step orchestration from Section 3.1 (mark PROVISIONING, create KC org, provision schema via TenantProvisioningService, send invitation, mark APPROVED). Method: `reject(UUID accessRequestId, String reason)` -- validate PENDING status, mark REJECTED. Method: `retry(UUID accessRequestId)` -- resume from failed step per Section 3.5 (check keycloakOrgId/tenantSchema to determine where it left off). Uses `@Transactional` (but note KC calls are non-transactional). Pattern: follow TenantProvisioningService for orchestration and error handling. |
| 296.9 | Create org name slug generator | 296B | | Add `toSlug(String companyName)` as a static utility in KeycloakOrgService or a separate `SlugUtils.java`. Lowercase, replace whitespace with hyphens, strip non-alphanumeric except hyphens, collapse consecutive hyphens, trim leading/trailing hyphens. Pattern: follow DocumentTemplate slug generation (Epic 93). |
| 296.10 | Add mutation endpoints to AccessRequestAdminController | 296B | | Extend `accessrequest/AccessRequestAdminController.java` (created in 295B). Add endpoints: `POST /{id}/approve` -- delegates to adminProvisioningService.approve(id), returns 200 with AccessRequestDetailResponse. `POST /{id}/reject` -- accepts optional RejectRequest(String reason), delegates to service. `POST /{id}/retry` -- delegates to service.retry(). All @PreAuthorize("hasAuthority('ROLE_PRODUCT_ADMIN')"). Pattern: controller discipline -- one-liner delegation. |
| 296.11 | Create RejectRequest DTO | 296B | | `accessrequest/dto/RejectRequest.java` record. Single field: `String reason` (optional, @Size(max=500)). |
| 296.12 | Add AdminProvisioningService unit tests | 296B | | `provisioning/AdminProvisioningServiceTest.java` (~5 tests): approve happy path (all 6 steps), approve from FAILED retries correctly, reject sets reason and reviewedBy, retry resumes from keycloakOrgId set but tenantSchema null, approve on APPROVED throws IllegalStateException. Mock KeycloakOrgService and TenantProvisioningService. |
| 296.13 | Add admin controller integration tests | 296B | | `accessrequest/AccessRequestAdminControllerTest.java` (~3 tests): POST approve returns 200, POST reject with reason returns 200, POST retry on FAILED returns 200. Mock Keycloak client (no real KC). Use MockMvc with PRODUCT_ADMIN JWT mock. |
| 296.14 | Verify approve flow end-to-end in test | 296B | | Integration test that exercises: submit access request, approve it, verify status transitions (PENDING -> PROVISIONING -> APPROVED), verify keycloakOrgId and tenantSchema set on entity. KC calls mocked. TenantProvisioningService mocked or uses Testcontainers Postgres for real schema creation. |

### Key Files

| File | Purpose |
|------|---------|
| `config/KeycloakAdminConfig.java` | KC admin client bean configuration |
| `provisioning/KeycloakOrgService.java` | KC org creation and invitation |
| `provisioning/AdminProvisioningService.java` | Orchestrates approve/reject/retry |
| `accessrequest/AccessRequestAdminController.java` | Admin mutation endpoints |
| `backend/pom.xml` | keycloak-admin-client dependency |

### Architecture Decisions

- Synchronous orchestration (not async/event-driven) for the approval flow -- admin gets immediate feedback on success/failure (ADR-154).
- PROVISIONING status set atomically before external calls begin -- prevents double-click race condition (ADR-155).
- Retry resumes from last successful step by inspecting `keycloakOrgId` and `tenantSchema` fields (Section 3.5).
- KC admin client uses client_credentials grant (service account), not username/password.

---

## Epic 297: JWT Refactor & Security Config

**Goal**: Rename `ClerkJwtUtils` to `JwtClaimExtractor` with Keycloak-only extraction logic. Update `ClerkJwtAuthenticationConverter` to `KeycloakJwtAuthConverter` with PRODUCT_ADMIN realm role extraction. Update `SecurityConfig` to permit the public access request endpoint and protect `/admin/**` with PRODUCT_ADMIN authority.

**References**: Architecture doc Sections 4.1-4.5 (JWT structure, claim extractor, role resolution, TenantFilter, SecurityConfig).

**Dependencies**: Epic 295 (needs the `/api/access-requests` endpoint to exist for permitAll config)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **297A** | 297.1-297.7 | Rename ClerkJwtUtils -> JwtClaimExtractor. Remove Clerk v2 format detection. Keycloak-only extractOrgId/extractOrgSlug/extractOrgRole. Add extractRealmRoles and isProductAdmin methods. Update all consumers (TenantFilter, MemberFilter, ClerkJwtAuthenticationConverter). Unit tests (~8 tests). | |
| **297B** | 297.8-297.13 | Rename ClerkJwtAuthenticationConverter -> KeycloakJwtAuthConverter. Add PRODUCT_ADMIN realm role to granted authorities. Update Roles.java with AUTHORITY_PRODUCT_ADMIN. SecurityConfig: add /api/access-requests permitAll, /admin/** hasAuthority PRODUCT_ADMIN. Add shouldNotFilter paths for admin endpoints in TenantFilter/MemberFilter. Integration tests for security rules (~6 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 297.1 | Rename ClerkJwtUtils to JwtClaimExtractor | 297A | | Rename `security/ClerkJwtUtils.java` -> `security/JwtClaimExtractor.java`. Remove all Clerk v2 extraction methods (extractClerkClaim, CLERK_ORG_CLAIM constant, isClerkJwt). Keep only Keycloak extraction. Per Section 4.2. |
| 297.2 | Implement Keycloak-only extractOrgId | 297A | | `JwtClaimExtractor.extractOrgId(Jwt)` -- extracts from `organization` claim map. Handles both map-of-maps format `{"org-name": {"id": "uuid"}}` and list format `["org-name"]`. Single-org constraint: takes first entry. Per Section 4.2. |
| 297.3 | Implement extractOrgSlug (Keycloak) | 297A | | `JwtClaimExtractor.extractOrgSlug(Jwt)` -- returns the map key from `organization` claim (the org alias IS the slug). |
| 297.4 | Implement extractOrgRole (Keycloak) | 297A | | `JwtClaimExtractor.extractOrgRole(Jwt)` -- checks rich format for roles array within org map entry. Returns null if no roles in JWT (expected path per Section 4.3 -- role resolved from DB by MemberFilter). Normalizes "org:owner" -> "owner". |
| 297.5 | Add isProductAdmin method | 297A | | `JwtClaimExtractor.isProductAdmin(Jwt)` -- checks `realm_access.roles` for "PRODUCT_ADMIN" string. Per Section 4.2. |
| 297.6 | Update all consumers of ClerkJwtUtils | 297A | | Update imports in: `multitenancy/TenantFilter.java` (ClerkJwtUtils -> JwtClaimExtractor, 3 call sites), `member/MemberFilter.java` (1 call site), `security/ClerkJwtAuthenticationConverter.java` (1 call site). Search codebase for any other references. |
| 297.7 | Add JwtClaimExtractor unit tests | 297A | | `security/JwtClaimExtractorTest.java` (~8 tests): extractOrgId from map-of-maps, extractOrgId from list format, extractOrgSlug, extractOrgRole from rich format, extractOrgRole returns null when no roles, isProductAdmin true when PRODUCT_ADMIN in realm_access.roles, isProductAdmin false when absent, null org claim returns null. Build mock Jwt objects with claims. Pattern: follow existing ClerkJwtUtils tests if any, or plain JUnit 5. |
| 297.8 | Rename ClerkJwtAuthenticationConverter | 297B | | Rename `security/ClerkJwtAuthenticationConverter.java` -> `security/KeycloakJwtAuthConverter.java`. Update @Component name. Update SecurityConfig import. |
| 297.9 | Add realm role extraction to JWT converter | 297B | | In `KeycloakJwtAuthConverter.extractAuthorities()`: after org role extraction, also extract `realm_access.roles` and map "PRODUCT_ADMIN" to `new SimpleGrantedAuthority("ROLE_PRODUCT_ADMIN")`. Per Section 4.5 keycloakJwtAuthConverter pattern. |
| 297.10 | Update Roles.java | 297B | | Add `AUTHORITY_PRODUCT_ADMIN = "ROLE_PRODUCT_ADMIN"` constant. Add `PRODUCT_ADMIN = "PRODUCT_ADMIN"` realm role constant. |
| 297.11 | Update SecurityConfig for new endpoints | 297B | | In `securityFilterChain()`: add `.requestMatchers(HttpMethod.POST, "/api/access-requests").permitAll()` before the `.requestMatchers("/api/**").authenticated()` rule. Add `.requestMatchers("/admin/**").hasAuthority(Roles.AUTHORITY_PRODUCT_ADMIN)` before `.anyRequest()`. Per Section 4.5. Also update the bean name reference from ClerkJwtAuthenticationConverter to KeycloakJwtAuthConverter. |
| 297.12 | Update TenantFilter/MemberFilter shouldNotFilter | 297B | | Add `path.startsWith("/admin/")` to `shouldNotFilter()` in both `TenantFilter.java` and `MemberFilter.java`. Admin endpoints are not org-scoped -- they operate on the public schema. Also add `path.equals("/api/access-requests")` for the public POST endpoint (no tenant context needed). |
| 297.13 | Add SecurityConfig integration tests | 297B | | `security/SecurityConfigAccessRequestTest.java` (~6 tests): POST /api/access-requests without auth returns 201 (after creating valid body), GET /admin/access-requests without auth returns 401, GET /admin/access-requests with regular user JWT returns 403, GET /admin/access-requests with PRODUCT_ADMIN JWT returns 200, POST /admin/access-requests/{id}/approve without PRODUCT_ADMIN returns 403, verify /api/** still requires auth. Use MockMvc with jwt() post-processor. |

### Key Files

| File | Purpose |
|------|---------|
| `security/JwtClaimExtractor.java` | Keycloak-only JWT claim extraction (renamed from ClerkJwtUtils) |
| `security/KeycloakJwtAuthConverter.java` | JWT -> Spring authorities with realm roles (renamed from ClerkJwtAuthenticationConverter) |
| `security/Roles.java` | Add PRODUCT_ADMIN authority constant |
| `security/SecurityConfig.java` | Add permitAll and PRODUCT_ADMIN authorization rules |
| `multitenancy/TenantFilter.java` | Update import + shouldNotFilter for /admin/ |
| `member/MemberFilter.java` | Update import + shouldNotFilter for /admin/ |

### Architecture Decisions

- Org role is NOT extracted from JWT -- MemberFilter resolves it from `Member.orgRole` in the database (Section 4.3, Option B). This is consistent with the existing architecture.
- PRODUCT_ADMIN is a Keycloak realm role (platform-level), distinct from org-level roles (owner/admin/member).
- Public POST endpoint for access requests must bypass both auth AND tenant/member filters.

---

## Epic 298: MemberFilter JIT Role Assignment

**Goal**: Enhance `MemberFilter` to check the `access_requests` table when creating a new member on first login, assigning the correct role (Owner for the original access requester). This closes the Keycloak invitation role gap (ADR-156).

**References**: Architecture doc Section 5 (Member JIT sync enhancement), [ADR-156](adr/ADR-156-invitation-role-gap-mitigation.md).

**Dependencies**: Epic 295 (AccessRequestRepository), Epic 297 (JwtClaimExtractor)

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **298A** | 298.1-298.5 | Add AccessRequestRepository.findApprovedByKeycloakOrgIdAndContactEmail query. Modify MemberFilter.lazyCreateMember to check access_requests for intended role. Use RequestScopes.ORG_ID to get Keycloak org ID. Integration tests (~4 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 298.1 | Add access request lookup query | 298A | | `AccessRequestRepository.java`: add `Optional<AccessRequest> findFirstByKeycloakOrgIdAndContactEmailAndStatus(String keycloakOrgId, String contactEmail, AccessRequestStatus status)`. This query finds the APPROVED access request matching the org and email, used to determine if the logging-in user is the original requester. |
| 298.2 | Inject AccessRequestRepository into MemberFilter | 298A | | Add `AccessRequestRepository` as a constructor parameter in `MemberFilter.java`. Update constructor injection. |
| 298.3 | Modify lazyCreateMember for role lookup | 298A | | In `MemberFilter.lazyCreateMember()`: before defaulting to `Roles.ORG_MEMBER`, check if `RequestScopes.ORG_ID` is bound, then query `accessRequestRepository.findFirstByKeycloakOrgIdAndContactEmailAndStatus(orgId, jwt.getClaimAsString("email"), AccessRequestStatus.APPROVED)`. If found, use `Roles.ORG_OWNER` instead of the JWT role or default. Per Section 5.1-5.2. |
| 298.4 | Add MemberFilter JIT role integration tests | 298A | | `member/MemberFilterJitRoleTest.java` (~4 tests): first login with matching access request gets Owner role, first login without access request gets Member role, first login with PENDING (not APPROVED) access request gets Member role (not Owner), second login uses cached member (no re-lookup). Use @SpringBootTest + MockMvc. Create access request records in test setup. |
| 298.5 | Add unit test for role determination logic | 298A | | `member/MemberFilterRoleDeterminationTest.java` (~2 tests): verify role precedence (access request match -> owner, no match -> member). Extract role determination to a private method for testability, or test via the integration tests only. |

### Key Files

| File | Purpose |
|------|---------|
| `member/MemberFilter.java` | Enhanced with access request role lookup |
| `accessrequest/AccessRequestRepository.java` | New query for matching approved requests |

### Architecture Decisions

- Role source of truth remains `Member.orgRole` in the tenant DB -- the access request lookup only happens at member creation time (first login). After that, the member record is used. (ADR-156, Option 3)
- Only APPROVED access requests are checked -- PENDING/REJECTED/FAILED are ignored for role assignment.

---

## Epic 299: Frontend -- Public Access Request Form

**Goal**: Build the public-facing access request form at `/request-access`. Visitors can submit their company name, contact details, and optional reason. The form calls `POST /api/access-requests` and shows a success confirmation.

**References**: Architecture doc Section 6.1 (public form wireframe).

**Dependencies**: Epic 295 (public POST endpoint exists)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **299A** | 299.1-299.7 | Public route /request-access (outside auth route groups). Form component with Zod validation. Server Action calling backend POST. Success/error states. Responsive layout matching design system. Frontend tests (~5 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 299.1 | Create request-access route | 299A | | `frontend/app/request-access/page.tsx`. Server component that renders the form. Public route (no auth wrapper). Route is outside `(app)/` and `(auth)/` route groups. Title: "Request Access to DocTeams". |
| 299.2 | Create AccessRequestForm client component | 299A | | `frontend/components/access-request/access-request-form.tsx`. "use client". Form fields: Company Name (Input, required), Your Name (Input, required), Email Address (Input, required, email validation), Reason (Textarea, optional, max 1000 chars). Submit button. Uses Shadcn UI components (Input, Textarea, Button, Label). Zod schema for client-side validation. Pattern: follow existing form patterns in the codebase (e.g., create-org-form.tsx). |
| 299.3 | Create Server Action for form submission | 299A | | `frontend/app/request-access/actions.ts`. Server Action `submitAccessRequest(formData)`. Calls backend `POST /api/access-requests` via fetch (no auth token needed -- public endpoint). Returns `{ success: boolean, error?: string }`. Note: does NOT use `lib/api.ts` (which attaches Bearer JWT) -- this is an unauthenticated call. Use `BACKEND_URL` env var directly. |
| 299.4 | Add success confirmation state | 299A | | After successful submission, the form is replaced with a success message: "Your request has been submitted. We will review it and send you an invitation if approved." Include a "Submit another" link to reset the form. Use Shadcn Alert or Card component for the confirmation. |
| 299.5 | Add error handling | 299A | | Handle 429 (rate limit) with message "Too many requests. Please try again later." Handle 400 (validation) with field-specific errors. Handle network errors with generic error alert. Pattern: use Shadcn Alert component with destructive variant. |
| 299.6 | Style the page layout | 299A | | Centered card layout, similar to sign-in page styling. Use the slate/teal design system. Max width container. Responsive for mobile. Pattern: follow `(auth)/layout.tsx` split-screen or centered card pattern. |
| 299.7 | Add frontend tests | 299A | | `frontend/__tests__/request-access.test.tsx` (~5 tests): form renders all fields, submit with valid data calls action, submit with empty required fields shows validation errors, success state renders confirmation message, error state renders error alert. Use @testing-library/react. Mock the server action. |

### Key Files

| File | Purpose |
|------|---------|
| `frontend/app/request-access/page.tsx` | Public route page |
| `frontend/components/access-request/access-request-form.tsx` | Form component |
| `frontend/app/request-access/actions.ts` | Server Action for submission |

### Architecture Decisions

- No auth needed -- the form is completely public. Does NOT use `lib/api.ts` (which attaches JWT).
- Route lives at top level `/request-access`, not under `(app)/` or `(auth)/` route groups.
- Zod validation on client side mirrors backend @Valid constraints.

---

## Epic 300: Frontend -- Admin Access Request Dashboard

**Goal**: Build the admin dashboard at `/admin/access-requests` for product admins to view, approve, reject, and retry access requests. This is a platform-level admin page (not org-scoped) that requires the PRODUCT_ADMIN role.

**References**: Architecture doc Section 6.2 (admin dashboard wireframe).

**Dependencies**: Epic 296 (admin API endpoints), Epic 297 (PRODUCT_ADMIN role in JWT)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **300A** | 300.1-300.7 | Admin layout at /admin. Access request list page with DataTable. Status filter (PENDING/APPROVED/REJECTED/FAILED/All). Pagination. API client functions for admin endpoints. Frontend tests (~5 tests). | |
| **300B** | 300.8-300.13 | Approve action (confirmation dialog). Reject action (dialog with reason textarea). Retry action for FAILED requests. Status badge component. Loading/optimistic update states. Frontend tests (~5 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 300.1 | Create admin layout | 300A | | `frontend/app/admin/layout.tsx`. Server component. Check auth context for PRODUCT_ADMIN role -- redirect to / if not admin. Minimal layout: header with "DocTeams Admin" title, navigation back to main app. No sidebar (simple admin panel). Pattern: follow existing layout patterns but simpler (no org scope). |
| 300.2 | Create admin API client functions | 300A | | `frontend/lib/admin-api.ts`. Functions: `getAccessRequests(status?, page?, size?)` -- GET /admin/access-requests with query params. `getAccessRequest(id)` -- GET /admin/access-requests/{id}. `approveAccessRequest(id)` -- POST .../approve. `rejectAccessRequest(id, reason?)` -- POST .../reject. `retryAccessRequest(id)` -- POST .../retry. Uses `lib/api.ts` fetch wrapper (needs JWT with PRODUCT_ADMIN role). TypeScript interfaces: AccessRequest, PaginatedAccessRequests. |
| 300.3 | Create access request list page | 300A | | `frontend/app/admin/access-requests/page.tsx`. Server component. Fetches access requests from API (default: all statuses, page 0). Renders AccessRequestTable component. Status filter dropdown (Shadcn Select). Pagination controls. |
| 300.4 | Create AccessRequestTable component | 300A | | `frontend/components/admin/access-request-table.tsx`. "use client". Columns: Company, Contact, Email, Status, Submitted, Actions. Uses Shadcn Table component. Receives data as props (serializable). Action column renders approve/reject/retry buttons based on status. Pattern: follow existing DataTable patterns in the codebase. |
| 300.5 | Create StatusFilter component | 300A | | `frontend/components/admin/status-filter.tsx`. "use client". Shadcn Select with options: All, Pending, Approved, Rejected, Failed. On change, updates URL search params to trigger server re-fetch. Pattern: follow existing filter patterns. |
| 300.6 | Create pagination component for admin | 300A | | Reuse existing pagination components if available, or create a simple `AdminPagination` component. Previous/Next buttons + page indicator. Updates URL search params. |
| 300.7 | Add list page frontend tests | 300A | | `frontend/__tests__/admin/access-request-list.test.tsx` (~5 tests): table renders access requests, status filter changes URL params, pagination renders correctly, empty state shows message, loading state. Mock API responses. |
| 300.8 | Create ApproveDialog component | 300B | | `frontend/components/admin/approve-dialog.tsx`. "use client". Shadcn AlertDialog. Confirmation: "Approve request from {companyName}? This will create a Keycloak org, provision a tenant schema, and send an invitation to {email}." Approve button triggers server action. Shows loading state during provisioning. Pattern: follow existing AlertDialog patterns. |
| 300.9 | Create RejectDialog component | 300B | | `frontend/components/admin/reject-dialog.tsx`. "use client". Shadcn Dialog with form. Textarea for optional rejection reason (@Size(max=500)). Cancel and Reject buttons. Reject triggers server action. Pattern: follow existing dialog-with-form patterns. |
| 300.10 | Create admin server actions | 300B | | `frontend/app/admin/access-requests/actions.ts`. Server Actions: `approveRequest(id)`, `rejectRequest(id, reason?)`, `retryRequest(id)`. Call admin API client functions. Revalidate the list page path after mutation. Return `{ success, error? }`. |
| 300.11 | Create StatusBadge component | 300B | | `frontend/components/admin/status-badge.tsx`. Maps status to Shadcn Badge variant: PENDING -> neutral, PROVISIONING -> warning, APPROVED -> success, REJECTED -> destructive, FAILED -> destructive. Pattern: follow existing Badge variant patterns. |
| 300.12 | Wire action buttons in AccessRequestTable | 300B | | Update AccessRequestTable: PENDING rows get Approve + Reject buttons. FAILED rows get Retry button. APPROVED/REJECTED rows get no action buttons (dash). Buttons trigger dialogs from 300.8/300.9. |
| 300.13 | Add action frontend tests | 300B | | `frontend/__tests__/admin/access-request-actions.test.tsx` (~5 tests): approve dialog renders on button click, approve calls server action, reject dialog renders textarea, reject with reason calls action, retry button calls action for FAILED status. Mock server actions. afterEach cleanup for Dialog components (Radix leak). |

### Key Files

| File | Purpose |
|------|---------|
| `frontend/app/admin/layout.tsx` | Admin layout with role check |
| `frontend/app/admin/access-requests/page.tsx` | List page |
| `frontend/app/admin/access-requests/actions.ts` | Server Actions for mutations |
| `frontend/lib/admin-api.ts` | API client for admin endpoints |
| `frontend/components/admin/access-request-table.tsx` | Data table component |
| `frontend/components/admin/approve-dialog.tsx` | Approve confirmation |
| `frontend/components/admin/reject-dialog.tsx` | Reject with reason |
| `frontend/components/admin/status-badge.tsx` | Status badge |

### Architecture Decisions

- Admin pages live at `/admin/` (top-level), not under `(app)/org/[slug]/` -- they are platform-scoped, not org-scoped.
- Auth check for PRODUCT_ADMIN happens in the layout (server component) -- redirects non-admins.
- Server Actions wrap API calls and revalidate the list page path for fresh data.

---

## Epic 301: Cleanup -- Remove Clerk-Specific Code

**Goal**: Remove Clerk-specific code paths that are no longer needed after the Keycloak migration and admin-approved provisioning. This includes the Clerk webhook route, sign-up page, create-org page, Clerk auth provider, ProcessedWebhook entity, and Clerk-specific test helpers.

**References**: Architecture doc Section 7 (impact on existing system -- files to remove, modify).

**Dependencies**: Epics 296, 297, 298, 300 (all new code in place before removing old code)

**Scope**: Backend + Frontend (separate slices)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **301A** | 301.1-301.6 | Backend: remove ProcessedWebhook entity + repo. Remove/simplify ProvisioningController webhook-triggered endpoint. Remove `isClerkJwt` references. Update test JWT mocks from Clerk v2 to Keycloak format. Remove Clerk-related comments/TODOs. Verify all tests pass (~0 new tests, update ~10-15 existing tests). | |
| **301B** | 301.7-301.12 | Frontend: remove Clerk webhook route (app/api/webhooks/clerk/). Remove sign-up page. Remove/redirect create-org page to /request-access. Remove Clerk auth provider (lib/auth/providers/clerk.ts). Update lib/auth/providers/index.ts. Remove ClerkProvider from root layout. Update proxy.ts. Frontend tests pass. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 301.1 | Remove ProcessedWebhook entity and repository | 301A | | Delete `webhook/ProcessedWebhook.java` and `webhook/ProcessedWebhookRepository.java` (if they exist -- check for `processed_webhooks` table references). Search for all usages before deleting. If the `processed_webhooks` table is referenced in global migrations, keep the migration file but note the table is no longer used. |
| 301.2 | Simplify ProvisioningController | 301A | | `provisioning/ProvisioningController.java` -- review the `/internal/orgs/provision` endpoint. If it was only used by the Clerk webhook route in Next.js, consider: (a) keeping it for potential future internal use, or (b) marking it as deprecated. Do NOT delete if other internal callers exist. Check all references. |
| 301.3 | Remove Clerk-specific detection code | 301A | | In `JwtClaimExtractor.java` (formerly ClerkJwtUtils): remove `isClerkJwt()` method if still present after Epic 297. Remove any remaining `CLERK_ORG_CLAIM` constant. Search codebase for "clerk" references (case-insensitive) and update or remove. |
| 301.4 | Update test JWT mocks to Keycloak format | 301A | | Search all test files for `jwt.claim("o", Map.of(` (Clerk v2 format) and update to Keycloak format: `.claim("organization", Map.of("org-slug", Map.of("id", ORG_ID)))`. Update role extraction in test helpers. This is a mechanical refactor across ~10-15 test files. Verify all tests pass after update. |
| 301.5 | Remove Clerk-related comments and TODOs | 301A | | Search for "Clerk", "clerk", "Svix" comments/TODOs and remove or update. Update Javadoc on `MemberFilter`, `TenantFilter`, `SecurityConfig` to reference Keycloak instead of Clerk. |
| 301.6 | Verify all backend tests pass | 301A | | Run `./mvnw clean test -q`. Fix any test failures caused by the cleanup. This is a verification step, not a code creation step. |
| 301.7 | Remove Clerk webhook route | 301B | | Delete `frontend/app/api/webhooks/clerk/route.ts` and `frontend/app/api/webhooks/clerk/route.test.ts`. Check if `lib/webhook-handlers.ts` exists and delete if so. Remove any webhook-related exports from lib/. |
| 301.8 | Remove sign-up page | 301B | | Delete `frontend/app/(auth)/sign-up/` directory (contains `[[...sign-up]]/page.tsx`). Any links to "/sign-up" in the codebase should be redirected to "/request-access". Search for "sign-up" references. |
| 301.9 | Remove/redirect create-org page | 301B | | Either delete `frontend/app/(app)/create-org/` or replace `page.tsx` content with a redirect to `/request-access`. Remove `create-org-form.tsx` and `actions.ts` from the directory. Update any navigation links that point to "/create-org". |
| 301.10 | Remove Clerk auth provider | 301B | | Delete `frontend/lib/auth/providers/clerk.ts`. Update `frontend/lib/auth/providers/index.ts` to remove Clerk import/export. Verify keycloak-bff provider is the only remaining provider. Update `frontend/lib/auth/server.ts` to remove Clerk dispatch logic (should only call keycloak-bff provider). |
| 301.11 | Update root layout | 301B | | `frontend/app/layout.tsx` -- remove `ClerkProvider` wrapper if it still exists. Remove `@clerk/nextjs` imports. If the layout was already updated in Phase 36/Phase 20, verify no Clerk references remain. |
| 301.12 | Verify frontend builds and tests pass | 301B | | Run `pnpm run build` and `pnpm test`. Fix any import errors or broken references caused by the cleanup. This is a verification step. |

### Key Files

| File | Purpose |
|------|---------|
| `frontend/app/api/webhooks/clerk/route.ts` | To be deleted |
| `frontend/app/(auth)/sign-up/` | To be deleted |
| `frontend/app/(app)/create-org/` | To be deleted or redirected |
| `frontend/lib/auth/providers/clerk.ts` | To be deleted |
| `frontend/lib/auth/providers/index.ts` | Remove Clerk export |
| `frontend/lib/auth/server.ts` | Remove Clerk dispatch |
| `backend/.../security/JwtClaimExtractor.java` | Remove any residual Clerk code |
| All test files with `claim("o", ...)` | Update JWT mocks to Keycloak format |

### Architecture Decisions

- Cleanup is intentionally the LAST epic -- ensures all new code works before removing old code paths.
- ProvisioningController `/internal/orgs/provision` endpoint is kept (not deleted) as it may be useful for internal tooling or migration scripts. It is marked as deprecated.
- Test JWT format migration is mechanical but touches many files -- grouped into a single slice to maintain consistency.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation | Epic |
|------|-----------|--------|------------|------|
| KC org creation succeeds but schema fails -- orphaned KC org | Low | Medium | Retry endpoint resumes from failed step; admin sees FAILED status | 296 |
| Keycloak admin-client version incompatible with Spring Boot 4 | Medium | High | Test dependency resolution early in 296A; fallback to HTTP client if needed | 296 |
| V15 global migration number conflicts with concurrent development | Medium | Medium | Check latest migration number before implementation; coordinate with team | 295 |
| PRODUCT_ADMIN role not present in test JWT mocks | Low | Medium | Create test helper for PRODUCT_ADMIN JWT mock in 297B | 297 |
| Public POST endpoint vulnerable to spam without CAPTCHA | Medium | Low | Per-email DB rate limiting (3/24h) sufficient for launch; CAPTCHA can be added later | 295 |
| Clerk cleanup breaks existing functionality | Medium | High | Cleanup is last epic (301); full test suite must pass before merge | 301 |
| MemberFilter access request lookup adds latency to all requests | Low | Low | Lookup only happens on first login (member not found path); subsequent requests use cached member | 298 |

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/admin-approved-provisioning.md` - Complete architecture specification with entity model, API design, service design, and JWT mapping
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` - Must be modified for permitAll and PRODUCT_ADMIN rules (Epics 297, 301)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` - Must be enhanced for JIT role assignment from access requests (Epic 298)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` - Called by AdminProvisioningService for schema creation (Epic 296)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` - Renamed to JwtClaimExtractor, Clerk logic removed (Epic 297)