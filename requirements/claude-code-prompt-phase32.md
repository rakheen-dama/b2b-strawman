# Phase 32 — Proposal → Engagement Pipeline

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 31, the platform has:

- **Document system**: Tiptap WYSIWYG editor with JSON storage, variable substitution, clause library, PDF rendering via OpenHTMLToPDF. Templates and clauses stored as Tiptap JSON in JSONB columns.
- **Document acceptance**: AcceptanceRequest entity with email delivery, portal-based acceptance flow, certificate generation, expiry processor.
- **Project templates**: ProjectTemplate entity with task/document template snapshots, RecurringSchedule for auto-instantiation.
- **Rate cards**: 3-level hierarchy (org → project → customer), cost rates, rate snapshots on time entries.
- **Retainer agreements**: ACTIVE/PAUSED/TERMINATED lifecycle, period consumption tracking, auto-invoicing on period close.
- **Invoicing**: full lifecycle (DRAFT → APPROVED → SENT → PAID / VOID), line items from unbilled time + expenses, tax handling, payment collection (Stripe/PayFast), PDF generation.
- **Customer lifecycle**: PROSPECT → ONBOARDING → ACTIVE → OFFBOARDED, with checklists and compliance packs.
- **Portal**: customer-facing app with magic link auth, project/invoice/document views, comment posting, document acceptance.
- **Email delivery**: SMTP + SendGrid BYOAK, template rendering, bounce handling, unsubscribe.
- **Entity lifecycles**: Project (ACTIVE → COMPLETED → ARCHIVED), Task (OPEN → IN_PROGRESS → DONE / CANCELLED), delete protection.
- **Expenses**: project-scoped, billable with markup, flows into invoices.

**Gap**: There is no way to send a prospective or existing client a formal proposal — a document that describes the scope of work, fee structure, and terms — and have their acceptance automatically spin up the engagement (project, billing, team, checklist). Today, this workflow is entirely manual: create a document, email it, wait for informal confirmation, then manually set up the project, configure billing, assign team members, and trigger onboarding. This is the #1 friction point for converting prospects into active engagements and the single biggest workflow gap compared to competitors like Practice/Ignition and Clio Grow.

## Objective

Build a **Proposal → Engagement Pipeline** that allows firm members to:

1. **Compose a proposal** using the Tiptap rich editor (scope of work, terms) with structured fee configuration (fixed, hourly, or retainer) and optional milestone billing schedule.
2. **Send the proposal** to a client contact via the portal, with email notification.
3. **Client accepts** in the portal — views the proposal, reviews fees and terms, clicks accept.
4. **Full orchestration on acceptance** — auto-creates project (from template if selected), sets up billing (invoices for fixed-fee, retainer agreement for retainer, rate card config for hourly), assigns team members, triggers onboarding checklist, sends notifications.
5. **Track proposals** on a dedicated proposals page with pipeline stats (open, accepted, declined, conversion rate, average time-to-accept) and per-customer proposal tabs.

This phase transforms DocTeams from "powerful toolkit" into "this runs my practice" — the connective tissue that wires existing capabilities into a single client-facing flow.

## Constraints & Assumptions

- **Proposals build on existing infrastructure**: document templates (Tiptap), AcceptanceRequest workflow, project templates, retainer agreements, rate cards, customer lifecycle, portal, email delivery. The Proposal entity is an **orchestrator**, not a new silo.
- **Three fee models**: fixed fee (with optional milestone schedule), hourly (backed by rate cards), retainer (backed by retainer agreement entity). A proposal has exactly one fee model.
- **Portal acceptance only**: clients accept proposals through the portal (not email-based acceptance links). The existing portal infrastructure handles auth (magic links) and rendering.
- **No view tracking in v1**: we track draft → sent → accepted/declined/expired but do not track when the client opens/views the proposal.
- **No proposal versioning/amendments in v1**: if a proposal needs changes after sending, the firm declines/expires the original and creates a new one.
- **No change orders in v1**: proposals are for new engagements. Modifying an existing project's scope via a proposal is a future feature.
- **Project ↔ Proposal linkage**: the created project references its originating proposal. This is informational (audit trail), not a runtime dependency.
- **Proposal numbering**: sequential per org, formatted as `PROP-{NNNN}` (similar to invoice numbering pattern). Uses a ProposalCounter entity.
- **Milestone billing**: fixed-fee proposals can define 1-N payment milestones, each with a description, percentage of total, and relative due date (e.g., "on acceptance", "+30 days", "+60 days"). On acceptance, creates draft invoices per milestone.

---

## Section 1 — Proposal Entity & Data Model

### Core Entity

New `Proposal` entity (tenant-scoped):

```
Proposal:
  id                  UUID (PK)
  proposalNumber      String (not null, unique per tenant) — "PROP-0001"
  title               String (not null, max 200) — e.g., "Annual Audit Engagement 2026"
  customerId          UUID (not null) — FK to Customer
  portalContactId     UUID (nullable) — FK to PortalContact (the recipient)
  status              ProposalStatus (not null, default DRAFT)
  feeModel            FeeModel enum (not null) — FIXED, HOURLY, RETAINER

  # Fee configuration (varies by feeModel)
  fixedFeeAmount      BigDecimal (nullable) — total fee for FIXED model
  fixedFeeCurrency    String (nullable, 3-char ISO) — defaults to org currency
  hourlyRateNote      String (nullable, max 500) — explanatory text for HOURLY (e.g., "Rates per our standard rate card")
  retainerAmount      BigDecimal (nullable) — monthly retainer amount for RETAINER model
  retainerCurrency    String (nullable, 3-char ISO)
  retainerHoursIncluded BigDecimal (nullable) — hours included per period

  # Document content
  contentJson         JSONB (not null) — Tiptap JSON for the proposal body (scope of work, terms)

  # Orchestration references
  projectTemplateId   UUID (nullable) — FK to ProjectTemplate (used on acceptance to create project)

  # Lifecycle
  sentAt              Instant (nullable) — when the proposal was sent
  expiresAt           Instant (nullable) — auto-expire after this date
  acceptedAt          Instant (nullable) — when the client accepted
  declinedAt          Instant (nullable) — when the client declined
  declineReason       String (nullable, max 500) — optional reason from client

  # Result references
  createdProjectId    UUID (nullable) — FK to Project (set after acceptance orchestration)
  createdRetainerId   UUID (nullable) — FK to RetainerAgreement (set after acceptance for RETAINER model)

  # Metadata
  createdById         UUID (not null) — FK to member who created the proposal
  createdAt           Instant
  updatedAt           Instant
```

### ProposalStatus Enum

```
ProposalStatus:
  DRAFT       — being composed, not yet sent
  SENT        — sent to client, awaiting response
  ACCEPTED    — client accepted
  DECLINED    — client declined
  EXPIRED     — past expiry date without response
```

### ProposalMilestone Entity

For fixed-fee proposals with milestone billing:

```
ProposalMilestone:
  id              UUID (PK)
  proposalId      UUID (not null) — FK to Proposal
  description     String (not null, max 200) — e.g., "On signing", "Mid-project delivery"
  percentage      BigDecimal (not null) — percentage of total fee (must sum to 100)
  relativeDueDays Integer (not null) — days after acceptance (0 = on acceptance, 30 = 30 days later)
  sortOrder       Integer (not null)
  invoiceId       UUID (nullable) — FK to Invoice (set after acceptance creates the invoice)
  createdAt       Instant
  updatedAt       Instant
```

### ProposalTeamMember Entity

Team members to assign to the project on acceptance:

```
ProposalTeamMember:
  id              UUID (PK)
  proposalId      UUID (not null) — FK to Proposal
  memberId        UUID (not null) — FK to Member
  role            String (nullable, max 100) — e.g., "Lead auditor", "Reviewer"
  sortOrder       Integer (not null)
```

### ProposalCounter Entity

Sequential numbering per org (follows InvoiceCounter pattern):

```
ProposalCounter:
  id              UUID (PK)
  nextNumber      Integer (not null, default 1)
```

### Business Rules

- Proposals can only be created for existing customers (any lifecycle status — including PROSPECT, since proposals are how prospects become active).
- Status transitions: DRAFT → SENT (on send), SENT → ACCEPTED (on client accept), SENT → DECLINED (on client decline), SENT → EXPIRED (by expiry processor).
- Only DRAFT proposals can be edited (content, fees, team, milestones).
- SENT proposals are immutable (no edits after sending).
- Milestones are only relevant for FIXED fee model. Percentages must sum to exactly 100.
- If no milestones are defined for a FIXED proposal, a single invoice for the full amount is created on acceptance.
- For HOURLY proposals, no invoices are created on acceptance — billing happens through normal time tracking and invoice generation.
- For RETAINER proposals, a RetainerAgreement is created on acceptance with the specified amount and hours.
- A PortalContact must exist for the customer to send a proposal. If the customer has no portal contacts, prompt the user to create one.
- Expiry is optional. If expiresAt is set, a scheduled processor moves SENT proposals to EXPIRED after the deadline.

---

## Section 2 — Proposal CRUD & Lifecycle API

### API Endpoints

```
# CRUD
POST   /api/proposals                     — create proposal (returns DRAFT)
GET    /api/proposals                     — list proposals (filterable by status, customerId, feeModel, createdById, date range)
GET    /api/proposals/{id}                — get proposal detail (includes milestones, team members)
PUT    /api/proposals/{id}                — update proposal (DRAFT only)
DELETE /api/proposals/{id}                — delete proposal (DRAFT only)

# Lifecycle
POST   /api/proposals/{id}/send           — transition DRAFT → SENT, sends email to portal contact
POST   /api/proposals/{id}/decline        — transition SENT → DECLINED (firm-initiated decline, e.g., withdrawn)

# Milestones (DRAFT only)
PUT    /api/proposals/{id}/milestones     — replace all milestones (bulk update)

# Team members (DRAFT only)
PUT    /api/proposals/{id}/team           — replace all team members (bulk update)

# Pipeline stats
GET    /api/proposals/stats               — aggregate stats: total by status, conversion rate, avg days to accept

# Customer-scoped
GET    /api/customers/{customerId}/proposals — list proposals for a specific customer
```

### Proposal Creation Flow

1. User selects customer, enters title, chooses fee model.
2. Proposal body loaded from a document template (optional — user can start from blank or select a template as a starting point). The template's Tiptap JSON is copied into the proposal's `contentJson`.
3. User configures fees based on model:
   - **FIXED**: enter total amount, optionally define milestones.
   - **HOURLY**: enter rate note (explanatory text). Actual rates come from existing rate card hierarchy.
   - **RETAINER**: enter monthly amount, included hours.
4. User optionally selects a project template (for auto-creation on acceptance).
5. User optionally adds team members (from org members list) with roles.
6. User edits the proposal body in the Tiptap editor. Variables available:
   - `{{client_name}}` — customer name
   - `{{client_contact_name}}` — portal contact name
   - `{{proposal_number}}` — proposal number
   - `{{proposal_date}}` — creation date
   - `{{fee_total}}` — total fee amount (FIXED) or retainer amount (RETAINER)
   - `{{fee_model}}` — "Fixed Fee" / "Hourly" / "Retainer"
   - `{{org_name}}` — organization name
   - `{{expiry_date}}` — proposal expiry date (if set)
7. User sets optional expiry date.
8. User previews the rendered proposal (client-side preview using Tiptap renderer, same as document templates).

### Send Flow

1. User clicks "Send" on a DRAFT proposal.
2. System validates: customer has a portal contact, fees are configured, content is not empty, milestones sum to 100% (if applicable).
3. System selects the portal contact (if customer has multiple, user chooses; if only one, auto-selects; if none, blocks with "Create a portal contact first").
4. Status transitions to SENT, `sentAt` is set.
5. Email sent to portal contact: "You have a new proposal from [org_name]: [proposal_title]. View and respond in your portal."
6. In-app notification to the proposal creator: "Proposal [number] sent to [client_name]."
7. Audit event: `proposal.sent`.

---

## Section 3 — Acceptance Orchestration

This is the core value of the phase — what happens when a client clicks "Accept" in the portal.

### Acceptance Trigger

Portal contact views the proposal in the portal and clicks "Accept." The system executes the following orchestration **in a single transaction** (with appropriate error handling):

### Step 1 — Update Proposal

- Status → ACCEPTED, `acceptedAt` = now.
- Audit event: `proposal.accepted`.

### Step 2 — Create Project

- If `projectTemplateId` is set: instantiate the project template (reuse existing `ProjectTemplateService.instantiate()`). This creates the project with tasks, document structure, etc.
- If no template: create a bare project with the proposal title as the project name.
- Link the project to the customer (`customerId`).
- Set `createdProjectId` on the proposal.

### Step 3 — Assign Team

- For each ProposalTeamMember, add the member to the project (reuse existing `ProjectMemberService.addMember()`).
- The `role` field is informational (stored in proposal), not mapped to project roles.

### Step 4 — Set Up Billing

Based on `feeModel`:

**FIXED (no milestones)**:
- Create a single DRAFT invoice for the customer/project with one line item: description = proposal title, amount = `fixedFeeAmount`, lineType = `FIXED_FEE`.

**FIXED (with milestones)**:
- For each ProposalMilestone, create a DRAFT invoice:
  - Amount = `fixedFeeAmount * percentage / 100`
  - Due date = `acceptedAt + relativeDueDays`
  - Description = milestone description
  - Link the invoice ID back to `ProposalMilestone.invoiceId`

**HOURLY**:
- No invoices created. Billing happens through normal time tracking → unbilled time → invoice generation flow.
- Optionally configure project-level rates if specific rates were discussed (out of scope for v1 — use existing rate card hierarchy).

**RETAINER**:
- Create a RetainerAgreement for the customer/project:
  - `amount` = `retainerAmount`, `currency` = `retainerCurrency`
  - `includedHours` = `retainerHoursIncluded`
  - `status` = ACTIVE
  - `startDate` = today
- Set `createdRetainerId` on the proposal.

### Step 5 — Trigger Customer Lifecycle

- If the customer is in PROSPECT status, transition to ONBOARDING (if applicable) or note that the customer needs onboarding.
- If the customer already has compliance checklists configured, instantiate them for the new project.

### Step 6 — Send Notifications

- Notify the proposal creator: "Your proposal [number] for [client_name] has been accepted! Project [project_name] has been created."
- Notify all assigned team members: "You have been assigned to project [project_name] from accepted proposal [number]."
- Audit event: `proposal.orchestration_completed` with details of what was created.

### Error Handling

- If any step fails, roll back the entire transaction. Proposal stays SENT.
- Log the error and notify the proposal creator: "Proposal acceptance for [client_name] encountered an error. Please set up the engagement manually."
- Common failure points: project template instantiation (template deleted?), member assignment (member deactivated?), retainer creation (validation failure?).

---

## Section 4 — Portal Proposal Experience

### Portal Endpoints (Backend)

```
# Portal-scoped (magic link auth)
GET    /portal/api/proposals                 — list proposals for the authenticated portal contact's customer
GET    /portal/api/proposals/{id}            — get proposal detail (rendered content, fee summary, milestones)
POST   /portal/api/proposals/{id}/accept     — accept the proposal
POST   /portal/api/proposals/{id}/decline    — decline the proposal (with optional reason)
```

### Portal Read-Model Extension

Add proposal data to the portal read-model:

```
PortalProposal:
  id                  UUID
  proposalNumber      String
  title               String
  status              String
  feeModel            String
  feeAmount           BigDecimal (total fee for FIXED, monthly for RETAINER, null for HOURLY)
  feeCurrency         String
  contentHtml         String — rendered HTML from Tiptap JSON (server-side render for portal)
  milestones          List<PortalMilestone> — description + percentage + due date
  sentAt              Instant
  expiresAt           Instant (nullable)
  orgName             String
  orgLogoUrl          String (nullable)
  orgBrandColor       String (nullable)
```

### Portal Proposal Page

- **Proposal list**: shows pending/recent proposals with status, date, amount.
- **Proposal detail**: renders the proposal body (scope of work), fee summary section (total amount, fee model, milestones if applicable), terms, org branding (logo, brand color).
- **Accept button**: prominent CTA. On click, confirmation dialog: "By accepting this proposal, you agree to the scope of work and fee structure described above. A project will be set up for you automatically."
- **Decline button**: secondary action. Opens a text input for optional decline reason.
- **Expired proposals**: shown with "This proposal has expired" banner. No accept/decline buttons.

---

## Section 5 — Firm-Facing Frontend

### Proposals Page (`/proposals`)

Top-level page accessible from sidebar navigation (between "Invoices" and "Reports" or similar placement).

**Pipeline stats header**:
- Cards showing: Total Open (SENT), Accepted this month, Declined this month, Conversion rate (accepted / (accepted + declined) over trailing 30 days), Average days to acceptance.

**Proposal list table**:
- Columns: Number, Title, Customer, Fee Model, Amount, Status, Sent Date, Actions.
- Filterable by: status, customer, fee model, date range, created by.
- Sortable by: sent date, amount, status.
- Status badges with color coding: DRAFT (gray), SENT (blue), ACCEPTED (green), DECLINED (red), EXPIRED (amber).

### Create/Edit Proposal Page (`/proposals/new`, `/proposals/{id}/edit`)

Multi-section form:

**Section 1 — Basics**:
- Title input
- Customer dropdown (searchable, from customer list)
- Portal contact dropdown (scoped to selected customer's portal contacts; "Create new contact" link if none exist)
- Fee model selector (Fixed Fee / Hourly / Retainer) — styled as card selection, not dropdown
- Expiry date (optional date picker)

**Section 2 — Fee Configuration** (dynamic based on fee model):
- **FIXED**: Amount input + currency. "Add milestone schedule" toggle → milestone editor (description, %, due days for each milestone; "Add milestone" button; validation that percentages sum to 100).
- **HOURLY**: Rate note textarea. Info text: "Billing will use your organization's rate card hierarchy."
- **RETAINER**: Monthly amount + currency + included hours per period.

**Section 3 — Proposal Body** (Tiptap Editor):
- Full rich editor (same as template editor from Phase 31).
- "Start from template" button — opens template picker, copies template content into the editor.
- Variable toolbar/menu for inserting `{{client_name}}`, `{{fee_total}}`, etc.
- Client-side preview toggle (renders variables with actual values from the selected customer/fee config).

**Section 4 — Team & Project Setup** (optional):
- Project template dropdown (from existing project templates; "None" = bare project).
- Team members multi-select (from org members list) with role text input per member.

**Actions**:
- "Save Draft" — saves and returns to proposal detail.
- "Preview" — renders the full proposal as the client will see it.
- "Send" — validates and sends (with confirmation dialog).

### Proposal Detail Page (`/proposals/{id}`)

- Header: proposal number, title, status badge, customer name (linked), sent date.
- Fee summary card: fee model, amount, milestones (if applicable).
- Rendered proposal body (Tiptap viewer, read-only).
- Team members list (if any).
- Project template reference (if any).
- **Actions** (context-dependent):
  - DRAFT: "Edit", "Send", "Delete"
  - SENT: "Withdraw" (→ DECLINED with reason "Withdrawn by firm"), "Copy" (create new from this)
  - ACCEPTED: "View Project" (link to created project), "View Invoice(s)" (if fixed-fee), "View Retainer" (if retainer)
  - DECLINED: "Copy" (create new from this), decline reason displayed
  - EXPIRED: "Copy" (create new from this)

### Customer Detail Page — Proposals Tab

New tab on customer detail page showing proposals for that customer. Same columns as the main proposals list but pre-filtered.

### Project Detail Page — Proposal Reference

If a project was created from a proposal, show a small "Created from Proposal PROP-XXXX" link in the project header/metadata section.

---

## Section 6 — Expiry Processor

### Scheduled Job

- Runs on a fixed schedule (e.g., every hour or once daily).
- Finds all proposals with `status = SENT` and `expiresAt < now()`.
- Transitions each to EXPIRED.
- Sends notification to proposal creator: "Your proposal [number] for [client_name] has expired."
- Sends email to portal contact: "The proposal from [org_name] has expired. Contact them if you'd like to discuss further."
- Audit event: `proposal.expired`.

---

## Section 7 — Migration Strategy

### Tenant Schema Migration (next available version)

```sql
-- Proposal counter
CREATE TABLE proposal_counters (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  next_number INTEGER NOT NULL DEFAULT 1
);

-- Proposal entity
CREATE TABLE proposals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  proposal_number VARCHAR(20) NOT NULL,
  title VARCHAR(200) NOT NULL,
  customer_id UUID NOT NULL REFERENCES customers(id),
  portal_contact_id UUID,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  fee_model VARCHAR(20) NOT NULL,

  fixed_fee_amount NUMERIC(12,2),
  fixed_fee_currency VARCHAR(3),
  hourly_rate_note VARCHAR(500),
  retainer_amount NUMERIC(12,2),
  retainer_currency VARCHAR(3),
  retainer_hours_included NUMERIC(6,1),

  content_json JSONB NOT NULL DEFAULT '{}',

  project_template_id UUID,

  sent_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  accepted_at TIMESTAMPTZ,
  declined_at TIMESTAMPTZ,
  decline_reason VARCHAR(500),

  created_project_id UUID,
  created_retainer_id UUID,

  created_by_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT proposals_status_check
    CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
  CONSTRAINT proposals_fee_model_check
    CHECK (fee_model IN ('FIXED', 'HOURLY', 'RETAINER')),
  CONSTRAINT proposals_number_unique UNIQUE (proposal_number)
);

CREATE INDEX idx_proposals_customer_id ON proposals(customer_id);
CREATE INDEX idx_proposals_status ON proposals(status);
CREATE INDEX idx_proposals_created_by ON proposals(created_by_id);
CREATE INDEX idx_proposals_expires_at ON proposals(expires_at) WHERE status = 'SENT';

-- Proposal milestones
CREATE TABLE proposal_milestones (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
  description VARCHAR(200) NOT NULL,
  percentage NUMERIC(5,2) NOT NULL,
  relative_due_days INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  invoice_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_proposal_milestones_proposal ON proposal_milestones(proposal_id);

-- Proposal team members
CREATE TABLE proposal_team_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
  member_id UUID NOT NULL,
  role VARCHAR(100),
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_proposal_team_proposal ON proposal_team_members(proposal_id);

-- InvoiceLine extension: add FIXED_FEE line type support
-- (existing line_type column from Phase 30 supports TIME and EXPENSE; add FIXED_FEE)
-- No DDL change needed if line_type is VARCHAR — just use the new value in code.
-- If line_type has a CHECK constraint, alter it:
-- ALTER TABLE invoice_lines DROP CONSTRAINT IF EXISTS invoice_lines_line_type_check;
-- ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_line_type_check
--   CHECK (line_type IN ('TIME', 'EXPENSE', 'FIXED_FEE'));

-- Portal read-model extension
-- Add proposal tables to portal schema (portal_read schema)
-- (Exact DDL depends on portal read-model patterns from Phase 7/22)
```

---

## Section 8 — Audit, Notifications & Activity

### Audit Events

- `proposal.created` — proposal created (details: customerId, feeModel, title)
- `proposal.updated` — proposal edited (details: changed fields)
- `proposal.sent` — proposal sent to client (details: portalContactId, expiresAt)
- `proposal.accepted` — client accepted (details: createdProjectId, createdRetainerId, milestoneInvoiceIds)
- `proposal.declined` — client declined (details: reason)
- `proposal.expired` — proposal expired
- `proposal.deleted` — draft proposal deleted
- `proposal.orchestration_completed` — full orchestration succeeded (details: all created entity IDs)

### Notifications

- **Proposal sent**: notify creator (confirmation).
- **Proposal accepted**: notify creator + all proposal team members.
- **Proposal declined**: notify creator.
- **Proposal expired**: notify creator.
- **Proposal received** (portal contact email): email with portal link.
- **Proposal expired** (portal contact email): email notification.

### Activity Feed

- Proposal events appear in the customer's activity feed.
- If a project is created from the proposal, the acceptance event also appears in the project's activity feed.

---

## Out of Scope

- **View tracking**: no tracking of when the client opens/views the proposal. Can be added later via portal page view events.
- **Proposal versioning/amendments**: no v2/v3 proposals. Firm withdraws and creates a new proposal if changes are needed.
- **Change orders**: proposals are for new engagements only. Modifying scope of an existing project via a new proposal is a future feature.
- **Proposal comparison**: no side-by-side comparison of multiple proposals for the same customer.
- **AI-generated scope**: no AI assistance in writing the proposal body. The Tiptap editor is manual.
- **Countersigning**: acceptance is one-sided (client accepts). No firm countersign step.
- **Approval workflow**: no internal approval (manager approves before send). Any member with appropriate org role can create and send proposals.
- **Discount codes or promotional pricing**: fees are manually configured per proposal.
- **Multi-currency proposals**: proposal uses a single currency. Cross-currency proposals are not supported.
- **Proposal PDF export**: proposals are viewed in the portal, not downloaded as PDFs. PDF export can be added later using existing Tiptap → PDF pipeline.

## ADR Topics to Address

1. **Proposal storage model** — Proposal as a standalone entity (with embedded Tiptap content) vs. proposal as a wrapper around a GeneratedDocument. Recommend standalone entity because proposals have unique lifecycle and orchestration concerns that don't map to the document generation pipeline.
2. **Orchestration transaction boundary** — single transaction for all acceptance steps vs. saga pattern with compensating actions. Recommend single transaction for v1 (simpler, all entities are in the same schema). If orchestration becomes complex, can refactor to saga later.
3. **Milestone invoice creation strategy** — create all milestone invoices on acceptance (as DRAFT with future due dates) vs. create invoices on-demand as milestones are reached. Recommend create-all-upfront: simpler, gives the firm visibility into the full billing schedule, invoices can be edited before approval.
4. **Portal proposal rendering** — client-side Tiptap rendering vs. server-side HTML rendering stored in portal read-model. Consider trade-offs: client-side is fresher but requires Tiptap bundle in portal; server-side is simpler for the portal but requires re-sync on proposal content changes. Recommend server-side rendered HTML in portal read-model (proposals are immutable after SENT, so no freshness concern).
5. **Proposal numbering** — sequential per org (PROP-0001) with a counter entity. Same pattern as InvoiceCounter. Consider whether the prefix should be configurable per org (out of scope for v1).
6. **Fee model flexibility** — one fee model per proposal vs. mixed (e.g., fixed fee for initial setup + hourly for ongoing work). Recommend single fee model per proposal for v1. Mixed billing can be handled by creating two proposals or manually configuring billing post-acceptance.

## Style & Boundaries

- Proposal follows the same patterns as Invoice for numbering (counter entity), lifecycle (status enum with transitions), and API structure (REST CRUD + lifecycle actions).
- Tiptap content handling reuses the infrastructure from Phase 31 — same JSON format, same variable substitution mechanism, same client-side preview approach.
- Portal integration reuses the read-model sync pattern from Phase 22 (domain events trigger read-model updates).
- Retainer creation on acceptance reuses `RetainerAgreementService` from Phase 17.
- Project instantiation from template reuses `ProjectTemplateService` from Phase 16.
- Invoice creation reuses `InvoiceService` from Phase 10.
- All new features emit audit events and update the activity feed via existing domain event infrastructure.
- The orchestration service should be a dedicated `ProposalOrchestrationService` (not embedded in `ProposalService`) to keep responsibilities clean and testable.
- Frontend follows existing patterns: Shadcn UI components, React Query for data fetching, same form patterns as invoice/retainer creation pages.
