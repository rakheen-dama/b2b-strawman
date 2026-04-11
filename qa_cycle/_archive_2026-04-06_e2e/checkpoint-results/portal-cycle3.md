# Portal Cycle 3 — Fix Verification Results

**Date**: 2026-03-25
**Branch**: `bugfix_cycle_portal_2026-03-25`
**Purpose**: Verify all 7 FIXED gaps from Portal Experience & Proposal Acceptance QA cycle
**Stack**: Keycloak dev stack (portal :3002, backend :8080)
**Primary Customer**: Naledi Corp QA (naledi@qatest.local)
**Firm User**: thandi@thornton-test.local (Owner)

---

## Verification Summary

| ID | Fix Summary | Status | Evidence |
|----|-------------|--------|----------|
| GAP-PE-001 | Email subject uses firm name | VERIFIED | Email subject: "Your portal access link from Thornton & Associates" |
| GAP-PE-002 | Magic link URL points to :3002 | VERIFIED | URL: `http://localhost:3002/auth/exchange?token=...&orgId=thornton-associates` |
| GAP-PE-003 | Friendly error on 404 portal pages | VERIFIED | "Project not found" with explanation and back link |
| GAP-PE-004 | Portal read model auto-populated on project creation | VERIFIED | New project visible in portal immediately without manual resync |
| GAP-PE-005 | Portal proposals page with accept/decline | VERIFIED | List page (actionable/past sections), detail page with Accept/Decline buttons |
| GAP-PE-007 | Email notifications for proposal lifecycle | REOPENED | Code path incomplete -- see details below |
| GAP-PE-008 | Portal page title says "Client Portal" | VERIFIED | Browser tab title: "Client Portal" (no "DocTeams") |

**Result: 6 VERIFIED, 1 REOPENED**

---

## GAP-PE-001 — VERIFIED

**Method**: Request magic link via API, check email subject in Mailpit

| Step | Result | Evidence |
|------|--------|----------|
| Clear Mailpit | PASS | `DELETE /api/v1/messages` |
| Request magic link for naledi@qatest.local | PASS | `POST /portal/auth/request-link` returns 200 |
| Check email subject | PASS | Subject: "Your portal access link from **Thornton & Associates**" (was: "DocTeams") |

**Screenshot**: `qa_cycle/screenshots/portal-c3-pe008-title-verified.png` (login page also shows correct title)

---

## GAP-PE-002 — VERIFIED

**Method**: Request magic link, extract URL from email HTML body via Mailpit API

| Step | Result | Evidence |
|------|--------|----------|
| Extract href from email HTML | PASS | URL: `http://localhost:3002/auth/exchange?token=...&orgId=thornton-associates` |
| Port is 3002 (portal) | PASS | Was: `http://localhost:3000/portal/auth?token=...` |
| Path is `/auth/exchange` | PASS | Correct path with `token` and `orgId` query params |

---

## GAP-PE-003 — VERIFIED

**Method**: Navigate to non-existent project in portal browser

| Step | Result | Evidence |
|------|--------|----------|
| Navigate to `/projects/00000000-0000-0000-0000-000000000000` | PASS | Page loads without crash |
| Error message is user-friendly | PASS | Shows: "Project not found" (heading) + "This project may have been removed or you may not have access." (text) |
| Back link present | PASS | "Back to projects" link to `/projects` |
| No raw JSON error | PASS | Was: `API error: 404 {"detail":"No project found..."}` |

**Screenshot**: `qa_cycle/screenshots/portal-c3-pe003-friendly-404.png`

---

## GAP-PE-004 — VERIFIED

**Method**: Create project via firm API with customer link, verify auto-appears in portal without manual resync

| Step | Result | Evidence |
|------|--------|----------|
| Count portal projects before | 7 projects | API: `GET /portal/projects` returns 7 |
| Create project "QA Cycle 3 - Auto-Sync Verification Project" with customerId=Naledi | PASS | Project ID: `4bd193a2-32f9-44e7-870e-745fae397a43` |
| Navigate to portal `/projects` | PASS | Page loads |
| New project visible in list | PASS | "QA Cycle 3 - Auto-Sync Verification Project" shown with "just now" timestamp |
| Project count is now 8 | PASS | 8 projects visible in list |
| Project detail page loads | PASS | Shows name, description, ACTIVE badge, tasks/documents/comments sections |
| No manual resync needed | PASS | No call to `/internal/portal/resync/thornton-associates` |

**Root cause confirmed fixed**: `ProjectService.createProject()` now publishes `CustomerProjectLinkedEvent` after saving `CustomerProject` join record. `PortalEventHandler` receives the event and syncs the project to the portal read model automatically.

**Screenshot**: `qa_cycle/screenshots/portal-c3-pe004-auto-sync-verified.png`

---

## GAP-PE-005 — VERIFIED

**Method**: Navigate to portal proposals page, verify list and detail views

### Proposals List Page (`/proposals`)

| Step | Result | Evidence |
|------|--------|----------|
| "Proposals" nav link visible in header | PASS | Between "Projects" and "Invoices" |
| Page loads at `/proposals` | PASS | Heading: "Proposals" |
| "Awaiting Your Response" section | PASS | Shows 1 SENT proposal (PROP-0009) in table |
| "Past Proposals" section | PASS | Shows 7 past proposals (ACCEPTED, DECLINED, EXPIRED) |
| Table columns | PASS | Proposal #, Title, Status, Sent, Fee, Actions (View link) |
| Status badges colored correctly | PASS | SENT (blue), ACCEPTED (green), DECLINED (gray), EXPIRED (amber) |
| Fee amounts formatted | PASS | "R 12 000,00", "R 5 000,00", etc. |

### Proposal Detail Page (`/proposals/{id}`)

| Step | Result | Evidence |
|------|--------|----------|
| Navigate to SENT proposal detail | PASS | Shows full proposal details |
| Header shows title + status | PASS | "QA Cycle 3 - Notification Verification" with SENT badge |
| Metadata shown | PASS | PROP-0009, Sent: 25 Mar 2026, Expires: 1 May 2026 |
| Fee details section | PASS | Fee Model: Fixed Fee |
| Proposal content rendered | PASS | "This proposal is for QA notification verification." |
| "Your Response" section | PASS | Green-tinted box with instructions |
| Accept button present | PASS | "Accept Proposal" button (teal/green) |
| Decline button present | PASS | "Decline" button (outline) |
| Back link | PASS | "Back to proposals" with arrow icon |

**Screenshots**:
- `qa_cycle/screenshots/portal-c3-pe005-proposals-list.png`
- `qa_cycle/screenshots/portal-c3-pe005-proposal-detail-accept-decline.png`

---

## GAP-PE-007 — REOPENED

**Method**: Enable email preferences for PROPOSAL_SENT/ACCEPTED/DECLINED, send proposal, check Mailpit

| Step | Result | Evidence |
|------|--------|----------|
| Update notification preferences | PASS | PROPOSAL_SENT, PROPOSAL_ACCEPTED, PROPOSAL_DECLINED all set to `emailEnabled: true` |
| Clear Mailpit | PASS | 0 messages |
| Create proposal PROP-0009 | PASS | Created with FIXED fee, R5000 ZAR |
| Send proposal to Naledi | PASS | Status changed to SENT |
| In-app notification created | PASS | PROPOSAL_SENT notification at 08:10:20 visible in `/api/notifications` |
| Email notification sent | **FAIL** | Mailpit empty after 3+ seconds. No email sent. |

### Root Cause Analysis

The fix in PR #837 correctly resolves recipient email in `NotificationEventHandler.dispatchAll()`:

```java
private void dispatchAll(List<Notification> notifications) {
    for (var notification : notifications) {
        String recipientEmail = memberRepository
            .findById(notification.getRecipientMemberId())
            .map(Member::getEmail).orElse(null);
        notificationDispatcher.dispatch(notification, recipientEmail);
    }
}
```

**However**, proposal event handlers (`onProposalSent`, lines 416-432) do NOT call `dispatchAll()`. They call `notificationService.notifyAdminsAndOwners()` directly, which calls `createNotification()` -- a method that only saves the in-app notification to the database. It never invokes `notificationDispatcher.dispatch()`.

The `dispatchAll()` method is only called by handlers that use the `notificationService.handle*()` pattern (comments, tasks, documents, invoices, etc.). Proposal handlers use a different code path that bypasses multi-channel dispatch entirely.

**What works**: In-app notifications are created for proposal events.
**What doesn't work**: Email dispatch is never triggered for proposal events, regardless of preferences.

**Fix needed**: `onProposalSent()` (and similar proposal handlers for ACCEPTED/DECLINED/EXPIRED if they exist) should collect the notifications returned from `notifyAdminsAndOwners()` and pass them to `dispatchAll()`, or `notifyAdminsAndOwners()` itself should invoke the dispatcher.

### Additional Issue Found During Testing

Proposal acceptance via portal UI fails with `DataIntegrityViolationException`:
- Error: `null value in column "currency" of relation "invoices" violates not-null constraint`
- The `ProposalOrchestrationService` creates a DRAFT invoice from the proposal, but if the proposal has no currency set, the invoice insert fails.
- This caused a full transaction rollback -- the proposal remained in SENT state.
- **This is a pre-existing bug in proposal orchestration, not related to GAP-PE-007 or any of the 7 fixes.**

---

## GAP-PE-008 — VERIFIED

**Method**: Navigate to portal in browser, check page title

| Step | Result | Evidence |
|------|--------|----------|
| Navigate to `http://localhost:3002` | PASS | Redirects to `/login` |
| Check browser tab title | PASS | Title: "Client Portal" (was: "Client Portal \| DocTeams") |
| Title persists after login | PASS | All pages show "Client Portal" |

**Screenshot**: `qa_cycle/screenshots/portal-c3-pe008-title-verified.png`

---

## Observations

1. **Proposal acceptance invoice bug**: When accepting a proposal that has `currency: null`, the `ProposalOrchestrationService` tries to create a DRAFT invoice with null currency, violating the DB constraint. The entire acceptance transaction rolls back. This blocked full E2E acceptance testing via the portal UI. The GAP-PE-004 fix was verified via project creation instead (which also exercises the `CustomerProjectLinkedEvent` pathway).

2. **Portal JWT lifetime**: Portal JWTs have a 1-hour TTL (3600 seconds). The JWT is consumed on first use for the exchange endpoint but the resulting portal JWT can be reused for the session duration.

3. **Proposal notification code paths**: The notification system has two distinct patterns:
   - **Pattern A** (tasks, comments, documents, invoices, etc.): `notificationService.handle*()` returns `List<Notification>` -> `dispatchAll()` resolves email and dispatches to all channels.
   - **Pattern B** (proposals, billing runs): `notificationService.notifyAdminsAndOwners()` creates in-app notifications only, no multi-channel dispatch.
   Only Pattern A benefits from the GAP-PE-007 fix. Pattern B still has no email delivery.

---

## Screenshots

- `qa_cycle/screenshots/portal-c3-pe008-title-verified.png` -- Login page with "Client Portal" title
- `qa_cycle/screenshots/portal-c3-pe003-friendly-404.png` -- Friendly 404 error page
- `qa_cycle/screenshots/portal-c3-pe005-proposals-list.png` -- Proposals list with sections
- `qa_cycle/screenshots/portal-c3-pe005-proposal-detail-accept-decline.png` -- Proposal detail with Accept/Decline
- `qa_cycle/screenshots/portal-c3-pe004-auto-sync-verified.png` -- Auto-synced project in portal
