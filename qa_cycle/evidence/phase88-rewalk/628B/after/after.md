# 628B AFTER evidence — e2e mock-auth stack :3001 rebuilt from worktree-epic-628B @ 35178f841, 2026-10-28 ~23:30 SAST

Frontend image rebuilt (`e2e-rebuild.sh frontend`, exit 0, e2e-frontend healthy). Backend container untouched (main code). Browser: `mcp__playwright` (the plugin Playwright profile was locked by another process).

| # | Check | Gap | Route | Viewport | BEFORE | AFTER | Verdict | Screenshot |
|---|---|---|---|---|---|---|---|---|
| O1a | Shell overflow | G-D15 | /settings/documents (+ ?tab=templates) | 1024 | 1471 | **1072** (toolbar row `Upload Word Template`/`New Template` spills 48 px; New Template button clipped). Bell visible (right 1000). Tables scroll in own box | **FAIL** (improved) | 01, 02 |
| O1b | Shell overflow | G-D15 | /settings/documents (+ templates) | 1440 | 1471, Status/Actions + bell clipped | 1440 = 1440; bell visible; some tables (CLOSURE, Cover Letter) need in-box horizontal scroll to reach Status/Actions | PASS (page) / note | 08, 09 |
| O1c | Shell overflow | G-D15 | /settings/documents | 1920 | 1920 | 1920 | PASS | 15, 16 |
| O1d | Shell overflow | — | /settings/firm | 1024/1440/1920 | (not measured) | = viewport | PASS | 03, 10, 17 |
| O1e | Shell overflow | 615C | /matters | 1024/1440/1920 | = viewport | = viewport (page-root grid wrapper retired) | PASS | 04, 11, 18 |
| O1f | Shell overflow | 615D | /clients | 1024/1440/1920 | = viewport | = viewport | PASS | 05, 12, 19 |
| O1g | Shell overflow | G88-M4 | /legal/disbursements | 1024/1440/1920 | 1653 / 1653 / 1920 | **1701 / 1701** / 1920 — window really scrolls 261 px right into empty space | **FAIL** | 06, 13, 20, diag-disbursements-scrolledX-1440 |
| O1h | Shell overflow | G88-M4 | /matters/ec2f3c51…?tab=disbursements | 1024/1440/1920 | 1487 / 1487 / 1920 | **1536 / 1536** / 1920 | **FAIL** | 07, 14, 21 |
| O1i | View-tab strip | R-7 | /matters, /clients | 768 | — | page 768 = 768; strip scrolls in its own box (cw 480 / sw 583, 587) | PASS | 22-matters, 22-clients |
| O1j | Settings strip w/o @container | R-7 | /settings/firm | 1024 / 768 | (616A: 1250 @1024 without @container) | 1024 = 1024; @768 strip scrolls in own box (cw 224 / sw 723) but page 863 from content (ZAR select, "Upload Document" button row) in 224 px column | strip PASS; page note | 03, 22-settingsFirm |
| O2 | Diary week title | G-D20 | /diary?view=week (26 Oct–1 Nov) | 1440 | "G…" (1 line, clientWidth 24) | time `09:30` on its own line above; title `line-clamp-2` → "Gauteng / Division…" (h 40 = 2×20 px); `title=` attr on link = full "Gauteng Division of the High Court, Johannesburg" (native tooltip; not capturable in screenshot); secondary line kept | PASS | 30, 31, 32 |
| O3a | Sidebar org name | G-A3 | desktop | 1440 | rgb(27,58,75) inline + opacity .8 on slate-950 ≈1.6:1 | class `truncate text-xs font-medium text-white/80`, colour white @0.8, opacity 1, no inline style; bg ≈ slate-950 (lab 2.2) → high contrast; 3×16 px `rounded-full` bar `background-color: var(--brand-color)` = rgb(27,58,75), aria-hidden | PASS | 33 |
| O3b | Sidebar org name | G-A3 | mobile sheet | 390 | text-white/60 | `truncate text-sm text-white/80` + same 3×16 brand bar | PASS | 34 |
| O4a | Settings label | G-D17 | /settings/fees, ?tab=tariffs | 1440 | tab "Tariffs" | tab "Rates" (id tariffs, selected on deep link); rail "Rates→?tab=tariffs"; h1 "Rates & Currency" | PASS | 40 |
| O4b | Settings label | G-D18 | /settings/connections, ?tab=xero | 1440 | tab "Xero" | tab "Integrations" (id xero, selected on deep link); rail "Integrations→?tab=xero"; h1 "Integrations" | PASS | 41 |
| O5a | h1 | D11 | /invoices/billing-runs | 1440 | h1 "Fee notes" | h1 "Fee runs"; sub-nav "Fee notes / Fee runs"; empty state "No fee runs yet" | PASS | 50 |
| O5b | h1 | D11 | /invoices/billing-runs/new | 1440 | — | h1 "New fee run" (h2 "Configure Billing Run" kept per R-6) | PASS | 51 |
| O5c | h1 | D11 | fee-run detail | — | — | no billing run exists in DB | BLOCKED (no data) | — |
| O5d | h1 | D11 | /invoices/collections | 1440 | h1 "Collections" | h1 "Debtors"; settings tab still "Collections" (h1 "Collections") | PASS | 52, 54 |
| O6a | Crumb | 628.4 | matter detail | 1440 | "… › Matters › Matter" | "… › Matters › Khumalo Property Holdings — Erf 1234 Sandton Transfer mvsk8fppefc6" (truncates at 1024, page 1024) | PASS | 60, 86 |
| O6b | Crumb | 628.4 | client detail | 1440 | "… › Clients › Client" | "… › Clients › Acme Corp" | PASS | 61 |
| O6c | Crumb | 628.4 | fee note INV-0001 (SENT) | 1440 | "… › Fee notes › Fee note" | "… › Fee notes › INV-0001" | PASS | 62 |
| O6c' | Crumb | 628.4 | draft fee note | — | — | no draft fee note in DB | BLOCKED (no data) | — |
| O6d | Crumb | 628.4 | /reports/invoice-aging | 1440 | — | "… › Reports › Invoice Aging Report" | PASS | 63 |
| O6e | Crumb | 628.4 | disbursement detail | 1440 | — | "… › Legal › Disbursements › Sheriff fee — service of process" | PASS | 64, 65 |
| O6f | Crumb | 628.4 | /invoices/billing-runs | 1440 | "… › billing-runs" | "… › Fee notes › Fee runs" | PASS | 50 |
| O6g | Crumb | 628.4 | /invoices/collections | 1440 | "… › Collections" | "… › Fee notes › Debtors" | PASS | 52 |
| O6h | Crumb | 628.4 | /compliance/requests | 1440 | "… › Compliance › requests" | "… › FICA & compliance › Data requests" | PASS | 55 |
| O6i | Crumb | 628.4 | /matters/new | 1440 | "… › Matters › new" | "… › Matters › New" | PASS | 56 |
| O6j | Stale label | R-2 | matter A → list → matter B (client-side) and A → B via ⌘K Recent | 1440 | — | MutationObserver sequence: A-name → "Matters" → "Matter" (~300 ms) → B-name; A→B via palette: "Matter" → B-name; A's name never shown on B | PASS | 70 |
| O6k | ⌘K Recent | R-3 | after client-side visits to INV-0001, invoice-aging report, disbursement | 1440 | — | Recent = [Acme Corp, Ndlovu Property Dispute 7mid, Khumalo …] only — no fee-note/report/disbursement | PASS | 71, 72 |
| O7a | Client badge | G-B1 | Acme Corp (ACTIVE/ACTIVE) | 1440 | "Active · Active" | "Active" (lifecycle only) | PASS | 80-acme |
| O7b | Client badge | G-B1 | Apex Holdings 5vui (ACTIVE/PROSPECT) | 1440 | "Active · Prospect" | "Prospect" | PASS | 80-apex5vui |
| O7c | Client badge | G-B1 | throwaway "Observer Throwaway 628B" created + archived via UI (DB ARCHIVED/OFFBOARDED) | 1440 | BLOCKED (no data) | "Archived" + "Offboarded" (record badge reappears) | PASS | 81 |
| O8a | Held | G-A7 | /settings/matters | 1440 | 6 × "Pack" | 6 × "Pack", 0 "SEEDER" | PASS (held) | 85 |
| O8b | Held | G-D16 | /settings/connections?tab=xero | 1440 | no PRO | no "PRO"/"Upgrade to Pro" | PASS (held) | 41 |
| O9a | Portal | 628.4 | /profile (prod build of worktree portal on :3002 → :8081) | 1440 | dt "Customer" | dts [Name, Email, Role, **Client**] | PASS | 90 |
| O9b | Portal | 628.4 | /home, /matters, /invoices, /matters/<id>, /invoices/<id> | 1440 | 0 legacy words | 0 legacy words in text/aria/title/placeholder/alt | PASS | 91–95 |
| O10 | Console | — | all :3001 + :3002 pages above | — | — | 0 errors / 0 warnings captured (page.on('console') + browser_console_messages) | PASS | — |

## FAIL diagnosis

**O1g/O1h (G88-M4 NOT closed).** `min-w-0` works — `<main>` is 1200 px at 1440, and the disbursement table scrolls inside its `overflow-x-auto` box. The remaining page overflow comes from the row "Actions" dropdown triggers' `<span class="sr-only">Actions</span>`: `sr-only` is `position:absolute`, and the nearest positioned ancestor is `<body>`, so these spans are not clipped by the non-positioned scroll wrapper and their static position (right = 1701 / 1536) extends `documentElement.scrollWidth`. The window genuinely scrolls 261 px right (diag screenshot). Culprit: `frontend/components/legal/disbursement-list-view.tsx:125` `<div className="overflow-x-auto" data-testid="disbursement-list">` (bespoke wrapper, lacks `relative`; shadcn `<Table>`'s wrapper is `relative w-full overflow-x-auto` for exactly this reason). Fix = add `relative` there. Same component serves the matter disbursements tab.

**O1a (G-D15 @1024 not fully closed).** Residual 48 px from `settings/documents/_tabs/templates-content.tsx:74/92` — `flex items-center justify-between` toolbar with a non-wrapping `flex gap-2` button pair (format select + help + Upload Word Template + New Template) wider than the 480 px content column. Fix = `flex-wrap` (and/or `min-w-0`) on that row. Fine at 1440/1920.

## Side observations (not 628B scope unless the orchestrator decides otherwise)
- 768 px: settings pages overflow from content (fixed-width selects / non-wrapping button rows) in the 224 px column beside the md+ settings rail (/settings/firm 863, /settings/documents 1056). Tab strip itself is fine. Not A/B'd vs main.
- 390 px dashboard: page 428 wide — header `ml-auto flex items-center gap-3` (org switcher + avatar + bell) pushes the bell ~36 px off-screen. Not A/B'd vs main.
- Diary compact title at 1440 has only ~68 px text width per day column, so "Gauteng / Division…" is all that shows in 2 lines; full text via native title tooltip.
- Portal sign-in: session cookie from the reproducer run was still valid (contact apex.holdings.mvsk8fppefc6@example.com) — no fresh magic-link exchange was needed.
- Data created: client "Observer Throwaway 628B" (ebfa5b5e-…, now ARCHIVED).
