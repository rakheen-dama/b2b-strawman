# ADR-153: Over-Allocation Policy

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 38 (Resource Planning & Capacity)

## Context

When a manager allocates a team member to projects such that the total allocated hours for a given week exceed the member's effective capacity, the system must decide how to handle this "over-allocation." For example, if Bob has 40 hours/week capacity and is allocated 25 hours to Project A and 20 hours to Project B (total 45 hours), is this an error, a warning, or silently allowed?

The policy directly affects the UX of the allocation grid — the signature UI of Phase 38. If over-allocation is blocked, the grid becomes a constraint-satisfaction puzzle where every cell change can fail. If silently allowed, managers lose visibility into capacity problems. The right policy must balance planning flexibility with operational awareness.

## Options Considered

1. **Hard block** — reject any allocation that would cause total allocated hours to exceed effective capacity for the week. The API returns an error and the allocation is not saved.
   - Pros: Guarantees no over-commitment; forces managers to make trade-offs explicitly (reduce Project A before adding Project B); capacity is always respected.
   - Cons: Hostile to real workflow — professional services firms routinely over-commit knowing some work will slip or be rescheduled; blocks the "plan optimistically, adjust later" pattern that managers use; makes the grid frustrating to use (constant error dialogs); forces artificial precision in early-stage planning when estimates are rough; managers would game the system by inflating capacity or deflating allocations.

2. **Warning with save** — save the allocation but return an over-allocation warning in the API response. The UI highlights over-allocated cells in red. A `MemberOverAllocatedEvent` domain event is published, enabling automation triggers (e.g., notify the resource manager).
   - Pros: No friction in planning — allocations always succeed; over-allocation is visible in the grid (red highlighting) and in reports; the automation event enables custom responses (notification, Slack message, etc.); matches how tools like Float, Productive.io, and Forecast handle over-allocation; preserves manager autonomy while surfacing risks.
   - Cons: Managers may ignore warnings habitually if over-allocation is common; requires UI investment in visual indicators.

3. **Silent allow** — no indication of over-allocation at save time. Over-allocation is only visible in utilization reports and the capacity grid's computed columns.
   - Pros: Simplest implementation; zero friction.
   - Cons: Managers may not notice capacity problems until they review reports; no opportunity for automation triggers; the grid shows raw hours but not the "problem" — users must mentally compare allocations to capacity.

4. **Configurable per org** — an org setting that lets firms choose between hard block, warning, or silent.
   - Pros: Maximum flexibility; each firm chooses the policy that matches their culture.
   - Cons: Triples the test surface for allocation CRUD; UI must handle three modes; the "hard block" mode still has all the problems described in option 1; most firms would choose "warning" anyway; adds a settings screen and migration for a decision that can be made once at the platform level.

## Decision

Option 2 — Warning with save. Allocations always succeed; over-allocation triggers a warning in the API response and publishes a domain event.

## Rationale

Professional services firms intentionally over-allocate. A partner scheduling work for next quarter knows that some projects will be delayed, some scopes will shrink, and some team members will become available as current work wraps up. Planning at 110% capacity for a future month is normal — it becomes a problem only when the week arrives and the over-commitment is still there. Blocking over-allocation at planning time would penalise this legitimate workflow.

The warning-with-save approach gives managers the best of both worlds: full flexibility in planning (no allocation is ever rejected) combined with clear visibility into capacity pressure. The grid UI uses colour coding — green (under 80%), amber (80-100%), red (over 100%) — so over-allocation is impossible to miss. The `MemberOverAllocatedEvent` domain event integrates with Phase 37's automation engine, allowing firms to define their own response: send a notification, post to Slack, or simply log for review.

The API response includes an `overAllocated` boolean and `overageHours` value on every allocation save, so the frontend can show contextual warnings (e.g., "Bob is now 5h over capacity for this week") without additional API calls. This design keeps the warning tightly coupled to the action that caused it, making it actionable rather than ambient.

## Consequences

- `ResourceAllocationService.createAllocation()` and `updateAllocation()` always persist the allocation, regardless of capacity
- API responses for allocation create/update include `overAllocated: boolean` and `overageHours: BigDecimal` fields
- The allocation grid UI colours cells by utilization band: green (< 80%), amber (80-100%), red (> 100%)
- `MemberOverAllocatedEvent` is published as a Spring application event when total allocation exceeds effective capacity
- When Phase 37 automation engine is deployed, the event is consumed by `AutomationEventListener` and mapped to the `MEMBER_OVER_ALLOCATED` trigger type — until then, the event is published but not consumed (no-op)
- Over-allocation warnings appear in the grid, in member detail views, and in utilization reports — multiple surfaces for visibility
- Firms that want hard blocking can approximate it with a Phase 37 automation rule that sends a high-priority notification on over-allocation, but the platform does not enforce it structurally
- No org-level configuration needed — the warning policy is universal, reducing settings complexity
