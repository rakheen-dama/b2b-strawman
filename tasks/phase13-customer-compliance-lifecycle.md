# Phase 13 — Customer Compliance & Lifecycle

Jurisdiction-agnostic compliance layer with customer lifecycle state machine (PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDED), checklist engine for onboarding verification, compliance packs (bundled seed data for jurisdiction-specific content), data subject request handling (ACCESS/DELETION/CORRECTION/OBJECTION), and retention policies with flag-then-purge workflow.

**Architecture doc**: `architecture/phase13-customer-compliance-lifecycle.md`

**ADRs**: [ADR-060](../adr/ADR-060-lifecycle-status-core-field.md) (lifecycle status), [ADR-061](../adr/ADR-061-checklist-first-class-entities.md) (checklist entities), [ADR-062](../adr/ADR-062-anonymization-over-hard-deletion.md) (anonymization), [ADR-063](../adr/ADR-063-compliance-packs-bundled-seed-data.md) (compliance packs)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 96 | Entity Foundation + Lifecycle Guards | Backend | -- | L | 96A, 96B | **Done** (PRs #198, #199) |
| 97 | Lifecycle Transitions + Dormancy Detection | Both | 96 | M | 97A, 97B | **Done** (PRs #200, #201) |
| 98 | Checklist Engine | Both | 96, 97 | L | 98A, 98B, 98C | |
| 99 | Compliance Packs + Seeder | Backend | 98 | M | 99A | |
| 100 | Data Subject Requests | Both | 96 | L | 100A, 100B | |
| 101 | Retention Policies | Both | 96 | M | 101A | |
| 102 | Compliance Dashboard | Frontend | 97, 98, 100, 101 | S | 102A | |

## Dependency Graph

```
[E96A Entity Foundation]──►[E96B Entities + Guards]
    (Backend V30)               (Backend)
         │
         ├──────────────────────────────────────────┐
         │                                          │
         ▼                                          │
    [E97A Lifecycle Backend]                        │
    (Backend)                                       │
         │                                          │
         ├──────────────►[E97B Lifecycle Frontend]  │
         │                (Frontend)                │
         │                                          │
         ▼                                          │
    [E98A Template CRUD]                            │
    (Backend)                                       │
         │                                          │
         ▼                                          │
    [E98B Instance + Completion]                    │
    (Backend)                                       │
         │                                          │
         ├──────────────►[E98C Checklist Frontend]  │
         │                (Frontend)                │
         │                                          │
         ▼                                          │
    [E99A Compliance Packs]                         │
    (Backend)                                       │
                                                    │
    [E100A Data Request Backend]◄───────────────────┤
    (Backend)                                       │
         │                                          │
         ▼                                          │
    [E100B Anonymization + Frontend]                │
    (Both)                                          │
                                                    │
    [E101A Retention Policies]◄─────────────────────┘
    (Both)

    [E102A Compliance Dashboard]◄── all above
    (Frontend)
```

**Parallel opportunities**:
- Epic 100 (data subject requests) can run in parallel with Epics 97/98 (lifecycle + checklists)
- Epic 101 (retention policies) can run in parallel with Epics 97/98/100
- After Epic 96, three independent tracks: lifecycle (97→98→99), data requests (100), retention (101)

## Implementation Order

### Stage 1: Foundation — Migration & Entities

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 96 | 96A | V30 migration (6 tables, Customer/OrgSettings columns, RLS, indexes, backfill). Customer/OrgSettings entity modifications. Integration tests for migration + entity persistence (~10 tests). Foundation for all compliance work. |
| 1b | Epic 96 | 96B | 6 new entities (ChecklistTemplate, ChecklistTemplateItem, ChecklistInstance, ChecklistInstanceItem, DataSubjectRequest, RetentionPolicy). 6 repositories with JPQL findOneById. CustomerLifecycleGuard service. Guard unit tests + shared-schema isolation tests (~20 tests). |

### Stage 2: Three Independent Tracks (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 97 | 97A | CustomerLifecycleService (transition execution, validation, audit/notification), CustomerLifecycleController, guard enforcement on existing controllers, integration tests (~12 backend tests). Depends on 96. |
| 2b | Epic 100 | 100A | DataSubjectRequestService CRUD + status transitions, DataExportService (data gathering, ZIP, S3), controller, integration tests (~15 backend tests). Independent of 97. |
| 2c | Epic 101 | 101A | RetentionPolicyService CRUD + retention check + purge, RetentionPolicyController, frontend settings page, integration tests (~17 tests). Independent of 97 and 100. |

### Stage 3: Lifecycle Frontend + Checklist Backend

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 97 | 97B | Frontend: LifecycleStatusBadge, LifecycleTransitionMenu, customer detail/list integration, DormancyCheckButton (~8 frontend tests). Depends on 97A. |
| 3b | Epic 98 | 98A | ChecklistTemplateService CRUD + validation, ChecklistTemplateController, integration tests (~15 backend tests). Depends on 96B. |
| 3c | Epic 100 | 100B | DataAnonymizationService, execute-deletion endpoint, frontend request pages + dialogs, tests (~13 tests). Depends on 100A. |

### Stage 4: Checklist Instance + Completion

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 98 | 98B | ChecklistInstanceService (instantiation, completion, dependency enforcement, lifecycle auto-transition), ChecklistInstanceController, integration tests (~15 backend tests). Depends on 98A + 97A (needs lifecycle transitions for auto-transition). |

### Stage 5: Checklist Frontend + Compliance Packs

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a | Epic 98 | 98C | Frontend: OnboardingTab, ChecklistProgress, ChecklistItemRow, ChecklistTemplateEditor, server actions, frontend tests (~10 tests). Depends on 98B. |
| 5b | Epic 99 | 99A | CompliancePackSeeder, 3 pack.json files, provisioning integration, default retention policies, pack activation UI, integration tests (~12 tests). Depends on 98A (checklist templates must exist). |

### Stage 6: Compliance Dashboard

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 6a | Epic 102 | 102A | ComplianceDashboard page, OnboardingPipeline, DataRequestList, RetentionFlagList, sidebar nav, frontend tests (~10 tests). Depends on all backend epics. |

### Timeline

```
Stage 1:  [96A] ──► [96B]                                       ← foundation (sequential)
Stage 2:  [97A]  //  [100A]  //  [101A]                         ← three parallel tracks
Stage 3:  [97B]  //  [98A]  //  [100B]                          ← parallel
Stage 4:  [98B]                                                  ← checklist completion (sequential from 98A + 97A)
Stage 5:  [98C]  //  [99A]                                       ← parallel
Stage 6:  [102A]                                                 ← dashboard (after all)
```

---

## Epic 96: Entity Foundation + Lifecycle Guards

**Goal**: Create V30 migration adding 6 new tables (checklist_templates, checklist_template_items, checklist_instances, checklist_instance_items, data_subject_requests, retention_policies) and extending Customer/OrgSettings with compliance columns. Create all 6 entities with tenant isolation, repositories, and `CustomerLifecycleGuard` service for action gating. Fully tested (entities, repositories, guards, tenant isolation).

**References**: Architecture doc Sections 13.2 (domain model), 13.3.4 (lifecycle guard), 13.10 (migrations). [ADR-060](../adr/ADR-060-lifecycle-status-core-field.md), [ADR-061](../adr/ADR-061-checklist-first-class-entities.md), [ADR-062](../adr/ADR-062-anonymization-over-hard-deletion.md), [ADR-063](../adr/ADR-063-compliance-packs-bundled-seed-data.md).

**Dependencies**: None (foundational)

**Scope**: Backend

**Estimated Effort**: L (migration + 6 entities + 6 repositories + guard + ~30 tests)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **96A** | 96.1-96.7 | V30 migration (6 tables, Customer/OrgSettings columns, RLS, indexes, backfill), Customer/OrgSettings entity modifications, integration tests for migration + entity persistence (~10 tests) | **Done** (PR #198) |
| **96B** | 96.8-96.18 | 6 new entities (ChecklistTemplate, ChecklistTemplateItem, ChecklistInstance, ChecklistInstanceItem, DataSubjectRequest, RetentionPolicy), 6 repositories with JPQL findOneById, CustomerLifecycleGuard service, guard unit tests + shared-schema isolation tests (~20 tests) | **Done** (PR #199) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 96.1 | Create V30 migration file | 96A | **Done** | `db/migration/tenant/V30__customer_compliance_lifecycle.sql`. Full SQL per Section 13.10: (1) ALTER TABLE customers ADD lifecycle_status, lifecycle_status_changed_at, lifecycle_status_changed_by, offboarded_at. (2) ALTER TABLE org_settings ADD dormancy_threshold_days, data_request_deadline_days, compliance_pack_status. (3) CREATE TABLE checklist_templates (12 fields). (4) CREATE TABLE checklist_template_items (12 fields). (5) CREATE TABLE checklist_instances (11 fields). (6) CREATE TABLE checklist_instance_items (17 fields). (7) CREATE TABLE data_subject_requests (15 fields). (8) CREATE TABLE retention_policies (9 fields). All indexes per Section 13.10 (17 indexes total). All RLS policies per Section 13.10 (6 RLS policies). Backfill: UPDATE customers SET lifecycle_status = 'ACTIVE' WHERE lifecycle_status IS NULL. Pattern: follow V28 for large multi-table migration. |
| 96.2 | Add lifecycle columns to Customer entity | 96A | **Done** | Modify `customer/Customer.java`. Add fields: `@Column(name = "lifecycle_status", nullable = false, length = 20) private String lifecycleStatus = "PROSPECT"`, `@Column(name = "lifecycle_status_changed_at") private Instant lifecycleStatusChangedAt`, `@Column(name = "lifecycle_status_changed_by") private UUID lifecycleStatusChangedBy`, `@Column(name = "offboarded_at") private Instant offboardedAt`. Add getters/setters. Add domain method: `transitionLifecycle(String newStatus, UUID changedBy, Instant changedAt, Instant offboardedAt)`. Pattern: follow existing Customer entity pattern. |
| 96.3 | Add compliance columns to OrgSettings entity | 96A | **Done** | Modify `settings/OrgSettings.java`. Add fields: `@Column(name = "dormancy_threshold_days") private Integer dormancyThresholdDays = 90`, `@Column(name = "data_request_deadline_days") private Integer dataRequestDeadlineDays = 30`, `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "compliance_pack_status", columnDefinition = "jsonb") private List<Map<String, Object>> compliancePackStatus`. Add method: `recordCompliancePackApplication(String packId, int version)`. Pattern: follow field_pack_status JSONB pattern from Phase 11. |
| 96.4 | Add Customer lifecycle tests | 96A | **Done** | Add tests to `customer/CustomerIntegrationTest.java` (~3 tests): (1) new customer defaults to lifecycle_status='PROSPECT', (2) transitionLifecycle updates all 4 fields, (3) findOneById respects @Filter with lifecycle columns. Pattern: extend existing test file. |
| 96.5 | Add OrgSettings compliance tests | 96A | **Done** | Add tests to `settings/OrgSettingsIntegrationTest.java` (~2 tests): (1) compliance columns have defaults (90, 30, null), (2) recordCompliancePackApplication appends to JSONB array. Pattern: extend existing test file. |
| 96.6 | Test V30 migration execution | 96A | **Done** | `compliance/V30MigrationTest.java` (~3 tests): (1) migration creates 6 tables in dedicated schema, (2) migration creates 6 tables in tenant_shared, (3) backfill sets existing customers to ACTIVE. Use FlywayMigrationRunner pattern. |
| 96.7 | Verify V30 migration runs cleanly | 96A | **Done** | Run `./mvnw clean test` and verify no migration errors. Check that all 6 tables exist in both dedicated schemas and tenant_shared. Verify RLS policies applied. Verify Customer/OrgSettings columns exist. Check backfill results. |
| 96.8 | Create ChecklistTemplate entity | 96B | **Done** | `checklist/ChecklistTemplate.java`. @Entity, @Table(name = "checklist_templates"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 12 fields per Section 13.2.3. Protected no-arg constructor. Public constructor taking (name, slug, description, customerType, source). Domain methods: activate(), deactivate(). Pattern: follow FieldDefinition entity from Phase 11. |
| 96.9 | Create ChecklistTemplateItem entity | 96B | **Done** | `checklist/ChecklistTemplateItem.java`. @Entity, @Table(name = "checklist_template_items"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 12 fields per Section 13.2.4. Protected no-arg constructor. Public constructor taking (templateId, name, description, sortOrder, required, requiresDocument). Pattern: follow ChecklistTemplate entity. |
| 96.10 | Create ChecklistInstance entity | 96B | **Done** | `checklist/ChecklistInstance.java`. @Entity, @Table(name = "checklist_instances"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 11 fields per Section 13.2.5. Protected no-arg constructor. Public constructor taking (templateId, customerId, status). Domain method: complete(UUID completedBy, Instant completedAt). Pattern: follow ChecklistTemplate entity. |
| 96.11 | Create ChecklistInstanceItem entity | 96B | **Done** | `checklist/ChecklistInstanceItem.java`. @Entity, @Table(name = "checklist_instance_items"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 17 fields per Section 13.2.6. Protected no-arg constructor. Public constructor taking (instanceId, templateItemId, name, sortOrder, required). Domain methods: complete(UUID completedBy, Instant completedAt, String notes, UUID documentId), skip(String reason). Pattern: follow ChecklistInstance entity with richer domain methods. |
| 96.12 | Create DataSubjectRequest entity | 96B | **Done** | `datarequest/DataSubjectRequest.java`. @Entity, @Table(name = "data_subject_requests"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 15 fields per Section 13.2.7. Protected no-arg constructor. Public constructor taking (customerId, requestType, description, deadline, requestedBy). Domain methods: transitionTo(String newStatus), complete(UUID completedBy, Instant completedAt), reject(String reason). Pattern: follow ChecklistInstance entity with status transitions. |
| 96.13 | Create RetentionPolicy entity | 96B | **Done** | `retention/RetentionPolicy.java`. @Entity, @Table(name = "retention_policies"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 9 fields per Section 13.2.8. Protected no-arg constructor. Public constructor taking (recordType, retentionDays, triggerEvent, action). Domain methods: activate(), deactivate(). Pattern: follow ChecklistTemplate entity. |
| 96.14 | Create 6 repositories | 96B | **Done** | Create: `ChecklistTemplateRepository.java`, `ChecklistTemplateItemRepository.java`, `ChecklistInstanceRepository.java`, `ChecklistInstanceItemRepository.java`, `DataSubjectRequestRepository.java`, `RetentionPolicyRepository.java`. All extend JpaRepository<Entity, UUID>. All add JPQL: `@Query("SELECT e FROM Entity e WHERE e.id = :id") Optional<Entity> findOneById(@Param("id") UUID id)`. Pattern: follow FieldDefinitionRepository from Phase 11. |
| 96.15 | Create CustomerLifecycleGuard service | 96B | **Done** | `compliance/CustomerLifecycleGuard.java`. @Service. Methods: `void requireActionPermitted(Customer customer, LifecycleAction action)` — checks action gating table (Section 13.3.3), throws ResourceConflictException if blocked. `void requireTransitionValid(Customer customer, String targetStatus, String notes)` — checks valid transitions table (Section 13.3.2), throws ResourceConflictException for invalid transitions or missing notes (OFFBOARDED → ACTIVE requires notes). Enum LifecycleAction {CREATE_PROJECT, CREATE_TASK, LOG_TIME, CREATE_INVOICE, UPLOAD_DOCUMENT, EDIT_CUSTOMER}. Pattern: stateless validation service per Section 13.3.4 pseudocode. |
| 96.16 | Add CustomerLifecycleGuard unit tests | 96B | **Done** | `compliance/CustomerLifecycleGuardTest.java` (~10 unit tests): (1) PROSPECT cannot CREATE_PROJECT (blocked), (2) ACTIVE can CREATE_INVOICE (allowed), (3) ONBOARDING cannot CREATE_INVOICE (blocked), (4) OFFBOARDED cannot EDIT_CUSTOMER (blocked), (5) DORMANT has no restrictions, (6) PROSPECT → ONBOARDING valid, (7) PROSPECT → ACTIVE invalid, (8) ONBOARDING → ACTIVE valid, (9) OFFBOARDED → ACTIVE valid with notes, (10) OFFBOARDED → ACTIVE invalid without notes. Pattern: unit test with mocked Customer objects. |
| 96.17 | Add entity persistence tests | 96B | **Done** | `checklist/ChecklistEntityIntegrationTest.java` (~5 tests): (1) save and retrieve ChecklistTemplate in dedicated schema, (2) save and retrieve in tenant_shared with @Filter, (3) findOneById respects @Filter, (4) cascade delete template → items works, (5) unique constraint on (tenant_id, slug) enforced. Similar for DataSubjectRequest (~3 tests), RetentionPolicy (~2 tests). Total ~10 tests. Pattern: follow FieldDefinitionIntegrationTest from Phase 11. |
| 96.18 | Add shared-schema isolation tests | 96B | **Done** | `compliance/ComplianceSharedSchemaTest.java` (~5 tests): (1) ChecklistTemplate RLS policy isolates by tenant, (2) ChecklistInstance RLS policy isolates, (3) DataSubjectRequest RLS policy isolates, (4) RetentionPolicy RLS policy isolates, (5) cross-tenant findOneById returns empty. Pattern: follow existing shared-schema tests from Phase 6/8. Provision 2 tenants, create records in tenant1, verify tenant2 cannot see them. |

### Key Files

**Slice 96A — Create:**
- `backend/src/main/resources/db/migration/tenant/V30__customer_compliance_lifecycle.sql`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/V30MigrationTest.java`

**Slice 96A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` — Add 4 lifecycle columns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — Add 3 compliance columns
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerIntegrationTest.java` — Add lifecycle tests
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsIntegrationTest.java` — Add compliance tests

**Slice 96A — Read for context:**
- `architecture/phase13-customer-compliance-lifecycle.md` Sections 13.2.1, 13.2.2, 13.10
- `backend/src/main/resources/db/migration/tenant/V28__add_saved_views.sql` — Latest migration, follow pattern
- `adr/ADR-060-lifecycle-status-core-field.md`

**Slice 96B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateItem.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstance.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItem.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicy.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateItemRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItemRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicyRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuardTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistEntityIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceSharedSchemaTest.java`

**Slice 96B — Read for context:**
- `architecture/phase13-customer-compliance-lifecycle.md` Sections 13.2.3-13.2.8, 13.3.4, 13.10
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java` — Entity pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionIntegrationTest.java` — Test pattern
- `adr/ADR-061-checklist-first-class-entities.md`
- `adr/ADR-062-anonymization-over-hard-deletion.md`
- `adr/ADR-063-compliance-packs-bundled-seed-data.md`

### Architecture Decisions

- **checklist/, datarequest/, retention/ packages**: Three new feature packages. `compliance/` package contains CustomerLifecycleGuard + lifecycle service (Epic 97).
- **Lifecycle status as core column**: Per ADR-060, `lifecycle_status` is a VARCHAR column on Customer (not a custom field). Drives platform behavior, queryable, type-safe.
- **4-table checklist model**: Per ADR-061, dedicate tables to ChecklistTemplate, ChecklistTemplateItem, ChecklistInstance, ChecklistInstanceItem. Queryable per-item, FK integrity, audit trail.
- **Anonymization over hard deletion**: Per ADR-062, DELETION requests anonymize PII (name → "Anonymized Customer a1b2c3", email → null) but retain financial records.
- **V30 backfill**: Existing customers set to lifecycle_status='ACTIVE' (already in use, past onboarding). New customers after migration default to 'PROSPECT'.
- **RLS policies**: All 6 new tables get RLS policies for shared-schema isolation. Pattern: `tenant_id = current_setting('app.current_tenant', true)`.
- **JPQL findOneById**: Required for all repositories to respect Hibernate @Filter in shared-schema mode.

---

## Epic 97: Lifecycle Transitions + Dormancy Detection

**Goal**: Implement lifecycle state machine transitions (PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDED), dormancy detection, backend API endpoints, frontend lifecycle status badge, transition action menu on customer detail page, lifecycle status column + filter on customer list page. Fully tested (transitions, guards, frontend).

**References**: Architecture doc Sections 13.3 (lifecycle state machine), 13.8.1 (lifecycle endpoints), 13.9.1 (transition sequence), 13.11 (notifications/audit), 13.13.3 (permissions).

**Dependencies**: Epic 96 (Customer entity with lifecycle columns, CustomerLifecycleGuard)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: M (backend service + controller + frontend components + ~20 tests)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **97A** | 97.1-97.8 | CustomerLifecycleService (transition execution, validation, audit/notification), CustomerLifecycleController (/transition, /dormancy-check), guard enforcement on existing controllers, integration tests (~12 backend tests) | **Done** (PR #200) |
| **97B** | 97.9-97.16 | Frontend: LifecycleStatusBadge component, LifecycleTransitionMenu, customer detail page integration, customer list column + filter, DormancyCheckButton, server actions, frontend tests (~8 tests) | **Done** (PR #201) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 97.1 | Create CustomerLifecycleService | 97A | **Done** | `compliance/CustomerLifecycleService.java`. @Service. Methods: `Customer transitionCustomer(UUID customerId, String targetStatus, String notes)` — load Customer, call CustomerLifecycleGuard.requireTransitionValid(), execute transition logic (set lifecycle_status, lifecycle_status_changed_at, lifecycle_status_changed_by, offboarded_at if transitioning to OFFBOARDED, clear offboarded_at if reactivating from OFFBOARDED), save, emit AuditEvent(CUSTOMER_STATUS_CHANGED), emit Notification(CUSTOMER_STATUS_CHANGED to org admins), return updated Customer. `List<Customer> checkDormancy()` — query customers with lifecycle_status='ACTIVE', last activity > dormancyThresholdDays ago, return list. Pattern: follow InvoiceService from Phase 10. Use AuditEventBuilder + ApplicationEventPublisher for events. |
| 97.2 | Create CustomerLifecycleController | 97A | **Done** | `compliance/CustomerLifecycleController.java`. @RestController, @RequestMapping("/api/customers"). Endpoints: POST /api/customers/{id}/transition (request: {targetStatus, notes}, response: CustomerResponse with lifecycle fields), POST /api/customers/dormancy-check (response: {thresholdDays, candidates: [{id, name, lastActivityAt, daysSinceActivity, currentStatus}]}). @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')"). Pattern: follow InvoiceController from Phase 10. Return ProblemDetail for 409 Conflict when transition invalid. |
| 97.3 | Add custom queries to CustomerRepository | 97A | **Done** | Extend `customer/CustomerRepository.java`. Add JPQL: `List<Customer> findByLifecycleStatus(String lifecycleStatus)`. Add JPQL for dormancy check: `@Query("SELECT c FROM Customer c WHERE c.lifecycleStatus = 'ACTIVE' AND c.updatedAt < :threshold") List<Customer> findActiveCustomersWithoutActivitySince(@Param("threshold") Instant threshold)`. Pattern: custom JPQL query. |
| 97.4 | Add lifecycle transition integration tests | 97A | **Done** | `compliance/CustomerLifecycleTransitionTest.java` (~7 tests): (1) PROSPECT → ONBOARDING succeeds, sets lifecycle_status + changed_at + changed_by, (2) ONBOARDING → ACTIVE succeeds, (3) ACTIVE → DORMANT succeeds, (4) ACTIVE → OFFBOARDED succeeds and sets offboarded_at, (5) OFFBOARDED → ACTIVE succeeds with notes and clears offboarded_at, (6) PROSPECT → ACTIVE fails (invalid transition), (7) transition emits AuditEvent + Notification. Pattern: integration test with provisioning, MockMvc POST /api/customers/{id}/transition. |
| 97.5 | Add dormancy detection integration tests | 97A | **Done** | `compliance/DormancyDetectionTest.java` (~3 tests): (1) customer with updatedAt > threshold is flagged, (2) customer with updatedAt < threshold is not flagged, (3) POST /api/customers/dormancy-check returns correct candidates. Pattern: integration test with provisioning. |
| 97.6 | Add guard enforcement on existing controllers | 97A | **Done** | Modify `project/ProjectController.java`, `invoice/InvoiceController.java`. Inject CustomerLifecycleGuard. When creating project/invoice linked to a customer, call `customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT or CREATE_INVOICE)` before proceeding. Return 409 if blocked. Pattern: pre-check before mutation. |
| 97.7 | Add guard enforcement integration tests | 97A | **Done** | `compliance/LifecycleGuardEnforcementTest.java` (~2 tests): (1) creating invoice for ONBOARDING customer returns 409, (2) creating project for PROSPECT customer returns 409. Pattern: integration test with provisioning. |
| 97.8 | Verify lifecycle backend complete | 97A | **Done** | Run `./mvnw clean test` and verify all lifecycle tests pass. |
| 97.9 | Create LifecycleStatusBadge component | 97B | **Done** | `components/customers/LifecycleStatusBadge.tsx`. Props: `lifecycleStatus: string`. Render color-coded badge per status: PROSPECT (gray), ONBOARDING (blue), ACTIVE (green), DORMANT (yellow), OFFBOARDED (red). Pattern: follow existing badge components. Use Shadcn Badge. |
| 97.10 | Create LifecycleTransitionMenu component | 97B | **Done** | `components/customers/LifecycleTransitionMenu.tsx`. Props: `customer: Customer, canManage: boolean`. Render DropdownMenu with available transitions based on current status. Each action calls transitionCustomer server action. Pattern: follow existing action menus. |
| 97.11 | Create transitionCustomer server action | 97B | **Done** | `lib/actions/compliance.ts`. Function: `transitionCustomer(customerId, targetStatus, notes?)`. Call POST /api/customers/{id}/transition. Revalidate customer detail page. Return success/error. Pattern: follow existing server actions from Phase 10. |
| 97.12 | Integrate lifecycle UI on customer detail page | 97B | **Done** | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx`. Display LifecycleStatusBadge next to customer name. Display LifecycleTransitionMenu in page header. Pass `canManage` prop (admin/owner only). Pattern: follow existing page layout. |
| 97.13 | Add lifecycle column to customer list page | 97B | **Done** | Modify `app/(app)/org/[slug]/customers/page.tsx`. Add "Lifecycle" column to customer table. Render LifecycleStatusBadge for each customer. Add filter dropdown for lifecycle status. Pattern: follow existing table columns + filters from Phase 11. |
| 97.14 | Create DormancyCheckButton component | 97B | **Done** | `components/compliance/DormancyCheckButton.tsx`. Button: "Check for Dormant Customers". On click, call checkDormancy server action, display results dialog. Admin/owner only. Pattern: follow existing action buttons. |
| 97.15 | Create checkDormancy server action | 97B | **Done** | `lib/actions/compliance.ts`. Function: `checkDormancy()`. Call POST /api/customers/dormancy-check. Return candidates list. Pattern: follow existing server actions. |
| 97.16 | Add lifecycle frontend tests | 97B | **Done** | `components/customers/LifecycleStatusBadge.test.tsx` (~5 tests): one per status. `components/customers/LifecycleTransitionMenu.test.tsx` (~3 tests): admin sees actions, member hidden, click calls action. Pattern: follow existing component tests from Phase 11. |

### Key Files

**Slice 97A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleTransitionTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyDetectionTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/LifecycleGuardEnforcementTest.java`

**Slice 97A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` — Add dormancy query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` — Add guard check
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — Add guard check

**Slice 97B — Create:**
- `frontend/components/customers/LifecycleStatusBadge.tsx`
- `frontend/components/customers/LifecycleTransitionMenu.tsx`
- `frontend/components/compliance/DormancyCheckButton.tsx`
- `frontend/lib/actions/compliance.ts`
- `frontend/components/customers/LifecycleStatusBadge.test.tsx`
- `frontend/components/customers/LifecycleTransitionMenu.test.tsx`

**Slice 97B — Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add badge + menu
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — Add lifecycle column + filter

### Architecture Decisions

- **compliance/ package**: CustomerLifecycleGuard (Epic 96) + CustomerLifecycleService + CustomerLifecycleController. All lifecycle logic centralized.
- **Lifecycle transitions emit events**: CUSTOMER_STATUS_CHANGED audit event + notification to org admins.
- **Dormancy detection is on-demand**: POST /api/customers/dormancy-check, not a scheduled job.
- **Guard enforcement**: Injected into ProjectController, InvoiceController. More controllers add guard checks as checklists integrate.
- **Notes required for reactivation**: OFFBOARDED → ACTIVE transition requires `notes` field. Enforced by CustomerLifecycleGuard.

---

## Epic 98: Checklist Engine

**Goal**: Implement checklist template/instance CRUD, item completion workflow, dependency enforcement, auto-instantiation on PROSPECT → ONBOARDING, auto-transition to ACTIVE when checklists complete, frontend onboarding tab on customer detail page. Fully tested (backend logic, frontend components).

**References**: Architecture doc Sections 13.4 (checklist engine), 13.8.2-13.8.3 (template/instance endpoints), 13.9.1-13.9.2 (sequence diagrams), 13.13.1-13.13.2 (permissions).

**Dependencies**: Epic 96 (entities + repositories), Epic 97 (lifecycle transitions for auto-transition to ACTIVE)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: L (template/instance CRUD + completion logic + dependency enforcement + frontend + ~40 tests)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **98A** | 98.1-98.10 | ChecklistTemplateService CRUD + clone + validation (circular deps, auto-instantiate uniqueness), ChecklistTemplateController, integration tests (~15 backend tests) | |
| **98B** | 98.11-98.20 | ChecklistInstanceService (instantiation, item completion, skip, reopen, dependency enforcement, completion detection, lifecycle auto-transition), ChecklistInstanceController, integration tests (~15 backend tests) | |
| **98C** | 98.21-98.30 | Frontend: OnboardingTab component, ChecklistProgress, ChecklistItemRow, ChecklistTemplateEditor (settings), server actions, frontend tests (~10 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 98.1 | Create ChecklistTemplateService | 98A | | `checklist/ChecklistTemplateService.java`. @Service. Methods: `List<ChecklistTemplate> listAll()`, `ChecklistTemplate create(CreateTemplateRequest req)` — validate slug uniqueness, validate only one auto_instantiate=true per customerType per tenant, save template + items. `ChecklistTemplate update(UUID id, UpdateTemplateRequest req)`. `void deactivate(UUID id)`. `ChecklistTemplate clone(UUID id, String newName)` — copy template + items, set source=ORG_CUSTOM, pack_id=null. Validation: circular dependency check (graph traversal on depends_on_item_id). Pattern: follow FieldDefinitionService from Phase 11. |
| 98.2 | Add template queries to repositories | 98A | | Extend `ChecklistTemplateRepository.java` and `ChecklistTemplateItemRepository.java`. Add JPQL queries for: findByActiveTrueOrderBySortOrder, countByCustomerTypeAndAutoInstantiateTrueAndActiveTrue, findByTemplateIdOrderBySortOrder, deleteByTemplateIdAndIdNotIn. Pattern: custom JPQL queries. |
| 98.3 | Create ChecklistTemplateController | 98A | | `checklist/ChecklistTemplateController.java`. @RestController, @RequestMapping("/api/checklist-templates"). Endpoints: GET (list), GET /{id} (get with items), POST (create), PUT /{id} (update), DELETE /{id} (deactivate), POST /{id}/clone. @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')") on mutations. Pattern: follow FieldDefinitionController from Phase 11. |
| 98.4 | Add template CRUD integration tests | 98A | | `checklist/ChecklistTemplateIntegrationTest.java` (~7 tests): create, update (add/remove items), deactivate, clone, circular dependency rejection, auto-instantiate uniqueness, slug uniqueness. Pattern: integration test with provisioning. |
| 98.5 | Add template permission integration tests | 98A | | `checklist/ChecklistTemplatePermissionTest.java` (~3 tests): admin create, member create blocked (403), member list allowed. Pattern: integration test with role-based access. |
| 98.6 | Add template queries (continued) | 98A | | Add JPQL: `findByActiveTrueAndAutoInstantiateTrueAndCustomerType(String customerType)` — for auto-instantiation during lifecycle transition. |
| 98.7 | Validate cross-template dependency rejection | 98A | | Test: item in template B with depends_on_item_id pointing to template A item is rejected. Pattern: integration test. |
| 98.8 | Validate template update orphan cleanup | 98A | | Test: update template removing 1 of 3 items, verify orphaned item deleted. Pattern: integration test. |
| 98.9 | Validate slug uniqueness on template | 98A | | Test: duplicate slug in same tenant returns 400. Pattern: integration test. |
| 98.10 | Verify template backend complete | 98A | | Run `./mvnw clean test` and verify all template tests pass. |
| 98.11 | Create ChecklistInstanceService | 98B | | `checklist/ChecklistInstanceService.java`. @Service. Methods: `ChecklistInstance instantiate(UUID customerId, UUID templateId)` — load template + items, create instance, copy items (snapshot), set status=BLOCKED for items with dependencies. `ChecklistInstanceItem completeItem(UUID itemId, ...)` — validate document requirement, validate dependency completion, set COMPLETED, check instance completion, check all-instances completion → auto-transition to ACTIVE. `ChecklistInstanceItem skipItem(UUID itemId, String reason)` — validate required=false. `ChecklistInstanceItem reopenItem(UUID itemId)`. Pattern: complex service per Section 13.4.3. |
| 98.12 | Add instance queries to repositories | 98B | | Extend `ChecklistInstanceRepository.java` and `ChecklistInstanceItemRepository.java`. Add JPQL queries for: findByCustomerIdOrderByCreatedAtDesc, countByCustomerIdAndStatusNot, findByInstanceIdOrderBySortOrder, countByInstanceIdAndRequiredTrueAndStatusNot, findByDependsOnItemId. |
| 98.13 | Create ChecklistInstanceController | 98B | | `checklist/ChecklistInstanceController.java`. @RestController, @RequestMapping("/api"). Endpoints: GET /customers/{customerId}/checklists, GET /checklist-instances/{id}, POST /customers/{customerId}/checklists, PUT /checklist-items/{id}/complete, PUT /checklist-items/{id}/skip, PUT /checklist-items/{id}/reopen. @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEAD')") on item completion. |
| 98.14 | Integrate auto-instantiation on lifecycle transition | 98B | | Modify `compliance/CustomerLifecycleService.java`. On PROSPECT → ONBOARDING, auto-instantiate active auto_instantiate templates. |
| 98.15 | Add instance CRUD integration tests | 98B | | `checklist/ChecklistInstanceIntegrationTest.java` (~8 tests): instantiate, completeItem, completeItem without doc when required, completeItem with incomplete dependency, last required item completes instance, skipItem required (rejected), skipItem optional, reopenItem. |
| 98.16 | Add dependency enforcement integration tests | 98B | | `checklist/ChecklistDependencyTest.java` (~4 tests): item starts BLOCKED, completing dependency unblocks, completing with incomplete dep returns 409, skipping dependency doesn't unblock. |
| 98.17 | Add checklist completion → lifecycle transition test | 98B | | `checklist/ChecklistCompletionLifecycleTest.java` (~3 tests): last item completes instance, last instance for ONBOARDING customer transitions to ACTIVE, already ACTIVE customer not re-transitioned. |
| 98.18 | Add auto-instantiation integration test | 98B | | `checklist/AutoInstantiationTest.java` (~2 tests): PROSPECT → ONBOARDING auto-instantiates, inactive templates not instantiated. |
| 98.19 | Add checklist permission integration tests | 98B | | `checklist/ChecklistInstancePermissionTest.java` (~3 tests): lead can complete, member cannot (403), admin can complete any. |
| 98.20 | Verify instance backend complete | 98B | | Run `./mvnw clean test`. |
| 98.21 | Create OnboardingTab component | 98C | | `components/customers/OnboardingTab.tsx`. List of ChecklistInstances for customer, "Manually Add Checklist" button (admin/owner). Pattern: follow existing tab components. |
| 98.22 | Create ChecklistProgress component | 98C | | `components/customers/ChecklistProgress.tsx`. Instance name, progress bar, status badge, list of ChecklistItemRow. Pattern: follow existing list components. |
| 98.23 | Create ChecklistItemRow component | 98C | | `components/customers/ChecklistItemRow.tsx`. Item name, status badge, action buttons (Mark Complete, Skip, Reopen). Pattern: follow existing row components. |
| 98.24 | Create ChecklistTemplateEditor component | 98C | | `components/settings/ChecklistTemplateEditor.tsx`. Admin/owner settings page for template CRUD. List templates, edit dialog, clone button. Pattern: follow FieldDefinitionEditor from Phase 11. |
| 98.25 | Create checklist server actions | 98C | | `lib/actions/checklists.ts`. Functions for instantiate, complete, skip, reopen, create/update/clone template. Pattern: follow existing server actions. |
| 98.26 | Integrate OnboardingTab on customer detail page | 98C | | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx`. Add "Onboarding" tab. Show only for PROSPECT/ONBOARDING/ACTIVE statuses. |
| 98.27 | Create ChecklistTemplateEditor page | 98C | | `app/(app)/org/[slug]/settings/checklists/page.tsx`. Admin/owner only. Pattern: follow settings pages. |
| 98.28 | Add ChecklistItemRow frontend tests | 98C | | `components/customers/ChecklistItemRow.test.tsx` (~5 tests): complete button, skip visibility, reopen visibility, click actions, status badge. |
| 98.29 | Add ChecklistProgress frontend tests | 98C | | `components/customers/ChecklistProgress.test.tsx` (~3 tests): progress bar, completed badge, item count. |
| 98.30 | Verify checklist frontend complete | 98C | | Run `pnpm test`. |

### Key Files

**Slice 98A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplatePermissionTest.java`

**Slice 98A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateItemRepository.java`

**Slice 98B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistDependencyTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistCompletionLifecycleTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/AutoInstantiationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstancePermissionTest.java`

**Slice 98B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItemRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleService.java`

**Slice 98C — Create:**
- `frontend/components/customers/OnboardingTab.tsx`
- `frontend/components/customers/ChecklistProgress.tsx`
- `frontend/components/customers/ChecklistItemRow.tsx`
- `frontend/components/settings/ChecklistTemplateEditor.tsx`
- `frontend/app/(app)/org/[slug]/settings/checklists/page.tsx`
- `frontend/lib/actions/checklists.ts`
- `frontend/components/customers/ChecklistItemRow.test.tsx`
- `frontend/components/customers/ChecklistProgress.test.tsx`

**Slice 98C — Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`

### Architecture Decisions

- **Snapshot behavior**: Items copied from template to instance at instantiation. Template changes don't affect in-progress instances. Per ADR-061.
- **Completion logic**: Instance completes when all required=true items are COMPLETED. Customer transitions to ACTIVE when all instances complete AND customer is ONBOARDING.
- **Dependency enforcement**: Items with depends_on_item_id start as BLOCKED. Completion of dependency unblocks dependent.
- **Document requirement**: If requires_document=true, item cannot complete without documentId.
- **Auto-instantiation**: On PROSPECT → ONBOARDING, all active auto_instantiate templates are instantiated.
- **Permissions**: Admin/owner/lead can complete items. Admin/owner for template management.

---

## Epic 99: Compliance Packs + Seeder

**Goal**: Build CompliancePackSeeder, create 3 pack.json files (generic-onboarding, sa-fica-individual, sa-fica-company), integrate with TenantProvisioningService, add pack activation UI in settings. Fully tested (pack loading, idempotent seeding, field definition delegation).

**References**: Architecture doc Sections 13.5 (compliance packs), 13.12.1 (seeder implementation). [ADR-063](../adr/ADR-063-compliance-packs-bundled-seed-data.md).

**Dependencies**: Epic 98 (checklist templates must exist for seeder to create them)

**Scope**: Backend

**Estimated Effort**: M (seeder service + 3 pack files + provisioning integration + ~12 tests)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **99A** | 99.1-99.12 | CompliancePackSeeder service, CompliancePackDefinition record, 3 pack.json files, provisioning integration, default retention policy seeding, pack activation UI, integration tests (~12 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 99.1 | Create CompliancePackDefinition record | 99A | | `checklist/CompliancePackDefinition.java`. Record DTO for pack.json deserialization. Fields per Section 13.5.2: packId, version, name, description, jurisdiction, customerType, checklistTemplate (nested), fieldDefinitions (optional), retentionOverrides (optional). Pattern: follow existing DTO records. |
| 99.2 | Create CompliancePackSeeder service | 99A | | `checklist/CompliancePackSeeder.java`. @Service. Method: `void seedPacksForTenant(String tenantId)`. Load classpath packs, check idempotency via OrgSettings.compliancePackStatus, create templates + items, delegate field definitions to FieldPackSeeder, create retention overrides, update OrgSettings. Pattern: follow FieldPackSeeder from Phase 11. |
| 99.3 | Create generic-onboarding pack.json | 99A | | `src/main/resources/compliance-packs/generic-onboarding/pack.json`. 4 items: verify contact details, upload engagement letter, confirm terms of service, set up billing preferences. Active by default. |
| 99.4 | Create sa-fica-individual pack.json | 99A | | `src/main/resources/compliance-packs/sa-fica-individual/pack.json`. 5 items: verify identity document, verify residential address, complete risk assessment, verify source of funds, sanctions and PEP screening. 3 field definitions: sa_id_number, passport_number, risk_rating. Inactive by default. |
| 99.5 | Create sa-fica-company pack.json | 99A | | `src/main/resources/compliance-packs/sa-fica-company/pack.json`. 6 items: verify CIPC registration, verify registered address, identify directors/trustees, verify beneficial ownership, complete risk assessment, verify source of funds. 2 field definitions: company_registration_number, entity_type. Inactive by default. |
| 99.6 | Integrate seeder with provisioning | 99A | | Modify `provisioning/TenantProvisioningService.java`. Call `compliancePackSeeder.seedPacksForTenant(tenantId)` after schema creation. Also seed 2 default retention policies (CUSTOMER 1825 days, AUDIT_EVENT 2555 days). |
| 99.7 | Add pack loading integration tests | 99A | | `checklist/CompliancePackSeederTest.java` (~5 tests): finds 3 packs, creates templates, generic-onboarding active, sa-fica inactive, idempotent re-seeding. |
| 99.8 | Add field definition delegation tests | 99A | | Test: sa-fica-individual pack creates 3 field definitions. Pattern: integration test. |
| 99.9 | Add retention override tests | 99A | | Test: pack with retentionOverrides creates RetentionPolicy record. |
| 99.10 | Add default retention policy tests | 99A | | `retention/DefaultRetentionPolicyTest.java` (~2 tests): provisioning seeds 2 defaults (CUSTOMER 1825, AUDIT_EVENT 2555). |
| 99.11 | Add pack activation UI | 99A | | Modify `app/(app)/org/[slug]/settings/checklists/page.tsx`. Add "Compliance Packs" section. List platform templates with active toggle. |
| 99.12 | Verify pack seeder complete | 99A | | Run `./mvnw clean test`. Verify pack seeding in new tenant. |

### Key Files

**Slice 99A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/CompliancePackDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/CompliancePackSeeder.java`
- `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`
- `backend/src/main/resources/compliance-packs/sa-fica-individual/pack.json`
- `backend/src/main/resources/compliance-packs/sa-fica-company/pack.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/checklist/CompliancePackSeederTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/retention/DefaultRetentionPolicyTest.java`

**Slice 99A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`
- `frontend/app/(app)/org/[slug]/settings/checklists/page.tsx`

### Architecture Decisions

- **Classpath resource packs**: Stored in `src/main/resources/compliance-packs/*/pack.json`. Seeded during provisioning. Per ADR-063.
- **Idempotent seeding**: OrgSettings.compliancePackStatus tracks applied packs. Same pattern as field_pack_status from Phase 11.
- **Field definition delegation**: CompliancePackSeeder delegates to FieldPackSeeder to avoid duplicating field seeding logic.
- **Generic pack active by default**: `generic-onboarding` created active. Jurisdiction packs created inactive — admin activates in settings.
- **Default retention policies**: 2 policies seeded during provisioning.

---

## Epic 100: Data Subject Requests

**Goal**: Implement data subject request intake, tracking, and fulfillment — DataSubjectRequestService CRUD + status transitions, DataExportService (data gathering + ZIP + S3), DataAnonymizationService (PII anonymization + document deletion), controller endpoints, frontend request list + detail + create/execute dialogs. Fully tested (backend logic, S3 integration, frontend).

**References**: Architecture doc Sections 13.6 (data subject request handling), 13.8.4 (endpoints), 13.9.3-13.9.4 (sequence diagrams), 13.13.4 (permissions).

**Dependencies**: Epic 96 (DataSubjectRequest entity)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: L (complex service logic + S3 + anonymization + frontend + ~28 tests)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **100A** | 100.1-100.12 | DataSubjectRequestService CRUD + status transitions, DataExportService (data gathering, ZIP, S3), controller, integration tests (~15 backend tests) | |
| **100B** | 100.13-100.20 | DataAnonymizationService (PII anonymization, document deletion, confirmation validation), frontend request list/detail/create/execute, server actions, frontend tests (~13 tests: 8 backend + 5 frontend) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 100.1 | Create DataSubjectRequestService | 100A | | `datarequest/DataSubjectRequestService.java`. @Service. Methods: `List<DataSubjectRequest> listAll()`, `DataSubjectRequest create(CreateRequestRequest req)` — calculate deadline, save, emit audit event. `DataSubjectRequest transitionStatus(UUID id, String newStatus)` — validate transition flow (RECEIVED → IN_PROGRESS/REJECTED, IN_PROGRESS → COMPLETED/REJECTED). Pattern: follow InvoiceService. |
| 100.2 | Add request queries to repository | 100A | | Extend `DataSubjectRequestRepository.java`. Add JPQL queries for ordered listing and status filtering. |
| 100.3 | Create DataExportService | 100A | | `datarequest/DataExportService.java`. @Service. Method: `String generateExport(UUID customerId)`. Gather all customer-scoped data (profile, projects, documents, invoices, time entries, comments). Serialize to JSON + CSV. Download documents from S3. Package into ZIP. Upload to S3. Return S3 key. Pattern: complex data pipeline. |
| 100.4 | Create DataSubjectRequestController | 100A | | `datarequest/DataSubjectRequestController.java`. @RestController, @RequestMapping("/api/data-requests"). Endpoints: GET (list), GET /{id}, POST (create), PUT /{id}/status, POST /{id}/export, GET /{id}/export/download. @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')"). |
| 100.5 | Add data export integration tests | 100A | | `datarequest/DataExportServiceTest.java` (~5 tests): ZIP creation, includes invoices, includes documents, uploads to S3, minimal ZIP for empty customer. |
| 100.6 | Add data request CRUD integration tests | 100A | | `datarequest/DataSubjectRequestIntegrationTest.java` (~5 tests): create with deadline calculation, valid transitions, invalid transitions, rejection with reason, ordered listing. |
| 100.7 | Add data request permission tests | 100A | | `datarequest/DataSubjectRequestPermissionTest.java` (~2 tests): admin allowed, member blocked. |
| 100.8 | Add export download endpoint test | 100A | | Test: GET /export/download returns presigned S3 URL. |
| 100.9 | Add deadline calculation test | 100A | | Test: deadline = requestedAt + dataRequestDeadlineDays from OrgSettings. |
| 100.10 | Placeholder for deadline notification | 100A | | Test: @Disabled placeholder for deadline-approaching notification (future enhancement). |
| 100.11 | Verify data export S3 integration | 100A | | Run integration test against LocalStack. Verify ZIP upload/download. |
| 100.12 | Verify data request backend complete | 100A | | Run `./mvnw clean test`. |
| 100.13 | Create DataAnonymizationService | 100B | | `datarequest/DataAnonymizationService.java`. @Service. Method: `void executeAnonymization(UUID customerId, String confirmCustomerName)`. Validate name confirmation. Anonymize customer PII. Delete S3 documents. Anonymize comments and portal contacts. Set lifecycle_status=OFFBOARDED. Emit audit events. Per ADR-062. |
| 100.14 | Add execute-deletion endpoint | 100B | | Extend controller. POST /data-requests/{id}/execute-deletion (body: {confirmCustomerName}). Validate requestType=DELETION AND status=IN_PROGRESS. |
| 100.15 | Add anonymization integration tests | 100B | | `datarequest/DataAnonymizationServiceTest.java` (~5 tests): name anonymized, PII nulled, documents deleted, comments/contacts anonymized, confirmation mismatch rejected. |
| 100.16 | Add execute-deletion endpoint tests | 100B | | `datarequest/DataDeletionExecutionTest.java` (~3 tests): correct name succeeds, wrong name returns 400, ACCESS request returns 400. |
| 100.17 | Create frontend data request pages | 100B | | `app/(app)/org/[slug]/compliance/requests/page.tsx` (list). `app/(app)/org/[slug]/compliance/requests/[id]/page.tsx` (detail). |
| 100.18 | Create request dialogs | 100B | | `components/compliance/CreateDataRequestDialog.tsx` (customer select, type, description). `components/compliance/ExecuteDeletionDialog.tsx` (name confirmation). |
| 100.19 | Create data request server actions | 100B | | `lib/actions/data-requests.ts`. Functions: create, transitionStatus, generateExport, downloadExport, executeDeletion. |
| 100.20 | Add data request frontend tests | 100B | | `components/compliance/ExecuteDeletionDialog.test.tsx` (~3 tests), `components/compliance/CreateDataRequestDialog.test.tsx` (~2 tests). |

### Key Files

**Slice 100A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestPermissionTest.java`

**Slice 100A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestRepository.java`

**Slice 100B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataDeletionExecutionTest.java`
- `frontend/app/(app)/org/[slug]/compliance/requests/page.tsx`
- `frontend/app/(app)/org/[slug]/compliance/requests/[id]/page.tsx`
- `frontend/components/compliance/CreateDataRequestDialog.tsx`
- `frontend/components/compliance/ExecuteDeletionDialog.tsx`
- `frontend/lib/actions/data-requests.ts`
- `frontend/components/compliance/ExecuteDeletionDialog.test.tsx`
- `frontend/components/compliance/CreateDataRequestDialog.test.tsx`

**Slice 100B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestController.java`

### Architecture Decisions

- **Anonymization over hard deletion**: Per ADR-062, DELETION requests anonymize PII but retain financial records.
- **Confirmation requirement**: Execute deletion requires exact customer name match.
- **Data export format**: ZIP containing JSON + CSV + documents/ folder.
- **S3 integration**: Export ZIP uploaded under `exports/` prefix. Download via presigned URL.
- **Status flow**: RECEIVED → IN_PROGRESS → COMPLETED/REJECTED.
- **Permissions**: Admin/owner only.

---

## Epic 101: Retention Policies

**Goal**: Implement retention policy CRUD, retention check (admin-triggered query), purge execution (flag-then-purge workflow), frontend settings page. Fully tested (backend logic, frontend).

**References**: Architecture doc Sections 13.7 (retention policies), 13.8.5 (endpoints), 13.13.5 (permissions).

**Dependencies**: Epic 96 (RetentionPolicy entity)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: M (service + controller + frontend + ~17 tests)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **101A** | 101.1-101.12 | RetentionPolicyService CRUD + retention check + purge, RetentionPolicyController, frontend settings page + check/purge UI, integration tests (~12 backend + 5 frontend tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 101.1 | Create RetentionPolicyService | 101A | | `retention/RetentionPolicyService.java`. @Service. Methods: `List<RetentionPolicy> listAll()`, `create(req)`, `update(id, req)`, `delete(id)`. `Map<String, Object> runRetentionCheck()` — for each active policy, query records past retention period. `Map<String, Object> executePurge(recordType, recordIds, reason)` — type-specific purge (CUSTOMER → DataAnonymizationService, AUDIT_EVENT → hard delete). Emit audit events. |
| 101.2 | Add policy queries to repository | 101A | | Extend `RetentionPolicyRepository.java`. Add JPQL queries for active policies and uniqueness check. |
| 101.3 | Create RetentionPolicyController | 101A | | `retention/RetentionPolicyController.java`. @RestController, @RequestMapping("/api/retention-policies"). Endpoints: GET (list), POST (create), PUT /{id} (update), DELETE /{id}, POST /check, POST /purge. @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')"). |
| 101.4 | Add retention check integration tests | 101A | | `retention/RetentionCheckIntegrationTest.java` (~5 tests): customer past retention flagged, customer within retention not flagged, correct counts, audit events flagged, no active policies returns empty. |
| 101.5 | Add purge execution integration tests | 101A | | `retention/RetentionPurgeIntegrationTest.java` (~4 tests): purge CUSTOMER calls anonymization, purge AUDIT_EVENT hard deletes, purge emits audit, reason recorded. |
| 101.6 | Add policy CRUD integration tests | 101A | | `retention/RetentionPolicyIntegrationTest.java` (~3 tests): create, duplicate rejected (409), delete removes. |
| 101.7 | Verify retention backend complete | 101A | | Run `./mvnw clean test`. |
| 101.8 | Create RetentionPolicyTable component | 101A | | `components/settings/RetentionPolicyTable.tsx`. Table with CRUD actions, "New Policy" button. |
| 101.9 | Create retention policy dialogs | 101A | | `components/settings/CreatePolicyDialog.tsx`, `components/settings/EditPolicyDialog.tsx`. |
| 101.10 | Create retention check/purge components | 101A | | `components/compliance/RetentionCheckButton.tsx`, `components/compliance/RetentionPurgeDialog.tsx`. |
| 101.11 | Create retention server actions | 101A | | `lib/actions/retention.ts`. Functions: createPolicy, updatePolicy, deletePolicy, runRetentionCheck, executePurge. |
| 101.12 | Create retention settings page | 101A | | `app/(app)/org/[slug]/settings/compliance/page.tsx`. RetentionPolicyTable + RetentionCheckButton. Add "Compliance" to settings sidebar nav. Frontend tests (~5 tests). |

### Key Files

**Slice 101A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicyService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicyController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/retention/RetentionCheckIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPurgeIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicyIntegrationTest.java`
- `frontend/components/settings/RetentionPolicyTable.tsx`
- `frontend/components/settings/CreatePolicyDialog.tsx`
- `frontend/components/settings/EditPolicyDialog.tsx`
- `frontend/components/compliance/RetentionCheckButton.tsx`
- `frontend/components/compliance/RetentionPurgeDialog.tsx`
- `frontend/app/(app)/org/[slug]/settings/compliance/page.tsx`
- `frontend/lib/actions/retention.ts`

**Slice 101A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicyRepository.java`

### Architecture Decisions

- **Flag-then-purge workflow**: Retention check identifies, admin reviews, admin confirms purge.
- **No automatic purge**: Admin-triggered only in v1.
- **Type-specific purge logic**: CUSTOMER → anonymization, AUDIT_EVENT → hard delete.
- **Default policies seeded in Epic 99**: 2 policies during provisioning.
- **Permissions**: Admin/owner only.

---

## Epic 102: Compliance Dashboard

**Goal**: Build compliance dashboard frontend page — overview cards (status distribution, onboarding pipeline, open requests, retention flags), onboarding pipeline widget, data requests widget, retention flags widget, dormancy check button. Integrate with sidebar nav (admin/owner only). Fully tested (frontend components).

**References**: Architecture doc Section 13.12.2 (frontend changes). All backend endpoints from Epics 97, 98, 100, 101.

**Dependencies**: Epics 97, 98, 100, 101 (all backend endpoints must exist)

**Scope**: Frontend

**Estimated Effort**: S (dashboard assembly + nav integration + ~10 tests)

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **102A** | 102.1-102.10 | ComplianceDashboard page, OnboardingPipeline widget, DataRequestList widget, RetentionFlagList widget, DormancyCheckButton integration, sidebar nav, frontend tests (~10 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 102.1 | Create ComplianceDashboard page | 102A | | `app/(app)/org/[slug]/compliance/page.tsx`. Overview cards: customers by status, onboarding count, open requests, retention flags. Widgets: OnboardingPipeline, DataRequestList, RetentionFlagList. Admin/owner only. |
| 102.2 | Create OnboardingPipeline widget | 102A | | `components/compliance/OnboardingPipeline.tsx`. Customers in ONBOARDING with checklist progress. Sort by started date. Limit 10. "View All" link. |
| 102.3 | Create DataRequestList widget | 102A | | `components/compliance/DataRequestList.tsx`. Open requests with deadline countdown. Sort by deadline ASC. Limit 5. "View All" link. |
| 102.4 | Create RetentionFlagList widget | 102A | | `components/compliance/RetentionFlagList.tsx`. Results of retention check grouped by record type. "Run Check" + "Purge" buttons. |
| 102.5 | Integrate DormancyCheckButton on dashboard | 102A | | Add DormancyCheckButton (from Epic 97) to compliance dashboard. |
| 102.6 | Add "Compliance" link to sidebar nav | 102A | | Modify sidebar nav. Add "Compliance" link (ShieldCheck icon), admin/owner only. |
| 102.7 | Add compliance dashboard tests | 102A | | `components/compliance/ComplianceDashboard.test.tsx` (~5 tests): renders cards, pipeline, requests, flags, permission guard. |
| 102.8 | Add OnboardingPipeline widget tests | 102A | | `components/compliance/OnboardingPipeline.test.tsx` (~2 tests): renders customers, progress bar. |
| 102.9 | Add DataRequestList widget tests | 102A | | `components/compliance/DataRequestList.test.tsx` (~2 tests): renders requests, overdue badge. |
| 102.10 | Verify compliance dashboard complete | 102A | | Run `pnpm test`. |

### Key Files

**Slice 102A — Create:**
- `frontend/app/(app)/org/[slug]/compliance/page.tsx`
- `frontend/components/compliance/ComplianceDashboard.tsx`
- `frontend/components/compliance/OnboardingPipeline.tsx`
- `frontend/components/compliance/DataRequestList.tsx`
- `frontend/components/compliance/RetentionFlagList.tsx`
- `frontend/components/compliance/ComplianceDashboard.test.tsx`
- `frontend/components/compliance/OnboardingPipeline.test.tsx`
- `frontend/components/compliance/DataRequestList.test.tsx`

**Slice 102A — Modify:**
- Sidebar navigation component

### Architecture Decisions

- **Dashboard is assembly, not new logic**: All data from existing endpoints.
- **Admin/owner only**: Sidebar link + page both gated.
- **Widgets are independent**: Can be developed/tested independently.
- **No real-time updates**: Fetched on page load. User refreshes for updates.
