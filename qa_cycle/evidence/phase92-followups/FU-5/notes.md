# FU-5: back-dated deposit reversal is covered from its reversal date on (Phase 92 649B S2)

Register row: `qa_cycle/evidence/phase92-exit/defect-register.md` "649B S2". Sources: `qa_cycle/evidence/phase92/649B/found.md` S2 and `qa_cycle/evidence/phase92/649C/found.md` H-2/H-3. Ruling: `tasks/phase92-decisions.md` row S2 and FU-5. Orchestrator default FU-5 D-1: use the lowest balance from the contra's date on.

## Root cause

A deposit reversal (a credit contra, rule `NEEDS_APPROVAL`) is saved AWAITING_APPROVAL with `transaction_date = reversalDate`. It posts on approval (`performApprovalCompletion` → `postReversalOnApproval`). At approval, `requireClientCovers` read `clientBalance(account, client, null)` and `requireMatterCellCovers` read `matterCellBalance(..., null)`. Both are the current balance over every posted row. Once posted, the contra lowers the balance as at its own date and as at every later date. So a back-dated contra could pass today's check and still leave the client ledger, or the matter cell, in debit as at a date between the reversal date and today. The integrity check flags `CLIENT_LEDGER_IN_DEBIT` only as at the date asked for (649C H-2/H-3), so nothing surfaced it.

Concrete case (the test history): deposit R1 000 on 1 Mar, payment R800 on 10 Mar, deposit R900 on 15 Mar. Today's balance is R1 100. A reversal of the first deposit dated 5 Mar covered "today" and was approved on HEAD. Afterwards the ledger stood at R1 000 − R1 000 = R0 as at 5 Mar, −R800 from 10 to 14 Mar, and R100 from 15 Mar.

## Rule implemented

At approval of a credit contra that takes money off a client (the existing `contraOf(original).ledgerEffect() < 0 && customer != null` branch), if the contra's date is **before today** (`LocalDate.now(clock)`), the client balance and the matter-cell balance must each be at least the amount at their **lowest point from the contra's date on**. That lowest point is min(balance as at the reversal date, running balance as at every later date a posted row is dated). The last point is the balance over every posted row, so this check is never weaker than the old one. It covers the reversal date, today, every date in between, and any later-dated rows already posted.

- If the contra is dated today (or the date is null), the code path is unchanged (`clientBalance(null)` / `matterCellBalance(null)`), and so is the message.
- Refusal: `InvalidStateException`, 400, title "Insufficient trust balance". The detail names the earliest date of the lowest balance and the balance there:
  - client: `Insufficient trust balance for <client> as at 10 March 2026. Available: R200,00, Requested: R1 000,00`
  - matter cell: `Insufficient trust balance on matter <matter> for <client> as at 10 March 2026. Available on matter: R200,00, Requested: R1 000,00`, with the same properties as before (`code` = `INSUFFICIENT_MATTER_TRUST_BALANCE`, `matterId`, `available` = the lowest cell balance, `requested`). No property was added, so the shape is unchanged.
- Date format: the house document date format `VariableFormatter.formatDateText` ("d MMMM yyyy", e.g. "5 March 2026"). This is the pattern trust refusals already use (`UnidentifiedDepositService` RECEIVED, `TrustReconciliationService` PERIOD_END_FORMAT, `IntegrityMessages`). The brief's "5 Mar 2026" was an example, and I followed the house format. Terminology: "client" and "matter" only (ADR-357; `TerminologyRemnantScanTest` green).
- Which date is named: the earliest date the balance stands at its minimum. Its figure is the "Available" amount, so the date and the amount in the message always agree. It is not necessarily the first date the balance falls short, which can be earlier if the balance drops in steps.

## Code

- `TrustLedgerQueryRepository`: `lowestClientBalanceFrom(account, client, from)` and `lowestMatterCellBalanceFrom(account, client, matter, from)`. These are native window queries in the style of FU-9's `lowestCashbookFrom`, sharing one SQL tail (`LOWEST_FROM`). They return the new projection `LowestBalanceRow (asAt, balance)` and always return one row (no history → `(from, 0)`).
- `TrustTransactionService`: `requireClientCovers(transaction, coveredFrom)` and `requireMatterCellCovers(..., coveredFrom)` overloads. A null `coveredFrom` is the old behaviour, and the old signatures delegate with null. Only the credit-contra branch of `performApprovalCompletion` passes a date.
- Locking: unchanged. The client anchor (`postingService.lockClient`) is still taken before either read, after the pending contra row and the original row. That matches the existing order and the global trust lock order in `TrustPostingService`'s Javadoc. No new lock.
- No migration. The queries use the existing columns and `idx_trust_txn_ledger_sum`.

## Callers: changed or not

| Caller | Change | Why |
|---|---|---|
| Credit-contra approval (deposit reversal) | **Changed**: back-dated → lowest from the reversal date | The defect. |
| Payment / refund / fee transfer / s86(4) placement approval (`requireClientCovers(transaction)`) | Unchanged (null) | "Lowest from max(today, date)" is **not** the same as today's `clientBalance(null)`: a future-dated posted inflow counts in the current balance but not in the balance as at today, so switching would tighten these outflows. That is out of scope. Back-dated fee transfers (F-646D-1) are a separate follow-up. |
| Transfer request (`requireMatterCellCovers` + `clientBalance(null)`, ~l.515) | Unchanged | Not a contra and not in scope. |
| `reverseTransferOut` (target's cover, ~l.1745) and `reverseUnidentifiedAllocation` (~l.1838) | Unchanged | These pair contras post at once with a `reversalDate` that may be in the past, and they check the current balance only. That is **the same class of defect** (see residual risk). They are not "on approval", and the ruling names the deposit reversal. One fix per PR. |
| `TrustInvestmentService` (matter cell, `null`) | Unchanged | Not a contra. |

**F-646D-1 (back-dated fee transfers):** the helper makes that fix trivial. At the `REQUIRED` branch call, pass `transaction.getTransactionDate()` when it is before today: `requireClientCovers(transaction, date.isBefore(today) ? date : null)`. A debit dated d lowers every balance from d on, which is exactly what the helper checks, and the refusal message and matter properties come for free. The same one-liner would cover back-dated payments and refunds if that is ruled.

## Tests

`backend/src/test/java/.../trustaccounting/transaction/BackDatedReversalCoverIntegrationTest.java` has one tenant and a fresh account per case, and ends `assertFirmBalanced`. Each refusal goes through `assertApprovalRefused` (nothing moves) or asserts the ProblemDetail directly (matter case).

1. `backDatedReversal_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate`: the history above, refused "as at 10 March 2026", R200.
2. `backDatedReversal_isRefused_whenTheClientWouldBeInDebitAsAtTheReversalDate`: payment on 3 Mar, refused "as at 5 March 2026" (the reversal date itself).
3. `backDatedReversal_isRefused_whenTheMatterCellWouldBeInDebitAsAtALaterDate`: a second matter holds R2 000, so the client covers on every date but the reversed deposit's matter dips to R200 on 10 Mar. Refused by the matter guard with code, matterId, available and requested.
4. `backDatedReversal_isApproved_whenCoveredOnEveryDateFromTheReversalDate`: approved; the as-at balances after 4, 5 and 10 Mar are checked, and the books balance as at the reversal date and today.
5. `reversalDatedToday_isApproved_whenTheClientCoversItToday`: approved as before.
6. `reversalDatedToday_isRefused_withTheUnchangedMessage_whenTheClientCannotCoverIt`: the old message, with no date.

Fixture additions (`TrustBooksFixture`): `deposit(account, client, amount, date)` and `onNewMatter(client)`. The existing `deposit(...)` delegates with `POSTING_DATE`, so it behaves the same.

- **Red** (`red-run.txt`): on unmodified production code at 8c611eb51, tests 1–3 FAIL with "Expecting code to raise a throwable" (the approval succeeded) and 4–6 pass.
- **Green** (`green-run.txt`): 6/6 pass.
- **Tier 2** (`-q`, after `spotless:apply`): every test under `verticals.legal.trustaccounting` (74 classes, which covers the transaction package, payments, refunds, fee transfers, transfers, unidentified allocations, investments, ledger, reports, books and integrity, and includes `TrustPostingRulesTest`), plus every class outside it that uses `TrustLedgerQueryRepository`, `TrustTransactionService` or `TrustBooksFixture` (18 classes), plus `LayerDependencyRulesTest`, `TestConventionsTest` and `TerminologyRemnantScanTest`. Result: **95 classes, 951 test cases, 0 failures, 0 errors, 0 skipped**, Maven exit 0. The full `./mvnw verify` is the orchestrator's.

## Residual risk

- **Same defect class, not fixed here:** `reverseTransferOut` (the target's TRANSFER_IN contra) and `reverseUnidentifiedAllocation` (the client's UNIDENT_ALLOC_IN contra) post at once with a possibly back-dated `reversalDate` and check the current balance only. Back-dated outflows (F-646D-1, and payments and refunds if ruled) are the same. Each is a one-line change on this helper, and each needs its own ruling and PR.
- A contra recorded today and approved on a later day becomes "back-dated" at approval and gets the stricter check. This is intended: it posts on its recorded date.
- The named date is the date of the lowest balance, not necessarily the first date of shortfall (see "Which date is named").
- The rule reads only posted rows. Other pending outflows awaiting approval are not reserved, which is the same as today.
