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

**PR #1802 CodeRabbit CR-6** (outside-diff, `TrustLedgerPortalSyncService.java:421`) is this finding.
Pre-existing on `origin/main` @ `2b7be66d6`: `TrustLedgerPortalSyncService.java:375`
(`.computeIfAbsent(matterId, id -> new MatterRollup(id, txn.getCustomerId()))`) and the balance write at
`:432` under `rollup.customerId`. Declined in 648D (orchestrator triage): the backfill still has no
production caller (its Javadoc now says so — CR-3). Fix = key the rollup by `(customerId, matterId)`.

## F-4 (PR #1802 CodeRabbit CR-1, pre-existing) — `recomputeMatter` writes rows and the balance snapshot as separate autocommit statements

A failure between two portal writes leaves committed rows whose running balance disagrees with the stored
balance until the matter's next replay. Pre-existing structure, unchanged by 648D: on `origin/main` @
`2b7be66d6`, `TrustLedgerPortalSyncService.recomputeMatter` (`:260`) calls `portalTrustRepo.upsertTransaction`
per row (`:283`) and `upsertBalance` per customer (`:297`) with no `portalTxTemplate` (only the backfill uses
it, `:393`). 648D kept the same structure (it adds the `invested_balance` argument to the same balance write;
no transaction boundary added or removed). Fix = wrap the matter's portal writes in
`portalTxTemplate.executeWithoutResult(...)`, the firm history read staying outside.
**Owner:** a portal-sync hardening slice (with F-5; candidate 652).

## F-5 (PR #1802 CodeRabbit CR-2, pre-existing) — concurrent replays of one matter can write an older snapshot last

Two `AFTER_COMMIT` replays of the same matter (e.g. a posting and an interest / reconciliation fan-out) read
the history and write the balance with no lock or monotonic guard, so a replay that read older history can
overwrite a newer `current_balance` / `invested_balance` until the next replay. Pre-existing on `origin/main`
@ `2b7be66d6`: read at `TrustLedgerPortalSyncService.java:261`
(`findByProjectIdOrderByTransactionDateAsc`), write at `:297` (`upsertBalance`), listeners at `:110/:143/
:163/:183`; 648D adds a second balance column to the same write but no new concurrency path.
Fix options (CodeRabbit): a per-matter lock (e.g. `pg_advisory_xact_lock` on the matter id inside F-4's
portal transaction) held from history read through balance write, or a monotonic-marker guard on the
balance upsert. **Owner:** a portal-sync hardening slice (with F-4; candidate 652).

## F-6 (PR #1802 CodeRabbit CR-4, declined by ruling) — existing matters with a posted s86(4) investment read invested 0 until their next trust event

Global V7 adds `invested_balance` with `DEFAULT 0` and never reads tenant schemas (a global migration
cannot). Declined under R12 / brief decision 5: no backfill runner in 648D; section 86(4) investments exist
only since 648A, on dev/e2e stacks that are reset; a fresh stack is correct. Stated in the V7 header
(`V7__portal_trust_invested_balance.sql:5-8`) and the PR body. If a persistent DB ever carries pre-V7
investments, the repair is a tenant-scoped replay — `backfillForTenant` has no caller (F-2, F-3).
**Owner:** whoever first ships to a persistent tenant DB with s86(4) investments (go-live checklist).

## F-7 (648D OBS, pre-existing, outside the slice) — placement with a percentage `interestRate` returns HTTP 500, not 400

During OBS setup the firm REST call `POST` placement with `interestRate: 7` (a percentage; the API expects a
fraction, 0.07) returned **HTTP 500** instead of a validation error: the request field has no upper bound and
the database CHECK rejects the value. The firm UI converts the percentage, so a user cannot reach it through
the screen; the transaction rolled back. Evidence: `gates/obs/attempt2-api-firm-c-place-500-rate7.json`,
`gates/README.md`. 648D touches no investment code. Fix: bean-validation bound on the request's rate
(`@DecimalMax`) → 400 with the app's message. **Owner:** an investment-hardening follow-up (648A area; candidate 652).

## Carried, unchanged

- 648A A-1 ("Inv place" / "Inv return" in the portal list) — **fixed by 648D** (`typeLabel` from the
  backend; vitest `renders the backend's label for section 86(4) rows, never the code`).
- F-17 (CI Surefire heap OOM at `-Xmx3g`, 648B) — 648D adds no new context-cache key (every new/changed
  test class keeps its existing annotation set; `TrustBooksFixture` / `TrustLedgerQueryRepository` /
  `portalJdbcClient` are existing beans). Report if it recurs; not fixed here.
