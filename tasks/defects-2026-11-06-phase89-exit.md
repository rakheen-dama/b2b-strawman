# Defects and findings — Phase 89 exit gate, 2026-11-06

**Source of truth for ids and severities:** `qa_cycle/evidence/phase89-rewalk/gaps.md` (the G89 register, 27 ids, written by the 636B walkers and confirmed by the orchestrator rulings 0.R3–0.R5 in `HANDOFF.md`). This document re-cuts that register, plus the follow-ups carried out of the Phase 89 slices, into **fix batches** so the work can ship as ordinary bug PRs rather than a new phase. Founder direction 2026-11-06: "these don't sound like they need a new phase to fix."

**Exit verdict (636B, re-walk of 106 rows: 95 PASS · 8 FAIL · 3 PARTIAL):** FAIL on 3 HIGH + 6 MEDIUM. Of the 38 Phase 88 gaps re-verified, 29 held, 5 are PARTIAL on scenario wording, 2 re-opened (G88-F40 → G89-F18; G88-F23 → G89-F12/F22).

**Pre-walk suites (636A, fresh stacks):** lifecycle 105/0, depth-ii 39/0/4 skipped, default 165/0/14 skipped, portal green.

---

## 1. One root cause behind all three HIGHs

Interest credits are posted **per client with no matter**. Every consumer then picks a different ledger:

| Consumer | Ledger it reads | Result |
|---|---|---|
| Refund approval (`TrustTransactionService.java:870-875`) | client ledger-card balance | a matter-linked refund can exceed the matter's balance → **G89-F12** |
| Matter closure gate | matter sub-ledger | "Matter trust balance is -R1,34" → closure blocked on the main Day-75 path |
| Matter Trust tab (`TrustBalanceCard.tsx:68-79`) | client ledger card | shows R 0,00 while the gate says -R 1,34 → **G89-F14** |
| Portal trust read-model (`TrustLedgerPortalSyncService.java:254-255`, `:518`) | per-matter projection; `hasPortalScope` requires a project id | client-level interest never reaches the client; firm R 1,34 vs portal R 0,00 → **G89-F22** |
| Client ledger card totals | deposits / payments / transfers | "Total Payments" excludes REFUNDs → **G89-F13** |

Decision needed before Batch A (founder): **(i)** allocate interest credits to matters (pro-rata to the matter sub-ledgers that earned them, so client and matter balances agree everywhere), or **(ii)** keep interest at client level and make every consumer agree on the client ledger (refund guard, Trust tab, portal projection all client-scoped, with matter sub-ledgers shown as a breakdown). Option (i) is the smaller behavioural surprise for a firm ("the matter earned the interest"); option (ii) is the smaller code change. The walkers' expected-behaviour column says "interest credits carry the matter, so client and matter balances agree" — option (i).

---

## 2. Register, grouped into fix batches

Verify cost: **BE** = full `./mvnw verify` + two reviews + CodeRabbit; **FE** / **PT** = frontend / portal gate only; **DOC** = no gate. One PR per batch unless noted; multi-gap PRs within a batch are same-file-family clusters and need founder authorisation (CLAUDE.md §7), requested here.

### Batch A — trust ledger: client vs matter (HIGH) — BE + FE + PT, one PR after the §1 decision

| ID | Sev | Observed | Where | Fix |
|---|---|---|---|---|
| G89-F12 | **HIGH** | Matter-linked REFUND of R 3 751,34 approved against a matter holding R 3 750,00; matter sub-ledger -R 1,34; closure blocked | `TrustTransactionService.java:671` (`recordRefund`), `:870-875`; `InterestService.java:512-520` | Refund guard checks the **matter** balance when the refund is matter-linked (or interest carries the matter, §1 option i). Reproduce-first integration test: deposit R 5 000 on matter, client-level interest R 1,34, refund R 3 751,34 against the matter → 400 ProblemDetail. |
| G89-F22 | **HIGH** | Portal trust balance R 0,00 vs firm client ledger R 1,34; portal projection drops client-level INTEREST_CREDIT rows | `TrustLedgerPortalSyncService.java:254-255` (`recomputeMatter` reads `findByProjectIdOrderByTransactionDateAsc`), `:518` (`hasPortalScope` needs `projectId != null`) | Project client-level rows into the portal read-model (client-scoped balance row + transaction rows with `matter_id` null) and show them on the portal Trust page; or, under option (i), no read-model change beyond the allocation. Test: `portal.portal_trust_balance` equals the firm client ledger after an interest run. |
| G89-F14 | MEDIUM | Matter Trust tab shows the client card (R 0,00) while the gate reads the matter (-R 1,34); "Funds Held R 1,34" on a CLOSED matter with a R 0,00 sub-ledger | `frontend/components/trust/TrustBalanceCard.tsx:68-79` | Trust tab on a matter shows the **matter** sub-ledger (with the client total as a secondary line). |
| G89-F13 | MEDIUM | Client ledger card "Total Payments R 1 250,00" excludes the R 3 751,34 REFUND; totals do not reconcile to the balance | ledger-card totals (not traced to a line) | Include REFUND in the outflow total, or add a "Refunds" line, so deposits + interest − payments − refunds − transfers = balance. Unit test on the totals builder. |

### Batch B — interest-run period validation (HIGH) — BE, one PR

| ID | Sev | Observed | Where | Fix |
|---|---|---|---|---|
| G89-F11 | **HIGH** (founder may downgrade to MEDIUM) | Interest run for 05→06 Nov created on 5 Nov; calculated, approved and **posted**; INTEREST_CREDIT rows dated tomorrow | `InterestService.java:122-145` (no elapsed-period guard), `:481` | Reject a period whose end is after the firm's today (`KaziTime`), at create and at approve; 400 ProblemDetail "period has not ended". Reproduce-first test with a clock pinned to the period start. |
| G89-F10 | LOW | Interest wizard Calculate table shows client UUIDs and "LPFF Rate: Arrangement" | `frontend/components/trust/InterestRunWizard.tsx` | Show client names; label the rate "LPFF arrangement rate n %". Same PR as F11 (FE gate only, no extra verify). |

### Batch C — statements, reports and client documents (MEDIUM + copy) — BE + FE, one PR (authorisation requested: same document-content family)

| ID | Sev | Observed | Where | Fix |
|---|---|---|---|---|
| G89-F16 | MEDIUM | Closure Statement of Account lists the WRITTEN_OFF R 500 disbursement and prints "Closing balance owing: R500,00" although the fee note is paid; the closure letter says R 2 000 | `DisbursementRepository.java:96-103` (no approval / billing-status filter); `StatementOfAccountContextBuilder.java:312-320` | Statement query excludes WRITTEN_OFF (and un-approved) disbursements; closing balance derives from fee-note balances; closure letter and SoA read the same figure. Golden-file test on the SoA context. |
| G89-F18 | MEDIUM (re-opens G88-F40) | Section 35 Data Pack renders "Sections [object Object]×5", 12 rows "— —", raw "38019.15"; PDF has headings and row counts only | `frontend/components/reports/report-results.tsx:73-86`; `Section35DataPackQuery.java:129-136` | Flatten the sub-report payload to the column shape `report-results` renders (or teach the renderer nested sections); format money. Browser-observed PASS required (G88-F40 was closed on a unit test). |
| G89-F21 | LOW | Closure letter prints "Date: 2026-11-06" (ISO); every other client document prints "6 November 2026" | `template-packs/legal-za/matter-closure-letter.json:101` | Use the long-date helper; pack version bump + reconcile entry (ADR-356). |
| G89-F6 | LOW | Trust Receipt / Client Trust Statement print "Section 78(1) Trust Account" (repealed Attorneys Act) | `template-packs/legal-za/trust-receipt.json:122`, `client-trust-statement.json:122` | "Section 86(2) trust account" (Legal Practice Act 28 of 2014); pack bump + reconcile. Attorney wording sign-off. |
| G89-F19 | LOW | Report picker placeholder "Select a trustaccount..." | `frontend/components/reports/entity-picker.tsx:91` | "Select a trust account…". |
| G89-F26 | LOW | Portal matter page prints the template description "… Matter type: LITIGATION" (raw enum) under the matter name; seeded on all six legal-za templates | `project-template-packs/legal-za.json:9,90,171,252,333,414` | Drop the "Matter type: <ENUM>" suffix from the descriptions; pack bump + reconcile. |

### Batch D — portal correctness (MEDIUM) — BE + PT, one PR

| ID | Sev | Observed | Where | Fix |
|---|---|---|---|---|
| G89-F23 | MEDIUM | Portal "Firm actions" announces "Statement of Account generated" / "Document generated for you" for closure documents saved as **internal drafts** | `PortalActivityEventTypes.java:44-48` (allow-list by event type only); labels `PortalActivityEventResponse.java:107-108` | Filter `document.generated` / `docx_document.generated` / `statement.generated` by the document's portal visibility at projection time; re-project on visibility change. Test: internal draft → no portal event; set PORTAL → event appears. |
| G89-F17 | MEDIUM | Trust › Reconciliation list always "Failed to load reconciliations" (backend returns `List`, frontend reads `.page`); the wizard itself works | `TrustReconciliationController.java:100-104`; `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/actions.ts:51-60` | Return a page (VIA_DTO) or read the list; add a wire-shape contract pin like 631A's. FE-only fix is possible (read the list) — then this moves to Batch E. |

### Batch E — frontend layout and copy (LOW) — FE only, one PR, no backend verify

| ID | Sev | Observed | Where |
|---|---|---|---|
| G89-F7 | LOW | Matter Overview KPI grid is 3 columns; the 4th tile wraps alone (pre-known 634B) | `frontend/components/projects/kpi-dashboard.tsx:331` |
| G89-F8 | LOW | Settings › Documents OTHER table 1621 px inside an 856 px card (pre-known 632C) | not traced |
| G89-F9 | LOW | 1024 fee-note Payment History wraps with a 9 px horizontal scroll (pre-known 632C) | `frontend/components/invoices/PaymentEventHistory.tsx:173-174` |
| G89-F15 | LOW | 1024 matter Documents table 938 px in 654 px; Download column off-screen | not traced |
| G89-F5 | LOW | Matter Overview "HOURS LOGGED 0.0h" stays stale after logging time until a full reload (G88-M9 family; see also the carried G88-M9 fee-note panels) | not traced |
| G89-F20 | LOW | DSAR detail badge prints raw "RECEIVED" / "IN_PROGRESS" / "COMPLETED"; "Processing Started" has no date | `compliance/requests/[id]` |

Carried Phase 88 LOWs in the same family, still open and cited by the walk: G-D10a (audit row prints the event key under the label), G-D10c / G88-F27 ("Access Denied" Title Case), G88-M9 (fee-note panels stale until reload), G88-F24 (Activity "status to DONE / IN_PROGRESS"), G88-F2 (Team "Joined —"), 633C D2 (portal deadline badges in capitals), 633C D6 ("Type DEPOSIT" in the trust email). Fold into Batch E where the file is already open.

### Batch F — tooling and process (LOW) — DOC / scripts, one PR

| ID | Sev | Observed | Fix |
|---|---|---|---|
| G89-F1 | LOW | Root `.dockerignore` does not exclude `frontend/e2e/screenshots/**` | add the exclusion |
| G89-F2 | LOW | `docker builder prune -f` keeps backend layers CACHED; a true cold build needs `-af` / `--no-cache` | fix the gate vocabulary in the phase doc and `e2e-up.sh` notes |
| G89-F3 | LOW | Depth-ii known-skip names in the brief / 634C `e2e-gate.md` misattribute 2 of 4 skips | correct the doc |
| 631A note | — | Legal regression-screenshot baselines under `frontend/e2e/screenshots/legal-*/` are not committed; every fresh worktree's first legal run fails 48 snapshot checks | commit the baselines or make regression screenshots opt-in |
| runner | — | `scripts/run-phase.sh` uses `timeout` without `-k`; a builder that ignores SIGTERM lives past its cap (634A: 229 min) | `timeout -k 120` (lesson recorded 2026-11-05) |

---

## 3. Scenario mismatches — founder §6 rulings, no code

Design proven at file:line; each needs a one-word ruling (amend the scenario, or file as a feature gap):

| ID | Row | Scenario says | Product does |
|---|---|---|---|
| G89-F4 | L 5.3 | Client › "Client comments" | Client › "Comments" (`matter-tab-groups.ts:70`) |
| G89-F24 | P-75.1 / P-75.2 | deadline-approaching nudge email for court dates and prescriptions, with click-through | nudges exist for custom-field dates only (`PortalEmailNotificationChannel.java:48,185`) |
| G89-F25 | P-75.6 | download the related document from the deadline detail panel | the panel has no document (`deadline-detail-panel.tsx:115-172`) |
| G89-F27 | P-3.4 | request state "Submitted" / "Awaiting review" after upload | request lifecycle has IN_PROGRESS = at least one item submitted (`RequestStatus.java:11-12`); the item reads "Submitted" |

Wording-only notes (not filed): 72.1 "withdrawal" is a Payment; 6.2's §5a-row-4 annotation is stale since 634E; the Section 35 "Financial Year End" input is not described by the scenario.

---

## 4. Carried follow-ups from Phase 89 slices (not in the register, not on a walked path)

| Source | Item | Suggested batch |
|---|---|---|
| 634E (1) | Comment lists load only the backend's first page (50, oldest first); a deep link to comment #51+ does not highlight — shared with task comments | later, cross-entity |
| 634E (2) | `CommentService.updateComment` lacks the PROJECT ⇒ SHARED guard that create has; a direct PUT can make a matter comment INTERNAL | **Batch D** (portal/confidentiality boundary; reproduce-first integration test) |
| 634E (3) | Extract the shared PROJECT ⇒ SHARED helper duplicated in the add form and the edit dialog | with (2) |
| 634E (5) | Portal-authored comments show the author as "Unknown" (backend) | **Batch D** |
| 632A (a) | Statements tab lists base-template SoAs only; matter menu lists "Standard Engagement Letter" / "Matter Summary Report" | Batch C if touched |
| 632A (b) | Required-party warnings show raw template paths | Batch C |
| 632A (c) | Generic-menu SoA / closure letter hollow; Trust Receipt / Client Trust Statement print "[… to be inserted]" | Batch C |
| 632C | Identical receipt file names; R1→R2 renumber; receipt template listed under INVOICE | Batch C |
| 634A | Inactive member fields, `dependsOn`, unnamed card buttons, Slug console error | Batch E |
| 634B | Outstanding tile links to a Statements tab whose latest statement shows R 0,00 | verify first (Day-30 statement showed R 5 335,00 owing) |
| 634C | No `@` autocomplete; mention recipients not access-filtered | separate small PR |
| 636B P-E.8 | Day-7 weekly digest (cron Monday 08:00 SAST) never elapsed in the Thu→Fri walk window — DEFERRED, no id | run the digest once by hand on a Monday or trigger the scheduler in a test stack |

Verification standing: G88-F12 attorney wording sign-off and the 631A Keycloak observation of the POPIA export remain MERGED-AWAITING-VERIFY (the latter was observed in 636B S2-26/S2-27 → can be closed in 636C).

---

## 5. Suggested order and cost

| Order | Batch | Gates | Blocks |
|---|---|---|---|
| 1 | §1 decision (founder) | — | Batch A |
| 2 | B — interest period guard (F11, F10) | BE + FE | — |
| 3 | A — client vs matter ledger (F12, F22, F14, F13) | BE + FE + PT | 636C exit re-check |
| 4 | D — portal correctness (F23, F17, 634E (2)(3)(5)) | BE + PT | — |
| 5 | C — documents and reports (F16, F18, F21, F6, F19, F26) | BE + FE | pack bump needs reconcile entries |
| 6 | E — layout and copy LOWs | FE | — |
| 7 | F — tooling | DOC | — |
| 8 | §3 rulings | — | closes F4, F24, F25, F27 |

Four backend verifies in total (B, A, D, C). Each batch is one PR with reproduce-first tests, two bound reviewer verdicts and a browser-observed PASS on the Keycloak stack for the rows that failed in 636B (S2-12, S2-25, S2-45, S2-64 side, S2-14 side). When A, B, C and D are merged, a targeted re-walk of those rows re-scores the exit; no full phase is needed.
