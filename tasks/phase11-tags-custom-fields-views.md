# Phase 11 — Tags, Custom Fields & Views

Phase 11 adds a **generic extensibility layer** to DocTeams — org-scoped custom fields (9 types), freeform tags, and saved filtered views. Field packs (JSON seed data) bootstrap new tenants with domain-appropriate fields. Custom field values are stored as JSONB on entity tables (not EAV), tags use a join table for referential integrity, and saved views execute filters server-side for consistent pagination.

**Architecture doc**: `architecture/phase11-tags-custom-fields-views.md`

**ADRs**: [ADR-052](../adr/ADR-052-jsonb-vs-eav-custom-field-storage.md) (JSONB vs EAV), [ADR-053](../adr/ADR-053-field-pack-seeding-strategy.md) (field pack seeding), [ADR-054](../adr/ADR-054-tag-storage-join-table-vs-array.md) (tag storage), [ADR-055](../adr/ADR-055-saved-view-filter-execution.md) (filter execution)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 87 | Field Definition & Custom Field Backend | Backend | -- | L | 87A, 87B, 87C | **Done** (PRs #179, #181, #183) |
| 88 | Tags Backend | Backend | -- | M | 88A, 88B | **Done** (PRs #180, #185) |
| 89 | Saved Views Backend | Backend | 87C, 88B | M | 89A, 89B | |
| 90 | Field Pack Seeding | Backend | 87A | S | 90A | **Done** (PR #182) |
| 91 | Custom Fields Frontend | Frontend | 87, 90 | L | 91A, 91B | 91A Done (PR #184) |
| 92 | Tags & Saved Views Frontend | Frontend | 88, 89 | L | 92A, 92B | |

## Dependency Graph

```
[E87A Field Definitions]──►[E87B Field Groups]──►[E87C Custom Field Values]
    (Backend V24)               (Backend)              (Backend)
         │                           │                      │
         │                           │                      ▼
         │                           │              [E89A Saved Views]
         │                           │                   (Backend V26)
         │                           │                      │
         │                           └──────────┬───────────┤
         │                                      ▼           ▼
         │                                  [E91A CF       [E91B CF
         │                                   Settings]      Entity UI]
         │                                   (Frontend)     (Frontend)
         │                                      │
         ▼                                      │
    [E90A Field Packs]                         │
    (Backend Seeding)                          │
                                               │
[E88A Tags CRUD]──►[E88B Tag Application]─────┤
    (Backend V25)      (Backend)              │
         │                 │                  │
         │                 └──────────────┬───┘
         │                                ▼
         │                            [E92A Tags UI]──►[E92B Views UI]
         │                            (Frontend)       (Frontend)
         │                                │
         └────────────────────────────────┘
                  [E89B View Filters]
                    (Backend)
```

**Parallel opportunities**:
- Epic 87A and 88A can run in parallel (independent migrations V24 and V25)
- Epic 90A can run immediately after 87A (doesn't wait for 87B/87C)
- Epic 88B and 87C are independent
- Epic 89A depends on both 87C and 88B being complete (needs custom field filtering + tag filtering)
- Frontend epics (91, 92) can have partial overlap if developers coordinate

## Implementation Order

### Stage 1: Foundation — Migrations & Core Entities (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 87 | 87A | V24 migration (field_definitions, field_groups, field_group_members tables, RLS policies, indexes). FieldDefinition entity with TenantAware, @FilterDef/@Filter, slug auto-generation, FieldDefinitionRepository with JPQL findOneById. FieldGroup entity and repository. FieldGroupMember entity and repository. FieldDefinitionService core CRUD (create, update, deactivate, list). FieldGroupService core CRUD. FieldDefinitionController + FieldGroupController with basic RBAC (admin/owner only). Integration tests (~12 tests). Foundation for all custom field work. |
| 1b | Epic 88 | 88A | V25 migration (tags, entity_tags tables, RLS policies, indexes). Tag entity with TenantAware, @FilterDef/@Filter, TagRepository with JPQL findOneById. EntityTag entity and repository. TagService CRUD (create, update, delete, list, search). TagController with RBAC (admin/owner for mutations, all members for reads). Integration tests for tag CRUD, slug generation, cascade delete (~8 tests). Independent of field definitions — can run in parallel with 87A. |

### Stage 2: Field Groups & Entity Extension (Sequential from 87A)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 87 | 87B | FieldGroupService membership management (addFieldToGroup, removeFieldFromGroup, reorderFields). FieldGroupController membership endpoints. Entity type validation (field entityType must match group entityType). Integration tests for group membership and entity type validation (~8 tests). Depends on 87A (needs FieldDefinition entity). |
| 2b | Epic 87 | 87C | V24 migration update: ALTER TABLE projects, tasks, customers ADD COLUMN custom_fields JSONB, applied_field_groups JSONB, CREATE INDEX GIN. CustomFieldValidator service (type checking, required validation, options validation, unknown key stripping). Extend ProjectService, TaskService, CustomerService to accept customFields on create/update. Extend list endpoints with JSONB containment filtering (customField[slug]=value query params). Extend entity response DTOs with customFields and appliedFieldGroups. PUT /api/{entityType}/{entityId}/field-groups endpoint. Integration tests for validation, filtering, and field group application (~15 tests). Depends on 87A (needs FieldDefinition for validation). |

### Stage 3: Tags & Entity Tagging (Sequential from 88A)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 88 | 88B | EntityTagService (setEntityTags with full-replace semantics, getEntityTags). Extend ProjectController, TaskController, CustomerController with POST /api/{entityType}/{entityId}/tags and GET endpoints. Tag filtering on list endpoints (tags=slug1,slug2 query param with AND logic EXISTS subqueries). Extend entity response DTOs with tags array. Integration tests for tag application, filtering, and permission checks (~12 tests). Depends on 88A (needs Tag entity). Independent of Epic 87C — can run in parallel. |

### Stage 4: Field Pack Seeding (Immediate after 87A)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 90 | 90A | FieldPackSeeder service in fielddefinition/ package. JSON pack files in src/main/resources/field-packs/: common-customer.json, common-project.json. FieldPackDefinition, FieldPackField DTO records. Integration with TenantProvisioningService (call FieldPackSeeder after migrations). V24 migration update: ALTER TABLE org_settings ADD COLUMN field_pack_status JSONB. Pack version tracking and idempotency. Integration tests for pack seeding, multi-pack, and idempotency (~6 tests). Depends on 87A (needs FieldDefinition entity). Can run immediately after 87A without waiting for 87B/87C. |

### Stage 5: Saved Views (Sequential from 87C + 88B)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a | Epic 89 | 89A | V26 migration (saved_views table, RLS policy, indexes, partial unique constraints). SavedView entity with TenantAware, @FilterDef/@Filter. SavedViewRepository with JPQL findOneById. SavedViewService CRUD (create, update, delete, list with personal/shared filtering). SavedViewController with RBAC (shared views require admin/owner). Integration tests for CRUD, personal/shared visibility, creator-only edit (~8 tests). Depends on 87A (V24 must exist first for V26). Does NOT yet include filter execution. |
| 5b | Epic 89 | 89B | ViewFilterService — translates filter JSONB to SQL WHERE clauses. Filter handlers: StatusFilterHandler, TagFilterHandler, CustomFieldFilterHandler, DateRangeFilterHandler, SearchFilterHandler. Extend ProjectController, TaskController, CustomerController list endpoints with ?view={viewId} query param. Integration tests for filter execution (status, tags, custom fields, date range, search, combined filters) (~10 tests). Depends on 87C (custom field filtering) and 88B (tag filtering). This is the convergence point — cannot start until both are complete. |

### Stage 6: Frontend — Custom Fields (Sequential from 87C + 90A)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 6a | Epic 91 | 91A | Settings page: Custom Fields (/org/[slug]/settings/custom-fields) with tabs per entity type. FieldDefinitionDialog (create/edit with dynamic validation inputs per fieldType). FieldGroupDialog (create/edit with field selection). DeleteFieldDialog, DeleteGroupDialog. API client functions (lib/api.ts). Settings sidebar nav update. Frontend tests for field/group management dialogs (~10 tests). Depends on 87B (group membership API) and 90A (field packs exist for realistic UX). |
| 6b | Epic 91 | 91B | CustomFieldSection component (renders fields by group with type-appropriate inputs: text, number, date, dropdown, boolean, currency, url, email, phone). Integrate into ProjectDetailPage, TaskDetailPage, CustomerDetailPage. FieldGroupSelector (apply groups to entity). Custom field columns on list pages (conditional rendering based on entity). Frontend tests for field rendering, input validation, and group application (~12 tests). Depends on 91A (settings UI must exist first for adding fields). |

### Stage 7: Frontend — Tags & Views (Sequential from 88B + 89B)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 7a | Epic 92 | 92A | Settings page: Tags (/org/[slug]/settings/tags) with TagDialog (create/edit). TagInput component (badges + auto-complete popover with inline tag creation for admin/owner). Integrate TagInput into ProjectDetailPage, TaskDetailPage, CustomerDetailPage. Tag badges on list pages. API client functions. Frontend tests for tag management, tag input, and auto-complete (~10 tests). Depends on 88B (tag application API). |
| 7b | Epic 92 | 92B | SavedViewSelector component (dropdown/tabs above list pages). CreateViewDialog (3-step: filters → columns → save). EditViewDialog. List page URL state management (?view=id or ?status=...&tags=... serialization). Integrate into ProjectsPage, TasksPage (my-work), CustomersPage. Filter UI components: TagFilter, CustomFieldFilter, DateRangeFilter, SearchInput. Frontend tests for view switching, filter serialization, and view dialogs (~12 tests). Depends on 89B (filter execution API). |

### Timeline

```
Stage 1:  [87A]  //  [88A]                                       ← foundation (parallel)
Stage 2:  [87B] ──► [87C]                                        ← field groups + values (sequential)
Stage 3:  [88B]                                                  ← tag application (parallel with 87C)
Stage 4:  [90A]                                                  ← field packs (parallel with 87B/87C/88B)
Stage 5:  [89A] ──► [89B]                                        ← saved views (89B waits for 87C + 88B)
Stage 6:  [91A] ──► [91B]                                        ← custom fields UI (sequential)
Stage 7:  [92A] ──► [92B]                                        ← tags + views UI (sequential)
```

**Critical path**: 87A → 87B → 87C → 89B → 91B → 92B
**Parallelizable**: 88A (with 87A), 88B (with 87C), 90A (with 87B/87C)

---

## Epic 87: Field Definition & Custom Field Backend

**Goal**: Establish the custom field infrastructure — field definitions (9 types), field groups for bundling, JSONB storage on entity tables, and a validation service that enforces type checking, required rules, and options constraints. Add GIN indexes for efficient custom field filtering.

**References**: Architecture doc Sections 11.2.1 (FieldDefinition), 11.2.2 (FieldGroup), 11.2.3 (FieldGroupMember), 11.2.7 (entity extensions), 11.3.1 (field definition CRUD), 11.3.2 (field group CRUD), 11.3.3 (custom field values), 11.6 (field type semantics), 11.10 (migrations). [ADR-052](../adr/ADR-052-jsonb-vs-eav-custom-field-storage.md).

**Dependencies**: None (new tables and package)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **87A** | 87.1-87.11 | V24 migration (field_definitions, field_groups, field_group_members, RLS, indexes), FieldDefinition entity with slug auto-gen, FieldGroup entity, FieldGroupMember entity, repositories with JPQL findOneById, FieldDefinitionService CRUD, FieldGroupService CRUD, controllers with RBAC, integration tests (~12 tests) | |
| **87B** | 87.12-87.14 | FieldGroupService membership management (addFieldToGroup, removeFieldFromGroup, reorderFields), FieldGroupController membership endpoints, entity type validation, integration tests for group membership (~8 tests) | |
| **87C** | 87.15-87.23 | V24 migration update (ALTER entity tables, GIN indexes), CustomFieldValidator service, extend ProjectService/TaskService/CustomerService for custom fields, JSONB containment filtering on list endpoints, field group application endpoint, integration tests for validation and filtering (~15 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 87.1 | Create V24 migration file | 87A | | `db/migration/tenant/V24__add_custom_fields_tags_views.sql`. Create field_definitions table (16 columns per Section 11.2.1: id, tenant_id, entity_type, name, slug, field_type, description, required, default_value JSONB, options JSONB, validation JSONB, sort_order, pack_id, pack_field_key, active, created_at, updated_at). Create field_groups table (10 columns per Section 11.2.2: id, tenant_id, entity_type, name, slug, description, pack_id, sort_order, active, created_at, updated_at). Create field_group_members table (5 columns per Section 11.2.3: id, tenant_id, field_group_id FK, field_definition_id FK, sort_order). Constraints: UNIQUE (tenant_id, entity_type, slug) on both field_definitions and field_groups, UNIQUE (field_group_id, field_definition_id) on field_group_members. Indexes per Section 11.10.2. RLS policies per Section 11.10.1. Pattern: follow V23 (invoices) for multi-table migration with RLS. |
| 87.2 | Create FieldDefinition entity | 87A | | `fielddefinition/FieldDefinition.java`. @Entity, @Table(name = "field_definitions"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 16 fields with @JdbcTypeCode(SqlTypes.JSON) for default_value, options, validation JSONB columns. Protected no-arg constructor. Public constructor taking (entityType, name, slug, fieldType, createdBy). Slug auto-generation method: `generateSlug(String name)` — lowercase, replace spaces/hyphens with underscores, strip non-alphanumeric, validate against ^[a-z][a-z0-9_]*$, append numeric suffix if conflict. Domain methods: updateMetadata(name, description, required, validation), deactivate(). Pattern: follow Invoice entity pattern from Phase 10. Use Map<String, Object> for JSONB fields. |
| 87.3 | Create FieldGroup entity | 87A | | `fielddefinition/FieldGroup.java`. @Entity, @Table(name = "field_groups"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 10 fields. Protected no-arg constructor. Public constructor taking (entityType, name, slug). Slug auto-generation via same method as FieldDefinition. Domain method: deactivate(). Pattern: follow FieldDefinition entity. |
| 87.4 | Create FieldGroupMember entity | 87A | | `fielddefinition/FieldGroupMember.java`. @Entity, @Table(name = "field_group_members"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 5 fields: id, tenantId, fieldGroupId UUID, fieldDefinitionId UUID, sortOrder int. No FK annotations (rely on DB FKs). Protected no-arg constructor. Public constructor taking (fieldGroupId, fieldDefinitionId, sortOrder). Pattern: simple join entity. |
| 87.5 | Create FieldDefinitionRepository | 87A | | `fielddefinition/FieldDefinitionRepository.java`. JpaRepository<FieldDefinition, UUID>. Add JPQL: `@Query("SELECT fd FROM FieldDefinition fd WHERE fd.id = :id") Optional<FieldDefinition> findOneById(@Param("id") UUID id)`. Add JPQL: `List<FieldDefinition> findByEntityTypeAndActiveTrueOrderBySortOrder(String entityType)`. Add JPQL: `Optional<FieldDefinition> findByEntityTypeAndSlug(String entityType, String slug)`. Pattern: follow InvoiceRepository (Phase 10). |
| 87.6 | Create FieldGroupRepository | 87A | | `fielddefinition/FieldGroupRepository.java`. JpaRepository<FieldGroup, UUID>. Add JPQL findOneById. Add JPQL: `List<FieldGroup> findByEntityTypeAndActiveTrueOrderBySortOrder(String entityType)`. Add JPQL: `Optional<FieldGroup> findByEntityTypeAndSlug(String entityType, String slug)`. Pattern: follow FieldDefinitionRepository. |
| 87.7 | Create FieldGroupMemberRepository | 87A | | `fielddefinition/FieldGroupMemberRepository.java`. JpaRepository<FieldGroupMember, UUID>. Add JPQL: `List<FieldGroupMember> findByFieldGroupIdOrderBySortOrder(UUID fieldGroupId)`. Add JPQL: `void deleteByFieldGroupIdAndFieldDefinitionId(UUID groupId, UUID fieldId)`. Pattern: simple repository. |
| 87.8 | Create FieldDefinitionService | 87A | | `fielddefinition/FieldDefinitionService.java`. @Service. Methods: `List<FieldDefinition> listByEntityType(String entityType)`, `FieldDefinition create(CreateFieldDefinitionRequest req)` — auto-generate slug if null, check uniqueness, handle suffix, `FieldDefinition update(UUID id, UpdateFieldDefinitionRequest req)`, `void deactivate(UUID id)`. RBAC checks: admin/owner only for mutations. Pattern: follow InvoiceService. Throw ResourceNotFoundException if not found. |
| 87.9 | Create FieldGroupService | 87A | | `fielddefinition/FieldGroupService.java`. @Service. Methods: `List<FieldGroup> listByEntityType(String entityType)`, `FieldGroup create(CreateFieldGroupRequest req)` — auto-generate slug if null, `FieldGroup update(UUID id, UpdateFieldGroupRequest req)`, `void deactivate(UUID id)`. RBAC checks: admin/owner only. Pattern: follow FieldDefinitionService. |
| 87.10 | Create FieldDefinitionController + FieldGroupController | 87A | | `fielddefinition/FieldDefinitionController.java` and `FieldGroupController.java`. @RestController, @RequestMapping("/api/field-definitions" and "/api/field-groups"). Endpoints: GET list (entityType query param), GET detail, POST create, PUT update, DELETE (soft-delete). DTO records: CreateFieldDefinitionRequest (11 fields), UpdateFieldDefinitionRequest (10 fields, no entityType), FieldDefinitionResponse (all fields), same pattern for group. @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')") on mutations. Pattern: follow InvoiceController (Phase 10). Return ProblemDetail for validation errors. |
| 87.11 | Add FieldDefinition + FieldGroup integration tests | 87A | | `fielddefinition/FieldDefinitionIntegrationTest.java` (~7 tests): (1) save and retrieve definition in dedicated schema, (2) save and retrieve in tenant_shared with filter, (3) findOneById respects @Filter, (4) slug auto-generation from name, (5) slug uniqueness within entityType and tenant, (6) deactivate sets active=false, (7) admin can create/update/delete, member cannot. `FieldGroupIntegrationTest.java` (~5 tests): (1) save/retrieve group, (2) findOneById respects filter, (3) slug auto-generation, (4) deactivate, (5) RBAC checks. Pattern: follow InvoiceIntegrationTest. Seed: provision tenant. |
| 87.12 | Extend FieldGroupService with membership methods | 87B | | Add methods to FieldGroupService: `void addFieldToGroup(UUID groupId, UUID fieldId, int sortOrder)` — validate entityType match, insert FieldGroupMember, `void removeFieldFromGroup(UUID groupId, UUID fieldId)` — delete FieldGroupMember, `void reorderFields(UUID groupId, List<UUID> fieldIds)` — update sortOrder for all members. Entity type validation: load FieldGroup and FieldDefinition, throw IllegalArgumentException if entityType mismatch. Pattern: service method validation pattern. |
| 87.13 | Add FieldGroupController membership endpoints | 87B | | Add endpoints to FieldGroupController: POST /api/field-groups/{id}/fields (body: { fieldDefinitionId, sortOrder }), DELETE /api/field-groups/{id}/fields/{fieldId}, PUT /api/field-groups/{id}/fields/reorder (body: { fieldIds: [...] }). @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')"). Return 400 for entity type mismatch. Pattern: follow controller conventions. |
| 87.14 | Add FieldGroupMember integration tests | 87B | | `FieldGroupMemberIntegrationTest.java` (~8 tests): (1) add field to group creates member, (2) remove field deletes member, (3) reorder updates sortOrder, (4) adding field with mismatched entityType throws 400, (5) member cannot add field to group (403), (6) duplicate field in group throws unique constraint error, (7) findByFieldGroupIdOrderBySortOrder returns members in correct order, (8) delete group cascades to members. Pattern: integration test with provisioning. |
| 87.15 | Update V24 migration with entity table columns | 87C | | Add to V24__add_custom_fields_tags_views.sql: ALTER TABLE projects ADD COLUMN custom_fields JSONB DEFAULT '{}'::jsonb, ADD COLUMN applied_field_groups JSONB. Same for tasks, customers. CREATE INDEX idx_projects_custom_fields ON projects USING GIN (custom_fields). Same GIN indexes for tasks, customers. Pattern: follow V23 ALTER TABLE pattern. |
| 87.16 | Update Project/Task/Customer entities with JSONB columns | 87C | | Modify `project/Project.java`, `task/Task.java`, `customer/Customer.java`. Add fields: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "custom_fields", columnDefinition = "jsonb") private Map<String, Object> customFields = new HashMap<>()`, `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "applied_field_groups", columnDefinition = "jsonb") private List<UUID> appliedFieldGroups`. Add getters/setters. Pattern: follow AuditEvent.details JSONB mapping. |
| 87.17 | Create CustomFieldValidator service | 87C | | `fielddefinition/CustomFieldValidator.java`. @Service. Method: `Map<String, Object> validate(String entityType, Map<String, Object> input, List<UUID> appliedGroupIds)`. Logic per Section 11.3.3: (1) load active FieldDefinitions for entityType, (2) strip unknown keys, (3) type check each value against fieldType (TEXT → String, NUMBER → BigDecimal, DATE → LocalDate, DROPDOWN → option value, BOOLEAN → Boolean, CURRENCY → Map with amount/currency, URL/EMAIL/PHONE → String with format validation), (4) apply validation rules (min/max, pattern, minLength, maxLength), (5) check required fields in applied groups. Return validated map. Throw IllegalArgumentException with field-level errors for bad input. Pattern: stateless validation service. |
| 87.18 | Extend ProjectService/TaskService/CustomerService for custom fields | 87C | | Modify `project/ProjectService.java`, `task/TaskService.java`, `customer/CustomerService.java`. Extend create/update methods to accept customFields Map<String, Object> and appliedFieldGroups List<UUID>. Call CustomFieldValidator.validate() before setting on entity. Update DTO response records to include customFields and appliedFieldGroups. Pattern: follow existing service methods. |
| 87.19 | Add custom field filtering to list endpoints | 87C | | Extend ProjectController, TaskController, CustomerController list methods to accept customField[slug]=value query params. Build JSONB containment queries: `WHERE p.custom_fields @> :json`. Use @Query with native SQL for custom field filtering. Pattern: native query with JSONB operators. |
| 87.20 | Add field group application endpoint | 87C | | Add endpoint to ProjectController, TaskController, CustomerController: PUT /api/{entityType}/{entityId}/field-groups (body: { appliedFieldGroups: [uuid, ...] }). Load FieldGroups, extract FieldDefinition IDs from FieldGroupMembers, update entity's appliedFieldGroups, return full FieldDefinition list for those groups. Pattern: custom endpoint pattern. |
| 87.21 | Add CustomFieldValidator integration tests | 87C | | `fielddefinition/CustomFieldValidatorTest.java` (~10 tests): (1) valid TEXT field passes, (2) TEXT field with pattern validation enforced, (3) NUMBER field rejects string, (4) DROPDOWN field rejects unknown option, (5) CURRENCY field requires amount and currency, (6) required field in applied group throws error when missing, (7) unknown keys are stripped, (8) empty input passes when no required fields, (9) URL field rejects invalid URL format, (10) DATE field rejects invalid date string. Pattern: unit test with mocked repository. |
| 87.22 | Add custom field CRUD integration tests | 87C | | `project/ProjectCustomFieldIntegrationTest.java` (~5 tests): (1) create project with custom fields, (2) update project custom fields, (3) custom field validation error returns 400 with field details, (4) apply field groups updates appliedFieldGroups, (5) JSONB containment filtering returns matching projects. Same pattern for TaskCustomFieldIntegrationTest, CustomerCustomFieldIntegrationTest. Total ~15 tests. Pattern: integration test with provisioning + field definitions seeded. |
| 87.23 | Verify V24 migration runs cleanly | 87C | | Run `./mvnw clean test` and verify no migration errors. Check that field_definitions, field_groups, field_group_members tables are created in both dedicated schemas and tenant_shared. Verify custom_fields columns exist on projects, tasks, customers with GIN indexes. Verify RLS policies applied. |

### Key Files

**Slice 87A — Create:**
- `backend/src/main/resources/db/migration/tenant/V24__add_custom_fields_tags_views.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupMember.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupMemberRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupIntegrationTest.java`

**Slice 87A — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Sections 11.2.1, 11.2.2, 11.2.3, 11.10.1, 11.10.2
- `backend/src/main/resources/db/migration/tenant/V23__create_invoices.sql` — Multi-table migration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` — Entity pattern reference
- `adr/ADR-052-jsonb-vs-eav-custom-field-storage.md`

**Slice 87B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` — Add membership methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupController.java` — Add membership endpoints

**Slice 87B — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupMemberIntegrationTest.java`

**Slice 87B — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Section 11.3.2

**Slice 87C — Modify:**
- `backend/src/main/resources/db/migration/tenant/V24__add_custom_fields_tags_views.sql` — Add ALTER TABLE statements
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` — Add JSONB columns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java` — Add JSONB columns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` — Add JSONB columns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — Accept customFields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Accept customFields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — Accept customFields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Custom field filtering + field group endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Same
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Same

**Slice 87C — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidatorTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectCustomFieldIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskCustomFieldIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerCustomFieldIntegrationTest.java`

**Slice 87C — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Sections 11.3.3, 11.6
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java` — JSONB mapping pattern
- `adr/ADR-052-jsonb-vs-eav-custom-field-storage.md`

### Architecture Decisions

- **fielddefinition/ package**: New feature package containing all custom field backend code (entities, repositories, services, controllers, validator). Follows feature-per-package convention.
- **Slug auto-generation**: If slug is null in request, generate from name by lowercasing, replacing spaces/hyphens with underscores, stripping non-alphanumeric, validating against ^[a-z][a-z0-9_]*$. Append numeric suffix (_2, _3) if conflict within tenant+entityType.
- **JSONB mapping**: Use @JdbcTypeCode(SqlTypes.JSON) with Map<String, Object> for custom_fields, options, validation. Same pattern as AuditEvent.details.
- **Field type validation**: CustomFieldValidator enforces type checking, format validation, and required field rules. Throws IllegalArgumentException with field-level errors.
- **Unknown key stripping**: Unknown custom field keys are silently removed during validation, preventing schema pollution.
- **JPQL findOneById**: Necessary for all new repositories to respect Hibernate @Filter in shared-schema mode.
- **V24 migration RLS policies**: All new tables get RLS policies following the established pattern from V13+ migrations.

---

## Epic 88: Tags Backend

**Goal**: Build the tags backend infrastructure — Tag entity, EntityTag polymorphic join table, tag CRUD API, entity tagging endpoints, tag search/auto-complete, and AND-logic tag filtering on list endpoints. Tag deletion cascades via ON DELETE CASCADE.

**References**: Architecture doc Sections 11.2.4 (Tag), 11.2.5 (EntityTag), 11.3.4 (tag CRUD & application), 11.4.3 (tags API), 11.10 (migrations). [ADR-054](../adr/ADR-054-tag-storage-join-table-vs-array.md).

**Dependencies**: None (new tables and package, independent of field definitions)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **88A** | 88.1-88.8 | V25 migration (tags, entity_tags, RLS, indexes, cascade delete), Tag entity with slug auto-gen, EntityTag entity, repositories with JPQL findOneById, TagService CRUD, TagController with RBAC, integration tests (~8 tests) | |
| **88B** | 88.9-88.15 | EntityTagService (setEntityTags with full-replace, getEntityTags), extend entity controllers with tag endpoints, tag filtering on list endpoints (AND logic EXISTS subqueries), extend entity response DTOs with tags array, integration tests (~12 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 88.1 | Create V25 migration file | 88A | | `db/migration/tenant/V25__add_tags.sql`. Create tags table (7 columns per Section 11.2.4: id, tenant_id, name, slug, color, created_at, updated_at). Create entity_tags table (6 columns per Section 11.2.5: id, tenant_id, tag_id FK, entity_type, entity_id, created_at). Constraints: UNIQUE (tenant_id, slug) on tags, UNIQUE (tag_id, entity_type, entity_id) on entity_tags. FK tag_id ON DELETE CASCADE. Indexes: idx_entity_tag_entity ON entity_tags(tenant_id, entity_type, entity_id), idx_entity_tag_tag ON entity_tags(tenant_id, tag_id, entity_type). RLS policies per Section 11.10.1. Pattern: follow V24 for multi-table migration. Can be created in parallel with V24 — no dependency. |
| 88.2 | Create Tag entity | 88A | | `tag/Tag.java`. @Entity, @Table(name = "tags"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 7 fields. Protected no-arg constructor. Public constructor taking (name, color). Slug auto-generation: `generateSlug(String name)` — same logic as FieldDefinition. Domain method: updateMetadata(name, color). Pattern: follow FieldDefinition entity. |
| 88.3 | Create EntityTag entity | 88A | | `tag/EntityTag.java`. @Entity, @Table(name = "entity_tags"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 6 fields: id, tenantId, tagId UUID, entityType String, entityId UUID, createdAt Instant. No FK annotations to entity (polymorphic reference). Protected no-arg constructor. Public constructor taking (tagId, entityType, entityId). Pattern: follow AuditEvent polymorphic pattern. |
| 88.4 | Create TagRepository | 88A | | `tag/TagRepository.java`. JpaRepository<Tag, UUID>. Add JPQL findOneById. Add JPQL: `List<Tag> findByOrderByNameAsc()`. Add JPQL: `List<Tag> findByNameStartingWithIgnoreCaseOrderByName(String prefix)` — for auto-complete. Add JPQL: `Optional<Tag> findBySlug(String slug)`. Pattern: follow FieldDefinitionRepository. |
| 88.5 | Create EntityTagRepository | 88A | | `tag/EntityTagRepository.java`. JpaRepository<EntityTag, UUID>. Add JPQL: `List<EntityTag> findByEntityTypeAndEntityId(String entityType, UUID entityId)`. Add JPQL: `void deleteByEntityTypeAndEntityId(String entityType, UUID entityId)`. Add JPQL: `long countByTagId(UUID tagId)` — for usage counting. Pattern: simple join table repository. |
| 88.6 | Create TagService | 88A | | `tag/TagService.java`. @Service. Methods: `List<Tag> listAll()`, `List<Tag> search(String prefix)` — calls repository.findByNameStartingWith, `Tag create(CreateTagRequest req)` — auto-generate slug if null, check uniqueness, `Tag update(UUID id, UpdateTagRequest req)`, `void delete(UUID id)` — cascade handled by FK. RBAC checks: admin/owner only for mutations, all members for reads. Pattern: follow FieldDefinitionService. |
| 88.7 | Create TagController | 88A | | `tag/TagController.java`. @RestController, @RequestMapping("/api/tags"). Endpoints: GET /api/tags (returns all tags), GET /api/tags?search={prefix} (auto-complete), POST /api/tags (create), PUT /api/tags/{id} (update), DELETE /api/tags/{id} (delete). DTO records: CreateTagRequest (name, color), UpdateTagRequest (name, color), TagResponse (all fields). @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')") on mutations. Pattern: follow FieldDefinitionController. |
| 88.8 | Add Tag integration tests | 88A | | `tag/TagIntegrationTest.java` (~8 tests): (1) save and retrieve tag in dedicated schema, (2) save and retrieve in tenant_shared with filter, (3) findOneById respects @Filter, (4) slug auto-generation from name, (5) slug uniqueness within tenant, (6) delete tag cascades to entity_tags (verify with EntityTagRepository.countByTagId), (7) search returns tags with matching prefix, (8) admin can create/update/delete, member cannot. Pattern: follow FieldDefinitionIntegrationTest. Seed: provision tenant, create tags, create entity_tags. |
| 88.9 | Create EntityTagService | 88B | | `tag/EntityTagService.java`. @Service. Methods: `List<Tag> getEntityTags(String entityType, UUID entityId)` — load EntityTag rows, join Tag, `void setEntityTags(String entityType, UUID entityId, List<UUID> tagIds)` — full-replace: delete all existing EntityTag rows for entity, insert new rows. Pattern: stateless service. Inject EntityTagRepository, TagRepository. |
| 88.10 | Add entity tag endpoints to controllers | 88B | | Extend ProjectController, TaskController, CustomerController. Add endpoints: POST /api/{entityType}/{entityId}/tags (body: { tagIds: [...] }) — calls EntityTagService.setEntityTags, GET /api/{entityType}/{entityId}/tags — returns TagResponse list. Pattern: custom endpoints. No special RBAC — entity-level permissions apply (project lead can tag own project, etc.). |
| 88.11 | Add tag filtering to list endpoints | 88B | | Extend ProjectController, TaskController, CustomerController list methods to accept tags=slug1,slug2 query param. Build EXISTS subqueries for AND logic: `WHERE EXISTS (SELECT 1 FROM entity_tags et JOIN tags t ON et.tag_id = t.id WHERE et.entity_type = :type AND et.entity_id = p.id AND t.slug = :slug1) AND EXISTS (...)`. Use native SQL query. Pattern: follow architecture doc Section 11.3.4 SQL example. |
| 88.12 | Extend entity response DTOs with tags array | 88B | | Modify ProjectController, TaskController, CustomerController response DTOs. Add `tags` field: List<TagResponse>. Load tags for each entity in list via EntityTagService.getEntityTags(). Handle N+1 with batch loading or separate query. Pattern: follow existing DTO enrichment patterns. |
| 88.13 | Add EntityTag integration tests | 88B | | `tag/EntityTagIntegrationTest.java` (~6 tests): (1) setEntityTags with empty list deletes all tags, (2) setEntityTags with 2 tags creates 2 EntityTag rows, (3) setEntityTags replaces existing tags, (4) getEntityTags returns tags in correct order, (5) delete tag cascades to EntityTag (verified via count), (6) cross-tenant EntityTag access returns empty (filter test). Pattern: integration test with provisioning. |
| 88.14 | Add tag application integration tests | 88B | | `project/ProjectTagIntegrationTest.java` (~3 tests): (1) POST /api/projects/{id}/tags sets tags, (2) GET /api/projects/{id}/tags returns tags, (3) tag filtering returns matching projects. Same pattern for TaskTagIntegrationTest, CustomerTagIntegrationTest. Total ~9 tests. Pattern: controller integration test with MockMvc. |
| 88.15 | Verify V25 migration runs cleanly | 88B | | Run `./mvnw clean test` and verify no migration errors. Check that tags, entity_tags tables are created in both dedicated schemas and tenant_shared. Verify cascade delete FK, indexes, RLS policies. |

### Key Files

**Slice 88A — Create:**
- `backend/src/main/resources/db/migration/tenant/V25__add_tags.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/Tag.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTag.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTagRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/tag/TagIntegrationTest.java`

**Slice 88A — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Sections 11.2.4, 11.2.5, 11.10.1, 11.10.2
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java` — Polymorphic entityType + entityId pattern
- `adr/ADR-054-tag-storage-join-table-vs-array.md`

**Slice 88B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTagService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/tag/EntityTagIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectTagIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskTagIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerTagIntegrationTest.java`

**Slice 88B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Add tag endpoints + filtering
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Same
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Same

**Slice 88B — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Section 11.3.4

### Architecture Decisions

- **tag/ package**: New feature package containing all tag backend code. Independent of fielddefinition/ package.
- **EntityTag polymorphic pattern**: Same as AuditEvent — no DB FK to entity tables, application-level integrity.
- **Tag filtering AND logic**: EXISTS subquery per tag. Simple, composable, efficient with proper indexes.
- **Full-replace semantics**: setEntityTags deletes all existing EntityTag rows and inserts new ones. Simplifies API (no add/remove endpoints).
- **Cascade delete**: tag_id FK ON DELETE CASCADE ensures deleting a tag removes all associations automatically.
- **V25 independent of V24**: Tags can be built in parallel with field definitions — no migration dependency.

---

## Epic 89: Saved Views Backend

**Goal**: Build saved views infrastructure — SavedView entity, CRUD API with personal/shared visibility, ViewFilterService that translates filter JSONB to SQL WHERE clauses, and integration with list endpoints via ?view={viewId} query param. Filters execute server-side for consistent pagination.

**References**: Architecture doc Sections 11.2.6 (SavedView), 11.3.5 (saved view CRUD), 11.3.6 (filter execution), 11.4.4 (views API), 11.10 (migrations). [ADR-055](../adr/ADR-055-saved-view-filter-execution.md).

**Dependencies**: Epic 87C (custom field filtering), Epic 88B (tag filtering) — ViewFilterService builds on these

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **89A** | 89.1-89.7 | V26 migration (saved_views, RLS, indexes, partial unique constraints), SavedView entity, SavedViewRepository with JPQL findOneById, SavedViewService CRUD (personal/shared filtering), SavedViewController with RBAC, integration tests (~8 tests) | |
| **89B** | 89.8-89.14 | ViewFilterService (filter-to-SQL translation), filter handlers (StatusFilterHandler, TagFilterHandler, CustomFieldFilterHandler, DateRangeFilterHandler, SearchFilterHandler), extend list endpoints with ?view={viewId} param, integration tests for filter execution (~10 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 89.1 | Create V26 migration file | 89A | | `db/migration/tenant/V26__add_saved_views.sql`. Create saved_views table (11 columns per Section 11.2.6: id, tenant_id, entity_type, name, filters JSONB NOT NULL DEFAULT '{}'::jsonb, columns JSONB, shared BOOLEAN NOT NULL DEFAULT false, created_by UUID NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, created_at, updated_at). Indexes: idx_saved_view_tenant_type ON (tenant_id, entity_type, shared), idx_saved_view_creator ON (tenant_id, created_by, entity_type). Partial unique index: uq_saved_view_shared_name ON (tenant_id, entity_type, name) WHERE shared = true. Partial unique index: uq_saved_view_personal_name ON (tenant_id, entity_type, name, created_by) WHERE shared = false. RLS policy. Pattern: follow V25 for single-table migration. Depends on V24 and V25 existing (must be numbered after). |
| 89.2 | Create SavedView entity | 89A | | `view/SavedView.java`. @Entity, @Table(name = "saved_views"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 11 fields with @JdbcTypeCode(SqlTypes.JSON) for filters and columns JSONB. Protected no-arg constructor. Public constructor taking (entityType, name, filters, columns, shared, createdBy). Domain method: updateFilters(filters, columns). Pattern: follow FieldDefinition entity. Use Map<String, Object> for filters, List<String> for columns. |
| 89.3 | Create SavedViewRepository | 89A | | `view/SavedViewRepository.java`. JpaRepository<SavedView, UUID>. Add JPQL findOneById. Add JPQL: `List<SavedView> findByEntityTypeAndSharedTrueOrderBySortOrder(String entityType)`. Add JPQL: `List<SavedView> findByEntityTypeAndCreatedByOrderBySortOrder(String entityType, UUID createdBy)`. Pattern: follow TagRepository. |
| 89.4 | Create SavedViewService | 89A | | `view/SavedViewService.java`. @Service. Methods: `List<SavedView> listViews(String entityType, UUID memberId)` — returns shared views + personal views for memberId, `SavedView create(CreateSavedViewRequest req)` — check RBAC: shared requires admin/owner, personal allowed for all members, `SavedView update(UUID id, UpdateSavedViewRequest req)` — check ownership: creator can edit personal view, admin/owner can edit any view, `void delete(UUID id)` — check ownership. Pattern: follow TagService with ownership checks. |
| 89.5 | Create SavedViewController | 89A | | `view/SavedViewController.java`. @RestController, @RequestMapping("/api/views"). Endpoints: GET /api/views?entityType={type} (returns shared + personal), POST /api/views (create), PUT /api/views/{id} (update), DELETE /api/views/{id} (delete). DTO records: CreateSavedViewRequest (entityType, name, filters, columns, shared, sortOrder), UpdateSavedViewRequest (name, filters, columns, sortOrder), SavedViewResponse (all fields). RBAC enforced in service. Pattern: follow TagController. |
| 89.6 | Add SavedView integration tests | 89A | | `view/SavedViewIntegrationTest.java` (~8 tests): (1) save and retrieve view in dedicated schema, (2) save and retrieve in tenant_shared with filter, (3) findOneById respects @Filter, (4) listViews returns shared + personal views, (5) shared view creation requires admin/owner, (6) personal view creation allowed for member, (7) creator can update own personal view, (8) admin can update any view. Pattern: follow TagIntegrationTest. Seed: provision tenant, create members. |
| 89.7 | Verify V26 migration runs cleanly | 89A | | Run `./mvnw clean test` and verify no migration errors. Check that saved_views table created with partial unique indexes and RLS policy. |
| 89.8 | Create ViewFilterService | 89B | | `view/ViewFilterService.java`. @Service. Method: `String buildWhereClause(Map<String, Object> filters, Map<String, Object> params)` — translates filter JSONB to SQL WHERE clauses, populates params map with named parameter bindings. Delegates to filter handlers. Returns WHERE clause as string (e.g., "p.status IN (:statuses) AND EXISTS (...) AND p.custom_fields @> :cf1"). Pattern: stateless service with handler composition. |
| 89.9 | Create filter handlers | 89B | | `view/StatusFilterHandler.java` — translates status array to `status IN (:statuses)`. `TagFilterHandler.java` — translates tags array to EXISTS subqueries (one per tag for AND logic). `CustomFieldFilterHandler.java` — translates customFields map to JSONB operators (@>, ->>, CAST). `DateRangeFilterHandler.java` — translates dateRange to `field >= :from AND field <= :to`. `SearchFilterHandler.java` — translates search string to `name ILIKE '%' || :search || '%'`. Each handler has method: `String buildPredicate(Object filterValue, Map<String, Object> params)`. Pattern: strategy pattern with handler per filter type. |
| 89.10 | Extend list endpoints with view parameter | 89B | | Modify ProjectController, TaskController, CustomerController list methods. Add query param: `@RequestParam(required = false) UUID view`. If view provided, load SavedView, extract filters JSONB, call ViewFilterService.buildWhereClause(), construct native query with WHERE clause. If view not provided, use existing filter params (status, tags, customField[slug], search). Pattern: conditional query building. Use @Query with native SQL for filtered lists. |
| 89.11 | Add ViewFilterService unit tests | 89B | | `view/ViewFilterServiceTest.java` (~5 tests): (1) status filter builds IN clause, (2) tags filter builds EXISTS subqueries, (3) custom field filter builds containment query, (4) date range filter builds range clause, (5) combined filters AND together. Pattern: unit test with mock handlers. |
| 89.12 | Add view filter execution integration tests | 89B | | `view/ViewFilterIntegrationTest.java` (~10 tests): (1) status filter returns matching projects, (2) tag filter with 2 tags returns projects with both, (3) custom field filter returns matching projects, (4) date range filter returns projects in range, (5) search filter returns projects with name match, (6) combined status + tags + custom field, (7) view parameter loads saved view and applies filters, (8) view with no filters returns all projects, (9) task view filtering, (10) customer view filtering. Pattern: integration test with seeded data (projects, tasks, customers, tags, field definitions). |
| 89.13 | Add filter handler unit tests | 89B | | Unit tests for each filter handler (~5 tests total). Pattern: simple unit tests. |
| 89.14 | Document filter schema and operators | 89B | | Update architecture doc Section 11.3.6 with final filter schema and SQL translation examples. No code change — documentation only. |

### Key Files

**Slice 89A — Create:**
- `backend/src/main/resources/db/migration/tenant/V26__add_saved_views.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedViewRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedViewService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedViewController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/view/SavedViewIntegrationTest.java`

**Slice 89A — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Sections 11.2.6, 11.3.5, 11.10
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/Tag.java` — Simple entity pattern

**Slice 89B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/ViewFilterService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/StatusFilterHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/TagFilterHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/CustomFieldFilterHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/DateRangeFilterHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SearchFilterHandler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/view/ViewFilterServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/view/ViewFilterIntegrationTest.java`

**Slice 89B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Add view param to list endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Same
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Same

**Slice 89B — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Section 11.3.6
- `adr/ADR-055-saved-view-filter-execution.md`

### Architecture Decisions

- **view/ package**: New feature package containing saved view backend code.
- **Filter handlers**: Strategy pattern for extensibility — adding new filter types requires only a new handler class.
- **Native SQL**: Filter execution uses native queries (not JPQL) because JSONB operators are PostgreSQL-specific. RLS handles tenant isolation.
- **Parameterized queries**: All filter values are parameterized (never interpolated) to prevent SQL injection.
- **Column configuration**: Stored in JSONB but frontend-only concern — backend always returns full entity data.
- **V26 after V24/V25**: SavedView migration must be numbered after field definitions and tags because views can filter on both.

---

## Epic 90: Field Pack Seeding

**Goal**: Build field pack seeding infrastructure — FieldPackSeeder service that reads JSON pack files from classpath, creates tenant-scoped FieldDefinition/FieldGroup/FieldGroupMember records during provisioning, tracks applied packs in OrgSettings.fieldPackStatus. Ship two platform packs: common-customer (8 address/contact fields), common-project (3 metadata fields).

**References**: Architecture doc Sections 11.3.7 (field pack seeding), 11.5.3 (sequence diagram), field pack examples. [ADR-053](../adr/ADR-053-field-pack-seeding-strategy.md).

**Dependencies**: Epic 87A (needs FieldDefinition entity)

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **90A** | 90.1-90.8 | FieldPackSeeder service, JSON pack files (common-customer.json, common-project.json), FieldPackDefinition DTO, integration with TenantProvisioningService, OrgSettings.fieldPackStatus column (V24 migration update), idempotency checks, integration tests (~6 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 90.1 | Update V24 migration with OrgSettings column | 90A | | Add to V24__add_custom_fields_tags_views.sql: ALTER TABLE org_settings ADD COLUMN field_pack_status JSONB. Pattern: simple ALTER TABLE. |
| 90.2 | Update OrgSettings entity with fieldPackStatus | 90A | | Modify `settings/OrgSettings.java`. Add field: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "field_pack_status", columnDefinition = "jsonb") private List<Map<String, Object>> fieldPackStatus`. Add getter/setter. Pattern: follow JSONB mapping pattern. |
| 90.3 | Create FieldPackDefinition DTOs | 90A | | `fielddefinition/FieldPackDefinition.java` record (packId, version, entityType, group: FieldPackGroup, fields: List<FieldPackField>). `FieldPackGroup.java` record (slug, name, description). `FieldPackField.java` record (slug, name, fieldType, description, required, defaultValue, options, validation, sortOrder). Pattern: simple DTO records for JSON deserialization. |
| 90.4 | Create common-customer.json pack file | 90A | | `src/main/resources/field-packs/common-customer.json`. JSON structure per architecture doc Section 11.3.7. Pack ID: "common-customer", version: 1, entityType: "CUSTOMER", group: { slug: "contact-address", name: "Contact & Address", description: "..." }, fields: [address_line1 TEXT, address_line2 TEXT, city TEXT, state_province TEXT, postal_code TEXT, country DROPDOWN (options: [US, CA, GB, AU, ZA, ...]), tax_number TEXT, phone PHONE]. Pattern: JSON data file. |
| 90.5 | Create common-project.json pack file | 90A | | `src/main/resources/field-packs/common-project.json`. Pack ID: "common-project", version: 1, entityType: "PROJECT", group: { slug: "project-info", name: "Project Info", description: "..." }, fields: [reference_number TEXT, priority DROPDOWN (options: [{ value: "low", label: "Low" }, { value: "medium", label: "Medium" }, { value: "high", label: "High" }]), category TEXT]. Pattern: JSON data file. |
| 90.6 | Create FieldPackSeeder service | 90A | | `fielddefinition/FieldPackSeeder.java`. @Service. Method: `void seedPacksForTenant(String tenantId)`. Logic: (1) scan classpath:field-packs/*.json, (2) deserialize to FieldPackDefinition, (3) for each pack, check OrgSettings.fieldPackStatus for existing application, (4) if not applied, create FieldGroup, create FieldDefinitions (set pack_id and pack_field_key), create FieldGroupMembers, (5) update OrgSettings.fieldPackStatus with { packId, version, appliedAt }. Pattern: follow existing service pattern. Use ScopedValue.where().call() for tenant binding. Inject FieldDefinitionRepository, FieldGroupRepository, FieldGroupMemberRepository, OrgSettingsRepository. |
| 90.7 | Integrate FieldPackSeeder with TenantProvisioningService | 90A | | Modify `provisioning/TenantProvisioningService.java`. After schema creation and Flyway migrations, call `fieldPackSeeder.seedPacksForTenant(schema)`. Pattern: add service method call to provisioning flow. |
| 90.8 | Add FieldPackSeeder integration tests | 90A | | `fielddefinition/FieldPackSeederIntegrationTest.java` (~6 tests): (1) seedPacksForTenant creates FieldDefinitions from packs, (2) seedPacksForTenant creates FieldGroups and members, (3) OrgSettings.fieldPackStatus updated with pack info, (4) re-seeding same tenant is idempotent (no duplicates), (5) two packs seed independently, (6) pack field definitions have pack_id and pack_field_key set. Pattern: integration test with provisioning. Verify counts, field attributes. |

### Key Files

**Slice 90A — Modify:**
- `backend/src/main/resources/db/migration/tenant/V24__add_custom_fields_tags_views.sql` — Add org_settings ALTER
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — Add fieldPackStatus field
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — Add FieldPackSeeder call

**Slice 90A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackField.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`
- `backend/src/main/resources/field-packs/common-customer.json`
- `backend/src/main/resources/field-packs/common-project.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeederIntegrationTest.java`

**Slice 90A — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Sections 11.3.7, 11.5.3
- `adr/ADR-053-field-pack-seeding-strategy.md`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — Provisioning flow

### Architecture Decisions

- **Classpath resource loading**: Pack JSON files in src/main/resources/field-packs/. Seeder scans directory with `ResourcePatternResolver`.
- **Per-tenant copies**: Seeding creates independent FieldDefinition records per tenant — no shared global definitions.
- **Idempotency**: Check OrgSettings.fieldPackStatus before applying pack. Re-seeding skips already-applied packs.
- **Pack identity tracking**: pack_id and pack_field_key columns enable future pack update detection.
- **Vertical fork extension**: Forks add domain packs by placing JSON files in field-packs/ directory — no code changes needed.

---

## Epic 91: Custom Fields Frontend

**Goal**: Build custom fields management UI in settings, field/group CRUD dialogs, CustomFieldSection component that renders fields by group with type-appropriate inputs, FieldGroupSelector, and custom field columns on list pages.

**References**: Architecture doc Sections 11.3.1, 11.3.2, 11.3.3, 11.6 (field type semantics), 11.4.1, 11.4.2, 11.4.5. Frontend patterns from `CLAUDE.md`.

**Dependencies**: Epic 87B (group membership API), Epic 90A (field packs for realistic UX)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **91A** | 91.1-91.8 | Settings page (/org/[slug]/settings/custom-fields), FieldDefinitionDialog, FieldGroupDialog, delete dialogs, API client functions, sidebar nav update, frontend tests (~10 tests) | |
| **91B** | 91.9-91.15 | CustomFieldSection component, FieldGroupSelector, integrate into entity detail pages, custom field columns on list pages, frontend tests (~12 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 91.1 | Create settings custom fields page | 91A | | `app/(app)/org/[slug]/settings/custom-fields/page.tsx`. Tabs per entity type (Projects, Tasks, Customers). Each tab has FieldDefinitionList (data table with columns: name, type, required, pack_id, active, actions) and FieldGroupList (cards with field count, pack_id). Fetch data via lib/api.ts functions (getFieldDefinitions, getFieldGroups). "Add Field" and "Add Group" buttons (admin/owner only). Pattern: follow settings/billing/page.tsx for tab layout. |
| 91.2 | Create FieldDefinitionDialog | 91A | | `components/field-definitions/FieldDefinitionDialog.tsx`. Shadcn Dialog with Form. Fields: entityType (readonly if editing), name, slug (auto-generated or manual), fieldType (dropdown), description, required (checkbox), sortOrder. Conditional inputs based on fieldType: options array for DROPDOWN (add/remove rows), validation object for TEXT/NUMBER/DATE (minLength, maxLength, pattern, min, max). Server action: createFieldDefinition or updateFieldDefinition. Pattern: follow CreateProjectDialog. Use react-hook-form + zod. |
| 91.3 | Create FieldGroupDialog | 91A | | `components/field-definitions/FieldGroupDialog.tsx`. Shadcn Dialog with Form. Fields: entityType (readonly if editing), name, slug, description, sortOrder, fieldDefinitionIds (multi-select from active fields for entityType). Server action: createFieldGroup or updateFieldGroup. Pattern: follow FieldDefinitionDialog. |
| 91.4 | Create delete dialogs | 91A | | `components/field-definitions/DeleteFieldDialog.tsx` and `DeleteGroupDialog.tsx`. Shadcn AlertDialog with confirmation. Server actions: deleteFieldDefinition, deleteFieldGroup. Pattern: follow DeleteProjectDialog. |
| 91.5 | Create API client functions | 91A | | Add to `lib/api.ts`: `getFieldDefinitions(entityType)`, `getFieldDefinition(id)`, `createFieldDefinition(req)`, `updateFieldDefinition(id, req)`, `deleteFieldDefinition(id)`, `getFieldGroups(entityType)`, `createFieldGroup(req)`, `updateFieldGroup(id, req)`, `deleteFieldGroup(id)`. Pattern: follow existing api.ts functions. All use auth().getToken() for JWT. |
| 91.6 | Update settings sidebar nav | 91A | | Modify `app/(app)/org/[slug]/settings/layout.tsx` (or wherever settings nav is defined). Add "Custom Fields" nav item. Pattern: follow billing nav item. |
| 91.7 | Add settings page tests | 91A | | `__tests__/settings/custom-fields.test.tsx` (~5 tests): (1) renders tabs for 3 entity types, (2) displays field definitions in table, (3) displays field groups in cards, (4) admin sees "Add Field" button, (5) member does not. Pattern: follow existing settings page tests. Mock api.ts functions. |
| 91.8 | Add dialog tests | 91A | | `__tests__/field-definitions/FieldDefinitionDialog.test.tsx` (~3 tests): (1) renders form fields, (2) conditional options input for DROPDOWN type, (3) submission calls createFieldDefinition. `__tests__/field-definitions/FieldGroupDialog.test.tsx` (~2 tests): (1) renders form, (2) submission calls createFieldGroup. Pattern: follow dialog test conventions. Use cleanup() afterEach for Radix. |
| 91.9 | Create CustomFieldSection component | 91B | | `components/field-definitions/CustomFieldSection.tsx`. Props: entityType, entityId, customFields Map<String, Object>, appliedFieldGroups UUID[], editable boolean. Loads FieldDefinitions and FieldGroups via API. Groups fields by FieldGroup. Renders each field with type-appropriate input: TEXT → Input, NUMBER → Input type="number", DATE → DatePicker, DROPDOWN → Select, BOOLEAN → Checkbox, CURRENCY → amount Input + currency Select, URL → Input type="url", EMAIL → Input type="email", PHONE → Input type="tel". Validation per field definition. Save button calls updateEntity server action. Pattern: dynamic form rendering. Use Shadcn Form + react-hook-form. |
| 91.10 | Create FieldGroupSelector component | 91B | | `components/field-definitions/FieldGroupSelector.tsx`. Props: entityType, entityId, appliedFieldGroups UUID[]. Multi-select dropdown of available FieldGroups for entityType. Save button calls setEntityFieldGroups server action (PUT /api/{entityType}/{entityId}/field-groups). Pattern: follow multi-select pattern from TeamMembersPanel. |
| 91.11 | Integrate CustomFieldSection into entity detail pages | 91B | | Modify `app/(app)/org/[slug]/projects/[id]/page.tsx`, entity detail pages for tasks and customers. Add CustomFieldSection component after existing detail sections. Pass customFields and appliedFieldGroups from entity response. Editable prop based on user permissions (project lead for projects, assigned user for tasks, admin/owner for customers). Pattern: add section to existing page. |
| 91.12 | Integrate FieldGroupSelector into entity detail pages | 91B | | Add FieldGroupSelector to project/task/customer detail pages. Admin/owner only. Displays currently applied groups as badges with remove action. Pattern: follow existing badge patterns. |
| 91.13 | Add custom field columns to list pages | 91B | | Modify `projects/page.tsx`, tasks and customers list pages. Add custom field columns conditionally based on appliedFieldGroups or saved view column config. Column headers use field definition name. Cell rendering uses field type semantics (format dates, show dropdown label, format currency). Pattern: follow existing list table columns. Use data table component. |
| 91.14 | Add CustomFieldSection tests | 91B | | `__tests__/field-definitions/CustomFieldSection.test.tsx` (~6 tests): (1) renders TEXT field as Input, (2) renders DROPDOWN field as Select with options, (3) renders BOOLEAN field as Checkbox, (4) renders CURRENCY field with amount + currency inputs, (5) validation error displayed for invalid input, (6) save button calls updateEntity. Pattern: component test with mock data. |
| 91.15 | Add list page and detail page integration tests | 91B | | `__tests__/projects/ProjectListCustomFields.test.tsx` (~3 tests): custom field column rendered, value formatted. `__tests__/projects/ProjectDetailCustomFields.test.tsx` (~3 tests): CustomFieldSection rendered, FieldGroupSelector rendered for admin, hidden for member. Same for tasks, customers. Total ~6 tests. Pattern: list/detail page tests. |

### Key Files

**Slice 91A — Create:**
- `frontend/app/(app)/org/[slug]/settings/custom-fields/page.tsx`
- `frontend/components/field-definitions/FieldDefinitionDialog.tsx`
- `frontend/components/field-definitions/FieldGroupDialog.tsx`
- `frontend/components/field-definitions/DeleteFieldDialog.tsx`
- `frontend/components/field-definitions/DeleteGroupDialog.tsx`
- `frontend/__tests__/settings/custom-fields.test.tsx`
- `frontend/__tests__/field-definitions/FieldDefinitionDialog.test.tsx`
- `frontend/__tests__/field-definitions/FieldGroupDialog.test.tsx`

**Slice 91A — Modify:**
- `frontend/lib/api.ts` — Add field definition/group API functions
- `frontend/app/(app)/org/[slug]/settings/layout.tsx` — Add nav item

**Slice 91A — Read for context:**
- `frontend/CLAUDE.md` — Shadcn patterns, server actions
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` — Settings page tab pattern
- `frontend/components/projects/CreateProjectDialog.tsx` — Dialog pattern

**Slice 91B — Create:**
- `frontend/components/field-definitions/CustomFieldSection.tsx`
- `frontend/components/field-definitions/FieldGroupSelector.tsx`
- `frontend/__tests__/field-definitions/CustomFieldSection.test.tsx`
- `frontend/__tests__/projects/ProjectListCustomFields.test.tsx`
- `frontend/__tests__/projects/ProjectDetailCustomFields.test.tsx`

**Slice 91B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add CustomFieldSection + FieldGroupSelector
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Add custom field columns
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Add custom field columns (if customers list exists)

**Slice 91B — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Section 11.6 (field type semantics)
- `frontend/components/ui/form.tsx` — Shadcn Form component

### Architecture Decisions

- **field-definitions/ component directory**: All custom field UI components in this directory. Follows feature-per-directory convention.
- **Dynamic field rendering**: CustomFieldSection renders input type based on fieldType. Type mapping defined in Section 11.6.
- **Server actions**: Field/group mutations use server actions pattern. API calls in actions, not in components.
- **Custom field columns**: Conditionally rendered based on appliedFieldGroups or saved view config. Frontend reads column config from saved view.

---

## Epic 92: Tags & Saved Views Frontend

**Goal**: Build tags management UI in settings, TagInput component with auto-complete, integrate tags into entity detail pages and list pages, SavedViewSelector component, CreateViewDialog, filter UI components, and view switching logic with URL state serialization.

**References**: Architecture doc Sections 11.3.4, 11.3.5, 11.3.6, 11.4.3, 11.4.4. Frontend patterns from `CLAUDE.md`.

**Dependencies**: Epic 88B (tag application API), Epic 89B (filter execution API)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **92A** | 92.1-92.8 | Settings page (/org/[slug]/settings/tags), TagDialog, TagInput component (badges + auto-complete with inline creation), integrate into entity detail pages, tag badges on list pages, frontend tests (~10 tests) | |
| **92B** | 92.9-92.17 | SavedViewSelector component, CreateViewDialog, EditViewDialog, list page URL state management, filter UI components (TagFilter, CustomFieldFilter, DateRangeFilter, SearchInput), integrate into list pages, frontend tests (~12 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 92.1 | Create settings tags page | 92A | | `app/(app)/org/[slug]/settings/tags/page.tsx`. Data table with columns: name (with color badge), slug, usage count (via API), actions. "Add Tag" button (admin/owner only). Fetch data via lib/api.ts getTags(). Pattern: follow settings/custom-fields/page.tsx. |
| 92.2 | Create TagDialog | 92A | | `components/tags/TagDialog.tsx`. Shadcn Dialog with Form. Fields: name, color (color picker or hex input). Server action: createTag or updateTag. Pattern: follow FieldDefinitionDialog. Use Shadcn Input for name, custom color input or Input type="color" for color. |
| 92.3 | Create TagInput component | 92A | | `components/tags/TagInput.tsx`. Props: entityType, entityId, tags TagResponse[], editable boolean. Displays tags as colored badges (Badge component with custom color via style prop). If editable, renders Popover with auto-complete input. Auto-complete queries searchTags(prefix). Selecting tag from list adds it. Typing new name + Enter creates tag inline (admin/owner only). Remove tag with X button on badge. Save button calls setEntityTags server action. Pattern: Shadcn Popover + Badge + Command (for auto-complete). |
| 92.4 | Integrate TagInput into entity detail pages | 92A | | Modify `projects/[id]/page.tsx`, `tasks/[id]/page.tsx`, `customers/[id]/page.tsx`. Add TagInput component in a "Tags" section. Pass tags from entity response. Editable prop based on user permissions. Pattern: add section to existing page. |
| 92.5 | Add tag badges to list pages | 92A | | Modify `projects/page.tsx`, `tasks/page.tsx`, `customers/page.tsx`. Add tags column to data table. Render tags as small colored badges (Badge component). Pattern: follow existing column rendering. |
| 92.6 | Create API client functions | 92A | | Add to `lib/api.ts`: `getTags()`, `searchTags(prefix)`, `createTag(req)`, `updateTag(id, req)`, `deleteTag(id)`, `getEntityTags(entityType, entityId)`, `setEntityTags(entityType, entityId, tagIds)`. Pattern: follow existing api.ts functions. |
| 92.7 | Add settings tags page tests | 92A | | `__tests__/settings/tags.test.tsx` (~3 tests): (1) renders tags in table, (2) admin sees "Add Tag" button, (3) member does not. Pattern: follow settings page tests. |
| 92.8 | Add TagInput tests | 92A | | `__tests__/tags/TagInput.test.tsx` (~7 tests): (1) renders badges for existing tags, (2) auto-complete shows matching tags, (3) selecting tag from list adds it, (4) inline tag creation works for admin, (5) inline creation disabled for member, (6) remove tag removes badge, (7) save button calls setEntityTags. Pattern: component test with mock API. Use cleanup() afterEach. |
| 92.9 | Create SavedViewSelector component | 92B | | `components/views/SavedViewSelector.tsx`. Props: entityType, views SavedViewResponse[], currentView UUID or null, onViewChange(viewId). Renders Tabs or Dropdown with view options (shared views + personal views). Active view highlighted. "Save View" button (opens CreateViewDialog). Pattern: Shadcn Tabs or Select. Use URL search params for view state. |
| 92.10 | Create CreateViewDialog | 92B | | `components/views/CreateViewDialog.tsx`. 3-step wizard: (1) configure filters (status multi-select, tags multi-select, custom field filters dynamic based on entity type, date range, search), (2) select columns (standard columns + custom field columns with cf: prefix), (3) save (name, shared checkbox for admin/owner). Server action: createSavedView. Pattern: multi-step dialog with state. Use Shadcn Dialog + Form + Tabs for steps. |
| 92.11 | Create EditViewDialog | 92B | | `components/views/EditViewDialog.tsx`. Same as CreateViewDialog but pre-filled with existing view data. Server action: updateSavedView. Pattern: follow CreateViewDialog. |
| 92.12 | Create filter UI components | 92B | | `components/views/TagFilter.tsx` — multi-select tags with auto-complete. `CustomFieldFilter.tsx` — dynamic inputs based on field type (text input for TEXT, number range for NUMBER, date range for DATE, dropdown for DROPDOWN). `DateRangeFilter.tsx` — from/to date pickers. `SearchInput.tsx` — text input with search icon. Pattern: reusable filter components. Use Shadcn Select, Input, DatePicker. |
| 92.13 | Add URL state management to list pages | 92B | | Modify `projects/page.tsx`, `tasks/page.tsx`, `customers/page.tsx`. Add URL search params for view state: ?view={id} or ?status=...&tags=...&customField[slug]=...&search=... Read params on page load. Apply filters via API query params. Update URL on filter change (useRouter + searchParams). Pattern: follow existing search param patterns. |
| 92.14 | Integrate SavedViewSelector into list pages | 92B | | Add SavedViewSelector to projects, tasks, customers list pages above data table. Fetch views via lib/api.ts getViews(entityType). Pass currentView from URL params. onViewChange updates URL and refetches data. Pattern: add component above table. |
| 92.15 | Add SavedViewSelector tests | 92B | | `__tests__/views/SavedViewSelector.test.tsx` (~3 tests): (1) renders shared views, (2) renders personal views, (3) clicking view updates URL. Pattern: component test with mock views. |
| 92.16 | Add CreateViewDialog tests | 92B | | `__tests__/views/CreateViewDialog.test.tsx` (~5 tests): (1) step 1 renders filter inputs, (2) step 2 renders column checkboxes, (3) step 3 renders name + shared inputs, (4) submission calls createSavedView, (5) shared checkbox disabled for member. Pattern: dialog test with wizard steps. |
| 92.17 | Add list page view switching tests | 92B | | `__tests__/projects/ProjectListViews.test.tsx` (~4 tests): (1) selecting view updates URL, (2) URL ?view=id loads view and applies filters, (3) manual filter change updates URL params, (4) view selector shows current view highlighted. Same pattern for tasks, customers. Total ~12 tests. Pattern: list page test with URL state. |

### Key Files

**Slice 92A — Create:**
- `frontend/app/(app)/org/[slug]/settings/tags/page.tsx`
- `frontend/components/tags/TagDialog.tsx`
- `frontend/components/tags/TagInput.tsx`
- `frontend/__tests__/settings/tags.test.tsx`
- `frontend/__tests__/tags/TagInput.test.tsx`

**Slice 92A — Modify:**
- `frontend/lib/api.ts` — Add tag API functions
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add TagInput
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Add tag badges column
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Add tag badges column

**Slice 92A — Read for context:**
- `frontend/CLAUDE.md` — Shadcn patterns
- `frontend/components/ui/badge.tsx` — Badge component for colored tags
- `frontend/components/ui/command.tsx` — Command component for auto-complete

**Slice 92B — Create:**
- `frontend/components/views/SavedViewSelector.tsx`
- `frontend/components/views/CreateViewDialog.tsx`
- `frontend/components/views/EditViewDialog.tsx`
- `frontend/components/views/TagFilter.tsx`
- `frontend/components/views/CustomFieldFilter.tsx`
- `frontend/components/views/DateRangeFilter.tsx`
- `frontend/components/views/SearchInput.tsx`
- `frontend/__tests__/views/SavedViewSelector.test.tsx`
- `frontend/__tests__/views/CreateViewDialog.test.tsx`
- `frontend/__tests__/projects/ProjectListViews.test.tsx`

**Slice 92B — Modify:**
- `frontend/lib/api.ts` — Add saved view API functions
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Add SavedViewSelector + URL state
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Add SavedViewSelector + URL state

**Slice 92B — Read for context:**
- `architecture/phase11-tags-custom-fields-views.md` Section 11.3.6 (filter schema)
- `frontend/components/ui/tabs.tsx` — Tabs component for view selector
- `frontend/components/ui/dialog.tsx` — Dialog for view dialogs

### Architecture Decisions

- **tags/ and views/ component directories**: Separate directories for tag and view components. Follows feature-per-directory convention.
- **TagInput inline creation**: Admin/owner can type new tag name and press Enter to create inline. Member cannot — auto-complete only.
- **Colored badges**: Tag badges use custom color from Tag.color via inline style. Default color if null.
- **URL state serialization**: Filter state serialized to URL search params (?view=id or ?status=...&tags=...). Enables shareable filtered views.
- **SavedViewSelector UI**: Tabs component for view switching. Alternative: Dropdown (Select). Choose based on UX review.
- **Filter components reusable**: TagFilter, CustomFieldFilter, etc. used in both CreateViewDialog and inline filter UI above list tables.
