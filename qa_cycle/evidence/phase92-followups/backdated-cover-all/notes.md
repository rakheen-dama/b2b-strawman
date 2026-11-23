# D-1: every back-dated client outflow is covered from its date on (Phase 92 residuals §1.1 / §1.2)

Ruling: founder D-1 (2026-11-23), extending S2 / FU-5 D-1 (deposit reversals) and F-646D-1 D-1 (fee transfers) to every client-ledger outflow. Source: `tasks/trust-accounting-open-issues-2026-11-23.md` §1.1, §1.2, §2 D-1.

## Rule

A client-ledger outflow dated before today (`LocalDate.now(clock)`) must be covered by the client balance **and** the matter-cell balance at their lowest point from the outflow's date on (`TrustLedgerQueryRepository.lowestClientBalanceFrom` / `lowestMatterCellBalanceFrom`, FU-5's helpers). An outflow dated today (or later) keeps today's check and today's message. The refusal shape is FU-5's: 400 "Insufficient trust balance", the detail naming " as at <d MMMM yyyy>" (the earliest date of the lowest balance) and that balance; a matter refusal keeps its properties (`code` `INSUFFICIENT_MATTER_TRUST_BALANCE`, `matterId`, `available`, `requested`).

## Code (`TrustTransactionService`, `TrustInvestmentService`)

- `coveredFromIfBackDated(date)` — the date when it is before today, else null. One rule for every site.
- `requireClientBalanceCovers(account, client, name, amount, coveredFrom)` — the client-balance read + refusal, shared by `requireClientCovers` (approval), `recordTransfer`, `reverseTransferOut` and `reverseUnidentifiedAllocation` (they had four copies of the same check). `requireMatterCellCovers` now always takes `coveredFrom`; its unused 5-argument overload was removed.
- No migration, no new query, no new lock.

## Outflows

| Outflow | Where checked | Date | Before | Now |
|---|---|---|---|---|
| `PAYMENT` | approval (`performApprovalCompletion`, `REQUIRED` branch) | `transaction_date` | current only | from its date on |
| `REFUND` | approval (same) | `transaction_date` | current only | from its date on |
| `FEE_TRANSFER` | approval (same) | `transaction_date` | from its date on (F-646D-1) | unchanged — the `FEE_TRANSFER`-only condition is generalised to every CLIENT-ledger `REQUIRED` type |
| `INV_PLACE` (s86(4)) | approval (same) | `deposit_date` (the row's date) | current only | from its date on |
| `INV_PLACE` (s86(4)) | request (`placeInvestment`, matter cell) | `depositDate` | current cell | lowest cell from `depositDate` on; message names the date |
| `TRANSFER_OUT` | `recordTransfer` (posts at once) | `transactionDate` | `clientBalance(null)` + cell(null) | source client and its matter cell from the date on |
| contra of `TRANSFER_IN` | `reverseTransferOut` (posts at once) | `reversalDate` | current only | receiving client and its cell from the reversal date on |
| contra of `UNIDENT_ALLOC_IN` | `reverseUnidentifiedAllocation` (posts at once) | `reversalDate` | current only | client and its cell from the reversal date on |
| contra of `DEPOSIT` | approval (credit-contra branch) | `reversalDate` | from its date on (FU-5) | unchanged (same helper) |

Every outflow above was searched for and listed by grepping `src/main` for every `new TrustTransaction(` and every `clientBalance(` / `matterCellBalance(` / `requireClientCovers` / `requireMatterCellCovers` call.

### Client debits checked and left unchanged (with reason)

| Row | Why no change |
|---|---|
| `INV_WITHDRAW` (client investment ledger) | Not a client-ledger outflow; its amount is the investment's whole balance and its date may not be before the deposit date or the last posted interest entry (`withdrawInvestment`), so the investment ledger cannot dip as at a past date. The paired `INV_RETURN` is a client inflow. |
| contra of `INV_INTEREST` (`reverseInterest`, unmatch) | Dated the entry's own date, so the pair nets to zero from that date — no as-at balance moves. |
| contras of `PAYMENT` / `REFUND` / `FEE_TRANSFER` (`IMMEDIATE`) | Client **inflows** (they credit the client back). |
| `TRANSFER_IN`, `UNIDENT_ALLOC_IN` direct reversal | Refused (`VIA_PAIR`): reversed only through their pair, covered above. |
| `UNIDENT_ALLOC_OUT`, `UNIDENT_FUND_PAY`, `FUND_*`, `BANK_CHARGE`, statement-line rows | `NON_CLIENT` ledger: no client balance. |
| `INV_PLACE`'s cashbook, every covered outflow's cashbook (FU-9 `requireCashbookCovers`) | Unchanged by instruction (lowest cashbook from `max(today, date)`). |
| `LegalDemoDataSeeder` | Posts straight through `TrustPostingService` on purpose (it leaves a client in debit, §16.3.15). |

## Locks

Unchanged. Every read still follows the client's lock anchor, which still precedes the account rows, so the global order in `TrustPostingService`'s Javadoc holds: approval — the row (caller), [placement / resolution hooks], the client anchor inside `requireClientCovers`, then the cashbook check's account lock; `recordTransfer` / `reverseTransferOut` — both anchors in ascending UUID order, then the reads; `reverseUnidentifiedAllocation` — the ALLOC_OUT, the item, the ALLOC_IN, the anchor, then the reads; `placeInvestment` — the anchor, the funding account, the investment account, then the read. Only the query after the anchor differs.

## Tests

New `backend/src/test/java/.../trustaccounting/transaction/BackDatedOutflowCoverIntegrationTest.java` (12): one tenant, a fresh account per case, `assertFirmBalanced` at the end. History "dip": R1 500 (1 Mar), payment R1 000 (10 Mar), R1 000 (15 Mar) → R1 500 today, R500 as at 10–14 March.

| Case | Expectation |
|---|---|
| `backDatedPayment_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate` | refused "as at 10 March 2026", R500 |
| `backDatedPayment_isRefused_whenTheMatterCellWouldBeInDebitAsAtALaterDate` | matter guard, properties |
| `backDatedRefund_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate` | refused, 10 March |
| `backDatedTransfer_isRefused_whenTheSourceWouldBeInDebitAsAtALaterDate` | refused at record; target unchanged |
| `backDatedTransfer_isRefused_whenTheSourceMatterCellWouldBeInDebitAsAtALaterDate` | matter guard |
| `backDatedPlacementRequest_isRefused_whenTheMatterCellIsEmptyAsAtItsDate` | request refused "as at 5 March 2026", R0 |
| `backDatedPlacementApproval_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate` | approval refused, 10 March; nothing on either account |
| `backDatedTransferReversal_isRefused_whenTheTargetWouldBeInDebitAsAtALaterDate` | refused "as at 10 March 2026", R100 |
| `backDatedAllocationReversal_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate` | refused, R100; allocation stays APPROVED |
| `backDatedPayment_isApproved_whenCoveredOnEveryDateFromItsDate` | approved, posts on 5 March; as-at balances checked |
| `paymentDatedToday_isApproved_whenTheClientCoversItToday` | as before |
| `paymentDatedToday_isRefused_withTheUnchangedMessage_whenTheClientCannotCoverIt` | today's message, no date |

Fixture: `TrustBooksFixture.requestRefund(…, transactionDate)` and `requestPlacement(…, depositDate)`; the existing signatures delegate with `POSTING_DATE`, so they behave the same.

Changed expectation: `TrustInvestmentServiceIntegrationTest.placement_mustBeCovered_byTheMatterCell_orByTheCashbook` — its placement is dated 1 March (back-dated), so the request refusal now names " as at 1 March 2026". This is the ruled refusal shape, not a regression.

- **Red** (`red-run.txt`): on unmodified production code (main `ef0563fe3`), the 9 refusal cases FAIL with "Expecting code to raise a throwable"; the 3 controls pass.
- **Green**: 12/12 (targeted, and in Tier 2).
- **Tier 2**: `tier2-run.txt`.

## Residual risk

- An outflow recorded today and approved on a later day is back-dated at approval and gets the stricter check — intended (it posts on its recorded date), as FU-5 / F-646D-1.
- Pending outflows awaiting approval are not reserved; only posted rows count, as before.
- The placement **request** checks the matter cell only (as before); the client total is checked at approval.
- The named date is the date of the lowest balance, not necessarily the first date of shortfall (FU-5).
- A back-dated outflow is still not checked against the cashbook as at its own past dates (FU-9 as designed); with every client ledger now covered from the date on, the cashbook can dip only through a non-client ledger.
- The dialogs still accept a past date and show the refusal only on approval / record; no frontend change for D-1.
