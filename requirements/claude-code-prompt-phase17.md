You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with dedicated schema-per-tenant isolation (Phase 13 eliminated shared schema).
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups and "My Work" cross-project dashboard.
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate` (3-level hierarchy: org-default -> project-override -> customer-override), `CostRate`, `ProjectBudget`, `OrgSettings` (default currency, default billing rate, default cost rate). Time entries have `billable` flag, `billing_rate_snapshot`, `cost_rate_snapshot`. Profitability reports.
- **Invoicing & billing from time** (Phase 10): Invoice/InvoiceLine entities, draft-to-paid lifecycle, unbilled time management, InvoiceNumberService (sequential per-tenant), PSP adapter seam, HTML invoice preview via Thymeleaf.
- **Tags, custom fields & views** (Phase 11): `FieldDefinition`, `FieldGroup`, `Tag`, `EntityTag`, `SavedView` entities.
- **Document templates & PDF generation** (Phase 12): `DocumentTemplate`, `GeneratedDocument` entities. Thymeleaf + OpenHTMLToPDF rendering pipeline.
- **Customer compliance & lifecycle** (Phase 14): Customer lifecycle state machine (Prospect → Onboarding → Active → Dormant → Offboarded), checklist engine, compliance packs, data subject requests, retention policies.
- **Contextual actions & setup guidance** (Phase 15): Setup status aggregation, contextual action cards on entity detail pages, empty state guidance.
- **Project templates & recurring schedules** (Phase 16): `ProjectTemplate`, `TemplateTask`, `RecurringSchedule`, `ScheduleExecution` entities. Save project as template, create from template, recurring schedule with frequency config, daily scheduler job with idempotent execution, name token resolution, role-based task assignment.

For **Phase 17**, I want to add **Retainer Agreements & Billing** — a commercial layer that wraps recurring work with per-customer retainer agreements, hour bank tracking, period-based billing, and automated invoice generation.

***

## Objective of Phase 17

Design and specify:

1. **Retainer agreements** — a `RetainerAgreement` entity representing a commercial arrangement with a customer: either an hour bank (X hours/period at a base fee) or a fixed fee ($Z/period). Optionally linked to a `RecurringSchedule` (Phase 16) to associate auto-created projects with the retainer.
2. **Retainer periods** — a `RetainerPeriod` entity tracking each billing period: allocated hours (including rollover from prior period), consumed hours (from time entries), overage, carry-forward calculation, and linked invoice.
3. **Period consumption tracking** — real-time tracking of time entries against the open retainer period. When a time entry is created/updated/deleted for a customer with an active retainer, the open period's consumed hours are updated. Members can see "hours remaining" on the retainer at any time.
4. **Admin-triggered period close** — when a period end date passes, the dashboard shows it as "ready to close." An admin reviews time entries, then clicks "Close Period." The system calculates final usage, applies the rollover policy, generates a DRAFT invoice, and opens the next period.
5. **Invoice generation from retainers** — auto-generation of DRAFT invoices using the existing invoicing system (Phase 10). Hour bank retainers produce a base fee line + optional overage line (at the customer's standard billing rate from the rate card hierarchy). Fixed fee retainers produce a single line item.
6. **Rollover logic** — three policies: FORFEIT (unused hours lost), CARRY_FORWARD (unused hours added to next period indefinitely), CARRY_CAPPED (unused hours carried up to a configurable maximum).
7. **Retainer dashboard and customer integration** — a dashboard showing all active retainers with current period status, and a retainer tab on the customer detail page showing agreement terms, period history, and consumption progress.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (dedicated schema per tenant).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - External billing/subscription platforms (Stripe Billing, Chargebee, etc.) — retainer billing is internal to the platform, not a SaaS subscription. The existing invoice system handles the output.
    - Complex financial calculation libraries — retainer math is straightforward (hours × rate, rollover arithmetic). Standard Java `BigDecimal` is sufficient.
    - Background jobs for period closing — period close is always admin-triggered. No automated close scheduler.
- **Overage uses existing billing rates.** Overage hours are billed at the customer's standard billing rate from the existing rate card hierarchy (Phase 8). No separate overage rate configuration. This keeps the rate model simple and consistent.
- **Single currency per retainer.** Retainers use the org's default currency (from `OrgSettings.default_currency`). Multi-currency retainers are out of scope.
- **Period close is admin-triggered, not automated.** Financial operations require human review. The system notifies admins when periods are ready to close, but never auto-closes or auto-generates invoices without explicit admin action.

2. **Tenancy**

- All new entities (`RetainerAgreement`, `RetainerPeriod`) follow the dedicated-schema-per-tenant model (Phase 13). No `tenant_id` columns needed — schema boundary provides isolation.
- Flyway migration for tenant schemas (not public).

3. **Permissions model**

- **Retainer agreement management** (create, edit, terminate agreements):
    - Org admins and owners only. Retainers are commercial arrangements — admin-level configuration.
- **Period close** (close period, generate invoice):
    - Org admins and owners only. This triggers invoice generation — a financial operation.
- **View retainer status** (current period, hours remaining, period history):
    - All org members can view retainer status for customers they have access to. This helps members understand how many hours remain on a retainer before logging time.
- **Retainer dashboard** (cross-customer retainer overview):
    - Org admins and owners only.

4. **Relationship to existing entities**

- **Customer**: A retainer is linked to exactly one customer. A customer can have at most one active retainer agreement at a time. The customer's lifecycle status is respected — retainers cannot be created for OFFBOARDED or PROSPECT customers.
- **RecurringSchedule** (Phase 16): A retainer optionally links to a recurring schedule. When linked, projects auto-created by the schedule are automatically associated with the retainer for consumption tracking purposes. When not linked, any time entries logged against the customer's projects count toward the retainer.
- **TimeEntry** (Phase 5): Time entries are the source of consumption data. When a billable time entry is created/updated/deleted for a customer with an active retainer and an open period, the period's consumed hours are recalculated. Only billable time entries count toward retainer consumption.
- **Invoice / InvoiceLine** (Phase 10): Period close generates a DRAFT invoice using the existing invoicing system. The retainer period stores a reference to the generated invoice. Invoice lines reference the retainer period for traceability.
- **BillingRate** (Phase 8): Overage calculation uses the customer's effective billing rate from the existing rate card hierarchy (customer override → project override → org default). The rate is resolved at period close time, not at time entry creation.
- **AuditEvent** (Phase 6): All retainer operations are audited.
- **Notification** (Phase 6.5): Notifications for period ready to close, period closed, retainer approaching capacity.

5. **Out of scope for Phase 17**

- Automated period closing (always admin-triggered).
- Multi-currency retainers (uses org default currency).
- Configurable overage rates (uses standard billing rate from rate hierarchy).
- Mid-period retainer term changes with proration (changes take effect next period).
- Retainer-specific profitability reports (existing Phase 8 profitability reports cover project/customer profitability, which includes retainer work).
- Retainer proposals/quotes (a future "Proposals & Engagement Letters" phase).
- Customer portal visibility of retainer status (future portal phase).
- Credit notes for overpayment / unused hours refunds.
- Retainer renewals with term renegotiation (update the agreement manually for new terms).

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase17-retainer-agreements-billing.md`, plus ADRs for key decisions.

### 1. RetainerAgreement entity

Design the **retainer agreement** data model:

1. **RetainerAgreement**

    - `id` (UUID, PK).
    - `customer_id` (UUID, FK -> customers — the customer this retainer is for).
    - `schedule_id` (UUID, nullable, FK -> recurring_schedules — optional link to a recurring schedule from Phase 16. If linked, projects created by this schedule are tracked against the retainer).
    - `name` (VARCHAR(300) — display name, e.g., "Monthly Retainer — Acme Corp").
    - `type` (VARCHAR(20), NOT NULL — one of: `HOUR_BANK`, `FIXED_FEE`).
    - `status` (VARCHAR(20), default 'ACTIVE' — one of: `ACTIVE`, `PAUSED`, `TERMINATED`).
        - `ACTIVE`: periods are tracked, consumption counts, period close is available.
        - `PAUSED`: no new periods open, existing open period remains. Consumption still counts against the open period. Used for temporary holds.
        - `TERMINATED`: retainer is over. Open period can be closed (final period). No new periods. Terminal state.
    - `frequency` (VARCHAR(20), NOT NULL — one of: `WEEKLY`, `FORTNIGHTLY`, `MONTHLY`, `QUARTERLY`, `SEMI_ANNUALLY`, `ANNUALLY`. Matches the schedule frequency if linked, but can be independent).
    - `start_date` (DATE, NOT NULL — first period start date).
    - `end_date` (DATE, nullable — if set, retainer terminates after this date).
    - **Hour bank fields** (applicable when type = HOUR_BANK):
        - `allocated_hours` (DECIMAL(10,2), nullable — hours per period, e.g., 40.00).
        - `period_fee` (DECIMAL(12,2), nullable — base fee per period, e.g., 20000.00. This is what the customer pays for the allocated hours).
    - **Fixed fee fields** (applicable when type = FIXED_FEE):
        - `period_fee` (DECIMAL(12,2), nullable — the fixed fee per period).
    - **Rollover fields** (applicable when type = HOUR_BANK):
        - `rollover_policy` (VARCHAR(20), default 'FORFEIT' — one of: `FORFEIT`, `CARRY_FORWARD`, `CARRY_CAPPED`).
        - `rollover_cap_hours` (DECIMAL(10,2), nullable — maximum hours that can carry forward. Only used when rollover_policy = CARRY_CAPPED).
    - `notes` (TEXT, nullable — internal notes about the agreement terms).
    - `created_by` (UUID — member who created the agreement).
    - `created_at`, `updated_at`.
    - Constraints:
        - A customer can have at most one ACTIVE or PAUSED retainer at a time. Enforce at service layer: creating a new retainer for a customer who already has an ACTIVE/PAUSED one is rejected.
        - If type = HOUR_BANK, `allocated_hours` and `period_fee` are required.
        - If type = FIXED_FEE, `period_fee` is required.
        - If rollover_policy = CARRY_CAPPED, `rollover_cap_hours` is required.

### 2. RetainerPeriod entity

Design the **period tracking** data model:

1. **RetainerPeriod**

    - `id` (UUID, PK).
    - `agreement_id` (UUID, FK -> retainer_agreements).
    - `period_start` (DATE, NOT NULL — start of the billing period).
    - `period_end` (DATE, NOT NULL — end of the billing period, exclusive).
    - `status` (VARCHAR(20), default 'OPEN' — one of: `OPEN`, `CLOSED`).
        - `OPEN`: currently active period. Time entries accumulate here.
        - `CLOSED`: period has been finalized. No further consumption changes.
    - **Hour bank tracking** (applicable when agreement type = HOUR_BANK):
        - `allocated_hours` (DECIMAL(10,2) — hours allocated for this period, INCLUDING rollover from prior period).
        - `base_allocated_hours` (DECIMAL(10,2) — the agreement's per-period allocation, WITHOUT rollover).
        - `rollover_hours_in` (DECIMAL(10,2), default 0 — hours carried forward from the previous period).
        - `consumed_hours` (DECIMAL(10,2), default 0 — total billable hours logged against this period).
        - `overage_hours` (DECIMAL(10,2), default 0 — MAX(0, consumed_hours - allocated_hours). Calculated at close time).
        - `remaining_hours` (DECIMAL(10,2) — MAX(0, allocated_hours - consumed_hours). Calculated field).
        - `rollover_hours_out` (DECIMAL(10,2), default 0 — hours to carry forward to the next period. Calculated at close time based on rollover policy).
    - **Fixed fee tracking** (applicable when agreement type = FIXED_FEE):
        - `consumed_hours` (DECIMAL(10,2), default 0 — total billable hours logged, for informational purposes).
    - `invoice_id` (UUID, nullable, FK -> invoices — the invoice generated when this period was closed).
    - `closed_at` (TIMESTAMP, nullable — when the period was closed).
    - `closed_by` (UUID, nullable — member who closed the period).
    - `created_at`, `updated_at`.
    - Constraints:
        - `(agreement_id, period_start)` unique — one period per start date per agreement.
        - Only one OPEN period per agreement at a time (enforced at service layer).

### 3. Period consumption tracking

Design how **time entries affect retainer periods**:

1. **Consumption calculation**

    - When a billable time entry is created, updated, or deleted for a project linked to a customer with an active retainer and an OPEN period:
        - The system recalculates `consumed_hours` on the open period by summing all billable time entries for the customer within the period's date range (`period_start <= entry.date < period_end`).
        - This is a **query-based calculation**, not an incremental counter. Recalculating from source data ensures consistency even if time entries are backdated, edited, or deleted.
    - The recalculation is triggered by an `ApplicationEvent` published when time entries change (reuse the existing event infrastructure from Phase 6.5).

2. **Which time entries count**

    - A time entry counts toward a retainer period if ALL of the following are true:
        - The time entry's project is linked to the retainer's customer (`project.customer_id = retainer.customer_id`).
        - The time entry is billable (`billable = true`).
        - The time entry's date falls within the period range (`period_start <= date < period_end`).
    - If the retainer has a linked `schedule_id`, optionally restrict to only projects created by that schedule (via `ScheduleExecution.project_id`). However, for v1, **count all customer projects** — this is simpler and matches how firms actually think about retainers (all work for a customer, not just scheduled work).

3. **Real-time remaining hours**

    - The `remaining_hours` on an OPEN period is always calculated as: `MAX(0, allocated_hours - consumed_hours)`.
    - This is exposed via the API and shown in the UI. Members can check at any time before logging time.
    - When remaining hours reach 0 (or a configurable threshold, e.g., 80% consumed), a notification is sent to org admins: "Retainer for Customer X is at [N]% capacity."

4. **Consumption threshold notifications**

    - Notify org admins at configurable thresholds:
        - 80% consumed: "Retainer approaching capacity" (warning).
        - 100% consumed: "Retainer fully consumed — further time is overage" (alert).
    - Thresholds are hardcoded for v1 (80%, 100%). Configurable thresholds are a future enhancement.

### 4. Period close and invoice generation

Design the **admin-triggered period close** flow:

1. **When to show "Ready to Close"**

    - A period is "ready to close" when `today >= period_end` and `status = OPEN`.
    - The retainer dashboard prominently shows periods that are ready to close, sorted by how overdue they are.
    - A notification is sent to org admins when a period becomes ready to close.

2. **Close Period flow**

    ```
    Admin clicks "Close Period" on retainer dashboard or customer detail
        │
        ├── System calculates final consumed_hours (re-query all time entries)
        │
        ├── For HOUR_BANK:
        │   ├── overage_hours = MAX(0, consumed_hours - allocated_hours)
        │   ├── unused_hours = MAX(0, allocated_hours - consumed_hours)
        │   ├── Apply rollover policy:
        │   │   ├── FORFEIT: rollover_hours_out = 0
        │   │   ├── CARRY_FORWARD: rollover_hours_out = unused_hours
        │   │   └── CARRY_CAPPED: rollover_hours_out = MIN(unused_hours, rollover_cap_hours)
        │   └── Generate DRAFT invoice:
        │       ├── Line 1: "Retainer — {period_start} to {period_end}" — amount = period_fee
        │       └── Line 2 (if overage > 0): "Overage ({overage_hours} hrs @ {rate}/hr)" — amount = overage_hours × effective_billing_rate
        │
        ├── For FIXED_FEE:
        │   └── Generate DRAFT invoice:
        │       └── Line 1: "Retainer — {period_start} to {period_end}" — amount = period_fee
        │
        ├── Set period.status = CLOSED, period.closed_at, period.closed_by
        ├── Set period.invoice_id = generated invoice ID
        │
        ├── If retainer is ACTIVE and not past end_date:
        │   └── Open next period:
        │       ├── Calculate next period_start and period_end from frequency
        │       ├── Set allocated_hours = base_allocated_hours + rollover_hours_out
        │       ├── Set rollover_hours_in = rollover_hours_out from closed period
        │       └── Set status = OPEN
        │
        ├── If retainer end_date has passed:
        │   └── Set agreement.status = TERMINATED
        │
        ├── Publish audit event (RETAINER_PERIOD_CLOSED)
        └── Publish notification (period closed, invoice generated)
    ```

3. **Overage rate resolution**

    - Overage hours are billed at the customer's effective billing rate from the existing rate card hierarchy (Phase 8):
        - Customer-specific billing rate (if set) → project-level override → org default billing rate.
    - The rate is resolved at period close time, not at time entry creation. This means rate changes during a period affect the overage calculation. This is intentional — it matches how firms actually bill (current rates, not historical).
    - The resolved rate is stored on the invoice line for auditability.

4. **Invoice integration**

    - The generated invoice uses the existing Invoice/InvoiceLine entities from Phase 10.
    - Invoice fields:
        - `customer_id` = retainer's customer.
        - `status` = DRAFT (always — admin must review and approve).
        - `due_date` = calculated from org's default payment terms.
    - Invoice lines include a `retainer_period_id` reference (nullable column on InvoiceLine, added via migration) for traceability. This allows the invoice detail page to show "Generated from retainer: [agreement name], period [start] – [end]."
    - The invoice follows the standard lifecycle: DRAFT → APPROVED → SENT → PAID. No special handling needed beyond creation.

### 5. First period creation

Design how the **first period** is created:

1. When a RetainerAgreement is created, the system automatically creates the first RetainerPeriod:
    - `period_start` = agreement.start_date.
    - `period_end` = calculated from frequency (e.g., monthly: start_date + 1 month).
    - `allocated_hours` = agreement.allocated_hours (no rollover for first period).
    - `base_allocated_hours` = agreement.allocated_hours.
    - `rollover_hours_in` = 0.
    - `status` = OPEN.

2. If the agreement start_date is in the future, the period is still created as OPEN. Time entries before the period start date don't count (date range filtering handles this).

### 6. Frontend pages and components

1. **Retainer dashboard** (`/org/[slug]/retainers`)

    - Sidebar navigation item (near Schedules from Phase 16).
    - **Summary cards**: total active retainers, periods ready to close, total overage this month.
    - **Retainers table**: agreement name, customer, type (Hour Bank / Fixed Fee), frequency, current period status, hours used/allocated (progress bar for hour bank), status badge.
    - **Periods ready to close** section: prominent list of overdue periods with "Close Period" action buttons.
    - Status filter tabs: Active, Paused, Terminated, All.
    - "New Retainer" button.

2. **Retainer create/edit dialog**

    - Customer selector (filtered to exclude OFFBOARDED/PROSPECT customers, and customers who already have an active retainer).
    - Retainer type selector (Hour Bank / Fixed Fee).
    - **Hour bank fields** (shown when type = Hour Bank):
        - Allocated hours per period (number input).
        - Period fee (currency input).
        - Rollover policy selector (Forfeit / Carry Forward / Carry Forward with Cap).
        - Rollover cap hours (shown when policy = Carry Forward with Cap).
    - **Fixed fee fields** (shown when type = Fixed Fee):
        - Period fee (currency input).
    - Frequency selector (dropdown).
    - Start date picker.
    - End date picker (optional).
    - Recurring schedule link (optional — dropdown of schedules for the selected customer).
    - Project lead selector (optional — for setting the lead on retainer-linked projects).
    - Notes (textarea).

3. **Customer detail — Retainer tab**

    - Visible when the customer has or had a retainer agreement.
    - **Active retainer card**: agreement name, type, frequency, terms summary, current period progress.
        - Hour bank: circular or linear progress showing consumed/allocated hours, with remaining hours prominently displayed.
        - Fixed fee: shows consumed hours (informational) and fee amount.
    - **Period history table**: period dates, allocated hours, consumed hours, overage, rollover out, invoice link (to invoice detail), status.
    - **Actions**: "Close Period" (when period is ready), "Pause Retainer", "Terminate Retainer."

4. **Close Period confirmation dialog**

    - Shows period summary: dates, allocated hours, consumed hours, overage (if any), rollover calculation.
    - Shows invoice preview: line items that will be generated.
    - "Close & Generate Invoice" button.
    - Warning if there's significant overage.

5. **Retainer status on project list**

    - For projects linked to a customer with an active retainer, show a small retainer indicator (icon or badge) on the project card/row. Helps members know when they're working on retainer time.

6. **Time entry — retainer context**

    - When logging time on a project linked to a customer with an active retainer:
        - Show a subtle indicator: "Retainer: X hrs remaining" near the time entry form.
        - If the retainer is fully consumed, show a warning: "Retainer fully consumed — this time will be overage."
    - This is informational only — it does not block time entry creation.

### 7. API endpoints summary

1. **Retainer agreements**

    - `GET /api/retainers` — list all retainer agreements. Query params: `status`, `customerId`. Returns agreements with current period summary.
    - `GET /api/retainers/{id}` — get agreement details with current period and recent period history.
    - `POST /api/retainers` — create a new retainer agreement. Admin/owner only. Body: `{ customerId, scheduleId?, name, type, frequency, startDate, endDate?, allocatedHours?, periodFee, rolloverPolicy?, rolloverCapHours?, notes? }`. Automatically creates the first period.
    - `PUT /api/retainers/{id}` — update agreement terms. Admin/owner only. Changes take effect on the next period (current open period is not retroactively adjusted).
    - `POST /api/retainers/{id}/pause` — pause an active retainer. Admin/owner only.
    - `POST /api/retainers/{id}/resume` — resume a paused retainer. Admin/owner only.
    - `POST /api/retainers/{id}/terminate` — terminate a retainer. Admin/owner only. The current open period can still be closed.

2. **Retainer periods**

    - `GET /api/retainers/{id}/periods` — list all periods for an agreement. Paginated. Most recent first.
    - `GET /api/retainers/{id}/periods/current` — get the current open period with real-time consumed/remaining hours.
    - `POST /api/retainers/{id}/periods/current/close` — close the current period. Admin/owner only. Triggers invoice generation and next period creation. Returns the closed period with invoice reference.

3. **Retainer summary (for UI integration)**

    - `GET /api/customers/{customerId}/retainer-summary` — lightweight endpoint returning the customer's active retainer status (if any): agreement type, current period hours used/remaining, percentage consumed. Used by the time entry form and project list indicators.

For each endpoint specify:
- Auth requirement (valid Clerk JWT, appropriate role).
- Tenant scoping (dedicated schema).
- Permission checks.
- Request/response DTOs.

### 8. Notification integration

Publish notifications for:
- **RETAINER_PERIOD_READY_TO_CLOSE**: Notify org admins when a period's end date passes and it's ready for closing. Include the retainer name, customer name, and period dates.
- **RETAINER_PERIOD_CLOSED**: Notify org admins when a period is closed. Include consumed hours, overage (if any), and a link to the generated invoice.
- **RETAINER_APPROACHING_CAPACITY**: Notify org admins when an hour bank retainer reaches 80% consumption. Include hours remaining and the customer name.
- **RETAINER_FULLY_CONSUMED**: Notify org admins when an hour bank retainer reaches 100% consumption. Include the customer name and a note that further time is overage.
- **RETAINER_TERMINATED**: Notify org admins when a retainer is terminated. Include the customer name and reason.

### 9. Audit integration

Publish audit events for:
- `RETAINER_CREATED` — new retainer agreement created (customer, type, terms).
- `RETAINER_UPDATED` — agreement terms modified (what changed).
- `RETAINER_PAUSED` — retainer paused (customer, reason if provided).
- `RETAINER_RESUMED` — retainer resumed (customer).
- `RETAINER_TERMINATED` — retainer terminated (customer, final period status).
- `RETAINER_PERIOD_CLOSED` — period closed (period dates, consumed hours, overage, rollover, invoice ID).
- `RETAINER_PERIOD_OPENED` — new period opened (period dates, allocated hours including rollover).
- `RETAINER_INVOICE_GENERATED` — invoice generated from period close (invoice ID, line items, total amount).

### 10. Migration notes

1. **New tables**: `retainer_agreements`, `retainer_periods`.
2. **Modified tables**: `invoice_lines` — add nullable `retainer_period_id` (UUID, FK -> retainer_periods) column for traceability.
3. **Migration numbering**: Check the latest migration number in the tenant schema and use the next sequential number. Do not hardcode — it depends on which phases have been deployed.
4. All migrations run in tenant schemas only (not public).

### 11. ADRs for key decisions

Add ADR-style sections for:

1. **Admin-triggered period close vs. automated close**:
    - Why period closing requires explicit admin action rather than running automatically on a schedule.
    - Financial operations (invoice generation) should have human oversight. Late time entries, corrections, and review are common before closing a period. Automated close risks generating invoices with incomplete data.
    - The system makes it easy (dashboard shows what's ready, one-click close) but never assumes.
    - Trade-off: periods can stay open indefinitely if admin doesn't act. Mitigation: notifications escalate with reminders.

2. **Standard billing rate for overage vs. configurable overage rate**:
    - Why overage hours use the existing billing rate hierarchy (Phase 8) rather than a separate overage rate per retainer.
    - Keeps the rate model unified — one place to configure rates, one calculation path. Reduces configuration surface.
    - The existing hierarchy (customer override → project override → org default) already handles per-customer rate differences.
    - Trade-off: can't charge a premium for overage. Most small-to-medium firms bill overage at the same rate. Premium overage is a large-firm pattern that can be added later.

3. **Query-based consumption vs. incremental counter**:
    - Why consumed hours are recalculated by querying time entries rather than maintaining an incrementally updated counter.
    - Query-based is self-healing: if a time entry is backdated, edited, or deleted, the consumption is always correct. Incremental counters require compensating logic for every mutation type.
    - Performance: the query is scoped to one customer, one date range. Even with thousands of time entries per period, this is a sub-millisecond indexed query.
    - Trade-off: slightly more expensive per read. But reads are infrequent (UI polling, not real-time) and the query is trivial.

4. **One active retainer per customer vs. multiple concurrent retainers**:
    - Why a customer can have at most one active (or paused) retainer at a time.
    - Multiple concurrent retainers create ambiguity: which retainer does a time entry count against? Allocation logic becomes complex (split hours, priority ordering, overflow routing).
    - One-retainer-per-customer is the common case for small-to-medium firms. A new retainer replaces the old one (terminate → create).
    - Trade-off: can't model "40 hours general consulting + 10 hours emergency support" as two separate retainers. Workaround: use a single retainer with 50 hours. True multi-retainer support is a future enhancement if customer demand warrants it.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- **Retainers are commercial agreements, not technical constructs.** The UI should use business language: "retainer," "period," "hour bank," "overage," "rollover." Not "subscription," "billing cycle," "quota."
- **The admin is always in control.** Period close, invoice generation, termination — all require explicit admin action. The system informs and recommends, but never acts on financial matters autonomously.
- **Consumption tracking is transparent.** Members should always know where they stand: how many hours remain, whether they're in overage. No surprises at period end.
- **Retainers build on existing infrastructure, not parallel it.** Invoice generation uses Phase 10's Invoice entity. Rate resolution uses Phase 8's rate hierarchy. Time entries come from Phase 5. Retainers are a coordination layer, not a new billing system.
- **Keep the math simple and auditable.** Every number on a closed period (allocated, consumed, overage, rollover) should be explainable by looking at the time entries and agreement terms. No black-box calculations.
- All new entities follow the dedicated-schema-per-tenant model (Phase 13). No `tenant_id` columns.
- Frontend additions use the existing Shadcn UI component library and olive design system.
- The retainer dashboard lives in the main navigation (operational concern). Retainer configuration per customer lives on the customer detail page.
- Rollover math must handle edge cases: first period (no rollover in), final period (no next period to roll into), paused periods (rollover freezes), resumed periods (rollover picks up from last closed period).

Return a single markdown document as your answer, ready to be added as `architecture/phase17-retainer-agreements-billing.md` and ADRs.
