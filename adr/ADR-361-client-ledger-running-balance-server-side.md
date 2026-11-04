# ADR-361: Client-ledger running balance is computed server-side in posting order

**Status**: Accepted

**Context**:

The firm-side client ledger card page computes its own running balance: `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[clientId]/page.tsx:176-205` starts from `ledgerCard.balance` and walks the DESC-ordered first page backwards, treating REFUND as an inflow (`isInflowType` at `:43-45` lists `DEPOSIT, TRANSFER_IN, REFUND, INTEREST_CREDIT`). The backend is the opposite on both counts: `ClientLedgerService.java:47-51` puts REFUND in `DEBIT_TYPES`, and `TrustTransactionRepository.calculateClientBalanceAsOfDate` (`:135-153`) subtracts it and counts only `status IN ('RECORDED','APPROVED')`. The `/history` endpoint the page reads (`ClientLedgerController.java:41-47`) sorts by `transactionDate DESC` only — no `createdAt` tie-break (`TrustTransactionRepository.java:60-61`) — and returns every status including `AWAITING_APPROVAL`, `REJECTED`, `REVERSED` and `REVERSAL` rows, which the backward walk then adds or subtracts as if posted. The Phase 88 re-walk showed the Moroka ledger with a wrong running balance after a refund (G88-F23, MEDIUM). `transactions/page.tsx:48-50` carries the same `isInflowType` but uses it only for the arrow and colour — a direction defect, not a balance one.

A correct server-side running balance already exists for one surface: `getClientLedgerStatement` (`ClientLedgerService.java:179-221`) opens with `calculateClientBalanceAsOfDate(start − 1)` and walks `findForStatement` rows (`:240-252`: posted statuses only, `transactionType <> 'REVERSAL'`, `ORDER BY transactionDate ASC, createdAt ASC`) into `LedgerStatementLine.runningBalance`. The portal trust sync walks `findByProjectIdOrderByTransactionDateAsc` with `ORDER BY transactionDate ASC, createdAt ASC, id ASC` (`:70-77`, Epic 495A). Since PR #1716 ([ADR-358](ADR-358-product-wide-sa-business-clock.md)) `transaction_date` is the SA calendar day, so posting order is `(transaction_date, created_at, id)`. The requirements left the placement open ("the backend supplies the running balance if that is the simpler single source — architecture decides"); ruling R-E1c decides it.

**Options Considered**:

1. **Fix the frontend walk: drop REFUND from `isInflowType`, skip non-posted and REVERSAL rows, keep walking backwards from `ledgerCard.balance`.**
   - Pros:
     - Smallest diff; no API change.
   - Cons:
     - The walk is only correct on page 0 and only when the page is sorted the way the balance was accumulated; `/history` has no `createdAt` tie-break, so two postings on one day can render in the wrong order and the balances between them are wrong even with the right signs.
     - Pages after the first already render `null` because the API returns no starting balance (`:177-181`); the defect is structural, not a sign error.
     - A second copy of the sign rules (`CREDIT_TYPES` / `DEBIT_TYPES`) lives in TypeScript and drifts again the next time a transaction type is added — the F43/F44 drift class ([ADR-365](ADR-365-wire-shape-contract-pins-compliance.md)).
2. **Switch the card page to the `/statement` endpoint's shape and drop `/history` for the ledger card.**
   - Pros:
     - Reuses the one correct running balance with no backend change.
   - Cons:
     - `/statement` is a date-range report (opening + closing + ASC lines) for the PDF; it is not paginated and carries no `AWAITING_APPROVAL` / `REJECTED` / `REVERSED` rows at all, which the ledger card must show (an attorney needs to see a rejected payment on the ledger, just without a balance).
     - The page would lose the filters and paging it already has.
3. **Materialised running balance column on `trust_transactions`, written at post time.**
   - Pros:
     - O(1) reads; the statement and the portal sync could read it too.
   - Cons:
     - A back-dated posting (user-entered `transaction_date` earlier than existing rows — allowed; `TrustPostingDateZoneIntegrationTest` has `USER_DATED` 2026-10-28) invalidates every later balance, so every post becomes a rewrite of the client's tail; reversals and approvals change the posted set after the fact.
     - A new tenant column (V10 section) and a backfill for a value the database can compute in one window function.
4. **Backend computes `runningBalance` in the `/history` query with a window function over posted rows only, in posting order; non-posted and REVERSAL rows carry `null`; filters bound in the same query (CHOSEN).**
   - Pros:
     - One source of truth: the same type sets as `calculateClientBalanceAsOfDate`, the same status filter as `findForStatement`, the same order as the portal sync. The statement, the balance-as-of and the history page cannot disagree because they share the predicate.
     - Paging is correct on every page because the window runs over the full posted set before the page is cut.
     - The frontend deletes its walk and its sign rules; the `isInflowType` bug in `transactions/page.tsx` becomes a one-line colour fix.
   - Cons:
     - A native query (the derived query cannot express a window); the rewrite also has to honour the four filter params the UI already sends and the server ignores (`type`, `status`, `dateFrom`, `dateTo` — `client-ledgers/actions.ts:102-105`), which is in scope only because it is the same query.
     - The window must be computed over the unfiltered posted set and the filters applied outside it, or a type filter would change the balances; the query shape must make that explicit.

**Decision**: Option 4 — `GET /api/trust-accounts/{accountId}/client-ledgers/{clientId}/history` rows gain a nullable `runningBalance`, computed server-side in a native query by a window function over posted rows only (`status IN ('RECORDED','APPROVED')`, type sets identical to `calculateClientBalanceAsOfDate`, `REVERSAL` excluded) ordered `(transaction_date, created_at, id)`; the page is returned in the reverse order (`DESC, DESC, DESC`); non-posted rows (`AWAITING_APPROVAL`, `REJECTED`, `REVERSED`) and `REVERSAL` rows carry `runningBalance = null` and render "—"; the `type`, `status`, `dateFrom`, `dateTo` filters are bound in the same query, applied after the window.

**Rationale**:

1. **The ledger is a trust-accounting artefact; its arithmetic belongs where the posting rules live.** `CREDIT_TYPES` / `DEBIT_TYPES`, the posted-status set and the REVERSAL exclusion are backend facts used by the balance-as-of query, the statement PDF, the closure gate (`TrustBalanceZeroGate`) and the portal sync. Duplicating them in a React page is how G88-F23 happened; a Section 35 reader compares the ledger card to the statement, and they must be the same number by construction, not by two implementations agreeing.
2. **Posting order is a defined thing now.** Before [ADR-358](ADR-358-product-wide-sa-business-clock.md) the server date could be the UTC day, so "date then created-at" was ambiguous at the SAST boundary; with the clock fixed, `(transaction_date, created_at, id)` is the order every trust reader already uses (`findForStatement`, `findByProjectIdOrderByTransactionDateAsc`), and this ADR names it as the ledger order.
3. **Invariant, tested on the real write path (Phase 88 lesson).** The integration test creates rows through `TrustTransactionService` as the UI does — a deposit, a fee transfer and a refund — then asserts that the first **posted** `/history` row's `runningBalance` equals `calculateClientBalanceAsOfDate(today)` and equals the `/statement` endpoint's last `LedgerStatementLine.runningBalance`; a second case adds an `AWAITING_APPROVAL` payment and asserts it renders `null` without moving the balance of the posted rows around it. The browser observation on the Keycloak tenant is the Moroka ledger after the Day 60 refund, row by row.
4. **Schema-per-tenant cost is nil.** The window runs inside the tenant schema on the existing `trust_transactions` index set; no migration, no cross-schema read, no global table.

**Consequences**:

- Positive:
  - The card page deletes its backward walk (`page.tsx:176-205`) and renders the field; both pages drop `REFUND` from `isInflowType` (colour and arrow only on the transactions page). `TrustTransactionResponse` and the TS type gain `runningBalance: number | null`.
  - Pages after the first show balances for the first time.
  - The `/history` filter params stop being silently ignored — a pre-existing LOW closed incidentally because the native query is rewritten anyway (rulings §1).
- Negative:
  - The repository method becomes a native query with a window function and the four optional filters; it needs its own repository test for each filter plus the "filter does not change balances" case.
  - "—" on a non-posted row is a new rendering state the badge map must cover; an attorney may ask why a rejected payment has no balance — the answer is in the label, not a tooltip.
  - `DISBURSEMENT_PAYMENT` appears in the TS union (`trust.ts:39-49`) but is not a stored value; the window's type sets do not list it, matching `calculateClientBalanceAsOfDate`.
- Follow-ups / not done:
  - No materialised balance column; no change to `/statement` or to the portal trust sync; no backend enum for transaction type or status (they stay `String` columns — [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) stored literals unchanged).
  - A transaction-type label map for the portal is R-E4c, not this ADR.

**Related**: [ADR-358](ADR-358-product-wide-sa-business-clock.md) (posting date is the SA day), [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) (portal trust sync uses the same order), [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md) (balance model precedent: persisted on the fee note, computed on the ledger), [ADR-365](ADR-365-wire-shape-contract-pins-compliance.md) (the FE/BE drift class this removes a member of), [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md); `architecture/phase89-go-live-readiness.md` §14.3, §14.4, §14.8, §14.11; `qa_cycle/evidence/phase88-rewalk/full/gaps.md` G88-F23.
