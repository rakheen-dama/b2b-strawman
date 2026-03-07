# Phase 37 -- Workflow Automations v1

Phase 37 adds a rule-based automation engine to the DocTeams platform. Firm admins define configurable rules that react to domain events and perform actions automatically: status-change reactions, follow-up reminders, task chaining, and stakeholder notifications. The engine introduces four new entities (`AutomationRule`, `AutomationAction`, `AutomationExecution`, `ActionExecution`), a single `AutomationEventListener` that bridges existing domain events to rule evaluation, six action executors that delegate to existing services, and a delayed-action scheduler following the proven `TimeReminderScheduler` pattern. Pre-built automation templates are seeded per tenant via JSON pack files.

**Architecture doc**: `architecture/phase37-workflow-automations.md`

**ADRs**:
- [ADR-145](../adr/ADR-145-rule-engine-vs-visual-workflow.md) -- Rule Engine vs Visual Workflow Builder (structured rules for v1)
- [ADR-146](../adr/ADR-146-automation-cycle-detection.md) -- Automation Cycle Detection (metadata flag on domain events)
- [ADR-147](../adr/ADR-147-delayed-action-scheduling.md) -- Delayed Action Scheduling (database polling, 15-min interval)
- [ADR-148](../adr/ADR-148-jsonb-config-vs-normalized-tables.md) -- JSONB Config vs Normalized Tables (JSONB with sealed class validation)
- [ADR-149](../adr/ADR-149-execution-logging-granularity.md) -- Execution Logging Granularity (both rule-level + action-level tables)

**Migrations**: V58 (tenant schema)

**Dependencies on prior phases**: Phase 4 (Customer, Task entities), Phase 5 (TimeEntry), Phase 6 (AuditService), Phase 6.5 (NotificationService, DomainEvent infrastructure), Phase 8 (ProjectBudget, BudgetThresholdEvent), Phase 10 (Invoice), Phase 16 (ProjectTemplate, ProjectInstantiationService), Phase 24 (EmailNotificationChannel), Phase 34 (InformationRequest).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 280 | Automation Entity Foundation & Migration | Backend | -- | M | 280A, 280B | **Done** (PRs #555, #556) |
| 281 | Trigger Matching & Condition Evaluation Engine | Backend | 280 | L | 281A, 281B | **Done** (PRs #557, #558) |
| 282 | Action Executors & Variable Resolution | Backend | 280, 281 | L | 282A, 282B | **Done** (PRs #559, #560) |
| 283 | Delayed Action Scheduler & Cycle Detection | Backend | 282 | M | 283A | **Done** (PR #561) |
| 284 | Rule CRUD API, Template Seeder & Execution Log API | Backend | 280, 281, 282, 283 | L | 284A, 284B | **Done** (PRs #562, #563) |
| 285 | Frontend: Rule List, Template Gallery & Settings Nav | Frontend | 284 | M | 285A | **Done** (PR #564) |
| 286 | Frontend: Rule Create/Edit Wizard | Frontend | 285 | L | 286A, 286B | **Done** (PRs #565, #566) |
| 287 | Frontend: Execution Log & Dashboard Widget | Frontend | 285 | M | 287A | **Done** (PR #567) |
| 288 | End-to-End Integration Tests & Edge Cases | Backend | 283, 284 | M | 288A | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────
[E280A V58 migration:
 automation_rules +
 automation_actions +
 automation_executions +
 action_executions tables +
 all indexes + constraints]
        |
[E280B AutomationRule +
 AutomationAction +
 AutomationExecution +
 ActionExecution entities +
 7 enums + repos + sealed
 config classes + config
 deserializer utility]
        |
        +─────────────────────────────+
        |                             |
[E281A AutomationEventListener +    [E281B ConditionEvaluator +
 trigger type mapping +              AutomationContext builder +
 trigger config matching +           context per trigger type +
 DomainEvent integration]            operator evaluation +
        |                             unit tests]
        +─────────────────────────────+
        |
[E282A ActionExecutor interface +
 CreateTaskActionExecutor +
 SendNotificationActionExecutor +
 SendEmailActionExecutor +
 VariableResolver +
 AutomationActionExecutor dispatcher +
 ActionExecution recording + tests]
        |
[E282B UpdateStatusActionExecutor +
 CreateProjectActionExecutor +
 AssignMemberActionExecutor +
 error handling (non-short-circuit) +
 failure notification + tests]
        |
[E283A AutomationScheduler +
 delayed action creation +
 scheduler picks up due actions +
 DomainEvent.automationExecutionId() +
 cycle detection in listener +
 executionId propagation + tests]
        |
        +─────────────────────────────+
        |                             |
[E284A AutomationRuleService +      [E284B AutomationTemplateService +
 AutomationRuleController +          AutomationTemplateController +
 AutomationActionController +        AutomationTemplateSeeder +
 DTOs + toggle + duplicate +         6 template JSON files +
 execution log API +                 OrgSettings extension +
 AutomationExecutionController +     V58 migration extension +
 audit events + tests]               provisioning hook + tests]

FRONTEND TRACK (after respective backend epics)
────────────────────────────────────────────────
[E285A settings/automations/
 page.tsx rule list +
 TemplateGallery Sheet +
 settings nav "Automations" +
 lib/api/automations.ts +
 RuleList component + tests]
        |
        +─────────────────────────────+
        |                             |
[E286A settings/automations/        [E287A Execution log tab +
 new/page.tsx +                      executions/page.tsx +
 [id]/page.tsx +                     ExecutionDetail Sheet +
 TriggerConfigForm +                 AutomationsWidget +
 ConditionBuilder +                  dashboard integration +
 tests]                              tests]
        |
[E286B ActionList + ActionForm +
 VariableInserter +
 delay toggle + reorder +
 save/cancel + tests]
```

**Parallel opportunities**:
- After E280B: E281A and E281B can start in parallel (listener vs. evaluator).
- After E284A: E284B can run in parallel (templates are independent of CRUD API).
- After E285A: E286A and E287A can start in parallel (create/edit vs. execution log).
- E288A can start after E283A and E284A/284B (needs full engine + API).

---

## Implementation Order

### Stage 0: Database Migration

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 280 | 280A | V58 tenant migration: CREATE TABLE automation_rules, automation_actions, automation_executions, action_executions + all indexes + constraints. ~1 new migration file. Backend only. | **Done** (PR #555) |

### Stage 1: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 280 | 280B | AutomationRule + AutomationAction + AutomationExecution + ActionExecution entities + 7 enums (TriggerType, ActionType, ExecutionStatus, ActionExecutionStatus, RuleSource, DelayUnit, ConditionOperator) + JPA repositories + sealed class hierarchies (TriggerConfig, ActionConfig, ActionResult) + config deserializer utility + persistence tests (~10 tests). ~12 new files. Backend only. | **Done** (PR #556) |

### Stage 2: Engine Core (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 281 | 281A | AutomationEventListener (@EventListener for DomainEvent) + trigger type mapping (event class to TriggerType) + trigger config matching logic (StatusChangeTriggerConfig, BudgetThresholdTriggerConfig, EmptyTriggerConfig matching against event data) + AutomationExecution creation for matched rules + integration tests (~10 tests). ~6 new files. Backend only. | **Done** (PR #557) |
| 2b (parallel) | 281 | 281B | ConditionEvaluator (dot-notation field resolution, 9 operators, AND logic) + AutomationContext builder (builds context map per trigger type from event data) + context definitions for all 8 trigger types + fail-safe behavior (unknown fields) + unit tests (~12 tests). ~4 new files. Backend only. | **Done** (PR #558) |

### Stage 3: Action Executors

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 282 | 282A | ActionExecutor interface + CreateTaskActionExecutor (delegates to TaskService) + SendNotificationActionExecutor (delegates to NotificationService) + SendEmailActionExecutor (delegates to EmailNotificationChannel) + VariableResolver ({{variable}} substitution) + AutomationActionExecutor dispatcher (routes by ActionType) + ActionExecution recording (COMPLETED/FAILED) + integration tests (~12 tests). ~8 new files. Backend only. | **Done** (PR #559) |
| 3b | 282 | 282B | UpdateStatusActionExecutor (delegates to entity-specific services) + CreateProjectActionExecutor (delegates to ProjectInstantiationService) + AssignMemberActionExecutor (delegates to ProjectMemberService) + error handling: failed action logged, subsequent actions still execute + AUTOMATION_ACTION_FAILED notification to org admins + integration tests (~10 tests). ~5 new files. Backend only. | **Done** (PR #560) |

### Stage 4: Scheduler & Cycle Detection

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 283 | 283A | AutomationScheduler (@Scheduled, 15-min poll, per-tenant ScopedValue binding) + delayed action creation (SCHEDULED ActionExecution with scheduledFor) + scheduler picks up due actions + cancels when rule disabled/deleted + DomainEvent.automationExecutionId() default method + cycle detection in AutomationEventListener + executionId propagation through executors -> services -> events + integration tests (~12 tests). ~5 new/modified files. Backend only. | **Done** (PR #561) |

### Stage 5: API & Templates (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 284 | 284A | AutomationRuleService (CRUD, toggle, duplicate, dry-run test) + AutomationRuleController (all rule endpoints) + AutomationActionController (action CRUD + reorder) + AutomationExecutionController (list, detail, per-rule executions) + request/response DTOs + audit events (7 event types) + API integration tests (~18 tests). ~8 new files. Backend only. | **Done** (PR #562) |
| 5b (parallel) | 284 | 284B | AutomationTemplateService (template listing + activation) + AutomationTemplateController (list + activate endpoints) + AutomationTemplateSeeder (loads from classpath JSON) + 6 template definition JSON files + OrgSettings.automationPackStatus JSONB field + V58 migration addendum (ALTER TABLE org_settings ADD COLUMN automation_pack_status) + provisioning + PackReconciliationRunner registration + integration tests (~8 tests). ~10 new/modified files. Backend only. | **Done** (PR #563) |

### Stage 6: Frontend Rule Management

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a | 285 | 285A | settings/automations/page.tsx (rule list with enabled toggles, DataTable) + TemplateGallery Sheet (template cards with activate button) + settings nav "Automations" link + lib/api/automations.ts (API client) + RuleList component + TemplateGallery component + server actions (toggle, delete, activate) + tests (~8 tests). ~8 new files. Frontend only. | **Done** (PR #564) |

### Stage 7: Frontend Create/Edit + Execution Log (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a (parallel) | 286 | 286A | settings/automations/new/page.tsx + settings/automations/[id]/page.tsx + RuleForm layout (3-section scrollable) + TriggerConfigForm (dynamic config by trigger type) + ConditionBuilder (add/remove rows, field/operator/value) + server actions (create, update) + tests (~8 tests). ~7 new files. Frontend only. | **Done** (PR #565) |
| 7b (parallel) | 287 | 287A | Execution log tab on rule detail page + settings/automations/executions/page.tsx (global log) + ExecutionLog DataTable + ExecutionDetail Sheet (per-action status/result/error) + AutomationsWidget (dashboard card: active rules, today's executions, failure badge) + dashboard integration + tests (~8 tests). ~7 new files. Frontend only. | **Done** (PR #567) |

### Stage 8: Frontend Actions Step

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 8a | 286 | 286B | ActionList (sortable ordered list) + ActionForm (dynamic config per action type: CREATE_TASK, SEND_NOTIFICATION, SEND_EMAIL, UPDATE_STATUS, CREATE_PROJECT, ASSIGN_MEMBER) + VariableInserter (variable dropdown for text fields) + delay toggle (Switch + duration + unit) + reorder action + save/cancel form integration + tests (~8 tests). ~6 new files. Frontend only. | **Done** (PR #566) |

### Stage 9: End-to-End Integration Tests

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 9a | 288 | 288A | End-to-end: domain event -> rule evaluation -> condition check -> action execution -> execution log. Multi-rule test (same event, multiple rules). Condition edge cases (null, unknown fields, type mismatches). Cycle detection end-to-end. Delayed action end-to-end. Template activation + execution. Error recovery (action failure). Rule deletion cancels scheduled actions. Rule toggle prevents execution. ~15 integration tests. ~1-2 test files. Backend only. | |

### Timeline

```
Stage 0: [280A]                                                    (sequential)
Stage 1: [280B]                                                    (sequential)
Stage 2: [281A] // [281B]                                          (parallel)
Stage 3: [282A] → [282B]                                           (sequential)
Stage 4: [283A]                                                    (sequential)
Stage 5: [284A] // [284B]                                          (parallel)
Stage 6: [285A]                                                    (sequential)
Stage 7: [286A] // [287A]                                          (parallel)
Stage 8: [286B]                                                    (sequential, after 286A)
Stage 9: [288A]                                                    (after Stages 4+5)
```

**Critical path**: 280A -> 280B -> 281A -> 282A -> 282B -> 283A -> 284A -> 285A -> 286A -> 286B (10 slices sequential).

**Fastest path with parallelism**: 16 slices total, 10 on critical path. Stages 2, 5, and 7 have parallel opportunities. Stage 9 can overlap with Stages 6-8.

---

## Epic 280: Automation Entity Foundation & Migration

**Goal**: Create the V58 tenant migration for all automation tables, build the four core entities with JPA mappings, define all enums and sealed config class hierarchies, and establish JPA repositories with JSONB persistence tests.

**References**: Architecture doc Sections 37.2 (domain model), 37.7 (V56 migration -- renumbered to V58), 37.8.3 (entity code pattern), 37.8.4 (sealed class pattern).

**Dependencies**: None -- this is the greenfield foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **280A** | 280.1--280.4 | V58 tenant migration: CREATE TABLE automation_rules, automation_actions, automation_executions, action_executions + all indexes + constraints + CHECK constraints. ~1 new migration file. Backend only. | **Done** (PR #555) |
| **280B** | 280.5--280.18 | AutomationRule + AutomationAction + AutomationExecution + ActionExecution entities + 7 enums + JPA repositories + sealed class hierarchies (TriggerConfig, ActionConfig, ActionResult) + ConfigDeserializer utility + persistence integration tests (~10 tests). ~12 new files. Backend only. | **Done** (PR #556) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 280.1 | Create V58 tenant migration -- automation_rules table | 280A | | New file: `backend/src/main/resources/db/migration/tenant/V58__create_automation_tables.sql`. CREATE TABLE automation_rules (id UUID PK DEFAULT gen_random_uuid(), name VARCHAR(200) NOT NULL, description VARCHAR(1000), enabled BOOLEAN NOT NULL DEFAULT true, trigger_type VARCHAR(50) NOT NULL, trigger_config JSONB NOT NULL, conditions JSONB, source VARCHAR(20) NOT NULL DEFAULT 'CUSTOM', template_slug VARCHAR(100), created_by UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()). Partial index: idx_automation_rules_enabled_trigger ON (enabled, trigger_type) WHERE enabled = true. See architecture doc Section 37.7. |
| 280.2 | V58 migration -- automation_actions table | 280A | 280.1 | Same file. CREATE TABLE automation_actions (id UUID PK, rule_id UUID NOT NULL FK CASCADE, sort_order INTEGER NOT NULL, action_type VARCHAR(30) NOT NULL, action_config JSONB NOT NULL, delay_duration INTEGER, delay_unit VARCHAR(10), created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ). UNIQUE(rule_id, sort_order). CHECK: both delay fields null or both non-null. |
| 280.3 | V58 migration -- automation_executions table | 280A | 280.1 | Same file. CREATE TABLE automation_executions (id UUID PK, rule_id UUID NOT NULL FK CASCADE, trigger_event_type VARCHAR(100) NOT NULL, trigger_event_data JSONB NOT NULL, conditions_met BOOLEAN NOT NULL, status VARCHAR(30) NOT NULL, started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ, error_message VARCHAR(2000), created_at TIMESTAMPTZ). Indexes: idx_automation_executions_rule_status, idx_automation_executions_started_at DESC. |
| 280.4 | V58 migration -- action_executions table | 280A | 280.1 | Same file. CREATE TABLE action_executions (id UUID PK, execution_id UUID NOT NULL FK CASCADE, action_id UUID FK SET NULL, status VARCHAR(20) NOT NULL, scheduled_for TIMESTAMPTZ, executed_at TIMESTAMPTZ, result_data JSONB, error_message VARCHAR(2000), error_detail TEXT, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ). Partial index: idx_action_executions_scheduled ON (scheduled_for, status) WHERE status = 'SCHEDULED'. |
| 280.5 | Create TriggerType enum | 280B | | New file: `automation/TriggerType.java`. Values: TASK_STATUS_CHANGED, PROJECT_STATUS_CHANGED, CUSTOMER_STATUS_CHANGED, INVOICE_STATUS_CHANGED, TIME_ENTRY_CREATED, BUDGET_THRESHOLD_REACHED, DOCUMENT_ACCEPTED, INFORMATION_REQUEST_COMPLETED. |
| 280.6 | Create ActionType enum | 280B | | New file: `automation/ActionType.java`. Values: CREATE_TASK, SEND_NOTIFICATION, SEND_EMAIL, UPDATE_STATUS, CREATE_PROJECT, ASSIGN_MEMBER. |
| 280.7 | Create ExecutionStatus enum | 280B | | New file: `automation/ExecutionStatus.java`. Values: TRIGGERED, ACTIONS_COMPLETED, ACTIONS_FAILED, CONDITIONS_NOT_MET. |
| 280.8 | Create ActionExecutionStatus enum | 280B | | New file: `automation/ActionExecutionStatus.java`. Values: PENDING, SCHEDULED, COMPLETED, FAILED, CANCELLED. |
| 280.9 | Create RuleSource, DelayUnit, ConditionOperator enums | 280B | | New files: `automation/RuleSource.java` (TEMPLATE, CUSTOM), `automation/DelayUnit.java` (MINUTES, HOURS, DAYS), `automation/ConditionOperator.java` (EQUALS, NOT_EQUALS, IN, NOT_IN, GREATER_THAN, LESS_THAN, CONTAINS, IS_NULL, IS_NOT_NULL). |
| 280.10 | Create AutomationRule entity | 280B | 280.5 | New file: `automation/AutomationRule.java`. @Entity @Table("automation_rules"). All fields per architecture doc Section 37.2.1. JSONB columns: triggerConfig (Map<String, Object>), conditions (Object). Protected no-arg constructor. Methods: toggle(), update(). Pattern: `informationrequest/RequestTemplate.java`. |
| 280.11 | Create AutomationAction entity | 280B | 280.6 | New file: `automation/AutomationAction.java`. @Entity @Table("automation_actions"). Fields per Section 37.2.2. JSONB: actionConfig. Pattern: `checklist/ChecklistTemplateItem.java` (child entity with sortOrder). |
| 280.12 | Create AutomationExecution entity | 280B | 280.7 | New file: `automation/AutomationExecution.java`. @Entity @Table("automation_executions"). Fields per Section 37.2.3. JSONB: triggerEventData. |
| 280.13 | Create ActionExecution entity | 280B | 280.8 | New file: `automation/ActionExecution.java`. @Entity @Table("action_executions"). Fields per Section 37.2.4. JSONB: resultData. |
| 280.14 | Create repositories for all 4 entities | 280B | 280.10, 280.11, 280.12, 280.13 | New files: `automation/AutomationRuleRepository.java` (findByEnabledAndTriggerType), `automation/AutomationActionRepository.java` (findByRuleIdOrderBySortOrder, deleteByRuleId), `automation/AutomationExecutionRepository.java` (findByRuleIdOrderByStartedAtDesc), `automation/ActionExecutionRepository.java` (findByExecutionId, findByStatusAndScheduledForBefore). Pattern: JpaRepository extending with custom queries. |
| 280.15 | Create sealed TriggerConfig hierarchy | 280B | | New file: `automation/config/TriggerConfig.java`. Sealed interface: permits StatusChangeTriggerConfig, BudgetThresholdTriggerConfig, EmptyTriggerConfig. Record implementations per architecture doc Section 37.8.4. |
| 280.16 | Create sealed ActionConfig hierarchy | 280B | | New file: `automation/config/ActionConfig.java`. Sealed interface: permits CreateTaskActionConfig, SendNotificationActionConfig, SendEmailActionConfig, UpdateStatusActionConfig, CreateProjectActionConfig, AssignMemberActionConfig. Records per Section 37.8.4. Include AssignTo enum (TRIGGER_ACTOR, PROJECT_OWNER, SPECIFIC_MEMBER, UNASSIGNED). |
| 280.17 | Create sealed ActionResult hierarchy + ConfigDeserializer | 280B | 280.15, 280.16 | New files: `automation/config/ActionResult.java` (sealed: ActionSuccess, ActionFailure), `automation/config/AutomationConfigDeserializer.java` (deserializeTriggerConfig/deserializeActionConfig using ObjectMapper.convertValue and triggerType/actionType switch). Pattern: Section 37.8.4 code example. |
| 280.18 | Write entity persistence integration tests | 280B | 280.14 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEntityTest.java`. Tests (~10): AutomationRule JSONB round-trip (triggerConfig, conditions), AutomationAction JSONB round-trip, AutomationExecution creation + status, ActionExecution SCHEDULED + status transitions, enum string storage, cascade delete (rule -> actions, execution -> action_executions), config deserializer (each trigger type, each action type), unique constraint on (rule_id, sort_order), delay CHECK constraint. Pattern: similar entity persistence tests in project. |

### Key Files

**Slice 280A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V58__create_automation_tables.sql`

**Slice 280B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ActionType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ExecutionStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ActionExecutionStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/RuleSource.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/DelayUnit.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ConditionOperator.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRule.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationAction.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecution.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ActionExecution.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecutionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ActionExecutionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/config/TriggerConfig.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/config/ActionConfig.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/config/ActionResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/config/AutomationConfigDeserializer.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEntityTest.java`

**Slice 280B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplate.java` -- entity pattern with JSONB
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateItem.java` -- child entity with sortOrder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` -- entity pattern

### Architecture Decisions

- **V58 migration (not V56)**: The architecture doc references V56, but V54-V57 were consumed by Phase 34 (information requests). V58 is the next available tenant migration number.
- **All 4 tables in a single V58 file**: All automation tables are new with no ALTER TABLE on existing tables. Single migration keeps the schema creation atomic.
- **Sealed config classes in `config/` sub-package**: Keeps the main `automation/` package clean. Entities in the root, config value objects in `config/`.
- **Enums as individual files**: Each enum gets its own file following existing codebase convention (not grouped into a single file).

---

## Epic 281: Trigger Matching & Condition Evaluation Engine

**Goal**: Build the AutomationEventListener that bridges domain events to rule evaluation, the ConditionEvaluator for field-based predicate evaluation, and the AutomationContext builder that assembles context maps per trigger type.

**References**: Architecture doc Sections 37.3.1 (listener flow), 37.3.2 (condition evaluation), 37.5.1 (sequence diagram).

**Dependencies**: Epic 280 (entities, enums, repositories).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **281A** | 281.1--281.6 | AutomationEventListener (@EventListener for DomainEvent) + trigger type mapping (event class -> TriggerType lookup table) + trigger config matching logic (StatusChangeTriggerConfig against event fromStatus/toStatus, BudgetThresholdTriggerConfig against threshold, EmptyTriggerConfig always matches) + AutomationExecution creation for matched rules + integration tests (~10 tests). ~6 new files. Backend only. | **Done** (PR #557) |
| **281B** | 281.7--281.12 | ConditionEvaluator (dot-notation field path resolution, 9 operators, AND logic, fail-safe for unknown fields) + AutomationContext builder (context map per trigger type, resolves entity + parent data) + context definitions for all 8 trigger types + unit tests (~12 tests). ~4 new files. Backend only. | **Done** (PR #558) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 281.1 | Create AutomationEventListener | 281A | | New file: `automation/AutomationEventListener.java`. @Component with @EventListener for DomainEvent. Steps: (1) map event class to TriggerType, (2) check cycle detection (automationExecutionId -- placeholder, wired in E283), (3) query enabled rules by triggerType, (4) for each rule: validate trigger config, delegate to condition evaluation and action execution. Wraps all processing in try-catch (outer transaction safety). Pattern: `schedule/TimeReminderScheduler.java` for error isolation. |
| 281.2 | Create TriggerTypeMapping utility | 281A | | New file: `automation/TriggerTypeMapping.java`. Static Map<Class<? extends DomainEvent>, TriggerType> mapping event classes to trigger types. Maps: TaskStatusChangedEvent -> TASK_STATUS_CHANGED, ProjectCompletedEvent/ProjectArchivedEvent/ProjectReopenedEvent -> PROJECT_STATUS_CHANGED, BudgetThresholdEvent -> BUDGET_THRESHOLD_REACHED, TimeEntryChangedEvent -> TIME_ENTRY_CREATED, DocumentUploadedEvent -> DOCUMENT_ACCEPTED, InvoiceSentEvent/InvoicePaidEvent/InvoiceVoidedEvent -> INVOICE_STATUS_CHANGED. Note: CustomerStatusChanged and InformationRequestCompleted events may not exist yet -- add mapping stubs that return null (no-op). |
| 281.3 | Create TriggerConfigMatcher | 281A | | New file: `automation/TriggerConfigMatcher.java`. Validates trigger config against event data. StatusChangeTriggerConfig: matches if toStatus matches event's new status (null = any). BudgetThresholdTriggerConfig: matches if event thresholdPercent >= config thresholdPercent. EmptyTriggerConfig: always matches. Uses AutomationConfigDeserializer to parse rule's triggerConfig JSONB. |
| 281.4 | Wire listener to create AutomationExecution records | 281A | 281.1 | In AutomationEventListener. After trigger config matches, create AutomationExecution with status=TRIGGERED, triggerEventData snapshot, triggerEventType. If trigger config does NOT match, skip (no execution record). Delegates to ConditionEvaluator (placeholder call for now, evaluator in 281B). |
| 281.5 | Write AutomationEventListener integration tests | 281A | 281.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListenerTest.java`. Tests (~10): event with no matching rules (no execution record), event with matching rule (execution created), trigger config match (toStatus matches), trigger config no-match (toStatus differs), multiple rules for same trigger type, disabled rule not matched, unmapped event type ignored, trigger config "any" (null status), execution record has correct triggerEventData snapshot, listener error does not propagate to caller. |
| 281.6 | Test trigger type mappings | 281A | 281.2 | In same test file or separate. Tests: each mapped event class resolves to correct TriggerType, unmapped event returns null. |
| 281.7 | Create AutomationContext builder | 281B | | New file: `automation/AutomationContext.java`. Builder class that constructs a Map<String, Map<String, Object>> context per trigger type. For TASK_STATUS_CHANGED: builds task.* (id, name, status, previousStatus, assigneeId, projectId), project.* (id, name, status, customerId), customer.* (id, name, status), actor.* (id, name), rule.* (id, name). See architecture doc Section 37.3.2 context table. |
| 281.8 | Implement context builders for all 8 trigger types | 281B | 281.7 | In AutomationContext. Methods: buildForTaskStatusChanged(event, rule), buildForProjectStatusChanged(event, rule), etc. Each reads entity data from event fields (events carry enough context -- no additional DB queries per DomainEvent contract). For triggers where parent data is in the event (e.g., projectId on TaskStatusChangedEvent), include it. |
| 281.9 | Create ConditionEvaluator | 281B | | New file: `automation/ConditionEvaluator.java`. evaluate(List<Map<String, Object>> conditions, Map<String, Map<String, Object>> context): boolean. Resolves dot-notation field paths (e.g., "project.status" -> context["project"]["status"]). Applies operator (EQUALS, NOT_EQUALS, IN, NOT_IN, GREATER_THAN, LESS_THAN, CONTAINS, IS_NULL, IS_NOT_NULL). ALL conditions must be true (AND logic). Empty/null conditions list returns true. |
| 281.10 | Implement fail-safe behavior in ConditionEvaluator | 281B | 281.9 | Unknown field paths evaluate to null. IS_NULL on unknown field returns true. All other operators on unknown field return false. Type mismatches (e.g., GREATER_THAN on a string) return false. Log warning for unknown field paths. |
| 281.11 | Wire ConditionEvaluator into AutomationEventListener | 281B | 281.9 | Modify AutomationEventListener to call ConditionEvaluator.evaluate() with parsed conditions and built context. If conditions not met: set execution.status = CONDITIONS_NOT_MET, save. If conditions met: set conditionsMet = true, proceed to action execution (placeholder for E282). |
| 281.12 | Write ConditionEvaluator + AutomationContext unit tests | 281B | 281.9, 281.7 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/ConditionEvaluatorTest.java`. Tests (~12): EQUALS match/no-match, NOT_EQUALS, IN (list), NOT_IN, GREATER_THAN (numeric), LESS_THAN, CONTAINS (string), IS_NULL (null field), IS_NOT_NULL, unknown field returns false, empty conditions returns true, multiple conditions AND logic (all true -> true, one false -> false), context builder for TASK_STATUS_CHANGED, context builder for BUDGET_THRESHOLD_REACHED. |

### Key Files

**Slice 281A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListener.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerTypeMapping.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerConfigMatcher.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListenerTest.java`

**Slice 281B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationContext.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ConditionEvaluator.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/ConditionEvaluatorTest.java`

**Slice 281A -- Modify:**
- None (greenfield).

**Slice 281A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` -- event interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TaskStatusChangedEvent.java` -- event record example
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/BudgetThresholdEvent.java` -- budget event
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` -- error isolation pattern

**Slice 281B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TaskStatusChangedEvent.java` -- event fields for context building
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InvoiceSentEvent.java` -- invoice event fields

### Architecture Decisions

- **Trigger config matching separate from condition evaluation**: TriggerConfigMatcher handles the "type-specific" matching (e.g., status change toStatus), while ConditionEvaluator handles the "generic" field-based conditions. This separation keeps each class focused.
- **No additional DB queries in context building**: The DomainEvent interface requires events to carry all necessary context. The context builder extracts from event fields only, no repository calls.
- **Missing event types handled gracefully**: CustomerStatusChangedEvent and InformationRequestCompletedEvent may not exist in the codebase yet. TriggerTypeMapping returns null for unmapped events, and the listener skips them. Future phases can add the mapping when those events are created.

---

## Epic 282: Action Executors & Variable Resolution

**Goal**: Build the six ActionExecutor implementations that delegate to existing services, the VariableResolver for {{variable}} substitution in text fields, and the AutomationActionExecutor dispatcher that routes by ActionType.

**References**: Architecture doc Sections 37.3.3 (action execution), 37.3.6 (variable substitution), 37.8.5 (ActionExecutor SPI pattern).

**Dependencies**: Epic 280 (entities), Epic 281 (AutomationContext, ConditionEvaluator).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **282A** | 282.1--282.8 | ActionExecutor interface + CreateTaskActionExecutor + SendNotificationActionExecutor + SendEmailActionExecutor + VariableResolver + AutomationActionExecutor dispatcher + ActionExecution recording (COMPLETED/FAILED) + integration tests (~12 tests). ~8 new files. Backend only. | **Done** (PR #559) |
| **282B** | 282.9--282.14 | UpdateStatusActionExecutor + CreateProjectActionExecutor + AssignMemberActionExecutor + error handling (failed action does not short-circuit) + AUTOMATION_ACTION_FAILED notification to org admins + integration tests (~10 tests). ~5 new files. Backend only. | **Done** (PR #560) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 282.1 | Create ActionExecutor interface | 282A | | New file: `automation/executor/ActionExecutor.java`. Interface: `ActionType supportedType()`, `ActionResult execute(ActionConfig config, AutomationContext context)`. See architecture doc Section 37.3.3. |
| 282.2 | Create VariableResolver | 282A | | New file: `automation/VariableResolver.java`. resolve(String template, AutomationContext context): String. Regex-based: find all `{{...}}` patterns, look up dot-notation path in context, replace with string value. Unresolved variables remain literal (safe). See Section 37.3.6. |
| 282.3 | Create CreateTaskActionExecutor | 282A | 282.1 | New file: `automation/executor/CreateTaskActionExecutor.java`. @Component. supportedType() = CREATE_TASK. Delegates to TaskService.create(). Resolves assignee from config (TRIGGER_ACTOR, PROJECT_OWNER, SPECIFIC_MEMBER, UNASSIGNED). Applies VariableResolver to taskName and taskDescription. Returns ActionSuccess with createdTaskId. Pattern: follow thin executor -> service delegation. |
| 282.4 | Create SendNotificationActionExecutor | 282A | 282.1 | New file: `automation/executor/SendNotificationActionExecutor.java`. @Component. Delegates to NotificationService.send(). Resolves recipient from config (TRIGGER_ACTOR, PROJECT_OWNER, PROJECT_MEMBERS, ALL_ADMINS, SPECIFIC_MEMBER). Applies VariableResolver to title and message. |
| 282.5 | Create SendEmailActionExecutor | 282A | 282.1 | New file: `automation/executor/SendEmailActionExecutor.java`. @Component. Delegates to EmailNotificationChannel. Resolves email address from recipient config. CUSTOMER_CONTACT resolves via PortalContactService. Applies VariableResolver to subject and body. |
| 282.6 | Create AutomationActionExecutor dispatcher | 282A | 282.1, 282.3, 282.4, 282.5 | New file: `automation/AutomationActionExecutor.java`. @Component. Collects all ActionExecutor via constructor injection (List<ActionExecutor>). Routes by ActionType. Deserializes actionConfig JSONB to sealed ActionConfig. Creates ActionExecution records: COMPLETED on success, FAILED on failure. See Section 37.8.5 code pattern. |
| 282.7 | Wire AutomationActionExecutor into AutomationEventListener | 282A | 282.6 | Modify: `automation/AutomationEventListener.java`. After conditions met, iterate rule's actions (sorted by sortOrder), call AutomationActionExecutor.execute() for each. Update AutomationExecution.status to ACTIONS_COMPLETED or ACTIONS_FAILED. |
| 282.8 | Write action executor integration tests | 282A | 282.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionExecutorTest.java`. Tests (~12): CreateTask happy path (task created, ActionExecution COMPLETED, resultData has createdTaskId), CreateTask with variable substitution in name, SendNotification happy path, SendEmail happy path, VariableResolver (all resolved, partially resolved, unresolved stays literal), dispatcher routes to correct executor, unknown action type returns failure, ActionExecution persisted with correct status, failed action records error message + detail. |
| 282.9 | Create UpdateStatusActionExecutor | 282B | | New file: `automation/executor/UpdateStatusActionExecutor.java`. @Component. Determines target entity from config (TRIGGER_ENTITY, specific entity by ID). Delegates to appropriate service (TaskService, ProjectService, CustomerService, InvoiceService) based on config's targetEntityType. Validates status transition is legal. |
| 282.10 | Create CreateProjectActionExecutor | 282B | | New file: `automation/executor/CreateProjectActionExecutor.java`. @Component. Delegates to ProjectInstantiationService.create(). Uses templateId from config. Optionally links to trigger's customer (from context). Returns ActionSuccess with createdProjectId. |
| 282.11 | Create AssignMemberActionExecutor | 282B | | New file: `automation/executor/AssignMemberActionExecutor.java`. @Component. Delegates to ProjectMemberService.addMember(). Uses memberId and role from config. Targets the trigger's project (from context). |
| 282.12 | Implement non-short-circuit error handling | 282B | 282.6 | Modify: `automation/AutomationActionExecutor.java`. When an action fails (exception or ActionFailure), log the error, create FAILED ActionExecution, but continue executing subsequent actions. Overall execution status = ACTIONS_FAILED if any action failed. |
| 282.13 | Implement AUTOMATION_ACTION_FAILED notification | 282B | 282.12 | Modify: `automation/AutomationActionExecutor.java`. When any action fails, send AUTOMATION_ACTION_FAILED notification to all org admins/owners via NotificationService. Include rule name, action type, and error summary. |
| 282.14 | Write remaining executor integration tests | 282B | 282.9, 282.10, 282.11, 282.13 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionExecutorExtendedTest.java`. Tests (~10): UpdateStatus happy path (status changed), UpdateStatus invalid transition (ActionFailure), CreateProject from template, AssignMember to project, non-short-circuit (action 1 fails, action 2 still executes), failure notification sent to admins, 3-action sequence with middle failure (action 1 + 3 succeed, action 2 fails, overall = ACTIONS_FAILED), execution status reflects worst case. |

### Key Files

**Slice 282A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/ActionExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/CreateTaskActionExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/SendNotificationActionExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/SendEmailActionExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/VariableResolver.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionExecutor.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionExecutorTest.java`

**Slice 282A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListener.java` -- wire action execution

**Slice 282B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/UpdateStatusActionExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/CreateProjectActionExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/AssignMemberActionExecutor.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionExecutorExtendedTest.java`

**Slice 282B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionExecutor.java` -- error handling, failure notification

**Read for context (both slices):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- create() delegation target
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- send() delegation target
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectInstantiationService.java` -- create() delegation target
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberService.java` -- addMember() delegation target

### Architecture Decisions

- **Split executors into two slices**: 282A covers the 3 most common executors (CreateTask, SendNotification, SendEmail) + the dispatcher + VariableResolver. 282B covers the remaining 3 (UpdateStatus, CreateProject, AssignMember) + error handling. This keeps each slice under 8 new files.
- **executor/ sub-package**: Keeps the 6 executor implementations organized separately from the core automation classes.
- **Non-short-circuit execution**: Failed actions do not stop subsequent actions. This matches the architecture decision that rules are linear action lists and each action is independent.

---

## Epic 283: Delayed Action Scheduler & Cycle Detection

**Goal**: Build the AutomationScheduler that polls for due delayed actions, implement the delayed action creation flow, add cycle detection via automationExecutionId on DomainEvent, and propagate the execution ID through action executors to services to events.

**References**: Architecture doc Sections 37.3.4 (delayed actions), 37.3.5 (cycle detection), 37.5.2 (delayed action sequence diagram), ADR-146, ADR-147.

**Dependencies**: Epics 280, 281, 282.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **283A** | 283.1--283.10 | AutomationScheduler (@Scheduled, 15-min poll, per-tenant ScopedValue) + delayed action creation (SCHEDULED ActionExecution with scheduledFor) + scheduler picks up due actions + cancels when rule disabled + DomainEvent.automationExecutionId() default method + cycle detection in listener + executionId propagation through executors + integration tests (~12 tests). ~5 new/modified files. Backend only. | **Done** (PR #561) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 283.1 | Add automationExecutionId default method to DomainEvent | 283A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java`. Add: `default UUID automationExecutionId() { return null; }`. This is a backwards-compatible addition -- existing event implementations inherit the default. |
| 283.2 | Wire cycle detection in AutomationEventListener | 283A | 283.1 | Modify: `automation/AutomationEventListener.java`. At step 3 (before querying rules): check `event.automationExecutionId() != null`. If present, log "Skipping automation evaluation -- event originated from execution {id}" and return immediately. |
| 283.3 | Implement delayed action creation in AutomationActionExecutor | 283A | | Modify: `automation/AutomationActionExecutor.java`. When action has delayDuration + delayUnit set: instead of executing immediately, create ActionExecution with status=SCHEDULED, scheduledFor=Instant.now() + delay, store the serialized context in a new JSONB field or reference. Return ActionSuccess(scheduled=true). |
| 283.4 | Create AutomationScheduler | 283A | | New file: `automation/AutomationScheduler.java`. @Component with @Scheduled(fixedRate = 900_000). Per-tenant iteration: OrgSchemaMappingRepository.findAll(), ScopedValue.where(TENANT_ID, schema).run(...). Per tenant: query ActionExecution WHERE status='SCHEDULED' AND scheduled_for <= now(). Per due action: load parent rule, check enabled, execute or cancel. Pattern: `schedule/TimeReminderScheduler.java`. |
| 283.5 | Implement scheduler execution logic | 283A | 283.4 | In AutomationScheduler. For each due ActionExecution: (1) load parent AutomationAction via actionId, (2) load parent AutomationRule via execution -> rule, (3) if rule disabled or deleted: mark CANCELLED, (4) if rule enabled: deserialize actionConfig, rebuild context from execution's stored data, execute via appropriate ActionExecutor, update status to COMPLETED or FAILED. Use TransactionTemplate per action. |
| 283.6 | Propagate automationExecutionId through executors | 283A | | Modify: action executors (CreateTaskActionExecutor, UpdateStatusActionExecutor, etc.) to accept and pass automationExecutionId when invoking services. This requires adding an executionId parameter to the ActionExecutor.execute() signature. Services that publish DomainEvents must include the executionId in the event. |
| 283.7 | Add automationExecutionId to relevant event implementations | 283A | 283.6 | Modify event records that can be produced by automation actions: TaskStatusChangedEvent (when UpdateStatus changes task status), ProjectCompletedEvent (when UpdateStatus completes project). Override automationExecutionId() in these events. Add a nullable UUID automationExecutionId field to the record constructors. Existing callers pass null. |
| 283.8 | Store context for delayed actions | 283A | 283.3 | The AutomationContext must be available when the delayed action is executed (hours/days later). Approach: serialize the context map as part of the ActionExecution (store in result_data or a dedicated context_data column -- prefer result_data since it is JSONB and currently nullable). On scheduler execution, deserialize and pass to the executor. |
| 283.9 | Write scheduler integration tests | 283A | 283.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationSchedulerTest.java`. Tests (~8): scheduler picks up due SCHEDULED action (status -> COMPLETED), scheduler skips not-yet-due actions, scheduler cancels when rule disabled, scheduler cancels when rule deleted, per-tenant isolation (tenant A action does not affect tenant B), delayed action creation (action with delay -> SCHEDULED + correct scheduledFor), execution ID stored on action execution. |
| 283.10 | Write cycle detection integration tests | 283A | 283.2, 283.7 | In same or separate test file. Tests (~4): automation-originated event skipped by listener (automationExecutionId set -> no execution record), normal event processed by listener (automationExecutionId null), CreateTask action passes executionId to task event, propagated executionId prevents re-trigger. |

### Key Files

**Slice 283A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationSchedulerTest.java`

**Slice 283A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` -- add automationExecutionId default method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TaskStatusChangedEvent.java` -- add automationExecutionId field
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListener.java` -- cycle detection check
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionExecutor.java` -- delayed action creation, executionId passing
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/ActionExecutor.java` -- add executionId to signature
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/CreateTaskActionExecutor.java` -- pass executionId

**Slice 283A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` -- per-tenant iteration, ScopedValue binding, TransactionTemplate
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` -- findAll() for tenant iteration

### Architecture Decisions

- **Context stored in ActionExecution.result_data**: For delayed actions, the automation context (needed for variable resolution and executor logic at execution time) is serialized into the JSONB result_data field of the SCHEDULED ActionExecution. This avoids adding a new column and reuses an existing nullable JSONB field. On execution, the scheduler deserializes the context. An alternative would be re-building context from the parent AutomationExecution's triggerEventData, which is cleaner but requires the context builder to work from stored event data rather than a live event.
- **ActionExecutor interface change**: Adding executionId to the execute() signature is a breaking change to the interface. Since all 6 implementations are in this phase, the change is contained. The executionId is nullable -- non-automation callers (e.g., test endpoints) pass null.
- **Minimal event modifications**: Only events that can actually be produced by automation actions need the automationExecutionId override. Most events remain unchanged. Start with TaskStatusChangedEvent; others can be added as needed when the corresponding executor is tested end-to-end.

---

## Epic 284: Rule CRUD API, Template Seeder & Execution Log API

**Goal**: Build the REST API for automation rule management (CRUD, toggle, duplicate, test), action management (CRUD, reorder), template listing and activation, execution log queries, the template seeder with JSON definition files, and wire into tenant provisioning.

**References**: Architecture doc Sections 37.4 (API surface), 37.4.5 (request/response shapes), 37.8.6 (pack seeder pattern), 37.6 (audit events).

**Dependencies**: Epics 280, 281, 282, 283 (full engine must work).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **284A** | 284.1--284.10 | AutomationRuleService (CRUD, toggle, duplicate, dry-run) + AutomationRuleController + AutomationActionController (CRUD + reorder) + AutomationExecutionController (list, detail, per-rule) + DTOs + audit events (7 types) + API integration tests (~18 tests). ~8 new files. Backend only. | **Done** (PR #562) |
| **284B** | 284.11--284.18 | AutomationTemplateService (listing + activation) + AutomationTemplateController + AutomationTemplateSeeder + 6 template JSON files + OrgSettings.automationPackStatus extension + provisioning + PackReconciliationRunner registration + integration tests (~8 tests). ~10 new/modified files. Backend only. | **Done** (PR #563) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 284.1 | Create AutomationRuleService | 284A | | New file: `automation/AutomationRuleService.java`. Methods: createRule(request, memberId), getRule(id), listRules(enabled, triggerType), updateRule(id, request), deleteRule(id), toggleRule(id), duplicateRule(id, memberId), testRule(id, sampleEventData). Delete cascades (JPA), cancels scheduled ActionExecutions. Toggle updates enabled field. Duplicate copies rule + actions with source=CUSTOM. Test runs condition evaluation against sample data (dry-run, no side effects). |
| 284.2 | Create automation DTOs | 284A | | New file: `automation/dto/AutomationDtos.java`. Records: CreateRuleRequest, UpdateRuleRequest, CreateActionRequest, UpdateActionRequest, ReorderActionsRequest(List<UUID> actionIds), AutomationRuleResponse (with actions), AutomationActionResponse, AutomationExecutionResponse (with actionExecutions), ActionExecutionResponse, TestRuleRequest(Map<String, Object> sampleEventData), TestRuleResponse(boolean conditionsMet, List<String> evaluationDetails). |
| 284.3 | Create AutomationRuleController | 284A | 284.1, 284.2 | New file: `automation/AutomationRuleController.java`. Endpoints per architecture doc Section 37.4.1: GET /api/automation-rules, POST, GET /{id}, PUT /{id}, DELETE /{id}, POST /{id}/toggle, POST /{id}/duplicate, POST /{id}/test. @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')"). Pure delegation to AutomationRuleService. Pattern: thin controller discipline from backend/CLAUDE.md. |
| 284.4 | Create AutomationActionController | 284A | 284.1, 284.2 | New file: `automation/AutomationActionController.java`. Endpoints per Section 37.4.2: POST /api/automation-rules/{id}/actions, PUT /.../actions/{actionId}, DELETE /.../actions/{actionId}, PUT /.../actions/reorder. @PreAuthorize admin/owner. Delegates to AutomationRuleService methods for action management. |
| 284.5 | Create AutomationExecutionController | 284A | 284.2 | New file: `automation/AutomationExecutionController.java`. Endpoints per Section 37.4.4: GET /api/automation-executions (filter: ruleId, status, dateRange), GET /{id} (with action details), GET /api/automation-rules/{id}/executions. @PreAuthorize admin/owner. Delegates to service for queries. |
| 284.6 | Add audit events to AutomationRuleService | 284A | 284.1 | Modify: AutomationRuleService. Publish audit events via AuditEventBuilder for: AUTOMATION_RULE_CREATED, UPDATED, DELETED, ENABLED, DISABLED, AUTOMATION_EXECUTED, AUTOMATION_ACTION_FAILED. Pattern: existing AuditEventBuilder usage in InformationRequestService, ProposalService. |
| 284.7 | Implement action CRUD in AutomationRuleService | 284A | 284.1 | In AutomationRuleService. Methods: addAction(ruleId, request), updateAction(ruleId, actionId, request), removeAction(ruleId, actionId), reorderActions(ruleId, actionIds). Reorder updates sortOrder for each action to match the provided ID order. |
| 284.8 | Implement rule deletion with scheduled action cancellation | 284A | 284.1 | In AutomationRuleService.deleteRule(). Before JPA cascade delete: query ActionExecution WHERE status='SCHEDULED' AND action references this rule's actions. Update all to CANCELLED. Then delete rule (cascades). |
| 284.9 | Implement dry-run test endpoint | 284A | 284.1 | In AutomationRuleService.testRule(). Accepts sample event data (Map<String, Object>). Builds AutomationContext from the sample data. Evaluates conditions via ConditionEvaluator. Returns which conditions matched/failed and what actions would fire (no execution). |
| 284.10 | Write API integration tests | 284A | 284.3, 284.4, 284.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleControllerTest.java`. Tests (~18): create rule (201), get rule (with actions), list rules (filter by enabled, triggerType), update rule, delete rule (cascades), toggle (enabled->disabled->enabled), duplicate (new rule with CUSTOM source), add action, update action, remove action, reorder actions, test endpoint (dry-run), execution list (filter by ruleId, status), execution detail (with action details), RBAC (member gets 403), audit events created on rule CRUD, pagination on execution list. |

| 284.11 | Create AutomationTemplateDefinition records | 284B | | New file: `automation/template/AutomationTemplateDefinition.java`. Records: AutomationTemplateDefinition(String slug, String name, String description, String category, TriggerType triggerType, Map<String, Object> triggerConfig, List<Map<String, Object>> conditions, List<TemplateActionDefinition> actions). TemplateActionDefinition(ActionType actionType, Map<String, Object> actionConfig, Integer delayDuration, DelayUnit delayUnit, int sortOrder). Pattern: `informationrequest/RequestPackDefinition.java`. |
| 284.12 | Create 6 template JSON files | 284B | 284.11 | New directory: `backend/src/main/resources/automation-templates/`. New file: `common.json` containing 6 templates: (1) task-completion-chain (TASK_STATUS_CHANGED -> CREATE_TASK), (2) overdue-invoice-reminder (INVOICE_STATUS_CHANGED -> SEND_EMAIL + delayed SEND_NOTIFICATION), (3) budget-alert-escalation (BUDGET_THRESHOLD_REACHED -> SEND_NOTIFICATION to admins), (4) new-project-welcome (PROJECT_STATUS_CHANGED to ACTIVE -> SEND_NOTIFICATION to members), (5) document-review-notification (DOCUMENT_ACCEPTED -> SEND_NOTIFICATION to project owner), (6) request-complete-followup (INFORMATION_REQUEST_COMPLETED -> CREATE_TASK for review). See architecture doc description of templates. |
| 284.13 | Create AutomationTemplateSeeder | 284B | 284.11, 284.12 | New file: `automation/template/AutomationTemplateSeeder.java`. Loads from classpath `automation-templates/common.json`. Creates AutomationRule + AutomationAction records with source=TEMPLATE, enabled=false (templates start inactive). Uses OrgSettings.automationPackStatus for idempotency. Pattern: `template/TemplatePackSeeder.java`, `informationrequest/RequestPackSeeder.java`. |
| 284.14 | Create AutomationTemplateService | 284B | 284.13 | New file: `automation/template/AutomationTemplateService.java`. listTemplates(): returns all template definitions from JSON (not from DB). activateTemplate(slug, memberId): loads template definition, creates AutomationRule (source=TEMPLATE, templateSlug=slug, enabled=true) + AutomationActions. Returns the created rule. Checks if already activated (rule with templateSlug exists) -- allows multiple activations (creates duplicates with unique names). |
| 284.15 | Create AutomationTemplateController | 284B | 284.14 | New file: `automation/template/AutomationTemplateController.java`. GET /api/automation-templates (list). POST /api/automation-templates/{slug}/activate (returns 201 + created rule). @PreAuthorize admin/owner. |
| 284.16 | Extend OrgSettings with automationPackStatus | 284B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add field: `automationPackStatus` (JSONB, nullable) with @JdbcTypeCode(SqlTypes.JSON). Add `recordAutomationPackApplication(String packId, String version)` and `isAutomationPackApplied(String packId)` methods. Pattern: existing pack status fields. Note: V58 migration must include ALTER TABLE org_settings ADD COLUMN automation_pack_status JSONB -- add to V58 or create separate V59. Prefer adding to V58 since it is in the same phase. |
| 284.17 | Wire AutomationTemplateSeeder into provisioning | 284B | 284.13 | Modify: provisioning service (`TenantProvisioningService` or similar) and `PackReconciliationRunner` to call `automationTemplateSeeder.seedPacksForTenant()`. Pattern: existing seeder registrations (template pack, compliance pack, request pack). |
| 284.18 | Write template + seeder integration tests | 284B | 284.15, 284.17 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationTemplateControllerTest.java`. Tests (~8): list templates (returns 6), activate template (creates rule + actions), activated template has source=TEMPLATE + templateSlug, activated rule is enabled, activate same template twice (creates 2 rules), RBAC (member 403), seeder idempotency (run twice, no duplicates), seeder creates rules with enabled=false. |

### Key Files

**Slice 284A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/dto/AutomationDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecutionController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleControllerTest.java`

**Slice 284B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateController.java`
- `backend/src/main/resources/automation-templates/common.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationTemplateControllerTest.java`

**Slice 284B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/` (provisioning service + PackReconciliationRunner)
- `backend/src/main/resources/db/migration/tenant/V58__create_automation_tables.sql` (add ALTER TABLE org_settings ADD COLUMN automation_pack_status)

**Read for context (both slices):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` -- pack seeder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestPackSeeder.java` -- pack seeder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- audit event creation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestController.java` -- controller pattern

### Architecture Decisions

- **Seeded templates start disabled**: AutomationTemplateSeeder creates rules with enabled=false. Admins must explicitly activate templates. This prevents unexpected automation on existing tenants when the phase is deployed.
- **Template activation creates a mutable copy**: Activating a template creates a real AutomationRule that the admin can modify freely. The source=TEMPLATE and templateSlug fields provide provenance. Multiple activations create independent copies.
- **V58 migration addendum for OrgSettings**: The ALTER TABLE org_settings ADD COLUMN statement is appended to V58 rather than creating a V59. This keeps Phase 37 to a single migration file. The column addition is safe (nullable, no default constraint).
- **Templates stored as classpath JSON, not DB**: Template definitions are static resource files, not database records. This keeps them versioned with code. The DB stores only activated copies (as AutomationRule records). The seeder pattern (JSON -> DB with idempotency via OrgSettings) is proven across 4 prior phases.

---

## Epic 285: Frontend -- Rule List, Template Gallery & Settings Nav

**Goal**: Create the automations settings page with rule list table, template gallery side panel, settings navigation link, and the API client for automation endpoints.

**References**: Architecture doc Sections 37.10.1 (automations settings page), 37.10.6 (component breakdown).

**Dependencies**: Epic 284 (API must be available).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **285A** | 285.1--285.9 | settings/automations/page.tsx (rule list with enabled toggles) + TemplateGallery Sheet component + settings nav "Automations" link + lib/api/automations.ts API client + RuleList component + server actions (toggle, delete, activate template) + tests (~8 tests). ~8 new files + ~1 modified file. Frontend only. | **Done** (PR #564) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 285.1 | Create automation API client | 285A | | New file: `frontend/lib/api/automations.ts`. Functions: listRules(params), getRule(id), createRule(data), updateRule(id, data), deleteRule(id), toggleRule(id), duplicateRule(id), listTemplates(), activateTemplate(slug), listExecutions(params), getExecution(id), getRuleExecutions(ruleId, params), addAction(ruleId, data), updateAction(ruleId, actionId, data), deleteAction(ruleId, actionId), reorderActions(ruleId, actionIds), testRule(id, sampleData). Uses lib/api.ts pattern. |
| 285.2 | Create RuleList component | 285A | 285.1 | New file: `frontend/components/automations/rule-list.tsx`. "use client". DataTable with columns: Name, Trigger Type (Badge), Enabled (Switch toggle), Last Triggered (relative timestamp), Executions (count), Status (last execution status icon). Row click navigates to /settings/automations/[id]. "New Automation" button. "Browse Templates" button. Empty state: illustration + "Create your first automation or browse templates". Pattern: existing DataTable usage in settings pages. |
| 285.3 | Create TemplateGallery component | 285A | 285.1 | New file: `frontend/components/automations/template-gallery.tsx`. "use client". Shadcn Sheet (side panel). Grid of template cards, each: name, description, trigger type Badge, action count. "Activate" button per template. Already-activated templates show "Activated" badge. Templates grouped by category. Pattern: existing Sheet usage in project. |
| 285.4 | Create automations settings page | 285A | 285.2, 285.3 | New file: `frontend/app/(app)/org/[slug]/settings/automations/page.tsx`. Server component. Fetches rules via API client. Renders RuleList + TemplateGallery trigger button. Breadcrumbs: Settings > Automations. |
| 285.5 | Create server actions for automations | 285A | 285.1 | New file: `frontend/app/(app)/org/[slug]/settings/automations/actions.ts`. "use server". Actions: toggleRuleAction(ruleId), deleteRuleAction(ruleId), activateTemplateAction(slug). Calls API client, revalidates path. Pattern: existing server actions in settings/. |
| 285.6 | Add "Automations" to settings navigation | 285A | | Modify: `frontend/lib/nav-items.ts` or settings layout. Add "Automations" link to settings sidebar nav, with Zap icon (from lucide-react). Position: after existing settings items (templates, custom fields, etc.). |
| 285.7 | Create TriggerTypeBadge component | 285A | | New file: `frontend/components/automations/trigger-type-badge.tsx`. Maps TriggerType enum to display label + color Badge variant. E.g., TASK_STATUS_CHANGED -> "Task Status" (teal), INVOICE_STATUS_CHANGED -> "Invoice Status" (amber). |
| 285.8 | Create ExecutionStatusBadge component | 285A | | New file: `frontend/components/automations/execution-status-badge.tsx`. Maps ExecutionStatus to display label + Badge variant. ACTIONS_COMPLETED -> "Completed" (success), ACTIONS_FAILED -> "Failed" (destructive), CONDITIONS_NOT_MET -> "Skipped" (neutral). |
| 285.9 | Write frontend tests | 285A | 285.4 | New file: `frontend/__tests__/automations/rule-list.test.tsx`. Tests (~8): renders rule list with data, empty state shown when no rules, toggle switch calls toggle action, delete button shows confirmation, template gallery opens as Sheet, activate template calls action, trigger type badge renders correctly, rule row click navigates to detail page. Pattern: existing settings test files. |

### Key Files

**Slice 285A -- Create:**
- `frontend/lib/api/automations.ts`
- `frontend/components/automations/rule-list.tsx`
- `frontend/components/automations/template-gallery.tsx`
- `frontend/components/automations/trigger-type-badge.tsx`
- `frontend/components/automations/execution-status-badge.tsx`
- `frontend/app/(app)/org/[slug]/settings/automations/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/automations/actions.ts`
- `frontend/__tests__/automations/rule-list.test.tsx`

**Slice 285A -- Modify:**
- `frontend/lib/nav-items.ts` (or settings layout) -- add Automations nav item

**Slice 285A -- Read for context:**
- `frontend/app/(app)/org/[slug]/settings/templates/page.tsx` -- settings page pattern
- `frontend/components/ui/data-table.tsx` -- DataTable component
- `frontend/components/ui/sheet.tsx` -- Sheet component
- `frontend/lib/api/` -- existing API client pattern

### Architecture Decisions

- **Single settings page with Sheet for templates**: Template gallery is a Sheet (side panel), not a separate page. This keeps the UX focused -- admins see their rules and can browse templates without leaving the page.
- **Server component page + client component list**: The page.tsx is a server component that fetches initial data. RuleList is a client component for interactive features (toggle, navigation).

---

## Epic 286: Frontend -- Rule Create/Edit Wizard

**Goal**: Build the rule create and edit pages with the three-section form (trigger, conditions, actions), including dynamic form fields based on trigger/action type, condition builder, action list with ordering and delay toggle, and variable insertion.

**References**: Architecture doc Sections 37.10.2 (rule create/edit page), 37.10.6 (component breakdown).

**Dependencies**: Epic 285 (navigation structure, API client).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **286A** | 286.1--286.7 | settings/automations/new/page.tsx + settings/automations/[id]/page.tsx + RuleForm (3-section layout) + TriggerConfigForm (dynamic config by trigger type) + ConditionBuilder (add/remove condition rows) + create/update server actions + tests (~8 tests). ~7 new files. Frontend only. | **Done** (PR #565) |
| **286B** | 286.8--286.14 | ActionList (sortable ordered list) + ActionForm (dynamic config per action type) + VariableInserter (dropdown for text fields) + delay toggle (Switch + duration + unit) + reorder integration + save/cancel form wiring + tests (~8 tests). ~6 new files. Frontend only. | **Done** (PR #566) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 286.1 | Create RuleForm component | 286A | | New file: `frontend/components/automations/rule-form.tsx`. "use client". Three-section scrollable form (not a stepper). Section 1: Trigger (name, description, trigger type, dynamic config). Section 2: Conditions (optional, collapsible). Section 3: Actions (placeholder for 286B). Save/Cancel buttons. Uses react-hook-form or controlled state. |
| 286.2 | Create TriggerConfigForm component | 286A | | New file: `frontend/components/automations/trigger-config-form.tsx`. "use client". Dynamic fields based on triggerType: Status change triggers -> "From Status" + "To Status" Select (options from relevant enum + "Any"). Budget threshold -> number Input with "%" suffix. Simple triggers -> helper text. |
| 286.3 | Create ConditionBuilder component | 286A | | New file: `frontend/components/automations/condition-builder.tsx`. "use client". "Add Condition" button. Each row: Field Select (filtered by trigger type -- e.g., for TASK_STATUS_CHANGED: task.status, task.name, project.status, project.name, customer.name) -> Operator Select (filtered by field type) -> Value Input (or Select for IN/NOT_IN, hidden for IS_NULL/IS_NOT_NULL) -> Remove button. Helper text: "All conditions must be true (AND logic)." |
| 286.4 | Create automations create page | 286A | 286.1 | New file: `frontend/app/(app)/org/[slug]/settings/automations/new/page.tsx`. Server component. Renders RuleForm in create mode. Breadcrumbs: Settings > Automations > New. |
| 286.5 | Create automations detail/edit page | 286A | 286.1 | New file: `frontend/app/(app)/org/[slug]/settings/automations/[id]/page.tsx`. Server component. Fetches rule via API. Renders header (name, enabled Switch, trigger Badge, created by, dropdown: Duplicate, Delete). Tabs: Configuration (RuleForm in edit mode), Execution Log (placeholder for 287A). |
| 286.6 | Create server actions for create/update | 286A | | New file: `frontend/app/(app)/org/[slug]/settings/automations/new/actions.ts` and extend existing actions.ts. createRuleAction(formData), updateRuleAction(id, formData). Calls API client, redirects to detail page on success. |
| 286.7 | Write trigger + condition form tests | 286A | 286.3 | New file: `frontend/__tests__/automations/rule-form.test.tsx`. Tests (~8): render form with all sections, trigger type change updates config fields, status change shows from/to selects, budget threshold shows number input, add condition row, remove condition row, condition operator options filter by field type, form validation (name required, trigger type required). |
| 286.8 | Create ActionList component | 286B | | New file: `frontend/components/automations/action-list.tsx`. "use client". Sortable list of action cards. Each card: action type icon, config summary, delay indicator, drag handle or up/down buttons, remove button. "Add Action" button -> action type Select. |
| 286.9 | Create ActionForm component | 286B | | New file: `frontend/components/automations/action-form.tsx`. "use client". Dynamic config per action type: CREATE_TASK (taskName Input, description Textarea, assignTo Select), SEND_NOTIFICATION (recipientType Select, title Input, message Textarea), SEND_EMAIL (recipientType Select, subject Input, body Textarea), UPDATE_STATUS (targetEntityType Select, newStatus Select), CREATE_PROJECT (templateId Select loaded from /api/project-templates), ASSIGN_MEMBER (member Combobox, role Select). |
| 286.10 | Create VariableInserter component | 286B | | New file: `frontend/components/automations/variable-inserter.tsx`. "use client". Popover triggered by "Insert Variable" button (or typing {{). Shows available variables filtered by trigger type. On select, inserts {{variable.path}} at cursor position. Variables: task.name, task.status, project.name, customer.name, invoice.invoiceNumber, actor.name, rule.name, etc. |
| 286.11 | Create delay toggle UI | 286B | | In ActionForm or ActionList. Per-action Switch "Add Delay". When enabled, shows: duration number Input + unit Select (Minutes, Hours, Days). Maps to delayDuration + delayUnit fields. |
| 286.12 | Implement action reorder | 286B | | In ActionList. Reorder triggers PUT /api/automation-rules/{id}/actions/reorder with new ID order. Updates sortOrder locally for optimistic UI. |
| 286.13 | Wire actions section into RuleForm | 286B | 286.8 | Modify: `components/automations/rule-form.tsx`. Replace action section placeholder with ActionList + ActionForm. Save button submits trigger config + conditions + actions together (create) or updates individually (edit). |
| 286.14 | Write action form tests | 286B | 286.9, 286.10 | New file: `frontend/__tests__/automations/action-form.test.tsx`. Tests (~8): add action shows type selector, CREATE_TASK form fields render, SEND_NOTIFICATION form fields render, variable inserter opens popover, variable insertion into text field, delay toggle shows duration/unit, remove action removes from list, reorder changes sort order. |

### Key Files

**Slice 286A -- Create:**
- `frontend/components/automations/rule-form.tsx`
- `frontend/components/automations/trigger-config-form.tsx`
- `frontend/components/automations/condition-builder.tsx`
- `frontend/app/(app)/org/[slug]/settings/automations/new/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/automations/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/automations/new/actions.ts`
- `frontend/__tests__/automations/rule-form.test.tsx`

**Slice 286B -- Create:**
- `frontend/components/automations/action-list.tsx`
- `frontend/components/automations/action-form.tsx`
- `frontend/components/automations/variable-inserter.tsx`
- `frontend/__tests__/automations/action-form.test.tsx`

**Slice 286B -- Modify:**
- `frontend/components/automations/rule-form.tsx` -- wire actions section

**Read for context (both slices):**
- `frontend/components/automations/rule-list.tsx` -- component pattern from 285A
- `frontend/components/ui/select.tsx` -- Select component
- `frontend/components/ui/popover.tsx` -- Popover component
- `frontend/app/(app)/org/[slug]/settings/templates/[id]/page.tsx` -- edit page pattern

### Architecture Decisions

- **Scrollable form, not stepper wizard**: All three sections (trigger, conditions, actions) are visible on one page. This matches the architecture doc and is simpler to implement than a multi-step wizard. Sections are collapsible for space management.
- **Two slices for create/edit**: 286A covers the page layout + trigger + conditions. 286B covers the action list + action forms + variable insertion. This split keeps each slice under 8 new files and avoids one massive slice.
- **Actions saved individually in edit mode**: In edit mode, actions are managed via individual API calls (add, update, remove, reorder). In create mode, actions are submitted together with the rule. This matches the API design (nested action endpoints under rule).

---

## Epic 287: Frontend -- Execution Log & Dashboard Widget

**Goal**: Build the execution log views (per-rule tab and global page), execution detail sheet, and the dashboard automation widget.

**References**: Architecture doc Sections 37.10.3 (rule detail page), 37.10.4 (global execution log), 37.10.5 (dashboard widget).

**Dependencies**: Epic 285 (navigation structure, API client).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **287A** | 287.1--287.8 | Execution log tab on rule detail page + settings/automations/executions/page.tsx (global log) + ExecutionLog DataTable + ExecutionDetail Sheet + AutomationsWidget (dashboard card) + dashboard integration + tests (~8 tests). ~7 new files + ~1 modified file. Frontend only. | **Done** (PR #567) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 287.1 | Create ExecutionLog component | 287A | | New file: `frontend/components/automations/execution-log.tsx`. "use client". DataTable with columns: Rule Name, Trigger Type (Badge), Triggered At (relative), Status (ExecutionStatusBadge from 285A), Conditions Met (check/x icon), Actions (count), Duration. Row click opens ExecutionDetail Sheet. Filters: Rule Select (optional, for global view), Status Select, Date Range. |
| 287.2 | Create ExecutionDetail component | 287A | | New file: `frontend/components/automations/execution-detail.tsx`. "use client". Shadcn Sheet. Shows: trigger event type, trigger event data (JSON viewer or key-value), conditions met, overall status. Per-action list: action type, status Badge, scheduled_for (if delayed), executed_at, result_data, error_message + error_detail (collapsible). |
| 287.3 | Create global execution log page | 287A | 287.1 | New file: `frontend/app/(app)/org/[slug]/settings/automations/executions/page.tsx`. Server component. Fetches executions via API. Renders ExecutionLog with all filters enabled. Breadcrumbs: Settings > Automations > Execution Log. |
| 287.4 | Wire execution log tab into rule detail page | 287A | 287.1 | Modify: `frontend/app/(app)/org/[slug]/settings/automations/[id]/page.tsx`. Add "Execution Log" tab (Shadcn Tabs). Tab content: ExecutionLog component filtered by ruleId. |
| 287.5 | Create AutomationsWidget component | 287A | | New file: `frontend/components/automations/automations-widget.tsx`. Card showing: "Automations" heading, active rules count, "X executions today" (succeeded/failed). If any failures today: red Badge with click-through to execution log filtered by status=FAILED. Only visible to admin/owner roles. |
| 287.6 | Add AutomationsWidget to dashboard | 287A | 287.5 | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. Add AutomationsWidget to the dashboard grid. Conditionally render for admin/owner roles. Fetch automation summary data (active rules count, today's executions). |
| 287.7 | Add "Execution Log" link to automations settings nav | 287A | | Small addition to the automations page or settings nav: link to /settings/automations/executions. Could be a secondary nav item or a link on the automations page header. |
| 287.8 | Write execution log + widget tests | 287A | 287.1, 287.5 | New file: `frontend/__tests__/automations/execution-log.test.tsx`. Tests (~8): execution log renders rows, status badges correct, row click opens detail sheet, detail sheet shows trigger event data, detail sheet shows per-action results, filters change displayed data, dashboard widget renders counts, widget shows failure badge when failures present. |

### Key Files

**Slice 287A -- Create:**
- `frontend/components/automations/execution-log.tsx`
- `frontend/components/automations/execution-detail.tsx`
- `frontend/components/automations/automations-widget.tsx`
- `frontend/app/(app)/org/[slug]/settings/automations/executions/page.tsx`
- `frontend/__tests__/automations/execution-log.test.tsx`

**Slice 287A -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/automations/[id]/page.tsx` -- add Execution Log tab
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` -- add AutomationsWidget

**Read for context:**
- `frontend/components/ui/sheet.tsx` -- Sheet component
- `frontend/components/ui/tabs.tsx` -- Tabs component
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` -- dashboard widget pattern

### Architecture Decisions

- **Execution log as both tab and global page**: Per-rule execution log (filtered by ruleId) lives as a tab on the rule detail page. Global execution log (all rules) is a separate page. Both use the same ExecutionLog component with different filter defaults.
- **Dashboard widget is admin/owner only**: Consistent with the RBAC model -- members cannot access automation features. The widget is conditionally rendered based on the user's role.

---

## Epic 288: End-to-End Integration Tests & Edge Cases

**Goal**: Comprehensive end-to-end integration tests covering cross-cutting scenarios not covered by individual slice tests: full event-to-execution flow, multi-rule evaluation, cycle detection end-to-end, delayed action lifecycle, template activation + execution, error recovery, and rule lifecycle effects on scheduled actions.

**References**: Architecture doc Section 37.11 Slice 37H.

**Dependencies**: Epics 283, 284 (full engine + API).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **288A** | 288.1--288.8 | End-to-end integration tests covering: full event->execution flow, multi-rule evaluation, condition edge cases, cycle detection end-to-end, delayed action lifecycle, template activation + execution, error recovery, rule lifecycle effects. ~15 integration tests. ~1-2 test files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 288.1 | End-to-end: domain event -> rule evaluation -> condition check -> action execution -> execution log | 288A | | Test: publish a TaskStatusChangedEvent, verify rule matches, conditions evaluated, CreateTask action executed, task exists in DB, AutomationExecution + ActionExecution records created with correct data. |
| 288.2 | Multi-rule test: same event triggers multiple rules | 288A | | Test: create 2 rules with same triggerType (TASK_STATUS_CHANGED). Publish event. Verify both rules evaluate (2 AutomationExecution records). Verify actions from both rules execute independently. |
| 288.3 | Condition edge cases | 288A | | Tests: null field value with IS_NULL -> true, null field with EQUALS -> false, GREATER_THAN on non-numeric -> false, IN with matching value -> true, unknown field path -> false, empty conditions list -> true (no filtering). |
| 288.4 | Cycle detection end-to-end | 288A | | Test: rule triggers on TASK_STATUS_CHANGED, action = CREATE_TASK. Publish task event. New task creation publishes another TaskStatusChangedEvent with automationExecutionId set. Verify listener skips the second event (no second execution record). |
| 288.5 | Delayed action end-to-end | 288A | | Test: rule with delayed action (e.g., SEND_NOTIFICATION after 1 minute). Publish event. Verify ActionExecution created with status=SCHEDULED and correct scheduledFor. Manually advance time or directly invoke scheduler. Verify action executes and status -> COMPLETED. |
| 288.6 | Template activation + execution end-to-end | 288A | | Test: activate "task-completion-chain" template. Verify rule created with source=TEMPLATE. Publish matching event (TaskStatusChangedEvent with toStatus=COMPLETED). Verify rule fires and action executes. |
| 288.7 | Error recovery: action failure does not block subsequent actions or outer transaction | 288A | | Test: rule with 3 actions -- action 2 throws exception. Verify action 1 executed (COMPLETED), action 2 failed (FAILED with error), action 3 still executed (COMPLETED). Overall execution = ACTIONS_FAILED. AUTOMATION_ACTION_FAILED notification sent. Original domain operation (task status change) is NOT rolled back. |
| 288.8 | Rule lifecycle effects on scheduled actions | 288A | | Tests: (1) Delete rule with SCHEDULED actions -> actions marked CANCELLED. (2) Disable rule -> scheduled actions marked CANCELLED on scheduler run. (3) Toggle rule off then on -> new events trigger rule again. |

### Key Files

**Slice 288A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEndToEndTest.java`

**Slice 288A -- Read for context:**
- All automation package files (entity, service, listener, executors, scheduler)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- verify task creation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationRepository.java` -- verify notifications

### Architecture Decisions

- **Separate E2E test epic**: These tests exercise cross-cutting scenarios that span multiple slices. Running them after the full engine is assembled ensures integration correctness.
- **Scheduler invocation in tests**: For delayed action tests, the scheduler can be invoked directly (call the @Scheduled method) rather than waiting for the 15-minute interval. This keeps tests fast and deterministic.
- **Error recovery verification**: The test explicitly verifies that the outer transaction (the domain operation that published the event) is not rolled back by automation failures. This is the most critical safety property of the engine.

---

### Critical Files for Implementation
List 3-5 files most critical for implementing this plan:
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase37-workflow-automations.md` - Full architecture specification with entity models, API surface, sequence diagrams, and implementation guidance
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` - Sealed interface that must be modified for cycle detection (automationExecutionId default method + new permit entries)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` - Pattern to follow for AutomationScheduler (per-tenant iteration, ScopedValue binding, TransactionTemplate)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` - Pattern to follow for AutomationTemplateSeeder (classpath JSON loading, OrgSettings idempotency)
- `/Users/rakheendama/Projects/2026/b2b-strawman/tasks/phase34-client-information-requests.md` - Reference for task file format conventions (epic structure, slice naming, task ID patterns)