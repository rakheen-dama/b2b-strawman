# 665B OBS-after: Settings › Firm II (Audit trail, Data protection, Data requests): layout merge gate

- **When:** 2026-11-26, 16:54Z to 17:10Z.
- **PR / head:** #1871, `4d9ce44cd` (branch `epic-665B/settings-firm-ii`).
- **What changed:** no product code was changed and no SQL was run. Data was changed only through the UI and reversibly (the same saves as before), and restored.
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090). Only the frontend was rebuilt, so the data is the same as in the before capture.
- **Browser:** the Playwright library, headless Chromium from the main checkout's `frontend/node_modules`. Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, rail expanded, theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).

## Provenance

- **Rebuild:** `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` from this worktree. Exit 0; the log is in `../../../../archive-665B-after/rebuild.log`, outside the repo. `curl /mock-login` → **200**.
- **Frontend container before the rebuild:** created `2026-11-26T15:46:02.895Z`, image `sha256:cf0194316993…`, compose `working_dir` = `worktree-epic-665A/compose` (the before state).
- **Frontend container after the rebuild:** `e2e-frontend` created `2026-11-26T16:54:14.810Z`, image `sha256:fa19e1c09fd2…` (image created `16:54:13.256Z`). Its compose `working_dir` label is **`/Users/…/worktree-epic-665B/compose`**.
- **Worktree:** HEAD `4d9ce44cd87c135ffe0c903c12e897f5dc9add0b`. `git status --short frontend packages` was **empty** at build time.
- **Backend:** `e2e-backend` was **not recreated**: created `15:24:49Z`, image `sha256:e7fd89e4…`, the same as before. Its image was rebuilt from cache only.
- **Data:**
  - Settings, data requests, personas, org roles and members are **identical** to `before/discover-post.json`.
  - There are now 282 audit events (before: 269–280). The extra events are the gate's own role assignments, saves and 403 probes.

## Scripts (`scripts/`)

**Copied unchanged** from `../before/scripts/`; `diff` is empty: `lib.cjs`, `00-discover.cjs`, `01-capture.cjs`, `02-roles.cjs`, `04-dataprot.cjs`, `05-assemble.cjs`. They were run with `PHASE=after`, which `lib.cjs` already supports. Output goes to `../after/` and raw tiles to `archive-665B-after/` (a sibling of the worktree).

**Edited (selector superset only, so it still matches the before DOM):** `03-audit.cjs` and `03b-audit-extra.cjs`. The Events card is now a `SettingsSection` (`data-slot="settings-section"`, title `h2`), not `data-slot="card"` + `card-title`, and the empty copy is an `EmptyState` (`data-slot="empty-state"`). Without the edit, 03's pending-card screenshot locator would time out. The exact diff:

```diff
--- before/scripts/03-audit.cjs
+++ after/scripts/03-audit.cjs
-  const cards = [...main.querySelectorAll('[data-slot="card"]')];
-  const events = cards.find((c) => /^Events/.test(txt(c.querySelector('[data-slot="card-title"]') || c)));
-  const title = events && events.querySelector('[data-slot="card-title"]');
+  const cards = [...main.querySelectorAll('[data-slot="card"],[data-slot="settings-section"]')];
+  const events = cards.find((c) => /^Events/.test(txt(c.querySelector('[data-slot="card-title"],h2') || c)));
+  const title = events && events.querySelector('[data-slot="card-title"],h2');
-  const empty = events && [...events.querySelectorAll("p")].find((p) => /^(No |The audit log)/.test(txt(p)));
+  const empty = events && (events.querySelector('[data-slot="empty-state"]') || [...events.querySelectorAll("p")].find((p) => /^(No |The audit log)/.test(txt(p))));
-      const card = p.locator('[data-slot="card"]').filter({ has: p.locator('[data-slot="card-title"]', { hasText: /^Events/ }) });
+      const card = p.locator('[data-slot="card"],[data-slot="settings-section"]').filter({ has: p.locator('[data-slot="card-title"],h2', { hasText: /^Events/ }) });
--- before/scripts/03b-audit-extra.cjs
+++ after/scripts/03b-audit-extra.cjs
-  const title = p.locator('[data-slot="card-title"]', { hasText: /^Events/ });
+  const title = p.locator('[data-slot="card-title"],[data-slot="settings-section"] h2', { hasText: /^Events/ });
-  ... t.closest('[data-slot="card"]') ... t.closest('[data-slot="card"]') ...
+  ... t.closest('[data-slot="card"],[data-slot="settings-section"]') ... (both occurrences)
```

**New, after-only** (the before run had no equivalent):

| Script | What |
|---|---|
| `06-after-checks.cjs` | Page overflow at 1024/1280/1440 × light/dark (+ the audit trail with row 1 expanded); K-109 at 390 light/dark; K-32 composited contrast (after, plus the before colour reproduced on the same canvas); expand semantics; a white/slate-950 box and contrast scan per tab |
| `06b-zero-and-names.cjs` | Zero-results selector counts, the exact pager text, the audit h2 accessible names |
| `06c-k109-widths.cjs` | Processing Register header title / description / action-column widths at 390/1024/1280/1440 |
| `05b-assemble-after.cjs` | Writes `after-checks.json`, `sheet-after-checks.png`, `crop-k109-390-{light,dark}.png` |

- **Executed:** `00 pre` → `01 alice` → `01 carol` → `02 check` → `02 assign "665A OBS Oversight"` → `01 carol-cr-holder` → `02 assign "665A OBS No Oversight"` → `01 carol-cr-lacker` → `02 assign "Member"` → `03` → `03b` → `04` → `06` → `00 post` → `05` → `06b` → `05b` → `06c`.
- Every script exited 0, and each ran once.
- `06b` contains one no-op line (`R.headingNames`, always undefined, so it is dropped from the JSON). It was left in rather than editing a script after its run.

## Results (before → after)

| # | Check | Before | After | Result |
|---|---|---|---|---|
| 1 | Gating per persona | Alice: 8 tabs. Carol / lacker: General, Subscription + notice "You don't have access to {Tab} — showing General instead." on all 3. Holder: General, Audit trail, Subscription; audit rendered, other two notice. Legacy routes 200, gated the same | **Identical**: tab bars, `gateOutcome`, notice text and the selected tab for 4 personas × 3 tabs × 4 variants; all 12 legacy-route records are byte-identical | **PASS** |
| 2 | One h1 in `#main-content` | 1 on all 48 page states. h1 "Audit log" / "Data Protection" / "DSAR Requests" / "General" (fallback) | **1 on all 48**, same text in every state | **PASS** |
| 2b | Heading diff | Audit: `[h1 Audit log]` only (cards were divs). DP: h1 + 6 h2. DSAR: h1 only | Audit: **h1 Audit log, h2 Preset, h2 Filters, h2 "Events282 total"** (the card titles became h2 with the same text). DP: **identical** (h1 + Jurisdiction / Information Officer / Data Subject Access Requests / Retention Policies / Processing Register / PAIA Manual). DSAR: **identical**. The zero-result state adds an `EmptyState` **h2** "No audit events in this range." | PASS (nit **F-665B-6**: the Events h2's accessible name is "Events289 total", with no separator) |
| 3 | Audit paging | See below | See below | **PASS** |
| 4 | No page h-scroll (1024/1280/1440 × light/dark) | 0 at 1024 and 1440 (1280 not captured) | **0 in all 18 tab × viewport × theme states**, and 0 with row 1 expanded at 1024/1280/1440 (light + dark) | **PASS** |
| 5 | K-109 at 390 | "Add Activity" right = 422, page +32 px (register) | "Add Activity" right **349**, "Export Register" right 349; the buttons **wrap** (tops 107 / 147), both inside the action column (142–349); page overflow **0**, light and dark | **PASS** (residual layout nit **F-665B-7**: the description column is 93 px / 8 lines at 390) |
| 6 | K-32 contrast (firm light, composited on the canvas) | `text-teal-600` → `#00a38f` on `#fdfeff` = **3.13:1** (the register value; reproduced on the after canvas with an injected `text-teal-600` span) | `text-(--button-accent)` → `#00776a` on `#fdfeff` = **5.41:1**, both for the audit entity link and for "View DSAR Requests →". Dark is unchanged (`dark:text-teal-400` `#00d5be` on `#14171c`, 9.63:1) | **PASS** |
| 7 | Save models | See below | See below | **PASS** |
| 8 | Console | 60 plain loads: 0 errors / 0 warnings / 0 hydration. 31 errors, all "Failed to load resource: 404" (K-45 prefetch) in 03/03b | 60 plain loads: **0 / 0 / 0**. **37** errors (26 in 03, 11 in 03b), plus 6 in 06; **all** "Failed to load resource: 404". 03b's URLs are all K-45 routes (`/settings/roles/{uuid}` ×3, `/settings/data-protection/requests/{uuid}`, `/documents/{uuid}` ×2, `/matters/{uuid}/closure`). **0 hydration** anywhere | **PASS**: the count rose only because more K-45 links sit on the shifted page 2 (data drift) |
| 9 | SEL-COMPAT | Every strict selector = 1 | **513 selector counts compared; every strict one is still exactly 1.** 7 diffs, all data drift from newer events: page-1 CRITICAL pill 1→0, entity-cell-link 19→16/17, entity-cell-literal 31→34/33, Sensitive preset rows 7→10. Interactive (preset option, event-type search + both spec keys, Log DSAR dialog fields) are identical | **PASS** |
| 10 | Dark mode | DSAR section box was `bg-white dark:bg-slate-950` | **0** visible white or slate-950 boxes ≥ 40×40 in `#main-content` on all 3 tabs, light and dark. The DSAR section is now `bg-card` (`sheet-after-checks.png`). Text < 4.5:1: 0 in light; dark data-protection / data-requests 0; dark audit 21 = `text-slate-500` "N total" and entity short ids at 3.80:1. That is **K-57 (backlog, pre-existing; these classes are unchanged by 665B)** | **PASS** |

### 3. Audit log paging (`audit-paging.json`, `zero-and-names.json`, `after-checks.json` → `expand`)

| Item | Before | After |
|---|---|---|
| Page size | 50 | **50** |
| Pager nav | `nav[aria-label="Audit log pagination"]`, "Page 1 of 6", Previous disabled | **same**. The kit's `DataTablePageLinks` uses `<button>` Previous / Next (onPageChange mode), Previous `disabled` on page 1 |
| Next / Previous URL | `?page=1` "Page 2 of 6" / the key removed | **`…&page=1` "Page 2 of 6"** / **`?tab=audit-trail`** (key removed) |
| Filter across pages | Actor = Alice → `actorId=…`, Next → `&page=1`, all 50 rows Alice | **same** (208 total, "Page 2 of 5", actors ["Alice Owner"]) |
| Filter change resets page | INFO toggle from page 2 → `page` removed | **same** (`…&severities=INFO`, "Page 1 of 4"). Entity type from page 2 → `entityType=customer`, "Page 1 of 1"; Clear filters → "Page 1 of 6" |
| Pending (RSC held 3 s) | "Loading…" span in the Events title; opacity 1, no aria-busy, Next enabled | **same** at 151 / 603 / 1 504 ms; the URL commits at ~3.5 s (`crop-audit-pending.png`) |
| Zero results | "No audit events in this range. Try widening the date range or changing filters." (one `<p>`, 84 px, card 190 px); pager "No events"; both buttons disabled | **Same words**, now a compact `EmptyState` (h2 title + p description, 164 px, section 339 px); the pager label is exactly **"No events"** (`getByText("No events", {exact})` = 1, "Page 1 of 0" = 0); Previous and Next disabled; `/No audit events/i` = 1. The same holds for `from=2030…` and the Compliance group preset (banner text identical) |
| Out of range `?page=99` | "Page 100 of 6" + "The audit log is empty…" (F-665B-2) | **unchanged** (parity; F-665B-2 stays on the backlog) |
| Expand | "Expand details" → "Collapse details", 1 details row | `[data-testid^=audit-row-]` `.first()` = **`<tr data-testid="audit-row-676c9f24…">`** (the row, before and after expanding). It has exactly 1 "Expand details" button. After the click, 1 **`div[data-testid="audit-row-details-676c9f24…"]`** (inside the kit's detail `tr`, matching the row's id) containing "Object(6) org_role: owner … Source: API IP: 172.18.0.7 Agent: node". The toggle reads "Collapse details". **Next → 0 details rows and 0 expanded toggles**; back on page 1 → 0 |
| Invalid entity id / export menu / filter options / event-type keys | as in before | **same** ("Enter a full ID" + aria-invalid; Download CSV / PDF; 82 event types; the spec keys resolve to 1 row each) |

### 7. Save models (`saves.json`; Alice 1440 light)

| Model | Before | After |
|---|---|---|
| Information Officer (per-card Save) | "Saved successfully." `<span class="text-sm text-teal-600 dark:text-teal-400">`, no role / aria-live; Save disabled while pristine and after | **Identical text and element.** It is now in the section footer's status slot. ≤ 82 ms, ~400–450 ms total; restored to "Test Officer 5qp3" |
| Retention row (Audit Event, unchanged) | Button disabled + spinner ~100 ms, no message, 2 server actions 200 | **same** (disabled + spinner at 18 ms, enabled at 126 ms; no alert; 2 × 200) |
| Processing Edit → Update | No message, dialog closes | **same** (738 ms; "Financial Records" this time, the first row after F-665B-3's reorder) |
| Execute Purge / Delete activity | `window.confirm` "Are you sure you want to execute the retention purge? This action cannot be undone." / "Delete this processing activity?" (dismissed) | **Both still fire with identical text; dismissed** |
| Add Activity / Log DSAR dialogs | Fit at 1024×768: Add/Edit 66–702 (submit 633–669), Log DSAR 105–663 (submit 594–630) | **Identical boxes at 1024** (and 1440: Add 132–768, Log 171–729); every submit is in the viewport. Fields and labels are identical |
| Export Register | toast "Export register is coming soon." | **same** |
| Restore | values identical, order changed (F-665B-3) | `discover-pre` vs `discover-post`: settings, data requests, personas, org roles and members **identical**. Retention policies are identical in values and order. Processing activities are identical as a set; "Financial Records" moved last (F-665B-3, pre-existing) |

- **Display change (intended per the commit; not a COPY-FREEZE word change):** the DSAR "Received" cell went from "Nov 26, 2026" (browser locale) to **"26 Nov 2026"** (SA `DateCell`). DSAR headers are now uppercase (kit header style) with the same words.
- **Pre-existing and unchanged:** F-665B-1 (stored `delete` still shows "Anonymize"), F-665B-5 (Jurisdiction combobox still has no aria-label / labelledby).

### 4. Table scrollers, visible / full width px (Alice; page overflow 0 everywhere)

| Table | 1024 light | 1024 dark | 1280 light | 1280 dark | 1440 light | 1440 dark |
|---|---|---|---|---|---|---|
| Audit events | 446/827 → **446/827** | 454/827 → **454/827** | — → **654/827** | — → **662/827** | 814/827 → **814/827** | 822/827 → **822/827** |
| Retention Policies | 444/631 → **446/631** | 452/631 → **454/631** | — → **654/654** | — → **662/662** | 812/812 → **814/814** | 820/820 → **822/822** |
| Processing Register | 444/1156 → **446/1156** | 452/1156 → **454/1156** | — → **654/1156** | — → **662/1156** | 812/1156 → **814/1156** | 820/1156 → **822/1156** |
| DSAR requests | 494/634 → **446/637** | 502/634 → **454/637** | — → **654/654** | — → **662/662** | 862/862 → **814/814** | 870/870 → **822/822** |

- **Audit trail with row 1 expanded:** the scroller is still 446/827 (1024), 654/827 (1280) and 814/827 (1440), and the page overflow is 0. Removing the `contain-inline-size` wrapper did not let the details row widen the page (`sheet-after-checks.png`, 1024 and 1280 tiles).
- **DSAR loses 48 px of visible width at 1024**, because the table now sits in a padded section (hidden width 140 → 191 px; DEADLINE / ACTIONS were already off-screen before). It still scrolls inside its frame. Noted, not a FAIL.
- **Document heights (light, before → after):**

| Tab | 1024 | 1440 |
|---|---|---|
| Audit trail | 3 265 → 3 232 | 2 899 → 2 914 |
| Data protection | 2 356 → 2 453 | 2 142 → 2 239 |
| Data requests | 824 → 824 | 956 → 956 |

- **K-109 widths (`k109-widths.json`):** Processing Register description 93 px (390), 150 px (1024), 358 px (1280), 518 px (1440); the action column is 207 / 288 px.

## Personas / Carol restored

- Carol was assigned through the UI (Team › Carol › Role › Save) in this order: Member → 665A OBS Oversight (`/api/me/capabilities` = [TEAM_OVERSIGHT]) → 665A OBS No Oversight ([PROJECT_MANAGEMENT]) → **Member**.
- After the final step, `GET /api/me/capabilities` (Carol's token) = role **Member**, isAdmin false, isOwner false, [AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST]; `/api/members/{id}/capabilities` overrides `[]`. `discover-post.json` confirms it.
- Both custom roles have 0 members again. Nothing was created.

## Files

| File | Contents |
|---|---|
| `sheet-{audit-trail,data-protection,data-requests}-{alice,carol,custom-role}.png` | Full page, cols 1024 light / 1024 dark / 1440 light / 1440 dark (custom-role: holder row, lacker row) |
| `sheet-audit-behaviour.png`, `crop-audit-pending.png`, `crop-audit-zero-results.png`, `crop-audit-out-of-range.png` | As before |
| `sheet-dataprot-interactions.png`, `crop-defect-retention-delete-as-anonymize.png`, `sheet-roles-assign.png` | As before |
| `sheet-after-checks.png`, `crop-k109-390-light.png`, `crop-k109-390-dark.png` | K-109 at 390, audit row expanded at 1024/1280, the dark DSAR section |
| `measurements.json`, `sel-compat.json`, `console.json`, `audit-paging.json`, `saves.json`, `discover-{pre,post}.json` | Same shape as before |
| `after-checks.json`, `zero-and-names.json`, `k109-widths.json` | After-only checks |

Raw full-page PNGs are in `/Users/rakheendama/Projects/2026/archive-665B-after/`, not in the repo.

## New findings (`../found.md`)

- **F-665B-6** (introduced, LOW a11y nit): the Events h2's accessible name is "Events289 total" ("…totalLoading…" while pending).
- **F-665B-7** (residual K-109, LOW, kit-level): at 390 the Processing Register title and description get 93 px beside the 207 px action column. K-109's button acceptance is met.
