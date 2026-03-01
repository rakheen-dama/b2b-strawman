# Phase 34 — Client Information Requests

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 33, the platform has:

- **Customer portal**: Standalone Next.js app with magic-link auth (PortalContact + PortalJwtService). Clients can view projects, invoices, documents, proposals, accept documents, and post comments. Portal data is synced via an event-driven read-model in a dedicated `portal` schema.
- **Checklist system (Phase 14)**: ChecklistTemplate → ChecklistInstance → ChecklistInstanceItem with document attachment support. Used for **internal** compliance tracking (firm verifies FICA docs). Templates can auto-instantiate on customer lifecycle transitions.
- **Document & S3 integration**: Document entity (multi-scope: PROJECT, ORG, CUSTOMER) with presigned upload URLs, S3 storage, and PENDING → UPLOADED status machine. Visibility control (INTERNAL / SHARED).
- **Email delivery (Phase 24)**: SMTP + SendGrid BYOAK, Thymeleaf templates, delivery logging, unsubscribe support, rate limiting.
- **Scheduled jobs**: TimeReminderScheduler pattern — iterates all tenant schemas, processes per-tenant with ScopedValue isolation, error-isolated.
- **Notification system (Phase 6.5)**: In-app + email channels, per-member preferences, type-driven routing.
- **Project templates (Phase 16)**: ProjectTemplate with task/document template snapshots. Used by proposals and recurring schedules for project instantiation.
- **Data completeness (Phase 33)**: Prerequisite enforcement system with field validation at lifecycle transitions and action points. PrerequisiteModal for inline remediation.
- **Proposal pipeline (Phase 32)**: Proposal acceptance auto-creates projects from templates, sets up billing, assigns team, triggers onboarding checklists.
- **Audit events (Phase 6)**: AuditEvent entity with JSONB details, covers all domain actions.
- **Activity feed (Phase 6.5)**: Activity tab on project detail pages, powered by audit events + comments.

**Gap**: The #1 daily friction for accounting and professional services firms is collecting documents and information from clients. Today, this happens entirely via email — documents get lost in inboxes, reminders are manual, tracking is nonexistent, and there's no central view of what's outstanding across all clients. The portal exists but clients have no reason to log in proactively. An information request system gives clients a clear checklist of what to provide, lets them upload directly, and gives the firm real-time visibility into completion status.

## Objective

Build a **Client Information Requests** system that allows firm members to:

1. **Create request templates** — reusable checklists of items to collect ("Annual Audit Document Pack", "Tax Return Supporting Docs") with per-item response types (file upload or text response).
2. **Send requests to clients** — create an information request from a template (or ad-hoc), link it to a customer and optionally a project, send to a portal contact.
3. **Client responds via portal** — client sees a checklist of items, uploads files or provides text responses per item. Clear progress tracking.
4. **Firm reviews submissions** — each submitted item is reviewed: accepted (done) or rejected with a reason (client re-submits). Request is complete when all items are accepted.
5. **Automated reminders** — system sends recurring reminders every N days while items are outstanding. Configurable interval per org.
6. **Auto-trigger from project templates** — project template can reference a request template. On project creation (manual or via proposal acceptance), a draft information request is auto-created for the firm to review and send.
7. **Track across all clients** — firm dashboard showing outstanding requests, overdue items, completion rates across all clients.

This phase transforms the portal from "view-only" to "interactive" — giving clients a reason to log in and giving firms real-time visibility into document collection status.

## Constraints & Assumptions

- **Dedicated entities**, not an extension of the checklist system. Checklists are internal (firm verifies). Information requests are external (client provides). Different audience, lifecycle, and portal experience.
- **Two response types**: `FILE_UPLOAD` and `TEXT_RESPONSE`. Each item in a request template specifies its type. No confirmation/checkbox type — document acceptance handles that use case.
- **Review cycle**: Client submits → item status SUBMITTED. Firm accepts → ACCEPTED. Firm rejects (with reason) → REJECTED. Client re-submits → SUBMITTED again. Request complete when ALL required items are ACCEPTED.
- **Scoping**: A request can be **customer-scoped** (general onboarding docs, no project link) or **project-scoped** (engagement-specific docs, linked to a project). Uploaded files auto-attach to the correct customer and project (if linked).
- **Reminders are interval-based**: No due date required. System sends a reminder every N days while items are outstanding. Default interval configurable in OrgSettings (e.g., every 5 days). Individual requests can override the org default.
- **Project template integration**: ProjectTemplate gains an optional `requestTemplateId` reference. On project instantiation, if a request template is referenced, a DRAFT information request is created (not auto-sent). Firm reviews and sends manually.
- **Portal read-model sync**: New `PortalRequest` and `PortalRequestItem` tables in the portal schema, synced via domain events following the established pattern.
- **File uploads use existing Document infrastructure**: Client uploads create Document entities (scope: CUSTOMER or PROJECT, visibility: SHARED) via presigned S3 URLs. The RequestItem references the uploaded Document.
- **Single portal contact per request**: A request is sent to one PortalContact. If the firm needs docs from multiple contacts, they create multiple requests.
- **No bulk request creation in v1**: Requests are created one at a time (per customer). Bulk operations are a future phase (Phase 36).

---

## Section 1 — Request Template Entity & Data Model

### RequestTemplate

Reusable template defining what items to collect. Org-scoped (each org creates their own, plus platform-seeded packs).

```
RequestTemplate:
  id                  UUID (PK)
  name                String (not null, max 200) — e.g., "Annual Audit Document Pack"
  description         String (nullable, max 1000)
  source              TemplateSource (PLATFORM, CUSTOM) — platform = seeded, custom = org-created
  packId              String (nullable) — for platform-seeded templates
  active              Boolean (not null, default true)
  createdAt           Timestamp
  updatedAt           Timestamp
```

### RequestTemplateItem

Individual items within a template.

```
RequestTemplateItem:
  id                  UUID (PK)
  templateId          UUID (FK to RequestTemplate)
  name                String (not null, max 200) — e.g., "Bank Statements (Jan–Dec)"
  description         String (nullable, max 1000) — guidance for the client
  responseType        ResponseType (FILE_UPLOAD, TEXT_RESPONSE)
  required            Boolean (not null, default true)
  fileTypeHints       String (nullable) — e.g., "PDF, Excel" — informational only, not enforced
  sortOrder           Integer (not null)
  createdAt           Timestamp
```

### Platform-Seeded Templates

Ship with common request packs:

| Pack Name | Items (examples) |
|-----------|-----------------|
| Annual Audit Document Pack | Trial balance, bank statements, fixed asset register, debtors/creditors age analysis, prior year signed AFS |
| Tax Return Supporting Docs | IRP5 certificates, medical aid tax certificate, retirement annuity certificate, investment income statements, logbook summary |
| Company Registration | Company registration certificate (CIPC), shareholder register, director appointments, B-BBEE certificate |
| Monthly Bookkeeping | Bank statements, petty cash slips, supplier invoices, payroll summaries |

Seeded via a `RequestPackSeeder` (same pattern as CompliancePackSeeder, ClausePackSeeder, FieldPackSeeder).

---

## Section 2 — InformationRequest Entity & Lifecycle

### InformationRequest

An instance of a request sent to a specific client.

```
InformationRequest:
  id                  UUID (PK)
  requestNumber       String (not null, unique per tenant) — "REQ-0001"
  requestTemplateId   UUID (nullable, FK to RequestTemplate) — null for ad-hoc requests
  customerId          UUID (not null, FK to Customer)
  projectId           UUID (nullable, FK to Project) — null for customer-scoped requests
  portalContactId     UUID (not null, FK to PortalContact) — the recipient
  status              RequestStatus (DRAFT, SENT, IN_PROGRESS, COMPLETED, CANCELLED)
  reminderIntervalDays Integer (nullable) — override org default; null = use org default
  lastReminderSentAt  Timestamp (nullable) — for interval tracking
  sentAt              Timestamp (nullable)
  completedAt         Timestamp (nullable)
  cancelledAt         Timestamp (nullable)
  createdBy           UUID (not null) — member who created
  createdAt           Timestamp
  updatedAt           Timestamp
```

### RequestItem

Individual items within a request instance.

```
RequestItem:
  id                  UUID (PK)
  requestId           UUID (FK to InformationRequest)
  templateItemId      UUID (nullable, FK to RequestTemplateItem) — null for ad-hoc items
  name                String (not null)
  description         String (nullable)
  responseType        ResponseType (FILE_UPLOAD, TEXT_RESPONSE)
  required            Boolean (not null, default true)
  fileTypeHints       String (nullable)
  sortOrder           Integer (not null)
  status              ItemStatus (PENDING, SUBMITTED, ACCEPTED, REJECTED)
  documentId          UUID (nullable, FK to Document) — for FILE_UPLOAD responses
  textResponse        String (nullable) — for TEXT_RESPONSE responses
  rejectionReason     String (nullable) — filled when firm rejects
  submittedAt         Timestamp (nullable)
  reviewedAt          Timestamp (nullable)
  reviewedBy          UUID (nullable) — member who accepted/rejected
  createdAt           Timestamp
  updatedAt           Timestamp
```

### Status Lifecycle

**Request-level:**
```
DRAFT → SENT → IN_PROGRESS → COMPLETED
                            → CANCELLED (from any state except COMPLETED)
```
- `DRAFT`: Created but not sent to client. Firm can edit items.
- `SENT`: Delivered to client (email sent, visible in portal). No client activity yet.
- `IN_PROGRESS`: At least one item has been submitted by the client.
- `COMPLETED`: All required items are ACCEPTED. (Optional items can be PENDING.)
- `CANCELLED`: Request withdrawn by firm.

Auto-transition: SENT → IN_PROGRESS on first client submission. IN_PROGRESS → COMPLETED when last required item is accepted.

**Item-level:**
```
PENDING → SUBMITTED → ACCEPTED
                    → REJECTED → PENDING (client re-submits)
```
- `PENDING`: Awaiting client response.
- `SUBMITTED`: Client has uploaded file or provided text. Awaiting firm review.
- `ACCEPTED`: Firm has approved the submission.
- `REJECTED`: Firm has rejected with reason. Item returns to PENDING state for client to re-submit.

### Numbering

`RequestNumberService` (same pattern as `InvoiceNumberService`, `ProposalNumberService`). Sequential per tenant: REQ-0001, REQ-0002, etc. Uses a `RequestCounter` entity.

---

## Section 3 — Backend API

### Request Template API

```
GET    /api/request-templates                    — List all templates (active filter)
POST   /api/request-templates                    — Create custom template
GET    /api/request-templates/{id}               — Get template with items
PUT    /api/request-templates/{id}               — Update template
DELETE /api/request-templates/{id}               — Delete template (soft: set active=false)
POST   /api/request-templates/{id}/items         — Add item to template
PUT    /api/request-templates/{id}/items/{itemId} — Update item
DELETE /api/request-templates/{id}/items/{itemId} — Remove item
POST   /api/request-templates/{id}/duplicate     — Clone template
```

### Information Request API

```
GET    /api/information-requests                 — List requests (filters: customerId, projectId, status)
POST   /api/information-requests                 — Create request (from template or ad-hoc)
GET    /api/information-requests/{id}            — Get request with items
PUT    /api/information-requests/{id}            — Update request (DRAFT only: edit items, change contact)
POST   /api/information-requests/{id}/send       — Send to client (DRAFT → SENT)
POST   /api/information-requests/{id}/cancel     — Cancel request
POST   /api/information-requests/{id}/items      — Add ad-hoc item (DRAFT only)

— Item review (firm-side):
POST   /api/information-requests/{id}/items/{itemId}/accept   — Accept submission
POST   /api/information-requests/{id}/items/{itemId}/reject   — Reject with reason
```

### Customer-Scoped Convenience Endpoints

```
GET    /api/customers/{customerId}/information-requests       — Requests for this customer
GET    /api/projects/{projectId}/information-requests         — Requests for this project
```

### Dashboard / Aggregation

```
GET    /api/information-requests/summary         — Aggregate stats: total, by status, items pending review
```

---

## Section 4 — Portal API & Client Experience

### Portal Backend Endpoints

```
GET    /portal/api/requests                      — List requests for this portal contact
GET    /portal/api/requests/{id}                 — Get request detail with items
POST   /portal/api/requests/{id}/items/{itemId}/upload    — Upload file for item (returns presigned URL)
POST   /portal/api/requests/{id}/items/{itemId}/submit    — Submit text response or confirm upload
```

### Portal Read-Model

New tables in portal schema:

```
portal_requests:
  id                  UUID (PK)
  request_number      String
  customer_id         UUID
  portal_contact_id   UUID
  project_id          UUID (nullable)
  project_name        String (nullable)
  status              String
  total_items         Integer
  submitted_items     Integer
  accepted_items      Integer
  rejected_items      Integer
  sent_at             Timestamp
  completed_at        Timestamp (nullable)
  synced_at           Timestamp

portal_request_items:
  id                  UUID (PK)
  request_id          UUID (FK)
  name                String
  description         String (nullable)
  response_type       String
  required            Boolean
  file_type_hints     String (nullable)
  sort_order          Integer
  status              String
  rejection_reason    String (nullable)
  document_id         UUID (nullable)
  text_response       String (nullable)
  synced_at           Timestamp
```

Sync via domain events following the established `PortalEventHandler` → `PortalReadModelRepository` pattern.

### Portal Frontend Pages

**Request List Page** (`/portal/(authenticated)/requests`):
- Lists all requests for the authenticated portal contact
- Shows: request number, project name (if linked), status badge, progress bar (X/Y items), sent date
- Sort by most recent, filter by status (open/completed)
- Prominent in portal nav — this is the primary action surface

**Request Detail Page** (`/portal/(authenticated)/requests/[id]`):
- Header: request number, linked project, status, progress summary
- Item list: each item shows name, description, response type, status badge
- For FILE_UPLOAD items: dropzone/upload button, shows uploaded file name if submitted
- For TEXT_RESPONSE items: text area input
- For REJECTED items: rejection reason displayed prominently, "Re-submit" action
- For ACCEPTED items: green checkmark, file name or text response shown (read-only)
- Submit button per item (not batch — clients may provide items over multiple sessions)

### Portal Upload Flow

1. Client clicks upload on a FILE_UPLOAD item
2. Frontend calls `POST /portal/api/requests/{id}/items/{itemId}/upload` with file metadata
3. Backend creates Document entity (scope: CUSTOMER or PROJECT, visibility: SHARED), returns presigned S3 URL
4. Frontend uploads file to S3
5. Frontend calls `POST /portal/api/requests/{id}/items/{itemId}/submit` to confirm
6. Backend updates RequestItem: status → SUBMITTED, documentId → document.id, submittedAt → now
7. Domain event → portal read-model sync + notification to firm

---

## Section 5 — Automated Reminders

### Reminder Scheduler

New `RequestReminderScheduler` following the `TimeReminderScheduler` pattern:

- **Frequency**: Runs every 6 hours (fixed rate)
- **Processing**: Iterates all tenant schemas via `OrgSchemaMappingRepository`
- **Per-tenant logic**:
  1. Find all requests with status SENT or IN_PROGRESS
  2. For each request: check if `daysSince(lastReminderSentAt || sentAt) >= reminderIntervalDays`
  3. If due: send reminder email to portal contact, update `lastReminderSentAt`
  4. Skip if reminder interval is 0 (reminders disabled for this request)

### Reminder Email

- Template: "You have X outstanding items for [Request Name]"
- Lists pending/rejected items by name
- "View in Portal" button linking to the request detail page
- Uses existing email delivery infrastructure (EmailNotificationChannel)

### OrgSettings Extension

```
OrgSettings extension:
  defaultRequestReminderDays   Integer (default 5) — org-wide default interval
```

Admin configures in Settings → Organization. Individual requests can override.

---

## Section 6 — Project Template Integration

### ProjectTemplate Extension

```
ProjectTemplate extension:
  requestTemplateId    UUID (nullable, FK to RequestTemplate)
```

When a project template references a request template:

1. **Manual project creation** (with template + linked customer): After project is created, system auto-creates an InformationRequest in DRAFT status from the referenced request template. Firm sees "A draft information request has been created" notification.
2. **Proposal acceptance → project instantiation**: Same behavior — draft request created, firm notified.
3. **Recurring schedule → project creation**: Draft request created, firm notified.

The request is always DRAFT — firm reviews, optionally customizes items, selects the portal contact, then sends.

### Frontend: Project Template Editor

- New optional field: "Information Request Template" — dropdown of active RequestTemplates
- Help text: "When a project is created from this template, a draft information request will be created for the linked customer."

---

## Section 7 — Firm-Side Frontend

### Request Templates Page (Settings → Request Templates)

- List all request templates (platform + custom)
- Create / edit / duplicate / deactivate templates
- Template editor: name, description, sortable item list
- Per-item editor: name, description, response type (dropdown), required toggle, file type hints
- Platform templates shown as read-only with "Duplicate to customize" action

### Information Requests Tab (Customer Detail Page)

- New tab on customer detail page: "Requests"
- Lists all information requests for this customer
- Shows: request number, linked project (if any), status, progress bar, sent date
- "New Request" button → create dialog

### Information Requests Tab (Project Detail Page)

- New tab on project detail page: "Requests" (if project has linked customer)
- Shows requests linked to this specific project
- Same UI pattern as customer tab

### Create Request Dialog

- Select template (or "Ad-hoc — no template")
- If template selected: items pre-populated, can add/remove/edit before sending
- Select customer (pre-filled if opened from customer page)
- Select project (optional, pre-filled if opened from project page)
- Select portal contact (dropdown of customer's portal contacts)
- Reminder interval (pre-filled from org default, editable)
- "Save as Draft" or "Send Now" actions

### Request Detail Page (Firm-Side)

- Header: request number, customer name, project name, portal contact, status badge
- Progress bar: X/Y items accepted
- Item list with review actions:
  - PENDING items: waiting for client (grey)
  - SUBMITTED items: needs review (amber) — "Accept" / "Reject" buttons
  - ACCEPTED items: done (green) — linked document viewable/downloadable
  - REJECTED items: sent back (red) — rejection reason shown
- Reject action opens a modal for the rejection reason
- Activity log at bottom (via audit events)
- "Cancel Request" action in dropdown menu
- "Resend Notification" action — re-sends the request email to the portal contact

### Dashboard Integration

- Company dashboard widget: "Information Requests"
  - Requests awaiting review (items in SUBMITTED status)
  - Overdue requests (no client activity in > 2× reminder interval)
  - Completion rate (last 30 days)
- Click-through to filtered request list

---

## Section 8 — Notifications, Audit & Activity

### Notification Types (New)

| Event | Recipient | Channel |
|-------|-----------|---------|
| Request sent | Portal contact (client) | Email |
| Item submitted by client | Request creator (firm member) | In-app + email |
| Item accepted by firm | Portal contact (client) | Email |
| Item rejected by firm | Portal contact (client) | Email (includes rejection reason) |
| Request completed (all items accepted) | Request creator + portal contact | In-app + email |
| Reminder (items outstanding) | Portal contact (client) | Email |
| Draft request auto-created (from project template) | Request creator / project members | In-app |

### Audit Events

- REQUEST_CREATED, REQUEST_SENT, REQUEST_CANCELLED, REQUEST_COMPLETED
- REQUEST_ITEM_SUBMITTED, REQUEST_ITEM_ACCEPTED, REQUEST_ITEM_REJECTED
- REQUEST_REMINDER_SENT

All include standard audit fields (actor, timestamp, entity references, JSONB details).

### Activity Feed Integration

- Request events appear in the project activity tab (if project-scoped)
- "Information request REQ-0042 sent to client" / "Client submitted 3 items" / "Request completed"

---

## Section 9 — File Management & Document Integration

### Upload Behavior

- Files uploaded via information requests create standard Document entities
- Document scope: PROJECT (if request is project-scoped) or CUSTOMER (if customer-only)
- Document visibility: SHARED (visible in portal, since the client uploaded it)
- Documents appear in the project's Documents tab and the portal's Documents page automatically

### Text Response Storage

- Text responses stored directly on the RequestItem entity (`textResponse` field)
- No Document entity needed for text responses
- Text responses are visible in the request detail page (firm and portal)
- Text responses can be copied to custom fields manually (future: auto-map to custom fields)

---

## Out of Scope

- **Bulk request creation** — sending the same request to multiple customers at once. Future phase (Bulk Operations).
- **Auto-mapping text responses to custom fields** — text response in a request item → auto-populate a customer custom field. Useful but adds complexity. Future enhancement.
- **Due dates on requests** — keeping it interval-based only. Due dates can be added later if firms want deadline-driven workflows.
- **Request versioning** — no "v2" of a request. If requirements change, cancel and create new.
- **Multi-contact requests** — one request, one portal contact. Multiple contacts = multiple requests.
- **File type enforcement** — file type hints are informational only. No server-side MIME type validation beyond what S3 already does.
- **Request approval workflow** (internal) — no "manager approves before sending" flow. Any member with access can send.
- **Batch item review** — accept/reject is per-item, not batch. Keeps the review thoughtful.

## ADR Topics

1. **Dedicated entity vs. checklist extension**: Why InformationRequest is separate from ChecklistInstance — different audience (external vs. internal), different lifecycle (review cycle vs. simple completion), different portal visibility requirements.
2. **Reminder strategy**: Interval-based (every N days) vs. deadline-based (X days before due) vs. hybrid. Decision: interval-based for simplicity and universal applicability.
3. **Portal upload flow**: Direct S3 upload (presigned URL from portal backend) vs. proxy through portal backend. Decision: presigned URL for performance consistency with existing upload patterns.
4. **Project template integration scope**: Auto-send vs. draft-on-creation. Decision: draft-on-creation to preserve firm control over per-engagement customization.

## Style & Boundaries

- Follow existing entity patterns: Spring Boot entity + JpaRepository + Service + Controller
- Request numbering follows InvoiceNumberService / ProposalNumberService pattern (RequestCounter entity)
- Portal read-model sync follows PortalEventHandler → PortalReadModelRepository pattern (domain events, upsert SQL)
- Portal frontend follows existing portal app patterns (Next.js, portalApi client, magic link auth)
- Firm-side frontend follows existing patterns (server actions, Shadcn UI, tabs on detail pages)
- Reminder scheduler follows TimeReminderScheduler pattern (per-tenant iteration, ScopedValue binding, error isolation)
- Email templates follow existing Thymeleaf template pattern (HTML + plain text)
- Platform-seeded templates follow existing pack seeder pattern (JSON definition files, idempotent seeding)
- Migration: adds request_templates, request_template_items, information_requests, request_items, request_counter tables. Extends project_templates with request_template_id. Extends org_settings with default_request_reminder_days.
- Test coverage: integration tests for lifecycle transitions, review cycle, reminder scheduling, portal submission flow, project template auto-draft
