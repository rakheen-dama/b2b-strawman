# FU-6b — refuse duplicate same-end-date statements unless replacing; replace / discard an imported statement

Defects: **F-646C-1** (two same-account statements ending on the same date both pass the period-fit
rule) and **F-652C-1** (an imported statement cannot be retired or replaced: a re-upload strands the
first statement's matches; a statement refused at wizard step 4 stays `IMPORTED` with no UI to
discard it and keeps counting in period checks and `UNRESOLVED_STATEMENT_LINES`).
Rulings / defaults: founder ruling F-646C-1; orchestrator default FU-6 D-1 (rules 1–6); 652C D-5,
D-6, D-12.

## Root cause

- **F-646C-1.** The import (`TrustReconciliationService.importBankStatement`) accepted any number
  of statements per account. Period-fit rule 3 (`requireStatementFitsPeriodEnd` →
  `BankStatementLineRepository.findOtherStatementsWithLinesBetween`) refuses only when *another*
  statement has a line **strictly after** the selected statement's last line and on or before the
  period end. Two statements whose last lines are on the same date can never refuse each other, so
  both fit every period end from that date on.
- **F-652C-1.** No statement status beyond `IMPORTED` / `RECONCILED` was ever written. There was no
  way to retire a statement, so its matches stayed on its lines: `findUnmatchedCandidates`
  (`t.bankStatementLineId IS NULL`) never offered those rows to a new statement. Rule 3 and
  `findUnresolvedInUnreconciledStatements` read every non-reconciled statement, so a leftover kept
  counting.

Red reproduction: `red-run.txt` (3 of 3 fail on unmodified code at 2fde9a191, for the reasons
stated there).

## Policy as implemented (default FU-6 D-1, rule by rule)

1. **Never hard-delete.** A replaced or discarded statement gets the new terminal status
   `RETIRED`, with `retired_at`, `retired_by`, `retired_reason`, and `replaced_by_statement_id` on
   a replace (V30, `chk_bank_stmt_retired`). Its file key, file and lines are kept. No trust row is
   deleted or rewritten. A replace's reason is "Replaced by <new file name>"; a discard's reason is
   the user's (required, max 500).
2. **Replace intent.** `POST /api/trust-accounts/{id}/bank-statements` takes an optional
   `replacesStatementId` (multipart field / request param). Without it, an import whose end date
   equals that of an existing non-RETIRED statement of the same account is refused:
   `ResourceConflictException` 409, title "A statement ending on this date is already imported",
   properties `code=bank_statement.duplicate_end_date`, `existingStatementId`, `existingFileName`.
   **"Same end date" definition:** the date period-fit uses for a statement, i.e. its last line's
   date, else its `period_end` when it has no lines (`requireStatementFitsPeriodEnd`:
   `findLastLineDate(...).orElse(statement.getPeriodEnd())`). The new statement's end date is
   computed the same way from the parsed lines (`endDateOf`); existing statements come from
   `BankStatementRepository.findLiveStatementsEndingOn` (`COALESCE(MAX(line date), period_end)`).
   **Why this definition:** rule 3 compares other statements' lines with the selected statement's
   last line date. Two statements are mutually indistinguishable for every period end exactly when
   their last-line dates are equal. When the dates differ, the earlier one fails rule 3 for any
   period end at or after the later one. So equality of this date is the precise ambiguity
   condition. RECONCILED statements count as "existing" too (conservative: a new statement that
   ends on a reconciled statement's date is refused, and a reconciled statement cannot be
   replaced).
3. **Refusals.** Replace and discard are refused (400 `InvalidStateException`, title "Cannot
   replace statement" / "Cannot discard statement") in these cases:
   - the old statement is `RECONCILED`;
   - a COMPLETED reconciliation uses it;
   - it has a `RECORDED` line ("Unmatch it first, which reverses those entries");
   - it is already RETIRED;
   - (replace) it belongs to another account.

   Otherwise its `AUTO_MATCHED` / `MANUALLY_MATCHED` lines are released through `releaseMatch`.
   That is the code `unmatch` now uses too, extracted unchanged: the line goes back to `UNMATCHED`
   with no transaction and no confidence, and the row is unlinked through
   `postingService.linkBankLine(row, null)`. `matched_count` falls by one per released line (so it
   is 0 after a retire, since no RECORDED line can remain). EXCLUDED lines stay as they are and
   retire with the statement.
4. **Draft reconciliations.**
   - **Replace:** every DRAFT on the old statement is re-pointed to the new one and recalculated
     through 652C's `continueDraft` (D-5). This is audited as `trust_reconciliation.statement_changed`.
     If the new statement does not fit a draft's period end, the whole replace is refused and
     nothing is stored. The detail names the draft's period end and the period rule that failed.
   - **Discard — DEFAULT CHOSEN: refuse** while a DRAFT uses the statement. The detail reads "The
     draft reconciliation to <date> uses this statement. Continue that reconciliation with another
     statement, or import the corrected statement as its replacement."
   - **Why refuse rather than detach:** it is the conservative option. It never changes a
     reconciliation, and it never creates a null-statement draft (the F-652C-3 legacy state, which
     calculate/complete answer with a stale-statement 409). It still keeps the wizard usable:
     - the step-4 leftover (the case discard exists for) has no reconciliation, because the
       wizard's create was refused;
     - a draft on a wrong statement is moved by a new wizard run (create re-points it, 652C), or
       by replacing the statement, after which the statement is free to discard.
5. **Discard.** `POST /api/bank-statements/{id}/discard` `{reason}` (`MANAGE_TRUST`) retires an
   eligible statement without a replacement. This covers the step-4 leftover.
6. **RETIRED is excluded wherever a live statement counts.** The full list of readers is in the
   next section.

Audit events, entity type `bank_statement` (lowercase):
- `bank_statement.replaced`: `trust_account_id`, `file_name`, `reason`, `replaced_by_statement_id`,
  `replaced_by_file_name`, `repointed_reconciliation_ids`.
- `bank_statement.discarded`: `trust_account_id`, `file_name`, `reason`.
- The existing `bank_statement.imported` gains `replaces_statement_id` on a replace.

## Readers of bank statements / lines, and what each does with RETIRED

Every reader was found by grepping `BankStatementRepository`, `BankStatementLineRepository`,
`bank_statements` and `bank_statement_lines` across `backend/src/main` (no reader outside
`verticals/legal/trustaccounting`; MCP / dashboard read through the integrity service).

| Reader | Change |
|---|---|
| `BankStatementLineRepository.findOtherStatementsWithLinesBetween` (period-fit rule 3) | `AND s.status <> 'RETIRED'` |
| `BankStatementLineRepository.findUnresolvedInUnreconciledStatements` (`UNRESOLVED_STATEMENT_LINES`, dashboard/integrity) | `status NOT IN ('RECONCILED','RETIRED')` |
| `requireStatementFitsPeriodEnd` (statement selection: create, calculate, complete) | refuses a RETIRED statement ("Statement retired") |
| `autoMatchStatement` | refused on a RETIRED statement, checked on the plain read and again under the statement's lock in step 3 (a retire that held the lines releases them, so a waiting auto-match could otherwise match lines of a now-retired statement) |
| `manualMatch` | refused (statement locked after line) |
| `excludeLine` | refused (plain read after the line lock; a retire locks every line first) |
| `unmatch` (matched line) | refused alongside the RECONCILED check (defence in depth: a retired statement has no matched lines) |
| `StatementLinePostingService.recordFromLine` | refused after the statement lock |
| `TrustInvestmentService.lockStatement` (section 86(4) interest from a line, both paths) | refused |
| `listBankStatements` / `GET …/bank-statements` | RETIRED hidden by default; `?includeRetired=true` returns them with status and who/when/why |
| `getBankStatement` | unchanged: returns a retired statement with its status and retirement fields (audit) |
| `TrustIntegrityService.unresolvedLines` with a statement id; `TrustBooksService.bankPosition`; `toReconciliationResponse` (`sumExcludedAmount`, unresolved count) | read the reconciliation's own statement. After FU-6b that is never a retired one: replace re-points it, discard refuses, selection refuses. Unchanged |
| `TrustTransactionRepository.findUnmatchedCandidates` (match candidates) | unchanged: released rows have `bank_statement_line_id = NULL`, so they become candidates again |
| `TrustLedgerQueryRepository` ~581, `TrustReportQueryRepository` ~157 (join line → statement for a linked row) | unchanged: no row stays linked to a retired statement's line (matches released, RECORDED refused) |
| `TrustTransactionService.reverseForUnmatch` | unchanged: RECORDED lines only, and a statement with one is never retired |

Frontend: the reconciliation list now lists the statements not yet reconciled (the list endpoint
hides RETIRED ones, so the UI hides them too: the "hide" option of rule 6). The wizard keeps
working only on the statement it uploaded.

## Lock order

This is consistent with `TrustPostingService`'s global order (the path's own rows, with the bank
statement after its lines, then client anchors, then account rows), with the line → transaction →
statement order (`findByIdForUpdate` Javadoc; auto-match CF-3), and with 652C D-13 (reconciliation
→ statement → account).

**Replace / discard** (`prepareRetirement`, then `retire`):
1. Every reconciliation on the statement: ids first (`findIdsByBankStatementId`, so no entity is
   loaded), then each row via `findByIdForUpdate` in id order (first read, fresh). COMPLETED
   refuses; DRAFT refuses a discard. A row re-pointed elsewhere while we waited is skipped.
2. Every line of the statement, in one locking query in auto-match's order (date, then id —
   `findByStatementIdForUpdate`). Auto-match locks a subset of the same rows in the same order, so
   the two never lock lines crosswise. A RECORDED line refuses. Matched lines are released, which
   updates their trust rows (the transaction step).
3. The statement: `findByIdForUpdate`, as its first read. It refuses when RETIRED, RECONCILED or
   of another account. Then the reconciliations on it are read again; one not locked in step 1
   refuses (409 "Reconciliation started at the same time"). `matched_count` is lowered.
4. Import only: the account's **import lock**, last. This is a transaction-scoped advisory lock,
   `pg_advisory_xact_lock(hashtextextended('bank_stmt_import:' || current_schema() || ':' ||
   accountId, 0))` (`BankStatementRepository.lockImportsOfAccount`, a native query). It is keyed
   per tenant schema and account. It serialises two imports of one account, so two same-end-date
   imports cannot both pass the check. A plain import takes only this lock. (Review round 1,
   finding 2: it replaces the trust account row `FOR UPDATE` of e5ef682, which made every posting
   on the account wait for the whole import, including the inserts, recalculation and storage
   upload.)
   - **Why it cannot deadlock.** Only an import takes it. After taking it, an import waits on
     nothing another import holds:
     - it inserts new rows;
     - it writes the old statement and drafts, which it locked before;
     - it reads the books plainly (the recalculation takes no row lock);
     - it uploads the file;
     - the FK `KEY SHARE` locks of its inserts conflict only with an account `FOR UPDATE` taker
       (complete's Rule 54.15 list lock, an account update, a final approval). None of those takes
       the advisory lock, and none waits on rows an import holds, other than through the
       reconciliation-row and statement locks, which an import takes before the advisory lock in
       the global order.

     A second import of the account that waits on the lock holds only its own old statement's rows
     (reconciliations, lines, rows, statement), and the first import never asks for those after
     taking the lock. A contended old statement is waited on before the lock, in `prepareRetirement`
     steps 1–3. So no cycle can close through the advisory lock.
   - **Other paths.** No other path relied on the import's account row lock. It existed only in
     e5ef682 (this PR), and no other code reads it.

**createReconciliation** now share-locks the statement (`findByIdForShare`, `FOR SHARE`) after the
reconciliation row, as its first read:
- A create that commits first is seen by the retire's re-query in step 3, and the retire refuses.
- A create that waits on a retire then finds the statement RETIRED.
- Two creates on one statement do not wait on each other. A share lock was chosen over `FOR UPDATE`
  because create never changes the statement. With `FOR UPDATE`, the 652C test
  `concurrentFirstCreates_oneRow_theLoserGets409NotA500` blocked on the statement instead of the
  unique index, and it now passes unchanged.

A refusal after a release rolls the release back with the transaction, as `unmatch` already does
when `requireNotReconciled` refuses.

**Review fix (Opus code-reviewer, before the PR).** `releaseMatch` now reads the matched row with
`TrustTransactionRepository.findByIdForUpdate`. It used a plain `findById`, the code `unmatch`
already had.
- **The lost update.** `TrustTransaction` has no `@Version` or `@DynamicUpdate`, so the unlink
  writes back the whole row. A reversal locks the row but never the line, so a reversal committed
  between the plain read and the flush was overwritten: the original went back to RECORDED while
  its contra stayed. A replace made this far more likely, because it waited on the account lock (now
  the import lock) inside that window.
- **Why the lock order still holds.** It is line → transaction → statement. A reversal holds the
  row and waits only on client anchors and the account, which a retire takes last or never.
- **Test.** `discard_racingAReversalOfAMatchedRow_waitsAndKeepsTheReversal`. Mutation check: with
  `findById` restored, it fails at "the reversal stands" (the status came back RECORDED).
- **Scope.** The same fix closes this latent race in the existing `unmatch` of a matched line.

The raw file is uploaded to storage last, after every refusal and DB write. Before, it was
uploaded before the DB insert. Now a refused import never leaves an orphaned object; a commit
failure after a successful upload still could, as before.

## Migration

`V30__phase92_bank_statement_retired.sql` (next free tenant version on this head: V29 was the
last). It:
- adds the four columns;
- swaps `chk_bank_stmt_status` to admit `RETIRED`;
- adds `chk_bank_stmt_retired`: who, when and why are set exactly on a RETIRED row, and
  `replaced_by_statement_id` only on a RETIRED row;
- adds FKs `retired_by → members`, `replaced_by_statement_id → bank_statements`.

It is idempotent (`ADD COLUMN IF NOT EXISTS`; every constraint guarded on `pg_constraint` joined
to `pg_namespace` and scoped to `current_schema()`). Existing rows validate as added (none
RETIRED, columns null). No data change. V1 untouched. `backend/db/BASELINE.md` was not edited
(its "next free" line is already stale, see Phase 92 memory; editing `backend/*.md` trips the
merge-gate marker rule, FU-26).

## Tests

Backend: new `BankStatementReplaceDiscardIntegrationTest` (17 tests).
- **Red reproductions (a, b, c):**
  - same-end-date import refused 409 with `existingStatementId` / `code`, nothing stored;
  - replace after auto-match releases A's match, A RETIRED with `matched_count` 0, B's auto-match
    finds the deposit;
  - a step-4 leftover counts in UNRESOLVED_STATEMENT_LINES and fails period-fit, and after the
    discard (HTTP, reason) it counts in neither.
- **Replace:**
  - an identical re-import with replace is accepted; who/when/why and replaced-by are stored;
    lines are kept; both audits written;
  - a draft is re-pointed and recalculated (B's balance, the released deposit outstanding,
    `statement_changed` audit), and a stale calculate on A gets the 409;
  - a replace whose new statement does not fit the draft's period end is refused whole, with
    nothing changed;
  - replace and discard are refused with a RECORDED line, and work after unmatching it;
  - replace and discard of a RECONCILED statement (completed reconciliation, and status-only) are
    refused;
  - a replace naming another account's statement is refused, with nothing changed;
  - (review round 1, D-2) a replace whose new statement clashes with *another* live statement is
    refused 409 `bank_statement.duplicate_end_date_while_replacing`. The detail reads "Statement
    … also ends on 9 March 2026. Discard it from the Reconciliation page first, then upload
    again." There is no `existingStatementId`. A stays live with its match, C is untouched, and the
    new statement is not stored.
- **Discard:**
  - refused while a draft uses it (HTTP ProblemDetail title/detail); a blank reason gives 400;
  - after the draft moves, the discard releases the matches, is audited, and the new statement
    auto-matches the deposit.
- **RETIRED excluded:**
  - hidden from the list, shown with `includeRetired`;
  - refused by create, auto-match, manual match, exclude, record-from-line, and a second discard.
- **Concurrency:**
  - a discard racing a held reversal of a matched row waits on the row lock, and the reversal
    stands (review fix; a mutation check fails without it);
  - a discard racing a held create waits on the statement lock and refuses 409;
  - a create racing a held discard waits on the lock and finds "Statement retired";
  - (review round 1, finding 3) two same-end-date imports of one account: the second waits on the
    import lock while the first is held open, then gets the 409, and exactly one live statement
    exists. Mutation check (`mutation-import-lock.txt`): without the lock, two live statements
    result ("expected 1L but was 2L");
  - a deposit on the account while an import is held open is not blocked. Mutation check: with
    the e5ef682 account row `FOR UPDATE` put back, the deposit waits and the test fails.
  - Mutation check: with create's statement read made a plain `findById`, both race tests fail
    (no wait observed).

Existing tests adapted. Each one imported two statements of one account ending on the same date,
which ruling F-646C-1 now refuses at import. Each is changed only as far as needed:
- `TrustReconciliationServiceMatchingTest` (one shared account, so dates collided across tests by
  run order): `autoMatch_signMismatch…` 05/03 → 06/03 (payment and line);
  `unmatch_clearsBothSides…` 19/03 → 24/03 (deposit and line, manual match);
  `autoMatch_skipsAlreadyMatched…` first, manually matched statement 22/03 → 26/03 (the second
  stays on the deposit's date, so the "already matched" exclusion is still what stops a tier-2
  match); `autoMatch_amountOnlyMultiple…` 22/03 → 29/03 (still no unique close-date candidate,
  still 0.40).
- `AutoMatchLockOrderIntegrationTest`: the second statement's lines are one or two days later.
  Tier 1 matches by reference, so each run wants the same rows; line order is preserved.
- `TrustReconciliationDraftIntegrationTest`:
  - `completeNaming…`: B a day later. The 409 comes from the expected-statement check before any
    period rule.
  - `staleCallDuringContinuation`: B is imported a day later, then its lines are moved onto A's
    date by SQL (`legacySameDay`). This is a same-day pair imported before FU-6b, which existing
    data can still hold, so the "A still fits, only the lock stands between" property is kept.
- `TrustReconciliationServiceReconciliationTest.createReconciliation_acceptsAnotherStatementLine
  OnThisStatementsLastLineDate`: the rule-3 boundary needs a same-day pair, so it uses the same
  legacy seeding (import a day later, then move the line and period by SQL).
- `TrustWireContractTest` snapshot regenerated (`-Dtrust.contract.update=true`, ADR-365):
  `BankStatementResponse` gains `retiredAt`, `retiredBy`, `retiredReason`,
  `replacedByStatementId`. Mirrored in `frontend/lib/types/trust.ts` and
  `frontend/__tests__/trust-wire-shapes.test.ts`.

Frontend (vitest):
- new `__tests__/trust/bank-statement-replace-discard.test.tsx` (12 tests), covering:
  - the action sends `replacesStatementId` (and omits it on a plain upload), and maps a 409
    duplicate to `duplicate`;
  - a replace refusal returns its detail;
  - discard posts the reason and returns a refusal;
  - the upload dialog offers "Replace existing statement" and re-submits with the existing id;
  - cancelling the replace makes no second call;
  - the `replacesStatementId` prop is used;
  - a refusal detail is shown;
  - the Discard dialog requires a reason, posts, refreshes and closes, and on a refusal shows the
    server's detail and stays open.
- `trust-reconciliation.test.tsx` +2:
  - the list page lists unreconciled statements with Discard and hides reconciled ones;
  - the wizard's re-upload passes its statement as `replacesStatementId`.

Evidence: `red-run.txt`, `green-run.txt`.

## Residual risk

- **Legacy duplicates.** Same-end-date pairs imported before FU-6b are not migrated. They still
  both pass rule 3 until one is replaced or discarded; both actions are now available for them.
  The duplicate check only blocks new imports.
- **Import lock.** Two imports of one account are serialised by an advisory lock held to commit.
  Postings are not affected (pinned by a test). An advisory lock is invisible to row-lock tooling;
  the key is documented on `lockImportsOfAccount`.
- **RECONCILED statements count as duplicates.** A new import ending on a reconciled statement's
  date is refused, and replacing a reconciled statement is refused. That is deliberate
  (conservative). A firm that genuinely needs a second statement for an already-reconciled date
  has no path; none is known.
- **Retired detail in the UI.** Retired statements are hidden from the UI. The API exposes them
  (`includeRetired`, the GET by id) for audit. There is no "show retired" toggle (minimal scope).
- **Wizard re-upload that hits a different duplicate: fixed** (review round 1, ruling FU-6b D-2:
  one import retires at most one statement). The backend refuses with
  `bank_statement.duplicate_end_date_while_replacing` and no replace invitation. The upload
  component never offers a second Replace while `replacesStatementId` is set and shows the
  refusal as a plain error. The user discards the clashing statement from the Reconciliation page,
  then uploads again.
- **Discard does not detach drafts.** A draft on a wrong statement must be moved first (new wizard
  run or replace). The refusal says how.
- **Pre-existing test noise.** The frontend `pnpm test` run prints a happy-dom
  `ECONNREFUSED 127.0.0.1:3000` / blob-URL iframe trace from an unrelated PDF-preview test. Exit 0,
  all tests pass. It is not introduced here.

## Defaults chosen (not ruled)

- **D-1. Discard while a DRAFT reconciliation uses the statement: refuse** (not detach). See rule
  4.
- **D-8 (was D-2 before the ruling took that label). "Same end date" = last line date, else `period_end`** (the period-fit definition).
  RECONCILED statements count; RETIRED ones do not.
- **D-2 (ruling, review round 1). One import retires at most one statement.** A replace that
  clashes with another live statement is refused; the user discards that statement first.
- **D-3. Replace does not require the old and new end dates to match.** Any non-retired statement
  of the same account can be replaced, which is what the wizard's corrected re-upload needs. The
  duplicate check excludes only the statement being replaced.
- **D-4. Lists hide RETIRED statements by default** (`includeRetired=true` to show them). The UI
  lists only the live, unreconciled statements, with Discard.
- **D-5. Replace reason is generated** ("Replaced by <file>"); the discard reason is required
  (1–500 chars).
- **D-6. `createReconciliation` share-locks the statement** (`FOR SHARE`), so create and retire are
  serialised.
- **D-7. Storage upload moved after the DB writes**, so a refused import leaves no orphaned object.
