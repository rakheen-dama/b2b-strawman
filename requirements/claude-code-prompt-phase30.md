# Phase 30 — Expenses, Recurring Tasks & Daily Work Completeness

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 29, the core entities have proper lifecycle management:

- **Project**: ACTIVE → COMPLETED → ARCHIVED lifecycle, due dates, customer link, delete protection.
- **Task**: OPEN → IN_PROGRESS → DONE / CANCELLED lifecycle with transition validation, priority enum.
- **Customer**: full lifecycle (PROSPECT → ACTIVE → OFFBOARDED), linked to projects.
- **Invoice**: DRAFT → APPROVED → SENT → PAID / VOID, with line items generated from unbilled time entries.
- **TimeEntry**: project+task scoped, BillingStatus (UNBILLED/BILLED/WRITTEN_OFF), rate snapshots.
- **Retainer**: ACTIVE/PAUSED/TERMINATED, with period consumption tracking and auto-invoicing.
- **ProjectBudget**: hours and/or currency budgets with status tracking.

Key infrastructure: audit events, notifications, domain events, activity feed, custom fields, tags, saved views, email delivery, portal read-model, document templates + PDF rendering, rate cards (org → project → customer hierarchy), profitability reports.

**Gap**: The platform can track time but not expenses/disbursements. Invoices only capture time-based fees. Many professional services firms bill 15-30% of revenue as disbursements (filing fees, travel, courier, counsel fees). Additionally, daily work organisation lacks: time logging reminders, recurring task automation, and a calendar/deadline view.

## Objective

Complete the daily work experience for team members and close the revenue-capture gap:

1. **Expense tracking** — log disbursements/expenses against projects, with receipt attachments, markup, and billing integration. Expenses flow into invoices and profitability calculations.
2. **Recurring tasks** — lightweight recurrence rules on tasks for repeatable work items (monthly bank recs, weekly status reports). Auto-creates the next instance when the current one is completed.
3. **Unlogged time reminders** — notification-based nudge for team members who haven't logged time on working days. Configurable per org.
4. **Calendar/deadline view** — frontend visualization of task due dates and project deadlines across all projects. No new backend entities — uses existing due date data from Phase 29.

## Constraints & Assumptions

- **Expense follows TimeEntry patterns**: same billing status enum (UNBILLED/BILLED/WRITTEN_OFF), same project+task scoping, same invoice generation integration point.
- **No receipt OCR**: receipt attachment is a simple document upload (reuse existing S3/Document infrastructure). No parsing or data extraction.
- **No expense approval workflow for v1**: team members log expenses directly. An approval step can be added later if needed.
- **Recurring tasks are simple**: RRULE-style recurrence (daily/weekly/monthly/yearly + interval). No complex calendar math (no "third Tuesday of the month"). No task dependencies.
- **Calendar view is read-only**: a visualization of existing data, not a drag-and-drop scheduling tool.
- **Unlogged time reminders are opt-in per org**: disabled by default. When enabled, applies to all members.

---

## Section 1 — Expense Entity & CRUD

### Data Model

New `Expense` entity (tenant-scoped):

```
Expense:
  id              UUID (PK)
  projectId       UUID (not null) — FK to Project
  taskId          UUID (nullable) — optional FK to Task
  memberId        UUID (not null) — who incurred the expense
  date            LocalDate (not null) — when the expense was incurred
  description     String (not null, max 500)
  amount          BigDecimal (not null) — cost amount in org currency
  currency        String (not null, 3-char ISO) — defaults to org currency from OrgSettings
  category        ExpenseCategory enum
  receiptDocumentId UUID (nullable) — FK to Document (uploaded receipt)
  billingStatus   BillingStatus (not null, default UNBILLED) — reuse existing enum
  markupPercent   BigDecimal (nullable) — e.g., 20.00 for 20% markup
  billableAmount  BigDecimal (computed) — amount * (1 + markupPercent/100), or amount if no markup
  notes           String (nullable, max 1000)
  createdAt       Instant
  updatedAt       Instant
```

`ExpenseCategory` enum:
```
ExpenseCategory:
  FILING_FEE        -- court fees, CIPC, SARS, deeds office
  TRAVEL            -- mileage, flights, accommodation
  COURIER           -- postage, courier services
  SOFTWARE          -- licenses, subscriptions billed to client
  SUBCONTRACTOR     -- outsourced work, counsel fees
  PRINTING          -- copying, binding, printing
  COMMUNICATION     -- phone, data, fax
  OTHER             -- catch-all
```

### Business Rules

- Expenses can only be created against ACTIVE projects (reuse project lifecycle guard from Phase 29).
- If `taskId` is provided, the task must belong to the specified project.
- `billingStatus` transitions follow the same rules as TimeEntry: UNBILLED → BILLED (when included on an invoice), BILLED → WRITTEN_OFF (manual write-off), UNBILLED → WRITTEN_OFF.
- `billableAmount` is calculated server-side: `amount * (1 + markupPercent/100)`. If `markupPercent` is null, `billableAmount = amount`.
- Expenses on COMPLETED/ARCHIVED projects cannot be created or modified.
- Default `markupPercent` can be configured in OrgSettings (org-level default, overridable per expense).

### API Endpoints

```
POST   /api/projects/{projectId}/expenses          -- create expense
GET    /api/projects/{projectId}/expenses          -- list expenses (filterable by status, category, date range, memberId)
GET    /api/projects/{projectId}/expenses/{id}     -- get expense detail
PUT    /api/projects/{projectId}/expenses/{id}     -- update expense
DELETE /api/projects/{projectId}/expenses/{id}     -- delete expense (only if UNBILLED)
PATCH  /api/projects/{projectId}/expenses/{id}/write-off  -- mark as WRITTEN_OFF

GET    /api/expenses/mine                          -- cross-project "my expenses" (for My Work page)
```

### Frontend

- **Project detail page**: new "Expenses" tab alongside existing Time tab. Lists expenses with date, description, amount, category, billing status. "Log Expense" button opens dialog.
- **Log Expense dialog**: date picker, description, amount, currency (defaulted), category dropdown, optional task dropdown (scoped to project tasks), markup % (defaulted from org settings), receipt upload (optional).
- **Expense list**: filterable by category, billing status, date range, member. Sortable by date, amount.
- **My Work page**: add "My Expenses" section or tab showing recent unbilled expenses across projects.
- **Receipt attachment**: small thumbnail/icon if receipt is attached, click to view/download (reuse existing document presigned URL flow).

### Audit & Notifications

- Audit events: `expense.created`, `expense.updated`, `expense.deleted`, `expense.written_off`
- No notifications for expense creation (it's a personal action). Notify project admins if an expense exceeds a threshold? — **Out of scope for v1, can add later.**

---

## Section 2 — Expenses on Invoices

### Invoice Generation Extension

The existing invoice generation flow pulls unbilled time entries. Extend it to also pull unbilled expenses:

- `GET /api/projects/{projectId}/unbilled-summary` — extend response to include:
  - `unbilledExpenses`: list of unbilled expenses with amount, billableAmount, category
  - `unbilledExpenseTotal`: sum of billableAmounts
  - Existing `unbilledTimeEntries` and `unbilledTimeTotal` remain unchanged.

- Invoice generation dialog: show two sections — "Unbilled Time" (existing) and "Unbilled Expenses" (new). User can select/deselect individual expenses.

- `InvoiceLine` extension: expenses create invoice lines with:
  - `lineType`: new enum value `EXPENSE` (alongside existing `TIME` type)
  - `description`: expense description + category
  - `amount`: billableAmount (cost + markup)
  - `quantity`: 1 (expenses are lump sums, not hours × rate)

- When an invoice is APPROVED, linked expenses transition to BILLED.
- When an invoice is VOIDED, linked expenses revert to UNBILLED.

### Profitability Extension

- **Project profitability**: expenses are costs. If billed (passed through to client), they're revenue-neutral or positive (with markup). If absorbed (WRITTEN_OFF or never billed), they reduce margin.
- Update project profitability calculation:
  - `totalCost` = cost of time + absorbed expenses
  - `totalRevenue` = invoiced time fees + invoiced expense pass-throughs
  - `margin` = (revenue - cost) / revenue
- **Expense summary in project financials tab**: show expense totals by category, billed vs. unbilled breakdown.

### Budget Extension

- ProjectBudget may optionally track expense budgets (separate from hour/currency budgets). **Out of scope for v1** — expenses are tracked but not budgeted. Can add expense budget in a future phase.

---

## Section 3 — Recurring Tasks

### Data Model

Add recurrence fields to the existing Task entity (not a new entity):

```
Task (additional fields):
  recurrenceRule     String (nullable) — simplified RRULE: "FREQ=MONTHLY;INTERVAL=1" etc.
  recurrenceEndDate  LocalDate (nullable) — stop creating new instances after this date
  parentTaskId       UUID (nullable) — FK to the original recurring task (for tracking lineage)
```

`RecurrenceFrequency` — not stored as enum, but parsed from the rule string:
```
Supported frequencies:
  DAILY      -- every N days
  WEEKLY     -- every N weeks (on same day of week)
  MONTHLY    -- every N months (on same day of month)
  YEARLY     -- every N years (on same date)
```

### Business Rules

- When a recurring task is marked DONE, the system auto-creates the next task instance:
  - Same project, same name (with date suffix if desired), same assignee, same priority
  - Due date calculated from recurrence rule (e.g., monthly task done on Feb 15 → next due Mar 15)
  - `parentTaskId` set to the original recurring task's ID (for lineage)
  - New task inherits `recurrenceRule` and `recurrenceEndDate`
  - If current date is past `recurrenceEndDate`, no new task is created
- When a recurring task is CANCELLED, no new instance is created (recurrence stops).
- Editing the recurrence rule on a task only affects future instances (completed instances are immutable).
- Removing the recurrence rule (setting to null) stops future auto-creation.

### API Changes

- `POST /api/projects/{projectId}/tasks` — add optional `recurrenceRule` and `recurrenceEndDate` fields.
- `PUT /api/projects/{projectId}/tasks/{id}` — allow updating recurrence fields.
- `GET /api/projects/{projectId}/tasks?recurring=true` — filter to show only recurring tasks.

### Frontend

- **Task creation dialog**: optional "Repeat" section — frequency dropdown (None, Daily, Weekly, Monthly, Yearly), interval input (every N), optional end date.
- **Task list**: recurring icon/badge on tasks that have a recurrence rule.
- **Task detail sheet**: show recurrence info, link to parent task (if this is a generated instance), option to edit/remove recurrence.
- **On task completion toast**: if recurring, show "Next instance created for [date]" confirmation.

### Audit & Notifications

- Audit event: `task.recurrence_created` (when auto-creation happens)
- Notification to assignee: "Your recurring task [name] has a new instance due [date]"

---

## Section 4 — Unlogged Time Reminders

### Configuration

Add to OrgSettings:
```
timeReminderEnabled     Boolean (default false)
timeReminderDays        String (default "MON,TUE,WED,THU,FRI") — which days count as working days
timeReminderTime        LocalTime (default 17:00) — when to check/send reminder
timeReminderMinHours    BigDecimal (default 4.0) — minimum hours expected per day
```

### Business Rules

- When enabled, a scheduled job runs daily at `timeReminderTime` (org timezone from OrgSettings).
- For each member in the org, check if they logged at least `timeReminderMinHours` on today's date.
- If not, create an in-app notification: "You have [0/X hours] logged for today ([day name]). Don't forget to log your time!"
- **No email for v1** — in-app notification only. Email reminders can be added later via the existing email notification channel.
- Weekends/non-working days (not in `timeReminderDays`) are skipped.
- Members can individually mute time reminders via notification preferences (reuse existing preference infrastructure).

### Implementation

- Use Spring `@Scheduled` with a fixed rate (check every hour, process orgs whose reminder time falls in that window). Or a simpler approach: single daily run at midnight UTC, process all orgs based on their timezone offset.
- Lightweight — no new entities, just a scheduled notification producer.

### API Changes

- OrgSettings endpoints already exist — add the new fields to the existing settings DTO.
- No new endpoints needed.

### Frontend

- **OrgSettings page**: "Time Tracking" section with toggle for reminders, working days checkboxes, reminder time picker, minimum hours input.
- **Notification preferences**: "Time reminders" toggle for individual opt-out.

---

## Section 5 — Calendar / Deadline View

### Scope

A **read-only calendar visualization** showing task due dates and project deadlines. No new backend entities — this is a frontend-only feature using existing API data.

### Data Sources

- Task due dates: `GET /api/projects/{projectId}/tasks` (already has `dueDate`)
- Project due dates: `GET /api/projects` (added in Phase 29)
- Cross-project view: `GET /api/tasks/mine` (My Work endpoint, already returns tasks with due dates)

### API Changes

- Add a `GET /api/calendar?from={date}&to={date}` convenience endpoint that returns:
  - Tasks with due dates in the range (across all accessible projects)
  - Projects with due dates in the range
  - Grouped by date for easy frontend rendering
- This avoids the frontend making N+1 calls per project.

### Frontend

- **New "Calendar" page** accessible from sidebar navigation (between "My Work" and "Reports" or similar placement).
- **Month view**: grid showing dates, with dots/badges for items due on each day. Click a day to see the list.
- **List view** (alternative): chronological list of upcoming deadlines, grouped by week. Shows: item name, type (task/project), project name, assignee, status.
- **Overdue section**: items past their due date that are still OPEN/IN_PROGRESS/ACTIVE, shown prominently at the top.
- **Filters**: by project, by type (tasks only / projects only / both), by assignee.
- **Color coding**: overdue = red, due this week = amber, due later = default.

---

## Section 6 — Migration Strategy

### Tenant Schema Migration (V47 or next available)

```sql
-- Expense entity
CREATE TABLE expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  task_id UUID REFERENCES tasks(id),
  member_id UUID NOT NULL,
  date DATE NOT NULL,
  description VARCHAR(500) NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  category VARCHAR(30) NOT NULL,
  receipt_document_id UUID,
  billing_status VARCHAR(20) NOT NULL DEFAULT 'UNBILLED',
  markup_percent NUMERIC(5,2),
  billable_amount NUMERIC(12,2) NOT NULL,
  notes VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT expenses_billing_status_check
    CHECK (billing_status IN ('UNBILLED', 'BILLED', 'WRITTEN_OFF')),
  CONSTRAINT expenses_category_check
    CHECK (category IN ('FILING_FEE', 'TRAVEL', 'COURIER', 'SOFTWARE', 'SUBCONTRACTOR', 'PRINTING', 'COMMUNICATION', 'OTHER'))
);
CREATE INDEX idx_expenses_project_id ON expenses(project_id);
CREATE INDEX idx_expenses_member_id ON expenses(member_id);
CREATE INDEX idx_expenses_billing_status ON expenses(billing_status);

-- Task: recurrence fields
ALTER TABLE tasks ADD COLUMN recurrence_rule VARCHAR(100);
ALTER TABLE tasks ADD COLUMN recurrence_end_date DATE;
ALTER TABLE tasks ADD COLUMN parent_task_id UUID;

-- InvoiceLine: line type extension
-- (check if invoice_lines already has a type column; if not, add one)
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS line_type VARCHAR(20) NOT NULL DEFAULT 'TIME';
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS expense_id UUID;

-- OrgSettings: time reminder fields
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_days VARCHAR(50) DEFAULT 'MON,TUE,WED,THU,FRI';
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_time TIME DEFAULT '17:00';
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_min_hours NUMERIC(4,1) DEFAULT 4.0;

-- OrgSettings: default expense markup
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS default_expense_markup_percent NUMERIC(5,2);
```

---

## Out of Scope

- **Expense approval workflow**: no manager approval step for v1. All members can log expenses directly.
- **Expense budgets**: expenses are tracked but not budgeted against project budgets.
- **Receipt OCR / data extraction**: receipt is a dumb file attachment.
- **Mileage calculator**: travel expenses are manually entered amounts, no distance-based calculation.
- **Complex recurrence rules**: no "first Monday of the month", no "every weekday". Simple frequency + interval only.
- **Calendar drag-and-drop**: the calendar view is read-only. No rescheduling by dragging.
- **Email time reminders**: in-app only for v1. Email channel can be added later.
- **Expense categories as custom/configurable**: the enum is fixed for v1. Custom categories can be added via custom fields if needed.
- **Portal expense visibility**: expenses are not shown in the customer portal for v1.

## ADR Topics to Address

1. **Expense vs. Disbursement terminology** — "Expense" is more universally understood; "Disbursement" is the legal/accounting term. Recommend "Expense" in the UI with vertical-specific relabeling as a future concern.
2. **Expense markup model** — per-expense override vs. org default. Both supported; per-expense takes precedence.
3. **Recurring task implementation** — fields on Task vs. separate RecurrenceRule entity. Recommend fields on Task for simplicity (no join needed, recurrence is a property of the task, not a separate concept).
4. **Calendar data aggregation** — dedicated endpoint vs. client-side assembly from multiple endpoints. Recommend dedicated endpoint to avoid N+1 problem.
5. **Invoice line type extension** — adding EXPENSE alongside TIME. Ensure existing invoice generation, PDF rendering, and portal display handle the new line type gracefully.

## Style & Boundaries

- Expense follows TimeEntry patterns closely: same billing status lifecycle, same project scoping, same invoice integration pattern.
- Recurring task auto-creation happens synchronously on task completion (not via scheduled job). Keep it simple — the next instance is created in the same transaction as the status change.
- Calendar endpoint returns lightweight DTOs (id, name, type, dueDate, status, projectName). No full entity loading.
- All new features emit audit events and update the activity feed via existing domain event infrastructure.
- OrgSettings extensions use the existing settings entity — no new tables for configuration.
