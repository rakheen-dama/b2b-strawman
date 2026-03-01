# ADR-133: Auto-Transition Behavior When Fields Incomplete — Block and Notify

**Status**: Accepted

**Context**:

DocTeams supports automatic lifecycle transitions for customers: when all items in a customer's onboarding checklist are completed, the system auto-transitions the customer from ONBOARDING to ACTIVE. This auto-transition is triggered by `ChecklistService` when the last checklist item is marked complete and is designed to reduce manual oversight — the bookkeeper doesn't need to remember to click "Activate" after verifying every checklist item.

Phase 33 introduces prerequisite enforcement for the ONBOARDING → ACTIVE transition: all custom fields marked as `requiredForContexts: ["LIFECYCLE_ACTIVATION"]` must be filled before the transition can occur. For manual transitions (user clicks "Activate Customer"), the soft-blocking `PrerequisiteModal` ([ADR-130](ADR-130-prerequisite-enforcement-strategy.md)) handles the flow — the user sees missing fields, fills them inline, and the transition proceeds. But auto-transitions have no human in the loop at the moment of execution. The checklist completion event fires in a backend service method; there is no browser session to display a modal.

The question is: when an auto-transition's prerequisite check fails, what should happen? The system must decide between allowing the transition with degraded guarantees, blocking it entirely, or implementing a retry mechanism.

**Options Considered**:

1. **Block auto-transition, send notification** -- When checklist completion triggers an auto-transition and the prerequisite check fails, the transition does not occur. The customer remains in ONBOARDING. A notification is sent to the customer's assigned team members explaining that checklist items are complete but required fields are missing.
   - Pros:
     - ACTIVE status is a reliable indicator that the customer's data is complete for lifecycle purposes
     - Notification ensures the gap is surfaced to the right people
     - Simple implementation — the transition code path already returns a `PrerequisiteCheck`; blocking is a conditional return
     - Consistent with the principle that ACTIVE means "ready for full platform engagement"
   - Cons:
     - Customer remains in ONBOARDING even though checklist is done — could confuse users who don't read the notification
     - If the notification is missed, the customer may be stuck in ONBOARDING indefinitely
     - Breaks the "checklist completion = activation" mental model that existing users may have

2. **Transition with warning notification** -- The auto-transition proceeds regardless of field completeness. A warning notification is sent indicating that the customer was activated with incomplete fields. The `SetupProgressCard` on the detail page highlights the remaining gaps.
   - Pros:
     - Preserves the existing auto-transition behavior — no workflow disruption
     - Customer becomes ACTIVE immediately, enabling full platform features
     - Missing fields can be filled retroactively without lifecycle friction
   - Cons:
     - ACTIVE status no longer guarantees data completeness — undermines the purpose of Phase 33
     - Warning notifications are easily ignored under workload pressure
     - Downstream actions (invoice generation, proposals) will hit their own prerequisite checks, creating a confusing sequence: customer is ACTIVE but can't generate invoices
     - External integrations (accounting sync) may receive incomplete customer records for ACTIVE customers

3. **Queue for automatic retry** -- The auto-transition is deferred. The system creates a pending transition record and periodically re-checks prerequisites (e.g., every hour or when custom fields are updated). When all fields are filled, the transition executes automatically.
   - Pros:
     - Eventually consistent — the transition happens automatically once data is complete
     - No user intervention needed to retry the transition
     - Combines the safety of blocking with the convenience of auto-transition
   - Cons:
     - Requires new infrastructure: a pending transition table, a scheduled job or event-driven re-check
     - Adds complexity to the lifecycle transition model
     - Unclear behavior for users: the customer is in ONBOARDING with a completed checklist but no clear indication of when activation will happen
     - Custom field updates must trigger re-evaluation — event wiring between field updates and lifecycle transitions
     - Over-engineered for the frequency of this scenario (most customers will have fields filled before checklist completion)

**Decision**: Option 1 -- Block auto-transition, send notification.

**Rationale**:

The primary goal of Phase 33 is to make ACTIVE status a meaningful indicator of data completeness. Allowing auto-transitions to bypass prerequisite checks (Option 2) would create a two-tier ACTIVE status: some customers are genuinely complete, others were auto-activated with gaps. This undermines trust in the lifecycle model and pushes the problem downstream to action-point checks, where the user experience is worse (getting blocked at invoice generation is more frustrating than getting blocked at activation).

Blocking the auto-transition is the correct behavior because it maintains the invariant: ONBOARDING → ACTIVE requires `LIFECYCLE_ACTIVATION` prerequisites, regardless of how the transition is triggered. The notification ensures the team is aware and can act. The notification uses the existing notification infrastructure (Phase 6.5) and links directly to the customer detail page where the missing fields can be filled.

The concern about users missing the notification is mitigated by Phase 33's completeness visibility enhancements: the customer list shows completeness indicators, the dashboard widget surfaces incomplete profiles, and the `SetupProgressCard` on the detail page explicitly shows what's blocking activation. These multiple visibility channels make it unlikely that a stuck customer goes unnoticed.

The queue-and-retry approach (Option 3) was rejected as over-engineered. The scenario where checklist completion and field completion are out of sync is a transitional state — it occurs when the firm's process has a bookkeeper completing checklists while a different team member handles data capture. The notification bridges this gap without introducing background jobs and pending transition state machines. If the pattern becomes common in practice, the retry mechanism can be revisited in a future phase.

**Consequences**:

- `CustomerLifecycleService` (or equivalent auto-transition handler) calls `PrerequisiteService.checkForContext(LIFECYCLE_ACTIVATION, CUSTOMER, customerId)` before executing the auto-transition
- If the check fails, the transition is not executed; the customer remains in ONBOARDING
- A notification is created via the existing notification pipeline: type `PREREQUISITE_BLOCKED_ACTIVATION`, recipients are the customer's assigned team members, message includes customer name and count of missing fields
- The notification links to the customer detail page (deep link to the `SetupProgressCard` section)
- The `SetupProgressCard` must clearly distinguish between "checklist complete, fields missing" and "checklist incomplete" states to avoid confusion
- When the user later fills the missing fields and manually triggers "Activate Customer," the standard soft-blocking flow applies (modal if still incomplete, direct transition if all fields are now filled)
- Existing tests for auto-transition must be updated: test customers need required `LIFECYCLE_ACTIVATION` fields pre-filled via `TestCustomerFactory.withRequiredFields()`
- Related: [ADR-130](ADR-130-prerequisite-enforcement-strategy.md) (soft-blocking is the manual transition UX; auto-transitions cannot show modals), [ADR-131](ADR-131-prerequisite-context-granularity.md) (`LIFECYCLE_ACTIVATION` is the context checked for this transition)
