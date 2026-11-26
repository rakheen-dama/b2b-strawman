# 664B OBS-after (PR #1869, head `7a41694fd`)

**Stack:** e2e mock-auth :3001.
- The frontend was rebuilt with `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` from `worktree-epic-664B` @ `7a41694fd`.
  - Image `sha256:9eef57638835`, container created 2026-11-26T13:42:52Z. The `pnpm --filter frontend build` step ran (45.6 s) and was not cached.
  - **Build check:** the served chunk `.next/static/chunks/3j8m0wuof2mk-.js` contains the new CreateViewDialog class `flex max-h-[90vh] flex-col sm:max-w-lg`. At `e0fc21fb6` that class was `sm:max-w-lg`.
- Backend :8081 and the data were not changed. The seed re-run was a no-op (`seed.json`: recorded 0, 60 / 60, view already existed).
- Captured 2026-11-26 ~13:46Z with the Playwright library (headless chromium). Settings are the same as before.

**Scripts:**
- `../before/scripts/00-seed.cjs`, `01-capture.cjs` and `02-assemble.cjs` were re-run **unedited** with `PHASE=after`.
- Two new additive scripts are in `scripts/`:
  - `03-k47-real-click.cjs` drives real mouse input. The before seed used `dispatchEvent` for Next / Save, and `01-capture` only trial-clicks.
  - `04-header-overlap.cjs` measures where the F1 sort icon sits, and checks sticky-header parity.

Verdicts are in `verdicts.json`, one line.

## 1. Other-creditors pagers: PASS, no differences
- Comparing `pager.json` before and after over every field gives **0 differences**:
  - pager text
  - rows, first and last row
  - control tag, text, aria-label and disabled state
  - Playwright role and name
  - test-id counts
  - 4 click flows
- **hrefs: 21/21 identical.**
- "Page n of m" is identical in all 9 states. "Page 10 of 3" on `?page=9` / `?itemsPage=9` is unchanged and still a pre-existing quirk.
- Previous and Next have the exact names "Previous" and "Next".
- The ends are disabled `<button>`s: Previous on the first page, Next on the last.
- Page-2 deep links work: `?page=1` shows "Page 2 of 3", and so does `?itemsPage=1`.
- The other list's param is kept: `?page=1` → `?page=1&itemsPage=1` → `?page=1&itemsPage=2` → `?page=0&itemsPage=2`.
- Carol sees the same pager.
- The pager crops look the same: the left/right chevron sits inside each button.

## 2. F1 / K-06 `/matters`: overflow REDUCED, not removed

Table width in container width (overflow px):

| persona | 1024 light | 1024 dark | 1440 light | 1440 dark |
|---|---|---|---|---|
| Alice before | 1054 / 710 (344) | 1054 / 718 (336) | 1193 / 1126 (67) | 1193 / 1134 (59) |
| Alice after | **1036 / 710 (326)** | 1036 / 718 (318) | 1175 / 1126 (49) | 1175 / 1134 (41) |
| Carol before | 925 / 710 (215) | 925 / 718 (207) | 1126 / 1126 (0) | 1134 / 1134 (0) |
| Carol after | **895 / 710 (185)** | 895 / 718 (177) | 1126 / 1126 (0) | 1134 / 1134 (0) |

- Overflow drops by **18 px** for Alice and **30 px** for Carol at 1024. Each content-sized sortable header lost the 6 px of inline icon and gap:
  - Reference 109→103
  - Status 84→78
  - Next in diary 130→124
  - for Carol also Unbilled 98→92 and Trust 77→71
- At 1024 the matters table **still scrolls sideways** (326 / 185 px). Matter (240) and Client (176) have fixed widths and did not change.
- **Header-width shift on sort: 0 px** in all 8 persona × viewport × theme cases. The table width is also unchanged on sort.
- **Chevron at rest:** visible in all 8, opacity 0.4, `display: block`, 14 px. Hovering leaves it unchanged (0.4). It is now `absolute top-1/2 right-1`.

### Spot-checks (Alice 1024 light)
- `/clients`: 710 / 710 → 710 / 710. Sort shift 0.
- `/reports/profitability`:
  - utilization table: 662 / 662 → 662 / 662.
  - matter profitability table: **1149 → 1137** in 662 (overflow 487 → 475).
  - client table: 754 → 754. It is not sortable and has its own inline `ArrowUpDown` icons, unchanged.
  - Sort shift is 0 for both sortable tables.
- Fund ledger: 754 / 662 → 754 / 662, unchanged. It has no sortable headers.

### Sort icon vs header text (`header-overlap.json`, `sheet-header-crops.png` at 2x)
- 60 sort icons were measured on 7 pages: matters for Alice and Carol at 1024 / 1440, clients default at 1024 / 1440, the clients K-11 view, and profitability.
  - **0 overlaps** between icon and label text, using the text-line rects of the label.
  - **0 icons outside their `th`.**
  - **0 clipped labels.**
- Where the label is right-aligned or fills the column, the gap between icon and label is **2 px**:
  - money headers: Unbilled, Trust, Billable Hours, Revenue, Cost, Margin, Margin %
  - two-line clients headers: "Outstanding / fee notes", "Trust / balance", "Open / matters"
  - Reference, Status, Next in diary, Completeness
- **Sticky clients header still works.** After a page scroll, the sortable `th` (CLIENT) and the plain `th` (ACTIONS) are at the same top, and both are `position: sticky`:
  - 1024: 397 → 111 px after scrolling 286 px.
  - 1440: 321 → 243 px.
  - `@2xl:sticky` wins over the new `relative`.
- On matters, neither `th` is sticky: Actions is `static`, the same as before. They move together.
- **FLAG (new visual behaviour, no overlap): the icon is detached on left-aligned headers in wide columns.**
  - The icon is now pinned to the right edge of the `th`. On those headers it sits **12 px before the next column's label**, and 103–241 px after its own label.
  - **Before**, it sat 4 px after its own label.
  - Measured cases:

    | page | header | gap to own label | gap to next label |
    |---|---|---|---|
    | matters | MATTER | 162 px | 12 px (CLIENT) |
    | matters | CLIENT | 103 px | 12 px (STATUS) |
    | clients 1024 | CLIENT | 130 px | 12 px (LIFECYCLE) |
    | clients 1440 | CLIENT | 224 px | 12 px (LIFECYCLE) |
    | profitability | Matter | 241 px | 12 px (Client) |
    | profitability | Client | 191 px | 12 px (CURRENCY) |

  - The clearest case is `/clients`. The **active** ascending arrow for Client reads as "↑ LIFECYCLE", which looks as if Lifecycle is the sorted column.
  - This is a legibility / association concern for the orchestrator or product to judge. It is not a geometric overlap.

## 3. K-11 saved-view Client column: FIXED

| | 1024 light | 1024 dark | 1440 light | 1440 dark |
|---|---|---|---|---|
| saved view Client th, before → after | 16 → **232** | 16 → **232** | 76 → **232** | 84 → **232** |
| saved view table / container, after | 1270 / 710 | 1270 / 718 | 1270 / 1126 | 1270 / 1134 |
| "All clients" Client th, before → after | 203 → 203 | 211 → 211 | 284 → 296 | 292 → 304 |

- At 1024 the saved view **scrolls sideways inside its frame** (`overflow-x: auto`), and the document does not scroll.
- Name links are now 73–216 px wide (before: 0 px at 1024 and 60 / 68 px at 1440). 3/10 are still truncated with an ellipsis, against 10/10 before.
- Note: at 1440 the saved view now also scrolls sideways, by 144 / 136 px. Before, it fit by squeezing Client to 76 / 84 px. This is the trade-off the design intends.
- **Default view at 1024 is unchanged:** same columns, widths and hidden columns.
  - At 1440, Client is 12 px wider. It took the 6 px each that "Open matters" and "Completeness" freed (F1).

## 4. K-47 Save View dialog: FIXED

| route | viewport | before top / bottom / h | after top / bottom / h | step body scrollH / clientH |
|---|---|---|---|---|
| /matters | 1024×768 | −780 / 1549 / 2329 | **38 / 730 / 691** | 2165 / 527 |
| /matters | 1024×900, 1440×900 | −714 / 1615 / 2329 | **45 / 855 / 810** | 2165 / 646 |
| /clients | 1024×768 | −269 / 1038 / 1307 | **38 / 730 / 691** | 1143 / 527 |
| /clients | 1024×900, 1440×900 | −203 / 1104 / 1307 | **45 / 855 / 810** | 1143 / 646 |

The numbers are the same in light and dark.

**`01-capture` results:**
- Title, Close and Next are fully in the viewport in **12/12** cases (before: 0/12).
- The trial click on Next gives "clickable" in 12/12 (before: "outside of the viewport").
- **Wheel artefact:** the capture wheels at the centre of the viewport. On /matters at 1024×768 that point is over a `<select multiple>` (Deeds Office), so the select scrolled and the body did not (scrollTop 0). This comes from how the test picks its point, not from the product.
- In the other 10 cases the body scrolled 600 px on /matters and 497 px on /clients (its maximum). The dialog top stayed fixed.

**`03-k47-real-click` (real mouse input) results: 12/12 PASS.** In each case:
1. A real `mouse.wheel(400)` over a point of the body that is not a form control scrolls the body **+400 px**. Title, Close and Next stay in the viewport.
2. A real `mouse.click` at the centre of Next works. Next is the topmost element there (`elementFromPoint`). The dialog goes to "Select Columns (Step 2 of 3)".
3. A second real click goes to "Name Your View (Step 3 of 3)", where Save View is in the viewport. Save is **not** clicked.
4. A real click on the Close X closes the dialog. The number of saved-view tabs is unchanged.

See `k47-real-click.json` and `sheet-k47-real-click.png`.

## 5. aria-sort: unchanged
- `sel-compat.json` is identical to before for otherCreditors, matters and clients.
- `/matters` has 9 "none" and 1 "ascending". `/clients` has 7 "none" and 1 "ascending".
- On every `th` the `aria-sort` value equals before in all 8 matters captures.

## 6. Console: clean
- 0 errors, 0 warnings, 0 hydration messages over:
  - 45 page loads in `01-capture`
  - 7 in `04`
  - 12 in `03`
