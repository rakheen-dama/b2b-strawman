# 648A — found while building (reported, not fixed in this slice)

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

Status: from code reading (the request-only check is pinned by the 648A tests; the double-approval
overdraft itself is not reproduced by a test in this slice).

`placeInvestment` refuses a section 86(3) placement when `cashbook(G, today) < principal`, but
`FIRM_INV_PLACE` is an `INTER_ACCOUNT` row, so approval (`TrustTransactionService`, which re-checks
cover only for `CLIENT`-ledger types) does not re-check it, and the request check does not deduct
other s86(3) placements still awaiting approval. Two pending R200 000 placements against a
R286 500 cashbook both pass the request check; approving both overdraws the banking account's
cashbook (−R113 500). This is the OPEN-3 default the orchestrator accepted ("check at request
only"); it is flagged here and in the PR for a decision. Suggested follow-up: at approval of a
`FIRM_INV_PLACE`, under the account's lock, re-check `cashbook(G, max(today, date)) ≥ amount`.

## F-4 (pre-existing guard, newly reachable): an investment account can be closed while it holds investments

Status: hypothesis from code reading — not reproduced by a test in this slice.

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
