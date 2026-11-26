# 664B observe-first — BEFORE (task 664.7)

**Stack:** e2e mock-auth :3001. The frontend was rebuilt with `e2e-rebuild.sh frontend` from `worktree-epic-664B` @ `e0fc21fb6` (clean tree). Container created 2026-11-26T13:06:15Z, image `sha256:8a31b1fa480c`. Backend :8081 was not changed. Captured 2026-11-26 ~13:19Z with the Playwright library (headless chromium 1.59.1), not the MCP. Settings: reduced motion, timezone Africa/Johannesburg, rail expanded. Theme set through localStorage `kazi-theme`.

**Scripts** (`scripts/`). Each takes `PHASE=before|after`. Output goes to `664B/$PHASE/` and raw tiles to `/tmp/664B-$PHASE-raw/`.
- `lib.cjs`: shared helpers. It loads Playwright from the worktree's `frontend/node_modules`, or from the sibling main checkout when the worktree has none.
- `00-seed.cjs`: idempotent seed. It writes `seed.json`. Once the data exists a re-run changes nothing.
- `01-capture.cjs`: captures. It writes `measurements.json`, `pager.json`, `sel-compat.json` and `console.json`.
- `02-assemble.cjs`: builds the contact sheets.

Re-run after the change, unedited, from `before/scripts/`:
`PHASE=after STACK="<build note>" node 00-seed.cjs && PHASE=after STACK="<build note>" node 01-capture.cjs && PHASE=after node 02-assemble.cjs`.

## Data seeded (persists on this stack; no SQL writes)
- **Other creditors (REST, as Alice):** one CSV bank statement `664B-seed-statement.csv` (`fe23dc15-…`) imported on the primary trust account "Mathebula & Partners Trust Account". It has 120 credit lines dated 2026-10-01 → 2026-11-24. Each line was recorded through `POST /api/bank-statement-lines/{id}/record`:
  - 60 lines as `FUND_INTEREST` (R10.01–R10.60, "664B seed interest NNN")
  - 60 lines as `UNIDENTIFIED_DEPOSIT` (R501–R560, "664B seed unknown deposit NNN")
  - Before seeding both lists were empty (0 / 0). After: 60 / 60, 0 errors.
  - Page size is 25 for both lists (`FUND_LEDGER_PAGE_SIZE` / `UNIDENTIFIED_PAGE_SIZE`, `other-creditors/page.tsx:33-34`), so each list has **3 pages** (25 / 25 / 10).
- **Saved view "664B K-11 view"** on `/clients` (Alice). Created in the browser through the Save View dialog: All clients, default columns kept as explicit columns, not shared. Its id is `fde1d1c4-…`. Next and Save are off-screen (K-47), so they were `dispatchEvent`'ed, as 662A did.

## 1. Other-creditors pagers (`pager.json`, `sheet-other-creditors-*.png`)
- **Page params are zero-based.**
  - `?page=1` shows "Page 2 of 3" and `?itemsPage=1` shows "Page 2 of 3". No params means page 0.
  - The page parses each param with `Math.max(0, parseInt)` (`page.tsx:61-62`), and `LedgerPager` renders `page.number + 1` (`:466`).
- **First page** (both lists): "Page 1 of 3", 25 rows each.
  - Previous is a disabled `<button>` named "Previous".
  - Next is a `<a>` link named "Next" with `href=…?page=1` or `…?page=0&itemsPage=1`.
- **Middle page:** both controls are links.
- **Last page** (`?page=2` / `?itemsPage=2`): "Page 3 of 3", 10 rows. Previous is a link and Next is a disabled button.
- **Each list keeps the other's param:**
  - Fund Next goes to `?page=1`.
  - Items Next then goes to `?page=1&itemsPage=1`, then `?page=1&itemsPage=2`.
  - Fund Previous goes to `?page=0&itemsPage=2`. The `page=0` is written explicitly.
- **Out of range** (`?page=9` / `?itemsPage=9`):
  - The page shows `fund-ledger-page-out-of-range` or `unidentified-deposits-page-out-of-range`.
  - The pager still renders **"Page 10 of 3"**, with Previous linking to `?page=8`, which is also out of range. This is a pre-existing quirk, recorded only.
- **Test ids on the landing page:**
  - Present: `other-creditors-page` 1, `fund-ledger-row` 25, `unidentified-deposit-row` 25, `fund-ledger-table` and `unidentified-deposits-table` 1 each.
  - Absent: `no-account-state` 0 and `fund-ledger-in-debit` 0. The Fund ledger is in credit after the seed.
- **Carol** (VIEW_TRUST) sees the same pagers.
- **Table widths at 1024:** `fund-ledger-table` is 754 px in a 662 px container, light (670 dark), so it scrolls sideways. At 1440 it fits (1078 / 1078).

## 2. F1 / K-06: `/matters` (`measurements.json` › `matters`, `sheet-matters-{alice,carol}.png`)

| persona | 1024×768 light | 1024×768 dark | 1440×900 light | 1440×900 dark |
|---|---|---|---|---|
| Alice (11 rows) | table **1054** in container **710** | 1054 in 718 | 1193 in 1126 | 1193 in 1134 |
| Carol (2 rows) | **925** in **710** | 925 in 718 | 1126 in 1126 | 1134 in 1134 |

- The container has `overflow-x: auto` and the document itself does not scroll.
- Alice 1024 header widths: Reference 109, Matter 240, Client 176, Status 84, Unbilled 150, Trust 117, Next in diary 130, Actions 48. Work type, Fee earner and Opened are priority-hidden (0 px).
- Inactive chevrons (`lucide-chevrons-up-down … opacity-40`) are always visible: opacity 0.4, `display: block`, 14 px wide, in layout. Hovering the header leaves them unchanged (0.4).
- The active sort icon (ArrowUp) is at opacity 1.
- Clicking "Reference" goes to `?sort=referenceNumber&dir=asc`. **Header widths shift by 0 px in every case.** The active and inactive icons are both 14 px.
- **Spot-checks** (Alice 1024 light):
  - `/clients`: 710 in 710, chevrons at 0.4, a sort click shifts nothing.
  - `/reports/profitability`, utilization table: 662 in 662.
  - `/reports/profitability`, matter profitability table: **1149 in 662**.
  - `/reports/profitability`, third table (Client / Revenue / Cost / Margin columns, not sortable): 754 in 662, no `aria-sort`.
  - The profitability tables sort by local button. Chevrons are at 0.4 and the sort click shifts nothing.

## 3. K-11 saved-view Client column: **REPRODUCED**

| | 1024 light | 1024 dark | 1440 light | 1440 dark |
|---|---|---|---|---|
| "All clients" Client th | 203 | 211 | 284 | 292 |
| saved view Client th | **16** | **16** | 76 | 84 |
| saved view name links | 10/10 at 0 px, all truncated | same | 10/10 at 60 px, all truncated | 10/10 at 68 px, all truncated |
| saved view table / container | 1066 / 710 | 1066 / 718 | 1126 / 1126 | 1134 / 1134 |

These numbers match 662A.

## 4. K-47 Save View step 1 "Configure Filters" (Alice; same numbers light and dark)

| route | viewport | dialog top / bottom / height | title top | Next top | Close top |
|---|---|---|---|---|---|
| /matters | 1024×768 | −780 / 1549 / **2329** | −755 | 1488 | −764 |
| /matters | 1024×900, 1440×900 | −714 / 1615 / 2329 | −689 | 1554 | −698 |
| /clients | 1024×768 | −269 / 1038 / **1307** | −244 | 977 | −253 |
| /clients | 1024×900, 1440×900 | −203 / 1104 / 1307 | −178 | 1043 | −187 |

- The title, Next and Close are outside the viewport in **12/12** captures.
- The dialog is `position: fixed`, `max-height: none`, `overflow-y: visible`. **The body does not scroll:** after a 600 px mouse wheel, the dialog top is unchanged.
- A trial click on Next fails with "element is outside of the viewport".
- On /clients the dialog grew from 662A's 1239 px to 1307 px. There are more custom fields now.

## 5–6. aria-sort and console
- `aria-sort` sits on every sortable `th`:
  - `/matters`: 9 "none" and 1 "ascending" (Next in diary is the default sort).
  - `/clients`: 7 "none" and 1 "ascending" (Client).
  - Actions, Contact and Currency have none.
  - Details are in `sel-compat.json` and `measurements.json` (`ths[].ariaSort`).
- Console over 45 page loads: **0 errors, 0 warnings, 0 hydration messages** (`console.json`).
