# 663C OBS-after — K-05, K-07, K-08, K-24, K-26, K-62/K-63/K-64 (+ KBD, SEL-COMPAT)

Captured 2026-11-26 (`03-capture.cjs` 10:36 UTC, `05-capture-extra.cjs` 10:38 UTC) on the running E2E mock-auth stack (frontend :3001, backend :8081, mock IDP :8090).
That stack was built from this worktree (`com.docker.compose.project.working_dir` = `worktree-epic-663C/compose`) at **HEAD `84f867eef`**, the head of PR #1867. Its data was left by the legal-lifecycle, legal-depth-ii, navigation and default e2e suites. No teardown, rebuild or reseed.
Personas: **Alice** (owner) and **Carol** (member). Themes: set through localStorage `kazi-theme`; the rail state through cookie `sidebar_state`, as in the before pass.
Reduced motion is on and the timezone is Africa/Johannesburg.

**Deviation (as before):** captures come from Playwright *library* scripts (`scripts/*.cjs`, `chromium.launch()`), not Playwright MCP.
Login reuses `661B/walk/scripts/common.cjs` (through `require`, unedited): Alice signs in through the `/mock-login` UI, Carol uses the `mock-auth-token` cookie.
Raw tiles, `tiles.json` and `tiles-extra.json` live in `.tmp/663C-after-raw/`, which git ignores.

## Like-for-like: what changed from `before/scripts/`
The before scripts are untouched. `after/scripts/` holds copies; each change is listed here and in the script's first line.

| After script | Source | Changes |
|---|---|---|
| `00-discover.cjs` | copy of before `00` | first comment line only |
| `01-discover-ids.cjs` | **new** (replaces before `01-discover-task.cjs`, which 404'd on the before stack too) | The stack was rebuilt, so the before ids are gone. Re-discovers INV-0005 (SENT) `83ca9f03-…` (before `fbf447d0-…`), the "Team spec" matter with 9 tasks `5e9c0144-…` "Team spec mwxeos7x3f94" (before `361f4148-…` "Team spec mwxbfat81651"), and a template id for K-26's long chain |
| `02-discover-tasks.cjs` | copy of before `02` | first comment line only (88 unassigned tasks; the before stack had 83) |
| `03-capture.cjs` | copy of before `03` | first line, RAW dir `.tmp/663C-after-raw`, `FEE_NOTE`, `TEAM_SPEC`, and the `stack` string. **Nothing else**: same viewports, trials, selectors and measurements. `diff` shows 6 changed lines |
| `04-assemble.cjs` | copy of before `04` | first line, RAW dir, "before" → "after" in titles, the K-63 title (no longer says "crash panel"), and 4 sheets for the `05` tiles appended at the end |
| `05-capture-extra.cjs` | **new**, after-only | the probes the before pass did not take (see each kink). Merges into `measurements.json` (key `extra`) and `console.json` (key `extra`), and writes `sel-compat.json` |

The report slug is unchanged (`invoice-aging`). The Carol matter for K-26 is again the first matter on her list: `9ce7439a-…` "Ndlovu v RAF mwxel6k525c4" (the before stack had `96fa7717-…`).

## Data created (no SQL, no REST writes, no UI writes)
- **Nothing was created.** Menus, the palette and the task-status Select were opened and closed with Escape. The task status stayed "Open" (`selectTriggerAfter`).
  - The only typing was into the palette's search input ("Clients" / "Matters", DA-13).
  - The template editor (`/settings/documents/templates/[id]/edit`) was only loaded, never edited or saved. It has no autosave.
- **Side effect, as before:** each read the server denies writes a `security.access_denied` audit event (NOTICE).
  - Sources: the 3 discovery GETs as Carol, and Carol's HTTP-404 deep links (5 in `03`, 8 in `05`).
  - K-64's runner now gates *before* the definition read, so Carol's `/reports/invoice-aging` no longer causes a 403.
  - The Home NOTICE pill reads 15 here (before: 13). Compare WARNING/CRITICAL, not NOTICE.

## Sheets
| File | Kink | What it shows |
|---|---|---|
| `sheet-k05-sidebar-density.png` | K-05 | Like before: rows Alice / Carol, light; 1440×900 rail crop (default; all open), 390×844 Sheet (top; scrolled to bottom) |
| `sheet-k05-dark-1440.png` | K-05 | after-only: 1440×900 **dark**, default groups, Alice / Carol (header positions in the caption) |
| `sheet-k07-palette-focus.png` | K-07 | Like before: rows Alice light / Alice dark / Carol light × header click, header Enter, pill click, ⌘K from Notifications (open; after Escape). `activeElement` is in each caption |
| `sheet-k07-keyboard-da13.png` | K-07 KBD | after-only: **keyboard only** (Tab from the top of the page to the trigger, Enter, Escape), header and pill; DA-13 (palette → type → Enter → new page; where focus lands). Rows Alice light / Carol light |
| `sheet-k08-sensitive-events.png` | K-08 | Like before: Alice's Home widget, 1440/1024 × light/dark, normal read |
| `sheet-k24-focused-item-icons.png` | K-24 | Like before: DPR-2 crops, light / dark; row menu ("Open matter", then "Open client") and task-status Select ("Open", then "In Progress") |
| `sheet-k26-breadcrumbs-1024.png` | K-26 | Like before: 1024×768 header strip, rail expanded / collapsed × light / dark × 4 page/persona rows |
| `sheet-k26-long-chain-1024.png` | K-26 | after-only: `/settings/documents/templates/[id]/edit`, 5 crumbs after the org, Alice, expanded / collapsed × light / dark |
| `sheet-k62-member-clients.png` | K-62 | Like before: Carol `/clients` and `?view=system:fica-review-due`; Alice control |
| `sheet-k63-member-404s.png` | K-63 | Like before: Carol on the 5 URLs |
| `sheet-k64-member-report-runner.png` | K-64 | Like before: Carol `/reports/invoice-aging` and `/reports`; Alice control |
| `sheet-k62-k63-extras.png` | K-62/K-63 | after-only: Carol **dark** `/clients` and `/invoices`; Alice's real 404s (matter nil-UUID; fee note nil-UUID); Carol "Back to Home" landing |

Tile-caption note: the before script's `noAccess` flag matches `/don.t have access/`. The new not-found copy contains "you don't have access to it", so the K-63 captions say "no access". The page is the not-found page (h1 "Page not found", `data-testid="org-not-found"`), not `PermissionDenied`.

## Before → after per kink

### K-05 — sidebar density (light; `getBoundingClientRect`, viewport px, scrollTop 0) — **PASS**
- Every row, before → after:
  - item height **32 → 28 px**;
  - gap between items **4 → 2 px**;
  - group header height **32 → 28 px**;
  - separator block **9 → 5 px**;
  - group padding **4/4 → 2/2 px**.
- The same values hold in dark at 1440 (`extra.K05.*.dark1440_default`; its header positions equal light's).
- `*` = header not fully above the fold. The fold is the bottom of `[data-sidebar="content"]`.

| Persona · viewport · state | Fold | Fees before → after | Trust before → after | Firm before → after | Overflow before → after | Below fold before → after |
|---|---|---|---|---|---|---|
| Alice 1440×900 default | 741 | 681–713 → **583–611** | 730–762 * → **620–648** | 779–811 * → **657–685** | 74 → **0** | Trust, Firm → **none** |
| Alice 1440×900 all open | 741 | 681 → 583 | 942 * → 798 * | 1239 * → 1043 * | 674 → 450 | Trust, Firm → Trust, Firm |
| Alice 1024×768 default | 609 | 681 * → 583–611 * (2 px cut) | 730 * → 620 * | 779 * → 657 * | 206 → 78 | Fees, Trust, Firm → Fees, Trust, Firm |
| **Alice 390×844 Sheet default** | 685 | **681–713 * → 583–611** | **730–762 * → 620–648** | **779–811 * → 657–685** | **130 → 2** | **Fees, Trust, Firm → none** |
| Alice 390 Sheet all open | 685 | 681 * → 583 | 942 * → 798 * | 1239 * → 1043 * | 730 → 506 | Fees, Trust, Firm → Trust, Firm |
| Carol 1440×900 default | 790 | 492 → 428 | 541 → 465 | 590 → 502 | 0 → 0 | none → none |
| Carol 1440×900 all open | 790 | 492 → 428 | 609 → 523 | 906 * → 768–796 * | 220 → 66 | Firm → Firm (6 px cut) |
| Carol 390 Sheet default | 734 | 492 → 428 | 541 → 465 | 590 → 502 | 0 → 0 | none → none |
| Carol 390 Sheet all open | 734 | 492 → 428 | 609 → 523 | 906 * → 768 * | 276 → 122 | Firm → Firm |

- **Default state:** the Fees, Trust and Firm headers now sit above the fold at 1440×900 and in the 390 Sheet. In the Sheet the Firm header's bottom (685) is exactly the fold, with 2 px of overflow left.
- **Still below the fold:**
  - At 1024×768 (default state) all three headers, but by much less: Fees by 2 px, overflow 206 → 78.
  - With every group open, Trust and Firm for the owner, and Firm for the member.
  - DA-3 accepts this ("where spacing allows"). Rows stay 28 px.
- **IA unchanged:**
  - Zone order and default `aria-expanded` are the same in the rail and the Sheet: Today/Matters/Clients open, Fees/Trust/Firm closed. Carol has no Clients zone.
  - Item counts match before: Alice 11 default / 28 all open; Carol 7 / 18.
  - First and last items match before: Home → Data requests / Settings for Alice, Home → Adverse parties / Settings for Carol.
  - The full ordered list (label + href) is recorded in `extra.K05.<who>.rail_allOpen.links`. The Sheet list equals the rail list for both personas (`sheetEqualsRail: true`).
  - The before pass recorded counts and the first and last items, not the full list. "Identical" therefore rests on those plus `git diff 3bc2110dc..84f867eef -- frontend/lib/nav-items.ts frontend/components/ui/sidebar.tsx` being empty.

### K-07 — palette focus return (1440, `document.activeElement`) — **PASS**
**18/18 trials: before → body, after → the opener.** Alice light, Alice dark and Carol light each give the same column:

| Trigger | Before open | After Escape, before | After Escape, **after** |
|---|---|---|---|
| header button, mouse click | body | body | **header `button[aria-label="Search, Command K"]`** |
| header button, focus + Enter | header button | body | **header button** |
| sidebar pill, mouse click | body | body | **pill** (`data-sidebar=menu-button`) |
| sidebar pill, focus + Enter | pill | body | **pill** |
| ⌘K from the focused Notifications button | Notifications | body | **Notifications button** |
| ⌘K from body | body | body | body (nothing to return to; reasonable) |

- In every trial the palette opened with focus on the cmdk input and closed on Escape (`stillOpen: false`).
- A mouse click focuses the button in Chromium, so the click cases return to the clicked trigger.

**KBD, keyboard only (`05`, no mouse):** Tab from the top of the page to the trigger, then Enter, then Escape.
- The header button is Tab stop 24 for Alice and 18 for Carol. The pill is Tab stop 2.
- Focus returns to the trigger with `:focus-visible`, so the ring is drawn (sheet). This holds for 6/6: Alice light, Alice dark, Carol light × header, pill.

**DA-13 (`05`):** open by keyboard, type "Clients" (Alice) or "Matters" (Carol), then Enter.
- The palette closes and the page changes to `/clients` or `/matters`.
- Focus lands on the **same trigger on the new page** (header button, or pill), `:focus-visible`. The trigger is a persistent shell element and still connected (X5). This holds for 6/6.

### K-08 — sensitive events (Alice, normal read) — normal read **PASS**; failing read **DEFERRED**
| | Before | After |
|---|---|---|
| widget present | yes (audience owner) | yes (audience owner) |
| pills | NOTICE 13 · WARNING 0 · CRITICAL 1 | NOTICE 15 · WARNING 0 · CRITICAL 2 |
| rows | 1 ("Matter Closure Override Used") | 2 (both "Matter Closure Override Used", written by this stack's e2e suites) |
| empty state / error line | no / no | no / no |
| height · width @1440 / @1024 | 204 · 1128 / 712 | 247 · 1128 / 712 (+43 px = one more row; dark 1136 / 720) |
| Carol | no widget (feeEarner) | no widget (feeEarner) |

- The widget renders rows with no error line. The count and row differences are data on the rebuilt stack, not code.
- **Failing read: DEFERRED to the component test.** This is the same finding as the before pass (DA-11):
  - The read is server-side (`dashboard/page.tsx`, Next server → `GET /api/audit-events`), so `page.route()` cannot touch it.
  - The widget's gate and the endpoint's gate are the same capability (TEAM_OVERSIGHT).
  - Forcing the failure needs SQL or a container change, and both are out of bounds.
  - The vitest test `sensitive-events-widget.test.tsx` "recent === null (read failed)…" is the only evidence for that branch. It was not observed in a browser.

### K-24 — keyboard-focused item icons (Alice 1440, composited, WCAG 2.x) — **PASS** (light); dark noted
| Theme | Item | Focus fill | Label | Icon before | Icon **after** |
|---|---|---|---|---|---|
| light | menu "Open matter" / "Open client" | `#00776a` | `#ffffff` 5.46:1 | `#4d545f` 1.40:1 | **`#ffffff` 5.46:1** (= label) |
| light | select "Open" (+ check indicator) / "In Progress" | `#00776a` | `#ffffff` 5.46:1 | `#4d545f` 1.40:1 | **`#ffffff` 5.46:1** (= label, indicator too) |
| dark | menu, both items | `#00c2b1` | `#ffffff` 2.25:1 | `#989faa` 1.19:1 | `#ffffff` **2.25:1** (= label) |
| dark | select, both items | `#00c2b1` | `#ffffff` 2.25:1 | `#989faa` 1.19:1 | `#ffffff` **2.25:1** (= label) |

- **Light:** the icon now takes the label colour and passes 3:1 (5.46:1). This is the register's K-24 ("firm light").
- **Dark:** the icon now equals the label at 2.25:1, which is still under 3:1. The cause is the white-on-`#00c2b1` focus fill itself (K-21 backlog). It was predicted in the before README and is a note only.
- DA-4 (the open sub-trigger state) is not observable here: no sub-menu exists on these surfaces.
- The task status was not changed: `selectTrigger` and `selectTriggerAfter` are both "Open".

### K-26 — breadcrumbs at 1024×768 (`nav[aria-label=Breadcrumb]` height; org crumb w × h) — **PASS** (register criteria), with an observation
| Page · persona | Expanded light | Expanded dark | Collapsed light | Collapsed dark |
|---|---|---|---|---|
| Fee note · Alice | 40 → **20**; org 141×40 → 141×20 | 20 → 20; 142×20 | 20 → 20 | 20 → 20 |
| `/trust-accounting/transactions` · Alice | 40 → **20**; org 116×40 → 116×20 | 40 → **20**; 119×40 → 119×20 | 20 → 20 | 20 → 20 |
| `/trust-accounting/transactions` · Carol | 40 → **20**; 116×40 → 116×20 | 40 → **20**; 119×40 → 119×20 | 20 → 20 | 20 → 20 |
| Matter detail · Carol | 40 → **20**; org 94×40 → 102×20 | 40 → **20**; 97×40 → 105×20 | 20 → 20 | 20 → 20 |
| **after-only** `/settings/documents/templates/[id]/edit` · Alice (5 crumbs) | 20; org 81×20 | 20; 83×20 | 20; 132×20 | 20; 141×20 |

- **All 7 wrapping cases are now one line (20 px).** The header stays 56 px, and `wraps: false` everywhere.
- The org crumb is ≤ 142 px (max-w-40 = 160 px). It has `title="E2E Test Organization"` (before: no title) and truncates with an ellipsis where the nav is narrow.
- **The last crumb is on screen in every capture** (`lastVisible: true`). It is truncated with an ellipsis at 1024 expanded, as it was before ("INV-00…", "Transac…").
- **Observation for the orchestrator (not a register criterion; nothing to compare, because the before pass had no long-chain page).**
  - The CodeRabbit round-1 change makes *middle* crumbs shrink too (`min-w-0 … max-w-40 truncate`, with `title`). The brief's original design had the middle crumbs `whitespace-nowrap` and only the org and last crumbs shrinking.
  - All crumbs therefore shrink together. On the 3-crumb pages at 1024 expanded the middle crumb is cut: "Fee not…", "Trust acco…", and for Carol's matter **"M…"** ("Matters").
  - On the 5-crumb template route at 1024 expanded every crumb is cut, and the **last crumb shows as "E"** ("Edit", 25 px), readable only through its `title`.
  - Collapsed, it improves: "Settin… / Docume… / Templa… / Templ… / E…".
  - It never wraps, but readability is poor. The fix may want a shrink priority (org and middle first, last crumb last). Suggested for `found.md` / backlog; the orchestrator decides.

### K-62 / K-63 / K-64 — Carol (member) 1440 (`#main-content`) — **PASS**
| URL | HTTP before → after | h1 before → after | Main content before → after |
|---|---|---|---|
| `/clients` | 200 → 200 | 0 → **1 "Clients"** | `PermissionDenied` → **client list** (26 clients; light and dark) |
| `/clients?view=system:fica-review-due` | 200 → 200 | 0 → 0 | "You don't have access to Clients" → **same** (D-152 kept; DA-6(a): the view option is still offered) |
| `/invoices` | 404 → 404 | 0 → **1** | crash panel → **in-shell "Page not found"** (light and dark) |
| `/invoices/collections` | 404 → 404 | 0 → **1** | crash panel → **"Page not found"** |
| `/invoices/unbilled` | 404 → 404 | 0 → **1** | crash panel → **"Page not found"** |
| `/compliance` | 404 → 404 | 0 → **1** | crash panel → **"Page not found"** |
| `/invoices/83ca9f03-…` (INV-0005) | 404 → 404 | 0 → **1** | crash panel → **"Page not found"** |
| `/reports/invoice-aging` | 200 → 200 | 0 → 0 | crash panel + React #441 → **"You don't have access to Reports"** (`PermissionDenied`, h2; DA-10) |
| `/reports` (control) | 200 → 200 | 0 → 0 | "You don't have access to Reports" → same |
| Alice `/clients?view=system:fica-review-due` | 200 → 200 | 1 → 1 | "Clients", FICA review due view, 0 clients → same |
| Alice `/reports/invoice-aging` | 200 → 200 | 1 → 1 | "Fee Note Aging Report" runner → same |
| Alice `/matters/00000000-…` (after-only) | — → 200 | — → 0 | the matter's own not-found ("This matter isn't on your list", h2, Back to Matters). The nearer not-found wins, and the file is unchanged by 663C |
| Alice `/invoices/00000000-…` (after-only) | — → 404 | — → 1 | org "Page not found" (a real `notFound()` from an owner path) |

- **"Back to Home"** has `href="/org/e2e-test-org/dashboard"`. Keyboard (focus + Enter) and mouse each land on `/dashboard` with h1 "Home" and the `home-page` testid.
- Each not-found page has exactly one h1 inside `#main-content` and one in the whole document. The shell (rail, header, breadcrumb) is intact (sheet).

## SEL-COMPAT (`sel-compat.json`, 1440 light)
- **22 pages** (Carol 12, Alice 10):
  - exactly **1** `main#main-content` on each;
  - `nav[aria-label="Main navigation"]` present (1) on each;
  - `nav[aria-label="Breadcrumb"]` present on each.
- h1 inside `#main-content`:
  - **1** on every content page and every org not-found page;
  - **0** on the `PermissionDenied` pages (Carol: the FICA review view, `/reports/invoice-aging`, `/reports`) and on Alice's matter not-found. These are pre-existing h2 designs, the K-44/K-54 backlog (DA-10).
  - Document-wide h1 = h1 in main on every page.
- **Palette:** `getByRole("dialog", { name: "Command Palette", exact: true })` → 1 for Alice and for Carol. The input is `role="combobox"`.

## Console (`console.json`; 0 hydration warnings in both passes)
| | Before | After `03` (like-for-like, 66 pages) | After `05` (extra, 50 pages) |
|---|---|---|---|
| errors | **6** = 5 × "Failed to load resource … 404" (K-63 URLs) + 1 × **"Minified React error #441"** (`/reports/invoice-aging`) | **5** = 5 × "Failed to load resource … 404" (the same 5 K-63 URLs) | 10 × "Failed to load resource … 404" (every load of a 404 URL: Carol dark `/invoices`, Back-to-Home ×2, SEL ×5, Alice invoice nil ×2) |
| warnings | 0 | 0 | 4 × `[tiptap warn]: Duplicate extension names found: ['link', 'underline']` (template editor, K-26 long-chain page) |

- **React #441 is gone** (K-64).
- **The 404 resource errors remain, as expected.** They are Chromium's network log line for a document served with HTTP 404, which `notFound()` correctly returns. They are not app exceptions, and they appear on every 404 page, Alice's included.
- The tiptap warning comes from the template editor page. That page is outside 663C's diff and was not in the before set, so it is not attributable to this PR. It is noted only.
- Every K-05/K-07/K-08/K-24/K-26 page in `03` is clean, as before.

## Verdicts
| Kink | Verdict | Basis |
|---|---|---|
| K-05 | **PASS** | 28 px rows. Default-state Fees/Trust/Firm above the fold at 1440 and in the 390 Sheet (before: below). IA identical. 1024 default and all-open still overflow (accepted, DA-3) |
| K-07 | **PASS** | 18/18 return to the opener (before 0/18); keyboard-only 6/6; DA-13 6/6 |
| K-08 | normal read **PASS**; failing read **DEFERRED** | widget unchanged with rows and no error line; failing branch is component-test-only (DA-11; no non-code way on this stack) |
| K-24 | **PASS** (light 1.40 → 5.46:1) | dark icon 1.19 → 2.25:1 = label; under 3:1 remains K-21 backlog (note) |
| K-26 | **PASS** on the register's criteria | 7/7 wrap cases 40 → 20 px; org ≤ 160 px with title; last crumb on screen. **Observation:** middle and last crumbs over-truncate at 1024 expanded ("M…", "E") |
| K-62 | **PASS** | Carol gets the list (h1 "Clients"), light and dark; the FICA review view is still denied |
| K-63 | **PASS** | 5/5 in-shell "Page not found", 1 h1, HTTP 404, Back to Home works (keyboard and mouse) |
| K-64 | **PASS** | Carol gets "You don't have access to Reports", with no crash and no React #441; Alice's runner is unchanged |

## Scripts
Run them from `scripts/` with `NODE_OPTIONS=""` (node 20), in this order:
1. `00-discover.cjs` (read-only)
2. `01-discover-ids.cjs` (read-only)
3. `02-discover-tasks.cjs` (read-only)
4. `03-capture.cjs`
5. `05-capture-extra.cjs` (merges into the `03` outputs)
6. `04-assemble.cjs`

Each ran once, except `01-discover-ids.cjs`. It ran three times while being written: the tasks endpoint path was corrected, and the template lookup was added. All three runs were read-only, and the file is the final version.
