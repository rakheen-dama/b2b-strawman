# Backend Test Cleanup — Audit Report & Implementation Plan

**Date**: 2026-04-04 (audit) / 2026-04-05 (execution)
**Scope**: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/`
**Total test files**: 488 | **Total LOC**: ~150,000 | **Estimated saveable LOC**: ~10,000–13,000

## Execution Summary (2026-04-05)

- **Epic 1**: DEFERRED — Clerk naming absorbed into 3 utility files
- **Epic 2**: DONE — Created `TestMemberHelper`, `TestJwtFactory`, `TestEntityHelper` in `testutil/`
- **Epic 3**: DONE — 252 files migrated, net -6,291 lines (13,756 deleted, 7,465 inserted)
- **Epic 4**: TODO — 53 files still using raw `new Customer()`
- **Epic 5**: TODO — Documentation updates

---

## Part 1: Audit Findings

### Finding 1: Stale Clerk Naming (CRITICAL)

The project migrated from Clerk to Keycloak, but **"clerk" naming persists in both production and test code**. This is not just a test problem — the API contract, entity model, and DB columns still use `clerkOrgId`/`clerkUserId`.

| Location | Count | Examples |
|----------|-------|---------|
| Production entity fields | 1 | `Member.clerkUserId` (`clerk_user_id` DB column) |
| Production API payloads | 1 | `SyncMemberRequest.clerkOrgId`, `.clerkUserId` |
| Production repository methods | 1 | `OrgSchemaMappingRepository.findByClerkOrgId()` |
| Production services (17 files) | 17 | `MemberSyncService`, `TenantFilter`, `ProvisioningController`, etc. |
| Test files with `clerkOrgId`/`clerkUserId` | 332 | Every integration test's `syncMember()` helper |
| Test data IDs with `clerk_` prefix | 14+ | `"clerk_user"`, `"clerk_webhook_admin"`, etc. |

**Root cause**: Build agents copied from existing tests without questioning whether "clerk" was current terminology. The production code was never renamed after the Keycloak migration.

**Note**: `OrgSchemaMappingRepository.findByClerkOrgId()` already delegates to `findByExternalOrgId()` — the Clerk-specific method is a compatibility shim that should be removed.

---

### Finding 2: `syncMember()` Duplicated in 257 Files (CRITICAL)

Every integration test that needs a member defines its own private `syncMember()` method — **257 copies** of essentially identical code:

```java
// This exact pattern appears 257 times across 257 files
private String syncMember(String clerkUserId, String email, String name, String orgRole) throws Exception {
    var result = mockMvc
        .perform(post("/internal/members/sync")
            .header("X-API-KEY", API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                """.formatted(ORG_ID, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
}
```

**Lines wasted**: ~3,800 (15 lines × 257 files, minus the 1 shared copy)

**Variants observed**:
- Some return `memberId`, others return void
- Some use `ORG_ID` as class constant, others accept it as parameter
- Some expect `status().isCreated()`, others expect `status().isOk()` (for updates)
- Minor formatting differences (single-line vs multi-line JSON)

---

### Finding 3: JWT Builder Methods Duplicated in 195 Files (HIGH)

Nearly identical `ownerJwt()`, `adminJwt()`, `memberJwt()` methods in 195 files:

```java
// This pattern (with role variations) appears 195 times
private JwtRequestPostProcessor ownerJwt() {
    return jwt().jwt(j -> j.subject("user_xyz_owner")
        .claim("o", Map.of("id", ORG_ID, "rol", "owner")));
}
```

Most files define 1-4 of these methods (owner, admin, member, second-user).

**Lines wasted**: ~2,700–4,500 (3-5 lines × 3-4 methods × 195 files)

---

### Finding 4: Entity Creation Helpers Duplicated (MEDIUM)

Private `createProject()`, `createCustomer()`, `createTask()`, `createTag()` methods duplicated across files:

| Helper | Files | Lines each |
|--------|-------|------------|
| `createCustomer()` | 16 | ~15 |
| `createProject()` | 12 | ~15 |
| `createTask()` | 10+ | ~10 |
| `createTag()` | 5 | ~15 |

**Lines wasted**: ~700–1,000

These are all MockMvc POST calls that create an entity and extract the ID from the response. The pattern is identical — only the endpoint URL and JSON payload differ.

---

### Finding 5: Boilerplate Annotation Stack on 250+ Classes (MEDIUM)

Every integration test repeats the same 5-line annotation stack:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
```

Plus the same field declarations:
```java
private static final String API_KEY = "test-api-key";
private static final String ORG_ID = "org_feature_test";  // varies
@Autowired private MockMvc mockMvc;
@Autowired private TenantProvisioningService provisioningService;
```

**Lines wasted**: ~2,500 (10 lines × 250 files)

---

### Finding 6: Tenant Provisioning Setup Duplicated (MEDIUM)

100+ files have identical `@BeforeAll` provisioning:

```java
@BeforeAll
void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Some Test Org", null);
    syncMember(ORG_ID, "user_owner", "owner@test.com", "Owner", "owner");
    // ... entity creation
}
```

---

### Finding 7: Existing Utilities Underutilized

| Utility | Purpose | Adoption |
|---------|---------|----------|
| `TestCustomerFactory` | Customer creation with lifecycle status | 20% (97/488 files) — 53 files still use raw `new Customer()` |
| `TestChecklistHelper` | Checklist completion automation | 4% (19 files) — appropriate for specialized use |
| `TestIds` | Reflection-based ID injection | 3% (15 files) — 11 files roll their own reflection |

---

### Finding 8: Largest Test Files (Potential Split Candidates)

| File | Lines |
|------|-------|
| `TrustTransactionServiceTest.java` | 2,533 |
| `ProjectTemplateControllerTest.java` | 1,189 |
| `RecurringScheduleServiceTest.java` | 1,044 |
| `InformationRequestControllerTest.java` | 917 |
| `AutomationRuleControllerTest.java` | 872 |

---

## Part 2: Implementation Plan

### Principles

1. **No test behavior changes** — refactoring only, all existing tests must continue to pass
2. **Incremental delivery** — each epic is a standalone PR that can be reviewed/merged independently
3. **Production code first** — rename Clerk references in prod code before updating tests
4. **Shared utilities over base classes** — prefer static helpers and composition over inheritance (avoids diamond problems, keeps tests self-documenting)

---

### Epic 1: Rename Clerk → External/IDP in Production Code (Foundation)

**Why first**: Tests call production APIs. If we rename the API payload fields, the test helpers must change too. Do this once, then all test cleanup references the new names.

**Scope**: Rename `clerkOrgId` → `externalOrgId`, `clerkUserId` → `externalUserId` in the API contract and entity model. Keep DB column names unchanged (use `@Column(name = "clerk_user_id")` mapping) to avoid a migration.

#### Slice 1A: API Contract & DTO Rename
- [ ] Rename `SyncMemberRequest` fields: `clerkOrgId` → `externalOrgId`, `clerkUserId` → `externalUserId`
- [ ] Add `@JsonProperty("clerkOrgId")` aliases for backward compatibility during transition (remove in 1C)
- [ ] Rename `SyncMemberResponse` field: `clerkUserId` → `externalUserId`
- [ ] Update `MemberSyncController` log messages and parameter names
- [ ] Update `MemberSyncService` method signatures and internal variable names
- [ ] Update all 17 production files that reference `clerkOrgId`/`clerkUserId`

#### Slice 1B: Repository & Entity Rename
- [ ] Rename `Member.clerkUserId` field → `externalUserId` (keep `@Column(name = "clerk_user_id")`)
- [ ] Rename `MemberRepository.findByClerkUserId()` → `findByExternalUserId()`
- [ ] Remove `OrgSchemaMappingRepository.findByClerkOrgId()` shim (callers use `findByExternalOrgId()` directly)
- [ ] Update all 26 production files that call `findByClerkOrgId`/`findByClerkUserId`

#### Slice 1C: Remove Backward Compatibility Aliases
- [ ] Remove `@JsonProperty("clerkOrgId")` aliases from 1A
- [ ] Verify no external callers depend on old field names (internal API only, so safe)

**Files touched**: ~43 production files, 0 test files (tests updated in later epics)

---

### Epic 2: Create Shared Test Utilities (Foundation)

**Why before test cleanup**: Build the utilities first so Epic 3/4 have something to refactor toward.

#### Slice 2A: `TestMemberHelper` — Replace 257 syncMember() Copies

Create `testutil/TestMemberHelper.java`:

```java
public final class TestMemberHelper {
    private static final String API_KEY = "test-api-key";

    /** Sync a member and return the memberId. */
    public static String syncMember(MockMvc mockMvc, String orgId,
            String externalUserId, String email, String name, String orgRole) throws Exception {
        var result = mockMvc.perform(post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"externalOrgId":"%s","externalUserId":"%s","email":"%s",
                     "name":"%s","avatarUrl":null,"orgRole":"%s"}
                    """.formatted(orgId, externalUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
    }

    /** Sync (update) an existing member, expects 200 OK. */
    public static String updateMember(MockMvc mockMvc, String orgId,
            String externalUserId, String email, String name, String orgRole) throws Exception {
        // similar but expects status().isOk()
    }

    /** Sync with default email/name derived from role. */
    public static String syncOwner(MockMvc mockMvc, String orgId) throws Exception {
        return syncMember(mockMvc, orgId, "user_owner", "owner@test.com", "Owner", "owner");
    }

    // syncAdmin(), syncMember() convenience methods...
}
```

- [ ] Create `TestMemberHelper.java` with all syncMember variants observed across 257 files
- [ ] Add convenience methods for common patterns: `syncOwner()`, `syncAdmin()`, `syncMember()`
- [ ] Verify it compiles and existing tests still pass (no test changes yet)

#### Slice 2B: `TestJwtFactory` — Replace 195 JWT Builder Copies

Create `testutil/TestJwtFactory.java`:

```java
public final class TestJwtFactory {

    public static JwtRequestPostProcessor ownerJwt(String orgId, String subject) {
        return jwt().jwt(j -> j.subject(subject).claim("o", Map.of("id", orgId, "rol", "owner")));
    }

    public static JwtRequestPostProcessor ownerJwt(String orgId) {
        return ownerJwt(orgId, "user_owner");
    }

    public static JwtRequestPostProcessor adminJwt(String orgId) {
        return jwt().jwt(j -> j.subject("user_admin").claim("o", Map.of("id", orgId, "rol", "admin")));
    }

    public static JwtRequestPostProcessor memberJwt(String orgId) {
        return jwt().jwt(j -> j.subject("user_member").claim("o", Map.of("id", orgId, "rol", "member")));
    }

    public static JwtRequestPostProcessor jwtAs(String orgId, String subject, String role) {
        return jwt().jwt(j -> j.subject(subject).claim("o", Map.of("id", orgId, "rol", role)));
    }
}
```

- [ ] Create `TestJwtFactory.java` with all JWT role variants observed across 195 files
- [ ] Support both simple (ownerJwt(orgId)) and custom (jwtAs(orgId, subject, role)) patterns
- [ ] Verify compilation

#### Slice 2C: `TestEntityHelper` — Replace Entity Creation Duplicates

Create `testutil/TestEntityHelper.java`:

```java
public final class TestEntityHelper {

    public static String createProject(MockMvc mockMvc, JwtRequestPostProcessor jwt,
            String name, String description) throws Exception {
        var result = mockMvc.perform(post("/api/projects").with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","description":"%s"}
                    """.formatted(name, description)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    public static String createCustomer(MockMvc mockMvc, JwtRequestPostProcessor jwt,
            String name, String email) throws Exception { /* ... */ }

    public static String createTask(MockMvc mockMvc, JwtRequestPostProcessor jwt,
            String projectId, String name) throws Exception { /* ... */ }

    public static String createTag(MockMvc mockMvc, JwtRequestPostProcessor jwt,
            String name, String color) throws Exception { /* ... */ }
}
```

- [ ] Create `TestEntityHelper.java` with factory methods for Project, Customer, Task, Tag, Document
- [ ] Extract patterns from the most common duplicates
- [ ] Verify compilation

**Files created**: 3 new files in `testutil/`

---

### Epic 3: Migrate Tests to Shared Utilities (Bulk Cleanup)

This is the high-volume work. Each slice targets a package group to keep PRs reviewable.

**Strategy**: For each test file:
1. Replace private `syncMember()` with `TestMemberHelper.syncMember()`
2. Replace private `ownerJwt()`/`adminJwt()`/`memberJwt()` with `TestJwtFactory.*`
3. Replace private `createProject()`/`createCustomer()`/etc. with `TestEntityHelper.*`
4. Remove the now-unused private methods
5. Update `"clerkOrgId"` → `"externalOrgId"` in any remaining inline JSON (should be eliminated by using helpers)
6. Update `clerk_` prefixed test data IDs to neutral names

#### Slice 3A: Core Domain Tests (~40 files)
- [ ] `project/` (6 files)
- [ ] `document/` (5 files)
- [ ] `member/` (5 files)
- [ ] `task/` (4 files)
- [ ] `customer/` (8 files)
- [ ] `mywork/` (2 files)
- [ ] `tag/` (3 files)
- [ ] `dashboard/` (1 file)
- [ ] `view/` (2 files)
- [ ] Run `./mvnw test` — all tests pass

#### Slice 3B: Financial Domain Tests (~35 files)
- [ ] `invoice/` (8 files)
- [ ] `billing/` (7 files)
- [ ] `billingrate/` (2 files)
- [ ] `billingrun/` (8 files)
- [ ] `budget/` (3 files)
- [ ] `expense/` (2 files)
- [ ] `rate/` (2 files)
- [ ] `report/` (3 files)
- [ ] Run `./mvnw test` — all tests pass

#### Slice 3C: Compliance & Lifecycle Tests (~25 files)
- [ ] `compliance/` (12 files)
- [ ] `acceptance/` (5 files)
- [ ] `retention/` (3 files)
- [ ] `dataaccess/` (4 files)
- [ ] Run `./mvnw test` — all tests pass

#### Slice 3D: Communication & Activity Tests (~20 files)
- [ ] `notification/` (4 files)
- [ ] `comment/` (3 files)
- [ ] `activity/` (3 files)
- [ ] `audit/` (11 files)
- [ ] Run `./mvnw test` — all tests pass

#### Slice 3E: Advanced Feature Tests (~30 files)
- [ ] `automation/` (6 files)
- [ ] `template/` (4 files)
- [ ] `proposal/` (6 files)
- [ ] `retainer/` (3 files)
- [ ] `schedule/` (7 files)
- [ ] `clause/` (8 files)
- [ ] Run `./mvnw test` — all tests pass

#### Slice 3F: Portal & Vertical Tests (~30 files)
- [ ] `portal/` (5 files)
- [ ] `customerbackend/` (3 files)
- [ ] `vertical/` (18 files — legal domain tests)
- [ ] Run `./mvnw test` — all tests pass

#### Slice 3G: Infrastructure & Security Tests (~20 files)
- [ ] `security/` (5 files)
- [ ] `provisioning/` (2 files)
- [ ] `orgrole/` (8 files)
- [ ] `assistant/` (6 files)
- [ ] `config/` (any test configs)
- [ ] Run `./mvnw test` — all tests pass

#### Slice 3H: Remaining Stragglers & Cleanup
- [ ] Any files missed in earlier slices
- [ ] Remove `clerk_` test data IDs — use neutral IDs like `"user_owner"`, `"user_admin"`
- [ ] Search for any remaining `clerk` references in test code
- [ ] Final `./mvnw test` — all 800+ tests pass
- [ ] `grep -r "clerkOrgId\|clerkUserId\|findByClerkOrgId\|clerk_" backend/src/test/` returns 0 results

---

### Epic 4: Enforce TestCustomerFactory Usage (Quick Win)

53 test files still use `new Customer(...)` without explicit lifecycle status.

- [ ] Find all `new Customer(` in test code
- [ ] Replace with `TestCustomerFactory.createActiveCustomer()` or `createCustomerWithStatus()`
- [ ] Run `./mvnw test` — all tests pass

**Files touched**: ~53 test files

---

### Epic 5: Update Documentation

- [ ] Update `backend/CLAUDE.md` — remove all Clerk references, document new test utilities
- [ ] Add "Test Utilities" section to `backend/CLAUDE.md` documenting `TestMemberHelper`, `TestJwtFactory`, `TestEntityHelper`
- [ ] Update anti-patterns section: "Never define private syncMember/ownerJwt helpers — use testutil classes"
- [ ] Update `CLAUDE.md` root if it references Clerk

---

## Execution Notes

### Order of Operations
```
Epic 1 (prod rename) — DEFERRED. Helpers absorb Clerk naming into 3 files.
Epic 2 (create utilities) → Epic 3 (bulk migrate) → Epic 4 (factory enforcement) → Epic 5 (docs)
```

Epic 2 uses current API field names (`clerkOrgId`/`clerkUserId`). Future prod rename = 3-file change.
Epic 3 slices (3A–3H) are sequential within the epic but each is a standalone PR.
Epic 4 can run in parallel with Epic 3.
Epic 5 runs last.

### Risk Mitigation
- **DB column names stay unchanged** — no Flyway migration needed for the rename
- **Each slice runs full test suite** — catch regressions immediately
- **`@JsonProperty` aliases** in Epic 1A provide backward compat during transition
- **No base class inheritance** — utilities are static helpers, so no fragile base class issues

### Estimated Impact
| Metric | Before | After |
|--------|--------|-------|
| Duplicated helper LOC | ~10,000–13,000 | ~300 (3 utility files) |
| Files with `clerk` references | 332 test + 43 prod | 0 |
| `syncMember()` definitions | 257 | 1 |
| JWT builder definitions | ~600 (3 per file × 195) | ~6 (in TestJwtFactory) |
| Test utility files | 3 | 6 |

### What This Plan Does NOT Cover
- Splitting oversized test files (2500+ LOC) — low priority, not a DRY issue
- Adding a base test class for annotations — discussed but deferred; annotation repetition is noisy but harmless, and base classes can introduce coupling
- Custom AssertJ assertions — nice-to-have but not a duplication problem
