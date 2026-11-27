# 669A OBS-before — compliance, conflict check, legal registers, information requests (task 669.1)

## Run and environment

**When (2026-11-27):** inventory + seed 08:29Z (one run); capture 08:38Z (91 page loads + interactions), assembled 08:43Z. A dry run (`NO_WRITE=1`) preceded the committed capture; the probe and one crop selector were fixed in between, and the committed output is the second, full run.

**Stack:** the running e2e mock-auth stack, org `e2e-test-org`. Not rebuilt, restarted, reseeded or torn down.
- Ports: frontend :3001, backend :8081, mock IDP :8090.
- All `e2e-*` containers created **2026-11-27T08:10:29Z** (a fresh seed at that time). The stack then holds rows from the 668A gate's e2e suites (names suffixed `mwypg4ea8b48`, `mwypcflye91f`, `iesh`, created 08:11–08:13Z).
- Frontend image `docteams-e2e-frontend` **`sha256:8386cd31455a`**, built **2026-11-27T08:10:29Z** from `worktree-epic-668A/compose` (compose labels `project.working_dir`). 668A's last code commit is `7d0336046` (07:51:17Z); the image's client chunks contain `@min-[34rem]:table-cell` and no `@min-[40rem]` / `@min-[46rem]:table-cell` (= 7d0336046's task-table thresholds). `git diff --stat 7d0336046 ed22a7bfa -- frontend packages compose` is **empty**, so the frontend is main **`ed22a7bfa`**, this worktree's HEAD.
- Backend image `sha256:e7fd89e47741`, built 2026-11-25T16:31:26Z. The only later backend commit on main is `d8aaba16a` (17:41Z, take-on #1861: `takeon/*` files only) — no compliance / conflict-check / disbursement / tariff / information-request code.

**Browser:** Playwright library (Chromium from the main checkout's `frontend/node_modules`), not the MCP. Node 20.11.1 (nvm), DPR 1, reduced motion, TZ Africa/Johannesburg, rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`. Personas log in through `/mock-login` (`#user-select`, "Sign In"): **Alice (owner)**, **Carol (member)** — Carol's capabilities: `AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST` (no `CUSTOMER_MANAGEMENT`, not admin).

**Committed:** 19 contact sheets + 8 crops (27 PNGs), 5 single-line JSON (`measurements`, `sel-compat`, `console`, `discover`, `seed`), 6 script files, this README = 39 files. Raw tiles in `/tmp/669A-before-raw/` (not committed).

**OBS-after** (no re-seed), from `before/scripts/`, unedited:
`PHASE=after node 01-capture.cjs && PHASE=after STACK="<frontend image + SHA>" node 02-assemble.cjs`
Every URL carries explicit ids (seed.json › `ids`); no URL depends on "today". **Each capture run writes one conflict check** (see §4), after all page loads, so in the replay every page load shows `History (9)` (before: `History (8)`) and the interactions `History (10)` (before: 9). Carol's own statements-empty matter is resolved from the API at start (`Bell Matter iesh`, first ACTIVE matter of hers by name with 0 statements).

## Data before the seed (`seed.json` › `dataBefore`)

- **Data requests: 0** (`GET /api/data-requests` → `[]`; Carol → 403).
- Conflict checks: 8 (2 CONFLICT_FOUND, 2 POTENTIAL_CONFLICT, 4 NO_CONFLICT, none resolved). Adverse parties: 3 (Pillay Holdings (Pty) Ltd / COMPANY, Road Accident Fund / GOVERNMENT, Thabo Mokoena / NATURAL_PERSON).
- Disbursements: 4, all incurred 2026-11-27: "Deeds office search fee" (trust-paid, trust tx `a3802913-…`, matter **CLOSED**), "Sheriff fee — re-service …" (office, ACTIVE matter `8f3dbe07-…`), "Sheriff fee — service of process" (WRITTEN_OFF, CLOSED matter), "Counsel's opinion …" (BILLED, CLOSED matter). `/api/matters` (ACTIVE list) has 12 of the 14 matters.
- Tariff schedules: 1 ("LSSA 2024/2025 High Court Party-and-Party", system, 19 items, `effectiveFrom 2024-04-01`, **no `code` field in the API response**).
- Information requests: REQ-0001 (SENT, Acme Corp / Website Redesign, `reminderIntervalDays: null`), REQ-0002 (SENT, Moroka Family Trust, 5).
- Clients 8 (5 ONBOARDING; Apex Holdings `73866953-…` has 0 checklists). Matters with 0 statements: 11 of 14.
- Enabled modules include `conflict_check`, `lssa_tariff`, `disbursements`, `information_requests`.

## Seed (`scripts/00-seed.cjs`; ids in `seed.json`) — all as Alice, no SQL

1. **Before the write**, the naturally empty `/compliance/requests` was probed and shot (Alice 1440 light + 1024 dark; `seed.json › preSeedEmpty`, tiles on `sheet-requests-alice.png`): h1 "Data Requests" (no count pill), `EmptyState` **variant `default`**, h2 "No data requests found" + "Create a new data subject request to get started.", **248 px tall** (1 128 / 720 wide), icon; doc h-overflow 0.
2. **The one seed write — real UI clicks:** `/compliance/requests` › "New Request" › client picker "Acme Corp" › Request Type "Access Request" › description "669A OBS seed — the client asked for a copy of the personal information the firm holds (POPIA s23)" › "Create Request" → **`6a41ee33-1124-402c-bf54-b4e0d0122109`**, RECEIVED, `requestedAt 2026-11-27T08:29:12Z` (10:29 SAST), deadline 2026-12-27. Why: without it the request table, the RECEIVED pill rows and `/compliance/requests/[id]` are unobservable. Nothing advanced (still RECEIVED).
3. **Capture-time write (every run):** one conflict check submitted through "Run Conflict Check" (Alice 1440 light), `checkedName "Pillay Holdings"`, `checkType NEW_MATTER` (`measurements.json › writes`). The result panel exists only after a submit.

Other writes: none. Dialogs (Resolve Conflict) were opened and Escaped; searches and filters are client state only.

## 1. `/compliance` (`sheet-compliance-alice.png`, `sheet-compliance-carol.png`)

- **Alice:** h1 **"Compliance"** (hand-rolled, `font-display text-3xl`, 30 px / 400), 1 level-1 heading, 1 `main#main-content`. Tabs **Overview\* · AI Audit** (Radix, `defaultValue="overview"`). **`?tab=` mechanics: none** — clicking AI Audit leaves the URL `/compliance`, and loading `/compliance?tab=ai-audit` still selects Overview. Overview h2s: Lifecycle Distribution · Onboarding Pipeline · Data Requests · Dormancy Check. AI Audit: h2 "AI Compliance Audit", "Run AI Audit" disabled (AI not configured), hand-rolled "No audits yet. Run an AI audit to assess your compliance posture." (134 px card).
- Tables: Onboarding Pipeline (5 rows) and open data requests (1 row) **710/712 at 1024 light, 1 126/1 128 at 1440 — no overflow** (onboarding columns Client · Duration · Progress at both widths; open-requests Client · Type · Deadline · Status). The data-request row prints raw enums **"ACCESS"** and **"RECEIVED"** (`DataRequestsSection.tsx:81`).
- Light contrast < 4.5: "29d remaining" #009966 **3.62**, "View All Requests" teal-600 **3.13**.
- **K-84 reproduced:** breadcrumb "FICA & compliance", active nav item "FICA & compliance", h1 "Compliance".
- **Carol (gate branch):** `notFound()` → **HTTP 404**, h1 "Page not found" (1 level-1 heading); breadcrumb still "FICA & compliance". The 404 is the only console error source (4 × "Failed to load resource: 404").

## 2. `/compliance/requests` (`sheet-requests-*.png`)

- **Alice:** h1 "Data Requests" + count pill "1", "New Request". Filter: pill **Links** All · Received · In Progress · Completed · Rejected → `?status=RECEIVED|IN_PROGRESS|COMPLETED|REJECTED`, "All" drops the param (Link navigation; selected pill = `bg-slate-900 text-white`; **no `aria-current`** on any pill). Rows per pill: All 1 · Received 1 · In Progress 0 · Completed 0 · Rejected 0.
- Table (Client · Type · Status · Deadline · Requested): **712/712 at 1024, 1 128/1 128 at 1440**, no overflow; row "Acme Corp | Access | Received | 27 Dec 2026 | 27 Nov 2026".
- **Empty states:** filtered → `EmptyState` **variant `default`**, h2 "No data requests found" + "No in progress data requests." / "No completed …" / "No rejected …", **248 px** (also at 1024 dark); unfiltered (pre-seed) the same component, "Create a new data subject request to get started." (§Seed 1). This is the guard's 1 allowlist line (`compliance/requests/page.tsx:101`, no `variant` prop).
- **Carol:** HTTP 200, h1 **"Compliance"** + "You do not have permission to view compliance data. Only admins and owners can access this page." (24 px at 1440, 48 px — two lines — at 1024).

## 3. `/compliance/requests/[id]` (`sheet-request-detail-*.png`, `crop-k82-*`, `crop-k68-*`)

- **Alice:** h1 **"Acme Corp"** 30 px **weight 700** (`font-display text-3xl font-bold`) — **K-82 reproduced**; badges Received · Access Request; "Requested: 27 Nov 2026, 10:29 · Deadline: 27 Dec 2026"; h2s DESCRIPTION · ACTIONS ("Start Processing") · STATUS TIMELINE ("Request Received 27 Nov 2026 · Processing Started · Awaiting Completion · Deadline: 27 Dec 2026"). No table; doc h-overflow 0 at every variant. The file has **19 `dark:` lines** (grep); dark tiles render cleanly (no light-on-dark text below 4.5 measured).
- **K-68 not reproduced on this data:** the request was created 10:29 SAST (08:29Z), outside the 00:00–01:59 SAST window, so header and timeline agree (27 Nov). Reproducing needs a request logged in that window (not possible through the UI at capture time); the code path is unchanged (`formatComplianceDate`, no `timeZone`).
- **Carol:** HTTP 200, h1 **"Data Request"** (30 px / 400) + "You do not have permission to view data requests. Only admins and owners can access this page."

## 4. `/conflict-check` (`sheet-conflict-check-*.png`, `crop-k60-*`)

- h1 "Conflict Check" (30 px / 400, hand-rolled) + subtitle; tabs **Run Check\* · History (8)** (client state — the URL never changes). Alice and Carol see the same page (module-gated only).
- **K-60 reproduced:** the form has no width limit — Name to Check / Check Type **1 128 px at 1440** (712 at 1024), ID / Registration / Client / Matter 556 px.
- **searchParams (prefill only; the page never writes the URL):** `?customerId=<Khumalo Property Holdings>&checkType=NEW_MATTER` → name "Khumalo Property Holdings mwypg4ea8b48", ID number **7605125009081** (from the client record), type New Matter, Client select = that client (Alice and Carol alike). `?checkedName=Thabo%20Mokoena&checkedIdNumber=8001015009087&checkType=PERIODIC_REVIEW` → all three prefilled. `?checkType=BOGUS` → ignored (New Client).
- **Run (the capture write):** `?checkedName=Pillay%20Holdings&checkType=NEW_MATTER` → "Run Conflict Check" → **URL unchanged**; result panel h3 "Potential Conflict", badge 4.52 (light) / 6.26 (dark), table Party · Match type · Score · Linked matter · Relationship = "Pillay Holdings (Pty) Ltd | Name Match | 57% | Ndlovu Property Dispute … | WITNESS", "Resolve Conflict". Result table 1 078/1 078 at 1440, 662/662 at 1024; no overflow. The checked-at line is **"11/27/2026, 10:41:35 AM"** (`toLocaleString()`, US order). Dark tiles were made by switching the `<html>` class on the same page (no second submit).
- **History:** table Date · Checked Name · Type · Result · Resolution · Actions, **1 128/1 128 at 1440, 720/720 at 1024 dark**, no overflow; Date column **"11/27/2026"** (`toLocaleDateString()`, US order — F-669A-B-1); type raw "NEW CLIENT"; "Resolve" link-buttons teal-600 **3.13** in light. Result filter "Conflict Found" → 2 rows (URL unchanged). Resolve dialog "Resolve Conflict" opens and closes on Escape. History empty state ("No conflict checks found.") not reachable on this data (8+ rows).

## 5. `/legal/adverse-parties` (`sheet-adverse-parties-*.png`)

- h1 "Adverse Parties" (hand-rolled) + subtitle; search "Search parties..." (320 px) + type select. `adverse-party-table`, 3 rows, columns Name · ID Number · Reg. Number · Type · Links · Actions: **712/712 at 1024, 1 128/1 128 at 1440**, no overflow. Alice and Carol identical.
- **Empty search** ("zzz-669A-no-match"): table removed (`adverse-party-table` 0), hand-rolled `<p>` "No adverse parties found." in an **86 px** block, no icon (Alice 1440 light / 1024 dark, Carol). "Pillay" → 1 row. URL never changes.

## 6. `/legal/disbursements` + `[id]` (`sheet-disbursements-*.png`, `sheet-disbursement-detail-*.png`, `crop-k66-k79-*`, `crop-k80-*`)

- List: h1 **"Disbursements"** + subtitle; `disbursements-list-page`; 4 × `disbursement-row-<id>`. Table **712/712 at 1024, 1 128/1 128 at 1440 — no overflow**: at 1024 the container query hides Matter / Category / Supplier / Billing (columns in view: Date, Description·Supplier, Amount, Approval, Actions).
- **K-66 reproduced:** Matter column "—" for **3 of 4** rows (Alice; the 3 are on CLOSED matters, absent from `/api/matters`); **Carol: 4 of 4 "—"** (her `/api/matters` has 4 matters, none of these). The column is hidden at 1024, so K-66 shows only at 1440.
- **K-79 reproduced:** list Date "2026-11-27" in `font-mono` (4/4); detail "Incurred 2026-11-27"; tariffs "Effective from 2024-04-01".
- Unbilled strip: Matter filter = "Ndlovu — transition repro" → `unbilled-disbursements-summary` "Unbilled Disbursements R 150,00 · VAT R 0,00", 1 row (URL unchanged). **Empty:** From date 2030-01-01 → hand-rolled "No disbursements found." (**86 px**, no icon, a `<div>`/`<p>`, not a heading); the only heading containing "Disbursements" stays the h1. Matter › Disbursements tab: h3 "Disbursements" (rows 1 / empty: "No unbilled disbursements" 38 px + "No disbursements found." 86 px).
- **Detail (`bf9dedfc-…`, trust-paid): K-80 reproduced** — h1 **"Deeds Office Fees"** (the category, 24 px) while the breadcrumb ends "Deeds office search fee"; Trust Transaction shows the raw UUID `a3802913-…` as a `font-mono` teal-600 link (**3.13**, K-37's disbursement link); "No receipt uploaded" 20 px. Office-paid detail: h1 "Sheriff Fees". **Carol** sees list and detail like Alice (HTTP 200).

## 7. `/legal/tariffs` (`sheet-tariffs-*.png`, `crop-k81-*`)

- h1 "Tariff Schedules" + subtitle; one schedule row (`schedule-row-<id>`), no `<table>` on the page. **K-81 reproduced:** an **empty Badge 22 px wide** after the schedule name (8/8 variants, both personas).
- Expanding the row: `tariff-item-search` appears (**0 at page load, 1 after expand**), 7 section headers, 19 items. Search "summons" → 2 items; **"zzz-669A-no-match" → "No tariff items found."** (hand-rolled, **125 px**, icon). The schedules-list empty ("No tariff schedules found.") is not reachable (1 system schedule). URL never changes. Carol identical.

## 8. `/information-requests/[id]` (`sheet-information-request-*.png`, `crop-k37-*`, `crop-k67-k85-*`)

- **Alice (admin branch):** h1 **"REQ-0001"** + Sent badge; client link "Acme Corp" + "| Website Redesign"; metadata panel Contact · Reminder Interval · Sent · Due · Progress; h2 "Items (1)". No table; no overflow.
- **K-67 reproduced:** "Reminder Interval **Every days**" (REQ-0001, `reminderIntervalDays null`); REQ-0002 reads "Every 5 days".
- **K-85 reproduced:** panel labels and the e-mail / "0/1 accepted" / "Waiting for client" **#6b7482 on #f8f9fb = 4.48** (light; dark 6.74).
- **K-37 reproduced:** client link "Acme Corp" teal-600 **#00a38f on #fdfeff = 3.13** (dark 10.74). The "|" separator is 1.54 (decorative).
- **Carol (non-admin branch):** HTTP 200, h1 **"Information Request"** + "You do not have permission to view information requests." (24 px).

## 9. Statements empty, checklist empty (`sheet-statements-checklist.png`, 1440 light)

- Matter Khumalo Property Holdings › Fees › Statements of account (`?tab=statements`): `statements-empty` "No statements have been generated for this matter yet." (**102 px**, text 4.48 on the slate-50 panel). Carol on that matter: "This matter isn't on your list" with **no h1 in `#main-content`** (0 level-1 headings); Carol on her own matter (Bell Matter iesh): the same `statements-empty` 102 px.
- Client Apex Holdings (ONBOARDING, 0 checklists) › `?tab=onboarding` (ChecklistInstancePanel): "No checklists yet." with icon — Alice (admin, plus "Manually Add Checklist") **160 px**, Carol (non-admin branch) **184 px**; text #6b7482 on white 4.68.

## 10. Shell and console

- Every capture (91 loads): `main#main-content` = 1 and `<main>` = 1; level-1 headings in main = 1 everywhere except Carol on a matter she is not on (0). Document h-overflow **0 on every page and variant**; no table overflows its container at 1024 or 1440.
- `console.json`: 4 errors, all "Failed to load resource: 404" on Carol's `/compliance` (the intended `notFound()`); **0 warnings, 0 page errors, 0 hydration** messages.

## 11. SEL-COMPAT before (`sel-compat.json`; 1440 light unless noted)

| Selector (spec:line) | Alice | Carol |
|---|---|---|
| h1 "Compliance" (day-90-review:351, compliance-overview:23) | 1 on `/compliance` | 0 on `/compliance` (404); 1 on `/compliance/requests` (gate h1) |
| h1 "Data Requests" (compliance-overview:47) | 1 (list, every pill) | 0 |
| h1 "Disbursements" (day-05:149) | 1 (1440 and 1024) | 1 |
| heading containing "Disbursements" (day-05:428) | list: h1 only, also with the empty list; matter tab: **h3 "Disbursements"** (rows and empty) | list: h1 only |
| `adverse-party-table` | 1 (0 on a no-match search) | 1 |
| `disbursement-row-*` | 4 (matter tab: 1) | 4 |
| `disbursements-list-page` | 1 | 1 |
| `statements-empty` (day-30-45:265) | 1 | 1 on her own matter; 0 on a matter she is not on |
| `tariff-item-search` | 0 at load, **1 after expanding the schedule** | same |
| `#main-content` level-1 headings | 1 on every page | 1 (0 on the not-on-matter page) |

Every testid per page is in `discover.json`.

## Findings (pre-existing, before)

- **F-669A-B-1** — Conflict-check dates use the browser's default locale: History Date "11/27/2026" (`conflict-check-history.tsx:160`, `toLocaleDateString()`), result "Checked … at 11/27/2026, 10:41:35 AM" (`conflict-check-result.tsx:93`, `toLocaleString()`) — every other page prints "27 Nov 2026". Record only (same class as K-79).
- **F-669A-B-2** — Compliance › Overview › Data Requests row prints raw enums "ACCESS" / "RECEIVED" (`DataRequestsSection.tsx:81`); the requests list prints "Access" / "Received".
- **F-669A-B-3** — `/compliance` tabs are not URL-addressable (`?tab=ai-audit` ignored); the data-request status pills carry no `aria-current`.
- **F-669A-B-4** — Light contrast below 4.5 not in the register: Compliance "29d remaining" 3.62, "View All Requests" 3.13, conflict History "Resolve" 3.13, matter-header client links 3.13, `statements-empty` / "No unbilled disbursements" text 4.48 on slate-50.
- Register kinks reproduced: **K-37** (3.13, info-request client link and the disbursement trust-tx link), **K-60** (form 1 128 px; hand-rolled headers on conflict check / adverse parties), **K-66** (3/4 "—" Alice, 4/4 Carol, 1440 only), **K-67** ("Every days"), **K-79** (list mono ISO, detail "Incurred 2026-11-27", tariffs "Effective from 2024-04-01"), **K-80** (h1 = category; raw mono UUID link), **K-81** (empty 22 px badge), **K-82** (weight 700), **K-84** (nav/breadcrumb "FICA & compliance" vs h1 "Compliance"), **K-85** (4.48). **K-68 not reproduced** (request created 10:29 SAST; needs a 00:00–01:59 SAST request).

## What OBS-after must replay

Run `01-capture.cjs` + `02-assemble.cjs` with `PHASE=after`, unedited, on this data — **do not re-seed**. Must stay identical: the 91 page loads (same URLs), Carol's gate outcomes (404 on `/compliance`; the "Compliance" / "Data Request" / "Information Request" h1 + permission text), prefill behaviour of the four conflict searchParams, the URL never changing on tab / filter / search / submit, row counts, the SEL-COMPAT table (h1 texts "Compliance" / "Data Requests" / "Disbursements"; no heading containing "Disbursements" in the empty list; `adverse-party-table`, `disbursement-row-<id>`, `disbursements-list-page`, `statements-empty`, `tariff-item-search`). Expected deltas: History count +1 per run (§Run), PageHeader on each page with the same h1 text, DataTable on the six tables (no overflow at 1024/1440), EmptyState variants on the hand-rolled empties (the requests EmptyState with an explicit variant), and the approved kinks (K-37, K-60, K-66, K-67, K-79, K-80, K-81, K-82, K-84, K-85).
