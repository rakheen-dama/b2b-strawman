# Test Plan 07: Projects, Tasks & Time

## Overview

This plan covers project lifecycle management, task workflows, time entry tracking, expenses, the My Work cross-project view, calendar views, and tagging/custom fields. Scenarios use the outline format: title, objective, preconditions, key steps, and key validations.

## Test Actors

| Actor | Role | Responsibilities |
|-------|------|------------------|
| Sofia Reyes | Member | Primary project/task/time tester |
| Aiden O'Brien | Member | My Work, expenses, comments |
| David Molefe | Member | Cross-project scenarios |
| James Chen | Admin | Project lifecycle transitions, admin operations |
| Fatima Al-Hassan | Member | Custom fields, tags, saved views |

---

## 1. Project CRUD & Lifecycle

### PROJ-001: Create a project with all fields

**Objective:** Verify a project can be created with complete data.
**Preconditions:** Sofia is a member of the org; customer "Acme Corp" exists (ACTIVE status).
**Key Steps:**
1. POST /api/projects with name, description, customerId, dueDate
2. Verify the response includes generated ID and status = ACTIVE
3. GET the project by ID and confirm all fields are persisted

**Key Validations:**
- Project status defaults to ACTIVE
- Customer association is recorded
- Project appears in the project list

### PROJ-002: Update project details

**Objective:** Verify project fields can be modified.
**Preconditions:** Project "Website Redesign" exists.
**Key Steps:**
1. PUT /api/projects/{id} with updated name and description
2. GET the project and verify changes

**Key Validations:**
- Name and description are updated
- Status, customerId, and other fields are unchanged
- Audit event is created for the update

### PROJ-003: Complete a project

**Objective:** Verify ACTIVE -> COMPLETED transition.
**Preconditions:** Project "Website Redesign" is ACTIVE with tasks in DONE or CANCELLED status.
**Key Steps:**
1. James (Admin) transitions the project to COMPLETED
2. Verify status change and timestamp
3. Attempt to create a new task on the completed project

**Key Validations:**
- Status is COMPLETED
- New task creation on a completed project is blocked or warns
- Project still appears in project list with COMPLETED badge

### PROJ-004: Archive a completed project

**Objective:** Verify COMPLETED -> ARCHIVED transition.
**Preconditions:** Project is in COMPLETED status.
**Key Steps:**
1. James archives the project
2. Verify the project no longer appears in default (active) project lists
3. Confirm the project is accessible via an "include archived" filter

**Key Validations:**
- Status is ARCHIVED
- Default list excludes archived projects
- Filtered list includes archived projects

### PROJ-005: Reopen an archived project

**Objective:** Verify an archived project can be reopened.
**Preconditions:** Project is in ARCHIVED status.
**Key Steps:**
1. James reopens the project (transition back to ACTIVE)
2. Verify the project reappears in the active project list
3. Confirm tasks and time entries are still associated

**Key Validations:**
- Status returns to ACTIVE
- All historical data (tasks, time entries) is preserved
- Project members retain their roles

### PROJ-006: Cannot skip lifecycle states

**Objective:** Verify invalid transitions are blocked.
**Preconditions:** Project is ACTIVE.
**Key Steps:**
1. Attempt to transition directly from ACTIVE to ARCHIVED
2. Verify the transition is rejected

**Key Validations:**
- 400 error with message about invalid state transition
- Project remains ACTIVE

### PROJ-007: Delete a project (Owner only)

**Objective:** Verify only Owners can delete projects.
**Preconditions:** Project exists; Sofia (Member) and James (Admin) are available.
**Key Steps:**
1. Sofia attempts DELETE /api/projects/{id} -- expect 403
2. James (Admin) attempts DELETE -- expect 403
3. Owner deletes the project -- expect 200/204

**Key Validations:**
- Only Owner role can delete
- Deleted project is no longer accessible
- Associated tasks and time entries are handled (cascade or blocked)

### PROJ-008: Create project from template

**Objective:** Verify a project template pre-populates structure.
**Preconditions:** A project template exists with predefined tasks and settings.
**Key Steps:**
1. Create a new project using the template ID
2. Verify the project is created with template-defined tasks
3. Confirm task structure matches template

**Key Validations:**
- Project is created with ACTIVE status
- Template tasks are cloned into the new project
- Template metadata does not link back (independent copy)

### PROJ-009: Project with due date validation

**Objective:** Verify due date constraints.
**Preconditions:** None.
**Key Steps:**
1. Create a project with dueDate in the past
2. Create a project with dueDate in the future

**Key Validations:**
- Past due dates may be accepted (with warning) or rejected per business rules
- Future due dates are accepted without issue

### PROJ-010: Unbilled summary on project

**Objective:** Verify unbilled time/expense rollup is accurate.
**Preconditions:** Project has time entries (some UNBILLED, some BILLED) and expenses.
**Key Steps:**
1. GET the project's unbilled summary endpoint
2. Compare totals against individual time entries and expenses

**Key Validations:**
- Unbilled hours match sum of UNBILLED time entries
- Unbilled amount matches calculated total using billing rates
- BILLED and NON_BILLABLE entries are excluded

---

## 2. Project Members

### PROJ-011: Add a member to a project

**Objective:** Verify members can be added to projects.
**Preconditions:** Project "Website Redesign" exists; David is an org member but not on this project.
**Key Steps:**
1. Add David to the project with a specific role
2. Verify David appears in the project member list
3. Confirm David can now access the project

**Key Validations:**
- Member list includes David with correct role
- David's project list includes "Website Redesign"

### PROJ-012: Remove a member from a project

**Objective:** Verify members can be removed.
**Preconditions:** David is a member of the project.
**Key Steps:**
1. Remove David from the project
2. Verify David no longer appears in the member list
3. Confirm David's existing time entries on the project are preserved

**Key Validations:**
- David loses access to the project
- Historical data (time entries, comments) remains intact

### PROJ-013: Update member role on a project

**Objective:** Verify role changes take effect.
**Preconditions:** Sofia has role "contributor" on the project.
**Key Steps:**
1. Update Sofia's project role to "lead"
2. Verify the role change in the member list

**Key Validations:**
- New role is reflected immediately
- Permissions associated with new role are enforced

### PROJ-014: Member access scope enforcement

**Objective:** Verify non-members cannot access project resources.
**Preconditions:** David is NOT a member of project "Internal Audit."
**Key Steps:**
1. David attempts GET /api/projects/{internalAuditId}
2. David attempts to create a task on the project
3. David attempts to log time on the project

**Key Validations:**
- All requests return 403 or 404
- No data from the project is leaked

### PROJ-015: Cannot remove the last project member

**Objective:** Verify the system prevents orphaned projects.
**Preconditions:** Project has exactly one member.
**Key Steps:**
1. Attempt to remove the last member

**Key Validations:**
- Request is rejected with an appropriate error
- The member remains on the project

### PROJ-016: Org Admin can access all projects

**Objective:** Verify Admin role bypasses project membership.
**Preconditions:** James (Admin) is not explicitly a member of project "Confidential Audit."
**Key Steps:**
1. James accesses the project
2. James views tasks and time entries

**Key Validations:**
- Admin can view and manage all projects regardless of membership
- Admin actions are recorded in audit log

---

## 3. Task CRUD & Lifecycle

### TASK-001: Create a task

**Objective:** Verify task creation with all fields.
**Preconditions:** Project "Website Redesign" exists; Sofia is a project member.
**Key Steps:**
1. POST a task with title, description, priority (HIGH), dueDate, assignee (Sofia)
2. Verify the task is created with status OPEN
3. Confirm the task appears in the project's task list

**Key Validations:**
- Status defaults to OPEN
- Priority is HIGH
- Assignee is set to Sofia

### TASK-002: Update a task

**Objective:** Verify task fields can be modified.
**Preconditions:** Task "Design Homepage" exists.
**Key Steps:**
1. Update title, description, and priority
2. Verify changes are persisted

**Key Validations:**
- All modified fields are updated
- Status and assignee remain unchanged

### TASK-003: Transition OPEN to IN_PROGRESS

**Objective:** Verify task can be started.
**Preconditions:** Task is in OPEN status.
**Key Steps:**
1. Transition task to IN_PROGRESS
2. Verify status change

**Key Validations:**
- Status is IN_PROGRESS
- Transition timestamp is recorded

### TASK-004: Transition IN_PROGRESS to DONE

**Objective:** Verify task completion.
**Preconditions:** Task is IN_PROGRESS.
**Key Steps:**
1. Transition task to DONE
2. Verify the task is marked complete

**Key Validations:**
- Status is DONE
- Completion timestamp is recorded
- Task still appears in task list (not deleted)

### TASK-005: Cancel a task from OPEN

**Objective:** Verify OPEN -> CANCELLED transition.
**Preconditions:** Task is OPEN.
**Key Steps:**
1. Transition task to CANCELLED
2. Verify status

**Key Validations:**
- Status is CANCELLED
- Associated time entries are preserved
- Task is excluded from active task counts

### TASK-006: Reopen a completed task

**Objective:** Verify any status -> OPEN transition (reopen).
**Preconditions:** Task is DONE.
**Key Steps:**
1. Reopen the task (transition to OPEN)
2. Verify status is OPEN again

**Key Validations:**
- Status returns to OPEN
- Previous completion data is preserved in history
- Task re-enters active task counts

### TASK-007: Claim a task (self-assign)

**Objective:** Verify a member can claim an unassigned task.
**Preconditions:** Task exists with no assignee; Sofia is a project member.
**Key Steps:**
1. Sofia claims the task via the claim endpoint
2. Verify Sofia is now the assignee

**Key Validations:**
- Assignee is set to Sofia
- No other fields are changed

### TASK-008: Release a claimed task

**Objective:** Verify a member can release a task they claimed.
**Preconditions:** Task is assigned to Sofia.
**Key Steps:**
1. Sofia releases the task
2. Verify the assignee is cleared

**Key Validations:**
- Assignee is null/empty
- Task status is unchanged

### TASK-009: Manage task items (checklist)

**Objective:** Verify task items can be created, toggled, and reordered.
**Preconditions:** Task "Design Homepage" exists.
**Key Steps:**
1. Add 3 task items: "Wireframe," "Mockup," "Review"
2. Toggle "Wireframe" as complete
3. Reorder items: move "Review" to position 1
4. Verify final state

**Key Validations:**
- 3 items exist on the task
- "Wireframe" is marked complete
- Order reflects the reorder operation
- Task completion percentage updates accordingly

### TASK-010: Task with recurrence

**Objective:** Verify recurring task configuration.
**Preconditions:** Project exists.
**Key Steps:**
1. Create a task with recurrence settings (e.g., weekly)
2. Complete the task
3. Verify a new instance is created per recurrence rules

**Key Validations:**
- Recurring task generates a new OPEN task upon completion
- New task inherits title, description, priority, assignee
- Due date advances per recurrence schedule

### TASK-011: Invalid task transition is rejected

**Objective:** Verify invalid state transitions are blocked.
**Preconditions:** Task is in CANCELLED status.
**Key Steps:**
1. Attempt to transition from CANCELLED to IN_PROGRESS

**Key Validations:**
- 400 error indicating invalid transition
- Task remains CANCELLED
- Only CANCELLED -> OPEN (reopen) is valid

### TASK-012: Delete a task

**Objective:** Verify task deletion behavior.
**Preconditions:** Task "Unused Draft" exists with no time entries.
**Key Steps:**
1. Delete the task
2. Verify it is removed from the task list
3. Attempt GET on the deleted task ID

**Key Validations:**
- 404 returned for deleted task
- Project task count decreases

---

## 4. Time Entry Management

### TIME-001: Log time on a task

**Objective:** Verify a time entry can be created.
**Preconditions:** Task "Design Homepage" is IN_PROGRESS; Sofia is the assignee.
**Key Steps:**
1. POST a time entry with taskId, date (today), durationMinutes (120), billable (true), description
2. Verify the time entry is created
3. Confirm billingRateSnapshot and costRateSnapshot are captured

**Key Validations:**
- Time entry is created with billingStatus = UNBILLED (since billable = true)
- Rate snapshots reflect current org/project/member rates
- Duration is 120 minutes

### TIME-002: Edit a time entry

**Objective:** Verify time entry fields can be modified.
**Preconditions:** Time entry exists for Sofia.
**Key Steps:**
1. Update durationMinutes from 120 to 90
2. Update description
3. Verify changes

**Key Validations:**
- Duration and description are updated
- Rate snapshots remain from original creation
- Audit trail records the modification

### TIME-003: Delete a time entry

**Objective:** Verify time entry can be removed.
**Preconditions:** Time entry exists with billingStatus = UNBILLED.
**Key Steps:**
1. DELETE the time entry
2. Verify it is removed from the task's time entry list
3. Confirm project time summary is recalculated

**Key Validations:**
- Time entry no longer exists
- Project total hours decrease accordingly

### TIME-004: Toggle billable status

**Objective:** Verify billable flag can be toggled.
**Preconditions:** Time entry exists with billable = true, billingStatus = UNBILLED.
**Key Steps:**
1. Toggle billable to false via the toggle endpoint
2. Verify billingStatus changes to NON_BILLABLE
3. Toggle back to true
4. Verify billingStatus returns to UNBILLED

**Key Validations:**
- Billable flag and billing status are synchronized
- Cannot toggle a BILLED entry (already invoiced)

### TIME-005: Cannot toggle a BILLED time entry

**Objective:** Verify invoiced entries are locked.
**Preconditions:** Time entry has billingStatus = BILLED.
**Key Steps:**
1. Attempt to toggle billable on the BILLED entry

**Key Validations:**
- 400 error indicating entry is already billed
- Entry remains unchanged

### TIME-006: Time summary by project

**Objective:** Verify project-level time aggregation.
**Preconditions:** Project has multiple time entries across different tasks and members.
**Key Steps:**
1. GET the project time summary endpoint
2. Compare totals against individual entries

**Key Validations:**
- Total hours match sum of all entry durations
- Billable vs. non-billable breakdown is accurate
- Per-member breakdown is correct

### TIME-007: Time summary by task

**Objective:** Verify task-level time aggregation.
**Preconditions:** Task has 3 time entries from different members.
**Key Steps:**
1. GET the task time summary
2. Verify totals

**Key Validations:**
- Total matches sum of entries on this task only
- Entries from other tasks are excluded

### TIME-008: Log time without a task

**Objective:** Verify time can be logged at project level without a task association.
**Preconditions:** Project exists; Sofia is a member.
**Key Steps:**
1. POST a time entry with projectId but no taskId
2. Verify creation

**Key Validations:**
- Entry is created with null taskId
- Entry appears in project time summary but not in any task's summary

### TIME-009: Rate snapshot accuracy

**Objective:** Verify rate snapshots reflect the rate hierarchy at logging time.
**Preconditions:** Org rate = 100/hr, project override = 120/hr for Sofia's role.
**Key Steps:**
1. Sofia logs 60 minutes on the project
2. Verify billingRateSnapshot = 120 (project override wins)
3. Change the project rate to 150/hr
4. Sofia logs another 60 minutes
5. Verify the new entry has billingRateSnapshot = 150

**Key Validations:**
- First entry snapshot = 120
- Second entry snapshot = 150
- First entry is unchanged after rate update

### TIME-010: Cannot log time on a project you are not a member of

**Objective:** Verify access control on time logging.
**Preconditions:** David is not a member of project "Internal Audit."
**Key Steps:**
1. David attempts to POST a time entry for the project

**Key Validations:**
- 403 or 404 is returned
- No time entry is created

---

## 5. My Work View

### MW-001: Cross-project task list

**Objective:** Verify My Work shows tasks across all projects for the current user.
**Preconditions:** Sofia has tasks assigned in 3 different projects.
**Key Steps:**
1. GET /api/my-work/tasks
2. Verify tasks from all 3 projects are included
3. Verify tasks assigned to other members are excluded

**Key Validations:**
- Only Sofia's assigned tasks appear
- Tasks are from multiple projects
- DONE/CANCELLED tasks may be excluded or filterable

### MW-002: Cross-project time entries

**Objective:** Verify My Work shows time entries across projects.
**Preconditions:** Sofia has logged time in 2 projects this week.
**Key Steps:**
1. GET /api/my-work/time-entries with date range filter
2. Verify entries from both projects appear

**Key Validations:**
- Only Sofia's entries are returned
- Date range filter is respected
- Entries include project name for context

### MW-003: My Work time summary

**Objective:** Verify aggregated time summary in My Work.
**Preconditions:** Sofia has logged time across multiple projects.
**Key Steps:**
1. GET /api/my-work/time-summary
2. Compare against individual project summaries

**Key Validations:**
- Total hours = sum across all projects
- Billable/non-billable breakdown is correct
- Per-project breakdown is available

### MW-004: My Work respects project membership

**Objective:** Verify My Work only includes data from accessible projects.
**Preconditions:** Sofia is removed from one of her projects.
**Key Steps:**
1. Remove Sofia from "Project C"
2. GET /api/my-work/tasks
3. Verify tasks from "Project C" no longer appear

**Key Validations:**
- Tasks from removed project are excluded
- Historical time entries may still be visible (read-only)

### MW-005: My Work with no assignments

**Objective:** Verify My Work handles empty state gracefully.
**Preconditions:** David has no assigned tasks and no time entries.
**Key Steps:**
1. GET /api/my-work/tasks
2. GET /api/my-work/time-entries

**Key Validations:**
- Empty arrays are returned (not errors)
- Summary shows 0 hours

---

## 6. Expenses

### EXP-001: Create an expense

**Objective:** Verify expense creation with all fields.
**Preconditions:** Project "Website Redesign" exists; Aiden is a member.
**Key Steps:**
1. POST an expense with projectId, date, category ("Travel"), description, amount (250.00), currency (ZAR)
2. Verify the expense is created with billingStatus = UNBILLED

**Key Validations:**
- Expense is created with correct fields
- Default billingStatus is UNBILLED
- Expense appears in project expense list

### EXP-002: Edit an expense

**Objective:** Verify expense fields can be modified.
**Preconditions:** Expense exists for Aiden.
**Key Steps:**
1. Update amount from 250.00 to 275.00 and category to "Meals"
2. Verify changes

**Key Validations:**
- Amount and category are updated
- BillingStatus is unchanged

### EXP-003: Delete an expense

**Objective:** Verify expense deletion.
**Preconditions:** UNBILLED expense exists.
**Key Steps:**
1. DELETE the expense
2. Verify removal from expense list

**Key Validations:**
- Expense no longer exists
- Project expense totals are recalculated

### EXP-004: Upload a receipt

**Objective:** Verify receipt file upload to S3.
**Preconditions:** Expense exists without a receipt.
**Key Steps:**
1. Upload a receipt image (JPEG) to the expense
2. Verify receiptS3Key is populated
3. Download the receipt and verify it matches

**Key Validations:**
- Receipt is stored in S3
- S3 key is recorded on the expense
- File is downloadable

### EXP-005: Write off an expense (Admin/Owner)

**Objective:** Verify Admin can write off an expense.
**Preconditions:** UNBILLED expense exists; James (Admin) is authenticated.
**Key Steps:**
1. James writes off the expense
2. Verify billingStatus changes to NON_BILLABLE

**Key Validations:**
- BillingStatus = NON_BILLABLE
- Only Admin/Owner can perform write-off
- Aiden (Member) attempting write-off gets 403

### EXP-006: Restore a written-off expense

**Objective:** Verify Admin can restore a written-off expense.
**Preconditions:** Expense has billingStatus = NON_BILLABLE.
**Key Steps:**
1. James restores the expense
2. Verify billingStatus returns to UNBILLED

**Key Validations:**
- BillingStatus = UNBILLED
- Expense re-enters unbilled totals

### EXP-007: Cannot write off a BILLED expense

**Objective:** Verify invoiced expenses cannot be written off.
**Preconditions:** Expense has billingStatus = BILLED.
**Key Steps:**
1. James attempts to write off the expense

**Key Validations:**
- 400 error indicating expense is already billed
- Expense remains BILLED

### EXP-008: My expenses view

**Objective:** Verify member can see their own expenses across projects.
**Preconditions:** Aiden has expenses in 2 projects.
**Key Steps:**
1. GET Aiden's expenses (filtered by memberId or "my expenses" endpoint)
2. Verify expenses from both projects appear

**Key Validations:**
- Only Aiden's expenses are returned
- Expenses from other members are excluded
- Total amount is correct across projects

---

## 7. Calendar View

### CAL-001: Cross-project deadlines

**Objective:** Verify calendar shows project due dates across all member projects.
**Preconditions:** David is a member of 3 projects with different due dates.
**Key Steps:**
1. GET the calendar view for the current month
2. Verify all 3 project due dates appear
3. Filter to a single project and verify only that project's date shows

**Key Validations:**
- All project due dates within the date range are displayed
- Projects where David is not a member are excluded

### CAL-002: Task due dates on calendar

**Objective:** Verify task-level due dates appear on the calendar.
**Preconditions:** Sofia has tasks with due dates spread across the week.
**Key Steps:**
1. GET the calendar view for the current week
2. Verify task due dates appear on correct days

**Key Validations:**
- Each task due date maps to the correct calendar day
- DONE and CANCELLED tasks are excluded (or visually distinct)
- Task title and project name are shown

### CAL-003: Calendar date range filtering

**Objective:** Verify calendar respects date range parameters.
**Preconditions:** Tasks and projects span multiple months.
**Key Steps:**
1. Request calendar for March 2026
2. Request calendar for Q1 2026
3. Verify each response only includes items within the range

**Key Validations:**
- Items outside the date range are excluded
- Boundary dates (first/last day of range) are included

### CAL-004: Calendar with no upcoming deadlines

**Objective:** Verify empty calendar state.
**Preconditions:** No tasks or projects have due dates in the queried range.
**Key Steps:**
1. GET calendar for a future month with no deadlines

**Key Validations:**
- Empty response (not an error)
- UI can render an empty calendar state

---

## 8. Tags & Custom Fields on Projects/Tasks

### TAG-001: Apply tags to a project

**Objective:** Verify tags can be added to projects.
**Preconditions:** Project "Website Redesign" exists.
**Key Steps:**
1. Fatima adds tags "urgent" and "client-facing" to the project
2. Verify tags are persisted on the project
3. Search/filter projects by tag "urgent"

**Key Validations:**
- Both tags appear on the project
- Tag-based filtering returns the correct projects
- Tags are case-insensitive or normalized

### TAG-002: Apply tags to a task

**Objective:** Verify tags work on tasks.
**Preconditions:** Task "Design Homepage" exists.
**Key Steps:**
1. Add tags "design" and "priority" to the task
2. Filter tasks by tag "design"

**Key Validations:**
- Tags are persisted on the task
- Filter returns only tasks with the matching tag

### TAG-003: Custom fields on projects via field groups

**Objective:** Verify custom fields can be applied through field groups.
**Preconditions:** A field group "Legal Project Fields" exists with fields: matter_number (text), jurisdiction (select).
**Key Steps:**
1. Apply the field group to project "Legal Review"
2. Set matter_number = "MAT-2026-001" and jurisdiction = "Western Cape"
3. GET the project and verify custom field values

**Key Validations:**
- Field group is associated with the project
- Custom field values are stored and retrievable
- Fields from unapplied groups are not shown

### TAG-004: Custom fields on tasks

**Objective:** Verify tasks support custom fields.
**Preconditions:** Task exists; custom fields are configured.
**Key Steps:**
1. Set custom field values on the task
2. Verify persistence
3. Update a custom field value

**Key Validations:**
- Custom field values are stored in the task's customFields JSONB
- Updates overwrite the specific field without affecting others

### TAG-005: Filter and sort by custom fields

**Objective:** Verify custom fields can be used for filtering.
**Preconditions:** Multiple projects have the "matter_number" custom field populated.
**Key Steps:**
1. Filter projects where matter_number starts with "MAT-2026"
2. Verify only matching projects are returned

**Key Validations:**
- Filter correctly queries JSONB custom fields
- Non-matching projects are excluded
- Performance is acceptable (JSONB index if applicable)
