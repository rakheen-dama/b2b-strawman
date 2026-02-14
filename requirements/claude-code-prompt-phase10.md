You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema-per-tenant) tiers.
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups and "My Work" cross-project dashboard.
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Customer portal backend** (Phase 7): magic links, read-model schema, portal contacts, portal APIs.
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate` (3-level hierarchy: org-default → project-override → customer-override), `CostRate`, `ProjectBudget`, `OrgSettings` (default currency). Time entries have `billable` flag, `billing_rate_snapshot`, `cost_rate_snapshot`. Profitability reports (project, customer, org, utilization). Budget alerts via notification pipeline.
- **Operational dashboards** (Phase 9, planned): company dashboard, project overview, personal dashboard, health scoring.

For **Phase 10**, I want to add **Invoicing & Billing from Time** — the bridge between tracked billable work and revenue collection. This phase introduces multi-project invoices, a complete invoice lifecycle, and a mocked payment service provider (PSP) adapter for future Stripe/payment integration.

***

## Objective of Phase 10

Design and specify:

1. **Invoice entity with multi-project line items** — invoices linked to a customer, with line items that can span multiple projects. Auto-generation from unbilled time entries plus support for manual line items.
2. **Invoice lifecycle** — Draft → Approved → Sent → Paid → Void, with each transition audited and notified.
3. **Unbilled time management** — mechanism to prevent double-billing: time entries are marked as "billed" when an invoice is approved. Clear visibility into what time is unbilled.
4. **Invoice generation flow** — select a customer + date range → pull unbilled billable time across their projects → group by project → produce an editable draft.
5. **HTML invoice preview** — a self-contained, print-friendly, PDF-ready invoice template rendered in the browser.
6. **Mocked PSP integration** — a `PaymentProvider` interface with a `MockPaymentProvider` implementation that generates fake payment references. Clean adapter seam for future Stripe integration.
7. **Internal payment recording** — staff manually marks invoices as paid (no customer-facing payment portal in this phase).

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - External payment services or PSP SDKs — the PSP is entirely mocked in this phase.
    - PDF generation libraries — HTML preview is the only rendering target; the template is structured for future PDF conversion.
    - Scheduled jobs or background workers — invoice generation and payment recording are user-initiated actions.
    - Separate microservices — everything stays in the existing backend deployable.
- All monetary amounts use `BigDecimal` (backend) / formatted strings (API responses). No floating-point currency math.
- Currency codes follow ISO 4217 (e.g., "ZAR", "USD", "GBP", "EUR").
- Invoice amounts are always in a single currency per invoice. Multi-currency time entries for the same customer require separate invoices per currency (or the user manually reconciles).

2. **Tenancy**

- All new entities (Invoice, InvoiceLine) follow the same tenant isolation model as existing entities:
    - Pro orgs: dedicated schema.
    - Starter orgs: `tenant_shared` schema with `tenant_id` column + Hibernate `@Filter` + RLS.
- All new entities must include Flyway migrations for both tenant and shared schemas.

3. **Permissions model**

- Invoice management (create, edit drafts, approve, send, void):
    - Org admins and owners: full access to all invoices.
    - Project leads: can generate invoices from their projects' time entries, but only org admins/owners can approve/send.
- Invoice viewing:
    - Org admins and owners: all invoices.
    - Project leads: invoices that contain line items from their projects.
    - Regular members: no invoice access (their time shows as "billed" in their time entry list, but they don't see invoices).
- Payment recording (marking as paid):
    - Org admins and owners only.

4. **Relationship to existing entities**

- **Customer**: An invoice belongs to exactly one customer. The customer's name, email, and address are snapshotted onto the invoice at creation time (so the invoice remains accurate even if customer details change later).
- **TimeEntry**: Line items can optionally reference a time entry. When an invoice is approved, referenced time entries are marked as `billed = true` with a reference back to the invoice. When an invoice is voided, those time entries revert to `billed = false`.
- **BillingRate**: Not directly referenced — the rate is already snapshotted on the time entry (`billing_rate_snapshot`). The invoice line item uses `time_entry.duration * time_entry.billing_rate_snapshot` for its amount.
- **ProjectBudget**: Invoiced amounts can be shown alongside budget data in the project financials tab (Phase 8), but no automated budget updates in this phase.
- **AuditEvent**: All invoice state transitions publish audit events.
- **Notification**: Invoice state changes (approved, sent, paid, voided) trigger notifications to relevant users.

5. **Out of scope for Phase 10**

- Recurring/scheduled invoicing — all invoices are manually generated.
- PDF generation — HTML template is PDF-ready but no server-side rendering.
- Customer-facing portal invoice views or payment links — invoices are internal-only in this phase.
- Tax calculation engine — invoices show pre-tax line items. A `tax_amount` field exists for manual entry but there's no tax rate configuration or auto-calculation.
- Credit notes or partial payments — an invoice is either fully paid or not.
- Multi-currency invoices — one currency per invoice. If a customer has time in multiple currencies, generate separate invoices.
- Approval workflows with multiple approvers — single approve action by an admin/owner.
- Email delivery of invoices — "Sent" status is a manual state change (the user sends the invoice outside the system or uses the HTML preview). Email integration is a future concern.
- Expense line items — only time-based and manual line items.

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase10-invoicing-billing.md`, plus ADRs for key decisions.

### 1. Invoice entity

Design an **Invoice** entity and supporting infrastructure:

1. **Data model**

    - `Invoice` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `customer_id` (UUID, FK → customers — the billed customer).
        - `invoice_number` (VARCHAR, unique per tenant — human-readable sequential number, e.g., "INV-0001").
        - `status` (ENUM: `DRAFT`, `APPROVED`, `SENT`, `PAID`, `VOID`).
        - `currency` (VARCHAR(3) — ISO 4217 code, all line items must be in this currency).
        - `issue_date` (DATE — when the invoice was issued/approved).
        - `due_date` (DATE — payment due date).
        - `subtotal` (DECIMAL(14,2) — sum of line item amounts before tax).
        - `tax_amount` (DECIMAL(14,2), default 0 — manually entered tax amount).
        - `total` (DECIMAL(14,2) — subtotal + tax_amount).
        - `notes` (TEXT, nullable — free-text notes displayed on the invoice).
        - `payment_terms` (VARCHAR, nullable — e.g., "Net 30", "Due on receipt").
        - `payment_reference` (VARCHAR, nullable — reference from PSP or manual entry).
        - `paid_at` (TIMESTAMP, nullable — when payment was recorded).
        - Customer snapshot fields (frozen at invoice creation):
            - `customer_name` (VARCHAR).
            - `customer_email` (VARCHAR, nullable).
            - `customer_address` (TEXT, nullable).
        - Org snapshot fields (frozen at invoice creation — for the "from" side of the invoice):
            - `org_name` (VARCHAR).
        - `created_by` (UUID — member who created the invoice).
        - `approved_by` (UUID, nullable — member who approved).
        - `created_at`, `updated_at` timestamps.
    - Constraints:
        - `invoice_number` is unique within a tenant.
        - `status` transitions are validated (see lifecycle below).
        - `currency` is immutable after creation.
    - Indexes:
        - `(customer_id, status)` for listing invoices per customer.
        - `(status)` for dashboard queries (e.g., all unpaid invoices).
        - `(invoice_number)` unique per tenant.
        - `(created_at)` for chronological listing.

2. **Invoice number generation**

    - Sequential per tenant: `INV-0001`, `INV-0002`, etc.
    - Use a tenant-scoped sequence or counter table to ensure gap-free sequential numbering.
    - The prefix `INV-` is hardcoded for v1. Future enhancement: configurable prefix per org in OrgSettings.
    - Invoice numbers are assigned when the invoice transitions from DRAFT to APPROVED (not at draft creation). Drafts have a temporary reference (e.g., "DRAFT-{uuid-short}") displayed in the UI.

3. **Invoice lifecycle and state transitions**

    ```
    DRAFT → APPROVED → SENT → PAID
      ↓         ↓        ↓
     (delete)  VOID     VOID
    ```

    - **DRAFT**: Editable. Line items can be added/removed/modified. Can be deleted entirely (hard delete — it was never finalized).
    - **DRAFT → APPROVED**: Validates all line items. Assigns the invoice number. Sets `issue_date` to today if not already set. Marks referenced time entries as `billed`. Records `approved_by`.
    - **APPROVED → SENT**: Marks the invoice as sent to the customer (manual action — no actual email). Sets `issue_date` if not already set.
    - **SENT → PAID**: Records payment. Sets `paid_at` and `payment_reference`.
    - **APPROVED → VOID** / **SENT → VOID**: Cancels the invoice. Reverts referenced time entries to `unbilled`. Void invoices retain their invoice number (never reused) and remain in the system for audit trail.
    - **DRAFT → (delete)**: Hard-deletes the draft. No audit trail needed for never-finalized drafts. Referenced time entries are not affected (they weren't marked as billed yet).

    Invalid transitions (e.g., PAID → DRAFT, VOID → anything) must be rejected with a 409 Conflict.

### 2. Invoice line items

Design an **InvoiceLine** entity:

1. **Data model**

    - `InvoiceLine` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `invoice_id` (UUID, FK → invoices).
        - `project_id` (UUID, nullable, FK → projects — which project this line relates to).
        - `time_entry_id` (UUID, nullable, FK → time_entries — if generated from a time entry).
        - `description` (TEXT — what the line item is for, e.g., "Backend development — 2025-01-15").
        - `quantity` (DECIMAL(10,4) — typically hours, but could be units for manual items).
        - `unit_price` (DECIMAL(12,2) — rate per unit, e.g., hourly rate).
        - `amount` (DECIMAL(14,2) — quantity × unit_price, computed and stored).
        - `sort_order` (INTEGER — display order within the invoice).
        - `created_at`, `updated_at` timestamps.
    - Constraints:
        - `invoice_id` is required.
        - `amount` must equal `quantity × unit_price` (enforced on write, not a DB constraint — allows manual override for fixed-fee items where quantity/unit_price may not apply cleanly).
        - `time_entry_id` if set, must be unique across all non-voided invoices (prevents double-billing at the DB level).
    - Indexes:
        - `(invoice_id, sort_order)` for ordered retrieval.
        - `(time_entry_id)` unique for non-voided invoices (partial unique index).
        - `(project_id)` for queries like "all invoice lines for this project."

2. **Line item types**

    - **Time-based line items**: Generated from unbilled time entries. `time_entry_id` is set. `quantity` = time entry duration in hours. `unit_price` = time entry's `billing_rate_snapshot`. `description` auto-generated from time entry metadata (task name, date, member name).
    - **Manual line items**: User-created. `time_entry_id` is null. `quantity` and `unit_price` are user-specified. `description` is user-written. Used for fixed fees, adjustments, discounts (negative amounts), or expenses.

3. **Grouping for display**

    - Line items are grouped by `project_id` in the invoice view. Manual items with no project appear in an "Other" section.
    - Within each project group, time-based items are sorted chronologically (by time entry date), then manual items by sort_order.

### 3. Time entry billing status

Extend the existing `TimeEntry` entity to track billing status:

1. **Schema changes to TimeEntry**

    - Add `invoice_id` (UUID, nullable, FK → invoices) — set when the time entry is included in an approved invoice. Cleared when the invoice is voided.
    - The existing `billable` flag (Phase 8) indicates whether time *can* be billed. The new `invoice_id` indicates whether it *has been* billed.
    - A time entry is "unbilled" if: `billable = true AND invoice_id IS NULL`.
    - A time entry is "billed" if: `invoice_id IS NOT NULL`.

2. **API changes to TimeEntry endpoints**

    - Existing time entry list endpoints gain a new filter: `billingStatus` with values `UNBILLED` (billable, no invoice), `BILLED` (has invoice), `NON_BILLABLE` (billable = false), `ALL` (default).
    - Time entry responses include `invoiceId` and `invoiceNumber` (nullable) for cross-referencing.
    - Time entries with `invoice_id` set cannot be edited or deleted (they're part of a finalized invoice). Return 409 Conflict if attempted. The user must void the invoice first.

3. **Unbilled time summary endpoint**

    - `GET /api/customers/{customerId}/unbilled-time` — returns unbilled billable time entries across all of the customer's projects, grouped by project, with totals per currency.
    - Query params: `from` (date), `to` (date) — date range filter.
    - This endpoint powers the invoice generation flow: the user sees what unbilled time exists before creating an invoice.

### 4. Invoice generation flow

Design the "create invoice from unbilled time" workflow:

1. **Step 1 — Preview unbilled time**

    - User navigates to Customer detail → "Invoices" tab → "New Invoice" button.
    - System calls `GET /api/customers/{customerId}/unbilled-time` to fetch unbilled entries.
    - Frontend displays unbilled entries grouped by project with checkboxes. User selects which entries to include and picks the invoice currency.
    - Entries in a different currency than the selected invoice currency are grayed out (cannot be included).

2. **Step 2 — Create draft**

    - User clicks "Create Draft".
    - Backend: `POST /api/invoices` with `customerId`, `currency`, and array of `timeEntryIds`.
    - Backend creates an Invoice in DRAFT status, generates InvoiceLines from the selected time entries (using their snapshotted rates), snapshots customer details, computes subtotal/total.
    - Returns the created draft with all line items.

3. **Step 3 — Edit draft**

    - User can: add manual line items, remove auto-generated line items, edit descriptions, adjust the due date, add notes, add payment terms.
    - Changes via `PUT /api/invoices/{id}` (invoice header) and line item CRUD endpoints.
    - Subtotal/total are recomputed on every change.

4. **Step 4 — Approve**

    - User (admin/owner) clicks "Approve".
    - Backend: `POST /api/invoices/{id}/approve`.
    - Assigns invoice number, sets status to APPROVED, marks referenced time entries as billed (`invoice_id = this invoice`).

5. **Step 5 — Send**

    - User clicks "Mark as Sent".
    - Backend: `POST /api/invoices/{id}/send`.
    - Sets status to SENT. In this phase, "send" is a manual status change — the user shares the HTML preview link or prints it. No email delivery.

6. **Step 6 — Record payment**

    - User clicks "Record Payment".
    - Backend: `POST /api/invoices/{id}/payment` with optional `paymentReference`.
    - Calls `PaymentProvider.recordPayment()` (mocked — returns success with a fake reference).
    - Sets status to PAID, records `paid_at` and `payment_reference`.

### 5. API endpoints

Full endpoint specification:

1. **Invoice CRUD**

    - `GET /api/invoices` — list invoices with filters: `customerId`, `projectId` (any line item references this project), `status`, `from` (issue date), `to` (issue date). Paginated.
    - `GET /api/invoices/{id}` — get invoice with all line items.
    - `POST /api/invoices` — create a new draft invoice. Body: `{ customerId, currency, timeEntryIds[], dueDate?, notes?, paymentTerms? }`.
    - `PUT /api/invoices/{id}` — update a draft invoice header (due date, notes, payment terms, tax amount). Only valid for DRAFT status.
    - `DELETE /api/invoices/{id}` — delete a draft invoice. Only valid for DRAFT status. Hard delete.

2. **Invoice lifecycle transitions**

    - `POST /api/invoices/{id}/approve` — transition DRAFT → APPROVED. Assigns invoice number, marks time entries as billed.
    - `POST /api/invoices/{id}/send` — transition APPROVED → SENT.
    - `POST /api/invoices/{id}/payment` — transition SENT → PAID. Body: `{ paymentReference? }`.
    - `POST /api/invoices/{id}/void` — transition APPROVED|SENT → VOID. Reverts time entries to unbilled.

3. **Invoice line items**

    - `POST /api/invoices/{id}/lines` — add a manual line item to a draft. Body: `{ projectId?, description, quantity, unitPrice, sortOrder? }`.
    - `PUT /api/invoices/{id}/lines/{lineId}` — update a line item on a draft. Editable: description, quantity, unitPrice, sortOrder.
    - `DELETE /api/invoices/{id}/lines/{lineId}` — remove a line item from a draft.

4. **Unbilled time**

    - `GET /api/customers/{customerId}/unbilled-time` — unbilled billable time entries across customer's projects, grouped by project and currency.

5. **Invoice preview**

    - `GET /api/invoices/{id}/preview` — returns the HTML invoice preview. Self-contained HTML page (inline styles, no external dependencies). Can be opened in a new tab or used in an iframe.

    For each endpoint specify:
    - Auth requirement (valid Clerk JWT, appropriate role).
    - Tenant scoping.
    - Permission checks (see permissions model above).
    - Request/response DTOs.

### 6. Mocked PSP integration

Design the payment service provider abstraction:

1. **Interface**

    ```java
    public interface PaymentProvider {
        PaymentResult recordPayment(PaymentRequest request);
    }
    ```

    - `PaymentRequest`: invoiceId, amount, currency, description.
    - `PaymentResult`: success (boolean), paymentReference (String), errorMessage (nullable).

2. **MockPaymentProvider implementation**

    - Always returns success.
    - Generates a fake payment reference: `MOCK-PAY-{UUID-short}`.
    - Logs the payment for debugging.
    - Configured as the default `@Bean` with a `@ConditionalOnProperty` (e.g., `payment.provider=mock`).

3. **Future integration seam**

    - A real Stripe implementation would: create a Stripe PaymentIntent, return the Stripe reference, and handle webhook callbacks for async payment confirmation.
    - The interface is deliberately simple — the mock proves the seam works without any PSP complexity.

### 7. HTML invoice template

Design the invoice preview:

1. **Template requirements**

    - Self-contained HTML page with inline CSS (no external stylesheets or scripts).
    - Print-friendly: uses `@media print` rules, fits on A4/Letter paper.
    - Clean, professional layout:
        - Header: org name, invoice number, issue date, due date, status badge.
        - "Bill to" section: customer name, email, address.
        - Line items table grouped by project:
            - Project name as section header.
            - Columns: Description, Quantity (hrs), Rate, Amount.
            - Per-project subtotal.
        - "Other items" section for manual lines without a project.
        - Totals section: Subtotal, Tax, Total.
        - Footer: payment terms, notes.
    - Rendered server-side (Spring Boot Thymeleaf or simple string template — no React SSR needed).

2. **Future PDF path**

    - Because the template is self-contained HTML with inline styles, future PDF generation is simply: pass this HTML to a rendering engine (OpenHTMLtoPDF, Puppeteer, etc.). No template changes needed.

### 8. Frontend — Invoice management

Design the frontend views:

1. **Customer detail → "Invoices" tab** (new tab)

    - List of invoices for this customer. Columns: invoice number (or "Draft"), status badge, issue date, due date, total, currency.
    - "New Invoice" button → opens invoice generation flow.
    - Click on invoice → navigates to invoice detail page.

2. **Invoice detail page** (`/invoices/{id}`)

    - **Draft view**: Editable form. Line items table with add/remove/edit. Header fields (due date, notes, payment terms, tax amount) editable. "Preview" button opens HTML preview in new tab. "Approve" button (admin/owner only).
    - **Approved/Sent view**: Read-only view of the invoice. "Preview" button. "Mark as Sent" button (if approved). "Record Payment" button (admin/owner). "Void" button (admin/owner).
    - **Paid view**: Read-only with payment details (reference, date).
    - **Void view**: Read-only with void indicator.

3. **Invoice generation dialog/flow**

    - Step 1: Select date range and currency. System fetches unbilled time.
    - Step 2: Review unbilled entries grouped by project. Checkbox selection. Running total shown.
    - Step 3: Click "Create Draft" → redirects to invoice detail page in edit mode.

4. **Invoices list page** (`/invoices`) — new sidebar nav item

    - All invoices across all customers. Filterable by status, customer, date range.
    - Summary cards at top: total outstanding (unpaid approved+sent), total overdue (past due date), total paid this month.
    - Table: invoice number, customer name, status badge, issue date, due date, total, currency.

5. **Time entry list enhancements**

    - Add billing status indicator: small badge showing "Billed" (with invoice number link) or "Unbilled" for billable entries.
    - Add filter dropdown: All / Unbilled / Billed / Non-billable.

### 9. Notification integration

- **INVOICE_APPROVED**: Notify the member who created the draft (if different from the approver).
- **INVOICE_SENT**: Notify org admins/owners (confirmation that invoice was sent).
- **INVOICE_PAID**: Notify the invoice creator and org admins/owners.
- **INVOICE_VOIDED**: Notify the invoice creator, the approver, and org admins/owners.
- Use the existing `ApplicationEvent` → `NotificationHandler` pipeline from Phase 6.5.
- New `NotificationType` enum values: `INVOICE_APPROVED`, `INVOICE_SENT`, `INVOICE_PAID`, `INVOICE_VOIDED`.

### 10. Audit integration

Publish audit events for:
- `INVOICE_CREATED` — draft created.
- `INVOICE_UPDATED` — draft header or line items modified.
- `INVOICE_APPROVED` — transitioned to approved, includes invoice number.
- `INVOICE_SENT` — marked as sent.
- `INVOICE_PAID` — payment recorded, includes payment reference.
- `INVOICE_VOIDED` — voided, includes reason if provided.
- `INVOICE_DELETED` — draft deleted.

### 11. ADRs for key decisions

Add ADR-style sections for:

1. **Invoice numbering strategy**:
    - Why sequential per-tenant (not globally unique or random).
    - Why numbers are assigned at approval (not draft creation) to avoid gaps from deleted drafts.
    - How the sequence is implemented (tenant-scoped counter table vs. DB sequence).
    - Future configurability (prefix, format).

2. **Line item granularity — one line per time entry**:
    - Why each time entry becomes its own line item (maximum transparency).
    - Alternative considered: aggregated lines (e.g., "Development — 40 hrs @ $150" per project per member).
    - Trade-off: verbosity vs. audit trail. Individual entries can be verified against time logs.
    - The description field and project grouping keep the invoice readable despite granularity.

3. **Double-billing prevention mechanism**:
    - Why `invoice_id` on TimeEntry (vs. a separate billing_status field or junction table).
    - Why the partial unique index on InvoiceLine.time_entry_id (excludes voided invoices).
    - How voiding an invoice cleanly reverts billing status.
    - Why billed time entries are locked from editing (data integrity).

4. **PSP adapter interface design**:
    - Why a simple synchronous interface (not event-driven or webhook-based) for v1.
    - How the mock implementation validates the seam.
    - What changes for a real Stripe integration (async webhooks, payment intent lifecycle).
    - Why `@ConditionalOnProperty` for provider selection.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Keep the design **generic and industry-agnostic** — invoicing from tracked time is universal to all professional services verticals. Do not introduce legal, accounting, or agency-specific billing concepts.
- All monetary amounts use `BigDecimal` / `DECIMAL` — never floating-point.
- Currency is always explicit (stored alongside every monetary value) — never implicit or derived.
- Build on Phase 8's rate card infrastructure — billing rates are already snapshotted on time entries. Invoice line items consume these snapshots directly.
- Build on Phase 6.5's notification infrastructure — invoice lifecycle events use the existing `ApplicationEvent` → notification handler pipeline.
- Build on Phase 6's audit infrastructure — all invoice mutations publish audit events.
- Build on Phase 4's customer model — invoices belong to customers. Customer details are snapshotted for invoice immutability.
- Invoice preview is rendered **server-side as self-contained HTML** — no client-side rendering or React SSR needed. The template is explicitly designed to be PDF-convertible in a future phase.
- The mocked PSP is a **clean adapter seam** — switching to a real provider should require only a new implementation class and a config change, with no changes to the invoice domain model or frontend.
- Frontend additions are consistent with the existing Shadcn UI design system and component patterns.
- The schema supports forward compatibility for: recurring invoicing (scheduled draft generation), credit notes (linked to an original invoice), partial payments (payment ledger per invoice), and customer portal invoice views.

Return a single markdown document as your answer, ready to be added as `architecture/phase10-invoicing-billing.md` and ADRs.
