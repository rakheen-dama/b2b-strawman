# Backend Code Audit — 2026-02-22

Audit of `backend/src/` against conventions in `backend/CLAUDE.md`, plus code duplication analysis.

---

## Convention Violations

### PASS — No Issues Found

| Convention | Status |
|---|---|
| No `@Autowired` on fields (main code) | PASS |
| No Lombok usage | PASS |
| No `ThreadLocal` usage | PASS |
| No direct `ProblemDetail` construction in controllers/services | PASS |
| No `Optional` returns for "not found" (all justified) | PASS |
| All controllers return `ResponseEntity` | PASS |
| No duplicate error helper methods in controllers | PASS |
| No `@Filter`/`@FilterDef`/`tenant_id` columns on entities | PASS |
| No `*Entity` class name suffix | PASS |
| No non-record DTOs | PASS |
| No missing `@Transactional` on write operations | PASS |
| No explicit `hibernate.multiTenancy` property | PASS |
| No `@ActiveProfiles("local")` in tests | PASS |
| No old `AutoConfigureMockMvc` import in tests | PASS |
| No `ThreadLocal` in tests | PASS |
| No flat JWT claims in test mocks | PASS |

### FAIL — Issues Found

#### 1. Non-Idempotent Migration (Severity: Medium)

**File:** `src/main/resources/db/migration/tenant/V36__create_integration_tables.sql`

Lines 4 and 16 use `CREATE TABLE` without `IF NOT EXISTS`:
```sql
CREATE TABLE org_secrets (        -- line 4, missing IF NOT EXISTS
CREATE TABLE org_integrations (   -- line 16, missing IF NOT EXISTS
```

**Risk:** Re-running migration during tenant provisioning retry will fail with "table already exists".

**Fix:** Add `IF NOT EXISTS` to both `CREATE TABLE` statements.

---

#### 2. Raw Customer Constructor in Tests (Severity: Medium)

26+ instances across 17 test files use `new Customer(...)` without explicit `LifecycleStatus`, defaulting to `PROSPECT`. Convention says to use `TestCustomerFactory`.

**Integration tests at risk** (may silently fail if `CustomerLifecycleGuard` is extended):

| File | Line(s) |
|---|---|
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

**Unit tests (lower risk — mocked, not persisted):**
- `template/ProjectContextBuilderTest.java`, `InvoiceContextBuilderTest.java`, `CustomerContextBuilderTest.java`
- `customer/CustomerLifecycleEntityTest.java` (deliberately tests PROSPECT behavior)
- `projecttemplate/NameTokenResolverTest.java`

**Fix:** Replace `new Customer(...)` with `TestCustomerFactory.createActiveCustomer()` in integration tests.

---

#### 3. Business Logic in Controllers (Severity: Medium)

**ProjectController** (lines 84–127) — View-based filtering with role checks, stream filtering, and SavedView validation inline in controller:
```java
if (view != null) {
    var savedView = savedViewRepository.findById(view).orElseThrow(...);
    if (!"PROJECT".equals(savedView.getEntityType())) { throw ... }
    List<Project> filtered = viewFilterService.executeFilterQuery(...);
    boolean isAdminOrOwner = "admin".equals(orgRole) || "owner".equals(orgRole);
    if (!isAdminOrOwner) {
        Set<UUID> accessibleIds = projectService.listProjects(memberId, orgRole).stream()...
        filtered = filtered.stream().filter(p -> accessibleIds.contains(p.getId())).toList();
    }
}
```

**TaskController** (lines 102–131) — Same view-based filtering pattern duplicated.

**CustomerController** (lines 98+) — Same view-based filtering pattern duplicated again.

**OrgSettingsController** (lines 69–78) — File validation logic (empty check, size check, content type check) inline in controller.

**GeneratedDocumentController** (lines 43–50) — Access control logic with entity type branching.

**Fix:** Extract view filtering into a shared service method. Extract file validation into a validator.

---

#### 4. Unsafe `RequestScopes.MEMBER_ID.get()` Without Bounds Check (Severity: Low)

Convention says use `RequestScopes.requireMemberId()`. These controllers call `.get()` directly:

| File | Lines |
|---|---|
| `template/DocumentTemplateController.java` | 117, 129 |
| `template/GeneratedDocumentController.java` | 44, 57 |

**Fix:** Replace `RequestScopes.MEMBER_ID.get()` → `RequestScopes.requireMemberId()`.

---

#### 5. Missing `@PreAuthorize` on Portal Endpoints (Severity: Low)

Portal endpoints rely on `CustomerAuthFilter` but lack explicit `@PreAuthorize` annotations, unlike the rest of the codebase:

| File | Endpoints |
|---|---|
| `portal/PortalAuthController.java` | `POST /request-link`, `POST /exchange` |
| `portal/PortalProjectController.java` | `GET /`, `GET /{id}` |
| `portal/PortalDocumentController.java` | `GET /projects/{id}/documents`, `GET /documents`, `GET /documents/{id}/presign-download` |

**Note:** These are intentionally public/portal-scoped. Adding `@PreAuthorize("isAuthenticated()")` would improve consistency but is not strictly required.

---

## Code Duplication — Reuse Opportunities

### HIGH Priority

#### 1. "Find or Throw" Pattern — 186+ instances across 50+ files

```java
repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Entity", id));
```

Repeated 4x in `InvoiceService`, 3x in `TaskService`, 2x in `ProjectBudgetService`, etc.

**Suggested consolidation:**
```java
// Add to a shared RepositoryHelper or as a default method on a base repository interface
public static <T> T findOrThrow(Optional<T> result, String entityType, Object id) {
    return result.orElseThrow(() -> new ResourceNotFoundException(entityType, id));
}
```

---

#### 2. Tenant/Org ID Extraction — 40+ instances across 25+ services

```java
String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
```

Repeated in `InvoiceService`, `CommentService`, `TimeEntryService`, `TaskService`, etc.

**Suggested consolidation:**
```java
// Add to RequestScopes class
public static String getTenantIdOrNull() {
    return TENANT_ID.isBound() ? TENANT_ID.get() : null;
}
public static String getOrgIdOrNull() {
    return ORG_ID.isBound() ? ORG_ID.get() : null;
}
```

---

### MEDIUM Priority

#### 3. Member Name Resolution — 10 instances across 6 services

```java
memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
```

Found in `TimeEntryService`, `InvoiceService`, `CommentService`, `TaskService`, etc.

**Suggested consolidation:** Extract to `MemberNameResolver` service or add a method to an existing shared service.

---

#### 4. Member ID + Org Role Extraction in Controllers — 40+ controller methods

```java
UUID memberId = RequestScopes.requireMemberId();
String orgRole = RequestScopes.getOrgRole();
```

Every authenticated endpoint repeats this two-line preamble.

**Suggested consolidation:** Create an `AuthContext` record and a `ControllerHelper.extractAuthContext()` method, or use a Spring `HandlerMethodArgumentResolver` to inject an `AuthContext` parameter.

---

#### 5. Domain Event Publishing Context — 15+ service methods

Services repeatedly assemble: tenantId, orgId, memberId, actorName, timestamp before publishing domain events.

**Suggested consolidation:** Create a `DomainEventFactory` that auto-extracts context from `RequestScopes` and resolves actor names.

---

#### 6. View-Based Filtering Logic — 3 controllers

`ProjectController`, `TaskController`, and `CustomerController` all duplicate the same SavedView resolution → filter execution → access control pattern.

**Suggested consolidation:** Extract to `ViewFilteringService.applyViewFilter(entityType, viewId, memberId, orgRole)`.

---

## Summary

| Category | Pass | Fail | Total Checked |
|---|---|---|---|
| Convention violations (main code) | 12 | 3 | 15 |
| Convention violations (tests) | 5 | 1 | 6 |
| Code duplication patterns | — | 6 | 6 |

**Top 3 actions by impact:**
1. **Extract view-based filtering** from 3 controllers into a shared service (removes ~150 lines of duplicated controller logic + fixes business-logic-in-controller violation)
2. **Add `RequestScopes` helper methods** (`getTenantIdOrNull`, `getOrgIdOrNull`) — 5-minute change, eliminates 40+ repeated ternary expressions
3. **Fix non-idempotent migration V36** — add `IF NOT EXISTS` (1-minute fix, prevents provisioning failures)
