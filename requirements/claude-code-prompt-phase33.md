# Phase 33 — Data Completeness & Prerequisite Enforcement

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 32, the platform has:

- **Customer lifecycle**: PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDING → OFFBOARDED. `CustomerLifecycleGuard` blocks PROSPECT customers from creating projects, invoices, time entries. Transition to ACTIVE is either manual or automatic on checklist completion — but **does not validate custom field completeness**.
- **Custom fields & field packs**: `FieldDefinition` with entity type scoping (PROJECT, TASK, CUSTOMER, INVOICE), required flag, visibility conditions. `FieldGroup` collections with `autoApply` flag. Field packs seeded from JSON (FICA, tax info, etc.). Custom field values stored as JSONB on entities.
- **Customer creation dialog**: Collects only base fields (name, type, email, phone, ID number, notes). Custom fields are **not collected at creation** — only editable later on the detail page after field groups are applied.
- **Setup guidance (Phase 15)**: `CustomerReadinessService` computes readiness (required fields filled, checklist progress, linked projects). `SetupProgressCard`, `ActionCard`, `TemplateReadinessCard` components display readiness **informally** — no enforcement.
- **Project templates (Phase 16)**: `ProjectTemplate` with task/document template snapshots. Used by proposals and recurring schedules. Currently declares task structure and team — but **no customer field requirements**.
- **Proposal → engagement pipeline (Phase 32)**: On proposal acceptance, auto-creates project from template, sets up billing, assigns team, triggers onboarding checklist. Currently does **not check** whether the customer has all required fields for the engagement type.
- **Invoicing, document generation, proposal sending**: All proceed without validating that prerequisite customer/project data is complete.
- **Integration ports (Phase 21)**: BYOAK infrastructure for future accounting sync. Incomplete customer data synced externally would be problematic.

**Gap**: The platform has rich metadata capabilities (field packs, required fields, readiness computation) but **never enforces data completeness**. Customer creation collects minimal data regardless of customer type. Lifecycle transitions don't validate field completeness. Action points (invoice generation, proposal sending, document generation) proceed with incomplete data. The result: users discover missing information at output time rather than intake time, and ACTIVE status doesn't guarantee the customer record is actually complete.

## Objective

Build a **Data Completeness & Prerequisite Enforcement** system that:

1. **Smart customer intake**: The creation dialog becomes type-aware — selecting a customer type surfaces auto-apply field groups with their required fields inline, collecting critical data at the door rather than after the fact.
2. **Lifecycle transition gates**: ONBOARDING → ACTIVE requires all base required custom fields to be filled. The `CustomerReadinessService` becomes an enforcing gate, not just an informational display.
3. **Engagement-level prerequisites**: Project templates declare required customer fields. When a project is created (or a proposal acceptance triggers project instantiation), the system checks whether the linked customer has the required data and surfaces gaps before proceeding.
4. **Action-point prerequisite checks**: Before generating invoices, sending proposals, or generating documents, the system validates that all required data sources are populated and blocks with an inline remediation modal if not.
5. **Prerequisite configuration**: Admins can configure which fields are required for which contexts (lifecycle activation, invoice generation, proposal sending, etc.). Field packs ship with sensible defaults; orgs can override.
6. **Completeness visibility**: Customer list shows completeness indicators, enhanced readiness cards show field-level breakdowns, and dashboard widgets surface customers with incomplete profiles.

This phase turns DocTeams' metadata system from "data capture" into "data quality assurance" — the difference between a database and a workflow tool.

## Constraints & Assumptions

- **No new domain entities** for the core prerequisite system. This is primarily about wiring existing infrastructure (field definitions, readiness service, lifecycle guards) into enforcement points.
- **`PrerequisiteContext` enum** (not free-form strings) defines where fields can be required: `LIFECYCLE_ACTIVATION`, `INVOICE_GENERATION`, `PROPOSAL_SEND`, `DOCUMENT_GENERATION`, `PROJECT_CREATION`. Extensible but controlled.
- **Field packs ship with default prerequisite contexts**. For example, FICA pack fields default to `LIFECYCLE_ACTIVATION`. Tax reference fields default to `INVOICE_GENERATION`. Orgs can override these defaults.
- **Prerequisite checks are soft-blocking, not hard-blocking**: The system shows a modal with the missing fields and lets the user fill them inline. It does NOT silently proceed, but it also doesn't require navigating away. Fill the gaps → continue the action.
- **Project template → customer field requirements** is a new association (join table or JSONB list on ProjectTemplate). This is the mechanism for engagement-level prerequisites.
- **Customer stays ACTIVE** when a new engagement surfaces additional field requirements. The customer doesn't regress through lifecycle states — the engagement (project/proposal) surfaces its own prerequisites independently.
- **Existing readiness computation is extended**, not replaced. `CustomerReadinessService` gains a `context` parameter so it can check readiness for a specific action (lifecycle activation vs. invoice generation vs. specific project template).
- **Backend validation is authoritative**. Frontend shows prerequisite checks for UX, but the backend enforces them. API calls without prerequisite satisfaction return 422 with structured error payloads listing missing fields.

---

## Section 1 — Smart Customer Intake Dialog

### Current State

`CreateCustomerDialog` collects: name, customerType (INDIVIDUAL/COMPANY/TRUST), email, phone, idNumber, notes. No custom fields. No field group awareness.

### Target State

The creation dialog becomes a two-step flow:

**Step 1 — Base Fields** (unchanged):
- Name (required), customer type (required), email (required), phone, notes

**Step 2 — Type-Aware Custom Fields** (new):
- On customer type selection, fetch auto-apply field groups for entity type CUSTOMER
- Render required fields from those groups inline in the dialog
- Optional fields shown in a collapsible "Additional Information" section
- Each field renders according to its `fieldType` (TEXT, NUMBER, DATE, SELECT, BOOLEAN, etc.)
- Conditional visibility rules (`visibilityCondition`) apply as normal
- "Skip for now" action collapses optional fields — but required fields must be filled or explicitly deferred

**On submit**:
- Creates customer with base fields AND custom field values in a single API call
- Auto-applies the relevant field groups (already happens via `autoApply`, but now with pre-filled values)
- Customer lands on detail page with accurate completeness score

### Backend Changes

- `CustomerController.create()` accepts optional `customFields: Map<String, Object>` in the request body
- `CustomerService.createCustomer()` validates custom field values against field definitions (type checking, required validation)
- New endpoint: `GET /api/field-definitions/intake?entityType=CUSTOMER` — returns auto-apply field groups with their field definitions, ordered for the intake form

### Frontend Changes

- `CreateCustomerDialog` becomes a multi-step dialog (or an expandable single-step with progressive disclosure)
- New `IntakeFieldsSection` component: renders field definitions as form controls, handles conditional visibility
- Reuses existing `CustomFieldRenderer` patterns from the custom fields UI (Phase 23)

---

## Section 2 — Lifecycle Transition Gates

### Current State

`CustomerLifecycleGuard` blocks certain actions for PROSPECT customers. Lifecycle transitions are validated for legal state transitions (e.g., can't go from PROSPECT directly to ACTIVE) but **not** for data completeness.

`CustomerReadinessService` computes:
- `requiredFields: {total, filled}` — checks required FieldDefinitions vs customer's customFields
- `checklistProgress: {total, completed}` — from ChecklistInstance
- `hasLinkedProjects: boolean`
- `overallReadiness: String`

This data is displayed in `SetupProgressCard` on the customer detail page but never enforced.

### Target State

Lifecycle transitions gain a **prerequisite check layer**:

- **ONBOARDING → ACTIVE**: Requires ALL custom fields marked as `requiredFor: LIFECYCLE_ACTIVATION` to be filled. This is the "base completeness" gate. If a firm uses the FICA field pack, the client's ID number, proof of address, etc. must be captured before activation.
- **PROSPECT → ONBOARDING**: No additional field requirements (lightweight — just intent to engage).
- **Other transitions** (ACTIVE → DORMANT, etc.): No field requirements (these are operational status changes, not data quality gates).

### Backend Changes

- `CustomerLifecycleService` (or equivalent transition handler) calls `CustomerReadinessService.checkPrerequisites(customerId, PrerequisiteContext.LIFECYCLE_ACTIVATION)` before executing ONBOARDING → ACTIVE transition
- Returns 422 with structured payload if prerequisites not met:
  ```json
  {
    "error": "PREREQUISITES_NOT_MET",
    "context": "LIFECYCLE_ACTIVATION",
    "missingFields": [
      {"fieldDefinitionId": "uuid", "name": "ID Number", "slug": "fica_id_number", "groupName": "FICA Requirements"}
    ],
    "message": "3 required fields must be completed before activating this customer"
  }
  ```
- Checklist completion auto-transition (existing) also checks field prerequisites — if checklists complete but fields aren't filled, the auto-transition is blocked and a notification is sent instead

### Frontend Changes

- Lifecycle transition buttons (e.g., "Activate Customer") call a prerequisite check endpoint first
- If prerequisites not met → `PrerequisiteModal` opens showing missing fields with inline editors
- User fills fields in the modal → resubmits transition → succeeds
- `SetupProgressCard` updated to show prerequisite context (which fields are blocking activation)

---

## Section 3 — Engagement-Level Prerequisites

### Concept

A customer is ACTIVE (base fields complete), but a new service engagement may require **additional** customer data. For example:
- Customer originally onboarded for **bookkeeping** — base FICA fields captured
- Now needs **annual tax return** — requires SARS tax reference number, tax year end date
- The **project template** for "Annual Tax Return" declares these as required customer fields

The customer doesn't go back through onboarding. The project creation (or proposal acceptance) surfaces the engagement-specific gaps.

### Data Model

New association: **ProjectTemplate → required customer FieldDefinitions**

```
project_templates table extension:
  required_customer_field_ids   UUID[] (JSONB array) — references field_definitions.id
```

Or a join table if preferred:
```
project_template_required_fields:
  project_template_id   UUID (FK to project_templates)
  field_definition_id   UUID (FK to field_definitions)
```

When a project template is configured, the admin can select which customer fields must be filled for this engagement type.

### Enforcement Points

1. **Manual project creation** with a linked customer + project template: Check customer's custom fields against template's required customer fields. If gaps → prerequisite modal.
2. **Proposal acceptance → project instantiation**: The proposal references a project template. On acceptance orchestration, check customer fields before creating the project. If gaps → the acceptance still succeeds (client-facing action shouldn't silently fail), but the project is created in a "SETUP_REQUIRED" state and the firm is notified of missing fields.
3. **Schedule-triggered project creation** (recurring schedules): Log a warning notification if customer fields are incomplete — don't block automated project creation, but alert the team.

### Backend Changes

- `ProjectTemplateService` gains `getRequiredCustomerFields(templateId)` method
- `PrerequisiteService.checkEngagementPrerequisites(customerId, templateId)` — checks customer's fields against template requirements
- `ProjectService.createFromTemplate()` calls prerequisite check; returns 422 if blocking (manual creation) or logs notification if non-blocking (automated)
- Project template API: `PUT /api/project-templates/{id}/required-customer-fields` to configure the association

### Frontend Changes

- Project template editor: new "Required Customer Fields" section — multi-select from available customer field definitions
- Project creation dialog: when a template is selected and a customer is linked, runs prerequisite check → shows `PrerequisiteModal` if gaps
- Proposal creation: when template is selected, shows a note if the linked customer is missing fields

---

## Section 4 — Action-Point Prerequisite Checks

### Concept

Before executing key actions, the system validates that all required data is present. This is the last line of defense — catches anything that lifecycle gates and engagement prerequisites didn't cover.

### Actions & Their Prerequisites

| Action | Prerequisite Check |
|--------|-------------------|
| **Generate invoice** | Customer has billing address; customer has VAT/tax number (if tax rates configured); project has at least one billable time entry or expense |
| **Send proposal** | Customer has a portal contact with email; proposal has fee configuration; proposal body is not empty |
| **Generate document** | All template variables have data sources (customer fields, project fields, etc.) that are populated; linked customer/project exists |
| **Send invoice** | Invoice has line items; customer has billing address; customer has portal contact (for portal delivery) or email (for email delivery) |
| **Create acceptance request** | Document is generated (PDF exists); customer has portal contact |

### Backend Changes

- New `PrerequisiteService` (central service, not per-domain):
  ```java
  public record PrerequisiteCheck(
      boolean passed,
      List<PrerequisiteViolation> violations
  ) {}

  public record PrerequisiteViolation(
      String code,           // e.g., "MISSING_BILLING_ADDRESS"
      String message,        // Human-readable
      String entityType,     // "CUSTOMER", "PROJECT", "INVOICE"
      UUID entityId,
      String fieldSlug,      // nullable — for custom field violations
      String resolution      // e.g., "Add a billing address to the customer profile"
  ) {}
  ```
- Each action's service calls `prerequisiteService.check(context, entityIds)` before executing
- Returns 422 with `PrerequisiteCheck` payload if violations exist
- Configurable: field definitions have `requiredForContexts: List<PrerequisiteContext>` (JSONB array on `field_definitions` table)

### Frontend Changes

- `usePrerequisiteCheck(context, entityIds)` hook — calls prerequisite endpoint, returns violations
- `PrerequisiteModal` component (shared across all action points):
  - Shows list of violations grouped by entity
  - For custom field violations: inline field editor to fill the value immediately
  - For structural violations (e.g., "no portal contact"): link to the relevant page
  - "Check Again" button after filling → re-validates → closes and proceeds if passed
- All "Generate Invoice", "Send Proposal", "Generate Document" buttons integrate the prerequisite check

---

## Section 5 — Prerequisite Configuration

### Field Definition Extension

Extend `FieldDefinition` entity:

```
field_definitions table extension:
  required_for_contexts   TEXT[] or JSONB — list of PrerequisiteContext values
```

`PrerequisiteContext` enum values:
- `LIFECYCLE_ACTIVATION` — required for ONBOARDING → ACTIVE transition
- `INVOICE_GENERATION` — required before generating an invoice for this customer
- `PROPOSAL_SEND` — required before sending a proposal to this customer
- `DOCUMENT_GENERATION` — required before generating a document for this customer/project
- `PROJECT_CREATION` — required before creating a project for this customer (general, not template-specific)

### Field Pack Defaults

Seeded field packs include default `requiredForContexts`:

| Pack | Field | Default Contexts |
|------|-------|-----------------|
| FICA | ID Number | `LIFECYCLE_ACTIVATION` |
| FICA | Proof of Address | `LIFECYCLE_ACTIVATION` |
| Tax | SARS Tax Reference | `INVOICE_GENERATION` |
| Tax | Tax Year End | — (optional) |
| Billing | Billing Address | `INVOICE_GENERATION`, `PROPOSAL_SEND` |
| Billing | VAT Number | `INVOICE_GENERATION` |

### Admin Configuration UI

- Field definition editor (Settings → Custom Fields): new "Required For" multi-select dropdown per field
- Options correspond to `PrerequisiteContext` values with human-readable labels
- Org-level overrides: if an org doesn't need VAT number for invoices, they remove `INVOICE_GENERATION` from that field's contexts
- Project template editor: separate UI for template-specific customer field requirements (Section 3)

### Backend Changes

- `FieldDefinition` entity gains `requiredForContexts` field (JSONB array of strings)
- `FieldDefinitionService` provides `getRequiredFieldsForContext(entityType, context)` — returns all field definitions required for a given context
- `PrerequisiteService` uses this to dynamically determine what to check
- Migration: adds column, seeds defaults for existing field packs
- API: `PATCH /api/field-definitions/{id}` already exists — extend to accept `requiredForContexts`

---

## Section 6 — Completeness Visibility

### Customer List Enhancements

- New column or badge: **completeness indicator** — shows percentage of required fields filled (across all contexts, not just lifecycle)
- Color coding: green (100%), amber (50-99%), red (<50%)
- Filterable: "Show customers with incomplete profiles"
- Sortable by completeness percentage

### Customer Detail Page Enhancements

- `SetupProgressCard` enhanced:
  - Grouped by context: "For Activation: 3/5 fields", "For Invoicing: 1/2 fields"
  - Each group expandable to show specific missing fields
  - Click a missing field → scrolls to or opens editor for that field
- Completeness ring/badge in the customer header area

### Dashboard Widget

- "Incomplete Customer Profiles" card on the company dashboard
- Shows count of customers with <100% completeness, grouped by what's missing
- "5 customers missing billing address" → click → filtered customer list
- Only shown to admin/owner roles

### Notification (Optional)

- Configurable: "Notify when customer has been in ONBOARDING for N days with incomplete required fields"
- Uses existing notification infrastructure
- Default: 7 days, configurable in org settings

---

## Section 7 — Reusable Prerequisite Modal Component

### `PrerequisiteModal` — The Inline Remediation Experience

This is the key UX component that makes prerequisite enforcement feel helpful rather than hostile.

**Behavior**:
1. User clicks "Generate Invoice" (or any gated action)
2. Frontend calls prerequisite check endpoint
3. If violations exist → `PrerequisiteModal` opens (instead of the action proceeding)
4. Modal shows: "Before generating this invoice, please complete the following:"
5. Lists violations grouped by entity (Customer, Project)
6. For custom field violations: inline editor right in the modal (text input, date picker, select, etc.)
7. For structural violations: "Add a portal contact" with a link/button
8. User fills fields → clicks "Check & Continue"
9. Modal re-validates → if passed, closes and executes the original action
10. If still failing → shows remaining violations

**Design Principles**:
- Never a dead end — always show how to resolve
- Inline editing for custom fields (don't navigate away)
- Links for structural issues (portal contact, project setup)
- Batch save — all field updates saved in one call
- Remember the original action — after prerequisites are met, execute it automatically

### Component API

```typescript
<PrerequisiteModal
  context="INVOICE_GENERATION"
  entityType="CUSTOMER"
  entityId={customerId}
  onResolved={() => proceedWithInvoiceGeneration()}
  onCancel={() => {}}
/>
```

Reused across: invoice generation, proposal sending, document generation, lifecycle transitions, project creation.

---

## Out of Scope

- **Workflow automation** (trigger → action rules) — that's Phase 37
- **Custom validation rules** beyond required/optional (e.g., "field X must be > 0", regex patterns) — future enhancement
- **Cross-entity prerequisite graphs** (e.g., "project requires customer AND project member AND budget") — keep it simple: customer field completeness per context
- **Prerequisite history/audit** — don't log every prerequisite check, only the enforcement outcomes (422 responses are already auditable)
- **Bulk prerequisite remediation** — no "fix all customers" batch UI. Individual customer fixes only.
- **Field validation beyond type checking** — field types already have basic validation (number, date, etc.). No custom regex or business rule validation in this phase.

## ADR Topics

1. **Prerequisite enforcement strategy**: Soft-blocking (modal with inline fix) vs. hard-blocking (navigate to entity, fix, come back). Decision: soft-blocking for UX flow.
2. **Prerequisite context granularity**: Per-action enum vs. free-form tags vs. hierarchical contexts. Decision: enum for type safety and discoverability.
3. **Engagement prerequisite storage**: JSONB array on ProjectTemplate vs. join table. Trade-offs: query performance, migration complexity, schema flexibility.
4. **Auto-transition behavior when fields incomplete**: Block and notify vs. transition anyway with warning. Decision: block auto-transition, send notification to prompt field completion.

## Style & Boundaries

- All prerequisite validation is server-authoritative — frontend checks are UX convenience, backend enforces
- 422 status code for prerequisite failures with structured `PrerequisiteCheck` response body
- Prerequisite checks are fast (single query per context, not N+1) — denormalize if needed
- `PrerequisiteModal` is a shared component — every action point uses the same UX pattern
- Field rendering in the modal reuses existing `CustomFieldRenderer` / `IntakeFieldsSection` — no duplicate form implementations
- Migration adds `required_for_contexts` column to `field_definitions`, seeds defaults for existing packs
- Existing tests for lifecycle transitions, invoice generation, etc. must be updated to pass prerequisite checks (test factories create customers with required fields pre-filled)
