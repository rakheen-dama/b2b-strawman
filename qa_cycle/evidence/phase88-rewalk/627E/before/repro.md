# Slice 627E BEFORE reproduction: G-D4 + N-2 (report exports / trust statement PDF in Keycloak BFF mode)

- **Date:** 2026-10-28 ~09:54–09:57 UTC
- **Stack:** KC dev stack. Frontend :3000 (`pnpm dev`, main checkout, origin/main pre-fix @ e1df4c43c), gateway :8443, backend :8080 (started before 627D, so no profitability CSV endpoint), Keycloak :8180.
- **Actor:** Thandi Mathebula (owner), `thandi@mathebula-test.local`. Logged in with a fresh session (cookies cleared, then KC two-step login).
- **Org / tenant:** `mathebula-partners` → `tenant_5039f2d497cf` (from `public.org_schema_mapping`).
- **Browser:** Playwright MCP (Chromium).
- **Backend-hit oracle:** backend and gateway logs do not record per-request access lines (`grep -c report-definitions` = 0 in both logs, including for the report run that worked). The oracle is the `REPORT_EXPORTED` audit event, which `ReportExportService` (:118, :167) writes on every CSV/PDF export. The tenant had **0** of these events before and after R1–R3. The R5 control shows that a real backend export writes an audit row (`audit.export.generated`) and produces openhtmltopdf render log lines.
- No data was created or modified. SQL was read-only.

## Code path (pre-fix)
`lib/auth/providers/keycloak-bff.ts:121-125` `getAuthToken()` always throws in BFF mode. `lib/api/reports.ts` `exportReportCsv`/`exportReportPdf` (:113, :141) catch that error and call `redirectToReLogin(..., "expired")`, which throws `NEXT_REDIRECT`. What the user sees depends on the caller:
- `reports/[reportSlug]/actions.ts` `exportReportCsvAction`/`exportReportPdfAction` wrap the call in a generic `try/catch`. `NEXT_REDIRECT` is not an `ApiError`, so the catch swallows it and returns `"Failed to export CSV."` / `"Failed to export PDF."`.
- `trust-accounting/client-ledgers/actions.ts` `generateStatementPdf` (:142) has **no** try/catch. `NEXT_REDIRECT` reaches the Next server-action runtime, which performs the redirect. The user is sent to the "session expired" page even though the session is valid.

## R1: Timesheet report, Export CSV: REPRODUCED
- URL: `http://localhost:3000/org/mathebula-partners/reports/timesheet` (slug `timesheet`)
- Params: From 2026-10-01, To 2026-10-31, Group By member. **Run Report works:** 15 entries, 28.75 h (Thandi 12.00 / Bob 10.50 / Carol 6.25).
- Clicked **Export CSV**. UI shows a red inline alert: **"Failed to export CSV."** No download event (Playwright `download` listener = null). The only browser request was the server-action `POST /org/mathebula-partners/reports/timesheet`.
- Frontend log: `ƒ exportReportCsvAction("timesheet", {...}) in 5ms`. At 5 ms there was no network round-trip.
- Backend: no `export/csv` in the log, no `REPORT_EXPORTED` audit event (0).
- Evidence: `627E-before-r1-export-csv.png`, `logs/r1-export-csv.txt`

## R2: Timesheet report, Export PDF: REPRODUCED
- Same page and params. Clicked **Export PDF**. UI shows **"Failed to export PDF."** No download. One server-action POST.
- Frontend log: `ƒ exportReportPdfAction("timesheet", {...}) in 2ms`.
- Backend: no `export/pdf`, `REPORT_EXPORTED` = 0.
- Evidence: `627E-before-r2-export-pdf.png`, `logs/r2-export-pdf.txt`

## R3: Trust client ledger, Print Statement (PDF): REPRODUCED, with a DIFFERENT failure mode
- URL: `http://localhost:3000/org/mathebula-partners/trust-accounting/client-ledgers/df7536c3-aa8d-4a49-b094-3db2bbbd2279` (Moroka Family Trust, balance R 38 000,00; trust account `1a2cfeee-0562-47e6-88d3-8a79dd7ba746` "Mathebula & Partners Trust Account"; 2 transactions on 2026-10-24).
- "Generate Statement" card: From 2026-10-01, To 2026-10-31, then clicked **Print Statement** (`627E-before-r3-statement-card-before-click.png`).
- **Observed:** the whole page navigated to `http://localhost:3000/sign-in?reason=expired&returnTo=%2Forg%2Fmathebula-partners%2Ftrust-accounting%2Fclient-ledgers%2Fdf7536c3-aa8d-4a49-b094-3db2bbbd2279`. It shows **"Your session expired"** / "Your session expired for security. Sign in to continue." / button "Continue to sign in". No download. The component's own error text ("Failed to generate statement. Please try again.") never appears.
- **The session was NOT expired.** A direct `goto` back to the same ledger URL right afterwards loaded normally (h1 "Moroka Family Trust", statement card present) with no login prompt.
- Frontend log: `ƒ generateStatementPdf("1a2cfeee-…", "df7536c3-…", "2026-10-01", "2026-10-31") in 3ms`, then `GET /sign-in?reason=expired&returnTo=…`.
- Backend: no `export/pdf`, `REPORT_EXPORTED` = 0.
- Evidence: `627E-before-r3-statement-pdf-redirect.png`, `logs/r3-statement-pdf.txt`

## R4: Profitability page, Export CSV button: REPRODUCED (absent)
- URL: `http://localhost:3000/org/mathebula-partners/reports/profitability` (h1 "Profitability").
- No element in `<main>` matches /export/i (count 0). The page has only these buttons and controls: "Help: Utilisation rate", "Help: Profitability margin", 3x "Expand currency breakdown", and sortable column headers (Name, Total Hours, Billable, Non-Billable, Utilization %, Matter, Client, Billable Hours, Revenue, Cost, Margin, Margin %, Customer, …). There are no links.
- Note: the running backend predates 627D, so `GET /api/reports/profitability/export/csv` also does not exist on this stack.
- Evidence: `627E-before-r4-profitability-no-export.png` (full page)

## R5 (control): Audit-log Download PDF in the same BFF session: WORKS
- URL: `http://localhost:3000/org/mathebula-partners/settings/firm?tab=audit-trail`, then Export, then **Download PDF**.
- Downloaded `audit-log-2026-10-28.pdf` (65 KB, `%PDF-1.6`). No error.
- Frontend log: `ƒ exportAuditPdfAction({"page":0,"size":50}) in 2535ms`. This is a real round-trip, unlike the 2–5 ms report actions.
- Backend log: openhtmltopdf render lines. Audit row `audit.export.generated` at 2026-10-28 09:57:19 UTC.
- This shows that the `getAuthFetchOptions` + `fetchWithTimeout` + `API_BASE` (gateway, cookie credentials) template in `lib/api/audit-events.ts` `exportAuditPdf` (:241) works in BFF mode.
- Evidence: `627E-before-r5-audit-pdf.png`, `627E-before-r5-audit-log-2026-10-28.pdf`, `logs/r5-audit-pdf-control.txt`

## Summary
| # | Surface | Verdict | Observed |
|---|---------|---------|----------|
| R1 | Timesheet Export CSV | REPRODUCED | "Failed to export CSV.", no download, no backend hit |
| R2 | Timesheet Export PDF | REPRODUCED | "Failed to export PDF.", no download, no backend hit |
| R3 | Client-ledger Print Statement | REPRODUCED (different mode) | False "Your session expired" redirect to `/sign-in?reason=expired`, session actually valid, no backend hit |
| R4 | Profitability Export CSV | REPRODUCED (absent) | No export control on the page |
| R5 | Audit PDF control | WORKS | PDF downloaded, backend rendered, audit row written |
