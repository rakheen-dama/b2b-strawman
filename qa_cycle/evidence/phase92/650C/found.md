# 650C found — defects outside the slice (reported, not fixed here)

- **F-650C-1 — MEDIUM (pre-existing on `main` @67d251e66; audit plane) — report PDF-export and execute audit rows are never persisted.**
  `ReportExportService.execute` / `exportAsPdf` run in a `@Transactional(readOnly = true)` transaction (unchanged
  by 650C except for the added `REPEATABLE_READ`). `DatabaseAuditService.log` is plain `@Transactional` (REQUIRED),
  joins that read-only transaction, whose Hibernate flush mode is MANUAL, so `auditEventRepository.save` is never
  flushed (UUID id → no insert at persist time). No error is raised.
  Delta reviewer A's probe (fresh tenant, MockMvc, scratch test since deleted): PDF export `REPORT_EXPORTED` 0→0
  (lost); CSV export 0→1 (kept: `writeCsvAndAudit` runs outside the transaction); `POST /execute`
  `REPORT_GENERATED` 0→0 (lost). Same result with main's annotations (`readOnly = true` only), so 650C neither
  causes nor widens it.
  Related: `ReportingControllerTest.exportPdfPersistsReportExportedAuditEvent` passes falsely (asserts
  `isNotEmpty()` on a tenant where sibling CSV tests already wrote `REPORT_EXPORTED` rows);
  `kazi-architecture/30-modules/reporting.md:62` says `REPORT_GENERATED` is recorded.
  Impact: PDF exports of every report (incl. the Section 35 pack) leave no audit trail (ADR-264 expects exports audited).
  Fix direction: audit write in `REQUIRES_NEW` or after commit; tighten the test to a count delta. → follow-up (decisions log).

- **F-650C-2 — LOW (pre-existing; reports) — receipts & payments drops entries reversed after the period.**
  `TrustTransactionRepository.findForReceiptsPayments` excludes any row whose status is now `REVERSED`, whatever
  the contra's date. After 650C CR-7 the Section 35 sweep evidence (parts 5/6) keeps a sweep reversed after the
  year end, so the same pack can list a sweep in parts 5/6 but not in part 1. Same family as 649B/646E F1
  (receipts & payments and reversals). Fix direction: the as-at contra rule CR-7 applied to `fundSweeps`
  (`r.transaction_date <= :toDate`). → follow-up.

- **F-650C-3 — LOW (wording, carried from v3) — the Section 35 "Client Ledger Statements" note still mentions the
  `customer_id` parameter.** Unchanged text from pack v3; a firm user does not know a parameter name.

- **F-650C-4 — INFO — F-7 (per-account integrity-check cost inside `checkFirm`) remains open**; `trust-position`
  (new definition in v4) calls `checkFirm(asOfDate)`, so its cost scales as in 649C OQ #9 (3 + 9 statements per account).

## OBS (2026-11-19, stack @48fb285fe, window 03:24:35Z–03:32:04Z) — evidence in `gates/obs/`, narrative in `gates/README.md`

- **F-650C-OBS-1 — HIGH (new in 650C; PDF) — wide trust-report PDFs are cut off at the right edge of the page.**
  - **FIXED in 733b8352f — re-probe run 2:** the schedule PDF (account, phase s and firm-wide runs) and the trust-position PDF (30 Sep and 19 Nov) are landscape A4 (841.9 × 595.3 pt), one page. On the rendered page 1, every column is inside the right margin: Charges Refunded R 57,50 · Paid to the Fund R 100,00 · Fund Ledger c/f R 41,45 · Check "Balanced" (both months and the Period row), and Integrity "No integrity flags" plus the bank note. The text extraction has them too. The other seven PDFs are portrait A4 and were viewed hi-res: nothing is cut. Evidence: `gates/obs-r2/*-trust-interest-charges-schedule.pdf-page1-hi.png`, `*trust-position.pdf-page1-hi.png`, `c-*.pdf-page1-hi.png`, `result-r.json`.
  The PDF is portrait A4 and the table runs past the right margin. Nothing on the page says columns are missing.
  - *Rule 54.28 schedule*: 14 of 18 columns print. **Charges Refunded, Paid to the Fund, Fund Ledger c/f and Check are not on the page**, and their text is not in the PDF either. The PDF therefore lacks the c/f figure and the Check column that the identity needs. The "Period" totals row is cut the same way.
  - *Trust Position*: the **Integrity** column is cut. Its header shows "IN" and its cells "No fla…". The column also carries the bank note "No reconciliation has this period end — …".
  - The screen, the CSV and the HTML preview (`GET …/preview`) have every column.
  - **Repro:** Alice → `/reports/trust-interest-charges-schedule`. From Month 2026-08-01, To Month 2026-09-30, Trust Account Mathebula & Partners Trust Account (any account with Fund-ledger rows). Run Report, then Export PDF, then open the PDF.
  - **Evidence:** `gates/obs/s-schedule-pdf-page1.png`, `c-trust-interest-charges-schedule.pdf(.txt|-page1.png)`, `c-trust-position.pdf-page1.png`, `f-2026-11-19-trust-position.pdf-page1.png`, and the matching `*-preview-*.txt` / `*.csv` with all columns.
  - **Fix direction:** a landscape `@page` (or a compact layout) for wide templates, plus a render test asserting that the last column header is on the page.

- **F-650C-OBS-2 — MEDIUM (new in 650C; PDF) — "−" (U+2212) prints as "#" in PDFs.**
  - **FIXED in 733b8352f — re-probe run 2:** the reconciliation PDF and both Section 35 PDFs print "Difference (adjusted bank – cashbook)", "… (adjusted bank – trust creditors)" and "… (cashbook – trust creditors)" with an en dash, and contain 0 "#" (rendered page viewed). Evidence: `gates/obs-r2/c-trust-reconciliation.pdf-page1-hi.png`, `*.pdf.txt`, `result-r.json` (`dashes`).
  - The trust-reconciliation PDF reads "Difference (adjusted bank **#** cashbook)", "Difference (adjusted bank # trust creditors)" and "Difference (cashbook # trust creditors)". Section 35 part (3) shows the same text.
  - These labels are new in 650C (D-20, `TrustReconciliationReportQuery` lines 98/119/124); main had none.
  - Screen, CSV and preview print "−" correctly.
  - **Likely cause:** the PDF falls back to the standard Type 1 fonts with WinAnsi encoding (240 PDFBox "Using fallback font LiberationSans for base font Helvetica…" WARNs in the window). WinAnsi has no U+2212, so the renderer substitutes "#". The en dash in "31–90 days" is in WinAnsi and prints correctly.
  - **Repro:** run Trust Reconciliation for the primary account after a completed reconciliation, then Export PDF.
  - **Evidence:** `gates/obs/c-trust-reconciliation.pdf-page1.png`, `c-trust-reconciliation.pdf.txt`, `c-section-35-data-pack.pdf.txt`.
  - **Fix direction:** use an ASCII "-" or the en dash "–" in these labels, or embed a Unicode font.

- **F-650C-OBS-3 — MEDIUM (new in 650C, D-34 + data) — the firm-wide Rule 54.28 schedule silently omits an account whose postings are dated before its opened date.**
  - **FIXED in 733b8352f — re-probe run 2:** the account was opened 2026-11-19 and its first posting is 2026-08-03. The firm-wide schedule for 1 Aug – 30 Sep 2026 shows Account Count 1, Identity Holds Yes, and the account's August (c/f R 30,00) and September (c/f R 41,45) rows, both Balanced and identical to the run with the account given. The firm-wide run for 1 Jan – 28 Feb 2026 (before any posting) shows "No data found", Account Count 0, Identity Holds "—". Posting validation before `opened_date` is unchanged (D-44); that ruling is still open. Evidence: `gates/obs-r2/r-firmwide-*`, `r-firmwide-before-postings-report-*.png`, `sql-r.txt`, `result-r.json`.
  - On the e2e stack the seeded primary account has `opened_date` 2026-11-19, the provisioning day. The product accepted postings dated Aug–Sep 2026 on it, through the deposit dialog and Record from statement.
  - The firm-wide schedule (no account) for 2026-08-01 – 2026-09-30 shows **"Account Count 0", "Identity Holds Yes" and "No data found"**.
  - For the same months, the firm-wide Trust Position (as at 30 Sep) and the firm-wide age analysis include the account, and so does the schedule run with the account named. A firm-wide range that reaches November also shows it, with 4 rows.
  - "Identity Holds: Yes" over zero blocks is vacuous.
  - Either postings before `opened_date` should be refused or flagged, or the firm-wide schedule should include an account that has rows in the period.
  - Ruling needed.
  - **Evidence:** `gates/obs/result-s.json` (`firmWide`, `firmWideToNov`), `s-firmwide-report-trust-interest-charges-schedule.png`, `sql-a.txt` (opened_date), `sql-b.txt` (postings).

- **F-650C-OBS-4 — LOW (summary cards; partly new) — raw ISO dates in summary cards.**
  - **FIXED in 733b8352f — re-probe run 2:** the summary cards print en-ZA dates. Trust Position As Of Date "30 Sept 2026" / "19 Nov 2026"; schedule Period From/To "01 Aug 2026" / "30 Sept 2026"; age analysis "19 Nov 2026"; reconciliation Period End "30 Sept 2026"; Section 35 FY Start/End "01 Oct 2025" / "30 Sept 2026". No card in the nine reports has an ISO date. Section 35 part (2) reads "· received 12 August 2026" (25 and 26 September 2026), and part (6) reads "30 September 2026 · b-statement-0WYGG.csv · line 10 · …", on screen and in the PDF. Left ISO by design (D-45): the PDF "Period:" / "As at:" headers, part (1) labels and the generic `date` cells. Evidence: `gates/obs-r2/result-r.json` (`dateCards`, `s35Received`, `s35Sweeps`), `c-report-*.png`.
  - **New in 650C** (on screen for the first time): Trust Position "As Of Date 2026-09-30", Rule 54.28 schedule "Period From 2026-08-01" / "Period To 2026-09-30", age analysis "As Of Date 2026-11-19".
  - **Pre-existing:** Trust Reconciliation "Period End", Section 35 "Financial Year Start/End".
  - **Also new in 650C:** ISO dates inside Section 35 item labels: part (2) "· received 2026-08-12" and part (6) "2026-09-30 · b-statement-….csv · line 10 · …".
  - PDF headers "Period: 2026-08-01 to 2026-09-30" and "As at: …" follow the existing template convention. The generic results table prints `date` columns as ISO (pre-existing).
  - **Evidence:** `gates/obs/result-c.json` (`cardIsoHits`), the `c-report-*.png` screenshots.

- **F-650C-OBS-5 — LOW (pre-existing since 650A; summary formatting) — the Rule 54.15 list's "After Lock Net" card prints "0", not "R 0,00".**
  - **FIXED in 733b8352f — re-probe run 2:** the card reads "After Lock Net R 0,00". Evidence: `gates/obs-r2/c-report-client-trust-balances.png`, `result-r.json` (`afterLockNet`).
  - The key `afterLockNet` is money but is not matched by `MONEY_SUMMARY_KEY` in `report-results.tsx`; D-39's additions did not cover it.
  - **Evidence:** `gates/obs/c-report-client-trust-balances.png`, `result-c.json`.

- **F-650C-OBS-6 — LOW (wording; follows D-1) — "Total Trust Balance" is the clients-only total.**
  - The Rule 54.15 list shows the card "Total Trust Balance R 10 000,00" next to "Creditor Total R 11 591,45".
  - Section 35 shows "Total Trust Balance At Year End R 10 000,00", while its trust-creditors part totals R 11 591,45.
  - The v3 key was kept for compatibility (D-1), but the card reads as the trust balance. It excludes the Fund (R 41,45) and the unidentified deposits (R 1 550,00).
  - **Evidence:** `result-c.json`, `c-report-client-trust-balances.png`, `c-report-section-35-data-pack.png`.

- **F-650C-OBS-7 — LOW (screen readability; new columns, pre-existing cell rule) — the schedule's Month and Check cells break mid-word.**
  - On screen, "September 2026" wraps as "Septe / mber / 2026" and "Balanced" as "Balanc / ed", at both 1280 and 1440.
  - The generic results table gives every `string` column `wrap-anywhere` (G89-F47). The 18-column schedule squeezes these narrow columns.
  - The table scrolls horizontally, and c/f and Check are reachable after scrolling.
  - **Evidence:** `gates/obs/s2-{1280,1440}-table-scrolled-right.png`, `result-s2.json` (`monthCells.lines`).

- **O-OBS-1 — INFO (pre-existing; not 650C) — a direct report URL errors for a member without financial visibility.**
  - Carol (Member: `VIEW_TRUST`, no `FINANCIAL_VISIBILITY`) opening `/reports/trust-interest-charges-schedule` directly gets the generic "Something went wrong" page.
  - The cause is a 403 from `getReportDefinition` passed to `handleApiError`, with React #441 in the console and a 403 ApiError in the frontend log.
  - The Trust › Reports page itself correctly shows "Trust reports need financial visibility…".
  - `reports/[reportSlug]/page.tsx` is unchanged by 650C. Related to FU-2.

- **O-OBS-2 — INFO — the Section 35 investments part prints nothing when there are no investments.**
  - Part (4), investments on the books, is absent from the pack. There is no "None" note, unlike part (6).
  - "Section Count 7" counts R&P, creditors, reconciliation, schedule, the two sweep sections and ledger statements.
  - Part (4) with real investments was not exercised: there are no investments on the stack.

- **O-OBS-3 — INFO (harness) — the PDF column-presence check gave false positives.**
  - The automated check flagged client-trust-balances ("General" for "General Balance"; Source printed as the per-account note) and client-ledger-statement ("Balance" for "Running Balance").
  - Both are template labels, not clipping. The rendered pages were inspected: nothing is cut on either.
  - For the cut columns of trust-position and the schedule, the rendered page is authoritative.

## OBS run 2 (2026-11-19, fresh stack @733b8352f, window 04:01:39Z–04:05:25Z) — evidence in `gates/obs-r2/`, narrative in `gates/README.md` "Re-probe run 2 @733b8352f"

- **No new defect.** OBS-1 to OBS-5 were re-observed as fixed (see the "FIXED in 733b8352f" lines above). OBS-6 and OBS-7 were not in the fix set and remain open (not re-probed).
- **O-OBS-R2-1 — INFO (consistency, not a defect).**
  - Summary cards use the app-wide `formatLocalDate` (en-ZA short month, e.g. "30 Sept 2026").
  - The 650C Section 35 labels use the long month ("30 September 2026").
  - Both are readable dates. Noted only in case a single style is wanted.
- **O-OBS-3 (harness) still applies, and run 2 adds trust-position to it.**
  - The scripted "PDF shows every on-screen column" check fails for trust-position because its PDF headers are template labels: "S86(4) Investments", "Fund Interest & Charges", "S86(3) Placements", "Adjusted Bank", with the bank note under Integrity.
  - The rendered page shows all 12 columns, so this is not clipping.
