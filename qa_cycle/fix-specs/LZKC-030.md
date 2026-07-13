# Fix Spec: LZKC-030 — SoA double-counts period-start-day trust transactions (opening balance boundary)

## Problem
Day 61 / 61.4: the client-facing Section 86 Statement of Account printed "Opening balance: R50 000,00" while ALSO itemising the same period-start-day deposit DEP/2026/001 (12 Jul, the period start date) in the Deposits table — so the statement fails self-reconciliation (50 000 + 70 000 − 70 000 = 50 000 ≠ printed closing R0,00). DB-true opening before 12 Jul is R0; the closing figure is correct. Repro: any matter whose first trust transaction shares the SoA period start date. Artefact: `statement-of-account-dlamini-v-road-accident-fund-2026-07-13.pdf` (SOA-66451e87-20260713).

## Root Cause (confirmed)
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java`, `buildTrustBlock` (lines 529-535):

```java
BigDecimal opening =
    clientLedgerService.getClientBalanceAsOfDate(customer.getId(), trustAccountId, periodStart);
BigDecimal closing =
    clientLedgerService.getClientBalanceAsOfDate(customer.getId(), trustAccountId, periodEnd);
var statement =
    clientLedgerService.getClientLedgerStatement(
        customer.getId(), trustAccountId, periodStart, periodEnd);
```

`getClientBalanceAsOfDate` is **inclusive** of the date — the underlying query is `t.transactionDate <= :asOfDate` (`backend/.../trustaccounting/transaction/TrustTransactionRepository.java:117`). So `opening` includes transactions dated ON `periodStart`, which `getClientLedgerStatement` then itemises again (it fetches `startDate..endDate` inclusive).

The ledger subsystem itself already gets this right: `ClientLedgerService.getClientLedgerStatement` (`backend/.../trustaccounting/ledger/ClientLedgerService.java:188-190`) computes its own opening as `calculateClientBalanceAsOfDate(..., startDate.minusDays(1))` and its closing as opening + itemised lines (`:216-218`) — i.e. the `LedgerStatementResponse(openingBalance, closingBalance, transactions)` record (`:68-71`) is **self-reconciling by construction**. The SoA builder ignores those fields and recomputes both balances with the off-by-one-day window.

Prior cycle didn't expose this because its first deposit fell after the period start date.

## Fix
In `StatementOfAccountContextBuilder.buildTrustBlock` (lines 529-535), delete the two `getClientBalanceAsOfDate` calls and take both balances from the statement the method already fetches:

```java
var statement =
    clientLedgerService.getClientLedgerStatement(
        customer.getId(), trustAccountId, periodStart, periodEnd);
BigDecimal opening = statement.openingBalance();
BigDecimal closing = statement.closingBalance();
```

(Adjust the `TrustBlock` construction at lines 557-561 accordingly; the null-guards can stay or drop — `LedgerStatementResponse` balances are never null since `calculateClientBalanceAsOfDate` COALESCEs to 0.)

This is the elegant fix: one source of truth, opening/deposits/payments/closing can never drift again, and it removes two redundant DB queries. Do NOT instead patch by passing `periodStart.minusDays(1)` into the existing calls — that fixes the number but leaves two parallel balance computations that can drift.

Note: the SoA `trust.closing_balance` also feeds `StatementSummary.trustBalanceHeld` (line 316-323) — unchanged in value (inclusive-of-periodEnd closing equals statement closing), so the persisted snapshot stays consistent.

## Scope
Backend only.
Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java`.
Test: extend `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilderTest.java` — seed a deposit whose `transactionDate == periodStart`, build the context, assert `trust.opening_balance == 0`, the deposit appears exactly once in `trust.deposits`, and `opening + Σdeposits − Σpayments == trust.closing_balance` (the self-reconciliation invariant). This reproduces the bug red-first per the reproduce-before-fix rule.
Migration needed: no.

## Verification
- New failing-then-green test above (this is the local reproduction).
- Full `bash scripts/verify.sh` (production-behaviour change → full backend suite per Quality Gates §5; `StatementRenderingIntegrationTest` / `StatementControllerIntegrationTest` in the same package also cover this path).
- Live re-run of Day 60 close + Day 61.4: regenerate a SoA for a matter whose first deposit is on the period start date; PDF must print Opening R0,00 and self-reconcile to the printed closing.

## Estimated Effort
M (30 min – 2 hr) — small code change, test is the bulk.
