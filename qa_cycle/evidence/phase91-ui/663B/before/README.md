# 663B OBS-before — empty detail tabs (task 663.8)

Captured 2026-11-26 08:44 UTC on the running E2E mock-auth stack (frontend :3001, backend :8081, mock IDP :8090).
- **Stack provenance:** the `e2e-frontend` image was built 2026-11-26 07:51 UTC from the 663A worktree. Its frontend code equals `main` @ `799c20c53`, which is before 663B. The image id is recorded in `measurements.json` (`frontendContainer`).
- **Stack data:** the stack still holds what the 663A run left: the legal suites plus the 663A OBS-after data. There was no stop, rebuild, reseed or teardown.
- **`scriptsHead`:** `measurements.json` records `scriptsHead` = `e405f930a`. That is the worktree HEAD at capture time, the 663B code commit, which is **not** deployed. It shows which script copy ran. It is not the code under test.

Personas: **Alice** (owner) and **Carol** (member). Viewport: 1440×900, light and dark. The theme is set through localStorage `kazi-theme`, and dark mode was confirmed through `html.dark` on every capture.
Reduced motion is on and the timezone is Africa/Johannesburg. Chromium 147.0.7727.15 ran through `@playwright/test` 1.59.1.

**Method:** a Playwright *library* script (`chromium.launch()`), not Playwright MCP, because MCP shares the user's Chrome profile.
- **Login:** reuses `661B/walk/scripts/common.cjs` through `require`, unedited. Alice signs in through the `/mock-login` UI. Carol uses the `mock-auth-token` cookie.
- **Per capture:** the script navigates to `?tab=…`, waits for network idle (it was reached on all 24 captures), then waits for the active `[role=tabpanel]`.
- **Locating the box:** it is the shared `[data-slot="empty-state"]` inside the active tab panel whose title text matches.
- **Measurements:** `getBoundingClientRect()`, `data-variant`, the title tag and text, and computed styles: box padding and gap; title font-size, weight, line-height and family; icon tile size.
- **outerHTML:** normalised (radix/React ids become `RID`, whitespace is collapsed), then hashed with sha256.
- **Screenshot:** the box is scrolled into view (`block: center`), then a viewport screenshot is taken.

Raw tiles and per-capture JSON are in `.tmp/663B-raw/before/`, which git ignores (`.gitignore:128 .tmp/`).

## Records used (no data created)
| Record | Id | Why it is empty |
|---|---|---|
| Matter "663A OBS empty-fields matter" (no client; Carol is a member) | `e355793a-2f34-487a-ad2d-b8c4641af74b` | 0 documents, no fee estimate (GET `/budget` returns 404), time summary 0 min / 0 entries, no linked client |
| Client "663A OBS empty-fields client" | `ca36f657-9925-4a08-966f-c64e7dcce501` | 0 `CUSTOMER_OVERRIDE` billing rates |

The 663A OBS-**after** run created both records. The ids named in the brief (`f34b1ffa…` / `facb8d69…`, from 663A OBS-before) **no longer exist** on this stack: GET returns 404.
- **No data was created or changed.** All API use was read-only GET calls with each persona's own token. `measurements.json` › `preconditions` records the result for each persona.
- **Lookup:** `capture.cjs` finds both records **by name** at run time. You can override this with `MATTER_ID` / `CLIENT_ID`.

## Substitution: Activity → matter Clients tab
The `activity-feed.tsx` EmptyState renders only when the first activity page is empty, or when the fetch fails (`activity-feed.tsx:21`). Through the UI it cannot be reached, because **every matter records a `project.created` event when it is opened**.
- **Evidence:** GET `/activity` on all 12 matters returned 1–17 items. This matter has 2 items: `project.created` and `project_member.added`.
- **Filtered feed:** when a filter matches nothing, `activity-feed-client.tsx:123` shows plain text, not EmptyState.
- **The Activity tab is still captured** (`sheet-activity.png`). It shows the populated feed, so the after run proves that the tab is unchanged.
- **Substitute:** the nearest 663B site that can be shown empty is **`components/projects/project-customers-panel.tsx`** ("No linked clients") on the same matter's **Clients** tab. It is in the same page, the same tab-panel container and the same width, and it is also swept to `variant="default"` in 663B.

## Sheets
Each sheet has 2 rows (Alice, Carol) × 2 tiles (1440 light, 1440 dark), shown at 0.5×. Each caption gives the title, variant, w×h, title tag and font-size, padding and gap, and the sha256 prefix.

| File | Surface (`?tab=`) | Alice | Carol |
|---|---|---|---|
| `sheet-activity.png` | Matter › Activity (`activity`) | populated feed, no empty state | populated feed, no empty state |
| `sheet-budget.png` | Matter › Fees · Fee estimate (`budget`) | "No fee estimate set": `default`, 324 px, `<h2>`, **Set fee estimate** button | same box with no button (`canManage` false), 268 px |
| `sheet-documents.png` | Matter › Documents (`documents`) | "No documents yet": `default`, 268 px, `<h2>` | identical |
| `sheet-time-summary.png` | Matter › Time (`time`) | "No time tracked yet": `default`, 248 px, `<h2>` | identical |
| `sheet-customer-rates.png` | Client › Rates (`rates`) | "No client rate overrides": `default`, 324 px, `<h2>`, **Add Override** button | **gated**: the page passes `ratesPanel` only when `isAdmin` (`clients/[id]/page.tsx:347`), so `?tab=rates` falls back to Overview |
| `sheet-matter-clients.png` | Matter › Clients (`clients`), the Activity substitute | "No linked clients": `default`, 248 px, `<h2>` | identical |

## Measurements (`measurements.json`, single line)
Every box measured: `data-variant="default"`, title `<h2>` with font-size 18px, weight 600 and line-height 28px (Sora). The box has padding 64px top and bottom, 0 left and right, a gap of 12px, and a 48×48 icon tile. Light and dark produce the same outerHTML hash.

| Surface | Persona | w × h (light / dark) | outerHTML sha256 |
|---|---|---|---|
| budget | Alice | 1080 / 1088 × 324 | `ec78d65d22cbe4531d563e52f9b223da4cf7ad38e8fa6bae0a444722e1aacd1d` (Radix dialog-trigger id normalised) |
| budget | Carol | 1080 / 1088 × 268 | `9a136d36812cb45c35bcc82e6ce11fca859f6037894fed2be03e1b8dfa52f8e9` |
| documents | both | 1080 / 1088 × 268 | `daad91a73136488723f74829f1f52304a386950c712c594b2ea1461c1f742325` |
| time-summary | both | 1080 / 1088 × 248 | `883ac7ee0bd2773ca10532dd409594fd4e531715f5c4fc3d2a03d0eb2aa4582d` |
| customer-rates | Alice | 1128 / 1136 × 324 | `d0471c32cea52d10c25bb54b1b6f7fce17eff4fa424c5951e73db0269539be2a` |
| matter-clients | both | 1080 / 1088 × 248 | `82dd1d38480bfb37e260f5339c6df48ef42a579ff001f4c0e8a8fa8a74ad6355` |

The 8 px width difference between light and dark is the same on every surface and for both personas, so it is page-level, not a property of the EmptyState. AFTER should compare light with light and dark with dark.
**Expected AFTER for an invisible sweep:** identical variant, w×h and sha256 on every row.

## Console (`console.json`)
24 captures: **0 errors, 0 warnings, 0 hydration warnings**.

## Re-run for AFTER
The scripts in `before/scripts/` must not be edited after this run. Once the stack runs the 663B frontend, run this from the worktree root:

```bash
export PATH=$HOME/.nvm/versions/node/v20.11.1/bin:$PATH NODE_OPTIONS=""
cd qa_cycle/evidence/phase91-ui/663B/before && PHASE=after node scripts/capture.cjs && PHASE=after node scripts/assemble.cjs
```

The run writes `663B/after/{measurements,console}.json` and `663B/after/sheet-*.png`, and keeps its raw tiles in `.tmp/663B-raw/after/`.
- **Records:** both records are found by name. If the stack was reseeded and they are gone, recreate them through the UI with 663A's `after/scripts/03-setup-fields.cjs`, or pass `MATTER_ID` / `CLIENT_ID` for any matter that has no documents, budget, time or client, and any client with no rate overrides. Carol must be a member of that matter.
- **Playwright fallback:** if the worktree has no `frontend/node_modules`, the scripts load `common.cjs` (and so Playwright) from the main checkout.
