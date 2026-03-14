# RBAC Decoupling Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple authorization from Keycloak Organization Roles — make the product database the sole RBAC authority.

**Architecture:** Layer-by-layer migration (backend → gateway → frontend). Backend becomes DB-authoritative for roles while maintaining backward compatibility via `ROLE_ORG_*` Spring Security authorities during migration. Gateway strips role from `/bff/me`. Frontend switches from `orgRole` auth context to capabilities-only. Final cleanup removes all legacy role machinery.

**Tech Stack:** Spring Boot 4, Spring Security, Hibernate, Flyway, Caffeine cache, Next.js 16, TypeScript

**Spec:** `docs/superpowers/specs/2026-03-14-rbac-decoupling-design.md`

---

## File Map

### New Files (Backend)

| File | Purpose |
|---|---|
| `backend/.../invitation/PendingInvitation.java` | Entity for pending member invitations |
| `backend/.../invitation/PendingInvitationRepository.java` | JPA repository |
| `backend/.../invitation/InvitationService.java` | Invitation lifecycle (create, accept, revoke, list) |
| `backend/.../invitation/InvitationController.java` | REST endpoints for invitation management |
| `backend/.../invitation/InvitationRequest.java` | Request DTO |
| `backend/.../invitation/InvitationResponse.java` | Response DTO |
| `backend/.../member/MemberCacheService.java` | Extracted Caffeine cache from MemberFilter |
| `backend/.../security/keycloak/KeycloakAdminClient.java` | Moved from gateway module |
| `backend/.../security/keycloak/KeycloakAdminProperties.java` | Config properties for Keycloak admin |
| `backend/.../security/JwtUtils.java` | Renamed from ClerkJwtUtils |
| `backend/.../resources/db/migration/tenant/V68__create_pending_invitations.sql` | Migration |
| `backend/.../resources/db/migration/tenant/V69__drop_members_org_role_column.sql` | Migration |

### Modified Files (Backend — Key)

| File | Change |
|---|---|
| `backend/.../member/MemberFilter.java` | Remove JWT role preference, use DB only, delegate cache to MemberCacheService |
| `backend/.../member/Member.java` | Remove `orgRole` VARCHAR field, add `getRoleSlug()` |
| `backend/.../member/MemberSyncService.java` | Rewire cache eviction to MemberCacheService |
| `backend/.../security/ClerkJwtAuthenticationConverter.java` | Stop reading org_role from JWT, grant ROLE_AUTHENTICATED |
| `backend/.../security/Roles.java` | Remove AUTHORITY_* constants (Epic 5) |
| `backend/.../orgrole/OrgRoleService.java` | Migrate from `member.getOrgRole()` to `member.getRoleSlug()`, add cache eviction |
| `backend/.../multitenancy/RequestScopes.java` | Add `requireOwner()` method |
| `~63 controller/service files` | `@PreAuthorize` → `@RequiresCapability` (mechanical) |

### Modified Files (Gateway)

| File | Change |
|---|---|
| `gateway/.../controller/BffController.java` | Remove `orgRole` from `BffUserInfo`, delete `/bff/orgs` |
| `gateway/.../controller/BffUserInfoExtractor.java` | Remove orgRole extraction |
| `gateway/.../controller/AdminProxyController.java` | Delete entire file |
| `gateway/.../controller/BffSecurity.java` | Delete entire file |
| `gateway/.../service/KeycloakAdminClient.java` | Delete (moved to backend) |

### Modified Files (Frontend)

| File | Change |
|---|---|
| `frontend/lib/auth/types.ts` | Remove `orgRole` from `AuthContext` |
| `frontend/lib/auth/providers/keycloak-bff.ts` | Remove orgRole extraction, delete `requireRole()` |
| `frontend/lib/auth/providers/mock/server.ts` | Parse Keycloak-format token |
| `frontend/lib/types/member.ts` | Remove `orgRole` from interfaces |
| `compose/mock-idp/src/index.ts` | Keycloak token format |
| `frontend/e2e/fixtures/auth.ts` | Drop orgRole from loginAs() |
| `~76 page/action/component files` | `orgRole` checks → `fetchMyCapabilities()` |

---

## Chunk 1: Epic 1 — Slices 1A & 1B (Backend Foundation)

### Task 1: Extract MemberCacheService from MemberFilter (Slice 1A)

**Files:**
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberCacheService.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java`

- [ ] **Step 1: Create MemberCacheService**

Create a new service that owns the Caffeine cache currently embedded in MemberFilter. This must be done first to avoid circular dependencies when OrgRoleService needs to evict cache entries.

```java
package io.b2mash.b2b.b2bstrawman.member;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.UUID;

@Service
public class MemberCacheService {

    // MemberInfo record — previously nested in MemberFilter
    public record MemberInfo(UUID memberId, String orgRole) {}

    private final Cache<String, MemberInfo> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    private static String key(String tenantId, String userId) {
        return tenantId + ":" + userId;
    }

    public MemberInfo get(String tenantId, String userId) {
        return cache.getIfPresent(key(tenantId, userId));
    }

    public void put(String tenantId, String userId, MemberInfo info) {
        cache.put(key(tenantId, userId), info);
    }

    public void evict(String tenantId, String userId) {
        cache.invalidate(key(tenantId, userId));
    }

    public void evictAll() {
        cache.invalidateAll();
    }
}
```

- [ ] **Step 2: Update MemberFilter to use MemberCacheService**

In `MemberFilter.java`:
- Remove the `Cache<String, MemberInfo> memberCache` field and the `MemberInfo` record definition
- Inject `MemberCacheService` instead
- Replace all `memberCache.getIfPresent(key)` → `memberCacheService.get(tenantId, userId)`
- Replace all `memberCache.put(key, info)` → `memberCacheService.put(tenantId, userId, info)`
- Replace `evictFromCache(tenantId, userId)` method body with `memberCacheService.evict(tenantId, userId)` (keep the public method as a delegate for now — MemberSyncService calls it)

- [ ] **Step 3: Update MemberSyncService to use MemberCacheService**

In `MemberSyncService.java`:
- Add `MemberCacheService` injection alongside existing `MemberFilter` injection
- Replace `memberFilter.evictFromCache(schemaName, clerkUserId)` at line 136 with `memberCacheService.evict(schemaName, clerkUserId)`
- Keep `MemberFilter` injection for now (it may still be used elsewhere)

- [ ] **Step 4: Run backend tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass (no behavior change, just cache extraction refactor)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberCacheService.java \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java
git commit -m "refactor: extract MemberCacheService from MemberFilter"
```

---

### Task 2: Create PendingInvitation entity and migration (Slice 1A)

**Files:**
- Create: `backend/src/main/resources/db/migration/tenant/V68__create_pending_invitations.sql`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitation.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitationRepository.java`

- [ ] **Step 1: Create Flyway migration**

**Important:** Check the latest migration version before creating this file. Run: `ls backend/src/main/resources/db/migration/tenant/V*.sql | tail -1`. If the latest is higher than V67, adjust the version number accordingly. At time of writing, V67 is the latest.

```sql
-- V68__create_pending_invitations.sql
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

CREATE UNIQUE INDEX uq_pending_invitation_email_pending
    ON pending_invitations(email) WHERE (status = 'PENDING');
```

- [ ] **Step 2: Create PendingInvitation entity**

Follow existing entity patterns in the codebase (see `Member.java`, `OrgRole.java` for style).

```java
package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_invitations")
public class PendingInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_role_id", nullable = false)
    private OrgRole orgRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private Member invitedBy;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected PendingInvitation() {}

    public PendingInvitation(String email, OrgRole orgRole, Member invitedBy, Instant expiresAt) {
        this.email = email;
        this.orgRole = orgRole;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public OrgRole getOrgRole() { return orgRole; }
    public Member getInvitedBy() { return invitedBy; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return "PENDING".equals(status) && !isExpired();
    }

    public void markAccepted() {
        this.status = "ACCEPTED";
        this.acceptedAt = Instant.now();
    }

    public void markRevoked() {
        this.status = "REVOKED";
    }
}
```

- [ ] **Step 3: Create repository**

```java
package io.b2mash.b2b.b2bstrawman.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingInvitationRepository extends JpaRepository<PendingInvitation, UUID> {

    Optional<PendingInvitation> findByEmailAndStatus(String email, String status);

    List<PendingInvitation> findAllByStatusOrderByCreatedAtDesc(String status);

    List<PendingInvitation> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 4: Run backend tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass (entity added, not yet used)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/V68__create_pending_invitations.sql \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/
git commit -m "feat: add PendingInvitation entity and migration (V68)"
```

---

### Task 3: Create InvitationService and InvitationController (Slice 1A)

**Files:**
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationService.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationController.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationRequest.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationResponse.java`
- Test: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationControllerTest.java`

- [ ] **Step 1: Write integration test for invitation CRUD**

Follow existing test patterns (see `OrgRoleControllerTest.java` for style). Test:
- POST /api/invitations with valid email + orgRoleId → 200, returns invitation
- POST /api/invitations with duplicate pending email → 409 conflict
- GET /api/invitations → 200, returns list
- DELETE /api/invitations/{id} → 204, marks as REVOKED
- Member role permission: non-admin gets 403

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -Dtest=InvitationControllerTest -q 2>&1 | tail -20`
Expected: FAIL — classes don't exist yet

- [ ] **Step 3: Create request/response DTOs**

`InvitationRequest.java`:
```java
package io.b2mash.b2b.b2bstrawman.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InvitationRequest(
    @Email @NotNull String email,
    @NotNull UUID orgRoleId
) {}
```

`InvitationResponse.java`:
```java
package io.b2mash.b2b.b2bstrawman.invitation;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
    UUID id,
    String email,
    String roleName,
    String status,
    Instant expiresAt,
    Instant createdAt,
    String invitedByName
) {
    public static InvitationResponse from(PendingInvitation inv) {
        return new InvitationResponse(
            inv.getId(),
            inv.getEmail(),
            inv.getOrgRole().getName(),
            inv.getStatus(),
            inv.getExpiresAt(),
            inv.getCreatedAt(),
            inv.getInvitedBy().getName()
        );
    }
}
```

- [ ] **Step 4: Create InvitationService**

```java
package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class InvitationService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final PendingInvitationRepository invitationRepo;
    private final OrgRoleRepository orgRoleRepo;
    private final MemberRepository memberRepo;

    public InvitationService(PendingInvitationRepository invitationRepo,
                             OrgRoleRepository orgRoleRepo,
                             MemberRepository memberRepo) {
        this.invitationRepo = invitationRepo;
        this.orgRoleRepo = orgRoleRepo;
        this.memberRepo = memberRepo;
    }

    public PendingInvitation invite(String email, UUID orgRoleId) {
        // Check for existing pending invitation
        invitationRepo.findByEmailAndStatus(email, "PENDING").ifPresent(existing -> {
            if (existing.isPending()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending invitation already exists for " + email);
            }
        });

        OrgRole role = orgRoleRepo.findById(orgRoleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));

        Member inviter = memberRepo.findById(RequestScopes.requireMemberId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        var invitation = new PendingInvitation(email, role, inviter, Instant.now().plus(INVITATION_TTL));
        return invitationRepo.save(invitation);
    }

    @Transactional(readOnly = true)
    public Optional<PendingInvitation> findPendingByEmail(String email) {
        return invitationRepo.findByEmailAndStatus(email, "PENDING")
            .filter(PendingInvitation::isPending);
    }

    public void markAccepted(UUID invitationId) {
        invitationRepo.findById(invitationId).ifPresent(PendingInvitation::markAccepted);
    }

    public void revoke(UUID invitationId) {
        var invitation = invitationRepo.findById(invitationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        if (!"PENDING".equals(invitation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is not pending");
        }
        invitation.markRevoked();
    }

    @Transactional(readOnly = true)
    public List<PendingInvitation> listAll() {
        return invitationRepo.findAllByOrderByCreatedAtDesc();
    }
}
```

- [ ] **Step 5: Create InvitationController**

```java
package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    @RequiresCapability("TEAM_OVERSIGHT")
    public ResponseEntity<InvitationResponse> invite(@Valid @RequestBody InvitationRequest request) {
        var invitation = invitationService.invite(request.email(), request.orgRoleId());
        return ResponseEntity.ok(InvitationResponse.from(invitation));
    }

    @GetMapping
    @RequiresCapability("TEAM_OVERSIGHT")
    public ResponseEntity<List<InvitationResponse>> list() {
        var invitations = invitationService.listAll().stream()
            .map(InvitationResponse::from)
            .toList();
        return ResponseEntity.ok(invitations);
    }

    @DeleteMapping("/{id}")
    @RequiresCapability("TEAM_OVERSIGHT")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        invitationService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 6: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/ \
       backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/
git commit -m "feat: add InvitationService and InvitationController"
```

---

### Task 4: Make MemberFilter DB-authoritative for roles (Slice 1B)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtAuthenticationConverter.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberCacheService.java`

- [ ] **Step 1: Update MemberFilter — remove JWT role preference**

In `MemberFilter.java`, modify the role resolution logic (around lines 100-124):
- Remove the branch that calls `ClerkJwtUtils.extractOrgRole(jwt)` and prefers it over DB
- Always read role from `member.getOrgRole()` (the DB-stored value)
- Update the lazy-create path to check `InvitationService.findPendingByEmail()` instead of using JWT `org_role`
- Inject `InvitationService` into MemberFilter

The lazy-create method should now:
1. Extract email from JWT claims
2. Call `invitationService.findPendingByEmail(email)`
3. If found and not expired → create member with `invitation.getOrgRole()`, call `invitationService.markAccepted()`
4. If not found → create member with system "member" role
5. First member in tenant → promote to "owner" (existing logic)

- [ ] **Step 2: Update ClerkJwtAuthenticationConverter — keep ROLE_ORG_* from JWT during migration**

The converter currently reads `org_role` from the JWT and maps it to `ROLE_ORG_*` Spring Security authorities. **During migration (Epics 1-4), keep this behavior unchanged** — `@PreAuthorize` annotations still exist and need `ROLE_ORG_*` authorities to function.

The only change in this step: add `ROLE_AUTHENTICATED` as an **additional** authority alongside the existing `ROLE_ORG_*` grants.

```java
// In extractAuthorities():
authorities.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
// Keep existing ROLE_ORG_* mapping from JWT — backward compat until Epic 5
```

The key behavioral change is in Step 1: `MemberFilter` now binds `RequestScopes.ORG_ROLE` from DB (not JWT). So `@RequiresCapability` (which reads `RequestScopes.CAPABILITIES`) uses the DB-sourced role, while `@PreAuthorize` (which reads Spring authorities from JWT) continues working during migration. The `ROLE_ORG_*` grants are removed in Task 16 (Epic 5A) after all `@PreAuthorize` annotations are gone.

- [ ] **Step 3: Update MemberCacheService.MemberInfo**

Update the `MemberInfo` record to store the role slug from the DB `OrgRole` entity, not the legacy `orgRole` string:

```java
public record MemberInfo(UUID memberId, String orgRoleSlug) {}
```

All callers that create `MemberInfo` must read from the `OrgRole` entity FK instead of `member.getOrgRole()`.

- [ ] **Step 4: Run backend tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass — behavior is equivalent since JWT role and DB role should be in sync for all test data

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtAuthenticationConverter.java \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberCacheService.java
git commit -m "feat: MemberFilter reads role from DB, not JWT (backward compat preserved)"
```

---

### Task 5: Migrate OrgRoleService from legacy orgRole string (Slice 1B)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java`

- [ ] **Step 1: Add getRoleSlug() convenience to Member.java**

Add a method that reads the role slug from the `OrgRole` entity relationship:

```java
public String getRoleSlug() {
    // During migration: prefer orgRoleId FK, fall back to legacy orgRole string
    if (orgRoleId != null) {
        // Requires the relationship to be initialized
        // For now, return the legacy value if entity not loaded
    }
    return orgRole;
}
```

**Note:** The exact implementation depends on whether `Member` has a `@ManyToOne OrgRole` relationship or just the `orgRoleId` UUID. Check the entity — if it only has `orgRoleId`, the service must load the `OrgRole` separately. Add the relationship if it doesn't exist:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "org_role_id", insertable = false, updatable = false)
private OrgRole orgRoleEntity;

public String getRoleSlug() {
    return orgRoleEntity != null ? orgRoleEntity.getSlug() : orgRole;
}
```

- [ ] **Step 2: Update OrgRoleService internal usage**

Per the spec Section 1.6:
- `assignRole()`: Change `"owner".equals(member.getOrgRole())` → `"owner".equals(member.getRoleSlug())`
- `resolveCapabilities()`: Remove the fallback that reads `orgRole` string when `orgRoleId == null`. After migration, `orgRoleId` will be NOT NULL.
- `displayName()`: Change to read from `orgRoleEntity.getName()`
- Add cache eviction: inject `MemberCacheService` and call `evict()` after `assignRole()`, and `evictAllForRole()` after `updateRole()` when capabilities change

- [ ] **Step 3: Add evictAllForRole to MemberCacheService**

```java
public void evictAllForRole(UUID orgRoleId, MemberRepository memberRepo) {
    String tenantId = RequestScopes.requireTenantId();
    memberRepo.findAllByOrgRoleId(orgRoleId)
        .forEach(m -> evict(tenantId, m.getClerkUserId()));
}
```

Add to `MemberRepository`:
```java
List<Member> findAllByOrgRoleId(UUID orgRoleId);
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberCacheService.java \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java
git commit -m "refactor: OrgRoleService uses orgRoleEntity instead of legacy orgRole string"
```

---

## Chunk 2: Epic 1 — Slices 1C, 1D, 1E (Backend Migration)

### Task 6: Add RequestScopes.requireOwner() and migrate @PreAuthorize (Slice 1C)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java`
- Modify: ~63 controller and service files (mechanical)

- [ ] **Step 1: Add requireOwner() to RequestScopes**

```java
public static void requireOwner() {
    if (!"owner".equals(getOrgRole())) {
        throw new io.b2mash.b2b.b2bstrawman.exception.ForbiddenException(
            "Only the organization owner can perform this action");
    }
}
```

- [ ] **Step 2: Migrate @PreAuthorize annotations — mechanical replacement**

This is a large mechanical task. Follow the domain-specific capability mapping from the spec Section 1.2:

**Pattern A — "any member" (remove annotation entirely):**
Find: `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")`
Action: Delete the annotation line. `MemberFilter` already ensures only org members reach `/api/**`.

**Pattern B — "admin or owner" (replace with domain capability):**
Find: `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")` OR `@PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN', 'ROLE_ORG_OWNER')")` (both variants exist)
Action: Replace with `@RequiresCapability("X")` where X is determined by the controller domain:

| Controller domain | Capability |
|---|---|
| Invoice, InvoiceLine, InvoiceCounter | `INVOICING` |
| Customer, CustomerChecklist, DataRequest | `CUSTOMER_MANAGEMENT` |
| Project, ProjectMember, Task, RecurringSchedule | `PROJECT_MANAGEMENT` |
| OrgMember, OrgRole, MemberCapabilities | `TEAM_OVERSIGHT` |
| BillingRate, CostRate, Budget, Report, Profitability, ReportingController | `FINANCIAL_VISIBILITY` |
| Automation, AutomationRule | `AUTOMATIONS` |
| ResourcePlanning, LeaveBlock, Capacity | `RESOURCE_PLANNING` |
| OrgSettings, Branding, Template, TemplateClause, Clause | `TEAM_OVERSIGHT` |
| AuditEvent | `TEAM_OVERSIGHT` |
| Onboarding, OnboardingController | `CUSTOMER_MANAGEMENT` |
| Retainer, RetainerAgreement, RetainerPeriod, RetainerSummary | `INVOICING` |
| Proposal, ProposalController | `INVOICING` (note: some Proposal endpoints use Pattern C — `hasRole('ORG_OWNER')`) |
| Calendar, CalendarController | `PROJECT_MANAGEMENT` |
| Prerequisite, PrerequisiteController | `PROJECT_MANAGEMENT` |
| FieldDefinition, FieldGroup | `TEAM_OVERSIGHT` |
| SavedView | Remove annotation (user-scoped views) |
| TaxRate | `FINANCIAL_VISIBILITY` |
| Dashboard | Remove annotation (member-accessible) |
| Integration, EmailAdmin | `TEAM_OVERSIGHT` |
| Expense | `FINANCIAL_VISIBILITY` |
| Comment (own), TimeEntry (own), Notification (own) | Remove annotation |
| Document, GeneratedDocument | Inherit from parent (project → `PROJECT_MANAGEMENT`) |

**Note:** Some controllers use `hasAnyAuthority(...)` instead of `hasAnyRole(...)`. These are functionally equivalent — apply the same mapping.

**Pattern C — "owner only" (~5 endpoints):**
Find: `@PreAuthorize("hasRole('ORG_OWNER')")`
Action: Remove the annotation, add `RequestScopes.requireOwner()` as the first line of the method body.

**De-duplication (29 files):**
Files that already have `@RequiresCapability`: remove `@PreAuthorize` but keep existing `@RequiresCapability`.

**Do NOT migrate:**
- `SecurityConfig.java` `authorizeHttpRequests()` path-level security
- `PlatformAdminFilter.java` group-based checks
- `PlatformAdminController` `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")` — these are group-based, not role-based

**Approach:** Process files alphabetically by package. For each file:
1. Read the file
2. Identify all `@PreAuthorize` annotations
3. Determine the domain from the class name/package
4. Apply the appropriate pattern (A, B, or C)
5. Remove the `@PreAuthorize` import if no longer used
6. Ensure `@RequiresCapability` import is present if added

- [ ] **Step 3: Run backend tests after each batch of ~10 files**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass after each batch. If a test fails with 403, the capability mapping was wrong — check the test's JWT setup and the controller's domain.

- [ ] **Step 4: Final full test run**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All 830+ tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: migrate @PreAuthorize to @RequiresCapability across all controllers"
```

---

### Task 7: Drop members.org_role VARCHAR column (Slice 1D)

**Files:**
- Create: `backend/src/main/resources/db/migration/tenant/V69__drop_members_org_role_column.sql`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java`

- [ ] **Step 1: Create migration**

```sql
-- V69__drop_members_org_role_column.sql
-- Backfill org_role_id for any members with NULL org_role_id
UPDATE members m
SET org_role_id = (SELECT id FROM org_roles WHERE slug = m.org_role AND is_system = true)
WHERE m.org_role_id IS NULL;

-- Make org_role_id NOT NULL
ALTER TABLE members ALTER COLUMN org_role_id SET NOT NULL;

-- Drop the legacy VARCHAR column
ALTER TABLE members DROP COLUMN org_role;
```

- [ ] **Step 2: Update Member.java entity**

- Remove `orgRole` String field and its `@Column` annotation
- Remove `getOrgRole()` and `setOrgRole()` methods
- Remove `orgRole` parameter from constructor(s)
- Update `updateFrom()` method to not accept `orgRole` parameter
- Update `getRoleSlug()` to no longer fall back to the deleted field:

```java
public String getRoleSlug() {
    return orgRoleEntity.getSlug();
}
```

- [ ] **Step 3: Fix all compilation errors**

After removing `member.getOrgRole()` / `member.setOrgRole()`, fix all callers:
- `MemberFilter.java` — use `member.getRoleSlug()` instead
- `MemberSyncService.java` — remove `orgRole` from `updateFrom()` call, set role via `member.setOrgRoleId()` + `orgRoleService.findSystemRoleBySlug()`
- Any test files that construct `Member` with `orgRole` string
- `MemberController` or any DTO that returns `orgRole` — return `member.getRoleSlug()` instead

- [ ] **Step 4: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: drop members.org_role VARCHAR column, org_role_id is sole authority (V69)"
```

---

### Task 8: Rename ClerkJwtUtils → JwtUtils (Slice 1E)

**Files:**
- Delete: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/JwtUtils.java`
- Modify: ~8 files that import ClerkJwtUtils

- [ ] **Step 1: Create JwtUtils.java**

Copy `ClerkJwtUtils.java` to `JwtUtils.java`. Then:
- Remove `extractOrgRole()` method entirely
- Remove `extractClerkClaim()` (private helper for Clerk v2 format)
- Remove `isClerkJwt()` method
- Remove Clerk v2 extraction paths from `extractOrgId()`, `extractOrgSlug()`
- Keep: `extractOrgId()` (Keycloak format only), `extractOrgSlug()`, `extractGroups()`, `extractEmail()`, `isKeycloakJwt()`, `isKeycloakFlatListFormat()`

- [ ] **Step 2: Update all imports**

Find all files that import `ClerkJwtUtils` and change to `JwtUtils`:
- `ClerkJwtAuthenticationConverter.java`
- `MemberFilter.java`
- `TenantFilter.java`
- `PlatformAdminFilter.java`
- `BffUserInfoExtractor.java` (gateway — will be updated in Epic 3, skip for now if it's in gateway module)
- Test files referencing `ClerkJwtUtils`

For gateway files that import `ClerkJwtUtils`: the gateway module has its own copy. Leave gateway files unchanged for now — they'll be cleaned up in Epic 3.

- [ ] **Step 3: Delete ClerkJwtUtils.java**

- [ ] **Step 4: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename ClerkJwtUtils to JwtUtils, strip Clerk v2 format"
```

---

## Chunk 3: Epics 2 & 3 (KeycloakAdminClient Move + Gateway Strip)

### Task 9: Move KeycloakAdminClient to backend (Slice 2A)

**Files:**
- Copy: `gateway/.../service/KeycloakAdminClient.java` → `backend/.../security/keycloak/KeycloakAdminClient.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationService.java`

- [ ] **Step 1: Create KeycloakAdminProperties**

```java
package io.b2mash.b2b.b2bstrawman.security.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
    String serverUrl,
    String realm,
    String username,
    String password
) {}
```

- [ ] **Step 2: Add required dependencies to backend pom.xml**

The gateway's `KeycloakAdminClient` uses `RestClient` (Spring Boot) and `JdkClientHttpRequestFactory` — no external Keycloak library needed. However, verify the backend `pom.xml` has `spring-boot-starter-web` (it should, as a web app). No new Maven dependency is required.

- [ ] **Step 3: Copy and adapt KeycloakAdminClient**

Copy from `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`.
- Update package to `io.b2mash.b2b.b2bstrawman.security.keycloak`
- Remove methods that are no longer needed: `updateMemberRole()`, `setUserAttribute()`, `ensureUserProfileAttribute()`
- Keep: `inviteMember()`, `createOrganization()`, `addMember()`, `resolveOrgId()`, `findOrganizationByAlias()`
- Wire to `KeycloakAdminProperties` for configuration

- [ ] **Step 3: Add config to application.yml**

```yaml
keycloak:
  admin:
    server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8180}
    realm: ${KEYCLOAK_REALM:docteams}
    username: ${KEYCLOAK_ADMIN_USERNAME:admin}
    password: ${KEYCLOAK_ADMIN_PASSWORD:admin}
```

- [ ] **Step 4: Wire InvitationService to call Keycloak**

Add `KeycloakAdminClient` injection to `InvitationService.invite()`. After creating the `PendingInvitation` record, call:

```java
keycloakAdmin.inviteMember(orgId, email, null, redirectUrl);
// null for role — Keycloak invitation is identity-only now
```

The `orgId` comes from `RequestScopes.ORG_ID` ScopedValue (check the actual accessor method in `RequestScopes.java` — it may be `RequestScopes.requireOrgId()` or accessed directly). The redirect URL comes from a config property.

- [ ] **Step 5: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`
Expected: All tests pass (Keycloak calls should be mocked in test profile)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/ \
       backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationService.java \
       backend/src/main/resources/application.yml
git commit -m "feat: move KeycloakAdminClient to backend, wire InvitationService"
```

---

### Task 10: Add org creation endpoint to backend (Slice 2B)

**Files:**
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/org/OrgController.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/org/OrgService.java`
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/org/CreateOrgRequest.java`

- [ ] **Step 1: Create org creation endpoint**

Move the logic currently in `BffController.createOrg()` (gateway) to backend. The endpoint:
- `POST /api/orgs` — requires platform admin or self-service flag
- Creates Keycloak org via `KeycloakAdminClient.createOrganization()`
- Adds creator as member
- Provisions tenant schema

Guard with `RequestScopes.isPlatformAdmin()` check (not `@PreAuthorize`).

- [ ] **Step 2: Write integration test**

- [ ] **Step 3: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/org/
git commit -m "feat: add POST /api/orgs endpoint (moved from gateway BFF)"
```

---

### Task 11: Gateway — /bff/me returns identity only (Slice 3A)

**Files:**
- Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`
- Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffUserInfoExtractor.java`

- [ ] **Step 1: Remove orgRole from BffUserInfo record**

In `BffController.java`, update the `BffUserInfo` record (lines 46-60):
- Remove the `orgRole` field
- Update `unauthenticated()` factory method
- Update the construction site in the `/bff/me` handler

- [ ] **Step 2: Simplify BffUserInfoExtractor**

Remove the `orgRole` extraction logic from `extractOrgInfo()`. The `OrgInfo` record should only contain `slug` and `id`, not `role`.

- [ ] **Step 3: Run gateway tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/gateway && ./mvnw clean verify -q 2>&1 | tail -20`

- [ ] **Step 4: Commit**

```bash
git add gateway/src/main/java/io/b2mash/b2b/gateway/controller/
git commit -m "refactor: /bff/me returns identity only, remove orgRole"
```

---

### Task 12: Gateway — Remove admin endpoints and BFF authorization (Slices 3B + 3C)

**Files:**
- Delete: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/AdminProxyController.java`
- Delete: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffSecurity.java`
- Delete: `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`
- Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`
- Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`

- [ ] **Step 1: Delete AdminProxyController, BffSecurity, KeycloakAdminClient**

- [ ] **Step 2: Remove /bff/orgs endpoint from BffController**

Delete the `createOrg()` method and related DTOs (`CreateOrgRequest`, `CreateOrgResponse`).

- [ ] **Step 3: Clean up GatewaySecurityConfig**

Remove any path patterns for deleted endpoints (`/bff/admin/**`, `/bff/orgs`). Verify `/api/**` still proxies correctly via TokenRelay.

- [ ] **Step 4: Clean up unused dependencies/config**

Remove any Keycloak admin-specific configuration from `application.yml` if no longer needed.

- [ ] **Step 5: Run gateway tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/gateway && ./mvnw clean verify -q 2>&1 | tail -20`

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove gateway admin endpoints, BffSecurity, KeycloakAdminClient"
```

---

## Chunk 4: Epics 4 & 5 (Frontend + Cleanup)

### Task 13: Frontend — Remove orgRole from auth types and providers (Slice 4A)

**Files:**
- Modify: `frontend/lib/auth/types.ts`
- Modify: `frontend/lib/auth/providers/keycloak-bff.ts`
- Modify: `frontend/lib/auth/providers/mock/server.ts`
- Modify: `frontend/lib/auth/server.ts`

- [ ] **Step 1: Remove orgRole from AuthContext type**

In `lib/auth/types.ts`:
```typescript
export interface AuthContext {
  userId: string;
  orgId: string;
  orgSlug: string;
  groups: string[];
  // orgRole removed
}
```

- [ ] **Step 2: Update keycloak-bff.ts provider**

- `getAuthContext()`: Remove `orgRole` from the returned object (the `/bff/me` response no longer includes it)
- Delete `requireRole()` function entirely
- Remove `requireRole` from exports

- [ ] **Step 3: Update mock/server.ts provider**

- Change token payload parsing: stop extracting `o.rol` from JWT
- Remove `orgRole` from returned `AuthContext`
- Delete `requireRole()` function

- [ ] **Step 4: Update server.ts re-exports**

Remove `requireRole` from the re-export list.

- [ ] **Step 5: Fix TypeScript compilation errors**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && npx tsc --noEmit 2>&1 | head -50`
Expected: Many errors from files that destructure `orgRole` from `getAuthContext()` — these will be fixed in Task 14.

**Known red state:** The codebase will not compile between Tasks 13 and 14. This is intentional — Task 13 removes the type, Task 14 fixes all consumers. Do not run CI between these tasks.

- [ ] **Step 6: Commit (types + providers only)**

```bash
git add frontend/lib/auth/
git commit -m "refactor: remove orgRole from AuthContext type and providers"
```

---

### Task 14: Frontend — Migrate all orgRole checks to capabilities (Slice 4B)

**Files:**
- Modify: ~107 files total (~77 source + ~30 test files) in `frontend/app/(app)/org/[slug]/`, `frontend/components/`, and `frontend/__tests__/`
- Modify: `frontend/lib/types/member.ts`

- [ ] **Step 1: Update member.ts types**

In `lib/types/member.ts`:
- Remove `orgRole: string` from `OrgMember` interface
- Remove `orgRole: string` from `ProjectMember` interface

- [ ] **Step 2: Migrate server actions (mechanical)**

For each file in `frontend/app/(app)/org/[slug]/*/actions.ts` that imports `getAuthContext` and checks `orgRole`:

**Before pattern:**
```typescript
const { orgRole } = await getAuthContext();
if (orgRole !== "org:admin" && orgRole !== "org:owner") {
  return { success: false, error: "Must be admin" };
}
```

**After pattern:**
```typescript
const caps = await fetchMyCapabilities();
if (!caps.isAdmin) {
  return { success: false, error: "Must be admin" };
}
```

Add import: `import { fetchMyCapabilities } from "@/lib/api/capabilities";`
Remove import of `getAuthContext` if no longer used in the file (it may still be needed for `orgSlug` or `userId`).

**Note:** `fetchMyCapabilities()` is `"server-only"` — safe to use in server actions and Server Components.

- [ ] **Step 3: Migrate page/layout files (mechanical)**

For each file that does `const { orgRole } = await getAuthContext()`:

**Before:**
```typescript
const { orgRole } = await getAuthContext();
const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
```

**After:**
```typescript
const caps = await fetchMyCapabilities();
const isAdmin = caps.isAdmin;
```

Some pages already use `CapabilityProvider` — those may only need to remove the `orgRole` destructuring.

- [ ] **Step 4: Fix TypeScript compilation**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && npx tsc --noEmit 2>&1 | head -100`
Fix remaining type errors. The compiler will catch every file that still references `orgRole`.

- [ ] **Step 5: Run frontend tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm test 2>&1 | tail -20`
Expected: Some test failures where mocks return `orgRole` — update test mocks to use capabilities instead.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: migrate all orgRole checks to fetchMyCapabilities()"
```

---

### Task 15: Frontend — Update mock IDP and E2E fixtures (Slice 4C)

**Files:**
- Modify: `compose/mock-idp/src/index.ts`
- Modify: `frontend/e2e/fixtures/auth.ts`
- Modify: `frontend/components/auth/mock-login-form.tsx`

- [ ] **Step 1: Update mock IDP token format**

In `compose/mock-idp/src/index.ts`, change the JWT payload from Clerk v2 to Keycloak format:

**Before:**
```typescript
const payload = {
  sub: userId,
  v: 2,
  o: { id: orgId, rol: orgRole, slg: orgSlug }
};
```

**After:**
```typescript
const payload = {
  sub: userId,
  organization: [orgSlug],
  groups: userGroups[userId] || [],
  email: userEmails[userId],
  iss: "http://mock-idp:8090",
  aud: "docteams-e2e"
};
```

Add user data maps (`userGroups`, `userEmails`) for the E2E seed users (alice, bob, carol).

Remove `orgRole` from the `/token` request body schema — it's no longer needed.

- [ ] **Step 2: Update mock-login-form.tsx**

Remove `orgRole` from the token request body sent to mock IDP.

- [ ] **Step 3: Update E2E auth fixture**

In `frontend/e2e/fixtures/auth.ts`:
- Remove `orgRole` from the `USERS` constant (or if role is needed for seed data, handle via DB seed, not token)
- Update `loginAs()` to not send `orgRole` to mock IDP

- [ ] **Step 4: Update mock/server.ts to parse Keycloak format**

The mock auth provider should now parse the new token format:
- Read `organization` claim (array of slugs) instead of `o.slg`
- Read `sub` for userId (unchanged)
- No `orgRole` extraction

- [ ] **Step 5: Verify E2E seed data sets org_role_id FK**

The E2E seed script must ensure member records have the correct `org_role_id` FK set (not just the now-dropped `org_role` VARCHAR). Check the seed SQL/script in `compose/` or `backend/src/main/resources/` — if it only sets `org_role`, update it to also set `org_role_id` by looking up the system role by slug.

Run: `grep -r "org_role" compose/ --include="*.sql" --include="*.ts" -l` to find seed files that need updating.

- [ ] **Step 6: Run E2E tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && npx playwright test 2>&1 | tail -30`
Expected: Smoke tests pass with new token format

- [ ] **Step 7: Commit**

```bash
git add compose/mock-idp/src/index.ts \
       frontend/e2e/fixtures/auth.ts \
       frontend/components/auth/mock-login-form.tsx \
       frontend/lib/auth/providers/mock/server.ts
git commit -m "refactor: mock IDP emits Keycloak-format tokens, no orgRole"
```

---

### Task 16: Cleanup — Remove ROLE_ORG_* authorities (Slice 5A)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtAuthenticationConverter.java`
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/Roles.java`
- Modify: ~11 test files

- [ ] **Step 1: Verify no @PreAuthorize remains**

Run: `grep -r "@PreAuthorize.*hasRole\|@PreAuthorize.*hasAnyRole" backend/src/main/java/ --include="*.java" -l`
Expected: Zero results (or only `SecurityConfig`, `PlatformAdminController` which use different patterns)

- [ ] **Step 2: Update ClerkJwtAuthenticationConverter**

Stop granting `ROLE_ORG_*` authorities. Grant only `ROLE_AUTHENTICATED`:

```java
private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    return List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
}
```

Remove the `ROLE_MAPPING` map and `ClerkJwtUtils.extractOrgRole()` call.

- [ ] **Step 3: Remove AUTHORITY_* constants from Roles.java**

Remove:
```java
public static final String AUTHORITY_ORG_OWNER = "ROLE_ORG_OWNER";
public static final String AUTHORITY_ORG_ADMIN = "ROLE_ORG_ADMIN";
public static final String AUTHORITY_ORG_MEMBER = "ROLE_ORG_MEMBER";
```

Keep `AUTHORITY_INTERNAL` (used by `ApiKeyAuthFilter`).

- [ ] **Step 4: Update test JWT mocks**

Find tests that grant `ROLE_ORG_*` authorities in JWT mocks:
```bash
grep -r "ROLE_ORG_\|ORG_OWNER\|ORG_ADMIN\|ORG_MEMBER" backend/src/test/ --include="*.java" -l
```

Update these to use capability-based test setup instead. The tests should set up member records with appropriate `OrgRole` entities, which `MemberFilter` will resolve to capabilities.

- [ ] **Step 5: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20`

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "cleanup: remove ROLE_ORG_* authorities, JWT is identity-only"
```

---

### Task 17: Cleanup — Remove webhook role sync and update CLAUDE.md (Slices 5B + 5C)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java`
- Modify: `backend/CLAUDE.md`
- Modify: `frontend/CLAUDE.md`

- [ ] **Step 1: Simplify MemberSyncService**

Remove the `orgRole` parameter from `syncMember()`. The service should only sync identity attributes (email, name, avatarUrl) from webhooks. Role changes happen exclusively through `OrgRoleService.assignRole()`.

Remove the `member.role_changed` audit event emission from `syncMember()` — role changes are now tracked in `OrgRoleService`.

- [ ] **Step 2: Update backend/CLAUDE.md**

- Replace references to `@PreAuthorize` with `@RequiresCapability` in security conventions
- Update JWT/token documentation to remove Clerk v2 format and `o.rol` claim
- Rename `ClerkJwtUtils` references to `JwtUtils`
- Document the `MemberCacheService` and cache eviction pattern
- Add `PendingInvitation` to the entity list

- [ ] **Step 3: Update frontend/CLAUDE.md**

- Document that `AuthContext` no longer contains `orgRole`
- Document that authorization comes from `fetchMyCapabilities()` / `CapabilityProvider`
- Remove references to `requireRole()`

- [ ] **Step 4: Run all tests (backend + frontend)**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw clean verify -q 2>&1 | tail -20
cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm test 2>&1 | tail -20
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "cleanup: remove webhook role sync, update CLAUDE.md conventions"
```

---

## Verification Checklist

After all tasks are complete, verify:

- [ ] `grep -r "org_role\|orgRole\|ClerkJwtUtils\|@PreAuthorize.*hasRole" backend/src/main/java/ --include="*.java"` → zero hits (except Roles.java constants `ORG_OWNER`/`ORG_ADMIN`/`ORG_MEMBER` which are slug values, not authorities)
- [ ] `grep -r "orgRole" frontend/lib/auth/ --include="*.ts"` → zero hits
- [ ] `grep -r "requireRole" frontend/ --include="*.ts" --include="*.tsx"` → zero hits
- [ ] Backend: `./mvnw clean verify` → all pass
- [ ] Frontend: `pnpm test` → all pass
- [ ] Frontend: `pnpm build` → builds successfully
- [ ] E2E: `npx playwright test` → smoke tests pass
