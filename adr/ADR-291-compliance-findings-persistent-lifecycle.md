# ADR-291: Compliance Findings as Persistent Entities with Lifecycle

**Status**: Accepted

**Context**:

The Phase 74 compliance audit skill produces severity-ranked findings -- FICA CDD gaps, POPIA registration shortfalls, approaching prescription deadlines, trust accounting irregularities. Each finding identifies a specific compliance gap, cites the regulatory basis, and recommends remediation. The question is how to persist findings so they can be tracked, resolved, and audited over time.

Findings have a natural lifecycle: they are discovered in an audit, acknowledged by a team member, worked on (remediation in progress), and eventually resolved or dismissed as false positives. This lifecycle exists independently of the audit report that discovered them -- a finding may take weeks to resolve, spanning multiple subsequent audits. The lifecycle also needs an audit trail: who acknowledged the finding, who resolved it, and what was done.

The existing entity inventory includes `Task` (Phase 4) and `ChecklistInstanceItem` (Phase 14), both of which track work items with status lifecycles. The question is whether compliance findings should reuse one of these entities or have their own.

**Options Considered**:

1. **Persistent `ComplianceAuditFinding` entity with dedicated status lifecycle (CHOSEN)** -- Create a new `ComplianceAuditFinding` entity linked to `ComplianceAuditReport`. Each finding has a status lifecycle: `OPEN` -> `ACKNOWLEDGED` -> `IN_PROGRESS` -> `RESOLVED` / `FALSE_POSITIVE`. Status transitions are audited. Findings reference the entity they relate to (customer, project) via soft FK for navigation.
   - Pros:
     - **Clean domain model.** Compliance findings are a distinct domain concept -- they have regulatory basis, severity, category, remediation recommendations, and entity references that don't map to task fields (assignee, due date, priority, project scope) or checklist fields (template linkage, completion percentage).
     - **Lifecycle tailored to compliance.** The OPEN -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED / FALSE_POSITIVE lifecycle reflects how compliance teams actually work: triage (acknowledge), remediate (in progress), close (resolved or false positive). Tasks have a different lifecycle (TODO -> IN_PROGRESS -> DONE) without the ACKNOWLEDGED or FALSE_POSITIVE states.
     - **Audit trail per finding.** Each status transition emits a `COMPLIANCE_FINDING_STATUS_CHANGED` audit event. This creates an unbroken chain: AI audit -> finding created -> team member acknowledged -> remediation completed -> finding resolved. For LSSA inspections, this audit trail demonstrates proactive compliance oversight.
     - **Report-scoped.** Findings are children of a report, not free-floating. Querying "all findings from the March 2026 audit" is a simple FK query. Deleting a report cascades to its findings (data cleanup).
     - **Cross-audit comparison.** When a subsequent audit identifies the same gap, it creates a new finding. The firm can compare findings across audits to see whether the compliance posture improved. Reusing the same finding across audits would lose this temporal dimension.
   - Cons:
     - **New entity and infrastructure.** A new table, JPA entity, repository, service, controller, and frontend components. More code to maintain than reusing an existing entity.
     - **Potential confusion with tasks.** Team members may see findings as "things to do" and expect them in the task list. They are not tasks -- they are compliance observations with remediation recommendations. The distinction requires UX clarity.
     - **No assignment.** Unlike tasks, findings are not assigned to a specific team member in v1. Remediation is coordinated outside the finding entity. If assignment is needed, it is a future enhancement.

2. **Ephemeral -- findings exist only in `AiExecution.output_content`** -- Don't persist findings as separate entities. The audit output JSON lives in the `AiExecution.output_content` column, and the frontend parses it directly. No finding lifecycle, no status tracking.
   - Pros:
     - **Zero new entities.** The audit output is already stored as TEXT in `AiExecution.output_content`. No additional persistence layer needed.
     - **Simple.** No lifecycle management, no status transitions, no audit events for findings. The audit is a snapshot -- read it, act on it, move on.
   - Cons:
     - **No lifecycle tracking.** The firm cannot track whether a finding was addressed. "Did we fix the 17 overdue CDD customers from the March audit?" requires re-running the audit and comparing results manually.
     - **No audit trail.** There is no record of who acknowledged a finding, when remediation started, or how it was resolved. For LSSA inspections, this gap is problematic -- the firm cannot demonstrate proactive compliance oversight.
     - **Unstructured querying.** Filtering findings by severity, category, or status requires JSON parsing at query time. "Show me all CRITICAL findings across all audits" is a JSON path query on `output_content`, which is neither indexed nor type-safe.
     - **No cross-audit comparison.** Without structured finding data, comparing compliance posture across audits requires frontend-side JSON diffing. This is fragile and does not scale.
     - **No notification integration.** Critical findings cannot trigger notifications because there is no entity to link the notification to. The notification says "you have critical findings" but cannot link to a specific finding.

3. **Findings as tasks -- create `Task` entities for each finding** -- For each finding in the audit output, create a `Task` entity linked to the relevant project or customer. The task's description contains the finding details. Standard task lifecycle (TODO -> IN_PROGRESS -> DONE) applies.
   - Pros:
     - **Reuses existing infrastructure.** `Task` entity, task list UI, task detail page, task notifications -- all exist from Phase 4. No new entities, no new frontend components for task display.
     - **Assignment and due dates.** Tasks can be assigned to specific team members with due dates, enabling structured remediation tracking.
     - **Visible in My Work.** Compliance remediation tasks appear in the assignee's My Work view alongside their other tasks.
   - Cons:
     - **Pollutes the task system.** A compliance audit for a firm with 200 clients could produce 20-50 findings. Creating 50 tasks per audit floods the task list with AI-generated items that are structurally different from human-created tasks. Tasks have assignees, due dates, priorities, checklists, time entries -- none of which apply to compliance findings.
     - **Wrong lifecycle.** Tasks are TODO -> IN_PROGRESS -> DONE. Compliance findings need ACKNOWLEDGED (triaged but not yet being worked) and FALSE_POSITIVE (dismissed as incorrect). Shoehorning these into task statuses ("DONE" for both resolved and false positive) loses the semantic distinction.
     - **No regulatory metadata.** Tasks have a description (free text) but no structured fields for severity, category, regulatory basis, or remediation recommendation. These would need to be encoded in the description text, losing queryability and structured rendering.
     - **Report-finding linkage is lost.** Tasks exist independently of the audit report. "Which tasks came from the March audit?" requires a naming convention or custom field hack. The clean parent-child relationship (report -> findings) is not representable in the task model.
     - **Cross-audit duplication.** If a finding recurs in a subsequent audit, the system would create a duplicate task. Deduplication ("is this finding already a task?") requires fuzzy matching on task descriptions -- unreliable.

**Decision**: Option 1 -- Persistent `ComplianceAuditFinding` entity with a dedicated OPEN -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED / FALSE_POSITIVE lifecycle. Findings are children of `ComplianceAuditReport` with cascading delete.

**Rationale**:

Compliance findings are a distinct domain concept that does not fit cleanly into either the task model or the ephemeral output model. They have regulatory metadata (severity, category, regulatory basis, remediation) that tasks lack. They have a lifecycle (ACKNOWLEDGED, FALSE_POSITIVE) that tasks don't support. They have a parent-child relationship with the audit report that tasks can't represent. And they need an audit trail for LSSA inspections that ephemeral output cannot provide.

The investment in a new entity is justified by the compliance use case: South African law firms face periodic LSSA inspections where they must demonstrate proactive compliance oversight. A finding lifecycle with audit events (who acknowledged, who resolved, when, how) is direct evidence of proactive management. Ephemeral output (Option 2) provides no such evidence. Tasks (Option 3) provide the wrong evidence -- a flood of auto-generated tasks with no regulatory structure.

The "no assignment" limitation in v1 is intentional. Compliance findings are observations, not work assignments. The managing partner reviews findings on the compliance dashboard and coordinates remediation through existing channels (verbal, email, task creation if needed). If finding assignment proves valuable, it is a single-field addition to the entity -- not an architectural decision.

**Consequences**:

- Positive:
  - Clean lifecycle: OPEN -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED / FALSE_POSITIVE. Each transition is audited with `COMPLIANCE_FINDING_STATUS_CHANGED` event.
  - Report-scoped: findings are children of a report, queryable by report, cascading on delete.
  - Structured metadata: severity, category, regulatory basis, remediation are typed fields, not free-text blobs. Filterable, sortable, aggregatable.
  - Cross-audit comparison: each audit creates new findings. Comparing findings across audits reveals compliance trends.

- Negative:
  - New entity and infrastructure. Adds one JPA entity, one repository, one service layer, one controller, and frontend components for finding list and detail. Estimated 2-3 days of implementation.
  - No assignment in v1. Findings cannot be directly assigned to team members. Remediation coordination happens outside the finding entity.
  - Potential confusion between findings and tasks. UX must clearly distinguish "compliance findings" from "tasks" -- different icons, different navigation, different terminology.

- Neutral:
  - `ComplianceAuditFinding` lives in the `compliance/` package alongside `ComplianceAuditReport`. Both entities share the same service (`ComplianceAuditReportService`) and controller.
  - Backward status transitions are not permitted. A resolved finding that recurs appears as a new finding in the next audit, preserving the audit trail. This avoids "resolved -> open -> resolved" cycles that complicate the timeline.
  - Terminal states (RESOLVED, FALSE_POSITIVE) set `resolved_by`, `resolved_at`, and require `resolution_notes`. This ensures every closure has attribution and justification.
  - The `entity_type` + `entity_id` soft reference on findings enables navigation links in the UI ("go to customer Mkhize Family Trust") without hard FK constraints across heterogeneous entity types.

- Related: [ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md) (execution gates -- audit publication creates findings), [ADR-290](ADR-290-on-demand-compliance-audit-over-scheduled.md) (on-demand audit -- findings are created when the attorney approves the gate), [ADR-292](ADR-292-ai-generated-document-provenance.md) (provenance -- findings trace back to the AiExecution via the report)
