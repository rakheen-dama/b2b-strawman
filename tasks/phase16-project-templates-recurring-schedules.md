# Phase 16 — Project Templates & Recurring Schedules

Phase 16 adds a **reusable project blueprint system** with optional automated scheduling to the DocTeams platform. Professional services firms perform the same types of engagements repeatedly — monthly bookkeeping, quarterly tax reviews, annual audits — each following a predictable structure of tasks, assignments, and deliverables. This phase eliminates that repetitive work by introducing project templates (blueprints that capture project structure) and recurring schedules (automated project creation on a cadence).

Four new entities (`ProjectTemplate`, `TemplateTask`, `RecurringSchedule`, `ScheduleExecution`), one join table (`template_tags`), a daily scheduled job, and frontend pages for template management, schedule configuration, and execution monitoring.

**Architecture doc**: `architecture/phase16-project-templates-recurring-schedules.md`

**ADRs**:
- [ADR-068](../adr/ADR-068-snapshot-based-templates.md) — Snapshot-Based Templates
- [ADR-069](../adr/ADR-069-role-based-assignment-hints.md) — Role-Based Assignment Hints
- [ADR-070](../adr/ADR-070-pre-calculated-next-execution-date.md) — Pre-Calculated next_execution_date
- [ADR-071](../adr/ADR-071-daily-batch-scheduler.md) — Daily Batch Scheduler

**MIGRATION**: `V30__project_templates_recurring_schedules.sql` — 4 tables + 1 join table.

**Dependencies on prior phases**: Phase 4 (Project, Task, Customer), Phase 6 (AuditService), Phase 6.5 (Notifications, ApplicationEvent), Phase 11 (Tag, EntityTag), Phase 13 (dedicated schema — no tenant_id), Phase 14 (Customer.lifecycleStatus).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 115 | Entity Foundation & Utilities | Backend | -- | M | 115A, 115B | |
| 116 | Template CRUD & Save from Project | Backend | 115 | M | 116A, 116B | |
| 117 | Template Instantiation | Backend | 116 | S | 117A | |
| 118 | Schedule CRUD & Lifecycle | Backend | 115 | M | 118A, 118B | |
| 119 | Scheduler Execution Engine | Backend | 117, 118 | M | 119A | |
| 120 | Template Management UI | Frontend | 116, 117 | M | 120A, 120B | |
| 121 | Schedule Management UI | Frontend | 118, 119 | M | 121A, 121B | |

---

## Dependency Graph

```
[E115A Migration + Entities + Repos]
            |
[E115B PeriodCalculator + NameTokenResolver + Unit Tests]
            |
     +------+------+
     |             |
     v             v
[E116A Template   [E118A Schedule
 Service + DTOs]   Service + DTOs]
     |             |
[E116B Template   [E118B Schedule
 Controller +      Controller +
 Tests]            Tests]
     |             |
[E117A Template    |
 Instantiation]    |
     |             |
     |      +------+
     |      |
     v      v
[E119A Scheduler Execution Engine]
     |      |
     v      v
[E120A Template   [E121A Schedule
 List + Editor]    List + Create]
     |             |
[E120B Save As +  [E121B Schedule
 New From]         Detail + History]
```

**Parallel opportunities**:
- Epics 116 and 118 are independent backend tracks after Epic 115 — can run in parallel.
- After Epic 119 completes: Epics 120 and 121 are independent frontend tracks — can run in parallel.
- Within each frontend epic: slices A and B are sequential.

---

## Implementation Order

### Stage 1: Foundation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 115 | 115A | V30 migration, 4 JPA entities, 5 repositories, `TemplateTagRepository` (JdbcClient). Foundation for everything else. |
| 1b | Epic 115 | 115B | `PeriodCalculator`, `NameTokenResolver` utility classes + ~22 unit tests. Pure logic, no dependencies on services. |

### Stage 2: Backend Domain Logic (Parallel tracks)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 116 | 116A | `ProjectTemplateService` — CRUD, saveFromProject, duplicate. DTOs. ~12 service tests. |
| 2b | Epic 116 | 116B | `ProjectTemplateController` — 7 REST endpoints (all except instantiate). Permission checks. SecurityConfig. ~15 controller integration tests. |
| 2c | Epic 118 | 118A | `RecurringScheduleService` — CRUD, pause, resume, lifecycle. DTOs. ~12 service tests. Parallel with 116. |
| 2d | Epic 118 | 118B | `RecurringScheduleController` — 8 REST endpoints. Permission checks. ~15 controller integration tests. Parallel with 116B. |

### Stage 3: Instantiation & Execution (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 117 | 117A | `instantiateTemplate()` in `ProjectTemplateService`. POST endpoint. Assignee resolution, tag application. Audit + notification events. ~15 integration tests. |
| 3b | Epic 119 | 119A | `RecurringScheduleExecutor` (@Scheduled cron), `processSchedulesForTenant()`, `executeSingleSchedule()`. Tenant iteration via `OrgSchemaMappingRepository`. Idempotency, lifecycle check, error isolation. Notification events. ~18 integration tests. |

### Stage 4: Frontend (Parallel tracks)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 120 | 120A | Template list page, template editor page, API client. Settings nav update. ~14 tests. |
| 4b | Epic 120 | 120B | SaveAsTemplateDialog, NewFromTemplateDialog, TemplatePicker. Project page integrations. ~16 tests. |
| 4c | Epic 121 | 121A | Schedule list page, create dialog, API client. Sidebar nav update. ~15 tests. Parallel with 120. |
| 4d | Epic 121 | 121B | Schedule detail page, edit dialog, execution history, pause/resume/delete actions. ~12 tests. |

### Timeline

```
Stage 1:  [115A] --> [115B]
Stage 2:  [116A] --> [116B]  //  [118A] --> [118B]   (parallel backend tracks)
Stage 3:  [117A] --> [119A]                           (sequential after 116B + 118B)
Stage 4:  [120A] --> [120B]  //  [121A] --> [121B]   (parallel frontend tracks after 119A)
```

**Critical path**: 115A -> 115B -> 116A -> 116B -> 117A -> 119A -> 120A -> 120B
**Parallel savings**: Stages 2 and 4 each have two parallel tracks.

---

## Epic 115: Entity Foundation & Utilities

**Goal**: Create the V30 database migration, all 4 JPA entities, 5 repositories, and the 2 utility classes (`PeriodCalculator`, `NameTokenResolver`) with comprehensive unit tests. This epic lays the data foundation — no service logic, no controllers.

**References**: Architecture doc Sections 2 (Domain Model), 7 (Migration), 8.2 (Entity Pattern), 8.3 (Repository Pattern). [ADR-068](../adr/ADR-068-snapshot-based-templates.md), [ADR-069](../adr/ADR-069-role-based-assignment-hints.md), [ADR-070](../adr/ADR-070-pre-calculated-next-execution-date.md).

**Dependencies**: None (foundation epic).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **115A** | 115.1–115.9 | V30 migration (4 tables + 1 join table), `ProjectTemplate` entity, `TemplateTask` entity, `RecurringSchedule` entity, `ScheduleExecution` entity, `ProjectTemplateRepository`, `TemplateTaskRepository`, `TemplateTagRepository` (JdbcClient), `RecurringScheduleRepository`, `ScheduleExecutionRepository`. Migration verification test + TemplateTagRepository integration test. ~10 tests. | **Done** (PR #235) |
| **115B** | 115.10–115.16 | `PeriodCalculator` utility (all 6 frequencies, period start/end, next execution date). `NameTokenResolver` utility (6 tokens: customer, month, month_short, year, period_start, period_end). Comprehensive unit tests for both. ~22 unit tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 115.1 | Create V30 migration | 115A | | `backend/src/main/resources/db/migration/tenant/V30__project_templates_recurring_schedules.sql`. Exact SQL from architecture doc Section 7. 4 tables: `project_templates`, `template_tasks`, `template_tags`, `recurring_schedules`, `schedule_executions`. 10 indexes. 3 constraints (unique, check). Copy SQL verbatim from architecture doc. |
| 115.2 | Create `ProjectTemplate` entity | 115A | 115.1 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplate.java`. Follow `Project` entity pattern. Fields: id, name, namePattern, description, billableDefault, source, sourceProjectId, active, createdBy, createdAt, updatedAt. Constructor, `update()`, `activate()`, `deactivate()` methods. `@Entity @Table(name = "project_templates")`. Pattern: `Project.java` in `project/` package. |
| 115.3 | Create `TemplateTask` entity | 115A | 115.1 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/TemplateTask.java`. Fields: id, templateId (UUID FK, not @ManyToOne), name, description, estimatedHours (BigDecimal), sortOrder, billable, assigneeRole (String, default "UNASSIGNED"), createdAt, updatedAt. Note: `TemplateTask.name` maps to `Task.title` during instantiation — document this in a comment. Pattern: follow `FieldDefinition.java` for entity with parent FK. |
| 115.4 | Create `RecurringSchedule` entity | 115A | 115.1 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringSchedule.java`. Fields per architecture doc Section 2.4. Include `recordExecution(Instant)` method that increments `executionCount` and sets `lastExecutedAt`. Include `setNextExecutionDate()`, `setStatus()` setters. Use String for status/frequency (not enum) per existing project patterns. Pattern: follow `Invoice.java` for entity with lifecycle status field. |
| 115.5 | Create `ScheduleExecution` entity | 115A | 115.1 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/ScheduleExecution.java`. Fields: id, scheduleId, projectId, periodStart, periodEnd, executedAt, createdAt. Immutable after creation — no update methods. Pattern: follow `AuditEvent.java` for immutable log entity. |
| 115.6 | Create JPA repositories | 115A | 115.2–115.5 | `ProjectTemplateRepository`, `TemplateTaskRepository` in `projecttemplate/`. `RecurringScheduleRepository`, `ScheduleExecutionRepository` in `schedule/`. Methods per architecture doc Section 8.3. Key queries: `findByActiveOrderByNameAsc()`, `findByTemplateIdOrderBySortOrder()`, `findByStatusAndNextExecutionDateLessThanEqual()`, `existsByScheduleIdAndPeriodStart()`, `existsByTemplateId()`. |
| 115.7 | Create `TemplateTagRepository` | 115A | 115.1 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/TemplateTagRepository.java`. Spring `@Repository` with `JdbcClient` (NOT JPA — join table, no entity). Methods: `save(templateId, tagId)` with `ON CONFLICT DO NOTHING`, `findTagIdsByTemplateId()`, `deleteByTemplateId()`, `deleteByTemplateIdAndTagId()`. Exact implementation in architecture doc Section 2.3. |
| 115.8 | Write V30 migration verification test | 115A | 115.1–115.7 | Test in `backend/src/test/java/.../projecttemplate/V30MigrationTest.java`. Verify migration applies cleanly. Save a `ProjectTemplate` + `TemplateTask` + tag association + `RecurringSchedule` + `ScheduleExecution`. Verify CASCADE delete on template removes tasks and tags. Verify unique constraints: `(template_id, sort_order)` on template_tasks, `(template_id, customer_id, frequency)` on recurring_schedules, `(schedule_id, period_start)` on schedule_executions. ~6 tests. |
| 115.9 | Write `TemplateTagRepository` integration test | 115A | 115.7 | Test in `backend/src/test/java/.../projecttemplate/TemplateTagRepositoryTest.java`. Tests: save tag, find tags by template, delete by template, delete specific tag, ON CONFLICT DO NOTHING on duplicate insert. ~4 tests. Requires a saved `ProjectTemplate` and `Tag` as test fixtures. |
| 115.10 | Create `PeriodCalculator` utility | 115B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/PeriodCalculator.java`. Static methods or Spring `@Component`. `Period` record with `start` and `end` (LocalDate). `calculateNextPeriod(anchor, frequency, executionCount)` — all 6 frequencies (WEEKLY, FORTNIGHTLY, MONTHLY, QUARTERLY, SEMI_ANNUALLY, ANNUALLY). `calculateNextExecutionDate(periodStart, leadTimeDays)`. Exact logic in architecture doc Section 3.5. |
| 115.11 | Create `NameTokenResolver` utility | 115B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/NameTokenResolver.java`. Static method or Spring `@Component`. `resolveNameTokens(pattern, customer, referenceDate, periodStart, periodEnd)`. 6 tokens: `{customer}`, `{month}`, `{month_short}`, `{year}`, `{period_start}`, `{period_end}`. Simple `String.replace()` — no template engine. Exact logic in architecture doc Section 3.6. |
| 115.12 | Write `PeriodCalculator` unit tests | 115B | 115.10 | `backend/src/test/java/.../projecttemplate/PeriodCalculatorTest.java`. Tests per architecture doc Section 3.5 examples table. All 6 frequencies, multiple execution counts. Edge cases: month-end (Jan 31 + 1 month = Feb 28), leap year (Feb 29), year boundary (Dec -> Jan). `calculateNextExecutionDate` with 0 and non-zero lead time. ~14 unit tests. |
| 115.13 | Write `NameTokenResolver` unit tests | 115B | 115.11 | `backend/src/test/java/.../projecttemplate/NameTokenResolverTest.java`. Tests: all 6 tokens individually, combined pattern (e.g., "Monthly Bookkeeping - {customer} - {month} {year}"), null customer (token left unreplaced), null referenceDate, null periodStart/periodEnd. ~8 unit tests. |
| 115.14 | Create `recalculateNextExecutionOnResume()` method | 115B | 115.10 | In `PeriodCalculator` or `RecurringScheduleService`. Logic from architecture doc Section 6.5 — skips past periods when resuming a paused schedule. Safety valve: max 1000 iterations. This is a pure calculation, tested as a unit test. |
| 115.15 | Write resume recalculation unit tests | 115B | 115.14 | Additional tests in `PeriodCalculatorTest`: resume with no missed periods (next date is future), resume with 3 missed periods (skips forward), resume with lead time. ~3 unit tests. |
| 115.16 | Verify all 115B tests pass in isolation | 115B | 115.12–115.15 | Run: `./mvnw test -pl backend -Dtest="PeriodCalculatorTest,NameTokenResolverTest" -q`. All ~22 unit tests must pass. These are pure unit tests — no Spring context, no database. |

### Key Files

**Slice 115A — Create:**
- `backend/src/main/resources/db/migration/tenant/V30__project_templates_recurring_schedules.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/TemplateTask.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/TemplateTaskRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/TemplateTagRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringSchedule.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/ScheduleExecution.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/ScheduleExecutionRepository.java`
- `backend/src/test/java/.../projecttemplate/V30MigrationTest.java`
- `backend/src/test/java/.../projecttemplate/TemplateTagRepositoryTest.java`

**Slice 115B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/PeriodCalculator.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/NameTokenResolver.java`
- `backend/src/test/java/.../projecttemplate/PeriodCalculatorTest.java`
- `backend/src/test/java/.../projecttemplate/NameTokenResolverTest.java`

**Read for context:**
- `architecture/phase16-project-templates-recurring-schedules.md` Sections 2, 3.5, 3.6, 7, 8.2, 8.3
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` — Entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` — Entity with lifecycle status
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java` — Immutable entity pattern
- `backend/src/main/resources/db/migration/tenant/` — Latest migration number (V29)
- `backend/CLAUDE.md` — Conventions

### Architecture Decisions

- **Package separation**: `projecttemplate/` for template entities (NOT `template/` — that's Phase 12 DocumentTemplate). `schedule/` for recurring schedule entities.
- **TemplateTagRepository uses JdbcClient**: The join table has no JPA entity. `JdbcClient` (Spring Boot 4) provides lightweight native SQL. This avoids a JPA entity for a simple composite-PK join table.
- **UUID FK columns, not @ManyToOne**: `TemplateTask.templateId` is a UUID column, not a `@ManyToOne` relationship. This matches the project's existing pattern (e.g., `Task.projectId`). Avoid lazy-loading complexity.
- **String for status/frequency**: Following existing patterns (`Invoice.status`, `Task.status`), use String columns rather than JPA `@Enumerated`. Validation happens in service layer.

---

## Epic 116: Template CRUD & Save from Project

**Goal**: Create the `ProjectTemplateService` with full CRUD operations (create, update, delete, duplicate, list, get) plus the `saveFromProject()` flow. Add the `ProjectTemplateController` REST endpoints (all except instantiate). Wire permission checks and audit events.

**References**: Architecture doc Sections 3.1 (Save from Project), 4.1 (Template Endpoints), 8.4 (Backend Changes), 10 (Audit), 11 (Permissions).

**Dependencies**: Epic 115 (entities and repositories must exist).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **116A** | 116.1–116.8 | `ProjectTemplateService` with CRUD + `saveFromProject()` + `duplicate()`. Request/response DTOs. Audit events (`template.created`, `template.updated`, `template.deleted`, `template.duplicated`). Delete guard (409 if active schedules reference template). Service-level integration tests. ~12 tests. | |
| **116B** | 116.9–116.16 | `ProjectTemplateController` with 7 REST endpoints. `SecurityConfig` update. Permission checks (admin/owner for mutations, lead for save-from-project). Controller integration tests with MockMvc. ~15 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 116.1 | Create request/response DTOs | 116A | | `projecttemplate/dto/` package. `CreateTemplateRequest` (name, namePattern, description, billableDefault, tasks list, tagIds list). `UpdateTemplateRequest` (same fields). `SaveFromProjectRequest` (name, namePattern, description, taskIds, tagIds, taskRoles map). `ProjectTemplateResponse` (all fields + taskCount + tagCount + tasks list + tags list). `TemplateTaskResponse` (id, name, description, estimatedHours, sortOrder, billable, assigneeRole). Follow existing DTO patterns — Java records with `@NotBlank`, `@Size` validation. Pattern: `invoice/dto/` package. |
| 116.2 | Create `ProjectTemplateService` — CRUD operations | 116A | 116.1 | `projecttemplate/ProjectTemplateService.java`. Methods: `create(request, memberId)`, `update(id, request)`, `delete(id)`, `duplicate(id, memberId)`, `list()`, `listActive()`, `get(id)`. Delete checks `RecurringScheduleRepository.existsByTemplateId()` — returns 409 Conflict if active schedules reference the template. Update replaces tasks (delete all + re-insert) and tags (delete all + re-insert). `@Transactional` on mutations. |
| 116.3 | Implement `saveFromProject()` | 116A | 116.2 | In `ProjectTemplateService`. Reads project tasks + tags, creates template snapshot. Permission check: `requireAdminOwnerOrProjectLead(orgRole, project, memberId)`. Logic per architecture doc Section 3.1. Key: Task ordering from `request.taskIds()` list order, not task entity order. |
| 116.4 | Implement `duplicate()` | 116A | 116.2 | Loads template + tasks + tags, creates new template with " (Copy)" suffix on name. Copies all tasks with same sort order and settings. Copies all tag associations. Sets `source = "MANUAL"` (not FROM_PROJECT). |
| 116.5 | Add audit events | 116A | 116.2–116.4 | Audit events for: `template.created`, `template.updated`, `template.deleted`, `template.duplicated`. Follow existing `AuditEventBuilder` pattern. Details maps per architecture doc Section 10. |
| 116.6 | Add notification event for template creation | 116A | 116.5 | Publish `TemplateCreatedEvent` using `ApplicationEventPublisher`. Follow `CommentService` event pattern. Recipients: org admins. |
| 116.7 | Write `ProjectTemplateService` integration tests | 116A | 116.2–116.6 | `backend/src/test/java/.../projecttemplate/ProjectTemplateServiceTest.java`. Tests: create template with tasks and tags, update template (replaces tasks), delete template (no schedules), delete template blocked by active schedule (409), duplicate template, saveFromProject with task selection, saveFromProject with role mapping, list active templates, get template with tasks and tags. ~12 tests. Use `@SpringBootTest` + Testcontainers. |
| 116.8 | Verify 116A tests pass | 116A | 116.7 | Run: `./mvnw test -pl backend -Dtest="ProjectTemplateServiceTest" -q`. All ~12 tests pass. |
| 116.9 | Create `ProjectTemplateController` | 116B | 116A | `projecttemplate/ProjectTemplateController.java`. 7 endpoints per architecture doc Section 4.1: `GET /api/project-templates`, `GET /api/project-templates/{id}`, `POST /api/project-templates`, `PUT /api/project-templates/{id}`, `DELETE /api/project-templates/{id}`, `POST /api/project-templates/{id}/duplicate`, `POST /api/project-templates/from-project/{projectId}`. Extract `memberId` from `RequestScopes.MEMBER_ID`, `orgRole` from `RequestScopes.ORG_ROLE`. Pattern: follow `InvoiceController` for CRUD controller structure. |
| 116.10 | Add permission checks | 116B | 116.9 | Admin/Owner required for: create, update, delete, duplicate. Admin/Owner/ProjectLead for: saveFromProject. All members can: list, get. Check `orgRole` in controller or service — follow existing pattern from `ProjectController`. For saveFromProject, also check project lead via `ProjectMemberRepository`. |
| 116.11 | Update `SecurityConfig` | 116B | 116.9 | Add `/api/project-templates/**` to authenticated endpoint list. All template endpoints require JWT authentication. No public access. Pattern: follow how `/api/invoices/**` is configured. |
| 116.12 | Response mapping — include tags from TagRepository | 116B | 116.9 | `ProjectTemplateResponse` includes `tags` list with id, name, color. Need to load tags by IDs from `TemplateTagRepository.findTagIdsByTemplateId()` then `TagRepository.findAllById()`. Map to a simple tag response DTO. |
| 116.13 | Write controller integration tests — CRUD | 116B | 116.9–116.12 | `backend/src/test/java/.../projecttemplate/ProjectTemplateControllerTest.java`. MockMvc tests. Tests: list templates (200), get template by ID (200), get non-existent template (404), create template with valid data (201), create template missing required fields (400), update template (200), delete template no schedules (204), delete template with active schedules (409), duplicate template (201). ~9 tests. |
| 116.14 | Write controller integration tests — permissions | 116B | 116.13 | Additional tests in same file. Tests: create template as member (403), update as member (403), delete as member (403), saveFromProject as admin (201), saveFromProject as project lead (201), saveFromProject as regular member (403). ~6 tests. Use `.with(jwt().authorities(...))` for different roles. |
| 116.15 | Write saveFromProject controller test | 116B | 116.14 | Test: POST `/api/project-templates/from-project/{projectId}` with valid project, task IDs, tag IDs, role mapping. Verify response includes task count, source = "FROM_PROJECT". Test with non-existent project (404). ~2 tests. |
| 116.16 | Verify all 116 tests pass | 116B | 116.13–116.15 | Run: `./mvnw test -pl backend -Dtest="ProjectTemplateServiceTest,ProjectTemplateControllerTest" -q`. All ~27 tests pass. |

### Key Files

**Slice 116A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/dto/CreateTemplateRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/dto/UpdateTemplateRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/dto/SaveFromProjectRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/dto/ProjectTemplateResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/dto/TemplateTaskResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/event/TemplateCreatedEvent.java`
- `backend/src/test/java/.../projecttemplate/ProjectTemplateServiceTest.java`

**Slice 116B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateController.java`
- `backend/src/test/java/.../projecttemplate/ProjectTemplateControllerTest.java`

**Slice 116B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java`

**Read for context:**
- `architecture/phase16-project-templates-recurring-schedules.md` Sections 3.1, 4.1, 10, 11
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — CRUD controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — Service with audit events
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Permission check pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` — Endpoint security config
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagRepository.java` — Tag lookup for response mapping

### Architecture Decisions

- **Update replaces tasks**: On template update, all existing tasks are deleted and re-created from the request. This is simpler than diffing and handles reordering cleanly. Same pattern as checklist template updates.
- **Delete guard with 409**: If a template has active recurring schedules, deletion returns 409 Conflict. Users must deactivate or delete schedules first, or set `active=false` on the template. This prevents orphaned schedule references.
- **saveFromProject permission**: Allows project leads to save their own project structure as a template. This is intentionally broader than other template mutations (admin/owner only) — it encourages template creation by practitioners.

---

## Epic 117: Template Instantiation

**Goal**: Add the `instantiateTemplate()` method to `ProjectTemplateService` and the corresponding POST endpoint. This creates a real `Project` with `Task` records, `EntityTag` records, and optional `CustomerProject` link from a template blueprint.

**References**: Architecture doc Sections 3.2 (Create from Template), 4.1 (instantiate endpoint), 5.1 (sequence diagram).

**Dependencies**: Epic 116 (template service must exist).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **117A** | 117.1–117.9 | `InstantiateTemplateRequest` DTO. `instantiateTemplate()` in `ProjectTemplateService`. `POST /api/project-templates/{id}/instantiate` endpoint. Assignee role resolution. Tag application via `EntityTagRepository`. Customer link via `CustomerProjectRepository`. Name token resolution. Audit event (`project.created_from_template`). Integration tests. ~15 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 117.1 | Create `InstantiateTemplateRequest` DTO | 117A | | `projecttemplate/dto/InstantiateTemplateRequest.java`. Fields: `name` (optional — overrides resolved name), `customerId` (optional), `projectLeadMemberId` (optional), `description` (optional). |
| 117.2 | Implement `instantiateTemplate()` | 117A | 117.1 | In `ProjectTemplateService`. Full logic per architecture doc Section 3.2. Steps: (1) Load template + verify active, (2) Resolve name tokens via `NameTokenResolver`, (3) Create `Project`, (4) Link to customer via `CustomerProjectRepository`, (5) Set project lead via `ProjectMemberRepository`, (6) Create tasks from template tasks — **note `TemplateTask.name` -> `Task.title`**, (7) Apply tags via `EntityTagRepository`. All in one `@Transactional`. |
| 117.3 | Implement `resolveAssignee()` | 117A | 117.2 | Helper method. `PROJECT_LEAD` -> projectLeadMemberId (from request), `ANY_MEMBER` -> null, `UNASSIGNED` -> null. If PROJECT_LEAD but no lead provided -> null. Assigned tasks stay `OPEN` status — no auto-transition to `IN_PROGRESS`. |
| 117.4 | Add instantiate endpoint | 117A | 117.2 | `POST /api/project-templates/{id}/instantiate` in `ProjectTemplateController`. Permission: all authenticated members (same as project creation). Returns 201 with created project. |
| 117.5 | Add audit event | 117A | 117.2 | `project.created_from_template` audit event. Details: `template_name`, `project_name`, `customer_name`. |
| 117.6 | Add notification event | 117A | 117.2 | Optional — publish event for project creation from template. Informational notification to org admins. |
| 117.7 | Write instantiation integration tests | 117A | 117.2–117.6 | `backend/src/test/java/.../projecttemplate/InstantiateTemplateIntegrationTest.java`. Tests: instantiate with customer and lead (verify project, tasks, tags, customer link, lead assignment), instantiate without customer, instantiate without lead (PROJECT_LEAD tasks get null assignee), instantiate with name override, instantiate inactive template (400), instantiate with token resolution (verify {customer}, {month}, {year} replaced), verify task ordering matches template sortOrder, verify EntityTag records created. ~10 integration tests. |
| 117.8 | Write controller instantiate test | 117A | 117.4, 117.7 | In `ProjectTemplateControllerTest`. Tests: POST instantiate returns 201 with project, POST instantiate non-existent template returns 404, POST instantiate inactive template returns 400, POST instantiate as member returns 201 (all members can create projects). ~4 tests. |
| 117.9 | Verify all 117A tests pass | 117A | 117.7–117.8 | Run: `./mvnw test -pl backend -Dtest="InstantiateTemplateIntegrationTest,ProjectTemplateControllerTest" -q`. All ~15 new tests pass (plus existing 116 tests). |

### Key Files

**Slice 117A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/dto/InstantiateTemplateRequest.java`
- `backend/src/test/java/.../projecttemplate/InstantiateTemplateIntegrationTest.java`

**Slice 117A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateService.java` — Add `instantiateTemplate()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateController.java` — Add instantiate endpoint

**Read for context:**
- `architecture/phase16-project-templates-recurring-schedules.md` Sections 3.2, 5.1
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java` — Constructor signature, confirm `title` field (NOT `name`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskRepository.java` — For saving tasks
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectRepository.java` — Customer-project linking
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectMemberRepository.java` — Project lead assignment
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTagRepository.java` — Tag application pattern

### Architecture Decisions

- **TemplateTask.name -> Task.title**: The template uses `name` but the `Task` entity uses `title`. The instantiation code must call `task.setTitle(tt.getName())`. This is documented in the architecture doc Section 2.2.
- **Snapshot pattern (ADR-068)**: Template tasks are copied — no live references. Changing a template after instantiation does NOT affect previously created projects.
- **No billable/estimatedHours on Task**: `Task` entity doesn't have these fields. `TemplateTask.billable` and `estimatedHours` are for template-level planning/display only — not copied during instantiation.

---

## Epic 118: Schedule CRUD & Lifecycle

**Goal**: Create the `RecurringScheduleService` with CRUD operations and lifecycle transitions (pause/resume/complete/delete). Add the `RecurringScheduleController` REST endpoints. Wire permission checks, audit events, and validation.

**References**: Architecture doc Sections 3.4 (Schedule Lifecycle), 4.2 (Schedule Endpoints), 10 (Audit), 11 (Permissions).

**Dependencies**: Epic 115 (entities and repositories must exist).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **118A** | 118.1–118.8 | `RecurringScheduleService` with CRUD + lifecycle (pause, resume, complete, delete). Request/response DTOs. `next_execution_date` calculation on create/resume. Delete guard (only PAUSED/COMPLETED). Service integration tests. ~12 tests. | |
| **118B** | 118.9–118.15 | `RecurringScheduleController` with 8 REST endpoints. `SecurityConfig` update. Permission checks (admin/owner for mutations). Audit events. Controller integration tests. ~15 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 118.1 | Create request/response DTOs | 118A | | `schedule/dto/` package. `CreateScheduleRequest` (templateId, customerId, frequency, startDate, endDate, leadTimeDays, projectLeadMemberId, nameOverride). `UpdateScheduleRequest` (nameOverride, endDate, leadTimeDays, projectLeadMemberId — frequency/startDate/template/customer immutable after creation). `ScheduleResponse` (all fields + templateName + customerName + projectLeadName). `ScheduleExecutionResponse` (id, projectId, projectName, periodStart, periodEnd, executedAt). Validation: `@NotNull` on required fields, `@PositiveOrZero` on leadTimeDays. |
| 118.2 | Create `RecurringScheduleService` — CRUD | 118A | 118.1 | `schedule/RecurringScheduleService.java`. Methods: `create(request, memberId)`, `update(id, request)`, `delete(id)`, `list(status, customerId, templateId)`, `get(id)`. On create: validate template exists and is active, validate customer exists, calculate `nextExecutionDate` via `PeriodCalculator`. Unique constraint `(template_id, customer_id, frequency)` — catch `DataIntegrityViolationException` and return 409. |
| 118.3 | Implement lifecycle transitions | 118A | 118.2 | Methods: `pause(id)` — ACTIVE -> PAUSED (preserve nextExecutionDate). `resume(id)` — PAUSED -> ACTIVE (recalculate nextExecutionDate, skip past periods). `complete(id)` — manual ACTIVE/PAUSED -> COMPLETED (terminal). Validation: only valid transitions. |
| 118.4 | Implement delete guard | 118A | 118.2 | Delete only allowed when status is PAUSED or COMPLETED. ACTIVE schedules must be paused first. Return 409 Conflict with descriptive message. |
| 118.5 | Add audit events | 118A | 118.2–118.4 | Audit events: `schedule.created`, `schedule.updated`, `schedule.paused`, `schedule.resumed`, `schedule.completed`, `schedule.deleted`. Details maps per architecture doc Section 10. |
| 118.6 | Add notification events | 118A | 118.5 | Publish `SchedulePausedEvent` for org admin notification. Follow existing `ApplicationEventPublisher` pattern. |
| 118.7 | Write `RecurringScheduleService` integration tests | 118A | 118.2–118.6 | `backend/src/test/java/.../schedule/RecurringScheduleServiceTest.java`. Tests: create schedule (verify nextExecutionDate calculated), create duplicate (template+customer+frequency) returns 409, update schedule, delete PAUSED schedule, delete ACTIVE schedule blocked (409), pause active, resume paused (verify nextExecutionDate recalculated), complete active, invalid transitions (pause already paused, resume active). ~12 tests. |
| 118.8 | Verify 118A tests pass | 118A | 118.7 | Run: `./mvnw test -pl backend -Dtest="RecurringScheduleServiceTest" -q`. All ~12 tests pass. |
| 118.9 | Create `RecurringScheduleController` | 118B | 118A | `schedule/RecurringScheduleController.java`. 8 endpoints per architecture doc Section 4.2: `GET /api/schedules`, `GET /api/schedules/{id}`, `POST /api/schedules`, `PUT /api/schedules/{id}`, `DELETE /api/schedules/{id}`, `POST /api/schedules/{id}/pause`, `POST /api/schedules/{id}/resume`, `GET /api/schedules/{id}/executions`. Pattern: follow `InvoiceController`. |
| 118.10 | Add permission checks | 118B | 118.9 | Admin/Owner for: create, update, delete, pause, resume. All members for: list, get, view executions. Check `RequestScopes.ORG_ROLE`. |
| 118.11 | Update `SecurityConfig` | 118B | 118.9 | Add `/api/schedules/**` to authenticated endpoint list. |
| 118.12 | Response mapping — include template/customer/member names | 118B | 118.9 | `ScheduleResponse` includes `templateName`, `customerName`, `projectLeadName`. Fetch from respective repositories. For executions: include `projectName` from `ProjectRepository`. |
| 118.13 | Write controller integration tests — CRUD | 118B | 118.9–118.12 | `backend/src/test/java/.../schedule/RecurringScheduleControllerTest.java`. MockMvc tests. Tests: list schedules (200), get schedule (200), create schedule (201), create with missing template (404), update schedule (200), delete paused (204), delete active (409), get executions empty (200). ~8 tests. |
| 118.14 | Write controller integration tests — lifecycle + permissions | 118B | 118.13 | Additional tests. Pause (200), resume (200), create as member (403), update as member (403), pause as member (403), list as member (200), list filtered by status (200). ~7 tests. |
| 118.15 | Verify all 118 tests pass | 118B | 118.13–118.14 | Run: `./mvnw test -pl backend -Dtest="RecurringScheduleServiceTest,RecurringScheduleControllerTest" -q`. All ~27 tests pass. |

### Key Files

**Slice 118A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/dto/CreateScheduleRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/dto/UpdateScheduleRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/dto/ScheduleResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/dto/ScheduleExecutionResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/event/SchedulePausedEvent.java`
- `backend/src/test/java/.../schedule/RecurringScheduleServiceTest.java`

**Slice 118B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleController.java`
- `backend/src/test/java/.../schedule/RecurringScheduleControllerTest.java`

**Slice 118B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java`

**Read for context:**
- `architecture/phase16-project-templates-recurring-schedules.md` Sections 3.4, 4.2, 6.5, 10, 11
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — Controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` — Customer lookup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` — Member lookup for projectLeadName

### Architecture Decisions

- **Frequency and startDate immutable**: Once a schedule is created, its frequency, startDate, templateId, and customerId cannot be changed. This prevents period calculation confusion. Users who need a different frequency should delete and recreate.
- **Delete guard**: Only PAUSED or COMPLETED schedules can be deleted. This is a safety measure — deleting an active schedule could silently stop project creation without the user realizing.
- **Unique constraint on (template, customer, frequency)**: Prevents duplicate schedules. If a user wants monthly AND quarterly for the same customer/template, those are two separate schedules — which is correct.

---

## Epic 119: Scheduler Execution Engine

**Goal**: Create the daily cron job that iterates all tenant schemas, finds due schedules, and creates projects. This is the core automation engine — it must be idempotent, fault-tolerant, and respect customer lifecycle status.

**References**: Architecture doc Sections 3.3 (Scheduler Execution), 6 (Scheduler Design), 9 (Notifications).

**Dependencies**: Epic 117 (instantiation logic), Epic 118 (schedule entities and service).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **119A** | 119.1–119.12 | `RecurringScheduleExecutor` with `@Scheduled(cron)`. `processSchedulesForTenant()` and `executeSingleSchedule()` in `RecurringScheduleService`. Tenant iteration via `OrgSchemaMappingRepository`. Idempotency via `(schedule_id, period_start)` unique constraint. Customer lifecycle check. Error isolation (REQUIRES_NEW per schedule, try/catch per tenant). Auto-completion when past end date. Notification events (`RECURRING_PROJECT_CREATED`, `SCHEDULE_SKIPPED`, `SCHEDULE_COMPLETED`). Integration tests. ~18 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 119.1 | Create `RecurringScheduleExecutor` | 119A | | `schedule/RecurringScheduleExecutor.java`. `@Component` with `@Scheduled(cron = "0 0 2 * * *")`. Iterates `OrgSchemaMappingRepository.findAll()`, binds `TENANT_ID` and `ORG_ID` via `ScopedValue.where().call()`. Pattern per architecture doc Section 3.3 + 6.2. Ensure `@EnableScheduling` is present (check `BackendApplication` or add `SchedulerConfig`). |
| 119.2 | Implement `processSchedulesForTenant()` | 119A | 119.1 | In `RecurringScheduleService`. Finds due schedules: `findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", today)`. Iterates and calls `executeSingleSchedule()` for each. Returns `int[]{processed, created}`. |
| 119.3 | Implement `executeSingleSchedule()` | 119A | 119.2 | `@Transactional(propagation = Propagation.REQUIRES_NEW)` for error isolation. Steps: (1) Customer lifecycle check, (2) Period calculation, (3) Idempotency check via `existsByScheduleIdAndPeriodStart()`, (4) Resolve name pattern, (5) Create project (reuse `instantiateFromTemplate()` logic), (6) Record `ScheduleExecution`, (7) Advance schedule (increment count, update nextExecutionDate). (8) Auto-complete if past endDate. Logic per architecture doc Section 3.3. |
| 119.4 | Implement customer lifecycle check | 119A | 119.3 | `isInactiveLifecycle(customer)` — returns true for `OFFBOARDED` and `PROSPECT`. Skips execution, publishes `SCHEDULE_SKIPPED` event, still advances to next period (don't re-check same period). |
| 119.5 | Implement auto-completion | 119A | 119.3 | After advancing schedule: if `nextExecutionDate > endDate`, set status to `COMPLETED`. Publish `SCHEDULE_COMPLETED` event. |
| 119.6 | Create notification events | 119A | 119.3–119.5 | `schedule/event/RecurringProjectCreatedEvent.java`, `ScheduleSkippedEvent.java`, `ScheduleCompletedEvent.java`. These extend the existing domain event base class. Scheduler uses `actorName = "Scheduler"` (no member context). Architecture doc Section 9. |
| 119.7 | Add `NotificationEventHandler` handlers | 119A | 119.6 | Modify `notification/NotificationEventHandler.java`. Add `@TransactionalEventListener` handlers for `RECURRING_PROJECT_CREATED` (notify project lead + org admins), `SCHEDULE_SKIPPED` (notify org admins), `SCHEDULE_COMPLETED` (notify org admins). Handle null `actorId` gracefully for scheduler-generated events. |
| 119.8 | Verify `@EnableScheduling` is configured | 119A | 119.1 | Check if `@EnableScheduling` exists on `BackendApplication` or a config class. If not, add it. Also: disable scheduling in test profile to prevent cron firing during tests — use `@ConditionalOnProperty` or `spring.task.scheduling.enabled` configuration. |
| 119.9 | Ensure `ORG_ID` ScopedValue exists | 119A | 119.1 | Check `RequestScopes.java` for an `ORG_ID` ScopedValue. The executor needs to bind `ORG_ID` (from `OrgSchemaMapping.getOrgId()`) so notification handlers can resolve org members. If missing, add `public static final ScopedValue<String> ORG_ID = ScopedValue.newInstance();`. |
| 119.10 | Write scheduler integration tests | 119A | 119.1–119.9 | `backend/src/test/java/.../schedule/RecurringScheduleExecutorTest.java`. Tests: (1) Happy path — due schedule creates project, records execution, advances nextExecutionDate. (2) Idempotency — re-running for same period skips (no duplicate project). (3) Customer OFFBOARDED — schedule skipped, period still advanced. (4) Customer PROSPECT — schedule skipped. (5) Error isolation — one schedule throws, others still processed. (6) Multiple due schedules — all processed. (7) Auto-completion — schedule past endDate transitions to COMPLETED. (8) Lead time — project created leadTimeDays before periodStart. ~8 tests. Use `@SpringBootTest` + Testcontainers. Disable cron in test — call `processSchedulesForTenant()` directly. |
| 119.11 | Write multi-tenant execution test | 119A | 119.10 | Additional test: (9) Two tenants with due schedules — both processed independently. Bind different TENANT_ID for each, verify projects created in correct schemas. (10) Failing tenant doesn't block other tenants. ~2 tests. |
| 119.12 | Verify all 119A tests pass | 119A | 119.10–119.11 | Run: `./mvnw test -pl backend -Dtest="RecurringScheduleExecutorTest" -q`. All ~18 tests pass (including existing service/controller tests). Run full suite: `./mvnw verify -pl backend -q` to ensure no regressions. |

### Key Files

**Slice 119A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/event/RecurringProjectCreatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/event/ScheduleSkippedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/event/ScheduleCompletedEvent.java`
- `backend/src/test/java/.../schedule/RecurringScheduleExecutorTest.java`

**Slice 119A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleService.java` — Add `processSchedulesForTenant()`, `executeSingleSchedule()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` — Add 3 event handlers
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — Add `ORG_ID` if missing

**Read for context:**
- `architecture/phase16-project-templates-recurring-schedules.md` Sections 3.3, 6, 9
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — ScopedValue pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` — Tenant iteration
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` — Event handler pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` — lifecycleStatus field

### Architecture Decisions

- **REQUIRES_NEW per schedule (ADR-071)**: Each schedule gets its own transaction. If one fails, the rollback is isolated. This is critical for reliability — a single bad schedule should never block the entire batch.
- **Idempotency via unique constraint**: The `(schedule_id, period_start)` unique constraint on `schedule_executions` is the idempotency guard. Even if the cron fires twice or runs on multiple instances, duplicate projects are impossible.
- **Customer lifecycle check**: Skipped customers still advance the period counter. This prevents pile-up — when a customer is re-activated, only future periods are processed, not all missed periods.
- **ScopedValue for ORG_ID**: The scheduler has no request context, but notification handlers need the org ID to resolve recipients. The executor binds ORG_ID from `OrgSchemaMapping.getOrgId()`.

---

## Epic 120: Template Management UI

**Goal**: Create frontend pages for template management — list page (in settings), template editor, "Save as Template" dialog (on project detail), and "New from Template" dialog (on project list). Add API client and template picker component.

**References**: Architecture doc Sections 8.5 (Frontend Changes), 8.6 (Testing Strategy).

**Dependencies**: Epics 116 and 117 (backend template APIs must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **120A** | 120.1–120.10 | API client (`lib/api/templates.ts`), `TemplateList` component, template list settings page, `TemplateEditor` component, template editor page, settings nav update. ~14 tests. | |
| **120B** | 120.11–120.20 | `SaveAsTemplateDialog`, `NewFromTemplateDialog`, `TemplatePicker` shared component, `NameTokenResolver` (frontend utility), project page integrations ("New from Template" button, "Save as Template" action). ~16 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 120.1 | Create templates API client | 120A | | `frontend/lib/api/templates.ts`. Functions: `getTemplates(token)`, `getTemplate(token, id)`, `createTemplate(token, data)`, `updateTemplate(token, id, data)`, `deleteTemplate(token, id)`, `duplicateTemplate(token, id)`, `saveFromProject(token, projectId, data)`, `instantiateTemplate(token, templateId, data)`. Pattern: follow `frontend/lib/api/invoices.ts`. |
| 120.2 | Create `TemplateList` component | 120A | 120.1 | `frontend/components/templates/TemplateList.tsx`. Client component. Displays table: name, source (badge: MANUAL / FROM_PROJECT), task count, tag count, active status (badge), actions (Edit, Duplicate, Delete, Use). Empty state: "No project templates yet." Admin/Owner see action buttons; members see read-only. Pattern: follow `components/invoices/InvoiceList.tsx` for table with actions. |
| 120.3 | Create template list settings page | 120A | 120.2 | `frontend/app/(app)/org/[slug]/settings/project-templates/page.tsx`. RSC page. Fetches templates via `getTemplates(token)`. Renders `TemplateList`. "Create Template" button for admin/owner. Page title: "Project Templates". |
| 120.4 | Create template list Server Actions | 120A | 120.3 | `frontend/app/(app)/org/[slug]/settings/project-templates/actions.ts`. Server Actions: `deleteTemplateAction(id)`, `duplicateTemplateAction(id)`. Both call backend API + `revalidatePath`. |
| 120.5 | Create `TemplateEditor` component | 120A | 120.1 | `frontend/components/templates/TemplateEditor.tsx`. Client component. Form fields: name, namePattern, description, billableDefault (checkbox). Task list section: add task row, remove task, reorder (up/down buttons or drag — simple up/down is fine), edit task fields (name, description, estimatedHours, billable, assigneeRole dropdown). Tag selector: multi-select from available tags (fetch from `/api/tags`). On save: POST or PUT template with nested tasks and tagIds. Pattern: follow `components/templates/TemplateEditorPage.tsx` (Phase 12 document template editor) for form structure. |
| 120.6 | Create template editor page | 120A | 120.5 | `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx`. RSC page. Fetches template detail via `getTemplate(token, id)`. For "new" route: render empty form. Renders `TemplateEditor` with save action. Breadcrumb: Settings > Project Templates > {name}. |
| 120.7 | Update settings nav | 120A | | Add "Project Templates" link to `frontend/app/(app)/org/[slug]/settings/layout.tsx` settings sidebar. Icon: `LayoutTemplate` from lucide-react (or `FileStack`). Only visible to admin/owner. |
| 120.8 | Write `TemplateList` component tests | 120A | 120.2 | `frontend/__tests__/templates/TemplateList.test.tsx`. `afterEach(() => cleanup())`. Tests: renders template name, renders task/tag counts, renders source badge, renders active/inactive badge, action dropdown visible for admin, action dropdown hidden for member, delete action triggers confirmation, empty state renders. ~8 tests. |
| 120.9 | Write `TemplateEditor` component tests | 120A | 120.5 | `frontend/__tests__/templates/TemplateEditor.test.tsx`. `afterEach(() => cleanup())`. Tests: renders form fields, add task row, remove task row, task reorder (move up/down), assigneeRole dropdown shows 3 options, billable checkbox toggles, save calls submit handler with correct data. ~6 tests. |
| 120.10 | Verify 120A frontend builds and tests pass | 120A | 120.8–120.9 | Run: `pnpm run build 2>&1 | tail -5` and `pnpm test --run 2>&1 | grep -E "(PASS|FAIL|Tests:)" | tail -10`. ~14 tests pass. TypeScript compiles. |
| 120.11 | Create `TemplatePicker` shared component | 120B | 120A | `frontend/components/templates/TemplatePicker.tsx`. Reusable component. Fetches active templates and displays as a selectable list (Combobox or Command). Shows: template name, task count, tags. Used by both `NewFromTemplateDialog` and `ScheduleCreateDialog`. Pattern: follow Shadcn Command pattern (cmdk) with value prop for search matching. |
| 120.12 | Create `NameTokenResolver` frontend utility | 120B | | `frontend/lib/name-token-resolver.ts`. Client-side replica of backend `NameTokenResolver`. `resolveNameTokens(pattern, customerName?, referenceDate?)` — replaces {customer}, {month}, {month_short}, {year}. Used for live name preview in dialogs. No {period_start}/{period_end} — those are scheduler-computed. |
| 120.13 | Create `SaveAsTemplateDialog` | 120B | 120.12 | `frontend/components/templates/SaveAsTemplateDialog.tsx`. Client component. Opens from project detail page. Steps: (1) Select tasks (checkbox list of project's tasks), (2) Set role for each selected task (dropdown: PROJECT_LEAD, ANY_MEMBER, UNASSIGNED), (3) Select tags (checkbox list of project's tags), (4) Enter template name, namePattern, description. Name pattern preview shows resolved example. Calls `saveFromProject(token, projectId, data)`. On success: toast + optional redirect to template editor. Pattern: follow multi-step dialog pattern from `NewFromTemplateDialog` or keep as single scrollable form. |
| 120.14 | Create `NewFromTemplateDialog` | 120B | 120.11, 120.12 | `frontend/components/templates/NewFromTemplateDialog.tsx`. Client component. Opens from project list page. Steps: (1) Select template via `TemplatePicker`, (2) Configure: customer (optional select), project lead (optional member select), name (auto-filled from pattern, editable), description. Live name preview via `resolveNameTokens()`. Calls `instantiateTemplate(token, templateId, data)`. On success: redirect to new project page. Pattern: multi-step dialog or single form with sections. |
| 120.15 | Add "Save as Template" to project detail page | 120B | 120.13 | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Add "Save as Template" button in project actions dropdown (for admin/owner/lead). Opens `SaveAsTemplateDialog` with project data pre-loaded. |
| 120.16 | Add "New from Template" to project list page | 120B | 120.14 | Modify `frontend/app/(app)/org/[slug]/projects/page.tsx`. Add "New from Template" button next to existing "New Project" button. Opens `NewFromTemplateDialog`. |
| 120.17 | Write `TemplatePicker` tests | 120B | 120.11 | `frontend/__tests__/templates/TemplatePicker.test.tsx`. `afterEach(() => cleanup())`. Tests: renders template options, search filters templates by name, selecting template calls onChange, no templates shows empty message. ~4 tests. |
| 120.18 | Write `SaveAsTemplateDialog` tests | 120B | 120.13 | `frontend/__tests__/templates/SaveAsTemplateDialog.test.tsx`. `afterEach(() => cleanup())`. Tests: dialog opens, task checkboxes rendered, role dropdown for each task, tag checkboxes rendered, name pattern preview updates, submit calls saveFromProject action, success closes dialog. ~6 tests. |
| 120.19 | Write `NewFromTemplateDialog` tests | 120B | 120.14 | `frontend/__tests__/templates/NewFromTemplateDialog.test.tsx`. `afterEach(() => cleanup())`. Tests: dialog opens, template picker visible, selecting template shows config form, live name preview updates with customer name, submit calls instantiateTemplate action, success redirects to project. ~6 tests. |
| 120.20 | Verify 120B frontend builds and tests pass | 120B | 120.17–120.19 | Run: `pnpm run build 2>&1 | tail -5` and `pnpm test --run 2>&1 | grep -E "(PASS|FAIL|Tests:)" | tail -10`. ~16 new tests pass (120B). All ~30 template tests pass (120A + 120B). |

### Key Files

**Slice 120A — Create:**
- `frontend/lib/api/templates.ts`
- `frontend/components/templates/TemplateList.tsx`
- `frontend/components/templates/TemplateEditor.tsx`
- `frontend/app/(app)/org/[slug]/settings/project-templates/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/project-templates/actions.ts`
- `frontend/__tests__/templates/TemplateList.test.tsx`
- `frontend/__tests__/templates/TemplateEditor.test.tsx`

**Slice 120A — Modify:**
- `frontend/app/(app)/org/[slug]/settings/layout.tsx` — Add "Project Templates" nav

**Slice 120B — Create:**
- `frontend/components/templates/TemplatePicker.tsx`
- `frontend/components/templates/SaveAsTemplateDialog.tsx`
- `frontend/components/templates/NewFromTemplateDialog.tsx`
- `frontend/lib/name-token-resolver.ts`
- `frontend/__tests__/templates/TemplatePicker.test.tsx`
- `frontend/__tests__/templates/SaveAsTemplateDialog.test.tsx`
- `frontend/__tests__/templates/NewFromTemplateDialog.test.tsx`

**Slice 120B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Add "New from Template" button
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add "Save as Template" action

**Read for context:**
- `architecture/phase16-project-templates-recurring-schedules.md` Sections 8.5, 8.6
- `frontend/CLAUDE.md` — Conventions
- `frontend/components/invoices/InvoiceList.tsx` — Table with actions pattern
- `frontend/app/(app)/org/[slug]/settings/layout.tsx` — Settings nav structure
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Project list page structure
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Project detail page structure
- `frontend/vitest.config.ts` — Test config for `@/*` alias

### Architecture Decisions

- **Template editor in settings**: Templates are org-wide configuration, so the management page lives under `/settings/project-templates`. This is consistent with rate card management (also under settings).
- **TemplatePicker as shared component**: Used by both `NewFromTemplateDialog` (Epic 120B) and `ScheduleCreateDialog` (Epic 121A). Built in 120B and imported by 121A.
- **Client-side name token resolver**: Duplicates backend logic intentionally. Running backend logic for a live preview would require an API call per keystroke — bad UX. The utility only handles 4 tokens ({customer}, {month}, {month_short}, {year}); {period_start}/{period_end} are scheduler-only.
- **`afterEach(() => cleanup())` mandatory**: All test files using Radix Dialog must include this per project lessons learned.

---

## Epic 121: Schedule Management UI

**Goal**: Create frontend pages for schedule management — list page with status tabs, create/edit dialogs, execution history, and pause/resume/delete actions. Add sidebar navigation.

**References**: Architecture doc Sections 8.5 (Frontend Changes), 8.6 (Testing Strategy).

**Dependencies**: Epics 118 and 119 (backend schedule APIs must exist). Epic 120B (TemplatePicker component).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **121A** | 121.1–121.10 | API client (`lib/api/schedules.ts`), `ScheduleList` component with status tabs, schedule list page, `ScheduleCreateDialog`, sidebar nav update. ~15 tests. | |
| **121B** | 121.11–121.19 | Schedule detail page, `ScheduleEditDialog`, `ExecutionHistory` component, pause/resume confirmation dialogs, delete action. ~12 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 121.1 | Create schedules API client | 121A | | `frontend/lib/api/schedules.ts`. Functions: `getSchedules(token, params?)`, `getSchedule(token, id)`, `createSchedule(token, data)`, `updateSchedule(token, id, data)`, `deleteSchedule(token, id)`, `pauseSchedule(token, id)`, `resumeSchedule(token, id)`, `getExecutions(token, scheduleId, page?, size?)`. Pattern: follow `frontend/lib/api/templates.ts` (from 120A). |
| 121.2 | Create `ScheduleList` component | 121A | 121.1 | `frontend/components/schedules/ScheduleList.tsx`. Client component. Status tabs: Active, Paused, Completed, All. Table columns: template name, customer name, frequency (badge), next execution date, last executed, execution count, status (badge with color), actions. Actions per status: ACTIVE (Pause, View), PAUSED (Resume, Delete, View), COMPLETED (Delete, View). Empty state per tab. Pattern: follow `components/invoices/InvoiceList.tsx` with tab filtering. |
| 121.3 | Create schedule list page | 121A | 121.2 | `frontend/app/(app)/org/[slug]/schedules/page.tsx`. RSC page. Fetches schedules via `getSchedules(token)`. Renders `ScheduleList`. "New Schedule" button for admin/owner. Page title: "Recurring Schedules". |
| 121.4 | Create schedule page Server Actions | 121A | 121.3 | `frontend/app/(app)/org/[slug]/schedules/actions.ts`. Server Actions: `createScheduleAction(formData)`, `pauseScheduleAction(id)`, `resumeScheduleAction(id)`, `deleteScheduleAction(id)`. All call backend API + `revalidatePath`. |
| 121.5 | Create `ScheduleCreateDialog` | 121A | 121.4 | `frontend/components/schedules/ScheduleCreateDialog.tsx`. Client component. Form fields: template (TemplatePicker from 120B), customer (select from `/api/customers`), frequency (select: 6 options), startDate (date input), endDate (optional date input), leadTimeDays (number, default 0), projectLeadMemberId (optional member select), nameOverride (optional, shows pattern preview). Live name preview using `resolveNameTokens()`. Submit calls `createScheduleAction`. Pattern: follow `ScheduleCreateDialog` pattern — single form, not multi-step. |
| 121.6 | Update sidebar navigation | 121A | | Add "Recurring Schedules" to sidebar navigation. Icon: `CalendarClock` or `RefreshCw` from lucide-react. Position: after "Schedules" or near the bottom of the main nav group. Check `frontend/lib/nav-items.ts` or `components/desktop-sidebar.tsx` for the nav structure. |
| 121.7 | Write `ScheduleList` component tests | 121A | 121.2 | `frontend/__tests__/schedules/ScheduleList.test.tsx`. `afterEach(() => cleanup())`. Tests: renders schedule rows, status badge colors (ACTIVE=green, PAUSED=yellow, COMPLETED=gray), pause action visible for ACTIVE, resume action visible for PAUSED, delete hidden for ACTIVE, tab filtering, empty state. ~8 tests. |
| 121.8 | Write `ScheduleCreateDialog` tests | 121A | 121.5 | `frontend/__tests__/schedules/ScheduleCreateDialog.test.tsx`. `afterEach(() => cleanup())`. Tests: dialog opens, frequency select shows 6 options, startDate required, name preview updates, valid submit calls action, success closes dialog. ~5 tests. |
| 121.9 | Write schedule page render test | 121A | 121.3 | `frontend/__tests__/schedules/SchedulesPage.test.tsx`. Tests: page renders heading, "New Schedule" button present. ~2 tests. Mock API to return empty array. |
| 121.10 | Verify 121A frontend builds and tests pass | 121A | 121.7–121.9 | Run: `pnpm run build 2>&1 | tail -5` and `pnpm test --run 2>&1 | grep -E "(PASS|FAIL|Tests:)" | tail -10`. ~15 tests pass. |
| 121.11 | Create `ScheduleEditDialog` component | 121B | 121A | `frontend/components/schedules/ScheduleEditDialog.tsx`. Client component. Editable fields: nameOverride, endDate, leadTimeDays, projectLeadMemberId. Non-editable (read-only display): template name, customer name, frequency, startDate. Submit calls `updateScheduleAction(id, formData)`. |
| 121.12 | Create `ExecutionHistory` component | 121B | 121.1 | `frontend/components/schedules/ExecutionHistory.tsx`. Client component. Fetches execution history via `getExecutions()`. Table: period (start-end), project name (link to project detail), executed date. Pagination or "Load more". Empty state: "No executions yet — projects will appear here after the first automated run." |
| 121.13 | Create schedule detail page | 121B | 121.11, 121.12 | `frontend/app/(app)/org/[slug]/schedules/[id]/page.tsx`. RSC page. Fetches schedule detail. Renders: schedule info header (status badge, template name, customer name, frequency, dates), `ScheduleEditDialog` (for admin/owner), action buttons (Pause/Resume/Delete), `ExecutionHistory` section. Breadcrumb: Schedules > {customerName} / {templateName}. |
| 121.14 | Add pause/resume confirmation dialogs | 121B | 121.13 | On schedule list and detail page: Pause triggers `AlertDialog` ("Pausing this schedule will stop automatic project creation. You can resume it at any time."). Resume: direct action, no confirmation needed (recovery action). Delete triggers `AlertDialog` ("This will permanently delete the schedule. Existing projects will not be affected."). Pattern: follow `DeleteProjectDialog` for AlertDialog. |
| 121.15 | Wire schedule list row to detail page | 121B | 121.13 | In `ScheduleList`: clicking schedule name navigates to `/schedules/{id}` detail page. Add "View" action in dropdown that does the same. |
| 121.16 | Write `ScheduleEditDialog` tests | 121B | 121.11 | `frontend/__tests__/schedules/ScheduleEditDialog.test.tsx`. `afterEach(() => cleanup())`. Tests: renders pre-filled fields, read-only fields displayed, leadTimeDays accepts numbers, submit calls update action. ~4 tests. |
| 121.17 | Write `ExecutionHistory` tests | 121B | 121.12 | `frontend/__tests__/schedules/ExecutionHistory.test.tsx`. `afterEach(() => cleanup())`. Tests: empty state message, renders execution rows, project name is link, period dates formatted. ~4 tests. |
| 121.18 | Write pause/resume/delete integration tests | 121B | 121.14 | Additional tests in `ScheduleList.test.tsx` or dedicated file. Tests: pause confirmation dialog shows, confirming pause calls action, delete confirmation dialog shows, confirming delete calls action. ~4 tests. |
| 121.19 | Verify all 121 tests pass and full frontend build | 121B | 121.16–121.18 | Run: `pnpm run build 2>&1 | tail -5` and `pnpm test --run 2>&1 | grep -E "(PASS|FAIL|Tests:)" | tail -10`. All ~27 schedule tests pass (121A + 121B). Full frontend test suite passes. |

### Key Files

**Slice 121A — Create:**
- `frontend/lib/api/schedules.ts`
- `frontend/components/schedules/ScheduleList.tsx`
- `frontend/components/schedules/ScheduleCreateDialog.tsx`
- `frontend/app/(app)/org/[slug]/schedules/page.tsx`
- `frontend/app/(app)/org/[slug]/schedules/actions.ts`
- `frontend/__tests__/schedules/ScheduleList.test.tsx`
- `frontend/__tests__/schedules/ScheduleCreateDialog.test.tsx`
- `frontend/__tests__/schedules/SchedulesPage.test.tsx`

**Slice 121A — Modify:**
- `frontend/lib/nav-items.ts` or `frontend/components/desktop-sidebar.tsx` — Add "Recurring Schedules" nav item

**Slice 121B — Create:**
- `frontend/components/schedules/ScheduleEditDialog.tsx`
- `frontend/components/schedules/ExecutionHistory.tsx`
- `frontend/app/(app)/org/[slug]/schedules/[id]/page.tsx`
- `frontend/__tests__/schedules/ScheduleEditDialog.test.tsx`
- `frontend/__tests__/schedules/ExecutionHistory.test.tsx`

**Slice 121B — Modify:**
- `frontend/components/schedules/ScheduleList.tsx` — Wire pause/resume/delete confirmations, row linking

**Read for context:**
- `architecture/phase16-project-templates-recurring-schedules.md` Sections 8.5, 8.6
- `frontend/components/templates/TemplatePicker.tsx` — Import from Epic 120B
- `frontend/lib/name-token-resolver.ts` — Import from Epic 120B for name preview
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` — List page with status filtering pattern
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` — Detail page pattern
- `frontend/components/desktop-sidebar.tsx` — Sidebar nav structure
- `frontend/vitest.config.ts` — Test config

### Architecture Decisions

- **Schedule detail page over expandable rows**: A dedicated `/schedules/[id]` page provides stable URLs, accommodates execution history pagination, and matches the project/customer/invoice detail page pattern.
- **TemplatePicker dependency on 120B**: `ScheduleCreateDialog` imports `TemplatePicker`. If 121A starts before 120B merges, use a simple `<select>` inline and refactor after merge.
- **Pause requires confirmation, resume does not**: Pausing has a material business consequence (projects stop being auto-created). Resume is a recovery action — no confirmation needed.
- **`afterEach(() => cleanup())` mandatory**: All test files using Radix Dialog/AlertDialog must include this.

---

## Test Target Summary

| Epic | Slice | Backend Tests | Frontend Tests |
|------|-------|--------------|----------------|
| 115 | 115A | ~10 | — |
| 115 | 115B | ~22 | — |
| 116 | 116A | ~12 | — |
| 116 | 116B | ~15 | — |
| 117 | 117A | ~15 | — |
| 118 | 118A | ~12 | — |
| 118 | 118B | ~15 | — |
| 119 | 119A | ~18 | — |
| 120 | 120A | — | ~14 |
| 120 | 120B | — | ~16 |
| 121 | 121A | — | ~15 |
| 121 | 121B | — | ~12 |
| **Total** | | **~119 backend** | **~57 frontend** |

**Grand total: ~176 new tests**
