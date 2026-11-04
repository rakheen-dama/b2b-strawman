# ADR-362: Interest runs are persisted drafts with a second-member approval surface

**Status**: Accepted

**Context**:

The interest-run lifecycle is already a persisted state machine on the backend: `InterestRun` (`interest_runs`, `status` is a `String` column — `"DRAFT"` at construction `InterestRun.java:74`, `"APPROVED"` at `InterestService.java:458`, `"POSTED"` at `:565`; no Java enum, no cancel state) with `created_by` and `approved_by`; `InterestService.approveInterestRun` (`:427-473`) requires DRAFT, requires calculated allocations ("Interest run not calculated", `:444-449`) and blocks self-approval at `:452-456` (`"Self-approval not allowed"` when `approverId.equals(run.getCreatedBy())`); the controller gates approve on `APPROVE_TRUST_PAYMENT` (`InterestController.java:57-59`) and create/calculate/post on `MANAGE_TRUST`. The gap is that the UI holds the only handle to a DRAFT in component state: `InterestRunWizard.tsx:63-68` keeps `runId`, `run`, `allocations` in `useState`, `handleOpenChange` resets to step 1 on close (`:76-80`), and the runs table (`trust-accounting/interest/page.tsx:134-187`) has no actions column, no row link and no detail route, with `<Badge>{run.status}</Badge>` rendering the raw enum (`:177`). A run created by one member can therefore never be approved by another: the second member cannot reach it (G88-F39, MEDIUM; red test `day-60-interest.spec.ts:159-175`, fixme at `:160`, expects a row button `/Review|Open/`, the wizard at "Step 3 of 4", "Approve Run", then "Post"). `fetchMyCapabilities()` returns no member id (`capabilities.ts:7-12`), so the client cannot tell the creator from an approver; and `InterestRunResponse` (`InterestService.java:82-96`) carries no allocation count, so the table cannot tell a calculated DRAFT from one never calculated (`totalInterest` is 0 in both cases when the balance is zero).

**Options Considered**:

1. **Wizard-only, held open: keep the DRAFT handle in the wizard and ask the creator to leave the dialog open until a colleague approves on the same screen.**
   - Pros:
     - No change at all.
   - Cons:
     - Not a workflow: the Day 60 scenario is "Thandi creates, Bob approves" on different sessions and days; a page refresh or a closed dialog orphans the DRAFT forever, and the backend's persisted state is wasted.
     - Fails the rule that trust approvals are a second person's act (`APPROVE_TRUST_PAYMENT`, the same split the 72.1 PAYMENT step uses).
2. **A dedicated detail route (`/trust-accounting/interest/[runId]`) with its own approve/post page, separate from the wizard.**
   - Pros:
     - Deep-linkable from notifications; a natural home for the allocation table.
   - Cons:
     - Two UIs for one lifecycle: the wizard already renders create → calculate → approve → post with the allocation table at step 3; a detail page duplicates that rendering and the approve/post handlers, and the two drift (the F43/F44 class).
     - A new route, layout and server page for a surface that is reached from one table; nothing in the requirements asks for a run detail destination.
3. **Runs table gains an Actions column whose actions re-open the existing wizard on the persisted run at the matching step; the backend adds two additive fields — the member id and the run's allocation count (CHOSEN).**
   - Pros:
     - The wizard stays the single UI for the lifecycle: a new `initialRun` prop loads `fetchInterestRunDetail(runId)` (already returns run + allocations, `actions.ts:62`) and jumps to the step the status implies.
     - Every action is gated by capability flags the page already computes (`hasManageTrust`, `canApproveTrust`, `page.tsx:48-51`) and by the run's state, so the table shows exactly the actions the backend would accept.
     - Two additive DTO fields: `MyCapabilitiesResponse.memberId` lets the client hide "Review & approve" from the creator (the backend self-approval block stays the authority and its error string is still surfaced verbatim if the UI is bypassed); `InterestRunResponse.allocationCount` (a count query) tells the table whether to offer "Continue" or "Review".
   - Cons:
     - The wizard gains a "resume" mode (load state from the server, skip steps) and a creator-facing "Awaiting approval by another member" state; its tests grow accordingly.
     - No cancel/delete for abandoned drafts in this phase (open question): a DRAFT that is never calculated stays in the list.
4. **Move approval to the trust approvals queue (the existing PAYMENT approval surface) and list DRAFT interest runs there.**
   - Pros:
     - One approvals inbox for everything `APPROVE_TRUST_PAYMENT` covers.
   - Cons:
     - The approvals queue is built on `TrustTransactionApprovalEvent` / `AWAITING_APPROVAL` transactions; an interest run is not a transaction until it is posted, so the queue would need a second row type and a second approve path — more surface than the gap warrants, and the posting step still has to happen in the wizard.

**Decision**: Option 3 — the backend is unchanged except two additive fields: `MyCapabilitiesResponse.memberId` (the id `RequestScopes.requireMemberId()` resolves) and `InterestRunResponse.allocationCount` (the count of `interest_allocations` for the run); no new states, no new endpoints. The runs table gains an **Actions** column: DRAFT with `allocationCount == 0` → "Continue" (`MANAGE_TRUST`); DRAFT with `allocationCount > 0` → "Review & approve" (`APPROVE_TRUST_PAYMENT` and `run.createdBy !== memberId`), otherwise "Review"; APPROVED → "Post" (`MANAGE_TRUST`). Each opens `InterestRunWizard` on the existing run via a new `initialRun` prop (the wizard loads `fetchInterestRunDetail(runId)` and jumps to the matching step); the creator sees "Awaiting approval by another member"; the status badge renders from a label map (Draft / Approved / Posted), never the raw enum; the backend error "Self-approval not allowed" (`InterestService.java:452-456`) remains the authority and is surfaced verbatim as the fallback.

**Rationale**:

1. **The backend already is the design; the UI was the gap.** Everything server-side the requirements asked for exists: DRAFT persisted with `createdBy`, approve/post by id, capability split on the controller, self-approval refused. Adding states or endpoints would re-decide a working lifecycle to fix a missing button. This is the "fix at the right layer" rule.
2. **Trust approvals are a two-person control; the UI must make the second person's path obvious and the first person's path closed.** Hiding "Review & approve" from the creator is a convenience; the backend block is the control. The additive `memberId` is the smallest honest way to give the client that fact — reading it from the auth context would couple the page to the Keycloak subject rather than the `members.id` the backend compares.
3. **One UI for one lifecycle avoids the drift class Phase 88 kept finding.** The wizard already renders the allocation table and the approve/post handlers; re-opening it at the right step reuses every line of that, and the e2e expectation ("Step 3 of 4", "Approve Run") is met without a second page.
4. **Raw enums are a client-facing defect firm-side too.** R-E4c bans them on the portal; the interest table applies the same label-map rule here so the Day 60 walk does not record "DRAFT" as a new LOW.
5. **Observed, not inferred.** The slice un-fixmes `day-60-interest.spec.ts:160` (created by Thandi, approved by Bob, then posted) and the Keycloak walk records the Day 60 approval by a second member at 1024 and 1440.

**Consequences**:

- Positive:
  - A DRAFT run outlives the browser session and is reachable by any member with the right capability from the runs table.
  - No migration, no new endpoint, no new state; `MyCapabilitiesResponse` gains `memberId` and `InterestRunResponse` gains `allocationCount`; the TS types follow (and both are candidates for the [ADR-365](ADR-365-wire-shape-contract-pins-compliance.md) pin style if it drifts again).
  - The wizard's `initialRun` mode is the one place "resume a run" lives; a future notification deep link can reuse it.
- Negative:
  - Abandoned drafts accumulate: there is no cancel/delete endpoint (`InterestController` has none) and none is added in this phase — **open question §14.12** (whether DRAFT runs may be deleted by `MANAGE_TRUST`, and whether an APPROVED run can be withdrawn before posting).
  - The creator's view of a calculated DRAFT is read-only ("Review" + the waiting message); if the firm has only one member with `APPROVE_TRUST_PAYMENT` and that member created the run, the run is stuck until a second approver exists — correct by design, but the empty state must say so.
  - `InterestRunWizard` tests gain resume cases per status and a self-approval-hidden case; the page test gains the actions matrix (capability × status).
- Explicitly not done: no detail route, no approvals-queue integration, no backend enum for `status`, no behaviour change to `InterestService` (`allocationCount` is a read-side count on the response record).

**Related**: [ADR-346](ADR-346-matter-visibility-and-team-management.md) (capability-gated actions pattern), [ADR-355](ADR-355-owner-only-capabilities-client-side.md) (client-side capability hiding is convenience; the backend is the authority), [ADR-361](ADR-361-client-ledger-running-balance-server-side.md) (same slice family, backend as single source), [ADR-365](ADR-365-wire-shape-contract-pins-compliance.md); `architecture/phase89-go-live-readiness.md` §14.3, §14.4, §14.9, §14.11, §14.12; `qa_cycle/evidence/phase88-rewalk/full/gaps.md` G88-F39.
