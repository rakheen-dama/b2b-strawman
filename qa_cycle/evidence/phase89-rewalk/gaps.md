# Phase 89 exit gate — 636B targeted Keycloak re-walk — G89 register

Walkers 636B.1 (2026-11-05 21:34–22:53 SAST), 636B.2 (22:54–23:26 SAST) and 636B.3 (2026-11-06 00:42–01:20 SAST, inside 00:00–02:00 under the founder's amendment). Stack `86e0fd5de` (KC dev, fresh tenant `tenant_5039f2d497cf`, D-1 option A). Step evidence: `steps/*.md`; candidate lists: `findings-session1.md` (old numbering), `findings-session2.md`, `findings-session3.md`. Severities: HIGH/MEDIUM marked **(confirmed)** were confirmed by the orchestrator (rulings 0.R3, 0.R4, 0.R5 — see the **Rulings index** at the top of `HANDOFF.md`); the rest are **proposed** and need orchestrator confirmation. This file supersedes the walkers' candidate lists for ids and severities.

## 1. Summary

**Exit test (`$TASK:675`: 0 HIGH + 0 MEDIUM open): FAIL.** Open: **3 HIGH** (G89-F11, G89-F12, G89-F22 — F11 recorded HIGH; the founder may downgrade it to MEDIUM in 636C if ruled an input-validation edge, ruling 0.R4 item 2) + **6 MEDIUM** (G89-F13, F14, F16, F17, F18, F23) + 18 LOW — every HIGH/MEDIUM confirmed by the orchestrator (rulings 0.R3 and 0.R5 below). Verdict FAIL (ruling 0.R3: "exit verdict FAIL").

**Ruling 0.R5 (orchestrator, 2026-11-06 ~01:20 SAST, after 636B.3; code read at `86e0fd5de`):** G89-F14 MEDIUM, F16 MEDIUM (`DisbursementRepository.findForStatement:96-103` has no approval/billing-status filter), F17 MEDIUM (`TrustReconciliationController.listReconciliations:100-104` returns `List`, `reconciliation/actions.ts:51-60` reads `result.page.*` — a wire-shape mismatch present since #936/#948; the wizard works), F18 MEDIUM (G88-F40 precedent) — **confirmed**. **G89-F22 raised MEDIUM → HIGH**: the client-facing portal trust balance disagrees with the firm's client ledger card (client-level interest credits never reach the per-matter portal read-model) — trust money path, Phase 88 O-4 rubric; same root cluster as F12. **G89-F23 raised LOW → MEDIUM**: the portal activity feed tells the client that documents saved as internal drafts were generated — a new product failure on the client-confidentiality boundary (content not exposed, hence not HIGH). Scenario mismatches F4, F24, F25 stay LOW and await founder §6 rulings (non-blocking).

Scored rows walked: **106** (PASS 95 · FAIL 8 · PARTIAL 3 · DEFERRED 0) + 2 N/A rows (4 `Removed —` step lines); S2-13 (75.2–75.8) is scored per ruling 0.R4. Every §4.1 / §4.2 row was driven; no scored row is DEFERRED. One **exit-checkpoint sub-item** is DEFERRED: P-E.8's Day-7 weekly digest (cron Monday 08:00 SAST, `PortalDigestScheduler.java:106`, never elapsed in the Thu→Fri walk window — not a product failure, no id; `steps/exit-checkpoints.md` P-E.8).

| Severity × class | Product bug | Scenario mismatch (authorisation needed — design proven) | Missing feature / scenario-internal | Process / tooling | Total |
|---|---|---|---|---|---|
| BLOCKER | 0 | 0 | 0 | 0 | 0 |
| HIGH | 3 (F11, F12, F22) | 0 | 0 | 0 | **3** |
| MEDIUM | 6 (F13, F14, F16, F17, F18, F23) | 0 | 0 | 0 | **6** |
| LOW | 11 (F5, F6, F7, F8, F9, F10, F15, F19, F20, F21, F26) | 4 (F4, F24, F25, F27) | 0 | 3 (F1, F2, F3) | **18** |
| **Total** | 20 | 4 | 0 | 3 | **27** |

Next free id: **G89-F28**.

*Counts updated 2026-11-06 after the CodeRabbit review of PR #1742: S1-65 PASS → PARTIAL (PDF/preview layout compared only for the region the preview frames show), S2-41 PASS → FAIL as written (new G89-F27, scenario mismatch), S2-56 PASS → FAIL as written (G89-F24 root, no new id).*

Clusters worth one fix each: **trust client-vs-matter ledger** (F12 HIGH, F22 HIGH, F14; F13 is the same ledger card) — interest credits are posted per client with no matter, while the matter gate, the matter tab, the Overview tile and the portal read-model each pick a different ledger; **interest-run period validation** (F11 HIGH); **statement / report content** (F16 SoA counts written-off disbursements, F18 Section 35 pack hollow, F21 closure-letter date).

## 2. Closed-gap re-verification (38 rows)

| Gap | Sev (P88) | Closer | Scored rows (this walk) | Result | Evidence |
|---|---|---|---|---|---|
| G88-F48 | HIGH | #1716 | — (suite-only, D-4) | suite-covered (636A), not walked — 636B.3 ran inside 00:00–02:00 SAST and every in-window date it saw was the SA date: CORR deposit dated 2026-11-06 at 22:44Z, closure "6 Nov 2026", retention "06 Nov 2031", diary week "2–8 November", "7d remaining", DSAR "06 Nov 2026, 00:59", portal "Paid on 6 Nov 2026" (incidental NOTE observations, not scored) | `suites/README.md`; `steps/days-46-90.md` CORR, S2-13, S2-19, S2-16, S2-17, S2-28; `steps/portal.md` S2-46 |
| G88-F49 | LOW | #1716 | — (suite-only, D-4) | suite-covered (636A), not walked — same in-window observations as F48 | as above |
| G88-F33 | HIGH | #1717 | S2-14, S2-51 | VERIFIED (D-6 fallback: Day 30 = 5 Nov, closure = 6 Nov SAST; scored on distinct uuid-segmented `s3_key`s + byte equality) | `logs/db-75.9.txt`, `logs/P-60.5-compare.txt` |
| G88-F12 | HIGH | #1718 | S1-49, S1-51, S1-53 | VERIFIED (bindings; attorney wording sign-off stays MERGED-AWAITING-VERIFY) | `steps/days-08-21.md` |
| G88-F38 | MEDIUM | #1719 | S1-58 | VERIFIED | `steps/days-22-45.md` |
| G-D10b | LOW | #1719 | S2-23 | VERIFIED | `steps/days-46-90.md` S2-23 |
| G88-F17 | LOW | #1719 | S1-73, S1-79, S2-02 | VERIFIED | `steps/days-22-45.md`, `days-46-90.md` |
| G88-F46 | LOW | #1719 | S1-67 | VERIFIED | `steps/days-22-45.md` |
| G88-F36 | LOW | #1720 / 634C | S1-35, S1-68 | VERIFIED | `steps/days-01-07.md`, `days-22-45.md`, `logs/db-6.2.txt`, `db-33.1-C2.txt` |
| G88-M10 | LOW | #1720 | S1-75, S1-76, S2-47 | PARTIAL — S1-76 is PARTIAL (wow-6 frame taken from the HTML preview; banking block never configured). The M10 property itself (tariff line last) holds on S1-75, S2-47 and the portal-downloaded INV-0002 PDF (`logs/P-45.6-fee-note-INV-0002.pdf`) | `steps/days-22-45.md`, `portal.md` |
| G88-F47 | LOW | #1721 | S2-29 | VERIFIED | `logs/typecheck.txt` |
| G88-F40 | MEDIUM | 630A | S1-16, S2-25 | **FAIL → G89-F18** (7 cards PASS on both runs; the Section 35 Data Pack renders no sub-report data) | `steps/day-00.md`, `days-46-90.md` S2-25 |
| G88-F39 | MEDIUM | 630B | S2-03 | VERIFIED | `steps/days-46-90.md` S2-03 |
| §14.12 #2 | — | 630B | S2-03 | VERIFIED | same |
| G88-F23 | MEDIUM | 630C | S2-12, S2-45 | **FAIL → G89-F12, G89-F22** — the running-balance defect F23 named is gone (every row = previous ± amount; REFUND an outflow, S2-12), but S2-12 is FAIL by ruling 0.R4 (G89-F12) and the firm/portal balance check S2-45 FAILs (G89-F22) | `steps/days-46-90.md` S2-12, `portal.md` S2-45 |
| C5 | LOW | 630C | S2-15 | VERIFIED | `logs/db-C5.txt` |
| G88-F43 | MEDIUM | 631A | S2-26 | VERIFIED | `logs/POPIA-1-export-archive.txt` |
| G88-F44 | MEDIUM | 631A | S2-27 | VERIFIED | `screenshots/POPIA-2-*` |
| C3 | LOW | 631B | S2-28 | VERIFIED (observed at 22:59Z, inside the zone-sensitive window) | `logs/db-C3.txt` |
| G88-F16 | MEDIUM | 632A | S1-63, S1-65, S1-67, S2-14, S2-51 | PARTIAL — S1-65 is PARTIAL (30.6 "identical layout" compared only for the region the preview frames show; no deviation). The F16 property itself (letterhead, no blank "VAT Reg:" / "File reference:" labels) holds on S1-63, S1-65, S1-67, S2-14 and S2-51 | `logs/30.6-soa.pdf`, `75.9-closure-letter.pdf` |
| C1 | LOW | 632A | S1-50, S1-52 | VERIFIED | `steps/days-08-21.md` |
| C6 | LOW | 632A | S1-61, S1-66 | VERIFIED | `steps/days-22-45.md` |
| G88-F32 | MEDIUM | 632B / 632C | S2-01, S2-48, S2-53 | VERIFIED | `logs/55.2-INV-0001-R1.pdf`, `P-45.6.txt`, `SIPHO-RCPT.txt` |
| G88-F19 | MEDIUM | 633A | S2-07 | VERIFIED | `steps/portal.md` / `days-46-90.md` S2-07 |
| §5b row 19 | — | 633B / 633C | S2-55, S2-56, S2-57, S2-59, S2-60 | PARTIAL — the product rows S2-57 (incl. judge / firm note absent from the DOM) and S2-60 PASS; S2-56's landing + "Due soon" flag observed by navigation; S2-55, S2-56 (no email to click through) and S2-59 FAIL **as written** (scenario mismatches: S2-55 and S2-56 G89-F24, S2-59 G89-F25) | `steps/portal.md` |
| G88-F20 | LOW | 633C | S2-07, S2-50 | VERIFIED | `steps/portal.md` |
| G88-F29 | LOW | 633C | S2-39, S2-41 | PARTIAL — S2-41 FAILs **as written** (request state "In progress", not "Submitted" / "Awaiting review" — scenario mismatch G89-F27). The F29 property itself (humanised request / item labels, raw enums 0) holds on S2-39 and S2-41 | `steps/portal.md` |
| G88-F30 | LOW | 633C | S2-38 | VERIFIED | `steps/portal.md` |
| G88-F31 | LOW | 633C | S2-44 | VERIFIED | `steps/portal.md` |
| G88-F3 | MEDIUM | 634A | S1-13, S1-14 | VERIFIED | `steps/day-00.md`, `logs/db-0.48.txt` |
| G88-M13 | MEDIUM | 634B | S1-30, S1-34, S1-80, S2-20 | VERIFIED | `steps/days-01-07.md`, `days-22-45.md`, `days-46-90.md` |
| G88-M2 | MEDIUM | 634B | S1-23, S1-42, S1-60, S2-05 | VERIFIED | step files |
| G-B8 | MEDIUM | 634B | S1-28 | VERIFIED | `steps/days-01-07.md` |
| G88-M6 | MEDIUM | 634C | S1-36, S1-38 | VERIFIED | `steps/days-01-07.md` |
| C2 | LOW | 634C | S1-35, S1-68, S1-69 | VERIFIED | `logs/db-33.1-C2.txt` |
| G88-M4 | MEDIUM | 634D | S1-32, S1-33, S1-77 | VERIFIED | step files |
| §14.12 #6 / B7 | — | 634E | S1-29, S1-38 | PARTIAL — product path PASS (S1-38; S1-29's product path recorded clean); S1-29 FAIL **as written** (scenario mismatch G89-F4) | `steps/days-01-07.md` |
| G-D5 | — | 635A / 617C | S2-16, S2-17 | VERIFIED — **observed closed** (Hearing today + 7 in Bob's "Upcoming · next 14 days") | `steps/days-46-90.md` |

Tally: **29 VERIFIED · 5 PARTIAL (M10, F16, F29, §5b row 19, §14.12 #6/B7) · 2 FAIL (F40 → G89-F18; F23 → G89-F12/F22) · 2 suite-covered, not walked (F48, F49)** = 38.

## 3. Register

Numbering per ruling 0.R2: F1–F3 = the 636A tooling notes; session-1 candidates F1/F2/F3 (`findings-session1.md`, old numbering) are **F4/F5/F6** here (step files updated to the new ids by 636B.3). Owners: the closing epic for re-opened behaviour, "new" for defects no Phase 89 slice owned, "founder §6" for scenario wording.

| ID | Severity | Step(s) | Class | Observed (one line) | file:line | Owner | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| G89-F1 | LOW | 636A (suites) | process / tooling | Root `.dockerignore` does not exclude `frontend/e2e/screenshots/**` (636A-c1) | `.dockerignore` "Re-deny heavy/generated paths" block | Epic 636 / tooling | `suites/README.md` :105 | open |
| G89-F2 | LOW | 636A (suites) | process / tooling | `docker builder prune -f` keeps backend layers `CACHED`; "cold build" needs `-af` / `--no-cache` (636A-c2) | gate vocabulary (636.1 / E2E-LEGAL) | Epic 636 / phase doc | `suites/README.md` :95, :110 | open |
| G89-F3 | LOW | 636A (suites) | process / tooling (doc) | Depth-ii known-skip names in the brief / 634C `e2e-gate.md` misattribute 2 of 4 skips (636A-c3) | 634C `e2e-gate.md` | Epic 636 / doc | `suites/README.md` :98, :111 | open |
| G89-F4 | LOW | S1-29 (L 5.3) | scenario mismatch — design proven | Client menu sub-tab is "Comments", scenario says "Client comments" | `frontend/lib/constants/matter-tab-groups.ts:70`; scenario `legal-za-90day-keycloak.md:273` | founder §6 | `steps/days-01-07.md` S1-29 | open |
| G89-F5 | LOW | S1-30 side | product bug (staleness) | Matter Overview "HOURS LOGGED 0.0h" after logging time until a full reload (G88-M9 family) | not traced (hypothesis) | new | `steps/days-01-07.md` S1-30 | open |
| G89-F6 | LOW | S1-67 side | product copy (regulatory) | Trust Receipt / Client Trust Statement print "— Section 78(1) Trust Account" (repealed Attorneys Act) | `template-packs/legal-za/trust-receipt.json:122`, `client-trust-statement.json:122` | new (632A family) | `screenshots/F16-c-*`, `F16-d-*` | open |
| G89-F7 | LOW | S1-80 side | product bug (layout) | Overview KPI grid is 3 columns, the 4th tile wraps alone (pre-known 634B) | `frontend/components/projects/kpi-dashboard.tsx:331` | 634B | `screenshots/45.10-*` | open |
| G89-F8 | LOW | S2-01 side | product bug (layout) | Settings › Documents OTHER table 1621 px in an 856 px card (pre-known 632C) | not traced | 632C | `screenshots/55.2-1440-settings-documents-payment-receipt.png` | open |
| G89-F9 | LOW | S2-01 side | product bug (layout) | 1024 fee-note Payment History wraps; 9 px horizontal scroll (pre-known 632C) | `frontend/components/invoices/PaymentEventHistory.tsx:173-174` | 632C | `screenshots/55.2-1024-inv0001-payment-history.png` | open |
| G89-F10 | LOW | S2-03 side | product bug (data display) | Interest wizard Calculate table shows "Client ID" UUIDs and "LPFF Rate: Arrangement" | `frontend/components/trust/InterestRunWizard.tsx` (not traced to line) | 630B | `screenshots/INT-60-1440-calculate-step.png` | open |
| G89-F11 | **HIGH (confirmed)** — recorded HIGH; founder may downgrade to MEDIUM in 636C if ruled an input-validation edge (ruling 0.R4 item 2) | S2-03 | product bug (trust ledger) | An interest run whose period ends tomorrow (05→06 Nov, created 5 Nov) was accepted, approved and posted; INTEREST_CREDIT rows dated in the future | `…/trustaccounting/interest/InterestService.java:122-145` (no elapsed-period guard), `:481` | 630B follow-up (post-Phase-89 bug batch) | `logs/db-INT-60.txt` | open |
| G89-F12 | **HIGH (confirmed)** | S2-12 (FAIL); S2-13 needed the CORR prerequisite (ruling 0.R4) | product bug (trust ledger) | A matter-linked REFUND may exceed the matter's trust balance (client-level interest credit) → matter sub-ledger -R 1,34, closure blocked on the main Day-75 path | `…/transaction/TrustTransactionService.java:671`, `:870-875`; `…/interest/InterestService.java:512-520` | 630C follow-up (post-Phase-89 bug batch) | `logs/db-PRE-75.txt`, `db-CORR-before.txt` | open |
| G89-F13 | **MEDIUM (confirmed)** | S2-12 side | product bug (trust reporting) | Client ledger card "Total Payments" excludes REFUNDs; totals do not reconcile to the balance — correct: R 5 000,00 + R 1,34 interest credit − R 1 250,00 − R 3 751,34 refund = R 0,00; card shows Deposits R 5 000,00 / Payments R 1 250,00 vs Balance R 0,00 (pre-known 630C) | not traced (ledger-card totals) | 630C | `screenshots/PRE-75-1440-sipho-ledger.png`, `P-CP21.2-1440-firm-sipho-ledger.png` | open |
| G89-F14 | **MEDIUM (confirmed, 0.R5)** | CORR / S2-13 side | product bug (trust reporting) | The matter Trust tab shows the **client** ledger card: R 0,00 while the matter gate said -R 1,34; "Funds Held R 1,34" on a CLOSED matter whose sub-ledger is R 0,00 | `frontend/components/trust/TrustBalanceCard.tsx:68-79` | 630C (with F12) | `logs/db-CORR-*.txt`, `screenshots/CORR-1440-matter-trust-tab-after.png` | open |
| G89-F15 | LOW | S2-14 side | product bug (layout) | 1024 matter Documents table 938 px in 654 px; Download column off-screen | not traced | new | `screenshots/75.9-1024-documents.png` | open |
| G89-F16 | **MEDIUM (confirmed, 0.R5)** | S2-14 side | product bug (document content) | Closure SoA lists the WRITTEN_OFF R 500 disbursement and prints "Closing balance owing: R500,00" (fee note paid); the closure letter says R 2 000 | `…/disbursement/DisbursementRepository.java:96-103`; `…/statement/StatementOfAccountContextBuilder.java:312-320` | new (632A area) | `logs/75.9-closure-soa.pdf`, `75.9-closure-letter.pdf` | open |
| G89-F17 | **MEDIUM (confirmed, 0.R5)** | S2-15 side | product bug (wire shape) | Trust › Reconciliation list always "Failed to load reconciliations" (backend `List`, frontend reads `.page`) (pre-known 630C) | `…/reconciliation/TrustReconciliationController.java:100-104`; `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/actions.ts:51-60` | 630C | `screenshots/C5-1440-reconciliation-list-failed.png` | open |
| G89-F18 | **MEDIUM (confirmed, 0.R5)** | S2-25 (FAIL) | product bug (report rendering) | Section 35 Data Pack: "Sections [object Object]×5", 12 rows "— —", raw "38019.15"; PDF = headings + row counts only | `frontend/components/reports/report-results.tsx:73-86`; `…/report/Section35DataPackQuery.java:129-136` | 630A (re-opens G88-F40) | `screenshots/TR-90-*`, `logs/TR-90-section-35-data-pack-report.pdf` | open |
| G89-F19 | LOW | S2-25 side | product copy | Report picker placeholder "Select a trustaccount..." | `frontend/components/reports/entity-picker.tsx:91` | 630A | `screenshots/TR-90-1440-section35-result.png` | open |
| G89-F20 | LOW | S2-28 side | product copy (raw enum) | DSAR detail badge raw "RECEIVED" / "IN_PROGRESS" / "COMPLETED"; "Processing Started" has no date | not traced (`compliance/requests/[id]`) | 631B area | `screenshots/C3-1440-dsar-detail-sa-times.png` | open |
| G89-F21 | LOW | S2-14 side | product copy (date format) | Closure letter prints "Date: 2026-11-06" (ISO) vs "6 November 2026" on every other client document | `template-packs/legal-za/matter-closure-letter.json:101` | 632A area | `logs/75.9-closure-letter.pdf` | open |
| G89-F22 | **HIGH (raised, 0.R5)** | S2-45 (FAIL) | product bug (portal trust read-model) | Firm Sipho client ledger R 1,34 vs portal R 0,00: the portal trust projection is per matter and drops client-level INTEREST_CREDITs | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java:254-255` (`recomputeMatter` projects only `findByProjectIdOrderByTransactionDateAsc(matterId)`), `:518` (`hasPortalScope`: `txn.getCustomerId() != null && txn.getProjectId() != null` — a client-level INTEREST_CREDIT has no project, so it never reaches `portal.portal_trust_transaction` / `portal_trust_balance`) | 633C / 630C (with F12) | `logs/db-P-CP21.2.txt` | open |
| G89-F23 | **MEDIUM (raised, 0.R5)** | S2-64 side | product bug (portal visibility) | Portal "Firm actions" announces "Statement of Account generated" / "Document generated for you" for closure docs saved as internal drafts | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityEventTypes.java:44-48` (allow-list by event type only: `document.generated`, `docx_document.generated`, `statement.generated` — no document-visibility check); labels `portal/PortalActivityEventResponse.java:107-108` ("Document generated for you", "Statement of Account generated") | new | `screenshots/P90.5-1440-activity-firm.png` | open |
| G89-F24 | LOW | S2-55 (P-75.1); S2-56 (P-75.2 click-through, same root) | scenario mismatch — design proven | No deadline-approaching nudge for court dates / prescriptions; the producer is custom-field dates only | `…/portal/notification/PortalEmailNotificationChannel.java:48,185`; PR #1738 item 7; scenario `portal-client-90day-keycloak.md:194` | founder §6 | `logs/mailpit-P75.1.txt` | open |
| G89-F25 | LOW | S2-59 (P-75.6) | scenario mismatch — design proven | Deadline details panel has no related document | `portal/components/deadlines/deadline-detail-panel.tsx:115-172`; PR #1738 item 8; scenario `portal-client-90day-keycloak.md:199` | founder §6 | `screenshots/P75.3-1440-hearing-detail.png` | open |
| G89-F26 | LOW | S2-50 / S2-52 side (portal wow-4 frame) | product copy (raw enum, client-facing) | The portal matter page prints the template description "Standard litigation workflow … **Matter type: LITIGATION**" (raw enum) under the matter name; the same suffix is seeded on every legal-za matter template ("Matter type: ESTATES", …) | `backend/src/main/resources/project-template-packs/legal-za.json:9,90,171,252,333,414` (all six template description strings end in "Matter type: <ENUM>") | new | `screenshots/p-wow-4-document.png`, `P60.2-1440-closed-matter.png` | open |
| G89-F27 | LOW | S2-41 (P-3.4) | scenario mismatch — design proven | After Upload and submit the item reads "Submitted" but the **request** reads "In progress"; P-3.4 expects the request state "Submitted" or "Awaiting review", which the request lifecycle does not have (IN_PROGRESS = at least one item submitted) | `backend/…/informationrequest/RequestStatus.java:11-12`; `…/customerbackend/service/PortalInformationRequestService.java:307-312`; `portal/lib/labels.ts:14`; scenario `portal-client-90day-keycloak.md:98` | founder §6 | `screenshots/P-3.4-1440-submitted.png`, `P-3.4-1024-submitted.png` | open |

Cited, still-open Phase 88 LOWs (not re-filed): G-D10a (audit row key under label — S1-79, S2-02, S2-23), G-D10c / G88-F27 ("Access Denied" Title Case — S2-23), G88-M9 (fee-note panels stale until reload — S1-73), G88-F24 (Activity "status to DONE / IN_PROGRESS" — wow-7, S2-22), G88-F2 (Team "Joined —" — S2-24), 633C D2 (portal deadline badges in capitals — S2-56/57), 633C D6 ("Type DEPOSIT" in the trust email — S2-42).

## 4. Scenario mismatches (founder §6)

| Row | Step | As written | Product (design proof) | Gap |
|---|---|---|---|---|
| S1-29 | L 5.3 | Client › "Client comments" | Client › "Comments" (`matter-tab-groups.ts:70`; PR #1738 out-of-scope item 1) | G89-F4 |
| S2-55 | P-75.1 | Deadline-approaching nudge email for the RAF dates | Nudge only for custom-field dates (`PortalEmailNotificationChannel.java:48,185`; PR #1738 item 7) | G89-F24 |
| S2-56 | P-75.2 | Click through (from the 75.1 nudge email) → `/deadlines` | No nudge email exists (same root as S2-55); landing + "Due soon" flag observed by navigation | G89-F24 |
| S2-41 | P-3.4 | Info-request state → "Submitted" or "Awaiting review" | Request reads "In progress" (`RequestStatus.java:11-12` IN_PROGRESS = at least one item submitted; `PortalInformationRequestService.java:307-312`; `portal/lib/labels.ts:14`); the item reads "Submitted" | G89-F27 |
| S2-59 | P-75.6 | Download the related document from the deadline detail view | Panel has no document (`deadline-detail-panel.tsx:115-172`; PR #1738 item 8) | G89-F25 |

Also recorded (wording only, not filed): 72.1 "withdrawal" is a Payment (PR #1738 item 13, S2-08); 6.2's §5a-row-4 annotation ("no matter-level comment thread") is stale since 634E (scout §8); the Section 35 "Financial Year End" input (S2-25) is not described by the scenario.

**Rulings applied (G89 Batch F, founder D3 2026-11-06).** All four mismatches were amended in the scenario files, each with a one-line justification citing the design proof as it stands on main after Batches A–E:
- G89-F4 → `legal-za-90day-keycloak.md:273` "Comments" (`frontend/lib/constants/matter-tab-groups.ts:70`).
- G89-F24 → `portal-client-90day-keycloak.md:194-195` (P-75.1 / P-75.2): open `/deadlines` directly, no nudge email for court dates / prescriptions, "Due soon" flag at ≤ 7 days (`PortalEmailNotificationChannel.java:48,201-202`, `FieldDateScannerJob.java:193`, `DeadlinePortalSyncService.java:78-79,527-528`). The handler line cited above as `:185` is now `:201-202`.
- G89-F25 → `portal-client-90day-keycloak.md:199` (P-75.6) removed (`deadline-detail-panel.tsx:121-171`, the panel's `<dl>`).
- G89-F27 → `portal-client-90day-keycloak.md:98` (P-3.4): item "Submitted", request "In progress" (`RequestStatus.java:11-12`, `portal/lib/labels.ts:15` — was `:14`).

The three wording-only notes above were **not** amended (D3 authorises exactly the four ids); they stay recorded here: `legal-za-90day-keycloak.md:491` (72.1 "withdrawal" — the product records it as a Payment), `:297` (6.2 — the §5a-row-4 annotation "no matter-level comment thread" is stale since 634E added one), and the Section 35 Data Pack "Financial Year End" input (S2-25) that no scenario step describes. Two lines that depend on the removed P-75.6 were also left as written and need a ruling: the Day 75 heading ("→ download related doc") and the Day 75 checkpoint "Download audit event recorded" (`portal-client-90day-keycloak.md:190`, `:203`).

## 5. Pre-known candidates (brief §8) — disposition

| Source | Candidate | Disposition |
|---|---|---|
| 630C | Reconciliation list "Failed to load" | observed (S2-15) → **G89-F17** |
| 630C | Ledger card "Total Payments" ignores REFUND | observed (S2-12) → **G89-F13** |
| 630C | Future-dated deposit accepted | not exercised directly; a future-dated **interest credit** was produced instead → **G89-F11** |
| 631B | `formatComplianceDate` zone-less (22:00–23:59Z only) | exercised at 22:59Z (S2-28): list + timeline printed the SA date "06 Nov 2026" → **not reproduced** |
| 632A (a) | Statements tab lists base-template SoAs only; matter menu lists "Standard Engagement Letter" / "Matter Summary Report" | menu entries seen (S1-26) — not on a scored path; carried (not re-filed) |
| 632A (b) | Warnings show raw paths | observed (S1-50) — known follow-up, cited, not re-filed |
| 632A (c) | Generic-menu SoA / closure letter hollow; Trust Receipt / Client Trust Statement "[… to be inserted]" | placeholders observed (S1-67) — cited, not re-filed; generic-menu SoA not on the walked path; the closure-flow SoA/letter were populated (S2-14) |
| 632C | Settings › Documents OTHER table overflow | observed → **G89-F8** |
| 632C | 1024 Payment History wraps | observed → **G89-F9** |
| 632C | Identical receipt file names; R1→R2 renumber; receipt template in INVOICE lists | not on the walked path — carried |
| 633A | Backfilled portal tasks share `sort_order 0` | fresh tenant → live path; tasks in template order (S2-07) → not reproduced |
| 633C D2 / D6 / F-1 / F-2 | Raw deadline badges / "Type DEPOSIT" / Home tile 60-day window / `next dev` double-POST | D2, D6 observed and cited (S2-57, S2-42); F-1 not re-observed (Lerato Home not opened); F-2 avoided by the prod build (one exchange POST per sign-in) |
| 634A | Inactive member fields, `dependsOn`, unnamed card buttons, Slug console error, 212 px cards at 1024 | 212 px cards seen, no overflow (S1-13); the rest not on the walked path — carried |
| 634B | 4th KPI tile wraps alone | observed → **G89-F7** |
| 634B | Outstanding tile links to a Statements tab whose latest statement shows R 0,00 | not judged (Day-30 statement shows R 5 335,00 owing) |
| 634C | No `@` autocomplete; recipients not access-filtered | no autocomplete observed (S1-35) — cited; access filter not on the walked path |
| 634E | First 50 comments only; portal-authored comments "Unknown" | not on the walked path — carried |
| 635A out-of-scope items 1–19 | 5.3 / 75.1 / 75.6 / 72.1 / 90.3 route etc. | 5.3 → **G89-F4**; P-75.1 → **G89-F24**; P-75.6 → **G89-F25**; 72.1 "withdrawal" recorded at S2-08; `/activity` route used at S2-63; others not on a scored path — carried |
| scout | 6.2 §5a-row-4 annotation stale | recorded in §4 above (wording only, not filed) |

## 6. Exit arithmetic and E.3 / P-E.3 inputs

- Open register: **BLOCKER 0 · HIGH 3 · MEDIUM 6 · LOW 18** (27 ids).
- Exit rule `$TASK:675` (0 HIGH + 0 MEDIUM open, every mismatch ruled): **FAIL** — 3 HIGH (F11, F12, F22) + 6 MEDIUM (F13, F14, F16, F17, F18, F23), all confirmed (0.R3, 0.R5); 4 scenario mismatches (F4, F24, F25, F27) await founder §6 rulings.
- **E.3** (0 BLOCKER/HIGH in this file): **FAIL** (3 HIGH). **P-E.3** (same input): **FAIL** (3 HIGH; the portal-only ids are F22 HIGH, F23 MEDIUM, F24/F25/F26/F27 LOW).
- Scored rows: 106 → PASS 95 · FAIL 8 (S1-29 F4, S2-12 F12, S2-25 F18, S2-41 F27, S2-45 F22, S2-55 F24, S2-56 F24, S2-59 F25) · PARTIAL 3 (S1-02 transient "Submitting…" not captured; S1-65 PDF/preview layout compared for the upper half only; S1-76 wow-6 from preview, no banking) · DEFERRED 0; N/A rows 2. Exit checkpoints: E PASS 6 · FAIL 2 · PARTIAL 1 · N/A 2; P-E PASS 3 · FAIL 2 · PARTIAL 3 · N/A 2 (P-E.2 / P-E.10 PARTIAL; one DEFERRED sub-item in P-E.8).
