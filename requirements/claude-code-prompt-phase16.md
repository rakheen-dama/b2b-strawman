You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with dedicated schema-per-tenant isolation (Phase 13 eliminated shared schema).
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups and "My Work" cross-project dashboard.
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Customer portal backend** (Phase 7): magic links, read-model schema, portal contacts, portal APIs.
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate` (3-level hierarchy: org-default -> project-override -> customer-override), `CostRate`, `ProjectBudget`, `OrgSettings` (default currency). Time entries have `billable` flag, `billing_rate_snapshot`, `cost_rate_snapshot`. Profitability reports.
- **Operational dashboards** (Phase 9): company dashboard, project overview, personal dashboard, health scoring.
- **Invoicing & billing from time** (Phase 10): Invoice/InvoiceLine entities, draft-to-paid lifecycle, unbilled time management, PSP adapter seam, HTML invoice preview via Thymeleaf.
- **Tags, custom fields & views** (Phase 11): `FieldDefinition`, `FieldGroup`, `Tag`, `EntityTag`, `SavedView` entities. JSONB custom field values on projects, tasks, and customers. Platform-shipped field packs with per-tenant seeding. Saved filtered views with custom column selection.
- **Document templates & PDF generation** (Phase 12): `DocumentTemplate`, `GeneratedDocument` entities. Thymeleaf + OpenHTMLToPDF rendering pipeline. Template packs (seed data). Org branding (logo, brand color, footer text on OrgSettings).
- **Customer compliance & lifecycle** (Phase 14): Customer lifecycle state machine (Prospect → Onboarding → Active → Dormant → Offboarded), checklist engine (templates + instances + items), compliance packs, data subject requests, retention policies.
- **Contextual actions & setup guidance** (Phase 15): Setup status aggregation, contextual action cards on entity detail pages, empty state guidance.

For **Phase 16**, I want to add **Project Templates & Recurring Schedules** — a reusable project blueprint system with optional automated scheduling that eliminates repetitive manual project creation for firms with recurring engagements.

***

## Objective of Phase 16

Design and specify:

1. **Project templates** — a `ProjectTemplate` entity that captures a reusable project blueprint (name pattern, description, task definitions with ordering and role-based assignment defaults, tags, billable flag). Users can save any existing project as a template, and create new projects from templates with a customization dialog.
2. **Template tasks** — a `TemplateTask` entity that stores task definitions within a template, including name, description, estimated hours, sort order, billable flag, and an assignee role hint (project_lead, any_member, or unassigned). At instantiation time, tasks are created from the template and auto-assigned based on the role mapping.
3. **Template management UI** — a settings page for listing, editing, duplicating, and deleting templates. A "Save as Template" action on existing projects. A "New from Template" entry point on the project list.
4. **Recurring schedules** — a `RecurringSchedule` entity linking a template to a customer with a frequency (weekly, fortnightly, monthly, quarterly, semi-annually, annually), start/end dates, lead time, and name pattern tokens (`{customer}`, `{month}`, `{year}`, `{period_start}`, `{period_end}`).
5. **Scheduler execution** — a daily scheduled job that checks for due schedules and auto-creates projects from templates. Idempotent (tracks last execution per schedule). Creates projects with resolved name tokens, linked to the correct customer, with tasks auto-assigned by role.
6. **Schedule management UI** — a page listing active/paused/completed schedules with create, edit, pause, resume, and execution history. Accessible from the sidebar.
7. **Audit and notification integration** — audit events for template and schedule CRUD operations, notifications when recurring projects are auto-created.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (dedicated schema per tenant).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - External scheduling libraries (Quartz, etc.) — use Spring's `@Scheduled` with a simple daily cron. The platform already uses this pattern for other periodic checks.
    - Complex CRON expressions — frequency is a bounded enum, not a freeform cron string.
    - Workflow/BPM engines — the scheduling logic is a simple "check if due, create if so" loop.
- **Templates are snapshots, not references.** When a project is created from a template, tasks are copied. If the template is later updated, existing projects are not affected. This is the same principle used for checklist templates (Phase 14).
- **Role-based assignment, not member-based.** Templates store an assignee role hint (e.g., "project_lead"), not a specific member UUID. At instantiation time, the system resolves the role to the actual member assigned to that role on the project. If no member matches, the task is left unassigned.

2. **Tenancy**

- All new entities (`ProjectTemplate`, `TemplateTask`, `RecurringSchedule`, `ScheduleExecution`) follow the dedicated-schema-per-tenant model (Phase 13). No `tenant_id` column needed — schema boundary provides isolation.
- Flyway migration for tenant schemas (not public).
- The scheduler job must iterate over all tenant schemas when checking for due schedules. Use the existing `TenantMigrationRunner` pattern to discover active schemas.

3. **Permissions model**

- **Template management** (create, edit, delete, duplicate templates):
    - Org admins and owners only.
- **Save project as template**:
    - Org admins, owners, and the project lead of the source project.
- **Create project from template** (manual instantiation):
    - Org admins, owners, and any member with project creation permission (same as regular project creation).
- **Schedule management** (create, edit, pause, resume, delete schedules):
    - Org admins and owners only. Schedules automate project creation — this is an admin-level configuration.
- **View templates and schedules**:
    - All org members can view the template list and schedule list (read-only). Only admins/owners can modify.

4. **Relationship to existing entities**

- **Project**: Templates capture a project's structure. The "Save as Template" action reads the project's tasks and creates a template from them. The "Create from Template" action creates a new Project with Tasks, optionally linked to a Customer.
- **Task**: Template tasks map 1:1 to real tasks at instantiation. Fields captured: name, description, estimated hours, sort order, billable flag, assignee role.
- **Customer**: Recurring schedules are linked to a customer. Each execution creates a project linked to that customer. The customer's lifecycle status is checked — if the customer is `OFFBOARDED` or `PROSPECT`, the schedule is skipped (with a log entry) rather than creating a project for an inactive customer.
- **Tags** (Phase 11): Templates can store a set of tag IDs. When a project is created from a template, these tags are applied to the new project.
- **AuditEvent**: All template CRUD, schedule CRUD, and schedule executions are audited.
- **Notification**: When a recurring schedule creates a project, a notification is sent to the project lead (if one is auto-assigned) and to the org admins.
- **Activity Feed**: Schedule-created projects appear in the customer's and project's activity feed with an indicator that they were auto-created.

5. **Out of scope for Phase 16**

- Retainer agreements, hour banks, rollover logic, retainer billing (Phase 17).
- Template marketplace / sharing across orgs.
- Template versioning (maintaining a history of template changes).
- Resource capacity checks before auto-creation (resource planning is a separate domain).
- Complex CRON expressions or timezone-aware scheduling (all schedules use the org's timezone, stored on OrgSettings).
- Recurring tasks within a project (tasks that repeat on their own schedule). This phase handles recurring projects, not recurring tasks.
- Budget auto-configuration from templates (budgets are set per-project after creation).

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase16-project-templates-recurring-schedules.md`, plus ADRs for key decisions.

### 1. ProjectTemplate entity

Design the **template** data model:

1. **ProjectTemplate**

    - `id` (UUID, PK).
    - `name` (VARCHAR(300) — the template's display name, e.g., "Monthly Bookkeeping").
    - `name_pattern` (VARCHAR(300) — the pattern used when creating projects, e.g., "Monthly Bookkeeping — {customer} — {month} {year}"). Supports tokens: `{customer}` (customer name), `{month}` (full month name), `{month_short}` (3-letter month), `{year}` (4-digit year), `{period_start}` (start date formatted), `{period_end}` (end date formatted).
    - `description` (TEXT, nullable — template description, also used as the default project description).
    - `billable_default` (BOOLEAN, default true — default billable flag for tasks created from this template).
    - `source` (VARCHAR(20) — `MANUAL` for templates created by users, `FROM_PROJECT` for templates saved from existing projects).
    - `source_project_id` (UUID, nullable — if source is `FROM_PROJECT`, the project this template was created from. Informational only, not a FK — the source project may be deleted).
    - `active` (BOOLEAN, default true — inactive templates don't appear in the "New from Template" picker).
    - `created_by` (UUID — member who created the template).
    - `created_at`, `updated_at`.

2. **TemplateTask**

    - `id` (UUID, PK).
    - `template_id` (UUID, FK -> project_templates, ON DELETE CASCADE).
    - `name` (VARCHAR(300) — task name).
    - `description` (TEXT, nullable — task description).
    - `estimated_hours` (DECIMAL(10,2), nullable — estimated hours for this task).
    - `sort_order` (INTEGER — display and creation order within the project).
    - `billable` (BOOLEAN, default true).
    - `assignee_role` (VARCHAR(20), default 'UNASSIGNED' — one of: `PROJECT_LEAD`, `ANY_MEMBER`, `UNASSIGNED`).
        - `PROJECT_LEAD`: assigned to whoever is set as the project lead at instantiation.
        - `ANY_MEMBER`: left for the first available member (not auto-assigned; just flagged as "needs assignment").
        - `UNASSIGNED`: created with no assignee.
    - `created_at`, `updated_at`.
    - Constraint: `(template_id, sort_order)` unique.

3. **TemplateTag** (join table)

    - `template_id` (UUID, FK -> project_templates, ON DELETE CASCADE).
    - `tag_id` (UUID, FK -> tags, ON DELETE CASCADE).
    - PK: `(template_id, tag_id)`.

### 2. RecurringSchedule entity

Design the **schedule** data model:

1. **RecurringSchedule**

    - `id` (UUID, PK).
    - `template_id` (UUID, FK -> project_templates — which template to instantiate).
    - `customer_id` (UUID, FK -> customers — which customer the recurring projects are for).
    - `name_override` (VARCHAR(300), nullable — if set, overrides the template's `name_pattern` for this schedule. Useful when the same template is used for different customers with different naming).
    - `frequency` (VARCHAR(20), NOT NULL — one of: `WEEKLY`, `FORTNIGHTLY`, `MONTHLY`, `QUARTERLY`, `SEMI_ANNUALLY`, `ANNUALLY`).
    - `start_date` (DATE, NOT NULL — first period start date. The schedule calculates future periods from this anchor).
    - `end_date` (DATE, nullable — if set, the schedule stops creating projects after this date).
    - `lead_time_days` (INTEGER, default 0 — create the project N days before the period start. E.g., lead_time_days=5 with monthly schedule starting March 1 → project created Feb 24).
    - `status` (VARCHAR(20), default 'ACTIVE' — one of: `ACTIVE`, `PAUSED`, `COMPLETED`).
        - `ACTIVE`: scheduler checks this schedule on each run.
        - `PAUSED`: scheduler skips this schedule. Can be resumed.
        - `COMPLETED`: end_date has passed, or manually marked as completed. Terminal state.
    - `next_execution_date` (DATE, nullable — pre-calculated date when the next project should be created. Updated after each execution. If null, the schedule needs initialization).
    - `last_executed_at` (TIMESTAMP, nullable — when the scheduler last created a project from this schedule).
    - `execution_count` (INTEGER, default 0 — how many projects have been created from this schedule).
    - `project_lead_member_id` (UUID, nullable — if set, the member to assign as project lead for auto-created projects. If null, projects are created without a lead).
    - `created_by` (UUID — member who created the schedule).
    - `created_at`, `updated_at`.
    - Constraints:
        - `(template_id, customer_id, frequency)` unique — prevents duplicate schedules for the same template/customer/frequency combination. If a firm wants the same template at different frequencies for the same customer, that's two schedules.

2. **ScheduleExecution** (execution history log)

    - `id` (UUID, PK).
    - `schedule_id` (UUID, FK -> recurring_schedules).
    - `project_id` (UUID, FK -> projects — the project that was created).
    - `period_start` (DATE — the period start date this execution covers).
    - `period_end` (DATE — the period end date this execution covers).
    - `executed_at` (TIMESTAMP — when the project was created).
    - `created_at`.
    - Constraint: `(schedule_id, period_start)` unique — prevents duplicate executions for the same period. This is the idempotency key.

### 3. Scheduler job

Design the **daily scheduling engine**:

1. **Execution flow**

    The scheduler runs daily (e.g., 02:00 UTC via `@Scheduled(cron = "0 0 2 * * *")`).

    For each active tenant schema:
    ```
    1. Query all RecurringSchedules WHERE status = 'ACTIVE'
       AND next_execution_date <= today
       AND (end_date IS NULL OR end_date >= today)
    2. For each due schedule:
       a. Resolve the customer — check lifecycle status:
          - If OFFBOARDED or PROSPECT: skip, log warning, continue
          - Otherwise: proceed
       b. Calculate the period (period_start, period_end) from the schedule's frequency and anchor
       c. Check idempotency: if ScheduleExecution already exists for this (schedule_id, period_start), skip
       d. Resolve name tokens using the template's name_pattern (or schedule's name_override):
          - {customer} → customer.name
          - {month} → period_start month name
          - {year} → period_start year
          - {period_start} → formatted date
          - {period_end} → formatted date
       e. Create Project from template:
          - Set name to resolved pattern
          - Link to customer
          - Set project lead if schedule.project_lead_member_id is set
          - Copy template description as project description
       f. Create Tasks from TemplateTask entries:
          - Copy name, description, estimated hours, sort order, billable
          - Auto-assign based on assignee_role:
            - PROJECT_LEAD → assign to project lead (if set)
            - ANY_MEMBER / UNASSIGNED → leave unassigned
       g. Apply tags from TemplateTag entries
       h. Create ScheduleExecution record (idempotency record)
       i. Calculate and update next_execution_date on the schedule
       j. Increment execution_count
       k. If next_execution_date > end_date: transition schedule to COMPLETED
       l. Publish audit event (RECURRING_PROJECT_CREATED)
       m. Publish notification to project lead + org admins
    ```

2. **Period calculation**

    Given a `start_date` (anchor) and `frequency`, the schedule computes periods:
    - `WEEKLY`: 7-day periods from anchor. Period N starts at `anchor + (N * 7 days)`.
    - `FORTNIGHTLY`: 14-day periods from anchor.
    - `MONTHLY`: Same day-of-month. If anchor is Jan 31 and month has fewer days, use last day of month.
    - `QUARTERLY`: Every 3 months from anchor month.
    - `SEMI_ANNUALLY`: Every 6 months from anchor month.
    - `ANNUALLY`: Same month and day each year.

    The `next_execution_date` is calculated as: `next_period_start - lead_time_days`.

3. **Tenant iteration**

    The scheduler must run across all tenant schemas. Use the existing pattern from `TenantMigrationRunner` to discover active schemas. For each schema, bind `RequestScopes.TENANT_ID` via `ScopedValue.where().call()`, then execute the schedule check within that tenant context.

4. **Error handling**

    - If project creation fails for one schedule, log the error and continue to the next schedule. Do not fail the entire batch.
    - If a tenant schema is unreachable, log and skip.
    - All execution attempts (success or failure) should be logged at INFO level with schedule ID, customer ID, and outcome.

### 4. Manual instantiation flow

Design the **"Create Project from Template"** user flow:

1. **Entry points**
    - Project list page: "New from Template" button (alongside existing "New Project" button).
    - Template management page: "Use Template" action on each template row.

2. **Instantiation dialog**
    - Step 1: Select template (if not already selected from entry point). Show template name, description, task count, tag count.
    - Step 2: Configure project details:
        - Project name (pre-filled from template name_pattern with token placeholders shown).
        - Customer selector (optional — templates can create customer-linked or standalone projects).
        - Project lead selector (member picker).
        - Start date (defaults to today).
        - Description (pre-filled from template, editable).
    - Step 3: Review tasks (read-only preview of tasks that will be created, with assignee resolution shown).
    - "Create Project" button.

3. **Server-side flow**
    - Same project/task creation logic as the scheduler, minus the schedule/period tracking.
    - Resolves name tokens using provided customer name and current date.
    - Creates Project + Tasks + Tags in a single transaction.
    - Returns the created project for redirect.

### 5. Save Project as Template

Design the **"Save as Template"** flow:

1. **Entry point**
    - Project detail page: "Save as Template" action in the project actions menu.
    - Available to org admins, owners, and the project's lead.

2. **Dialog**
    - Template name (pre-filled from project name, editable).
    - Name pattern (pre-filled as project name, editable — user can add tokens).
    - Description (pre-filled from project description, editable).
    - Task list preview (checkboxes to include/exclude specific tasks).
    - Tag list preview (checkboxes to include/exclude specific tags).
    - For each included task: assignee role selector (PROJECT_LEAD / ANY_MEMBER / UNASSIGNED) — defaulted based on whether the task's current assignee is the project lead or not.

3. **Server-side flow**
    - Create ProjectTemplate with `source = FROM_PROJECT`, `source_project_id = project.id`.
    - Create TemplateTask entries for each selected task (copies name, description, estimated_hours, sort_order, billable).
    - Create TemplateTag entries for each selected tag.
    - Return the created template.

### 6. Frontend pages and components

1. **Template management page** (`/org/[slug]/settings/templates`)

    - Admin/owner only (settings section).
    - Table: template name, task count, tag count, source (Manual/From Project), active status toggle, created date.
    - Actions per row: Edit, Duplicate, Use Template, Delete.
    - "Create Template" button (manual creation from scratch).

2. **Template editor** (`/org/[slug]/settings/templates/[id]`)

    - Edit template name, name pattern, description, billable default.
    - Task list with drag-to-reorder:
        - Each task: name, description, estimated hours, billable toggle, assignee role selector.
        - Add task, remove task.
    - Tag selector (multi-select from existing org tags).
    - Save / Cancel.

3. **Schedule management page** (`/org/[slug]/schedules`)

    - Sidebar navigation item (under existing items, near Projects).
    - Table: schedule name (resolved template name + customer), frequency, customer, next execution, status, execution count.
    - Status filter tabs: Active, Paused, Completed, All.
    - Actions per row: Edit, Pause/Resume, View History, Delete.
    - "New Schedule" button.

4. **Schedule create/edit dialog**

    - Template selector (from active templates).
    - Customer selector.
    - Frequency selector (dropdown: Weekly, Fortnightly, Monthly, Quarterly, Semi-Annually, Annually).
    - Start date picker.
    - End date picker (optional).
    - Lead time days (number input, default 0).
    - Project lead selector (member picker, optional).
    - Name override (optional — shows the template's name_pattern as placeholder).
    - Preview: shows what the next project name would look like with current settings.

5. **Schedule execution history** (expandable section or sub-page)

    - Table: period (start–end), project name (linked to project), created date.
    - Shows last N executions with "Load More" pagination.

6. **"New from Template" dialog** (project list page)

    - Template picker → Customization form → Task preview → Create.
    - As described in Section 4 above.

7. **"Save as Template" dialog** (project detail page)

    - As described in Section 5 above.

### 7. API endpoints summary

1. **Project templates**

    - `GET /api/templates` — list all templates. Query params: `active` (boolean filter). Returns template list with task count and tag count.
    - `GET /api/templates/{id}` — get template with tasks and tags.
    - `POST /api/templates` — create a new template. Admin/owner only. Body: `{ name, namePattern, description, billableDefault, tasks: [...], tagIds: [...] }`.
    - `PUT /api/templates/{id}` — update template, tasks, and tags. Admin/owner only. Full replacement of tasks and tags (delete existing, create new).
    - `DELETE /api/templates/{id}` — soft-delete (set active = false) or hard-delete if no schedules reference it. Admin/owner only.
    - `POST /api/templates/{id}/duplicate` — create a copy of the template. Admin/owner only.
    - `POST /api/templates/from-project/{projectId}` — save a project as a template. Admin/owner/lead only. Body: `{ name, namePattern, description, taskIds: [...], tagIds: [...], taskRoles: { taskId: role } }`.
    - `POST /api/templates/{id}/instantiate` — create a project from a template. Body: `{ name, customerId?, projectLeadMemberId?, startDate?, description? }`. Returns created project.

2. **Recurring schedules**

    - `GET /api/schedules` — list all schedules. Query params: `status` (ACTIVE/PAUSED/COMPLETED), `customerId`, `templateId`.
    - `GET /api/schedules/{id}` — get schedule details with recent executions.
    - `POST /api/schedules` — create a new schedule. Admin/owner only. Body: `{ templateId, customerId, frequency, startDate, endDate?, leadTimeDays, projectLeadMemberId?, nameOverride? }`.
    - `PUT /api/schedules/{id}` — update schedule. Admin/owner only. Cannot change template_id or customer_id after creation.
    - `DELETE /api/schedules/{id}` — delete schedule. Admin/owner only. Only if status is PAUSED or COMPLETED.
    - `POST /api/schedules/{id}/pause` — pause an active schedule. Admin/owner only.
    - `POST /api/schedules/{id}/resume` — resume a paused schedule. Admin/owner only. Recalculates next_execution_date.
    - `GET /api/schedules/{id}/executions` — list execution history. Paginated.

For each endpoint specify:
- Auth requirement (valid Clerk JWT, appropriate role).
- Tenant scoping (dedicated schema).
- Permission checks.
- Request/response DTOs.

### 8. Notification integration

Publish notifications for:
- **TEMPLATE_CREATED**: Notify org admins when a new template is created (informational).
- **RECURRING_PROJECT_CREATED**: Notify the project lead (if assigned) and org admins when a recurring schedule creates a project. Include the project name, customer name, and period.
- **SCHEDULE_PAUSED**: Notify org admins when a schedule is paused.
- **SCHEDULE_COMPLETED**: Notify org admins when a schedule reaches its end date and transitions to COMPLETED.
- **SCHEDULE_SKIPPED**: Notify org admins when a schedule execution is skipped due to customer lifecycle status (e.g., customer is OFFBOARDED). This is important — silent skips are dangerous in a compliance context.

### 9. Audit integration

Publish audit events for:
- `TEMPLATE_CREATED` — new template created (name, source, task count).
- `TEMPLATE_UPDATED` — template modified (name, changes summary).
- `TEMPLATE_DELETED` — template deleted (name).
- `TEMPLATE_DUPLICATED` — template duplicated (source template name, new template name).
- `PROJECT_CREATED_FROM_TEMPLATE` — project created via manual instantiation (template name, project name, customer name if linked).
- `SCHEDULE_CREATED` — new recurring schedule created (template name, customer name, frequency).
- `SCHEDULE_UPDATED` — schedule modified (changes summary).
- `SCHEDULE_PAUSED` — schedule paused (reason if provided).
- `SCHEDULE_RESUMED` — schedule resumed.
- `SCHEDULE_COMPLETED` — schedule reached end date.
- `SCHEDULE_DELETED` — schedule deleted.
- `RECURRING_PROJECT_CREATED` — project auto-created by scheduler (schedule id, template name, project name, customer name, period).
- `SCHEDULE_EXECUTION_SKIPPED` — execution skipped (schedule id, reason — e.g., customer offboarded, duplicate period).

### 10. ADRs for key decisions

Add ADR-style sections for:

1. **Snapshot-based templates vs. reference-based templates**:
    - Why template tasks are copied (snapshot) at instantiation rather than maintaining a live reference to the template definition.
    - Snapshot means: changing a template doesn't retroactively alter existing projects. This is critical for auditability and prevents unexpected changes to in-progress work.
    - Same pattern as checklist templates (Phase 14) and document templates (Phase 12).
    - Trade-off: template updates don't propagate. But this is a feature, not a bug — firms need predictable project structures.

2. **Role-based assignment hints vs. member-based assignment**:
    - Why templates store `assignee_role` (PROJECT_LEAD, ANY_MEMBER, UNASSIGNED) instead of specific member UUIDs.
    - Templates are reused across customers and time periods — a specific member may leave, change roles, or be unavailable. Role hints are stable; member references are fragile.
    - At instantiation time, role resolution is simple: PROJECT_LEAD → whoever is set as lead, others → unassigned. No complex resource planning needed.
    - Trade-off: less precision in auto-assignment. But combined with the "lead time" feature, the project lead has time to review and adjust assignments before work begins.

3. **Pre-calculated next_execution_date vs. on-the-fly calculation**:
    - Why `RecurringSchedule` stores `next_execution_date` as a pre-calculated column rather than computing it at query time.
    - Pre-calculation makes the scheduler query trivially simple: `WHERE next_execution_date <= today AND status = 'ACTIVE'`. No date arithmetic in SQL.
    - The date is recalculated after each execution and when a schedule is resumed after pausing. Single source of truth.
    - Trade-off: must keep the column in sync. But updates only happen on execution or resume — low frequency, high reliability.

4. **Daily batch scheduler vs. event-driven scheduling**:
    - Why a simple daily `@Scheduled` cron job rather than a more sophisticated event-driven approach (message queue, distributed scheduler).
    - Recurring project creation is inherently a daily concern — firms think in periods (monthly, quarterly), not minutes. A daily check at 02:00 UTC covers all use cases with ample precision.
    - The lead_time_days feature handles the "create before period starts" requirement without sub-day scheduling.
    - No additional infrastructure (Redis, RabbitMQ, etc.) needed. Spring's built-in `@Scheduled` is sufficient and already battle-tested in the platform.
    - Trade-off: maximum 24-hour delay if the scheduler misses a run (e.g., deployment during the window). Mitigation: the scheduler is idempotent and catches up on the next run.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- **Templates are building blocks, not rigid contracts.** Users should feel empowered to customize when instantiating — pre-fill from the template, but let them change anything before creating. The template saves time; it doesn't constrain.
- **The scheduler is boring by design.** A daily cron that iterates schemas, checks dates, creates projects. No event sourcing, no distributed locking, no retry queues. Simple, reliable, observable. If it fails, it logs and catches up tomorrow.
- **Idempotency is non-negotiable.** The `(schedule_id, period_start)` unique constraint on `ScheduleExecution` means double-runs are impossible. This is the safety net that lets the scheduler be simple.
- **Customer lifecycle awareness.** The scheduler respects the customer lifecycle state machine from Phase 14. It will not create projects for offboarded or prospect customers. This prevents the embarrassing situation of auto-creating projects for a customer who left.
- **Name tokens are simple string substitution.** No Thymeleaf, no template engine. `{customer}` → customer name, `{month}` → "March". A `Map<String, String>` replacement loop is sufficient. Keep it dead simple.
- **No cascading schedule changes.** Updating a template doesn't retroactively update schedules that reference it. Updating a schedule doesn't retroactively update projects it already created. Each layer is independent after creation.
- All new entities follow the dedicated-schema-per-tenant model (Phase 13). No `tenant_id` columns.
- Frontend additions use the existing Shadcn UI component library and olive design system.
- The template management page lives in Settings (it's a configuration concern). The schedule management page lives in the main navigation (it's an operational concern that all members should see).
- Keep the scheduler observable: log each execution, track execution counts, show history in the UI. Admins should be able to see "what happened and when" at a glance.

Return a single markdown document as your answer, ready to be added as `architecture/phase16-project-templates-recurring-schedules.md` and ADRs.
