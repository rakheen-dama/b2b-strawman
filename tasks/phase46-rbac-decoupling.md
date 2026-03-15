# Phase 46 — RBAC Decoupling (Application-Managed Roles)

This phase completes the separation of authentication from authorization. Keycloak remains the identity provider (OIDC login, SSO, token issuance, org membership, invitation emails), but the product database becomes the **sole authority** for all authorization decisions. The JWT no longer carries role information. `@PreAuthorize` annotations on remaining controller files are replaced with `@RequiresCapability` or removed (where `MemberFilter` is the implicit gate). A new `PendingInvitation` entity handles pre-authentication role assignment. The gateway is stripped of all authorization responsibilities, and the frontend switches from `orgRole` to the capabilities API for UI gating.

**Architecture doc**: `architecture/phase46-rbac-decoupling.md`

**ADRs**:
- [ADR-178](adr/ADR-178-db-authoritative-role-resolution.md) — DB-Authoritative Role Resolution
- [ADR-179](adr/ADR-179-pending-invitation-role-assignment.md) — PendingInvitation for Role Assignment
- [ADR-180](adr/ADR-180-gateway-authorization-removal.md) — Gateway Authorization Removal

**Dependencies on prior phases**:
- Phase 41 (Org Roles): `OrgRole`, `Capability`, `OrgRoleService`, `@RequiresCapability`, `CapabilityAuthorizationManager`, `RequestScopes.CAPABILITIES`
- Phase 36 (Gateway BFF): `BffController`, `BffUserInfoExtractor`, `AdminProxyController`, `KeycloakAdminClient`
- Phase 20 (Auth Abstraction): `lib/auth/` module, mock IDP, E2E fixtures
- Phase 13 (Dedicated Schema): Schema-per-tenant isolation

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 345 | PendingInvitation Entity & Invitation Service | Backend | — | M | 345A, 345B | **Done** (PR #670) |
| 346 | MemberFilter DB-Only Role Resolution | Backend | 345 | M | 346A, 346B | **Done** (PR #671) |
| 347 | @PreAuthorize Migration (Remaining Controllers) | Backend | 346 | L | 347A, 347B, 347C | **Done** (PR #672, #673, #674) |
| 348 | Member Entity Cleanup & JwtUtils Rename | Backend | 347 | M | 348A, 348B | **Done** (PR #676) |
| 349 | KeycloakAdminClient Backend Move & Org Endpoint | Backend | 345 | M | 349A, 349B | |
| 350 | Gateway Authorization Removal | Gateway | 349 | M | 350A, 350B | |
| 351 | Frontend Capabilities-Only Authorization | Frontend | 350A | L | 351A, 351B, 351C | |
| 352 | Mock IDP & E2E Fixture Update | Frontend/E2E | 351 | S | 352A | |
| 353 | Cleanup — Remove ROLE_ORG_* & Role Sync | Backend | 351 | S | 353A, 353B | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────────────────────────────────────────────────────────

[E345A PendingInvitation
 entity, V68 migration,
 repository, dto records]
        |
[E345B InvitationService
 CRUD, InvitationController,
 audit events, expiry logic,
 + integration tests]
        |
        +─────────────────────────────────────+
        |                                     |
[E346A MemberFilter:                       [E349A Copy KeycloakAdminClient
 remove jwtHasExplicitRole,                 to backend security/keycloak/,
 role always from DB,                       config properties,
 invitation lookup in                       wire to InvitationService
 lazyCreateMember,                          + integration test]
 grant ROLE_ORG_* from DB                        |
 (backward compat)                         [E349B POST /api/orgs
 + tests]                                   endpoint, org creation
        |                                   logic from gateway
[E346B RequestScopes.requireOwner(),        + tests]
 cache eviction on role
 mutation, MemberRepository
 findAllByOrgRoleId
 + tests]
        |
[E347A @PreAuthorize migration
 batch 1: settings, template,
 document, field definitions
 (~70 annotations, ~12 files)
 + test updates]
        |
[E347B @PreAuthorize migration
 batch 2: task, time, expense,
 project, calendar, budget
 (~60 annotations, ~14 files)
 + test updates]
        |
[E347C @PreAuthorize migration
 batch 3: billing, retainer,
 notification, dashboard,
 remaining controllers
 (~55 annotations, ~16 files)
 + test updates]
        |
        +─────────────────────────────────────+
        |                                     |
[E348A Drop members.org_role             [E348B Rename ClerkJwtUtils
 VARCHAR, make org_role_id                → JwtUtils, remove Clerk v2
 NOT NULL, V69 migration,                 format methods,
 update Member entity,                    extractOrgRole(),
 update constructor/factories             add extractEmail(),
 + test updates]                          update ~8 imports
                                          + tests]

GATEWAY TRACK (after E349)
──────────────────────────────────────────────────────────

                                     [E349B ready]
                                          |
                                     [E350A /bff/me returns
                                      identity only, remove
                                      orgRole from BffUserInfo,
                                      update BffUserInfoExtractor
                                      + tests]
                                          |
                                     [E350B Delete admin
                                      endpoints, BffSecurity,
                                      AdminProxyController,
                                      KeycloakAdminClient
                                      from gateway
                                      + compile verification]

FRONTEND TRACK (after E350A)
──────────────────────────────────────────────────────────

                                     [E350A ready]
                                          |
                                     [E351A Remove orgRole
                                      from AuthContext, update
                                      types.ts, keycloak-bff.ts,
                                      mock/server.ts, delete
                                      requireRole()
                                      + tests]
                                          |
                                     [E351B Migrate orgRole
                                      checks batch 1: settings,
                                      invoices, customers,
                                      projects pages/actions
                                      (~15 files)
                                      + test updates]
                                          |
                                     [E351C Migrate orgRole
                                      checks batch 2: team,
                                      dashboard, remaining
                                      pages/actions/components
                                      (~15 files)
                                      + test updates]
                                          |
                                     [E352A Update mock IDP
                                      token format, E2E
                                      fixtures, mock-login-form
                                      + E2E smoke tests]

CLEANUP TRACK (after E351)
──────────────────────────────────────────────────────────

                                     [E353A Remove ROLE_ORG_*
                                      authority grants from
                                      ClerkJwtAuthenticationConverter,
                                      remove Roles.AUTHORITY_*
                                      constants
                                      + tests]
                                          |
                                     [E353B Remove MemberSyncService
                                      role sync path, clean
                                      unused audit events
                                      + tests]
```

**Parallel opportunities**:
- E345A/B are sequential (entity before service).
- E346 and E349 can run in parallel after E345B (independent backend domains).
- E347A, E347B, E347C are sequential (incremental migration, must verify passing tests between batches).
- E348A and E348B can run in parallel after E347C (entity cleanup and class rename are independent).
- E350A/B are sequential (identity-only response before deleting admin endpoints).
- E351A/B/C are sequential (type change before usage migration).
- E353A/B are sequential (remove authorities before removing sync path).

---

## Implementation Order

### Stage 0: PendingInvitation Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 345 | 345A | `PendingInvitation` entity, V68 migration, repository, DTO records. ~5 new files. Backend only. | **Done** (PR #670) |
| 0b | 345 | 345B | `InvitationService` CRUD, `InvitationController` (POST, GET, DELETE), audit events, expiry validation. ~5 new files + 1 test file (~10 tests). Backend only. | **Done** (PR #670) |

### Stage 1: MemberFilter DB-Only + KeycloakAdminClient Move (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 346 | 346A | `MemberFilter`: remove `jwtHasExplicitRole` branch, role always from DB `orgRoleId`, add invitation lookup in `lazyCreateMember()`, grant `ROLE_ORG_*` from DB role in converter (backward compat). ~4 modified files (~8 tests). Backend only. | **Done** (PR #671) |
| 1b | 346 | 346B | `RequestScopes.requireOwner()`, cache eviction calls in `OrgRoleService.assignRole()` and `updateRole()`, `MemberRepository.findAllByOrgRoleId()`. ~4 modified files (~6 tests). Backend only. | **Done** (PR #671) |
| 1c (parallel with 1a) | 349 | 349A | Copy `KeycloakAdminClient` from gateway to backend `security/keycloak/`, add config properties, wire to `InvitationService`. ~4 new/modified files (~3 tests). Backend only. | |
| 1d | 349 | 349B | `POST /api/orgs` endpoint in new `OrgController`, move org creation logic from gateway, platform-admin guard. ~3 new files (~4 tests). Backend only. | |

### Stage 2: @PreAuthorize Migration (sequential batches)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 347 | 347A | Migrate `@PreAuthorize` → `@RequiresCapability` or remove on: `OrgSettingsController` (10), `DocumentTemplateController` (9+8 overlap), `FieldDefinitionController` (6), `FieldGroupController` (7), `GeneratedDocumentController` (4), `DocumentController` (9). ~6-8 modified controller files + test updates (~12 test adjustments). Backend only. | **Done** (PR #672) |
| 2b | 347 | 347B | Migrate on: `TaskController` (13), `TaskItemController` (6), `TimeEntryController` (5), `ProjectTimeSummaryController` (3), `ExpenseController` (6), `ProjectController` (8), `CalendarController` (1), `ProjectBudgetController` (4), `MyWorkController` (3). ~9 modified controller files + test updates (~12 test adjustments). Backend only. | **Done** (PR #673) |
| 2c | 347 | 347C | Migrate on: `BillingController` (2), `RetainerAgreementController` (7), `RetainerPeriodController` (3), `RetainerSummaryController` (2), `NotificationController` (5), `NotificationPreferenceController` (2), `DashboardController` (8), `CommentController` (4), `AuditEventController` (2), `ReportingController` (6), `ReportController` (3), `TagController` (3), `SavedViewController` (4), `ViewOnboardingController` (2), remaining controllers. ~14-16 modified controller files + test updates (~12 test adjustments). Backend only. | **Done** (PR #674) |

### Stage 3: Entity Cleanup (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 348 | 348A | V69 migration (backfill `org_role_id`, make NOT NULL, drop `org_role` VARCHAR), update `Member.java` entity (replace `orgRole` String with `@ManyToOne OrgRole orgRoleEntity`, add `getRoleSlug()`), update constructor and test factories. ~5 modified files (~8 test adjustments). Backend only. | **Done** (PR #676) |
| 3b (parallel) | 348 | 348B | Rename `ClerkJwtUtils` → `JwtUtils`, remove `extractOrgRole()`, `extractClerkClaim()`, `isClerkJwt()`, `isKeycloakFlatListFormat()`. Add `extractEmail()`. Update all imports (~8 files). ~9 modified files (~5 tests). Backend only. | **Done** (PR #676) |

### Stage 4: Gateway Strip

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 350 | 350A | `/bff/me` returns identity only: remove `orgRole` from `BffUserInfo`, update `BffUserInfoExtractor` to remove `extractOrgRole()` and `role` from `OrgInfo`. ~2 modified files (~3 tests). Gateway only. | |
| 4b | 350 | 350B | Delete `BffSecurity.java`, `AdminProxyController.java`, `KeycloakAdminClient.java` from gateway. Remove `createOrg()` from `BffController`. Clean unused config. ~4 deleted + 1 modified file (~2 tests). Gateway only. | |

### Stage 5: Frontend Migration (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 351 | 351A | Remove `orgRole` from `AuthContext` interface in `types.ts`, update `keycloak-bff.ts` (remove `orgRole` extraction, delete `requireRole()`), update `mock/server.ts` (parse Keycloak-only format). ~4 modified files (~6 tests). Frontend only. | |
| 5b | 351 | 351B | Migrate `orgRole` checks → `fetchMyCapabilities()` in: settings layout, invoices pages/actions (6 files), customers pages/actions (5 files), projects pages/actions (4 files). ~15 modified files (~5 test updates). Frontend only. | |
| 5c | 351 | 351C | Migrate remaining `orgRole` checks: team page/actions, dashboard, my-work, retainers, schedules, compliance, documents, billing, resource pages/actions, component files (`member-list.tsx`, `task-list-panel.tsx`, `expense-list.tsx`, etc.). ~15 modified files (~5 test updates). Frontend only. | |

### Stage 6: E2E & Mock IDP

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a | 352 | 352A | Update `compose/mock-idp/src/index.ts` to Keycloak-only token format (remove Clerk v2 `o.rol`), update `e2e/fixtures/auth.ts` (drop `orgRole` from `loginAs()`), update `mock-login-form.tsx`. ~3 modified files (~3 E2E smoke tests). Frontend/E2E. | |

### Stage 7: Cleanup

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a | 353 | 353A | Remove `ROLE_ORG_*` authority grants from `ClerkJwtAuthenticationConverter`, remove `Roles.AUTHORITY_ORG_OWNER/ADMIN/MEMBER` constants. ~2 modified files (~4 tests). Backend only. | |
| 7b | 353 | 353B | Remove `MemberSyncService` role sync path (webhook role sync code), clean unused role sync audit events. ~2 modified files (~3 tests). Backend only. | |

### Timeline

```
Stage 0: [345A] -> [345B]                                                     (sequential)
Stage 1: [346A] // [349A] -> [346B] -> [349B]                                 (346 + 349 parallel start)
Stage 2: [347A] -> [347B] -> [347C]                                            (sequential batches)
Stage 3: [348A] // [348B]                                                      (parallel after 347C)
Stage 4: [350A] -> [350B]                                                      (sequential, after 349B)
Stage 5: [351A] -> [351B] -> [351C]                                            (sequential, after 350A)
Stage 6: [352A]                                                                (after 351C)
Stage 7: [353A] -> [353B]                                                      (after 351C)
```

**Critical path**: 345A -> 345B -> 346A -> 346B -> 347A -> 347B -> 347C -> 348A -> 350A (requires 349B) -> 351A -> 351B -> 351C -> 353A -> 353B (15 slices sequential).

**Fastest path with parallelism**: 17 slices total. Stages 6 and 7 can run in parallel with each other after Stage 5. Stage 3 (2 slices) runs fully parallel. Stage 1 has partial parallelism (346A // 349A).

---

## Epic 345: PendingInvitation Entity & Invitation Service

**Goal**: Create the `PendingInvitation` entity, V68 Flyway migration, repository, DTO records, and the `InvitationService` with full CRUD and `InvitationController` endpoints.

**References**: Architecture doc Sections 11.2.1, 11.3.2, 11.4, 11.8.

**Dependencies**: None — greenfield entity in tenant schema.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **345A** | 345.1--345.5 | `PendingInvitation` entity, `InvitationStatus` enum (PENDING/ACCEPTED/EXPIRED/REVOKED), `PendingInvitationRepository`, DTO records, V68 tenant migration (1 new table + 2 indexes). ~5 new files. Backend only. | **Done** (PR #670) |
| **345B** | 345.6--345.12 | `InvitationService` (create, list, revoke, findPendingByEmail, markAccepted, expiry check), `InvitationController` (POST, GET, DELETE), audit events (INVITATION_CREATED, INVITATION_REVOKED), validation (duplicate email, member exists, role exists). ~5 new files + 1 test file (~10 tests). Backend only. | **Done** (PR #670) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 345.1 | Create `InvitationStatus` enum | 345A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationStatus.java`. Values: `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED`. Pattern: `backend/.../billingrun/BillingRunStatus.java` for enum conventions. |
| 345.2 | Create `PendingInvitation` entity | 345A | 345.1 | New file: `backend/.../invitation/PendingInvitation.java`. JPA entity mapped to `pending_invitations`. Fields: `id` (UUID PK), `email` (String 255, not null), `orgRole` (`@ManyToOne` lazy to `OrgRole`), `invitedBy` (`@ManyToOne` lazy to `Member`), `status` (String 20, not null, default PENDING), `expiresAt` (Instant, not null), `createdAt` (Instant), `acceptedAt` (Instant, nullable). Domain methods: `accept()`, `revoke()`, `isExpired()`. Pattern: `backend/.../orgrole/OrgRole.java` for entity conventions. |
| 345.3 | Create `PendingInvitationRepository` | 345A | 345.2 | New file: `backend/.../invitation/PendingInvitationRepository.java`. Extends `JpaRepository<PendingInvitation, UUID>`. Methods: `Optional<PendingInvitation> findByEmailAndStatus(String email, String status)`, `List<PendingInvitation> findAllByStatusOrderByCreatedAtDesc(String status)`, `List<PendingInvitation> findAllByOrderByCreatedAtDesc()`. Pattern: `backend/.../orgrole/OrgRoleRepository.java`. |
| 345.4 | Create invitation DTO records | 345A | | New file: `backend/.../invitation/dto/InvitationDtos.java`. Records: `CreateInvitationRequest(String email, UUID orgRoleId)`, `PendingInvitationResponse(UUID id, String email, String roleName, String roleSlug, String invitedByName, String status, Instant expiresAt, Instant createdAt, Instant acceptedAt)`. Pattern: `backend/.../orgrole/dto/OrgRoleDtos.java`. |
| 345.5 | Create V68 tenant migration | 345A | | New file: `backend/src/main/resources/db/migration/tenant/V68__pending_invitations.sql`. DDL: (1) CREATE TABLE `pending_invitations` (id UUID PK DEFAULT gen_random_uuid(), email VARCHAR(255) NOT NULL, org_role_id UUID NOT NULL REFERENCES org_roles(id), invited_by UUID NOT NULL REFERENCES members(id), status VARCHAR(20) NOT NULL DEFAULT 'PENDING', expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), accepted_at TIMESTAMPTZ). (2) CREATE UNIQUE INDEX `uq_pending_invitation_email_pending` ON pending_invitations(email) WHERE (status = 'PENDING'). (3) CREATE INDEX `idx_pending_invitations_email_status` ON pending_invitations(email, status). Pattern: `backend/.../db/migration/tenant/V67__create_org_role_tables.sql` for version numbering. |
| 345.6 | Create `InvitationService` core methods | 345B | 345A | New file: `backend/.../invitation/InvitationService.java`. `@Service`. Constructor injects `PendingInvitationRepository`, `OrgRoleRepository`, `MemberRepository`, `AuditService`. Methods: `createInvitation(CreateInvitationRequest, UUID invitedByMemberId)` — validates role exists, email not already a member, no existing PENDING invitation for email; creates with 7-day expiry. `findPendingByEmail(String email)` — returns Optional, checks not expired. `markAccepted(UUID invitationId)` — sets status=ACCEPTED, acceptedAt=now(). |
| 345.7 | Add list and revoke methods to `InvitationService` | 345B | 345.6 | Modify `InvitationService`: add `listInvitations(String statusFilter)` — returns all or filtered by status. `revokeInvitation(UUID invitationId)` — validates status is PENDING, sets status=REVOKED, emits INVITATION_REVOKED audit event. |
| 345.8 | Create `InvitationController` | 345B | 345.7 | New file: `backend/.../invitation/InvitationController.java`. `@RestController @RequestMapping("/api/invitations")`. Endpoints: `POST /` with `@RequiresCapability("TEAM_OVERSIGHT")`, `GET /` with `@RequiresCapability("TEAM_OVERSIGHT")` + optional `?status=` query param, `DELETE /{id}` with `@RequiresCapability("TEAM_OVERSIGHT")`. Pure delegation to `InvitationService`. Pattern: `backend/.../orgrole/OrgRoleController.java`. |
| 345.9 | Add audit events for invitations | 345B | 345.8 | Modify `InvitationService`: emit `AuditEvent` with type `INVITATION_CREATED` on create (details: email, role name, invited by), `INVITATION_REVOKED` on revoke (details: email, revoked by). Use `AuditEventBuilder`. Pattern: existing audit events in `OrgRoleService`. |
| 345.10 | Add expiry validation | 345B | 345.6 | Modify `InvitationService.findPendingByEmail()`: if invitation exists but `expiresAt` is past, update status to EXPIRED and return empty. Add `isExpired()` domain method on `PendingInvitation` entity. |
| 345.11 | Write integration tests for InvitationService | 345B | 345.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationServiceTest.java`. Tests (~5): (1) `createInvitation_validRequest_createsPending`; (2) `createInvitation_duplicateEmail_throws409`; (3) `createInvitation_existingMember_throws409`; (4) `revokeInvitation_pending_setsRevoked`; (5) `findPendingByEmail_expired_returnsEmpty`. Use `@SpringBootTest @Import(TestcontainersConfiguration.class)`. |
| 345.12 | Write integration tests for InvitationController | 345B | 345.11 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationControllerTest.java`. Tests (~5): (1) `createInvitation_admin_returns201`; (2) `createInvitation_member_returns403`; (3) `listInvitations_admin_returnsList`; (4) `revokeInvitation_admin_returns204`; (5) `revokeInvitation_alreadyAccepted_returns400`. Use MockMvc with JWT mocks. |

### Key Files

**Slice 345A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitation.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitationRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/dto/InvitationDtos.java`
- `backend/src/main/resources/db/migration/tenant/V68__pending_invitations.sql`

**Slice 345A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRole.java` — entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunStatus.java` — enum pattern
- `backend/src/main/resources/db/migration/tenant/V67__create_org_role_tables.sql` — latest migration

**Slice 345B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationControllerTest.java`

**Slice 345B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleController.java` — controller pattern with `@RequiresCapability`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` — service + audit event pattern

### Architecture Decisions

- **Tenant-scoped entity**: `PendingInvitation` lives in the tenant schema. No `@FilterDef`/`@Filter` needed (dedicated schema handles isolation per Phase 13).
- **Partial unique index**: `UNIQUE(email) WHERE (status = 'PENDING')` — only one active invitation per email per tenant. Historical records preserved.
- **Status lifecycle**: PENDING -> ACCEPTED/EXPIRED/REVOKED. No soft deletes. Expired status set lazily on query, not by a scheduled job.
- **7-day expiry default**: Hardcoded in `InvitationService`. Configurable per `OrgSettings` would be a future enhancement.

---

## Epic 346: MemberFilter DB-Only Role Resolution

**Goal**: Make the `MemberFilter` resolve roles exclusively from the DB `org_role_id` FK, eliminating the JWT-based role preference branch. Add invitation lookup during lazy member creation. Add `RequestScopes.requireOwner()` convenience method and cache eviction on role mutations.

**References**: Architecture doc Sections 11.3.1, 11.3.2, 11.3.4.

**Dependencies**: Epic 345 (InvitationService for lazy-create lookup).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **346A** | 346.1--346.6 | `MemberFilter`: remove `jwtHasExplicitRole` branch (and `isKeycloakFlatListFormat()` call from `ClerkJwtUtils`), role always from DB `orgRoleId`. Inject `InvitationService`, add pending invitation lookup in `lazyCreateMember()`. Update `ClerkJwtAuthenticationConverter` to grant `ROLE_ORG_*` from DB role (backward compat during migration). ~4 modified files (~8 tests). Backend only. | **Done** (PR #671) |
| **346B** | 346.7--346.12 | `RequestScopes.requireOwner()` convenience method. Add cache eviction calls in `OrgRoleService.assignRole()` and `updateRole()`. Add `MemberRepository.findAllByOrgRoleId()` for bulk eviction. ~4 modified files (~6 tests). Backend only. | **Done** (PR #671) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 346.1 | Remove `jwtHasExplicitRole` branch from `MemberFilter` | 346A | 345B | Modify: `backend/.../member/MemberFilter.java`. In `resolveMember()`, remove the `if (jwtHasExplicitRole)` branch that prefers JWT `org_role` claim over DB `orgRoleId`. Role always comes from `member.getOrgRoleId()` -> `OrgRole` entity -> `slug`. Also remove the call to `ClerkJwtUtils.isKeycloakFlatListFormat()` (its only usage). |
| 346.2 | Add invitation lookup in `lazyCreateMember()` | 346A | 346.1 | Modify: `backend/.../member/MemberFilter.java`. Inject `InvitationService`. In `lazyCreateMember()`: extract email from JWT (via `ClerkJwtUtils.extractEmail()` or `jwt.getClaimAsString("email")`), call `invitationService.findPendingByEmail(email)`. If found and not expired: use invitation's `orgRole` for new member, call `invitationService.markAccepted(id)`. If not found: default to system "member" role. If first member in empty tenant: promote to "owner" (existing founding user logic). |
| 346.3 | Grant `ROLE_ORG_*` from DB role in converter | 346A | 346.1 | Modify: `backend/.../security/ClerkJwtAuthenticationConverter.java`. Instead of mapping JWT `org_role` claim to `ROLE_ORG_*` authority, the converter now grants `ROLE_ORG_*` based on the DB-stored role. This requires access to the member info. During this backward-compat phase, always grant `ROLE_ORG_MEMBER` as baseline (MemberFilter guarantees membership), and add `ROLE_ORG_ADMIN`/`ROLE_ORG_OWNER` based on `RequestScopes.ORG_ROLE` if bound. This keeps `@PreAuthorize` annotations working until Epic 347 migrates them. |
| 346.4 | Remove `ClerkJwtUtils.isKeycloakFlatListFormat()` | 346A | 346.1 | Modify: `backend/.../security/ClerkJwtUtils.java`. Delete `isKeycloakFlatListFormat()` method — its only caller was the `jwtHasExplicitRole` branch in `MemberFilter` which was removed in 346.1. |
| 346.5 | Write integration test: role from DB only | 346A | 346.3 | New/modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/member/MemberFilterIntegrationTest.java`. Tests (~4): (1) `resolveMember_jwtWithOrgRole_ignoresJwtUsesDb`; (2) `lazyCreateMember_withPendingInvitation_usesInvitationRole`; (3) `lazyCreateMember_noPendingInvitation_usesDefaultMemberRole`; (4) `lazyCreateMember_firstMember_promotesToOwner`. |
| 346.6 | Write integration test: invitation acceptance flow | 346A | 346.5 | Modify test file from 346.5. Tests (~4): (5) `lazyCreateMember_expiredInvitation_usesDefaultRole`; (6) `lazyCreateMember_revokedInvitation_usesDefaultRole`; (7) `lazyCreateMember_acceptedInvitation_marksAccepted`; (8) `lazyCreateMember_invitationWithCustomRole_assignsCustomRole`. |
| 346.7 | Add `requireOwner()` to `RequestScopes` | 346B | 346A | Modify: `backend/.../multitenancy/RequestScopes.java`. Add `public static void requireOwner() { if (!"owner".equals(getOrgRole())) { throw new ForbiddenException("Only the organization owner can perform this action"); } }`. |
| 346.8 | Add cache eviction in `OrgRoleService.assignRole()` | 346B | 346.7 | Modify: `backend/.../orgrole/OrgRoleService.java`. In `assignRole(memberId, orgRoleId, overrides)`: after DB update, call `memberFilter.evictFromCache(tenantId, member.clerkUserId)`. Inject `MemberFilter` for cache access. |
| 346.9 | Add bulk cache eviction in `OrgRoleService.updateRole()` | 346B | 346.8 | Modify: `backend/.../orgrole/OrgRoleService.java`. In `updateRole(roleId, capabilities)`: after DB update, call `memberRepository.findAllByOrgRoleId(roleId)` -> evict each member from cache. |
| 346.10 | Add `findAllByOrgRoleId` to `MemberRepository` | 346B | | Modify: `backend/.../member/MemberRepository.java`. Add `List<Member> findAllByOrgRoleId(UUID orgRoleId)`. Note: `findByOrgRoleId` may already exist from Phase 41 (312.13) — verify and use existing if present. |
| 346.11 | Write test: cache eviction on role assignment | 346B | 346.10 | Modify existing `OrgRoleServiceTest.java`. Tests (~3): (1) `assignRole_evictsMemberFromCache`; (2) `updateRole_evictsAllMembersWithRole`; (3) `requireOwner_nonOwner_throwsForbidden`. |
| 346.12 | Write test: requireOwner behavior | 346B | 346.7 | Modify test. Tests (~3): (4) `requireOwner_owner_noException`; (5) `requireOwner_admin_throwsForbidden`; (6) `requireOwner_member_throwsForbidden`. |

### Key Files

**Slice 346A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` — remove JWT role preference, add invitation lookup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtAuthenticationConverter.java` — grant ROLE_ORG_* from DB
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` — remove `isKeycloakFlatListFormat()`

**Slice 346A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationService.java` — findPendingByEmail()
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` — resolveCapabilities()

**Slice 346B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — add requireOwner()
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` — add cache eviction
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` — add findAllByOrgRoleId

### Architecture Decisions

- **Backward-compat ROLE_ORG_* grants**: During the migration period (Epics 346-347), `ClerkJwtAuthenticationConverter` still grants `ROLE_ORG_*` authorities from the DB role so existing `@PreAuthorize` annotations continue to work. These are removed in Epic 353 after all annotations are migrated.
- **Single-member vs. bulk eviction**: `assignRole()` evicts one member (common operation). `updateRole()` evicts all members of that role (rare admin operation). Both are acceptable because the cache has a 1-hour TTL safety net.
- **Invitation lookup cost**: The `findPendingByEmail()` query runs only during `lazyCreateMember()` (first authentication), not on subsequent requests. The partial index on `(email) WHERE status = 'PENDING'` ensures fast lookup.

---

## Epic 347: @PreAuthorize Migration (Remaining Controllers)

**Goal**: Replace all remaining `@PreAuthorize` annotations with `@RequiresCapability` or remove them (where `MemberFilter` is the implicit gate). This epic handles the ~185 remaining `@PreAuthorize` annotations across ~42 controller files that were not migrated in Phase 41 (Epics 314-315).

**References**: Architecture doc Section 11.3.3, 11.9.

**Dependencies**: Epic 346 (DB-only role resolution + backward-compat authority grants).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **347A** | 347.1--347.3 | Migrate `@PreAuthorize` on settings, template, document, and field definition controllers (~70 annotations across ~8 files). Replace `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` with appropriate `@RequiresCapability`. Remove `hasAnyRole('ORG_MEMBER', ...)` (redundant with MemberFilter). Replace `hasRole('ORG_OWNER')` with `RequestScopes.requireOwner()` call in service. + test updates (~12 adjustments). Backend only. | **Done** (PR #672) |
| **347B** | 347.4--347.6 | Migrate `@PreAuthorize` on task, time entry, expense, project, calendar, and budget controllers (~60 annotations across ~10 files). + test updates (~12 adjustments). Backend only. | **Done** (PR #673) |
| **347C** | 347.7--347.9 | Migrate `@PreAuthorize` on billing, retainer, notification, dashboard, comment, audit, reporting, tag, view, and remaining controllers (~55 annotations across ~16 files). + test updates (~12 adjustments). Backend only. | **Done** (PR #674) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 347.1 | Migrate settings & template controllers | 347A | 346B | Modify: `OrgSettingsController.java` (10 annotations), `DocumentTemplateController.java` (17 annotations — 9 admin + 8 member), `GeneratedDocumentController.java` (4 annotations). Pattern mapping: `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` -> `@RequiresCapability("TEAM_OVERSIGHT")` for admin writes. `hasAnyRole('ORG_MEMBER', ...)` -> remove annotation. `hasRole('ORG_OWNER')` -> move check to service via `RequestScopes.requireOwner()`. |
| 347.2 | Migrate field definition & document controllers | 347A | 347.1 | Modify: `FieldDefinitionController.java` (6 annotations), `FieldGroupController.java` (7 annotations), `DocumentController.java` (9 annotations). Same pattern mapping. For field definitions: admin writes map to `@RequiresCapability("TEAM_OVERSIGHT")`. For documents: member reads -> remove annotation, admin writes -> `@RequiresCapability("PROJECT_MANAGEMENT")`. |
| 347.3 | Update test JWT mocks for batch 1 | 347A | 347.2 | Update integration tests for all controllers modified in 347.1-347.2. Replace `.authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")))` with JWT mocks that set up appropriate capabilities in `RequestScopes.CAPABILITIES`. ~12 test file adjustments. |
| 347.4 | Migrate task, time entry & expense controllers | 347B | 347A | Modify: `TaskController.java` (13 annotations), `TaskItemController.java` (6 annotations), `TimeEntryController.java` (5 annotations), `ProjectTimeSummaryController.java` (3 annotations), `ExpenseController.java` (6 annotations). Task/time member reads -> remove. Admin writes -> `@RequiresCapability("PROJECT_MANAGEMENT")`. Expense approval -> `@RequiresCapability("FINANCIAL_VISIBILITY")`. |
| 347.5 | Migrate project, calendar & budget controllers | 347B | 347.4 | Modify: `ProjectController.java` (8 annotations), `CalendarController.java` (1), `ProjectBudgetController.java` (4), `MyWorkController.java` (3). Project creates -> `@RequiresCapability("PROJECT_MANAGEMENT")`. Project deletes -> `RequestScopes.requireOwner()` in service. Budget writes -> `@RequiresCapability("FINANCIAL_VISIBILITY")`. |
| 347.6 | Update test JWT mocks for batch 2 | 347B | 347.5 | Update integration tests for all controllers modified in 347.4-347.5. ~12 test file adjustments. |
| 347.7 | Migrate billing, retainer & notification controllers | 347C | 347B | Modify: `BillingController.java` (2), `RetainerAgreementController.java` (7), `RetainerPeriodController.java` (3), `RetainerSummaryController.java` (2), `NotificationController.java` (5), `NotificationPreferenceController.java` (2). Billing -> `@RequiresCapability("INVOICING")` or `RequestScopes.requireOwner()`. Retainer writes -> `@RequiresCapability("FINANCIAL_VISIBILITY")`. Notification -> member reads -> remove. |
| 347.8 | Migrate dashboard, comment, audit, reporting & remaining controllers | 347C | 347.7 | Modify: `DashboardController.java` (8), `CommentController.java` (4), `AuditEventController.java` (2), `ReportingController.java` (6), `ReportController.java` (3), `TagController.java` (3), `SavedViewController.java` (4), `ProposalController.java` (4), `ProjectTemplateController.java` (5), `RecurringScheduleController.java` (3), `BillingRateController.java` (5), `OnboardingController.java` (2), `OrgMemberController.java` (2), `ProjectMemberController.java` (4), any remaining controllers. Dashboard reads -> remove. Audit reads -> `@RequiresCapability("TEAM_OVERSIGHT")`. |
| 347.9 | Update test JWT mocks for batch 3 | 347C | 347.8 | Update integration tests for all controllers modified in 347.7-347.8. ~12 test file adjustments. Verify all 830+ existing tests pass after migration is complete. |

### Key Files

**Slice 347A — Modify:**
- `backend/.../settings/OrgSettingsController.java`
- `backend/.../template/DocumentTemplateController.java`
- `backend/.../template/GeneratedDocumentController.java`
- `backend/.../fielddefinition/FieldDefinitionController.java`
- `backend/.../fielddefinition/FieldGroupController.java`
- `backend/.../document/DocumentController.java`

**Slice 347B — Modify:**
- `backend/.../task/TaskController.java`
- `backend/.../task/TaskItemController.java`
- `backend/.../timeentry/TimeEntryController.java`
- `backend/.../timeentry/ProjectTimeSummaryController.java`
- `backend/.../expense/ExpenseController.java`
- `backend/.../project/ProjectController.java`
- `backend/.../budget/ProjectBudgetController.java`

**Slice 347C — Modify:**
- `backend/.../billing/BillingController.java`
- `backend/.../retainer/RetainerAgreementController.java`
- `backend/.../dashboard/DashboardController.java`
- `backend/.../reporting/ReportingController.java`
- Plus ~10 additional controllers

**All Slices — Read for context:**
- `backend/.../orgrole/RequiresCapability.java` — annotation to use
- `backend/.../orgrole/Capability.java` — valid capability values

### Architecture Decisions

- **Three-pattern mapping**: (1) `hasAnyRole('ORG_MEMBER', ...)` -> remove (MemberFilter is implicit gate). (2) `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` -> `@RequiresCapability("CAPABILITY")` with appropriate capability. (3) `hasRole('ORG_OWNER')` -> `RequestScopes.requireOwner()` in service layer (~5 endpoints: delete org role, transfer ownership, manage billing, delete organization).
- **Capability mapping**: Admin actions map to the most relevant capability: `TEAM_OVERSIGHT` for member/role management, `PROJECT_MANAGEMENT` for project CRUD, `FINANCIAL_VISIBILITY` for financial data, `INVOICING` for invoice operations, `CUSTOMER_MANAGEMENT` for customer operations.
- **Sequential batches**: Each batch must be verified by running full test suite before proceeding. A missed annotation surfaces as a 403 in tests.

---

## Epic 348: Member Entity Cleanup & JwtUtils Rename

**Goal**: Drop the legacy `members.org_role` VARCHAR column (making `org_role_id` the only role reference), update the `Member` entity to use `@ManyToOne` for `orgRoleEntity`, and rename `ClerkJwtUtils` to `JwtUtils` removing all Clerk v2 format code.

**References**: Architecture doc Sections 11.2.2, 11.2.3, 11.8.

**Dependencies**: Epic 347 (all `@PreAuthorize` annotations migrated — no code reads `member.getOrgRole()` string anymore).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **348A** | 348.1--348.5 | V69 migration (backfill org_role_id, make NOT NULL, drop org_role VARCHAR). Update `Member.java`: replace `orgRole` String field with `@ManyToOne OrgRole orgRoleEntity`, add `getRoleSlug()`, update constructor. Update `MemberFilter`, `MemberSyncService`, test factories. ~5 modified files (~8 test adjustments). Backend only. | **Done** (PR #676) |
| **348B** | 348.6--348.10 | Rename `ClerkJwtUtils.java` -> `JwtUtils.java`. Remove `extractClerkClaim()`, `extractOrgRole()`, `isClerkJwt()`. Add `extractEmail()`. Update all imports (~8 files). ~9 modified files (~5 tests). Backend only. | **Done** (PR #676) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 348.1 | Create V69 migration | 348A | 347C | New file: `backend/src/main/resources/db/migration/tenant/V69__member_org_role_cleanup.sql`. (1) UPDATE members SET org_role_id = (SELECT id FROM org_roles WHERE slug = org_role AND is_system = true LIMIT 1) WHERE org_role_id IS NULL; (2) ALTER TABLE members ALTER COLUMN org_role_id SET NOT NULL; (3) ALTER TABLE members DROP COLUMN org_role. Pattern: see architecture doc Section 11.8. |
| 348.2 | Update `Member` entity — replace orgRole with orgRoleEntity | 348A | 348.1 | Modify: `backend/.../member/Member.java`. Remove `@Column(name = "org_role") private String orgRole`. Replace `@Column(name = "org_role_id") private UUID orgRoleId` with `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "org_role_id", nullable = false) private OrgRole orgRoleEntity`. Add `public String getRoleSlug() { return orgRoleEntity.getSlug(); }`. Update constructor to accept `OrgRole orgRoleEntity` instead of `String orgRole`. |
| 348.3 | Update `MemberFilter` for entity change | 348A | 348.2 | Modify: `backend/.../member/MemberFilter.java`. In `lazyCreateMember()`: change `new Member(..., effectiveRole)` to `new Member(..., orgRoleEntity)`. Replace `member.setOrgRoleId(uuid)` with `member.setOrgRoleEntity(orgRole)`. In `resolveMember()`: change `m.getOrgRole()` to `m.getRoleSlug()` for binding to `RequestScopes.ORG_ROLE`. |
| 348.4 | Update `MemberSyncService` and other callers | 348A | 348.3 | Modify any code that sets `member.setOrgRole(String)` or reads `member.getOrgRole()`. Check `MemberSyncService`, `OrgRoleService`, test helpers. Replace with `orgRoleEntity` getter/setter. |
| 348.5 | Update test factories and verify tests pass | 348A | 348.4 | Modify test factories and test helpers that construct `Member` instances. Update `TestMemberFactory` if it exists, or update inline member creation in tests. Run full test suite to verify no `getOrgRole()` calls remain. ~8 test file adjustments. |
| 348.6 | Rename `ClerkJwtUtils` to `JwtUtils` | 348B | 347C | Rename: `backend/.../security/ClerkJwtUtils.java` -> `backend/.../security/JwtUtils.java`. Update class name and all internal references. |
| 348.7 | Remove Clerk v2 format methods | 348B | 348.6 | Modify `JwtUtils.java`: delete `extractClerkClaim()`, `extractOrgRole()`, `isClerkJwt()`. These are dead code — Clerk was removed in Phase 20 and the JWT-based role extraction was removed in Epic 346. Note: `isKeycloakFlatListFormat()` was already removed in 346.4. |
| 348.8 | Add `extractEmail()` method | 348B | 348.7 | Modify `JwtUtils.java`: add `public static String extractEmail(Jwt jwt) { return jwt.getClaimAsString("email"); }`. Currently inlined in `MemberFilter`. |
| 348.9 | Update all imports to `JwtUtils` | 348B | 348.6 | Update imports in: `MemberFilter.java`, `TenantFilter.java`, `ClerkJwtAuthenticationConverter.java`, `PlatformAdminFilter.java`, and any test files that reference `ClerkJwtUtils`. Grep for `ClerkJwtUtils` across the codebase. ~8 files. |
| 348.10 | Write tests verifying JwtUtils works | 348B | 348.9 | Modify/create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/security/JwtUtilsTest.java`. Tests (~5): (1) `extractOrgId_keycloakFormat_returnsOrgId`; (2) `extractOrgSlug_keycloakFormat_returnsSlug`; (3) `extractEmail_returnsEmail`; (4) `extractGroups_returnsGroups`; (5) `extractOrgRole_methodDoesNotExist` (verify removed). Remove any Clerk v2 format tests. |

### Key Files

**Slice 348A — Create:**
- `backend/src/main/resources/db/migration/tenant/V69__member_org_role_cleanup.sql`

**Slice 348A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java`

**Slice 348B — Rename:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` -> `JwtUtils.java`

**Slice 348B — Modify:**
- `backend/.../member/MemberFilter.java` — import update
- `backend/.../multitenancy/TenantFilter.java` — import update (if used)
- `backend/.../security/ClerkJwtAuthenticationConverter.java` — import update
- `backend/.../security/PlatformAdminFilter.java` — import update

### Architecture Decisions

- **Destructive migration V69**: Drops `org_role` column permanently. The backfill step ensures no data is lost. This migration runs ONLY after all code paths use `org_role_id` exclusively (ensured by Epic 347 completion).
- **`@ManyToOne` replaces UUID FK**: The `Member.orgRoleEntity` field uses `@ManyToOne` lazy fetch. This is a project convention change from the raw UUID FK pattern used in Phase 41 (312.3). The `@ManyToOne` provides type-safe access to the `OrgRole` entity and avoids manual `orgRoleRepository.findById()` calls.
- **Class rename over alias**: `ClerkJwtUtils` is renamed, not aliased. A deprecated wrapper would add unnecessary indirection.

---

## Epic 349: KeycloakAdminClient Backend Move & Org Endpoint

**Goal**: Copy `KeycloakAdminClient` from the gateway to the backend `security/keycloak/` package and wire it to `InvitationService`. Add `POST /api/orgs` endpoint to replace the gateway's `POST /bff/orgs`.

**References**: Architecture doc Sections 11.6, 11.4.

**Dependencies**: Epic 345 (InvitationService exists to wire into).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **349A** | 349.1--349.4 | Copy `KeycloakAdminClient` to backend `security/keycloak/` package, add Keycloak Admin REST API config properties, wire `inviteUser()` to `InvitationService.createInvitation()`. ~4 new/modified files (~3 tests). Backend only. | |
| **349B** | 349.5--349.8 | New `OrgController` with `POST /api/orgs` endpoint, move org creation logic from gateway `BffController.createOrg()`, platform-admin guard via `RequestScopes.isPlatformAdmin()`. ~3 new files (~4 tests). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 349.1 | Copy `KeycloakAdminClient` to backend | 349A | 345B | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java`. Copy from `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`. Update package declaration. Adjust imports as needed. Key methods: `inviteUser(String email, String orgSlug)`, `createOrganization(String name)`. Pattern: read existing gateway implementation for API calls. |
| 349.2 | Add Keycloak Admin config properties | 349A | 349.1 | Modify: `backend/src/main/resources/application.yml` (or add `application-keycloak.yml`). Add properties: `keycloak.admin.server-url`, `keycloak.admin.realm`, `keycloak.admin.client-id`, `keycloak.admin.client-secret`. Create config class: `backend/.../security/keycloak/KeycloakAdminConfig.java` with `@ConfigurationProperties`. |
| 349.3 | Wire `KeycloakAdminClient` to `InvitationService` | 349A | 349.2 | Modify: `backend/.../invitation/InvitationService.java`. Inject `KeycloakAdminClient`. In `createInvitation()`: after saving `PendingInvitation`, call `keycloakAdminClient.inviteUser(email, orgSlug)` to trigger Keycloak invitation email. Handle failure gracefully (log error, don't roll back the DB record). |
| 349.4 | Write integration test for KC invite | 349A | 349.3 | Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationServiceTest.java`. Add test (~3): (1) `createInvitation_callsKeycloakInvite`; (2) `createInvitation_keycloakFails_invitationStillCreated`; (3) verify `KeycloakAdminClient` is called with correct email and orgSlug. Use `@MockBean` for `KeycloakAdminClient` in tests. |
| 349.5 | Create `OrgController` | 349B | 349A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/OrgController.java`. `@RestController @RequestMapping("/api/orgs")`. Endpoint: `POST /` — accepts `{ name: String }`, calls `OrgProvisioningService.createOrg(name)`. Guard: check `RequestScopes.isPlatformAdmin()` (or equivalent `PlatformAdminFilter` check). Returns `{ orgId, slug }`. |
| 349.6 | Create `OrgProvisioningService` | 349B | 349.5 | New file (or modify existing): `backend/.../provisioning/OrgProvisioningService.java`. Method `createOrg(String name)`: calls `keycloakAdminClient.createOrganization(name)` -> gets org ID and slug -> provisions tenant schema (reuse existing provisioning logic). Pattern: existing `TenantProvisioningService`. |
| 349.7 | Write integration tests for `OrgController` | 349B | 349.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/OrgControllerTest.java`. Tests (~4): (1) `createOrg_platformAdmin_returns201`; (2) `createOrg_regularMember_returns403`; (3) `createOrg_missingName_returns400`; (4) `createOrg_duplicateName_returns409`. |
| 349.8 | Verify gateway `POST /bff/orgs` still works (temporary) | 349B | 349.7 | Manual verification: both `POST /bff/orgs` (gateway) and `POST /api/orgs` (backend) work. The gateway endpoint will be removed in Epic 350. No code change needed. |

### Key Files

**Slice 349A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminConfig.java`

**Slice 349A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationService.java` — wire KC client

**Slice 349A — Read for context:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java` — source to copy

**Slice 349B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/OrgController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/OrgControllerTest.java`

**Slice 349B — Read for context:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java` — createOrg() logic to replicate
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/` — existing provisioning logic

### Architecture Decisions

- **Copy, not move**: `KeycloakAdminClient` is copied to the backend while the gateway version remains. The gateway version is deleted in Epic 350B. This avoids a breaking transition period.
- **Graceful Keycloak failure**: If the Keycloak invite API call fails, the `PendingInvitation` DB record is still created. The admin can retry or the invitation email can be resent. This prevents lost invitations due to transient KC issues.
- **Platform-admin guard**: `POST /api/orgs` uses the existing `PlatformAdminFilter` / `RequestScopes.isPlatformAdmin()` check. Only platform admins (Keycloak `platform-admins` group) can create new organizations.

---

## Epic 350: Gateway Authorization Removal

**Goal**: Strip all authorization responsibilities from the gateway, making it a pure authentication + proxy layer. Remove `orgRole` from `/bff/me` response, delete admin endpoints, delete `BffSecurity`, and delete the gateway's `KeycloakAdminClient`.

**References**: Architecture doc Section 11.6, ADR-180.

**Dependencies**: Epic 349 (backend has replacement endpoints before gateway ones are removed).

**Scope**: Gateway

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **350A** | 350.1--350.3 | `/bff/me` returns identity only: remove `orgRole` from `BffUserInfo` record, update `BffUserInfoExtractor` to remove `extractOrgRole()` and `role` from `OrgInfo`. ~2 modified files (~3 tests). Gateway only. | |
| **350B** | 350.4--350.7 | Delete `BffSecurity.java`, `AdminProxyController.java`, `KeycloakAdminClient.java` from gateway. Remove `createOrg()` from `BffController`. Clean unused dependencies. ~4 deleted + 1 modified file (~2 tests). Gateway only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 350.1 | Remove `orgRole` from `BffUserInfo` | 350A | 349B | Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`. Change `BffUserInfo` record to remove `orgRole` field. Update the factory method that constructs the response. |
| 350.2 | Update `BffUserInfoExtractor` | 350A | 350.1 | Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffUserInfoExtractor.java`. Remove `extractOrgRole()` method. Remove `role` field from `OrgInfo` record. Update `extractUserInfo()` to not extract or include role. |
| 350.3 | Write test: /bff/me response shape | 350A | 350.2 | Modify gateway tests. Tests (~3): (1) `/bff/me` response contains `authenticated`, `userId`, `email`, `orgId`, `orgSlug`, `groups` but NOT `orgRole`; (2) `/bff/me` unauthenticated returns `{authenticated: false}`; (3) verify JSON schema matches new shape. |
| 350.4 | Delete `BffSecurity.java` | 350B | 350A | Delete: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffSecurity.java`. This was the `@PreAuthorize` helper for OIDC role checks in the gateway. |
| 350.5 | Delete `AdminProxyController.java` | 350B | 350.4 | Delete: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/AdminProxyController.java`. Its `/bff/admin/invite` endpoint is replaced by `POST /api/invitations` in backend. |
| 350.6 | Remove `createOrg()` from `BffController` | 350B | 350.5 | Modify: `gateway/.../controller/BffController.java`. Remove the `POST /bff/orgs` endpoint method and `KeycloakAdminClient` injection. The endpoint is replaced by `POST /api/orgs` in backend. |
| 350.7 | Delete `KeycloakAdminClient` from gateway | 350B | 350.6 | Delete: `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`. Remove Keycloak Admin REST API dependency from gateway `pom.xml` / build config if applicable. Verify gateway compiles and starts without the deleted files. |

### Key Files

**Slice 350A — Modify:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffUserInfoExtractor.java`

**Slice 350B — Delete:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffSecurity.java`
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/AdminProxyController.java`
- `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`

**Slice 350B — Modify:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java` — remove createOrg()

### Architecture Decisions

- **Identity-only gateway**: After this epic, the gateway handles ONLY: (1) OAuth2 login/logout, (2) SESSION cookie management, (3) TokenRelay to backend, (4) `/bff/me` for identity, (5) CSRF/CORS. No authorization decisions.
- **350A before 350B**: The `/bff/me` shape change (350A) must deploy before deleting admin endpoints (350B). Frontend depends on the shape change to know orgRole is gone.
- **No breaking change for authenticated users**: Users continue to get identity data from `/bff/me`. Authorization data comes from `/api/me/capabilities` (already exists from Phase 41).

---

## Epic 351: Frontend Capabilities-Only Authorization

**Goal**: Remove `orgRole` from the `AuthContext` type and all frontend consumers. Replace all `orgRole`-based admin checks with the capabilities API (`fetchMyCapabilities()`). Update auth providers to parse identity-only responses.

**References**: Architecture doc Section 11.7.

**Dependencies**: Epic 350A (gateway no longer returns `orgRole` in `/bff/me`).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **351A** | 351.1--351.5 | Remove `orgRole` from `AuthContext` interface, update `keycloak-bff.ts` (remove extraction, delete `requireRole()`), update `mock/server.ts` (Keycloak-only format), update `types.ts`. ~4 modified files (~6 tests). Frontend only. | |
| **351B** | 351.6--351.9 | Migrate `orgRole` checks to capabilities in settings, invoices, customers, projects pages/actions (~15 files). + test updates. Frontend only. | |
| **351C** | 351.10--351.13 | Migrate remaining `orgRole` checks in team, dashboard, retainers, schedules, compliance, documents, my-work, billing, resources, and component files (~15 files). + test updates. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 351.1 | Remove `orgRole` from `AuthContext` | 351A | 350A | Modify: `frontend/lib/auth/types.ts`. Remove `orgRole: string` from `AuthContext` interface. This is a breaking type change — TypeScript compiler will flag all consumers. |
| 351.2 | Update `keycloak-bff.ts` provider | 351A | 351.1 | Modify: `frontend/lib/auth/providers/keycloak-bff.ts`. In `getAuthContext()`: remove `orgRole` extraction from `/bff/me` response. Delete `requireRole()` function entirely. |
| 351.3 | Update `mock/server.ts` provider | 351A | 351.1 | Modify: `frontend/lib/auth/providers/mock/server.ts`. Parse mock IDP token without `o.rol` field (Clerk v2 format removed). Return identity-only `AuthContext`. |
| 351.4 | Update auth type tests | 351A | 351.3 | Modify: `frontend/__tests__/lib/auth/types.test.ts`, `frontend/__tests__/lib/auth/keycloak-bff-provider.test.ts`, `frontend/__tests__/lib/auth/mock-server-provider.test.ts`. Remove `orgRole` from test expectations and mock data. ~3 test files (~6 test adjustments). |
| 351.5 | Update `lib/types/member.ts` | 351A | 351.1 | Modify: `frontend/lib/types/member.ts`. If `orgRole` is referenced in member type definitions, update accordingly. Check for any type re-exports that include `orgRole`. |
| 351.6 | Migrate settings pages/actions | 351B | 351A | Modify: `frontend/app/(app)/org/[slug]/settings/layout.tsx`, `frontend/app/(app)/org/[slug]/settings/*/page.tsx` and `actions.ts` files that reference `orgRole`. Pattern: replace `const { orgRole } = await getAuthContext(); const isAdmin = orgRole === "org:admin" \|\| orgRole === "org:owner"` with `const caps = await fetchMyCapabilities(); const isAdmin = caps.isAdmin`. ~6 files. |
| 351.7 | Migrate invoices pages/actions | 351B | 351.6 | Modify: `frontend/app/(app)/org/[slug]/invoices/` — `invoice-crud-actions.ts`, `invoice-payment-actions.ts`, `billing-runs/` actions and pages, `[id]/page.tsx`. ~6 files. |
| 351.8 | Migrate customers and projects pages/actions | 351B | 351.7 | Modify: `frontend/app/(app)/org/[slug]/customers/` — `page.tsx`, `actions.ts`, `[id]/page.tsx`, `[id]/actions.ts`, `[id]/lifecycle-actions.ts`, `[id]/invoice-actions.ts`, `[id]/checklist-actions.ts`. `frontend/app/(app)/org/[slug]/projects/` — `page.tsx`, `actions.ts`, `[id]/page.tsx`, `[id]/actions.ts`, `[id]/task-actions.ts`. ~8 files. |
| 351.9 | Update tests for batch 1 | 351B | 351.8 | Modify test files that reference `orgRole` for settings, invoices, customers, projects. Replace mock `orgRole` values with mock capabilities. ~5 test files. |
| 351.10 | Migrate team, dashboard, my-work pages | 351C | 351B | Modify: `frontend/app/(app)/org/[slug]/team/page.tsx`, `team/member-actions.ts`, `team/invitation-actions.ts`, `dashboard/page.tsx`, `my-work/page.tsx`, `my-work/my-work-tasks-client.tsx`. ~6 files. |
| 351.11 | Migrate remaining pages (retainers, schedules, compliance, documents, profitability, resources, billing) | 351C | 351.10 | Modify: `retainers/page.tsx`, `retainers/actions.ts`, `retainers/[id]/page.tsx`, `retainers/[id]/actions.ts`, `schedules/page.tsx`, `schedules/[id]/page.tsx`, `compliance/` pages/actions, `documents/page.tsx`, `profitability/actions.ts`, `resources/` actions, `settings/billing/actions.ts`. ~10 files. |
| 351.12 | Migrate component files | 351C | 351.11 | Modify: `frontend/components/team/member-list.tsx`, `frontend/components/team/invite-member-form.tsx`, `frontend/components/tasks/task-list-panel.tsx`, `frontend/components/tasks/task-detail-sheet.tsx`, `frontend/components/tasks/time-entry-list.tsx`, `frontend/components/expenses/expense-list.tsx`, `frontend/components/projects/project-members-panel.tsx`, `frontend/components/settings/settings-sidebar.tsx`. ~8 files. |
| 351.13 | Update tests for batch 2 | 351C | 351.12 | Modify remaining test files that reference `orgRole`. ~5 test files. Run `pnpm run lint` and `pnpm test` to verify all frontend tests pass with no `orgRole` references. |

### Key Files

**Slice 351A — Modify:**
- `frontend/lib/auth/types.ts` — remove orgRole
- `frontend/lib/auth/providers/keycloak-bff.ts` — remove extraction + requireRole()
- `frontend/lib/auth/providers/mock/server.ts` — identity-only parsing
- `frontend/lib/types/member.ts` — orgRole type updates

**Slice 351B — Modify (~15 files):**
- `frontend/app/(app)/org/[slug]/settings/layout.tsx`
- `frontend/app/(app)/org/[slug]/invoices/*/` (6 files)
- `frontend/app/(app)/org/[slug]/customers/*/` (5 files)
- `frontend/app/(app)/org/[slug]/projects/*/` (4 files)

**Slice 351C — Modify (~15 files):**
- `frontend/app/(app)/org/[slug]/team/` (3 files)
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx`
- `frontend/components/team/member-list.tsx`
- `frontend/components/tasks/task-list-panel.tsx`
- Plus ~10 additional page/component files

**All Slices — Read for context:**
- `frontend/lib/api/capabilities.ts` — fetchMyCapabilities() API client (already exists from Phase 41)
- `frontend/lib/capabilities.tsx` — CapabilityProvider context (already exists)

### Architecture Decisions

- **Three sequential batches**: TypeScript compiler will flag all `orgRole` consumers after 351A. Batches are split to keep each slice within the 8-12 file limit.
- **`fetchMyCapabilities()` is the replacement**: All `orgRole === "org:admin"` checks become `caps.isAdmin`. The capabilities API already exists from Phase 41.
- **Frontend checks are defense-in-depth only**: Backend `@RequiresCapability` is the real security gate. Frontend checks control UI visibility (show/hide buttons, settings sections).

---

## Epic 352: Mock IDP & E2E Fixture Update

**Goal**: Update the mock IDP token format from Clerk v2 to Keycloak-only, and update E2E fixtures to match.

**References**: Architecture doc Section 11.7.4.

**Dependencies**: Epic 351 (frontend no longer expects `orgRole`).

**Scope**: Frontend/E2E

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **352A** | 352.1--352.4 | Update `compose/mock-idp/src/index.ts` to Keycloak-only token payload (remove `o.rol`). Update `e2e/fixtures/auth.ts` (drop `orgRole` from `loginAs()`). Update `frontend/components/auth/mock-login-form.tsx`. Run E2E smoke tests. ~3 modified files (~3 E2E tests). | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 352.1 | Update mock IDP token format | 352A | 351C | Modify: `compose/mock-idp/src/index.ts`. Change token payload from Clerk v2 format `{ sub, v: 2, o: { id, rol, slg } }` to Keycloak format `{ sub, organization: [orgSlug], groups: userGroups[userId] \|\| [], email: userEmails[userId] }`. Remove `orgRole` from user data and token construction. |
| 352.2 | Update `mock-login-form.tsx` | 352A | 352.1 | Modify: `frontend/components/auth/mock-login-form.tsx`. Remove `orgRole` from the token request to mock IDP. The form still allows selecting a user (Alice/Bob/Carol) but no longer sends a role parameter. |
| 352.3 | Update E2E auth fixtures | 352A | 352.2 | Modify: `frontend/e2e/fixtures/auth.ts`. In `loginAs()`: drop `orgRole` parameter from the mock IDP `/token` request body. Role for E2E users comes from seed data in the DB (member -> org_role_id FK), not from the token. |
| 352.4 | Run E2E smoke tests | 352A | 352.3 | Verify the 3 existing Playwright smoke tests pass with the updated token format. Tests should: (1) login as Alice (owner), (2) navigate to dashboard, (3) verify admin UI elements are visible (sourced from capabilities API, not token). |

### Key Files

**Slice 352A — Modify:**
- `compose/mock-idp/src/index.ts` — Keycloak-only token format
- `frontend/components/auth/mock-login-form.tsx` — remove role from token request
- `frontend/e2e/fixtures/auth.ts` — drop orgRole from loginAs()

**Slice 352A — Read for context:**
- `compose/mock-idp/src/users.ts` — user data structure
- `compose/mock-idp/src/keys.ts` — RSA key generation (unchanged)

### Architecture Decisions

- **Keycloak-only token format**: The mock IDP now issues tokens that match Keycloak's actual format: `organization` as flat list, `groups` for platform-admin check, `email` for identity. No `org_role` or Clerk `o.rol`.
- **Role from DB seed**: E2E users (Alice=owner, Bob=admin, Carol=member) get their roles from the seed data script, not from the token. This matches the production flow exactly.

---

## Epic 353: Cleanup — Remove ROLE_ORG_* & Role Sync

**Goal**: Remove the last vestiges of JWT-based authorization: `ROLE_ORG_*` Spring Security authorities and the `MemberSyncService` role sync webhook path.

**References**: Architecture doc Section 11.12 (Epic 5).

**Dependencies**: Epic 351 (all `@PreAuthorize` annotations removed from controllers, frontend uses capabilities).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **353A** | 353.1--353.3 | Remove `ROLE_ORG_*` authority grants from `ClerkJwtAuthenticationConverter` (or renamed `JwtUtils` integration). Remove `Roles.AUTHORITY_ORG_OWNER`, `AUTHORITY_ORG_ADMIN`, `AUTHORITY_ORG_MEMBER` constants. ~2 modified files (~4 tests). Backend only. | |
| **353B** | 353.4--353.6 | Remove `MemberSyncService` webhook role sync code path (the part that syncs Keycloak org role to DB). Clean unused role sync audit events. ~2 modified files (~3 tests). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 353.1 | Remove `ROLE_ORG_*` authority grants | 353A | 351C | Modify: `backend/.../security/ClerkJwtAuthenticationConverter.java`. Stop granting `ROLE_ORG_OWNER`, `ROLE_ORG_ADMIN`, `ROLE_ORG_MEMBER` authorities. The converter should only extract identity claims (sub, organization). Since no `@PreAuthorize` annotations remain (all migrated in Epic 347), these authorities have no consumers. |
| 353.2 | Remove `Roles.AUTHORITY_*` constants | 353A | 353.1 | Modify: `backend/.../security/Roles.java`. Remove `AUTHORITY_ORG_OWNER`, `AUTHORITY_ORG_ADMIN`, `AUTHORITY_ORG_MEMBER` constants. If the `Roles` class becomes empty, consider deleting it entirely. Grep for any remaining references. |
| 353.3 | Update tests to not use ROLE_ORG_* | 353A | 353.2 | Update any remaining test files that set `.authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_*")))` in JWT mocks. These should use capability-based test setup instead. Verify all tests pass without `ROLE_ORG_*` authorities. ~4 test file adjustments. |
| 353.4 | Remove role sync from `MemberSyncService` | 353B | 353A | Modify: `backend/.../member/MemberSyncService.java`. Remove the code path that syncs Keycloak org role to the member's `orgRole` field (which no longer exists after V69 migration). The sync service should only handle identity sync (name, email, avatar). |
| 353.5 | Clean unused role sync audit events | 353B | 353.4 | If `MemberSyncService` emitted audit events for role changes via webhook (e.g., `MEMBER_ROLE_SYNCED`), remove those event emissions. Role changes are now audit-logged only through `OrgRoleService.assignRole()`. |
| 353.6 | Verify final state | 353B | 353.5 | Run full test suite. Grep codebase for: (1) `ROLE_ORG_` — should find 0 references. (2) `hasRole\|hasAnyRole` — should find 0 in controller files (may remain in `SecurityConfig` for authentication checks). (3) `org_role` (lowercase, column name) — should find 0 in entity files. (4) `extractOrgRole` — should find 0 references. |

### Key Files

**Slice 353A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtAuthenticationConverter.java` (or `JwtAuthenticationConverter.java` if renamed)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/Roles.java`

**Slice 353B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java`

### Architecture Decisions

- **Last slice guards correctness**: The verification grep in 353.6 ensures no dead references remain. Any residual `ROLE_ORG_*` reference would be a silent no-op (no `@PreAuthorize` to consume it), but removing them eliminates developer confusion.
- **MemberSyncService retains identity sync**: The service still handles webhook-triggered identity updates (name, email, avatar changes from Keycloak). Only the role sync path is removed.

---

## Risk Register

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Missed `@PreAuthorize` migration causes 403 | High | Low | Every annotation is covered by existing 830+ backend integration tests. A missed migration surfaces immediately. |
| Cache eviction missed on role mutation | High | Low | 1-hour TTL safety net. All mutation paths have explicit eviction calls (346.8, 346.9). |
| Frontend orgRole references missed | Medium | Low | TypeScript compiler flags all consumers after `AuthContext.orgRole` removal. Grep verification in 353.6. |
| V69 migration data loss | High | Very Low | Backfill step runs before DROP COLUMN. V68 (PendingInvitation) runs first, V69 runs only after all code paths use `org_role_id`. |
| KeycloakAdminClient copy diverges | Low | Medium | Gateway version is deleted in 350B (2 slices after copy). Short-lived duplication window. |
| E2E tests break with new token format | Medium | Medium | Mock IDP update (352A) is tested with existing Playwright smoke tests. Seed data ensures correct roles. |

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` - Core logic change: JWT role preference removal, invitation lookup in lazy-create, cache eviction integration
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java` - Entity restructure: drop orgRole VARCHAR, add @ManyToOne OrgRole, update constructor
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` - Rename to JwtUtils, remove Clerk v2 format methods, remove extractOrgRole()
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/auth/types.ts` - Breaking type change: remove orgRole from AuthContext, cascades to 107 frontend files
- `/Users/rakheendama/Projects/2026/b2b-strawman/gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java` - Remove orgRole from BffUserInfo, delete createOrg(), remove KeycloakAdminClient dependency
