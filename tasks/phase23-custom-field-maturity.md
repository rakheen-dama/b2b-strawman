# Phase 23 — Custom Field Maturity & Data Integrity

Phase 23 hardens the custom fields system from "data entry convenience" to "data integrity enforcement". It extends the custom fields infrastructure (Phase 11) and readiness checks (Phase 15) with auto-apply field groups, field group dependencies, conditional field visibility, template-declared required fields with generation validation, invoice custom fields, a task field pack, billable time rate warnings, and three bug fixes. All changes are tenant-scoped -- the single tenant migration V38 handles all DDL changes.

**Architecture doc**: `architecture/phase23-custom-field-maturity.md`

**ADRs**:
- [ADR-092](../adr/ADR-092-auto-apply-strategy.md) -- Auto-Apply Retroactive Strategy (synchronous JSONB append)
- [ADR-093](../adr/ADR-093-template-required-fields.md) -- Template Required Field References (soft slug-based refs)
- [ADR-094](../adr/ADR-094-conditional-field-visibility.md) -- Conditional Visibility Evaluation (frontend + backend, preserve values)

**Migration**: V38 tenant -- `auto_apply` + `depends_on` on `field_groups`, `visibility_condition` on `field_definitions`, `required_context_fields` on `document_templates`, `custom_fields` + `applied_field_groups` on `invoices`, GIN index + partial index.

**Dependencies on prior phases**: Phase 8 (Rate Cards), Phase 10 (Invoicing), Phase 11 (Custom Fields), Phase 12 (Document Templates), Phase 15 (Setup Guidance).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 161 | Auto-Apply Field Groups & V38 Migration | Backend | -- | L | 161A, 161B | **Done** (PRs #333, #334) |
| 162 | Field Group Dependencies | Backend + Frontend | 161 | S | 162A | **Done** (PR #335) |
| 163 | Conditional Field Visibility | Backend + Frontend | 161 | M | 163A, 163B, 163C | **Done** (PRs #336, #337, #338) |
| 164 | Invoice Custom Fields & Task Pack | Backend + Frontend | 161 | M | 164A, 164B, 164C | **Done** (PRs #339, #340, #341) |
| 165 | Template Required Fields & Generation Validation | Backend + Frontend | 161, 164A | L | 165A, 165B, 165C | |
| 166 | Rate Warnings & Bug Fixes | Backend + Frontend | -- | M | 166A, 166B | |

---

## Dependency Graph

```
                          [E161A V38 + Auto-Apply Backend] ------> [E161B Auto-Apply Entity Creation Integration]
                                    |
          +-------------------------+------------------------------+-------------------------------+
          |                         |                              |                               |
    [E162A Group Deps]   [E163A Visibility Backend]   [E164A Invoice CF Backend]   [E165A Template Req Fields Backend]
          |                         |                        |         |                           |
          |              [E163B Visibility Frontend]  [E164B Invoice CF Frontend]  [E165B Template Req Fields Frontend]
          |                         |                        |                                    |
          |              [E163C Visibility Editor]    [E164C Task Pack]            [E165C Invoice Gen Validation]
          |                                                                              (needs 164A, 165A)
          |
[E166A Rate Warnings] .............. (parallel, no deps)
[E166B Bug Fixes] .................. (parallel, no deps)
```

**Parallel opportunities**:
- Epics 166A and 166B are fully independent -- can start immediately, parallel with everything.
- After 161A+161B complete: Epics 162, 163, 164, and 165A can start in parallel.
- 163B depends on 163A. 163C depends on 163B.
- 164B depends on 164A. 164C depends on 161B (auto-apply for pack seeding).
- 165B depends on 165A. 165C depends on 165A and 164A.

---

## Implementation Order

### Stage 0: Independent bug fixes and rate warnings (parallel with everything)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 0a (parallel) | 166 | 166A | Billable time rate warnings -- backend response DTO + LogTimeDialog warning banner |
| 0b (parallel) | 166 | 166B | Bug fixes -- DATE validation, CURRENCY blankness, field type immutability |

### Stage 1: Foundation

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 1a | 161 | 161A | V38 migration (all DDL), FieldGroup entity updates (`autoApply`, `dependsOn`), `FieldGroupRepository` query, `FieldGroupService` toggle/retroactive apply, `PATCH` endpoint, pack seeder update, pack JSON updates. Backend only. | **Done** (PR #333) |
| 1b | 161 | 161B | Auto-apply integration into entity creation services (`CustomerService`, `ProjectService`, `TaskService`), settings UI auto-apply toggle on field group dialog. Backend + Frontend. | **Done** (PR #334) |

### Stage 2: Parallel feature tracks (after Stage 1)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 2a (parallel) | 162 | 162A | Field group dependencies -- backend validation, dependency resolution in group application, frontend dependency selector in edit dialog | **Done** (PR #335) |
| 2b (parallel) | 163 | 163A | Conditional visibility backend -- `FieldDefinition.visibilityCondition`, `CustomFieldValidator` visibility evaluation, condition validation at save time | **Done** (PR #336) |
| 2c (parallel) | 164 | 164A | Invoice custom fields backend -- `EntityType.INVOICE`, `Invoice` entity columns, custom field + field group endpoints, `InvoiceContextBuilder` extension, auto-apply integration | **Done** (PR #339) |
| 2d (parallel) | 165 | 165A | Template required fields backend -- `DocumentTemplate.requiredContextFields`, `TemplateValidationService`, preview/generate endpoint extensions | **Done** (PR #342) |

### Stage 3: Frontend integration (after respective Stage 2 backend slices)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 3a (parallel) | 163 | 163B | Conditional visibility frontend -- `CustomFieldSection` `isFieldVisible()`, reactive show/hide, `validateField` skips hidden | **Done** (PR #337) |
| 3b (parallel) | 164 | 164B | Invoice custom fields frontend -- invoice detail page `CustomFieldSection` + `FieldGroupSelector`, settings INVOICE tab | **Done** (PR #340) |
| 3c (parallel) | 164 | 164C | Task field pack -- `common-task.json`, `FieldPackSeeder` `autoApply` processing | **Done** (PR #341) |
| 3d (parallel) | 165 | 165B | Template required fields frontend -- template editor field selector, generation dialog validation display |

### Stage 4: Final integration

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 4a | 163 | 163C | Visibility condition editor UI in field definition dialog | **Done** (PR #338) |
| 4b | 165 | 165C | Invoice generation validation checklist + invoice send validation with override |

### Timeline

```
Stage 0: [166A] // [166B]                                (parallel, immediate)
Stage 1: [161A] --> [161B]                               (sequential)
Stage 2: [162A] // [163A] // [164A] // [165A]            (parallel, after 161B)
Stage 3: [163B] // [164B] // [164C] // [165B]            (parallel, after respective Stage 2)
Stage 4: [163C] // [165C]                                (parallel, after Stage 3)
```

**Critical path**: 161A --> 161B --> 165A --> 165B --> 165C

---

## Epic 161: Auto-Apply Field Groups & V38 Migration

**Goal**: Create the V38 tenant migration with ALL DDL changes for Phase 23. Implement the auto-apply flag on `FieldGroup` with retroactive apply via synchronous JSONB array append. Integrate auto-apply into entity creation services. Expose auto-apply toggle in the settings UI.

**References**: Architecture doc Sections 23.2.1 (FieldGroup entity), 23.3.1 (auto-apply on creation), 23.3.2 (retroactive apply), 23.7 (V38 migration DDL), 23.8.1 (backend changes table).

**Dependencies**: None -- this is the foundation epic. Creates V38 migration used by all other epics.

**Scope**: Backend + Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **161A** | 161.1--161.9 | V38 migration (8 DDL statements) + `FieldGroup` entity updates (`autoApply`, `dependsOn` fields) + `FieldGroupRepository` query method + `FieldGroupService.toggleAutoApply()` with retroactive apply + `PATCH /api/field-groups/{id}/auto-apply` endpoint + updated POST/PUT field group endpoints + `FieldPackSeeder` autoApply support + pack JSON updates + integration tests. ~6 new/modified backend files, ~2 resource files. Backend only. | **Done** (PR #333) |
| **161B** | 161.10--161.15 | Auto-apply integration into `CustomerService.create()`, `ProjectService.create()`, `TaskService.create()` + `FieldGroupService.resolveAutoApplyGroupIds()` + settings UI auto-apply toggle on field group create/edit dialog + frontend type updates + integration tests for auto-apply on creation. ~3 modified backend files, ~3 modified frontend files. | **Done** (PR #334) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 161.1 | Create V38 tenant migration | 161A | | New file: `backend/src/main/resources/db/migration/tenant/V38__custom_field_maturity.sql`. Contains ALL Phase 23 DDL: (1) `ALTER TABLE field_groups ADD COLUMN auto_apply BOOLEAN NOT NULL DEFAULT false`, (2) `ADD COLUMN depends_on JSONB`, (3) `ALTER TABLE field_definitions ADD COLUMN visibility_condition JSONB`, (4) `ALTER TABLE document_templates ADD COLUMN required_context_fields JSONB`, (5) `ALTER TABLE invoices ADD COLUMN custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb`, (6) `ADD COLUMN applied_field_groups JSONB NOT NULL DEFAULT '[]'::jsonb`, (7) GIN index on `invoices.custom_fields`, (8) partial index on `field_groups(entity_type, auto_apply)`. Exact SQL in architecture doc Section 23.7. |
| 161.2 | Add `autoApply` and `dependsOn` fields to `FieldGroup` entity | 161A | 161.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java`. Add `@Column(name = "auto_apply", nullable = false) private boolean autoApply;` (default false in constructor). Add `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "depends_on", columnDefinition = "jsonb") private List<UUID> dependsOn;` (default null). Add getters/setters. Pattern: existing JSONB field pattern on `FieldDefinition.options`. |
| 161.3 | Add `findByEntityTypeAndAutoApplyTrueAndActiveTrue` to `FieldGroupRepository` | 161A | 161.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupRepository.java`. Add `List<FieldGroup> findByEntityTypeAndAutoApplyTrueAndActiveTrue(EntityType entityType);`. Spring Data derived query. |
| 161.4 | Add `toggleAutoApply()` and `retroactiveApply()` to `FieldGroupService` | 161A | 161.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java`. Add `toggleAutoApply(UUID groupId, boolean autoApply)` -- if toggling from false to true, call `retroactiveApply(group)`. Add `retroactiveApply(FieldGroup group)` -- uses `EntityManager.createNativeQuery()` with JSONB `||` append and `@>` containment check. Add `entityTableName(EntityType)` helper returning table name. See architecture doc Section 23.3.2 for exact SQL. |
| 161.5 | Add `PATCH /{id}/auto-apply` endpoint to `FieldGroupController` | 161A | 161.4 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupController.java`. Add `@PatchMapping("/{id}/auto-apply")` with `@PreAuthorize("hasAnyRole('org:admin','org:owner')")`. Request body: `record ToggleAutoApplyRequest(boolean autoApply) {}`. Delegates to `FieldGroupService.toggleAutoApply()`. Returns updated `FieldGroup` response. |
| 161.6 | Update POST/PUT field group endpoints to accept `autoApply` | 161A | 161.2 | Modify: `FieldGroupController.java`. Update `CreateFieldGroupRequest` and `UpdateFieldGroupRequest` DTOs to include optional `autoApply` (default false). Wire through to entity on create/update. |
| 161.7 | Update `FieldPackSeeder` to read `autoApply` from pack JSON | 161A | 161.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`. When creating `FieldGroup` from pack group definition, check for `"autoApply"` field in JSON and set on entity. Update `FieldPackGroup.java` (or equivalent DTO) to include `autoApply` field. |
| 161.8 | Update existing pack JSON files with `autoApply: true` | 161A | 161.7 | Modify: `backend/src/main/resources/field-packs/common-customer.json` -- add `"autoApply": true` to each group definition. Modify: `backend/src/main/resources/field-packs/common-project.json` -- add `"autoApply": true` to each group definition. |
| 161.9 | Write integration tests for auto-apply toggle and retroactive apply | 161A | 161.4, 161.5 | New file: `backend/src/test/java/.../fielddefinition/FieldGroupAutoApplyIntegrationTest.java`. Tests: (1) `toggle_autoApply_updates_field_group`, (2) `retroactive_apply_adds_group_to_existing_entities`, (3) `retroactive_apply_skips_entities_already_having_group`, (4) `toggle_autoApply_off_does_not_remove_from_entities`, (5) `patch_endpoint_requires_admin_role`. ~5 integration tests. Pattern: existing `FieldGroupControllerIntegrationTest` (or similar). Use `TestCustomerFactory.createActiveCustomer()` for customer test data. |
| 161.10 | Add `resolveAutoApplyGroupIds()` to `FieldGroupService` | 161B | 161A | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java`. Add `resolveAutoApplyGroupIds(EntityType entityType)` that calls `findAutoApplyGroups()`, collects group IDs, and resolves one-level dependencies from `dependsOn`. Returns `List<UUID>`. See architecture doc Section 23.3.1 for exact implementation. |
| 161.11 | Integrate auto-apply into `CustomerService.create()` | 161B | 161.10 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java`. After entity save, call `fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER)` and set on entity's `appliedFieldGroups`. Save again. Inject `FieldGroupService`. |
| 161.12 | Integrate auto-apply into `ProjectService.create()` and `TaskService.create()` | 161B | 161.10 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` and `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java`. Same pattern as 161.11. Inject `FieldGroupService`, call `resolveAutoApplyGroupIds()` during create. |
| 161.13 | Add auto-apply toggle to field group dialog in settings UI | 161B | 161.6 | Modify: `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` (or the field group dialog component). Add a checkbox/switch for `autoApply` in the create and edit field group dialog. Wire to API request body. Show label "Auto-apply to new entities" with helper text. Pattern: existing toggle/checkbox in field group dialog. |
| 161.14 | Update frontend API types for `autoApply` and `dependsOn` | 161B | | Modify: `frontend/lib/api/field-definitions.ts` (or equivalent type file). Add `autoApply: boolean` and `dependsOn: string[] | null` to `FieldGroupResponse` type. Add `autoApply?: boolean` to create/update request types. |
| 161.15 | Write integration tests for auto-apply on entity creation | 161B | 161.11, 161.12 | New file or extend existing: `backend/src/test/java/.../fielddefinition/AutoApplyOnCreationIntegrationTest.java`. Tests: (1) `new_customer_gets_auto_apply_groups`, (2) `new_project_gets_auto_apply_groups`, (3) `new_task_gets_auto_apply_groups`, (4) `entity_with_no_auto_apply_groups_has_empty_list`. ~4 integration tests. |

### Key Files

**Slice 161A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V38__custom_field_maturity.sql` -- All Phase 23 DDL (8 statements)
- `backend/src/test/java/.../fielddefinition/FieldGroupAutoApplyIntegrationTest.java` -- 5 integration tests

**Slice 161A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java` -- Add `autoApply`, `dependsOn` fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupRepository.java` -- Add query method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` -- Toggle, retroactive apply
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupController.java` -- PATCH endpoint, DTO updates
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java` -- Read autoApply from JSON
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackGroup.java` -- Add autoApply field
- `backend/src/main/resources/field-packs/common-customer.json` -- Add `"autoApply": true`
- `backend/src/main/resources/field-packs/common-project.json` -- Add `"autoApply": true`

**Slice 161B -- Create:**
- `backend/src/test/java/.../fielddefinition/AutoApplyOnCreationIntegrationTest.java` -- 4 integration tests

**Slice 161B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` -- Add `resolveAutoApplyGroupIds()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` -- Auto-apply on create
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` -- Auto-apply on create
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- Auto-apply on create
- `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` -- Auto-apply toggle
- `frontend/lib/api/field-definitions.ts` -- Type updates

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java` -- Existing entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java` -- Existing seeder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` -- Where to inject auto-apply
- `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` -- Existing dialog pattern

### Architecture Decisions

- **V38 migration includes ALL Phase 23 DDL**: All `ALTER TABLE` and `CREATE INDEX` statements for all epics are in a single migration. Only Slice 161A creates V38 -- all other slices consume the columns it creates. This prevents migration ordering issues.
- **Retroactive apply is synchronous**: See ADR-092. When `autoApply` is toggled to `true`, a single `UPDATE ... SET applied_field_groups = applied_field_groups || ... WHERE NOT applied_field_groups @> ...` query is executed per entity type. Safe for schema-per-tenant (bounded row count).
- **Pack JSON `autoApply` field**: The `FieldPackSeeder` reads `autoApply` from the pack JSON group definition and sets it on the created `FieldGroup`. Existing packs updated to `true`.

---

## Epic 162: Field Group Dependencies

**Goal**: Implement one-level field group dependencies -- when a group is applied to an entity, its `dependsOn` groups are co-applied. Validation prevents self-references and mutual dependencies. Frontend settings UI allows configuring dependencies.

**References**: Architecture doc Sections 23.3.3 (dependency resolution), 23.4.1 (modified endpoints).

**Dependencies**: Epic 161 (V38 migration creates `depends_on` column, `FieldGroup` entity has the field).

**Scope**: Backend + Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **162A** | 162.1--162.7 | Dependency validation in `FieldGroupService` (same entityType, no self-ref, no mutual dep) + dependency resolution in field group application endpoint + updated `FieldGroupSelector` to handle deps from server response + dependency selector in field group edit dialog (multi-select) + integration tests + frontend tests. ~3 modified backend files, ~3 modified frontend files. | **Done** (PR #335) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 162.1 | Add dependency validation to `FieldGroupService` | 162A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java`. In create/update methods, validate `dependsOn` list: (1) all IDs must reference active field groups of same `entityType`, (2) no self-references, (3) no mutual dependency (A depends on B and B depends on A). Throw `InvalidStateException` on violation. |
| 162.2 | Add dependency resolution to field group application | 162A | 162.1 | Modify: `FieldGroupService.java` (or the service that handles `PUT /api/{entityType}/{id}/field-groups`). When a group with `dependsOn` is applied, add missing dependency group IDs to the entity's `appliedFieldGroups`. One level only -- no cascading. Return the full updated list. |
| 162.3 | Update field group create/update endpoints for `dependsOn` | 162A | 162.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupController.java`. Update `CreateFieldGroupRequest` and `UpdateFieldGroupRequest` DTOs to include optional `dependsOn` (List<UUID>). Wire through to service. Response includes `dependsOn`. |
| 162.4 | Add dependency selector to field group edit dialog | 162A | 162.3 | Modify: `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx`. In the field group create/edit dialog, add a multi-select for `dependsOn` -- populated with other active groups of the same `entityType`. Label: "Depends on (co-apply when this group is applied)". Pattern: Shadcn multi-select or combobox. |
| 162.5 | Update `FieldGroupSelector` to handle dependency resolution from server | 162A | 162.2 | Modify: `frontend/components/field-definitions/FieldGroupSelector.tsx`. After applying a group via `PUT /{entityType}/{id}/field-groups`, the response returns the full `appliedFieldGroups` including resolved deps. Re-render from the response -- no client-side resolution needed. Ensure the selector shows newly applied dependency groups. |
| 162.6 | Write integration tests for field group dependencies | 162A | 162.2, 162.3 | New file: `backend/src/test/java/.../fielddefinition/FieldGroupDependencyIntegrationTest.java`. Tests: (1) `apply_group_with_deps_also_applies_dependency_groups`, (2) `one_level_only_no_cascade`, (3) `circular_dependency_rejected`, (4) `self_reference_rejected`, (5) `remove_dependency_group_allowed`. ~5 tests. |
| 162.7 | Write frontend tests for dependency selector | 162A | 162.4 | Add tests to `frontend/__tests__/field-definitions/FieldGroupDialog.test.tsx` (or new file). Tests: (1) `renders_dependency_selector_with_same_entity_type_groups`, (2) `excludes_self_from_dependency_options`. ~2 tests. |

### Key Files

**Slice 162A -- Create:**
- `backend/src/test/java/.../fielddefinition/FieldGroupDependencyIntegrationTest.java` -- 5 integration tests

**Slice 162A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` -- Validation + resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupController.java` -- DTO updates
- `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` -- Dependency selector
- `frontend/components/field-definitions/FieldGroupSelector.tsx` -- Handle server-resolved deps
- `frontend/__tests__/field-definitions/FieldGroupDialog.test.tsx` -- 2 tests

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` -- Existing service logic
- `frontend/components/field-definitions/FieldGroupSelector.tsx` -- Existing selector pattern

### Architecture Decisions

- **One level only**: If A depends on B and B depends on C, applying A applies A and B -- NOT C. This is intentional simplicity.
- **Dependencies are a convenience, not a constraint**: Removing a co-applied dependency group is allowed. The system does not re-apply or block removal.
- **Server-side resolution**: The `PUT /{entityType}/{id}/field-groups` endpoint resolves dependencies server-side. The frontend re-renders from the response -- no client-side dependency logic.

---

## Epic 163: Conditional Field Visibility

**Goal**: Add conditional visibility to custom field definitions. Fields can be shown/hidden based on another field's value. Both frontend and backend evaluate conditions. Hidden field values are preserved. The field definition settings dialog gets a visibility condition editor.

**References**: Architecture doc Sections 23.2.2 (FieldDefinition entity), 23.3.4 (visibility evaluation), 23.6 (deep dive), ADR-094.

**Dependencies**: Epic 161 (V38 migration creates `visibility_condition` column).

**Scope**: Backend + Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **163A** | 163.1--163.6 | Backend: `FieldDefinition.visibilityCondition` field, `CustomFieldValidator.isFieldVisible()`, condition validation at save time (same entityType, no self-ref), updated POST/PUT field definition endpoints, integration tests. ~4 modified backend files. | **Done** (PR #336) |
| **163B** | 163.7--163.11 | Frontend: `CustomFieldSection` `isFieldVisible()`, reactive show/hide, `validateField` skips hidden fields, frontend unit tests. ~2 modified frontend files, ~1 test file. | **Done** (PR #337) |
| **163C** | 163.12--163.16 | Frontend: Visibility condition editor UI in field definition dialog -- controlling field selector, operator selector, value input, preview, clear button, frontend tests. ~2 modified frontend files, ~1 test file. | **Done** (PR #338) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 163.1 | Add `visibilityCondition` field to `FieldDefinition` entity | 163A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java`. Add `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "visibility_condition", columnDefinition = "jsonb") private Map<String, Object> visibilityCondition;` with getter/setter. Default null. V38 column already exists from 161A. |
| 163.2 | Add `isFieldVisible()` to `CustomFieldValidator` | 163A | 163.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java`. Add `private boolean isFieldVisible(FieldDefinition definition, Map<String, Object> allValues, Map<String, FieldDefinition> slugToDefinition)`. Implements `eq`, `neq`, `in` operators. If controlling field not in slugToDefinition, return true (visible). If actual value null, return false (hidden). Exact logic in architecture doc Section 23.3.4. |
| 163.3 | Integrate visibility check into `CustomFieldValidator.validate()` | 163A | 163.2 | Modify: `CustomFieldValidator.java`. In the validation loop, before required check, call `isFieldVisible()`. If field is hidden, skip ALL validation (type + required). Build `slugToDefinition` map from all active field definitions of the entity type. |
| 163.4 | Add visibility condition validation at field definition save time | 163A | 163.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java`. In create/update, validate `visibilityCondition`: (1) `dependsOnSlug` must reference an active `FieldDefinition` of same `entityType`, (2) no self-reference (`dependsOnSlug` != own slug), (3) `operator` must be one of `eq`, `neq`, `in`. Throw `InvalidStateException` on violation. |
| 163.5 | Update field definition endpoints to accept `visibilityCondition` | 163A | 163.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionController.java`. Update create/update DTOs to include optional `visibilityCondition` (Map<String, Object>). Wire through to entity. Response includes `visibilityCondition`. |
| 163.6 | Write integration tests for conditional visibility backend | 163A | 163.3, 163.4 | New file: `backend/src/test/java/.../fielddefinition/ConditionalVisibilityIntegrationTest.java`. Tests: (1) `eq_operator_hides_field_when_value_differs`, (2) `neq_operator_hides_field_when_value_matches`, (3) `in_operator_shows_field_when_value_in_list`, (4) `hidden_required_field_skips_validation`, (5) `deactivated_controlling_field_makes_dependent_visible`, (6) `self_reference_condition_rejected`, (7) `cross_entity_type_condition_rejected`. ~7 tests. |
| 163.7 | Add `isFieldVisible()` function to `CustomFieldSection` | 163B | 163A | Modify: `frontend/components/field-definitions/CustomFieldSection.tsx`. Add `isFieldVisible(field, currentValues)` function implementing `eq`, `neq`, `in` operators. Logic in architecture doc Section 23.3.4. In the render loop, filter out fields where `!isFieldVisible(field, currentValues)`. |
| 163.8 | Update `validateField` to skip hidden fields | 163B | 163.7 | Modify: `CustomFieldSection.tsx`. At the top of `validateField()`, add: `if (!isFieldVisible(field, currentValues)) return null;`. This requires passing `currentValues` to `validateField`. |
| 163.9 | Ensure reactive show/hide on controlling field change | 163B | 163.7 | Modify: `CustomFieldSection.tsx`. Verify that when a controlling field value changes (dropdown selection, text input), React re-renders and dependent fields appear/disappear. Since `isFieldVisible` reads from `currentValues` state, and field changes update that state, this should work automatically. Test manually and add test. |
| 163.10 | Update frontend types for `visibilityCondition` | 163B | | Modify: `frontend/lib/api/field-definitions.ts`. Add `visibilityCondition?: { dependsOnSlug: string; operator: string; value: string | string[] } | null` to `FieldDefinitionResponse` type. |
| 163.11 | Write frontend unit tests for conditional visibility | 163B | 163.7, 163.8 | Modify or extend: `frontend/__tests__/field-definitions/CustomFieldSection.test.tsx`. Tests: (1) `isFieldVisible_eq_returns_true_when_match`, (2) `isFieldVisible_eq_returns_false_when_no_match`, (3) `isFieldVisible_neq_operator`, (4) `isFieldVisible_in_operator`, (5) `validateField_skips_hidden_required_field`. ~5 tests. |
| 163.12 | Add visibility condition editor section to field definition dialog | 163C | 163B | Modify: `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx`. In the field definition create/edit dialog, add a collapsible "Visibility Condition" section below the existing fields. Include a checkbox "Show conditionally" that toggles the condition editor. |
| 163.13 | Add controlling field selector | 163C | 163.12 | Within the visibility condition editor: add a `Select` dropdown populated with other active field definitions of the same `entityType`. Excludes the current field. Label: "Show this field when". Fetches from existing field definitions list (already loaded for the page). |
| 163.14 | Add operator and value inputs | 163C | 163.13 | Add operator `Select` (options: "equals", "does not equal", "is one of") mapping to `eq`, `neq`, `in`. For `eq`/`neq`: single text input (or for DROPDOWN controlling fields, a select from the controlling field's options). For `in`: multi-value tag input. |
| 163.15 | Add condition preview and clear button | 163C | 163.14 | Below the inputs, show a human-readable preview: "Show this field when **Matter Type** equals **Litigation**". Add a "Clear condition" button that removes the visibility condition. |
| 163.16 | Write frontend tests for visibility condition editor | 163C | 163.14 | New file or extend: `frontend/__tests__/settings/custom-fields-visibility-editor.test.tsx`. Tests: (1) `renders_controlling_field_options`, (2) `submits_eq_condition`, (3) `clears_condition`. ~3 tests. |

### Key Files

**Slice 163A -- Create:**
- `backend/src/test/java/.../fielddefinition/ConditionalVisibilityIntegrationTest.java` -- 7 integration tests

**Slice 163A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java` -- Add `visibilityCondition`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java` -- `isFieldVisible()`, integrate into validate()
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java` -- Condition validation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionController.java` -- DTO updates

**Slice 163B -- Modify:**
- `frontend/components/field-definitions/CustomFieldSection.tsx` -- `isFieldVisible()`, validateField skip, reactive rendering
- `frontend/lib/api/field-definitions.ts` -- Type updates
- `frontend/__tests__/field-definitions/CustomFieldSection.test.tsx` -- 5 unit tests

**Slice 163C -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` -- Visibility condition editor
- `frontend/__tests__/settings/custom-fields-visibility-editor.test.tsx` -- 3 tests

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java` -- Existing validation flow
- `frontend/components/field-definitions/CustomFieldSection.tsx` -- Existing render/validate pattern

### Architecture Decisions

- **Frontend + backend evaluation**: See ADR-094. Both sides evaluate `isFieldVisible()` independently. Backend skips validation for hidden fields. Frontend hides them from the DOM.
- **Preserve hidden values**: Hidden field values are NOT cleared from JSONB. If the controlling field changes back, the value reappears. Template rendering always has access to all values.
- **Three operators only**: `eq`, `neq`, `in`. Minimal complexity, covers practical cases.

---

## Epic 164: Invoice Custom Fields & Task Pack

**Goal**: Add `INVOICE` to `EntityType` enum, add custom field columns to the `Invoice` entity, add custom field + field group endpoints for invoices, extend `InvoiceContextBuilder`, add invoice custom fields to the invoice detail page, create the `common-task.json` field pack.

**References**: Architecture doc Sections 23.2.4 (Invoice entity), 23.2.5 (EntityType enum), 23.2.6 (task pack), 23.4.4 (invoice endpoints), 23.8.1 (backend changes).

**Dependencies**: Epic 161 (V38 migration creates invoice columns, auto-apply infrastructure).

**Scope**: Backend + Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **164A** | 164.1--164.8 | Backend: `EntityType.INVOICE`, `Invoice` entity columns (`customFields`, `appliedFieldGroups`), `PUT /api/invoices/{id}/custom-fields` and `PUT /api/invoices/{id}/field-groups` endpoints, `InvoiceService.create()` auto-apply, `InvoiceContextBuilder` extension, invoice GET response extension, integration tests. ~5 modified backend files, ~1 test file. | **Done** (PR #339) |
| **164B** | 164.9--164.13 | Frontend: Invoice detail page adds `CustomFieldSection` and `FieldGroupSelector`, settings page adds INVOICE tab for field/group management, type updates, frontend tests. ~4 modified frontend files. | **Done** (PR #340) |
| **164C** | 164.14--164.17 | Backend: `common-task.json` field pack (Task Info group with priority, category, estimated_hours), `FieldPackSeeder` processes `autoApply`, integration test. ~2 new files, ~1 modified file. | **Done** (PR #341) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 164.1 | Add `INVOICE` to `EntityType` enum | 164A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/EntityType.java`. Add `INVOICE` value. This enables field definitions and groups with `entityType = INVOICE`. |
| 164.2 | Add `customFields` and `appliedFieldGroups` to `Invoice` entity | 164A | 164.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java`. Add `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false) private Map<String, Object> customFields = new HashMap<>();` and `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "applied_field_groups", columnDefinition = "jsonb", nullable = false) private List<UUID> appliedFieldGroups = new ArrayList<>();`. Pattern: existing `Customer.java` custom field columns. |
| 164.3 | Add invoice custom field update endpoint | 164A | 164.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java`. Add `@PutMapping("/{id}/custom-fields")` with `@PreAuthorize("hasAnyRole('org:admin','org:owner')")`. Request body: `Map<String, Object>`. Validates via `CustomFieldValidator.validate(EntityType.INVOICE, ...)`. Pattern: existing customer/project custom field endpoints. |
| 164.4 | Add invoice field group update endpoint | 164A | 164.2 | Modify: `InvoiceController.java`. Add `@PutMapping("/{id}/field-groups")` with `@PreAuthorize("hasAnyRole('org:admin','org:owner')")`. Request body: `List<UUID>`. Pattern: existing customer/project field group endpoints. Delegates to service, which handles dependency resolution (if Epic 162 is complete). |
| 164.5 | Integrate auto-apply into `InvoiceService.create()` | 164A | 164.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`. After invoice creation, call `fieldGroupService.resolveAutoApplyGroupIds(EntityType.INVOICE)` and set on entity. Inject `FieldGroupService`. |
| 164.6 | Extend `InvoiceContextBuilder` with invoice custom fields | 164A | 164.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/InvoiceContextBuilder.java`. Add `invoice.customFields` to the template context map so that Thymeleaf templates can access `${invoice.customFields['po_number']}`. |
| 164.7 | Extend invoice GET response with `customFields` and `appliedFieldGroups` | 164A | 164.2 | Modify: `InvoiceController.java` (or its response DTO). Include `customFields` and `appliedFieldGroups` in the invoice detail response. |
| 164.8 | Write integration tests for invoice custom fields | 164A | 164.3, 164.5 | New file: `backend/src/test/java/.../invoice/InvoiceCustomFieldIntegrationTest.java`. Tests: (1) `update_invoice_custom_fields`, (2) `custom_field_validation_enforced`, (3) `update_invoice_field_groups`, (4) `auto_apply_on_invoice_creation`, (5) `invoice_context_builder_includes_custom_fields`, (6) `get_invoice_includes_custom_fields`. ~6 tests. Use `TestCustomerFactory.createActiveCustomer()`. |
| 164.9 | Add `CustomFieldSection` to invoice detail page | 164B | 164A | Modify: `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`. Import and render `CustomFieldSection` component with `entityType="INVOICE"` and the invoice's `customFields`/`appliedFieldGroups`. Wire save action to `PUT /api/invoices/{id}/custom-fields`. |
| 164.10 | Add `FieldGroupSelector` to invoice detail page | 164B | 164.9 | Modify: `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`. Import and render `FieldGroupSelector` with `entityType="INVOICE"`. Wire to `PUT /api/invoices/{id}/field-groups`. Pattern: existing customer/project detail pages. |
| 164.11 | Add INVOICE tab to custom fields settings page | 164B | 164.1 | Modify: `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx`. Add "Invoice" tab alongside existing "Customer", "Project", "Task" tabs. Renders the same field definition and field group management tables, filtered by `entityType = INVOICE`. |
| 164.12 | Update frontend invoice types | 164B | | Modify: `frontend/lib/api/invoices.ts` (or equivalent). Add `customFields: Record<string, unknown>` and `appliedFieldGroups: string[]` to invoice response type. Add `updateInvoiceCustomFields()` and `updateInvoiceFieldGroups()` functions. |
| 164.13 | Write frontend tests for invoice custom fields | 164B | 164.9 | New file or extend: `frontend/__tests__/invoices/invoice-custom-fields.test.tsx`. Tests: (1) `renders_custom_field_section_on_invoice_detail`, (2) `renders_field_group_selector_on_invoice_detail`. ~2 tests. |
| 164.14 | Create `common-task.json` field pack | 164C | | New file: `backend/src/main/resources/field-packs/common-task.json`. Contains "Task Info" group with 3 fields: `priority` (DROPDOWN: low/medium/high/urgent), `category` (TEXT), `estimated_hours` (NUMBER, min: 0). Set `"autoApply": true` on the group. Exact JSON in architecture doc Section 23.2.6. |
| 164.15 | Ensure `FieldPackSeeder` processes `autoApply` from JSON | 164C | 164.14 | Verify that 161A's `FieldPackSeeder` update correctly processes the `autoApply` field from `common-task.json`. If `FieldPackGroup.java` (the DTO for pack JSON deserialization) does not yet have `autoApply`, add it. This may have been done in 161A -- verify and supplement if needed. |
| 164.16 | Update `FieldPackSeeder` to include `common-task.json` | 164C | 164.14 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`. Add `"common-task"` to the list of packs to seed. Follow the existing pattern for `common-customer` and `common-project`. |
| 164.17 | Write integration test for task field pack seeding | 164C | 164.16 | New file or extend existing: `backend/src/test/java/.../fielddefinition/TaskFieldPackSeedingIntegrationTest.java`. Tests: (1) `task_pack_seeds_group_with_auto_apply`, (2) `task_pack_seeds_three_fields`, (3) `task_pack_idempotent`. ~3 tests. |

### Key Files

**Slice 164A -- Create:**
- `backend/src/test/java/.../invoice/InvoiceCustomFieldIntegrationTest.java` -- 6 integration tests

**Slice 164A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/EntityType.java` -- Add `INVOICE`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- Add JSONB columns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` -- 2 new endpoints + response extension
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Auto-apply on create
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/InvoiceContextBuilder.java` -- Add customFields to context

**Slice 164B -- Modify:**
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` -- CustomFieldSection + FieldGroupSelector
- `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` -- INVOICE tab
- `frontend/lib/api/invoices.ts` -- Type + function updates

**Slice 164C -- Create:**
- `backend/src/main/resources/field-packs/common-task.json` -- Task field pack
- `backend/src/test/java/.../fielddefinition/TaskFieldPackSeedingIntegrationTest.java` -- 3 tests

**Slice 164C -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java` -- Add common-task

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` -- Custom field column pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` -- Custom field endpoint pattern
- `backend/src/main/resources/field-packs/common-customer.json` -- Pack JSON format
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` -- CustomFieldSection integration pattern

### Architecture Decisions

- **`EntityType.INVOICE` enables all existing infrastructure**: Adding the enum value immediately enables field definition CRUD, group CRUD, `CustomFieldValidator`, `CustomFieldSection`, and `FieldGroupSelector` for invoices.
- **No invoice field pack seeded**: Different verticals (legal, accounting) need different invoice fields. No default pack -- admins create fields manually.
- **Task field pack sets `autoApply: true`**: New tasks automatically get the "Task Info" group with Priority, Category, and Estimated Hours.

---

## Epic 165: Template Required Fields & Generation Validation

**Goal**: Add `requiredContextFields` to `DocumentTemplate`, create `TemplateValidationService` for generation-time field checking, extend the template editor with a required fields selector, build the invoice generation validation checklist, and extend the invoice send flow with field validation and admin override.

**References**: Architecture doc Sections 23.2.3 (DocumentTemplate), 23.3.5 (template validation), 23.3.6 (invoice generation checklist), 23.4.3 (template endpoints), 23.4.6 (invoice lifecycle endpoints), ADR-093.

**Dependencies**: Epic 161 (V38 migration), Epic 164A (invoice custom fields for generation validation checks).

**Scope**: Backend + Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **165A** | 165.1--165.8 | Backend: `DocumentTemplate.requiredContextFields` field, `TemplateValidationService` (validates required fields against context), preview/generate endpoint extensions with `validationResult`/`warnings`, updated template create/update endpoints, integration tests. ~4 modified backend files, ~1 new file, ~1 test file. | **Done** (PR #342) |
| **165B** | 165.9--165.14 | Frontend: Template editor required fields selector (entity type + slug picker), generation dialog validation display (pass/warn indicators, "Generate anyway"), frontend tests. ~3 modified frontend files, ~1 test file. | |
| **165C** | 165.15--165.22 | Backend + Frontend: `InvoiceValidationService` with full checklist (customer fields, org branding, time entry rates, template fields), `POST /api/invoices/validate-generation` endpoint, invoice generation dialog checklist display, invoice send validation with ADMIN/OWNER override, integration tests, frontend tests. ~4 modified backend files, ~1 new backend file, ~3 modified frontend files. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 165.1 | Add `requiredContextFields` to `DocumentTemplate` entity | 165A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`. Add `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "required_context_fields", columnDefinition = "jsonb") private List<Map<String, String>> requiredContextFields;` with getter/setter. V38 column exists from 161A. |
| 165.2 | Create `TemplateValidationService` | 165A | 165.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateValidationService.java`. Method: `validateRequiredFields(DocumentTemplate template, Map<String, Object> context)` -- iterates `requiredContextFields`, checks each `{entity, slug}` against context map. Returns `ValidationResult(String status, List<MissingField> missingFields, List<UnknownField> unknownFields)`. For unknown fields: check if slug exists as active `FieldDefinition` -- if not, add to unknownFields. Pattern: `CustomerReadinessService` for readiness check structure. |
| 165.3 | Extend template preview endpoint with `validationResult` | 165A | 165.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java`. In `POST /api/templates/{id}/preview`, after building context and before rendering, call `TemplateValidationService.validateRequiredFields()`. Include `validationResult` in the response alongside `html`. |
| 165.4 | Extend template generate endpoint with `warnings` and `acknowledgeWarnings` | 165A | 165.2 | Modify: `DocumentTemplateController.java`. In `POST /api/templates/{id}/generate`, validate required fields. If missing fields exist and `acknowledgeWarnings` is not true, return 422 with the validation result. If `acknowledgeWarnings` is true, proceed and save `warnings` metadata on `GeneratedDocument`. |
| 165.5 | Add `warnings` field to `GeneratedDocument` entity | 165A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java`. Add a JSONB `warnings` field to store missing field warnings. If V38 doesn't include this column, add it to V38 or create a supplementary migration. Check if the `GeneratedDocument` already has a suitable field. |
| 165.6 | Update template create/update endpoints to accept `requiredContextFields` | 165A | 165.1 | Modify: `DocumentTemplateController.java`. Update create/update DTOs to include optional `requiredContextFields` (List<Map<String, String>>). Validate `entity` values are valid (`customer`, `project`, `task`, `invoice`). Wire through to entity. |
| 165.7 | Extend `DocumentGenerationReadinessService` with template field checks | 165A | 165.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/DocumentGenerationReadinessService.java`. Add a check for template required fields: if the selected template has `requiredContextFields`, verify they are populated for the target entity. Include in readiness result. |
| 165.8 | Write integration tests for template required fields | 165A | 165.3, 165.4 | New file: `backend/src/test/java/.../template/TemplateRequiredFieldsIntegrationTest.java`. Tests: (1) `save_template_with_required_fields`, (2) `preview_returns_validation_result_with_missing_fields`, (3) `preview_returns_pass_when_all_fields_present`, (4) `generate_with_missing_fields_blocked_without_acknowledge`, (5) `generate_with_acknowledge_proceeds_and_saves_warnings`, (6) `unknown_field_reported_in_validation`. ~6 tests. |
| 165.9 | Add required fields selector to template editor | 165B | 165A | Modify: `frontend/components/templates/template-editor.tsx` (or equivalent). Add a section "Required Context Fields" below the template body editor. Entity type dropdown (customer, project, task, invoice) + field slug selector (populated from existing field definitions of that entity type). Add/remove buttons for each field reference. Display as a list of `{entity, slug}` pairs. |
| 165.10 | Update template editor form submission to include `requiredContextFields` | 165B | 165.9 | Modify: template editor save action. Include `requiredContextFields` array in the POST/PUT request body. |
| 165.11 | Add validation result display to generation dialog | 165B | 165A | Modify: `frontend/components/templates/` (generation dialog component -- may be `generate-document-dialog.tsx` or embedded in template page). After preview response, display `validationResult` as a checklist with pass (green check) / warn (yellow triangle) indicators per field. Show "Generate anyway" button when warnings exist. |
| 165.12 | Update frontend template types | 165B | | Modify: `frontend/lib/api/` (template types file). Add `requiredContextFields?: Array<{entity: string; slug: string}>` to template response type. Add `validationResult` to preview response type. Add `acknowledgeWarnings?: boolean` to generate request type. |
| 165.13 | Write frontend tests for template editor required fields | 165B | 165.9 | New file: `frontend/__tests__/templates/template-required-fields.test.tsx`. Tests: (1) `renders_required_fields_selector`, (2) `adds_field_reference`, (3) `removes_field_reference`. ~3 tests. |
| 165.14 | Write frontend tests for generation dialog validation display | 165B | 165.11 | New file or extend: `frontend/__tests__/templates/generation-validation.test.tsx`. Tests: (1) `shows_validation_warnings`, (2) `shows_generate_anyway_button_on_warnings`. ~2 tests. |
| 165.15 | Create `InvoiceValidationService` | 165C | 165A, 164A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceValidationService.java`. Method: `validateInvoiceGeneration(UUID customerId, List<UUID> timeEntryIds, UUID templateId)` returns `List<ValidationCheck>`. Checks: (1) customer required fields (via `CustomerReadinessService`), (2) org branding (`OrgSettings.orgName` non-blank), (3) time entry rates (count null `snapshotRate`), (4) template required fields (if template specified, via `TemplateValidationService`). Define `record ValidationCheck(String name, Severity severity, boolean passed, String message)` and `enum Severity { INFO, WARNING, CRITICAL }`. See architecture doc Section 23.3.6. |
| 165.16 | Add `POST /api/invoices/validate-generation` endpoint | 165C | 165.15 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java`. Add `@PostMapping("/validate-generation")` with `@PreAuthorize("hasAnyRole('org:admin','org:owner')")`. Request body: `{customerId, timeEntryIds, templateId}`. Delegates to `InvoiceValidationService`. Returns `List<ValidationCheck>`. |
| 165.17 | Add invoice send validation | 165C | 165.15 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`. In the method that transitions invoice to SENT status, call `InvoiceValidationService` to check critical fields. If critical failures exist and role is MEMBER, throw `InvalidStateException`. If ADMIN/OWNER and `overrideWarnings` is false, return 422 with `canOverride: true`. If `overrideWarnings` is true, proceed. |
| 165.18 | Update `POST /api/invoices/{id}/send` endpoint for validation | 165C | 165.17 | Modify: `InvoiceController.java`. Update send endpoint to accept optional `overrideWarnings: boolean` in request body. Pass through to service. Return validation errors with `canOverride` flag in 422 response. |
| 165.19 | Update invoice generation dialog with validation checklist | 165C | 165.16 | Modify: `frontend/components/invoices/generate-invoice-dialog.tsx` (or equivalent). Before showing the generation confirmation, call `POST /api/invoices/validate-generation`. Display results as a checklist with pass/warn/fail icons per check. Show "Generate" button with warning count badge if warnings exist. |
| 165.20 | Add send validation and override to invoice send flow | 165C | 165.18 | Modify: `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` (or a send dialog component). When sending invoice, if 422 returned with `canOverride: true`, show a confirmation dialog for ADMIN/OWNER: "Send anyway? Missing fields: [list]". On confirm, re-call with `overrideWarnings: true`. For MEMBER, show error with link to fix fields. |
| 165.21 | Write integration tests for invoice generation validation | 165C | 165.16, 165.17 | New file: `backend/src/test/java/.../invoice/InvoiceGenerationValidationIntegrationTest.java`. Tests: (1) `validate_generation_returns_full_checklist`, (2) `null_rate_time_entries_produce_warning`, (3) `missing_org_name_produces_warning`, (4) `send_blocked_for_member_with_critical_failures`, (5) `send_allowed_for_admin_with_override`, (6) `send_proceeds_when_no_critical_failures`. ~6 tests. |
| 165.22 | Write frontend tests for invoice generation dialog checklist | 165C | 165.19 | New file or extend: `frontend/__tests__/invoices/invoice-generation-validation.test.tsx`. Tests: (1) `renders_validation_checklist`, (2) `shows_override_dialog_for_admin`. ~2 tests. |

### Key Files

**Slice 165A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateValidationService.java` -- Required field validation
- `backend/src/test/java/.../template/TemplateRequiredFieldsIntegrationTest.java` -- 6 integration tests

**Slice 165A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` -- Add `requiredContextFields`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` -- Preview/generate/CRUD updates
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java` -- Add `warnings` field
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/DocumentGenerationReadinessService.java` -- Template field check

**Slice 165B -- Modify:**
- `frontend/components/templates/template-editor.tsx` -- Required fields selector
- `frontend/components/templates/` (generation dialog) -- Validation display + "Generate anyway"
- `frontend/lib/api/` (template types) -- Type updates

**Slice 165C -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceValidationService.java` -- Full validation checklist
- `backend/src/test/java/.../invoice/InvoiceGenerationValidationIntegrationTest.java` -- 6 integration tests

**Slice 165C -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` -- validate-generation + send updates
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Send validation logic
- `frontend/components/invoices/generate-invoice-dialog.tsx` -- Validation checklist
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` -- Send override dialog

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` -- Existing entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` -- Generation flow
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadinessService.java` -- Readiness check pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Status transitions

### Architecture Decisions

- **Soft references by slug**: See ADR-093. Template `requiredContextFields` are `{entity, slug}` pairs, not FK references. Deleted fields render as "unknown" warnings. Templates survive field deletion.
- **Draft vs final distinction**: Draft generation is allowed with warnings and "Generate anyway". SENT transition blocks on critical failures for MEMBER role, allows ADMIN/OWNER override.
- **`InvoiceValidationService` is separate from `InvoiceService`**: Validation logic is complex enough to warrant its own service. It composes `CustomerReadinessService`, `OrgSettingsService`, time entry queries, and `TemplateValidationService`.

---

## Epic 166: Rate Warnings & Bug Fixes

**Goal**: Add rate card warnings to billable time entry creation and invoice generation. Fix three bugs: DATE client validation missing min/max, CURRENCY blankness check using naive toString, field type changeable after values exist.

**References**: Architecture doc Sections 23.3.7 (rate warning), 23.3.8 (field type immutability), 23.3.9 (bug fixes).

**Dependencies**: None -- fully independent, can run in parallel with all other epics.

**Scope**: Backend + Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **166A** | 166.1--166.6 | Rate warnings: `TimeEntryService` response DTO extended with `rateWarning`, `LogTimeDialog` warning banner, invoice generation dialog null-rate list, backend integration test, frontend test. ~3 modified backend files, ~2 modified frontend files. | |
| **166B** | 166.7--166.14 | Bug fixes: DATE min/max in backend `CustomFieldValidator.validateDate()` + frontend `validateField`, CURRENCY blankness in `CustomerReadinessService` + `ProjectSetupStatusService`, field type immutability in `FieldDefinitionService.update()`, integration tests, frontend test. ~4 modified backend files, ~1 modified frontend file, ~1 test file. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 166.1 | Add `rateWarning` to time entry creation response | 166A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java`. In `create()`, when `billable = true` and `snapshotRate == null` (no rate resolved), set a `rateWarning` string on the response: "No rate card found. This time entry will generate a zero-amount invoice line item." |
| 166.2 | Extend `TimeEntryController` response DTO with `rateWarning` | 166A | 166.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java`. Update the response record to include optional `String rateWarning`. Only populated when billable and no rate found. |
| 166.3 | Add rate warning banner to `LogTimeDialog` | 166A | | Modify: `frontend/components/tasks/log-time-dialog.tsx` (or equivalent log time component). When `billable` is toggled on, check if `resolveRate()` returns null. If so, show a warning banner (yellow/amber alert): "No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings > Rates." Warning is non-blocking. Pattern: existing alert/banner components. |
| 166.4 | Add null-rate warnings to invoice generation dialog | 166A | | Modify: `frontend/components/invoices/generate-invoice-dialog.tsx` (or equivalent). When displaying candidate time entries for invoice generation, check for entries with `snapshotRate === null`. If any, show a warning section: "N time entries have no rate card" with entry details (member name, hours, date, project/task). Pattern: existing warning display. |
| 166.5 | Write integration test for rate warning on time entry | 166A | 166.1 | New file or extend: `backend/src/test/java/.../timeentry/TimeEntryRateWarningIntegrationTest.java`. Tests: (1) `billable_time_entry_without_rate_includes_warning`, (2) `billable_time_entry_with_rate_has_no_warning`, (3) `non_billable_time_entry_has_no_warning`. ~3 tests. |
| 166.6 | Write frontend test for LogTimeDialog rate warning | 166A | 166.3 | Modify or extend: `frontend/__tests__/tasks/log-time-dialog.test.tsx` (or equivalent). Tests: (1) `shows_rate_warning_when_billable_and_no_rate`, (2) `hides_rate_warning_when_rate_exists`. ~2 tests. |
| 166.7 | Fix DATE min/max validation in backend `CustomFieldValidator` | 166B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java`. In `validateDate()` (or equivalent DATE validation method), after format check, add min/max date comparison using `String.compareTo()` (YYYY-MM-DD strings sort lexicographically). Check `validation.min` and `validation.max` from the field definition. See architecture doc Section 23.3.9 for exact code. **Note**: Method signature may need to change to accept `FieldDefinition` (instead of just `Object value`) to access `validation` rules. Update call site in `validateFieldValue()`. |
| 166.8 | Fix DATE min/max validation in frontend `validateField` | 166B | | Modify: `frontend/components/field-definitions/CustomFieldSection.tsx`. In `validateField()`, add `case "DATE"` that checks `validation.min` and `validation.max` using string comparison. Error messages: "must be on or after {min}" / "must be on or before {max}". See architecture doc Section 23.3.9. |
| 166.9 | Fix CURRENCY blankness check in `CustomerReadinessService` | 166B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadinessService.java`. Replace `!value.toString().isBlank()` with `isFieldValueFilled(fd, value)` method that checks CURRENCY objects: cast to Map, check `amount != null` AND `currency != null && !currency.toString().isBlank()`. See architecture doc Section 23.3.9. |
| 166.10 | Fix CURRENCY blankness check in `ProjectSetupStatusService` | 166B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/ProjectSetupStatusService.java`. Same fix as 166.9. Extract `isFieldValueFilled()` to a shared utility or duplicate in both services (both files are small). |
| 166.11 | Add field type immutability check to `FieldDefinitionService.update()` | 166B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java`. In `update()`, if the request changes `fieldType`: (1) determine entity table from `entityType`, (2) execute native query `SELECT EXISTS (SELECT 1 FROM <table> WHERE custom_fields ->> :slug IS NOT NULL)`, (3) if true, throw `InvalidStateException("Field type cannot be changed after values exist. Create a new field instead.")`. Uses same `entityTableName()` helper from 161A (or duplicate). |
| 166.12 | Write integration tests for DATE validation fix | 166B | 166.7 | New file or extend: `backend/src/test/java/.../fielddefinition/DateValidationIntegrationTest.java`. Tests: (1) `date_below_min_rejected`, (2) `date_above_max_rejected`, (3) `date_within_range_accepted`. ~3 tests. |
| 166.13 | Write integration tests for CURRENCY blankness and field type immutability | 166B | 166.9, 166.11 | New file or extend: `backend/src/test/java/.../fielddefinition/FieldValidationBugFixIntegrationTest.java`. Tests: (1) `currency_with_empty_code_detected_as_unfilled`, (2) `currency_with_valid_code_detected_as_filled`, (3) `field_type_change_blocked_when_values_exist`, (4) `field_type_change_allowed_when_no_values`. ~4 tests. |
| 166.14 | Write frontend test for DATE validation | 166B | 166.8 | Modify: `frontend/__tests__/field-definitions/CustomFieldSection.test.tsx`. Tests: (1) `validateField_date_below_min_returns_error`, (2) `validateField_date_above_max_returns_error`. ~2 tests. |

### Key Files

**Slice 166A -- Create:**
- `backend/src/test/java/.../timeentry/TimeEntryRateWarningIntegrationTest.java` -- 3 tests

**Slice 166A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` -- Add `rateWarning`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java` -- DTO extension
- `frontend/components/tasks/log-time-dialog.tsx` -- Rate warning banner
- `frontend/components/invoices/generate-invoice-dialog.tsx` -- Null-rate list

**Slice 166B -- Create:**
- `backend/src/test/java/.../fielddefinition/FieldValidationBugFixIntegrationTest.java` -- 4 tests

**Slice 166B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java` -- DATE min/max fix
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadinessService.java` -- CURRENCY blankness fix
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/ProjectSetupStatusService.java` -- CURRENCY blankness fix
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java` -- Type immutability check
- `frontend/components/field-definitions/CustomFieldSection.tsx` -- DATE validation fix
- `frontend/__tests__/field-definitions/CustomFieldSection.test.tsx` -- 2 tests

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` -- Where to add warning
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java` -- Existing validation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadinessService.java` -- Blankness check location
- `frontend/components/field-definitions/CustomFieldSection.tsx` -- `validateField` function

### Architecture Decisions

- **Rate warning is non-blocking**: The warning does not prevent time entry creation. Users may intend to configure rates later.
- **DATE comparison is lexicographic**: YYYY-MM-DD format strings sort correctly with `compareTo()`. No need to parse to `LocalDate` for comparison.
- **CURRENCY blankness is type-aware**: The readiness services must check `amount != null && currency != null && !currency.isBlank()` rather than naive `toString().isBlank()` on the map object.
- **Field type immutability uses existence query**: A native `SELECT EXISTS` query on the entity table checks if any row has a non-null value for the field's slug. This is a lightweight check before the type change.

---

## Test Summary

| Epic | Slice | Backend Integration Tests | Frontend Unit Tests | Total |
|------|-------|--------------------------|--------------------:|------:|
| 161 | 161A | 5 | 0 | 5 |
| 161 | 161B | 4 | 0 | 4 |
| 162 | 162A | 5 | 2 | 7 |
| 163 | 163A | 7 | 0 | 7 |
| 163 | 163B | 0 | 5 | 5 |
| 163 | 163C | 0 | 3 | 3 |
| 164 | 164A | 6 | 0 | 6 |
| 164 | 164B | 0 | 2 | 2 |
| 164 | 164C | 3 | 0 | 3 |
| 165 | 165A | 6 | 0 | 6 |
| 165 | 165B | 0 | 5 | 5 |
| 165 | 165C | 6 | 2 | 8 |
| 166 | 166A | 3 | 2 | 5 |
| 166 | 166B | 7 | 2 | 9 |
| **Total** | | **52** | **23** | **~75** |

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` - Core service: auto-apply logic, retroactive apply, dependency resolution. Modified in 161A, 161B, and 162A.
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java` - Validation hub: visibility condition evaluation (163A), DATE min/max fix (166B). Both frontend and backend must stay in sync.
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/field-definitions/CustomFieldSection.tsx` - Frontend rendering: visibility evaluation (163B), validateField updates (163B, 166B). Shared across all entity detail pages.
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` - Invoice lifecycle: auto-apply integration (164A), send validation (165C). High-impact changes to existing financial flow.
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/db/migration/tenant/V38__custom_field_maturity.sql` - Single migration for all Phase 23 DDL. Created in 161A, consumed by all subsequent slices. Must be correct on first deployment.