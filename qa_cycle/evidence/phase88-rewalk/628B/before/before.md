# 628B BEFORE evidence — e2e mock-auth stack :3001 (main @ 835f53338), 2026-10-28 ~22:50 SAST

| # | Check | Gap | Route | Viewport | Measured | Result | Screenshot |
|---|---|---|---|---|---|---|---|
| 1a | Shell overflow | G-D15 | /settings/documents (=?tab=templates) | 1024 | scrollWidth 1471 vs 1024 | REPRODUCED | 01, 02 |
| 1b | Shell overflow | G-D15 | /settings/documents | 1440 | 1471 vs 1440 (STATUS/ACTIONS cols + bell clipped) | REPRODUCED | 06, 07 |
| 1c | Shell overflow | G-D15 | /settings/documents | 1920 | 1920 = 1920 | NOT REPRODUCED (fits) | 11, 12 |
| 1d | Shell overflow | (615C wkaround) | /matters | 1024/1440/1920 | 1024/1440/1920 = viewport | NOT REPRODUCED (local workaround holds) | 03, 08, 13 |
| 1e | Shell overflow | (615D wkaround) | /clients | 1024/1440/1920 | = viewport | NOT REPRODUCED | 04, 09, 14 |
| 1f | Shell overflow | G88-M4 | /legal/disbursements | 1024/1440/1920 | 1653 / 1653 / 1920 | REPRODUCED @1024,1440 | 05, 10, 15 |
| 1g | Shell overflow | G88-M4 | /matters/ec2f3c51…?tab=disbursements (Ndlovu v RAF — Depth II, 2 rows) | 1024/1440/1920 | 1487 / 1487 / 1920 | REPRODUCED @1024,1440 | 16 |
| 2a | Breadcrumb | 628.4 (D11/R8) | matter detail | 1440 | "E2E Test Organization › Matters › Matter" (h1 = matter name) | REPRODUCED (generic last crumb) | 21 |
| 2b | Breadcrumb | 628.4 | client detail (Acme Corp) | 1440 | "… › Clients › Client" | REPRODUCED | 22 |
| 2c | Breadcrumb | 628.4 | fee note detail (INV-0001) | 1440 | "… › Fee notes › Fee note" | REPRODUCED | 23 |
| 2d | Breadcrumb | 628.4 | /invoices/billing-runs | 1440 | "… › Fee notes › billing-runs" (raw segment) | REPRODUCED | 24 |
| 2e | Breadcrumb | 628.4 | /invoices/collections | 1440 | "… › Fee notes › Collections" (nav says Debtors) | REPRODUCED | 25 |
| 2f | Breadcrumb | 628.4 | /compliance/requests | 1440 | "… › Compliance › requests" (raw, lower-case) | REPRODUCED | 26 |
| 2g | Breadcrumb | 628.4 | /matters/new | 1440 | "… › Matters › new" (raw) | REPRODUCED | 27 |
| 2h | Breadcrumb | — | /my-work, /trust-accounting/*, /legal/*, /settings/* , /diary, /reports | 1440 | "My Work", "Trust Accounting › Client Ledgers/Reports", "Legal › Tariffs/Disbursements", "Settings › Firm/Documents/Fees & Trust/Connections/Matters" | OK-ish (Title Case vs sentence-case nav; "Legal"/"Compliance" parents not nav words) | — |
| 3a | Settings label | G-D17 / D11 | /settings/fees | 1440 | tab "Tariffs" href ?tab=tariffs; page h1 "Rates & Currency" | REPRODUCED | 28 |
| 3b | Settings label | G-D18 / D11 | /settings/connections | 1440 | tab "Xero" href ?tab=xero; h1 "Integrations", shows 6 domain cards | REPRODUCED | 29 |
| 4a | Nav vs h1 | D11 | /invoices/billing-runs | 1440 | nav "Fee runs"; h1 "Fee notes" | REPRODUCED (h1 only) | 24, 20 |
| 4b | Nav vs h1 | D11 | /invoices/collections | 1440 | nav "Debtors"; h1 "Collections"; settings tab "Collections" | REPRODUCED (h1 only) | 25, 28 |
| 5a | Client badge | 628.4 | Acme Corp (status ACTIVE, lifecycle ACTIVE) | 1440 | header "Active | Active" | REPRODUCED (duplicate badge) | 30 |
| 5b | Client badge | 628.4 | Apex Holdings 5vui (ACTIVE/PROSPECT) | 1440 | header "Active | Prospect" | REPRODUCED | 31 |
| 5c | Client badge non-ACTIVE record | 628.4 | — | — | DB: all 9 customers status=ACTIVE; none archived | BLOCKED (no data) | — |
| 6 | Diary week titles | G-D20 | /diary?view=week (week 26 Oct–1 Nov 2026) | 1440 | court-date title "Gauteng Division of the High Court, Johannesburg" renders "G…" (span scrollWidth 332 / clientWidth 24; day col 153 px) | REPRODUCED | 32 |
| 7a | Sidebar org name | G-A3 | desktop sidebar | 1440 | color rgb(27,58,75) (= --brand-color #1b3a4b) via inline style, opacity 0.8, 12px, on slate-950 → ≈1.6:1 contrast | REPRODUCED | 33, 06 |
| 7b | Sidebar org name | G-A3 | mobile sheet | 390 | class text-white/60 (no brand colour) — legible | NOT REPRODUCED | 34 |
| 8a | Portal terminology | 628.4 | /home /matters /matters/<id> /invoices /invoices/<id> /requests /trust /deadlines /activity (2 contacts) | 1440 | 0 legacy words in text or aria/title/placeholder/alt | NOT REPRODUCED | 40-47, 60-69 |
| 8b | Portal terminology | 628.4 | /profile | 1440 | field label "Customer" (value = client name) | REPRODUCED | 47, 67 |
| 9 | PRO badge | G-D16 | /settings/connections?tab=xero | 1440 | no "PRO"/"Upgrade to Pro" text | NOT REPRODUCED (628A fixed) | 29 |
| 10 | Template source label | G-A7 | /settings/matters | 1440 | 6 SEEDER templates show "Pack" | NOT REPRODUCED (628A fixed) | 35 |

Notes:
- Data mutation: to get a diary event I created one court date via the UI (Khumalo Property Holdings — Erf 1234 Sandton Transfer, Hearing, 2026-10-29 09:30, court "Gauteng Division of the High Court, Johannesburg"). Diary had 0 court dates / 0 prescriptions / 0 dated tasks before.
- Side-finding: creating a court date on a matter with no client (Ndlovu v RAF 5vui, customer_id NULL) → UI "An unexpected error occurred"; backend 500 InvalidDataAccessApiUsageException "The given id must not be null" at CourtCalendarService.toResponse:513 (customerRepository.findById(null)); row rolled back. Out of 628B scope.
- Side-finding: settings/documents templates still show "Invoice Cover Letter" (template name), "ORGANIZATION" entity type (raw enum); settings/matters shows "Matter type: COLLECTIONS" (raw enum).
- Portal: production build of worktree-epic-628B/portal (== main 835f53338) on :3002 against :8081; dev-mode magic link from page; KC portal restored via svc.sh (pid 17622, :3002 → 307).
