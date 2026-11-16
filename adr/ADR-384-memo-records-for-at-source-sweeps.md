# ADR-384: Memo records carry Rule 54.28 figures for interest and charges that never reach the trust account

**Status**: Accepted

**Context**:

[ADR-375](ADR-375-statement-driven-trust-interest.md) makes the cashbook a mirror of the bank statement: interest, Fund sweeps and bank charges are recorded only from statement lines ([ADR-380](ADR-380-statement-line-classification-and-posting.md)). That leaves a gap wherever the statement does not show them. L3 (High): the bank pays the Fund's interest by automated monthly transfer, with recoverable charges set off and the net paid. L4 (Medium; low per bank): how each bank lays this out is not published and differs by bank; on section 86(2) accounts interest and charges *generally* post to the trust account and the net is swept visibly. So a bank may show the gross interest and the sweep, or only a net credit, or nothing at all on the trust statement.

Reporting does not have the same gap. L11 (High): Rule 54.28 requires gross interest earned and gross charges levied to be reported per section 86(2), (3) and (4) account, even where no charges claim is made. Where the bank swept at source, those gross figures exist (on a bank advice or a Fund statement) and are not in the cashbook, because there is no line to record them from. The requirements (E3, E6) call for a memo record per account and month in that case.

**Options Considered**:

1. **Post a wash pair**: interest received and a Fund sweep on the same day, so the figures live in the cashbook.
   - Pros: One source for the schedule; no second kind of record. The pair nets to zero, so the books still balance.
   - Cons: Both rows are movements the trust statement does not show. They would sit as outstanding items that can never be matched, or need a special exemption from the outstanding query, and bank-originated types can only be created from a statement line. It breaks "the cashbook mirrors the statement" for the sake of a report.
2. **Free-text notes on the reconciliation.**
   - Pros: No new table; the note travels with the month's reconciliation.
   - Cons: Text cannot be totalled into a schedule, and Rule 54.28 wants figures per account. A note also ties the figures to a reconciliation existing and being editable.
3. **Parameters typed into the report at run time.**
   - Pros: Nothing stored; the report still prints the right totals.
   - Cons: Nothing is recorded, so the same report run twice can differ, the Section 35 data pack cannot reproduce it, and there is no audit trail of who supplied the figures.
4. **A structured memo record per account and month, outside the ledger (chosen).**
   - Pros: The figures are stored, attributable and totalled, and they cannot affect any balance. The cashbook stays a mirror of the statement.
   - Cons: A second source feeds the schedule, so the same interest could be both posted and memoed. The figures are typed by the firm with only a source note as evidence.

**Decision**: Option 4 — where interest and charges never reach the trust account nothing is posted to the cashbook, and a `trust_interest_memos` row per trust account and month records `gross_interest`, `gross_charges` and `charges_vat` with a `source_note`, has no effect on any balance, and is read only by the Rule 54.28 schedule and the Section 35 data pack, which show posted and memo figures in separate columns.

**Rationale**:

The memo exists so that two rules can both hold without bending either: the cashbook contains only what the statement shows, and the schedule contains everything Rule 54.28 asks for (L11). Option 1 satisfies the second by breaking the first, and the phase's central test, a reconciliation that balances against the statement, would then depend on rows the statement can never confirm. Options 2 and 3 keep the cashbook clean but do not produce a recorded figure. A structured record does both, and because it is not a transaction it needs none of the posting machinery: no type in the registry, no effect columns, no reversal, no approval. Maker-checker is not applied because no money moves; saving needs `MANAGE_TRUST` and is audited as `trust_interest_memo.saved` with the previous values on an update.

The shape follows from how the figures are used. One row per `(trust_account_id, period_month)`, edited in place, because the schedule is per account and month. `period_month` is the first day of the month by CHECK. `source_note` is required so the figure always says where it came from. A memo may be saved or corrected at any time, including for a month whose list is locked, because it is not part of the locked list and moves no term of the equation ([ADR-382](ADR-382-monthly-trust-creditor-list-locking.md)); the harness asserts exactly that with `Expected.none()`.

The duplication risk in option 4's cons is handled by presentation, not by a rule. The schedule shows "in the cashbook" and "at source (memo)" in separate columns and never adds them silently, so a firm that both records the interest line and memos the same interest sees it twice on the page. Kazi cannot tell which layout a given bank uses (L4), so it does not try to block one when the other exists. The mixed case is covered the same way: where a bank shows a single net credit (interest less charges), the firm records that line as `FUND_INTEREST` for the net amount and puts the gross figures in the memo; Kazi does not gross up a line.

This ADR depends on L4, whose confidence is Medium and low per bank. If it turns out that every bank a design partner uses shows gross interest and the sweep on the trust statement, the memo is simply never used and nothing else changes. If banks commonly show nothing, the memo carries most firms' section 86(2) figures and its evidential weakness, a typed number with a note, matters more and would need a follow-up decision. The table sits in each tenant schema with a foreign key to `trust_accounts`; it has no cross-tenant aspect.

**Consequences**:

- Positive:
  - The Rule 54.28 schedule can be complete for a bank that sweeps at source, without a single cashbook row the statement does not show.
  - Memo figures are stored, audited and reproducible in the Section 35 data pack.
  - No effect on any balance, the reconciliation or the monthly list, by construction.
- Negative:
  - The schedule has two sources. Posted and memo columns can overlap, and only the reader catches it.
  - Memo figures are unverified by Kazi: there is no statement line behind them, and the source note is free text.
  - A memo can be changed after the month's list is locked. The audit event records it, but the locked list does not.
  - Fund sweeps made at source have no matched statement line, so the Section 35 pack's sweep evidence cannot show one for those months.
  - One memo per account and month: a firm with several advices in a month must total them.

**Related**: [ADR-375](ADR-375-statement-driven-trust-interest.md) (nothing is calculated; the cashbook mirrors the statement), [ADR-380](ADR-380-statement-line-classification-and-posting.md) (what is recorded when a line does exist), [ADR-381](ADR-381-bank-charges-and-fund-ledger-debit.md) (charges that do reach the account), [ADR-382](ADR-382-monthly-trust-creditor-list-locking.md) (memos sit outside the locked list), [ADR-356](ADR-356-pack-content-reconciliation.md) (report pack 3 → 4); [architecture §16.2.9, §16.3.13, §16.7.9, §16.8.3](../architecture/phase92-trust-books-provably-right.md); slice 650B.
