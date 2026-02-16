# ADR-067: Period-Based Tracking -- Explicit RetainerPeriod Entity vs Computed Windows

**Status**: Accepted

**Context**: Retainers bill on a recurring schedule (monthly, quarterly, or annually). The system needs to track per-period data: which billing period is currently active, what the allocated/used/overage hours are, which invoice was generated for which period, and how rollover hours flow between periods.

The question is whether to model billing periods as explicit database records or compute them on the fly from the retainer's start date and billing frequency.

**Options Considered**:

1. **Explicit RetainerPeriod entity** -- A `retainer_periods` table with one row per billing period. Each period has its own `period_start`, `period_end`, `allocated_hours`, `used_hours`, `rolled_over_hours`, `overage_hours`, `status` (OPEN/CLOSED/INVOICED), and `invoice_id`. Periods are created explicitly: the first period is created when the retainer is activated, subsequent periods are created by the billing job when closing the previous period.
   - Pros: Rollover tracking is natural (each period records what was rolled over from the previous one). Invoice linkage is a direct FK. Frozen hour values provide an immutable audit trail. Period status enables idempotency (the billing job checks for OPEN status). Historical periods can be queried and displayed. Period-level notes or adjustments are possible in the future. The entity's lifecycle is self-documenting.
   - Cons: More storage (one row per period per retainer). The billing job must create new period records (not just advance a date). Period cleanup may be needed for long-running retainers.

2. **Computed windows from retainer start + frequency** -- No period table. Instead, compute the current billing period's start and end dates from the retainer's `start_date`, `billing_frequency`, and `billing_anchor_day`. Track rollover as a running total on the retainer entity. Track invoice linkage via a separate mapping.
   - Pros: Less storage. No period creation logic.
   - Cons: Rollover computation is complex (must replay all historical periods to compute the current allocation). No natural place to store frozen `used_hours` for past periods (would need a separate "period snapshot" on the invoice). Invoice linkage requires a mapping table or convention. Idempotency is harder (no period status to check). Historical usage queries require computing all past windows and querying time entries for each. Resume-from-pause recomputation is fragile.

3. **Hybrid: computed windows + snapshot on close** -- Compute windows dynamically but create a snapshot record when a period closes (for invoicing and audit).
   - Pros: Less storage for open/future periods. Snapshot provides immutability for closed periods.
   - Cons: Two different representations of "a period" (computed vs materialized). Rollover still requires replaying history. More complex code to handle both cases.

**Decision**: Option 1 -- Explicit `RetainerPeriod` entity.

**Rationale**: The explicit period approach makes every aspect of retainer billing simpler and more auditable:

- **Rollover**: Each period stores `rolled_over_hours` explicitly. No need to replay history. A period's total allocation is `retainer.allocated_hours + rolled_over_hours` -- directly readable.
- **Invoice linkage**: `invoice_id` FK on the period. One join to find "which invoice was generated for March 2026."
- **Frozen values**: When a period closes, `used_hours` and `overage_hours` are stored permanently. The invoice references these exact values. If time entries are edited after period close, the period's financials are unchanged.
- **Idempotency**: The billing job checks `WHERE status = 'OPEN'`. If a period is already CLOSED or INVOICED, it is skipped. This is a simple, reliable guard.
- **History display**: `SELECT * FROM retainer_periods WHERE retainer_id = ? ORDER BY period_start DESC` gives a complete billing history with all financial details.
- **Resume adjustment**: When a paused retainer resumes, the current OPEN period's `period_end` is adjusted. With computed windows, this adjustment would require storing an exception to the regular schedule.

The storage cost is minimal: one row per period per retainer. A monthly retainer running for 5 years produces 60 rows. Even at scale (1000 tenants, 50 retainers each, 12 periods/year), this is 600,000 rows -- trivial for PostgreSQL.

The trade-off is that the billing job must explicitly create new period records. This is intentional: period creation is a business event (with specific allocation, rollover, and date values) that should be recorded, not derived.

**Consequences**:
- `retainer_periods` table with one row per billing cycle per retainer.
- The billing job creates new period records (period creation is an explicit action, not implicit).
- Historical period data is directly queryable without computation.
- Rollover values are stored per-period, not computed from history.
- Invoice linkage is a simple FK on the period record.
- Period status (OPEN -> CLOSED -> INVOICED) provides a clear lifecycle and idempotency guard.
- The UI can display a complete period history table with all financial details.
- Future enhancements (capped rollover, period-level notes, manual adjustments) have a natural home in the period entity.
