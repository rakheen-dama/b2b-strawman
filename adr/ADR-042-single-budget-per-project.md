# ADR-042: Single Budget Per Project

**Status**: Accepted

**Context**: Phase 8 adds project budgets to track financial and time consumption against planned limits. The existing `Project` entity has no budget-related fields (see context inventory Section 12). Budget tracking is a common requirement for professional services firms: fixed-fee projects have a monetary cap, retainer agreements have monthly hour limits, and time-and-materials projects often have "not to exceed" thresholds.

The key design question is the budget granularity: should a project have a single budget (one set of hour/amount limits), or should it support multiple budgets tied to phases, milestones, or time periods? More granular budgets provide tighter control but require additional entities (phases, milestones), more complex status calculations, and a significantly more involved admin UX.

Budget status is computed on read, consistent with the on-the-fly aggregation strategy from [ADR-022](ADR-022-time-aggregation-strategy.md). No materialized summary tables or background jobs maintain consumption state. Budget alerts fire synchronously via the existing `ApplicationEvent` + `NotificationEventHandler` pipeline from Phase 6.5 ([ADR-036](ADR-036-synchronous-notification-fanout.md)).

**Options Considered**:

1. **No budgets (track manually outside the system)** -- Rely on external spreadsheets or project management tools for budget tracking. The platform only provides raw time data.
   - Pros:
     - Zero implementation effort
     - No new entities or migrations
     - Users keep existing workflows for budget management
   - Cons:
     - Defeats the purpose of Phase 8's revenue infrastructure -- time data without budget context has limited financial value
     - No threshold alerts -- budget overruns are discovered only during manual review
     - Data lives in two places (time in DocTeams, budgets in spreadsheets), creating reconciliation overhead
     - Cannot show budget status on project dashboards -- key financial visibility is missing

2. **Single budget per project (one-to-one with Project)** -- A `ProjectBudget` entity with optional `budgetHours` and/or `budgetAmount` + `budgetCurrency`. One budget per project, enforced by a unique constraint on `project_id`.
   - Pros:
     - Covers the 80% case: fixed-fee projects (monetary cap), retainers (hour cap), and T&M with "not to exceed" (both caps)
     - Simple data model: one entity, one-to-one with Project, no phase or milestone dependencies
     - Straightforward status calculation: `SUM(consumed) / budget` for both hours and amount
     - Clean admin UX: one form per project, with hours and/or amount fields plus an alert threshold slider
     - Alert logic is simple: compare total consumed against threshold, fire once (deduplicated via `threshold_notified` flag)
     - Consistent with on-the-fly aggregation ([ADR-022](ADR-022-time-aggregation-strategy.md)) -- status is computed from `time_entries` at query time
   - Cons:
     - Cannot track budget consumption by project phase or milestone (e.g., "Discovery phase: 40 hours, Build phase: 200 hours")
     - Cannot set different alert thresholds for different phases
     - Rolling budgets (monthly resets) not supported -- budget is lifetime for the project
     - Projects with distinct deliverables or work packages cannot have separate budget tracking per deliverable

3. **Phased/milestone-based budgets** -- A `ProjectBudgetPhase` entity linked to project phases or milestones, each with its own hour and amount limits.
   - Pros:
     - Fine-grained budget control: each phase or milestone has its own budget and alert threshold
     - Supports complex project structures (discovery, design, build, QA as separate phases)
     - Better visibility into where budget is being consumed across project stages
   - Cons:
     - Requires a `Phase` or `Milestone` entity that does not exist in the current data model -- significant schema addition
     - Time entries must be attributed to a phase (new FK or tag), adding friction to the time logging UX
     - Admin UX complexity: multiple budget forms per project, phase assignment for time entries, per-phase status views
     - Status calculation is more complex: per-phase consumption, cross-phase totals, handling time entries not assigned to any phase
     - Alert logic must handle per-phase thresholds and project-wide aggregates
     - Over-engineers the v1 use case -- most small-to-medium professional services firms track a single project budget, not phase-level budgets

4. **Rolling budget with period resets** -- A single budget that resets on a configurable period (monthly, quarterly). Tracks consumption within each period, not lifetime.
   - Pros:
     - Natural model for retainer agreements ("100 hours per month")
     - Automatic reset -- no manual budget updates each period
   - Cons:
     - Requires period tracking infrastructure (period start/end dates, rollover logic, unused-hours-carry-forward decisions)
     - Status calculation must determine the current period and aggregate only within it
     - Historical period data must be preserved for reporting -- adds a `BudgetPeriod` entity or snapshot table
     - Mixing rolling and one-time budgets requires polymorphism or a budget-type discriminator
     - Significantly more complex than the use case warrants for v1

**Decision**: Single budget per project (Option 2).

**Rationale**: Option 2 provides the simplest useful budget model that covers the dominant use cases without introducing dependencies on entities or concepts that do not yet exist:

1. **80% coverage**: The three most common project budget scenarios in professional services are: (a) fixed-fee: "this project has a $50,000 cap" (monetary budget), (b) retainer/hours-based: "we allocated 200 hours for this engagement" (hours budget), (c) T&M with ceiling: "bill hourly but do not exceed $75,000 or 400 hours" (both). A single `ProjectBudget` with optional `budgetHours` and optional `budgetAmount` handles all three. At least one must be set (validation constraint).

2. **No phantom dependencies**: Phased budgets (Option 3) require a `Phase` or `Milestone` entity and a way to link time entries to phases. The current data model has `Project > Task > TimeEntry` ([ADR-021](ADR-021-time-tracking-model.md)). There is no phase concept. Introducing one for budget purposes would be the tail wagging the dog -- the budget feature should not drive the creation of a project structure feature. If phases are introduced in a future phase (resource planning, project templates), phased budgets can be layered on top.

3. **Consistent with existing patterns**: Budget status is computed identically to the time aggregation approach in [ADR-022](ADR-022-time-aggregation-strategy.md) -- `SUM` queries on `time_entries` at read time, no materialized tables. Hours consumed = `SUM(duration_minutes) / 60.0` for all entries (billable and non-billable, since hour budgets track total effort). Amount consumed = `SUM(duration_hours * billing_rate_snapshot)` where `billing_rate_currency = budget_currency` (currency matching per [ADR-041](ADR-041-multi-currency-store-in-original.md)).

4. **Alert integration**: The budget alert mechanism uses the existing `ApplicationEvent` + `NotificationEventHandler` pattern from Phase 6.5 ([ADR-036](ADR-036-synchronous-notification-fanout.md)). When `TimeEntryService` creates or updates an entry, it checks whether the project has a budget, computes consumption, and if the threshold is crossed, publishes a `BudgetThresholdEvent`. The handler creates notifications for project leads and org admins/owners. The `threshold_notified` flag on `ProjectBudget` prevents duplicate alerts; it resets when budget values are updated.

5. **Migration path to phased budgets**: If phased budgets are needed in the future:
   - Add a `ProjectBudgetPhase` entity with `projectBudgetId`, `phaseName`, `budgetHours`, `budgetAmount`, `budgetCurrency`, `alertThresholdPct`
   - Add a `phase` tag or FK on `TimeEntry` (or `Task`) to attribute time to phases
   - The single `ProjectBudget` becomes the project-wide aggregate, with `ProjectBudgetPhase` rows providing phase-level detail
   - Existing single-budget data is valid and requires no migration -- it simply has no phase breakdown

**Consequences**:
- New `ProjectBudget` entity (tenant-scoped, `TenantAware` + `@Filter` + RLS) with one-to-one unique constraint on `project_id`
- Fields: `budgetHours` (DECIMAL, nullable), `budgetAmount` (DECIMAL, nullable), `budgetCurrency` (VARCHAR(3), nullable, required if `budgetAmount` set), `alertThresholdPct` (INTEGER, default 80), `thresholdNotified` (BOOLEAN, default false), `notes` (TEXT, nullable)
- Validation: at least one of `budgetHours` or `budgetAmount` must be non-null
- Budget status API (`GET /api/projects/{projectId}/budget`) returns computed consumption, remaining, percentage, and status enum (`ON_TRACK`, `AT_RISK`, `OVER_BUDGET`)
- Budget amount consumption only counts time entries whose `billing_rate_currency` matches the `budget_currency` -- cross-currency entries are excluded from monetary consumption (per [ADR-041](ADR-041-multi-currency-store-in-original.md))
- Budget alerts fire via `ApplicationEvent` on time entry create/update, deduplicated by `threshold_notified` flag
- Tenant migration V18 creates the `project_budgets` table with RLS policy
- Audit events: `BUDGET_CREATED`, `BUDGET_UPDATED`, `BUDGET_DELETED` via the existing `AuditService` pattern
- No phased or rolling budget support in this version -- explicitly documented as future enhancement
