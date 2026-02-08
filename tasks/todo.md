# Epic 18: MemberFilter + MemberContext — Slice Breakdown

## Overview

Epic 18 adds request-level member resolution and migrates ownership columns to UUID FKs.
Split into **two slices** with a clean boundary: infrastructure first, then data model change.

---

## Slice 18A: MemberContext + MemberFilter Infrastructure

**Scope**: Pure additions — no changes to existing entities, migrations, or behavior.
**Risk**: Low — existing tests should pass because MemberFilter lazy-creates missing members.
**Tasks**: 18.1, 18.2, 18.3, 18.8, + MemberFilter-specific tests (part of 18.9)

### Task Checklist

- [ ] **18.1 — Create MemberContext**
  - `member/MemberContext.java`
  - ThreadLocal pattern identical to `TenantContext`:
    - `setCurrentMemberId(UUID)`, `getCurrentMemberId()`, `setOrgRole(String)`, `getOrgRole()`, `clear()`
  - Final class, private constructor, static methods only

- [ ] **18.2 — Create MemberFilter**
  - `member/MemberFilter.java` — `OncePerRequestFilter`
  - Runs after `TenantFilter` (needs tenant context to query member table)
  - Logic:
    1. Extract JWT `sub` (Clerk user ID) from `SecurityContextHolder`
    2. Check cache: `ConcurrentHashMap<String, UUID>` keyed by `tenantId:clerkUserId`
    3. Cache miss → `MemberRepository.findByClerkUserId(clerkUserId)`
    4. If not found → **lazy-create** minimal member (clerkUserId from `sub`, orgRole from `o.rol`, placeholder name/email). Webhook will upsert full data later.
    5. Set `MemberContext.setCurrentMemberId(memberId)` and `MemberContext.setOrgRole(orgRole)`
  - `shouldNotFilter()` returns true for `/internal/*` and `/actuator/*`
  - `finally` block clears `MemberContext`
  - Public `evictCacheEntry(tenantId, clerkUserId)` for use by `MemberSyncService.deleteMember()`

- [ ] **18.3 — Wire MemberFilter into SecurityConfig**
  - `SecurityConfig.java` changes:
    - `.addFilterAfter(memberFilter, TenantFilter.class)`
    - Move tenantLoggingFilter: `.addFilterAfter(tenantLoggingFilter, MemberFilter.class)`
  - New filter chain order:
    ```
    ApiKeyAuthFilter → BearerTokenAuth → TenantFilter → MemberFilter → TenantLoggingFilter
    ```

- [ ] **18.8 — Update TenantLoggingFilter**
  - Add `MDC.put("memberId", MemberContext.getCurrentMemberId().toString())`
  - Guard with null check (MemberContext is null for `/internal/*` requests)
  - Add `MDC.remove("memberId")` in finally block

- [ ] **18.9a — Add MemberFilter integration tests**
  - `member/MemberFilterIntegrationTest.java`
  - Test cases:
    - Valid member → MemberContext set correctly
    - Unknown user → lazy-created with minimal data, context set
    - `/internal/*` endpoint → filter skipped, MemberContext null
    - Cache hit on second request (verify single DB query via log or mock)
  - Verify existing `ProjectIntegrationTest` and `DocumentIntegrationTest` still pass
    - They should, because lazy-create handles missing members
    - But `$.createdBy` still returns `"user_owner"` (String) — unchanged in this slice

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Lazy-create unknown members | Solves race condition: user hits API before membership webhook processed |
| Cache keyed by `tenantId:clerkUserId` | Same user can be member of multiple orgs — must be scoped per tenant |
| No TTL on cache | Member IDs are immutable UUIDs — only cleared on member deletion |
| MemberFilter after TenantFilter | Needs `TenantContext.getTenantId()` to resolve correct tenant schema |

### Verification

1. `./mvnw test` — all existing tests pass (MemberFilter transparent to existing code)
2. MemberFilter-specific tests pass
3. MDC `memberId` appears in logs for authenticated requests

---

## Slice 18B: FK Migration + Entity Refactor

**Scope**: Breaking data model change — columns migrate from VARCHAR to UUID with FK constraints.
**Risk**: Medium — migration alters existing columns, all entity assertions in tests change.
**Tasks**: 18.4, 18.5, 18.6, 18.7, + existing test updates (part of 18.9)
**Depends on**: Slice 18A merged and passing.

### Task Checklist

- [ ] **18.4 — Create V4 tenant migration**
  - `db/migration/tenant/V4__migrate_ownership_to_members.sql`
  - Multi-step migration:
    1. **Backfill members**: `INSERT INTO members (clerk_user_id, email, name, org_role) SELECT DISTINCT created_by, ..., 'unknown', 'member' FROM projects ON CONFLICT DO NOTHING` (same for `documents.uploaded_by`)
    2. **Add temp columns**: `ALTER TABLE projects ADD COLUMN created_by_member_id UUID`; `ALTER TABLE documents ADD COLUMN uploaded_by_member_id UUID`
    3. **Populate**: `UPDATE projects SET created_by_member_id = m.id FROM members m WHERE m.clerk_user_id = projects.created_by`; same for documents
    4. **Drop old columns**: `ALTER TABLE projects DROP COLUMN created_by`; same for documents
    5. **Rename**: `ALTER TABLE projects RENAME COLUMN created_by_member_id TO created_by`; same for documents
    6. **Add constraints**: `ALTER TABLE projects ADD CONSTRAINT fk_project_created_by FOREIGN KEY (created_by) REFERENCES members(id)`; same for documents
    7. **Add NOT NULL**: `ALTER TABLE projects ALTER COLUMN created_by SET NOT NULL`; same for documents
  - Must be idempotent (IF NOT EXISTS guards where possible)
  - Works on empty tables too (no rows to migrate = no-op)

- [ ] **18.5 — Update Project entity**
  - `project/Project.java`:
    - `createdBy`: `String` → `UUID` (column type now `uuid`)
    - `@Column(name = "created_by", nullable = false, updatable = false)`
  - `project/ProjectController.java`:
    - Change: `UUID createdBy = MemberContext.getCurrentMemberId();` (was `auth.getName()`)
  - `project/ProjectService.java`:
    - `createProject(...)` parameter: `String createdBy` → `UUID createdBy`

- [ ] **18.6 — Update Document entity**
  - `document/Document.java`:
    - `uploadedBy`: `String` → `UUID` (column type now `uuid`)
    - `@Column(name = "uploaded_by", nullable = false, updatable = false)`
  - `document/DocumentController.java`:
    - Change: `UUID uploadedBy = MemberContext.getCurrentMemberId();` (was `auth.getName()`)
  - `document/DocumentService.java`:
    - Parameter type: `String uploadedBy` → `UUID uploadedBy`

- [ ] **18.7 — Update response DTOs**
  - `ProjectController.ProjectResponse`:
    - `createdBy`: `String` → `UUID`
    - JSON serialization: UUID → `"550e8400-..."` string (backward compatible format, different value)
  - `DocumentController.DocumentResponse` (or equivalent):
    - `uploadedBy`: `String` → `UUID`
  - **Frontend impact**: Response shape unchanged (still a string in JSON), but value changes from Clerk user ID (`user_abc123`) to UUID (`550e8400-...`). Frontend currently displays `createdBy` nowhere critical — mainly for future use.

- [ ] **18.9b — Update existing integration tests**
  - `ProjectIntegrationTest.java`:
    - `@BeforeAll`: Create member records via `MemberRepository.save()` for each test user (`user_owner`, `user_admin`, `user_member`)
    - Update assertions: `$.createdBy` now returns a UUID string, not `"user_owner"`
    - Store member UUIDs as test constants for assertions
  - `DocumentIntegrationTest.java`:
    - Same pattern: seed members, update `uploadedBy` assertions
  - `MemberSyncIntegrationTest.java`:
    - Should be unaffected (uses `/internal/*` endpoints, no MemberFilter)

### Migration Safety

| Concern | Mitigation |
|---------|------------|
| Data loss during column rename | Temp column + populate + drop + rename pattern preserves all data |
| Empty table (new tenant) | Migration is a no-op on empty tables — no rows to backfill |
| Missing member for existing data | Step 1 backfills members from DISTINCT clerk user IDs with placeholder data |
| Rollback | No automated rollback — but temp columns created before old columns dropped |
| FK constraint on non-existent member | Step 1 ensures all referenced members exist before FK added |

### Verification

1. `./mvnw test` — all tests pass with new assertions
2. Migration works on both empty and populated tenant schemas
3. FK constraints enforce referential integrity
4. `$.createdBy` returns UUID string in API responses
5. Project creation uses `MemberContext.getCurrentMemberId()` (UUID, not clerk user ID)

---

## Dependency Graph

```
Epic 17 (Done) ──► Slice 18A ──► Slice 18B ──► Epic 19
                   (infra)       (migration)
```

## Effort Estimates

| Slice | Tasks | New Files | Modified Files | Test Impact | Effort |
|-------|-------|-----------|----------------|-------------|--------|
| **18A** | 18.1, 18.2, 18.3, 18.8, 18.9a | 3 | 2 | Additive (new tests only) | S-M |
| **18B** | 18.4, 18.5, 18.6, 18.7, 18.9b | 1 | 6+ | Breaking (all entity tests) | M |

## Files Summary

### Slice 18A — Create
- `member/MemberContext.java`
- `member/MemberFilter.java`
- `member/MemberFilterIntegrationTest.java` (test)

### Slice 18A — Modify
- `security/SecurityConfig.java` — add MemberFilter to chain
- `multitenancy/TenantLoggingFilter.java` — add memberId MDC

### Slice 18B — Create
- `db/migration/tenant/V4__migrate_ownership_to_members.sql`

### Slice 18B — Modify
- `project/Project.java` — createdBy String→UUID
- `project/ProjectController.java` — use MemberContext
- `project/ProjectService.java` — parameter type change
- `document/Document.java` — uploadedBy String→UUID
- `document/DocumentController.java` — use MemberContext
- `document/DocumentService.java` — parameter type change
- `project/ProjectIntegrationTest.java` — seed members, UUID assertions
- `document/DocumentIntegrationTest.java` — seed members, UUID assertions
