# 02 — Invoicing & Billing

**Domain:** Invoice CRUD, line items, tax calculations, unbilled time generation, status lifecycle, preview, portal access
**Primary Testers:** James Chen (Admin), Marcus Webb (Admin)
**Supporting:** Thandi Nkosi (Owner), Lerato Dlamini (Member — RBAC boundary)
**Portal Contact:** ben.finance@acmecorp.com (BILLING role)

---

## Prerequisites

All test cases assume:
- Organization "DocTeams" is provisioned with OrgSettings configured (currency: ZAR)
- Acme Corp (ACTIVE) and Dunbar & Associates (ACTIVE) exist as customers
- Bright Solutions (PROSPECT) exists for guard testing
- At least one project ("Acme Website Redesign") is linked to Acme Corp via customer_projects
- Members have billing rates configured: James R500/hr, Marcus R800/hr, Sofia R300/hr
- A default TaxRate "VAT" exists at 15.00%, isDefault=true, isExempt=false
- An exempt TaxRate "Zero-rated" exists at 0.00%, isDefault=false, isExempt=true
- Portal contact ben.finance@acmecorp.com is set up with BILLING role for Acme Corp

---

## 1. Invoice CRUD

### INV-001: Create draft invoice for active customer

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** Acme Corp is an ACTIVE customer
**When** James sends POST /api/invoices with:
```json
{
  "customerId": "<acme-corp-id>",
  "currency": "ZAR",
  "dueDate": "today+30",
  "notes": "Website redesign - Phase 1",
  "paymentTerms": "Net 30"
}
```
**Then** response status is 201 Created
**And** response body contains:
- `status` = "DRAFT"
- `currency` = "ZAR"
- `customerName` = "Acme Corp"
- `orgName` = "DocTeams"
- `invoiceNumber` = null (assigned on approval, not creation)
- `subtotal` = 0.00 (no lines yet)
- `taxAmount` = 0.00
- `total` = 0.00
- `dueDate` = today+30
- `notes` = "Website redesign - Phase 1"
- `paymentTerms` = "Net 30"
- `createdBy` = James's member ID

---

### INV-002: Create draft invoice fails for PROSPECT customer

**Severity:** High
**Actor:** James Chen (Admin)

**Given** Bright Solutions has lifecycle status PROSPECT
**When** James sends POST /api/invoices with:
```json
{
  "customerId": "<bright-solutions-id>",
  "currency": "ZAR"
}
```
**Then** response status is 400 Bad Request
**And** error message contains "Customer must be in ACTIVE status"

---

### INV-003: Update draft invoice fields

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists for Acme Corp with dueDate=today+30
**When** James sends PUT /api/invoices/{id} with:
```json
{
  "dueDate": "today+45",
  "notes": "Updated notes for Phase 1",
  "paymentTerms": "Net 45",
  "taxAmount": null
}
```
**Then** response status is 200 OK
**And** `dueDate` = today+45
**And** `notes` = "Updated notes for Phase 1"
**And** `paymentTerms` = "Net 45"
**And** `updatedAt` is later than `createdAt`

---

### INV-004: Update non-DRAFT invoice is rejected

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** an APPROVED invoice exists (status = APPROVED)
**When** James sends PUT /api/invoices/{id} with:
```json
{
  "notes": "Trying to edit after approval"
}
```
**Then** response status is 400 Bad Request
**And** error message contains "Only draft invoices can be edited"

---

### INV-005: Delete draft invoice

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists for Acme Corp
**When** James sends DELETE /api/invoices/{id}
**Then** response status is 204 No Content
**And** subsequent GET /api/invoices/{id} returns 404

---

### INV-006: Delete non-DRAFT invoice is rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an APPROVED invoice exists
**When** James sends DELETE /api/invoices/{id}
**Then** response status is 400 Bad Request
**And** the invoice still exists when retrieved via GET

---

### INV-007: Get invoice detail

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists for Acme Corp with 2 line items
**When** James sends GET /api/invoices/{id}
**Then** response status is 200 OK
**And** response contains all invoice fields including `lines` array with 2 entries
**And** each line has `id`, `description`, `quantity`, `unitPrice`, `amount`, `sortOrder`

---

### INV-008: List invoices with filters

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** 3 invoices exist: 2 for Acme Corp (1 DRAFT, 1 APPROVED), 1 for Dunbar (DRAFT)
**When** James sends GET /api/invoices?customerId=<acme-corp-id>
**Then** response contains exactly 2 invoices, both for Acme Corp

**When** James sends GET /api/invoices?status=DRAFT
**Then** response contains exactly 2 invoices (1 Acme + 1 Dunbar)

**When** James sends GET /api/invoices?customerId=<acme-corp-id>&status=DRAFT
**Then** response contains exactly 1 invoice

---

### INV-009: Create invoice with missing required fields

**Severity:** Medium
**Actor:** James Chen (Admin)

**When** James sends POST /api/invoices with:
```json
{
  "currency": "ZAR"
}
```
(missing customerId)
**Then** response status is 400 Bad Request (validation error on @NotNull customerId)

**When** James sends POST /api/invoices with:
```json
{
  "customerId": "<acme-corp-id>",
  "currency": "AB"
}
```
(currency too short — @Size min=3)
**Then** response status is 400 Bad Request

---

## 2. Invoice Numbering

### INV-010: Invoice number assigned on approval

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists with `invoiceNumber` = null
**When** James sends POST /api/invoices/{id}/approve
**Then** response status is 200 OK
**And** `invoiceNumber` matches pattern "INV-NNNN" (e.g., "INV-0001")
**And** `status` = "APPROVED"
**And** `issueDate` is set to today (auto-assigned if null)
**And** `approvedBy` = James's member ID

---

### INV-011: Sequential invoice numbering across approvals

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** no invoices have been approved yet in this org
**When** James creates and approves Invoice A
**Then** Invoice A receives `invoiceNumber` = "INV-0001"

**When** James creates and approves Invoice B
**Then** Invoice B receives `invoiceNumber` = "INV-0002"

**When** James creates and approves Invoice C
**Then** Invoice C receives `invoiceNumber` = "INV-0003"

---

### INV-012: Voided invoice retains its number and does not create gaps

**Severity:** High
**Actor:** James Chen (Admin)

**Given** INV-0001 (APPROVED), INV-0002 (APPROVED), INV-0003 (APPROVED) exist
**When** James voids INV-0002
**Then** INV-0002 retains its `invoiceNumber` = "INV-0002" with status VOID

**When** James creates and approves a new Invoice D
**Then** Invoice D receives `invoiceNumber` = "INV-0004" (no gap-filling)

---

### INV-013: Invoice number format is zero-padded

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** 9 invoices have been approved
**When** James approves the 10th invoice
**Then** `invoiceNumber` = "INV-0010" (not "INV-10")

---

## 3. Line Item Management

### INV-014: Add manual line item to draft invoice

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists for Acme Corp with subtotal R0.00
**When** James sends POST /api/invoices/{id}/lines with:
```json
{
  "description": "Strategy consultation",
  "quantity": 4.0000,
  "unitPrice": 500.00,
  "sortOrder": 0
}
```
**Then** response status is 201 Created
**And** line item has:
- `description` = "Strategy consultation"
- `quantity` = 4.0000
- `unitPrice` = 500.00
- `amount` = 2000.00 (4 x R500)
- `lineType` = "TIME"
- `sortOrder` = 0
**And** invoice `subtotal` = R2,000.00
**And** invoice `total` is recalculated

---

### INV-015: Add multiple line items and verify subtotal accumulation

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists with no lines
**When** James adds line 1: quantity=2.0000, unitPrice=500.00 (amount = R1,000.00)
**And** James adds line 2: quantity=1.0000, unitPrice=800.00 (amount = R800.00)
**And** James adds line 3: quantity=0.5000, unitPrice=500.00 (amount = R250.00)
**Then** invoice `subtotal` = R2,050.00

**Math:**
- Line 1: 2.0000 x R500.00 = R1,000.00
- Line 2: 1.0000 x R800.00 = R800.00
- Line 3: 0.5000 x R500.00 = R250.00
- Subtotal: R1,000.00 + R800.00 + R250.00 = R2,050.00

---

### INV-016: Update line item recalculates amount and subtotal

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice with one line: quantity=2.0000, unitPrice=500.00 (amount=R1,000.00)
**When** James sends PUT /api/invoices/{id}/lines/{lineId} with:
```json
{
  "description": "Updated consultation",
  "quantity": 3.0000,
  "unitPrice": 600.00,
  "sortOrder": 0
}
```
**Then** line `amount` = R1,800.00 (3 x R600)
**And** invoice `subtotal` = R1,800.00

---

### INV-017: Delete line item recalculates subtotal

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice with 2 lines: line A (R1,000.00) and line B (R800.00), subtotal=R1,800.00
**When** James sends DELETE /api/invoices/{id}/lines/{lineA-id}
**Then** response status is 204 No Content
**And** invoice `subtotal` = R800.00 (only line B remains)

---

### INV-018: Cannot add line item to non-DRAFT invoice

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an APPROVED invoice exists
**When** James sends POST /api/invoices/{id}/lines with valid line data
**Then** response status is 400 Bad Request
**And** error message contains "Only draft invoices can be edited"

---

### INV-019: Line item validation — quantity must be positive

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists
**When** James sends POST /api/invoices/{id}/lines with:
```json
{
  "description": "Bad line",
  "quantity": 0,
  "unitPrice": 500.00,
  "sortOrder": 0
}
```
**Then** response status is 400 Bad Request (validation: @Positive on quantity)

---

### INV-020: Line item with tax rate applied

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists and a TaxRate "VAT" (15%) is configured
**When** James sends POST /api/invoices/{id}/lines with:
```json
{
  "description": "Consulting work",
  "quantity": 2.0000,
  "unitPrice": 1000.00,
  "sortOrder": 0,
  "taxRateId": "<vat-tax-rate-id>"
}
```
**Then** line item has:
- `amount` = R2,000.00
- `taxRateName` = "VAT"
- `taxRatePercent` = 15.00
- `taxAmount` calculated based on tax-inclusive/exclusive org setting
**And** invoice totals are recalculated

---

## 4. Tax Calculations

### INV-021: Tax-exclusive calculation — single line item

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false
**And** TaxRate "VAT" at 15.00% exists
**And** a DRAFT invoice has one line: quantity=2.0000, unitPrice=R1,000.00, taxRateId=VAT

**Then** calculations are:
- Line amount: 2 x R1,000.00 = R2,000.00
- Line tax: R2,000.00 x 15% = R300.00
- Invoice subtotal: R2,000.00
- Invoice taxAmount: R300.00
- Invoice total: R2,000.00 + R300.00 = **R2,300.00**

**Math verification:**
```
amount   = 2.0000 * 1000.00 = 2000.00
tax      = 2000.00 * 15 / 100 = 300.00
subtotal = 2000.00
total    = 2000.00 + 300.00 = 2300.00
```

---

### INV-022: Tax-inclusive calculation — single line item

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = true
**And** TaxRate "VAT" at 15.00% exists
**And** a DRAFT invoice has one line: quantity=2.0000, unitPrice=R1,000.00, taxRateId=VAT

**Then** calculations are:
- Line amount: 2 x R1,000.00 = R2,000.00
- Divisor: 1 + (15/100) = 1.15
- Ex-tax amount: R2,000.00 / 1.15 = R1,739.13 (HALF_UP)
- Line tax: R2,000.00 - R1,739.13 = R260.87
- Invoice subtotal: R2,000.00
- Invoice taxAmount: R260.87
- Invoice total: R2,000.00 (inclusive — total equals subtotal)

**Math verification:**
```
amount    = 2.0000 * 1000.00 = 2000.00
divisor   = 1 + (15 / 100) = 1.15
exTax     = 2000.00 / 1.15 = 1739.13 (rounded HALF_UP, scale 2)
tax       = 2000.00 - 1739.13 = 260.87
subtotal  = 2000.00
total     = 2000.00 (tax-inclusive: total = subtotal)
```

---

### INV-023: Tax-exclusive calculation — multiple lines, same rate

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false, TaxRate "VAT" at 15.00%
**And** a DRAFT invoice has 3 lines, all with VAT applied:
- Line 1: quantity=2.0000, unitPrice=R500.00 → amount = R1,000.00
- Line 2: quantity=1.0000, unitPrice=R800.00 → amount = R800.00
- Line 3: quantity=0.5000, unitPrice=R500.00 → amount = R250.00

**Then:**
- Line 1 tax: R1,000.00 x 15% = R150.00
- Line 2 tax: R800.00 x 15% = R120.00
- Line 3 tax: R250.00 x 15% = R37.50
- Invoice subtotal: R1,000.00 + R800.00 + R250.00 = R2,050.00
- Invoice taxAmount: R150.00 + R120.00 + R37.50 = R307.50
- Invoice total: R2,050.00 + R307.50 = **R2,357.50**

**Math verification:**
```
line1_tax = 1000.00 * 15 / 100 = 150.00
line2_tax = 800.00 * 15 / 100  = 120.00
line3_tax = 250.00 * 15 / 100  = 37.50
subtotal  = 1000.00 + 800.00 + 250.00 = 2050.00
taxAmount = 150.00 + 120.00 + 37.50   = 307.50
total     = 2050.00 + 307.50          = 2357.50
```

---

### INV-024: Tax-exempt line excluded from tax calculation

**Severity:** High
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false, TaxRate "VAT" at 15.00%, TaxRate "Zero-rated" at 0.00% (exempt=true)
**And** a DRAFT invoice has 2 lines:
- Line 1: quantity=2.0000, unitPrice=R1,000.00, taxRateId=VAT → amount=R2,000.00
- Line 2: quantity=1.0000, unitPrice=R500.00, taxRateId=Zero-rated (exempt) → amount=R500.00

**Then:**
- Line 1 tax: R2,000.00 x 15% = R300.00
- Line 2 tax: R0.00 (exempt — short-circuited to ZERO regardless of rate)
- Invoice subtotal: R2,000.00 + R500.00 = R2,500.00
- Invoice taxAmount: R300.00
- Invoice total: R2,500.00 + R300.00 = **R2,800.00**

---

### INV-025: Mixed tax rates on different lines

**Severity:** High
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false
**And** TaxRate "VAT" at 15.00% and TaxRate "Reduced" at 5.00% (non-exempt) exist
**And** a DRAFT invoice has:
- Line 1: quantity=1.0000, unitPrice=R10,000.00, taxRateId=VAT → amount=R10,000.00
- Line 2: quantity=1.0000, unitPrice=R4,000.00, taxRateId=Reduced → amount=R4,000.00

**Then:**
- Line 1 tax: R10,000.00 x 15% = R1,500.00
- Line 2 tax: R4,000.00 x 5% = R200.00
- Invoice subtotal: R10,000.00 + R4,000.00 = R14,000.00
- Invoice taxAmount: R1,500.00 + R200.00 = R1,700.00
- Invoice total: R14,000.00 + R1,700.00 = **R15,700.00**

**And** the tax breakdown contains 2 entries:
- "VAT" (15.00%): taxableAmount=R10,000.00, taxAmount=R1,500.00
- "Reduced" (5.00%): taxableAmount=R4,000.00, taxAmount=R200.00

---

### INV-026: Line with no tax rate — no tax calculated

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false
**And** a DRAFT invoice has 1 line with no taxRateId: quantity=3.0000, unitPrice=R1,000.00

**Then:**
- Line amount: R3,000.00
- Line taxAmount: null (no tax rate applied)
- Invoice subtotal: R3,000.00
- Invoice taxAmount: R0.00 (no per-line tax present)
- Invoice total: R3,000.00

---

## 5. Unbilled Time to Invoice Generation

### INV-027: Generate invoice from unbilled time entries

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** Acme Corp is ACTIVE with project "Acme Website Redesign"
**And** 3 billable, UNBILLED time entries exist for that project:
- Entry A: 120 mins by James (billingRateSnapshot=R500, currency=ZAR)
- Entry B: 60 mins by Marcus (billingRateSnapshot=R800, currency=ZAR)
- Entry C: 30 mins by James (billingRateSnapshot=R500, currency=ZAR)

**When** James sends POST /api/invoices with:
```json
{
  "customerId": "<acme-corp-id>",
  "currency": "ZAR",
  "timeEntryIds": ["<entry-a-id>", "<entry-b-id>", "<entry-c-id>"],
  "dueDate": "today+30",
  "paymentTerms": "Net 30"
}
```

**Then** response status is 201 Created
**And** invoice has 3 line items:
- Line 1: quantity=2.0000 (120/60), unitPrice=R500.00, amount=R1,000.00
- Line 2: quantity=1.0000 (60/60), unitPrice=R800.00, amount=R800.00
- Line 3: quantity=0.5000 (30/60), unitPrice=R500.00, amount=R250.00
**And** invoice subtotal = R2,050.00

**Math:**
```
line1: 120/60 = 2.0000 hrs * R500.00 = R1,000.00
line2: 60/60  = 1.0000 hrs * R800.00 = R800.00
line3: 30/60  = 0.5000 hrs * R500.00 = R250.00
subtotal = R1,000.00 + R800.00 + R250.00 = R2,050.00
```

---

### INV-028: Double-billing prevention — time entries linked to invoice

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** time entry A was included in Invoice 1 (entry A now has invoiceId set)
**When** James sends POST /api/invoices with:
```json
{
  "customerId": "<acme-corp-id>",
  "currency": "ZAR",
  "timeEntryIds": ["<entry-a-id>"]
}
```
**Then** response status is 409 Conflict
**And** error message contains "already linked to an invoice"

---

### INV-029: Non-billable time entry rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a time entry exists with billable=false (NON_BILLABLE)
**When** James includes it in timeEntryIds for invoice creation
**Then** response status is 400 Bad Request
**And** error message contains "not marked as billable"

---

### INV-030: Time entry without billing rate uses zero unit price

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a billable time entry exists with billingRateSnapshot=null, duration=60 mins
**When** James includes it in invoice creation
**Then** line item has unitPrice=R0.00 and amount=R0.00

---

### INV-031: Currency mismatch between time entry and invoice rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a billable time entry with billingRateCurrency="USD"
**When** James creates an invoice with currency="ZAR" including that entry
**Then** response status is 400 Bad Request
**And** error message contains "Currency mismatch"

---

### INV-032: Time entry belonging to different customer's project rejected

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** a time entry belongs to a project linked to Dunbar & Associates (not Acme Corp)
**When** James creates an invoice for Acme Corp including that time entry
**Then** response status is 400 Bad Request
**And** error message contains "not linked to customer"

---

### INV-033: Validate generation endpoint checks prerequisites

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** Acme Corp has all required customer fields filled
**And** 2 unbilled time entries exist, both with billing rates set
**When** James sends POST /api/invoices/validate-generation with:
```json
{
  "customerId": "<acme-corp-id>",
  "timeEntryIds": ["<entry-a-id>", "<entry-b-id>"]
}
```
**Then** response status is 200 OK
**And** response contains validation checks:
- "customer_required_fields": passed=true
- "org_name": passed=true
- "time_entry_rates": passed=true

---

### INV-034: Validate generation warns about missing billing rates

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** 2 time entries, one with billingRateSnapshot=null
**When** James validates generation with both entry IDs
**Then** response contains "time_entry_rates" check: passed=false, message="1 time entries without billing rates"
**And** severity = "WARNING" (not blocking)

---

### INV-035: Time entry without task is rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a billable time entry exists with taskId=null
**When** James includes it in invoice creation
**Then** response status is 400 Bad Request
**And** error message contains "not linked to a task"

---

### INV-036: Default tax rate auto-applied to generated lines

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a default TaxRate "VAT" (15%) is configured
**And** OrgSettings.taxInclusive = false
**When** James creates an invoice from 2 unbilled time entries:
- Entry A: 60 mins @ R500/hr → line amount = R500.00
- Entry B: 120 mins @ R800/hr → line amount = R1,600.00

**Then** both line items have:
- `taxRateId` = VAT ID
- `taxRateName` = "VAT"
- `taxRatePercent` = 15.00
**And** line A tax = R500.00 x 15% = R75.00
**And** line B tax = R1,600.00 x 15% = R240.00
**And** invoice taxAmount = R75.00 + R240.00 = R315.00
**And** invoice total = R2,100.00 + R315.00 = **R2,415.00**

---

## 6. Invoice Lifecycle

### INV-037: Full happy path — DRAFT to APPROVED to SENT to PAID

**Severity:** Critical
**Actor:** James Chen (Admin)

**Step 1 — Create draft:**
**Given** Acme Corp is ACTIVE
**When** James creates a draft invoice for Acme Corp with 1 line (R2,000.00)
**Then** status = DRAFT, invoiceNumber = null

**Step 2 — Approve:**
**When** James sends POST /api/invoices/{id}/approve
**Then** status = APPROVED
**And** invoiceNumber = "INV-NNNN" (assigned)
**And** issueDate = today
**And** approvedBy = James's member ID

**Step 3 — Send:**
**When** James sends POST /api/invoices/{id}/send
**Then** status = SENT

**Step 4 — Record payment:**
**When** James sends POST /api/invoices/{id}/payment with:
```json
{
  "paymentReference": "EFT-20260306-001"
}
```
**Then** status = PAID
**And** paymentReference = "EFT-20260306-001"
**And** paidAt is set to current timestamp

---

### INV-038: Void from APPROVED status

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an APPROVED invoice exists
**When** James sends POST /api/invoices/{id}/void
**Then** status = VOID
**And** invoiceNumber is retained (not cleared)

---

### INV-039: Void from SENT status

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a SENT invoice exists
**When** James sends POST /api/invoices/{id}/void
**Then** status = VOID

---

### INV-040: Void from DRAFT is rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists
**When** James sends POST /api/invoices/{id}/void
**Then** response status is 400 Bad Request
**And** error message contains "Only approved or sent invoices can be voided"

---

### INV-041: Void from PAID is rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a PAID invoice exists
**When** James sends POST /api/invoices/{id}/void
**Then** response status is 400 Bad Request
**And** error message contains "Only approved or sent invoices can be voided"

---

### INV-042: Send from DRAFT is rejected (must approve first)

**Severity:** High
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists
**When** James sends POST /api/invoices/{id}/send
**Then** response status is 400 Bad Request
**And** error message contains "Only approved invoices can be sent"

---

### INV-043: Record payment on APPROVED (not SENT) is rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an APPROVED invoice exists (not yet sent)
**When** James sends POST /api/invoices/{id}/payment
**Then** response status is 400 Bad Request
**And** error message contains "Only sent invoices can be paid"

---

### INV-044: Approve DRAFT with no lines still succeeds

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists with zero line items (subtotal = R0.00)
**When** James sends POST /api/invoices/{id}/approve
**Then** response status is 200 OK
**And** status = APPROVED, invoiceNumber is assigned
**And** total = R0.00

---

### INV-045: Blocked transitions matrix

**Severity:** High
**Actor:** James Chen (Admin)

| Current Status | approve | send | payment | void | Expected |
|----------------|---------|------|---------|------|----------|
| DRAFT          | OK      | 400  | 400     | 400  | Only approve from DRAFT |
| APPROVED       | 400     | OK   | 400     | OK   | Send or void from APPROVED |
| SENT           | 400     | 400  | OK      | OK   | Pay or void from SENT |
| PAID           | 400     | 400  | 400     | 400  | Terminal state |
| VOID           | 400     | 400  | 400     | 400  | Terminal state |

For each invalid transition, verify:
**When** the action is attempted on an invoice in the wrong status
**Then** response status is 400 Bad Request
**And** the invoice status remains unchanged

---

## 7. Invoice Preview & PDF

### INV-046: Preview returns HTML with invoice data

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an APPROVED invoice exists for Acme Corp with:
- invoiceNumber = "INV-0001"
- 2 line items (R1,000.00 and R800.00)
- subtotal = R1,800.00, taxAmount = R270.00 (15% VAT), total = R2,070.00
- notes = "Thank you for your business"

**When** James sends GET /api/invoices/{id}/preview
**Then** response status is 200 OK
**And** Content-Type is "text/html"
**And** HTML body contains:
- "INV-0001" (invoice number)
- "Acme Corp" (customer name)
- "DocTeams" (org name)
- "R1,800.00" or "1800.00" (subtotal)
- "R2,070.00" or "2070.00" (total)
- "Thank you for your business" (notes)
- Line item descriptions and amounts

---

### INV-047: Preview of DRAFT invoice (no invoice number)

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists with no invoice number
**When** James sends GET /api/invoices/{id}/preview
**Then** response status is 200 OK
**And** HTML does not contain "INV-" (no number assigned yet)
**And** other invoice data (customer, lines, totals) is present

---

## 8. Portal Invoice Access

### INV-048: Portal contact lists invoices for their customer

**Severity:** Critical
**Actor:** ben.finance@acmecorp.com (Portal — BILLING)

**Given** Acme Corp has 2 invoices: INV-0001 (SENT), INV-0002 (PAID)
**And** Dunbar has 1 invoice: INV-0003 (SENT)
**When** ben.finance authenticates via portal JWT and sends GET /portal/invoices
**Then** response status is 200 OK
**And** response contains exactly 2 invoices (INV-0001, INV-0002)
**And** each has: id, invoiceNumber, status, issueDate, dueDate, total, currency
**And** Dunbar's INV-0003 is NOT included (tenant-scoped to customer)

---

### INV-049: Portal contact views invoice detail with line items

**Severity:** High
**Actor:** ben.finance@acmecorp.com (Portal — BILLING)

**Given** INV-0001 (SENT) exists for Acme Corp with 2 line items and VAT
**When** ben.finance sends GET /portal/invoices/{id}
**Then** response status is 200 OK
**And** response contains:
- invoiceNumber, status, issueDate, dueDate
- subtotal, taxAmount, total, currency
- notes
- paymentUrl (if payment link was generated)
- lines[] array with description, quantity, unitPrice, amount, taxRateName, taxRatePercent, taxAmount
- taxBreakdown[] (grouped by rate)
- taxRegistrationNumber, taxLabel, taxInclusive flag

---

### INV-050: Portal contact checks payment status

**Severity:** High
**Actor:** ben.finance@acmecorp.com (Portal — BILLING)

**Given** INV-0001 is SENT with a payment link generated
**When** ben.finance sends GET /portal/invoices/{id}/payment-status
**Then** response status is 200 OK
**And** response contains current payment status information

---

### INV-051: Portal contact downloads invoice PDF

**Severity:** High
**Actor:** ben.finance@acmecorp.com (Portal — BILLING)

**Given** INV-0001 has a generated PDF document stored in S3
**When** ben.finance sends GET /portal/invoices/{id}/download
**Then** response status is 200 OK
**And** response contains `downloadUrl` (presigned S3 URL)

---

### INV-052: Portal contact cannot access another customer's invoice

**Severity:** Critical
**Actor:** ben.finance@acmecorp.com (Portal — BILLING)

**Given** INV-0003 belongs to Dunbar & Associates (not Acme Corp)
**When** ben.finance sends GET /portal/invoices/{dunbar-invoice-id}
**Then** response status is 404 Not Found (not 403 — security by obscurity)

---

### INV-053: Portal contacts only see non-DRAFT invoices

**Severity:** High
**Actor:** ben.finance@acmecorp.com (Portal — BILLING)

**Given** Acme Corp has invoices: DRAFT, APPROVED, SENT, PAID
**When** ben.finance sends GET /portal/invoices
**Then** response does NOT include the DRAFT invoice
**And** response includes APPROVED, SENT, and PAID invoices

---

## 9. RBAC for Invoicing

### INV-054: Admin can perform all invoice operations

**Severity:** High
**Actor:** James Chen (Admin)

**When** James (ORG_ADMIN) performs each of these operations:
- POST /api/invoices (create)
- PUT /api/invoices/{id} (update)
- DELETE /api/invoices/{id} (delete)
- GET /api/invoices/{id} (read)
- GET /api/invoices (list)
- POST /api/invoices/{id}/lines (add line)
- PUT /api/invoices/{id}/lines/{lineId} (update line)
- DELETE /api/invoices/{id}/lines/{lineId} (delete line)
- POST /api/invoices/{id}/approve
- POST /api/invoices/{id}/send
- POST /api/invoices/{id}/payment
- POST /api/invoices/{id}/void
- GET /api/invoices/{id}/preview
- PUT /api/invoices/{id}/custom-fields
- PUT /api/invoices/{id}/field-groups
- POST /api/invoices/validate-generation

**Then** all operations succeed (no 403)

---

### INV-055: Owner can perform all invoice operations

**Severity:** High
**Actor:** Thandi Nkosi (Owner)

**When** Thandi (ORG_OWNER) performs the same operations as INV-054
**Then** all operations succeed (no 403)

---

### INV-056: Member is blocked from all invoice operations

**Severity:** Critical
**Actor:** Lerato Dlamini (Member)

**When** Lerato (ORG_MEMBER) attempts each invoice endpoint:
- POST /api/invoices → 403 Forbidden
- PUT /api/invoices/{id} → 403 Forbidden
- DELETE /api/invoices/{id} → 403 Forbidden
- GET /api/invoices/{id} → 403 Forbidden
- GET /api/invoices → 403 Forbidden
- POST /api/invoices/{id}/lines → 403 Forbidden
- POST /api/invoices/{id}/approve → 403 Forbidden
- POST /api/invoices/{id}/send → 403 Forbidden
- POST /api/invoices/{id}/payment → 403 Forbidden
- POST /api/invoices/{id}/void → 403 Forbidden

**Then** every operation returns 403
**And** no data is leaked in error responses

---

### INV-057: Cross-admin verification — Marcus sees James's invoices

**Severity:** Medium
**Actor:** Marcus Webb (Admin)

**Given** James created a draft invoice for Acme Corp
**When** Marcus sends GET /api/invoices/{id}
**Then** response status is 200 OK
**And** Marcus can view the full invoice detail (same org, both Admins)

---

## 10. Financial Accuracy

### INV-058: Exact calculation — 3 time entries, tax-exclusive 15%

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false, TaxRate "VAT" at 15.00%
**And** 3 unbilled time entries for Acme Corp:
- Entry A: 120 mins, billingRateSnapshot=R500.00
- Entry B: 60 mins, billingRateSnapshot=R800.00
- Entry C: 30 mins, billingRateSnapshot=R500.00

**When** James creates an invoice from all 3 entries

**Then:**
```
Line A: quantity = 120/60 = 2.0000, unitPrice = R500.00
  amount = 2.0000 * 500.00 = R1,000.00
  tax    = 1000.00 * 15 / 100 = R150.00

Line B: quantity = 60/60 = 1.0000, unitPrice = R800.00
  amount = 1.0000 * 800.00 = R800.00
  tax    = 800.00 * 15 / 100 = R120.00

Line C: quantity = 30/60 = 0.5000, unitPrice = R500.00
  amount = 0.5000 * 500.00 = R250.00
  tax    = 250.00 * 15 / 100 = R37.50

Subtotal  = 1000.00 + 800.00 + 250.00 = R2,050.00
Tax total = 150.00 + 120.00 + 37.50   = R307.50
Total     = 2050.00 + 307.50          = R2,357.50
```

**And** invoice.subtotal = 2050.00
**And** invoice.taxAmount = 307.50
**And** invoice.total = 2357.50

---

### INV-059: Exact calculation — tax-inclusive 15%, multi-line

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = true, TaxRate "VAT" at 15.00%
**And** 2 manual lines added to a DRAFT invoice:
- Line 1: quantity=1.0000, unitPrice=R1,150.00 → amount=R1,150.00
- Line 2: quantity=1.0000, unitPrice=R575.00 → amount=R575.00

**Then:**
```
Line 1: divisor = 1.15
  exTax = 1150.00 / 1.15 = R1,000.00
  tax   = 1150.00 - 1000.00 = R150.00

Line 2: divisor = 1.15
  exTax = 575.00 / 1.15 = R500.00
  tax   = 575.00 - 500.00 = R75.00

Subtotal  = 1150.00 + 575.00 = R1,725.00
Tax total = 150.00 + 75.00   = R225.00
Total     = R1,725.00 (tax-inclusive: total = subtotal)
```

**And** invoice.subtotal = 1725.00
**And** invoice.taxAmount = 225.00
**And** invoice.total = 1725.00

---

### INV-060: Exact calculation — fractional hours produce correct rounding

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false, TaxRate "VAT" at 15.00%
**And** 1 time entry: 45 mins, billingRateSnapshot=R700.00

**Then:**
```
quantity = 45 / 60 = 0.7500 (scale 4, HALF_UP)
amount   = 0.7500 * 700.00 = R525.00
tax      = 525.00 * 15 / 100 = R78.75
subtotal = R525.00
total    = 525.00 + 78.75 = R603.75
```

---

### INV-061: Exact calculation — rounding edge case with tax-inclusive

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = true, TaxRate "VAT" at 15.00%
**And** 1 manual line: quantity=1.0000, unitPrice=R100.00 → amount=R100.00

**Then:**
```
divisor = 1.15
exTax   = 100.00 / 1.15 = 86.96 (HALF_UP: 86.9565... → 86.96)
tax     = 100.00 - 86.96 = R13.04
subtotal = R100.00
total    = R100.00
```

Note: R86.96 + R13.04 = R100.00 (no rounding loss)

---

### INV-062: Large invoice — 10 lines, mixed rates

**Severity:** High
**Actor:** Marcus Webb (Admin)

**Given** OrgSettings.taxInclusive = false
**And** TaxRate "VAT" 15.00%, TaxRate "Reduced" 5.00%
**And** DRAFT invoice with 10 lines:

| # | qty    | unitPrice | taxRate  | amount      | tax        |
|---|--------|-----------|----------|-------------|------------|
| 1 | 2.0000 | R500.00   | VAT 15%  | R1,000.00   | R150.00    |
| 2 | 1.0000 | R800.00   | VAT 15%  | R800.00     | R120.00    |
| 3 | 0.5000 | R500.00   | VAT 15%  | R250.00     | R37.50     |
| 4 | 3.0000 | R1,000.00 | VAT 15%  | R3,000.00   | R450.00    |
| 5 | 1.5000 | R600.00   | VAT 15%  | R900.00     | R135.00    |
| 6 | 4.0000 | R300.00   | Reduced 5% | R1,200.00 | R60.00     |
| 7 | 2.0000 | R400.00   | Reduced 5% | R800.00   | R40.00     |
| 8 | 1.0000 | R2,000.00 | VAT 15%  | R2,000.00   | R300.00    |
| 9 | 0.2500 | R800.00   | VAT 15%  | R200.00     | R30.00     |
| 10| 1.0000 | R500.00   | (none)   | R500.00     | R0.00      |

**Then:**
```
Subtotal = 1000 + 800 + 250 + 3000 + 900 + 1200 + 800 + 2000 + 200 + 500 = R10,650.00
VAT total  = 150 + 120 + 37.50 + 450 + 135 + 300 + 30 = R1,222.50
Reduced total = 60 + 40 = R100.00
Tax amount = 1222.50 + 100.00 = R1,322.50
Total = 10650.00 + 1322.50 = R11,972.50
```

**And** tax breakdown has 2 entries:
- "VAT" (15.00%): taxableAmount=R8,150.00, taxAmount=R1,222.50
- "Reduced" (5.00%): taxableAmount=R2,000.00, taxAmount=R100.00

---

## 11. Edge Cases

### INV-063: Zero-amount invoice

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists with no line items
**Then** subtotal=R0.00, taxAmount=R0.00, total=R0.00

**When** James approves the invoice
**Then** status = APPROVED, invoiceNumber assigned
**And** total remains R0.00

**When** James sends the invoice
**Then** status = SENT

**When** James records payment with reference "PRO-BONO-001"
**Then** status = PAID, paymentReference="PRO-BONO-001"

---

### INV-064: Single line item invoice

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice with exactly 1 line: quantity=1.0000, unitPrice=R5,000.00
**And** OrgSettings.taxInclusive = false, VAT at 15%

**Then:**
- subtotal = R5,000.00
- taxAmount = R750.00
- total = R5,750.00

**When** James approves, sends, and records payment
**Then** full lifecycle completes with correct totals preserved at each step

---

### INV-065: Delete all lines from invoice

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice with 2 lines: R1,000.00 and R800.00 (subtotal=R1,800.00)
**When** James deletes line 1 and then line 2
**Then** invoice has no lines, subtotal=R0.00, taxAmount=R0.00, total=R0.00

---

### INV-066: Void invoice then create new invoice from same time entries

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** Invoice A was created from time entries [E1, E2], approved, then voided
**And** time entries E1 and E2 still have invoiceId = Invoice A's ID

**When** James attempts to create a new invoice from [E1, E2]
**Then** either:
- (a) The system unlinks time entries on void, allowing re-invoicing → 201 Created
- (b) Time entries remain linked to the voided invoice → 409 Conflict with "already linked to an invoice"

**Expected behavior note:** Verify which behavior the system implements and document it. If (b), the user must manually unlink time entries before re-invoicing voided work.

---

### INV-067: Invoice for customer with missing email

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** Acme Corp has customerEmail = null
**When** James creates a draft invoice for Acme Corp
**Then** response status is 201 Created
**And** invoice.customerEmail = null (snapshot of current state)
**And** invoice can be approved

**When** James attempts to send the invoice
**Then** behavior depends on email being required for send (verify: does send fail without email or is it optional?)

---

### INV-068: Custom fields on invoice

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice exists
**When** James sends PUT /api/invoices/{id}/custom-fields with:
```json
{
  "customFields": {
    "po_number": "PO-2026-0042",
    "department": "Engineering"
  }
}
```
**Then** response status is 200 OK
**And** invoice.customFields contains both keys with correct values

---

### INV-069: Concurrent invoice approval numbering

**Severity:** Critical
**Actor:** James Chen (Admin) + Marcus Webb (Admin)

**Given** 2 DRAFT invoices exist
**When** James approves Invoice A at the same time Marcus approves Invoice B
**Then** each receives a unique, sequential invoice number (no duplicates, no gaps)
**And** the counter atomically increments via the UPSERT pattern

**Verification:** Check that INV-NNNN numbers are unique across both invoices.

---

### INV-070: Approve sets issueDate to today only if null

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice with issueDate already set to "2026-04-01"
**When** James approves it on 2026-03-06
**Then** issueDate remains "2026-04-01" (not overwritten to today)

**Given** a DRAFT invoice with issueDate = null
**When** James approves it on 2026-03-06
**Then** issueDate = "2026-03-06" (auto-set to today)

---

### INV-071: Payment events history

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** an invoice has had a payment link generated and payment recorded
**When** James sends GET /api/invoices/{id}/payment-events
**Then** response status is 200 OK
**And** response contains a list of payment events with status history

---

### INV-072: Refresh payment link on sent invoice

**Severity:** Medium
**Actor:** James Chen (Admin)

**Given** a SENT invoice with an expired payment link
**When** James sends POST /api/invoices/{id}/refresh-payment-link
**Then** response status is 200 OK
**And** invoice.paymentUrl is updated to a new URL

---

### INV-073: Invoice line sort order preserved

**Severity:** Low
**Actor:** James Chen (Admin)

**Given** a DRAFT invoice with 3 lines: sortOrder 0, 1, 2
**When** James retrieves the invoice via GET
**Then** lines are returned in sortOrder ascending (0, 1, 2)

**When** James updates line at sortOrder 1 to sortOrder 5
**Then** the new order is: 0, 2, 5

---

### INV-074: Audit events created for invoice lifecycle

**Severity:** High
**Actor:** James Chen (Admin)

**Given** James creates, approves, sends, and records payment on an invoice
**Then** the audit log contains entries for:
- `invoice.created`
- `invoice.approved`
- `invoice.sent`
- `invoice.paid`

Each audit event includes:
- entityType = "invoice"
- entityId = the invoice UUID
- details with relevant context (customer_id, invoice_number, etc.)

---

### INV-075: Invoice with expense line items

**Severity:** High
**Actor:** James Chen (Admin)

**Given** Acme Corp has a project with 2 billable expenses:
- Expense A: R500.00 (category: TRAVEL, description: "Client visit")
- Expense B: R1,200.00 (category: SOFTWARE, description: "License renewal")
**And** neither expense is already invoiced

**When** James creates an invoice with expenseIds=[A, B]
**Then** response status is 201 Created
**And** 2 line items with lineType=EXPENSE:
- Line 1: description="Client visit [TRAVEL]", quantity=1.0000, unitPrice=R500.00, amount=R500.00
- Line 2: description="License renewal [SOFTWARE]", quantity=1.0000, unitPrice=R1,200.00, amount=R1,200.00
**And** subtotal = R1,700.00
**And** both expenses are marked as billed (expense.invoiceId set)

---

### INV-076: Double-billing prevention for expenses

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** Expense A was already included in Invoice 1 (expense.invoiceId is set)
**When** James creates a new invoice including Expense A in expenseIds
**Then** response status is 409 Conflict
**And** error message contains "already linked to an invoice"

---

### INV-077: Mixed time entries and expenses in single invoice

**Severity:** High
**Actor:** James Chen (Admin)

**Given** OrgSettings.taxInclusive = false, TaxRate "VAT" 15%
**And** 1 unbilled time entry: 120 mins @ R500/hr
**And** 1 billable expense: R800.00

**When** James creates invoice with both timeEntryIds and expenseIds

**Then:**
```
Time line: 2.0000 hrs * R500.00 = R1,000.00, tax = R150.00
Expense line: 1.0000 * R800.00 = R800.00, tax = R120.00

Subtotal  = R1,000.00 + R800.00 = R1,800.00
Tax       = R150.00 + R120.00   = R270.00
Total     = R1,800.00 + R270.00 = R2,070.00
```

**And** time lines appear first (sortOrder 0), expense lines after (sortOrder 1)

---

### INV-078: Non-billable expense is rejected

**Severity:** High
**Actor:** James Chen (Admin)

**Given** an expense exists with billable=false
**When** James includes it in expenseIds for invoice creation
**Then** response status is 400 Bad Request
**And** error message contains "not marked as billable"

---

### INV-079: Expense belonging to different customer's project rejected

**Severity:** Critical
**Actor:** James Chen (Admin)

**Given** an expense belongs to a project linked to Dunbar (not Acme Corp)
**When** James creates an invoice for Acme Corp including that expense
**Then** response status is 400 Bad Request
**And** error message contains "not linked to customer"
