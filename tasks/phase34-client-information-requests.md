# Phase 34 -- Client Information Requests

Phase 34 introduces a Client Information Requests system that transforms the customer portal from a view-only surface into an interactive document collection platform. Firms create reusable request templates (e.g., "Annual Audit Document Pack"), send structured requests to portal contacts, and clients respond by uploading files or providing text. Firm members review each submission (accept/reject with reason), and automated reminders keep clients engaged. Integration with project templates enables auto-draft creation on project instantiation.

**Architecture doc**: `architecture/phase34-client-information-requests.md`

**ADRs**:
- [ADR-134](../adr/ADR-134-dedicated-entity-vs-checklist-extension.md) -- Dedicated Entity vs. Checklist Extension
- [ADR-135](../adr/ADR-135-reminder-strategy.md) -- Reminder Strategy (interval-based)
- [ADR-136](../adr/ADR-136-portal-upload-flow.md) -- Portal Upload Flow (presigned S3 URLs)
- [ADR-137](../adr/ADR-137-project-template-integration-scope.md) -- Project Template Integration Scope (draft-on-creation)

**Migrations**: V54, V55 (tenant schema), V13 (global/portal schema)

**Dependencies on prior phases**: Phase 7 (PortalContact, PortalJwtService, PortalEventHandler, PortalReadModelRepository), Phase 12 (TemplatePackSeeder pattern), Phase 16 (ProjectTemplate, ProjectInstantiationService), Phase 22 (Portal frontend app), Phase 24 (EmailNotificationChannel, Thymeleaf email templates), Phase 6 (AuditEventService), Phase 6.5 (NotificationService), Phase 8 (OrgSettings), Phase 9 (Dashboard).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 252 | RequestTemplate Entity Foundation & Pack Seeder | Backend | -- | M | 252A, 252B | **Done** (PRs #539, #540) |
| 253 | InformationRequest Entity & Lifecycle Backend | Backend | 252 | L | 253A, 253B | **Done** (PRs #541, #542) |
| 254 | Domain Events, Portal Read-Model Sync & Portal API | Backend | 253 | L | 254A, 254B | **Done** (PRs #543, #544) |
| 255 | Notifications, Audit & Reminder Scheduler | Backend | 253, 254 | M | 255A, 255B | **Done** (PRs #545, #546) |
| 256 | Project Template Integration & OrgSettings Extension | Backend | 253, 255 | M | 256A | **Done** (PR #547) |
| 257 | Request Template Management UI | Frontend | 252 | M | 257A | **Done** (PR #548) |
| 258 | Firm-Side Request Pages & Review UI | Frontend | 253, 257 | L | 258A, 258B | **Done** (PRs #549, #550) |
| 259 | Portal Request Pages (Upload & Submit) | Frontend | 254 | M | 259A, 259B | |
| 260 | Dashboard Widget, Settings & Template Editor Integration | Frontend | 255, 256, 258 | M | 260A, 260B | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────
[E252A V54 migration (partial):
 request_templates +
 request_template_items tables +
 request_counters table]
        |
[E252B RequestTemplate +
 RequestTemplateItem entities +
 RequestTemplateController CRUD +
 RequestPackSeeder + pack JSON +
 OrgSettings requestPackStatus +
 V55 migration (partial:
 request_pack_status column) +
 provisioning hook + tests]
        |
[E253A V54 migration (remainder):
 information_requests +
 request_items tables +
 InformationRequest +
 RequestItem + RequestCounter
 entities + repos +
 RequestNumberService + enums]
        |
[E253B InformationRequestService
 (create, send, cancel) +
 InformationRequestController
 (all firm-side endpoints) +
 item review (accept, reject) +
 auto-complete detection +
 customer/project convenience +
 dashboard summary endpoint +
 integration tests]
        |
        +──────────────────────+──────────────────────+
        |                      |                      |
[E254A Domain events +      [E255A Email templates  [E256A ProjectTemplate
 V13 global migration:       (request-sent,          extension:
 portal_requests +           item-accepted,          requestTemplateId +
 portal_request_items +      item-rejected,          V55 migration (partial:
 PortalEventHandler          completed, reminder) +  request_template_id col) +
 extensions +                NotificationService     ProjectInstantiationService
 PortalReadModel upserts     integration +           auto-draft + tests]
 + sync tests]               AuditEventService
        |                    integration + tests]
[E254B PortalInformation          |
 RequestController +         [E255B Reminder
 PortalInformation            Scheduler +
 RequestService +             OrgSettings extension:
 upload initiation +          defaultRequestReminder
 submit (file + text) +       Days + V55 migration
 portal auth tests]           (partial) + tests]

FRONTEND TRACK (after respective backend epics)
────────────────────────────────────────────────
[E257A Request template
 management pages:
 list, create, edit,
 item editor, duplicate,
 API client + tests]
        |
[E258A CreateRequestDialog +
 RequestStatusBadge +
 ItemStatusBadge +
 RequestProgressBar +
 customer detail "Requests" tab +
 project detail "Requests" tab +
 request list actions + tests]
        |
[E258B information-requests/
 [id]/page.tsx detail page +
 review actions (accept/reject) +
 RejectItemDialog +
 cancel + resend actions +
 tests]

[E259A Portal request list
 page + nav item +
 portalApi client +
 RequestProgressBar (portal) +
 tests]
        |
[E259B Portal request detail
 page + file upload +
 text response submit +
 re-submission + tests]

[E260A Dashboard "Information
 Requests" widget +
 settings page reminder
 interval config + tests]
        |
[E260B Project template editor
 "Request Template" dropdown +
 settings nav link + tests]
```

**Parallel opportunities**:
- After E253B: E254A, E255A, and E256A can all start in parallel (3 independent backend tracks).
- After E252B: E257A can start (frontend template management, independent of E253).
- E259A/259B are independent of E258A/258B (portal vs. firm-side frontend).
- E260A and E260B can run in parallel after their respective dependencies.

---

## Implementation Order

### Stage 0: Database Migration & Template Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 252 | 252A | V54 tenant migration (partial): CREATE TABLE request_templates, request_template_items, request_counters + all indexes. ~1 new migration file. Backend only. | **Done** (PR #539) |
| 0b | 252 | 252B | RequestTemplate + RequestTemplateItem entities, repos, RequestTemplateController (full CRUD + duplicate), RequestPackSeeder + 4 pack JSON files, OrgSettings requestPackStatus extension, V55 migration (partial: request_pack_status column), provisioning hook + integration tests (~15 tests). ~12 new/modified files. Backend only. | **Done** (PR #540) |

### Stage 1: Request Entity & Lifecycle

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 253 | 253A | V54 migration extension (remainder): CREATE TABLE information_requests, request_items + indexes. InformationRequest + RequestItem + RequestCounter entities + enums (RequestStatus, ItemStatus, ResponseType) + repos + RequestNumberService. ~8 new files. Backend only. | **Done** (PR #541) |
| 1b | 253 | 253B | InformationRequestService (create from template + ad-hoc, send, cancel, accept, reject, auto-complete) + InformationRequestController (all firm-side endpoints + customer/project convenience + dashboard summary) + DTOs + integration tests (~25 tests). ~8 new files + ~1 test file. Backend only. | **Done** (PR #542) |

### Stage 2: Backend Tracks (parallel) + Template Frontend

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 254 | 254A | Domain events (6 event classes) + V13 global migration (portal_requests + portal_request_items) + PortalEventHandler extensions (6 handler methods) + PortalReadModelRepository upsert methods + PortalRequestView/PortalRequestItemView records + sync integration tests (~10 tests). ~12 new/modified files. Backend only. | **Done** (PR #543) |
| 2b (parallel) | 255 | 255A | Email templates (5 Thymeleaf HTML+text pairs: request-sent, item-accepted, item-rejected, request-completed, request-reminder) + NotificationService integration (7 notification types) + AuditEventService integration (8 audit event types) + activity feed wiring + tests (~12 tests). ~10 new/modified files. Backend only. | **Done** (PR #545) |
| 2c (parallel) | 257 | 257A | Request template management frontend: settings nav item, list page (settings/request-templates/page.tsx), create page (new/page.tsx), edit page ([id]/page.tsx), item editor (response type, required, drag-reorder), platform template "Duplicate" action, lib/api/information-requests.ts API client, server actions + tests (~10 tests). ~8 new files. Frontend only. | **Done** (PR #548) |

### Stage 3: Portal Backend + Scheduler + Integration Backend

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 254 | 254B | PortalInformationRequestController + PortalInformationRequestService (list, detail, upload initiation via DocumentService + presigned URL, submit file, submit text, re-submission flow) + portal auth/authorization tests (~10 tests). ~5 new files + ~1 test file. Backend only. | **Done** (PR #544) |
| 3b (parallel) | 255 | 255B | RequestReminderScheduler (per-tenant iteration, interval-based, ScopedValue binding) + OrgSettings extension (defaultRequestReminderDays) + V55 migration (partial: default_request_reminder_days column) + reminder audit event + scheduler tests (~8 tests). ~5 new/modified files. Backend only. | **Done** (PR #546) |
| 3c (parallel) | 256 | 256A | ProjectTemplate extension (requestTemplateId field) + V55 migration (partial: request_template_id column on project_templates) + ProjectInstantiationService extension (auto-draft creation) + in-app notification for project members + integration tests (~8 tests). ~5 modified files + ~1 test file. Backend only. | **Done** (PR #547) |

### Stage 4: Firm-Side Frontend

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 258 | 258A | CreateRequestDialog (template selection, customer/project/contact pickers, interval config) + RequestStatusBadge + ItemStatusBadge + RequestProgressBar shared components + customer detail page "Requests" tab + project detail page "Requests" tab + request-actions.ts server actions + API client extensions + tests (~8 tests). ~10 new/modified files. Frontend only. | **Done** (PR #549) |
| 4b | 258 | 258B | information-requests/[id]/page.tsx firm-side detail page (header, progress, item list with status) + Accept/Reject review actions + RejectItemDialog modal + Cancel request + Resend notification + tests (~8 tests). ~5 new files. Frontend only. | **Done** (PR #550) |

### Stage 5: Portal Frontend (parallel with Stage 4)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 259 | 259A | Portal request list page (portal/(authenticated)/requests/page.tsx) + portal layout nav "Requests" item + lib/api/portal-requests.ts API client + RequestProgressBar (portal variant) + RequestStatusBadge (portal) + tests (~6 tests). ~5 new/modified files. Frontend only. | |
| 5b (parallel) | 259 | 259B | Portal request detail page (portal/(authenticated)/requests/[id]/page.tsx) + file upload flow (dropzone, presigned URL upload, submit confirmation) + text response input + re-submission after rejection + item status display + tests (~6 tests). ~3 new files. Frontend only. | |

### Stage 6: Dashboard, Settings & Template Editor Integration

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a (parallel) | 260 | 260A | Dashboard "Information Requests" widget (awaiting review, overdue, completion rate, click-through) + settings/page.tsx "Request Templates" nav link + OrgSettings reminder interval configuration in settings page + tests (~5 tests). ~4 modified files. Frontend only. | |
| 6b (parallel) | 260 | 260B | Project template editor [id]/page.tsx: "Information Request Template" combobox populated from request templates API + help text + persist via PUT + tests (~4 tests). ~2 modified files. Frontend only. | |

### Timeline

```
Stage 0: [252A] → [252B]                                          (sequential)
Stage 1: [253A] → [253B]                                          (sequential)
Stage 2: [254A] // [255A] // [257A]                               (parallel)
Stage 3: [254B] // [255B] // [256A]                               (parallel)
Stage 4: [258A] → [258B]                                          (sequential)
Stage 5: [259A] → [259B]                                          (parallel with Stage 4)
Stage 6: [260A] // [260B]                                         (parallel)
```

**Critical path**: 252A -> 252B -> 253A -> 253B -> 254A -> 254B -> 259A -> 259B (8 slices sequential at most on portal track).

**Alternate critical path (firm-side)**: 252A -> 252B -> 253A -> 253B -> 255A -> 258A -> 258B -> 260A (8 slices).

**Fastest path with parallelism**: 16 slices total, 8 slices on critical path. Stages 2, 3, 5, and 6 all have parallel opportunities.

---

## Epic 252: RequestTemplate Entity Foundation & Pack Seeder

**Goal**: Create the V54 tenant migration for request template tables, build the RequestTemplate + RequestTemplateItem entities with full CRUD controller, implement platform pack seeding (4 packs), and wire into tenant provisioning.

**References**: Architecture doc Sections 11.2 (RequestTemplate, RequestTemplateItem), 11.3.1 (Template CRUD), 11.3.10 (Pack Seeding), 11.4 (Request Template API), 11.10 (V54 migration partial), 11.11 (implementation guidance).

**Dependencies**: None -- this is the greenfield foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **252A** | 252.1--252.3 | V54 tenant migration (partial): CREATE TABLE request_templates + request_template_items + request_counters + all indexes + constraints. ~1 new migration file. Backend only. | **Done** (PR #539) |
| **252B** | 252.4--252.17 | RequestTemplate + RequestTemplateItem entities + TemplateSource enum + repos + RequestTemplateController (CRUD + duplicate) + DTOs + RequestPackSeeder + 4 pack JSON definitions + OrgSettings requestPackStatus extension + V55 migration (partial: request_pack_status column) + provisioning hook + integration tests (~15 tests). ~12 new/modified files. Backend only. | **Done** (PR #540) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 252.1 | Create V54 tenant migration -- request_templates table | 252A | | New file: `backend/src/main/resources/db/migration/tenant/V54__create_information_request_tables.sql`. CREATE TABLE request_templates (id UUID PK, name VARCHAR(200) NOT NULL, description VARCHAR(1000), source VARCHAR(20) NOT NULL DEFAULT 'CUSTOM', pack_id VARCHAR(100), active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ). Indexes: idx_request_templates_active, idx_request_templates_pack_id. See architecture doc Section 11.10. |
| 252.2 | V54 migration -- request_template_items table | 252A | 252.1 | Same file. CREATE TABLE request_template_items (id UUID PK, template_id UUID NOT NULL FK CASCADE, name VARCHAR(200) NOT NULL, description VARCHAR(1000), response_type VARCHAR(20) NOT NULL, required BOOLEAN NOT NULL DEFAULT TRUE, file_type_hints VARCHAR(200), sort_order INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ). Index: idx_request_template_items_template. |
| 252.3 | V54 migration -- request_counters table | 252A | 252.1 | Same file. CREATE TABLE request_counters (id UUID PK, next_number INTEGER NOT NULL DEFAULT 1, singleton BOOLEAN NOT NULL DEFAULT TRUE, CONSTRAINT request_counters_singleton UNIQUE (singleton)). Needed by RequestNumberService in Epic 253. |
| 252.4 | Create TemplateSource enum | 252B | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/TemplateSource.java`. Values: PLATFORM, CUSTOM. Pattern: similar to `template/TemplateSource.java`. |
| 252.5 | Create ResponseType enum | 252B | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/ResponseType.java`. Values: FILE_UPLOAD, TEXT_RESPONSE. |
| 252.6 | Create RequestTemplate entity | 252B | 252.4 | New file: `informationrequest/RequestTemplate.java`. @Entity @Table("request_templates"), UUID PK, name, description, source (String), packId, active. Pattern: `template/DocumentTemplate.java`. Post-Phase 13 pattern: no @Filter/@FilterDef. |
| 252.7 | Create RequestTemplateItem entity | 252B | 252.5 | New file: `informationrequest/RequestTemplateItem.java`. @Entity @Table("request_template_items"), UUID PK, templateId (UUID FK by value), name, description, responseType (String), required, fileTypeHints, sortOrder, createdAt. Pattern: `checklist/ChecklistTemplateItem.java`. |
| 252.8 | Create RequestTemplateRepository | 252B | 252.6 | New file: `informationrequest/RequestTemplateRepository.java`. JpaRepository<RequestTemplate, UUID>. Methods: findByActiveTrue(), findByPackId(String). |
| 252.9 | Create RequestTemplateItemRepository | 252B | 252.7 | New file: `informationrequest/RequestTemplateItemRepository.java`. JpaRepository<RequestTemplateItem, UUID>. Methods: findByTemplateIdOrderBySortOrder(UUID), deleteByTemplateId(UUID). |
| 252.10 | Create RequestTemplateController with CRUD endpoints | 252B | 252.8, 252.9 | New file: `informationrequest/RequestTemplateController.java`. Endpoints: GET /api/request-templates (query: active), POST (create), GET /{id} (with items), PUT /{id} (update), DELETE /{id} (soft-deactivate), POST /{id}/items, PUT /{id}/items/{itemId}, DELETE /{id}/items/{itemId}, POST /{id}/duplicate. RBAC: Member for read, Admin+ for write. Pattern: `checklist/ChecklistTemplateController.java`. |
| 252.11 | Create DTOs for template API | 252B | | New file: `informationrequest/dto/RequestTemplateDtos.java`. Records: CreateTemplateRequest, UpdateTemplateRequest, CreateTemplateItemRequest, UpdateTemplateItemRequest, RequestTemplateResponse (with items), RequestTemplateItemResponse. Pattern: nested records or dto sub-package. |
| 252.12 | Create RequestPackDefinition records | 252B | | New file: `informationrequest/RequestPackDefinition.java`. Records: RequestPackDefinition(String packId, String name, String description, String version, List<RequestPackItem> items). RequestPackItem(String name, String description, String responseType, boolean required, String fileTypeHints, int sortOrder). Pattern: `template/TemplatePackDefinition.java`. |
| 252.13 | Create 4 platform pack JSON files | 252B | 252.12 | New directory: `backend/src/main/resources/request-packs/`. New files: `annual-audit/pack.json`, `tax-return/pack.json`, `company-registration/pack.json`, `monthly-bookkeeping/pack.json`. Each contains packId, name, description, version, items array. See architecture doc Section 11.3.10 for pack contents. |
| 252.14 | Create RequestPackSeeder | 252B | 252.12, 252.8, 252.9 | New file: `informationrequest/RequestPackSeeder.java`. Follows CompliancePackSeeder pattern: loads from classpath `request-packs/*/pack.json`, checks OrgSettings.requestPackStatus for idempotency, creates RequestTemplate + items for each pack. Pattern: `checklist/CompliancePackSeeder.java` or `template/TemplatePackSeeder.java`. |
| 252.15 | Extend OrgSettings with requestPackStatus | 252B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add field: `requestPackStatus` (JSONB, nullable) with `@JdbcTypeCode(SqlTypes.JSON)`. Add `recordRequestPackApplication(String packId, String version)` and `isRequestPackApplied(String packId)` methods. Pattern: existing pack status fields on OrgSettings. |
| 252.16 | Create V55 migration (partial: request_pack_status) | 252B | | New file: `backend/src/main/resources/db/migration/tenant/V55__extend_project_templates_and_org_settings_for_requests.sql`. ALTER TABLE org_settings ADD COLUMN request_pack_status JSONB. (Other V55 columns added in later slices.) |
| 252.17 | Wire RequestPackSeeder into provisioning + write integration tests | 252B | 252.14 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/` (TenantProvisioningService or similar) to call `requestPackSeeder.seedPacksForTenant()`. New test file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateControllerTest.java`. Tests (~15): template CRUD (create, read, update, deactivate, list active, list all), item CRUD (add, update, remove, reorder), duplicate template, pack seeder idempotency, pack seeder creates 4 templates, provisioning triggers seeder, RBAC (member read-only, admin can write). Pattern: `checklist/ChecklistTemplateControllerTest.java`. |

### Key Files

**Slice 252A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V54__create_information_request_tables.sql`

**Slice 252B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/TemplateSource.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/ResponseType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateItem.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateItemRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/dto/RequestTemplateDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestPackDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestPackSeeder.java`
- `backend/src/main/resources/request-packs/annual-audit/pack.json`
- `backend/src/main/resources/request-packs/tax-return/pack.json`
- `backend/src/main/resources/request-packs/company-registration/pack.json`
- `backend/src/main/resources/request-packs/monthly-bookkeeping/pack.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateControllerTest.java`

**Slice 252B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/` (provisioning service)

**Slice 252B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` -- pack seeder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateController.java` -- template CRUD pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` -- entity pattern

### Architecture Decisions

- **Separate V54 migration file**: Contains all new tables for Phase 34. request_counters table included here even though RequestCounter entity is in Epic 253, because the migration must be atomic and request_counters has no FK dependencies on other Phase 34 tables.
- **V55 is additive ALTER TABLE only**: Extends existing tables (org_settings, project_templates) without touching Phase 34 tables. Split from V54 because it modifies pre-existing tables.
- **TemplateSource as enum, not String constant**: Following established pattern where enums that could grow (PLATFORM, CUSTOM) are real Java enums. Stored as String in DB for portability.
- **Pack seeder follows established pattern**: Same classpath-based JSON loading, OrgSettings pack status tracking, and idempotent application as CompliancePackSeeder and TemplatePackSeeder.

---

## Epic 253: InformationRequest Entity & Lifecycle Backend

**Goal**: Build the InformationRequest + RequestItem + RequestCounter entities, the RequestNumberService, the full InformationRequestService with lifecycle management (create, send, cancel, accept, reject, auto-complete), and the InformationRequestController with all firm-side endpoints including customer/project convenience and dashboard summary.

**References**: Architecture doc Sections 11.2 (entities), 11.3.2-11.3.5 (lifecycle), 11.3.9 (numbering), 11.4 (API surface), 11.11 (implementation guidance).

**Dependencies**: Epic 252 (RequestTemplate entities for template-based creation; V54 migration for information_requests and request_items tables).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **253A** | 253.1--253.8 | InformationRequest + RequestItem + RequestCounter entities + RequestStatus/ItemStatus enums + InformationRequestRepository + RequestItemRepository + RequestNumberService (atomic upsert). ~8 new files. Backend only. | **Done** (PR #541) |
| **253B** | 253.9--253.16 | InformationRequestService (full lifecycle: create template + ad-hoc, send, cancel, accept, reject, auto-complete) + InformationRequestController (all firm-side endpoints + customer/project convenience + dashboard summary) + DTOs + integration tests (~25 tests). ~6 new files + ~1 test file. Backend only. | **Done** (PR #542) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 253.1 | Create RequestStatus enum | 253A | | New file: `informationrequest/RequestStatus.java`. Values: DRAFT, SENT, IN_PROGRESS, COMPLETED, CANCELLED. |
| 253.2 | Create ItemStatus enum | 253A | | New file: `informationrequest/ItemStatus.java`. Values: PENDING, SUBMITTED, ACCEPTED, REJECTED. |
| 253.3 | Create InformationRequest entity | 253A | 253.1 | New file: `informationrequest/InformationRequest.java`. @Entity @Table("information_requests"). Fields per architecture doc Section 11.2. Lifecycle methods: send(), markInProgress(), complete(), cancel(). See architecture doc Section 11.11 for full entity code. Pattern: `proposal/Proposal.java` (similar lifecycle state machine). |
| 253.4 | Create RequestItem entity | 253A | 253.2 | New file: `informationrequest/RequestItem.java`. @Entity @Table("request_items"). Fields per architecture doc Section 11.2. Methods: submit(UUID documentId), submitText(String text), accept(UUID reviewedBy), reject(String reason, UUID reviewedBy). Pattern: `checklist/ChecklistInstanceItem.java`. |
| 253.5 | Create RequestCounter entity | 253A | | New file: `informationrequest/RequestCounter.java`. @Entity @Table("request_counters"). Singleton counter pattern. Fields: id, nextNumber, singleton. Pattern: `invoice/InvoiceCounter.java`, `proposal/ProposalCounter.java`. |
| 253.6 | Create InformationRequestRepository | 253A | 253.3 | New file: `informationrequest/InformationRequestRepository.java`. JpaRepository<InformationRequest, UUID>. Methods: findByCustomerId(UUID), findByProjectId(UUID), findByStatusIn(List<String>), countByStatus(String). |
| 253.7 | Create RequestItemRepository | 253A | 253.4 | New file: `informationrequest/RequestItemRepository.java`. JpaRepository<RequestItem, UUID>. Methods: findByRequestId(UUID), findByRequestIdOrderBySortOrder(UUID), countByRequestIdAndStatus(UUID, String). |
| 253.8 | Create RequestNumberService | 253A | 253.5 | New file: `informationrequest/RequestNumberService.java`. Atomic upsert pattern: INSERT INTO request_counters ... ON CONFLICT DO UPDATE SET next_number = next_number + 1 RETURNING next_number - 1. Format: "REQ-%04d". Pattern: `invoice/InvoiceNumberService.java`, `proposal/ProposalNumberService.java`. |
| 253.9 | Create InformationRequestService -- create operations | 253B | 253.3, 253.4 | New file: `informationrequest/InformationRequestService.java`. Methods: createFromTemplate(templateId, customerId, projectId, portalContactId, reminderIntervalDays), createAdHoc(customerId, projectId, portalContactId, reminderIntervalDays). Validates customer, portal contact, project. Copies template items. Sets DRAFT status. Uses RequestNumberService. Pattern: `proposal/ProposalService.java`. |
| 253.10 | InformationRequestService -- send, cancel operations | 253B | 253.9 | In same file. send(requestId): validate at least 1 item, validate portal contact ACTIVE, set SENT + sentAt, publish InformationRequestSentEvent (event class as placeholder -- full events in E254A). cancel(requestId): validate not COMPLETED, set CANCELLED + cancelledAt. |
| 253.11 | InformationRequestService -- accept, reject, auto-complete | 253B | 253.9 | In same file. acceptItem(requestId, itemId): validate SUBMITTED, set ACCEPTED + reviewedAt/reviewedBy, call checkAutoComplete(). rejectItem(requestId, itemId, reason): validate SUBMITTED, set REJECTED + clear documentId/textResponse + set rejectionReason. checkAutoComplete(): if all required items ACCEPTED, transition request to COMPLETED. |
| 253.12 | InformationRequestService -- update DRAFT, add ad-hoc item | 253B | 253.9 | In same file. updateRequest(requestId, ...): only DRAFT status. addItem(requestId, name, description, responseType, required, fileTypeHints, sortOrder): only DRAFT status. resendNotification(requestId): re-publish sent event. |
| 253.13 | Create InformationRequest DTOs | 253B | | New file: `informationrequest/dto/InformationRequestDtos.java`. Records: CreateInformationRequestRequest, UpdateInformationRequestRequest, CreateAdHocItemRequest, RejectItemRequest(reason), InformationRequestResponse (with items + enriched names), RequestItemResponse, DashboardSummaryResponse. |
| 253.14 | Create InformationRequestController | 253B | 253.9, 253.13 | New file: `informationrequest/InformationRequestController.java`. All firm-side endpoints per architecture doc Section 11.4: CRUD, send, cancel, add item, accept, reject, resend-notification. Customer/project convenience: GET /api/customers/{customerId}/information-requests, GET /api/projects/{projectId}/information-requests. Dashboard: GET /api/information-requests/summary. RBAC: Member for most operations, project access check for project-scoped. |
| 253.15 | Dashboard summary query | 253B | 253.6, 253.7 | In InformationRequestService. Method: getDashboardSummary(). Returns: total, byStatus counts, itemsPendingReview (items with SUBMITTED status), overdueRequests (no activity in > 2x interval), completionRateLast30Days. See architecture doc Section 11.4 response shape. |
| 253.16 | Write InformationRequestController integration tests | 253B | 253.14 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestControllerTest.java`. Tests (~25): create from template (items copied), create ad-hoc, send (DRAFT->SENT), cancel (various states), accept (SUBMITTED->ACCEPTED), reject (with reason, clears response), auto-complete (all required accepted), auto-transition (SENT->IN_PROGRESS on first submission), numbering (sequential REQ-0001, REQ-0002), update DRAFT, add ad-hoc item, cannot send empty request, cannot cancel completed, customer convenience endpoint, project convenience endpoint, dashboard summary, RBAC (project access), invalid portal contact, invalid customer. Pattern: `proposal/ProposalControllerTest.java`. |

### Key Files

**Slice 253A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/ItemStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestItem.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestCounter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestItemRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestNumberService.java`

**Slice 253B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/dto/InformationRequestDtos.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestControllerTest.java`

**Slice 253B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java` -- lifecycle state machine pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java` -- controller endpoint pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNumberService.java` -- numbering pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactService.java` -- portal contact validation

### Architecture Decisions

- **Entities split from service**: 253A creates entities/repos only (no business logic). 253B adds the service with full lifecycle. This keeps each slice focused and avoids a single monolithic slice.
- **Event placeholders**: InformationRequestService calls `applicationEventPublisher.publishEvent()` in 253B, but the event classes (InformationRequestSentEvent etc.) are created as simple record stubs in this slice. Full event handling (portal sync, notifications) is wired in Epics 254 and 255.
- **Dashboard summary uses repository queries**: The summary endpoint uses count queries on InformationRequestRepository and RequestItemRepository rather than materialized views, following the existing dashboard pattern from Phase 9.

---

## Epic 254: Domain Events, Portal Read-Model Sync & Portal API

**Goal**: Create the domain event classes, wire portal read-model sync via PortalEventHandler, create the portal schema migration (V13), and build the PortalInformationRequestController + PortalInformationRequestService for client-facing API (list, detail, upload, submit).

**References**: Architecture doc Sections 11.5 (sequence diagrams), 11.6 (portal read-model sync), 11.3.6 (portal submission flow), 11.4 (portal API).

**Dependencies**: Epic 253 (InformationRequest entities and service).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **254A** | 254.1--254.8 | 6 domain event classes + V13 global migration (portal_requests + portal_request_items) + PortalEventHandler extensions (6 handler methods) + PortalReadModelRepository upsert methods + PortalRequestView/PortalRequestItemView records + sync integration tests (~10 tests). ~12 new/modified files. Backend only. | **Done** (PR #543) |
| **254B** | 254.9--254.14 | PortalInformationRequestController + PortalInformationRequestService (list, detail, upload initiation, submit file, submit text, re-submission) + portal auth/authorization + integration tests (~10 tests). ~5 new files + ~1 test file. Backend only. | **Done** (PR #544) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 254.1 | Create domain event classes | 254A | | New files in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/`: InformationRequestSentEvent, InformationRequestCancelledEvent, InformationRequestCompletedEvent, RequestItemSubmittedEvent, RequestItemAcceptedEvent, RequestItemRejectedEvent. Each extends DomainEvent or is a record with tenantId, orgId, requestId, and event-specific fields. Pattern: `event/ProposalSentEvent.java`, `event/AcceptanceRequestSentEvent.java`. |
| 254.2 | Wire event publishing into InformationRequestService | 254A | 254.1 | Modify: `informationrequest/InformationRequestService.java`. Replace placeholder event stubs with actual event classes. Publish: SentEvent on send(), CancelledEvent on cancel(), CompletedEvent on auto-complete, ItemSubmittedEvent on portal submit (wired later), ItemAcceptedEvent on accept, ItemRejectedEvent on reject. |
| 254.3 | Create V13 global migration -- portal request tables | 254A | | New file: `backend/src/main/resources/db/migration/global/V13__portal_requests.sql`. CREATE TABLE portal.portal_requests and portal.portal_request_items with all columns and indexes per architecture doc Section 11.6. |
| 254.4 | Create PortalRequestView and PortalRequestItemView records | 254A | | New files in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/`: PortalRequestView.java, PortalRequestItemView.java. Records mapping portal schema columns. Pattern: existing `model/` records in customerbackend package. |
| 254.5 | Extend PortalReadModelRepository with upsert methods | 254A | 254.4 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java`. New methods: upsertPortalRequest(InformationRequest, String projectName, String orgId), upsertPortalRequestItem(RequestItem), updatePortalRequestStatus(UUID requestId, String status, Instant completedAt), updatePortalRequestItemStatus(UUID itemId, String status, String rejectionReason, UUID documentId, String textResponse), recalculatePortalRequestCounts(UUID requestId). Uses native SQL INSERT ... ON CONFLICT per architecture doc Section 11.6. |
| 254.6 | Extend PortalEventHandler with request event handlers | 254A | 254.1, 254.5 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`. 6 new @TransactionalEventListener(AFTER_COMMIT) methods: onInformationRequestSent, onInformationRequestCancelled, onInformationRequestCompleted, onRequestItemSubmitted, onRequestItemAccepted, onRequestItemRejected. Each calls PortalReadModelRepository upsert methods within tenant scope. Pattern: existing handler methods in PortalEventHandler. |
| 254.7 | Extend PortalQueryService for request queries | 254A | 254.4 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/` or new `PortalRequestQueryService`. Methods: findRequestsByPortalContactId(UUID), findRequestById(UUID), findRequestItemsByRequestId(UUID). Uses JdbcTemplate queries against portal schema. |
| 254.8 | Write portal sync integration tests | 254A | 254.6 | New or extend test file. Tests (~10): SentEvent creates portal_requests + portal_request_items, CancelledEvent updates status, CompletedEvent updates status + completed_at, ItemSubmittedEvent updates item status + recalculates counts, ItemAcceptedEvent updates item, ItemRejectedEvent updates item + rejection_reason, idempotent upserts, count recalculation accuracy, query by portal contact returns only their requests. Pattern: existing portal sync tests. |
| 254.9 | Create PortalInformationRequestService | 254B | 254.7 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalInformationRequestService.java`. Methods: listRequests(UUID portalContactId), getRequest(UUID requestId, UUID portalContactId), initiateUpload(requestId, itemId, fileName, contentType, size, portalContactId), submitItem(requestId, itemId, documentId, portalContactId), submitTextResponse(requestId, itemId, text, portalContactId). Auth checks: portalContactId must match request.portalContactId. Upload creates Document entity (SHARED visibility). Pattern: `customerbackend/service/PortalProposalService.java`. |
| 254.10 | PortalInformationRequestService -- upload initiation | 254B | 254.9 | In same file. initiateUpload: creates Document entity via DocumentService (scope = PROJECT if project-scoped else CUSTOMER, visibility = SHARED), generates presigned S3 URL, returns {documentId, uploadUrl, expiresAt}. Pattern: `s3/S3PresignedUrlService.java` + `document/DocumentService.java`. |
| 254.11 | PortalInformationRequestService -- submit + auto-transition | 254B | 254.9 | In same file. submitItem: validates item PENDING or REJECTED, confirms document upload, sets SUBMITTED + documentId + submittedAt. submitTextResponse: validates item PENDING or REJECTED, sets SUBMITTED + textResponse + submittedAt. Both call markInProgress on request if status is SENT. Publish RequestItemSubmittedEvent. |
| 254.12 | Create PortalInformationRequestController | 254B | 254.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalInformationRequestController.java`. Endpoints: GET /portal/api/requests, GET /portal/api/requests/{id}, POST /portal/api/requests/{id}/items/{itemId}/upload, POST /portal/api/requests/{id}/items/{itemId}/submit. Auth: CustomerAuthFilter extracts portalContactId from JWT. Pattern: `customerbackend/controller/PortalAcceptanceRequestController.java`. |
| 254.13 | Create portal request DTOs | 254B | | DTOs in controller or new file. Records: PortalRequestListResponse, PortalRequestDetailResponse (with items), PortalRequestItemResponse, UploadInitiationRequest(fileName, contentType, size), UploadInitiationResponse(documentId, uploadUrl, expiresAt), SubmitItemRequest(documentId or textResponse). |
| 254.14 | Write portal API integration tests | 254B | 254.12 | New test file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/PortalInformationRequestControllerTest.java`. Tests (~10): list requests by portal contact, get request detail, upload initiation (creates Document + presigned URL), submit file (PENDING -> SUBMITTED), submit text response, re-submit after rejection (REJECTED -> SUBMITTED), auto-transition SENT -> IN_PROGRESS, auth: different portal contact denied, auth: wrong customer denied, submit on ACCEPTED item rejected. Pattern: existing portal controller tests. |

### Key Files

**Slice 254A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InformationRequestSentEvent.java` (+ 5 more event files)
- `backend/src/main/resources/db/migration/global/V13__portal_requests.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalRequestView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalRequestItemView.java`

**Slice 254A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java`

**Slice 254B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalInformationRequestService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalInformationRequestController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/PortalInformationRequestControllerTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` -- existing sync pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalAcceptanceRequestController.java` -- portal controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` -- document creation for uploads
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/S3PresignedUrlService.java` -- presigned URL pattern

### Architecture Decisions

- **Events as records in event/ package**: Following the established pattern where domain events live in a shared `event/` package, not within the feature package. Each event is a Java record.
- **Portal sync uses AFTER_COMMIT listeners**: Ensures portal read-model is only updated after the tenant-schema transaction commits successfully. Same pattern as existing portal sync for proposals, acceptance requests, etc.
- **Upload initiation creates Document entity immediately**: The Document starts in PENDING state; the portal frontend uploads to S3 using the presigned URL, then calls submit to confirm. This matches the existing upload flow pattern.

---

## Epic 255: Notifications, Audit & Reminder Scheduler

**Goal**: Wire notification delivery (email to portal contacts, in-app + email to firm members) for all request lifecycle events, create audit events for all transitions, build the RequestReminderScheduler for automated interval-based reminders, and extend OrgSettings with the default reminder interval.

**References**: Architecture doc Sections 11.7 (automated reminders), 11.9 (notifications & audit), 11.3.7 (reminder scheduling).

**Dependencies**: Epic 253 (InformationRequest entities), Epic 254 (domain events).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **255A** | 255.1--255.7 | 5 Thymeleaf email template pairs (HTML + text) + NotificationService integration (7 notification types) + AuditEventService integration (8 audit event types) + activity feed formatting + tests (~12 tests). ~10 new/modified files. Backend only. | **Done** (PR #545) |
| **255B** | 255.8--255.14 | RequestReminderScheduler (per-tenant iteration, ScopedValue, interval-based) + OrgSettings extension (defaultRequestReminderDays) + V55 migration extension (default_request_reminder_days column) + reminder email template + REQUEST_REMINDER_SENT audit event + scheduler integration tests (~8 tests). ~5 new/modified files. Backend only. | **Done** (PR #546) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 255.1 | Create email templates for request lifecycle | 255A | | New files in `backend/src/main/resources/templates/email/`: `request-sent.html` + `request-sent.txt`, `request-item-accepted.html` + `request-item-accepted.txt`, `request-item-rejected.html` + `request-item-rejected.txt`, `request-completed.html` + `request-completed.txt`. Thymeleaf templates with portal link, item details, rejection reason. Pattern: existing email templates in same directory. |
| 255.2 | Create event listeners for email notifications to portal contacts | 255A | 255.1 | New file or extend: `informationrequest/InformationRequestEmailEventListener.java`. @TransactionalEventListener for SentEvent (email to portal contact), ItemAcceptedEvent (email to contact), ItemRejectedEvent (email with reason to contact), CompletedEvent (email to contact). Uses EmailNotificationChannel. Pattern: `invoice/InvoiceEmailEventListener.java`. |
| 255.3 | Create event listeners for in-app notifications to firm members | 255A | | New file or extend: `informationrequest/InformationRequestNotificationEventListener.java`. @TransactionalEventListener for ItemSubmittedEvent (in-app + email to request creator), CompletedEvent (in-app to creator). Uses NotificationService.createIfEnabled(). Pattern: existing notification event listeners. |
| 255.4 | Wire audit events for all request lifecycle transitions | 255A | | Modify InformationRequestService or create dedicated listener. 8 audit event types: REQUEST_CREATED, REQUEST_SENT, REQUEST_CANCELLED, REQUEST_COMPLETED, REQUEST_ITEM_SUBMITTED, REQUEST_ITEM_ACCEPTED, REQUEST_ITEM_REJECTED. Each with JSONB details per architecture doc Section 11.9. Uses AuditEventService. Pattern: existing audit event creation throughout codebase. |
| 255.5 | Wire activity feed integration | 255A | 255.4 | Ensure audit events use entityType = "InformationRequest" and include projectId. Modify `activity/ActivityFeedFormatter` (or equivalent) to format request-related audit events for the project activity tab. Messages: "Information request REQ-XXXX sent to {contact}", "Client submitted N items", "REQ-XXXX completed". Pattern: existing activity feed formatting. |
| 255.6 | Create InformationRequestDraftCreatedEvent + listener | 255A | | New event: `event/InformationRequestDraftCreatedEvent.java`. Listener: sends in-app notification to project members when a draft request is auto-created from a project template (used by Epic 256). Pattern: existing in-app notification events. |
| 255.7 | Write notification and audit integration tests | 255A | 255.2, 255.3, 255.4 | New or extend test file. Tests (~12): email sent on request sent, email sent on item accepted, email with reason on item rejected, email on completion, in-app notification on item submitted, in-app notification on completion, audit event created for each of 8 types, audit event details contain correct JSONB, activity feed query returns request events for project. Pattern: existing notification/audit tests. |
| 255.8 | Extend OrgSettings with defaultRequestReminderDays | 255B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add field: `defaultRequestReminderDays` (Integer, nullable). Getter/setter. Default via migration. |
| 255.9 | Extend V55 migration with default_request_reminder_days | 255B | | Modify: `backend/src/main/resources/db/migration/tenant/V55__extend_project_templates_and_org_settings_for_requests.sql`. Add: ALTER TABLE org_settings ADD COLUMN default_request_reminder_days INTEGER DEFAULT 5. (This V55 file was created in 252B with request_pack_status; this adds the second column.) |
| 255.10 | Extend OrgSettingsController for reminder interval | 255B | 255.8 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`. Ensure GET and PUT endpoints include defaultRequestReminderDays in the response/request payload. |
| 255.11 | Create request-reminder email template | 255B | | New files: `backend/src/main/resources/templates/email/request-reminder.html` + `request-reminder.txt`. Template: "You have X outstanding items for {requestNumber}", lists pending/rejected items, "View in Portal" link. Per architecture doc Section 11.7. |
| 255.12 | Create RequestReminderScheduler | 255B | 255.8 | New file: `informationrequest/RequestReminderScheduler.java`. @Component with @Scheduled(fixedRate = 21_600_000). Iterates OrgSchemaMappingRepository.findAll(). Per-tenant: ScopedValue.where().call(), TransactionTemplate.execute(). Loads OrgSettings for default interval. Queries findByStatusIn(SENT, IN_PROGRESS). For each request: calculate interval, check daysSince, send reminder email, update lastReminderSentAt, publish REQUEST_REMINDER_SENT audit event. Per architecture doc Section 11.7. Pattern: `schedule/TimeReminderScheduler.java`. |
| 255.13 | Implement sendReminder helper | 255B | 255.11, 255.12 | In RequestReminderScheduler. Loads pending/rejected items for the request. Renders request-reminder email template. Sends via EmailNotificationChannel to portal contact. Updates request.lastReminderSentAt. Logs REQUEST_REMINDER_SENT audit event. |
| 255.14 | Write scheduler integration tests | 255B | 255.12 | New test file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderSchedulerTest.java`. Tests (~8): reminder sent when interval exceeded, reminder not sent when interval not reached, uses request override interval when set, uses org default when request interval null, skips when interval = 0 (disabled), updates lastReminderSentAt after sending, per-tenant isolation (one bad tenant doesn't block others), audit event created for each reminder sent. Pattern: `schedule/TimeReminderSchedulerTest.java`. |

### Key Files

**Slice 255A -- Create:**
- `backend/src/main/resources/templates/email/request-sent.html` (+ .txt)
- `backend/src/main/resources/templates/email/request-item-accepted.html` (+ .txt)
- `backend/src/main/resources/templates/email/request-item-rejected.html` (+ .txt)
- `backend/src/main/resources/templates/email/request-completed.html` (+ .txt)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestEmailEventListener.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestNotificationEventListener.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InformationRequestDraftCreatedEvent.java`

**Slice 255A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestService.java` (audit events)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/` (activity feed formatter)

**Slice 255B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderScheduler.java`
- `backend/src/main/resources/templates/email/request-reminder.html` (+ .txt)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderSchedulerTest.java`

**Slice 255B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`
- `backend/src/main/resources/db/migration/tenant/V55__extend_project_templates_and_org_settings_for_requests.sql`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` -- scheduler pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceEmailEventListener.java` -- email event listener pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/` -- NotificationService usage

### Architecture Decisions

- **Notifications and audit in same epic**: These are tightly coupled -- the same events trigger both. Splitting would cause duplication of event listener boilerplate.
- **Scheduler in separate slice from notifications**: The scheduler is a self-contained component with its own test strategy (time-based, per-tenant). It depends on notification infrastructure but is independently testable.
- **V55 migration extended, not new**: The V55 file from 252B is extended with the additional column. Flyway checksums will need to be recalculated if slices are implemented sequentially on the same branch. Alternatively, the builder can write all V55 content at once and the brief should specify all columns.

---

## Epic 256: Project Template Integration & OrgSettings Extension

**Goal**: Extend ProjectTemplate with requestTemplateId, extend ProjectInstantiationService to auto-create draft information requests when projects are created from templates, and add the V55 migration column for project_templates.

**References**: Architecture doc Sections 11.8 (project template integration), 11.3.8 (auto-draft creation flow).

**Dependencies**: Epic 253 (InformationRequestService.createFromTemplate), Epic 255 (InformationRequestDraftCreatedEvent + notification listener).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **256A** | 256.1--256.6 | ProjectTemplate extension (requestTemplateId) + V55 migration extension (request_template_id column) + ProjectInstantiationService auto-draft creation + notification + integration tests (~8 tests). ~5 modified files + ~1 test file. Backend only. | **Done** (PR #547) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 256.1 | Extend ProjectTemplate entity with requestTemplateId | 256A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplate.java`. Add field: `requestTemplateId` (UUID, nullable). @Column(name = "request_template_id"). Getter/setter. |
| 256.2 | Extend V55 migration with request_template_id column | 256A | | Modify: `backend/src/main/resources/db/migration/tenant/V55__extend_project_templates_and_org_settings_for_requests.sql`. Add: ALTER TABLE project_templates ADD COLUMN request_template_id UUID REFERENCES request_templates(id). |
| 256.3 | Extend ProjectTemplate DTOs to include requestTemplateId | 256A | 256.1 | Modify: ProjectTemplate-related DTOs (in controller or dto package). Include requestTemplateId in request and response objects for GET and PUT endpoints. |
| 256.4 | Extend ProjectInstantiationService with auto-draft creation | 256A | 256.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/` (ProjectInstantiationService or equivalent). After project creation: check template.getRequestTemplateId(), resolve customer via CustomerProject, find PRIMARY portal contact, call InformationRequestService.createFromTemplate() in DRAFT status. Skip if no portal contact found. Publish InformationRequestDraftCreatedEvent. Per architecture doc Section 11.3.8. |
| 256.5 | Extend ProjectTemplateController for requestTemplateId | 256A | 256.3 | Modify: ProjectTemplateController or related controller. Ensure PUT /api/project-templates/{id} accepts and persists requestTemplateId. Ensure GET returns requestTemplateId. |
| 256.6 | Write auto-draft integration tests | 256A | 256.4 | New or extend test file. Tests (~8): template with requestTemplateId creates draft request on project creation, draft request has correct customer + project + contact, draft request has items copied from request template, template without requestTemplateId skips draft creation, project without linked customer skips draft creation, customer without portal contact skips draft creation (log warning), proposal acceptance triggers auto-draft, recurring schedule execution triggers auto-draft. Pattern: existing ProjectInstantiationService tests. |

### Key Files

**Slice 256A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/` (instantiation service)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/` (controller)
- `backend/src/main/resources/db/migration/tenant/V55__extend_project_templates_and_org_settings_for_requests.sql`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactRepository.java` -- find primary contact
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/` -- CustomerProject relationship

### Architecture Decisions

- **Draft-on-creation, not auto-send**: Per ADR-137, the system creates a DRAFT request. The firm must review, optionally customize items, select the portal contact if the auto-detected one is wrong, and then manually send. This preserves per-engagement control.
- **Graceful degradation**: If no portal contact exists for the customer, the auto-draft is silently skipped with a log warning. This avoids blocking project creation for a non-critical feature.

---

## Epic 257: Request Template Management UI

**Goal**: Build the firm-side settings pages for managing request templates: list, create, edit with sortable item editor, duplicate, and deactivate. Includes the API client for template endpoints.

**References**: Architecture doc Section 11.11 (frontend changes), requirements Section 7 (request templates page).

**Dependencies**: Epic 252 (backend template endpoints must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **257A** | 257.1--257.8 | Settings nav item + request template list page + create page + edit page with item editor (response type, required, drag-reorder, file type hints) + platform template "Duplicate" action + lib/api/information-requests.ts API client + server actions + tests (~10 tests). ~8 new files. Frontend only. | **Done** (PR #548) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 257.1 | Create API client for request template endpoints | 257A | | New file: `frontend/lib/api/information-requests.ts`. Functions: listTemplates(active?), getTemplate(id), createTemplate(data), updateTemplate(id, data), deactivateTemplate(id), duplicateTemplate(id), addTemplateItem(templateId, data), updateTemplateItem(templateId, itemId, data), removeTemplateItem(templateId, itemId). Uses existing api.ts fetch wrapper. Pattern: `frontend/lib/api.ts` usage in other API client files. |
| 257.2 | Add "Request Templates" to settings navigation | 257A | | Modify: `frontend/app/(app)/org/[slug]/settings/page.tsx` (or settings nav configuration). Add "Request Templates" link to settings sidebar/list. Pattern: existing settings nav items (e.g., "Project Templates", "Clauses", "Tags"). |
| 257.3 | Create request template list page | 257A | 257.1 | New file: `frontend/app/(app)/org/[slug]/settings/request-templates/page.tsx`. Lists all templates. Platform templates have "PLATFORM" badge and are read-only. Custom templates have edit/deactivate actions. "Duplicate" action for platform templates. Filter: active/all toggle. "New Template" button. Server actions for data fetching. Pattern: `frontend/app/(app)/org/[slug]/settings/project-templates/page.tsx`. |
| 257.4 | Create request template server actions | 257A | 257.1 | New file: `frontend/app/(app)/org/[slug]/settings/request-templates/actions.ts`. Server actions: getTemplates(), getTemplate(id), createTemplate(), updateTemplate(), deactivateTemplate(), duplicateTemplate(), addItem(), updateItem(), removeItem(). Pattern: existing settings action files. |
| 257.5 | Create new template page | 257A | 257.1 | New file: `frontend/app/(app)/org/[slug]/settings/request-templates/new/page.tsx`. Form: name, description. On save redirects to edit page. Pattern: existing create flows. |
| 257.6 | Create template editor page | 257A | 257.4 | New file: `frontend/app/(app)/org/[slug]/settings/request-templates/[id]/page.tsx`. Header: name (editable), description. Item list: sortable (drag-to-reorder), each item shows name, response type badge, required indicator. Add item form. Per-item editor: name, description, response type dropdown (FILE_UPLOAD, TEXT_RESPONSE), required toggle, file type hints input. Platform templates: read-only view with "Duplicate to customize" button. Pattern: `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx`. |
| 257.7 | Create shared components for request templates | 257A | | New components: ResponseTypeBadge (FILE_UPLOAD -> "File Upload", TEXT_RESPONSE -> "Text"), TemplateSourceBadge (PLATFORM/CUSTOM). Add to `frontend/components/information-requests/` directory. |
| 257.8 | Write frontend tests for template management | 257A | 257.3, 257.6 | New test file: `frontend/__tests__/request-templates.test.tsx`. Tests (~10): list page renders templates, active filter works, create template form submission, edit template updates name, add item to template, update item response type, remove item, duplicate template, platform template is read-only, deactivate template. Pattern: existing settings page tests. |

### Key Files

**Slice 257A -- Create:**
- `frontend/lib/api/information-requests.ts`
- `frontend/app/(app)/org/[slug]/settings/request-templates/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/request-templates/actions.ts`
- `frontend/app/(app)/org/[slug]/settings/request-templates/new/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/request-templates/[id]/page.tsx`
- `frontend/components/information-requests/response-type-badge.tsx`
- `frontend/components/information-requests/template-source-badge.tsx`
- `frontend/__tests__/request-templates.test.tsx`

**Slice 257A -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` (nav item)
- `frontend/lib/nav-items.ts` (if settings nav is centralized)

**Read for context:**
- `frontend/app/(app)/org/[slug]/settings/project-templates/page.tsx` -- template management UI pattern
- `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx` -- template editor pattern
- `frontend/app/(app)/org/[slug]/settings/clauses/` -- settings page pattern

### Architecture Decisions

- **Single slice for all template pages**: Template management is self-contained (list + create + edit). The item editor is part of the edit page, not a separate component. This keeps the slice cohesive.
- **Platform templates read-only with duplicate action**: Users cannot edit platform-seeded templates. "Duplicate" creates a CUSTOM copy they can edit freely. This preserves the ability to re-seed/update platform templates in future versions.

---

## Epic 258: Firm-Side Request Pages & Review UI

**Goal**: Build the firm-side UI for creating, viewing, and managing information requests. Includes the create request dialog, customer/project "Requests" tabs, request detail page with accept/reject review actions, and shared UI components.

**References**: Architecture doc Section 11.11 (frontend changes), requirements Section 7 (firm-side frontend).

**Dependencies**: Epic 253 (backend request endpoints), Epic 257 (API client shared).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **258A** | 258.1--258.8 | CreateRequestDialog + RequestStatusBadge + ItemStatusBadge + RequestProgressBar + customer detail "Requests" tab + project detail "Requests" tab + request-actions.ts + API client extensions + tests (~8 tests). ~10 new/modified files. Frontend only. | **Done** (PR #549) |
| **258B** | 258.9--258.15 | information-requests/[id]/page.tsx detail page + item list with status + Accept/Reject review actions + RejectItemDialog modal + Cancel request + Resend notification + tests (~8 tests). ~5 new files. Frontend only. | **Done** (PR #550) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 258.1 | Extend API client with request CRUD + review endpoints | 258A | | Modify: `frontend/lib/api/information-requests.ts`. Add functions: listRequests(filters), createRequest(data), getRequest(id), updateRequest(id, data), sendRequest(id), cancelRequest(id), addItem(requestId, data), acceptItem(requestId, itemId), rejectItem(requestId, itemId, reason), resendNotification(requestId), getRequestSummary(), getCustomerRequests(customerId), getProjectRequests(projectId). |
| 258.2 | Create shared request UI components | 258A | | New files in `frontend/components/information-requests/`: `request-status-badge.tsx` (DRAFT=grey, SENT=blue, IN_PROGRESS=amber, COMPLETED=green, CANCELLED=red), `item-status-badge.tsx` (PENDING=grey, SUBMITTED=amber, ACCEPTED=green, REJECTED=red), `request-progress-bar.tsx` (X/Y items accepted, visual bar). Pattern: existing badge components. |
| 258.3 | Create CreateRequestDialog component | 258A | 258.1, 258.2 | New file: `frontend/components/information-requests/create-request-dialog.tsx`. Dialog with: template selection dropdown (from listTemplates, or "Ad-hoc"), customer picker (pre-filled if from customer page), project picker (optional, pre-filled if from project page), portal contact picker (dropdown of customer's contacts), reminder interval input (pre-filled from org settings). "Save as Draft" and "Send Now" actions. Pattern: existing create dialogs (e.g., CreateInvoiceDialog). |
| 258.4 | Create request list component | 258A | 258.2 | New file: `frontend/components/information-requests/request-list.tsx`. Reusable table: request number, customer name (if not customer-scoped), project name, status badge, progress bar, sent date, actions (view). Used by customer tab, project tab, and potentially a standalone list page. |
| 258.5 | Add "Requests" tab to customer detail page | 258A | 258.3, 258.4 | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`. Add new tab "Requests" alongside existing tabs. Content: RequestList component filtered by customerId. "New Request" button opens CreateRequestDialog with customer pre-filled. |
| 258.6 | Add "Requests" tab to project detail page | 258A | 258.4 | Modify: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Add new tab "Requests" (shown only if project has linked customer). Content: RequestList filtered by projectId. "New Request" button with project + customer pre-filled. |
| 258.7 | Create request server actions | 258A | 258.1 | New file: `frontend/app/(app)/org/[slug]/customers/[id]/request-actions.ts` (or shared location). Server actions: getCustomerRequests(), getProjectRequests(), createRequest(), sendRequest(). Pattern: existing action files (e.g., `invoice-actions.ts`). |
| 258.8 | Write frontend tests for request creation and tabs | 258A | 258.5, 258.6 | New test file: `frontend/__tests__/information-requests.test.tsx`. Tests (~8): CreateRequestDialog renders with template dropdown, create from template copies items, customer tab shows requests, project tab shows requests (with customer), project tab hidden (no customer), progress bar displays correctly, status badge colors, send action transitions status. |
| 258.9 | Create request detail page | 258B | | New file: `frontend/app/(app)/org/[slug]/information-requests/[id]/page.tsx`. Header: request number, customer name (linked), project name (if linked), portal contact name/email, status badge. Progress bar. Metadata: reminder interval, sent date, completed date. Pattern: existing detail pages (e.g., proposal detail). |
| 258.10 | Create request detail item list | 258B | 258.9 | In same page. Item list sorted by sortOrder. Each item: name, description, response type badge, status badge. PENDING: grey, waiting for client. SUBMITTED: amber, "Accept" + "Reject" buttons. ACCEPTED: green checkmark, linked document (viewable/downloadable) or text response (displayed). REJECTED: red, rejection reason shown. |
| 258.11 | Create RejectItemDialog component | 258B | | New file: `frontend/components/information-requests/reject-item-dialog.tsx`. Modal with textarea for rejection reason. "Reject" confirmation button. Pattern: existing confirmation dialogs. |
| 258.12 | Wire accept and reject actions | 258B | 258.10, 258.11 | In detail page. Accept: calls acceptItem API, refreshes page. Reject: opens RejectItemDialog, on confirm calls rejectItem API with reason, refreshes page. Both optimistically update UI. |
| 258.13 | Add cancel and resend actions | 258B | 258.9 | In detail page header dropdown menu. "Cancel Request" with confirmation dialog. "Resend Notification" action (re-sends email to portal contact). Both with success toasts. |
| 258.14 | Create request detail server actions | 258B | | New file: `frontend/app/(app)/org/[slug]/information-requests/[id]/actions.ts`. Server actions: getRequest(), acceptItem(), rejectItem(), cancelRequest(), resendNotification(). |
| 258.15 | Write frontend tests for detail page and review | 258B | 258.9 | New test file: `frontend/__tests__/information-request-detail.test.tsx`. Tests (~8): detail page renders header info, item list displays all items, accept button on SUBMITTED item, reject opens dialog, reject sends reason, accepted item shows document link, rejected item shows reason, cancel request, progress bar updates after accept. |

### Key Files

**Slice 258A -- Create:**
- `frontend/components/information-requests/request-status-badge.tsx`
- `frontend/components/information-requests/item-status-badge.tsx`
- `frontend/components/information-requests/request-progress-bar.tsx`
- `frontend/components/information-requests/create-request-dialog.tsx`
- `frontend/components/information-requests/request-list.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/request-actions.ts`
- `frontend/__tests__/information-requests.test.tsx`

**Slice 258A -- Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (new tab)
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` (new tab)
- `frontend/lib/api/information-requests.ts` (extensions)

**Slice 258B -- Create:**
- `frontend/app/(app)/org/[slug]/information-requests/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/information-requests/[id]/actions.ts`
- `frontend/components/information-requests/reject-item-dialog.tsx`
- `frontend/__tests__/information-request-detail.test.tsx`

**Read for context:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` -- tab integration pattern
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- tab integration pattern
- `frontend/components/` -- existing dialog and badge component patterns

### Architecture Decisions

- **Two slices for firm-side**: 258A handles list/creation (lower complexity), 258B handles detail/review (higher complexity with accept/reject interactions). Each slice stays within the 8-12 file target.
- **Reusable RequestList component**: Used in both customer and project tabs, avoiding code duplication.
- **Detail page is a standalone route**: `/information-requests/[id]/` rather than nested under customer or project. This allows direct linking from notifications, dashboard, etc.

---

## Epic 259: Portal Request Pages (Upload & Submit)

**Goal**: Build the portal-facing frontend pages for clients to view their information requests, upload files, provide text responses, and track progress. Includes the request list page, request detail page with upload/submit functionality, and portal navigation integration.

**References**: Architecture doc Section 11.11 (frontend changes), requirements Section 4 (portal frontend).

**Dependencies**: Epic 254 (portal backend API).

**Scope**: Frontend (Portal)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **259A** | 259.1--259.5 | Portal request list page + nav item + portalApi client + portal request components + tests (~6 tests). ~5 new/modified files. Frontend only. | |
| **259B** | 259.6--259.11 | Portal request detail page + file upload flow (dropzone, presigned URL, submit) + text response input + re-submission after rejection + tests (~6 tests). ~3 new files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 259.1 | Create portal API client for requests | 259A | | New file: `frontend/lib/api/portal-requests.ts`. Functions: listPortalRequests(), getPortalRequest(id), initiateUpload(requestId, itemId, data), submitItem(requestId, itemId, data). Uses existing portalApi fetch wrapper. Pattern: existing portal API client files. |
| 259.2 | Add "Requests" to portal navigation | 259A | | Modify: `frontend/app/portal/(authenticated)/layout.tsx`. Add "Requests" nav item (prominent placement, e.g., before Documents). Icon: InboxIcon or similar. Pattern: existing portal nav items. |
| 259.3 | Create portal request list page | 259A | 259.1, 259.2 | New file: `frontend/app/portal/(authenticated)/requests/page.tsx`. Lists all requests for the authenticated portal contact. Shows: request number, project name (if linked), status badge, progress bar (X/Y items), sent date. Sort by most recent. Filter: open/completed tabs. Pattern: `frontend/app/portal/(authenticated)/projects/page.tsx`. |
| 259.4 | Create portal request UI components | 259A | | New components in `frontend/components/portal/`: `portal-request-status-badge.tsx`, `portal-request-progress-bar.tsx`. Simplified versions of firm-side components, portal-themed. Pattern: existing portal components. |
| 259.5 | Write portal request list tests | 259A | 259.3 | New test file: `frontend/__tests__/portal-requests.test.tsx`. Tests (~6): list page renders requests, status badge colors, progress bar shows correct counts, filter by open/completed, empty state when no requests, project name shown when linked. |
| 259.6 | Create portal request detail page | 259B | 259.1 | New file: `frontend/app/portal/(authenticated)/requests/[id]/page.tsx`. Header: request number, project name (if linked), status, progress summary. Item list: each item shows name, description, response type indicator, status badge. |
| 259.7 | Implement file upload flow | 259B | 259.6 | In detail page. For FILE_UPLOAD items in PENDING or REJECTED state: dropzone/upload button. On file selected: (1) call initiateUpload for presigned URL, (2) upload file to S3 via presigned URL, (3) call submitItem with documentId. Shows upload progress. On success: item status updates to SUBMITTED. Pattern: existing document upload flow in portal. |
| 259.8 | Implement text response flow | 259B | 259.6 | In detail page. For TEXT_RESPONSE items in PENDING or REJECTED state: textarea input with "Submit" button. On submit: calls submitItem with textResponse. On success: item status updates to SUBMITTED. |
| 259.9 | Implement status-specific item display | 259B | 259.6 | PENDING: show upload/text input. SUBMITTED: "Awaiting review" indicator, show submitted file name or text (read-only). ACCEPTED: green checkmark, show file name or text. REJECTED: rejection reason displayed prominently, upload/text input re-enabled for re-submission. |
| 259.10 | Implement re-submission after rejection | 259B | 259.7, 259.8, 259.9 | For REJECTED items: rejection reason shown, upload/text input re-enabled. On re-submit: item transitions from REJECTED to SUBMITTED. Same upload/text flow as initial submission. |
| 259.11 | Write portal request detail tests | 259B | 259.6 | New test file: `frontend/__tests__/portal-request-detail.test.tsx`. Tests (~6): detail page renders items, file upload item shows dropzone, text response item shows textarea, accepted item is read-only, rejected item shows reason + re-submit, progress updates after submission. |

### Key Files

**Slice 259A -- Create:**
- `frontend/lib/api/portal-requests.ts`
- `frontend/app/portal/(authenticated)/requests/page.tsx`
- `frontend/components/portal/portal-request-status-badge.tsx`
- `frontend/components/portal/portal-request-progress-bar.tsx`
- `frontend/__tests__/portal-requests.test.tsx`

**Slice 259A -- Modify:**
- `frontend/app/portal/(authenticated)/layout.tsx` (nav item)

**Slice 259B -- Create:**
- `frontend/app/portal/(authenticated)/requests/[id]/page.tsx`
- `frontend/__tests__/portal-request-detail.test.tsx`

**Read for context:**
- `frontend/app/portal/(authenticated)/projects/page.tsx` -- portal page pattern
- `frontend/app/portal/(authenticated)/projects/[id]/page.tsx` -- portal detail page pattern
- `frontend/app/portal/(authenticated)/documents/page.tsx` -- portal document upload pattern (if exists)

### Architecture Decisions

- **Two slices for portal**: List page (simple, read-only display) vs. detail page (complex with upload/submit interactions). The detail page is the most complex single page in this phase due to the file upload flow.
- **Portal components separate from firm-side**: Portal has its own styled components (portal-request-status-badge, portal-request-progress-bar) to maintain visual consistency with the portal theme, which is distinct from the firm-side app.

---

## Epic 260: Dashboard Widget, Settings & Template Editor Integration

**Goal**: Add the "Information Requests" dashboard widget showing outstanding requests, overdue items, and completion rates. Configure the default reminder interval in org settings. Integrate request template selection into the project template editor.

**References**: Architecture doc Sections 11.8 (project template editor), 11.11 (dashboard integration).

**Dependencies**: Epic 255 (reminder interval in OrgSettings), Epic 256 (project template requestTemplateId), Epic 258 (firm-side request pages for click-through).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **260A** | 260.1--260.5 | Dashboard "Information Requests" widget + OrgSettings reminder interval config in settings page + tests (~5 tests). ~4 modified files. Frontend only. | |
| **260B** | 260.6--260.9 | Project template editor "Information Request Template" combobox + help text + persist via PUT + tests (~4 tests). ~2 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 260.1 | Create dashboard widget for information requests | 260A | | New component: `frontend/components/dashboard/information-requests-widget.tsx`. Shows: requests awaiting review (items SUBMITTED), overdue requests count, completion rate (last 30 days). Click-through to filtered request list. Uses getRequestSummary() API. Pattern: existing dashboard widgets on `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. |
| 260.2 | Integrate widget into company dashboard page | 260A | 260.1 | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. Add InformationRequestsWidget to dashboard grid. Position: alongside existing widgets. |
| 260.3 | Add request summary to dashboard server actions | 260A | | Modify: dashboard actions file. Add getInformationRequestSummary() server action. |
| 260.4 | Add reminder interval to org settings page | 260A | | Modify: `frontend/app/(app)/org/[slug]/settings/page.tsx` (or organization settings section). Add "Default Request Reminder Interval (days)" input field. Read from OrgSettings, persist via PUT /api/org-settings. Help text: "Automated reminders will be sent to clients every N days while items are outstanding." Pattern: existing OrgSettings fields in settings page. |
| 260.5 | Write dashboard and settings tests | 260A | 260.1, 260.4 | New or extend test file. Tests (~5): dashboard widget renders summary data, click-through navigates to requests, overdue count displays, settings page shows reminder interval, settings page saves reminder interval. |
| 260.6 | Add request template combobox to project template editor | 260B | | Modify: `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx`. New field: "Information Request Template" -- Combobox/Select populated from GET /api/request-templates?active=true. Optional field, can be cleared. Help text: "When a project is created from this template, a draft information request will be created for the linked customer." Pattern: existing combobox fields in template editor. |
| 260.7 | Add request template server action | 260B | | Modify: `frontend/app/(app)/org/[slug]/settings/project-templates/actions.ts`. Add getActiveRequestTemplates() server action for populating the dropdown. Extend updateTemplate() to include requestTemplateId. |
| 260.8 | Persist requestTemplateId on template save | 260B | 260.6 | In template editor page. Include requestTemplateId in the PUT /api/project-templates/{id} payload. Null when cleared. |
| 260.9 | Write template editor integration tests | 260B | 260.6 | New or extend test file. Tests (~4): template editor shows request template dropdown, selecting template saves requestTemplateId, clearing selection saves null, dropdown only shows active templates. |

### Key Files

**Slice 260A -- Create:**
- `frontend/components/dashboard/information-requests-widget.tsx`

**Slice 260A -- Modify:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` (widget integration)
- `frontend/app/(app)/org/[slug]/settings/page.tsx` (reminder interval)

**Slice 260B -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx` (combobox)
- `frontend/app/(app)/org/[slug]/settings/project-templates/actions.ts` (server action)

**Read for context:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` -- dashboard widget pattern
- `frontend/app/(app)/org/[slug]/settings/project-templates/[id]/page.tsx` -- template editor pattern

### Architecture Decisions

- **Dashboard widget and settings in one slice**: Both are small modifications to existing pages (4 files modified). Keeping them together avoids a slice that's too small to be worth the overhead.
- **Project template editor in separate slice**: The template editor modification is independent of the dashboard widget and may require reading the project template editor code. Separate slice keeps context focused.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase34-client-information-requests.md` - Full architecture specification with entity models, API specs, sequence diagrams, and migration SQL
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java` - Primary pattern reference for lifecycle state machine, numbering, event publishing
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` - Pattern for portal read-model sync event handlers
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` - Pattern for per-tenant scheduled processing with ScopedValue
- `/Users/rakheendama/Projects/2026/b2b-strawman/tasks/phase33-data-completeness-prerequisites.md` - Format reference for epic structure, slice sizing, and task ID conventions