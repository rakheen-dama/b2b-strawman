# Cycle 4 -- Day 7: First Week of Work

**Date**: 2026-04-06
**Actors**: Carol (Member), Bob (Admin), Alice (Owner)
**Build**: Branch `bugfix_cycle_2026-04-06` (includes PRs #970-975)
**Method**: UI (Playwright MCP) + DB for prerequisites (project member assignment, task status)

## Prerequisites (Setup via DB)

- Added all 3 members (Alice, Bob, Carol) to all 6 project_members -- matters had 0 members from Day 2-3 API creation
- Changed task "Initial consultation & case assessment" status to IN_PROGRESS and assigned to Carol via DB (task detail dialog has no status change control for Member role, Assignee combobox disabled)

## Checkpoint Results

### Carol (Candidate Attorney) -- Junior Work

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 7.1 | Login as Carol, navigate to My Work | PASS | My Work page loads. Shows "No tasks assigned to you" initially (tasks unassigned from template creation). Time Today: 0m. |
| 7.2 | Verify assigned action items visible across matters | PARTIAL | After DB assignment, Carol's "Initial consultation" task shows on My Work. 53 available (unassigned) tasks visible. |
| 7.3 | Open Sipho matter > Tasks tab > "Initial consultation & case assessment" | PASS | Task detail dialog opens. Status: Open, Priority: Medium, Assignee: Unassigned (combobox disabled for Member role). Description: "Taking instructions, evaluating merits". |
| 7.4 | Mark status -> In Progress | PARTIAL | No status change UI control in task detail for Member role. Changed via DB. UI reflects IN_PROGRESS after refresh. |
| 7.5 | Log Time: 90 min, billable, description | PASS | Log Time dialog opened from task row. Duration: 1h 30m. Description: "Taking instructions from client re: personal injury claim against RAF". Billable: checked. |
| 7.6 | Verify time recording in Time tab | PASS | Time tab shows: Total 1h 30m, Billable 1h 30m, 1 entry, 1 contributor. By Task: "Initial consultation" 1h 30m. |
| 7.7 | Rate snapshot = R550 (Carol's rate) | PASS | Dialog shows "Billing rate: R 550,00/hr (member default)". DB confirms: billing_rate_snapshot=550.00, cost_rate_snapshot=200.00, currency=ZAR. Value: R 825,00 (550 x 1.5h). |
| 7.8 | QuickCollect vs Mokoena: log 60 min on "Skip tracing" | PASS | Navigated to matter. Log Time dialog: R 550,00/hr. Duration: 1h 0m. Description: "Debtor address verification -- TPN trace". |
| 7.9 | QuickCollect vs Pillay: log 45 min on "Letter of demand" | PASS | Log Time dialog: R 550,00/hr. Duration: 0h 45m. Description: "Drafting Section 129 notice". |

### Bob (Associate) -- Substantive Legal Work

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 7.10 | Login as Bob, open Sipho matter | PASS | Bob sees full sidebar (Court Calendar, Clients, Finance). Sipho matter loads with "Initial consultation" showing In Progress / Carol Member. |
| 7.11 | Log 120 min on "Letter of demand" | PASS | Log Time dialog: R 1 200,00/hr. Duration: 2h 0m. Description: "Drafting demand letter to Road Accident Fund". |
| 7.12 | Rate snapshot = R1,200 (Bob's rate) | PASS | Dialog shows "Billing rate: R 1 200,00/hr (member default)". DB confirms: billing_rate_snapshot=1200.00. |
| 7.13 | Apex matter: log 180 min on "Due diligence review" | PASS | Duration: 3h 0m. Description: "Reviewing shareholder agreements and MOI". Rate: R 1 200,00/hr confirmed. |
| 7.14 | Add comment on Sipho matter task | PASS | Opened task detail > Comments tab. Posted: "RAF claim -- need police report number and J88 medical report from client". Visibility: Internal only. |
| 7.15 | Verify comment with Bob's name and timestamp | PASS | Comment displays: Author "Bob Admin", timestamp "now". Edit/Delete buttons available. |

### Alice (Senior Partner) -- Advisory & Court Calendar

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 7.16 | Login as Alice, open Moroka estates matter | PASS | Matter loads: "Deceased Estate -- Peter Moroka", Client: Moroka Family Trust, 9 tasks, 3 members. |
| 7.17 | Log 60 min on "Report estate to Master" | PASS | Duration: 1h 0m. Description: "Reviewing estate documents, preparing J294 reporting form". |
| 7.18 | Rate snapshot = R2,500 (Alice's rate) | PASS | Dialog shows "Billing rate: R 2 500,00/hr (member default)". DB confirms: billing_rate_snapshot=2500.00. |
| 7.19 | Navigate to Court Calendar | PASS | Court Calendar page loads with filters (Status, Type, date range, search), tabs (List, Calendar, Prescriptions), "New Court Date" button. |
| 7.20 | Add court date: Sipho, Motion, 30 days, Gauteng Div | PARTIAL | **GAP-D7-01**: "Schedule Court Date" dialog matter dropdown is EMPTY (no options). Cannot create court date via UI. Created via DB: Motion, 2026-05-06, Gauteng Division Johannesburg, status SCHEDULED. |
| 7.21 | Verify court date with SCHEDULED status | PASS | Court Dates tab on Sipho matter shows: Date 2026-05-06, Time 10:00, Type Motion, Court "Gauteng Division, Johannesburg", Status "Scheduled". |
| 7.22 | Screenshot: Court calendar | SKIP | Court date visible on matter tab but global Court Calendar creation blocked by GAP-D7-01. |

### Verification

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 7.23 | Sipho Activity tab: time + comment in feed | PASS | Activity tab shows 3 events in chronological order: Carol 1h 30m (9 min ago), Bob 2h (6 min ago), Bob commented (4 min ago). Note: task names show as "a task" instead of actual names (minor display bug). |
| 7.24 | My Work as Carol: action items with updated status | PASS | My Tasks: 1 task ("Initial consultation & case assessment", In Progress, 1h 30m logged). Time Today: 3h 15m. Available Tasks: 53. Time Breakdown chart shows 3 projects (1.5h + 1.0h + 0.8h = 3.3h). |
| 7.25 | Screenshot: My Work page | SKIP | Verified via accessibility snapshot (functional equivalent). |

## Day 7 Checkpoint Summary

| Checkpoint | Result |
|-----------|--------|
| Time recordings by 3 users on 4 matters | PASS (6 Day 7 entries: Carol x3, Bob x2, Alice x1) |
| Rate snapshots match billing rates | PASS (Carol R550, Bob R1200, Alice R2500) |
| Action item transitioned to IN_PROGRESS | PARTIAL (via DB -- no UI control for Member role) |
| Comment visible on matter | PASS (Bob's comment with name + timestamp) |
| Court date with SCHEDULED status | PARTIAL (created via DB due to GAP-D7-01, displays correctly in UI) |
| Activity feed shows chronological events | PASS (3 events in correct order) |
| My Work page reflects correct assignments | PASS (1 assigned task, 53 available, time breakdown correct) |
| Terminology: "Time Recordings" not "Time Entries" | FAIL (Tab label says "Time", task detail says "Time Entries", not "Time Recordings") |

## New Gaps Found

| ID | Summary | Severity | Status |
|----|---------|----------|--------|
| GAP-D7-01 | Court Calendar "Schedule Court Date" dialog: matter dropdown is empty (no options populated). Cannot create court date from either global Court Calendar or matter Court Dates tab. | HIGH | OPEN |
| GAP-D7-02 | "Add Member" dialog crashes with `TypeError: Cannot read properties of null (reading 'charAt')` -- likely caused by member records with null names in DB (stale API-created members). | MEDIUM | OPEN |
| GAP-D7-03 | Task detail dialog: no status change control for Member role (Assignee combobox disabled). Members cannot change task status or self-assign without "Claim" button on task list. | LOW | OPEN |
| GAP-D7-04 | Activity feed shows generic "a task" / "task" instead of actual task names (e.g., "Initial consultation & case assessment"). | LOW | OPEN |
| GAP-D7-05 | Adverse Parties tab has no "Add Adverse Party" button -- read-only display only. | MEDIUM | OPEN |

## Data State After Day 7

- **Time entries**: 6 (Carol: 90+60+45 min, Bob: 120+180 min, Alice: 60 min)
- **Total billable hours**: 9h 15m
- **Comments**: 1 (Bob on Sipho "Initial consultation" task)
- **Court dates**: 1 (Sipho, Motion, 2026-05-06, SCHEDULED)
- **Task status changes**: 1 (Initial consultation: OPEN -> IN_PROGRESS, assigned to Carol)
