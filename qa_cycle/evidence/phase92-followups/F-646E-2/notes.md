# F-646E-2 notes

Root cause: `GET /api/trust-accounts/{id}/transactions` (TrustTransactionController.listTransactions) and
`TrustTransactionRepository.findFiltered` had no date or client predicate. The frontend (`actions.ts`)
already sent `dateFrom`, `dateTo`, `clientId`, `matterId`, so Spring silently ignored the unknown params.

Filters supported (query params, all optional, unchanged: matterId, type, status, ledger, excludeLinked):
- `clientId` (UUID, matches trust_transactions.customer_id; unknown id -> empty page)
- `dateFrom`, `dateTo` (ISO date, inclusive on transactionDate; from > to -> 400 InvalidStateException)

Implementation: JPQL optional predicates in `findFiltered` (same pattern as existing params; dates use
`CAST(:p AS LocalDate) IS NULL` so Postgres can type a null bind); one service method; controller and
@RequiresCapability unchanged. Response shape and pagination unchanged. No wire-contract snapshot change
(response shape untouched; TrustWireContractTest green).

Frontend: no production change needed. `actions.ts` already sends the backend's names; the page is a
server component keyed on `searchParams` so router.push refetches; clearing a filter removes the param.
Added `__tests__/trust-transaction-filters.test.tsx` (3 tests) pinning this. These pass on unmodified
frontend, so the frontend vitest is a guard, not a red test.

Tests: TrustTransactionControllerTest gained `listTransactions_dateRangeAndClientFilters_narrowTheRows`
and `listTransactions_fromAfterTo_returns400`; both failed on unmodified code (red-run.txt: returned all
rows / 200), pass after (20/20).
Note: the Transactions page offers a free-text "Client ID" UUID input; a malformed UUID would give a 400
from param binding. Pre-existing UI shape, not changed here.
