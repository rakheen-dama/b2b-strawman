# Phase 14 — Customer Compliance & Lifecycle

Phase 14 adds a **jurisdiction-agnostic compliance system** managing the full customer lifecycle — prospect through onboarding, active engagement, dormancy detection, and offboarding. The system introduces a lifecycle state machine on the Customer entity that gates platform actions, a first-class checklist engine for per-step verification, compliance packs (bundled seed data per jurisdiction), data subject request workflows (access/deletion under POPIA/GDPR), and retention policy management.

**Architecture doc**: `architecture/phase14-customer-compliance-lifecycle.md`

**ADRs**: [ADR-060](../adr/ADR-060-lifecycle-status-core-field.md) (lifecycle status as core field), [ADR-061](../adr/ADR-061-checklist-first-class-entities.md) (checklist first-class entities), [ADR-062](../adr/ADR-062-anonymization-over-hard-deletion.md) (anonymization over hard deletion), [ADR-063](../adr/ADR-063-compliance-packs-bundled-seed-data.md) (compliance packs as bundled seed data)

**MIGRATION**: V29 (V28 is the latest from Phase 12). Single migration creates all new tables + ALTERs to Customer and OrgSettings.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 100 | Customer Lifecycle Foundation | Backend | -- | M | 100A, 100B | **Done** (PRs #208, #209) |
| 101 | Checklist Template Engine | Backend | 100 | M | 101A, 101B | **Done** (PRs #210, #211) |
| 102 | Checklist Instance Engine | Backend | 101 | M | 102A, 102B | **Done** (PRs #212, #213) |
| 103 | Compliance Pack Seeding & Instantiation | Backend | 101 | M | 103A, 103B | **Done** (PRs #214, #215) |
| 104 | Data Subject Requests | Backend | 100 | M | 104A, 104B | |
| 105 | Retention & Dormancy | Backend | 100 | M | 105A, 105B | |
| 106 | Lifecycle & Checklist Frontend | Frontend | 102, 103 | M | 106A, 106B | |
| 107 | Data Requests & Settings Frontend | Frontend | 104, 105 | M | 107A, 107B | |
| 108 | Compliance Dashboard | Frontend | 106, 107 | S | 108A | |

## Dependency Graph

```
[E100A V29 Migration + Customer Lifecycle]──►[E100B Guard + Dormancy + Controller]
     (Backend)                                    (Backend)
         │
         ├──────────────────────────────────────────────────────────────────┐
         │                                                                  │
         ▼                                                                  │
[E101A Checklist Template Entity + CRUD]──►[E101B Template Advanced]        │
     (Backend)                                  (Backend)                   │
         │                                          │                       │
         │                                          │                       │
         ▼                                          │                       │
[E102A Instance Entity + Service]──►[E102B Dependency Chain + Controller]   │
     (Backend)                           (Backend)                          │
                                             │                              │
         ┌───────────────────────────────────┘                              │
         │                                                                  │
         ▼                                                                  │
[E103A Pack Seeder + Packs]──►[E103B Instantiation + Provisioning]          │
     (Backend)                     (Backend)                                │
                                       │                                    │
                                       ▼                                    │
                            [E106A Lifecycle UI]──►[E106B Checklist UI]     │
                                 (Frontend)            (Frontend)           │
                                                           │                │
                                                           │     ┌──────────┘
                                                           │     │
                                                           │  [E104A DSR Entity + Export]──►[E104B Anonymization]
                                                           │     (Backend)                     (Backend)
                                                           │                                       │
                                                           │  [E105A Retention Entity + Check]──►[E105B Purge + Seeding]
                                                           │     (Backend)                         (Backend)
                                                           │                                       │
                                                           │     ┌─────────────────────────────────┘
                                                           │     │
                                                           │  [E107A DSR Frontend]
                                                           │     (Frontend)
                                                           │  [E107B Retention Settings]
                                                           │     (Frontend)
                                                           │                │
                                                           └────────┬───────┘
                                                                    │
                                                                    ▼
                                                           [E108A Compliance Dashboard]
                                                                (Frontend)
```

**Parallel opportunities**:
- After Epic 100 completes: Epics 101, 104, 105 can proceed in parallel (three independent backend tracks)
- After Epic 101 completes: Epic 102 follows sequentially, then 103
- After Epics 102+103 complete: Epic 106 (frontend)
- After Epics 104+105 complete: Epic 107 (frontend)
- Epic 108 is the final frontend slice after 106 and 107

## Implementation Order

### Stage 1: Foundation — Migration & Lifecycle (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 100 | 100A | **V29 migration** (Customer ALTERs, OrgSettings ALTERs, 6 new tables). Customer entity extension with lifecycle_status, customerType, offboardedAt. LifecycleStatus validation. ~5 entity/validation tests. Foundation for everything. | **Done** (PR #208) |
| 1b | Epic 100 | 100B | CustomerLifecycleService (state machine), CustomerLifecycleGuard (action gating), lifecycle controller endpoints, dormancy detection query, guard integration in existing services. ~20 tests. | **Done** (PR #209) |

### Stage 2: Checklist Engine (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 101 | 101A | ChecklistTemplate + ChecklistTemplateItem entities, repos, ChecklistTemplateService CRUD, ChecklistTemplateController. ~15 tests. | **Done** (PR #210) |
| 2b | Epic 101 | 101B | Template slug generation, dependency validation (cycle detection), clone/reset, ordering. ~12 tests. | **Done** (PR #211) |
| 2c | Epic 102 | 102A | ChecklistInstance + ChecklistInstanceItem entities, repos, ChecklistInstanceService (create from template, complete item, skip item, progress calculation). ~15 tests. | **Done** (PR #212) |
| 2d | Epic 102 | 102B | Item dependency chain enforcement, document linking, auto-cascade (instance complete → lifecycle advance), controller endpoints, audit events. ~15 tests. |

### Stage 3: Pack Seeding & Instantiation (Sequential, parallel with Stage 4+5)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 103 | 103A | CompliancePackSeeder, pack.json schema, 3 shipped packs, OrgSettings compliancePackStatus tracking. ~10 tests. | **Done** (PR #214) |
| 3b | Epic 103 | 103B | ChecklistInstantiationService (auto-create on ONBOARDING transition), lifecycle service integration, tenant provisioning integration. ~12 tests. | **Done** (PR #215) |

### Stage 4: Data Subject Requests (Parallel with Stage 2+3)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 104 | 104A | DataSubjectRequest entity + repo, DataSubjectRequestService, DataExportService (ZIP generation), DataRequestController, deadline tracking. ~15 tests. |
| 4b | Epic 104 | 104B | DataAnonymizationService, anonymization of customer PII + documents + comments + portal contacts, controller extension, deadline notification. ~12 tests. |

### Stage 5: Retention & Dormancy (Parallel with Stage 2+3+4)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a | Epic 105 | 105A | RetentionPolicy entity + repo, RetentionService (evaluate policies, flag records), retention check query. ~10 tests. |
| 5b | Epic 105 | 105B | Purge execution, RetentionController, default retention policy seeding via compliance packs, provisioning integration. ~10 tests. |

### Stage 6: Frontend — Lifecycle & Checklists

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 6a | Epic 106 | 106A | LifecycleStatusBadge, LifecycleTransitionDropdown, customer list lifecycle column, customer detail lifecycle section, transition dialogs, server actions. ~10 tests. |
| 6b | Epic 106 | 106B | ChecklistInstancePanel (Onboarding tab), ChecklistInstanceItemRow, progress bar, template selection, document upload link. ~10 tests. |

### Stage 7: Frontend — Data Requests & Settings (Parallel with Stage 6)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 7a | Epic 107 | 107A | Data request list page, CreateDataRequestDialog, request detail with timeline, export download, DeletionConfirmDialog. ~8 tests. |
| 7b | Epic 107 | 107B | Compliance settings page, retention policy table, "Run Retention Check" with results, dormancy threshold config. ~7 tests. |

### Stage 8: Dashboard

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 8a | Epic 108 | 108A | ComplianceDashboardPage, lifecycle distribution stats, onboarding pipeline, data requests section, dormancy candidates, sidebar nav, lifecycle-summary backend endpoint. ~10 tests. |

### Timeline

```
Stage 1:  [100A] ──► [100B]                                          ← foundation
Stage 2:  [101A] ──► [101B] ──► [102A] ──► [102B]                  ← checklist engine
Stage 3:  [103A] ──► [103B]                                          ← pack seeding (after 101B)
Stage 4:  [104A] ──► [104B]                                          ← data requests (after 100B)
Stage 5:  [105A] ──► [105B]                                          ← retention (after 100B)
Stage 6:  [106A] ──► [106B]                                          ← lifecycle UI (after 103B)
Stage 7:  [107A]  //  [107B]                                         ← DSR + settings UI (after 104B, 105B)
Stage 8:  [108A]                                                      ← dashboard (after 106B + 107A/B)
```

**Critical path**: 100A → 100B → 101A → 101B → 102A → 102B → 103A → 103B → 106A → 106B → 108A
**Parallelizable**: Stages 4+5 can run in parallel with Stage 2+3 after 100B. Stage 7 parallel with Stage 6.

---

## Epic 100: Customer Lifecycle Foundation

**Goal**: Extend the Customer entity with a lifecycle state machine (PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDED), add action gating via CustomerLifecycleGuard, implement lifecycle transition endpoints, and add dormancy detection. This epic includes the complete V29 migration covering ALL Phase 14 tables.

**References**: Architecture doc Sections 14.2.1 (Customer changes), 14.2.2 (OrgSettings changes), 14.2.10 (state machine), 14.3.1 (lifecycle transitions), 14.3.7 (dormancy detection), 14.4.1 (lifecycle endpoints), 14.6 (migration). [ADR-060](../adr/ADR-060-lifecycle-status-core-field.md).

**Dependencies**: None (foundation epic)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **100A** | 100.1–100.7 | V29 migration (Customer ALTERs, OrgSettings ALTERs, 6 new tables with constraints and indexes). Customer entity extension with lifecycle_status, customerType, offboardedAt fields. Domain methods: transitionLifecycleStatus(), anonymize(). OrgSettings extension with dormancyThresholdDays, dataRequestDeadlineDays, compliancePackStatus. Entity validation tests (~5 tests). | **Done** (PR #208) |
| **100B** | 100.8–100.16 | CustomerLifecycleService (state machine validation, transitions, side effect orchestration). CustomerLifecycleGuard (action gating per lifecycle status). LifecycleAction enum. CustomerStatusChangedEvent. CustomerController lifecycle endpoints. Dormancy detection query. Guard integration in ProjectService, InvoiceService, TimeEntryService, DocumentService. Integration tests (~20 tests). | **Done** (PR #209) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 100.1 | Create V29 migration file | 100A | | `db/migration/tenant/V29__customer_compliance_lifecycle.sql`. 8 blocks: (1) ALTER customers — add customer_type, lifecycle_status, lifecycle_status_changed_at, lifecycle_status_changed_by, offboarded_at + backfill existing to ACTIVE + index. (2) ALTER org_settings — add dormancy_threshold_days, data_request_deadline_days, compliance_pack_status. (3) CREATE checklist_templates with constraints + index. (4) CREATE checklist_template_items with FK + index. (5) CREATE checklist_instances with UNIQUE + indexes. (6) CREATE checklist_instance_items with UNIQUE + indexes. (7) CREATE data_subject_requests with indexes. (8) CREATE retention_policies with UNIQUE. Full SQL in architecture doc Section 14.6. No RLS (post-Phase 13). Pattern: follow V27__create_document_templates.sql. |
| 100.2 | Extend Customer entity with lifecycle fields | 100A | | Modify `customer/Customer.java`. Add fields: customerType (VARCHAR 20, default INDIVIDUAL), lifecycleStatus (VARCHAR 20, default PROSPECT), lifecycleStatusChangedAt (Instant), lifecycleStatusChangedBy (UUID), offboardedAt (Instant). Add domain method `transitionLifecycleStatus(String targetStatus, UUID actorId)` — sets status, changedAt, changedBy. Add domain method `anonymize(String replacementName)` — replaces name, email, phone with anonymized values. No public setters. Pattern: follow Invoice entity (Phase 10) for domain method style. |
| 100.3 | Extend OrgSettings entity with compliance fields | 100A | | Modify `orgsettings/OrgSettings.java`. Add fields: dormancyThresholdDays (Integer, nullable), dataRequestDeadlineDays (Integer, nullable), compliancePackStatus (JSONB via @JdbcTypeCode). Add domain method `recordCompliancePackApplication(String packId, String version)`. Pattern: follow existing templatePackStatus field on OrgSettings. |
| 100.4 | Add customerType to CustomerService create flow | 100A | | Modify `customer/CustomerService.java` and `customer/CreateCustomerRequest` DTO. Add optional `customerType` field (default INDIVIDUAL). Customer constructor accepts customerType. |
| 100.5 | Add lifecycle_status to customer list/detail DTOs | 100A | | Modify customer response DTOs to include lifecycleStatus, customerType, lifecycleStatusChangedAt. Modify customer list endpoint to support `?lifecycleStatus=ACTIVE` query parameter filtering. |
| 100.6 | Add Customer entity validation tests | 100A | | `customer/CustomerLifecycleEntityTest.java` (~5 tests): transitionLifecycleStatus sets all fields, anonymize replaces PII, default lifecycle is PROSPECT, customerType defaults to INDIVIDUAL, offboardedAt set on OFFBOARDED transition. Pattern: unit test. |
| 100.7 | Verify V29 migration runs cleanly | 100A | | Run `./mvnw clean test -q` and verify no migration errors. Check all tables created, Customer backfill applied (existing ACTIVE → lifecycle ACTIVE, ARCHIVED → OFFBOARDED). |
| 100.8 | Create CustomerLifecycleService | 100B | | `compliance/CustomerLifecycleService.java`. @Service. Method: `transition(UUID customerId, String targetStatus, String notes, UUID actorId)`. Validates state machine (PROSPECT→ONBOARDING, ONBOARDING→ACTIVE/PROSPECT, ACTIVE→DORMANT/OFFBOARDED, DORMANT→ACTIVE/OFFBOARDED, OFFBOARDED→ACTIVE). Checks guards (ONBOARDING→ACTIVE requires all checklists complete — stub for now, returns true). Sets offboardedAt on OFFBOARDED, clears on reactivation. Publishes audit event CUSTOMER_STATUS_CHANGED. Publishes CustomerStatusChangedEvent. Returns updated customer. Pattern: follow architecture doc Section 14.3.1 code. |
| 100.9 | Create CustomerLifecycleGuard | 100B | | `compliance/CustomerLifecycleGuard.java`. @Component. Method: `requireActionPermitted(Customer customer, LifecycleAction action)`. Validates action × status matrix per architecture doc Section 14.3.1 table. Throws InvalidStateException with descriptive message. Actions: CREATE_PROJECT, CREATE_TASK, CREATE_INVOICE, CREATE_TIME_ENTRY, CREATE_DOCUMENT, CREATE_COMMENT (always allowed). |
| 100.10 | Create LifecycleAction enum | 100B | | `compliance/LifecycleAction.java`. Enum: CREATE_PROJECT, CREATE_TASK, CREATE_INVOICE, CREATE_TIME_ENTRY, CREATE_DOCUMENT, CREATE_COMMENT. Each has a `label()` method for error messages. |
| 100.11 | Create CustomerStatusChangedEvent | 100B | | `compliance/CustomerStatusChangedEvent.java`. ApplicationEvent subclass. Fields: customerId, oldStatus, newStatus. Published by CustomerLifecycleService. Consumed by existing notification handler pattern. |
| 100.12 | Add lifecycle endpoints to CustomerController | 100B | | Modify `customer/CustomerController.java`. Add: POST `/api/customers/{id}/transition` (body: targetStatus, notes), GET `/api/customers/{id}/lifecycle` (returns audit event history for CUSTOMER_STATUS_CHANGED), POST `/api/customers/dormancy-check`. All @PreAuthorize admin/owner. DTOs: TransitionRequest(targetStatus, notes), TransitionResponse(id, name, lifecycleStatus, changedAt, changedBy, checklistsInstantiated), DormancyCheckResponse(thresholdDays, candidates[]). |
| 100.13 | Implement dormancy detection | 100B | | Add method to `compliance/CustomerLifecycleService.java`: `runDormancyCheck()`. Native query finds ACTIVE customers with no linked activity in last N days (configurable via OrgSettings.dormancyThresholdDays, default 90). Activity = time entries, task updates, documents, invoices, comments on customer-linked items. SQL in architecture doc Section 14.3.7. Returns DormancyCandidate list (customerId, name, lastActivityDate, daysSinceActivity). |
| 100.14 | Integrate guard in existing services | 100B | | Modify `project/ProjectService.java` (CREATE_PROJECT when customer linked), `invoice/InvoiceService.java` (CREATE_INVOICE), `timeentry/TimeEntryService.java` (CREATE_TIME_ENTRY when project has customer), `document/DocumentService.java` (CREATE_DOCUMENT when customer scope). Each calls `guard.requireActionPermitted(customer, action)` before proceeding. Only applies when the operation is customer-linked — operations without a customer are unaffected. |
| 100.15 | Add CustomerLifecycleService integration tests | 100B | | `compliance/CustomerLifecycleServiceTest.java` (~12 tests): valid transitions (PROSPECT→ONBOARDING, ONBOARDING→ACTIVE, ACTIVE→DORMANT, DORMANT→ACTIVE, ACTIVE→OFFBOARDED, OFFBOARDED→ACTIVE), invalid transitions (PROSPECT→ACTIVE, ONBOARDING→DORMANT) return 409, offboardedAt set/cleared correctly, audit event published, ONBOARDING→ACTIVE guard stub works. Pattern: @SpringBootTest with test tenant. |
| 100.16 | Add lifecycle controller and guard integration tests | 100B | | `compliance/CustomerLifecycleControllerTest.java` (~8 tests): POST transition endpoint valid/invalid, GET lifecycle history returns audit events, POST dormancy-check returns candidates, member 403 on transition. `compliance/CustomerLifecycleGuardTest.java` (~5 tests): CREATE_INVOICE blocked for PROSPECT/ONBOARDING/OFFBOARDED, allowed for ACTIVE/DORMANT, CREATE_COMMENT always allowed. Pattern: MockMvc + unit test. |

### Key Files

**Slice 100A — Create:**
- `backend/src/main/resources/db/migration/tenant/V29__customer_compliance_lifecycle.sql`
- `backend/src/test/java/.../customer/CustomerLifecycleEntityTest.java`

**Slice 100A — Modify:**
- `backend/src/main/java/.../customer/Customer.java` — Add lifecycle fields + domain methods
- `backend/src/main/java/.../orgsettings/OrgSettings.java` — Add compliance config fields
- `backend/src/main/java/.../customer/CustomerService.java` — Accept customerType
- Customer DTOs — Add lifecycle fields

**Slice 100B — Create:**
- `backend/src/main/java/.../compliance/CustomerLifecycleService.java`
- `backend/src/main/java/.../compliance/CustomerLifecycleGuard.java`
- `backend/src/main/java/.../compliance/LifecycleAction.java`
- `backend/src/main/java/.../compliance/CustomerStatusChangedEvent.java`
- `backend/src/test/java/.../compliance/CustomerLifecycleServiceTest.java`
- `backend/src/test/java/.../compliance/CustomerLifecycleControllerTest.java`
- `backend/src/test/java/.../compliance/CustomerLifecycleGuardTest.java`

**Slice 100B — Modify:**
- `backend/src/main/java/.../customer/CustomerController.java` — Add lifecycle endpoints
- `backend/src/main/java/.../project/ProjectService.java` — Guard integration
- `backend/src/main/java/.../invoice/InvoiceService.java` — Guard integration
- `backend/src/main/java/.../timeentry/TimeEntryService.java` — Guard integration
- `backend/src/main/java/.../document/DocumentService.java` — Guard integration

**Read for context:**
- `architecture/phase14-customer-compliance-lifecycle.md` Sections 14.2.1, 14.2.2, 14.2.10, 14.3.1, 14.3.7, 14.4.1, 14.6
- `adr/ADR-060-lifecycle-status-core-field.md`
- `backend/src/main/java/.../invoice/Invoice.java` — Post-Phase 13 entity pattern
- `backend/src/main/java/.../customer/Customer.java` — Existing entity to extend

### Architecture Decisions

- **ADR-060 (Lifecycle as core field)**: lifecycle_status is a column on Customer, not a custom field, because it drives platform behaviour (action gating, query filtering, dashboard metrics).
- **V29 includes ALL tables**: Single migration creates everything — even tables for Epics 101–105. This avoids migration ordering issues between parallel epic tracks.
- **Backfill strategy**: Existing ACTIVE customers → lifecycle ACTIVE. Existing ARCHIVED → OFFBOARDED. New customers default to PROSPECT.
- **Guard is opt-in per service**: Only customer-linked operations check the guard. Non-customer operations (e.g., creating an org-level project) skip it entirely.

---

## Epic 101: Checklist Template Engine

**Goal**: Implement the checklist template data model — templates with items, CRUD API, slug generation, dependency validation, and clone/reset functionality. Templates define what verification steps are required for a customer type.

**References**: Architecture doc Sections 14.2.3 (ChecklistTemplate), 14.2.4 (ChecklistTemplateItem), 14.4.2 (template endpoints), 14.7.3 (entity pattern), 14.7.4 (repository pattern). [ADR-061](../adr/ADR-061-checklist-first-class-entities.md).

**Dependencies**: Epic 100 (V29 migration creates checklist tables)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **101A** | 101.1–101.8 | ChecklistTemplate + ChecklistTemplateItem entities, repositories, ChecklistTemplateService CRUD (create with items, update, deactivate, list), ChecklistTemplateController with RBAC, DTOs. Integration tests (~15 tests). | **Done** (PR #210) |
| **101B** | 101.9–101.14 | Slug generation from template name, dependency validation (cycle detection), clone platform template, ordering support, advanced query methods. Integration tests (~12 tests). | **Done** (PR #211) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 101.1 | Create ChecklistTemplate entity | 101A | **Done** | `checklist/ChecklistTemplate.java`. 12 fields per architecture doc Section 14.2.3. @Entity, @Table(name = "checklist_templates"). No @Filter/@FilterDef (post-Phase 13). GenerationType.UUID. Domain methods: update(name, description, autoInstantiate), deactivate(). Protected no-arg + public constructor(name, slug, customerType, source, autoInstantiate). Pattern: architecture doc Section 14.7.3 code. |
| 101.2 | Create ChecklistTemplateItem entity | 101A | **Done** | `checklist/ChecklistTemplateItem.java`. 10 fields per architecture doc Section 14.2.4. @ManyToOne to ChecklistTemplate (templateId FK). Domain methods: update(name, description, sortOrder, required, requiresDocument, requiredDocumentLabel, dependsOnItemId). Protected no-arg + public constructor(templateId, name, sortOrder, required). |
| 101.3 | Create ChecklistTemplateRepository | 101A | **Done** | `checklist/ChecklistTemplateRepository.java`. JpaRepository<ChecklistTemplate, UUID>. Methods: findByActiveOrderBySortOrder(boolean), findByActiveAndAutoInstantiateAndCustomerTypeIn(boolean, boolean, List<String>), existsBySlug(String). |
| 101.4 | Create ChecklistTemplateItemRepository | 101A | **Done** | `checklist/ChecklistTemplateItemRepository.java`. JpaRepository<ChecklistTemplateItem, UUID>. Methods: findByTemplateIdOrderBySortOrder(UUID), deleteByTemplateId(UUID). |
| 101.5 | Create ChecklistTemplateService | 101A | **Done** | `checklist/ChecklistTemplateService.java`. @Service. CRUD: listActive(), getById(id) with items, create(CreateChecklistTemplateRequest), update(id, UpdateChecklistTemplateRequest), deactivate(id). Create saves template + items in single transaction. Update replaces items (delete old, insert new). @PreAuthorize admin/owner on mutations. |
| 101.6 | Create ChecklistTemplateController | 101A | **Done** | `checklist/ChecklistTemplateController.java`. @RestController, @RequestMapping("/api/checklist-templates"). Endpoints per architecture doc Section 14.4.2: GET list, GET /{id} with items, POST create, PUT /{id} update, DELETE /{id} soft-delete. DTOs: CreateChecklistTemplateRequest, UpdateChecklistTemplateRequest, ChecklistTemplateResponse, ChecklistTemplateItemResponse. |
| 101.7 | Add template CRUD integration tests | 101A | **Done** | `checklist/ChecklistTemplateServiceTest.java` (~8 tests): create template with items, list active only, get by id includes items, update replaces items, deactivate hides from list, slug uniqueness, customer type filter, admin-only mutation. Pattern: @SpringBootTest. |
| 101.8 | Add template controller integration tests | 101A | **Done** | `checklist/ChecklistTemplateControllerTest.java` (~7 tests): POST creates with items, GET list returns active, GET /{id} includes items, PUT updates items, DELETE soft-deletes, member 403, filter by customerType. Pattern: MockMvc. |
| 101.9 | Add slug generation | 101B | | Modify `checklist/ChecklistTemplateService.java`. Auto-generate slug from name if not provided: lowercase, replace spaces with hyphens, strip non-alphanumeric except hyphens, validate ^[a-z][a-z0-9-]*$. Append -2, -3 for uniqueness. |
| 101.10 | Add dependency validation | 101B | | Modify `checklist/ChecklistTemplateService.java`. Validate item dependencies during create/update: dependsOnItemId must reference an item in the same template. Detect circular dependencies (A depends on B depends on A). Throw 400 Bad Request on cycle. |
| 101.11 | Add clone template endpoint | 101B | | Modify `checklist/ChecklistTemplateService.java`. Method: `cloneTemplate(UUID templateId)`. Load template, copy all fields with source=ORG_CUSTOM, copy all items with new template FK, slug="{original-slug}-custom". Add POST /api/checklist-templates/{id}/clone to controller. |
| 101.12 | Add template ordering support | 101B | | Modify `checklist/ChecklistTemplateService.java`. Support explicit sortOrder on templates. Reorder items within a template when sortOrder changes. |
| 101.13 | Add slug generation tests | 101B | | `checklist/ChecklistTemplateSlugTest.java` (~4 tests): slug from name, special chars stripped, uniqueness suffix, regex validation. Pattern: unit test. |
| 101.14 | Add advanced template tests | 101B | | `checklist/ChecklistTemplateAdvancedTest.java` (~8 tests): clone creates ORG_CUSTOM copy, clone copies items, dependency cycle rejected, dependency to other template rejected, slug auto-increment on conflict, ordering persisted. Pattern: @SpringBootTest. |

### Key Files

**Slice 101A — Create:**
- `backend/src/main/java/.../checklist/ChecklistTemplate.java`
- `backend/src/main/java/.../checklist/ChecklistTemplateItem.java`
- `backend/src/main/java/.../checklist/ChecklistTemplateRepository.java`
- `backend/src/main/java/.../checklist/ChecklistTemplateItemRepository.java`
- `backend/src/main/java/.../checklist/ChecklistTemplateService.java`
- `backend/src/main/java/.../checklist/ChecklistTemplateController.java`
- `backend/src/test/java/.../checklist/ChecklistTemplateServiceTest.java`
- `backend/src/test/java/.../checklist/ChecklistTemplateControllerTest.java`

**Read for context:**
- `architecture/phase14-customer-compliance-lifecycle.md` Sections 14.2.3, 14.2.4, 14.4.2, 14.7.3, 14.7.4
- `adr/ADR-061-checklist-first-class-entities.md`
- `backend/src/main/java/.../template/DocumentTemplate.java` — Slug generation pattern
- `backend/src/main/java/.../template/DocumentTemplateService.java` — CRUD service pattern

### Architecture Decisions

- **ADR-061 (First-class entities)**: Four dedicated tables for checklists — per-item queryability, audit trails, document FK linking, template/instance separation.
- **Items stored with template**: ChecklistTemplateItems have FK to template. On update, old items are deleted and new ones inserted (replace strategy, simpler than diffing).
- **Slug generation**: Same pattern as DocumentTemplate — auto-generate from name, validate regex, suffix for uniqueness.

---

## Epic 102: Checklist Instance Engine

**Goal**: Implement checklist instantiation and item completion — creating instances from templates (snapshot pattern), completing items with dependency chain enforcement, document linking, auto-cascade to instance completion, and lifecycle advancement when all checklists are done.

**References**: Architecture doc Sections 14.2.5 (ChecklistInstance), 14.2.6 (ChecklistInstanceItem), 14.3.2 (instantiation), 14.3.3 (item completion), 14.4.3 (instance endpoints), 14.5.1–14.5.2 (sequence diagrams).

**Dependencies**: Epic 101 (checklist template entities)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **102A** | 102.1–102.7 | ChecklistInstance + ChecklistInstanceItem entities, repos, ChecklistInstanceService (create from template with snapshot, complete item, skip item, progress calculation). Unit + integration tests (~15 tests). | **Done** (PR #212) |
| **102B** | 102.8–102.14 | Dependency chain enforcement (blocked items, auto-unblock), document requirement validation, auto-cascade (all required complete → instance complete → lifecycle advance), reopen item, controller endpoints, audit events. Integration tests (~15 tests). | **Done** (PR #213) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 102.1 | Create ChecklistInstance entity | 102A | | `checklist/ChecklistInstance.java`. 8 fields per architecture doc Section 14.2.5. Domain methods: complete(UUID actorId), cancel(). Protected no-arg + public constructor(templateId, customerId, startedAt). |
| 102.2 | Create ChecklistInstanceItem entity | 102A | | `checklist/ChecklistInstanceItem.java`. 16 fields per architecture doc Section 14.2.6. Domain methods: complete(UUID actorId, String notes, UUID documentId), skip(String reason), reopen(), block(), unblock(). Status transitions: PENDING→IN_PROGRESS→COMPLETED, PENDING→SKIPPED, BLOCKED→PENDING (unblock). |
| 102.3 | Create ChecklistInstanceRepository | 102A | | `checklist/ChecklistInstanceRepository.java`. JpaRepository. Methods: findByCustomerId(UUID), existsByCustomerIdAndTemplateId(UUID, UUID), existsByCustomerIdAndStatusNot(UUID, String). |
| 102.4 | Create ChecklistInstanceItemRepository | 102A | | `checklist/ChecklistInstanceItemRepository.java`. JpaRepository. Methods: findByInstanceIdOrderBySortOrder(UUID), findByDependsOnItemIdAndStatus(UUID, String), existsByInstanceIdAndRequiredAndStatusNot(UUID, boolean, String). |
| 102.5 | Create ChecklistInstanceService — instantiation | 102A | | `checklist/ChecklistInstanceService.java`. @Service. Method: `createFromTemplate(UUID templateId, UUID customerId)`. Load template items, create instance, copy items as snapshot (name, description, sortOrder, required, requiresDocument, label). Two-pass: first create items, then resolve dependencies (map templateItemId → instanceItemId). Set BLOCKED status for items with dependencies. |
| 102.6 | Add item completion and skip | 102A | | Modify `checklist/ChecklistInstanceService.java`. Method: `completeItem(UUID itemId, String notes, UUID documentId, UUID actorId)`. Method: `skipItem(UUID itemId, String reason, UUID actorId)`. Basic completion without dependency checks (added in 102B). Progress calculation: `getProgress(UUID instanceId)` returns {completed, total, requiredCompleted, requiredTotal}. |
| 102.7 | Add instance service integration tests | 102A | | `checklist/ChecklistInstanceServiceTest.java` (~15 tests): create from template creates instance + items, item count matches template, snapshot captures field values, complete item sets status/actor/timestamp, skip item sets SKIPPED, progress calculation correct, idempotent instantiation (existsByCustomerIdAndTemplateId), items ordered by sortOrder. Pattern: @SpringBootTest. |
| 102.8 | Add dependency chain enforcement | 102B | | Modify `checklist/ChecklistInstanceService.java`. completeItem: check if item has dependsOnItemId → verify dependency is COMPLETED, else throw 409. After completion: find items blocked by this item (findByDependsOnItemIdAndStatus), set them to PENDING. |
| 102.9 | Add document requirement validation | 102B | | Modify `checklist/ChecklistInstanceService.java`. completeItem: if item.requiresDocument && documentId == null → throw 400 with message including requiredDocumentLabel. |
| 102.10 | Add auto-cascade completion | 102B | | Modify `checklist/ChecklistInstanceService.java`. After completeItem: check if all required items in instance are COMPLETED (existsByInstanceIdAndRequiredAndStatusNot). If yes → instance.complete(). Then check if all instances for customer are COMPLETED → call customerLifecycleService.transition(customerId, "ACTIVE"). |
| 102.11 | Add reopen item | 102B | | Modify `checklist/ChecklistInstanceService.java`. Method: `reopenItem(UUID itemId, UUID actorId)`. Sets item back to PENDING, clears completedAt/completedBy/notes/documentId. If instance was COMPLETED, revert to IN_PROGRESS. Admin/owner only. |
| 102.12 | Create ChecklistInstanceController | 102B | | `checklist/ChecklistInstanceController.java`. @RestController. Endpoints per architecture doc Section 14.4.3: GET /api/customers/{customerId}/checklists, GET /api/checklist-instances/{id}, POST /api/customers/{customerId}/checklists (manual instantiate), PUT /api/checklist-items/{id}/complete, PUT /api/checklist-items/{id}/skip, PUT /api/checklist-items/{id}/reopen. DTOs: ChecklistInstanceResponse (with items + progress), ChecklistItemResponse, CompleteItemRequest(notes, documentId), SkipItemRequest(reason). |
| 102.13 | Add audit events for checklist operations | 102B | | Modify `checklist/ChecklistInstanceService.java`. Audit events: CHECKLIST_ITEM_COMPLETED (itemName, completedBy, notes, documentId), CHECKLIST_ITEM_SKIPPED (itemName, reason), CHECKLIST_COMPLETED (instanceId, customerId), CHECKLIST_INSTANTIATED (templateName, customerId, instanceId). |
| 102.14 | Add dependency and cascade integration tests | 102B | | `checklist/ChecklistInstanceAdvancedTest.java` (~15 tests): blocked item cannot be completed (409), completing dependency unblocks dependent, document required but missing (400), document provided allows completion, all required complete → instance complete, all instances complete → lifecycle advances to ACTIVE, reopen reverts instance to IN_PROGRESS, cancel sets all items to cancelled state, controller endpoints return progress. Pattern: @SpringBootTest + MockMvc. |

### Key Files

**Slice 102A — Create:**
- `backend/src/main/java/.../checklist/ChecklistInstance.java`
- `backend/src/main/java/.../checklist/ChecklistInstanceItem.java`
- `backend/src/main/java/.../checklist/ChecklistInstanceRepository.java`
- `backend/src/main/java/.../checklist/ChecklistInstanceItemRepository.java`
- `backend/src/main/java/.../checklist/ChecklistInstanceService.java`
- `backend/src/test/java/.../checklist/ChecklistInstanceServiceTest.java`

**Slice 102B — Create:**
- `backend/src/main/java/.../checklist/ChecklistInstanceController.java`
- `backend/src/test/java/.../checklist/ChecklistInstanceAdvancedTest.java`

**Read for context:**
- `architecture/phase14-customer-compliance-lifecycle.md` Sections 14.2.5, 14.2.6, 14.3.2, 14.3.3, 14.4.3, 14.5.1, 14.5.2

### Architecture Decisions

- **Snapshot pattern**: Instance items copy template item fields at instantiation time — template changes do NOT affect in-progress checklists.
- **Two-pass dependency resolution**: First create all items (without deps), then resolve dependencies using templateItemId→instanceItemId mapping.
- **Auto-cascade**: Item complete → check instance → check all instances → lifecycle advance. Cascading eliminates manual lifecycle transitions after onboarding.

---

## Epic 103: Compliance Pack Seeding & Instantiation

**Goal**: Implement the compliance pack seeder (classpath JSON packs with checklist templates, field definitions, and retention policies), the auto-instantiation service (creates checklist instances when customer transitions to ONBOARDING), and integration with tenant provisioning.

**References**: Architecture doc Sections 14.3.4 (pack seeding), 14.3.2 (instantiation flow), 14.5.1 (sequence diagram). [ADR-063](../adr/ADR-063-compliance-packs-bundled-seed-data.md).

**Dependencies**: Epic 101 (checklist template entities for pack seeding)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **103A** | 103.1–103.6 | CompliancePackSeeder service, CompliancePackDefinition records, 3 shipped packs (generic-onboarding, sa-fica-individual, sa-fica-company), OrgSettings compliancePackStatus tracking. Integration tests (~10 tests). | **Done** (PR #214) |
| **103B** | 103.7–103.12 | ChecklistInstantiationService (auto-create instances on ONBOARDING transition), integration with CustomerLifecycleService side effects, cancel instances on ONBOARDING→PROSPECT, provisioning integration. Integration tests (~12 tests). | **Done** (PR #215) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 103.1 | Create CompliancePackDefinition records | 103A | | `compliance/CompliancePackDefinition.java` record. Fields: packId, name, description, version, jurisdiction, customerType, checklistTemplate (CompliancePackChecklistTemplate), fieldDefinitions (List, nullable), retentionOverrides (List, nullable). Nested records: CompliancePackChecklistTemplate(name, slug, autoInstantiate, items[]), CompliancePackItem(name, description, sortOrder, required, requiresDocument, requiredDocumentLabel, dependsOnItemKey). Pattern: follow TemplatePackDefinition (Phase 12). |
| 103.2 | Create CompliancePackSeeder | 103A | | `compliance/CompliancePackSeeder.java`. @Service. Method: `seedPacksForTenant()`. Scan classpath:compliance-packs/*/pack.json. For each pack: check OrgSettings.compliancePackStatus for idempotency, create ChecklistTemplate with source=PLATFORM + items, delegate fieldDefinitions to FieldPackSeeder if present, seed retentionOverrides into retention_policies if present, record pack application. Pattern: follow TemplatePackSeeder (Phase 12) exactly. Architecture doc Section 14.3.4. |
| 103.3 | Create generic-onboarding pack | 103A | | `resources/compliance-packs/generic-onboarding/pack.json`. 4 items: "Confirm client engagement", "Verify contact details", "Confirm billing arrangements", "Upload signed engagement letter" (requiresDocument: true). customerType: ANY, autoInstantiate: true. No fieldDefinitions, no retentionOverrides. |
| 103.4 | Create sa-fica-individual pack | 103A | | `resources/compliance-packs/sa-fica-individual/pack.json`. 5 items per architecture doc Section 14.3.4 example. customerType: INDIVIDUAL, autoInstantiate: false. Includes fieldDefinitions (sa_id_number, passport_number, risk_rating) and retentionOverrides (CUSTOMER 5 years). |
| 103.5 | Create sa-fica-company pack | 103A | | `resources/compliance-packs/sa-fica-company/pack.json`. 6 items: "Verify company registration", "Verify directors/members", "Verify registered address", "Confirm source of funds", "Perform risk assessment" (depends on verify-registration), "FICA compliance sign-off" (depends on risk-assessment). customerType: COMPANY, autoInstantiate: false. Includes fieldDefinitions (company_registration_number, entity_type). |
| 103.6 | Add pack seeder integration tests | 103A | | `compliance/CompliancePackSeederTest.java` (~10 tests): seed creates templates from all 3 packs, generic pack has 4 items, FICA individual has 5 items with field cross-seeding, idempotency (second call creates nothing), compliancePackStatus updated, template source=PLATFORM, FICA items have correct dependencies, retentionOverrides create RetentionPolicy records. Pattern: @SpringBootTest. |
| 103.7 | Create ChecklistInstantiationService | 103B | | `compliance/ChecklistInstantiationService.java`. @Service. Method: `instantiateForCustomer(Customer customer)`. Find active templates matching customer type with autoInstantiate=true. For each, delegate to ChecklistInstanceService.createFromTemplate(). Skip if already instantiated (idempotency). Return list of created instances. Architecture doc Section 14.3.2. |
| 103.8 | Add cancel active instances | 103B | | Modify `compliance/ChecklistInstantiationService.java`. Method: `cancelActiveInstances(UUID customerId)`. Find all IN_PROGRESS instances for customer, set status to CANCELLED. Called when ONBOARDING→PROSPECT transition occurs. |
| 103.9 | Integrate instantiation with lifecycle service | 103B | | Modify `compliance/CustomerLifecycleService.java`. On PROSPECT→ONBOARDING: call `checklistInstantiationService.instantiateForCustomer(customer)`, include count in response (checklistsInstantiated). On ONBOARDING→PROSPECT: call `checklistInstantiationService.cancelActiveInstances(customerId)`. Update ONBOARDING→ACTIVE guard: check `!instanceRepository.existsByCustomerIdAndStatusNot(customerId, "COMPLETED")`. |
| 103.10 | Integrate seeder with tenant provisioning | 103B | | Modify `provisioning/TenantProvisioningService.java`. Call `compliancePackSeeder.seedPacksForTenant()` after Flyway migrations, alongside existing fieldPackSeeder and templatePackSeeder calls. |
| 103.11 | Add instantiation integration tests | 103B | | `compliance/ChecklistInstantiationServiceTest.java` (~8 tests): ONBOARDING transition creates instances from matching templates, ANY template matches all customer types, INDIVIDUAL only matches INDIVIDUAL, idempotent (re-transition doesn't duplicate), cancel sets instances to CANCELLED, ONBOARDING→ACTIVE blocked when instances IN_PROGRESS, ONBOARDING→ACTIVE allowed when all COMPLETED. Pattern: @SpringBootTest. |
| 103.12 | Add provisioning integration test | 103B | | `compliance/ComplianceProvisioningTest.java` (~4 tests): new tenant has generic-onboarding template seeded, FICA packs seeded with field definitions, compliance_pack_status tracks all 3 packs, creating customer and starting onboarding instantiates generic checklist. Pattern: @SpringBootTest with full provisioning. |

### Key Files

**Slice 103A — Create:**
- `backend/src/main/java/.../compliance/CompliancePackDefinition.java`
- `backend/src/main/java/.../compliance/CompliancePackSeeder.java`
- `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`
- `backend/src/main/resources/compliance-packs/sa-fica-individual/pack.json`
- `backend/src/main/resources/compliance-packs/sa-fica-company/pack.json`
- `backend/src/test/java/.../compliance/CompliancePackSeederTest.java`

**Slice 103B — Create:**
- `backend/src/main/java/.../compliance/ChecklistInstantiationService.java`
- `backend/src/test/java/.../compliance/ChecklistInstantiationServiceTest.java`
- `backend/src/test/java/.../compliance/ComplianceProvisioningTest.java`

**Slice 103B — Modify:**
- `backend/src/main/java/.../compliance/CustomerLifecycleService.java` — Wire instantiation/cancel
- `backend/src/main/java/.../provisioning/TenantProvisioningService.java` — Call pack seeder

**Read for context:**
- `architecture/phase14-customer-compliance-lifecycle.md` Sections 14.3.2, 14.3.4
- `adr/ADR-063-compliance-packs-bundled-seed-data.md`
- `backend/src/main/java/.../template/TemplatePackSeeder.java` — Pack seeder pattern to follow

### Architecture Decisions

- **ADR-063 (Packs as seed data)**: Classpath resources seeded during provisioning. No runtime marketplace.
- **Cross-seeding**: Compliance packs can include fieldDefinitions (delegated to FieldPackSeeder) and retentionOverrides. Single pack ships a complete compliance kit.
- **Generic pack is auto-instantiate**: The generic-onboarding pack creates a checklist automatically when any customer enters ONBOARDING. FICA packs are opt-in (autoInstantiate: false) — admin activates them manually.

---

## Epic 104: Data Subject Requests

**Goal**: Implement the data subject request lifecycle (ACCESS/DELETION/CORRECTION/OBJECTION), data export (ZIP with JSON+CSV), data anonymization (PII replacement preserving financial records), and request deadline tracking with notifications.

**References**: Architecture doc Sections 14.2.7 (DataSubjectRequest), 14.3.5 (request lifecycle), 14.4.4 (endpoints), 14.5.3 (sequence diagram). [ADR-062](../adr/ADR-062-anonymization-over-hard-deletion.md).

**Dependencies**: Epic 100 (V29 migration, lifecycle service for offboarding after anonymization)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **104A** | 104.1–104.8 | DataSubjectRequest entity + repo, DataSubjectRequestService (create, status transitions, deadline calculation), DataExportService (ZIP generation with customer data, S3 upload), DataRequestController CRUD + export endpoints. Integration tests (~15 tests). | |
| **104B** | 104.9–104.14 | DataAnonymizationService (PII clearing, S3 document deletion, comment redaction, portal contact anonymization, financial records preserved), execute-deletion endpoint, deadline notification events, Comment.redact() and PortalContact.anonymize() domain methods. Integration tests (~12 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 104.1 | Create DataSubjectRequest entity | 104A | | `datarequest/DataSubjectRequest.java`. 14 fields per architecture doc Section 14.2.7. Domain methods: startProcessing(UUID actorId), complete(UUID actorId), reject(String reason, UUID actorId). Status transitions: RECEIVED→IN_PROGRESS→COMPLETED/REJECTED. Deadline is LocalDate. Pattern: post-Phase 13 entity. |
| 104.2 | Create DataSubjectRequestRepository | 104A | | `datarequest/DataSubjectRequestRepository.java`. JpaRepository. Methods: findByStatus(String), findByCustomerId(UUID), findByStatusInAndDeadlineBefore(List<String>, LocalDate), countByStatusIn(List<String>). |
| 104.3 | Create DataSubjectRequestService | 104A | | `datarequest/DataSubjectRequestService.java`. @Service. Methods: createRequest(customerId, requestType, description, actorId) — calculates deadline from OrgSettings.dataRequestDeadlineDays. startProcessing(id, actorId). completeRequest(id, actorId). rejectRequest(id, reason, actorId). listAll(), listByStatus(status), getById(id). Audit events: DATA_REQUEST_CREATED, DATA_REQUEST_COMPLETED, DATA_REQUEST_REJECTED. Admin/owner only. |
| 104.4 | Create DataExportService | 104A | | `datarequest/DataExportService.java`. @Service. Method: `generateExport(UUID requestId, UUID actorId)`. Collects: customer profile, projects (via customer_projects), documents (by customerId), invoices (by customerId), time entries (billable, by customer's projects), comments (portal-visible). Generates ZIP with data.json + summary.csv. Uploads to S3 at org/{tenantId}/exports/{requestId}.zip. Sets exportFileKey on request. Audit event: DATA_EXPORT_GENERATED. Architecture doc Section 14.3.5. |
| 104.5 | Create DataRequestController | 104A | | `datarequest/DataRequestController.java`. @RestController, @RequestMapping("/api/data-requests"). Endpoints per architecture doc Section 14.4.4: GET list (filter by status), GET /{id}, POST create, PUT /{id}/status, POST /{id}/export, GET /{id}/export/download (S3 pre-signed URL redirect). All @PreAuthorize admin/owner. |
| 104.6 | Add request CRUD integration tests | 104A | | `datarequest/DataSubjectRequestServiceTest.java` (~8 tests): create sets deadline from OrgSettings, status transitions valid, invalid transitions rejected, list by status, audit events published, deadline calculated correctly with custom OrgSettings value. Pattern: @SpringBootTest. |
| 104.7 | Add export integration tests | 104A | | `datarequest/DataExportServiceTest.java` (~4 tests): export generates ZIP with customer data, ZIP contains data.json and summary.csv, S3 upload called, exportFileKey set on request. Pattern: @SpringBootTest with S3 mock. |
| 104.8 | Add controller integration tests | 104A | | `datarequest/DataRequestControllerTest.java` (~3 tests): POST creates request, GET /{id}/export/download returns pre-signed URL, member 403. Pattern: MockMvc. |
| 104.9 | Create DataAnonymizationService | 104B | | `datarequest/DataAnonymizationService.java`. @Service. Method: `executeAnonymization(UUID requestId, String confirmCustomerName, UUID actorId)`. Verification: customer name must match confirmCustomerName exactly. Steps per architecture doc Section 14.3.5: (1) customer.anonymize(), (2) delete customer-scoped documents from S3 + DB, (3) redact portal-visible comments, (4) anonymize portal contacts, (5) transition lifecycle to OFFBOARDED, (6) complete request. Audit event: DATA_DELETION_EXECUTED with counts. |
| 104.10 | Add Comment.redact() domain method | 104B | | Modify `comment/Comment.java`. Method: `redact(String replacement)` — replaces content with replacement string, sets updatedAt. |
| 104.11 | Add PortalContact.anonymize() domain method | 104B | | Modify `portal/PortalContact.java`. Method: `anonymize(String replacementName)` — replaces name, email, phone with anonymized values. |
| 104.12 | Add execute-deletion endpoint | 104B | | Modify `datarequest/DataRequestController.java`. POST `/api/data-requests/{id}/execute-deletion`. Body: {confirmCustomerName}. Calls anonymization service. Returns anonymization summary (documentsDeleted, commentsRedacted, portalContactsAnonymized, financialRecordsPreserved). 400 if name doesn't match, 409 if request type not DELETION or not IN_PROGRESS. |
| 104.13 | Add deadline notification events | 104B | | Modify `datarequest/DataSubjectRequestService.java`. Method: `checkDeadlines()`. Find requests with deadline within 7 days → publish DATA_REQUEST_DEADLINE_APPROACHING. Find requests past deadline → publish DATA_REQUEST_OVERDUE. Called from a @Scheduled method or admin-triggered endpoint. |
| 104.14 | Add anonymization integration tests | 104B | | `datarequest/DataAnonymizationServiceTest.java` (~12 tests): customer PII anonymized, documents deleted from S3, documents deleted from DB, comments redacted (portal-visible only), internal comments preserved, portal contacts anonymized, invoices preserved, time entries preserved, customer transitioned to OFFBOARDED, name mismatch rejected (400), wrong request type rejected (409), audit event records counts. Pattern: @SpringBootTest. |

### Key Files

**Slice 104A — Create:**
- `backend/src/main/java/.../datarequest/DataSubjectRequest.java`
- `backend/src/main/java/.../datarequest/DataSubjectRequestRepository.java`
- `backend/src/main/java/.../datarequest/DataSubjectRequestService.java`
- `backend/src/main/java/.../datarequest/DataExportService.java`
- `backend/src/main/java/.../datarequest/DataRequestController.java`
- `backend/src/test/java/.../datarequest/DataSubjectRequestServiceTest.java`
- `backend/src/test/java/.../datarequest/DataExportServiceTest.java`
- `backend/src/test/java/.../datarequest/DataRequestControllerTest.java`

**Slice 104B — Create:**
- `backend/src/main/java/.../datarequest/DataAnonymizationService.java`
- `backend/src/test/java/.../datarequest/DataAnonymizationServiceTest.java`

**Slice 104B — Modify:**
- `backend/src/main/java/.../comment/Comment.java` — Add redact() method
- `backend/src/main/java/.../portal/PortalContact.java` — Add anonymize() method
- `backend/src/main/java/.../datarequest/DataRequestController.java` — Add execute-deletion endpoint

**Read for context:**
- `architecture/phase14-customer-compliance-lifecycle.md` Sections 14.2.7, 14.3.5, 14.4.4, 14.5.3
- `adr/ADR-062-anonymization-over-hard-deletion.md`

### Architecture Decisions

- **ADR-062 (Anonymization over hard deletion)**: PII replaced with placeholders. Financial records (invoices, time entries, billing rates) preserved for legal obligations.
- **Confirmation check**: Exact customer name match required before anonymization — prevents accidental data destruction.
- **Export scope**: Only platform data. No external system discovery.

---

## Epic 105: Retention & Dormancy

**Goal**: Implement retention policies (configurable periods per record type), retention check execution (flag records past retention), purge workflow, and default retention policy seeding via compliance packs.

**References**: Architecture doc Sections 14.2.8 (RetentionPolicy), 14.3.6 (retention check), 14.4.5 (endpoints).

**Dependencies**: Epic 100 (V29 migration, lifecycle status for trigger evaluation)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **105A** | 105.1–105.6 | RetentionPolicy entity + repo, RetentionService (evaluate policies, flag records), RetentionCheckResult, dormancy threshold from OrgSettings. Integration tests (~10 tests). | |
| **105B** | 105.7–105.11 | RetentionController (CRUD, check, purge endpoints), purge execution, default retention policy seeding, provisioning integration. Integration tests (~10 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 105.1 | Create RetentionPolicy entity | 105A | | `retention/RetentionPolicy.java`. 7 fields per architecture doc Section 14.2.8. Domain methods: update(retentionDays, action), deactivate(). recordType: CUSTOMER, PROJECT, DOCUMENT, TIME_ENTRY, INVOICE, AUDIT_EVENT, COMMENT. triggerEvent: CUSTOMER_OFFBOARDED, PROJECT_COMPLETED, RECORD_CREATED. action: FLAG, ANONYMIZE. |
| 105.2 | Create RetentionPolicyRepository | 105A | | `retention/RetentionPolicyRepository.java`. JpaRepository. Methods: findByActive(boolean), existsByRecordTypeAndTriggerEvent(String, String). |
| 105.3 | Create RetentionCheckResult | 105A | | `retention/RetentionCheckResult.java`. Record or class. Fields: checkedAt (Instant), flagged (Map<String, FlaggedRecords>). FlaggedRecords: count, action, recordIds. Method: addFlagged(recordType, action, ids). |
| 105.4 | Create RetentionService | 105A | | `retention/RetentionService.java`. @Service. Method: `runCheck()`. Load active policies. For each, calculate cutoff date. Query for records past retention: CUSTOMER (offboarded_at < cutoff), AUDIT_EVENT (occurred_at < cutoff), DOCUMENT (created_at < cutoff for offboarded customers). Return RetentionCheckResult. Audit event: RETENTION_CHECK_EXECUTED. Architecture doc Section 14.3.6. |
| 105.5 | Create RetentionPolicyService | 105A | | `retention/RetentionPolicyService.java`. @Service. CRUD: listActive(), create(recordType, retentionDays, triggerEvent, action), update(id, retentionDays, action), delete(id). Uniqueness check on (recordType, triggerEvent). Admin/owner only. |
| 105.6 | Add retention service integration tests | 105A | | `retention/RetentionServiceTest.java` (~10 tests): check with no policies returns empty, CUSTOMER policy flags offboarded customers past retention, AUDIT_EVENT policy flags old events, active only (inactive ignored), cutoff calculation correct, audit event published, CRUD creates/updates/deletes policies, uniqueness enforced on (recordType, triggerEvent). Pattern: @SpringBootTest. |
| 105.7 | Create RetentionController | 105B | | `retention/RetentionController.java`. @RestController, @RequestMapping("/api/retention-policies"). Endpoints per architecture doc Section 14.4.5: GET list, POST create, PUT /{id}, DELETE /{id}, POST /check, POST /purge. All @PreAuthorize admin/owner. |
| 105.8 | Add purge execution | 105B | | Modify `retention/RetentionService.java`. Method: `executePurge(String recordType, List<UUID> recordIds)`. For CUSTOMER records: delegate to DataAnonymizationService. For AUDIT_EVENT/COMMENT: hard delete. For DOCUMENT: delete from S3 + DB. Individual audit event per purge: RETENTION_PURGE_EXECUTED. |
| 105.9 | Add default retention policy seeding | 105B | | Modify `compliance/CompliancePackSeeder.java`. When pack includes retentionOverrides, seed RetentionPolicy records. Default from FICA packs: CUSTOMER 5 years, AUDIT_EVENT 7 years. Skip if policy already exists for (recordType, triggerEvent). |
| 105.10 | Add provisioning integration | 105B | | Verify that CompliancePackSeeder (already integrated in 103B) seeds retention policies during tenant provisioning alongside checklist templates. |
| 105.11 | Add retention controller and purge tests | 105B | | `retention/RetentionControllerTest.java` (~10 tests): GET returns active policies, POST creates policy, POST /check returns flagged records, POST /purge deletes audit events, POST /purge anonymizes customer records via DataAnonymizationService, member 403, duplicate policy rejected (409), delete removes policy. Pattern: MockMvc + @SpringBootTest. |

### Key Files

**Slice 105A — Create:**
- `backend/src/main/java/.../retention/RetentionPolicy.java`
- `backend/src/main/java/.../retention/RetentionPolicyRepository.java`
- `backend/src/main/java/.../retention/RetentionCheckResult.java`
- `backend/src/main/java/.../retention/RetentionService.java`
- `backend/src/main/java/.../retention/RetentionPolicyService.java`
- `backend/src/test/java/.../retention/RetentionServiceTest.java`

**Slice 105B — Create:**
- `backend/src/main/java/.../retention/RetentionController.java`
- `backend/src/test/java/.../retention/RetentionControllerTest.java`

**Slice 105B — Modify:**
- `backend/src/main/java/.../compliance/CompliancePackSeeder.java` — Seed retention policies from packs

**Read for context:**
- `architecture/phase14-customer-compliance-lifecycle.md` Sections 14.2.8, 14.3.6, 14.4.5

### Architecture Decisions

- **FLAG is default action**: Records are flagged for admin review, not auto-deleted. ANONYMIZE available for aggressive retention posture.
- **Per-purge audit**: Each individual purge action is audited separately — full traceability of what was deleted and when.
- **Retention seeding**: Via compliance packs (same mechanism as checklist templates). No separate retention migration.

---

## Epic 106: Lifecycle & Checklist Frontend

**Goal**: Build the frontend for customer lifecycle management — lifecycle status badges, transition dropdowns, confirmation dialogs, and the Onboarding tab with checklist panel showing item completion, document uploads, and progress tracking.

**References**: Architecture doc Section 14.7.2 (frontend routes and components).

**Dependencies**: Epics 102 (checklist instance engine), 103 (pack seeding + instantiation)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **106A** | 106.1–106.8 | LifecycleStatusBadge, LifecycleTransitionDropdown, customer list lifecycle column + filter, customer detail lifecycle section, transition confirmation dialogs, server actions for lifecycle API calls. Frontend tests (~10 tests). | |
| **106B** | 106.9–106.15 | ChecklistInstancePanel (Onboarding tab on customer detail), ChecklistInstanceItemRow (complete/skip/reopen with document upload link), progress bar, template selector for manual instantiation, checklist API client. Frontend tests (~10 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 106.1 | Create LifecycleStatusBadge component | 106A | | `components/compliance/LifecycleStatusBadge.tsx`. Props: status string. Color mapping: PROSPECT=grey, ONBOARDING=blue, ACTIVE=green, DORMANT=amber, OFFBOARDED=red. Uses existing Badge variant pattern from Shadcn. |
| 106.2 | Create LifecycleTransitionDropdown component | 106A | | `components/compliance/LifecycleTransitionDropdown.tsx`. Props: currentStatus, customerId, onTransition callback. Shows valid transitions based on current status. DropdownMenu with items: "Start Onboarding" (PROSPECT), "Activate" (ONBOARDING, only if guard allows), "Cancel Onboarding" (ONBOARDING→PROSPECT), "Mark as Dormant" (ACTIVE), "Offboard Customer" (ACTIVE/DORMANT), "Reactivate" (DORMANT/OFFBOARDED). Each triggers confirmation dialog. |
| 106.3 | Create transition confirmation dialogs | 106A | | `components/compliance/TransitionConfirmDialog.tsx`. AlertDialog with title, description of consequences. "Offboard Customer" shows destructive warning (read-only, blocks new work). "Reactivate from Offboarded" requires notes input (audit justification). "Cancel Onboarding" warns checklists will be cancelled. Pattern: follow DeleteProjectDialog for destructive confirmation. |
| 106.4 | Add lifecycle column to customer list | 106A | | Modify `app/(app)/org/[slug]/customers/page.tsx`. Add "Status" column with LifecycleStatusBadge. Add `?lifecycleStatus=` query param filter (dropdown or tabs: All, Prospect, Onboarding, Active, Dormant, Offboarded). |
| 106.5 | Add lifecycle section to customer detail | 106A | | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx`. Add LifecycleStatusBadge next to customer name. Add LifecycleTransitionDropdown in actions area. Show lifecycleStatusChangedAt as "Since {date}" subtitle. |
| 106.6 | Create lifecycle server actions | 106A | | `app/(app)/org/[slug]/customers/[id]/lifecycle-actions.ts`. `transitionCustomerLifecycle(customerId, targetStatus, notes)` — POST /api/customers/{id}/transition. `getLifecycleHistory(customerId)` — GET /api/customers/{id}/lifecycle. Revalidate customer page on success. |
| 106.7 | Add compliance API client functions | 106A | | `lib/compliance-api.ts`. Functions: transitionLifecycle(id, targetStatus, notes), getLifecycleHistory(id), runDormancyCheck(). Pattern: follow existing API client files. |
| 106.8 | Add lifecycle frontend tests | 106A | | `__tests__/compliance/LifecycleStatusBadge.test.tsx` (~4 tests): renders correct color per status, displays status text. `__tests__/compliance/LifecycleTransitionDropdown.test.tsx` (~6 tests): shows valid transitions for each status, PROSPECT shows "Start Onboarding", OFFBOARDED shows "Reactivate", calls onTransition callback. Pattern: @testing-library/react, afterEach cleanup(). |
| 106.9 | Create ChecklistInstancePanel component | 106B | | `components/compliance/ChecklistInstancePanel.tsx`. Props: customerId, instances[]. Rendered as "Onboarding" tab on customer detail page. Shows each checklist instance: template name, status badge, progress bar (completed/total), list of ChecklistInstanceItemRow. Collapsed when complete. "Manually Add Checklist" button (admin only) with template selector. |
| 106.10 | Create ChecklistInstanceItemRow component | 106B | | `components/compliance/ChecklistInstanceItemRow.tsx`. Props: item, onComplete, onSkip, onReopen. Shows: checkbox (for complete), name, status badge, document upload link (if requiresDocument), dependency indicator (blocked by {dependency name}), completion metadata (completedBy, completedAt, notes). Actions: "Mark Complete" (opens notes + document upload), "Skip" (optional items only, with reason), "Reopen" (admin only, completed items). |
| 106.11 | Create checklist progress bar | 106B | | `components/compliance/ChecklistProgressBar.tsx`. Props: completed, total, requiredCompleted, requiredTotal. Shows progress bar with "3/5 completed (2/4 required)" text. Green when all required done. |
| 106.12 | Add Onboarding tab to customer detail | 106B | | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx`. Add "Onboarding" tab (visible when customer has ONBOARDING status or has any checklist instances). Tab content: ChecklistInstancePanel. Fetch checklist data via GET /api/customers/{customerId}/checklists. |
| 106.13 | Create checklist server actions | 106B | | `app/(app)/org/[slug]/customers/[id]/checklist-actions.ts`. `getCustomerChecklists(customerId)` — GET /api/customers/{customerId}/checklists. `completeChecklistItem(itemId, notes, documentId)` — PUT /api/checklist-items/{id}/complete. `skipChecklistItem(itemId, reason)` — PUT /api/checklist-items/{id}/skip. `reopenChecklistItem(itemId)` — PUT /api/checklist-items/{id}/reopen. `instantiateChecklist(customerId, templateId)` — POST /api/customers/{customerId}/checklists. |
| 106.14 | Add checklist API client functions | 106B | | `lib/checklist-api.ts`. Functions: getCustomerChecklists(customerId), getChecklistInstance(id), completeItem(id, body), skipItem(id, body), reopenItem(id), instantiateChecklist(customerId, templateId), getTemplates(). |
| 106.15 | Add checklist frontend tests | 106B | | `__tests__/compliance/ChecklistInstancePanel.test.tsx` (~5 tests): renders instances with progress, completed instances collapsed, manual add button visible for admin. `__tests__/compliance/ChecklistInstanceItemRow.test.tsx` (~5 tests): shows blocked indicator, complete button calls action, skip available for optional items, document upload link shown when required, reopen visible for admin only. Pattern: @testing-library/react. |

### Key Files

**Slice 106A — Create:**
- `frontend/components/compliance/LifecycleStatusBadge.tsx`
- `frontend/components/compliance/LifecycleTransitionDropdown.tsx`
- `frontend/components/compliance/TransitionConfirmDialog.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/lifecycle-actions.ts`
- `frontend/lib/compliance-api.ts`
- `frontend/__tests__/compliance/LifecycleStatusBadge.test.tsx`
- `frontend/__tests__/compliance/LifecycleTransitionDropdown.test.tsx`

**Slice 106A — Modify:**
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Lifecycle column + filter
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Lifecycle section

**Slice 106B — Create:**
- `frontend/components/compliance/ChecklistInstancePanel.tsx`
- `frontend/components/compliance/ChecklistInstanceItemRow.tsx`
- `frontend/components/compliance/ChecklistProgressBar.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/checklist-actions.ts`
- `frontend/lib/checklist-api.ts`
- `frontend/__tests__/compliance/ChecklistInstancePanel.test.tsx`
- `frontend/__tests__/compliance/ChecklistInstanceItemRow.test.tsx`

**Read for context:**
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Existing customer list
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Existing customer detail with tabs

### Architecture Decisions

- **Lifecycle badge colors**: Match severity: grey (neutral/new), blue (in progress), green (healthy), amber (warning), red (critical/ended).
- **Checklist as tab**: Onboarding data shown in a tab alongside existing tabs (Documents, Activity, etc.), not a separate page.
- **Transition dropdown**: Dynamically computed from current status — no impossible transitions shown.

---

## Epic 107: Data Requests & Settings Frontend

**Goal**: Build the frontend for data subject request management (list, create, process, export, delete) and compliance settings (retention policies, dormancy threshold, checklist template management).

**References**: Architecture doc Section 14.7.2 (frontend routes).

**Dependencies**: Epics 104 (data request backend), 105 (retention backend)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **107A** | 107.1–107.7 | Data request list page (/compliance/requests), CreateDataRequestDialog, request detail with status timeline, export download, DeletionConfirmDialog (type customer name). Frontend tests (~8 tests). | |
| **107B** | 107.8–107.14 | Compliance settings page (/settings/compliance), retention policy table (CRUD), "Run Retention Check" with results display, dormancy threshold + deadline days config, checklist template list in settings. Frontend tests (~7 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 107.1 | Create DataRequestListPage | 107A | | `app/(app)/org/[slug]/compliance/requests/page.tsx`. Server component. Table: customer name, request type badge, status badge, deadline (with countdown/overdue color), requestedAt, actions. Filter by status (tabs or dropdown). "New Request" button opens CreateDataRequestDialog. Link to detail page per row. Admin/owner only. Pattern: follow existing list pages (invoices, customers). |
| 107.2 | Create CreateDataRequestDialog | 107A | | `components/compliance/CreateDataRequestDialog.tsx`. Dialog form: customer selector (Command/combobox search), request type (ACCESS, DELETION, CORRECTION, OBJECTION), description (textarea). On submit: POST /api/data-requests. Revalidate list. Pattern: follow CreateInvoiceDialog pattern. |
| 107.3 | Create DataRequestDetailPage | 107A | | `app/(app)/org/[slug]/compliance/requests/[id]/page.tsx`. Server component. Header: customer name, request type, status badge, deadline. Status timeline (DataRequestTimeline component). Actions by status: RECEIVED → "Start Processing", IN_PROGRESS → "Complete" or "Reject" or "Generate Export" (ACCESS) or "Execute Deletion" (DELETION). Export download button (when exportFileKey exists). Notes section. |
| 107.4 | Create DataRequestTimeline component | 107A | | `components/compliance/DataRequestTimeline.tsx`. Props: request object with status history. Vertical timeline: RECEIVED (date), IN_PROGRESS (date, who), COMPLETED/REJECTED (date, who, reason if rejected). Pattern: follow simple timeline markup, not a full timeline library. |
| 107.5 | Create DeletionConfirmDialog | 107A | | `components/compliance/DeletionConfirmDialog.tsx`. AlertDialog. Destructive confirmation: shows consequences list (PII anonymized, documents deleted, comments redacted, financial records preserved). Input: "Type the customer name to confirm". Button disabled until name matches. On confirm: POST /api/data-requests/{id}/execute-deletion. Shows anonymization summary on success. Pattern: follow destructive confirmation pattern from DeleteProjectDialog. |
| 107.6 | Add data request server actions | 107A | | `app/(app)/org/[slug]/compliance/requests/actions.ts`. createDataRequest(customerId, requestType, description), updateRequestStatus(id, status, reason?), generateExport(id), executeDeletion(id, confirmCustomerName), getExportDownloadUrl(id). |
| 107.7 | Add data request frontend tests | 107A | | `__tests__/compliance/DataRequestListPage.test.tsx` (~4 tests): renders request rows, filter by status, deadline shows overdue in red. `__tests__/compliance/DeletionConfirmDialog.test.tsx` (~4 tests): button disabled until name matches, confirm calls action, shows consequences, empty input keeps button disabled. Pattern: @testing-library/react. |
| 107.8 | Create ComplianceSettingsPage | 107B | | `app/(app)/org/[slug]/settings/compliance/page.tsx`. Server component. Sections: (1) General — dormancy threshold days input, data request deadline days input. (2) Retention Policies — RetentionPolicyTable. (3) Compliance Packs — list of applied packs (from OrgSettings.compliancePackStatus). Admin/owner only. Pattern: follow existing settings pages. |
| 107.9 | Create RetentionPolicyTable component | 107B | | `components/compliance/RetentionPolicyTable.tsx`. Props: policies[], onSave, onDelete. Editable table: record type (select), trigger event (select), retention days (number input), action (FLAG/ANONYMIZE select), active toggle. Add row button. Delete button per row. Save button calls PUT/POST endpoints. Pattern: follow rate card table pattern from Phase 8. |
| 107.10 | Create RetentionCheckResults component | 107B | | `components/compliance/RetentionCheckResults.tsx`. Props: result (RetentionCheckResult). Shows flagged counts per record type. "Purge Selected" button per type. Expandable list of flagged record IDs (or summary). "Run Check" button triggers POST /api/retention-policies/check. |
| 107.11 | Add checklist template list to settings | 107B | | `app/(app)/org/[slug]/settings/checklists/page.tsx`. List of checklist templates: name, customerType badge, source badge (PLATFORM/ORG_CUSTOM), autoInstantiate toggle, item count, actions (edit, clone, deactivate). "Create Template" button. Link to edit page. Admin/owner only. |
| 107.12 | Add settings navigation links | 107B | | Modify `lib/nav-items.ts` or settings layout. Add "Checklists" and "Compliance" links to settings sidebar. Admin/owner only. |
| 107.13 | Add compliance settings server actions | 107B | | `app/(app)/org/[slug]/settings/compliance/actions.ts`. updateComplianceSettings(dormancyThresholdDays, dataRequestDeadlineDays) — PATCH /api/settings/compliance. getRetentionPolicies(), createRetentionPolicy(...), updateRetentionPolicy(id, ...), deleteRetentionPolicy(id), runRetentionCheck(), executePurge(recordType, recordIds[]). |
| 107.14 | Add settings frontend tests | 107B | | `__tests__/compliance/RetentionPolicyTable.test.tsx` (~4 tests): renders policy rows, add row creates new entry, delete removes row, save calls action. `__tests__/compliance/ComplianceSettingsPage.test.tsx` (~3 tests): renders dormancy/deadline inputs, save updates settings, displays applied packs. Pattern: @testing-library/react. |

### Key Files

**Slice 107A — Create:**
- `frontend/app/(app)/org/[slug]/compliance/requests/page.tsx`
- `frontend/app/(app)/org/[slug]/compliance/requests/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/compliance/requests/actions.ts`
- `frontend/components/compliance/CreateDataRequestDialog.tsx`
- `frontend/components/compliance/DataRequestTimeline.tsx`
- `frontend/components/compliance/DeletionConfirmDialog.tsx`
- `frontend/__tests__/compliance/DataRequestListPage.test.tsx`
- `frontend/__tests__/compliance/DeletionConfirmDialog.test.tsx`

**Slice 107B — Create:**
- `frontend/app/(app)/org/[slug]/settings/compliance/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/compliance/actions.ts`
- `frontend/app/(app)/org/[slug]/settings/checklists/page.tsx`
- `frontend/components/compliance/RetentionPolicyTable.tsx`
- `frontend/components/compliance/RetentionCheckResults.tsx`
- `frontend/__tests__/compliance/RetentionPolicyTable.test.tsx`
- `frontend/__tests__/compliance/ComplianceSettingsPage.test.tsx`

**Slice 107B — Modify:**
- `frontend/lib/nav-items.ts` — Add Checklists + Compliance settings links

**Read for context:**
- `frontend/app/(app)/org/[slug]/settings/` — Existing settings page structure
- `frontend/app/(app)/org/[slug]/invoices/` — List page pattern

### Architecture Decisions

- **Deletion requires name confirmation**: Typing the exact customer name prevents accidental data destruction. Pattern from GitHub's repository deletion flow.
- **Settings split**: Retention policies and pack management are in /settings/compliance. Checklist template management is in /settings/checklists — separate route because templates are complex enough for their own page.

---

## Epic 108: Compliance Dashboard

**Goal**: Build the compliance overview dashboard — lifecycle distribution, onboarding pipeline, open data requests, dormancy candidates — providing admins with a single-page compliance health view.

**References**: Architecture doc Section 14.7.2 (ComplianceDashboardPage).

**Dependencies**: Epics 106 (lifecycle UI components), 107 (data request + retention components)

**Scope**: Frontend (+ 1 small backend endpoint)

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **108A** | 108.1–108.10 | ComplianceDashboardPage (/compliance), LifecycleDistributionSection (stat cards), OnboardingPipelineSection, DataRequestsSection, DormancyCandidateList, sidebar nav entry, GET /api/customers/lifecycle-summary backend endpoint. Frontend tests (~10 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 108.1 | Create ComplianceDashboardPage | 108A | | `app/(app)/org/[slug]/compliance/page.tsx`. Server component. Four sections: (1) LifecycleDistributionSection, (2) OnboardingPipelineSection, (3) DataRequestsSection, (4) DormancyCandidateList (after button click). Admin/owner only. Pattern: follow CompanyDashboardPage from Phase 9. |
| 108.2 | Create LifecycleDistributionSection | 108A | | `components/compliance/LifecycleDistributionSection.tsx`. Props: counts Record<string, number>. Five stat cards: PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDED. Each: colored icon (matching badge colors), count, label. Click navigates to /customers?lifecycleStatus=STATUS. Pattern: DashboardStatCard from Phase 9. |
| 108.3 | Create OnboardingPipelineSection | 108A | | `components/compliance/OnboardingPipelineSection.tsx`. Props: customers[] with {id, name, lifecycleStatusChangedAt, checklistProgress{completed, total}}. Table: customer name (link), duration ("In onboarding 5 days"), progress bar. Sort by duration descending (longest first). Empty state: "No customers currently in onboarding". |
| 108.4 | Create DataRequestsSection | 108A | | `components/compliance/DataRequestsSection.tsx`. Props: openCount, urgentRequests[]. Shows: "N open requests" badge. Up to 5 most urgent with deadline countdown (green >7d, amber 3-7d, red <3d/overdue). "View All Requests" link → /compliance/requests. Empty state: "No open data requests". |
| 108.5 | Create DormancyCandidateList | 108A | | `components/compliance/DormancyCandidateList.tsx`. Props: candidates[]. Shown after "Check for Dormant Customers" button. Table: customer name (link), last activity date, days since activity (red if >threshold). "Mark as Dormant" per row. Bulk "Mark Selected" with checkboxes. Empty state: "No dormant customers detected." |
| 108.6 | Create compliance dashboard server actions | 108A | | `app/(app)/org/[slug]/compliance/actions.ts`. getComplianceDashboardData(orgSlug) — aggregates lifecycle counts, onboarding customers with progress, top 5 open data requests. runDormancyCheck(orgSlug) — POST /api/customers/dormancy-check. markCustomerDormant(customerId) — POST /api/customers/{id}/transition {targetStatus: "DORMANT"}. |
| 108.7 | Add lifecycle-summary backend endpoint | 108A | | Modify `customer/CustomerController.java`. Add GET /api/customers/lifecycle-summary. Native query: `SELECT lifecycle_status, COUNT(*) FROM customers WHERE status = 'ACTIVE' GROUP BY lifecycle_status`. Return Map<String, Long>. Admin/owner only. Add 2 assertions to existing CustomerLifecycleControllerTest. |
| 108.8 | Add Compliance to sidebar navigation | 108A | | Modify `lib/nav-items.ts`. Add "Compliance" link to main sidebar (not settings). Icon: ShieldCheck from lucide-react. Admin/owner only role gate. Position: below Projects and above Settings. |
| 108.9 | Add dashboard frontend tests | 108A | | `__tests__/compliance/LifecycleDistributionSection.test.tsx` (~4 tests): renders 5 stat cards, correct counts, click navigates. `__tests__/compliance/DormancyCandidateList.test.tsx` (~4 tests): empty state, renders rows, mark as dormant calls action, days highlighted. Pattern: @testing-library/react. |
| 108.10 | Add dashboard API client | 108A | | `lib/compliance-api.ts` additions. getLifecycleStatusCounts(), getOnboardingPipelineData(), getDashboardDataRequests(). Pattern: follow existing API client. |

### Key Files

**Slice 108A — Create:**
- `frontend/app/(app)/org/[slug]/compliance/page.tsx`
- `frontend/app/(app)/org/[slug]/compliance/actions.ts`
- `frontend/components/compliance/LifecycleDistributionSection.tsx`
- `frontend/components/compliance/OnboardingPipelineSection.tsx`
- `frontend/components/compliance/DataRequestsSection.tsx`
- `frontend/components/compliance/DormancyCandidateList.tsx`
- `frontend/__tests__/compliance/LifecycleDistributionSection.test.tsx`
- `frontend/__tests__/compliance/DormancyCandidateList.test.tsx`

**Slice 108A — Modify:**
- `frontend/lib/nav-items.ts` — Add Compliance to sidebar
- `frontend/lib/compliance-api.ts` — Add dashboard API functions
- `backend/src/main/java/.../customer/CustomerController.java` — Add /lifecycle-summary endpoint

**Read for context:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — Dashboard page pattern from Phase 9

### Architecture Decisions

- **Dashboard is read-only**: No mutations except "Mark as Dormant" (calls lifecycle transition). All other actions navigate to detail pages.
- **Backend aggregate**: Lifecycle counts from `GROUP BY` query, not client-side counting. Scales with large customer bases.
- **Small backend addition in frontend slice**: The lifecycle-summary endpoint is ~10 lines — small enough to include in the frontend slice rather than creating a separate backend slice.

---

## Appendix: Slice Dependency Summary

```
[100A] ──► [100B] ──► [101A] ──► [101B] ──► [102A] ──► [102B]
                 │                                          │
                 │                                    [103A] ──► [103B]
                 │                                                  │
                 │                                         [106A] ──► [106B]
                 │                                                        │
                 ├──► [104A] ──► [104B]                                   │
                 │                   │                                    │
                 │              [107A]                                    │
                 │                                                        │
                 ├──► [105A] ──► [105B]                                   │
                 │                   │                                    │
                 │              [107B]                                    │
                 │                                                        │
                 └────────────────────────────────────────────────► [108A]
```

**Minimum parallel build plan** (all tracks starting after 100B):
- **Track A** (checklist engine): 101A → 101B → 102A → 102B → 103A → 103B
- **Track B** (data requests): 104A → 104B (start immediately after 100B)
- **Track C** (retention): 105A → 105B (start immediately after 100B)

After Track A completes:
- **Track D** (lifecycle + checklist UI): 106A → 106B

After Track B completes:
- **Track E** (DSR UI): 107A

After Track C completes:
- **Track F** (settings UI): 107B

After Tracks D + E + F: **108A** (dashboard)

---

## Appendix: New Packages Summary

| Package | Location | Contents |
|---------|----------|----------|
| `compliance/` | `backend/.../b2bstrawman/compliance/` | CustomerLifecycleService, CustomerLifecycleGuard, LifecycleAction, CustomerStatusChangedEvent, CompliancePackSeeder, CompliancePackDefinition, ChecklistInstantiationService |
| `checklist/` | `backend/.../b2bstrawman/checklist/` | ChecklistTemplate, ChecklistTemplateItem, ChecklistInstance, ChecklistInstanceItem entities; all repositories; ChecklistTemplateService, ChecklistInstanceService; controllers; DTOs |
| `datarequest/` | `backend/.../b2bstrawman/datarequest/` | DataSubjectRequest entity + repo; DataSubjectRequestService, DataExportService, DataAnonymizationService; DataRequestController |
| `retention/` | `backend/.../b2bstrawman/retention/` | RetentionPolicy entity + repo; RetentionService, RetentionPolicyService; RetentionController; RetentionCheckResult |

**New resource directories**:
- `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`
- `backend/src/main/resources/compliance-packs/sa-fica-individual/pack.json`
- `backend/src/main/resources/compliance-packs/sa-fica-company/pack.json`

**New frontend directories**:
- `frontend/components/compliance/` — All compliance components
- `frontend/app/(app)/org/[slug]/compliance/` — Dashboard + requests routes
- `frontend/app/(app)/org/[slug]/settings/checklists/` — Template management
- `frontend/app/(app)/org/[slug]/settings/compliance/` — Retention + pack settings

---

## Appendix: Test Count Targets

| Epic | Slice | Backend Tests | Frontend Tests | Total |
|------|-------|--------------|----------------|-------|
| 100 | 100A | 5 | — | 5 |
| 100 | 100B | 25 | — | 25 |
| 101 | 101A | 15 | — | 15 |
| 101 | 101B | 12 | — | 12 |
| 102 | 102A | 15 | — | 15 |
| 102 | 102B | 15 | — | 15 |
| 103 | 103A | 10 | — | 10 |
| 103 | 103B | 12 | — | 12 |
| 104 | 104A | 15 | — | 15 |
| 104 | 104B | 12 | — | 12 |
| 105 | 105A | 10 | — | 10 |
| 105 | 105B | 10 | — | 10 |
| 106 | 106A | — | 10 | 10 |
| 106 | 106B | — | 10 | 10 |
| 107 | 107A | — | 8 | 8 |
| 107 | 107B | — | 7 | 7 |
| 108 | 108A | 2 | 8 | 10 |
| **Total** | | **~158** | **~43** | **~201** |
