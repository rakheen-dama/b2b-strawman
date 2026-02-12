# ADR-040: Point-in-Time Rate Snapshotting

**Status**: Accepted

**Context**: Phase 8 adds automatic billing and cost rate resolution to time entries (see [ADR-039](ADR-039-rate-resolution-hierarchy.md) for the rate hierarchy). When a staff member logs 2 hours on a project, the system resolves the applicable billing rate (e.g., $200/hr) and cost rate (e.g., $120/hr) to compute the billable value ($400) and cost ($240). The question is: when should this rate resolution happen, and what happens when rates change?

The existing `TimeEntry` entity (Phase 5, [ADR-021](ADR-021-time-tracking-model.md)) has a `rateCents` field that is user-supplied at creation time. Phase 8 replaces this with system-resolved rates. The new fields -- `billingRateSnapshot`, `billingRateCurrency`, `costRateSnapshot`, `costRateCurrency` -- must be populated automatically. The critical design decision is whether these fields are frozen at creation time (snapshot approach) or resolved dynamically at query time (always-current approach).

This decision has significant implications for financial reporting accuracy. Professional services firms use historical time data for client invoicing, project profitability analysis, and revenue recognition. If a rate change retroactively revalues all historical time entries, it undermines trust in financial reports and can cause discrepancies between what was invoiced and what the system shows.

**Options Considered**:

1. **Always-current valuation (resolve rate at query time)** -- No rate fields on `TimeEntry`. When displaying or aggregating time entries, join with `BillingRate` and `CostRate` tables to resolve the applicable rate based on the entry's date and the rate's effective date range.
   - Pros:
     - No snapshot fields on `TimeEntry` -- simpler schema
     - Rate corrections instantly propagate to all affected time entries
     - No stale data -- reports always reflect the current rate configuration
     - No re-snapshot mechanism needed for rate corrections
   - Cons:
     - Historical reports change retroactively when rates are updated -- a project that showed $50K revenue last month might show $55K after a rate correction, breaking auditability
     - Query complexity: every time entry aggregation requires a multi-table join with date-range matching against the rate hierarchy (three levels from [ADR-039](ADR-039-rate-resolution-hierarchy.md))
     - Query performance: the rate resolution join is expensive -- for each time entry, the system must check project override, then customer override, then member default, filtering by effective date range. This turns simple `SUM(duration * rate)` into a correlated subquery or lateral join
     - Inconsistent with invoice line items -- if an invoice was generated from a time entry at $200/hr but the rate was later corrected to $175/hr, the system shows a different value than the invoice
     - Cannot determine the billable value of a time entry without the full rate hierarchy context -- makes data exports and API responses dependent on related entities

2. **Point-in-time snapshot at creation (freeze rate on TimeEntry)** -- When a time entry is created, resolve the billing and cost rates and store them directly on the `TimeEntry` row. These snapshot fields are read-only after creation.
   - Pros:
     - Financial data is immutable -- a time entry created at $200/hr shows $200/hr forever, matching what was (or will be) invoiced
     - Simple aggregation queries: `SUM(duration_hours * billing_rate_snapshot)` with no joins to rate tables
     - Time entries are self-contained -- API responses and data exports include the rate without additional lookups
     - Consistent with [ADR-022](ADR-022-time-aggregation-strategy.md) -- on-the-fly SQL aggregation works efficiently because the rate is co-located on the `TimeEntry` row
     - Forward-compatible with invoicing -- each time entry becomes a natural invoice line item with its own rate and value
     - Audit trail: the snapshot documents what rate was in effect at the time of entry, which is itself valuable metadata
   - Cons:
     - Rate corrections do not automatically propagate to existing time entries -- requires an explicit re-snapshot step
     - Additional columns on `TimeEntry` (billing rate, billing currency, cost rate, cost currency)
     - When a time entry's date or project is updated, the rate must be re-resolved and re-snapshotted
     - Historical time entries (created before Phase 8) will have null snapshots until backfilled

3. **Hybrid (snapshot + recalculation trigger)** -- Store rate snapshots on `TimeEntry`, but also provide a "recalculate" action that re-resolves rates for a set of time entries based on current rate configuration.
   - Pros:
     - Default behavior is snapshot-based (immutable, auditable)
     - Explicit recalculation provides an escape hatch for rate corrections
     - Administrators control when and which entries are revalued
     - Can target recalculation to specific date ranges, projects, or members
   - Cons:
     - Two sources of truth: the snapshot and the current rate table. Which one is "correct"?
     - Recalculation adds operational complexity -- must decide when to trigger, who has permission, and how to audit the change
     - Partial recalculation can leave some entries at old rates and others at new rates within the same project
     - Adds an admin endpoint, service method, and UI action that must be carefully permissioned and audited

**Decision**: Point-in-time snapshot at creation (Option 2), with a dedicated admin backfill endpoint for rate corrections.

**Rationale**: Option 2 provides the financial immutability that professional services firms require, while the backfill endpoint (borrowed from Option 3's recalculation concept) handles the practical need for rate corrections:

1. **Financial integrity**: In professional services, the rate at which time was valued when it was logged is a business fact. Changing rates retroactively without explicit action violates the principle of least surprise. If a project lead runs a profitability report on Monday showing $50K revenue, that same report should show $50K on Tuesday -- unless someone explicitly corrected rates and re-snapshotted. Always-current valuation (Option 1) violates this by silently revaluing history whenever rates change.

2. **Query simplicity**: The on-the-fly aggregation strategy from [ADR-022](ADR-022-time-aggregation-strategy.md) depends on efficient `SUM/GROUP BY` queries on `time_entries`. With snapshots, profitability queries are `SUM(duration * billing_rate_snapshot) GROUP BY billing_rate_currency` -- a single-table scan. Without snapshots, every aggregation query must join against `billing_rates` with a three-level fallback (project > customer > member default) and date-range matching. For a project with 1,000 time entries, this means 1,000 correlated subqueries or a complex lateral join. The snapshot approach keeps aggregation O(n) on the time entries table.

3. **Invoice compatibility**: Phase 8 is explicitly designed as the bridge to future invoicing. Each time entry with a billing rate snapshot is a natural invoice line item: "2 hours at $200/hr = $400 USD." If rates were resolved at query time, the system could show a different value than what appears on a previously generated invoice. Snapshots ensure consistency between the system's records and any external documents derived from them.

4. **Rate correction workflow**: When a rate is entered incorrectly, the admin creates a new `BillingRate` with the corrected value and appropriate effective dates. This does not affect existing time entries (the old snapshot persists). If the admin wants to propagate the correction, they use the backfill endpoint: `POST /api/admin/time-entries/re-snapshot` with filters (`memberId`, `projectId`, `dateFrom`, `dateTo`). The endpoint re-resolves rates for matching entries, updates the snapshots, and logs an audit event for each change. This is an explicit, auditable action -- not a silent side effect.

5. **Re-snapshot on context change**: When a time entry's `date` or `projectId` is updated (via the existing `PUT` endpoint), the rate is re-resolved and re-snapshotted for the new context. This is correct because the entry is being reattributed to a different time or project, so the applicable rate may differ. The old snapshot is replaced, and the change is captured in the audit delta (existing pattern from `TimeEntryService.updateTimeEntry`).

**Consequences**:
- `TimeEntry` gains four new columns: `billing_rate_snapshot` (DECIMAL(12,2), nullable), `billing_rate_currency` (VARCHAR(3), nullable), `cost_rate_snapshot` (DECIMAL(12,2), nullable), `cost_rate_currency` (VARCHAR(3), nullable)
- `TimeEntryService.createTimeEntry()` calls `BillingRateService.resolveRate()` and `CostRateService.resolveCostRate()` to populate snapshots at creation time
- `TimeEntryService.updateTimeEntry()` re-resolves and re-snapshots rates when `date` or `projectId` changes
- Null snapshots mean "no rate was configured at the time of entry" -- billable value and cost value are null, margin shows as "N/A"
- Existing time entries (pre-Phase 8) will have null snapshots; an optional backfill endpoint allows admins to retroactively resolve rates
- Profitability queries (see [ADR-043](ADR-043-margin-aware-profitability.md)) use snapshot fields directly: `SUM(duration_hours * billing_rate_snapshot)` grouped by `billing_rate_currency`
- Backfill endpoint (`POST /api/admin/time-entries/re-snapshot`) accepts date range, member, and project filters; re-resolves rates and publishes audit events for each updated entry
- The existing `rateCents` field on `TimeEntry` is superseded but retained for backward compatibility; new entries no longer populate it
- Billing and cost rate snapshots may be in different currencies (billing in client currency, cost in org currency) -- this is intentional and mirrors real-world practice
