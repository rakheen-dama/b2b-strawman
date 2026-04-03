# Layer 2: Settings, Custom Fields & Tags (Scenario Outlines)

**Product:** DocTeams — Multi-tenant B2B SaaS Practice Management Platform
**Date:** 2026-03-06
**Prepared by:** QA Team

---

## How to Read This Document

Each scenario outline contains: title, objective, preconditions, key steps, and key validations. Actors are drawn from the [test team](00-overview.md#test-team) and [test customers](00-overview.md#test-customers).

---

## 1. Org Settings

### SET-001: Update Default Currency

**Objective:** Verify that an Owner can update the organization's default currency.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings exist with defaultCurrency="ZAR"

**Key Steps:**
1. Thandi updates defaultCurrency to "USD".

**Key Validations:**
- OrgSettings.defaultCurrency is now "USD".
- updatedAt timestamp changed.
- New invoices, rates, and budgets default to USD.
- Audit event logged for settings change.

---

### SET-002: Upload Org Logo

**Objective:** Verify that an Owner can upload a logo image to S3.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings exist with logoS3Key=null

**Key Steps:**
1. Thandi uploads a PNG logo file.

**Key Validations:**
- Logo file uploaded to S3.
- OrgSettings.logoS3Key is set to the S3 key.
- Logo appears in portal branding and document generation.
- Presigned URL for logo is valid and returns the image.

---

### SET-003: Delete Org Logo

**Objective:** Verify that an Owner can remove the uploaded logo.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings has logoS3Key set

**Key Steps:**
1. Thandi deletes the logo.

**Key Validations:**
- OrgSettings.logoS3Key is set to null.
- S3 object is deleted (or marked for deletion).
- Portal and documents fall back to default/no logo.

---

### SET-004: Update Brand Color

**Objective:** Verify updating the organization's brand color.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings exist with brandColor=null

**Key Steps:**
1. Thandi sets brandColor to "#1E40AF".

**Key Validations:**
- OrgSettings.brandColor is "#1E40AF".
- Portal UI reflects the brand color.
- Generated documents apply the brand color.

---

### SET-005: Update Document Footer Text

**Objective:** Verify updating the footer text used in generated documents.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings exist

**Key Steps:**
1. Thandi sets documentFooterText to "Acme Professional Services (Pty) Ltd | Reg No. 2020/123456/07".

**Key Validations:**
- OrgSettings.documentFooterText is updated.
- Generated PDFs include the footer text.

---

### SET-006: Toggle Feature Flags

**Objective:** Verify toggling integration domain feature flags.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings: accountingEnabled=false, aiEnabled=false, documentSigningEnabled=false

**Key Steps:**
1. Thandi enables accountingEnabled and documentSigningEnabled, leaves aiEnabled=false.

**Key Validations:**
- accountingEnabled=true, documentSigningEnabled=true, aiEnabled=false.
- Features guarded by these flags become available/unavailable accordingly.

---

### SET-007: Update Tax Configuration

**Objective:** Verify updating tax settings (registration number, label, inclusive/exclusive).

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings exist with taxInclusive=false

**Key Steps:**
1. Thandi updates: taxRegistrationNumber="4210123456", taxRegistrationLabel="VAT No.", taxLabel="VAT", taxInclusive=true.

**Key Validations:**
- All four tax fields updated.
- taxInclusive=true affects invoice calculations (amounts shown as tax-inclusive).
- Tax registration number and label appear on generated invoices.

---

### SET-008: Configure Time Reminders

**Objective:** Verify configuring the time reminder system.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings: timeReminderEnabled=false

**Key Steps:**
1. Thandi sets: timeReminderEnabled=true, timeReminderDays="MON,TUE,WED,THU,FRI", timeReminderTime=17:00, timeReminderMinMinutes=480 (8 hours).

**Key Validations:**
- All time reminder fields updated.
- getWorkingDays() returns Monday through Friday.
- getTimeReminderMinHours() returns 8.0.
- TIME_REMINDER notifications fire on configured days for members below the threshold.

---

## 2. Custom Field Definitions

### CF-001: Create TEXT Field Definition

**Objective:** Verify creating a custom TEXT field for projects.

**Actors:** Fatima Al-Hassan (Member with appropriate permissions)

**Preconditions:**
- No existing field with the same slug for PROJECT entity type

**Key Steps:**
1. Create a FieldDefinition with entityType=PROJECT, name="Engagement Manager", fieldType=TEXT.

**Key Validations:**
- Field created with slug auto-generated ("engagement_manager").
- fieldType=TEXT, entityType=PROJECT, active=true, required=false.
- sortOrder defaults to 0.

---

### CF-002: Create NUMBER Field Definition

**Objective:** Verify creating a numeric custom field with validation.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- None

**Key Steps:**
1. Create a FieldDefinition with entityType=TASK, name="Estimated Hours", fieldType=NUMBER, validation={"min": 0, "max": 1000}.

**Key Validations:**
- Field created with fieldType=NUMBER.
- validation JSONB stored correctly with min/max.
- Slug generated: "estimated_hours".

---

### CF-003: Create DROPDOWN Field Definition

**Objective:** Verify creating a dropdown field with options.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- None

**Key Steps:**
1. Create a FieldDefinition with entityType=CUSTOMER, name="Industry", fieldType=DROPDOWN, options=[{"label":"Technology","value":"tech"}, {"label":"Finance","value":"finance"}, {"label":"Healthcare","value":"healthcare"}].

**Key Validations:**
- Field created with fieldType=DROPDOWN.
- options JSONB contains all 3 dropdown options.
- Slug generated: "industry".

---

### CF-004: Create BOOLEAN, CURRENCY, DATE, URL, EMAIL, PHONE Fields

**Objective:** Verify creating each remaining field type.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- None

**Key Steps:**
1. Create a BOOLEAN field: name="Is Priority Client", entityType=CUSTOMER.
2. Create a CURRENCY field: name="Budget Limit", entityType=PROJECT.
3. Create a DATE field: name="Review Date", entityType=TASK.
4. Create a URL field: name="Company Website", entityType=CUSTOMER.
5. Create an EMAIL field: name="Secondary Contact", entityType=CUSTOMER.
6. Create a PHONE field: name="Office Phone", entityType=CUSTOMER.

**Key Validations:**
- Each field created with correct fieldType enum value.
- Slugs generated correctly for each: "is_priority_client", "budget_limit", "review_date", "company_website", "secondary_contact", "office_phone".

---

### CF-005: Set Required Flag on Field

**Objective:** Verify that a field can be marked as required.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A FieldDefinition exists with required=false

**Key Steps:**
1. Update the field: required=true.

**Key Validations:**
- Field required=true.
- Entities missing this field value should be flagged as incomplete (if validation is enforced).

---

### CF-006: Update Field Metadata

**Objective:** Verify updating field name, description, and validation without changing type.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A FieldDefinition exists

**Key Steps:**
1. Call updateMetadata with new name, description, required flag, and validation rules.

**Key Validations:**
- name, description, required, validation updated.
- slug remains unchanged (immutable after creation).
- updatedAt changed.

---

### CF-007: Deactivate Field Definition (Soft Delete)

**Objective:** Verify soft-deleting a field definition.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A FieldDefinition exists with active=true

**Key Steps:**
1. Deactivate the field.

**Key Validations:**
- active=false, updatedAt changed.
- Field no longer appears in active field listings.
- Existing field values on entities are preserved (not deleted).

---

### CF-008: Slug Generation Validation

**Objective:** Verify that slug generation handles edge cases correctly.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- None

**Key Steps:**
1. Create a field with name="Tax ID Number" (spaces and uppercase).
2. Create a field with name="123Invalid" (starts with number).
3. Create a field with name="" (blank).

**Key Validations:**
- "Tax ID Number" generates slug "tax_id_number".
- "123Invalid" throws InvalidStateException (slug must start with a letter).
- "" throws InvalidStateException (name must not be blank).

---

## 3. Field Groups

### FG-001: Create Field Group

**Objective:** Verify creating a field group as an organizational container for fields.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- None

**Key Steps:**
1. Create a FieldGroup with entityType=PROJECT, name="Financial Details", slug="financial_details".

**Key Validations:**
- Group created with active=true, autoApply=false, sortOrder=0.
- Group is available for field assignment.

---

### FG-002: Add Fields to Group

**Objective:** Verify associating field definitions with a group.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A FieldGroup exists for PROJECT
- 3 PROJECT FieldDefinitions exist

**Key Steps:**
1. Associate the 3 fields with the group.

**Key Validations:**
- Fields are linked to the group.
- Group contains exactly 3 fields when queried.

---

### FG-003: Remove Field from Group

**Objective:** Verify removing a field from a group.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A group with 3 fields

**Key Steps:**
1. Remove 1 field from the group.

**Key Validations:**
- Group now contains 2 fields.
- Removed field still exists as a standalone definition (not deleted).

---

### FG-004: Reorder Fields in Group

**Objective:** Verify changing the sort order of fields within a group.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A group with 3 fields at sortOrder 0, 1, 2

**Key Steps:**
1. Update sortOrder: move field at position 2 to position 0.

**Key Validations:**
- Fields returned in new order when sorted by sortOrder.

---

### FG-005: Auto-Apply Group

**Objective:** Verify that groups with autoApply=true are automatically applied to new entities.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A FieldGroup with entityType=PROJECT and autoApply=true, containing 2 fields

**Key Steps:**
1. Create a new project.

**Key Validations:**
- New project automatically has the group's field definitions available.
- Field values default to the field's defaultValue (if set) or null.

---

### FG-006: Update Group Metadata

**Objective:** Verify updating group name, description, and sortOrder.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A FieldGroup exists

**Key Steps:**
1. Update: name="Updated Financial Details", description="Contains all financial custom fields", sortOrder=5.

**Key Validations:**
- All metadata fields updated.
- updatedAt changed.
- slug remains unchanged.

---

## 4. Custom Field Values

### CF-009: Set Custom Field Value on Project

**Objective:** Verify setting a custom field value on a project entity.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A TEXT FieldDefinition exists for PROJECT (slug="engagement_manager")
- A project exists

**Key Steps:**
1. Set the field value: fieldSlug="engagement_manager", value="James Chen".

**Key Validations:**
- Field value stored in JSONB format on the project.
- Value retrievable by field slug.

---

### CF-010: Set Custom Field Value on Task

**Objective:** Verify setting a custom field value on a task.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A NUMBER FieldDefinition exists for TASK (slug="estimated_hours")
- A task exists

**Key Steps:**
1. Set the field value: fieldSlug="estimated_hours", value=8.5.

**Key Validations:**
- Numeric value stored correctly.
- Value validation enforced (within min/max if validation rules set).

---

### CF-011: Set Custom Field Value on Customer

**Objective:** Verify setting a custom field value on a customer entity.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A DROPDOWN FieldDefinition exists for CUSTOMER (slug="industry")
- Acme Corp exists

**Key Steps:**
1. Set the field value: fieldSlug="industry", value="tech".

**Key Validations:**
- Dropdown value matches one of the defined options.
- Invalid values (not in options list) are rejected.

---

### CF-012: Set Custom Field Value on Invoice

**Objective:** Verify setting a custom field value on an invoice.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A FieldDefinition exists for INVOICE
- An invoice exists

**Key Steps:**
1. Set the field value on the invoice.

**Key Validations:**
- Value stored in JSONB on the invoice.
- Value included in invoice detail response.

---

### CF-013: Query Entities by Custom Field Value

**Objective:** Verify filtering entities by custom field values.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- Multiple customers with "industry" field set to different values ("tech", "finance", "healthcare")

**Key Steps:**
1. Filter customers where industry="tech".

**Key Validations:**
- Only customers with industry="tech" returned.
- Customers with other industry values excluded.

---

### CF-014: JSONB Storage Integrity

**Objective:** Verify that custom field values survive round-trips correctly across all types.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- Fields of each type exist with values set

**Key Steps:**
1. Set values for TEXT, NUMBER, DATE, BOOLEAN, CURRENCY, URL, EMAIL, PHONE fields.
2. Read back all values.

**Key Validations:**
- TEXT: string preserved exactly.
- NUMBER: decimal precision preserved.
- DATE: ISO date format preserved.
- BOOLEAN: true/false preserved (not "true"/"false" strings).
- CURRENCY: amount and currency code preserved.
- URL/EMAIL/PHONE: string values preserved with format validation.

---

## 5. Tags

### TAG-001: Create Tag

**Objective:** Verify creating a new tag with auto-generated slug.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- No tag with the same slug exists

**Key Steps:**
1. Create a Tag with name="High Priority", color="#EF4444".

**Key Validations:**
- Tag created with slug="high_priority" (auto-generated).
- color="#EF4444".
- createdAt and updatedAt set.

---

### TAG-002: Update Tag Color

**Objective:** Verify updating a tag's display color.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A tag exists with color="#EF4444"

**Key Steps:**
1. Update the tag: name remains same, color changed to "#10B981".

**Key Validations:**
- color="#10B981", updatedAt changed.
- slug remains unchanged (immutable).

---

### TAG-003: Delete Tag

**Objective:** Verify deleting a tag.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A tag exists

**Key Steps:**
1. Delete the tag.

**Key Validations:**
- Tag removed from the listing.
- Tag associations on entities are cleaned up.

---

### TAG-004: Apply Tags to Entities

**Objective:** Verify applying tags to projects, tasks, and customers.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- Tags exist: "urgent", "vip", "audit"
- A project, task, and customer exist

**Key Steps:**
1. Apply "urgent" and "audit" tags to the project.
2. Apply "urgent" tag to the task.
3. Apply "vip" tag to the customer.

**Key Validations:**
- Project has 2 tags associated.
- Task has 1 tag associated.
- Customer has 1 tag associated.
- Tags appear in entity detail responses.

---

### TAG-005: Filter Entities by Tag

**Objective:** Verify filtering entities by tag.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- Multiple projects with different tag combinations

**Key Steps:**
1. Filter projects by tag "urgent".

**Key Validations:**
- Only projects tagged "urgent" returned.
- Projects without "urgent" tag excluded.

---

## 6. Project Templates

### PT-001: Create Project Template

**Objective:** Verify creating a new project template from scratch.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- None

**Key Steps:**
1. Create a ProjectTemplate with name="Standard Audit", namePattern="{customer} Annual Audit {year}", description="Template for annual audit engagements", billableDefault=true.

**Key Validations:**
- Template created with source="MANUAL", active=true.
- namePattern stored for project name generation.
- billableDefault=true (projects created from template default to billable).

---

### PT-002: Create Template from Existing Project

**Objective:** Verify creating a template by extracting structure from an existing project.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A project exists with tasks, custom field values, and document structure

**Key Steps:**
1. Create a template from the existing project.

**Key Validations:**
- Template created with source="PROJECT", sourceProjectId set.
- Template tasks (TemplateTasks) created matching the project's tasks.
- Template task items (TemplateTaskItems) created matching task checklist items.

---

### PT-003: Instantiate Project from Template

**Objective:** Verify creating a new project by instantiating a template.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ProjectTemplate exists with 5 TemplateTasks
- An ACTIVE customer exists

**Key Steps:**
1. Instantiate the template for Acme Corp with project name "Acme Annual Audit 2026".

**Key Validations:**
- Project created with status ACTIVE.
- 5 tasks created matching the template's TemplateTasks.
- Task items created matching TemplateTaskItems.
- billableDefault applied to the project.
- Customer linked to the project.

---

### PT-004: Duplicate Template

**Objective:** Verify duplicating an existing template.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ProjectTemplate exists with tasks and items

**Key Steps:**
1. Duplicate the template with a new name.

**Key Validations:**
- New template created as a copy with new ID.
- All TemplateTasks and TemplateTaskItems duplicated.
- source="MANUAL" (or "DUPLICATE") on the new template.

---

### PT-005: Template Prerequisite Check (Required Customer Fields)

**Objective:** Verify that templates with requiredCustomerFieldIds block instantiation if fields are missing.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ProjectTemplate with requiredCustomerFieldIds=[fieldId1, fieldId2]
- A customer with only fieldId1 set (fieldId2 missing)

**Key Steps:**
1. Attempt to instantiate the template for the customer.

**Key Validations:**
- Instantiation blocked with an error identifying the missing field(s).
- Project is NOT created.

---

### PT-006: Deactivate Template

**Objective:** Verify deactivating a template so it cannot be instantiated.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ProjectTemplate with active=true

**Key Steps:**
1. Set active=false on the template.

**Key Validations:**
- Template no longer appears in active template listings.
- Attempting to instantiate a deactivated template is blocked.

---

## 7. Checklist Templates

### CKL-001: Create Checklist Template

**Objective:** Verify creating a reusable checklist template.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- None

**Key Steps:**
1. Create a ChecklistTemplate with name="Standard Onboarding", customerType="COMPANY", source="MANUAL", autoInstantiate=true.

**Key Validations:**
- Template created with slug auto-generated ("standard-onboarding", note: hyphens not underscores).
- active=true, sortOrder=0.
- autoInstantiate=true means it will be auto-applied to new customers of type COMPANY.

---

### CKL-002: Update Checklist Template

**Objective:** Verify updating a checklist template's metadata.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ChecklistTemplate exists

**Key Steps:**
1. Update: name="Enhanced Onboarding", description="Comprehensive onboarding for premium clients", autoInstantiate=false.

**Key Validations:**
- name, description, autoInstantiate updated.
- updatedAt changed.
- slug remains unchanged.

---

### CKL-003: Clone Checklist Template

**Objective:** Verify cloning a checklist template to create a variant.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ChecklistTemplate exists with associated items

**Key Steps:**
1. Clone the template with a new name.

**Key Validations:**
- New template created with new ID and slug.
- All checklist items duplicated.
- source remains or is set appropriately.

---

### CKL-004: Deactivate Checklist Template

**Objective:** Verify soft-deleting a checklist template.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ChecklistTemplate with active=true

**Key Steps:**
1. Deactivate the template.

**Key Validations:**
- active=false, updatedAt changed.
- Template no longer auto-instantiates for new customers.
- Existing checklists already instantiated from this template are unaffected.

---

### CKL-005: Instantiate Checklist for Customer

**Objective:** Verify that a checklist template is instantiated for a customer during onboarding.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ChecklistTemplate with autoInstantiate=true exists
- A new customer is being created

**Key Steps:**
1. Create a new customer that matches the template's customerType.
2. Transition customer to ONBOARDING.

**Key Validations:**
- Checklist instance created for the customer based on the template.
- All template items appear as checklist items with status PENDING.
- Completing all items triggers auto-transition to ACTIVE.

---

## 8. Schedules

### SCHED-001: Create Recurring Schedule

**Objective:** Verify creating a recurring schedule linked to a template and customer.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A ProjectTemplate exists
- An ACTIVE customer exists

**Key Steps:**
1. Create a RecurringSchedule with templateId, customerId=Acme Corp, frequency="MONTHLY", startDate=today, endDate=today+365, leadTimeDays=7.

**Key Validations:**
- Schedule created with status="ACTIVE", executionCount=0.
- nextExecutionDate computed based on startDate and frequency.
- leadTimeDays=7 means project creation happens 7 days before period start.

---

### SCHED-002: Pause Schedule

**Objective:** Verify pausing an active schedule.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A RecurringSchedule with status="ACTIVE"

**Key Steps:**
1. Set status to "PAUSED".

**Key Validations:**
- status="PAUSED", updatedAt changed.
- Schedule skips execution while paused.

---

### SCHED-003: Resume Schedule

**Objective:** Verify resuming a paused schedule.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A RecurringSchedule with status="PAUSED"

**Key Steps:**
1. Set status to "ACTIVE".

**Key Validations:**
- status="ACTIVE".
- nextExecutionDate recalculated if the pause caused a missed execution.

---

### SCHED-004: Delete Schedule

**Objective:** Verify deleting a schedule.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A RecurringSchedule exists

**Key Steps:**
1. Delete the schedule.

**Key Validations:**
- Schedule removed from listings.
- Associated ScheduleExecution records remain for audit purposes (or are cascade deleted, verify which).

---

### SCHED-005: View Schedule Executions

**Objective:** Verify viewing the execution history of a schedule.

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- A RecurringSchedule with executionCount > 0

**Key Steps:**
1. List ScheduleExecution records for the schedule.

**Key Validations:**
- Execution records show: period dates, execution timestamp, created project reference.
- executionCount on the schedule matches the number of records.
- lastExecutedAt matches the most recent execution.

---

## 9. Saved Views

### VIEW-001: Create Saved View

**Objective:** Verify creating a personal saved view with filters.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- None

**Key Steps:**
1. Create a SavedView with entityType="PROJECT", name="My Active Projects", filters={"status":"ACTIVE","assignee":"fatima"}, columns=["name","status","customer","deadline"], shared=false.

**Key Validations:**
- View created with createdBy=Fatima's ID.
- filters and columns stored as JSONB.
- shared=false (personal view).
- sortOrder set.

---

### VIEW-002: Update Saved View Filters

**Objective:** Verify updating the filter and column configuration of a saved view.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A SavedView exists created by Fatima

**Key Steps:**
1. Update filters to add a tag filter: filters={"status":"ACTIVE","tag":"urgent"}, columns=["name","status","tags","deadline"].

**Key Validations:**
- filters and columns updated.
- updatedAt changed.
- name preserved (or updated if provided).

---

### VIEW-003: Apply Saved View

**Objective:** Verify that applying a saved view returns correctly filtered results.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A SavedView exists with filters for status=ACTIVE
- Multiple projects exist with different statuses

**Key Steps:**
1. Fatima applies the saved view.

**Key Validations:**
- Only projects matching the filter criteria are returned.
- Results respect column selection (if enforced by the UI).

---

### VIEW-004: Delete Saved View

**Objective:** Verify deleting a saved view.

**Actors:** Fatima Al-Hassan (Member)

**Preconditions:**
- A SavedView exists created by Fatima

**Key Steps:**
1. Fatima deletes the saved view.

**Key Validations:**
- View removed from listings.
- Other users' views are unaffected.

---

## 10. RBAC for Settings

### SET-009: Owner Can Modify All Settings

**Objective:** Verify that Owners have full access to modify all org settings.

**Actors:** Thandi Nkosi (Owner)

**Preconditions:**
- OrgSettings exist

**Key Steps:**
1. Thandi updates currency, branding, tax config, feature flags, and time reminders.

**Key Validations:**
- All updates succeed with 200 status.
- All fields reflect the new values.

---

### SET-010: Admin Can Modify Settings

**Objective:** Verify that Admins can modify org settings (same as Owner for settings).

**Actors:** Priya Sharma (Admin)

**Preconditions:**
- OrgSettings exist

**Key Steps:**
1. Priya updates brand color and document footer text.

**Key Validations:**
- Updates succeed.
- Admin has write access to org settings.

---

### SET-011: Member Cannot Modify Settings

**Objective:** Verify that Members cannot modify org settings.

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- OrgSettings exist

**Key Steps:**
1. Lerato attempts to update defaultCurrency.
2. Lerato attempts to upload a logo.
3. Lerato attempts to toggle feature flags.

**Key Validations:**
- All modification attempts return 403 Forbidden.
- Settings remain unchanged.

---

### SET-012: Member Can Read Settings

**Objective:** Verify that Members can read org settings (needed for UI rendering).

**Actors:** Lerato Dlamini (Member)

**Preconditions:**
- OrgSettings exist with values set

**Key Steps:**
1. Lerato reads the org settings.

**Key Validations:**
- Response returns current settings values.
- Read access is permitted for Members.
- No sensitive data exposure beyond what's needed for UI rendering.
