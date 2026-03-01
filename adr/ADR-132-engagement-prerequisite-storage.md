# ADR-132: Engagement Prerequisite Storage — JSONB Array vs. Join Table

**Status**: Accepted

**Context**:

Phase 33 introduces engagement-level prerequisites: a project template can declare that certain customer fields must be filled before a project of that type can be created for a customer. For example, an "Annual Tax Return" template requires the customer to have a SARS tax reference number and a tax year end date. This association between project templates and required customer field definitions needs to be persisted and queried efficiently.

The association is fundamentally many-to-many: a project template can require multiple field definitions, and a field definition can be required by multiple templates. However, the access patterns are asymmetric. The primary query is template-centric: "given this template, which customer fields are required?" The reverse query (given a field definition, which templates require it?) is only needed for admin UI presentation (showing where a field is used) and is infrequent. The cardinality is low — a typical template requires 2-8 customer fields, and an org might have 5-20 templates.

DocTeams uses JSONB storage extensively for similar low-cardinality associations: `FieldGroup.dependsOn` (JSONB UUID array), `FieldDefinition.requiredForContexts` (JSONB string array, introduced in this phase), `OrgSettings.fieldPackStatus` (JSONB map), and `Customer.customFields` (JSONB map). The schema-per-tenant model means each tenant has its own `project_templates` table, so cross-tenant query considerations do not apply.

**Options Considered**:

1. **JSONB UUID array on `project_templates`** -- Add a `required_customer_field_ids JSONB DEFAULT '[]'` column to the `project_templates` table. Store an array of `FieldDefinition` UUIDs directly on the template.
   - Pros:
     - Single-table query: `SELECT required_customer_field_ids FROM project_templates WHERE id = ?` retrieves the association in one query
     - No join needed for the primary access pattern (template → required fields)
     - Consistent with existing JSONB patterns in the codebase (`dependsOn`, `requiredForContexts`)
     - Simple migration: one `ALTER TABLE ADD COLUMN`
     - No additional entity class or repository needed
     - Low cardinality (2-8 UUIDs) fits well in JSONB
   - Cons:
     - No referential integrity — deleted field definitions leave orphan UUIDs in the array
     - Reverse query (field → templates) requires full-table scan with JSONB contains (`@>`)
     - Cannot use JPA `@ManyToMany` mapping — must handle UUID resolution manually in service layer
     - Array manipulation (add/remove field) requires loading, modifying, and saving the entire array

2. **Join table (`project_template_required_fields`)** -- Create a new table with `(project_template_id, field_definition_id)` composite key and foreign key constraints to both tables.
   - Pros:
     - Referential integrity enforced by the database (cascading deletes possible)
     - Both forward and reverse queries are efficient with indexes
     - Standard JPA `@ManyToMany` mapping with `@JoinTable`
     - Individual field add/remove operations are single INSERT/DELETE statements
   - Cons:
     - Requires a new migration table, new entity or mapping annotation, new repository (or mapping on existing entity)
     - Additional join for the primary query (template → fields)
     - Inconsistent with the codebase's existing JSONB-based association patterns
     - More complex migration (CREATE TABLE + indexes + foreign keys)
     - `@ManyToMany` with JPA in schema-per-tenant requires careful handling of entity loading

3. **Embedded field pack reference** -- Instead of referencing individual field definitions, store a reference to a field group or pack. The template declares "requires the FICA pack" rather than listing individual fields.
   - Pros:
     - Higher-level abstraction — template requirements track pack updates automatically
     - Fewer IDs to store (one pack reference vs. multiple field IDs)
     - Semantic clarity: "this engagement requires FICA compliance"
   - Cons:
     - Too coarse-grained — a template might need 2 fields from FICA and 1 from billing, not the entire packs
     - Pack membership can change; adding a field to a pack silently adds a new requirement to all templates referencing that pack
     - Not all customer fields are in packs — org-created custom fields would need a separate mechanism
     - Mixes concerns: field packs are for organizing fields, not for declaring prerequisites

**Decision**: Option 1 -- JSONB UUID array on `project_templates`.

**Rationale**:

The JSONB array approach is the best fit for this association given DocTeams' existing patterns, the access profile, and the cardinality constraints. The primary access pattern (template → required field IDs) is a single-column read with no join. The cardinality is inherently low (a template rarely requires more than 10 customer fields), so the array fits comfortably in a JSONB column. And critically, the codebase already uses this exact pattern for `FieldGroup.dependsOn` and `FieldDefinition.requiredForContexts` — introducing a join table for this one association would be inconsistent.

The referential integrity concern is manageable. When a field definition is deactivated or deleted, the `FieldDefinitionService` can include a cleanup step that removes the field's ID from any `ProjectTemplate.requiredCustomerFieldIds` arrays. This is a single UPDATE query per deactivation and is consistent with how `FieldGroup.dependsOn` handles member removal. The reverse query (field → templates) is infrequent enough that a JSONB `@>` scan across 5-20 templates is negligible.

The join table approach was not chosen because it would be the only `@ManyToMany` JPA mapping in the project. All other associations use either JSONB (for low-cardinality metadata) or explicit join entities with additional fields (e.g., `FieldGroupMember` with `sortOrder`, `CustomerProject` with project metadata). A pure join table with no additional fields is better modeled as JSONB in this codebase.

The embedded pack reference was rejected because it conflates field organization with prerequisite declaration. A template should be able to cherry-pick individual fields from different groups without being coupled to pack membership. If the FICA pack gains a new field, existing templates should not silently gain a new requirement.

**Consequences**:

- `ProjectTemplate` entity gains `requiredCustomerFieldIds` field: `List<UUID>` mapped to JSONB
- V53 migration adds `required_customer_field_ids JSONB NOT NULL DEFAULT '[]'` to `project_templates`
- `ProjectTemplateService` handles UUID resolution: loads `FieldDefinition` records by ID for prerequisite evaluation
- `FieldDefinitionService` includes a cleanup method called on field deactivation: removes the field's UUID from all templates' `requiredCustomerFieldIds`
- No new JPA entity or repository for the association
- Reverse lookup (field → templates using it) uses native query: `SELECT * FROM project_templates WHERE required_customer_field_ids @> '["uuid"]'::jsonb`
- Related: [ADR-131](ADR-131-prerequisite-context-granularity.md) (context-based requirements are stored on `FieldDefinition`, not on `ProjectTemplate` — the two mechanisms are orthogonal)
