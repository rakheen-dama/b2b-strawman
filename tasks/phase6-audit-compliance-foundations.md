# Phase 6 -- Audit & Compliance Foundations

Phase 6 adds **backend-only audit trail and compliance infrastructure** to the platform. It introduces a generic `AuditEvent` entity, a service abstraction for recording events, and a retention/integrity strategy -- all scoped within the existing multi-tenant model. No new UI is added in this phase; the infrastructure is designed for future dashboards, SIEM integration, and compliance reporting. See `phase6-audit-compliance-foundations.md` (Section 12 of ARCHITECTURE.md) and [ADR-025](../adr/ADR-025-audit-storage-location.md)--[ADR-029](../adr/ADR-029-audit-logging-abstraction.md) for design details.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 50 | Audit Infrastructure -- Entity, Service & Migration | Backend | -- | M | 50A, 50B | |
| 51 | Domain Event Integration -- Services | Backend | 50 | L | 51A, 51B | |
| 52 | Security Event Integration | Backend | 50 | S | 52A | |
| 53 | Audit Query API | Backend | 50 | M | 53A, 53B | |

## Dependency Graph

```
[E50 Audit Infrastructure] ──────────┬──► [E51 Domain Event Integration]
                                      ├──► [E52 Security Event Integration]
                                      └──► [E53 Audit Query API]
```

**Parallel tracks**: After Epic 50 (Audit Infrastructure) lands, Epics 51, 52, and 53 can all begin in parallel -- they have zero dependency on each other. Epic 51 modifies domain services. Epic 52 modifies the exception handler and security configuration. Epic 53 creates new controllers. None of these overlap in the files they touch.

## Implementation Order

### Stage 1: Backend Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 50: Audit Infrastructure | V14 migration + entity + service + builder is the prerequisite for all other Phase 6 work. |

### Stage 2: Event Integration + Query API (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 51: Domain Event Integration | Depends on `AuditService` from Epic 50. Modifies 8 existing service files. |
| 2b | Epic 52: Security Event Integration | Depends on `AuditService` from Epic 50. Modifies exception handler and security config. |
| 2c | Epic 53: Audit Query API | Depends on `AuditEventRepository` and `DatabaseAuditService` from Epic 50. Creates new controllers. |

### Timeline

```
Stage 1:  [E50]                       <- foundation (must complete first)
Stage 2:  [E51] [E52] [E53]          <- parallel (after E50)
```

---

## Epic 50: Audit Infrastructure -- Entity, Service & Migration

**Goal**: Create the `audit_events` table, implement the `AuditEvent` entity with the standard `@FilterDef`/`@Filter`/`TenantAware` pattern, build the `AuditService` interface and `DatabaseAuditService` implementation, the `AuditEventRecord` DTO, the `AuditEventBuilder` convenience builder, and the `AuditEventFilter` query record. Includes tenant isolation verification for both Pro (dedicated schema) and Starter (shared schema + `@Filter`), and append-only integrity verification (update trigger enforcement).

**References**: [ADR-025](../adr/ADR-025-audit-storage-location.md), [ADR-026](../adr/ADR-026-audit-event-granularity.md), [ADR-028](../adr/ADR-028-audit-integrity-approach.md), [ADR-029](../adr/ADR-029-audit-logging-abstraction.md), `phase6-audit-compliance-foundations.md` Sections 12.1.1, 12.2, 12.4, 12.5.2, 12.8, 12.9.2--12.9.3

**Dependencies**: None (builds on existing multi-tenant infrastructure)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **50A** | 50.1--50.5 | V14 migration, AuditEvent entity, AuditEventRepository, AuditEventRecord + AuditEventFilter records, AuditEventBuilder | **Done** (PR #100) |
| **50B** | 50.6--50.10 | AuditService interface, DatabaseAuditService implementation, integration tests (log + query + tenant isolation + append-only enforcement), retention configuration properties | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 50.1 | Create V14 tenant migration for audit_events table | 50A | **Done** | `db/migration/tenant/V14__create_audit_events.sql`. Columns per Section 12.1.1: `id` (UUID PK DEFAULT gen_random_uuid()), `event_type` (VARCHAR(100) NOT NULL), `entity_type` (VARCHAR(50) NOT NULL), `entity_id` (UUID NOT NULL), `actor_id` (UUID, nullable), `actor_type` (VARCHAR(20) NOT NULL DEFAULT 'USER'), `source` (VARCHAR(30) NOT NULL), `ip_address` (VARCHAR(45), nullable), `user_agent` (VARCHAR(500), nullable), `details` (JSONB, nullable), `tenant_id` (VARCHAR(255)), `occurred_at` (TIMESTAMPTZ NOT NULL). Indexes: `idx_audit_entity (entity_type, entity_id)`, `idx_audit_actor (actor_id) WHERE actor_id IS NOT NULL`, `idx_audit_occurred (occurred_at DESC)`, `idx_audit_type_time (event_type, occurred_at DESC)`, `idx_audit_tenant_time (tenant_id, occurred_at DESC)`. Append-only trigger: `prevent_audit_update()` function + `audit_events_no_update` BEFORE UPDATE trigger. RLS: `ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY; CREATE POLICY audit_events_tenant_isolation ON audit_events USING (tenant_id = current_setting('app.current_tenant', true))`. Pattern: follow `V13__create_time_entries.sql` structure (RLS policy, indexes, tenant_id column). Note: NO foreign keys to any other table -- entity_id is stored as-is. No `updated_at` column. No `created_at` -- use `occurred_at` only. |
| 50.2 | Create AuditEvent entity | 50A | **Done** | `audit/AuditEvent.java` -- JPA entity mapped to `audit_events`. Fields: UUID id, String eventType (VARCHAR(100), NOT NULL), String entityType (VARCHAR(50), NOT NULL), UUID entityId (NOT NULL), UUID actorId (nullable), String actorType (VARCHAR(20), NOT NULL, default "USER"), String source (VARCHAR(30), NOT NULL), String ipAddress (VARCHAR(45), nullable), String userAgent (VARCHAR(500), nullable), `Map<String, Object> details` (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`, nullable), String tenantId, Instant occurredAt (NOT NULL, updatable=false). Annotations: `@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))`, `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Constructor takes `AuditEventRecord` and sets all fields + `occurredAt = Instant.now()`. **Key differences from other entities**: no `updatedAt`, no `@Version`, no setters except `setTenantId()`. Getters only. Protected no-arg constructor for JPA. Pattern: follow `timeentry/TimeEntry.java` entity structure but with immutability constraints per Section 12.9.2. |
| 50.3 | Create AuditEventRepository | 50A | **Done** | `audit/AuditEventRepository.java` -- extends `JpaRepository<AuditEvent, UUID>`. Methods: `Optional<AuditEvent> findOneById(UUID id)` (JPQL `@Query` for `@Filter` compatibility -- CRITICAL, do NOT use `findById()`), `Page<AuditEvent> findByFilter(String entityType, UUID entityId, UUID actorId, String eventTypePrefix, Instant from, Instant to, Pageable pageable)` (multi-parameter JPQL query with nullable filters, ORDER BY occurredAt DESC -- see Section 12.9.3), `List<Object[]> countByEventType()` (GROUP BY eventType, ORDER BY COUNT DESC -- for stats endpoint). Pattern: follow `timeentry/TimeEntryRepository.java` with JPQL `findOneById`. The `findByFilter` method uses nullable parameter pattern: `(:param IS NULL OR e.field = :param)` for each filter. For `eventTypePrefix`, use `LIKE CONCAT(:eventTypePrefix, '%')`. |
| 50.4 | Create AuditEventRecord and AuditEventFilter records | 50A | **Done** | `audit/AuditEventRecord.java` -- Java record: `AuditEventRecord(String eventType, String entityType, UUID entityId, UUID actorId, String actorType, String source, String ipAddress, String userAgent, Map<String, Object> details)`. This is the non-JPA DTO passed to `AuditService.log()`. `audit/AuditEventFilter.java` -- Java record: `AuditEventFilter(String entityType, UUID entityId, UUID actorId, String eventType, Instant from, Instant to)`. Used by `AuditService.findEvents()` for query filtering. All fields nullable. Pattern: follow existing DTO record pattern (e.g., `ProvisioningController.ProvisioningRequest`). |
| 50.5 | Create AuditEventBuilder | 50A | **Done** | `audit/AuditEventBuilder.java` -- Builder class that constructs an `AuditEventRecord`. Required fields: `eventType`, `entityType`, `entityId`. Optional fields: `details(Map<String, Object>)`, `actorId(UUID)`, `actorType(String)`, `source(String)`. The `build()` method auto-populates: (1) `actorId` from `RequestScopes.MEMBER_ID` if bound and not explicitly set, (2) `actorType` = "USER" if `MEMBER_ID` bound, "SYSTEM" otherwise (unless explicitly set), (3) `source` = "API" if `RequestContextHolder.getRequestAttributes()` is non-null, "INTERNAL" otherwise (unless explicitly set), (4) `ipAddress` from `HttpServletRequest.getRemoteAddr()` via `RequestContextHolder` (null if not in HTTP context), (5) `userAgent` from `HttpServletRequest.getHeader("User-Agent")` truncated to 500 chars (null if not in HTTP context). Uses `RequestContextHolder.getRequestAttributes()` cast to `ServletRequestAttributes` to get `HttpServletRequest`. Static `builder()` factory method. Pattern: standard Java builder (no Lombok). |
| 50.6 | Create AuditService interface | 50B | | `audit/AuditService.java` -- Interface with two methods: `void log(AuditEventRecord event)` (records a single audit event within the current transaction) and `Page<AuditEvent> findEvents(AuditEventFilter filter, Pageable pageable)` (paginated query, scoped to current tenant via Hibernate `@Filter`). |
| 50.7 | Create DatabaseAuditService implementation | 50B | | `audit/DatabaseAuditService.java` -- `@Service` implementing `AuditService`. Constructor injection of `AuditEventRepository`. `log()`: creates new `AuditEvent(record)`, calls `repository.save()`. `findEvents()`: delegates to `repository.findByFilter()`, mapping `AuditEventFilter` fields to repository parameters. Both methods `@Transactional`. The `log()` method participates in the caller's transaction (no `REQUIRES_NEW` -- rollback of domain operation also rolls back audit event). Pattern: follow `TimeEntryService.java` structure for constructor injection and `@Transactional` usage. |
| 50.8 | Add DatabaseAuditService integration tests (log + query) | 50B | | `audit/AuditServiceIntegrationTest.java`. ~10 tests: log an event and verify it's persisted, log event with null details, log event with JSONB details map, query events by entityType, query events by entityId, query events by actorId, query events by eventType prefix, query events by time range (from/to), query with pagination (page 0 size 2 of 5 events), verify `countByEventType()` returns correct counts. Seed test org/tenant in `@BeforeAll` with Pro tier (follow `TimeEntryIntegrationTest.java` pattern). Use `ScopedValue.where(RequestScopes.TENANT_ID, schema)` for tenant context. |
| 50.9 | Add tenant isolation + append-only integration tests | 50B | | `audit/AuditTenantIsolationTest.java`. ~5 tests: (1) Provision two Pro orgs, log events in each, verify org A cannot see org B's events. (2) Provision two Starter orgs (shared schema), log events in each, verify tenant_id isolation via `@Filter`. (3) Verify `tenant_id` is auto-populated by `TenantAwareEntityListener`. (4) Verify UPDATE on audit_events raises `DataIntegrityViolationException` (trigger enforcement). (5) Verify AuditEvent has no setter methods for mutable fields (entity immutability -- can be a unit test). Pattern: follow `multitenancy/StarterTenantIntegrationTest.java` for shared-schema isolation, `multitenancy/MixedTenantIntegrationTest.java` for cross-tier verification. |
| 50.10 | Add audit retention configuration properties | 50B | | Add to `application.yml` (all profiles): `audit.retention.domain-events-days: 1095`, `audit.retention.security-events-days: 365`, `audit.retention.purge-enabled: false`. Create `audit/AuditRetentionProperties.java` -- `@ConfigurationProperties(prefix = "audit.retention")` record with fields `int domainEventsDays` (default 1095), `int securityEventsDays` (default 365), `boolean purgeEnabled` (default false). Register via `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`. The scheduled purge job is NOT implemented in Phase 6 -- only the configuration is defined. Pattern: follow existing `@ConfigurationProperties` usage in the codebase. |

### Key Files

**Slice 50A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V14__create_audit_events.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRecord.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventFilter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java`

**Slice 50A -- Read for context:**
- `backend/src/main/resources/db/migration/tenant/V13__create_time_entries.sql` -- migration pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` -- entity pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` -- JPQL findOneById pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` -- interface to implement
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` -- entity listener
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- ScopedValue context for AuditEventBuilder

**Slice 50B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditRetentionProperties.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditServiceIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditTenantIsolationTest.java`

**Slice 50B -- Modify:**
- `backend/src/main/resources/application.yml` -- Add `audit.retention.*` properties

**Slice 50B -- Read for context:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java` -- test setup pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/StarterTenantIntegrationTest.java` -- shared schema test pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/MixedTenantIntegrationTest.java` -- cross-tier test pattern

### Architecture Decisions

- **`audit/` package**: New feature package following existing `task/`, `customer/`, `project/`, `timeentry/` pattern.
- **No `@Version`**: Audit events are write-once, never updated. No optimistic locking needed.
- **No `updated_at`**: Events are immutable. Only `occurred_at` is recorded (application timestamp, not DB default).
- **No FK to audited entities**: `entity_id` is a UUID stored as-is. Audit records survive entity deletion.
- **JPQL `findOneById`**: Required because `JpaRepository.findById()` uses `EntityManager.find()` which bypasses Hibernate `@Filter`. Same pattern as Task, Customer, TimeEntry.
- **Same-transaction semantics**: `AuditService.log()` participates in the caller's transaction. If the domain operation rolls back, the audit event rolls back too.
- **Two-slice decomposition**: 50A (migration + entity + records + builder) creates the data model. 50B (service + tests + config) layers on the service implementation and verifies correctness. Each slice touches 6-7 files.

---

## Epic 51: Domain Event Integration -- Services

**Goal**: Add `AuditService` injection and `auditService.log()` calls to all service methods that perform mutating operations on tenant-scoped entities. After this epic, every create/update/delete/claim/release/archive/link/unlink/sync operation across all domain entities automatically produces an audit event with the correct `eventType`, `entityType`, `entityId`, and `details` JSONB.

**References**: [ADR-029](../adr/ADR-029-audit-logging-abstraction.md), `phase6-audit-compliance-foundations.md` Section 12.3.1, 12.3.3

**Dependencies**: Epic 50 (AuditService + AuditEventBuilder)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **51A** | 51.1--51.5 | Audit logging in ProjectService, CustomerService, CustomerProjectService, ProjectMemberService + integration tests | |
| **51B** | 51.6--51.10 | Audit logging in TaskService, TimeEntryService, DocumentService, MemberSyncService + integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 51.1 | Add audit logging to ProjectService | 51A | | Inject `AuditService` via constructor. Add `auditService.log()` calls to: `createProject()` -- event `project.created`, details `{"name": project.getName()}`, entity project.getId(); `updateProject()` -- event `project.updated`, details with changed fields (compare before/after for name, description -- capture `{"name": {"from": oldName, "to": newName}}` pattern); `deleteProject()` -- event `project.deleted`, details `{"name": project.getName()}`, entity project.getId(). Use `AuditEventBuilder.builder().eventType(...).entityType("project").entityId(...).details(...).build()` pattern. All audit calls placed AFTER successful mutation, within the same `@Transactional` method. Pattern: follow conceptual example in Section 12.3.3. ~15 lines added to service. |
| 51.2 | Add audit logging to CustomerService and CustomerProjectService | 51A | | **CustomerService**: Inject `AuditService`. Add calls for: `createCustomer()` -- `customer.created`, details `{"name", "email"}`; `updateCustomer()` -- `customer.updated`, details with changed fields only (compare old vs new for name, email, phone, status); `archiveCustomer()` -- `customer.archived`, no details needed. **CustomerProjectService**: Inject `AuditService`. Add calls for: `linkCustomerToProject()` -- `customer.linked`, entityType "customer", entityId customerId, details `{"project_id": projectId.toString()}`; `unlinkCustomerFromProject()` -- `customer.unlinked`, same pattern. ~25 lines added across both services. |
| 51.3 | Add audit logging to ProjectMemberService | 51A | | Inject `AuditService`. Add calls for: `addMember()` -- `project_member.added`, entityType "project_member", entityId projectMember.getId(), details `{"project_id": projectId.toString(), "member_id": memberId.toString(), "role": "contributor"}`; `removeMember()` -- `project_member.removed`, details `{"project_id": projectId.toString(), "member_id": memberId.toString()}`; `transferLead()` -- log TWO events: `project_member.role_changed` for the old lead (details `{"role": {"from": "lead", "to": "contributor"}}`) and `project_member.role_changed` for the new lead (details `{"role": {"from": "contributor", "to": "lead"}}`). ~20 lines added. |
| 51.4 | Add integration tests for ProjectService, CustomerService, CustomerProjectService, ProjectMemberService audit events | 51A | | `audit/ProjectServiceAuditTest.java` -- ~6 tests: `project.created` event has correct entityType/entityId/details, `project.updated` captures field changes, `project.deleted` captured. `audit/CustomerServiceAuditTest.java` -- ~6 tests: `customer.created`, `customer.updated` with field diff, `customer.archived`, `customer.linked` with project_id in details, `customer.unlinked`. `audit/ProjectMemberAuditTest.java` -- ~4 tests: `project_member.added` with role, `project_member.removed`, `transferLead` produces two `project_member.role_changed` events. Total ~16 tests. Each test invokes the service method, then queries `AuditEventRepository` to verify the expected event exists with correct fields. Use `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers. Seed test org/tenant/members/projects in `@BeforeAll`. Pattern: follow `TimeEntryIntegrationTest.java` for setup, but verify audit events after API calls via `mockMvc` then query repository directly. |
| 51.5 | Verify rollback semantics -- failed operation does not produce audit event | 51A | | Add to `ProjectServiceAuditTest.java` -- ~2 tests: (1) attempt to create a project that fails validation (e.g., null name) -- verify no `project.created` event in audit_events. (2) attempt to update a non-existent project (404) -- verify no `project.updated` event. This validates that audit events participate in the same transaction. |
| 51.6 | Add audit logging to TaskService | 51B | | Inject `AuditService`. Add calls for: `createTask()` -- `task.created`, details `{"title": task.getTitle(), "project_id": projectId.toString()}`; `updateTask()` -- `task.updated`, details with changed fields (compare before/after for title, description, status, priority, assigneeId, dueDate -- only include fields that actually changed); `deleteTask()` -- `task.deleted`, details `{"title": task.getTitle(), "project_id": task.getProjectId().toString()}`; `claimTask()` -- `task.claimed`, details `{"assignee_id": memberId.toString()}`; `releaseTask()` -- `task.released`, details `{"previous_assignee_id": previousAssigneeId.toString()}` (capture assignee before release). ~30 lines added. For `updateTask()`, save the old field values before calling `task.update()` so the diff can be computed. |
| 51.7 | Add audit logging to TimeEntryService | 51B | | Inject `AuditService`. Add calls for: `createTimeEntry()` -- `time_entry.created`, details `{"task_id": taskId.toString(), "duration_minutes": durationMinutes, "billable": billable}`; `updateTimeEntry()` -- `time_entry.updated`, details with changed fields only (compare before/after for date, durationMinutes, billable, rateCents, description); `deleteTimeEntry()` -- `time_entry.deleted`, details `{"task_id": entry.getTaskId().toString()}`. ~20 lines added. |
| 51.8 | Add audit logging to DocumentService | 51B | | Inject `AuditService`. Add calls for: `initiateUpload()` + `initiateOrgUpload()` + `initiateCustomerUpload()` -- `document.created`, details `{"scope": scope, "file_name": fileName}`; `confirmUpload()` -- `document.uploaded`, entity document.getId(); `cancelUpload()` (acts as delete in current code) -- `document.deleted`, details `{"file_name": document.getFileName()}`; `getPresignedDownloadUrl()` -- `document.accessed`, details `{"scope": document.getScope(), "file_name": document.getFileName()}`; `toggleVisibility()` -- `document.visibility_changed`, details `{"visibility": {"from": oldVisibility, "to": newVisibility}}`. ~30 lines added. Note: `getPresignedDownloadUrl()` is a read-only method but produces an audit event for compliance (document access tracking). |
| 51.9 | Add audit logging to MemberSyncService | 51B | | Inject `AuditService`. This service uses `ScopedValue.where().call()` + `TransactionTemplate` for internal endpoint calls. Add audit calls INSIDE the `TransactionTemplate.execute()` lambda (same transaction as the member operation). For `syncMember()`: if `created` (new member) -- `member.synced`, actorType "WEBHOOK", source "WEBHOOK", details `{"action": "added", "email": email}`; if existing member updated -- `member.role_changed` if orgRole changed, details `{"org_role": {"from": oldRole, "to": newRole}}`. For `deleteMember()` -- `member.removed`, actorType "WEBHOOK", source "WEBHOOK", details `{"email": clerkUserId}`. Note: `MemberSyncService` has no `RequestScopes.MEMBER_ID` bound (called from internal endpoints) -- use `AuditEventBuilder.builder().actorType("WEBHOOK").source("WEBHOOK").actorId(null)` to override defaults. ~20 lines added. |
| 51.10 | Add integration tests for TaskService, TimeEntryService, DocumentService, MemberSyncService audit events | 51B | | `audit/TaskServiceAuditTest.java` -- ~7 tests: `task.created`, `task.updated` with field diff, `task.deleted`, `task.claimed`, `task.released` with previous_assignee_id, verify updateTask with no actual changes produces no audit event or produces event with empty details. `audit/TimeEntryServiceAuditTest.java` -- ~4 tests: `time_entry.created`, `time_entry.updated`, `time_entry.deleted`. `audit/DocumentServiceAuditTest.java` -- ~5 tests: `document.created` (project scope), `document.uploaded`, `document.deleted`, `document.accessed`, `document.visibility_changed`. `audit/MemberSyncAuditTest.java` -- ~4 tests: `member.synced` for new member, `member.role_changed`, `member.removed`, verify actorType="WEBHOOK" and source="WEBHOOK". Total ~20 tests. Pattern: follow existing integration test patterns. For document tests, mock S3PresignedUrlService. |

### Key Files

**Slice 51A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` -- Add AuditService injection + log() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` -- Add AuditService injection + log() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectService.java` -- Add AuditService injection + log() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberService.java` -- Add AuditService injection + log() calls

**Slice 51A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/ProjectServiceAuditTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/CustomerServiceAuditTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/ProjectMemberAuditTest.java`

**Slice 51A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- Interface to call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- Builder to use
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectIntegrationTest.java` -- Existing test setup pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/CustomerIntegrationTest.java` -- Existing test setup pattern

**Slice 51B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- Add AuditService injection + log() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` -- Add AuditService injection + log() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` -- Add AuditService injection + log() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java` -- Add AuditService injection + log() calls

**Slice 51B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/TaskServiceAuditTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/TimeEntryServiceAuditTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/DocumentServiceAuditTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/MemberSyncAuditTest.java`

**Slice 51B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- Interface to call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- Builder to use
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskIntegrationTest.java` -- Existing task test setup
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java` -- Existing time entry test setup
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/document/DocumentIntegrationTest.java` -- Existing document test setup (S3 mocking)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncIntegrationTest.java` -- Existing member sync test setup

### Architecture Decisions

- **Two-slice split by service group**: Slice 51A covers Project/Customer/CustomerProject/ProjectMember (4 services, ~7 files modified/created). Slice 51B covers Task/TimeEntry/Document/MemberSync (4 services, ~8 files modified/created). This keeps each slice under 12 files.
- **Explicit `log()` calls**: Each service method explicitly calls `AuditService.log()` with the appropriate event details, per ADR-029. No AOP, no Hibernate listeners.
- **Delta capture for updates**: Update methods save old field values before mutation, then construct a `details` map with only the changed fields using `{"field": {"from": old, "to": new}}` format.
- **MemberSyncService special handling**: Uses explicit `actorType("WEBHOOK")` and `source("WEBHOOK")` because this service operates outside the normal HTTP request context -- no `MEMBER_ID` is bound.
- **Audit tests in `audit/` package**: Test files live in the `audit/` test package even though they test service methods in other packages. This groups all audit-related tests together and avoids cluttering existing test files.

---

## Epic 52: Security Event Integration

**Goal**: Add audit logging for security-relevant events: permission-denied responses (403), authentication failures (401), and sensitive document access (customer-scoped documents). After this epic, all security-relevant actions are captured in the audit trail for incident investigation and compliance.

**References**: `phase6-audit-compliance-foundations.md` Section 12.3.2, 12.3.3

**Dependencies**: Epic 50 (AuditService + AuditEventBuilder)

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **52A** | 52.1--52.5 | Security audit events in GlobalExceptionHandler (access denied), SecurityConfig (auth failed), DocumentService (sensitive doc access), integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 52.1 | Add access-denied audit logging to GlobalExceptionHandler | 52A | | Inject `AuditService` via constructor into `GlobalExceptionHandler`. Add a new `@ExceptionHandler` method for `org.springframework.security.access.AccessDeniedException` (thrown by Spring Security `@PreAuthorize` failures). In the handler: use `AuditEventBuilder.builder().eventType("security.access_denied").entityType("security").entityId(UUID.randomUUID())` (or a deterministic UUID -- since there's no specific entity, use a random one for uniqueness). Capture details from `HttpServletRequest` (inject via method parameter): `{"path": request.getRequestURI(), "method": request.getMethod(), "reason": "insufficient_role"}`. Also handle `ForbiddenException` (thrown by service-layer code) -- add audit logging in a new `@ExceptionHandler(ForbiddenException.class)` method or modify the existing exception flow. Note: `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` which already handles `ErrorResponseException` subtypes. For `ForbiddenException` (extends `ErrorResponseException`), add audit logging by overriding `handleErrorResponseException()` or adding a specific handler. Return 403 ProblemDetail after logging. ~15 lines added. |
| 52.2 | Add auth-failed audit logging via custom AuthenticationEntryPoint | 52A | | The codebase uses Spring Security's built-in `BearerTokenAuthenticationFilter` (no custom JwtAuthFilter). Auth failures (expired/invalid JWT) are handled by `BearerTokenAuthenticationEntryPoint`. To capture `security.auth_failed` events: create `audit/AuditAuthenticationEntryPoint.java` -- implements `AuthenticationEntryPoint`. Constructor injects `AuditService` and delegates to `BearerTokenAuthenticationEntryPoint`. On `commence()`: log `security.auth_failed` event with details `{"reason": authException.getMessage(), "path": request.getRequestURI()}`, entityType "security", actorId null, actorType "SYSTEM", source "API". Then delegate to `BearerTokenAuthenticationEntryPoint.commence()` for the actual 401 response. Note: this runs BEFORE tenant context is bound (auth failed = no valid JWT = no tenant), so the audit event needs special handling -- log to a best-effort approach. Since `RequestScopes.TENANT_ID` is not bound, the event will be saved without tenant context. This is acceptable for auth failures. However, consider logging auth failures to application logs only (not DB) if tenant context is required for DB write. **Alternative approach**: If DB write without tenant is problematic, use `log.warn()` for auth failures and skip DB persistence. The architecture doc says "captured in JwtAuthFilter when JWT validation fails" -- since there's no custom filter, the `AuthenticationEntryPoint` is the correct integration point. |
| 52.3 | Register AuditAuthenticationEntryPoint in SecurityConfig | 52A | | Modify `security/SecurityConfig.java`: inject `AuditAuthenticationEntryPoint` via constructor. In `securityFilterChain()`, add `.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)).authenticationEntryPoint(auditAuthEntryPoint))`. This replaces the default `BearerTokenAuthenticationEntryPoint` with our auditing wrapper. ~3 lines changed in SecurityConfig. |
| 52.4 | Add sensitive document access security event to DocumentService | 52A | | In `DocumentService.getPresignedDownloadUrl()`: after the existing `document.accessed` domain event from Epic 51, add a SECOND audit event for customer-scoped documents only: `if (document.getScope() == Document.Scope.CUSTOMER)` then log `security.document_accessed` with details `{"document_id": documentId.toString(), "scope": "CUSTOMER", "customer_id": document.getCustomerId().toString(), "file_name": document.getFileName()}`. This dual-logging (domain + security) enables separate monitoring on the `security.` prefix. ~5 lines added. |
| 52.5 | Add security audit integration tests | 52A | | `audit/SecurityAuditTest.java`. ~6 tests: (1) `security.access_denied` event captured when `@PreAuthorize` rejects a request (e.g., member calls DELETE on a project -- 403), verify event has path and method in details. (2) `security.access_denied` captured when `ForbiddenException` thrown by service code (e.g., contributor tries to delete another's time entry). (3) `security.auth_failed` captured on invalid JWT (test with missing/malformed Authorization header). (4) `security.auth_failed` not captured on valid JWT (no false positives). (5) `security.document_accessed` captured when downloading customer-scoped document. (6) `security.document_accessed` NOT captured when downloading project-scoped document (only customer scope triggers security event). Pattern: use MockMvc to make requests with various JWT configurations. For auth failure tests, use `mockMvc.perform(get("/api/projects").header("Authorization", "Bearer invalid_token"))`. Note: auth failure audit events may be logged to application logs rather than DB if tenant context is not available -- adjust test expectations accordingly. |

### Key Files

**Slice 52A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/GlobalExceptionHandler.java` -- Add AuditService injection + access-denied logging
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` -- Register AuditAuthenticationEntryPoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` -- Add security.document_accessed event for customer-scoped docs

**Slice 52A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditAuthenticationEntryPoint.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/SecurityAuditTest.java`

**Slice 52A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- Interface to call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- Builder to use
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ForbiddenException.java` -- Exception class to handle
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/security/SecurityIntegrationTest.java` -- Existing security test pattern

### Architecture Decisions

- **Single slice**: This epic touches only 3 existing files + creates 2 new files (5 total). Well within the 8-12 file limit for one slice.
- **Auth failure handling challenge**: The `BearerTokenAuthenticationFilter` runs BEFORE tenant context is bound. Auth failures therefore cannot be written to a tenant-scoped `audit_events` table. Two approaches: (a) Log to application logs only (`log.warn`) -- simple, no tenant needed. (b) Create a dedicated handler that writes to a fallback. The implementation should prefer option (a) for Phase 6 simplicity, with the `AuditAuthenticationEntryPoint` logging a structured warning that can be captured by CloudWatch/ELK in production.
- **Dual document access logging**: Customer-scoped document downloads produce both `document.accessed` (domain event, from Epic 51) and `security.document_accessed` (security event). This enables separate alert/monitoring on the `security.` prefix without losing the domain-level audit trail.
- **`AccessDeniedException` vs `ForbiddenException`**: Both produce 403 responses but are thrown from different layers. `AccessDeniedException` comes from Spring Security `@PreAuthorize` annotations. `ForbiddenException` comes from service-layer code (e.g., `ProjectAccessService`). Both should produce `security.access_denied` audit events.

---

## Epic 53: Audit Query API

**Goal**: Expose audit events via REST API endpoints. Tenant-scoped endpoints allow org owners and admins to query their organization's audit trail. Internal endpoints allow platform operators to query across tenants and view aggregate statistics. Includes proper role-based access control and pagination.

**References**: `phase6-audit-compliance-foundations.md` Section 12.6

**Dependencies**: Epic 50 (AuditService + AuditEventRepository + DatabaseAuditService)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **53A** | 53.1--53.4 | AuditEventController (tenant-scoped GET /api/audit-events endpoints), role-based access (owner/admin only), pagination, integration tests | |
| **53B** | 53.5--53.8 | InternalAuditController (GET /internal/audit-events, /stats), cross-tenant query via ScopedValue, SecurityConfig update, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 53.1 | Create AuditEventController with tenant-scoped list endpoint | 53A | | `audit/AuditEventController.java` -- `@RestController`. `GET /api/audit-events` -- accepts query parameters: `entityType` (String, optional), `entityId` (UUID, optional), `actorId` (UUID, optional), `eventType` (String, optional -- prefix match), `from` (Instant, optional), `to` (Instant, optional), `page` (int, default 0), `size` (int, default 50, max 200). `@PreAuthorize("hasAnyRole('ORG_OWNER', 'ORG_ADMIN')")` -- only owner and admin can view audit events (regular members denied). Constructs `AuditEventFilter` from query params, calls `auditService.findEvents(filter, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "occurredAt")))`. Returns `Page<AuditEventResponse>`. Response DTO `AuditEventResponse`: `UUID id, String eventType, String entityType, UUID entityId, UUID actorId, String actorType, String source, Map<String, Object> details, Instant occurredAt`. Note: `ip_address` and `user_agent` EXCLUDED from tenant-scoped response (PII minimization). Pattern: follow `TimeEntryController.java` for controller structure, `@PreAuthorize` pattern. |
| 53.2 | Add entity-specific audit endpoint | 53A | | Add to `AuditEventController.java`: `GET /api/audit-events/{entityType}/{entityId}` -- convenience endpoint that pre-fills `entityType` and `entityId` filters. Same `@PreAuthorize` as list endpoint. Accepts same additional query params (actorId, eventType, from, to, page, size). Constructs `AuditEventFilter` with `entityType` and `entityId` from path, delegates to same `auditService.findEvents()`. Returns `Page<AuditEventResponse>`. Pattern: similar to `GET /api/tasks/{taskId}/time-entries` nested endpoint pattern. |
| 53.3 | Create AuditEventResponse DTO | 53A | | Inner record in `AuditEventController` (following existing DTO pattern): `AuditEventResponse(UUID id, String eventType, String entityType, UUID entityId, UUID actorId, String actorType, String source, Map<String, Object> details, Instant occurredAt)` with `static from(AuditEvent event)` factory method. Deliberately excludes `ipAddress`, `userAgent`, and `tenantId` from the response. |
| 53.4 | Add tenant-scoped audit endpoint integration tests | 53A | | `audit/AuditEventControllerTest.java`. ~10 tests: (1) Owner can list audit events (200). (2) Admin can list audit events (200). (3) Regular member denied (403). (4) Filter by entityType returns only matching events. (5) Filter by entityId returns events for specific entity. (6) Filter by eventType prefix (e.g., "task." matches task.created, task.updated). (7) Filter by time range (from/to). (8) Pagination works (page 0, page 1 with size 2). (9) Entity-specific endpoint `GET /api/audit-events/task/{taskId}` returns correct events. (10) Response excludes ipAddress and userAgent fields. Seed test data: provision org, create project and task via service, which produces audit events (if Epic 51 is merged) or manually insert audit events via repository. Pattern: follow `TimeEntryIntegrationTest.java` for MockMvc setup, JWT mock helpers. |
| 53.5 | Create InternalAuditController with cross-tenant query endpoint | 53B | | `audit/InternalAuditController.java` -- `@RestController @RequestMapping("/internal/audit-events")`. `GET /internal/audit-events` -- accepts query parameters: `orgId` (String, required -- Clerk org ID), plus all filter params from tenant-scoped endpoint. Resolves schema via `OrgSchemaMappingRepository.findByClerkOrgId(orgId)`. Executes query within tenant context: `ScopedValue.where(RequestScopes.TENANT_ID, schema).call(() -> auditService.findEvents(filter, pageable))`. Returns `Page<InternalAuditEventResponse>` which INCLUDES `ipAddress` and `userAgent` (available to platform operators). Internal API key auth only (no JWT). Pattern: follow `provisioning/ProvisioningController.java` for internal endpoint pattern and `member/MemberSyncService.java` for `ScopedValue.where().call()` pattern. |
| 53.6 | Add stats endpoint to InternalAuditController | 53B | | Add to `InternalAuditController.java`: `GET /internal/audit-events/stats` -- accepts `orgId` (String, required). Resolves schema, executes within tenant context. Calls `auditEventRepository.countByEventType()`. Returns `List<EventTypeCount>` where `EventTypeCount(String eventType, long count)`. Also include `totalEvents` count. Pattern: simple aggregate endpoint similar to a dashboard summary. |
| 53.7 | Update SecurityConfig for audit endpoints | 53B | | Modify `security/SecurityConfig.java`: ensure `/api/audit-events/**` is covered by the existing `.requestMatchers("/api/**").authenticated()` rule (it already is). Ensure `/internal/audit-events/**` is covered by the existing `.requestMatchers("/internal/**").authenticated()` rule (it already is). **No changes needed** if the existing wildcard patterns cover the new paths. Verify this by reading the SecurityConfig -- the current config uses `/api/**` and `/internal/**` which already match. Add a comment noting audit endpoints if desired. This task may be a no-op verify step. |
| 53.8 | Add internal audit endpoint integration tests | 53B | | `audit/InternalAuditControllerTest.java`. ~8 tests: (1) Internal API key auth succeeds (200). (2) No API key returns 401. (3) Query with valid orgId returns audit events for that tenant. (4) Query with invalid orgId returns 404 or empty. (5) Stats endpoint returns event type counts. (6) Stats endpoint with no events returns empty list. (7) Pagination on internal endpoint. (8) InternalAuditEventResponse INCLUDES ipAddress and userAgent (unlike tenant-scoped endpoint). Seed test data by provisioning orgs and manually inserting audit events. Pattern: follow `provisioning/ProvisioningIntegrationTest.java` for internal API key test setup (`header("X-API-KEY", apiKey)`). |

### Key Files

**Slice 53A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventControllerTest.java`

**Slice 53A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- Interface to call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` -- Implementation details
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java` -- Controller pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- Org role check pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java` -- MockMvc test pattern

**Slice 53B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/InternalAuditController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/InternalAuditControllerTest.java`

**Slice 53B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` -- Verify audit endpoint coverage (likely no-op)

**Slice 53B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/ProvisioningController.java` -- Internal endpoint pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java` -- ScopedValue.where().call() pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` -- Schema resolution
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/ProvisioningIntegrationTest.java` -- Internal API key test pattern

### Architecture Decisions

- **Two-slice split**: 53A (tenant-scoped endpoints, 2 files created + tests) and 53B (internal endpoints + SecurityConfig, 2-3 files created/modified + tests). Each slice touches 5-7 files.
- **PII minimization on tenant endpoint**: `AuditEventResponse` excludes `ipAddress` and `userAgent`. `InternalAuditEventResponse` includes them. This is a deliberate privacy boundary -- org admins see what happened, platform operators see where it happened from.
- **Internal endpoint uses ScopedValue**: Cross-tenant queries resolve the schema from `orgId`, then bind `RequestScopes.TENANT_ID` via `ScopedValue.where()` to execute the query in the correct tenant context. This is the same pattern used by `MemberSyncService`.
- **SecurityConfig likely no-op**: The existing `/api/**` and `/internal/**` patterns already cover the new endpoints. Task 53.7 is a verification step to confirm this.
- **Page size capped at 200**: To prevent large result sets, the controller enforces `Math.min(size, 200)` on the page size parameter.

---

## Architecture Decisions

| ADR | Title | Epic |
|-----|-------|------|
| [ADR-025](../adr/ADR-025-audit-storage-location.md) | Audit Storage Location -- Per-tenant table following existing multi-tenant pattern | Epic 50 |
| [ADR-026](../adr/ADR-026-audit-event-granularity.md) | Audit Event Granularity -- Key-field delta capture with free-form string event types | Epics 50, 51 |
| [ADR-027](../adr/ADR-027-audit-retention-strategy.md) | Audit Retention Strategy -- Fixed configurable periods, purge job deferred | Epic 50 |
| [ADR-028](../adr/ADR-028-audit-integrity-approach.md) | Audit Integrity Approach -- App-level immutability + DB trigger, hash chain deferred | Epic 50 |
| [ADR-029](../adr/ADR-029-audit-logging-abstraction.md) | Audit Logging Abstraction -- Explicit `AuditService.log()` calls, no AOP | Epics 51, 52 |
