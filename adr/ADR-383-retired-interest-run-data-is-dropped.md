# ADR-383: Retired interest-run tables, rates and transaction types are dropped, not kept read-only

**Status**: Accepted

**Context**:

[ADR-375](ADR-375-statement-driven-trust-interest.md) removes the calculated interest run. That leaves data behind in every tenant schema: the tables `interest_runs`, `interest_allocations`, `interest_allocation_lines` and `lpff_rates`; `trust_transactions` rows of type `INTEREST_CREDIT` and `INTEREST_LPFF` with their `REVERSAL` rows; and `trust_investments` rows created under the old model, where a placement was a `PAYMENT` that took the money off the books ([ADR-379](ADR-379-investment-accounting-on-the-books.md)).

Some of this data cannot be carried into the new model as it stands. `INTEREST_LPFF` has a cashbook effect and no ledger, so it cannot satisfy the posting CHECK `chk_trust_txn_posting` introduced by [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md). `INTEREST_CREDIT` rows credit clients with interest that vests in the Fund (L1, High) and are the source of the measured R 25,52 by which the cashbook sits below the client ledgers on the e2e stack. A legacy investment has no investment account to belong to.

The requirements settle the facts the decision turns on. Founder decision 5: "No real tenant has posted an interest run. Only demo, e2e and dev data exist. No remediation tooling is built; affected environments are reset." Founder decision 6: the run is removed, not kept as an estimate. Constraints: "No production data to preserve… default is drop", and no real tenant has placed an investment. Ruling R12 confirms drop.

**Options Considered**:

1. **Keep the tables read-only, with a historical screen.**
   - Pros: Nothing is lost; a past run can be inspected.
   - Cons: It preserves entities, repositories, a page and tests for figures Kazi now says were wrong in law, in a product with no tenant that has any. The retired transaction types would have to stay in the type CHECK and be special-cased out of every sum.
2. **Keep the tables and rows but hide them, converting interest postings into Fund-ledger entries by migration.**
   - Pros: Cashbook history is continuous; the books balance after conversion without deleting a posted row.
   - Cons: The conversion invents entries: a calculated client credit is not interest the bank paid to the Fund, and no statement line backs it. It is remediation tooling by another name, which founder decision 5 rules out, written and tested for dev data only.
3. **Export to an archive file per tenant, then drop.**
   - Pros: The schema is clean and a record survives.
   - Cons: An export format, a storage location per tenant and a retention rule, for data that exists only on demo, e2e and dev stacks that are reset anyway.
4. **Drop outright (chosen; R12, founder decisions 5 and 6).**
   - Pros: No dead tables, no retired types in any constraint, no special cases in balance queries. The smallest migrations and the least code to verify.
   - Cons: Irreversible. Safe only while the premise holds that no real tenant has posted a run or placed an investment.

**Decision**: Option 4 — `interest_runs`, `interest_allocations`, `interest_allocation_lines` and `lpff_rates` are dropped; rows of type `INTEREST_CREDIT` and `INTEREST_LPFF` and their reversals are deleted and both names leave `chk_trust_txn_type`; all legacy `trust_investments` rows are deleted by V21 while their placement payments and withdrawal deposits stay as the ordinary payments and deposits they were posted as; and no read-only archive and no remediation tooling is built.

**Rationale**:

The decision is safe for one reason only, and the ADR records it so that the reason is checked before the migrations ever meet real data: no real tenant has posted an interest run or placed an investment. Given that, every alternative spends build and verify effort on preserving calculations that the legal basis says should never have been posted. Option 2 is the only one that keeps the books continuous, and it does so by fabricating Fund entries with no bank evidence, the opposite of what this phase is for.

The work is split across three tenant migrations so that each one leaves a schema that satisfies its own constraints, and each ships a schema test that applies it to the old shapes it claims to handle:

- **Slice 646A** deletes the interest postings and their reversals, after unlinking them from `interest_allocations`, `interest_allocation_lines` and any matched `bank_statement_lines`, and resets `POSTED` runs to `APPROVED`. It runs first because the posting CHECK cannot be added while an `INTEREST_LPFF` row exists. The guard from slice 645A already stops new ones.
- **Slice 648A** (V21) deletes all legacy `trust_investments` rows: none can be carried over, because under the old model no investment account exists for any of them. Their placement `PAYMENT` and withdrawal `DEPOSIT` rows stay as the ordinary payments and deposits they were posted as, so every existing balance is unchanged and the equation still holds. The delete is guarded by the absence of the new `investment_account_id` column, so it can never run against new-model rows.
- **Slice 651A** drops the four tables, children first with no `CASCADE`, once statement-driven interest and the interest-and-charges schedule exist. `idx_trust_txn_project`, created alongside those tables, is kept because the matter debit guard uses it.

Every migration runs per tenant schema and must still *succeed* on a schema holding demo-seeder data and on a walked dev schema holding posted runs, investments and reconciliations; where old rows cannot be carried forward they are deleted and the migration's header says which and why. That is the whole of data handling in this phase. Dev and e2e environments are reset. Both `V1__baseline.sql` files stay immutable.

Audit history is the one thing deliberately not touched. Audit rows are never rewritten, so `interest_run` and `lpff_rate` events remain; their entity links no longer resolve, and the audit UI already renders an unknown entity as plain text.

**Consequences**:

- Positive:
  - After 651A no table, type, rate or setting from the calculated run exists in any tenant schema, and no balance query special-cases a retired type.
  - `chk_trust_txn_posting` can hold for every posted row.
  - Seeded and walked dev stacks migrate cleanly; no backfill runner or remediation tool is built or maintained.
- Negative:
  - Irreversible data loss on any schema that holds interest runs. If the premise is ever false for a real tenant, the migrations must not be applied until that tenant's data is dealt with by hand. The phase's closure report carries a short written correction procedure for such a case; nothing more is built.
  - On a walked dev schema, deleting posted `INTEREST_CREDIT` rows changes client balances by the interest that had been credited. That is the intended correction; if such a client had already paid the interest out, the derived balance could fall below zero and would show as an integrity flag, not as a migration failure.
  - Portal read-model rows for deleted interest postings linger until that matter is next re-projected; stacks are reset in any case.
  - Audit entries for interest runs and LPFF rates point at entities that no longer exist.
  - Reconciliations completed before the phase keep their stored figures, which were calculated on the old equation.

**Related**: [ADR-375](ADR-375-statement-driven-trust-interest.md) (why the run is removed), [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md) (the posting CHECK the old rows cannot satisfy), [ADR-379](ADR-379-investment-accounting-on-the-books.md) (why legacy investments cannot be carried forward), [ADR-366](ADR-366-interest-credits-allocated-to-matters.md) (superseded; its index survives), [ADR-234](ADR-234-interest-daily-balance-method.md), [ADR-362](ADR-362-interest-run-draft-approval-surface.md); [architecture §16.2.12, §16.3.14, §16.7.1, §16.7.2, §16.7.7, §16.7.10](../architecture/phase92-trust-books-provably-right.md); slices 646A, 648A, 651A.
