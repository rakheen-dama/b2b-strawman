# 641B "after" OBS (task 641.12)

Captured 2026-11-12 between about 16:45Z and 17:10Z. The target was a fresh e2e mock-auth stack built from the 641B worktree at code head `4754eda99`: frontend on :3001, backend on :8081. The run order was `e2e-down.sh`, then `docker builder prune -f`, then `e2e-up.sh`.

- **Provenance:** the compose `working_dir` label is `worktree-epic-641B/compose`. The containers were created at 16:25:49Z. The built SSR chunk `.next/server/chunks/ssr/_18n19h5._.js` contains the branch-only string "None scheduled".
- **Data:** the stack was not reseeded. It holds the data left by the gate run: projects 27, then legal-lifecycle 105, then legal-depth-ii 40 passed / 4 skipped.

## Method

- **Driver:** `obs-after.cjs`, a Node Playwright library script (`chromium.launch()`, nvm node v20.11.1). Playwright MCP was not used because the user's live Chrome holds the profile, and that browser was left alone.
- **Raw data:** `obs-after.json`, with three sections: `loads` (19 page loads), `tabs` (22 tab visits) and `dialogs` (8 records).
- **Theme:** set with `localStorage['kazi-theme']` before each `goto`. `<html class>` matched the requested theme on all 19 loads.
- **Waits:** `load`, then `h1` visible, then `metric-card-grid` visible, then 2.5 s. No `networkidle`.
- **Contrast:** computed colours were resolved to sRGB through a canvas pixel, and WCAG ratios were taken against the nearest opaque ancestor background.
- **Grid:** row and column positions come from the unique `top` / `left` values of the tile wrapper boxes.

## Matters used (looked up with a read-only psql SELECT on `tenant_7d218705360b`)

| key | id | name | what it holds | users |
|---|---|---|---|---|
| rich | f04aaa84-880f-41c0-8a0d-1cd67dbdf99d | Ndlovu v RAF mwdrhfyu3ef3 | LITIGATION. Case no., court and opposing party set. Open fee note R 5 482,63. One trust FEE_TRANSFER of R 500 AWAITING_APPROVAL, so the balance is R 0,00. Court dates: MOTION 2026-12-12 **POSTPONED** and MOTION 2027-01-11 **SCHEDULED**. 4/4 time entries are invoiced. | Alice (lead), Carol (member) |
| estate | a9245382-b2c3-4150-9c97-cb8f58f17bfd | Estate Late Peter Moroka mwdrhfyu3ef3 | Trust R 241 521,50. Unbilled 6,0h (R 15 000,00). Outstanding R 125,00. No court dates. | Alice |
| closed | 9eb0f749-a1b7-4565-b28b-c509f0a2a56f | Ndlovu v RAF — Depth II mwdrm1q38f4b | CLOSED. Trust R 3 750,00. Unbilled R 5 000,00. | Alice |
| shared | a3b0ec9e-903f-46a8-a6ba-df650ddc6823 | Budget Test Project fzfy | The same seed matter as before/'s "shared": budget plus time, internal. | Alice, Carol |
| website | 58604884-c735-4d92-a2cd-a01599ee124e | Website Redesign | The same seed matter as before/'s "rich": Acme Corp, R 8 625,00 outstanding. | Alice |
| internal | fbfb09aa-9a06-4571-9147-fe3b8f52b5cf | Time Test Project fzfw | Internal, with time logged. | Alice, Carol |

The rich matter has a real upcoming SCHEDULED court date. It also exercises the postponement rule: the earlier POSTPONED row is skipped and the tile shows the later SCHEDULED one.

## Tiles rendered and grid layout

Every tile has `data-slot=stat-card`, `data-variant="compact"` (**compact variant observed**), `data-state="ready"`, `role=group` and `aria-labelledby`. Every tile is 98 px tall. No tile is a link. No value, label or hint overflows: `scrollWidth <= clientWidth` on all of them. `document` has no horizontal overflow on any load.

| user | matter | viewport | theme | tiles (value / hint) | grid |
|---|---|---|---|---|---|
| Alice | rich | 1024×768 | light | WIP R 0,00 (0,0h · 0 entries); Trust R 0,00; Outstanding R 5 482,63 (0 overdue); Court 11 Jan 2027 (Gauteng Division, Pretoria) | 2×2: WIP r1c1, Trust r1c2, Outstanding r2c1, Court r2c2. Tiles 309 px wide, card 672 px |
| Alice | rich | 1024×768 | dark | same | 2×2 |
| Alice | rich | 1280×800 | light | same | 4×1. Tiles 213 px, card 928 px |
| Alice | rich | 1440×900 | light | same | 4×1. Tiles 253 px, card 1088 px |
| Alice | rich | 1440×900 | dark | same | 4×1 |
| Alice | estate | 1440 / 1024 | light | WIP R 15 000,00 (6,0h · 1 entry); Trust R 241 521,50; Outstanding R 125,00 (0 overdue); Court "—" (None scheduled) | 4×1 / 2×2 |
| Alice | closed | 1440 | light | WIP R 5 000,00 (2,0h · 1 entry); Trust R 3 750,00; Outstanding R 0,00 (0 overdue); Court "—" (None scheduled) | 4×1 |
| Alice | shared | 1440 / 1024 | light | WIP R 0,00; Trust R 0,00; Outstanding R 0,00; Court "—" (None scheduled) | 4×1 / 2×2 |
| Alice | website | 1440 | light | WIP R 0,00; Trust R 0,00; Outstanding R 8 625,00; Court "—" (None scheduled) | 4×1 |
| Carol | rich | 1024×768 | light / dark | Trust R 0,00; Court 11 Jan 2027 (Gauteng Division, Pretoria) | 2×1. Tiles 309 px |
| Carol | rich | 1440×900 | light / dark | same | 2×1. Tiles 517 px |
| Carol | internal | 1440 light / 1024 dark | | Trust R 0,00; Court "—" (None scheduled) | 2×1 |
| Carol | shared | 1440 / 1024 | light | Trust R 0,00; Court "—" (None scheduled) | 2×1 |

How this compares with what was expected:

- **Alice (owner):** all 4 tiles, as expected.
- **Carol (member):** her caps are VIEW_LEGAL, VIEW_TRUST, MANAGE_DISBURSEMENTS, GENERATE_STATEMENT_OF_ACCOUNT and AI_EXECUTE, with no FINANCIAL_VISIBILITY or PROJECT_MANAGEMENT. WIP and Outstanding are omitted, Trust is present and Court is present. This is as expected.
- **Gaps:** the strip reflows with no holes. The 2-tile strip uses `grid-cols-2 @3xl:grid-cols-2`, and the 4-tile strip uses `grid-cols-2 @3xl:grid-cols-4`.
- **Breakpoint:** the `@3xl` (48rem) container breakpoint fires between card widths 672 and 928. So the 2×2 → 4×1 switch is reachable on real viewports.
- **3-tile and 1-tile cases: NOT OBSERVED.**
  - No seeded role lacks exactly one of the tile gates.
  - The trust and court modules are on for the whole org.
  - No read failed, so the error state ("Couldn't load") was not observed live either.

Contrast (WCAG):

| | label / hint | value |
|---|---|---|
| light | 7.57 | 17.79 |
| dark | 6.74 | 17.96 |

All pass AA. No dark-mode contrast issue was found on the tiles.

## Header DOM facts

These are identical across viewport and theme for each user/matter pair.

- **Header card:** `[data-testid=matter-header-card]` is present with `data-slot="detail-hero"`. It contains exactly 1 `h1`, which is `h1[data-testid=matter-name]` inside `#main-content`. The header sits above the tablist on every load.
- **Header facts (rich matter):** `matter-header-fields` is inside the card.

  | testid | dt | dd |
  |---|---|---|
  | `matter-header-field-case_number` | "Case no." | 2026/12345 |
  | `matter-header-field-court_name` | "·Court" | Gauteng Division, Pretoria |
  | `matter-header-field-opposing_party` | "·Opposing Party" | Road Accident Fund |

  The "·" separator sits inside the 2nd and 3rd `dt`. That comes from `HeaderFactsStrip`, which is unchanged by this diff, and the e2e assertion on the case_number `dt` passed. On the other matters `matter-header-fields` is absent because there are no values.
- **Status block:** `project-status-badge` reads "Active" plus the work type ("LITIGATION" / "ESTATES"). On the closed matter it reads "Closed" + "LITIGATION".
- **Subtitle (new):** client link or "Internal Matter", then fee earner, then "Opened 12 Nov 2026". For example: "Sipho Ndlovu mwdrhfyu3ef3 · Alice Owner · Opened 12 Nov 2026".
- **`header-lifecycle-actions`:**
  - Alice on active matters: `close-matter-btn` "Close Matter", `complete-project-btn` "Complete Matter", and `project-overflow-menu`.
  - Alice on the closed matter: `reopen-matter-btn` "Reopen Matter".
  - Carol: present but empty.
- **Identifier chip:** absent on every matter, because `reference_number` is NULL on all of them. The chip itself was not observed.
- **`project-closed-at`:** still absent on the CLOSED matter. This is pre-existing finding B1 from before/: the API returns no `closedAt`, so it is not a 641B regression.
- **`project-overflow-menu`:** still has no text and no `aria-label`. This is pre-existing finding B2.

## Strip on every tab (Alice, rich, 1440 light)

The tab bar is grouped. Details, Work, Fees, Client and Activity are menu triggers (`aria-haspopup=menu`). The walk visited all 22 destinations: every menu item plus Overview and Diary. On all 22:

- `matter-header-card` was present and visible;
- 4 tiles were shown;
- the header sat above the tablist;
- there was 1 `h1`;
- there were 0 console messages.

Screenshot of a non-Overview tab: `alice-rich-1440-light-tab-time-recording.png` (Fees · Time recording).

## Overview tab: before → after

The KPIDashboard cards are identical in set, order and values to before/ for the same seed matters:

| matter | before/ | after/ |
|---|---|---|
| shared, Alice | budget 2% · hours 2.0h · tasks 0% · trust R 0,00 · invoices R 0,00 | same |
| shared, Carol | budget 2% · hours 2.0h · tasks 0% · trust R 0,00 (no invoices card) | same |
| website (before/'s "rich") | hours 0.0h · tasks 0% · trust R 0,00 · invoices R 8 625,00 | same |

- `project-health-header`: "Healthy" on shared, and "Unknown · Client: Acme Corp · No tasks created yet" on website. Same as before/.
- `setup-progress-bar` is present.
- `matter-upcoming-deadlines-tile` renders:
  - on the rich matter it lists 2 rows, Dec 12 and Jan 11;
  - elsewhere it shows "No upcoming deadlines."

The `budget` prop (Fee estimate 2%) still renders, which confirms that the page-level single `getMatterBudget` read reaches KPIDashboard.

## Header text: before → after

The same testids and the same order of identity, status and actions. The changes are additive:

- the subtitle gains the fee earner and the opened date;
- the tile strip is added below.

Example, Alice / shared:

| | header text |
|---|---|
| before | "Budget Test Project 6crhActiveInternal MatterClose MatterComplete Matter" |
| after | "Budget Test Project fzfyActiveInternal Matter·Alice Owner·Opened 12 Nov 2026Close MatterComplete Matter" followed by the tile text |

## Console

- **Final run:** 0 errors, 0 warnings and 0 `pageerror` across all 19 loads, all 22 tab visits and all dialog opens. **0 hydration warnings.**
- **Earlier run of the same script:** one load (the first Carol load right after login, carol-rich-1024-light) logged `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch`. This is the dashboard's mock-auth userinfo fetch being aborted by the immediate `goto`. It is mock-auth infra, not the hero, and it did not recur in the final run.

## Create / Edit matter dialogs (641.10), Alice at 1024×768, light and dark

Screenshots:

- `alice-dialog-{create,edit}-1024-{light,dark}.png` (as opened);
- `-scrolled.png` (after wheel-scrolling to the footer).

What each dialog contains:

| | Create | Edit |
|---|---|---|
| title | "Create Matter" | "Edit Matter" |
| description | "Add a new matter to your organization." | "Update your matter's details." |
| section headings | "MATTER DETAILS", "CLASSIFICATION" (uppercase muted xs) | same |
| labels | Name; Description (optional); Due Date (optional); Client (optional); Reference Number (optional); Priority (optional); Work Type (optional) | same 7 |
| footer | Cancel / Create Matter | Cancel / Save Changes |
| other | Conflict Check panel ("Run a conflict check first →") above the footer | prefilled with the matter's values |

- Both dialogs also have the close "×" button.
- The labels match main's source label text. The only additions are the two section headings and the footer's new `border-t pt-4`; the computed 1px top border was observed.
- **Footer below the fold at 768 px:**
  - DialogContent's inner scroller (`max-h-[90vh] overflow-y-auto`, 689 px) holds content of 939 px for Create and 789 px for Edit.
  - So the footer starts at y=901 / 751, below the dialog's bottom edge at 729, and needs a scroll of 250 / 100 px.
  - Wheel scroll reaches it: footerTop 651 in both dialogs, and the submit button hit-tests to itself.
  - The same `max-h-[90vh] overflow-y-auto` is on main, so scrolling was already needed there. The two new headings add about 40 px of scroll.
  - Main was not rendered in this session, so its exact scroll amount was not measured.
- Dark mode: the inputs, selects, conflict panel and footer border are all legible. Nothing looked wrong.

## Things worth a look (critical read)

1. **Above-the-fold cost at 1024×768 for Alice (design trade-off, not a defect).**
   - Before: the tabs started at about y=330 (before/alice-rich-1024-light.png).
   - After: on the rich matter they start at about y=634. The 2×2 strip adds about 220 px, and the lifecycle buttons wrap below the title block.
   - The Overview content therefore starts below the fold on a 768 px-tall screen.
   - At 1440 the cost is about 120 px (tabs at y≈476 on the rich matter).
   - See `alice-rich-1024-light.png` and `alice-rich-1440-light-tab-time-recording.png`.
2. **The same figures appear twice on Overview.** Trust balance and Outstanding now show in the hero tiles and again as KPIDashboard cards just below them ("TRUST BALANCE", "OUTSTANDING FEE NOTES"). See `alice-rich-1024-light.png` and `alice-website-1440-light.png`. This may be intended per ADR-373, but it is visible duplication.
3. **The court tile disagrees with the Overview list on the rich matter.**
   - The hero says Next court date = **11 Jan 2027**, which is correct because only SCHEDULED rows count.
   - The Overview "Upcoming Deadlines" list shows **Dec 12** first, with no status marker; that row is POSTPONED.
   - The list's behaviour is unchanged (same rows, now passed in as a prop instead of fetched), but the two widgets side by side now visibly disagree.
4. **The court tile hint repeats the header facts.** The hint is the court name (+ judge), because the backend `describeCourtDate` puts that in `description`. On litigation matters "Gauteng Division, Pretoria" therefore appears in the facts strip and again in the tile.
5. **Empty court tile for screen readers.** The value renders `Unavailable` as "—" plus sr-only "Not available", and the hint says "None scheduled". A screen reader hears "Not available … None scheduled", which is slightly contradictory. Minor.
6. **Unbilled WIP shows zero next to billed time on the rich matter.** WIP reads R 0,00 · 0,0h · 0 entries while the Time tab shows 5h 15m billable. This is correct: the DB shows 4/4 entries invoiced. It can still read as surprising.
7. **Pre-existing, unchanged:**
   - B1: `project-closed-at` never renders.
   - B2: `project-overflow-menu` has no accessible name.

No visual regressions, truncation, misaligned tiles or dark-mode contrast failures were observed. Tile heights are uniform within each row, even when a tile has no hint (Trust).

## Page size

`wc -l frontend/app/(app)/org/[slug]/matters/[id]/page.tsx`: **384** lines. Main has 680. The logic moved to `matter-page-data.ts` (430) and `hero-tiles.ts` (147).

## Files

- **Full-page screenshots:** `<user>-<matter>-<viewport>-<theme>.png` (19). Each has a `-hero.png` clip of `[data-testid=matter-header-card]` (19).
- **Tab screenshot:** `alice-rich-1440-light-tab-time-recording.png`.
- **Dialog screenshots:** `alice-dialog-{create,edit}-1024-{light,dark}[-scrolled].png` (8).
- **Data and driver:** `obs-after.json`, `obs-after.cjs`.
