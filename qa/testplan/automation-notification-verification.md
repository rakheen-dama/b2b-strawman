# Test Plan: Automation & Notification Content Verification
## DocTeams Platform — Event-Driven Pipeline Testing

**Version**: 1.0
**Date**: 2026-03-17
**Author**: Product + QA
**Vertical**: accounting-za (Thornton & Associates)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025). See `qa/keycloak-e2e-guide.md` for setup.
**Depends on**: Phase 49 test plan T0 (seed data) — run that first or share the same seeded environment

---

## 1. Purpose

The automation and notification systems are fully built (10 trigger types, 5 action types,
23 email templates, in-app notifications, execution tracking) but have **zero E2E test
coverage**. Backend integration tests verify individual components — but nobody has tested
whether creating a real automation rule, triggering it via a real user action, and checking
the resulting email in Mailpit actually works end-to-end.

This is the same "content verification" philosophy as Phase 49: the plumbing works, but
does the right thing come out the other end?

**Core question**: If Thornton & Associates sets up an automation rule "When a customer
transitions to ACTIVE, send an email to the account manager" — does Alice actually get
that email? And does it say the right thing?

## 2. Scope

### In Scope

| Track | Description | Method |
|-------|-------------|--------|
| T1 | Automation rule CRUD via UI | Automated (Playwright) |
| T2 | Trigger verification — fire real events, verify rules execute | Automated |
| T3 | Action verification — verify each action type produces correct output | Automated + Mailpit |
| T4 | Email template content verification — read emails in Mailpit, check content | Automated |
| T5 | In-app notification content verification | Automated (Playwright DOM) |
| T6 | Notification preferences — disable channel, verify it's respected | Automated |
| T7 | Vertical automation templates — accounting-za seeded rules fire correctly | Automated |

### Out of Scope

- Automation rule authoring UX polish (button placement, form validation)
- Email deliverability (DKIM, SPF, inbox placement — not applicable in E2E)
- Push notifications (not implemented)
- Scheduled automations beyond `FIELD_DATE_APPROACHING` (which uses `@Scheduled`)

## 3. Prerequisites

### 3.1 Shared Seed Data

Same as Phase 49 T0 — Thornton & Associates 90-day lifecycle seeded.

**Additional prerequisites:**

| Requirement | How to verify |
|-------------|---------------|
| Automation templates seeded (accounting-za pack) | Settings > Automations shows rules |
| Mailpit is empty (or note existing email count) | `GET http://localhost:8025/api/v1/messages` |
| At least 1 customer in PROSPECT status (for trigger testing) | Create a fresh test customer |

### 3.2 Mailpit API Reference

Mailpit exposes a REST API for checking received emails:

```
GET  http://localhost:8025/api/v1/messages              # List all messages
GET  http://localhost:8025/api/v1/message/{id}           # Get specific message (headers + body)
GET  http://localhost:8025/api/v1/search?query=subject:X # Search by subject, to, from, etc.
DELETE http://localhost:8025/api/v1/messages              # Clear all messages
```

**Before each track**: Clear Mailpit (`DELETE /api/v1/messages`) to isolate email assertions.

### 3.3 Notation

- [ ] **PASS** — automation fired, action executed, content correct
- [ ] **FAIL** — automation didn't fire, wrong action, wrong content
- [ ] **PARTIAL** — fired but with issues (wrong recipient, missing data in email)
- [ ] **NOT_FIRED** — event occurred but automation didn't trigger (check rule config)

---

## 4. Test Tracks

---

### Track 1 — Automation Rule CRUD

**Goal**: Verify the firm can create, edit, enable/disable, and delete automation rules
via the UI. This is the setup for all subsequent trigger tests.

**Actor**: Alice (Owner)

#### T1.1 — View Seeded Automation Rules

- [ ] **T1.1.1** Navigate to Settings > Automations
- [ ] **T1.1.2** Verify accounting-za automation templates are listed (expect ~4 rules from vertical pack)
- [ ] **T1.1.3** Note the seeded rules and their trigger types:
  - Expected: CUSTOMER_STATUS_CHANGED, FIELD_DATE_APPROACHING, PROPOSAL_SENT, and possibly others
- [ ] **T1.1.4** Each rule shows: name, trigger type, enabled/disabled status

#### T1.2 — Create a Custom Automation Rule

- [ ] **T1.2.1** Click "New Automation" (or equivalent button)
- [ ] **T1.2.2** Create rule:
  - Name: "Notify Alice on invoice payment"
  - Trigger: INVOICE_STATUS_CHANGED
  - Condition: status = PAID
  - Action: SendNotification
  - Recipient: Alice
  - Message: "Invoice {{invoice.invoiceNumber}} has been paid by {{customer.name}}"
- [ ] **T1.2.3** Save → verify rule appears in list as ENABLED
- [ ] **T1.2.4** Note the rule ID for later verification

#### T1.3 — Edit Rule

- [ ] **T1.3.1** Open the custom rule → edit the name to "Payment received — notify Alice"
- [ ] **T1.3.2** Save → verify name updated in list

#### T1.4 — Disable / Enable Rule

- [ ] **T1.4.1** Disable the custom rule (toggle or button)
- [ ] **T1.4.2** Verify status shows DISABLED
- [ ] **T1.4.3** Re-enable the rule
- [ ] **T1.4.4** Verify status shows ENABLED

#### T1.5 — View Execution History

- [ ] **T1.5.1** Navigate to Settings > Automations > Executions (or execution log page)
- [ ] **T1.5.2** Verify the page loads (may be empty if no automations have fired yet)
- [ ] **T1.5.3** Note the table columns: rule name, trigger event, status, timestamp

---

### Track 2 — Trigger Verification

**Goal**: For each trigger type, perform the real user action that should fire the trigger
and verify the automation executes. We test a subset of the 10 triggers — the ones most
relevant to the accounting vertical.

#### T2.1 — CUSTOMER_STATUS_CHANGED

**Setup**: Create a new test customer to have a clean trigger.

- [ ] **T2.1.1** Verify a seeded automation rule exists for CUSTOMER_STATUS_CHANGED
  (or create one: trigger = CUSTOMER_STATUS_CHANGED, condition = newStatus = ONBOARDING,
  action = SendNotification to Alice, message = "{{customer.name}} moved to Onboarding")
- [ ] **T2.1.2** Clear Mailpit
- [ ] **T2.1.3** Create customer: "Test Automation Client"
- [ ] **T2.1.4** Transition customer from PROSPECT → ONBOARDING
- [ ] **T2.1.5** Wait 5 seconds → check automation execution history:
  - Rule name matches
  - Status = COMPLETED (not FAILED or PENDING)
  - Trigger event = CUSTOMER_STATUS_CHANGED
- [ ] **T2.1.6** Check notifications (bell icon or notifications page) → notification present for Alice
- [ ] **T2.1.7** Check Mailpit → if action includes SendEmail, verify email received

#### T2.2 — INVOICE_STATUS_CHANGED

**Setup**: Use the custom rule created in T1.2 (notify on PAID), or create a fresh one.

- [ ] **T2.2.1** Verify the rule is ENABLED
- [ ] **T2.2.2** Clear Mailpit
- [ ] **T2.2.3** Navigate to an existing SENT invoice → click "Record Payment"
- [ ] **T2.2.4** Enter payment reference → submit → invoice status = PAID
- [ ] **T2.2.5** Wait 5 seconds → check execution history:
  - Rule fired for INVOICE_STATUS_CHANGED
  - Status = COMPLETED
- [ ] **T2.2.6** Check notifications → notification present with correct invoice number
- [ ] **T2.2.7** Notification message contains the real invoice number and customer name
  (not `{{invoice.invoiceNumber}}` as literal text)

#### T2.3 — TASK_STATUS_CHANGED

- [ ] **T2.3.1** Create rule: trigger = TASK_STATUS_CHANGED, condition = newStatus = COMPLETED,
  action = SendNotification to project owner, message = "Task {{task.name}} completed on {{project.name}}"
- [ ] **T2.3.2** Navigate to a project → open a task → mark as COMPLETED
- [ ] **T2.3.3** Check execution history → rule fired, COMPLETED
- [ ] **T2.3.4** Check notification → message contains actual task name and project name

#### T2.4 — TIME_ENTRY_CREATED

- [ ] **T2.4.1** Create rule: trigger = TIME_ENTRY_CREATED,
  action = SendNotification to project owner, message = "{{member.name}} logged {{timeEntry.duration}} on {{project.name}}"
- [ ] **T2.4.2** Log a time entry on a project
- [ ] **T2.4.3** Check execution history → rule fired
- [ ] **T2.4.4** Check notification → message contains member name, duration, project name

#### T2.5 — PROPOSAL_SENT

- [ ] **T2.5.1** Verify a seeded rule exists for PROPOSAL_SENT (from accounting-za pack)
- [ ] **T2.5.2** Clear Mailpit
- [ ] **T2.5.3** Create a new proposal → send it
- [ ] **T2.5.4** Check execution history → rule fired for PROPOSAL_SENT
- [ ] **T2.5.5** Check Mailpit → if action is SendEmail, verify email content

#### T2.6 — BUDGET_THRESHOLD_REACHED

- [ ] **T2.6.1** Create rule: trigger = BUDGET_THRESHOLD_REACHED, threshold = 80%,
  action = SendNotification to project owner
- [ ] **T2.6.2** Find a project with a budget set and hours near 80%
  (or set a small budget like 2 hours on a project, then log 1.5 hours)
- [ ] **T2.6.3** Log enough time to cross the 80% threshold
- [ ] **T2.6.4** Check execution history → rule fired
- [ ] **T2.6.5** Notification mentions the project name and budget percentage

#### T2.7 — Disabled Rule Does NOT Fire

**Critical negative test**: An event matching a disabled rule should NOT trigger execution.

- [ ] **T2.7.1** Disable the TASK_STATUS_CHANGED rule from T2.3
- [ ] **T2.7.2** Mark another task as COMPLETED
- [ ] **T2.7.3** Check execution history → NO new execution for the disabled rule
- [ ] **T2.7.4** Re-enable the rule after verification

---

### Track 3 — Action Verification

**Goal**: Verify each action type produces the correct output when triggered.

#### T3.1 — SendNotification Action

- [ ] **T3.1.1** Covered by T2.1-T2.6 — verify notifications appear in-app
- [ ] **T3.1.2** Additional check: notification bell counter increments
- [ ] **T3.1.3** Notification is marked as unread initially
- [ ] **T3.1.4** Clicking the notification marks it as read

#### T3.2 — SendEmail Action

- [ ] **T3.2.1** Create rule with SendEmail action (trigger = any convenient event)
- [ ] **T3.2.2** Specify recipient email (Alice's email or a test email)
- [ ] **T3.2.3** Fire the trigger event
- [ ] **T3.2.4** Check Mailpit → email received
- [ ] **T3.2.5** Email contains:
  - Correct recipient
  - Subject line with context (not empty)
  - Body with resolved variables (not `{{...}}` literals)
  - Firm branding (name, possibly colour)

#### T3.3 — CreateTask Action

- [ ] **T3.3.1** Create rule: trigger = CUSTOMER_STATUS_CHANGED, condition = newStatus = ACTIVE,
  action = CreateTask, task name = "Schedule onboarding call with {{customer.name}}"
- [ ] **T3.3.2** Transition a customer to ACTIVE
- [ ] **T3.3.3** Check execution history → rule fired, COMPLETED
- [ ] **T3.3.4** Navigate to the relevant project → verify a new task was created
- [ ] **T3.3.5** Task name contains the actual customer name (not `{{customer.name}}`)
- [ ] **T3.3.6** If the task has no associated project context → note how the system chooses where to create it

#### T3.4 — CreateProject Action (if supported)

- [ ] **T3.4.1** If CreateProject is available as an action → create a rule that auto-creates a project
  on some trigger (e.g., CUSTOMER_STATUS_CHANGED to ACTIVE)
- [ ] **T3.4.2** Fire the trigger → verify project created
- [ ] **T3.4.3** If not configurable via UI → note as observation

#### T3.5 — AssignMember Action (if supported)

- [ ] **T3.5.1** If AssignMember is available → create rule that assigns a member to a task/project
  on a trigger
- [ ] **T3.5.2** Fire the trigger → verify assignment occurred
- [ ] **T3.5.3** If not configurable via UI → note as observation

---

### Track 4 — Email Template Content Verification

**Goal**: The system has 23 email templates. When specific events occur, the emails sent
to Mailpit should contain correct, resolved content — not broken templates or missing data.

**Method**: Trigger events that send system emails (not automation emails), then read the
email content from Mailpit and verify.

#### T4.1 — Invoice Delivery Email

- [ ] **T4.1.1** Clear Mailpit
- [ ] **T4.1.2** Create a new invoice for a customer → approve → send
- [ ] **T4.1.3** Check Mailpit → email received
- [ ] **T4.1.4** Email content check:
  - Subject references invoice number or "Invoice"
  - Body contains customer name
  - Body contains invoice amount in ZAR
  - Body contains a link or PDF attachment
  - No `{{...}}` unresolved variables
  - Firm name/branding present

#### T4.2 — Proposal Notification Email (to Portal Contact)

- [ ] **T4.2.1** Clear Mailpit
- [ ] **T4.2.2** Send a proposal to a portal contact
- [ ] **T4.2.3** Check Mailpit → email received at portal contact's address
- [ ] **T4.2.4** Email content check:
  - Subject references the proposal or firm
  - Body contains the proposal title
  - Body contains the fee/amount
  - Body contains a portal link to view/accept
  - Firm branding present
  - No unresolved variables

#### T4.3 — Information Request Email (to Portal Contact)

- [ ] **T4.3.1** Clear Mailpit
- [ ] **T4.3.2** Send an information request to a customer's portal contact
- [ ] **T4.3.3** Check Mailpit → email received
- [ ] **T4.3.4** Email content check:
  - Subject references the request or "documents required"
  - Body lists the requested items (or summarises the request)
  - Body contains a portal link
  - Firm name/branding present
  - No unresolved variables

#### T4.4 — Document Acceptance Confirmation Email

- [ ] **T4.4.1** Clear Mailpit
- [ ] **T4.4.2** Portal contact accepts a document (e.g., engagement letter)
- [ ] **T4.4.3** Check Mailpit → confirmation email sent (to firm and/or portal contact)
- [ ] **T4.4.4** Email content check:
  - References the document title
  - Contains acceptance date/time
  - Mentions who accepted
  - No unresolved variables

#### T4.5 — Member Added Notification Email

- [ ] **T4.5.1** Clear Mailpit
- [ ] **T4.5.2** Add a team member to a project (if the UI supports it)
- [ ] **T4.5.3** Check Mailpit → notification email to the added member
- [ ] **T4.5.4** Email content check:
  - References the project name
  - Mentions the member's role
  - Contains a link to the project
  - No unresolved variables

#### T4.6 — Magic Link Email

- [ ] **T4.6.1** Clear Mailpit
- [ ] **T4.6.2** Request a magic link for a portal contact
- [ ] **T4.6.3** Check Mailpit → magic link email received
- [ ] **T4.6.4** Email content check:
  - Subject: references "portal access" or firm name
  - Body: contains a single clickable link
  - Link: valid URL with token parameter
  - Firm branding: name, possibly logo
  - Security: mentions link expiry or single-use nature
  - No unresolved variables

#### T4.7 — Email Template Branding Consistency

After checking multiple email types, assess overall branding:

- [ ] **T4.7.1** Do all emails use the same base layout/template?
- [ ] **T4.7.2** Is the firm name consistent across all emails?
- [ ] **T4.7.3** Is the brand colour used in email styling (header, buttons)?
- [ ] **T4.7.4** Do emails have a consistent footer (firm address, unsubscribe, etc.)?
- [ ] **T4.7.5** If branding is inconsistent → log as GAP (per-template fix needed)

---

### Track 5 — In-App Notification Content

**Goal**: Verify that in-app notifications (bell icon, notification page) contain correct,
actionable content — not generic messages or broken links.

**Actor**: Alice (Owner)

#### T5.1 — Notification Bell

- [ ] **T5.1.1** After triggering events in T2, check the notification bell in the header
- [ ] **T5.1.2** Bell shows an unread count (badge with number)
- [ ] **T5.1.3** Click bell → dropdown/panel shows recent notifications
- [ ] **T5.1.4** Each notification has: message text, timestamp, read/unread indicator

#### T5.2 — Notification Content

- [ ] **T5.2.1** Navigate to the Notifications page
- [ ] **T5.2.2** Verify notifications from T2 triggers are listed
- [ ] **T5.2.3** For each notification, verify:
  - Message contains resolved data (real customer names, project names, amounts)
  - No `{{...}}` literal text
  - Timestamp is recent (not epoch, not blank)
- [ ] **T5.2.4** Click a notification → verify it links to the relevant entity:
  - Invoice notification → links to invoice detail page
  - Task notification → links to task or project
  - Customer notification → links to customer detail
- [ ] **T5.2.5** If notification links are broken or go to wrong page → log as GAP

#### T5.3 — Mark As Read

- [ ] **T5.3.1** Click an unread notification → verify it's marked as read
- [ ] **T5.3.2** Bell counter decrements
- [ ] **T5.3.3** "Mark all as read" (if available) → counter goes to 0

---

### Track 6 — Notification Preferences

**Goal**: Verify that users can control which notifications they receive and through
which channels, and that the system respects those preferences.

#### T6.1 — View Preferences

**Actor**: Alice

- [ ] **T6.1.1** Navigate to Settings > Notifications (or notification preferences page)
- [ ] **T6.1.2** Verify preference categories are listed (e.g., invoices, tasks, proposals, etc.)
- [ ] **T6.1.3** Each category shows channel toggles (In-app, Email)
- [ ] **T6.1.4** Note the default state (all enabled? some disabled?)

#### T6.2 — Disable Email for a Category

- [ ] **T6.2.1** Disable email notifications for "Invoice" events (keep in-app enabled)
- [ ] **T6.2.2** Save preferences
- [ ] **T6.2.3** Clear Mailpit
- [ ] **T6.2.4** Trigger an invoice event (e.g., approve an invoice)
- [ ] **T6.2.5** Check in-app notifications → notification present → **PASS** (in-app still works)
- [ ] **T6.2.6** Check Mailpit → NO email for this event → **PASS** (preference respected)
- [ ] **T6.2.7** If email was sent despite being disabled → **FAIL** (preference not respected)

#### T6.3 — Re-Enable and Verify

- [ ] **T6.3.1** Re-enable email for "Invoice" events
- [ ] **T6.3.2** Clear Mailpit
- [ ] **T6.3.3** Trigger another invoice event
- [ ] **T6.3.4** Check Mailpit → email received → **PASS** (re-enabled correctly)

#### T6.4 — Preference Persistence

- [ ] **T6.4.1** Disable email for "Task" events
- [ ] **T6.4.2** Navigate away from preferences page
- [ ] **T6.4.3** Return to preferences page → verify "Task" email is still disabled
- [ ] **T6.4.4** Log out and log back in → verify preferences persisted

---

### Track 7 — Vertical Automation Templates

**Goal**: The accounting-za vertical profile seeds automation templates. Verify these
templates are correctly configured and actually fire for their intended scenarios.

#### T7.1 — Identify Seeded Rules

- [ ] **T7.1.1** Navigate to Settings > Automations
- [ ] **T7.1.2** List all rules that came from the accounting-za automation pack:
  - Note: name, trigger type, action type, enabled status
- [ ] **T7.1.3** Verify all seeded rules are ENABLED by default

#### T7.2 — Test Each Seeded Rule

For each seeded automation rule, trigger the corresponding event and verify execution.
Document each rule test:

```
Rule: [name]
Trigger: [trigger type]
Action: [action type]
Test: [what you did to trigger it]
Result: FIRED / NOT_FIRED
Execution Status: COMPLETED / FAILED
Output: [notification text or email content]
Variables Resolved: YES / NO
```

- [ ] **T7.2.1** Test seeded rule #1: [record name, trigger, action, result]
- [ ] **T7.2.2** Test seeded rule #2: [record name, trigger, action, result]
- [ ] **T7.2.3** Test seeded rule #3: [record name, trigger, action, result]
- [ ] **T7.2.4** Test seeded rule #4: [record name, trigger, action, result]

#### T7.3 — Execution History Accuracy

- [ ] **T7.3.1** After firing multiple rules, navigate to execution history page
- [ ] **T7.3.2** Verify all triggered rules appear in the log
- [ ] **T7.3.3** Each execution shows: rule name, trigger event, status, timestamp, duration
- [ ] **T7.3.4** Failed executions (if any) show error reason
- [ ] **T7.3.5** Verify execution count matches the number of rules triggered

---

## 5. Verification Prompts (for AI Agent Reading Emails)

### 5.1 Email Content Verification Prompt

```
Analyse this email from Mailpit and check for the following:

1. UNRESOLVED VARIABLES: Search for "{{" or "}}". Report each occurrence.
   Expected: zero.

2. RECIPIENT: Verify the "To" address matches the expected recipient.

3. SUBJECT LINE: Should be descriptive and contain context
   (entity name, action, or firm name). Not blank or generic.

4. BODY CONTENT:
   - Contains the expected entity references (customer name, invoice number, etc.)
   - Contains actionable information (link, amount, date)
   - Is readable and professional (not raw HTML tags or broken formatting)

5. BRANDING:
   - Firm/org name appears in the email
   - If HTML email: check for brand colour in inline styles or headers

6. LINKS:
   - Any links in the email point to valid URLs (portal or app)
   - Links contain expected paths (e.g., /portal/proposals/xxx)

Report as:
- PASS: [check description]
- FAIL: [description] — found: "[actual]", expected: "[expected]"
- WARN: [observation that may indicate an issue]
```

---

## 6. Gap Reporting Format

Same structure as Phase 49. Categories specific to this plan:

| Category | Examples |
|----------|---------|
| automation-trigger | Rule doesn't fire for the expected event |
| automation-action | Action executes but produces wrong output |
| automation-variable | Variable in action template not resolved |
| email-content | Email has wrong data, missing fields, unresolved variables |
| email-delivery | Email not sent (check Mailpit is empty) |
| notification-content | In-app notification has wrong or missing data |
| notification-link | Notification links to wrong page |
| preference-violation | Email sent despite being disabled in preferences |

---

## 7. Success Criteria

| Criterion | Target |
|-----------|--------|
| Custom automation rule CRUD works | PASS |
| CUSTOMER_STATUS_CHANGED trigger fires | PASS |
| INVOICE_STATUS_CHANGED trigger fires | PASS |
| TASK_STATUS_CHANGED trigger fires | PASS |
| TIME_ENTRY_CREATED trigger fires | PASS |
| PROPOSAL_SENT trigger fires | PASS |
| BUDGET_THRESHOLD_REACHED trigger fires | PASS |
| Disabled rules do NOT fire | PASS |
| SendNotification action produces in-app notification | PASS |
| SendEmail action produces email in Mailpit | PASS |
| CreateTask action creates real task | PASS |
| All email templates have zero unresolved variables | 0 occurrences |
| Email branding consistent across templates | PASS |
| Notification links navigate to correct pages | 100% |
| Notification preferences are respected | PASS |
| Vertical automation templates fire correctly | 100% |
| Execution history accurately reflects what happened | PASS |

---

## 8. Execution Notes

### Execution Order

1. **T1 — Automation CRUD**: Set up the rules needed for trigger testing.
2. **T7 — Vertical Templates**: Check seeded rules exist before creating custom ones.
3. **T2 — Trigger Verification**: Fire events, verify rules execute. Core of this plan.
4. **T3 — Action Verification**: Verify outputs (notifications, emails, tasks).
5. **T4 — Email Content**: Deep-dive into Mailpit emails from T2/T3.
6. **T5 — In-App Notifications**: Check notification bell and page content.
7. **T6 — Preferences**: Disable/enable and verify respected. Run last.

### Mailpit Isolation

Before each track that checks email, clear Mailpit:
```bash
curl -X DELETE http://localhost:8025/api/v1/messages
```
This prevents false positives from emails sent in earlier tracks.

### Timing Sensitivity

Automations may execute asynchronously. After triggering an event:
1. Wait 5 seconds before checking execution history
2. If execution shows PENDING after 10 seconds → possible issue
3. If execution never appears → rule matching or event listener may be broken

### When to Stop

- If T2.1 (CUSTOMER_STATUS_CHANGED) doesn't fire → investigate the automation event listener
  before testing other triggers. The issue is likely systemic (listener not wired, event bus
  broken) rather than trigger-specific.
- If emails arrive in Mailpit but with all variables unresolved → the `AutomationContext`
  builder is broken. Fix before testing more templates.
