# ADR-382: The monthly trust creditor list is frozen when the month's reconciliation completes; back-dated postings are recorded and flagged against it

**Status**: Accepted

**Context**:

L10 (High) describes the Rule 54.15 list as monthly: every person for whom money is held, by name, totalled and compared with trust bank + trust investment + trust cash. Kazi's nearest thing is the `client-trust-balances` report, which at `main` `8440d3cf4` lists clients only, from stored ledger cards, as at now. Nothing is kept of what a month's list showed when the month was reconciled. Two properties of the current model make a past month unstable: reconciliation figures are not bounded by the period end, and a reversal removes its original from every sum, so a balance as at an earlier date changes after the fact. [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md) and [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md) fix both, so a month's figures can be recomputed as the books are now known. That still does not answer "what did the firm compare and sign off on the day?".

The requirements (E6) ask that completing the month's reconciliation freezes the list as a stored artefact with the comparison result, and that later back-dated postings do not alter it and are flagged against it. A competing constraint applies: no rule in this phase may stop a firm recording a transaction that appears on its bank statement, and a bank can show, or a firm discover, a movement dated in a closed month. A5 (assumption) concerns retention: seven years, or five on the older text.

**Options Considered**:

1. **No artefact**: recompute the list as at the month end on demand.
   - Pros: Nothing to store; always consistent with the books.
   - Cons: A back-dated posting silently changes last month's list, and nothing records what was compared when the reconciliation was completed. It does not meet E6.
2. **A period lock that refuses postings dated in a locked month.**
   - Pros: A locked month's figures can never move; no "adjustments" concept is needed.
   - Cons: It stops a firm recording a bank movement dated in a closed month, which the requirements forbid. The firm would post it with a false date or not at all, and either makes the cashbook stop mirroring the bank.
3. **Store only the totals and recompute the names.**
   - Pros: A small row; the comparison result is kept.
   - Cons: The list is the names and their balances. Recomputed lines would drift from stored totals after any back-dated posting or client rename, and the artefact would no longer agree with itself.
4. **Store the rendered PDF as the artefact.**
   - Pros: Exactly what was seen, in the form an auditor reads.
   - Cons: Opaque: the after-lock schedule, the Section 35 pack and the firm-wide report cannot query it. It ties the evidence to a template version and adds a file-storage dependency to completing a reconciliation.
5. **An immutable structured snapshot, with after-lock adjustments shown beside it (chosen).**
   - Pros: The list is frozen as data, queryable and renderable. Back-dated entries are recorded normally and are visible against the month they affect.
   - Cons: A locked list can differ from the live figures for the same date, and both must be explained to the user. Names and balances are duplicated into the snapshot.

Two narrower choices were weighed:

- **Per trust account or firm-wide locking.** Completion is per account, and a list can only be frozen against a balance that was reconciled. A firm-wide lock would have to wait for every account or freeze unreconciled figures. Per account, with the firm-wide report assembling every account's list for the month.
- **A synchronous call or publishing `ReconciliationCompleted`.** The lock must be atomic with completion: a completed month-end reconciliation with no list, or a list for a completion that rolled back, are both wrong. A direct call in the same transaction gives that; an after-commit listener does not.

**Decision**: Option 5 — completing a reconciliation whose period end is a month end writes, in the same transaction, an immutable `trust_monthly_lists` row for that trust account holding the reconciled money side, every creditor by name with balances, the totals, the comparison result and the flags; a later posting dated in a locked month is recorded normally, never changes the locked row, and is shown against it as an after-lock adjustment (`posted_at` later than `locked_at`) and as the integrity flag `BACKDATED_AFTER_LOCK`; and locked lists are never edited, re-locked or deleted.

**Rationale**:

The two requirements pull in opposite directions and only option 5 satisfies both: evidence that does not change, and books that can always take what the bank shows. A list that changes after it was compared and signed off is not evidence of what was compared, so the row is written once. A posting that is refused because of its date makes the cashbook wrong, so nothing is refused. The link between the two is one comparison on data that already exists: a row is back-dated against list `L` when `transaction_date <= L.period_end` and `posted_at > L.locked_at`. No lock column, no period table and no check on the posting path are needed. Posting dialogs warn before the fact using `lockedThrough` on the trust account, and the Rule 54.15 report prints the locked list followed by a clearly separate "Entered after this list was locked" schedule.

The row can only be written by a balanced reconciliation: `completeReconciliation` passes the gate of [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md) first, and the table carries `CHECK (difference = 0)` and a month-end CHECK on `period_end`. A reconciliation to any other day completes without a lock. The month a list locks for is the reconciliation's `period_end`, the date the firm chose, never the statement's last line date: a statement whose last movement is on the 30th still locks October when the firm reconciles to 31 October, with the statement's closing balance carried forward to that date ([ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md)). Because every term is computed as at the period end, the frozen figures are the figures that were true for that date when the firm completed, which is why this decision depends on period-bounded reconciliation landing first. Reconciliations completed before this phase get no list: their figures were calculated as at "now", and freezing them would freeze a wrong comparison.

`lines` and `integrity_flags` are `jsonb` because the artefact is written once and read whole; no query joins to a line. Names are frozen with it on purpose. What is frozen: every client with a non-zero general balance with a per-matter breakdown, every client with a non-zero invested balance, the Fund interest-and-charges line (shown even at zero or in debit), each open unidentified deposit individually, and the inter-account line where non-zero. Whether Rule 54.15 requires the list per person only or per matter, and what "each balance noted in the ledger" requires of the system, is an open legal point (OL-8); the working assumption is per person with the per-matter breakdown frozen alongside, and a different answer adds a write in `TrustMonthlyListService.lock`, not a model change. Rows are never updated or deleted, so A5 holds whichever retention period is right.

The table lives in each tenant schema like every other trust table; there is no cross-tenant artefact. The lock adds no approval step: completion is not a posting, and any member with `MANAGE_TRUST` completes, as today.

**Consequences**:

- Positive:
  - Each month end has a stored list that states its own comparison result, who locked it and when, and it never changes.
  - A back-dated entry is never refused and never hidden: it appears as an after-lock adjustment, on the report and as a dashboard flag.
  - The firm-wide Rule 54.15 report can say per account whether its list is locked or live.
  - Locked lists hold exactly the opening figures a future balance snapshot would need ([ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md)).
- Negative:
  - There is no reopen and no unlock. A list locked on a mistake stays, with the correction shown beside it.
  - A locked list and the live position for the same date can differ, and users must understand which they are reading.
  - A month is locked only if a reconciliation is completed to its last day; a firm that reconciles mid-month only gets no list for that month.
  - A firm-wide list is fully locked only when every account's is.
  - A memo record may be saved or corrected for a locked month, because it is not part of the list ([ADR-384](ADR-384-memo-records-for-at-source-sweeps.md)).
  - `TrustDomainEvent.ReconciliationCompleted` stays unpublished; anything that later wants to react to completion must add the publisher.

**Related**: [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md) (the gate that precedes the lock; as-at figures), [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md) (`posted_at`; contra reversals keep history stable), [ADR-376](ADR-376-non-client-trust-creditor-ledger.md) and [ADR-379](ADR-379-investment-accounting-on-the-books.md) (the lines the list contains), [ADR-380](ADR-380-statement-line-classification-and-posting.md) (unmatch in a locked month), [ADR-356](ADR-356-pack-content-reconciliation.md) (report pack bump); [architecture §16.2.10, §16.3.4, §16.3.10, §16.7.9, §16.8.1](../architecture/phase92-trust-books-provably-right.md); slice 650A.
