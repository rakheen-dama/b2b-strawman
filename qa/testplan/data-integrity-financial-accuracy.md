# Test Plan: Data Integrity & Financial Accuracy
## DocTeams Platform — State Machines, Rate Resolution, Invoice Math, Audit Completeness

**Version**: 1.0
**Date**: 2026-03-18
**Author**: Product + QA
**Vertical**: accounting-za (Thornton & Associates)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025). See `qa/keycloak-e2e-guide.md` for setup.
**Depends on**: Phase 49 test plan T0 (seed data)

---

## 1. Purpose

The platform handles money, compliance status, and legal documents. If the state machines
allow invalid transitions, the rate hierarchy picks the wrong rate, the invoice math rounds
incorrectly, or the audit trail has gaps — the firm makes decisions on wrong data, sends
incorrect invoices, or fails a compliance review.

This plan tests the **integrity guarantees** the platform makes: that lifecycle guards can't
be bypassed, rate snapshots are deterministic, invoice arithmetic is correct to the cent,
and every significant action leaves a trace.

**Core question**: Can Thornton & Associates trust the numbers, the statuses, and the
audit trail this platform produces?

## 2. Scope

| Track | Focus | Checkpoints |
|-------|-------|-------------|
| T1 | State machine integrity — lifecycle guards and invalid transition rejection | ~35 |
| T2 | Rate hierarchy resolution and snapshot accuracy | ~25 |
| T3 | Invoice arithmetic — line items, tax, rounding, void, partial payment | ~30 |
| T4 | Audit trail completeness — action matrix, actor, timestamp, detail | ~35 |

## 3. Prerequisites

### 3.1 Shared Seed Data

Same as Phase 49 T0 — Thornton & Associates 90-day lifecycle seeded with custom field values.

**Additional requirements for this plan:**

| Requirement | Purpose |
|-------------|---------|
| At least 1 customer in PROSPECT status | T1 invalid transition testing |
| At least 1 invoice in DRAFT status | T1 + T3 invoice lifecycle testing |
| At least 1 invoice in SENT status | T3 payment testing |
| Multiple billing rates at different levels (org, project) | T2 hierarchy testing |
| Mailpit accessible | T4 notification audit cross-reference |

### 3.2 Notation

- [ ] **PASS** — system enforced the constraint correctly
- [ ] **FAIL** — constraint bypassed, wrong data accepted, or wrong result produced
- [ ] **REJECTED** — system correctly rejected an invalid action (expected behaviour)
- [ ] **MATH_OK** — arithmetic verified correct
- [ ] **MATH_ERR** — arithmetic wrong (note expected vs actual)
- [ ] **AUDIT_OK** — audit event present with correct data
- [ ] **AUDIT_MISSING** — expected audit event not found

---

## 4. Test Tracks

---

### Track 1 — State Machine Integrity

**Goal**: For every lifecycle entity, attempt invalid state transitions and verify the
system rejects them. Then verify valid transitions work and produce correct state.

The platform has 7 state machines. We test the 4 that handle money or compliance:
Customer, Invoice, Proposal, Task/Project.

#### T1.1 — Customer Lifecycle Guards

**Valid states**: PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDING → OFFBOARDED

**Valid transition tests:**

- [ ] **T1.1.1** PROSPECT → ONBOARDING: transition a PROSPECT customer → verify status = ONBOARDING
- [ ] **T1.1.2** ONBOARDING → ACTIVE: complete all FICA checklist items → verify auto-transition to ACTIVE
- [ ] **T1.1.3** ACTIVE → DORMANT: verify this transition is available (may be manual or scheduled)
- [ ] **T1.1.4** DORMANT → ACTIVE: reactivate a dormant customer → verify status = ACTIVE
- [ ] **T1.1.5** ACTIVE → OFFBOARDING → OFFBOARDED: verify the full exit path works

**Invalid transition tests (expect rejection):**

- [ ] **T1.1.6** PROSPECT → ACTIVE (skip ONBOARDING): attempt via API `PUT /api/customers/{id}/lifecycle` with `newStatus=ACTIVE` → expect HTTP 400 or 409
- [ ] **T1.1.7** ONBOARDING → ACTIVE (incomplete checklist): attempt transition with unchecked FICA items → expect rejection with message about incomplete onboarding
- [ ] **T1.1.8** OFFBOARDED → ONBOARDING: attempt → expect rejection (only OFFBOARDED → ACTIVE is valid)
- [ ] **T1.1.9** PROSPECT → DORMANT: attempt → expect rejection (DORMANT only reachable from ACTIVE)
- [ ] **T1.1.10** ACTIVE → PROSPECT: attempt backward transition → expect rejection

**Lifecycle action guards:**

- [ ] **T1.1.11** PROSPECT customer: attempt to create a project → expect rejection ("Lifecycle action blocked")
- [ ] **T1.1.12** PROSPECT customer: attempt to create an invoice → expect rejection
- [ ] **T1.1.13** PROSPECT customer: attempt to create a time entry → expect rejection
- [ ] **T1.1.14** ACTIVE customer: create a project → expect success (guard allows it)
- [ ] **T1.1.15** OFFBOARDED customer: attempt to create a project → expect rejection

---

#### T1.2 — Invoice Lifecycle Guards

**Valid states**: DRAFT → APPROVED → SENT → PAID; any non-PAID → VOID

**Valid transition tests:**

- [ ] **T1.2.1** DRAFT → APPROVED: approve an invoice → verify status = APPROVED
- [ ] **T1.2.2** APPROVED → SENT: send the invoice → verify status = SENT
- [ ] **T1.2.3** SENT → PAID: record payment → verify status = PAID
- [ ] **T1.2.4** DRAFT → VOID: void a draft invoice → verify status = VOID

**Invalid transition tests (expect HTTP 409 ResourceConflictException):**

- [ ] **T1.2.5** DRAFT → SENT (skip APPROVED): attempt `POST /api/invoices/{id}/send` on a DRAFT → expect 409
- [ ] **T1.2.6** DRAFT → PAID (skip APPROVED + SENT): attempt `POST /api/invoices/{id}/payments` on a DRAFT → expect 409
- [ ] **T1.2.7** APPROVED → PAID (skip SENT): attempt payment on APPROVED invoice → expect 409
- [ ] **T1.2.8** PAID → DRAFT: attempt to revert a paid invoice to draft → expect 409
- [ ] **T1.2.9** PAID → VOID: attempt to void a paid invoice → expect 409 (paid invoices should not be voidable — verify this is the rule)
- [ ] **T1.2.10** VOID → APPROVED: attempt to resurrect a voided invoice → expect 409

**Draft editing guard:**

- [ ] **T1.2.11** DRAFT invoice: edit line items → expect success
- [ ] **T1.2.12** APPROVED invoice: attempt to edit line items → expect rejection (only DRAFT allows editing)
- [ ] **T1.2.13** SENT invoice: attempt to edit line items → expect rejection

---

#### T1.3 — Proposal Lifecycle Guards

**Valid states**: DRAFT → SENT → ACCEPTED/DECLINED/EXPIRED

**Valid transition tests:**

- [ ] **T1.3.1** DRAFT → SENT: send a proposal → verify status = SENT
- [ ] **T1.3.2** SENT → ACCEPTED: portal contact accepts → verify status = ACCEPTED
- [ ] **T1.3.3** SENT → DECLINED: portal contact declines → verify status = DECLINED

**Invalid transition tests:**

- [ ] **T1.3.4** DRAFT → ACCEPTED: attempt acceptance on unsent proposal → expect rejection
- [ ] **T1.3.5** ACCEPTED → SENT: attempt to re-send an accepted proposal → expect rejection
- [ ] **T1.3.6** DECLINED → ACCEPTED: attempt to accept a declined proposal → expect rejection
- [ ] **T1.3.7** EXPIRED → ACCEPTED: attempt to accept an expired proposal → expect rejection (critical — an expired proposal should NOT be acceptable)

---

#### T1.4 — Task & Project Lifecycle Guards

**Task states**: OPEN → IN_PROGRESS → DONE; OPEN/IN_PROGRESS → CANCELLED; DONE/CANCELLED → OPEN

- [ ] **T1.4.1** OPEN → IN_PROGRESS → DONE: happy path → verify each transition
- [ ] **T1.4.2** DONE → IN_PROGRESS: attempt → expect rejection (DONE can only go to OPEN)
- [ ] **T1.4.3** CANCELLED → IN_PROGRESS: attempt → expect rejection (CANCELLED can only go to OPEN)
- [ ] **T1.4.4** IN_PROGRESS → DONE → OPEN (reopen): verify this valid path works

**Project states**: ACTIVE → COMPLETED → ARCHIVED

- [ ] **T1.4.5** ARCHIVED project: attempt to create a task → expect rejection ("Project is read-only")
- [ ] **T1.4.6** ARCHIVED project: attempt to log time → expect rejection
- [ ] **T1.4.7** ARCHIVED project: attempt to create a comment → expect rejection
- [ ] **T1.4.8** ARCHIVED → ACTIVE (unarchive): verify this works if allowed

---

#### T1.5 — Void Invoice Side Effects

**Goal**: Voiding an invoice should revert time entries and expenses to UNBILLED status.

- [ ] **T1.5.1** Create an invoice from unbilled time entries (2+ entries)
- [ ] **T1.5.2** Verify time entries now show billing status = BILLED (invoiceId is set)
- [ ] **T1.5.3** Void the invoice
- [ ] **T1.5.4** Verify time entries revert to UNBILLED (invoiceId cleared)
- [ ] **T1.5.5** Verify expenses (if any) revert to UNBILLED
- [ ] **T1.5.6** Verify the voided invoice still exists in the system (not deleted) with status = VOID
- [ ] **T1.5.7** Verify the voided invoice's line items are preserved (for audit trail)
- [ ] **T1.5.8** Verify the reverted time entries can be re-invoiced on a new invoice

---

### Track 2 — Rate Hierarchy Resolution & Snapshot Accuracy

**Goal**: Verify the 3-level rate hierarchy (org default → customer override → project override)
resolves correctly, and that rate snapshots on time entries are immutable once captured.

#### T2.1 — Rate Hierarchy: Org Default Only

**Setup**: Carol has an org-level billing rate of R450/hr. No project or customer overrides.

- [ ] **T2.1.1** Log time as Carol on any project → verify rate snapshot = R450
- [ ] **T2.1.2** Check time entry detail → `snapshotBillingRate` = 450.00, currency = ZAR
- [ ] **T2.1.3** Verify cost rate snapshot also captured (R200/hr for Carol)

#### T2.2 — Rate Hierarchy: Project Override Wins

**Setup**: Create a project-level billing rate for Carol on the Kgosi project = R500/hr.

- [ ] **T2.2.1** Navigate to the Kgosi project settings (or rate card settings) and add a project override for Carol: R500/hr
- [ ] **T2.2.2** Log time as Carol on the Kgosi project → verify rate snapshot = **R500** (project override, not org R450)
- [ ] **T2.2.3** Log time as Carol on the Naledi project (no project override) → verify rate snapshot = **R450** (org default)
- [ ] **T2.2.4** Confirm: the project override only applies to the specific project, not globally

#### T2.3 — Rate Hierarchy: Customer Override

**Setup**: Create a customer-level billing rate for Carol on Vukani = R475/hr.

- [ ] **T2.3.1** Add customer override for Carol on Vukani: R475/hr
- [ ] **T2.3.2** Log time as Carol on the Vukani project → verify rate snapshot:
  - If project also has an override → project override wins (higher specificity)
  - If project has NO override → customer override wins: **R475**
- [ ] **T2.3.3** Verify the resolution order: project override > customer override > org default

#### T2.4 — Rate Hierarchy: No Rate Found

**Setup**: Create a new team member (or find one without any billing rate configured).

- [ ] **T2.4.1** Log time for a member with no billing rate at any level
- [ ] **T2.4.2** Verify the system handles this gracefully:
  - Warning message shown? ("No rate card found")
  - Rate snapshot = R0.00 or null?
  - Time entry still created (not rejected)?
- [ ] **T2.4.3** Create an invoice including this zero-rate time entry → verify line item amount = R0.00
- [ ] **T2.4.4** Note: is this the desired behaviour, or should the system require a rate before allowing time logging?

#### T2.5 — Rate Snapshot Immutability

**Goal**: Once a time entry captures a rate snapshot, changing the rate card should NOT
retroactively change existing snapshots. Only new time entries use the new rate.

- [ ] **T2.5.1** Log time as Alice on Kgosi project → verify snapshot = R1,500
- [ ] **T2.5.2** Note the time entry ID and snapshot amount
- [ ] **T2.5.3** Change Alice's org billing rate from R1,500 to R1,800
- [ ] **T2.5.4** Check the EXISTING time entry → snapshot should still be **R1,500** (immutable)
- [ ] **T2.5.5** Log a NEW time entry as Alice → snapshot should be **R1,800** (new rate)
- [ ] **T2.5.6** This is critical for invoice accuracy: invoices generated from old time entries must use the rate that was active when the work was done, not the current rate

#### T2.6 — Rate Snapshot on Date Change

**Goal**: If a time entry's date is changed to a different period, the rate snapshot should
re-snapshot based on the new date (in case rates were different then).

- [ ] **T2.6.1** Alice has rate R1,500 effective from 2026-01-01
- [ ] **T2.6.2** Log time entry dated today → snapshot = R1,500
- [ ] **T2.6.3** Edit the time entry date to a date before the rate was effective (if such a date exists)
- [ ] **T2.6.4** Verify the rate snapshot updates to match the rate that was active on the new date
- [ ] **T2.6.5** If no rate existed on the old date → snapshot should become R0.00 or warn
- [ ] **T2.6.6** Note: this behaviour may or may not be implemented. Document what actually happens.

#### T2.7 — Multi-User Same Project Rate Correctness

**Goal**: Different team members on the same project get different rate snapshots.

- [ ] **T2.7.1** Log time as Alice on Kgosi project → snapshot = R1,500 (or current rate)
- [ ] **T2.7.2** Log time as Bob on same project → snapshot = R850
- [ ] **T2.7.3** Log time as Carol on same project → snapshot = R450 (or project override if set in T2.2)
- [ ] **T2.7.4** Verify all three time entries on the same project have DIFFERENT rate snapshots
- [ ] **T2.7.5** Generate an invoice for this project → verify line items use each member's snapshot rate, not a blended rate

---

### Track 3 — Invoice Arithmetic

**Goal**: Verify that invoice calculations are correct to the cent — line item totals,
subtotals, tax amounts, and grand totals. Test rounding edge cases and the void/re-invoice cycle.

#### T3.1 — Basic Line Item Calculation

- [ ] **T3.1.1** Create invoice with 1 line item: Description = "Bookkeeping", Qty = 3, Unit Price = R450 (Carol's rate)
- [ ] **T3.1.2** Verify line amount = R1,350.00 (3 × R450)
- [ ] **T3.1.3** Add tax: VAT 15%
- [ ] **T3.1.4** Verify tax amount = R202.50 (R1,350 × 15%)
- [ ] **T3.1.5** Verify total = R1,552.50 (R1,350 + R202.50)

#### T3.2 — Multiple Line Items

- [ ] **T3.2.1** Create invoice with 3 line items:
  - Line 1: "Tax consultation" Qty = 0.5, Price = R1,500 (Alice 30 min) → amount = R750.00
  - Line 2: "Bookkeeping" Qty = 2, Price = R450 (Carol) → amount = R900.00
  - Line 3: "Admin liaison" Qty = 1, Price = R850 (Bob) → amount = R850.00
- [ ] **T3.2.2** Verify subtotal = R2,500.00 (R750 + R900 + R850)
- [ ] **T3.2.3** Apply 15% VAT → tax = R375.00
- [ ] **T3.2.4** Verify total = R2,875.00

#### T3.3 — Rounding Edge Cases

**Goal**: Test amounts that produce non-terminating decimals when multiplied by 15%.

- [ ] **T3.3.1** Create line: Qty = 1, Price = R1,333.33
  - Tax = R1,333.33 × 15% = R199.9995 → rounded HALF_UP = **R200.00**
  - Total = R1,333.33 + R200.00 = **R1,533.33**
- [ ] **T3.3.2** Verify the platform produces exactly R200.00 tax and R1,533.33 total

- [ ] **T3.3.3** Create line: Qty = 1, Price = R99.99
  - Tax = R99.99 × 15% = R14.9985 → rounded HALF_UP = **R15.00**
  - Total = R99.99 + R15.00 = **R114.99**
- [ ] **T3.3.4** Verify exact amounts

- [ ] **T3.3.5** Create line: Qty = 3, Price = R333.33
  - Line amount = R999.99
  - Tax = R999.99 × 15% = R149.9985 → rounded = **R150.00**
  - Total = R999.99 + R150.00 = **R1,149.99**
- [ ] **T3.3.6** Verify exact amounts

#### T3.4 — Per-Line Tax vs Invoice-Level Tax

- [ ] **T3.4.1** Check: does the system calculate tax per line item or on the subtotal?
  - Per-line: each line's tax is rounded individually, then summed
  - Invoice-level: subtotal is computed first, then tax applied to subtotal
  - These can produce different results due to rounding
- [ ] **T3.4.2** Create invoice with 3 lines, each R333.33:
  - Per-line tax: 3 × R50.00 = R150.00 total tax
  - Invoice-level tax: R999.99 × 15% = R150.00 total tax
  - (In this case they match, but not always)
- [ ] **T3.4.3** Create invoice with 3 lines at R111.11:
  - Per-line tax: 3 × R16.67 = **R50.01** (each rounded up: R111.11 × 0.15 = R16.6665 → R16.67)
  - Invoice-level tax: R333.33 × 15% = R49.9995 → **R50.00**
  - If system uses per-line: total tax = R50.01. If invoice-level: R50.00.
- [ ] **T3.4.4** Document which method the system uses and verify it's consistent

#### T3.5 — Zero-Value and Edge Cases

- [ ] **T3.5.1** Create line with Qty = 0, Price = R1,500 → line amount should = R0.00
- [ ] **T3.5.2** Create line with Qty = 1, Price = R0.00 → line amount should = R0.00
- [ ] **T3.5.3** Create line with fractional quantity: Qty = 0.25, Price = R1,500 → amount = R375.00
- [ ] **T3.5.4** Create line with very small amount: Qty = 1, Price = R0.01 → tax = R0.00 (R0.01 × 15% = R0.0015 → R0.00)
- [ ] **T3.5.5** Create invoice with only tax-exempt lines → total tax = R0.00, total = subtotal

#### T3.6 — Retainer Invoice Arithmetic

- [ ] **T3.6.1** Create a retainer invoice: 1 line "Monthly Retainer — Kgosi", Qty = 1, Price = R5,500
- [ ] **T3.6.2** Add VAT 15% → tax = R825.00, total = R6,325.00
- [ ] **T3.6.3** If retainer has overage hours (e.g., 4 hours over the 10-hour bank):
  - Add overage line: Qty = 4, Price = R450 (Carol's rate) = R1,800
  - Retainer line: R5,500
  - Subtotal = R7,300
  - Tax = R1,095.00
  - Total = R8,395.00
- [ ] **T3.6.4** Verify calculations

#### T3.7 — Void and Re-Invoice Cycle

**Goal**: Void an invoice, verify entries revert, create a new invoice from the same entries,
verify the new invoice math is correct.

- [ ] **T3.7.1** Create invoice A from 3 time entries (Carol: 3h, Bob: 1h, Alice: 0.5h)
- [ ] **T3.7.2** Record expected amounts:
  - Carol: 3 × R450 = R1,350
  - Bob: 1 × R850 = R850
  - Alice: 0.5 × R1,500 = R750
  - Subtotal = R2,950, Tax = R442.50, Total = R3,392.50
- [ ] **T3.7.3** Verify invoice A has these exact amounts
- [ ] **T3.7.4** Approve and send invoice A
- [ ] **T3.7.5** Void invoice A → verify status = VOID
- [ ] **T3.7.6** Verify the 3 time entries are now UNBILLED again
- [ ] **T3.7.7** Create invoice B from the same 3 time entries
- [ ] **T3.7.8** Verify invoice B has the SAME amounts as invoice A (rate snapshots are immutable)
- [ ] **T3.7.9** Verify invoice A and invoice B have DIFFERENT invoice numbers

#### T3.8 — Payment Recording

- [ ] **T3.8.1** Send an invoice (SENT status)
- [ ] **T3.8.2** Record payment: reference = "EFT-2026-TEST-001"
- [ ] **T3.8.3** Verify invoice status = PAID
- [ ] **T3.8.4** Verify `paidAt` timestamp is set
- [ ] **T3.8.5** Verify `paymentReference` = "EFT-2026-TEST-001"
- [ ] **T3.8.6** Note: does the system support partial payments? (Expected: no — single full payment model)
  If partial payments are supported, test:
  - Pay R1,000 of a R3,000 invoice → status stays SENT, balance = R2,000
  - Pay remaining R2,000 → status = PAID, balance = R0

---

### Track 4 — Audit Trail Completeness

**Goal**: Verify that every significant action produces an audit event with correct actor,
timestamp, entity reference, and action-specific details. Test by performing actions and
then querying the audit log.

**Method**: Perform an action via the UI → query `GET /api/audit-events?entityType=X&entityId=Y`
→ verify the most recent event matches.

#### T4.1 — Customer Lifecycle Audit Events

- [ ] **T4.1.1** Create a new customer → query audit events for that customer
  - Expect: `customer.created` event
  - Details: customer name
  - Actor: current user (Bob)
  - Source: API
- [ ] **T4.1.2** Transition PROSPECT → ONBOARDING → query audit
  - Expect: `customer.lifecycle.transitioned`
  - Details: `oldStatus=PROSPECT`, `newStatus=ONBOARDING`
- [ ] **T4.1.3** Transition ONBOARDING → ACTIVE (via checklist completion) → query audit
  - Expect: `customer.lifecycle.transitioned`
  - Details: `oldStatus=ONBOARDING`, `newStatus=ACTIVE`

#### T4.2 — Invoice Lifecycle Audit Events

- [ ] **T4.2.1** Create invoice → expect `invoice.created`
  - Details: invoice_number, customer_name, currency
- [ ] **T4.2.2** Approve invoice → expect `invoice.approved`
  - Details: invoice_number
- [ ] **T4.2.3** Send invoice → expect `invoice.sent`
  - Details: invoice_number
- [ ] **T4.2.4** Record payment → expect `invoice.paid`
  - Details: invoice_number, payment_reference, total, paid_at
- [ ] **T4.2.5** Void an invoice → expect `invoice.voided`
  - Details: invoice_number, total, reverted_time_entry_count, reverted_expense_count
- [ ] **T4.2.6** Verify: voided invoice audit includes count of reverted entries (not just "voided")

#### T4.3 — Time Entry Audit Events

- [ ] **T4.3.1** Create time entry → expect `time_entry.created`
  - Details: project, duration, billable, rate snapshot
- [ ] **T4.3.2** Edit time entry (change duration) → expect `time_entry.updated`
  - Details: what changed
- [ ] **T4.3.3** Delete time entry → expect `time_entry.deleted`
  - Details: entry ID, project

#### T4.4 — Proposal Audit Events

- [ ] **T4.4.1** Create proposal → expect `proposal.created`
- [ ] **T4.4.2** Send proposal → expect `proposal.sent`
- [ ] **T4.4.3** Portal contact accepts → expect `proposal.accepted`
  - Actor type should be "SYSTEM" or portal contact (not org member)
  - Details: acceptance metadata

#### T4.5 — Document and Acceptance Audit Events

- [ ] **T4.5.1** Generate document → expect `document.created`
  - Details: template used, entity type, entity ID
- [ ] **T4.5.2** Send for acceptance → expect event logged
- [ ] **T4.5.3** Portal contact accepts → expect `document.accepted`
  - Details: acceptor name, timestamp, IP address

#### T4.6 — Rate and Billing Audit Events

- [ ] **T4.6.1** Create billing rate → expect `billing_rate.created`
  - Details: member, rate, currency, scope
- [ ] **T4.6.2** Update billing rate → expect `billing_rate.updated`
  - Details: old rate, new rate
- [ ] **T4.6.3** Delete billing rate → expect `billing_rate.deleted`

#### T4.7 — Project and Task Audit Events

- [ ] **T4.7.1** Create project → expect `project.created`
- [ ] **T4.7.2** Archive project → expect `project.archived`
- [ ] **T4.7.3** Create task → expect `task.created`
- [ ] **T4.7.4** Change task status → expect `task.status_changed`
  - Details: old status, new status
- [ ] **T4.7.5** Complete task → expect `task.completed`

#### T4.8 — Comment and Expense Audit Events

- [ ] **T4.8.1** Create comment → expect `comment.created`
  - Details: entity type (project/task), entity ID
- [ ] **T4.8.2** Edit comment → expect `comment.updated`
- [ ] **T4.8.3** Delete comment → expect `comment.deleted`
- [ ] **T4.8.4** Create expense → expect `expense.created`
  - Details: amount, category, billable, project
- [ ] **T4.8.5** Delete expense → expect `expense.deleted`

#### T4.9 — Role and Member Audit Events

- [ ] **T4.9.1** If role management is accessible: create/update role → expect `role.created`/`role.updated`
  - Details for update: addedCapabilities, removedCapabilities, affectedMemberCount
- [ ] **T4.9.2** Change member role → expect `member.role_changed`
  - Details: memberId, memberName, previousRole, newRole

#### T4.10 — Audit Event Field Correctness

For 5 randomly selected audit events from the above tests, verify:

- [ ] **T4.10.1** `actorId` matches the logged-in user's member ID
- [ ] **T4.10.2** `actorType` = "USER" (for user-initiated actions)
- [ ] **T4.10.3** `source` = "API"
- [ ] **T4.10.4** `occurredAt` is within the last 60 seconds of the action
- [ ] **T4.10.5** `entityType` and `entityId` point to the correct entity
- [ ] **T4.10.6** `details` JSONB contains the expected key-value pairs (not empty, not null)
- [ ] **T4.10.7** `ipAddress` is populated (not null — E2E requests come from localhost)

#### T4.11 — Audit Immutability

- [ ] **T4.11.1** Attempt to update an audit event via direct database query:
  `UPDATE audit_events SET event_type = 'tampered' WHERE id = ?`
  → expect rejection by DB trigger
- [ ] **T4.11.2** If testing via API: attempt `PUT /api/audit-events/{id}` → expect 405 Method Not Allowed or 404 (no update endpoint should exist)
- [ ] **T4.11.3** Note: this may only be testable via direct DB access in the E2E postgres container

#### T4.12 — System-Initiated vs User-Initiated

- [ ] **T4.12.1** Trigger an automated action (e.g., auto-dormancy, checklist auto-transition, automation rule)
- [ ] **T4.12.2** Query the audit event → verify `actorType` = "SYSTEM" (not "USER")
- [ ] **T4.12.3** Verify `actorId` is null or a system identifier (not a user member ID)

---

## 5. Verification Approach

### State Machine Testing (T1)

**Primary method**: Playwright performs the action via UI. If the UI prevents the action
(button not shown, option not available), also test via direct API call to verify backend
enforcement.

**API testing pattern** (via Playwright `page.evaluate`):
```javascript
const response = await fetch('/api/invoices/{id}/send', {
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' }
});
// Expect: 409 Conflict for invalid transitions
```

This matters because a missing button is a UI decision, but backend enforcement is the
security boundary. Both should agree, but if they don't, backend is the source of truth.

### Invoice Arithmetic (T3)

**Verification**: After creating each invoice, read the invoice detail (API or UI) and
compare actual amounts against hand-calculated expected amounts.

For each test case, record:
```
Expected: subtotal=R2,500.00, tax=R375.00, total=R2,875.00
Actual:   subtotal=R____.____, tax=R____.____, total=R____.____
Match:    YES / NO
```

### Audit Trail (T4)

**Query pattern**: After performing an action, query the audit API:
```
GET /api/audit-events?entityType=invoice&entityId={id}&sort=occurredAt,desc&size=1
```
Read the response and verify the event type, actor, and details.

---

## 6. Gap Reporting Format

Same structure as Phase 49. Categories specific to this plan:

| Category | Severity Guide |
|----------|---------------|
| state-bypass | **Blocker** — invalid state transition accepted |
| guard-bypass | **Blocker** — lifecycle guard allowed prohibited action |
| rate-wrong | **Major** — wrong rate snapshot captured (affects billing) |
| rate-missing | **Minor** — no rate found, but handled gracefully |
| math-error | **Major** — invoice arithmetic is incorrect |
| rounding-error | **Minor** if < R1.00 difference; **Major** if > R1.00 |
| audit-missing | **Major** — significant action has no audit event |
| audit-wrong | **Major** — audit event has wrong actor, entity, or details |
| audit-immutable | **Blocker** — audit events can be modified |
| void-incomplete | **Major** — void didn't revert all child entities |

**Severity override**: Any state-bypass or guard-bypass is automatically **blocker**.
Any audit-immutable failure is automatically **blocker**.

---

## 7. Success Criteria

| Criterion | Target |
|-----------|--------|
| All invalid state transitions rejected by backend | 100% |
| Lifecycle guards block prohibited actions for PROSPECT/OFFBOARDED customers | 100% |
| Invoice editing blocked for non-DRAFT invoices | 100% |
| Rate hierarchy resolves to most specific rate | 100% |
| Rate snapshots are immutable after capture | 100% |
| Invoice arithmetic correct for all test cases | 0 errors |
| Rounding uses HALF_UP consistently | 100% |
| Void reverts all child entities (time entries, expenses) | 100% |
| Audit event exists for every tested action | 100% |
| Audit events have correct actor, type, entity, details | 100% |
| Audit events are immutable (DB trigger blocks updates) | 100% |
| Zero state-bypass or guard-bypass gaps | 0 |

---

## 8. Execution Notes

### Execution Order

1. **T1.1-T1.3 — State Machine Tests**: Run first. If state machines are broken,
   invoice and audit tests may produce misleading results.
2. **T2 — Rate Hierarchy**: Independent of T1. Can run in parallel if two agents available.
3. **T3 — Invoice Arithmetic**: Depends on rate hierarchy being understood (T2).
   Uses invoices created with known rates.
4. **T4 — Audit Trail**: Run last. Every action from T1-T3 should have produced audit events.
   T4 can cross-reference actions performed in earlier tracks.

### API vs UI Testing

For state machine tests (T1), **always test both paths**:
1. UI path: is the invalid action's button/option hidden or disabled?
2. API path: does the backend reject the request with proper HTTP status?

A UI-only test is insufficient — a determined user or API caller can bypass the UI.

### Reusable Test Customer

Create a fresh "Test Integrity Customer" at the start of this plan for T1 lifecycle tests.
Don't pollute the Thornton & Associates seed data with invalid transition attempts.

### Database Access for T4.11

Audit immutability (T4.11) requires direct DB access:
```bash
docker exec -it e2e-postgres psql -U postgres -d app
\c app
SET search_path TO 'tenant_<schema>';
UPDATE audit_events SET event_type = 'tampered' WHERE id = '<uuid>';
-- Expected: ERROR from pg_trigger
```
