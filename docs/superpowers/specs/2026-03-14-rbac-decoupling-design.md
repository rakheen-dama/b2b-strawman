# RBAC Decoupling: Application-Managed Roles

**Date:** 2026-03-14
**Status:** Draft
**Scope:** Decouple authorization from Keycloak Organization Roles; make the product database the sole authority for RBAC.

## Problem

Keycloak Organization Roles are problematic for end-to-end RBAC:

- **Immature feature**: CVE-2026-1529 (forged invitation JWTs), missing org claims for multi-org users, Organization Roles ACL promised for "early 2025" still not fully delivered in 26.5.
- **Stale token problem**: JWT `org_role` claim can be stale until the token expires or the session is refreshed. Role changes in Keycloak are not reflected in active sessions.
- **Split authority**: The codebase has two parallel authorization systems — `@PreAuthorize("hasRole('ORG_*')")` (from JWT claims) and `@RequiresCapability` (from DB-stored capabilities). This creates confusion about which is authoritative.
- **Limited custom roles**: Keycloak org roles are fixed strings. The product already has an `OrgRole` entity with custom roles and per-member capability overrides that Keycloak knows nothing about.

## Decision

**Use Keycloak for authentication only. The product database becomes the sole authority for authorization.**

- Keycloak continues to handle: OIDC login, SSO, token issuance, org membership, invitation emails, platform-admin groups.
- The product DB owns: which role a member has, what capabilities that role grants, per-member overrides, custom role definitions, all authorization decisions.

## Design Decisions Summary

| Decision | Choice | Rationale |
|---|---|---|
| Authorization source | Product DB only | Already 80% built (OrgRole, Capability, OrgRoleService) |
| `@PreAuthorize` migration | All → `@RequiresCapability` | Single authorization model, custom role support |
| `/bff/me` response | Identity only (no orgRole) | Clean authn/authz separation |
| Invitation role assignment | `PendingInvitation` DB record | Full decoupling from Keycloak role attributes |
| `members.org_role` VARCHAR | Drop column, `org_role_id` FK only | No live data, single source of truth |
| `ClerkJwtUtils` | Rename to `JwtUtils`, strip Clerk v2 + org_role | Clerk removed in Phase 20, clean slate |
| Gateway admin operations | Move `KeycloakAdminClient` to backend | Backend owns entire member lifecycle |
| Migration strategy | Layer-by-layer (backend → gateway → frontend) | Matches proven workflow, independent testing per layer |

---

## Architecture After Migration

```
┌─────────────┐     OIDC      ┌──────────┐    SESSION     ┌─────────┐
│  Keycloak   │◄──────────────│ Gateway  │◄───────────────│Frontend │
│ (authn only)│  (identity +  │  (BFF)   │   /bff/me =    │(Next.js)│
│             │  org membership)          │   identity only │         │
└─────────────┘               └────┬─────┘                └────┬────┘
                                   │ TokenRelay                │
                                   ▼                           │
                              ┌─────────┐  /api/me/caps       │
                              │ Backend │◄─────────────────────┘
                              │(Spring) │
                              │         │
                              │ MemberFilter:                  │
                              │   JWT sub → find Member in DB  │
                              │   DB org_role_id → OrgRole     │
                              │   Capabilities → ScopedValue   │
                              │                                │
                              │ Authorization:                 │
                              │   @RequiresCapability (DB)     │
                              │   RequestScopes.requireOwner() │
                              └─────────┘
```

### Token flow (post-migration)

```
1. User authenticates with Keycloak → JWT issued
2. JWT contains: sub (userId), organization (org membership), groups (platform-admin), email
3. JWT does NOT contain: org_role (removed)
4. Gateway stores token in session, relays to backend on /api/** calls
5. Backend TenantFilter: JWT organization claim → resolve tenant schema → bind TENANT_ID
6. Backend MemberFilter: JWT sub → find Member in DB → read orgRoleId FK → resolve capabilities → bind ORG_ROLE + CAPABILITIES
7. Controllers: @RequiresCapability checks RequestScopes.CAPABILITIES
8. Frontend: /bff/me for identity, /api/me/capabilities for authorization
```

---

## Section 1: Backend — Role Resolution & Authorization

### 1.1 DB-authoritative role resolution

**`MemberFilter`** changes:

- Remove the `jwtHasExplicitRole` branch that prefers JWT `org_role` over DB.
- Role resolution path becomes: JWT `sub` → lookup `Member` in DB → read `member.orgRoleId` FK → load `OrgRole` entity → bind `orgRole.getSlug()` to `RequestScopes.ORG_ROLE`.
- Capabilities resolved via `OrgRoleService.resolveCapabilities(memberId)` (unchanged).
- Cache (Caffeine, 1-hour TTL) keyed by `tenantId + clerkUserId` (unchanged).

**`ClerkJwtAuthenticationConverter`** changes:

- Stop mapping JWT `org_role` → `ROLE_ORG_*` GrantedAuthorities.
- Grant `ROLE_AUTHENTICATED` for all valid JWT bearers.
- During migration (Epics 1-4): also grant `ROLE_ORG_*` from DB-sourced role for backward compatibility with un-migrated `@PreAuthorize` annotations.
- After migration (Epic 5): remove `ROLE_ORG_*` grants entirely.

### 1.2 @PreAuthorize → @RequiresCapability migration

**Scope:** ~253 `@PreAuthorize` annotations across ~63 source files (controllers + services). 29 of these files already have `@RequiresCapability` annotations — those need de-duplication (remove `@PreAuthorize`, keep `@RequiresCapability`).

**General mapping rules:**

| Current pattern | New pattern | Notes |
|---|---|---|
| `hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')` | Remove annotation | `MemberFilter` ensures only members reach `/api/**` controllers |
| `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` | `@RequiresCapability(...)` — see domain mapping below | Map to domain-specific capability |
| `hasRole('ORG_OWNER')` | `RequestScopes.requireOwner()` | ~5 truly owner-only endpoints (delete org role, transfer ownership) |

**Domain-specific capability mapping:**

| Controller / Service domain | Capability | Examples |
|---|---|---|
| Invoice, InvoiceLine, InvoiceCounter | `INVOICING` | createInvoice, updateInvoice, sendInvoice |
| Customer, CustomerChecklist, DataRequest | `CUSTOMER_MANAGEMENT` | createCustomer, updateChecklist |
| Project, ProjectMember, Task, RecurringSchedule | `PROJECT_MANAGEMENT` | createProject, assignMember, createTask |
| OrgMember, OrgRole, MemberCapabilities | `TEAM_OVERSIGHT` | inviteMember, assignRole, updateCapabilities |
| BillingRate, CostRate, Budget, Report, Profitability | `FINANCIAL_VISIBILITY` | viewRates, updateBudget, viewProfitability |
| Automation, AutomationRule | `AUTOMATIONS` | createRule, updateRule, deleteRule |
| ResourcePlanning, LeaveBlock, Capacity | `RESOURCE_PLANNING` | createLeaveBlock, viewCapacity |
| Comment (own comments) | Remove annotation | Members can manage own comments |
| TimeEntry (own entries) | Remove annotation | Members can manage own time entries |
| Notification, NotificationPreference (own) | Remove annotation | Members manage own notifications |
| OrgSettings, Branding, Template | `TEAM_OVERSIGHT` | Org-wide settings are admin operations |
| Document, GeneratedDocument | Inherit from parent resource | Document on a project → `PROJECT_MANAGEMENT` |
| AuditEvent (read) | `TEAM_OVERSIGHT` | Only admins view audit logs |
| Expense | `FINANCIAL_VISIBILITY` | Financial data |

**Non-controller `@PreAuthorize` usage (do NOT migrate):**

| File | Keep/Migrate | Reason |
|---|---|---|
| `SecurityConfig.java` `authorizeHttpRequests()` | Keep as-is | HTTP-level path security, not role-based |
| `PlatformAdminFilter.java` | Keep as-is | Uses JWT `groups` claim, not org roles |
| `Roles.java` | N/A | Constants only, no annotations |

**`RequestScopes.requireOwner()`** — new convenience method:
```java
public static void requireOwner() {
    if (!"owner".equals(getOrgRole())) {
        throw new ForbiddenException("Only the organization owner can perform this action");
    }
}
```

Used for owner-only operations where capability-based access would be inappropriate. `requireOwner()` is intentionally slug-based (structural identity) — "owner" is a unique system role representing organizational control. There is exactly one owner per org. Custom roles with equivalent permissions would still not pass this check, which is by design.

### 1.3 Drop `members.org_role` VARCHAR column

New Flyway migration (tenant schema):

```sql
-- Backfill org_role_id for any members with NULL org_role_id
UPDATE members m
SET org_role_id = (SELECT id FROM org_roles WHERE slug = m.org_role AND is_system = true)
WHERE m.org_role_id IS NULL;

-- Make org_role_id NOT NULL
ALTER TABLE members ALTER COLUMN org_role_id SET NOT NULL;

-- Drop the legacy VARCHAR column
ALTER TABLE members DROP COLUMN org_role;
```

**`Member.java`** entity changes:
- Remove `orgRole` String field.
- `orgRoleId` / `orgRoleEntity` relationship becomes the only role accessor.
- Add convenience: `member.getRoleSlug()` → `member.getOrgRoleEntity().getSlug()`.

### 1.4 Rename `ClerkJwtUtils` → `JwtUtils`

- Remove all Clerk v2 format extraction (`o.id`, `o.rol`, `o.slg`).
- Remove `extractOrgRole()` method entirely — JWT no longer carries role.
- Keep: `extractOrgId()` (from `organization` claim), `extractOrgSlug()`, `extractGroups()`, `extractEmail()`.
- Only handles Keycloak token format.

### 1.5 Cache eviction on role changes

**Eviction mechanism:** Extract the Caffeine cache from `MemberFilter` into a standalone `MemberCacheService` bean. Both `MemberFilter` and `OrgRoleService` inject `MemberCacheService`. This avoids a circular dependency between `MemberFilter` ↔ `OrgRoleService`.

```java
@Service
public class MemberCacheService {
    private final Cache<String, MemberInfo> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(1))
        .build();

    public MemberInfo get(String tenantId, String userId) { ... }
    public void put(String tenantId, String userId, MemberInfo info) { ... }
    public void evict(String tenantId, String userId) { ... }
    public void evictAllForRole(UUID orgRoleId, MemberRepository memberRepo) {
        memberRepo.findAllByOrgRoleId(orgRoleId)
            .forEach(m -> evict(RequestScopes.requireTenantId(), m.getClerkUserId()));
    }
}
```

**Eviction coverage:**

| Mutation | Eviction |
|---|---|
| `OrgRoleService.assignRole(memberId, orgRoleId, overrides)` | `memberCacheService.evict(tenantId, userId)` |
| `OrgRoleService.updateRole(roleId, capabilities)` | `memberCacheService.evictAllForRole(roleId, memberRepo)` |
| `OrgRoleService.deleteRole(roleId)` | Blocked if any members assigned (existing behavior) |
| `MemberSyncService.syncMember()` | `memberCacheService.evict(tenantId, userId)` (existing behavior, rewired) |

### 1.6 Migrate OrgRoleService internal usage

**Must happen before dropping the VARCHAR column (1.3).**

`OrgRoleService` and other services currently read `member.getOrgRole()` (the VARCHAR string). These must be updated to `member.getOrgRoleEntity().getSlug()` before the column is dropped:

| File | Current usage | Migration |
|---|---|---|
| `OrgRoleService.assignRole()` | `"owner".equals(member.getOrgRole())` | `"owner".equals(member.getRoleSlug())` |
| `OrgRoleService.resolveCapabilities()` | Fallback when `orgRoleId == null` reads `orgRole` string | Remove fallback — `orgRoleId` will be NOT NULL |
| `OrgRoleService.displayName()` | Reads `orgRole` string | Read from `orgRoleEntity.getName()` |
| `ProjectAccessService` | `Roles.ORG_OWNER.equals(actor.orgRole())` | Unchanged — `ActorContext.orgRole()` reads from `RequestScopes.ORG_ROLE` |
| `ActorContext.fromRequestScopes()` | Reads `RequestScopes.ORG_ROLE` | Unchanged — `ORG_ROLE` ScopedValue is bound from DB slug |

---

## Section 2: Backend — PendingInvitation Entity

### 2.1 Schema

New Flyway migration (tenant schema):

```sql
CREATE TABLE pending_invitations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    org_role_id     UUID NOT NULL REFERENCES org_roles(id),
    invited_by      UUID NOT NULL REFERENCES members(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    accepted_at     TIMESTAMP WITH TIME ZONE
);

-- Partial unique index: one active invitation per email per tenant
CREATE UNIQUE INDEX uq_pending_invitation_email_pending
    ON pending_invitations(email) WHERE (status = 'PENDING');
```

**Status values:** `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED`

Partial unique index ensures only one active (PENDING) invitation per email per tenant.

### 2.2 Entity

```java
@Entity
@Table(name = "pending_invitations")
public class PendingInvitation {
    @Id @GeneratedValue UUID id;
    String email;
    @ManyToOne OrgRole orgRole;
    @ManyToOne Member invitedBy;
    String status;          // PENDING, ACCEPTED, EXPIRED, REVOKED
    Instant expiresAt;
    Instant createdAt;
    Instant acceptedAt;
}
```

### 2.3 InvitationService

```java
@Service
public class InvitationService {
    // Create invitation: save PendingInvitation + call Keycloak to send email
    PendingInvitation invite(String email, UUID orgRoleId);

    // Lookup for MemberFilter during lazy-create
    Optional<PendingInvitation> findPendingByEmail(String email);

    // Accept: called by MemberFilter on successful lazy-create
    void markAccepted(UUID invitationId);

    // Revoke: called by admin
    void revoke(UUID invitationId);

    // List: admin view of pending/recent invitations
    List<PendingInvitation> listForOrg();
}
```

### 2.4 MemberFilter integration

The lazy-create path changes:

```
No existing Member found for userId in tenant:
  1. Extract email from JWT claims
  2. invitationService.findPendingByEmail(email)
  3. If found and not expired:
       → Create Member with invitation.orgRole
       → invitationService.markAccepted(invitation.id)
  4. If not found:
       → Create Member with system "member" role
  5. First member in new tenant (no other members exist):
       → Promote to "owner" (founding user logic, unchanged)
```

### 2.5 Admin endpoints

| Endpoint | Guard | Purpose |
|---|---|---|
| `POST /api/invitations` | `@RequiresCapability("TEAM_OVERSIGHT")` | Create invitation + trigger Keycloak email |
| `GET /api/invitations` | `@RequiresCapability("TEAM_OVERSIGHT")` | List pending/recent invitations |
| `DELETE /api/invitations/{id}` | `@RequiresCapability("TEAM_OVERSIGHT")` | Revoke a pending invitation |

---

## Section 3: Backend — Move KeycloakAdminClient

### 3.1 Relocate from gateway to backend

Move `KeycloakAdminClient.java` from `gateway/src/main/java/.../gateway/keycloak/` to `backend/src/main/java/.../security/keycloak/`.

Configuration properties (`keycloak.admin.*`) move to backend's `application.yml`:
```yaml
keycloak:
  admin:
    server-url: http://keycloak:8180
    realm: docteams
    username: ${KEYCLOAK_ADMIN_USERNAME}
    password: ${KEYCLOAK_ADMIN_PASSWORD}
```

### 3.2 Operations that move to backend

| Operation | Current location | New location |
|---|---|---|
| Send invitation email | Gateway `/bff/admin/invite` | Backend `InvitationService.invite()` |
| Create organization | Gateway `/bff/orgs` | Backend `POST /api/orgs` |
| Update member role in KC | Gateway `KeycloakAdminClient.updateMemberRole()` | **Removed** — roles are DB-only |
| Set user attribute (org_role) | Gateway `KeycloakAdminClient.setUserAttribute()` | **Removed** — no longer needed |

### 3.3 Gateway retains

- OAuth2 login/logout flow (Spring Security OAuth2 Client)
- Session management (SESSION cookie)
- TokenRelay filter
- `/bff/me` (identity extraction from OIDC session)
- CORS configuration

### 3.4 New backend endpoints

| Endpoint | Guard | Purpose |
|---|---|---|
| `POST /api/orgs` | `RequestScopes.isPlatformAdmin()` or self-service flag | Create Keycloak org + provision tenant schema |

---

## Section 4: Gateway — Strip Authorization

### 4.1 `/bff/me` response change

**`BffUserInfoExtractor`** removes `orgRole` from extraction and response:

```java
// BEFORE
return new BffUserInfo(userId, email, name, orgId, orgSlug, orgRole, groups);

// AFTER
return new BffUserInfo(userId, email, name, orgId, orgSlug, groups);
```

`BffUserInfo` DTO: remove `orgRole` field.

### 4.2 Remove authorization endpoints

- Delete `POST /bff/admin/invite` endpoint and handler.
- Delete `POST /bff/orgs` endpoint and handler.
- Delete `BffSecurity` class (role checks on OIDC claims).
- Delete `KeycloakAdminClient.java` from gateway module.

### 4.3 Remaining gateway routes

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: backend-api
          uri: http://backend:8080
          predicates:
            - Path=/api/**
          filters:
            - TokenRelay=
        - id: bff-me
          # Handled by BffController directly
        - id: bff-logout
          # Handled by BffController directly
```

---

## Section 5: Frontend — Capabilities-Only Authorization

### 5.1 Type changes

**`lib/auth/types.ts`:**
```typescript
// Remove orgRole from AuthContext
export interface AuthContext {
  userId: string;
  orgId: string;
  orgSlug: string;
  groups: string[];
  // orgRole: string — REMOVED
}
```

### 5.2 Provider changes

**`keycloak-bff.ts`:**
- `getAuthContext()` returns identity only from `/bff/me`.
- Remove `requireRole()` function entirely.

**`mock/server.ts`:**
- Parse Keycloak-format token (no `o.rol` field).
- Return identity only.

### 5.3 Authorization pattern migration

All files that read `orgRole` from `getAuthContext()` switch to `fetchMyCapabilities()`:

```typescript
// BEFORE (20+ files)
const { orgRole } = await getAuthContext();
const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

// AFTER
const caps = await fetchMyCapabilities();
const isAdmin = caps.isAdmin;
```

These are defense-in-depth only — the backend enforces all authorization via `@RequiresCapability`.

### 5.4 Mock IDP token format

**`compose/mock-idp/src/index.ts`:**

```typescript
// BEFORE (Clerk v2 format)
const payload = {
  sub: userId,
  v: 2,
  o: { id: orgId, rol: orgRole, slg: orgSlug }
};

// AFTER (Keycloak format)
const payload = {
  sub: userId,
  organization: [orgSlug],
  groups: userGroups[userId] || [],
  email: userEmails[userId]
};
```

**`e2e/fixtures/auth.ts`:**
- `loginAs()` drops `orgRole` parameter from mock IDP `/token` request.
- Role for E2E users comes from seed data in the DB (existing E2E seed script).

### 5.5 Files impacted

**Auth core (~5 files):**
| File | Change |
|---|---|
| `lib/auth/types.ts` | Remove `orgRole` from `AuthContext` |
| `lib/auth/server.ts` | Update re-exports |
| `lib/auth/providers/keycloak-bff.ts` | Remove `orgRole` extraction, delete `requireRole()` |
| `lib/auth/providers/mock/server.ts` | Parse Keycloak format, remove role |
| `lib/auth/middleware.ts` | No change (checks cookies, not roles) |

**Pages and layouts (~12 files):**
| File | Change |
|---|---|
| `app/.../settings/layout.tsx` | `getAuthContext()` → `fetchMyCapabilities()` |
| `app/.../customers/[id]/page.tsx` | Same |
| All pages deriving `isAdmin` from `orgRole` | Same pattern |

**Server actions (~38 files):**
All `app/(app)/org/[slug]/*/actions.ts` files that check `orgRole` switch to `caps.isAdmin`.

**Components (~5 files):**
| File | Change |
|---|---|
| `components/auth/mock-login-form.tsx` | Remove role from token request |
| `components/settings/settings-sidebar.tsx` | `isAdmin` prop sourced from capabilities |

**Note on client components:** `fetchMyCapabilities()` is marked `"server-only"`. Client components (dialogs, sidebars) cannot call it directly. The existing pattern — Server Components fetch capabilities and pass `isAdmin`/`isOwner` as props or via `CapabilityProvider` context — continues to work. No new client-side fetching is needed.

**E2E and mock (~3 files):**
| File | Change |
|---|---|
| `e2e/fixtures/auth.ts` | Drop `orgRole` param |
| `compose/mock-idp/src/index.ts` | Keycloak token format |
| `components/auth/mock-login-form.tsx` | Already counted above |

---

## Section 6: Epic Breakdown & Migration Order

Layer-by-layer, bottom-up. Each epic is independently deployable.

### Epic 1: Backend — DB-Authoritative Roles

| Slice | Scope | Files |
|---|---|---|
| **1A** | PendingInvitation entity, repository, service, controller, migration. Extract `MemberCacheService` from `MemberFilter`. | ~10 new files, 1 migration |
| **1B** | MemberFilter reads role from DB only. Grant `ROLE_ORG_*` from DB role (backward compat). Remove JWT `org_role` preference. Migrate `OrgRoleService` internal usage from `member.getOrgRole()` string to `member.getRoleSlug()`. | ~6 files |
| **1C** | Migrate `@PreAuthorize` → `@RequiresCapability` across all controllers and services (~253 annotations, ~63 files). De-duplicate 29 files that already have `@RequiresCapability`. Add `RequestScopes.requireOwner()`. Leave `SecurityConfig` path-level and `PlatformAdminFilter` group-based checks untouched. | ~63 files (mechanical) |
| **1D** | Drop `members.org_role` VARCHAR column, make `org_role_id` NOT NULL. Update `Member.java` entity — remove `orgRole` string field, add `getRoleSlug()` convenience. | ~5 files, 1 migration |
| **1E** | Rename `ClerkJwtUtils` → `JwtUtils`, strip Clerk v2 format + `extractOrgRole()`. Update all imports. | ~8 files (rename + update imports) |

### Epic 2: Backend — Move KeycloakAdminClient

| Slice | Scope | Files |
|---|---|---|
| **2A** | Copy `KeycloakAdminClient` to backend, add config properties, wire `InvitationService` to send Keycloak emails. | ~5 files |
| **2B** | Add `POST /api/orgs` endpoint to backend. Move org creation logic from gateway. | ~3 files |

### Epic 3: Gateway — Strip Authorization

| Slice | Scope | Files |
|---|---|---|
| **3A** | `/bff/me` returns identity only. Remove `orgRole` from `BffUserInfo` and `BffUserInfoExtractor`. | ~3 files |
| **3B** | Remove `/bff/admin/invite`, `/bff/orgs` endpoints. Delete `AdminProxyController`, `BffSecurity`. | ~5 files |
| **3C** | Delete `KeycloakAdminClient` from gateway module. Clean up unused config and Keycloak admin dependencies. | ~4 files |

### Epic 4: Frontend — Capabilities-Only Authorization

| Slice | Scope | Files |
|---|---|---|
| **4A** | Remove `orgRole` from `AuthContext` type + both providers. Delete `requireRole()`. | ~5 files |
| **4B** | Migrate all `orgRole` checks to `fetchMyCapabilities()` in layouts, pages, server actions. Note: `fetchMyCapabilities()` is `"server-only"` — client components receive `isAdmin`/`isOwner` as props from Server Components (existing pattern via `CapabilityProvider`). | ~50 files (mechanical, includes ~38 action files + ~12 page/layout files) |
| **4C** | Update mock IDP token format + E2E fixtures. Update mock provider parser. | ~4 files |

### Epic 5: Cleanup

| Slice | Scope | Files |
|---|---|---|
| **5A** | Remove `ROLE_ORG_*` authority grants from `MemberFilter` / converter. Remove `Roles.AUTHORITY_*` constants. Update backend integration test JWT mocks that grant `ROLE_ORG_*` authorities — replace with capability-based test setup. | ~15 files (4 source + ~11 test files) |
| **5B** | Remove `MemberSyncService` webhook role sync path (roles no longer come from IDP). Clean up unused role sync audit events. | ~3 files |
| **5C** | Update `backend/CLAUDE.md` and `frontend/CLAUDE.md` to reflect new conventions: `@RequiresCapability` replaces `@PreAuthorize`, `JwtUtils` replaces `ClerkJwtUtils`, capabilities-only frontend auth pattern. Remove references to Clerk JWT v2 format and `o.rol` claim structure. | ~2 files |

### Dependencies

```
Epic 1 (all slices sequential: 1A → 1B → 1C → 1D → 1E)
  ↓
Epic 2 (2A → 2B, can parallel with Epic 1D/1E)
  ↓
Epic 3 (3A → 3B → 3C, depends on Epic 2)
  ↓
Epic 4 (4A → 4B → 4C, depends on Epic 3A)
  ↓
Epic 5 (5A → 5B → 5C, depends on Epic 4)
```

**Note on E2E test continuity:** The mock IDP token format changes in Epic 4C. Between Epics 3A (gateway drops `orgRole` from `/bff/me`) and 4C (mock IDP updated), E2E tests that depend on `orgRole` from `/bff/me` will fail. This is acceptable because: (a) Epic 4A removes `orgRole` from `AuthContext` in the same PR window, and (b) Epics 3-4 are deployed together in practice.

### Safety invariants

- **No lockouts during migration:** `MemberFilter` grants `ROLE_ORG_*` from DB throughout Epics 1-4. Only removed in Epic 5A after all `@PreAuthorize` annotations are confirmed gone.
- **Backend tests pass after each slice:** Every `@RequiresCapability` migration is covered by existing integration tests (830+ backend tests). A missed migration surfaces as a 403 in tests.
- **Frontend is last:** Gateway and frontend changes only happen after backend is fully migrated and tested.
- **E2E validates end-to-end:** After Epic 4, full Playwright suite on E2E stack confirms the complete flow works with DB-only roles.

### Rollback strategy

Each epic is independently deployable. If Epic 3 causes issues:
- Revert gateway changes.
- Frontend still works (still reading `orgRole` from `/bff/me` until Epic 4).
- Backend is already on DB-authoritative roles but that's internal — no external contract broken.

---

## Security Considerations

### Preserved

- **JWT signature validation** — Keycloak JWKS endpoint validates all tokens (unchanged).
- **Schema-per-tenant isolation** — roles live in tenant schemas, zero cross-tenant leakage.
- **Defense-in-depth** — frontend checks are UX guards, backend `@RequiresCapability` is the security boundary.
- **Platform-admin group check** — `groups` JWT claim still used for platform-level operations.
- **Owner protection** — cannot change owner's role or assign system owner role (existing logic in `OrgRoleService`).

### Improved

- **No stale JWT roles** — role changes take effect on next request (DB + cache eviction), not on next token refresh.
- **No Keycloak org role bugs** — CVE-2026-1529 and multi-org claim issues become irrelevant.
- **Single authorization model** — `@RequiresCapability` everywhere, no confusion between JWT role checks and DB capability checks.

### Cache eviction (critical)

Implemented via `MemberCacheService` (extracted from `MemberFilter` to avoid circular dependencies). See Section 1.5.

| Mutation | Eviction scope |
|---|---|
| `assignRole(memberId, orgRoleId)` | Evict that member |
| `updateRole(roleId, capabilities)` | Evict ALL members with that role |
| `deleteRole(roleId)` | Blocked if members assigned |
| Webhook member sync | Evict that member (existing, rewired to `MemberCacheService`) |

### Risks mitigated

| Risk | Mitigation |
|---|---|
| Missed `@PreAuthorize` migration → silent 403 | `ROLE_ORG_*` granted from DB during migration; only removed in final cleanup slice after all annotations confirmed gone. 830+ integration tests catch regressions. |
| Invitation race (user arrives before record committed) | Invitation is synchronous admin action; user arrives via email link seconds-to-hours later. If invitation deleted/expired, user gets default "member" role. |
| Cache staleness after role change | Explicit eviction on all mutation paths. 1-hour TTL as safety net. |
| `MemberFilter` bypassed | Applied to all `/api/**` routes by `SecurityConfig`. Portal routes use separate `CustomerAuthFilter`. Internal routes use `ApiKeyAuthFilter`. No unprotected paths. |

---

## Open Questions (Resolved)

**Q1: Keycloak invitation emails — does Keycloak have an API for sending emails independent of org role assignment?**

Yes. Keycloak 26+ has `POST /admin/realms/{realm}/organizations/{orgId}/members/invite-user` which sends an invitation email with a redirect URL. The role assignment is a separate step (`/organization-roles/grant`). The backend's `InvitationService` calls only the invite-user endpoint (identity onboarding) and stores the role in the product DB. If Keycloak's invitation email proves inadequate, the backend can send its own email using the existing email infrastructure (Mailpit in dev).

**Q2: POST /api/orgs — does the backend need Keycloak Admin SDK dependency?**

Yes. The backend will add a dependency on `keycloak-admin-client` (the official Keycloak Java Admin Client library). This is the same library the gateway currently uses. Configuration properties (`keycloak.admin.*`) move to the backend's `application.yml`. This adds ~1 new Maven dependency and a configuration block.

**Q3: Cache TTL — is 1-hour stale data acceptable if eviction fails silently?**

The 1-hour TTL is a safety net, not the primary eviction mechanism. `MemberCacheService` evicts explicitly on all mutation paths. If eviction fails (e.g., the service throws before eviction), the mutation itself also failed (transactional), so the cache is still consistent. The only scenario where stale data persists is if the eviction call succeeds but the cache entry is somehow not removed — this is a Caffeine bug, not a design issue. For security-sensitive operations (owner-only checks), the check reads `RequestScopes.ORG_ROLE` which is populated from the cached value — a stale "admin" could not pass a `requireOwner()` check because it compares against "owner" specifically.
