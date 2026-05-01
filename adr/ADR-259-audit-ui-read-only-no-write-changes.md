# ADR-259: Audit UI is a Read Layer over Existing Writes

**Status**: Accepted

**Context**:

Kazi has been writing to an append-only `audit_events` table since Phase 6 ([ADR-025](ADR-025-audit-storage-location.md), [ADR-029](ADR-029-audit-logging-abstraction.md)). Writers across the codebase emit events for projects, tasks, customers, invoices, proposals, documents, comments, time entries, rate cards, budgets, retainers, notifications, trust transactions, trust approvals, interest postings, reconciliations, matter-closure overrides (with the operator's justification verbatim), disbursements, engagement prerequisites, information requests, acceptances, automations, data-protection actions, member role changes, capability changes, and security events. The write side is mature and battle-tested across 60-plus phases.

What has never existed is a way for a firm administrator to *read* this data. There is no admin UI; the existing `AuditEventController` is gated by `TEAM_OVERSIGHT` but is consumed by no frontend code (a `grep frontend/**/audit*` returns nothing). A compliance officer asking "who approved the closure of Matter 0042 and why?" today must either ask an engineer to run `psql` against the tenant schema or wait for the answer indefinitely. Phase 69 builds the firm-side admin UI to close that gap.

A natural temptation, while building the read surface, is to "just fix" any write-side gaps discovered along the way — a flow that doesn't emit an event when it should, a `details` JSON shape that's inconsistent, a missing actor field. There are likely such gaps; the codebase has accreted over 60+ phases and write-side audit coverage was never centrally audited (the irony is acknowledged). The question is whether Phase 69 should fix them or report them.

**Options Considered**:

1. **Bundle write-side audit gaps into Phase 69.** Phase 69 ships both the read UI and any write-side fixes uncovered during build/QA — missing emit calls, inconsistent `details` shapes, normalised actor fields.
   - Pros:
     - Single coherent phase: "the audit log works end-to-end" is one deliverable rather than two.
     - Issues found during QA are fixed immediately rather than logged and deferred.
     - The 30-day capstone (§12.6 of the architecture doc) actually tests the system in its fixed state, not its current state.
   - Cons:
     - Scope explosion. The audit log is touched by every domain service in the codebase; "fix all the gaps" is potentially 30+ PRs and dozens of files. Phase 69 is sized as a 6-epic, ~10-slice phase; absorbing write-side fixes turns it into a phase of unknown size.
     - Each write-side fix is its own change with its own test surface, its own regression risk, and its own correctness review. Bundling these together means a single phase carries the regression risk of every domain service it touches.
     - A "we fixed it as we went" phase is hostile to /breakdown and /epic_v2 — those tools assume a discrete deliverable list. A phase that grows scope mid-flight breaks autonomous execution.
     - The read UI is independently valuable today. Shipping it conditional on a complete write-side audit means the read UI is delayed indefinitely.

2. **Read-only with gap report (CHOSEN).** Phase 69 ships only the read layer. Any write-side gap discovered during build or QA is logged in a structured gap report (`tasks/phase69-gap-report.md`) and proposed as Phase 70+ scope.
   - Pros:
     - Bounded scope. The phase ships a discrete set of slices (six epics, ~10 slices) with knowable test surface.
     - Read-side work is independently valuable. A compliance officer can answer "who closed Matter 0042?" tomorrow even if some adjacent flow doesn't emit an event correctly.
     - The gap report becomes a structured, prioritised input for the next phase rather than diffuse "we should fix these someday" sentiments. Each gap can be triaged on its own merits.
     - Aligns with the overall Kazi cadence: phases are sized around a coherent feature surface, not around exhaustive coverage of an existing system.
   - Cons:
     - The 30-day capstone may surface gaps that make the demo look incomplete ("this trust deposit doesn't show in the audit log because the write isn't there yet"). Mitigation: the capstone seed scenarios are chosen from flows that *are* known to emit; gaps in scenarios that aren't seeded are out-of-band findings.
     - There is a coordination cost — Phase 70+ has to actually pick up the gap report, rather than the gap report sitting orphaned. This is a workflow risk, not a scope risk.

3. **Freeze Phase 69 until a write-side audit gap-audit completes.** Run a separate scope-finding phase that catalogs every domain flow, asserts whether it emits the expected event, and produces a gap list. Phase 69 starts only after that catalog is complete and clean.
   - Pros:
     - Maximum confidence that the read UI shows everything an auditor would expect.
     - Decouples "what is missing" from "what does the read UI need" cleanly.
   - Cons:
     - Months of additional delay before any read-side value ships.
     - The gap audit itself is a phase-sized effort with no user-facing value — there is nothing to demo.
     - Many gaps are discoverable cheaply *during* read-side work (because the read UI surfaces them) — running a separate pre-audit duplicates effort.

**Decision**: Option 2 — read-only with a gap report.

**Rationale**:

The audit log infrastructure is mature; the gap is read, not write. Building the read UI first is the right ordering on the merits — it lets a compliance officer answer real questions tomorrow with the events that *are* being emitted, and it surfaces any write-side gaps as side-effects of doing useful work, rather than as the primary work of an auditing phase that has no user-facing deliverable.

Bundling write-side fixes (Option 1) is the kind of scope creep that turns a tractable phase into an open-ended one. Kazi's autonomous workflow tools (/breakdown, /epic_v2, /phase_v2) need discrete slice lists with knowable test surfaces. A phase that says "and also fix any write gaps discovered" cannot be /breakdown'd cleanly because the slice list isn't known until QA runs. Phase 69 is already a six-epic phase; absorbing the write-side gap-fix work risks making it untrackable.

Freezing the read UI behind a pre-audit (Option 3) is the wrong trade — it indefinitely delays user-facing value to chase exhaustive coverage. The read UI itself is the cheapest discovery tool: every "this flow doesn't show what I expect" finding becomes a gap-report row.

The discipline encoded by this ADR is: **find gaps, log them, do not fix them in this phase.** The capstone gap report (`tasks/phase69-gap-report.md`) is a Phase 70+ deliverable input.

**Consequences**:

- Positive:
  - Phase 69 ships a discrete, testable, /breakdown-able slice list.
  - The read UI delivers value on the events being emitted today, without waiting for anything else.
  - The gap report is a structured input for the next phase — each gap can be triaged on its own merits (priority, effort, risk).
  - The 30-day capstone (§6) explicitly produces the gap report as a deliverable, so the discovery process is built into the phase itself.

- Negative:
  - The audit log will visibly have gaps after Phase 69 ships. A demo to a compliance officer may surface "why doesn't this trust event appear?" — the answer is "Phase 70+ scope" rather than "fixed". Mitigation: the gap report is publicly tracked.
  - There is a coordination cost between Phase 69 and the next phase that picks up the gap report. A gap report that sits unactioned is wasted. Mitigation: the gap report is reviewed at phase-close as a /next-phase input.

- Neutral:
  - The capability gate (`TEAM_OVERSIGHT`) and the Phase 50 DSAR pack integration are unaffected — both work against the events being emitted today.
  - The gap report itself is markdown in `tasks/` — no infrastructure required.

- Related: [ADR-025](ADR-025-audit-storage-location.md) (per-tenant audit table), [ADR-026](ADR-026-audit-event-granularity.md) (event granularity), [ADR-029](ADR-029-audit-logging-abstraction.md) (`AuditService` abstraction), [ADR-260](ADR-260-audit-generic-diff-over-event-templates-v1.md) (generic diff viewer trade-off — same v1-scope-discipline), [ADR-264](ADR-264-audit-export-is-auditable.md) (audit export is itself audited — a write change introduced in this phase, the one allowed exception).
