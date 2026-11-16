# ADR-377: The trust equation, as at a date, and where it is enforced: row constraint, test harness, integrity check, reconciliation gate — never a block on posting

**Status**: Accepted

**Context**:

Nothing in Kazi or its tests asserts that the trust books balance. The evidence at `main` `8440d3cf4` (paths relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`):

- An interest run leaves the cashbook **R 25,52 below the client ledgers** on the e2e stack, and no test or screen reported it ([ADR-375](ADR-375-statement-driven-trust-interest.md)).
- The demo seeder writes four `RECORDED` transactions and no ledger card: cashbook R27 000,00, stored card total R0,00.
- `reconciliation/TrustReconciliationService.calculateReconciliation` (`:607-678`) balances only when adjusted bank = cashbook = client ledger total, and none of those figures is bounded by the period: `calculateCashbookBalance`, the outstanding-items pair and `calculateTotalTrustBalance` take only a trust account id. A reconciliation for a past `periodEnd` compares that statement's closing balance with today's books.
- No existing reversal test asserts cashbook = card total afterwards.

The requirements' objective 4 is one equation that "holds after every posting path, is asserted by tests, and is visible to the firm": trust bank + trust investment accounts (adjusted for outstanding items) = cashbook = client ledgers + client investment creditors + non-client trust creditors. L12 (Medium) says the auditor agrees reconciliations to total trust creditors and inspects suspense ledgers and debit balances. Founder decision 7 fixes the enforcement style: "tests + a visible check. No hard block on day-to-day posting." The requirements also state that no rule in this phase may stop a firm recording a transaction that appears on its bank statement.

**Options Considered**:

1. **A hard block**: refuse any posting that would leave the books out of balance.
   - Pros: The books can never be observed out of balance. The strongest guarantee on paper.
   - Cons: Founder decision 7 rules it out. A block on imbalance stops the entries that would correct it, and can stop a firm recording what its bank statement shows. The blocking conditions are programming defects, not user errors, so the firm would be refused a deposit at month end for a bug it cannot fix.
2. **A deferred database constraint or trigger** that checks the account's sums at commit.
   - Pros: Enforced below the application; no code path can forget it.
   - Cons: Still a hard block, with the same objections. It runs an aggregate over the account's rows on every commit, must be installed in every tenant schema by migration, and puts the rule in PL/pgSQL where the Java tests and the registry cannot see it. It cannot know the bank side at all.
3. **A nightly batch check with a notification.**
   - Pros: No cost on the posting path; simple to add.
   - Cons: Late by up to a day, invisible at the moment the firm completes a reconciliation, and it needs a scheduler across tenant schemas for a figure that is cheap to compute on read.
4. **Tests plus a visible check plus a completion gate, with a row-shape CHECK (chosen).**
   - Pros: Defects are caught before release by the harness; anything that escapes is shown to the firm with both totals and the difference; a reconciliation cannot be completed on books that do not balance; posting is never refused.
   - Cons: The books *can* be out of balance in a running system. Four mechanisms to keep, and the harness must be extended by every slice that changes posting.
5. **Tests only.**
   - Pros: The least to build.
   - Cons: Nothing is visible to the firm or its auditor, and a defect that the tests miss is silent, which is the state the phase starts from.

Two narrower choices were weighed:

- **Figures "as at now" or "as at the period end" (ruling R1).** As at now is what HEAD does and is why a past period cannot be reconciled truthfully. As at the period end costs a date bound on every query and makes every term derivable for any date.
- **A match dated by the statement line's date or by the time the match was made.** By line date, a payment matched in November to a November line is still outstanding as at 31 October, and re-running October after November is matched gives October's answer. By match time, October's figures would change whenever later matching is done.

**Decision**: Option 4 — the equation (EQ-1 to EQ-6, architecture §16.1.2) is defined per trust account and firm-wide with every term computed as at a date, including outstanding items dated by the statement line's date, and is enforced at four levels (the row-shape CHECK `chk_trust_txn_posting`, the independent test harness `TrustBooksHarness` asserted after every posting path, the visible `TrustIntegrityService` check on the dashboard, and the gate G1–G4 on completing a reconciliation), and it never blocks the recording of a transaction, nor do warning flags block completion.

**Rationale**:

Each level catches what the one before cannot. The row CHECK makes a posted row move the cashbook and one ledger by the same signed amount, or move a ledger only. It is a shape check, the row-level half of EQ-2 (cashbook = trust creditors): a row with the wrong sign, or on the wrong ledger for its type, passes it, and EQ-6 with the harness covers those. Given the shape, EQ-2 can fail only if cashbook-neutral rows on an account do not sum to zero. The creditor side is one definition of six terms used everywhere, `clientLedgers + investmentCreditors + fundLedger + unidentified + otherNonClient + interAccount`; `otherNonClient` is always zero in this phase and is a term so that a future creditor type is counted from its first row. EQ-4 (every posted unidentified-ledger row carries its item, each item's balance as at the date is 0 or its amount, and the ledger equals the sum of the items open by that test), EQ-5 (per investment) and EQ-6 (stored effects match the registry) are not structural, and EQ-1 (adjusted bank = cashbook) cannot be known to any constraint. The harness covers those in tests, with its own SQL so that a mistake in `TrustBooksService` cannot certify itself, and it asserts the size of each move as well as the balance, because a posting that moves nothing also "balances". The integrity check and the gate show the firm the same terms in production. A CHECK is per row and per tenant schema and costs nothing measurable; the aggregate checks run on read, not on the posting path.

The gate sits on completing a reconciliation because that is the moment the firm asserts the month is right, and because completion at a month end freezes the monthly list ([ADR-382](ADR-382-monthly-trust-creditor-list-locking.md)). It recalculates first and completes only if EQ-1 (G1), EQ-2 (G2), EQ-4 and EQ-5 (G3) hold as at the period end and no statement line is `UNMATCHED` (G4). All four conditions are evaluated and a refusal is given once, with every failed condition in one response: the title is that of the first failing condition in the order G1 → G4 and the detail carries one sentence per failing condition, naming each side's total and the difference. A firm fixing a month end needs the whole list, and the usual case, an unrecorded line, fails G1 and G4 together. Completion is not a posting, so refusing it does not stop the firm recording anything.

The period end is the date the firm chooses for the reconciliation, not the statement's own `period_end`, which the parsers derive from the last line's date, the last movement and not the end of the period. `bank(A,D)` is the statement's `closing_balance` carried forward unchanged to `D`. That is sound because creation refuses a statement with a line dated after `D`, and refuses when another imported statement of the account has movements between this statement's last line and `D`. EQ-1 is therefore keyed on a reconciliation's period end.

Warnings do not block, each on its own footing. `FUND_LEDGER_IN_DEBIT` is a warning by founder decision 9 and assumption A1 ([ADR-381](ADR-381-bank-charges-and-fund-ledger-debit.md)). `UNIDENTIFIED_OVER_ONE_YEAR` (L9, High), `CHARGES_TO_REFUND` and `BACKDATED_AFTER_LOCK` are information the firm acts on, none of which makes the books unequal. `CLIENT_LEDGER_IN_DEBIT` is not covered by decision 9 or A1, which concern the Fund ledger only, and the rule numbers the requirements' E5 gives for it are not among positions L1–L12. Whether a client ledger in debit must be corrected or acknowledged before completion is an open legal point (OL-7) and an open founder ruling (architecture §16.12.2 #5); this ADR proceeds on OL-7's working assumption, flag only, and if the answer differs the change is one condition in the gate and a boolean on the complete request. All flags are stored on the reconciliation and frozen into the monthly list.

As-at figures are required by more than reconciliation: the monthly list locks against a reconciled balance and the as-at trust position serves the auditor's second test date. A lock built on a comparison of an October statement with November's books would freeze a figure that was never true, which is why R1 lands in slice 646B and not with the reports. "As at D" means the books as now known for dates up to D; what was known when a month was closed is answered by the locked list.

**Consequences**:

- Positive:
  - Every posting path has a test that fails if it breaks the equation or moves the wrong amount. The list of known failures lives in the test tree as pinned defects, not in a document.
  - The firm sees "Books balance" or the two totals and the difference on the trust dashboard, with flags and links.
  - A reconciliation for a past month uses that month's figures and gives the same answer whenever it is re-run, apart from entries later dated into it.
  - A reconciliation cannot complete with an unresolved statement line or an imbalance.
- Negative:
  - A posting defect that escapes the harness reaches production and is reported, not prevented. The firm can keep posting on books that show a blocking flag.
  - EQ-1 is checkable only at a reconciliation's period end, and a reconciliation needs a statement with no line dated after that date. Reconciling from a typed bank balance is not built.
  - The harness is a second implementation of the sums and must be kept in step by hand; every posting slice pays for extending it.
  - The gate may refuse completion for a reason the firm cannot fix itself (G2, G3). The message says what differs, but the remedy is a software fix.
  - The row CHECK does not catch a wrong sign or a wrong ledger for a type; that class rests on EQ-6 in the harness and the registry being the only writer.
  - `TrustDomainEvent.ReconciliationCompleted` remains unpublished; the list lock is a direct call.

**Related**: [ADR-376](ADR-376-non-client-trust-creditor-ledger.md) (the non-client terms), [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md) (effects on the row; what makes EQ-2 nearly structural), [ADR-379](ADR-379-investment-accounting-on-the-books.md) (investment terms and the inter-account term), [ADR-381](ADR-381-bank-charges-and-fund-ledger-debit.md) (allow and flag), [ADR-382](ADR-382-monthly-trust-creditor-list-locking.md) (what completion freezes), [ADR-233](ADR-233-bank-reconciliation-matching.md) (matching, unchanged), [ADR-358](ADR-358-product-wide-sa-business-clock.md) (every "today"); [architecture §16.1.2, §16.3.3, §16.3.4, §16.3.9, §16.9.1, §16.9.2](../architecture/phase92-trust-books-provably-right.md); slices 649A, 646B, 649C.
