# Backend Debt Remediation — Critical & High Priority

Findings from full backend code evaluation (Feb 2026). Each item includes the problem explanation, why it matters, affected files, and a concrete implementation plan.

---

## Item 1: API Key Timing Attack (CRITICAL)

### Problem

`security/ApiKeyAuthFilter.java:33` uses `String.equals()` to compare the submitted API key against the expected value:

```java
if (expectedApiKey.equals(apiKey)) {
```

`String.equals()` returns `false` as soon as a character mismatch is found. An attacker can measure response time to determine how many leading characters of the key are correct, then brute-force one character at a time. This is called a **timing side-channel attack**.

### Why It Matters

This filter is the sole gate for `/internal/*` endpoints — tenant provisioning, member sync, and any future internal APIs. A leaked key allows arbitrary tenant schema creation.

### Affected Files

| File | Line | Change |
|------|------|--------|
| `security/ApiKeyAuthFilter.java` | 33 | Replace `equals()` with constant-time compare |

### Implementation Plan

1. **Replace the comparison** at line 33 with `MessageDigest.isEqual()`:
   ```java
   import java.nio.charset.StandardCharsets;
   import java.security.MessageDigest;

   if (apiKey != null
       && MessageDigest.isEqual(
           expectedApiKey.getBytes(StandardCharsets.UTF_8),
           apiKey.getBytes(StandardCharsets.UTF_8))) {
   ```
   `MessageDigest.isEqual()` always compares every byte, taking constant time regardless of where the mismatch occurs.

2. **Null guard**: The existing code implicitly handles `apiKey == null` because `expectedApiKey.equals(null)` returns `false`. The new version needs an explicit `apiKey != null` check since `getBytes()` would NPE.

3. **Add imports**: `java.nio.charset.StandardCharsets` and `java.security.MessageDigest`.

4. **Write a unit test** (`ApiKeyAuthFilterTest.java`) covering:
   - Valid key → 200 (request continues)
   - Invalid key → 401
   - Missing header (null) → 401
   - Empty string key → 401

### Verification

- Run `./mvnw test` — all existing `SecurityIntegrationTest` tests still pass (lines 50–67 already test valid/invalid/missing key via integration tests)
- New unit test passes

### Effort: ~30 minutes

---

## Item 2: Missing FK Indexes (HIGH)

### Problem

Migration `V4__migrate_ownership_to_members.sql` adds foreign key constraints on `projects.created_by` (line 52) and `documents.uploaded_by` (line 53) but creates no indexes on those columns.

PostgreSQL does **not** auto-create indexes for FK columns. Without them:
- Any `JOIN` or `WHERE` filter on these columns → sequential scan
- `ON DELETE CASCADE` from `members` table → sequential scan of `projects` and `documents` to find dependent rows
- Performance degrades linearly with row count

### Why It Matters

Every member deletion triggers cascade checks across both tables. As tenants accumulate projects and documents, these scans become the dominant cost of member removal operations.

### Affected Files

| File | Change |
|------|--------|
| `db/migration/tenant/V6__add_missing_fk_indexes.sql` | **New migration file** |

### Implementation Plan

1. **Create** `src/main/resources/db/migration/tenant/V6__add_missing_fk_indexes.sql`:
   ```sql
   -- V6: Add indexes on FK columns that were missing from V4

   CREATE INDEX IF NOT EXISTS idx_projects_created_by
       ON projects (created_by);

   CREATE INDEX IF NOT EXISTS idx_documents_uploaded_by
       ON documents (uploaded_by);
   ```

2. That's it. Flyway runs this on next startup for all existing tenant schemas via `TenantMigrationRunner`.

### Verification

- Run `./mvnw test` — Testcontainers Postgres applies the migration; all tests still pass
- Optionally verify via `EXPLAIN ANALYZE` in psql after migration that queries on these columns use index scans

### Effort: ~15 minutes

---

## Item 3: Extract ClerkJwtUtils (HIGH — DRY)

### Problem

Four files independently extract Clerk JWT v2 nested org claims with nearly identical `@SuppressWarnings("unchecked")` blocks:

| File | Line | Extracts |
|------|------|----------|
| `multitenancy/TenantFilter.java` | 77–85 | `o.id` (org ID) |
| `member/MemberFilter.java` | 138–145 | `o.rol` (org role) |
| `security/ClerkJwtAuthenticationConverter.java` | 31–39 | `o.rol` (org role) |
| `document/DocumentController.java` | 37–42 | `o.id` (org ID) |

The pattern is:
```java
@SuppressWarnings("unchecked")
Map<String, Object> orgClaim = jwt.getClaim("o");
if (orgClaim != null) {
    return (String) orgClaim.get("id"); // or "rol"
}
return null;
```

### Why It Matters

This extracts security-critical data. If Clerk changes JWT format, you'd update 4 locations. One miss → silent auth bypass. The unchecked casts also silently fail with `ClassCastException` if claim types change.

### Affected Files

| File | Change |
|------|--------|
| `security/ClerkJwtUtils.java` | **New file** — static utility |
| `multitenancy/TenantFilter.java` | Replace `extractOrgId()` with `ClerkJwtUtils.extractOrgId()` |
| `member/MemberFilter.java` | Replace `extractOrgRole()` with `ClerkJwtUtils.extractOrgRole()` |
| `security/ClerkJwtAuthenticationConverter.java` | Replace inline extraction with `ClerkJwtUtils.extractOrgRole()` |
| `document/DocumentController.java` | Replace inline extraction with `ClerkJwtUtils.extractOrgId()` |

### Implementation Plan

1. **Create** `security/ClerkJwtUtils.java`:
   ```java
   package io.b2mash.b2b.b2bstrawman.security;

   import java.util.Map;
   import org.springframework.security.oauth2.jwt.Jwt;

   /**
    * Extracts Clerk JWT v2 org claims from the nested "o" object.
    * Clerk v2 format: { "o": { "id": "org_xxx", "rol": "owner", "slg": "my-org" } }
    */
   public final class ClerkJwtUtils {

     private static final String ORG_CLAIM = "o";

     /** Extracts the org ID (o.id) from a Clerk JWT v2 token. */
     public static String extractOrgId(Jwt jwt) {
       return extractNestedClaim(jwt, "id");
     }

     /** Extracts the org role (o.rol) from a Clerk JWT v2 token. */
     public static String extractOrgRole(Jwt jwt) {
       return extractNestedClaim(jwt, "rol");
     }

     private static String extractNestedClaim(Jwt jwt, String key) {
       Object orgClaim = jwt.getClaim(ORG_CLAIM);
       if (orgClaim instanceof Map<?, ?> map) {
         Object value = map.get(key);
         if (value instanceof String str) {
           return str;
         }
       }
       return null;
     }

     private ClerkJwtUtils() {}
   }
   ```

   Key improvement: uses `instanceof` pattern matching instead of unchecked cast. If Clerk sends a non-Map or non-String value, this returns `null` gracefully instead of throwing `ClassCastException`.

2. **Update TenantFilter.java** — delete `extractOrgId()` method (lines 77–85), call `ClerkJwtUtils.extractOrgId(jwt)` at line 38.

3. **Update MemberFilter.java** — delete `extractOrgRole()` method (lines 138–145), call `ClerkJwtUtils.extractOrgRole(jwt)` at line 81.

4. **Update ClerkJwtAuthenticationConverter.java** — replace lines 33–39 extraction with:
   ```java
   String orgRole = ClerkJwtUtils.extractOrgRole(jwt);
   ```

5. **Update DocumentController.java** — replace lines 37–42 with:
   ```java
   String orgId = ClerkJwtUtils.extractOrgId(auth.getToken());
   if (orgId == null) {
     throw new MissingOrganizationContextException();
   }
   ```

### Verification

- Run `./mvnw test` — all integration tests pass (JWT mock format is unchanged)
- Verify no remaining usages of raw `jwt.getClaim("o")` via `grep`

### Effort: ~45 minutes

---

## Item 4: Extract Service Access Control Pattern (HIGH — DRY + Performance)

### Problem

Seven methods across two services repeat this pattern:

```java
if (!projectRepository.existsById(projectId)) {              // Query 1 (redundant)
    throw new ResourceNotFoundException("Project", projectId);
}
var access = projectAccessService.checkAccess(projectId, memberId, orgRole);  // Query 2
if (!access.canView()) {
    throw new ResourceNotFoundException("Project", projectId);
}
```

**Locations:**
| File | Methods |
|------|---------|
| `DocumentService.java` | `listDocuments` (34–41), `initiateUpload` (54–60) |
| `DocumentService.java` | `confirmUpload` (81–84), `cancelUpload` (98–101), `getPresignedDownloadUrl` (116–119) |
| `ProjectService.java` | `getProject` (43–48), `updateProject` (64–69) |

The `existsById()` call is fully redundant — `checkAccess()` already queries `project_members` which references the project. If the project doesn't exist, `checkAccess()` returns `ProjectAccess.DENIED`, and the `!canView()` branch throws the same `ResourceNotFoundException`.

This means every document/project operation executes **one extra database query** for no reason.

### Why It Matters

- **DRY**: 7 copies of the same 4-line block. If access semantics change, 7 locations to update.
- **Performance**: Redundant `existsById()` adds a query per request. For document-heavy endpoints, this is noticeable latency.

### Affected Files

| File | Change |
|------|--------|
| `member/ProjectAccessService.java` | Add `requireViewAccess()` and `requireEditAccess()` methods |
| `document/DocumentService.java` | Replace 5 inline blocks with single method calls |
| `project/ProjectService.java` | Replace 2 inline blocks with single method calls |

### Implementation Plan

1. **Add methods to `ProjectAccessService.java`**:

   ```java
   /**
    * Checks access and throws ResourceNotFoundException if the caller cannot view the project.
    * This provides security-by-obscurity: unauthorized users see "not found" rather than "forbidden".
    */
   @Transactional(readOnly = true)
   public ProjectAccess requireViewAccess(UUID projectId, UUID memberId, String orgRole) {
     var access = checkAccess(projectId, memberId, orgRole);
     if (!access.canView()) {
       throw new ResourceNotFoundException("Project", projectId);
     }
     return access;
   }

   /**
    * Checks access and throws ForbiddenException if the caller cannot edit the project.
    * Calls requireViewAccess first, so non-members get 404 (not 403).
    */
   @Transactional(readOnly = true)
   public ProjectAccess requireEditAccess(UUID projectId, UUID memberId, String orgRole) {
     var access = requireViewAccess(projectId, memberId, orgRole);
     if (!access.canEdit()) {
       throw new ForbiddenException(
           "Cannot edit project", "You do not have permission to edit project " + projectId);
     }
     return access;
   }
   ```

2. **Update DocumentService.java** — replace all 5 inline access blocks.

   For `listDocuments` and `initiateUpload` (project-ID based):
   ```java
   // Before (lines 34-41):
   if (!projectRepository.existsById(projectId)) {
     throw new ResourceNotFoundException("Project", projectId);
   }
   var access = projectAccessService.checkAccess(projectId, memberId, orgRole);
   if (!access.canView()) {
     throw new ResourceNotFoundException("Project", projectId);
   }

   // After:
   projectAccessService.requireViewAccess(projectId, memberId, orgRole);
   ```

   For `confirmUpload`, `cancelUpload`, `getPresignedDownloadUrl` (document-ID based — find document first, then check project access):
   ```java
   // Before:
   var access = projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
   if (!access.canView()) {
     throw new ResourceNotFoundException("Document", documentId);
   }

   // After:
   projectAccessService.requireViewAccess(document.getProjectId(), memberId, orgRole);
   ```

3. **Update ProjectService.java**:

   For `getProject` — replace inline check:
   ```java
   // Before (lines 43-48):
   var project = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
   var access = projectAccessService.checkAccess(id, memberId, orgRole);
   if (!access.canView()) {
     throw new ResourceNotFoundException("Project", id);
   }

   // After:
   var project = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
   var access = projectAccessService.requireViewAccess(id, memberId, orgRole);
   ```

   For `updateProject` — use `requireEditAccess` (also removes the separate `canEdit()` block at lines 70–73):
   ```java
   // Before (lines 64-73):
   var project = repository.findById(id).orElseThrow(...);
   var access = projectAccessService.checkAccess(id, memberId, orgRole);
   if (!access.canView()) { throw ... }
   if (!access.canEdit()) { throw ... }

   // After:
   var project = repository.findById(id).orElseThrow(...);
   var access = projectAccessService.requireEditAccess(id, memberId, orgRole);
   ```

4. **Remove `projectRepository` dependency from `DocumentService`** — after removing all `existsById()` calls, `DocumentService` no longer needs `ProjectRepository`. Remove the field and constructor parameter.

### Verification

- Run `./mvnw test` — all 176+ tests pass
- Verify no remaining raw `checkAccess()` + `canView()` patterns via `grep`

### Effort: ~1 hour

---

## Item 5: Tests for ApiKeyAuthFilter (HIGH)

### Problem

`ApiKeyAuthFilter` is the sole authentication gate for `/internal/*` endpoints. Current coverage:

- `SecurityIntegrationTest` has 3 relevant tests (lines 50–67): valid key, missing key, invalid key
- No unit tests exist for the filter in isolation
- No tests for edge cases: empty string key, whitespace key, null expected key

### Why It Matters

After fixing the timing attack (Item 1), new tests ensure the `MessageDigest.isEqual()` replacement preserves all existing behavior. The filter is also simple enough that unit tests provide fast feedback without the overhead of Spring context startup.

### Affected Files

| File | Change |
|------|--------|
| `security/ApiKeyAuthFilterTest.java` | **New file** — unit test |

### Implementation Plan

1. **Create** `src/test/java/io/b2mash/b2b/b2bstrawman/security/ApiKeyAuthFilterTest.java`:

   Test cases:
   | Test | Input | Expected |
   |------|-------|----------|
   | `validKey_continuesFilterChain` | Correct key | `filterChain.doFilter()` called, auth set in SecurityContext |
   | `invalidKey_returns401` | Wrong key | 401, `filterChain.doFilter()` NOT called |
   | `missingHeader_returns401` | No `X-API-KEY` header | 401 |
   | `emptyKey_returns401` | Empty string | 401 |
   | `shouldNotFilter_nonInternalPath` | `/api/projects` | `shouldNotFilter()` returns `true` |
   | `shouldFilter_internalPath` | `/internal/orgs/provision` | `shouldNotFilter()` returns `false` |
   | `validKey_setsInternalServiceAuth` | Correct key | Principal is `"internal-service"`, authority is `ROLE_INTERNAL_SERVICE` |

2. Use `MockHttpServletRequest`, `MockHttpServletResponse`, and a mock `FilterChain` (no Spring context needed).

### Verification

- `./mvnw test -pl backend -Dtest=ApiKeyAuthFilterTest` passes

### Effort: ~30 minutes

---

## Item 6: Centralize Role Constants (HIGH)

### Problem

Role strings are scattered across 4+ files with different constant names and scopes:

| File | Constants | Scope |
|------|-----------|-------|
| `ProjectAccessService.java:10-12` | `ROLE_OWNER`, `ROLE_ADMIN`, `ROLE_LEAD` | `private static final` |
| `ProjectMemberService.java:18-19` | `ROLE_LEAD`, `ROLE_MEMBER` | `static final` (package-private) |
| `ProjectService.java:35` | Inline `"owner"`, `"admin"` | string literals |
| `ClerkJwtAuthenticationConverter.java:19-23` | Inline `Map.of("owner", ...)` | string literals |
| `MemberFilter.java:118` | Inline `"member"` | string literal |

The same logical value (e.g. `"lead"`) is defined in two files as `ROLE_LEAD` with different visibility. The converter uses inline literals for the same values. A typo in any one of these silently breaks authorization.

### Why It Matters

Authorization logic is the most critical code path in the system. Role comparisons should be DRY, type-safe, and defined once.

### Affected Files

| File | Change |
|------|--------|
| `security/Roles.java` | **New file** — shared constants |
| `member/ProjectAccessService.java` | Remove private constants, import `Roles` |
| `member/ProjectMemberService.java` | Remove package constants, import `Roles` |
| `project/ProjectService.java` | Replace inline strings with `Roles` |
| `security/ClerkJwtAuthenticationConverter.java` | Replace inline map values with `Roles` |
| `member/MemberFilter.java` | Replace inline `"member"` with `Roles` |
| `security/ApiKeyAuthFilter.java` | Replace inline authority string with `Roles` |

### Implementation Plan

1. **Create** `security/Roles.java`:
   ```java
   package io.b2mash.b2b.b2bstrawman.security;

   /**
    * Centralized role constants used across authentication, authorization, and access control.
    *
    * Org roles come from Clerk JWT v2 "o.rol" claim.
    * Project roles are application-defined in the project_members table.
    * Spring authorities are the ROLE_ prefixed versions used by @PreAuthorize.
    */
   public final class Roles {

     // Org-level roles (Clerk JWT v2 "o.rol" values)
     public static final String ORG_OWNER = "owner";
     public static final String ORG_ADMIN = "admin";
     public static final String ORG_MEMBER = "member";

     // Project-level roles (project_members.project_role values)
     public static final String PROJECT_LEAD = "lead";
     public static final String PROJECT_MEMBER = "member";

     // Spring Security granted authorities
     public static final String AUTHORITY_ORG_OWNER = "ROLE_ORG_OWNER";
     public static final String AUTHORITY_ORG_ADMIN = "ROLE_ORG_ADMIN";
     public static final String AUTHORITY_ORG_MEMBER = "ROLE_ORG_MEMBER";
     public static final String AUTHORITY_INTERNAL = "ROLE_INTERNAL_SERVICE";

     private Roles() {}
   }
   ```

2. **Update `ProjectAccessService.java`** — delete lines 10–12, replace usages:
   - `ROLE_OWNER` → `Roles.ORG_OWNER`
   - `ROLE_ADMIN` → `Roles.ORG_ADMIN`
   - `ROLE_LEAD` → `Roles.PROJECT_LEAD`

3. **Update `ProjectMemberService.java`** — delete lines 18–19, replace usages:
   - `ROLE_LEAD` → `Roles.PROJECT_LEAD`
   - `ROLE_MEMBER` → `Roles.PROJECT_MEMBER`

4. **Update `ProjectService.java`** — replace inline strings at line 35:
   ```java
   // Before:
   if ("owner".equals(orgRole) || "admin".equals(orgRole)) {
   // After:
   if (Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole)) {
   ```

5. **Update `ClerkJwtAuthenticationConverter.java`** — replace inline map at lines 19–23:
   ```java
   private static final Map<String, String> ROLE_MAPPING =
       Map.of(
           Roles.ORG_OWNER, Roles.AUTHORITY_ORG_OWNER,
           Roles.ORG_ADMIN, Roles.AUTHORITY_ORG_ADMIN,
           Roles.ORG_MEMBER, Roles.AUTHORITY_ORG_MEMBER);
   ```

6. **Update `MemberFilter.java`** — replace inline `"member"` at line 118:
   ```java
   orgRole != null ? orgRole : Roles.ORG_MEMBER
   ```

7. **Update `ApiKeyAuthFilter.java`** — replace inline authority at line 50:
   ```java
   super(List.of(new SimpleGrantedAuthority(Roles.AUTHORITY_INTERNAL)));
   ```

### Verification

- Run `./mvnw test` — all tests pass (constants have same string values as the replaced literals)
- `grep -r '"owner"\|"admin"\|"member"\|"lead"' --include="*.java" src/main/` should find no remaining role literals outside of `Roles.java`

### Effort: ~45 minutes

---

## Implementation Order

The items have minimal dependencies on each other but a logical ordering helps:

```
Step 1: Item 6 (Roles constants)         — foundation, no risk
Step 2: Item 3 (ClerkJwtUtils)           — foundation, no risk
Step 3: Item 1 (Timing attack fix)       — security critical
Step 4: Item 5 (ApiKeyAuthFilter tests)  — tests the fix from Step 3
Step 5: Item 4 (Access control DRY)      — largest change, benefits from Steps 1-2
Step 6: Item 2 (FK indexes)              — independent, deploy last to avoid migration ordering issues
```

Steps 1–2 are pure additive (new files + imports) and can't break anything.
Step 3 is a single-line behavioral change.
Steps 4–5 are the most involved refactors.
Step 6 is a standalone SQL migration.

**Total estimated effort: ~3.5 hours**

---

## Out of Scope (Deferred)

- **Pagination** — deferred per request; will change the API contract
- **Medium-priority items** — `member/` package restructure, test boilerplate dedup, config consolidation, input validation, cache externalization
- **Low-priority items** — minor cleanup and polish
