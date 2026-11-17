# 647B — found (outside the slice's scope, or a call made in the slice)

Slice: 647B — Unidentified deposits (hold). Branch `epic-647B/unidentified-deposits-hold`, base `main` 7853b7a4f.

## F-1 — `openItems` on the creditor-accounts response is not built (Spec vs code #4, ruling R4)

§16.4.2's example shows `"openItems": 1` on the `UNIDENTIFIED_DEPOSITS` entry of
`GET /api/trust-accounts/{id}/creditor-accounts`. `TrustCreditorLedgerService.TrustCreditorAccountResponse`
has no such field and no 647 task lists it, so 647B does not add it (scope discipline; it would also change a
pinned-adjacent wire record). The count is derivable today from
`GET /api/trust-accounts/{id}/unidentified-deposits?status=HELD` (`page.totalElements`).
**Orchestrator call:** add it in 647D (with the resolve UI) or 649C (integrity / age flag), or drop it from the spec.

## F-2 — direct `UNIDENTIFIED_DEPOSIT` test rows now carry an anchor statement (Spec vs code #5, ruling R5)

V21's `chk_trust_txn_unidentified_item` refuses an `UNIDENTIFIED_DEPOSIT` row without an item, so
`TrustBooksFixture.postNonClientDirect(account, "UNIDENTIFIED_DEPOSIT", …)` now delegates to
`unidentifiedDepositDirect`, which writes the recorded shape: its own statement with one line `RECORDED` as
`UNIDENTIFIED_DEPOSIT`, a `HELD` item, the row naming item and line, `matched_count = 1`. Consequence for
future tests: such an account carries an extra statement dated the row's date, so a reconciliation on that
account whose own statement ends before that date is refused by the "another imported statement with
movements between …" period rule. `TrustPositionAsAtIntegrationTest.reconciliation_storesAndReturnsEveryCreditorTermInItsOwnComponent`
hit exactly this and now records its unidentified deposit from its own CSV (bank 1 400.00, o/s deposits 950.00,
everything else unchanged, difference R100). The three other callers (dashboard position, balance trend, MCP
catalogue) do not reconcile and pass unchanged.

## F-3 — the Fund-ledger pager was extracted (no behaviour change)

`other-creditors/page.tsx` now pages two lists. The Fund ledger keeps `?page=`; the items page on `?itemsPage=`.
The previously inline Prev/Next markup was extracted into a local `LedgerPager` used by both lists (same
markup). A Fund-ledger link now also carries `&itemsPage=N` when the items are not on their first page, so
paging one list keeps the other's place; with `itemsPage = 0` the hrefs are byte-identical to before
(`?page=N`, pinned by the existing vitest case).

## Not newly observed (recorded elsewhere)

- A future-dated line can be recorded but not unmatched until its date (647A found.md #3 / 647C found.md #4) —
  applies equally to an unidentified deposit; not re-tested here.

## F-4 — a future-dated HELD item breaks harness EQ-4 rule (d) (orchestrator, from the re-review @563ef6185)

The review fix clamped `ageDays` to ≥ 0, so a future-dated item lists with age 0 and no one-year flag. Such an item
is HELD from recording but has no balance as at today, so `TrustBooksHarness.assertItemised` rule (d)
("as at today, an item is HELD exactly when it is open") would fail for it. No test records one, so nothing is red.
This is not a 647B defect: it follows from 647A found.md #3 / 647C found.md #4 (a future-dated statement line can
be recorded). Resolve it with that same product/legal call: either refuse future-dated lines at record time, or
evaluate rule (d) as at `max(today, received_date)`.
