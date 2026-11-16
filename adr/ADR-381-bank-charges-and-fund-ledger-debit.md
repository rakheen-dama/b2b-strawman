# ADR-381: Bank charges debit the Fund ledger with a recoverable flag and VAT; a Fund ledger in debit is allowed and flagged

**Status**: Accepted

**Context**:

Kazi has no bank-charges transaction type (`chk_trust_txn_type`, tenant `V1__baseline.sql:1821`), so a charge the bank debits to a trust account cannot be put in the cashbook at all. The legal basis describes how charges relate to the Fund's interest:

- **L3 (High):** recoverable bank charges are set off against section 86(2)/(3) interest and the net is paid to the Fund.
- **L6 (Medium):** only charges on the LPFF's recoverable list offset interest; VAT vendors deduct net of VAT; where charges exceed interest the firm funds the shortfall from business.
- **A4 (assumption):** the Fund ledger is one per trust bank account and carries both interest and charges.

Charges and interest do not arrive in a convenient order. In the phase's worked exit month, two charges (R 115,00 recoverable and R 57,50 non-recoverable) post on 30 October and the month's interest (R 1 234,56) on 31 October, so as at 30 October the Fund interest-and-charges ledger is **R 172,50 in debit** while the books still balance at R 102 957,50. The requirements (E3) say non-recoverable charges and VAT are expected to be redirected to the business account by the bank, and where the bank debits trust anyway Kazi records it and flags it for the firm to refund from business.

Whether that transient debit is acceptable is not settled law. It is **assumption A1**: a transient debit on the Fund ledger is recorded as it happened and flagged, not blocked; the requirements note that the LPFF's June 2020 notice treats charges as a set-off on the same account and is silent on timing, and that no carve-out from Rule 54.14.9 was found. Founder decision 9 fixes the product behaviour: "Fund ledger in debit: allow, but flag. Kazi records what the bank did and warns; it never blocks the posting and adds no firm-float concept."

**Options Considered**:

1. **Block a charge or sweep that would put the Fund ledger in debit.**
   - Pros: The ledger is never in debit; the strictest reading of A1's open question.
   - Cons: Contradicts founder decision 9 and the constraint that no rule may stop a firm recording a transaction on its bank statement. The bank has already taken the money, so the block would only make the cashbook wrong and the month unreconcilable until interest arrives.
2. **A firm-funded float entry type** that keeps the ledger non-negative.
   - Pros: The ledger never shows a debit, and the firm's funding is explicit.
   - Cons: Out of scope by founder decision 9 "unless an auditor requires it". It asks the firm to record a movement the bank statement does not show, or to make a real transfer in advance of every charge. It is the fallback if A1 is wrong, not the starting point.
3. **Post non-recoverable charges to a separate "firm to refund" creditor account.**
   - Pros: The Fund ledger shows only what concerns the Fund; the firm's shortfall has its own balance.
   - Cons: That account would be a "creditor" that is always in debit, a third creditor type the requirements say is not built. Which ledger a charge lands on would depend on the recoverable flag being right at entry, so correcting the flag means moving money between ledgers. A4 assumes one ledger carrying interest and charges.
4. **Allow and flag on the one ledger (chosen; founder decision 9, A1).**
   - Pros: The cashbook mirrors the bank whatever order the lines arrive in. One ledger, one flag for the debit, one flag for what the firm owes. No new account type.
   - Cons: A trust ledger can show a debit balance without anything stopping it. The Fund ledger mixes money owed to the Fund with a shortfall the firm must make good, separated only by flags and the schedule.

Two narrower choices were weighed:

- **VAT as a separate transaction, or as a column on the charge.** The bank shows one debit, so one row mirrors the line; `charge_vat_amount` records the VAT included in it. A separate transaction would split one bank line into two rows for a figure only reports need.
- **One type or two for a bank's refund of a charge and a firm's top-up from the business account.** On the statement both are a credit that restores the Fund ledger, and the firm cannot reliably tell Kazi which it is. One type, `CHARGE_REFUND`.

**Decision**: Option 4 — every bank charge on a trust account is recorded as it appears as a `BANK_CHARGE` that debits that account's Fund interest-and-charges ledger and carries `charge_recoverable` and `charge_vat_amount`; a non-recoverable charge raises the `CHARGES_TO_REFUND` flag until `CHARGE_REFUND` credits cover it; and a Fund ledger in debit is allowed and raises the `FUND_LEDGER_IN_DEBIT` warning, never blocking a posting or a reconciliation, with no firm-float concept.

**Rationale**:

The Fund ledger is the only ledger on a trust account that is not a client's, and the bank has taken the money, so the charge has to land there whether it is recoverable or not (E3). Because of that, `FUND_SWEEP` and `BANK_CHARGE` carry no sufficiency check. The books stay balanced throughout: each row moves the cashbook and the Fund ledger by the same amount, and the worked month shows the equation holding on the day the ledger is R 172,50 in debit. The debit is a fact about timing, reported by `FUND_LEDGER_IN_DEBIT`; what the firm owes trust is a different fact, reported by `CHARGES_TO_REFUND` as the excess of non-recoverable charges over refunds. Both are warnings under the enforcement model of [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md): shown on the dashboard, stored on the reconciliation, frozen into the monthly list, and never a block.

This decision rests on A1, which is an assumption to be confirmed with a practising trust auditor, not a legal position. The design is chosen so that a different answer changes configuration, not the data model. If a transient debit is not acceptable, the remedy is a firm-funded float entry type on the same ledger (one registry constant: `NON_CLIENT`, Fund, credit), or making the flag require an acknowledgement at completion (one boolean on the complete request). Neither needs a table change or a migration of posted rows, in any tenant schema. If A4 is wrong and interest and charges need separate ledgers, the change is a second `TrustCreditorAccountType` and a registry edit ([ADR-376](ADR-376-non-client-trust-creditor-ledger.md)).

The two charge fields sit on `trust_transactions` and not in a side table because they are two scalars read with the row by the Fund ledger screen and the interest-and-charges schedule; `chk_trust_txn_charge` makes them non-null exactly for `BANK_CHARGE` and keeps the VAT between zero and the amount. The schedule reports gross charges whether or not a claim is made (L11, High), split recoverable and non-recoverable, with VAT. How the VAT on a *recoverable* charge should be treated, and whether it depends on the firm's VAT status, is an open legal point (OL-3): the working assumption is that VAT is captured and reported and `CHARGES_TO_REFUND` counts non-recoverable charges only; a different answer is a query change. Whether charges on one account are ever set off against interest on another is also open (OL-9); each account keeps its own Fund ledger.

Charges are bank-originated, so they are recorded from the statement line without approval ([ADR-380](ADR-380-statement-line-classification-and-posting.md)); a mis-set recoverable flag is corrected by unmatching the line and recording it again.

**Consequences**:

- Positive:
  - Bank charges reach the cashbook on the bank's date, so a month with charges can reconcile.
  - The firm sees how much it must refund to trust from the business account, and the flag clears when the refund is recorded.
  - Gross charges, the recoverable split and VAT are available for the interest-and-charges schedule from the postings themselves.
  - If A1 or A4 is answered differently, the change is a registry constant, an enum value or one request field.
- Negative:
  - A debit balance on a trust ledger is possible and persists until interest or a refund arrives. If A1 is wrong, firms will have recorded months in that state before the float or the acknowledgement exists.
  - Kazi does not check a charge against the LPFF's recoverable list; the flag is the firm's assertion (L6 is Medium confidence and the list is not held).
  - The Fund ledger's balance is not purely "owed to the Fund" while non-recoverable charges are unrefunded.
  - `CHARGE_REFUND` does not distinguish a bank's refund from a firm's top-up.
  - Correcting the recoverable flag or the VAT means unmatch and re-record; there is no edit.

**Related**: [ADR-231](ADR-231-negative-balance-prevention.md) (client balances: the sufficiency rule for firm-initiated client debits is unchanged; this ADR concerns the Fund ledger only), [ADR-376](ADR-376-non-client-trust-creditor-ledger.md) (the ledger), [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md) (warnings never block), [ADR-380](ADR-380-statement-line-classification-and-posting.md) (how charges are recorded), [ADR-384](ADR-384-memo-records-for-at-source-sweeps.md) (charges levied at source); [architecture §16.2.2, §16.2.4, §16.3.5, §16.3.9, §16.9.6 step 14, §16.14.1](../architecture/phase92-trust-books-provably-right.md); slice 647A.
