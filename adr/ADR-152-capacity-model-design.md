# ADR-152: Capacity Model Design

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 38 (Resource Planning & Capacity)

## Context

Resource planning requires knowing how many hours each team member is available per week. This "capacity" is the denominator in every utilization calculation and the baseline against which over-allocation is measured. The model must handle full-time staff (40 hours/week), part-time staff (e.g., 24 hours/week), and capacity changes over time (e.g., a member goes from full-time to 4 days/week starting next month).

The design must balance expressiveness against complexity. Professional services firms typically have simple capacity models — most staff work standard hours, with occasional changes for part-time arrangements or phased returns from leave. The system should handle these cases well without over-engineering for edge cases that belong in HR software.

## Options Considered

1. **Single weeklyHours with effective dates** — a `MemberCapacity` entity with `weeklyHours`, `effectiveFrom`, and optional `effectiveTo`. Resolution: find the record where the target week falls within the effective date range. Falls back to an org-wide default (`OrgSettings.defaultWeeklyCapacityHours`).
   - Pros: Simple to understand and implement; covers full-time, part-time, and capacity changes over time; the effective-date pattern is already proven in the codebase (`BillingRate.effectiveFrom/To`); org-wide default means most members need zero configuration; leave handled separately via `LeaveBlock`.
   - Cons: Cannot express "available Mon-Wed only" within a week (but weekly allocation granularity means this rarely matters); a member with multiple capacity changes generates multiple records (manageable volume).

2. **Complex shift patterns** — daily availability patterns per member (e.g., Monday 8h, Tuesday 6h, Wednesday 0h, Thursday 8h, Friday 8h).
   - Pros: Maximum precision; can model irregular schedules, compressed work weeks, and day-specific availability.
   - Cons: Significant implementation complexity — capacity calculation requires summing day-level patterns per week; UI needs a schedule editor; most professional services staff work standard hours, making this overkill; interacts poorly with weekly allocation granularity (if allocations are weekly, daily capacity detail adds noise without actionable precision).

3. **Role-based defaults** — capacity derived from member role (e.g., partner = 30h, senior = 40h, junior = 40h, intern = 20h).
   - Pros: Zero per-member configuration; consistent across the org.
   - Cons: Members don't have a "seniority role" field currently — would need a new entity or enum; same role can have different hours (a part-time senior vs. full-time senior); doesn't handle individual capacity changes; conflates organisational hierarchy with availability.

4. **Calendar-integrated capacity** — sync available hours from Google Calendar or Outlook. Capacity derived from "free" time blocks.
   - Pros: Always up-to-date; reflects actual availability including meetings and personal commitments.
   - Cons: Requires external integration (Google/Microsoft APIs) — Phase 38 has no integration ports; calendar availability != work capacity (a "free" block during lunch isn't work time); unreliable for planning (calendars change constantly); privacy concerns with reading staff calendars; hard dependency on external service availability.

## Decision

Option 1 — Single `weeklyHours` with effective dates and org-wide default fallback.

## Rationale

The effective-dated capacity model follows the same pattern already established for billing rates ([ADR-039](../adr/ADR-039-rate-hierarchy.md)) — a design that has proven robust across the platform. The resolution logic is identical in shape: find the record with the latest `effectiveFrom` that is on or before the target date, with a fallback chain (member-specific record -> org default -> hard default of 40.0).

For the target user base (professional services firms with 5-50 staff), capacity configurations are rare events — set once per member, changed occasionally. The vast majority of members work standard hours and need no `MemberCapacity` record at all; the org default handles them. Part-time arrangements are the primary use case for per-member records, and effective dates cleanly handle transitions ("Alice goes to 32h/week starting 2026-04-01").

Temporary reductions in availability (holiday, sick leave, conferences) are handled by `LeaveBlock`, not by changing capacity. This separation is important: capacity represents the member's contractual or standard availability, while leave represents temporary absences. Mixing them in one model would require constant capacity adjustments for every holiday.

## Consequences

- `MemberCapacity` entity with `weeklyHours` (BigDecimal), `effectiveFrom` (LocalDate, must be Monday), `effectiveTo` (LocalDate, nullable) — simple, auditable history
- `OrgSettings` gains a `defaultWeeklyCapacityHours` column (BigDecimal, default 40.0) — zero-config for standard firms
- Resolution chain: `MemberCapacity` record for the week -> `OrgSettings.defaultWeeklyCapacityHours` -> hard default 40.0
- `CapacityService.getMemberEffectiveCapacity()` further reduces capacity by leave days: `weeklyHours * (5 - leaveDaysInWeek) / 5`
- Members with standard hours require no configuration — the org default covers them
- Capacity changes are auditable via the `MemberCapacity` history (with `createdBy` for attribution)
- Cannot model sub-day availability patterns (e.g., "mornings only") — accepted trade-off given weekly allocation granularity
- Future enhancement: if shift patterns become necessary, `MemberCapacity` could gain a JSONB `dayPattern` column without breaking the existing weekly resolution
