# 665B OBS after-fix: audit pager focus, collapse parity, Events heading name

- **When:** 2026-11-26, about 17:30Z to 17:49Z.
- **PR / head:** #1871, `a8720ea0c` (fix commit after OBS-after measured `4d9ce44cd`). Branch `epic-665B/settings-firm-ii`.
- **Scope:** read-only. URL changes, row expand and filter toggles only. Nothing was saved, no SQL was run, and no product code was touched.
- **Browser:** the Playwright library, headless Chromium from the main checkout's `frontend/node_modules`. Not the MCP. Node 20.11.1 (nvm), DPR 1, reduced motion, Africa/Johannesburg, theme via localStorage `kazi-theme`, rail expanded, Alice signed in through `/mock-login` "Sign In".

## Provenance

- `e2e-frontend` was created `2026-11-26T17:20:00.395Z` from image `sha256:116fe413f67b…`. Its compose `working_dir` label is `/Users/…/worktree-epic-665B/compose`.
- `e2e-backend` was created `17:20:00.317Z` from image `sha256:e7fd89e4…`, with the same `working_dir` label.
- Worktree HEAD is `a8720ea0c439e47e390104f3b4a360bafaed1192`, committed at 17:17:43Z, which is before the containers were created. `git status --short frontend packages` was empty.
- `curl /mock-login` returned 200.
- **Data:** a fresh stack, after the legal and settings e2e suites ran. There were 258 audit events (6 pages of 50).

## Script

`scripts/07-after-fix.cjs` is new. It reuses the main checkout's `../../../661B/walk/scripts/common.cjs` for login, `newCtx`, theme and console watch. It does not use the worktree's `lib.cjs`, because that file would create an output directory inside the worktree.

The script ran three times:

1. The first run crashed on an HTML comment node in the h2.
2. A first full run followed, but its console summary counted labels instead of loads.
3. The run recorded here exited 0. Its console is in `/tmp/665b-afterfix-run.log`, outside the repo.

All runs gave the same results, apart from data-dependent 404 counts (41 vs 40).

## Results

| # | Check | Result | Numbers |
|---|---|---|---|
| 1 | Keyboard focus kept on paging (1440 light and 1024 light) | **PASS** | The button was focused with `.focus()` and activated with **Enter**: Next (1→2), Next (2→3), Previous (3→2). After the URL and pager text committed, and again after networkidle + 400 ms, `activeElement` was the `BUTTON` with the right name, connected, inside `nav[aria-label="Audit log pagination"]`. It was **never `body`**. A JS marker set on the button before Enter was still on `activeElement` after every step, so it is the **same DOM node**: the pager is no longer remounted. |
| 2a | Collapse on page change | **PASS** | Row `audit-row-fefb7b13…` was expanded on page 1: 1 details row, visible, `aria-expanded="true"`. Next ("Page 2 of 6"): **0 details rows, 0 expanded toggles**. Previous ("Page 1 of 6"): **0 details rows**; the same row is present with `aria-expanded="false"` and the label "Expand details". The expansion did not reopen. |
| 2b | Same-page filter change | Recorded: **stays expanded** | Re-expanded the same row (severity CRITICAL), then toggled `severity-toggle-CRITICAL`. URL `?tab=audit-trail&severities=CRITICAL`, "Page 1 of 1". The row is still at index 0, its details row is still present (1), and `aria-expanded="true"`. This is the expected behaviour: no page change, so no reset. |
| 3 | Events h2 accessible name | **PASS** | `getByRole('heading',{level:2,name:/^Events \d… total$/})` = **1**. The no-space form `/^Events\d/` = **0**. ARIA snapshot: `heading "Events 258 total" [level=2]`. `textContent` = `"Events 258 total"`. Child nodes: `#text "Events"`, a comment, `#text " "`, `<span class="ml-2 font-mono …">`. |
| 3 | Gap "Events" → count | measured | The right edge of the "Events" glyphs is at 625.64 px; the span starts at 637.94 px, so the gap is **12.30 px**. That is the span's `margin-left` of 8 px plus one space of 4.29 px (600 18px sans). Before the fix it would have been 8 px (ml-2 only). See `crop-events-heading-1440-light.png`. |
| 4 | Page size and pager | **PASS** | 50 `tr[data-testid^=audit-row-]` rows. "Page 1 of 6". Previous is disabled on page 1. |
| 4 | Actor filter persists to page 2 | **PASS** | Actor = Alice Owner gives `…&actorId=8d1177e4…` and "Page 1 of 4". Next gives `…&actorId=…&page=1` and "Page 2 of 4", with actors on the page = ["Alice Owner"]. |
| 4 | A filter change removes `page` | **PASS** | INFO toggled from page 2 gives `…&actorId=…&severities=INFO` with no `page`, and "Page 1 of 4". The actor filter is kept. |
| 4 | Zero-result pager | **PASS** | `entityId=00000000-0000-4000-8000-000000000665` gives a pager reading exactly **"No events"** (`getByText('No events',{exact})` = 1). Previous and Next are both disabled. |
| 4 | First `[data-testid^=audit-row-]` | **PASS** | `TR` |
| 4 | No page h-scroll | **PASS** | `scrollWidth === clientWidth` in all 8 states: 1024 and 1440, light and dark, each plain and with row 1 expanded. Values: 1024/1024 and 1440/1440. See `crop-row-expanded-1024-dark.png`. |
| 5 | Console, plain loads | **PASS** | 18 plain loads across the audit, data-protection and data-requests tabs, at 1024/1440 and light/dark. **0** non-K-45 errors, **0** warnings and **0** hydration messages, both on plain loads and during interactions. |
| 5 | K-45 (known, pre-existing, backlog) | listed | There were 40 "Failed to load resource: 404" console errors, all from entity-link prefetches. They occurred only at 1440, where the Entity column is in view. The routes that returned 404: `/matters/{uuid}/closure`, `/settings/data-protection/requests/{uuid}`, `/documents/{uuid}`, `/trust-accounting/transactions/{uuid}` and `/settings/roles/{uuid}`. These are exactly the five routes K-45 lists. |

## Files

| File | Contents |
|---|---|
| `scripts/07-after-fix.cjs` | All checks |
| `checks.json` | Single line holding the full results: the focus trace per step (`beforeEnter`, `afterUrlAndPager`, `afterSettle`), collapse, heading, regression, overflow and console |
| `crop-pager-focus-1440-light.png` | The pager after the last step: "Page 2 of 6" with the focus ring on Previous |
| `crop-events-heading-1440-light.png` | The "Events 258 total" heading at 1440 light |
| `crop-row-expanded-1024-dark.png` | Row 1 expanded at 1024 dark, with no page overflow |
