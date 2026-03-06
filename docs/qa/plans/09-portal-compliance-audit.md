# Layer 2: Portal, Compliance & Audit (Scenario Outlines)

**Product:** DocTeams — Multi-tenant B2B SaaS Practice Management Platform
**Date:** 2026-03-06
**Prepared by:** QA Team

---

## How to Read This Document

Each scenario outline contains: title, objective, preconditions, key steps, and key validations. Actors are drawn from the [test team](00-overview.md#test-team) and [test customers](00-overview.md#test-customers).

---

## 1. Portal Authentication

### PORTAL-001: Request Magic Link (Happy Path)

**Objective:** Verify that a portal contact can request a magic link for authentication.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- Alice exists as an ACTIVE portal contact for Acme Corp
- Email delivery is configured

**Key Steps:**
1. Alice navigates to the portal login page.
2. Alice enters her email address and requests a magic link.

**Key Validations:**
- MagicLinkToken record created in the database with a unique token, contactId=Alice's ID, and expiration timestamp.
- Email dispatched to alice.porter@acmecorp.com containing the magic link URL.
- No information leaked about whether the email is registered (generic "link sent" message).

---

### PORTAL-002: Exchange Magic Link Token for JWT

**Objective:** Verify that clicking a valid magic link exchanges the token for a portal JWT session.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A valid, unexpired MagicLinkToken exists for Alice

**Key Steps:**
1. Alice clicks the magic link URL (containing the token).
2. System validates the token and issues a portal JWT.

**Key Validations:**
- Token is consumed (cannot be reused).
- Portal JWT contains: contactId, customerId, orgId, role (PRIMARY).
- Alice is redirected to the portal dashboard.
- Audit event logged for portal login with IP address and user agent.

---

### PORTAL-003: Expired Magic Link Rejected

**Objective:** Verify that an expired magic link is rejected.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A MagicLinkToken exists for Alice that has passed its expiration timestamp

**Key Steps:**
1. Alice clicks the expired magic link.

**Key Validations:**
- System returns an error indicating the link has expired.
- No JWT is issued.
- Alice is prompted to request a new magic link.

---

### PORTAL-004: Invalid Token Rejected

**Objective:** Verify that a fabricated or tampered token is rejected.

**Actors:** (anonymous attacker)

**Preconditions:**
- None

**Key Steps:**
1. An unknown party submits a random/fabricated token to the exchange endpoint.

**Key Validations:**
- System returns 401 or appropriate error (PortalAuthException).
- No JWT is issued.
- No information leaked about valid tokens.

---

### PORTAL-005: Portal Branding Applied

**Objective:** Verify that org branding (logo, brand color) is applied to the portal UI.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- OrgSettings has logoS3Key set and brandColor="#1E40AF"
- Alice has an active portal session

**Key Steps:**
1. Alice views the portal dashboard.

**Key Validations:**
- Org logo is displayed (fetched via logoS3Key presigned URL).
- Brand color (#1E40AF) is applied to portal UI elements.
- documentFooterText appears in generated/rendered documents.

---

## 2. Portal Project & Task View

### PORTAL-006: List Projects (Portal Contact)

**Objective:** Verify that a portal contact can list projects linked to their customer.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- Acme Corp has 2 linked projects: "Annual Audit 2026" and "Tax Advisory"
- Alice has an active portal session

**Key Steps:**
1. Alice lists projects via the portal API.

**Key Validations:**
- Response contains exactly 2 projects (PortalProjectSummaryView).
- Each summary includes: projectId, name, status.
- No projects from other customers (Dunbar) are visible.

---

### PORTAL-007: View Project Detail (Portal Contact)

**Objective:** Verify that a portal contact can view a specific project's detail.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- "Annual Audit 2026" is linked to Acme Corp

**Key Steps:**
1. Alice views the project detail for "Annual Audit 2026" via portal API.

**Key Validations:**
- Response includes PortalProjectView with: name, status, description.
- Sensitive internal details (budget, cost rates, internal notes) are NOT exposed.

---

### PORTAL-008: List Tasks (Portal Contact)

**Objective:** Verify that a portal contact can see tasks in a project.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- "Annual Audit 2026" has 3 tasks with various statuses

**Key Steps:**
1. Alice lists tasks for "Annual Audit 2026" via the portal.

**Key Validations:**
- Tasks are listed with limited fields (name, status, due date).
- Internal fields (assignee details, time logged, billable flag) are NOT exposed.

---

### PORTAL-009: Portal Contact Cannot Access Other Customer's Project

**Objective:** Verify cross-customer isolation in the portal.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact for Acme Corp)

**Preconditions:**
- Dunbar & Associates has a project "Dunbar Annual Review"

**Key Steps:**
1. Alice attempts to view "Dunbar Annual Review" project detail via portal API.

**Key Validations:**
- Request returns 403 or 404.
- No data from Dunbar's project is exposed.

---

### PORTAL-010: Suspended Portal Contact Blocked

**Objective:** Verify that a SUSPENDED portal contact cannot access the portal.

**Actors:** alice.porter@acmecorp.com (after suspension)

**Preconditions:**
- Alice's portal contact status has been changed to SUSPENDED

**Key Steps:**
1. Alice attempts to request a magic link.
2. (If a valid token exists) Alice attempts to use an existing token.

**Key Validations:**
- Magic link request silently succeeds (no leak) but no token is created.
- Token exchange is rejected for suspended contacts.
- No portal access is granted.

---

## 3. Portal Invoice & Payment

### PORTAL-011: BILLING Contact Lists Invoices

**Objective:** Verify that a BILLING portal contact can view invoices for their customer.

**Actors:** ben.finance@acmecorp.com (BILLING portal contact)

**Preconditions:**
- Acme Corp has 3 invoices: SENT, PAID, VOIDED
- Ben has an active portal session

**Key Steps:**
1. Ben lists invoices via the portal API.

**Key Validations:**
- Response contains all 3 invoices with: invoiceNumber, status, totalAmount, issuedDate.
- Line item detail available for each invoice.

---

### PORTAL-012: BILLING Contact Views Invoice Detail

**Objective:** Verify invoice detail view in the portal.

**Actors:** ben.finance@acmecorp.com (BILLING portal contact)

**Preconditions:**
- A SENT invoice exists for Acme Corp with 2 line items

**Key Steps:**
1. Ben views the invoice detail.

**Key Validations:**
- Invoice header: number, status, dates, currency, tax info.
- Line items: description, quantity, rate, amount.
- Totals: subtotal, tax, total.
- Payment status visible.

---

### PORTAL-013: BILLING Contact Downloads Invoice PDF

**Objective:** Verify that a BILLING contact can download the invoice PDF.

**Actors:** ben.finance@acmecorp.com (BILLING portal contact)

**Preconditions:**
- A SENT invoice with a generated PDF exists

**Key Steps:**
1. Ben requests the invoice PDF download via the portal.

**Key Validations:**
- PDF is downloadable with correct content.
- PDF contains org branding (logo, brand color, footer text).

---

### PORTAL-014: Non-BILLING Contact Cannot View Invoices

**Objective:** Verify that PRIMARY and GENERAL contacts cannot access invoices.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- Alice is a PRIMARY contact (not BILLING)
- Invoices exist for Acme Corp

**Key Steps:**
1. Alice attempts to list invoices via the portal API.

**Key Validations:**
- Request returns 403 or empty list (invoice access restricted to BILLING role).
- Alice cannot view invoice detail or download PDFs.

---

### PORTAL-015: Invoice Payment Status Visible

**Objective:** Verify that the payment status of invoices is accurately reflected in the portal.

**Actors:** ben.finance@acmecorp.com (BILLING portal contact)

**Preconditions:**
- Invoices exist in SENT, PAID, and OVERDUE statuses

**Key Steps:**
1. Ben lists invoices and checks payment status for each.

**Key Validations:**
- SENT invoice shows payment outstanding.
- PAID invoice shows payment completed with payment date.
- Status badges/labels correctly reflect each state.

---

## 4. Portal Information Requests

### PORTAL-016: View Information Request (Portal Contact)

**Objective:** Verify that a portal contact can view an information request sent to them.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- An InformationRequest exists in SENT status, addressed to Alice, with 3 RequestItems

**Key Steps:**
1. Alice views the information request via the portal.

**Key Validations:**
- Request displayed with: requestNumber, status=SENT, list of items.
- Each item shows: name, description, responseType (FILE_UPLOAD or TEXT_RESPONSE), status=PENDING, required flag.

---

### PORTAL-017: Submit File Upload Item

**Objective:** Verify that a portal contact can submit a file upload response for a request item.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A RequestItem exists with responseType=FILE_UPLOAD, status=PENDING

**Key Steps:**
1. Alice uploads a file for the item.

**Key Validations:**
- Item status transitions from PENDING to SUBMITTED.
- documentId is set on the item.
- submittedAt timestamp is recorded.
- InformationRequest transitions from SENT to IN_PROGRESS (first submission triggers this).

---

### PORTAL-018: Submit Text Response Item

**Objective:** Verify that a portal contact can submit a text response.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A RequestItem exists with responseType=TEXT_RESPONSE, status=PENDING

**Key Steps:**
1. Alice submits text response "Our fiscal year ends March 31".

**Key Validations:**
- Item status transitions to SUBMITTED.
- textResponse is set to the submitted text.
- submittedAt timestamp is recorded.

---

### PORTAL-019: Resubmit Rejected Item

**Objective:** Verify that a portal contact can resubmit a previously rejected item.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A RequestItem exists with status=REJECTED (previously submitted but rejected by the firm)
- rejectionReason is set (e.g., "Document is illegible, please rescan")

**Key Steps:**
1. Alice views the rejected item and sees the rejection reason.
2. Alice uploads a new file for the item.

**Key Validations:**
- Item status transitions from REJECTED to SUBMITTED.
- Previous documentId/textResponse cleared on rejection; new submission replaces them.
- rejectionReason is cleared after resubmission.
- submittedAt updated to new submission time.

---

### PORTAL-020: Firm Accepts Item

**Objective:** Verify that a firm member can accept a submitted item.

**Actors:** Priya Sharma (Admin), alice.porter@acmecorp.com

**Preconditions:**
- A RequestItem exists with status=SUBMITTED

**Key Steps:**
1. Priya reviews the submitted item and accepts it.

**Key Validations:**
- Item status transitions from SUBMITTED to ACCEPTED.
- reviewedBy set to Priya's member ID.
- reviewedAt timestamp recorded.

---

### PORTAL-021: All Items Accepted Completes Request

**Objective:** Verify that accepting all required items auto-completes the InformationRequest.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An InformationRequest with 3 items: 2 required, 1 optional
- 2 required items in SUBMITTED status, 1 optional item in PENDING

**Key Steps:**
1. Priya accepts both required items.

**Key Validations:**
- InformationRequest status transitions from IN_PROGRESS to COMPLETED.
- completedAt timestamp is set.
- Optional PENDING item does not block completion.
- Audit event logged for request completion.

---

## 5. Data Subject Requests

### DSR-001: Create Data Subject Request

**Objective:** Verify creating a new data subject request (e.g., GDPR data access request).

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- Acme Corp exists as an ACTIVE customer

**Key Steps:**
1. Thandi creates a DataSubjectRequest for Acme Corp with requestType="ACCESS", description="Full data export requested by client", deadline=today+30.

**Key Validations:**
- Request created with status=RECEIVED, requestedBy=Thandi's ID, requestedAt set.
- deadline set to today+30.
- Audit event logged for data request creation.

---

### DSR-002: Start Processing and Generate Export

**Objective:** Verify transitioning a request to IN_PROGRESS and generating the data export.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A DataSubjectRequest exists in RECEIVED status

**Key Steps:**
1. Priya transitions the request to IN_PROGRESS.
2. Priya triggers the data export generation.

**Key Validations:**
- Status transitions from RECEIVED to IN_PROGRESS.
- Export file generated and uploaded to S3.
- exportFileKey is set on the request.
- Audit event logged for status transition.

---

### DSR-003: Download Data Export

**Objective:** Verify downloading the generated data export.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A DataSubjectRequest with exportFileKey set (export generated)

**Key Steps:**
1. Priya requests a presigned download URL for the export.

**Key Validations:**
- Presigned URL returned for the export file.
- File is downloadable and contains the customer's data.

---

### DSR-004: Execute Deletion (Anonymization)

**Objective:** Verify executing a data deletion/anonymization request.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- A DataSubjectRequest exists in IN_PROGRESS status with requestType="DELETION"

**Key Steps:**
1. Thandi executes the deletion request.

**Key Validations:**
- Customer PII fields anonymized.
- Portal contacts anonymized (email replaced with "anon-{id}@anonymized.invalid", displayName replaced).
- Comments redacted (body replaced with anonymization text).
- Request status transitions to COMPLETED, completedAt and completedBy set.
- Audit event logged for data deletion execution.

---

### DSR-005: Check Deadline Compliance

**Objective:** Verify that overdue data requests are flagged.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A DataSubjectRequest exists with deadline in the past, status still IN_PROGRESS

**Key Steps:**
1. Priya views the data requests list.

**Key Validations:**
- Overdue requests are identifiable (deadline < today and status not COMPLETED/REJECTED).
- System provides visibility into compliance risk.

---

### DSR-006: Audit Trail for Data Request Lifecycle

**Objective:** Verify that every data request lifecycle action is fully audited.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- A DataSubjectRequest has gone through RECEIVED -> IN_PROGRESS -> COMPLETED

**Key Steps:**
1. Thandi queries audit events filtered by entityType="DATA_SUBJECT_REQUEST" and entityId=request ID.

**Key Validations:**
- Audit events exist for: creation, status transitions (each), export generation, deletion execution.
- Each event includes actorId, actorType, occurredAt.
- Events are immutable (append-only).

---

## 6. Retention Policies

### RET-P-001: Create Retention Policy

**Objective:** Verify creating a data retention policy.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- No existing retention policy for the specified recordType

**Key Steps:**
1. Thandi creates a RetentionPolicy with recordType="AUDIT_EVENT", retentionDays=2555 (7 years), triggerEvent="CREATION", action="DELETE".

**Key Validations:**
- Policy created with active=true.
- Fields match input: recordType, retentionDays, triggerEvent, action.
- Audit event logged for policy creation.

---

### RET-P-002: Check Retention Compliance

**Objective:** Verify that the system can identify records that exceed their retention period.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A retention policy exists for AUDIT_EVENT with retentionDays=365
- Audit events exist older than 365 days

**Key Steps:**
1. Priya runs the retention compliance check.

**Key Validations:**
- System identifies records past their retention deadline.
- Report shows: recordType, count of expired records, oldest record date.

---

### RET-P-003: Execute Retention Purge

**Objective:** Verify that expired records are purged according to the retention policy action.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- Retention policy with action="DELETE" and records past retention period

**Key Steps:**
1. Thandi triggers retention purge.

**Key Validations:**
- Records older than retentionDays are deleted (or anonymized, depending on action).
- Records within retention period are untouched.
- Audit event logged for purge execution with count of affected records.

---

### RET-P-004: Deactivate Retention Policy

**Objective:** Verify that a deactivated policy stops enforcement.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- An active retention policy exists

**Key Steps:**
1. Thandi deactivates the policy.

**Key Validations:**
- Policy active=false, updatedAt changed.
- Subsequent compliance checks and purges skip this policy.

---

## 7. Audit Event Coverage

### AUDIT-001: Customer Create Audit Event

**Objective:** Verify that creating a customer generates an audit event.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- Lerato has Admin or higher role (or action is performed by an Admin)

**Key Steps:**
1. Create a new customer "TestAudit Corp".

**Key Validations:**
- AuditEvent created with: eventType containing "CREATED", entityType="CUSTOMER", entityId=new customer ID, actorId set, actorType="MEMBER", source set.
- occurredAt timestamp is set.

---

### AUDIT-002: Project Create Audit Event

**Objective:** Verify that creating a project generates an audit event.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An ACTIVE customer exists

**Key Steps:**
1. Priya creates a project for the customer.

**Key Validations:**
- AuditEvent with entityType="PROJECT", eventType containing "CREATED".
- details JSONB includes relevant context (project name, customer reference).

---

### AUDIT-003: Task Status Transition Audit Event

**Objective:** Verify that task status transitions generate audit events.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- A task exists in TODO status

**Key Steps:**
1. Lerato transitions the task from TODO to IN_PROGRESS.

**Key Validations:**
- AuditEvent with entityType="TASK", eventType containing status transition info.
- details JSONB includes previous and new status.

---

### AUDIT-004: Invoice Approve Audit Event

**Objective:** Verify that approving an invoice generates an audit event.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An invoice exists in DRAFT status

**Key Steps:**
1. Priya approves the invoice (DRAFT to APPROVED).

**Key Validations:**
- AuditEvent with entityType="INVOICE", eventType containing "APPROVED".
- details includes invoice number and amount.

---

### AUDIT-005: Document Generate Audit Event

**Objective:** Verify that generating a document (PDF) creates an audit event.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A document template exists, context data available

**Key Steps:**
1. Priya generates a PDF document from a template.

**Key Validations:**
- AuditEvent with entityType="GENERATED_DOCUMENT" or "DOCUMENT", eventType containing "GENERATED".
- details includes template reference and context entity.

---

### AUDIT-006: Acceptance Accept Audit Event

**Objective:** Verify that a portal contact accepting an acceptance request generates an audit event.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- An acceptance request exists in PENDING status

**Key Steps:**
1. Alice accepts the request via the portal.

**Key Validations:**
- AuditEvent with eventType containing "ACCEPTED".
- actorType="PORTAL_CONTACT" or equivalent.
- ipAddress and userAgent captured from the portal request.

---

### AUDIT-007: Login Audit Event

**Objective:** Verify that portal login (magic link exchange) generates an audit event.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- Valid magic link token exists

**Key Steps:**
1. Alice exchanges the magic link for a portal JWT.

**Key Validations:**
- AuditEvent with eventType containing "LOGIN" or "PORTAL_AUTH".
- ipAddress and userAgent captured.
- actorId references the portal contact.

---

### AUDIT-008: Settings Change Audit Event

**Objective:** Verify that changing org settings generates an audit event.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- OrgSettings exist with defaultCurrency="ZAR"

**Key Steps:**
1. Priya updates the default currency to "USD".

**Key Validations:**
- AuditEvent with entityType="ORG_SETTINGS" or "SETTINGS", eventType containing "UPDATED".
- details JSONB includes the changed field(s) and old/new values.

---

## 8. Audit Immutability

### AUDIT-009: Audit Events Cannot Be Updated

**Objective:** Verify that audit events are immutable and cannot be updated via any mechanism.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An AuditEvent record exists in the database

**Key Steps:**
1. Attempt to update the audit event record (via direct SQL: `UPDATE audit_events SET event_type='MODIFIED' WHERE id=...`).

**Key Validations:**
- Database trigger rejects the UPDATE operation.
- AuditEvent entity is annotated with @Immutable (Hibernate skips dirty-checking).
- No API endpoint exists for updating audit events.

---

### AUDIT-010: Audit Events Cannot Be Deleted

**Objective:** Verify that audit events cannot be deleted.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An AuditEvent record exists in the database

**Key Steps:**
1. Attempt to delete the audit event record (via direct SQL: `DELETE FROM audit_events WHERE id=...`).

**Key Validations:**
- Database trigger rejects the DELETE operation.
- No API endpoint exists for deleting audit events.
- Audit events persist for the lifetime defined by retention policies (separate purge mechanism only).
