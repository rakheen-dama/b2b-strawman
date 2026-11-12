# 639A evidence — PageHeader, StatCard, EmptyState (in place), DetailHero, kpi/chart-format (E3A)

Code heads: `bc7233bf8` (slice) → `f265fc609` (review fix: tests + one comment, no rendered code). Commits after it add evidence under this folder only. PR #1763.

| File / folder | What |
|---|---|
| `gates-summary.txt` | Orchestrator FE-GATE + PORTAL-GATE re-runs (exit 0), incl. the triage of a Node-26 false start (Homebrew node's built-in Web Storage shadows happy-dom `localStorage`; CI pins node 20) |
| `e2e/summary.txt` | E2E-LEGAL on a fresh stack (`e2e-down` → `docker builder prune -f` → `e2e-up`) built from the worktree @ `bc7233bf8`: legal-lifecycle **105 passed**, legal-depth-ii **40 passed / 4 skipped** — both = baseline |
| `probe.cjs` | Sample discovery: which seeded routes render `[data-slot="empty-state"]` |
| `obs.cjs`, `obs-report.json`, `*-{1024,1440}-{light,dark}.png`, `*-emptystate.png` | Own OBS (639.7): three sampled `EmptyState` importers × 1024/1440 × light/dark (12 page shots + 12 element crops) |
| `portal/` | PORTAL-GATE observation: portal `/home` at 1024 light (`portal-obs.cjs` → `portal/obs-report.json`) |

OBS was driven with a Playwright **library** script (real headless Chromium), not Playwright MCP — the MCP Chrome profile is held by the user's live session (same deviation as 637A–638B). Scripts are committed byte-for-byte as executed.

## Samples (A8 — the matters *filtered*-empty view is a hand-rolled `<div>`, not `EmptyState`, so it is not a sample; matters list is 641A)

| Sample | Route | EmptyState(s) rendered |
|---|---|---|
| Empty settings list | `/settings/matters?tab=tags` | "No tags yet" (+ "Create tag" action) |
| Empty page-level lists | `/my-work` | "No tasks assigned to you" (+ "Browse matters"), "No time recordings yet" |
| Empty report | `/reports/profitability` | "No profitability data yet" (+ "Go to rate settings" secondary link) |

## Findings (from `obs-report.json`, all 12 captures)

- Every capture: exactly **one `<h1>`** (Tags / My Work / Profitability); `html` class = the theme under test (`light` / `dark`).
- Icon tile **48×48**, `bg-muted` resolves per theme (light `lab(95.1 …)`, dark `lab(14.1 …)`), svg `aria-hidden="true"`; title is an `<h2>` in **Sora 600** (`font-display`); description keeps `max-w-md`; actions and secondary links render unchanged (props untouched — 48 importers, none edited).
- **Hydration warnings: 0** on all 12 loads. Console: 1 entry total — `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch` on the first dark navigation, which is the dashboard's in-flight mock-auth userinfo fetch aborted by the script navigating away immediately after login (harness timing; the light pass on the same flow logged none; not an EmptyState code path).
- Portal `/home` @1024 light: h1 "Home", body background and `--ring` identical to 637A's capture, console empty — package exports/tokens unchanged for the portal.

Note for 640C (out of 639A scope — `my-work/page.tsx` is not in this diff): at 1024 the My Work page header description wraps into a narrow column beside the title (`my-work-1024-*.png`).

**Full primitive OBS (PageHeader, StatCard, DetailHero at 1024/1440 × light/dark) is carried by 640A (OBS-CARRY)** — they have no consumer in this slice.
