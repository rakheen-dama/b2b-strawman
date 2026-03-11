# Backend Code Quality Improvement Plan

**Date**: 2026-03-10
**Scope**: `backend/src/main/java` — 856 classes, 87,348 LOC, 101 packages
**Goal**: Reduce duplication, lower cyclomatic complexity in hotspots, eliminate code smells — without changing external behavior.

---

## Executive Summary

The backend is well-organized (feature-first packages, clean naming, healthy avg 102 LOC/class). However, rapid phase-by-phase delivery has introduced:

- **~1,500 lines** of identifiable duplication across CRUD services, pack seeders, and controllers
- **5 God methods** exceeding 100 lines with 8+ conditional branches
- **50+ method signatures** sharing the same `(UUID memberId, String orgRole)` data clump
- **3 controllers** that bypass the service layer and call repositories directly
- **6 single-implementation interfaces** adding cognitive overhead without benefit

The plan is organized into 7 epics (0–6), ordered by dependency. **Epic 0 must complete before any production refactoring begins** — it fixes tests that are coupled to implementation details, which would produce false failures during refactoring.

---

## Epic 0: Decouple Implementation-Tied Tests (PREREQUISITE)

**Impact**: CRITICAL | **Risk**: LOW | **Scope**: ~35 test files, ~140 test methods

### Why This Comes First

The test suite is the safety net for all subsequent refactoring. But **35 unit test files** use Mockito `verify()`, `ArgumentCaptor`, `InOrder`, and direct constructor instantiation — they test *how* code works internally, not *what* it produces. These tests will produce false failures during Epics 1–6 even when behavior is preserved, undermining confidence in the refactoring.

### Category A: verify()-Heavy Tests → Convert to Behavior Assertions (18 files, ~100 verify() calls)

Tests that assert "method X was called N times" instead of "the correct result was produced." These break when internal delegation changes (Epic 2 method extraction, Epic 6 service splits).

**Critical files (30+ verify calls, will break across multiple epics):**

| File | verify() calls | Breaks during |
|------|---------------|---------------|
| `PortalEventHandlerTest.java` | 30+ | Epic 2, 6 |
| `DocumentServiceTest.java` | 18 | Epic 2, 6 |
| `OrgRoleServiceTest.java` | 9 | Epic 6 |
| `NotificationDispatcherTest.java` | 10 | Epic 6 |
| `TenantProvisioningServiceTest.java` | 5 | Epic 6 |

**Fix strategy**: For each `verify()` call, determine what observable side effect it's guarding and assert on that instead:
- `verify(repo).save(entity)` → assert the entity exists in the DB after the operation
- `verify(eventPublisher).publishEvent(any())` → use `@RecordApplicationEvents` and assert on captured events
- `verify(auditService).log(...)` → query the audit table and assert on the audit record

**Where verify() is acceptable (DO NOT convert)**:
- `verifyNoInteractions()` in negative test cases (testing that nothing happens) — keep these
- `verify()` on external integration mocks (Stripe, SendGrid) — these test contract compliance, not internals

### Category B: ArgumentCaptor Tests → Assert on Outputs (5 files)

Tests that capture arguments passed to mocked collaborators and assert on their structure. This tests *how* data flows between classes, not what the system produces.

| File | What's captured | Fix |
|------|----------------|-----|
| `DocumentServiceTest.java` | S3 key format | Assert on returned URL/key from the public API |
| `DataExportServiceTest.java` | S3 upload key/content-type | Assert on the export result, verify S3 via LocalStack |
| `AcceptanceCertificateServiceTest.java` | Thymeleaf template context vars | Assert on rendered PDF content/structure |
| `StripePaymentGatewayTest.java` | Stripe SessionCreateParams | Keep — this is external contract testing |
| `SendGridEmailProviderTest.java` | Request JSON body | Keep — this is external contract testing |

### Category C: Direct Constructor Instantiation → Spring Context (9 files)

Tests that call `new SomeService(dep1, dep2, ..., depN)` directly. These break immediately when constructor signatures change (Epic 1 ActorContext, Epic 6 splits).

| File | Constructor params | Fix |
|------|-------------------|-----|
| `TenantProvisioningServiceTest.java` | 11 params + spy() | Convert to `@SpringBootTest` + `@MockitoBean` for external deps |
| `EngagementPrerequisiteTest.java` | 6 params | Convert to `@SpringBootTest` |
| `DocumentGenerationReadinessServiceTest.java` | Direct construction | Convert to `@SpringBootTest` |
| `CustomerReadinessServiceTest.java` | Direct construction | Convert to `@SpringBootTest` |
| `UnbilledTimeSummaryServiceTest.java` | Direct construction | Convert to `@SpringBootTest` |
| `ViewFilterServiceTest.java` | Direct construction | Convert to `@SpringBootTest` |

**Note**: `PdfConversionServiceTest` (no-arg constructor) and `TaxCalculationServiceTest` (no-arg constructor) are safe — no parameters to break.

### Category D: Reflection-Based ID Injection → Test Factory Methods (13 files)

Tests use `getDeclaredField("id").setAccessible(true)` to inject IDs into JPA entities. While these won't break during *this* refactoring plan, they're fragile and should be fixed opportunistically.

**Fix**: Create `TestEntityFactory` utility methods that use JPA's `@PersistenceContext` to persist-and-flush entities (getting real IDs), or use a shared reflection helper that's maintained in one place:

```java
public final class TestIds {
    public static <T> T withId(T entity, UUID id) {
        // Single reflection helper, maintained once
    }
}
```

**Files using reflection** (13): `TaxCalculationServiceTest`, `InvoiceRecalculationTest`, `ProjectServiceTest`, `DocumentServiceTest`, `TaskServiceTest`, `OrgRoleServiceTest`, `CustomFieldValidatorTest`, `EngagementPrerequisiteTest`, `ClauseResolverTest`, `RetainerPeriodTaxIntegrationTest`, `TaxRateBatchRecalcIntegrationTest`, `PortalEventHandlerTest`, `PortalEventHandlerCommentTest`

### Category E: Exception Message Assertions → Exception Type Assertions (10 files, ~25 assertions)

Tests that assert on exact exception message strings (`hasMessageContaining("3 task(s)")`) break if error messages are reworded. Most of these should assert on exception *type* and *key fields* instead.

**High-risk files:**
- `ProjectServiceTest.java` — 6 message assertions on delete guards
- `ExpenseTest.java` — 7 message assertions on billing state
- `MagicLinkTokenIntegrationTest.java` — 4 message assertions
- `AccessRequestApprovalServiceTest.java` — 1 exact `hasMessage()` (CRITICAL)

**Fix strategy**:
- Where the exception class is specific enough (e.g., `ResourceConflictException`), assert on type only
- Where the message carries domain meaning (e.g., "already been used"), assert with `containsIgnoringCase()` for resilience
- For `hasMessage()` exact matches → convert to `hasMessageContaining()` at minimum

### Tasks (ordered)
- [ ] 0A: Create `TestIds.withId()` shared reflection helper, migrate 13 files
- [ ] 0B: Convert `TenantProvisioningServiceTest` to `@SpringBootTest` (highest-risk constructor)
- [ ] 0C: Convert `EngagementPrerequisiteTest` + 4 other direct-constructor tests to `@SpringBootTest`
- [ ] 0D: Convert `PortalEventHandlerTest` verify() calls to behavior assertions (biggest file)
- [ ] 0E: Convert `DocumentServiceTest` verify() + ArgumentCaptor to output assertions
- [ ] 0F: Convert `OrgRoleServiceTest` verify() calls to behavior assertions
- [ ] 0G: Relax exception message assertions across 10 files
- [ ] 0H: Verify: `./mvnw clean verify -q` — all tests pass with identical behavior

### What NOT to Touch
- **MockMvc integration tests** (176 files) — already test behavior via HTTP, refactoring-safe
- **Stripe/SendGrid ArgumentCaptor tests** — these test external API contracts, which is correct
- **InOrder in ClauseResolverTest** — only fix if Epic 2/6 actually touches clause resolution
- **Audit detail key assertions** — these are testing observable output (the audit record), which is correct behavior testing

---

## Epic 1: Extract `ActorContext` Value Object (Data Clump Elimination)

**Impact**: HIGH | **Risk**: LOW | **Estimated duplication removed**: ~200 lines

### Problem
The tuple `(UUID memberId, String orgRole)` appears in 50+ method signatures across 11+ service classes. This is the most pervasive data clump in the codebase.

### Affected Files
| File | Occurrences |
|------|-------------|
| `task/TaskService.java` | 8 methods |
| `task/TaskItemService.java` | 6 methods |
| `timeentry/TimeEntryService.java` | 7 methods |
| `project/ProjectService.java` | 7 methods |
| `dashboard/DashboardService.java` | 6 methods |
| `expense/ExpenseService.java` | 5 methods |
| `document/DocumentService.java` | 5 methods |
| `customer/CustomerProjectService.java` | 5 methods |
| `settings/OrgSettingsService.java` | 4 methods |
| `member/ProjectAccessService.java` | 3 methods |
| `budget/ProjectBudgetService.java` | 3 methods |

### Implementation

1. Create `ActorContext` record in `multitenancy/` package:
   ```java
   public record ActorContext(UUID memberId, String orgRole) {
       public static ActorContext fromRequestScopes() {
           return new ActorContext(
               RequestScopes.requireMemberId(),
               RequestScopes.getOrgRole()
           );
       }
       public boolean isOwnerOrAdmin() {
           return "ORG_OWNER".equals(orgRole) || "ORG_ADMIN".equals(orgRole);
       }
   }
   ```
2. Replace `(UUID memberId, String orgRole)` pairs in all service method signatures with `ActorContext`
3. Update all controller call sites to pass `ActorContext.fromRequestScopes()`
4. Update all tests to construct `ActorContext` directly

### Tasks
- [ ] Create `ActorContext` record with factory method and convenience accessors
- [ ] Refactor `TaskService` + `TaskItemService` (14 methods)
- [ ] Refactor `TimeEntryService` + `ExpenseService` (12 methods)
- [ ] Refactor `ProjectService` + `ProjectAccessService` (10 methods)
- [ ] Refactor remaining services (DashboardService, DocumentService, etc.)
- [ ] Update all controllers to use `ActorContext.fromRequestScopes()`
- [ ] Update all integration tests
- [ ] Verify: `./mvnw clean verify -q`

---

## Epic 2: Decompose God Methods (Cyclomatic Complexity)

**Impact**: HIGH | **Risk**: MEDIUM | **Methods affected**: 5

### Problem
Five methods have cyclomatic complexity that makes them hard to test, review, and modify safely.

### Target Methods

#### 2A. `InvoiceService.createDraft()` — 246 lines, 15+ branches (HIGHEST PRIORITY)
**File**: `invoice/InvoiceService.java:184–427`

Extract into:
- `validateInvoicePrerequisites(customer, project)` — lifecycle guard + prerequisite checks
- `createTimeEntryLines(invoice, timeEntryIds)` — loop with task lookup, rate snapshotting
- `createExpenseLines(invoice, expenseIds)` — loop with expense validation, markup calc
- `applyDefaultTax(invoice, orgSettings)` — tax rate lookup and application
- `applyFieldGroups(invoice, fieldGroupIds)` — auto-apply group resolution

#### 2B. `BillingRunService.loadPreview()` — 110 lines, 8+ branches
**File**: `billingrun/BillingRunService.java:247–353`

Extract into:
- `discoverEligibleCustomers(billingRun)` — auto-discover vs. provided list
- `buildPreviewItem(customer, billingRun)` — prerequisite checks + aggregation
- `aggregatePreviewTotals(items)` — BigDecimal accumulation

#### 2C. `TimeEntryService.createTimeEntry()` — 92 lines, 9+ branches
**File**: `timeentry/TimeEntryService.java:89–181`

Extract into:
- `validateProjectAndCustomer(projectId)` — archived check + lifecycle guard
- `snapshotRates(timeEntry, project)` — billing rate + cost rate capture

#### 2D. `ExpenseService.updateExpense()` — 92 lines, 7+ branches
**File**: `expense/ExpenseService.java:192–283`

Extract into:
- `validateExpenseUpdate(expense, request)` — billed guard + project lifecycle
- `applyExpenseChanges(expense, request)` — null-coalescing field updates

#### 2E. `AcceptanceService` — 1,199 lines, 26 public methods
**File**: `acceptance/AcceptanceService.java`

Split into:
- `AcceptanceService` — core CRUD + state transitions (keep)
- `AcceptanceNotificationService` — email/notification dispatch (extract)
- `AcceptanceCeremonyService` — certificate generation (extract)

### Tasks
- [ ] 2A: Decompose `InvoiceService.createDraft()` into 5 private methods
- [ ] 2B: Decompose `BillingRunService.loadPreview()` into 3 private methods
- [ ] 2C: Decompose `TimeEntryService.createTimeEntry()` into 2 private methods
- [ ] 2D: Decompose `ExpenseService.updateExpense()` into 2 private methods
- [ ] 2E: Extract `AcceptanceNotificationService` and `AcceptanceCeremonyService`
- [ ] Verify each decomposition preserves exact behavior (tests must pass unchanged)

---

## Epic 3: Extract `AbstractPackSeeder` Base Class (Seeder Duplication)

**Impact**: HIGH | **Risk**: LOW | **Estimated duplication removed**: ~600 lines

### Problem
6 seeder classes duplicate identical logic for: classpath resource loading, JSON parsing, pack-already-applied checks, OrgSettings tracking.

### Affected Files
| Seeder | Lines | Package |
|--------|-------|---------|
| `TemplatePackSeeder` | ~120 | template |
| `ClausePackSeeder` | ~120 | clause |
| `FieldPackSeeder` | ~120 | fielddefinition |
| `CompliancePackSeeder` | ~120 | compliance |
| `RequestPackSeeder` | ~120 | informationrequest |
| `AutomationTemplateSeeder` | ~120 | automation/template |

### Implementation

1. Create `AbstractPackSeeder<T>` in a shared `seeder/` package:
   ```java
   public abstract class AbstractPackSeeder<T> {
       // Template method pattern
       protected abstract String getPackResourcePattern();
       protected abstract Class<T[]> getPackArrayType();
       protected abstract String getPackSettingsKey();
       protected abstract void seedPackEntities(List<T> items);

       // Shared implementation
       public final void seedPacksForTenant(String tenantId, String orgId) { ... }
       private List<T> loadPacks() { ... }
       private boolean isPackAlreadyApplied(OrgSettings settings, String packId) { ... }
   }
   ```
2. Convert each seeder to extend `AbstractPackSeeder<T>`, implementing only the 4 abstract methods
3. Each seeder shrinks from ~120 lines to ~30 lines

### Tasks
- [ ] Create `AbstractPackSeeder<T>` with template method pattern
- [ ] Migrate `TemplatePackSeeder` (pilot — verify approach)
- [ ] Migrate remaining 5 seeders
- [ ] Verify: all pack seeding integration tests pass
- [ ] Remove duplicated code from each seeder

---

## Epic 4: Fix Service Layer Violations (Controllers Calling Repos)

**Impact**: HIGH | **Risk**: LOW | **Files affected**: 3 controllers

### Problem
Three controllers bypass the service layer and call repositories directly, mixing HTTP handling with business logic.

### 4A. `ReportingController` — Most Severe
**File**: `reporting/ReportingController.java:118–193`

**Violations**:
- `reportDefinitionRepository.findBySlug()` called directly (lines 123, 163)
- PDF export orchestration in controller (fetch definition → execute → render → audit)
- CSV export with audit logging in `finally` block
- Audit event construction in controller

**Fix**: Create `ReportExportService` with:
- `exportAsPdf(String slug, Map<String, Object> params): byte[]`
- `exportAsCsv(String slug, Map<String, Object> params): byte[]`
- Move audit logging into service

### 4B. `DataRequestController`
**File**: `datarequest/DataRequestController.java:185–189`

**Violations**:
- `customerRepository.findAllById()` called directly
- `resolveCustomerNames()` method in controller

**Fix**: Move `resolveCustomerNames()` to `DataRequestService` or use existing `CustomerNameResolver`.

### 4C. `DocumentController`
**File**: `document/DocumentController.java:35`

**Violation**: `MemberRepository` injected but potentially unused.

**Fix**: Remove unused injection or move usage to service.

### Tasks
- [ ] Create `ReportExportService` and move orchestration + audit logic
- [ ] Slim `ReportingController` to pure delegation
- [ ] Move `resolveCustomerNames()` from `DataRequestController` to service
- [ ] Audit `DocumentController` for unused repo injection
- [ ] Verify: all controller integration tests pass

---

## Epic 5: Extract Shared CRUD Utilities (Service Duplication)

**Impact**: MEDIUM | **Risk**: MEDIUM | **Estimated duplication removed**: ~400 lines

### Problem
4+ services repeat identical patterns for: custom field validation, field group resolution, audit delta building.

### 5A. Extract `FieldGroupResolver` Utility
**Affected**: CustomerService, ProjectService, TaskService, InvoiceService

The `setFieldGroups()` pattern (validate groups exist, check entity type, resolve dependencies, collect field def IDs) is duplicated across 4 services at ~30 lines each.

```java
@Component
public class FieldGroupResolver {
    public List<UUID> resolveAndValidate(
        Set<UUID> groupIds, EntityType entityType,
        FieldGroupRepository groupRepo, FieldGroupMemberRepository memberRepo,
        FieldGroupService groupService
    ) { ... }
}
```

### 5B. Extract `AuditDeltaBuilder` Utility
**Affected**: CustomerService, ProjectService, TaskService, and 10+ other services

The "compare old vs new, build delta map" pattern repeats everywhere:

```java
public class AuditDeltaBuilder {
    private final Map<String, Object> deltas = new LinkedHashMap<>();

    public AuditDeltaBuilder track(String field, Object oldVal, Object newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            deltas.put(field, Map.of("from", oldVal, "to", newVal));
        }
        return this;
    }

    public Map<String, Object> build() {
        return deltas.isEmpty() ? null : Map.copyOf(deltas);
    }
}
```

### 5C. Extract `DeleteGuard` Pattern
**Affected**: CustomerService, ProjectService

The "check N repositories for linked resources before delete" pattern:

```java
public class DeleteGuard {
    public static DeleteGuard forEntity(String entityName, UUID entityId) { ... }
    public DeleteGuard checkNotExists(String resource, Supplier<Boolean> exists) { ... }
    public DeleteGuard checkCountZero(String resource, Supplier<Long> count) { ... }
    public void execute() { /* throws ResourceConflictException if any guard failed */ }
}
```

### Tasks
- [ ] 5A: Create `FieldGroupResolver` and refactor 4 services
- [ ] 5B: Create `AuditDeltaBuilder` and refactor 10+ services
- [ ] 5C: Create `DeleteGuard` and refactor CustomerService + ProjectService
- [ ] Verify: all integration tests pass

---

## Epic 6: Reduce Constructor Bloat (Dependency Injection Cleanup)

**Impact**: MEDIUM | **Risk**: LOW | **Files affected**: 2 major services

### Problem
`ProjectService` has 19 injected dependencies and `TimeEntryService` has 15. This signals SRP violations — these services coordinate too many concerns.

### 6A. `ProjectService` (19 → ~10 dependencies)

Extract:
- `ProjectFieldService` — handles custom fields + field groups (absorbs FieldGroupResolver from Epic 5)
- `ProjectDeletionGuard` — handles all pre-delete checks (absorbs DeleteGuard from Epic 5)

The remaining `ProjectService` handles core CRUD + lifecycle, delegating to the extracted services.

### 6B. `TimeEntryService` (15 → ~8 dependencies)

Extract:
- `TimeEntryValidationService` — project/customer lifecycle checks
- `RateSnapshotService` — billing + cost rate snapshotting

### Tasks
- [x] 6A: Extract `ProjectFieldService` from `ProjectService` — **Done** (PR #667)
- [x] 6A: Extract `ProjectDeletionGuard` from `ProjectService` — **Done** (PR #667)
- [x] 6B: Extract `TimeEntryValidationService` from `TimeEntryService` — **Done** (PR #667)
- [x] 6B: Extract `RateSnapshotService` from `TimeEntryService` — **Done** (PR #667)
- [x] Verify constructor parameter counts reduced to targets — **Done** (17→10, 14→9)
- [x] Verify: all integration tests pass — **Done** (3504 tests, 0 failures)

---

## Deferred / Not Recommended

### Single-Implementation Interfaces (6 interfaces)
**Decision**: DEFER. While `AuditService`, `StorageService`, `AccountingProvider`, `DocumentSigningProvider`, `AiProvider`, and `SecretStore` each have only one implementation today, these are **integration seams** — exactly where interfaces are appropriate. A BYOAK (Bring Your Own Auth/Keys) strategy is planned, which will add implementations. No action needed.

### Circular Package Dependencies (6 cycles)
**Decision**: DEFER. The cycles (invoice ↔ billingrun, acceptance ↔ customerbackend, etc.) are domain-driven, not architectural anti-patterns. Breaking them would require an event bus or shared DTOs package that adds complexity without improving cohesion.

### `PortalReadModelRepository` (952 lines, 40+ methods)
**Decision**: DEFER. This is a CQRS read model — it's inherently a projection of multiple aggregates into one query surface. The method count is high but each method is simple. Splitting would fragment the read model's cohesion.

### `NotificationService` (855 lines, 47 if-statements)
**Decision**: DEFER. The if-statements are event routing — each handles a different event type. A strategy pattern or event handler registry could clean this up, but it would also make the notification routing harder to trace. Current approach is explicit and debuggable.

---

## Execution Order

```
Epic 0 (Test decoupling)       ──→ PREREQUISITE — must complete before any production refactoring
    │
    ├── Epic 1 (ActorContext)          ──→ First production change (simplest, highest reach)
    ├── Epic 3 (AbstractPackSeeder)    ──→ Independent, can parallel with Epic 1
    └── Epic 4 (Service layer fixes)   ──→ Independent, can parallel with Epic 1
         │
         Epic 2 (God method decomp)    ──→ After Epic 1 (methods use memberId/orgRole)
         │
         Epic 5 (CRUD utilities)       ──→ After Epic 2 (builds on decomposed methods)
         │
         Epic 6 (Constructor cleanup)  ──→ After Epic 5 (uses extracted utilities)
```

**Phase 1**: Epic 0 (test hardening)
**Phase 2**: Epics 1, 3, and 4 in parallel
**Phase 3**: Epics 2→5→6 sequentially

---

## Success Metrics

| Metric | Before | Target |
|--------|--------|--------|
| Tests with verify() on internal calls | ~100 calls in 18 files | <10 (only external contracts) |
| Tests with direct constructor instantiation | 9 files | 0 (all use Spring context) |
| Tests with exact message assertions | ~25 assertions | 0 (all use type or containsIgnoringCase) |
| Reflection ID injection locations | 13 files | 1 (shared `TestIds` helper) |
| `(memberId, orgRole)` parameter pairs | 50+ | 0 |
| Seeder avg LOC | ~120 | ~30 |
| `InvoiceService.createDraft()` LOC | 246 | <50 (orchestrator) |
| Controllers with repo injection | 3 | 0 |
| `ProjectService` constructor params | 19 | ~10 |
| `TimeEntryService` constructor params | 15 | ~8 |
| Total duplicated LOC removed | — | ~1,500 |

---

## Risk Mitigation

1. **Pure refactoring** — no feature changes, no API changes, no migration changes
2. **Each epic is independently shippable** — can stop after any epic
3. **Existing tests are the safety net** — if tests pass, behavior is preserved
4. **Method-level extractions first** (Epic 2) before class-level extractions (Epic 6)
5. **Pilot approach** for Epic 3 — migrate one seeder first, verify, then batch the rest
