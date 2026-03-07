# Phase 39 — Admin-Approved Org Provisioning

Phase 39 replaces self-registration with an **admin-approved access request pipeline**. Prospective customers submit a public form (with company email OTP verification), platform admins review and approve/reject requests via a dedicated panel, and approved requests trigger Keycloak org creation, tenant schema provisioning, and owner invitation -- all reusing existing infrastructure from Phase 36 and Phase 13.

**Architecture doc**: `architecture/phase39-admin-approved-provisioning.md`

**Dependencies on prior phases**:
- Phase 36 (Keycloak + Gateway BFF): `KeycloakAdminClient`, Gateway BFF session, auth abstraction
- Phase 13 (Dedicated Schema): `TenantProvisioningService`
- Phase 6.5 (Notifications): Email sending infrastructure

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 295 | Access Request Entity Foundation & Migration | Backend | -- | M | 295A, 295B | **Done** (PR #582) |
| 296 | OTP Verification & Public Access Request API | Backend | 295 | M | 296A, 296B | **Done** (PR #583, #584) |
| 297 | Platform Admin Identity & Security Infrastructure | Backend | 295 | M | 297A, 297B | |
| 298 | Approval Pipeline & Platform Admin API | Backend | 296, 297 | M | 298A, 298B | |
| 299 | Keycloak Configuration & Gateway Routing | Infra | 297 | S | 299A | |
| 300 | Public Access Request Form (Frontend) | Frontend | 296 | M | 300A, 300B | |
| 301 | Platform Admin Panel (Frontend) | Frontend | 298, 300 | M | 301A, 301B | |
| 302 | Self-Service Org Creation Gate & JIT Provisioning Toggle | Backend + Frontend | 298 | S | 302A | |

---

## Dependency Graph

```
BACKEND TRACK (sequential core, parallel branches)
──────────────────────────────────────────────────

[E295A Access Request entity,
 enum, DTO records, repo
 + global migration V15]
        |
[E295B Access Request config
 properties, email domain
 validator, PasswordEncoder
 bean + unit tests]
        |
        +──────────────────────────────────+
        |                                  |
[E296A OTP generation, email              [E297A RequestScopes.GROUPS,
 sending, submit endpoint                  ClerkJwtUtils.extractGroups,
 + public controller                       PlatformSecurityService,
 + SecurityConfig permitAll                PlatformAdminFilter
 + integration tests]                      + unit tests]
        |                                  |
[E296B OTP verification endpoint,         [E297B SecurityConfig platform-
 attempt tracking, expiry,                 admin filter chain ordering,
 status promotion                          @PreAuthorize integration
 + integration tests]                      + integration tests]
        |                                  |
        +─────────────┬───────────────────+
                      |
[E298A Approval service:
 KC org creation, tenant
 provisioning, KC invitation,
 idempotency + integration tests]
        |
[E298B Platform admin controller:
 list/approve/reject endpoints,
 @PreAuthorize guards
 + integration tests]
        |
        +──────────────────────────────────+
        |                                  |
[E302A Disable self-service               |
 org creation, JIT provisioning           |
 toggle, feature flag                     |
 + test updates]                          |
                                          |
INFRA TRACK (parallel with 296/297)       |
────────────────────────────────────      |
[E299A Keycloak seed:                     |
 platform-admins group,                   |
 Group Membership Mapper,                 |
 realm-export.json update,                |
 gateway route config]                    |
                                          |
FRONTEND TRACK (after backend APIs)       |
────────────────────────────────────      |
[E300A /request-access page:              |
 Step 1 form (email, name,               |
 org, country, industry),                 |
 blocked domain check,                    |
 server action, submit flow]              |
        |                                 |
[E300B OTP verification step:             |
 Step 2 (OTP input), Step 3              |
 (success), error handling,              |
 + tests]                                |
        |                                 |
        +─────────────────────────────────+
        |
[E301A AuthContext groups field,
 BFF /bff/me groups extraction,
 (platform-admin) layout +
 route guard + nav link]
        |
[E301B AccessRequestsTable,
 ApproveDialog, RejectDialog,
 server actions, filtering
 + tests]
```

**Parallel opportunities**:
- E295A/B are sequential (entity before config).
- E296A/B and E297A/B can run in parallel after E295B (public API vs. admin identity -- independent domains).
- E299A (Keycloak config) is independent of the backend track -- can start anytime after E297A.
- E300A/B (public form frontend) can start after E296B is complete.
- E301A/B (admin panel frontend) requires E298B + E300A (needs AuthContext groups + admin API).
- E302A can run after E298B (depends on approval pipeline being ready).

---

## Implementation Order

### Stage 0: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 295 | 295A | `AccessRequest` entity, `AccessRequestStatus` enum, `AccessRequestRepository`, DTO records, global migration `V15__create_access_requests.sql`. ~6 new files. Backend only. | **Done** (PR #582) |
| 0b | 295 | 295B | `AccessRequestConfigProperties` (blocked domains, OTP expiry, max attempts), `EmailDomainValidator`, `PasswordEncoder` bean for OTP hashing. ~4 new files (~6 unit tests). Backend only. | **Done** (PR #582) |

### Stage 1: Public API & Platform Admin Identity (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 296 | 296A | `AccessRequestService.submitRequest()` — OTP generation, email domain validation, OTP email sending via existing `EmailProvider`. `AccessRequestPublicController` with `POST /api/access-requests`. `SecurityConfig` update to permitAll on `/api/access-requests/**`. ~4 new/modified files (~8 tests). Backend only. | **Done** (PR #583) |
| 1b (parallel) | 296 | 296B | `AccessRequestService.verifyOtp()` — OTP verification, attempt tracking, expiry check, status promotion to PENDING. `POST /api/access-requests/verify` endpoint. ~2 modified files (~7 tests). Backend only. | **Done** (PR #584) |
| 1c (parallel) | 297 | 297A | `RequestScopes.GROUPS` ScopedValue, `ClerkJwtUtils.extractGroups()`, `PlatformSecurityService.isPlatformAdmin()`. New `PlatformAdminFilter` binding groups from JWT. ~4 new/modified files (~6 unit tests). Backend only. | **Done** (PR #585) |
| 1d | 297 | 297B | `SecurityConfig` filter chain update — `PlatformAdminFilter` ordering, `/api/platform-admin/**` requiring authentication. `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")` integration test. ~2 modified files (~5 tests). Backend only. | |

### Stage 2: Approval Pipeline & Admin API (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 298 | 298A | `AccessRequestApprovalService.approve()` — orchestrates KC org creation (via backend's own Keycloak admin client or HTTP call to gateway), tenant provisioning, KC invitation. Idempotent. `reject()` method. ~3 new files (~8 tests). Backend only. | |
| 2b | 298 | 298B | `PlatformAdminController` — `GET /api/platform-admin/access-requests`, `POST /{id}/approve`, `POST /{id}/reject`. `@PreAuthorize` guards. ~2 new files (~7 tests). Backend only. | |

### Stage 3: Keycloak Configuration (parallel with Stage 1-2)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 299 | 299A | Keycloak seed script: create `platform-admins` group, add Group Membership Mapper to `gateway-bff` client scope, update `realm-export.json`, gateway route for `/api/platform-admin/**`. ~4 modified files. Infra only. | |

### Stage 4: Public Frontend (after Stage 1)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 300 | 300A | `/request-access` page + route, `RequestAccessForm` component (Step 1: email, name, org, country, industry), server action calling `POST /api/access-requests`, client-side blocked domain check. ~6 new files. Frontend only. | |
| 4b | 300 | 300B | OTP verification step (Step 2: OTP input), success message (Step 3), error handling, retry flow. Server action calling `POST /api/access-requests/verify`. ~3 modified/new files (~6 tests). Frontend only. | |

### Stage 5: Platform Admin Frontend (after Stage 2 + 4a)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 301 | 301A | `AuthContext.groups` field, BFF `/bff/me` groups extraction update, `(platform-admin)` layout with route guard, sidebar nav link (conditional on group). ~6 new/modified files (~4 tests). Frontend only. | |
| 5b | 301 | 301B | `AccessRequestsTable`, `ApproveDialog`, `RejectDialog`, server actions for approve/reject, status filtering. ~6 new files (~6 tests). Frontend only. | |

### Stage 6: Cleanup & Toggle (after Stage 2)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a | 302 | 302A | Disable/gate self-service org creation on frontend (remove or hide create-org page for non-platform-admins), add `app.jit-provisioning.enabled` toggle to backend `TenantFilter`. ~4 modified files (~4 tests). Both. | |

### Timeline

```
Stage 0: [295A] → [295B]                                              (sequential)
Stage 1: [296A] → [296B] // [297A] → [297B]                          (2 parallel tracks)
Stage 2: [298A] → [298B]                                              (sequential, after both Stage 1 tracks)
Stage 3: [299A]                                                        (parallel with Stages 1-2, after 297A)
Stage 4: [300A] → [300B]                                              (after 296B)
Stage 5: [301A] → [301B]                                              (after 298B + 300A)
Stage 6: [302A]                                                        (after 298B)
```

**Critical path**: 295A -> 295B -> 296A -> 296B -> 298A -> 298B -> 301A -> 301B (8 slices sequential at most).

**Fastest path with parallelism**: 2 starting points after 295B, infra parallel, frontend overlapping. Estimated: 13 slices total, 8 on critical path.

---

## Epic 295: Access Request Entity Foundation & Migration

**Goal**: Create the `AccessRequest` entity in the public schema with its enum, repository, DTO records, and the global database migration. This is the foundational data model that all other epics depend on.

**References**: Architecture doc Sections 39.3, 39.9.

**Dependencies**: None -- greenfield entity in public schema.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **295A** | 295.1--295.5 | `AccessRequest` entity (JPA, public schema), `AccessRequestStatus` enum, `AccessRequestRepository` with custom queries, DTO records (`AccessRequestSubmission`, `AccessRequestResponse`, `OtpVerifyRequest`), global migration `V15__create_access_requests.sql`. ~6 new files. Backend only. | **Done** (PR #582) |
| **295B** | 295.6--295.10 | `AccessRequestConfigProperties` record (blocked domains list, OTP expiry minutes, max attempts), `EmailDomainValidator` utility, `PasswordEncoder` bean registration for OTP BCrypt hashing, unit tests for domain validation and config binding. ~4 new files (~6 tests). Backend only. | **Done** (PR #582) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 295.1 | Create `AccessRequestStatus` enum | 295A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestStatus.java`. Values: `PENDING_VERIFICATION`, `PENDING`, `APPROVED`, `REJECTED`. Pattern: `backend/.../customer/LifecycleStatus.java` for enum conventions. |
| 295.2 | Create `AccessRequest` entity | 295A | 295.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequest.java`. JPA entity mapped to `access_requests` table. Fields per architecture doc Section 39.3.1. Use `@Table(schema = "public")` explicitly since this is NOT a tenant-scoped entity. Use `@PrePersist`/`@PreUpdate` for `createdAt`/`updatedAt`. Pattern: `backend/.../provisioning/Organization.java` (also public schema entity). |
| 295.3 | Create `AccessRequestRepository` | 295A | 295.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestRepository.java`. Extends `JpaRepository<AccessRequest, UUID>`. Custom queries: `findByEmailAndStatus(String email, AccessRequestStatus status)`, `existsByEmailAndStatusIn(String email, List<AccessRequestStatus> statuses)` (for duplicate check), `findByStatusOrderByCreatedAtAsc(AccessRequestStatus status)` (admin list). Pattern: `backend/.../provisioning/OrganizationRepository.java`. |
| 295.4 | Create DTO records | 295A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/dto/AccessRequestDtos.java`. Records: `AccessRequestSubmission(String email, String fullName, String organizationName, String country, String industry)`, `OtpVerifyRequest(String email, String otp)`, `AccessRequestResponse(UUID id, String email, String fullName, String organizationName, String country, String industry, AccessRequestStatus status, Instant otpVerifiedAt, Instant createdAt, String reviewedBy, Instant reviewedAt, String keycloakOrgId)`, `SubmitResponse(String message, int expiresInMinutes)`, `VerifyResponse(String message)`. Pattern: `backend/.../informationrequest/dto/InformationRequestDtos.java` for nested records in single file. |
| 295.5 | Create global migration `V15__create_access_requests.sql` | 295A | | New file: `backend/src/main/resources/db/migration/global/V15__create_access_requests.sql`. DDL from architecture doc Section 39.9: `CREATE TABLE access_requests(...)`, partial unique index on `(email) WHERE status IN ('PENDING_VERIFICATION', 'PENDING')`, indexes on `status` and `email`. Pattern: existing global migrations `V1__*.sql` through `V14__*.sql`. |
| 295.6 | Create `AccessRequestConfigProperties` | 295B | 295A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestConfigProperties.java`. `@ConfigurationProperties(prefix = "app.access-request")`. Fields: `List<String> blockedEmailDomains`, `int otpExpiryMinutes` (default 10), `int otpMaxAttempts` (default 5). Pattern: existing `@ConfigurationProperties` classes in the codebase. Add YAML defaults to `application.yml`. |
| 295.7 | Add config defaults to `application.yml` | 295B | 295.6 | Modify: `backend/src/main/resources/application.yml`. Add `app.access-request.blocked-email-domains` list (gmail.com, yahoo.com, hotmail.com, outlook.com, aol.com, icloud.com, mail.com, protonmail.com, zoho.com), `otp-expiry-minutes: 10`, `otp-max-attempts: 5`. |
| 295.8 | Create `EmailDomainValidator` | 295B | 295.6 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/EmailDomainValidator.java`. Utility class: `boolean isBlockedDomain(String email)` — extracts domain after `@`, case-insensitive match against `blockedEmailDomains` list. Constructor injection of `AccessRequestConfigProperties`. |
| 295.9 | Register `PasswordEncoder` bean | 295B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/` — add a `@Bean PasswordEncoder passwordEncoder()` returning `new BCryptPasswordEncoder()` (if not already registered). Check if Spring Security auto-config already provides one. The `PasswordEncoder` is used for OTP hashing in `AccessRequestService`. |
| 295.10 | Write unit tests for config and domain validator | 295B | 295.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/EmailDomainValidatorTest.java`. Tests (~6): (1) `gmailBlocked`; (2) `companEmailAllowed`; (3) `caseInsensitive`; (4) `nullEmailThrows`; (5) `emptyDomainListAllowsAll`; (6) `subdomainOfBlockedNotBlocked` (e.g., `corp.gmail.com` should NOT be blocked). Pure unit tests, no Spring context. |

### Key Files

**Slice 295A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/dto/AccessRequestDtos.java`
- `backend/src/main/resources/db/migration/global/V15__create_access_requests.sql`

**Slice 295A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java` -- public schema entity pattern
- `backend/src/main/resources/db/migration/global/V14__portal_requests.sql` -- latest migration for version numbering

**Slice 295B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestConfigProperties.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/EmailDomainValidator.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/EmailDomainValidatorTest.java`

**Slice 295B -- Modify:**
- `backend/src/main/resources/application.yml` -- add `app.access-request` config block

**Slice 295B -- Read for context:**
- Existing `@ConfigurationProperties` classes in the project for pattern

### Architecture Decisions

- **Public schema entity**: `AccessRequest` lives in the `public` schema (not tenant-scoped) because it exists before any tenant is created. Uses `@Table(schema = "public")` explicitly.
- **Global migration**: `V15__create_access_requests.sql` runs once against the public schema at startup, not per-tenant.
- **DTO records in single file**: Following the `InformationRequestDtos.java` pattern of grouping related request/response records in one file.
- **BCrypt for OTP hash**: Reuses Spring Security's `PasswordEncoder` (BCrypt) to hash 6-digit OTP codes. The hash is cleared after successful verification.

---

## Epic 296: OTP Verification & Public Access Request API

**Goal**: Implement the public (unauthenticated) API endpoints for submitting access requests and verifying OTP codes. Includes email sending, rate limiting, and the security configuration change to permit unauthenticated access.

**References**: Architecture doc Sections 39.5.1, 39.7.

**Dependencies**: Epic 295 (entity, repository, config, domain validator).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **296A** | 296.1--296.6 | `AccessRequestService.submitRequest()` -- validate email domain, check duplicate pending, generate OTP, hash with BCrypt, save `PENDING_VERIFICATION` entity, send OTP email. `AccessRequestPublicController` with `POST /api/access-requests`. `SecurityConfig` update to permitAll on `/api/access-requests/**`. OTP email template method in service. ~4 new/modified files (~8 integration tests). Backend only. | **Done** (PR #583) |
| **296B** | 296.7--296.11 | `AccessRequestService.verifyOtp()` -- look up by email + `PENDING_VERIFICATION` status, check attempt count, check expiry, match OTP against hash, promote to `PENDING`, clear OTP hash. `POST /api/access-requests/verify` endpoint. ~2 modified files (~7 integration tests). Backend only. | **Done** (PR #584) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 296.1 | Add `POST /api/access-requests` to `SecurityConfig` permitAll | 296A | 295A | Modify: `backend/.../security/SecurityConfig.java`. In `securityFilterChain()`, add `.requestMatchers("/api/access-requests/**").permitAll()` BEFORE the `.requestMatchers("/api/**").authenticated()` line. Pattern: existing `.requestMatchers("/api/webhooks/email/**").permitAll()` in same method. |
| 296.2 | Create `AccessRequestService` with `submitRequest()` | 296A | 295B | New file: `backend/.../accessrequest/AccessRequestService.java`. `@Service` with constructor injection of `AccessRequestRepository`, `AccessRequestConfigProperties`, `EmailDomainValidator`, `PasswordEncoder`, `SecureRandom`. `submitRequest(AccessRequestSubmission dto)` method: (1) validate domain, (2) check duplicate via `existsByEmailAndStatusIn()`, (3) generate 6-digit OTP, (4) create entity with BCrypt-hashed OTP and `PENDING_VERIFICATION` status, (5) send OTP email. Throws `InvalidStateException` for blocked domains, `ResourceConflictException` for duplicates. Pattern: `backend/.../portal/MagicLinkService.java` for similar OTP/token generation pattern. |
| 296.3 | Add OTP email sending method | 296A | 296.2 | In `AccessRequestService` or a new `AccessRequestEmailService`. Use `JavaMailSender` directly (this is a public/global context email, NOT tenant-scoped -- cannot use `EmailNotificationChannel` which requires tenant context). Simple text email: "Your DocTeams verification code is: {OTP}. This code expires in {minutes} minutes." Pattern: `backend/.../portal/PortalEmailService.java` for direct `JavaMailSender` usage. If `JavaMailSender` is not available, use the `SmtpEmailProvider` or `NoOpEmailProvider` via `EmailProvider` interface. |
| 296.4 | Create `AccessRequestPublicController` | 296A | 296.2 | New file: `backend/.../accessrequest/AccessRequestPublicController.java`. `@RestController @RequestMapping("/api/access-requests")`. Single endpoint: `@PostMapping` delegating to `accessRequestService.submitRequest()`, returns `ResponseEntity<SubmitResponse>`. Pure delegation -- no business logic. |
| 296.5 | Create `AccessRequestMapper` (entity to response) | 296A | 295.4 | In `AccessRequestService` or as a private method. Maps `AccessRequest` entity to `AccessRequestResponse` DTO. Used by both public and admin endpoints. |
| 296.6 | Write integration tests for submit endpoint | 296A | 296.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestPublicControllerTest.java`. Tests (~8): (1) `submitRequest_validCompanyEmail_returns200`; (2) `submitRequest_blockedDomain_returns400`; (3) `submitRequest_duplicatePending_returns409`; (4) `submitRequest_missingFields_returns400`; (5) `submitRequest_createsEntityWithPendingVerification`; (6) `submitRequest_noAuthRequired` (no JWT, should still work); (7) `submitRequest_otpHashStored`; (8) `submitRequest_previousRejectedEmail_allowsNewRequest`. Use `@SpringBootTest @AutoConfigureMockMvc`. No JWT needed (public endpoint). |
| 296.7 | Implement `verifyOtp()` in `AccessRequestService` | 296B | 296A | Modify: `backend/.../accessrequest/AccessRequestService.java`. `verifyOtp(String email, String otp)` method: (1) find by email + `PENDING_VERIFICATION`, (2) check `otpAttempts >= maxAttempts` -> throw `InvalidStateException` with 429-like message, (3) check `otpExpiresAt.isBefore(now)` -> throw `InvalidStateException`, (4) increment attempts, (5) match OTP via `passwordEncoder.matches()`, (6) on success: promote to `PENDING`, set `otpVerifiedAt`, clear `otpHash`. |
| 296.8 | Add `POST /api/access-requests/verify` endpoint | 296B | 296.7 | Modify: `backend/.../accessrequest/AccessRequestPublicController.java`. Add `@PostMapping("/verify")` delegating to `accessRequestService.verifyOtp()`, returns `ResponseEntity<VerifyResponse>`. |
| 296.9 | Create custom exception for too many attempts | 296B | | New file or modify: `backend/.../exception/TooManyRequestsException.java`. Extends `RuntimeException`, mapped to HTTP 429 via `@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)` or a `@ControllerAdvice` handler. Check if a global exception handler already exists. Pattern: other exceptions in `backend/.../exception/` package. |
| 296.10 | Add expired request cleanup query | 296B | | Modify: `backend/.../accessrequest/AccessRequestRepository.java`. Add `@Modifying @Query` method: `deleteByStatusAndOtpExpiresAtBefore(AccessRequestStatus status, Instant cutoff)` for periodic cleanup of stale `PENDING_VERIFICATION` records. (Scheduler implementation is out of scope for this phase -- just the query.) |
| 296.11 | Write integration tests for verify endpoint | 296B | 296.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestVerifyTest.java`. Tests (~7): (1) `verifyOtp_validCode_promotesToPending`; (2) `verifyOtp_invalidCode_incrementsAttempts`; (3) `verifyOtp_expiredCode_returns400`; (4) `verifyOtp_tooManyAttempts_returns429`; (5) `verifyOtp_noMatchingRequest_returns404`; (6) `verifyOtp_clearsOtpHashOnSuccess`; (7) `verifyOtp_setsOtpVerifiedAt`. Use `@SpringBootTest @AutoConfigureMockMvc`. |

### Key Files

**Slice 296A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestPublicController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestPublicControllerTest.java`

**Slice 296A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` -- add permitAll for `/api/access-requests/**`

**Slice 296A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalEmailService.java` -- email sending pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkService.java` -- OTP/token generation pattern

**Slice 296B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestPublicController.java`

**Slice 296B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/TooManyRequestsException.java` (if not already present)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestVerifyTest.java`

### Architecture Decisions

- **Direct `JavaMailSender` usage**: OTP emails are sent from a public (pre-tenancy) context, so the tenant-scoped `EmailNotificationChannel` cannot be used. Direct `JavaMailSender` or `SmtpEmailProvider` is appropriate here.
- **BCrypt OTP hashing**: Prevents OTP exposure in database breaches. Hash is cleared after verification for data minimization.
- **SecurityConfig permitAll**: `/api/access-requests/**` is added to the existing main filter chain's permitAll list, alongside webhooks and portal acceptance endpoints.

---

## Epic 297: Platform Admin Identity & Security Infrastructure

**Goal**: Introduce the platform admin concept to the backend: extract `groups` claim from JWT, expose via `RequestScopes.GROUPS`, and create the `PlatformSecurityService` for `@PreAuthorize` checks on platform admin endpoints.

**References**: Architecture doc Sections 39.4.2, 39.4.3.

**Dependencies**: Epic 295 (entity exists for admin queries).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **297A** | 297.1--297.5 | `RequestScopes.GROUPS` ScopedValue, `ClerkJwtUtils.extractGroups()` method, `PlatformSecurityService` with `isPlatformAdmin()`, `PlatformAdminFilter` that binds groups from JWT. ~4 new/modified files (~6 unit tests). Backend only. | **Done** (PR #585) |
| **297B** | 297.6--297.9 | `SecurityConfig` update to insert `PlatformAdminFilter` in filter chain, verify `/api/platform-admin/**` requires authentication, `@PreAuthorize` integration test with mock JWT containing groups claim. ~2 modified files (~5 integration tests). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 297.1 | Add `GROUPS` ScopedValue to `RequestScopes` | 297A | | Modify: `backend/.../multitenancy/RequestScopes.java`. Add `public static final ScopedValue<Set<String>> GROUPS = ScopedValue.newInstance();`. Add `public static Set<String> getGroups()` helper (returns empty set if not bound). Add `public static boolean isPlatformAdmin()` convenience method checking if `GROUPS` contains `"platform-admins"`. |
| 297.2 | Add `extractGroups()` to `ClerkJwtUtils` | 297A | | Modify: `backend/.../security/ClerkJwtUtils.java`. Add `public static Set<String> extractGroups(Jwt jwt)` method. For Keycloak JWTs: extract `groups` claim (expected `List<String>`). For Clerk JWTs: return empty set (Clerk does not support groups). Handle null/missing claim gracefully. |
| 297.3 | Create `PlatformSecurityService` | 297A | 297.1 | New file: `backend/.../accessrequest/PlatformSecurityService.java`. `@Service("platformSecurityService")`. Method: `public boolean isPlatformAdmin()` -- delegates to `RequestScopes.isPlatformAdmin()`. This is the SpEL target for `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")`. Pattern: other SpEL-referenced services used in `@PreAuthorize`. |
| 297.4 | Create `PlatformAdminFilter` | 297A | 297.1, 297.2 | New file: `backend/.../security/PlatformAdminFilter.java`. Extends `OncePerRequestFilter`. Extracts JWT from Spring Security context (via `SecurityContextHolder`), calls `ClerkJwtUtils.extractGroups()`, binds to `RequestScopes.GROUPS` via `ScopedValue.where().run()`. Only activates if JWT authentication is present. Pattern: `backend/.../multitenancy/TenantFilter.java` for ScopedValue binding in filter. |
| 297.5 | Write unit tests for groups extraction and platform security | 297A | 297.2, 297.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtilsGroupsTest.java`. Tests (~6): (1) `extractGroups_keycloakJwt_returnsGroups`; (2) `extractGroups_clerkJwt_returnsEmpty`; (3) `extractGroups_missingClaim_returnsEmpty`; (4) `extractGroups_nullClaim_returnsEmpty`; (5) `isPlatformAdmin_withGroup_returnsTrue`; (6) `isPlatformAdmin_withoutGroup_returnsFalse`. Use mock `Jwt` objects. |
| 297.6 | Add `PlatformAdminFilter` to `SecurityConfig` filter chain | 297B | 297A | Modify: `backend/.../security/SecurityConfig.java`. Add `PlatformAdminFilter` to constructor injection. Insert `.addFilterAfter(platformAdminFilter, MemberFilter.class)` in `securityFilterChain()`. The filter runs after `MemberFilter` so JWT is already validated and tenant/member context is bound. Platform admin endpoints also need tenant context to exist. |
| 297.7 | Ensure `/api/platform-admin/**` requires authentication | 297B | 297.6 | Verify: In `SecurityConfig.securityFilterChain()`, `/api/platform-admin/**` is matched by the existing `.requestMatchers("/api/**").authenticated()` catch-all. No additional rule needed -- just verify this with a test. The `@PreAuthorize` on the controller provides the additional group check. |
| 297.8 | Enable `@EnableMethodSecurity` if not already | 297B | | Verify: `SecurityConfig.java` already has `@EnableMethodSecurity`. If not, add it. This is required for `@PreAuthorize` annotations to work. |
| 297.9 | Write integration tests for platform admin security | 297B | 297.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminSecurityTest.java`. Tests (~5): (1) `platformAdminEndpoint_withGroupClaim_returns200`; (2) `platformAdminEndpoint_withoutGroupClaim_returns403`; (3) `platformAdminEndpoint_noAuth_returns401`; (4) `platformAdminEndpoint_wrongGroup_returns403`; (5) `regularApiEndpoint_unaffectedByGroupFilter`. Use `@SpringBootTest @AutoConfigureMockMvc` with mock JWT containing `groups` claim. Mock JWT pattern: `jwt().jwt(j -> j.subject("admin-user").claim("groups", List.of("platform-admins")).claim("o", Map.of(...))).authorities(...)`. |

### Key Files

**Slice 297A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- add `GROUPS` ScopedValue
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` -- add `extractGroups()`

**Slice 297A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformSecurityService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/PlatformAdminFilter.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtilsGroupsTest.java`

**Slice 297B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` -- add filter + verify auth rules

**Slice 297B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminSecurityTest.java`

**Slice 297B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` -- ScopedValue binding in filter pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` -- filter chain ordering

### Architecture Decisions

- **ScopedValue for groups**: Consistent with existing `RequestScopes` pattern. Groups are bound once by `PlatformAdminFilter` and accessible throughout the request scope.
- **Filter after MemberFilter**: Platform admin endpoints are also org-scoped (the admin has a JWT with org claims). The groups filter runs after tenant/member resolution so all contexts are available.
- **SpEL-based `@PreAuthorize`**: Using `@platformSecurityService.isPlatformAdmin()` rather than role-based authorities keeps the platform admin concept separate from org roles. Platform admins are NOT an org role -- they are a cross-tenant capability.
- **Keycloak-only groups**: The `groups` claim is only available in Keycloak JWTs (via Group Membership Mapper). Clerk JWTs return an empty group set, which is correct -- Clerk mode does not support platform admin functionality.

---

## Epic 298: Approval Pipeline & Platform Admin API

**Goal**: Implement the approval/rejection workflow that orchestrates Keycloak org creation, tenant schema provisioning, and Keycloak invitation on approval. Expose platform admin API endpoints for listing, approving, and rejecting access requests.

**References**: Architecture doc Sections 39.5.2, 39.6.

**Dependencies**: Epic 296 (access request submission exists), Epic 297 (platform admin security).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **298A** | 298.1--298.5 | `AccessRequestApprovalService` -- orchestrates approval: create KC org via `KeycloakAdminClient` (call gateway or add backend's own KC client), provision tenant schema via `TenantProvisioningService`, send KC invitation, mark `APPROVED`. `reject()` method. Idempotent retry support. ~3 new files (~8 integration tests). Backend only. | |
| **298B** | 298.6--298.10 | `PlatformAdminController` with `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")`. Endpoints: `GET /api/platform-admin/access-requests` (with optional `?status=` filter), `POST /api/platform-admin/access-requests/{id}/approve`, `POST /api/platform-admin/access-requests/{id}/reject`. Pure delegation to service. ~2 new files (~7 integration tests). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 298.1 | Create `KeycloakProvisioningClient` in backend | 298A | | New file: `backend/.../accessrequest/KeycloakProvisioningClient.java`. `@Service` wrapping a `RestClient` that calls the **gateway's admin proxy endpoints** (`POST /bff/admin/invite`, etc.) or calls Keycloak Admin API directly. Decision: calling the gateway is simpler (reuses existing `KeycloakAdminClient` in gateway), but requires backend-to-gateway HTTP calls. Alternative: duplicate minimal Keycloak admin logic in backend. **Recommended**: Create a lightweight Keycloak Admin REST client in the backend using the same `RestClient` pattern as the gateway's `KeycloakAdminClient.java`. This avoids circular dependency (backend -> gateway -> backend). Methods: `createOrganization(String name, String slug)`, `inviteUser(String orgId, String email)`. Uses master realm admin credentials from config. |
| 298.2 | Add Keycloak admin config to backend `application.yml` | 298A | 298.1 | Modify: `backend/src/main/resources/application.yml`. Add `keycloak.admin.auth-server-url`, `keycloak.admin.realm`, `keycloak.admin.username`, `keycloak.admin.password` properties (same as gateway config, from env vars). Only needed when `app.access-request` features are enabled. |
| 298.3 | Implement `AccessRequestApprovalService.approve()` | 298A | 298.1, 296A | New file: `backend/.../accessrequest/AccessRequestApprovalService.java`. `@Service @Transactional`. `approve(UUID requestId, String adminUserId)` method per architecture doc Section 39.6: (1) find request, verify `PENDING` status, (2) slugify org name, (3) call `keycloakProvisioningClient.createOrganization()`, (4) call `tenantProvisioningService.provisionTenant()` with KC org ID, (5) call `keycloakProvisioningClient.inviteUser()`, (6) mark `APPROVED`, set `keycloakOrgId`, `reviewedBy`, `reviewedAt`. Catch exceptions -> set `provisioningError`, re-throw. |
| 298.4 | Implement `AccessRequestApprovalService.reject()` | 298A | 298.3 | In `AccessRequestApprovalService`. `reject(UUID requestId, String adminUserId)`: find request, verify `PENDING` status, set status to `REJECTED`, set `reviewedBy`, `reviewedAt`. Simple -- no external calls. |
| 298.5 | Write integration tests for approval pipeline | 298A | 298.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalServiceTest.java`. Tests (~8): (1) `approve_pendingRequest_createsKcOrg` (mock KC client); (2) `approve_pendingRequest_provisionsTenant` (mock provisioning); (3) `approve_pendingRequest_sendsInvitation` (mock KC client); (4) `approve_pendingRequest_marksApproved`; (5) `approve_alreadyApproved_throwsConflict`; (6) `approve_rejected_throwsConflict`; (7) `approve_kcFailure_setsProvisioningError`; (8) `reject_pendingRequest_marksRejected`. Use `@SpringBootTest` with mocked `KeycloakProvisioningClient` and `TenantProvisioningService` via `@MockitoBean`. |
| 298.6 | Create `PlatformAdminController` | 298B | 298A, 297B | New file: `backend/.../accessrequest/PlatformAdminController.java`. `@RestController @RequestMapping("/api/platform-admin/access-requests") @PreAuthorize("@platformSecurityService.isPlatformAdmin()")`. Three endpoints, pure delegation. Pattern: thin controller discipline from `backend/CLAUDE.md`. |
| 298.7 | Add `GET /api/platform-admin/access-requests` (list) | 298B | 298.6 | In `PlatformAdminController`. `@GetMapping` with optional `@RequestParam("status") AccessRequestStatus status`. Delegates to service method that calls `repository.findByStatus()` or `repository.findAll()`. Returns `List<AccessRequestResponse>`. |
| 298.8 | Add `POST /{id}/approve` endpoint | 298B | 298.6 | In `PlatformAdminController`. `@PostMapping("/{id}/approve")`. Extracts admin user ID from JWT (`SecurityContextHolder` or `@AuthenticationPrincipal`). Delegates to `approvalService.approve(id, adminUserId)`. Returns approval result DTO. |
| 298.9 | Add `POST /{id}/reject` endpoint | 298B | 298.6 | In `PlatformAdminController`. `@PostMapping("/{id}/reject")`. Delegates to `approvalService.reject(id, adminUserId)`. Returns simple success message. |
| 298.10 | Write integration tests for admin controller | 298B | 298.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminControllerTest.java`. Tests (~7): (1) `listRequests_platformAdmin_returns200`; (2) `listRequests_filterByStatus_returnsFiltered`; (3) `listRequests_nonAdmin_returns403`; (4) `approve_platformAdmin_returns200`; (5) `approve_nonAdmin_returns403`; (6) `reject_platformAdmin_returns200`; (7) `approve_notFound_returns404`. Use `@SpringBootTest @AutoConfigureMockMvc` with mock JWT containing `groups: ["platform-admins"]`. Mock `KeycloakProvisioningClient` and `TenantProvisioningService` via `@MockitoBean`. |

### Key Files

**Slice 298A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalServiceTest.java`

**Slice 298A -- Modify:**
- `backend/src/main/resources/application.yml` -- Keycloak admin config

**Slice 298A -- Read for context:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java` -- Keycloak Admin API pattern to replicate
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` -- provisioning API

**Slice 298B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminControllerTest.java`

### Architecture Decisions

- **Backend's own Keycloak admin client**: Rather than calling the gateway's admin proxy (which would create a backend -> gateway -> Keycloak circular dependency), the backend gets its own lightweight `KeycloakProvisioningClient` that calls Keycloak Admin REST API directly. This duplicates ~50 lines of REST client code from the gateway but keeps the architecture clean. The gateway client handles org member management (invitations, member list), while the backend client handles provisioning-specific operations (org creation, invitation for approved requests).
- **`@PreAuthorize` on class level**: All methods in `PlatformAdminController` require platform admin access, so the annotation is placed at class level rather than per-method.
- **Idempotent approval**: Per architecture doc Section 39.6, Keycloak `createOrganization()` returns existing org if alias matches, `provisionTenant()` checks for existing schema, and `inviteUser()` ignores duplicates. Safe to retry after partial failures.

---

## Epic 299: Keycloak Configuration & Gateway Routing

**Goal**: Configure Keycloak realm with the `platform-admins` group and Group Membership Mapper, update the gateway to proxy platform admin API routes, and update seed scripts.

**References**: Architecture doc Sections 39.4.1, 39.4.3, 39.10.

**Dependencies**: Epic 297 (backend expects `groups` claim in JWT).

**Scope**: Infra

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **299A** | 299.1--299.5 | Keycloak seed script updates: create `platform-admins` group, add Group Membership Mapper to `gateway-bff` client scope, assign initial admin user to group. Update `realm-export.json`. Gateway `application.yml` route for `/api/platform-admin/**`. ~4 modified files. Infra only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 299.1 | Add `platform-admins` group creation to seed script | 299A | | Modify: `compose/scripts/keycloak-seed.sh`. After realm setup, create group via Keycloak Admin REST API: `POST /admin/realms/docteams/groups` with `{"name": "platform-admins"}`. Pattern: existing group/role creation calls in the same script. |
| 299.2 | Add Group Membership Mapper to client scope | 299A | 299.1 | Modify: `compose/scripts/keycloak-seed.sh`. Add protocol mapper to the `gateway-bff` client scope: `POST /admin/realms/docteams/client-scopes/{id}/protocol-mappers/models` with `{"name": "groups", "protocol": "openid-connect", "protocolMapper": "oidc-group-membership-mapper", "config": {"claim.name": "groups", "full.path": "false", "id.token.claim": "true", "access.token.claim": "true"}}`. |
| 299.3 | Assign seed admin user to `platform-admins` group | 299A | 299.1 | Modify: `compose/scripts/keycloak-seed.sh`. After creating the admin user (or using existing seed user), add to group: `PUT /admin/realms/docteams/users/{userId}/groups/{groupId}`. This ensures local dev has a platform admin for testing. |
| 299.4 | Update `realm-export.json` | 299A | | Modify: `compose/keycloak/realm-export.json`. Add `platform-admins` group definition and the Group Membership Mapper to the client scope section. This ensures automated imports include the configuration. |
| 299.5 | Verify gateway route for `/api/platform-admin/**` | 299A | | Verify: Gateway's existing route config for `/api/**` should already proxy `/api/platform-admin/**` to the backend (it is a sub-path of `/api/**`). If not, add explicit route in `gateway/src/main/resources/application.yml`. No change expected -- just verify and document. |

### Key Files

**Slice 299A -- Modify:**
- `compose/scripts/keycloak-seed.sh` -- group creation, mapper, admin assignment
- `compose/keycloak/realm-export.json` -- group and mapper config
- `gateway/src/main/resources/application.yml` -- verify route (likely no change)

**Slice 299A -- Read for context:**
- Existing `keycloak-seed.sh` for API call patterns
- `gateway/src/main/resources/application.yml` for route configuration patterns

### Architecture Decisions

- **Group Membership Mapper (built-in)**: Uses Keycloak's built-in `oidc-group-membership-mapper` -- no custom SPI needed. The mapper adds a `groups` array to both ID and access tokens.
- **Seed script for local dev**: The group and mapper are created via seed script for local development. Production Keycloak should have these configured via realm import or Terraform/Pulumi.
- **Gateway route reuse**: The existing `/api/**` route in the gateway already covers `/api/platform-admin/**`. No new route definition needed.

---

## Epic 300: Public Access Request Form (Frontend)

**Goal**: Create the public `/request-access` page with a multi-step form: Step 1 collects company details, Step 2 verifies OTP, Step 3 shows success message. Unauthenticated -- no login required.

**References**: Architecture doc Section 39.8.1.

**Dependencies**: Epic 296 (backend public API endpoints).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **300A** | 300.1--300.6 | `/request-access` page + route (outside `(app)` layout), `RequestAccessForm` component (Step 1: email, full name, org name, country dropdown, industry dropdown), client-side blocked domain check, server action calling `POST /api/access-requests`. ~6 new files. Frontend only. | |
| **300B** | 300.7--300.11 | OTP verification step (Step 2: 6-digit OTP input), success message (Step 3: "Your request has been submitted"), error handling (invalid OTP, expired, too many attempts), retry flow (resend OTP). Server action calling `POST /api/access-requests/verify`. ~3 modified/new files (~6 tests). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 300.1 | Create `/request-access` route | 300A | | New file: `frontend/app/request-access/page.tsx`. Server Component page outside `(app)` layout -- no auth required. Simple page shell rendering `RequestAccessForm`. Pattern: `frontend/app/page.tsx` (landing page, also unauthenticated). |
| 300.2 | Create `RequestAccessForm` component (Step 1) | 300A | 300.1 | New file: `frontend/components/access-request/request-access-form.tsx`. `"use client"` component. Multi-step form with state management via `useState`. Step 1 fields: email (text input), full name (text input), organization name (text input), country (combobox/select), industry (select). Client-side email domain validation. Submit button triggers server action. Pattern: existing form components in `frontend/components/`. Use Shadcn `Input`, `Select`, `Button`, `Label`. Slate color scheme, teal accent on submit button. |
| 300.3 | Create country and industry lists | 300A | | New file: `frontend/lib/access-request-data.ts`. Export `COUNTRIES: string[]` (common list) and `INDUSTRIES: string[]` (Accounting, Legal, Consulting, Engineering, Architecture, IT Services, Marketing, Other). Export `BLOCKED_EMAIL_DOMAINS: string[]` (mirror of backend list for client-side validation). |
| 300.4 | Create server action for submit | 300A | | New file: `frontend/app/request-access/actions.ts`. `submitAccessRequest(formData)` server action -- calls backend `POST /api/access-requests` via direct `fetch()` (no auth token needed, public endpoint). Returns `{success, message, expiresInMinutes}` or `{error}`. Note: this server action does NOT use `lib/api.ts` since that attaches auth tokens. Use raw `fetch()` with `BACKEND_URL` or gateway URL. |
| 300.5 | Create layout for request-access | 300A | | New file: `frontend/app/request-access/layout.tsx`. Minimal layout without sidebar/header -- centered card design on concrete gray background. DocTeams logo at top. Pattern: `frontend/app/(auth)/layout.tsx` (split-screen auth layout) for inspiration on unauthenticated layouts. |
| 300.6 | Style the form | 300A | | In `request-access-form.tsx`. Centered card (`max-w-lg mx-auto`), Sora heading, IBM Plex Sans body, slate borders, teal accent submit button. Responsive. Loading state on submit (spinner or disabled button). |
| 300.7 | Add OTP verification step (Step 2) | 300B | 300A | Modify: `frontend/components/access-request/request-access-form.tsx`. When Step 1 succeeds, show Step 2: 6-digit OTP input. Could use 6 separate digit inputs or a single text input with `inputMode="numeric" maxLength={6}`. Timer showing OTP expiry countdown. "Resend code" link (re-submits Step 1 form data). |
| 300.8 | Create server action for OTP verification | 300B | | Modify: `frontend/app/request-access/actions.ts`. `verifyAccessRequestOtp(email, otp)` server action -- calls `POST /api/access-requests/verify` via raw `fetch()`. Returns `{success, message}` or `{error}`. |
| 300.9 | Add success step (Step 3) | 300B | 300.7 | Modify: `frontend/components/access-request/request-access-form.tsx`. When Step 2 succeeds, show Step 3: success message ("Your access request has been submitted for review. We'll notify you by email once it's been reviewed."), checkmark icon, "Back to home" link. |
| 300.10 | Add error handling | 300B | 300.7 | In `request-access-form.tsx`. Handle: blocked domain (show inline error on email field), duplicate pending (show message with contact info), invalid OTP (show error, allow retry), expired OTP (show "resend" prompt), too many attempts (show "request new code" prompt). Use Shadcn `Alert` or inline error messages. |
| 300.11 | Write tests for access request form | 300B | 300.7 | New file: `frontend/__tests__/access-request/request-access-form.test.tsx`. Tests (~6): (1) `rendersStep1FormFields`; (2) `blockedDomainShowsError`; (3) `submitShowsOtpInput`; (4) `validOtpShowsSuccess`; (5) `invalidOtpShowsError`; (6) `resendOtpResubmitsForm`. Mock server actions. Pattern: existing frontend tests in `frontend/__tests__/`. |

### Key Files

**Slice 300A -- Create:**
- `frontend/app/request-access/page.tsx`
- `frontend/app/request-access/layout.tsx`
- `frontend/app/request-access/actions.ts`
- `frontend/components/access-request/request-access-form.tsx`
- `frontend/lib/access-request-data.ts`

**Slice 300A -- Read for context:**
- `frontend/app/(auth)/layout.tsx` -- unauthenticated layout pattern
- `frontend/components/ui/input.tsx`, `frontend/components/ui/button.tsx`, `frontend/components/ui/select.tsx` -- Shadcn component usage

**Slice 300B -- Modify:**
- `frontend/components/access-request/request-access-form.tsx` -- add Steps 2 and 3
- `frontend/app/request-access/actions.ts` -- add verify action

**Slice 300B -- Create:**
- `frontend/__tests__/access-request/request-access-form.test.tsx`

### Architecture Decisions

- **Outside `(app)` layout**: The `/request-access` route is public (no auth) so it lives at the root level, not inside `(app)/`. It has its own minimal layout.
- **Raw `fetch()` for public API**: The `lib/api.ts` client attaches auth tokens, which is inappropriate for unauthenticated requests. Server actions use raw `fetch()` with `BACKEND_URL` (or gateway URL in BFF mode).
- **Client-side domain validation**: The blocked domain list is duplicated client-side for immediate feedback. Server-side validation is the source of truth.
- **Multi-step form**: State-managed via `useState` in a single `"use client"` component rather than separate routes. Keeps the flow simple and avoids URL manipulation.

---

## Epic 301: Platform Admin Panel (Frontend)

**Goal**: Create the platform admin panel at `/platform-admin/access-requests` with a filterable table, approve/reject dialogs, and route guard based on the `groups` claim in the auth context.

**References**: Architecture doc Sections 39.4.4, 39.8.2.

**Dependencies**: Epic 298 (backend admin API), Epic 300 (needs `AuthContext` groups pattern).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **301A** | 301.1--301.5 | Extend `AuthContext` with `groups: string[]`, update BFF `/bff/me` response parsing to extract groups, update keycloak-bff provider. Create `(platform-admin)` route group with layout and route guard. Add conditional "Platform Admin" nav link to sidebar. ~6 modified/new files (~4 tests). Frontend only. | |
| **301B** | 301.6--301.11 | `AccessRequestsTable` component with status filtering, `ApproveDialog` confirmation, `RejectDialog` confirmation, server actions for approve/reject/list, loading states. ~6 new files (~6 tests). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 301.1 | Add `groups` to `AuthContext` interface | 301A | | Modify: `frontend/lib/auth/types.ts`. Add `groups: string[]` to `AuthContext` interface. |
| 301.2 | Update BFF auth provider to extract groups | 301A | 301.1 | Modify: `frontend/lib/auth/providers/keycloak-bff.ts`. In `getAuthContext()`, extract `groups` from `/bff/me` response. If `groups` is not in BFF response, return `[]`. |
| 301.3 | Update BFF `/bff/me` endpoint to include groups | 301A | | Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`. Add `List<String> groups` field to `BffUserInfo` record. Extract groups from `OidcUser` claims (`user.getClaim("groups")`). Handle null (return empty list). Also update `BffUserInfoExtractor` if groups extraction logic is complex. |
| 301.4 | Create `(platform-admin)` route group with layout | 301A | 301.1 | New files: `frontend/app/(app)/org/[slug]/platform-admin/layout.tsx` OR `frontend/app/(platform-admin)/layout.tsx` (decide placement -- should it be org-scoped or global?). Per architecture doc, platform admin is cross-tenant, so create at `frontend/app/(app)/platform-admin/layout.tsx` (inside `(app)` for auth but outside `org/[slug]` for no-org requirement). Layout checks `groups.includes("platform-admins")` from auth context, renders 404 for non-admins. Simple sidebar with "Access Requests" link. |
| 301.5 | Add conditional "Platform Admin" nav link | 301A | 301.4 | Modify: `frontend/lib/nav-items.ts` or `frontend/components/desktop-sidebar.tsx`. Add "Platform Admin" link (icon: `Shield` from lucide-react) visible only when `groups.includes("platform-admins")`. Links to `/platform-admin/access-requests`. |
| 301.6 | Create server actions for admin endpoints | 301B | 301A | New file: `frontend/app/(app)/platform-admin/access-requests/actions.ts`. Actions: `listAccessRequests(status?: string)` -- calls `GET /api/platform-admin/access-requests`, `approveAccessRequest(id: string)` -- calls `POST /api/platform-admin/access-requests/{id}/approve`, `rejectAccessRequest(id: string)` -- calls `POST /api/platform-admin/access-requests/{id}/reject`. Use `lib/api.ts` (authenticated). |
| 301.7 | Create `AccessRequestsTable` component | 301B | 301.6 | New file: `frontend/components/access-request/access-requests-table.tsx`. `"use client"` component. Columns: Org Name, Email, Name, Country, Industry, Submitted (relative time), Status (badge). Status filter tabs: ALL / PENDING / APPROVED / REJECTED (default PENDING). Sorted by creation date ascending (oldest first). Uses Shadcn `Table`, `Badge`, `Tabs`. Pattern: existing table components (e.g., team member list). |
| 301.8 | Create `/platform-admin/access-requests` page | 301B | 301.7 | New file: `frontend/app/(app)/platform-admin/access-requests/page.tsx`. Server Component. Fetches access requests via server action, passes to `AccessRequestsTable`. Page heading "Access Requests". |
| 301.9 | Create `ApproveDialog` component | 301B | 301.6 | New file: `frontend/components/access-request/approve-dialog.tsx`. `"use client"` component. Shadcn `AlertDialog` confirming: "Approve access request for {orgName}? This will create a Keycloak organization, provision a tenant schema, and send an invitation to {email}." Approve button (teal accent), Cancel button. Calls `approveAccessRequest()` action on confirm. Loading state during provisioning. Pattern: existing dialog components with `afterEach(() => cleanup())` in tests. |
| 301.10 | Create `RejectDialog` component | 301B | 301.9 | New file: `frontend/components/access-request/reject-dialog.tsx`. `"use client"` component. Simple `AlertDialog`: "Reject access request from {email} for {orgName}? This action cannot be undone." Reject button (destructive variant), Cancel. Calls `rejectAccessRequest()` action. |
| 301.11 | Write tests for admin panel | 301B | 301.7 | New file: `frontend/__tests__/access-request/access-requests-table.test.tsx`. Tests (~6): (1) `rendersTableWithPendingRequests`; (2) `filterByStatus_showsMatchingRequests`; (3) `approveDialog_showsConfirmation`; (4) `rejectDialog_showsConfirmation`; (5) `approveAction_refreshesList`; (6) `emptyState_showsMessage`. Mock server actions. Use `cleanup()` in `afterEach`. |

### Key Files

**Slice 301A -- Modify:**
- `frontend/lib/auth/types.ts` -- add `groups` field
- `frontend/lib/auth/providers/keycloak-bff.ts` -- extract groups from BFF response
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java` -- add `groups` to `/bff/me`
- `frontend/components/desktop-sidebar.tsx` or `frontend/lib/nav-items.ts` -- conditional nav link

**Slice 301A -- Create:**
- `frontend/app/(app)/platform-admin/layout.tsx` -- route guard layout
- `frontend/app/(app)/platform-admin/access-requests/page.tsx` (stub)

**Slice 301B -- Create:**
- `frontend/app/(app)/platform-admin/access-requests/actions.ts`
- `frontend/components/access-request/access-requests-table.tsx`
- `frontend/components/access-request/approve-dialog.tsx`
- `frontend/components/access-request/reject-dialog.tsx`
- `frontend/__tests__/access-request/access-requests-table.test.tsx`

**Slice 301B -- Read for context:**
- `frontend/components/team/` -- table and dialog patterns for member management

### Architecture Decisions

- **Platform admin route outside org scope**: The `(platform-admin)` layout lives under `(app)/` (requires auth) but NOT under `org/[slug]/` (not org-specific). Platform admins operate cross-tenant. This requires the `(app)/layout.tsx` to handle routes where org context may be absent.
- **Route guard via auth context**: The layout checks `groups.includes("platform-admins")` and renders a 404 (not a redirect) for non-admins. This is security-by-obscurity consistent with the backend's 403 response.
- **Gateway BFF `/bff/me` extension**: The `groups` field is added to the BFF response. This is the only change to the gateway in this phase. The Clerk and mock auth providers return `groups: []` (they do not support platform admin).

---

## Epic 302: Self-Service Org Creation Gate & JIT Provisioning Toggle

**Goal**: Disable self-service organization creation for non-platform-admins and add a feature toggle for JIT tenant provisioning so that provisioning only happens through the admin approval pipeline.

**References**: Architecture doc Sections 39.10 (items 3, 4), 39.11.

**Dependencies**: Epic 298 (approval pipeline is the replacement for self-service).

**Scope**: Backend + Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **302A** | 302.1--302.5 | Gate/remove `POST /bff/orgs` (self-service org creation) for non-platform-admins or behind a feature flag. Add `app.jit-provisioning.enabled=false` config property to backend, conditional check in `TenantFilter` JIT provisioning path. Update frontend create-org page. ~4 modified files (~4 tests). Both. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 302.1 | Add JIT provisioning toggle to backend | 302A | | Modify: `backend/src/main/resources/application.yml`. Add `app.jit-provisioning.enabled: false`. Modify: `backend/.../multitenancy/TenantFilter.java` (or wherever JIT provisioning is triggered). Check this property before calling `TenantProvisioningService`. When disabled, JIT provisioning is skipped -- tenants must be pre-provisioned via the approval pipeline. |
| 302.2 | Gate self-service org creation in gateway | 302A | 302.1 | Modify: `gateway/.../controller/BffController.java`. Add a check to `POST /bff/orgs`: either (a) check for `platform-admins` group in the user's claims and only allow org creation for platform admins, or (b) add a `app.self-service-org-creation.enabled=false` config property and return 403 when disabled. Option (b) is simpler and more explicit. |
| 302.3 | Update frontend create-org page | 302A | 302.2 | Modify: `frontend/app/(app)/create-org/page.tsx`. When `AUTH_MODE === "keycloak"`, redirect to `/request-access` instead of showing the create-org form. Or conditionally render a message: "Organization creation is managed by administrators. Please submit an access request." with link to `/request-access`. |
| 302.4 | Update frontend dashboard (no-org state) | 302A | 302.3 | Modify: `frontend/app/(app)/dashboard/page.tsx`. When user has no org and `AUTH_MODE === "keycloak"`, show a different message: "Your access request is being reviewed" or "Contact your administrator" instead of "Create an Organization". |
| 302.5 | Write tests for JIT toggle and org creation gate | 302A | 302.1 | Modify existing test files or new file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/JitProvisioningToggleTest.java`. Tests (~4): (1) `jitEnabled_provisionsTenant`; (2) `jitDisabled_skipsProvisioning`; (3) `createOrg_disabled_returns403` (gateway); (4) `createOrg_platformAdmin_returns201` (gateway). |

### Key Files

**Slice 302A -- Modify:**
- `backend/src/main/resources/application.yml` -- JIT provisioning toggle
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` -- conditional JIT
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java` -- gate org creation
- `frontend/app/(app)/create-org/page.tsx` -- redirect or message
- `frontend/app/(app)/dashboard/page.tsx` -- no-org state message

**Slice 302A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` -- understand JIT provisioning trigger location

### Architecture Decisions

- **Feature flag over removal**: Rather than removing self-service org creation entirely, a `app.self-service-org-creation.enabled` flag allows reverting. The flag defaults to `false` in Keycloak mode. Clerk mode retains self-service.
- **Graceful degradation**: Users who reach the create-org page in Keycloak mode see a helpful redirect to the access request form, not a broken page.
- **JIT provisioning disable**: When `app.jit-provisioning.enabled=false`, `TenantFilter` does not trigger automatic schema creation on first request. This prevents unapproved users from provisioning tenant schemas by somehow obtaining a valid JWT with an org claim.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` - Core security config that needs permitAll for public endpoints and PlatformAdminFilter insertion
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` - Must add GROUPS ScopedValue for platform admin identity
- `/Users/rakheendama/Projects/2026/b2b-strawman/gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java` - Pattern reference for backend's own Keycloak admin client
- `/Users/rakheendama/Projects/2026/b2b-strawman/gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java` - Must extend /bff/me with groups claim and gate org creation
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/auth/types.ts` - AuthContext interface must add groups field for frontend route guards