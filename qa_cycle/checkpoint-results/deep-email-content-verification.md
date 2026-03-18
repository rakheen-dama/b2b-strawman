# Deep Email Content Verification -- T3.2 / T4 (2026-03-18)

## Summary

BUG-AUTO-01 (SendEmail action silently fails) is **VERIFIED FIXED**. Emails now reach Mailpit.
Two new bugs discovered during email content verification.

## T3.2 -- BUG-AUTO-01 Verification: SendEmail Action

| Field | Value |
|-------|-------|
| **Result** | **VERIFIED** |
| **Rule** | "Email on Task Completion" (custom, created via UI) |
| **Trigger** | TASK_STATUS_CHANGED, toStatus = COMPLETED |
| **Action** | SendEmail, recipient = TRIGGER_ACTOR |
| **Steps** | 1. Authenticated as Alice at mock-login. 2. Cleared Mailpit (DELETE /api/v1/messages). 3. Created automation rule via UI: trigger=TaskStatusChanged, toStatus=Done, action=SendEmail, subject="Task completed: {{task.name}}", body="The task {{task.name}} on project {{project.name}} has been completed." 4. Hit BUG-EMAIL-01 (see below) -- rule stored toStatus="DONE" but backend uses "COMPLETED". Fixed rule in DB. 5. Completed task "Prepare trial balance" on project "Annual Tax Return 2026 -- Kgosi". 6. Waited 5s. Email arrived in Mailpit. |
| **Mailpit** | 1 email received at alice@e2e-test.local, subject "Task completed: Prepare trial balance" |
| **Verdict** | BUG-AUTO-01 is **VERIFIED FIXED**. The `notification-automation.html` template renders correctly, `AUTOMATION_EMAIL` type mapping works, `deliver()` returns true. |

## T4 -- Email Content Verification

### T4.1 -- Task Completion Email

| Check | Result | Detail |
|-------|--------|--------|
| Subject | PASS | "Task completed: Prepare trial balance" -- `{{task.name}}` resolved correctly |
| Recipient | PASS | `alice@e2e-test.local` -- correct Trigger Actor resolution |
| Greeting | PASS | "Hi Alice Owner," -- personalized with member name |
| Body -- task.name | PASS | "The task Prepare trial balance on project..." -- resolved |
| Body -- project.name | **FAIL** | Literal `{{project.name}}` appears in body -- **NOT RESOLVED** (BUG-EMAIL-02) |
| Unresolved variables | **FAIL** | 1 instance of `{{project.name}}` found in body |
| From address | PASS | `noreply@docteams.app` -- professional |
| Branding -- Org name | PASS | "E2E Test Organization" in header, greeting close, and footer |
| Branding -- Brand color | PASS | `#1B5E20` (green) in header background |
| HTML layout | PASS | Professional responsive email template with proper table layout |
| Footer | PASS | "E2E Test Organization" in footer section |
| Links | N/A | No links in this email |

### T4.2 -- Invoice Paid Email

Created additional "Email on Invoice Paid" rule (trigger=INVOICE_STATUS_CHANGED, toStatus=PAID, action=SendEmail).
Marked INV-0001 (Naledi Hair Studio, R 1,638.75) as PAID via UI "Record Payment" button.

| Check | Result | Detail |
|-------|--------|--------|
| Subject | PASS | "Invoice INV-0001 paid" -- `{{invoice.invoiceNumber}}` resolved correctly |
| Recipient | PASS | `alice@e2e-test.local` |
| Greeting | PASS | "Hi Alice Owner," |
| Body -- invoice.invoiceNumber | PASS | "Invoice INV-0001" -- resolved |
| Body -- customer.name | PASS | "customer Naledi Hair Studio" -- resolved |
| Body -- invoice.totalAmount | **FAIL** | Literal `{{invoice.totalAmount}}` appears -- **NOT RESOLVED** (BUG-EMAIL-02) |
| Unresolved variables | **FAIL** | 1 instance of `{{invoice.totalAmount}}` found |
| Branding -- Org name | PASS | "E2E Test Organization" in header, body, footer |
| Branding -- Brand color | PASS | `#1B5E20` (green) header |
| HTML layout | PASS | Same professional template |

### T4.3 -- System Notification Emails

| Check | Result | Detail |
|-------|--------|--------|
| System emails in Mailpit | N/A | No system notification emails appeared in Mailpit. System notifications use the in-app notification channel, not email. Only automation SendEmail actions send to SMTP/Mailpit. This is by design. |

### T4.7 -- Email Branding Consistency

Both emails were compared:

| Check | Result | Detail |
|-------|--------|--------|
| Same base layout | PASS | Both use identical `notification-automation.html` template with wrapper table, header, content, footer |
| Firm name consistent | PASS | "E2E Test Organization" in both headers and footers |
| Brand color in header | PASS | `#1B5E20` (green) in both emails' header `background-color` |
| Consistent footer | PASS | Both have identical footer structure with org name |
| Font family | PASS | Both use `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif` |

## New Bugs Found

### BUG-EMAIL-01: Frontend automation form sends wrong task status values

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Component** | Frontend -- automation new rule form |
| **Description** | The "To Status" dropdown for TASK_STATUS_CHANGED shows "Done" and stores `"DONE"` in `trigger_config.toStatus`, but the backend `TaskCompletedEvent` uses status `"COMPLETED"`. The seeded "Task Completion Chain" rule correctly uses `"COMPLETED"`. |
| **Root cause** | Frontend dropdown displays human-readable labels ("Open", "In Progress", "Done", "Cancelled") but sends these as enum values. The backend uses a different set: `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. The mismatch means UI-created rules with status filters never match. |
| **Impact** | Any TASK_STATUS_CHANGED rule created via UI with a toStatus filter will silently fail to trigger. Only rules created via templates (which use correct backend values) work. |
| **Workaround** | Create rules via API or DB with correct enum values (`COMPLETED` not `DONE`). |

### BUG-EMAIL-02: Automation email template variables partially unresolved

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Component** | Backend -- `AutomationContext` context builders |
| **Description** | Several template variables are not available in the automation context: (1) `{{project.name}}` is null for `TaskCompletedEvent` because the event's details map only includes `"title"`, not `"project_name"`. (2) `{{invoice.totalAmount}}` is not populated for `InvoicePaidEvent` because `AutomationContext.buildInvoiceStatusChanged()` only adds `invoiceNumber`, `status`, and `customerId` to the invoice context. |
| **Root cause** | `TaskCompletedEvent` published with `Map.of("title", task.getTitle())` at `TaskService.java:749` -- missing `project_name`. `AutomationContext.buildTaskCompleted()` calls `detailValue(event, "project_name")` which returns null. Similarly, `buildInvoiceStatusChanged()` never adds `totalAmount` to the invoice map. |
| **Affected variables** | `{{project.name}}` (task events), `{{invoice.totalAmount}}` (invoice events). Potentially other variables not tested. |
| **Workaround** | Use only variables that are known to resolve: `{{task.name}}`, `{{invoice.invoiceNumber}}`, `{{customer.name}}`, `{{actor.name}}`. |

## Variables Resolution Matrix

| Variable | Task Events | Invoice Events | Customer Events |
|----------|------------|----------------|-----------------|
| `{{task.name}}` | RESOLVES | N/A | N/A |
| `{{task.status}}` | RESOLVES | N/A | N/A |
| `{{project.name}}` | **NULL** | N/A | N/A |
| `{{project.id}}` | RESOLVES | N/A | N/A |
| `{{customer.name}}` | **NULL** (no customer_name in details) | RESOLVES | RESOLVES |
| `{{invoice.invoiceNumber}}` | N/A | RESOLVES | N/A |
| `{{invoice.totalAmount}}` | N/A | **NULL** | N/A |
| `{{invoice.status}}` | N/A | RESOLVES | N/A |
| `{{actor.name}}` | RESOLVES | RESOLVES | RESOLVES |
| `{{rule.name}}` | RESOLVES | RESOLVES | RESOLVES |

## Email Samples

### Email 1: Task Completion

```
From: noreply@docteams.app
To: alice@e2e-test.local
Subject: Task completed: Prepare trial balance

Hi Alice Owner,

The task Prepare trial balance on project {{project.name}} has been completed.

Best,
E2E Test Organization
```

### Email 2: Invoice Paid

```
From: noreply@docteams.app
To: alice@e2e-test.local
Subject: Invoice INV-0001 paid

Hi Alice Owner,

Great news! Invoice INV-0001 for customer Naledi Hair Studio has been marked as paid. Amount: {{invoice.totalAmount}}.

Best,
E2E Test Organization
```
