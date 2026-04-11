# Deep Automation QA Results -- T2/T3/T4 (2026-03-18)

## Summary

Tested the previously untested automation tracks using lifecycle seed data.
All 3 remaining T2 triggers fire correctly. T3.2 (SendEmail) reveals a bug.
T4 (email content) is blocked by T3.2.

## Test Results

### T2.2 -- INVOICE_STATUS_CHANGED

| Field | Value |
|-------|-------|
| **Result** | PASS |
| **Rule** | QA Test: Invoice Paid Notification (custom, created during test) |
| **Trigger** | InvoicePaidEvent |
| **Action** | Send Notification to Trigger Actor |
| **Steps** | 1. Created automation rule: trigger=INVOICE_STATUS_CHANGED, toStatus=Paid, action=SendNotification. 2. Navigated to INV-0001 (SENT, Naledi Hair Studio). 3. Clicked "Record Payment", reference=QA-TEST-PAY-001. 4. INV-0001 transitioned to PAID. 5. Waited 5s. Notification count went from 2 to 3. |
| **Execution** | Completed in 8ms. Conditions met. |
| **Evidence** | Screenshot: `t2.2-invoice-status-changed-execution.png` |

### T2.5 -- PROPOSAL_SENT

| Field | Value |
|-------|-------|
| **Result** | PASS |
| **Rule** | Proposal Follow-up (5 days) (seeded from accounting-za template pack) |
| **Trigger** | ProposalSentEvent |
| **Action** | Send Notification (delayed 5 days) |
| **Steps** | 1. Created PROP-0002 via API (customer: Vukani Tech Solutions, retainer R5,000). 2. Added content (Tiptap doc). 3. Sent proposal via `POST /api/proposals/{id}/send` with portalContactId. 4. Waited 5s. |
| **Execution** | Completed in 4ms. Conditions met. |
| **Evidence** | Screenshot: `t2.5-proposal-sent-execution.png` |
| **UI Bug** | Customer selector Popover inside Dialog does not open on click (Radix Popover + Dialog modal conflict). Had to use API fallback. See BUG-UI-01. |

### T2.6 -- BUDGET_THRESHOLD_REACHED

| Field | Value |
|-------|-------|
| **Result** | PARTIAL (trigger PASS, action FAIL) |
| **Rule** | Engagement Budget Alert (80%) (seeded from accounting-za template pack) |
| **Trigger** | BudgetThresholdEvent |
| **Action** | Send Notification to Project Owner |
| **Steps** | 1. Monthly Bookkeeping -- Kgosi budget: 10h, 7.5h consumed (75%), threshold 80%. 2. Logged 1h time entry via API (task: Reconcile accounts). 3. Budget crossed to 85% (hours) / 95.91% (amount). 4. Waited 5s. |
| **Execution** | Failed in 17ms. Conditions met. Trigger event data: `consumed_pct: 95.91, project_name: Monthly Bookkeeping -- Kgosi, dimension: amount`. |
| **Failure** | `SEND_NOTIFICATION: No recipients resolved for type: PROJECT_OWNER` -- no project owner assigned in seed data. |
| **Evidence** | Screenshot: `t2.6-budget-threshold-execution.png` |
| **Assessment** | Trigger fires correctly. Action failure is a seed data gap (no project owner), not a code bug. |

### T3.2 -- SendEmail Action

| Field | Value |
|-------|-------|
| **Result** | FAIL |
| **Rule** | QA Test: Task Complete Email (custom, created during test) |
| **Trigger** | TaskCompletedEvent |
| **Action** | Send Email to Trigger Actor |
| **Steps** | 1. Created automation rule via API: trigger=TASK_STATUS_CHANGED, toStatus=COMPLETED, action=SendEmail. 2. Cleared Mailpit. 3. Completed task "Client liaison and follow-up" via `PATCH /api/tasks/{id}/complete`. 4. Waited 5s. |
| **Execution** | Marked "Completed" in 12ms. Result data: `emailSentTo: alice@e2e-test.local`. |
| **Mailpit** | 0 emails received. |
| **Root Cause** | Backend log: `No email template mapping for notification type 'AUTOMATION_EMAIL'` then `Skipping email for notification ... -- unmapped type 'AUTOMATION_EMAIL'`. The SendEmailActionExecutor creates a notification of type AUTOMATION_EMAIL, but EmailNotificationChannel has no template registered for that type, so the actual SMTP send is skipped. The action incorrectly reports success. |
| **Bug** | BUG-AUTO-01: SendEmail automation action silently fails -- no email template mapped for AUTOMATION_EMAIL. |

### T4.1-T4.8 -- Email Content Verification

| Field | Value |
|-------|-------|
| **Result** | BLOCKED |
| **Reason** | Blocked by T3.2 failure. No automation emails reach Mailpit. Information request emails were sent during seed but Mailpit was cleared before QA began. |

## Bugs Found

### BUG-AUTO-01: SendEmail automation action silently fails (HIGH)

- **Severity**: HIGH
- **Component**: `SendEmailActionExecutor` / `EmailNotificationChannel`
- **Symptom**: SendEmail automation action reports "Completed" with `emailSentTo` in result data, but no email is actually sent via SMTP.
- **Root Cause**: The executor creates a notification of type `AUTOMATION_EMAIL`. The `EmailNotificationChannel` has no template mapping for this type, logs a WARN, and skips the email. The executor does not check whether the email was actually delivered.
- **Fix**: Register an email template for `AUTOMATION_EMAIL` in the template mapping (or use a generic automation email template). Also fix the executor to detect delivery failure and mark the action as FAILED.
- **Files**: `SendEmailActionExecutor.java`, `EmailNotificationChannel.java`

### BUG-UI-01: Proposal dialog customer selector unresponsive (MEDIUM)

- **Severity**: MEDIUM
- **Component**: `CreateProposalDialog` (frontend)
- **Symptom**: The customer selector (Radix Popover with Command/cmdk) inside the New Proposal dialog (Radix Dialog) does not open when clicked.
- **Root Cause**: Known Radix UI issue -- Popover nested inside a modal Dialog. The Dialog's focus trap and pointer-event interception prevents the Popover trigger from receiving click events.
- **Workaround**: Use API directly to create proposals.
- **Fix**: Add `modal={false}` to the Popover component, or use a Select/Combobox that works inside Dialog modals.
- **File**: `frontend/components/proposals/create-proposal-dialog.tsx` line 185-204

## Execution Log Summary (11 total executions)

| # | Rule | Trigger | Status | Time |
|---|------|---------|--------|------|
| 1 | QA Test: Task Complete Email | TaskCompletedEvent | Completed | 12ms |
| 2 | Task Completion Chain | TaskCompletedEvent | Completed | 46ms |
| 3 | Engagement Budget Alert (80%) | BudgetThresholdEvent | Failed | 17ms |
| 4 | Proposal Follow-up (5 days) | ProposalSentEvent | Completed | 4ms |
| 5 | QA Test: Invoice Paid Notification | InvoicePaidEvent | Completed | 8ms |
| 6-11 | FICA Reminder (7 days) x5, Proposal Follow-up (seed) | Various | Completed | 1-6ms |
