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
- **Invoicing & billing from time** (Phase 10): Invoice/InvoiceLine entities, draft-to-paid lifecycle, unbilled time management, PSP adapter seam, HTML invoice preview.

For **Phase 11**, I want to add **Tags, Custom Fields & Views** — a generic extensibility layer that lets organizations customize their projects, tasks, and customers with structured fields, freeform tags, and saved filtered views. Field definitions are org-scoped but bootstrapped from platform-provided "field packs" — seed data that vertical forks can replace or extend without code changes.

***

## Objective of Phase 11

Design and specify:

1. **Field definition system** — org-scoped field definitions with typed values (text, number, date, dropdown, boolean, currency, URL, email, phone). Fields are attached to entity types (project, task, customer).
2. **Field groups ("field packs")** — named bundles of related field definitions that can be applied together. Platform ships default packs (e.g., "Common Customer Fields," "Common Project Fields"). Vertical forks add domain-specific packs (e.g., "Litigation Fields," "Conveyancing Fields") via seed data. Orgs can adopt, customize, or ignore packs.
3. **Custom field values** — JSONB storage of field values on projects, tasks, and customers. Validated against field definitions at write time.
4. **Tags** — freeform, multi-value labels on projects, tasks, and customers. Org-scoped tag namespace with auto-complete. Optional color per tag.
5. **Saved views** — named, reusable filter configurations on list pages (projects, tasks, customers). Filter by custom field values, tags, status, assignee, date ranges. Views can be personal or shared within the org.
6. **Field pack infrastructure** — seed data mechanism for bootstrapping field definitions from JSON/YAML configuration. Pack identity tracking to support updates without clobbering org customizations.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - NoSQL databases or document stores — JSONB in Postgres handles the semi-structured field values.
    - GraphQL or alternative query layers — REST API with filter parameters.
    - Client-side state management libraries (Redux, Zustand) — server components + URL search params for filter state.
    - Separate microservices — everything stays in the existing backend deployable.
- JSONB is the storage strategy for custom field values (not EAV tables). This keeps queries simple and avoids join explosion. Indexed via GIN indexes for filtering.
- Field definitions are relational (normal tables with FK relationships), not JSONB — they need referential integrity, querying, and lifecycle management.

2. **Tenancy**

- All new entities (FieldDefinition, FieldGroup, FieldGroupMember, Tag, EntityTag, SavedView) follow the same tenant isolation model as existing entities:
    - Pro orgs: dedicated schema.
    - Starter orgs: `tenant_shared` schema with `tenant_id` column + Hibernate `@Filter` + RLS.
- All new entities must include Flyway migrations for both tenant and shared schemas.
- Field packs are seeded per-tenant during tenant provisioning (not globally). Each tenant gets its own copies of field definitions from packs, which they can then customize independently.

3. **Permissions model**

- Field definition management (create, edit, delete field definitions and groups):
    - Org admins and owners only.
- Field value editing (setting custom field values on projects, tasks, customers):
    - Same permissions as the parent entity. If you can edit a project, you can edit its custom field values.
- Tag management (create tags, apply/remove tags):
    - All org members can apply existing tags.
    - Org admins and owners can create new tags and delete tags.
- Saved views:
    - Any member can create personal views.
    - Org admins and owners can create shared views.
    - Personal views are only visible to the creator.
    - Shared views are visible to all org members.

4. **Relationship to existing entities**

- **Project**: Gains `custom_fields` (JSONB) column and tag associations. Custom fields displayed in project detail view. Filterable in project list.
- **Task**: Gains `custom_fields` (JSONB) column and tag associations. Custom fields displayed in task detail view. Filterable in task list.
- **Customer**: Gains `custom_fields` (JSONB) column and tag associations. Custom fields displayed in customer detail view. Filterable in customer list.
- **OrgSettings**: Extended to track which field packs have been adopted and any pack-level customization state.
- **AuditEvent**: Field definition changes (create, update, delete) publish audit events. Saved view changes are not audited (low-value noise).

5. **Out of scope for Phase 11**

- Conditional/dependent fields (show field X only when field Y = value).
- Computed/formula fields (field value derived from other fields).
- Field-level permissions (all org members see all custom fields).
- Relational/lookup fields (field that references another entity — e.g., a "Related Project" field on a task).
- Custom field types beyond the core set (no file upload, no rich text, no multi-select dropdown).
- Bulk field value editing (editing one entity at a time only).
- Custom fields on documents, time entries, or invoices (only projects, tasks, customers in v1).
- Import/export of field definitions or field packs via UI.
- Drag-and-drop field ordering in the UI (use simple up/down reordering or sort_order input).
- Document template variable substitution from custom fields (Phase 12).
- Reporting/analytics breakdown by custom fields (future phase).
- Tag hierarchy or nested tags (flat namespace only).

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase11-tags-custom-fields-views.md`, plus ADRs for key decisions.

### 1. Field definition entity

Design a **FieldDefinition** entity:

1. **Data model**

    - `FieldDefinition` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `entity_type` (ENUM: `PROJECT`, `TASK`, `CUSTOMER` — which entity type this field applies to).
        - `name` (VARCHAR(100) — human-readable field label, e.g., "Case Number," "Court Jurisdiction").
        - `slug` (VARCHAR(100) — machine-readable key used in JSONB storage and API, e.g., "case_number," "court_jurisdiction"). Auto-generated from name if not provided. Must be unique per (tenant, entity_type).
        - `field_type` (ENUM: `TEXT`, `NUMBER`, `DATE`, `DROPDOWN`, `BOOLEAN`, `CURRENCY`, `URL`, `EMAIL`, `PHONE`).
        - `description` (TEXT, nullable — help text shown to users).
        - `required` (BOOLEAN, default false — whether a value must be provided when creating/editing the entity).
        - `default_value` (JSONB, nullable — default value for the field, type-checked against field_type).
        - `options` (JSONB, nullable — for DROPDOWN fields: array of `{ value: string, label: string }` objects. Null for other field types).
        - `validation` (JSONB, nullable — optional validation rules: `{ min, max, pattern, minLength, maxLength }`).
        - `sort_order` (INTEGER — display order among fields for this entity_type within the org).
        - `pack_id` (VARCHAR, nullable — identifier of the field pack this definition originated from, e.g., "common-customer", "legal-litigation". Null for org-created fields).
        - `pack_field_key` (VARCHAR, nullable — the field's key within its pack, used for update tracking. Null for org-created fields).
        - `active` (BOOLEAN, default true — soft-delete flag. Inactive fields are hidden from forms but their values are preserved).
        - `created_at`, `updated_at` timestamps.
    - Constraints:
        - `(tenant_id, entity_type, slug)` is unique — no two fields with the same slug for the same entity type in the same tenant.
        - `slug` must match pattern `[a-z][a-z0-9_]*` (lowercase, underscore-separated, starts with letter).
        - `options` must be non-empty array when `field_type = DROPDOWN`.
        - `pack_id` and `pack_field_key` are both null or both non-null.
    - Indexes:
        - `(tenant_id, entity_type, active)` for listing active fields per entity type.
        - `(tenant_id, entity_type, slug)` unique.
        - `(tenant_id, pack_id)` for finding all fields from a specific pack.

2. **Field type semantics**

    For each field type, define:
    - Storage format in JSONB (e.g., TEXT stores as JSON string, NUMBER stores as JSON number, DATE stores as ISO 8601 string "YYYY-MM-DD").
    - Validation rules (e.g., EMAIL validates format, URL validates format, CURRENCY stores as `{ amount: number, currency: "ZAR" }`).
    - Frontend input component mapping (e.g., TEXT -> Input, NUMBER -> Input type="number", DATE -> DatePicker, DROPDOWN -> Select, BOOLEAN -> Checkbox).
    - Filtering semantics (e.g., TEXT supports contains/equals, NUMBER supports equals/gt/lt/range, DATE supports equals/range, DROPDOWN supports equals/in, BOOLEAN supports equals).

### 2. Field groups

Design a **FieldGroup** entity and the group membership relationship:

1. **Data model**

    - `FieldGroup` entity:
        - `id` (UUID).
        - `tenant_id` (for shared-schema isolation).
        - `entity_type` (ENUM: `PROJECT`, `TASK`, `CUSTOMER` — groups are scoped to one entity type).
        - `name` (VARCHAR(100) — display name, e.g., "Litigation Fields," "Common Customer Info").
        - `slug` (VARCHAR(100) — machine-readable key, e.g., "litigation-fields").
        - `description` (TEXT, nullable).
        - `pack_id` (VARCHAR, nullable — field pack this group originated from).
        - `sort_order` (INTEGER — display order among groups for this entity_type).
        - `active` (BOOLEAN, default true).
        - `created_at`, `updated_at` timestamps.
    - Constraints:
        - `(tenant_id, entity_type, slug)` is unique.
    - `FieldGroupMember` join entity:
        - `id` (UUID).
        - `tenant_id`.
        - `field_group_id` (UUID, FK -> field_groups).
        - `field_definition_id` (UUID, FK -> field_definitions).
        - `sort_order` (INTEGER — display order within the group).
    - Constraints:
        - `(field_group_id, field_definition_id)` is unique — a field can only appear once in a group.
        - The field definition's `entity_type` must match the group's `entity_type`.
        - A field definition can belong to multiple groups.

2. **Group application**

    - When creating a project, the user can select one or more field groups (e.g., "Litigation" + "Common Project") or individual fields. The selected groups determine which custom fields appear in the entity's detail form.
    - The entity does NOT store a foreign key to the group. Instead, the group is a convenience for bulk-selecting fields. The entity's `custom_fields` JSONB stores values keyed by field slug, regardless of how the fields were selected.
    - If a field is later removed from a group, existing values on entities are preserved (orphaned values are harmless — they just don't render in the form anymore).
    - Projects, tasks, and customers gain an `applied_field_groups` (JSONB array of group IDs, nullable) column to remember which groups were selected — used to pre-select groups in edit forms and to render the right field sections.

### 3. Field pack infrastructure

Design the seed data mechanism for field packs:

1. **Pack definition format**

    - Packs are defined as JSON files in the backend resources (e.g., `src/main/resources/field-packs/common-customer.json`).
    - Format:
        ```json
        {
            "packId": "common-customer",
            "name": "Common Customer Fields",
            "entityType": "CUSTOMER",
            "description": "Standard customer fields: address, contact info, tax details",
            "fields": [
                {
                    "key": "address_line1",
                    "name": "Address Line 1",
                    "fieldType": "TEXT",
                    "required": false,
                    "sortOrder": 1
                },
                {
                    "key": "address_line2",
                    "name": "Address Line 2",
                    "fieldType": "TEXT",
                    "required": false,
                    "sortOrder": 2
                },
                {
                    "key": "city",
                    "name": "City",
                    "fieldType": "TEXT",
                    "required": false,
                    "sortOrder": 3
                },
                {
                    "key": "state_province",
                    "name": "State / Province",
                    "fieldType": "TEXT",
                    "sortOrder": 4
                },
                {
                    "key": "postal_code",
                    "name": "Postal Code",
                    "fieldType": "TEXT",
                    "sortOrder": 5
                },
                {
                    "key": "country",
                    "name": "Country",
                    "fieldType": "DROPDOWN",
                    "options": [
                        { "value": "ZA", "label": "South Africa" },
                        { "value": "US", "label": "United States" },
                        { "value": "GB", "label": "United Kingdom" }
                    ],
                    "sortOrder": 6
                },
                {
                    "key": "tax_number",
                    "name": "Tax Number",
                    "fieldType": "TEXT",
                    "description": "VAT number or tax identification number",
                    "sortOrder": 7
                },
                {
                    "key": "phone",
                    "name": "Phone Number",
                    "fieldType": "PHONE",
                    "sortOrder": 8
                }
            ],
            "group": {
                "name": "Contact & Address",
                "slug": "contact-address"
            }
        }
        ```

2. **Seeding mechanism**

    - Packs are seeded during **tenant provisioning** (when a new org is created). The provisioning service reads all pack JSON files from the classpath and creates tenant-scoped FieldDefinition and FieldGroup records.
    - Each seeded FieldDefinition has `pack_id` and `pack_field_key` set, linking it back to the pack for update tracking.
    - If a pack definition changes (e.g., a new field is added in a platform update), the system can detect which fields are new by comparing `pack_field_key` values against existing records. Only new fields are added. Existing fields that were modified by the org are not overwritten.
    - A `FieldPackStatus` table (or JSONB on OrgSettings) tracks which packs have been applied and at which version: `{ packId, version, appliedAt }`.

3. **Platform-shipped packs**

    Ship these default packs (v1):

    - `common-customer`: address (line1, line2, city, state/province, postal code, country dropdown), tax number, phone number. Group: "Contact & Address."
    - `common-project`: reference number (text), priority (dropdown: Low/Medium/High/Urgent), category (text). Group: "Project Info."
    - `common-task`: (no default pack for tasks in v1 — tasks are already well-structured with status, assignee, due date).

    Vertical forks add their own packs (not shipped with the core platform, but the infrastructure supports them):
    - `legal-litigation`: case number, court, filing date, opposing counsel, case status dropdown.
    - `legal-conveyancing`: property address, transfer duty (currency), bond amount (currency), seller, buyer.
    - `accounting-engagement`: engagement type dropdown, financial year, reporting deadline (date).

4. **Pack management (admin-only, future UI)**

    - For v1, pack management is backend/config only — no frontend UI for managing packs themselves.
    - Admins manage individual field definitions through the settings UI (which may have originated from a pack or be org-created).
    - The pack tracking (`pack_id`, `pack_field_key`) is infrastructure for future features: "a new version of the Common Customer pack is available — 2 new fields added. Apply update?"

### 4. Custom field values on entities

Design the storage and access pattern for custom field values:

1. **Schema changes to Project, Task, Customer**

    - Add `custom_fields` column (JSONB, nullable, default `'{}'::jsonb`) to each entity table.
    - Add `applied_field_groups` column (JSONB, nullable — array of group UUIDs) to each entity table.
    - GIN index on `custom_fields` for each table to support filtering.
    - Flyway migrations for both tenant and shared schemas.

2. **JSONB structure**

    ```json
    {
        "case_number": "2025/12345",
        "court": "high_court_gauteng",
        "filing_date": "2025-06-15",
        "opposing_counsel": "Smith & Associates",
        "is_urgent": true,
        "estimated_value": { "amount": 500000, "currency": "ZAR" }
    }
    ```

    - Keys are field definition slugs.
    - Values are typed according to the field type (see field type semantics).
    - Unknown keys (not matching any active field definition) are preserved but ignored in the UI.

3. **Validation**

    - On create/update of a project, task, or customer, the `custom_fields` payload is validated against active field definitions for that entity type:
        - Each key must correspond to an active FieldDefinition slug.
        - Each value must match the field's type (e.g., NUMBER must be a valid number, DATE must be ISO 8601 date, DROPDOWN value must be in the options list).
        - Required fields (where `required = true`) must have a non-null value if the field is in any of the entity's `applied_field_groups`.
        - Validation errors return 400 with field-level error details: `{ field: "slug", message: "..." }`.
    - Unknown keys in the payload are silently stripped (not stored) to prevent schema pollution.

4. **API changes**

    - Existing entity CRUD endpoints (e.g., `PUT /api/projects/{id}`) accept an optional `customFields` object in the request body.
    - Entity response DTOs include `customFields` (JSONB as a map) and `appliedFieldGroups` (array of group IDs).
    - New query parameter on list endpoints: `customField[slug]=value` for filtering by custom field values. The backend translates this into a JSONB containment query (`custom_fields @> '{"slug": "value"}'`).
    - New endpoint for bulk-setting applied groups: `PUT /api/projects/{id}/field-groups` with body `{ groupIds: [...] }` — sets the entity's applied groups and returns the full set of field definitions for those groups.

### 5. Tags

Design the tagging system:

1. **Data model**

    - `Tag` entity:
        - `id` (UUID).
        - `tenant_id`.
        - `name` (VARCHAR(50) — display name, e.g., "Urgent," "VIP Client," "Q1-2026").
        - `slug` (VARCHAR(50) — URL-safe lowercase key, auto-generated from name).
        - `color` (VARCHAR(7), nullable — hex color code, e.g., "#EF4444". Null = default neutral color).
        - `created_at`, `updated_at`.
    - Constraints:
        - `(tenant_id, slug)` is unique — no duplicate tag names within an org.
    - `EntityTag` join table:
        - `id` (UUID).
        - `tenant_id`.
        - `tag_id` (UUID, FK -> tags).
        - `entity_type` (ENUM: `PROJECT`, `TASK`, `CUSTOMER`).
        - `entity_id` (UUID — polymorphic reference to the tagged entity).
        - `created_at`.
    - Constraints:
        - `(tag_id, entity_type, entity_id)` is unique — can't apply the same tag twice.
    - Indexes:
        - `(tenant_id, entity_type, entity_id)` for "get all tags for this entity."
        - `(tenant_id, tag_id, entity_type)` for "find all entities with this tag."
        - `(tenant_id, slug)` unique on Tag.

2. **Tag API**

    - `GET /api/tags` — list all tags for the org. Returns id, name, slug, color.
    - `POST /api/tags` — create a new tag. Body: `{ name, color? }`. Admin/owner only.
    - `PUT /api/tags/{id}` — update tag name or color. Admin/owner only.
    - `DELETE /api/tags/{id}` — delete a tag. Removes all EntityTag associations. Admin/owner only.
    - `POST /api/{entityType}/{entityId}/tags` — apply tags to an entity. Body: `{ tagIds: [...] }`. Replaces all tags (full replace, not add/remove).
    - `GET /api/{entityType}/{entityId}/tags` — get tags for a specific entity.

3. **Tag filtering on list endpoints**

    - Existing list endpoints (projects, tasks, customers) gain a `tags` query parameter: `tags=tag-slug-1,tag-slug-2` — returns entities that have ALL specified tags (AND logic).
    - Response DTOs for list endpoints include a `tags` array: `[{ id, name, slug, color }]`.

4. **Tag auto-complete**

    - The create/edit forms include a tag input that auto-completes from existing org tags.
    - Users with admin/owner role can create new tags inline (type a name that doesn't exist + press Enter).
    - Regular members can only select from existing tags.

### 6. Saved views

Design the saved views system:

1. **Data model**

    - `SavedView` entity:
        - `id` (UUID).
        - `tenant_id`.
        - `entity_type` (ENUM: `PROJECT`, `TASK`, `CUSTOMER` — which list page this view applies to).
        - `name` (VARCHAR(100) — display name, e.g., "My Open Tasks," "VIP Customers," "Active Litigation Matters").
        - `filters` (JSONB — the filter configuration):
            ```json
            {
                "status": ["ACTIVE", "ON_HOLD"],
                "tags": ["vip-client", "urgent"],
                "assignee": "member-uuid",
                "customFields": {
                    "court": { "op": "eq", "value": "high_court_gauteng" },
                    "filing_date": { "op": "gte", "value": "2025-01-01" },
                    "is_urgent": { "op": "eq", "value": true }
                },
                "dateRange": {
                    "field": "created_at",
                    "from": "2025-01-01",
                    "to": "2025-12-31"
                },
                "search": "keyword"
            }
            ```
        - `columns` (JSONB, nullable — which columns to display and their order. Null = default columns):
            ```json
            ["name", "status", "customer", "cf:case_number", "cf:court", "tags", "updated_at"]
            ```
            Custom field columns use the `cf:` prefix followed by the field slug.
        - `shared` (BOOLEAN, default false — if true, visible to all org members; if false, only visible to the creator).
        - `created_by` (UUID — member who created the view).
        - `sort_order` (INTEGER — display order in the view switcher).
        - `created_at`, `updated_at`.
    - Constraints:
        - `(tenant_id, entity_type, name, created_by)` should be unique for personal views.
        - Shared views: `(tenant_id, entity_type, name)` should be unique.
    - Indexes:
        - `(tenant_id, entity_type, shared)` for listing available views.
        - `(tenant_id, created_by, entity_type)` for listing personal views.

2. **Saved view API**

    - `GET /api/views?entityType=PROJECT` — list saved views for the entity type. Returns: shared views + the requesting user's personal views.
    - `POST /api/views` — create a saved view. Body: `{ entityType, name, filters, columns?, shared? }`. `shared` requires admin/owner role.
    - `PUT /api/views/{id}` — update a saved view. Only the creator (personal) or admin/owner (shared) can edit.
    - `DELETE /api/views/{id}` — delete a saved view. Only the creator (personal) or admin/owner (shared) can delete.

3. **Frontend integration**

    - Each list page (projects, tasks, customers) gains a "views" dropdown/tabs above the list.
    - Default views (hardcoded): "All" (no filters), plus any saved views.
    - Selecting a view applies its filters and columns to the list.
    - A "Save View" button appears when filters are active but don't match a saved view.
    - The current filter state is serialized to URL search params for shareability (e.g., `?view=saved-view-id` or `?status=ACTIVE&tags=urgent`).

### 7. Frontend — field management settings

Design the admin UI for managing field definitions and groups:

1. **Settings page: Custom Fields** (`/org/[slug]/settings/custom-fields`)

    - Tab per entity type: Projects | Tasks | Customers.
    - Each tab shows:
        - **Field Groups section**: list of groups with expand/collapse. Each group shows its fields. "Add Group" button. Edit/delete/reorder groups.
        - **Ungrouped Fields section**: fields not in any group. "Add Field" button.
    - **Add/Edit Field dialog**:
        - Fields: Name, Slug (auto-generated, editable), Type (dropdown), Description, Required (checkbox), Default Value (type-appropriate input), Options (for DROPDOWN — dynamic list of value/label pairs), Validation rules (conditional on type).
        - Pack origin badge: if the field came from a pack, show "From: Common Customer Pack" with a visual indicator. Pack-origin fields can be edited but show a "customized" badge.
    - **Add/Edit Group dialog**:
        - Fields: Name, Description.
        - Field selection: multi-select from existing field definitions for the entity type.
        - Reorder fields within the group (up/down buttons).

2. **Settings page: Tags** (`/org/[slug]/settings/tags`)

    - List of all org tags with color swatch, name, and usage count (how many entities use this tag).
    - "Add Tag" button.
    - Edit tag name/color inline or via dialog.
    - Delete tag with confirmation (warns about entity count affected).

3. **Entity detail views — custom fields rendering**

    - Project detail, task detail, and customer detail views gain a "Custom Fields" section.
    - Fields are rendered grouped by FieldGroup, with ungrouped fields in a separate section.
    - Each field renders with the appropriate input component based on its type.
    - Empty optional fields show a placeholder "Add value" link.
    - A "Manage Fields" link (admin/owner only) navigates to the settings page.

4. **Entity detail views — tags rendering**

    - Tags displayed as colored badges below the entity title or in a sidebar section.
    - Click to add/remove tags via a popover with auto-complete search.

5. **List views — custom columns and tag badges**

    - List pages render tag badges in each row.
    - When a saved view specifies custom field columns (`cf:slug`), those columns appear in the table.
    - Custom field column values are rendered as plain text (no inline editing in list views).

### 8. API endpoints summary

Full endpoint specification:

1. **Field definitions**

    - `GET /api/field-definitions?entityType=PROJECT` — list active field definitions for an entity type.
    - `GET /api/field-definitions/{id}` — get a single field definition.
    - `POST /api/field-definitions` — create a field definition. Admin/owner only.
    - `PUT /api/field-definitions/{id}` — update a field definition. Admin/owner only.
    - `DELETE /api/field-definitions/{id}` — soft-delete (set active=false). Admin/owner only.

2. **Field groups**

    - `GET /api/field-groups?entityType=PROJECT` — list active groups with their field members.
    - `POST /api/field-groups` — create a group with initial field members. Admin/owner only.
    - `PUT /api/field-groups/{id}` — update group name/description and field membership. Admin/owner only.
    - `DELETE /api/field-groups/{id}` — soft-delete. Admin/owner only. Does not delete member field definitions.

3. **Tags**

    - `GET /api/tags` — list all org tags.
    - `POST /api/tags` — create a tag. Admin/owner only.
    - `PUT /api/tags/{id}` — update a tag. Admin/owner only.
    - `DELETE /api/tags/{id}` — delete a tag and all associations. Admin/owner only.
    - `POST /api/{entityType}/{entityId}/tags` — set tags on an entity. Body: `{ tagIds: [...] }`.
    - `GET /api/{entityType}/{entityId}/tags` — get tags for an entity.

4. **Saved views**

    - `GET /api/views?entityType=PROJECT` — list available views (shared + personal).
    - `POST /api/views` — create a saved view.
    - `PUT /api/views/{id}` — update a saved view.
    - `DELETE /api/views/{id}` — delete a saved view.

5. **Custom field values** (on existing entity endpoints)

    - All existing entity CRUD endpoints accept `customFields` in request body and return it in responses.
    - All existing entity list endpoints accept `customField[slug]=value` query params for filtering.
    - `PUT /api/{entityType}/{entityId}/field-groups` — set applied field groups.

For each endpoint specify:
- Auth requirement (valid Clerk JWT, appropriate role).
- Tenant scoping.
- Permission checks.
- Request/response DTOs.

### 9. Notification integration

- **FIELD_DEFINITION_CREATED**: No notification (admin action, visible immediately in settings).
- **TAG_APPLIED**: No notification in v1 (too noisy). Future consideration: notify entity assignee when a specific tag is applied (e.g., "urgent").

### 10. Audit integration

Publish audit events for:
- `FIELD_DEFINITION_CREATED` — new field created, includes field name, type, entity type.
- `FIELD_DEFINITION_UPDATED` — field modified, includes changed attributes.
- `FIELD_DEFINITION_DELETED` — field deactivated.
- `FIELD_GROUP_CREATED` — new group created.
- `FIELD_GROUP_UPDATED` — group modified.
- `FIELD_GROUP_DELETED` — group deactivated.
- `TAG_CREATED` — new tag created.
- `TAG_DELETED` — tag deleted.
- Custom field value changes on entities are captured as part of the entity's existing audit events (e.g., `PROJECT_UPDATED` already fires — the audit detail JSONB includes the custom fields diff).

### 11. ADRs for key decisions

Add ADR-style sections for:

1. **JSONB vs. EAV for custom field storage**:
    - Why JSONB on the entity table (not a separate key-value table).
    - Performance characteristics: GIN index for containment queries, no join explosion.
    - Trade-offs: schema-on-read flexibility vs. no FK enforcement on values.
    - Migration path: if query patterns change, GIN indexes can be replaced with expression indexes on specific fields.
    - How this interacts with Hibernate @Filter for multitenant isolation (JSONB columns are just regular columns — filter applies normally).

2. **Field pack seeding strategy**:
    - Why packs are seeded per-tenant (not shared/global read-only definitions).
    - Why per-tenant copies allow independent customization without affecting other tenants.
    - The update problem: how to add new pack fields to existing tenants without clobbering customizations.
    - Version tracking via FieldPackStatus.
    - How vertical forks add domain packs (classpath resource, picked up by the seeding service).

3. **Tag storage: join table vs. array column**:
    - Why a join table (`EntityTag`) instead of a JSONB array or `text[]` column on the entity.
    - The join table enables: tag-based filtering with standard SQL JOINs, referential integrity (cascade delete), usage counting, and cross-entity-type tag queries.
    - Trade-off: slightly more complex writes (insert/delete join rows) vs. much simpler reads and queries.
    - The polymorphic `entity_type + entity_id` pattern and its limitations (no FK to the actual entity — relies on application-level integrity).

4. **Saved view filter execution**:
    - How filter JSONB is translated to SQL query predicates.
    - Why filters are executed server-side (not client-side) for consistent pagination and performance.
    - The custom field filter uses JSONB containment operators (`@>`, `->>`, etc.) — covered by GIN index.
    - How tag filters use EXISTS subqueries on the EntityTag join table.
    - Why column configuration is stored but rendered client-side (the backend returns all data; the frontend selects which columns to display).

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Keep the design **generic and industry-agnostic**. The field definition system is a platform primitive — it knows nothing about legal matters, accounting engagements, or agency campaigns. Domain-specific knowledge lives in field packs, which are seed data, not code.
- Field packs are the extensibility seam for vertical forks. The core platform ships a minimal set of "common" packs. Each fork adds its domain packs as JSON resources — no code changes to the field system itself.
- JSONB is the right storage trade-off at this scale (hundreds of field definitions per tenant, not millions). If a tenant has >10,000 entities with custom fields, GIN indexes handle the query load. EAV would be premature optimization that adds complexity.
- Tags are deliberately simple: flat namespace, freeform text, optional color. No tag hierarchy, no tag types, no tag metadata beyond color. This covers 90% of use cases. The remaining 10% (tag groups, structured taxonomies) can be added later without schema changes.
- Saved views are a frontend-centric feature backed by a thin persistence layer. The backend stores the filter configuration; the frontend interprets and renders it. This keeps the backend simple and avoids encoding UI concerns (column widths, sort directions, grouping) in the API.
- All new entities follow the existing tenant isolation model (dedicated schema for Pro, shared schema with @Filter + RLS for Starter). No exceptions.
- Frontend additions use the existing Shadcn UI component library and olive design system. Field type inputs reuse existing form components (Input, Select, DatePicker, Checkbox) — no new design primitives needed.
- The `applied_field_groups` on entities is a UX convenience, not a hard constraint. The custom_fields JSONB can contain values for any active field definition, regardless of group membership. Groups just determine which fields are shown in the form.

Return a single markdown document as your answer, ready to be added as `architecture/phase11-tags-custom-fields-views.md` and ADRs.
