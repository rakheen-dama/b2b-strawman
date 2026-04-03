# Invoicing & Billing

## Invoices

### What an Invoice Is
An invoice is a billing document sent to a customer. Invoices can be manually created or generated from unbilled time entries. They follow a strict lifecycle with audited state transitions.

### Invoice Fields
| Field | Type | Notes |
|-------|------|-------|
| Invoice Number | Text | Auto-generated at approval (not at draft creation) |
| Customer | Link | Required |
| Status | Enum | DRAFT, APPROVED, SENT, PAID, VOID |
| Currency | Text | e.g., "ZAR", "USD" |
| Issue Date | Date | Set at approval |
| Due Date | Date | User-configurable |
| Subtotal | Decimal | Sum of line item amounts (before tax) |
| Tax Amount | Decimal | Sum of line item tax amounts |
| Total | Decimal | Subtotal + Tax |
| Notes | Text | Optional notes to customer |
| Payment Terms | Text | e.g., "Net 30" |
| Payment URL | URL | Generated payment link (Stripe/PayFast) |
| Custom Fields | Dynamic | Based on applied field groups |

### Invoice Lifecycle

```
DRAFT ──→ APPROVED ──→ SENT ──→ PAID
              │          │
              └──→ VOID ←┘
```

| Status | Meaning | Actions Available |
|--------|---------|-------------------|
| DRAFT | Being composed | Edit lines, change customer, add/remove items. Only state where edits are allowed. |
| APPROVED | Ready to send | Invoice number assigned, issue date set. Can void. |
| SENT | Delivered to customer | Email sent (if email configured). Payment link active. Can record payment or void. |
| PAID | Payment received | Terminal. Payment reference and date recorded. |
| VOID | Cancelled | Terminal. Reason recorded in audit trail. |

### Invoice Line Items
Each invoice has one or more line items:

| Field | Type | Notes |
|-------|------|-------|
| Description | Text | What was done |
| Quantity | Decimal | Hours, units, etc. |
| Unit Price | Decimal | Rate per unit |
| Amount | Decimal | Quantity × Unit Price |
| Project | Link | Optional (which project this work relates to) |
| Time Entry | Link | Optional (if generated from time) |
| Retainer Period | Link | Optional (if from retainer billing) |
| Tax Rate | Link | Optional (from org tax rates) |
| Tax Amount | Decimal | Calculated from tax rate |
| Tax Exempt | Boolean | Override to skip tax |

### Invoice Generation from Time

The most common flow for creating invoices:

1. Navigate to customer → Invoices tab
2. Click "Generate Invoice" (or from customer's unbilled time summary)
3. **Generation dialog** opens:
   - Shows unbilled time entries for this customer
   - Filter by date range, project
   - Select which entries to include (checkboxes)
   - Option to select a document template (for PDF formatting)
4. System validates:
   - Customer has required fields (checked against template requirements)
   - All selected time entries are billable and unbilled
   - No double-billing (entries not on another draft)
5. Creates DRAFT invoice with line items from selected time entries
6. User reviews, edits if needed, then approves

### Manual Invoice Creation
- Click "New Invoice" → select customer → empty draft
- Add line items manually (description, qty, price)
- Same lifecycle from there

### Invoice Detail Page
- **Header**: Invoice number, status badge, customer name, dates
- **Line items table**: description, qty, unit price, tax, amount per line
- **Totals section**: subtotal, tax breakdown (per tax rate), total
- **Action buttons** (context-dependent):
  - DRAFT: Edit, Approve, Delete
  - APPROVED: Send, Void, Download PDF
  - SENT: Record Payment, Void, Download PDF, Refresh Payment Link
  - PAID: Download PDF, View Certificate
- **Payment History**: timeline of payment events (link sent, payment attempted, payment received)
- **HTML Preview**: rendered invoice as it would appear in PDF

### Invoice List Page
- Table: invoice number, customer, status, total, issue date, due date
- Filter by status, customer
- Status badges (color-coded)
- Click row → invoice detail

---

## Tax Handling

### Tax Rates (Settings)
- Org defines tax rates: name (e.g., "VAT"), percentage (e.g., 15%), default flag
- Multiple rates supported (e.g., VAT 15%, Zero-rated 0%)
- Default rate auto-applied to new invoice lines

### Per-Line Tax
- Each line item can have a different tax rate (or be tax-exempt)
- Tax amount = line amount × tax rate percentage
- Invoice total = sum of (line amounts + line tax amounts)

### Tax Display
- Invoice detail shows tax breakdown by rate (e.g., "VAT 15%: R1,500.00")
- PDF preview and portal both show full tax breakdown

---

## Payment Collection

### Payment Gateways
Two payment providers supported (configurable per org in Settings → Integrations):
- **Stripe** — international payments
- **PayFast** — South African payments

### Payment Flow
1. Invoice SENT → system generates a payment link (via configured gateway)
2. Link sent to customer (email and/or portal)
3. Customer clicks link → redirected to payment provider's hosted checkout
4. Payment provider sends webhook on success
5. System reconciles: marks invoice PAID, records payment event

### Payment Link Refresh
- If a payment link expires or needs regenerating: "Refresh Payment Link" action on sent invoices

### Record Manual Payment
- For payments received outside the platform (bank transfer, cash)
- "Record Payment" action → enters payment reference and date
