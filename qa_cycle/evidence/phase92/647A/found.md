# 647A — found outside scope (reported, not fixed)

Slice 647A (record from statement). Every item below was re-checked on branch
`epic-647A/record-from-statement` (base `main` 1c9fa37c8). None is fixed in 647A; each names a
suggested owner. Orchestrator rulings on the brief's §14 decisions are recorded in the PR body.

## 1. HF-7 / 649B F3 — a posted REVERSAL contra is an auto-match candidate (still live, newly reachable)

- `TrustTransactionRepository.findUnmatchedCandidates` selects `status IN ('RECORDED', 'APPROVED')
  AND bankStatementLineId IS NULL` — it does not exclude `transaction_type = 'REVERSAL'` (nor rows
  with `reversal_of IS NOT NULL`).
- 647A makes this reachable from a new path: unmatching a recorded line books a contra that is
  `RECORDED`, unlinked, of the opposite cashbook sign, dated the line's date. A later auto-match of a
  *different* statement line of the same amount and opposite sign can pick that contra. The
  outstanding query cancels an original/contra pair only while **both** are off any line, so such a
  match would put the original back into the outstanding items.
- Not reproduced in 647A (out of the task list). Suggested owner: **649C or 652B** — a one-line
  exclusion of `REVERSAL` rows from the candidate query, behind a test that reproduces the false
  match first.

## 2. D1 — the section 86(4) refusal of FUND_INTEREST is unreachable until 648C (owner 648B)

- §16.3.5: `FUND_INTEREST` "is refused on a section 86(4) investment account". The section is
  derived from `account_type = 'INVESTMENT'` **and** `investment_basis = 'CLIENT_INSTRUCTION'`; the
  `investment_basis` column arrives in 648C. Using `accountType == INVESTMENT` as a proxy would wrongly
  refuse section 86(3) Fund interest (the posting table allows `FUND_INTEREST` on s86(2) and s86(3)
  accounts).
- Orchestrator ruling D1 → (a): no refusal in 647A; the Record dialog filters by line sign only.
  Owner **648B** (it adds `INVESTMENT_INTEREST`, the classification the refusal points to, after
  648C's section derivation).

## 3. Future-dated statement line — recordable, but not unmatchable until its date (observation)

- `recordFromLine` accepts a line dated after today (the spec has no rule). Unmatching it goes through
  `reverseForUnmatch` → `resolveReversalDate`, which refuses a reversal date in the future ("The
  reversal date cannot be in the future", `TrustTransactionService`). So a future-dated recorded line
  cannot be unmatched until that date.
- No fix; an orchestrator / product call if it matters (refuse future-dated lines at record, or let
  the unmatch contra take the line's date even when future).

## 4. HF-4 / F-646E-3 — `PAIR` reversal branch always calls `reverseTransferOut` (carried to 647D)

- Still live; not reachable in 647A (647A posts only `FUND_INTEREST`, `FUND_SWEEP`, `BANK_CHARGE`,
  `CHARGE_REFUND`, all `Reversal.IMMEDIATE`). `UNIDENT_ALLOC_OUT` is also `Reversal.PAIR`; its first
  writer is **647D**, which owns the fix.

## 5. HF-8 / 649B F5 — `manualMatch` refuses a `REVERSED` original (noted)

- `TrustReconciliationService.manualMatch` accepts only `APPROVED` / `RECORDED` rows; a `REVERSED`
  original cannot be matched to its statement line. Unchanged by 647A; noted for 649C / 652B.

## 6. Other observations from building 647A

- **VAT with more than two decimals — FIXED in the PR #1794 review round.** `chargeVatAmount` now
  carries `@Digits(integer = 13, fraction = 2)` (400 "Validation failed") and the Zod schema refuses
  more than two decimals; `7.499` on a −7.50 line no longer reaches `chk_trust_txn_charge` as a 500.
- **Native number validation in the Record dialog.** In jsdom (vitest) an `<input type="number"
  step="0.01">` holding `7.5` blocked the form submit with no message (adding `noValidate` made the
  same test pass, so native constraint validation was the cause; a real browser was not checked). The dialog's form is `noValidate`: the Zod schema (and the server) are the
  authority. Not a defect elsewhere today, but any trust dialog that relies on native `step` checks
  could show the same silent block in tests.
- **`bank_statement_line.unmatched` for matched lines still has no details.** 647A adds
  `classification`, `reversed_transaction_ids`, `statement_id` for a recorded line only (the
  `AUTO_MATCHED` / `MANUALLY_MATCHED` path is unchanged, per the brief). The activity sentence
  distinguishes the two by the presence of `classification`.

## 7. Out of scope from the PR #1794 review round (orchestrator rulings)

- **`autoMatchStatement` does not lock the lines it matches.** The review round put a
  `findByIdForUpdate` line lock on `manualMatch`, `excludeLine`, `unmatch` and record-from-statement,
  and a statement lock (`BankStatementRepository.findByIdForUpdate`, line-then-statement order) on
  every path in `TrustReconciliationService` / `StatementLinePostingService` that changes
  `matched_count` — except auto-match. `autoMatchStatement` reads the statement and its `UNMATCHED`
  lines without a lock and sets `matched_count = alreadyMatched + autoMatched`, so a record / manual
  match committed on the same statement while auto-match runs can be overwritten (a line flipped to
  `AUTO_MATCHED` over a `RECORDED` state, or the count losing an update). Fix: lock the statement
  first, then re-read each candidate line under `findByIdForUpdate` and skip it unless still
  `UNMATCHED`. Owner: orchestrator to assign (652A/652B touch the matching flow).
- **Stale recalculation after a wizard reset in the three pre-existing handlers.** The review round
  added a wizard-generation guard to `handleRecord` only (`reconciliation/new/page.tsx`).
  `handleMatch`, `handleExclude` and `handleUnmatch` share the defect: each awaits
  `calculateReconciliation(reconciliation.id)` and then calls `setReconciliation(...)` with no check,
  so a Back-and-reset while the recalculation is in flight restores the old account's reconciliation
  into the new wizard (and `handleStartReview` then reuses it instead of creating one). Owner
  **652A**, which already owns those handlers' missing `else` on a failed recalculation; the same
  `wizardGeneration` ref is there to reuse.
