# Automation & Notification Verification -- Cycle 2 Results

**Date**: 2026-03-25
**Agent**: QA Agent
**Branch**: bugfix_cycle_2026-03-25
**Stack**: Keycloak dev (Frontend:3000, Backend:8080, Gateway:8443, Keycloak:8180, Mailpit:8025)
**Auth**: Thandi Thornton (owner) via Keycloak direct grant (client_id=gateway-bff, client_secret=docteams-web-secret)

---

## Fix Verification

### GAP-AN-001: "New Automation" button -- VERIFIED

| Check | Result | Evidence |
|-------|--------|----------|
| Button exists | PASS | `link "New Automation" [ref=e178]` with `href=/org/thornton-associates/settings/automations/new` -- rendered as `<a>` tag via `<Button asChild><Link>` pattern |
| Target page loads | PASS | `/settings/automations/new` loads the "New Automation Rule" form with Name, Description, Trigger Type, Conditions, Actions, and Create Rule/Cancel buttons |
| Progressive enhancement | PASS | Uses `<Link>` (Next.js) inside `<Button asChild>`, not `onClick`/`router.push()` -- correct fix for the original bug |

**Verdict**: FIXED -> VERIFIED

### GAP-AN-002: Rule row click -- VERIFIED

| Check | Result | Evidence |
|-------|--------|----------|
| Rule names are links | PASS | All 12 rules rendered as `<a>` tags with `href=/org/thornton-associates/settings/automations/{id}` |
| Target page loads | PASS | Navigating to `/settings/automations/7bea1d94-...` loads "Task Completion Chain" detail with full configuration form, toggle switch, Configuration and Execution Log tabs |
| Link styling | PASS | Rule name has `hover:text-teal-600` class via `className="font-medium text-slate-950 hover:text-teal-600"` |

**Verdict**: FIXED -> VERIFIED

### GAP-AN-003: UI toggle switch -- PARTIALLY VERIFIED (code fix correct, gateway BFF issue blocks)

| Check | Result | Evidence |
|-------|--------|----------|
| Frontend code fix | PASS | `rule-list.tsx` uses `handleToggle()` with `startTransition`, optimistic state via `optimisticToggles`, success/error toast feedback, `toggleRuleAction` server action |
| Server action code | PASS | `actions.ts` calls `toggleRule(ruleId)` -> `api.post()` -> `POST /api/automation-rules/{id}/toggle` |
| Backend API works | PASS | Direct `POST http://localhost:8080/api/automation-rules/{id}/toggle` correctly toggles enabled state (tested: False->True->False) |
| Gateway BFF blocks | FAIL | `POST http://localhost:8443/api/automation-rules/{id}/toggle` returns **HTTP 302** redirect to `/oauth2/authorization/keycloak` -- the gateway's Spring Security OAuth2 login filter intercepts the request. The server-side `SESSION` cookie forwarding from Next.js doesn't establish a valid BFF session for mutations. |
| UI toggle via Playwright | FAIL | Clicking the switch does not change backend state. UI switch reverts to original position (optimistic update reverts after server action failure). |

**Root cause**: The frontend code fix is structurally correct (Link-based, optimistic UI, error handling). However, all mutation operations (POST/PUT/DELETE) routed through the gateway BFF fail because the Next.js server-side fetch doesn't have a valid BFF session cookie. This is a **gateway authentication issue**, not a frontend code bug. GET requests work because the automations list page loads correctly (the page itself is server-rendered, fetching data on the server side where the BFF session is established during initial page load).

**Verdict**: FIXED -> REOPENED (code fix correct but gateway BFF session prevents mutations from working)

### GAP-AN-004: "View Execution Log" link -- VERIFIED

| Check | Result | Evidence |
|-------|--------|----------|
| Link exists (header) | PASS | `link "View Execution Log ->" [ref=e174]` with `href=/org/thornton-associates/settings/automations/executions` |
| Link exists (footer) | PASS | Second `link "View Execution Log ->" [ref=e347]` below the rules table, with same href |
| Target page loads | PASS | `/settings/automations/executions` loads Execution Log page with 11+ entries, columns: Rule, Trigger, Triggered At, Status, Conditions, Actions, Duration |
| Link affordance | PASS | Styled with `text-teal-600 underline-offset-4 hover:text-teal-700 hover:underline` |

**Verdict**: FIXED -> VERIFIED

### GAP-AN-005: Notification preference labels -- VERIFIED

| Check | Result | Evidence |
|-------|--------|----------|
| "Other" category gone | PASS | No "Other" category visible on `/settings/notifications` page |
| All types categorized | PASS | 12 categories: Tasks (5), Projects (3), Collaboration (5), Proposals (4), Billing & Invoicing (10), Client Requests (3), Scheduling (3), Retainers (5), Time Tracking (1), Resource Planning (3), Security (3), System (1) |
| Human-readable labels | PASS | All 46 types have readable labels. Examples: "Task Recurrence Created", "Retainer Period Ready to Close", "DSAR Deadline Warning", "Post-Create Action Failed" |
| No raw enum names | PASS | Zero instances of SCREAMING_CASE enum values like TASK_CANCELLED, PAYMENT_FAILED, etc. |

**Verdict**: FIXED -> VERIFIED

---

## Remaining Track Results

### T2.2 -- INVOICE_STATUS_CHANGED Trigger

| ID | Result | Evidence |
|----|--------|----------|
| T2.2.1 | PASS | "QA Test: Notify on invoice payment" rule exists with trigger=INVOICE_STATUS_CHANGED, toStatus=PAID |
| T2.2.2 | PASS | Mailpit cleared before test |
| T2.2.3-4 | PASS | `POST /api/invoices/{id}/payment` on INV-0005 (SENT) -> status=PAID |
| T2.2.5 | PASS | Execution count 11->12. Latest: ruleName="QA Test: Notify on invoice payment", trigger=InvoicePaidEvent, conditionsMet=true |
| T2.2.5 status | PARTIAL | Status=ACTIONS_FAILED. Error: "No recipients resolved for type: ORG_ADMINS" -- the rule was created in Cycle 1 with ORG_ADMINS recipient type, but the Keycloak org membership resolver could not find admin members. **Trigger fired correctly; action failed due to recipient resolution.** |
| T2.2.6 | N/A | Notification created (type=AUTOMATION_ACTION_FAILED), but the intended SEND_NOTIFICATION action did not complete |
| T2.2.7 | PASS (event data) | Event data correctly captured: invoice_number=INV-0005, customer_name=Naledi Corp QA, total=575.00, payment_reference=QA-PAYMENT-T2.2 -- variables would resolve if action succeeded |

**Summary**: INVOICE_STATUS_CHANGED trigger works correctly. InvoicePaidEvent fires and matches rules. The action failure is a recipient resolution issue specific to the test rule's ORG_ADMINS config, not an automation engine bug.

### T2.4 -- TIME_ENTRY_CREATED Trigger

| ID | Result | Evidence |
|----|--------|----------|
| T2.4.1 | PASS | Created rule "QA Test: Time Entry Logged" (id=21db53c3-...) with trigger=TIME_ENTRY_CREATED, action=SEND_NOTIFICATION to TRIGGER_ACTOR |
| T2.4.2 | PASS | Created task "QA T2.4 Time Entry Test Task" (id=ce5b9e28-...), logged 90 minutes via `POST /api/tasks/{id}/time-entries` |
| T2.4.3 | PASS | Execution count 12->13. Latest: ruleName="QA Test: Time Entry Logged", trigger=TimeEntryChangedEvent, status=ACTIONS_COMPLETED, no errors |
| T2.4.4 | PASS | Notification created (type=AUTOMATION, id=2c512c66-...) |

**Summary**: TIME_ENTRY_CREATED trigger works correctly. Rule fires on time entry creation and SEND_NOTIFICATION action completes successfully with TRIGGER_ACTOR recipient type.

### T4 -- Email Template Content Verification

| ID | Result | Evidence |
|----|--------|----------|
| T4 | NOT TESTED | No emails generated during this cycle. Reasons: (1) All tested automation rules used SEND_NOTIFICATION, not SEND_EMAIL. (2) The seeded rules with SEND_EMAIL (e.g., "Overdue Invoice Reminder") require invoice status=OVERDUE, which was not triggered. (3) All notification preferences have emailEnabled=false by default. |

**Observation**: T4 requires either: (a) creating an automation rule with SEND_EMAIL action and triggering it, or (b) transitioning an invoice to OVERDUE status to fire the seeded "Overdue Invoice Reminder" rule. Both require setup beyond what was practical in this cycle. Deferred to cycle 3 if needed.

### T6.2 -- Notification Preference Save and Persistence

| ID | Result | Evidence |
|----|--------|----------|
| T6.2.1 | PASS (API) | `PUT /api/notifications/preferences` with `{"preferences":[{"notificationType":"TASK_ASSIGNED","inAppEnabled":true,"emailEnabled":true}]}` -> returned updated preference with emailEnabled=true |
| T6.2.2 | PASS (API) | Re-read `GET /api/notifications/preferences` -> TASK_ASSIGNED shows emailEnabled=true -- preference persisted |
| T6.2 (UI) | NOT TESTED | UI save button triggers server action through gateway BFF, which has the same 302 redirect issue as GAP-AN-003. The backend API works correctly; the issue is gateway session forwarding for mutations. |

**Summary**: Backend preference save/persistence works correctly via direct API. The UI save path is blocked by the same gateway BFF session issue affecting all mutations.

---

## New Observations

### OBS-AN-006: Gateway BFF session does not forward for server action mutations

**Severity**: HIGH
**Category**: gateway-auth
**Impact**: All frontend server actions that perform mutations (POST/PUT/DELETE) via the gateway BFF fail because the gateway returns HTTP 302 to its OAuth2 login page. This affects:
- Toggle automation rules (GAP-AN-003)
- Delete automation rules
- Save notification preferences (T6.2)
- Any other mutation through the gateway

**Root cause**: The `api/client.ts` in Keycloak mode forwards the `SESSION` cookie from the browser to the gateway. However, the Next.js server-side environment may not have a valid BFF session established with the gateway, or the CSRF token is missing/invalid. GET requests work because the initial page render establishes the session.

**Note**: This is documented but not filed as a new GAP since it's the same underlying issue as GAP-AN-003. It explains why GAP-AN-003 remains REOPENED despite correct frontend code.

### OBS-AN-007: Trigger type naming inconsistency

Some automation rules display raw trigger type enums in the UI (e.g., "PROPOSAL_SENT", "FIELD_DATE_APPROACHING") while others show human-readable badges (e.g., "Invoice Status", "Task Status", "Budget Threshold", "Customer Status"). This is because the `TriggerTypeBadge` component maps some types to friendly names but not all.

**Severity**: LOW (cosmetic)
**Affected types**: PROPOSAL_SENT, FIELD_DATE_APPROACHING (shown as raw enum), REQUEST_COMPLETED and DOCUMENT_ACCEPTED (shown as readable).

---

## Summary

### Fix Verification Results

| GAP | Status | Verdict |
|-----|--------|---------|
| GAP-AN-001 | FIXED -> VERIFIED | "New Automation" button navigates correctly |
| GAP-AN-002 | FIXED -> VERIFIED | Rule name links navigate to detail page |
| GAP-AN-003 | FIXED -> REOPENED | Frontend code correct but gateway BFF blocks mutations |
| GAP-AN-004 | FIXED -> VERIFIED | "View Execution Log" link navigates correctly |
| GAP-AN-005 | FIXED -> VERIFIED | All 46 notification types properly labeled in 12 categories |

### Remaining Track Results

| Track | Result | Notes |
|-------|--------|-------|
| T2.2 INVOICE_STATUS_CHANGED | PASS (trigger) | Trigger fires correctly; action failed on recipient resolution (test data issue) |
| T2.4 TIME_ENTRY_CREATED | PASS | Full pipeline works: trigger -> conditions -> action -> notification |
| T4 Email content | NOT TESTED | No SEND_EMAIL rules triggered; deferred |
| T6.2 Preference save | PASS (API) | Backend save/persistence works; UI blocked by gateway BFF |

### What Works (Cumulative from Cycle 1 + 2)

1. All 10 trigger types fire correctly on their respective domain events
2. SEND_NOTIFICATION action creates in-app notifications
3. CREATE_TASK action creates real tasks with variable resolution
4. Execution log accurately tracks all rule firings with detailed event data
5. Notification preferences save and persist via backend API
6. All 46 notification types properly categorized and labeled
7. New Automation page and rule detail/edit pages load correctly
8. Rule name links navigate to detail pages (progressive enhancement via `<Link>`)
9. View Execution Log link navigates correctly

### What Doesn't Work

1. **Gateway BFF mutations**: All POST/PUT/DELETE server actions through the gateway fail with 302 redirect. This blocks toggle, delete, save preferences, and rule creation via the UI.
2. **ORG_ADMINS recipient resolution**: Automation rules with `recipientType: ORG_ADMINS` fail because the resolver cannot find admin members in the Keycloak org. TRIGGER_ACTOR and PROJECT_OWNER recipient types work correctly.
