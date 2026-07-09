# ADR-327: AI Reminder Drafting & Debtor Triage — Frame-Owns-Facts Drafting; Deterministic Triage, AI Narrates

**Status**: Accepted

**Context**:

Phase 83 introduces two AI touchpoints on the money layer: drafting graduated reminder emails (stage-toned, house-styled, context-aware) and making sense of the debtor book (who is drifting, who always pays late, who needs a call). The AI foundation is mature — `AiSkill` beans auto-registered into `AiSkillExecutionService`, prompts on the classpath, `AiFirmProfile` for house style, `LlmJsonParser` schema validation, `AiExecution` cost metering, `StubAiProvider` for tests. The design questions: how much of the email the AI writes, how drafting context is assembled, whether triage is its own skill, and how job-context (system) invocation and cost are handled.

**Options Considered**:

### A. How much of the email the AI writes

1. **Frame-owns-facts (CHOSEN)** — the Thymeleaf `collection-reminder` template owns branding, the invoice facts table (number, amount, due date), the payment CTA (`paymentUrl`/`portalUrl`, mirroring `invoice-delivery.html`), and footer; the AI writes only `subject` + letter body paragraphs.
   - Pros: a hallucinated amount or broken payment link is *impossible*, not just unlikely — the mechanical parts never pass through the model; the approver reviews exactly the human-language surface; the template is the same shape as invoice delivery, so rendering, rate limiting, and delivery logging are verbatim reuse.
   - Cons: the AI cannot restructure the whole email (e.g. lead with the amount) — bounded creativity is the point.
2. **AI writes the full email HTML.**
   - Pros: maximal stylistic freedom.
   - Cons: every fact in the email becomes a hallucination surface; approvers must proofread numbers, not just tone; template/branding drift per email; payment-link correctness depends on the model echoing a URL.
3. **No AI — static per-stage templates with variable substitution.**
   - Pros: zero AI cost or risk.
   - Cons: discards the phase's differentiator (context-aware tone: a 62-day reminder to a 5-year client should not read like one to a new client); the founder chose the AI bundle explicitly; static dunning is what every competitor ships.

### B. Drafting context assembly

1. **Deterministic context block from platform data (CHOSEN)** — invoice facts, relationship metrics (customer since, lifetime billed, median days-to-pay), chase history from the `CollectionActivity` ledger, stage tone, advisor annotations (ADR-329) — assembled in Java, never fetched by the model.
   - Pros: prompts are reproducible and testable; POPIA surface is knowable (exactly these fields egress to the provider); the stub can assert on inputs.
   - Cons: context breadth is fixed per release (fine — additions are code changes with review).
2. **Tool-use / retrieval during drafting** — let the model query for more context.
   - Pros: richer context on demand.
   - Cons: massively larger egress and audit surface for a one-paragraph letter; non-reproducible drafts; the skill framework is deliberately assemble-then-call.
3. **Minimal context (invoice facts only).**
   - Pros: smallest egress.
   - Cons: produces the generic letter option A3 would have — relationship-blind tone is the failure mode this skill exists to avoid.

### C. Triage: own skill or digest section

1. **Deterministic signals + narration inside the digest skill (CHOSEN)** — `CollectionsTriageService` computes `DRIFTING` / `SERIAL_LATE` / `GONE_QUIET` / `ESCALATED` / `TRUST_FUNDS_AVAILABLE` from queries; the debtors page shows the signals directly; the `cash-digest` skill receives them as context and ranks/narrates the top risks.
   - Pros: strictly smaller (requirements: "prefer whichever is smaller") — no second metered skill, schema, or prompt asset; the frontend annotations work with AI disabled and cost nothing; the AI judges *presentation* (which three risks matter most) while the *facts* are computed; signals are unit-testable arithmetic.
   - Cons: no free-form AI ranking outside the weekly digest cadence (acceptable: the debtors page orders by exposure/days-overdue deterministically).
2. **A standalone `debtor-triage` skill** producing ranked judgments on demand.
   - Pros: on-demand AI reads of the book; independent evolution.
   - Cons: a second execution to meter and stub; its output would need persistence or re-running per page view; duplicates what the digest narration already does weekly.
3. **Triage gates** — AI proposes per-customer actions (call, write off, escalate) through gates.
   - Pros: actionable inbox.
   - Cons: explicitly excluded by requirements ("triage does not create its own gates in v1"); floods the approval queue with judgment calls beside the send-critical reminder gates.

### D. System invocation & cost posture

1. **Skills invoked from job context with `invokedBy = null` (system); per-reminder metering (CHOSEN)** — one `AiExecution` per draft, one per weekly digest; V133 conditionally relaxes `ai_executions.invoked_by` if NOT NULL.
   - Pros: cost attribution stays per-action in the existing `AiExecution` ledger (the per-skill cost dashboards keep working); volume is bounded — drafts happen once per `(invoice, stage)`, not per scan run, because the ledger gates re-drafting; AI failure is contained per invoice (`SKIPPED(draft_failed)`, retryable) so one bad call cannot sink a tenant's scan.
   - Cons: a big overdue book's first scan produces a burst of drafts (bounded by overdue invoice count; the gate queue itself throttles what actually goes out).
2. **Batch drafting** — one LLM call drafts all of a tenant's due reminders.
   - Pros: fewer calls, some token savings on shared context.
   - Cons: one failure poisons the whole batch; per-reminder cost attribution lost; output parsing must split N letters reliably; letters cross-contaminate tone.
3. **Draft lazily at approval time** rather than at scan time.
   - Pros: no tokens spent on reminders that get cancelled by payment first.
   - Cons: the approver can no longer *read the draft before approving* — the queue would show promises, not letters, gutting the review's meaning; approval latency inflates with a synchronous LLM call per click.

**Decision**: Frame-owns-facts drafting (A1) over deterministic Java-assembled context (B1); triage as deterministic signals narrated inside the digest skill (C1); scan-time drafting, system-invoked, metered per reminder (D1).

**Rationale**:

1. **Partition by failure cost.** Facts (amounts, links) have catastrophic failure cost and zero benefit from AI — template them. Tone has high benefit and recoverable failure cost (a human reviews it) — model it. Judgment about *presentation* (top risks) is cheap to be wrong about — let the digest narrate. Everything lands on the side of the line its failure mode dictates.
2. **The review must review the real thing.** Scan-time drafting means the approver reads the actual letter; the gate payload *is* the email body. Any design where drafting happens after approval (D3) or where the model controls facts (A2) makes approval theatre.
3. **Smallest AI surface that delivers the value.** One new drafting skill, one narration duty added to the digest skill, zero triage skills, zero new provider machinery — consistent with the metering, stubbing, and prompt-asset conventions of the five existing skills.

**Consequences**:

- Positive: reminder emails cannot contain wrong amounts or dead payment links; house style comes from `AiFirmProfile` like every other skill; per-`(invoice, stage)` drafting bounds token spend structurally.
- Positive: triage signals are free, always-on, and testable; AI-disabled tenants keep the full debtors page.
- Positive: `StubAiProvider` extensions make the entire loop CI-exercisable with zero live tokens.
- Negative: draft-then-cancel wastes one metered execution when payment lands during the approval window — accepted; the alternative (D3) breaks review integrity.
- Negative: `invoked_by = null` introduces "system" as an actor in the AI execution ledger; dashboards/queries assuming a member must tolerate null (audited at build; conditional migration).
- Negative: tone quality depends on prompt assets per stage; a weak stage-3 prompt produces weak final notices — prompt review is part of the QA capstone.
- Related: [ADR-326](ADR-326-gated-send-safety-model.md) (the gate that carries the draft), [ADR-328](ADR-328-weekly-cash-digest.md) (the narration consumer), [ADR-329](ADR-329-trust-aware-collections-extension-seam.md) (the trust annotation input), Phase 72 AI-foundation ADRs (280–285).
