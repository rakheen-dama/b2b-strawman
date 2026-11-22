# F-646D-1: a back-dated fee transfer is covered from its date on (Phase 92 646D)

Register row: `qa_cycle/evidence/phase92-exit/defect-register.md` "F-646D-1". Source: `qa_cycle/evidence/phase92/646D/found.md` F-646D-1 (orchestrator ruling D8: no rule was added in 646D).

## Ruling

**F-646D-1 D-1 (orchestrator DEFAULT, by analogy with founder ruling S2 / FU-5 D-1).** A fee transfer dated before today must be covered by both the client balance and the matter-cell balance at their **lowest** point from its transaction date on. A fee transfer dated today is checked as before. Other outflow types are not changed.

## Root cause

`recordFeeTransfer` accepts any date that is not in the future (`resolveFeeTransferDate`, ruling #6). The row is saved AWAITING_APPROVAL with `transaction_date` set to that date. It posts on approval (`performApprovalCompletion`, the `Approval.REQUIRED` branch), where `requireClientCovers(transaction)` read the **current** balance (`clientBalance(..., null)` / `matterCellBalance(..., null)`). Once posted, the debit lowers the client's balance, and the matter cell's, as at its own date and every later date. So a fee transfer dated before the deposit it draws on, or one that makes the balance dip on a date between its date and today, passed today's check. It left the client ledger, or the matter cell, in debit as at those past dates. The integrity check flags `CLIENT_LEDGER_IN_DEBIT` only as at the date asked for (649C H-2/H-3). The 650A `BACKDATED_AFTER_LOCK` check and the 650D locked-month warning narrow the window but do not close it.

Reproduced on unmodified code (HEAD 56fd0d3a9; `red-run.txt`): a deposit of R2 000 on 10 March and a fee transfer of R1 150 dated 5 March. The transfer was approved, so the client stood at −R1 150 as at 5 to 9 March.

## Rule implemented

At approval of a `FEE_TRANSFER` whose `transaction_date` is before `LocalDate.now(clock)`, `requireClientCovers` gets `coveredFrom = transaction_date`. This is the FU-5 helper. Both the client balance and the matter-cell balance must be at least the amount at their lowest point from that date on: min(balance as at the date, running balance as at every later date a posted row is dated). The last point is the balance over every posted row, so the check is never weaker than the old one.

- **Dated today** (or later, which `resolveFeeTransferDate` refuses anyway): `coveredFrom = null`. The code path and the message are unchanged.
- **Refusal shape** (the same as FU-5): `InvalidStateException`, 400, title "Insufficient trust balance".
  - Client detail: `Insufficient trust balance for <client> as at 10 March 2026. Available: R500,00, Requested: R1 150,00`.
  - Matter detail: `Insufficient trust balance on matter <matter> for <client> as at 5 March 2026. Available on matter: R0,00, Requested: R1 150,00`.
  - Properties are unchanged: `code` = `INSUFFICIENT_MATTER_TRUST_BALANCE`, `matterId`, `available` (the lowest cell balance) and `requested`.
  - The named date is the earliest date the balance stands at its minimum.
- **Locks are unchanged.** The client anchor (`postingService.lockClient`) is still taken inside `requireClientCovers` before either read. It comes after the fee-transfer row (locked `FOR UPDATE` by `approveTransaction`) and before the account's cashbook lock (`requireCashbookCovers`), as before. No lock was added and none moved; only the read after the anchor differs. The global lock order in `TrustPostingService`'s Javadoc is therefore untouched.
- No migration and no new query.

## Call sites

A fee transfer is cover-checked in exactly one place. I grepped `src/main` for `FEE_TRANSFER`, `recordFeeTransfer`, `FeeTransfer`, `approveTransaction(` and fee-note / billing runs:

| Path | Cover check? | Change |
|---|---|---|
| `recordFeeTransfer` (record path; the controller's only caller) | None. It saves AWAITING_APPROVAL and checks only the date, the fee note and the matter. | None. Conservative choice: no record-time check was added, because the money leaves at approval and the ruling is about the posting. |
| `performApprovalCompletion` → `Approval.REQUIRED` branch → `requireClientCovers` | Yes. This is the only cover check. Single mode (`approveSingleMode`) and the dual-mode second approval (`approveDualMode`) both reach it. A dual-mode first approval checks no cover, as before. | **Changed:** `coveredFrom = transactionDate` when the row is a `FEE_TRANSFER` dated before today. |
| Batch / fee-note-run path | None exists. Billing runs create fee notes, not trust fee transfers. No other `src/main` code builds a `FEE_TRANSFER` row or calls `recordFeeTransfer` / `approveTransaction`. | n/a |

Tidy-up: the one-argument `requireClientCovers(TrustTransaction)` overload had no caller left, so I removed it and folded its Javadoc into the two-argument method. `resolveFeeTransferDate`'s Javadoc now points to the approval-time rule.

## Other outflows with the same gap (NOT changed; each needs its own ruling and PR)

| Outflow | Date source | Cover check today | Same gap? |
|---|---|---|---|
| `PAYMENT` (`recordPayment` → approval) | `request.transactionDate()`, any past date | `requireClientCovers(…, null)`: current balance only | **Yes.** It is a one-line change at the same call site (drop the `FEE_TRANSFER` condition). |
| `REFUND` (`recordRefund` → approval) | `request.transactionDate()` | same | **Yes**, as for payments. |
| `INV_PLACE` (section 86(4) placement, `TrustInvestmentService`) | `request.depositDate()` | `matterCellBalance(…, null)` at request, then `requireClientCovers(…, null)` at approval | **Yes**, at both points. |
| `TRANSFER_OUT` (`recordTransfer`, posts at once) | `request.transactionDate()` | `clientBalance(source, null)` + `requireMatterCellCovers(…)` (null) | **Yes**, for the source client and its matter. |
| `reverseTransferOut` / `reverseUnidentifiedAllocation` (pair contras) | `reversalDate` | current balance only | **Yes**, already listed in FU-5's residual risk. |
| Cashbook (`requireCashbookCovers`, FU-9) for every covered outflow, this one included | n/a | lowest cashbook from `max(today, date)` | Partly. A back-dated outflow is not checked against the cashbook as at its own past dates. With every client ledger now covered from the date on, the cashbook can dip only if a non-client ledger is negative. This was left as FU-9 designed it. |

## Tests

New `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/BackDatedFeeTransferCoverIntegrationTest.java`. It has one tenant, a fresh account per case and the fixture's R1 150,00 fee note, and it ends with `assertFirmBalanced`.

1. `backDatedFeeTransfer_isRefused_whenDatedBeforeTheDepositItDrawsOn`: the only deposit is on 10 March and the transfer is dated 5 March. Refused "as at 5 March 2026", R0,00; nothing moves.
2. `backDatedFeeTransfer_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate`: deposit R1 500 on 1 March, payment R1 000 on 10 March, deposit R1 000 on 15 March, transfer dated 5 March. Refused "as at 10 March 2026", R500,00.
3. `backDatedFeeTransfer_isRefused_whenTheMatterCellIsEmptyAsAtItsDate`: the other matter holds R2 000 from 1 March, so the client covers. The fee note's matter is funded only on 10 March. Refused by the matter guard with code, matterId, available 0 and requested 1150.
4. `backDatedFeeTransfer_isRefused_whenTheMatterCellWouldBeInDebitAsAtALaterDate`: the other matter holds R2 000, and the fee matter has the history from case 2. Refused by the matter guard "as at 10 March 2026", available 500.
5. `backDatedFeeTransfer_isApproved_whenCoveredOnEveryDateFromItsDate`: the lowest balance from 5 March is R1 500. Approved, and the cashbook and client move by −R1 150. The as-at balances before, on and after the date are checked, and the books balance as at the date and today.
6. `feeTransferDatedToday_isApproved_whenTheClientCoversItToday`: the history from case 2, with the transfer dated today. Approved as before.
7. `feeTransferDatedToday_isRefused_withTheUnchangedMessage_whenTheClientCannotCoverIt`: today's message, with no date.

Fixture: `TrustBooksFixture.requestFeeTransfer(account, client, transactionDate)`. The existing two-argument method delegates with `null` (today), so it behaves the same.

- **Red** (`red-run.txt`): on unmodified production code, cases 1–4 FAIL with "Expecting code to raise a throwable" (the approval succeeded); cases 5–7 pass. My first red attempt also failed case 7, because the test typed a plain space where the rand format uses U+00A0. That was a test-text error, fixed before the recorded red run, and not a product change.
- **Green** (`green-run.txt`): 7/7.
- **Tier 2** (`tier2-run.txt`, `-q`, after `spotless:apply`): it covers the whole `trustaccounting/transaction` test package (17 classes), the whole `architecture` package (7 classes), and every test class that mentions fee transfers (`FEE_TRANSFER`, `recordFeeTransfer`, `requestFeeTransfer`, `TrustBooksFixture`, "fee transfer", `trust_fee_transfer`, `TRUST_TRANSFER`), which is 58 classes overlapping the package. Result: **70 classes, 1 054 test cases, 0 failures, 0 errors, 0 skipped**, Maven exit 0. Every requested class has a report. The full `./mvnw verify` is the orchestrator's.

## Residual risk

- A fee transfer recorded today and approved on a later day is "back-dated" at approval and gets the stricter check. This is intended (the same as FU-5): it posts on its recorded date.
- Pending outflows awaiting approval are not reserved; only posted rows count, as before.
- The named date is the date of the lowest balance, which is not necessarily the first date of shortfall.
- The UI shows the refusal on approval only. The record dialog does not warn that a back-dated transfer will be refused, so the frontend was not touched (backend-only fix).
- The same gap stays open for payments, refunds, section 86(4) placements and transfers (table above).
