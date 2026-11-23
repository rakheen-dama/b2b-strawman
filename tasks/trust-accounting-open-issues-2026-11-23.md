# Trust accounting — open issues (2026-11-23)

**Status of Phase 92's exit bar: NOT YET MET.** Every row the exit register (`qa_cycle/evidence/phase92-exit/defect-register.md`) listed as an open HIGH or MEDIUM on 2026-11-20 is now fixed or closed (follow-up PRs #1823–#1842). But the follow-up builders found **MEDIUM-class residuals of the same defect classes** (§1). Those residuals were never added to the register. The register's own rule is that a pending ruling does not close a row, so they count against exit-bar item (3), "0 open HIGH or MEDIUM trust defects". The independent review of the closure PR (#1843, REQUEST_CHANGES @616d915) found this gap. **#1843 is held. It must not merge with its PASS claim** until the §1 items are fixed or ruled.

What *is* green, on `main` `ef0563fe3` (2026-11-23, `qa_cycle/evidence/phase92-exit/closure-2026-11-23/` on branch `docs/phase92-exit-gate-pass`):

| Gate | Result |
|---|---|
| Full backend verify | 7 557 testcases / 0 failures |
| Harness | 70 / 70 (exit-bar item 2 met) |
| Frontend | 4 633 passed |
| Portal | 292 passed |
| E2E exit month | 16 / 16 (item 1 met) |
| E2E lifecycle | 105 / 105 |
| E2E depth-ii | 40 / 4 |
| E2E default | 168 / 0 / 12 |
| E2E portal | 34 / 34 |

Item (4), the registers, is unchanged and met.

Severity words follow the register's rubric. A *missing refusal* that lets a client ledger go into debit as at a past date with no flag is MEDIUM (F-646D-1, 649B S2). So is a *deadlock with no corruption* (CF-3b).

---

## 1. MEDIUM-class residuals — block the exit bar (fix or rule)

### 1.1 Back-dated outflows are cover-checked only as at today (class F-646D-1 — MEDIUM)

FU-5 (#1827) and F-646D-1 (#1835) fixed this for **deposit reversals** and **fee transfers**. A back-dated row of either kind must now be covered by the client balance and the matter cell at their lowest point from the row's date on (`TrustTransactionService.requireClientCovers(transaction, coveredFrom)` + `TrustLedgerQueryRepository.lowestClientBalanceFrom` / `lowestMatterCellBalanceFrom`). The same gap remains on the outflows below (evidence: `qa_cycle/evidence/phase92-followups/F-646D-1/notes.md` "Other outflows with the same gap"; code `TrustTransactionService.performApprovalCompletion`, where only `FEE_TRANSFER` passes a `coveredFrom` date):

| Outflow | Date source | Check today | Fix |
|---|---|---|---|
| `PAYMENT` (`recordPayment` → approval) | `transactionDate`, any past date (editable in `RecordPaymentDialog`) | current client balance only | one line at the same call site: pass `coveredFrom` for any back-dated client outflow |
| `REFUND` (`recordRefund` → approval) | `transactionDate` (`RecordRefundDialog`) | current only | same |
| `TRANSFER_OUT` (`recordTransfer`, posts at once) | `transactionDate` (`RecordTransferDialog`) | `clientBalance(source, null)` + matter cell (null) | use the lowest-from helpers for the source client and matter |
| `INV_PLACE`, s86(4) placement (`TrustInvestmentService`) | `depositDate` | `matterCellBalance(…, null)` at request, current client at approval | same helpers at both points |

**Reachable from the UI.** The payment, refund and transfer dialogs all accept a past date; past months show only a `LockedMonthWarning`. **Effect:** the client ledger (or matter cell) can be in debit as at past dates, with no flag (the integrity check scans today and the date asked). **Ruling basis:** S2 / FU-5 D-1 ("as at the reversal date as well as today" → lowest balance from the date on), extended by analogy in F-646D-1 D-1. **Proposed:** one cluster PR covering all four, plus 1.2 (same bug class; CLAUDE.md §7 needs your OK for a cluster).

### 1.2 Back-dated pair reversals are cover-checked only as at today (class 649B S2 — MEDIUM)

`reverseTransferOut` (the receiving client's side of a transfer) and `reverseUnidentifiedAllocation` post at once with a `reversalDate`, but check the current balance only. Evidence: `qa_cycle/evidence/phase92-followups/FU-5/notes.md` (residual risk: "the same class of defect"). Fix: one line each on the same helper. Fits the 1.1 cluster PR.

### 1.3 Possible deadlock: `reverseInterest` vs auto-match (CF-3b class — MEDIUM hypothesis)

`TrustInvestmentService.reverseInterest` locks two transaction rows (the net row, then the Fund-share row) in a fixed order that is **not** transaction-id order. Auto-match (CF-3, #1826) locks the rows it matches in id order, so an auto-match that plans both rows could deadlock against a concurrent interest reversal (40P01 → a generic error; no corruption). Evidence: `qa_cycle/evidence/phase92-followups/CF-3/notes.md` (residual risk). **Not reproduced.** Proposed: a reproduce-first gated test (the pattern from `AutoMatchLockOrderIntegrationTest`). If it goes red, lock both rows in id order in `reverseInterest`; if it does not, close the hypothesis with the guard test.

### 1.5 A retry after a page reload can still post twice (F-11 residual — MEDIUM)

F-11 (#1841) keeps the `Idempotency-Key` in the page, so a resubmit **in the same page** after a lost response replays instead of posting again. After a **reload** (or from a second tab), the unsettled key is gone and the retry sends a new key, which the server treats as a new request. `recordPayment` (and the other trust writes) check only the reference format, not for an existing row with the same reference, amount and client. A retry after a reload therefore adds another `AWAITING_APPROVAL` row (for payments, refunds and fee transfers), and approving both posts twice. For writes that post at once (e.g. a deposit), the second row posts immediately. This is the original 648B F-11 defect ("a resubmit after a lost response posts twice") still reachable through a reload, so F-11 is **partly** closed (CodeRabbit, #1844). Fix options: (a) keep an unsettled request's key across reloads in `sessionStorage`, keyed by the form and its request content, so a reload and resubmit reuse it (frontend only; doesn't cover a second device); (b) have the server replay an unsettled matching request (risks refusing a genuine identical second entry). Proposed: (a), plus a visible "a submit of this entry may already have been recorded — check the ledger" hint when a stored unsettled key exists.

### 1.4 Not yet rated — rate before re-closing

- **Documents build their trust context twice** (FU-3 D-1). `GeneratedDocumentService.generateDocument` builds the trust context once for `containsTrustData` and once for the PDF. A client's first-ever trust posting landing between the two could store a PDF that prints trust money while it is flagged as not containing trust data, which bypasses the G89-F52 download check. Proposed fix: derive the flag from the PDF's own context. Needs a rubric rating; the reviewer of #1843 suggests rating it in the same pass as 1.1–1.3.

**After §1:** re-run the exit gate on the new `main` (scripted, unattended, ~1 h: `exit-gate.sh` in the closure evidence), add the §1 rows to the register with their dispositions, then merge a corrected #1843.

---

## 2. Decisions waiting on the founder

| # | Question | Context | Recommendation |
|---|---|---|---|
| D-1 | Extend "covered from its date on" to all back-dated outflows (§1.1/§1.2)? | Rulings so far cover deposit reversals (S2) and, by analogy, fee transfers | **Yes**, one cluster PR |
| D-2 | Interest reversal has no reason. `POST /api/trust-investment-interest/{id}/reverse` takes no body and writes a fixed reason ("Section 86(4) interest entry reversed"). Every other trust reversal requires one (`ReverseRequest`). | The audit trail never says why an interest entry was reversed (648B F-1 D-1) | Require a reason, like every other reversal (small backend + dialog change). Legal check optional |
| D-3 | F-6 opinion: the trust auditor / LPC to confirm the Rule 54.14.10 reading before the `CASHBOOK_IN_DEBIT` wording is relied on | `qa_cycle/evidence/phase92/legal/F-6-opinion.md` (signed off; this point open) | Ask your auditor |
| D-4 | FU-17..FU-25: 652F's proposed follow-ups (652F D-3) were never ruled | Most are now done under other ids (CF-3, F-646D-1, 648D, F-646E-2, 648B F-1/F-11, 647C #2, 646C O-2, FU-27) | Mark them superseded in the decisions log |
| D-5 | Open legal points OL-1..OL-9 and assumptions A1–A6 | `architecture/phase92-trust-books-provably-right.md` §16.14; carried unchanged | No action until a legal question becomes blocking |

---

## 3. Follow-up candidates — fail-safe (no wrong posted figure; LOW)

**Reconciliation and matching**
- **Auto-match runs line by line, so tier 1 (reference) does not come first** (FU-16 D-2; `qa_cycle/evidence/phase92-followups/FU-16/probe-tier1-not-first.txt`, pre-existing on `main`). An unrelated same-amount, same-day debit can AUTO_MATCH a payment at 0.80 and leave that payment's own referenced line unmatched. G4 holds the month. Fix: a tier-1 pass over every line first, then tiers 2–4.
- **The wizard pane offers contras as manual-match candidates for any same-amount line** (F-652B-1). A person can still pair a reversal with an unrelated line by hand.
- **646C O-2 remainder.** Two coincidental stray auto-match links of opposite sign and equal total, on an undiscarded wrong statement, cancel in G1. A completed reconciliation could then under-list both outstanding items (#1840 notes).
- **A draft reconciliation cannot be abandoned in the UI.** A wrong statement used by a draft can be discarded or replaced only with a statement that fits the draft's period (FU-6b D-3). Non-blocking warning only.
- **No "Replace" action from the statement list** for a corrected file with a different end date (FU-6b D-3).
- **The late-upload notice is not shown after the wizard unmounts** (FU-6b D-3).
- **The step-4 refusal could name Discard.** The reconciliation history table is primary-account only.
- **The FU-16 (c) wire** exposes the statement-only type set to the frontend (F-652H-2, F-652B-1/5).

**Reports, documents, audit**
- **FU-12 residual.** A crash between a report's commit and its audit commit loses that audit row. A saturated pool times the audit write out (ERROR logged).
- **The `ReportExecutionServiceTest` audit assertion** is still `isNotEmpty()` / `getFirst()`. It is now backed by the id-scoped integration test.

**Idempotency (F-11, #1841)**
- **BigDecimal parsing in `TrustRequestHasher`.** Amounts differing beyond about 15 significant digits hash equal. This is reachable only by key misuse.
- **The coverage guard does not assert that annotated mappings are under `/api/`.**
- **409 copy.** The "in progress" message suits postings but not match, approve or complete. A request that is genuinely in flight should say "wait a moment, then check".
- **A reload or second tab after a lost response gets a new key.** Keys expire after 7 days.

**UI**
- **A partial or malformed client UUID in the Transactions filter shows the generic error card**, the same as Matter ID (F-646E-2 D-1). A client picker would fix both.
- **App-wide: `components/ui/dialog.tsx` hard-codes `sm:max-w-lg` on the outer wrapper**, so `sm:max-w-2xl` dialogs probably never widen. The interest-entries confirm panel could `scrollIntoView`.
- **The investments page's interest-entries gating has no page-level test** (648B F-1 D-1).

**Tests and docs hygiene (LOW review notes)**
- **FU-27:** `harness.md` still says "69 rows" and has a stale helper line ref. One redundant `doesNotContain`. The path-11 debtor should be tied to the harness oracle.
- **646C-O2 guard:** thin ProblemDetail assertions.
- **F-646E-2:** cosmetic `CAST AS LocalDate` and the fully qualified `java.time.LocalDate`.
- **CF-3:** auto-match `MatchResult.alreadyMatched` is now read at the end of the run. A skipped match does not fall back to another candidate.

---

## 4. Original follow-ups still open (from `tasks/phase92-decisions.md`)

| FU | Item | Note |
|---|---|---|
| FU-7 | SoA label "Trust balance held" (general only) vs CTS "Held in trust" (general + invested); `legal-za` template pack version bump + reconcile | LOW, wording (648F O-1) |
| FU-10 | After-lock boundary uses app-host `clock.instant()`; order by a DB-side value instead | LOW (F-13); red test with a skewed clock first |
| FU-14 | Postings dated before a trust account's `opened_date` are accepted | Proposed: an integrity flag, not a refusal |
| FU-15 | Merge-gate hook `gh pr diff --name-only` fails above 300 files (HTTP 406) | Tooling; fall back to `gh api …/pulls/N/files --paginate` |
| FU-26 | `backend/db/BASELINE.md` next-free numbers are stale (says V16 / V7) | **Next free: tenant V32, global V8** (V30 and V31 landed in #1831 and #1841). A `backend/*.md` edit trips the merge-gate marker rule, so it goes with the next backend PR |

---

## 5. LOW register rows carried to the backlog

**Gate basis for exit-bar item (3): source severity.** The register counts open HIGH/MEDIUM by **source severity**, not by its rubric: register notes (2026-11-20) note 4, "Source severity kept where the rubric reads higher", and the 652F closure headline "by the register's rule that source severity wins" (`tasks/phase92-trust-books-provably-right.md` § Closure report C.1). That rule was applied in both directions in Phase 92 (CF-3a and F-650C-2 were counted at source severity too), so the two rubric-MEDIUM LOW rows below are outside item (3) by that rule, not by omission. If the founder wants the rubric to govern instead, these two join §1.

`qa_cycle/evidence/phase92-exit/defect-register.md` (as updated on `docs/phase92-exit-gate-pass`): **116 LOW rows** are open (7 assigned to a follow-up, 104 unassigned, 5 LOW hypotheses). Two rows are LOW by source but **MEDIUM under the rubric**: they show a misleading figure, but the source severity is kept.

- **648E F-4.** `TrustBalanceCard` in client mode: a fully invested client shows "No Funds" / R0.00 above "Total held in trust R30 000".
- **F-652E-3.** The trust-position summary cards stay firm-wide when the report is filtered to one account.

API-only rows, unreachable from the UI today: 652D (i) (a client with money on two open GENERAL accounts sees one account on the SoA and CTS), F-652D-O3, 652D (iii), §16.14.4 #9.
