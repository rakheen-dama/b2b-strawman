# 646B — out-of-scope finding (not built here)

**Owner slice:** 647A (the first slice that posts a `NON_CLIENT` row).
**Status:** recorded per the orchestrator ruling of 2026-11-16; no code change in 646B.

## Finding

`TrustPostingService.postReversal` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustPostingService.java`, `postReversal` / `contraOf`) builds a `REVERSAL` row by copying the original's id onto `reversal_of` and applying `contraOf(original)`, which carries the original's **ledger** (`TrustLedgerEffect.ledger`). The caller constructs the `REVERSAL` row through the 11-argument `TrustTransaction` constructor, so it carries the original's `customer_id` / `project_id`. Nothing copies `creditor_account_id` or `investment_id` (646B added both columns with getters only and no setters).

V18 (646B) adds `chk_trust_txn_ledger_target`:

- `NON_CLIENT` requires `creditor_account_id IS NOT NULL` and `customer_id IS NULL AND project_id IS NULL`;
- `CLIENT_INVESTMENT` and `INTER_ACCOUNT` require `investment_id IS NOT NULL`.

So once 647A posts a `NON_CLIENT` row (for example `FUND_SWEEP` or `BANK_CHARGE`) or 648A posts a `CLIENT_INVESTMENT` / `INTER_ACCOUNT` row, reversing it through `postReversal` would save a contra with `ledger = NON_CLIENT` (or an investment ledger) and a null `creditor_account_id` (or `investment_id`). The database would refuse it with SQLState 23514 on `chk_trust_txn_ledger_target`.

## Not reachable in 646B

646B adds no posting path for any new type. Every posted row is `ledger = 'CLIENT'` with a client, and a `CLIENT` row's contra needs neither column. Nothing in main code can produce the failing shape yet.

## What 647A must do

When 647A adds the mutator for `creditorAccountId` (and 648A the one for `investmentId`), `postReversal` must copy both from the original onto the contra, together with the ledger target. 647A should pin this with a test that reverses a posted `NON_CLIENT` row and asserts the contra carries the original's `creditor_account_id`. The equivalent `investment_id` test belongs in 648A.
