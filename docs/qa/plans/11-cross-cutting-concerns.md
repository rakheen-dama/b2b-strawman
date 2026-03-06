# Layer 3: Cross-Cutting Concerns

**Product:** DocTeams — Multi-tenant B2B SaaS Practice Management Platform
**Date:** 2026-03-06
**Prepared by:** QA Team

---

## How to Read This Document

This file covers quality concerns that span multiple domains. Each scenario outline contains: title, objective, preconditions, key steps, and key validations. The RBAC matrix provides a single reference for role-based access across all features.

---

## RBAC Matrix

| Operation | Owner | Admin | Member | Portal Contact |
|-----------|-------|-------|--------|----------------|
| **Customers** | | | | |
| Create customer | Allowed | Allowed | Blocked | Blocked |
| Update customer | Allowed | Allowed | Blocked | Blocked |
| Delete customer | Allowed | Blocked | Blocked | Blocked |
| Transition lifecycle | Allowed | Allowed | Blocked | Blocked |
| **Projects** | | | | |
| Create project | Allowed | Allowed | Blocked | Blocked |
| Update project | Allowed | Allowed | Blocked | Blocked |
| Delete project | Allowed | Blocked | Blocked | Blocked |
| View project | Allowed | Allowed | Allowed (if member) | Limited (own customer only) |
| **Tasks** | | | | |
| Create task | Allowed | Allowed | Allowed (if project member) | Blocked |
| Update task | Allowed | Allowed | Allowed (if project member) | Blocked |
| Assign task | Allowed | Allowed | Allowed (self-assign only) | Blocked |
| Transition status | Allowed | Allowed | Allowed (if assignee/member) | Blocked |
| View tasks | Allowed | Allowed | Allowed (if project member) | Limited (own customer projects) |
| **Time Entries** | | | | |
| Log time | Allowed | Allowed | Allowed (own entries) | Blocked |
| Edit time entry | Allowed | Allowed | Allowed (own entries) | Blocked |
| Delete time entry | Allowed | Allowed | Allowed (own entries) | Blocked |
| View all time entries | Allowed | Allowed | Limited (own + project) | Blocked |
| **Invoices** | | | | |
| Create invoice | Allowed | Allowed | Blocked | Blocked |
| Approve invoice | Allowed | Allowed | Blocked | Blocked |
| Send invoice | Allowed | Allowed | Blocked | Blocked |
| Void invoice | Allowed | Allowed | Blocked | Blocked |
| Delete invoice | Allowed | Blocked | Blocked | Blocked |
| View invoice | Allowed | Allowed | Blocked | Limited (BILLING role only) |
| **Rates & Budgets** | | | | |
| Manage billing rates | Allowed | Allowed | Blocked | Blocked |
| View billing rates | Allowed | Allowed | Allowed | Blocked |
| View cost rates | Allowed | Allowed | Blocked | Blocked |
| Configure budget | Allowed | Allowed | Blocked | Blocked |
| View budget status | Allowed | Allowed | Allowed (if project member) | Blocked |
| **Documents** | | | | |
| Upload document | Allowed | Allowed | Allowed (if project member) | Blocked |
| Delete document | Allowed | Allowed | Blocked | Blocked |
| Toggle visibility | Allowed | Allowed | Blocked | Blocked |
| Download document | Allowed | Allowed | Allowed (if project member) | Limited (SHARED only) |
| **Proposals** | | | | |
| Create proposal | Allowed | Allowed | Blocked | Blocked |
| Send proposal | Allowed | Allowed | Blocked | Blocked |
| Accept proposal | Blocked | Blocked | Blocked | Allowed (PRIMARY only) |
| **Templates** | | | | |
| Manage project templates | Allowed | Allowed | Blocked | Blocked |
| Manage document templates | Allowed | Allowed | Blocked | Blocked |
| Manage checklist templates | Allowed | Allowed | Blocked | Blocked |
| **Settings** | | | | |
| Modify org settings | Allowed | Allowed | Blocked | Blocked |
| Read org settings | Allowed | Allowed | Allowed | Blocked |
| Configure compliance | Allowed | Allowed | Blocked | Blocked |
| **Audit & Compliance** | | | | |
| View audit log | Allowed | Allowed | Blocked | Blocked |
| Create data subject request | Allowed | Allowed | Blocked | Blocked |
| Execute data deletion | Allowed | Blocked | Blocked | Blocked |
| Manage retention policies | Allowed | Blocked | Blocked | Blocked |
| **Profitability Reports** | | | | |
| View profitability reports | Allowed | Allowed | Blocked | Blocked |
| View utilization reports | Allowed | Allowed | Blocked | Blocked |
| **Comments** | | | | |
| Create comment | Allowed | Allowed | Allowed (if project member) | Allowed (SHARED only) |
| Edit comment | Allowed | Allowed (any) | Allowed (own only) | Blocked |
| Delete comment | Allowed | Allowed (any) | Allowed (own only) | Blocked |
| **Notifications** | | | | |
| View notifications | Allowed | Allowed | Allowed (own only) | Blocked |
| Manage preferences | Allowed | Allowed | Allowed (own only) | Blocked |

---

## 1. RBAC Enforcement

### RBAC-001: Member Cannot Create Customer

**Objective:** Verify that a Member role cannot create a customer.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- Lerato is authenticated with ROLE_ORG_MEMBER

**Key Steps:**
1. Lerato calls POST `/api/customers` with valid customer data.

**Key Validations:**
- Response: 403 Forbidden.
- No customer created.

---

### RBAC-002: Member Cannot Create Project

**Objective:** Verify that a Member role cannot create a project.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- An ACTIVE customer exists

**Key Steps:**
1. Lerato calls POST `/api/projects` with valid project data.

**Key Validations:**
- Response: 403 Forbidden.
- No project created.

---

### RBAC-003: Member Cannot Approve Invoice

**Objective:** Verify that a Member cannot approve an invoice.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- A DRAFT invoice exists

**Key Steps:**
1. Lerato attempts to approve the invoice.

**Key Validations:**
- Response: 403 Forbidden.
- Invoice remains in DRAFT status.

---

### RBAC-004: Member Cannot Modify Org Settings

**Objective:** Verify that a Member cannot change settings.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- OrgSettings exist

**Key Steps:**
1. Lerato attempts to update defaultCurrency.

**Key Validations:**
- Response: 403 Forbidden.

---

### RBAC-005: Admin Cannot Delete Project

**Objective:** Verify that delete operations are restricted to Owner only.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A project exists

**Key Steps:**
1. Priya calls DELETE `/api/projects/{id}`.

**Key Validations:**
- Response: 403 Forbidden.
- Project still exists.

---

### RBAC-006: Owner Can Delete Project

**Objective:** Verify that Owner role can perform delete operations.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- A project exists with no blocking dependencies

**Key Steps:**
1. Thandi calls DELETE `/api/projects/{id}`.

**Key Validations:**
- Response: 200 OK (or 204 No Content).
- Project is deleted.

---

### RBAC-007: Admin Cannot Delete Proposal

**Objective:** Verify that only Owner can delete a proposal.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A DRAFT proposal exists

**Key Steps:**
1. Priya attempts to delete the proposal.

**Key Validations:**
- Response: 403 Forbidden.

---

### RBAC-008: Portal Contact Cannot Access Other Customer Data

**Objective:** Verify cross-customer data isolation in the portal.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact for Acme Corp)

**Preconditions:**
- Dunbar & Associates has projects, documents, and invoices

**Key Steps:**
1. Alice attempts to access Dunbar's project list.
2. Alice attempts to access Dunbar's documents.

**Key Validations:**
- All requests return 403 or empty results.
- No Dunbar data is exposed.

---

### RBAC-009: Portal Contact BILLING Role Required for Invoices

**Objective:** Verify that only BILLING portal contacts can access invoices.

**Actors:** alice.porter@acmecorp.com (PRIMARY), ben.finance@acmecorp.com (BILLING)

**Preconditions:**
- Invoices exist for Acme Corp

**Key Steps:**
1. Alice (PRIMARY) attempts to list invoices.
2. Ben (BILLING) lists invoices.

**Key Validations:**
- Alice: blocked (403 or empty).
- Ben: invoices returned successfully.

---

### RBAC-010: Member Cannot View Cost Rates

**Objective:** Verify that cost rate data is restricted to Admin/Owner.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- Cost rates configured for the org

**Key Steps:**
1. Lerato attempts to access cost rate endpoints.

**Key Validations:**
- Response: 403 Forbidden.
- Cost rate data not exposed to Members (prevents margin visibility).

---

## 2. Data Integrity

### DI-001: Delete Customer with Linked Projects

**Objective:** Verify behavior when attempting to delete a customer that has linked projects.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- Acme Corp has 2 linked projects

**Key Steps:**
1. Thandi attempts to delete Acme Corp.

**Key Validations:**
- Deletion blocked with an error referencing the linked projects.
- Customer and projects remain intact.

---

### DI-002: Delete Project with Time Entries

**Objective:** Verify behavior when deleting a project that has time entries.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- A project has 5 time entries logged

**Key Steps:**
1. Thandi attempts to delete the project.

**Key Validations:**
- Deletion is either: blocked (referential integrity), or cascades to delete time entries (verify which is intended).
- No orphaned time entries remain if cascade is used.

---

### DI-003: Archive Customer with Open Invoices

**Objective:** Verify that archiving/offboarding a customer with open invoices is handled correctly.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- Acme Corp has a SENT invoice (unpaid)

**Key Steps:**
1. Thandi transitions Acme Corp toward OFFBOARDING.

**Key Validations:**
- System either blocks the transition or warns about outstanding invoices.
- Invoice is not silently voided or lost.

---

### DI-004: Void Invoice with Linked Time Entries

**Objective:** Verify that voiding an invoice properly reverts the billing status of linked time entries.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An invoice exists with 3 line items linked to 3 time entries (billingStatus=BILLED)

**Key Steps:**
1. Priya voids the invoice.

**Key Validations:**
- Invoice status transitions to VOIDED.
- All 3 time entries revert billingStatus from BILLED to UNBILLED.
- Time entries are available for re-invoicing.

---

### DI-005: Delete Member with Time Entries

**Objective:** Verify behavior when a member who has logged time is removed from the org.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- Lerato has 10 time entries across 3 projects

**Key Steps:**
1. Lerato is removed from the organization (Clerk webhook).

**Key Validations:**
- Time entries remain intact (historical data preserved).
- Time entries reference Lerato's member ID (still valid for reporting).
- Lerato's project memberships are cleaned up.
- No orphaned references that break queries.

---

### DI-006: Delete Customer with Retainer Agreement

**Objective:** Verify referential integrity for customers with active retainers.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- Customer has an ACTIVE retainer agreement with open period

**Key Steps:**
1. Thandi attempts to delete the customer.

**Key Validations:**
- Deletion blocked or requires retainer to be terminated first.
- Retainer agreement and periods remain intact.

---

### DI-007: Delete Project Template in Use by Schedules

**Objective:** Verify that deleting a template used by active schedules is handled.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ProjectTemplate is referenced by 2 active RecurringSchedules

**Key Steps:**
1. Priya deactivates the template.

**Key Validations:**
- Template can be deactivated (soft delete).
- Existing schedules referencing the template either: continue with the deactivated template, or are flagged as invalid.

---

### DI-008: Concurrent Time Entry Logging Against Same Task

**Objective:** Verify that two members can log time to the same task simultaneously without data loss.

**Actors:** Sofia Reyes (Member), Aiden O'Brien (Member)

**Preconditions:**
- A task exists in a project where both are members

**Key Steps:**
1. Sofia and Aiden both submit time entries against the same task at the same moment.

**Key Validations:**
- Both time entries are created successfully (no lost writes).
- Task and project time rollups correctly reflect both entries.

---

## 3. Optimistic Locking

### OL-001: Concurrent Task Edit (Version Conflict)

**Objective:** Verify that optimistic locking prevents lost updates on concurrent task edits.

**Actors:** Sofia Reyes (Member), Aiden O'Brien (Member)

**Preconditions:**
- A task exists at version 1

**Key Steps:**
1. Sofia reads the task (receives version=1).
2. Aiden reads the task (receives version=1).
3. Sofia updates the task title (version increments to 2).
4. Aiden attempts to update the task description (sending version=1).

**Key Validations:**
- Sofia's update succeeds (version now 2).
- Aiden's update fails with 409 Conflict (version mismatch).
- Task reflects Sofia's changes only.
- Aiden must re-read and retry.

---

### OL-002: Concurrent Project Edit

**Objective:** Verify optimistic locking on project updates.

**Actors:** Priya Sharma (Admin), Marcus Webb (Admin)

**Preconditions:**
- A project exists at version 3

**Key Steps:**
1. Priya reads the project (version=3).
2. Marcus reads the project (version=3).
3. Priya updates the project deadline.
4. Marcus attempts to update the project description.

**Key Validations:**
- First update succeeds; second fails with 409 Conflict.

---

### OL-003: Concurrent Invoice Line Edit

**Objective:** Verify optimistic locking on invoice edits.

**Actors:** Priya Sharma (Admin), Marcus Webb (Admin)

**Preconditions:**
- A DRAFT invoice exists

**Key Steps:**
1. Both admins read the invoice simultaneously.
2. Priya adds a line item.
3. Marcus attempts to modify an existing line item.

**Key Validations:**
- One succeeds; the other fails with 409 Conflict.
- Invoice data is consistent (no partial updates).

---

### OL-004: Version Number Increments Correctly

**Objective:** Verify that the version field increments by 1 on each successful update.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- An entity exists at version 0

**Key Steps:**
1. Update the entity 3 times in sequence.

**Key Validations:**
- After update 1: version=1.
- After update 2: version=2.
- After update 3: version=3.
- Each response includes the current version.

---

## 4. Cascading Effects

### CASCADE-001: Customer Lifecycle Change Affects Linked Projects

**Objective:** Verify that transitioning a customer to DORMANT or OFFBOARDING affects linked projects appropriately.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- Acme Corp is ACTIVE with 2 ACTIVE projects

**Key Steps:**
1. Thandi transitions Acme Corp to DORMANT.

**Key Validations:**
- Projects may be archived or flagged.
- New project creation blocked for DORMANT customer (lifecycle guard).
- Existing time entries and invoices remain accessible for reporting.

---

### CASCADE-002: Project Archive Affects Tasks

**Objective:** Verify that archiving a project affects its tasks.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A project with 5 tasks in various statuses (TODO, IN_PROGRESS, DONE)

**Key Steps:**
1. Priya archives the project.

**Key Validations:**
- Tasks in active states (TODO, IN_PROGRESS) are either archived or transitioned.
- Tasks no longer appear in My Work for assigned members.
- Time logging against archived project tasks is blocked.

---

### CASCADE-003: Invoice Void Reverts Time Entry Billing Status

**Objective:** Verify the cascade from invoice void to time entry billing status.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- Invoice with 3 time entries marked BILLED

**Key Steps:**
1. Priya voids the invoice.

**Key Validations:**
- All 3 time entries revert to UNBILLED.
- Time entries appear in "unbilled time" queries.
- Project time rollup correctly reflects UNBILLED status.

---

### CASCADE-004: Retainer Terminate Closes Period

**Objective:** Verify that terminating a retainer agreement closes the current open period.

**Actors:** Marcus Webb (Admin)

**Preconditions:**
- A retainer agreement with an OPEN period

**Key Steps:**
1. Marcus terminates the retainer agreement.

**Key Validations:**
- Agreement status transitions to TERMINATED.
- Open period is closed with actual end date.
- No new periods can be opened.

---

### CASCADE-005: Proposal Accept Creates Project and Retainer

**Objective:** Verify the full cascade from proposal acceptance to project and retainer creation.

**Actors:** alice.porter@acmecorp.com (PRIMARY portal contact)

**Preconditions:**
- A SENT proposal with fee model RETAINER exists

**Key Steps:**
1. Alice accepts the proposal via the portal.

**Key Validations:**
- Proposal status transitions to ACCEPTED.
- Project auto-created with name from proposal, linked to customer.
- Retainer agreement auto-created with amount, hours, rollover from proposal.
- First retainer period opened.
- Notifications sent to proposal creator.
- Audit events for: acceptance, project creation, retainer creation.

---

### CASCADE-006: Customer Delete Cascades to Portal Contacts

**Objective:** Verify that deleting (or anonymizing) a customer affects portal contacts.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- A customer with 2 portal contacts

**Key Steps:**
1. Thandi executes a data deletion for the customer.

**Key Validations:**
- Portal contacts are anonymized (email replaced, displayName replaced, status=ARCHIVED).
- Portal access is revoked (magic link tokens invalidated, JWT validation fails).

---

## 5. Notification Completeness

### NOTIF-C-001: Master Notification Checklist

**Objective:** Verify that every significant action has a corresponding notification type and preference toggle.

**Actors:** QA Team (verification exercise)

**Preconditions:**
- Full feature set deployed

**Key Steps:**
1. Review the following action-to-notification mapping:

| Action | Notification Type | Preference Toggle |
|--------|------------------|-------------------|
| Task assigned to member | TASK_ASSIGNED | Yes |
| Comment posted on entity | COMMENT_ADDED | Yes |
| Document visibility set to SHARED | DOCUMENT_SHARED | Yes |
| Budget threshold breached | BUDGET_ALERT | Yes |
| Invoice approved | INVOICE_APPROVED | Yes |
| Invoice sent | INVOICE_SENT | Yes |
| Invoice paid | INVOICE_PAID | Yes |
| Invoice voided | INVOICE_VOIDED | Yes |
| Document generated from template | DOCUMENT_GENERATED | Yes |
| Acceptance request completed | ACCEPTANCE_COMPLETED | Yes |
| Time reminder (below threshold) | TIME_REMINDER | Yes |
| Proposal sent | PROPOSAL_SENT | Yes |
| Proposal accepted | PROPOSAL_ACCEPTED | Yes |
| Proposal declined | PROPOSAL_DECLINED | Yes |
| Proposal expired | PROPOSAL_EXPIRED | Yes |

**Key Validations:**
- Every notification type in the table above is actually dispatched when the action occurs.
- Every notification type has a corresponding NotificationPreference toggle (inAppEnabled + emailEnabled).
- Disabling a preference suppresses the notification for that channel.
- No "orphan" notification types exist (defined in code but never dispatched, or dispatched without a preference toggle).

---

## 6. Audit Completeness

### AUDIT-C-001: Master Audit Checklist

**Objective:** Verify that every significant action generates an audit event with correct metadata.

**Actors:** QA Team (verification exercise)

**Preconditions:**
- Full feature set deployed

**Key Steps:**
1. Review the following action-to-audit mapping:

| Action | entityType | eventType Pattern |
|--------|------------|-------------------|
| Customer created | CUSTOMER | *CREATED* |
| Customer lifecycle transition | CUSTOMER | *TRANSITION* or *UPDATED* |
| Project created | PROJECT | *CREATED* |
| Project updated | PROJECT | *UPDATED* |
| Project deleted | PROJECT | *DELETED* |
| Task created | TASK | *CREATED* |
| Task status transition | TASK | *STATUS* or *UPDATED* |
| Time entry logged | TIME_ENTRY | *CREATED* |
| Invoice created | INVOICE | *CREATED* |
| Invoice approved | INVOICE | *APPROVED* |
| Invoice sent | INVOICE | *SENT* |
| Invoice paid | INVOICE | *PAID* |
| Invoice voided | INVOICE | *VOIDED* |
| Document uploaded | DOCUMENT | *UPLOADED* or *CREATED* |
| Document visibility changed | DOCUMENT | *VISIBILITY* or *UPDATED* |
| Document generated | GENERATED_DOCUMENT | *GENERATED* |
| Acceptance request sent | ACCEPTANCE_REQUEST | *SENT* |
| Acceptance completed | ACCEPTANCE_REQUEST | *COMPLETED* or *ACCEPTED* |
| Portal login | PORTAL_CONTACT | *LOGIN* or *AUTH* |
| Settings changed | ORG_SETTINGS | *UPDATED* |
| Proposal sent | PROPOSAL | *SENT* |
| Proposal accepted/declined | PROPOSAL | *ACCEPTED* / *DECLINED* |
| Data deletion executed | DATA_SUBJECT_REQUEST | *COMPLETED* or *EXECUTED* |

**Key Validations:**
- Every action above generates at least one AuditEvent.
- Each event has: actorId, actorType, source, occurredAt.
- Portal actions include ipAddress and userAgent.
- details JSONB contains meaningful context (entity name, old/new values for transitions).
- Events are immutable (enforced by DB trigger and @Immutable annotation).

---

## 7. Search & Filtering

### FILTER-001: Filter Projects by Status, Customer, Tag, Custom Field

**Objective:** Verify that project listing supports multi-criteria filtering.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- 10 projects with varying statuses, customers, tags, and custom field values

**Key Steps:**
1. Filter by status=ACTIVE.
2. Filter by customer=Acme Corp.
3. Filter by tag="urgent".
4. Filter by custom field industry="tech".
5. Combine: status=ACTIVE AND tag="urgent".

**Key Validations:**
- Each filter returns only matching projects.
- Combined filters apply AND logic (intersection).
- Count matches expected results.

---

### FILTER-002: Filter Tasks by Assignee, Priority, Status

**Objective:** Verify task filtering across common dimensions.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A project with 15 tasks, varying assignees, priorities, statuses

**Key Steps:**
1. Filter by assignee=Sofia.
2. Filter by status=IN_PROGRESS.
3. Filter by both.

**Key Validations:**
- Results correctly filtered.
- Unassigned tasks excluded from assignee filter.

---

### FILTER-003: Filter Invoices by Status, Customer, Date Range

**Objective:** Verify invoice listing filters.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- 20 invoices across statuses and customers, spanning multiple months

**Key Steps:**
1. Filter by status=SENT.
2. Filter by customer=Acme Corp.
3. Filter by date range (this month).
4. Combine all three.

**Key Validations:**
- Each filter narrows results correctly.
- Date range filter uses issued date.
- Combined filters apply AND logic.

---

### FILTER-004: Filter Time Entries by Member, Date, Billable

**Objective:** Verify time entry filtering.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- Time entries from multiple members, dates, and billable/non-billable

**Key Steps:**
1. Filter by member=Sofia.
2. Filter by billable=true.
3. Filter by date range.

**Key Validations:**
- Results correctly filtered.
- Total hours calculation reflects filtered results only.

---

### FILTER-005: Saved View Applies Correctly

**Objective:** Verify that a saved view's stored filters produce correct results.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A SavedView exists with filters={"status":"ACTIVE","tag":"audit"}

**Key Steps:**
1. Apply the saved view.

**Key Validations:**
- Results match manual filtering with status=ACTIVE AND tag=audit.
- View's column selection is respected if applicable.

---

## 8. Pagination & Sorting

### PAGE-001: Large List Paginates Correctly

**Objective:** Verify pagination on a large dataset.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- 50 projects exist in the org

**Key Steps:**
1. Request page 0, size 10.
2. Request page 1, size 10.
3. Request last page.

**Key Validations:**
- Page 0: 10 items, page.totalElements=50, page.totalPages=5, page.number=0.
- Page 1: 10 items, page.number=1.
- Last page: remaining items, correct count.
- No duplicate items across pages.
- Response uses VIA_DTO format: `{ content: [...], page: { totalElements, totalPages, size, number } }`.

---

### PAGE-002: Sort by Date, Name, Amount

**Objective:** Verify sorting across different fields.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- Entities with varying dates, names, and amounts

**Key Steps:**
1. Sort projects by createdAt descending.
2. Sort customers by name ascending.
3. Sort invoices by total amount descending.

**Key Validations:**
- Items returned in correct order for each sort.
- Sort is stable (items with equal sort values have consistent ordering).

---

### PAGE-003: Page Boundaries Correct

**Objective:** Verify edge cases in pagination.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- 23 items exist

**Key Steps:**
1. Request page 0, size 10 (expect 10 items).
2. Request page 2, size 10 (expect 3 items, last page).
3. Request page 3, size 10 (expect 0 items or empty page).

**Key Validations:**
- Page 2: content has 3 items, totalPages=3.
- Page 3: content is empty (no error thrown for out-of-range page).

---

## 9. Concurrent Operations

### CONC-001: Two Admins Approve Same Invoice Simultaneously

**Objective:** Verify that concurrent approval attempts are handled safely.

**Actors:** Priya Sharma (Admin), Marcus Webb (Admin)

**Preconditions:**
- A DRAFT invoice exists

**Key Steps:**
1. Priya and Marcus both attempt to approve the same invoice at the same moment.

**Key Validations:**
- Exactly one approval succeeds (invoice transitions to APPROVED once).
- The second attempt either: fails with 409 Conflict (version mismatch), or returns a no-op result (already APPROVED).
- Invoice is in APPROVED status with correct approver.
- No duplicate audit events or notifications.

---

### CONC-002: Two Members Log Time to Same Task

**Objective:** Verify that concurrent time logging does not cause data loss.

**Actors:** Sofia Reyes (Member), Aiden O'Brien (Member)

**Preconditions:**
- A task exists in a shared project

**Key Steps:**
1. Sofia and Aiden both submit time entries against the same task within milliseconds.

**Key Validations:**
- Both time entries are created (separate records, no conflict).
- Task time rollup reflects both entries.
- Project time rollup reflects both entries.

---

### CONC-003: Budget Status Recalculates Under Concurrent Time Logging

**Objective:** Verify that budget calculations remain accurate under concurrent writes.

**Actors:** Sofia Reyes (Member), Aiden O'Brien (Member)

**Preconditions:**
- A project has a budget of 100 hours, currently at 95 hours used
- Budget alert threshold is 100%

**Key Steps:**
1. Sofia logs 3 hours and Aiden logs 4 hours simultaneously.

**Key Validations:**
- Both time entries created (total now 102 hours).
- Budget status correctly shows over-budget.
- Budget alert notification fires (threshold exceeded).
- No race condition where the alert fires twice or not at all.
- Budget percentage calculation is consistent (102/100 = 102%).
