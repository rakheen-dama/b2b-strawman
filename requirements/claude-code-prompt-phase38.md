# Phase 38 — Resource Planning & Capacity

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 37, the platform has:

- **Time tracking (Phase 5)**: `TimeEntry` entity with duration, billable flag, rate snapshots, linked to tasks and projects. Members log time daily. `TimeEntryService` provides CRUD and aggregation queries.
- **Project members (Phase 1)**: `ProjectMember` entity linking members to projects with roles (OWNER, CONTRIBUTOR, VIEWER). `ProjectMemberService` manages assignments.
- **Rate cards (Phase 8)**: Three-level rate hierarchy (org → project → customer). `BillingRate` and `CostRate` entities. Rate resolution via `RateResolutionService`. Cost rates enable cost-of-time calculations.
- **Budgets (Phase 8)**: `ProjectBudget` with hours and/or currency budgets. Budget consumption tracked via time entries. Threshold alerts at 50%, 75%, 90%, 100%.
- **Profitability reports (Phase 8)**: Project P&L, customer profitability, org-level utilization, margin analysis. `ProfitabilityReportService` with aggregation queries.
- **Dashboards (Phase 9)**: Company dashboard with health scores, project overview tabs, personal dashboard. Shared chart component library (hybrid SVG + Recharts).
- **Workflow automations (Phase 37)**: Rule-based automation engine with triggers, conditions, and actions. Supports delayed actions. Extensible trigger/action type system.
- **OrgSettings (Phase 8)**: Org-scoped configuration entity with extensible columns.
- **Member entity**: Synced from auth provider (Clerk/Keycloak) via webhooks. Contains name, email, role, avatar. Tenant-scoped.
- **Recurring tasks (Phase 30)**: Tasks with recurrence patterns. `RecurringTaskScheduler` creates task instances.
- **Expenses (Phase 30)**: `Expense` entity linked to projects, billable/non-billable, markup support.

**Gap**: The platform tracks what work *was done* (time entries) but has no concept of what work *is planned*. A partner asked "can we take this new client?" has no system answer — they mentally tally who's busy. There's no way to see team capacity, allocate members to future work, or forecast utilization. This is the #1 operational gap for firms with 5+ team members, and the feature that separates basic time trackers (Harvest, Toggl) from practice management platforms (Productive.io, Scoro, Float).

## Objective

Build a **resource planning and capacity management** system that allows firm managers to:

1. **Configure team capacity** — set weekly available hours per member (default 40h, adjustable for part-time staff). Support effective dates for capacity changes.
2. **Allocate members to projects** — plan how many hours each member should spend on each project per week. Forward-looking commitments, independent of time entries.
3. **View capacity at a glance** — an allocation grid (members × weeks) showing planned hours, remaining capacity, and over-allocation warnings.
4. **Forecast utilization** — combine planned allocations (future) with actual time entries (past) to show utilization trends and projections. Answer "are we over-committed next month?"
5. **Staff projects effectively** — from the project perspective, see who's allocated, total hours planned, and gaps between planned and budgeted hours.
6. **Mark leave/unavailability** — simple date range blocks that reduce a member's available capacity. No approval workflow — just a visibility marker.
7. **Surface insights on dashboards** — team utilization widget on company dashboard, personal allocation on personal dashboard, project staffing on project overview.

## Constraints & Assumptions

- **Weekly granularity** for allocations. A `ResourceAllocation` is "Member X is allocated Y hours to Project Z for the week starting Monday YYYY-MM-DD." No daily or hourly breakdowns within a week.
- **Hours per week** as the base unit. The UI can derive and display percentages (e.g., "75% of capacity") but the stored value is always hours.
- **Planned vs. actual comparison** — allocations are forward-looking plans; time entries are backward-looking actuals. The system compares them but they're independent. An allocation doesn't constrain time logging.
- **No approval workflow** — admins/owners allocate directly. No "request X hours of Bob" flow. Members can view their own allocations but not modify them.
- **No skill-based matching** — no competency/skill model on members. Allocation decisions are made by humans who know their team. Skill matching is a future phase.
- **Leave is simple date ranges** — no leave types (annual, sick, etc.), no accrual, no approval. Just "Member X is unavailable from date A to date B" with an optional note. Reduces available capacity for affected weeks proportionally.
- **ISO weeks** (Monday start) for week boundaries. Consistent across the system.
- **Allocation doesn't require project membership** — allocating a member to a project auto-adds them as a ProjectMember (CONTRIBUTOR) if not already assigned. Reduces friction.
- **Historical allocations are preserved** — past allocations are not deleted, enabling actual-vs-planned analysis. No auto-cleanup.
- **Automation integration** — register a new trigger type `MEMBER_OVER_ALLOCATED` with Phase 37's automation engine. Fires when a member's total allocations for a week exceed their capacity.

---

## Section 1 — Member Capacity Data Model

### MemberCapacity

Configurable weekly capacity per member. Supports effective dates for changes (e.g., member goes part-time starting March 1).

```
MemberCapacity:
  id                  UUID (PK)
  memberId            UUID (FK to Member, not null)
  weeklyHours         BigDecimal (not null) — e.g., 40.0, 32.0, 20.0
  effectiveFrom       LocalDate (not null) — when this capacity takes effect (must be a Monday)
  effectiveTo         LocalDate (nullable) — when this capacity ends (null = current/indefinite)
  note                String (nullable, max 500) — e.g., "Reduced to 4 days/week"
  createdBy           UUID (not null)
  createdAt           Timestamp
  updatedAt           Timestamp
```

**Resolution logic**: For a given member and week, find the `MemberCapacity` record where `effectiveFrom <= weekStart` and (`effectiveTo IS NULL` or `effectiveTo >= weekStart`). If no record exists, default to org-wide default (stored in `OrgSettings.defaultWeeklyCapacityHours`, default 40.0).

**OrgSettings extension**:
```
OrgSettings extension:
  defaultWeeklyCapacityHours   BigDecimal (default 40.0) — org-wide default
```

---

## Section 2 — Resource Allocation Data Model

### ResourceAllocation

The core planning entity — planned hours per member per project per week.

```
ResourceAllocation:
  id                  UUID (PK)
  memberId            UUID (FK to Member, not null)
  projectId           UUID (FK to Project, not null)
  weekStart           LocalDate (not null) — Monday of the week (ISO week)
  allocatedHours      BigDecimal (not null) — planned hours for this week
  note                String (nullable, max 500) — e.g., "Tax return preparation"
  createdBy           UUID (not null)
  createdAt           Timestamp
  updatedAt           Timestamp

  UNIQUE(memberId, projectId, weekStart) — one allocation per member per project per week
```

**Validation**:
- `allocatedHours` must be > 0 and <= 168 (sanity check)
- `weekStart` must be a Monday
- Project must not be ARCHIVED or COMPLETED
- Over-allocation is allowed (warning, not error) — firms may intentionally over-commit knowing some projects will slip

### Allocation Aggregation Queries

Key queries the service must support efficiently:

```
-- Member's total allocation for a week (across all projects)
SELECT SUM(allocated_hours) FROM resource_allocations
WHERE member_id = ? AND week_start = ?

-- Project's total allocation for a week (across all members)
SELECT SUM(allocated_hours) FROM resource_allocations
WHERE project_id = ? AND week_start = ?

-- Member's allocations for a date range (for timeline view)
SELECT * FROM resource_allocations
WHERE member_id = ? AND week_start BETWEEN ? AND ?

-- All allocations for a date range (for the full grid view)
SELECT * FROM resource_allocations
WHERE week_start BETWEEN ? AND ?
ORDER BY member_id, week_start
```

Indexes: `(member_id, week_start)`, `(project_id, week_start)`, `(week_start)`.

---

## Section 3 — Leave / Unavailability

### LeaveBlock

Simple date range marker for member unavailability.

```
LeaveBlock:
  id                  UUID (PK)
  memberId            UUID (FK to Member, not null)
  startDate           LocalDate (not null)
  endDate             LocalDate (not null) — inclusive
  note                String (nullable, max 500) — e.g., "Annual leave", "Conference"
  createdBy           UUID (not null)
  createdAt           Timestamp
  updatedAt           Timestamp
```

**Capacity reduction logic**: For a given member and week, calculate the number of leave days that fall within that week (Mon-Fri only, exclude weekends). Reduce available capacity proportionally:

```
effectiveCapacity = weeklyHours × (5 - leaveDaysInWeek) / 5
```

Example: Member has 40h/week capacity, 2 leave days in a week → effective capacity = 40 × 3/5 = 24h.

**Validation**:
- `endDate >= startDate`
- No overlap validation — overlapping leave blocks are merged logically during capacity calculation
- Past leave blocks are preserved for historical accuracy

---

## Section 4 — Capacity & Utilization Service

### CapacityService

Core service that calculates capacity, allocations, and utilization for members across time periods.

```
Key methods:

getMemberCapacity(memberId, weekStart) → BigDecimal
  — Resolves effective capacity for a member for a given week (MemberCapacity record or org default)

getMemberEffectiveCapacity(memberId, weekStart) → BigDecimal
  — Capacity minus leave reduction for that week

getMemberAllocations(memberId, weekStart, weekEnd) → List<AllocationSummary>
  — All allocations for a member in a date range, grouped by week
  — Each AllocationSummary: { weekStart, allocations: [{projectId, projectName, hours}], totalAllocated, capacity, effectiveCapacity, remainingCapacity }

getProjectAllocations(projectId, weekStart, weekEnd) → List<ProjectAllocationSummary>
  — All allocations for a project in a date range, grouped by week
  — Each: { weekStart, allocations: [{memberId, memberName, hours}], totalAllocated }

getTeamCapacityGrid(weekStart, weekEnd) → TeamCapacityGrid
  — The full grid: all members × all weeks in range
  — Per cell: allocated hours, effective capacity, utilization %, over-allocation flag
  — Per member row: total allocated, total capacity, overall utilization %
  — Per week column: team total allocated, team total capacity

getMemberUtilization(memberId, weekStart, weekEnd) → UtilizationSummary
  — Combines allocations (planned) with actual time entries (logged)
  — Per week: { planned hours, actual hours, capacity, planned utilization %, actual utilization %, billable actual hours, billable utilization % }

getTeamUtilization(weekStart, weekEnd) → List<MemberUtilizationSummary>
  — All members' utilization for a date range
  — Sortable by utilization %, name, over-allocation
```

### Over-Allocation Detection

When allocations are saved, `CapacityService` checks if the member's total allocations for that week exceed effective capacity. If so:
1. Return a warning in the API response (not an error — allocation is saved)
2. If Phase 37 automation engine has a `MEMBER_OVER_ALLOCATED` trigger registered, fire the domain event

```
MemberOverAllocatedEvent:
  memberId            UUID
  weekStart           LocalDate
  totalAllocated      BigDecimal
  effectiveCapacity   BigDecimal
  overageHours        BigDecimal
```

---

## Section 5 — Backend API

### Member Capacity API

```
GET    /api/members/{memberId}/capacity              — Get current and historical capacity records
POST   /api/members/{memberId}/capacity              — Set new capacity (effectiveFrom date)
PUT    /api/members/{memberId}/capacity/{id}          — Update capacity record
DELETE /api/members/{memberId}/capacity/{id}          — Delete capacity record
```

### Resource Allocation API

```
GET    /api/resource-allocations                      — List allocations (filters: memberId, projectId, weekStart, weekEnd)
POST   /api/resource-allocations                      — Create allocation (memberId, projectId, weekStart, allocatedHours)
PUT    /api/resource-allocations/{id}                 — Update allocation (hours, note)
DELETE /api/resource-allocations/{id}                 — Delete allocation
POST   /api/resource-allocations/bulk                 — Bulk create/update allocations (for drag-to-fill or copy-week)
```

**Bulk endpoint** accepts an array of `{ memberId, projectId, weekStart, allocatedHours }`. Upserts based on the unique constraint. Essential for the grid UI where users allocate multiple cells at once.

### Leave API

```
GET    /api/members/{memberId}/leave                  — List leave blocks for a member
POST   /api/members/{memberId}/leave                  — Create leave block
PUT    /api/members/{memberId}/leave/{id}             — Update leave block
DELETE /api/members/{memberId}/leave/{id}              — Delete leave block
GET    /api/leave                                     — List all leave blocks (filter: dateRange) — for team calendar view
```

### Capacity & Utilization API

```
GET    /api/capacity/team                             — Team capacity grid (query: weekStart, weekEnd, default 4 weeks)
GET    /api/capacity/members/{memberId}               — Member capacity detail (query: weekStart, weekEnd)
GET    /api/capacity/projects/{projectId}             — Project staffing view (query: weekStart, weekEnd)
GET    /api/utilization/team                          — Team utilization (query: weekStart, weekEnd) — planned + actual
GET    /api/utilization/members/{memberId}            — Member utilization detail
```

### Access Control

- Capacity config (set member hours): `org:admin` and `org:owner`
- Allocation CRUD: `org:admin` and `org:owner`
- Leave CRUD: `org:admin`, `org:owner`, or the member themselves (own leave only)
- Capacity/utilization read: `org:admin`, `org:owner`, and `org:member` (members see their own + team aggregate)

---

## Section 6 — Frontend: Allocation Grid (Signature UI)

### Resource Planning Page (Top-Level Nav: "Resources")

New top-level navigation item, accessible to all members (admins see full grid, members see their own row).

**Grid Layout:**
- **Rows**: Team members (avatar + name), sorted by name or utilization
- **Columns**: Weeks (Mon date headers), default 4-week view, scrollable/pageable
- **Cells**: Stacked colored bars showing project allocations (each project = distinct color from a palette)
  - Cell shows: total allocated hours / effective capacity (e.g., "32/40")
  - Color coding: green (< 80%), amber (80-100%), red (> 100% — over-allocated)
  - Click cell → popover showing allocation breakdown by project with edit capability
- **Row summary**: Total allocated for visible range, average utilization %
- **Column summary (footer)**: Team total allocated vs. team total capacity per week

**Grid Interactions:**
- Click empty cell → "Add Allocation" popover (select project, enter hours)
- Click existing allocation → edit hours or remove
- "Copy Week" action → copies a member's allocations from one week to another (or to a range)
- Week range selector: 4-week / 8-week / 12-week view
- Date navigation: previous/next period, "This Week" jump button
- Filter by: member (search), project, show only over-allocated

**Leave indicators:**
- Leave days shown as hatched/striped overlay on the cell
- Effective capacity adjusts visually (e.g., "24/24" instead of "24/40" if 2 leave days)
- "Add Leave" action on member row dropdown

### Member Detail View

Accessible by clicking a member name in the grid, or from the Team page.

- **Capacity config**: Current weekly hours, history of changes, "Update Capacity" button
- **Allocation timeline**: Horizontal timeline of project allocations over 12 weeks
- **Utilization chart**: Line chart — planned utilization vs. actual utilization (from time entries) over time
- **Leave calendar**: Simple calendar showing leave blocks
- **This week summary**: allocated hours by project, time logged so far, remaining capacity

### Project Staffing Tab (Project Detail Page)

New tab on project detail: "Staffing" (visible to project members).

- **Allocated members**: List showing each member allocated to this project, their weekly hours, and the date range of allocation
- **Weekly breakdown**: Small grid (members × weeks) for this project only
- **Planned vs. budget**: If project has a budget, show total planned hours vs. budget hours. Warning if planned exceeds budget.
- **Add Allocation**: Button to allocate a new member to this project (opens allocation dialog)
- **Actual vs. planned**: For past weeks, compare allocated hours to actual time logged on this project

---

## Section 7 — Frontend: Utilization Dashboard

### Team Utilization Page (Sub-Nav under Resources, or Tab)

- **Utilization table**: All members with their utilization metrics
  - Columns: member name, role, weekly capacity, allocated hours (this week), actual hours (this week), planned utilization %, actual utilization %, billable utilization %
  - Sortable by any column
  - Date range selector (this week, last week, this month, custom range)
- **Utilization chart**: Bar chart — each member's planned vs. actual utilization side-by-side
- **Team average**: Aggregate utilization metrics at the top
- **Trend chart**: Line chart showing team average utilization over the past 8-12 weeks

### Dashboard Widgets

**Company Dashboard** (new widget: "Team Capacity"):
- Team utilization % (this week) — donut chart
- Over-allocated members count (click → filtered grid)
- Under-utilized members (< 50% allocated) count
- "View Resources" link

**Personal Dashboard** (new widget: "My Schedule"):
- My allocations this week (list of projects + hours)
- My capacity remaining
- Leave coming up (next leave block if any)

---

## Section 8 — Notifications, Audit & Automation Integration

### Notification Types (New)

| Event | Recipient | Channel |
|-------|-----------|---------|
| Allocation created/changed for a member | The allocated member | In-app |
| Member over-allocated (> 100% capacity) | Org admins + the member | In-app |
| Leave block created for a member | The member (if created by admin) | In-app |

### Audit Events

- MEMBER_CAPACITY_UPDATED (details: memberId, oldHours, newHours, effectiveFrom)
- ALLOCATION_CREATED, ALLOCATION_UPDATED, ALLOCATION_DELETED (details: memberId, projectId, weekStart, hours)
- LEAVE_CREATED, LEAVE_UPDATED, LEAVE_DELETED (details: memberId, startDate, endDate)

### Automation Integration (Phase 37 Extension)

Register a new trigger type with the automation engine:

| TriggerType | Domain Event | Config Fields |
|-------------|-------------|---------------|
| `MEMBER_OVER_ALLOCATED` | `MemberOverAllocatedEvent` | `thresholdPercent` (Integer, default 100) — fire when allocation exceeds this % of capacity |

This allows firms to create automations like: "When any team member is allocated over 100%, notify the resource manager."

The trigger type is added to the `TriggerType` enum and registered with the `AutomationEventListener`. No changes to the automation engine itself — just a new event type flowing through the existing infrastructure.

---

## Section 9 — Profitability Integration

### Projected Profitability

With allocations providing forward-looking planned hours, the profitability system (Phase 8) can be extended to show **projected** figures:

- **Projected revenue**: allocated hours × billing rate (per member per project)
- **Projected cost**: allocated hours × cost rate
- **Projected margin**: projected revenue - projected cost

These projections surface on:
- **Project financials tab**: "Projected" column alongside "Actual" for revenue, cost, margin
- **Profitability reports**: Optional "Include projections" toggle that adds planned-hours-based estimates for future periods

Implementation: `ProfitabilityReportService` gains a `includeProjections` parameter. When true, it queries `ResourceAllocation` for future weeks and applies the same rate resolution logic used for time entries.

---

## Out of Scope

- **Skill-based matching** — "find me someone with audit experience." Needs a skills/competency entity on members. Future phase.
- **Sub-weekly granularity** — daily or hourly allocation within a week. Weekly is sufficient for firm-level planning.
- **Leave approval workflows** — leave types, accrual balances, manager approval. This is HR software scope. V1 is just visibility markers.
- **Scenario planning** — "what if we move Bob from Project A to B?" with side-by-side comparison. V2 feature.
- **Resource requests** — "Project X needs 20h of senior audit capacity" as a formal request that managers fulfill. Too process-heavy for v1.
- **Timesheet integration** — auto-creating time entries from allocations or vice versa. Allocations and time entries remain independent.
- **External calendar sync** — syncing leave blocks with Google Calendar / Outlook. Needs integration ports (Phase 21).
- **Recurring allocations** — "allocate 20h/week for the next 8 weeks" as a single action. Can be approximated with bulk endpoint + copy-week UI.

## ADR Topics

1. **Weekly vs. daily allocation granularity**: Why weekly is the right default for practice management. Trade-offs: simplicity and reduced data volume vs. precision. Weekly matches how firms plan ("Bob is on the audit this week") and avoids the overhead of daily allocation management. Daily allocation is staff augmentation territory, not professional services.
2. **Planned vs. actual separation**: Why allocations don't constrain time logging. Decision: allocations are planning signals, not enforcement. Members may log time on projects they're not allocated to (ad-hoc work). Over-allocation is a warning, not a block. This matches real-world professional services where plans shift daily.
3. **Capacity model design**: Single `weeklyHours` with effective dates vs. complex shift patterns vs. role-based defaults. Decision: simple effective-dated capacity with org-wide default. Covers full-time, part-time, and capacity changes. Leave blocks handle temporary reductions.
4. **Over-allocation policy**: Error vs. warning vs. silent. Decision: warning (API response flag + optional automation trigger). Firms intentionally over-allocate knowing some work will slip. Blocking over-allocation would make the tool hostile to real workflow.

## Style & Boundaries

- Follow existing entity patterns: Spring Boot entity + JpaRepository + Service + Controller
- Capacity calculations centralized in `CapacityService` — single source of truth for "how many hours does this person have?"
- Allocation grid is the signature UI investment — should feel smooth and responsive. Consider optimistic updates for cell edits.
- Utilization queries combine `ResourceAllocation` (planned) with `TimeEntry` (actual) via joins in the service layer, not at the DB level (different query patterns)
- Leave capacity reduction is calculated, not stored — `CapacityService.getMemberEffectiveCapacity()` computes it on the fly from `LeaveBlock` records
- Profitability projections are read-only extensions to existing report services, not new entities
- Automation trigger registration follows the sealed-class pattern established in Phase 37
- Migration: adds member_capacities, resource_allocations, leave_blocks tables. Extends org_settings with default_weekly_capacity_hours. Single tenant migration file.
- Indexes on (member_id, week_start) and (project_id, week_start) for allocation queries — these power the grid view and must be fast
- Test coverage: integration tests for capacity resolution (effective dates, leave reduction), allocation CRUD with over-allocation detection, utilization calculation (planned vs. actual), bulk allocation, projected profitability
