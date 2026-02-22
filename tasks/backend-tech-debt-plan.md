# Backend Technical Debt Clearance Plan

**Date:** 2026-02-22
**Sources:** `tasks/backend-audit-findings.md`, `tasks/backend-test-audit.md`
**Goal:** Clear all actionable tech debt from both audits in incremental, agent-safe slices

---

## Execution Strategy

- Each task is a self-contained slice executable by an independent builder agent
- Tasks are ordered by dependency — later tasks may depend on earlier ones (noted explicitly)
- Context budget: each task touches ≤15 files and includes a full brief
- All tasks target `main` via worktree branches (`tech-debt/TD-{N}`)
- Run `./mvnw clean verify -q` after each task to confirm green

---

## Task TD-1: Add RequestScopes Helper Methods

**Branch:** `tech-debt/td-1-request-scopes-helpers`
**Risk:** Low | **Effort:** 15 min | **Files:** ~5-8
**Depends on:** Nothing

### Problem
40+ call sites repeat `RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null` and the equivalent for `ORG_ID`. The class already has `requireMemberId()`, `requireOrgId()`, `getOrgRole()` but lacks nullable getters for `TENANT_ID` and `ORG_ID`.

### Brief
1. **Add to `RequestScopes.java`** (`backend/src/main/java/.../multitenancy/RequestScopes.java`):
   ```java
   public static String getTenantIdOrNull() {
       return TENANT_ID.isBound() ? TENANT_ID.get() : null;
   }
   public static String getOrgIdOrNull() {
       return ORG_ID.isBound() ? ORG_ID.get() : null;
   }
   ```
2. **Find-and-replace** all instances of the ternary pattern across the codebase:
   - Search: `RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null`
   - Replace: `RequestScopes.getTenantIdOrNull()`
   - Same for `ORG_ID` variant → `RequestScopes.getOrgIdOrNull()`
3. Grep for any remaining `TENANT_ID.isBound()` + `TENANT_ID.get()` two-line patterns and convert those too.

### Verification
- `./mvnw clean verify -q` passes
- `grep -r "TENANT_ID.isBound.*TENANT_ID.get" backend/src/main/` returns 0 hits
- `grep -r "ORG_ID.isBound.*ORG_ID.get" backend/src/main/` returns 0 hits

---

## Task TD-2: Fix Unsafe RequestScopes.MEMBER_ID.get() in Controllers

**Branch:** `tech-debt/td-2-require-member-id`
**Risk:** Low | **Effort:** 10 min | **Files:** 2
**Depends on:** Nothing

### Problem
Convention says controllers must use `RequestScopes.requireMemberId()` (throws if unbound), not `.MEMBER_ID.get()` (silent null). Four call sites violate this.

### Brief
In these two files, replace `RequestScopes.MEMBER_ID.get()` → `RequestScopes.requireMemberId()`:

1. **`template/DocumentTemplateController.java`** — lines 117 and 129
2. **`template/GeneratedDocumentController.java`** — lines 44 and 57

These endpoints are all behind `@PreAuthorize` so `MEMBER_ID` is always bound, but `requireMemberId()` is the convention and provides a clear error if the invariant is ever violated.

### Verification
- `./mvnw clean verify -q` passes
- `grep -rn "MEMBER_ID.get()" backend/src/main/` returns 0 hits in controller files

---

## Task TD-3: Shared LocalStack Container in Test Configuration

**Branch:** `tech-debt/td-3-shared-localstack`
**Risk:** Medium | **Effort:** 30 min | **Files:** 6
**Depends on:** Nothing

### Problem
5 test classes each start their own `LocalStackContainer` (~5-7s startup each = ~25-35s wasted). The Postgres container is already shared via `TestcontainersConfiguration` — LocalStack should follow the same pattern.

### Brief
1. **Edit `TestcontainersConfiguration.java`** — add a shared LocalStack bean:
   ```java
   @Bean
   LocalStackContainer localStackContainer() {
       var container = new LocalStackContainer(
           DockerImageName.parse("localstack/localstack:latest"))
           .withServices(LocalStackContainer.Service.S3);
       container.start();
       return container;
   }

   @Bean
   DynamicPropertyRegistrar s3Properties(LocalStackContainer localstack) {
       return registry -> {
           registry.add("aws.s3.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
           registry.add("aws.s3.region", () -> localstack.getRegion());
           registry.add("aws.credentials.access-key-id", () -> localstack.getAccessKey());
           registry.add("aws.credentials.secret-access-key", () -> localstack.getSecretKey());
       };
   }
   ```
   Note: Check the exact property names used in the existing tests' `@DynamicPropertySource` methods — the shared bean must set the same properties.

2. **Remove per-class LocalStack** from these 5 test files:
   - `s3/S3PresignedUrlServiceTest.java` — remove `@Container` field + `@DynamicPropertySource` method
   - `template/DocumentGenerationIntegrationTest.java` — remove static LocalStack + `@DynamicPropertySource`
   - `template/GeneratedDocumentControllerTest.java` — remove static LocalStack + `@DynamicPropertySource`
   - `template/DocumentAuditNotificationTest.java` — remove static LocalStack + `@DynamicPropertySource`
   - `settings/BrandingIntegrationTest.java` — remove static LocalStack + `@DynamicPropertySource`

3. **Ensure bucket creation** — some tests create buckets in `@BeforeAll`. The shared config should create the default S3 bucket. Check what bucket name is used (likely from `aws.s3.bucket-name` property) and create it in the `localStackContainer()` bean or a separate `@Bean` that depends on the container.

### Verification
- `./mvnw clean verify -q` passes
- All 5 previously-LocalStack tests still pass
- `grep -rn "LocalStackContainer" backend/src/test/` only shows `TestcontainersConfiguration.java`
- Build time improves by ~20s (optional: measure before/after with `time ./mvnw clean verify -q`)

---

## Task TD-4: Delete Redundant Test Files + Merge Unique Assertions

**Branch:** `tech-debt/td-4-delete-redundant-tests`
**Risk:** Medium | **Effort:** 45 min | **Files:** ~6
**Depends on:** TD-3 (shared LocalStack — `DocumentTemplateIntegrationTest` uses LocalStack)

### Problem
3 test files (20 tests) are redundant per the test audit. Two have one unique assertion each that should be merged into their surviving counterparts before deletion.

### Brief

#### 4A: Delete `BackendApplicationTests.java`
- File: `backend/src/test/java/.../BackendApplicationTests.java`
- Contains 1 test (`contextLoads()`) with an empty body. Every other `@SpringBootTest` also boots context.
- **Action:** Delete the file. No merge needed.

#### 4B: Merge + Delete `DocumentTemplateIntegrationTest.java`
- File: `backend/src/test/java/.../template/DocumentTemplateIntegrationTest.java`
- 8 tests, all covered by `DocumentTemplateControllerTest` or `DocumentTemplateSlugTest`
- **Before deleting:** Find `shouldIsolateBetweenTenants` test. Add an equivalent cross-tenant 404 assertion to `DocumentTemplateControllerTest`. The test should: create a template in tenant A, then switch to tenant B context and verify GET returns 404 (not the template from tenant A).
- **Action:** Add the merged test, then delete the integration test file.

#### 4C: Merge + Delete `BillingRateIntegrationTest.java`
- File: `backend/src/test/java/.../billingrate/BillingRateIntegrationTest.java`
- 11 tests, heavy overlap with `BillingRateControllerTest` (16 tests covering same flows)
- **Before deleting:** Find `createRate_rejectsCompoundScope` test. Add an equivalent 400 assertion to `BillingRateControllerTest`.
- **Action:** Add the merged test, then delete the integration test file.

#### 4D: Remove trivial test methods
- **`CustomerLifecycleEntityTest.java`** — remove 5 tests: `defaultLifecycleIsProspect`, `customerTypeDefaultsToIndividual`, `customerTypeConstructorAcceptsEnum`, `offboardedAtIsNullByDefault`, `lifecycleStatusConstructorSetsExplicitStatus`. Keep all state transition tests.
- **`RetainerAgreementEntityTest.java`** — remove 2 tests: `create_createdAtAndUpdatedAtSet`, `create_defaultsConsumedAndOverageToZero`.

### Verification
- `./mvnw clean verify -q` passes
- Deleted files no longer exist
- The merged assertions pass in their new homes
- Total test count drops by ~18 (20 deleted − 2 merged)

---

## Task TD-5: Extract View-Based Filtering from Controllers

**Branch:** `tech-debt/td-5-extract-view-filtering`
**Risk:** Medium-High | **Effort:** 60 min | **Files:** ~5-7
**Depends on:** Nothing

### Problem
`ProjectController`, `TaskController`, and `CustomerController` each duplicate ~40 lines of identical view-based filtering logic: load SavedView → validate entity type → execute filter query → apply access control. They also duplicate `extractCustomFieldFilters()` and `matchesCustomFieldFilters()` helper methods.

### Brief

#### 5A: Create `ViewFilterHelper` in the `view/` package
Create `backend/src/main/java/.../view/ViewFilterHelper.java`:
```java
@Component
public class ViewFilterHelper {

    private final SavedViewRepository savedViewRepository;
    private final ViewFilterService viewFilterService;

    // Constructor injection

    /**
     * Resolve a SavedView, validate entity type, execute filter query,
     * and apply access control for non-admin users.
     */
    public <T> List<T> applyViewFilter(
            UUID viewId,
            String entityType,        // "PROJECT", "TASK", "CUSTOMER"
            String tableName,         // "projects", "tasks", "customers"
            Class<T> entityClass,
            Set<UUID> accessibleIds,  // null for admins (no filtering)
            Function<T, UUID> idExtractor) {

        var savedView = savedViewRepository.findById(viewId)
            .orElseThrow(() -> new ResourceNotFoundException("SavedView", viewId));

        if (!entityType.equals(savedView.getEntityType())) {
            throw new InvalidStateException("View mismatch",
                "View is for " + savedView.getEntityType() + ", not " + entityType);
        }

        List<T> filtered = viewFilterService.executeFilterQuery(
            tableName, entityClass, savedView.getFilters(), entityType);

        if (accessibleIds != null) {
            filtered = filtered.stream()
                .filter(e -> accessibleIds.contains(idExtractor.apply(e)))
                .toList();
        }

        return filtered;
    }
}
```

Note: Study the actual controller code closely — the exact error types, filter map construction, and access control logic may differ slightly between controllers. Match the existing behavior exactly.

#### 5B: Extract shared static helpers
Create or add to a utility class (e.g., `view/ViewFilterHelper.java` or a separate `view/CustomFieldFilterUtil.java`):
- `extractCustomFieldFilters(Map<String, String> allParams)` — static method
- `matchesCustomFieldFilters(Map<String, Object> customFields, Map<String, String> filters)` — static method

#### 5C: Refactor the three controllers
Replace the inline view-filter blocks in:
- `project/ProjectController.java`
- `task/TaskController.java`
- `customer/CustomerController.java`

With calls to `viewFilterHelper.applyViewFilter(...)` and the extracted static helpers.

### Verification
- `./mvnw clean verify -q` passes
- All existing view filter tests pass (SavedViewIntegrationTest, ViewFilterIntegrationTest, etc.)
- Each controller's list endpoint tests pass unchanged
- `grep -rn "savedViewRepository" backend/src/main/java/` shows it only in `ViewFilterHelper` and the `view/` package (not in controllers)

---

## Task TD-6: Replace `new Customer(...)` with TestCustomerFactory in Integration Tests

**Branch:** `tech-debt/td-6-test-customer-factory`
**Risk:** Low-Medium | **Effort:** 45 min | **Files:** ~11 integration test files
**Depends on:** Nothing

### Problem
26+ instances across 17 test files use `new Customer(...)` without explicit `LifecycleStatus`, defaulting to `PROSPECT`. Convention says to use `TestCustomerFactory`. Integration tests are at risk if `CustomerLifecycleGuard` is extended.

### Brief
Replace `new Customer(...)` with `TestCustomerFactory.createActiveCustomer(...)` in the **integration test files only** (unit tests with mocks are lower risk and can be left as-is):

| File | Approx lines |
|------|-------------|
| `schedule/RecurringScheduleServiceTest.java` | 90 |
| `projecttemplate/InstantiateTemplateIntegrationTest.java` | 115, 146 |
| `datarequest/DataSubjectRequestServiceTest.java` | 75 |
| `datarequest/DataExportServiceTest.java` | 82 |
| `projecttemplate/V30MigrationTest.java` | 190, 240 |
| `projecttemplate/ProjectTemplateControllerTest.java` | 352 |
| `projecttemplate/ProjectTemplateServiceTest.java` | 222 |
| `schedule/RecurringScheduleExecutorTest.java` | 674 |
| `schedule/RecurringScheduleControllerTest.java` | 112 |
| `compliance/ChecklistInstantiationServiceTest.java` | 250 |
| `compliance/ComplianceProvisioningTest.java` | 110 |

**Pattern to find:** `new Customer("` or `new Customer(name` where it's constructing a test customer.

**Replace with:** `TestCustomerFactory.createActiveCustomer(name, email, createdByMemberId)`. Add the import: `import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;`

**Important:** Some constructors pass `CustomerType` (INDIVIDUAL/COMPANY). Use `TestCustomerFactory.createActiveCustomer(name, email, createdBy, type)` for those.

**Exception:** `CustomerLifecycleEntityTest.java` deliberately tests PROSPECT behavior — do NOT change that file.

### Verification
- `./mvnw clean verify -q` passes
- `grep -rn "new Customer(" backend/src/test/java/` returns only:
  - `CustomerLifecycleEntityTest.java` (intentional)
  - Pure unit test files (acceptable)
  - `TestCustomerFactory.java` itself

---

## Task TD-7: Extract Member Name Resolution Helper

**Branch:** `tech-debt/td-7-member-name-resolver`
**Risk:** Low | **Effort:** 30 min | **Files:** ~8
**Depends on:** Nothing

### Problem
10 instances across 6 services repeat `memberRepository.findById(id).map(m -> m.getName()).orElse("Unknown")`. This is a cross-cutting concern.

### Brief
1. **Create `MemberNameResolver`** in the `member/` package:
   ```java
   @Service
   public class MemberNameResolver {
       private final MemberRepository memberRepository;

       // Constructor injection

       public String resolveName(UUID memberId) {
           return memberRepository.findById(memberId)
               .map(Member::getName)
               .orElse("Unknown");
       }

       public Map<UUID, String> resolveNames(Collection<UUID> memberIds) {
           return memberRepository.findAllById(memberIds).stream()
               .collect(Collectors.toMap(Member::getId, Member::getName));
       }
   }
   ```
2. **Replace** the inline pattern in all services that use it. Search for `.map(m -> m.getName()).orElse("Unknown")` or similar patterns.
3. Use `resolveNames(Collection)` batch variant in controllers that resolve names for lists (currently in ProjectController, TaskController, CustomerController `resolveNames` private methods).

### Verification
- `./mvnw clean verify -q` passes
- `grep -rn "orElse.*Unknown" backend/src/main/java/` returns 0 hits in service files (only in `MemberNameResolver` itself)

---

## Execution Order & Dependencies

```
TD-1 (helpers)       ─── independent
TD-2 (require ID)    ─── independent
TD-3 (localstack)    ─── independent, but do before TD-4
TD-4 (delete tests)  ─── depends on TD-3
TD-5 (view filter)   ─── independent, largest task
TD-6 (test factory)  ─── independent
TD-7 (name resolver) ─── independent
```

**Recommended parallel tracks:**

| Track A | Track B | Track C |
|---------|---------|---------|
| TD-1 | TD-3 | TD-5 |
| TD-2 | TD-4 | TD-6 |
|       |      | TD-7 |

All three tracks can run in parallel since they touch different files.

---

## Impact Summary

| Task | Lines Removed | Lines Added | Tests Fixed | Time Saved |
|------|-------------|-------------|-------------|------------|
| TD-1 | ~40 | ~10 | 0 | — |
| TD-2 | 0 | 0 (4 edits) | 0 | — |
| TD-3 | ~75 | ~20 | 0 | ~20s build |
| TD-4 | ~600 | ~30 | −18 tests | ~20s build |
| TD-5 | ~150 | ~60 | 0 | — |
| TD-6 | ~26 | ~26 | 0 | — |
| TD-7 | ~30 | ~25 | 0 | — |
| **Total** | **~920** | **~175** | **−18** | **~40s** |

---

## Items Explicitly Deferred

| Item | Reason |
|------|--------|
| JUnit 5 parallel execution | Audit found it requires dedicated stabilization effort (10+ green runs). Not safe for incremental tasks. |
| `@PreAuthorize` on Portal endpoints | Intentionally public/portal-scoped. Adding would improve consistency but is not strictly required. Deferred to a portal security review. |
| "Find or Throw" pattern consolidation (186+ instances) | High blast radius — touches 50+ files. Would need its own dedicated PR with careful review. Consider as a future cleanup sprint. |
| `AuthContext` record + `HandlerMethodArgumentResolver` | Nice-to-have but would touch 40+ controller methods. Better as a dedicated refactor phase. |
| `DomainEventFactory` for event publishing context | 15+ service methods affected. Moderate risk of breaking event consumers. Defer to a dedicated audit. |
| Business logic in `OrgSettingsController` (file validation) | Only 1 controller, ~10 lines. Not worth extracting until a second file-upload endpoint exists. |
| Business logic in `GeneratedDocumentController` (access control branching) | Small, self-contained. Extract if more entity types are added. |
