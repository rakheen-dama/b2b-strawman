# 644B: §15.8.7 metrics after (task 644.5)

**Measured on:** branch `phase90/44B-exit-closure` @ `058d6f27e` (2026-11-13 22:01 UTC). Its product code is identical to the 644A gate head `2773ec9b6`: `git log --oneline 2773ec9b6..HEAD -- backend frontend portal packages gateway compose` prints nothing. **Baseline:** 637.1, `main` @ `f7b7dbdf1` (`baseline/README.md`, § "§15.8.7 / §15.2.5 metrics").

**Method:**
- Every command below is the 637.1 command, byte-for-byte, run from the worktree root with `bash -c` (git 2.52.0, macOS).
- The raw output of every command, including the full hex listing and the per-file `dark:` listing, is in [`metrics-raw.txt`](metrics-raw.txt).
- `inscope.txt` is the 57-path list from `baseline/README.md` `<details>`. It is committed as [`metrics-inscope-baseline.txt`](metrics-inscope-baseline.txt), extracted with `awk '/^```$/{f=!f; next} f' qa_cycle/evidence/phase90-ui/baseline/README.md`. Reviewers can re-run any row with `cp qa_cycle/evidence/phase90-ui/metrics-inscope-baseline.txt inscope.txt` and then the command.

## Before → after

| Metric | Command (637.1, verbatim) | 637.1 @`f7b7dbdf1` | Exit @`058d6f27e` | Δ |
|---|---|---|---|---|
| Inline display h1 | `git grep -c "font-display text-3xl" -- frontend/app frontend/components \| awk -F: '{s+=$NF} END {print s}'` | 99 | **93** | −6 (dashboard-header, matters, clients, trust-accounting, reports, reports/profitability, all now on `PageHeader`) |
| Raw hex: §15.8.7 verbatim (ERE) | `git grep -nE "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" \| wc -l` | 6 | **2** | −4. Under-counts: macOS git ERE has no `\b`. The 2 hits are the brand-colour help strings `branding-section.tsx:92` and `general-settings-form.tsx:137` |
| Raw hex: PCRE (OQ #22) | `git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" \| wc -l` | 46 | **32** | −14 = −16 removed (`debtors-widget` 4, `time-breakdown` 6, deleted `mini-progress-ring` 4, `horizontal-bar-chart` 1, `radial-gauge` 1) +2 new (`shell/app-sidebar.tsx:226` comment "(#1B3A4B)"; `ui/chart.tsx:68` shadcn registry selector `[stroke='#ccc']`, not a painted colour). 13 of the 32 are comment false positives (`PR #nnnn`, "(#1B3A4B)") |
| Raw hex: wider `.ts/.tsx` incl. `lib` | `git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.ts" "frontend/components/**/*.tsx" "frontend/app/**/*.ts" "frontend/app/**/*.tsx" "frontend/lib/**/*.ts" "frontend/lib/**/*.tsx" \| wc -l` | 58 | **44** | −14 |
| Raw hex: wider incl. files directly under `components/` and `lib/` | as above plus `"frontend/components/*.ts" "frontend/components/*.tsx" "frontend/lib/*.ts"` | 68 | **47** | −21. 17 of the 47 are comment false positives (`PR #nnnn`, "(#1B3A4B)"). The real sites are listed below |
| Ad-hoc `<table` on in-scope files | `tr '\n' '\0' < inscope.txt \| xargs -0 git --literal-pathspecs grep -c "<table" -- \| awk -F: '{s+=$NF} END {print s+0}'` | 1 (`trust-accounting/page.tsx`) | **0** | −1 (643A moved Recent Transactions onto `DataTable`) |
| `dark:` lines on in-scope files | same with `"dark:"` | 225 | **27** | −198 (−199 removed, +1 added; reconciliation below) |
| `dark:` lines, whole frontend | `git grep -c "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" \| awk -F: '{s+=$NF} END {print s}'` | 3851 | **3648** | −203 |
| `dark:` occurrences, whole frontend | `git grep -o "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" \| wc -l` | 4610 | **4379** | −231 |
| Dead chart / widget files | `ls frontend/components/dashboard/{horizontal-bar-chart,radial-gauge,kpi-card,sparkline-chart,sparkline,incomplete-profiles-widget,information-requests-widget}.tsx \| wc -l` | 7 | **0** | −7. `ls` prints 7 "No such file or directory" lines (raw file) |
| Chart usages, **637.1 definition** (importers of the old dashboard charts) | `git grep -lE "from \"@/components/dashboard/(donut-chart\|mini-progress-ring\|micro-stacked-bar)\"" -- frontend/app frontend/components` | 3 | **0** | −3. Exit 1 = no match: the three modules were deleted (639B, 639D) |
| Chart usages, **644.5 definition** (importers of `$FC/charts/*`; new, no baseline) | `git grep -lE "from \"@/components/charts/" -- frontend/app frontend/components \| grep -v "^frontend/components/charts/"` | n/a | **10 importers / 9 live** | `dashboard/{debtors-widget,kpi-row,unbilled-wip-chart}`, `my-work/{time-breakdown,weekly-time-summary}`, `profitability/{billing-trend-chart,profitability-ranking-chart,utilization-chart}`, `trust/trust-balance-trend-chart`, and `projects/overview-metrics-strip.tsx`. The last one has **no production importer**: `git grep -l "OverviewMetricsStrip" -- frontend/app frontend/components frontend/lib`, minus its own file, exits 1 (641B; deletion candidate, C.6) |
| Detail page lines: matter | `wc -l "frontend/app/(app)/org/[slug]/matters/[id]/page.tsx"` | 680 | **384** | −296 (target < 450: **met**) |
| Detail page lines: client | `wc -l "frontend/app/(app)/org/[slug]/clients/[id]/page.tsx"` | 756 | **380** | −376 (target < 550: **met**) |
| Detail page lines: trust dashboard | `wc -l "frontend/app/(app)/org/[slug]/trust-accounting/page.tsx"` | 426 | **363** | −63 (no target) |

## Like-with-like caveat (in-scope file list)

Six of the 57 baseline in-scope paths no longer exist at the exit head: `frontend/components/{desktop-sidebar,mobile-sidebar,nav-zone}.tsx` (638A), `frontend/components/matters/matter-table-row.tsx` (641A), `frontend/components/clients/client-table-row.tsx` (642A) and `frontend/components/customers/trust-balance-card.tsx` (642A re-export deleted).
- `git --literal-pathspecs grep` skips them silently.
- All six had **0** `dark:` lines and 0 `<table` at `f7b7dbdf1` (`git show f7b7dbdf1:<path> | grep -c "dark:"`), so dropping them changes neither in-scope sum.

The list does **not** include the files Phase 90 created (`components/shell/*`, `components/data-table/*`, `components/charts/*`, `kpi-row.tsx`, `matter-columns.tsx`, `client-columns.tsx`, `trust/*`, `profitability/*-chart.tsx`, …). A separate count over the 51 non-test `.ts/.tsx` files added since `f7b7dbdf1` (`git diff --name-only --diff-filter=A f7b7dbdf1..HEAD`) finds 2 `dark:` lines. Both are in shadcn CLI files: `components/ui/toggle.tsx` 1 (`dark:aria-invalid:…`) and `components/ui/chart.tsx` 1 (the `THEMES` key). Both are registry code, not hand-written pairs.

## `dark:` reconciliation (in-scope 225 → 27)

Re-derived per file from git (`git show f7b7dbdf1:<path> | grep -c "dark:"` vs `grep -c "dark:" <path>`). The baseline per-file counts sum to **225**, the same as 637.1. Every per-file reduction matches the DARK-AUDIT figures in the slice PR bodies (`gh pr view <n> --json body -q .body`; #1763, #1765 and #1766 have no DARK-AUDIT section, and 639A's number is in its Files table):

| Slice (PR) | Files: `dark:` before → after | Δ |
|---|---|---|
| 638A (#1761) + 638B (#1762) | `org/[slug]/layout.tsx` 2 → 0; `breadcrumbs.tsx` 4 → 0 | −6 |
| 639A (#1763) + 639B (#1764) | `empty-state.tsx` 3 → 0; `my-work/time-breakdown.tsx` 5 → 0 | −8 |
| 640A (#1767) | `dashboard-header.tsx` 2 → 0 | −2 |
| 640B (#1768) | 9 dashboard widgets + `widget-parts.tsx` 30 → 0 | −30 |
| 640C (#1769) | `weekly-time-summary.tsx` 18 → 0; `dashboard/loading.tsx` 1 → 0 | −19 |
| 641A (#1770) | `matters-table.tsx` 6 → 0; `matters/page.tsx` 6 → 0 | −12 |
| 641B (#1771) | `matter-header-card.tsx` 7 → 1; `create-project-dialog` 3 → 0; `edit-project-dialog` 1 → 0; `matters/[id]/page.tsx` 1 → 0 (`kpi-dashboard.tsx` 10 → 10, props-only ruling) | −11 |
| 642A (#1772) | `clients-table.tsx` 8 → 0; `clients/page.tsx` 6 → 0 | −14 |
| 642B (#1773) | `clients/[id]/page.tsx` 5, `client-header-card.tsx` 2, `create-customer-dialog` 3, `edit-customer-dialog` 3 → 0 | −13 |
| 642C (#1774) | `grouped-tab-bar.tsx` 4 → 0 | −4 |
| 643A (#1775) | `trust-accounting/page.tsx` 40 → 0 | −40 |
| 643B (#1776) | (no in-scope `dark:` change) | 0 |
| 643C (#1777) | `utilization-table.tsx` 17, `project-profitability-table.tsx` 16, `reports/profitability/page.tsx` 3 → 0; `reports/page.tsx` 5 → 1 | −40 |
| 637A / 639D | `lib/chart-theme.ts` 0 → 1 (a doc comment, line 5, that contains the string "`dark:` overrides") | +1 |
| **Total** | | **−199 + 1 = −198 → 27** |

**The 27 left at the exit head** (`metrics-raw.txt`, per-file listing):
- `components/auth-header-controls.tsx` 7 and `components/auth/user-menu-bff.tsx` 7. These are header controls hosted by the new shell. 638B left them as long-tail debt.
- `components/projects/kpi-dashboard.tsx` 10 (641B props-only ruling).
- `components/projects/matter-header-card.tsx` 1 (`:71`, teal client link).
- `app/(app)/org/[slug]/reports/page.tsx` 1 (`:28`, teal "Run report →"; no token for it).
- `lib/chart-theme.ts` 1 (comment, not a class).

## Remaining raw-hex sites (wider count, 47 lines; 17 comment false positives)

| Site | Lines | Kind | Disposition |
|---|---|---|---|
| `components/integrations/XeroConnectionCard.tsx` | 97, 98, 141 | Xero brand colour `#13B5EA` | Intentional brand exception (637A). `:141` white-on-brand is 2.38:1, dark-walk D-5 |
| `components/customers/XeroContactBadge.tsx` | 32 | Xero brand colour | Intentional brand exception |
| `components/editor/client-renderer.ts` | 25–61 (8 lines) | Document-render CSS (never themed) | Out of scope: renders documents, not the app |
| `lib/invoice-preview-error.ts` | 51–53 | Standalone error HTML for the invoice-preview iframe | Out of scope: not app chrome |
| `settings/documents/_tabs/branding-section.tsx` | 29, 92, 187 | Brand-colour default, help text and placeholder | Data defaults, not paint (637A backlog) |
| `settings/firm/_tabs/general.tsx` | 54 | Brand-colour default | Same |
| `components/settings/general-settings-form.tsx` | 137, 307, 315 | Brand-colour help text, default, placeholder | Same |
| `components/tags/TagDialog.tsx` | 65, 129, 139 | Tag colour help text and placeholder | Same (tag colours are user data) |
| `components/tags/TagInput.tsx:26`, `settings/matters/_tabs/tags-content.tsx:17` | 1 + 1 | Contrast text `#000000`/`#FFFFFF` for user tag colours | Could use `--foreground`/`--background`; backlog |
| `components/__tests__/app-sidebar.test.tsx` | 453, 459 | Test fixture `#1B3A4B` | Test data |
| `components/ui/chart.tsx` | 68 | shadcn registry attribute selector `[stroke='#ccc']` | Registry code (targets recharts' default strokes) |

## Migrations and dependencies (C.4 inputs)

- `git diff --name-only f7b7dbdf1..HEAD -- backend/src/main/resources/db` is empty: **Phase 90 added no migration.**
- Latest files at both baseline and exit: tenant `V15__g89_trust_investment_cancelled.sql`, global `V6__portal_trust_reference_200.sql`. So the true state is **tenant V1–V15 (next free V16), global V1–V6 (next free V7)**. G89 Batch L (#1757) landed both before the 637.1 baseline. The task-file header ("V1–V14 … V1–V5", written at `1bf85f0b1`) and `TASKS.md:5` ("next free V12 / V4") were stale; 644B corrects them.
- Dependencies added (`git diff f7b7dbdf1..HEAD -- frontend/package.json portal/package.json packages/*/package.json`): exactly **`next-themes` 0.4.6** and **`@tanstack/react-table` 8.21.3** (exact pins). `packages/ui/package.json` gained two subpath exports (`./page-header`, `./stat-card`), not dependencies.
