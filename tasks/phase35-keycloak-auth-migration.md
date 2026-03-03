# Phase 35 — Keycloak Auth Provider Migration

Phase 35 adds **Keycloak 26.5 as a self-hosted auth provider** alongside the existing Clerk integration. The migration is additive — Clerk remains as the default provider, selectable via `AUTH_MODE`. The design leverages Phase 20's auth abstraction layer, which already decoupled 74+ files from Clerk imports. Key changes: custom Keycloak SPI for JWT claim alignment, app-initiated provisioning (replacing webhook-driven), next-auth v5 for frontend OIDC, and provider-agnostic field renames.

**Architecture doc**: `architecture/phase35-keycloak-auth-migration.md`

**ADRs**: [ADR-138](../adr/ADR-138-auth-provider-strategy.md) (Keycloak selection), [ADR-139](../adr/ADR-139-jwt-claim-format-design.md) (JWT claim format), [ADR-140](../adr/ADR-140-provisioning-flow-direction.md) (app-initiated provisioning), [ADR-141](../adr/ADR-141-frontend-auth-library.md) (next-auth v5), [ADR-142](../adr/ADR-142-field-naming-migration.md) (field naming migration)

**Migration coordination**: Global V13 (public schema renames), Tenant V55 (member table rename). Keycloak uses its own `keycloak` schema — no conflict with application migrations.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 261 | Backend Field Renames | Backend + Frontend | -- | M | 261A, 261B, 261C | **Done** (PRs #507, #508) |
| 262 | Keycloak SPI + Docker Setup | Keycloak + Infra | -- | M | 262A, 262B | **Done** (PRs #509, #510) |
| 263 | Backend Keycloak Integration | Backend | 262 | L | 263A, 263B | **Done** (PRs #511, #512) |
| 264 | Frontend Keycloak Provider | Frontend | 261C | M | 264A, 264B | Not started |
| 265 | Frontend UI Components | Frontend | 263B, 264B | M | 265A, 265B, 265C | Not started |
| 266 | Integration Testing + E2E | Both | 263B, 265C | L | 266A, 266B | Not started |
| 267 | Documentation | Docs | 266B | S | 267A | Not started |

## Dependency Graph

```
[E261A Field Renames] ──┬──► [E261B Frontend DTOs]
     (Backend)          ├──► [E261C Class Renames]
                        │         │
[E262A SPI Module] ─────┤         │
     (Keycloak)         │         │
[E262B Docker Setup] ───┤         │
     (Infra)            │         │
                        ├──► [E263A Admin API Client] ──► [E263B Org Endpoints]
                        │         (Backend)                    (Backend)
                        │                                         │
                        └──► [E264A next-auth Setup] ──► [E264B Middleware + Client]
                                  (Frontend)                   (Frontend)
                                                                  │
                                                    ┌─────────────┼─────────────┐
                                                    ▼             ▼             ▼
                                              [E265A Auth   [E265B Header  [E265C Team
                                               Pages]        Controls]     Components]
                                                    │             │             │
                                                    └─────────────┼─────────────┘
                                                                  ▼
                                                    [E266A Backend Tests] ──► [E266B E2E]
                                                                                  │
                                                                                  ▼
                                                                          [E267A Docs]
```

**Parallel tracks** (after 261A completes):
- Track 1: 261B + 261C — quick renames (~1 day)
- Track 2: 262A + 262B — Keycloak infrastructure (~2-3 days, independent of 261)
- Track 3: 263A → 263B — backend Keycloak integration (~3-4 days, needs 262B)
- Track 4: 264A → 264B → 265A-C — frontend (~4-5 days, needs 261C)
- Track 5: 266A → 266B → 267A — testing + docs (~3-4 days, needs 263B + 265C)

## Implementation Order

### Stage 1: Foundation — Field Renames + Infrastructure (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 261 | 261A | **Flyway migrations V13 (global) + V55 (tenant)** — rename `clerk_org_id` → `external_org_id`, `clerk_user_id` → `external_user_id`. Update 19 backend files: entities, repositories, services, controllers. Mechanical refactor, validated by compilation + tests. Foundation for everything else. | **Done** (PR #507) |
| 1b | Epic 261 | 261B | **Frontend DTO renames** — update `lib/internal-api.ts`, `lib/webhook-handlers.ts` to use `externalOrgId`/`externalUserId`. Must match backend changes from 261A. | **Done** (shipped with PR #507) |
| 1c | Epic 261 | 261C | **Class renames** — `ClerkJwtUtils` → `JwtClaimExtractor`, `ClerkJwtAuthenticationConverter` → `OrgJwtAuthenticationConverter`. Update all imports (~8 files). Provider-agnostic naming. | **Done** (PR #508) |
| 1d | Epic 262 | 262A | **Custom protocol mapper SPI** — `keycloak-spi/` Maven module with `OrgRoleProtocolMapper`. Reads org membership + role, injects `"o"` claim matching Clerk v2 format. ~80-100 lines Java. Can run in parallel with 261. | **Done** (PR #509) |
| 1e | Epic 262 | 262B | **Docker Compose + realm config** — add Keycloak service (port 9090), `realm-export.json`, `application-keycloak.yml`. Can run in parallel with 261. | **Done** (PR #510) |

### Stage 2: Backend Integration (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 263 | 263A | **KeycloakAdminService** — REST client for Keycloak Admin API. Operations: createOrg, deleteOrg, addMember, inviteUser, listInvitations, cancelInvitation, getUserOrganizations. Uses Spring RestClient with client credentials auth. @ConditionalOnProperty for Keycloak-only activation. Integration tests with Testcontainers (Keycloak + Postgres). Depends on 262B (Docker setup). | **Done** (PR #511) |
| 2b | Epic 263 | 263B | **OrgManagementController** — REST endpoints: POST /api/orgs (create org + provision), GET /api/orgs/mine (list user orgs), POST /api/orgs/{id}/invite, GET /api/orgs/{id}/invitations, DELETE /api/orgs/{id}/invitations/{invId}. OrgManagementService orchestrates Keycloak Admin calls + TenantProvisioningService. Integration tests. Depends on 263A. | **Done** (PR #512) |

### Stage 3: Frontend (Sequential, then Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 264 | 264A | **next-auth v5 setup** — install `next-auth`, create `auth.ts` config with Keycloak OIDC provider, create `lib/auth/providers/keycloak.ts` (5 server functions: getAuthContext, getAuthToken, getCurrentUserEmail, hasPlan, requireRole), create `app/api/auth/[...nextauth]/route.ts`. Update `lib/auth/server.ts` dispatch. Depends on 261C (class renames). |
| 3b | Epic 264 | 264B | **Middleware + client** — add `createKeycloakMiddleware()` to `lib/auth/middleware.ts`, create `lib/auth/client/keycloak-context.tsx` using `SessionProvider`, update `lib/auth/client/auth-provider.tsx` with Keycloak branch, update `lib/auth/client/hooks.ts` (useAuthUser, useSignOut). Depends on 264A. |
| 3c | Epic 265 | 265A | **Auth pages** — update `sign-in/page.tsx` (Keycloak redirect), `sign-up/page.tsx` (redirect to `/create-org`), `create-org/page.tsx` (custom org creation form → POST /api/orgs). Depends on 264B + 263B. |
| 3d | Epic 265 | 265B | **Header controls** — custom `KeycloakUserButton` + `KeycloakOrgSwitcher` in `auth-header-controls.tsx`. OrgSwitcher fetches from GET /api/orgs/mine, triggers re-auth with `kc_org` param. Update `sidebar-user-footer.tsx`. Depends on 264B + 263B. Can parallel with 265A. |
| 3e | Epic 265 | 265C | **Team components** — update `member-list.tsx`, `invite-member-form.tsx`, `pending-invitations.tsx` with Keycloak branches (call backend API instead of Clerk hooks). Update `team/actions.ts` inviteMember server action for Keycloak. Depends on 264B + 263B. Can parallel with 265A, 265B. |

### Stage 4: Testing + Documentation

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 266 | 266A | **Backend integration tests** — full provisioning flow with Keycloak org IDs, OrgManagementController tests, Testcontainers. Verify Clerk regression (existing tests still pass). Depends on 263B. |
| 4b | Epic 266 | 266B | **E2E stack** — add Keycloak to docker-compose.e2e.yml (port 9091), update seed script (create org in Keycloak + provision), Playwright auth fixture (`loginAs()` via Keycloak), smoke tests (login, dashboard, team page). Depends on 265C + 266A. |
| 4c | Epic 267 | 267A | **Documentation** — update `backend/CLAUDE.md`, `frontend/CLAUDE.md` with Keycloak profile/mode docs. Update `TASKS.md` with Phase 35 status. Update `MEMORY.md`. Depends on 266B. |

### Timeline

```
Stage 1:  [261A] ──► [261B // 261C]     ← field renames (sequential then parallel)
          [262A // 262B]                 ← Keycloak infra (parallel with 261)
Stage 2:  [263A] ──► [263B]              ← backend integration (sequential)
Stage 3:  [264A] ──► [264B] ──► [265A // 265B // 265C]  ← frontend (sequential then parallel)
Stage 4:  [266A] ──► [266B] ──► [267A]  ← testing + docs (sequential)
```

**Critical path**: 261A → 261C → 264A → 264B → 265C → 266B → 267A
**Parallelizable**: 261B//261C, 262A//262B (with 261), 265A//265B//265C

---

## Epic 261: Backend Field Renames

**Goal**: Rename all Clerk-specific fields and classes to provider-agnostic names. This is a mechanical refactor that touches many files but carries low logic risk — validated by compilation and the existing test suite (~830+ backend tests).

**References**: Architecture doc Section 35.3. [ADR-142](../adr/ADR-142-field-naming-migration.md).

**Dependencies**: None (foundation epic)

**Scope**: Backend + Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **261A** | 261.1-261.8 | Flyway migrations V13 (global) + V55 (tenant), entity field renames (Organization, OrgSchemaMapping, Member), repository method renames (4 repos), service/controller param renames (8 services), DTO field renames (3 controllers), test factory updates, verify all tests pass | **Done** (PR #507) |
| **261B** | 261.9-261.11 | Frontend DTO renames in `lib/internal-api.ts` and `lib/webhook-handlers.ts`, update webhook test | **Done** (shipped with PR #507 — atomic API contract change) |
| **261C** | 261.12-261.16 | Class renames: `ClerkJwtUtils` → `JwtClaimExtractor`, `ClerkJwtAuthenticationConverter` → `OrgJwtAuthenticationConverter`, update all imports in SecurityConfig, TenantFilter, MemberFilter, tests | **Done** (PR #508) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 261.1 | Create V13 global migration | 261A | Not started | `db/migration/global/V13__rename_clerk_to_external.sql`. `ALTER TABLE organizations RENAME COLUMN clerk_org_id TO external_org_id; ALTER TABLE org_schema_mapping RENAME COLUMN clerk_org_id TO external_org_id;` Update index names. Metadata-only operation. |
| 261.2 | Create V55 tenant migration | 261A | Not started | `db/migration/tenant/V55__rename_clerk_user_id_to_external.sql`. `ALTER TABLE members RENAME COLUMN clerk_user_id TO external_user_id;` Rename index/constraint. |
| 261.3 | Rename entity fields | 261A | Not started | `Organization.java`: `clerkOrgId` → `externalOrgId` (field, `@Column`, getter, constructor). `OrgSchemaMapping.java`: same rename. `Member.java`: `clerkUserId` → `externalUserId`. |
| 261.4 | Rename repository methods | 261A | Not started | `OrganizationRepository`: `findByClerkOrgId()` → `findByExternalOrgId()`. `OrgSchemaMappingRepository`: same. `MemberRepository`: `findByClerkUserId()` → `findByExternalUserId()`, `deleteByClerkUserId()` → `deleteByExternalUserId()`, `existsByClerkUserId()` → `existsByExternalUserId()`. |
| 261.5 | Rename service/controller params | 261A | Not started | Update parameter names in `TenantProvisioningService`, `ProvisioningController`, `MemberSyncService`, `MemberSyncController`, `PlanSyncService`, `PlanSyncController`, `SubscriptionService`, `SchemaNameGenerator`. All `clerkOrgId` → `externalOrgId`, `clerkUserId` → `externalUserId`. |
| 261.6 | Rename DTO fields | 261A | Not started | `ProvisioningController.ProvisioningRequest`: `clerkOrgId` → `externalOrgId`. `MemberSyncController.SyncMemberRequest`: `clerkOrgId` → `externalOrgId`, `clerkUserId` → `externalUserId`. `PlanSyncController.PlanSyncRequest`: `clerkOrgId` → `externalOrgId`. |
| 261.7 | Update TenantFilter + MemberFilter | 261A | Not started | `TenantFilter`: rename `clerkOrgId` variable and `evictSchema(String clerkOrgId)` → `evictSchema(String externalOrgId)`. `MemberFilter`: rename `clerkUserId` variable, cache key pattern. |
| 261.8 | Update test factories + verify | 261A | Not started | Update `TestCustomerFactory`, test JWT helpers, all integration tests referencing `clerkOrgId`/`clerkUserId`. Run `./mvnw clean verify -q` — all tests must pass. |
| 261.9 | Rename frontend DTOs | 261B | Not started | `lib/internal-api.ts`: rename `clerkOrgId` → `externalOrgId`, `clerkUserId` → `externalUserId` in request types and payload construction. |
| 261.10 | Update webhook handlers | 261B | Not started | `lib/webhook-handlers.ts`: update all payload construction to use `externalOrgId`/`externalUserId` field names when calling backend `/internal/*` endpoints. |
| 261.11 | Update webhook test | 261B | Not started | `app/api/webhooks/clerk/route.test.ts`: update test payloads to match renamed fields. |
| 261.12 | Rename ClerkJwtUtils → JwtClaimExtractor | 261C | Not started | Rename file and class. Update all import statements. |
| 261.13 | Rename ClerkJwtAuthenticationConverter → OrgJwtAuthenticationConverter | 261C | Not started | Rename file and class. Update `SecurityConfig.java` constructor injection. |
| 261.14 | Update filter imports | 261C | Not started | `TenantFilter.java`, `MemberFilter.java`: update `ClerkJwtUtils` → `JwtClaimExtractor` references. |
| 261.15 | Update security test imports | 261C | Not started | All test files referencing `ClerkJwtUtils` or `ClerkJwtAuthenticationConverter`. |
| 261.16 | Verify all tests pass | 261C | Not started | Run `./mvnw clean verify -q`. All ~830+ tests pass with renamed classes. |

---

## Epic 262: Keycloak SPI + Docker Setup

**Goal**: Establish Keycloak infrastructure — the custom protocol mapper SPI that shapes JWT claims, and the Docker Compose services with pre-configured realm.

**References**: Architecture doc Sections 35.4 (SPI module), 35.9 (infrastructure). [ADR-139](../adr/ADR-139-jwt-claim-format-design.md).

**Dependencies**: None (infrastructure epic, can parallel with 261)

**Scope**: Keycloak + Infrastructure

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **262A** | 262.1-262.5 | New `keycloak-spi/` Maven module, `OrgRoleProtocolMapper` implementing `OIDCAccessTokenMapper`, reads org membership + role via OrganizationProvider SPI, injects `"o"` claim, META-INF services descriptor, unit tests | **Done** (PR #509) |
| **262B** | 262.6-262.11 | Keycloak Docker service in docker-compose.yml (port 9090), `compose/keycloak/realm-export.json` (realm, clients, org feature, mapper, roles, SMTP), `application-keycloak.yml` Spring profile, dev-up.sh script update, verify Keycloak starts and SPI loads | **Done** (PR #510) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 262.1 | Create keycloak-spi Maven module | 262A | Not started | `keycloak-spi/pom.xml` with dependencies: `keycloak-server-spi`, `keycloak-server-spi-private`, `keycloak-services` (provided scope). Target: JAR artifact. |
| 262.2 | Implement OrgRoleProtocolMapper | 262A | Not started | `com/docteams/keycloak/OrgRoleProtocolMapper.java`. Implements `OIDCAccessTokenMapper`. Reads `kc_org` session note → finds org membership → extracts role → injects `"o"` claim with `{id, rol, slg}`. ~80-100 lines. |
| 262.3 | Add SPI service descriptor | 262A | Not started | `META-INF/services/org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper` pointing to `OrgRoleProtocolMapper`. |
| 262.4 | Write mapper unit tests | 262A | Not started | Test: single-org user gets claim, multi-org user with kc_org gets correct org, no org membership returns no claim, role mapping (owner/admin/member). |
| 262.5 | Build and verify JAR | 262A | Not started | `cd keycloak-spi && mvn clean package`. Verify JAR structure contains mapper class + SPI descriptor. |
| 262.6 | Add Keycloak to docker-compose.yml | 262B | Not started | `keycloak` service: `quay.io/keycloak/keycloak:26.1`, `start-dev --import-realm`, port 9090, postgres connection (keycloak schema), volume mounts for realm-export.json and SPI JAR. |
| 262.7 | Create realm-export.json | 262B | Not started | `compose/keycloak/realm-export.json`. Realm: `docteams`. Clients: `docteams-web` (public, PKCE, redirect URIs), `docteams-admin` (confidential, service account). Organization feature enabled. OrgRoleProtocolMapper configured on docteams-web client. Default org roles: owner, admin, member. SMTP: Mailpit (mailpit:1025). |
| 262.8 | Create application-keycloak.yml | 262B | Not started | `backend/src/main/resources/application-keycloak.yml`. JWT issuer-uri + jwk-set-uri pointing to Keycloak. Keycloak admin config (server-url, realm, client-id, client-secret). |
| 262.9 | Update dev-up.sh | 262B | Not started | Add optional `--keycloak` flag to `compose/scripts/dev-up.sh` to include Keycloak service. |
| 262.10 | Create Keycloak dev seed script | 262B | Not started | `compose/scripts/keycloak-seed.sh` — creates a test org and test users in Keycloak via Admin API (for local dev convenience). |
| 262.11 | Verify Keycloak starts with SPI | 262B | Not started | Start stack with `--keycloak`, verify admin console accessible at localhost:9090, verify SPI JAR loaded (check Keycloak logs for mapper registration). |

---

## Epic 263: Backend Keycloak Integration

**Goal**: Backend can manage organizations and members via Keycloak Admin API. New REST endpoints enable the frontend to create orgs, invite members, and list invitations through the backend (which proxies to Keycloak).

**References**: Architecture doc Sections 35.6 (provisioning flow), 35.7 (member sync), 35.8 (backend integration). [ADR-140](../adr/ADR-140-provisioning-flow-direction.md).

**Dependencies**: Epic 262 (Docker setup must exist for integration tests)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **263A** | 263.1-263.6 | `KeycloakAdminService` — REST client for Keycloak Admin API. Operations: createOrg, deleteOrg, addMember, inviteUser, listInvitations, cancelInvitation, getUserOrganizations. Client credentials auth. `KeycloakConfig` with RestClient bean. @ConditionalOnProperty. Integration tests with Testcontainers | **Done** (PR #511) |
| **263B** | 263.7-263.12 | `OrgManagementController` — POST /api/orgs (create + provision), GET /api/orgs/mine, POST /api/orgs/{id}/invite, GET /api/orgs/{id}/invitations, DELETE /api/orgs/{id}/invitations/{invId}. `OrgManagementService` orchestrating Keycloak + provisioning. Integration tests | **Done** (PR #512) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 263.1 | Create KeycloakConfig | 263A | Not started | `keycloak/KeycloakConfig.java`. @Configuration, @ConditionalOnProperty. Reads config props. Creates RestClient bean with client credentials OAuth2 token. |
| 263.2 | Create KeycloakAdminService | 263A | Not started | `keycloak/KeycloakAdminService.java`. @Service, @ConditionalOnProperty. 7 methods mapping to Keycloak Admin REST API endpoints. Error handling (404 → ResourceNotFound, 409 → Conflict). |
| 263.3 | Add Keycloak response DTOs | 263A | Not started | Records: `KeycloakOrganization`, `KeycloakOrgMember`, `KeycloakInvitation`. Map Keycloak Admin API JSON responses. |
| 263.4 | Write KeycloakAdminService integration tests | 263A | Not started | Testcontainers with Keycloak + Postgres. Tests: createOrg, addMember, inviteUser, listMembers, getUserOrgs, deleteOrg. ~8 tests. |
| 263.5 | Add Testcontainers Keycloak dependency | 263A | Not started | Add `org.testcontainers:keycloak` to `pom.xml`. Create `KeycloakTestContainer` base class with realm import. |
| 263.6 | Verify conditional activation | 263A | Not started | Verify `KeycloakAdminService` bean NOT created when `keycloak.admin.enabled` is missing. Run existing tests (Clerk mode) — zero failures. |
| 263.7 | Create OrgManagementService | 263B | Not started | `keycloak/OrgManagementService.java`. @Service, @ConditionalOnProperty. Methods: createOrganization (Keycloak + provision + add owner), listUserOrganizations, inviteToOrganization, listInvitations, cancelInvitation. Compensating delete on provisioning failure. |
| 263.8 | Create OrgManagementController | 263B | Not started | `keycloak/OrgManagementController.java`. @RestController, @RequestMapping("/api/orgs"), @ConditionalOnProperty. 5 endpoints per architecture doc Section 35.8.3. |
| 263.9 | Create request/response DTOs | 263B | Not started | Records: `CreateOrgRequest(name, adminEmail, adminPassword)`, `CreateOrgResponse(slug, orgId)`, `UserOrgResponse(id, name, slug, role)`, `InviteRequest(email, role)`, `InvitationResponse(id, email, status, createdAt)`. |
| 263.10 | Write OrgManagementController integration tests | 263B | Not started | Testcontainers. Tests: create org provisions schema, create org adds creator as owner, list user orgs, invite sends to Keycloak, list invitations, cancel invitation, duplicate org name → 409. ~10 tests. |
| 263.11 | Test provisioning flow end-to-end | 263B | Not started | Integration test: create org via /api/orgs → verify Keycloak org exists + tenant schema provisioned + OrgSchemaMapping created + creator member synced. |
| 263.12 | Test Clerk mode regression | 263B | Not started | Run full test suite without Keycloak profile. Verify @ConditionalOnProperty beans are absent. Zero test failures. |

---

## Epic 264: Frontend Keycloak Provider

**Goal**: Server-side and client-side authentication working with Keycloak via next-auth v5. The existing `lib/auth/` dispatch pattern is extended with a `keycloak` provider.

**References**: Architecture doc Section 35.5. [ADR-141](../adr/ADR-141-frontend-auth-library.md).

**Dependencies**: Epic 261C (class renames for clean imports)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **264A** | 264.1-264.7 | Install next-auth v5, create `auth.ts` config (Keycloak OIDC provider, JWT callbacks, session strategy), create `lib/auth/providers/keycloak.ts` (5 server functions), create `app/api/auth/[...nextauth]/route.ts`, update `lib/auth/server.ts` dispatch, extend session types | Not started |
| **264B** | 264.8-264.12 | `createKeycloakMiddleware()` in middleware.ts, `lib/auth/client/keycloak-context.tsx` (SessionProvider wrapper), update `auth-provider.tsx` with Keycloak branch, update `hooks.ts` (useAuthUser, useOrgMembers, useSignOut for Keycloak), frontend tests | Not started |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 264.1 | Install next-auth v5 | 264A | Not started | `pnpm add next-auth@5`. Add to `package.json`. |
| 264.2 | Create auth.ts config | 264A | Not started | `frontend/auth.ts`. NextAuth config: Keycloak OIDC provider (clientId, clientSecret, issuer from env), JWT callback (store accessToken, refreshToken, expiresAt), session callback (expose accessToken), pages config. |
| 264.3 | Create Keycloak server provider | 264A | Not started | `lib/auth/providers/keycloak.ts`. Implement 5 functions: `getAuthContext()` (decode access token for org claims), `getAuthToken()` (return access token from session), `getCurrentUserEmail()` (from session), `hasPlan()` (check org attribute or default true), `requireRole()` (check org role from token). |
| 264.4 | Create next-auth API route | 264A | Not started | `app/api/auth/[...nextauth]/route.ts`. Export GET and POST handlers from `auth.ts`. |
| 264.5 | Update server.ts dispatch | 264A | Not started | `lib/auth/server.ts`: add `if (AUTH_MODE === "keycloak") return keycloakProvider.function()` for all 5 functions. Import keycloak provider. |
| 264.6 | Extend session types | 264A | Not started | `types/next-auth.d.ts`: extend Session to include `accessToken: string`. Extend JWT to include `accessToken`, `refreshToken`, `expiresAt`. |
| 264.7 | Add Keycloak env vars to .env.example | 264A | Not started | `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `KEYCLOAK_ISSUER`, `NEXTAUTH_SECRET`, `NEXTAUTH_URL`. |
| 264.8 | Add createKeycloakMiddleware | 264B | Not started | `lib/auth/middleware.ts`: new function checking `next-auth.session-token` cookie, redirecting to `/api/auth/signin` if absent. Reuse `isPublicRoute()`. Handle org redirect (`/dashboard` → `/org/{slug}/dashboard`). |
| 264.9 | Create Keycloak client context | 264B | Not started | `lib/auth/client/keycloak-context.tsx`: wrapper using `SessionProvider` from `next-auth/react`. |
| 264.10 | Update auth-provider.tsx | 264B | Not started | Add Keycloak branch: `if (AUTH_MODE === "keycloak") return <KeycloakAuthProvider>{children}</KeycloakAuthProvider>`. |
| 264.11 | Update client hooks | 264B | Not started | `lib/auth/client/hooks.ts`: add Keycloak implementations. `useAuthUser()` → `useSession()` from next-auth. `useSignOut()` → `signOut()` from next-auth. `useOrgMembers()` → fetch from `/api/orgs/{id}/members` (same as mock mode). |
| 264.12 | Frontend tests | 264B | Not started | Vitest: test provider dispatch with `AUTH_MODE=keycloak`, test middleware redirect logic, test hook implementations. ~5 tests. |

---

## Epic 265: Frontend UI Components

**Goal**: All Clerk UI components have working Keycloak alternatives. Each component gets a Keycloak branch alongside existing Clerk and mock branches.

**References**: Architecture doc Section 35.5.5 (UI component replacements), 35.10 (registration model).

**Dependencies**: Epic 264B (client provider + hooks), Epic 263B (backend org endpoints)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **265A** | 265.1-265.4 | Auth pages: sign-in (Keycloak redirect), sign-up (redirect to create-org), create-org (custom form → POST /api/orgs with name + email + password) | Not started |
| **265B** | 265.5-265.8 | Header controls: custom KeycloakUserButton (user info + sign-out), KeycloakOrgSwitcher (fetch from GET /api/orgs/mine, re-auth with kc_org param). Sidebar user footer | Not started |
| **265C** | 265.9-265.13 | Team components: member-list, invite-member-form, pending-invitations — Keycloak branches calling backend API. Update team/actions.ts inviteMember for Keycloak | Not started |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 265.1 | Update sign-in page | 265A | Not started | `app/(auth)/sign-in/page.tsx`: add Keycloak branch. Call `signIn("keycloak", { callbackUrl: "/dashboard" })` from next-auth/react. |
| 265.2 | Update sign-up page | 265A | Not started | `app/(auth)/sign-up/page.tsx`: add Keycloak branch. Redirect to `/create-org` with explanation message (invitation-only model). |
| 265.3 | Build custom org creation form | 265A | Not started | `app/(app)/create-org/page.tsx`: add Keycloak branch with form (org name, admin email, password). Submit → call POST /api/orgs via server action → re-auth with kc_org → redirect to dashboard. |
| 265.4 | Auth page tests | 265A | Not started | Vitest: sign-in redirect, sign-up redirect to create-org, org creation form submission. ~3 tests. |
| 265.5 | Build KeycloakUserButton | 265B | Not started | Custom dropdown in `auth-header-controls.tsx`. Shows user name + avatar from session. Sign-out calls `signOut()`. Pattern: similar to existing MockUserButton. |
| 265.6 | Build KeycloakOrgSwitcher | 265B | Not started | Custom dropdown in `auth-header-controls.tsx`. Fetches user's orgs from `GET /api/orgs/mine`. On org select: triggers re-authentication with `kc_org={alias}` param via `signIn("keycloak", { kc_org: alias })`. Shows current org name + badge. |
| 265.7 | Update sidebar-user-footer | 265B | Not started | `components/sidebar-user-footer.tsx`: add Keycloak branch using `useSession()` from next-auth for user info. |
| 265.8 | Header control tests | 265B | Not started | Vitest: user button renders, org switcher renders orgs, sign-out triggers next-auth signOut. ~3 tests. |
| 265.9 | Update member-list for Keycloak | 265C | Not started | `components/team/member-list.tsx`: add Keycloak branch. Fetch members from backend API (same as mock mode pattern). Remove `useOrganization()` dependency in Keycloak mode. |
| 265.10 | Update invite-member-form for Keycloak | 265C | Not started | `components/team/invite-member-form.tsx`: add Keycloak branch. Role selector uses "owner"/"admin"/"member" (without "org:" prefix). Submit calls updated inviteMember server action. |
| 265.11 | Update pending-invitations for Keycloak | 265C | Not started | `components/team/pending-invitations.tsx`: add Keycloak branch. Fetch from `GET /api/orgs/{id}/invitations`. Show status (pending/expired). Cancel action calls `DELETE /api/orgs/{id}/invitations/{invId}`. |
| 265.12 | Update team actions | 265C | Not started | `app/(app)/org/[slug]/team/actions.ts`: add Keycloak branch to `inviteMember()`. Call `POST /api/orgs/{id}/invite` with Bearer token. |
| 265.13 | Team component tests | 265C | Not started | Vitest: member list renders, invite form submits, pending invitations display. ~4 tests. |

---

## Epic 266: Integration Testing + E2E

**Goal**: Full end-to-end validation of Keycloak mode. Verify backend integration tests, E2E stack with Keycloak, and Playwright smoke tests.

**References**: Architecture doc Sections 35.6 (provisioning flow), 35.9 (infrastructure).

**Dependencies**: Epic 263B (backend endpoints), Epic 265C (frontend components)

**Scope**: Both

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **266A** | 266.1-266.4 | Backend integration tests: full provisioning flow with Keycloak org IDs, OrgManagement tests, Clerk regression (all existing tests pass unchanged) | Not started |
| **266B** | 266.5-266.10 | E2E stack: Keycloak in docker-compose.e2e.yml, seed script (create KC org + provision tenant + sync members), Playwright auth fixture, smoke tests (login, dashboard, team page, invite flow) | Not started |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 266.1 | Keycloak provisioning flow test | 266A | Not started | Integration test: POST /api/orgs with Keycloak Testcontainer → verify KC org exists + schema provisioned + mapping created + member synced. |
| 266.2 | OrgManagement endpoint tests | 266A | Not started | Tests for all 5 endpoints: create org, list user orgs, invite, list invitations, cancel invitation. Testcontainers. |
| 266.3 | JWT claim extraction test | 266A | Not started | Verify `JwtClaimExtractor` works with Keycloak-formatted tokens (same `"o"` claim). Verify TenantFilter resolves schema correctly. |
| 266.4 | Clerk regression test | 266A | Not started | Run full `./mvnw clean verify -q` WITHOUT Keycloak profile. All ~830+ tests pass. Zero @ConditionalOnProperty beans created. |
| 266.5 | Add Keycloak to E2E Docker stack | 266B | Not started | `compose/docker-compose.e2e.yml`: add `keycloak` service (port 9091), depends_on postgres, volume mounts for realm-export.json and SPI JAR. |
| 266.6 | Create E2E seed script for Keycloak | 266B | Not started | Update seed container to create org in Keycloak via Admin API, provision tenant via /internal/orgs/provision, sync test users (Alice/Bob/Carol). |
| 266.7 | Create Playwright auth fixture | 266B | Not started | `e2e/fixtures/keycloak-auth.ts`: `loginAsKeycloak(page, user)` — get token from Keycloak via direct grant (resource owner password), set session cookie. |
| 266.8 | Smoke test: login + dashboard | 266B | Not started | Playwright: login as Alice, verify dashboard loads, verify org context correct. |
| 266.9 | Smoke test: team page | 266B | Not started | Playwright: navigate to team page, verify member list loads, verify invite form visible for owner. |
| 266.10 | Smoke test: org creation | 266B | Not started | Playwright: fill create-org form, submit, verify redirect to dashboard, verify org provisioned. |

---

## Epic 267: Documentation

**Goal**: Update all project documentation to reflect Phase 35 changes.

**References**: All architecture doc sections and ADRs.

**Dependencies**: Epic 266B (E2E validation complete)

**Scope**: Docs

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **267A** | 267.1-267.5 | Update backend/CLAUDE.md (Keycloak profile, renamed classes), frontend/CLAUDE.md (AUTH_MODE=keycloak, next-auth), TASKS.md (Phase 35 entry), MEMORY.md (Phase 35 summary) | Not started |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 267.1 | Update backend/CLAUDE.md | 267A | Not started | Document Keycloak Spring profile, JwtClaimExtractor (renamed from ClerkJwtUtils), OrgJwtAuthenticationConverter, KeycloakAdminService, OrgManagementController. |
| 267.2 | Update frontend/CLAUDE.md | 267A | Not started | Document AUTH_MODE=keycloak, next-auth v5 setup, keycloak provider, env vars. |
| 267.3 | Update TASKS.md | 267A | Not started | Add Phase 35 entry with epic/status summary. |
| 267.4 | Update MEMORY.md | 267A | Not started | Add Phase 35 completion summary, key lessons, architectural decisions. |
| 267.5 | Update CLAUDE.md (root) | 267A | Not started | Add Keycloak to tech stack table, update local dev quick start with Keycloak option, add Keycloak admin URL to agent nav table. |
