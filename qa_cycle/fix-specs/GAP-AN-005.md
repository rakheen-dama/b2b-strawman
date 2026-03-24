# Fix Spec: GAP-AN-005 — "Other" notification prefs category shows raw enum names

## Problem
The notification preferences page (`/settings/notifications`) groups notification types into categories. The "Other" category contains 19 raw enum names (e.g., TASK_CANCELLED, PAYMENT_FAILED, PROPOSAL_SENT) displayed as-is instead of human-readable labels in appropriate categories. The backend returns 41 notification types total, but the frontend `NOTIFICATION_TYPE_LABELS` map only covers 24 of them. The remaining types fall through to the "Other" bucket and display their raw enum string.

## Root Cause
The `NOTIFICATION_TYPE_LABELS` map in `frontend/components/notifications/notification-preferences-form.tsx` (lines 10-72) was created during Phase 6.5 and has not been updated since. Subsequent phases added new notification types to the backend's `NOTIFICATION_TYPES` list in `NotificationService.java` (lines 131-178), but the frontend mapping was never extended.

**Backend types NOT mapped in the frontend (falling into "Other"):**

| Enum Value | Should Be Labeled | Should Be Categorized |
|---|---|---|
| TASK_CANCELLED | Task Cancelled | Tasks |
| TASK_RECURRENCE_CREATED | Task Recurrence Created | Tasks |
| PAYMENT_FAILED | Payment Failed | Billing & Invoicing |
| PAYMENT_LINK_EXPIRED | Payment Link Expired | Billing & Invoicing |
| BILLING_RUN_COMPLETED | Billing Run Completed | Billing & Invoicing |
| BILLING_RUN_SENT | Billing Run Sent | Billing & Invoicing |
| BILLING_RUN_FAILURES | Billing Run Failures | Billing & Invoicing |
| ACCEPTANCE_COMPLETED | Acceptance Completed | Collaboration |
| PROJECT_COMPLETED | Project Completed | Projects |
| PROJECT_ARCHIVED | Project Archived | Projects |
| PROPOSAL_SENT | Proposal Sent | Proposals |
| PROPOSAL_ACCEPTED | Proposal Accepted | Proposals |
| PROPOSAL_DECLINED | Proposal Declined | Proposals |
| PROPOSAL_EXPIRED | Proposal Expired | Proposals |
| PREREQUISITE_BLOCKED_ACTIVATION | Prerequisite Blocked Activation | Projects |
| INFORMATION_REQUEST_ITEM_SUBMITTED | Information Request Item Submitted | Client Requests |
| INFORMATION_REQUEST_COMPLETED | Information Request Completed | Client Requests |
| INFORMATION_REQUEST_DRAFT_CREATED | Information Request Draft Created | Client Requests |
| ROLE_PERMISSIONS_CHANGED | Role Permissions Changed | Security |
| RETENTION_PURGE_WARNING | Retention Purge Warning | Security |
| DSAR_DEADLINE_WARNING | DSAR Deadline Warning | Security |
| POST_CREATE_ACTION_FAILED | Post-Create Action Failed | System |

**Confirmed by code reading:**
- `backend/.../NotificationService.java` lines 131-178: 41 notification types
- `frontend/.../notification-preferences-form.tsx` lines 10-72: 24 types mapped
- Unmapped types: 17 (the QA report counted 19 in "Other" which may include slight differences in what was deployed vs. the source)

## Fix
Extend the `NOTIFICATION_TYPE_LABELS` map to cover all backend notification types, and add new category groups.

### Step 1: Update `NOTIFICATION_TYPE_LABELS` in `frontend/components/notifications/notification-preferences-form.tsx`

Replace the existing `NOTIFICATION_TYPE_LABELS` object (lines 10-72) with the complete mapping:

```typescript
const NOTIFICATION_TYPE_LABELS: Record<
  string,
  { label: string; category: string }
> = {
  // Tasks
  TASK_ASSIGNED: { label: "Task Assigned", category: "Tasks" },
  TASK_CLAIMED: { label: "Task Claimed", category: "Tasks" },
  TASK_UPDATED: { label: "Task Updated", category: "Tasks" },
  TASK_CANCELLED: { label: "Task Cancelled", category: "Tasks" },
  TASK_RECURRENCE_CREATED: { label: "Task Recurrence Created", category: "Tasks" },
  // Projects
  PROJECT_COMPLETED: { label: "Project Completed", category: "Projects" },
  PROJECT_ARCHIVED: { label: "Project Archived", category: "Projects" },
  PREREQUISITE_BLOCKED_ACTIVATION: { label: "Prerequisite Blocked", category: "Projects" },
  // Collaboration
  COMMENT_ADDED: { label: "Comment Added", category: "Collaboration" },
  DOCUMENT_SHARED: { label: "Document Shared", category: "Collaboration" },
  MEMBER_INVITED: { label: "Member Invited", category: "Collaboration" },
  DOCUMENT_GENERATED: { label: "Document Generated", category: "Collaboration" },
  ACCEPTANCE_COMPLETED: { label: "Acceptance Completed", category: "Collaboration" },
  // Proposals
  PROPOSAL_SENT: { label: "Proposal Sent", category: "Proposals" },
  PROPOSAL_ACCEPTED: { label: "Proposal Accepted", category: "Proposals" },
  PROPOSAL_DECLINED: { label: "Proposal Declined", category: "Proposals" },
  PROPOSAL_EXPIRED: { label: "Proposal Expired", category: "Proposals" },
  // Billing & Invoicing
  BUDGET_ALERT: { label: "Budget Alert", category: "Billing & Invoicing" },
  INVOICE_APPROVED: { label: "Invoice Approved", category: "Billing & Invoicing" },
  INVOICE_SENT: { label: "Invoice Sent", category: "Billing & Invoicing" },
  INVOICE_PAID: { label: "Invoice Paid", category: "Billing & Invoicing" },
  INVOICE_VOIDED: { label: "Invoice Voided", category: "Billing & Invoicing" },
  PAYMENT_FAILED: { label: "Payment Failed", category: "Billing & Invoicing" },
  PAYMENT_LINK_EXPIRED: { label: "Payment Link Expired", category: "Billing & Invoicing" },
  BILLING_RUN_COMPLETED: { label: "Billing Run Completed", category: "Billing & Invoicing" },
  BILLING_RUN_SENT: { label: "Billing Run Sent", category: "Billing & Invoicing" },
  BILLING_RUN_FAILURES: { label: "Billing Run Failures", category: "Billing & Invoicing" },
  // Client Requests
  INFORMATION_REQUEST_ITEM_SUBMITTED: { label: "Request Item Submitted", category: "Client Requests" },
  INFORMATION_REQUEST_COMPLETED: { label: "Request Completed", category: "Client Requests" },
  INFORMATION_REQUEST_DRAFT_CREATED: { label: "Request Draft Created", category: "Client Requests" },
  // Scheduling
  RECURRING_PROJECT_CREATED: { label: "Recurring Project Created", category: "Scheduling" },
  SCHEDULE_SKIPPED: { label: "Schedule Skipped", category: "Scheduling" },
  SCHEDULE_COMPLETED: { label: "Schedule Completed", category: "Scheduling" },
  // Retainers
  RETAINER_PERIOD_READY_TO_CLOSE: { label: "Retainer Period Ready to Close", category: "Retainers" },
  RETAINER_PERIOD_CLOSED: { label: "Retainer Period Closed", category: "Retainers" },
  RETAINER_APPROACHING_CAPACITY: { label: "Retainer Approaching Capacity", category: "Retainers" },
  RETAINER_FULLY_CONSUMED: { label: "Retainer Fully Consumed", category: "Retainers" },
  RETAINER_TERMINATED: { label: "Retainer Terminated", category: "Retainers" },
  // Time Tracking
  TIME_REMINDER: { label: "Time Reminders", category: "Time Tracking" },
  // Resource Planning
  ALLOCATION_CHANGED: { label: "Allocation Changed", category: "Resource Planning" },
  MEMBER_OVER_ALLOCATED: { label: "Member Over-Allocated", category: "Resource Planning" },
  LEAVE_CREATED: { label: "Leave Created", category: "Resource Planning" },
  // Security & Compliance
  ROLE_PERMISSIONS_CHANGED: { label: "Role Permissions Changed", category: "Security" },
  RETENTION_PURGE_WARNING: { label: "Retention Purge Warning", category: "Security" },
  DSAR_DEADLINE_WARNING: { label: "DSAR Deadline Warning", category: "Security" },
  // System
  POST_CREATE_ACTION_FAILED: { label: "Post-Create Action Failed", category: "System" },
};
```

### Step 2: Update `categoryOrder` array

Change the `categoryOrder` array (lines 127-136) to include the new categories:

```typescript
const categoryOrder = [
  "Tasks",
  "Projects",
  "Collaboration",
  "Proposals",
  "Billing & Invoicing",
  "Client Requests",
  "Scheduling",
  "Retainers",
  "Time Tracking",
  "Resource Planning",
  "Security",
  "System",
  "Other",
];
```

## Scope
Frontend only
Files to modify:
- `frontend/components/notifications/notification-preferences-form.tsx`

## Verification
Re-run QA checkpoint T6.1 — navigate to Settings > Notifications and verify:
1. No "Other" category appears (or it is empty)
2. All notification types display human-readable labels
3. New categories (Projects, Proposals, Client Requests, Security, System) are visible and correctly populated

## Estimated Effort
S (< 30 min) — pure data mapping, no logic changes.
