# ADR-375: Trust interest is recorded from the bank statement, never calculated; statutory shares are constants

**Status**: Accepted (supersedes [ADR-234](ADR-234-interest-daily-balance-method.md), [ADR-235](ADR-235-statutory-vs-configurable-lpff-share.md), [ADR-362](ADR-362-interest-run-draft-approval-surface.md) and [ADR-366](ADR-366-interest-credits-allocated-to-matters.md))

**Context**:

Kazi calculates trust interest. Paths below are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`, verified at `main` `8440d3cf4`.

- `interest/InterestService.java:272-345` works out a notional figure per client from daily ledger balances × `LpffRate.ratePercent` and splits it between the client and the Fund by `lpffSharePercent`, or by the statutory 5% (`:317-343`) applied to the client's *general* balance whenever the client has any `CLIENT_INSTRUCTION` investment.
- Posting (`:540-628`) writes one `INTEREST_CREDIT` per client-matter line (credited to the cashbook and to the client's ledger card, `:604`) and one client-less `INTEREST_LPFF` (`:611-629`).
- The gross interest is never recorded as received. The Fund's outflow is therefore posted for money the cashbook never took in, and no reconciliation can balance after a run. Measured on the e2e stack: the cashbook is **R 25,52 below the client ledgers**.
- Posting is live at HEAD: `postInterestRun` checks only the module, status `APPROVED`, period ended and account `ACTIVE`.

The legal basis in the requirements overturns the premise of ADR-234, which was that clients share the interest on general trust money:

- **L1 (High):** interest on section 86(2) and 86(3) money vests 100% in the Legal Practitioners' Fidelity Fund; clients receive none.
- **L2 (High):** only section 86(4) interest goes to the client, 95% to the client and 5% to the Fund.
- **L3 (High):** the bank pays the Fund's interest by automated monthly transfer, net of recoverable bank charges.
- **L4 (Medium; low per bank):** how each bank lays this out on the statement is not published and differs by bank.

The question is what replaces the run. The requirements add two constraints: "Kazi stays a mirror of the bank", and founder decision 6, "the calculated interest run is removed, not kept as an estimate".

**Options Considered**:

1. **Keep the calculated run and correct its recipients**: clients get nothing on general money; the run posts the gross as received, then the Fund's share as paid.
   - Pros: The smallest change to screens and to the firm's monthly routine. Figures are available before the statement arrives.
   - Cons: A calculated figure is not the bank's figure. The bank's rate, day count and set-off of charges (L3) are the bank's, and the statement layout is unknown per bank (L4), so the cashbook would differ from the statement by a small amount every month and need an adjusting entry to reconcile. It still posts something the bank did not do. It contradicts founder decision 6.
2. **Statement-driven posting, with the calculation kept as a non-posting estimate or expected-interest check.**
   - Pros: The firm gets a figure to compare with what the bank credited.
   - Cons: Founder decision 6 rules it out, and the requirements list "an interest estimator or expected-interest check of any kind" as out of scope. It keeps `lpff_rates`, the run tables, the wizard and their tests alive, with a rate the firm must maintain. Two interest figures would sit side by side for the same month, one of which Kazi posted as fact until this phase.
3. **Statement-driven only, as a firm-entered monthly figure with no link to a statement line.**
   - Pros: Works for every bank layout, including a bank that shows nothing on the trust statement. One small form.
   - Cons: A typed figure can be wrong or invented, and nothing ties it to evidence. The row stays an outstanding item until someone matches it by hand. The Section 35 pack could not show each Fund sweep with its statement line (L12, Medium: the auditor inspects bank statements for interest credited and tests that it was paid to the Fund).
4. **Statement-driven only, recorded from a classified statement line (chosen).**
   - Pros: The cashbook carries exactly what the bank did, on the bank's date, and the entry is matched to its line from the moment it exists. Nothing is calculated, so nothing can disagree with the statement. No rate is stored.
   - Cons: The firm must classify the interest and sweep lines each month. Kazi can no longer tell the firm what interest to expect. Where the bank sweeps at source there is no line to record from, so a second mechanism is needed for the reporting figures ([ADR-384](ADR-384-memo-records-for-at-source-sweeps.md)).

**Decision**: Option 4 — Kazi never calculates trust interest: interest on section 86(2) and 86(3) money is recorded only from a bank statement line as money held for the Fund (`FUND_INTEREST`) and leaves when the sweep line is recorded (`FUND_SWEEP`), interest on a section 86(4) investment is recorded per investment from what the bank credited, the Fund's 5% of section 86(4) interest is the code constant `TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT`, no interest rate or configurable share is stored, and the calculated run, its approval surface and its per-matter allocation are removed.

**Rationale**:

Under L1 no calculation can produce a client entitlement on general trust money, so the run's per-client output has no lawful use. What remains is the Fund's interest, and the bank both credits and sweeps it (L3). The only reliable source for those two figures is the statement. Options 1 and 2 keep a number Kazi worked out next to a number the bank produced, and the R 25,52 gap shows what happens when the books follow the first. Option 3 records the right kind of fact without its evidence. Option 4 is the only one where the entry cannot exist without the bank line it describes, which is what L12 says an auditor looks for, and it is the same record-from-statement action that bank charges and unidentified deposits need ([ADR-380](ADR-380-statement-line-classification-and-posting.md)), so interest adds two classifications to one mechanism and no flow of its own.

Removal is cheap here for a reason that will not always hold: no real tenant has posted an interest run (founder decision 5). There is no history to convert and no remediation tooling to build ([ADR-383](ADR-383-retired-interest-run-data-is-dropped.md)). The work is sequenced by risk and verify cost: slice 645A makes `postInterestRun` and `approveInterestRun` refuse on day one of the phase, and slice 651A deletes `T/interest/`, `T/lpff/`, the four tables, the page and wizard, and the `interest-allocation` report, once statement-driven posting and the interest-and-charges schedule are in place. Each tenant schema is migrated by the same tenant migrations; nothing is per-firm.

The 5% is a constant because L2 fixes it. ADR-235 reached the same conclusion for the same reason, and that constant survives; what goes is the configurable `lpffSharePercent` and the "client has any client-instructed investment" switch on general balances. The constant's Javadoc is re-pointed to [ADR-379](ADR-379-investment-accounting-on-the-books.md), which defines where it is applied.

**Consequences**:

- Positive:
  - Clients are never credited interest that vests in the Fund. A client sees interest only as `INV_INTEREST` rows on their own section 86(4) investments.
  - A reconciliation can balance in a month that carries interest: the interest line and the sweep line each become a matched cashbook entry.
  - Each Fund sweep is tied to its statement line and shown as such in the Section 35 data pack.
  - No rate table, no rate maintenance, no rounding or allocation rules to defend.
- Negative:
  - Kazi gives no expected-interest figure. A firm that wants to check the bank's interest does so outside Kazi.
  - Classifying lines is monthly manual work. Parser suggestions reduce it, but never post.
  - Between 645A and 651A the run's draft and calculate screens still exist behind a notice, with posting and approval refused.
  - Scenario steps that run or post an interest run become invalid. Their amendment needs explicit founder authorisation before any scenario file is edited (architecture §16.12.1).
  - The consumer plugin in `../claude-for-legal-sa` still mentions interest runs; the follow-ups are listed in architecture §16.6.5 and are not done in this phase.
- What survives from the superseded ADRs (their status lines were set when this ADR was accepted, not in a slice; slice 651A only updates `kazi-architecture/90-adr-index.md` and the remaining references):
  - ADR-234: nothing.
  - ADR-235: the constant `STATUTORY_LPFF_SHARE_PERCENT`.
  - ADR-362: `MyCapabilitiesResponse.memberId`, which has other consumers.
  - ADR-366: `idx_trust_txn_project` and the matter-cell queries, which the matter debit guard uses.

**Related**: [ADR-234](ADR-234-interest-daily-balance-method.md), [ADR-235](ADR-235-statutory-vs-configurable-lpff-share.md), [ADR-362](ADR-362-interest-run-draft-approval-surface.md), [ADR-366](ADR-366-interest-credits-allocated-to-matters.md) (all superseded by this ADR); [ADR-376](ADR-376-non-client-trust-creditor-ledger.md) (the Fund ledger the interest is held on), [ADR-379](ADR-379-investment-accounting-on-the-books.md) (section 86(4) interest per investment), [ADR-380](ADR-380-statement-line-classification-and-posting.md) (record from statement), [ADR-383](ADR-383-retired-interest-run-data-is-dropped.md) (the run's data is dropped), [ADR-384](ADR-384-memo-records-for-at-source-sweeps.md) (at-source sweeps); [architecture §16.3.1, §16.3.5, §16.3.14, §16.6.2](../architecture/phase92-trust-books-provably-right.md); slices 645A, 647A, 651A.
