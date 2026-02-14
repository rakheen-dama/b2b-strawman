# Phase 9 Handover — Operational Dashboards

## Status: 7 of 12 slices complete

| Slice | Status | PR | Notes |
|-------|--------|-----|-------|
| **75A** | Done | #151 | HealthStatus enum, ProjectHealthCalculator, records, 16 unit tests |
| **75B** | Done | #153 | DashboardService (project health), endpoints, Caffeine cache, 8 integration tests |
| **77A** | Done | #154 | KpiCard, HealthBadge, MiniProgressRing, SparklineChart (SVG), 14 tests |
| **77B** | Done | #155 | HorizontalBarChart (Recharts), DateRangeSelector, 7 tests. `recharts` dep added. |
| **76A** | Done | #156 | Company KPI endpoint, project health list, org-level cache, financial redaction |
| **76B** | Done | #157 | Team workload endpoint, cross-project activity endpoint, 12 integration tests, V22 indexes |
| **79A** | Done | #158 | Project member-hours, personal dashboard, utilization, upcoming deadlines, 7 integration tests |
| **78A** | **Next** | -- | Dashboard page replacement, KpiCardRow, ProjectHealthWidget |
| **78B** | Pending | -- | TeamWorkloadWidget, RecentActivityWidget, responsive layout |
| **79B** | Pending | -- | OverviewTab, project detail tab integration |
| **80A** | Pending | -- | PersonalKpis, TimeBreakdown chart, UpcomingDeadlines |
| **80B** | Pending | -- | Enhanced My Work page with dashboard header |

## Remaining Execution Order

```
Stage 2 (remaining): [76B]  //  [79A]     ← can run in parallel, but /phase runs sequentially
Stage 3:             [78A → 78B]  //  [79B]
Stage 4:             [80A → 80B]
```

## What's Built So Far

### Backend (`dashboard/` package)
- `HealthStatus` enum (HEALTHY, AT_RISK, CRITICAL, UNKNOWN)
- `ProjectHealthCalculator` — 6 deterministic rules, pure utility class
- `ProjectHealthInput` / `ProjectHealthResult` records
- `DashboardService` — project health, task summary, org KPIs, project health list, Caffeine caches (project 1min, org 3min)
- `DashboardController` — 4 endpoints:
  - `GET /api/projects/{id}/health`
  - `GET /api/projects/{id}/task-summary`
  - `GET /api/dashboard/kpis?from=&to=`
  - `GET /api/dashboard/project-health`
- DTOs: `ProjectHealthDetail`, `ProjectHealthMetrics`, `TaskSummary`, `KpiResponse`, `KpiValues`, `TrendPoint`, `ProjectHealth`
- Projections: `OrgHoursSummaryProjection`, `TrendProjection`
- New queries in `TaskRepository`, `TimeEntryRepository`, `ProjectRepository`, `AuditEventRepository`

### Frontend (`components/dashboard/`)
- `KpiCard` — metric card with label, value, trend, sparkline slot
- `HealthBadge` — sm/md/lg status badges
- `MiniProgressRing` — SVG ring chart
- `SparklineChart` — SVG sparkline (uses `useId()` for gradient uniqueness)
- `HorizontalBarChart` — Recharts stacked bar chart
- `DateRangeSelector` — preset date ranges with URL param sync

### Infrastructure
- `.epic-brief.md` added to `.gitignore` (prevents accidental commits)
- `recharts` dependency added to frontend

## Key Patterns Established

1. **Scout → Builder pipeline**: Works well. Scout writes `.epic-brief.md` to worktree, builder reads only the brief.
2. **Builder must NOT stage `.epic-brief.md`**: The `.gitignore` entry now prevents this, but remind builders explicitly.
3. **Review findings**: Most "critical" findings from reviewers are defensive hardening, not actual vulnerabilities. Triage carefully — fix genuine bugs (gradient ID collision, no-activity sentinel), skip theoretical issues.
4. **Caffeine cache keys**: Always include `tenantId` + `orgRole`. Never include `memberId` for org-level caches.
5. **Financial redaction**: Single redaction point after cache lookup, not duplicated in hit/miss paths.

## How to Resume

Run `/phase 9` — the skill reads the status table and skips Done slices. The next slice is **76B**.

Or manually:
```
/phase 9 from 76B
```

## ADRs Referenced
- ADR-044: Aggregation caching (Caffeine, short TTLs)
- ADR-045: Project health scoring (deterministic rule-based)
- ADR-046: Hybrid SVG + Recharts charting
- ADR-047: Fixed dashboard layout

## Task File
`tasks/phase9-operational-dashboards.md` — full epic/task descriptions

## Test Counts (approximate)
- Backend: ~870+ tests (added ~30 in this session)
- Frontend: ~303 tests (added ~21 in this session)
