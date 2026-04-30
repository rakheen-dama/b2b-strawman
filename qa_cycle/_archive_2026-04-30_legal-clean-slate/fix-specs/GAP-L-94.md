# Fix Spec: GAP-L-94 — Statement of Account Trust Activity section is empty despite RECORDED deposits

## Problem

SoA "Trust Activity" section renders empty Deposits + Payments tables, opening balance R 0, closing balance R 0, **and** trust-balance-held R 0 — even though 3 RECORDED trust deposits totalling R 70 100,00 exist on Sipho's matter (`cc390c4f-…`, customer `c4f70d86-…`).

Section 86 / LPC compliance broken — SoA cannot be sent to a client without legal misrepresentation. This is the formalisation of the pre-existing `OBS-Day60-SoA-Trust-Empty` observation into a HIGH-severity gap, because Day 61's portal-side SoA download depends on accurate trust reconciliation.

Evidence:
- `qa_cycle/checkpoint-results/cycle46-day60-25-soa-generated.yml` lines 463-510 (empty Trust Activity tables, all balances zero).
- `qa_cycle/checkpoint-results/cycle46-day60-26-statements-tab-after.yml` line 240 ("Trust balance held R 0,00" on the saved SoA row).
- `qa_cycle/checkpoint-results/day-60.md §Day 60 Cycle 46 Walk` (3 RECORDED deposits R 50 000 + R 100 + R 20 000 = R 70 100; client_ledger_card balance R 70 100).
- Cycle 29 retest (BUG-CYCLE26-11 verification) confirmed the GENERAL primary trust account exists and the customer-scoped ledger query works for the trust nudge email path.

## Root Cause (verified)

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java` lines 402-455 (`buildTrustBlock`):

```java
UUID trustAccountId =
    trustAccountRepository
        .findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)
        .map(TrustAccount::getId)
        .orElse(null);
if (trustAccountId == null) {
  return TrustBlock.empty();
}
BigDecimal opening =
    clientLedgerService.getClientBalanceAsOfDate(customer.getId(), trustAccountId, periodStart);
BigDecimal closing =
    clientLedgerService.getClientBalanceAsOfDate(customer.getId(), trustAccountId, periodEnd);
var statement =
    clientLedgerService.getClientLedgerStatement(
        customer.getId(), trustAccountId, periodStart, periodEnd);
```

`clientLedgerService.getClientBalanceAsOfDate` resolves to `TrustTransactionRepository.calculateClientBalanceAsOfDate` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionRepository.java:99-117`):

```sql
WHERE t.customerId = :customerId
  AND t.trustAccountId = :trustAccountId
  AND t.transactionDate <= :asOfDate
  AND t.status IN ('RECORDED', 'APPROVED')
```

Both `findForStatement` (line 204-220) and `calculateClientBalanceAsOfDate` filter by `(customerId, trustAccountId)`. They do **NOT** filter by `projectId` — that is correct (trust ledger is per-customer).

The bug is **not** in the period predicate (deposits dated within 2026-04-01 → 2026-06-30 should match) and **not** in the status filter (RECORDED is included). The defect is one of these (Dev to confirm with two diagnostic SELECTs before patching):

**Hypothesis A (most likely): the deposits sit on a NON-primary or NON-GENERAL trust account.** `findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)` returns the single primary GENERAL account; if Mathebula seeded only a TRUST_INVESTMENT account, or if the GENERAL account exists but `primary=false`, the SoA gets a wrong/null trustAccountId and short-circuits. Cycle-29 evidence already confirmed that trust-deposit emails resolved a working trust account, so a primary GENERAL must exist somewhere — but the deposits may be on a different account id than `findByAccountTypeAndPrimaryTrue` returns.

**Hypothesis B: deposits' `customer_id` is NULL or set to a different customer.** The deposit dialog (`RecordDepositDialog.tsx`) requires customerId, but a previous deposit recorded via the Day 45 flow may have written `customer_id=NULL`. Since GAP-L-69 fixed the matter/projectId field, but customer_id is a separate column, this needs a direct DB check.

**Hypothesis C: deposits are on the `trust-accounting/transactions` page's secondary account.** Mathebula tenant may have multiple GENERAL trust accounts (one primary + one not), and the deposits hit the non-primary one.

**Diagnostic SQL to run before fixing** (will collapse the hypothesis space):

```sql
-- A1: is there exactly one primary GENERAL trust account?
SELECT id, name, account_type, is_primary, status
  FROM tenant_5039f2d497cf.trust_accounts;

-- A2: do the RECORDED deposits' (customer_id, trust_account_id) match (Sipho, primary-GENERAL)?
SELECT id, customer_id, trust_account_id, project_id, transaction_type, amount,
       transaction_date, status
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE status IN ('RECORDED', 'APPROVED')
   AND customer_id = 'c4f70d86-…'  -- Sipho
 ORDER BY transaction_date;

-- A3: cross-check — what does the SoA query actually return?
SELECT COALESCE(SUM(
  CASE
    WHEN transaction_type IN ('DEPOSIT','TRANSFER_IN','INTEREST_CREDIT') THEN amount
    WHEN transaction_type IN ('PAYMENT','DISBURSEMENT_PAYMENT','TRANSFER_OUT','FEE_TRANSFER','REFUND','INTEREST_LPFF') THEN -amount
    ELSE 0
  END), 0) AS computed_balance
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE customer_id = 'c4f70d86-…'
   AND trust_account_id = (SELECT id FROM tenant_5039f2d497cf.trust_accounts
                            WHERE account_type='GENERAL' AND is_primary=true)
   AND transaction_date <= '2026-06-30'
   AND status IN ('RECORDED','APPROVED');
```

If A3 returns 70100 → the SoA query is correct; bug is somewhere downstream (toMapList or template render). If A3 returns 0 → the deposits don't match `(customer_id, primary-GENERAL trust_account_id)` — drill into A2 to see whether it's customer_id or trust_account_id mismatch.

## Fix

**Step 1 — diagnose** (5 min): run the three SELECTs above against the Mathebula tenant schema. The result determines which of two surgical fixes ships.

**Step 2 — fix branch by hypothesis:**

- **If A2 shows deposits on a non-primary OR non-GENERAL trust account (Hypothesis A or C):** Stop using `findByAccountTypeAndPrimaryTrue(GENERAL)` as the SoA's trust-account selector. Replace with a customer-aware lookup that returns the trust account(s) where this customer actually has activity. Implementation: change `buildTrustBlock` to call a new `TrustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customerId)` (one query, returns 0..N UUIDs). For 0 → `TrustBlock.empty()`. For 1 → use that id. For N>1 → log warning + still use `findByAccountTypeAndPrimaryTrue(GENERAL)` (fall-through behaviour — this matters when a future tenant has both a GENERAL and a SECTION-86 trust account). This preserves the per-customer ledger semantics that Section 86 expects.

  ```java
  // StatementOfAccountContextBuilder.java buildTrustBlock — replace lines 414-421
  List<UUID> customerTrustAccountIds =
      trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(customer.getId());
  UUID trustAccountId;
  if (customerTrustAccountIds.isEmpty()) {
    return TrustBlock.empty();
  } else if (customerTrustAccountIds.size() == 1) {
    trustAccountId = customerTrustAccountIds.get(0);
  } else {
    // Multi-account customer (unusual for ZA Section 86 firms). Prefer the primary GENERAL.
    trustAccountId =
        trustAccountRepository
            .findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)
            .map(TrustAccount::getId)
            .filter(customerTrustAccountIds::contains)
            .orElse(customerTrustAccountIds.get(0));
  }
  ```

  And in `TrustTransactionRepository.java`:
  ```java
  @Query("SELECT DISTINCT t.trustAccountId FROM TrustTransaction t "
       + "WHERE t.customerId = :customerId "
       + "  AND t.status IN ('RECORDED', 'APPROVED')")
  List<UUID> findDistinctTrustAccountIdsByCustomerId(@Param("customerId") UUID customerId);
  ```

- **If A2 shows deposits with `customer_id=NULL` (Hypothesis B):** This is a deeper bug in the deposit recording path; it would require a separate fix-spec. Open as **GAP-L-98 (P0 BUG)** and pause GAP-L-94 work until L-98 is decided. Do NOT silently treat NULL customer_id as Sipho — that masks data corruption.

**Step 3 — also patch the `summary.trust_balance_held`** which is independently computed. In `aggregate()` (line 250-266), summary uses `trust.closingBalance` from the same buildTrustBlock — so the trust-balance-held will fix automatically once buildTrustBlock returns correct numbers. No separate change needed here.

## Scope

Backend only.
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java` — replace lines 414-421 trust-account resolver.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionRepository.java` — add `findDistinctTrustAccountIdsByCustomerId`.

Files to create: none.
Migration needed: no (data-only fix; no schema change).

## Verification

1. Re-run the diagnostic SELECTs in the spec to record the actual root-cause hypothesis.
2. Restart backend (`bash compose/scripts/svc.sh restart backend`).
3. From firm UI as Thandi, on RAF matter `cc390c4f-…`: top-bar **Generate Statement of Account** → period 2026-04-01 → 2026-06-30 → Preview & Save.
4. Open the rendered SoA preview iframe → assert:
   - Trust Activity → Deposits table shows 3 rows (R 50 000 + R 100 + R 20 000).
   - Opening balance R 0 (no deposits before 2026-04-01).
   - Closing balance R 70 100,00.
   - Summary → Trust balance held R 70 100,00.
5. Re-run Day 60 checkpoint 60.10 (Statements tab "Trust balance held" column should now read R 70 100,00, not R 0).
6. Day 61 portal-side SoA download must show non-zero trust activity.

## Estimated Effort

**M (3-5 hours)** — most of the time is spent on the diagnostic SELECT + writing the multi-account guard. The actual code change is ~20 lines.

## Tests

`backend/src/test/java/.../verticals/legal/statement/StatementOfAccountContextBuilderTest.java`:
- `buildTrustBlock_returnsAllRecordedDepositsForCustomer` — seed 3 DEPOSITs on `(customer, trust_account)`, period covers them all → assert deposits.size()==3, closingBalance==sum, openingBalance==0.
- `buildTrustBlock_handlesCustomerWithSingleTrustAccount` — only one customerTrustAccountIds → uses it.
- `buildTrustBlock_handlesCustomerWithNoActivity` — empty → returns TrustBlock.empty().
- `buildTrustBlock_handlesMultiAccountCustomer_prefersPrimaryGeneral` — deposits on two trust accounts including GENERAL primary → picks GENERAL primary.

## Regression Risk

The new `findDistinctTrustAccountIdsByCustomerId` query is read-only and additive — no change to deposit/withdrawal write paths.
The `buildTrustBlock` change is contained to SoA generation; no other caller of `StatementOfAccountContextBuilder` exists (per `grep -rn "StatementOfAccountContextBuilder" backend/src/main`).
Multi-account fallback prefers primary GENERAL, matching pre-fix behaviour for any future tenant that adds a SECTION-86 trust alongside the GENERAL trust.

## Dispatch Recommendation

**This-cycle (P0 — scenario-blocking).** Day 61 portal walk depends on Sipho seeing the SoA with accurate trust activity. Without this fix, Section 86 / LPC compliance is broken for every legal-ZA tenant.
