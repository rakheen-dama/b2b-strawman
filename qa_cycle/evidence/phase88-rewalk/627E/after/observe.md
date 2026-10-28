# 627E — 627.11 observed run on the KC dev stack (after fix)

- **Date:** 2026-10-28, 10:24–10:30Z (12:24–12:30 SAST). Browser: Playwright MCP (Chromium), KC two-step login. No chrome extension.
- **PR / code under test:** #1697, worktree head `f3a098bba` (frontend served from `$WT/frontend`). Backend from `$MAIN` (`e1df4c43c`). `git diff e1df4c43c f3a098bba -- backend` is empty, so the backend is the same code as the PR.
- **Data:** no data was created or modified, by SQL or by UI. SQL was read-only SELECT. The only writes were the export audit rows the product itself emits.

## Stack (PID / etime / cwd)

| Port | PID | etime at check | cwd | Note |
|---|---|---|---|---|
| 8080 backend | 5527 (svc pid 5394) | 00:15 at start, 06:40 at end | `$MAIN/backend` | Restarted twice. See "Backend restarts" below. Left running. |
| 3000 frontend (run) | 4700 (svc pid 4657) | 00:05 at start | `$WT/frontend` | Started with `$WT/compose/scripts/svc.sh start frontend`. Log `$WT/.svc/logs/frontend.log` (gitignored). |
| 3000 frontend (restored) | 9943 (svc pid 9900) | 00:06 | `$MAIN/frontend` | Swapped back after the run. `GET /` → 200. |
| 8443 gateway | 68482 | 4d03h | `$MAIN/gateway` | untouched |
| 3002 portal | 88319 | 3d09h | `$MAIN/portal` | untouched |
| 8180 Keycloak | docker | — | — | untouched. `realms/docteams` OK |

### Backend restarts
1. **Run 1 (PID 4150):** started 10:22:59Z after killing the old :8080 holder (PID 39387, etime 3d11h). Then `svc.sh stop backend`; its stale pid file named 39260, which was still alive and was killed as well. Then `./mvnw -q clean compile -DskipTests` (EXIT 0; tenant V1..V8 present). Then `svc.sh start backend` with `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SERVLET_DISPATCHERSERVLET=DEBUG` and `LOGGING_LEVEL_IO_B2MASH_B2B_B2BSTRAWMAN_REPORT=DEBUG`. It was ready in 39 s and applied tenant V6–V8 (see `migrations.md`).
2. **The brief's DispatcherServlet env var does not work.** After login, the dashboard's API calls produced no `DispatcherServlet` request lines. Spring Boot relaxed binding lowercases env-var logger names, so the env var sets the level on the logger `org.springframework.web.servlet.dispatcherservlet`, not on `…DispatcherServlet`. Class-level loggers cannot be set through `LOGGING_LEVEL_*`. The package-level `…_REPORT` var does work.
3. **Run 2 (PID 5527):** killed 4150, ran `svc.sh stop`, then restarted at 10:25:09Z with `SPRING_APPLICATION_JSON='{"logging":{"level":{"org.springframework.web.servlet.DispatcherServlet":"DEBUG","io.b2mash.b2b.b2bstrawman.report":"DEBUG"}}}'` plus the two env vars. It was ready in 18 s. Flyway: both tenants were already at v8, with "No migration necessary". `GET "/actuator/health"` DEBUG lines appeared, which proves the oracle works. All O-rows ran against run 2.

The run-1 window (10:24:49–10:25:05Z) produced three `fetchUnreadCount` 500s in the frontend log. Those came from the backend being down during restart 2 and are unrelated to the slice.

## O1–O8

| # | Surface | Status | Key evidence |
|---|---|---|---|
| O1 | Timesheet Export CSV (Thandi) | **PASS** | Ran 2026-10-01..31, grouped by member: 15 entries / 28.75 h. Playwright `download` gave `timesheet-report.csv` (280 B). No inline error and no navigation. Frontend: `ƒ exportReportCsvAction("timesheet", …) in 47ms`. Backend: `GET "/api/report-definitions/timesheet/export/csv?groupBy=member&dateTo=2026-10-31&dateFrom=2026-10-01"` → `Completed 200 OK`. Audit: `REPORT_EXPORTED csv rowCount 3 @ 10:25:27.997Z`. |
| O2 | Timesheet Export PDF (Thandi) | **PARTIAL** | The download, the backend request line and the render all happened, but the audit-row oracle was **not met**. `timesheet-report.pdf` (2 806 B, magic `%PDF-1.6`) downloaded. Frontend: `ƒ exportReportPdfAction(…) in 3298ms`. Backend: `GET "/api/report-definitions/timesheet/export/pdf?…"`, then openhtmltopdf render lines (`parse stylesheets 27ms`, `Matcher created with 182 selectors`, `Using fast-mode renderer`), then `Completed 200 OK`. **No `REPORT_EXPORTED` pdf row was written.** Cause is a pre-existing backend bug, finding F-1. |
| O3 | Client-ledger statement PDF (Thandi, Moroka Family Trust, 2026-10-01..31) | **PARTIAL** (expected per R-1) | No redirect to /sign-in or "session expired" (`framenavigated` list empty; URL unchanged). No download. The request reached the backend: `GET "/api/report-definitions/client-ledger-statement/export/pdf?trust_account_id=1a2cfeee-…&dateTo=2026-10-31&dateFrom=2026-10-01&customer_id=df7536c3-…"` → `Completed 404 NOT_FOUND`. Frontend: `ApiError … No reportdefinition found with id client-ledger-statement` (status 404), server action POST 500, `ƒ generateStatementPdf(…) in 41ms`. The card shows inline **"Failed to generate statement. Please try again."** Browser console has one error: server-action POST 500. N-2 auth half is fixed. The PDF needs the `TrustReportPackSeeder` wiring follow-up. |
| O4 | Profitability Export CSV (Thandi, default range) | **PASS** | `profitability-2026-10-01-to-2026-10-28.csv` (354 B, CRLF, no BOM, UTF-8 em dash intact). Header `Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %`. 3 rows equal to the card's 3 rows (Estate Late Peter Moroka 20.00/37000.00/14900.00/22100.00/59.73; Nomvula Khoza — Debt Recovery 5.25/6137.50/2450.00/3687.50/60.08; Sipho Dlamini v. Standard Bank 3.50/2900.00/1150.00/1750.00/60.34; card shows 20.0h/R 37 000,00/…/59.7%). Frontend: `ƒ exportProfitabilityCsvAction("2026-10-01", "2026-10-28") in 45ms`. Backend: `GET "/api/reports/profitability/export/csv?from=2026-10-01&to=2026-10-28"` and `Profitability CSV export: 3 row(s) for 2026-10-01 to 2026-10-28` → 200. |
| O5 | Matter card range drives export | **PASS** | Changed only `#prof-from` to 2026-10-24. After the change: util 10-01..10-28, prof 10-24..10-28, cust-prof 10-01..10-28 (other cards untouched). `profitability-2026-10-24-to-2026-10-28.csv` (354 B). Frontend: `exportProfitabilityCsvAction("2026-10-24", "2026-10-28")`. Backend: `GET "/api/reports/profitability/export/csv?from=2026-10-24&to=2026-10-28"` and `Profitability CSV export: 3 row(s) for 2026-10-24 to 2026-10-28`. Rows are identical to O4 because all 15 time entries are dated 2026-10-24 (data, not a defect). |
| O6 | Audit trail → Export → Download PDF (control) | **PASS** | `audit-log-2026-10-28.pdf` (66 008 B, `%PDF-1.6`), saved as `627E-after-o6-audit-log-2026-10-28.pdf`. Frontend: `ƒ exportAuditPdfAction({"page":0,"size":50}) in 1106ms`. Backend: `GET "/api/audit-events/export.pdf"`, then openhtmltopdf lines, then 200. Audit: `audit.export.generated PDF 199 @ 10:27:55.360Z`. |
| O7 | Layout 1024 / 375 px | **PASS** (on its oracle) | At 1024 the button sits inline after From/To on the Matter card header row (y 514.5, the same row as the inputs at y 516). At 375 the title wraps above, and From/To/Export CSV stay on one row (y 578–580). Hiding the button in-DOM did not change page width (1469 → 1469), so the button does not break or widen the header. **Separate pre-existing finding F-2:** the page overflows horizontally at every viewport below 1469 px, which pushes the button (and the pre-existing Utilization "To" input) off-screen at 1024/1280. Screenshots: `627E-after-o7-*`. |
| O8 | Role check | **PASS** | **Carol** (member, avatar "CM"): `/reports/profitability` shows "You don't have access to Profitability / Contact your organisation admin to update your role." `profitability-export-csv` count 0. No `/api/reports/profitability*` request in the backend log during her session. **Bob** (admin, avatar "BN"): Export CSV downloaded `profitability-2026-10-01-to-2026-10-28.csv` (354 B, byte-identical to Thandi's). Frontend: `exportProfitabilityCsvAction("2026-10-01","2026-10-28") in 23ms`. Backend: `Profitability CSV export: 3 row(s) …` → 200. |

## Downloads (size, magic / `head -3`)

```
280   timesheet-report.csv
      # Timesheet Report
      # Generated: 2026-10-28T10:25:27.992738Z
      # Parameters: groupBy=member, dateTo=2026-10-31, dateFrom=2026-10-01
      (then: Group,Total Hours,Billable Hours,Non-Billable,Entries / Thandi Mathebula,12.00,12.00,0.00,5 / Bob Ndlovu,10.50,10.50,0.00,5 / Carol Mokoena,6.25,6.25,0.00,5)
2806  timesheet-report.pdf                    %PDF-1.6
354   profitability-2026-10-01-to-2026-10-28.csv
      Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %
      Estate Late Peter Moroka,Moroka Family Trust,ZAR,20.00,37000.00,14900.00,22100.00,59.73
      Nomvula Khoza — Debt Recovery (Mokoena Traders),Nomvula Khoza,ZAR,5.25,6137.50,2450.00,3687.50,60.08
354   profitability-2026-10-24-to-2026-10-28.csv   (same 4 lines)
354   627E-after-o8-bob-profitability-2026-10-01-to-2026-10-28.csv   (cmp-identical to Thandi's)
66008 627E-after-o6-audit-log-2026-10-28.pdf  %PDF-1.6
```

## Audit SELECT (read-only, `tenant_5039f2d497cf`, after the run)

```
       event_type       | format | row_count |   slug    |      actor       |          occurred_at
------------------------+--------+-----------+-----------+------------------+-------------------------------
 REPORT_EXPORTED        | csv    | 3         | timesheet | Thandi Mathebula | 2026-10-28 10:25:27.997023+00
 audit.export.generated | PDF    | 199       |           | Thandi Mathebula | 2026-10-28 10:27:55.359801+00
(2 rows)
```
`REPORT_EXPORTED` total: 0 before the run, 1 after. The O2 PDF export left no row.

## Findings (not caused by 627E; for orchestrator triage)

- **F-1 (backend, pre-existing): report PDF exports never write their `REPORT_EXPORTED` audit row.** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportExportService.java:106-107` `exportAsPdf` is `@Transactional(readOnly = true)` and calls `auditService.log(...)` (:116) inside that transaction. `DatabaseAuditService.log` is `@Transactional` (REQUIRED), so it joins the read-only transaction and the insert is never flushed. The CSV path is unaffected because `writeCsvAndAudit` (:158) runs outside the read-only `exportAsCsv` transaction. `ReportingControllerTest` covers only the CSV audit (`exportCsvPersistsReportExportedAuditEventWithCsvFormat`); there is no PDF audit test. Observed here: PDF 200, 0 audit rows.
- **F-2 (frontend layout, pre-existing): `/reports/profitability` overflows horizontally below about 1469 px.** `documentElement.scrollWidth` = 1469 at 1024 and 1280, and 1198 at 375. All three cards are stretched to 1150 px. Hiding the Matter Profitability table restores 1024 (main 784). Hiding the Export button changes nothing, and the table markup is unchanged by the PR diff. The `overflow-x-auto` table wrapper is defeated by an app-shell flex item (`div.flex.flex-1.flex-col`, min-width auto) with no `min-w-0`. Effect: at 1024/1280 the right-aligned card controls are off-screen without horizontal page scroll. That covers the new Export CSV (right edge x=1404) and the existing Utilization "To" input (x=1404).
- **F-3 (minor, pre-existing):** the timesheet PDF render logs `Couldn't resolve relative URI(org/tenant_5039f2d497cf/branding/logo.png) because no base URI was provided` three times, so the tenant logo is not embedded in report PDFs.
- **F-4 (brief/plan):** `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SERVLET_DISPATCHERSERVLET` has no effect (relaxed binding lowercases it). Use `SPRING_APPLICATION_JSON` or the package-level `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SERVLET=DEBUG`.

## Artefacts
- Screenshots: `627E-after-o1-timesheet-export-csv.png`, `-o2-timesheet-export-pdf.png`, `-o3-statement-card-before-click.png`, `-o3-statement-inline-error.png`, `-o4-profitability-before-export.png`, `-o4-profitability-export-csv.png`, `-o5-matter-range-export.png`, `-o6-audit-pdf.png`, `-o7-profitability-{1024px,1024px-unscrolled,1024px-matter-card,1280px-unscrolled,375px,375px-unscrolled,375px-matter-card}.png`, `-o8-carol-profitability-denied.png`, `-o8-bob-profitability-export.png`.
- Logs (redacted): `logs/o1-timesheet-csv.txt`, `logs/o2-timesheet-pdf.txt`, `logs/o3-statement-pdf.txt`, `logs/o4-o5-o8-profitability-csv.txt`, `logs/o6-audit-pdf-control.txt`, `logs/frontend-wt-full-redacted.txt`, `logs/backend-kc-messages-redacted.txt` (run 2), `logs/backend-kc-run1-migrations-redacted.txt` (run 1), `logs/playwright/*` (console logs + page snapshots). Token scan (`eyJ…`, `Bearer …`) found 0 hits.
- `migrations.md`: before/after `flyway_schema_history` plus the V6–V8 application statement.

## Teardown
Signed out via the UI (landed on `/signed-out`), cleared cookies (0 left) and closed the browser. Swapped the frontend back to `$MAIN` (PID 9943, cwd `$MAIN/frontend`, `GET /` 200). The backend is left running from `$MAIN` (PID 5527) with DEBUG logging still on. Stopped caffeinate (PID 2554). Copied this run's 33 files out of `$MAIN/.playwright-mcp/` (screenshots, console logs, snapshots, duplicate downloads) and then deleted only those files; the directory is back to 1450 entries. Nothing was committed.
