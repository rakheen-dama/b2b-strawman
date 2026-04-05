# Phase 63 — Custom Field Graduation: Promoting Structural Fields Across All Entities

## System Context

Phase 11 introduced a custom fields system (EAV-style JSONB on entities) with field packs seeded per vertical profile. Over 50+ phases of domain building, many "custom" fields have proven to be structurally necessary — queried in service logic (conflict checks, deadline calculations), required for core flows (invoicing, proposals), rendered in every template, and present for every tenant in every vertical. They are "custom" in name only.

**The existing infrastructure**:

- **FieldDefinition / FieldGroup / FieldGroupMember** entities define what fields exist, how they're grouped, and their validation rules
- **Entity JSONB columns**: `custom_fields` (Map<String, Object>) and `applied_field_groups` (List<UUID>) on Customer, Project, Task, Invoice
- **Field packs**: JSON files in `src/main/resources/field-packs/` seeded by `FieldPackSeeder` during tenant provisioning
- **PrerequisiteService**: Enforces required-for-context rules (e.g., `address_line1` required for INVOICE_GENERATION) by reading from JSONB
- **Template context builders**: Expose custom fields as `${entity.customFields.slug}` in Thymeleaf templates
- **CustomFieldSection** (frontend): Renders all custom fields grouped by FieldGroup, handles visibility conditions

**What already exists as structural columns** (do NOT re-add):
- Customer: `name`, `email`, `phone`, `id_number`, `customer_type` (INDIVIDUAL/COMPANY/TRUST), `lifecycle_status`, `notes`
- Project: `name`, `description`, `status`, `customer_id`, `due_date`
- Task: `title`, `description`, `status`, `priority` (already an enum), `type`, `assignee_id`, `due_date`
- Invoice: `invoice_number`, `status`, `currency`, `issue_date`, `due_date`, `subtotal`, `tax_amount`, `total`, `notes`, `payment_terms`, `payment_reference`, `customer_name`, `customer_email`, `customer_address`

**The problem**: Fields like `tax_number`, `contact_name`, `entity_type`, `financial_year_end`, `work_type`, `estimated_hours`, and address components live in JSONB. This means:
- No database indexes — conflict check joins to JSONB, deadline calculation extracts from JSONB
- No type safety — `financial_year_end` stored as string, `estimated_hours` stored as string
- Awkward template access — `${customer.customFields.tax_number}` instead of `${customer.taxNumber}`
- Prerequisite checks query JSONB instead of simple null checks on typed columns
- Frontend renders these in the generic CustomFieldSection instead of purpose-built form fields

## Objective

Promote ~21 universal/structural custom fields to proper entity columns across Customer, Project, Task, and Invoice. Remove the corresponding field definitions from pack JSON files. Update services, controllers, templates, and frontend forms to use the structural columns directly.

**Not in scope**: Migrating existing data from JSONB to new columns. Old data in `custom_fields` JSONB stays as-is. New writes go to structural columns. The custom fields system remains for genuinely custom, tenant-defined, or vertical-specific fields.

## Constraints & Assumptions

- **No data backfill** — All new columns are nullable. Existing entities keep their JSONB data. New entity creation and edits write to structural columns. No migration script copies JSONB values to columns.
- **Pack cleanup is removal, not modification** — Promoted fields are deleted from pack JSON files entirely. The `FieldPackSeeder` will simply not create these FieldDefinitions for new tenants. Existing tenants retain their old FieldDefinitions and FieldValues (orphaned but harmless).
- **Custom fields system stays** — `FieldDefinition`, `FieldGroup`, `FieldPackSeeder`, `CustomFieldSection` all remain. They just have fewer fields to manage after this phase.
- **`customer_type` stays as-is** — The existing `CustomerType` enum (INDIVIDUAL, COMPANY, TRUST) is a broad classification. The new `entity_type` column is the granular legal entity form (PTY_LTD, CC, SOLE_PROPRIETOR, NPC, PARTNERSHIP, ESTATE, GOVERNMENT, etc.). These are complementary, not duplicative.
- **Task.priority already exists** — The common-task pack's `priority` field is already a structural column. This phase just removes it from the pack JSON.
- **Invoice.payment_reference already exists** — Same as above.
- **Migration number**: Use whatever comes after the latest applied migration at the time of implementation (V85 is current; Phases 60/61 may add V86+).

---

## Section 1 — Data Model Changes

### 1.1 Customer Entity — 13 New Columns

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `registration_number` | `VARCHAR(100)` | Yes | Company/trust registration. Conflict check matching. |
| `address_line1` | `VARCHAR(255)` | Yes | |
| `address_line2` | `VARCHAR(255)` | Yes | |
| `city` | `VARCHAR(100)` | Yes | |
| `state_province` | `VARCHAR(100)` | Yes | |
| `postal_code` | `VARCHAR(20)` | Yes | |
| `country` | `VARCHAR(2)` | Yes | ISO 3166-1 alpha-2 (ZA, US, GB, etc.) |
| `tax_number` | `VARCHAR(100)` | Yes | Universal tax ID (VAT, EIN, GST, etc.). Replaces both common pack `tax_number` and accounting-za `vat_number`. |
| `contact_name` | `VARCHAR(255)` | Yes | Primary contact for this customer. |
| `contact_email` | `VARCHAR(255)` | Yes | Primary contact email. |
| `contact_phone` | `VARCHAR(50)` | Yes | Primary contact phone. |
| `entity_type` | `VARCHAR(30)` | Yes | Granular legal entity form: PTY_LTD, SOLE_PROPRIETOR, CC, TRUST, PARTNERSHIP, NPC, ESTATE, GOVERNMENT, etc. VARCHAR because values differ per vertical. Validated in service layer. |
| `financial_year_end` | `DATE` | Yes | Used by DeadlineCalculationService. Accounting-originated but useful cross-vertical. |

**Indexes**:
- `idx_customers_registration_number` on `registration_number` (conflict check)
- `idx_customers_tax_number` on `tax_number`
- `idx_customers_entity_type` on `entity_type`

### 1.2 Project Entity — 3 New Columns

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `reference_number` | `VARCHAR(100)` | Yes | External reference (PO number, matter number, etc.) |
| `priority` | `VARCHAR(20)` | Yes | Enum: LOW, MEDIUM, HIGH. Not NOT NULL — existing projects have no priority. |
| `work_type` | `VARCHAR(50)` | Yes | Unifies accounting `engagement_type` and legal `matter_type`. VARCHAR because values differ per vertical. |

**Index**:
- `idx_projects_work_type` on `work_type` (filtering, reporting)

### 1.3 Task Entity — 1 New Column

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `estimated_hours` | `DECIMAL(8,2)` | Yes | Feeds into budget/profitability. Was custom field with min:0 validation. |

Task already has structural `priority` — no change needed there.

### 1.4 Invoice Entity — 4 New Columns

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `po_number` | `VARCHAR(100)` | Yes | Customer's purchase order number. Standard B2B invoicing field. |
| `tax_type` | `VARCHAR(20)` | Yes | Enum: VAT, GST, SALES_TAX, NONE. Drives tax calculation display. |
| `billing_period_start` | `DATE` | Yes | For recurring/retainer invoices. |
| `billing_period_end` | `DATE` | Yes | For recurring/retainer invoices. |

Invoice already has structural `payment_reference` — no change needed there.

### 1.5 Migration

Single migration adding all columns:

```sql
-- Customer
ALTER TABLE customers ADD COLUMN registration_number VARCHAR(100);
ALTER TABLE customers ADD COLUMN address_line1 VARCHAR(255);
ALTER TABLE customers ADD COLUMN address_line2 VARCHAR(255);
ALTER TABLE customers ADD COLUMN city VARCHAR(100);
ALTER TABLE customers ADD COLUMN state_province VARCHAR(100);
ALTER TABLE customers ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE customers ADD COLUMN country VARCHAR(2);
ALTER TABLE customers ADD COLUMN tax_number VARCHAR(100);
ALTER TABLE customers ADD COLUMN contact_name VARCHAR(255);
ALTER TABLE customers ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE customers ADD COLUMN contact_phone VARCHAR(50);
ALTER TABLE customers ADD COLUMN entity_type VARCHAR(30);
ALTER TABLE customers ADD COLUMN financial_year_end DATE;

CREATE INDEX idx_customers_registration_number ON customers(registration_number);
CREATE INDEX idx_customers_tax_number ON customers(tax_number);
CREATE INDEX idx_customers_entity_type ON customers(entity_type);

-- Project
ALTER TABLE projects ADD COLUMN reference_number VARCHAR(100);
ALTER TABLE projects ADD COLUMN priority VARCHAR(20);
ALTER TABLE projects ADD COLUMN work_type VARCHAR(50);

CREATE INDEX idx_projects_work_type ON projects(work_type);

-- Task
ALTER TABLE tasks ADD COLUMN estimated_hours DECIMAL(8,2);

-- Invoice
ALTER TABLE invoices ADD COLUMN po_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN tax_type VARCHAR(20);
ALTER TABLE invoices ADD COLUMN billing_period_start DATE;
ALTER TABLE invoices ADD COLUMN billing_period_end DATE;
```

---

## Section 2 — Backend Entity & DTO Updates

### 2.1 Customer Entity

Add all 13 fields to `Customer.java` with `@Column` annotations. All nullable. Add getters/setters.

Update `CustomerRequest` / `CustomerResponse` DTOs:
- `CustomerRequest`: Add all 13 fields as optional parameters.
- `CustomerResponse`: Add all 13 fields. Continue to include `customFields` map for remaining genuinely custom fields.

### 2.2 Project Entity

Add `referenceNumber`, `priority`, `workType` fields to `Project.java`.

Create `ProjectPriority` enum (LOW, MEDIUM, HIGH) for type safety. Use `@Enumerated(EnumType.STRING)`.

Update `ProjectRequest` / `ProjectResponse` DTOs accordingly.

### 2.3 Task Entity

Add `estimatedHours` (BigDecimal) to `Task.java`. Validation: `@DecimalMin("0")`.

Update `TaskRequest` / `TaskResponse` DTOs.

### 2.4 Invoice Entity

Add `poNumber`, `taxType`, `billingPeriodStart`, `billingPeriodEnd` to `Invoice.java`.

Create `TaxType` enum (VAT, GST, SALES_TAX, NONE). Use `@Enumerated(EnumType.STRING)`.

Update `InvoiceRequest` / `InvoiceResponse` DTOs.

---

## Section 3 — Service Layer Updates

### 3.1 ConflictCheckService

Currently reads `registration_number` from JSONB for conflict matching. Update to use `customer.getRegistrationNumber()` directly. This enables a proper `WHERE registration_number = ?` query instead of JSONB extraction.

### 3.2 DeadlineCalculationService

Currently reads `financial_year_end`, `engagement_type`, and `tax_year` from JSONB. Update:
- `customer.getFinancialYearEnd()` — now a proper `LocalDate`, no string parsing needed
- `project.getWorkType()` — replaces `engagement_type` JSONB lookup
- `tax_year` stays in JSONB (accounting-specific, not promoted)

### 3.3 PrerequisiteService

Currently checks JSONB for required fields before actions (invoice generation, proposal send). Update to check structural columns:
- Invoice generation: check `customer.getAddressLine1()`, `customer.getCity()`, `customer.getCountry()`, `customer.getTaxNumber()` directly instead of JSONB extraction
- Proposal send: check `customer.getContactName()`, `customer.getContactEmail()` directly

The prerequisite framework should gain awareness of structural field requirements alongside custom field requirements. Add a `StructuralPrerequisite` concept: a list of entity getter checks (field name + null check) that run alongside `requiredForContexts` custom field checks.

### 3.4 CustomerService / ProjectService / TaskService / InvoiceService

Update create and update methods to accept and persist the new structural fields. Standard setter calls — no special logic.

### 3.5 CustomFieldFilterUtil

Currently filters list views by JSONB custom field values. For promoted fields, update to use proper column-based WHERE clauses. This is a significant query performance improvement for filtering by city, country, entity_type, work_type, etc.

---

## Section 4 — Template Context Updates

### 4.1 CustomerContextBuilder

Expose promoted fields as direct template variables:
- `${customer.taxNumber}` (in addition to keeping `${customer.customFields.tax_number}` as an alias for backward compatibility with existing templates)
- `${customer.addressLine1}`, `${customer.city}`, `${customer.country}`, etc.
- `${customer.contactName}`, `${customer.contactEmail}`, `${customer.contactPhone}`
- `${customer.entityType}`, `${customer.financialYearEnd}`
- `${customer.registrationNumber}`

### 4.2 ProjectContextBuilder

- `${project.referenceNumber}`, `${project.priority}`, `${project.workType}`

### 4.3 InvoiceContextBuilder

- `${invoice.poNumber}`, `${invoice.taxType}`, `${invoice.billingPeriodStart}`, `${invoice.billingPeriodEnd}`

### 4.4 VariableMetadataRegistry

Update the variable metadata registry to list promoted fields in the "Customer Fields" / "Project Fields" / etc. groups rather than the "Custom Fields" group. Update the template editor variable picker accordingly.

---

## Section 5 — Frontend Form Updates

### 5.1 Customer Form

Move promoted fields OUT of `CustomFieldSection` and INTO the main customer create/edit form as proper typed inputs:

- **Address section**: `addressLine1` (text), `addressLine2` (text), `city` (text), `stateProvince` (text), `postalCode` (text), `country` (select — ISO 3166-1 alpha-2 dropdown)
- **Contact section**: `contactName` (text), `contactEmail` (email input), `contactPhone` (tel input)
- **Business details section**: `registrationNumber` (text), `taxNumber` (text), `entityType` (select — values from vertical profile or all values combined), `financialYearEnd` (date picker)

The `CustomFieldSection` continues to render for remaining genuinely custom fields (SARS reference, FICA status, trust details, referred_by, etc.).

### 5.2 Project Form

Add to main project form:
- `referenceNumber` (text input)
- `priority` (select: Low, Medium, High)
- `workType` (select — values context-dependent on vertical profile)

### 5.3 Task Form

Add to main task form:
- `estimatedHours` (number input, min 0, step 0.25)

Remove `priority` from common-task pack (already structural on Task entity). The CustomFieldSection for tasks will no longer show priority.

### 5.4 Invoice Form

Add to invoice create/edit form:
- `poNumber` (text input)
- `taxType` (select: VAT, GST, Sales Tax, None)
- `billingPeriodStart` (date picker)
- `billingPeriodEnd` (date picker)

Remove `payment_reference` from common-invoice pack (already structural on Invoice entity).

### 5.5 Customer Detail / Project Detail Pages

Update detail pages to show promoted fields in their proper sections rather than in the custom fields panel. Address fields render as formatted address block. Contact fields render in a contact card.

---

## Section 6 — Pack JSON Cleanup

### 6.1 Fields to REMOVE from pack files

**common-customer.json** — Remove entire "Contact & Address" group:
- `address_line1`, `address_line2`, `city`, `state_province`, `postal_code`, `country`, `tax_number`, `phone`

**accounting-za-customer.json** — Remove promoted fields:
- `acct_company_registration_number` (→ `registration_number` column)
- `vat_number` (→ `tax_number` column)
- `acct_entity_type` (→ `entity_type` column)
- `financial_year_end` (→ `financial_year_end` column)
- `primary_contact_name` (→ `contact_name` column)
- `primary_contact_email` (→ `contact_email` column)
- `primary_contact_phone` (→ `contact_phone` column)
- `registered_address` (→ `address_line1` column)
- `postal_address` — keep as custom (secondary address, not universal)

**legal-za-customer.json** — Remove promoted fields:
- `client_type` (→ `entity_type` column)
- `id_passport_number` — already structural as `id_number`
- `registration_number` (→ `registration_number` column)
- `physical_address` (→ `address_line1` column)
- `postal_address` — keep as custom

**common-project.json** — Remove promoted fields:
- `reference_number` (→ `reference_number` column)
- `priority` (→ `priority` column)
- Keep `category` as custom

**accounting-za-project.json** — Remove promoted field:
- `engagement_type` (→ `work_type` column)
- Keep `tax_year`, `sars_submission_deadline`, `assigned_reviewer`, `complexity` as custom

**legal-za-project.json** — Remove promoted field:
- `matter_type` (→ `work_type` column)
- Keep `case_number`, `court_name`, `opposing_party`, `opposing_attorney`, `advocate_name`, `date_of_instruction`, `estimated_value` as custom

**common-task.json** — Remove promoted fields:
- `priority` (already structural on Task)
- `estimated_hours` (→ `estimated_hours` column)
- Keep `category` as custom

**common-invoice.json** — Remove promoted fields:
- `purchase_order_number` (→ `po_number` column)
- `payment_reference` (already structural on Invoice)
- `tax_type` (→ `tax_type` column)
- `billing_period_start` (→ `billing_period_start` column)
- `billing_period_end` (→ `billing_period_end` column)

After cleanup, `common-invoice.json` will be **empty** — delete the file and remove the pack reference.

### 6.2 Fields that STAY custom

**Customer**: `sars_tax_reference`, `sars_efiling_profile`, `financial_year_end` (wait — this is promoted), `industry_sic_code`, `trading_as`, `fica_verified`, `fica_verification_date`, `referred_by`, `preferred_correspondence`, `postal_address`, all trust-specific fields (`trust_registration_number`, `trust_deed_date`, `trust_type`, `trustee_names`, `trustee_type`, `letters_of_authority_date`)

**Project**: `tax_year`, `sars_submission_deadline`, `assigned_reviewer`, `complexity`, `case_number`, `court_name`, `opposing_party`, `opposing_attorney`, `advocate_name`, `date_of_instruction`, `estimated_value`, `category`

**Task**: `category`

**Invoice**: None remaining — pack file deleted.

---

## Section 7 — Prerequisite Context Migration

The `requiredForContexts` on promoted FieldDefinitions (e.g., `address_line1` required for INVOICE_GENERATION) must be replicated as structural checks. Two approaches:

**Recommended**: Add a `StructuralPrerequisiteCheck` that maps prerequisite contexts to entity column null-checks:

```
INVOICE_GENERATION → customer.addressLine1 != null, customer.city != null, customer.country != null, customer.taxNumber != null
PROPOSAL_SEND → customer.contactName != null, customer.contactEmail != null
```

These run alongside the existing custom field prerequisite checks in `PrerequisiteService`. Same `PrerequisiteCheck` response format — the caller doesn't know whether a violation came from a structural field or a custom field.

---

## Out of Scope

- **Data backfill**: No migration of JSONB values to new columns. Old data stays in JSONB.
- **Removing FieldDefinitions from existing tenants**: Existing tenants keep their old FieldDefinition/FieldValue records. Orphaned but harmless.
- **Custom field infrastructure changes**: The EAV system, `FieldPackSeeder`, admin field management UI all stay.
- **Validation rule migration**: Simple null checks migrate to structural prerequisite checks. Complex validation (regex patterns on custom fields) stays in the custom field system for remaining fields.
- **`tax_year` promotion**: Stays custom — accounting-specific and string-typed (YYYY format). DeadlineCalculationService continues to read it from JSONB.
- **Portal read-model updates**: The portal read-model (Phase 7) denormalizes customer data. Updating portal projections to include promoted fields is a follow-up concern.

## ADR Topics

- **ADR: Structural vs. Custom Field Boundary** — Define the principle: a field is structural when it's (a) queried in service logic, (b) required for a core flow, (c) present in all/most verticals, or (d) needs indexing. Document this so future field additions follow the principle rather than defaulting to custom.
- **ADR: `entity_type` as VARCHAR vs. Enum** — Explain why `entity_type` and `work_type` are VARCHAR columns with service-layer validation rather than Java enums: the valid values differ per vertical profile and may be extended by future verticals without a migration.

## Style & Boundaries

- All new columns are nullable — no NOT NULL constraints. Existing entities must not break.
- Use `@Column` annotations with explicit `name` and `length`.
- Use Java enums (`ProjectPriority`, `TaxType`) where values are universal and fixed. Use VARCHAR where values are vertical-dependent (`entity_type`, `work_type`).
- Frontend forms should use the existing Shadcn form components (Input, Select, DatePicker). No new component library additions.
- Template backward compatibility: keep `customFields.slug` aliases in context builders for one phase, then remove in a follow-up.
- Country dropdown uses ISO 3166-1 alpha-2 codes. Ship with the same country list as the existing common-customer pack's `country` dropdown options, expandable later.
