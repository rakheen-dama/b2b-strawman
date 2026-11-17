# 647C — found outside scope (reported, not fixed)

Slice 647C (parser suggestions + Fund ledger rows). Base `main` 64f1325ac. Nothing below is changed
in 647C; each item names a suggested owner.

## 1. `INVESTMENT_INTEREST` is never suggested in 647C (owner: 648B)

- Architecture §16.3.5 keyword table, row 2: a credit containing `INTEREST` (not `LPFF`/`FIDELITY`)
  suggests `FUND_INTEREST`, "stored as `INVESTMENT_INTEREST` when the statement's account is a
  section 86(4) investment account".
- Not implementable yet: the section is `account_type = 'INVESTMENT'` **and**
  `investment_basis = 'CLIENT_INSTRUCTION'`; `TrustAccount` has no `investment_basis` until 648C, and
  `StatementLineClassification` has no `INVESTMENT_INTEREST` constant until 648B. Using
  `accountType == INVESTMENT` as a proxy would mis-suggest on section 86(3) accounts (same reasoning
  as 647A found.md #2 / ruling D1 → (a)).
- Orchestrator ruling (647C #1): not suggested in 647C. 647C stores whatever
  `CsvBankStatementParser.suggestClassification` returns (`FUND_INTEREST` for such a line); the
  mapping `FUND_INTEREST` → `INVESTMENT_INTEREST` for an s86(4) account belongs at the import step
  (`TrustReconciliationService.importBankStatement`, where the statement's account is known — the
  parser does not know the account) and goes in with 648B, after 648C's section derivation.
- Harmless meanwhile: the Record dialog pre-selects a suggestion only when it is among the
  classifications offered for the line (`RecordFromStatementDialog.tsx`, `suggested`), so a later
  `INVESTMENT_INTEREST` suggestion pre-selects nothing until 648B offers it (pinned by the vitest
  case "pre-selects nothing … the line's sign does not allow (647C)").

## 2. Hypothesis (UNREPRODUCED) — client-ledger history running balance disagrees with the as-at client balance between a reversed row and a later-dated contra

Status: **unverified hypothesis**, needs a red integration test before any fix (root `CLAUDE.md` §4).
Owner: orchestrator to assign.

- Mechanism. Since 649B a reversal is a contra: "the original keeps its effects and is only marked
  `REVERSED`" (`transaction/TrustPostingService.java:19-20`, class Javadoc), and every Phase 92
  balance sums posted rows (`posted_at IS NOT NULL`), e.g. `TrustLedgerQueryRepository.clientBalance`
  (`transaction/TrustLedgerQueryRepository.java:58-69`).
- The client-ledger history window still uses the pre-649B predicate:
  `transaction/TrustTransactionRepository.java:107-108` —
  `t.status IN ('RECORDED','APPROVED') AND t.transaction_type <> 'REVERSAL'` (the Javadoc at `:79`
  says so). The REVERSED original and its REVERSAL contra are both left out of the window.
- The generic reversal endpoint lets the contra fall on a **later** date than the original:
  `TrustTransactionService.resolveReversalDate` (`transaction/TrustTransactionService.java:1493`)
  takes the requested date or today, refusing only future dates and dates before the original.
- Expected symptom: a client deposit D on d1, another row R on d2, D reversed with a contra on d3
  (d1 < d2 < d3). `GET /api/trust-accounts/{id}/client-ledgers/{clientId}/history` shows R's running
  balance **without** D, while `clientBalance(asAt = d2)` (and any as-at card / statement for d2)
  **includes** D. From d3 on both agree (D and its contra net to zero). Same-date contras (unmatch of
  a recorded line, 647A) are unaffected.
- Suggested test: `ClientLedgerRunningBalanceIntegrationTest` — post D (d1), R (d2), reverse D with
  `reversalDate` d3, assert R's `runningBalance` equals `clientBalance(account, client, d2)`. Expected
  red on `main` if the hypothesis holds. The fix, if reproduced, is the predicate 647C uses for the
  creditor ledger (`posted_at IS NOT NULL`, every posted row incl. REVERSED/REVERSAL gets a balance —
  `TrustLedgerQueryRepository.creditorLedgerRows`), which is a visible behaviour change for the
  client ledger (ADR-361 shows "—" on reversed rows today) and so needs its own ruling.

## 3. Note — substring keyword matching gives known false positives (by spec, not a defect)

- §16.3.5 says "description contains", case-insensitive. Implemented literally (orchestrator ruling
  647C #4). Ordinary payments whose description contains `FEE` suggest `BANK_CHARGE` on a debit:
  "Sheriff fees payment", "Disbursement, courier fees", "Advocate consultation fee" (existing
  fixtures) — pinned in `BankStatementParserTest` (`suggestClassification_followsTheKeywordTable`
  and the per-fixture lists). "COFFEE" would match too.
- Mitigations already in the design: auto-match runs first and clears the suggestion of every line
  it matches; the system never acts on a suggestion; the firm confirms in the dialog. Whole-word
  matching would be a product change — raise it if OBS shows it misleads.

## 4. Product/legal call — a statement line dated after today can be recorded (owner: orchestrator)

Raised by CodeRabbit on PR #1795 (the newest Fund-ledger running balance can differ from the Fund
card). Orchestrator ruling: **not a 647C defect** — a row's running balance is the balance as at
that row's date, and the card is as at today. Nothing is changed in 647C. Paths below are under
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`.

- 647A's `StatementLinePostingService.recordFromLine` (`reconciliation/StatementLinePostingService.java:114`)
  has **no date-after-today refusal**. Its checks are: line is `UNMATCHED` (`:123`), account not
  closed (`:138`), sign agrees and charge detail (`:147-148`). The row is then posted with the
  **line's** date as-is (`line.getTransactionDate()`, `:173`) through `postingService.record`
  (`:181`), and `TrustPostingService` does not check dates either. The statement import
  (`TrustReconciliationService.importBankStatement`, `reconciliation/TrustReconciliationService.java:172`)
  has no future-date check, and `recordFromLine` does not require a reconciliation. The only
  "not in the future" rule on this path is the reconciliation **period end**
  (`reconciliation/TrustReconciliationService.java:727-729`). The manual paths do refuse future
  dates: fee transfer (`transaction/TrustTransactionService.java:1484`) and reversal date
  (`transaction/TrustTransactionService.java:1498`).
- Consequence: if a statement line dated after today is recorded (e.g. a bank-dated value date, or a
  wrong date in the CSV), the newest Fund-ledger row's running balance (as at the row's date) differs
  from the Fund card on `/trust-accounting/other-creditors`, which is as at today
  (`creditor/TrustCreditorLedgerService.java:77`, `LocalDate.now(clock)`). Both figures are
  correct for their own date. They reconcile again once that date is reached.
- Decision needed: should recording from a statement line (and/or the import) refuse a line dated
  after today, like the fee-transfer and reversal paths do? Or should a future-dated row be allowed
  and flagged? This is a product/legal call, not a 647C fix.
