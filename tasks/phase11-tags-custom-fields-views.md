# Phase 11 — Tags, Custom Fields & Views

Phase 11 adds a **generic extensibility layer** to the DocTeams platform — the ability for organizations to customize their data model without code changes. Organizations can define custom fields (9 typed values: text, number, date, dropdown, boolean, currency, URL, email, phone), apply freeform color-coded tags, and save filtered views of their data. The phase introduces six new entities (`FieldDefinition`, `FieldGroup`, `FieldGroupMember`, `Tag`, `EntityTag`, `SavedView`), alters three existing entities (`Project`, `Task`, `Customer`) with JSONB custom field storage, and adds a seed-data mechanism for bootstrapping field definitions from platform-provided "field packs."

**Architecture doc**: `architecture/phase11-tags-custom-fields-views.md` (Section 11 of ARCHITECTURE.md)

**ADRs**: [ADR-052](../adr/ADR-052-jsonb-vs-eav-custom-field-storage.md) (JSONB vs EAV), [ADR-053](../adr/ADR-053-field-pack-seeding-strategy.md) (field pack seeding), [ADR-054](../adr/ADR-054-tag-storage-join-table-vs-array.md) (tag storage), [ADR-055](../adr/ADR-055-saved-view-filter-execution.md) (saved view filters)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 87 | Field Definition & Group Backend | Backend | -- | L | 87A, 87B, 87C | |
| 88 | Custom Field Values on Entities | Backend | 87 | M | 88A, 88B | |
| 89 | Tag Backend | Backend | 87A (migration) | M | 89A, 89B | |
| 90 | Field Pack Infrastructure | Backend | 87, 88 | M | 90A, 90B | |
| 91 | Saved View Backend | Backend | 88, 89 | M | 91A, 91B | |
| 92 | Saved View Frontend — Filters & Views | Frontend | 91 | M | 92A, 92B | |
| 93 | Field & Tag Management Frontend | Frontend | 87, 89 | L | 93A, 93B, 93C | |
| 94 | Entity Detail Custom Fields & Tags Frontend | Frontend | 88, 89, 92 | L | 94A, 94B, 94C | |

## Dependency Graph

```
[E87 Field Definition & Group Backend]
      (87A: Migration, 87B: FieldDefinition, 87C: FieldGroup)
          │
          ├──────────────────────────────┐
          │                              │
          ▼                              ▼
[E88 Custom Field Values]          [E89 Tag Backend]
    (Backend)                          (Backend)
    88A: Validator                     89A: Tag CRUD
    88B: Entity Integration            89B: Entity Tags + Filtering
          │                              │
          ├──────────────┬───────────────┤
          │              │               │
          ▼              ▼               │
    [E90 Field Pack]  [E91 Saved View]  │
      (Backend)         (Backend)        │
      90A: Seeder       91A: Entity      │
      90B: Integration  91B: Filter Exec │
                             │           │
                             ├───────────┤
                             ▼           ▼
                        [E92 Saved View Frontend]
                             (Frontend)
                             92A: View Selector
                             92B: Filters
                                  │
          ┌───────────────────────┴───────────────────────┐
          │                                               │
          ▼                                               ▼
[E93 Field & Tag Settings Frontend]         [E94 Entity Detail UI]
        (Frontend)                                (Frontend)
        93A: Field Settings                       94A: Custom Fields
        93B: Tag Settings                         94B: Tag Selection
        93C: Field Groups                         94C: Integration
```

**Parallel tracks**:
- After Epic 87A completes: Epics 87B, 87C, and 89A can all run in parallel (migration is foundation).
- After Epic 88A completes: Epic 90A (field pack seeding) can begin.
- After Epic 91B completes: Epic 92 (saved view frontend) can begin.
- After Epic 89B completes: Epic 93B (tag settings) can begin in parallel with 92.
- Epic 94 (entity detail UI) is the final convergence — depends on all backend APIs.

## Implementation Order

### Stage 1: Backend Entity Foundation

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1 | Epic 87 | 87A | V24 migration (6 new tables: field_definitions, field_groups, field_group_members, tags, entity_tags, saved_views; 3 ALTER statements: projects/tasks/customers.custom_fields + applied_field_groups; org_settings.field_pack_status). Foundation for everything. |
| 2 | Epic 87 | 87B | FieldDefinition entity/repo/service/controller, EntityType/FieldType enums, slug generation, audit events. Depends on V24 from 87A. |
| 3 | Epic 87 | 87C | FieldGroup + FieldGroupMember entities/repos/services/controllers, group membership management. Depends on V24 + FieldDefinition from 87B. |

### Stage 2: Custom Fields, Tags & Pack Infrastructure (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 88 | 88A | CustomFieldValidator service, validation logic for all 9 field types. Depends on 87B (field definitions). |
| 4b | Epic 89 | 89A | Tag entity/repo/service/controller, tag CRUD endpoints, audit events. Depends on 87A (migration). Can run parallel with 88A. |
| 5 | Epic 88 | 88B | Modify Project/Task/Customer entities, services, controllers to accept/validate customFields. Custom field filtering on list endpoints. Depends on 88A. |
| 6a | Epic 89 | 89B | EntityTag entity/repo, entity tag endpoints (POST/GET /api/{entityType}/{entityId}/tags), tag filtering on list endpoints, tag data in list response DTOs. Depends on 89A. |
| 6b | Epic 90 | 90A | FieldPackSeeder service, JSON pack files (common-customer.json, common-project.json), FieldPackDefinition record. Depends on 88A (validator). Can run parallel with 89B. |
| 7 | Epic 90 | 90B | Integration with TenantProvisioningService, OrgSettings.fieldPackStatus tracking, pack update detection logic. Depends on 90A + 88B (entity integration). |

### Stage 3: Saved Views

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 8 | Epic 91 | 91A | SavedView entity/repo/service/controller, saved view CRUD endpoints, RBAC (shared vs personal). Depends on 88B + 89B (filtering APIs exist). |
| 9 | Epic 91 | 91B | SavedViewFilterService, Spring Data Specification-based filter execution, JSONB containment predicates, tag EXISTS subqueries. Depends on 91A. |

### Stage 4: Frontend — Views & Filters

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 10 | Epic 92 | 92A | View selector component, "Save View" button, view management (create/edit/delete dialogs), URL search param serialization. Depends on 91A (saved view API). |
| 11 | Epic 92 | 92B | Filter builder components (status, tags, custom fields, date range, search), custom column rendering (`cf:slug` prefix), tag badges in lists. Depends on 91B (filter execution) + 92A. |

### Stage 5: Frontend — Settings & Entity Detail

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 12 | Epic 93 | 93A | Settings > Custom Fields page — field definition list per entity type, add/edit field dialog, field type selector, validation config. Depends on 87B (field API). |
| 13 | Epic 93 | 93B | Settings > Tags page — tag list with color/usage count, add/edit/delete dialogs, color picker. Depends on 89A (tag API). Can run parallel with 93A. |
| 14 | Epic 93 | 93C | Field group management UI on settings page, group field membership, group assignment to entities. Depends on 87C (group API) + 93A. |
| 15 | Epic 94 | 94A | Custom fields section on project/task/customer detail pages — grouped display, type-appropriate input components (DatePicker, Select, Input, Checkbox), "Add value" placeholders. Depends on 88B (entity custom fields API) + 93A (field UI components). |
| 16 | Epic 94 | 94B | Tag selection UI — tag badges below title, popover with auto-complete, tag creation inline. Depends on 89B (entity tag API) + 93B (tag components). Can run parallel with 94A. |
| 17 | Epic 94 | 94C | Integration tests for entity detail UI, applied field groups UI (`PUT /api/{entityType}/{entityId}/field-groups`), sidebar nav links. Depends on 94A + 94B. |

### Timeline

```
Stage 1:  [87A] → [87B] → [87C]                                   ← field/group entities
Stage 2:  [88A // 89A] → [88B, 89B // 90A] → [90B]                ← custom fields + tags + packs (parallel)
Stage 3:  [91A] → [91B]                                           ← saved views
Stage 4:  [92A] → [92B]                                           ← saved view frontend
Stage 5:  [93A // 93B] → [93C, 94A // 94B] → [94C]                ← settings + entity detail (parallel)
```

---

## Epic 87: Field Definition & Group Backend

**Goal**: Create V24 migration with all 6 new tables (field_definitions, field_groups, field_group_members, tags, entity_tags, saved_views), ALTER existing tables (projects/tasks/customers add custom_fields + applied_field_groups columns, org_settings add field_pack_status), implement FieldDefinition + FieldGroup + FieldGroupMember entities with full TenantAware pattern, RBAC (admin/owner write, any member read), slug generation, validation, and audit events.

**References**: Architecture doc Sections 11.2.1-11.2.3, 11.2.7, 11.3.1, 11.7, 11.9. [ADR-052](../adr/ADR-052-jsonb-vs-eav-custom-field-storage.md).

**Dependencies**: None (builds on existing multi-tenant infrastructure)

**Scope**: Backend

**Estimated Effort**: L (large due to migration complexity and 3 entity packages)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **87A** | 87.1-87.3 | V24 migration (all tables + alterations), shared-schema RLS, GIN indexes on JSONB columns | |
| **87B** | 87.4-87.11 | FieldDefinition entity/repo/service/controller, EntityType/FieldType enums, slug generation, CRUD endpoints, audit events, tests | |
| **87C** | 87.12-87.19 | FieldGroup + FieldGroupMember entities/repos/services/controllers, group membership, CRUD endpoints, audit events, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 87.1 | Create V24 tenant migration | 87A | | `db/migration/tenant/V24__add_tags_custom_fields_views.sql`. Six new tables (field_definitions, field_groups, field_group_members, tags, entity_tags, saved_views) with all columns, constraints, and indexes per Section 11.9. ALTER TABLE projects/tasks/customers ADD COLUMN custom_fields JSONB DEFAULT '{}', ADD COLUMN applied_field_groups JSONB. GIN indexes: idx_projects_custom_fields, idx_tasks_custom_fields, idx_customers_custom_fields. ALTER TABLE org_settings ADD COLUMN field_pack_status JSONB. Constraints: chk_slug_format on field_definitions, chk_pack_consistency. Unique indexes: idx_fd_tenant_entitytype_slug, idx_fg_tenant_entitytype_slug, idx_tags_tenant_slug, idx_sv_personal_unique (partial WHERE shared = false), idx_sv_shared_unique (partial WHERE shared = true). Pattern: follow `V23__add_invoices.sql` for table structure with JSONB columns + GIN indexes. ~250 lines. |
| 87.2 | Create V24 shared-schema migration with RLS | 87A | | `db/migration/global/V24__add_tags_custom_fields_views_shared.sql`. Same DDL as tenant migration, plus RLS policies on all 6 new tables using `USING/WITH CHECK (tenant_id = current_setting('app.current_tenant'))`. Pattern: follow existing RLS policies in `V23__add_invoices_shared.sql`. ~280 lines. |
| 87.3 | Verify migration in both dedicated and shared schemas | 87A | | Write MigrationVerificationTest — provision one dedicated tenant, one shared tenant, verify all 6 tables exist with correct columns/indexes/constraints, verify JSONB columns exist on projects/tasks/customers with GIN indexes, verify RLS policies active in shared schema. ~1 test class, 2 test methods. Pattern: follow existing migration verification tests. |
| 87.4 | Create EntityType and FieldType enums | 87B | | `fielddefinition/EntityType.java` — enum values: PROJECT, TASK, CUSTOMER. `fielddefinition/FieldType.java` — enum values: TEXT, NUMBER, DATE, DROPDOWN, BOOLEAN, CURRENCY, URL, EMAIL, PHONE. No additional methods needed (backend validation will handle semantics). Pattern: simple enums like `InvoiceStatus.java`. |
| 87.5 | Create FieldDefinition entity | 87B | | `fielddefinition/FieldDefinition.java` — JPA entity mapped to field_definitions. All fields per Section 11.2.1 (17 columns). `@FilterDef`/`@Filter` for `tenantFilter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. `@JdbcTypeCode(SqlTypes.JSON)` for defaultValue, options, validation JSONB columns. Domain methods: `deactivate()` (sets active = false), `canEdit()` (returns active), `update(name, description, required, defaultValue, options, validation, sortOrder)` (validates active == true before update). Constructor with required fields: `entityType, name, slug, fieldType`. Pattern: follow `Invoice.java` for entity structure with JSONB columns. |
| 87.6 | Create FieldDefinitionRepository | 87B | | `fielddefinition/FieldDefinitionRepository.java` — extends `JpaRepository<FieldDefinition, UUID>`. Methods: `findOneById(UUID id)` (JPQL, bypasses findById), `List<FieldDefinition> findByEntityTypeAndActiveOrderBySortOrder(EntityType entityType, boolean active)`, `Optional<FieldDefinition> findByEntityTypeAndSlug(EntityType entityType, String slug)`, `List<FieldDefinition> findByPackId(String packId)`. Pattern: follow `InvoiceRepository.java` for JPQL findOneById. |
| 87.7 | Create FieldDefinitionService | 87B | | `fielddefinition/FieldDefinitionService.java` — `@Service`. Methods: `createFieldDefinition(CreateFieldDefRequest)` — generates slug if not provided via `generateSlug(name)`, validates uniqueness (entity_type + slug), validates DROPDOWN has non-empty options, persists, publishes FIELD_DEFINITION_CREATED audit event. `updateFieldDefinition(UUID id, UpdateFieldDefRequest)` — loads by ID, checks canEdit(), validates, persists, publishes FIELD_DEFINITION_UPDATED audit event. `deactivateFieldDefinition(UUID id)` — soft-delete, publishes FIELD_DEFINITION_DELETED. `listActiveFieldDefinitions(EntityType entityType)`. Slug generation: lowercase, replace non-alphanumeric with underscore, strip leading/trailing underscores, collapse multiple underscores. Pattern: follow `InvoiceService.java` for create/update/audit patterns. ~200 lines. |
| 87.8 | Create FieldDefinitionController | 87B | | `fielddefinition/FieldDefinitionController.java` — `@RestController`, `@RequestMapping("/api/field-definitions")`. Endpoints: `GET /?entityType={type}` → `listActiveFieldDefinitions()`, `GET /{id}` → `getFieldDefinition()`, `POST /` → `createFieldDefinition()` (`@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")`), `PUT /{id}` → `updateFieldDefinition()` (admin/owner), `DELETE /{id}` → `deactivateFieldDefinition()` (admin/owner). Request/response DTOs as nested records. Pattern: follow `InvoiceController.java` for DTO structure. ~150 lines. |
| 87.9 | Add audit event types for field definitions | 87B | | Modify `audit/AuditEventType.java` — add enum values: FIELD_DEFINITION_CREATED, FIELD_DEFINITION_UPDATED, FIELD_DEFINITION_DELETED. No additional logic needed (AuditService handles these generically). Pattern: follow existing audit event types. |
| 87.10 | Add field definition integration tests (creation & listing) | 87B | | `fielddefinition/FieldDefinitionIntegrationTest.java` (~12 tests): Create field definition (TEXT type, all required fields), create with auto-generated slug, create DROPDOWN with options, create fails without options for DROPDOWN, slug uniqueness validation (same entity_type + slug), deactivate field definition, update field definition, update fails when inactive, list active field definitions filtered by entity_type, audit events published (created/updated/deleted), tenant isolation (tenant A cannot see tenant B's fields). Seed: provision tenant, sync 1 member (admin). Pattern: follow `InvoiceEntityIntegrationTest.java` for test structure. |
| 87.11 | Add field definition RBAC tests | 87B | | `fielddefinition/FieldDefinitionSecurityTest.java` (~6 tests): Admin can create/update/delete, Owner can create/update/delete, Project Lead cannot create/update/delete (403), Contributor cannot create/update/delete (403), any member can list/get field definitions. Use `@WithMockUser` + MockMvc. Pattern: follow existing RBAC tests in `InvoiceControllerTest.java`. |
| 87.12 | Create FieldGroup entity | 87C | | `fieldgroup/FieldGroup.java` — JPA entity mapped to field_groups. All fields per Section 11.2.2 (10 columns). `@FilterDef`/`@Filter`/`TenantAware` pattern. Domain methods: `deactivate()`, `canEdit()`, `update(name, description, sortOrder)`. Constructor: `entityType, name, slug`. Pattern: follow `FieldDefinition.java` from 87B. |
| 87.13 | Create FieldGroupMember entity | 87C | | `fieldgroup/FieldGroupMember.java` — JPA entity mapped to field_group_members. All fields per Section 11.2.3 (5 columns). `@FilterDef`/`@Filter`/`TenantAware` pattern. Constructor: `fieldGroupId, fieldDefinitionId, sortOrder`. Pattern: follow join entity pattern from `CustomerProject.java` (customer-project linking). |
| 87.14 | Create FieldGroupRepository and FieldGroupMemberRepository | 87C | | `fieldgroup/FieldGroupRepository.java` — extends `JpaRepository<FieldGroup, UUID>`. Methods: `findOneById(UUID id)`, `List<FieldGroup> findByEntityTypeAndActiveOrderBySortOrder(EntityType entityType, boolean active)`. `fieldgroup/FieldGroupMemberRepository.java` — extends `JpaRepository<FieldGroupMember, UUID>`. Methods: `List<FieldGroupMember> findByFieldGroupIdOrderBySortOrder(UUID groupId)`, `void deleteByFieldGroupId(UUID groupId)`, `Optional<FieldGroupMember> findByFieldGroupIdAndFieldDefinitionId(UUID groupId, UUID fieldDefId)`. Pattern: follow repository patterns from 87B. |
| 87.15 | Create FieldGroupService | 87C | | `fieldgroup/FieldGroupService.java` — `@Service`. Methods: `createFieldGroup(CreateFieldGroupRequest)` — validates all field definition IDs exist and match entityType, creates FieldGroup, creates FieldGroupMember records, publishes FIELD_GROUP_CREATED. `updateFieldGroup(UUID id, UpdateFieldGroupRequest)` — updates name/description/sortOrder, replaces field membership (DELETE existing, INSERT new), validates entity_type match, publishes FIELD_GROUP_UPDATED. `deactivateFieldGroup(UUID id)` — soft-delete, publishes FIELD_GROUP_DELETED. `listActiveFieldGroups(EntityType entityType)` — returns groups with nested field definition list. `getFieldGroupWithMembers(UUID id)`. Validation: all field definitions in a group must share the same entity_type as the group. Pattern: follow service patterns from 87B + join entity management from `ProjectMemberService.java`. ~250 lines. |
| 87.16 | Create FieldGroupController | 87C | | `fieldgroup/FieldGroupController.java` — `@RestController`, `@RequestMapping("/api/field-groups")`. Endpoints: `GET /?entityType={type}` → `listActiveFieldGroups()` (includes nested fields), `POST /` → `createFieldGroup()` (admin/owner), `PUT /{id}` → `updateFieldGroup()` (admin/owner), `DELETE /{id}` → `deactivateFieldGroup()` (admin/owner). Request DTO includes `fieldDefinitionIds: List<UUID>`. Response DTO includes nested `fields: List<FieldDefinitionSummary>`. Pattern: follow controller patterns from 87B. ~120 lines. |
| 87.17 | Add audit event types for field groups | 87C | | Modify `audit/AuditEventType.java` — add: FIELD_GROUP_CREATED, FIELD_GROUP_UPDATED, FIELD_GROUP_DELETED. |
| 87.18 | Add field group integration tests | 87C | | `fieldgroup/FieldGroupIntegrationTest.java` (~10 tests): Create field group with 3 field definitions, create fails when field definition not found, create fails when field definitions have mismatched entity_type, update group (name change), update group membership (add/remove fields), deactivate group, list active groups with nested fields, audit events published, tenant isolation. Seed: provision tenant, sync 1 admin, create 5 field definitions (3 for PROJECT, 2 for CUSTOMER). Pattern: follow integration test patterns from 87B. |
| 87.19 | Add field group RBAC tests | 87C | | `fieldgroup/FieldGroupSecurityTest.java` (~5 tests): Admin/Owner can create/update/delete, Lead/Contributor cannot (403), any member can list/get groups. Pattern: follow RBAC test patterns from 87B. |

### Key Files

**Slice 87A — Create:**
- `backend/src/main/resources/db/migration/tenant/V24__add_tags_custom_fields_views.sql`
- `backend/src/main/resources/db/migration/global/V24__add_tags_custom_fields_views_shared.sql`

**Slice 87A — Read for context:**
- `backend/src/main/resources/db/migration/tenant/V23__add_invoices.sql` — Migration pattern with JSONB + GIN indexes
- `backend/src/main/resources/db/migration/global/V23__add_invoices_shared.sql` — RLS policy pattern

**Slice 87B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/EntityType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionSecurityTest.java`

**Slice 87B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventType.java` — Add FIELD_DEFINITION_* events

**Slice 87B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` — Entity with JSONB columns pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — Service with audit event publishing
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` — Interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` — Listener

**Slice 87C — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroupMember.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroupRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroupMemberRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroupService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroupController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroupIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fieldgroup/FieldGroupSecurityTest.java`

**Slice 87C — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventType.java` — Add FIELD_GROUP_* events

**Slice 87C — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMember.java` — Join entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberService.java` — Join entity management

### Architecture Decisions

- [ADR-052](../adr/ADR-052-jsonb-vs-eav-custom-field-storage.md) — JSONB vs. EAV for custom field storage (chose JSONB for simplicity, single-query reads, and natural tenant isolation)

---

## Epic 88: Custom Field Values on Entities

**Goal**: Add `customFields` (Map<String, Object>) and `appliedFieldGroups` (List<UUID>) to Project, Task, Customer entities. Implement CustomFieldValidator service with type-specific validation for all 9 field types. Modify existing entity services to accept and validate custom fields on create/update. Add custom field filtering on list endpoints using JSONB containment queries. Add endpoint for setting applied field groups on entities.

**References**: Architecture doc Sections 11.2.7, 11.2.8, 11.3.2, 11.4.5.

**Dependencies**: Epic 87 (field definitions must exist for validation)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **88A** | 88.1-88.5 | CustomFieldValidator service with type-specific validation logic, validation tests | |
| **88B** | 88.6-88.13 | Modify Project/Task/Customer entities + services + controllers, custom field filtering on lists, applied field groups endpoint, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 88.1 | Create CustomFieldValidator service | 88A | | `fielddefinition/CustomFieldValidator.java` — `@Service`. Injected: `FieldDefinitionRepository`. Method: `ValidationResult validate(EntityType entityType, Map<String, Object> customFields, List<UUID> appliedGroupIds)`. Logic: (1) Load active field definitions for entityType. (2) Strip unknown keys from customFields (not matching any slug). (3) For each key, validate value against field type (see Section 11.2.8 for type semantics). (4) For DROPDOWN, verify value exists in options array. (5) For required fields: if field belongs to any appliedGroup, value must be non-null. (6) Return ValidationResult record with `valid: boolean, fieldErrors: List<FieldError>` where FieldError is `record(String field, String message)`. Pattern: validation service similar to `InvoiceService` validation logic but standalone. ~150 lines. |
| 88.2 | Add TEXT field validation | 88A | | In CustomFieldValidator, add private method `validateTextField(FieldDefinition def, Object value)` — verify value is String, apply minLength/maxLength/pattern validation from def.validation JSONB. Return FieldError if invalid. Pattern: standard string validation. |
| 88.3 | Add NUMBER/CURRENCY field validation | 88A | | Add `validateNumberField()` — verify value is Number (Integer/Long/Double/BigDecimal), apply min/max validation. Add `validateCurrencyField()` — verify value is Map with keys "amount" (number), "currency" (3-char ISO 4217 code), apply min/max on amount. Pattern: type checking + range validation. |
| 88.4 | Add DATE/DROPDOWN/BOOLEAN/URL/EMAIL/PHONE validation | 88A | | Add validation methods for remaining types: `validateDateField()` (ISO 8601 string, parseable), `validateDropdownField()` (value in options list), `validateBooleanField()` (true/false), `validateUrlField()` (URL format), `validateEmailField()` (email format), `validatePhoneField()` (freeform, any string). Pattern: type + format validation. |
| 88.5 | Add CustomFieldValidator unit tests | 88A | | `fielddefinition/CustomFieldValidatorTest.java` (~15 tests): Valid TEXT field, TEXT exceeds maxLength, NUMBER below min, DROPDOWN invalid option, CURRENCY missing amount, DATE invalid format, BOOLEAN not boolean, required field missing when group applied, unknown keys stripped, multiple validation errors accumulated. Use `@ExtendWith(MockitoExtension.class)`, mock FieldDefinitionRepository. Pattern: unit test with mocks. |
| 88.6 | Add customFields and appliedFieldGroups to Project/Task/Customer entities | 88B | | Modify `project/Project.java`, `task/Task.java`, `customer/Customer.java` — add fields: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "custom_fields", columnDefinition = "jsonb") private Map<String, Object> customFields = new HashMap<>()` and `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "applied_field_groups", columnDefinition = "jsonb") private List<UUID> appliedFieldGroups`. Add setter methods: `setCustomFields(Map<String, Object>)`, `setAppliedFieldGroups(List<UUID>)`. Pattern: follow `Invoice.java` for JSONB field mapping. ~3 files modified. |
| 88.7 | Modify ProjectService/TaskService/CustomerService to validate custom fields | 88B | | Inject CustomFieldValidator into all three services. Modify `createProject()`, `updateProject()` methods to accept customFields in request, call `validator.validate()`, throw `ValidationException` with field errors if invalid (400 response with ProblemDetail), persist validated customFields. Same for Task/Customer services. Pattern: follow validation patterns from existing service methods. ~3 files modified, ~30 lines added per file. |
| 88.8 | Modify Project/Task/Customer controllers to accept customFields | 88B | | Modify request DTOs (CreateProjectRequest, UpdateProjectRequest, etc.) to include `Map<String, Object> customFields` and `List<UUID> appliedFieldGroups`. Modify response DTOs to include these fields. Pass customFields through to service layer. Pattern: follow existing DTO structure in controllers. ~3 files modified, ~15 lines added per file. |
| 88.9 | Add custom field filtering to list endpoints | 88B | | Modify `ProjectController.listProjects()`, `TaskController.listTasks()`, `CustomerController.listCustomers()` to accept `customField[slug]=value` query params. Parse into Map<String, String>. Pass to service layer. In service layer, build JSONB containment predicates using Spring Data Specification: `WHERE custom_fields @> '{"slug": "value"}'::jsonb`. For numeric/date range queries, parse `customField[slug][op]=gt&customField[slug][value]=100` into operator-based predicates using `(custom_fields ->> 'slug')::numeric > :value`. Return filtered Page<>. Pattern: follow existing filter parameter handling in list endpoints, use Specification pattern from saved view backend (will be implemented in Epic 91). ~3 services modified, ~50 lines added per service. |
| 88.10 | Add applied field groups endpoint | 88B | | Add endpoint: `PUT /api/{entityType}/{entityId}/field-groups` with body `{ "groupIds": ["uuid1", "uuid2"] }`. In ProjectService/TaskService/CustomerService, add method `setAppliedFieldGroups(UUID entityId, List<UUID> groupIds)` — loads entity, sets appliedFieldGroups, persists. No validation needed (groupIds are advisory for UI, not enforced). Pattern: follow existing entity update patterns. ~3 controllers + services modified, ~20 lines each. |
| 88.11 | Add custom field integration tests (validation) | 88B | | `project/ProjectCustomFieldIntegrationTest.java` (~12 tests): Create project with TEXT custom field, create with NUMBER field, update custom fields (add value, change value, remove value), validation errors returned (field too long, number out of range, dropdown invalid option, required field missing), unknown keys stripped, custom field values persisted and retrieved correctly, appliedFieldGroups persisted. Seed: provision tenant, sync admin, create field definitions (TEXT, NUMBER, DROPDOWN), create field group. Pattern: follow integration test patterns from Epic 87. ~12 tests. Repeat for Task and Customer (~36 tests total). |
| 88.12 | Add custom field filtering integration tests | 88B | | `project/ProjectFilteringIntegrationTest.java` (~8 tests): Filter projects by TEXT custom field (exact match), filter by NUMBER custom field (range: gt, gte, lt, lte), filter by DROPDOWN custom field (eq), filter by multiple custom fields (AND logic), filter returns empty when no match, GIN index used (verify via EXPLAIN). Seed: create 10 projects with varied custom field values. Pattern: follow existing filtering tests. ~8 tests. Repeat for Task and Customer (~24 tests total). |
| 88.13 | Add applied field groups tests | 88B | | `project/ProjectFieldGroupsTest.java` (~3 tests): Set applied field groups on project, retrieve project with appliedFieldGroups, update appliedFieldGroups (replace). Pattern: simple CRUD tests. ~3 tests. Repeat for Task and Customer (~9 tests total). |

### Key Files

**Slice 88A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidatorTest.java`

**Slice 88B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` — Add customFields + appliedFieldGroups
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java` — Add customFields + appliedFieldGroups
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` — Add customFields + appliedFieldGroups
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — Validate custom fields, filtering
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Validate custom fields, filtering
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — Validate custom fields, filtering
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Accept customFields in DTOs
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Accept customFields in DTOs
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Accept customFields in DTOs

**Slice 88B — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectCustomFieldIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskCustomFieldIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerCustomFieldIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectFilteringIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskFilteringIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerFilteringIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectFieldGroupsTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskFieldGroupsTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerFieldGroupsTest.java`

**Slice 88B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` — JSONB field mapping pattern
- Existing service/controller validation patterns

### Architecture Decisions

- [ADR-052](../adr/ADR-052-jsonb-vs-eav-custom-field-storage.md) — JSONB column for custom field values

---

## Epic 89: Tag Backend

**Goal**: Implement Tag + EntityTag entities with TenantAware pattern, tag CRUD endpoints (admin/owner create/update/delete, any member read/apply), entity tag endpoints (POST/GET /api/{entityType}/{entityId}/tags), tag filtering on existing list endpoints using EXISTS subqueries, tag data in list response DTOs, cascade delete on tag removal, auto-complete search, and audit events.

**References**: Architecture doc Sections 11.2.4-11.2.5, 11.3.3, 11.4.3, 11.4.5. [ADR-054](../adr/ADR-054-tag-storage-join-table-vs-array.md).

**Dependencies**: Epic 87A (migration)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **89A** | 89.1-89.8 | Tag entity/repo/service/controller, tag CRUD endpoints, slug generation, audit events, RBAC, tests | |
| **89B** | 89.9-89.15 | EntityTag entity/repo, entity tag endpoints, tag filtering on lists, tag data in DTOs, cascade delete, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 89.1 | Create Tag entity | 89A | | `tag/Tag.java` — JPA entity mapped to tags. All fields per Section 11.2.4 (7 columns). `@FilterDef`/`@Filter`/`TenantAware` pattern. Domain methods: `update(String name, String color)` (regenerates slug from name). Constructor: `name, slug, color`. Slug generation: lowercase, replace non-alphanumeric with hyphen, strip leading/trailing hyphens. Pattern: follow `FieldDefinition.java` entity structure. |
| 89.2 | Create TagRepository | 89A | | `tag/TagRepository.java` — extends `JpaRepository<Tag, UUID>`. Methods: `findOneById(UUID id)`, `List<Tag> findAllByOrderByName()`, `List<Tag> findByNameContainingIgnoreCaseOrderByName(String search)`, `Optional<Tag> findBySlug(String slug)`. Pattern: follow repository patterns from Epic 87. |
| 89.3 | Create TagService | 89A | | `tag/TagService.java` — `@Service`. Methods: `createTag(CreateTagRequest)` — generates slug from name, validates uniqueness (slug), persists, publishes TAG_CREATED audit event. `updateTag(UUID id, UpdateTagRequest)` — updates name/color, regenerates slug, publishes TAG_UPDATED. `deleteTag(UUID id)` — hard delete (cascade removes EntityTag rows via FK), counts affected entities before delete, publishes TAG_DELETED with affected count. `listTags(Optional<String> search)` — returns all tags or filtered by name search. Slug generation: same pattern as Tag entity method. Pattern: follow `FieldDefinitionService.java`. ~150 lines. |
| 89.4 | Create TagController | 89A | | `tag/TagController.java` — `@RestController`, `@RequestMapping("/api/tags")`. Endpoints: `GET /` → `listTags()`, `GET /?search={query}` → `searchTags()`, `POST /` → `createTag()` (admin/owner), `PUT /{id}` → `updateTag()` (admin/owner), `DELETE /{id}` → `deleteTag()` (admin/owner). Request/response DTOs as nested records. Pattern: follow `FieldDefinitionController.java`. ~100 lines. |
| 89.5 | Add audit event types for tags | 89A | | Modify `audit/AuditEventType.java` — add: TAG_CREATED, TAG_DELETED. (TAG_UPDATED omitted per architecture doc — low-value event). |
| 89.6 | Add tag integration tests (CRUD) | 89A | | `tag/TagIntegrationTest.java` (~10 tests): Create tag with color, create tag without color (null), update tag name (slug regenerates), update tag color, delete tag, slug uniqueness validation, list all tags (ordered by name), search tags by name (partial match), audit events published (created/deleted), tenant isolation. Seed: provision tenant, sync admin. Pattern: follow integration test patterns from Epic 87. |
| 89.7 | Add tag RBAC tests | 89A | | `tag/TagSecurityTest.java` (~5 tests): Admin/Owner can create/update/delete, Lead/Contributor cannot (403), any member can list/search tags. Pattern: follow RBAC test patterns from Epic 87. |
| 89.8 | Add tag cascade delete test | 89A | | In TagIntegrationTest, add test: Create tag, create 3 EntityTag associations (different entity types), delete tag, verify EntityTag rows cascade-deleted. Uses native query to check entity_tags table directly. Pattern: FK cascade verification test. |
| 89.9 | Create EntityTag entity | 89B | | `tag/EntityTag.java` — JPA entity mapped to entity_tags. All fields per Section 11.2.5 (6 columns). `@FilterDef`/`@Filter`/`TenantAware` pattern. `@ManyToOne` relationship to Tag (eager fetch for tag metadata). Polymorphic reference: `entityType` (enum), `entityId` (UUID, no FK). Constructor: `tagId, entityType, entityId`. Pattern: follow polymorphic pattern from `comment/Comment.java` (entity_type + entity_id). |
| 89.10 | Create EntityTagRepository | 89B | | `tag/EntityTagRepository.java` — extends `JpaRepository<EntityTag, UUID>`. Methods: `List<EntityTag> findByEntityTypeAndEntityId(EntityType entityType, UUID entityId)` (fetch-join Tag), `void deleteByEntityTypeAndEntityId(EntityType entityType, UUID entityId)`, `boolean existsByTagIdAndEntityTypeAndEntityId(UUID tagId, EntityType entityType, UUID entityId)`. Pattern: follow repository patterns from Epic 87. |
| 89.11 | Add entity tag endpoints | 89B | | Modify `ProjectController`, `TaskController`, `CustomerController` — add endpoints: `POST /api/{entityType}/{entityId}/tags` with body `{ "tagIds": ["uuid1", "uuid2"] }` → full replacement (DELETE all existing, INSERT new). `GET /api/{entityType}/{entityId}/tags` → returns List<Tag>. Service methods: `setEntityTags(UUID entityId, List<UUID> tagIds)` in ProjectService/TaskService/CustomerService — validates all tag IDs exist, deletes existing EntityTag rows, creates new rows. RBAC: any member can apply tags (same as edit entity). Pattern: follow entity tag application flow from Section 11.3.3. ~3 controllers + services modified, ~30 lines each. |
| 89.12 | Add tag filtering to list endpoints | 89B | | Modify `ProjectService.listProjects()`, `TaskService.listTasks()`, `CustomerService.listCustomers()` to accept `tags=slug1,slug2` query param (comma-separated). Parse into List<String>. Build Spring Data Specification with EXISTS subqueries for each tag slug (AND logic): `WHERE EXISTS (SELECT 1 FROM entity_tags et JOIN tags t ON t.id = et.tag_id WHERE t.slug = :slug AND et.entity_type = :type AND et.entity_id = root.id)`. Multiple tags = multiple EXISTS clauses ANDed. Return filtered Page<>. Pattern: follow filtering patterns from Epic 88 + Section 11.4.3. ~3 services modified, ~40 lines added per service. |
| 89.13 | Add tags to list response DTOs | 89B | | Modify response DTOs for `ProjectController.listProjects()`, `TaskController.listTasks()`, `CustomerController.listCustomers()` — include `tags: List<TagSummary>` where TagSummary = `record(UUID id, String name, String slug, String color)`. In service layer, batch-load EntityTag + Tag for all entities in the page using `IN (:entityIds)` query. Map results back to DTOs. Pattern: follow batch loading pattern for related entities (similar to how project members are loaded for project lists). ~3 controllers + services modified, ~30 lines added per controller. |
| 89.14 | Add entity tag integration tests | 89B | | `project/ProjectTagIntegrationTest.java` (~8 tests): Set tags on project (POST with tagIds), get tags for project (returns Tag list), replace tags (full replacement), set empty tags (removes all), tag filtering (single tag, multiple tags with AND logic), tag data in list response (includes tag name/slug/color), invalid tag ID validation (404). Seed: provision tenant, create 5 tags, create 10 projects. Pattern: follow integration test patterns. ~8 tests. Repeat for Task and Customer (~24 tests total). |
| 89.15 | Add tag filtering integration tests | 89B | | `project/ProjectTagFilteringTest.java` (~6 tests): Filter projects by single tag, filter by multiple tags (AND logic), filter returns empty when no match, filtered count correct, tag filtering combined with status filter, tag filtering with pagination. Seed: create 15 projects with varied tag assignments. Pattern: follow filtering test patterns. ~6 tests. Repeat for Task and Customer (~18 tests total). |

### Key Files

**Slice 89A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/Tag.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/tag/TagIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/tag/TagSecurityTest.java`

**Slice 89A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventType.java` — Add TAG_* events

**Slice 89B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTag.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTagRepository.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectTagIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskTagIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerTagIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectTagFilteringTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskTagFilteringTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerTagFilteringTest.java`

**Slice 89B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — Tag filtering + setEntityTags
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Tag filtering + setEntityTags
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — Tag filtering + setEntityTags
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Tag endpoints + DTO changes
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Tag endpoints + DTO changes
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Tag endpoints + DTO changes

**Slice 89B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java` — Polymorphic entity_type + entity_id pattern

### Architecture Decisions

- [ADR-054](../adr/ADR-054-tag-storage-join-table-vs-array.md) — Join table vs. array column for tags (chose join table for referential integrity, usage counting, and standard SQL filtering)

---

## Epic 90: Field Pack Infrastructure

**Goal**: Implement FieldPackSeeder service to load field pack JSON files from classpath during tenant provisioning. Create JSON pack files (`common-customer.json`, `common-project.json`) with sample field definitions and groups. Integrate seeding with TenantProvisioningService. Track applied packs in OrgSettings.fieldPackStatus. Implement pack update detection logic for future use.

**References**: Architecture doc Sections 11.3.5, 11.8 Slice D. [ADR-053](../adr/ADR-053-field-pack-seeding-strategy.md).

**Dependencies**: Epic 87 (field definition entities), Epic 88 (OrgSettings alteration, CustomFieldValidator)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **90A** | 90.1-90.6 | FieldPackSeeder service, JSON pack files, FieldPackDefinition record, pack loading logic, unit tests | |
| **90B** | 90.7-90.10 | Integration with TenantProvisioningService, OrgSettings.fieldPackStatus tracking, pack update detection, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 90.1 | Create FieldPackDefinition record | 90A | | `fielddefinition/pack/FieldPackDefinition.java` — record for JSON deserialization. Fields: `String packId`, `int version`, `String name`, `String description`, `EntityType entityType`, `List<FieldDefSpec> fields`, `FieldGroupSpec group`. Nested records: `FieldDefSpec(String key, String name, String slug, FieldType fieldType, String description, boolean required, Map<String,Object> defaultValue, List<Map<String,String>> options, Map<String,Object> validation, int sortOrder)` and `FieldGroupSpec(String name, String slug, String description, int sortOrder)`. Pattern: simple record structure for JSON binding. |
| 90.2 | Create common-customer.json pack | 90A | | `resources/field-packs/common-customer.json` — JSON file defining 8 customer fields: `address_line1` (TEXT), `address_line2` (TEXT), `city` (TEXT), `postal_code` (TEXT), `phone` (PHONE), `tax_number` (TEXT with pattern validation), `industry` (DROPDOWN with 5 options: "Legal", "Accounting", "Marketing", "Technology", "Other"), `website` (URL). Group: "Contact & Address". packId: "common-customer", version: 1. Pattern: follow architecture doc Section 11.8 Slice D examples. ~60 lines JSON. |
| 90.3 | Create common-project.json pack | 90A | | `resources/field-packs/common-project.json` — JSON file defining 5 project fields: `project_code` (TEXT with pattern ^[A-Z0-9-]+$), `estimated_hours` (NUMBER with min 0), `start_date` (DATE), `end_date` (DATE), `is_billable` (BOOLEAN). Group: "Project Details". packId: "common-project", version: 1. ~50 lines JSON. |
| 90.4 | Create FieldPackSeeder service | 90A | | `fielddefinition/pack/FieldPackSeeder.java` — `@Service`. Injected: `ResourcePatternResolver`, `ObjectMapper`, `FieldDefinitionRepository`, `FieldGroupRepository`, `FieldGroupMemberRepository`. Method: `void seedPacks(String tenantId)` — (1) Scan classpath for `field-packs/*.json` using ResourcePatternResolver. (2) For each resource, deserialize to FieldPackDefinition. (3) Create FieldDefinition records with `pack_id` and `pack_field_key` set, tenant_id from param. (4) Create FieldGroup record with `pack_id` set. (5) Create FieldGroupMember records linking definitions to group. (6) Use `ScopedValue.where(RequestScopes.TENANT_ID, tenantId).run(() -> { repositories.save(...) })` pattern. (7) Return List<AppliedPackInfo> with `record(String packId, int version, Instant appliedAt)`. Pattern: classpath resource loading similar to Flyway's script scanning, scoped execution pattern from `TenantProvisioningService`. ~120 lines. |
| 90.5 | Add FieldPackSeeder unit tests | 90A | | `fielddefinition/pack/FieldPackSeederTest.java` (~5 tests): Deserialize common-customer.json (verify all fields present), deserialize common-project.json, seedPacks() creates FieldDefinition records with correct pack_id/pack_field_key, seedPacks() creates FieldGroup with correct pack_id, seedPacks() creates FieldGroupMember records. Use test-specific JSON files in `test/resources/field-packs/`. Mock repositories. Pattern: unit test with mocks. |
| 90.6 | Add pack JSON schema validation | 90A | | In FieldPackSeeder, add validation after deserialization: verify packId not empty, version > 0, entityType not null, fields list not empty, group not null. Throw IllegalArgumentException with descriptive message if invalid. Test: add unit test with invalid JSON (missing packId, negative version, empty fields). Pattern: defensive validation. ~2 tests added to 90.5. |
| 90.7 | Add fieldPackStatus field to OrgSettings entity | 90B | | Modify `orgsettings/OrgSettings.java` — add field: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "field_pack_status", columnDefinition = "jsonb") private List<Map<String, Object>> fieldPackStatus`. Add setter: `setFieldPackStatus(List<Map<String, Object>>)`. Pattern: follow JSONB field pattern from Invoice entity. ~5 lines added. |
| 90.8 | Integrate FieldPackSeeder with TenantProvisioningService | 90B | | Modify `provisioning/TenantProvisioningService.java` — inject FieldPackSeeder. In `provisionTenant()` method, after schema creation and migrations, call `List<AppliedPackInfo> appliedPacks = fieldPackSeeder.seedPacks(schemaName)`. Create OrgSettings record (or update if exists) with `fieldPackStatus = appliedPacks.stream().map(p -> Map.of("packId", p.packId(), "version", p.version(), "appliedAt", p.appliedAt())).toList()`. Use scoped execution pattern. Pattern: follow existing provisioning steps in TenantProvisioningService. ~15 lines added. |
| 90.9 | Add pack update detection logic | 90B | | In FieldPackSeeder, add method: `List<PackUpdate> detectUpdates(String tenantId, List<Map<String,Object>> currentStatus)` — (1) Load all pack JSON files from classpath. (2) For each pack, compare JSON version against currentStatus version for that packId. (3) If JSON version > currentStatus version: load existing field definitions by pack_id, compare pack_field_key values, identify new fields not yet in tenant. (4) Return List<PackUpdate> with `record(String packId, int currentVersion, int availableVersion, List<FieldDefSpec> newFields)`. Uses `ScopedValue.where()` for repo calls. Pattern: version comparison + field diff logic. ~80 lines. Future use: admin UI calls this to show available updates. |
| 90.10 | Add field pack integration tests | 90B | | `fielddefinition/pack/FieldPackIntegrationTest.java` (~8 tests): Provision tenant seeds packs (verify field definitions created with pack_id), OrgSettings.fieldPackStatus populated with pack info (common-customer and common-project), field definitions have correct pack_field_key, field group created with pack_id, field group members created, detect updates when pack version incremented (new fields identified), idempotency (seeding twice doesn't duplicate), tenant isolation (packs in tenant A independent of tenant B). Seed: provision 2 tenants. Pattern: follow provisioning test patterns. |

### Key Files

**Slice 90A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/pack/FieldPackDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/pack/FieldPackSeeder.java`
- `backend/src/main/resources/field-packs/common-customer.json`
- `backend/src/main/resources/field-packs/common-project.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/pack/FieldPackSeederTest.java`

**Slice 90B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgsettings/OrgSettings.java` — Add fieldPackStatus field
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — Call FieldPackSeeder

**Slice 90B — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/pack/FieldPackIntegrationTest.java`

**Slice 90B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — Provisioning flow pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` — JSONB field pattern

### Architecture Decisions

- [ADR-053](../adr/ADR-053-field-pack-seeding-strategy.md) — Per-tenant copy at provisioning (chose per-tenant seeding over shared global definitions for full independence and customization freedom)

---

## Epic 91: Saved View Backend

**Goal**: Implement SavedView entity with personal/shared views, saved view CRUD endpoints with RBAC (any member creates personal, admin/owner creates shared, creator or admin can update/delete), SavedViewFilterService to translate filter JSONB to Spring Data Specification predicates (status, tags, custom fields, date range, search), filter execution on existing list endpoints, and integration tests.

**References**: Architecture doc Sections 11.2.6, 11.3.4, 11.4.4, 11.5.2. [ADR-055](../adr/ADR-055-saved-view-filter-execution.md).

**Dependencies**: Epic 88 (custom field filtering), Epic 89 (tag filtering)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **91A** | 91.1-91.8 | SavedView entity/repo/service/controller, saved view CRUD endpoints, RBAC (personal vs shared), tests | |
| **91B** | 91.9-91.14 | SavedViewFilterService with Specification-based filter execution, JSONB containment + tag EXISTS predicates, integration with list endpoints, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 91.1 | Create SavedView entity | 91A | | `savedview/SavedView.java` — JPA entity mapped to saved_views. All fields per Section 11.2.6 (10 columns). `@FilterDef`/`@Filter`/`TenantAware` pattern. `@JdbcTypeCode(SqlTypes.JSON)` for filters and columns JSONB. Domain methods: `canEdit(UUID memberId)` (returns true if createdBy == memberId OR member is admin/owner — pass role as param), `update(name, filters, columns, shared)`. Constructor: `entityType, name, filters, columns, shared, createdBy`. Pattern: follow SavedView entity structure from architecture doc. |
| 91.2 | Create SavedViewRepository | 91A | | `savedview/SavedViewRepository.java` — extends `JpaRepository<SavedView, UUID>`. Methods: `findOneById(UUID id)`, `List<SavedView> findByEntityTypeAndSharedTrue(EntityType entityType)` (shared views for all members), `List<SavedView> findByEntityTypeAndCreatedBy(EntityType entityType, UUID createdBy)` (personal views), `boolean existsByEntityTypeAndNameAndSharedTrue(EntityType entityType, String name)` (check shared view name uniqueness), `boolean existsByEntityTypeAndNameAndCreatedByAndSharedFalse(EntityType entityType, String name, UUID createdBy)` (check personal view name uniqueness). Pattern: follow repository patterns from prior epics. |
| 91.3 | Create SavedViewService | 91A | | `savedview/SavedViewService.java` — `@Service`. Methods: `createSavedView(CreateSavedViewRequest)` — validates uniqueness (shared vs personal), checks requester role if shared (admin/owner only), persists, no audit event. `updateSavedView(UUID id, UpdateSavedViewRequest)` — loads view, checks `canEdit(memberId, orgRole)`, validates name uniqueness, persists. `deleteSavedView(UUID id)` — checks `canEdit()`, deletes. `listAvailableViews(EntityType entityType, UUID memberId)` — returns shared views + personal views for memberId. `getSavedView(UUID id)` — validates requester can see it (is creator OR view is shared). Pattern: follow service patterns from prior epics. ~180 lines. |
| 91.4 | Create SavedViewController | 91A | | `savedview/SavedViewController.java` — `@RestController`, `@RequestMapping("/api/views")`. Endpoints: `GET /?entityType={type}` → `listAvailableViews()` (any member), `POST /` → `createSavedView()` (any member for personal, admin/owner for shared — service enforces), `PUT /{id}` → `updateSavedView()` (creator or admin/owner — service enforces), `DELETE /{id}` → `deleteSavedView()` (creator or admin/owner — service enforces). Request DTOs include `filters: Map<String, Object>`, `columns: List<String>`, `shared: boolean`. Response DTOs include all fields. Pattern: follow controller patterns. ~120 lines. |
| 91.5 | Add saved view integration tests (CRUD) | 91A | | `savedview/SavedViewIntegrationTest.java` (~12 tests): Create personal view, create shared view (admin), create shared view fails for non-admin (403 enforced in service via role check), update personal view (creator), update personal view fails for non-creator (403), update shared view (admin), delete personal view, delete shared view (admin), list available views (returns shared + personal for requester), shared view name uniqueness validation, personal view name uniqueness validation (same name OK for different users), getSavedView validates access. Seed: provision tenant, sync 3 members (1 admin, 2 contributors). Pattern: follow integration test patterns. |
| 91.6 | Add saved view RBAC tests | 91A | | `savedview/SavedViewSecurityTest.java` (~6 tests): Any member can create personal view, admin/owner can create shared view, contributor cannot create shared view (service throws ForbiddenException -> 403), creator can update/delete personal view, non-creator cannot update/delete personal view (403), admin can update/delete any shared view. Pattern: follow RBAC test patterns. |
| 91.7 | Add partial unique indexes test | 91A | | In SavedViewIntegrationTest, add test: Create personal view "My Tasks" for user A, create personal view "My Tasks" for user B (succeeds — different creator), create shared view "Team Tasks", create shared view "Team Tasks" again (fails — duplicate shared name). Verifies partial unique indexes from migration. ~1 test. |
| 91.8 | Add filter JSONB validation | 91A | | In SavedViewService.createSavedView(), add basic validation: filters must be valid JSON object (not null, not array), columns (if provided) must be JSON array of strings. Throw ValidationException if invalid. Test: add test with invalid filters (non-object) and invalid columns (non-array). Pattern: defensive validation. ~2 tests added to 91.5. |
| 91.9 | Create SavedViewFilterService | 91B | | `savedview/SavedViewFilterService.java` — `@Service`. Injected: `FieldDefinitionRepository`, `TagRepository`. Method: `<T> Specification<T> buildSpecification(Map<String, Object> filters, EntityType entityType, Class<T> entityClass)` — parses filters map and returns composed Specification. Handles: (1) `status` key → `root.get("status").in(values)`. (2) `tags` key → for each slug, add EXISTS subquery `EXISTS (SELECT 1 FROM entity_tags et JOIN tags t ON ... WHERE t.slug = :slug AND et.entity_id = root.id)`. (3) `customFields` key → for each entry, parse operator (`eq`, `gt`, `gte`, `lt`, `lte`, `contains`) and build JSONB predicate (`custom_fields @> '{"slug":"value"}'` for eq, `(custom_fields ->> 'slug')::numeric > :value` for gt). (4) `dateRange` key → parse field + from/to, add range predicate on that field. (5) `search` key → ILIKE on name field. All predicates ANDed. Uses CriteriaBuilder.function() for JSONB operators. Pattern: follow Specification composition pattern from Spring Data docs + JSONB query examples from Epic 88. ~200 lines. |
| 91.10 | Add filter service unit tests | 91B | | `savedview/SavedViewFilterServiceTest.java` (~12 tests): Build spec with status filter, build spec with single tag filter, build spec with multiple tag filters (AND), build spec with custom field eq filter (JSONB @>), build spec with custom field gt filter (JSONB ->> cast), build spec with date range filter, build spec with search filter, build spec with combined filters (status + tags + customFields), empty filters returns no predicates (all results), null filters throws NPE or returns no predicates. Use `@ExtendWith(MockitoExtension.class)`, mock repositories. Pattern: unit test with mocks. |
| 91.11 | Integrate SavedViewFilterService with list endpoints | 91B | | Modify `ProjectController`, `TaskController`, `CustomerController` — add query param `view={viewId}`. If viewId present: load SavedView, call `SavedViewFilterService.buildSpecification(view.getFilters(), entityType, entityClass)`, pass Specification to service layer. Service layer applies spec to repository query. Return filtered Page<>. Pattern: follow existing filter param handling, add Specification composition to existing queries. ~3 controllers + services modified, ~25 lines added per pair. |
| 91.12 | Add filter execution integration tests (status + tags) | 91B | | `savedview/FilterExecutionIntegrationTest.java` (~8 tests): Create saved view with status filter (ACTIVE), apply view to project list (returns only ACTIVE projects). Create view with single tag filter, apply (returns tagged projects). Create view with multiple tag filters (AND logic), apply (returns projects with ALL tags). Create view with status + tag combined, apply. Create view with date range filter (created_at from/to), apply. Create view with search filter (ILIKE on name), apply. Apply view with empty filters (returns all). Apply non-existent view (404). Seed: create 15 projects with varied status, 5 tags, varied tag assignments. Pattern: follow integration test patterns. |
| 91.13 | Add filter execution integration tests (custom fields) | 91B | | `savedview/CustomFieldFilterExecutionTest.java` (~6 tests): Create saved view with custom field eq filter (TEXT field), apply (JSONB @> containment). Create view with custom field gt filter (NUMBER field), apply ((custom_fields ->> 'slug')::numeric > value). Create view with DROPDOWN filter, apply. Create view with DATE range filter on custom field, apply. Create view with combined status + custom field filters, apply. Verify GIN index usage on custom_fields via EXPLAIN ANALYZE (optional — manual verification). Seed: create 20 projects with varied custom field values. Pattern: follow integration test patterns from Epic 88. |
| 91.14 | Add filter execution performance test | 91B | | In FilterExecutionIntegrationTest, add test: Create 500 projects with varied custom fields and tags, create saved view with 3 filters (status + 2 custom fields), apply view, verify query completes in <500ms (reasonable threshold for JSONB + GIN index). Optional: log EXPLAIN ANALYZE output. Seed: bulk create 500 projects. Pattern: performance sanity check. ~1 test. |

### Key Files

**Slice 91A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/savedview/SavedView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/savedview/SavedViewRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/savedview/SavedViewService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/savedview/SavedViewController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/savedview/SavedViewIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/savedview/SavedViewSecurityTest.java`

**Slice 91B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/savedview/SavedViewFilterService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/savedview/SavedViewFilterServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/savedview/FilterExecutionIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/savedview/CustomFieldFilterExecutionTest.java`

**Slice 91B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Add view param
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Add view param
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Add view param
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — Apply Specification
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Apply Specification
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — Apply Specification

**Slice 91B — Read for context:**
- Spring Data JPA Specification documentation
- Existing filter parameter handling in list endpoints

### Architecture Decisions

- [ADR-055](../adr/ADR-055-saved-view-filter-execution.md) — Server-side filter execution with dynamic query building (chose server-side for correct pagination, index utilization, and security)

---

## Epic 92: Saved View Frontend — Filters & Views

**Goal**: Build frontend view selector component, "Save View" button, view management dialogs (create/edit/delete), URL search param serialization for filter state, filter builder components (status, tags, custom fields, date range, search), custom column rendering for `cf:slug` columns, and tag badges in list views.

**References**: Architecture doc Sections 11.4.4, 11.5.2.

**Dependencies**: Epic 91 (saved view backend APIs)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **92A** | 92.1-92.7 | View selector component, "Save View" button, view CRUD dialogs, URL search param handling, tests | |
| **92B** | 92.8-92.14 | Filter builder components (status, tags, custom fields, date range, search), custom column rendering, tag badges, integration, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 92.1 | Create ViewSelector component | 92A | | `components/views/view-selector.tsx` — `"use client"`. Displays saved view dropdown (Shadcn Select) with two sections: "Shared Views" and "My Views". Fetches views from `GET /api/views?entityType={type}` on mount. On selection, navigates to `?view={viewId}`. Shows "All {Entities}" as default option (no view filter). Pattern: follow existing dropdown components. Uses `useRouter` for navigation. ~80 lines. |
| 92.2 | Add ViewSelector to list pages | 92A | | Modify `app/(app)/org/[slug]/projects/page.tsx`, `customers/page.tsx` — add `<ViewSelector entityType={...} />` above the table. Pass current viewId from searchParams. Pattern: follow existing page component structure. ~2-3 files modified, ~10 lines each. |
| 92.3 | Create "Save View" button and dialog | 92A | | `components/views/save-view-dialog.tsx` — `"use client"`. Dialog with form: name (text input), shared (checkbox, only visible to admin/owner). On submit, calls server action `saveView(entityType, name, filters, columns, shared)` which POSTs to `/api/views`. Dialog shows when user has active filters but no view selected (or a modified view). Button appears in page header next to filters. Pattern: follow existing dialog patterns (LogTimeDialog). ~100 lines. |
| 92.4 | Create view management dialog | 92A | | `components/views/manage-views-dialog.tsx` — `"use client"`. Shows list of saved views (personal + shared) with edit/delete actions. Edit: opens inline form or modal to update name/shared. Delete: confirmation, then calls server action `deleteView(viewId)` which DELETEs `/api/views/{id}`. Only creator or admin can edit/delete. Pattern: follow existing management UI patterns. ~120 lines. |
| 92.5 | Add view server actions | 92A | | `app/(app)/org/[slug]/projects/view-actions.ts` (or shared `lib/view-actions.ts`) — server actions: `async function saveView(entityType, name, filters, columns, shared)` — calls API `POST /api/views` with JWT, revalidates page. `async function updateView(viewId, name, filters, columns, shared)` — calls `PUT /api/views/{id}`. `async function deleteView(viewId)` — calls `DELETE /api/views/{id}`. Pattern: follow existing server action patterns. ~60 lines. |
| 92.6 | Add URL search param serialization | 92A | | In list page server components, parse `searchParams.view` to load SavedView filters. Serialize active filters to URL search params: `?status=ACTIVE&tags=urgent,vip&customField[court]=high_court`. On filter change, update URL via `useRouter().push()` in client component. Pattern: follow existing URL-based state patterns. Modify list pages to accept filter params, pass to API calls. ~2-3 pages modified, ~30 lines added per page. |
| 92.7 | Add view selector tests | 92A | | `__tests__/components/views/view-selector.test.tsx` (~5 tests): Renders with default "All Projects" option, fetches and displays shared views, fetches and displays personal views, navigates to view URL on selection, shows correct view as selected from searchParams. Mock `GET /api/views` API. Pattern: follow existing component test patterns. |
| 92.8 | Create filter builder components — status, search | 92B | | `components/views/status-filter.tsx` — multi-select dropdown for status (ACTIVE, ON_HOLD, COMPLETED, CANCELLED). `components/views/search-filter.tsx` — text input with debounce (500ms). Both update URL search params on change. Pattern: follow existing filter components. ~50 lines each. |
| 92.9 | Create filter builder components — tags | 92B | | `components/views/tag-filter.tsx` — multi-select with auto-complete. Fetches all tags from `GET /api/tags`, allows selecting multiple. Shows selected tags as badges (removable). Updates URL search param `tags=slug1,slug2`. Pattern: follow Shadcn Command component for auto-complete, existing badge components for display. ~100 lines. |
| 92.10 | Create filter builder components — custom fields | 92B | | `components/views/custom-field-filter.tsx` — dynamic filter builder. Fetches field definitions for entity type from `GET /api/field-definitions?entityType={type}`. For each field type, renders appropriate input: TEXT/EMAIL/URL/PHONE = text input, NUMBER/CURRENCY = number input with operator dropdown (eq/gt/gte/lt/lte), DATE = date picker with operator, DROPDOWN = select with options, BOOLEAN = checkbox. Updates URL search param `customField[slug]=value` or `customField[slug][op]=gt&customField[slug][value]=100`. Pattern: follow type-specific input patterns from Shadcn components. ~150 lines. |
| 92.11 | Create custom column rendering logic | 92B | | Modify table components in list pages — accept `columns` prop from SavedView (or default columns if no view). For each column, if it starts with `cf:`, extract slug, look up field definition, render custom field value with appropriate formatting (number with commas, date with formatDate(), currency with symbol, URL as link, boolean as checkbox icon). Render tag badges for `tags` column. Pattern: follow existing table column rendering, custom formatters. ~2-3 table components modified, ~40 lines added per component. |
| 92.12 | Create tag badge component | 92B | | `components/views/tag-badge.tsx` — renders tag with name, color background (using tag.color or default neutral), and optional remove button. Used in tag filters and in table cells. Pattern: follow existing Badge component from Shadcn, add color prop. ~40 lines. |
| 92.13 | Add filter builder tests | 92B | | `__tests__/components/views/filter-builder.test.tsx` (~10 tests): Status filter selects options, search filter debounces input, tag filter auto-completes and selects tags, custom field filter renders TEXT input, custom field filter renders NUMBER with operator dropdown, custom field filter renders DATE picker, custom field filter renders DROPDOWN select, filter changes update URL search params, multiple filters combine correctly (AND logic). Mock API calls. Pattern: follow component test patterns. |
| 92.14 | Add custom column rendering tests | 92B | | `__tests__/components/projects/project-list.test.tsx` additions (~5 tests): Custom column rendering for TEXT field (displays value), custom column rendering for NUMBER field (formats with commas), custom column rendering for DATE field (uses formatDate), custom column rendering for CURRENCY field (shows amount + symbol), tag column rendering (shows tag badges with colors). Mock project data with custom fields and tags. Pattern: follow existing table test patterns. |

### Key Files

**Slice 92A — Create:**
- `frontend/components/views/view-selector.tsx`
- `frontend/components/views/save-view-dialog.tsx`
- `frontend/components/views/manage-views-dialog.tsx`
- `frontend/lib/view-actions.ts` (or per-entity actions file)
- `frontend/__tests__/components/views/view-selector.test.tsx`

**Slice 92A — Modify:**
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Add ViewSelector
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Add ViewSelector

**Slice 92B — Create:**
- `frontend/components/views/status-filter.tsx`
- `frontend/components/views/search-filter.tsx`
- `frontend/components/views/tag-filter.tsx`
- `frontend/components/views/custom-field-filter.tsx`
- `frontend/components/views/tag-badge.tsx`
- `frontend/__tests__/components/views/filter-builder.test.tsx`

**Slice 92B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Add filter builders, custom column rendering
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Add filter builders, custom column rendering
- `frontend/__tests__/components/projects/project-list.test.tsx` — Add custom column tests

**Slice 92B — Read for context:**
- `frontend/components/projects/log-time-dialog.tsx` — Dialog pattern
- `frontend/components/ui/badge.tsx` — Badge component
- Shadcn Command component for auto-complete

---

## Epic 93: Field & Tag Management Frontend

**Goal**: Build settings pages for custom field management (field definition list per entity type, add/edit field dialog, field type selector, validation config) and tag management (tag list with color/usage count, add/edit/delete dialogs, color picker). Add field group management UI. Add sidebar nav links under Settings.

**References**: Architecture doc Sections 11.3.1, 11.3.3, 11.8 Slice F.

**Dependencies**: Epic 87 (field API), Epic 89 (tag API)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **93A** | 93.1-93.7 | Settings > Custom Fields page — field definition list, add/edit dialog, field type selector, validation config, tests | |
| **93B** | 93.8-93.13 | Settings > Tags page — tag list with usage count, add/edit/delete dialogs, color picker, tests | |
| **93C** | 93.14-93.18 | Field group management UI on settings page, group field membership, group assignment, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 93.1 | Create settings custom fields page route | 93A | | `app/(app)/org/[slug]/settings/custom-fields/page.tsx` — server component. Fetches field definitions for all entity types (`GET /api/field-definitions?entityType={type}` x3). Renders tabs for PROJECT, TASK, CUSTOMER. Each tab shows field definition table (name, slug, type, required, active) with add/edit/deactivate actions. Pattern: follow existing settings page structure. ~100 lines. |
| 93.2 | Create FieldDefinitionTable component | 93A | | `components/settings/field-definition-table.tsx` — `"use client"`. Displays field definitions in table (columns: name, slug, type, required, active). Actions: edit (opens dialog), deactivate (confirmation + server action). Pattern: follow existing table components. ~80 lines. |
| 93.3 | Create AddFieldDialog component | 93A | | `components/settings/add-field-dialog.tsx` — `"use client"`. Form fields: entityType (select: PROJECT/TASK/CUSTOMER), name (text), slug (text, auto-generated from name), fieldType (select with 9 options), description (textarea), required (checkbox), defaultValue (dynamic based on fieldType), options (for DROPDOWN — multi-input), validation (min/max/pattern/minLength/maxLength based on fieldType). On submit, calls server action `createFieldDefinition()` which POSTs to `/api/field-definitions`. Pattern: follow existing form dialog patterns (LogTimeDialog), dynamic form fields based on type. ~200 lines. |
| 93.4 | Create EditFieldDialog component | 93A | | `components/settings/edit-field-dialog.tsx` — similar to AddFieldDialog but pre-filled with existing field definition data. fieldType and slug not editable (slug immutability per architecture). Calls `updateFieldDefinition(id, ...)` which PUTs to `/api/field-definitions/{id}`. Pattern: follow AddFieldDialog structure. ~180 lines. |
| 93.5 | Add field definition server actions | 93A | | `app/(app)/org/[slug]/settings/custom-fields/actions.ts` — server actions: `async function createFieldDefinition(entityType, name, slug, fieldType, description, required, defaultValue, options, validation, sortOrder)` — calls API `POST /api/field-definitions`, revalidates. `async function updateFieldDefinition(id, ...)` — calls `PUT /api/field-definitions/{id}`. `async function deactivateFieldDefinition(id)` — calls `DELETE /api/field-definitions/{id}`. Pattern: follow existing server action patterns. ~80 lines. |
| 93.6 | Add slug auto-generation logic | 93A | | In AddFieldDialog, add `useEffect` that watches `name` field and auto-generates `slug` using the same logic as backend (lowercase, replace non-alphanumeric with underscore, strip leading/trailing underscores). User can manually override slug. Pattern: client-side slug generation matching backend FieldDefinitionService.generateSlug(). ~20 lines added to AddFieldDialog. |
| 93.7 | Add custom fields settings tests | 93A | | `__tests__/app/settings/custom-fields.test.tsx` (~8 tests): Settings page renders field definitions per entity type, tab switching works (PROJECT/TASK/CUSTOMER), AddFieldDialog opens and submits (calls server action), slug auto-generated from name, EditFieldDialog pre-fills data, deactivate field confirmation, field type selector shows all 9 types, DROPDOWN type shows options input. Mock API calls. Pattern: follow existing settings page tests. |
| 93.8 | Create settings tags page route | 93B | | `app/(app)/org/[slug]/settings/tags/page.tsx` — server component. Fetches all tags (`GET /api/tags`). Fetches usage count per tag (count of entity_tags for each tag — backend provides this or frontend calculates from entity lists). Renders tag table (name, color badge, usage count, actions). Pattern: follow settings page structure from 93A. ~80 lines. |
| 93.9 | Create TagTable component | 93B | | `components/settings/tag-table.tsx` — `"use client"`. Displays tags in table (columns: color badge, name, usage count, actions). Actions: edit (opens dialog), delete (confirmation + server action, shows warning if usage count > 0). Pattern: follow table component patterns. ~70 lines. |
| 93.10 | Create AddTagDialog component | 93B | | `components/settings/add-tag-dialog.tsx` — `"use client"`. Form fields: name (text), color (color picker — Shadcn color input or simple hex input). On submit, calls server action `createTag(name, color)` which POSTs to `/api/tags`. Pattern: follow dialog patterns from 93A. ~60 lines. |
| 93.11 | Create EditTagDialog component | 93B | | `components/settings/edit-tag-dialog.tsx` — similar to AddTagDialog, pre-filled with tag data. Calls `updateTag(id, name, color)`. Pattern: follow EditFieldDialog structure. ~60 lines. |
| 93.12 | Add tag server actions | 93B | | `app/(app)/org/[slug]/settings/tags/actions.ts` — server actions: `async function createTag(name, color)`, `async function updateTag(id, name, color)`, `async function deleteTag(id)`. Pattern: follow server action patterns from 93A. ~50 lines. |
| 93.13 | Add tags settings tests | 93B | | `__tests__/app/settings/tags.test.tsx` (~6 tests): Tags page renders tag list, AddTagDialog opens and submits, color picker allows selecting color, EditTagDialog pre-fills data, delete tag shows warning if usage > 0, delete confirmation required. Mock API calls. Pattern: follow settings test patterns. |
| 93.14 | Add field group section to custom fields page | 93C | | Modify `app/(app)/org/[slug]/settings/custom-fields/page.tsx` — add "Field Groups" section below field definitions table on each tab. Shows list of field groups for the entity type (fetched from `GET /api/field-groups?entityType={type}`). Each group shows name, description, member count, actions (edit, deactivate). Pattern: follow existing section structure. ~40 lines added. |
| 93.15 | Create AddFieldGroupDialog component | 93C | | `components/settings/add-field-group-dialog.tsx` — `"use client"`. Form fields: entityType (select), name (text), slug (auto-generated), description (textarea), fieldDefinitionIds (multi-select from available field definitions for the entity type). On submit, calls server action `createFieldGroup(entityType, name, slug, description, fieldDefinitionIds)` which POSTs to `/api/field-groups`. Pattern: follow AddFieldDialog structure, multi-select for field definitions. ~120 lines. |
| 93.16 | Create EditFieldGroupDialog component | 93C | | `components/settings/edit-field-group-dialog.tsx` — similar to AddFieldGroupDialog, pre-filled with group data. Allows updating name, description, and field membership (add/remove fields). Calls `updateFieldGroup(id, ...)` which PUTs to `/api/field-groups/{id}`. Pattern: follow EditFieldDialog structure. ~120 lines. |
| 93.17 | Add field group server actions | 93C | | `app/(app)/org/[slug]/settings/custom-fields/actions.ts` — add actions: `async function createFieldGroup(...)`, `async function updateFieldGroup(...)`, `async function deactivateFieldGroup(id)`. Pattern: follow existing action patterns. ~60 lines added. |
| 93.18 | Add field group tests | 93C | | `__tests__/app/settings/custom-fields.test.tsx` additions (~5 tests): Field groups section renders, AddFieldGroupDialog opens and submits, field definition multi-select shows available fields, EditFieldGroupDialog pre-fills membership, deactivate group confirmation. Mock API calls. Pattern: follow existing test patterns. |

### Key Files

**Slice 93A — Create:**
- `frontend/app/(app)/org/[slug]/settings/custom-fields/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/custom-fields/actions.ts`
- `frontend/components/settings/field-definition-table.tsx`
- `frontend/components/settings/add-field-dialog.tsx`
- `frontend/components/settings/edit-field-dialog.tsx`
- `frontend/__tests__/app/settings/custom-fields.test.tsx`

**Slice 93B — Create:**
- `frontend/app/(app)/org/[slug]/settings/tags/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/tags/actions.ts`
- `frontend/components/settings/tag-table.tsx`
- `frontend/components/settings/add-tag-dialog.tsx`
- `frontend/components/settings/edit-tag-dialog.tsx`
- `frontend/__tests__/app/settings/tags.test.tsx`

**Slice 93C — Modify:**
- `frontend/app/(app)/org/[slug]/settings/custom-fields/page.tsx` — Add field groups section
- `frontend/app/(app)/org/[slug]/settings/custom-fields/actions.ts` — Add field group actions

**Slice 93C — Create:**
- `frontend/components/settings/add-field-group-dialog.tsx`
- `frontend/components/settings/edit-field-group-dialog.tsx`

**Slice 93C — Read for context:**
- Existing settings pages for layout patterns
- Shadcn multi-select or Command component for field definition selection

---

## Epic 94: Entity Detail Custom Fields & Tags Frontend

**Goal**: Add custom fields section to project/task/customer detail pages with grouped display, type-appropriate input components, "Add value" placeholders. Add tag selection UI with tag badges below title, popover with auto-complete, inline tag creation. Integrate applied field groups UI. Add sidebar nav links for settings pages.

**References**: Architecture doc Sections 11.3.2, 11.3.3, 11.8 Slice F.

**Dependencies**: Epic 88 (entity custom fields API), Epic 89 (entity tag API), Epic 92 (saved view frontend for shared components)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **94A** | 94.1-94.7 | Custom fields section on entity detail pages — grouped display, type-appropriate inputs, edit/save, tests | |
| **94B** | 94.8-94.13 | Tag selection UI on entity detail pages — tag badges, popover auto-complete, inline tag creation, tests | |
| **94C** | 94.14-94.17 | Applied field groups UI, sidebar nav links, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 94.1 | Create CustomFieldsSection component | 94A | | `components/custom-fields/custom-fields-section.tsx` — `"use client"`. Accepts props: `entityType`, `customFields: Map<String, Object>`, `appliedFieldGroups: List<UUID>`, `readOnly: boolean`. Fetches field definitions and field groups for entity type. Groups fields by FieldGroup (displays group name as section header). For each field, renders appropriate input based on fieldType: TEXT/EMAIL/URL/PHONE = Input, NUMBER = Input type="number", DATE = DatePicker (Shadcn), DROPDOWN = Select, BOOLEAN = Checkbox, CURRENCY = Input + currency select, PHONE = Input type="tel". Shows current value if exists, "Add value" placeholder if null. Edit/save mode toggle. Pattern: follow existing form sections, type-specific input components from Shadcn. ~200 lines. |
| 94.2 | Add CustomFieldsSection to project detail page | 94A | | Modify `app/(app)/org/[slug]/projects/[id]/page.tsx` — add `<CustomFieldsSection entityType="PROJECT" customFields={project.customFields} appliedFieldGroups={project.appliedFieldGroups} readOnly={!canManage} />` below project details. Pattern: follow existing section integration. ~10 lines added. |
| 94.3 | Add CustomFieldsSection to task detail dialog/page | 94A | | Modify task detail UI (wherever it renders) — add `<CustomFieldsSection entityType="TASK" customFields={task.customFields} appliedFieldGroups={task.appliedFieldGroups} readOnly={!canEdit} />`. Pattern: follow project pattern. ~10 lines added. |
| 94.4 | Add CustomFieldsSection to customer detail page | 94A | | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx` — add `<CustomFieldsSection entityType="CUSTOMER" customFields={customer.customFields} appliedFieldGroups={customer.appliedFieldGroups} readOnly={!isAdminOrOwner} />`. Pattern: follow project pattern. ~10 lines added. |
| 94.5 | Add custom fields server action | 94A | | Create or modify entity actions files (`projects/[id]/actions.ts`, etc.) — add action: `async function updateCustomFields(entityId, entityType, customFields)` — calls `PUT /api/{entityType}/{entityId}` with customFields in body, revalidates. Pattern: follow existing entity update actions. ~20 lines per entity type (3 files). |
| 94.6 | Add type-specific input components | 94A | | Create or reuse input components: `components/custom-fields/currency-input.tsx` (number input + currency dropdown, uses org defaultCurrency from OrgSettings), `components/custom-fields/date-input.tsx` (wrapper around Shadcn DatePicker), `components/custom-fields/dropdown-input.tsx` (Select with options from field definition). Pattern: follow Shadcn component patterns. ~50 lines each, ~150 total. |
| 94.7 | Add custom fields section tests | 94A | | `__tests__/components/custom-fields/custom-fields-section.test.tsx` (~10 tests): Renders TEXT field with value, renders NUMBER field with value, renders DATE field (uses DatePicker), renders DROPDOWN with options, renders BOOLEAN as checkbox, renders CURRENCY with amount + currency, renders grouped fields (under FieldGroup headers), edit mode allows changing values, save calls updateCustomFields action, readOnly mode disables inputs. Mock field definitions and groups. Pattern: follow component test patterns. |
| 94.8 | Create TagSelector component | 94B | | `components/tags/tag-selector.tsx` — `"use client"`. Displays current tags as badges below entity title (removable). "Add tag" button opens popover with auto-complete search (Shadcn Command component). Fetches all tags from `GET /api/tags`. On tag selection, calls server action `setEntityTags(entityId, entityType, tagIds)` which POSTs to `/api/{entityType}/{entityId}/tags`. Supports inline tag creation (if search doesn't match, show "Create tag" option). Pattern: follow tag filter component from Epic 92, Shadcn Command for auto-complete. ~120 lines. |
| 94.9 | Add TagSelector to project detail page | 94B | | Modify `app/(app)/org/[slug]/projects/[id]/page.tsx` — add `<TagSelector entityType="PROJECT" entityId={project.id} currentTags={project.tags} readOnly={!canManage} />` below project title. Pattern: follow existing UI integration. ~10 lines added. |
| 94.10 | Add TagSelector to task detail dialog/page | 94B | | Modify task detail UI — add `<TagSelector entityType="TASK" entityId={task.id} currentTags={task.tags} readOnly={!canEdit} />`. Pattern: follow project pattern. ~10 lines added. |
| 94.11 | Add TagSelector to customer detail page | 94B | | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx` — add `<TagSelector entityType="CUSTOMER" entityId={customer.id} currentTags={customer.tags} readOnly={!isAdminOrOwner} />`. Pattern: follow project pattern. ~10 lines added. |
| 94.12 | Add tag server actions | 94B | | Create or modify entity actions files — add actions: `async function setEntityTags(entityId, entityType, tagIds)` — calls `POST /api/{entityType}/{entityId}/tags`, revalidates. `async function createTagInline(name, color)` — calls `POST /api/tags`, returns new tag. Pattern: follow existing action patterns. ~30 lines per entity type (3 files). |
| 94.13 | Add tag selector tests | 94B | | `__tests__/components/tags/tag-selector.test.tsx` (~8 tests): Renders current tags as badges, "Add tag" opens popover, auto-complete searches tags, tag selection calls setEntityTags action, tag removal calls setEntityTags with updated list, inline tag creation (when no match), readOnly mode disables add/remove, tag badges show correct colors. Mock API calls. Pattern: follow component test patterns. |
| 94.14 | Add applied field groups UI | 94C | | Modify CustomFieldsSection component — add "Manage Field Groups" button (admin/owner only) that opens a dialog to select which field groups apply to this entity. Dialog lists all field groups for the entity type (checkboxes). On save, calls `PUT /api/{entityType}/{entityId}/field-groups` with selected group IDs. Displayed fields update to show fields from selected groups. Pattern: follow existing management dialog patterns. ~80 lines added to CustomFieldsSection. |
| 94.15 | Add sidebar nav links for settings | 94C | | Modify `lib/nav-items.ts` — add two new nav items under Settings section: "Custom Fields" (route: `/org/{slug}/settings/custom-fields`, icon: ListTree or similar), "Tags" (route: `/org/{slug}/settings/tags`, icon: Tag). Pattern: follow existing nav item structure. ~10 lines. |
| 94.16 | Add sidebar nav links tests | 94C | | `__tests__/components/desktop-sidebar.test.tsx` additions (~2 tests): Sidebar shows "Custom Fields" link under Settings, sidebar shows "Tags" link under Settings. Pattern: follow existing sidebar test patterns. |
| 94.17 | Add integration tests for entity detail UI | 94C | | `__tests__/integration/entity-custom-fields-tags.test.tsx` (~6 tests): Project detail page renders CustomFieldsSection with values, project detail page renders TagSelector with tags, editing custom field value saves correctly, adding tag updates entity, removing tag updates entity, applied field groups update shows correct fields. Mock all API calls. Pattern: follow integration test patterns for page rendering. |

### Key Files

**Slice 94A — Create:**
- `frontend/components/custom-fields/custom-fields-section.tsx`
- `frontend/components/custom-fields/currency-input.tsx`
- `frontend/components/custom-fields/date-input.tsx`
- `frontend/components/custom-fields/dropdown-input.tsx`
- `frontend/__tests__/components/custom-fields/custom-fields-section.test.tsx`

**Slice 94A — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add CustomFieldsSection
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add CustomFieldsSection
- Task detail page/dialog — Add CustomFieldsSection
- Entity action files (create or modify) — Add updateCustomFields action

**Slice 94B — Create:**
- `frontend/components/tags/tag-selector.tsx`
- `frontend/__tests__/components/tags/tag-selector.test.tsx`

**Slice 94B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add TagSelector
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add TagSelector
- Task detail page/dialog — Add TagSelector
- Entity action files — Add setEntityTags, createTagInline actions

**Slice 94C — Modify:**
- `frontend/components/custom-fields/custom-fields-section.tsx` — Add field groups management
- `frontend/lib/nav-items.ts` — Add settings nav links
- `frontend/__tests__/components/desktop-sidebar.test.tsx` — Add nav link tests

**Slice 94C — Create:**
- `frontend/__tests__/integration/entity-custom-fields-tags.test.tsx`

**Slice 94C — Read for context:**
- Existing entity detail pages for layout patterns
- Shadcn Command component for auto-complete
- Shadcn DatePicker for date inputs
