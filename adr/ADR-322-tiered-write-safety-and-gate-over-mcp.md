# ADR-322: Tiered Write Safety & Gate-over-MCP — Tool-Identity Enforcement and the Synthetic AiExecution

**Status**: Accepted

**Context**:

Phase 81's MCP write tools do two materially different things. Filing an email as a **correspondence record** and its **attachments as documents** are *recording* actions — they capture what happened, are easily reversible/auditable, and carry low risk. Creating a matter **task/deadline** is an *acting* action — it puts a deliverable and a due date into the firm's workflow, and for legal work a wrongly-created deadline has real consequences. Kazi already has the write-safety machinery for "acting": `AiExecutionGate` (PENDING → an authorised member approves inside Kazi under `AI_REVIEW` → `GateActionExecutor` performs the action; 72h expiry). But **gate creation has only ever happened inside in-product AI skill execution**, and every gate hangs off an `AiExecution` via `execution_id NOT NULL`. `propose_task` over MCP has no such execution (extraction happened in the firm's Claude — BYOC, ADR-320). Two decisions: (1) how to split direct vs gated writes so a client cannot bypass the gate; (2) how to satisfy `execution_id NOT NULL` for an MCP-created gate. The mandate: reuse the gate verbatim (no second gate, no second approval UI), expose only gate *creation* over MCP, and ship exactly **one** Tier-2 proof tool.

**Options Considered** (the `execution_id NOT NULL` problem):

1. **Synthetic lightweight `AiExecution` per MCP proposal (CHOSEN)** — `propose_task` first records a minimal `AiExecution` (actor = the MCP member, provider = `MCP`, source = BYOC, model = none, tokens = 0, cost = 0), then creates the gate hanging off it normally.
   - Pros: preserves `execution_id NOT NULL` and the `gate.getExecution().getId()` contract verbatim (the executor and review UI need no special-casing); gives the cost-metering surface an honest zero-cost record of an AI-proposed action; "proposed via MCP by <member>" renders with no branching; token-cost = 0 is itself the BYOC cost-model signal.
   - Cons: a synthetic row that represents no Kazi-side model run (needs a clear "externally executed / BYOC" status so it isn't mistaken for a real execution); a tiny bit of write amplification (one extra insert per proposal).
2. **Make `execution_id` nullable** — relax the FK so gates can exist without an execution.
   - Pros: no synthetic row.
   - Cons: weakens an invariant relied on across the gate subsystem (`GateActionExecutor` calls `gate.getExecution().getId()`; the review UI assumes an execution); every consumer must now null-check; a schema change to `ai_execution_gates` plus defensive code everywhere — high blast radius for a one-tool feature; the requirements explicitly say "do NOT make execution_id nullable."
3. **A separate gate-origin table / second gate type for MCP** — model MCP-originated gates outside the `AiExecution` relationship.
   - Pros: no synthetic execution, no nullable FK.
   - Cons: a parallel gate-origin concept fragments the gate model, needs its own joins in the review UI and executor, and violates the "no second gate machinery" mandate; far more code than a synthetic row.

**Options Considered** (the direct-vs-gated split):

1. **Enforce by tool identity — there is no "direct task" tool (CHOSEN)** — `file_correspondence`/`attach_document` persist directly; `propose_task` *only* creates a gate. There is no MCP path that creates a `Task` directly.
   - Pros: a client cannot bypass the gate because no bypass path exists; the safety classification is server-side and structural, not a trusted flag in the request.
   - Cons: bulk/auto task creation is impossible by design in v1 (intended — that's v2 and still gated).
2. **One write tool with a `requiresApproval` flag** — a single tool that either persists or gates based on a parameter.
   - Pros: fewer tools.
   - Cons: the safety boundary becomes a client-supplied flag — a buggy or malicious client could request direct creation of an acting action; trust-by-flag is exactly what the mandate forbids.
3. **All writes gated** — even correspondence/attachments go through a gate.
   - Pros: uniform.
   - Cons: gating recording actions adds approval friction with no safety benefit; the founder explicitly classified recording as Tier-1 direct.

**Decision**: Enforce the direct-vs-gated split by **tool identity** — `file_correspondence` and `attach_document` are Tier-1 direct audited writes; `propose_task` is the single Tier-2 tool and creates only a PENDING `AiExecutionGate` (no `Task`). To create that gate over MCP, record a **synthetic lightweight `AiExecution`** (provider `MCP`, cost 0) so `execution_id NOT NULL` holds, then call a new public `AiExecutionGateService.createGate(...)`. Approval stays exclusively in-product (`AiExecutionGateController`, `AI_REVIEW`); on approval the existing `GateActionExecutor` runs a new `CreateTaskFromCorrespondenceAction` arm calling `TaskService.createTask`. Exactly one Tier-2 tool ships now.

**Rationale**:

1. **Structural beats trusted.** A safety boundary enforced by tool identity (no direct-task path exists) cannot be bypassed by a client; a `requiresApproval` flag could. For legal deadlines, the gate must be unbypassable.
2. **Synthetic execution preserves the invariant cheaply.** The `execution_id NOT NULL` contract is relied on across the gate subsystem; a synthetic `AiExecution` honours it verbatim with zero special-casing, versus nullable-FK's broad blast radius or a fragmenting gate-origin table. The zero token/cost is an honest BYOC record and doubles as the cost-model signal (the firm paid the tokens).
3. **Reuse the gate verbatim.** Only gate *creation* is new (a public `createGate` method that did not exist — gate creation was internal to skill execution); the lifecycle, expiry scheduler, approval UI, and `AI_REVIEW` gating are reused as-is. The only other new code is one `GateAction` record + parse/execute arm — and the sealed-interface `switch` is exhaustive, so the compiler *forces* the new arm (no silently-unhandled action).
4. **Recording stays direct.** Tier-1 actions are reversible/auditable and low-risk; gating them would add friction with no safety gain, matching the founder's classification.
5. **One proof now.** Wiring and proving the full Claude→propose→approve-in-Kazi→execute loop with exactly one tool de-risks the seam without committing to bulk extraction (v2). The seam, once proven, generalises to future Tier-2 actions, all gated.

**Consequences**:
- Positive: the gate is structurally unbypassable; the existing gate machinery and approval UI are reused untouched; the compiler enforces the new executor arm; cost metering honestly records a zero-cost BYOC proposal; the human-in-the-loop is preserved for acting actions.
- Positive: the approving member sees the originating correspondence on the gate (informed approval); the created task links back to the correspondence via a `correspondenceId` custom field; audit records "AI-proposed via MCP → member-approved → executed" with actors + timestamps.
- Negative: a synthetic `AiExecution` row per proposal (minor write amplification) that must carry a clear "externally executed / BYOC" status so it isn't read as a real Kazi run; cost/usage dashboards must treat `provider=MCP, cost=0` rows correctly (zero, not missing).
- Positive: `propose_task` ships a **mandatory v1 open-gate guard** — before creating a synthetic execution + gate it checks for an existing PENDING gate with the same `(correspondenceId, gate_type=CREATE_TASK_FROM_CORRESPONDENCE)` pair and, if one exists, returns that `gateId` with `duplicate: true` instead of stacking a second gate for a reviewer to triage. The *fuller* dedupe (idempotency keys over all action params, request-replay protection) stays v2. `Task` has no FK to correspondence, so the back-link is written via the `customFields` param (`Map.of("correspondenceId", a.correspondenceId().toString())`), not a hard FK.
- Related: [ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md) (the existing execution-gate pattern reused verbatim; only its creation path is extended), [ADR-319](ADR-319-inbound-correspondence-domain.md) (the correspondence the task is proposed from), [ADR-321](ADR-321-mcp-write-tool-category.md) (`MCP_WRITE` authorises proposing; `AI_REVIEW` authorises approving), [ADR-320](ADR-320-byoc-ingestion-boundary.md) (why the execution is synthetic/zero-cost — extraction was BYOC), [ADR-303](ADR-303-mcp-authentication.md) (the auth chain that binds the member behind the synthetic execution).
