# 666A OBS-before: Fee notes list, Fee runs (list, wizard, detail), Debtors, Unbilled work — task 666.1 "Observe-first"

- **When:** 2026-11-27, 00:13Z to 00:27Z. Discover-pre 00:13:41Z; time entries 00:16:21Z; Alice capture (state pre) 00:16:34Z; fee run created 00:18:59Z (03, stopped at step 2); Alice capture (state preview) 00:19:42Z; wizard resumed 00:20:19–00:20:40Z (03b); Alice capture (state post) 00:20:49Z; role created 00:22:02Z, assigned 00:22:05Z; invoicing-role capture 00:22:13Z; Carol back to Member 00:23:11Z; Carol capture 00:23:16Z; module off 00:24:29Z → on 00:24:52Z; spec run 00:25:02–00:25:10Z; crops 00:25:49Z; discover-post 00:26:16Z; assemble 00:27:20Z.
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`. Not restarted, rebuilt, reseeded or torn down.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`; the worktree has none). Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm). The wizard keeps its state in React, so its dark tiles use the theme script's own class swap in-page (`<html>` `light|dark` + `style.colorScheme`, `components/theme/theme-config.ts`) instead of a reload.
- **What changed:** no product code, no SQL. Reads through the API (GET); every OBS write went through the UI (2 time entries, 1 fee run with 2 fee notes approved, 1 role, Carol's role there and back, the Bulk Billing module off and on). The spec run made its own API calls.
- **Raw files:** 188 raw PNGs in `/tmp/666A-before-raw/`, outside the repo. Committed: 11 contact sheets + 6 defect crops.

## Stack observed (build source)

- **Frontend container:** `docker inspect e2e-frontend` → created **`2026-11-26T23:51:00.915Z`**, image `sha256:b25eb4cb947c…`, compose `working_dir` label `/Users/…/worktree-epic-665F/compose`. 665F's final product head was `cec81dad5`.
- **It equals this worktree:** `git diff --stat cec81dad5 HEAD -- frontend packages compose` (HEAD = `2ef49ee4b` = `main`) is **empty** (exit 0). Caveat as before: uncommitted changes in the 665F worktree at build time can't be ruled out after the fact.
- **Backend:** `e2e-backend` created `2026-11-26T23:51:00.513Z`, image `sha256:e7fd89e47741…`; org roles before this OBS = Owner, Admin, Member only (none of 665F's roles: a fresh seed).

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities | Fee-note API (GET) |
|---|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / false / true | 29 (all, incl. INVOICING, FINANCIAL_VISIBILITY, AI_REVIEW) | all 200 |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST | 403 on all 7 (invoices, outstanding-summary, billing-runs, debtors, gates, unbilled-summary, modules) |
| `carol-cr-invoicing` | Carol while holding **"666A OBS Invoicing"** | that / false / false | **INVOICING** only | 200: invoices, billing-runs, debtors, unbilled-summary; **403**: outstanding-summary, ai/gates, settings/modules |

### Custom role (`02-roles.cjs`, through the UI as Alice)

- The Create Role dialog offers INVOICING under the label **"Billing"** (`lib/capabilities.tsx:30-31`) and FINANCIAL_VISIBILITY as "Financial Visibility", so the requested persona is buildable: **"666A OBS Invoicing"** → id **`fbf39c81-36ec-444b-8f3f-ec6c0bb95792`**, exactly `[INVOICING]` (no FINANCIAL_VISIBILITY, no AI_REVIEW). Created via Settings › Firm › Roles › New Role at 00:22:02Z; verified by `GET /api/org-roles`.
- **Assignment** (Team › Carol's row › Member Detail › Role › Save): Member → 666A OBS Invoicing (00:22:05Z) → **Member** (00:23:11Z). After each step `GET /api/members/{id}/capabilities`, Carol's `/api/me/capabilities` and her GET status on 7 endpoints were recorded (`measurements.json` → `customRoles`).
- **Final:** Carol is **Member**, `overrides: []`, the same 5 capabilities. The role is **left on the stack** (0 members) for OBS-after; `02-roles.cjs create` is idempotent.

## Data created / changed (ids for OBS-after)

| What | How | Final state |
|---|---|---|
| Time entry 1h30 billable on "Khumalo Property Holdings — Erf 1234 Sandton Transfer mwy7mytk9055" › task "Receive instruction & conflict check" → id `52edcc52-5fb4-493f-95a1-bfbb5a975c97` (R 3 750,00, member default rate) | Matter › Work › Tasks › Log Time (`02-time.cjs`) | billed by the run (INV-0006) |
| Time entry 45m billable on "Ndlovu Property Dispute mwy7iyj8bd18" › "Initial consultation & case assessment" → id `cea815a6-a91d-46f6-8e62-d6952b758d7c` (R 1 875,00) | same | billed by the run (INV-0007) |
| **Fee run `af572249-eb88-4571-a33c-c5c8e5d3e9dd`** (period 2026-11-01 – 2026-11-27, ZAR, **name null**) | New fee run wizard (`03` steps 1–2, `03b` steps 2–5) | **COMPLETED**: 2 clients, 2 fee notes generated, 0 sent, 0 failed, total R 6 468,75 |
| Fee notes **INV-0006** `007aeed4-9860-4dce-bb28-00b37d9c96e5` (Khumalo, R 4 312,50) and **INV-0007** `d6467449-d114-41e1-a262-703724524578` (Sipho Ndlovu mwy7iyj8bd18, R 2 156,25) | generated on wizard step 4, approved by "Approve All & Continue" | **APPROVED, not sent** ("Send All" → "Confirm Send" → **Cancel**) |
| Role "666A OBS Invoicing" `fbf39c81-…` | Roles UI | **left**, 0 members |
| Carol Member → 666A OBS Invoicing → Member | Team UI | restored |
| Module `bulk_billing` ON → OFF (00:24:29Z) → ON (00:24:52Z) | Settings › Firm › Features switch "Toggle Bulk Billing Runs" (`04-module-off.cjs`, restore in `finally`) | **ON** (as before) |
| **Spec run** (00:25:02–00:25:10Z): clients "InvLife Client p3zn", "InvCrud Client p3zp", "Arith Client p3zn" (lifecycle ACTIVE); 13 fee notes (8 DRAFT, INV-0008 PAID, INV-0009/INV-0010 VOID, INV-0011 APPROVED); 3 emails to Mailpit (INV-0008/9/10 sends) | `e2e/tests/invoices/` | left |

- **Why the time entries:** before them, `GET /api/invoices/unbilled-summary` was `[]` for every period. The only unbilled time on the stack (R 21 250 over 3 clients) belongs to **ONBOARDING** clients, and the summary and the fee-run preview count lifecycle-ACTIVE clients only (`UnbilledTimeService.java:118`, `BillingRunSelectionService.java:533-535`). Without them, Unbilled work had no "with data" state and the wizard would stop at an empty "Select Clients" (no items, no detail item links).
- **Why approve:** wizard step 5 is only reachable through "Approve All & Continue". Approval sends no email (Mailpit 14 before the OBS run; the 3 new messages are all from the spec run, 00:25:05–07Z). Step 5's "Send All" was opened and **cancelled**.
- **03 stopped after creating the run:** it looked the run up by `notes`, which `GET /api/billing-runs` does not return. Scripts are not edited after running, so `03b-fee-run-resume.cjs` (a copy with the lookup by period + creator) resumed the run through the wizard's own entry point `/invoices/billing-runs/new?billingRunId=<id>` (starts at step 2), and re-shot step 1 on a separate page without pressing Next. `01b-capture.cjs` is `01-capture.cjs` with the same lookup fix and a `preview` state; `01` itself ran once (`alice pre`). No second run was created (`discover-post`: 1 run).
- Nothing destructive was confirmed. Mailpit 14 → 17 (spec run only).

## Per surface

States: **pre** = before the OBS run (8 fee notes, no runs); **preview** = the OBS run in PREVIEW (wizard left at step 2); **post** = run COMPLETED, INV-0006/0007 APPROVED. Every route: `main#main-content` = 1, `<main>` = 1, h1 in `#main-content` = 1 (except the module-off fallback: **0**), h1 page-wide = 1, document h-overflow 0 at 1024 and 1440, light and dark. Document title on all routes: the generic **"Kazi — Practice management, built for Africa"**.

### `/invoices` (Fee notes)

- **Header:** h1 "Fee notes" + HelpTip ("Help: Fee note lifecycle"); count pill `span.rounded-full bg-slate-200` = number of listed rows (8 pre, 10 post; filtered count under a filter; absent when 0); "New fee note" accent button (dropdown, shown when there are active clients).
- **Fee notes | Fee runs:** two plain links, **no `aria-current`** on either (P-8). Active "Fee notes": `border-b-2 border-slate-900 … text-slate-900`; inactive "Fee runs": `border-transparent … text-slate-500`. Same on `/invoices/billing-runs` with the roles swapped. Hrefs `/org/e2e-test-org/invoices` and `/org/e2e-test-org/invoices/billing-runs`.
- **Tiles (owner, `grid gap-4 sm:grid-cols-2 lg:grid-cols-4`):** "Total outstanding" `fee-notes-total-outstanding` R 18 257,63 · "Overdue" `fee-notes-overdue` **R 0,00 in `text-red-600`** (K-73) · "Approved, not sent" `fee-notes-approved-unsent` R 0,00 (pre) / R 6 468,75 (post) · "Paid This Month" `fee-notes-paid-this-month` R 40 250,00 (`text-green-600`, 3.22:1, 24 px semibold = large text). API `outstanding-summary`: outstanding 18 257,63 (4), overdue 0, approvedUnsent 0 → 6 468,75 (2).
- **Invoicing role (no FINANCIAL_VISIBILITY):** **"Paid This Month" alone** (R 40 250,00), container `grid gap-4 sm:max-w-xs`, **320 px**, 1 column; the other three testids absent (0); no `outstanding-summary` request needed (403 for that role anyway).
- **Carol:** HTTP **404**, h1 **"Page not found"**, "This page doesn't exist, or you don't have access to it." + "Back to Home" (breadcrumb still "Fee notes").
- **Status filter links** (clicked one by one, `sheet-filters-alice.png`; `measurements.json` → `runs.*.filters`): All → `/invoices` · Draft → `/invoices?status=DRAFT` · Approved → `?status=APPROVED` · Sent → `?status=SENT` · Partially Paid → `?status=PARTIALLY_PAID` · Paid → `?status=PAID` · Void → `?status=VOID`. Active chip `bg-slate-900 text-white`. Empty filter → EmptyState "No approved fee notes found" / "No void fee notes found" (pre). Identical URLs for the invoicing role.
- **URL-only state:** `?customerId=<client id>` → 1 row (that client), chip "All" active, **nothing on the page names the client filter**; no UI control sets `customerId`, and every chip's href drops it. `?customerId=…&status=SENT` → 1 row, "Sent" active. `?status=FOO` and `?status=draft` → **EmptyState "No fee notes yet"** with "All" active although 8 fee notes exist (the API 400 is swallowed). There is **no search box**.
- **Table:** raw `<table>` in `div.overflow-x-auto`; headers FEE NOTE, CLIENT, STATUS, ISSUE DATE (`sm:`), DUE DATE (`lg:`), TOTAL, CURRENCY (`xl:` → hidden at 1024). Row link = the number (or "Draft") → `/invoices/{id}`; `row-balance-due` on the PARTIALLY_PAID row (1). Status chips: Draft neutral, Approved/Sent lead, Partially Paid warning, Paid success. `main.getByRole("row")` = rows + 1.
- **Brought forward — take-on badge (P-1 re-scout):** **not on the stack** — 0 of 23 fee notes carry `takeOnBatchId` (discover-post); `fee-note-takeon-badge` count 0 everywhere. Creating one needs the Phase 93 matter take-on + sign-off flow; not attempted.

### `/invoices/billing-runs` (Fee runs)

- **pre:** h1 "Fee runs", "New Billing Run" (header button-link) + EmptyState "No fee runs yet" with a second "New Billing Run" link (2 links).
- **preview:** amber banner **"Active billing run in progress" / "“” is currently in preview." + "View"** (the name is null), table NAME | STATUS | PERIOD | CLIENTS | FEE NOTES | TOTAL | CREATED; the row's Name cell is an **empty link**; Status "Preview".
- **post:** banner gone; row Completed, 01 Nov 2026 – 27 Nov 2026, 2, 2, R 6 468,75, 27 Nov 2026; Name link still empty (crop) — no visible way into the detail now.
- **Carol and the invoicing role:** in-page denial, h1 "Fee runs" + "You do not have permission to view billing runs. Only admins and owners can access this page." — no Fee notes | Fee runs links (0/0), although `GET /api/billing-runs` answers **200** for INVOICING.
- **Module off (`04-module-off.cjs`, `sheet-module-off-alice.png`):** `/billing-runs`, `/new`, `/[id]` all render `ModuleDisabledFallback`: **no h1**, h2 "Bulk Billing Runs is not enabled", text "…An admin can enable it in Settings → Features.", link "Go to Features" → `/org/e2e-test-org/settings/features` (legacy route, redirects); card 448 px. `/invoices` keeps its "Fee runs" link with the module off.

### `/invoices/billing-runs/new` (wizard, `sheet-wizard-alice.png`)

h1 "New fee run"; `nav[aria-label="Wizard steps"]` with 5 numbered badges: Configure, Select Clients, Review & Cherry-Pick, Review Drafts, Send.

1. **Configure** — h2 "Configure Billing Run"; Period From / Period To (date), Cut-off Date (optional), Notes (optional), "Next". **No Name field.** Next with nothing typed → "Period From is required. Period To is required." (red list). Cut-off Date and Notes are **not in the backend request** (`BillingRunDtos.java:23-30` has name, periodFrom, periodTo, currency, includeExpenses) — dropped.
2. **Select Clients** — checkbox table (header checkbox "Select all clients") | Client | Unbilled Time | Unbilled Disbursements | Total; 2 rows, all selected; summary bar "2 clients selected, R 5 625,00 total" + Back / Next.
3. **Review & Cherry-Pick** — one collapsible section per client (button `aria-expanded`); expanded: Include | Date | Member | Task | Hours | Rate | Amount (1 row) + Exclude; "2 clients included".
4. **Review Drafts** — generation on mount; "Set Due Date for All", "Set Payment Terms for All"; table Client | **Fee note #** | Items | Total | Status, Fee note # = **"INV-007aeed4" / "INV-d6467449"** (UUID prefix); "Drafts: 2  Total: R 5 625,00"; row click → sheet "Edit fee note" (Due Date, Payment Terms, Notes, Save) → Escape; "Approve All & Continue".
5. **Send** — table Client | Fee note # (still the UUID prefix while the real numbers are INV-0006 / INV-0007) | Total | Status "Approved"; "Ready to send: 2 fee notes totaling R 5 625,00"; "Send All" → alertdialog **"Confirm Send — Send 2 fee notes totaling R 5 625,00? This action cannot be undone."** → Cancel. The fee notes total **R 6 468,75** (VAT included).
- Step badges (K-76): active white on teal-600 **3.16:1** light and dark; pending 4.18 light / 5.84 dark; completed 4.75 light / 9.9 dark. At 1024 the step labels wrap to two lines. Step tables fit at 1024 (662 / 628 / 710 px, no h-overflow).

### `/invoices/billing-runs/[id]` (`sheet-run-detail-alice.png`)

- "Back to Fee runs" link; **h1 empty** (K-65) + status pill (Preview / Completed); "Period: 01 Nov 2026 – 27 Nov 2026 · Completed 27 Nov 2026"; PREVIEW: "Cancel Run"; COMPLETED: **"Approve All Generated" + "Cancel Run"** (both still offered after every fee note was approved).
- **Summary cards** (private `StatCard`, `grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5`): labels **Clients, Fee notes generated, Sent, Failed, Total Amount** (rendered uppercase); post: 2, 2, 0, 0, **R 6 468,75**. 5 columns at both widths: cards 130 px (1024) / 213 px (1440); at 1024 "R 6 468,75" **spills 42 px** out of its card.
- **Items** (h2): Client | Status | Time Amount | Disbursement Amount | Total | Fee note | Failure Reason; post rows "Generated", R 3 750,00 / R 1 875,00 (ex-VAT), "View fee note" → `/invoices/{id}` (teal-600, **3.13:1**, K-34). PREVIEW: 2 rows "Pending", no links. 1024: table 738 (preview) / 753 (post) px in 710 (Failure Reason cut 29 / 44).
- Carol / invoicing role: h1 "Fee run" + the same denial sentence.

### `/invoices/collections` (Debtors, `sheet-collections-alice.png`)

- h1 "Debtors" + subtitle; h2 "Debtor book" + subtitle; table in `overflow-x-auto rounded-lg border`: CLIENT (link → `/clients/{id}` + "1 fee note · ZAR") | OUTSTANDING | OLDEST (`sm:`) | CURRENT / 30D / 60D / 90D+ (`lg:`) | SIGNALS | LAST ACTIVITY (`xl:`). 4 debtors (Acme Corp R 8 625,00; Sipho Ndlovu mwy7iyj8bd18 R 5 482,63; Apex Holdings mwy7mytk9055 R 4 025,00; Moroka Family Trust R 125,00), all "30d until due"; one chip "Trust funds available" (`triage-badge-TRUST_FUNDS_AVAILABLE`); Last activity "No chase history" (slate-400, **2.64:1**).
- h2 "Pending reminders" (AI_REVIEW holders) → h3 **"No reminders awaiting approval"** (0 pending `SEND_COLLECTION_REMINDER` gates). The invoicing role has **no** Pending reminders section (no AI_REVIEW). Carol → 404 "Page not found".
- **Not reached:** reminder cards and their AI review links (no pending gates on the stack; making one needs an AI collections run); the empty Debtor book ("No outstanding balances. Everything is settled.") — needs every fee note settled.

### `/invoices/unbilled` (Unbilled work, `sheet-unbilled-alice.png`)

- h1 "Unbilled work"; `unbilled-period-help` "Unbilled time and expenses per client from 27 Nov 2024 to 27 Nov 2026, in ZAR. The period defaults to the last 24 months …"; form `aria-label="Unbilled work filters"`: From `#unbilled-period-from`, To `#unbilled-period-to` (date), Currency (Select, `#unbilled-currency` / testid `unbilled-currency`, options ZAR, USD, EUR, GBP), "Apply" → `router.replace(?periodFrom=…&periodTo=…&currency=…)`.
- **With data (pre):** `unbilled-work-table` CLIENT | TIME | EXPENSES | TOTAL | READY TO BILL — Khumalo R 3 750,00 (1) / Sipho Ndlovu R 1 875,00 (1), "Yes"; `unbilled-grand-total`. 1024: 738 px in 710 (READY TO BILL cut 28).
- **Empty:** `?periodFrom=2001-01-01&periodTo=2001-01-31&currency=ZAR` → EmptyState **"No unbilled work"** "No client has unbilled time or expenses in this period and currency. Widen the period or pick another currency." Post (default period) is also empty — the run billed both entries — although 3 ONBOARDING clients still carry R 21 250 unbilled (API).
- Invoicing role: same page (empty, post). Carol → 404.

## Measurements (`measurements.json`)

- **Main content:** `#main-content` inner width **760 px at 1024**, **1176 px at 1440** (every route); document h-overflow 0 everywhere (light and dark).
- **Tile container** (`measurements.json` → `tileContainer`): owner `div.grid` **712 px at 1024** (4 cols × 166 px, via `lg:grid-cols-4`) and **1128 px at 1440** (4 × 270). **At 1024 the container is < 768 px**, so a `@3xl` (48rem) container query would give the 1024 layout 2 columns where today it has 4. Invoicing role: 320 px, 1 col, at both widths.
- **Tables (scroller client / table width):** list 712/712 (1024), 1128/1128 (1440) — no overflow; Fee runs list 712/712, 1128/1128; Debtor book **710/894 at 1024 (+184; 90D+ cut 11 px, SIGNALS hidden)**, 1126/1126 at 1440; Unbilled 710/738 (+28) / 1126; run Items 710/738 (preview) and 710/753 (post), 1126 at 1440.
- **K-72 (list at 1024):** every number link breaks at its hyphen (crop run: 6/6 "INV-…" on 2 lines; "Draft" 1 line); headers FEE NOTE, ISSUE DATE, DUE DATE on 2 lines; rows **49–77 px**; **4 rows above the 768 fold** (8 at 1440, rows 49–61).
- **K-73:** "Overdue" R 0,00 `text-red-600` (4.77:1 — the colour, not contrast, is the kink).
- **K-71:** Debtor book 894 px in 710; "30d until due" on 3 lines (4/4); rows 85–113 px. Dark identical.
- **K-65:** detail h1 text "" (PREVIEW and COMPLETED); list Name link empty; banner "“”".
- **K-34:** "View fee note" teal-600 on white **3.13:1**.
- **Run summary cards:** 5 columns at 1024 (130 px), "Total Amount" spills 42 px.
- **Other contrast (< AA, `lowContrast*`):** "No chase history" 2.64 (light) / 4.24 (dark); unbilled "(1)"/"(0)" counts 4.24 (dark); "New fee note" accent button 3.16 (dark).

## SEL-COMPAT before (`sel-compat.json`, 1024 light)

| Selector (spec) | Alice | invoicing role | Carol |
|---|---|---|---|
| h1 "Fee notes" (`day-30-billing:83,297`, `day-90:113`) | 1 | 1 | 0 (404) |
| h1 "Fee runs" (`billing-runs.spec:20`) | 1 | 1 (denial) | 1 (denial) |
| h1 "New fee run" (`:42`) / h2 "Configure Billing Run" (`:46`) | 1 / 1 | 1 / 0 | 1 / 0 |
| main link "Fee notes" / "Fee runs" exact (`:28-29`) on `/billing-runs` | 1 / 1 | 0 / 0 | 0 / 0 |
| main link "New Billing Run" (`:31,38`, `.first()`) | 2 (pre, empty) / 1 (with runs) | 0 | 0 |
| `locator("main").getByRole("row")` on `/invoices` (`invoice-crud:55,106`) | 9 pre / 11 post | 11 | 0 |
| testid `row-balance-due` | 1 | 1 | 0 |
| testids `fee-notes-total-outstanding` / `-overdue` / `-approved-unsent` / `-paid-this-month` | 1/1/1/1 | 0/0/0/1 | 0 |
| testid `fee-note-takeon-badge` | 0 | 0 | 0 |
| h1 "Debtors" / h2 "Debtor book" / h2 "Pending reminders" | 1/1/1 | 1/1/0 | 0 |
| h1 "Unbilled work"; testids `unbilled-period-help`, `unbilled-currency`, `unbilled-work-table`, `unbilled-grand-total` | 1; 1, 1, 1 (pre) / 0 (post), same | 1; 1, 1, 0, 0 | 0 |

`unbilled-summary-expenses.test.tsx:121` is a vitest text on the client-detail unbilled summary, not on these routes; not observable here.

## Spec baseline (`specs-baseline.json`)

- **Command:** from the **main checkout** `frontend/` @ `2ef49ee4b` (spec files byte-identical to the worktree's), nvm Node 20: `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 SHELL=/bin/bash NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/invoices/ --reporter=line` (`--config` needed: no root `frontend/playwright.config.*`). Log `/tmp/666A-before-raw/spec-run.log`.
- **Result: 21 passed, 0 failed, 0 skipped** (7.5 s, 4 workers): BR-01 ×2, INV-01 (crud) ×5, INV-02 (lifecycle) ×8, INV-03 (arithmetic) ×6.
- **Tree:** `git status --short` of the main checkout identical before and after; nothing new under `frontend/test-results` or screenshots. Nothing to restore.

## Console (`console.json`)

149 page loads in the captures + the setup, wizard and module runs: **16 errors, 0 warnings, 0 page errors, 0 hydration** — all 16 are "Failed to load resource: … 404" for Carol's 4 notFound routes × 4 variants (the document itself is a 404). Nothing else. The crops run does not record console events.

## Defects / kinks observed (pre-existing; not fixed)

Register kinks (owner 666A unless noted):

- **F-666A-B1 (K-72):** list at 1024 — numbers break at the hyphen, date headers wrap, rows 49–77 px, 4 rows above the fold. Crop `crop-defect-list-table-1024.png`.
- **F-666A-B2 (K-73):** "Overdue" R 0,00 in red. Crop `crop-defect-tiles-overdue-1440.png`.
- **F-666A-B3 (K-71):** Debtor book 894 px in 710 at 1024, SIGNALS out of view, "30d until due" on 3 lines. Crop `crop-defect-debtors-table-1024.png`.
- **F-666A-B4 (K-65):** fee runs have no name: detail h1 empty, list Name link empty, banner "“”". **Now captured:** once the run is no longer active the list row has no visible link into the detail (the empty anchor is the only link). Crops `crop-defect-run-detail-header-1440.png`, `crop-defect-runs-list-row-1440.png`.
- **F-666A-B5 (K-34):** "View fee note" teal-600 3.13:1. Crop `crop-defect-run-items-1440.png`.
- **F-666A-B6 (K-76):** step badges 3.16:1 (active) / 4.18:1 (pending, light); labels wrap at 1024.
- **F-666A-B7 (K-77):** "New Billing Run", "Active billing run in progress", "Configure Billing Run", "Bulk Billing Runs is not enabled", "…view billing runs" on Fee-run pages.
- **F-666A-B8 (K-78):** three "no access" treatments on one family: 404 "Page not found" (list, Debtors, Unbilled) vs bare h1 + sentence (3 fee-run routes) vs the module-off card.
- **F-666A-B9 (K-14 / FD-10, backlog):** "Paid This Month" is computed from the filtered list — any status filter except Paid shows R 0,00 while the three debtor tiles stay.

New:

- **F-666A-B10 (layout, 1024):** fee-run summary cards stay 5 columns at 1024 (130 px each); "R 6 468,75" spills 42 px out of the "Total Amount" card.
- **F-666A-B11 (money, MEDIUM):** one run, two totals. Wizard steps 2/4/5, the Send confirm ("Send 2 fee notes totaling R 5 625,00") and the detail Items table show the **ex-VAT** unbilled amounts (R 3 750 + R 1 875); the run's "Total Amount" card, the list's Total and the fee notes themselves are **VAT-inclusive** (R 6 468,75 = R 4 312,50 + R 2 156,25).
- **F-666A-B12 (copy, MEDIUM):** wizard steps 4 and 5 show "Fee note #" as `INV-` + the first 8 hex digits of the fee-note UUID ("INV-007aeed4"), even after approval assigned INV-0006 / INV-0007 (code comment at `review-drafts-step.tsx`: "use truncated ID as placeholder"). It reads like a real number.
- **F-666A-B13 (data loss, MEDIUM; Notes observed, Cut-off from code):** the Configure step's "Cut-off Date" ("Only include time entries logged up to this date") and "Notes" are sent by the frontend (`lib/api/billing-runs.ts:144-145`) but `CreateBillingRunRequest` has neither (`BillingRunDtos.java:23-30`): both are silently dropped. The typed Notes appear nowhere (API response has no `notes`). A user relying on the cut-off would bill entries after it (not exercised).
- **F-666A-B14 (copy / behaviour):** Unbilled work says "No client has unbilled time or expenses in this period" while 3 ONBOARDING clients carry R 21 250 of unbilled time (client pages / `GET /api/clients/{id}/unbilled-summary`); the summary counts lifecycle-ACTIVE clients only (`UnbilledTimeService.java:118`) and the page doesn't say so.
- **F-666A-B15 (URL state, LOW):** `?status=FOO` / `?status=draft` → "No fee notes yet" with "All" active though fee notes exist.
- **F-666A-B16 (URL state, LOW):** `?customerId=` filters the list with no visible indicator and no UI to set or clear it; any status chip drops it.
- **F-666A-B17 (behaviour, LOW; buttons observed, effect not exercised):** a COMPLETED run whose fee notes are all approved still offers "Approve All Generated" and "Cancel Run"; its items keep status "Generated".
- **F-666A-B18 (gating):** INVOICING holders get 200 from `GET /api/billing-runs` but the three fee-run pages deny them (admin/owner only); the denial page also drops the Fee notes | Fee runs links. (The app rail's Fees group was collapsed in every capture, so its items per persona were not probed.)
- **F-666A-B19 (a11y / contrast, LOW):** "No chase history" 2.64:1 (light); unbilled "(n)" counts 4.24:1 (dark); "New fee note" accent button 3.16:1 (dark).
- **F-666A-B20 (layout, 1024, LOW):** Unbilled table 738 px in 710 (READY TO BILL cut 28); run Items table 753 px in 710 (Failure Reason cut 44). D-44 class.
- **F-666A-B21 (structure, LOW):** the module-off fallback has no h1 (h2 only) and links to the legacy `/settings/features`; `/invoices` keeps the "Fee runs" link while the module is off.
- **F-666A-B22 (metadata, LOW):** all 7 routes have the generic title "Kazi — Practice management, built for Africa".
- **P-8 (known, not a defect id):** neither Fee notes | Fee runs link carries `aria-current`.

## Not reached (and why)

- Brought-forward take-on badge — no take-on fee note on the stack (needs the Phase 93 take-on + sign-off flow).
- Reminder cards / AI review links on Debtors — 0 pending `SEND_COLLECTION_REMINDER` gates.
- Empty Debtor book — needs every outstanding fee note settled.
- Fee run IN_PROGRESS status and the post-send "Billing Run Complete" view — not sending (no email by design).
- Fee-run item FAILED / Failure Reason text — both clients generated cleanly.

## Files

- **Contact sheets:** `sheet-list-alice.png` (rows pre / post), `sheet-filters-alice.png` (7 filter clicks), `sheet-billing-runs-alice.png` (pre / preview / post), `sheet-run-detail-alice.png` (preview / post), `sheet-wizard-alice.png` (5 steps + validation, sheet, confirm), `sheet-collections-alice.png`, `sheet-unbilled-alice.png` (with data / empty period / post), `sheet-carol-cr-invoicing.png`, `sheet-carol.png` (7 routes each), `sheet-module-off-alice.png`, `sheet-setup.png` (time entries, role create, role assignments). Columns 1024 light | 1024 dark | 1440 light | 1440 dark, full page.
- **Crops:** `crop-defect-{list-table-1024,tiles-overdue-1440,debtors-table-1024,run-detail-header-1440,runs-list-row-1440,run-items-1440}.png`. Crops ran after the spec run (23 fee notes then).
- **JSON** (single line): `discover-{pre,post}.json`, `measurements.json` (`runs`, `at1024`, `at1440`, `tileContainer`, `feeRun`, `moduleOff`, `time`, `customRoles`, `crops`), `sel-compat.json`, `console.json`, `specs-baseline.json`.
- **Scripts** (as executed, in this order): `00-discover pre` → `02-time` → `01-capture alice pre` → `03-fee-run` (created the run, stopped at step 2) → `01b-capture alice preview` → `03b-fee-run-resume` → `01b-capture alice post` → `02-roles create` → `02-roles assign "666A OBS Invoicing"` → `01b-capture carol-cr-invoicing post` → `02-roles assign Member` → `01b-capture carol post` → `04-module-off` → spec run → `08-defect-crops` → `00-discover post` → `05-assemble`. Shared: `lib.cjs`, `probes.cjs`. All take `PHASE=before|after|after-fix`.
