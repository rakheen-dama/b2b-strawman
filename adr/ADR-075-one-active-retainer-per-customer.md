# ADR-075: One Active Retainer Per Customer

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

When designing retainer agreements, a fundamental question is whether a customer can have multiple concurrent active retainers or only one. This affects how time entries are allocated to retainers, how consumption is calculated, and how the UI presents retainer status.

The platform serves small-to-medium professional services firms (accounting firms, consultancies). In this market, a retainer typically represents the entire commercial relationship with a customer for a given period — "we provide X hours of service per month for $Y." The retainer is the billing wrapper around all work done for the customer.

Multiple concurrent retainers would require a mechanism to determine which retainer a time entry counts against. Options include: split by project, split by task type, priority-based overflow, or manual allocation. Each adds significant complexity to the consumption tracking, period close, and invoice generation flows.

**Options Considered**:

1. **One active retainer per customer (chosen)** — A customer can have at most one retainer with status ACTIVE or PAUSED at any time. Creating a new retainer requires terminating or waiting for the existing one to complete.
   - Pros: Unambiguous time entry allocation — all billable time for the customer counts toward the single retainer; simple consumption query (no allocation logic); clear UI — one retainer card, one progress bar, one "hours remaining" indicator; matches the common case for small-to-medium firms; no conflict resolution needed.
   - Cons: Cannot model "40 hours general + 10 hours emergency" as separate retainers; changing retainer terms requires updating the existing agreement or terminate-and-recreate; no granular tracking of different work streams under separate retainer terms.

2. **Multiple concurrent retainers with project-based allocation** — Each retainer is linked to specific projects. Time entries are allocated based on which project they belong to.
   - Pros: Granular tracking per work stream; different terms for different project types; natural fit for firms with distinct service lines.
   - Cons: Requires explicit project-to-retainer mapping; orphan projects (not linked to any retainer) need handling; adding a new project requires deciding which retainer it belongs to; complicates the consumption query (must filter by retainer's projects); period close generates multiple invoices or requires consolidation logic.

3. **Multiple concurrent retainers with priority-based overflow** — Retainers have a priority order. Time is allocated to the highest-priority retainer first, then overflows to the next.
   - Pros: Automatic allocation without manual mapping; supports "base + overflow" patterns.
   - Cons: Priority ordering is non-obvious — which retainer should fill first?; overflow calculation is complex (must track remaining capacity across retainers in order); editing a time entry can cascade across multiple retainers; period close must handle partial retainer consumption; UI complexity — showing consumption across multiple retainers with overflow arrows.

4. **Multiple concurrent retainers with manual allocation** — Members explicitly choose which retainer a time entry counts against when logging time.
   - Pros: Full control over allocation; supports any commercial arrangement.
   - Cons: Adds a required field to every time entry; members must understand retainer structure to log time correctly; mis-allocation is common and hard to detect; breaks the current simple time entry UX; requires "reallocate" functionality for corrections.

**Decision**: Option 1 — one active retainer per customer.

**Rationale**:

The target market (small-to-medium professional services firms) overwhelmingly uses a single retainer per customer. The retainer represents the commercial relationship, not individual work streams. When a firm says "we have a 40-hour monthly retainer with Acme," they mean all work for Acme is covered, regardless of which project or task type.

The "40 hours general + 10 hours emergency" pattern exists but is uncommon in this market segment. The practical workaround is a single 50-hour retainer. If more granular tracking is needed, the firm uses project-level time reports (already available in Phase 5) to break down where the hours went, while billing under a single retainer umbrella.

The simplicity benefit is substantial. With one retainer per customer: the consumption query has no allocation logic (sum all billable time for the customer), the UI shows a single clear progress indicator, the time entry form shows one "hours remaining" number, and period close generates one invoice. Every multi-retainer option introduces allocation complexity that permeates the entire system — from time entry creation through consumption tracking through invoicing.

If customer demand for multi-retainer support emerges, the single-retainer constraint is enforced at the service layer (not the database), making it straightforward to relax in a future phase by removing the validation check and adding allocation logic.

**Consequences**:

- Positive:
  - Unambiguous consumption tracking — all billable time counts toward the one retainer
  - Simple, clear UI — one progress bar, one "hours remaining" indicator
  - No allocation decisions during time entry creation
  - Period close generates exactly one invoice per retainer
  - Straightforward to implement, test, and explain

- Negative:
  - Cannot model multiple concurrent retainers with different terms for different work streams (workaround: single retainer with combined hours, use project reports for breakdown)
  - Changing retainer terms requires updating the existing agreement or terminate-then-create (mitigated by the update endpoint allowing term changes that take effect next period)
  - A customer transitioning between retainer types must terminate the old retainer and create a new one (no automatic migration)
