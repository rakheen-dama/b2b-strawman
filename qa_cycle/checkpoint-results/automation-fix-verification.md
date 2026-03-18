# Fix Verification Results — Automation Bugs (Cycle 2)

**Date**: 2026-03-18
**Agent**: QA Agent
**Branch**: `bugfix_cycle_automation_2026-03-18`

---

## BUG-AUTO-01: Actions not persisted when creating automation rules — VERIFIED

**Fix PR**: #746

### Test Steps
1. Navigated to Settings > Automations > New Automation
2. Created rule: "QA Test Rule - Notify on task completion"
   - Trigger: Task Status Changed, To Status: Done
   - Action: Send Notification, Recipient: Trigger Actor, Title: "Task completed", Message: "Task has been marked as done"
3. Clicked "Create Rule" — redirected to automations list
4. Rule appeared in list with "1" in Actions column (confirms action count persisted)
5. Clicked into rule detail page
6. Action section shows: Send Notification with all fields populated (Recipient: Trigger Actor, Title: "Task completed", Message: "Task has been marked as done")

### Result: **VERIFIED**
Action data survives create-and-reload cycle. The fix correctly added `actions` field to CreateRuleRequest DTO and persists actions atomically with the rule.

---

## BUG-AUTO-02: Seeded rules disabled by default — VERIFIED

**Fix PR**: #747

### Test Steps
1. Navigated to Settings > Automations
2. Inspected all 11 seeded automation rules
3. Every toggle switch shows `[checked]` (ENABLED)

### Seeded Rules (all ENABLED):
1. Request Complete Follow-up (Request Completed)
2. Proposal Follow-up (5 days) (PROPOSAL_SENT)
3. Document Review Notification (Document Accepted)
4. New Project Welcome (Project Status)
5. Budget Alert Escalation (Budget Threshold)
6. Overdue Invoice Reminder (Invoice Status)
7. Task Completion Chain (Task Status)
8. SARS Deadline Reminder (FIELD_DATE_APPROACHING)
9. Invoice Overdue (30 days) (Invoice Status)
10. Engagement Budget Alert (80%) (Budget Threshold)
11. FICA Reminder (7 days) (Customer Status)

### Result: **VERIFIED**
All 11 seeded rules default to ENABLED. No manual toggle needed.

---

## BUG-AUTO-03: TaskCompletedEvent mapping missing — VERIFIED

**Fix PR**: #745

### Test Steps
1. Created task "QA Automation Test Task" in Website Redesign project
2. Updated task status to IN_PROGRESS via API (PUT /api/tasks/{id})
3. Completed task via API (PATCH /api/tasks/{id}/complete)
4. Checked backend logs (within 1 second of completion):
   - `Evaluating 2 rule(s) for trigger type TASK_STATUS_CHANGED from event TaskCompletedEvent`
   - `Created automation execution for rule ... (Task Completion Chain) with status TRIGGERED`
   - `Automation created task c1bfb31d... in project 70574e97...`
5. Checked execution history UI: "Task Completion Chain" shows Completed, trigger=TaskCompletedEvent, 23ms

### Result: **VERIFIED**
TaskCompletedEvent is now correctly mapped to TASK_STATUS_CHANGED trigger type. The Task Completion Chain rule fires and creates a follow-up task.

### Evidence
- Screenshot: `fix-verification-execution-log.png` (execution log showing both Completed executions)

---

## New Bug Discovered During Fix Verification

### BUG-AUTO-04: Task status dropdown in detail panel fails silently (PUT /api/tasks/{id} returns 405)

**Severity**: Medium
**Track**: T2 (blocks UI-based task status changes)
**Cascading**: No (API-based completion works; this is a UI-only issue)

**Root cause**: `handleStatusChange()` in `task-detail-sheet.tsx` calls `updateTask()` which uses `PUT /api/tasks/{id}` with the full task body including the new status. However:
1. The first status change attempt (OPEN -> IN_PROGRESS via TaskStatusChangedEvent) calls PUT, which the backend **does** support, but the trigger config match for the seeded Task Completion Chain rule fails because it expects COMPLETED status specifically.
2. When going OPEN -> DONE directly via the dropdown, the PUT call sends `status: "DONE"` but the backend may reject or the frontend `updateTask` optimistically updates then reverts on failure.

**Backend log evidence**:
```
"Request method 'PUT' is not supported"
```
This was logged when the frontend tried to update the task status via the dropdown.

**Actual flow**: PUT /api/tasks/{id} exists and works (confirmed via curl), but the frontend server action's API call may have auth/routing issues in the E2E stack, or the status transition from OPEN -> DONE is rejected by the backend lifecycle guard (must go OPEN -> IN_PROGRESS -> DONE).

**Workaround**: Task completion works via PATCH /api/tasks/{id}/complete (which is what the "Mark Done" button uses), but that button only appears when status is IN_PROGRESS.

**Fix suggestion**: The TaskStatusSelect should use lifecycle-aware server actions (`completeTask`, `cancelTask`, `reopenTask`) instead of the generic `updateTask` PUT, OR the dropdown should enforce valid transitions (OPEN can only go to IN_PROGRESS, IN_PROGRESS can go to DONE).
