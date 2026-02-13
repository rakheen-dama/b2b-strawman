You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema-per-tenant) tiers.
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups ("My Work" cross-project personal view).
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Customer portal backend** (Phase 7): magic links, read-model schema, portal contacts, Thymeleaf harness.
- **Rate cards, budgets, and profitability** (Phase 8 — in progress): billing rates with three-level hierarchy (member default → customer → project override), cost rates, billable time classification with point-in-time rate snapshots, project budgets with threshold alerts, and query-derived profitability endpoints (`/api/reports/utilization`, `/api/reports/profitability`, `/api/projects/{id}/profitability`, `/api/projects/{id}/budget/status`).

For **Phase 9**, I want to add an **operational dashboards layer** — the connective tissue that turns existing data into at-a-glance situational awareness. Today, every entity (projects, customers, tasks, time entries) lives on its own page behind 2-3 clicks. Users can't answer "how is my business doing?" without navigating to half a dozen different screens. This phase fixes that.

***

## Objective of Phase 9

Design and specify:

1. **Company Dashboard** — the org-level home screen for owners and admins. KPI cards with trend data, project health overview, team workload distribution, and cross-cutting "needs attention" widgets.
2. **Project Overview Tab** — a new default tab on the project detail page that surfaces health, budget, tasks, time, and activity in a single view, replacing the current tab-first navigation with an at-a-glance summary.
3. **Personal Dashboard** — an upgrade to the existing "My Work" page that adds utilization metrics, time breakdowns, and productivity context around the existing task list.
4. **Project Health Scoring** — a computed health score for every project based on budget consumption, task completion, overdue ratio, and activity recency.
5. **Dashboard Aggregation Backend** — new API endpoints purpose-built for dashboard widgets, optimized for fast reads with short-TTL caching.
6. **Shared Dashboard Components** — reusable chart and widget components that establish a visual vocabulary for data visualization across the app.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - Charting libraries that require heavy client-side JS bundles. Use lightweight options: Recharts (already popular in the React ecosystem, tree-shakeable) or a minimal SVG-based approach for sparklines. Evaluate and decide in ADR.
    - WebSocket or SSE connections for real-time updates — dashboards load on page visit and can be manually refreshed. Consistent with ADR-038 (polling approach).
    - Separate analytics databases, data warehouses, OLAP cubes, or external BI tools — all queries run against the existing Postgres database.
    - Background jobs or scheduled workers for pre-computing aggregations — compute on read, cache in memory.
- Use **Caffeine in-process cache** (already a project dependency) for expensive aggregation queries. Short TTL (2-5 minutes) to balance freshness with performance.
- All dashboard endpoints return pre-computed/aggregated data — the frontend should NOT need to make N+1 calls or client-side aggregate raw data.

2. **Tenancy**

- No new persistent entities are introduced in this phase. All dashboard data is derived from existing entities (Projects, Tasks, TimeEntries, Members, ProjectBudgets, BillingRates, CostRates).
- All aggregation queries must respect tenant isolation:
    - Pro orgs: queries scoped to the dedicated schema.
    - Starter orgs: queries in `tenant_shared` with `tenant_id` filter via Hibernate `@Filter` or RLS (`app.current_tenant` set by `TenantFilterTransactionManager`).
- Native SQL aggregation queries (which bypass Hibernate `@Filter`) rely on RLS policies — do NOT add manual `tenant_id` WHERE clauses.

3. **Permissions model**

- Company Dashboard:
    - Full dashboard visible to org admins/owners.
    - Members see a limited version: their own utilization, projects they have access to, no org-wide financial metrics (billable value, margin).
    - Team workload widget: admins/owners see all members. Members see only their own row.
- Project Overview Tab:
    - Visible to all project members (same access as existing project detail tabs).
    - Financial metrics (billable value, margin) within the overview are visible only to project leads and org admins/owners.
- Personal Dashboard:
    - Every authenticated member sees their own data. No cross-member visibility.

4. **Data dependencies on Phase 8**

- Dashboard KPIs and project overview will reference Phase 8 data (billing rates, budgets, profitability). Design the dashboard to **degrade gracefully** when Phase 8 data is incomplete:
    - If no rates are configured: utilization shows hours only (no billable value). Financial KPI cards show "Set up rates to see revenue data" placeholder.
    - If no budget is set for a project: health score uses task-based metrics only. Budget widget shows "No budget configured" with a link to set one up.
    - If no time entries exist: metrics show zero with an appropriate empty state, not errors.
- Phase 9 should be implementable in parallel with the remaining Phase 8 epics (68-74) — it depends on Phase 8's data model (entities, schemas) being in place, but not on Phase 8's frontend being complete.

5. **Out of scope for Phase 9**

- Custom/configurable dashboard layouts (widget drag-and-drop, widget selection) — opinionated defaults only.
- Saved dashboard views or dashboard sharing.
- PDF/CSV export of dashboard data — deferred to a future reporting phase.
- Scheduled dashboard email digests — deferred.
- Real-time or live-updating dashboards — load on visit, manual refresh.
- Customer-facing dashboards (portal scope) — the portal has its own views.
- Historical trend data beyond 12 weeks — dashboards show current state + recent trends, not long-range analytics.
- Drill-down charts (clicking a bar to filter the whole dashboard) — each widget links to its corresponding detail page, not to a filtered dashboard state.

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase9-operational-dashboards.md`, plus ADRs for key decisions.

### 1. Dashboard Aggregation Backend

Design the backend services and endpoints that power all three dashboards:

1. **DashboardService** — a service class that encapsulates all aggregation queries and caching logic.

    The service should have methods for each dashboard "widget" — each method returns a pre-shaped DTO ready for the API response. All methods accept a date range (`from`, `to`) for period scoping.

    Caching strategy: Use Caffeine cache with composite keys `(tenantId, endpoint, dateRange)`. TTL of 3 minutes for org-level metrics, 1 minute for project-level metrics. Cache is per-tenant to respect isolation. Cache eviction on write operations is NOT required — short TTL handles staleness acceptably.

2. **Company Dashboard endpoints**

    - `GET /api/dashboard/kpis?from=&to=` — returns org-level KPI values:
        - `activeProjectCount`: projects with status ACTIVE.
        - `totalHoursLogged`: sum of all time entry durations in the period.
        - `billablePercent`: billable hours / total hours × 100 in the period.
        - `overdueTaskCount`: tasks with status != DONE and due date < today.
        - `averageMarginPercent`: weighted average margin across all projects with financial data (null if no rate data).
        - `trend`: array of `{ period: "YYYY-Www" or "YYYY-MM-DD", value }` objects for the primary KPI (hours logged) over the last 6 periods at the appropriate granularity (daily if range ≤ 7 days, weekly if range ≤ 90 days, monthly otherwise).
        - `previousPeriod`: same KPIs computed for the equivalent prior period (e.g., if `from`/`to` spans this month, `previousPeriod` spans last month). Used for delta/change indicators on KPI cards.
    - `GET /api/dashboard/project-health` — returns a list of all projects the user has access to, with computed health:
        - `projectId`, `projectName`, `customerName` (nullable).
        - `healthStatus`: enum `HEALTHY`, `AT_RISK`, `CRITICAL`, `UNKNOWN`.
        - `healthReasons`: array of strings explaining the status (e.g., "Budget 92% consumed", "5 overdue tasks", "No activity in 14 days").
        - `tasksDone`, `tasksTotal`, `completionPercent`.
        - `budgetConsumedPercent` (nullable — null if no budget).
        - `hoursLogged` in the period.
        - Sorted by severity: CRITICAL first, then AT_RISK, then HEALTHY. Within each group, sorted by `completionPercent` ascending (least complete first).
    - `GET /api/dashboard/team-workload?from=&to=` — returns workload distribution:
        - Array of `{ memberId, memberName, totalHours, billableHours, projects: [{ projectId, projectName, hours }] }`.
        - Each member's `projects` array is sorted by hours descending, capped at top 5 projects + an "Other" aggregate.
        - Only members with logged hours in the period are included.
        - Org admins/owners: all members. Regular members: only their own entry.

    For each endpoint specify:
    - Auth requirement (valid Clerk JWT).
    - Tenant scoping.
    - Permission checks (see permissions model above).
    - Request/response DTOs.
    - Caching strategy (key, TTL).

3. **Project Overview endpoints**

    - `GET /api/projects/{projectId}/health` — returns the project's computed health score:
        - `healthStatus`: `HEALTHY` / `AT_RISK` / `CRITICAL` / `UNKNOWN`.
        - `healthReasons`: array of explanatory strings.
        - `metrics`: `{ tasksDone, tasksInProgress, tasksTodo, tasksOverdue, totalTasks, completionPercent, budgetConsumedPercent (nullable), hoursThisPeriod, daysSinceLastActivity }`.
    - `GET /api/projects/{projectId}/task-summary` — returns task counts grouped by status:
        - `{ todo, inProgress, inReview, done, total, overdueCount }`.
        - Simple aggregation query on tasks table filtered by project.
    - `GET /api/projects/{projectId}/member-hours?from=&to=` — returns hours breakdown by member:
        - Array of `{ memberId, memberName, totalHours, billableHours }`.
        - Sorted by totalHours descending.

    Note: The project overview tab will also consume existing endpoints from Phase 5 and Phase 8:
    - `GET /api/projects/{projectId}/budget/status` (Phase 8) — budget consumption.
    - `GET /api/projects/{projectId}/profitability` (Phase 8) — financial metrics.
    - `GET /api/projects/{projectId}/activity` (Phase 6.5) — recent activity.

4. **Personal Dashboard endpoints**

    - `GET /api/dashboard/personal?from=&to=` — returns the current member's dashboard data:
        - `utilization`: `{ totalHours, billableHours, billablePercent }` for the period.
        - `projectBreakdown`: array of `{ projectId, projectName, hours, percent }` — how time is distributed across projects. Sorted by hours descending.
        - `overdueTaskCount`: tasks assigned to this member with due date < today and status != DONE.
        - `upcomingDeadlines`: array of `{ taskId, taskName, projectName, dueDate }` — next 5 tasks by due date.
        - `trend`: array of `{ period, hours }` for the last 6 periods (same granularity logic as company KPIs).

5. **Health scoring algorithm**

    Define the project health scoring logic as a deterministic, rule-based algorithm:

    ```
    Start: HEALTHY

    If project has a budget:
        If budgetConsumedPercent >= 100 → CRITICAL (reason: "Over budget")
        If budgetConsumedPercent >= alertThreshold AND completionPercent < budgetConsumedPercent - 10 → AT_RISK (reason: "Budget {X}% consumed but only {Y}% of tasks complete")

    If overdueTaskCount / totalTasks > 0.3 → CRITICAL (reason: "{N} of {M} tasks overdue")
    If overdueTaskCount / totalTasks > 0.1 → AT_RISK (reason: "{N} overdue tasks")

    If totalTasks > 0 AND daysSinceLastActivity > 14 → AT_RISK (reason: "No activity in {N} days")

    If totalTasks == 0 → UNKNOWN (reason: "No tasks created yet")

    Severity escalation: if multiple rules fire, take the worst status.
    ```

    Implement this as a pure function in a `ProjectHealthCalculator` utility class — no side effects, fully testable. The health status is never persisted — always computed from current data.

### 2. Company Dashboard (Home Screen)

Design the new org-level dashboard that replaces the current landing page:

1. **Page layout**

    - Route: `/dashboard` (replaces whatever the current post-login landing is).
    - **Header area**: Org name, date range selector (preset: "This Week", "This Month", "Last 30 Days", "This Quarter", "Custom").
    - **KPI cards row**: 4-5 cards in a responsive grid (2 columns on mobile, 4-5 on desktop).
    - **Main content area**: 2-column grid on desktop, single column on mobile.
        - Left column (wider): "Projects" widget showing project health list.
        - Right column (narrower): "Team Workload" widget + "Recent Activity" widget (stacked).
    - **Empty states**: Each widget has a meaningful empty state. E.g., "No time logged this period" with a link to the time tracking feature.

2. **KPI cards**

    Each card shows:
    - Label (e.g., "Active Projects").
    - Current value (large, prominent).
    - Change indicator: up/down arrow with percentage change vs. previous period. Green for positive changes (more billable %, fewer overdue), red for negative.
    - Sparkline: small inline trend chart (last 6 periods).
    - Click action: navigates to the relevant detail page (e.g., "Overdue Tasks" card → task list filtered to overdue).

    Cards:
    - **Active Projects** — count. Links to project list.
    - **Hours Logged** — total hours in period. Links to time entries / My Work.
    - **Billable %** — billable hours / total hours. Admins/owners only. Links to utilization report (Phase 8).
    - **Overdue Tasks** — count. Links to a filtered task view (or My Work for non-admins).
    - **Avg. Margin** — weighted average margin %. Admins/owners only. Links to profitability page (Phase 8). Shows placeholder if no rate data.

3. **Project Health widget**

    - List of projects with health badge (green/yellow/red dot), project name, customer name, completion progress bar, and health reasons as tooltip or subtitle text.
    - Sorted by severity (critical first).
    - Shows top 10 projects. "View all projects →" link at bottom.
    - Each row is clickable → navigates to project detail (Overview tab).
    - Filter tabs above the list: "All" / "At Risk" / "Critical" — quick filter without page reload.

4. **Team Workload widget**

    - Horizontal stacked bar chart: each bar is a team member, segments are projects (top 5 + "Other"), bar length = total hours.
    - Member names on Y-axis, hours on X-axis.
    - Color-coded by project (consistent colors across the dashboard).
    - Hover/tap reveals breakdown tooltip.
    - Below the chart: a simple legend showing the top projects by total hours.
    - Admins/owners see all members. Regular members see only their own bar (with a note: "Contact an admin to see team-wide data").

5. **Recent Activity widget**

    - Reuses the existing activity feed API and components from Phase 6.5.
    - Shows the 10 most recent activities across all projects the user has access to.
    - Each entry: avatar/icon, description, project name badge, timestamp.
    - "View all activity →" link.

6. **Responsive behavior**

    - Desktop (≥1024px): Full layout as described.
    - Tablet (768-1023px): KPIs in 2×2 grid, main content single column.
    - Mobile (<768px): KPIs stacked vertically (scrollable horizontal strip), widgets stacked.

### 3. Project Overview Tab

Design a new "Overview" tab as the default landing when opening a project:

1. **Tab structure**

    - Add "Overview" as the **first tab** in the project detail page.
    - Existing tabs (Tasks, Documents, Time, Activity, Financials/Budget from Phase 8) remain in their current order after Overview.
    - Overview becomes the default active tab when navigating to a project.

2. **Layout**

    - **Top row**: Health badge (large, with reasons listed below as small text), Project name + customer name, quick action buttons (Log Time, Add Task).
    - **Metrics strip**: 4 compact stat cards in a row:
        - Tasks: "{done}/{total} complete" with mini progress ring.
        - Hours: "{hours}h logged" with sparkline or "this period" qualifier.
        - Budget: "{consumed}% used" with mini progress bar (color-coded). "No budget" if none set.
        - Margin: "{margin}%" with up/down indicator. Visible to leads/admins only. "—" if no rate data.
    - **Two-column body**:
        - Left: "Tasks" mini-list — overdue tasks (if any, highlighted red) + next 5 upcoming tasks by due date. "View all tasks →" link.
        - Right top: "Team" — member hours breakdown (mini horizontal bars). "View time details →" link.
        - Right bottom: "Recent Activity" — last 5 activity items. "View all →" link.

3. **Interactivity**

    - Every section links to its corresponding deep-dive tab.
    - Health reasons are displayed as small pill badges (e.g., 🔴 "Over budget", 🟡 "3 overdue tasks").
    - Metrics strip values update when the global date range selector is changed (if one is present) — but the Overview tab does NOT have its own date range selector. It shows "current state" (tasks, budget) plus "this month" for time-based metrics.

4. **Server component strategy**

    - The Overview tab is a server component that fetches data from multiple endpoints in parallel (using `Promise.all`):
        - `/api/projects/{id}/health`
        - `/api/projects/{id}/task-summary`
        - `/api/projects/{id}/member-hours?from=&to=`
        - `/api/projects/{id}/budget/status` (Phase 8)
        - `/api/projects/{id}/profitability` (Phase 8)
        - `/api/projects/{id}/activity?limit=5` (Phase 6.5)
    - This avoids waterfalls and loads all widgets simultaneously.

### 4. Personal Dashboard (My Work Upgrade)

Enhance the existing "My Work" page into a personal dashboard:

1. **Current state**

    The existing My Work page (Phase 5) shows:
    - Cross-project task list (assigned to the current member).
    - Cross-project time entries.

    The enhancement adds a dashboard header above the existing content.

2. **Dashboard header section** (new, added above the existing task/time lists)

    - **My KPIs row**: 3 compact cards:
        - "Hours This Week/Month": total hours logged. Sparkline trend.
        - "Billable %": billable hours / total hours. Shows "—" if no rates configured.
        - "Overdue Tasks": count, red highlight if > 0.
    - **Time Breakdown**: Small horizontal bar chart showing hours per project (top 5 + "Other"). Color-coded, same project colors as company dashboard.
    - **Date range**: Matches the company dashboard selector presets. Defaults to "This Week".

3. **Enhanced task list** (modification to existing)

    - Add urgency indicators: overdue tasks highlighted with red left border and "X days overdue" badge.
    - Group by: "Overdue" section at top (if any), then "Due This Week", then "Upcoming", then "No Due Date".
    - The existing task list component is enhanced, not replaced.

4. **Upcoming Deadlines section** (new, below the dashboard header)

    - Visual timeline or simple list of next 5 deadlines across all projects.
    - Each entry: task name, project name, due date, days remaining.
    - Tasks due within 2 days highlighted amber, overdue highlighted red.

### 5. Shared Dashboard Components

Design a reusable component library for dashboard visualizations:

1. **KpiCard component**

    Props:
    - `label`: string.
    - `value`: string or number (formatted by caller).
    - `changePercent`: number (nullable — shows delta arrow if present).
    - `changeDirection`: "positive" | "negative" | "neutral" — determines color (green/red). The *meaning* of positive depends on the metric (more hours = positive, more overdue = negative), so the caller decides.
    - `trend`: array of numbers (for sparkline, nullable).
    - `href`: string (nullable — makes the card clickable).
    - `emptyState`: string (nullable — shown instead of value when data is missing).

    Visual: Card with subtle border, large value, small label above, change indicator + sparkline below.

2. **HealthBadge component**

    Props:
    - `status`: "HEALTHY" | "AT_RISK" | "CRITICAL" | "UNKNOWN".
    - `reasons`: string array (nullable — shown as tooltip or expandable list).
    - `size`: "sm" | "md" | "lg".

    Visual: Colored dot (green/yellow/red/gray) with optional label text. Tooltip shows reasons on hover.

3. **MiniProgressRing component**

    Props:
    - `value`: number (0-100, percentage).
    - `size`: number (px, default 32).
    - `color`: string (auto from value: green > 66, yellow > 33, red ≤ 33 — or caller-specified).

    Visual: Small SVG circular progress indicator. Used for task completion in compact contexts.

4. **SparklineChart component**

    Props:
    - `data`: number array.
    - `width`: number (default 80).
    - `height`: number (default 24).
    - `color`: string.

    Visual: Minimal SVG polyline with no axes, labels, or grid. Just the trend shape. Fills the available space.

5. **HorizontalBarChart component**

    Props:
    - `data`: array of `{ label, segments: [{ label, value, color }] }`.
    - `maxValue`: number (nullable — auto-calculated if omitted).
    - `showLegend`: boolean.

    Visual: Stacked horizontal bars with labels on Y-axis, values on hover/tap. Used for team workload and personal time breakdown.

6. **DateRangeSelector component**

    Props:
    - `value`: `{ from: Date, to: Date }`.
    - `onChange`: callback.
    - `presets`: array of `{ label, from, to }`.

    Presets: "This Week" (Mon-Sun), "This Month", "Last 30 Days", "This Quarter", "Custom" (opens date picker).

    This component should use URL search params (`?from=&to=`) so that date range selections are shareable and survive page refreshes.

7. **Charting library decision**

    Evaluate in ADR:
    - **Option A: Recharts** — popular React charting library, good Shadcn/Tailwind integration, tree-shakeable. Handles bar charts, sparklines, progress indicators.
    - **Option B: Custom SVG components** — minimal bundle size, full control, but more implementation effort. Good for sparklines and simple charts, less practical for interactive bar charts.
    - **Option C: Hybrid** — custom SVG for simple visualizations (sparkline, progress ring), Recharts for complex charts (stacked bar, future charts).

    Recommendation: Option C (hybrid). Sparklines and progress rings are trivial in SVG and don't justify a library dependency. The stacked bar chart benefits from Recharts' layout engine and interactivity.

### 6. Performance and Caching Strategy

Design the caching and performance approach for dashboard queries:

1. **Caffeine cache configuration**

    - Cache name: `dashboardCache`.
    - Key structure: `"{tenantId}:{endpoint}:{dateRangeHash}"` — string-based composite key.
    - TTL: 3 minutes for org-level aggregations (KPIs, project health, team workload). 1 minute for project-level metrics (health, task summary, member hours).
    - Max entries: 1000 per tenant (prevents unbounded memory growth).
    - No write-through eviction — short TTL is sufficient.

2. **Query optimization**

    - All aggregation queries should use a single SQL query per widget where possible (no N+1 patterns).
    - Use native SQL for complex aggregations (utilization, workload distribution) — these bypass Hibernate `@Filter` but are covered by RLS.
    - Use JPQL for simpler queries (task counts, overdue count) where Hibernate `@Filter` suffices.
    - Consider adding database indexes for common aggregation patterns:
        - `time_entries (project_id, date)` — for period-scoped time aggregation.
        - `tasks (project_id, status, due_date)` — for task summary and overdue counts.
        - Evaluate whether existing indexes cover these patterns before adding new ones.

3. **Frontend data fetching**

    - Company Dashboard: server component that fetches all widget data in parallel via `Promise.all`. Each widget renders independently (with its own loading/error state via Suspense boundaries).
    - Project Overview: same parallel fetch pattern.
    - Personal Dashboard: single endpoint (`/api/dashboard/personal`) returns all personal data in one response to minimize round-trips.
    - No client-side polling or auto-refresh. Manual refresh via a refresh button in the date range selector bar.

### 7. ADRs for key decisions

Add ADR-style sections for:

1. **Dashboard data: query-time aggregation with in-memory caching**
    - Why not materialized views, pre-computed tables, or background jobs.
    - Why Caffeine in-process cache is sufficient at the expected scale (tens of thousands of time entries per tenant, not millions).
    - TTL trade-offs: freshness vs. database load.
    - When to revisit: if P95 dashboard load time exceeds 2 seconds, introduce materialized views or read replicas.
    - Why per-tenant cache isolation is critical for multitenancy.

2. **Project health scoring: discrete rule-based algorithm**
    - Why discrete rules (HEALTHY/AT_RISK/CRITICAL) over weighted numeric scores (0-100).
    - Advantages: explainable (users see *why* a project is at risk), debuggable, no arbitrary weights to tune.
    - The reasons array is the key UX differentiator — users don't just see red, they see "Budget 92% consumed but only 60% of tasks complete."
    - Why health is computed, never stored — avoids stale scores and staleness bugs.
    - Future extension: allow orgs to configure threshold values (the 30%/10% overdue thresholds, the 14-day inactivity window).

3. **Charting approach: hybrid SVG + Recharts**
    - Why pure SVG for simple visualizations (sparklines, progress rings) — zero dependencies, tiny bundle, full design control.
    - Why Recharts for complex visualizations (stacked bar charts) — layout, interactivity, accessibility, tooltips.
    - Bundle impact analysis: Recharts tree-shaken import of `BarChart` + `Bar` + `Tooltip` vs. full library.
    - Why not D3 (too low-level for this use case), Chart.js (canvas-based, harder to style with Tailwind), or Nivo (heavier than Recharts).

4. **Dashboard layout: opinionated fixed layout, not configurable**
    - Why not widget customization in v1 — complexity of persistence (user preferences table), drag-and-drop UX, testing surface area.
    - The "lean" approach: ship an opinionated layout, observe what users actually look at, then optimize. Customization is a v2 feature if needed.
    - Why the specific layout choices (KPIs on top, health list left, workload right) — follows the F-reading-pattern, most important info in the top-left quadrant.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Keep the design **generic and industry-agnostic** — utilization, project health, and workload distribution apply to all professional services verticals.
- Dashboards are **read-only views** — no mutations, no CRUD. Every widget is a lens on existing data with links to the pages where you can take action.
- Build on Phase 8's financial infrastructure — reference utilization and profitability endpoints, don't duplicate them. Dashboard-specific endpoints add *operational* metrics (health scoring, workload distribution, trend data) that Phase 8 doesn't cover.
- Build on Phase 6.5's activity feed — the company dashboard and project overview both surface recent activity using the existing feed API.
- Build on Phase 5's time tracking — all hour-based metrics derive from existing `TimeEntry` data.
- **Graceful degradation** is a first-class concern: every widget must render sensibly when its data source is empty or incomplete. A dashboard with zero time entries should still show project/task metrics. A dashboard with no rates configured should still show hours (just not billable value).
- Frontend additions use the existing Shadcn UI design system. Dashboard-specific components (KpiCard, HealthBadge, etc.) should feel native to the existing design language — same border radii, shadows, color palette, typography scale.
- The Company Dashboard replaces the current post-login landing page. The sidebar navigation item "Dashboard" (if it exists) or the app logo should navigate here.
- URL-based date range (`?from=YYYY-MM-DD&to=YYYY-MM-DD`) enables bookmarkable and shareable dashboard states.
- All dashboard components must be responsive (mobile-friendly). The experience degrades gracefully on small screens — fewer columns, stacked layout, but no hidden data.
- No new database migrations in this phase. If indexes are needed for performance, add them as part of an existing migration version or as a new version, but the schema is not expected to change.

Return a single markdown document as your answer, ready to be added as `architecture/phase9-operational-dashboards.md` and ADRs.
