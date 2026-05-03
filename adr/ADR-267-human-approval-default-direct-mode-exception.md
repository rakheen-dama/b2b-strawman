# ADR-267: Human Approval Is the Default for AI Output; Inbox Comment-Posting Is the Single DIRECT-Mode Exception

**Status**: Accepted

**Context**:

Phase 52 established that every AI write action is reversible-only and gated on an explicit confirmation card before commit ([ADR-203](ADR-203-completable-future-confirmation.md)). That model works because the invoker is the user — the confirmation card pauses an SSE stream and the same user clicks Confirm or Cancel. Phase 70 introduces a new invocation path: the Phase 37 automation engine fires a specialist via `INVOKE_AI_SPECIALIST`. The user is no longer in the loop at invocation time. A weekly scheduled rule "every Monday 07:00 summarise active matters" has no human waiting on a confirmation card.

Two competing pulls exist. On one side, the existing discipline ("nothing AI-generated commits without a human") is a real safety property — it is what lets us call this output a "proposal" rather than an "action," and it is what keeps a hallucinated time-entry polish from silently appearing on a client invoice. On the other side, requiring approval on every scheduled output (e.g. a weekly summary comment on every active matter, generating perhaps 30+ pending review-queue items every Monday morning) creates review-queue fatigue that can dominate the drudgery cost the automation was meant to remove. If a partner has to scroll past 30 pending summaries every Monday, the automation has not saved time.

**Options Considered**:

1. **Universal human approval — every AI output, every invocation source, requires explicit approval.** No DIRECT mode. All scheduled summaries, all extracted fields, all polished descriptions sit in the review queue until a human clicks Approve.
   - Pros:
     - Maximum safety. No AI output ever reaches a customer-facing surface or a persisted entity without a human eye.
     - Single mental model: AI proposes, human disposes. Every time.
     - Easiest to explain in a sales call: "AI never acts on its own."
   - Cons:
     - Review-queue fatigue is real and predictable. A 30-matter firm with the weekly summary template enabled gets 30 pending items every Monday at 07:01. By week 4, the partner stops opening the queue and the automation is dead weight.
     - The drudgery cost ("read 30 weekly status emails") is replaced by a near-identical drudgery cost ("approve 30 weekly summaries"). The automation has shifted the friction without removing it.
     - High-volume / low-stakes outputs (a comment on a matter, attributed and reversible by deletion) get the same friction as high-stakes outputs (an invoice line-item rewrite). The friction is not proportional to the risk.

2. **Universal DIRECT mode — every AI output applies immediately, audit captures the act.** No review queue gate; the `AiSpecialistInvocation` row records what happened but doesn't block it.
   - Pros:
     - Zero review friction. Maximum automation benefit.
     - Simplest implementation — no PENDING_APPROVAL state machine.
   - Cons:
     - Hallucinated time-entry polish reaches a real client invoice. Hallucinated extracted RSA ID number reaches a customer record. Hallucinated summary appears on a matter as a comment that the firm cannot un-publish without manual deletion.
     - Phase 52's whole confirmation-on-write contract ([ADR-203](ADR-203-completable-future-confirmation.md)) is broken — the contract doesn't care whether the user or a rule invoked the action; what matters is that no AI output mutates entities without a checkpoint. Phase 70 cannot be the moment that contract dissolves silently.
     - Reputational risk: one bad polished invoice reaching a real client is a much bigger story than 30 pending review-queue items.

3. **Human approval default; one carved-out exception for low-stakes, attributed, reversible outputs (CHOSEN).** Every Billing and Intake output, and every REVIEW-mode invocation regardless of specialist, queues for human approval. The single DIRECT exception: Inbox specialist's comment-posting output, when invoked by a scheduled / rule-driven automation that explicitly opts into DIRECT mode. The output posts immediately as a comment with a visible "Posted by Inbox Assistant" tag; the `AiSpecialistInvocation` row records `status=AUTO_APPLIED` so it remains audit-visible.
   - Pros:
     - Preserves the Phase 52 confirmation contract for every output that mutates an existing entity (Billing polishes time-entry descriptions; Intake updates customer fields). These are exactly the cases the contract was written for.
     - Removes the fatigue case. Weekly matter summaries (the highest-volume scheduled output) post directly with a clear AI-attribution badge, and the partner reads them when they next open the matter — instead of approving 30 summaries Monday morning.
     - The exception is justifiable on three independent grounds: (i) the output is a *new comment*, not a mutation of any existing entity; (ii) it is reversible by deletion (a comment can be removed in one click, unlike a polished line on a sent invoice); (iii) it is clearly attributed via the "Posted by Inbox Assistant" tag — readers know it was AI.
     - DIRECT mode is opt-in per rule. A firm that wants every summary reviewed before posting can leave the rule's `mode` set to REVIEW; the carve-out is a *capability* of the action config, not a default.
     - Audit visibility is unchanged — every DIRECT post still writes an `AiSpecialistInvocation` row and still emits `ai.specialist.invoked` and `ai.specialist.auto_applied` audit events. The output is fast, but the trail is complete.
   - Cons:
     - Two modes (REVIEW, DIRECT) instead of one. The action config schema must support both, and the executor must branch.
     - "DIRECT only for Inbox comment-posting" is a policy boundary enforced in code (the `InvokeAiSpecialistActionExecutor` validates that `mode=DIRECT` is only legal when `specialistId=INBOX` and the output is a `PostInboxSummary` tool call). A future specialist that wants DIRECT mode requires a deliberate ADR amendment, not a config flag.

**Decision**: Option 3 — human approval is the default; the single carve-out is Inbox specialist comment-posting in scheduled DIRECT mode. The `InvokeAiSpecialistActionExecutor` validates that any non-Inbox specialist or any non-comment-posting tool with `mode=DIRECT` is rejected at rule-save time and at execution time.

**Rationale**:

The risk-vs-fatigue trade-off is not symmetric. A polished time-entry that hallucinates a "two-hour conference call with senior partner" reaches a client on a sent invoice and damages trust. A summary comment that gets one fact wrong appears on a firm-internal matter page tagged as AI-generated; the partner reads it, notices the error, deletes it (or comments correcting it), and moves on. The cost-of-error is order-of-magnitude different. The safety policy should be proportional to that cost.

The three carve-out conditions — new comment (not mutation), reversible by deletion, clearly AI-attributed — together form a sufficient safety net for the Inbox case. Removing any one of them changes the calculus: if the comment were not attributed, readers might mistake AI text for partner notes; if the output mutated an existing comment instead of creating a new one, deletion would not be a clean undo; if the output were anything other than a comment (e.g. a status change, a customer field update), the "reversible by deletion" property would not hold. The carve-out is therefore not a slippery slope; it is a discrete cell in a 3D matrix, with no neighbouring cell that opens up further.

Universal human approval (Option 1) is rejected because the most important design goal of Phase 70 is removing drudgery, and a 30-item approval queue every Monday morning is a drudgery surface, not a removal of one. Universal DIRECT (Option 2) is rejected because Phase 52's confirmation contract is a real safety property; we are not silently retiring it for a small UX win.

The opt-in nature of DIRECT (per-rule config, default REVIEW) means a cautious firm can run every weekly summary through approval if they want. The carve-out is the maximum freedom available, not a default behaviour.

**Consequences**:

- Positive:
  - The Billing polish + grouping outputs always require human approval. The Intake field-extraction always requires human approval. The Inbox on-demand summary (member-invoked) always requires human approval (because the user is in front of the panel; the same Phase 52 confirmation flow applies). Only the Inbox scheduled summary in opt-in DIRECT mode posts without a queue gate.
  - The review queue stays tractable in volume. A typical firm enabling all four pre-seeded templates sees a manageable trickle of REVIEW items (extracted fields per intake submission, polished invoices per send) rather than a flood.
  - DIRECT-mode outputs still create an `AiSpecialistInvocation` row with `status=AUTO_APPLIED`. They appear in the review queue page filtered by status — a partner who wants to retrospectively review the week's auto-posts can do so without ever having to gate them at write time.
  - "Posted by Inbox Assistant" attribution is rendered as a visible badge in the comment list; the comment author field also records the system actor. No reader is fooled.

- Negative:
  - The action executor has a branch on `mode`. Slight added complexity over a uniform path.
  - The policy boundary ("DIRECT only for Inbox + comment posting") is enforced in code, not in the data model. A maintenance commit could weaken it without a migration; the ADR + a unit test that asserts the rejection are the guardrails.
  - A future specialist that wants DIRECT mode requires a fresh ADR amendment that re-justifies all three carve-out conditions.

- Neutral:
  - The DIRECT-mode posting still emits `ai.specialist.auto_applied` audit events that show up in the Phase 69 audit log filtered by event-type. A regulator asking "what did the AI post on its own this month?" can answer the question from the audit log.
  - The opt-in default (REVIEW) for the pre-seeded weekly-summary template is a product call, not an architecture call. The seeder will ship it as DIRECT in `legal-za` and `consulting-za` profiles where the partner-fatigue case is strongest; firms can flip it back to REVIEW per-rule.
  - **Future-hardening.** The DIRECT-mode boundary is currently encoded as `specialist.id().equals('INBOX')` in the runner. A future hardening pass should add `directModeAllowedTools: List<String>` to the `Specialist` record so the boundary is data-described, not branched-on by id. Defer until a second DIRECT-mode use case appears.

- Related: [ADR-203](ADR-203-completable-future-confirmation.md) (Phase 52 confirmation-on-write — the contract this ADR re-affirms), [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) (specialist registry — the Inbox specialist's `automationCapable` flag), [ADR-270](ADR-270-ai-specialist-invocation-jsonb-output.md) (the invocation table — captures both REVIEW and DIRECT outcomes), [ADR-271](ADR-271-scheduled-trigger-extension.md) (the scheduled trigger that drives the Inbox-DIRECT pre-seeded template), [ADR-264](ADR-264-audit-export-is-auditable.md) (Phase 69 reflexive audit — same discipline of "if it's a privileged action, the trail records it").
