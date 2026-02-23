# Phase 22 — Customer Portal Frontend

Phase 22 builds the **customer-facing portal frontend** -- a separate Next.js 16 application at `portal/` that gives clients a branded, self-service view into their projects, documents, invoices, and comment threads. It also extends the Phase 7 portal backend with invoice and task data in the portal read-model, new API endpoints for invoices/tasks/branding/comment posting, and a V8 global migration.

**Architecture doc**: `architecture/phase22-customer-portal-frontend.md`

**ADRs**:
- [ADR-076](../adr/ADR-076-separate-portal-app.md) -- Separate Next.js App for Customer Portal
- [ADR-077](../adr/ADR-077-portal-jwt-storage.md) -- Client-Side JWT Storage for Portal (localStorage)
- [ADR-078](../adr/ADR-078-portal-read-model-extension.md) -- Portal Read-Model Extension for Invoices and Tasks
- [ADR-079](../adr/ADR-079-portal-org-identification.md) -- Org Identification Strategy (JWT-derived, no URL org segment)

**Migration**: V8 global -- `portal_invoices`, `portal_invoice_lines`, `portal_tasks` tables in portal schema

**Dependencies on prior phases**: Phase 7 (portal backend prototype -- `PortalContact`, `MagicLinkToken`, portal read-model, `CustomerAuthFilter`, portal JWT, `PortalEventHandler`), Phase 10 (Invoice entity and lifecycle), Phase 5 (Task entity), Phase 8 (OrgSettings branding fields), Phase 12 (GeneratedDocument for invoice PDFs).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 153 | Portal Read-Model Extension -- Invoice Sync + Endpoints | Backend | -- | M | 153A, 153B | |
| 154 | Portal Read-Model Extension -- Task Sync + Endpoint | Backend | 153A | S | 154A | |
| 155 | Portal Branding Endpoint + Comment POST | Backend | -- | S | 155A | |
| 156 | Portal App Scaffolding + Auth Flow | Portal | 155 | M | 156A, 156B | |
| 157 | Portal Shell, Branding + Project List Page | Portal | 156 | M | 157A | |
| 158 | Portal Project Detail Page | Portal | 157, 154, 155 | M | 158A | |
| 159 | Portal Invoice List + Detail Pages | Portal | 157, 153 | M | 159A | |
| 160 | Portal Profile, Responsive Polish + Docker | Portal | 156-159 | S | 160A | |

---

## Dependency Graph

```
[E153A Invoice sync + V8 migration] --------> [E153B Invoice endpoints] -----------> [E159A Invoice pages]
  |                                                                                        |
  +-- [E154A Task sync + endpoint] ------------------------------------------> [E158A Project detail page]
                                                                                    |
[E155A Branding + Comment POST] ---> [E156A Portal scaffold] --> [E156B Auth pages] |
                                                |                                  |
                                         [E157A Shell + Projects] ------> [E158A] --+
                                                |                                  |
                                                +------> [E159A Invoice pages] ----+
                                                                                   |
                                                                           [E160A Polish + Docker]
```

**Parallel opportunities**:
- Epics 153, 154 (after 153A), and 155 are backend-only and can run in parallel in Stage 1.
- Epic 156 depends only on 155 (branding endpoint for login page display).
- Epics 158 and 159 are independent of each other and can run in parallel after 157.
- Epic 160 depends on all other portal slices.

---

## Implementation Order

### Stage 1: Backend foundations (parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a (parallel) | Epic 153 | 153A | V8 global migration (all 3 tables) + `InvoiceSyncEvent` + `PortalEventHandler` invoice sync handlers + `PortalReadModelRepository` invoice methods + view records. Foundation for both invoice and task endpoints. | **Done** (PR #323) |
| 1b (parallel) | Epic 155 | 155A | `PortalBrandingController` (public endpoint) + `PortalCommentController` POST method. Independent of invoice/task sync. |

### Stage 2: Backend -- remaining endpoints (parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a (parallel) | Epic 153 | 153B | `PortalInvoiceController` with 3 endpoints. Depends on 153A (read-model repo methods exist). |
| 2b (parallel) | Epic 154 | 154A | Task portal events + `PortalEventHandler` task sync + `GET /portal/projects/{id}/tasks`. Depends on 153A (V8 migration includes `portal_tasks` table). |

### Stage 3: Portal scaffold

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 156 | 156A | Portal app scaffolding: `package.json`, Next.js config, Tailwind, Shadcn init, `lib/auth.ts`, `lib/api-client.ts`, `lib/types.ts`, `hooks/use-auth.ts`. |
| 3b | Epic 156 | 156B | Login page, token exchange page, root redirect. Depends on 156A (auth layer) and 155A (branding endpoint). |

### Stage 4: Portal pages (partially parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 157 | 157A | Shell layout, BrandingProvider, header, footer, project list page. Depends on 156B (auth flow complete). |
| 4b (parallel) | Epic 158 | 158A | Project detail page (tasks, documents, comments, summary). Depends on 157A (shell) + 154A (task endpoint) + 155A (comment POST). |
| 4c (parallel) | Epic 159 | 159A | Invoice list and detail pages. Depends on 157A (shell) + 153B (invoice endpoints). |

### Stage 5: Polish

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5 | Epic 160 | 160A | Profile page, responsive polish, Dockerfile, Docker Compose entry. Depends on all portal slices. |

### Timeline

```
Stage 1: [153A] // [155A]                       (parallel)
Stage 2: [153B] // [154A]                       (parallel, after 153A)
Stage 3: [156A] --> [156B]                      (sequential, after 155A)
Stage 4: [157A] --> [158A] // [159A]            (157A then parallel)
Stage 5: [160A]                                 (after all)
```

**Critical path**: 155A --> 156A --> 156B --> 157A --> 158A --> 160A

---

## Epic 153: Portal Read-Model Extension -- Invoice Sync + Endpoints

**Goal**: Create the V8 global migration that adds `portal_invoices`, `portal_invoice_lines`, and `portal_tasks` tables to the portal schema. Implement invoice sync via `InvoiceSyncEvent` portal domain events and `PortalEventHandler` additions. Build the `PortalInvoiceController` with list, detail, and PDF download endpoints. The V8 migration covers ALL three tables (invoices, invoice lines, tasks) even though task sync is implemented in Epic 154 -- this avoids splitting a single migration across epics.

**References**: Architecture doc Sections 22.6.1 (migration DDL), 22.6.2 (invoice sync), 22.6.4 (invoice endpoints), 22.9 (V8 SQL).

**Dependencies**: None (standalone backend epic).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **153A** | 153.1--153.10 | V8 global migration (3 tables, 5 indexes) + `InvoiceSyncEvent` portal domain event + `PortalDomainEvent` permits update + `PortalEventHandler` invoice sync handlers (SENT upsert, PAID status update, VOID delete) + `PortalReadModelRepository` invoice upsert/delete/query methods + `PortalInvoiceView` and `PortalInvoiceLineView` records + `InvoiceService` publishes `InvoiceSyncEvent` on status transitions. ~6 new files, ~4 modified files. | **Done** (PR #323) |
| **153B** | 153.11--153.16 | `PortalInvoiceController` with 3 endpoints (list, detail, download) + response DTOs + `PortalReadModelService` invoice query methods + integration tests for all endpoints. ~3 new files, ~2 modified files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 153.1 | Create V8 global migration | 153A | | New file: `backend/src/main/resources/db/migration/global/V8__extend_portal_read_model.sql`. Contains DDL for `portal.portal_invoices`, `portal.portal_invoice_lines`, `portal.portal_tasks` tables plus 5 indexes. Exact SQL in architecture doc Section 22.9. All tables use `IF NOT EXISTS`. |
| 153.2 | Create `InvoiceSyncEvent.java` portal domain event | 153A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/InvoiceSyncEvent.java`. Extends `PortalDomainEvent`. Fields: `UUID invoiceId`, `UUID customerId`, `String invoiceNumber`, `String status`, `LocalDate issueDate`, `LocalDate dueDate`, `BigDecimal subtotal`, `BigDecimal taxAmount`, `BigDecimal total`, `String currency`, `String notes`. Constructor calls `super(orgId, tenantId)`. Pattern: `ProjectCreatedEvent.java`. |
| 153.3 | Update `PortalDomainEvent.java` permits list | 153A | 153.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalDomainEvent.java`. Add `InvoiceSyncEvent` to the `permits` clause. (Task sync events from Epic 154 will also be added later.) |
| 153.4 | Create `PortalInvoiceView.java` record | 153A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalInvoiceView.java`. Record: `(UUID id, String orgId, UUID customerId, String invoiceNumber, String status, LocalDate issueDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total, String currency, String notes)`. Pattern: `PortalProjectView.java`. |
| 153.5 | Create `PortalInvoiceLineView.java` record | 153A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalInvoiceLineView.java`. Record: `(UUID id, UUID portalInvoiceId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, int sortOrder)`. |
| 153.6 | Add invoice methods to `PortalReadModelRepository` | 153A | 153.4, 153.5 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java`. Add methods: `upsertPortalInvoice(...)`, `upsertPortalInvoiceLine(...)`, `updatePortalInvoiceStatus(UUID id, String status)`, `deletePortalInvoice(UUID id)`, `deletePortalInvoicesByOrg(String orgId)`, `findInvoicesByCustomer(String orgId, UUID customerId)`, `findInvoiceById(UUID id, String orgId)`, `findInvoiceLinesByInvoice(UUID portalInvoiceId)`, `deletePortalInvoiceLinesByInvoice(UUID portalInvoiceId)`. Use `JdbcClient`-based SQL with `ON CONFLICT (id) DO UPDATE` upsert pattern. Follow existing project/document methods in the same file. |
| 153.7 | Add invoice sync handlers to `PortalEventHandler` | 153A | 153.2, 153.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`. Add `@TransactionalEventListener(phase = AFTER_COMMIT)` handler `onInvoiceSynced(InvoiceSyncEvent event)`. Logic: if status is `SENT` -- upsert invoice + fetch and upsert all line items from tenant schema via `handleInTenantScope`. If status is `PAID` -- update status only. If status is `VOID` -- delete invoice (cascades to lines). Inject `InvoiceRepository` and `InvoiceLineRepository` (or the `InvoiceService`) for reading tenant data. Pattern: existing `onProjectCreated()` handler. |
| 153.8 | Publish `InvoiceSyncEvent` from `InvoiceService` | 153A | 153.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`. In the methods that transition invoice to SENT, PAID, or VOID status, publish an `InvoiceSyncEvent` via `ApplicationEventPublisher`. The event carries the full invoice data (for SENT) or just the ID and status (for PAID/VOID). Must happen alongside the existing `DomainEvent` publishing (audit/notification events), not replacing it. Check if the invoice's project is linked to a customer before publishing -- only customer-linked invoices should sync to the portal. |
| 153.9 | Write integration tests for invoice sync lifecycle | 153A | 153.7, 153.8 | New file: `backend/src/test/java/.../customerbackend/handler/PortalInvoiceSyncIntegrationTest.java`. Tests: (1) `sent_invoice_syncs_to_read_model` -- create invoice, mark SENT, verify `portal_invoices` + `portal_invoice_lines` rows. (2) `paid_invoice_updates_status` -- mark PAID, verify status column. (3) `voided_invoice_removed_from_read_model` -- mark VOID, verify deletion + cascade. (4) `draft_invoice_not_synced` -- create invoice (DRAFT), verify no read-model rows. (5) `invoice_without_customer_not_synced` -- invoice on project not linked to customer, verify no sync. (6) `org_deletion_cleans_up_invoices`. ~6 integration tests. Pattern: existing `PortalEventHandlerIntegrationTest`. |
| 153.10 | Write integration test for read-model repository invoice methods | 153A | 153.6 | Add tests to existing or new test file for `PortalReadModelRepository`: (1) `upsert_invoice_and_query_by_customer`. (2) `upsert_invoice_line_and_query_by_invoice`. (3) `delete_invoice_cascades_to_lines`. ~3 tests. |
| 153.11 | Create `PortalInvoiceController` with list endpoint | 153B | 153A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalInvoiceController.java`. `@RestController @RequestMapping("/portal/invoices")`. GET `/` -- reads `orgId` and `customerId` from `RequestScopes`, delegates to `PortalReadModelService.listInvoices(orgId, customerId)`. Returns `List<PortalInvoiceResponse>`. Pattern: `PortalProjectController`. |
| 153.12 | Add invoice detail endpoint to `PortalInvoiceController` | 153B | 153.11 | GET `/{id}` -- queries invoice by ID, verifies `orgId` matches. Queries line items. Returns `PortalInvoiceDetailResponse` (invoice + lines). Returns 404 if not found or wrong org/customer. |
| 153.13 | Add invoice PDF download endpoint to `PortalInvoiceController` | 153B | 153.11 | GET `/{id}/download` -- validates invoice belongs to authenticated customer's org. Looks up `GeneratedDocument` for this invoice (by entity reference). Generates presigned S3 URL via `StorageService.generateDownloadUrl()`. Returns `{ downloadUrl }`. Returns 404 if no PDF exists. Pattern: `PortalDocumentController.presignDownload()`. |
| 153.14 | Add invoice query methods to `PortalReadModelService` | 153B | 153.11 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalReadModelService.java`. Add `listInvoices(String orgId, UUID customerId)`, `getInvoiceDetail(UUID id, String orgId)`, `getInvoiceLines(UUID invoiceId)`. Delegates to `PortalReadModelRepository`. Validates project ownership where needed. |
| 153.15 | Write integration tests for `PortalInvoiceController` | 153B | 153.11, 153.12, 153.13 | New file: `backend/src/test/java/.../customerbackend/controller/PortalInvoiceControllerIntegrationTest.java`. Tests: (1) `list_invoices_returns_customer_invoices_only`. (2) `list_invoices_empty_state`. (3) `get_invoice_detail_with_lines`. (4) `get_invoice_detail_wrong_org_returns_404`. (5) `download_returns_presigned_url`. (6) `download_no_pdf_returns_404`. (7) `unauthorized_returns_401`. ~7 integration tests. Use portal JWT test helper. Pattern: `PortalProjectControllerIntegrationTest`. |
| 153.16 | Create response DTOs for invoice endpoints | 153B | | Define as nested records in `PortalInvoiceController`: `PortalInvoiceResponse(UUID id, String invoiceNumber, String status, LocalDate issueDate, LocalDate dueDate, BigDecimal total, String currency)`, `PortalInvoiceDetailResponse(UUID id, String invoiceNumber, String status, LocalDate issueDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total, String currency, String notes, List<PortalInvoiceLineResponse> lines)`, `PortalInvoiceLineResponse(UUID id, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, int sortOrder)`, `PortalDownloadResponse(String downloadUrl)`. |

### Key Files

**Slice 153A -- Create:**
- `backend/src/main/resources/db/migration/global/V8__extend_portal_read_model.sql` -- 3 tables, 5 indexes
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/InvoiceSyncEvent.java` -- Portal domain event
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalInvoiceView.java` -- Query result record
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalInvoiceLineView.java` -- Query result record
- `backend/src/test/java/.../customerbackend/handler/PortalInvoiceSyncIntegrationTest.java` -- 6 integration tests

**Slice 153A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalDomainEvent.java` -- Add `InvoiceSyncEvent` to permits
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java` -- Add 9 invoice methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` -- Add `onInvoiceSynced()` handler
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Publish `InvoiceSyncEvent`

**Slice 153B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalInvoiceController.java` -- 3 endpoints + DTOs
- `backend/src/test/java/.../customerbackend/controller/PortalInvoiceControllerIntegrationTest.java` -- 7 integration tests

**Slice 153B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalReadModelService.java` -- Add 3 invoice query methods

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` -- Existing event handler pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java` -- JdbcClient upsert pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalProjectController.java` -- Controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalDocumentController.java` -- Presigned download pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Where to publish sync events

### Architecture Decisions

- **V8 migration includes all 3 tables**: `portal_invoices`, `portal_invoice_lines`, and `portal_tasks` are added in a single migration even though task sync is in Epic 154. Splitting DDL across migrations for the same schema extension is fragile.
- **Status gate at sync time, not query time**: Only SENT and PAID invoices enter the portal read-model. DRAFT and APPROVED stay internal. VOID removes the row. This is enforced in the `PortalEventHandler`, not in the query. See ADR-078.
- **`InvoiceSyncEvent` carries full data for SENT**: The handler needs invoice + line items to upsert. Rather than re-querying the tenant schema in the handler, the event carries all necessary fields. For PAID/VOID, only ID and status are needed.
- **Customer linkage check before publishing**: Only invoices on projects linked to a customer are synced. Check `project.getCustomer() != null` before publishing `InvoiceSyncEvent`.

---

## Epic 154: Portal Read-Model Extension -- Task Sync + Endpoint

**Goal**: Implement task sync to the portal read-model via portal domain events. Build the `GET /portal/projects/{projectId}/tasks` endpoint. The `portal_tasks` table already exists from the V8 migration in Epic 153A.

**References**: Architecture doc Sections 22.6.3 (task sync), 22.6.5 (task endpoint).

**Dependencies**: Epic 153A (V8 migration must exist for `portal_tasks` table).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **154A** | 154.1--154.9 | `PortalTaskCreatedEvent`, `PortalTaskUpdatedEvent`, `PortalTaskDeletedEvent` portal domain events + `PortalDomainEvent` permits update + `PortalEventHandler` task sync handlers + `PortalReadModelRepository` task methods + `PortalTaskView` record + `GET /portal/projects/{id}/tasks` endpoint + `TaskService` publishes portal events + integration tests. ~5 new files, ~5 modified files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 154.1 | Create `PortalTaskCreatedEvent.java` | 154A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalTaskCreatedEvent.java`. Extends `PortalDomainEvent`. Fields: `UUID taskId`, `UUID projectId`, `String name`, `String status`, `String assigneeName`, `int sortOrder`. Pattern: `ProjectCreatedEvent.java`. |
| 154.2 | Create `PortalTaskUpdatedEvent.java` | 154A | | New file: same package. Extends `PortalDomainEvent`. Same fields as created event. Used for status changes, assignments, name edits. |
| 154.3 | Create `PortalTaskDeletedEvent.java` | 154A | | New file: same package. Extends `PortalDomainEvent`. Fields: `UUID taskId`. Used when a task is deleted. |
| 154.4 | Update `PortalDomainEvent.java` permits list | 154A | 154.1-154.3 | Modify: `PortalDomainEvent.java`. Add `PortalTaskCreatedEvent`, `PortalTaskUpdatedEvent`, `PortalTaskDeletedEvent` to permits clause. |
| 154.5 | Create `PortalTaskView.java` record | 154A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalTaskView.java`. Record: `(UUID id, String orgId, UUID portalProjectId, String name, String status, String assigneeName, int sortOrder)`. |
| 154.6 | Add task methods to `PortalReadModelRepository` | 154A | 154.5 | Modify: `PortalReadModelRepository.java`. Add: `upsertPortalTask(UUID id, String orgId, UUID portalProjectId, String name, String status, String assigneeName, int sortOrder)`, `deletePortalTask(UUID id)`, `deleteTasksByPortalProjectId(UUID projectId, String orgId)`, `deletePortalTasksByOrg(String orgId)`, `findTasksByProject(UUID portalProjectId, String orgId)`. JdbcClient pattern with `ON CONFLICT (id) DO UPDATE`. |
| 154.7 | Add task sync handlers to `PortalEventHandler` | 154A | 154.1-154.3, 154.6 | Modify: `PortalEventHandler.java`. Add 3 handlers: `onTaskCreated(PortalTaskCreatedEvent)` -- upsert to `portal_tasks`. `onTaskUpdated(PortalTaskUpdatedEvent)` -- upsert (same SQL). `onTaskDeleted(PortalTaskDeletedEvent)` -- delete row. Also update existing `onProjectDeleted()` handler to call `deleteTasksByPortalProjectId()` as a backup (DB cascade handles it via FK, but explicit cleanup is documented in architecture). |
| 154.8 | Publish portal task events from `TaskService` | 154A | 154.1-154.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java`. On task create, update, status change, assign, claim -- publish `PortalTaskCreatedEvent` or `PortalTaskUpdatedEvent`. On delete -- publish `PortalTaskDeletedEvent`. Only publish if the task's project is linked to a customer (`project.getCustomer() != null`). Resolve assignee name from `Member` entity at publish time. |
| 154.9 | Add task list endpoint + integration tests | 154A | 154.6, 154.7, 154.8 | Add `GET /portal/projects/{projectId}/tasks` to `PortalProjectController` (or create `PortalTaskController`). Reads `orgId`, `customerId` from `RequestScopes`. Verifies project belongs to customer. Queries `PortalReadModelRepository.findTasksByProject()`. Returns `List<PortalTaskResponse>`. Response DTO: `PortalTaskResponse(UUID id, String name, String status, String assigneeName, int sortOrder)`. New test file: `PortalTaskSyncIntegrationTest.java` with ~8 tests: (1) task_created_syncs_to_read_model, (2) task_updated_syncs, (3) task_deleted_removes, (4) task_in_non_customer_project_not_synced, (5) project_delete_cascades_tasks, (6) list_tasks_endpoint_returns_project_tasks, (7) list_tasks_wrong_customer_returns_404, (8) list_tasks_unauthorized_returns_401. |

### Key Files

**Slice 154A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalTaskCreatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalTaskUpdatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalTaskDeletedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalTaskView.java`
- `backend/src/test/java/.../customerbackend/PortalTaskSyncIntegrationTest.java`

**Slice 154A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalDomainEvent.java` -- Add 3 task events to permits
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java` -- Add 5 task methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` -- Add 3 task handlers + update project delete handler
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- Publish portal task events
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalProjectController.java` (or new controller) -- Add task list endpoint

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- Where to publish events
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java` -- Task entity fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/ProjectCreatedEvent.java` -- Event pattern

### Architecture Decisions

- **Minimal task projection**: Only `name`, `status`, and `assignee_name` are synced. No description, estimated hours, billable flag, or time entries. Internal details stay internal per ADR-078.
- **Assignee name resolved at sync time**: The portal read-model stores the display name string, not a member ID. This avoids cross-schema joins at query time. If the member's name changes, the task row is stale until the next task update -- acceptable for v1.
- **Endpoint on `PortalProjectController`**: The task list is scoped to a project, matching the existing `/portal/projects/{id}/documents` and `/portal/projects/{id}/comments` patterns. A separate controller would be premature.

---

## Epic 155: Portal Branding Endpoint + Comment POST

**Goal**: Create the public branding endpoint (`GET /portal/branding?orgId=...`) that the login page calls before authentication. Add comment posting capability (`POST /portal/projects/{projectId}/comments`) for portal contacts. Update `CustomerAuthFilter` to skip authentication on the branding path.

**References**: Architecture doc Sections 22.6.6 (branding), 22.6.7 (comment POST).

**Dependencies**: None (fully independent of invoice/task sync).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **155A** | 155.1--155.9 | `PortalBrandingController` (public endpoint, no auth) + `CustomerAuthFilter` skip path update + `PortalCommentController` POST method + content validation + comment source=PORTAL + integration tests. ~2 new files, ~3 modified files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 155.1 | Create `PortalBrandingController` | 155A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalBrandingController.java`. `@RestController`. GET `/portal/branding?orgId={orgId}`. Resolves org's schema from `OrgSchemaMappingRepository.findByClerkOrgId(orgId)`. Within tenant scope, reads `OrgSettings` for `logoS3Key`, `brandColor`, `documentFooterText`. If `logoS3Key` set, generates presigned URL via `StorageService.generateDownloadUrl()`. Returns `BrandingResponse(String orgName, String logoUrl, String brandColor, String footerText)`. Adds `Cache-Control: public, max-age=3600` header. Returns 404 for invalid/unprovisioned org. |
| 155.2 | Update `CustomerAuthFilter.shouldNotFilter()` | 155A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/CustomerAuthFilter.java`. Add `/portal/branding` to the list of paths that skip authentication. Currently skips `/portal/auth/*` and `/portal/dev/*`. |
| 155.3 | Add POST method to `PortalCommentController` | 155A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalCommentController.java`. Add `POST` handler. Request body: `CreateCommentRequest(String content)`. Validation: `@NotBlank`, `@Size(max = 2000)`. Reads `customerId`, `orgId` from `RequestScopes`. Verifies project belongs to customer (via `PortalReadModelService`). Creates `Comment` entity in tenant schema with `source = PORTAL` and contact's display name as author. |
| 155.4 | Implement comment creation in tenant schema | 155A | 155.3 | The POST handler needs to create a `Comment` entity in the tenant schema. This requires switching to the tenant context (via `handleInTenantScope` or similar pattern from `PortalEventHandler`). Inject `CommentService` or `CommentRepository` + `ApplicationEventPublisher`. Set comment `source` field to `PORTAL`. Resolve contact display name from `PortalContact` or from the portal JWT claims. Publish `CommentCreatedEvent` for notifications. Return 201 Created with `PortalCommentResponse(id, authorName, content, createdAt)`. |
| 155.5 | Sync portal comment to read-model | 155A | 155.4 | Ensure the comment created via POST is synced to the portal read-model. If the existing `PortalEventHandler.onCommentCreated()` handler exists and listens for `CommentCreatedEvent`, this happens automatically. If not, verify the comment appears in the read-model after creation. The existing GET endpoint should return the new comment. |
| 155.6 | Write integration tests for branding endpoint | 155A | 155.1 | Tests: (1) `branding_returns_org_name_and_color`. (2) `branding_without_logo_returns_null_logoUrl`. (3) `branding_unknown_org_returns_404`. (4) `branding_has_cache_control_header`. (5) `branding_no_auth_required`. ~5 tests. |
| 155.7 | Write integration tests for comment POST | 155A | 155.3, 155.4 | Tests: (1) `post_comment_returns_201_with_comment`. (2) `post_comment_empty_content_returns_400`. (3) `post_comment_exceeds_2000_chars_returns_400`. (4) `post_comment_wrong_project_returns_404`. (5) `post_comment_without_auth_returns_401`. (6) `post_comment_appears_in_get_list`. ~6 tests. |
| 155.8 | Verify branding endpoint with presigned logo URL | 155A | 155.1 | Integration test: create org with `logoS3Key` set, call branding endpoint, verify `logoUrl` is a presigned URL string (not null, contains expected key prefix). Mock `StorageService` for S3 interaction. |
| 155.9 | Verify comment notification trigger | 155A | 155.4 | Integration test: post a comment via portal, verify `CommentCreatedEvent` is published (or verify that notifications would be fanned out). This ensures the existing notification pipeline handles portal comments. |

### Key Files

**Slice 155A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalBrandingController.java` -- Public branding endpoint
- `backend/src/test/java/.../portal/PortalBrandingControllerIntegrationTest.java` -- 5 branding tests
- `backend/src/test/java/.../customerbackend/controller/PortalCommentPostIntegrationTest.java` -- 6 comment POST tests

**Slice 155A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/CustomerAuthFilter.java` -- Add `/portal/branding` to skip list
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalCommentController.java` -- Add POST method + request DTO

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/CustomerAuthFilter.java` -- `shouldNotFilter()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` -- OrgSettings access pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java` -- Comment creation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- ScopedValue access
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` -- `handleInTenantScope` pattern

### Architecture Decisions

- **Public branding endpoint**: No auth required. Org IDs are not secret (they are Clerk org IDs visible in URLs). The endpoint returns only branding data (logo, color, name). This is acceptable per architecture doc Section 22.6.6.
- **Comment source = PORTAL**: Distinguishes portal comments from internal team comments in the activity feed and notifications. The existing `Comment` entity likely has a `source` field or can be extended.
- **Content validation at 2000 characters**: Matches the portal UI character limit. Server-side validation is the enforcement point.

---

## Epic 156: Portal App Scaffolding + Auth Flow

**Goal**: Create the `portal/` Next.js 16 application from scratch. Set up the project scaffolding, Shadcn UI initialization, auth utilities (`lib/auth.ts`, `lib/api-client.ts`), TypeScript types, and the login/token exchange pages. After this epic, a user can request a magic link, exchange it for a JWT, and be redirected to the authenticated area.

**References**: Architecture doc Sections 22.2 (directory structure), 22.3 (auth flow).

**Dependencies**: Epic 155 (branding endpoint for login page display).

**Scope**: Portal (new Next.js app)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **156A** | 156.1--156.7 | Portal directory scaffolding: `package.json`, `next.config.ts`, `tsconfig.json`, `tailwind.config.ts`, `vitest.config.ts`, Shadcn UI init (Button, Card, Input, Label), `lib/auth.ts`, `lib/api-client.ts`, `lib/types.ts`, `hooks/use-auth.ts`, `app/layout.tsx` (root HTML shell). ~12 new files. |  |
| **156B** | 156.8--156.14 | `app/login/page.tsx` (magic link request form with org branding), `app/auth/exchange/page.tsx` (token exchange + JWT storage + redirect), `app/page.tsx` (root redirect), `app/not-found.tsx`, auth flow tests. ~6 new files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 156.1 | Create `portal/package.json` | 156A | | New file: `portal/package.json`. Dependencies: `next` 16.x, `react` 19.x, `react-dom` 19.x, `jose` (JWT decoding). Dev dependencies: `typescript` 5.x, `@types/react`, `@types/node`, `vitest`, `@testing-library/react`, `@testing-library/dom`, `happy-dom`, `tailwindcss` v4, `@tailwindcss/postcss`, `postcss`. Scripts: `dev`, `build`, `start`, `test`, `lint`. Pattern: `frontend/package.json` (same Next.js version, same Tailwind version). |
| 156.2 | Create `portal/next.config.ts`, `tsconfig.json`, `tailwind.config.ts`, `vitest.config.ts`, `postcss.config.mjs` | 156A | 156.1 | Configuration files. `next.config.ts`: `output: "standalone"`, no Clerk integration. `tsconfig.json`: `@/*` path alias pointing to portal root. `tailwind.config.ts`: duplicate color palette/fonts from `frontend/tailwind.config.ts`. `vitest.config.ts`: same as `frontend/vitest.config.ts` with `@/*` alias. `postcss.config.mjs`: `@tailwindcss/postcss` plugin. ~5 config files. |
| 156.3 | Initialize Shadcn UI components | 156A | 156.2 | Create `portal/components/ui/` with the essential Shadcn components: `button.tsx`, `card.tsx`, `input.tsx`, `label.tsx`, `badge.tsx`, `separator.tsx`, `skeleton.tsx`. Copy from `frontend/components/ui/` -- these are stable, small files. Also create `portal/app/globals.css` with Tailwind directives and the same color tokens as `frontend/app/globals.css`. ~8 files. |
| 156.4 | Create `portal/lib/auth.ts` | 156A | | New file. Auth utilities: `storeAuth(jwt: string, customer: CustomerInfo)` -- stores in localStorage. `getAuth(): { jwt: string, customer: CustomerInfo } | null` -- reads from localStorage, checks JWT expiry via `jose.decodeJwt()`. `clearAuth()` -- removes from localStorage. `isAuthenticated(): boolean`. `getJwt(): string | null`. Types: `CustomerInfo { id: string, name: string, email: string, orgId: string }`. Keys: `portal_jwt`, `portal_customer`. |
| 156.5 | Create `portal/lib/api-client.ts` | 156A | 156.4 | New file. Fetch wrapper: `portalFetch(path: string, options?: RequestInit): Promise<Response>`. Reads JWT from `auth.getJwt()`. Adds `Authorization: Bearer {jwt}` header. Prepends `NEXT_PUBLIC_PORTAL_API_URL` to path. On 401 response: calls `clearAuth()`, redirects to `/login`. Throws on network errors. Helper: `portalGet<T>(path: string): Promise<T>` -- calls `portalFetch`, parses JSON. |
| 156.6 | Create `portal/lib/types.ts` | 156A | | New file. TypeScript types matching all portal API response shapes: `PortalProject`, `PortalProjectDetail`, `PortalDocument`, `PortalComment`, `PortalTask`, `PortalInvoice`, `PortalInvoiceDetail`, `PortalInvoiceLine`, `PortalProjectSummary`, `PortalProfile`, `BrandingInfo`, `AuthExchangeResponse`. ~12 type definitions. |
| 156.7 | Create `portal/hooks/use-auth.ts` and root layout | 156A | 156.4 | New files. `hooks/use-auth.ts`: React hook wrapping `lib/auth.ts`. Returns `{ isAuthenticated, jwt, customer, logout }`. `logout()` calls `clearAuth()` + `router.push('/login')`. `app/layout.tsx`: root HTML shell with font loading, `<html>` + `<body>` + `{children}`. Import `globals.css`. No auth logic here -- that lives in the authenticated layout. |
| 156.8 | Create `portal/app/login/page.tsx` | 156B | 156A | New file. Login page: reads `orgId` from URL search params (`?orgId=...`). If `orgId` present, fetches branding from `GET /portal/branding?orgId={orgId}` and displays org logo + name. Email input form. On submit, calls `POST /portal/auth/request-link { email, orgId }`. Success state: "Check your email for a login link." Error states: rate limit, network error. If no `orgId`, shows generic DocTeams login without branding. Client component (`"use client"`). |
| 156.9 | Create `portal/app/auth/exchange/page.tsx` | 156B | 156A | New file. Token exchange page: reads `token` and `orgId` from URL search params. On mount (`useEffect`), calls `POST /portal/auth/exchange { token, orgId }`. On success: calls `storeAuth(jwt, customer)`, redirects to `/projects`. On failure: shows "Link expired or invalid" with "Back to Login" button. Loading state while exchanging. Client component. |
| 156.10 | Create `portal/app/page.tsx` (root redirect) | 156B | 156A | New file. Root page: checks `isAuthenticated()`. If authenticated, redirect to `/projects`. If not, redirect to `/login`. Client component with `useEffect` + `router.push()`. |
| 156.11 | Create `portal/app/not-found.tsx` | 156B | | New file. Simple "Page not found" with link back to `/projects`. |
| 156.12 | Write tests for `lib/auth.ts` | 156B | 156.4 | New test file: `portal/lib/__tests__/auth.test.ts`. Tests: (1) `storeAuth_saves_to_localStorage`. (2) `getAuth_returns_stored_data`. (3) `getAuth_returns_null_when_expired`. (4) `clearAuth_removes_entries`. (5) `isAuthenticated_returns_false_when_no_jwt`. Mock `localStorage` and `jose.decodeJwt`. ~5 tests. |
| 156.13 | Write tests for `lib/api-client.ts` | 156B | 156.5 | New test file: `portal/lib/__tests__/api-client.test.ts`. Tests: (1) `portalFetch_adds_auth_header`. (2) `portalFetch_prepends_base_url`. (3) `portalFetch_on_401_clears_auth_and_redirects`. (4) `portalGet_parses_json`. Mock `fetch` and `auth`. ~4 tests. |
| 156.14 | Write tests for login and exchange pages | 156B | 156.8, 156.9 | New test files: `portal/app/login/__tests__/page.test.tsx`, `portal/app/auth/exchange/__tests__/page.test.tsx`. Login tests: (1) renders email input, (2) shows success message after submit, (3) displays branding when orgId present. Exchange tests: (1) redirects to /projects on success, (2) shows error on failure, (3) shows loading state. ~6 tests total. |

### Key Files

**Slice 156A -- Create:**
- `portal/package.json` -- Project manifest
- `portal/next.config.ts` -- Next.js configuration (standalone output, no Clerk)
- `portal/tsconfig.json` -- TypeScript config with `@/*` alias
- `portal/tailwind.config.ts` -- Tailwind v4 config (duplicated from frontend)
- `portal/vitest.config.ts` -- Test config
- `portal/app/globals.css` -- Tailwind directives + color tokens
- `portal/app/layout.tsx` -- Root HTML shell
- `portal/components/ui/*.tsx` -- Shadcn UI components (7 files)
- `portal/lib/auth.ts` -- JWT storage/retrieval/expiry
- `portal/lib/api-client.ts` -- Fetch wrapper with JWT injection
- `portal/lib/types.ts` -- TypeScript types for all API responses
- `portal/hooks/use-auth.ts` -- Auth state hook

**Slice 156B -- Create:**
- `portal/app/login/page.tsx` -- Magic link request form
- `portal/app/auth/exchange/page.tsx` -- Token exchange page
- `portal/app/page.tsx` -- Root redirect
- `portal/app/not-found.tsx` -- 404 page
- `portal/lib/__tests__/auth.test.ts` -- 5 auth utility tests
- `portal/lib/__tests__/api-client.test.ts` -- 4 API client tests
- `portal/app/login/__tests__/page.test.tsx` -- 3 login page tests
- `portal/app/auth/exchange/__tests__/page.test.tsx` -- 3 exchange page tests

**Read for context:**
- `frontend/package.json` -- Dependency versions to match
- `frontend/tailwind.config.ts` -- Color palette to duplicate
- `frontend/vitest.config.ts` -- Test config to duplicate
- `frontend/app/globals.css` -- CSS tokens to duplicate
- `frontend/components/ui/button.tsx` -- Shadcn component to copy

### Architecture Decisions

- **Separate app per ADR-076**: The portal is a distinct Next.js application in `portal/`, not a route group in `frontend/`. Auth isolation is the primary driver -- no Clerk middleware, no Clerk SDK.
- **localStorage for JWT per ADR-077**: The portal JWT is stored client-side. 1-hour TTL limits risk. Read-only access limits impact of XSS.
- **JWT-derived org identity per ADR-079**: No org segment in URLs. The login page receives `orgId` via query parameter from the magic link email. After auth, `orgId` comes from the JWT claims.
- **`jose` for JWT decoding**: Lightweight library for decoding JWT claims (expiry check). No verification -- the backend verifies the JWT.

---

## Epic 157: Portal Shell, Branding + Project List Page

**Goal**: Build the authenticated portal shell (header, footer, navigation, BrandingProvider context) and the project list page (the default landing page after authentication). After this epic, authenticated users see a branded portal with a project grid.

**References**: Architecture doc Sections 22.4 (shell and branding), 22.5.1 (project list).

**Dependencies**: Epic 156 (auth layer, API client, types).

**Scope**: Portal

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **157A** | 157.1--157.10 | `BrandingProvider` context + `use-branding` hook + `(authenticated)/layout.tsx` (auth guard + shell) + `portal-header.tsx` (logo, nav, profile, mobile menu) + `portal-footer.tsx` + CSS custom properties + `project-card.tsx` + `status-badge.tsx` + `(authenticated)/projects/page.tsx` (project grid, empty state) + tests. ~10 new files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 157.1 | Create `portal/components/branding-provider.tsx` | 157A | | New file. React context provider. `BrandingContext { orgName, logoUrl, brandColor, footerText, isLoading }`. Fetches branding from `GET /portal/branding?orgId={orgId}` using orgId from stored customer info. Fallbacks: no logo = text-only header, no brandColor = `#3B82F6`, no footerText = null. |
| 157.2 | Create `portal/hooks/use-branding.ts` | 157A | 157.1 | New file. Simple context consumer hook: `useBranding(): BrandingContext`. Throws if used outside `BrandingProvider`. |
| 157.3 | Create `portal/app/(authenticated)/layout.tsx` | 157A | 157.1 | New file. Auth guard: reads JWT via `useAuth()`. If not authenticated or expired, redirect to `/login`. Wraps children in `BrandingProvider`. Renders shell: `<PortalHeader />`, `<main>{children}</main>`, `<PortalFooter />`. Sets CSS custom property `--portal-brand-color` on root element from branding context. Content container: `max-w-6xl mx-auto` with responsive padding. Client component. |
| 157.4 | Create `portal/components/portal-header.tsx` | 157A | 157.2 | New file. Fixed-top bar. Left: org logo (from branding, or org name text if no logo). Center: navigation links -- "Projects" and "Invoices" with active state highlighting using brand color underline. Right: contact display name + "Profile" link + "Logout" button. Mobile: hamburger menu for navigation links. Uses `useBranding()` and `useAuth()` hooks. |
| 157.5 | Create `portal/components/portal-footer.tsx` | 157A | 157.2 | New file. Fixed at page bottom. Shows org's custom footer text (from branding) if set. Always shows "Powered by DocTeams" in small, unobtrusive text. |
| 157.6 | Create `portal/components/status-badge.tsx` | 157A | | New file. Reusable status indicator component. Props: `status: string`, optional `variant` for different color maps. Project statuses: ACTIVE=green, COMPLETED=blue, ON_HOLD=yellow, CANCELLED=gray. Task statuses: OPEN=gray, IN_PROGRESS=blue, REVIEW=yellow, DONE=green. Uses Shadcn `Badge` component with dynamic color classes. |
| 157.7 | Create `portal/components/project-card.tsx` | 157A | 157.6 | New file. Project summary card for grid layout. Props: `project: PortalProject`. Displays: project name (bold), status badge, description excerpt (2 lines truncated), document count icon + count, last activity date (relative). Click navigates to `/projects/{id}`. Uses Shadcn `Card`. |
| 157.8 | Create `portal/app/(authenticated)/projects/page.tsx` | 157A | 157.7 | New file. Project list page (home/dashboard). Title: "Your Projects". Fetches projects from `GET /portal/projects`. Responsive grid: 1 col mobile, 2 cols tablet, 3 cols desktop. Renders `ProjectCard` for each. Empty state: "No projects yet. Your {orgName} team will share projects with you here." Loading skeleton while fetching. Client component. |
| 157.9 | Write component tests | 157A | 157.4, 157.6, 157.7 | New test files: `portal/components/__tests__/portal-header.test.tsx` (~3 tests: renders nav links, shows logout, shows branding), `portal/components/__tests__/project-card.test.tsx` (~2 tests: renders project info, truncates description), `portal/components/__tests__/status-badge.test.tsx` (~2 tests: renders correct colors for statuses). |
| 157.10 | Write project list page test | 157A | 157.8 | New test file: `portal/app/(authenticated)/projects/__tests__/page.test.tsx`. Tests: (1) renders project cards when data returned, (2) renders empty state when no projects, (3) shows loading skeleton. ~3 tests. Mock `portalGet`. |

### Key Files

**Slice 157A -- Create:**
- `portal/components/branding-provider.tsx` -- BrandingProvider context
- `portal/hooks/use-branding.ts` -- Branding context hook
- `portal/app/(authenticated)/layout.tsx` -- Auth guard + portal shell
- `portal/components/portal-header.tsx` -- Header with nav, branding, mobile menu
- `portal/components/portal-footer.tsx` -- Footer with "Powered by DocTeams"
- `portal/components/status-badge.tsx` -- Reusable status indicator
- `portal/components/project-card.tsx` -- Project summary card
- `portal/app/(authenticated)/projects/page.tsx` -- Project list page
- `portal/components/__tests__/portal-header.test.tsx` -- 3 tests
- `portal/components/__tests__/project-card.test.tsx` -- 2 tests
- `portal/components/__tests__/status-badge.test.tsx` -- 2 tests
- `portal/app/(authenticated)/projects/__tests__/page.test.tsx` -- 3 tests

**Read for context:**
- `frontend/components/desktop-sidebar.tsx` -- Navigation pattern reference
- `frontend/app/(app)/org/[slug]/layout.tsx` -- Org-scoped layout pattern
- `frontend/components/ui/card.tsx` -- Shadcn Card component pattern
- Architecture doc Sections 22.4, 22.5.1

### Architecture Decisions

- **No sidebar, header-only nav**: Clients have 3 sections (projects, invoices, profile). A sidebar is overkill. Header navigation is cleaner for a read-heavy portal.
- **CSS custom property for brand color**: `--portal-brand-color` is set on the `(authenticated)/layout.tsx` root element from the branding context. Components use `var(--portal-brand-color)` for primary accents. This is simpler than passing brand color as a prop through every component.
- **Content-centered layout**: `max-w-6xl mx-auto` container. Professional, clean aesthetic.

---

## Epic 158: Portal Project Detail Page

**Goal**: Build the project detail page with all sections: header, summary card, tasks, documents, and comments (including the comment reply form). This is the most complex portal page, combining data from 5 API endpoints.

**References**: Architecture doc Section 22.5.2.

**Dependencies**: Epic 157 (shell, status badge), Epic 154 (task endpoint), Epic 155 (comment POST).

**Scope**: Portal

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **158A** | 158.1--158.9 | `(authenticated)/projects/[id]/page.tsx` (project detail with 5 sections) + `task-list.tsx` + `document-list.tsx` + `comment-section.tsx` (with reply form) + summary card + tests. ~6 new files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 158.1 | Create `portal/components/task-list.tsx` | 158A | | New file. Read-only task list. Props: `tasks: PortalTask[]`. Renders table/list with columns: task name, status badge, assignee name (or "Unassigned"). Uses `StatusBadge` for task statuses. Empty state: "No tasks yet." No edit/create actions. |
| 158.2 | Create `portal/components/document-list.tsx` | 158A | | New file. Document table. Props: `documents: PortalDocument[]`, `onDownload: (id: string) => void`. Columns: file name, type icon (based on content type), file size (formatted), uploaded date. Download button per row. On download click, calls `GET /portal/documents/{id}/presign-download` via `portalGet`, then opens presigned URL in new tab (`window.open`). Empty state: "No documents shared yet." |
| 158.3 | Create `portal/components/comment-section.tsx` | 158A | | New file. Comment thread with reply form. Props: `projectId: string`, `comments: PortalComment[]`, `onCommentPosted: () => void`. Chronological list: author name, relative date, content. "Add a comment" textarea + "Post Comment" button at bottom. On post, calls `POST /portal/projects/{projectId}/comments`. Character count indicator when > 1800 chars. Max 2000 characters. Optimistic UI: append comment to list on success, refetch on error. Loading state on submit button. |
| 158.4 | Create `portal/lib/format.ts` | 158A | | New file. Formatting utilities: `formatCurrency(amount: number, currency: string): string` -- formats with currency symbol (e.g., "R 1,250.00" for ZAR). `formatRelativeDate(date: string): string` -- "2 days ago", "just now". `formatFileSize(bytes: number): string` -- "1.2 MB", "340 KB". `formatDate(date: string): string` -- "15 Feb 2026". |
| 158.5 | Create `portal/app/(authenticated)/projects/[id]/page.tsx` | 158A | 158.1-158.4 | New file. Project detail page. Fetches in parallel: project detail, tasks, documents, comments, summary (5 API calls via `Promise.all`). Renders sections in order: (a) Project header (name, status badge, description), (b) Summary card (total hours, billable hours, last activity -- shown only if totalHours > 0), (c) Tasks section with count badge, (d) Documents section with count badge, (e) Comments section with count badge. Loading skeletons for each section. Client component. |
| 158.6 | Write `task-list.tsx` tests | 158A | 158.1 | New file: `portal/components/__tests__/task-list.test.tsx`. Tests: (1) renders task names and statuses, (2) shows "Unassigned" for null assignee, (3) renders empty state. ~3 tests. |
| 158.7 | Write `document-list.tsx` tests | 158A | 158.2 | New file: `portal/components/__tests__/document-list.test.tsx`. Tests: (1) renders document names, (2) download button calls handler, (3) renders empty state. ~3 tests. |
| 158.8 | Write `comment-section.tsx` tests | 158A | 158.3 | New file: `portal/components/__tests__/comment-section.test.tsx`. Tests: (1) renders comment thread, (2) submit button posts comment, (3) character count appears at 1800+, (4) disables submit when empty. ~4 tests. |
| 158.9 | Write project detail page test | 158A | 158.5 | New file: `portal/app/(authenticated)/projects/[id]/__tests__/page.test.tsx`. Tests: (1) renders all sections, (2) hides summary card when no hours, (3) shows loading skeletons. ~3 tests. Mock all 5 API calls. |

### Key Files

**Slice 158A -- Create:**
- `portal/components/task-list.tsx` -- Read-only task list
- `portal/components/document-list.tsx` -- Document table with download
- `portal/components/comment-section.tsx` -- Comment thread with reply form
- `portal/lib/format.ts` -- Currency, date, file size formatters
- `portal/app/(authenticated)/projects/[id]/page.tsx` -- Project detail page
- `portal/components/__tests__/task-list.test.tsx` -- 3 tests
- `portal/components/__tests__/document-list.test.tsx` -- 3 tests
- `portal/components/__tests__/comment-section.test.tsx` -- 4 tests
- `portal/app/(authenticated)/projects/[id]/__tests__/page.test.tsx` -- 3 tests

**Read for context:**
- `portal/lib/api-client.ts` -- How to make API calls (from Epic 156)
- `portal/lib/types.ts` -- Type definitions (from Epic 156)
- `portal/components/status-badge.tsx` -- Status badge component (from Epic 157)
- `frontend/components/projects/comment-section.tsx` -- Admin comment section pattern (if it exists)

### Architecture Decisions

- **5 parallel API calls**: All data sources are independent. `Promise.all` reduces perceived latency. Each section has its own loading skeleton.
- **Comment character limit enforced client-side and server-side**: The textarea enforces 2000 chars. The backend validates independently (155A). Defense in depth.
- **No task interaction**: Tasks are read-only in the portal. No create/edit/status change. This is by design -- clients view progress but don't manage tasks.

---

## Epic 159: Portal Invoice List + Detail Pages

**Goal**: Build the invoice list page (table with status, dates, totals) and invoice detail page (header, line items table, totals section, PDF download). Add currency formatting and invoice-specific status badge.

**References**: Architecture doc Sections 22.5.3 (invoice list), 22.5.4 (invoice detail).

**Dependencies**: Epic 157 (shell), Epic 153 (invoice endpoints).

**Scope**: Portal

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **159A** | 159.1--159.8 | `invoice-status-badge.tsx` + `invoice-line-table.tsx` + `(authenticated)/invoices/page.tsx` (invoice list table) + `(authenticated)/invoices/[id]/page.tsx` (invoice detail with lines, totals, PDF download) + tests. ~6 new files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 159.1 | Create `portal/components/invoice-status-badge.tsx` | 159A | | New file. Color-coded status badge for invoices. SENT=blue, PAID=green, VOID=gray. Uses Shadcn `Badge`. Distinct from `status-badge.tsx` because invoice statuses have different color semantics than project/task statuses. |
| 159.2 | Create `portal/components/invoice-line-table.tsx` | 159A | | New file. Invoice line items table. Props: `lines: PortalInvoiceLine[]`, `currency: string`. Columns: Description, Quantity, Rate, Amount. Each row formatted with `formatCurrency`. Footer row: Subtotal, Tax, **Total** (bold). |
| 159.3 | Create `portal/app/(authenticated)/invoices/page.tsx` | 159A | 159.1 | New file. Invoice list page. Title: "Invoices". Fetches from `GET /portal/invoices`. Table columns: Invoice # (link to detail), Status (badge), Issue Date, Due Date, Total (formatted currency), Actions ("View" link, "Download PDF" button). Sorted by issue date descending (server-side). Empty state: "No invoices yet." Loading skeleton. |
| 159.4 | Create `portal/app/(authenticated)/invoices/[id]/page.tsx` | 159A | 159.1, 159.2 | New file. Invoice detail page. Fetches from `GET /portal/invoices/{id}`. Invoice header: number (h1), status badge, issue date, due date. "Download PDF" button (prominent, top-right). Line items table (`InvoiceLineTable`). Totals section: subtotal, tax, total (bold). Notes section (if `notes` not null). On download, calls `GET /portal/invoices/{id}/download`, opens presigned URL in new tab. |
| 159.5 | Extend `portal/lib/format.ts` if needed | 159A | | Ensure `formatCurrency` handles ZAR (R prefix), USD ($), EUR, GBP. Use `Intl.NumberFormat` with currency code for locale-aware formatting. If `format.ts` was created in Epic 158, this task extends it. If not, create it here. |
| 159.6 | Write `invoice-status-badge.tsx` tests | 159A | 159.1 | New test file: `portal/components/__tests__/invoice-status-badge.test.tsx`. Tests: (1) SENT renders blue, (2) PAID renders green, (3) VOID renders gray. ~3 tests. |
| 159.7 | Write `invoice-line-table.tsx` tests | 159A | 159.2 | New test file: `portal/components/__tests__/invoice-line-table.test.tsx`. Tests: (1) renders line items with amounts, (2) shows subtotal, tax, total in footer, (3) formats currency correctly. ~3 tests. |
| 159.8 | Write invoice page tests | 159A | 159.3, 159.4 | New test files: `portal/app/(authenticated)/invoices/__tests__/page.test.tsx` (~3 tests: renders table, empty state, download button), `portal/app/(authenticated)/invoices/[id]/__tests__/page.test.tsx` (~3 tests: renders detail, renders lines, download PDF). ~6 tests total. |

### Key Files

**Slice 159A -- Create:**
- `portal/components/invoice-status-badge.tsx` -- Invoice status colors
- `portal/components/invoice-line-table.tsx` -- Line items table with totals
- `portal/app/(authenticated)/invoices/page.tsx` -- Invoice list page
- `portal/app/(authenticated)/invoices/[id]/page.tsx` -- Invoice detail page
- `portal/components/__tests__/invoice-status-badge.test.tsx` -- 3 tests
- `portal/components/__tests__/invoice-line-table.test.tsx` -- 3 tests
- `portal/app/(authenticated)/invoices/__tests__/page.test.tsx` -- 3 tests
- `portal/app/(authenticated)/invoices/[id]/__tests__/page.test.tsx` -- 3 tests

**Read for context:**
- `portal/lib/api-client.ts` -- API client (from Epic 156)
- `portal/lib/types.ts` -- `PortalInvoice`, `PortalInvoiceDetail`, `PortalInvoiceLine` types
- `portal/lib/format.ts` -- `formatCurrency` utility (from Epic 158)
- `portal/components/status-badge.tsx` -- General status badge pattern (from Epic 157)
- `frontend/app/(app)/org/[slug]/invoices/` -- Admin invoice pages (for reference, not for copying)

### Architecture Decisions

- **Separate invoice status badge**: Invoice statuses (SENT, PAID, VOID) have different color semantics than project/task statuses. A separate component is clearer than overloading `status-badge.tsx` with conditional logic.
- **No payment action**: The portal shows invoice status but clients cannot pay online in v1. This is explicitly out of scope per architecture doc Section 22.1.
- **Currency formatting via `Intl.NumberFormat`**: Handles locale-aware formatting for ZAR, USD, EUR, GBP without a library dependency.

---

## Epic 160: Portal Profile, Responsive Polish + Docker

**Goal**: Build the profile page (read-only contact info), apply responsive polish across all pages, create the Docker configuration for the portal app, and add the Docker Compose service definition. This is the final polish slice.

**References**: Architecture doc Sections 22.5.5 (profile), 22.2 (Docker config, directory structure).

**Dependencies**: All other portal epics (156-159).

**Scope**: Portal + Infrastructure

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **160A** | 160.1--160.8 | `(authenticated)/profile/page.tsx` (contact info display) + responsive polish (mobile hamburger, touch-friendly buttons, responsive tables) + `portal/Dockerfile` (multi-stage, port 3001) + Docker Compose entry (commented out) + tests. ~4 new files, ~4 modified files. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 160.1 | Create `portal/app/(authenticated)/profile/page.tsx` | 160A | | New file. Profile page. Fetches from `GET /portal/me`. Displays: display name, email, role (PRIMARY, BILLING, GENERAL), customer name. Read-only -- no editing in v1. Clean card layout using Shadcn `Card`. |
| 160.2 | Responsive polish -- mobile hamburger menu | 160A | | Modify: `portal/components/portal-header.tsx`. Ensure the mobile hamburger menu works correctly: navigation links collapse on small screens, hamburger toggle opens a slide-out or dropdown menu. Test on viewport widths 320px, 768px, 1024px. |
| 160.3 | Responsive polish -- touch-friendly buttons and tables | 160A | | Modify: `portal/components/document-list.tsx`, `portal/app/(authenticated)/invoices/page.tsx`. Ensure table layouts are responsive (horizontal scroll on mobile or stack vertically). Download/action buttons have adequate touch targets (min 44px). |
| 160.4 | Create `portal/Dockerfile` | 160A | | New file. Multi-stage Docker build (see architecture doc Section 22.2 for exact Dockerfile). Stage 1: deps (pnpm install). Stage 2: build (with `NEXT_PUBLIC_PORTAL_API_URL` build arg). Stage 3: production runner (port 3001, node user). Pattern: `frontend/Dockerfile`. |
| 160.5 | Add Docker Compose service definition | 160A | | Modify: `compose/docker-compose.yml`. Add a commented-out `portal` service definition (matching the pattern of the existing commented-out `frontend` service). Build context: `../portal`. Port mapping: `3001:3001`. Build arg: `NEXT_PUBLIC_PORTAL_API_URL: http://backend:8080`. Depends on `backend`. |
| 160.6 | Write profile page test | 160A | 160.1 | New test file: `portal/app/(authenticated)/profile/__tests__/page.test.tsx`. Tests: (1) renders contact info, (2) shows customer name, (3) displays role. ~3 tests. |
| 160.7 | Docker build verification | 160A | 160.4 | Run `docker build -t portal-test --build-arg NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8080 portal/` to verify the Dockerfile builds without errors. This is a build-time check, not a runtime test. |
| 160.8 | Final integration smoke test description | 160A | | Document in the task notes: the full auth-to-browse-to-comment flow. Login -> exchange -> projects list -> project detail -> post comment -> invoices list -> invoice detail -> download PDF -> profile -> logout. This can be tested manually via the E2E stack or automated in a future Playwright suite. Not a code task -- a test plan note for QA. |

### Key Files

**Slice 160A -- Create:**
- `portal/app/(authenticated)/profile/page.tsx` -- Contact info display
- `portal/Dockerfile` -- Multi-stage Docker build, port 3001
- `portal/app/(authenticated)/profile/__tests__/page.test.tsx` -- 3 tests

**Slice 160A -- Modify:**
- `portal/components/portal-header.tsx` -- Responsive polish for mobile menu
- `portal/components/document-list.tsx` -- Responsive table layout
- `portal/app/(authenticated)/invoices/page.tsx` -- Responsive table layout
- `compose/docker-compose.yml` -- Add commented-out portal service

**Read for context:**
- `frontend/Dockerfile` -- Docker build pattern to match
- `compose/docker-compose.yml` -- Existing service definition pattern
- Architecture doc Section 22.2 (Dockerfile and Docker Compose config)

### Architecture Decisions

- **Profile is read-only in v1**: No contact info editing. Editing would require a new backend endpoint and validation logic. Out of scope.
- **Docker port 3001**: Distinct from the admin frontend (3000) and backend (8080). The portal runs as a separate container.
- **Commented-out Docker Compose entry**: Matches the existing pattern for the admin frontend. Uncommented when deploying to production.
