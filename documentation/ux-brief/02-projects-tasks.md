# Projects & Tasks

## Projects

### What a Project Is
A project is the primary work container. It groups tasks, time entries, documents, and comments. Projects can be linked to one or more customers.

### Project Fields
| Field | Type | Notes |
|-------|------|-------|
| Name | Text | Required |
| Description | Text | Optional |
| Status | Enum | ACTIVE, COMPLETED, ARCHIVED |
| Customer | Link | Optional, M:N relationship |
| Due Date | Date | Optional |
| Custom Fields | Dynamic | Based on applied field groups |
| Tags | Multi-select | From org tag library |
| Created By | Member | Auto-set |

### Project Lifecycle

```
ACTIVE ──→ COMPLETED ──→ ARCHIVED
  ↑            │
  └────────────┘ (reopen)
```

- **ACTIVE**: Default state. All operations allowed.
- **COMPLETED**: Marks delivery done. Time entries still allowed (for billing wrap-up). Confirmation dialog warns about unbilled time.
- **ARCHIVED**: Read-only. No mutations allowed. Can be reopened.

### Project Detail Page (Tabbed)

**Overview Tab**
- Health indicators (from backend scoring)
- Key metrics: task completion %, total hours, budget status, billable value
- Timeline (start date, due date, days remaining)
- Setup status cards (shows what's configured vs missing)

**Team Tab**
- List of project members with roles
- Add member dialog (select from org members)
- Remove member, transfer lead actions

**Time Tab**
- Time summary panel: total hours, billable hours, billable value
- Per-member time breakdown
- Time entry list (recent entries on this project's tasks)

**Financials Tab**
- Budget configuration (hours and/or currency)
- Budget vs actual progress bar
- Rate cards applied to this project
- Project P&L (revenue vs cost)

**Documents Tab**
- Documents scoped to this project
- Upload zone (drag-and-drop)
- Visibility toggle (internal/shared with portal)
- Generated documents list (PDFs from templates)
- "Generate Document" dropdown (pick template → generation dialog)

**Comments Tab**
- Threaded comment section
- Add comment form
- Edit/delete own comments

**Activity Tab**
- Chronological activity feed (audit events formatted for humans)
- Filter by entity type (tasks, documents, comments, members)

### Project List Page
- Table/card list of all projects
- Filter by status (Active, Completed, Archived, All)
- Search by name
- Tag filter
- Saved views (user-created filter presets)
- "New Project" button → Create dialog

### Create Project Dialog
- Name (required)
- Description
- Customer (optional, select from customer list)
- Due date (optional)
- Template (optional — create from project template)

---

## Tasks

### What a Task Is
A task is a unit of work within a project. Tasks can be assigned to members, have time logged against them, and carry subtasks (items).

### Task Fields
| Field | Type | Notes |
|-------|------|-------|
| Title | Text | Required |
| Description | Text | Optional, supports rich text |
| Project | Link | Required (tasks always belong to a project) |
| Status | Enum | OPEN, IN_PROGRESS, DONE, CANCELLED |
| Priority | Enum | LOW, MEDIUM, HIGH, URGENT |
| Assignee | Member | Optional |
| Due Date | Date | Optional |
| Type | Text | Optional free-form categorization |
| Custom Fields | Dynamic | Based on applied field groups |
| Tags | Multi-select | From org tag library |

### Task Lifecycle

```
       ┌──────────────┐
       ↓              │
    OPEN ──→ IN_PROGRESS ──→ DONE
      │          │              ↓
      │          │         (reopen → OPEN)
      ↓          ↓
   CANCELLED ←───┘
      │
      └──→ (reopen → OPEN)
```

- **OPEN**: Default. Unassigned or assigned but not started.
- **IN_PROGRESS**: Someone is actively working on it. Set via "claim" action (auto-assigns to claimant).
- **DONE**: Terminal. Task completed. Can be reopened.
- **CANCELLED**: Terminal. Task abandoned. Can be reopened.

### Key Task Actions
| Action | Transition | Side Effects |
|--------|-----------|-------------|
| Claim | OPEN → IN_PROGRESS | Sets assignee to current user |
| Release | IN_PROGRESS → OPEN | Clears assignee |
| Complete | IN_PROGRESS → DONE | Sets completedAt, completedBy |
| Cancel | OPEN/IN_PROGRESS → CANCELLED | Sets cancelledAt, cancelledBy |
| Reopen | DONE/CANCELLED → OPEN | Clears all lifecycle timestamps |

### Task Detail Sheet
Tasks open in a side sheet (slide-over panel), not a full page. Contains:
- Title (editable inline)
- Description (editable)
- Status with transition buttons
- Assignee selector (dropdown of project members)
- Priority selector
- Due date picker
- Subtask list (items) with checkboxes
- Time entries logged against this task
- "Log Time" button
- Comments section
- Tags
- Custom fields

### Task List (within Project Detail)
- List of all tasks in the project
- Filter by status, assignee, priority
- Saved views
- "New Task" button
- Click row → opens task detail sheet

### Subtasks (Task Items)
- Simple checklist items within a task
- Fields: title, sort order, completed (boolean)
- Toggle complete with checkbox
- Add/remove/reorder

### Task Assignment Flow
1. Task created (no assignee) → appears in "Available Tasks" in My Work
2. Member clicks "Claim" → task moves to IN_PROGRESS, assignee set
3. Or: Admin/Owner sets assignee via dropdown → appears in member's "Assigned Tasks"
4. Member can "Release" → returns to OPEN, clears assignee

---

## My Work Page

The personal command center for individual team members.

### Sections
1. **Assigned Tasks** — tasks where current user is assignee, grouped by project
2. **Available Tasks** — unassigned OPEN tasks in user's projects (claimable)
3. **Urgency View** — high-priority and overdue tasks across all projects
4. **Upcoming Deadlines** — tasks with due dates in the next 7 days
5. **Time Logged Today** — today's time entries with running total
6. **Weekly Time Summary** — hours per day chart for current week
7. **Personal KPIs** — utilization rate, billable hours this period, task completion rate
