# 657A — scout counts and file list

## Hazard scout (task 657.1, grepped at `4299bc058`)

### `switch` / `case` over the registry enums (main; no such switch in test code)

| Site | Over | Exhaustive? | 657A change |
|---|---|---|---|
| `ledger/LedgerTotals.plus` | `TrustTransactionType` | yes | 5 brought-forward arms (signed) + 6 non-client take-on types → `this` |
| `ledger/ClientLedgerService.InvestedMovements.plus` | `TrustTransactionType` | yes | `TAKEON_INVESTMENT` → placed; other 10 → `this` |
| `report/TrustInterestChargesScheduleQuery.addFundGroup` | `TrustTransactionType` | no (`default` → unexplained) | `TAKEON_FUND`, `TAKEON_FUND_DEBIT` → `takeOnBroughtForward`; pinned by `TrustInterestChargesFundGroupTest` |
| `transaction/TrustPostingService.recordWithPair` `switch (pairApproval)` | `Approval` | yes | `AT_SIGN_OFF -> null` |
| `transaction/TrustPostingService.postReversal` `switch (rule)` | `Reversal` | no (`default` throws) | none |
| `transaction/TrustTransactionService` PAIR reversal | `TrustTransactionType` | no | none (no take-on type is `PAIR`) |
| `activity/ActivityMessageFormatter.trustTypeNoun` | code string | no (registry-label fallback) | none |
| `transaction/TrustTransactionTypeLabels.placementNoun` | code string | no | none |
| `Origin` | — | no switch anywhere (BE or FE) | none |

### Construction fan-out

- `new LedgerTotals(` — main only inside `LedgerTotals`; test `LedgerTotalsTest` (8 → 9 args).
- `new CreditorLine(` — `TrustMonthlyListService` ×4 (FUND, UNIDENTIFIED, INTER_ACCOUNT, `clientLine`); no test constructs one.
- `new TrustPostingService(` — `TrustPostingServiceRecordWithPairTest` (+ the new `TrustPostingServiceRecordOpeningTest`); constructor signature unchanged.
- `new TrustAccountResponse(` / `new ClientLedgerCardResponse(` / `new TrustTransactionTypeResponse(` — only their services.
- Readers of `LedgerTotals` / `CreditorLine` components (`TrustLedgerQueryRepository`, `TrustMonthlyList`, `ClientTrustBalancesQuery`, `Section35DataPackQuery`) compile unchanged (C-14, D-67).

### Hard-coded counts

- 25 → 36: `TrustTransactionTypeRegistryTest` (values, TABLE), `TrustTransactionTypeSchemaTest`, `TrustTransactionTypeControllerTest` ×2.
- `TrustCreditorAccountIntegrationTest:555` stays **25** (V18-targeted scratch schema) and now compares against the registry less the take-on types (D-58 — the brief said 36; red on the first Tier-2a run).
- never-client-visible NON_CLIENT/INTER_ACCOUNT set 12 → 17; investment-row set 9 → 12; bank-origin and statement-only sets unchanged.
- `LedgerTotalsTest` no-op list 15 → 21.
- `trust-contract.json` regenerated (`-Dtrust.contract.update=true`): additions only — `Origin` `TAKE_ON`; `TrustTransactionTypeResponse` `takeOnOnly`, `clearedAtCutover`; `TrustAccountResponse` `takeOnCutoverDate`; `ClientLedgerCardResponse` `broughtForward`; `CreditorLine` `broughtForward`.

### C-5 / C-20 (informational, D-59)

- `postDirect(` is called from **2** test files (`TrustReversalEquationIntegrationTest`, `DerivedLedgerBalanceIntegrationTest`), not 35; `TrustBooksFixture` was referenced by 38 test files at HEAD (41 after 657A).
- `UnidentifiedDepositService` calls `item.getBankStatementLineId().toString()` only on the open-from-line and reverse-by-unmatch paths — never reachable for a taken-on item; no backend change. FE `UnidentifiedDeposit.bankStatementLineId` → `string | null`. The "Taken on at {date}" display for line-less items is 657C/657D.

## Files changed (vs `main` @ `4299bc058`)

**20 main (19 Java + V33) · 17 test (+ `trust-contract.json`) · 6 FE main · 8 FE test** (estimate ≈ 21 · 21 · 8); plus evidence and the decisions log.

```
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccount.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountRepository.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountService.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/books/CreditorLine.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/books/TrustMonthlyListService.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/creditor/UnidentifiedDeposit.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/creditor/UnidentifiedDepositService.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestment.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/LedgerTotals.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/report/TrustInterestChargesScheduleQuery.java
A	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/takeon/OpeningAuthority.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustLedgerQueryRepository.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustPostingService.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransaction.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionRepository.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java
M	backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionType.java
A	backend/src/main/resources/db/migration/tenant/V33__phase93_trust_take_on.sql
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/takeon/TakeOnEntryPointsArchTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/TrustBooksFixture.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/TrustBooksHarness.java
A	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustTakeOnSchemaTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/creditor/TrustCreditorAccountIntegrationTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/LedgerTotalsTest.java
A	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/report/TrustInterestChargesFundGroupTest.java
A	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/takeon/OpeningAuthorities.java
A	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/takeon/OpeningBalanceAsDepositCharacterisationIntegrationTest.java
A	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/takeon/TakeOnReportsOverCutoverIntegrationTest.java
A	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/takeon/TrustOpeningPostingIntegrationTest.java
A	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustPostingServiceRecordOpeningTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustPostingServiceRecordWithPairTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionTypeControllerTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionTypeLabelsTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionTypeRegistryTest.java
M	backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionTypeSchemaTest.java
M	backend/src/test/resources/wire/trust-contract.json
M	frontend/__tests__/trust-investments-reports.test.tsx
M	frontend/__tests__/trust-reconciliation.test.tsx
M	frontend/__tests__/trust-tabs-settings.test.tsx
M	frontend/__tests__/trust-transactions.test.tsx
M	frontend/__tests__/trust-wire-shapes.test.ts
M	frontend/__tests__/trust/client-ledger-page.test.tsx
M	frontend/__tests__/trust/ledger-totals-breakdown.test.tsx
M	frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[clientId]/page.tsx
M	frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/lists/[listId]/page.tsx
M	frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx
M	frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx
M	frontend/components/trust/ledger-totals-breakdown.tsx
M	frontend/lib/types/__tests__/trust.test.ts
M	frontend/lib/types/trust.ts
A	qa_cycle/evidence/phase93/657A/characterisation.md
```
