# ADR-326: Gated-Send Safety Model — No Send Path Outside Gate Execution, Batch Approval, Terminal Rejection

**Status**: Accepted

**Context**:

Phase 83's reminders are AI-drafted emails to the firm's *clients* — the most trust-sensitive output the platform produces. A wrongly-worded or wrongly-timed collections email (nagging a client who paid an hour ago, a final-demand tone to a 10-year relationship) damages the firm, not just the software. The founder decision is explicit: **gated always, policy later** — every reminder is human-approved before sending, no auto-send code path ships, and the design must not preclude a later org-policy auto-send toggle. The platform already has exactly one approval machine: `AiExecutionGate` (PENDING → APPROVED/REJECTED/EXPIRED, 72 h expiry, `AI_REVIEW` capability, `GateActionExecutor` performing the approved action) — ADR-322 established that there is one approval surface and no parallel safety machinery. Open questions: how sending is bound to approval, how a firm approves dozens of reminders without dying of clicks, what rejection means for future scans, and how internal escalation ("flag for partner call") relates to gating.

**Options Considered**:

### A. Binding "send" to "approved"

1. **The executor is the only sender (CHOSEN)** — a new sealed `GateAction` variant `SendCollectionReminderAction`; `GateActionExecutor` delegates to `CollectionReminderSendService`; no controller, service, or job exposes a direct send. The scan produces gates, never emails.
   - Pros: safety by construction, not by discipline — there is no code path where an unapproved reminder reaches SMTP; reuses gate lifecycle, expiry, audit (`ai.gate.*`), and the review UI verbatim; a future auto-send toggle has one obvious implementation point (policy-driven auto-approve of the gate, preserving the executor as sole sender).
   - Cons: even a hypothetically "safe" reminder needs a human click in v1 (that is the founder decision, not a defect).
2. **A collections-side send endpoint guarded by "must reference an approved gate"**.
   - Pros: keeps collections code self-contained.
   - Cons: the guard is a runtime check, not a structural guarantee — a refactor or a new caller can bypass it; duplicates what the executor already is; two senders to audit.
3. **Direct send with a review queue in front (no gates)** — a collections-owned "pending reminders" table with its own approve button.
   - Pros: no coupling to the AI-gate machinery.
   - Cons: rebuilds gates poorly — second approval surface, second expiry scheduler, second audit family; precisely what ADR-322 prohibits; the drafts *are* AI output, which is what gates exist to review.

### B. Approval ergonomics

1. **Batch-approve endpoint on the existing gate controller (CHOSEN)** — `POST /api/ai/gates/batch-approve` `{gateIds, notes}`; per-gate transactions; 200 with per-gate dispositions; UI multi-select with per-item preview on the collections page.
   - Pros: a month-end batch of 30 reminders is one review pass, not 30 round-trips; per-gate transactions mean one already-expired gate (paid invoice) doesn't block nine valid sends; generic — any future gate type benefits; capability check remains the single existing `AI_REVIEW`.
   - Cons: batch UX invites rubber-stamping (mitigated: per-item preview is one click away and the card shows subject + stage + amount; the founder accepted the trade explicitly).
2. **Loop the existing single-approve endpoint from the frontend.**
   - Pros: zero backend change.
   - Cons: N round-trips with partial-failure states smeared across the client; no server-side disposition record; retries double-approve races the client must handle.
3. **Auto-approve stage-1 nudges, gate only stages 2–3.**
   - Pros: less clicking for the gentlest tier.
   - Cons: violates the founder decision verbatim ("no auto-send code path ships in v1"); stage 1 still reaches the client, and stage-1 mistakes to good clients are exactly the trust-destroying case.

### C. Rejection semantics

1. **Rejection is terminal for that `(invoice, stage)` (CHOSEN)** — the ledger row goes `REJECTED`; the scan never re-proposes that stage; the invoice progresses to the next stage when its threshold passes.
   - Pros: respects the human's judgment ("don't send this") without nagging them with the same draft daily; trivially enforced by the existing unique row; the *next* stage still arrives, so rejection is "skip this letter", not "never chase this client" (that is what `collectionsExempt` is for).
   - Cons: "I rejected only the wording" has no re-draft button in v1 (workaround: reject, wait for the next stage, or edit nothing — accepted as a v2 refinement).
2. **Re-propose the same stage after a cool-off** (e.g. 7 days).
   - Pros: recovers from wording-only rejections automatically.
   - Cons: turns a human "no" into "ask again later" — the approver has no way to say a durable no short of exempting the whole customer; cool-off state adds a timer column and scan arithmetic for ambiguous benefit.
3. **Rejection cascades to all future stages for the invoice.**
   - Pros: strongest reading of "no".
   - Cons: conflates one letter's rejection with abandoning collection of the invoice entirely; firms that dislike a stage-1 draft usually still want the stage-3 letter.

### D. Escalation ("flag for partner call")

1. **Deterministic, ungated: ledger row `FLAGGED` + `COLLECTION_ESCALATED` notification to admins/owners (CHOSEN)**.
   - Pros: nothing client-facing happens — gates protect client-facing AI output, and this is neither AI nor client-facing; zero AI cost; cannot be blocked by an unconfigured provider.
   - Cons: no approval record for the flag itself (the audit event `collections.escalation.flagged` is the record).
2. **A gated task-creation proposal** (reuse the `CreateTaskFromCorrespondenceAction` pattern).
   - Pros: an approved, assignable follow-up task.
   - Cons: tasks are project-scoped in this codebase and invoices are customer-scoped — there is no natural `projectId` for the task; gating an internal nudge adds approval labour with no safety benefit; a notification achieves the "partner knows" outcome directly.
3. **Escalation drafts a phone-call script via AI, gated.**
   - Pros: maximally helpful.
   - Cons: scope creep with AI cost attached; the deterministic flag is the requirement, verbatim.

**Decision**: Executor-only sending via a new `SendCollectionReminderAction` (A1), a generic batch-approve endpoint with per-gate dispositions (B1), terminal per-stage rejection (C1), and deterministic ungated escalation (D1).

**Rationale**:

1. **Safety must be structural.** "No send without approval" enforced by code review is a policy; enforced by there being no other sender, it is a property. The executor already exists, already audits, already expires — reusing it means the collections domain cannot regress the guarantee.
2. **The gate machinery is the auto-send seam too.** When the founder later enables policy-driven auto-send for stage 1, the implementation is "auto-approve the gate under org policy" — the executor, audit trail, cancellation listener, and delivery log all continue to apply unchanged. Deferring auto-send costs nothing architecturally.
3. **Rejection semantics follow the ledger.** One row per `(invoice, stage)` makes "terminal per stage" the zero-mechanism reading: the row exists in `REJECTED`, so the scan's "un-actioned stage" predicate excludes it. The alternatives all require *more* state to express *less* respectful behaviour.
4. **Gates guard the client boundary, not the org chart.** Escalation is the firm talking to itself; wrapping it in approval machinery confuses "review AI output before it leaves the building" with "review everything AI-adjacent".

**Consequences**:

- Positive: an unapproved reminder cannot be sent, by construction; the payment-cancellation listener (ADR-325) closes the other half — an approved-but-obsolete reminder cannot be sent either, because the gate is EXPIRED before the executor sees it.
- Positive: batch approval makes gated-always operationally tolerable, removing the main pressure to build auto-send prematurely.
- Positive: `AI_REVIEW` remains the single approval capability; no new RBAC concept.
- Negative: approval latency is a real window — a client may pay after approval but before... no: send happens synchronously on approval; the residual window is *draft-to-approval*, during which payment expires the gate. The truly unavoidable case (payment seconds after send) is logged and bounded by human cadence.
- Negative: no re-draft-on-reject in v1; approvers who dislike wording must wait for the next stage or accept the draft.
- Negative: batch endpoint returns 200 with per-gate failures — clients must read dispositions, not status codes.
- Related: [ADR-322](ADR-322-tiered-write-safety-and-gate-over-mcp.md) (one approval surface, executor pattern), [ADR-325](ADR-325-collections-domain-dunning-engine.md) (ledger + cancellation), [ADR-327](ADR-327-ai-reminder-drafting-debtor-triage.md) (what the gate payload contains).
