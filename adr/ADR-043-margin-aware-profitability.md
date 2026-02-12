# ADR-043: Margin-Aware Profitability

**Status**: Accepted

**Context**: Phase 8 adds profitability views that derive financial insight from time entries, billing rates, and cost rates. The question is whether profitability should start as revenue-only (billable value from time entries) and add cost tracking later, or include cost-of-time from the beginning to provide margin analysis.

The platform already has the data infrastructure to support margin calculation: `TimeEntry` captures `durationMinutes` and `billable` flag (Phase 5, [ADR-021](ADR-021-time-tracking-model.md)), billing rate snapshots provide revenue per hour, and cost rate snapshots provide internal cost per hour (both snapshotted at creation per [ADR-040](ADR-040-point-in-time-rate-snapshotting.md)). The profitability calculation is straightforward: `margin = billableValue - costValue`, where `billableValue = SUM(duration_hours * billing_rate_snapshot)` for billable entries and `costValue = SUM(duration_hours * cost_rate_snapshot)` for all entries (cost is incurred regardless of whether time is billable).

In professional services, cost-of-time (internal labor cost) is the dominant cost component -- typically 60-80% of total costs. Other costs (travel, software licenses, subcontractors) exist but are a separate domain. The critical insight is that once an organization starts tracking time without cost rates, the historical time entries can never be accurately costed retroactively. You can backfill rate snapshots (see [ADR-040](ADR-040-point-in-time-rate-snapshotting.md)), but only if cost rates with appropriate effective date ranges were set up at some point. If cost rates are introduced six months after time tracking begins, those six months of time entries have no cost data -- the margin for that period is permanently unknowable.

**Options Considered**:

1. **Revenue-only views (no cost tracking)** -- Profitability views show only billable value and utilization. No cost rate entity, no cost snapshots on time entries, no margin calculation.
   - Pros:
     - Simplest implementation -- fewer entities, no cost rate management UI, no margin calculation
     - Useful on its own: revenue per project, per customer, per member is valuable for billing and forecasting
     - Faster to ship -- less surface area to implement and test
     - No need for the "cost rate is optional" degradation logic
   - Cons:
     - Revenue without cost is misleading -- a $100K project with $95K cost is not as healthy as a $50K project with $20K cost, but revenue-only views cannot distinguish them
     - Creates a historical data gap: if cost tracking is added later, all time entries created before that point have no cost data. Margin analysis can only start from the date cost rates were introduced.
     - Organizations that start with revenue-only views have no incentive to set up cost rates until they need margin analysis -- by then, months of historical cost data is lost
     - Professional services firms make staffing and pricing decisions based on margin, not revenue. Revenue-only views provide incomplete decision support.

2. **Full margin views with cost rates from day one** -- Both billing rates and cost rates are introduced in Phase 8. Time entry snapshots include both billing and cost rates. Profitability views show revenue, cost, margin, and margin percentage. Cost rates are optional -- views degrade gracefully when they are not configured.
   - Pros:
     - Provides complete financial picture from the start: revenue, cost, margin, margin percentage
     - Prevents the historical data gap -- organizations that set up cost rates early get accurate margin data from day one
     - Graceful degradation: if cost rates are not configured, `costRateSnapshot` is null, `costValue` is null, margin shows as "N/A" rather than zero
     - Cost rate entity is simpler than billing rate (no customer/project overrides per [ADR-039](ADR-039-rate-resolution-hierarchy.md)) -- just `(member_id, date) -> hourly_cost`
     - Aligns with the professional services value proposition: the platform helps firms understand which projects and clients are actually profitable, not just which ones generate the most revenue
     - Forward-compatible with expense tracking -- when expenses are added later, they slot into the existing margin framework (`margin = revenue - (labor_cost + expenses)`)
   - Cons:
     - More entities and UI surface in Phase 8: `CostRate` entity, cost rate management UI, margin columns in profitability views
     - Organizations must set up both billing and cost rates to get full value -- more onboarding friction
     - Cost rates are sensitive data (internal labor costs) -- adds a permission concern (restricted to org admins/owners)
     - "N/A" margin values may confuse users who expect a number -- requires clear UI messaging about why margin is unavailable

3. **Full P&L with expense tracking** -- In addition to cost rates, support direct expenses (travel, licenses, subcontractors) allocated to projects. Profitability = revenue - (labor cost + expenses).
   - Pros:
     - True project P&L -- not just labor margin but total profitability
     - Complete financial management within the platform
     - Expense data feeds into future invoicing (reimbursable expenses as invoice line items)
   - Cons:
     - Expense tracking is a significant domain: expense categories, receipt uploads, approval workflows, reimbursable vs. non-reimbursable, allocation rules for shared expenses
     - Requires new entities (`Expense`, `ExpenseCategory`), file upload integration (receipts to S3), and approval workflows (explicitly out of scope for Phase 8)
     - Over-engineers Phase 8 -- labor cost is the dominant cost in professional services. Adding expenses can be a separate phase.
     - Expense data quality depends on consistent manual entry -- unlike time entries (which are already captured), expenses are a new data entry burden

**Decision**: Full margin views with cost rates from day one (Option 2).

**Rationale**: Option 2 provides the financial insight that makes the platform genuinely useful for business decisions, while keeping the cost-rate model simple and making it optional for organizations that are not ready for it:

1. **Preventing the data gap**: This is the strongest argument for including cost rates from day one. Professional services profitability is measured in margins, and margin requires cost data. If Phase 8 ships revenue-only views and cost tracking is added in Phase 9 or 10, every organization that adopted Phase 8 has a gap: months of time entries with billing rate snapshots but no cost rate snapshots. The backfill endpoint from [ADR-040](ADR-040-point-in-time-rate-snapshotting.md) can retroactively apply cost rates, but only if the organization actually creates `CostRate` entries with historical effective dates. Most will not bother, and the gap persists. Introducing cost rates alongside billing rates means the snapshot mechanism captures both from the start.

2. **Cost rate simplicity**: Unlike billing rates which have a three-level hierarchy ([ADR-039](ADR-039-rate-resolution-hierarchy.md)), cost rates are flat: one rate per member per date range. There are no project or customer overrides for internal labor cost -- the cost to the organization of a member's hour is the same regardless of which project or client the time is billed to. This makes the `CostRate` entity, resolution logic, and admin UI significantly simpler than `BillingRate`. The incremental effort to include cost rates in Phase 8 is modest relative to the billing rate infrastructure that is already being built.

3. **Graceful degradation**: The profitability views handle missing cost data without errors or misleading numbers. The rules are:
   - If `costRateSnapshot` is null (no cost rate was configured): `costValue` is null, `margin` is null, `marginPercent` displays as "N/A"
   - If `billingRateSnapshot` is null (no billing rate was configured): `billableValue` is null, `margin` is null
   - If both are present: full margin calculation (`billableValue - costValue`)
   - The frontend renders "N/A" badges in margin columns and shows a setup prompt: "Configure cost rates to see margin analysis"
   This ensures users are never shown a $0 cost (which implies zero margin = 100% profit) when the actual situation is "cost is unknown."

4. **Permission model**: Cost rates are restricted to org admins/owners only. Regular members and project leads cannot see internal labor costs. This matches the sensitivity of the data -- a member should not know their own (or a colleague's) internal cost rate, as this is an organizational financial metric, not an individual one. Profitability views that include margin data are similarly restricted to org admins/owners and project leads (for their own projects).

5. **Expense tracking deferral**: Expenses are explicitly deferred. In professional services, labor cost typically accounts for 60-80% of total project cost. Adding expense tracking (Option 3) would require a significant domain expansion (categories, receipts, approvals, allocations) that is out of scope. The margin framework built in Phase 8 is additive: when expenses are introduced in a future phase, they slot into the cost side of the equation. The profitability views would expand from `margin = billableValue - laborCost` to `margin = billableValue - (laborCost + expenses)` with no structural changes to existing data.

**Consequences**:
- `CostRate` entity (tenant-scoped) with `memberId`, `currency`, `hourlyCost` (BigDecimal), `effectiveFrom`, `effectiveTo` (nullable), no-overlap date constraint per member
- `CostRate` resolution: simple `(memberId, date)` lookup -- no hierarchy, no project/customer overrides
- `TimeEntry` snapshots include both `billingRateSnapshot`/`billingRateCurrency` and `costRateSnapshot`/`costRateCurrency`, populated at creation time
- Profitability endpoints return per-currency results (per [ADR-041](ADR-041-multi-currency-store-in-original.md)) with `billableValue`, `costValue`, `margin`, `marginPercent` -- margin fields are null when cost data is missing
- Cost rate management is restricted to org admins/owners; margin data in profitability views is restricted to org admins/owners and project leads
- Frontend displays "N/A" for margin when cost rate snapshots are null, with a setup prompt guiding users to configure cost rates
- Audit events for cost rate mutations: `COST_RATE_CREATED`, `COST_RATE_UPDATED`, `COST_RATE_DELETED` via the existing `AuditService` pattern from Phase 6
- Expense tracking is not included -- profitability measures labor margin only. Documented as a future enhancement.
- The profitability framework is additive: future expense tracking extends the cost side without restructuring existing calculations
- Utilization metric (`billableHours / totalHours * 100`) is available even without cost rates -- it depends only on the `billable` flag and `durationMinutes` on time entries
