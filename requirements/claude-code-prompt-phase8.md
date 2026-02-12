You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema-per-tenant) tiers.
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups and "My Work" cross-project dashboard.
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Customer portal groundwork** (Phase 7 planned): magic links, read-model schema, portal contacts.

For **Phase 8**, I want to add the **revenue infrastructure layer** — the bridge between tracked work and financial insight. This phase introduces billing rates, cost rates, billable time classification, project budgets, and profitability views.

***

## Objective of Phase 8

Design and specify:

1. **Billing rate cards** — per-member default rates with project-level and customer-level overrides, supporting multiple currencies and effective date ranges.
2. **Cost rates** — internal cost-of-time per member for margin calculation.
3. **Billable time classification** — enrich existing time entries with a billable flag and point-in-time rate snapshots.
4. **Project budgets** — hours and/or monetary budgets per project with threshold-based alerts.
5. **Profitability views** — query-derived project P&L, customer lifetime value, and team utilization metrics.
6. **Organization currency settings** — default currency at the org level.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - External currency conversion services or exchange rate APIs — multi-currency means "store in original currency, aggregate by currency." No automatic conversion.
    - New scheduled jobs or background workers — budget alerts fire synchronously via the existing `ApplicationEvent` + notification pipeline from Phase 6.5.
    - Separate microservices — everything stays in the existing backend deployable.
- All monetary amounts use `BigDecimal` (backend) / formatted strings (API responses). No floating-point currency math.
- Currency codes follow ISO 4217 (e.g., "ZAR", "USD", "GBP", "EUR").

2. **Tenancy**

- All new entities (BillingRate, CostRate, ProjectBudget) follow the same tenant isolation model as existing entities:
    - Pro orgs: dedicated schema.
    - Starter orgs: `tenant_shared` schema with `tenant_id` column + Hibernate `@Filter` + RLS.
- All new entities must include Flyway migrations for both tenant and shared schemas.

3. **Permissions model**

- Billing rates:
    - Only org admins/owners can manage member default billing rates and cost rates (org-level settings).
    - Project leads and org admins/owners can manage project-level rate overrides.
    - Org admins/owners can manage customer-level rate overrides.
    - All project members can view the resolved rate for their own time entries (read-only).
- Project budgets:
    - Only project leads and org admins/owners can create or update a project budget.
    - All project members can view budget status (consumption, remaining).
- Profitability views:
    - Project financials tab visible to project leads and org admins/owners.
    - Customer financials visible to org admins/owners.
    - Org-wide profitability page visible to org admins/owners only.
    - Individual utilization: members can see their own utilization. Admins/owners see all members.

4. **Multi-currency model**

- Each organization has a `default_currency` setting (ISO 4217). Defaults to "USD" if not set.
- Billing rates and cost rates store their own `currency` field. New rates default to the org's `default_currency` but can always be overridden.
- Project budgets store `budget_currency` when a monetary budget is set.
- Aggregation queries (profitability, budget status) **group results by currency**. A project with ZAR and USD time entries shows two revenue line items.
- No currency conversion, no base-currency normalization. This is a future concern.
- Frontend always displays the currency code or symbol alongside monetary amounts.

5. **Out of scope for Phase 8**

- Invoice generation and lifecycle (draft, sent, paid) — future phase, needs PSP integration.
- Payment service provider integration (Stripe, Paystack, etc.) — explicitly deferred.
- Trust/IOLTA accounting — legal-vertical-specific, pluggable later.
- Currency conversion and exchange rates — no normalization across currencies.
- Recurring retainers and hour banks — separate domain.
- Resource planning and capacity forecasting — separate domain.
- Phased or milestone-based budgets — single budget per project in this phase.
- Expense tracking — only time-based costs in this phase.
- Approval workflows for rates or budgets — direct CRUD for authorized roles.

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase8-rate-cards-budgets-profitability.md`, plus ADRs for key decisions.

### 1. Billing rate cards

Design a **BillingRate** entity and rate resolution engine:

1. **Data model**

    - `BillingRate` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `member_id` (UUID, FK → members — whose rate this is).
        - `project_id` (UUID, nullable, FK → projects — if set, this is a project-specific override).
        - `customer_id` (UUID, nullable, FK → customers — if set, this is a customer-negotiated rate).
        - `currency` (VARCHAR(3) — ISO 4217 code).
        - `hourly_rate` (DECIMAL(12,2) — billing amount per hour).
        - `effective_from` (DATE — when this rate becomes active).
        - `effective_to` (DATE, nullable — null means "current / no end date").
        - `created_at`, `updated_at` timestamps.
    - Constraints:
        - A member-default rate has both `project_id` and `customer_id` as null.
        - A project override has `project_id` set, `customer_id` null.
        - A customer override has `customer_id` set, `project_id` null.
        - `project_id` and `customer_id` must NOT both be non-null (no project+customer compound overrides).
        - No overlapping effective date ranges for the same (member, project, customer) combination.
    - Indexes:
        - `(member_id, project_id, customer_id, effective_from)` for rate resolution queries.
        - `(project_id)` for listing project rate overrides.
        - `(customer_id)` for listing customer rate overrides.

2. **Rate resolution logic**

    Given a `(member_id, project_id, date)` tuple, resolve the effective billing rate:

    1. Look for a project-specific rate: `member_id` match, `project_id` match, `customer_id` is null, date within `[effective_from, effective_to]`.
    2. If no project rate, look for a customer-specific rate: resolve the project's `customer_id`, then find `member_id` match, `customer_id` match, `project_id` is null, date within range.
    3. If no customer rate, look for the member's default rate: `member_id` match, both `project_id` and `customer_id` null, date within range.
    4. If no rate found, return null (time entry cannot be valued).

    Implement this as a service method `resolveRate(memberId, projectId, date) → Optional<BillingRate>`.

3. **API endpoints**

    - `GET /api/billing-rates` — list billing rates with optional filters: `memberId`, `projectId`, `customerId`, `activeOnly` (boolean, filters to rates covering today's date).
    - `POST /api/billing-rates` — create a new rate. Validate no overlapping date ranges for the same scope.
    - `PUT /api/billing-rates/{id}` — update a rate. Allow changing `hourly_rate`, `currency`, `effective_from`, `effective_to`.
    - `DELETE /api/billing-rates/{id}` — delete a rate.
    - `GET /api/billing-rates/resolve?memberId={id}&projectId={id}&date={date}` — resolve the effective billing rate for a member on a project at a given date. Returns the resolved rate with its source level (member-default, customer-override, or project-override).

    For each endpoint specify:
    - Auth requirement (valid Clerk JWT, appropriate role).
    - Tenant scoping.
    - Permission checks (see permissions model above).
    - Request/response DTOs.

4. **Frontend**

    - **Org settings → "Rates & Currency" page**: Table of all members with their default billing rates. Inline edit or dialog to set/update rates. Currency selector defaults to org currency.
    - **Project settings → "Rates" tab**: Table of project-specific rate overrides per member. Add/edit/remove overrides. Show resolved rate alongside (to see what would apply without the override).
    - **Customer detail → "Rates" tab**: Table of customer-negotiated rate overrides per member. Same add/edit/remove pattern.

5. **Audit integration**

    - Publish audit events for `BILLING_RATE_CREATED`, `BILLING_RATE_UPDATED`, `BILLING_RATE_DELETED`.

### 2. Cost rates

Design a **CostRate** entity for internal member costs:

1. **Data model**

    - `CostRate` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `member_id` (UUID, FK → members).
        - `currency` (VARCHAR(3) — ISO 4217 code).
        - `hourly_cost` (DECIMAL(12,2) — internal cost per hour).
        - `effective_from` (DATE).
        - `effective_to` (DATE, nullable).
        - `created_at`, `updated_at` timestamps.
    - Cost rates do **not** have project or customer overrides — they represent the organization's internal cost for a member's time, regardless of which project or customer the time is billed to.
    - Same no-overlap constraint on effective date ranges per member.
    - Index: `(member_id, effective_from)` for resolution.

2. **Resolution logic**

    Given a `(member_id, date)` tuple, resolve the effective cost rate:
    - Find the `CostRate` for that member where date falls within `[effective_from, effective_to]`.
    - Return null if no cost rate is configured (cost tracking is optional).

    Service method: `resolveCostRate(memberId, date) → Optional<CostRate>`.

3. **API endpoints**

    - `GET /api/cost-rates?memberId={id}` — list cost rates for a member.
    - `POST /api/cost-rates` — create a cost rate.
    - `PUT /api/cost-rates/{id}` — update a cost rate.
    - `DELETE /api/cost-rates/{id}` — delete a cost rate.

    All cost rate endpoints are restricted to org admins/owners only.

4. **Frontend**

    - Integrated into the "Rates & Currency" org settings page alongside billing rates. Each member row shows both their billing rate and cost rate. Cost rates have their own edit dialog/inline edit.

5. **Audit integration**

    - Publish audit events for `COST_RATE_CREATED`, `COST_RATE_UPDATED`, `COST_RATE_DELETED`.

### 3. Billable time classification

Enrich the existing `TimeEntry` entity to support billable tracking:

1. **Schema changes to TimeEntry**

    - Add `billable` (BOOLEAN, default `true`) — whether this time entry is billable to the client.
    - Add `billing_rate_snapshot` (DECIMAL(12,2), nullable) — the billing rate resolved at entry creation. Frozen for historical accuracy.
    - Add `billing_rate_currency` (VARCHAR(3), nullable) — currency of the billing rate snapshot.
    - Add `cost_rate_snapshot` (DECIMAL(12,2), nullable) — the cost rate resolved at entry creation. Frozen for historical accuracy.
    - Add `cost_rate_currency` (VARCHAR(3), nullable) — currency of the cost rate snapshot.

    Billing and cost rates may be in different currencies (billing in client's currency, cost in org's currency).

2. **Rate snapshot behavior**

    - When a `TimeEntry` is **created**: resolve the billing rate and cost rate for `(member_id, project_id, entry_date)` and snapshot both into the time entry. If no rate is found, the snapshot is null.
    - When a `TimeEntry` is **updated** (date or project changed): re-resolve and re-snapshot rates for the new context.
    - Rate snapshots are **read-only** after creation — they represent the rate that was in effect at the time. Changing a BillingRate or CostRate does NOT retroactively update existing time entries.
    - A time entry's **billable value** = `duration_hours × billing_rate_snapshot` (null if either is null).
    - A time entry's **cost value** = `duration_hours × cost_rate_snapshot` (null if either is null).

3. **API changes to TimeEntry endpoints**

    - Existing `POST /api/projects/{projectId}/time-entries` and `PUT` endpoints:
        - Accept optional `billable` field (default `true`).
        - Return `billable`, `billingRateSnapshot`, `billingRateCurrency`, `costRateSnapshot`, `costRateCurrency`, `billableValue`, `costValue` in responses.
    - New endpoint: `PATCH /api/projects/{projectId}/time-entries/{id}/billable` — toggle the billable flag. Accepts `{ "billable": true/false }`. Useful for bulk reclassification.
    - Existing list/summary endpoints should support filtering by `billable=true/false`.

4. **Frontend changes**

    - **Log Time dialog**: Add a "Billable" checkbox (default checked). Below the duration field, show the resolved rate and estimated value: "2.5h × R1,800/hr = R4,500 ZAR" — updates live as the user types duration.
    - **Time entry list**: Add a billable indicator column (small icon or badge). Add a filter toggle: "All / Billable / Non-billable."
    - **Edit time entry**: Billable flag is editable. Rate snapshot is shown read-only for context.

5. **Migration strategy**

    - Existing time entries (created before this phase) will have `billable = true` (default), with null rate snapshots. Historical entries won't show billable values until rates are backfilled.
    - Optionally provide a one-time **backfill command** (admin-only API endpoint or management task) that resolves and snapshots rates for historical time entries based on their dates. This is a convenience, not a requirement.

### 4. Project budgets

Design a **ProjectBudget** entity for tracking budgets:

1. **Data model**

    - `ProjectBudget` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `project_id` (UUID, FK → projects — one budget per project, enforced by unique constraint).
        - `budget_hours` (DECIMAL(10,2), nullable — hour budget).
        - `budget_amount` (DECIMAL(14,2), nullable — monetary budget).
        - `budget_currency` (VARCHAR(3), nullable — required if `budget_amount` is set).
        - `alert_threshold_pct` (INTEGER, default 80 — percentage at which to trigger a budget alert notification).
        - `threshold_notified` (BOOLEAN, default false — prevents duplicate threshold notifications; reset when budget values change).
        - `notes` (TEXT, nullable — optional context, e.g., "Includes discovery phase only").
        - `created_at`, `updated_at` timestamps.
    - At least one of `budget_hours` or `budget_amount` must be set (validation constraint).
    - Index: `(project_id)` unique.

2. **Budget status calculation**

    Budget status is computed from time entries, not stored:

    - **Hours consumed**: `SUM(duration) FROM time_entries WHERE project_id = ? AND billable = true` (or all entries — decide in ADR whether non-billable time counts toward hour budgets).
    - **Amount consumed**: `SUM(billing_rate_snapshot × duration) FROM time_entries WHERE project_id = ? AND billable = true AND billing_rate_currency = budget_currency`.
    - **Hours remaining**: `budget_hours - hours_consumed`.
    - **Amount remaining**: `budget_amount - amount_consumed`.
    - **Percentage consumed**: `consumed / budget × 100` for both hours and amount.
    - **Status**: `ON_TRACK` (< alert threshold), `AT_RISK` (>= alert threshold, < 100%), `OVER_BUDGET` (>= 100%).

3. **Budget alert notifications**

    When a time entry is created or updated, check if the project has a budget and whether the new total crosses the alert threshold:

    - Publish a `BudgetThresholdEvent` via `ApplicationEvent`.
    - The existing notification handler creates notifications for **project leads and org admins/owners**.
    - Notification type: `BUDGET_ALERT`.
    - Deduplicate using `threshold_notified` flag on `ProjectBudget` — reset when budget values are updated.

4. **API endpoints**

    - `GET /api/projects/{projectId}/budget` — returns the budget and current status (consumed, remaining, percentage, status enum). Returns 404 if no budget is set.
    - `PUT /api/projects/{projectId}/budget` — create or update the project budget (upsert). Resets `threshold_notified` if budget values change.
    - `DELETE /api/projects/{projectId}/budget` — remove the budget.
    - `GET /api/projects/{projectId}/budget/status` — lightweight endpoint returning just the computed status (for dashboard widgets).

5. **Frontend**

    - **Project detail → "Budget" tab** (new tab or section within Financials):
        - Budget configuration form: hours, amount + currency, alert threshold slider (50%–100%).
        - Budget progress visualization: dual progress bars for hours and amount. Color-coded: green (on track), amber (at risk), red (over budget).
        - Consumed/remaining breakdown with percentages.
    - **Project list enhancement**: Optional budget status indicator on the project card/row (small colored dot or badge).

6. **Audit integration**

    - Publish audit events for `BUDGET_CREATED`, `BUDGET_UPDATED`, `BUDGET_DELETED`.

### 5. Profitability views

Design query-based profitability analytics (no new entities — derived from existing data):

1. **Project profitability**

    - **Endpoint**: `GET /api/projects/{projectId}/profitability`
    - **Response** (per currency):
        - `currency`: the currency code.
        - `totalBillableHours`: sum of billable time entry durations.
        - `totalNonBillableHours`: sum of non-billable time entry durations.
        - `totalHours`: sum of all time entry durations.
        - `billableValue`: sum of (duration × billing_rate_snapshot) for billable entries.
        - `costValue`: sum of (duration × cost_rate_snapshot) for all entries (billable and non-billable — cost is incurred regardless).
        - `margin`: `billableValue - costValue`.
        - `marginPercent`: `margin / billableValue × 100` (null if billableValue is 0).
    - **Query params**: `from` (date), `to` (date) for date range filtering.
    - Results grouped by `billing_rate_currency` for multi-currency projects.

2. **Customer profitability**

    - **Endpoint**: `GET /api/customers/{customerId}/profitability`
    - Same response structure as project profitability, aggregated across all projects linked to this customer.
    - **Query params**: `from`, `to`.

3. **Team utilization**

    - **Endpoint**: `GET /api/reports/utilization`
    - **Response**: array of member utilization records:
        - `memberId`, `memberName`.
        - `totalHours`: all time logged in the period.
        - `billableHours`: billable time logged.
        - `nonBillableHours`: non-billable time logged.
        - `utilizationPercent`: `billableHours / totalHours × 100`.
        - Per-currency breakdown of `billableValue` and `costValue`.
    - **Query params**: `from` (date, required), `to` (date, required), `memberId` (optional — filter to specific member for self-service).
    - Org admins/owners see all members. Regular members can query their own utilization only.

4. **Organization profitability summary**

    - **Endpoint**: `GET /api/reports/profitability`
    - **Response**: array of project profitability summaries (one entry per project+currency combination), sorted by margin descending.
    - **Query params**: `from`, `to`, `customerId` (optional).
    - Restricted to org admins/owners.

5. **Frontend — Profitability page** (new sidebar nav item, restricted to admins/owners)

    - **Utilization section**: Table of team members with utilization bars. Sortable by utilization %, billable hours, total hours. Date range picker.
    - **Project profitability section**: Table of projects ranked by margin. Columns: project name, customer, billable value, cost, margin, margin %. Currency grouped. Date range picker.
    - **Customer profitability section**: Table of customers ranked by lifetime billable value. Expandable to show per-project breakdown.

6. **Frontend — Project detail → "Financials" tab**

    - Shows project profitability data inline: billable value, cost, margin, hours breakdown.
    - Integrates with budget panel if a budget exists (side-by-side or stacked).
    - Visible to project leads and admins/owners.

7. **Frontend — Customer detail → "Financials" tab**

    - Shows customer lifetime profitability: total billable value, cost, margin across all projects.
    - Per-project breakdown table below.
    - Visible to admins/owners.

### 6. Organization currency settings

Extend the organization settings:

1. **Data model change**

    - Add `default_currency` (VARCHAR(3), default `'USD'`) to the organization/tenant settings. This could be:
        - A new column on an existing `OrgSettings` or tenant metadata entity (if one exists).
        - A new lightweight `OrgSettings` entity if none exists: `tenant_id` (unique), `default_currency`, `created_at`, `updated_at`.
    - The default currency is used as the default for new billing rates, cost rates, and project budgets — but can always be overridden.

2. **API endpoints**

    - `GET /api/settings` — returns org settings including `defaultCurrency`.
    - `PUT /api/settings` — update org settings. Only `defaultCurrency` in this phase, but the endpoint/entity should be extensible.
    - Restricted to org admins/owners.

3. **Frontend**

    - Org settings page: Currency selector dropdown (searchable list of ISO 4217 currencies, with common ones prioritized: ZAR, USD, GBP, EUR, AUD, CAD, etc.).
    - Changing the default currency does NOT retroactively change existing rates or budgets — it only affects the default for new ones.

### 7. ADRs for key decisions

Add ADR-style sections for:

1. **Rate resolution hierarchy** (project > customer > member default):
    - Why this three-level hierarchy.
    - Why project+customer compound overrides are not supported (simplicity, avoids combinatorial explosion).
    - How effective date ranges prevent ambiguity.

2. **Point-in-time rate snapshotting on time entries**:
    - Why billing and cost rates are frozen at time entry creation.
    - Trade-offs vs. always-current valuation.
    - How this enables accurate historical reporting and prevents "surprise" retroactive revaluation.

3. **Multi-currency: store-in-original, no conversion**:
    - Why currency conversion is deferred.
    - How group-by-currency aggregation works in practice.
    - What future conversion support would look like (exchange rate table, base currency, conversion-on-read).

4. **Single budget per project (v1)**:
    - Why not phased/milestone-based budgets.
    - How single budget covers 80% of use cases (fixed-fee projects, retainers with hour caps, simple project caps).
    - Migration path to phased budgets in the future.

5. **Profitability = billable value minus cost (margin-aware from day one)**:
    - Why including cost rates from the start (avoiding historical data gaps).
    - Why expense tracking is deferred (cost-of-time is the dominant cost in professional services).
    - How profitability views degrade gracefully when cost rates aren't configured (show revenue only, margin shows as "N/A").

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Keep the design **generic and industry-agnostic** — rate cards, budgets, and profitability analysis are universal to all professional services verticals. Do not introduce legal, accounting, or agency-specific concepts.
- All monetary amounts use `BigDecimal` / `DECIMAL` — never floating-point.
- Currency is always explicit (stored alongside every monetary value) — never implicit or derived.
- Build on Phase 5's time tracking infrastructure — do not redesign or replace `TimeEntry`.
- Build on Phase 6.5's notification infrastructure — budget alerts use the existing `ApplicationEvent` → notification handler pipeline.
- Build on Phase 6's audit infrastructure — all mutations publish audit events.
- Profitability views are **read-only derived queries** — no materialized tables, no separate storage. If performance becomes a concern, caching or materialized views can be introduced later.
- Budget status is **computed on read** — no background jobs maintaining budget consumption state.
- Frontend additions are consistent with the existing Shadcn UI design system and component patterns.
- The schema supports forward compatibility for future invoicing (Phase N+1): billable time entries with rate snapshots are the line-item source for future invoice generation.

Return a single markdown document as your answer, ready to be added as `architecture/phase8-rate-cards-budgets-profitability.md` and ADRs.
