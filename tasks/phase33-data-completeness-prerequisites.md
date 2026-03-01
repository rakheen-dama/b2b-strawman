# Phase 33 -- Data Completeness & Prerequisite Enforcement

Phase 33 transforms DocTeams' metadata infrastructure from passive data capture into active data quality enforcement. The platform already has rich field definition capabilities (Phase 11/23), a customer readiness computation layer (Phase 15), lifecycle guards (Phase 4), and project templates (Phase 16) -- but none enforce completeness at the points that matter. This phase wires existing infrastructure into enforcement points by introducing a `PrerequisiteContext` enum, a central `PrerequisiteService`, and a shared `PrerequisiteModal` frontend component for inline remediation.

**Architecture doc**: `architecture/phase33-data-completeness-prerequisites.md`

**ADRs**:
- [ADR-130](../adr/ADR-130-prerequisite-enforcement-strategy.md) -- Prerequisite Enforcement Strategy (soft-blocking with inline remediation modal)
- [ADR-131](../adr/ADR-131-prerequisite-context-granularity.md) -- Prerequisite Context Granularity (typed enum)
- [ADR-132](../adr/ADR-132-engagement-prerequisite-storage.md) -- Engagement Prerequisite Storage (JSONB array on ProjectTemplate)
- [ADR-133](../adr/ADR-133-auto-transition-incomplete-fields.md) -- Auto-Transition Behavior When Fields Incomplete (block and notify)

**Migrations**: V53, V54 (tenant schema)

**Dependencies on prior phases**: Phase 4 (Customer lifecycle), Phase 11/23 (FieldDefinition, field packs, CustomFieldUtils), Phase 15 (CustomerReadinessService, SetupProgressCard), Phase 16 (ProjectTemplate), Phase 10 (InvoiceService), Phase 32 (Proposal acceptance), Phase 12 (Document generation), Phase 6.5 (NotificationService), Phase 6 (AuditService).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 240 | Prerequisite Infrastructure -- Migration, Enum & Core Service | Backend | -- | M | 240A, 240B | **Done** |
| 241 | Prerequisite REST API & Field Definition Extension | Backend | 240 | M | 241A, 241B | **Done** |
| 242 | Lifecycle Transition Gate | Backend | 241 | M | 242A, 242B | **Done** |
| 243 | Engagement Prerequisites -- Template Extension & Checks | Backend | 241 | M | 243A, 243B | 243A **Done** |
| 244 | Action-Point Prerequisite Wiring | Backend | 241 | M | 244A, 244B | |
| 245 | PrerequisiteModal & Shared Frontend Components | Frontend | 241 | M | 245A, 245B | |
| 246 | Smart Customer Intake Dialog | Frontend | 241, 245 | M | 246A, 246B | |
| 247 | Prerequisite Configuration UI | Frontend | 245 | S | 247A | |
| 248 | Lifecycle Transition Frontend Integration | Frontend | 242, 245 | S | 248A | |
| 249 | Engagement & Action-Point Frontend Integration | Frontend | 243, 244, 245 | M | 249A, 249B | |
| 250 | Completeness Visibility -- Backend Queries | Backend | 241 | M | 250A | |
| 251 | Completeness Visibility -- Frontend & Dashboard | Frontend | 250, 245 | M | 251A, 251B | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────
[E240A V53 migration: required_for_contexts
 column on field_definitions + GIN index
 + seed data for common-customer pack]
        |
[E240B PrerequisiteContext enum +
 PrerequisiteCheck/Violation records +
 PrerequisiteService core evaluation
 (custom field checks, no structural)
 + FieldDefinition entity extension
 + FieldPackField/Seeder extension
 + unit tests]
        |
[E241A FieldDefinitionRepository context
 query (native JSONB @>) +
 FieldDefinitionService.getIntakeFields() +
 FieldDefinitionService.getRequiredFieldsForContext() +
 CustomerService.createCustomer() extension
 (customFields validation) + tests]
        |
[E241B PrerequisiteController:
 GET /api/prerequisites/check +
 GET /api/field-definitions/intake +
 PATCH field-definitions extension
 for requiredForContexts +
 integration tests]
        |
        +──────────────────────+──────────────────────+──────────────────────+
        |                      |                      |                      |
[E242A Wire prerequisite   [E243A V54 migration:  [E244A Wire              [E250A Completeness
 check into                 required_customer_     prerequisite checks      summary endpoint:
 CustomerLifecycleService   field_ids column on    into InvoiceService,     batch computation,
 for ONBOARDING→ACTIVE,     ProjectTemplate +      ProposalService.send,    per-context readiness,
 auto-transition block +    entity extension +     DocumentGeneration +     CustomerReadinessService
 notification on failure    ProjectTemplateService structural checks        .computeReadinessByContext
 + tests]                   .getRequiredFields +   (portal contact,         + tests]
        |                   .updateRequired +      billing address) +
[E242B Extended tests:      prerequisite-check     tests]
 auto-transition scenarios  endpoint + tests]           |
 + TestCustomerFactory              |              [E244B Extended tests:
 .withRequiredFields helper [E243B ProjectTemplate  structural violations,
 + existing lifecycle test  Controller extension:   action-point 422
 updates]                   PUT required-fields,    responses, cross-domain
                            GET prerequisite-check  wiring + existing
                            + template editor       test updates]
                            integration tests]

FRONTEND TRACK (after E241B)
─────────────────────────────
[E245A Prerequisite types +
 usePrerequisiteCheck hook +
 InlineFieldEditor component +
 prerequisite-modal.tsx core +
 prerequisite-violation-list.tsx
 + frontend tests]
        |
[E245B PrerequisiteModal:
 grouped violations display,
 inline field editing,
 "Check & Continue" re-validation,
 batch save + auto-proceed
 + tests]
        |
        +──────────────────+──────────────────+──────────────────+──────────────────+
        |                  |                  |                  |                  |
[E246A intake endpoint  [E247A Settings     [E248A Customer    [E249A Invoice,     [E251A Completeness
 action + Intake         custom-fields       detail page        proposal, document  badge component +
 FieldsSection           "Required For"      lifecycle          generation buttons  customer list
 component +             multi-select on     transition         wired through       completeness column
 field type rendering    FieldDefinition     button wired       prerequisiteCheck   + filter + sort +
 + tests]                Dialog + context    through            hook + modal        tests]
        |                labels + tests]     prerequisiteCheck  integration              |
[E246B CreateCustomer                        hook + modal       + tests]            [E251B Enhanced
 Dialog multi-step                           + tests]                |              SetupProgressCard
 (base → intake) +                                              [E249B Template     + completeness ring
 submit with custom                                              editor Required    + dashboard widget
 fields + tests]                                                 Customer Fields    + tests]
                                                                 section + project
                                                                 creation modal
                                                                 integration + tests]
```

**Parallel opportunities**:
- After E241B: E242A, E243A, E244A, E250A, and E245A can all start in parallel (5 independent tracks).
- After E245B: E246A, E247A, E248A, E249A, and E251A can all start in parallel (5 independent tracks).
- E243A and E243B are sequential but independent of E242 and E244.
- Frontend E246-E251 slices are largely independent of each other after E245B (shared modal).

---

## Implementation Order

### Stage 0: Database Migration & Core Infrastructure

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 240 | 240A | V53 tenant migration: ALTER TABLE field_definitions ADD COLUMN required_for_contexts JSONB + GIN index + seed UPDATEs for common-customer pack fields. ~1 new migration file. Backend only. | **Done** (PR #485) |
| 0b | 240 | 240B | PrerequisiteContext enum + PrerequisiteCheck/PrerequisiteViolation records + PrerequisiteService core evaluation (custom field checks only) + FieldDefinition entity extension + FieldPackField/FieldPackSeeder extension + unit tests (~10 tests). ~7 new/modified files. Backend only. | **Done** (PR #486) |

### Stage 1: Repository Queries, Service Extensions & REST API

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 241 | 241A | FieldDefinitionRepository native JSONB query + FieldDefinitionService.getIntakeFields() + FieldDefinitionService.getRequiredFieldsForContext() + CustomerService.createCustomer() extension for customFields validation + tests (~8 tests). ~4 modified files, ~1 test file. Backend only. | **Done** (PR #487) |
| 1b | 241 | 241B | PrerequisiteController: GET /api/prerequisites/check + GET /api/field-definitions/intake endpoint + PATCH field-definitions extension for requiredForContexts + integration tests (~12 tests). ~3 modified/new files, ~1 test file. Backend only. | **Done** (PR #489) |

### Stage 2: Backend Enforcement Points & Template Extension (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 242 | 242A | Wire PrerequisiteService.checkForContext(LIFECYCLE_ACTIVATION) into CustomerLifecycleService for ONBOARDING->ACTIVE. Block auto-transition + send notification on failure. ~2 modified files. Backend only. | **Done** (PR #490) |
| 2b (parallel) | 243 | 243A | V54 migration (required_customer_field_ids on project_templates) + ProjectTemplate entity extension + ProjectTemplateService.getRequiredCustomerFields() + PrerequisiteService.checkEngagementPrerequisites() + tests (~8 tests). ~4 modified/new files. Backend only. | **Done** (PR #492) |
| 2c (parallel) | 244 | 244A | Wire prerequisite checks into InvoiceService, ProposalService.sendProposal(), DocumentGenerationReadinessService + structural checks (portal contact, billing address) in PrerequisiteService + tests (~8 tests). ~4 modified files. Backend only. | |
| 2d (parallel) | 250 | 250A | GET /api/customers/completeness-summary endpoint + CustomerReadinessService.computeReadinessByContext() + batch completeness computation + CompletenessScore record + tests (~6 tests). ~3 modified/new files. Backend only. | |
| 2e (parallel) | 245 | 245A | Prerequisite TypeScript types + usePrerequisiteCheck hook + InlineFieldEditor component + prerequisite-violation-list.tsx + prerequisites API client + frontend tests (~6 tests). ~6 new files. Frontend only. | |

### Stage 3: Backend Enforcement Completion & Frontend Modal

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 242 | 242B | Extended lifecycle prerequisite tests: auto-transition scenarios, TestCustomerFactory.withRequiredFields() helper, update existing lifecycle tests to pass with prerequisites (~8 tests). ~3 modified files. Backend only. | **Done** (PR #491) |
| 3b (parallel) | 243 | 243B | ProjectTemplateController extension: PUT /{id}/required-customer-fields + GET /{id}/prerequisite-check + integration tests (~8 tests). ~2 modified files, ~1 test file. Backend only. | |
| 3c (parallel) | 244 | 244B | Extended action-point tests: structural violation scenarios, cross-domain 422 responses, existing invoice/proposal/document test updates (~8 tests). ~3 modified test files. Backend only. | |
| 3d (parallel) | 245 | 245B | PrerequisiteModal component: grouped violations, inline editing, "Check & Continue" re-validation, batch field save, auto-proceed on resolved, onCancel + tests (~6 tests). ~2 new files. Frontend only. | |

### Stage 4: Frontend Integration (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 246 | 246A | IntakeFieldsSection component (renders auto-apply field groups as form controls per field type) + intake endpoint server action + tests (~4 tests). ~3 new files. Frontend only. | |
| 4b (parallel) | 247 | 247A | Settings custom-fields "Required For" multi-select on FieldDefinitionDialog + PrerequisiteContext labels constant + tests (~3 tests). ~3 modified files. Frontend only. | |
| 4c (parallel) | 248 | 248A | Customer detail page lifecycle transition button wired through usePrerequisiteCheck hook + PrerequisiteModal integration + lifecycle-actions.ts update + tests (~4 tests). ~3 modified files. Frontend only. | |
| 4d (parallel) | 249 | 249A | Invoice generation, proposal send, document generation buttons wired through prerequisite check + PrerequisiteModal integration + tests (~5 tests). ~5 modified files. Frontend only. | |
| 4e (parallel) | 251 | 251A | CompletenesssBadge component + customer list page completeness column + filter + sort + completeness server actions + tests (~4 tests). ~4 new/modified files. Frontend only. | |

### Stage 5: Remaining Frontend

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 246 | 246B | CreateCustomerDialog multi-step flow (base fields -> intake custom fields) + submit with customFields payload + tests (~5 tests). ~2 modified files. Frontend only. | |
| 5b (parallel) | 249 | 249B | Project template editor "Required Customer Fields" section + project creation dialog prerequisite check + tests (~4 tests). ~3 modified files. Frontend only. | |
| 5c (parallel) | 251 | 251B | Enhanced SetupProgressCard with context-grouped display + completeness ring on customer detail header + "Incomplete Customer Profiles" dashboard widget + tests (~5 tests). ~4 modified/new files. Frontend only. | |

### Timeline

```
Stage 0: [240A] → [240B]                                                    (sequential)
Stage 1: [241A] → [241B]                                                    (sequential)
Stage 2: [242A] // [243A] // [244A] // [250A] // [245A]                    (parallel)
Stage 3: [242B] // [243B] // [244B] // [245B]                              (parallel)
Stage 4: [246A] // [247A] // [248A] // [249A] // [251A]                    (parallel)
Stage 5: [246B] // [249B] // [251B]                                         (parallel)
```

**Critical path**: 240A -> 240B -> 241A -> 241B -> 245A -> 245B -> 246A -> 246B (8 slices sequential at most).

**Fastest path with parallelism**: 240A -> 240B -> 241A -> 241B -> then 5 parallel backend + 1 frontend track -> converge at Stage 4. Estimated: 22 slices total, 8 slices on critical path.

---

## Epic 240: Prerequisite Infrastructure -- Migration, Enum & Core Service

**Goal**: Create the V53 tenant schema migration extending `field_definitions` with `required_for_contexts`, then build the `PrerequisiteContext` enum, `PrerequisiteCheck`/`PrerequisiteViolation` value objects, core `PrerequisiteService` evaluation logic, and extend `FieldDefinition` entity with the new column.

**References**: Architecture doc Sections 33.2, 33.8, 33.9.1. ADR-131 (typed enum).

**Dependencies**: None -- this is the greenfield foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **240A** | 240.1--240.3 | V53 tenant migration: ALTER TABLE field_definitions ADD COLUMN required_for_contexts JSONB NOT NULL DEFAULT '[]' + GIN index on required_for_contexts + seed UPDATEs for common-customer pack fields (address_line1, city, country, tax_number). ~1 new migration file. Backend only. | **Done** (PR #485) |
| **240B** | 240.4--240.12 | `PrerequisiteContext` enum (5 values) + `PrerequisiteCheck` record + `PrerequisiteViolation` record + `PrerequisiteService` core evaluation (custom field checks, no structural checks yet) + `FieldDefinition` entity extension (`requiredForContexts` JSONB field) + `FieldPackField` record extension + `FieldPackSeeder` extension + unit tests (~10 tests). ~7 new/modified files. Backend only. | **Done** (PR #486) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 240.1 | Create V53 tenant migration -- field_definitions extension | 240A | | New file: `backend/src/main/resources/db/migration/tenant/V53__field_definition_prerequisite_contexts.sql`. ALTER TABLE field_definitions ADD COLUMN required_for_contexts JSONB NOT NULL DEFAULT '[]'. See architecture doc Section 33.8 for exact SQL. |
| 240.2 | V53 migration -- GIN index for JSONB contains queries | 240A | 240.1 | Same file. CREATE INDEX idx_field_definitions_required_contexts ON field_definitions USING GIN (required_for_contexts). Enables efficient `@>` (contains) queries for context lookup. |
| 240.3 | V53 migration -- seed default contexts for existing pack fields | 240A | 240.1 | Same file. UPDATE field_definitions SET required_for_contexts for common-customer pack fields: address_line1 -> ["INVOICE_GENERATION", "PROPOSAL_SEND"], city/country/tax_number -> ["INVOICE_GENERATION"]. Idempotent: only updates where required_for_contexts = '[]'. |
| 240.4 | Create `PrerequisiteContext` enum | 240B | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteContext.java`. Values: LIFECYCLE_ACTIVATION, INVOICE_GENERATION, PROPOSAL_SEND, DOCUMENT_GENERATION, PROJECT_CREATION. Include `getDisplayLabel()` method returning human-readable strings. Pattern: `fielddefinition/EntityType.java`. |
| 240.5 | Create `PrerequisiteViolation` record | 240B | | New file: `prerequisite/PrerequisiteViolation.java`. Fields: code (String), message (String), entityType (String), entityId (UUID), fieldSlug (String nullable), groupName (String nullable), resolution (String). See architecture doc Section 33.2.4. Pattern: Java record, same package. |
| 240.6 | Create `PrerequisiteCheck` record | 240B | 240.5 | New file: `prerequisite/PrerequisiteCheck.java`. Fields: passed (boolean), context (PrerequisiteContext), violations (List<PrerequisiteViolation>). Static factory: `passed(PrerequisiteContext)`. Pattern: Java record. |
| 240.7 | Extend `FieldDefinition` entity with `requiredForContexts` | 240B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java`. Add field: `requiredForContexts` as `List<String>` with `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(name = "required_for_contexts", columnDefinition = "jsonb")`. Default: `new ArrayList<>()`. Add getter/setter. Pattern: existing JSONB fields on `Customer.customFields`. |
| 240.8 | Extend `FieldPackField` record with `requiredForContexts` | 240B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackField.java`. Add optional field `requiredForContexts` (List<String>, default empty). This is the pack JSON schema extension. |
| 240.9 | Extend `FieldPackSeeder` to set `requiredForContexts` | 240B | 240.8 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`. When creating FieldDefinition from pack field, set `requiredForContexts` from `FieldPackField.requiredForContexts()`. New tenants will get contexts from pack JSON; existing tenants get contexts from V53 migration seed. |
| 240.10 | Implement `PrerequisiteService` core evaluation | 240B | 240.4, 240.6, 240.7 | New file: `prerequisite/PrerequisiteService.java`. Method: `checkForContext(PrerequisiteContext context, EntityType entityType, UUID entityId)`. Loads required field definitions (via a simple `findAll` + filter in this slice -- native query added in 241A). Loads entity's custom fields. Evaluates each required field using `CustomFieldUtils.isFieldValueFilled()`. Returns PrerequisiteCheck. No structural checks in this slice. Pattern: `setupstatus/CustomerReadinessService.java`. |
| 240.11 | Implement `PrerequisiteService.checkEngagementPrerequisites()` | 240B | 240.10 | In `PrerequisiteService.java`. Method: `checkEngagementPrerequisites(UUID customerId, UUID templateId)`. Placeholder that returns `passed` -- full implementation in Epic 243 after ProjectTemplate is extended. |
| 240.12 | Write PrerequisiteService unit tests | 240B | 240.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteServiceTest.java`. Tests (~10): (1) `checkForContext_allFieldsFilled_returnsPassed`; (2) `checkForContext_missingRequiredField_returnsFailed`; (3) `checkForContext_noFieldsRequired_returnsPassed`; (4) `checkForContext_nullCustomFields_returnsViolationsForAllRequired`; (5) `checkForContext_partiallyFilled_returnsOnlyMissingViolations`; (6) `checkForContext_contextWithNoMatchingFields_returnsPassed`; (7) `violationContainsFieldSlugAndGroupName`; (8) `violationContainsEntityTypeAndId`; (9) `checkEngagementPrerequisites_emptyTemplate_returnsPassed`; (10) `prerequisiteContext_displayLabels_areCorrect`. Pattern: `setupstatus/CustomerReadinessServiceTest.java` (or similar unit test). |

### Key Files

**Slice 240A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V53__field_definition_prerequisite_contexts.sql`

**Slice 240B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteContext.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteViolation.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteCheck.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteServiceTest.java`

**Slice 240B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackField.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`

**Slice 240B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadinessService.java` -- readiness evaluation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldUtils.java` -- `isFieldValueFilled()` utility
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/EntityType.java` -- enum pattern

### Architecture Decisions

- **No new domain entities**: Phase 33 extends existing entities (`FieldDefinition`, `ProjectTemplate`) with JSONB columns rather than introducing new tables. See architecture doc Section 33.2.
- **Typed enum for contexts**: `PrerequisiteContext` uses a bounded Java enum for compile-time safety. See ADR-131.
- **Value objects as records**: `PrerequisiteCheck` and `PrerequisiteViolation` are Java records, not JPA entities. They are serialized directly in 422 response bodies.
- **requiredForContexts is orthogonal to required**: The existing `required` boolean controls form-level validation; `requiredForContexts` controls action-point enforcement. A field can be optional at intake but required for invoice generation.

---

## Epic 241: Prerequisite REST API & Field Definition Extension

**Goal**: Add the native JSONB repository query, field definition service extensions for intake and context-based lookup, customer creation extension for custom field validation, and the REST API endpoints for prerequisite checking and intake field retrieval.

**References**: Architecture doc Sections 33.3.1, 33.4, 33.9.1, 33.9.3.

**Dependencies**: Epic 240 (core infrastructure).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **241A** | 241.1--241.6 | FieldDefinitionRepository native JSONB `@>` query + FieldDefinitionService.getIntakeFields() + FieldDefinitionService.getRequiredFieldsForContext() + CustomerService.createCustomer() extension for customFields validation + tests (~8 tests). ~4 modified files, ~1 test file. Backend only. | **Done** (PR #487) |
| **241B** | 241.7--241.14 | PrerequisiteController: GET /api/prerequisites/check + GET /api/field-definitions/intake endpoint on FieldDefinitionController + PATCH field-definitions extension for requiredForContexts + integration tests (~12 tests). ~3 modified/new files, ~1 test file. Backend only. | **Done** (PR #489) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 241.1 | Add native JSONB query to FieldDefinitionRepository | 241A | 240B | Modify: `fielddefinition/FieldDefinitionRepository.java`. Add `@Query(nativeQuery = true)` method: `findRequiredForContext(@Param("entityType") String entityType, @Param("context") String context)` using `WHERE entity_type = :entityType AND active = true AND required_for_contexts @> :context::jsonb`. Context param format: `'["LIFECYCLE_ACTIVATION"]'`. See architecture doc Section 33.9.3. |
| 241.2 | Implement `FieldDefinitionService.getRequiredFieldsForContext()` | 241A | 241.1 | Modify: `fielddefinition/FieldDefinitionService.java`. New method: `List<FieldDefinition> getRequiredFieldsForContext(EntityType entityType, PrerequisiteContext context)`. Delegates to repository native query. Returns ordered list. |
| 241.3 | Implement `FieldDefinitionService.getIntakeFields()` | 241A | | Modify: `fielddefinition/FieldDefinitionService.java`. New method: `List<IntakeFieldGroup> getIntakeFields(EntityType entityType)`. Fetches auto-apply FieldGroups for the entity type with their FieldDefinitions via FieldGroupMemberRepository. Returns groups with fields ordered by sortOrder. `IntakeFieldGroup` is a new nested record: `(UUID id, String name, String slug, List<FieldDefinition> fields)`. |
| 241.4 | Extend `CustomerService.createCustomer()` for custom field validation | 241A | | Modify: `customer/CustomerService.java`. Accept optional `Map<String, Object> customFields` in creation. If present: load auto-apply field definitions for CUSTOMER, validate each custom field value against its FieldDefinition (type checking via `CustomFieldValidator`), set on `customer.customFields` JSONB. Reject unknown field slugs with 400. |
| 241.5 | Wire PrerequisiteService to use repository query | 241A | 241.1 | Modify: `prerequisite/PrerequisiteService.java`. Replace the initial `findAll + filter` approach from 240B with the native query: `fieldDefinitionService.getRequiredFieldsForContext(entityType, context)`. |
| 241.6 | Write service-level integration tests | 241A | 241.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionContextTest.java`. Tests (~8): (1) `findRequiredForContext_returnsMatchingFields`; (2) `findRequiredForContext_ignoresInactiveFields`; (3) `findRequiredForContext_noMatchingContext_returnsEmpty`; (4) `getIntakeFields_returnsAutoApplyGroupsWithFields`; (5) `getIntakeFields_orderedBySortOrder`; (6) `createCustomerWithFields_validFields_succeeds`; (7) `createCustomerWithFields_invalidType_returns400`; (8) `createCustomerWithFields_unknownSlug_returns400`. Pattern: `fielddefinition/FieldDefinitionServiceTest.java` (if exists) or `customer/CustomerControllerTest.java`. |
| 241.7 | Create `PrerequisiteController` | 241B | 241A | New file: `prerequisite/PrerequisiteController.java`. Endpoint: `GET /api/prerequisites/check?context={context}&entityType={entityType}&entityId={entityId}` (MEMBER+). Validates context is a valid PrerequisiteContext enum value (400 if not). Calls `prerequisiteService.checkForContext()`. Always returns 200 with PrerequisiteCheck payload (this is a query endpoint, not an action -- see architecture doc Section 33.4.3). Pattern: `setupstatus/ProjectSetupStatusService.java` style controller or `report/ReportController.java`. |
| 241.8 | Add `GET /api/field-definitions/intake` endpoint | 241B | 241.3 | Modify: `fielddefinition/FieldDefinitionController.java`. New endpoint: `GET /api/field-definitions/intake?entityType=CUSTOMER` (MEMBER+). Calls `fieldDefinitionService.getIntakeFields(entityType)`. Returns IntakeFieldGroupResponse with nested fields. Response shape per architecture doc Section 33.4.3. |
| 241.9 | Extend PATCH field-definitions to accept requiredForContexts | 241B | 240.7 | Modify: `fielddefinition/FieldDefinitionController.java`. Extend existing `PATCH /api/field-definitions/{id}` request body to accept optional `requiredForContexts: List<String>`. Validate all values are valid PrerequisiteContext names (400 if not). Update FieldDefinition entity. ADMIN+ role. |
| 241.10 | Create response DTOs for prerequisite and intake endpoints | 241B | | In PrerequisiteController or FieldDefinitionController as nested records. `PrerequisiteCheckResponse`: maps from PrerequisiteCheck record. `IntakeFieldGroupResponse`: groups with fields. `IntakeFieldResponse`: field definition with type, required, options, requiredForContexts. |
| 241.11 | Write prerequisite controller integration tests -- happy paths | 241B | 241.7 | New file: `prerequisite/PrerequisiteControllerTest.java`. Tests (1-6): (1) `check_allFieldsFilled_returnsPassed`; (2) `check_missingFields_returnsFailedWithViolations`; (3) `check_invalidContext_returns400`; (4) `check_nonExistentEntity_returns404`; (5) `check_noFieldsForContext_returnsPassed`; (6) `check_asMember_succeeds`. Pattern: `expense/ExpenseControllerTest.java`. |
| 241.12 | Write intake endpoint integration tests | 241B | 241.8 | Continuing `PrerequisiteControllerTest.java` or new `FieldDefinitionIntakeTest.java`. Tests (7-9): (7) `intake_returnsAutoApplyGroupsWithFields`; (8) `intake_fieldsOrderedBySortOrder`; (9) `intake_nonCustomerEntityType_returnsGroups`. |
| 241.13 | Write field definition context PATCH tests | 241B | 241.9 | Continuing test file. Tests (10-12): (10) `patchFieldDefinition_setRequiredForContexts_succeeds`; (11) `patchFieldDefinition_invalidContext_returns400`; (12) `patchFieldDefinition_asMember_returns403`. |
| 241.14 | Write customer creation with fields test | 241B | 241.4 | Add test to existing `customer/CustomerControllerTest.java` or new file. Tests: `createCustomer_withCustomFields_succeeds`, `createCustomer_withInvalidFieldType_returns400`. |

### Key Files

**Slice 241A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`

**Slice 241A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionContextTest.java`

**Slice 241A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java` -- type validation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` -- group loading pattern

**Slice 241B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteControllerTest.java`

**Slice 241B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionController.java`

**Slice 241B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionController.java` -- existing PATCH pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerControllerTest.java` -- test scaffolding

### Architecture Decisions

- **Native query for JSONB contains**: JPQL does not support PostgreSQL's `@>` operator. All context-based field definition lookups use native queries with the GIN index from V53. See architecture doc Section 33.9.3.
- **Prerequisite check endpoint returns 200**: `GET /api/prerequisites/check` is a query, not an action. The `passed: false` payload signals the frontend. 422 is reserved for action endpoints (lifecycle transition, invoice generation) where the internal prerequisite check fails.
- **Customer creation extension is additive**: The existing `POST /api/customers` already accepts `customFields` in the payload (stored as JSONB). This slice adds field definition validation to that existing flow.

---

## Epic 242: Lifecycle Transition Gate

**Goal**: Wire `PrerequisiteService.checkForContext(LIFECYCLE_ACTIVATION)` into `CustomerLifecycleService` for the ONBOARDING -> ACTIVE transition. Block auto-transitions on prerequisite failure and send notifications. Update test infrastructure.

**References**: Architecture doc Sections 33.3.2, 33.5.2. ADR-133 (block and notify).

**Dependencies**: Epic 241 (PrerequisiteService with native query).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **242A** | 242.1--242.5 | Wire PrerequisiteService.checkForContext(LIFECYCLE_ACTIVATION) into CustomerLifecycleService for ONBOARDING->ACTIVE transition. Modify manual transition to return 422 on failure. Block auto-transition (checklist completion) + send notification on failure. ~3 modified files. Backend only. | **Done** (PR #490) |
| **242B** | 242.6--242.10 | Extended lifecycle prerequisite tests: auto-transition blocking, notification on failure, TestCustomerFactory.withRequiredFields() helper, update existing lifecycle tests to fill prerequisites. ~8 tests across ~3 files. Backend only. | **Done** (PR #491) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 242.1 | Wire prerequisite check into manual lifecycle transition | 242A | 241A | Modify: `compliance/CustomerLifecycleService.java`. In the `transitionStatus(customerId, targetStatus)` method, when target is ACTIVE and current is ONBOARDING: call `prerequisiteService.checkForContext(LIFECYCLE_ACTIVATION, CUSTOMER, customerId)`. If failed, throw a new `PrerequisiteNotMetException` (see 242.2) that produces a 422 response with the PrerequisiteCheck payload. |
| 242.2 | Create `PrerequisiteNotMetException` | 242A | | New file: `exception/PrerequisiteNotMetException.java` or `prerequisite/PrerequisiteNotMetException.java`. Extends `RuntimeException`. Carries a `PrerequisiteCheck` payload. The global exception handler (or `@ExceptionHandler` on the controller) maps this to 422 with the check as the response body. Pattern: `exception/ResourceNotFoundException.java`. |
| 242.3 | Wire prerequisite check into auto-transition | 242A | 242.1 | Modify: `compliance/CustomerLifecycleService.java` or `checklist/ChecklistInstantiationService.java` (wherever auto-transition is triggered on checklist completion). Before executing auto-transition: run prerequisite check. If failed: do NOT transition, send notification instead. |
| 242.4 | Send notification on blocked auto-transition | 242A | 242.3 | In the auto-transition code path: create notification via existing `NotificationService`. Type: `PREREQUISITE_BLOCKED_ACTIVATION`. Recipients: customer's assigned team members (query via existing member/project associations). Message: "Customer {name} has completed all checklist items but has {count} incomplete required fields for activation." Link to customer detail page. Pattern: existing notification creation in `compliance/CustomerLifecycleService.java`. |
| 242.5 | Add notification template for PREREQUISITE_BLOCKED_ACTIVATION | 242A | 242.4 | Modify: notification template constants/enum (wherever notification types are registered). Add `PREREQUISITE_BLOCKED_ACTIVATION` type with message template. ~1 modified file. |
| 242.6 | Create `TestCustomerFactory.withRequiredFields()` helper | 242B | 242A | Modify: test helper `TestCustomerFactory.java` (or equivalent test factory). New method: `withRequiredFields(PrerequisiteContext context)` that pre-fills all field definitions marked as required for the given context. Uses existing test field definition setup. |
| 242.7 | Write lifecycle prerequisite integration tests -- manual transition | 242B | 242.1 | New or extended test file: `compliance/CustomerLifecyclePrerequisiteTest.java`. Tests (~4): (1) `transitionToActive_allFieldsFilled_succeeds`; (2) `transitionToActive_missingFields_returns422WithViolations`; (3) `transitionToActive_noFieldsRequired_succeeds`; (4) `transitionToActive_fromProspect_skipPrerequisiteCheck`. |
| 242.8 | Write lifecycle prerequisite integration tests -- auto-transition | 242B | 242.3 | Continuing test file. Tests (~3): (5) `autoTransition_fieldsComplete_transitionsToActive`; (6) `autoTransition_fieldsMissing_blocksAndSendsNotification`; (7) `autoTransition_notificationContainsCustomerNameAndFieldCount`. |
| 242.9 | Update existing lifecycle tests to fill prerequisites | 242B | 242.6 | Modify existing test files that test ONBOARDING -> ACTIVE transitions (e.g., in `compliance/` test package). Use `TestCustomerFactory.withRequiredFields(LIFECYCLE_ACTIVATION)` to ensure existing tests don't fail with new 422 responses. |
| 242.10 | Write prerequisite exception handler test | 242B | 242.2 | Test: `PrerequisiteNotMetException` produces 422 with correct JSON structure (passed, context, violations array). ~1 test in controller test file. |

### Key Files

**Slice 242A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstantiationService.java` (if auto-transition is here)

**Slice 242A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteNotMetException.java`

**Slice 242A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- notification creation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ResourceNotFoundException.java` -- exception pattern

**Slice 242B -- Modify/Create:**
- Test helper factory file (project-specific location)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecyclePrerequisiteTest.java` (new)
- Existing lifecycle test files (modify to use withRequiredFields)

### Architecture Decisions

- **Block auto-transition, send notification**: Per ADR-133, ACTIVE status is a reliable indicator of data completeness. Auto-transitions that would bypass this are blocked, and a notification bridges the gap for the team.
- **PrerequisiteNotMetException**: A dedicated exception type with the PrerequisiteCheck payload enables consistent 422 responses from any enforcement point (lifecycle, invoice, etc.).

---

## Epic 243: Engagement Prerequisites -- Template Extension & Checks

**Goal**: Extend `ProjectTemplate` with `requiredCustomerFieldIds` JSONB column (V54 migration), implement the template-level prerequisite checking service, and add REST endpoints for configuring and checking template prerequisites.

**References**: Architecture doc Sections 33.2.2, 33.3.3, 33.6.4, 33.8. ADR-132 (JSONB array).

**Dependencies**: Epic 241 (PrerequisiteService core).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **243A** | 243.1--243.7 | V54 migration (required_customer_field_ids on project_templates) + ProjectTemplate entity extension + ProjectTemplateService.getRequiredCustomerFields() + ProjectTemplateService.updateRequiredCustomerFields() + PrerequisiteService.checkEngagementPrerequisites() full implementation + tests (~8 tests). ~5 modified/new files. Backend only. | **Done** (PR #492) |
| **243B** | 243.8--243.14 | ProjectTemplateController extension: PUT /{id}/required-customer-fields + GET /{id}/prerequisite-check?customerId={id} + wire notification for automated project creation (proposal acceptance, recurring schedules) + integration tests (~8 tests). ~3 modified files, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 243.1 | Create V54 tenant migration -- project template extension | 243A | | New file: `backend/src/main/resources/db/migration/tenant/V54__project_template_required_customer_fields.sql`. ALTER TABLE project_templates ADD COLUMN required_customer_field_ids JSONB NOT NULL DEFAULT '[]'. Add partial index on non-empty arrays. See architecture doc Section 33.8. |
| 243.2 | Extend `ProjectTemplate` entity | 243A | 243.1 | Modify: `projecttemplate/ProjectTemplate.java`. Add field: `requiredCustomerFieldIds` as `List<UUID>` with `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(name = "required_customer_field_ids", columnDefinition = "jsonb")`. Default: `new ArrayList<>()`. Add getter/setter. Pattern: similar JSONB usage in `FieldDefinition.requiredForContexts`. |
| 243.3 | Implement `ProjectTemplateService.getRequiredCustomerFields()` | 243A | 243.2 | Modify: `projecttemplate/ProjectTemplateService.java`. New method: `List<FieldDefinition> getRequiredCustomerFields(UUID templateId)`. Loads template, resolves `requiredCustomerFieldIds` to FieldDefinition records via `fieldDefinitionRepository.findAllById()`. Filters to active fields only. |
| 243.4 | Implement `ProjectTemplateService.updateRequiredCustomerFields()` | 243A | 243.2 | Modify: `projecttemplate/ProjectTemplateService.java`. New method: `updateRequiredCustomerFields(UUID templateId, List<UUID> fieldDefinitionIds)`. Validates: template exists (404), all IDs reference active customer FieldDefinitions (400 if not). Sets `requiredCustomerFieldIds` and saves. |
| 243.5 | Implement `PrerequisiteService.checkEngagementPrerequisites()` fully | 243A | 243.3 | Modify: `prerequisite/PrerequisiteService.java`. Replace placeholder from 240B. Loads template's required customer fields, evaluates customer's customFields against them. Returns PrerequisiteCheck with PROJECT_CREATION context. |
| 243.6 | Add field definition cleanup on deactivation | 243A | 243.2 | Modify: `fielddefinition/FieldDefinitionService.java`. When a field definition is deactivated, remove its UUID from all ProjectTemplate.requiredCustomerFieldIds arrays. Native query: `UPDATE project_templates SET required_customer_field_ids = required_customer_field_ids - '"uuid"'::jsonb WHERE required_customer_field_ids @> '"uuid"'::jsonb`. |
| 243.7 | Write engagement prerequisite service tests | 243A | 243.5 | New file or extend existing: `prerequisite/EngagementPrerequisiteTest.java`. Tests (~8): (1) `checkEngagement_allFieldsFilled_passes`; (2) `checkEngagement_missingField_returnsViolation`; (3) `checkEngagement_templateHasNoRequirements_passes`; (4) `checkEngagement_inactiveFieldInTemplate_skipped`; (5) `updateRequiredFields_validIds_succeeds`; (6) `updateRequiredFields_nonExistentId_returns400`; (7) `updateRequiredFields_nonCustomerField_returns400`; (8) `fieldDeactivation_removesFromTemplates`. |
| 243.8 | Add `PUT /{id}/required-customer-fields` to ProjectTemplateController | 243B | 243.4 | Modify: `projecttemplate/ProjectTemplateController.java`. New endpoint: `PUT /api/project-templates/{id}/required-customer-fields` (ADMIN+). Request body: `{ fieldDefinitionIds: [uuid, ...] }`. Calls `projectTemplateService.updateRequiredCustomerFields()`. Returns 200 with updated template. |
| 243.9 | Add `GET /{id}/prerequisite-check` to ProjectTemplateController | 243B | 243.5 | Modify: `projecttemplate/ProjectTemplateController.java`. New endpoint: `GET /api/project-templates/{id}/prerequisite-check?customerId={custId}` (MEMBER+). Calls `prerequisiteService.checkEngagementPrerequisites(customerId, templateId)`. Returns PrerequisiteCheck (200 always -- query endpoint). |
| 243.10 | Wire engagement prerequisites into manual project creation | 243B | 243.5 | Modify: `project/ProjectService.java` (or wherever project-from-template creation happens). When creating from template with linked customer: call `prerequisiteService.checkEngagementPrerequisites()`. If failed and manual creation: throw `PrerequisiteNotMetException`. |
| 243.11 | Wire notification for automated project creation | 243B | 243.5 | Modify: `proposal/ProposalOrchestrationService.java` and `schedule/ScheduleExecutionService.java` (or equivalent). When creating project from template and prerequisites fail: log notification to team, but proceed with project creation. Notification: "Customer {name} is missing fields required for {template.name}: {field list}". |
| 243.12 | Write controller integration tests -- template required fields | 243B | 243.8 | New file: `projecttemplate/ProjectTemplatePrerequisiteTest.java`. Tests (~4): (1) `updateRequiredFields_asAdmin_succeeds`; (2) `updateRequiredFields_asMember_returns403`; (3) `getPrerequisiteCheck_fieldsComplete_returnsPassed`; (4) `getPrerequisiteCheck_fieldsMissing_returnsViolations`. |
| 243.13 | Write controller integration tests -- project creation gating | 243B | 243.10 | Tests (~2): (5) `createProjectFromTemplate_prerequisitesMet_succeeds`; (6) `createProjectFromTemplate_prerequisitesNotMet_returns422`. |
| 243.14 | Write automated creation notification tests | 243B | 243.11 | Tests (~2): (7) `proposalAcceptance_prerequisitesNotMet_createsProjectAndNotifies`; (8) `scheduleExecution_prerequisitesNotMet_createsProjectAndNotifies`. |

### Key Files

**Slice 243A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V54__project_template_required_customer_fields.sql`

**Slice 243A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionService.java`

**Slice 243B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalOrchestrationService.java`

**Slice 243B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/` -- schedule execution pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- notification creation

### Architecture Decisions

- **JSONB UUID array on ProjectTemplate**: Chosen over join table for consistency with codebase patterns (FieldGroup.dependsOn, FieldDefinition.requiredForContexts). Low cardinality (2-8 fields per template). See ADR-132.
- **Manual vs. automated enforcement**: Manual project creation blocks with 422. Automated creation (proposal acceptance, schedules) proceeds with notification. Client-facing actions must not silently fail.
- **Cleanup on field deactivation**: Removes orphan UUIDs from template arrays when a field is deactivated.

---

## Epic 244: Action-Point Prerequisite Wiring

**Goal**: Wire prerequisite checks into invoice generation, proposal sending, and document generation flows. Add structural prerequisite checks to `PrerequisiteService` for non-custom-field validations (portal contact, billing address, invoice lines).

**References**: Architecture doc Sections 33.3.4, 33.5.3.

**Dependencies**: Epic 241 (PrerequisiteService with REST API).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **244A** | 244.1--244.7 | Add structural prerequisite checks to PrerequisiteService (portal contact, billing address checks per context). Wire prerequisite checks into InvoiceService, ProposalService.sendProposal(), DocumentGenerationReadinessService. Tests (~8 tests). ~5 modified files. Backend only. | |
| **244B** | 244.8--244.13 | Extended action-point tests: structural violation scenarios, cross-domain 422 responses, update existing invoice/proposal/document tests to pass prerequisites. ~8 tests across ~3 files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 244.1 | Add structural checks to PrerequisiteService | 244A | 241A | Modify: `prerequisite/PrerequisiteService.java`. New method: `checkStructural(PrerequisiteContext context, EntityType entityType, UUID entityId)`. Per context: INVOICE_GENERATION checks billing address field exists in customFields, customer has portal contact or email for delivery; PROPOSAL_SEND checks customer has portal contact with email, proposal body is not empty; DOCUMENT_GENERATION delegates to existing `DocumentGenerationReadinessService`. Returns List<PrerequisiteViolation> with null fieldSlug and resolution text for structural issues. |
| 244.2 | Wire prerequisite check into InvoiceService | 244A | 244.1 | Modify: `invoice/InvoiceService.java`. Before invoice generation (in `generateInvoice()` or equivalent): load customer via project, call `prerequisiteService.checkForContext(INVOICE_GENERATION, CUSTOMER, customerId)`. If failed: throw `PrerequisiteNotMetException`. |
| 244.3 | Wire prerequisite check into ProposalService.sendProposal() | 244A | 244.1 | Modify: `proposal/ProposalService.java`. In `sendProposal()` method: call `prerequisiteService.checkForContext(PROPOSAL_SEND, CUSTOMER, customerId)`. If failed: throw `PrerequisiteNotMetException`. |
| 244.4 | Wire prerequisite check into document generation | 244A | 244.1 | Modify: `template/` or `document/` package (wherever document generation is triggered). Before generating: call `prerequisiteService.checkForContext(DOCUMENT_GENERATION, CUSTOMER, customerId)`. If failed: throw `PrerequisiteNotMetException`. |
| 244.5 | Add helper method for loading customer from project context | 244A | | Modify: `prerequisite/PrerequisiteService.java`. Helper: `resolveCustomerIdFromProject(UUID projectId)`. Loads project, finds linked customer via CustomerProjectRepository. Used by invoice generation and document generation where the entry point is a project, not a customer. |
| 244.6 | Write structural check unit tests | 244A | 244.1 | Extend `prerequisite/PrerequisiteServiceTest.java`. Tests (~4): (1) `structuralCheck_invoiceGeneration_missingBillingAddress_returnsViolation`; (2) `structuralCheck_proposalSend_missingPortalContact_returnsViolation`; (3) `structuralCheck_proposalSend_emptyBody_returnsViolation`; (4) `structuralCheck_invoiceGeneration_allPresent_noViolations`. |
| 244.7 | Write action-point wiring integration tests | 244A | 244.2, 244.3 | New file: `prerequisite/ActionPointPrerequisiteTest.java`. Tests (~4): (5) `generateInvoice_prerequisitesNotMet_returns422`; (6) `sendProposal_prerequisitesNotMet_returns422`; (7) `generateDocument_prerequisitesNotMet_returns422`; (8) `generateInvoice_prerequisitesMet_succeeds`. |
| 244.8 | Write structural violation detail tests | 244B | 244A | Extend test file. Tests (~3): (1) `structuralViolation_containsResolutionText`; (2) `structuralViolation_fieldSlugIsNull`; (3) `combinedViolations_customFieldAndStructural_bothReturned`. |
| 244.9 | Write cross-domain 422 response format tests | 244B | 244A | Tests (~2): (4) `422Response_containsContextAndViolationsArray`; (5) `422Response_violationCodesAreConsistent`. |
| 244.10 | Update existing invoice generation tests | 244B | 244.2 | Modify existing invoice test files. Ensure test customers have prerequisite fields filled (billing address, etc.). Use TestCustomerFactory helper or add custom field setup to test data. |
| 244.11 | Update existing proposal send tests | 244B | 244.3 | Modify existing proposal test files. Ensure test customers have portal contact and required PROPOSAL_SEND fields. |
| 244.12 | Update existing document generation tests | 244B | 244.4 | Modify existing document generation test files. Ensure test data satisfies DOCUMENT_GENERATION prerequisites. |
| 244.13 | Write combined prerequisite flow test | 244B | 244A | Test (~1): `prerequisiteCheckThenAction_fillFieldsAndRetry_succeeds` -- end-to-end: check returns violations, update customer fields, re-check returns passed. |

### Key Files

**Slice 244A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/DocumentGenerationReadinessService.java`

**Slice 244A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/ActionPointPrerequisiteTest.java`

**Slice 244B -- Modify:**
- Existing invoice test files
- Existing proposal test files
- Existing document generation test files

**Slice 244B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectRepository.java` -- project-to-customer lookup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/` -- portal contact entity for structural checks

### Architecture Decisions

- **Structural checks are hardcoded per context**: Unlike custom field checks (dynamic from FieldDefinition), structural checks (portal contact, billing address) are specific to each action type and hardcoded in PrerequisiteService. This is intentional -- structural prerequisites are domain-specific invariants, not user-configurable.
- **Resolution text for structural violations**: Non-custom-field violations include a `resolution` string that guides the user to the relevant page (e.g., "Add a portal contact on the customer detail page"). The frontend renders this as a link.

---

## Epic 245: PrerequisiteModal & Shared Frontend Components

**Goal**: Build the shared `PrerequisiteModal` component, `usePrerequisiteCheck` hook, `InlineFieldEditor`, violation list, and TypeScript types. These are the foundation components reused by all frontend enforcement points.

**References**: Architecture doc Sections 33.7, 33.9.2, 33.9.3. ADR-130 (soft-blocking with inline remediation).

**Dependencies**: Epic 241 (REST API endpoints).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **245A** | 245.1--245.8 | PrerequisiteContext/Check/Violation TypeScript types + usePrerequisiteCheck hook + InlineFieldEditor component (all FieldType variants) + prerequisite-violation-list.tsx + prerequisites API client + tests (~6 tests). ~6 new files. Frontend only. | |
| **245B** | 245.9--245.14 | PrerequisiteModal component: grouped violations, inline field editing, "Check & Continue" re-validation, batch save via customer update, auto-proceed via onResolved callback + tests (~6 tests). ~2 new files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 245.1 | Create prerequisite TypeScript types | 245A | | New file: `frontend/components/prerequisite/types.ts`. Interfaces: `PrerequisiteContext` (enum-like const), `PrerequisiteCheck` (passed, context, violations), `PrerequisiteViolation` (code, message, entityType, entityId, fieldSlug, groupName, resolution), `IntakeFieldGroup` (id, name, slug, fields), `IntakeField` (id, name, slug, fieldType, required, options, defaultValue, requiredForContexts). Export `PREREQUISITE_CONTEXT_LABELS` map for display. |
| 245.2 | Create prerequisites API client | 245A | | New file: `frontend/lib/prerequisites.ts`. Functions: `checkPrerequisites(context, entityType, entityId)` calls `GET /api/prerequisites/check`, `fetchIntakeFields(entityType)` calls `GET /api/field-definitions/intake`, `checkEngagementPrerequisites(templateId, customerId)` calls `GET /api/project-templates/{id}/prerequisite-check`. Uses existing `api.ts` fetch wrapper. Pattern: `frontend/lib/setup-status.ts`. |
| 245.3 | Create `usePrerequisiteCheck` hook | 245A | 245.2 | New file: `frontend/hooks/use-prerequisite-check.ts`. Hook: `usePrerequisiteCheck(context, entityType, entityId)`. Returns `{ check, loading, runCheck, reset }`. `runCheck()` calls API, sets state. `reset()` clears check state. Pattern: existing hooks in `frontend/hooks/`. |
| 245.4 | Create `InlineFieldEditor` component | 245A | | New file: `frontend/components/prerequisite/inline-field-editor.tsx`. Renders a single custom field editor based on `fieldType`: TEXT -> Input, NUMBER -> Input[type=number], DATE -> DatePicker, SELECT/DROPDOWN -> Select, BOOLEAN -> Checkbox, PHONE -> Input[type=tel]. Props: `fieldDefinition`, `value`, `onChange`. Reuses Shadcn UI primitives. Pattern: existing custom field rendering in `frontend/components/field-definitions/CustomFieldSection.tsx`. |
| 245.5 | Create `PrerequisiteViolationList` component | 245A | 245.1 | New file: `frontend/components/prerequisite/prerequisite-violation-list.tsx`. Renders violations grouped by entityType. For custom field violations (fieldSlug non-null): shows field name + InlineFieldEditor. For structural violations: shows message + resolution text as a link. Pattern: list rendering similar to `frontend/components/setup/field-value-grid.tsx`. |
| 245.6 | Create prerequisite server actions | 245A | | New file: `frontend/lib/actions/prerequisite-actions.ts`. Server actions: `checkPrerequisitesAction(context, entityType, entityId)`, `fetchIntakeFieldsAction(entityType)`, `checkEngagementPrerequisitesAction(templateId, customerId)`. Wraps API client with auth token injection. Pattern: existing `frontend/app/(app)/org/[slug]/customers/actions.ts`. |
| 245.7 | Write InlineFieldEditor tests | 245A | 245.4 | New file: `frontend/__tests__/components/prerequisite/inline-field-editor.test.tsx`. Tests (~3): (1) `rendersTextInputForTextField`; (2) `rendersDatePickerForDateField`; (3) `callsOnChangeWithNewValue`. Pattern: existing component tests. |
| 245.8 | Write usePrerequisiteCheck hook test | 245A | 245.3 | New file: `frontend/__tests__/hooks/use-prerequisite-check.test.ts`. Tests (~3): (1) `runCheck_setLoadingThenResult`; (2) `runCheck_passedTrue_noViolations`; (3) `reset_clearsCheckState`. |
| 245.9 | Create `PrerequisiteModal` component -- layout and violations display | 245B | 245A | New file: `frontend/components/prerequisite/prerequisite-modal.tsx`. Props: `open`, `context`, `violations`, `entityType`, `entityId`, `onResolved`, `onCancel`. Dialog (Shadcn Dialog) with title based on context label. Renders PrerequisiteViolationList with inline editors for custom field violations. "Check & Continue" button at bottom. Pattern: `frontend/components/acceptance/` dialogs for modal structure. |
| 245.10 | Implement "Check & Continue" re-validation flow | 245B | 245.9 | In `prerequisite-modal.tsx`. On "Check & Continue": (1) batch-save edited field values via `PUT /api/customers/{id}` (update customFields), (2) re-run prerequisite check via `checkPrerequisitesAction()`, (3) if passed -> call `onResolved()` and close modal, (4) if still failing -> update violations display. |
| 245.11 | Implement batch field value save | 245B | 245.10 | In `prerequisite-modal.tsx`. Collect all edited field values from InlineFieldEditor instances. Submit as single `PUT /api/customers/{entityId}` with `{ customFields: { slug: value, ... } }`. Handle errors (display validation errors inline). |
| 245.12 | Add loading states and error handling | 245B | 245.10 | In `prerequisite-modal.tsx`. Loading spinner on "Check & Continue" during save + re-check. Error toast on save failure. Disable button while loading. |
| 245.13 | Write PrerequisiteModal tests -- display | 245B | 245.9 | New file: `frontend/__tests__/components/prerequisite/prerequisite-modal.test.tsx`. Tests (~3): (1) `rendersViolationsGroupedByEntity`; (2) `rendersInlineEditorForCustomFieldViolations`; (3) `rendersResolutionLinkForStructuralViolations`. |
| 245.14 | Write PrerequisiteModal tests -- interaction | 245B | 245.10 | Continuing test file. Tests (~3): (4) `checkAndContinue_savesFieldsAndRechecks`; (5) `checkAndContinue_passed_callsOnResolved`; (6) `checkAndContinue_stillFailing_updatesViolations`. |

### Key Files

**Slice 245A -- Create:**
- `frontend/components/prerequisite/types.ts`
- `frontend/lib/prerequisites.ts`
- `frontend/hooks/use-prerequisite-check.ts`
- `frontend/components/prerequisite/inline-field-editor.tsx`
- `frontend/components/prerequisite/prerequisite-violation-list.tsx`
- `frontend/lib/actions/prerequisite-actions.ts`

**Slice 245A -- Read for context:**
- `frontend/components/field-definitions/CustomFieldSection.tsx` -- field rendering pattern
- `frontend/components/setup/field-value-grid.tsx` -- field display pattern
- `frontend/lib/setup-status.ts` -- API client pattern

**Slice 245B -- Create:**
- `frontend/components/prerequisite/prerequisite-modal.tsx`
- `frontend/__tests__/components/prerequisite/prerequisite-modal.test.tsx`

**Slice 245B -- Read for context:**
- `frontend/components/acceptance/` -- modal/dialog patterns
- `frontend/app/(app)/org/[slug]/customers/actions.ts` -- customer update server action pattern

### Architecture Decisions

- **PrerequisiteModal is the single inline remediation UX**: Per ADR-130, all enforcement points use the same modal. This avoids duplicate form implementations across invoice, proposal, document, and lifecycle UI.
- **InlineFieldEditor reuses Shadcn primitives**: Each FieldType maps to a Shadcn input component. No custom input implementations.
- **Batch save via existing customer update endpoint**: The modal saves all edited fields in one PUT call to the existing customer update endpoint. No new save endpoint needed.

---

## Epic 246: Smart Customer Intake Dialog

**Goal**: Transform the `CreateCustomerDialog` from a single-step base-fields form into a multi-step dialog that surfaces auto-apply field groups with required fields inline at creation time.

**References**: Architecture doc Sections 33.3.1, 33.5.1.

**Dependencies**: Epics 241 (intake endpoint), 245 (InlineFieldEditor).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **246A** | 246.1--246.5 | `IntakeFieldsSection` component (renders auto-apply field groups as form sections, renders fields by type, handles conditional visibility) + intake endpoint server action + tests (~4 tests). ~3 new files. Frontend only. | |
| **246B** | 246.6--246.10 | `CreateCustomerDialog` multi-step flow: Step 1 (base fields, existing) -> Step 2 (intake custom fields from IntakeFieldsSection). Submit includes customFields in payload. "Skip for now" collapses optional fields. Tests (~5 tests). ~2 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 246.1 | Create `IntakeFieldsSection` component | 246A | 245A | New file: `frontend/components/customers/intake-fields-section.tsx`. Props: `groups: IntakeFieldGroup[]`, `values: Record<string, any>`, `onChange: (slug, value) => void`. Renders each group as a collapsible section with group name header. Required fields shown expanded; optional fields in collapsible "Additional Information" section. Each field renders via InlineFieldEditor (from 245A). Handles conditional visibility rules (visibilityCondition on FieldDefinition). Pattern: `frontend/components/field-definitions/CustomFieldSection.tsx`. |
| 246.2 | Create intake fetch server action | 246A | | New file or extend: `frontend/app/(app)/org/[slug]/customers/intake-actions.ts`. Server action: `fetchIntakeFields(entityType)` wrapping `fetchIntakeFieldsAction()` from prerequisite-actions. |
| 246.3 | Write IntakeFieldsSection tests | 246A | 246.1 | New file: `frontend/__tests__/components/customers/intake-fields-section.test.tsx`. Tests (~4): (1) `rendersGroupsWithFieldsByType`; (2) `requiredFieldsShownExpanded`; (3) `optionalFieldsInCollapsibleSection`; (4) `callsOnChangeWhenFieldEdited`. |
| 246.4 | Add TypeScript types for intake response | 246A | | Extend `frontend/components/prerequisite/types.ts` or `frontend/lib/types.ts` with intake response shape if not already covered by 245.1. |
| 246.5 | Handle visibility conditions in IntakeFieldsSection | 246A | 246.1 | In `intake-fields-section.tsx`. Apply `visibilityCondition` logic from FieldDefinition. If a field has a visibility condition, only show it when the condition is met (check against current form values). Pattern: existing conditional visibility in `frontend/components/field-definitions/CustomFieldSection.tsx`. |
| 246.6 | Convert CreateCustomerDialog to multi-step | 246B | 246A | Modify: `frontend/components/customers/create-customer-dialog.tsx`. Add Step 2 after base fields. On customer type selection -> fetch intake fields for CUSTOMER entity type. Show loading state while fetching. Render IntakeFieldsSection with fetched groups. "Next" button from Step 1 to Step 2. "Back" button from Step 2 to Step 1. "Create" button on Step 2 submits. |
| 246.7 | Submit with customFields in payload | 246B | 246.6 | In `create-customer-dialog.tsx`. On submit: combine base fields + custom field values from IntakeFieldsSection. Call `createCustomer` action with `{ ...baseFields, customFields: { [slug]: value, ... } }`. Handle 400 validation errors (display field-level errors). |
| 246.8 | Add "Skip for now" for optional fields | 246B | 246.6 | In Step 2: if only optional fields remain (all required fields are filled or there are no required fields), show "Skip for now" link that collapses optional fields and enables "Create". |
| 246.9 | Update customer create action to accept customFields | 246B | | Modify: `frontend/app/(app)/org/[slug]/customers/actions.ts`. Extend `createCustomer` server action to include `customFields` in the request body to `POST /api/customers`. |
| 246.10 | Write CreateCustomerDialog multi-step tests | 246B | 246.6 | Extend or new: `frontend/__tests__/components/customers/create-customer-dialog.test.tsx`. Tests (~5): (1) `showsStep2AfterStep1Next`; (2) `fetchesIntakeFieldsOnTypeSelection`; (3) `submitIncludesCustomFields`; (4) `backButtonReturnsToStep1`; (5) `skipForNow_collapsesOptionalFields`. |

### Key Files

**Slice 246A -- Create:**
- `frontend/components/customers/intake-fields-section.tsx`
- `frontend/app/(app)/org/[slug]/customers/intake-actions.ts`
- `frontend/__tests__/components/customers/intake-fields-section.test.tsx`

**Slice 246B -- Modify:**
- `frontend/components/customers/create-customer-dialog.tsx`
- `frontend/app/(app)/org/[slug]/customers/actions.ts`

**Slice 246B -- Read for context:**
- `frontend/components/customers/edit-customer-dialog.tsx` -- customer edit pattern
- `frontend/components/field-definitions/CustomFieldSection.tsx` -- field rendering reference

### Architecture Decisions

- **Two-step dialog, not separate page**: The intake remains a dialog (not a full page) for consistency with the existing CreateCustomerDialog pattern. Step 2 extends the dialog rather than replacing it.
- **Intake endpoint is separate from prerequisite check**: `GET /api/field-definitions/intake` returns all auto-apply fields for form rendering. It does not filter by `requiredForContexts` -- the dialog renders all fields with visual distinction for required vs. optional.

---

## Epic 247: Prerequisite Configuration UI

**Goal**: Add "Required For" multi-select to the field definition editor in Settings, allowing admins to configure which prerequisite contexts each field participates in.

**References**: Architecture doc Sections 33.6.1, 33.6.3.

**Dependencies**: Epic 245 (types and context labels).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **247A** | 247.1--247.5 | "Required For" multi-select on FieldDefinitionDialog + PrerequisiteContext human-readable labels + update PATCH action to include requiredForContexts + tests (~3 tests). ~3 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 247.1 | Add "Required For" multi-select to FieldDefinitionDialog | 247A | 245A | Modify: `frontend/components/field-definitions/FieldDefinitionDialog.tsx`. Add a multi-select dropdown (Shadcn MultiSelect or checkboxes) below the "Required" toggle. Options from `PREREQUISITE_CONTEXT_LABELS` (from prerequisite/types.ts): "Customer Activation", "Invoice Generation", "Proposal Sending", "Document Generation", "Project Creation". Pre-populated from field definition's `requiredForContexts`. |
| 247.2 | Update field definition PATCH action | 247A | | Modify: `frontend/app/(app)/org/[slug]/settings/custom-fields/actions.ts`. Extend the update field definition action to include `requiredForContexts` in the PATCH request body. |
| 247.3 | Show "Required For" in field definition list | 247A | | Modify: `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` or equivalent. Show prerequisite context badges/pills next to field definitions that have non-empty `requiredForContexts`. |
| 247.4 | Handle field pack defaults display | 247A | | In FieldDefinitionDialog: for fields from packs (where `packId` is non-null), show the current `requiredForContexts` as the default with a note "Set by field pack -- override by changing selections." |
| 247.5 | Write configuration UI tests | 247A | 247.1 | New or extend: `frontend/__tests__/components/field-definitions/field-definition-dialog.test.tsx`. Tests (~3): (1) `rendersRequiredForMultiSelect`; (2) `submitIncludesRequiredForContexts`; (3) `showsPackDefaultNote`. |

### Key Files

**Slice 247A -- Modify:**
- `frontend/components/field-definitions/FieldDefinitionDialog.tsx`
- `frontend/app/(app)/org/[slug]/settings/custom-fields/actions.ts`
- `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx`

**Slice 247A -- Read for context:**
- `frontend/components/prerequisite/types.ts` -- PREREQUISITE_CONTEXT_LABELS

### Architecture Decisions

- **Multi-select dropdown, not checkboxes**: Uses a dropdown for space efficiency (5 options fit well in a dropdown). Consistent with Shadcn UI patterns.
- **Full replacement semantics**: PATCH with `requiredForContexts` replaces the entire array. No additive/subtractive API.

---

## Epic 248: Lifecycle Transition Frontend Integration

**Goal**: Wire the `PrerequisiteModal` into the customer detail page lifecycle transition buttons so that ONBOARDING -> ACTIVE transitions show the modal when prerequisites are not met.

**References**: Architecture doc Sections 33.3.2, 33.5.2.

**Dependencies**: Epics 242 (backend lifecycle gate), 245 (PrerequisiteModal).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **248A** | 248.1--248.5 | Customer detail page "Activate" button wired through usePrerequisiteCheck. On 422 -> open PrerequisiteModal. On modal resolved -> re-trigger transition. Tests (~4 tests). ~3 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 248.1 | Wire prerequisite check before lifecycle transition | 248A | 245B | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` or lifecycle button component. Before calling transition action: call `checkPrerequisitesAction(LIFECYCLE_ACTIVATION, CUSTOMER, customerId)`. If not passed -> open PrerequisiteModal instead of calling transition. |
| 248.2 | Handle 422 from transition endpoint | 248A | 242A | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/lifecycle-actions.ts`. Extend transition action to catch 422 responses and return the PrerequisiteCheck payload to the caller. The page component interprets this to open the modal. |
| 248.3 | Integrate PrerequisiteModal on customer detail | 248A | 248.1 | In customer detail page: render `<PrerequisiteModal>` component with `context="LIFECYCLE_ACTIVATION"`, `entityId={customerId}`, `onResolved` callback that retries the transition action. |
| 248.4 | Update SetupProgressCard to show activation prerequisites | 248A | | Modify: `frontend/components/setup/setup-progress-card.tsx`. When customer is in ONBOARDING status, highlight which fields are blocking activation (using `LIFECYCLE_ACTIVATION` context data from readiness API). |
| 248.5 | Write lifecycle transition frontend tests | 248A | 248.1 | New or extend: `frontend/__tests__/app/customers/lifecycle-transition.test.tsx`. Tests (~4): (1) `activateButton_prerequisitesNotMet_opensModal`; (2) `modal_fillFieldsAndCheckContinue_transitionsSuccessfully`; (3) `activateButton_prerequisitesMet_transitionsDirectly`; (4) `setupProgressCard_showsActivationBlockers`. |

### Key Files

**Slice 248A -- Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/lifecycle-actions.ts`
- `frontend/components/setup/setup-progress-card.tsx`

**Slice 248A -- Read for context:**
- `frontend/components/prerequisite/prerequisite-modal.tsx` -- modal component from 245B
- `frontend/hooks/use-prerequisite-check.ts` -- hook from 245A

---

## Epic 249: Engagement & Action-Point Frontend Integration

**Goal**: Wire the `PrerequisiteModal` into invoice generation, proposal sending, document generation buttons, and project creation dialog. Add "Required Customer Fields" configuration to the project template editor.

**References**: Architecture doc Sections 33.3.3, 33.3.4, 33.5.3, 33.5.4, 33.6.4.

**Dependencies**: Epics 243 (engagement backend), 244 (action-point backend), 245 (PrerequisiteModal).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **249A** | 249.1--249.6 | Invoice generation, proposal send, document generation buttons wired through prerequisite check + PrerequisiteModal integration at each action point + tests (~5 tests). ~5 modified files. Frontend only. | |
| **249B** | 249.7--249.12 | Project template editor "Required Customer Fields" multi-select section + project creation dialog prerequisite check with template + tests (~4 tests). ~3 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 249.1 | Wire prerequisite check before invoice generation | 249A | 245B | Modify: invoice generation UI (likely `frontend/components/invoices/` or project detail invoice tab). Before generating: check prerequisites with `INVOICE_GENERATION` context. If not passed -> open PrerequisiteModal. On resolved -> proceed with generation. |
| 249.2 | Wire prerequisite check before proposal send | 249A | 245B | Modify: `frontend/components/proposals/` or proposal detail page. Before sending: check prerequisites with `PROPOSAL_SEND` context for the customer. If not passed -> open PrerequisiteModal. On resolved -> proceed with send. Intercept 422 from send endpoint as fallback. |
| 249.3 | Wire prerequisite check before document generation | 249A | 245B | Modify: document generation dialog or button component. Before generating: check prerequisites with `DOCUMENT_GENERATION` context. If not passed -> open PrerequisiteModal. On resolved -> proceed with generation. |
| 249.4 | Create reusable `PrerequisiteGatedAction` wrapper | 249A | 245B | New file: `frontend/components/prerequisite/prerequisite-gated-action.tsx`. A higher-order component or render-prop component that wraps an action button: on click -> run prerequisite check -> if passed, execute action; if failed, show modal. Props: `context`, `entityType`, `entityId`, `onAction`, `children`. Reduces boilerplate for 249.1-249.3. |
| 249.5 | Wire PrerequisiteGatedAction into action buttons | 249A | 249.4 | Apply `PrerequisiteGatedAction` wrapper to all gated buttons: "Generate Invoice", "Send Proposal", "Generate Document". Each button becomes a child of the wrapper with appropriate context/entity props. |
| 249.6 | Write action-point frontend tests | 249A | 249.1 | New or extend test files. Tests (~5): (1) `generateInvoice_prerequisitesNotMet_opensModal`; (2) `sendProposal_prerequisitesNotMet_opensModal`; (3) `generateDocument_prerequisitesNotMet_opensModal`; (4) `gatedAction_prerequisitesMet_executesAction`; (5) `gatedAction_modalResolved_executesAction`. |
| 249.7 | Add "Required Customer Fields" section to template editor | 249B | 243B | Modify: `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx`. New section: "Required Customer Fields" with a searchable multi-select of active customer FieldDefinition records. Fetch available fields via existing field definition list endpoint (filter by entityType=CUSTOMER, active=true). Save via `PUT /api/project-templates/{id}/required-customer-fields`. |
| 249.8 | Create template required fields server action | 249B | | New or extend: `frontend/app/(app)/org/[slug]/settings/project-templates/actions.ts`. Server action: `updateRequiredCustomerFields(templateId, fieldDefinitionIds)` wrapping PUT endpoint. |
| 249.9 | Wire prerequisite check into project creation dialog | 249B | 245B | Modify: project creation dialog (wherever "Create Project from Template" exists). When template is selected and customer is linked: call `checkEngagementPrerequisitesAction(templateId, customerId)`. If not passed -> open PrerequisiteModal before proceeding. On resolved -> continue project creation. |
| 249.10 | Show prerequisite info during proposal creation | 249B | | Modify: proposal creation form (where template is selected). When template is selected and customer is linked: show informational note if customer is missing template-required fields. Non-blocking at proposal creation -- blocking happens at acceptance/send. |
| 249.11 | Write template required fields tests | 249B | 249.7 | Tests (~2): (1) `templateEditor_selectRequiredFields_saves`; (2) `templateEditor_displaysExistingRequiredFields`. |
| 249.12 | Write project creation prerequisite tests | 249B | 249.9 | Tests (~2): (3) `createProjectFromTemplate_prerequisitesNotMet_opensModal`; (4) `createProjectFromTemplate_noTemplate_noPrerequisiteCheck`. |

### Key Files

**Slice 249A -- Create:**
- `frontend/components/prerequisite/prerequisite-gated-action.tsx`

**Slice 249A -- Modify:**
- Invoice generation UI component(s)
- Proposal detail/send UI component(s)
- Document generation UI component(s)

**Slice 249B -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/project-templates/actions.ts`
- Project creation dialog component

### Architecture Decisions

- **PrerequisiteGatedAction wrapper reduces boilerplate**: A single wrapper component handles the check-before-action pattern for all enforcement points, keeping individual action buttons clean.
- **Proposal creation is informational, not blocking**: Template prerequisite info is shown but not enforced at proposal creation -- enforcement happens at send and acceptance.

---

## Epic 250: Completeness Visibility -- Backend Queries

**Goal**: Add backend endpoints for customer completeness summary computation, per-context readiness breakdown, and aggregated completeness stats for the dashboard widget.

**References**: Architecture doc Sections 33.7.1, 33.7.2, 33.7.3.

**Dependencies**: Epic 241 (field definition context queries).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **250A** | 250.1--250.7 | GET /api/customers/completeness-summary endpoint + CustomerReadinessService.computeReadinessByContext() + CompletenessScore record + batch computation (avoid N+1) + aggregated query for dashboard widget + tests (~6 tests). ~3 modified/new files, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 250.1 | Implement `CustomerReadinessService.computeReadinessByContext()` | 250A | 241A | Modify: `setupstatus/CustomerReadinessService.java`. New method: `Map<PrerequisiteContext, RequiredFieldStatus> computeReadinessByContext(UUID customerId)`. For each PrerequisiteContext with at least one required field: count total required, count filled (from customer.customFields). Return per-context breakdown. Reuses `CustomFieldUtils.isFieldValueFilled()`. |
| 250.2 | Create `CompletenessScore` record | 250A | | In `setupstatus/` or `prerequisite/` package. Record: `CompletenessScore(int totalRequired, int filled, int percentage)`. Computed from per-context readiness. |
| 250.3 | Implement batch completeness computation | 250A | 250.1 | In `CustomerReadinessService.java`. New method: `Map<UUID, CompletenessScore> batchComputeCompleteness(List<UUID> customerIds)`. Loads all required field definitions in one query. Loads all customers' customFields in one query. Evaluates completeness per customer. Avoids N+1. |
| 250.4 | Add `GET /api/customers/completeness-summary` endpoint | 250A | 250.3 | Modify: `customer/CustomerController.java` or new `prerequisite/PrerequisiteController.java`. Endpoint: `GET /api/customers/completeness-summary?customerIds=uuid1,uuid2,...` (MEMBER+). Returns `Map<UUID, CompletenessScore>`. For customer list page integration (called with current page's customer IDs). |
| 250.5 | Add aggregated completeness query for dashboard | 250A | 250.3 | In `CustomerReadinessService.java`. New method: `List<MissingFieldSummary> getTopMissingFields(int limit)`. Returns the N most common missing required fields with customer counts. Record: `MissingFieldSummary(String fieldName, String fieldSlug, int customerCount)`. Used by dashboard widget. |
| 250.6 | Add aggregated endpoint | 250A | 250.5 | Extend completeness-summary endpoint or new endpoint: `GET /api/customers/completeness-summary?aggregated=true` (ADMIN+). Returns `{ topMissingFields: [...], incompleteCount: N, totalCount: N }`. |
| 250.7 | Write completeness query tests | 250A | 250.1 | New file: `setupstatus/CompletenessQueryTest.java` or `prerequisite/CompletenessQueryTest.java`. Tests (~6): (1) `computeReadinessByContext_returnsPerContextBreakdown`; (2) `batchCompute_multipleCustomers_returnsScores`; (3) `batchCompute_avoidNPlusOne_singleQuery`; (4) `completenessEndpoint_returnsScoresForRequestedIds`; (5) `aggregatedQuery_returnsTopMissingFields`; (6) `completenessScore_allFieldsFilled_returns100Percent`. |

### Key Files

**Slice 250A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadinessService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` (or PrerequisiteController)

**Slice 250A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/CompletenessQueryTest.java`

**Slice 250A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/RequiredFieldStatus.java` -- existing readiness record
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` -- batch loading pattern

### Architecture Decisions

- **Batch computation avoids N+1**: The completeness summary endpoint accepts a list of customer IDs (from the current page) and computes all scores in a single pass. Field definitions are loaded once, not per-customer.
- **Aggregated query is separate from per-customer**: The dashboard widget uses a different query shape (top missing fields by customer count) than the list page (per-customer scores).

---

## Epic 251: Completeness Visibility -- Frontend & Dashboard

**Goal**: Add completeness badges to the customer list, enhance the `SetupProgressCard` with context-grouped display, add a completeness ring on the customer detail header, and create the "Incomplete Customer Profiles" dashboard widget.

**References**: Architecture doc Sections 33.7.1, 33.7.2, 33.7.3.

**Dependencies**: Epics 250 (backend completeness queries), 245 (shared components).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **251A** | 251.1--251.5 | `CompletenessBadge` component + customer list page completeness column + filter ("show incomplete") + sort by completeness + completeness server actions + tests (~4 tests). ~4 new/modified files. Frontend only. | |
| **251B** | 251.6--251.11 | Enhanced `SetupProgressCard` with context-grouped display + expandable groups showing missing fields + completeness ring on customer detail header + "Incomplete Customer Profiles" dashboard widget + tests (~5 tests). ~4 modified/new files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 251.1 | Create `CompletenessBadge` component | 251A | | New file: `frontend/components/customers/completeness-badge.tsx`. Props: `score: CompletenessScore`. Renders a pill/badge with percentage. Color: green (100%), amber (50-99%), red (<50%). Pattern: `frontend/components/` badge components (e.g., status badges). |
| 251.2 | Create completeness server actions | 251A | | New or extend: `frontend/app/(app)/org/[slug]/customers/actions.ts`. Server actions: `fetchCompletenessSummary(customerIds)`, `fetchAggregatedCompleteness()`. Wraps completeness-summary endpoints. |
| 251.3 | Add completeness column to customer list | 251A | 251.1, 251.2 | Modify: `frontend/app/(app)/org/[slug]/customers/page.tsx`. After loading customers page, call `fetchCompletenessSummary` with page customer IDs. Add "Completeness" column rendering `CompletenessBadge`. |
| 251.4 | Add completeness filter and sort | 251A | 251.3 | Modify: customer list page. Add "Show incomplete" filter toggle (completeness < 100%). Add sort option for completeness column (ascending = most incomplete first). |
| 251.5 | Write completeness list tests | 251A | 251.1 | Tests (~4): (1) `completenessBadge_100percent_showsGreen`; (2) `completenessBadge_50percent_showsAmber`; (3) `customerList_showsCompletenessColumn`; (4) `customerList_filterIncomplete_showsOnlyIncomplete`. |
| 251.6 | Enhance SetupProgressCard with context-grouped display | 251B | 245A | Modify: `frontend/components/setup/setup-progress-card.tsx`. Replace single "Required Fields: 3/5" with per-context groups: "For Activation: 3/5 fields", "For Invoicing: 1/2 fields". Each group shows context label + filled/total. Green checkmark for 100% groups. Uses readiness-by-context data (requires backend endpoint from 250A or extending existing readiness call). |
| 251.7 | Add expandable missing field details | 251B | 251.6 | In SetupProgressCard: each context group is expandable (click to reveal missing field names). Click a field name -> scroll to or highlight the field on the customer detail page. |
| 251.8 | Add completeness ring to customer detail header | 251B | | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`. Add a circular progress indicator (ring/donut) in the customer header area showing overall completeness percentage. Uses CompletenessScore data. CSS: Tailwind + SVG ring. |
| 251.9 | Create "Incomplete Customer Profiles" dashboard widget | 251B | 251.2 | New file: `frontend/components/dashboard/incomplete-profiles-widget.tsx`. Shown on company dashboard for admin/owner roles. Fetches aggregated completeness data. Shows: "N customers with incomplete profiles", top missing fields with counts ("5 customers missing billing address"). Click group -> navigate to filtered customer list. Pattern: existing dashboard widgets in `frontend/components/dashboard/`. |
| 251.10 | Integrate dashboard widget | 251B | 251.9 | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. Add `IncompleteProfilesWidget` for admin/owner roles. Position alongside existing dashboard cards. |
| 251.11 | Write visibility tests | 251B | 251.6, 251.9 | Tests (~5): (1) `setupProgressCard_showsContextGroupedDisplay`; (2) `setupProgressCard_expandableGroupShowsMissingFields`; (3) `completenessRing_showsPercentage`; (4) `dashboardWidget_showsIncompleteCount`; (5) `dashboardWidget_clickGroup_navigatesToFilteredList`. |

### Key Files

**Slice 251A -- Create:**
- `frontend/components/customers/completeness-badge.tsx`

**Slice 251A -- Modify:**
- `frontend/app/(app)/org/[slug]/customers/page.tsx`
- `frontend/app/(app)/org/[slug]/customers/actions.ts`

**Slice 251B -- Modify:**
- `frontend/components/setup/setup-progress-card.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx`

**Slice 251B -- Create:**
- `frontend/components/dashboard/incomplete-profiles-widget.tsx`

**Slice 251B -- Read for context:**
- `frontend/components/setup/` -- existing setup components
- `frontend/components/dashboard/` -- existing dashboard widget pattern

### Architecture Decisions

- **Completeness is fetched per-page, not per-customer**: The customer list fetches completeness scores in batch for the current page to avoid N+1 API calls.
- **Dashboard widget is admin/owner only**: Aggregate completeness data is a management concern; regular members see completeness on individual customers they access.
- **Completeness ring uses SVG**: A lightweight SVG donut chart rather than a charting library for the header ring. Consistent with the Signal Deck aesthetic.

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java` -- Core entity to extend with `requiredForContexts` JSONB column; foundation for all prerequisite evaluation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadinessService.java` -- Existing readiness computation to extend with context-based evaluation and completeness scoring
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleService.java` -- Primary lifecycle enforcement point where ONBOARDING->ACTIVE prerequisite gate is wired
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/customers/create-customer-dialog.tsx` -- Existing dialog to convert to multi-step with intake field rendering
- `/Users/rakheendama/Projects/2026/b2b-strawman/tasks/phase32-proposal-engagement-pipeline.md` -- Reference format for epic/slice/task table structure, Key Files sections, and Architecture Decisions sections