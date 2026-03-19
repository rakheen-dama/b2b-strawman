# Phase 50 — Data Protection Compliance (POPIA / Jurisdiction-Aware)

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation, af-south-1 hosting) with 49 phases of functionality: projects, customers, tasks, time tracking, invoicing (with tax, payments, bulk billing), rate cards, budgets, profitability dashboards, document templates (Tiptap + Word + PDF), proposals, document acceptance, retainer agreements, recurring tasks/schedules, expenses, custom fields, tags, saved views, workflow automations, resource planning, comments, notifications, activity feeds, audit trails, client information requests, customer portal (with magic links and payment collection), reporting/export, email delivery, command palette, RBAC with custom roles, Keycloak auth with Gateway BFF, admin-approved org provisioning, in-app AI assistant (BYOAK), and a vertical architecture with module guards, profile system, and tenant-gated modules (Phase 49).

**The existing infrastructure that this phase builds on**:
- **Audit trail** (Phase 6): `AuditEvent` entity captures all domain events with JSONB details. Security events, domain events, and query API already exist. This satisfies POPIA's accountability principle for operations already audited.
- **Document template engine** (Phase 12): `DocumentTemplate` entity with Tiptap rendering, context builders (Project/Customer/Invoice), PDF generation via OpenHTMLToPDF, S3 upload. Supports template packs and clone-and-edit. PAIA manual generation will use this directly.
- **OrgSettings** (Phase 8, extended Phase 49): Per-tenant settings including `vertical_profile`, `enabled_modules`, branding (logo, brand_color, footer), default currency, tax rates. New columns will be added here for data protection jurisdiction and retention configuration.
- **Module guard** (Phase 49): `VerticalModuleGuard` gates access to vertical-specific endpoints per tenant. Data protection features are universal (not vertical-gated) but the guard pattern informs how jurisdiction-specific behavior is toggled.
- **Notification system** (Phase 6.5): `Notification` entity with in-app and email channels, preferences, templates. Used to alert staff about retention purge warnings and DSAR processing deadlines.
- **Customer & PortalContact entities** (Phases 4, 7): The primary data subjects. Customer has projects, documents, time entries, invoices, comments, custom field values. PortalContact has magic links and portal access. These are the targets for data export and anonymization.
- **Reporting/export** (Phase 19): Existing export infrastructure (CSV/PDF) provides patterns for data packaging. Data export extends this to produce comprehensive PI bundles.

**The problem**: The platform processes personal information (PI) on behalf of tenants (firms) who are responsible parties under POPIA and equivalent data protection laws. Neither the platform nor the tenants currently have tooling to:
1. Produce a comprehensive data export when a data subject (client/contact) exercises their right of access
2. Anonymize a data subject's personal information while preserving financial records required by tax law
3. Enforce data retention limits — personal information is kept indefinitely by default
4. Generate the mandatory PAIA Section 51 manual that every SA private body must have
5. Document what personal information is processed, why, and for how long (processing activity register)

**The fix**: Build tenant-facing data protection tools that also satisfy the platform's own compliance obligations. The same data export that helps a firm respond to a client's access request is the same capability DocTeams uses to respond to its own DSARs. One layer of code serves both purposes. Design for jurisdiction awareness from day one — POPIA is the first locale, but the entities and UI use generic terminology ("data protection jurisdiction" not "POPIA mode") so GDPR/LGPD/etc. can be added later as configuration.

## Objective

1. **Data Subject Export** — a "Download all data" action on the customer detail page that bundles all personal information related to that customer (profile, projects, documents, time entries, invoices, comments, custom field values, portal contact details) into a downloadable ZIP. Staff-initiated only (no portal self-serve in v1).
2. **Data Subject Anonymization** — a "Delete personal data" action that scrubs PI from a customer record and all associated entities while preserving financial records (invoices, time entries with monetary values) in anonymized form with a reference ID. Required by SA tax law (Income Tax Act: 5 years, VAT Act: 5 years).
3. **Retention Policies** — per-entity-type retention settings in Org Settings with a scheduled purge job. Configurable retention periods for customers, time entries, audit logs, documents, and comments. Warnings before auto-purge via notifications. Jurisdiction-aware defaults (SA: 5 years for financial records).
4. **PAIA Manual Generation** — a document template and context builder that generates a pre-filled Section 51 manual per tenant, leveraging the existing Phase 12 template engine. The template is jurisdiction-specific (SA first), included in tenant pack seeding.
5. **Processing Activity Register** — a settings page where tenants document what PI they collect, why, from whom, and how long they retain it. Pre-populated with sensible defaults based on the platform's known data model. Exportable for regulatory submission.
6. **DSAR Tracking** — a lightweight request lifecycle entity for tracking data subject access requests from receipt through completion. Staff-only intake (manual logging). Status tracking, deadline awareness, and audit trail integration.

## Constraints & Assumptions

- **Staff-only DSAR intake.** Data subjects contact the firm by phone/email. Firm staff log the request in DocTeams. No portal self-serve DSAR submission in v1. This avoids portal UI scope creep and matches how most small-to-medium firms actually handle these requests.
- **af-south-1 hosting eliminates cross-border concerns.** All data stays in South Africa. No need for cross-border transfer documentation or adequacy assessments in v1. If a tenant operates in the EU, they'd need additional configuration — that's future work.
- **Financial record preservation is non-negotiable.** SA Income Tax Act (Section 29) and VAT Act (Section 55) require financial records to be kept for 5 years from the end of the relevant tax year. Anonymization must preserve invoices and financially-relevant time entries in a form that satisfies audit requirements. The anonymized reference ID replaces the customer name but the financial data (amounts, dates, line items) is retained.
- **Jurisdiction-aware but POPIA-first.** All entities use generic field names (`data_protection_jurisdiction`, not `popia_mode`). POPIA-specific content (PAIA manual template, legal basis labels, retention defaults) is delivered as seed data / jurisdiction packs. Adding GDPR support later means adding a new jurisdiction pack, not changing code.
- **No consent engine.** Most B2B professional services processing is under "contractual necessity" or "legitimate interest" — not consent. Consent is one of six POPIA legal bases (Section 11) and usually the wrong one for the engagements this platform manages. If specific consent capture is needed (e.g., marketing), it can be added incrementally — it doesn't block the core compliance tooling.
- **No automated breach notification.** Breach notification to the Information Regulator is an organizational process, not a software workflow. A simple breach incident log (date, description, affected subjects, notification status) is sufficient for recordkeeping.
- **Leverage existing infrastructure aggressively.** PAIA manual uses Phase 12 templates. Notifications use Phase 6.5 infrastructure. Audit trail integration uses Phase 6 events. Export patterns follow Phase 19 conventions. New entities should be minimal.

---

## Section 1 — Data Protection Foundation

### 1.1 OrgSettings Extensions

Add data protection configuration to the existing `OrgSettings` entity:

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `data_protection_jurisdiction` | `VARCHAR(10)` | `null` | ISO 3166-1 alpha-2 code: "ZA" (POPIA), "EU" (GDPR), "BR" (LGPD), etc. Null means not configured. |
| `retention_policy_enabled` | `BOOLEAN` | `false` | Whether automated retention enforcement is active for this tenant. |
| `default_retention_months` | `INTEGER` | `null` | Default retention period in months. Overridden per entity type via `RetentionPolicy`. |
| `financial_retention_months` | `INTEGER` | `60` | Retention for financial records (invoices, billable time entries). Defaults to 60 (5 years) for SA tax compliance. Cannot be set below the jurisdiction minimum. |
| `information_officer_name` | `VARCHAR(255)` | `null` | Name of the designated Information Officer (POPIA Section 55). |
| `information_officer_email` | `VARCHAR(255)` | `null` | Contact email for data protection requests. |

### 1.2 Jurisdiction Pack (Seed Data)

Jurisdiction packs are static configuration bundles applied when `data_protection_jurisdiction` is set. They are NOT database entities — they're functions in the seeder, same pattern as vertical profiles (Phase 49).

```
JURISDICTION_ZA (South Africa — POPIA):
  financial_retention_months_minimum: 60   (SA Income Tax Act + VAT Act)
  legal_bases: ["consent", "contractual_necessity", "legal_obligation",
                "legitimate_interest", "public_interest", "vital_interest",
                "law_enforcement", "historical_research"]
  mandatory_document: "paia_section_51_manual"
  regulator_name: "Information Regulator (South Africa)"
  regulator_url: "https://inforegulator.org.za"
  breach_notification_deadline: "as soon as reasonably possible"
  dsar_response_deadline_days: 30

JURISDICTION_EU (GDPR — future):
  financial_retention_months_minimum: varies by member state
  legal_bases: ["consent", "contract", "legal_obligation",
                "vital_interests", "public_interest", "legitimate_interest"]
  mandatory_document: "dpia_template"
  regulator_name: varies by member state
  breach_notification_deadline: "72 hours"
  dsar_response_deadline_days: 30
```

Only ZA is implemented in this phase. EU/BR/etc. are defined here for API design guidance — to ensure the data model is jurisdiction-agnostic.

### 1.3 Flyway Migration

Single migration adding new columns to `org_settings` and creating new tables (see sections below). Use the next available migration version number.

---

## Section 2 — Data Subject Export

### 2.1 Export Scope

When a staff member triggers "Download all data" for a customer, the system produces a ZIP file containing all personal information associated with that customer across the tenant schema:

| Entity | What's Included | Format |
|--------|----------------|--------|
| Customer | All fields (name, email, phone, address, tax number, custom field values, status, lifecycle dates) | JSON |
| PortalContact(s) | All portal contacts linked to this customer (name, email, phone, role) | JSON |
| Projects | All projects linked to this customer (name, description, status, dates, budget) | JSON |
| Documents | All documents scoped to this customer or their projects (metadata + file content from S3) | JSON metadata + original files |
| Tasks | All tasks in customer-linked projects (title, description, status, assignee names, dates) | JSON |
| Time Entries | All time entries on customer-linked projects (date, duration, description, member name, billable flag, rate) | JSON |
| Invoices | All invoices for this customer (number, status, line items, amounts, dates, payments) | JSON |
| Comments | All comments on customer-linked entities (author, content, dates) | JSON |
| Custom Field Values | All custom field values on customer and related entities | JSON |
| Audit Events | All audit events referencing this customer (filtered by entity_type + entity_id) | JSON |

### 2.2 Export Service

```
DataSubjectExportService
  + exportCustomerData(customerId: UUID) → ExportResult
  + getExportStatus(exportId: UUID) → ExportStatus
```

**Behavior**:
- Collects data from all related entities in the tenant schema
- Packages into a ZIP with a structured directory layout:
  ```
  customer-export-{id}/
  ├── customer.json
  ├── portal-contacts.json
  ├── projects/
  │   ├── project-{id}.json
  │   └── ...
  ├── documents/
  │   ├── document-{id}.json
  │   ├── document-{id}-file.pdf  (original file from S3)
  │   └── ...
  ├── time-entries.json
  ├── invoices.json
  ├── comments.json
  ├── custom-fields.json
  ├── audit-events.json
  └── export-metadata.json  (export date, tenant, jurisdiction, scope)
  ```
- Uploads the ZIP to S3 with a time-limited presigned URL (24-hour expiry)
- Creates an audit event: `DATA_SUBJECT_EXPORT` with the customer ID and export scope
- For large datasets (many documents/files), run synchronously for small exports (< 50 files), async with status polling for larger ones

### 2.3 Export Endpoint

**`POST /api/customers/{customerId}/data-export`** — triggers the export. Returns:
```json
{
  "exportId": "uuid",
  "status": "PROCESSING",
  "estimatedFiles": 23
}
```

**`GET /api/data-exports/{exportId}`** — poll for status. Returns:
```json
{
  "exportId": "uuid",
  "status": "COMPLETED",
  "downloadUrl": "https://s3.af-south-1.amazonaws.com/...",
  "expiresAt": "2026-03-20T10:00:00Z",
  "fileCount": 23,
  "totalSizeBytes": 4521000
}
```

**`GET /api/data-exports`** — list all exports for the tenant (audit trail). Paginated.

**Authorization**: Requires `OWNER` or `ADMIN` role. This is a sensitive operation — members should not be able to export all client data.

### 2.4 Frontend — Export Action

Add a "Download all data" button to the **Customer Detail page** (in the actions dropdown or a dedicated "Data Protection" tab):

- Button click → confirmation dialog: "This will export all personal information associated with {customer name}. The export may take a few minutes for customers with many documents."
- On confirm → POST to export endpoint → show progress indicator
- On completion → download link with expiry countdown
- Show export history (list of previous exports with dates and download links if still valid)

---

## Section 3 — Data Subject Anonymization

### 3.1 Anonymization Strategy

Anonymization replaces personal information with non-identifying reference values while preserving the structural integrity of financial records. This is a **destructive, irreversible operation**.

**What gets anonymized:**

| Entity | Field | Anonymized Value |
|--------|-------|-----------------|
| Customer | name | `"Anonymized Customer REF-{shortId}"` |
| Customer | email | `"anonymized-{shortId}@redacted.invalid"` |
| Customer | phone | `null` |
| Customer | address fields | `null` |
| Customer | tax_number | `null` |
| Customer | notes | `null` |
| Customer | custom field values (PI-flagged) | `null` |
| PortalContact | name, email, phone | Same pattern as Customer |
| Comments | content (on customer entities) | `"[Content removed — data subject anonymization]"` |
| Documents | content/files (customer-scoped) | Files deleted from S3, metadata retained with `"[Removed]"` title |

**What is preserved (for tax compliance):**

| Entity | Preserved Fields | Why |
|--------|-----------------|-----|
| Invoices | Number, dates, line items, amounts, tax, status, payments | SA Income Tax Act Section 29, VAT Act Section 55 |
| Invoices | Customer reference | Changed to `"REF-{shortId}"` (anonymized but traceable for audit) |
| Time Entries (billable) | Date, duration, rate, amount | Part of the financial record chain |
| Time Entries | Description, member reference | Description cleared, member name retained (member is not the data subject) |
| Projects | Retained with anonymized customer reference | Financial context |
| Audit Events | Retained in full | Regulatory requirement — audit trail is immutable |

### 3.2 Anonymization Safeguards

This is the most dangerous operation in the system. Safeguards:

1. **Confirmation flow**: Two-step confirmation. First: "Are you sure?" with summary of what will be anonymized. Second: type the customer name to confirm (same pattern as GitHub repository deletion).
2. **Financial record check**: Before anonymization, check if any invoices are within the financial retention period (`financial_retention_months` from OrgSettings). If so, warn: "This customer has invoices within the {N}-year financial retention period. Financial records will be preserved in anonymized form, but personal data will be removed."
3. **Pre-anonymization export**: Automatically trigger a data export before anonymization. The export is stored for the retention period as proof that the data subject's request was fulfilled.
4. **Audit event**: `DATA_SUBJECT_ANONYMIZED` with metadata: who performed it, what was anonymized, reference ID assigned.
5. **Status marker**: Customer status set to `ANONYMIZED` (new enum value). Anonymized customers appear in lists with a visual indicator and cannot be edited.
6. **Irreversibility**: No undo. The pre-anonymization export is the only recovery path.

### 3.3 Anonymization Service

```
DataSubjectAnonymizationService
  + anonymizeCustomer(customerId: UUID, performedBy: UUID) → AnonymizationResult
  + previewAnonymization(customerId: UUID) → AnonymizationPreview
```

**`previewAnonymization`** returns a summary of what will be affected:
```json
{
  "customerId": "uuid",
  "customerName": "John Smith",
  "affectedEntities": {
    "portalContacts": 2,
    "projects": 3,
    "documents": 12,
    "timeEntries": 45,
    "invoices": 8,
    "comments": 23,
    "customFieldValues": 6
  },
  "financialRecordsRetained": 8,
  "financialRetentionExpiresAt": "2031-03-19"
}
```

### 3.4 Anonymization Endpoint

**`POST /api/customers/{customerId}/anonymize`** — triggers anonymization.

Request body:
```json
{
  "confirmationName": "John Smith",
  "reason": "Data subject request"
}
```

**Authorization**: `OWNER` only. Not admin, not member. This is irreversible.

### 3.5 Frontend — Anonymization Action

Add to Customer Detail page (actions dropdown or Data Protection tab):

- "Delete personal data" button → preview dialog showing affected entity counts
- Financial retention warning if applicable
- Type-to-confirm step (customer name)
- Processing indicator → completion confirmation
- After anonymization: customer detail page shows anonymized state with `ANONYMIZED` badge, fields display anonymized values, edit is disabled

---

## Section 4 — Retention Policies

### 4.1 RetentionPolicy Entity

```
RetentionPolicy (tenant-scoped)
  id: UUID
  entity_type: VARCHAR(50)       — "customer", "time_entry", "document", "comment", "audit_event"
  retention_months: INTEGER      — how long to retain after last activity
  action: VARCHAR(20)            — "anonymize" or "delete"
  enabled: BOOLEAN               — whether this policy is actively enforced
  last_evaluated_at: TIMESTAMP   — when the purge job last checked this policy
  created_at / updated_at: TIMESTAMP
```

**Seeded defaults** (when `data_protection_jurisdiction = "ZA"`):

| Entity Type | Default Retention | Action | Notes |
|-------------|-------------------|--------|-------|
| `customer` | 60 months | anonymize | Matches financial retention |
| `time_entry` | 60 months | delete | After financial retention expires |
| `document` | 60 months | delete | After financial retention expires |
| `comment` | 36 months | delete | No regulatory requirement |
| `audit_event` | 84 months (7 years) | delete | Best practice for audit trails |

**Constraint**: `retention_months` for entity types linked to financial records cannot be set below `financial_retention_months` from OrgSettings. The UI should enforce this with validation.

### 4.2 Retention Evaluation Job

A scheduled job (cron or manual trigger) that evaluates retention policies:

```
RetentionEvaluationService
  + evaluateAll() → RetentionEvaluationResult
  + evaluatePolicy(policyId: UUID) → PolicyEvaluationResult
  + previewPurge(policyId: UUID) → PurgePreview
```

**Behavior**:
1. For each enabled `RetentionPolicy`, find entities of that type where `last_activity_date + retention_months < now()`
2. "Last activity" varies by entity type:
   - Customer: most recent of (updated_at, last invoice date, last time entry date, last project activity)
   - Time entry: the entry's `date` field
   - Document: `updated_at`
   - Comment: `created_at`
   - Audit event: `created_at`
3. **Two-phase approach**: First, identify candidates and send notifications (30-day warning). Second, execute the action (anonymize or delete) after the warning period if no intervention.
4. Notification to org owners: "X customers are approaching retention limits and will be anonymized in 30 days. Review and extend if needed."
5. Audit event: `RETENTION_PURGE_EXECUTED` with entity type, count, and policy details.

**Trigger**: Manual via admin endpoint initially. Cron scheduling is future work (this phase provides the manual trigger and the evaluation logic). A "Run retention check" button in settings is sufficient for v1.

### 4.3 Retention Policy Endpoints

**`GET /api/settings/retention-policies`** — list all policies for the tenant.
**`PUT /api/settings/retention-policies/{id}`** — update a policy (retention period, action, enabled).
**`POST /api/settings/retention-policies/evaluate`** — trigger manual evaluation. Returns preview of what would be affected.
**`POST /api/settings/retention-policies/execute`** — execute pending purges (after preview/confirmation).

**Authorization**: `OWNER` or `ADMIN`.

### 4.4 Frontend — Retention Settings

Add a "Data Retention" section to **Settings → Data Protection** (new settings tab):

- Table of retention policies with columns: Entity Type, Retention Period, Action, Enabled, Last Evaluated
- Inline editing for retention period and action
- Validation: cannot set financial-linked retention below the jurisdiction minimum
- "Run retention check" button → shows preview of affected entities → confirm to execute
- Warning banner if retention policies are not configured: "Data retention policies are not configured. Personal information will be retained indefinitely."

---

## Section 5 — DSAR Tracking

### 5.1 DataSubjectRequest Entity

```
DataSubjectRequest (tenant-scoped)
  id: UUID
  customer_id: UUID (nullable)      — linked customer, if identifiable
  subject_name: VARCHAR(255)        — name of the requesting person
  subject_email: VARCHAR(255)       — contact email
  request_type: VARCHAR(30)         — "ACCESS", "CORRECTION", "DELETION", "OBJECTION"
  status: VARCHAR(20)               — "RECEIVED", "VERIFIED", "PROCESSING", "COMPLETED", "DENIED"
  received_at: TIMESTAMP            — when the request was received
  deadline_at: TIMESTAMP            — calculated from jurisdiction (e.g., received_at + 30 days for POPIA)
  completed_at: TIMESTAMP (nullable)
  resolution_notes: TEXT (nullable)  — how the request was resolved
  performed_by: UUID (nullable)     — member who processed the request
  created_at / updated_at: TIMESTAMP
```

### 5.2 DSAR Lifecycle

```
RECEIVED → VERIFIED → PROCESSING → COMPLETED
                                  → DENIED (with reason)
```

- **RECEIVED**: Staff logs the request (manual intake form)
- **VERIFIED**: Staff confirms the identity of the requester (out-of-band — phone, email, ID document). Manual status transition.
- **PROCESSING**: Work is in progress (export being generated, anonymization being prepared)
- **COMPLETED**: Request fulfilled. Resolution notes recorded. Audit event created.
- **DENIED**: Request denied with documented reason (e.g., legal obligation to retain, identity not verified). Audit event created.

**Deadline tracking**: On creation, `deadline_at` is calculated from the jurisdiction's `dsar_response_deadline_days` (30 days for POPIA). Notifications sent at 7 days and 2 days before deadline. Overdue requests are highlighted in the UI.

### 5.3 DSAR Endpoints

**`POST /api/dsar`** — create a new request. Body: `{ subjectName, subjectEmail, requestType, customerId?, receivedAt? }`.
**`GET /api/dsar`** — list all requests for the tenant. Filterable by status, type. Paginated.
**`GET /api/dsar/{id}`** — get request details.
**`PUT /api/dsar/{id}/status`** — transition status. Body: `{ status, resolutionNotes? }`.

**Authorization**: `OWNER` or `ADMIN`.

### 5.4 Frontend — DSAR Management

Add a "Data Requests" page accessible from **Settings → Data Protection**:

- Table of DSARs: Subject Name, Type, Status, Received, Deadline, Assigned To
- Status badges with color coding (green = completed, red = overdue, amber = approaching deadline)
- "Log new request" dialog: subject name, email, request type, linked customer (optional dropdown), date received
- Detail view: full request timeline, status transitions, linked export/anonymization actions
- Quick actions: from a DSAR detail, buttons to "Export customer data" or "Anonymize customer data" (linking directly to the Section 2/3 functionality)

---

## Section 6 — PAIA Manual Generation

### 6.1 Template Strategy

Leverage the existing Phase 12 document template engine. The PAIA Section 51 manual is a document template with a dedicated context builder.

**Template**: A new template in the "compliance" template pack, seeded for jurisdiction "ZA". The template content follows the Information Regulator's prescribed format for a Section 51 manual:

1. Contact details of the private body (from OrgSettings: org name, address, information officer)
2. Description of the guide on how to use PAIA (standard boilerplate)
3. Categories of records held (derived from the platform's data model — customers, projects, invoices, etc.)
4. Processing description (from the processing register if populated, otherwise sensible defaults)
5. How to submit an access request (the firm's contact details)
6. Remedies available if request is refused

### 6.2 PAIA Context Builder

```
PaiaManualContextBuilder implements TemplateContextBuilder
  + buildContext(tenantSchema: String) → Map<String, Object>
```

Assembles context from:
- `OrgSettings` (org name, information officer, address, contact details, branding)
- `RetentionPolicy` entries (for the data retention section)
- Processing register entries if they exist (Section 6.3 below)
- Standard boilerplate text (PAIA Section 51 requirements)
- Current date for the "last updated" field

### 6.3 Frontend — Generate PAIA Manual

Add a "Generate PAIA Manual" button to **Settings → Data Protection**:

- Click → preview the generated document (HTML preview, same as Phase 12 document preview)
- "Download PDF" button on the preview
- The generated document is saved as a `GeneratedDocument` (existing Phase 12 entity) for recordkeeping
- Re-generation updates the document with current org settings

---

## Section 7 — Processing Activity Register

### 7.1 ProcessingActivity Entity

```
ProcessingActivity (tenant-scoped)
  id: UUID
  category: VARCHAR(100)           — "Client Information", "Financial Records", "Employee Data", etc.
  description: TEXT                — what PI is collected and processed
  legal_basis: VARCHAR(50)         — "contractual_necessity", "legitimate_interest", "legal_obligation", etc.
  data_subjects: VARCHAR(255)      — who the data is about: "Clients", "Employees", "Suppliers"
  retention_period: VARCHAR(100)   — human-readable: "5 years after last engagement"
  recipients: VARCHAR(255)         — who the data is shared with: "SARS (legal obligation)", "None"
  created_at / updated_at: TIMESTAMP
```

### 7.2 Seeded Defaults

When `data_protection_jurisdiction` is set, seed sensible processing activity entries based on the platform's known data model:

| Category | Description | Legal Basis | Data Subjects | Retention |
|----------|-------------|-------------|---------------|-----------|
| Client Information | Names, contact details, tax numbers for client relationship management | Contractual necessity | Clients | Duration of engagement + 5 years |
| Financial Records | Invoices, payment records, billing information | Legal obligation (Income Tax Act, VAT Act) | Clients | 5 years from end of tax year |
| Time & Work Records | Time entries, task descriptions linked to client work | Legitimate interest (service delivery) | Clients, Employees | Duration of engagement + 5 years |
| Project Documentation | Documents, proposals, correspondence related to engagements | Contractual necessity | Clients | Duration of engagement + 5 years |
| Communication Records | Comments, notifications, activity feeds | Legitimate interest (operational records) | Clients, Employees | 3 years |
| Portal Access | Magic link tokens, access logs for client portal | Contractual necessity | Client contacts | Duration of portal access + 1 year |

### 7.3 Processing Register Endpoints

**`GET /api/settings/processing-activities`** — list all entries. Paginated.
**`POST /api/settings/processing-activities`** — create a new entry.
**`PUT /api/settings/processing-activities/{id}`** — update an entry.
**`DELETE /api/settings/processing-activities/{id}`** — delete an entry.

**Authorization**: `OWNER` or `ADMIN`.

### 7.4 Frontend — Processing Register Page

Add a "Processing Register" section to **Settings → Data Protection**:

- Table of processing activities: Category, Description, Legal Basis, Data Subjects, Retention
- Add/edit/delete entries
- "Export register" button → downloads as PDF or CSV (for submission to the Information Regulator if requested)
- Pre-populated with seeded defaults — tenants can edit to match their actual practices

---

## Section 8 — Data Protection Settings Tab

### 8.1 Settings Page Structure

Add a new **"Data Protection"** tab to the Settings page. This tab consolidates all data protection features:

| Section | Content |
|---------|---------|
| **Jurisdiction** | Dropdown to set `data_protection_jurisdiction` (South Africa, EU, Brazil, or "Not configured"). Changing jurisdiction seeds default retention policies and processing activities. |
| **Information Officer** | Name and email fields for the designated information officer. |
| **Data Requests** | Link to the DSAR management page (Section 5.4). Shows count of open/overdue requests as a badge. |
| **Retention Policies** | Retention policy table (Section 4.4). |
| **Processing Register** | Processing activity table (Section 7.4). |
| **PAIA Manual** | Generate/download button (Section 6.3). Shows last generated date. |

### 8.2 Jurisdiction Onboarding Flow

When a tenant sets their jurisdiction for the first time:

1. Set `data_protection_jurisdiction` on OrgSettings
2. Seed default retention policies for the jurisdiction
3. Seed default processing activities
4. Seed the PAIA manual template (if ZA)
5. Show a notification: "Data protection settings configured for {jurisdiction}. Review your retention policies and processing register."

This is additive — setting a jurisdiction does not remove existing data or configuration.

---

## Section 9 — API Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/customers/{id}/data-export` | Trigger data export |
| `GET` | `/api/data-exports/{id}` | Get export status/download URL |
| `GET` | `/api/data-exports` | List all exports |
| `POST` | `/api/customers/{id}/anonymize` | Anonymize customer data |
| `GET` | `/api/customers/{id}/anonymize/preview` | Preview anonymization impact |
| `GET` | `/api/settings/retention-policies` | List retention policies |
| `PUT` | `/api/settings/retention-policies/{id}` | Update a retention policy |
| `POST` | `/api/settings/retention-policies/evaluate` | Preview retention evaluation |
| `POST` | `/api/settings/retention-policies/execute` | Execute retention purge |
| `POST` | `/api/dsar` | Log a new DSAR |
| `GET` | `/api/dsar` | List DSARs |
| `GET` | `/api/dsar/{id}` | Get DSAR details |
| `PUT` | `/api/dsar/{id}/status` | Update DSAR status |
| `GET` | `/api/settings/processing-activities` | List processing activities |
| `POST` | `/api/settings/processing-activities` | Create processing activity |
| `PUT` | `/api/settings/processing-activities/{id}` | Update processing activity |
| `DELETE` | `/api/settings/processing-activities/{id}` | Delete processing activity |
| `POST` | `/api/settings/paia-manual/generate` | Generate PAIA manual |

---

## Section 10 — Testing

### 10.1 Backend Tests

| Test | What it verifies |
|------|-----------------|
| `DataSubjectExportServiceTest` | Collects data from all related entities for a customer |
| `DataSubjectExportServiceTest` | Generates correct ZIP structure with all files |
| `DataSubjectExportServiceTest` | Creates audit event on export |
| `DataSubjectExportServiceTest` | Handles customers with no documents/invoices gracefully |
| `DataSubjectAnonymizationServiceTest` | Anonymizes customer PI fields correctly |
| `DataSubjectAnonymizationServiceTest` | Preserves invoice financial data with anonymized reference |
| `DataSubjectAnonymizationServiceTest` | Preserves billable time entry amounts |
| `DataSubjectAnonymizationServiceTest` | Deletes S3 files for customer-scoped documents |
| `DataSubjectAnonymizationServiceTest` | Creates pre-anonymization export |
| `DataSubjectAnonymizationServiceTest` | Sets customer status to ANONYMIZED |
| `DataSubjectAnonymizationServiceTest` | Rejects anonymization without correct confirmation name |
| `DataSubjectAnonymizationServiceTest` | Creates audit event with full details |
| `RetentionEvaluationServiceTest` | Identifies entities past retention period |
| `RetentionEvaluationServiceTest` | Respects financial retention minimum |
| `RetentionEvaluationServiceTest` | Sends warning notifications before purge |
| `RetentionEvaluationServiceTest` | Executes anonymize vs. delete correctly |
| `DataSubjectRequestServiceTest` | Creates DSAR with correct deadline calculation |
| `DataSubjectRequestServiceTest` | Status transitions follow valid lifecycle |
| `DataSubjectRequestServiceTest` | Sends deadline warning notifications |
| `ProcessingActivityServiceTest` | CRUD operations work correctly |
| `ProcessingActivityServiceTest` | Jurisdiction seeding creates default entries |
| `PaiaManualContextBuilderTest` | Assembles correct context from OrgSettings |
| `DataExportControllerTest` | Authorization: only OWNER/ADMIN can export |
| `AnonymizationControllerTest` | Authorization: only OWNER can anonymize |
| `RetentionPolicyControllerTest` | Validates minimum retention for financial entities |

### 10.2 Frontend Tests

| Test | What it verifies |
|------|-----------------|
| Data Protection settings tab | Renders all sections correctly |
| Jurisdiction selector | Seeds defaults on first selection |
| Export action | Confirmation dialog, progress, download link |
| Anonymization action | Two-step confirmation with name typing |
| Anonymization action | Shows preservation warning for financial records |
| Retention policies table | Inline editing with validation |
| DSAR management | Create, list, status transitions |
| DSAR management | Deadline badges (green/amber/red) |
| Processing register | CRUD with export |
| PAIA manual | Generate and download |

---

## Out of Scope

- **Portal self-serve DSAR submission.** Data subjects contact the firm directly. Portal DSAR intake is a future enhancement.
- **Consent capture engine.** B2B professional services processing is primarily under contractual necessity and legitimate interest. Consent management can be added incrementally if specific use cases emerge.
- **Automated breach notification to the Information Regulator.** This is an organizational process. The system provides a breach incident log field on DSARs but does not send notifications to external parties.
- **Cross-border transfer documentation.** af-south-1 hosting eliminates this for SA. EU tenants would need adequacy assessment documentation — future work.
- **Data Protection Impact Assessments (DPIA).** GDPR requirement, not POPIA. Future jurisdiction pack.
- **Automated cron-based retention enforcement.** v1 provides manual trigger with preview/confirm. Cron scheduling is a configuration step on top of the existing logic.
- **Member/employee PI handling.** This phase focuses on customer/client data subjects. Employee data protection (members of the org) is a separate concern with different retention rules.
- **Encryption at rest beyond what PostgreSQL/S3 provide.** AWS af-south-1 provides encryption at rest by default. Application-level encryption of specific fields is out of scope.

## ADR Topics

- **ADR: Anonymization vs. deletion** — why anonymization is preferred over hard deletion for POPIA compliance. Financial record preservation requirements make hard deletion illegal for invoiced customers. Anonymization satisfies both the data subject's right to deletion (Section 24) and the responsible party's legal obligation to retain financial records (Income Tax Act Section 29).
- **ADR: Retention policy granularity** — per-entity-type policies vs. per-record policies vs. global policy. Per-entity-type balances flexibility with simplicity. Per-record is too granular (every customer would need individual retention config). Global is too coarse (financial records need different retention than comments).
- **ADR: DSAR deadline calculation** — jurisdiction-based deadline (30 days POPIA, 30 days GDPR) vs. configurable per-tenant. Recommend jurisdiction-based with the ability to override to a shorter deadline (firms may want to respond faster than required). Never allow longer than jurisdiction maximum.
- **ADR: Pre-anonymization export storage** — how long to keep the pre-anonymization export. Recommend same as `financial_retention_months` — the export serves as proof of compliance and should be retained as long as the financial records it relates to.

## Style & Boundaries

- Follow existing patterns exactly. The export service should look like existing report generation services. The anonymization service follows the pattern of existing lifecycle transitions. DSAR entity follows existing entity patterns (tenant-scoped, audited).
- The Data Protection settings tab is a first-class settings section, not an afterthought. It should feel as polished as the existing Rate Cards or Budget settings.
- Anonymization is the most security-critical feature in the platform. Test it exhaustively. Every field that should be anonymized must be verified. Every field that should be preserved must be verified. Edge cases: customer with no invoices (straight delete is fine), customer with invoices in retention period (anonymize but preserve), customer already anonymized (reject with clear error).
- The DSAR tracking is intentionally lightweight. No workflow engine, no assignment system, no SLA escalation. It's a log with status tracking and deadline awareness. Firms with 3-10 people don't need ticketing — they need a checklist.
- Jurisdiction awareness means field names and labels are generic. The code says `dataProtectionJurisdiction`, not `popiaEnabled`. The template says "Information Officer", not "POPIA Information Officer". The legal basis labels come from jurisdiction seed data, not hardcoded strings.
- Pre-populate aggressively. When a tenant sets their jurisdiction, they should see sensible defaults everywhere — retention policies, processing activities, PAIA manual content. The goal is "review and adjust", not "build from scratch."
