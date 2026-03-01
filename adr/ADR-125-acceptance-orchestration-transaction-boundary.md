# ADR-125: Acceptance Orchestration Transaction Boundary

**Status**: Accepted

**Context**:

When a portal contact accepts a proposal, the system must execute a multi-step orchestration: update the proposal status, create a project (optionally from a template), assign team members to the project, set up billing (invoices for fixed-fee, retainer agreement for retainer), and transition the customer lifecycle. These steps are interdependent — a created project is meaningless without team assignment, and milestone invoices reference the created project. The question is whether all steps should execute within a single database transaction or use an event-driven saga pattern with compensating actions.

The system currently has two relevant patterns for multi-step operations: `ProjectTemplateService.instantiate()` creates a project with tasks and documents in a single transaction, and `AcceptanceRequest.markAccepted()` is a single entity update with post-commit event handlers for notifications. Neither involves cross-entity orchestration at the scale of proposal acceptance (which touches Proposal, Project, CustomerProject, ProjectMember, Invoice, InvoiceLine, RetainerAgreement, and potentially Customer entities).

All entities involved live in the same tenant schema — there are no cross-schema or cross-service boundaries. The orchestration does not call external APIs (no payment processing, no third-party integrations). The notification step (informing the creator and team members) is inherently asynchronous and should not block the acceptance response.

**Options Considered**:

1. **Single database transaction** -- All entity modifications (proposal update, project creation, team assignment, billing setup, customer lifecycle) execute within one `@Transactional` method. Notifications are published as Spring `ApplicationEvent`s with `@TransactionalEventListener(phase = AFTER_COMMIT)` and processed asynchronously after the transaction commits.
   - Pros:
     - Atomicity: either all steps succeed or none do — no partial state (e.g., project created but no invoices, or invoices created but proposal still shows SENT)
     - Simplicity: standard Spring `@Transactional` annotation, no saga infrastructure, no compensating actions to implement and test
     - Debuggability: one transaction ID in logs, one rollback to trace on failure
     - All entities are in the same schema — no distributed transaction coordinator needed
     - Consistent with existing patterns (`ProjectTemplateService.instantiate()` uses single transaction for multi-entity creation)
   - Cons:
     - Transaction duration: if project template instantiation is slow (many tasks, many documents), the transaction holds locks longer
     - Retry granularity: a failure in any step requires retrying the entire orchestration, not just the failed step
     - Notification delay: notifications are deferred until after commit — the portal contact sees the success response before the creator is notified (acceptable)

2. **Saga with compensating actions** -- Each step is a separate transaction. A saga coordinator tracks step completion. If step N fails, compensating actions for steps 1 through N-1 are executed (e.g., delete the created project, void the created invoices, remove team assignments).
   - Pros:
     - Fine-grained retry: can retry only the failed step without re-executing successful steps
     - Shorter individual transactions
     - Scales to cross-service orchestration if the system evolves to microservices
   - Cons:
     - Significant complexity: every step needs a compensating action, and every compensating action must be idempotent
     - Partial state is visible between steps — e.g., a project might exist without invoices while the saga is in progress
     - Compensating actions can themselves fail, requiring manual intervention
     - No saga infrastructure exists in the codebase — would need to be built or adopted
     - Over-engineering for a monolithic application where all entities are in the same schema and same JVM

3. **Hybrid: transaction for data + async for side effects** -- All entity modifications in a single transaction. Side effects (email, portal read-model sync, audit events) are processed asynchronously after commit. If side effects fail, they can be retried independently without affecting the core data.
   - Pros:
     - Same atomicity guarantees as Option 1 for the core data operations
     - Side effect failures (email send failure, portal sync failure) do not block acceptance or cause rollback
     - Clear separation between "must succeed atomically" (data) and "should eventually succeed" (notifications, sync)
   - Cons:
     - Side effects can lag behind the transaction (portal read-model may show SENT briefly after acceptance completes)
     - Retry infrastructure needed for failed side effects (Spring Retry or manual re-process)

**Decision**: Option 3 -- Hybrid (transaction for data + async for side effects). In practice, this is a refinement of Option 1 that explicitly classifies which operations are transactional and which are eventual.

**Rationale**:

The orchestration involves entities entirely within a single tenant schema and a single JVM process. There is no distributed systems problem to solve. A single transaction provides the strongest correctness guarantee (atomicity) with the least implementation and testing complexity. The saga pattern (Option 2) would require building compensating actions for every step — deleting projects, voiding invoices, removing team members, reverting customer lifecycle transitions — each of which introduces new failure modes. This is a textbook case of unnecessary complexity for a monolith.

The hybrid refinement over pure single-transaction (Option 1) is a practical recognition that some operations should not block the acceptance response or cause rollback if they fail:

- **Email notifications**: If the SMTP server is temporarily unavailable, the acceptance should still succeed. The creator can see the accepted proposal in the UI.
- **Portal read-model sync**: If the portal schema update fails, the portal will show stale data briefly. The status can be repaired by a reconciliation process or the next sync event.
- **Audit events**: Audit event rows are persisted within the transaction (for compliance integrity — the audit trail must reflect the exact state change atomically). Post-commit side effects triggered by those events (such as notification emails) are asynchronous.

The transactional boundary includes: proposal status update, project creation (including template instantiation), customer-project link, team member assignment, invoice creation (for FIXED fee), retainer agreement creation (for RETAINER fee), milestone invoice ID back-references, customer lifecycle transition, and audit event persistence. Everything outside that boundary (email delivery, portal schema sync, in-app notification creation) uses `@TransactionalEventListener(phase = AFTER_COMMIT)`.

If the orchestration becomes noticeably slow due to large project templates (dozens of tasks and documents), the solution is to optimize `ProjectTemplateService.instantiate()` — not to decompose the transaction. Template instantiation is already optimized for batch insert.

**Consequences**:

- `ProposalOrchestrationService.acceptProposal()` is annotated with `@Transactional`
- All entity modifications (proposal, project, invoices, retainer, team members, customer lifecycle, audit events) execute within the single transaction
- Spring `ApplicationEvent`s are published for post-commit processing: `ProposalAcceptedEvent`, which triggers email notifications, in-app notifications, and portal read-model sync
- If any step within the transaction fails, the entire orchestration rolls back; the proposal remains in SENT status
- The portal contact receives a synchronous success/failure response — no polling or callback needed
- If post-commit side effects fail, they are logged and can be retried via a reconciliation mechanism (out of scope for v1 — manual reprocess is acceptable)
- Future evolution: if the system moves to microservices with cross-service boundaries, this decision should be revisited in favor of a saga. For the current monolith, single-transaction is correct.
- Related: [ADR-124](ADR-124-proposal-storage-model.md)
