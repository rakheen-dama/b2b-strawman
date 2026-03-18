# Track 1 -- Automation Rule CRUD

**Date**: 2026-03-18
**Actor**: Alice (Owner)
**URL**: http://localhost:3001/org/e2e-test-org/settings/automations

---

## T1.1 -- View Seeded Automation Rules

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.1.1 Navigate to Settings > Automations | PASS | Page loaded at /org/e2e-test-org/settings/automations. No console errors. |
| T1.1.2 Verify accounting-za automation templates are listed (~4 rules expected) | PARTIAL | **11 rules** listed (more than the expected ~4). All from seeded vertical pack. |
| T1.1.3 Note seeded rules and trigger types | PASS | See table below. |
| T1.1.4 Each rule shows: name, trigger type, enabled/disabled status | PASS | Table columns: Name (with description), Trigger, Enabled (toggle switch), Last Updated, Actions (count), Delete button. |

### Seeded Rules Inventory

| # | Rule Name | Trigger Type | Actions | Enabled |
|---|-----------|-------------|---------|---------|
| 1 | Request Complete Follow-up | Request Completed | 1 | **DISABLED** |
| 2 | Proposal Follow-up (5 days) | PROPOSAL_SENT | 1 | **DISABLED** |
| 3 | Document Review Notification | Document Accepted | 1 | **DISABLED** |
| 4 | New Project Welcome | Project Status | 1 | **DISABLED** |
| 5 | Budget Alert Escalation | Budget Threshold | 1 | **DISABLED** |
| 6 | Overdue Invoice Reminder | Invoice Status | 2 | **DISABLED** |
| 7 | Task Completion Chain | Task Status | 1 | **DISABLED** |
| 8 | SARS Deadline Reminder | FIELD_DATE_APPROACHING | 1 | **DISABLED** |
| 9 | Invoice Overdue (30 days) | Invoice Status | 2 | **DISABLED** |
| 10 | Engagement Budget Alert (80%) | Budget Threshold | 1 | **DISABLED** |
| 11 | FICA Reminder (7 days) | Customer Status | 1 | **DISABLED** |

### BUG-AUTO-02: All seeded rules are DISABLED by default

All 11 seeded automation rules have their toggle switches in the OFF (unchecked) position. The dashboard Automations widget correctly reflects "Active Rules: 0". The test plan expected seeded rules to be ENABLED by default (T7.1.3).

**Impact**: Seeded rules will never fire unless manually enabled. For a vertical pack that represents best-practice automations, the default should be enabled.

**Severity**: Medium -- rules exist but require manual activation.

---

## T1.2 -- Create a Custom Automation Rule

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.2.1 Click "New Automation" | PASS | Navigated to /org/e2e-test-org/settings/automations/new. Form loaded with fields: Name, Description, Trigger Type, Conditions, Actions. |
| T1.2.2 Create rule with specified config | PARTIAL | Filled Name, Description, Trigger (Invoice Status Changed), To Status (Paid), Action (Send Notification), Recipient (All Admins -- no "Alice" option, uses role-based), Title, Message with variables. **Note**: No individual-user recipient option; only Trigger Actor, Project Owner, All Project Members, All Admins. |
| T1.2.3 Save -- verify rule appears in list as ENABLED | PASS | Rule appeared at top of list. Toggle shows ENABLED (checked). Last Updated: "now". |
| T1.2.4 Note rule ID | PASS | ID: `588911d7-4ff8-44f5-b48f-03ec382bd1b2` |

### BUG-AUTO-01: Actions not persisted on rule creation (CRITICAL)

When creating a new automation rule via "New Automation" > fill form > "Create Rule":
- The rule name, description, trigger type, and trigger config ARE saved.
- The **actions are NOT saved**. Opening the rule detail shows "No actions configured."
- The Actions column in the list shows "0" for the new rule (vs 1 or 2 for seeded rules).
- Reproduced by: reload page after create -- actions gone.
- Re-adding the action via the edit form and clicking "Save Changes" also does NOT persist the action (same result on reload).

**Verified seeded rules DO have persisted actions**: "Task Completion Chain" shows a fully configured "Create Task" action with template variables (Task Name: "Follow-up: {{task.name}}", Description, Assign To: Project Owner). So the backend can store and return actions -- the bug is in the frontend create/update API call not including actions in the payload.

**Impact**: CRITICAL BLOCKER for T2 trigger testing with custom rules. Seeded rules with existing actions may still work if enabled.

**Severity**: Critical

---

## T1.3 -- Edit Rule

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.3.1 Open custom rule, edit name to "Payment received -- notify Alice" | PASS | Name field updated. |
| T1.3.2 Save -- verify name updated in list | PASS | Heading changed to "Payment received -- notify Alice". Confirmed in list view. |

---

## T1.4 -- Disable / Enable Rule

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.4.1 Disable the custom rule (toggle) | PASS | Toggle switch clicked. aria-checked changed to "false", data-state to "unchecked". |
| T1.4.2 Verify status shows DISABLED | PASS | Switch visually shows disabled state. |
| T1.4.3 Re-enable the rule | PASS | Toggle switch clicked again. aria-checked changed to "true". |
| T1.4.4 Verify status shows ENABLED | PASS | Switch visually shows enabled state (checked). |

---

## T1.5 -- View Execution History

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T1.5.1 Navigate to execution log | PASS | Two paths available: (1) "View Execution Log" link on automations list page -> /settings/automations/executions, (2) "Execution Log" tab on individual rule detail page. |
| T1.5.2 Verify page loads (may be empty) | PASS | Both pages load. Show "No executions found." |
| T1.5.3 Note table columns | PASS | Global execution log has filters: All Rules, All Statuses, Refresh button. Per-rule execution log has: All Statuses filter, Refresh button. No columns visible yet (empty state). |

---

## UI Observations

1. **Available trigger types (8)**: Task Status Changed, Project Status Changed, Customer Status Changed, Invoice Status Changed, Time Entry Created, Budget Threshold Reached, Document Accepted, Information Request Completed. Notable absence: PROPOSAL_SENT appears in seeded rules but not in the dropdown (displayed as raw enum "PROPOSAL_SENT" vs friendly name).

2. **Available action types (6)**: Create Task, Send Notification, Send Email, Update Status, Create Project, Assign Member.

3. **Recipient options for Send Notification**: Trigger Actor, Project Owner, All Project Members, All Admins. No individual user selection.

4. **Insert Variable buttons** present on Title and Message fields for template variable insertion.

5. **Add Delay toggle** available on actions.

6. **Console error**: React hydration mismatch (#418) on page load -- minor, not related to functionality.

---

## Summary

| Area | Status |
|------|--------|
| Rule listing | PASS |
| Rule creation (metadata) | PASS |
| Rule creation (actions) | **FAIL** -- BUG-AUTO-01 |
| Rule editing (name) | PASS |
| Rule enable/disable toggle | PASS |
| Execution history page | PASS |
| Seeded rules enabled by default | **FAIL** -- BUG-AUTO-02 |

**Blocking**: BUG-AUTO-01 blocks creation of custom rules with actions for T2 trigger testing. However, seeded rules have persisted actions and can be enabled for testing. Recommend proceeding with T7 (seeded rule verification) after enabling seeded rules.
