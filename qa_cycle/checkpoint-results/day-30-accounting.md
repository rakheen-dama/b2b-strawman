# Day 30 — Automation trigger check: Year-End Pack budget (accounting cycle)

**Date**: 2026-05-15
**Actor**: Bob Ndlovu (time logging), Thandi Thornton (notification verification)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`, checkpoint 30.1

---

## Checkpoint 30.1 — Automation trigger check: year-end pack budget ~80%

### Setup

Year-End Pack engagement (ID: `388d5104-7789-4ad6-bb6c-6d045e9663f3`) had:
- Budget: 40h / R60,000 / 80% alert threshold (configured Day 14)
- Hours logged before Day 30: 5.0h (13% used)
- Need to reach 80% (32h) to trigger the automation

### Time entries logged (as Bob)

| Task | Hours | Description | Rate |
|------|-------|-------------|------|
| Trial balance review & adjusting journals | 8.0h | TB review -- detailed journal analysis and adjustments | R 850/hr |
| Draft annual financial statements | 8.0h | AFS drafting -- notes to financial statements and accounting policies | R 850/hr |
| Tax computation & ITR14 preparation | 8.0h | Tax computation and ITR14 preparation -- provisional tax reconciliation | R 850/hr |
| CIPC annual return filing | 4.0h | CIPC annual return preparation and filing | R 850/hr |
| **Total new** | **28.0h** | | |
| **Previous** | **5.0h** | | |
| **Grand total** | **33.0h** | **83% of 40h budget** | |

### Results

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 30.1a | Budget tab shows >80% consumed | **PASS** | Budget: 33h/40h = 83% used, 7h remaining. Status: "At Risk". Amount: R29,350/R60,000 = 49%. Alert threshold: 80%. Screenshot: `qa_cycle/evidence/day-30/year-end-pack-budget-83pct.png` |
| 30.1b | "Engagement Budget Alert (80%)" automation fires | **PASS** (trigger detected, notification action FAILED) | Automation execution log: `BudgetThresholdEvent` triggered 32 seconds after 4th time entry. Trigger data: `consumed_pct: 82.5`, `project_name: "Kgosi Holdings -- FY2025/26 Year-End Pack"`. Action "Send Notification" FAILED: `"No recipients resolved for type: PROJECT_OWNER"`. Root cause: engagement has 0 assigned members with PROJECT_OWNER role. Screenshot: `qa_cycle/evidence/day-30/automation-execution-budget-alert.png` |
| 30.1c | "Budget Alert Escalation" automation fires | **FAIL** (deserialization error) | Backend log: `BudgetThresholdTriggerConfig["thresholdPercent"]` null mapping error. `MismatchedInputException: Cannot map null into type int`. The rule's trigger config has a null `thresholdPercent` field that fails Jackson deserialization. |
| 30.1d | Thandi receives budget alert notification | **PASS** | Thandi's notification page shows: "Project 'Kgosi Holdings -- FY2025/26 Year-End Pack' has reached 82.50% of its hours budget" (4 minutes ago, Unread). This came from the BudgetCheckService direct notification path, not from the automation rule. Screenshot: `qa_cycle/evidence/day-30/thandi-budget-alert-notification.png` |
| 30.1e | Dashboard shows engagement "At Risk" | **PASS** | Dashboard Budget Health: 3 on track, 1 at risk, 0 over budget. Year-End Pack row shows amber dot with `aria-label="At Risk"`. Hours: 33.0h. Monthly total: 49.5h. |

### Summary

| Metric | Value |
|--------|-------|
| Checkpoints | 3 PASS / 1 PARTIAL / 1 FAIL |
| Budget automation triggered | YES (BudgetThresholdEvent fired) |
| Notification delivered to Thandi | YES (via BudgetCheckService direct path) |
| Automation notification action succeeded | NO (no PROJECT_OWNER members on engagement) |
| Budget Alert Escalation rule | FAIL (Jackson null thresholdPercent deserialization) |

### New gaps

| Gap ID | Summary | Severity | Owner | Day | Notes |
|--------|---------|----------|-------|-----|-------|
| OBS-4007 | "Engagement Budget Alert (80%)" automation SEND_NOTIFICATION fails: "No recipients resolved for type: PROJECT_OWNER" | LOW | Dev | 30 | The engagement has 0 assigned members. The automation rule targets PROJECT_OWNER recipients but nobody has that role on the engagement. The BudgetCheckService sends a direct notification to the org owner (Thandi) which succeeds, so the user still gets the alert. The automation action itself should either fall back to org owner or the engagement should auto-assign the creator as a member. |
| OBS-4008 | "Budget Alert Escalation" rule fails with Jackson deserialization error: null thresholdPercent | LOW | Dev | 30 | `BudgetThresholdTriggerConfig["thresholdPercent"]` is null in the rule's trigger config JSON. Jackson cannot map null to primitive `int`. Fix: either make the field `Integer` (nullable) with a default, or ensure the seeder populates a threshold value. |

---

## Day 30 — PARTIAL

Budget threshold detection works. Thandi received the budget notification via the direct BudgetCheckService path. Both automation rules (Budget Alert 80% and Budget Alert Escalation) had execution issues -- one due to no PROJECT_OWNER recipients, the other due to a null config value. Neither gap is a blocker since the core budget notification was delivered.
