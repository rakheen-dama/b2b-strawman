# ADR-379: Investments stay on the books: investment trust accounts, client investment creditor balances, gross and net section 86(4) interest

**Status**: Accepted

**Context**:

At `main` `8440d3cf4`, placing a trust investment takes the money off the books. In `investment/TrustInvestmentService.java` (under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`):

- placement posts a `PAYMENT` (`:140-150`) that on approval debits the client's ledger card (`transaction/TrustTransactionService.java:1035-1036`), so the invested money leaves the client's trust balance and the cashbook while invested;
- `recordInterestEarned` (`:188-228`) only increments the hand-typed `trust_investments.interest_earned`; no transaction is written;
- `withdrawInvestment` (`:230-298`) posts one `DEPOSIT` of principal plus that typed figure;
- no 5% Fund share exists on this path.

The legal basis says otherwise:

- **L7 (High):** invested money remains a trust creditor balance for the client, and the trust investment account counts on the money side. Removing it from the client's balance at placement is a known inspection finding.
- **L2 (High):** section 86(4) interest goes 95% to the client and 5% to the Fund. **L1 (High):** section 86(3) interest is the Fund's.
- **L8 (Medium):** section 86(4) interest is recognised when the bank credits the investment account, and investment proceeds return through the trust banking account.
- **A2 (assumption):** section 86(4) interest may arrive gross, with the 5% debited separately, or net of the 5%.
- **A3 (assumption):** Kazi keeps the per-investment figures a firm needs for IT3(b) and does not file.

The existing QA step 60.16 asserts that the trust balance is *reduced* by a placement, which is the finding L7 describes.

**Options Considered**:

1. **Today's model**: placement is a payment; the money is off the books until withdrawn.
   - Pros: No change. One account, one cashbook.
   - Cons: Contradicts L7. The client's balance and the monthly creditor list understate what the firm holds, the investment account is on no side of any equation, and interest has no posting.
2. **Keep the money on the client's general ledger and track the investment as a memo "earmark"** with no second account.
   - Pros: The client's balance is right with almost no posting change.
   - Cons: The trust banking account's cashbook would then exceed its bank balance by the amount invested, permanently, so that account could never reconcile. The investment account's statement, interest and 5% have nowhere to be recorded. Earmarked money would still look available for a fee transfer.
3. **A separate investment sub-ledger keyed by investment, with no `trust_accounts` row.**
   - Pros: Models the investment directly; no new account type semantics.
   - Cons: The investment account is a bank account with a statement, a cashbook, a Fund share and reporting figures of its own. A sub-ledger would need its own statement import, matching, reconciliation and as-at queries, duplicating what `trust_accounts` already gives.
4. **Investment accounts as trust accounts, with paired placement and return rows (chosen).**
   - Pros: Statement import, matching, reconciliation, the Fund ledger and the as-at queries work on an investment account unchanged. The money side is literally "trust bank + trust investment accounts". The client's total is unchanged by placement.
   - Cons: Every placement and withdrawal is two rows on two accounts that must be kept paired. Eight new transaction types. The firm must create an investment account before placing, and reconcile it.

For interest, three records were weighed. **A typed running total** (today) has no posting, no date and no 5%. **A single net-only row** balances the cashbook but loses the gross and the Fund's share whenever the bank credits net, and those are the figures the Rule 54.28 schedule (L11, High) and the tax record (A3) need. **One entry per bank credit holding gross, the Fund's 5% and net, with a presentation** keeps all three whichever way the bank shows it.

**Decision**: Option 4 — a trust investment account is a `trust_accounts` row of type `INVESTMENT` carrying its `investment_basis`; a section 86(4) placement moves money from the client's general ledger on the banking account to a client investment creditor balance on the investment account by paired rows (`INV_PLACE` / `INV_RECEIVE`, returned by `INV_WITHDRAW` / `INV_RETURN`) so the client's total is unchanged; a section 86(3) placement moves cash between accounts through the `INTER_ACCOUNT` ledger (`FIRM_INV_*`) and changes no client's balance; section 86(4) interest is one `trust_investment_interest` entry per bank credit holding gross, the Fund's 5% and net with a `GROSS` or `NET` presentation deciding which rows reach the cashbook; and section 86(3) interest is the Fund's.

**Rationale**:

The equation's money side is "trust bank + trust investment accounts" (L7), so the investment account has to be something the reconciliation can see. Making it a `trust_accounts` row means each investment account is reconciled to its own statement and locked in its own monthly list by the same code as a banking account, and it gets its own Fund interest-and-charges ledger for the 5% ([ADR-376](ADR-376-non-client-trust-creditor-ledger.md)). The client investment creditor balance is not a table: it is `SUM(ledger_effect)` over posted rows on ledger `CLIENT_INVESTMENT` ([ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md)). A client's total is general plus invested, and placement moves an amount from one to the other. Each account satisfies cashbook = creditors after every step because each row moves its own account's cashbook and one ledger equally.

A section 86(3) placement is the firm's initiative over pooled surplus and changes no client's balance. The cash still leaves the banking account, so that account's cashbook falls below its client ledgers by the amount placed and the investment account's rises by the same. The `INTER_ACCOUNT` ledger records exactly that pair; the two terms cancel firm-wide (EQ-3) and are shown to the practitioner as "Placed in section 86(3) investment accounts" and "Held for the firm's trust creditors". This is why `trust_investments.customer_id` becomes nullable and why the ledger-target constraint has a case with neither a client nor a creditor account.

Maker-checker follows the direction of the money, as it did for the rows these replace: a placement moves money out of the trust banking account and needs approval under that account's mode ([ADR-232](ADR-232-configurable-dual-authorization.md)); a withdrawal brings it back and needs none; recording interest needs none because the bank credited it. Approval is a property of the type in the registry, which is why there are four placement and four return types and not two generic ones: `INV_PLACE` and `FIRM_INV_PLACE` are `REQUIRED`; `INV_WITHDRAW` and `FIRM_INV_WITHDRAW` are `NONE`; the receiving and returning legs (`INV_RECEIVE`, `FIRM_INV_RECEIVE`, `INV_RETURN`, `FIRM_INV_RETURN`) are `WITH_PAIR`, created and posted with their pair.

Three mechanics follow from the two-row model. First, the insert order: the placement row needs the investment's id (`chk_trust_txn_ledger_target` requires `investment_id` on `FIRM_INV_PLACE`, and it is wanted on `INV_PLACE`), so in one transaction the investment is saved `ACTIVE` with `deposit_transaction_id` null, the reference is generated from its id, the placement row is saved `AWAITING_APPROVAL` carrying `investment_id`, and `deposit_transaction_id` is then set. The column therefore becomes nullable, null only inside the placing transaction and never in a committed row. Second, references are generated by Kazi and the request has no reference field: `INV-{first 8 hex of the investment id}` on both rows of a placement and `INV-WD-{same 8 hex}` on both rows of a withdrawal, replacing today's `INV-{institution}-{accountNumber}`, which repeats for a second investment at the same institution. Each row is matched to its own account's statement line by the ordinary matcher; no new matching code. Third, the existing placement-reversal guard in `TrustTransactionService` (`:1272-1290`, an investment lookup on a `PAYMENT`) is replaced in 648A by the registry: `INV_PLACE` and `FIRM_INV_PLACE` have `reversal = NONE`, so the reversal is refused on the type with the same message. Withdrawal is whole-investment and its amount is the investment's derived balance, capital plus every posted net interest entry, so nothing is typed in (L8: the proceeds return through the trust banking account).

The interest entry exists because of A2. With `GROSS` the line's amount is split into `INV_INTEREST` (net, to the client's investment creditor balance) and `FUND_INV_SHARE` (to the investment account's Fund ledger), and the later 5% debit is recorded as a `FUND_SWEEP`. With `NET` only `INV_INTEREST` reaches the cashbook and the entry alone holds the gross and the 5% that went to the Fund at source. Both are supported, so if A2 is wrong in either direction nothing changes. The 5% is `TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT`, a constant, not a setting (L2); the service computes it half-up to the cent and accepts a typed Fund share within R0,01 so the bank's own rounding can be mirrored. That rounding rule is a working assumption (OL-5); a different answer changes a constant. The entry snapshots `customers.tax_number` as `client_tax_reference` (A3); if a filing or export feature is later required it reads the same table.

Two further points rest on working assumptions, not settled law. A `SECTION_86` trust account is treated as a trust banking account reporting under section 86(2) (OL-1); a different answer changes one mapping method. Several section 86(4) investments may share one investment account, with interest always recorded against a named investment (OL-2); a different answer adds one service check.

No real tenant has placed an investment, so all legacy `trust_investments` rows are deleted by the 648A migration (none can be carried over, because no investment account exists for any of them) and their old `PAYMENT` and `DEPOSIT` rows stay as the ordinary postings they were ([ADR-383](ADR-383-retired-interest-run-data-is-dropped.md)).

**Consequences**:

- Positive:
  - Invested money stays a trust creditor balance. The client ledger, the monthly list, the portal and the statement of account show general and invested balances, and the client's total does not move at placement.
  - Investment accounts are reconciled and locked like any trust account.
  - Each section 86(4) interest credit has a dated record of gross, Fund 5% and net, with the client's tax reference where held.
  - A client sees interest only from their own section 86(4) investments, labelled "Section 86(4) investment interest".
  - Consumers that ask "what can pay a fee note" read the general balance only; the matter closure gate reads general plus invested.
- Negative:
  - Paired rows across two accounts must stay in step; EQ-3 and EQ-5 are checked by the harness and the integrity check, not by a constraint.
  - A firm has more accounts to import statements for and reconcile. An investment evidenced only by a bank certificate must be keyed as a small CSV; a typed-balance reconciliation is not built.
  - Reference matching across the two statements works only if the firm quotes Kazi's generated reference on the transfer; otherwise the amount-and-date signals carry the match.
  - Placement and return rows cannot be reversed (registry reversal `NONE`; the refusal points to withdrawing the investment), and `INV_INTEREST` / `FUND_INV_SHARE` reverse only through their interest entry (`OWNER`, via `reverseInterest`), which is refused once the investment is withdrawn.
  - Partial withdrawal and setting an investment `MATURED` are not built.
  - QA steps 60.12–60.20 change meaning; their amendment needs founder authorisation (architecture §16.12.1).
  - The portal read-model gains a column, which needs a global migration ahead of the tenant one in the same deploy.

**Related**: [ADR-235](ADR-235-statutory-vs-configurable-lpff-share.md) (superseded; its constant is now applied here), [ADR-375](ADR-375-statement-driven-trust-interest.md), [ADR-376](ADR-376-non-client-trust-creditor-ledger.md), [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md) (EQ-3, EQ-5), [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md), [ADR-380](ADR-380-statement-line-classification-and-posting.md) (the `INVESTMENT_INTEREST` classification), [ADR-232](ADR-232-configurable-dual-authorization.md), [ADR-383](ADR-383-retired-interest-run-data-is-dropped.md); [architecture §16.2.7, §16.2.8, §16.3.7, §16.3.8, §16.5.2, §16.7.7, §16.7.8](../architecture/phase92-trust-books-provably-right.md); slices 648A, 648B.
