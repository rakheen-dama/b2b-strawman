# 646E — defects found outside the slice (reported, NOT fixed)

Phase rule: a defect outside the stated scope is written here, reported, and re-specced — never added.

## F-646E-1 — Reconciliation split pane prints a raw type enum (orchestrator ruling D7 → 646C)

- `frontend/components/trust/ReconciliationSplitPane.tsx:304` renders
  `{tx.transactionType.replace(/_/g, " ")}` — a raw code on screen ("FEE TRANSFER", and for the
  Phase 92 types e.g. "UNIDENT FUND PAY"), contrary to §16.6 "no raw enum reaches a screen".
- Same bug class as 646E; the fix is one line (`{tx.typeLabel}` — the field now arrives on every
  `TrustTransaction` DTO) plus a test assertion.
- Left for **646C**, which edits `ReconciliationSplitPane.tsx` next (ruling D7).

## F-646E-2 — Transactions page date / client filters are silently ignored (pre-existing)

- The firm Transactions page passes `dateFrom`, `dateTo` and `customerId` into `fetchTransactions`
  (`frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx`, the `fetchTransactions(accountId, {...})`
  call), which sends them as `dateFrom`, `dateTo` and `clientId` query params
  (`frontend/app/(app)/org/[slug]/trust-accounting/transactions/actions.ts:62-66`).
- `GET /api/trust-accounts/{accountId}/transactions` accepts only `matterId`, `type`, `status`,
  `ledger` (new in 646E) and `excludeLinked`
  (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionController.java:43-47`);
  `TrustTransactionRepository.findFiltered` has no date or client predicate.
- Effect: choosing a date range or a client in `TransactionFilters` on the Transactions page changes
  the URL but not the rows — the list stays unfiltered with no indication.
- Not fixed here (scope: 646E is registry-as-data). Needs its own slice: either add the three
  filters to the endpoint (JPQL predicates + params) or remove the inputs from the page.
