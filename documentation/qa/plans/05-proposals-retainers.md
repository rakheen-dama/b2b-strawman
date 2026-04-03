# 05 — Proposals & Retainers Domain Test Plan

**Domain:** Proposal pipeline (DRAFT through ACCEPTED/DECLINED/EXPIRED), retainer agreements, periods, rollover, portal acceptance
**Primary Tester:** James Chen (Admin)
**Supporting:** Priya Sharma (Admin), Thandi Nkosi (Owner), Lerato Dlamini (Member), alice.porter@acmecorp.com (Portal Contact)

---

## Test Data Reference

| Customer | Lifecycle Status | Purpose |
|----------|-----------------|---------|
| Acme Corp | ACTIVE | Primary proposal/retainer customer |
| Bright Solutions | PROSPECT | Proposal for PROSPECT customer (tests orchestration lifecycle transition) |
| Dunbar & Associates | ACTIVE | Cross-customer retainer testing |

### Portal Contacts

| Contact | Customer | Role |
|---------|----------|------|
| alice.porter@acmecorp.com | Acme Corp | PRIMARY |
| ben.finance@acmecorp.com | Acme Corp | BILLING |

---

## 1. Proposal CRUD

### PROP-001: Create proposal with FIXED fee model

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**And** customer "Acme Corp" exists
**When** James sends `POST /api/proposals` with body:
```json
{
  "title": "Website Redesign Phase 1",
  "customerId": "{acmeId}",
  "feeModel": "FIXED",
  "fixedFeeAmount": 150000.00,
  "fixedFeeCurrency": "ZAR",
  "contentJson": {"type": "doc", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "Scope of work..."}]}]},
  "expiresAt": "2026-04-06T00:00:00Z"
}
```
**Then** the response status is 201
**And** the proposal has `status` = "DRAFT"
**And** `proposalNumber` is populated (sequential, e.g., "PROP-001")
**And** `feeModel` = "FIXED"
**And** `fixedFeeAmount` = 150000.00
**And** `fixedFeeCurrency` = "ZAR"
**And** `createdById` equals James's member ID

### PROP-002: Create proposal with HOURLY fee model

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James sends `POST /api/proposals` with `feeModel: "HOURLY"` and `hourlyRateNote: "Standard rates per rate card"`
**Then** the response status is 201
**And** `feeModel` = "HOURLY"
**And** `hourlyRateNote` = "Standard rates per rate card"

### PROP-003: Create proposal with RETAINER fee model

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James sends `POST /api/proposals` with:
```json
{
  "title": "Monthly Advisory Retainer",
  "customerId": "{acmeId}",
  "feeModel": "RETAINER",
  "retainerAmount": 25000.00,
  "retainerCurrency": "ZAR",
  "retainerHoursIncluded": 40
}
```
**Then** the response status is 201
**And** `feeModel` = "RETAINER"
**And** `retainerAmount` = 25000.00
**And** `retainerHoursIncluded` = 40

### PROP-004: Update proposal in DRAFT status

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal exists for Acme Corp
**When** James sends `PUT /api/proposals/{id}` with updated title and fixedFeeAmount
**Then** the response status is 200
**And** the title and amount are updated
**And** `updatedAt` is refreshed

### PROP-005: Update proposal in SENT status blocked

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** a SENT proposal exists
**When** James sends `PUT /api/proposals/{id}` with updated title
**Then** the response status is 400
**And** the error detail contains "Cannot edit proposal in status SENT"

### PROP-006: Update proposal in ACCEPTED status blocked

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACCEPTED proposal exists
**When** James sends `PUT /api/proposals/{id}` with updated title
**Then** the response status is 400
**And** the error detail contains "Cannot edit proposal in status ACCEPTED"

### PROP-007: Delete proposal (Owner only, non-DRAFT)

**Severity:** High
**Actor:** Thandi Nkosi (Owner)

**Given** a SENT proposal exists
**When** James (Admin) sends `DELETE /api/proposals/{id}`
**Then** the response status is 403 (only Owner can delete non-DRAFT proposals)
**When** Thandi (Owner) sends `DELETE /api/proposals/{id}`
**Then** the response status is 204

### PROP-008: Delete DRAFT proposal by Admin

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal exists
**When** James sends `DELETE /api/proposals/{id}`
**Then** the response status is 204 (Admin can delete; endpoint requires ORG_OWNER role per controller)

**Note:** The controller uses `@PreAuthorize("hasRole('ORG_OWNER')")` for DELETE, so Admins cannot delete proposals at all. This test verifies the RBAC enforcement.

**Corrected expectation:**
**When** James (Admin) sends `DELETE /api/proposals/{id}`
**Then** the response status is 403

### PROP-009: Proposal numbering is sequential per org

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** no proposals exist yet in the org
**When** James creates three proposals in sequence
**Then** the proposal numbers are sequential (e.g., "PROP-001", "PROP-002", "PROP-003")
**And** deleting a proposal does NOT recycle its number

### PROP-010: Create proposal with missing required fields rejected

**Severity:** Medium
**Actor:** James Chen (Admin)

**When** James sends `POST /api/proposals` with missing `title`
**Then** the response status is 400 with "title is required"
**When** James sends `POST /api/proposals` with missing `customerId`
**Then** the response status is 400 with "customerId is required"
**When** James sends `POST /api/proposals` with missing `feeModel`
**Then** the response status is 400 with "feeModel is required"

### PROP-011: List proposals with filters

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** proposals exist for multiple customers in various statuses
**When** James sends `GET /api/proposals?customerId={acmeId}&status=DRAFT`
**Then** only DRAFT proposals for Acme Corp are returned
**When** James sends `GET /api/proposals?feeModel=RETAINER`
**Then** only RETAINER proposals are returned

### PROP-012: List proposals for specific customer

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** Acme Corp has 3 proposals
**When** James sends `GET /api/customers/{acmeId}/proposals`
**Then** the response contains a paginated list of 3 proposals scoped to Acme Corp

---

## 2. Proposal Lifecycle

### PROP-020: Send proposal (DRAFT to SENT)

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal exists for Acme Corp
**And** portal contact "alice.porter@acmecorp.com" is linked to Acme Corp
**When** James sends `POST /api/proposals/{id}/send` with body `{"portalContactId": "{aliceId}"}`
**Then** the response status is 200
**And** `status` = "SENT"
**And** `sentAt` is populated
**And** `portalContactId` equals alice's ID

### PROP-021: Send proposal without portal contact rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal exists
**When** James sends `POST /api/proposals/{id}/send` with missing `portalContactId`
**Then** the response status is 400 with "portalContactId is required"

### PROP-022: Send already-SENT proposal blocked

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a SENT proposal exists
**When** James sends `POST /api/proposals/{id}/send` again
**Then** the response status is 400
**And** the error detail contains "Cannot mark as sent proposal in status SENT"

### PROP-023: Withdraw proposal (SENT to DRAFT)

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a SENT proposal exists
**When** James sends `POST /api/proposals/{id}/withdraw`
**Then** the response status is 200
**And** `status` = "DRAFT"
**And** `sentAt` is cleared to null
**And** `portalContactId` is cleared to null
**And** the proposal is now editable again

### PROP-024: Withdraw non-SENT proposal blocked

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal exists
**When** James sends `POST /api/proposals/{id}/withdraw`
**Then** the response status is 400
**And** the error detail contains "Cannot withdraw proposal in status DRAFT"

### PROP-025: Accept proposal (SENT to ACCEPTED) via portal

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT proposal exists addressed to alice
**When** alice sends `POST /portal/api/proposals/{id}/accept`
**Then** the response status is 200
**And** the proposal status changes to ACCEPTED
**And** `acceptedAt` is populated
**And** an audit event `proposal.accepted` is logged

### PROP-026: Decline proposal (SENT to DECLINED) via portal with reason

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT proposal exists addressed to alice
**When** alice sends `POST /portal/api/proposals/{id}/decline` with body `{"reason": "Budget constraints"}`
**Then** the response status is 200
**And** the proposal status changes to DECLINED
**And** `declinedAt` is populated
**And** `declineReason` = "Budget constraints"

### PROP-027: Decline proposal without reason

**Severity:** Medium
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT proposal exists addressed to alice
**When** alice sends `POST /portal/api/proposals/{id}/decline` with empty body or `{"reason": null}`
**Then** the response status is 200
**And** the proposal status changes to DECLINED
**And** `declineReason` is null

### PROP-028: Proposal expiry (SENT to EXPIRED)

**Severity:** High
**Actor:** System (ProposalExpiryProcessor)

**Given** a SENT proposal exists with `expiresAt` set to a past timestamp
**When** the expiry processor runs
**Then** the proposal status changes to EXPIRED
**And** a `ProposalExpiredEvent` is published
**And** the proposal is no longer actionable

### PROP-029: Accept already-ACCEPTED proposal blocked

**Severity:** Medium
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** an ACCEPTED proposal exists
**When** alice sends `POST /portal/api/proposals/{id}/accept`
**Then** the response status is 400
**And** the error detail contains "Cannot mark as accepted proposal in status ACCEPTED"

### PROP-030: Accept EXPIRED proposal blocked

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** an EXPIRED proposal exists
**When** alice sends `POST /portal/api/proposals/{id}/accept`
**Then** the response status is 400
**And** the error detail contains "Cannot mark as accepted proposal in status EXPIRED"

---

## 3. Proposal Content

### PROP-040: Set Tiptap JSON content on proposal

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal exists
**When** James sends `PUT /api/proposals/{id}` with `contentJson` containing a valid Tiptap document structure
**Then** the response status is 200
**And** `contentJson` is stored as the provided JSON object

### PROP-041: Replace milestones on proposal

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT FIXED-fee proposal exists
**When** James sends `PUT /api/proposals/{id}/milestones` with body:
```json
[
  {"description": "Design completed", "percentage": 30, "relativeDueDays": 30, "sortOrder": 1},
  {"description": "Development completed", "percentage": 50, "relativeDueDays": 60, "sortOrder": 2},
  {"description": "Final delivery", "percentage": 20, "relativeDueDays": 90, "sortOrder": 3}
]
```
**Then** the response status is 200
**And** the response contains 3 milestones with correct percentages summing to 100
**And** each milestone has a valid `sortOrder`

### PROP-042: Replace milestones on SENT proposal blocked

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a SENT proposal exists
**When** James sends `PUT /api/proposals/{id}/milestones`
**Then** the response status is 400 (proposal must be editable)

### PROP-043: Assign team members to proposal

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal exists
**And** team members Sofia and Aiden are org members
**When** James sends `PUT /api/proposals/{id}/team` with body:
```json
[
  {"memberId": "{sofiaId}", "role": "Lead", "sortOrder": 1},
  {"memberId": "{aidenId}", "role": "Support", "sortOrder": 2}
]
```
**Then** the response status is 200
**And** the response contains 2 team members with correct roles and sort orders

### PROP-044: Replace team members overwrites previous assignments

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT proposal has team members Sofia and Aiden assigned
**When** James sends `PUT /api/proposals/{id}/team` with only Marcus
**Then** the response contains 1 team member (Marcus)
**And** Sofia and Aiden are no longer assigned

### PROP-045: Set project template on proposal

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a project template "Standard Engagement" exists
**And** a DRAFT proposal exists
**When** James sends `PUT /api/proposals/{id}` with `projectTemplateId` set to the template ID
**Then** the `projectTemplateId` is stored on the proposal

---

## 4. Portal Proposal Flow

### PROP-050: Portal contact lists their proposals

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** alice is authenticated with a portal JWT
**And** Acme Corp has 2 proposals: one SENT (addressed to alice) and one DRAFT
**When** alice sends `GET /portal/api/proposals`
**Then** the response contains only the SENT proposal (DRAFT not visible to portal)

### PROP-051: Portal contact views proposal detail

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT proposal exists addressed to alice
**When** alice sends `GET /portal/api/proposals/{id}`
**Then** the response contains the proposal title, content, fee details, milestones, and team
**And** internal fields (createdById, audit history) are NOT exposed

### PROP-052: Portal contact cannot view another customer's proposal

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** alice is authenticated for Acme Corp
**And** a SENT proposal exists for Dunbar & Associates
**When** alice sends `GET /portal/api/proposals/{dunbarProposalId}`
**Then** the response status is 404 (customer ID mismatch)

### PROP-053: Portal contact mismatch on accept blocked

**Severity:** Critical
**Actor:** ben.finance@acmecorp.com (Portal Contact, BILLING role)

**Given** a SENT proposal exists addressed to alice (PRIMARY)
**When** ben (a different portal contact for the same customer) sends `POST /portal/api/proposals/{id}/accept`
**Then** the response status is 400
**And** the error detail contains "Portal contact does not match"

---

## 5. Project Auto-Creation

### PROP-054: Accepted FIXED proposal creates project

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT FIXED-fee proposal exists with title "Website Redesign Phase 1" for Acme Corp
**And** the proposal has 2 team members assigned
**When** alice accepts the proposal via `POST /portal/api/proposals/{id}/accept`
**Then** a new project is created with name matching the proposal title
**And** `createdProjectId` is set on the proposal
**And** the project's `customerId` matches Acme Corp's ID
**And** the 2 team members are assigned to the new project

### PROP-055: Accepted FIXED proposal with milestones creates invoices

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT FIXED-fee proposal exists with:
- `fixedFeeAmount` = 100000.00
- 3 milestones: 30% (due +30 days), 50% (due +60 days), 20% (due +90 days)
**When** alice accepts the proposal
**Then** 3 draft invoices are created:
- Invoice 1: amount = 30000.00, due date = today + 30 days
- Invoice 2: amount = 50000.00, due date = today + 60 days
- Invoice 3: amount = 20000.00, due date = today + 90 days
**And** each milestone's `invoiceId` is linked to the corresponding invoice

### PROP-056: Accepted FIXED proposal without milestones creates single invoice

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT FIXED-fee proposal with `fixedFeeAmount` = 150000.00 and NO milestones
**When** alice accepts the proposal
**Then** a single draft invoice is created with amount = 150000.00 and due date = today + 30 days

### PROP-057: Accepted HOURLY proposal creates project only (no billing entities)

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT HOURLY proposal exists
**When** alice accepts the proposal
**Then** a project is created
**And** NO invoices or retainer agreements are created
**And** `createdRetainerId` is null on the proposal

### PROP-058: Accepted proposal with project template instantiates template

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT proposal exists with `projectTemplateId` set to a template with predefined tasks and task groups
**When** alice accepts the proposal
**Then** the created project includes the template's tasks and task groups
**And** `createdProjectId` is set on the proposal

### PROP-059: Proposal acceptance transitions PROSPECT customer to ONBOARDING

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** customer "Bright Solutions" has lifecycle status PROSPECT
**And** a SENT proposal exists for "Bright Solutions" addressed to a portal contact
**When** the portal contact accepts the proposal
**Then** "Bright Solutions" lifecycle status transitions from PROSPECT to ONBOARDING
**And** the project is created successfully (lifecycle guard no longer blocks, since customer is now ONBOARDING)

### PROP-060: Proposal acceptance for ACTIVE customer does not change lifecycle

**Severity:** Medium
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** customer "Acme Corp" has lifecycle status ACTIVE
**And** a SENT proposal exists for Acme Corp
**When** alice accepts the proposal
**Then** Acme Corp lifecycle status remains ACTIVE (no transition needed)

---

## 6. Retainer Auto-Creation

### PROP-061: Accepted RETAINER proposal creates retainer agreement

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT RETAINER proposal exists with:
- `retainerAmount` = 25000.00
- `retainerCurrency` = "ZAR"
- `retainerHoursIncluded` = 40
**When** alice accepts the proposal
**Then** a retainer agreement is created with:
- `customerId` matching the proposal's customer
- `name` = "Retainer -- {proposal title}"
- `type` = HOUR_BANK
- `frequency` = MONTHLY
- `allocatedHours` = 40
- `periodFee` = 25000.00
- `rolloverPolicy` = FORFEIT
- `status` = ACTIVE
- `startDate` = today
**And** `createdRetainerId` is set on the proposal
**And** a project is also created (`createdProjectId` is set)

### PROP-062: Retainer agreement has first period auto-created

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a retainer agreement was created from proposal acceptance
**When** James sends `GET /api/retainers/{id}/periods/current`
**Then** an OPEN period exists with:
- `periodStart` = the retainer's start date
- `periodEnd` = start date + 1 month (MONTHLY frequency)
- `allocatedHours` = 40
- `consumedHours` = 0
- `remainingHours` = 40
- `rolloverHoursIn` = 0

---

## 7. Retainer CRUD & Lifecycle

### RET-001: Create retainer agreement manually

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James sends `POST /api/retainers` with body:
```json
{
  "customerId": "{acmeId}",
  "name": "Quarterly Advisory Retainer",
  "type": "HOUR_BANK",
  "frequency": "QUARTERLY",
  "startDate": "2026-04-01",
  "endDate": "2027-03-31",
  "allocatedHours": 120,
  "periodFee": 75000.00,
  "rolloverPolicy": "CARRY_FORWARD",
  "rolloverCapHours": null,
  "notes": "Includes strategic advisory"
}
```
**Then** the response status is 201
**And** the retainer has `status` = "ACTIVE"
**And** all fields match the request

### RET-002: Create FIXED_FEE retainer

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James creates a retainer with `type: "FIXED_FEE"`, `frequency: "MONTHLY"`, `periodFee: 50000.00`, and `allocatedHours: null`
**Then** the response status is 201
**And** `type` = "FIXED_FEE"
**And** `allocatedHours` is null

### RET-003: Update retainer terms

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer exists
**When** James sends `PUT /api/retainers/{id}` with updated `name`, `allocatedHours`, `periodFee`, and `rolloverPolicy`
**Then** the response status is 200
**And** the updated fields are reflected

### RET-004: Pause an ACTIVE retainer

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer exists
**When** James sends `POST /api/retainers/{id}/pause`
**Then** the response status is 200
**And** `status` = "PAUSED"

### RET-005: Resume a PAUSED retainer

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a PAUSED retainer exists
**When** James sends `POST /api/retainers/{id}/resume`
**Then** the response status is 200
**And** `status` = "ACTIVE"

### RET-006: Terminate a retainer

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer exists
**When** James sends `POST /api/retainers/{id}/terminate`
**Then** the response status is 200
**And** `status` = "TERMINATED"

### RET-007: Terminate a PAUSED retainer

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a PAUSED retainer exists
**When** James sends `POST /api/retainers/{id}/terminate`
**Then** the response status is 200
**And** `status` = "TERMINATED"

### RET-008: Pause a non-ACTIVE retainer blocked

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a PAUSED retainer exists
**When** James sends `POST /api/retainers/{id}/pause`
**Then** the response status is 400 (or 500 with "Only active retainers can be paused")

### RET-009: Resume a non-PAUSED retainer blocked

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer exists
**When** James sends `POST /api/retainers/{id}/resume`
**Then** the response status is 400 (or 500 with "Only paused retainers can be resumed")

### RET-010: Terminate an already-TERMINATED retainer blocked

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a TERMINATED retainer exists
**When** James sends `POST /api/retainers/{id}/terminate`
**Then** the response status is 400 (or 500 with "Retainer is already terminated")

### RET-011: List retainers with status filter

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** retainers exist in ACTIVE, PAUSED, and TERMINATED statuses
**When** James sends `GET /api/retainers?status=ACTIVE`
**Then** only ACTIVE retainers are returned

### RET-012: List retainers by customer

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** Acme Corp has 2 retainers, Dunbar has 1
**When** James sends `GET /api/retainers?customerId={acmeId}`
**Then** only retainers for Acme Corp are returned

---

## 8. Retainer Periods

### RET-020: First period auto-created on retainer creation

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James creates a MONTHLY retainer starting 2026-04-01 with allocatedHours = 40
**When** James sends `GET /api/retainers/{id}/periods/current`
**Then** the current period has:
- `status` = "OPEN"
- `periodStart` = 2026-04-01
- `periodEnd` = 2026-05-01
- `allocatedHours` = 40
- `baseAllocatedHours` = 40
- `rolloverHoursIn` = 0
- `consumedHours` = 0
- `remainingHours` = 40

### RET-021: Period consumption updates on time entry

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer with an OPEN period having `allocatedHours` = 40 and `consumedHours` = 0
**When** a time entry of 8 hours is logged against a project linked to the retainer's customer
**And** the consumption listener processes the event
**Then** the period `consumedHours` = 8
**And** `remainingHours` = 32

### RET-022: Close period with FORFEIT rollover

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer with `rolloverPolicy` = FORFEIT
**And** the current period has `allocatedHours` = 40, `consumedHours` = 30
**When** James sends `POST /api/retainers/{id}/periods/current/close`
**Then** the period `status` = "CLOSED"
**And** `closedAt` is populated
**And** `closedBy` equals James's member ID
**And** `remainingHours` = 10
**And** `rolloverHoursOut` = 0 (forfeited)
**And** a new OPEN period is created with `rolloverHoursIn` = 0

### RET-023: Close period with CARRY_FORWARD rollover

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer with `rolloverPolicy` = CARRY_FORWARD
**And** the current period has `allocatedHours` = 40, `consumedHours` = 25
**When** James sends `POST /api/retainers/{id}/periods/current/close`
**Then** the closed period has `rolloverHoursOut` = 15
**And** a new OPEN period is created with:
- `rolloverHoursIn` = 15
- `allocatedHours` = 55 (base 40 + rollover 15)
- `baseAllocatedHours` = 40
- `remainingHours` = 55

### RET-024: Close period with CARRY_CAPPED rollover

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer with `rolloverPolicy` = CARRY_CAPPED and `rolloverCapHours` = 10
**And** the current period has `allocatedHours` = 40, `consumedHours` = 20 (unused = 20)
**When** James sends `POST /api/retainers/{id}/periods/current/close`
**Then** the closed period has `rolloverHoursOut` = 10 (capped at rolloverCapHours, not full 20)
**And** a new OPEN period is created with `rolloverHoursIn` = 10
**And** `allocatedHours` = 50 (base 40 + capped rollover 10)

### RET-025: Close period with overage hours

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an HOUR_BANK retainer with the current period having `allocatedHours` = 40 and `consumedHours` = 55
**When** James closes the current period
**Then** `overageHours` = 15
**And** `remainingHours` = 0
**And** `rolloverHoursOut` = 0 (nothing to roll over when in overage)

### RET-026: Cannot close an already-closed period

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a period with status CLOSED
**When** James attempts to close it again
**Then** the response fails with "Period is already closed"

### RET-027: Cannot update consumption on closed period

**Severity:** High
**Actor:** System

**Given** a period with status CLOSED
**When** the system attempts to update consumption
**Then** an error is thrown: "Cannot update consumption on a closed period"

### RET-028: List periods with pagination

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a retainer with 5 closed periods and 1 open period
**When** James sends `GET /api/retainers/{id}/periods?page=0&size=3`
**Then** the response is paginated with 3 periods and total elements = 6

### RET-029: FIXED_FEE retainer period has null allocatedHours

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a FIXED_FEE retainer
**When** James views the current period
**Then** `allocatedHours` is null
**And** `remainingHours` = 0
**And** `consumedHours` tracks hours logged (informational, not capped)

### RET-030: Period dates follow frequency

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a QUARTERLY retainer starting 2026-01-01
**When** James views the current period
**Then** `periodStart` = 2026-01-01 and `periodEnd` = 2026-04-01
**When** the period is closed and a new one opens
**Then** the new period has `periodStart` = 2026-04-01 and `periodEnd` = 2026-07-01

---

## 9. Retainer Summary

### RET-040: Customer retainer summary

**Severity:** High
**Actor:** James Chen (Admin)

**Given** customer "Acme Corp" has 1 ACTIVE retainer with allocatedHours = 40 and consumedHours = 25 in the current period
**When** James sends `GET /api/customers/{acmeId}/retainer-summary`
**Then** the response includes:
- Total active retainers count
- Current period summary (allocated, consumed, remaining hours)
- Retainer agreement details

### RET-041: Customer with no retainers returns empty summary

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** a customer with no retainer agreements
**When** James sends `GET /api/customers/{id}/retainer-summary`
**Then** the response returns an empty/default summary (no error)

---

## 10. Proposal Stats

### PROP-070: Proposal stats with pipeline counts

**Severity:** Medium
**Actor:** Thandi Nkosi (Owner)

**Given** the org has proposals in various statuses: 3 DRAFT, 2 SENT, 4 ACCEPTED, 1 DECLINED, 1 EXPIRED
**When** Thandi sends `GET /api/proposals/stats`
**Then** the response includes:
- Count by status (draft: 3, sent: 2, accepted: 4, declined: 1, expired: 1)
- Conversion rate calculation (accepted / (accepted + declined + expired) = 4/6 = 66.67%)

### PROP-071: Stats with no proposals returns zeros

**Severity:** Low
**Actor:** Thandi Nkosi (Owner)

**Given** no proposals exist in the org
**When** Thandi sends `GET /api/proposals/stats`
**Then** the response returns all counts as 0 and conversion rate as 0 (no division by zero)

---

## 11. RBAC

### PROP-080: Admin can create proposals

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James sends `POST /api/proposals` with valid data
**Then** the response status is 201

### PROP-081: Member cannot create proposals

**Severity:** Critical
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `POST /api/proposals` with valid data
**Then** the response status is 403

### PROP-082: Member can view proposals

**Severity:** High
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `GET /api/proposals`
**Then** the response status is 200
**When** Lerato sends `GET /api/proposals/{id}`
**Then** the response status is 200

### PROP-083: Member cannot send or withdraw proposals

**Severity:** High
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `POST /api/proposals/{id}/send`
**Then** the response status is 403
**When** Lerato sends `POST /api/proposals/{id}/withdraw`
**Then** the response status is 403

### PROP-084: Only Owner can delete proposals

**Severity:** High
**Actor:** James Chen (Admin), Thandi Nkosi (Owner)

**Given** a DRAFT proposal exists
**When** James (Admin) sends `DELETE /api/proposals/{id}`
**Then** the response status is 403
**When** Thandi (Owner) sends `DELETE /api/proposals/{id}`
**Then** the response status is 204

### PROP-085: Member cannot view proposal stats

**Severity:** Medium
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `GET /api/proposals/stats`
**Then** the response status is 403

### RET-050: Admin can create and manage retainers

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James is authenticated as Admin
**When** James creates, pauses, resumes, and terminates a retainer
**Then** all operations succeed with status 200/201

### RET-051: Member cannot create retainers

**Severity:** High
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `POST /api/retainers` with valid data
**Then** the response status is 403

### RET-052: Member can view retainers and periods

**Severity:** Medium
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `GET /api/retainers/{id}`
**Then** the response status is 200
**When** Lerato sends `GET /api/retainers/{id}/periods`
**Then** the response status is 200
**When** Lerato sends `GET /api/retainers/{id}/periods/current`
**Then** the response status is 200

### RET-053: Member cannot close periods

**Severity:** High
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `POST /api/retainers/{id}/periods/current/close`
**Then** the response status is 403

### RET-054: Member cannot list retainers (list requires Admin+)

**Severity:** Medium
**Actor:** Lerato Dlamini (Member)

**Given** Lerato is authenticated as Member
**When** Lerato sends `GET /api/retainers`
**Then** the response status is 403

### PROP-086: Portal contact can only accept/decline proposals addressed to them

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** alice is authenticated with a portal JWT
**When** alice sends `POST /portal/api/proposals/{id}/accept` for a proposal addressed to her
**Then** the response status is 200
**When** alice sends `POST /portal/api/proposals/{id}/accept` for a proposal addressed to a different portal contact
**Then** the response status is 400 with "Portal contact does not match"

---

## 12. Edge Cases

### PROP-090: Proposal for PROSPECT customer triggers lifecycle transition on acceptance

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** customer "Bright Solutions" has lifecycle status PROSPECT
**And** a SENT proposal exists for "Bright Solutions"
**When** the proposal is accepted via portal
**Then** the orchestration service:
1. Marks the proposal ACCEPTED
2. Transitions "Bright Solutions" from PROSPECT to ONBOARDING
3. Creates the project (now permitted because customer is ONBOARDING)
4. Assigns team members
5. Creates billing entities (if applicable)
**And** "Bright Solutions" lifecycle status is now ONBOARDING

### PROP-091: Proposal number uniqueness across deleted proposals

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** proposals PROP-001 and PROP-002 exist
**When** Thandi (Owner) deletes PROP-002
**And** James creates a new proposal
**Then** the new proposal gets PROP-003 (not PROP-002; numbers are never recycled)

### PROP-092: Expired proposal cannot be accepted

**Severity:** High
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT proposal with `expiresAt` = yesterday
**And** the expiry processor has run and set status to EXPIRED
**When** alice sends `POST /portal/api/proposals/{id}/accept`
**Then** the response status is 400
**And** the error contains "Cannot mark as accepted proposal in status EXPIRED"

### PROP-093: Orchestration failure rolls back entire transaction

**Severity:** Critical
**Actor:** alice.porter@acmecorp.com (Portal Contact)

**Given** a SENT RETAINER proposal exists
**And** an error condition will cause retainer creation to fail (e.g., database constraint)
**When** alice accepts the proposal
**Then** the entire transaction rolls back:
- Proposal status remains SENT (not ACCEPTED)
- No project is created
- No retainer agreement is created
- Customer lifecycle is not transitioned
**And** a `ProposalOrchestrationFailedEvent` is published

### RET-060: Terminate retainer with open period

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an ACTIVE retainer with an OPEN period having `consumedHours` = 15
**When** James terminates the retainer
**Then** `status` = "TERMINATED"
**But** the open period remains OPEN (it is not auto-closed)
**And** no new periods will be created going forward

### RET-061: Close period creates next period with correct frequency dates

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a MONTHLY retainer with current period 2026-03-01 to 2026-04-01
**When** James closes the current period
**Then** the new period has `periodStart` = 2026-04-01 and `periodEnd` = 2026-05-01

**Given** a QUARTERLY retainer with current period 2026-01-01 to 2026-04-01
**When** James closes the current period
**Then** the new period has `periodStart` = 2026-04-01 and `periodEnd` = 2026-07-01

**Given** an ANNUALLY retainer with current period 2026-01-01 to 2027-01-01
**When** James closes the current period
**Then** the new period has `periodStart` = 2027-01-01 and `periodEnd` = 2028-01-01

### RET-062: Carry-forward with zero remaining hours

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a CARRY_FORWARD retainer with current period `allocatedHours` = 40 and `consumedHours` = 45 (overage)
**When** James closes the current period
**Then** `rolloverHoursOut` = 0 (nothing to carry forward when in overage)
**And** the new period has `rolloverHoursIn` = 0 and `allocatedHours` = base hours only

### RET-063: Ready-to-close notification check on retainer list

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** retainer periods exist that have passed their `periodEnd` date but are still OPEN
**When** James sends `GET /api/retainers`
**Then** the system triggers `checkAndNotifyReadyToClose()` to notify admins about periods needing closure

### PROP-094: Concurrent proposal acceptance

**Severity:** Medium
**Actor:** alice.porter@acmecorp.com, ben.finance@acmecorp.com (Portal Contacts)

**Given** a SENT proposal addressed to alice exists
**When** alice and another authorized contact simultaneously send accept requests
**Then** exactly one succeeds and the other fails with a state conflict
**And** only one project and one set of billing entities are created

### PROP-095: Proposal with all frequency options for retainer

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** James creates retainers with each frequency: WEEKLY, FORTNIGHTLY, MONTHLY, QUARTERLY, SEMI_ANNUALLY, ANNUALLY
**When** James views the current period for each
**Then** period end dates match the expected calculation:
- WEEKLY: start + 1 week
- FORTNIGHTLY: start + 2 weeks
- MONTHLY: start + 1 month
- QUARTERLY: start + 3 months
- SEMI_ANNUALLY: start + 6 months
- ANNUALLY: start + 1 year
