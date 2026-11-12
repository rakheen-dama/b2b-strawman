# Phase 90 baseline statement (task 637.1)

Measured on `main` **before any 637A code**, in a detached worktree at **`f7b7dbdf1`** (P1 #1758 merged, Phase 90 not started), 2026-11-11/12.
644A / 644B compare against these numbers, not memory. Every number is listed with the exact command that produced it (OQ #22).

## Gates

| Gate | Command | Result |
|---|---|---|
| FE-GATE | `cd frontend && rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | exit 0 — **457 files, 3763 passed / 2 skipped (3765)** |
| PORTAL-GATE | `cd portal && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | exit 0 — **51 files, 284 passed (284)** |

## E2E (fresh stack: `e2e-down.sh` → `docker builder prune -f` → `e2e-up.sh`, mock auth :3001, started 02:01 SAST)

Runner: `e2e/run-e2e.sh` (this folder). Summary: `e2e/summary.txt`; suite tails in `e2e/*-tail.txt`.

| Suite | Command | Result |
|---|---|---|
| legal-lifecycle | `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle` | **105 passed** (3.1m) |
| legal-depth-ii | `… pnpm test:e2e:legal-depth-ii` | **40 passed, 4 skipped** (2.0m) |
| E2E-DEFAULT | `… pnpm test:e2e` | **157 passed, 1 failed, 7 did not run, 14 skipped** (1.4m) |

**Baseline E2E-DEFAULT failure (pre-existing on `main`, cause unconfirmed):** `e2e/tests/invoices/invoice-lifecycle.spec.ts:88` *INV-02 DRAFT → APPROVED* — `expect(getByRole('button', { name: 'Approve' })).toHaveCount(0)` still saw 1 button after 5 s (`e2e/default-failure-INV-02.txt`); the 7 "did not run" are the rest of that serial describe. What the evidence shows: the same test **passed** on the next fresh stack (637A run started 18 min later at a different revision, `../637A/e2e/summary.txt`: 165 passed / 0 failed / 14 skipped), and Phase 90 touches no invoice code. That points to an intermittent failure (suspected timing) but does **not** establish the cause — a state-transition defect that fails intermittently is not excluded. Not owned by Phase 90; 644A compares against these counts, and a recurrence gets its own investigation/fix PR rather than being waived as a flake.

## §15.8.7 / §15.2.5 metrics (run from the repo root at `f7b7dbdf1`)

| Metric | Command | Value |
|---|---|---|
| Inline display h1 | `git grep -c "font-display text-3xl" -- frontend/app frontend/components \| awk -F: '{s+=$NF} END {print s}'` | **99** (doc said 94 at `1bf85f0b1`) |
| Raw hex — §15.8.7 command verbatim (ERE) | `git grep -nE "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" \| wc -l` | **6** — under-counts: macOS git's ERE has no `\b` |
| Raw hex — same, PCRE | `git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" \| wc -l` | **46** (doc said 37) |
| Raw hex — wider `.ts/.tsx` incl. `lib` (§15.2.5 "≈50") | `git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.ts" "frontend/components/**/*.tsx" "frontend/app/**/*.ts" "frontend/app/**/*.tsx" "frontend/lib/**/*.ts" "frontend/lib/**/*.tsx" \| wc -l` | **58** (≈20 are `PR #1234` comment false positives) |
| Raw hex — wider incl. files directly under `components/` and `lib/` | as above plus `"frontend/components/*.ts" "frontend/components/*.tsx" "frontend/lib/*.ts"` | **68** |
| Ad-hoc `<table` on in-scope files | `tr '\n' '\0' < inscope.txt \| xargs -0 git --literal-pathspecs grep -c "<table" -- \| awk -F: '{s+=$NF} END {print s+0}'` | **1** (`trust-accounting/page.tsx`) |
| `dark:` lines on in-scope files | same with `"dark:"` | **225** |
| `dark:` lines, whole frontend | `git grep -c "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" \| awk -F: '{s+=$NF} END {print s}'` | **3851** (§15.1 "≈3,850") |
| `dark:` occurrences, whole frontend | `git grep -o "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" \| wc -l` | **4610** (ADR-371 "≈4,600") |
| Dead chart / widget files | `ls frontend/components/dashboard/{horizontal-bar-chart,radial-gauge,kpi-card,sparkline-chart,sparkline,incomplete-profiles-widget,information-requests-widget}.tsx \| wc -l` | **7** |
| Chart usages | `git grep -lE "from \"@/components/dashboard/(donut-chart\|mini-progress-ring\|micro-stacked-bar)\"" -- frontend/app frontend/components` | **3** (`debtors-widget`, `my-work/time-breakdown`, `projects/overview-metrics-strip`) |
| Detail page lines | `wc -l` on `matters/[id]/page.tsx`, `clients/[id]/page.tsx`, `trust-accounting/page.tsx` | **680 / 756 / 426** |

`inscope.txt` = the 57 existing files named in §15.8.2 (listed below). Paths contain `[slug]`/`[id]`, hence `--literal-pathspecs`.

<details><summary>In-scope file list (57)</summary>

```
frontend/app/globals.css
frontend/lib/chart-theme.ts
frontend/app/layout.tsx
frontend/components/auth-header-controls.tsx
frontend/components/auth/user-menu-bff.tsx
frontend/components/desktop-sidebar.tsx
frontend/components/mobile-sidebar.tsx
frontend/components/nav-zone.tsx
frontend/components/sidebar-user-footer.tsx
frontend/components/sidebar-utility-footer.tsx
frontend/app/(app)/org/[slug]/layout.tsx
frontend/components/command-palette-dialog.tsx
frontend/components/breadcrumbs.tsx
frontend/components/empty-state.tsx
frontend/components/my-work/time-breakdown.tsx
frontend/components/projects/overview-metrics-strip.tsx
frontend/components/dashboard/debtors-widget.tsx
frontend/components/dashboard/home-diary-widget.tsx
frontend/components/dashboard/my-tasks-widget.tsx
frontend/components/dashboard/unbilled-wip-widget.tsx
frontend/components/dashboard/trust-position-widget.tsx
frontend/components/dashboard/fica-outstanding-widget.tsx
frontend/components/dashboard/matter-health-widget.tsx
frontend/components/dashboard/recent-activity-widget.tsx
frontend/components/dashboard/sensitive-events-widget.tsx
frontend/components/dashboard/widget-parts.tsx
frontend/components/my-work/weekly-time-summary.tsx
frontend/app/(app)/org/[slug]/dashboard/loading.tsx
frontend/app/(app)/org/[slug]/dashboard/page.tsx
frontend/app/(app)/org/[slug]/dashboard/dashboard-header.tsx
frontend/components/matters/matters-table.tsx
frontend/app/(app)/org/[slug]/matters/page.tsx
frontend/components/matters/matter-table-row.tsx
frontend/components/projects/matter-header-card.tsx
frontend/app/(app)/org/[slug]/matters/[id]/page.tsx
frontend/components/projects/kpi-dashboard.tsx
frontend/components/projects/create-project-dialog.tsx
frontend/components/projects/edit-project-dialog.tsx
frontend/components/clients/clients-table.tsx
frontend/app/(app)/org/[slug]/clients/page.tsx
frontend/components/clients/client-table-row.tsx
frontend/components/customers/trust-balance-card.tsx
frontend/components/customers/client-header-card.tsx
frontend/components/customers/client-header-card-with-lifecycle.tsx
frontend/app/(app)/org/[slug]/clients/[id]/page.tsx
frontend/components/customers/create-customer-dialog.tsx
frontend/components/customers/edit-customer-dialog.tsx
frontend/components/shared/grouped-tab-bar.tsx
frontend/components/matters/matter-tabs.tsx
frontend/components/customers/customer-grouped-tabs.tsx
frontend/app/(app)/org/[slug]/trust-accounting/page.tsx
frontend/app/(app)/org/[slug]/trust-accounting/actions.ts
frontend/app/(app)/org/[slug]/reports/profitability/page.tsx
frontend/components/profitability/profitability-content.tsx
frontend/components/profitability/utilization-table.tsx
frontend/components/profitability/project-profitability-table.tsx
frontend/app/(app)/org/[slug]/reports/page.tsx
```
</details>
