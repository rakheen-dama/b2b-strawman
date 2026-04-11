# Track 7 -- Vertical Automation Templates

**Date**: 2026-03-18
**Actor**: Alice (Owner)
**URL**: http://localhost:3001/org/e2e-test-org/settings/automations

---

## T7.1 -- Identify Seeded Rules

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T7.1.1 Navigate to Settings > Automations | PASS | Page loaded. 12 rules total (11 seeded + 1 custom). |
| T7.1.2 List all rules from accounting-za pack | PASS | See Track 1 inventory. 11 seeded rules documented with name, trigger, action count, enabled status. |
| T7.1.3 Verify all seeded rules are ENABLED by default | **FAIL** | All 11 seeded rules were DISABLED by default. Manually enabled all 11 during this track. See BUG-AUTO-02. |

### Post-T7.1 Action: Enabled All Seeded Rules

All 11 seeded rules were manually toggled to ENABLED via the list page switches. Confirmed via `aria-checked="true"` on all 12 switches (11 seeded + 1 custom). Backend recorded `automation_rule.enabled` audit events for each.

---

## T7.2 -- Test Seeded Rule: Task Completion Chain

**Rule**: Task Completion Chain
**Trigger**: Task Status Changed (to completed)
**Action**: Create Task (name: "Follow-up: {{task.name}}", assign to: Project Owner)
**Test**: Completed task "Gather financial data" on project "Annual Tax Return 2026 -- Kgosi"

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T7.2.1 Enable rule | PASS | Toggle switched to checked. |
| T7.2.1 Fire trigger (complete a task) | PASS | Task "Gather financial data" marked as Done. Task count dropped from 3 to 2 in filtered view. |
| T7.2.1 Check execution history (5s wait) | **FAIL** | "No executions found" after 10+ seconds. |
| T7.2.1 Check execution history (10s+ wait, refresh) | **FAIL** | Still "No executions found" after manual refresh. |

### BUG-AUTO-03: AutomationEventListener missing trigger type mappings (CRITICAL BLOCKER)

**Backend log evidence:**
```
Recorded audit event: type=task.completed, entity=task/d5ce474f-edac-427d-a056-dccfd5e20221
No trigger type mapping for event class TaskCompletedEvent, skipping
```

The `AutomationEventListener` receives domain events but has no mapping from `TaskCompletedEvent` to the `TASK_STATUS_CHANGED` trigger type. The event fires correctly (audit recorded) but the automation engine skips it.

**Additional unmapped events found in logs:**
- `DocumentGeneratedEvent` -- no trigger type mapping

**Events that DO have mappings (from earlier log entries):**
- `INVOICE_STATUS_CHANGED` -- mapping exists (showed "No enabled rules" when rules were disabled)
- `PROPOSAL_SENT` -- mapping exists (showed "No enabled rules" when rules were disabled)

**Impact**: The Task Completion Chain seeded rule can never fire. Any automation triggered by TASK_STATUS_CHANGED is broken. Other trigger types (INVOICE_STATUS_CHANGED, PROPOSAL_SENT) may still work since they have mappings.

**Root cause**: The `AutomationEventListener` maintains an event-to-trigger-type mapping table. `TaskCompletedEvent` (and likely `TaskStatusChangedEvent` if separate) is not registered in this mapping.

**Severity**: Critical -- systemic for task-related automations, but not all automations.

---

## T7.2 -- Remaining Seeded Rules (NOT TESTED)

Due to BUG-AUTO-03, the remaining seeded rules were not individually fired in this track. However, based on backend log analysis, the following trigger mappings are confirmed:

| Trigger Type | Mapping Status | Evidence |
|-------------|---------------|----------|
| TASK_STATUS_CHANGED | **MISSING** | `TaskCompletedEvent` has no mapping. BUG-AUTO-03. |
| INVOICE_STATUS_CHANGED | EXISTS | Log showed "No enabled rules" (correct when disabled). |
| PROPOSAL_SENT | EXISTS | Log showed "No enabled rules" (correct when disabled). |
| CUSTOMER_STATUS_CHANGED | UNKNOWN | Not tested yet. |
| BUDGET_THRESHOLD_REACHED | UNKNOWN | Not tested yet. |
| FIELD_DATE_APPROACHING | UNKNOWN | Scheduler-based, different trigger mechanism. |
| REQUEST_COMPLETED | UNKNOWN | Not tested yet. |
| DOCUMENT_ACCEPTED | UNKNOWN | Not tested yet. May be affected like `DocumentGeneratedEvent`. |

---

## T7.3 -- Execution History Accuracy

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T7.3.1 Navigate to execution history after firing rules | PASS | Page loads at /settings/automations/executions. |
| T7.3.2 Verify triggered rules appear in log | **FAIL** | No executions recorded because rules never executed (BUG-AUTO-03). |
| T7.3.3 Columns visible | N/A | Empty state -- "No executions found." |
| T7.3.4 Failed executions show error reason | N/A | No executions to verify. |
| T7.3.5 Execution count matches triggers | N/A | Zero executions, zero successful triggers. |

---

## Summary

| Area | Status |
|------|--------|
| Seeded rules listed | PASS (11 rules) |
| Seeded rules enabled by default | **FAIL** (BUG-AUTO-02) |
| Manual enable toggles | PASS (all 11 enabled manually) |
| Task Completion Chain trigger fires | **FAIL** (BUG-AUTO-03) |
| Execution history records | **FAIL** (no executions due to BUG-AUTO-03) |

**Recommendation**: Proceed to T2 (Trigger Verification) to test triggers that have confirmed mappings (INVOICE_STATUS_CHANGED, PROPOSAL_SENT) and unknown mappings (CUSTOMER_STATUS_CHANGED). The TASK_STATUS_CHANGED blocker is isolated to task events, not all automations.
