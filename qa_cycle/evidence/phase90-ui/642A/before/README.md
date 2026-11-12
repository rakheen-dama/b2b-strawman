# 642A: OBS BEFORE (task 642.1), Clients list baseline

- **Build SHA:** `38c34ce921046db1c4171472432ec100fc10d27f` (main checkout, `main` == `origin/main`; the clients list code is unchanged from 641B).
- **Date:** 2026-11-12 (SAST).
- **Stack:** mock-auth e2e (:3001 / :8081 / :8090 / :5433). Built fresh from the main checkout: `e2e-down.sh` (removed the 641B stack), then `docker builder prune -f`, then `e2e-up.sh`. Containers were created 19:24 SAST and all were healthy. The log is `e2e-up-pristine.txt`; it ends at exit 0. **The stack is left running.**
- **Driver:** `obs-642a-before.cjs` (Playwright library 1.59.1, headless Chromium, nvm node v20.11.1, `NODE_OPTIONS=""`). Login is `/mock-login`, then `#user-select`, then Sign In (Alice = owner, Carol = member). Theme is set through `localStorage["kazi-theme"]`; the `<html class>` read back as `light` / `dark` as expected.
- **Structured data:** `before-pristine.json` and `before-rich.json`, 12 steps each. Pristine has 1,617 leaf fields and rich has 2,194. Screenshots are in `pristine/` (25) and `rich/` (28), named `<step>-<width>-<theme>.png`.
- **Parity tool:** `compare-642a.py <run> [after.json]`. It normalises UUIDs and rendered dates (`12 Nov 2026`), so two stacks built on different days still compare equal. Self-test (before compared against a copy of itself): pristine **511 IDENTICAL / 0 DIFF**, rich **552 IDENTICAL / 0 DIFF**.

## Datasets: replay these exactly in the AFTER run

| Order | Run | Setup | Command |
|---|---|---|---|
| 1 | `pristine` | Fresh e2e seed only: **1 client**, Acme Corp (Individual, Active, FICA Done, 1 open matter, outstanding R 8 625,00 · 1, trust R 0,00, completeness N/A). No tags, no saved views. | `node obs-642a-before.cjs before-pristine 0` |
| 2 | (setup) | `bash compose/seed/rich-seed.sh` (`rich-seed.txt`, exit 0) | from the repo root |
| 3 | (setup) | `bash setup-rich-api.sh` (`setup-rich-api.txt`), **API only**. Creates tags VIP / Corporate / Estate / Litigation. Tags Acme with Corporate+VIP, Bright with VIP, Dormant with Estate, and Zulu with all 4 (this shows the "+1" chip). Creates **Zulu Prospect Holdings** (Company, PROSPECT, no phone, `referred_by` = Website) and **Mkhize Family Trust** (Trust, PROSPECT). Records trust deposits of **R 15 000** for Bright (SEO Audit) and **R 2 500** for Dormant (Legacy Migration); both come back with status RECORDED and both count in `trustBalance`. Sets `referred_by` `requiredForContexts=["DOCUMENT_GENERATION"]` so that Completeness has real values: Zulu is 100%, the other five are 0%. | `bash setup-rich-api.sh` |
| 4 | `rich` | The same stack. The script creates saved view **"OBS 642A view"** through the Save View dialog: started on All clients, default columns (all 9 checked, 0 custom), not shared. | `node obs-642a-before.cjs before-rich 1` |
| 5 | (revert) | Sets `referred_by` `requiredForContexts=[]` again, so the specs are not affected by the completeness setup. | `bash setup-rich-api.sh revert` |
| 6 | specs | `pnpm test:e2e:customers` (see below). **The specs create clients, so the stack afterwards is rich + 10 spec-created clients (16 rows) and must not be re-captured as a baseline.** | |

Rich clients, as the API returned them: Acme Corp (Active, FICA Done, 2 open, R 8 625,00 · 1, R 0,00, 0%), Bright Solutions (Active, Done, 2, —, R 15 000,00, 0%), Carlos Mendez (Onboarding, In progress, 1, —, R 0,00, 0%), Dormant Industries (Dormant, Done, 1, —, R 2 500,00, 0%), Mkhize Family Trust (Prospect, Not started, 0, —, R 0,00, 0%), Zulu Prospect Holdings (Prospect, Not started, 0, —, R 0,00, 100%). All six were created on the same day, so the Created column shows the same date for every row. **Use `rich` as the sort, filter and search oracle.** Pristine has one row, so every order there is trivial.

Abbreviations below: AC Acme Corp, BS Bright Solutions, CM Carlos Mendez, DI Dormant Industries, MT Mkhize Family Trust, ZP Zulu Prospect Holdings.

## 1. Default list, `/org/e2e-test-org/clients`
- Page structure:
  - h1 is `Clients`; there is exactly one h1 in the document.
  - Subtitle `6 clients` (pristine `1 client`).
  - sr-only caption `Clients — All clients`.
  - Selected tab is **All clients**.
  - Footer `[data-testid=clients-footer]` reads `Showing 6 clients`.
- Default order is Client name ascending: AC, BS, CM, DI, MT, ZP.
- Columns, in order: Client, Lifecycle, FICA, Open matters, Outstanding fee notes, Trust balance, Completeness, Created, Contact, plus an Actions header (sr-only).
  - Contact is the only column that is not a sort link.
  - The Actions header has no aria-sort.
  - Every other inactive header has `aria-sort="none"`.
  - The Client header defaults to `ascending` with no URL params, and its href is `?sortBy=name&sortDir=desc`.
- Row DOM:
  - Each row is a real `<tr data-testid="client-row" data-slot="table-row">`, cursor pointer, tabIndex -1, no role, 10 `<td>`.
  - The row sits inside `table[data-testid=clients-table][data-slot=table]`, inside `div[data-slot=table-container]`, inside `div.@container`.
  - Name cell: `<a data-testid="client-link" title=<name>>`. Under the name are `[data-testid=client-type]` (Individual/Company/Trust) and outline tag badges (at most 3, then "+N").
  - FICA cell: `[data-testid=client-fica-status]`. Done is shown as a badge plus the verified date; In progress is an outline badge; Not started is red text.
  - Last cell: `button[data-testid=client-row-actions-trigger][aria-haspopup=menu]` with sr-only text "Client actions".
- Interactions:
  - **Clicking a plain cell (Lifecycle) navigates** to `/clients/<id>`.
  - The client link navigates to the client page (h1 = name).
  - Opening the row menu does **not** navigate. It has one menuitem, `<a>` "Open client", which links to `/clients/<id>` and navigates when clicked.
- `New Client` button opens the dialog **Create Client** (closed with Escape; nothing was created).

## 2. Sort (rich): every sortable column, clicked twice
The first click on an inactive column goes to `?sortBy=<key>&sortDir=asc` (aria-sort `ascending`, arrow ↑). The second goes to `desc` (`descending`, ↓). The third href goes back to `asc`. Clicking the default-active Client column first goes to `desc`.

| Column | key | asc | desc |
|---|---|---|---|
| Client | name | AC BS CM DI MT ZP | ZP MT DI CM BS AC |
| Lifecycle | lifecycleStatus | MT ZP CM AC BS DI | DI AC BS CM MT ZP |
| FICA | ficaStatus | MT ZP CM AC BS DI | AC BS DI CM MT ZP |
| Open matters | openMatterCount | MT ZP CM DI AC BS | AC BS CM DI MT ZP |
| Outstanding fee notes | outstanding | BS CM DI MT ZP AC | AC BS CM DI MT ZP |
| Trust balance | trustBalance | AC CM MT ZP DI BS | BS DI AC CM MT ZP |
| Completeness | completeness | AC BS CM DI MT ZP | ZP AC BS CM DI MT |
| Created | createdAt | AC BS CM DI ZP MT | MT ZP DI CM BS AC |

**Parity trap (same as 641A): ties are NOT reversed by `desc`.** Ties fall back to name **asc** in both directions, and nulls sort last in both (`sortClientRows`). Examples: Open matters desc gives AC BS (2,2), then CM DI, then MT ZP. Lifecycle sorts by the lifecycle order (PROSPECT < ONBOARDING < ACTIVE < DORMANT …), not alphabetically. FICA order is NOT_STARTED < IN_PROGRESS < DONE. Outstanding sorts by the total, so a 0 total (rendered "—") still sorts as 0, not as null. Every sort header href keeps the other params; the full lists are in `9-combined.start.sortHrefs` and `6-filter-sheet.applied.sortHrefs`.

## 3. Saved views and clearOnSavedView
- Tabs (role=tab): **All clients, Active, FICA outstanding, With trust balance** (system views, `data-system-view`), then the saved view **OBS 642A view**.
- Switch URLs and results:

  | Tab | URL | Rows |
  |---|---|---|
  | All clients | `/clients` (no param) | 6 |
  | Active | `?view=system%3Aactive` | AC, BS |
  | FICA outstanding | `?view=system%3Afica-outstanding` | CM, MT, ZP |
  | With trust balance | `?view=system%3Awith-trust-balance` | BS, DI |
  | OBS 642A view | `?view=<uuid>` | 6, all 9 columns incl. **Contact** |

  In pristine, FICA outstanding and With trust balance are both empty ("No clients match this view." / Clear filters).
- The caption follows the view: `Clients — <view>`.
- On the saved view, the **lifecycle pills and the Filters button are hidden**, and "Show incomplete" stays.
- **clearOnSavedView**, starting from `?tags=vip&lifecycleStatus=ACTIVE&sortBy=createdAt&sortDir=desc` (rows BS, AC; Filters badge 1; pill Active):
  - Switching to **FICA outstanding** gives `…&sortDir=desc&view=system%3Afica-outstanding`. **tags and lifecycleStatus are kept** and the list is empty.
  - Switching to **All clients** leaves the URL unchanged (there was no view param to drop).
  - Switching to the **saved view** gives `?sortBy=createdAt&sortDir=desc&view=<uuid>`. **tags and lifecycleStatus are dropped, the sort is kept**, and the rows are MT ZP DI CM BS AC.
- Save View dialog titles are Configure Filters, then Select Columns, then Name Your View. The columns step lists 9 client columns (checked) and 9 custom fields (unchecked).

## 4. Lifecycle filter (`[data-testid=client-lifecycle-filter]` role=group "Lifecycle", links with `aria-current`)

| Pill | URL | Rows |
|---|---|---|
| All | `/clients` | 6 |
| Prospect | `?lifecycleStatus=PROSPECT` | MT ZP |
| Onboarding | `?lifecycleStatus=ONBOARDING` | CM |
| Active | `?lifecycleStatus=ACTIVE` | AC BS |
| Dormant | `?lifecycleStatus=DORMANT` | DI |
| Offboarding | `?lifecycleStatus=OFFBOARDING` | empty: subtitle `0 clients`, no footer, "No clients match this view." + Clear filters (`/clients`) |
| Offboarded | `?lifecycleStatus=OFFBOARDED` | empty, same as Offboarding |

- **Show incomplete** (`[data-testid=client-show-incomplete]`, a link) goes to `?showIncomplete=true` and shows AC BS CM DI MT (all at 0%). Clicking it again goes back to `/clients`. In pristine (Acme is N/A) it shows the EmptyState **"All clients are complete / All clients have 100% completeness."** rather than the dashed box.
- On `?view=system%3Aactive` the pills carry the view, and "All" writes `lifecycleStatus=ALL`. That gives `?view=system%3Aactive&lifecycleStatus=ALL` with all 6 rows, pill All active, tab still Active.

## 5. Search (`role=search` "Search clients", placeholder `Search clients…`)
- Search is debounced and uses `router.replace`. `?search=Acme` returns AC (`Showing 1 client`).
- Search also matches e-mail: `?search=contact` returns AC.
- `?search=zzqx-no-such-client` shows no table, no footer, subtitle `0 clients`, the dashed box, and **Clear filters** (`href=/org/e2e-test-org/clients`, which drops every param). Clicking it gives 6 rows and an empty search box.

## 6. Filter sheet (`client-filters-trigger`, only when tags exist and not on a saved view)
- The sheet is "Filter clients" / "Show only clients carrying every selected tag.", with the controls Tags, Add Tag (options Corporate, Estate, Litigation, VIP), Clear, Apply, Close.
- Corporate + Apply gives `?tags=corporate` with AC and ZP. The trigger reads `Filters` plus a badge `1`. The pill and sort hrefs keep `tags`.
- Adding Show incomplete gives `?tags=corporate&showIncomplete=true` with AC.
- Clear in the sheet gives `?showIncomplete=true`.
- Pristine has no tags, so no trigger.

## 7. Keyboard
- From the search box, Tab moves through the 7 lifecycle pills, then Show incomplete, then Filters (rich only), then the **Client** header link.
- That is 10 tabs in rich and 9 in pristine. Focus outline is `auto`.
- Enter on the Client header goes to `?sortBy=name&sortDir=desc`.

## 8. Combined (query preservation)
- Starting point: `?lifecycleStatus=ACTIVE&search=Acme&sortBy=name&sortDir=desc`, which shows AC.
- Sort hrefs keep every param, e.g. FICA is `?lifecycleStatus=ACTIVE&search=Acme&sortBy=ficaStatus&sortDir=asc`. The pills and the Show incomplete link keep search and sort.
- Clicking FICA gives `?lifecycleStatus=ACTIVE&search=Acme&sortBy=ficaStatus&sortDir=asc`.
- Then the All pill gives `?search=Acme&sortBy=ficaStatus&sortDir=asc`.
- Then clearing the search gives `?sortBy=ficaStatus&sortDir=asc` (MT ZP CM AC BS DI).

## 9. Empty and edge variants (`10-empty`)
- These each show the dashed "No clients match this view." box with Clear filters:
  - search no-match
  - `lifecycleStatus=OFFBOARDED`
  - the empty system views (pristine)
- `?showIncomplete=true` in pristine shows the EmptyState "All clients are complete".
- An unknown saved-view UUID falls back to All clients with all rows.
- `?sortBy=contact` (not sortable) falls back to Client ascending.

## 10. Layout and overflow (the sticky-header question)

| Width | View | Visible headers | Table / scroll container (px) | Table scrolls sideways | Page scrolls sideways |
|---|---|---|---|---|---|
| 1440 | default | all except **Contact** | 1134 / 1134 | **no** (0) | no |
| 1440 | sorted by Created | same | 1134 / 1134 | no | no |
| 1440 | saved view (all 9) | all incl. Contact | 1134 / 1134 | no | no |
| 1024 | default | Client, Lifecycle, FICA, Outstanding, Trust, Actions (hidden: Open matters, Completeness, Created, Contact) | 718 / 718 | **no** (0) | no |
| 1024 | sorted by Created | the 1024 default set + Created | 718 / 718 | no | no |
| 1024 | **saved view (all 9)** | all 9 | **1021 / 718** | **yes, +303 px** | no |

The light and dark numbers are identical. `thead` is `position: static` (not sticky today). The scroll container is `div[data-slot=table-container]` (`overflow-x: auto`).

**So the default list never scrolls sideways at 1024 or 1440, but a saved view with explicit columns does at 1024.** In that case the container scrolls; the page does not.

## 11. Carol (member)
- Carol gets the **PermissionDenied page** ("You don't have access to Clients / Contact your organisation admin to update your role. / Go to dashboard") at both 1440 and 1024. There is no table, no h1 in `#main-content`, and no New Client or Save View buttons.
- So **no money columns are shown or redacted in the UI for a member**.
- The page gates on `isAdmin || isOwner || CUSTOMER_MANAGEMENT`, and Carol's capabilities are `[AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST]`.
- For the record, `GET /api/clients` as Carol returns **200** with `outstandingFeeNoteTotal: null` (redacted) and `trustBalance: 0` (not redacted, because she has VIEW_TRUST). See `carol-api-pristine.txt`.

## 12. Console
- On the clients pages there were **no errors, no warnings, and no hydration messages** in either run.
- The only console error is `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch` on **/dashboard** right after login (once per context in the rich run). This is a mock-auth artefact and is not on the clients page.

## SEL-COMPAT before
- `pnpm test:e2e:customers` ran from the main checkout's `frontend/` with `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001` on node v20.11.1 (`/opt/homebrew/bin/pnpm`). It ran after the rich capture and the revert.
- Result: **18 passed, 2 skipped, 0 failed**, exit 0 (10.1 s). The log is `e2e-customers-before.txt`.
- The 2 skips are the annotated `628F-BLOCKER` DORMANT→OFFBOARDING / OFFBOARDING→OFFBOARDED tests, which are skipped by design.
- Afterwards the main checkout was restored (`git checkout -- frontend/test-results documentation/screenshots`, and the run's `frontend/test-results/.last-run.json` was removed). `git status` there is the same as before.
- Selector inventory is in `sel-compat-before.txt`:
  - `client-row` is used at `customer-crud.spec.ts:177`.
  - `clients-footer` is used at `:181`.
  - The `Search clients` search role is used at `:155`.
  - The h1 `Clients` is used at `:39/:149/:172`.
  - `clients-table` is used at `day-90-review.spec.ts:74`.
  - `openClient` is defined at `helpers/legal-depth-ii.ts:156`. It uses `getByRole("link",{name,exact})` on `/clients`, and there are 7 call sites.
  - Nothing in e2e uses `client-lifecycle-filter`, `client-link`, `client-fica-status`, `client-show-incomplete`, `client-filters-trigger`, `client-row-actions-trigger` or `sortBy=`.
  - `day-30-billing.spec.ts:97` "Search clients..." is the fee-note client picker, not this page.

## Pre-existing defects / oddities (not 642A scope, recorded only)
1. **Saved view with explicit columns at 1024: the Client column collapses and the names are invisible.**
   - The Client `th` is 16 px wide and `client-link` is **0 px** wide, so the header reads "CLIENT↑CYCLE" over Lifecycle and no client name is visible. The table also overflows by 303 px.
   - Cause: `CLIENT_CELL_CLASS = "w-full max-w-0"`, combined with no priority hiding when a saved view picks the columns.
   - Evidence: `rich/01-saved-view-1024-light.png` and `probe-saved-view-client-column.json`.
   - At 1440 the same view gives the Client column only 129 px.
2. **Save View dialog, step 1:** it is ~1240 px tall with `overflow-y: visible`. At 1440×900 it spans y −169…1070, so **Next is off-screen** (`nextInViewport: false`). The script clicks it through the DOM. This is the same defect 641A recorded for matters.
3. **Ties and nulls are not reversed by `desc`.** This is by design in `sortClientRows`, but a TanStack swap will differ unless it replicates this behaviour.
4. **No firm-UI path to OFFBOARDING/OFFBOARDED** (spec 628F-BLOCKER), so those two pills are always empty on these datasets.
5. `day-90-review.spec.ts:73` has the comment "a second, hidden copy of the list renders outside #main-content". It is **stale**: there is exactly one `clients-table` in the document today (`probe-duplicate-list.json`).

## Files
`obs-642a-before.cjs` (the OBS driver, parameterised by tag; the AFTER run re-uses it unchanged except for any probes it needs to add), `compare-642a.py`, `setup-rich-api.sh` + `setup-rich-api.txt`, `rich-seed.txt`, `e2e-up-pristine.txt`, `obs-run-{pristine,rich}.log`, `before-{pristine,rich}.json`, `carol-api-pristine.txt`, `e2e-customers-before.txt`, `sel-compat-before.txt`, `probe-saved-view-client-column.{cjs,json}`, `probe-duplicate-list.{cjs,json}`, `pristine/*.png` (25), `rich/*.png` (28).
