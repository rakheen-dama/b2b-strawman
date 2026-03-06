# ADR-150: Weekly vs. Daily Allocation Granularity

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 38 (Resource Planning & Capacity)

## Context

The resource planning system needs a time granularity for allocations — the smallest unit at which a manager can say "Member X is spending Y hours on Project Z." The choice of granularity has cascading effects on data volume, UI complexity, planning overhead, and how well the tool matches real-world professional services workflows.

Professional services firms (accounting, legal, consulting) plan work at the engagement level: "Bob is on the Smith audit this week" or "Alice has 20 hours on the tax return next week." They do not typically plan at the granularity of "Bob spends 4 hours on Smith Monday, 3 hours Tuesday, 5 hours Wednesday." That level of precision belongs to staff augmentation or shift-based scheduling — a different domain entirely.

## Options Considered

1. **Daily allocation** — one record per member per project per day.
   - Pros: Maximum precision; supports day-level utilization views; natural fit for shift-based scheduling.
   - Cons: 5x more records than weekly (one per working day vs. one per week); UI requires a day-level grid that is harder to scan at team scale; planning overhead is high — managers must decide hours per day, not per week; most professional services firms don't plan at this granularity.

2. **Weekly allocation (ISO Monday start)** — one record per member per project per week.
   - Pros: Matches how firms actually plan ("Bob is on this engagement this week"); manageable data volume (~52 records per member per project per year); grid UI fits naturally on screen (members × weeks); simple UNIQUE constraint on `(member_id, project_id, week_start)`; leave reduction is straightforward (proportional to weekdays lost).
   - Cons: Cannot express "Bob is available Monday-Wednesday only this week" (but leave blocks handle full-day absences); less precise than daily for firms that need sub-week visibility.

3. **Monthly allocation** — one record per member per project per month.
   - Pros: Minimal data volume; high-level strategic planning view.
   - Cons: Too coarse for operational planning — a month is too long to detect over-allocation or respond to changes; cannot show week-by-week trends; poor fit for short engagements (1-2 week jobs); utilization reporting lacks the resolution needed to act on capacity issues.

4. **Flexible granularity (configurable per org)** — daily, weekly, or monthly as an org setting.
   - Pros: Maximum flexibility; each firm chooses what fits.
   - Cons: Significant implementation complexity — every query, UI component, and calculation must handle all three modes; testing surface triples; mixed-granularity comparisons are confusing; most firms would choose weekly anyway, so the flexibility adds cost without proportional value.

## Decision

Option 2 — Weekly allocation with ISO Monday start.

## Rationale

Weekly granularity is the natural planning cadence for professional services firms. Partners and managers think in weeks: "Who's on what next week?" and "Can we fit a new engagement in the first two weeks of April?" This matches the planning UX of established tools like Float, Productive.io, and Forecast — all of which default to weekly views for resource planning.

The data volume argument is significant at scale. A 20-person firm with 10 active projects generates roughly 200 allocation records per week at weekly granularity, versus 1,000 at daily. Over a year, that's ~10,000 vs. ~50,000 records — the weekly model keeps the `resource_allocations` table lean and queries fast without additional partitioning or archival strategies.

For sub-week precision needs (e.g., "Bob is out Wednesday"), the `LeaveBlock` entity handles date-range absences that proportionally reduce weekly capacity. This combination — weekly allocations plus date-level leave blocks — covers the operational needs of firms with 5-50 team members without the overhead of daily allocation management.

## Consequences

- `ResourceAllocation` has a `week_start` column (LocalDate, always a Monday) rather than a date range or single date
- UNIQUE constraint on `(member_id, project_id, week_start)` enforces one allocation per member per project per week
- Grid UI renders as members (rows) × weeks (columns) — a natural, scannable layout
- Leave blocks operate at date granularity and reduce weekly capacity proportionally — this bridges the gap between weekly allocations and day-level absences
- Firms needing daily precision must use time entries (actuals) rather than allocations (planned) for day-level tracking
- Future migration to daily granularity would require splitting existing weekly records — a non-trivial but feasible data migration if demand materialises
- Copy-week and bulk-fill operations are simple: duplicate records with shifted `week_start` values
