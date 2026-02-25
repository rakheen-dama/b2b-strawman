# Phase 23 — Custom Field Maturity & Data Integrity

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with a custom fields system built in Phase 11. The system supports 9 field types (TEXT, NUMBER, DATE, DROPDOWN, BOOLEAN, CURRENCY, URL, EMAIL, PHONE), field groups as organizational containers applied per entity instance, field packs seeded on tenant provisioning, saved view filtering via JSONB queries, and readiness checks that count unfilled required fields.

**Current state of custom fields:**
- `FieldDefinition` entity with validation rules (min/max, pattern, required)
- `FieldGroup` entity with `FieldGroupMember` join — groups are manually applied per entity instance
- `EntityType` enum covers PROJECT, TASK, CUSTOMER (not INVOICE)
- Field packs: `common-customer` (Contact & Address), `common-project` (Project Info). No task pack.
- `CustomFieldValidator` enforces type/validation on write, but only for fields in applied groups
- `CustomFieldSection` frontend component renders fields in view/edit mode on customer, project, and task detail pages
- `FieldGroupSelector` component lets users apply/remove groups per entity
- Readiness checks (Phase 15): `CustomerReadinessService` and `ProjectSetupStatusService` count unfilled required fields
- Document templates (Phase 12): `CustomerContextBuilder`, `ProjectContextBuilder`, `InvoiceContextBuilder` expose `customFields` map in Thymeleaf rendering context
- Invoicing (Phase 10): Full invoice lifecycle with line items from billable time entries. Rate snapshots on time entries. PDF generation via templates.

**Known gaps this phase addresses:**
1. Field groups are opt-in per instance — new entities start with no groups, so required fields are not enforced
2. No connection between what a template needs and what data exists — documents generate with blank fields
3. Billable time can be logged without rate cards — invoices get zero-amount line items
4. Invoices have no custom fields (no `EntityType.INVOICE`)
5. No task field pack seeded by default
6. No conditional field visibility or group dependencies
7. Minor bugs: DATE client validation missing min/max, CURRENCY blankness check uses naive `.toString().isBlank()`, field type can be changed after values exist (data corruption risk)

## Objective

Harden the custom fields system into a reliable data integrity layer. After this phase:
- Orgs can configure field groups to auto-apply to new entities, ensuring required fields are always enforced
- Document generation validates that required context fields are populated before producing final output
- Billable time logging warns when no rate card exists
- Invoices support custom fields like any other entity
- Field visibility can be conditional on other field values
- Related field groups can be linked so applying one auto-applies its dependencies

## Constraints & Assumptions

- All changes are tenant-scoped (schema-per-tenant). No shared schema changes needed.
- The existing `FieldDefinition`, `FieldGroup`, `FieldGroupMember` schema should be extended, not replaced.
- Custom field validation must remain consistent between backend (`CustomFieldValidator`) and frontend (`validateField`).
- The readiness system (Phase 15) should be extended, not duplicated.
- Document template rendering (Phase 12) must remain backward-compatible — existing templates must continue to work.
- Invoice custom fields follow the exact same storage pattern as project/task/customer (JSONB column on entity table).
- This phase does NOT build a visual form builder or drag-and-drop field designer.

## Detailed Requirements

### 1. Auto-Apply Field Groups

**Problem:** Field groups must be manually applied to each entity instance. A required field in a group has no effect until someone applies that group.

**Requirements:**
- Add an `autoApply` boolean flag to `FieldGroup` entity (default `false`).
- When `autoApply` is `true`, every new entity of that group's `entityType` automatically gets the group in its `applied_field_groups` list.
- When a field is added to or removed from an auto-applied group, all entities that have that group applied should reflect the change (the group membership is the source of truth — fields render based on group membership, so adding a field to the group automatically surfaces it on all entities with that group applied; no sync needed for field additions).
- **Retroactive apply**: When `autoApply` is toggled from `false` to `true`, the system should apply the group to existing entities that don't have it yet. **Architect: assess whether this is a simple UPDATE query on the JSONB column or whether it introduces complexity that warrants deferring to an explicit admin "Apply to all existing" action. Recommend the approach.**
- The settings UI should expose the `autoApply` toggle on the field group create/edit dialog.
- Field packs should set `autoApply: true` on their seeded groups (e.g., "Contact & Address" should auto-apply to all new customers).

### 2. Template-Declared Required Fields & Generation Validation

**Problem:** Document templates reference custom field values (e.g., `${customer.customFields['tax_number']}`) but there's no declaration of what fields a template needs. Documents generate with blank placeholders.

**Requirements:**
- Add a `requiredContextFields` property to `DocumentTemplate` entity — a JSONB list of field references, e.g., `[{"entity": "customer", "slug": "tax_number"}, {"entity": "customer", "slug": "address_line1"}, {"entity": "project", "slug": "reference_number"}]`.
- The template editor UI should allow authors to declare required fields (selectable from existing field definitions per entity type).
- At document generation time, validate that all declared required fields have non-empty values in the rendering context.
- **Draft behavior**: Generation is allowed with warnings. The generation dialog should show a list of missing fields with a "Generate anyway" option. The generated document should be marked with a `warnings` metadata field.
- **Final behavior**: When an invoice transitions to SENT status (or a document is marked as final), block the transition if the associated generated document has unresolved required field warnings — unless the user has ADMIN or OWNER role, in which case allow with confirmation.
- Extend `DocumentGenerationReadiness` (Phase 15) to include template required field checks.

### 3. Billable Time Rate Warnings

**Problem:** Users can log billable time on projects/members with no rate card configured. This produces time entries with null snapshot rates, which generate zero-amount invoice line items.

**Requirements:**
- When creating a billable time entry, check if a rate card exists for the member/project/customer combination (using the existing rate hierarchy from Phase 8).
- If no rate exists: show a non-blocking warning in the UI — "No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings → Rates."
- The warning should appear in the LogTimeDialog before submission, not after.
- Do NOT block time entry creation — the user may intend to set up rates later.
- When generating an invoice, if any candidate time entries have null rates: show a warning in the generation dialog listing the affected entries. Allow generation but flag the line items.

### 4. Invoice Generation Validation (Extended)

**Problem:** Invoices can be generated even when critical data is missing — no customer address, no org branding, null-rate line items.

**Requirements:**
- Extend the invoice generation flow to validate:
  - Customer has required fields filled (as defined by applied field groups)
  - Org has branding configured (logo, business name at minimum)
  - All candidate time entries have non-null rates (or user acknowledges the gap)
- Surface validation results in the generation dialog as a checklist with pass/warn/fail indicators.
- Draft generation: allowed with warnings.
- Final send: blocked if critical fields are missing (customer address, org business name). Admin override allowed.

### 5. Invoice Custom Fields

**Problem:** `EntityType` enum only covers PROJECT, TASK, CUSTOMER. Invoices cannot have custom fields, but verticals need invoice-level fields (e.g., PO Number, Payment Terms, Tax Classification).

**Requirements:**
- Add `INVOICE` to the `EntityType` enum.
- Add `custom_fields JSONB DEFAULT '{}'::jsonb` and `applied_field_groups JSONB` columns to the `invoices` table (new migration).
- Add GIN index on `invoices.custom_fields`.
- Extend `FieldDefinition` and `FieldGroup` CRUD to support `entityType = INVOICE`.
- Extend the invoice detail page to render `CustomFieldSection` and `FieldGroupSelector`.
- Extend `InvoiceContextBuilder` to include `invoice.customFields` in the template rendering context.
- Extend saved views filtering to support invoice custom fields (if invoice list has saved views — check existing implementation).
- No invoice field pack seeded by default (different forks will have different invoice fields).

### 6. Task Field Pack

**Problem:** Tasks support custom fields but no defaults are seeded — new tenants start with bare tasks.

**Requirements:**
- Create `common-task.json` field pack with a "Task Info" group containing:
  - `priority` (DROPDOWN: low, medium, high, urgent)
  - `category` (TEXT)
  - `estimated_hours` (NUMBER, min: 0)
- Set `autoApply: true` on the seeded group.
- Pack seeding follows the existing `FieldPackSeeder` pattern with idempotency via `OrgSettings.fieldPackStatus`.

### 7. Field Group Dependencies

**Problem:** Some field groups are logically related — applying "Billing Details" should also apply "Contact & Address" because you can't bill someone without an address.

**Requirements:**
- Add a `dependsOn` field to `FieldGroup` — a list of field group IDs that should be auto-applied when this group is applied.
- One level only — no cascading dependencies (if A depends on B and B depends on C, applying A applies B but NOT C).
- When a group with dependencies is applied to an entity, its dependency groups are also applied (if not already present).
- The settings UI should allow admins to configure dependencies when editing a group (selectable from other groups of the same entity type).
- Removing a dependency group that was auto-applied: allowed (the dependency is a convenience, not a constraint).

### 8. Conditional Field Visibility

**Problem:** Some fields are only relevant when another field has a specific value (e.g., "Trust Account Number" only when "Matter Type" = "Litigation").

**Requirements:**
- Add an optional `visibilityCondition` to `FieldDefinition` — a simple condition object: `{"dependsOnSlug": "matter_type", "operator": "eq", "value": "litigation"}`.
- Supported operators: `eq`, `neq`, `in` (value is a list).
- The condition field must reference another `FieldDefinition` of the same entity type.
- **Frontend behavior**: `CustomFieldSection` evaluates visibility conditions against current field values. Hidden fields are not rendered. When the controlling field changes, dependent fields show/hide reactively.
- **Backend behavior**: `CustomFieldValidator` skips validation (including required checks) for fields whose visibility condition is not met. Values for hidden fields are preserved (not cleared) — they just aren't visible or validated.
- **Template rendering**: Hidden field values are still available in the template context (the template author decides whether to use them).
- The field definition dialog should allow setting a visibility condition (select controlling field, operator, value).

### 9. Bug Fixes & Hardening

These are small fixes discovered during codebase analysis:

- **DATE client validation**: Wire min/max date validation rules into the frontend `validateField` function. The backend already supports these rules; the frontend skips them.
- **CURRENCY blankness check**: In `CustomerReadinessService.computeRequiredFields`, the blankness check uses `.toString().isBlank()` which doesn't correctly handle CURRENCY objects (`{amount: 0, currency: ""}` is non-blank as a string). Fix to check `amount` is non-null and `currency` is non-blank for CURRENCY type fields.
- **Field type immutability**: Prevent changing `fieldType` on a `FieldDefinition` that has existing values stored on any entity. Return a validation error: "Field type cannot be changed after values exist. Create a new field instead." The check should query whether any entity of the field's `entityType` has a non-null value for the field's slug in its `custom_fields` JSONB.

## Out of Scope

- Visual form builder / drag-and-drop field designer
- Multi-level conditional logic (if A then if B then show C)
- Cascading group dependencies (only one level)
- Custom field reporting or analytics
- Bulk editing of custom field values across entities
- Custom field API for external integrations

## ADR Topics

The architect should produce ADRs for:

1. **Auto-apply strategy** — retroactive apply mechanics: JSONB array append query vs. explicit admin action vs. deferred. Assess data volume implications and migration safety.
2. **Template required fields** — how field references are stored and resolved at generation time. Consider: what happens when a referenced field definition is deleted? Soft reference (render blank) vs. hard reference (block generation)?
3. **Conditional visibility** — where evaluation happens (frontend only vs. frontend + backend). Consider: should hidden fields' values be cleared when the condition changes, or preserved silently?

## Style & Boundaries

- Follow existing patterns: `FieldDefinition` entity style, `CustomFieldValidator` structure, `CustomFieldSection` component patterns.
- Backend validation and frontend validation must stay in sync — any new validation rule needs both implementations.
- All new fields/columns need Flyway migrations in the tenant schema.
- Readiness services should be extended, not duplicated.
- Field pack JSON files follow the existing format in `classpath:field-packs/`.
- Settings UI follows the existing custom fields settings page layout (tabs by entity type, tables with action dropdowns).
