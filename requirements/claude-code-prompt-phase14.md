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
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate` (3-level hierarchy: org-default -> project-override -> customer-override), `CostRate`, `ProjectBudget`, `OrgSettings` (default currency). Time entries have `billable` flag, `billing_rate_snapshot`, `cost_rate_snapshot`. Profitability reports.
- **Operational dashboards** (Phase 9): company dashboard, project overview, personal dashboard, health scoring.
- **Invoicing & billing from time** (Phase 10): Invoice/InvoiceLine entities, draft-to-paid lifecycle, unbilled time management, PSP adapter seam, HTML invoice preview via Thymeleaf.
- **Tags, custom fields & views** (Phase 11): `FieldDefinition`, `FieldGroup`, `Tag`, `EntityTag`, `SavedView` entities. JSONB custom field values on projects, tasks, and customers. Platform-shipped field packs with per-tenant seeding. Saved filtered views with custom column selection.
- **Document templates & PDF generation** (Phase 12): `DocumentTemplate`, `GeneratedDocument` entities. Thymeleaf + OpenHTMLToPDF rendering pipeline. Template packs (seed data). Org branding (logo, brand color, footer text on OrgSettings).

For **Phase 13**, I want to add **Customer Compliance & Lifecycle** — a jurisdiction-agnostic compliance system that manages the full customer lifecycle (prospect through offboarding), with configurable onboarding checklists, data subject request handling, and retention policies.

***

## Objective of Phase 13

Design and specify:

1. **Customer lifecycle state machine** — a configurable status progression (Prospect → Onboarding → Active → Dormant → Offboarded) with transition guards, audit trail, and the ability to gate platform actions based on lifecycle status.
2. **Checklist engine** — a first-class `ChecklistTemplate` → `ChecklistInstance` → `ChecklistItem` model that tracks per-step completion (who, when), supports ordering and dependencies, and blocks lifecycle transitions until required items are complete.
3. **Onboarding document requirements** — required document types per checklist template, with status gates that prevent lifecycle advancement until all required documents are uploaded and verified.
4. **Compliance packs** — seed data for jurisdiction-specific onboarding requirements (e.g., "SA Individual FICA", "SA Company FICA"), following the same pack pattern as field packs (Phase 11) and template packs (Phase 12). The engine is generic; only the pack content is jurisdiction-specific.
5. **Data subject request handling** — intake, tracking, and fulfilment of data access/export and deletion/anonymization requests, with configurable response deadlines and audit trail.
6. **Retention policies** — configurable retention periods per record type, automatic flagging of records past retention, and admin purge/anonymize workflow.
7. **Frontend** — lifecycle status on customer detail, onboarding progress tracker, compliance dashboard for admins, data request management UI, retention policy configuration.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - External KYC/identity verification services — the platform does not verify identities itself. It provides the checklist engine and document collection workflow. Integration with verification providers (nCino, Sumsub, etc.) is a future phase via the BYOAK integration framework.
    - Workflow/BPM engines (Camunda, Flowable, etc.) — the lifecycle state machine is a simple, bounded state machine implemented directly in the service layer. No external engine needed.
    - Consent management platforms or cookie banner tools — out of scope for a B2B practice management platform.
- **Jurisdiction-agnostic design principle**: The engine (state machine, checklist runner, retention scheduler) is code. Jurisdiction-specific content (which checklist items, which retention periods, which field requirements) is seed data delivered as compliance packs. No jurisdiction-specific code in the core platform.
- **Generic UI terminology**: Use "Compliance Status", "Onboarding", "Data Request", "Retention Policy" — not "FICA", "POPIA", "GDPR". Pack names can use jurisdiction-specific terms (e.g., pack named "SA FICA — Individual"), but the UI framework is generic.

2. **Tenancy**

- All new entities (`ChecklistTemplate`, `ChecklistInstance`, `ChecklistItem`, `DataSubjectRequest`, `RetentionPolicy`) follow the same tenant isolation model as existing entities:
    - Pro orgs: dedicated schema.
    - Starter orgs: `tenant_shared` schema with `tenant_id` column + Hibernate `@Filter` + RLS.
- All new entities must include Flyway migrations for both tenant and shared schemas.
- Compliance packs are seeded per-tenant during tenant provisioning (same pattern as field packs and template packs).

3. **Permissions model**

- **Checklist template management** (create, edit, delete templates):
    - Org admins and owners only.
- **Checklist instance management** (complete items, verify documents):
    - Org admins, owners, and the project lead of the customer's linked project (if applicable). Members with customer access can view but not complete items.
- **Customer lifecycle transitions** (change status):
    - Org admins and owners. Automated transitions (e.g., all checklist items complete → status advances) respect the same permission model.
- **Data subject request management** (create, process, fulfil requests):
    - Org admins and owners only. These involve data export/deletion — admin-level operations.
- **Retention policy configuration**:
    - Org admins and owners only.
- **Compliance dashboard** (view onboarding status, pending requests, retention flags):
    - Org admins and owners. Members see only the lifecycle status on customer cards they have access to.

4. **Relationship to existing entities**

- **Customer**: Extended with a `lifecycle_status` field (ENUM: `PROSPECT`, `ONBOARDING`, `ACTIVE`, `DORMANT`, `OFFBOARDED`, default `PROSPECT`). This is a core entity field, not a custom field — it drives platform behaviour (action gating, filtering, dashboard metrics).
- **Document**: Onboarding document requirements reference the existing Document entity. When a required document type is uploaded to a customer, the checklist item can be marked as fulfilled. Documents remain in the existing document system — no parallel storage.
- **Custom Fields** (Phase 11): Compliance packs can ship custom field definitions alongside checklist templates (e.g., "SA FICA" pack includes both a checklist template and field definitions for ID number, tax number, etc.). The pack seeder handles both.
- **AuditEvent**: All lifecycle transitions, checklist completions, data subject requests, and retention actions are audited. The existing audit infrastructure handles this.
- **Notification**: Configurable notifications for lifecycle transitions ("Customer X moved to Active"), approaching retention deadlines, and data subject request deadlines.
- **Activity Feed**: Lifecycle transitions and checklist completions appear in the customer's activity feed (extending the existing activity system).

5. **Out of scope for Phase 13**

- Identity verification via external APIs (DHA, CIPC, biometric providers). The platform collects documents and tracks verification steps — actual verification is manual or via future integrations.
- Sanctions/PEP screening. This is dedicated KYC provider territory.
- Automated data discovery across external systems. Data export/deletion covers data within the platform only.
- Consent management (opt-in/opt-out tracking for marketing, cookies). Not relevant for B2B practice management.
- Compliance reporting/analytics (e.g., "average onboarding time", "requests by type"). A future reporting phase can query the data; this phase captures it.
- Multi-step approval workflows for lifecycle transitions (e.g., "two admins must approve offboarding"). Simple permission checks are sufficient for v1.
- Automated anonymization scheduling (auto-purge after retention period). v1 flags records and presents them to admins for manual action. Automated purge is a future enhancement.
- Customer self-service data requests via the portal. Requests are submitted by the org's admins on behalf of the data subject. Portal integration is a future enhancement.

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase13-customer-compliance-lifecycle.md`, plus ADRs for key decisions.

### 1. Customer lifecycle state machine

Design the **lifecycle status** model:

1. **Status values and transitions**

    - `PROSPECT` — initial status. The customer record exists but no engagement has started. No onboarding checklist instantiated yet.
    - `ONBOARDING` — active engagement has begun. A checklist instance is created from a template. The customer cannot be billed (invoicing blocked) until onboarding is complete.
    - `ACTIVE` — onboarding complete, all required checklist items verified. Full platform functionality available.
    - `DORMANT` — no active projects or time entries for a configurable period. Automated detection via a scheduled check, or manual transition by admin. Can be reactivated to `ACTIVE`.
    - `OFFBOARDED` — relationship ended. Retention clock starts. Customer data is read-only (no new projects, tasks, time entries, invoices). Can be reversed to `ACTIVE` if needed (with audit trail).

    Valid transitions:
    ```
    PROSPECT → ONBOARDING (admin action: "Start Onboarding", instantiates checklist)
    ONBOARDING → ACTIVE (guard: all required checklist items complete)
    ACTIVE → DORMANT (automated or manual)
    DORMANT → ACTIVE (admin action: "Reactivate")
    ACTIVE → OFFBOARDED (admin action: "Offboard Customer")
    DORMANT → OFFBOARDED (admin action: "Offboard Customer")
    OFFBOARDED → ACTIVE (admin action: "Reactivate", with audit justification)
    ```

    Invalid transitions (e.g., `PROSPECT → ACTIVE` skipping onboarding, `OFFBOARDED → ONBOARDING`) should be rejected by the state machine.

2. **Action gating by lifecycle status**

    Define which platform actions are gated by lifecycle status. This is the mechanism that gives the lifecycle real teeth — it's not just a label.

    - `PROSPECT`: Can create the customer record, add basic information, upload documents. Cannot create projects, tasks, time entries, or invoices for this customer.
    - `ONBOARDING`: Can create projects and tasks linked to this customer (work may begin during onboarding). Cannot create invoices for this customer (billing blocked until Active).
    - `ACTIVE`: Full functionality. No restrictions.
    - `DORMANT`: Same as Active (full functionality). Dormant is informational — the customer can be worked with if needed.
    - `OFFBOARDED`: Read-only. Cannot create new projects, tasks, time entries, invoices, or documents for this customer. Existing data is viewable. Comments on existing items are allowed (for notes about offboarding/handover).

    The gating should be implemented as a service-layer check (`CustomerLifecycleGuard`) called by controllers before mutation operations. The guard checks the customer's lifecycle status and throws a `403 Forbidden` (or `409 Conflict` with a descriptive message) if the action is not permitted.

3. **Customer entity changes**

    - Add `lifecycle_status` column (VARCHAR(20), NOT NULL, DEFAULT 'PROSPECT') to the Customer table.
    - Add `lifecycle_status_changed_at` (TIMESTAMP, nullable) — when the status last changed.
    - Add `lifecycle_status_changed_by` (UUID, nullable) — member who last changed the status.
    - Add `offboarded_at` (TIMESTAMP, nullable) — when the customer was offboarded. Used for retention period calculation.
    - Flyway migration for both tenant and shared schemas.

4. **Dormancy detection**

    - A configurable dormancy threshold (days since last activity) stored on OrgSettings: `dormancy_threshold_days` (INTEGER, nullable, default 90).
    - "Activity" defined as: any time entry, task status change, document upload, invoice creation, or comment on items linked to this customer.
    - Detection can be a scheduled job (daily) or on-demand check. For v1, implement as an on-demand admin action ("Check for Dormant Customers" button on the compliance dashboard) rather than a background scheduler. Simpler, no cron infrastructure needed.
    - The check queries for Active customers with no linked activity in the past N days and presents them for admin review before transitioning.

### 2. Checklist engine

Design the **first-class checklist** entity model:

1. **ChecklistTemplate**

    - `id` (UUID).
    - `tenant_id`.
    - `name` (VARCHAR(200) — e.g., "Individual Client Onboarding", "Company Client Onboarding").
    - `slug` (VARCHAR(200) — machine-readable key, e.g., "individual-client-onboarding").
    - `description` (TEXT, nullable — describes when this checklist applies).
    - `customer_type` (ENUM: `INDIVIDUAL`, `COMPANY`, `ANY` — which type of customer this checklist is for. `ANY` means it applies to both).
    - `source` (ENUM: `PLATFORM`, `ORG_CUSTOM` — same pattern as document templates).
    - `pack_id` (VARCHAR, nullable — compliance pack this template originated from).
    - `pack_template_key` (VARCHAR, nullable — key within the pack).
    - `active` (BOOLEAN, default true).
    - `auto_instantiate` (BOOLEAN, default true — if true, this checklist is automatically instantiated when a customer transitions to ONBOARDING. If false, it must be manually instantiated by an admin).
    - `sort_order` (INTEGER).
    - `created_at`, `updated_at`.
    - Constraints:
        - `(tenant_id, slug)` unique.
        - Only one `auto_instantiate = true` template per `customer_type` per tenant (the "default" onboarding checklist). Enforced at the service layer, not DB constraint.

2. **ChecklistTemplateItem** (the "recipe" — what items exist in a checklist template)

    - `id` (UUID).
    - `tenant_id`.
    - `template_id` (UUID, FK -> checklist_templates).
    - `name` (VARCHAR(300) — e.g., "Verify identity document", "Obtain proof of residential address").
    - `description` (TEXT, nullable — detailed instructions for the person completing this item).
    - `sort_order` (INTEGER — display order within the checklist).
    - `required` (BOOLEAN, default true — if true, this item must be completed before the checklist is considered complete. If false, it's optional/recommended).
    - `requires_document` (BOOLEAN, default false — if true, a document must be uploaded and linked to this item before it can be marked complete).
    - `required_document_label` (VARCHAR(200), nullable — e.g., "Certified copy of ID", "Proof of address (not older than 3 months)". Shown as the upload prompt).
    - `depends_on_item_id` (UUID, nullable, FK -> checklist_template_items — if set, this item cannot be started until the dependency item is complete. Simple linear dependencies, not a DAG).
    - `created_at`, `updated_at`.
    - Constraints:
        - `depends_on_item_id` cannot reference an item from a different template.
        - No circular dependencies (enforce at service layer).

3. **ChecklistInstance** (a checklist instantiated for a specific customer)

    - `id` (UUID).
    - `tenant_id`.
    - `template_id` (UUID, FK -> checklist_templates — which template this was instantiated from).
    - `customer_id` (UUID, FK -> customers — which customer this checklist is for).
    - `status` (ENUM: `IN_PROGRESS`, `COMPLETED`, `CANCELLED`).
    - `started_at` (TIMESTAMP — when the checklist was instantiated).
    - `completed_at` (TIMESTAMP, nullable — when all required items were completed).
    - `completed_by` (UUID, nullable — member who completed the last required item, triggering completion).
    - `created_at`, `updated_at`.
    - Constraints:
        - `(tenant_id, customer_id, template_id)` unique — a customer can only have one instance of a given checklist template.

4. **ChecklistInstanceItem** (individual items within an instantiated checklist)

    - `id` (UUID).
    - `tenant_id`.
    - `instance_id` (UUID, FK -> checklist_instances).
    - `template_item_id` (UUID, FK -> checklist_template_items — reference to the original template item).
    - `name` (VARCHAR(300) — copied from template item at instantiation time, so template changes don't retroactively alter in-progress checklists).
    - `description` (TEXT, nullable — copied from template item).
    - `sort_order` (INTEGER — copied from template item).
    - `required` (BOOLEAN — copied from template item).
    - `requires_document` (BOOLEAN — copied from template item).
    - `required_document_label` (VARCHAR(200), nullable — copied from template item).
    - `status` (ENUM: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`, `BLOCKED`).
    - `completed_at` (TIMESTAMP, nullable).
    - `completed_by` (UUID, nullable — member who marked this item complete).
    - `notes` (TEXT, nullable — optional notes added during completion, e.g., "Verified against original ID in person").
    - `document_id` (UUID, nullable, FK -> documents — if `requires_document`, the uploaded document linked to this item).
    - `depends_on_item_id` (UUID, nullable, FK -> checklist_instance_items — dependency, copied from template).
    - `created_at`, `updated_at`.
    - Constraints:
        - `(instance_id, template_item_id)` unique — one instance item per template item per instance.
        - `COMPLETED` status requires `completed_by` and `completed_at` to be non-null.
        - If `requires_document` is true, `COMPLETED` status requires `document_id` to be non-null.
        - If `depends_on_item_id` references an item that is not `COMPLETED`, status cannot be set to `IN_PROGRESS` or `COMPLETED` (enforced at service layer).

5. **Checklist completion logic**

    - When all `required = true` items in an instance are `COMPLETED`, the instance status automatically transitions to `COMPLETED`.
    - When the instance transitions to `COMPLETED`, if the customer's lifecycle status is `ONBOARDING`, attempt to transition to `ACTIVE` (guard: all active checklist instances for this customer must be `COMPLETED`).
    - Optional items do not block completion but are tracked for audit purposes.
    - `SKIPPED` items are treated as not-completed for the purpose of the completion check. Only `COMPLETED` counts.

### 3. Compliance packs

Design the seed data mechanism for jurisdiction-specific compliance content:

1. **Pack definition format**

    - Compliance packs are defined as directories under `src/main/resources/compliance-packs/`:
        ```
        compliance-packs/
            sa-fica-individual/
                pack.json
            sa-fica-company/
                pack.json
            generic-onboarding/
                pack.json
        ```
    - `pack.json` defines the pack metadata, checklist template, and optionally associated field definitions:
        ```json
        {
            "packId": "sa-fica-individual",
            "name": "SA FICA — Individual Client",
            "description": "FICA-compliant onboarding checklist for individual clients in South Africa",
            "jurisdiction": "ZA",
            "customerType": "INDIVIDUAL",
            "checklistTemplate": {
                "key": "sa-fica-individual-onboarding",
                "name": "Individual Client Onboarding (FICA)",
                "description": "Verify identity, address, and source of funds per FICA requirements",
                "autoInstantiate": false,
                "items": [
                    {
                        "key": "verify-identity",
                        "name": "Verify identity document",
                        "description": "Obtain and verify a certified copy of the client's South African ID or passport",
                        "sortOrder": 1,
                        "required": true,
                        "requiresDocument": true,
                        "requiredDocumentLabel": "Certified copy of SA ID / Passport",
                        "dependsOnKey": null
                    },
                    {
                        "key": "verify-address",
                        "name": "Verify residential address",
                        "description": "Obtain proof of residential address not older than 3 months (utility bill, bank statement, or municipal account)",
                        "sortOrder": 2,
                        "required": true,
                        "requiresDocument": true,
                        "requiredDocumentLabel": "Proof of address (not older than 3 months)",
                        "dependsOnKey": null
                    },
                    {
                        "key": "risk-assessment",
                        "name": "Complete risk assessment",
                        "description": "Assess the client's money laundering / terrorist financing risk rating based on FICA Schedule 3",
                        "sortOrder": 3,
                        "required": true,
                        "requiresDocument": false,
                        "dependsOnKey": "verify-identity"
                    },
                    {
                        "key": "source-of-funds",
                        "name": "Verify source of funds (if applicable)",
                        "description": "For high-risk clients or transactions above the prescribed threshold, verify and document the source of funds",
                        "sortOrder": 4,
                        "required": false,
                        "requiresDocument": true,
                        "requiredDocumentLabel": "Source of funds documentation",
                        "dependsOnKey": "risk-assessment"
                    },
                    {
                        "key": "sanctions-screening",
                        "name": "Sanctions and PEP screening",
                        "description": "Screen the client against sanctions lists and determine if they are a Prominent Influential Person (PIP)",
                        "sortOrder": 5,
                        "required": true,
                        "requiresDocument": false,
                        "dependsOnKey": "verify-identity"
                    }
                ]
            },
            "fieldDefinitions": [
                {
                    "slug": "sa_id_number",
                    "name": "SA ID Number",
                    "fieldType": "TEXT",
                    "entityType": "CUSTOMER",
                    "groupSlug": "identity",
                    "validation": "za_id"
                },
                {
                    "slug": "passport_number",
                    "name": "Passport Number",
                    "fieldType": "TEXT",
                    "entityType": "CUSTOMER",
                    "groupSlug": "identity"
                },
                {
                    "slug": "risk_rating",
                    "name": "Risk Rating",
                    "fieldType": "SELECT",
                    "entityType": "CUSTOMER",
                    "groupSlug": "compliance",
                    "options": ["Low", "Medium", "High"]
                }
            ]
        }
        ```

2. **Shipped packs (v1)**

    Ship three default packs:

    - `generic-onboarding`: A minimal, jurisdiction-agnostic onboarding checklist (verify contact details, upload engagement letter, confirm terms of service). Works for any country/vertical. `autoInstantiate = true`.
    - `sa-fica-individual`: SA FICA-compliant checklist for individual clients (as above). `autoInstantiate = false` (org must explicitly choose to use FICA checklists).
    - `sa-fica-company`: SA FICA-compliant checklist for company/trust clients (includes beneficial ownership verification, CIPC registration, director identification). `autoInstantiate = false`.

    The `generic-onboarding` pack is the default — it's auto-instantiated for all tenants. SA-specific packs are available but must be manually activated by the org admin.

3. **Pack activation model**

    - All packs are seeded during provisioning (checklist templates created with `active = false` for non-generic packs).
    - Org admins can activate/deactivate checklist templates in settings.
    - An org can have multiple active templates for different customer types (e.g., one for individuals, one for companies).
    - The `auto_instantiate` flag determines whether a checklist is automatically created when a customer enters ONBOARDING. Orgs can override this per template.

4. **Pack extensibility for forks**

    - Vertical forks add their own pack directories under `compliance-packs/`. The seeder discovers all packs on the classpath automatically.
    - Examples for future forks:
        - `uk-aml`: UK Anti-Money Laundering Regulations checklist.
        - `au-aml-ctf`: Australian AML/CTF Rules checklist.
        - `us-cdd`: US Customer Due Diligence (FinCEN CDD Rule) checklist.
    - No core code changes needed to add a new jurisdiction — just a new pack directory with `pack.json`.

### 4. Data subject request handling

Design the **data subject request** workflow:

1. **DataSubjectRequest entity**

    - `id` (UUID).
    - `tenant_id`.
    - `customer_id` (UUID, FK -> customers — which customer the request is about).
    - `request_type` (ENUM: `ACCESS`, `DELETION`, `CORRECTION`, `OBJECTION`).
        - `ACCESS` — the data subject wants a copy of their personal information held by the org.
        - `DELETION` — the data subject wants their personal information deleted or anonymized.
        - `CORRECTION` — the data subject wants inaccurate information corrected.
        - `OBJECTION` — the data subject objects to certain processing activities.
    - `status` (ENUM: `RECEIVED`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`).
    - `description` (TEXT — details of what the data subject is requesting).
    - `rejection_reason` (TEXT, nullable — if rejected, why. E.g., "Retention period not yet expired; legal obligation to retain").
    - `deadline` (DATE — calculated from org's configured response deadline. E.g., POPIA allows "a reasonable period" but common practice is 30 days; GDPR is 30 days).
    - `requested_at` (TIMESTAMP — when the request was received by the org).
    - `requested_by` (UUID — admin who logged the request in the system).
    - `completed_at` (TIMESTAMP, nullable).
    - `completed_by` (UUID, nullable).
    - `export_file_key` (VARCHAR, nullable — S3 key for the exported data file, if request type is ACCESS).
    - `notes` (TEXT, nullable — internal notes about the request processing).
    - `created_at`, `updated_at`.
    - Indexes:
        - `(tenant_id, status)` — for listing open requests.
        - `(tenant_id, customer_id)` — for viewing requests related to a customer.
        - `(tenant_id, deadline)` — for finding requests approaching their deadline.

2. **Data export (ACCESS requests)**

    - When an ACCESS request is being fulfilled, the system generates a data export package for the customer.
    - The export includes all platform data associated with the customer:
        - Customer profile (name, email, custom fields).
        - Projects linked to the customer (names, statuses — not internal team discussions).
        - Documents uploaded to the customer scope.
        - Invoices issued to the customer.
        - Time entries billed to the customer (summary, not internal notes).
        - Comments on customer-scoped items (only those visible in the portal context, not internal-only comments).
    - Export format: JSON (machine-readable, as recommended by POPIA/GDPR for data portability) + optional CSV summary.
    - The export is generated as a ZIP file, uploaded to S3, and linked to the request via `export_file_key`.
    - The admin can download the export and provide it to the data subject.

3. **Data deletion/anonymization (DELETION requests)**

    - When a DELETION request is approved, the system anonymizes the customer's personal information.
    - **Anonymization, not hard deletion**: Financial records (invoices, time entries) must be retained for legal/tax obligations. Instead of deleting:
        - Customer name → "Anonymized Customer [hash]"
        - Customer email → null
        - Customer custom fields → cleared
        - Documents in customer scope → deleted from S3, Document records removed.
        - Comments by portal contacts for this customer → content replaced with "[Removed]".
    - Records that reference the customer (invoices, time entries, projects) retain their structure but the customer's PII is removed.
    - The customer's lifecycle status is set to `OFFBOARDED` after anonymization.
    - This is a destructive, irreversible operation. The UI must require explicit confirmation (type the customer name to confirm, similar to GitHub repo deletion pattern).

4. **Response deadline configuration**

    - Add `data_request_deadline_days` to OrgSettings (INTEGER, default 30).
    - When a request is created, `deadline` is calculated as `requested_at + data_request_deadline_days`.
    - The compliance dashboard shows requests approaching their deadline (within 7 days) prominently.
    - Notification sent to org admins when a request is within 7 days of its deadline.

### 5. Retention policies

Design the **retention policy** model:

1. **RetentionPolicy entity**

    - `id` (UUID).
    - `tenant_id`.
    - `record_type` (ENUM: `CUSTOMER`, `PROJECT`, `DOCUMENT`, `TIME_ENTRY`, `INVOICE`, `AUDIT_EVENT`, `COMMENT`).
    - `retention_days` (INTEGER — how many days after the trigger event to retain the record).
    - `trigger_event` (ENUM: `CUSTOMER_OFFBOARDED`, `PROJECT_COMPLETED`, `RECORD_CREATED`).
        - `CUSTOMER_OFFBOARDED` — retention clock starts from `customer.offboarded_at`. Used for customer-scoped data (FICA: 5 years = 1825 days).
        - `PROJECT_COMPLETED` — retention clock starts from project completion date. Used for project-scoped data.
        - `RECORD_CREATED` — retention clock starts from record creation. Used for audit events, activity logs.
    - `action` (ENUM: `FLAG`, `ANONYMIZE`).
        - `FLAG` — mark the record as past-retention for admin review. No automatic deletion.
        - `ANONYMIZE` — mark for anonymization (same logic as data subject deletion, applied per-record).
    - `active` (BOOLEAN, default true).
    - `created_at`, `updated_at`.
    - Constraints:
        - `(tenant_id, record_type, trigger_event)` unique — one policy per record type per trigger.

2. **Default retention policies**

    - Seeded during provisioning with sensible defaults (configurable by admin):
        - Customer data after offboarding: 1825 days (5 years), FLAG.
        - Audit events after creation: 2555 days (7 years), FLAG.
        - All others: no default policy (retain indefinitely).
    - Compliance packs can override defaults. E.g., the SA FICA pack could set customer retention to 1825 days (5 years per FICA Section 22).

3. **Retention check workflow**

    - An admin-triggered "Check Retention" action (not automated scheduler for v1).
    - Queries all records matching active retention policies where the trigger date + retention days < today.
    - Returns a list of flagged records grouped by type, with counts and sample data.
    - Admin can review and action flagged records:
        - **Extend**: override the retention period for specific records (with audit trail and reason).
        - **Purge/Anonymize**: execute the configured action on selected records.
    - Each purge/anonymize action is individually audited.

4. **Retention dashboard**

    - Part of the compliance dashboard.
    - Shows: active retention policies, number of records approaching retention (within 30 days), number of records past retention (flagged).
    - Quick actions: run retention check, view flagged records, bulk purge.

### 6. Frontend — compliance UI

Design the frontend components:

1. **Customer lifecycle status**

    - **Customer detail page**: prominent lifecycle status badge (color-coded: grey=Prospect, blue=Onboarding, green=Active, amber=Dormant, red=Offboarded).
    - Lifecycle transition actions in a dropdown or action menu:
        - "Start Onboarding" (Prospect → Onboarding) — triggers checklist instantiation.
        - "Reactivate" (Dormant → Active, Offboarded → Active).
        - "Mark as Dormant" (Active → Dormant).
        - "Offboard Customer" (Active/Dormant → Offboarded) — confirmation dialog with consequences explained.
    - **Customer list page**: lifecycle status column with filter support. Saved views (Phase 11) can filter by status.
    - **Dashboard**: lifecycle status distribution widget (pie/donut chart or status counts).

2. **Onboarding progress tracker**

    - **Customer detail page — Onboarding tab** (visible when status = ONBOARDING or when checklist instances exist):
        - Shows each active checklist instance with progress (e.g., "3 of 5 items complete").
        - Each item shows: name, description, status badge, completed by / completed at (if done), required document upload area (if applicable).
        - Item actions:
            - "Mark Complete" — with optional notes field.
            - "Upload Document" — file picker that uploads to the customer's document scope and links to the checklist item.
            - "Skip" — for optional items, with reason.
        - Dependencies visualized: blocked items are greyed out with a "Waiting for: [dependency item name]" indicator.
        - Completion progress bar at the top of each checklist.

3. **Compliance dashboard** (`/org/[slug]/compliance`)

    - Admin/owner only. Accessible from the sidebar navigation.
    - **Overview section**: counts of customers by lifecycle status, onboarding in progress, pending data requests, records past retention.
    - **Onboarding pipeline**: list of customers currently in ONBOARDING status, sorted by how long they've been onboarding, with checklist progress.
    - **Data requests**: list of open data subject requests, with deadline countdown. Colour-coded: green (>7 days), amber (3-7 days), red (<3 days or overdue).
    - **Retention flags**: count of records past retention, grouped by type. "Run Check" button.
    - **Dormancy check**: "Check for Dormant Customers" button that runs the dormancy detection query.

4. **Data subject request management** (`/org/[slug]/compliance/requests`)

    - **Request list**: table of all requests with status, type, customer, deadline, created date.
    - **Create request**: dialog with customer selector, request type dropdown, description textarea.
    - **Request detail**: shows request details, timeline of status changes, notes field, action buttons:
        - "Start Processing" (RECEIVED → IN_PROGRESS).
        - "Generate Export" (for ACCESS requests) — triggers export, shows download link when ready.
        - "Execute Deletion" (for DELETION requests) — confirmation dialog with destructive action warning.
        - "Complete" (IN_PROGRESS → COMPLETED).
        - "Reject" (with reason field).

5. **Retention policy settings** (`/org/[slug]/settings/compliance`)

    - Table of retention policies: record type, retention period (editable), trigger event, action.
    - Add/edit/delete policies.
    - "Run Retention Check" button with results view.

6. **Checklist template management** (`/org/[slug]/settings/checklists`)

    - Admin/owner only.
    - List of checklist templates with: name, customer type, source (Platform/Custom), active status, auto-instantiate toggle.
    - Create/edit template: name, description, customer type, items list with drag-to-reorder.
    - Each item: name, description, required toggle, requires document toggle, document label, dependency selector.
    - Clone platform template to create org-custom version (same pattern as document templates).

### 7. API endpoints summary

Full endpoint specification:

1. **Customer lifecycle**

    - `POST /api/customers/{id}/transition` — transition lifecycle status. Body: `{ targetStatus, notes? }`. Validates the transition is valid, checks guards (e.g., checklist completion for ONBOARDING → ACTIVE). Returns updated customer.
    - `GET /api/customers/{id}/lifecycle` — get lifecycle history (list of transitions with timestamp, actor, notes).
    - `POST /api/customers/dormancy-check` — run dormancy detection. Returns list of customers that should be flagged as dormant. Does not transition them — returns candidates for admin review.

2. **Checklist templates**

    - `GET /api/checklist-templates` — list active templates. Optional query params: `customerType`. Returns all active templates (platform + org custom).
    - `GET /api/checklist-templates/{id}` — get template with items.
    - `POST /api/checklist-templates` — create a new template (ORG_CUSTOM). Admin/owner only.
    - `PUT /api/checklist-templates/{id}` — update template and items. Admin/owner only. Only ORG_CUSTOM templates.
    - `DELETE /api/checklist-templates/{id}` — soft-delete. Admin/owner only.
    - `POST /api/checklist-templates/{id}/clone` — clone platform template. Admin/owner only.

3. **Checklist instances**

    - `GET /api/customers/{customerId}/checklists` — list checklist instances for a customer.
    - `GET /api/checklist-instances/{id}` — get instance with items, completion status.
    - `POST /api/customers/{customerId}/checklists` — manually instantiate a checklist for a customer. Body: `{ templateId }`. Auto-instantiation happens via the lifecycle transition.
    - `PUT /api/checklist-items/{id}/complete` — mark item as complete. Body: `{ notes?, documentId? }`. Validates dependencies and document requirements.
    - `PUT /api/checklist-items/{id}/skip` — skip an optional item. Body: `{ reason }`.
    - `PUT /api/checklist-items/{id}/reopen` — reopen a completed item (undo completion). Admin only.

4. **Data subject requests**

    - `GET /api/data-requests` — list all requests. Query params: `status`, `customerId`.
    - `GET /api/data-requests/{id}` — get request details.
    - `POST /api/data-requests` — create a new request. Body: `{ customerId, requestType, description }`.
    - `PUT /api/data-requests/{id}/status` — transition request status. Body: `{ status, notes?, rejectionReason? }`.
    - `POST /api/data-requests/{id}/export` — generate data export for an ACCESS request. Returns the export file metadata when ready.
    - `GET /api/data-requests/{id}/export/download` — download the generated export file.
    - `POST /api/data-requests/{id}/execute-deletion` — execute anonymization for a DELETION request. Requires confirmation body: `{ confirmCustomerName }`.

5. **Retention policies**

    - `GET /api/retention-policies` — list all policies for the tenant.
    - `POST /api/retention-policies` — create a policy. Admin/owner only.
    - `PUT /api/retention-policies/{id}` — update a policy. Admin/owner only.
    - `DELETE /api/retention-policies/{id}` — delete a policy. Admin/owner only.
    - `POST /api/retention-policies/check` — run retention check. Returns flagged records grouped by type.
    - `POST /api/retention-policies/purge` — purge/anonymize flagged records. Body: `{ recordType, recordIds[] }`. Admin/owner only. Audited.

For each endpoint specify:
- Auth requirement (valid Clerk JWT, appropriate role).
- Tenant scoping.
- Permission checks.
- Request/response DTOs.

### 8. Notification integration

Publish notifications for:
- **CUSTOMER_STATUS_CHANGED**: Notify org admins when a customer transitions lifecycle status. Include the old and new status.
- **CHECKLIST_COMPLETED**: Notify the admin who started onboarding when all required checklist items are complete.
- **DATA_REQUEST_DEADLINE_APPROACHING**: Notify org admins when a data subject request is within 7 days of its deadline.
- **DATA_REQUEST_OVERDUE**: Notify org admins when a data subject request is past its deadline.
- **RETENTION_RECORDS_FLAGGED**: Notify org admins when a retention check finds records past their retention period.

### 9. Audit integration

Publish audit events for:
- `CUSTOMER_STATUS_CHANGED` — lifecycle transition (old status, new status, actor, notes).
- `CHECKLIST_INSTANTIATED` — checklist created for customer (template name, customer id).
- `CHECKLIST_ITEM_COMPLETED` — individual item completed (item name, completed by, notes, document id if applicable).
- `CHECKLIST_ITEM_SKIPPED` — optional item skipped (item name, reason).
- `CHECKLIST_COMPLETED` — all required items in a checklist instance are complete.
- `DATA_REQUEST_CREATED` — new data subject request logged (type, customer id).
- `DATA_REQUEST_COMPLETED` — request fulfilled (type, action taken).
- `DATA_REQUEST_REJECTED` — request rejected (type, rejection reason).
- `DATA_EXPORT_GENERATED` — data export created for ACCESS request (file size, record counts).
- `DATA_DELETION_EXECUTED` — customer data anonymized (records affected by type).
- `RETENTION_CHECK_EXECUTED` — retention check run (records flagged by type).
- `RETENTION_PURGE_EXECUTED` — records purged/anonymized (record type, count, actor).

### 10. ADRs for key decisions

Add ADR-style sections for:

1. **Lifecycle status as a core entity field vs. custom field**:
    - Why `lifecycle_status` is a column on the Customer table (drives platform behaviour, used in guards and queries) rather than a custom field (which is metadata, not behaviour). The status controls what actions are permitted — it's structural, not informational.
    - Trade-off: adding a column to an existing entity requires a migration. But the alternative (querying a JSONB custom field for every customer mutation) is both slower and semantically wrong.

2. **Checklist engine: first-class entities vs. JSONB on Customer**:
    - Why dedicated entities (`ChecklistTemplate`, `ChecklistInstance`, `ChecklistItem`) rather than storing checklist state as a JSONB blob on the Customer entity.
    - Benefits: queryable (which customers have incomplete checklists?), auditable per-step (who verified identity?), supports templates and instances independently, supports document linking per item.
    - Trade-off: more entities (4 new tables), more complex queries. But the checklist is the core value proposition of this phase — it deserves proper relational modelling.

3. **Anonymization vs. hard deletion for data subject requests**:
    - Why anonymization (replace PII with placeholder values) rather than hard deletion (DELETE FROM).
    - Financial records (invoices, time entries) may have legal retention obligations that conflict with deletion requests. Anonymization satisfies the data protection requirement (PII is removed) while preserving the structural integrity of financial records.
    - This aligns with both POPIA Section 14 (retention for legal obligation) and GDPR Article 17(3)(b) (legal claims).

4. **Compliance packs: bundled seed data vs. marketplace/plugin architecture**:
    - Why compliance packs are classpath resources seeded during provisioning (same as field packs and template packs) rather than a runtime plugin/marketplace system.
    - At this stage, packs are shipped by the platform (or fork) developer. There's no need for runtime installation of compliance requirements — they're baked into the deployment. A marketplace model adds complexity (versioning, compatibility, security review) that isn't justified until the product has many diverse customers.
    - The pack format is designed to be forward-compatible: if a marketplace is built later, the same `pack.json` format can be used.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- The **engine is code, the content is seed data.** This is the foundational design principle. Every line of code in this phase should work identically whether the compliance pack is "SA FICA", "UK AML", or "Generic Onboarding." If you find yourself writing jurisdiction-specific logic in a service class, stop — it belongs in a pack.
- **No SA-specific field names in the schema.** The Customer entity gets `lifecycle_status` (universal). SA-specific fields (ID number, risk rating) are custom fields delivered via the compliance pack's `fieldDefinitions` array.
- **Generic UI labels everywhere.** The compliance dashboard says "Onboarding Checklists," not "FICA Verification." The checklist template name (which comes from the pack) can say "Individual Client Onboarding (FICA)" — but that's data, not code.
- **Checklist items are snapshots, not references.** When a checklist is instantiated, item names/descriptions are copied from the template. If the template is updated later, existing in-progress checklists are not affected. This prevents the nightmare of retroactively changing compliance requirements on active verifications.
- **Data deletion is anonymization.** Never hard-delete customer records. Replace PII with anonymous values, remove documents from S3, clear custom fields. Financial records keep their structure for accounting integrity.
- **Retention is flag-first, purge-second.** The system flags records past retention for admin review. It does not automatically delete anything. Admins make the final call. This is critical for compliance — automated deletion is a liability.
- **One checklist template per customer type is the happy path.** An individual customer gets the "Individual Client Onboarding" checklist; a company customer gets the "Company Client Onboarding" checklist. The system supports multiple simultaneous checklists per customer (for edge cases), but the default UX is one-checklist-per-customer.
- All new entities follow the existing tenant isolation model. No exceptions.
- Frontend additions use the existing Shadcn UI component library and olive design system.
- Keep the lifecycle state machine simple. No nested states, no parallel states, no history states. A flat enum with validated transitions is sufficient. If the state machine needs to become more complex in the future, it can be evolved — but v1 should be dead simple.
- The compliance dashboard is an admin tool, not a member-facing feature. Members see lifecycle status on customer cards and can interact with checklists they have permission for. The full compliance overview is admin-only.

Return a single markdown document as your answer, ready to be added as `architecture/phase13-customer-compliance-lifecycle.md` and ADRs.
