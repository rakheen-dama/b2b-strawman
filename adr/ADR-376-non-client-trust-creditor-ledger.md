# ADR-376: A trust creditor ledger for non-clients: typed accounts per trust account, itemised unidentified deposits

**Status**: Accepted

**Context**:

Kazi has no way to hold trust money for anyone who is not a client. The only client-less row the schema allows is `INTEREST_LPFF` (`chk_trust_txn_customer_required`, `backend/src/main/resources/db/migration/tenant/V1__baseline.sql:1819`), and no Fund ledger, suspense account or unidentified-deposits concept exists in Java or SQL. Two kinds of money need one:

- **The Fund's interest and the bank charges set off against it.** L5 (Medium-high): while the Fund's interest sits in a trust account, the Fund is a trust creditor that is not a client; it appears in the creditor list and the reconciliation, one ledger per trust bank account. A4 (assumption): that ledger carries both interest and charges.
- **Deposits whose owner is unknown.** L9 (High): such money is held as trust money, itemised and aged, and no fees or payments may be taken from it.

Without a ledger for these, the cashbook can never equal the creditor side once the bank credits interest or an unknown deposit arrives: the cashbook rises and no ledger does. The requirements (E2) ask for typed accounts per trust account, two types shipped, a model that admits further types without a schema change, and the client-required constraint reworked to "client or non-client creditor account, never both, never neither".

**Options Considered**:

1. **No table: a `ledger` value per non-client kind on the transaction** (`FUND`, `UNIDENTIFIED`).
   - Pros: No new entity, repository or foreign key. Balances are a `SUM` with one predicate.
   - Cons: Every further kind is a new value in a checked column, which is a schema change E2 forbids. There is no row to carry a display name, or to stand for a later type that allows several accounts per trust account (a named third party). Splitting interest from charges, if A4 proves wrong, would mean a constraint swap in every tenant schema.
2. **Model the Fund and "Unidentified" as system rows in `customers`**, so the existing client ledger carries them.
   - Pros: Reuses the client ledger, its screens, reports and constraint unchanged. The least code.
   - Cons: A fake client leaks into, or must be filtered out of, every client list, the portal, fee notes, FICA and MCP. `PAYMENT` and `FEE_TRANSFER` could target it, so "no fees or payments from unidentified money" (L9) would rest on service checks instead of holding by construction. `customers` is firm-wide while L5 wants one ledger per trust bank account. A Fund ledger in debit would read as a client in debit.
3. **A general chart of accounts and double-entry journal for trust.**
   - Pros: The most general model; any future account type fits.
   - Cons: It replaces the whole posting model for a requirement that names two account types. It would run beside `trust_transactions` or force its rewrite, and every backend slice of the phase would carry that cost in its full verify. Nothing in the requirements needs it.
4. **Typed creditor accounts per trust account, with itemised unidentified deposits (chosen).**
   - Pros: One small table gives each non-client creditor an id, a type and a name per trust account. The type is validated in Java, so a further type needs no schema change. Unidentified deposits are rows with a status, so the ledger balance is always the sum of open items and an age analysis is a query.
   - Cons: A new table, new foreign keys on `trust_transactions`, and a second record (the item) that must stay in step with the ledger rows.

Two narrower choices were weighed inside option 4:

- **Per-trust-account or firm-wide Fund ledger.** Per account follows L5 and A4 and lets each account's reconciliation balance on its own. A firm-wide ledger would leave every individual account out of balance by its share. Whether charges on one account are ever set off against interest on another is open (OL-9); the working assumption is no.
- **Whole-item or splittable unidentified deposits.** Whole-item: one bank credit has one payer. A firm that must spread it over two matters allocates it to one and uses the existing transfer. Splitting would need item lines and a partly-resolved state for no stated requirement.
- **"Open as at a date" from the item's status columns, from a resolution-history table, or from the item's own ledger rows.** Status columns only describe now: an allocation dated the 10th and undone on the 20th leaves the item `HELD` with `resolved_date` null, so a status rule would call it open on the 15th while the ledger shows it allocated. A history table would be a second record of what the transactions already say, and one more thing to keep in step. Deriving it from the rows that carry the item's id makes both sides of EQ-4 come from the same rows, so they agree at every date. From the rows.

**Decision**: Option 4 — a `trust_creditor_accounts` table holds typed creditor accounts per trust account (`FUND_INTEREST_AND_CHARGES` and `UNIDENTIFIED_DEPOSITS` ship), each transaction targets exactly one ledger under `chk_trust_txn_ledger_target`, the `account_type` column carries no CHECK so a further type needs no schema change, unidentified deposits are itemised one row per bank credit in `unidentified_deposits`, and every balance is derived.

**Rationale**:

The row constraint is what makes the ledger trustworthy. `chk_trust_txn_ledger_target` replaces `chk_trust_txn_customer_required` and states, per value of `ledger`, which of `customer_id`, `project_id`, `creditor_account_id` and `investment_id` must and must not be set: a `NON_CLIENT` row has a creditor account and no client or matter; a `CLIENT` row has a client and no creditor account. Because `PAYMENT` and `FEE_TRANSFER` are `CLIENT`-ledger types in the registry ([ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md)), neither can touch a non-client ledger, so L9's "no fees or payments" holds without a service check. Option 2 cannot give that guarantee and option 1 gives it only until the next type needs a migration. Each tenant schema gets the table and both accounts for every existing trust account from the 646B migration; new trust accounts get them from `TrustAccountService.createTrustAccount`. There is no tenant column and no cross-schema read.

`account_type` is the one status-like column in the module without a DDL CHECK, on purpose, and the entity Javadoc says why. The partial unique index `uq_trust_creditor_account_singleton` covers only the two shipped types, so a later type that allows several accounts per trust account needs no index change either. If A4 is wrong and an auditor wants interest and charges on separate ledgers, the change is a second `TrustCreditorAccountType` and a `creditorAccountType` edit on `BANK_CHARGE` and `CHARGE_REFUND` in the registry: naming and one enum, no table change.

Itemising is what L9 asks for and what keeps the unidentified ledger honest. The only debits on it are `UNIDENT_ALLOC_OUT` and `UNIDENT_FUND_PAY`, each for exactly one `HELD` item's amount and each moving that item out of `HELD` under a row lock, so that ledger cannot go into debit. Both resolutions are firm-initiated and follow the account's maker-checker rule ([ADR-380](ADR-380-statement-line-classification-and-posting.md)). The item keeps its `bank_statement_line_id` for life.

Every row that moves an unidentified-deposits ledger names its item through `trust_transactions.unidentified_deposit_id`. The CHECK `chk_trust_txn_unidentified_item` requires it on `UNIDENTIFIED_DEPOSIT`, `UNIDENT_ALLOC_OUT` and `UNIDENT_FUND_PAY`; it is copied onto `UNIDENT_ALLOC_IN` and, by `TrustPostingService.postReversal`, onto their `REVERSAL` rows (a CHECK cannot force it there, so the harness asserts it). An item's balance as at `D` is `SUM(ledger_effect)` over its posted `NON_CLIENT` rows dated on or before `D`, served by `idx_trust_txn_unidentified_deposit`; the item is open as at `D` exactly when that balance equals its `amount`. `resolved_date` describes the present only: null for `HELD`, the allocation rows' date for `ALLOCATED`, the payment row's date for `PAID_TO_FUND`, and for `REVERSED` the date of the contra row that reversed the opening deposit (the line's date); it is cleared when an allocation or payment is undone and is never used to decide whether an item was open at a past date. Because the opening row needs the item's id, the item is saved first, so `unidentified_deposits.deposit_transaction_id` is nullable, null only inside the creating transaction.

A fourth status, `REVERSED`, beyond the requirements' three, exists so that unmatching the line that opened an item does not delete the item and leave the audit trail pointing at nothing.

One departure from the requirements' wording is recorded in the architecture and confirmed here: `chk_trust_txn_ledger_target` has a fourth case, `INTER_ACCOUNT`, with neither a client nor a creditor account. It carries section 86(3) placements, which change no client's balance and are not owed to a non-client creditor ([ADR-379](ADR-379-investment-accounting-on-the-books.md)). No third non-client creditor type is built.

**Consequences**:

- Positive:
  - The Fund's interest and unidentified deposits have a ledger, so the creditor side can equal the cashbook.
  - The reconciliation, the dashboard and the monthly list show the creditor side broken down: client ledgers, Fund interest and charges, unidentified deposits.
  - Unidentified deposits can be aged, flagged after one year, allocated to a client and matter, or paid to the Fund, each with its original bank line attached.
  - A further creditor type is an enum constant and a registry entry.
- Negative:
  - EQ-4 is not structural: every posted row on the unidentified ledger must carry its item, each item's balance must be 0 or its amount, and as at today its status must agree with that balance. The harness and the integrity check (`UNIDENTIFIED_ITEMS_MISMATCH`) guard this ([ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md)); the CHECK covers three types only.
  - One more nullable column, foreign key and partial index on `trust_transactions`, and two tables that reference each other (`unidentified_deposits.deposit_transaction_id` and `trust_transactions.unidentified_deposit_id`), which fixes the insert order.
  - A column without a CHECK depends on Java validation. A row written outside the application could carry an unknown type; the position query counts such rows under `other_non_client` so they are not silently dropped.
  - An item cannot be split. Spreading one deposit over two matters takes an allocation and a transfer.
  - When an unidentified amount may or must be paid to the Fund is not enforced (OL-4, open): Kazi shows the item's age and warns. Whether an identified deposit is credited on the date of identification or of receipt is a working assumption (OL-6: identification).

**Related**: [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md) (EQ-4 and the flags), [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md) (registry and derived balances), [ADR-380](ADR-380-statement-line-classification-and-posting.md) (how Fund and unidentified rows are created), [ADR-381](ADR-381-bank-charges-and-fund-ledger-debit.md) (charges and a Fund ledger in debit), [ADR-232](ADR-232-configurable-dual-authorization.md) (maker-checker for allocation and Fund payment); [architecture §16.2.2, §16.2.4, §16.2.5, §16.3.6, §16.7.4, §16.7.6](../architecture/phase92-trust-books-provably-right.md); slices 646B, 647B.
