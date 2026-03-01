# ADR-135: Reminder Strategy — Interval-Based Reminders

**Status**: Accepted

**Context**:

Phase 34 introduces automated reminders for outstanding information requests. When a firm sends a request to a client and items remain incomplete, the system should proactively remind the client without manual intervention from the firm. The key design decision is how to determine when reminders should be sent.

Professional services firms have varying workflows. Some operate with strict deadlines (tax returns due by a specific date), while others operate with rolling engagements where there is no hard due date — they simply need the client to respond "soon." The reminder strategy must accommodate both common patterns while keeping the initial implementation simple. The existing `TimeReminderScheduler` provides a proven pattern for per-tenant scheduled processing.

**Options Considered**:

1. **Fixed interval (every N days)** -- Send a reminder every N days while items are outstanding. N is configurable per org (default) and overridable per request. No due date required.
   - Pros:
     - Universally applicable — works whether or not a deadline exists
     - Simple to implement and reason about: `daysSince(lastReminder) >= interval`
     - No "overdue" concept to manage (no cliff edge where reminders change behavior)
     - Per-request override gives flexibility without schema complexity
     - Familiar pattern: "nudge every 5 days" is intuitive for firm admins
   - Cons:
     - No urgency escalation as a deadline approaches
     - Cannot express "remind more frequently in the last week before due date"
     - Firms with strict deadlines may want countdown-style reminders

2. **Deadline-based with countdown reminders** -- Each request has a due date. Reminders sent at configurable intervals before the deadline (e.g., 14 days, 7 days, 3 days, 1 day before).
   - Pros:
     - Natural for deadline-driven workflows (tax returns, regulatory filings)
     - Escalating urgency is built in
     - Clear "overdue" status when deadline passes
   - Cons:
     - Requires a due date on every request — many requests don't have natural deadlines
     - More complex configuration (milestone-based reminder schedule)
     - "What happens after the deadline?" question — do reminders stop? Continue at a different interval?
     - Not all professional services work has hard deadlines

3. **Hybrid (interval + optional deadline)** -- Default to interval-based, but optionally attach a due date with deadline-specific reminder behavior.
   - Pros:
     - Maximum flexibility — covers both use cases
     - Firms can use whichever model fits
   - Cons:
     - Two code paths for reminder calculation
     - More complex scheduler logic (interval vs. countdown vs. post-deadline)
     - More UI surface (interval config + deadline config + deadline reminder config)
     - Over-engineers the v1 — can always add deadline support later
     - Testing matrix grows significantly

**Decision**: Option 1 -- Fixed interval.

**Rationale**:

The interval-based approach is the right starting point because it requires no assumptions about the nature of the work. Not all information requests have deadlines — a firm collecting company registration documents for a new client has no specific due date. The interval model works universally: "remind the client every 5 days until they respond."

The implementation is straightforward and mirrors the existing `TimeReminderScheduler` pattern: iterate tenants, check interval against last reminder timestamp, send if due. The per-request override (`reminderIntervalDays`) gives firms control without adding schema complexity. Setting the interval to 0 disables reminders for a specific request.

Deadline support can be added in a future phase as an additive change — add a `dueDate` field to `InformationRequest`, extend the scheduler to check both interval and deadline proximity, and add "overdue" status derivation. This layering is clean because interval-based reminders remain valid even when deadlines exist (they handle the "after deadline" case naturally). Starting with the hybrid approach would front-load complexity for a feature that many firms won't need in v1.

Related: [ADR-117](ADR-117-time-reminder-scheduling.md) (time reminder scheduling pattern).

**Consequences**:

- `InformationRequest` has `reminderIntervalDays` (nullable Integer) — null means "use org default"
- `OrgSettings` gains `defaultRequestReminderDays` (Integer, default 5)
- `RequestReminderScheduler` runs every 6 hours, processes all tenants
- Setting `reminderIntervalDays = 0` on a request disables reminders for that request
- No "overdue" concept in v1 — the dashboard derives "overdue" heuristically (no client activity in > 2x interval)
- Future enhancement: add `dueDate` field and deadline-aware reminder logic as an additive change
