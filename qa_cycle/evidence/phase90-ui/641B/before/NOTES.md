# 641B observe-first "before" (task 641.7)

Captured 2026-11-12 ~16:06Z against the e2e mock-auth stack (frontend :3001, backend :8081), which was built from main-equivalent code. The live DOM confirms this: the header is main's `Card` with no hero tiles.

- **Driver:** `obs-before.cjs`, a Node Playwright library script (`chromium.launch()`, nvm node v20.11.1). Playwright MCP was not used because it returned "Browser is already in use" (that is the user's live session, and it was left alone).
- **Raw data:** `obs-before.json`, one record per page load (DOM facts, console messages, HTTP status).
- **Theme:** set with `localStorage['kazi-theme']`, then the page was reloaded. The head init script applies it before first paint. Verified with `<html class>`: `light` or `dark` matched the request on all 14 loads. The header toggle was not used: it is a Light/Dark/System segmented control inside the user menu, and localStorage is the same key it writes.
- **Waits:** `waitUntil: 'load'`, then the first `h1` visible, then `metric-card-grid` visible, plus 2.5 s for hydration and late console messages. No `networkidle`.
- **Artefact:** in the full-page shots at 1024×768 the dark sidebar background stops at viewport height. That is the sticky sidebar under `fullPage`, not a product defect.

## Matters used

| key | id | URL | why |
|---|---|---|---|
| shared | `e56794da-53ad-41aa-83e1-8d65ee33dc50` | `/org/e2e-test-org/matters/e56794da-53ad-41aa-83e1-8d65ee33dc50` | "Budget Test Project 6crh". The only kind of matter Carol can open (she is a project member of just 3 test matters). Has a budget and 2.0h time; no client (internal). |
| rich | `1960b1cd-0efd-4e2e-a55b-3fdcebf53365` | `/org/e2e-test-org/matters/1960b1cd-0efd-4e2e-a55b-3fdcebf53365` | "Website Redesign". Has a client (Acme Corp) and fee notes (R 8 625,00 outstanding). No trust or budget. Alice only. |
| closed | `71588d0d-c986-4ff3-8eb4-55f8ca2e7a4c` | `/org/e2e-test-org/matters/71588d0d-c986-4ff3-8eb4-55f8ca2e7a4c` | "Ndlovu v RAF — Depth II". CLOSED, LITIGATION, trust R 3 750,00. Alice only, 1440 light. |

No matter in this seed combines a client, trust and fee notes. No matter has case number, court or opposing-party values, so no header facts appear anywhere. No matter has court dates, so every page shows "No upcoming deadlines."

## Screenshots (full page, Overview tab is the default selected tab)

| file | user / matter | viewport | theme |
|---|---|---|---|
| alice-shared-1024-light.png | Alice / shared | 1024×768 | light |
| alice-shared-1024-dark.png | Alice / shared | 1024×768 | dark |
| alice-shared-1440-light.png | Alice / shared | 1440×900 | light |
| alice-shared-1440-dark.png | Alice / shared | 1440×900 | dark |
| carol-shared-1024-light.png | Carol / shared | 1024×768 | light |
| carol-shared-1024-dark.png | Carol / shared | 1024×768 | dark |
| carol-shared-1440-light.png | Carol / shared | 1440×900 | light |
| carol-shared-1440-dark.png | Carol / shared | 1440×900 | dark |
| alice-rich-1024-light.png | Alice / rich | 1024×768 | light |
| alice-rich-1024-dark.png | Alice / rich | 1024×768 | dark |
| alice-rich-1440-light.png | Alice / rich | 1440×900 | light |
| alice-rich-1440-dark.png | Alice / rich | 1440×900 | dark |
| alice-closed-1440-light.png | Alice / closed | 1440×900 | light |
| carol-rich-1440-light.png | Carol / rich (access check) | 1440×900 | light |

`carol-rich-1440-light.png` returns HTTP 200 but renders "This matter isn't on your list" with no `h1`, no header card and no tabs. Carol is not a member of that matter.

## Header DOM facts (`[data-testid=matter-header-card]`)

These facts were identical across viewport and theme for each user/matter pair.

| | Alice / shared | Carol / shared | Alice / rich | Alice / closed |
|---|---|---|---|---|
| header card present | yes (`DIV`, shadcn Card) | yes | yes | yes |
| `h1` count | 1 | 1 | 1 | 1 |
| `h1[data-testid=matter-name]` text | "Budget Test Project 6crh" | "Budget Test Project 6crh" | "Website Redesign" | "Ndlovu v RAF — Depth II mwdp05zs03d6" |
| `project-status-badge` | "Active" | "Active" | "Active" | "Closed" |
| work type span | none | none | none | "LITIGATION" |
| client line | "Internal Matter" | "Internal Matter" | "Acme Corp" (link) | "Sipho Ndlovu mwdp05zs03d6" (link) |
| `project-closed-at` | n/a | n/a | n/a | **absent** (see finding B1) |
| `matter-header-fields` | absent (no values) | absent | absent | absent |
| `matter-header-field-*` children | none | none | none | none |
| `header-lifecycle-actions` | present | present, **empty** | present | present |
| lifecycle buttons | `close-matter-btn` "Close Matter", `complete-project-btn` "Complete Matter", `project-overflow-menu` (icon-only, **no text and no aria-label**) | none | same 3 as Alice / shared | `reopen-matter-btn` "Reopen Matter" |

Header `textContent`:

- Alice / shared: "Budget Test Project 6crhActiveInternal MatterClose MatterComplete Matter"
- Carol / shared: "Budget Test Project 6crhActiveInternal Matter"

Main has no reference-number code chip on these matters (`reference_number` is NULL on all of them).

## Overview tab KPI cards (`[data-testid=metric-card-grid]` > `metric-card-*`)

| card | Alice / shared | Carol / shared | Alice / rich | Alice / closed |
|---|---|---|---|---|
| `metric-card-budget` "Fee estimate consumed" | 2% | 2% | hidden (no budget) | hidden |
| `metric-card-hours` "Hours logged" | 2.0h | 2.0h | 0.0h | 2.0h |
| `metric-card-tasks` "Task completion" | 0% | 0% | 0% | 0% |
| `metric-card-trust` "Trust balance" | R 0,00 | R 0,00 | R 0,00 | R 3 750,00 |
| `metric-card-invoices` "Outstanding fee notes" | R 0,00 | **hidden** | R 8 625,00 | R 0,00 |

Alice / shared therefore shows 5 cards and Carol shows 4. Carol does not get the Outstanding fee notes card but does get the Trust balance card. No `metric-card-deadline` ("Days to deadline") appears because no due date is set.

Other Overview content:

- `project-health-header`: "Healthy" on the shared matter; "Unknown · Client: Acme Corp · No tasks created yet" on the rich matter.
- `setup-progress-bar` is present on all matters.
- `matter-upcoming-deadlines-tile` reads "No upcoming deadlines." on all matters.

## Console

0 browser console errors, 0 warnings, 0 `pageerror` events on all 14 page loads, so there are no hydration warnings. Captured types: `error`, `warning`, `pageerror`.

Server side, each Carol load of the shared matter logs one backend WARN:

`Forbidden: path=/api/matters/e56794da-…/time-summary/by-member … reason=Per-member time breakdown is restricted to matter leads, org admins, and org owners`

The page asks for an endpoint that a member is not allowed to use, and the 403 is swallowed. This happens on main and is not caused by 641B.

## Request de-dup evidence (`/budget`, `/upcoming-deadlines`)

**No request logging exists, so this could not be proven from logs.**

- `docker compose -f compose/docker-compose.e2e.yml logs --since 10m backend | grep -i "budget\|upcoming-deadlines"` returns **0 matches** right after the 14 loads. The backend logs only exceptions (GlobalExceptionHandler WARNs) and job-queue INFO lines. There is no access log.
- The frontend container emits no fetch logs: 0 lines in the window, and no `logging` key in `next.config`.
- `pg_stat_statements` is not installed.
- Logging config was not changed.

What the main source shows:

- **`/budget` is called twice in one render:**
  - `app/(app)/org/[slug]/matters/[id]/page.tsx:302` calls `getMatterBudget(id)` (for the Budget tab).
  - `components/projects/kpi-dashboard.tsx:155` calls `getMatterBudget(projectId)` (for the Overview).
  - Caveat: both go through `apiRequest`, which calls `fetch` (GET) with the same URL and options and no AbortSignal. Next's per-render fetch memoization may therefore already collapse these into one network call. That is unverified.
- **`/upcoming-deadlines` is called only once on main**, from `components/projects/kpi-dashboard.tsx:156` (`getMatterUpcomingDeadlines`). No other caller exists in `frontend/app` or `frontend/components`. The "read twice" premise does not hold for deadlines on main. It would only become a double read if 641B adds a second reader, such as the hero tiles.

## Findings that exist before 641B (not fixed here)

- **B1.** `project-closed-at` can never render. `GET /api/matters/{id}` returns no `closedAt` key; the response keys are `archivedAt` and `completedAt` but no `closedAt`. The DB has `projects.closed_at = 2026-11-12 15:20:13Z` for `71588d0d…`. The frontend `Matter.closedAt` (`lib/types/matter.ts:28`) is therefore always undefined, and the header's `projectStatus === "CLOSED" && closedAt` guard is never true. No e2e test asserts `project-closed-at`.
- **B2.** `project-overflow-menu` (header "…" button) has no accessible name: no text and no `aria-label`.
- **B3.** Carol's matter page triggers a 403 on `/time-summary/by-member` (see Console).

## SEL-COMPAT "before"

`frontend/e2e` is byte-identical to main (`git diff --quiet main -- frontend/e2e`). Paths below are relative to `frontend/`.

Direct header selectors:

- `e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts:259`: `page.getByTestId("matter-header-card").getByTestId("matter-header-fields")`. The test needs `matter-header-fields` nested inside `matter-header-card`, and expects count 0 when empty (`:261`).
- `e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts:279-282`: `strip.locator("dd")` expects exactly the 3 values in order, each visible.
- `e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts:284`: `strip.getByTestId("matter-header-field-case_number").locator("dt")` toHaveText "Case no.".
- `e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts:289`: after reload, `getByRole("heading", { level: 1, name: siphoMatter() })`.
- `e2e/tests/projects/project-crud.spec.ts:230`: `page.getByTestId("project-status-badge")` toContainText "Archived".
- `e2e/tests/legal-depth-ii/null-prefetch-probe.spec.ts:13` is a comment; `:48` is the marker `#main-content [data-testid="matter-name"]`, so `matter-name` must sit inside `#main-content`.

No hits for `project-closed-at` or `header-lifecycle-actions`. No direct hits for `matter-header-field-` apart from `:284` above.

h1 waits in helpers:

- `e2e/helpers/legal-depth-ii.ts:147`, `openMatter`: `clickThrough(link, page.getByRole("heading", { level: 1, name: matterName }))`. The name is matched as a substring, not exactly.
- `e2e/helpers/legal-lifecycle.ts:217`, `createTemplateMatter`: `expect(page.getByRole("heading", { level: 1, name })).toBeVisible({ timeout: 15_000 })`.
- `e2e/helpers/legal-lifecycle.ts:218`, `createTemplateMatter`: `expect(page.locator("main")).toContainText(client)`, so the client name must stay inside `main`.

Related lifecycle-button selectors (not in the requested list, recorded for completeness):

- `e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts:234`: `getByTestId("close-matter-btn")` visible.
- `e2e/helpers/legal-depth-ii.ts:245`: `getByTestId("close-matter-btn").click()`.

No e2e hits for `metric-card*`, `project-health-header`, `matter-upcoming-deadlines-tile`, `project-overflow-menu`, `complete-project-btn` or `reopen-matter-btn`.
