# ADR-111: Project Completion Semantics

**Status**: Accepted

**Context**:

Projects currently have no status field -- they exist in a single implicit "active" state from creation until deletion. Phase 29 adds a `ProjectStatus` lifecycle to support engagement tracking for professional services firms. The key design question is how many states a project needs and what each state means. The answer determines which guardrails the system can enforce, what workflows users follow when wrapping up engagements, and how the UI organizes projects in list views.

Two concepts are often conflated: "the work is done" (a business milestone) and "I don't want to see this anymore" (an organizational action). Conflating them into a single non-active state forces users to choose between recording a milestone and decluttering their workspace, or it forces them to do both at the same time. Professional services firms track engagement completion separately from housekeeping -- an engagement may be finished but kept visible while invoices are still in transit.

**Options Considered**:

1. **Two-state model (ACTIVE -> ARCHIVED)** -- A single non-active state. Projects are either active or archived. Archiving means both "work is done" and "hidden from default views."
   - Pros:
     - Simplest possible state machine: one transition forward, one back (restore)
     - No ambiguity about which non-active state to use
     - Fewer UI states to design and test
   - Cons:
     - Conflates two distinct concepts: business completion (engagement finished, financial records closed) and organizational cleanup (hide from default list)
     - Cannot distinguish between "project finished successfully" and "project abandoned" -- both are just "archived"
     - No completion timestamp or completion guardrails: archiving has no preconditions, so there is no forcing function to close out open tasks or acknowledge unbilled time
     - Downstream actions (final invoice generation, client notification, profitability snapshot) have no clear trigger point

2. **Three-state model (ACTIVE -> COMPLETED -> ARCHIVED)** -- COMPLETED means work is done and finances are reconciled (or explicitly waived). ARCHIVED means hidden from default views and made read-only. Two distinct lifecycle milestones.
   - Pros:
     - Separates business completion from organizational cleanup: COMPLETED is a milestone with guardrails (all tasks done, unbilled time acknowledged), ARCHIVED is a housekeeping action
     - COMPLETED can trigger downstream actions: final invoice prompt, client portal notification, profitability report snapshot
     - COMPLETED projects remain visible in default lists (important during the invoicing tail period), while ARCHIVED projects are hidden
     - Matches accounting software patterns: "close period" vs. "archive period" are distinct actions
     - `completedAt`/`completedBy` timestamps have clear semantics separate from `archivedAt`
   - Cons:
     - Two non-active states require more UI design: status badges, filter options, contextual action buttons for each state
     - Users must learn the difference between COMPLETED and ARCHIVED -- potential for confusion
     - Slightly more complex state machine (5 transitions vs. 2)

3. **Four-state model (ACTIVE -> ON_HOLD -> COMPLETED -> ARCHIVED)** -- Adds ON_HOLD for paused projects that are not abandoned but have no active work.
   - Pros:
     - Distinguishes active work from paused engagements waiting for client input, regulatory approval, or seasonal timing
     - ON_HOLD could restrict new time entry creation (preventing accidental billing to paused projects)
     - More granular reporting: "how many projects are on hold" is a useful metric for resource planning
   - Cons:
     - ON_HOLD semantics overlap with simply having no active tasks on a project -- the same outcome without a formal state
     - Adds another status badge, filter option, and set of transitions to design and test
     - ON_HOLD is more useful for agencies managing dozens of concurrent client projects; for accounting/legal firms doing periodic engagements, projects naturally have idle periods without needing a formal pause state
     - Can be added later as a transition from ACTIVE without breaking the three-state machine

**Decision**: Option 2 -- Three-state model (ACTIVE -> COMPLETED -> ARCHIVED).

**Rationale**:

Professional services firms need a clear signal that an engagement is finished. "Finished" means all deliverables are done, all billable work is either invoiced or explicitly written off, and the engagement record is ready for archival. This is the COMPLETED state. It carries preconditions (all tasks DONE or CANCELLED, unbilled time acknowledged) that serve as a forcing function for clean engagement closure. Without these guardrails, projects accumulate orphaned open tasks and unbilled time indefinitely.

ARCHIVED is a separate concern: it means "I've processed this project and don't need it in my daily view anymore." An archived project is read-only -- no new tasks, time entries, or documents can be created. This matches the accounting concept of a "closed period" where historical data is preserved but locked against modification. The distinction is important because a project may be COMPLETED (work done, final invoice sent) but not yet ARCHIVED (the team wants to see it in their project list until the invoice is paid).

The two-state model (Option 1) loses this distinction. If the only non-active state is ARCHIVED, then archiving becomes the only way to "close" a project -- but archiving also hides it from view. This forces users to either keep finished projects visible (cluttering their list) or hide them before financial reconciliation is complete.

The four-state model (Option 3) adds ON_HOLD, which is a valid concept but not essential for Phase 29. ON_HOLD can be added as a future transition from ACTIVE without any breaking changes to the three-state machine. Building it now would be premature given the phase's focus on structural hardening rather than workflow optimization.

**Consequences**:

- `ProjectStatus` enum defines `ACTIVE`, `COMPLETED`, `ARCHIVED` with an `allowedTransitions()` method
- Completion (ACTIVE -> COMPLETED) enforces guardrails: all tasks must be DONE/CANCELLED, unbilled time requires explicit `acknowledgeUnbilledTime` flag
- Archival (COMPLETED -> ARCHIVED or ACTIVE -> ARCHIVED) makes the project read-only: no new tasks, time entries, documents, or invoices can be created
- Reopening is supported: COMPLETED -> ACTIVE (clears completedAt/completedBy) and ARCHIVED -> ACTIVE (clears archivedAt, restores full editability)
- Direct archive (ACTIVE -> ARCHIVED) is allowed for abandoned projects that were never formally completed -- no completion guardrails apply
- All existing projects default to ACTIVE via the migration's DEFAULT clause -- no data migration needed
- UI requires three states in filter chips (Active, Completed, Archived) plus contextual action buttons per state
- ON_HOLD is explicitly deferred: it can be introduced later as a new transition from ACTIVE without schema or state machine changes
- Related: [ADR-112](ADR-112-delete-vs-archive-philosophy.md) (archive as soft-delete for projects with operational data), [ADR-110](ADR-110-task-status-representation.md) (task terminal states that feed into project completion guardrails)
