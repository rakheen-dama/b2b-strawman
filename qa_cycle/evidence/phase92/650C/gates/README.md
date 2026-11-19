# 650C gates — OBS (observed behaviour)

> **Result of record: "Re-probe run 2 @733b8352f"** at the end of this file. Run 2 rebuilt the stack fresh from `733b8352f` and re-ran a, b, s, c, d, e, f and the new r phase. Its evidence is in `obs-r2/`. Run 1 (below, stack @48fb285fe, evidence in `obs/`) is kept as the record of the findings F-650C-OBS-1..7. For the checks run 2 repeated, run 2 replaces run 1. Phases g (Carol), s2 (on-screen widths) and i (boot reconcile) were not re-run, so run 1 remains their record.

**Result: the slice row's OBS holds.** All nine reports run in the browser, and the Rule 54.28 schedule identity holds on a recorded month (August and September 2026, on screen, CSV and preview; = SQL = API). The run also found PDF defects: **F-650C-OBS-1 HIGH** (the schedule and trust-position PDFs are cut off at the right edge, so the schedule PDF has no c/f or Check column) and **F-650C-OBS-2 MEDIUM** ("−" prints as "#"). It found one coverage issue, **F-650C-OBS-3 MEDIUM** (the firm-wide schedule omits an account whose postings are dated before its opened date), and LOW findings OBS-4 to OBS-7. Details are in `../found.md`.

**Stack.** The fresh e2e mock-auth stack was built from this worktree at `48fb285fe` (backend image 05:12 SAST, frontend 04:59 SAST). It was not rebuilt or wiped by OBS. Tenant `tenant_7d218705360b` was provisioned 2026-11-19T03:16:16Z. The seeded primary GENERAL account "Mathebula & Partners Trust Account" (`opened_date` 2026-11-19) was empty before the run (0 postings, statements and reconciliations; `obs/sql-a.txt`).

**OBS window: 2026-11-19T03:24:35Z – 03:35:11Z** (SAST 05:24–05:35; today 2026-11-19). Phases ran in the order a, b, s, c, d, e, f, g, then the s2 probe, sharing `obs/state.json` and run tag `ZL6X0`. Phase s ran twice; the second run (03:27:38Z) is the record. Its only change was splitting checks and adding the CSV/PDF-column checks after the PDF cut was seen. No data was written in s.

**Method.**
- **Browser:** headless Chromium driven by the Playwright library (`obs.cjs`, `obs-lib.cjs`, `obs-report.cjs`, `obs-phases2.cjs`). Logins are through `/mock-login` with timezone Africa/Johannesburg: Alice (owner) for everything, Bob (admin) for one approval, Carol (member) for g.
- **Writes, through the UI only:** the Record Deposit dialog, reconciliation wizard upload / auto-match / **Record from statement** / Complete, the 650B memo dialog, the Allocate dialog, and Bob's Approve button.
- **SQL:** read-only (SELECT guard in `obs-lib.cjs`).
- **API:** GETs only — `report-definitions` (incl. `…/preview` HTML for a text cross-check), `total-balance`, `creditor-accounts`, `unidentified-deposits`, `trust-integrity`, `trust-accounts/{id}/integrity`, `me/capabilities`.
- **Reports** were run through the report page form and **Run Report**, then **Export CSV** and **Export PDF** (the browser downloads are saved in `obs/`).
- **PDF text** was extracted with macOS PDFKit (`pdftext.js`; pdftotext is not installed), and page 1 was rendered to PNG with `sips` for visual review.
- **Raw-code scan (D-6)** covers summary cards, table headers and cells (textContent, so CSS upper-casing is not mistaken for a code), CSV, the PDF (underscore constants and literals, because PDF headers are CSS-uppercased) and the preview HTML text. It is case-sensitive against the type, ledger, status, section, basis, flag and creditor constants, plus `true`/`false`/`null`/`[object Object]`.

**Data recorded through the UI (phase b).**
- **D1:** a deposit, Acme Corp / Website Redesign, R 10 000,00, dated 3 Aug 2026.
- **Statement** `obs/b-statement-ZL6X0.csv` (10 lines, period end 30 Sep 2026). Auto-match matched D1 (line 1). The other 9 lines were each recorded from their statement line:

| line | date | amount | recorded as |
|---|---|---|---|
| 2 | 12 Aug | +600,00 | Unidentified deposit |
| 3 | 31 Aug | +30,00 | Interest for the Fund |
| 4 | 8 Sep | −57,50 | Bank charge, recoverable, VAT 7,50 |
| 5 | 15 Sep | −12,00 | Bank charge, non-recoverable, VAT 1,57 |
| 6 | 20 Sep | +57,50 | Charge refunded |
| 7 | 25 Sep | +750,00 | Unidentified deposit |
| 8 | 26 Sep | +200,00 | Unidentified deposit |
| 9 | 30 Sep | +123,45 | Interest for the Fund |
| 10 | 30 Sep | −100,00 | Interest paid to the Fund (sweep) |

- **Reconciliation:** completed, and step 5 read "September 2026 list locked — R 11 591,45 agrees with the cashbook and the adjusted bank balance."
- **Memo** for September 2026: R 200,00 interest / R 15,00 charges / R 1,96 VAT.
- **UNK3** (R 200,00) was allocated by Alice to Acme / Website Redesign, dated 15 Oct 2026; Bob approved it.

| # | Check | Verdict | Observed | Evidence (`obs/`) |
|---|---|---|---|---|
| a | Catalogue | **PASS** (11/11) | Trust › Reports shows **exactly nine cards**, in this order: Trust Receipts & Payments · **Trust Creditors List (Rule 54.15)** · **Trust Position as at a Date** · Client Ledger Statement · Trust Reconciliation · Investment Register · **Interest and Charges Schedule (Rule 54.28)** · **Unidentified Deposits Age Analysis** · Section 35 Data Pack. There is **no `interest-allocation` card**, no raw code on the page, and each card links to `/reports/{slug}`. In the DB, `report_definitions` TRUST holds the nine slugs and 0 `interest-allocation` rows, and every template body contains the scope sentence. `pack_install` has `trust-reports` at **pack_version 4**, and `org_settings.report_pack_status` has trust-reports **version 4**. `GET /api/report-definitions` returns 9 TRUST reports. | `a-01-trust-reports-page.png`, `api-a-report-definitions.json`, `sql-a.txt`, `result-a.json` |
| b | Record a month; Rule 54.28 schedule identity | **PASS** (b 7/7; s 18/20 — the 2 fails are F-OBS-4 and F-OBS-1) | See the identity section below. Every recorded line posted with the expected `ledger_effect` (SQL). The schedule for 1 Aug – 30 Sep 2026 (account given) shows one block, "Section 86(2) trust banking account", with August and September rows, both **Check "Balanced"** and "Identity Holds: Yes". The screen figures equal the SQL sums of the posted rows by type. **c/f equals the SQL Fund ledger and the API** `total-balance?asAt=…creditorBreakdown.fundInterestAndCharges` at each month end (30,00 / 41,45). **The memo** sits in its own columns (Sep: R 200,00 / R 15,00 / VAT R 1,96; Aug: R 0,00) and is outside the identity. The s86(4)-only columns show "—". The CSV has all 18 columns, both rows "Balanced". **Inverted range** (30 Sep → 1 Aug) and **inverted inside one month** (20 Sep → 5 Sep, D-33) are both refused in the red error box with "Parameter 'dateTo' is before 'dateFrom'", and no table. **On screen** the 18-column table scrolls horizontally, and c/f and Check are reachable at 1280 and 1440 (s2). **Fails:** the **PDF is cut at the right edge** (no Refunded / Paid to the Fund / c/f / Check; F-OBS-1); the summary cards print **Period From/To as ISO** (F-OBS-4); Month and Check cells break mid-word (F-OBS-7, s2). | `b-01..05*.png`, `b-statement-ZL6X0.csv`, `sql-b.txt`; `s-report-trust-interest-charges-schedule.png`, `s-trust-interest-charges-schedule.{csv,pdf,pdf.txt}`, `s-schedule-pdf-page1.png`, `s-preview-schedule.{html,txt}`, `api-s-total-balance-2026-0{8-31,9-30}.json`, `sql-s.txt`, `result-s.json`; `s2-{1280,1440}-table-scrolled-right.png`, `result-s2.json` |
| c | Each of the nine reports runs | **PASS** for "runs"; **FAIL** for PDF completeness of 2 reports (F-OBS-1) and the "−" glyph (F-OBS-2). 90/99 scripted; the 9 fails are 5 ISO-date summary cards (F-OBS-4), 2 real PDF cuts and 2 harness false positives (O-OBS-3) | All nine ran in the UI with no error box, no toast and no form error. Each showed summary cards and a table, or "No data found" for the investment register (no investments on the stack). Each exported a CSV with a header and a `%PDF-` file through the buttons. **The scope sentence** "Trust cash, estates and other trust assets are not recorded in Kazi and are not included in this comparison." appears as a summary card on screen, in **all nine PDFs** and in all nine previews. **No raw code** appears in any summary card, table cell, header, CSV, PDF or preview (0 hits ×9). Labels come from the registry and are words: "Interest received for the Legal Practitioners' Fidelity Fund", "Bank charge refunded from the business account", "Interest paid to the Legal Practitioners' Fidelity Fund", "Unidentified deposit allocated", "Deposit identified and allocated", and so on. Ledger headings, section words, "Completed", "Open" / "Resolved" are also words. **Money summary values are currency** and **booleans are Yes/No** (Fully Locked, Balanced, Is Balanced, Identity Holds), except "After Lock Net = 0" (F-OBS-5). **Visual PDF review (page 1):** receipts & payments, Rule 54.15 list, ledger statement, reconciliation, investment register (headers per D-19, no LPFF rate), age analysis and Section 35 are complete. **Schedule and trust position are cut** (F-OBS-1). The reconciliation and Section 35 PDFs print "−" as "#" (F-OBS-2). | `c-report-<slug>.png` ×9, `c-<slug>.{csv,pdf,pdf.txt,pdf-page1.png}` ×9, `c-preview-<slug>.{html,txt}` ×9, `result-c.json` |
| d | Unidentified deposits age | **PASS** (8/8) | **As at 19 Nov 2026:** R 750,00 (25 Sep, **55 days**) is in **"31–90 days"**, Open; R 600,00 (12 Aug, **99 days**) is in **"91–180 days"**, Open; R 200,00 shows as **Resolved**, "Allocated to Acme Corp on 15 October 2026". The band cards read 0–30 R 0 · 31–90 R 750,00 · 91–180 R 600,00 · 181–365 R 0 · over one year R 0; Open Total **R 1 350,00** = API unidentified creditor = SQL. **As at 1 Oct 2026**, before the allocation, all three are **Open by their own rows (EQ-4)**, even though UNK3's current status is ALLOCATED: 50 d "31–90", 6 d and 5 d "0–30". There is no resolved row; Open Total R 1 550,00 = `total-balance?asAt=2026-10-01`. No raw codes. **"Over one year": NOT EXERCISED.** No item more than a year old can be recorded on this stack without a back-dated statement, and the band reads R 0. | `d-asof-2026-1{1-19,0-01}-*.png`, `…pdf-page1.png`, `api-d-*.json`, `sql-d.txt`, `result-d.json` |
| e | Section 35 data pack | **PASS** (12/12), with O-OBS-2 | FYE **30 Sep 2026** (FY 1 Oct 2025 – 30 Sep 2026), 7 sections:<br>(1) Receipts & payments: 10 rows + Total R 11 591,45.<br>(2) **"Trust creditors (Rule 54.15)"**, "Locked 19 November 2026 by Alice Owner.": Acme R 10 000,00, **the Fund R 41,45**, **three unidentified lines**, Total R 11 591,45.<br>(3) The 30 Sep reconciliation with both comparisons and the stored breakdown.<br>(5) **Rule 54.28 year totals:** b/f 0,00 · interest 153,45 · memo 200,00 · charges 69,50 (of which recoverable 57,50, VAT 9,07) · memo charges 15,00 (VAT 1,96) · refunded 57,50 · paid to the Fund 100,00 · c/f 41,45. 0 + 153,45 − 69,50 + 57,50 − 100,00 = 41,45 = SQL Fund ledger at 30 Sep.<br>(6) **"Fund sweeps and their statement lines (Rule 54.14.16)"**: "2026-09-30 · **b-statement-ZL6X0.csv · line 10** · LPFF/SEP/ZL6X0", −R 100,00 (= SQL file name and line number). "Fund sweeps with no statement line": "None in the financial year".<br>(7) The ledger-statements note.<br>No raw code on screen, in the PDF or in the preview; the scope sentence is in all three. **FYE 28 Feb 2027** (live): runs; part (2) "Not locked …", Acme R 10 200,00, and the two remaining unidentified lines after the 15 Oct allocation.<br>**Part (4) investments: NOT EXERCISED.** There are no investments on the stack and the part is absent (O-OBS-2). | `e-fye-*.png`, `e-fye-2026-09-30-section-35-data-pack.{pdf,pdf.txt,csv}`, `e-preview-fye-*.{html,txt}`, `sql-e.txt`, `result-e.json` |
| f | Trust position | **PASS** (13/13), PDF cut in F-OBS-1 | **As at 30 Sep 2026** and **19 Nov 2026** (firm-wide), every firm-wide card equals `GET /api/trust-integrity?asAt=` exactly: cashbook, client ledgers, s86(4), Fund, unidentified, other non-client, inter-account, creditor total, difference. Balanced "Yes" = API `balanced: true`. All money values are currency. The row Section reads "Section 86(2) trust banking account" and Integrity reads "No integrity flags". At 30 Sep (a reconciliation has that period end) Adjusted Bank Balance R 11 591,45 is shown. At 19 Nov the bank note reads "No reconciliation has this period end — the cashbook is compared with trust creditors only". 19 Nov figures: client ledgers R 10 200,00, Fund R 41,45, unidentified R 1 350,00. The "As Of Date" card is ISO (F-OBS-4). | `f-2026-*-report-trust-position.png`, `f-2026-11-19-trust-position.{pdf,pdf-page1.png,csv}`, `api-f-trust-integrity-*.json`, `api-f-account-integrity-*.json`, `result-f.json` |
| g | Carol (member) | **PASS** (3/3) — behaviour per the existing capability rules | `me/capabilities`: role Member with `VIEW_TRUST` and **no `FINANCIAL_VISIBILITY`**. Trust › Reports shows the heading and the card "Trust reports need financial visibility. Ask an owner or admin to grant it.", with 0 report cards. `GET /api/report-definitions` → 403. Opening `/reports/trust-interest-charges-schedule` directly gives the generic "Something went wrong" page: a 403 ApiError server-side and React #441 in the console. The report detail page is unchanged by 650C, so this is pre-existing (O-OBS-1, related to FU-2). | `g-01-carol-trust-reports.png`, `g-02-carol-schedule-url.png`, `api-g-carol-*.json`, `result-g.json` |
| h | Logs | **PASS** (0 ERROR, 0 exceptions) | **Backend in the window:** 0 ERROR, 0 stack traces. 240 WARN are PDFBox base-14 font fallbacks, 16 per PDF render × 15 UI PDF exports; this happens on every report PDF and is not specific to 650C, but it is the likely cause of F-OBS-2. 2 WARN are the 403s induced by g. **Frontend container:** only the g 403. **Browser:** 0 page errors; console errors are the mock-auth "failed to fetch userinfo" right after login (e, f, s2) and the g React #441. **Audit:** REPORT_EXPORTED csv = 15 for 15 CSV exports; pdf = 0 for 15 PDF exports; REPORT_GENERATED = 0 for the UI runs. This is the browser-path observation of the pre-existing F-650C-1. | `../obs-log-summary.txt`, `sql-h-i.txt` (`obs/*.log` gitignored) |
| i | Boot reconcile | **Fresh install observed; 3 → 4 reconcile: covered by test, not observable on a fresh stack** | Boot at 03:16:16Z logged "Applied report pack standard-reports v2 …" and **"Trust report pack seeded for tenant tenant_7d218705360b: 9 definitions"**. There is no reconcile or membership line: the tenant was created at v4 (`pack_install` 4, `report_pack_status` 4, 9 TRUST rows). The 3 → 4 membership + field reconcile is pinned by `TrustReportPackSeederIntegrationTest`. Observing it without writes would need a tenant installed at v3, which a fresh stack does not have. | `../obs-log-summary.txt`, `sql-a.txt`, `sql-h-i.txt` |

**Schedule month rows and the identity, by hand from the screen.**

| Month | b/f | Gross interest (cashbook) | Charges rec. / non-rec. (VAT) | Refunded | Paid to the Fund | c/f | Memo interest / charges / VAT | Check |
|---|---|---|---|---|---|---|---|---|
| August 2026 | R 0,00 | R 30,00 | R 0,00 / R 0,00 (R 0,00) | R 0,00 | R 0,00 | R 30,00 | R 0,00 / R 0,00 / R 0,00 | Balanced |
| September 2026 | R 30,00 | R 123,45 | R 57,50 / R 12,00 (R 9,07) | R 57,50 | R 100,00 | R 41,45 | R 200,00 / R 15,00 / R 1,96 | Balanced |

- August: 0,00 + 30,00 − 0,00 + 0,00 − 0,00 = **30,00** = c/f.
- September: 30,00 + 123,45 − 69,50 + 57,50 − 100,00 = **41,45** = c/f.
- The September b/f equals the August c/f.
- SQL for September by type (`sql-s.txt`): FUND_INTEREST 123,45; BANK_CHARGE 69,50 (recoverable 57,50, non-recoverable 12,00, VAT 9,07); CHARGE_REFUND 57,50; FUND_SWEEP 100,00.
- SQL Fund ledger: 31 Jul 0,00 · 31 Aug 30,00 · 30 Sep 41,45.
- API `fundInterestAndCharges`: 31 Aug 30, 30 Sep 41.45.
- The memo (R 200,00 / R 15,00) appears only in the memo columns and is not in either side of the identity.

**Not exercised.**
- An s86(4) or s86(3) block in the schedule (Fund 5% credited / paid at source), and investment-register rows and Section 35 part (4). There are no investments on the stack, so creating them was out of this OBS's scope.
- The age analysis "over one year" band.
- A reversed row in the schedule (contra in another month, D-8).
- The trust-position firm-wide integrity table (D-31), which only appears when flags exist; there were none.
- Bob as a report runner. Owner and admin share the capability path.
- The 3 → 4 boot reconcile (i).

**Findings** (full repro and evidence in `../found.md`, "OBS" heading):

| ID | Severity | Summary |
|---|---|---|
| F-650C-OBS-1 | HIGH | Schedule and trust-position PDFs are cut off at the right page edge. |
| F-650C-OBS-2 | MEDIUM | "−" prints as "#" in the reconciliation and Section 35 PDFs. |
| F-650C-OBS-3 | MEDIUM | The firm-wide schedule omits an account whose postings predate its opened date ("Account Count 0", "Identity Holds Yes"). |
| F-650C-OBS-4 | LOW | ISO dates in summary cards and Section 35 labels. |
| F-650C-OBS-5 | LOW, pre-existing | "After Lock Net" is not formatted as currency. |
| F-650C-OBS-6 | LOW | "Total Trust Balance" is the clients-only total. |
| F-650C-OBS-7 | LOW | Mid-word breaks in the schedule's Month and Check cells. |

O-OBS-1..3 are INFO.

**Stack state left behind (the stack is UP).** The primary account holds:
- D1 and the 9 statement-line postings (Aug–Sep 2026).
- One COMPLETED reconciliation to 30 Sep 2026 and the locked September 2026 list (creditor total R 11 591,45).
- The September 2026 memo.
- UNK1 and UNK2 HELD; UNK3 ALLOCATED to Acme on 15 Oct 2026 (UNIDENT_ALLOC_OUT/IN, approved by Bob).

Cashbook R 11 591,45; Fund R 41,45; unidentified R 1 350,00; Acme R 10 200,00. The E2E-LEGAL gate needs a wipe (`e2e-down.sh` / `e2e-up.sh`) before it runs.

---

## Re-probe run 2 @733b8352f

**Result: F-650C-OBS-1 to OBS-5 are fixed, as observed in the browser and in the exported files.** The core checks (catalogue, schedule identity, all nine reports, age analysis, Section 35, trust position, logs) hold on the final code. No new defect was found. This run is the result of record for every check it repeated.

**Stack.** A fresh e2e mock-auth stack was built from the worktree at `733b8352f`: backend image 03:57:03Z, frontend image 03:57:29Z, containers started 03:57:30Z. Tenant `tenant_7d218705360b` was provisioned at 03:58:27Z ("Trust report pack seeded … 9 definitions"; `pack_install` trust-reports 4). Before the run the books were empty (0 postings, statements and reconciliations), and the primary account "Mathebula & Partners Trust Account" had `opened_date` 2026-11-19.
- **Fix presence:** checked in the backend jar. `TrustReportQueryRepository.accountsWithPostingsOnOrBefore` is present, and the class `TrustReconciliationReportQuery` holds "Difference (adjusted bank – cashbook)" with U+2013.

**Run-2 window: 2026-11-19T04:01:39Z – 04:05:25Z** (SAST 06:01–06:05). The phases ran in the order a, b, s, c, d, e, f, r, with `OBS_OUT=obs-r2`, run tag `0WYGG` and statement `b-statement-0WYGG.csv`.
- Phase r ran twice (04:04:07Z and 04:04:40Z); the second run is the record. The first run's only failure was a harness regex that expected a three-letter month and so rejected "30 Sept 2026". That is ICU's en-ZA short month, produced by the app-wide `formatLocalDate` in `packages/shared/src/format.ts`. The regex now accepts it.
- The first run is kept as `result-r-attempt1.json` / `run-r-attempt1.out`. It wrote no data. The only extra effect was one firm-wide CSV/PDF export.

**Data.** Run 1's recipe was repeated through the UI only: deposit D1, the 10-line statement, auto-match, 9 × Record from statement, Complete, the September memo, the UNK3 allocation, and Bob's approval. Step 5 read "September 2026 list locked — R 11 591,45 agrees with the cashbook and the adjusted bank balance."

**Harness changes for run 2.** These are backward compatible; run 1 is reproducible with no environment variable set.
- `obs-lib.cjs`: `OUT` now honours `OBS_OUT`, with default `obs`.
- `obs-phases2.cjs`: check e(3) accepts "–" or "−" in the Difference labels (the fix changed the glyph).
- New `obs-r2.cjs` adds phase r: the OBS-1..5 checks, firm-wide schedule runs, PDF page size via `sips`, page count via PDFKit, and a vector render of page 1 at 1800 px via `qlmanage` (`*.pdf-page1-hi.png`) for the visual right-edge check.
- `obs.cjs` registers phase r.

| # | Check | Verdict | Observed (run 2) | Evidence (`obs-r2/`) |
|---|---|---|---|---|
| 1 | Catalogue | **PASS** (11/11) | Nine cards and no `interest-allocation` card, on the page, in the DB and in the API. No raw code on the page. pack_version 4. Every template carries the scope sentence. | `a-01-trust-reports-page.png`, `api-a-report-definitions.json`, `sql-a.txt`, `result-a.json` |
| 2 | Schedule identity (recorded month) | **PASS** (s 20/20, b 7/7) | See the table below. Both rows are "Balanced". The summary card says "Identity Holds: Yes". The screen figures equal the SQL sums by type, and c/f equals the SQL Fund ledger and API `fundInterestAndCharges` (30 / 41.45). The memo is in its own columns. The s86(4) columns show "—". The CSV has all 18 columns and both rows read "Balanced". Both inverted ranges are refused with "Parameter 'dateTo' is before 'dateFrom'". **The check "PDF shows every schedule column", which failed in run 1, now passes.** | `s-report-*.png`, `s-trust-interest-charges-schedule.{csv,pdf,pdf.txt,pdf-page1-hi.png}`, `s-preview-schedule.*`, `api-s-*.json`, `sql-s.txt`, `result-s.json` |
| 3 | OBS-1: wide PDFs | **PASS** | The schedule PDF (account run, phase s, firm-wide run) and the trust-position PDF (30 Sep and 19 Nov) are **landscape A4, 841.9 × 595.3 pt, one page**. **The rendered page 1 was viewed.** Every column is on the page, up to and including **Charges Refunded, Paid to the Fund, Fund Ledger c/f and Check**. The "Period" totals row reads R 0,00 · R 153,45 · R 200,00 · — · — · — · R 57,50 · R 12,00 · R 9,07 · R 15,00 · R 1,96 · R 57,50 · R 100,00 · **R 41,45 · Balanced**. In the trust position, the **Integrity** column shows "No integrity flags", and at 19 Nov also "No reconciliation has this period end — the cashbook is compared with trust creditors only", fully inside the right margin. The text extraction has every one of these labels and values. **The other seven PDFs** are portrait A4. The hi-res page 1 of each was viewed: receipts & payments, Rule 54.15 list, ledger statement, reconciliation, investment register, age analysis (19 Nov) and Section 35 (FYE 30 Sep). **Nothing is cut at the right edge.** The 3 scripted "column missing" fails in c are harness false positives: the PDF header labels differ from the on-screen labels. On client-trust-balances "General Balance" prints as "General" and Source as the per-account note. On client-ledger-statement "Running Balance" prints as "Balance". On trust-position the PDF uses "S86(4) Investments", "Fund Interest & Charges", "S86(3) Placements" and "Adjusted Bank", and puts the bank note in Integrity (O-OBS-3). The rendered pages show every column. | `{c,s,f,r}-*trust-interest-charges-schedule*.pdf-page1-hi.png`, `c-trust-position.pdf-page1-hi.png`, `f-2026-11-19-trust-position.pdf-page1-hi.png`, `c-*.pdf-page1-hi.png` ×9, `*.pdf.txt`, `result-r.json` (`widePdfs`, `otherPdfs`) |
| 4 | OBS-2: dashes | **PASS** | The reconciliation PDF and both Section 35 PDFs (from c and from e, FYE 30 Sep) print "Difference (adjusted bank **–** cashbook)", "Difference (adjusted bank – trust creditors)" and "Difference (cashbook – trust creditors)". Those PDFs contain **0 "#"** and 0 U+2212. This was seen on the rendered reconciliation page and in the text. | `c-trust-reconciliation.pdf{,.txt,-page1-hi.png}`, `c-section-35-data-pack.pdf.txt`, `e-fye-2026-09-30-section-35-data-pack.pdf.txt`, `result-r.json` (`dashes`) |
| 5 | OBS-3: firm-wide schedule | **PASS** | The firm-wide run (no account) for 1 Aug – 30 Sep 2026 shows **Account Count 1** and **Identity Holds Yes**, with the primary account's August row (c/f R 30,00, Balanced) and September row (c/f R 41,45, Balanced). The figures are identical to the run with the account given. The account was opened 2026-11-19 and its first posting is 2026-08-03 (`sql-r.txt`), so this exercises the D-44 "postings on or before" path. The CSV and the landscape PDF are complete. In phase s, firm-wide Aug–Nov shows 4 rows. **Firm-wide 1 Jan – 28 Feb 2026, before any posting:** "No data found", Account Count 0, **Identity Holds "—"** (was a vacuous "Yes"), with 0 postings on or before 28 Feb in SQL. | `r-firmwide-*.{png,csv,pdf,pdf.txt,pdf-page1-hi.png}`, `r-firmwide-before-postings-report-*.png`, `s-firmwide*.png`, `sql-r.txt`, `result-r.json` |
| 6 | OBS-4: dates in words | **PASS** | Summary cards: Trust Position As Of Date "30 Sept 2026" / "19 Nov 2026" · schedule Period From/To "01 Aug 2026" / "30 Sept 2026" (firm-wide the same; before-postings "01 Jan 2026" / "28 Feb 2026") · age analysis As Of Date "19 Nov 2026" · reconciliation Period End "30 Sept 2026" · Section 35 Financial Year Start/End "01 Oct 2025" / "30 Sept 2026". No summary card in any of the nine reports has an ISO date (`c: … no raw ISO date` passes ×9). Section 35 part (2) reads "Eft credit unknown remitter · received **12 August 2026**", "… received 25 September 2026" and "… received 26 September 2026". Part (6) reads "**30 September 2026** · b-statement-0WYGG.csv · line 10 · LPFF/SEP/0WYGG", −R 100,00. The PDF carries the same wording. **Left ISO by design (D-45):** the PDF "Period:" / "As at:" headers, part (1) receipts & payments labels, and generic `date` table cells. | `c-report-*.png`, `f-*-report-trust-position.png`, `e-fye-2026-09-30-*`, `result-c.json`, `result-e.json`, `result-r.json` (`dateCards`, `s35Received`, `s35Sweeps`) |
| 7 | OBS-5: After Lock Net | **PASS** | The Rule 54.15 list card reads "After Lock Net **R 0,00**" (screenshot viewed). | `c-report-client-trust-balances.png`, `result-r.json` (`afterLockNet`) |
| 8 | Nine reports run | **PASS** (c 96/99; the 3 fails are the harness false positives in row 3) | Each of the nine ran through the UI with no error box, toast or form error. Each showed cards and a table, or "No data found" for the investment register. Each exported a CSV with a header and a `%PDF-` file through the buttons. The scope sentence is on screen, in the PDF and in the preview for all nine. **0 raw codes** across cards, table, CSV, PDF and preview for all nine. Also observed: d 8/8 (age bands 55 d "31–90" R 750,00, 99 d "91–180" R 600,00, R 200,00 Resolved "Allocated to Acme Corp on 15 October 2026", Open Total R 1 350,00 = API = SQL; as at 1 Oct all three open, R 1 550,00); e 12/12 (year identity 0 + 153,45 − 69,50 + 57,50 − 100,00 = 41,45 = SQL; part (6) sweep with file and line 10); f 13/13 (every firm-wide card = `GET /api/trust-integrity`, Balanced Yes). | `result-{c,d,e,f}.json`, `c-*`, `d-*`, `e-*`, `f-*`, `api-{d,f}-*.json`, `sql-{c,d,e,f}.txt` |
| 9 | Logs | **PASS** | **Backend, in the window (316 lines):** 0 ERROR, 0 exceptions / stack traces. All 256 WARN are the PDFBox base-14 font fallback (16 per PDF × 16 PDF renders). There is no other WARN. The 14 non-font WARNs in the container log are all at boot (03:57–03:58Z). **Frontend container, in the window:** 0 lines. **Browser:** **0 page errors** in every phase; the only console error is the known mock-auth "failed to fetch userinfo" on /dashboard right after login (f). **Audit:** REPORT_EXPORTED csv = 16 for 16 CSV exports, and pdf = 0, which is the pre-existing F-650C-1. | `../../obs-log-summary.txt` (run-2 section), `sql-h.txt`, `result-*.json` (`*.log` gitignored) |

**Schedule month rows (run 2, on screen; the account run and the firm-wide run are identical).**

| Month | b/f | Gross interest (cashbook) | Charges rec. / non-rec. (VAT) | Refunded | Paid to the Fund | c/f | Memo interest / charges / VAT | Check |
|---|---|---|---|---|---|---|---|---|
| August 2026 | R 0,00 | R 30,00 | R 0,00 / R 0,00 (R 0,00) | R 0,00 | R 0,00 | R 30,00 | R 0,00 / R 0,00 / R 0,00 | Balanced |
| September 2026 | R 30,00 | R 123,45 | R 57,50 / R 12,00 (R 9,07) | R 57,50 | R 100,00 | R 41,45 | R 200,00 / R 15,00 / R 1,96 | Balanced |

- August: 0,00 + 30,00 − 0,00 + 0,00 − 0,00 = **30,00** = c/f.
- September: 30,00 + 123,45 − 69,50 + 57,50 − 100,00 = **41,45** = c/f.
- SQL, September by type: FUND_INTEREST 123,45; BANK_CHARGE 69,50 (rec 57,50 / non-rec 12,00 / VAT 9,07); CHARGE_REFUND 57,50; FUND_SWEEP 100,00.
- SQL Fund ledger: 31 Jul 0 · 31 Aug 30,00 · 30 Sep 41,45.
- API: 30 / 41.45.

**Not re-run in run 2 (run 1 stays their record):** g (Carol), s2 (on-screen column reachability; F-OBS-7 was not in the fix set), and i (boot reconcile).
- F-650C-OBS-6 and OBS-7 were not fixed in `733b8352f` and remain open.
- The run-1 "Not exercised" list still applies.

**Stack state left behind (UP).** It is the same shape as at the end of run 1, with run tag `0WYGG`. Cashbook R 11 591,45; Fund R 41,45; unidentified R 1 350,00; Acme R 10 200,00; one COMPLETED reconciliation to 30 Sep 2026 with the September list locked; the September memo; UNK3 ALLOCATED on 15 Oct 2026. Wipe the stack before E2E-LEGAL.

---

**Archive note (orchestrator, 2026-11-19, before merge).** To keep PR #1809 under GitHub's 300-file diff limit (the merge-gate hook classifies a PR through `gh pr diff --name-only`, which returns HTTP 406 above 300 files), the raw report exports cited above — every `*.pdf`, `*.pdf.txt`, `*-preview*.txt`, `*.html`, `*.csv` (except the `b-statement-*.csv` inputs) and `*.out` file — are packed unchanged into `obs/raw-exports.tar.gz` (66 files) and `obs-r2/raw-exports.tar.gz` (78 files). Paths cited as `obs/<file>` / `obs-r2/<file>` resolve inside those archives (`tar -xzf raw-exports.tar.gz <file>`). Screenshots, page renders (PNG), `api-*.json`, `sql-*.txt` and `result-*.json` remain loose.
