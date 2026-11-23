# §1.5 — an Idempotency-Key survives a page reload (648B F-11 residual, MEDIUM; ruled option (a), frontend only)

Source: `tasks/trust-accounting-open-issues-2026-11-23.md` §1.5 (on `main` via #1844); founder ruling 2026-11-23 (option (a)).

## Root cause

F-11 (#1841) kept the key of a submit whose outcome is unknown (network failure, 5xx, in-progress 409) in page state only (`createIdempotencyKeys`' in-memory `Map`). A reload — or a remount — lost it, so the same submit went out with a fresh key and the backend ran it as a new request: a second `AWAITING_APPROVAL` row (payment, refund, fee transfer, placement…) or an immediate second posting (deposit, transfer). Reproduced red on main (`red-run.txt`): after a lost response and a reload the resubmit used `page2-1`, not `page1-1`.

## Fix (`frontend/hooks/use-idempotency-key.ts`, shared — every call site inherits it)

- **Persist unsettled keys.** `run(request, call)` writes `{key, at}` to `sessionStorage` under `kazi.trust-idempotency:` + `JSON.stringify(request)` — the request's endpoint identifier (the first array element, e.g. `"recordPayment"`) plus its ids and normalised form values, i.e. exactly the identity F-11 already used for "changed request". It is written **when the request is sent** (an in-flight request's outcome is unknown too: a reload mid-request is the commonest lost response), and kept while the outcome stays unknown. A later `run` of the same request — in this page or after a reload — reuses it; the backend then replays the first result or answers the in-progress 409, never posting again.
- **Clear on settlement.** A success or a definite refusal removes the entry (only if it still holds this submit's key, so a concurrent identical submit cannot clear another's key).
- **TTL: 24 hours** (`UNSETTLED_KEY_TTL_MS`). A reload-and-retry happens within minutes, so a day loses nothing; it must stay well inside the backend's 7-day `RETENTION` (a key reused after the backend purged it would run as a new request — the very thing this prevents); and it bounds how long a stale entry can show the hint. `sessionStorage` is per tab and cleared when the tab closes, so most entries go much sooner. Expired or malformed entries are ignored and removed when read.
- **Never blocks.** Every storage access is in `try/catch`; no storage (SSR, blocked, private mode, quota) → the F-11 in-memory behaviour, unchanged.
- **Concurrency kept.** A concurrent identical submit in the same page still gets its own key (F-11 test unchanged): a request in flight is never re-read from storage by the same store.
- **Hint.** `useIdempotencyKey(scope?)` now also returns `unsettled`: true when a stored unsettled key exists under the form's `scope` (read after mount, so the server render never touches storage) or, after a submit, when its request is left unsettled. `components/trust/unsettled-submit-notice.tsx` renders the ruled text, "A submit of this entry may already have been recorded — check the ledger before submitting again." (a warning `Alert`, `role="status"`; an inline one-line form for the row-level Approve button). Legal terms only (ADR-357).
- **Calculate excluded.** Reconciliation calculate keeps `freshIdempotencyKey()` per call (untouched; it never goes through the hook).

## Call sites (all 17 files that use the hook; the brief's 26 trust-write actions)

Each passes a scope (the action name + its owning id) and renders the notice next to its error line: `RecordDepositDialog`, `RecordPaymentDialog`, `RecordRefundDialog`, `RecordTransferDialog`, `RecordFeeTransferDialog`, `PlaceInvestmentDialog`, `WithdrawInvestmentDialog`, `RecordInvestmentInterestDialog`, `AllocateUnidentifiedDialog`, `PayToFundDialog`, `InterestMemoDialog`, `DiscardStatementDialog`, `reversal-button`, `approval-badge` (approve inline + reject dialog), `CreateTrustAccountDialog` (its request was a bare object; it now carries a stable form identifier, `["createTrustAccount", input]`), `InvestmentInterestEntriesDialog` (per entry: `["reverseInvestmentInterest", entryId]`), and the reconciliation wizard (`reconciliation/new/page.tsx`: no single scope — upload, auto-match, create, manual match, exclude, unmatch, record-from-line, complete — so its notice shows after a submit of the wizard leaves an outcome unknown).

## Tests (vitest)

`frontend/__tests__/trust/trust-idempotency-reload.test.tsx` (new, 11):
- a resubmit after a simulated reload (new store, same storage, same content) reuses the key — **red on main** (`red-run.txt`);
- the key is kept while the first submit is still in flight (reload mid-request);
- a success clears the entry; a definite refusal clears the entry;
- changed content gets a new key (and the first request keeps its own);
- an entry older than the 24 h TTL is ignored and removed;
- unavailable storage (every access throws) falls back to in-memory keys;
- the tab's `sessionStorage` is the default;
- `useIdempotencyKey(scope).unsettled` reflects a stored key for its scope (and not another account's);
- the hint renders in `RecordPaymentDialog` when its form has a stored unsettled key, and not otherwise.

`__tests__/trust/trust-idempotency-key.test.tsx` (F-11): unchanged cases, plus `sessionStorage.clear()` before each (keys now persist across cases in one file) and one D-2 case (the interest reversal sends `{ reason }` with the key).

Green: `green-run.txt`.

## Out of scope / residual risk

- **A second device, or a second tab** (its own `sessionStorage`), is not covered: it starts with a fresh key. The hint does not appear there. The backend does no duplicate-content check (ruled out of scope: no backend change).
- **A key stuck IN_PROGRESS server-side — resolved in-PR by the escape action (ruling, 2026-11-23).** The backend refuses a key whose first request is still in progress or never completed (`TrustIdempotencyService.claim`, 409 `idempotency_in_progress`). With persistence, a reload no longer gives a fresh key, so without an escape the same content would keep getting the 409 for up to 24 h (a broken UI path this PR would have introduced; rated MEDIUM by the orchestrator). Fix:
  - **"Submit as a new entry".** Whenever the form's notice shows — the form opens with a stored unsettled key, or a submit ends unknown (the in-progress 409, a 5xx, a lost response) — it carries a secondary button, `variant="outline"` + `hover:bg-muted`, introduced by "Only if you have checked the ledger and it was not recorded:". It calls `discardUnsettled()` (forgets the form's unsettled keys in memory and in `sessionStorage`: its scope and the last request it ran), then submits the enclosing form, which takes a fresh key. It is a deliberate second step: a plain resubmit still reuses the stuck key (tested). Approve (no form) discards and approves again; the interest-entry confirm discards that entry's key; the reconciliation wizard (no single form) discards, and the person repeats the step.
  - **Backend 409 copy** (`TrustIdempotencyService.IN_PROGRESS_DETAIL`; title and code unchanged): "An earlier submit of this entry is still in progress, or its outcome is unknown. Check the ledger to see whether it was recorded; if it was not, choose 'Submit as a new entry'." `TrustIdempotencyIntegrationTest` asserts it.
  - **Tests** (`trust-idempotency-reload.test.tsx`, on `ReversalButton`): the action appears on the in-progress 409 and on the stored-key hint; using it sends a fresh key (never the stale one) and clears the stored entry; it is never automatic (a plain resubmit reuses the stuck key); it is absent when nothing is unsettled and after a definite refusal. Red before the action existed: `escape-red-run.txt`.
  - `hasUnsettled` now also sees this page's in-memory unsettled keys, so the hint and the action appear even where storage is unavailable.
- The hint is advisory; it keys on the form scope, so after an unknown outcome it shows on that form (e.g. any payment on that account) until the entry settles or expires.
