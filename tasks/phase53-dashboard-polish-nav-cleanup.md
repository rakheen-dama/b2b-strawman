# Phase 53 — Dashboard Polish, Navigation Cleanup & Print-Accurate Previews

Phase 53 is a **pure frontend visual overhaul** targeting the three primary dashboard surfaces (Org Dashboard, My Work, Project Detail Overview tab), the sidebar navigation, org-level documents, and document preview rendering. No new backend endpoints, no new entities, no database migrations. Every data point displayed on these surfaces already exists in the current API — this phase changes presentation, composition, and density to make the product **screenshot-ready for the landing page**. The aesthetic target is Scoro-level data density and chart richness.

**Architecture doc**: `architecture/phase53-dashboard-polish-nav-cleanup.md`

**ADRs**: [ADR-205](../adr/ADR-205-chart-theming-strategy.md) (chart theming strategy — centralized TypeScript theme config object)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 393 | Chart Component Library & Foundation | Frontend | -- | M | 393A, 393B | **Done** (PRs #813, #814) |
| 394 | Dashboard Redesigns | Frontend | 393A | L | 394A, 394B | |
| 395 | Project Detail & Document Preview | Frontend | 393A (395A only) | M | 395A, 395B | |
| 396 | Test Updates & Visual Baselines | Frontend | 393-395 | S | 396A | |

## Dependency Graph

```
393A (Chart Library & Theme)     393B (Sidebar + Docs Relocation)     395B (A4 Preview)
     |                                |                                    |
     +--------+---------+             |                                    |
     |        |         |         (independent)                       (independent)
  394A     394B      395A                                                  |
  (Org     (My Work  (Project                                              |
  Dash)    Redesign) Overview)                                             |
     |        |         |             |                                    |
     +--------+---------+-------------+------------------------------------+
                         |
                      396A (Tests & Visual Baselines)
```

**Parallel tracks**:
- 393A and 393B have no shared dependencies — can start immediately in parallel.
- 395B (A4 preview) is fully independent — can run in parallel with any slice.
- After 393A: slices 394A, 394B, and 395A can run in parallel (all depend only on 393A).
- 396A runs last after all other slices are complete.

## Implementation Order

### Stage 1: Foundation (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 393 | 393A | Chart theme config + new micro-chart components (Sparkline, RadialGauge, MicroStackedBar, ChartTooltip, DonutChart) + HorizontalBarChart theme migration. Foundation for all dashboard redesigns. | **Done** (PR #813) |
| 1b | Epic 393 | 393B | Sidebar nav cleanup (rename groups, remove Documents, move Proposals) + org documents relocation to Settings. Fully independent of 393A. | **Done** (PR #814) |
| 1c | Epic 395 | 395B | A4 preview wrapper component + GenerateDocumentDialog integration. Fully independent. |

### Stage 2: Dashboard Redesigns (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 394 | 394A | Org Dashboard full layout restructure — MetricsStrip, hero two-panel, secondary three-column. Depends on 393A chart components. | **Done** (PR #815) |
| 2b | Epic 394 | 394B | My Work page full layout restructure — Today's Agenda hero, WeeklyRhythmStrip, two-column work panels. Depends on 393A chart components. |
| 2c | Epic 395 | 395A | Project Detail Overview tab polish — health band, compact setup bar, two-panel body. Depends on 393A chart components. |

### Stage 3: Test Stabilization

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 396 | 396A | Update all broken tests (sidebar selectors, dashboard structure, widget ordering), refresh visual baselines, verify data-testid coverage. Depends on all prior slices. |

### Timeline

```
Stage 1:  [393A]  //  [393B]  //  [395B]      <- foundation + independent (parallel)
Stage 2:  [394A]  //  [394B]  //  [395A]      <- dashboard redesigns (parallel, after 393A)
Stage 3:  [396A]                               <- test stabilization (after all above)
```

---

## Epic 393: Chart Component Library & Foundation

**Goal**: Create the shared chart theme configuration and all new micro-chart/visualization components that the dashboard redesigns depend on. Additionally, clean up sidebar navigation labels and relocate the org documents page to Settings. These two slices are independent and can run in parallel.

**References**: Architecture doc Sections 53.6 (chart component library), 53.7 (sidebar navigation), 53.8 (org documents relocation). [ADR-205](../adr/ADR-205-chart-theming-strategy.md) (chart theming strategy).

**Dependencies**: None (first epic in Phase 53)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **393A** | 393.1-393.8 | `CHART_THEME` config, Sparkline, RadialGauge, MicroStackedBar, ChartTooltip, DonutChart components, HorizontalBarChart theme migration, unit tests (~12 tests) | **Done** (PR #813) |
| **393B** | 393.9-393.15 | Sidebar nav cleanup (4 changes to nav-items.ts), delete org documents page, create OrgDocumentsSection in Settings, update Organization settings entry, test updates (~6 tests) | **Done** (PR #814) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 393.1 | Create `CHART_THEME` config | 393A | | `lib/chart-theme.ts`. Export `CHART_THEME` constant with: `colors` (5 chart vars via `var(--chart-N)`), `slate` (grid, axis, muted colors), `gradientOpacity` ({top: 0.3, bottom: 0.0}), `tooltip` (slate-900 bg, white text, 8px radius, shadow), `grid` (3 3 dash, slate-200 stroke), `bar` (radius [4,4,0,0]), `donut` (innerRadius 60%, outerRadius 80%, cornerRadius 4, paddingAngle 2), `area` (monotone, no dot, activeDot config), `fontFamily` (var(--font-mono)). Full spec in Section 53.6.1. Pattern: follow `lib/utils.ts` for export structure. |
| 393.2 | Create `Sparkline` component | 393A | | `components/dashboard/sparkline.tsx`. "use client". Props: `data: number[], width?: number (80), height?: number (24), color?: string (var(--chart-2)), showGradient?: boolean (true), className?: string`. SVG `<polyline>` for stroke + `<polygon>` for gradient fill area. `<linearGradient>` def with unique ID (useId()). No axes, no labels, no tooltips. `data-testid="sparkline"`. Pattern: evolves existing `sparkline-chart.tsx` (custom SVG approach). See Section 53.6.7. |
| 393.3 | Create `RadialGauge` component | 393A | | `components/dashboard/radial-gauge.tsx`. "use client". Props: `value: number (0-100), size?: number (48), strokeWidth?: number (6), thresholds?: {low: number, high: number} ({60, 90}), className?: string`. SVG 270-degree arc (gap at bottom). Color by threshold: `<low` = slate-400, `low-high` = teal-500, `>high` = amber-500. Background track: slate-200. Center text: `font-mono tabular-nums text-sm font-bold`. `data-testid="radial-gauge"`. See Section 53.6.7. |
| 393.4 | Create `MicroStackedBar` component | 393A | | `components/dashboard/micro-stacked-bar.tsx`. "use client". Props: `segments: Array<{value: number, color: string, label?: string}>, width?: number (120), height?: number (8), className?: string`. Pure CSS: flexbox children with `flex-grow` proportional to value. `rounded-full` outer container. Title attribute on segments for hover tooltip. `data-testid="micro-stacked-bar"`. See Section 53.6.7. |
| 393.5 | Create `ChartTooltip` component | 393A | | `components/dashboard/chart-tooltip.tsx`. "use client". Shared Recharts custom tooltip component. Consumes `CHART_THEME.tooltip` for styling. Background: slate-900, text: white, rounded: 8px, shadow, padding: 8px 12px. Labels in `font-sans text-xs`, values in `font-mono tabular-nums`. Export as default content prop for Recharts `<Tooltip content={<ChartTooltip />} />`. `data-testid="chart-tooltip"`. See Section 53.6.5. |
| 393.6 | Create `DonutChart` component | 393A | | `components/dashboard/donut-chart.tsx`. "use client". Recharts `<PieChart>` with `<Pie innerRadius="60%" outerRadius="80%" cornerRadius={4} paddingAngle={2}>`. Props: `data: Array<{name: string, value: number, color?: string}>, centerValue?: string, centerLabel?: string, height?: number (200), className?: string`. Gradient fills per segment via `<defs>`. Center content via custom label component. Horizontal legend below. Uses `CHART_THEME.donut` defaults. `data-testid="donut-chart"`. See Section 53.6.3. |
| 393.7 | Update `HorizontalBarChart` to use `CHART_THEME` | 393A | | Modify `components/dashboard/horizontal-bar-chart.tsx`. Replace `DEFAULT_COLORS` array with `CHART_THEME.colors`. Add optional `referenceLine?: number` prop for target/threshold rendering (uses Recharts `<ReferenceLine>`). Apply `CHART_THEME.tooltip` to Tooltip component. Apply `CHART_THEME.grid` to CartesianGrid. Apply `CHART_THEME.bar.radius` to Bar. Keep existing props/API stable. Pattern: existing component at `components/dashboard/horizontal-bar-chart.tsx`. |
| 393.8 | Add unit tests for all new chart components | 393A | | `__tests__/dashboard/chart-components.test.tsx` (~12 tests): (1) Sparkline renders SVG polyline with data, (2) Sparkline handles empty data array, (3) Sparkline respects custom width/height, (4) RadialGauge renders value text, (5) RadialGauge applies teal for value in optimal range, (6) RadialGauge applies slate for under-threshold, (7) RadialGauge applies amber for over-threshold, (8) RadialGauge handles edge cases 0 and 100, (9) MicroStackedBar renders segments proportionally, (10) MicroStackedBar handles single segment, (11) DonutChart renders with center content, (12) ChartTooltip renders with theme styling. Pattern: follow `__tests__/dashboard/sparkline-chart.test.tsx` and `__tests__/dashboard/health-badge.test.tsx`. Use `@testing-library/react` + `happy-dom`. |
| 393.9 | Rename "Delivery" group to "Projects" in nav-items | 393B | | Modify `lib/nav-items.ts`. Change `id: "delivery"` to `id: "projects"` and `label: "Delivery"` to `label: "Projects"` in the NAV_GROUPS array. See Section 53.7.1 Change 2. |
| 393.10 | Remove Documents item from sidebar nav | 393B | | Modify `lib/nav-items.ts`. Remove the Documents item (`label: "Documents"`, `href: (slug) => `/org/${slug}/documents``) from the former Delivery (now Projects) group. See Section 53.7.1 Change 1. |
| 393.11 | Move Proposals from Finance to Clients group | 393B | | Modify `lib/nav-items.ts`. Remove the Proposals item from the Finance group items array. Add it to the Clients group items array after Customers and before Retainers. Keep the same icon, href, and requiredCapability. See Section 53.7.1 Change 3. |
| 393.12 | Rename "Team & Resources" to "Team" | 393B | | Modify `lib/nav-items.ts`. Change `label: "Team & Resources"` to `label: "Team"` in the NAV_GROUPS array. The `id: "team"` stays unchanged. See Section 53.7.1 Change 4. |
| 393.13 | Delete org documents standalone page and relocate to Settings | 393B | | Delete `app/(app)/org/[slug]/documents/page.tsx`. Create `components/settings/org-documents-section.tsx` — "use client" component that renders document list + upload area. Reuse `OrgDocumentUpload` from `components/documents/org-document-upload.tsx` (or extract its upload logic). List: document name, upload date, file size, download link, delete action. `data-testid="org-documents-section"`. See Section 53.8.2. |
| 393.14 | Add OrgDocumentsSection to Settings general page and update nav entry | 393B | | Modify `app/(app)/org/[slug]/settings/general/page.tsx` — add `OrgDocumentsSection` as third section below existing `VerticalProfileSection` and `GeneralSettingsForm`. Modify `lib/nav-items.ts` — update `SETTINGS_ITEMS` Organization entry: remove `comingSoon: true`, update href to `(slug) => `/org/${slug}/settings/general``. See Section 53.8.2. |
| 393.15 | Update sidebar and org documents tests | 393B | | Update/create tests (~6 tests): (1) Update sidebar nav tests to assert "Projects" instead of "Delivery", (2) assert "Team" instead of "Team & Resources", (3) assert Documents item no longer in sidebar, (4) assert Proposals appears in Clients group, (5) test OrgDocumentsSection renders document list, (6) test OrgDocumentsSection upload area. Delete any tests specific to the standalone `/documents` page. Pattern: follow `__tests__/dashboard/company-dashboard.test.tsx`. |

### Key Files

**Slice 393A — Create:**
- `frontend/lib/chart-theme.ts`
- `frontend/components/dashboard/sparkline.tsx`
- `frontend/components/dashboard/radial-gauge.tsx`
- `frontend/components/dashboard/micro-stacked-bar.tsx`
- `frontend/components/dashboard/chart-tooltip.tsx`
- `frontend/components/dashboard/donut-chart.tsx`
- `frontend/__tests__/dashboard/chart-components.test.tsx`

**Slice 393A — Modify:**
- `frontend/components/dashboard/horizontal-bar-chart.tsx` — Replace DEFAULT_COLORS with CHART_THEME, add referenceLine prop

**Slice 393A — Read for context:**
- `frontend/components/dashboard/sparkline-chart.tsx` — Existing SVG sparkline pattern to evolve
- `frontend/components/dashboard/mini-progress-ring.tsx` — Existing SVG ring pattern (RadialGauge is a larger metrics-strip variant)
- `frontend/__tests__/dashboard/sparkline-chart.test.tsx` — Test pattern for chart components
- `frontend/__tests__/dashboard/horizontal-bar-chart.test.tsx` — Existing test for the modified component
- `frontend/app/globals.css` — Chart color CSS custom properties (`--chart-1` through `--chart-5`)
- `adr/ADR-205-chart-theming-strategy.md` — Theming decision and rationale

**Slice 393B — Create:**
- `frontend/components/settings/org-documents-section.tsx`

**Slice 393B — Modify:**
- `frontend/lib/nav-items.ts` — 5 changes (rename groups, remove Documents, move Proposals, update Organization settings entry)
- `frontend/app/(app)/org/[slug]/settings/general/page.tsx` — Add OrgDocumentsSection

**Slice 393B — Delete:**
- `frontend/app/(app)/org/[slug]/documents/page.tsx`

**Slice 393B — Read for context:**
- `frontend/components/documents/org-document-upload.tsx` — Reuse upload logic in new settings section
- `frontend/app/(app)/org/[slug]/documents/page.tsx` — Understand current page structure before deletion
- `frontend/app/(app)/org/[slug]/documents/actions.ts` — Document fetch/upload server actions (keep, reuse)
- `frontend/components/settings/general-settings-form.tsx` — Pattern for settings page sections
- `frontend/components/desktop-sidebar.tsx` — Verify sidebar renders NAV_GROUPS dynamically (no code changes needed)

### Architecture Decisions

- **`CHART_THEME` as plain TypeScript object, not React Context**: Recharts consumes primitive values as props, not reactive state. A Context wrapper would add unnecessary re-render surface for static configuration. Components import `CHART_THEME` and spread relevant properties. See ADR-205.
- **CSS `var()` for fill/stroke, hardcoded OKLCH for gradient stops only**: Direct fill/stroke colors use `var(--chart-N)` references (auto-resolve for dark mode). SVG gradient `<stop>` elements use hardcoded OKLCH values because `var()` support in SVG gradients is inconsistent across browsers.
- **Sparkline stays custom SVG (not Recharts)**: Micro-charts at 80x24px do not benefit from Recharts overhead (ResponsiveContainer, scales, axes). Custom SVG is lighter and more controllable at this scale. Follows existing `sparkline-chart.tsx` precedent.
- **Sidebar group `id` change (delivery -> projects) is harmless**: The id is used only as a Framer Motion `layoutId` for the active indicator animation key. Changing it does not affect functionality.
- **Org Documents relocated to Settings, not removed**: The functionality is preserved but deprioritized from a top-level sidebar entry to a section within Settings > Organization (General), matching its admin-only usage pattern.

---

## Epic 394: Dashboard Redesigns

**Goal**: Redesign the Org Dashboard and My Work page layouts to achieve Scoro-level data density — replacing spacious KPI card rows with compact metrics strips, introducing hero sections with two-panel layouts, and reorganizing secondary widgets into multi-column grids.

**References**: Architecture doc Sections 53.3 (Org Dashboard redesign), 53.4 (My Work redesign), 53.2.4 (density principles).

**Dependencies**: Epic 393A (chart components — Sparkline, RadialGauge, MicroStackedBar, DonutChart, ChartTooltip)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **394A** | 394.1-394.8 | Org Dashboard: MetricsStrip (6 metrics), GettingStartedCard banner, hero two-panel (ProjectHealth + TeamTime), secondary three-column (Activity, Deadlines, AdminStats/MyWeek), page layout restructure, tests (~8 tests) | **Done** (PR #815) |
| **394B** | 394.9-394.15 | My Work: TodaysAgenda hero, WeeklyRhythmStrip, two-column work panels (tasks + time/activity), extended widgets area, page layout restructure, tests (~8 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 394.1 | Create `MetricsStrip` component | 394A | | `components/dashboard/metrics-strip.tsx`. "use client". 6 compact metric cells in CSS Grid (`grid-cols-6` at lg, `grid-cols-3` at md, `grid-cols-2` at sm). Each cell: label (`text-[11px] uppercase tracking-wider text-slate-500`), value (`text-xl font-mono tabular-nums font-bold`), visualization. Metrics: Active Projects (Sparkline), Hours This Month (MicroStackedBar), Revenue (trend arrow), Overdue Tasks (severity color), Team Utilization (RadialGauge), Budget Health (three-dot summary). Card: `bg-card rounded-lg border border-slate-200/60 p-3`. `data-testid="metrics-strip"`, each cell `data-testid="metric-{name}"`. Props: `kpis`, `capacityData`, `projectHealth[]`. See Section 53.3.4. |
| 394.2 | Update `GettingStartedCard` to banner format | 394A | | Modify `components/dashboard/getting-started-card.tsx`. Change from card to slim banner: `bg-teal-600/10 border border-teal-600/20`, ~48px tall, full width. Add auto-hide when `kpis.activeProjects > 3`. Move to first element inside dashboard page content, above DashboardHeader. Keep existing `useOnboardingProgress()` dismiss logic. `data-testid="getting-started-banner"`. See Section 53.3.3. |
| 394.3 | Update `ProjectHealthWidget` for hero panel | 394A | | Modify `components/dashboard/project-health-widget.tsx`. Dense table rows: project name + customer (stacked), HealthBadge (sm), CompletionProgressBar (thin), hours (`font-mono`), task ratio. Filter tabs: All / At Risk / Over Budget. Sortable columns: health, budget %, name. Max 10 rows, "View all projects" footer. `data-testid="project-health-panel"`. See Section 53.3.5. |
| 394.4 | Update `TeamWorkloadWidget` for hero panel | 394A | | Modify `components/dashboard/team-workload-widget.tsx`. Horizontal bars with color gradient (teal=under, amber/red=over). Target reference line at 80% via `<ReferenceLine>` (uses 393A HorizontalBarChart upgrade). Member name + utilization %. Below: DonutChart (from 393A) showing hours by top 5 projects + "Other", center text = total hours. `data-testid="team-time-panel"`. See Section 53.3.5. |
| 394.5 | Create `AdminStatsColumn` component | 394A | | `components/dashboard/admin-stats-column.tsx`. "use client". Compact stat cards (~32px rows): icon + number + label. Stats: incomplete profiles count, pending info requests, automation runs. "Manage" links to respective pages. Role-gated (admin/owner only). `data-testid="admin-stats-column"`. See Section 53.3.6. |
| 394.6 | Create `MyWeekColumn` component | 394A | | `components/dashboard/my-week-column.tsx`. "use client". Member's weekly summary: hours logged today, tasks completed this week, next assigned task. Derived from existing personal KPI data. `data-testid="my-week-column"`. See Section 53.3.6. |
| 394.7 | Restructure Org Dashboard page layout | 394A | | Modify `app/(app)/org/[slug]/dashboard/page.tsx`. New layout order: (1) GettingStartedCard banner (conditional), (2) DashboardHeader, (3) MetricsStrip (replaces KpiCardRow), (4) Hero two-panel `grid-cols-1 lg:grid-cols-5 gap-4` — left `col-span-3` ProjectHealthWidget, right `col-span-2` TeamWorkload+DonutChart, (5) Secondary three-column `grid-cols-1 md:grid-cols-3 gap-4` — RecentActivityWidget (compact), DeadlineWidget (list format), AdminStatsColumn or MyWeekColumn (role-dependent). Update `RecentActivityWidget` inline: compact rows ~36px, `even:bg-slate-50/50`, 15 items, "View All" link. Update `DeadlineWidget` inline: list format, 7-10 items, color-coded urgency. No new data fetching — same `fetchDashboardKpis()`, `fetchProjectHealth()`, `fetchTeamWorkload()`, `fetchDashboardActivity()`. See Section 53.3. |
| 394.8 | Add/update Org Dashboard tests | 394A | | Update `__tests__/dashboard/company-dashboard.test.tsx` and add `__tests__/dashboard/metrics-strip.test.tsx` (~8 tests): (1) MetricsStrip renders 6 metric cells, (2) MetricsStrip handles null/undefined KPI values gracefully, (3) GettingStartedCard auto-hides when activeProjects > 3, (4) Dashboard page renders MetricsStrip (not KpiCardRow), (5) Hero panels render project health + team time, (6) Secondary area renders 3 columns, (7) Admin role shows AdminStatsColumn, (8) Member role shows MyWeekColumn. Pattern: follow `__tests__/dashboard/company-dashboard.test.tsx` and `__tests__/dashboard/kpi-card-row.test.tsx`. |
| 394.9 | Create `TodaysAgenda` component | 394B | | `components/dashboard/todays-agenda.tsx`. "use client". Hero section answering "what should I work on right now?". Three horizontal sections at lg, stacked at smaller: (1) Today's Tasks — due today/overdue, compact rows ~36px (task name, project badge, time estimate `font-mono`, "Log Time" button), max 5 visible, (2) Time Logged Today — horizontal progress bar (`2h 15m / 8h` in `font-mono tabular-nums`), teal fill for logged, slate-200 remaining, target from weeklyCapacity/5, (3) Next Deadline — single most urgent, countdown, color-coded. `data-testid="todays-tasks"`, `data-testid="time-progress-today"`, `data-testid="next-deadline"`. `bg-card rounded-lg border p-4`. See Section 53.4.3. |
| 394.10 | Create `WeeklyRhythmStrip` component | 394B | | `components/dashboard/weekly-rhythm-strip.tsx`. "use client". ~48px tall strip, 7 day columns (Mon-Sun). Small vertical bars (~32px max height): teal fill for hours logged, slate-200 for remaining capacity. Current day highlighted with border accent. Weekly total at right end in `font-mono tabular-nums font-bold`. Clickable days — calls `onDaySelect(index)` callback for filtering. `data-testid="weekly-rhythm-strip"`, each day `data-testid="rhythm-day-{index}"` (0=Mon, 6=Sun). See Section 53.4.4. |
| 394.11 | Update task panels for compact layout | 394B | | Review `app/(app)/org/[slug]/my-work/my-work-tasks-client.tsx`. Compact list rows (~36px): task name, project (small badge), due date (`font-mono`), priority indicator (colored dot), time estimate. Tabs: My Tasks / Available — preserved. Inline quick actions compacted. `data-testid="tasks-panel"`. Minimal changes to existing component — mainly tighten spacing (p-4 not p-6, gap-4 not gap-6, text-sm rows). See Section 53.4.5. |
| 394.12 | Update time and activity column | 394B | | Modify my-work sub-components. Today's Time Entries: compact list from `components/my-work/today-time-entries.tsx` (tighten rows). This Week's Breakdown: replace `TimeBreakdown` pie with DonutChart from 393A (top 4 projects + "Other", billable/non-billable). Recent Activity: last 5 personal items, compact rows. `data-testid="time-activity-panel"`. See Section 53.4.5. |
| 394.13 | Restructure My Work page layout | 394B | | Modify `app/(app)/org/[slug]/my-work/page.tsx`. New layout: (1) MyWorkHeader + Timesheet link (preserved), (2) TodaysAgenda hero section, (3) WeeklyRhythmStrip, (4) Two-column work panels `grid-cols-1 lg:grid-cols-5 gap-4` — left `col-span-3` tasks panel (MyWorkTasksClient), right `col-span-2` time/activity column, (5) Extended widgets area (MyExpenses, etc.) below as compact card summaries. Replace `PersonalKpis` component with the TodaysAgenda hero (KPI data feeds into agenda). Remove existing `TimeBreakdown`+`UpcomingDeadlines` row (deadline moved into hero, time breakdown into right panel). `data-testid="extended-widgets"`. See Section 53.4. |
| 394.14 | Manage WeeklyRhythmStrip day selection state | 394B | | In the My Work page, add `useState` for selected day index (null = show all). Pass `onDaySelect` to WeeklyRhythmStrip. When a day is selected, filter the task list and time entries to that day's data. Clear selection on second click (toggle). This is a page-level concern, not a component concern — the state lives in `page.tsx` and is passed down. |
| 394.15 | Add/update My Work tests | 394B | | Update `__tests__/dashboard/my-work-dashboard.test.tsx`, `__tests__/dashboard/my-work-enhancements.test.tsx`, and add `__tests__/dashboard/weekly-rhythm-strip.test.tsx`, `__tests__/dashboard/todays-agenda.test.tsx` (~8 tests): (1) TodaysAgenda renders today's tasks section, (2) TodaysAgenda renders time progress bar, (3) TodaysAgenda renders next deadline, (4) TodaysAgenda handles empty tasks, (5) WeeklyRhythmStrip renders 7 day columns, (6) WeeklyRhythmStrip highlights current day, (7) WeeklyRhythmStrip calls onDaySelect on click, (8) My Work page renders TodaysAgenda + WeeklyRhythmStrip (not PersonalKpis). Pattern: follow `__tests__/dashboard/my-work-dashboard.test.tsx`. |

### Key Files

**Slice 394A — Create:**
- `frontend/components/dashboard/metrics-strip.tsx`
- `frontend/components/dashboard/admin-stats-column.tsx`
- `frontend/components/dashboard/my-week-column.tsx`
- `frontend/__tests__/dashboard/metrics-strip.test.tsx`

**Slice 394A — Modify:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — Full layout restructure
- `frontend/components/dashboard/getting-started-card.tsx` — Banner format + auto-hide
- `frontend/components/dashboard/project-health-widget.tsx` — Dense table, sorting
- `frontend/components/dashboard/team-workload-widget.tsx` — Utilization gradient, DonutChart
- `frontend/components/dashboard/recent-activity-widget.tsx` — Compact rows, 15 items
- `frontend/components/dashboard/deadline-widget.tsx` — List format, urgency colors
- `frontend/__tests__/dashboard/company-dashboard.test.tsx` — Update structural assertions

**Slice 394A — Read for context:**
- `frontend/components/dashboard/kpi-card-row.tsx` — Being replaced by MetricsStrip (understand its data props)
- `frontend/components/dashboard/kpi-card.tsx` — Individual KPI card being replaced
- `frontend/app/(app)/org/[slug]/dashboard/dashboard-header.tsx` — Preserved, understand positioning
- `frontend/components/dashboard/incomplete-profiles-widget.tsx` — Data source for AdminStatsColumn
- `frontend/components/dashboard/information-requests-widget.tsx` — Data source for AdminStatsColumn
- `frontend/components/dashboard/my-schedule-widget.tsx` — Being removed from Org Dashboard

**Slice 394B — Create:**
- `frontend/components/dashboard/todays-agenda.tsx`
- `frontend/components/dashboard/weekly-rhythm-strip.tsx`
- `frontend/__tests__/dashboard/weekly-rhythm-strip.test.tsx`
- `frontend/__tests__/dashboard/todays-agenda.test.tsx`

**Slice 394B — Modify:**
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — Full layout restructure
- `frontend/app/(app)/org/[slug]/my-work/my-work-tasks-client.tsx` — Compact spacing
- `frontend/components/my-work/today-time-entries.tsx` — Compact rows
- `frontend/components/my-work/time-breakdown.tsx` — Replace pie with DonutChart
- `frontend/__tests__/dashboard/my-work-dashboard.test.tsx` — Update assertions
- `frontend/__tests__/dashboard/my-work-enhancements.test.tsx` — Update assertions

**Slice 394B — Read for context:**
- `frontend/components/my-work/personal-kpis.tsx` — Being replaced by TodaysAgenda (understand data shape)
- `frontend/components/my-work/weekly-time-summary.tsx` — Data feeds WeeklyRhythmStrip
- `frontend/components/my-work/upcoming-deadlines.tsx` — Deadline moved into hero
- `frontend/components/my-work/my-expenses.tsx` — Preserved in extended widgets
- `frontend/app/(app)/org/[slug]/my-work/actions.ts` — Server actions for data fetching

### Architecture Decisions

- **MetricsStrip replaces KpiCardRow**: The KpiCardRow renders 3-5 large cards (~100px each). MetricsStrip renders 6 compact cells in a single ~80-100px strip with inline visualizations (Sparkline, RadialGauge, MicroStackedBar). Same data, higher density.
- **GettingStartedCard becomes a banner**: The card pushed the metrics strip below the fold on smaller screens. A slim 48px banner adds context without consuming vertical space.
- **No new data fetching**: All data for the redesigned layouts comes from existing server actions (`fetchDashboardKpis()`, `fetchProjectHealth()`, `fetchTeamWorkload()`, `fetchDashboardActivity()`). The layouts are recomposed, not re-queried.
- **WeeklyRhythmStrip as both visualization and filter**: The strip communicates "how am I tracking?" at a glance while also serving as a click-to-filter control for the task and time panels below. State lives in the page component.
- **TodaysAgenda replaces PersonalKpis**: The hero section answers "what should I work on right now?" rather than showing abstract KPI numbers. Same underlying data, more actionable presentation.
- **MyScheduleWidget removed from Org Dashboard**: Allocation/leave data is already covered by the My Work page. The MyWeekColumn for members shows hours/tasks/upcoming from existing KPI data, not allocations.

---

## Epic 395: Project Detail & Document Preview

**Goal**: Polish the project detail overview tab with a colored health band, compact setup progress, and two-panel body layout. Separately, fix document preview rendering to use A4-proportioned containers with CSS transform scaling for print-accurate previews.

**References**: Architecture doc Sections 53.5 (project overview polish), 53.9 (document preview print-accuracy).

**Dependencies**: 395A depends on 393A (DonutChart, MicroStackedBar). 395B is fully independent.

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **395A** | 395.1-395.5 | Project detail overview: health band + metrics strip, compact setup bar (auto-hide), two-panel body (activity/tasks + financial/team), tests (~5 tests) | |
| **395B** | 395.6-395.9 | A4PreviewWrapper component, GenerateDocumentDialog integration, template editor preview integration, tests (~4 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 395.1 | Add health header colored band | 395A | | Modify `components/projects/overview-tab.tsx`. Replace `OverviewHealthHeader` text-only display with colored band: `border-t-4` with color mapped from health status (green=HEALTHY, amber=AT_RISK, red=CRITICAL, slate=UNKNOWN). Metrics strip below (~60-80px): Budget spent/total (%), Hours logged/estimated, Tasks completed/total, Revenue invoiced. Numbers in `font-mono tabular-nums`. `data-testid="project-health-header"`. See Section 53.5.2. |
| 395.2 | Replace setup cards with compact checklist bar | 395A | | Modify `components/projects/overview-tab.tsx`. Replace `SetupProgressCard` + `ActionCard` + `TemplateReadinessCard` instances: if `completedSteps < totalSteps`, show compact checklist bar: `"3/6 setup steps complete"` with `CompletionProgressBar` (existing component) and collapsible detail. If all complete: **hide entirely**. `data-testid="setup-progress-bar"` (only rendered when incomplete). See Section 53.5.2. |
| 395.3 | Restructure overview body to two-panel layout | 395A | | Modify `components/projects/overview-tab.tsx`. Replace existing body with 60/40 split: Left (`col-span-3`): recent project activity (10 items, compact), task status `MicroStackedBar` (Open/InProgress/Done), upcoming task deadlines (5 items). `data-testid="activity-tasks-panel"`. Right (`col-span-2`): budget `CompletionProgressBar` with threshold coloring, time breakdown DonutChart (billable vs non-billable by member), compact team roster (avatar initials row with tooltip), unbilled time callout with "Generate Invoice" CTA. `data-testid="financial-team-panel"`. See Section 53.5.2. |
| 395.4 | Update overview tab data integration | 395A | | Ensure all data for the new layout is sourced from existing `Promise.allSettled` fetches in the overview tab server component. No new API calls. Verify: project health data, setup progress, task summary, activity, team members, budget, time entries, unbilled amount are all available from current data flow. Wire into new sub-sections. |
| 395.5 | Add/update overview tab tests | 395A | | Update `__tests__/components/projects/overview-tab-setup.test.tsx` and `__tests__/dashboard/project-overview.test.tsx` (~5 tests): (1) Health band renders with correct color for AT_RISK project, (2) Setup bar hides when all steps complete, (3) Setup bar shows progress when incomplete, (4) Activity/tasks panel renders activity list, (5) Financial/team panel renders budget bar. Pattern: follow existing test files. |
| 395.6 | Create `A4PreviewWrapper` component | 395B | | `components/documents/a4-preview-wrapper.tsx`. "use client". Props: `html: string, className?: string`. Constants: `A4_WIDTH = 794`, `A4_HEIGHT = 1123` (px at 96 DPI). Uses `useRef` + `ResizeObserver` to calculate scale factor: `containerWidth / A4_WIDTH`. Renders: outer container `bg-slate-800 p-6 rounded-lg` (dark surround), sized wrapper at `A4_WIDTH * scale` x `A4_HEIGHT * scale`, inner div at full A4 size with `transform: scale(${scale})` + `transformOrigin: "top center"`, paper div `bg-white shadow-xl ring-1 ring-slate-200/20`, iframe `sandbox="" srcDoc={html}`. `data-testid="a4-preview-wrapper"`. Full spec in Section 53.9.3. |
| 395.7 | Integrate A4PreviewWrapper into GenerateDocumentDialog | 395B | | Modify `components/templates/GenerateDocumentDialog.tsx`. Replace the existing unconstrained iframe with `<A4PreviewWrapper html={html} />`. Import from `@/components/documents/a4-preview-wrapper`. Keep all dialog behavior, form fields, and generation logic unchanged. See Section 53.9.5. |
| 395.8 | Integrate A4PreviewWrapper into template editor preview | 395B | | Locate the template editor preview component in `app/(app)/org/[slug]/settings/templates/[id]/edit/` or `components/templates/TemplateEditor.tsx`. If a preview iframe exists there, wrap it with `A4PreviewWrapper`. If no standalone preview exists (preview only in GenerateDocumentDialog), skip this task. |
| 395.9 | Add A4PreviewWrapper tests | 395B | | `__tests__/components/documents/a4-preview-wrapper.test.tsx` (~4 tests): (1) renders iframe with srcDoc, (2) applies data-testid attribute, (3) renders dark surround container, (4) renders paper shadow effect. Note: ResizeObserver and scale calculation are hard to test in happy-dom; focus on structural rendering. Pattern: follow `__tests__/components/documents/documents-panel.test.tsx`. |

### Key Files

**Slice 395A — Modify:**
- `frontend/components/projects/overview-tab.tsx` — Health band, setup bar, two-panel body

**Slice 395A — Read for context:**
- `frontend/components/projects/overview-health-header.tsx` — Being replaced with health band
- `frontend/components/projects/overview-metrics-strip.tsx` — Existing metrics strip (evolving)
- `frontend/components/dashboard/completion-progress-bar.tsx` — Reused for budget, setup progress
- `frontend/components/dashboard/health-badge.tsx` — Reused in health header
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Server component providing data to overview tab
- `frontend/__tests__/components/projects/overview-tab-setup.test.tsx` — Existing test pattern
- `frontend/__tests__/dashboard/project-overview.test.tsx` — Existing test pattern

**Slice 395B — Create:**
- `frontend/components/documents/a4-preview-wrapper.tsx`
- `frontend/__tests__/components/documents/a4-preview-wrapper.test.tsx`

**Slice 395B — Modify:**
- `frontend/components/templates/GenerateDocumentDialog.tsx` — Replace iframe with A4PreviewWrapper

**Slice 395B — Read for context:**
- `frontend/components/templates/TemplateEditor.tsx` — Check for preview rendering
- `frontend/app/(app)/org/[slug]/settings/templates/[id]/edit/` — Template editor page

### Architecture Decisions

- **Health band uses `border-t-4` with color mapping**: A thick top border provides a strong visual health signal without adding layout complexity. Color mapping: green (HEALTHY), amber (AT_RISK), red (CRITICAL), slate (UNKNOWN).
- **Setup progress auto-hides when complete**: A completed setup section adds no value after the first week. Hiding it reclaims premium above-the-fold space for project health data.
- **A4PreviewWrapper uses CSS `transform: scale()`, not responsive sizing**: The document must render at its full A4 width (794px) for accurate font sizing and layout, then scale down to fit the container. Responsive sizing would change the document's actual render width, altering line breaks and pagination.
- **ResizeObserver for dynamic scaling**: The container width varies by dialog size, window size, and sidebar state. A one-time calculation would be stale after window resize. ResizeObserver ensures the scale stays current.
- **Dark surround (bg-slate-800)**: Matches the PDF viewer convention users expect — a dark background with a floating white page. Creates visual separation between the app UI and the document content.

---

## Epic 396: Test Updates & Visual Baselines

**Goal**: Comprehensive test stabilization pass after all visual changes are complete. Update all broken E2E tests, refresh visual regression baselines, verify data-testid coverage, and ensure the full test suite passes green.

**References**: Architecture doc Section 53.11.3 (testing strategy).

**Dependencies**: All previous slices (393A, 393B, 394A, 394B, 395A, 395B)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **396A** | 396.1-396.5 | Fix all broken tests from layout changes, update E2E selectors, refresh visual regression baselines, verify data-testid coverage, run full suite green (~0 new tests, updates only) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 396.1 | Fix broken sidebar/navigation tests | 396A | | Run `pnpm test` and identify failures in sidebar-related tests. Update selectors: "Delivery" -> "Projects", "Team & Resources" -> "Team", Documents item removed, Proposals in Clients group. Check `__tests__/dashboard/company-dashboard.test.tsx` and any sidebar-specific test files. Pattern: search test files for "Delivery", "Team & Resources", "Documents" string assertions. |
| 396.2 | Fix broken dashboard page tests | 396A | | Update `__tests__/dashboard/company-dashboard.test.tsx`: KpiCardRow references -> MetricsStrip, widget ordering changes, new data-testid selectors. Update `__tests__/dashboard/kpi-card-row.test.tsx` if MetricsStrip fully replaces KpiCardRow (may need to delete or redirect). Update `__tests__/dashboard/kpi-card.test.tsx` similarly. Ensure all assertions use `data-testid` selectors. |
| 396.3 | Fix broken My Work and project overview tests | 396A | | Update `__tests__/dashboard/my-work-dashboard.test.tsx`: PersonalKpis replaced by TodaysAgenda, TimeBreakdown row removed, WeeklyRhythmStrip added. Update `__tests__/dashboard/project-overview.test.tsx`: setup card visibility changes, health header redesign. Update `__tests__/components/my-work/weekly-time-summary.test.tsx` and `__tests__/components/my-work/assigned-task-list.test.tsx` if layout assumptions changed. |
| 396.4 | Verify data-testid coverage across all new/modified components | 396A | | Grep all components modified/created in this phase for `data-testid` attributes. Verify all testids from the architecture doc are present: `getting-started-banner`, `metrics-strip`, `metric-{name}`, `project-health-panel`, `team-time-panel`, `admin-stats-column`, `my-week-column`, `todays-tasks`, `time-progress-today`, `next-deadline`, `weekly-rhythm-strip`, `rhythm-day-{index}`, `tasks-panel`, `time-activity-panel`, `extended-widgets`, `project-health-header`, `setup-progress-bar`, `activity-tasks-panel`, `financial-team-panel`, `a4-preview-wrapper`, `org-documents-section`, `sparkline`, `radial-gauge`, `micro-stacked-bar`, `donut-chart`, `chart-tooltip`. |
| 396.5 | Run full test suite and fix remaining failures | 396A | | Run `pnpm test` (Vitest full suite). Fix any remaining failures. Run `pnpm run lint` to verify no linting errors. Run `pnpm run build` to verify no TypeScript compilation errors. If E2E tests (Playwright) are configured, run those and update any broken selectors or screenshot baselines. Document any test patterns that changed in a summary for the PR description. |

### Key Files

**Slice 396A — Modify (test files only):**
- `frontend/__tests__/dashboard/company-dashboard.test.tsx` — Sidebar + dashboard layout assertions
- `frontend/__tests__/dashboard/kpi-card-row.test.tsx` — May need to delete or redirect to MetricsStrip
- `frontend/__tests__/dashboard/kpi-card.test.tsx` — May need to delete if KpiCard is unused
- `frontend/__tests__/dashboard/my-work-dashboard.test.tsx` — Layout restructure assertions
- `frontend/__tests__/dashboard/my-work-enhancements.test.tsx` — Layout changes
- `frontend/__tests__/dashboard/project-overview.test.tsx` — Health header, setup progress
- `frontend/__tests__/dashboard/personal-kpis.test.tsx` — May need deletion if PersonalKpis replaced
- `frontend/__tests__/dashboard/time-breakdown.test.tsx` — DonutChart replacement
- `frontend/__tests__/dashboard/upcoming-deadlines.test.tsx` — Relocated into hero/secondary

### Architecture Decisions

- **Test files only, no production code**: This slice exists to catch any test breakage not addressed by the individual slice test tasks. It touches zero production files — only test files and baselines.
- **data-testid as the stability contract**: All structural assertions in tests should use `data-testid` selectors, not CSS class names or DOM hierarchy. This decouples tests from visual styling changes and makes the test suite resilient to future design iterations.
- **Build verification included**: `pnpm run build` catches TypeScript errors that `pnpm test` (Vitest) might miss (e.g., unused imports from deleted components, type mismatches in changed props).
