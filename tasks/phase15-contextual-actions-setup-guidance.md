# Phase 15 — Contextual Actions & Setup Guidance

Phase 15 adds a **contextual awareness layer** to entity detail pages — surfacing what is missing, what is ready, and what to do next. The platform has accumulated powerful features across 14 phases (rate cards, budgets, custom fields, compliance checklists, document templates, invoicing), but these features are siloed across tabs and settings pages. Phase 15 eliminates the guesswork by computing setup completeness on-the-fly and rendering actionable guidance cards directly on the entity detail pages where the user is already working.

This is a **pure read/aggregation layer** over existing data. No new database tables, no migrations, no new entities. Four new backend services compute completeness by querying existing repositories. Five new GET endpoints expose these computations. The frontend adds reusable components (`SetupProgressCard`, `ActionCard`, `FieldValueGrid`, `EmptyState`, `TemplateReadinessCard`) that render on project and customer detail pages.

**Architecture doc**: `architecture/phase15-contextual-actions-setup-guidance.md`

**ADRs**:
- [ADR-065](../adr/ADR-065-hardcoded-setup-checks.md) — Hardcoded Setup Checks vs. Configurable Setup Engine
- [ADR-066](../adr/ADR-066-computed-status-over-persisted.md) — Computed Status vs. Persisted Status
- [ADR-067](../adr/ADR-067-entity-detail-page-action-surface.md) — Entity Detail Pages as Action Surface

**MIGRATION**: None. Phase 15 has no new database tables, no migrations, and no entity changes.

**Dependencies on prior phases**: Phase 5 (TimeEntry), Phase 8 (BillingRate, ProjectBudget, OrgSettings), Phase 10 (Invoice/unbilled time), Phase 11 (FieldDefinition, FieldGroup), Phase 12 (DocumentTemplate, TemplateContextBuilder), Phase 14 (Customer.lifecycleStatus, V29 checklist tables).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 109 | Project Setup Status & Unbilled Time — Backend | Backend | -- | M | 109A, 109B | **Done** |
| 110 | Customer Readiness & Document Generation Readiness — Backend | Backend | -- | M | 110A, 110B | **Done** |
| 111 | Reusable Frontend Components & API Client | Frontend | 109, 110 | M | 111A | **Done** (PR #229) |
| 112 | Project Detail Page Integration | Frontend | 111 | S | 112A | |
| 113 | Customer Detail Page Integration | Frontend | 111 | S | 113A | |
| 114 | Empty State Rollout | Frontend | 111 | S | 114A | |

---

## Dependency Graph

```
[E109A ProjectSetupStatusService + DTOs + Endpoint]
[E109B UnbilledTimeSummaryService + DTOs + Endpoints]
      (Backend, independent — both can start immediately)
                          |
[E110A CustomerReadinessService + DTOs + Endpoint]
[E110B DocumentGenerationReadinessService + DTOs + Endpoint]
      (Backend, independent — both can start immediately)
                          |
                          v
             [E111A Reusable Frontend Components + API Client]
              (Frontend — depends on 109 + 110 for API schema)
                          |
              +-----------+--------------+
              |           |              |
              v           v              v
        [E112A         [E113A        [E114A
    Project Detail  Customer Detail  Empty State
    Integration]    Integration]    Rollout]
      (Frontend)     (Frontend)     (Frontend)
     (parallel)     (parallel)     (parallel)
```

**Parallel opportunities**:
- Epics 109 and 110 are fully independent — both backend tracks start immediately and can be built in parallel.
- Within each epic: slices A and B are sequential but independent of the other epic.
- After Epic 111 completes: Epics 112, 113, and 114 are fully independent and can run in parallel.

---

## Implementation Order

### Stage 1: Backend Services (Parallel backend tracks)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 109 | 109A | `ProjectSetupStatus` + `RequiredFieldStatus` + `FieldStatus` DTOs, `ProjectSetupStatusService`, `BillingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull()`, endpoint on `ProjectController`. ~26 tests. Foundation for project detail page. | **Done** (PR #225) |
| 1b | Epic 109 | 109B | `UnbilledTimeSummary` + `ProjectUnbilledBreakdown` DTOs, `UnbilledTimeSummaryService` (two native SQL queries), project + customer unbilled endpoints on `ProjectController` and `CustomerController`. ~26 tests. |
| 1c | Epic 110 | 110A | `CustomerReadiness` + `ChecklistProgress` DTOs, `CustomerReadinessService` (native SQL for checklist tables), endpoint on `CustomerController`. ~26 tests. Parallel with 109. | **Done** (PR #227) |
| 1d | Epic 110 | 110B | `TemplateReadiness` DTO, `DocumentGenerationReadinessService` (context builder inspection, hardcoded key checks), endpoint on `DocumentTemplateController`. ~26 tests. Parallel with 109. | **Done** (PR #228) |

### Stage 2: Frontend Components (After Stage 1)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 111 | 111A | `components/setup/` directory with all 5 reusable components, `lib/api/setup-status.ts` API client, and component tests. All components are self-contained and testable with mocked data. ~19 component tests. | **Done** (PR #229) |

### Stage 3: Page Integration + Empty States (Parallel after Stage 2)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 112 | 112A | Wire `SetupProgressCard`, `ActionCard`, `TemplateReadinessCard`, `FieldValueGrid` into project detail page. Parallel `Promise.all()` fetches. ~5 tests. |
| 3b | Epic 113 | 113A | Wire customer readiness, unbilled summary, template readiness into customer detail page. Lifecycle action prompt. ~5 tests. |
| 3c | Epic 114 | 114A | Upgrade existing `components/empty-state.tsx` with `actionHref`/`onAction` support and card-based styling. Replace 8 "No items" placeholders across the app. ~8 tests. |

### Timeline

```
Stage 1a:  [109A] --> [109B]   (project backend track)
Stage 1b:  [110A] --> [110B]   (customer/template backend track, parallel with 109)
Stage 2:   [111A]              (frontend components — after both backend tracks)
Stage 3:   [112A]  //  [113A]  //  [114A]   (page integrations — all parallel after 111A)
```

**Critical path**: 109A -> 109B -> 111A -> 112A
**Parallelizable**: 109 and 110 tracks are fully parallel. 112A, 113A, 114A are fully parallel after 111A.

---

## Epic 109: Project Setup Status & Unbilled Time — Backend

**Goal**: Create the `ProjectSetupStatusService` and `UnbilledTimeSummaryService` with their DTOs and corresponding API endpoints. This epic handles the two project-scoped aggregation services and adds the `existsByProjectIdIsNullAndCustomerIdIsNull()` method to `BillingRateRepository`. Both services are read-only: they query existing repositories and return computed DTOs — no new tables, no mutations.

**References**: Architecture doc Sections 15.2.1 (DTOs), 15.3.1 (ProjectSetupStatusService), 15.3.3 (UnbilledTimeSummaryService), 15.4 (API surface), 15.9.1 (file list), 15.9.3 (testing). [ADR-065](../adr/ADR-065-hardcoded-setup-checks.md), [ADR-066](../adr/ADR-066-computed-status-over-persisted.md).

**Dependencies**: None (aggregation only — reads existing repos from Phases 4, 5, 8, 11)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **109A** | 109.1–109.10 | `ProjectSetupStatus`, `RequiredFieldStatus`, `FieldStatus` Java records. `ProjectSetupStatusService` with 5-check aggregation logic and `computeRequiredFields()` helper. `BillingRateRepository` extension method. `GET /api/projects/{id}/setup-status` endpoint on `ProjectController`. Unit tests for all service combinations and integration tests for the endpoint. ~26 tests total. | **Done** (PR #225) |
| **109B** | 109.11–109.20 | `UnbilledTimeSummary`, `ProjectUnbilledBreakdown` Java records. `UnbilledTimeSummaryService` with two native SQL queries (project-scoped and customer-scoped). `GET /api/projects/{id}/unbilled-summary` and `GET /api/customers/{id}/unbilled-summary` endpoints. Unit tests for SQL aggregation logic and integration tests for both endpoints. ~26 tests total. | **Done** (PR #226) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 109.1 | Create `setupstatus` package with `ProjectSetupStatus` record | 109A | | `backend/src/main/java/.../setupstatus/ProjectSetupStatus.java`. Java record with 8 fields: `projectId` (UUID), `customerAssigned` (boolean), `rateCardConfigured` (boolean), `budgetConfigured` (boolean), `teamAssigned` (boolean), `requiredFields` (RequiredFieldStatus), `completionPercentage` (int), `overallComplete` (boolean). No Jackson annotations needed — records serialize automatically. Pattern: follow `budget/ProjectBudgetStatus.java` for read-only DTO record style. |
| 109.2 | Create `RequiredFieldStatus` and `FieldStatus` records | 109A | | `backend/src/main/java/.../setupstatus/RequiredFieldStatus.java` — record with `filled` (int), `total` (int), `fields` (List<FieldStatus>). `backend/src/main/java/.../setupstatus/FieldStatus.java` — record with `name` (String), `slug` (String), `filled` (boolean). Both in `setupstatus/` package. Architecture doc Section 15.2.1 has exact field definitions. |
| 109.3 | Add `existsByProjectIdIsNullAndCustomerIdIsNull()` to `BillingRateRepository` | 109A | | Modify `backend/src/main/java/.../billingrate/BillingRateRepository.java`. Add one Spring Data derived query method: `boolean existsByProjectIdIsNullAndCustomerIdIsNull()`. This checks for org-level default rates without loading all records. Architecture doc Section 15.3.1 explains the two-step rate card check rationale. Pattern: Spring Data derived query — no @Query annotation needed. |
| 109.4 | Create `ProjectSetupStatusService` | 109A | | `backend/src/main/java/.../setupstatus/ProjectSetupStatusService.java`. @Service, @Transactional(readOnly = true), constructor injection. Depends on 6 repositories: `ProjectRepository`, `CustomerProjectRepository`, `BillingRateRepository`, `ProjectBudgetRepository`, `ProjectMemberRepository`, `FieldDefinitionRepository`. Method: `getSetupStatus(UUID projectId)`. Rate card check: `findByFilters(null, projectId, null).isEmpty() == false OR existsByProjectIdIsNullAndCustomerIdIsNull()`. Team check: `findByProjectId(projectId).size() >= 2`. `computeRequiredFields()` private method: filters `FieldDefinition` records by `isRequired()`, builds `FieldStatus` list from entity's `customFields` map. Full implementation in architecture doc Section 15.3.1. Pattern: follow `report/ProfitabilityService.java` for read-only multi-repo aggregation service style. |
| 109.5 | Add `getSetupStatus` endpoint to `ProjectController` | 109A | | Modify `backend/src/main/java/.../project/ProjectController.java`. Add: `@GetMapping("/{id}/setup-status")`, `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")`. Method body: one-line delegation — `return ResponseEntity.ok(projectSetupStatusService.getSetupStatus(id))`. Inject `ProjectSetupStatusService` via constructor. No request body, no query params. Architecture doc Section 15.4.3 has the controller code snippet. |
| 109.6 | Write `ProjectSetupStatusService` unit tests | 109A | | `backend/src/test/java/.../setupstatus/ProjectSetupStatusServiceTest.java`. ~10 unit tests with mocked repos. Cases: (1) all checks pass -> 100% complete, (2) no customer assigned -> 80% complete, (3) no project-level rates but org-level rates exist -> rateCardConfigured=true, (4) no project-level rates AND no org-level rates -> rateCardConfigured=false, (5) only 1 team member -> teamAssigned=false, (6) 2+ team members -> teamAssigned=true, (7) budget missing -> 80% complete, (8) 0 required fields defined -> requiredFields.total=0 counts as passed, (9) 1/3 required fields filled -> not complete, (10) project not found -> ResourceNotFoundException. Pattern: `@ExtendWith(MockitoExtension.class)`, mock all 6 repos. |
| 109.7 | Write `ProjectSetupStatus` integration tests | 109A | | `backend/src/test/java/.../setupstatus/ProjectSetupStatusControllerTest.java`. ~5 integration tests with @SpringBootTest + Testcontainers. Cases: (1) freshly created project returns customerAssigned=false, budgetConfigured=false, (2) project with linked customer returns customerAssigned=true, (3) project with budget returns budgetConfigured=true, (4) 401 for unauthenticated request, (5) 404 for non-existent project ID. Pattern: follow `budget/ProjectBudgetControllerTest.java` — use MockMvc, set up tenant via ScopedValue, use JWT mock. |
| 109.8 | Create integration test helper for setup status scenarios | 109A | | In test class or a shared base: helper method `createProjectWithSetup(boolean withCustomer, boolean withBudget, boolean withExtraMembers)` that creates the test data using existing repos. Avoids repeating setup boilerplate across tests. This is internal to the test file. Pattern: static factory methods in existing controller tests. |
| 109.9 | Verify no impact on existing ProjectController tests | 109A | | Run `./mvnw test -q -Dtest=ProjectControllerTest -pl backend` and confirm all existing tests still pass after the controller modification. The new endpoint is additive — no changes to existing methods. Document result in task notes. |
| 109.10 | Wire `ProjectSetupStatusService` into Spring context | 109A | | Confirm the new `setupstatus` package is discovered by Spring component scan. The base package `io.b2mash.b2b.b2bstrawman` is already the scan root, so no explicit `@ComponentScan` change is needed. Verify by checking that `BackendApplication.java` is in the root package and has no restricting `scanBasePackages`. |
| 109.11 | Create `UnbilledTimeSummary` and `ProjectUnbilledBreakdown` records | 109B | | `backend/src/main/java/.../setupstatus/UnbilledTimeSummary.java` — record with `totalHours` (BigDecimal), `totalAmount` (BigDecimal), `currency` (String), `entryCount` (int), `byProject` (List<ProjectUnbilledBreakdown>, nullable). `backend/src/main/java/.../setupstatus/ProjectUnbilledBreakdown.java` — record with `projectId` (UUID), `projectName` (String), `hours` (BigDecimal), `amount` (BigDecimal), `entryCount` (int). Both in `setupstatus/` package. Architecture doc Section 15.2.1 has exact field definitions. |
| 109.12 | Create `UnbilledTimeSummaryService` | 109B | | `backend/src/main/java/.../setupstatus/UnbilledTimeSummaryService.java`. @Service, @Transactional(readOnly = true). Depends on `EntityManager` (for native SQL) and `OrgSettingsRepository` (for currency). Methods: `getProjectUnbilledSummary(UUID projectId)` and `getCustomerUnbilledSummary(UUID customerId)`. Project query: SUM of `(duration_minutes / 60.0) * billing_rate_snapshot` for billable entries with null invoice_id on the given project. Customer query: same but grouped by project with JOIN to customer_projects. Currency: `orgSettingsRepository.findByTenantSchema().map(OrgSettings::getDefaultCurrency).orElse("USD")`. Architecture doc Section 15.3.3 has both SQL queries verbatim. Pattern: follow `report/ProfitabilityService.java` for `EntityManager.createNativeQuery()` usage. |
| 109.13 | Map native query results to `UnbilledTimeSummary` | 109B | | Within `UnbilledTimeSummaryService`: project query returns a single `Object[]` row — cast index 0 to BigDecimal (total_minutes), index 1 to BigDecimal (total_amount), index 2 to Long (entry_count). Convert `total_minutes / 60` to hours. Customer query returns `List<Object[]>` — map each row to `ProjectUnbilledBreakdown`. Total hours/amount/entry_count is the sum across all breakdown rows. Return empty summary (all zeros, null byProject) when no unbilled entries found. Pattern: follow existing native query result mapping in `report/ProfitabilityService.java`. |
| 109.14 | Add unbilled summary endpoints to `ProjectController` and `CustomerController` | 109B | | Modify `backend/src/main/java/.../project/ProjectController.java`: add `GET /{id}/unbilled-summary`. Modify `backend/src/main/java/.../customer/CustomerController.java`: add `GET /{id}/unbilled-summary`. Both: `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")`. Both: one-line delegation to `unbilledTimeSummaryService`. Inject service via constructor in both controllers. Architecture doc Section 15.4.1 has endpoint table. |
| 109.15 | Write `UnbilledTimeSummaryService` unit tests | 109B | | `backend/src/test/java/.../setupstatus/UnbilledTimeSummaryServiceTest.java`. ~6 unit tests with mocked EntityManager. Cases: (1) project with 3 billable entries -> correct totalHours/totalAmount, (2) project with no billable entries -> zero summary, (3) project with entries that have null billing_rate_snapshot -> excluded from amount, (4) project with invoiced entries (invoice_id not null) -> excluded, (5) customer summary groups by project correctly with correct per-project breakdown, (6) currency resolved from OrgSettings. Mock EntityManager returning test `Object[]` arrays. |
| 109.16 | Write project unbilled summary integration test | 109B | | `backend/src/test/java/.../setupstatus/UnbilledTimeSummaryControllerTest.java`. ~8 integration tests. Cases: (1) project with 2 billable time entries -> summary shows correct totals, (2) project with all entries invoiced -> zeros, (3) customer with 2 projects' entries -> byProject populated, (4) customer with no unbilled entries -> zeros and null byProject, (5) unauthenticated -> 401, (6) non-existent project -> 404, (7) non-existent customer -> 404, (8) project with mix of billable and non-billable -> only billable counted. Pattern: create TimeEntry records with billable=true, billing_rate_snapshot set; call endpoint; assert response. Follow existing TimeEntry test setup patterns from `timeentry/TimeEntryServiceTest.java`. |
| 109.17 | Verify `OrgSettings` currency resolution | 109B | | Confirm `OrgSettingsRepository` has a method to find the current tenant's settings (it should already exist from Phase 8). If it uses `findAll()` and takes the first result (single row per tenant schema), document this. If there's a dedicated `findSingleton()` method, use that. Read `orgsettings/OrgSettingsRepository.java` to confirm the query method name before using it. |
| 109.18 | Verify customer unbilled summary calls correct customer_projects join | 109B | | Customer unbilled summary must JOIN `customer_projects cp ON cp.project_id = p.id WHERE cp.customer_id = :customerId`. This requires the `customer_projects` table to exist with the expected schema (created in Phase 4). Verify the SQL works by running the integration test against Testcontainers. |
| 109.19 | Check `BillingRateRepository.findByFilters` signature | 109A | | Before using `findByFilters(null, projectId, null)` in `ProjectSetupStatusService`, read `billingrate/BillingRateRepository.java` to confirm the method signature and that passing `null` for the first param returns project-scoped rates only. If the method does not support nullable params, use an alternative derived query `findByProjectId(projectId)` instead. |
| 109.20 | Run full backend test suite after 109B | 109B | | `./mvnw clean verify -q -pl backend`. Confirm no regressions in existing tests. Extract any failures from `target/surefire-reports/` and `target/failsafe-reports/`. All new tests must pass. Note: V29 migration must be present for Testcontainers-based integration tests (Phase 14 must be merged). |

### Key Files

**Slice 109A — Create:**
- `backend/src/main/java/.../setupstatus/ProjectSetupStatus.java`
- `backend/src/main/java/.../setupstatus/RequiredFieldStatus.java`
- `backend/src/main/java/.../setupstatus/FieldStatus.java`
- `backend/src/main/java/.../setupstatus/ProjectSetupStatusService.java`
- `backend/src/test/java/.../setupstatus/ProjectSetupStatusServiceTest.java`
- `backend/src/test/java/.../setupstatus/ProjectSetupStatusControllerTest.java`

**Slice 109A — Modify:**
- `backend/src/main/java/.../billingrate/BillingRateRepository.java` — Add `existsByProjectIdIsNullAndCustomerIdIsNull()`
- `backend/src/main/java/.../project/ProjectController.java` — Add `GET /{id}/setup-status`

**Slice 109B — Create:**
- `backend/src/main/java/.../setupstatus/UnbilledTimeSummary.java`
- `backend/src/main/java/.../setupstatus/ProjectUnbilledBreakdown.java`
- `backend/src/main/java/.../setupstatus/UnbilledTimeSummaryService.java`
- `backend/src/test/java/.../setupstatus/UnbilledTimeSummaryServiceTest.java`
- `backend/src/test/java/.../setupstatus/UnbilledTimeSummaryControllerTest.java`

**Slice 109B — Modify:**
- `backend/src/main/java/.../project/ProjectController.java` — Add `GET /{id}/unbilled-summary`
- `backend/src/main/java/.../customer/CustomerController.java` — Add `GET /{id}/unbilled-summary`

**Read for context:**
- `architecture/phase15-contextual-actions-setup-guidance.md` Sections 15.2.1, 15.3.1, 15.3.3, 15.4.1, 15.4.2, 15.9.1, 15.9.3
- `backend/src/main/java/.../billingrate/BillingRateRepository.java` — Confirm `findByFilters` signature and add `existsByProjectIdIsNullAndCustomerIdIsNull()`
- `backend/src/main/java/.../report/ProfitabilityService.java` — Pattern for multi-repo read-only aggregation
- `backend/src/main/java/.../budget/ProjectBudgetStatus.java` — DTO record pattern
- `backend/src/main/java/.../orgsettings/OrgSettingsRepository.java` — Confirm singleton query method name
- `backend/src/main/java/.../timeentry/TimeEntryServiceTest.java` — Integration test setup for time entries

### Architecture Decisions

- **ADR-065**: Setup checks are hardcoded in service methods, not configurable — `ProjectSetupStatusService` is ~60 lines of explicit repository calls.
- **ADR-066**: Status computed on-the-fly per request — no event handlers, no new tables, always consistent.
- **Two-step rate check**: Project is "configured" if project-level rate overrides exist OR org-level defaults exist. Implemented via `findByFilters(null, projectId, null)` (project overrides) OR `existsByProjectIdIsNullAndCustomerIdIsNull()` (org defaults).
- **Native SQL for unbilled summary**: Aggregation (SUM, GROUP BY) is more efficient as native SQL than loading all `TimeEntry` records into memory and summing in Java. The queries are stable, tested in integration tests, and not subject to Hibernate mapping concerns.
- **Separate from InvoiceService.getUnbilledTime()**: That method returns per-entry detail (far more data) and lacks a project-scoped variant. The new service returns only aggregates.

---

## Epic 110: Customer Readiness & Document Generation Readiness — Backend

**Goal**: Create the `CustomerReadinessService` and `DocumentGenerationReadinessService` with their DTOs and corresponding API endpoints. `CustomerReadinessService` queries the V29 checklist tables via native SQL (since Phase 14 Java entities are stubs). `DocumentGenerationReadinessService` uses existing `TemplateContextBuilder` beans to check what would be missing from a document generation context. Both services are read-only.

**References**: Architecture doc Sections 15.2.1 (DTOs), 15.3.2 (CustomerReadinessService), 15.3.4 (DocumentGenerationReadinessService), 15.4 (API surface), 15.9.1 (file list), 15.9.3 (testing). [ADR-065](../adr/ADR-065-hardcoded-setup-checks.md), [ADR-066](../adr/ADR-066-computed-status-over-persisted.md).

**Dependencies**: None (reads existing repos from Phases 4, 11, 12, 14)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **110A** | 110.1–110.10 | `CustomerReadiness`, `ChecklistProgress` Java records. `CustomerReadinessService` with lifecycle status read, native SQL checklist progress query, required field check, linked project check, and `overallReadiness` computation. `GET /api/customers/{id}/readiness` endpoint on `CustomerController`. ~26 tests total. | **Done** (PR #227) |
| **110B** | 110.11–110.20 | `TemplateReadiness` Java record. `DocumentGenerationReadinessService` with `TemplateContextBuilder` inspection and hardcoded required-key checks per entity type. `GET /api/templates/readiness` endpoint on `DocumentTemplateController`. ~26 tests total. | **Done** (PR #228) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 110.1 | Create `CustomerReadiness` and `ChecklistProgress` records | 110A | | `backend/src/main/java/.../setupstatus/CustomerReadiness.java` — record with `customerId` (UUID), `lifecycleStatus` (String), `checklistProgress` (ChecklistProgress, nullable), `requiredFields` (RequiredFieldStatus), `hasLinkedProjects` (boolean), `overallReadiness` (String). `backend/src/main/java/.../setupstatus/ChecklistProgress.java` — record with `checklistName` (String), `completed` (int), `total` (int), `percentComplete` (int). Architecture doc Section 15.2.1 has exact fields. `RequiredFieldStatus` and `FieldStatus` are shared with Epic 109 — do not duplicate. |
| 110.2 | Create `CustomerReadinessService` skeleton | 110A | | `backend/src/main/java/.../setupstatus/CustomerReadinessService.java`. @Service, @Transactional(readOnly = true). Dependencies: `CustomerRepository`, `CustomerProjectRepository`, `FieldDefinitionRepository`, `EntityManager`. Method: `getReadiness(UUID customerId)`. Validate customer exists via `customerRepository.findById()`. Reads `customer.getLifecycleStatus()` directly. Pattern: constructor injection, no @Autowired. |
| 110.3 | Implement checklist progress via native SQL | 110A | | Within `CustomerReadinessService`: private method `queryChecklistProgress(UUID customerId)` using `EntityManager.createNativeQuery()`. SQL from architecture doc Section 15.3.2 verbatim: joins `checklist_instances` + `checklist_templates` + `checklist_instance_items`, filters `ci.status = 'IN_PROGRESS'`, LIMIT 1. Returns `ChecklistProgress` record or `null` if no result. Handle the case where V29 tables exist but no checklist instance is present (query returns empty result -> return null). Do NOT throw if no instance found. Pattern: `entityManager.createNativeQuery(sql).setParameter("customerId", customerId).getResultList()`. |
| 110.4 | Implement `overallReadiness` computation | 110A | | Within `CustomerReadinessService`: private method `computeOverallReadiness(...)`. Logic from architecture doc Section 15.3.2: `"Complete"` = lifecycle ACTIVE + all required fields filled + (no checklist OR checklist complete) + has linked projects. `"In Progress"` = lifecycle ONBOARDING, or some but not all required fields filled, or checklist in progress. `"Needs Attention"` = lifecycle PROSPECT, or no linked projects, or 0 required fields filled (when required fields exist). |
| 110.5 | Reuse `computeRequiredFields()` from `ProjectSetupStatusService` | 110A | | The `computeRequiredFields()` logic is identical for customer and project — same `FieldDefinitionRepository` call, same `customFields` map check, different `EntityType` enum value. Extract this as a static helper method in a new `SetupStatusHelper.java` class in the `setupstatus` package, or replicate it in `CustomerReadinessService` (simpler, avoids premature abstraction). Both approaches are acceptable. Document chosen approach. |
| 110.6 | Add `getReadiness` endpoint to `CustomerController` | 110A | | Modify `backend/src/main/java/.../customer/CustomerController.java`. Add `@GetMapping("/{id}/readiness")`, `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")`. One-line delegation to `customerReadinessService.getReadiness(id)`. Inject service via constructor. Architecture doc Section 15.4.1 endpoint table. |
| 110.7 | Write `CustomerReadinessService` unit tests | 110A | | `backend/src/test/java/.../setupstatus/CustomerReadinessServiceTest.java`. ~8 unit tests. Cases: (1) PROSPECT + no projects -> Needs Attention, (2) ONBOARDING + checklist in progress -> In Progress, (3) ACTIVE + all fields filled + checklist complete + has projects -> Complete, (4) ACTIVE + checklist is null (no instance) -> Complete if other checks pass, (5) lifecycle DORMANT -> In Progress, (6) customer not found -> ResourceNotFoundException, (7) required fields 0/3 filled -> Needs Attention, (8) checklist 5/5 complete -> checklistProgress.percentComplete = 100. Mock EntityManager for native SQL by returning test `Object[]` data. |
| 110.8 | Write `CustomerReadiness` integration tests | 110A | | `backend/src/test/java/.../setupstatus/CustomerReadinessControllerTest.java`. ~5 integration tests. Cases: (1) PROSPECT customer with no projects -> overallReadiness=Needs Attention, (2) ACTIVE customer with linked project -> hasLinkedProjects=true, (3) customer with ONBOARDING lifecycle -> lifecycleStatus=ONBOARDING in response, (4) 401 unauthenticated, (5) 404 non-existent customer. Note: integration tests cannot easily seed V29 checklist data without Phase 14 entities — set `checklistProgress=null` path is acceptable for initial integration coverage. |
| 110.9 | Handle null `lifecycleStatus` on older Customer records | 110A | | Phase 14's V29 migration backfills existing customers with `lifecycleStatus = 'ACTIVE'`. But if the V29 migration is not yet applied (e.g., CI runs before Phase 14 merges), `customer.getLifecycleStatus()` may return null. Guard: if null, treat as `"ACTIVE"` for the readiness computation. Add a null-safe read in `CustomerReadinessService`. |
| 110.10 | Verify V29 tables are accessible via native SQL in Testcontainers | 110A | | The integration test will use Testcontainers + Flyway to apply all migrations including V29. Confirm that `checklist_instances` and related tables are created and queryable. If V29 does not yet exist in the migration folder (Phase 14 not merged), the integration tests must skip the checklist assertion. Add a `@Assumptions` guard or note in the test. |
| 110.11 | Create `TemplateReadiness` record | 110B | | `backend/src/main/java/.../setupstatus/TemplateReadiness.java` — record with `templateId` (UUID), `templateName` (String), `templateSlug` (String), `ready` (boolean), `missingFields` (List<String>). Architecture doc Section 15.2.1. |
| 110.12 | Create `DocumentGenerationReadinessService` | 110B | | `backend/src/main/java/.../setupstatus/DocumentGenerationReadinessService.java`. @Service, @Transactional(readOnly = true). Dependencies: `DocumentTemplateRepository`, `List<TemplateContextBuilder>` (Spring injects all beans implementing this interface). Method: `checkReadiness(String entityTypeStr, UUID entityId)`. Steps: (1) find templates by `DocumentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(entityType)`. (2) find the matching `TemplateContextBuilder` using `builder.supports(entityType)`. (3) call `builder.buildContext(entityId, null)` to get the context map. (4) for each template, inspect the context map for required keys. Return `List<TemplateReadiness>`. Architecture doc Section 15.3.4. |
| 110.13 | Implement hardcoded required-key checks per entity type | 110B | | Within `DocumentGenerationReadinessService`: private method `getMissingFields(Map<String, Object> context, String entityType)`. Required keys per entity type from architecture doc Section 15.3.4: PROJECT requires `project.name` non-null, `customer` non-null, `org.name` non-null. CUSTOMER requires `customer.name`, `customer.email`, `org.name`. INVOICE requires `invoice.number`, `customer.name`, `lines` non-empty list. Returns `List<String>` of human-readable field names (e.g., "Customer Name", "Customer Address"). ADR-065 explains why hardcoded checks are preferred over Thymeleaf dry-run. |
| 110.14 | Guard `buildContext(entityId, null)` null memberId | 110B | | Before calling `builder.buildContext(entityId, null)`, verify that existing `TemplateContextBuilder` implementations handle null `memberId` without NPE. Read `template/ProjectContextBuilder.java` to check if `contextHelper.buildGeneratedByMap(null)` is null-safe. If not, add a null guard in `DocumentGenerationReadinessService`: wrap in try-catch or pass a sentinel value. Architecture doc Section 15.3.4 highlights this risk explicitly. |
| 110.15 | Add `getReadiness` endpoint to `DocumentTemplateController` | 110B | | Modify `backend/src/main/java/.../template/DocumentTemplateController.java`. Add `@GetMapping("/readiness")`, `@RequestParam String entityType`, `@RequestParam UUID entityId`, `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")`. Returns `List<TemplateReadiness>`. Delegates to `documentGenerationReadinessService.checkReadiness(entityType, entityId)`. Inject service via constructor. Architecture doc Section 15.4.1. |
| 110.16 | Write `DocumentGenerationReadinessService` unit tests | 110B | | `backend/src/test/java/.../setupstatus/DocumentGenerationReadinessServiceTest.java`. ~6 unit tests with mocked `DocumentTemplateRepository` and `TemplateContextBuilder`. Cases: (1) template with complete context -> ready=true, missingFields empty, (2) project template missing customer context key -> ready=false, missingFields=["Customer"], (3) no templates for entity type -> empty list returned, (4) multiple templates -> returns list with mixed ready/not-ready, (5) null memberId passed to builder -> no NPE (null safety guard tested), (6) unknown entity type -> empty list or appropriate error. |
| 110.17 | Write `DocumentTemplateController` readiness integration tests | 110B | | `backend/src/test/java/.../setupstatus/DocumentGenerationReadinessControllerTest.java`. ~5 integration tests. Cases: (1) `GET /api/templates/readiness?entityType=PROJECT&entityId={id}` returns list of templates, (2) project with linked customer -> template shows ready=true, (3) project without linked customer -> template shows ready=false with missingFields, (4) 401 unauthenticated, (5) invalid entityType -> 400 bad request. Pattern: seed a `DocumentTemplate` using `DocumentTemplateRepository.save()`, then call endpoint. |
| 110.18 | Verify `DocumentTemplateRepository` has `findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder` | 110B | | Read `template/DocumentTemplateRepository.java` to confirm the exact method name for querying templates by entity type. The architecture doc assumes this method exists from Phase 12. If the actual method name differs, use the correct name. |
| 110.19 | Handle entity type string-to-enum conversion | 110B | | The readiness endpoint receives `entityType` as a String query param. Convert to the appropriate enum value (`TemplateEntityType` or equivalent from Phase 12). Handle unknown values with a `400 Bad Request` via `@ExceptionHandler` or Spring's built-in enum conversion. Read `template/DocumentTemplate.java` to identify the actual enum type used. |
| 110.20 | Run full backend test suite after 110B | 110B | | `./mvnw clean verify -q -pl backend`. Confirm all new tests pass and no regressions. Total new backend tests across Epics 109+110: ~104 (26 per slice x 4 slices). Extract failures from surefire/failsafe reports. |

### Key Files

**Slice 110A — Create:**
- `backend/src/main/java/.../setupstatus/CustomerReadiness.java`
- `backend/src/main/java/.../setupstatus/ChecklistProgress.java`
- `backend/src/main/java/.../setupstatus/CustomerReadinessService.java`
- `backend/src/test/java/.../setupstatus/CustomerReadinessServiceTest.java`
- `backend/src/test/java/.../setupstatus/CustomerReadinessControllerTest.java`

**Slice 110A — Modify:**
- `backend/src/main/java/.../customer/CustomerController.java` — Add `GET /{id}/readiness`

**Slice 110B — Create:**
- `backend/src/main/java/.../setupstatus/TemplateReadiness.java`
- `backend/src/main/java/.../setupstatus/DocumentGenerationReadinessService.java`
- `backend/src/test/java/.../setupstatus/DocumentGenerationReadinessServiceTest.java`
- `backend/src/test/java/.../setupstatus/DocumentGenerationReadinessControllerTest.java`

**Slice 110B — Modify:**
- `backend/src/main/java/.../template/DocumentTemplateController.java` — Add `GET /readiness`

**Read for context:**
- `architecture/phase15-contextual-actions-setup-guidance.md` Sections 15.2.1, 15.3.2, 15.3.4, 15.4.1, 15.4.2
- `backend/src/main/java/.../customer/Customer.java` — Confirm `lifecycleStatus` field accessor name
- `backend/src/main/java/.../template/DocumentTemplateRepository.java` — Confirm query method for entity type
- `backend/src/main/java/.../template/ProjectContextBuilder.java` — Check null-safety of `buildContext(entityId, null)`
- `backend/src/main/java/.../template/DocumentTemplate.java` — Identify `TemplateEntityType` enum
- `backend/src/main/java/.../customer/CustomerController.java` — Existing controller structure
- `backend/src/main/java/.../setupstatus/ProjectSetupStatusService.java` — `computeRequiredFields()` pattern to reuse or replicate

### Architecture Decisions

- **ADR-065**: Template readiness uses hardcoded key checks (not Thymeleaf dry-run) — fast, testable, maintainable. Required keys per entity type defined in `DocumentGenerationReadinessService`.
- **Native SQL for checklist progress**: Phase 14 Java `@Entity` classes are stubs. The V29 tables exist; native SQL queries them directly without needing the Java entity layer. When Phase 14 is fully implemented, this can optionally be migrated to a JPA query — the native SQL is more resilient to the stub dependency.
- **Null `memberId` guard**: `buildContext(entityId, null)` must not NPE. A null guard is added in `DocumentGenerationReadinessService` because the readiness check does not need "generated by" metadata.
- **`overallReadiness` is a 3-value string enum**: `"Complete"`, `"In Progress"`, `"Needs Attention"` — not a Java enum, to avoid the frontend needing enum deserialization. Matches the API response schema in architecture doc Section 15.4.2.

---

## Epic 111: Reusable Frontend Components & API Client

**Goal**: Create the five reusable setup guidance UI components (`SetupProgressCard`, `ActionCard`, `FieldValueGrid`, `EmptyState` upgrade, `TemplateReadinessCard`) and the `lib/api/setup-status.ts` API client with fetch functions for all 5 new endpoints. All components are self-contained and testable with mocked data — they do not call the backend directly; that is done by the server components in Epics 112 and 113.

**References**: Architecture doc Sections 15.6.1 (component specs), 15.7.2 (EmptyState), 15.9.2 (file list), 15.9.3 (frontend testing).

**Dependencies**: Epics 109 + 110 (for API response types — components can be coded with mocked data, but the TypeScript interfaces must match the actual API response schemas)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **111A** | 111.1–111.15 | Create `components/setup/` directory with 5 components. Upgrade existing `components/empty-state.tsx` with `actionHref`/`onAction` props and card-based styling. Create `lib/api/setup-status.ts`. Write component tests for all 5 new components. ~19 component tests. | **Done** (PR #229) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 111.1 | Create `components/setup/` directory and TypeScript interfaces | 111A | | Create `components/setup/types.ts` (or define inline in each component). TypeScript interfaces from architecture doc Section 15.6.1: `SetupStep`, `SetupProgressCardProps`, `ActionCardProps`, `FieldValue`, `FieldGroupInfo`, `FieldValueGridProps`, `TemplateReadinessItem`, `TemplateReadinessCardProps`. Use `interface` for props (per frontend CLAUDE.md), `type` for unions. These interfaces mirror the backend DTO shapes. |
| 111.2 | Create `SetupProgressCard` component | 111A | | `components/setup/setup-progress-card.tsx`. Client component (`"use client"` — uses state for collapse/expand). Props: `SetupProgressCardProps` from architecture doc Section 15.6.1. Renders: (1) When `overallComplete = true`: compact badge with expand button. (2) When `overallComplete = false`: full card with Shadcn `Progress` bar, step list with Lucide `CheckCircle2` (complete) and `AlertCircle` (incomplete) icons, action links. Action links only render if `actionHref` provided and `permissionRequired` is not set to true (or caller passes a `canManage` flag). Collapse state managed with `useState`. Pattern: follow `components/budget/` for Shadcn Card usage. |
| 111.3 | Create `ActionCard` component | 111A | | `components/setup/action-card.tsx`. Server component (no state needed). Props: `ActionCardProps` from architecture doc Section 15.6.1. Renders: Shadcn `Card` with icon, title, description, optional primary and secondary action `Button` components. When `primaryAction` has `href`, use Next.js `<Link>` wrapped in `<Button asChild>`. `variant="accent"` renders with subtle teal background (`bg-teal-50 dark:bg-teal-950`). Pattern: follow `components/dashboard/` metric card patterns. |
| 111.4 | Create `FieldValueGrid` component | 111A | | `components/setup/field-value-grid.tsx`. Server component. Props: `FieldValueGridProps` from architecture doc Section 15.6.1. Renders: responsive 2-column grid of field name + value pairs. Grouped fields render under group heading if `groups` provided. Required fields with null/empty value render with amber left border (`border-l-2 border-amber-400`) and "Not set" text in muted style. Optional "Edit Fields" link at top-right if `editHref` provided. Pattern: follow `components/field-definitions/` patterns from Phase 11 frontend. |
| 111.5 | Upgrade existing `EmptyState` component | 111A | | Modify `components/empty-state.tsx`. The existing component has props for icon, title, description, and action. Phase 15 requires `actionLabel?: string`, `actionHref?: string`, and `onAction?: () => void` as typed alternatives. The existing `action` prop should be kept for backwards compatibility. Add new props: `actionLabel`, `actionHref` (renders `<Button asChild><Link href={actionHref}>` when provided), `onAction` (renders `<Button onClick={onAction}>` when provided without `actionHref`). Also update the visual style to use Shadcn `Card` with `border-none bg-muted/30`. Keep backwards compatibility — existing callers that pass `action` as a node still work. |
| 111.6 | Create `TemplateReadinessCard` component | 111A | | `components/setup/template-readiness-card.tsx`. Client component (`"use client"` — needs tooltip hover state). Props: `TemplateReadinessCardProps` from architecture doc Section 15.6.1. Renders: list of templates. Ready: Lucide `CheckCircle2` in teal, template name, "Generate" button using `generateHref(templateId)`. Not-ready: Lucide `AlertTriangle` in amber, template name, disabled "Generate" button with Shadcn `Tooltip` showing "Fill these fields first: {missingFields.join(', ')}". Pattern: use `import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"`. Check if Tooltip component exists in `components/ui/` — if not, add it via `npx shadcn@latest add tooltip`. |
| 111.7 | Create `lib/api/setup-status.ts` API client | 111A | | `frontend/lib/api/setup-status.ts`. Server-side fetch functions (no `"use client"` — called from server components in `page.tsx`). Functions: `fetchProjectSetupStatus(id: string)`, `fetchProjectUnbilledSummary(id: string)`, `fetchCustomerReadiness(id: string)`, `fetchCustomerUnbilledSummary(id: string)`, `fetchTemplateReadiness(entityType: string, entityId: string)`. Each calls `lib/api.ts` (the existing API client with Bearer JWT attached). Return types match the DTO shapes defined in TypeScript interfaces. Pattern: follow existing API fetch function style in the project. |
| 111.8 | Define API response TypeScript types | 111A | | In `lib/api/setup-status.ts` or a separate `lib/types/setup-status.ts`: define TypeScript types mirroring the Java records: `ProjectSetupStatus`, `RequiredFieldStatus`, `FieldStatus`, `CustomerReadiness`, `ChecklistProgress`, `UnbilledTimeSummary`, `ProjectUnbilledBreakdown`, `TemplateReadiness`. These mirror the backend DTOs from architecture doc Section 15.2.1. |
| 111.9 | Write `SetupProgressCard` component tests | 111A | | `components/setup/setup-progress-card.test.tsx`. ~5 tests. Cases: (1) overallComplete=false -> full card with progress bar visible, (2) overallComplete=true -> compact badge visible, full card hidden, (3) expand button toggles full card visibility, (4) step with actionHref renders link with correct href, (5) step with permissionRequired=true and canManage=false -> action link not rendered. Use `@testing-library/react` + `vi.fn()`. `afterEach(() => cleanup())` for Radix components. |
| 111.10 | Write `ActionCard` component tests | 111A | | `components/setup/action-card.test.tsx`. ~3 tests. Cases: (1) renders icon, title, description, (2) primaryAction with href renders Link, (3) variant="accent" applies accent class. |
| 111.11 | Write `FieldValueGrid` component tests | 111A | | `components/setup/field-value-grid.test.tsx`. ~4 tests. Cases: (1) renders field name and value pairs, (2) required field with null value shows "Not set" with amber border, (3) grouped fields render under group heading, (4) editHref renders "Edit Fields" link. |
| 111.12 | Write `EmptyState` component tests | 111A | | `components/empty-state.test.tsx` (if not already present). ~3 tests. Cases: (1) renders with actionHref -> Link button, (2) renders with onAction callback -> button calls callback on click, (3) renders without action -> no button. Verify backwards compatibility: (4) existing `action` prop still renders. Run existing tests first to understand current test coverage. |
| 111.13 | Write `TemplateReadinessCard` component tests | 111A | | `components/setup/template-readiness-card.test.tsx`. ~4 tests. Cases: (1) ready template -> shows checkmark and enabled Generate button, (2) not-ready template -> shows warning icon and disabled Generate button, (3) not-ready template has tooltip with missing fields listed, (4) multiple templates renders all in list. Tooltip visibility may require user-event hover simulation. |
| 111.14 | Add `components/setup/index.ts` barrel export | 111A | | `components/setup/index.ts` — re-exports all 5 setup components for cleaner imports in page files. |
| 111.15 | Run frontend lint and tests after 111A | 111A | | `pnpm run lint` and `pnpm test` from `frontend/`. Confirm 0 lint errors and all new tests pass. Check that the `EmptyState` upgrade does not break any existing tests that import it — search for existing imports to find all callers. |

### Key Files

**Slice 111A — Create:**
- `frontend/components/setup/setup-progress-card.tsx`
- `frontend/components/setup/action-card.tsx`
- `frontend/components/setup/field-value-grid.tsx`
- `frontend/components/setup/template-readiness-card.tsx`
- `frontend/components/setup/index.ts`
- `frontend/lib/api/setup-status.ts`
- `frontend/components/setup/setup-progress-card.test.tsx`
- `frontend/components/setup/action-card.test.tsx`
- `frontend/components/setup/field-value-grid.test.tsx`
- `frontend/components/setup/template-readiness-card.test.tsx`

**Slice 111A — Modify:**
- `frontend/components/empty-state.tsx` — Upgrade with `actionHref`, `onAction`, card-based styling
- `frontend/components/empty-state.test.tsx` — Add new test cases (create if not present)

**Read for context:**
- `architecture/phase15-contextual-actions-setup-guidance.md` Sections 15.6.1, 15.7.2, 15.9.2, 15.9.3
- `frontend/components/empty-state.tsx` — Existing component to upgrade
- `frontend/CLAUDE.md` — Anti-patterns, Radix cleanup, "use client" rules
- `frontend/components/ui/` — Confirm Tooltip, Progress, Card components available
- `frontend/lib/api.ts` — Existing API client to call from `setup-status.ts`

### Architecture Decisions

- **ADR-067**: Components live on entity detail pages, not a central dashboard.
- **`EmptyState` upgrade (not replace)**: The existing `components/empty-state.tsx` already has callers in the codebase. Adding new props with backwards-compatible defaults avoids breaking existing usages while enabling the richer action patterns needed by Epic 114.
- **`SetupProgressCard` is a client component**: The collapse/expand behavior requires `useState`. All other setup components are server components by default.
- **`TemplateReadinessCard` is a client component**: Tooltip hover state and the disabled button behavior require client-side interactivity.

---

## Epic 112: Project Detail Page Integration

**Goal**: Wire the reusable setup components from Epic 111 into the project detail page's Overview tab. The `page.tsx` server component fetches setup status, unbilled summary, and template readiness in parallel using `Promise.all()`. The Overview tab renders `SetupProgressCard`, `ActionCard` (for unbilled time), `TemplateReadinessCard`, and `FieldValueGrid`. All action links are permission-aware using existing `canManage` flags.

**References**: Architecture doc Section 15.6.2 (project page integration), Section 15.5.1 (sequence diagram).

**Dependencies**: Epic 111 (components), Epics 109 + 110 (endpoints must be deployed)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **112A** | 112.1–112.9 | Modify `projects/[id]/page.tsx` to fetch 3 new endpoints in parallel. Modify `components/projects/overview-tab.tsx` to render 4 setup guidance cards. Map `ProjectSetupStatus` to `SetupStep[]` array with action hrefs. Permission-aware rendering using `canManage`. ~5 frontend tests. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 112.1 | Audit current `projects/[id]/page.tsx` data fetching | 112A | | Read `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` to understand: (1) what is currently fetched, (2) how `params` are accessed (must use `await params` per Next.js 16), (3) what props are passed to overview tab, (4) how `canManage` / `isAdmin` flags are computed and threaded to child components. |
| 112.2 | Add parallel fetch calls to `projects/[id]/page.tsx` | 112A | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Add three new fetches alongside the existing project fetch: `fetchProjectSetupStatus(id)`, `fetchProjectUnbilledSummary(id)`, `fetchTemplateReadiness("PROJECT", id)`. Use `Promise.all()` to keep them parallel. Pass the results as props to the Overview tab component. Architecture doc Section 15.5.1 shows the parallel fetch pattern. |
| 112.3 | Map `ProjectSetupStatus` to `SetupStep[]` | 112A | | Either in `page.tsx` or in a helper function `mapProjectSetupSteps(status, canManage)`: produce the `SetupStep[]` array from architecture doc Section 15.6.2. Step list: `customerAssigned` -> "Customer assigned" / `?tab=customers`, `rateCardConfigured` -> "Rate card configured" / `?tab=rates` (permissionRequired: true), `budgetConfigured` -> "Budget set" / `?tab=budget`, `teamAssigned` -> "Team members added" / `?tab=members`, `requiredFields` -> "Required fields filled (X/Y)" / `#custom-fields`. When `requiredFields.total === 0`, show "No required fields defined" as detail (not a failing check). |
| 112.4 | Modify overview tab to render setup guidance | 112A | | Modify `frontend/components/projects/overview-tab.tsx` (or equivalent). Add the four new cards at the top, above existing content: (1) `SetupProgressCard` — always rendered, auto-collapses when `overallComplete = true`. (2) `ActionCard` — only rendered when `unbilledSummary.entryCount > 0`. (3) `TemplateReadinessCard` — only rendered when `templateReadiness.length > 0`. (4) `FieldValueGrid` — rendered when custom fields exist. |
| 112.5 | Format unbilled time for `ActionCard` description | 112A | | Import `formatCurrency` from `lib/format.ts` (or equivalent). Description: `${formatCurrency(unbilledSummary.totalAmount, unbilledSummary.currency)} across ${unbilledSummary.totalHours.toFixed(1)} hours`. Primary action: "Create Invoice" -> `/org/${slug}/invoices/new?projectId=${id}`. Secondary action: "View Entries" -> `?tab=time`. |
| 112.6 | Add permission guards for action links | 112A | | In the `SetupStep` mappings: `permissionRequired: true` on "Configure Rates" (rate card step) and "Set Budget" (budget step) — these only show if `canManage` (admin/owner or project lead). In `ActionCard` for unbilled time: "Create Invoice" only renders if `isAdmin` prop is true. Thread `canManage` and `isAdmin` from `page.tsx` through to the components. |
| 112.7 | Write project page integration tests | 112A | | ~5 tests. Cases: (1) setup status with overallComplete=false -> SetupProgressCard visible with progress bar, (2) unbilled entryCount=0 -> ActionCard not rendered, (3) unbilled entryCount > 0 -> ActionCard visible with amount, (4) canManage=false -> "Configure Rates" link not visible, (5) template readiness empty -> TemplateReadinessCard not rendered. Mock the fetch functions with `vi.mock()`. |
| 112.8 | Verify overview tab accepts new props without TypeScript errors | 112A | | After adding new props, run `pnpm run build` to confirm no TypeScript type errors. The component's prop interface must be explicitly extended. Do NOT use `any` for the new prop types. |
| 112.9 | Run lint and test after 112A | 112A | | `pnpm run lint && pnpm test` from `frontend/`. All new and existing tests must pass. |

### Key Files

**Slice 112A — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add parallel fetches for 3 new endpoints
- `frontend/components/projects/overview-tab.tsx` — Render 4 setup guidance cards

**Read for context:**
- `architecture/phase15-contextual-actions-setup-guidance.md` Sections 15.6.2, 15.8.2
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Current data fetching structure
- `frontend/components/projects/overview-tab.tsx` — Current tab content structure
- `frontend/lib/format.ts` — Confirm `formatCurrency` function name and signature
- `frontend/components/setup/` — Components created in Epic 111

### Architecture Decisions

- **ADR-067**: Cards live on the detail page Overview tab — no dashboard modifications.
- **`Promise.all()` for parallel fetches**: All four fetches run in parallel. If any fails, the page falls back gracefully — setup cards are optional enhancements, not page-blocking data.
- **Auto-collapse when complete**: `SetupProgressCard` defaults to collapsed when `overallComplete = true`.

---

## Epic 113: Customer Detail Page Integration

**Goal**: Wire the setup components into the customer detail page. The customer page fetches four endpoints in parallel and renders `SetupProgressCard` (adapted for customer readiness steps), `ActionCard` (unbilled time with per-project breakdown), `TemplateReadinessCard`, and `FieldValueGrid`. Adds a lifecycle action prompt — contextual buttons ("Start Onboarding", "Activate Customer") based on the customer's lifecycle status and checklist completion.

**References**: Architecture doc Section 15.6.2 (customer page integration), Section 15.5.2 (sequence diagram), Section 15.8.2 (frontend action visibility).

**Dependencies**: Epic 111 (components), Epics 109 + 110 (endpoints)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **113A** | 113.1–113.9 | Modify `customers/[id]/page.tsx` to fetch 4 new endpoints in parallel. Map `CustomerReadiness` to setup steps. Render 4 setup guidance cards above tabs. Add lifecycle action prompt with "Start Onboarding" and "Activate Customer" server actions. ~5 frontend tests. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 113.1 | Audit current `customers/[id]/page.tsx` data fetching | 113A | | Read `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` to understand: (1) what is currently fetched, (2) how `params` are accessed, (3) how `isAdmin`/`canManage` is computed, (4) where the customer's lifecycle status is currently displayed. |
| 113.2 | Add parallel fetch calls to `customers/[id]/page.tsx` | 113A | | Add four parallel fetches: `fetchCustomer(id)` (existing), `fetchCustomerReadiness(id)`, `fetchCustomerUnbilledSummary(id)`, `fetchTemplateReadiness("CUSTOMER", id)`. Use `Promise.all()`. Pass results as props to the component tree. |
| 113.3 | Map `CustomerReadiness` to setup steps | 113A | | In `page.tsx` or a helper: produce `SetupStep[]`. Steps: (1) `hasLinkedProjects` -> "Linked to a project" / "Link Project" (href: `?tab=projects`), (2) `requiredFields.filled === requiredFields.total` -> "Required fields filled (X/Y)" / "Fill Fields" (href: `?tab=custom-fields`), (3) checklistProgress -> "Onboarding checklist (X/Y complete)" / "View Checklist" (href: `?tab=onboarding`), (4) `lifecycleStatus === 'ACTIVE'` -> "Customer is active". `overallComplete` maps to `customerReadiness.overallReadiness === "Complete"`. |
| 113.4 | Render setup guidance cards above customer tabs | 113A | | Above `CustomerTabs`: render (1) `SetupProgressCard` using customer readiness steps. (2) `ActionCard` for unbilled time with per-project breakdown in description. (3) `TemplateReadinessCard`. (4) `FieldValueGrid` with customer custom fields. Only render each card when relevant. |
| 113.5 | Add lifecycle action prompt | 113A | | Below the `SetupProgressCard`, render a contextual call-to-action based on `lifecycleStatus`: `PROSPECT` -> "Ready to start onboarding?" + "Start Onboarding" button. `ONBOARDING` + `checklistProgress.percentComplete === 100` -> "All items verified -- Activate Customer" button. Only render if `isAdmin`. Use existing `POST /api/customers/{id}/transition` server action from Phase 14. |
| 113.6 | Handle null `checklistProgress` in step mapping | 113A | | When `checklistProgress = null`, omit the checklist step from the steps list. Only add the step if `checklistProgress != null`. |
| 113.7 | Format customer unbilled summary description | 113A | | If `byProject` has 1 project -> `"${totalAmount} from ${byProject[0].projectName}"`. If multiple -> `"${totalAmount} across ${byProject.length} projects"`. Primary action: "Create Invoice". Secondary action: "View All Entries". |
| 113.8 | Write customer page integration tests | 113A | | ~5 tests. Cases: (1) PROSPECT customer -> "Start Onboarding" prompt visible (if isAdmin), (2) non-admin -> lifecycle prompt not rendered, (3) overallReadiness="Complete" -> SetupProgressCard collapsed, (4) unbilledSummary.entryCount=0 -> ActionCard not rendered, (5) ONBOARDING + checklist 100% -> "Activate Customer" button visible. |
| 113.9 | Run lint and test after 113A | 113A | | `pnpm run lint && pnpm test` from `frontend/`. All new and existing customer page tests must pass. |

### Key Files

**Slice 113A — Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add parallel fetches, render 4 setup guidance cards + lifecycle prompt
- `frontend/app/(app)/org/[slug]/customers/[id]/actions.ts` — May need new `startOnboarding` / `activateCustomer` server actions (or reuse existing transition action from Phase 14)

**Read for context:**
- `architecture/phase15-contextual-actions-setup-guidance.md` Sections 15.6.2, 15.8.2
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Current structure
- `frontend/app/(app)/org/[slug]/customers/[id]/actions.ts` — Existing customer actions
- `frontend/components/setup/` — Components from Epic 111

### Architecture Decisions

- **ADR-067**: Customer readiness cards go above the existing tabs — always visible when the customer page is open.
- **Lifecycle action prompt is conditional on role**: Only admins can trigger lifecycle transitions.
- **Null checklist step**: When `checklistProgress = null`, the checklist step is omitted.

---

## Epic 114: Empty State Rollout

**Goal**: Replace all 8 "No items" text placeholders across the application with the upgraded `EmptyState` component from Epic 111. Each replacement is a mechanical substitution using the icon/heading/description/action catalog from architecture doc Section 15.7.

**References**: Architecture doc Section 15.7 (empty state catalog), Section 15.7.3 (implementation approach).

**Dependencies**: Epic 111 (`EmptyState` component upgrade with `actionHref` and `onAction` props)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **114A** | 114.1–114.12 | Replace empty states in 8 locations: project tasks tab, project time entries, project documents, project team, customer projects, customer documents, invoice list, custom fields section. Write ~8 rendering tests (one per location). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 114.1 | Locate and audit all 8 empty state locations | 114A | | Read each of the 8 files to find the current empty state text. Files to check: `components/tasks/task-list-panel.tsx`, `components/tasks/time-entry-list.tsx`, `components/documents/documents-panel.tsx`, `components/projects/project-members-panel.tsx`, `components/customers/customer-projects-panel.tsx`, customer documents component, invoice list page, and custom fields display. Note the exact condition and current placeholder text. |
| 114.2 | Replace empty state in task list panel | 114A | | Modify `components/tasks/task-list-panel.tsx`. Replace existing "No tasks yet" with `<EmptyState icon={CheckSquare} title="Track work on this project" description="Create tasks to organise and assign work items for your team." actionLabel="Add Task" onAction={() => setShowAddTask(true)} />`. Import `CheckSquare` from `lucide-react`. |
| 114.3 | Replace empty state in time entry list | 114A | | Modify `components/tasks/time-entry-list.tsx`. Replace "No time logged" text with `<EmptyState icon={Clock} title="No time logged yet" description="Track time spent on this project to enable billing and profitability reporting." actionLabel="Log Time" onAction={() => setShowLogTime(true)} />`. |
| 114.4 | Replace empty state in documents panel | 114A | | Modify `components/documents/documents-panel.tsx`. Replace "No documents" text with `<EmptyState icon={FileUp} title="No documents uploaded" description="Upload proposals, contracts, and deliverables for this project." actionLabel="Upload Document" onAction={() => triggerFileUpload()} />`. |
| 114.5 | Replace empty state in project members panel | 114A | | Modify `components/projects/project-members-panel.tsx`. Replace with `<EmptyState icon={Users} title="Add your team" description="Invite team members to collaborate on this project." actionLabel="Add Member" onAction={() => setShowAddMember(true)} />`. |
| 114.6 | Replace empty state in customer projects panel | 114A | | Modify `components/customers/customer-projects-panel.tsx`. Replace "No projects" text with `<EmptyState icon={Folder} title="No projects yet" description="Create a project for this customer to start tracking work and billing time." actionLabel="Create Project" actionHref={createProjectHref} />`. |
| 114.7 | Replace empty state in customer documents panel | 114A | | Locate the customer documents component and replace "No documents" text with `<EmptyState icon={FileUp} title="No documents uploaded" description="Upload contracts, ID documents, and correspondence for this customer." actionLabel="Upload Document" onAction={() => triggerFileUpload()} />`. |
| 114.8 | Replace empty state in invoice list page | 114A | | Modify `frontend/app/(app)/org/[slug]/invoices/page.tsx`. Replace "No invoices yet" text with `<EmptyState icon={Receipt} title="No invoices yet" description="Generate invoices from tracked time to bill your customers." actionLabel="Create Invoice" actionHref={createInvoiceHref} />`. |
| 114.9 | Replace empty state in custom fields display | 114A | | Locate where custom field values are displayed with a "No custom fields configured" placeholder. Replace with `<EmptyState icon={ListChecks} title="No custom fields configured" description="Custom fields let you track additional information specific to your workflow." actionLabel="Configure Fields" actionHref={configureFieldsHref} />`. |
| 114.10 | Verify all import paths are correct after replacements | 114A | | After all 8 replacements, confirm each file imports `EmptyState` from `"@/components/empty-state"`. Confirm each icon import is from `"lucide-react"`. Run `pnpm run lint` to catch missing imports. |
| 114.11 | Write rendering tests for all 8 empty state locations | 114A | | Add or extend test files for each modified component. Minimum 1 test per location: when the data list is empty, the `EmptyState` with the correct `title` is rendered. 8 tests total. `afterEach(() => cleanup())` where Radix components are present. |
| 114.12 | Run full frontend test suite after 114A | 114A | | `pnpm run lint && pnpm test` from `frontend/`. All 8 new empty state tests must pass. No regressions in existing tests. |

### Key Files

**Slice 114A — Modify (8 files):**
- `frontend/components/tasks/task-list-panel.tsx` — Project tasks empty state
- `frontend/components/tasks/time-entry-list.tsx` — Project time entries empty state
- `frontend/components/documents/documents-panel.tsx` — Project documents empty state
- `frontend/components/projects/project-members-panel.tsx` — Project team empty state
- `frontend/components/customers/customer-projects-panel.tsx` — Customer projects empty state
- Customer documents component (file TBD)
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` — Invoice list empty state
- Custom fields component (file TBD)

**Read for context:**
- `architecture/phase15-contextual-actions-setup-guidance.md` Sections 15.7.1, 15.7.2, 15.7.3
- `frontend/components/empty-state.tsx` — The upgraded component from Epic 111
- Each of the 8 files listed above — to locate the exact empty state text to replace

### Architecture Decisions

- **Upgrade, not replace, `EmptyState`**: Adding `actionHref`/`onAction` as optional props with backward-compatible defaults prevents regressions.
- **Mechanical substitution**: Each of the 8 replacements is a 1-10 line change. Do NOT redesign the components or restructure the containing panels.
- **8 tests, 1 per location**: Each test verifies only that the `EmptyState` title text is present when the list is empty.
