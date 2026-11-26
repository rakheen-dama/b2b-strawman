# 663C OBS-before — K-05, K-07, K-08, K-24, K-26, K-62/K-63/K-64 (task 663.11)

Captured 2026-11-26 (last run 10:08 UTC) on the running E2E mock-auth stack (frontend :3001, backend :8081, mock IDP :8090).
That stack was built from the 663B head. Its frontend code equals `main` @ `3bc2110dc` (`c66dd0575..3bc2110dc` touches no `frontend/` file), so this is the state **before PR #1867**. No teardown, rebuild or reseed.
Personas: **Alice** (owner) and **Carol** (member). Themes: set through localStorage `kazi-theme`; the rail state through cookie `sidebar_state`, as in the 661B/662A/663A scripts.
Reduced motion is on and the timezone is Africa/Johannesburg.

**Deviation (as 663A):** captures come from Playwright *library* scripts (`scripts/*.cjs`, `chromium.launch()`), not Playwright MCP.
Login reuses `661B/walk/scripts/common.cjs` (through `require`, unedited): Alice signs in through the `/mock-login` UI, Carol uses the `mock-auth-token` cookie.
Raw tiles and `tiles.json` live in `.tmp/663C-before-raw/`, which git ignores.

## Data created (no SQL, no REST writes, no UI writes)
- **Nothing was created through the UI.** Menus, the palette and the task-status Select were opened and closed with Escape. The status stayed "Open" (`selectTriggerAfter` in `measurements.json`).
- **Side effect, recorded:** every read the server denies writes a `security.access_denied` audit event (severity NOTICE).
  - Sources: the 3 discovery GETs as Carol (10:00 UTC), and Carol's 5 HTTP-404 deep links on each of the 2 capture runs (10:06 and 10:08 UTC).
  - The Home "NOTICE" pill therefore reads **13** in the sheet. The first run read 8.
  - The after-capture will raise it again. Compare the WARNING/CRITICAL pills and the rows, not NOTICE.

## Discovery (read-only GETs with each persona's own token)
- `00-discover.cjs`:
  - Alice's capabilities (owner, incl. TEAM_OVERSIGHT, FINANCIAL_VISIBILITY, CUSTOMER_MANAGEMENT).
  - Carol's capabilities: AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST. Her `/api/invoices`, `/api/report-definitions` and `/api/audit-events` calls → 403.
  - Fee note INV-0005 `fbf447d0-…` (SENT).
  - Report slugs; `invoice-aging` was used.
  - 1 WARNING/CRITICAL event in the last 7 days.
- `01-discover-task.cjs`: the 663A task id → **404**. That task is not on this stack, and the script is kept as executed.
- `02-discover-tasks.cjs`: Alice has 83 unassigned tasks. The K-24 Select uses the first task of matter "Team spec mwxbfat81651" (`361f4148-…`, 9 tasks), opened from the matter's Tasks tab.
- The FICA review view param is `view=system:fica-review-due` (`frontend/lib/constants/system-views.ts:76`). Alice's control capture confirms it selects "FICA review due" (0 clients).

## Sheets
| File | Kink | What it shows |
|---|---|---|
| `sheet-k05-sidebar-density.png` | K-05 | Rows Alice / Carol, light: 1440×900 rail crop (default groups; all groups open), 390×844 Sheet (top; scrolled to bottom) |
| `sheet-k07-palette-focus.png` | K-07 | Rows Alice light / Alice dark / Carol light. Columns: header-button click (open, after Escape); header-button Enter (after Escape); sidebar-pill click (open, after Escape); ⌘K from the focused Notifications button (open, after Escape). The dark row skips 2 "open" tiles by design ("not captured"). `activeElement` is in each caption |
| `sheet-k08-sensitive-events.png` | K-08 | Alice's Home widget (element shot), 1440/1024 × light/dark, **normal read**: 1 CRITICAL row |
| `sheet-k24-focused-item-icons.png` | K-24 | DPR-2 crops at 1440, light / dark. Matters row menu ("Open matter" focused by keyboard, then ArrowDown to "Open client"). Task-status Select ("Open" focused, then ArrowDown to "In Progress") |
| `sheet-k26-breadcrumbs-1024.png` | K-26 | 1024×768 header strip, columns rail expanded / collapsed. Rows: Alice fee-note detail, Alice `/trust-accounting/transactions`, Carol the same trust page, Carol matter detail; each light and dark |
| `sheet-k62-member-clients.png` | K-62 | Carol on `/clients` and `/clients?view=system:fica-review-due`; Alice control on the review view |
| `sheet-k63-member-404s.png` | K-63 | Carol on `/invoices`, `/invoices/collections`, `/invoices/unbilled`, `/compliance` and `/invoices/fbf447d0-…` |
| `sheet-k64-member-report-runner.png` | K-64 | Carol on `/reports/invoice-aging` and `/reports`; Alice control on the runner |

## Measurements (`measurements.json`)

### K-05 — sidebar, light (`getBoundingClientRect`, viewport px, scrollTop 0)
- Every row measures the same: item 32 px, gap between items 4 px, separator block 9 px, group padding 4/4 px.
- Owner default groups: Today/Matters/Clients open; Fees/Trust/Firm closed.
- **Fold** = the bottom of the scrolling `[data-sidebar="content"]` (the footer sits below it).
- Group-header columns give the top and bottom of each header; `*` means not fully above the fold.

| Persona · viewport · state | Fold | Today | Matters | Clients | Fees | Trust | Firm | Items (first → last) | Overflow | Below fold |
|---|---|---|---|---|---|---|---|---|---|---|
| Alice 1440×900 default | 741 | 150–182 | 303–335 | 528–560 | 681–713 | 730–762 * | 779–811 * | 11: Home 182 → Data requests 632–664 | 74 | Trust, Firm |
| Alice 1440×900 all open | 741 | 150 | 303 | 528 | 681 | 942 * | 1239 * | 28: last Settings 1379–1411 | 674 | Trust, Firm (+ their items) |
| Alice 1024×768 default | 609 | 150 | 303 | 528 | 681 * | 730 * | 779 * | 11 | 206 | Fees, Trust, Firm |
| Alice 390×844 Sheet default | 685 | 150–182 | 303–335 | 528–560 | **681–713 *** | **730–762 *** | **779–811 *** | 11: last Data requests 632–664 | 130 | **Fees, Trust, Firm** |
| Alice 390 Sheet all open | 685 | 150 | 303 | 528 | 681 * | 942 * | 1239 * | 28: last Settings 1379–1411 | 730 | Fees, Trust, Firm |
| Carol 1440×900 default | 790 | 150 | 303 | — | 492–524 | 541–573 | 590–622 | 7: last Adverse parties 443–475 | 0 | none |
| Carol 1440×900 all open | 790 | 150 | 303 | — | 492 | 609 | 906 * | 18: last Settings 974–1006 | 220 | Firm |
| Carol 390 Sheet default | 734 | 150 | 303 | — | 492 | 541 | 590 | 7 | 0 | none |
| Carol 390 Sheet all open | 734 | 150 | 303 | — | 492 | 609 | 906 * | 18 | 276 | Firm |

- 390 owner Sheet: the Fees header top sits at 681, 4 px above the 685 fold, so it is cut. Trust and Firm need 77/126 px of scrolling.
- The register's walk measured 713/762/811 against 685. This stack draws the same rows 32 px higher, but the defect is unchanged: all three headers sit below the fold.
- 1440 owner default: Trust and Firm are below the fold, as the register says.

### K-07 — palette focus (1440, `document.activeElement`)
**18/18 trials reproduce** (Alice light, Alice dark, Carol light × 6 triggers). The palette opens with focus on the cmdk `<input>`, and after Escape focus is on **`<body>`**:

| Trigger | Before open | After Escape |
|---|---|---|
| header button, mouse click | body | body |
| header button, focus + Enter | `button[aria-label="Search, Command K"]` (header) | body |
| sidebar pill, mouse click | body | body |
| sidebar pill, focus + Enter | `button[aria-label="Search, Command K"][data-sidebar=menu-button]` | body |
| ⌘K (Meta+k) from the focused Notifications button | `button[aria-label="Notifications"]` | body |
| ⌘K from body | body | body |

### K-08 — sensitive events (Alice, normal read)
- The widget is present (audience `owner`), 204 px tall, 1128 px wide at 1440 and 712 px at 1024.
- Pills: NOTICE 13, WARNING 0, CRITICAL 1. Rows: 1, "Matter Closure Override Used". No empty state and no error line.
- Carol (audience `feeEarner`) has no widget, as expected (`plan.sensitive` needs TEAM_OVERSIGHT).

**The failing read could not be observed on this stack. That observation is component-test-only (DA-11).**
- The read is server-side: `dashboard/page.tsx:135-145` is `listAuditEvents` from the Next server to `GET /api/audit-events`, so `page.route()` never sees it.
- Nothing in the request is user-controlled: no search params and fixed `from`/`to`/`size`.
- The failure cannot be caused by permissions alone:
  - The widget's gate (`plan.sensitive = firmView && has("TEAM_OVERSIGHT")`) and the endpoint's gate (`@RequiresCapability("TEAM_OVERSIGHT")`, `AuditEventController.java:45-46`) are the same capability.
  - A role without TEAM_OVERSIGHT removes the widget rather than failing its read.
  - The facets read (`:126`) shares the same gate.
- The two remaining routes are both excluded by this task's rules:
  - Stopping the backend or the network to the backend means modifying a container. It would also fail the capabilities read first, which is a different page path.
  - Data-dependent failures need SQL.
- The vitest null-branch test is the evidence for the failing-read branch. The after-capture can only show the normal-read branch again.

### K-24 — keyboard-focused item icons (Alice, 1440, computed colours composited, WCAG 2.x)
| Theme | Item | Focus fill | Label | Icon(s) |
|---|---|---|---|---|
| light | menu "Open matter" / "Open client" | `#00776a` | `#ffffff` 5.46:1 | `#4d545f` **1.40:1** (ExternalLink, UserRound) |
| light | select "Open" / "In Progress" | `#00776a` | `#ffffff` 5.46:1 | `#4d545f` **1.40:1** (Circle / LoaderCircle; the "Open" check indicator also 1.40:1) |
| dark | menu, both items | `#00c2b1` | `#ffffff` **2.25:1** | `#989faa` **1.19:1** |
| dark | select, both items | `#00c2b1` | `#ffffff` **2.25:1** | `#989faa` **1.19:1** |

- Firm light reproduces the register exactly: 1.40:1.
- **Observed beyond the register (dark).** The focused item's white *label* is 2.25:1 on the dark `--accent` `#00c2b1`, and the icon is 1.19:1. The register's K-24 row covers icons only ("light"), so K-24's fix (icons to `text-accent-foreground`) will bring dark icons to the label's 2.25:1, not to 3:1.

### K-26 — breadcrumbs at 1024×768 (`nav[aria-label=Breadcrumb]` height; org crumb width × height)
| Page · persona | Expanded light | Expanded dark | Collapsed light | Collapsed dark |
|---|---|---|---|---|
| Fee-note detail · Alice | **40 px**, org 141×40 (2 lines; "Fee / notes" 2 lines; last "INV-00…") | 20 px, org 142×20 | 20 px | 20 px |
| `/trust-accounting/transactions` · Alice | **40**, org 116×40 ("Trust / accounting" 2 lines; last "Transac…") | **40**, org 119×40 | 20 | 20 |
| `/trust-accounting/transactions` · Carol | **40**, org 116×40 | **40**, org 119×40 | 20 | 20 |
| Matter detail (`96fa7717-…`) · Carol | **40**, org 94×40 | **40**, org 97×40 | 20 | 20 |

- The nav is 311–319 px wide with the rail expanded and 314–430 px collapsed. The header is 56 px in every case.
- The org crumb has no `title`.
- **The rail-expanded half of K-26 reproduces** (7/8, light and dark, owner and member). Alice's fee-note page in dark just fits, being 3 px wider than light.
- **The rail-collapsed half does not reproduce on these pages.** The register says 40 px in light. Here all 8 collapsed captures are 20 px, with the org crumb on one line at 142 px. The register's collapsed observation came from a different header width.

### K-62 / K-63 / K-64 — Carol (member), 1440 light (`#main-content`)
| URL | HTTP | h1 count | Main heading / text |
|---|---|---|---|
| `/clients` | 200 | 0 | h2 "You don't have access to Clients" (`PermissionDenied`) |
| `/clients?view=system:fica-review-due` | 200 | 0 | h2 "You don't have access to Clients" |
| `/invoices` | **404** | 0 | **crash panel**: h2 "Something went wrong", with Try again / Refresh page / Go back |
| `/invoices/collections` | **404** | 0 | crash panel |
| `/invoices/unbilled` | **404** | 0 | crash panel |
| `/compliance` | **404** | 0 | crash panel |
| `/invoices/fbf447d0-…` | **404** | 0 | crash panel |
| `/reports/invoice-aging` | 200 | 0 | **crash panel** + console "Minified React error #441" |
| `/reports` (control) | 200 | 0 | h2 "You don't have access to Reports" |
| Alice `/clients?view=system:fica-review-due` (control) | 200 | 1 | h1 "Clients", "FICA review due" view, 0 clients |
| Alice `/reports/invoice-aging` (control) | 200 | 1 | h1 "Fee Note Aging Report" |

K-62, K-63 (5/5) and K-64 all reproduce as registered.

## Console (`console.json`)
- 66 captures, **0 hydration warnings**, 0 warnings and 6 errors.
- The errors all come from Carol's pages:
  - 5 × "Failed to load resource: … 404 (Not Found)" on the five K-63 URLs;
  - 1 × "Minified React error #441" on `/reports/invoice-aging` (K-64).
- Every Alice page and every K-05/K-07/K-08/K-24/K-26 page is clean.

## Scripts
Run them from this directory with `NODE_OPTIONS=""`, in this order:
1. `00-discover.cjs` (read-only)
2. `01-discover-task.cjs` (read-only; got the 404 noted above)
3. `02-discover-tasks.cjs` (read-only)
4. `03-capture.cjs`
5. `04-assemble.cjs`

`03-capture.cjs` ran twice. The second run added a `scrollTop = 0` reset before the rail's "all open" measurement, because expanding a zone scrolls the rail. Every file here comes from that second run.
