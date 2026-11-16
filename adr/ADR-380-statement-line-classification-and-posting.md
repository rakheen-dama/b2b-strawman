# ADR-380: Statement lines are classified and posted from: transaction types, approval rules, unmatch semantics, parser suggestions

**Status**: Accepted (extends [ADR-233](ADR-233-bank-reconciliation-matching.md); applies [ADR-232](ADR-232-configurable-dual-authorization.md))

**Context**:

A bank statement line in Kazi can only be `UNMATCHED`, `AUTO_MATCHED`, `MANUALLY_MATCHED` or `EXCLUDED` (`chk_bank_line_match_status` in the tenant `V1__baseline.sql`; the field is in `reconciliation/BankStatementLine.java:44-54`). There is no line classification, and an unmatched or excluded line adjusts nothing in the reconciliation (`reconciliation/TrustReconciliationService.java`, exclude at `:486-512`). Movements the bank originates therefore have no route into the cashbook: there is no bank-charges transaction type, no type for interest received or swept that is not tied to the calculated run, and no way to take up a deposit whose owner is unknown. A month that contains any of them cannot reconcile unless the firm excludes the line, which leaves exactly that amount as a difference.

The legal basis describes what those lines are. L3 (High): the bank pays the Fund's interest by automated monthly transfer, net of recoverable bank charges. L4 (Medium; low per bank): how each bank lays this out on the statement is not published and differs by bank. L9 (High): money whose owner is unknown is held as trust money. Two constraints from the requirements bound the design: "Kazi stays a mirror of the bank" and "maker-checker stays", with the default that bank-originated lines the firm did not initiate post as recorded and matched, while allocating an unidentified deposit to a client needs approval. The requirements also rule out auto-posting from statements without firm confirmation.

One technical fact shapes the names: `transaction_type` is `varchar(20)` in the tenant table `trust_transactions` and in the global read-model table `portal.portal_trust_transaction`, and the requirements' suggested `LPFF_INTEREST_RECEIVED` is 22 characters.

**Options Considered**:

1. **No new action**: the firm records a manual transaction of a new type, then matches it to the line.
   - Pros: Reuses the existing record and match screens. No new line state.
   - Cons: Two steps for one fact, and the amount and date are retyped, so they can differ from the line. A "record bank charge" dialog lets a firm post a charge that is on no statement. Nothing ties the entry to its evidence until someone matches it.
2. **Auto-post from recognised narrations**, with a review queue.
   - Pros: The least monthly work for the firm.
   - Cons: Ruled out by the requirements. Narrations are unpublished and differ by bank (L4), so recognition would be guesswork posting to trust books; a wrong auto-posting has to be found and reversed after the fact.
3. **Record-from-line, with approval required for every created posting.**
   - Pros: A uniform rule; a second member sees every statement-derived entry.
   - Cons: The bank has already moved the money. Approval cannot undo that; it only delays the cashbook from mirroring the statement, and the reconciliation would not balance until every interest and charge line was approved.
4. **Record-from-line; bank-originated postings unapproved, firm-initiated ones approved (chosen).**
   - Pros: One action creates the entry from the line's own amount and date and links it. The cashbook mirrors the statement at once. Maker-checker stays where the firm is making a decision about whose money it is or moving money out.
   - Cons: A single member with `MANAGE_TRUST` can post interest, sweep, charge and unidentified-deposit entries alone. A wrong classification is found by review, not by a second approval.

Three narrower choices were weighed:

- **Widen `transaction_type` in both schemas, or keep names within 20 characters (ruling R5).** Widening needs a tenant migration, a global migration and an entity-length change, and buys only longer names. Short names (`FUND_INTEREST`, `FUND_SWEEP`) need none; the short form appears only in type codes and every label a person reads uses the long form, Legal Practitioners' Fidelity Fund.
- **Unmatch as a hard delete of the created rows, or as a contra reversal.** Delete is simple but removes a posted row and its audit target. A contra through the normal reversal path keeps both.
- **Per-bank narration rules, or one shared keyword table.** Per-bank rules would be guesses given L4's low per-bank confidence. One table of obvious keywords is honest about what is known.

**Decision**: Option 4 — an unmatched statement line can be recorded from: the firm confirms a classification and Kazi creates the transaction dated the line's date and linked to the line, which becomes `RECORDED`; approval follows the registry's `approval` attribute exactly (architecture §16.2.1) — bank-originated postings need none and post `RECORDED`, firm-initiated postings that take money out of a trust banking account or move it to another creditor on the firm's say-so (payment, fee transfer, refund, investment placement on both bases, allocation of an unidentified deposit, payment to the Fund) follow the account's maker-checker rule, and the remaining firm-originated types need none, each for a stated reason; unmatching a recorded line reverses what it created through the normal reversal path as a same-date contra; parser suggestions come from one shared keyword table, are stored on the line and never post; type names fit `varchar(20)`; and no capability is added.

**Rationale**:

`StatementLinePostingService.recordFromLine` is the only way a bank-originated type comes into existence (registry `origin = BANK`: `FUND_INTEREST`, `FUND_SWEEP`, `BANK_CHARGE`, `CHARGE_REFUND`, `UNIDENTIFIED_DEPOSIT`). There is deliberately no free-standing dialog for them: a charge that is not on a statement line is not yet a fact. The six classifications are `FUND_INTEREST`, `FUND_SWEEP`, `BANK_CHARGE`, `CHARGE_REFUND`, `UNIDENTIFIED_DEPOSIT` and `INVESTMENT_INTEREST`; the sixth, beyond the requirements' five, is offered only on a section 86(4) investment account and delegates to the per-investment interest record ([ADR-379](ADR-379-investment-accounting-on-the-books.md)). The classification must agree with the line's sign and with the account's section, and every created row moves the cashbook and one ledger by the same amount, so the books balance after each one.

The approval rule is the requirements' default, confirmed. For a bank-originated line the control is not a second signature but the evidence: the entry cannot be invented because it is created from an imported line, it is audited with the member who classified it, it appears on the Fund ledger and the interest-and-charges schedule, and a mistake is undone by unmatching. Where the firm takes money out of a trust banking account or moves it to another creditor on its own say-so, approval stays under the account's existing single or dual mode ([ADR-232](ADR-232-configurable-dual-authorization.md)). Those are the `REQUIRED` types: `PAYMENT`, `FEE_TRANSFER`, `REFUND`, `INV_PLACE`, `FIRM_INV_PLACE`, `UNIDENT_ALLOC_OUT` and `UNIDENT_FUND_PAY`.

The rule is not "firm-originated means approved". These firm-originated types are `NONE`, each for its own reason: `DEPOSIT` (money coming in; today's rule, unchanged); `TRANSFER_OUT` / `TRANSFER_IN` (today's rule, unchanged: no cash leaves the account); `INV_INTEREST` and `FUND_INV_SHARE` (the bank credited the interest; they are `FIRM` only because they can also be entered from a bank advice with no statement line, and they add to trust creditors and take nothing out); and `INV_WITHDRAW` / `FIRM_INV_WITHDRAW` (money returning to the trust banking account, replacing today's unapproved withdrawal deposit). `WITH_PAIR` rows (`UNIDENT_ALLOC_IN`, `INV_RECEIVE`, `FIRM_INV_RECEIVE`, `INV_RETURN`, `FIRM_INV_RETURN`) take effect with their pair and inherit whatever control the pair had. No new capability is added because `VIEW_TRUST`, `MANAGE_TRUST` and `APPROVE_TRUST_PAYMENT` already separate reading, recording and approving; a fourth would need a tenant migration inserting role grants in every schema for no finer control than the phase needs.

`RECORDED` is a fifth `match_status`, not a reuse of `MANUALLY_MATCHED`, because the two unmatch differently. A matched line lets go of a transaction that existed before the match. A recorded line created its rows, so unmatching reverses them through `TrustTransactionService.reverseForUnmatch` under the contra model ([ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md)), each contra dated the line's date. That date is chosen because unmatching corrects a classification mistake: the bank never did what the row said, and a same-date contra leaves no trace in any as-at balance. If the date falls in a locked month, both rows show as after-lock adjustments, which is the truth ([ADR-382](ADR-382-monthly-trust-creditor-list-locking.md)). Unmatching a recorded line depends on a founder ruling that a `RECORDED` row may be reversed (architecture §16.12.2 #1); the design proceeds on the recommended answer.

The matcher of [ADR-233](ADR-233-bank-reconciliation-matching.md) is unchanged: auto-match runs first with the same threshold and signals, and a line that auto-matches keeps no suggestion. `CsvBankStatementParser.suggestClassification` holds one default keyword table and no bank parser overrides it in this phase. `UNIDENTIFIED_DEPOSIT` is never suggested, because the absence of a match is not evidence of an unknown owner. One parser hypothesis was found in review and must be reproduced before any fix: a zero-amount opening or closing balance row (line 3 of the existing `fixtures/trust/standard-bank-sample.csv`) is imported as a statement line, which could be neither matched nor recorded and would hold a reconciliation at gate G4. If `BankStatementParserTest.zeroAmountBalanceRows_areNotImportedAsLines` is red on `main`, conditional slice 652G makes the shared parser skip a row whose amount is zero and whose description is an opening, closing, brought-forward or carried-forward balance marker, still taking the balances from the running-balance column; if it does not reproduce, it is struck with the evidence and 652G is not built.

Kazi does not gross up a line: where a bank shows a single net credit, that amount is recorded and the gross figures go in a memo record ([ADR-384](ADR-384-memo-records-for-at-source-sweeps.md)).

**Consequences**:

- Positive:
  - Interest, sweeps, charges, charge refunds and unidentified deposits reach the cashbook on the bank's date, already matched to their line.
  - A reconciliation can reach zero difference without excluding real bank movements, and the integrity flag can name lines that are neither matched, recorded nor excluded.
  - No migration to widen a type column in either schema; the portal read-model stores the same codes.
  - A wrong classification is fully undone by one action and leaves an audit trail.
- Negative:
  - Bank-originated entries have no second-member check. The risk accepted is misclassification, not invention.
  - Suggestions are weak by design; on an unfamiliar bank layout the firm classifies by hand.
  - `CHARGE_REFUND` is one type for a bank's refund and a firm's top-up, so the books do not say which it was ([ADR-381](ADR-381-bank-charges-and-fund-ledger-debit.md)).
  - An `EXCLUDED` line still cannot be un-excluded, and excluding a real movement leaves that amount as the bank-to-cashbook difference; the screen says so.
  - Postings on a `CLOSED` trust account are still refused, including recording a late statement line. It is the one existing rule that can stop a firm recording a bank movement; it is kept as-is and raised for a later ruling (architecture §16.14.4).

**Related**: [ADR-232](ADR-232-configurable-dual-authorization.md) (applied: maker-checker for firm-initiated postings), [ADR-233](ADR-233-bank-reconciliation-matching.md) (extended: a line may be recorded from as well as matched), [ADR-375](ADR-375-statement-driven-trust-interest.md), [ADR-376](ADR-376-non-client-trust-creditor-ledger.md), [ADR-378](ADR-378-ledger-balances-derived-from-posting-effects.md) (registry, contra reversal), [ADR-379](ADR-379-investment-accounting-on-the-books.md), [ADR-381](ADR-381-bank-charges-and-fund-ledger-debit.md), [ADR-384](ADR-384-memo-records-for-at-source-sweeps.md), [ADR-238](ADR-238-entity-type-varchar-vs-enum.md); [architecture §16.2.1, §16.2.6, §16.3.5, §16.4.3, §16.7.5, §16.9.3, §16.11](../architecture/phase92-trust-books-provably-right.md); slices 647A, 647B, 648B, and 652G if the parser hypothesis reproduces.
