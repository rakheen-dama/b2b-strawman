# Phase 92 — Trust Books That Are Provably Right: Statement-Driven Interest, Non-Client Trust Creditors, Investments On The Books

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-11-16). **Runs before Phase 91** (UI modernization II, requirements written, not built) — founder decision: trust correctness is a release blocker for design partners, the UI phase is not, and Phase 91's trust pages should be redesigned on the corrected model. Input: `Kazi trust accounting findings and proposal.md` (repo root, discrepancies D1–D12), a code trace (2026-11-16), a Fable legal-advisor report and a Perplexity cross-check (2026-11-16). Ideation log: `.claude/ideas/phase92-ideation-2026-11-16.md`.
>
> **Founder decisions (2026-11-16):**
> 1. **Sequence:** Phase 92 → Phase 91 → Phase 84 (take-on).
> 2. **Posting guard is slice 1 of this phase**, not a standalone pre-phase PR.
> 3. **Scope = "provably right books"**, all eight epics below in one phase (investments are not split out).
> 4. **Legal basis:** the Fable legal advisor's research plus Perplexity is sufficient to build on, with the founder's sign-off. The points still wanting a practising trust auditor are carried as an **assumptions register** (§ Assumptions), designed so a different answer changes configuration or copy, not the data model.
> 5. **No real tenant has posted an interest run.** Only demo, e2e and dev data exist. No remediation tooling is built; affected environments are reset.
> 6. **The calculated interest run is removed**, not kept as an estimate.
> 7. **Invariant enforcement = tests + a visible check.** No hard block on day-to-day posting.
> 8. **Exit bar = an observed balanced month** in the browser on a fresh stack, plus the three Playwright suites. No full 90-day re-walk.
> 9. **Fund ledger in debit: allow, but flag.** Kazi records what the bank did and warns; it never blocks the posting and adds no firm-float concept (see A1).
>
> **Scouting note (code trace 2026-11-16 against `main` `8440d3cf4`; paths relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` unless written in full. Re-read file:line at HEAD before relying on them. Items marked HYPOTHESIS are unproven and must be reproduced by an integration test before any fix — root CLAUDE.md §4):**
> - **Interest run.** `interest/InterestService.java:272-345` calculates per-client notional interest from daily ledger balances × `LpffRate.ratePercent` and splits it client/Fund (`lpffSharePercent`, or the statutory 5% at `:317-343` whenever the client has any `CLIENT_INSTRUCTION` investment — applied to the client's *general* balance). Posting (`:540-628`) writes one `INTEREST_CREDIT` per client-matter line (credits cashbook and `ClientLedgerCard.addInterestCredit`, `:604`) and one client-less `INTEREST_LPFF` (`:611-628`). The gross interest is never recorded as received.
> - **Posting is live.** `postInterestRun` checks only module on, status APPROVED (`:520-522`), period ended (`:526`), account ACTIVE (`:536`). Endpoint needs `MANAGE_TRUST` (`interest/InterestController.java:64-66`). UI: "Post" in `frontend/app/(app)/org/[slug]/trust-accounting/interest/InterestPageClient.tsx:85`; "Post to Ledger" in `frontend/components/trust/InterestRunWizard.tsx:203-215, 603-610`.
> - **Transaction types and cashbook sign** (`chk_trust_txn_type`, `db/migration/tenant/V1__baseline.sql:1821`; `transaction/TrustTransactionRepository.java:176-196`): DEPOSIT +, INTEREST_CREDIT +, PAYMENT −, FEE_TRANSFER −, REFUND −, INTEREST_LPFF −, TRANSFER_IN/OUT neutral, REVERSAL excluded. Only RECORDED and APPROVED rows count. **No bank-charges type.**
> - **No non-client trust creditor exists.** The only client-less row allowed is `INTEREST_LPFF` (`chk_trust_txn_customer_required`, `V1__baseline.sql:1819`). No LPFF ledger, suspense or unidentified-deposits concept in Java or SQL.
> - **Reconciliation.** `reconciliation/TrustReconciliationService.calculateReconciliation` (`:607-678`) balances only when adjusted bank = cashbook = client ledger total; adjusted bank uses outstanding *transactions* only (`:637-642`, repository `:404-428`). A bank line can only be UNMATCHED / AUTO_MATCHED / MANUALLY_MATCHED / EXCLUDED (`reconciliation/BankStatementLine.java:45-54`, exclude at service `:486-512`); there is **no line classification** and an unmatched or excluded line adjusts nothing.
> - **Statement import** is CSV only: `AbsaCsvParser`, `FnbCsvParser`, `NedbankCsvParser`, `StandardBankCsvParser`, `GenericCsvParser` (fallback).
> - **Investments.** `investment/TrustInvestmentService.java`: placement posts a `PAYMENT` (`:140-150`) that on approval debits the client's ledger card (`transaction/TrustTransactionService.java:1035-1036`); `recordInterestEarned` (`:188-228`) only increments `investment.interestEarned` (hand-typed, no transaction); `withdrawInvestment` (`:230-298`) posts one `DEPOSIT` of principal + recorded interest (`:261`). **No 5% Fund share on this path**, and invested money leaves the client's trust balance while invested.
> - **Ledger card vs cashbook.** The card is a stored balance updated inline (deposit `TrustTransactionService.java:378`, approval `:1035-1040`, interest `InterestService.java:604`); the cashbook is recomputed from transactions. Any path that touches one and not the other breaks reconciliation silently.
> - **HYPOTHESIS — credit reversal is a no-op.** Reversing a DEPOSIT or INTEREST_CREDIT creates an AWAITING_APPROVAL `REVERSAL` (`TrustTransactionService.java:1307-1308`) and leaves the original APPROVED (`:1412-1414`); `performApprovalCompletion` touches the card only for PAYMENT / FEE_TRANSFER / REFUND (`:995, :1034-1045`); the cashbook ignores REVERSAL rows. So an approved credit reversal may change neither cashbook nor card.
> - **HYPOTHESIS — debit reversal** credits the card only if one exists (`:1345-1355`) while the cashbook picks it up regardless.
> - **`INTEREST_LPFF` cannot be reversed** (in neither `CREDIT_TYPES` nor `DEBIT_TYPES`, `:1217-1221`; reversing throws at `:1309-1312`).
> - **Reports** (`report/TrustReportPackSeeder.java:54-60`): `trust-receipts-payments`, `client-trust-balances`, `client-ledger-statement`, `trust-reconciliation`, `investment-register`, `interest-allocation`, `section-35-data-pack` (`Section35DataPackQuery.java:76-141`).
> - **Other consumers of the interest types:** `ClientLedgerStatementQuery.java:97`, `TrustReceiptsPaymentsQuery.java:116`, `ledger/ClientLedgerService.java:51-54`, `reconciliation/TrustReconciliationService.java:51-53`, `verticals/legal/statement/StatementOfAccountContextBuilder.java:108-116`, `customerbackend/service/TrustLedgerPortalSyncService.java` (`:553`).
> - **Design record to supersede:** ADR-234 (daily-balance method), ADR-235 (statutory vs configurable LPFF share), ADR-362 (interest-run draft approval surface), ADR-366 (interest credits allocated to matters).
> - **Already fixed, do not re-plan:** recon wizard step-4 crash (#1778), missing initial calculate and double-create guard (#1779).

## System Context

Kazi is a legal-only (`legal-za`) multi-tenant practice-management SaaS for South African law firms (schema-per-tenant; Spring Boot 4 / Java 25; Next.js 16; firm app + client portal). Its trust accounting module has trust accounts (types `GENERAL`, `INVESTMENT`, `SECTION_86`), trust transactions with a maker-checker approval flow, per-client-per-matter ledger cards, CSV bank-statement import with auto and manual matching, a reconciliation wizard, s86(3)/(4) investment records, a calculated interest run with LPFF rates, a report pack including a Section 35 auditor data pack, statements of account, and a portal trust ledger synced from the firm side.

Three phases of trust work (88, 89, the G89 fix batches) left a defect that makes the books wrong in law: the interest run credits clients with interest that vests in the Legal Practitioners Fidelity Fund, and posts a Fund outflow for money never recorded as received, so no reconciliation can balance after a run (measured: cashbook R 25,52 below client ledgers on the e2e stack). Tracing that defect showed the wider cause: **nothing in the product or its tests asserts that the trust books balance**, there is no way to hold trust money for anyone who is not a client, and invested money drops off the books while invested.

## Objective

Make Kazi's trust books provably right against the Legal Practice Act 28 of 2014 (s86, s87) and LPC Rule 54:

1. The trust cashbook mirrors the bank statement. Interest, Fund sweeps and bank charges are **recorded from statement lines, never calculated**.
2. Trust money held for a non-client (the Fund's interest, unidentified deposits) has a ledger.
3. Money invested under s86(3)/(4) stays a trust creditor balance while invested, and s86(4) interest is recorded per investment with the statutory 95/5 split.
4. One equation holds after every posting path, is asserted by tests, and is visible to the firm:
   **trust bank + trust investment accounts (adjusted for outstanding items) = cashbook = client ledgers + client investment creditors + non-client trust creditors.**
5. The Rule 54.15 monthly list and the Rule 54.28 interest-and-charges figures come straight from those books.
6. A firm can complete a genuinely balanced monthly reconciliation in the browser.

## Legal basis (advisor positions, 2026-11-16)

| # | Position | Basis | Confidence |
| --- | --- | --- | --- |
| L1 | Interest on s86(2) and s86(3) money vests 100% in the Fund; clients receive none. | LPA s86(5)(a), read | High |
| L2 | Only s86(4) interest goes to the client: 95% client, 5% Fund. | LPA s86(5)(b), read | High |
| L3 | The bank pays the Fund's interest by automated monthly transfer (compulsory from 1 Sep 2025); recoverable bank charges are set off against s86(2)/(3) interest and the net is paid. | Rule 54.14.16 as amended (Gazette 49833); LPFF notices June 2020 and July 2025, read | High |
| L4 | How each bank lays this out on the statement is not published and differs by bank. On s86(2) accounts interest and charges generally post to the trust account and the net is swept visibly. | LSSA 2016 notice, FNB page, Tech4Law (secondary) | Medium; low per bank |
| L5 | While the Fund's interest sits in a trust account, the Fund is a trust creditor that is not a client; it appears in the creditor list and the reconciliation, one ledger per trust bank account. | Rule 1.39 definition read; De Rebus 2021 and 2024 (secondary) | Medium-high |
| L6 | Only charges on the LPFF's recoverable list offset interest; VAT vendors deduct net of VAT; where charges exceed interest the firm funds the shortfall from business. | LPFF June 2020 notice read; LPFF list (extract); De Rebus 2021 | Medium |
| L7 | Invested money remains a trust creditor balance for the client; the trust investment account counts on the money side. Removing it from the client's balance at placement is a known inspection finding. | Rule 54.14.8 and 54.15.1 read; De Rebus Sept 2024 | High |
| L8 | s86(4) interest is recognised when the bank credits the investment account (or on maturity if it only accrues then). Investment proceeds return through the trust banking account. | SAICA FAQ 11 (secondary); Rule 54.14.7.3 read | Medium |
| L9 | Money whose owner is unknown, or unclaimed after one year, is held as trust money, itemised and aged, and paid to the Fund after the second annual closing. No fees or payments may be taken from it. | LPA s87(4) via LPFF 2024 guide, read; De Rebus 2015 (extract) | High |
| L10 | The Rule 54.15 list is **monthly**: every person for whom money is held, by name, totalled and compared with trust bank + trust investment + trust cash; each balance noted in the ledger. | Rule 54.15.1–.2, read | High |
| L11 | Rule 54.28: report gross interest earned and gross charges levied per s86(2), (3) and (4) account, even where no charges claim is made. | Rule 54.28, read | High |
| L12 | The auditor inspects bank statements for interest credited and tests that it was paid to the Fund on time, agrees reconciliations to total trust creditors, and inspects suspense ledgers and debit balances. | IRBA guide (extracts only; irba.co.za returned 403) | Medium |

## Assumptions (to confirm with a practising trust auditor or tax adviser; not blocking)

| # | Assumption | If wrong, what changes |
| --- | --- | --- |
| A1 | A transient debit on the Fund interest-and-charges ledger (bank charges posting before the month's interest) is recorded as it happened and flagged, not blocked. The LPFF June 2020 notice treats charges as a set-off against interest on the same account and is silent on timing; no carve-out from Rule 54.14.9 was found. | Add a firm-funded float entry type on that ledger, or tighten the warning to a required acknowledgement. No model change. |
| A2 | s86(4) interest may arrive gross (with the 5% debited separately) or net of the 5%. Kazi supports both, recording gross, Fund 5% and net per investment either way. | Nothing; both are supported. |
| A3 | Kazi keeps the per-investment figures a firm needs for IT3(b) (gross, 5%, net, dates, client tax identifier) but does not file. A SAICA FAQ says the practitioner must submit IT3(b) for s86(4) interest; who files in practice is unconfirmed. | A later export or filing feature. Out of scope here. |
| A4 | The Fund ledger is one per trust bank account and carries both interest and charges ("interest and charges" in one ledger, as De Rebus 2021 describes). | Split into two accounts on the same ledger. Naming only. |
| A5 | Record retention for the monthly list is seven years (the 2018 text says five; a 2020 amendment reportedly made it seven; amending gazette not read). Kazi never deletes locked lists, so either holds. | Nothing. |
| A6 | Rule 54.14.16.5 (non-payment to the Fund is misconduct) survives the 2023 amendment. Only informs copy. | Copy. |

## Constraints & Assumptions

- **Reproduce before fix** (root CLAUDE.md §4). Every HYPOTHESIS in the scouting note gets a failing integration test before any production change; if it does not reproduce, it is struck with the evidence, not fixed.
- **No production data to preserve.** No real tenant has posted an interest run or an investment. Migrations may restructure or drop interest-run data; demo and e2e seeders are updated and environments reset. `/architecture` rules whether the retired tables are dropped or kept read-only; default is drop.
- **Migration numbers are next-free at build time** (currently tenant V16 / global V7 as a guide only).
- **Slice by verify cost.** One PR per slice, backend and frontend together where they close one gap; minimise full backend verifies per epic. Full `./mvnw verify` (via `backend/scripts/verify.sh`) and the frontend gate including `format:check` remain the merge bar.
- **UI slices run the legal Playwright suites**, on a fresh stack for gate-grade runs, never overlapping a Maven verify.
- **Scenario amendments** (root CLAUDE.md §6): removing the calculated interest run and changing the investment flow invalidates steps in `qa/testplan/demos/*.md` and the legal lifecycle specs. The founder has decided the product change; `/architecture` must list the exact step ids to amend for explicit founder authorisation before any scenario file is edited.
- **Kazi stays a mirror of the bank.** No rule in this phase may stop a firm recording a transaction that appears on its bank statement.
- **Maker-checker stays.** New posting paths follow the existing approval and self-approval rules; `/architecture` rules which statement-derived postings need approval (default: bank-originated lines the firm did not initiate — interest, sweep, charges — post as RECORDED and matched; allocation of an unidentified deposit to a client needs approval).
- **SA time, ZAR, legal-za only.** No per-firm variants.
- **Phase 91 not started.** Trust UI in this phase uses the Phase 90 primitives as they stand; do not pre-empt Phase 91's redesign.

## Epic specifications

Recommended order: E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8, with the E5 harness introduced early (it may start as characterisation tests with known failures listed) and extended as each epic lands. `/architecture` and `/breakdown` may re-cut by verify cost.

### E1 — Stop the harm: disable interest-run posting (slice 1)

- Backend: `postInterestRun` refuses with a clear domain error; approval of runs is likewise refused. Calculation and draft viewing stay until E7 removes them.
- Frontend: hide Post / Post to Ledger and the approve action; the interest page carries a short notice that calculated interest is not posted and why (s86(5)).
- Demo and e2e seeders stop posting interest runs; existing seeded interest postings are removed so the seeded books balance.
- Tests: a posting attempt is rejected; the UI offers no way to post.
- This slice is small and independent so it can merge within the first day of the phase.

### E2 — Non-client trust creditor ledger and the reconciliation equation

- A **trust creditor ledger for non-clients**, per trust account, with typed accounts. Two types ship: **Fund interest and charges** (the Fund is the creditor) and **Unidentified deposits** (owner unknown). The model must admit further types without a schema change, but none is built.
- Transactions against a non-client creditor carry no client and no matter; the client-required constraint is reworked to "client **or** non-client creditor account, never both, never neither".
- Unidentified deposits are **itemised**: each has date received, bank description, amount, and status (held, allocated to a client, paid to the Fund), so the ledger balance is always the sum of open items and an age analysis is possible.
- The **cashbook = client ledgers + non-client creditors** equation replaces cashbook = client ledgers in `calculateReconciliation`, in the dashboard, and anywhere else the comparison is made. E4 adds the investment terms.
- The reconciliation screen shows the creditor side broken down: client ledgers, Fund interest and charges, unidentified deposits.
- A debit balance on the Fund ledger is allowed and flagged (decision 9, A1). A debit balance on unidentified deposits is impossible by construction.
- `INTEREST_LPFF` is retired as a type (E7 removes the code that writes it).

### E3 — Post from the statement line

The heart of the phase: the firm classifies bank-originated lines and Kazi creates the matched trust transaction.

- On an unmatched statement line, alongside Match and Exclude, a **Record from statement** action with these classifications:
  - **Interest received (Fund)** — on a general (s86(2)) or firm-initiative (s86(3)) account: credits the cashbook and the Fund ledger.
  - **Fund sweep** — the transfer to the Fund: debits the cashbook and the Fund ledger.
  - **Bank charge** — debits the cashbook and the Fund ledger; flagged recoverable or non-recoverable, with VAT captured separately. Non-recoverable charges and VAT are expected to be redirected to the business account by the bank; where the bank debits trust anyway, Kazi records it and flags it for the firm to refund from business.
  - **Bank charge refund / firm top-up** — a credit from the business account restoring the Fund ledger.
  - **Unidentified deposit** — credits the cashbook and opens an unidentified-deposit item.
- Each created transaction is dated at the statement line's date, linked to the line, and the line becomes matched. Unmatching reverses the created transaction through the normal reversal path.
- **Allocate an unidentified deposit** to a client and matter later: moves the item to the client's ledger (maker-checker), keeping the link to the original bank line. **Pay to the Fund** (s87(4)) closes an item against a payment. The Fund submission paperwork itself is out of scope.
- Parser-assisted suggestion: where a bank parser can recognise an interest, sweep or charge narration, pre-select the classification; the firm confirms. Never auto-post.
- Net presentation (L4): where a bank shows only the net sweep or nothing at all on the trust statement, the firm records nothing in the cashbook; gross interest and charges for Rule 54.28 are then captured as a **memo record** per account and month (E6).
- New transaction types follow the findings doc's table as a starting point (`LPFF_INTEREST_RECEIVED`, `LPFF_INTEREST_PAID`, plus bank-charge and unidentified-deposit types); final names are an `/architecture` call.
- Audit events and activity labels for every new action.

### E4 — s86(3)/(4) investments kept on the books, interest per investment

- **Placement** moves the client's money from their general trust balance to a **client investment creditor balance** backed by the trust investment account. The client's total trust balance (general + invested) is unchanged by placement. Firm-initiative s86(3) investments move surplus general money to an investment account with no change to any client's balance.
- The trust position counts **trust investment account balances on the money side** and client investment creditors on the creditor side (L7). The equation in E2 gains both terms.
- **s86(4) interest** is recorded per investment from that investment account's statement or bank advice: gross, Fund 5%, client 95%, date. Both presentations are supported (A2): gross credited with the 5% debited separately, or net credited. The 5% sits on the Fund ledger for that investment account until swept; the 95% accrues to the client's investment creditor balance.
- **s86(3) interest** is 100% the Fund's: recorded as interest received (Fund) on the investment account and swept, as in E3.
- **Withdrawal / maturity** returns capital plus the client's interest to the trust banking account and from the investment creditor balance to the client's general balance (Rule 54.14.7.3), tied to the bank lines on both accounts. The hand-typed interest figure is replaced by the recorded interest entries.
- The statutory 5% is a constant, not a setting. The "client has any client-instructed investment" switch on general balances disappears with the run (E7).
- Per-investment tax record: gross, 5%, net, dates and the client's tax identifier where held (A3). No filing.
- Investment register report and the client ledger statement show invested balances and investment interest correctly.
- Client statements of account and the portal show a client interest **only** from their own s86(4) investments.

### E5 — Invariant harness and trust integrity check

- **Backend test harness:** one reusable assertion of the full equation, per trust account and firm-wide, called after every posting path in integration tests: deposit, payment and approval, fee transfer, refund, inter-client transfer, every reversal (credit and debit, before and after approval), each E3 classification and its unmatch, unidentified-deposit allocation and Fund payment, investment placement, interest and withdrawal, period-crossing cases.
- **Reproduce the two reversal HYPOTHESES** with failing tests first. Fix what reproduces (each as its own slice if the fix is not trivial); strike what does not.
- Close the structural cause: `/architecture` rules whether ledger-card balances stay stored (with the harness guarding them) or become derived from transactions. Recommendation to evaluate: derived, since the Phase 89 fix #1746 already moved ledger totals that way.
- **Trust integrity check** visible to the firm: on the trust dashboard and as a gate before a reconciliation can be completed. When it fails it names each side's total and the difference, and lists flags: Fund ledger in debit, any client ledger in debit (Rule 54.14.9 / 54.14.11), unidentified deposits older than one year, statement lines neither matched, recorded nor excluded.
- The check never blocks day-to-day posting (decision 7).

### E6 — Rule 54.15 monthly list and Rule 54.28 schedule

- **Monthly trust creditor list (Rule 54.15):** as at a month end, every person for whom money is held, by name, with general and invested balances, plus the Fund and unidentified-deposit lines; totalled and compared with trust bank + trust investment + trust cash. Evolve `client-trust-balances` rather than adding a parallel report.
- **Lockable per month:** completing the month's reconciliation freezes that month's list as a stored artefact with the comparison result; later back-dated postings do not alter it and are flagged against it.
- **As-at-any-date trust position** for the auditor's second test date.
- **Interest and charges schedule (Rule 54.28):** per account and month, gross interest earned and gross charges levied for s86(2), s86(3) and s86(4) accounts, with brought-forward and carried-forward Fund balances, sourced from E3/E4 postings and memo records. Replaces `interest-allocation`.
- **Unidentified deposits age analysis.**
- **Section 35 data pack** updated: the Fund and unidentified lines in client balances, investments on the books, the new schedule in place of interest allocation, each Fund sweep shown with its matched statement line as Rule 54.14.16 evidence.
- Report pack version bump for `legal-za`.

### E7 — Remove the calculated interest run

- Delete the interest run, its wizard, approval surface, LPFF rate table, `lpffSharePercent`, allocation lines, the `interest-allocation` report and their endpoints, navigation entries, permissions text, audit labels and notifications. Remove the E1 guard with them.
- Retire `INTEREST_LPFF`; `INTEREST_CREDIT` survives only if `/architecture` keeps it for s86(4) (E4), linked to an investment.
- Sweep every consumer listed in the scouting note (ledger service, statement builders, portal sync, receipts and payments, reconciliation) so none references the retired types.
- Check the MCP read tools and the `kazi-legal-za` skill pack (`trust-reconciliation` skill, in `../claude-for-legal-sa`) for references to interest runs; update the backend tool contracts here and list the consumer-repo changes as a follow-up.
- New ADR superseding ADR-234, ADR-235, ADR-362 and ADR-366; mark each superseded.
- Update the findings doc's status section and `architecture/` trust references.

### E8 — Reconciliation wizard defects and the exit gate

- **Wizard defects (each reproduced first):** match, exclude and unmatch ignore a failed recalculation without telling the user; re-uploading a statement or changing account or period after a draft exists orphans the draft; the manual-match pane lists only APPROVED transactions while auto-match also uses RECORDED; the reported closed-primary-account bug in the two statement builders (currently unverified).
- `BankStatementParseException` → HTTP 500 (noted in Phase 89) is fixed if it reproduces.
- **Exit gate — the observed balanced month.** On a fresh stack, driven in the browser with Playwright, one month for one firm containing: client deposits on two matters, a payment with second-member approval, a fee transfer, a refund, an inter-client transfer, a reversal of a credit and of a debit, bank interest and its Fund sweep, a recoverable and a non-recoverable bank charge, an unidentified deposit later allocated to a client and a second one left open, and a full s86(4) cycle (place, interest with the 5%, withdraw). The bank statement is imported from CSV. The reconciliation shows a zero difference and completes; the monthly list locks and its total agrees with bank + investment; the Rule 54.28 schedule and the Section 35 pack agree with the same figures; the portal shows the client only their s86(4) interest.
- The month is captured as a committed Playwright spec (extending `e2e/tests/legal-lifecycle/day-45-recon-prescription.spec.ts` or a new spec) so it stays a regression test.
- The three Playwright suites green on a fresh stack; verify markers for every slice; closure report with evidence paths.
- **Exit bar:** the month above observed end to end, the invariant harness green across all posting paths, 0 open HIGH or MEDIUM trust defects, every assumption in § Assumptions still listed with its status.

## Out of Scope

- Remediation tooling for posted interest runs (no real tenant; environments are reset). A short written correction procedure for a future case is welcome in the closure report, nothing more.
- Bank feeds, new statement formats (OFX, PDF), or auto-posting from statements without firm confirmation.
- IT3(b) generation or filing; the s87(4) Fund submission form; the LPFF bank-charge and audit-fee refund claim.
- A firm-funded float on the Fund ledger (A1) unless an auditor requires it.
- An interest estimator or expected-interest check of any kind.
- Trust cash handling, estates and "other trust assets" in the Rule 54.15 comparison (Kazi holds no such balances today; state this on the report).
- Rule 55 investments with no underlying matter.
- Phase 91 UI redesign of trust pages; dark-mode work.
- Changes to fee notes, business-side accounting or Xero.

## ADR topics for `/architecture`

1. **Statement-driven trust interest** — supersedes ADR-234, 235, 362, 366: interest is recorded from the bank, never calculated; statutory shares are constants.
2. **Non-client trust creditor ledger** — model (typed accounts per trust account, itemised unidentified deposits), constraint rework, extensibility.
3. **The trust equation and where it is enforced** — harness, integrity check, reconciliation gate; why not a hard block.
4. **Ledger balances: stored or derived** — given the card/cashbook divergence class.
5. **Investment accounting** — client investment creditor balances, how placement, interest and withdrawal post across the general and investment accounts, gross vs net s86(4) presentation.
6. **Statement line classification and posting** — transaction types, approval rules for bank-originated postings, unmatch semantics, parser suggestions.
7. **Bank charges and Fund ledger debit** — recoverable flag, VAT, allow-and-flag (A1).
8. **Monthly list locking** — what is frozen, how back-dated postings are treated.
9. **Retired data** — drop vs read-only for interest-run tables and types, given no production data.
10. **Memo records for at-source sweeps** — Rule 54.28 figures when nothing reaches the trust account.

## Style and boundaries

- Read `backend/CLAUDE.md` and `frontend/CLAUDE.md` before touching either side. Use the `vercel:nextjs`, `react-best-practices` and `shadcn` skills for UI questions.
- Legal questions arising during architecture or build go to a Fable legal-advisor agent with cited sources (Perplexity for research), and the founder signs off. Do not settle a point of law from first principles in a slice.
- Client-facing and auditor-facing wording (statements, portal, reports) uses the Act's and Rules' terms: "trust creditor", "Legal Practitioners' Fidelity Fund", "section 86(4) investment".
- No raw enums on any screen or document.
- Every slice that changes posting behaviour extends the invariant harness in the same PR.
- PASS means observed in the browser against the backend and database, per root CLAUDE.md §3. "Unit test green" is not an observation of a balanced reconciliation.
- One fix per PR; a newly found defect outside the slice is reported and re-specced, not bundled.
