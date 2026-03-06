# Layer 2: Documents, Comments & Notifications (Scenario Outlines)

**Product:** DocTeams — Multi-tenant B2B SaaS Practice Management Platform
**Date:** 2026-03-06
**Prepared by:** QA Team

---

## How to Read This Document

Each scenario outline contains: title, objective, preconditions, key steps, and key validations. This is not full Given/When/Then; it is a structured skeleton suitable for manual or automated expansion. Actors are drawn from the [test team](00-overview.md#test-team) and [test customers](00-overview.md#test-customers).

---

## 1. Document Upload & Management

### DOC-U-001: Project-Scoped Document Upload (Happy Path)

**Objective:** Verify the full upload flow for a PROJECT-scoped document: init, presigned URL, S3 upload, confirm.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Acme Corp is ACTIVE with at least one project ("Annual Audit 2026")
- Yuki is a member of the project
- S3 (LocalStack) is running

**Key Steps:**
1. Yuki calls POST `/api/projects/{projectId}/documents/upload-init` with fileName "audit-report.pdf", contentType "application/pdf", size 524288.
2. System returns a presigned upload URL and a document ID. Document status is PENDING.
3. Yuki uploads the file to the presigned URL.
4. Yuki calls POST `/api/documents/{id}/confirm`.

**Key Validations:**
- After init: document record exists with status=PENDING, scope=PROJECT, visibility=INTERNAL, s3Key assigned.
- After confirm: status transitions to UPLOADED, uploadedAt is set.
- Document appears in project document list.
- Audit event logged with eventType containing "DOCUMENT" and entityType "DOCUMENT".

---

### DOC-U-002: Org-Scoped Document Upload

**Objective:** Verify upload of an ORG-scoped document (not tied to a project or customer).

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- Org exists and is provisioned

**Key Steps:**
1. Priya initiates an org-scoped document upload with scope=ORG, no projectId or customerId.
2. Priya uploads to presigned URL and confirms.

**Key Validations:**
- Document created with scope=ORG, projectId=null, customerId=null.
- Document appears in org-level document list.
- Document does NOT appear in any project document list.

---

### DOC-U-003: Customer-Scoped Document Upload

**Objective:** Verify upload of a CUSTOMER-scoped document tied to a specific customer.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Acme Corp exists and is ACTIVE

**Key Steps:**
1. Yuki initiates a customer-scoped document upload with scope=CUSTOMER, customerId=Acme Corp's ID.
2. Yuki uploads and confirms.

**Key Validations:**
- Document created with scope=CUSTOMER, customerId set, projectId=null.
- Document appears in customer document list for Acme Corp.

---

### DOC-U-004: Presigned Download URL

**Objective:** Verify that a confirmed document can be downloaded via presigned URL.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- A PROJECT-scoped document exists with status=UPLOADED

**Key Steps:**
1. Yuki calls GET `/api/documents/{id}/presign-download`.
2. System returns a presigned download URL.

**Key Validations:**
- Response contains a valid presigned URL with expiration.
- URL is accessible and returns the correct file content.

---

### DOC-U-005: Upload Cancel (No Confirm)

**Objective:** Verify that a document initiated but never confirmed remains in PENDING status.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has access to a project

**Key Steps:**
1. Yuki calls upload-init to create a document record.
2. Yuki does NOT call confirm. Time passes.

**Key Validations:**
- Document remains in PENDING status.
- Document does NOT appear in "uploaded" document lists (if filtered by UPLOADED status).

---

### DOC-U-006: Visibility Toggle (INTERNAL to SHARED)

**Objective:** Verify that an Admin/Owner can toggle document visibility from INTERNAL to SHARED.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A PROJECT-scoped document exists with visibility=INTERNAL and status=UPLOADED

**Key Steps:**
1. Priya updates the document's visibility to SHARED.

**Key Validations:**
- Document visibility changes to SHARED.
- Document becomes visible to portal contacts (tested in section 7).
- Audit event logged for visibility change.
- Notification of type DOCUMENT_SHARED sent to relevant recipients.

---

### DOC-U-007: Visibility Toggle (SHARED to INTERNAL)

**Objective:** Verify that visibility can be reverted from SHARED to INTERNAL.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A document exists with visibility=SHARED

**Key Steps:**
1. Priya updates the document's visibility to INTERNAL.

**Key Validations:**
- Document visibility changes to INTERNAL.
- Document is no longer visible to portal contacts.

---

### DOC-U-008: List Documents by Scope

**Objective:** Verify that document listing correctly filters by scope.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Documents exist across all three scopes: PROJECT, ORG, CUSTOMER

**Key Steps:**
1. Yuki lists project documents for a specific project.
2. Yuki lists org-level documents.
3. Yuki lists customer documents for Acme Corp.

**Key Validations:**
- Project list contains only PROJECT-scoped documents for that project.
- Org list contains only ORG-scoped documents.
- Customer list contains only CUSTOMER-scoped documents for Acme Corp.
- No scope cross-contamination.

---

## 2. Comment CRUD & Threading

### COM-001: Create Comment on Task

**Objective:** Verify creating a comment on a task entity.

**Actors:** Aiden O'Brien (Member)

**Preconditions:**
- A task exists in a project where Aiden is a member

**Key Steps:**
1. Aiden posts a comment on the task with body "Initial review notes look good", visibility=INTERNAL.

**Key Validations:**
- Comment created with entityType="TASK", entityId=task ID, projectId set, authorMemberId=Aiden's ID.
- visibility=INTERNAL, source=INTERNAL, parentId=null.
- createdAt and updatedAt are set.
- Notification of type COMMENT_ADDED sent to relevant project members (excluding Aiden).

---

### COM-002: Create Comment on Document

**Objective:** Verify creating a comment on a document entity.

**Actors:** Aiden O'Brien (Member)

**Preconditions:**
- A project-scoped document exists with status=UPLOADED

**Key Steps:**
1. Aiden posts a comment on the document with body "Please review section 3", visibility=INTERNAL.

**Key Validations:**
- Comment created with entityType="DOCUMENT", entityId=document ID.
- Comment appears in the document's comment thread.

---

### COM-003: Edit Comment

**Objective:** Verify that the comment author can edit their own comment body.

**Actors:** Aiden O'Brien (Member)

**Preconditions:**
- Aiden has an existing comment

**Key Steps:**
1. Aiden updates the comment body to "Updated: Initial review notes look good, pending section 4".

**Key Validations:**
- Comment body is updated.
- updatedAt changes; createdAt remains unchanged.
- Other fields (visibility, entityType, entityId) remain unchanged.

---

### COM-004: Delete Comment

**Objective:** Verify that the comment author (or Admin) can delete a comment.

**Actors:** Aiden O'Brien (Member)

**Preconditions:**
- Aiden has an existing comment

**Key Steps:**
1. Aiden deletes the comment.

**Key Validations:**
- Comment is removed from the listing.
- Audit event logged for comment deletion.

---

### COM-005: Reply Threading (parentId)

**Objective:** Verify that comments can be threaded via parentId.

**Actors:** Aiden O'Brien (Member), Yuki Tanaka (Member)

**Preconditions:**
- A top-level comment exists on a task (created by Aiden)

**Key Steps:**
1. Yuki posts a reply to Aiden's comment by setting parentId to Aiden's comment ID.
2. Aiden posts a second reply to the same parent.

**Key Validations:**
- Both replies have parentId set to the original comment's ID.
- When listing comments for the task, threading structure is preserved (parent + children).
- Top-level comment has parentId=null.

---

### COM-006: INTERNAL Visibility Comment

**Objective:** Verify that INTERNAL comments are visible only to org members, not portal contacts.

**Actors:** Aiden O'Brien (Member)

**Preconditions:**
- A task exists on a project linked to Acme Corp
- alice.porter@acmecorp.com is a portal contact

**Key Steps:**
1. Aiden creates a comment with visibility=INTERNAL on the task.

**Key Validations:**
- Comment is visible to org members via internal API.
- Comment is NOT visible via portal comment endpoints.

---

### COM-007: SHARED Visibility Comment

**Objective:** Verify that SHARED comments are visible to both org members and portal contacts.

**Actors:** Aiden O'Brien (Member), alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A task exists on a project linked to Acme Corp

**Key Steps:**
1. Aiden creates a comment with visibility=SHARED on the task.
2. Alice accesses the task comments via the portal.

**Key Validations:**
- Comment is visible to org members.
- Comment is visible to Alice via portal comment endpoint (PortalCommentView).
- Comment body and author information are present in the portal view.

---

### COM-008: Portal Contact Posts Comment

**Objective:** Verify that a portal contact can post a comment via the portal API.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A task exists on a project linked to Acme Corp
- Alice has an active portal session (JWT)

**Key Steps:**
1. Alice posts a comment via the portal API on the task.

**Key Validations:**
- Comment created with source="PORTAL" (or equivalent).
- Comment visibility defaults to SHARED (portal comments are always shared).
- Comment is visible to org members via internal API.
- Notification sent to relevant org members about the portal comment.

---

## 3. Notification Triggers

### NOTIF-001: Task Assigned Notification

**Objective:** Verify that assigning a task to a member triggers a TASK_ASSIGNED notification.

**Actors:** Priya Sharma (Admin), Yuki Tanaka (Member)

**Preconditions:**
- A task exists unassigned in a project

**Key Steps:**
1. Priya assigns the task to Yuki.

**Key Validations:**
- Notification created for Yuki with type=TASK_ASSIGNED.
- Notification title references the task name.
- referenceEntityType and referenceEntityId point to the task.
- isRead=false.

---

### NOTIF-002: Comment Posted Notification

**Objective:** Verify that posting a comment triggers a COMMENT_ADDED notification to project members.

**Actors:** Aiden O'Brien (Member), Yuki Tanaka (Member)

**Preconditions:**
- Both Aiden and Yuki are members of the same project
- A task exists in the project

**Key Steps:**
1. Aiden posts a comment on the task.

**Key Validations:**
- Notification of type COMMENT_ADDED created for Yuki (and other project members).
- Notification NOT created for Aiden (author excluded).

---

### NOTIF-003: Invoice Sent Notification

**Objective:** Verify that sending an invoice triggers an INVOICE_SENT notification.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An invoice exists in APPROVED status

**Key Steps:**
1. Priya sends the invoice (transitions to SENT).

**Key Validations:**
- Notification of type INVOICE_SENT created for relevant recipients.
- Audit event logged for INVOICE_SENT.

---

### NOTIF-004: Document Shared Notification

**Objective:** Verify that sharing a document triggers a DOCUMENT_SHARED notification.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A document exists with visibility=INTERNAL

**Key Steps:**
1. Priya toggles visibility to SHARED.

**Key Validations:**
- Notification of type DOCUMENT_SHARED created for relevant recipients.

---

### NOTIF-005: Acceptance Request Completed Notification

**Objective:** Verify that completing an acceptance request triggers an ACCEPTANCE_COMPLETED notification.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- An acceptance request exists in PENDING status for Alice

**Key Steps:**
1. Alice accepts the request via the portal.

**Key Validations:**
- Notification of type ACCEPTANCE_COMPLETED created for the org member who sent the request.

---

### NOTIF-006: Proposal Sent Notification

**Objective:** Verify that sending a proposal triggers a PROPOSAL_SENT notification.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A proposal exists in DRAFT status

**Key Steps:**
1. Priya sends the proposal.

**Key Validations:**
- Notification of type PROPOSAL_SENT created for relevant recipients.

---

### NOTIF-007: Budget Alert Notification

**Objective:** Verify that breaching a budget threshold triggers a BUDGET_ALERT notification.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- A project has a budget configured with a threshold (e.g., 80%)
- Current usage is below 80%

**Key Steps:**
1. Yuki logs time that pushes usage past the 80% threshold.

**Key Validations:**
- Notification of type BUDGET_ALERT created for project members/admins.
- Notification body references the threshold percentage and project name.

---

### NOTIF-008: Checklist Completed (Customer Onboarding)

**Objective:** Verify that completing all onboarding checklist items triggers appropriate notifications.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A customer is in ONBOARDING status with an incomplete checklist

**Key Steps:**
1. Priya completes all remaining checklist items.

**Key Validations:**
- Customer auto-transitions to ACTIVE.
- Audit events logged for checklist completion and lifecycle transition.

---

### NOTIF-009: Information Request Sent Notification

**Objective:** Verify that sending an information request generates a notification.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An InformationRequest exists in DRAFT status with items

**Key Steps:**
1. Priya sends the information request (DRAFT to SENT).

**Key Validations:**
- Audit event logged for request sent.
- sentAt timestamp is set on the InformationRequest.

---

### NOTIF-010: Time Reminder Notification

**Objective:** Verify that the time reminder system generates TIME_REMINDER notifications.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Org settings: timeReminderEnabled=true, timeReminderDays includes today's day, timeReminderMinMinutes=240 (4 hours)
- Yuki has logged less than 4 hours today

**Key Steps:**
1. Time reminder job fires at the configured timeReminderTime.

**Key Validations:**
- Notification of type TIME_REMINDER created for Yuki.
- Members who have already logged sufficient time do NOT receive the notification.

---

## 4. Notification Management

### NOTIF-011: List Notifications

**Objective:** Verify that a member can list their notifications with correct ordering.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has multiple notifications (mix of read and unread)

**Key Steps:**
1. Yuki calls the notification list endpoint.

**Key Validations:**
- Response contains Yuki's notifications only (not other members').
- Notifications ordered by createdAt descending (newest first).
- Each notification includes: id, type, title, body, isRead, createdAt, referenceEntityType, referenceEntityId.

---

### NOTIF-012: Mark Single Notification Read

**Objective:** Verify marking a single notification as read.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has an unread notification

**Key Steps:**
1. Yuki marks the notification as read.

**Key Validations:**
- Notification isRead changes from false to true.
- Unread count decrements by 1.

---

### NOTIF-013: Mark All Notifications Read

**Objective:** Verify the bulk "mark all read" operation.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has 5 unread notifications

**Key Steps:**
1. Yuki calls the "mark all read" endpoint.

**Key Validations:**
- All 5 notifications are now isRead=true.
- Unread count is 0.

---

### NOTIF-014: Delete Notification

**Objective:** Verify that a member can delete their own notification.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has an existing notification

**Key Steps:**
1. Yuki deletes the notification.

**Key Validations:**
- Notification no longer appears in Yuki's notification list.
- Total notification count decrements.

---

### NOTIF-015: Unread Count Badge

**Objective:** Verify the unread count endpoint for the notification bell badge.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has 3 unread and 2 read notifications

**Key Steps:**
1. Yuki calls the unread count endpoint.

**Key Validations:**
- Response returns count=3.
- After marking 1 as read, count returns 2.

---

## 5. Notification Preferences

### NOTIF-016: Toggle In-App Notification Preference

**Objective:** Verify that a member can disable in-app notifications for a specific type.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has default preferences (in-app enabled for all types)

**Key Steps:**
1. Yuki sets inAppEnabled=false for notificationType=COMMENT_ADDED.
2. Another member posts a comment on a task where Yuki is a project member.

**Key Validations:**
- After step 1: preference record exists with inAppEnabled=false for COMMENT_ADDED.
- After step 2: NO in-app notification created for Yuki for COMMENT_ADDED.
- Other notification types still fire normally for Yuki.

---

### NOTIF-017: Toggle Email Notification Preference

**Objective:** Verify that a member can toggle email notifications independently of in-app.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has default preferences

**Key Steps:**
1. Yuki sets emailEnabled=false for notificationType=TASK_ASSIGNED.

**Key Validations:**
- Preference updated: emailEnabled=false, inAppEnabled remains true.
- Task assignment still creates in-app notification but skips email delivery.

---

### NOTIF-018: Default Preferences for New Notification Types

**Objective:** Verify that notification types without explicit preferences default to enabled.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has NOT set any preference for BUDGET_ALERT

**Key Steps:**
1. A budget threshold event fires for a project where Yuki is a member.

**Key Validations:**
- In-app notification created (default=enabled).
- No preference record needs to exist — absence means "enabled".

---

### NOTIF-019: List All Preferences with Defaults

**Objective:** Verify that the preference listing endpoint returns all known notification types with current settings.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has explicitly set 2 preferences, remaining types have no explicit record

**Key Steps:**
1. Yuki calls the preferences list endpoint.

**Key Validations:**
- Response includes all known notification types (TASK_ASSIGNED, COMMENT_ADDED, DOCUMENT_SHARED, BUDGET_ALERT, INVOICE_APPROVED, INVOICE_SENT, INVOICE_PAID, INVOICE_VOIDED, DOCUMENT_GENERATED, ACCEPTANCE_COMPLETED, TIME_REMINDER, PROPOSAL_SENT, PROPOSAL_ACCEPTED, PROPOSAL_DECLINED, PROPOSAL_EXPIRED).
- Explicitly set preferences show their configured values.
- Types without explicit preferences show defaults (both enabled).

---

## 6. Email Delivery

### EMAIL-001: Email Delivery Log Entry

**Objective:** Verify that sending a notification via email creates a delivery log record.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has emailEnabled=true for the relevant notification type
- Email integration is configured

**Key Steps:**
1. A notification-triggering event fires (e.g., TASK_ASSIGNED).

**Key Validations:**
- Email delivery log entry created with: recipientEmail, notificationType, status (SENT/DELIVERED/FAILED), sentAt.
- Log entry references the notification ID.

---

### EMAIL-002: Email Delivery Stats

**Objective:** Verify that email delivery statistics are aggregated correctly.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- Multiple emails have been sent with mixed delivery statuses

**Key Steps:**
1. Priya queries the email delivery stats endpoint.

**Key Validations:**
- Stats include: totalSent, totalDelivered, totalFailed, totalBounced.
- Counts match the actual delivery log records.

---

### EMAIL-003: Test Email Send

**Objective:** Verify that an admin can send a test email to validate configuration.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- Email integration is configured

**Key Steps:**
1. Priya triggers a test email send to a specified email address.

**Key Validations:**
- Test email is dispatched.
- Delivery log entry created with a test marker.

---

### EMAIL-004: Unsubscribe via Email Link

**Objective:** Verify that the unsubscribe mechanism in emails correctly updates notification preferences.

**Actors:** Yuki Tanaka (Member)

**Preconditions:**
- Yuki has emailEnabled=true for COMMENT_ADDED

**Key Steps:**
1. Yuki clicks the unsubscribe link in a COMMENT_ADDED email notification.

**Key Validations:**
- NotificationPreference for COMMENT_ADDED updated: emailEnabled=false.
- inAppEnabled remains unchanged.
- Subsequent COMMENT_ADDED events do not send email to Yuki.

---

## 7. Portal Document Access

### DOC-U-009: Portal Contact Sees SHARED Documents Only

**Objective:** Verify that portal contacts can only see documents with visibility=SHARED.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- Project linked to Acme Corp has 3 documents: 2 INTERNAL, 1 SHARED
- Alice has an active portal session

**Key Steps:**
1. Alice lists documents for the project via the portal API.

**Key Validations:**
- Response contains exactly 1 document (the SHARED one).
- INTERNAL documents are NOT present in the response.
- PortalDocumentView includes: fileName, contentType, size, uploadedAt.

---

### DOC-U-010: Portal Contact Downloads SHARED Document

**Objective:** Verify that a portal contact can download a SHARED document.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A SHARED document exists with status=UPLOADED

**Key Steps:**
1. Alice requests a download URL for the SHARED document via the portal API.

**Key Validations:**
- Presigned download URL is returned.
- URL is valid and provides access to the file.

---

### DOC-U-011: Portal Contact Cannot See INTERNAL Documents

**Objective:** Explicitly verify that INTERNAL documents are hidden from portal access.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- An INTERNAL document exists on a project linked to Acme Corp

**Key Steps:**
1. Alice attempts to access the INTERNAL document by ID via the portal API.

**Key Validations:**
- Request returns 404 or empty result (document not found in portal scope).
- No presigned URL is generated for the INTERNAL document.

---

### DOC-U-012: Portal Contact Cannot Access Other Customer's Documents

**Objective:** Verify that a portal contact cannot access documents from a different customer's project.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact for Acme Corp)

**Preconditions:**
- A SHARED document exists on a project linked to Dunbar & Associates (not Acme Corp)

**Key Steps:**
1. Alice attempts to list documents for the Dunbar project via the portal API.

**Key Validations:**
- Request returns 403 or empty result.
- Alice cannot see or download Dunbar's documents.
