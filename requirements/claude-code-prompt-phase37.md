# Phase 37 — Workflow Automations v1

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 36, the platform has:

- **Domain events (Phase 6/6.5)**: Application-level domain events for all major entity lifecycle transitions (task, project, customer, invoice, document, comment, time entry, budget, proposal, information request). Events are dispatched synchronously via Spring's `ApplicationEventPublisher` and consumed by notification handlers, audit writers, portal sync handlers, and activity feed formatters.
- **Notification system (Phase 6.5)**: In-app + email channels, per-member preferences, type-driven routing via `NotificationHandler` implementations. `EmailNotificationChannel` with Thymeleaf templates and delivery logging.
- **Audit events (Phase 6)**: `AuditEvent` entity with JSONB details covering all domain actions. Queryable API with filters.
- **Activity feed (Phase 6.5)**: Project-scoped activity tab, powered by audit events + comments. `ActivityFeedService` with `ActivityFormatter` implementations.
- **Scheduled jobs**: `TimeReminderScheduler` and `RequestReminderScheduler` patterns — per-tenant iteration via `OrgSchemaMappingRepository`, `ScopedValue` binding, 15-min/6-hour polling, error-isolated per tenant.
- **Project templates (Phase 16)**: `ProjectTemplate` with task/document template snapshots, request template references. Used by proposals and recurring schedules for project instantiation.
- **Customer lifecycle (Phase 14)**: Customer status machine (PROSPECT → ONBOARDING → ACTIVE → INACTIVE → ARCHIVED) with `CustomerLifecycleGuard` blocking actions for invalid states. Compliance checklists with auto-transition on completion.
- **Task lifecycle (Phase 4/5)**: Task status (OPEN → IN_PROGRESS → COMPLETED → ARCHIVED), assignment, claim/release, time tracking.
- **Invoice lifecycle (Phase 10)**: Invoice status (DRAFT → SENT → OVERDUE → PAID → CANCELLED → VOID) with `InvoiceNumberService`.
- **Project lifecycle**: Project status (PLANNING → ACTIVE → ON_HOLD → COMPLETED → ARCHIVED).
- **Budget tracking (Phase 8)**: `ProjectBudget` with hours/currency budgets, consumption tracking, threshold alerts (50%, 75%, 90%, 100%).
- **Proposal pipeline (Phase 32)**: Proposal acceptance auto-creates projects, sets up billing, assigns team — an existing example of "event-driven orchestration."
- **OrgSettings (Phase 8)**: Org-scoped configuration entity with extensible columns (default currency, billing rates, reminder intervals, etc.).
- **Email delivery (Phase 24)**: SMTP + SendGrid BYOAK, Thymeleaf templates, delivery logging, unsubscribe support, rate limiting.

**Gap**: Every status change, task assignment, follow-up notification, and project setup step is currently manual or hard-coded into specific domain services (e.g., proposal acceptance orchestration, checklist auto-transition). Firms have repeatable patterns — "when a task is completed, create the next task," "when an invoice is overdue 30 days, email the client and notify the account manager," "when all tasks are done, mark the project complete" — but there's no way to express these rules without code changes. The platform has the sensory nervous system (domain events, notifications, audit) but lacks the motor nervous system (configurable reactions to events).

## Objective

Build a **rule-based automation engine** that allows firm admins to:

1. **Define automation rules** — trigger (when something happens) + conditions (optional filters) + actions (do one or more things). Configured via forms, not code.
2. **Choose from 8 trigger types** — covering the major entity lifecycle transitions already emitting domain events.
3. **Apply conditions** — filter when a rule should fire based on entity attributes (e.g., "only when project type = Tax Return", "only when invoice amount > 10000").
4. **Execute 6 action types** — create tasks, send notifications, send emails, update entity statuses, create projects from templates, assign members.
5. **Schedule delayed actions** — "wait 3 days then send reminder" with a polling scheduler for deferred execution.
6. **Monitor executions** — full audit trail of every trigger event, condition evaluation, and action execution. Queryable by admins.
7. **Start from templates** — 5-8 pre-built automation templates seeded per tenant that firms can activate and customize.

This phase wires existing features together — the automation engine consumes the same domain events that already power notifications and audit, and invokes the same services that already handle task creation, status updates, email delivery, and project instantiation.

## Constraints & Assumptions

- **Linear rules only** — no branching (if-else paths), no loops, no parallel execution paths. A rule is: trigger → evaluate conditions → execute actions sequentially. Branching/visual workflows are v2.
- **Tenant-scoped** — all rules, executions, and scheduled actions are scoped to the tenant schema. No cross-tenant or platform-level automations.
- **Domain event integration** — the automation engine subscribes to existing domain events via a single `AutomationEventListener` Spring component. No new event types needed; rules declare which event type they listen to.
- **JSONB configuration** — trigger config, conditions, and action config stored as JSONB. Validated at the application layer (Java sealed classes for type safety, TypeScript discriminated unions on frontend). Same pattern as custom field values (Phase 11, ADR-052).
- **Delayed actions use polling** — `AutomationScheduler` polls every 15 minutes for due `ScheduledAction` records. Reuses the `TimeReminderScheduler` per-tenant iteration pattern. No message queue or cron expressions.
- **Actions execute as system** — automated actions are performed as the "system" actor, not impersonating a specific member. Audit events record `actor_type: AUTOMATION` with a reference to the rule.
- **Error handling: log + notify** — failed actions are logged in `AutomationExecution` with error detail and stack trace. The org owner/admin receives an in-app notification. The rule stays active (no auto-disable). Repeated failures are visible in the execution log.
- **No rate limiting in v1** — at target firm sizes (2-50 members), runaway automation loops are unlikely. If a rule triggers itself (e.g., "on task created → create task"), the engine detects the cycle by checking if the triggering event originated from an automation execution and skips re-trigger. Simple cycle detection, not general rate limiting.
- **Templates are mutable copies** — when a firm activates a seeded automation template, it creates a full copy of the rule (not a reference). The firm owns it and can modify freely. Template updates don't propagate.

---

## Section 1 — AutomationRule Entity & Data Model

### AutomationRule

The core configuration entity. Defines when and how an automation fires.

```
AutomationRule:
  id                  UUID (PK)
  name                String (not null, max 200) — e.g., "Task Completion Chain"
  description         String (nullable, max 1000)
  enabled             Boolean (not null, default true)
  triggerType         TriggerType (enum, not null) — see trigger types below
  triggerConfig       JSONB (not null) — trigger-specific configuration
  conditions          JSONB (nullable) — array of condition objects
  source              RuleSource (TEMPLATE, CUSTOM) — template = created from seeded template
  templateSlug        String (nullable) — identifies which template this was created from
  createdBy           UUID (not null) — member who created
  createdAt           Timestamp
  updatedAt           Timestamp
```

### AutomationAction

Ordered list of actions to execute when a rule fires.

```
AutomationAction:
  id                  UUID (PK)
  ruleId              UUID (FK to AutomationRule, not null)
  sortOrder           Integer (not null) — execution sequence
  actionType          ActionType (enum, not null) — see action types below
  actionConfig        JSONB (not null) — action-specific configuration
  delayDuration       Integer (nullable) — delay amount (null = immediate)
  delayUnit           DelayUnit (nullable, enum: MINUTES, HOURS, DAYS) — delay unit
  createdAt           Timestamp
  updatedAt           Timestamp
```

### AutomationExecution

Audit trail for every rule evaluation and action execution.

```
AutomationExecution:
  id                  UUID (PK)
  ruleId              UUID (FK to AutomationRule, not null)
  triggerEventType    String (not null) — the domain event class name
  triggerEventData    JSONB (not null) — snapshot of the triggering event payload
  conditionsMet       Boolean (not null) — whether conditions evaluated to true
  status              ExecutionStatus (TRIGGERED, ACTIONS_COMPLETED, ACTIONS_FAILED, CONDITIONS_NOT_MET)
  startedAt           Timestamp (not null)
  completedAt         Timestamp (nullable)
  errorMessage        String (nullable) — summary of first failure
  createdAt           Timestamp
```

### ActionExecution

Per-action execution detail (child of AutomationExecution).

```
ActionExecution:
  id                  UUID (PK)
  executionId         UUID (FK to AutomationExecution, not null)
  actionId            UUID (FK to AutomationAction, not null)
  status              ActionExecutionStatus (PENDING, SCHEDULED, COMPLETED, FAILED, CANCELLED)
  scheduledFor        Timestamp (nullable) — for delayed actions
  executedAt          Timestamp (nullable)
  resultData          JSONB (nullable) — e.g., {"createdTaskId": "..."} for traceability
  errorMessage        String (nullable)
  errorDetail         String (nullable) — stack trace or detailed error
  createdAt           Timestamp
  updatedAt           Timestamp
```

### AutomationTemplate

Seeded template definitions (not a persistent entity — loaded from JSON seed files, used to create AutomationRule copies).

```
AutomationTemplate (seed data, not entity):
  slug                String — unique identifier, e.g., "overdue-invoice-reminder"
  name                String
  description         String
  triggerType         TriggerType
  triggerConfig       JSONB
  conditions          JSONB (nullable)
  actions             List of { actionType, actionConfig, delayDuration, delayUnit, sortOrder }
```

---

## Section 2 — Trigger Types

### Supported Triggers

| TriggerType | Domain Event | Config Fields |
|-------------|-------------|---------------|
| `TASK_STATUS_CHANGED` | `TaskStatusChangedEvent` | `fromStatus` (nullable), `toStatus` (nullable) — null means "any" |
| `PROJECT_STATUS_CHANGED` | `ProjectStatusChangedEvent` | `fromStatus` (nullable), `toStatus` (nullable) |
| `CUSTOMER_STATUS_CHANGED` | `CustomerStatusChangedEvent` | `fromStatus` (nullable), `toStatus` (nullable) |
| `INVOICE_STATUS_CHANGED` | `InvoiceStatusChangedEvent` | `fromStatus` (nullable), `toStatus` (nullable) |
| `TIME_ENTRY_CREATED` | `TimeEntryCreatedEvent` | (no config — fires on any time entry creation) |
| `BUDGET_THRESHOLD_REACHED` | `BudgetThresholdEvent` | `thresholdPercent` (Integer) — e.g., 80, 90, 100 |
| `DOCUMENT_ACCEPTED` | `DocumentAcceptedEvent` | (no config — fires on any document acceptance) |
| `INFORMATION_REQUEST_COMPLETED` | `InformationRequestCompletedEvent` | (no config — fires when all required items accepted) |

### Trigger Config Validation

Each trigger type has a corresponding Java sealed class for config validation:

```java
sealed interface TriggerConfig permits
    StatusChangeTriggerConfig,
    BudgetThresholdTriggerConfig,
    EmptyTriggerConfig {
}

record StatusChangeTriggerConfig(String fromStatus, String toStatus) implements TriggerConfig {}
record BudgetThresholdTriggerConfig(int thresholdPercent) implements TriggerConfig {}
record EmptyTriggerConfig() implements TriggerConfig {}
```

Jackson polymorphic deserialization from JSONB using `triggerType` as discriminator.

---

## Section 3 — Condition Engine

### Condition Model

Conditions are an array of field-based predicates evaluated against the trigger event context. All conditions must be true (AND logic). OR logic can be achieved by creating multiple rules.

```
Condition:
  field               String — dot-notation path, e.g., "project.status", "customer.name", "task.assigneeId"
  operator            ConditionOperator (EQUALS, NOT_EQUALS, IN, NOT_IN, GREATER_THAN, LESS_THAN, CONTAINS, IS_NULL, IS_NOT_NULL)
  value               Object — the comparison value (String, Number, List for IN/NOT_IN, null for IS_NULL/IS_NOT_NULL)
```

### Context Resolution

When a trigger fires, the automation engine builds an evaluation context from the triggering event:

```
Context for TASK_STATUS_CHANGED:
  task.id, task.name, task.status, task.previousStatus, task.assigneeId, task.projectId
  project.id, project.name, project.status, project.customerId
  customer.id, customer.name, customer.status

Context for INVOICE_STATUS_CHANGED:
  invoice.id, invoice.invoiceNumber, invoice.status, invoice.previousStatus, invoice.totalAmount, invoice.customerId
  customer.id, customer.name, customer.status

(similar for other trigger types — each provides the primary entity + its parent chain)
```

The `ConditionEvaluator` resolves field paths against this context map and applies operators. Unknown fields evaluate to false (fail-safe).

### Condition UI

Frontend renders conditions as a list of rows: [Field dropdown] [Operator dropdown] [Value input]. Field dropdown options are dynamically filtered based on the selected trigger type (only show fields available in that trigger's context).

---

## Section 4 — Action Types

### Supported Actions

| ActionType | Description | Config Fields |
|------------|-------------|---------------|
| `CREATE_TASK` | Creates a new task in the trigger's project | `taskName` (String), `taskDescription` (nullable String), `assignTo` (enum: TRIGGER_ACTOR, PROJECT_OWNER, SPECIFIC_MEMBER, UNASSIGNED), `specificMemberId` (nullable UUID), `taskStatus` (default OPEN) |
| `SEND_NOTIFICATION` | Sends an in-app notification | `recipientType` (enum: TRIGGER_ACTOR, PROJECT_OWNER, PROJECT_MEMBERS, SPECIFIC_MEMBER, ORG_ADMINS), `specificMemberId` (nullable UUID), `title` (String with variable substitution), `message` (String with variable substitution) |
| `SEND_EMAIL` | Sends an email to a portal contact or member | `recipientType` (enum: CUSTOMER_CONTACT, TRIGGER_ACTOR, PROJECT_OWNER, SPECIFIC_MEMBER), `specificMemberId` (nullable UUID), `subject` (String with variable substitution), `body` (String with variable substitution) |
| `UPDATE_STATUS` | Updates the status of the triggering entity or its parent | `targetEntity` (enum: TRIGGER_ENTITY, PROJECT, CUSTOMER), `newStatus` (String) |
| `CREATE_PROJECT` | Creates a project from a template | `projectTemplateId` (UUID), `assignToCustomer` (Boolean, default true — uses trigger's customer context) |
| `ASSIGN_MEMBER` | Assigns a member to the trigger's project | `memberId` (UUID), `role` (String, e.g., "CONTRIBUTOR") |

### Variable Substitution

Notification and email text fields support variable substitution using `{{variable}}` syntax:

```
Available variables (resolved from trigger context):
  {{task.name}}, {{task.status}}
  {{project.name}}, {{project.status}}
  {{customer.name}}
  {{invoice.invoiceNumber}}, {{invoice.totalAmount}}
  {{actor.name}} — the person/system that triggered the event
  {{rule.name}} — the automation rule name
```

`VariableResolver` maps variable names to context values. Unresolved variables render as-is (e.g., `{{unknown}}` stays literal).

### Action Config Validation

Each action type has a corresponding sealed class, same pattern as trigger configs:

```java
sealed interface ActionConfig permits
    CreateTaskActionConfig,
    SendNotificationActionConfig,
    SendEmailActionConfig,
    UpdateStatusActionConfig,
    CreateProjectActionConfig,
    AssignMemberActionConfig {
}
```

---

## Section 5 — Automation Engine (Backend Core)

### AutomationEventListener

Single Spring `@EventListener` component that bridges domain events to the automation engine:

```
1. Receive domain event (e.g., TaskStatusChangedEvent)
2. Check if event originated from an automation execution (cycle detection)
   - If yes: skip processing, log "cycle detected for rule X"
3. Query all enabled AutomationRules where triggerType matches the event type
4. For each matching rule:
   a. Evaluate triggerConfig against event (e.g., does toStatus match?)
   b. If trigger matches: build context, evaluate conditions
   c. If conditions met: create AutomationExecution, execute actions
   d. If conditions not met: create AutomationExecution with status CONDITIONS_NOT_MET
```

### Cycle Detection

Domain events fired by automation actions carry a metadata flag: `automationExecutionId`. The `AutomationEventListener` checks for this flag and skips processing if present. This prevents infinite loops (e.g., "on task created → create task → on task created → ...").

Implementation: Add an `automationExecutionId` field to domain event base class (nullable). When an action creates an entity, it passes the execution ID to the service, which includes it in the emitted event.

### Action Executor

`AutomationActionExecutor` dispatches to type-specific executors:

```java
public interface ActionExecutor {
    ActionType supportedType();
    ActionResult execute(ActionConfig config, AutomationContext context);
}

// Implementations:
CreateTaskActionExecutor    → delegates to TaskService.create()
SendNotificationActionExecutor → delegates to NotificationService.send()
SendEmailActionExecutor     → delegates to EmailNotificationChannel.send()
UpdateStatusActionExecutor  → delegates to entity-specific service.updateStatus()
CreateProjectActionExecutor → delegates to ProjectInstantiationService.create()
AssignMemberActionExecutor  → delegates to ProjectMemberService.addMember()
```

Each executor returns `ActionResult` (success + resultData, or failure + errorMessage).

### Delayed Action Flow

For actions with `delayDuration` set:

1. During execution, instead of invoking the executor immediately, create an `ActionExecution` with status `SCHEDULED` and `scheduledFor = now + delay`.
2. `AutomationScheduler` (new scheduled job) polls every 15 minutes:
   - Find all `ActionExecution` records with status `SCHEDULED` and `scheduledFor <= now()`
   - Per-tenant iteration (same pattern as `TimeReminderScheduler`)
   - Execute each action via `AutomationActionExecutor`
   - Update status to `COMPLETED` or `FAILED`
3. If the parent rule has been disabled or deleted since scheduling, mark as `CANCELLED`.

---

## Section 6 — Automation Templates (Seed Data)

### Template Pack

Ship with 6 pre-built automation templates, seeded per tenant via `AutomationTemplateSeeder` (same pattern as `CompliancePackSeeder`, `FieldPackSeeder`):

| Template Slug | Trigger | Conditions | Actions |
|---------------|---------|------------|---------|
| `overdue-invoice-reminder` | INVOICE_STATUS_CHANGED (toStatus: OVERDUE) | — | Send email to customer contact ("Your invoice {{invoice.invoiceNumber}} is overdue"), delay 7 days + send notification to project owner |
| `task-completion-notify` | TASK_STATUS_CHANGED (toStatus: COMPLETED) | — | Send notification to project owner ("{{task.name}} completed by {{actor.name}}") |
| `budget-alert-90` | BUDGET_THRESHOLD_REACHED (90%) | — | Send notification to project owner ("Project {{project.name}} has reached 90% of budget") |
| `project-completion-check` | TASK_STATUS_CHANGED (toStatus: COMPLETED) | — | Send notification to project owner ("{{task.name}} completed — check if all tasks are done") |
| `new-customer-onboarding` | CUSTOMER_STATUS_CHANGED (toStatus: ACTIVE) | — | Send notification to org admins ("{{customer.name}} is now active — consider creating an engagement") |
| `request-completed-notify` | INFORMATION_REQUEST_COMPLETED | — | Send notification to project members ("All items received for {{customer.name}}") |

Templates are **not auto-enabled**. Firms browse the template gallery in the UI and click "Activate" to create a mutable copy.

### Seeder Behavior

- `AutomationTemplateSeeder` runs on tenant provisioning (new tenants) and as a one-time migration for existing tenants.
- Templates are stored as JSON definition files in `src/main/resources/automation-templates/`.
- Seeder does NOT create AutomationRule records — it only provides the template definitions. The UI reads template definitions from the seeder and creates rules on activation.
- Idempotent: template definitions can be updated in code without affecting already-activated rules.

---

## Section 7 — Backend API

### Automation Rule API

```
GET    /api/automation-rules                        — List all rules (filter: enabled, triggerType)
POST   /api/automation-rules                        — Create rule
GET    /api/automation-rules/{id}                   — Get rule with actions
PUT    /api/automation-rules/{id}                   — Update rule (name, description, enabled, triggerConfig, conditions)
DELETE /api/automation-rules/{id}                   — Delete rule (cascades to actions, cancels scheduled actions)
POST   /api/automation-rules/{id}/toggle            — Enable/disable rule
POST   /api/automation-rules/{id}/duplicate         — Clone rule
POST   /api/automation-rules/{id}/test              — Dry-run: evaluate conditions against a sample event, return what would happen (no side effects)
```

### Automation Action API (nested under rule)

```
POST   /api/automation-rules/{id}/actions           — Add action to rule
PUT    /api/automation-rules/{id}/actions/{actionId} — Update action
DELETE /api/automation-rules/{id}/actions/{actionId} — Remove action
PUT    /api/automation-rules/{id}/actions/reorder    — Reorder actions (accepts sorted list of action IDs)
```

### Automation Template API

```
GET    /api/automation-templates                    — List available templates (from seed data)
POST   /api/automation-templates/{slug}/activate    — Create a rule from template (returns the new AutomationRule)
```

### Execution Log API

```
GET    /api/automation-executions                   — List executions (filter: ruleId, status, dateRange)
GET    /api/automation-executions/{id}              — Get execution with action details
GET    /api/automation-rules/{id}/executions        — Executions for a specific rule
```

### Access Control

- Rule CRUD: `org:admin` and `org:owner` roles only. Members cannot create or modify automations.
- Execution log read: `org:admin` and `org:owner` roles.
- Template browsing: `org:admin` and `org:owner` roles.

---

## Section 8 — Frontend

### Automations Settings Page (Settings → Automations)

New top-level settings page, accessible to admins/owners.

**Rule List View:**
- Table: name, trigger type (badge), enabled toggle, last triggered (timestamp), execution count, status (last execution status icon)
- "New Automation" button → opens create wizard
- "Browse Templates" button → opens template gallery
- Row click → opens rule detail/edit

**Template Gallery (modal or side panel):**
- Grid/list of seeded automation templates
- Each shows: name, description, trigger type badge, action summary
- "Activate" button → creates a mutable copy, opens edit view for customization
- Already-activated templates show "Activated" badge (match by templateSlug)

### Rule Create/Edit Wizard

Three-step form (not a literal wizard stepper — can be a single scrollable page with sections):

**Step 1 — Trigger:**
- Trigger type dropdown (8 options with descriptions)
- Trigger-specific config fields render dynamically based on selection:
  - Status change triggers: "From Status" and "To Status" dropdowns (options loaded from the relevant enum, plus "Any")
  - Budget threshold: number input (percent)
  - Simple triggers (time entry, document accepted, request completed): no additional config

**Step 2 — Conditions (optional):**
- "Add Condition" button → adds a condition row
- Each row: [Field dropdown] [Operator dropdown] [Value input]
- Field dropdown options filtered by trigger type (shows relevant fields from that trigger's context)
- Operator dropdown filtered by field type (string fields: EQUALS, NOT_EQUALS, CONTAINS, IN; number fields: EQUALS, GREATER_THAN, LESS_THAN; nullable fields: IS_NULL, IS_NOT_NULL)
- Remove button per row
- Helper text: "All conditions must be true (AND logic). For OR logic, create separate rules."

**Step 3 — Actions:**
- Ordered list of actions (drag to reorder or up/down buttons)
- "Add Action" button → action type dropdown
- Per-action config fields render dynamically:
  - CREATE_TASK: task name, description, assign-to dropdown
  - SEND_NOTIFICATION: recipient type dropdown, title input, message textarea (with variable chip insertion)
  - SEND_EMAIL: recipient type, subject, body (with variable chip insertion)
  - UPDATE_STATUS: target entity dropdown, new status dropdown
  - CREATE_PROJECT: project template dropdown
  - ASSIGN_MEMBER: member search/select, role dropdown
- Delay toggle per action: "Execute immediately" / "Delay by [number] [minutes/hours/days]"
- Remove button per action

**Variable insertion:**
- In text fields (notification title/message, email subject/body): a "Insert Variable" button or `{{` trigger that shows a dropdown of available variables based on the trigger type
- Variables render as styled chips in the input (similar to mention chips)

### Rule Detail Page

- Header: rule name, enabled toggle, trigger type badge, created by, created date
- Edit button → reopens wizard in edit mode
- "Test Rule" button → dry-run modal (select a sample entity, see what conditions evaluate to and what actions would fire)
- Execution Log tab: table of recent executions
  - Columns: triggered at, status (badge), conditions met, actions executed, duration
  - Click → execution detail (shows each action's status, result data, error if any)
  - Filterable by status, date range
- "Duplicate" and "Delete" actions in dropdown menu

### Execution Log Page (Settings → Automations → Execution Log)

Global execution log across all rules:
- Table: rule name, trigger type, triggered at, status, actions (count), duration
- Filters: rule, status, date range
- Click → execution detail
- Useful for debugging "what fired when?" across all automations

### Dashboard Integration

- Company dashboard widget: "Automations"
  - Active rules count
  - Executions today (succeeded / failed)
  - Click-through to execution log if any failures

---

## Section 9 — Notifications & Audit

### Notification Types (New)

| Event | Recipient | Channel |
|-------|-----------|---------|
| Automation action failed | Org admins/owners | In-app |
| Automation rule created | Audit only (no notification) | — |
| Automation rule enabled/disabled | Audit only | — |

### Audit Events

- AUTOMATION_RULE_CREATED, AUTOMATION_RULE_UPDATED, AUTOMATION_RULE_DELETED
- AUTOMATION_RULE_ENABLED, AUTOMATION_RULE_DISABLED
- AUTOMATION_EXECUTED (details include: rule name, trigger event summary, conditions met, action count, status)
- AUTOMATION_ACTION_FAILED (details include: rule name, action type, error message)

All include standard audit fields (actor, timestamp, entity references, JSONB details). Automated actions record `actor_type: AUTOMATION` in their own audit events.

---

## Out of Scope

- **Visual workflow builder** — drag-and-drop canvas with branching, delays, loops. Phase 37 is structured rules; visual builder is v2.
- **Branching logic (if-else)** — rules are linear. No conditional branching within a single rule.
- **OR conditions** — all conditions are AND. Use multiple rules for OR logic.
- **Webhook-out as action type** — needs integration ports from Phase 21. Future action type.
- **Cross-tenant automations** — no platform-level or multi-tenant rules.
- **Rate limiting / concurrency controls** — unnecessary at target firm sizes. Cycle detection covers the dangerous case.
- **Undo/rollback of executed actions** — actions are one-way. Users can manually reverse if needed.
- **Custom trigger types** — the 8 supported triggers cover the existing domain events. Custom/webhook-in triggers are future.
- **Action chaining across rules** — rule A's action triggers rule B. Explicitly prevented by cycle detection in v1.
- **Execution retention/cleanup** — no auto-purge of old execution records. Future phase can add retention policies.

## ADR Topics

1. **Rule engine vs. visual workflow**: Why a structured rules engine (trigger → conditions → actions) over a visual workflow builder for v1. Trade-offs: simplicity of implementation and UX vs. expressiveness. Rules cover 80% of use cases; visual builder adds complexity for the remaining 20%.
2. **Cycle detection strategy**: How to prevent infinite loops when automations trigger events that match other automations. Decision: metadata flag on domain events (`automationExecutionId`) checked by the event listener. Simple, reliable, no performance overhead.
3. **Delayed action scheduling**: Polling (15-min interval) vs. message queue vs. database-backed scheduler. Decision: polling reuses the proven `TimeReminderScheduler` pattern, requires no new infrastructure, and 15-min granularity is sufficient for business process automation.
4. **JSONB config vs. normalized tables**: Why trigger config, conditions, and action config are JSONB rather than normalized relational tables. Decision: JSONB provides schema flexibility for diverse trigger/action types while keeping the entity model simple. Application-layer validation via sealed classes ensures type safety.
5. **Execution logging granularity**: Per-rule execution vs. per-action execution. Decision: both — `AutomationExecution` for the rule-level view, `ActionExecution` for per-action detail. Enables both "did the rule fire?" and "which action failed?" debugging.

## Style & Boundaries

- Follow existing entity patterns: Spring Boot entity + JpaRepository + Service + Controller
- Domain event integration via `@EventListener` on existing event types — no new event infrastructure
- Sealed classes/interfaces for type-safe JSONB config deserialization (Java 25 sealed types)
- Action executors delegate to existing services (TaskService, NotificationService, EmailNotificationChannel, ProjectInstantiationService, ProjectMemberService) — no business logic duplication
- Scheduler follows TimeReminderScheduler pattern (per-tenant iteration, ScopedValue binding, error isolation, 15-min polling)
- Frontend follows existing settings page patterns (Shadcn UI, server actions, dynamic form rendering)
- Template seeder follows existing pack seeder pattern (JSON definition files, idempotent)
- Access control: admin/owner only for rule management, consistent with other settings pages
- Migration: adds automation_rules, automation_actions, automation_executions, action_executions tables. Single tenant migration file.
- Test coverage: integration tests for trigger matching, condition evaluation, action execution, cycle detection, delayed action scheduling, template activation, error handling
