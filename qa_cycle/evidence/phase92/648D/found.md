# 648D — found outside the slice (reported, not fixed)

## F-1 (carried 648E C-3 / 648A re-gate N-2; OQ-6 → (b)) — "section 86(3) investment **account**" wording on firm surfaces

`ActivityMessageFormatter.trustTypeNoun`, `AuditEventTypeRegistry` `trust_firm_inv_place.*` and
`TrustTransactionTypeLabels.placementNoun` (`:43`) describe a section 86(3) placement as "placement in a
section 86(3) investment account". All are firm-side surfaces (activity feed, audit registry, approval
notification), none is in 648.11/648.12, and 648D needed no change to `TrustTransactionTypeLabels`.
Pins that move with a fix: `ActivityMessageFormatterTest`, `TrustNotificationHandlerTest`.
**Owner:** 648F (it rewrites client-statement labels) or a firm label slice.

## F-2 (OQ-8 → (a)) — architecture §16.7.8 claims `recomputeMatter` deletes no-longer-eligible rows; it does not

§16.7.8 (arch doc, ~line 2005): "Portal rows for the interest postings deleted by tenant V16 are removed
when `recomputeMatter` next replays that matter (it deletes rows no longer eligible)". Code:
`TrustLedgerPortalSyncService.recomputeMatter` only upserts (still true after 648D — its Javadoc now says
so); deletion happens only in `backfillForTenant`'s drift repair, which has **no production caller**
(grep of `backend/src/main`: only its own class and a Javadoc in `RequestScopes`). 648D widens eligibility
(adds `CLIENT_INVESTMENT` rows), so it creates no stale rows itself. On a persistent DB, portal rows of
interest postings removed by tenant V16 stay until a wipe (R12: dev/e2e stacks are reset).
**Owner:** 651A (deletes the interest run) — fix the doc claim, or add a per-matter
"delete rows not in this replay" (`deleteTransactionsForMatterNotInIds` already exists) if the founder
wants the read-model to self-heal.

## F-3 (pre-existing, not 648D's; noted because 648D touched the method) — backfill attributes every row of a matter to the first row's client

`backfillForTenant`'s `MatterRollup` is keyed by matter and stamps every row and the balance with the
**first** row's `customer_id` (`new MatterRollup(id, txn.getCustomerId())`), unlike `recomputeMatter`'s
per-row attribution. A multi-client matter (e.g. a same-matter transfer between two clients) would be
projected wholly to one client by the backfill. 648D kept the behaviour (the split is applied inside the
existing rollup). No production caller today (F-2), so no live impact. **Owner:** whoever first gives the
backfill a caller.

## Carried, unchanged

- 648A A-1 ("Inv place" / "Inv return" in the portal list) — **fixed by 648D** (`typeLabel` from the
  backend; vitest `renders the backend's label for section 86(4) rows, never the code`).
- F-17 (CI Surefire heap OOM at `-Xmx3g`, 648B) — 648D adds no new context-cache key (every new/changed
  test class keeps its existing annotation set; `TrustBooksFixture` / `TrustLedgerQueryRepository` /
  `portalJdbcClient` are existing beans). Report if it recurs; not fixed here.
