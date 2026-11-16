# Kazi trust accounting: findings and proposal

Nov 16, 2026 · @Rakheen Dama

## Summary

Kazi's trust interest run is wrong in law. It credits clients with interest that belongs to the Legal Practitioners Fidelity Fund (LPFF), and the trust three-way reconciliation cannot balance after any interest run. The Fable legal advisor recommends treating interest-run posting as a release blocker for design partners.

- **What the law says:** under s86(5)(a) of the Legal Practice Act 28 of 2014, interest on general trust money (s86(2)) and firm-initiative investments (s86(3)) vests 100% in the Fund. Only client-instructed investments (s86(4)) pay the client: 95% client, 5% Fund (s86(5)(b)).
- **Who pays the Fund:** since the December 2023 rule amendment, and compulsorily since 1 September 2025, the bank sweeps the Fund's interest automatically for all three account types.
- **What Kazi does:** it calculates notional interest on every client's general trust balance, splits it between client and Fund at a firm-configurable percentage, credits the client share to client ledgers, and posts the Fund's share as a cashbook outflow that was never recorded as received.
- **Observed effect:** on the e2e stack, the cashbook sits R 25,52 below the client ledger total, exactly the posted LPFF share. Once a bank sweeps 100% of general-account interest, the gap grows to the whole "client share".
- **Proposed fix:** post interest from the bank statement, never from a calculation. Treat the Fund's unpaid interest as a non-client trust creditor. Credit clients only on s86(4) investments.

**Decisions needed from you**

1. Disable interest-run posting now, until the rework lands? (Recommended.)
2. Approve the statement-driven model in Proposed model as the basis for requirements and `/architecture`.
3. Send the open questions to a practising attorney or trust auditor before detailed design.
4. Decide how to remediate interest runs already posted, if any design partner has posted one.

None of the legal research was checked with Perplexity, because no Perplexity tool was connected. The legal advisor read the primary sources listed in Sources; where it could not, this doc says so.

## What the law requires

Only interest on client-instructed s86(4) investments belongs to a client; all other trust interest belongs to the Fund, and the bank pays the Fund directly.

| Provision | Requirement | How it was verified |
| --- | --- | --- |
| LPA s86(2) | A trust account practice keeps a trust account at a bank for money held or received for clients. | Read in the Act ([acts.co.za](https://www.acts.co.za/legal-practice-act-2014/86__trust_accounts)) |
| LPA s86(3) | The practice may invest surplus trust money on its own initiative in a separate trust investment account. | Read in the Act |
| LPA s86(4) | On a client's instruction, the practice invests that client's money in a separate trust investment account for that client. | Read in the Act |
| LPA s86(5)(a) | Interest on s86(2) and s86(3) money "must … be paid over to the Fund and vests in the Fund". | Quoted from the Act |
| LPA s86(5)(b) | Interest on s86(4) money is paid to the client, "provided that 5% of the interest accrued … must be paid over to the Fund and vests in the Fund". | Quoted from the Act |
| LPC Rule 54.14.16 (amended, [Gazette 49833](https://www.gov.za/sites/default/files/gcis_document/202312/49833gen2234.pdf), 8 Dec 2023) | For s86(2), s86(3) and s86(4) accounts, the practice's **bank** pays the Fund's interest to the Fund within 5 working days of the following month, through the Automated Monthly Transfer System. | Full text read |
| [LPFF notice, 15 Jul 2025](https://lssa.org.za/wp-content/uploads/2025/07/Communication-Compulsory-Automation-of-trust-interest-receipts-2025.pdf) | Automation is compulsory from 1 Sep 2025. s86(2) and s86(3): 100% of interest less recoverable bank charges. s86(4): 5%. All by bank sweep. | Full text read |
| LPC Rule 54.14.8 | Trust bank, trust investment and trust cash balances together must never be less than total trust creditor balances. | Summarised fetch, not verbatim |
| LPC Rule 54.15 | A monthly list of every person for whom money is held, totalled and compared with bank and investment balances, kept for 7 years. | Summarised fetch, not verbatim |
| LPC Rule 54.28 | Report gross interest earned and gross bank charges per s86(2), s86(3) and s86(4) account. | Summarised fetch, not verbatim |

**What changed over time.** Under the repealed Attorneys Act, clients received 100% of interest on s78(2A) investments, and practitioners could pay the Fund manually. The LPA introduced the 5% Fund share on client investments from 1 March 2019. The 2023 rule amendment and the 2025 LPFF notice moved payment of all Fund interest to the bank.

**IT3(b) certificates.** The bank issues these, not the firm or Kazi: one to the client for 95% of s86(4) interest and one to the Fund for the 5%. This rests on secondary sources the advisor could not open.

## What Kazi does today

Kazi calculates interest rather than recording it from the bank, gives clients a share of interest on general trust money, and posts the Fund's share as money paid out that was never received. Paths below are under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`.

**1. The interest run calculates, per client** (`interest/InterestService.java`, lines 272–345)

- For each client, it computes gross interest on their daily trust ledger balance over the period, at `LpffRate.ratePercent`. Rate changes split the period into segments (ADR-234, daily-balance method).
- **It splits that gross interest between client and Fund:**
  - If the client has **any** non-cancelled `CLIENT_INSTRUCTION` (s86(4)) investment overlapping the period, the Fund gets 5% (`STATUTORY_LPFF_SHARE_PERCENT`, line 340). That 5% is applied to interest on the client's **general ledger balance**, not on the investment.
  - Otherwise, the Fund gets `LpffRate.lpffSharePercent`, a percentage the firm configures. The client gets the rest.
- It never consults `TrustAccountType` (`GENERAL`, `INVESTMENT`, `SECTION_86`), so general trust money and investment money are treated alike.

**2. Posting the run** (`interest/InterestService.java`, lines 540–628)

- One `INTEREST_CREDIT` per client and matter line for the **client share**. It credits the cashbook, and `ClientLedgerCard.addInterestCredit` (line 604) credits the client's ledger card.
- One `INTEREST_LPFF` for the **total Fund share** (lines 611–628). It has no client, its status is `RECORDED`, and it's dated at period end.
- **The gross interest is never recorded as received.** No transaction brings the Fund's share into the cashbook.

**3. How the cashbook and reconciliation treat it**

- `transaction/TrustTransactionRepository.calculateCashbookBalance` (around line 185) counts `INTEREST_LPFF` as a **debit**, alongside payments and fee transfers. The portal ledger sync and the statement of account do the same (`TrustLedgerPortalSyncService` line 553, `StatementOfAccountContextBuilder` line 116).
- Unmatched `INTEREST_LPFF` rows also count as outstanding payments in the reconciliation.
- `reconciliation/TrustReconciliationService.calculateReconciliation` (lines 607–678) marks a reconciliation balanced only when adjusted bank (bank + outstanding deposits − outstanding payments) = cashbook = client ledger total. There is no term for money owed to the Fund.

**4. The separate investment path** (`investment/TrustInvestmentService.java`)

- `recordInterestEarned` (line 189) accumulates interest on a `TrustInvestment`, and `withdrawInvestment` (line 231) pays principal plus that interest back to the client.
- Whether this path deducts the Fund's 5%, or overlaps with the interest run, has not been traced.

**5. The decisions this was built on**

- **ADR-234** (line 7): "Trust accounts earn interest from the bank. This interest must be allocated between individual clients … and the Legal Practitioners Fidelity Fund."
- **ADR-235** (line 7) describes s86(3) interest as split between clients and the Fund. It is correct that the s86(4) 5% is statutory.

**6. Measured effect** (e2e stack, 2026-11-14, after one interest run)

| Figure | Amount |
| --- | --- |
| Client ledger total (3 cards) | R 290 275,50 |
| Cashbook balance | R 290 249,98 |
| Difference | R 25,52 |
| `INTEREST_LPFF` posted, no client | R 25,52 |
| `INTEREST_CREDIT` posted to clients | R 4,00 + R 21,50 |

The reconciliation wizard showed "not balanced" for exactly this difference.

## Discrepancy register

Twelve gaps separate Kazi from the Act, the LPC Rules and the advisor's reading; seven are high severity, and five of those stem from one design choice: calculating interest per client instead of recording it from the bank.

| # | Area | What Kazi does | What the Act or Rules say | Legal advisor's view | Severity |
| --- | --- | --- | --- | --- | --- |
| D1 | Who owns general trust interest | Credits clients a share of interest on general trust balances. | s86(5)(a): interest on s86(2) and s86(3) money vests 100% in the Fund. | Clients get nothing on general or firm-initiative money. | High |
| D2 | Source of interest figures | Calculates notional interest from daily ledger balances × a configured rate. | Rule 54.14.16: the bank pays the Fund the actual interest. Rule 54.28: report actual gross interest per account. | Record interest from the bank statement; never calculate it for posting. | High |
| D3 | Size of the Fund's share | Firm-configurable `lpffSharePercent` for general accounts. | Fixed by statute: 100% on s86(2)/(3), 5% on s86(4). | Remove the configurable split. | High |
| D4 | s86(4) 5% rule | Applies 5% to interest on the client's **general** balance if the client has any client-instructed investment. | s86(5)(b): 5% of interest on the s86(4) investment money itself. | Record per investment, from that investment's statement. | High |
| D5 | Posting the Fund's share | `INTEREST_LPFF` is a cashbook outflow with no matching receipt. | Trust records must reflect money actually held and paid (Rules 54.14.8, 54.15). | Post receipt and payment through an LPFF ledger, or nothing if swept before it reaches the account. | High |
| D6 | Three-way reconciliation | Balanced only if adjusted bank = cashbook = client ledgers; no term for money owed to the Fund. | Rule 54.15: list every person money is held for; Rule 54.14.8: trust money ≥ trust creditors. | Bank = cashbook = client ledgers + LPFF ledger; LPFF as its own creditor line. | High |
| D7 | What clients see | Statements of account and the portal show clients interest on general money. | Clients are not entitled to it (s86(5)(a)). | Client harm and reputational risk. | High |
| D8 | Who pays the Fund | Posts the payment as a firm-recorded outflow, implying the firm pays. | Rule 54.14.16 (Dec 2023) and LPFF notice: the bank sweeps; compulsory from 1 Sep 2025. | Kazi evidences the sweep by matching it to the statement. | Medium |
| D9 | Account types | Interest run ignores `TrustAccountType`. | The Act treats s86(2), s86(3) and s86(4) accounts differently. | Treatment must follow the account type. | Medium |
| D10 | Bank charges | No treatment of recoverable bank charges. | LPFF notice: Fund receives 100% "less recoverable bank charges". | Charges need their own treatment or the LPFF ledger won't clear. | Medium |
| D11 | Design record | ADR-234 and ADR-235 say general interest is split between clients and the Fund. | s86(5)(a) contradicts both. | Supersede with a new ADR. | Medium |
| D12 | Investment interest path | `TrustInvestmentService` pays principal plus accumulated interest on withdrawal; 5% deduction untraced. | s86(5)(b): 5% of s86(4) interest to the Fund. | Not traced; may double-count with the run. | To confirm |

Severity is the advisor's assessment combined with the measured effect; change it in place if you disagree.

## Other trust findings this cycle

The reconciliation wizard had never been able to complete a reconciliation in the UI; two PRs fixed that, and the interest posting above is now the main thing stopping a reconciliation from balancing.

| Finding | Status | Reference |
| --- | --- | --- |
| Wizard crashed at step 4: the frontend expected a `lines` field the auto-match API never returns. Broken since Epic 449 (PR #948, April 2026). | Fixed | PR #1778, merged 2026-11-13 |
| Step 4 opened on an uncalculated reconciliation (all R 0,00, "not balanced"), so Complete stayed disabled. | Fixed | PR #1779, merged 2026-11-13 |
| Double-clicking Review could create two draft reconciliations; Back during preparation could land a stale result. | Fixed | PR #1779 |
| Cashbook R 25,52 below client ledgers after an interest run, so no reconciliation can balance. | Open | This doc, D5 and D6 |
| Match, exclude and unmatch ignore a failed recalculation without telling the user. | Open | Pre-existing, `reconciliation/new/page.tsx` |
| Re-uploading a statement, or changing account or period, after a draft exists leaves that draft orphaned. | Open | Pre-existing |
| The manual-match pane lists only APPROVED transactions, while auto-match also matches RECORDED ones. | Open | Pre-existing, low |
| The two statement builders have the same closed-primary-account bug as the trust dashboard. | Unverified | Reported by the Phase 90 643A agent; not reproduced |
| Completing a genuinely balanced reconciliation has been proven by unit tests only. The e2e data cannot balance until D5 and D6 are fixed. | Open | Awaiting browser verification |

## Proposed model

One rule replaces the interest run: the trust cashbook mirrors the bank statement, and while the Fund's interest sits in a trust account the Fund is a trust creditor that is not a client. The advisor rates this medium-high confidence, pending the bank-statement questions in Open questions.

&#91;embedded content: interest posting · today vs proposed\]

Today the Fund's share leaves the cashbook without ever entering it. The proposed model records only what the bank statement shows, so the cashbook equals client ledgers plus the LPFF ledger.

**By account type**

- **General trust (s86(2)) and firm-initiative investments (s86(3)):** clients receive nothing. Kazi records the interest the bank actually credited as money held for the Fund, then the sweep to the Fund when it appears on the statement. Nothing is calculated or allocated to clients.
- **Client-instructed investments (s86(4)):** the only interest a client earns. Kazi records it per `TrustInvestment`, from that investment account's statement: gross, 5% to the Fund, 95% to the client.
- **The calculated run is retired.** `lpffSharePercent` goes as a firm setting, and "has a client-instructed investment" stops switching the treatment of general balances. `ratePercent` may survive only as an estimate or sanity check, never as a posting source.

**How the money can move, and what Kazi records**

- **(a) Swept before it reaches the trust account:** post nothing to the cashbook or ledgers. Keep a memo record of gross interest and bank charges for the Rule 54.28 report.
- **(b) Credited to the trust account, then swept out** (bank sweep or legacy firm payment): post a receipt into an LPFF ledger, then a payment from it. Between the two, the balance is owed to the Fund.
- **No configuration flag:** postings follow the statement, so Kazi is correct in either case without the firm declaring which one applies.

**Transaction types**

| Type | Client | Cashbook | Client ledger | LPFF ledger | When it's posted |
| --- | --- | --- | --- | --- | --- |
| `LPFF_INTEREST_RECEIVED` (new) | none | + | none | + | The bank credits interest on an s86(2) or s86(3) account |
| `LPFF_INTEREST_PAID` (replaces `INTEREST_LPFF`) | none | − | none | − | The sweep or payment to the Fund appears on the statement |
| `INTEREST_CREDIT` (s86(4) only, linked to an investment) | required | + | + | none | The client's interest arrives in the trust account |
| `LPFF_INTEREST_S86_4` (only if the bank credits gross) | required | no change | − 5% | + 5% | Moves the Fund's 5% off the client's card until it is swept |

In the normal s86(4) case the bank sweeps the 5% at source, so `INTEREST_CREDIT` is the net 95%, and the gross and the 5% live on the investment record, not in the cashbook.

**Invariants Kazi should enforce**

- Cashbook = sum of client ledger cards + LPFF ledger balance.
- The LPFF ledger balance is never negative.

**Reconciliation and records**

- **Three-way test:** adjusted bank = cashbook = client ledger total + LPFF ledger balance.
- **Reconciliation screen and Rule 54.15 monthly creditor list:** show "Interest held for LPFF" as its own line.
- **Rule 54.14.8 check:** count the Fund's unpaid interest as a trust creditor when comparing trust money to trust creditors.
- **Rule 54.28 report:** keep gross interest and gross charges per account type, even when the bank sweeps at source.
- **Rule 54.14.16 evidence:** match each `LPFF_INTEREST_PAID` to its statement line.
- **IT3(b) queries:** keep per-client, per-investment gross, 5% and net figures for s86(4).

## Risk and release-blocker recommendation

The advisor's call, at high confidence: interest-run posting is a release blocker for any design partner who uses it, and should be disabled until the rework lands.

- **Misstated trust position.** Client cards carry interest that vested in the Fund. A firm that pays it out is paying one client with other clients' or the Fund's money, which is a trust deficit under Rule 54.14.8.
- **The reconciliation can never balance.** The measured R 25,52 understates it: once the bank sweeps 100% of general-account interest, the bank balance also falls below the cashbook by the whole client share.
- **Audit and regulatory exposure.** An unbalanced reconciliation and wrongly credited clients should be expected to draw an adverse auditor's trust report, which can threaten the firm's Fidelity Fund Certificate. The firm also under-accounts to the Fund.
- **Client harm and reputation.** Statements of account and the portal tell clients they earned interest they are not entitled to.

**Who is affected.** Any tenant that has posted an interest run on a general trust account. Whether a design partner has done so is not yet known; it can be checked with a read-only query of posted interest runs per tenant.

## Open questions for an attorney or trust auditor

Five points need a practising attorney or trust auditor before detailed design; the kazi-legal-za skill pack's existing attorney sign-off gate on trust statutes is the natural route.

- [ ] **Bank statements:** how does each major bank show s86(2) interest and the Fund sweep? Credited then debited, netted against charges, or never shown on the trust account's statement?
- [ ] **Creditor line:** do auditors accept the Fund's unpaid interest as a separate, non-client trust creditor line in the Rule 54.15 list and the reconciliation?
- [ ] **Bank charges:** how should recoverable bank charges be treated in the trust books, given the Fund receives interest "less recoverable bank charges"?
- [ ] **s86(4) timing:** is interest recognised at maturity or as it is capitalised monthly, and how should Kazi record the IT3(b) split?
- [ ] **Remediation:** how should interest runs already posted on general accounts be reversed, and what should firms tell clients who were shown that interest?

Two factual points the advisor could not confirm from primary sources:

- Whether the Rule 54.14.16.5 misconduct wording, under which non-payment to the Fund is misconduct, survived the 2023 amendment.
- The IRBA s86(5) letter and the 2018 LPFF manual-payment notice, both of which returned access errors.

## Proposed implementation plan

Stop the harm first with a small guard, then treat the rework as its own phase; nothing beyond step 1 should start before you sign off this doc.

1. **Disable interest-run posting.** A small backend guard plus hiding the Post action in the UI. Calculation stays available as a read-only preview, labelled as not a posting. One PR, with full backend verify and the legal e2e suites.
2. **Find affected tenants.** A read-only query listing posted interest runs per tenant and account type, to see whether any design partner is exposed.
3. **Attorney or auditor review** of Open questions, in parallel with step 2.
4. **Requirements, then `/architecture`.** Cover the statement-driven posting model, the LPFF ledger and creditor line, the new transaction types, the reconciliation formula, the s86(4) per-investment interest path, and the Rule 54.15 and 54.28 reports. Add a new ADR superseding ADR-234 and ADR-235.
5. **`/breakdown` and build** as a phase, with each slice browser-verified against a reconciliation that actually balances.
6. **Remediation** of posted runs, once step 3 settles how to reverse them and what to tell clients.
7. **Close the open verification:** complete a genuinely balanced reconciliation in the browser, end to end.

The smaller wizard items in Other trust findings (silent recalculation failures, orphaned drafts, the manual-match filter) can ride along with step 5 or go as separate small PRs.

## Sources

The legal findings come from a legal advisor agent (Claude Fable 5.2) that read the primary sources marked below. Supporting searches were plain web searches; no Perplexity tool was connected. Code findings were checked against the repository on 2026-11-16.

| Source | Type | Read in full? |
| --- | --- | --- |
| [Legal Practice Act 28 of 2014, s86 (acts.co.za)](https://www.acts.co.za/legal-practice-act-2014/86__trust_accounts) | Primary, unofficial consolidation | Yes; s86(5) quoted |
| [Government Gazette 49833, Notice 2234 of 2023 (LPC Rules amendment)](https://www.gov.za/sites/default/files/gcis_document/202312/49833gen2234.pdf) | Primary | Yes |
| [LPFF communication, 15 July 2025: compulsory automation of trust interest](https://lssa.org.za/wp-content/uploads/2025/07/Communication-Compulsory-Automation-of-trust-interest-receipts-2025.pdf) | Primary, Fund notice | Yes |
| [LPC accounting rules (acts.co.za)](https://beta.acts.co.za/legal-practice-act-2014/n401_accounting_rules.php) | Primary, unofficial consolidation | Summarised fetch only; may show pre-amendment 54.14.16 text |
| [Acts Online: LPFF automation of trust interest receipts (2025)](https://acts.co.za/news/blog/2025/07/lpff-automation-trust-interest-receipts) | Secondary | Yes |
| [LPFF: Trust bank accounts](https://www.fidfund.co.za/trust-accounts/) | Secondary | No, search result only |
| [De Rebus: Payment of trust interest to the LPFF (2020)](https://www.derebus.org.za/wp-content/uploads/2020/07/Payment-of-trust-interest-accrued-on-trust-accounts-to-the-LPFF-in-terms-of-LPA-and-LPC-Rules_2020.pdf) | Secondary | No, search result only |
| [IRBA: Sweeping of the 5% of interest on s86(4)](<https://www.irba.co.za/upload/Section%2086(5)%20letter.pdf>) | Secondary | No, access error |
| [IRBA: LPFF notice on manual interest payment, Nov 2018](https://www.irba.co.za/upload/LPFF%20Communication%20to%20Practitioners%20for%20payment%20of%20manual%20interest%201%20Nov%202018.pdf) | Primary, superseded | No, access error |
| [SAICA: Analysis of the changes to the legal profession (2019)](https://saicawebprstorage.blob.core.windows.net/uploads/resources/Analysis-of-the-Changes-to-the-Legal-Profession.pdf) | Secondary | No, search result only |
| [SAICA: FAQ on the IRBA guide for legal practitioners' trust accounts](https://saicawebprstorage.blob.core.windows.net/uploads/resources/FAQ_Application_of_the_IRBA_Revised_Guide_for_Registered_Auditors_Engagements_on_Legal_Practitioners_Trust_Accounts_ALL_FAQs.pdf) | Secondary | No, search result only |

**Code read:** `interest/InterestService.java`, `transaction/TrustTransactionRepository.java`, `reconciliation/TrustReconciliationService.java`, `investment/TrustInvestmentService.java`, `InvestmentBasis.java`, `TrustAccountType.java`, `lpff/LpffRate.java`, `adr/ADR-234-interest-daily-balance-method.md` and `adr/ADR-235-statutory-vs-configurable-lpff-share.md`. ADR-362 and ADR-366 were not read.
