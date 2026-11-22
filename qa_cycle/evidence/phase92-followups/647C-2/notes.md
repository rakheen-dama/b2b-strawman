# 647C #2 / 649B F2: the client-ledger `/history` running balance leaves out reversed pairs

Register row: `qa_cycle/evidence/phase92-exit/defect-register.md`, "647C #2 / 649B F2" (HIGH if reproduced).
Sources: `qa_cycle/evidence/phase92/647C/found.md` #2, `649B/found.md` F2, `652H/found.md` "Not touched".

**Verdict: REPRODUCED → FIXED in this PR.**

## Red, on `origin/main` @ `34f45c07170ea48f471c67df08cb9e916979d957`

The full output is in `red-run.txt`. Two new tests in `ClientLedgerRunningBalanceIntegrationTest` post every row
through `TrustTransactionService`, which is the UI path. Each test then asserts that every posted row's
`runningBalance` equals `clientBalance(account, client, asAt = row date)`.

| Case | Rows (oldest first) | Shown on main | `clientBalance(asAt)` |
|---|---|---|---|
| Payment reversed by a later contra | deposit 1 000 (d-10), payment 400 (d-6, approved), deposit 100 (d-4), contra (d-2) | null, **1 100 on the un-reversed d-4 deposit**, null | 600 at d-6, **700 at d-4**, 1 100 at d-2 |
| Deposit reversed by a later contra (the approval path) | deposit 1 000 (d-10), deposit 300 (d-8), payment 100 (d-6, approved), contra (d-2, approved) | null, **900 on the un-reversed d-6 payment**, null | 1 300 at d-8, **1 200 at d-6**, 900 at d-2 |

So the defect is not only the "—" on the two halves of the pair. An ordinary row between the original's
date and the contra's date shows a wrong balance on the firm's client ledger.

While the deposit-reversal contra awaits approval, the history is right. The original is still RECORDED,
and the contra is unposted with a null balance. The test asserts this state too.

## Cause

`TrustTransactionRepository.findClientHistoryWithRunningBalance`'s window CTE still used the predicate from
before 649B: `status IN ('RECORDED','APPROVED') AND transaction_type <> 'REVERSAL'`. Under the contra model
(ADR-378), a reversed original keeps its effect on its own date, and the contra carries the negation on its
own date. `clientBalance` sums every posted row (`posted_at IS NOT NULL`).

## Fix

The window runs over `posted_at IS NOT NULL`, which is a straight cumulative `SUM(ledger_effect)` in posting
order. Every other Phase 92 reader already uses this rule:
- the creditor ledger, `TrustLedgerQueryRepository.creditorLedgerRows`;
- Receipts & Payments, `findForReceiptsPayments` (FU-13, PR #1824);
- the client-ledger statement, `findForStatement`.

Unposted rows (awaiting approval, rejected) keep a null balance. The CTE stays `MATERIALIZED`, and the
EXPLAIN plan test is still green.

**Visible change.** On the firm client-ledger page, a REVERSED original and its REVERSAL contra now show a
running balance instead of "—". The 647C note (`647C/found.md` #2) said this change "needs its own
ruling". The orchestrator brief for this follow-up authorised this fix: "history should include both
halves on their own dates". It is flagged here so the founder can sign off.

**Unchanged:**
- The firm-facing type labels.
- `direction`.
- The filters, which still bind after the window, so they never change a balance.
- The page order.

**Client surfaces are not touched:**
- The portal trust ledger is a separate projection (`TrustLedgerPortalSyncService`) and does not read `/history`.
- The reversal reason still reaches client surfaces only through `TrustTransactionTypeLabels.clientDescription`.

The frontend renders the server value or "—" for null (`client-ledgers/[clientId]/page.tsx`). It does not
depend on reversed rows being null. Only three comments changed: the page, `lib/types/trust.ts`, and a
vitest comment.

## Tests changed

- New tests:
  - `history_paymentReversedByALaterContra_everyPostedRowIsTheAsAtClientBalance`
  - `history_depositReversedByALaterApprovedContra_everyPostedRowIsTheAsAtClientBalance`
- `history_runningBalance_matchesBalanceAsOfAndStatement_andNonPostedRowsAreNull`: the old pins were null on the
  REVERSED payment and on its contra. The new pins are 8 300 for the payment (d-2), 7 150 for the fee
  transfer, and 7 850 for the contra.
  - The newest posted row is now the contra.
  - It still equals `clientBalance(today)` and the statement's closing balance.
  - The oldest → newest walk now signs each posted row by its `direction`. A payment's contra is IN.
