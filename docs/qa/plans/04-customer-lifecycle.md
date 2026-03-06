# 04 — Customer Lifecycle Domain Test Plan

**Domain:** Customer lifecycle state machine, lifecycle guards, onboarding checklists, dormancy, completeness scores, portal contacts
**Primary Tester:** James Chen (Admin)
**Supporting:** Priya Sharma (Admin), Thandi Nkosi (Owner), Lerato Dlamini (Member)

---

## Test Data Reference

| Customer | Lifecycle Status | Purpose |
|----------|-----------------|---------|
| Bright Solutions | PROSPECT | Lifecycle guard testing, initial transition |
| Crestview Holdings | ONBOARDING | Checklist/auto-transition testing |
| Acme Corp | ACTIVE | Steady-state operations, dormancy candidate |
| Echo Ventures | DORMANT | Reactivation, dormancy workflows |
| Fable Industries | OFFBOARDING | Restricted operations, offboarding completion |

### Portal Contacts

| Contact | Customer | Role |
|---------|----------|------|
| alice.porter@acmecorp.com | Acme Corp | PRIMARY |
| ben.finance@acmecorp.com | Acme Corp | BILLING |

---

## 1. Lifecycle State Machine

### CL-001: Valid transition PROSPECT to ONBOARDING

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" exists with lifecycle status PROSPECT
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ONBOARDING", "notes": "Starting onboarding"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "ONBOARDING"
**And** `lifecycleStatusChangedAt` is populated with the current timestamp
**And** `lifecycleStatusChangedBy` equals James's member ID
**And** an audit event of type `customer.lifecycle_transition` is recorded with `from: PROSPECT, to: ONBOARDING`

### CL-002: Valid transition ONBOARDING to ACTIVE (manual)

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Crestview Holdings" exists with lifecycle status ONBOARDING
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ACTIVE", "notes": "Onboarding complete"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "ACTIVE"
**And** lifecycle history via `GET /api/customers/{id}/lifecycle` includes an entry from ONBOARDING to ACTIVE

### CL-003: Valid transition ACTIVE to DORMANT

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists with lifecycle status ACTIVE
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "DORMANT", "notes": "No activity for 90 days"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "DORMANT"

### CL-004: Valid transition DORMANT to ACTIVE (reactivation)

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Echo Ventures" exists with lifecycle status DORMANT
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ACTIVE", "notes": "Re-engaged client"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "ACTIVE"

### CL-005: Valid transition ACTIVE to OFFBOARDING

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists with lifecycle status ACTIVE
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "OFFBOARDING", "notes": "Client disengaging"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "OFFBOARDING"

### CL-006: Valid transition OFFBOARDING to OFFBOARDED

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Fable Industries" exists with lifecycle status OFFBOARDING
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "OFFBOARDED", "notes": "Offboarding complete"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "OFFBOARDED"
**And** `offboardedAt` is populated with the current timestamp

### CL-007: Valid transition OFFBOARDED to ACTIVE (re-engagement)

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** an OFFBOARDED customer exists
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ACTIVE", "notes": "Re-engaging"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "ACTIVE"

### CL-008: Valid transition DORMANT to OFFBOARDING

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customer "Echo Ventures" exists with lifecycle status DORMANT
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "OFFBOARDING"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "OFFBOARDING"

### CL-009: Valid transition ONBOARDING to OFFBOARDING

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a customer exists with lifecycle status ONBOARDING
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "OFFBOARDING", "notes": "Client withdrew"}`
**Then** the response status is 200
**And** the response body shows `lifecycleStatus` = "OFFBOARDING"

### CL-010: Blocked transition PROSPECT to ACTIVE (skips ONBOARDING)

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" exists with lifecycle status PROSPECT
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ACTIVE"}`
**Then** the response status is 400
**And** the error detail contains "Cannot transition from PROSPECT to ACTIVE"

### CL-011: Blocked transition PROSPECT to DORMANT

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" exists with lifecycle status PROSPECT
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "DORMANT"}`
**Then** the response status is 400
**And** the error detail contains "Cannot transition from PROSPECT to DORMANT"

### CL-012: Blocked transition ACTIVE to ONBOARDING (reverse)

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists with lifecycle status ACTIVE
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ONBOARDING"}`
**Then** the response status is 400
**And** the error detail contains "Cannot transition from ACTIVE to ONBOARDING"

### CL-013: Blocked transition OFFBOARDING to ACTIVE (must go through OFFBOARDED)

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Fable Industries" exists with lifecycle status OFFBOARDING
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ACTIVE"}`
**Then** the response status is 400
**And** the error detail contains "Cannot transition from OFFBOARDING to ACTIVE"

### CL-014: Blocked transition OFFBOARDED to DORMANT

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** an OFFBOARDED customer exists
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "DORMANT"}`
**Then** the response status is 400

### CL-015: Lifecycle history tracks all transitions

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" has been transitioned PROSPECT -> ONBOARDING -> ACTIVE
**When** James sends `GET /api/customers/{id}/lifecycle`
**Then** the response contains at least 2 audit events of type `customer.lifecycle_transition`
**And** events are ordered chronologically
**And** each event includes the `from` status, `to` status, and acting member ID

---

## 2. Lifecycle Guards

### CL-020: PROSPECT blocked from CREATE_PROJECT

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" exists with lifecycle status PROSPECT
**When** James attempts to create a project linked to "Bright Solutions" via `POST /api/projects` with `customerId` set to Bright Solutions' ID
**Then** the response status is 400
**And** the error detail contains "Cannot create project for customer in PROSPECT lifecycle status"

### CL-021: PROSPECT blocked from CREATE_TASK

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" exists with lifecycle status PROSPECT
**And** a project exists that is linked to "Bright Solutions" (linked before lifecycle guard was enforced, or linked via direct DB setup)
**When** James attempts to create a task on that project
**Then** the response status is 400
**And** the error detail contains "Cannot create task for customer in PROSPECT lifecycle status"

### CL-022: PROSPECT blocked from CREATE_TIME_ENTRY

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" exists with lifecycle status PROSPECT
**When** James attempts to log a time entry against a task belonging to "Bright Solutions"
**Then** the response status is 400
**And** the error detail contains "Cannot create time entry for customer in PROSPECT lifecycle status"

### CL-023: PROSPECT blocked from CREATE_INVOICE

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" exists with lifecycle status PROSPECT
**When** James attempts to create an invoice for "Bright Solutions" via `POST /api/invoices`
**Then** the response status is 400
**And** the error detail contains "Cannot create invoice for customer in PROSPECT lifecycle status"

### CL-024: ONBOARDING allows CREATE_PROJECT

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Crestview Holdings" exists with lifecycle status ONBOARDING
**When** James creates a project linked to "Crestview Holdings"
**Then** the response status is 201
**And** the project is created successfully

### CL-025: ONBOARDING blocked from CREATE_INVOICE

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Crestview Holdings" exists with lifecycle status ONBOARDING
**When** James attempts to create an invoice for "Crestview Holdings"
**Then** the response status is 400
**And** the error detail contains "Cannot create invoice for customer in ONBOARDING lifecycle status"

### CL-026: ACTIVE allows all operations

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists with lifecycle status ACTIVE
**When** James creates a project, task, time entry, and invoice for "Acme Corp"
**Then** all operations succeed with status 201

### CL-027: DORMANT allows CREATE_INVOICE

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customer "Echo Ventures" exists with lifecycle status DORMANT
**When** James creates an invoice for "Echo Ventures"
**Then** the response status is 201 (DORMANT allows invoicing for outstanding work)

### CL-028: OFFBOARDING blocked from CREATE_PROJECT and CREATE_TASK

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Fable Industries" exists with lifecycle status OFFBOARDING
**When** James attempts to create a project linked to "Fable Industries"
**Then** the response status is 400
**And** the error detail contains "Cannot create project for customer in OFFBOARDING lifecycle status"
**When** James attempts to create a task on an existing project for "Fable Industries"
**Then** the response status is 400

### CL-029: OFFBOARDED blocked from all create operations except comments

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an OFFBOARDED customer exists
**When** James attempts CREATE_PROJECT, CREATE_TASK, CREATE_TIME_ENTRY, CREATE_INVOICE, and CREATE_DOCUMENT
**Then** all operations return 400 except CREATE_DOCUMENT which also returns 400
**When** James attempts CREATE_COMMENT
**Then** the operation succeeds (comments are always allowed)

### CL-030: CREATE_DOCUMENT allowed for all statuses except OFFBOARDED

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customers in PROSPECT, ONBOARDING, ACTIVE, DORMANT, and OFFBOARDING statuses
**When** James uploads a document scoped to each customer
**Then** operations succeed for PROSPECT, ONBOARDING, ACTIVE, DORMANT, and OFFBOARDING
**And** the operation fails for OFFBOARDED with status 400

---

## 3. Checklists

### CL-040: Create checklist template

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** Priya is authenticated as Admin
**When** Priya sends `POST /api/checklist-templates` with body:
```json
{
  "name": "Standard Onboarding",
  "description": "Standard client onboarding checklist",
  "items": [
    {"name": "Collect ID documents", "description": "Upload certified ID", "sortOrder": 1, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Certified ID Copy"},
    {"name": "Verify banking details", "sortOrder": 2, "required": true, "requiresDocument": false},
    {"name": "Welcome pack sent", "sortOrder": 3, "required": false, "requiresDocument": false}
  ]
}
```
**Then** the response status is 201
**And** the template contains 3 items with correct sort orders and required flags

### CL-041: Instantiate checklist for customer

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** checklist template "Standard Onboarding" exists
**And** customer "Crestview Holdings" exists with lifecycle status ONBOARDING
**When** Priya sends `POST /api/checklist-instances` with body `{"templateId": "{templateId}", "customerId": "{crestwiewId}"}`
**Then** the response status is 201
**And** the instance status is "IN_PROGRESS"
**And** the instance contains 3 items all with status "PENDING"
**And** an audit event `checklist.instance.created` is logged

### CL-042: Duplicate checklist instance blocked

**Severity:** Medium
**Actor:** Priya Sharma (Admin)

**Given** a checklist instance already exists for "Crestview Holdings" from template "Standard Onboarding"
**When** Priya attempts to create another instance from the same template for the same customer
**Then** the response status is 409
**And** the error detail contains "A checklist instance already exists for this customer and template"

### CL-043: Complete a checklist item

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** a checklist instance exists for "Crestview Holdings" with item "Verify banking details" in PENDING status
**When** Priya sends `POST /api/checklist-instances/items/{itemId}/complete` with body `{"notes": "Verified with bank statement"}`
**Then** the response status is 200
**And** the item status changes to "COMPLETED"
**And** `completedAt` is populated
**And** `completedBy` equals Priya's member ID
**And** an audit event `checklist.item.completed` is logged

### CL-044: Complete a checklist item requiring a document

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** checklist item "Collect ID documents" has `requiresDocument` = true and `requiredDocumentLabel` = "Certified ID Copy"
**When** Priya attempts to complete the item without a `documentId`
**Then** the response status is 400
**And** the error detail contains "This item requires a document upload. Please upload: Certified ID Copy"
**When** Priya completes the item with a valid `documentId`
**Then** the item status changes to "COMPLETED" and `documentId` is stored

### CL-045: Skip a non-required checklist item

**Severity:** Medium
**Actor:** Priya Sharma (Admin)

**Given** checklist item "Welcome pack sent" is non-required and in PENDING status
**When** Priya sends `POST /api/checklist-instances/items/{itemId}/skip` with body `{"reason": "Not applicable for this client"}`
**Then** the response status is 200
**And** the item status changes to "SKIPPED"
**And** the `notes` field contains the skip reason
**And** an audit event `checklist.item.skipped` is logged

### CL-046: Reopen a completed checklist item

**Severity:** Medium
**Actor:** Priya Sharma (Admin)

**Given** checklist item "Verify banking details" has status COMPLETED
**When** Priya sends `POST /api/checklist-instances/items/{itemId}/reopen`
**Then** the response status is 200
**And** the item status changes to "PENDING"
**And** `completedAt`, `completedBy`, `notes`, and `documentId` are cleared

### CL-047: Reopen a skipped checklist item

**Severity:** Medium
**Actor:** Priya Sharma (Admin)

**Given** checklist item "Welcome pack sent" has status SKIPPED
**When** Priya sends `POST /api/checklist-instances/items/{itemId}/reopen`
**Then** the response status is 200
**And** the item status changes to "PENDING"

### CL-048: Cannot reopen a PENDING item

**Severity:** Low
**Actor:** Priya Sharma (Admin)

**Given** a checklist item with status PENDING
**When** Priya attempts to reopen it
**Then** the response status is 400
**And** the error detail contains "Cannot reopen item in status 'PENDING'"

### CL-049: Auto-transition ONBOARDING to ACTIVE on all checklists complete

**Severity:** Critical
**Actor:** Priya Sharma (Admin)

**Given** customer "Crestview Holdings" has lifecycle status ONBOARDING
**And** exactly one checklist instance with 3 items (2 required, 1 optional)
**When** Priya completes or skips all required items
**Then** the checklist instance status auto-transitions to "COMPLETED"
**And** customer "Crestview Holdings" lifecycle status auto-transitions to ACTIVE
**And** an audit event `checklist.instance.completed` is logged
**And** a lifecycle transition audit event is logged

### CL-050: Auto-transition blocked when not all instances complete

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** customer "Crestview Holdings" has lifecycle status ONBOARDING
**And** two checklist instances exist: Instance A (3 items) and Instance B (2 items)
**When** Priya completes all items in Instance A
**Then** Instance A status changes to "COMPLETED"
**But** the customer lifecycle remains ONBOARDING (Instance B still IN_PROGRESS)
**When** Priya completes all required items in Instance B
**Then** Instance B status changes to "COMPLETED"
**And** customer lifecycle auto-transitions to ACTIVE

### CL-051: Auto-transition only fires during ONBOARDING

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** customer "Bright Solutions" has lifecycle status PROSPECT
**And** a checklist instance is created and all items are completed
**When** the last required item is completed
**Then** the checklist instance status changes to "COMPLETED"
**But** the customer lifecycle remains PROSPECT (auto-transition only fires from ONBOARDING)

### CL-052: Reopen item reverts completed instance to IN_PROGRESS

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** a checklist instance for "Crestview Holdings" has status COMPLETED
**When** Priya reopens one of the completed items
**Then** the checklist instance status reverts to "IN_PROGRESS"

### CL-053: Dependency chain enforcement on checklist items

**Severity:** Medium
**Actor:** Priya Sharma (Admin)

**Given** a checklist template where item B depends on item A
**And** a checklist instance is created from this template
**Then** item B has status "BLOCKED" and item A has status "PENDING"
**When** Priya attempts to complete item B
**Then** the response status is 409
**And** the error detail contains "depends on" and the dependency item's name
**When** Priya completes item A
**Then** item B is automatically unblocked to "PENDING"

### CL-054: Checklist progress endpoint

**Severity:** Medium
**Actor:** Priya Sharma (Admin)

**Given** a checklist instance with 5 items: 3 required (2 completed, 1 pending), 2 optional (1 skipped, 1 pending)
**When** Priya sends `GET /api/checklist-instances/{id}/progress`
**Then** the response contains `completed: 2, total: 5, requiredCompleted: 2, requiredTotal: 3`

### CL-055: List checklist instances for customer

**Severity:** Medium
**Actor:** Priya Sharma (Admin)

**Given** customer "Crestview Holdings" has 2 checklist instances
**When** Priya sends `GET /api/customers/{id}/checklist-instances`
**Then** the response contains 2 instances, each with their items populated
**And** items include member names for `completedBy` fields

---

## 4. Dormancy

### CL-060: Configure dormancy threshold

**Severity:** High
**Actor:** Thandi Nkosi (Owner)

**Given** Thandi is authenticated as Owner
**When** Thandi updates org settings to set `dormancyThresholdDays` = 90
**Then** the response status is 200
**And** `GET /api/org-settings` returns `dormancyThresholdDays` = 90

### CL-061: Run dormancy check identifies dormant candidates

**Severity:** High
**Actor:** James Chen (Admin)

**Given** `dormancyThresholdDays` is set to 90
**And** customer "Acme Corp" (ACTIVE) has had no activity for 95 days
**And** customer "Crestview Holdings" (ACTIVE) has had activity within the last 30 days
**When** James sends `POST /api/customers/dormancy-check`
**Then** the response contains a `candidates` list
**And** "Acme Corp" appears as a candidate with `daysSinceActivity` >= 90
**And** "Crestview Holdings" does NOT appear as a candidate
**And** the response includes `thresholdDays` = 90

### CL-062: Dormancy check returns empty list when no candidates

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** `dormancyThresholdDays` is set to 365
**And** all ACTIVE customers have had activity within the last 60 days
**When** James sends `POST /api/customers/dormancy-check`
**Then** the response contains `candidates` as an empty list

### CL-063: Manual dormancy transition after check

**Severity:** High
**Actor:** James Chen (Admin)

**Given** the dormancy check identified "Acme Corp" as a candidate
**When** James sends `POST /api/customers/{acmeId}/transition` with body `{"targetStatus": "DORMANT", "notes": "Identified by dormancy check"}`
**Then** the response status is 200
**And** "Acme Corp" lifecycle status is DORMANT

### CL-064: Reactivation from DORMANT to ACTIVE

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Echo Ventures" has lifecycle status DORMANT
**When** James sends `POST /api/customers/{id}/transition` with body `{"targetStatus": "ACTIVE", "notes": "Client re-engaged, new project"}`
**Then** the response status is 200
**And** lifecycle status changes to ACTIVE
**And** lifecycle history includes the DORMANT -> ACTIVE transition

---

## 5. Completeness Scores

### CL-070: Single customer completeness score

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" has name, email, phone, idNumber all populated
**And** required custom fields are filled
**When** James sends `GET /api/customers/{acmeId}/readiness`
**Then** the response contains a readiness object with completeness data
**And** the score reflects the ratio of filled required fields

### CL-071: Batch completeness scores

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customers "Acme Corp" (fully complete) and "Bright Solutions" (missing phone and idNumber) exist
**When** James sends `GET /api/customers/completeness-summary?customerIds={acmeId},{brightId}`
**Then** the response is a map keyed by customer ID
**And** "Acme Corp" has a higher completeness score than "Bright Solutions"

### CL-072: Aggregated org-level completeness summary

**Severity:** Medium
**Actor:** Thandi Nkosi (Owner)

**Given** multiple customers exist with varying completeness levels
**When** Thandi sends `GET /api/customers/completeness-summary/aggregated`
**Then** the response includes overall statistics across all customers
**And** the response includes the top 10 least-complete customers

### CL-073: Completeness score updates after customer edit

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" is missing `phone` and `idNumber` and has a low completeness score
**When** James updates "Bright Solutions" with phone and idNumber populated
**And** James re-requests the completeness score
**Then** the new score is higher than before

---

## 6. Customer CRUD

### CL-080: Create customer with all fields

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James sends `POST /api/customers` with body:
```json
{
  "name": "New Client Ltd",
  "email": "contact@newclient.com",
  "phone": "+27-11-555-0100",
  "idNumber": "2001010001082",
  "notes": "Referred by Acme Corp",
  "customerType": "COMPANY"
}
```
**Then** the response status is 201
**And** the customer has `lifecycleStatus` = "PROSPECT" (default)
**And** the customer has `status` = "ACTIVE" (record status, not lifecycle)
**And** `createdBy` matches James's member ID
**And** an audit event `customer.created` is logged

### CL-081: Create customer with duplicate email rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a customer already exists with email "contact@acmecorp.com"
**When** James attempts to create another customer with email "contact@acmecorp.com"
**Then** the response status is 409
**And** the error detail contains "A customer with email contact@acmecorp.com already exists"

### CL-082: Update customer details

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists
**When** James sends `PUT /api/customers/{id}` with updated name, phone, and notes
**Then** the response status is 200
**And** the updated fields are reflected in the response
**And** `updatedAt` is refreshed
**And** an audit event `customer.updated` is logged with changed field delta

### CL-083: Update customer email to an already-taken email rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" and customer "Bright Solutions" exist with different emails
**When** James attempts to update "Acme Corp" to use "Bright Solutions"' email
**Then** the response status is 409

### CL-084: Archive customer with no linked entities

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a customer exists with no linked projects, invoices, or retainer agreements
**When** James sends `DELETE /api/customers/{id}`
**Then** the response status is 200
**And** the customer `status` changes to "ARCHIVED"
**And** the customer `lifecycleStatus` changes to OFFBOARDED
**And** `offboardedAt` is populated
**And** an audit event `customer.archived` is logged

### CL-085: Archive customer with linked projects blocked

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" has linked projects
**When** James sends `DELETE /api/customers/{acmeId}`
**Then** the response status is 409
**And** the error detail contains "Cannot archive customer with linked projects"

### CL-086: Archive customer with invoices blocked

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" has invoices
**When** James sends `DELETE /api/customers/{acmeId}`
**Then** the response status is 409
**And** the error detail contains "Cannot archive customer with" and "invoice(s)"

### CL-087: Archive customer with retainer agreements blocked

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" has an active retainer agreement
**When** James sends `DELETE /api/customers/{acmeId}`
**Then** the response status is 409
**And** the error detail contains "Cannot archive customer with" and "retainer agreement(s)"

### CL-088: Unarchive customer

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an archived customer exists (`status` = "ARCHIVED")
**When** James sends `POST /api/customers/{id}/unarchive`
**Then** the response status is 200
**And** the customer `status` changes to "ACTIVE"
**And** the customer `lifecycleStatus` changes to DORMANT
**And** `offboardedAt` is cleared
**And** an audit event `customer.unarchived` is logged

### CL-089: Unarchive non-archived customer rejected

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** a customer with `status` = "ACTIVE" (not archived)
**When** James sends `POST /api/customers/{id}/unarchive`
**Then** the response status is 400
**And** the error detail contains "Customer is not archived"

### CL-090: Link project to customer

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" and project "Website Redesign" both exist
**When** James sends `POST /api/customers/{acmeId}/projects/{projectId}`
**Then** the response status is 201
**And** the link includes `customerId`, `projectId`, `linkedBy`, and `createdAt`

### CL-091: Unlink project from customer

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" is linked to project "Website Redesign"
**When** James sends `DELETE /api/customers/{acmeId}/projects/{projectId}`
**Then** the response status is 204

### CL-092: List projects for customer

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" is linked to 2 projects
**When** James sends `GET /api/customers/{acmeId}/projects`
**Then** the response contains 2 project entries with id, name, description, and createdAt

### CL-093: Set custom fields on customer

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a custom field "industry" of type TEXT is defined for CUSTOMER entity type
**And** customer "Acme Corp" exists
**When** James sends `PUT /api/customers/{id}` with `customFields: {"industry": "Technology"}`
**Then** the response shows `customFields.industry` = "Technology"

### CL-094: Reject unknown custom field slug

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** no custom field "unknown_field" is defined for CUSTOMER entity type
**When** James creates a customer with `customFields: {"unknown_field": "value"}`
**Then** the response status is 400
**And** the error detail contains "Field slug 'unknown_field' does not exist for entity type CUSTOMER"

### CL-095: Set tags on customer

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** tags "vip" and "priority" exist
**When** James sends `POST /api/customers/{id}/tags` with body `{"tagIds": ["{vipTagId}", "{priorityTagId}"]}`
**Then** the response contains 2 tags
**When** James sends `GET /api/customers/{id}/tags`
**Then** the response contains the same 2 tags

### CL-096: List customers filtered by lifecycle status

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customers in various lifecycle statuses exist
**When** James sends `GET /api/customers?lifecycleStatus=ACTIVE`
**Then** the response only contains customers with `lifecycleStatus` = "ACTIVE"

### CL-097: Lifecycle summary endpoint

**Severity:** Medium
**Actor:** Thandi Nkosi (Owner)

**Given** customers exist across multiple lifecycle statuses
**When** Thandi sends `GET /api/customers/lifecycle-summary`
**Then** the response is a map of status to count (e.g., `{"ACTIVE": 2, "PROSPECT": 1, "DORMANT": 1}`)
**And** all statuses with at least one customer are represented

---

## 7. Portal Contacts

### CL-100: Create PRIMARY portal contact

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists
**When** James creates a portal contact with email "alice.porter@acmecorp.com", role PRIMARY, linked to Acme Corp
**Then** the portal contact is created with status ACTIVE
**And** the `role` is PRIMARY

### CL-101: Create BILLING portal contact

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists
**When** James creates a portal contact with email "ben.finance@acmecorp.com", role BILLING
**Then** the portal contact is created with role BILLING

### CL-102: Create GENERAL portal contact

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists
**When** James creates a portal contact with email "general@acmecorp.com", role GENERAL
**Then** the portal contact is created with role GENERAL

### CL-103: Portal contact can authenticate via magic link

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** portal contact "alice.porter@acmecorp.com" is linked to "Acme Corp"
**When** a magic link is generated for alice.porter@acmecorp.com
**And** the magic link token is exchanged for a portal JWT
**Then** the portal JWT contains the correct `customerId` and `portalContactId`
**And** alice can access portal endpoints (`/portal/api/*`)

### CL-104: Portal contact only sees their own customer's data

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** alice is authenticated with a portal JWT scoped to Acme Corp
**When** alice attempts to access data for "Bright Solutions" (a different customer)
**Then** the request is rejected (404 or 403)

---

## 8. RBAC

### CL-110: Admin can transition lifecycle

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James sends `POST /api/customers/{id}/transition` with a valid transition
**Then** the response status is 200

### CL-111: Owner can transition lifecycle

**Severity:** High
**Actor:** Thandi Nkosi (Owner)

**Given** Thandi is authenticated as Owner
**When** Thandi sends `POST /api/customers/{id}/transition` with a valid transition
**Then** the response status is 200

### CL-112: Member cannot transition lifecycle

**Severity:** Critical
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `POST /api/customers/{id}/transition` with a valid transition
**Then** the response status is 403

### CL-113: Member can view customers

**Severity:** High
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `GET /api/customers`
**Then** the response status is 200 and the customer list is returned
**When** Lerato sends `GET /api/customers/{id}`
**Then** the response status is 200

### CL-114: Member cannot create customers

**Severity:** High
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `POST /api/customers` with valid data
**Then** the response status is 403

### CL-115: Member cannot archive/unarchive customers

**Severity:** High
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `DELETE /api/customers/{id}` (archive)
**Then** the response status is 403
**When** Lerato sends `POST /api/customers/{id}/unarchive`
**Then** the response status is 403

### CL-116: Member cannot run dormancy check

**Severity:** Medium
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `POST /api/customers/dormancy-check`
**Then** the response status is 403

### CL-117: Member can view lifecycle history

**Severity:** Medium
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `GET /api/customers/{id}/lifecycle`
**Then** the response status is 200

### CL-118: Admin can manage tags but Member cannot

**Severity:** Medium
**Actor:** James Chen (Admin), Lerato Dlamini (Member)

**Given** James is authenticated as Admin and Lerato as Member
**When** James sends `POST /api/customers/{id}/tags` with valid tag IDs
**Then** the response status is 200
**When** Lerato sends `POST /api/customers/{id}/tags`
**Then** the response status is 403
**When** Lerato sends `GET /api/customers/{id}/tags`
**Then** the response status is 200 (read is allowed)

---

## 9. Edge Cases

### CL-120: Complete all checklists while customer is in PROSPECT (no auto-transition)

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** customer "Bright Solutions" has lifecycle status PROSPECT
**And** a checklist instance exists for "Bright Solutions" with 2 required items
**When** Priya completes all required items
**Then** the checklist instance status changes to "COMPLETED"
**But** "Bright Solutions" lifecycle status remains PROSPECT (auto-transition only fires from ONBOARDING)
**And** no lifecycle transition audit event is logged

### CL-121: Duplicate customer names allowed

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" exists with email "contact@acmecorp.com"
**When** James creates another customer with name "Acme Corp" but email "contact2@acmecorp.com"
**Then** the response status is 201 (duplicate names are allowed; only email must be unique)

### CL-122: Customer with active projects during offboarding

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Fable Industries" has lifecycle status OFFBOARDING
**And** "Fable Industries" has 2 linked projects with open tasks
**When** James transitions "Fable Industries" to OFFBOARDED
**Then** the response status is 200 (transition succeeds)
**And** `offboardedAt` is set
**But** the linked projects still exist and are accessible (archival is a separate operation)

### CL-123: Customer PII anonymization after offboarding

**Severity:** High
**Actor:** Thandi Nkosi (Owner)

**Given** an OFFBOARDED customer exists with name "Fable Industries", email "info@fable.com", phone "+27-11-555-0101", idNumber "1234567890"
**When** the anonymize operation is triggered
**Then** the customer name is replaced with a generic value
**And** the email is replaced with "anon-{id}@anonymized.invalid"
**And** phone and idNumber are set to null
**And** `updatedAt` is refreshed

### CL-124: Lifecycle transition with notes preserved

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** customer "Bright Solutions" has lifecycle status PROSPECT
**When** James transitions to ONBOARDING with notes "Signed engagement letter on 2026-03-01"
**Then** the lifecycle history entry includes the notes text

### CL-125: Prerequisite check blocks auto-transition when required fields missing

**Severity:** High
**Actor:** Priya Sharma (Admin)

**Given** customer "Crestview Holdings" has lifecycle status ONBOARDING
**And** a prerequisite rule requires certain custom fields for LIFECYCLE_ACTIVATION
**And** those fields are NOT populated on "Crestview Holdings"
**When** all checklist items are completed
**Then** the checklist instance status changes to "COMPLETED"
**But** the customer lifecycle remains ONBOARDING (prerequisite check blocks auto-transition)
**And** a notification "PREREQUISITE_BLOCKED_ACTIVATION" is sent to admins and owners

### CL-126: Concurrent lifecycle transitions

**Severity:** Medium
**Actor:** James Chen (Admin), Priya Sharma (Admin)

**Given** customer "Echo Ventures" has lifecycle status DORMANT
**When** James and Priya simultaneously send transition requests to ACTIVE
**Then** exactly one succeeds and one fails (or both succeed idempotently if Hibernate optimistic locking is in place)
**And** the final state is consistent

### CL-127: Filter customers by custom field values

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** customers exist with custom field "industry" set to "Technology" and "Finance"
**When** James sends `GET /api/customers?customField.industry=Technology`
**Then** only customers with `industry` = "Technology" are returned

### CL-128: Filter customers by tag slugs

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** some customers are tagged with "vip"
**When** James sends `GET /api/customers?tag=vip`
**Then** only customers tagged with "vip" are returned
