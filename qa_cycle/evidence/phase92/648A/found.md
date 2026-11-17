# 648A — found while building and in review (F-1/F-2 reported only; F-3/F-4/F-5 fixed in this slice; F-6 OPEN — founder ruling)

## F-1 (hand-off to 648E, already planned): the client ledger card's totals do not count investment rows

`ClientLedgerService.ClientLedgerCardResponse.balance` is `TrustLedgerQueryRepository.clientBalance`
(every posted `CLIENT`-ledger row), but its totals come from `LedgerTotals` (deposits, transfers
in/out, payments, fee transfers, refunds). `INV_PLACE` and `INV_RETURN` are `CLIENT`-ledger rows
that no total names, so once a section 86(4) placement is approved the G89-F13 comment on the
record ("deposits + transfers in - payments - fee transfers - refunds - transfers out == balance")
no longer holds for that client: the balance falls by the principal and no total moves.

- Repro: place and approve a R30 000 s86(4) investment for QuickCollect (day-60 E2E does this). The card's
  `balance` is R15 000; deposits R45 000 and every other total R0.
- Owner: 648E (client-ledger card / `LedgerTotals` / "Invested (section 86(4))" figure). The brief
  marks these files "do not touch" for 648A. No backend test pins the invariant for investment rows
  today (Tier 2 is green), so nothing here goes red; 648E should add the test.

## F-2 (pre-existing, unrelated): `trust-transactions.test.tsx` makes real HTTP calls to :8080

The vitest file logs ~90 `connect ECONNREFUSED ::1:8080 / 127.0.0.1:8080` `AggregateError`s on the
base commit (`8ca0441ba`, measured with this slice's changes stashed) as well as with them; its tests
pass. Something the transactions page renders reaches the real API client (an unmocked fetch). Noise
only; it does not fail the suite. Out of slice.

## OPEN-14 checked: the day-60 cashbook covers the R200 000 section 86(3) placement

Before day 60 the lifecycle posts Moroka R250 000 (day 7–14) and QuickCollect R45 000 (day 7–14)
and approves Moroka's R8 500 fee transfer (day 30); day 45 records no trust row. Cashbook on day 60
= R286 500 ≥ R200 000, so the scenario figures stand unchanged. After both placements are approved
the banking account's cashbook is R56 500 (R286 500 − R200 000 − R30 000); no later lifecycle day
asserts a cashbook or a client-ledger figure (day 75 has no trust step; day 90 checks the ledger
rows are present, the register's bases, and records a pending R500 Sipho fee transfer).

## F-3 (accepted ruling OPEN-3, re-raised by the automated security review): s86(3) cover is checked at request only

Status: **fixed in 648A (review cycles 1 and 2)** — orchestrator ruling: OPEN-3 reversed.
Reproduced first (`red-backend-review-f3.txt`: the second approval posted). Fix: at the final
approval of a placement, `TrustTransactionService` dispatches to
`InvestmentPlacementHooks.onPlacementApproving` (review cycle 2: moved behind the hook —
`TrustInvestmentService` owns the funding-account lock, the cashbook re-check and the refusal
wording once, shared with the request). For a `FIRM_INV_PLACE` it locks the funding account row
and refuses unless `cashbook(G, today) >= amount` with the request's own refusal ("Insufficient
trust balance" / "The trust account's cashbook does not cover this section 86(3) placement. …").
`placeInvestment` takes the funding account's lock too (funding, then investment account), so the
request's read and the approval's re-read are serialised. Approval lock order: placement row →
funding account (hook, s86(3)) → client anchor (s86(4), `requireClientCovers`) → investment row.
Pinned by
`TrustInvestmentServiceIntegrationTest.section86_3_cover_isCheckedAgainAtApproval_soTwoPendingPlacementsCannotOverdrawTheCashbook`
(sequential: two pending R3 000 placements against R5 000: the first approves, the second is
refused with `assertMoves` none on both accounts, stays AWAITING_APPROVAL) and, review cycle 2,
`TrustInvestmentServiceIntegrationTest.twoSection86_3_approvalsAtOnce_secondWaitsOnTheFundingAccountLock_andIsRefused`
(concurrent: the first approval's transaction is held open after posting; the second is observed
in `pg_stat_activity` blocked by the first's backend on a trust_accounts query, then refused once
the first commits; exactly one placement posts; red without the lock —
`red-backend-review2-concurrent.txt`). The class ends `assertFirmBalanced`. The cashbook exposure
that remains through **client** debits is F-6 below (it replaces the earlier "Residual" note here,
which named only the concurrent case).

Original text (as found):

`placeInvestment` refuses a section 86(3) placement when `cashbook(G, today) < principal`, but
`FIRM_INV_PLACE` is an `INTER_ACCOUNT` row, so approval (`TrustTransactionService`, which re-checks
cover only for `CLIENT`-ledger types) does not re-check it, and the request check does not deduct
other s86(3) placements still awaiting approval. Two pending R200 000 placements against a
R286 500 cashbook both pass the request check; approving both overdraws the banking account's
cashbook (−R113 500). This is the OPEN-3 default the orchestrator accepted ("check at request
only"); it is flagged here and in the PR for a decision. Suggested follow-up: at approval of a
`FIRM_INV_PLACE`, under the account's lock, re-check `cashbook(G, max(today, date)) ≥ amount`.

## F-6 (review cycle 2, Reviewer A HIGH): a client debit after a section 86(3) placement can take the cashbook into debit

Status: **OPEN — founder ruling required (sufficiency model, §16 line 628 / R14 / ADR-380)**.
**Owner: 649C.** Orchestrator ruling (b): not changed in 648A; the current behaviour is pinned.

A section 86(3) placement lowers the banking account's cashbook and leaves every client ledger as it
was — the `INTER_ACCOUNT` ledger carries the offset. A client payment, refund or fee transfer is
checked at record and again at approval (`TrustTransactionService.requireClientCovers`, under the
client's lock anchor) against the client's ledger and matter cell only, never the account's
cashbook, so:

- **Sequential case (no race needed):** G's cashbook R5 000, all client A's → a R3 000 s86(3)
  placement is approved (cashbook R2 000; A's ledger still R5 000) → a R4 000 payment for A passes
  `requireClientCovers` and posts → G's cashbook is **−R2 000**, A's ledger R1 000, G's
  inter-account term −R3 000. EQ-2 on G (−2 000 = 1 000 + (−3 000)) and EQ-3 firm-wide still hold
  because the inter-account term cancels against the investment account's, so `assertFirmBalanced`
  does not detect it.
- **Concurrent variant:** a client debit's approval locks the transaction row then the client's
  lock anchor, never the account row, so it is not serialised with an s86(3) approval (which locks
  the funding account). Each re-reads its own cover — the placement the cashbook (which excludes
  the pending payment), the payment the client ledger (which the placement does not touch) — so
  both post even when the cashbook covers only one of them. With F-6's model the same outcome is
  reachable sequentially anyway; the concurrent case adds no new end state.

Why not fixed here: the Phase 92 architecture fixes the sufficiency-check model — §16 (~line 628)
"Sufficiency checks stay where they are (record time and approval time, under the client card's
row lock) and read the derived balance … Pending debits are not reserved, as today", founder
decision R14 "no hard block on posting", ADR-380 "never a block on recording a transaction". A
cashbook block on every client-ledger debit changes that model and is a founder call.

Options for the founder:

- **(b, proposed — 649C):** an integrity flag "trust banking account cashbook in debit"
  (warning-level, alongside `CLIENT_LEDGER_IN_DEBIT`), raised when a GENERAL account's derived
  cashbook as at a date is below zero. No posting is blocked; the condition is surfaced.
- **(a, alternative):** at every `CLIENT`-ledger debit approval (PAYMENT, REFUND, FEE_TRANSFER,
  INV_PLACE, and a debit contra), lock the trust account row and require `cashbook(account, today) ≥
  amount` in addition to the client/matter cover. Lock-order constraint: the account lock must be
  taken **before** the client anchor — `placeInvestment` locks funding account → investment
  account → client anchor, and the s86(3) approval locks placement row → funding account; a
  payment approval today takes transaction row → client anchor, so (a) must become transaction row
  → account → client anchor (never anchor then account, or an s86(4) placement request and a
  payment approval for the same client can deadlock). This also serialises every client debit on
  the account with every other, a throughput cost on a busy banking account.

Pinned by
`TrustInvestmentServiceIntegrationTest.clientPaymentAfterASection86_3Placement_isCheckedAgainstTheClientLedgerOnly_ruling648A_F6`
(the sequential case above: the payment posts, `assertMoves` shows cashbook and client −R4 000
exactly, G's cashbook is −R2 000, client R1 000, inter-account −R3 000, `assertBalancedAsAt` on
both accounts and `assertFirmBalanced` hold). Whichever option the founder picks, that test is the
one to flip.

## F-4 (pre-existing guard, newly reachable): an investment account can be closed while it holds investments

Status: **fixed in 648A (review cycle 1)** — reproduced first (`red-backend-review-f4.txt`: both
closes succeeded). Fix: `TrustAccountService.closeTrustAccount` (under the account's lock) also
refuses while an investment placed from **or** held in the account is ACTIVE/MATURED
(`TrustInvestmentRepository.existsByFundingOrInvestmentAccountAndStatusIn` — a placement awaiting
approval is ACTIVE, so it counts; this also covers CodeRabbit's funding-account point on this
file) and on a non-zero cashbook ("Non-zero cashbook balance of R… must be resolved first.");
`withdrawInvestment` refuses unless both accounts are ACTIVE. Pinned by
`TrustAccountServiceTest.closeTrustAccount_isRefused_whileItHoldsOrFundsAnActiveOrMaturedInvestment`,
`TrustAccountServiceTest.closeTrustAccount_isAllowed_whenItsInvestmentsAreWithdrawnOrCancelled`,
`TrustInvestmentServiceIntegrationTest.investmentAccount_isNotClosed_whileItHoldsTheInvestment_andWithdrawalNeedsBothActive`
(pending and approved placement both block; withdrawal refused on a closed account with nothing
moved; close succeeds after the withdrawal) and
`TrustInvestmentServiceIntegrationTest.account_isNotClosed_whileItsCashbookIsNotZero`.

Original text (as found):

`TrustAccountService.closeTrustAccount` refuses only a non-zero **client-ledger** total
(`clientLedgerTotal`, `ledger = 'CLIENT'`). An `INVESTMENT` account's rows are `CLIENT_INVESTMENT`
(s86(4)) or `INTER_ACCOUNT` (s86(3)), so its client-ledger total is always zero: it can be closed
with R200 000 invested, and the later withdrawal then posts `INV_WITHDRAW` / `FIRM_INV_WITHDRAW`
on the closed account (`withdrawInvestment` does not re-check either account's status; the
placement does). Likewise a funding account whose client ledgers are zero can be closed while a
section 86(3) investment funded from it is outstanding. Not fixed here (the close guard is outside
648.3–648.6, and refusing the withdrawal alone would strand the money). Suggested fix: refuse
`closeTrustAccount` while `existsByInvestmentAccountIdAndStatusIn(id, ACTIVE/MATURED)` or the
account's cashbook is non-zero, and refuse a withdrawal unless both accounts are `ACTIVE`.

## F-5 (found by the orchestrator's E2E run on 2accb1674, fixed in 648A review cycle 1): "Create Account" outside the viewport

Status: **found and fixed**. `day-60-interest.spec.ts:374` ("Place investment — R200,000 …
(s86(3), names no client)") failed: the stacked "Add Investment Account" dialog
(`CreateTrustAccountDialog`, opened from `PlaceInvestmentDialog`) is taller than the 720-px test
viewport since 648C added the basis select and its help text, and its content did not scroll, so
Playwright reported "Create Account" outside of the viewport (screenshot in the orchestrator's run
artefacts). Fix in the product: the dialog content takes the repo's pattern `max-h-[90vh]
overflow-y-auto` (as `PlaceInvestmentDialog` and `RecordTransferDialog`). Pinned by the vitest
`trust-investment-basis.test.tsx` › "the account dialog's content scrolls within the viewport"
(red without the classes: `red-frontend-review-dialog-scroll.txt`). The E2E re-run is the
orchestrator's.

## Review cycle 1 — deferred to 648B (reported only)

- **Recorded interest is not on the books** (CodeRabbit, `TrustInvestmentService.java:464`):
  `PUT /api/trust-investments/{id}/interest` still only adds to `interestEarned` (OPEN-2, kept until
  648B), so a withdrawal returns the ledger balance without it. 648B replaces the endpoint with
  posted `INV_INTEREST` / `FUND_INV_SHARE` rows; the withdraw dialog no longer promises that
  recorded interest returns.
- **Withdrawal date vs later investment entries** (CodeRabbit, `TrustLedgerQueryRepository.java:405`):
  `investmentBalance` has no as-at bound. Unreachable in 648A — an investment's only rows before
  its withdrawal are the receive leg dated the deposit date, and the withdrawal date is refused
  before the deposit date — but once 648B posts interest rows a withdrawal dated before one would
  return it early. 648B must pick the policy (refuse a withdrawal dated before the investment's
  last posted entry, or withdraw the as-at balance).

