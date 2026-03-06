# ADR-151: Planned vs. Actual Separation

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 38 (Resource Planning & Capacity)

## Context

The platform now has two sources of "hours spent on a project": `ResourceAllocation` (planned hours, forward-looking) and `TimeEntry` (actual hours, backward-looking). The question is whether these two systems should be coupled — specifically, whether allocations should constrain time logging. For example, if Bob is allocated 20 hours to Project A this week, should the system prevent him from logging time on Project B where he has no allocation?

This decision affects the core user experience of time tracking and resource planning. Professional services firms operate in environments where plans shift daily — a client calls with an urgent request, a team member gets pulled onto an ad-hoc task, or estimated effort turns out to be wrong. The relationship between planned and actual work is informational, not prescriptive.

## Options Considered

1. **Hard constraint** — members can only log time on projects they are allocated to for the relevant week.
   - Pros: Forces discipline in planning; ensures allocations stay up-to-date because gaps are immediately visible; planned-vs-actual comparison is always meaningful.
   - Cons: Blocks legitimate ad-hoc work — a member helping a colleague for 2 hours would need an allocation first; creates friction in time logging (the most frequent user action); forces managers to micro-manage allocations to keep them current; penalises firms with fluid work patterns; would require real-time allocation checks on every time entry save.

2. **Soft warning** — time logging shows a warning when a member logs time on a project they are not allocated to, but allows it.
   - Pros: Nudges planning discipline without blocking work; makes unplanned work visible.
   - Cons: Warning fatigue — if 30% of time entries trigger warnings, users learn to ignore them; still adds friction to time logging; implies allocations should be comprehensive (every project, every member, every week), which is unrealistic for many firms; adds UI complexity to the time entry flow.

3. **Full independence** — allocations and time entries are completely separate systems, compared only in reports.
   - Pros: Zero friction in time logging (the existing flow is unchanged); allocations are purely a planning tool, not an enforcement mechanism; planned-vs-actual comparison happens in reports where it belongs; works for firms that allocate strategically (only major engagements) without requiring 100% allocation coverage; no performance overhead on time entry saves.
   - Cons: Allocations can drift from reality if managers don't review utilization reports; no real-time feedback when actual diverges from planned.

## Decision

Option 3 — Full independence between allocations and time entries.

## Rationale

Professional services reality is messy. A tax accountant allocated 30 hours to an audit might spend 5 hours helping a colleague with a client query, 2 hours on internal admin, and 1 hour on an urgent regulatory filing — none of which were planned. Blocking or warning on these time entries would make the system hostile to real workflow and drive users toward workarounds (logging everything under one project, or abandoning time tracking).

The existing `TimeEntry` system (Phase 5) has been in production use across the platform and its UX is optimised for speed — log time, pick a task, enter hours, done. Adding allocation checks to this flow would degrade the experience for every time entry to serve a planning concern that only matters at the weekly review level.

The comparison between planned and actual is genuinely valuable — but it belongs in the utilization report and the capacity grid, not in the time entry form. `UtilizationService` combines both data sources to show planned utilization (from allocations) alongside actual utilization (from time entries), giving managers the insight they need without burdening individual contributors with allocation awareness during their daily time logging.

## Consequences

- `TimeEntry` saves have no dependency on `ResourceAllocation` — no additional queries, no validation changes
- The existing time logging UX is completely unchanged by Phase 38
- `UtilizationService` is the integration point: it queries both `ResourceAllocation` and `TimeEntry` for the same date ranges and computes planned vs. actual metrics
- Utilization reports show three metrics: planned utilization % (allocation / capacity), actual utilization % (time entries / capacity), and billable utilization % (billable time entries / capacity)
- Managers must proactively review utilization reports to spot divergence between planned and actual — the system does not push this information into the time entry workflow
- Firms that want tighter coupling can use Phase 37 automations (e.g., trigger a notification when actual hours on a project exceed allocated hours by more than 20%)
- Future phases could add an optional "soft warning" mode as an org setting without architectural changes, since the independence is a policy choice, not a structural constraint
