You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema-per-tenant) tiers.
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, notes, `billable` flag, `billing_rate_snapshot`, `cost_rate_snapshot`.
- **Rate cards** (Phase 8): `BillingRate` (3-level hierarchy: org-default → project-override → customer-override), `CostRate`, `OrgSettings` (default currency, billing defaults).
- **Invoicing** (Phase 10): `Invoice` entity with lifecycle (DRAFT → APPROVED → SENT → PAID / VOID), `InvoiceLine` with per-time-entry granularity, `InvoiceCounter` for sequential numbering, unbilled time management, HTML preview, mocked PSP adapter.
- **Document templates** (Phase 12): `DocumentTemplate` with Thymeleaf + OpenHTMLToPDF rendering pipeline, context builders, generated document tracking.
- **Customer compliance** (Phase 13, in progress): Customer lifecycle state machine (PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDED), checklist engine, compliance packs, retention policies.
- **Notifications** (Phase 6.5): `ApplicationEvent`-based fan-out, in-app notifications, notification preferences.
- **Audit** (Phase 6): domain mutation logging with queryable API.
- **Tags, custom fields, saved views** (Phase 11): flexible metadata and filtering across entities.
- **Operational dashboards** (Phase 9): company dashboard, project overview, personal dashboard, health scoring.
- **Scheduling infrastructure**: None yet — this phase introduces it.

For **Phase 14**, I want to add **Recurring Work & Retainers** — the domain that turns one-off project billing into predictable recurring revenue. This phase introduces retainer agreements, hour bank tracking, recurring invoice generation, and a lightweight job scheduling backbone.

***

## Objective of Phase 14

Design and specify:

1. **Retainer agreement entity** — a commercial contract between the org and a customer, defining billing terms (fixed-fee or hour bank), billing frequency, and pricing. Supports multiple retainers per customer.
2. **Hour bank mechanics** — period-based allocation tracking: hours allocated, used (from time entries), rolled over from previous periods, available balance. Overage billing at a configurable rate.
3. **Retainer lifecycle** — DRAFT → ACTIVE → PAUSED → EXPIRED / CANCELLED, with each transition audited and notified.
4. **Recurring invoice generation** — a background scheduler that, at the end of each billing period, auto-generates draft invoices from active retainers. Fixed-fee retainers produce a single line item; hour bank retainers produce base fee + overage lines.
5. **Job scheduling backbone** — a lightweight, tenant-aware scheduling infrastructure using Spring `@Scheduled` with idempotent job execution. Designed for reuse by future periodic jobs (dormancy detection, retention policy enforcement, subscription renewals).
6. **Retainer management UI** — frontend views for creating, managing, and monitoring retainers with utilization visibility.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - External job scheduling frameworks (Quartz, Hangfire, etc.) — use Spring's built-in `@Scheduled` with a custom tenant-aware wrapper.
    - External message queues — scheduling is in-process. If the backend restarts mid-cycle, idempotent jobs simply re-run on the next tick.
    - Separate microservices — everything stays in the existing backend deployable.
- All monetary amounts use `BigDecimal` (backend) / formatted strings (API responses). No floating-point currency math.
- Currency follows ISO 4217. Retainer currency matches the customer's invoicing currency (or org default).

2. **Tenancy**

- All new entities (Retainer, RetainerPeriod, ScheduledJobLog) follow the same tenant isolation model as existing entities:
    - Pro orgs: dedicated schema.
    - Starter orgs: `tenant_shared` schema with `tenant_id` column + Hibernate `@Filter` + RLS.
- All new entities must include Flyway migrations for both tenant and shared schemas.
- The scheduler runs in system context, iterates over all tenants, and executes each tenant's work inside `ScopedValue.where(RequestScopes.TENANT_ID, schema).call()`.

3. **Permissions model**

- Retainer management (create, edit, activate, pause, cancel):
    - Org admins and owners: full access.
    - Project leads: can view retainers linked to their projects but cannot create or modify.
    - Regular members: no retainer access.
- Retainer viewing:
    - Org admins and owners: all retainers.
    - Project leads: retainers associated with customers of their projects.
- Auto-generated invoices follow existing invoice permissions (Phase 10).

4. **Relationship to existing entities**

- **Customer**: A retainer belongs to exactly one customer. A customer can have multiple active retainers (e.g., bookkeeping retainer + advisory retainer).
- **Invoice**: Recurring invoice generation creates Invoice entities (DRAFT status) using the existing Phase 10 invoice infrastructure. The retainer is a new "source" for invoice creation alongside manual creation and unbilled-time generation.
- **TimeEntry**: For hour bank retainers, time entries on retainer-associated projects are tracked against the hour bank. Time entries do NOT gain a direct FK to the retainer — instead, the hour bank usage is computed via a query: "billable time entries for projects linked to this retainer's customer, within this period, that are not yet billed to another invoice."
- **BillingRate**: Hour bank retainers use the existing rate hierarchy for overage billing. The base retainer rate (for the fixed portion) is stored on the retainer itself.
- **OrgSettings**: Default currency is used when creating retainers if no currency is specified.
- **AuditEvent**: All retainer state transitions and period closings publish audit events.
- **Notification**: Retainer lifecycle events and period alerts trigger notifications.

5. **Out of scope for Phase 14**

- Project templates and auto-spawning recurring projects — that's Phase 15.
- Proration for mid-cycle starts, pauses, or cancellations — v1 retainers bill full periods only. Mid-cycle changes take effect at the next period boundary.
- Multi-currency retainers — one currency per retainer, matching the org or customer default.
- Retainer-specific rate overrides — retainers use the existing rate card hierarchy (Phase 8). The retainer stores only the fixed base fee.
- Customer portal visibility of retainers — internal-only in this phase.
- Email delivery of auto-generated invoices — drafts are generated; staff review and send manually.
- Credit notes for retainer overpayments or adjustments.
- Retainer approval workflows — creating/activating a retainer is a direct action by an admin/owner.
- Complex rollover policies (partial rollover, rollover expiry) — v1 supports: no rollover, or full rollover (uncapped). Capped rollover is a future enhancement.

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase14-recurring-work-retainers.md`, plus ADRs for key decisions.

### 1. Retainer entity

Design a **Retainer** entity and supporting infrastructure:

1. **Data model**

    - `Retainer` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `customer_id` (UUID, FK → customers — the client under this retainer).
        - `name` (VARCHAR — human-readable label, e.g., "Monthly Bookkeeping Retainer").
        - `type` (ENUM: `FIXED_FEE`, `HOUR_BANK`).
        - `status` (ENUM: `DRAFT`, `ACTIVE`, `PAUSED`, `EXPIRED`, `CANCELLED`).
        - `currency` (VARCHAR(3) — ISO 4217, all financial amounts in this currency).
        - `billing_frequency` (ENUM: `MONTHLY`, `QUARTERLY`, `ANNUALLY`).
        - `billing_anchor_day` (INTEGER, 1-28 — the day of the month/quarter/year when billing cycles start. Capped at 28 to avoid month-length issues).
        - `base_fee` (DECIMAL(14,2) — the fixed fee per period. For FIXED_FEE type, this is the invoice amount. For HOUR_BANK, this is the base fee that covers the allocated hours).
        - `allocated_hours` (DECIMAL(10,2), nullable — only for HOUR_BANK type. Hours included per period).
        - `overage_rate` (DECIMAL(12,2), nullable — only for HOUR_BANK type. Rate per hour for usage beyond allocation).
        - `rollover_policy` (ENUM: `NONE`, `UNLIMITED` — what happens to unused hours. NONE = lost. UNLIMITED = carry forward indefinitely).
        - `start_date` (DATE — when the retainer begins. First billing period starts on or after this date).
        - `end_date` (DATE, nullable — when the retainer ends. Null = open-ended).
        - `next_billing_date` (DATE — computed: the next date a billing cycle closes and an invoice should be generated).
        - `notes` (TEXT, nullable — internal notes about the retainer agreement).
        - `created_by` (UUID — member who created the retainer).
        - `created_at`, `updated_at` timestamps.
    - Constraints:
        - `allocated_hours` and `overage_rate` are required when `type = HOUR_BANK`, null when `type = FIXED_FEE`.
        - `base_fee` must be positive.
        - `billing_anchor_day` between 1 and 28.
        - `start_date` must be before `end_date` (if end_date is set).
        - A customer can have multiple retainers (different services), but business logic should warn (not prevent) if a customer already has an active retainer.
    - Indexes:
        - `(customer_id, status)` for listing retainers per customer.
        - `(status, next_billing_date)` for the scheduler query ("active retainers due for billing").
        - `(created_at)` for chronological listing.

2. **Retainer lifecycle and state transitions**

    ```
    DRAFT → ACTIVE → PAUSED → ACTIVE (resume)
               ↓         ↓
           EXPIRED   CANCELLED
               ↓
           CANCELLED
    ```

    - **DRAFT**: Editable. All fields can be modified. Can be deleted (hard delete).
    - **DRAFT → ACTIVE**: Validates all required fields. Computes `next_billing_date` based on `start_date` and `billing_frequency`. The retainer begins accruing.
    - **ACTIVE → PAUSED**: Temporarily suspends billing. Current period's allocation is frozen (no new time accrues against it). `next_billing_date` is not advanced.
    - **PAUSED → ACTIVE**: Resumes the retainer. Recomputes `next_billing_date` from today.
    - **ACTIVE → EXPIRED**: Automatic when `end_date` is reached and the final billing period closes. Generates a final invoice.
    - **ACTIVE → CANCELLED** / **PAUSED → CANCELLED**: Manual cancellation. Generates a final invoice for any outstanding period. Cancelled retainers retain their data for audit trail.
    - **EXPIRED → CANCELLED**: Allows cancelling an expired retainer (e.g., if the final invoice was disputed).

    Invalid transitions (e.g., CANCELLED → ACTIVE, DRAFT → PAUSED) must be rejected with a 409 Conflict.

### 2. Retainer period tracking

Design a **RetainerPeriod** entity to track per-period hour bank usage:

1. **Data model**

    - `RetainerPeriod` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `retainer_id` (UUID, FK → retainers).
        - `period_start` (DATE — inclusive start of this billing period).
        - `period_end` (DATE — inclusive end of this billing period).
        - `allocated_hours` (DECIMAL(10,2) — hours allocated for this period, copied from retainer + any rollover).
        - `used_hours` (DECIMAL(10,2), default 0 — computed: sum of billable time entries for this customer in this period).
        - `rolled_over_hours` (DECIMAL(10,2), default 0 — unused hours carried from the previous period).
        - `overage_hours` (DECIMAL(10,2), default 0 — max(0, used_hours - allocated_hours)).
        - `status` (ENUM: `OPEN`, `CLOSED`, `INVOICED`).
        - `invoice_id` (UUID, nullable, FK → invoices — the invoice generated for this period).
        - `created_at`, `updated_at` timestamps.
    - Constraints:
        - One `OPEN` period per retainer at a time.
        - `period_start` < `period_end`.
        - `(retainer_id, period_start)` unique — no overlapping periods.
    - Indexes:
        - `(retainer_id, status)` for finding the current open period.
        - `(retainer_id, period_start)` for chronological period listing.

2. **Period lifecycle**

    - **OPEN**: The current active period. `used_hours` is recomputed on read (query against time entries, not stored — or updated periodically by the scheduler).
    - **CLOSED**: The period has ended and an invoice should be generated. `used_hours`, `overage_hours` are finalized.
    - **INVOICED**: An invoice has been generated for this period. `invoice_id` is set.

3. **Hour computation**

    - `used_hours` for an OPEN period is computed dynamically: `SELECT SUM(duration) FROM time_entries WHERE project_id IN (projects for this customer) AND billable = true AND date BETWEEN period_start AND period_end AND invoice_id IS NULL`.
    - When the period closes, `used_hours` is frozen (stored permanently).
    - `rollover_hours` for a new period = previous period's `max(0, allocated_hours - used_hours)` if rollover policy is UNLIMITED, else 0.
    - `allocated_hours` for a new period = retainer's `allocated_hours` + `rolled_over_hours`.

### 3. Job scheduling backbone

Design a lightweight, tenant-aware scheduling infrastructure:

1. **Design principles**

    - **Tenant-aware**: Each job iterates over all active tenants and executes tenant-scoped work inside `ScopedValue.where(RequestScopes.TENANT_ID, schema).call()`.
    - **Idempotent**: Every job checks whether its work has already been done for the current cycle before proceeding. Safe to re-run after restarts.
    - **Observable**: Each job run logs its execution (tenant, result, errors) to a `ScheduledJobLog` entity for debugging and audit.
    - **Configurable**: Job schedules are defined in `application.yml` properties, not hardcoded. Default: daily at 02:00 UTC.

2. **ScheduledJobLog entity**

    - `id` (UUID).
    - `tenant_id` (VARCHAR — the tenant this run executed for, or "SYSTEM" for cross-tenant operations).
    - `job_name` (VARCHAR — e.g., "retainer-billing", "dormancy-check").
    - `started_at` (TIMESTAMP).
    - `completed_at` (TIMESTAMP, nullable).
    - `status` (ENUM: `RUNNING`, `COMPLETED`, `FAILED`).
    - `result_summary` (TEXT, nullable — e.g., "Generated 3 invoices", "No retainers due").
    - `error_message` (TEXT, nullable — stack trace or error detail on failure).
    - This entity lives in the `public` schema (cross-tenant, since it logs across all tenants).
    - Retention: configurable, default 90 days. Old logs are cleaned up by a separate housekeeping job.

3. **TenantAwareJob abstraction**

    - A base class or interface that:
        - Receives a list of active tenant schemas.
        - For each tenant, binds `ScopedValue.where(TENANT_ID, schema)` and calls the job's `executeForTenant(schema)` method.
        - Catches exceptions per-tenant (one tenant's failure doesn't block others).
        - Logs results to `ScheduledJobLog`.
    - Concrete jobs extend this and implement `executeForTenant()`.

4. **Retainer billing job** (first concrete job)

    - Schedule: configurable, default daily at 02:00 UTC.
    - Logic per tenant:
        1. Query active retainers where `next_billing_date <= today`.
        2. For each due retainer:
            a. Close the current `RetainerPeriod` (finalize `used_hours`, compute `overage_hours`).
            b. Generate a draft `Invoice` from the retainer terms:
                - Fixed-fee: single line item with `base_fee`.
                - Hour bank: line item for `base_fee` + line item for overage (`overage_hours × overage_rate`) if any.
            c. Set the period's `status = INVOICED`, `invoice_id = generated invoice`.
            d. Create the next `RetainerPeriod` (with rollover if applicable).
            e. Advance the retainer's `next_billing_date`.
        3. If retainer has `end_date` and next period would extend beyond it, set status to `EXPIRED` instead of creating a new period.
    - Idempotency: check `RetainerPeriod.status != OPEN` for the current period date range before processing. If already CLOSED or INVOICED, skip.

### 4. API endpoints

Full endpoint specification:

1. **Retainer CRUD**

    - `GET /api/retainers` — list retainers with filters: `customerId`, `status`, `type`. Paginated.
    - `GET /api/retainers/{id}` — get retainer with current period summary.
    - `POST /api/retainers` — create a new draft retainer. Body: `{ customerId, name, type, currency, billingFrequency, billingAnchorDay, baseFee, allocatedHours?, overageRate?, rolloverPolicy?, startDate, endDate?, notes? }`.
    - `PUT /api/retainers/{id}` — update a draft retainer. Only valid for DRAFT status.
    - `DELETE /api/retainers/{id}` — delete a draft retainer. Only valid for DRAFT status. Hard delete.

2. **Retainer lifecycle transitions**

    - `POST /api/retainers/{id}/activate` — transition DRAFT → ACTIVE. Validates, computes next billing date, creates first RetainerPeriod.
    - `POST /api/retainers/{id}/pause` — transition ACTIVE → PAUSED.
    - `POST /api/retainers/{id}/resume` — transition PAUSED → ACTIVE. Recomputes next billing date.
    - `POST /api/retainers/{id}/cancel` — transition ACTIVE|PAUSED → CANCELLED. Generates final invoice if needed.

3. **Retainer period queries**

    - `GET /api/retainers/{id}/periods` — list all periods for a retainer, ordered by period_start desc. Includes computed `used_hours` for the current OPEN period.
    - `GET /api/retainers/{id}/periods/current` — get the current OPEN period with real-time hour bank status (allocated, used, remaining, overage).

4. **Manual billing trigger**

    - `POST /api/retainers/{id}/generate-invoice` — manually trigger invoice generation for the current period, closing it early. Useful for mid-cycle billing or testing. Admin/owner only.

5. **Scheduler admin endpoints** (admin/owner only)

    - `GET /api/admin/jobs` — list recent job runs with status and summaries. Paginated.
    - `POST /api/admin/jobs/{jobName}/run` — manually trigger a job run (for testing/debugging). Returns the job log entry.

For each endpoint specify:
- Auth requirement (valid Clerk JWT, appropriate role).
- Tenant scoping.
- Permission checks (see permissions model above).
- Request/response DTOs.

### 5. Frontend — Retainer management

Design the frontend views:

1. **Customer detail → "Retainers" tab** (new tab)

    - List of retainers for this customer. Columns: name, type badge (Fixed Fee / Hour Bank), status badge, billing frequency, base fee, current period usage (progress bar for hour bank), next billing date.
    - "New Retainer" button → opens creation dialog.
    - Click on retainer → navigates to retainer detail page.

2. **Retainer detail page** (`/retainers/{id}`)

    - **Header**: Retainer name, customer name (link), status badge, type badge.
    - **Summary cards**: Base fee per period, billing frequency, next billing date. For hour bank: allocated hours, used hours (with progress bar), remaining hours, overage hours.
    - **Draft view**: Editable form for all fields. "Activate" button.
    - **Active view**: Read-only terms. Current period usage card. Action buttons: Pause, Cancel, Generate Invoice (manual trigger).
    - **Period history**: Table of past periods — period dates, allocated hours, used hours, overage, invoice number (link). Expandable to show constituent time entries.

3. **Retainer creation dialog**

    - Step 1: Select customer, name, type (Fixed Fee / Hour Bank), currency.
    - Step 2: Billing terms — frequency, anchor day, base fee. For hour bank: allocated hours, overage rate, rollover policy.
    - Step 3: Schedule — start date, end date (optional).
    - Creates a DRAFT retainer. User activates separately.

4. **Retainers list page** (`/retainers`) — new sidebar nav item

    - All retainers across all customers. Filterable by status, type, customer.
    - Summary cards at top: active retainers count, total monthly recurring revenue (sum of base fees normalized to monthly), retainers due for billing this week.
    - Table: name, customer, type, status, base fee, frequency, next billing date, current utilization (hour bank only).

5. **Dashboard integration**

    - Add a "Recurring Revenue" card to the company dashboard (Phase 9) showing: total MRR, active retainers count, retainers due this week.
    - Add retainer status to the customer detail summary.

### 6. Notification integration

- **RETAINER_ACTIVATED**: Notify org admins/owners when a retainer goes active.
- **RETAINER_PAUSED**: Notify org admins/owners.
- **RETAINER_CANCELLED**: Notify org admins/owners and the retainer creator.
- **RETAINER_EXPIRED**: Notify org admins/owners and the retainer creator.
- **RETAINER_PERIOD_CLOSING**: Notify org admins/owners 3 days before a billing period closes (heads-up to review time entries).
- **RETAINER_OVERAGE**: Notify org admins/owners when hour bank usage exceeds allocation mid-period.
- **RETAINER_INVOICE_GENERATED**: Notify org admins/owners when the scheduler generates a draft invoice from a retainer.
- Use the existing `ApplicationEvent` → `NotificationHandler` pipeline from Phase 6.5.

### 7. Audit integration

Publish audit events for:
- `RETAINER_CREATED` — draft created.
- `RETAINER_UPDATED` — draft modified.
- `RETAINER_ACTIVATED` — transitioned to active.
- `RETAINER_PAUSED` — paused.
- `RETAINER_RESUMED` — resumed from pause.
- `RETAINER_CANCELLED` — cancelled.
- `RETAINER_EXPIRED` — auto-expired.
- `RETAINER_PERIOD_CLOSED` — period closed by scheduler, includes hours summary.
- `RETAINER_INVOICE_GENERATED` — invoice auto-generated, includes invoice number and amount.
- `SCHEDULED_JOB_COMPLETED` — job run completed, includes summary.
- `SCHEDULED_JOB_FAILED` — job run failed, includes error.

### 8. ADRs for key decisions

Add ADR-style sections for:

1. **Retainer billing model — fixed-fee vs. hour bank as type enum**:
    - Why a single entity with a `type` discriminator (not separate FixedFeeRetainer / HourBankRetainer entities).
    - Trade-off: simpler queries and UI vs. nullable fields that only apply to one type.
    - Why nullable `allocated_hours` and `overage_rate` are acceptable (validated by type).

2. **Hour bank computation — query-time vs. stored**:
    - Why `used_hours` for OPEN periods is computed dynamically (always accurate) but frozen on period close (immutable for invoicing).
    - Alternative considered: real-time materialized view. Rejected for complexity.
    - How this avoids double-counting with the existing `invoice_id` billing status on time entries.

3. **Scheduling infrastructure — Spring @Scheduled vs. external scheduler**:
    - Why Spring's built-in `@Scheduled` with tenant iteration (not Quartz, not external cron).
    - Trade-offs: simplicity and single-deployable vs. distributed execution and job queuing.
    - Why idempotent design makes "missed runs" a non-issue.
    - How `ScheduledJobLog` provides observability without a dedicated job monitoring tool.
    - Reuse path: dormancy detection, retention enforcement, subscription renewals.

4. **Period-based tracking — explicit RetainerPeriod entity vs. computed windows**:
    - Why explicit period records (not computed from retainer start date + frequency).
    - Benefits: rollover tracking, period-specific notes, invoice linkage, auditability.
    - Trade-off: more storage vs. simpler rollover and billing logic.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Keep the design **generic and industry-agnostic** — retainers and recurring billing are universal to all professional services verticals. Do not introduce legal, accounting, or agency-specific billing concepts.
- All monetary amounts use `BigDecimal` / `DECIMAL` — never floating-point.
- Currency is always explicit (stored alongside every monetary value) — never implicit or derived.
- Build on Phase 10's invoice infrastructure — the retainer billing job creates Invoice entities using the existing invoice domain model. No parallel invoicing system.
- Build on Phase 8's rate card infrastructure — overage billing uses the existing rate hierarchy for rate lookups.
- Build on Phase 6.5's notification infrastructure — retainer lifecycle events use the existing `ApplicationEvent` → notification handler pipeline.
- Build on Phase 6's audit infrastructure — all retainer mutations publish audit events.
- Build on Phase 4's customer model — retainers belong to customers.
- The scheduling backbone is designed for reuse — it's not retainer-specific. Future jobs (dormancy detection from Phase 13, retention enforcement, subscription renewals) plug into the same `TenantAwareJob` abstraction.
- The scheduler runs in-process on the backend. For v1, this is acceptable because the job workload is light (iterate tenants, query retainers, generate invoices). If the platform scales to thousands of tenants, the scheduler can be extracted to a dedicated worker without changing the job logic.
- Frontend additions are consistent with the existing Shadcn UI design system and component patterns.
- The schema supports forward compatibility for: capped rollover (max rollover hours), proration, retainer-specific rate overrides, customer portal retainer visibility, retainer renewal/extension workflows.

Return a single markdown document as your answer, ready to be added as `architecture/phase14-recurring-work-retainers.md` and ADRs.
