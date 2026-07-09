# ADR-329: Trust-Aware Collections Extension Seam — No-Op-Default Advisor, legal-za over ClientLedgerService, Fork-Neutral Core

**Status**: Accepted

**Context**:

Collections must be fork-neutral — every vertical bills and chases. But for the legal-za lighthouse there is one behaviour that only makes sense for law firms: when the client already holds funds in the firm's trust account, the right move is often a **section 86(4) fee transfer**, not a payment-demand email — and chasing a client whose money the firm is literally holding reads as incompetent. The platform has a precedent for exactly this shape of cross-vertical awareness: `TrustBoundaryGuard` (Phase 71) consults trust tables when they exist and treats `DataAccessException` ("trust tables absent — non-legal tenant") as "condition not applicable". The requirements mandate: core collections code never imports legal packages; the trust behaviour is an extension point, not core logic; the advisor informs, it never blocks or acts.

**Options Considered**:

### A. Shape of the extension point

1. **`CollectionsAdvisor` SPI — Spring list-injected contributor beans, no-op default (CHOSEN)** — `List<CollectionsAdvice> adviseFor(customerId)`; core collects all advisor beans; advice records `(signal, detail)` feed triage signals, drafting context, and the digest's risks section.
   - Pros: core has zero compile-time knowledge of trust (the legal advisor lives in `verticals/legal/collections/` and simply exists as a bean); the seam is generic — a future accounting-za advisor ("client has SARS refund pending") plugs in identically; list injection is the platform's own registration idiom (`AiSkill`s, `JobHandler`s register by existence); trivially testable (inject a fake advisor).
   - Cons: an interface with one production implementation at ship time (accepted: the no-op default plus the test fake make it three, and the seam cost is one file).
2. **Direct conditional trust check in core** — core calls `ClientLedgerCardRepository` wrapped in try/catch, like `TrustBoundaryGuard` does internally.
   - Pros: no new abstraction; smallest diff.
   - Cons: **core imports `verticals/legal/trustaccounting`** — the exact boundary violation the requirements forbid; every future vertical annotation grows another conditional in core; untestable without legal fixtures; `TrustBoundaryGuard` could get away with it because it lives in `integration/accounting`, not in a fork-neutral core domain.
3. **Vertical behaviour via automation rules / packs** — ship the trust check as pack-installed automation content.
   - Pros: no code seam at all; per-firm configurability.
   - Cons: the automation engine triggers on domain events, not on "the scan is assembling context" — there is no hook where a rule could inject drafting context or digest annotations; would require inventing that hook, which is a bigger seam than the SPI.

### B. What the advice is allowed to do

1. **Inform only (CHOSEN)** — advice appears as a triage signal (`TRUST_FUNDS_AVAILABLE` + amount) on the debtors page, as context the reminder drafter *may acknowledge*, and in the digest's risk narration ("consider a fee transfer"). It never suppresses a reminder, never proposes a trust transaction, never moves money.
   - Pros: trust money movement has its own heavily-guarded workflow (Phase 60: approval workflows, s86 compliance, audit) — collections must not become a second door into it; a suggestion in front of an attorney is the legally safe posture (the fee-transfer decision has professional-conduct implications no heuristic should make); the reminder still goes out unless a human decides otherwise, so collections behaviour stays uniform across verticals.
   - Cons: a firm might send a payment reminder despite available trust funds if the approver ignores the badge (their call — the badge and the drafted acknowledgment make it informed).
2. **Advice suppresses the reminder** (auto-skip when trust covers the invoice).
   - Pros: prevents the awkward email automatically.
   - Cons: silently *not chasing* is a money-affecting decision taken by a heuristic; trust balances can be earmarked for disbursements the ledger sum doesn't reveal; a suppressed reminder with no human in the loop is auto-*conduct* — the same class the founder banned for sends.
3. **Advice proposes a gated fee-transfer action.**
   - Pros: closes the loop into an approvable action.
   - Cons: creating trust transactions from the collections domain crosses into Phase 60's approval machinery from outside; a fee transfer requires judgment about matter state and billing completeness far beyond "balance > 0"; explicitly a v2+ question at best.

### C. Failure posture for the legal advisor

1. **Fail-open to empty advice on `DataAccessException` (CHOSEN)** — absent trust tables (non-legal tenant) or any lookup failure yields no advice; the scan and digest proceed.
   - Pros: mirrors `TrustBoundaryGuard`'s table-absence tolerance so one pattern governs both; advice is informational, so its absence degrades gracefully — a missing badge, never a broken scan.
   - Cons: a genuinely failing trust query is indistinguishable from "not a legal tenant" at the advice layer (mitigated: debug-logged, same as the guard; the guard itself still fail-*closes* the sync boundary where it matters).
2. **Fail-closed** — advisor failure aborts the scan for that customer.
   - Pros: never acts on incomplete information.
   - Cons: inverts the risk: the "action" protected by failing closed is *sending a normal reminder*, which is safe and human-approved anyway; blocking dunning because an optional annotation errored is tail wagging dog.
3. **Capability/profile detection instead of exception tolerance** — check the tenant's installed vertical profile before querying.
   - Pros: no exception-driven control flow.
   - Cons: couples the advisor to pack/profile metadata that trust-table existence already encodes operationally; `TrustBoundaryGuard` chose exception tolerance for the same reason — the tables are the ground truth.

**Decision**: A core `CollectionsAdvisor` SPI with a no-op default and Spring list-injection (A1); legal-za ships `TrustAwareCollectionsAdvisor` over `ClientLedgerCardRepository.sumBalancesForCustomer` that informs only (B1) and fails open to empty advice on `DataAccessException` (C1). Core collections has no `verticals/legal` imports — enforced by a boundary test in slice 83-5.

**Rationale**:

1. **The fork strategy is the product strategy.** Collections is foundation code destined for every vertical fork; one legal import in core makes the fork surgery bigger forever. The SPI costs one interface and buys a permanent boundary — the same trade `TrustBoundaryGuard` made, formalized into a seam because this time the consumer *is* fork-neutral core.
2. **Advice and action have different safety budgets.** Phase 83's whole safety model (ADR-326) exists because client-facing actions are dangerous. Advice is not an action; giving it action-like powers (suppression, transaction proposals) would smuggle un-gated conduct in through the annotation channel.
3. **Tolerance matches the guard, direction matches the stakes.** Both components ask "do trust tables apply here?"; both treat absence as non-applicability. The guard fail-closes because it protects an outbound sync boundary; the advisor fail-opens because it decorates a human-reviewed flow. Same pattern, deliberately opposite defaults, each justified by what a failure costs.

**Consequences**:

- Positive: legal firms see "R 84 200,00 held in trust — consider a fee transfer" on the debtors page, in the draft's context, and in the digest — the lighthouse-vertical polish — while consulting-za/accounting-za run identical core code with zero trust language anywhere.
- Positive: the seam generalizes — future advisors (retainer balance available, payment plan active) are new beans, not core changes.
- Positive: boundary enforced by test, not convention (core-imports check in 83-5).
- Negative: one more indirection layer for a single v1 implementation; readers must find the legal advisor to see the trust behaviour.
- Negative: informed-but-ignorable means the awkward "reminder despite trust funds" email can still be approved — mitigation is UI prominence of the badge, not prevention.
- Negative: advice quality depends on `sumBalancesForCustomer` semantics (aggregate balance, not matter-earmarked availability) — the detail string says "held in trust", deliberately not "available to transfer".
- Related: [ADR-325](ADR-325-collections-domain-dunning-engine.md) (the scan/digest that consume advice), [ADR-326](ADR-326-gated-send-safety-model.md) (why advice must not act), [ADR-327](ADR-327-ai-reminder-drafting-debtor-triage.md) (advice as drafting context), Phase 60 trust-accounting ADRs (the machinery deliberately not entered), Phase 71 `TrustBoundaryGuard` (the tolerance pattern mirrored).
