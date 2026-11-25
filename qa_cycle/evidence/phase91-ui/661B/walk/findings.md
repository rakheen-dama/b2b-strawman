# 661B — whole-app light walk (task 661.13), head `d62c5dc19`

| | |
|---|---|
| Head | `phase91/61B-light-scheme` @ **`d62c5dc19`** (PR #1863), worktree `worktree-epic-661B` |
| Stack | e2e mock-auth `:3001` / `:8081`. Only the frontend was rebuilt from this head (`bash compose/scripts/e2e-rebuild.sh frontend`, data kept). The served CSS contains `--button-accent-foreground:var(--color-white)` (3 matches). Data: legal-lifecycle + legal-depth-ii + navigation suites |
| Persona / viewport | Alice (owner), logged in through `/mock-login`; 1440×900, **light** (`kazi-theme=light`), rail expanded, `reducedMotion: reduce`, Africa/Johannesburg |
| Method | `scripts/walk-light.cjs`, a Playwright **library** script (the Playwright MCP profile belongs to the user's live session, memory `env_playwright_mcp_profile_lock_obs`). Read-only: pages are opened, nothing is clicked or saved. Ids come from the API or from links on the parent page. Per page: `load` + `#main-content h1` + 2 s settle, full-page screenshot clipped at 3200 px (raw tiles kept out of the tree, in `/tmp/661b-walk-raw`), the light audit, console. Helpers: `scripts/common.cjs`, `scripts/audit.cjs` |
| Data files | `walk.json` (one line: ids, skipped routes, per-page audit), `console.json` |

## How "the scheme made it illegible" was tested (BLOCKS-661B bar)

`scripts/audit.cjs` measures every visible text owner with its effective WCAG 2.x ratio against its alpha-blended background (Phase 90 `pageAudit` method). It also measures icon-only controls (3:1, non-text) and form fields whose border and fill both disappear. Each page is measured **twice, on the same DOM nodes, in one synchronous pass**:

- **A, as rendered:** the firm light scheme is on.
- **B, counterfactual:** `data-app-shell` is removed from the shell wrapper. `:root:not(.dark):has([data-app-shell="firm"])` and the inset rules then stop matching, so the page computes today's pre-scheme light tokens: dark rail, `#f4f6f8` canvas, teal-600 accent, slate-950 primary. The attribute is put back right away, and transitions are disabled during the measurement.

Proof that the switch worked: `firmLightA` is true and `firmLightB` is false on all 82 pages (`walk.json`). A finding is **scheme-attributable** when it is below its floor in A but at or above it in B. B is never screenshotted.

**Result: 0 scheme-attributable items on 82 pages.** That holds for text (`schemeTextCount`), for text the scheme pushed below 3:1 (`schemeIllegibleCount`), for icon-only controls (`schemeIconCount`) and for faint fields (`schemeFaintCount`). Of the text owners below their floor in either state, none measured lower in A than in B. Over all pages, 310 text owners are below their floor with the scheme on, against **441** in the pre-scheme counterfactual. The new canvas (`#fdfeff`) lifts many grey-on-canvas pairs; for example slate-400 goes from 2.46 to 2.64. The rail text was measured on every page (`inRail`) and has no failing item. Visually, all 14 contact sheets were opened and read: no page has text or a control that is unreadable or invisible against the new canvas, rail or accent.

**`BLOCKS-661B` rows: none.**

## Coverage

- **Routes:** `frontend/app/(app)/org/[slug]/**/page.tsx` holds **58** route files.
  - **50** were loaded directly: 38 app routes, `/settings` and the 11 settings sub-pages.
  - The **5** family hosts (`/settings/{firm,matters,documents,fees,connections}`) were covered through their **32** `?tab=` panels.
  - **3** were skipped (below), so **55 / 58** route files were rendered.
- **Settings tabs: 32 / 32**, parsed from `frontend/lib/settings-nav.ts`. Every one returned 200 with its own h1, and its own tab was selected.
- **Settings sub-pages: 11 / 11** were visited. `/settings/matters/checklists/[id]/edit` redirects to the read-only detail view for a Platform checklist (W-9), so the edit form itself was not observed.
- **82 page loads in all.** Every one returned HTTP 200 with `dark=false`, exactly one `main#main-content` and no error text. 81 have exactly one h1 in `#main-content` (W-7 is the exception).

| Skipped route | Why |
|---|---|
| `/compliance/requests/[id]` | No data subject request on the stack (`/api/data-requests` → 0; the list page has no row link). Creating one would be a write |
| `/invoices/billing-runs/[id]` | No fee run on the stack (`/api/billing-runs` → 0) |
| `/trust-accounting/reconciliation/lists/[listId]` | No reconciliation on the stack ("0 reconciliations found"), so no locked monthly list |

## Console

The sink probe was captured (`probeCaptured: true`), so a zero here is a real zero.

- **0 hydration warnings, 0 page errors.**
- 1 error: #42 `settings/firm?tab=audit-trail`, "Failed to load resource: 404".
- 2 warnings: #75 and #76, the template editor, `[tiptap warn]: Duplicate extension names found: ['link', 'underline']`.
- No other page logged an error or a warning (W-10).

## Findings: seed intake for 662A (owner: 662A register)

Every row except W-11 is **pre-existing**: it measures the same with the scheme off (B), or worse, so none of them is caused or worsened by 661B. W-11 is stale imagery, not a contrast item. Ratios are measured (`walk.json` → `audit.lowText` / `lowIcons` / `faint`), WCAG 2.x, unrounded inputs.

| Id | Sev | What | Where (evidence) | Measured (scheme on / pre-scheme) |
|---|---|---|---|---|
| W-1 | LOW | The header notification badge count is white on a hard-coded `bg-teal-600`. It does not use the accent token, so in firm light it stays teal-600 while every accent fill is now teal-700 | `frontend/components/notifications/notification-bell.tsx:69`. On every page (81 of 82; the badge renders in the header); visible in every tile | `#ffffff` on `#00a38f` **3.16** / 3.16 (floor 4.5) |
| W-2 | LOW | Hard-coded `text-teal-600` links and CTAs on the canvas or a card, e.g. "Run report →" ×13 on `/reports` and "Run Report →" ×9 on `/trust-accounting/reports`; client/matter/fee-note entity links in the audit log; "Set up", "View checklist", trust-tx link, "View Execution Log →" | #26, #37 (`walk-05.png`, `walk-07.png`), #42, #21, #32, #71 | `#00a38f` on `#fdfeff` **3.13** / 3.13 (2.92 where it sat on the old canvas) |
| W-3 | LOW | `text-slate-400` hints and empty-state text: "Nothing in the diary today", "0/500 characters", "PNG, JPG, or SVG. Max 2 MB.", "—" cells, "No chase history", "/ 8h", "No content available"; a `text-slate-300` separator | #7, #14, #23, #39–40, #50, #56, #75 and others | `#989faa` on `#fdfeff` **2.64** / 2.46; on white 2.67 / 2.67; separator `#cbd0d7` 1.54 / 1.43 |
| W-4 | LOW | `text-green-600` status words and amounts ("Active", "Yes", "Setup complete", "R 250 000,00", margin %) | #3, #28, #32, #52, #70 | `#00a63e` **3.19–3.22** / same |
| W-5 | LOW | Icon-only controls below 3:1 (non-text, WCAG 1.4.11): the `text-slate-400` help "?" popover triggers ("Help: Saved views", "Help: Fee note lifecycle" … on about 16 pages), the "Expand clause" chevrons, and a Select chevron | #2, #10, #11, #20, #23, #28, #29, #39, #40, #47, #50, #51, #56, #57, #60, #66, #68, #69, #75 | **2.64** / 2.46 (on white 2.67; on `#f8f9fb` 2.53) |
| W-6 | LOW | Input, select and textarea borders measure ≈ 1.3:1 against the surface around them, with no fill contrast (the Phase 90 "faint field" heuristic; WCAG 1.4.11 asks 3:1 for a boundary that is needed to identify the control). This is today's shadcn `border-input` | 106 fields on about 40 pages, e.g. #15, #17, #22, #38, #42, #55, #76, #79, #81, #82 | border **1.27–1.30** / 1.19–1.30, fill 1.00 / 1.00–1.08 (the scheme slightly improves it on the canvas) |
| W-7 | LOW | **Two** level-1 headings in `#main-content` on the template editor. The Tiptap document's `{org.name}` heading renders as an `h1` inside the page. This breaks the SEL-COMPAT one-h1 invariant | #75 `/settings/documents/templates/[id]/edit` (`walk-13.png`) | h1 = ["Engagement Letter — Litigation", "{org.name}"] |
| W-8 | LOW | The matter **not-found / not-on-your-team** branch ("This matter isn't on your list") has **no** h1; its title is an `h2`. The one-h1 invariant also covers permission branches | `frontend/app/(app)/org/[slug]/matters/[id]/not-found.tsx:26`; observed as Carol in `../../scheme-after/personas.png` (tile "not-on-team") | `h1` count 0 |
| W-9 | INFO | `/settings/matters/checklists/[id]/edit` for a Platform checklist redirects to the read-only detail page ("This is a platform template and cannot be edited"). All 5 checklists on the stack are Platform, so the edit form was not observed | #78 (`walk-13.png`) | url `/settings/matters/checklists/<id>` |
| W-10 | LOW | Console: a 404 resource on the audit-trail tab, and a Tiptap duplicate-extension warning on the template editor and new-template page | #42, #75, #76 | 1 error, 2 warnings |
| W-11 | LOW | **Stale marketing product screenshots (made stale by 661B; docs, not legibility).** Marketing `/` shows product screenshots with the **pre-scheme dark rail**. Marketing itself is outside the firm shell and pixel-unchanged, but the pictures no longer match the firm light app | `../../scheme-after/marketing.png` (full page: "One platform, no patchwork" section), `frontend/public/screenshots/*.png` | — (image content) |

Already on the register and seen again here (not re-filed): the breadcrumb wraps at 1024 (`../found.md`), F-661A-2 (the search pill reads as active) and the focused-menu-item icons (`../found.md`). A focused menu, select or toggle is not visible in a static walk; those were measured in `../after/`.

## Per page (82)

Columns:

- **low:** text owners below their floor (4.5, or 3 for large text), scheme on / pre-scheme.
- **min:** the lowest text ratio on the page (scheme on).
- **scheme:** scheme-attributable text · icons · faint fields.
- **console:** errors / warnings / hydration warnings.

W-1 (the header badge) applies to every page and is not repeated per row.

| # | Route | HTTP | h1 | low | min | scheme | console | Result | Sheet |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `/ai/reviews` | 200 | AI Reviews | 1 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-01.png` |
| 2 | `/clients` | 200 | Clients | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5, W-6) | `walk-01.png` |
| 3 | `/clients/[id]` → `/clients/5d338835-af1e-49d4-8cf9-d46f3d03644f` | 200 | Acme Corp | 3 / 3 | 2.52 | 0·0·0 | 0/0/0 | OK | `walk-01.png` |
| 4 | `/compliance` | 200 | Compliance | 1 / 7 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-01.png` |
| 5 | `/compliance/requests` | 200 | Data Requests | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-01.png` |
| 6 | `/conflict-check` | 200 | Conflict Check | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-01.png` |
| 7 | `/dashboard` | 200 | Home | 2 / 2 | 2.64 | 0·0·0 | 0/0/0 | OK (seed: W-3) | `walk-02.png` |
| 8 | `/diary` | 200 | Diary | 2 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-02.png` |
| 9 | `/information-requests/[id]` → `/information-requests/558a68c8-f483-44da-b74b-0314d2361e77` | 200 | REQ-0001 | 11 / 11 | 1.54 | 0·0·0 | 0/0/0 | OK (seed: W-2, W-3) | `walk-02.png` |
| 10 | `/invoices` | 200 | Fee notes | 1 / 3 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5) | `walk-02.png` |
| 11 | `/invoices/[id]` → `/invoices/62c9aa43-8cd0-4acb-abf4-ccabe9d9cd63` | 200 | Draft fee note | 1 / 6 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5) | `walk-02.png` |
| 12 | `/invoices/billing-runs` | 200 | Fee runs | 1 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-02.png` |
| 13 | `/invoices/billing-runs/new` | 200 | New fee run | 6 / 10 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-3) | `walk-03.png` |
| 14 | `/invoices/collections` | 200 | Debtors | 8 / 13 | 2.64 | 0·0·0 | 0/0/0 | OK (seed: W-3) | `walk-03.png` |
| 15 | `/invoices/unbilled` | 200 | Unbilled work | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-03.png` |
| 16 | `/legal/adverse-parties` | 200 | Adverse Parties | 1 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-03.png` |
| 17 | `/legal/disbursements` | 200 | Disbursements | 1 / 14 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-03.png` |
| 18 | `/legal/disbursements/[id]` → `/legal/disbursements/f36d9227-0916-4376-bc09-c5b4ad0d0518` | 200 | Deeds Office Fees | 2 / 3 | 3.13 | 0·0·0 | 0/0/0 | OK (seed: W-2) | `walk-03.png` |
| 19 | `/legal/tariffs` | 200 | Tariff Schedules | 1 / 3 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-04.png` |
| 20 | `/matters` | 200 | Matters | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5, W-6) | `walk-04.png` |
| 21 | `/matters/[id]` → `/matters/682b8e3d-ec1d-4b01-aeb5-9d691686637a` | 200 | Estate Late Peter Moroka mwwjtue3603b | 5 / 5 | 3.13 | 0·0·0 | 0/0/0 | OK (seed: W-2) | `walk-04.png` |
| 22 | `/matters/new` | 200 | New Matter | 2 / 2 | 2.52 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-04.png` |
| 23 | `/my-work` | 200 | My Work | 6 / 6 | 2.34 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-5) | `walk-04.png` |
| 24 | `/my-work/timesheet` | 200 | Timesheet | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-04.png` |
| 25 | `/notifications` | 200 | Notifications | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-05.png` |
| 26 | `/reports` | 200 | Reports | 14 / 14 | 3.13 | 0·0·0 | 0/0/0 | OK (seed: W-2) | `walk-05.png` |
| 27 | `/reports/[reportSlug]` → `/reports/timesheet` | 200 | Timesheet Report | 5 / 5 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-05.png` |
| 28 | `/reports/profitability` | 200 | Profitability | 11 / 11 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-4, W-5, W-6) | `walk-05.png` |
| 29 | `/team` | 200 | Team | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5, W-6) | `walk-05.png` |
| 30 | `/trust-accounting` | 200 | Trust Accounting | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-05.png` |
| 31 | `/trust-accounting/client-ledgers` | 200 | Client Ledgers | 4 / 4 | 3.13 | 0·0·0 | 0/0/0 | OK (seed: W-2, W-6) | `walk-06.png` |
| 32 | `/trust-accounting/client-ledgers/[clientId]` → `/trust-accounting/client-ledgers/1ea2df84-f016-4380-9561-03e0c568a93a` | 200 | Moroka Family Trust mwwjtue3603b | 6 / 7 | 2.64 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-4, W-6) | `walk-06.png` |
| 33 | `/trust-accounting/investments` | 200 | Investments | 2 / 2 | 3.13 | 0·0·0 | 0/0/0 | OK (seed: W-2, W-6) | `walk-06.png` |
| 34 | `/trust-accounting/other-creditors` | 200 | Other trust creditors | 2 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-06.png` |
| 35 | `/trust-accounting/reconciliation` | 200 | Reconciliation | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-06.png` |
| 36 | `/trust-accounting/reconciliation/new` | 200 | New Reconciliation | 6 / 11 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-6) | `walk-06.png` |
| 37 | `/trust-accounting/reports` | 200 | Trust Reports | 10 / 10 | 3.13 | 0·0·0 | 0/0/0 | OK (seed: W-2) | `walk-07.png` |
| 38 | `/trust-accounting/transactions` | 200 | Transactions | 4 / 4 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-4, W-6) | `walk-07.png` |
| 39 | `/settings` → `/settings/firm` | 200 | General | 4 / 4 | 2.67 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-5, W-6) | `walk-07.png` |
| 40 | `/settings/firm?tab=general` | 200 | General | 4 / 4 | 2.67 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-5, W-6) | `walk-07.png` |
| 41 | `/settings/firm?tab=roles` | 200 | Roles & Permissions | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-07.png` |
| 42 | `/settings/firm?tab=audit-trail` | 200 | Audit log | 20 / 22 | 2.53 | 0·0·0 | 1/0/0 | OK (seed: W-2, W-3, W-6, W-10) | `walk-07.png` |
| 43 | `/settings/firm?tab=data-protection` | 200 | Data Protection | 2 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-2, W-6) | `walk-08.png` |
| 44 | `/settings/firm?tab=data-requests` | 200 | DSAR Requests | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-08.png` |
| 45 | `/settings/firm?tab=compliance` | 200 | Compliance | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-08.png` |
| 46 | `/settings/firm?tab=features` | 200 | Features | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-08.png` |
| 47 | `/settings/firm?tab=subscription` | 200 | Billing | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5) | `walk-08.png` |
| 48 | `/settings/matters?tab=templates` | 200 | Matter Templates | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-08.png` |
| 49 | `/settings/matters?tab=naming` | 200 | Matter Naming | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-09.png` |
| 50 | `/settings/matters?tab=custom-fields` | 200 | Custom Fields | 49 / 73 | 2.64 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-5) | `walk-09.png` |
| 51 | `/settings/matters?tab=tags` | 200 | Tags | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5) | `walk-09.png` |
| 52 | `/settings/matters?tab=checklists` | 200 | Checklists | 3 / 3 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-4) | `walk-09.png` |
| 53 | `/settings/matters?tab=request-templates` | 200 | Request Templates | 4 / 4 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-4) | `walk-09.png` |
| 54 | `/settings/matters?tab=request-settings` | 200 | Request Settings | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-09.png` |
| 55 | `/settings/matters?tab=take-on` | 200 | Take-on | 1 / 5 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-10.png` |
| 56 | `/settings/documents?tab=templates` | 200 | Templates | 3 / 31 | 2.67 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-5, W-6) | `walk-10.png` |
| 57 | `/settings/documents?tab=clauses` | 200 | Clause Library | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5, W-6) | `walk-10.png` |
| 58 | `/settings/documents?tab=acceptance` | 200 | Document Acceptance | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-10.png` |
| 59 | `/settings/documents?tab=email` | 200 | Email | 1 / 7 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-10.png` |
| 60 | `/settings/fees?tab=tariffs` | 200 | Rates & Currency | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-5) | `walk-10.png` |
| 61 | `/settings/fees?tab=tax` | 200 | Tax Settings | 4 / 4 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-4) | `walk-11.png` |
| 62 | `/settings/fees?tab=time-recording` | 200 | Time Tracking | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-11.png` |
| 63 | `/settings/fees?tab=fee-runs` | 200 | Batch Billing | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-11.png` |
| 64 | `/settings/fees?tab=collections` | 200 | Collections | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-11.png` |
| 65 | `/settings/fees?tab=trust-accounting` | 200 | Trust Accounting Settings | 1 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-11.png` |
| 66 | `/settings/connections?tab=xero` | 200 | Integrations | 2 / 2 | 2.15 | 0·0·0 | 0/0/0 | OK (seed: W-5, W-6) | `walk-11.png` |
| 67 | `/settings/connections?tab=mcp` | 200 | Claude / MCP Connector | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-12.png` |
| 68 | `/settings/connections?tab=ai` | 200 | Set Up AI | 4 / 4 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-2, W-5, W-6) | `walk-12.png` |
| 69 | `/settings/connections?tab=notifications` | 200 | Notification Preferences | 9 / 9 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-5) | `walk-12.png` |
| 70 | `/settings/connections?tab=packs` | 200 | Packs | 6 / 6 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-12.png` |
| 71 | `/settings/connections?tab=automations` | 200 | Automations | 3 / 13 | 3.13 | 0·0·0 | 0/0/0 | OK (seed: W-2) | `walk-12.png` |
| 72 | `/settings/connections/ai/history` | 200 | Execution History | 1 / 2 | 3.16 | 0·0·0 | 0/0/0 | OK | `walk-12.png` |
| 73 | `/settings/connections/automations/executions` | 200 | Execution Log | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-13.png` |
| 74 | `/settings/connections/xero/sync-log` | 200 | Sync Log | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-13.png` |
| 75 | `/settings/documents/templates/[id]/edit` → `/settings/documents/templates/1840c27d-fe72-4d9e-82ff-237ce001d1a5/edit` | 200 | Engagement Letter — Litigation; {org.name} | 6 / 6 | 2.67 | 0·0·0 | 0/1/0 | OK (seed: W-3, W-5, W-7, W-10) | `walk-13.png` |
| 76 | `/settings/documents/templates/new` | 200 | New Template | 1 / 1 | 3.16 | 0·0·0 | 0/1/0 | OK (seed: W-6, W-10) | `walk-13.png` |
| 77 | `/settings/matters/checklists/[id]` → `/settings/matters/checklists/bf7ead55-5686-4e07-ab5d-2069ffa351be` | 200 | Generic Client Onboarding | 5 / 6 | 2.64 | 0·0·0 | 0/0/0 | OK (seed: W-3) | `walk-13.png` |
| 78 | `/settings/matters/checklists/[id]/edit` → `/settings/matters/checklists/bf7ead55-5686-4e07-ab5d-2069ffa351be` | 200 | Generic Client Onboarding | 5 / 6 | 2.64 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-9) | `walk-13.png` |
| 79 | `/settings/matters/checklists/new` | 200 | New Checklist Template | 2 / 2 | 2.67 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-6) | `walk-14.png` |
| 80 | `/settings/matters/request-templates/[id]` → `/settings/matters/request-templates/ad73652c-a2a2-48ef-841b-b250d565a39b` | 200 | Liquidation and Distribution Account Pack | 11 / 11 | 2.67 | 0·0·0 | 0/0/0 | OK (seed: W-3) | `walk-14.png` |
| 81 | `/settings/matters/request-templates/new` | 200 | New Request Template | 1 / 1 | 3.16 | 0·0·0 | 0/0/0 | OK (seed: W-6) | `walk-14.png` |
| 82 | `/settings/matters/templates/[id]` → `/settings/matters/templates/0af5b41a-9507-40ba-b78f-1ae2907ec68d` | 200 | Collections (Debt Recovery) | 11 / 17 | 2.64 | 0·0·0 | 0/0/0 | OK (seed: W-3, W-6) | `walk-14.png` |
