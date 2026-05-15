# Day 87 Checkpoint Results — Automation Wow Moment

**Date**: 2026-05-15
**Actor**: Thandi Thornton (Owner)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`

---

## Checkpoint 87.1 — Force automation trigger (engagement budget past 80%)

**Status**: PASS

**Steps executed**:
1. Logged in as Thandi (Owner), navigated to Moroka Family Trust AFS engagement (ID: `0a39ccb1-070d-4078-9240-4a4fab254017`)
2. Navigated to Budget tab — confirmed "No budget configured"
3. Clicked "Configure budget" — dialog opened with fields for Hours, Amount, Currency, Alert Threshold
4. Set budget: **8 hours / R 10,000 / ZAR / 80% alert threshold**
5. Saved — budget immediately showed **81% hours used** (6h 30m existing / 8h budget), status "At Risk"
6. Navigated to Tasks tab, clicked "Log Time" on "Draft annual financial statements" task
7. Logged **1.0h** billable time: "AFS draft review -- beneficiary schedules cross-check" (R 1,500/hr)
8. Confirmed on Budget tab: **7h 30m / 8h = 94% hours used**, **R 8,650 / R 10,000 = 87% amount used**, both "At Risk"

**Evidence**: Budget automation triggered by time entry pushing consumption past 80% threshold. Notification count in header badge incremented from 14 to 15 immediately after time entry was logged.

---

## Checkpoint 87.2 — Verify notification in Thandi's notification bell

**Status**: PASS

**Steps executed**:
1. Clicked notification bell (showing 15 unread)
2. Top notification: **"Budget alert: Moroka Family Trust -- FY2025/26 Annual Trust AFS at 80%"** — 53 seconds ago, Unread
3. Also visible: earlier budget alert from Day 30 — "Project 'Kgosi Holdings -- FY2025/26 Year-End Pack' has reached 82.50% of its hours budget" — 4 hours ago
4. Navigated to full Notifications page (`/org/thornton-associates/notifications`) — confirmed the same budget alert at top of list with "2 minutes ago" timestamp

**Evidence**: Budget alert notification delivered to Thandi's notification bell via BudgetCheckService (direct notification to org owner). Automation rule for engagement budget alert (80%) fired successfully.

---

## Checkpoint 87.3 — Screenshot: Automation wow moment

**Status**: PASS

**Screenshots captured**:
1. `qa_cycle/evidence/day-87/automation-budget-alert-notification.png` (141 KB) — Notification dropdown showing "Budget alert: Moroka Family Trust -- FY2025/26 Annual Trust AFS at 80%" over the Budget tab (94% hours, 87% amount, At Risk status)
2. `qa_cycle/evidence/day-87/notifications-page-budget-alerts.png` — Full notifications page showing both budget alerts (Moroka Trust at 80% + Kgosi Year-End Pack at 82.50%)

---

## Summary

| Checkpoint | Description | Status |
|------------|-------------|--------|
| 87.1 | Force automation trigger (budget past 80%) | PASS |
| 87.2 | Verify notification in Thandi's bell | PASS |
| 87.3 | Screenshot: automation wow moment | PASS |

**Total**: 3 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED
**New gaps**: None

**Budget details (Moroka Trust AFS)**:
- Hours: 7h 30m / 8h (94%) — At Risk
- Amount: R 8,650 / R 10,000 (87%) — At Risk
- Alert threshold: 80%
- Time entry logged: 1.0h by Thandi on "Draft annual financial statements" task

**Total hours this cycle**: 61.0h (previous 60.0h + 1.0h Day 87)
