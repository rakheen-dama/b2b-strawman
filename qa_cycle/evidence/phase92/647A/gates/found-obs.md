# 647A gates — found items (reported, not fixed)

Gate run on PR #1794 @ ab1714caf, 2026-11-17. **No defect (F-n) in 647A's scope.** Observations only:

- **O-1 (brief / harness).** `GET /api/trust-accounts/{id}/cashbook-balance?asAt=` returns `{balance}` only
  (`TrustTransactionService.CashbookBalanceResponse`). The creditor breakdown is on
  `GET /api/trust-accounts/{id}/total-balance?asAt=` (`ClientLedgerService.TotalBalanceResponse`). OBS read both. Not a
  defect.
- **O-2 (pre-existing, out of scope).** On wizard step 4, "Unmatched Trust Transactions" lists APPROVED unlinked rows
  dated **after** the period end: `FEE/MOROKA/…` R 8 500, dated 17 Nov, appears with a 14 Nov period end
  (`obs/09-*`, `obs/12-*`). The cause is `reconciliation/new/page.tsx` `handleStartReview` / `handleUnmatch`, which
  call `fetchTransactions(…, { status: "APPROVED" })` with no date bound. This code is identical on `main`; 647A did
  not change it. Server-side figures are unaffected (as at the period end). Suggested owner: 652A.
- **O-3 (UX, low).** A recorded bank charge's badge reads "Recorded: Bank charge" whether or not it is recoverable from
  the Fidelity Fund. The recoverable flag and the VAT are visible only in the row (API/SQL). This is a product call.
- **O-4 (harness).** Playwright `fullPage` screenshots draw the fixed sidebar and top bar mid-page. The element crops
  are exact.
