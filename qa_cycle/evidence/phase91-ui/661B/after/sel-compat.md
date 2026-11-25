# 661B — SEL-COMPAT after (task 661.14)

Replays the SEL-COMPAT list in `../before/README.md`. Greps were run at `6e3e0566f` (worktree `worktree-epic-661B`). Live counts come from `live-selectors.json`, written by `scripts/capture-after.cjs` on the fresh e2e stack built from `6e3e0566f`. The spec runs are listed at the end.

| Selector / invariant | Before (`551778100`): present at | After (`6e3e0566f`): still present at | Live count, before → after |
|---|---|---|---|
| Exactly one `<main id="main-content">` | `frontend/app/(app)/org/[slug]/layout.tsx:142` | `frontend/app/(app)/org/[slug]/layout.tsx:147`. The line moved because of the added comments. `bg-background` was dropped from `main` (the inset paints it). `SidebarInset` is still a `div` (`components/ui/sidebar.tsx:327-329`), so no second `<main>` | matter 1/1 → **1/1**; dashboard 1/1 → **1/1**; fee notes — → **1/1**; matters list — → **1/1** |
| Exactly one h1 inside `#main-content` | `e2e/tests/navigation/sidebar-navigation.spec.ts:38`, `:39` | same lines (`:38` `page.locator("#main-content")`, `:39` `getByRole("heading", { level: 1 }).first()`) | matter 1 → **1** ("Estate Late Peter Moroka …"); dashboard 1 → **1** ("Home"); fee notes **1** ("Fee notes"); matters **1** ("Matters") |
| `navigation[name="Main navigation"]` | spec `day-00-firm-setup.spec.ts:104`, `:264`; `sidebar-navigation.spec.ts:80`; source `components/shell/app-sidebar.tsx:264` | spec lines unchanged (`:104`, `:264`, `:80`). Source is now `components/shell/app-sidebar.tsx:265`: one comment line was added above it | 1 → **1** on every page probed |
| day-00 link names, visible | `day-00-firm-setup.spec.ts:109-110` | `day-00-firm-setup.spec.ts:109-110` (unchanged) | legal-lifecycle **105/105** |
| day-00 link names, absent | `day-00-firm-setup.spec.ts:112-113` | `:112-113` (unchanged) | legal-lifecycle **105/105** |
| day-00 module links | `day-00-firm-setup.spec.ts:268-270` | `:268-270` (unchanged) | legal-lifecycle **105/105** |
| Fees / Trust start collapsed | spec `:272-275`; source `lib/nav-items.ts:174`, `:228` | spec `:272-275`; `lib/nav-items.ts:174`, `:228` `defaultExpanded: false` (unchanged) | `shell-1440.png`: FEES chevron-right (collapsed) in all four tiles |
| Trust links after expand | `day-00-firm-setup.spec.ts:274`, `:276-277` | unchanged | legal-lifecycle **105/105** |
| Absence of "Interest" | `day-00-firm-setup.spec.ts:279` | unchanged | legal-lifecycle **105/105** |
| `org-brand-accent` testid | source `components/shell/app-sidebar.tsx:230`; vitest `app-sidebar.test.tsx:456`; not in `e2e/` | source `app-sidebar.tsx:231`; vitest `components/__tests__/app-sidebar.test.tsx:459`; still not referenced in `e2e/` | 1 → **1** on every page probed |
| "Search, Command K" (never exactly "Search") | `app-header.tsx:41`, `app-sidebar.tsx:244`; vitest `app-header.test.tsx:59,61,71`, `app-sidebar.test.tsx:167,445`; specs click a page-level "Search" at `day-07-14-work-trust.spec.ts:226`, `day-30-billing.spec.ts:240` | `components/shell/app-header.tsx:41`; `components/shell/app-sidebar.tsx:245`; vitest `app-header.test.tsx:59,61,71`, `app-sidebar.test.tsx:170,448`; specs `day-07-14-work-trust.spec.ts:226`, `day-30-billing.spec.ts:240` (unchanged) | 2 → **2** per page (header + rail pill). Buttons named exactly "Search" in the shell: **0** |
| Strict `locator("main")` uses in `e2e/` | 28 lines in 12 files | **28 lines in 12 files** (`grep -rnE "locator\(['\"]main['\"]\)" frontend/e2e`) | exactly one `<main>` holds (row 1) |
| New attribute `data-app-shell="firm"` (scopes ADR-392) | — | `frontend/app/(app)/org/[slug]/layout.tsx:129` (on `SidebarProvider` → `div[data-slot=sidebar-wrapper]`). **Not referenced in `e2e/`** (`grep -rn data-app-shell frontend/e2e` → 0) | **1** per firm page (`dataAppShellFirm`); 0 on marketing `/` (`probes.json` `marketing-light.dataAppShell`) |

No role, accessible name, heading, testid, placeholder or URL that any spec uses changed. No spec was edited.

## Spec runs on the fresh stack (from `frontend/`, `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001`)

| Suite | Baseline (`../../baseline/README.md`) | After (`6e3e0566f`) |
|---|---|---|
| `test:e2e:legal-lifecycle` | 105 passed | **105 passed**, 0 failed, 0 skipped (3.0 min), exit 0 |
| `test:e2e:legal-depth-ii` | 40 passed, 4 skipped | **40 passed, 4 skipped**, 0 failed (1.9 min), exit 0. The 4 skips are the same tests: `day-30-45-soa-writeoff.spec.ts:361` and the three opt-in `null-prefetch-probe.spec.ts:74` |
| `test:e2e:navigation` (includes `sidebar-navigation.spec.ts` NAV-01 ×15 + NAV-02, `command-palette.spec.ts` ×3) | no baseline count | **17 passed**, 0 failed (5.3 s), exit 0 |
