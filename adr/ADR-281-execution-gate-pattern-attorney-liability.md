# ADR-281: Execution Gate Pattern for Attorney Liability

**Status**: Accepted

**Context**:

Phase 72 AI skills produce recommendations that, if acted upon, modify a client's legal status: completing FICA checklist items (marking a client's KYC as verified), selecting a matter template (pre-populating a new engagement), clearing a conflict check. Under the Attorneys Act (South Africa), the attorney of record is personally liable for all work product — there is no "the AI did it" defence. A compliance failure traced to unchecked AI output would expose the firm to Law Society disciplinary action.

Phase 70 already established the `AiSpecialistInvocation` approval queue for specialist tool proposals (ADR-267: human approval default, direct mode exception for Inbox comment-posting). Phase 72 needs a similar approval mechanism for skill-level actions, but the shape is different: specialists produce a single JSONB `OutputPayload` per invocation (1:1), while skills produce multiple independent proposed actions per invocation (1:N). The question is how to enforce attorney review and whether to allow any auto-execution.

**Options Considered**:

1. **Auto-execute with audit trail** — AI actions take effect immediately. Every action is logged in `AuditEvent` with `source = AI_ASSISTED`. The attorney can review after the fact and reverse if needed.
   - Pros:
     - Fastest workflow for the attorney. No approval step, no delay.
     - Simpler implementation — no gate entity, no approval UI, no expiry logic.
     - The audit trail provides accountability and reversibility.
   - Cons:
     - **Violates the Attorneys Act liability model.** The attorney has not reviewed the AI's work before it takes legal effect. A FICA checklist item marked complete by AI without attorney review is a compliance failure, regardless of audit trail.
     - Reversibility is not equivalent to prevention. Marking a checklist item complete triggers downstream workflows (customer lifecycle transition, engagement letter generation). Reversing the completion after the fact may not reverse the downstream effects.
     - Firms would not trust AI that auto-modifies client records. The product would be perceived as risky rather than helpful.
     - No distinction between AI-confident and AI-uncertain recommendations — everything executes.

2. **Mandatory execution gates for all AI-proposed actions (CHOSEN)** — Every skill action that would modify an entity creates an `AiExecutionGate` in PENDING status. The action only executes after explicit attorney approval. Gates expire after 72 hours.
   - Pros:
     - **Full compliance with the Attorneys Act.** The attorney reviews and approves every AI-suggested action before it takes legal effect. The gate approval is itself an audited event, creating an unbroken chain of accountability.
     - **Builds trust incrementally.** Attorneys see every AI recommendation alongside its reasoning before it takes effect. Over time, they develop confidence in the AI's accuracy and speed up their review process. This is the trust-building pattern from Claude for Legal's "execution gates."
     - **Clean 1:N model.** One skill execution produces N gates — each independently approvable. The attorney can approve some and reject others from the same invocation.
     - **72-hour expiry prevents stale actions.** If an attorney doesn't review a gate within 72 hours, it expires safely. No action is taken on stale recommendations.
     - **Informational actions don't create gates.** Fee estimates, document requirement lists, and risk flags are displayed but don't create gates — the attorney uses them as advisory information. Only actions that modify entities (complete checklist items, select template, clear conflict) require gates.
   - Cons:
     - **Adds friction to the workflow.** The attorney must review and approve every AI recommendation. For a firm processing 50 clients/year, this is 50+ gate reviews for FICA alone.
     - **72-hour window may be too short.** Busy attorneys may not review gates within 72 hours, requiring re-invocation of the skill. Mitigated: the default is configurable in future (firm profile extension), and the skill can be re-invoked trivially.
     - **No batch approval.** In v1, each gate must be approved individually. A "approve all from this execution" batch action is deferred to Phase 73+.

3. **Risk-calibrated gates** — Low-risk actions (e.g., selecting a matter template, which is a UI pre-fill) auto-execute. High-risk actions (e.g., marking FICA items complete) require gates. Risk classification is per gate type.
   - Pros:
     - Reduces friction for low-risk actions. Template selection and document requirement lists could take effect immediately.
     - Matches the Phase 70 carve-out pattern (ADR-267): Inbox comment-posting auto-executes because it is low-risk and reversible.
   - Cons:
     - **Risk classification is a product decision, not an agent decision.** Which actions are "low risk" is subjective and firm-dependent. A firm that has been disciplined for FICA failures would consider template selection high-risk if it influences downstream compliance steps. Hard-coding risk levels removes firm control.
     - **Complicates the mental model.** The attorney must understand which actions auto-execute and which require review. If the classification is wrong (an action they expected to review was auto-executed), trust erodes rather than builds.
     - **Scope creep risk.** Each new skill would need a risk classification decision, which is an architectural decision, not an implementation decision. Mandatory gates avoid this entire class of decisions.
     - **The Attorneys Act doesn't distinguish risk levels for liability.** The attorney is liable for all work product, regardless of the action's perceived risk. A "low risk" auto-execution that turns out to be wrong carries the same liability as a "high risk" one.

**Decision**: Option 2 — Mandatory execution gates for all AI-proposed actions that modify entities. Informational outputs (fee estimates, document lists, risk flags) are displayed without gates.

**Rationale**:

The Attorneys Act liability framework makes mandatory gates the only defensible choice for a legal practice management tool. The attorney's approval of an AI recommendation is not just a UX preference — it is a legal requirement that the attorney has reviewed and endorsed the work product. Auto-execution (Option 1) violates this requirement. Risk-calibrated gates (Option 3) introduce classification complexity that is better deferred until firms have used the system long enough to express preferences.

The friction of mandatory gates is intentional and valuable. It forces the attorney to read the AI's reasoning, which serves three purposes: it catches errors, it builds familiarity with the AI's capabilities, and it creates an auditable record of attorney oversight. As firms develop trust, future phases can introduce batch approval and risk-calibrated options as opt-in features — but the default must be the safest option.

The 1:N model (one execution, multiple gates) is essential because skills produce compound recommendations. A FICA verification might recommend completing three checklist items and flagging one for additional documentation. The attorney should be able to approve the three completions and reject the flag, or vice versa. The Phase 70 `AiSpecialistInvocation` pattern (1:1 invocation-to-output) cannot express this.

**Consequences**:

- Positive:
  - Every AI-suggested entity modification is attorney-reviewed. Full compliance with the Attorneys Act.
  - Clean audit trail: `AI_SKILL_INVOKED` -> `AI_GATE_APPROVED` / `AI_GATE_REJECTED` -> entity modification with `source = AI_ASSISTED`.
  - 72-hour expiry prevents unbounded pending gates. The system self-cleans.
  - Informational outputs (fee estimates, risk flags) are zero-friction — displayed immediately, no gate required.

- Negative:
  - Mandatory approval adds 1-2 clicks per AI recommendation. For high-volume firms, this friction may discourage AI adoption. Mitigated by: the recommendations panel displays results immediately (the attorney sees the value before the gate exists), and batch approval is planned for Phase 73.
  - Gate expiry means unreviewed recommendations are lost. The attorney must re-invoke the skill if they miss the window.

- Neutral:
  - `AiExecutionGate` is a new entity separate from `AiSpecialistInvocation`. The two approval patterns coexist: specialist invocations use the Phase 70 queue, skill executions use the Phase 72 gate pattern. Both follow the "AI proposes, human disposes" principle with different structural shapes.
  - The expiry worker runs hourly via `@Scheduled` and iterates through all tenants using `TenantScopedRunner` (ADR-T008).

- Related: [ADR-267](ADR-267-human-approval-default-direct-mode-exception.md) (Phase 70 approval default — same principle, different entity shape), [ADR-270](ADR-270-ai-specialist-invocation-jsonb-output.md) (specialist invocation JSONB output — the 1:1 pattern that Phase 72 extends to 1:N), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant — gates are tenant-scoped), [ADR-T008](ADR-T008-tenant-scoped-runner.md) (tenant-scoped runner — expiry job)
