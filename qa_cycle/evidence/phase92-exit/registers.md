# Phase 92 exit — assumptions, open legal points, interest-run correction procedure (652F, exit-bar item 4)

Compiled 2026-11-20 on `epic-652F/exit-gate` = `main` `b08a8916a`. Sources: architecture §16.14.1–§16.14.3,
`requirements/claude-code-prompt-phase92.md` (L1–L12, A1–A6, Out of Scope), ADR-375, ADR-383, `tasks/phase92-decisions.md`,
`qa_cycle/evidence/phase92/legal/F-6-opinion.md`, and the slice evidence cited. `$EV` = `qa_cycle/evidence/phase92`,
`$X` = `qa_cycle/evidence/phase92-exit`, `$B` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting`.

**No assumption was confirmed with a practising trust auditor or tax adviser during the phase.** "Holds" below means the
build relies on the assumption and nothing in the phase contradicted it. It does not mean it has been confirmed.

## 1. Assumptions A1–A6 (§16.14.1)

| # | Assumption | Status | What the phase did |
|---|---|---|---|
| A1 | A transient debit on the Fund interest-and-charges ledger is recorded as it happened and flagged, not blocked. | **Holds** (unconfirmed) | ADR-381 and founder decision 9: no sufficiency check on `FUND_SWEEP` / `BANK_CHARGE`; warning flag `FUND_LEDGER_IN_DEBIT` (`$B/books/TrustIntegrityService.java:84`). Observed in the exit month: G's Fund ledger is R 172,50 in debit on 30 Oct with the flag raised (`$X/README.md:161-162,187`). No firm float was built (out of scope unless an auditor requires it). |
| A2 | Section 86(4) interest may arrive gross or net of the 5 %; both are supported. | **Holds** (unconfirmed) | 648B (PR #1801): `trust_investment_interest` records gross, Fund 5 % and net with an `InterestPresentation` for either form. The exit month records a gross entry (I-A #13: gross 400,00 → Fund 20,00 / client 380,00, `$X/README.md:175`). |
| A3 | Kazi keeps the per-investment figures for IT3(b) and does not file. | **Holds** (unconfirmed) | V24 keeps gross, 5 %, net, date and `client_tax_reference` per entry (`V24__phase92_investment_interest.sql:32`). No export or filing was built (out of scope). Who files in practice is still unconfirmed (requirements A3). |
| A4 | The Fund ledger is one per trust bank account and carries both interest and charges. | **Holds** (unconfirmed) | 646B (PR #1790): one `FUND_INTEREST_AND_CHARGES` creditor account per trust account (`V18__phase92_trust_creditor_accounts.sql:22,29`). Charges debit it (647A). The Rule 54.28 schedule reports per account (650C). |
| A5 | The monthly list is kept for seven years (or five). | **Holds in code**; the period is unconfirmed | 650A (PR #1806): `trust_monthly_lists` rows are never updated or deleted; `backend/src/main` has no delete path for them. 652C's V29 deletes duplicate reconciliations but never one a list references (dec 652C D-1). No retention or purge rule exists, so the five- or seven-year question does not yet affect the code. |
| A6 | Rule 54.14.16.5 (non-payment to the Fund is misconduct) survives the 2023 amendment. | **Holds as copy only** (unconfirmed) | The interest notice that carried it went with the interest page (651C, PR #1810). It now informs only the Section 35 heading "Fund sweeps and their statement lines (Rule 54.14.16)" (`$B/report/Section35DataPackQuery.java:54`). If the assumption is wrong, the change is copy. |

## 2. Open legal points OL-1..OL-9 (§16.14.2)

A slice never settles a point of law (task file R-13; requirements, "Style and boundaries"). **All nine are still open.**
Each was built on its working assumption, chosen so that a different answer changes configuration, copy or a query, not
the data model. No Fable legal-advisor opinion was given on any of OL-1..OL-9 during the phase. The one opinion in the
phase is on F-6, an adjacent point; it is noted after the table.

| # | Point | Working assumption built | Status |
|---|---|---|---|
| OL-1 | What a `SECTION_86` trust account is for Rule 54.28's per-section reporting | Reports under s86(2), like `GENERAL` (`TrustAccountSection`, 648C) | Open |
| OL-2 | May several s86(4) investments share one investment bank account? | Allowed; interest is always recorded against a named investment (648A). The exit month uses one account per client. | Open |
| OL-3 | Is the VAT on a recoverable bank charge the firm's, and does it depend on VAT status? | VAT captured per charge; `CHARGES_TO_REFUND` counts non-recoverable charges only (647A, 649C). Raised in the exit month (`$X/README.md:162`). | Open |
| OL-4 | When may or must an unidentified or unclaimed amount be paid to the Fund (L9)? | Not enforced. Age shown, over-one-year flag, dialog warning for younger items (647D, 649C). | Open |
| OL-5 | Rounding of the Fund's 5 %, and whose figure governs a one-cent difference | Half-up on gross; a typed Fund share within R0,01 is accepted (648B; `TrustAccountingConstants`). The wizard path has no typed share (`$EV/648B/found.md` F-4). | Open |
| OL-6 | Is an identified deposit credited on the identification date or the receipt date? | Identification date (`allocationDate`), receipt date in the description (647D) | Open |
| OL-7 | Must a client ledger in debit be corrected or acknowledged before a month's reconciliation completes? | Flag only; completion not blocked (649C) | Open. Founder ruling #5 ("a client ledger in debit is flag-only (OL-7 stays open)") is a product ruling, not an answer on the law. The F-6 opinion lists OL-7 as related. |
| OL-8 | Rule 54.15: per person only, or per matter? What does "each balance noted in the ledger" require? | Per person, with a per-matter breakdown frozen alongside; the list id is recorded on the reconciliation (650A) | Open |
| OL-9 | Are recoverable charges on one trust account set off against interest on another? | No: one Fund ledger and one schedule block per account (650C). The schedule shows no firm totals (`$X/found.md` F-652E-2). | Open |

**Adjacent opinion, F-6** (`$EV/legal/F-6-opinion.md`, Fable, signed off by the founder 2026-11-18). A trust banking
account's cashbook in debit after an s86(3) placement: block firm-initiated outflows at approval unless the cashbook covers
them, never block recording bank facts, and raise a `CASHBOOK_IN_DEBIT` flag. It is not built; it is follow-up FU-9
(`defect-register.md`, 648A F-6). The opinion leaves its own point open: "a trust auditor or the LPC to confirm whether
'trust bank accounts' in Rule 54.14.10 includes investment accounts, and whether an overdrawn s86(2) account is
reportable regardless of cover."

## 3. Correction procedure for a posted calculated interest run (text only)

> **Not legal advice.** A working procedure for the firm's attorney to adapt. Under the legal-basis rule (founder
> decision 4) it needs attorney review and the founder's sign-off before any firm relies on it. No tooling is built
> for it (requirements, Out of Scope; ADR-383).

**Why it exists.** No real tenant posted a calculated run, and environments are reset (founder decision 5). The phase
removed the run in stages: 645A (PR #1786) made posting and approval refuse. 646A's V16 (PR #1788) deleted every
`INTEREST_CREDIT` / `INTEREST_LPFF` posting and its reversals without a contra. 651A's V27 (PR #1811) and 651E's V28
(PR #1812) dropped the run and rate tables. Audit rows stay (ADR-383). Interest is now recorded only from what the bank
did: `FUND_INTEREST` / `FUND_SWEEP` from statement lines (647A, PR #1794) and per-investment s86(4) entries (648B, PR
#1801). This procedure covers a future case: a restored backup, or a firm that acted on a posted run before it migrated.

**The law it applies** (requirements L1, L2). Interest on s86(2) and s86(3) money vests in the Fund under s86(5)(a); no
client is entitled to any of it. Only s86(4) interest is the client's, less 5 % to the Fund under s86(5)(b), and only as
the bank credited it.

1. **Identify.** From the audit trail (`interest_run` events) and any export taken before migration, list each run's
   credited postings: client credits (`INTEREST_CREDIT`), Fund entries (`INTEREST_LPFF`), dates, amounts, clients,
   matters. List every later entry that relied on them, such as a payment or fee transfer drawn from credited "interest",
   and every statement or portal view that showed them.
2. **Remove the credits.** Precondition: the schema has the Phase 92 reversal model (V17, 649B). A schema not yet
   migrated is migrated first; its V16 then deletes every `INTEREST_CREDIT` / `INTEREST_LPFF` posting, so those need no
   entry. Where calculated interest still stands on the migrated schema as an ordinary entry (re-keyed under another
   type), reverse each with a contra reversal dated in the open period, reason "Calculated interest, no bank evidence
   (s86(5))". Never date one into a locked or already-reported month. Do not rely on 650A's `BACKDATED_AFTER_LOCK` flag
   to catch that: it sees only months locked in Kazi after migration.
3. **Record what the bank did.** For each s86(2) / s86(3) account, record the statement's interest line as Fund interest
   and the sweep line as the Fund sweep, using Record from statement. For each s86(4) investment, record each bank credit
   from the investment account's statement or bank advice: gross, Fund 5 %, net.
4. **Money already paid out.** A payment or fee transfer drawn from credited interest does not by itself create a
   shortfall. Check each affected client's ledger after step 2's removals (V16's deletions included). Only where it is
   negative (`CLIENT_LEDGER_IN_DEBIT`) does the firm make good, and only that negative amount: a deposit from its
   business account with a clear reference. The money left the bank, so do not reverse the payment. The attorney decides
   whether a Rule 54.14.10 / 54.14.11 report to the Council is due.
5. **Client records.** Regenerate the affected statements of account and client trust statements. Tell each affected
   client in writing that the earlier figures included interest that was not theirs. On a persistent database the portal
   may still show old interest rows, and replaying the portal does not remove them: `recomputeMatter` only upserts
   (`$X/defect-register.md` 646A obs-3, 648D F-2). Engineering removes or rebuilds those rows in a controlled way under
   a follow-up; until then they are an accepted residual, named as such to the client and the auditor.
6. **Reconcile and lock.** Reconcile the open month (the full equation; completion gate G1–G4). Confirm that every
   integrity flag is explained. Lock the Rule 54.15 list. A locked month cannot be reopened or unlocked (out of scope),
   so corrections are new postings dated in the open period. The after-lock schedule shows only postings dated on or
   before the locked period end, so it does not show them. Direct the auditor to the open period's ledger and
   reconciliation (the month in which the corrections are dated) and to the audit trail. Keep the working papers for
   the Section 35 report.
