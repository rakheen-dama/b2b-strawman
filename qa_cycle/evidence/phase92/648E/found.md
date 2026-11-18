# 648E — found / carried (not fixed in this slice)

Scope discipline (task file): out-of-row items are recorded here, reported and re-specced — nothing below is coded in 648E.

## Carried from earlier slices (owner in brackets)

| # | Source | Item | Owner | Note |
|---|---|---|---|---|
| C-1 | 648A re-gate O-1 | Client Trust Statement and Statement of Account show only the general balance after an s86(4) placement (no invested figure) | **648F** (task 648.13) | 648E covers the firm client-ledger page and the client Trust tab (shared `LedgerTotalsBreakdown`) only. |
| C-2 | 648A A-2 (LOW) | `client-trust-balances` report prints deposits / payments / fee transfers only (no transfer or investment columns), so after an approved s86(4) placement its balance (P lower) is not explained by the printed totals | **650C** (definition edits land in 650B/650C) | `ClientTrustBalancesQuery` untouched; with OQ-1 Option B its printed output is unchanged (it only `put`s deposits/payments/feeTransfers). |
| C-3 | 648A A-3 + re-gate N-2 (LOW) | Activity feed / audit registry / s86(3) approval notification still say "placement in a section 86(3) investment **account**" (`ActivityMessageFormatter.trustTypeNoun`, `AuditEventTypeRegistry` `trust_firm_inv_place.*`, `TrustTransactionTypeLabels.placementNoun`) | proposed **648D** (reworks `typeLabel`) | None of these files is in 648.7. |
| C-4 | 648A re-gate N-1 (MEDIUM) | Client Trust Statement prints "Inv place" / "Inv return" (`ClientTrustStatementContext.typeLabel`) | **648F** | — |
| C-5 | 648A re-gate P-4 (pre-existing) | Trust notification bodies print "R7500" (no thousands separator / decimals) — `TrustNotificationHandler` | no owner yet | carry. |
| C-6 | 648C gates P-3 | The Approval Settings card on the trust settings tab lists investment accounts too | product/UI call — natural owner the Phase 91 settings shell | Analysis: a placement takes the **funding** account's approval mode; on an investment account only `WITH_PAIR` / `NONE` rows post (`INV_RECEIVE`, `INV_WITHDRAW`, 648B `INV_INTEREST`), so an investment account's approval setting governs nothing today. `$FA/settings/fees/_tabs/trust-accounting.tsx` not in 648.7. |
| C-7 | 648C F-3 | `CreateTrustAccountDialog` defaults "primary" for every type; the account pickers (incl. `[clientId]/page.tsx` `accounts.find(a => a.isPrimary) ?? accounts[0]`) do not check the type | re-spec owner open | Pre-existing. The lifecycle opens investment accounts from the preset dialog (`isPrimary: preset === undefined` → false), so day-60 is unaffected. Picker not changed in 648E. |
| C-8 | 648A review cycle 1 | `investmentBalance` has no as-at bound | **648B** picks the policy | 648E's `investedBalance` is undated, like the card's `balance` (`clientSummary` has no as-at). |
| C-9 | 648A F-6 | Cashbook in debit after s86(3) — sufficiency model | **649C** (founder ruling) | — |

## New findings while building 648E (report only)

| # | Where | Finding | Severity | Candidate fix / owner |
|---|---|---|---|---|
| F-1 | `frontend/components/trust/TrustBalanceCard.tsx` matter mode | The line "{client} — trust balance across all matters: R{ledger.balance}" shows the client's **general** balance while the tile above it shows the matter total (general + invested). After an s86(4) placement the two figures disagree with no explanation. | LOW | Say "general" in the line, or add the invested figure (`ledger.investedBalance` is now on the card). UI owner (Phase 91 / a trust UI slice). Client mode of the same card does pick up 648E through `LedgerTotalsBreakdown`. |
| F-2 | `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/page.tsx` (list) | The client-ledgers list shows Balance + four totals, no invested column. The API rows now carry `investedBalance`, `totalInvestmentPlacements`, `totalInvestmentReturns`. | LOW | A later UI slice can show "Invested" beside the balance. Not in 648.7. |
| F-3 | `frontend/e2e/tests/legal-lifecycle/day-60-interest.spec.ts` | `Number.prototype.toLocaleString("en-ZA")` under the local nvm node 20.11.1 prints "30,000.00" (not "30 000,00") — ICU data differs by build — so the brief's `rand` helper sketch would have produced a pattern that never matches. 648E builds the "30 000,00" string by hand. | INFO | Any spec that formats expected figures with `toLocaleString("en-ZA")` is build-dependent; prefer explicit formatting. |
| F-4 | `frontend/components/trust/TrustBalanceCard.tsx` client mode (PR #1800 review) | The headline figure and badge read `ledger.balance` (general), so a client whose whole balance is placed in an s86(4) investment shows "No Funds" / R0.00 above the breakdown's "Total held in trust R30 000". The server returns no total-held field; the UI adds general + invested. | LOW | Drive the headline/badge from general + invested (or a server-side `totalHeld`). Trust UI owner (Phase 91 / a trust UI slice); recorded hand-off, not in 648E. |

## Hand-off to 648B (forward note, no action needed in 648E)

With OQ-1 Option B the card identity is `deposits + transfersIn + investmentReturns − payments − feeTransfers − refunds − transfersOut − investmentPlacements = balance (general)`. A net interest entry (`INV_INTEREST`, ledger `CLIENT_INVESTMENT`, with `investment_id`) lands in `investedBalance` through `TrustLedgerQueryRepository.investedBalancesByClient` with no 648E change, and on withdrawal the larger return lands in `totalInvestmentReturns` — the identity holds without a new card total.

## From the PR #1800 re-review (Reviewer B, non-blocking Mediums — report only)

| # | Finding | Owner |
|---|---|---|
| R-1 | `ClientLedgerService.getClientLedger` / `listClientLedgers` are the codebase's first non-default isolation (`@Transactional(readOnly = true, isolation = REPEATABLE_READ)` — one snapshot for general + invested + totals, CodeRabbit CR-1). `backend/CLAUDE.md` does not say whether this is the pattern or the exception; suggested line: "Read endpoints composing several aggregates use `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`; the default stays READ_COMMITTED." | docs follow-up (any slice touching `backend/CLAUDE.md`) |
| R-2 | Other multi-statement trust reads (646D read-side consumers: creditor ledger, reconciliation views) still compose aggregates across separate READ COMMITTED snapshots. Not a 648E regression; recorded so REPEATABLE_READ is not assumed to be the norm. | 649C / 650x owner to assess |
