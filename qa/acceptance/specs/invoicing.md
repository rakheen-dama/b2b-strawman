# Invoicing — Acceptance Test Spec

> **Feature ID**: `invoicing`
> **Phase(s)**: 10 (core), 26 (tax — sibling spec), 30 (expense lines — sibling spec), 40 (billing-runs — sibling spec)
> **Last Updated**: 2026-05-06
> **Status**: DRAFT

## Overview

Invoicing is the core billing artefact in Kazi. Each invoice belongs to one customer, contains one or more line items, and progresses through a strict five-state lifecycle (DRAFT → APPROVED → SENT → PAID, with VOID as an off-ramp from APPROVED/SENT). Once approved, an invoice receives a gap-free, per-tenant invoice number, freezes its customer/org snapshot, and becomes immutable except for status transitions and payment events. This spec covers manual invoice CRUD, line-item editing, the lifecycle state machine, numbering, subtotal arithmetic, RBAC, list/filter behaviour, and payment recording. **Out of scope** (covered by sibling specs in the `invoice-core` group): tax-rate application and tax math (`invoice-tax`), generating invoices from unbilled time/expenses (`invoice-generation`), email delivery (`invoice-email`), online payment (`invoice-payments`), batch billing runs (`billing-runs`).

## Prerequisites

- E2E mock-auth stack running (`bash compose/scripts/e2e-up.sh`) — frontend on `:3001`, backend on `:8081`, mock IDP on `:8090`.
- API lifecycle seed loaded (`bash compose/seed/lifecycle-test.sh`) — provides `e2e-test-org`, three users, and an ACTIVE customer with prerequisite fields populated.
- Mock JWT for Alice (owner), Bob (admin), Carol (member) — fetched from `${MOCK_IDP_URL}/token` with `orgId=org_e2e_test`, `orgSlug=e2e-test-org`.
- At least one customer with `lifecycleStatus=ACTIVE` and `status=ACTIVE`. For send-path tests the customer must also have a non-blank `taxNumber` (hard-blocks send otherwise).
- Org settings must have a non-blank `orgName` (hard-blocks send otherwise).
- For payment-recording tests, the mock payment gateway must accept the request (no special prereq under E2E).

## Test Environment

- **Stack**: E2E mock-auth (frontend `http://localhost:3001`, backend `http://localhost:8081`, mock IDP `http://localhost:8090`).
- **Auth**: Mock IDP (Alice=owner, Bob=admin, Carol=member).
- **Org**: `e2e-test-org` (`orgId=org_e2e_test`).
- **Currency**: `ZAR` for all examples (currency is per-invoice, immutable post-create).

## Acceptance Criteria

### AC-001: Create draft invoice for a customer

**Given** an authenticated user with `INVOICING` capability and an ACTIVE customer
**When** the user POSTs `/api/invoices` with `{customerId, currency, notes?, dueDate?, paymentTerms?, poNumber?, taxType?, billingPeriodStart?, billingPeriodEnd?}`
**Then** a new invoice is created with `status=DRAFT`, `invoiceNumber=null`, `subtotal=0.00`, `taxAmount=0.00`, `total=0.00`, denormalised `customerName/customerEmail/customerAddress` and `orgName`, and `createdBy` set from JWT

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Happy path — minimal payload | `{customerId, currency: "ZAR"}` | 201; body has `id`, `status="DRAFT"`, `invoiceNumber=null`, `customerName` populated from customer record, `Location: /api/invoices/{id}` header | P0 |
| 2 | Full payload | `{customerId, currency: "ZAR", notes, paymentTerms: "Net 30", poNumber: "PO-123", taxType: "VAT", billingPeriodStart, billingPeriodEnd, dueDate}` | 201; all fields persisted on returned `InvoiceResponse` | P1 |
| 3 | Missing `customerId` | `{currency: "ZAR"}` | 400 ProblemDetail (`@NotNull` violation) | P1 |
| 4 | Invalid currency length | `{customerId, currency: "USDD"}` | 400 (must be exactly 3 chars per `@Size(min=3,max=3)`) | P1 |
| 5 | Blank currency | `{customerId, currency: ""}` | 400 (`@NotBlank`) | P1 |
| 6 | `paymentTerms` over 100 chars | length 101 | 400 | P2 |
| 7 | `poNumber` over 100 chars | length 101 | 400 | P2 |
| 8 | Customer with missing `taxNumber` | ACTIVE customer with blank tax number | 201; response `warnings` array contains `"tax_number_missing"` (draft accepted, warning surfaced) | P0 |
| 9 | Subsequent GET on the same draft | re-fetch via `GET /api/invoices/{id}` | 200; `warnings` array is **empty** (warnings only emitted on create per `InvoiceResponse` Javadoc) | P1 |

**Automation Notes:**
- Endpoint: `POST /api/invoices` returns 201 with `Location: /api/invoices/{id}`.
- Selector hints: `<CreateInvoiceButton>` text "Create Invoice" — no explicit `data-testid` (use `getByRole('button', {name: 'Create Invoice'})`).
- API verification: re-GET `/api/invoices/{id}`, assert status=DRAFT, invoiceNumber=null.
- Response shape includes resolved fields not on entity: `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel` (sourced from org settings at read time — not snapshotted).

---

### AC-002: Update draft invoice metadata

**Given** an invoice in `DRAFT` status
**When** the user PUTs `/api/invoices/{id}` with mutable fields (`dueDate`, `notes`, `paymentTerms`, `taxAmount`, `poNumber`, `taxType`, `billingPeriodStart`, `billingPeriodEnd`)
**Then** the fields are updated; `customerId`, `currency`, and identity fields remain immutable

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Set due date and notes on draft | `{dueDate: "2026-06-30", notes: "Net 30"}` | 200; persisted on re-GET | P0 |
| 2 | Negative `taxAmount` | `{taxAmount: -1.00}` | 400 (`@PositiveOrZero`) | P1 |
| 3 | Update non-DRAFT (APPROVED) invoice | PUT after `/approve` | 409 ResourceConflictException ("Only draft invoices can be edited") | P0 |
| 4 | Update non-DRAFT (SENT) invoice | PUT after `/send` | 409 | P0 |
| 5 | Update non-DRAFT (PAID) invoice | PUT after `/payment` | 409 | P1 |
| 6 | Update non-DRAFT (VOID) invoice | PUT after `/void` | 409 | P1 |
| 7 | Currency cannot be changed | `{currency: "USD"}` on a ZAR invoice | Currency unchanged on re-GET (field absent from `UpdateInvoiceRequest`) | P1 |

**Automation Notes:**
- Verify in UI: detail page header should show updated values after re-render.
- Status must equal `DRAFT` before edit attempt — fetch via API to confirm.

---

### AC-003: Delete draft invoice

**Given** an invoice in `DRAFT` status
**When** the user DELETEs `/api/invoices/{id}`
**Then** the invoice and all its lines are removed

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Delete empty draft | DRAFT, 0 lines | 204; subsequent GET returns 404 | P0 |
| 2 | Delete draft with lines | DRAFT, ≥1 lines | 204; lines removed; time-entry/expense `invoiceId` (if any) cleared | P0 |
| 3 | Delete APPROVED invoice | non-DRAFT | 409 ("Only draft invoices can be deleted") | P0 |
| 4 | Delete SENT invoice | non-DRAFT | 409 | P0 |
| 5 | Delete PAID invoice | non-DRAFT | 409 | P0 |
| 6 | Delete VOID invoice | non-DRAFT | 409 | P0 |
| 7 | Delete non-existent invoice | bogus UUID | 404 ResourceNotFoundException | P1 |

**Automation Notes:**
- After delete, `GET /api/invoices?customerId={id}` should not contain the deleted invoice.

---

### AC-004: Add manual line item to draft

**Given** an invoice in `DRAFT` status
**When** the user POSTs `/api/invoices/{id}/lines` with `{description, quantity, unitPrice, sortOrder?, projectId?, taxRateId?, tariffItemId?}`
**Then** a new `InvoiceLine` row is created, `amount = quantity × unitPrice` (HALF_UP, scale 2), and the invoice subtotal/total are recalculated

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Single line, qty=3, price=450.00 | `{description: "Consulting", quantity: 3, unitPrice: 450, sortOrder: 0}` | 201; `line.amount=1350.00`, `invoice.subtotal=1350.00`, `invoice.total=1350.00` | P0 |
| 2 | Fractional quantity 0.25 × 1200 | `{quantity: 0.25, unitPrice: 1200}` | `line.amount=300.00`, `subtotal=300.00` | P0 |
| 3 | Non-terminating: 1.5 × 333.33 = 499.995 | `{quantity: 1.5, unitPrice: 333.33}` | Rounded HALF_UP to `500.00` (covered by existing `invoice-arithmetic.spec.ts:150-165` which currently accepts 499.99–500.00 — **see Known Bug #1**) | P0 |
| 4 | Quantity = 0 | `{quantity: 0, unitPrice: 500}` | 400 (`@Positive` requires > 0) | P0 |
| 5 | Negative quantity | `{quantity: -1, unitPrice: 500}` | 400 | P0 |
| 6 | Unit price = 0 | `{quantity: 1, unitPrice: 0}` | 201; `line.amount=0.00` (`@PositiveOrZero` allows 0) | P1 |
| 7 | Negative unit price | `{quantity: 1, unitPrice: -10}` | 400 | P0 |
| 8 | Missing description | `{quantity: 1, unitPrice: 100}` | 400 (description is `@NotBlank` per AddLineItemRequest) | P1 |
| 9 | `sortOrder` omitted | omit field | 201; backend auto-assigns next ordinal | P1 |
| 10 | Add line to APPROVED invoice | invoice in any non-DRAFT status | 409 (covered by `invoice-lifecycle.spec.ts:293-324`) | P0 |
| 11 | Two lines, totals roll up | line A: 2×500=1000; line B: 1×1500=1500 | `subtotal=2500.00`, `total=2500.00` (covered by `invoice-arithmetic.spec.ts:129-148`) | P0 |
| 12 | High precision quantity | `{quantity: 2.5555}` (4-decimal scale) | 201; `BigDecimal` precision 10 scale 4 preserved on quantity, amount HALF_UP to 2 dp | P2 |

**Automation Notes:**
- Endpoint: `POST /api/invoices/{id}/lines` returns 201 with `Location: /api/invoices/{id}` (note: not `/lines/{lineId}`).
- UI selectors for line editor (`invoice-line-editor.tsx`): description input, quantity input, unit-price input, "Add Line" button — **no explicit `data-testid` attributes found**, automation must use `getByLabel` / `getByPlaceholder` or text-content selectors. **Gap recorded — see Known Bug #2**.
- Verify subtotal in UI: `invoice-totals-section.tsx` renders `Subtotal:` label followed by formatted currency.

---

### AC-005: Update line item on draft

**Given** a draft invoice with at least one line
**When** the user PUTs `/api/invoices/{id}/lines/{lineId}` with new `{description, quantity, unitPrice, sortOrder, taxRateId?}`
**Then** the line is updated, `line.amount` is recalculated, and the invoice subtotal/total are recalculated

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Edit qty + price (covered by `invoice-crud.spec.ts:138-173`) | qty=5, price=500 | `line.amount=2500.00`, `subtotal=2500.00`, description updated | P0 |
| 2 | Update line on APPROVED invoice | invoice non-DRAFT | 409 | P0 |
| 3 | Update non-existent line | bogus `lineId` | 404 | P1 |
| 4 | Update line that belongs to a different invoice | `lineId` from another invoice | 404 (line not found within invoice scope) | P1 |
| 5 | Set quantity to 0 via update | `{quantity: 0}` | 400 | P0 |

**Automation Notes:**
- After update, re-GET the parent invoice and verify `subtotal` reflects the new line amount.

---

### AC-006: Delete line item from draft

**Given** a draft invoice with multiple lines
**When** the user DELETEs `/api/invoices/{id}/lines/{lineId}`
**Then** the line is removed and the invoice subtotal/total are recalculated

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Delete one of two lines (covered by `invoice-crud.spec.ts:175-219`) | 2 lines, delete one | 204; `subtotal` reduced by deleted line's amount | P0 |
| 2 | Delete the only line | 1 line | 204; subtotal=0; invoice still exists in DRAFT (no auto-delete) | P1 |
| 3 | Delete line on APPROVED invoice | non-DRAFT | 409 | P0 |
| 4 | Delete non-existent line | bogus UUID | 404 | P2 |

---

### AC-007: Approve draft invoice (DRAFT → APPROVED)

**Given** a draft invoice with at least one line item
**When** the user POSTs `/api/invoices/{id}/approve`
**Then** status transitions to APPROVED, an invoice number is assigned (`INV-` + 4-digit zero-padded sequence), `issueDate` defaults to today, `approvedBy` is set from JWT, and time-entries/expenses linked to the invoice are marked as billed

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Happy path approval (covered by `invoice-lifecycle.spec.ts:104-122`) | DRAFT, ≥1 line | 200; `status="APPROVED"`, `invoiceNumber` matches `^INV-\d{4}$`, `issueDate=today`, `approvedBy=alice.memberId` | P0 |
| 2 | Approve with no lines | DRAFT, 0 lines | 400 InvalidStateException ("Invoice must have at least one line item before approval") | P0 |
| 3 | Sequence is gap-free per tenant | Approve 3 invoices in sequence | Numbers are `INV-XXXX, INV-XXXX+1, INV-XXXX+2` (consecutive) | P0 |
| 4 | Number unique within tenant | Approve N invoices, query all | All `invoiceNumber` values are distinct | P0 |
| 5 | Approve already-APPROVED invoice | approved once, retry | 409 ResourceConflictException ("Only draft invoices can be approved") | P0 |
| 6 | Approve SENT invoice | non-DRAFT | 409 | P0 |
| 7 | Approve VOID invoice | non-DRAFT | 409 | P0 |
| 8 | `issueDate` preserved if already set | draft has `issueDate` populated via prior workflow | 200; existing `issueDate` is **not** overwritten | P1 |
| 9 | Approve with already-invoiced time entry | line references a `timeEntryId` already linked to another invoice | 409 ResourceConflictException ("Time entry already billed") | P1 |
| 10 | Audit event emitted | approve happy path | `invoice.approved` audit row exists with `actorMemberId`, `invoiceId` | P1 |
| 11 | `InvoiceApprovedEvent` published | approve happy path | Side-effect listeners (e.g., audit, sync) fire | P2 |

**Automation Notes:**
- UI: `getByRole("button", {name: "Approve"})` (existing test uses exact match).
- API verification: re-GET the invoice; assert `invoiceNumber` matches `/^INV-\d{4}$/`.
- After approve, the UI status badge variant changes from `neutral` ("Draft") to `lead` ("Approved").

---

### AC-008: Send approved invoice (APPROVED → SENT)

**Given** an approved invoice
**When** the user POSTs `/api/invoices/{id}/send` with optional `{overrideWarnings: true}`
**Then** validation runs (org name, customer required fields, customer tax number — all CRITICAL); if no critical failures (or `overrideWarnings=true`), status transitions to SENT, a payment link is generated, and `paymentSessionId`/`paymentUrl` are set

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Happy path with `overrideWarnings=true` (existing E2E pattern) | APPROVED, valid org+customer | 200; `status="SENT"`, `paymentUrl` non-null | P0 |
| 2 | Send DRAFT invoice (existing test `invoice-lifecycle.spec.ts:326-340`) | DRAFT | 400/409 ("Cannot skip DRAFT → SENT") | P0 |
| 3 | Send PAID invoice | PAID | 409 | P0 |
| 4 | Send VOID invoice | VOID | 409 | P0 |
| 5 | Send with missing customer tax number, no override | customer.taxNumber blank | 400 InvoiceValidationFailedException; response includes critical check `customer_tax_number_missing` | P0 |
| 6 | Send with missing customer tax number, override=true | customer.taxNumber blank | 200; status=SENT (warnings overridden) | P0 |
| 7 | Send with blank org name, no override | orgSettings.orgName blank | 400 InvoiceValidationFailedException; check `organization_name_missing` | P0 |
| 8 | Send with missing required customer field (no email/address), no override | customer fields incomplete | 400; check `customer_required_fields_incomplete` | P0 |
| 9 | Audit event emitted | send happy path | `invoice.sent` audit row | P1 |
| 10 | `InvoiceSentEvent` published | send happy path | downstream listeners fire (email delivery is `invoice-email`) | P2 |

**Automation Notes:**
- UI button: `getByRole("button", {name: /Send Invoice/i})` (regex match — existing test).
- For E2E: prefer API send with `overrideWarnings=true` to avoid fixture coupling; UI test only the happy-path button click.
- Validation error response shape: `{type: "...", title: "Invoice validation failed", status: 400, checks: [{code, message, severity}]}`.

---

### AC-009: Record payment (SENT → PAID)

**Given** a sent invoice
**When** the user POSTs `/api/invoices/{id}/payment` with optional `{paymentReference}`
**Then** status transitions to PAID, `paidAt=now()`, `paymentReference` is stored, and a `PaymentEvent` row is created with `status=COMPLETED`

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Happy path with reference (covered by `invoice-lifecycle.spec.ts:160-222`) | SENT | 200; `status="PAID"`, `paidAt` non-null, `paymentReference="PAY-XXXX"` | P0 |
| 2 | Record payment with no reference | `{}` body or missing body | 200; `paymentReference=null` | P1 |
| 3 | Record payment on DRAFT | DRAFT | 400/409 | P0 |
| 4 | Record payment on APPROVED | APPROVED | 400/409 | P0 |
| 5 | Record payment on PAID (idempotency check) | already PAID | 409 ResourceConflictException | P0 |
| 6 | Record payment on VOID | VOID | 409 | P0 |
| 7 | `PaymentEvent` row created | happy path | `GET /api/invoices/{id}/payment-events` returns ≥1 row with `status="COMPLETED"` | P1 |
| 8 | Audit event emitted | happy path | `invoice.paid` audit row | P1 |

**Automation Notes:**
- UI button: `getByRole("button", {name: /Record Payment/i})`.
- Payment form is **inline** (not a dialog) — see existing `invoice-lifecycle.spec.ts:198-216`.
- Reference input: `getByPlaceholder(/CHK-12345|Wire transfer/i)` (current placeholder text).
- Confirm button: `getByRole("button", {name: /Confirm Payment/i})`.

---

### AC-010: Void invoice (APPROVED/SENT → VOID)

**Given** an approved or sent invoice
**When** the user POSTs `/api/invoices/{id}/void`
**Then** status transitions to VOID, all linked time entries are unbilled (`invoiceId=null`), all linked expenses are unbilled, and the invoice number is permanently retained (not reused)

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Void APPROVED invoice | APPROVED | 200; `status="VOID"` | P0 |
| 2 | Void SENT invoice (covered by `invoice-lifecycle.spec.ts:224-264`) | SENT | 200; `status="VOID"` | P0 |
| 3 | Void DRAFT invoice via `/void` endpoint | DRAFT | 409 (DRAFT must use DELETE not VOID) | P0 |
| 4 | Void PAID invoice (covered by `invoice-lifecycle.spec.ts:342-352`) | PAID | 400/409 | P0 |
| 5 | Void already-VOID invoice | VOID | 409 | P0 |
| 6 | Time entries released after void (covered by `invoice-lifecycle.spec.ts:266-291`) | invoice with TIME lines | linked time entries' `invoiceId=null` post-void | P0 |
| 7 | Expenses released after void | invoice with EXPENSE lines | linked expenses' `invoiceId=null` post-void | P0 |
| 8 | Invoice number retained after void | sequence: approve INV-0005, void it; approve INV-0006 next | `INV-0005` is permanently retired; next approval is `INV-0006`, never `INV-0005` | P0 |
| 9 | Audit event emitted | void happy path | `invoice.voided` audit row | P1 |

**Automation Notes:**
- UI button: `getByRole("button", {name: /Void/i})`.
- Existing test pattern checks for confirmation dialog with `getByRole("dialog")` and confirms via `getByRole("button", {name: /Void|Confirm/i})`.

---

### AC-011: List invoices with filters

**Given** a tenant with multiple invoices in mixed statuses across multiple customers/projects
**When** the user GETs `/api/invoices?customerId=&status=&projectId=` (any subset of filters)
**Then** the response is filtered accordingly; if no filters, all invoices for the tenant are returned

**Test Cases:**

| # | Scenario | Filter | Expected | Priority |
|---|----------|--------|----------|----------|
| 1 | No filters | `GET /api/invoices` | 200; all tenant invoices | P0 |
| 2 | By customerId | `?customerId={id}` | only that customer's invoices | P0 |
| 3 | By status DRAFT | `?status=DRAFT` | only DRAFT invoices | P0 |
| 4 | By status APPROVED | `?status=APPROVED` | only APPROVED | P0 |
| 5 | By status SENT | `?status=SENT` | only SENT | P0 |
| 6 | By status PAID | `?status=PAID` | only PAID | P0 |
| 7 | By status VOID | `?status=VOID` | only VOID | P0 |
| 8 | By projectId | `?projectId={id}` | invoices that have at least one line with that `projectId` | P1 |
| 9 | Multiple filters: customerId + status | `?customerId=&status=` | service applies only the **first** non-null filter (customerId wins per current implementation). **See Known Bug #3** — multi-filter combinations behave inconsistently | P1 |
| 10 | Tenant isolation | tenant A queries from tenant B's JWT | only tenant A's invoices visible (schema-per-tenant boundary) | P0 |

**Automation Notes:**
- UI: List page at `/org/{slug}/invoices`. Status tab filters set the `?status=` query param.
- Summary cards on list page: "Total Outstanding" (APPROVED+SENT), "Total Overdue" (APPROVED+SENT with `dueDate < today`), "Paid This Month" (PAID with `paidAt >= startOfMonth`). Verify each card's number matches the corresponding subset.
- Empty state: when zero invoices, list page should render an empty-state card (selector tbd — gap noted).

---

### AC-012: Invoice detail page

**Given** an invoice in any status
**When** the user navigates to `/org/{slug}/invoices/{id}`
**Then** the page renders header (number/status badge/customer), lines table, totals section, status-appropriate action buttons, custom-fields section, audit section, and back link

**Test Cases:**

| # | Scenario | Status | Expected visible elements | Priority |
|---|----------|--------|---------------------------|----------|
| 1 | DRAFT detail page | DRAFT | "Draft" badge (variant `neutral`); buttons: Approve, Add Line, Delete; line editor available | P0 |
| 2 | APPROVED detail | APPROVED | "Approved" badge (variant `lead`); `INV-XXXX` shown; buttons: Send Invoice, Void; line editor disabled/hidden | P0 |
| 3 | SENT detail | SENT | "Sent" badge (variant `lead`); buttons: Record Payment, Refresh Payment Link, Void; payment URL displayed | P0 |
| 4 | PAID detail | PAID | "Paid" badge (variant `success`); `paidAt` shown; payment events list visible; no Record Payment button | P0 |
| 5 | VOID detail | VOID | "Void" badge (variant `destructive`); no action buttons (terminal) | P0 |
| 6 | Back-to-list link | any | "Back to Invoices" link at top, navigates to `/org/{slug}/invoices` | P1 |
| 7 | Generate Document dropdown | any (if templates exist) | Dropdown rendered when org has invoice templates configured | P2 |
| 8 | Audit section shows creator/approver | any | "Created by Alice" + timestamp; "Approved by Alice" if status≥APPROVED | P1 |
| 9 | Warnings banner on freshly created draft | DRAFT just created with `tax_number_missing` warning | Inline warning banner visible (sourced from `InvoiceResponse.warnings`) | P1 |

**Automation Notes:**
- Status badge component (`status-badge.tsx`) — text matches enum casing: "Draft", "Approved", "Sent", "Paid", "Void". No `data-testid`; selector via `getByText`.
- Detail page route: `/org/[slug]/invoices/[id]`.

---

### AC-013: Custom fields on invoice

**Given** an invoice and admin-defined custom field definitions
**When** the user PUTs `/api/invoices/{id}/custom-fields` with `{customFields: {key: value}}`
**Then** the values are merged into `invoice.customFields` (JSONB)

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Set custom field on DRAFT | `{customFields: {"po_number_internal": "X-001"}}` | 200; field persisted | P1 |
| 2 | Set custom field on APPROVED | non-DRAFT | 200 (custom fields editable across statuses; not subject to draft-only guard) | P2 |
| 3 | Update applied field groups | PUT `/api/invoices/{id}/field-groups` with `{appliedFieldGroups: [uuid]}` | 200; returns the resolved field-definition list for those groups | P2 |
| 4 | Field-group definitions returned shape | response | List of `FieldDefinitionResponse` matching org-level definitions | P2 |

**Automation Notes:**
- Endpoint: `PUT /api/invoices/{id}/custom-fields` accepts `UpdateCustomFieldsRequest{customFields: Map<String,Object>}`.
- UI: read/edit gated by `isAdmin` flag on detail page.

---

### AC-014: Invoice preview HTML

**Given** an invoice in any status
**When** the user GETs `/api/invoices/{id}/preview`
**Then** an HTML rendering of the invoice is returned (Content-Type: `text/html`)

**Test Cases:**

| # | Scenario | Expected | Priority |
|---|----------|----------|----------|
| 1 | DRAFT preview | 200; HTML contains "DRAFT" watermark or status indicator; customer name, line descriptions, subtotal/total visible | P1 |
| 2 | APPROVED preview | HTML contains `INV-XXXX`, `issueDate`, customer details, lines, totals | P1 |
| 3 | Preview of non-existent invoice | 404 | P2 |
| 4 | Tax breakdown rendered | invoice with per-line tax | tax-rate breakdown table present | P2 (covered more deeply by `invoice-tax`) |

**Automation Notes:**
- The frontend has a server-side route `frontend/app/api/invoices/[id]/preview/route.ts` that proxies to the backend preview — verify both backend and frontend paths return identical HTML.

---

### AC-015: Refresh payment link (SENT)

**Given** a sent invoice with an existing payment URL
**When** the user POSTs `/api/invoices/{id}/refresh-payment-link`
**Then** a new payment session is created and `paymentUrl` is replaced

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Refresh on SENT | SENT invoice with `paymentUrl` populated | 200; `paymentUrl` differs from previous value | P1 |
| 2 | Refresh on DRAFT | DRAFT | 400/409 | P1 |
| 3 | Refresh on PAID | PAID | 409 (no payment session needed) | P1 |
| 4 | Refresh on VOID | VOID | 409 | P1 |

---

### AC-016: Get payment events

**Given** an invoice with one or more payment events
**When** the user GETs `/api/invoices/{id}/payment-events`
**Then** the list of `PaymentEventResponse` is returned in chronological order

**Test Cases:**

| # | Scenario | Expected | Priority |
|---|----------|----------|----------|
| 1 | DRAFT invoice (no events) | 200; empty list | P2 |
| 2 | PAID invoice | 200; ≥1 event with `status="COMPLETED"` | P1 |
| 3 | Multiple events (e.g., CREATED → PENDING → COMPLETED) | All events returned in order | P2 |
| 4 | Reversed payment | one COMPLETED + one reversal/cancellation event | P2 (deeper in `invoice-payments` spec) |

---

## State Machine Tests

### Allowed Transitions

| From | To | Trigger | Guards | Expected |
|------|----|----|-------|----------|
| DRAFT | APPROVED | `POST /{id}/approve` | ≥1 line; no time/expense already on another invoice | 200; number assigned, `issueDate=today`, `approvedBy` set; entries marked billed |
| APPROVED | SENT | `POST /{id}/send` | Org name set; customer required fields incl. tax number; or `overrideWarnings=true` | 200; payment link generated |
| SENT | PAID | `POST /{id}/payment` | Payment gateway success | 200; `paidAt=now()`; `PaymentEvent(COMPLETED)` row |
| APPROVED | VOID | `POST /{id}/void` | none | 200; entries unbilled |
| SENT | VOID | `POST /{id}/void` | none | 200; entries unbilled |
| DRAFT | (deleted) | `DELETE /{id}` | none | 204 |

### Forbidden Transitions

| From | To | Attempted via | Expected error |
|------|----|----|---|
| DRAFT | SENT | `POST /{id}/send` | 400/409 (must approve first) |
| DRAFT | PAID | `POST /{id}/payment` | 400/409 |
| DRAFT | VOID | `POST /{id}/void` | 409 (use DELETE for drafts) |
| APPROVED | DRAFT | n/a | no endpoint exists; cannot revert |
| APPROVED | PAID | `POST /{id}/payment` | 400/409 (must send first) |
| SENT | DRAFT | n/a | no endpoint |
| SENT | APPROVED | n/a | no endpoint |
| PAID | DRAFT | n/a | no endpoint |
| PAID | APPROVED | n/a | no endpoint |
| PAID | SENT | n/a | no public endpoint (internal `reversePayment` exists for trust-accounting reversal flows — covered by trust spec) |
| PAID | VOID | `POST /{id}/void` | 400/409 (covered by existing test) |
| VOID | * | any | 409 (terminal) |

### Invariants

- Once status ≠ DRAFT, line items are immutable (add/edit/delete blocked at service layer).
- Once status ≠ DRAFT, `currency`, `customerId`, `customerName`, `customerEmail`, `customerAddress`, `orgName`, `notes`, `paymentTerms`, `dueDate` are also immutable (no PUT endpoint accepts them post-DRAFT — verify by attempting PUT and asserting 409).
- `invoiceNumber` is set exactly once on first approval and never changes thereafter (even after void).
- `createdAt` is `updatable=false`; cannot be modified.

---

## Permission Matrix

All endpoints are gated by `@RequiresCapability("INVOICING")`. The capability is resolved from each member's `OrgRole` entity by `MemberFilter`. The default seeding for the E2E stack grants `INVOICING` capability to `org:owner` and `org:admin` roles, but **not** to `org:member` (verify by inspecting seed scripts or the role-capability mapping).

| Action | Owner (Alice) | Admin (Bob) | Member (Carol) | Portal Contact |
|--------|--------------|-------------|----------------|----------------|
| Create draft (`POST /api/invoices`) | ✅ | ✅ | ❌ (403) | ❌ |
| Update draft (`PUT /api/invoices/{id}`) | ✅ | ✅ | ❌ | ❌ |
| Delete draft | ✅ | ✅ | ❌ | ❌ |
| List invoices (`GET /api/invoices`) | ✅ | ✅ | ❌ | ❌ (uses portal API) |
| Get invoice detail | ✅ | ✅ | ❌ | ❌ (portal route) |
| Add/update/delete line | ✅ | ✅ | ❌ | ❌ |
| Approve | ✅ | ✅ | ❌ | ❌ |
| Send | ✅ | ✅ | ❌ | ❌ |
| Record payment | ✅ | ✅ | ❌ | ❌ |
| Void | ✅ | ✅ | ❌ | ❌ |
| Refresh payment link | ✅ | ✅ | ❌ | ❌ |
| Update custom fields | ✅ | ✅ | ❌ | ❌ |
| Preview HTML | ✅ | ✅ | ❌ | ❌ |

**Test Cases (P1):**

| # | Scenario | User | Endpoint | Expected |
|---|----------|------|----------|----------|
| 1 | Member cannot create draft | Carol | `POST /api/invoices` | 403 ForbiddenException |
| 2 | Member cannot list invoices | Carol | `GET /api/invoices` | 403 |
| 3 | Member cannot approve | Carol | `POST /{id}/approve` | 403 |
| 4 | Admin can create draft | Bob | `POST /api/invoices` | 201 |
| 5 | Owner can do everything | Alice | full lifecycle | all 200/201 |
| 6 | Cross-tenant access denied | Alice from tenant A queries tenant B's invoice | `GET /api/invoices/{id}` | 404 (not 403, per security-by-obscurity convention) |

> Note: This matrix assumes default capability seeding. If your org has overridden capabilities on a custom OrgRole, results will differ — fetch the actual member's capability set first.

---

## Financial Accuracy

All amounts are `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`. Quantity is precision 10 / scale 4. Currency is per-invoice (immutable post-create).

| # | Scenario | Inputs | Expected line.amount | Expected subtotal | Expected total |
|---|----------|--------|---------------------|-------------------|---------------|
| 1 | Whole numbers | qty=3, price=450.00 | 1350.00 | 1350.00 | 1350.00 |
| 2 | Two lines | (2 × 500) + (1 × 1500) | 1000.00, 1500.00 | 2500.00 | 2500.00 |
| 3 | Fractional qty | qty=0.25, price=1200 | 300.00 | 300.00 | 300.00 |
| 4 | Decimal price | qty=2, price=99.99 | 199.98 | 199.98 | 199.98 |
| 5 | HALF_UP rounding (up) | qty=1.5, price=333.33 → 499.995 | **500.00** | 500.00 | 500.00 |
| 6 | HALF_UP rounding (down) | qty=1.5, price=333.32 → 499.98 | 499.98 | 499.98 | 499.98 |
| 7 | Zero unit price | qty=1, price=0 | 0.00 | 0.00 | 0.00 |
| 8 | High precision quantity | qty=2.5555, price=10 | 25.56 (HALF_UP from 25.555) | 25.56 | 25.56 |
| 9 | Many lines (10× of qty=1, price=10) | 10 lines | 10.00 each | 100.00 | 100.00 |
| 10 | Zero-value invoice | 1 line, qty=1, price=0 | 0.00 | 0.00 | 0.00 — invoice approve/send is permitted (no `total > 0` guard) |

Tax math (per-line tax application, tax-inclusive vs exclusive, multi-rate breakdown) is covered in `invoice-tax` — not tested here.

---

## Cross-Feature Integration Points

| Integration | Related Feature | What to Verify |
|-------------|----------------|----------------|
| Approve marks linked time entries as billed | `time-entries` | After approve, `timeEntry.invoiceId == invoice.id` for each TIME line; can no longer be re-invoiced |
| Approve marks linked expenses as billed | `expenses` | After approve, `expense.invoiceId == invoice.id` for each EXPENSE line |
| Void releases time entries | `time-entries` | After void, `timeEntry.invoiceId == null` for previously-linked entries |
| Void releases expenses | `expenses` | After void, `expense.invoiceId == null` |
| Customer denormalisation | `customer-crud` | Customer name/email/address frozen at invoice create time; later customer edits do **not** propagate to existing invoices |
| Tax-rate snapshot on lines | `invoice-tax` | When a line is added with `taxRateId`, name/percent are snapshotted; subsequent rate edits don't alter past lines |
| Numbering counter | (singleton, per tenant) | `InvoiceCounter` row has `singleton=TRUE` unique constraint; concurrent approval calls serialise via row lock |
| Portal invoice view | `portal-invoices` | SENT/PAID invoices visible to the customer's portal contact via portal endpoint (different RBAC path) |
| Audit trail | `audit-trail` | Each transition (`approved`, `sent`, `paid`, `voided`) emits an audit row with actor + invoice ID |
| Domain events | `notifications-inapp`, `email-notifications` | `InvoiceApprovedEvent`/`InvoiceSentEvent`/`InvoicePaidEvent`/`InvoiceVoidedEvent`/`InvoicePaymentReversedEvent`/`InvoicePaymentPartiallyReversedEvent` are published on transition; subscribers (email, in-app) drive notifications |
| Accounting sync | `integration-ports` (future Xero) | `InvoiceSyncEvent` is published on send — picked up by sync adapter |
| Generation from time/expenses | `invoice-generation` | `POST /api/invoices` with `timeEntryIds[]`/`expenseIds[]` populates lines + warnings (covered there, not here) |
| Disbursement lines (legal vertical) | `legal-disbursements` | `POST /{id}/disbursement-lines` gated by `VerticalModuleGuard` — only available with disbursements module |
| Custom fields | `custom-fields` | `appliedFieldGroups` and `customFields` follow the global field-definition framework |

---

## Known Bugs

Discovered during spec creation (code reading on 2026-05-06):

| # | Description | Severity | Source | Status |
|---|-------------|----------|--------|--------|
| 1 | `invoice-arithmetic.spec.ts:150-165` accepts both `499.99` and `500.00` for the `1.5 × 333.33` rounding case ("either is acceptable depending on rounding strategy"). Backend uses `RoundingMode.HALF_UP` deterministically, so the assertion should pin to `500.00`. The looseness masks a regression risk if rounding mode is changed. | Low | `frontend/e2e/tests/invoices/invoice-arithmetic.spec.ts:150-165` | OPEN |
| 2 | Frontend type `InvoiceLineType` (in `frontend/lib/types/invoice.ts:9`) is **missing the `DISBURSEMENT` value** that exists in the backend `InvoiceLineType` enum. Any invoice with disbursement lines (legal-za vertical) returns line objects whose `lineType` is not in the union — TypeScript narrowing in line-table/line-editor will be wrong. | Medium | Frontend type vs `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineType.java` | OPEN |
| 3 | `InvoiceController.listInvoices` accepts `customerId`, `status`, `projectId` as independent query params — but the underlying `invoiceService.findAll(customerId, status, projectId)` may apply only the first non-null filter (per agent's read of the service). UI sets `?status=` from tabs and `?customerId=` from customer detail tab; it does not currently combine them. The behaviour for combined filters is undocumented and inconsistent. | Medium | `InvoiceController.java:95-102` + service implementation | OPEN |
| 4 | `InvoiceResponse` exposes `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel` resolved at read-time from org settings (not snapshotted onto the invoice). If an org changes its VAT registration after an invoice was sent, re-fetching the invoice years later will return the **current** values, not the values at issue date. This breaks audit/legal reproducibility for past invoices. | Medium | `InvoiceResponse` builder + render service | OPEN — needs product decision (snapshot at approve? read-time dynamic? both with versioning?) |
| 5 | Line-editor and draft-form components have **no `data-testid` attributes** observed. Existing E2E tests rely on `getByText`, `getByPlaceholder`, and `getByRole` selectors. This is fragile (text changes break tests, ambiguous matches break tests). Adding stable `data-testid` attributes to invoice components would simplify automation. | Low | `frontend/components/invoices/*.tsx` | OPEN — automation gap, not a functional defect |
| 6 | Frontend `InvoiceLineResponse` (in `frontend/lib/types/invoice.ts`) does not include `disbursementId` even though the backend entity has the field. UI cannot identify the source disbursement of a DISBURSEMENT line (e.g., for grouping or audit display). | Low | Frontend type vs `InvoiceLine.java:43-44` | OPEN |
| 7 | `paymentDestination` is hardcoded to `"OPERATING"` at invoice creation. The field exists for future multi-fund support (TRUST, ESCROW) but is not currently configurable. Trust-accounting flows (`trust-approvals`) deal with this via separate trust-fee transfer paths. Document as design intent, not a bug, but worth flagging since the field is non-null and rarely set. | Info | `Invoice.java:108-109` constructor | NOT-A-BUG |

---

## Playwright Test File Mapping

| Spec File | Coverage |
|-----------|----------|
| `frontend/e2e/tests/invoices/invoice-crud.spec.ts` (existing, keep) | AC-001 (1,2), AC-004 (1,11), AC-005 (1), AC-006 (1) |
| `frontend/e2e/tests/invoices/invoice-lifecycle.spec.ts` (existing, expand) | AC-007 (1), AC-008 (1), AC-009 (1), AC-010 (2,4,6), AC-002 (3 — "Cannot edit approved"), state-machine forbidden transitions |
| `frontend/e2e/tests/invoices/invoice-arithmetic.spec.ts` (existing, tighten) | AC-004 (1-7), Financial Accuracy table rows 1-9. **Tighten Bug #1 — pin rounding case to 500.00.** |
| `frontend/e2e/tests/invoices/invoice-validation.spec.ts` (new) | AC-007 (2,5–9), AC-008 (5–8), validation gates for missing tax number / org name / required fields |
| `frontend/e2e/tests/invoices/invoice-list.spec.ts` (new) | AC-011 (all), summary cards, status tab filtering, tenant isolation |
| `frontend/e2e/tests/invoices/invoice-detail.spec.ts` (new) | AC-012 (all), AC-014 (preview), warnings banner |
| `frontend/e2e/tests/invoices/invoice-rbac.spec.ts` (new) | Permission Matrix (all P1 cases) |
| `frontend/e2e/tests/invoices/invoice-numbering.spec.ts` (new) | AC-007 (3,4), AC-010 (8), gap-free sequence + retention after void |
| `frontend/e2e/tests/invoices/invoice-payments-basic.spec.ts` (new — basic only) | AC-009 (2,3,4,5,7), AC-015, AC-016 (deeper in `invoice-payments`) |
| `frontend/e2e/tests/invoices/invoice-custom-fields.spec.ts` (new) | AC-013 (all) |

---

## Out-of-Scope (deferred to sibling specs)

- **Tax math**: rate snapshotting, per-line tax, tax-inclusive vs exclusive, multi-rate breakdown, tax-exempt lines → `invoice-tax`.
- **Generation from time/expenses**: customer-pick, time-entry pick, validation-generation endpoint, unbilled summary → `invoice-generation`.
- **Email delivery**: send-via-email mechanics, template rendering, payment-link embedding → `invoice-email`.
- **Online payment**: Stripe/PayFast checkout, webhook reconciliation, partial reversal → `invoice-payments`.
- **Batch billing runs**: cherry-pick, preview, generate-many, approve-many, send-many → `billing-runs`.
- **Retainers**: drawdown, period close, retainer-driven invoice generation → `retainers`.
- **Trust-accounting reversal**: reverse-payment from trust transfer flows → `trust-approvals` / `trust-transactions`.
- **Disbursement lines** (legal vertical): `POST /{id}/disbursement-lines` and DISBURSEMENT line type → `legal-disbursements`.
- **Tariff lines** (legal vertical): TARIFF line type, tariff-item lookup → `legal-tariffs`.
