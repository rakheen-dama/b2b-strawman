# Phase 9 — Operational Dashboards

Phase 9 adds the **operational dashboards layer** — the connective tissue that turns existing data into at-a-glance situational awareness. It introduces three purpose-built dashboards (Company Dashboard, Project Overview tab, Personal Dashboard), a project health scoring algorithm, cross-project activity aggregation, and a shared data visualization component library. All dashboard data is derived from existing entities through aggregation queries cached in memory with short TTLs. No new persistent entities are introduced.

**Architecture doc**: `architecture/phase9-operational-dashboards.md`

**ADRs**: [ADR-044](../adr/ADR-044-dashboard-aggregation-caching.md) (aggregation caching), [ADR-045](../adr/ADR-045-project-health-scoring.md) (health scoring), [ADR-046](../adr/ADR-046-dashboard-charting-approach.md) (hybrid SVG + Recharts), [ADR-047](../adr/ADR-047-dashboard-layout-strategy.md) (fixed layout)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 75 | Health Scoring & Project Health Endpoints | Backend | -- | M | 75A, 75B | **Done** (PRs #151, #153) |
| 76 | Company Dashboard Backend | Backend | 75 | L | 76A, 76B | **Done** (PRs #156, #157) |
| 77 | Shared Dashboard Components | Frontend | -- | M | 77A, 77B | **Done** (PRs #154, #155) |
| 78 | Company Dashboard Frontend | Frontend | 76, 77 | M | 78A, 78B | **Done** (PRs #159, #160) |
| 79 | Project Overview Tab | Both | 75, 77 | M | 79A, 79B | **Done** (PRs #158, #161) |
| 80 | Personal Dashboard | Both | 79A, 77 | M | 80A, 80B | 80A **Done** (PR #162) |

## Dependency Graph

```
[E75 Health Scoring] ──┬──► [E76 Company Dashboard BE] ──► [E78 Company Dashboard FE]
     (Backend)         │                                         ▲
                       │                                         │
                       ├──► [E79A Project Overview BE] ──► [E79B Project Overview FE]
                       │            │
                       │            └──► [E80A Personal Dashboard FE] ──► [E80B Personal Dashboard FE]
                       │                                                       ▲
[E77 Shared Components]──────────────────────────────────────────────────────────┘
     (Frontend)        └──► [E78] + [E79B] + [E80B]
```

**Parallel tracks**:
- Epic 75 (backend health scoring) and Epic 77 (frontend shared components) have no dependencies and can start immediately in parallel.
- After Epic 75 completes: Epic 76 (company dashboard backend) and Epic 79A (project overview backend) can run in parallel.
- After Epic 76 + Epic 77: Epic 78 (company dashboard frontend) can proceed.
- After Epic 75 + Epic 77: Epic 79B (project overview frontend) can proceed.
- After Epic 79A + Epic 77: Epic 80 (personal dashboard) can proceed.
- Epic 78 and Epic 79B can run in parallel once their respective dependencies are met.

## Implementation Order

### Stage 1: Foundation (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 75 | 75A | HealthStatus enum, ProjectHealthCalculator, ProjectHealthInput/Result records, unit tests. Pure utility class with no Spring dependencies — foundation for all health scoring. |
| 1b | Epic 75 | 75B | DashboardService (project health methods only), project health + task summary endpoints, TaskRepository query methods, Caffeine project cache, integration tests. Depends on 75A. |
| 1c | Epic 77 | 77A | KpiCard, HealthBadge, MiniProgressRing, SparklineChart — pure SVG components with tests. No external dependencies. |
| 1d | Epic 77 | 77B | HorizontalBarChart (Recharts), DateRangeSelector, Recharts dependency, component tests. Can run parallel with 77A. |

### Stage 2: Company Dashboard Backend + Project Overview Backend (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 76 | 76A | DashboardController, KPI endpoint, project health list endpoint, KPI + trend DTOs, org-level cache, native SQL aggregation queries, permission-based financial field redaction. Depends on 75B. |
| 2b | Epic 76 | 76B | Team workload endpoint, cross-project activity endpoint, team workload + activity DTOs, integration tests for all company dashboard endpoints with role assertions. Depends on 76A. |
| 2c | Epic 79 | 79A | Project member-hours endpoint, personal dashboard backend endpoint, PersonalDashboard + related DTOs, integration tests. Depends on 75B. |

### Stage 3: Frontend Dashboards (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 78 | 78A | Dashboard page replacement, KpiCardRow, ProjectHealthWidget (with filter tabs), dashboard fetch functions, date range URL sync. Depends on 76A + 77A. |
| 3b | Epic 78 | 78B | TeamWorkloadWidget, RecentActivityWidget, responsive layout, empty states, permission-based KPI visibility. Depends on 76B + 77B. |
| 3c | Epic 79 | 79B | OverviewTab server component, project detail tab integration (Overview as first/default tab), health badge + metrics strip, tasks mini-list, team hours + activity sections, Promise.allSettled pattern. Depends on 79A + 77A + 77B. |

### Stage 4: Personal Dashboard

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 80 | 80A | PersonalKpis component, TimeBreakdown chart, UpcomingDeadlines list, personal dashboard fetch function. Depends on 79A (backend endpoint) + 77A + 77B. |
| 4b | Epic 80 | 80B | Enhance My Work page with dashboard header, date range selector integration, enhanced task list with urgency grouping, tests. Depends on 80A. |

### Timeline

```
Stage 1:  [75A] → [75B]  //  [77A // 77B]             ← foundation (parallel tracks)
Stage 2:  [76A → 76B]  //  [79A]                       ← backend dashboards (parallel)
Stage 3:  [78A → 78B]  //  [79B]                       ← frontend dashboards (parallel)
Stage 4:  [80A → 80B]                                   ← personal dashboard
```

---

## Epic 75: Health Scoring & Project Health Endpoints

**Goal**: Implement the deterministic rule-based project health scoring algorithm and the project-scoped health and task summary backend endpoints. This epic delivers the `ProjectHealthCalculator` utility class (pure function, no Spring dependencies), the `DashboardService` project-level methods, the Caffeine project-level cache, new `TaskRepository` query methods, and two REST endpoints (`/api/projects/{projectId}/health` and `/api/projects/{projectId}/task-summary`).

**References**: Architecture doc Sections 9.2, 9.3 (health algorithm), 9.4 (caching), 9.5.2 (project endpoints), 9.11 (queries), 9.12 (permissions). [ADR-044](../adr/ADR-044-dashboard-aggregation-caching.md), [ADR-045](../adr/ADR-045-project-health-scoring.md).

**Dependencies**: None (first epic in Phase 9)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **75A** | 75.1-75.5 | HealthStatus enum, ProjectHealthInput/Result records, ProjectHealthCalculator static utility, exhaustive unit tests (~15 tests) | |
| **75B** | 75.6-75.12 | DashboardService (project methods), DashboardController (project endpoints), TaskRepository query additions, Caffeine project cache, AuditEventRepository addition, integration tests (~10 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 75.1 | Create HealthStatus enum | 75A | | `dashboard/HealthStatus.java`. Four values: HEALTHY, AT_RISK, CRITICAL, UNKNOWN. Add `severity()` method returning int (UNKNOWN=0, HEALTHY=1, AT_RISK=2, CRITICAL=3) for escalation comparisons. Pattern: standard Java enum, no Spring dependencies. |
| 75.2 | Create ProjectHealthInput record | 75A | | `dashboard/ProjectHealthInput.java`. Record with fields: `int totalTasks, int doneTasks, int overdueTasks, Double budgetConsumedPercent, int alertThresholdPct, double completionPercent, int daysSinceLastActivity`. See Section 9.3.1. |
| 75.3 | Create ProjectHealthResult record | 75A | | `dashboard/ProjectHealthResult.java`. Record with fields: `HealthStatus status, List<String> reasons`. |
| 75.4 | Implement ProjectHealthCalculator | 75A | | `dashboard/ProjectHealthCalculator.java`. Final utility class with private constructor. Static `calculate(ProjectHealthInput) -> ProjectHealthResult` method. Static `escalate(HealthStatus, HealthStatus)` helper. Constants: `OVERDUE_CRITICAL_THRESHOLD = 0.3`, `OVERDUE_AT_RISK_THRESHOLD = 0.1`, `INACTIVITY_DAYS_THRESHOLD = 14`, `DEFAULT_BUDGET_ALERT_THRESHOLD = 80`. Six rules per Section 9.3.1 pseudocode. Pattern: pure function, no Spring dependencies, no database access. |
| 75.5 | Add ProjectHealthCalculator unit tests | 75A | | `dashboard/ProjectHealthCalculatorTest.java` (~15 tests): (1) no tasks returns UNKNOWN, (2) all tasks done returns HEALTHY, (3) >30% overdue returns CRITICAL, (4) 10-30% overdue returns AT_RISK, (5) budget >= 100% returns CRITICAL, (6) budget at alert threshold with low completion returns AT_RISK, (7) inactive >14 days returns AT_RISK, (8) inactive exactly 14 days returns HEALTHY, (9) null budget skips budget rules, (10) multiple rules escalate to worst, (11) CRITICAL + AT_RISK reasons both appear, (12) zero overdue returns HEALTHY, (13) budget below alert threshold returns HEALTHY, (14) 100% completion with high budget consumed returns HEALTHY, (15) edge case: 1 task overdue of 3 total (33.3% > 30%) returns CRITICAL. Standard JUnit 5 tests, no Spring context. Pattern: follow unit test conventions in `backend/src/test/`. |
| 75.6 | Add TaskRepository query methods for dashboard | 75B | | Modify `task/TaskRepository.java`. Add JPQL methods: `@Query("SELECT COUNT(t) FROM Task t WHERE t.projectId = :projectId") long countByProjectId(@Param("projectId") UUID projectId)`, `@Query("SELECT COUNT(t) FROM Task t WHERE t.projectId = :projectId AND t.status = :status") long countByProjectIdAndStatus(...)`, `@Query("SELECT COUNT(t) FROM Task t WHERE t.projectId = :projectId AND t.status <> 'DONE' AND t.dueDate < :today") long countOverdueByProjectId(...)`, `@Query("SELECT COUNT(t) FROM Task t WHERE t.status <> 'DONE' AND t.dueDate < :today") long countOrgOverdue(...)`. JPQL queries benefit from Hibernate `@Filter` for tenant isolation. Pattern: follow existing JPQL query methods in `TaskRepository`. |
| 75.7 | Add AuditEventRepository method for days since last activity | 75B | | Modify `audit/AuditEventRepository.java`. Add native SQL query: `findMostRecentByProject(UUID projectId)` — `SELECT MAX(ae.occurred_at) FROM audit_events ae WHERE (ae.details->>'project_id')::uuid = :projectId`. Returns `Optional<Instant>`. RLS handles tenant isolation. Pattern: follow existing native queries in `AuditEventRepository`. |
| 75.8 | Create dashboard DTO records | 75B | | `dashboard/dto/ProjectHealthDetail.java` — record with `HealthStatus healthStatus, List<String> healthReasons, ProjectHealthMetrics metrics`. `dashboard/dto/ProjectHealthMetrics.java` — record per Section 9.2.2. `dashboard/dto/TaskSummary.java` — record with `int todo, int inProgress, int inReview, int done, int total, int overdueCount`. |
| 75.9 | Create DashboardService (project methods) | 75B | | `dashboard/DashboardService.java`. `@Service`. Inject: `TaskRepository`, `TimeEntryRepository`, `AuditEventRepository`, `ProjectRepository`. Caffeine project-level cache field: `Cache<String, Object>` with 5,000 max entries, 1-min TTL. Methods: `getProjectHealth(UUID projectId, String tenantId)` — gathers task counts, budget data (native SQL), last activity; builds `ProjectHealthInput`; calls `ProjectHealthCalculator.calculate()`; assembles `ProjectHealthDetail`. `getTaskSummary(UUID projectId, String tenantId)` — conditional COUNT query per Section 9.11.1 query #3. Both use `getIfPresent() + put()` cache pattern per ADR-044. Budget query uses native SQL with LEFT JOIN to `project_budgets` — returns null if no budget row exists. Pattern: follow `timeentry/TimeEntryService.java` for repository injection. Cache pattern: follow existing Caffeine usage in `TenantFilter`. |
| 75.10 | Create DashboardController (project endpoints) | 75B | | `dashboard/DashboardController.java`. `@RestController`. Two endpoints: `GET /api/projects/{projectId}/health` — calls `ProjectAccessService.requireViewAccess()`, then `dashboardService.getProjectHealth()`. Returns `ProjectHealthDetail`. `GET /api/projects/{projectId}/task-summary` — same access check, returns `TaskSummary`. Extract tenantId from `RequestScopes.TENANT_ID`, memberId from `RequestScopes.MEMBER_ID`. Pattern: follow `activity/ActivityController.java` for project-scoped endpoint with access check. |
| 75.11 | Add DashboardController integration tests (project endpoints) | 75B | | `dashboard/DashboardProjectIntegrationTest.java` (~8 tests): (1) health returns HEALTHY for project with all tasks done, (2) health returns AT_RISK with overdue tasks, (3) health returns UNKNOWN for project with no tasks, (4) health includes reasons array, (5) task summary returns correct counts by status, (6) task summary includes overdue count, (7) non-member cannot access project health (404), (8) admin can access any project health. Seed: provision tenant, sync 2 members (admin + member), create project, create tasks with various statuses, add project member. Pattern: follow `activity/ActivityControllerIntegrationTest.java` (or similar Phase 6.5 integration test with tenant provisioning + data seeding). |
| 75.12 | Add Caffeine dependency to pom.xml if not present | 75B | | Check if `com.github.ben-manes.caffeine:caffeine` is already in `backend/pom.xml`. If not, add it. Caffeine is already used by `TenantFilter` and `MemberFilter`, so it should already be present. Verify and confirm. |

### Key Files

**Slice 75A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/HealthStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/ProjectHealthInput.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/ProjectHealthResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/ProjectHealthCalculator.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/dashboard/ProjectHealthCalculatorTest.java`

**Slice 75A — Read for context:**
- `architecture/phase9-operational-dashboards.md` Section 9.3 — algorithm specification

**Slice 75B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/ProjectHealthDetail.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/ProjectHealthMetrics.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/TaskSummary.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardProjectIntegrationTest.java`

**Slice 75B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskRepository.java` — Add count query methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — Add `findMostRecentByProject()`

**Slice 75B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityController.java` — Project-scoped endpoint pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectAccessService.java` — View access check
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — ScopedValue access
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — Native SQL query pattern

### Architecture Decisions

- **`dashboard/` package**: New feature package containing all Phase 9 backend code (service, controller, DTOs, calculator). Follows the feature-per-package convention.
- **`dto/` sub-package**: Dashboard has more DTOs than most features (15+ records). Sub-package keeps the main package readable. Records only — no entity classes.
- **ProjectHealthCalculator as static utility**: Not a Spring bean. Pure function with no dependencies, fully unit-testable without Spring context. Called by DashboardService.
- **Project endpoints in DashboardController**: Not in ProjectController. Dashboard-specific endpoints live with dashboard code, even though they are scoped under `/api/projects/{id}/`. This keeps ProjectController focused on CRUD operations.
- **Caffeine project cache in DashboardService**: Inline cache instance (field-level), consistent with TenantFilter and MemberFilter patterns. Not a Spring-managed `CacheManager` — simpler, no configuration overhead.
- **JPQL for task counts, native SQL for budget**: Consistent with the codebase pattern — simple entity queries use JPQL (benefiting from `@Filter`), complex cross-table aggregations and JSONB access use native SQL (relying on RLS).

---

## Epic 76: Company Dashboard Backend

**Goal**: Build the company-level dashboard backend endpoints — org-level KPIs with trend/previous-period computation, project health list sorted by severity, team workload by member with project breakdown, and cross-project activity feed. This epic extends `DashboardService` with org-level methods, creates the `DashboardController` company-scoped endpoints, implements the Caffeine org-level cache, and handles permission-based financial field redaction.

**References**: Architecture doc Sections 9.4 (service + caching), 9.5.1 (company endpoints), 9.11.1 (aggregation queries), 9.12 (permissions).

**Dependencies**: Epic 75 (ProjectHealthCalculator reused for project health list, DashboardService partially created)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **76A** | 76.1-76.7 | KPI endpoint with trend + previous period, project health list endpoint, KPI DTOs, org-level cache, native SQL aggregation queries, financial field redaction | |
| **76B** | 76.8-76.14 | Team workload endpoint, cross-project activity endpoint, DTOs, native SQL queries, role-based filtering, full integration tests for all company dashboard endpoints (~12 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 76.1 | Create KPI-related DTO records | 76A | | `dashboard/dto/KpiResponse.java` — record with `int activeProjectCount, double totalHoursLogged, Double billablePercent, int overdueTaskCount, Double averageMarginPercent, List<TrendPoint> trend, KpiValues previousPeriod`. `dashboard/dto/KpiValues.java` — record (same fields minus trend/previousPeriod). `dashboard/dto/TrendPoint.java` — record with `String period, double value`. `dashboard/dto/ProjectHealth.java` — record per Section 9.2.2 (summary item for health list). See Section 9.5.1. |
| 76.2 | Add native SQL aggregation queries to repositories | 76A | | Modify `timeentry/TimeEntryRepository.java` — add native SQL query `findOrgHoursSummary(LocalDate from, LocalDate to)` returning total_minutes and billable_minutes. Uses RLS. Modify `project/ProjectRepository.java` — add `countActiveProjects()` JPQL query (count where status is active/non-archived). Pattern: follow existing native SQL queries in `TimeEntryRepository`. Use `CAST(:from AS DATE)` for nullable params if needed. |
| 76.3 | Implement trend computation in DashboardService | 76A | | Add private method to `DashboardService`: `computeTrend(String tenantId, LocalDate from, LocalDate to)`. Determines granularity (daily for 1-7 days, weekly for 8-90, monthly for 91+). Generates last 6 period boundaries. Runs single GROUP BY native SQL query per trend (hours by period). Formats period labels as ISO strings. See Section 9.4.3. |
| 76.4 | Implement previous period computation in DashboardService | 76A | | Add private method: `computePreviousPeriod(String tenantId, LocalDate from, LocalDate to)`. Calculates mirror period (same duration, immediately preceding). Runs same KPI queries for previous period. Returns `KpiValues`. See Section 9.4.4. |
| 76.5 | Implement getCompanyKpis() in DashboardService | 76A | | Add org-level Caffeine cache field: `Cache<String, Object>` with 1,000 max entries, 3-min TTL. `getCompanyKpis(String tenantId, String orgRole, LocalDate from, LocalDate to)` — aggregates active project count, total hours, billable percent, overdue task count (from 75B TaskRepository method), avg margin percent (null until Phase 8 rate snapshots exist). Calls `computeTrend()` and `computePreviousPeriod()`. Applies financial field redaction for non-admin/non-owner via `withFinancialsRedacted()` pattern on KpiResponse. Cache key: `"{tenantId}:kpis:{from}_{to}"`. |
| 76.6 | Implement getProjectHealthList() in DashboardService | 76A | | `getProjectHealthList(String tenantId, UUID memberId, String orgRole)` — queries all accessible projects (admin/owner: all; member: via project_members join). For each project, calls `ProjectHealthCalculator.calculate()` with gathered metrics. Sorts result: CRITICAL first, then AT_RISK, HEALTHY, UNKNOWN; within each severity, by completionPercent ascending. Returns `List<ProjectHealth>`. Cache key: `"{tenantId}:project-health:{memberId}:{orgRole}"`. |
| 76.7 | Add KPI and project health list endpoints to DashboardController | 76A | | Add to `dashboard/DashboardController.java`: `GET /api/dashboard/kpis?from=&to=` (required date params, returns KpiResponse), `GET /api/dashboard/project-health` (returns `List<ProjectHealth>`). Both require valid Clerk JWT (ORG_MEMBER+). Extract tenantId, memberId, orgRole from RequestScopes. Pattern: follow existing controllers for param extraction. |
| 76.8 | Create team workload DTO records | 76B | | `dashboard/dto/TeamWorkloadEntry.java` — record with `UUID memberId, String memberName, double totalHours, double billableHours, List<ProjectHoursEntry> projects`. `dashboard/dto/ProjectHoursEntry.java` — record with `UUID projectId, String projectName, double hours`. |
| 76.9 | Create cross-project activity DTO | 76B | | `dashboard/dto/CrossProjectActivityItem.java` — record with `UUID eventId, String eventType, String description, String actorName, UUID projectId, String projectName, Instant occurredAt`. |
| 76.10 | Implement getTeamWorkload() in DashboardService | 76B | | `getTeamWorkload(String tenantId, UUID memberId, String orgRole, LocalDate from, LocalDate to)` — native SQL query per Section 9.11.1 query #4 (GROUP BY member, project). Post-process flat result to group by member, cap at 5 projects per member + "Other" aggregate. Admin/owner: all members. Regular member: own entry only. Cache key: `"{tenantId}:team-workload:{orgRole}:{from}_{to}"`. |
| 76.11 | Implement getCrossProjectActivity() in DashboardService | 76B | | `getCrossProjectActivity(String tenantId, UUID memberId, String orgRole, int limit)` — native SQL query per Section 9.11.1 query #5. Admin/owner: all events. Member: filter to projects they belong to via `project_members` subquery. Enrich results with actor name (from MemberRepository) and project name (from ProjectRepository or join). Cache key: `"{tenantId}:activity:{memberId}:{orgRole}:{limit}"`. |
| 76.12 | Add team workload and activity endpoints to DashboardController | 76B | | Add to `DashboardController`: `GET /api/dashboard/team-workload?from=&to=` (returns `List<TeamWorkloadEntry>`), `GET /api/dashboard/activity?limit=10` (optional limit param, default 10, max 50, returns `List<CrossProjectActivityItem>`). Both ORG_MEMBER+. |
| 76.13 | Add company dashboard integration tests | 76B | | `dashboard/DashboardCompanyIntegrationTest.java` (~12 tests): (1) KPI returns correct active project count, (2) KPI returns total hours for period, (3) KPI billablePercent is null for non-admin, (4) KPI billablePercent is present for admin, (5) KPI includes trend with correct period count, (6) KPI includes previousPeriod values, (7) project health list sorted by severity, (8) project health list filtered by member access, (9) team workload returns all members for admin, (10) team workload returns only self for member, (11) cross-project activity returns recent events, (12) cross-project activity filtered by member project access. Seed: provision tenant, sync admin + 2 members, create 3 projects, create tasks with various statuses and due dates, create time entries across date ranges. Pattern: follow `DashboardProjectIntegrationTest.java` from 75B. |
| 76.14 | Add V20 performance indexes migration (optional) | 76B | | `db/migration/tenant/V20__add_dashboard_indexes.sql`. Two indexes per Section 9.11.2: `CREATE INDEX IF NOT EXISTS idx_time_entries_date ON time_entries (date)` and `CREATE INDEX IF NOT EXISTS idx_tasks_due_date_status ON tasks (due_date, status) WHERE due_date IS NOT NULL`. Small, additive, no write overhead concern. Note: V20 may conflict with Phase 8 (Epic 69 also uses V20). If Phase 8 V20 already exists at implementation time, use V21 instead. |

### Key Files

**Slice 76A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/KpiResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/KpiValues.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/TrendPoint.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/ProjectHealth.java`

**Slice 76A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardService.java` — Add org-level methods + org cache
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardController.java` — Add company dashboard endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — Add native SQL aggregation query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectRepository.java` — Add active count query

**Slice 76A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — orgRole access
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` — Member name lookup

**Slice 76B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/TeamWorkloadEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/ProjectHoursEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/CrossProjectActivityItem.java`
- `backend/src/main/resources/db/migration/tenant/V20__add_dashboard_indexes.sql`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardCompanyIntegrationTest.java`

**Slice 76B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardService.java` — Add team workload + activity methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardController.java` — Add workload + activity endpoints

**Slice 76B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberRepository.java` — Member project access join
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — Activity query pattern

### Architecture Decisions

- **Two-slice split for company dashboard BE**: 76A handles KPIs + project health list (the most complex computations — trend, previous period, health aggregation). 76B handles team workload + activity (more straightforward aggregation) plus all integration tests. This keeps each slice to ~8 files created/modified.
- **Financial field redaction in service layer**: `KpiResponse.withFinancialsRedacted()` pattern per Section 9.12.3. Service nulls out `billablePercent` and `averageMarginPercent` for non-admin callers. Controller is thin delegation.
- **`averageMarginPercent` returns null initially**: Depends on Phase 8 rate snapshot data on TimeEntry. Until Phase 8 is complete, this field is null and the KPI card shows the empty state. Graceful degradation by design.
- **V20 migration number may need adjustment**: If Phase 8 Epic 69 (V20 for rate snapshots) has already been merged at implementation time, use V21 for dashboard indexes. The migration file comment should note this dependency.
- **Team workload "Other" aggregation in Java**: The SQL query returns flat (member, project, hours) rows. Post-processing to group, cap at 5 projects, and aggregate remainder as "Other" is done in DashboardService, not SQL. This avoids complex window functions and keeps the query simple.

---

## Epic 77: Shared Dashboard Components

**Goal**: Build the shared frontend component library that establishes the visual vocabulary for data visualization across all three dashboards. Components are pure presentational (no data fetching) and follow the Shadcn UI design language with olive color tokens. SVG-based sparklines and progress rings require no external dependencies. The HorizontalBarChart uses Recharts (new dependency).

**References**: Architecture doc Section 9.9 (component specs). [ADR-046](../adr/ADR-046-dashboard-charting-approach.md) (hybrid SVG + Recharts), [ADR-047](../adr/ADR-047-dashboard-layout-strategy.md) (fixed layout).

**Dependencies**: None (can be built in parallel with backend epics)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **77A** | 77.1-77.6 | KpiCard, HealthBadge, MiniProgressRing, SparklineChart — pure SVG components, no external dependencies, component tests (~8 tests) | |
| **77B** | 77.7-77.11 | HorizontalBarChart (Recharts dependency), DateRangeSelector, Recharts installation, component tests (~7 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 77.1 | Create KpiCard component | 77A | | `frontend/components/dashboard/kpi-card.tsx`. Props per Section 9.9.1: `label, value, changePercent, changeDirection, trend, href, emptyState`. Shadcn Card with olive border. Large value (text-2xl font-bold), small label (text-sm text-muted-foreground). Change indicator: green up arrow for positive, red down arrow for negative. SparklineChart integration (renders inline if trend provided). If `href` set, wrap in `Link`. If `emptyState` set and value is null/zero, show empty state text. "use client" if href uses Link. Pattern: follow `frontend/components/ui/card.tsx` for card styling. |
| 77.2 | Create HealthBadge component | 77A | | `frontend/components/dashboard/health-badge.tsx`. Props per Section 9.9.2: `status, reasons, size`. Three sizes: sm (dot only, 8px), md (dot + text), lg (dot + text + reasons). Colors: HEALTHY=green-500, AT_RISK=amber-500, CRITICAL=red-500, UNKNOWN=gray-400. Tooltip on sm/md sizes using Shadcn Tooltip. Inline reasons on lg. Pattern: follow `frontend/components/ui/badge.tsx` for styling conventions. |
| 77.3 | Create MiniProgressRing component | 77A | | `frontend/components/dashboard/mini-progress-ring.tsx`. Props per Section 9.9.3: `value (0-100), size (default 32), color (auto if omitted)`. Pure SVG circle with `stroke-dasharray` and `stroke-dashoffset`. Background circle in `muted`, foreground arc in computed color. Auto-color: >66 = green-500, >33 = amber-500, <=33 = red-500. Center text if size >= 40. No external library. |
| 77.4 | Create SparklineChart component | 77A | | `frontend/components/dashboard/sparkline-chart.tsx`. Props per Section 9.9.4: `data (number[]), width (default 80), height (default 24), color (default "currentColor")`. Pure SVG polyline. Normalize data to fill height. Subtle gradient fill below line. No axes, labels, grid. No external library. See ADR-046. |
| 77.5 | Add tests for KpiCard and HealthBadge | 77A | | `frontend/__tests__/dashboard/kpi-card.test.tsx` (~4 tests): renders value and label, renders change indicator (green up), renders empty state when value is null, renders as link when href provided. `frontend/__tests__/dashboard/health-badge.test.tsx` (~4 tests): renders green dot for HEALTHY, renders amber for AT_RISK with text at md size, renders reasons at lg size, shows tooltip on sm size hover. Pattern: follow `frontend/__tests__/` existing test files. Import `cleanup` from `@testing-library/react` and call in `afterEach` for Radix-based tooltip. |
| 77.6 | Add tests for MiniProgressRing and SparklineChart | 77A | | `frontend/__tests__/dashboard/mini-progress-ring.test.tsx` (~2 tests): renders SVG at boundary values (0, 50, 100), auto-color applied correctly. `frontend/__tests__/dashboard/sparkline-chart.test.tsx` (~2 tests): renders SVG polyline with data, handles empty data array. |
| 77.7 | Install Recharts dependency | 77B | | Run `pnpm add recharts` in frontend directory. Recharts is tree-shakeable. Only `BarChart, Bar, XAxis, YAxis, Tooltip, Legend, ResponsiveContainer` will be imported. See ADR-046. |
| 77.8 | Create HorizontalBarChart component | 77B | | `frontend/components/dashboard/horizontal-bar-chart.tsx`. "use client". Props per Section 9.9.5: `data (label + segments array), maxValue, showLegend`. Uses Recharts `BarChart` with `layout="vertical"`, `Bar` components for stacked segments, `Tooltip` for hover, `Legend` for color key. `ResponsiveContainer` for responsive width. Color palette: consistent project colors. Pattern: standard Recharts usage, see Recharts documentation. |
| 77.9 | Create DateRangeSelector component | 77B | | `frontend/components/dashboard/date-range-selector.tsx`. "use client". Props per Section 9.9.6: `value, onChange, presets`. Presets: "This Week" (Mon-Sun), "This Month", "Last 30 Days", "This Quarter", "Custom" (opens Shadcn DatePickerWithRange via react-day-picker). URL sync: reads from `searchParams`, updates via `useRouter().push()` with shallow navigation. Format: `?from=YYYY-MM-DD&to=YYYY-MM-DD`. Refresh button: `RefreshCw` icon from lucide-react, calls `router.refresh()`. Pattern: follow `frontend/components/ui/` for Shadcn component integration. |
| 77.10 | Add tests for HorizontalBarChart | 77B | | `frontend/__tests__/dashboard/horizontal-bar-chart.test.tsx` (~3 tests): renders bars for given data, renders legend when showLegend is true, handles empty data. Mock Recharts if needed for happy-dom compatibility. |
| 77.11 | Add tests for DateRangeSelector | 77B | | `frontend/__tests__/dashboard/date-range-selector.test.tsx` (~4 tests): renders preset buttons, selecting "This Month" calls onChange with correct dates, renders custom date picker on "Custom" click, refresh button calls router.refresh(). Mock `useRouter` and `useSearchParams`. |

### Key Files

**Slice 77A — Create:**
- `frontend/components/dashboard/kpi-card.tsx`
- `frontend/components/dashboard/health-badge.tsx`
- `frontend/components/dashboard/mini-progress-ring.tsx`
- `frontend/components/dashboard/sparkline-chart.tsx`
- `frontend/__tests__/dashboard/kpi-card.test.tsx`
- `frontend/__tests__/dashboard/health-badge.test.tsx`
- `frontend/__tests__/dashboard/mini-progress-ring.test.tsx`
- `frontend/__tests__/dashboard/sparkline-chart.test.tsx`

**Slice 77A — Read for context:**
- `frontend/components/ui/card.tsx` — Card styling conventions
- `frontend/components/ui/badge.tsx` — Badge variant pattern
- `frontend/components/ui/tooltip.tsx` — Tooltip usage
- `frontend/app/globals.css` — Olive color tokens

**Slice 77B — Create:**
- `frontend/components/dashboard/horizontal-bar-chart.tsx`
- `frontend/components/dashboard/date-range-selector.tsx`
- `frontend/__tests__/dashboard/horizontal-bar-chart.test.tsx`
- `frontend/__tests__/dashboard/date-range-selector.test.tsx`

**Slice 77B — Modify:**
- `frontend/package.json` — Add `recharts` dependency

**Slice 77B — Read for context:**
- `frontend/components/ui/popover.tsx` — Date picker integration pattern
- `frontend/components/ui/button.tsx` — Button variant styling

### Architecture Decisions

- **`components/dashboard/` directory**: New component directory for all dashboard visualization components. Shared across company dashboard, project overview, and personal dashboard.
- **Two-slice split**: 77A contains pure SVG components (zero dependencies, simple). 77B contains the Recharts-dependent bar chart and the more complex DateRangeSelector (URL sync, presets, date picker). This isolates the external dependency addition to one slice.
- **SparklineChart is pure SVG, not Recharts**: Per ADR-046, a sparkline is a single SVG polyline (~15 lines of code). Using Recharts would add API surface and runtime overhead without value.
- **MiniProgressRing is pure SVG**: A circular progress indicator uses `stroke-dasharray/stroke-dashoffset` — standard SVG, no library needed.
- **HorizontalBarChart uses Recharts**: Stacked bar charts with tooltips, legends, and responsive sizing justify a library. Per ADR-046, implementing this from scratch would be 300+ lines with significant edge case handling.
- **Recharts via `pnpm add recharts`**: Tree-shakeable, only loaded on dashboard pages via Next.js code splitting.

---

## Epic 78: Company Dashboard Frontend

**Goal**: Replace the existing basic dashboard page with the full company dashboard. This includes the KPI card row, project health widget with filter tabs, team workload chart, recent activity widget, date range URL parameter handling, responsive layout, permission-based KPI visibility, and empty states for all widgets.

**References**: Architecture doc Section 9.6 (company dashboard frontend), 9.6.7 (server component strategy).

**Dependencies**: Epic 76 (backend company dashboard endpoints), Epic 77 (shared dashboard components)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **78A** | 78.1-78.7 | Dashboard page replacement, KpiCardRow, ProjectHealthWidget, dashboard fetch functions, date range URL sync | |
| **78B** | 78.8-78.13 | TeamWorkloadWidget, RecentActivityWidget, DashboardLayout, responsive design, empty states, tests (~5 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 78.1 | Create dashboard fetch functions | 78A | | `frontend/lib/actions/dashboard.ts`. Server-side fetch functions: `fetchDashboardKpis(from, to)`, `fetchProjectHealth()`, `fetchTeamWorkload(from, to)`, `fetchDashboardActivity(limit)`, `fetchPersonalDashboard(from, to)`. Each calls the corresponding backend endpoint via `api.ts` with error handling (try/catch, return null on failure). Pattern: follow `frontend/lib/actions/activity.ts` for server action fetch pattern. |
| 78.2 | Create date range utility functions | 78A | | Add to `frontend/lib/actions/dashboard.ts` or `frontend/lib/date-utils.ts`: `resolveDateRange(searchParams)` — extracts `from` and `to` from URL search params, defaults to "This Month" if absent. Returns `{ from: string, to: string }` in YYYY-MM-DD format. Used by company dashboard and personal dashboard pages. |
| 78.3 | Create KpiCardRow component | 78A | | `frontend/components/dashboard/kpi-card-row.tsx`. Accepts `kpis: KpiResponse` and `isAdmin: boolean`. Renders 5 KpiCard components in a flex row: Active Projects, Hours Logged, Billable % (admin only), Overdue Tasks, Avg. Margin (admin only). When admin-only cards are hidden, remaining 3 expand. Each card has appropriate link, changeDirection logic, and sparkline from trend array. See Section 9.6.2 for card specifications. |
| 78.4 | Create ProjectHealthWidget component | 78A | | `frontend/components/dashboard/project-health-widget.tsx`. "use client". Accepts `projects: ProjectHealth[]`. Filter tabs: "All" / "At Risk" / "Critical" (client-side filtering, no API call). Max 10 visible rows. Each row: HealthBadge (sm), project name (bold), customer name (muted), completion progress bar (percent label inside), health reasons (small muted text). Row click: navigates to project detail. Footer: "View all projects" link. Empty state: "No projects yet..." Pattern: follow `frontend/components/projects/` for project list row styling. |
| 78.5 | Create progress bar sub-component for ProjectHealthWidget | 78A | | `frontend/components/dashboard/completion-progress-bar.tsx`. Simple div-based progress bar with percentage label. Colors adapt: green if >66%, amber if >33%, red if <=33%. Used inside ProjectHealthWidget rows. Could be inlined in ProjectHealthWidget if simple enough. |
| 78.6 | Replace dashboard page.tsx | 78A | | Replace `frontend/app/(app)/org/[slug]/dashboard/page.tsx` with the company dashboard. Server component. Extract date range from searchParams via `resolveDateRange()`. Fetch all 4 endpoints in parallel via `Promise.all([fetchDashboardKpis(from, to), fetchProjectHealth(), fetchTeamWorkload(from, to), fetchDashboardActivity(10)])`. Pass data to child components. Determine isAdmin from Clerk role. Individual try/catch per fetch for graceful degradation. See Section 9.6.7. Pattern: follow existing `dashboard/page.tsx` for page structure (async function, params). |
| 78.7 | Integrate DateRangeSelector into dashboard page | 78A | | Add `DateRangeSelector` to the page header area. URL params control the date range. Default: "This Month". Changing the range triggers page navigation with updated search params (server re-render). The DateRangeSelector is a "use client" component embedded in the server page. Pattern: standard client component in server component pattern. |
| 78.8 | Create TeamWorkloadWidget component | 78B | | `frontend/components/dashboard/team-workload-widget.tsx`. "use client". Accepts `data: TeamWorkloadEntry[]` and `isAdmin: boolean`. If admin: renders HorizontalBarChart with stacked bars (member bars, project segments). Legend below chart. If member: single bar for self with muted note "Contact an admin to see team-wide data." Empty state: "No time logged this period." See Section 9.6.4. |
| 78.9 | Create RecentActivityWidget component | 78B | | `frontend/components/dashboard/recent-activity-widget.tsx`. Accepts `items: CrossProjectActivityItem[]`. Renders activity list reusing the existing activity item rendering pattern from Phase 6.5 (icon/avatar, description, project name badge, relative timestamp). Footer: "View all activity" link. Empty state: "No recent activity across your projects." Pattern: follow `frontend/components/activity/` for activity item rendering. See Section 9.6.5. |
| 78.10 | Create DashboardLayout component | 78B | | `frontend/components/dashboard/dashboard-layout.tsx`. Wrapper component for the dashboard page. Implements the CSS grid layout: `grid-cols-1 lg:grid-cols-5` with project health spanning 3 columns and right sidebar spanning 2 columns. Responsive breakpoints: mobile (<768px) stacks vertically, tablet (768-1023px) single column, desktop (>=1024px) two-column grid. See Section 9.6.1. Pattern: Tailwind CSS grid, no JavaScript layout library. |
| 78.11 | Add error states for failed widget loads | 78B | | Each widget should handle a null data prop gracefully with an error state message: "Unable to load {widget name}. Please try again." This is achieved via the individual try/catch in fetch functions (78.1) returning null, and each widget checking for null props. |
| 78.12 | Add company dashboard frontend tests | 78B | | `frontend/__tests__/dashboard/company-dashboard.test.tsx` (~5 tests): (1) renders KPI cards with data, (2) hides financial KPIs for non-admin, (3) project health filter tabs work (client-side), (4) empty state renders when no projects, (5) activity widget renders items. Mock fetch functions. Pattern: follow `frontend/__tests__/` conventions. |
| 78.13 | Verify responsive layout at breakpoints | 78B | | Manual verification task — no automated test. Ensure the layout renders correctly at mobile, tablet, and desktop breakpoints. KPIs: horizontal scroll strip on mobile, 2x3 grid on tablet, single row on desktop. Main content: stacked on mobile/tablet, two-column on desktop. |

### Key Files

**Slice 78A — Create:**
- `frontend/lib/actions/dashboard.ts`
- `frontend/components/dashboard/kpi-card-row.tsx`
- `frontend/components/dashboard/project-health-widget.tsx`
- `frontend/components/dashboard/completion-progress-bar.tsx`

**Slice 78A — Modify:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — Full replacement with company dashboard

**Slice 78A — Read for context:**
- `frontend/lib/actions/activity.ts` — Server action fetch pattern
- `frontend/lib/api.ts` — API client usage
- `frontend/components/activity/` — Activity item rendering pattern
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — Current dashboard (to understand what is being replaced)

**Slice 78B — Create:**
- `frontend/components/dashboard/team-workload-widget.tsx`
- `frontend/components/dashboard/recent-activity-widget.tsx`
- `frontend/components/dashboard/dashboard-layout.tsx`
- `frontend/__tests__/dashboard/company-dashboard.test.tsx`

**Slice 78B — Read for context:**
- `frontend/components/activity/` — Activity item rendering for RecentActivityWidget
- `frontend/components/dashboard/horizontal-bar-chart.tsx` — From Epic 77B
- `frontend/app/globals.css` — Responsive breakpoints, olive tokens

### Architecture Decisions

- **Full page replacement for dashboard**: The existing `dashboard/page.tsx` has hardcoded placeholder data. It is replaced entirely, not incrementally enhanced. The new page is a server component with parallel data fetching.
- **`Promise.all` not `Promise.allSettled`**: The company dashboard calls only Phase 9 endpoints that are guaranteed to exist. Contrast with the Project Overview tab (Section 9.7.6) which uses `Promise.allSettled` because it calls Phase 8 endpoints that may not exist yet.
- **Two-slice split**: 78A builds the page skeleton + KPI cards + project health (the primary content). 78B adds the secondary widgets (workload, activity), layout container, and tests. This keeps each slice to ~6-8 files.
- **Client-side filter tabs**: Project health filter (All/At Risk/Critical) is client-side state. No additional API calls. The full list is fetched once and filtered in the browser.
- **DateRangeSelector as client component in server page**: Standard Next.js pattern. The selector is "use client", controls URL params; the server page reads those params on each render.

---

## Epic 79: Project Overview Tab

**Goal**: Add the Project Overview tab as the first/default tab in the project detail page, with a backend endpoint for project member-hours, and a frontend tab displaying health badge, metrics strip, tasks mini-list, team hours chart, and recent activity. This is split into separate backend and frontend slices.

**References**: Architecture doc Sections 9.5.2 (member-hours endpoint), 9.7 (project overview tab), 9.5.3 (personal dashboard endpoint).

**Dependencies**: Epic 75 (project health + task summary endpoints), Epic 77 (shared components)

**Scope**: Both (Backend + Frontend — split into separate slices)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **79A** | 79.1-79.6 | Backend: project member-hours endpoint, personal dashboard endpoint, PersonalDashboard DTOs, TaskRepository additions, integration tests (~7 tests) | |
| **79B** | 79.7-79.13 | Frontend: OverviewTab server component, project detail tab integration, metrics strip, tasks mini-list, Promise.allSettled pattern, tests (~3 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 79.1 | Create member-hours DTO records | 79A | | `dashboard/dto/MemberHoursEntry.java` — record with `UUID memberId, String memberName, double totalHours, double billableHours`. |
| 79.2 | Implement getProjectMemberHours() in DashboardService | 79A | | Add `getProjectMemberHours(UUID projectId, String tenantId, LocalDate from, LocalDate to)` to `DashboardService`. Native SQL query joining `time_entries`, `tasks` (scope by project), `members` (for names). Returns `List<MemberHoursEntry>` sorted by totalHours descending. Cache key: `"{tenantId}:project:{projectId}:member-hours:{from}_{to}"`. Uses project-level cache (1-min TTL). See Section 9.11.1 query similar to #4 but scoped to one project. |
| 79.3 | Add member-hours endpoint to DashboardController | 79A | | Add `GET /api/projects/{projectId}/member-hours?from=&to=` to `DashboardController`. Requires `ProjectAccessService.requireViewAccess()`. Returns `List<MemberHoursEntry>`. Extract tenantId from RequestScopes. |
| 79.4 | Create PersonalDashboard DTO records | 79A | | `dashboard/dto/PersonalDashboard.java` — record with `UtilizationSummary utilization, List<ProjectBreakdownEntry> projectBreakdown, int overdueTaskCount, List<UpcomingDeadline> upcomingDeadlines, List<TrendPoint> trend`. `dashboard/dto/UtilizationSummary.java` — record with `double totalHours, double billableHours, Double billablePercent`. `dashboard/dto/ProjectBreakdownEntry.java` — record. `dashboard/dto/UpcomingDeadline.java` — record with `UUID taskId, String taskName, String projectName, LocalDate dueDate`. |
| 79.5 | Implement getPersonalDashboard() in DashboardService | 79A | | Add `getPersonalDashboard(UUID memberId, String tenantId, LocalDate from, LocalDate to)`. Self-scoped: `WHERE member_id = :memberId` is its own authorization per ADR-023. Computes: utilization (total + billable hours for period), project breakdown (hours per project, top 5 + Other, with percent), overdue task count, upcoming deadlines (next 5 tasks by due date). Reuses `computeTrend()` from 76A scoped to member. Add `findUpcomingByAssignee(UUID memberId, int limit)` to `TaskRepository` (JPQL: tasks with dueDate >= today AND status != 'DONE' AND assigneeId = memberId, ORDER BY dueDate ASC, LIMIT :limit). Cache key: `"{tenantId}:personal:{memberId}:{from}_{to}"`. Uses project-level cache (1-min TTL). |
| 79.6 | Add personal dashboard and member-hours integration tests | 79A | | `dashboard/DashboardPersonalIntegrationTest.java` (~7 tests): (1) personal dashboard returns utilization for member, (2) personal dashboard returns project breakdown, (3) personal dashboard returns overdue task count, (4) personal dashboard returns upcoming deadlines sorted by date, (5) personal dashboard returns trend, (6) member-hours returns correct breakdown for project, (7) member-hours requires project view access. Seed: provision tenant, sync 2 members, create project + tasks + time entries assigned to specific member. Pattern: follow existing integration test patterns. |
| 79.7 | Create OverviewTab server component | 79B | | `frontend/components/projects/overview-tab.tsx`. Server component. Fetches 6 endpoints in parallel via `Promise.allSettled`: health, task summary, member hours (from Phase 9), budget status (Phase 8 — may 404), profitability (Phase 8 — may 404), activity (Phase 6.5). Each settled promise: fulfilled = data, rejected = null. Components render with null-safe props. See Section 9.7.6 code pattern. |
| 79.8 | Create overview tab health and actions section | 79B | | Within `overview-tab.tsx` or `frontend/components/projects/overview-health-header.tsx`. Top row: large HealthBadge (lg), project name, customer name, health reasons as pill badges (colors match severity). Quick action buttons: "Log Time" and "Add Task" (visible if `canEdit`). |
| 79.9 | Create overview tab metrics strip | 79B | | Within `overview-tab.tsx` or `frontend/components/projects/overview-metrics-strip.tsx`. Four compact KpiCards (no sparkline, smaller): Tasks (done/total with MiniProgressRing), Hours (sum from member-hours), Budget (from Phase 8 — empty state "No budget" if null), Margin (from Phase 8 — admin only, empty state if null). See Section 9.7.4. |
| 79.10 | Create overview tab body (tasks mini-list + team hours + activity) | 79B | | Two-column layout. Left: tasks mini-list with overdue section (red border, "X days overdue" badge) and upcoming section (next 5 by due date). Footer: "View all tasks" link to Tasks tab. Right top: team hours mini HorizontalBarChart. Right bottom: last 5 activity items. Footer links to respective tabs. See Section 9.7.5. Data from task summary, member hours, and activity fetches. |
| 79.11 | Integrate Overview as first/default tab in project detail page | 79B | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — add Overview as the first tab in the tab list. Make it the default tab (selected on initial load). Update tab order: Overview, Tasks, Documents, Time, Activity. Pass projectId and access context to OverviewTab. May need to adjust how the existing tabs component works to support a server component tab. See Section 9.7.1. |
| 79.12 | Add project overview frontend tests | 79B | | `frontend/__tests__/dashboard/project-overview.test.tsx` (~3 tests): (1) renders health badge with correct status, (2) renders metrics strip with task counts, (3) handles Phase 8 endpoint failures gracefully (budget/profitability null). Mock fetch functions. |
| 79.13 | Handle Phase 8 endpoint graceful degradation | 79B | | Ensure budget and profitability widgets show appropriate empty states when Phase 8 endpoints return 404 or are unavailable: Budget shows "No budget" with link placeholder, Margin shows "—" for all users. `Promise.allSettled` handles the rejection without crashing the tab. |

### Key Files

**Slice 79A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/MemberHoursEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/PersonalDashboard.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/UtilizationSummary.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/ProjectBreakdownEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/UpcomingDeadline.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardPersonalIntegrationTest.java`

**Slice 79A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardService.java` — Add member-hours + personal dashboard methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardController.java` — Add member-hours + personal endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskRepository.java` — Add `findUpcomingByAssignee()` query

**Slice 79A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mywork/MyWorkService.java` — Self-scoped query pattern (ADR-023)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — Native SQL aggregation pattern

**Slice 79B — Create:**
- `frontend/components/projects/overview-tab.tsx`
- `frontend/components/projects/overview-health-header.tsx`
- `frontend/components/projects/overview-metrics-strip.tsx`
- `frontend/__tests__/dashboard/project-overview.test.tsx`

**Slice 79B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add Overview as first/default tab

**Slice 79B — Read for context:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Current tab structure
- `frontend/components/projects/` — Existing project components
- `frontend/components/dashboard/` — Shared dashboard components from Epic 77
- `frontend/lib/actions/dashboard.ts` — Dashboard fetch functions from Epic 78A
- `frontend/components/activity/` — Activity item rendering pattern

### Architecture Decisions

- **Backend/frontend split**: Slice 79A (backend) creates the member-hours endpoint and personal dashboard endpoint. Slice 79B (frontend) builds the Overview tab. This follows the constraint that backend and frontend cannot be in the same slice.
- **Personal dashboard endpoint in 79A (not 80)**: The `GET /api/dashboard/personal` endpoint is created in 79A because it is a backend slice. Epic 80 (personal dashboard frontend) consumes this endpoint.
- **`Promise.allSettled` for project overview**: The overview tab calls Phase 8 endpoints (budget status, profitability) that may not exist yet. `Promise.allSettled` ensures the tab renders even when these endpoints 404. Contrast with the company dashboard which uses `Promise.all` because it only calls Phase 9 endpoints.
- **Overview as first tab**: The project detail page currently defaults to the Tasks tab. Phase 9 adds Overview as the first tab and makes it the default. This is a UX improvement — users see a summary before drilling into details.
- **Tasks mini-list data**: The overview tab's task mini-list does not require a new endpoint. It uses the existing `GET /api/projects/{id}/tasks` endpoint with appropriate parameters (or derives from the task summary + a limited task fetch).

---

## Epic 80: Personal Dashboard

**Goal**: Enhance the existing My Work page with a dashboard header section including personal KPIs (hours, billable %, overdue tasks), a time breakdown chart by project, an upcoming deadlines list, and date range selector integration. The existing task and time entry lists are preserved below the new dashboard header. The task list is enhanced with urgency indicators and grouping.

**References**: Architecture doc Section 9.8 (personal dashboard), 9.8.2-9.8.7 (components and behavior).

**Dependencies**: Epic 79A (personal dashboard backend endpoint), Epic 77 (shared components)

**Scope**: Frontend (backend endpoint created in Epic 79A)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **80A** | 80.1-80.6 | PersonalKpis component, TimeBreakdown chart, UpcomingDeadlines list, personal dashboard fetch function, date range integration | |
| **80B** | 80.7-80.12 | My Work page enhancement with dashboard header, enhanced task list with urgency grouping, date range selector, tests (~5 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 80.1 | Create PersonalKpis component | 80A | | `frontend/components/my-work/personal-kpis.tsx`. Three KpiCard components: (1) "Hours {period}" with total hours, sparkline from trend, (2) "Billable %" with percent (or "—" empty state if no billable data), (3) "Overdue Tasks" with count (red if > 0). Label adapts to date range: "Hours This Week" for weekly, "Hours This Month" for monthly. See Section 9.8.3. |
| 80.2 | Create TimeBreakdown component | 80A | | `frontend/components/my-work/time-breakdown.tsx`. "use client". Uses HorizontalBarChart from Epic 77. Single-segment bars (no stacking — personal view). Shows top 5 projects + "Other" aggregate from `projectBreakdown` data. Color-coded with consistent project colors. Label + hours + percent for each bar. See Section 9.8.4. |
| 80.3 | Create UpcomingDeadlines component | 80A | | `frontend/components/my-work/upcoming-deadlines.tsx`. Simple list of next 5 deadlines. Columns: task name (linked), project name (badge/pill), due date (formatted), days remaining ("5 days", "2 days" in amber if <=2, "Overdue" in red if past). Data from `upcomingDeadlines` field of PersonalDashboard response. See Section 9.8.6. |
| 80.4 | Create personal dashboard data fetching | 80A | | Add `fetchPersonalDashboard(from, to)` to `frontend/lib/actions/dashboard.ts` (may already exist from 78.1 — verify and add if not). Calls `GET /api/dashboard/personal?from=&to=`. Returns `PersonalDashboard` type or null on error. |
| 80.5 | Define PersonalDashboard TypeScript types | 80A | | Add TypeScript interfaces in `frontend/lib/actions/dashboard.ts` (or a separate `frontend/types/dashboard.ts`): `PersonalDashboard`, `UtilizationSummary`, `ProjectBreakdownEntry`, `UpcomingDeadline`, `TrendPoint`. Match the backend DTO shapes from 79A. |
| 80.6 | Add tests for PersonalKpis, TimeBreakdown, UpcomingDeadlines | 80A | | `frontend/__tests__/dashboard/personal-kpis.test.tsx` (~2 tests): renders hours and overdue count, hides billable when null. `frontend/__tests__/dashboard/time-breakdown.test.tsx` (~1 test): renders bars for project breakdown. `frontend/__tests__/dashboard/upcoming-deadlines.test.tsx` (~2 tests): renders deadline list, shows "Overdue" label for past-due items. |
| 80.7 | Enhance My Work page with dashboard header | 80B | | Modify `frontend/app/(app)/org/[slug]/my-work/page.tsx`. Add date range extraction from searchParams (same pattern as company dashboard). Fetch personal dashboard data via `fetchPersonalDashboard()`. Add dashboard header section above existing content: PersonalKpis row, TimeBreakdown chart, UpcomingDeadlines list. Existing task list and time entry list remain below. See Section 9.8.1. |
| 80.8 | Integrate DateRangeSelector into My Work page | 80B | | Add DateRangeSelector to the My Work page header. Default: "This Week" (not "This Month" like company dashboard). URL sync via search params. Changing the range re-fetches personal dashboard data (KPIs, time breakdown, trend). Task list is NOT affected by date range — always shows current assignments. See Section 9.8.7. |
| 80.9 | Create enhanced task list with urgency grouping | 80B | | `frontend/components/my-work/urgency-task-list.tsx`. "use client". Enhances the existing task list display with urgency indicators and grouping. Four sections: (1) "Overdue" (red header, red left border, "X days overdue" badge, sorted by overdue days desc), (2) "Due This Week" (amber header if tasks due within 2 days), (3) "Upcoming" (tasks beyond this week), (4) "No Due Date". Grouping computed client-side from existing task response data. See Section 9.8.5. Pattern: follow `frontend/components/my-work/` existing components. |
| 80.10 | Integrate urgency task list into My Work page | 80B | | Modify `frontend/app/(app)/org/[slug]/my-work/page.tsx` — replace or wrap the existing assigned task list rendering with the new `UrgencyTaskList` component. Pass the same task data but let the component handle grouping. |
| 80.11 | Add My Work page integration tests | 80B | | `frontend/__tests__/dashboard/my-work-dashboard.test.tsx` (~3 tests): (1) renders personal KPIs when data available, (2) renders time breakdown chart, (3) urgency grouping renders overdue tasks in separate section. Mock fetch functions and task data. |
| 80.12 | Verify date range selector does not affect task list | 80B | | Ensure the task list always shows current assignments regardless of the date range selected. Only KPIs, time breakdown, and trend are affected by the date range. Manual verification + test assertion that task list rendering does not depend on date range props. |

### Key Files

**Slice 80A — Create:**
- `frontend/components/my-work/personal-kpis.tsx`
- `frontend/components/my-work/time-breakdown.tsx`
- `frontend/components/my-work/upcoming-deadlines.tsx`
- `frontend/__tests__/dashboard/personal-kpis.test.tsx`
- `frontend/__tests__/dashboard/time-breakdown.test.tsx`
- `frontend/__tests__/dashboard/upcoming-deadlines.test.tsx`

**Slice 80A — Modify:**
- `frontend/lib/actions/dashboard.ts` — Add `fetchPersonalDashboard()` if not present, add TypeScript types

**Slice 80A — Read for context:**
- `frontend/components/dashboard/kpi-card.tsx` — KpiCard component from Epic 77
- `frontend/components/dashboard/horizontal-bar-chart.tsx` — HorizontalBarChart from Epic 77
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — Current My Work page structure

**Slice 80B — Create:**
- `frontend/components/my-work/urgency-task-list.tsx`
- `frontend/__tests__/dashboard/my-work-dashboard.test.tsx`

**Slice 80B — Modify:**
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — Add dashboard header, date range, integrate urgency task list

**Slice 80B — Read for context:**
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — Current structure (preserved below new header)
- `frontend/components/my-work/` — Existing My Work components
- `frontend/components/dashboard/date-range-selector.tsx` — DateRangeSelector from Epic 77
- `frontend/app/(app)/org/[slug]/my-work/actions.ts` — Existing My Work actions

### Architecture Decisions

- **Enhancement, not replacement**: The My Work page is enhanced with a dashboard header above existing content. The existing task list and time entry list are preserved. This avoids breaking existing functionality.
- **Backend endpoint in Epic 79A**: The `GET /api/dashboard/personal` endpoint is created in the backend slice of Epic 79 (79A), not in Epic 80. Epic 80 is frontend-only and consumes the endpoint.
- **Date range default "This Week"**: Different from company dashboard default ("This Month"). Personal dashboard focuses on the current work period, while company dashboard shows a broader view.
- **Urgency grouping is client-side**: The task grouping (Overdue, Due This Week, Upcoming, No Due Date) is computed from existing task response data in the browser. No new backend endpoint needed.
- **Two-slice split**: 80A creates the reusable components (KPIs, breakdown, deadlines). 80B integrates them into the My Work page and adds the urgency task list. This keeps component creation separate from page integration.

---

## Test Summary

| Epic | Slice | Backend Tests | Frontend Tests | Total |
|------|-------|---------------|----------------|-------|
| 75 | 75A | 15 (unit) | — | 15 |
| 75 | 75B | 8 (integration) | — | 8 |
| 76 | 76A | — | — | — |
| 76 | 76B | 12 (integration) | — | 12 |
| 77 | 77A | — | 12 (component) | 12 |
| 77 | 77B | — | 7 (component) | 7 |
| 78 | 78A | — | — | — |
| 78 | 78B | — | 5 (integration) | 5 |
| 79 | 79A | 7 (integration) | — | 7 |
| 79 | 79B | — | 3 (component) | 3 |
| 80 | 80A | — | 5 (component) | 5 |
| 80 | 80B | — | 3 (integration) | 3 |
| **Total** | | **42** | **35** | **77** |
