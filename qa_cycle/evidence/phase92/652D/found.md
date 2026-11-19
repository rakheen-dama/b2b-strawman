# 652D found.md — out-of-scope items

Every item below is **reported, not fixed in 652D**. They are re-spec candidates for the orchestrator.

## (i) A client with money on two open accounts sees one account — reported, not fixed in 652D
Both client documents are single-account documents by design. The 652D rule (`ClientTrustStatementContext.statementAccountId`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientTrustStatementContext.java:178`) picks one account. When the client holds money on two open accounts at the document date, it picks the primary GENERAL among them, otherwise the first id. The other account's money does not appear on the SoA (`StatementOfAccountContextBuilder.java:555-571`) or on the CTS. A multi-account statement would be a product change.

## (ii) "Holds money" counted the general ledger only — now covered by ruling 652D D-2
**Covered (review D-2).** The D-1 narrowing step read only `ClientLedgerService.getClientBalanceAsOfDate`, the client's **general** balance. An `INV_PLACE` takes the money off the general ledger, so a client whose money on the replacement account was entirely invested under s86(4) (general R0, invested > 0) looked like the closed primary (R0). No account passed the filter, the tie-break picked the closed primary again, and the invested block was read against it, so the invested money vanished from both documents (case (d), `red-d2-surefire.txt`).

D-2 counts general **plus** invested: `ClientTrustStatementContext.heldOn` (`ClientTrustStatementContext.java:202`) adds `ClientLedgerService.getClientInvestedBalanceAsOfDate` (`ClientLedgerService.java:338`, module-guarded; `TrustLedgerQueryRepository.clientInvestedBalance` COALESCEs to zero) to the general balance as at the document date. Case (d) of `StatementBuilderClosedPrimaryAccountIntegrationTest` is green.

Residual, reported only: when the client holds money (general or invested) on **two** accounts at the document date, both pass the filter and the tie-break prints one of them, so the other account's money and invested block do not appear. That is item (i) (single-account documents by design), not a gap in the "holds money" test.

## (iii) Frontend trust pages default to a closed primary — reported, not fixed in 652D
These pages pick `accounts.find(a => a.isPrimary) ?? accounts[0]` and do not check status, so on a tenant whose primary is CLOSED they open on the closed account. Compare `clients/[id]/hero-tiles.ts:67`, which does check `status === "ACTIVE"`. All paths are under `frontend/app/(app)/org/[slug]/trust-accounting/`:
- `transactions/page.tsx:139`
- `client-ledgers/page.tsx:57`
- `client-ledgers/[clientId]/page.tsx:117`
- `other-creditors/page.tsx:97`
- `investments/page.tsx:121`
- `reconciliation/new/page.tsx:211`
- **Also found (not in the brief's list):** `page.tsx:178` (the trust-accounting landing page) and `reconciliation/page.tsx:59`. Same pattern.

This is a frontend defect, separate from 652D.

## (iv) No UI to close a trust account, and no way to move the primary flag — reported, not fixed in 652D
- `TrustAccountService.closeTrustAccount` (`backend/.../trustaccounting/TrustAccountService.java:332`) has no frontend caller. Nothing under `frontend/lib/api` or `frontend/app/(app)/org/[slug]/trust-accounting` posts to `/close`.
- `updateTrustAccount` (`TrustAccountService.java:256`) has no primary field and refuses a closed account.
- The unique index `idx_trust_accounts_primary_general` (`backend/src/main/resources/db/migration/tenant/V1__baseline.sql:2218`) allows only one primary GENERAL account.

So a closed primary stays primary for good, and no other account can become primary. Postings on a CLOSED account are refused (§16.14.4, last row). This was already noted.

## (v) §16.14.4 dashboard half — not examined, not changed
`DashboardService.java:614` (the SECTION_86-as-main shape) is a dashboard concern. Case (c) of the reproduction shows that the statement builders are not affected (see README). 652D does not touch the dashboard.
