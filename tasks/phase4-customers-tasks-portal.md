# Phase 4 — Customers, Document Scopes & Tasks

Phase 4 extends the platform from an internal staff-collaboration tool to a client-aware system. It introduces three new domain concepts — **Customers**, **document scopes**, and **Tasks** — and lays the groundwork for a future customer-facing portal. All additions are evolutionary: they reuse the existing tenant isolation model (schema-per-tenant for Pro, shared schema + RLS for Starter) and add new entities alongside the existing Project/Document/Member model. See ARCHITECTURE.md §10 for the full design and [ADR-017](../adr/ADR-017-customer-as-org-child.md)–[ADR-020](../adr/ADR-020-customer-portal-approach.md) for decision records.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 37 | Customer Backend — Entity, CRUD & Linking | Backend | — | M | 37A, 37B | |
| 38 | Customer Frontend — List, Detail & Dialogs | Frontend | 37 | M | 38A, 38B | |
| 39 | Task Backend — Entity, CRUD, Claim & Release | Backend | — | M | 39A, 39B | |
| 40 | Task Frontend — List, Creation & Claim UI | Frontend | 39 | M | 40A, 40B | |
| 41 | Document Scope Extension — Backend | Backend | 37 | M | 41A, 41B | |
| 42 | Document Scope Extension — Frontend | Frontend | 38, 41 | M | 42A, 42B | |
| 43 | Customer Portal Groundwork | Both | 37, 41 | L | 43A, 43B, 43C | |

## Dependency Graph

```
[E37 Customer Backend] ──────────────────────┬──► [E38 Customer Frontend]
                                              ├──► [E41 Doc Scope Backend] ──► [E42 Doc Scope Frontend]
                                              └──► [E43 Portal Groundwork]
[E39 Task Backend] ──────────────────────────────► [E40 Task Frontend]
[E38] + [E41] ──────────────────────────────────► [E42]
[E37] + [E41] ──────────────────────────────────► [E43]
```

**Parallel tracks**: Epics 37 (customer backend) and 39 (task backend) have zero dependency on each other and can be developed concurrently. After 37 lands, 38 (customer frontend) and 41 (document scope backend) can begin in parallel. Epic 43 (portal groundwork) is last — it depends on both customers and document scopes being complete.

## Implementation Order

### Stage 1: Backend Foundations (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 1a | Epic 37: Customer Backend | Customer entity is the prerequisite for document scope extension (V11 references `customers` table) and the customer frontend. |
| 1b | Epic 39: Task Backend | Independent domain — no dependency on customers. Can run in parallel. |

### Stage 2: Frontend + Scope Extension (After Stage 1)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 38: Customer Frontend | Depends on customer API from Epic 37. |
| 2b | Epic 40: Task Frontend | Depends on task API from Epic 39. |
| 2c | Epic 41: Document Scope Extension Backend | Depends on `customers` table from Epic 37 (V11 references `customers`). |

### Stage 3: Document Scope Frontend

| Order | Epic | Rationale |
|-------|------|-----------|
| 3 | Epic 42: Document Scope Frontend | Depends on both customer frontend (38) and document scope backend (41). |

### Stage 4: Portal Groundwork

| Order | Epic | Rationale |
|-------|------|-----------|
| 4 | Epic 43: Customer Portal Groundwork | Depends on customers (37) and document scopes (41). Last because the portal consumes everything built above. |

### Timeline

```
Stage 1:  [E37] [E39]                <- parallel (independent domains)
Stage 2:  [E38] [E40] [E41]          <- parallel (after their respective Stage 1 deps)
Stage 3:  [E42]                       <- after E38 + E41
Stage 4:  [E43]                       <- after E37 + E41
```

---

## Epic 37: Customer Backend — Entity, CRUD & Linking

**Goal**: Create the `customers` and `customer_projects` tables, implement Customer entity with full CRUD endpoints, and provide customer-project linking/unlinking. Includes access control following the existing `ProjectAccessService` pattern.

**References**: [ADR-017](../adr/ADR-017-customer-as-org-child.md), ARCHITECTURE.md §10.2.1, §10.2.2, §10.3.1, §10.5.1

**Dependencies**: None (builds on existing Phase 1+2 infrastructure)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **37A** | 37.1–37.6 | Migrations, entities, repositories, Customer service + CRUD controller, integration tests | **Done** (PR #73) |
| **37B** | 37.7–37.12 | CustomerProject linking, access control, project-side customer listing, integration tests | **Done** (PR #74) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 37.1 | Create V9 tenant migration for customers table | 37A | **Done** | `db/migration/tenant/V9__create_customers.sql`. Columns: `id` (UUID PK DEFAULT gen_random_uuid()), `name` (VARCHAR(255) NOT NULL), `email` (VARCHAR(255) NOT NULL), `phone` (VARCHAR(50)), `id_number` (VARCHAR(100)), `status` (VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'), `notes` (TEXT), `created_by` (UUID NOT NULL REFERENCES members(id)), `tenant_id` (VARCHAR(255)), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()), `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). UNIQUE(email, tenant_id). Indexes: `idx_customers_tenant_id`, `idx_customers_tenant_id_status`, `idx_customers_tenant_id_email`. RLS: `ALTER TABLE customers ENABLE ROW LEVEL SECURITY; CREATE POLICY tenant_isolation ON customers USING (tenant_id = current_setting('app.current_tenant', true) OR tenant_id IS NULL)`. Pattern: follow `V7__add_tenant_id_for_shared.sql` RLS pattern. |
| 37.2 | Create V10 tenant migration for customer_projects table | 37A | **Done** | `db/migration/tenant/V10__create_customer_projects.sql`. Columns: `id` (UUID PK DEFAULT gen_random_uuid()), `customer_id` (UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE), `project_id` (UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE), `linked_by` (UUID REFERENCES members(id) ON DELETE SET NULL), `tenant_id` (VARCHAR(255)), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). UNIQUE(customer_id, project_id). Indexes: `idx_customer_projects_customer_id`, `idx_customer_projects_project_id`, `idx_customer_projects_tenant_id`. RLS policy matching V9 pattern. |
| 37.3 | Create Customer entity | 37A | **Done** | `customer/Customer.java` — JPA entity mapped to `customers` table. UUID id, name, email, phone, idNumber, status (String, ACTIVE/ARCHIVED), notes, createdBy (UUID), tenantId, createdAt, updatedAt. Annotations: `@FilterDef`/`@Filter` for `tenantFilter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Pattern: follow `Member.java` entity structure. |
| 37.4 | Create CustomerRepository | 37A | **Done** | `customer/CustomerRepository.java` — extends `JpaRepository<Customer, UUID>`. Methods: `List<Customer> findAll()`, `Optional<Customer> findOneById(UUID id)` (JPQL `@Query` — not `findById()` which bypasses Hibernate `@Filter`), `Optional<Customer> findByEmail(String email)`, `boolean existsByEmail(String email)`. Pattern: follow `ProjectRepository.java` with JPQL `findOneById`. |
| 37.5 | Create CustomerService + CustomerController | 37A | **Done** | **Service** (`customer/CustomerService.java`): `listCustomers()`, `getCustomer(UUID id)`, `createCustomer(name, email, phone, idNumber, notes, createdBy)` (checks email uniqueness, throws `ResourceConflictException`), `updateCustomer(UUID id, name, email, phone, idNumber, notes)`, `archiveCustomer(UUID id)` (sets status=ARCHIVED, no hard delete). All `@Transactional`. **Controller** (`customer/CustomerController.java`): `@RequestMapping("/api/customers")`. `POST` (Owner/Admin), `GET` list (MEMBER+), `GET /{id}` (MEMBER+), `PUT /{id}` (Owner/Admin), `DELETE /{id}` (Owner/Admin — archives). Nested record DTOs: `CreateCustomerRequest(name, email, phone, idNumber, notes)`, `UpdateCustomerRequest(name, email, phone, idNumber, notes)`, `CustomerResponse(id, name, email, phone, idNumber, status, notes, createdBy, createdAt, updatedAt)`. Pattern: follow `ProjectController.java` style. |
| 37.6 | Add Customer CRUD integration tests | 37A | **Done** | `customer/CustomerIntegrationTest.java`. ~12 tests: create customer (201), create with duplicate email (409), list customers (returns all in tenant), get by id (200), update customer (200), archive customer (200, status=ARCHIVED), RBAC — member cannot create (403), RBAC — member can list and get (200), tenant isolation (customer in tenant A invisible in tenant B), validation errors (missing name 400, missing email 400), get non-existent customer (404). Seed test members in `@BeforeAll` with Pro tier (follow `ProjectIntegrationTest` pattern). |
| 37.7 | Create CustomerProject entity and repository | 37B | | **Entity** (`customer/CustomerProject.java`): UUID id, customerId (UUID), projectId (UUID), linkedBy (UUID nullable), tenantId, createdAt. `@FilterDef`/`@Filter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. **Repository** (`customer/CustomerProjectRepository.java`): `findByCustomerId(UUID)`, `findByProjectId(UUID)`, `existsByCustomerIdAndProjectId(UUID, UUID)`, `deleteByCustomerIdAndProjectId(UUID, UUID)`. Pattern: follow `ProjectMember.java` / `ProjectMemberRepository.java`. |
| 37.8 | Create CustomerProjectService | 37B | | `customer/CustomerProjectService.java`. Methods: `linkCustomerToProject(customerId, projectId, linkedBy, memberId, orgRole)` — validates customer and project exist in tenant, checks not already linked (409), checks permission (Owner/Admin or Project Lead via `ProjectAccessService`). `unlinkCustomerFromProject(customerId, projectId, memberId, orgRole)` — same permission check, deletes junction row. `listProjectsForCustomer(customerId)` — returns list of projects linked to customer with project details. `listCustomersForProject(projectId, memberId, orgRole)` — checks project access, returns list of customers linked to project. All `@Transactional`. |
| 37.9 | Create CustomerProject linking endpoints | 37B | | Add to `CustomerController.java`: `POST /api/customers/{id}/projects/{projectId}` (link, 201), `DELETE /api/customers/{id}/projects/{projectId}` (unlink, 204), `GET /api/customers/{id}/projects` (list projects for customer, 200). Create `ProjectCustomerController.java`: `GET /api/projects/{id}/customers` (list customers for project, 200). DTOs: `CustomerProjectResponse(customerId, projectId, linkedBy, createdAt)`. Auth: Owner/Admin for link/unlink, Project Lead via service check, MEMBER+ for reads. Pattern: follow `ProjectMemberController.java`. |
| 37.10 | Add nav link to sidebar | 37B | | Add "Customers" entry to `frontend/lib/nav-items.ts` with `Users` Lucide icon, href `/org/[slug]/customers`. Small frontend change included here since it is a single-line addition. |
| 37.11 | Add CustomerProject linking integration tests | 37B | | `customer/CustomerProjectIntegrationTest.java`. ~10 tests: link customer to project (201), link duplicate (409), unlink (204), list projects for customer, list customers for project, RBAC — member cannot link (403 unless project lead), project lead can link (201), unlink non-existent (404), tenant isolation (cross-tenant link impossible), cascading delete — archiving customer does not delete links (ON DELETE CASCADE only for hard delete). |
| 37.12 | Verify shared schema isolation | 37B | | Add Starter tenant tests to `CustomerIntegrationTest` or `CustomerProjectIntegrationTest`: provision two Starter orgs, create customers in each, verify customers are isolated (org A cannot see org B's customers). Verify `tenant_id` is populated correctly on shared schema entities. Pattern: follow `StarterTenantIntegrationTest.java`. ~3 additional tests. |

### Key Files

**Slice 37A — Create:**
- `backend/src/main/resources/db/migration/tenant/V9__create_customers.sql`
- `backend/src/main/resources/db/migration/tenant/V10__create_customer_projects.sql`
- `backend/src/main/java/.../customer/Customer.java`
- `backend/src/main/java/.../customer/CustomerRepository.java`
- `backend/src/main/java/.../customer/CustomerService.java`
- `backend/src/main/java/.../customer/CustomerController.java`
- `backend/src/test/java/.../customer/CustomerIntegrationTest.java`

**Slice 37B — Create:**
- `backend/src/main/java/.../customer/CustomerProject.java`
- `backend/src/main/java/.../customer/CustomerProjectRepository.java`
- `backend/src/main/java/.../customer/CustomerProjectService.java`
- `backend/src/main/java/.../customer/ProjectCustomerController.java`
- `backend/src/test/java/.../customer/CustomerProjectIntegrationTest.java`

**Slice 37B — Modify:**
- `backend/src/main/java/.../customer/CustomerController.java` — Add linking endpoints
- `frontend/lib/nav-items.ts` — Add "Customers" nav entry

### Architecture Decisions

- **`customer/` package**: All customer-related classes grouped by feature, following existing `project/`, `document/`, `member/` pattern.
- **Soft delete via archive**: `DELETE /api/customers/{id}` sets `status = ARCHIVED` rather than removing the row. This preserves audit trails and prevents orphaned documents (if added in Epic 41).
- **No bidirectional JPA relationships**: `CustomerProject` uses plain UUID references. Joins done via JPQL when display data is needed. Consistent with `ProjectMember` pattern.
- **Email uniqueness scoped to tenant_id**: `UNIQUE(email, tenant_id)` allows the same customer email across different orgs in the shared schema. In dedicated schemas, `tenant_id` is NULL so the constraint becomes effectively `UNIQUE(email)` per schema.
- **JPQL `findOneById`**: Required because `JpaRepository.findById()` uses `EntityManager.find()` which bypasses Hibernate `@Filter`. Same fix applied in Epic 24C for Project and Document.

---

## Epic 38: Customer Frontend — List, Detail & Dialogs

**Goal**: Build the customer list page, customer detail page, create/edit customer dialog, and link-to-project dialog. Staff can manage customers and their project associations through the UI.

**References**: ARCHITECTURE.md §10.10 Slice B, §10.4.1

**Dependencies**: Epic 37

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **38A** | 38.1–38.5 | Types, server actions, customer list page, create dialog, page integration | **Done** (PR #75) |
| **38B** | 38.6–38.10 | Customer detail page, edit dialog, link-to-project dialog, project list, tests | **Done** (PR #76) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 38.1 | Add Customer TypeScript types | 38A | | In `lib/types.ts`: `Customer { id, name, email, phone, idNumber, status, notes, createdBy, createdAt, updatedAt }`, `CustomerStatus = "ACTIVE" \| "ARCHIVED"`, `CreateCustomerRequest { name, email, phone?, idNumber?, notes? }`, `UpdateCustomerRequest { name, email, phone?, idNumber?, notes? }`, `CustomerProject { customerId, projectId, linkedBy, createdAt }`. Pattern: follow existing `Project` and `OrgMember` type definitions. |
| 38.2 | Create customer server actions | 38A | | `app/(app)/org/[slug]/customers/actions.ts` — `fetchCustomers()`, `createCustomer(slug, formData)`, `updateCustomer(slug, id, formData)`, `archiveCustomer(slug, id)`. Each calls the corresponding API endpoint via `api.get/post/put/delete`. Uses `revalidatePath` to refresh customer list and detail pages. Standard ActionResult pattern with error handling. Pattern: follow `projects/actions.ts`. |
| 38.3 | Build customer list page | 38A | | `app/(app)/org/[slug]/customers/page.tsx` — server component. Fetches `GET /api/customers` via `api.get`. Page header: Instrument Serif h1 "Customers" + customer count badge. Right-aligned: "New Customer" pill button (admin/owner only). Catalyst-style table: Name (link to detail), Email, Phone, Status (badge: ACTIVE green, ARCHIVED olive-400), Created date. Row hover: `hover:bg-olive-50`. Empty state: Users icon + "No customers yet" + "Add your first customer" CTA. Pattern: follow `projects/page.tsx` for page structure, `team/page.tsx` for table styling. |
| 38.4 | Build CreateCustomerDialog | 38A | | `components/customers/create-customer-dialog.tsx` — client component. Shadcn Dialog with form fields: Name (required), Email (required), Phone (optional), ID Number (optional), Notes (optional textarea). "Create" pill button + "Cancel" plain button. Server action `createCustomer`. Inline validation errors. Form reset on dialog close. Pattern: follow `create-project-dialog.tsx`. |
| 38.5 | Add loading.tsx for customer list | 38A | | `app/(app)/org/[slug]/customers/loading.tsx` — skeleton with table rows. Pattern: follow `projects/loading.tsx`. |
| 38.6 | Build customer detail page | 38B | | `app/(app)/org/[slug]/customers/[id]/page.tsx` — server component. Fetches `GET /api/customers/{id}` and `GET /api/customers/{id}/projects`. Header: customer name (Instrument Serif), email below, status badge. Meta line: phone, ID number (if present), created date. Tabs: "Projects" (linked projects list) and "Documents" (placeholder for Epic 42). Edit/Archive action buttons (admin/owner). Pattern: follow `projects/[id]/page.tsx` for header and tab structure. |
| 38.7 | Build EditCustomerDialog | 38B | | `components/customers/edit-customer-dialog.tsx` — client component with form pre-population from customer data. Server action `updateCustomer`. Same fields as create. Pattern: follow `edit-project-dialog.tsx`. |
| 38.8 | Build ArchiveCustomerDialog | 38B | | `components/customers/archive-customer-dialog.tsx` — AlertDialog for confirmation. "Archive **{name}**? Their project links will be preserved but they will be hidden from active customer lists." "Archive" destructive pill + "Cancel" plain. Server action `archiveCustomer`. Pattern: follow `delete-project-dialog.tsx`. |
| 38.9 | Build LinkProjectDialog | 38B | | `components/customers/link-project-dialog.tsx` — client component. Shadcn Dialog with searchable project list via Shadcn Command (cmdk). Fetches `GET /api/projects` to populate list. Filters out already-linked projects. Shows project name + description truncated. Server action `linkCustomerToProject(slug, customerId, projectId)`. Add to `customers/[id]/actions.ts`: `linkProject`, `unlinkProject`. Pattern: follow `add-member-dialog.tsx` for cmdk search pattern. |
| 38.10 | Add frontend tests | 38B | | `__tests__/components/customers/` — ~8 tests: customer list renders with correct columns, create dialog validates required fields, edit dialog pre-populates, archive dialog shows customer name, link project dialog filters existing links, empty state renders for no customers, role-based button visibility (admin/owner see actions, member does not). Pattern: follow `add-member-dialog.test.tsx`. |

### Key Files

**Slice 38A — Create:**
- `frontend/app/(app)/org/[slug]/customers/page.tsx`
- `frontend/app/(app)/org/[slug]/customers/actions.ts`
- `frontend/app/(app)/org/[slug]/customers/loading.tsx`
- `frontend/components/customers/create-customer-dialog.tsx`

**Slice 38A — Modify:**
- `frontend/lib/types.ts` — Add Customer types

**Slice 38B — Create:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/actions.ts`
- `frontend/components/customers/edit-customer-dialog.tsx`
- `frontend/components/customers/archive-customer-dialog.tsx`
- `frontend/components/customers/link-project-dialog.tsx`
- `frontend/__tests__/components/customers/` — Test files

---

## Epic 39: Task Backend — Entity, CRUD, Claim & Release

**Goal**: Create the `tasks` table, implement Task entity with full CRUD, claim/release endpoints with optimistic locking, and filtered listing with query parameters (status, assignee, priority, sort).

**References**: [ADR-019](../adr/ADR-019-task-claim-workflow.md), ARCHITECTURE.md §10.2.3, §10.3.2, §10.5.1

**Dependencies**: None (builds on existing Phase 1+2 infrastructure; tasks are project-scoped and use existing `ProjectAccessService`)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **39A** | 39.1–39.5 | Migration, entity, repository, Task service + CRUD controller, integration tests | **Done** (PR #77) |
| **39B** | 39.6–39.10 | Claim/release endpoints, optimistic locking, filtered listing, race condition tests | **Done** (PR #78) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 39.1 | Create V12 tenant migration for tasks table | 39A | | `db/migration/tenant/V12__create_tasks.sql`. Columns per ARCHITECTURE.md §10.2.3: `id` (UUID PK DEFAULT gen_random_uuid()), `project_id` (UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE), `title` (VARCHAR(500) NOT NULL), `description` (TEXT), `status` (VARCHAR(20) NOT NULL DEFAULT 'OPEN'), `priority` (VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'), `type` (VARCHAR(100)), `assignee_id` (UUID REFERENCES members(id) ON DELETE SET NULL), `created_by` (UUID NOT NULL REFERENCES members(id)), `due_date` (DATE), `version` (INTEGER NOT NULL DEFAULT 0), `tenant_id` (VARCHAR(255)), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()), `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). Indexes: `idx_tasks_project_id`, `idx_tasks_assignee_id`, `idx_tasks_project_id_status`, `idx_tasks_project_id_assignee_id`, `idx_tasks_tenant_id`. RLS policy matching V9 pattern. Pattern: follow `V5__create_project_members.sql` structure. |
| 39.2 | Create Task entity | 39A | | `task/Task.java` — JPA entity. UUID id, projectId (UUID), title, description, status (String: OPEN/IN_PROGRESS/DONE/CANCELLED), priority (String: LOW/MEDIUM/HIGH), type (String nullable), assigneeId (UUID nullable), createdBy (UUID), dueDate (LocalDate nullable), version (int, `@Version`), tenantId, createdAt, updatedAt. `@FilterDef`/`@Filter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Add `claim(UUID memberId)` method (sets assigneeId + status=IN_PROGRESS), `release()` method (clears assigneeId + status=OPEN). Pattern: follow `Document.java` entity structure. |
| 39.3 | Create TaskRepository | 39A | | `task/TaskRepository.java` — extends `JpaRepository<Task, UUID>`. Methods: `List<Task> findByProjectId(UUID projectId)`, `Optional<Task> findOneById(UUID id)` (JPQL `@Query` for `@Filter` compatibility), custom JPQL for filtered listing: `findByProjectIdWithFilters(UUID projectId, String status, UUID assigneeId, String priority, Sort sort)` using `@Query` with optional WHERE clauses via Specification or dynamic JPQL. Pattern: follow `ProjectRepository.java` with JPQL `findOneById`. |
| 39.4 | Create TaskService and TaskController (CRUD) | 39A | | **Service** (`task/TaskService.java`): `listTasks(projectId, memberId, orgRole, status, assigneeId, priority, sort)` — checks project view access via `ProjectAccessService`, queries with filters. `getTask(taskId, memberId, orgRole)` — looks up task, checks project access. `createTask(projectId, title, description, priority, type, dueDate, createdBy, orgRole)` — checks project edit access. `updateTask(taskId, title, description, priority, status, type, dueDate, assigneeId, memberId, orgRole)` — checks permission (lead/admin/owner can update any, contributor can update own assigned). `deleteTask(taskId, memberId, orgRole)` — checks project lead/admin/owner. **Controller** (`task/TaskController.java`): `POST /api/projects/{projectId}/tasks` (201), `GET /api/projects/{projectId}/tasks` (200, supports `?status=&assigneeId=&priority=&sort=`), `GET /api/tasks/{id}` (200), `PUT /api/tasks/{id}` (200), `DELETE /api/tasks/{id}` (204). DTOs: `CreateTaskRequest(title, description, priority, type, dueDate)`, `UpdateTaskRequest(title, description, priority, status, type, dueDate, assigneeId)`, `TaskResponse(id, projectId, title, description, status, priority, type, assigneeId, assigneeName, createdBy, createdByName, dueDate, version, createdAt, updatedAt)`. Pattern: follow `ProjectMemberController.java` for project-scoped endpoints. |
| 39.5 | Add Task CRUD integration tests | 39A | | `task/TaskIntegrationTest.java`. ~12 tests: create task (201), list tasks for project, get task by id, update task, delete task (204), RBAC — project member can create (201), RBAC — non-project-member cannot view (404), RBAC — contributor cannot delete (403), RBAC — lead can delete (204), query filter by status, query filter by assignee, tenant isolation (task in tenant A invisible in tenant B). Seed test members + project in `@BeforeAll` with Pro tier. Pattern: follow `ProjectIntegrationTest.java`. |
| 39.6 | Add claim and release endpoints | 39B | | Add to `TaskController.java`: `POST /api/tasks/{id}/claim` (200), `POST /api/tasks/{id}/release` (200). Add to `TaskService.java`: `claimTask(taskId, memberId, orgRole)` — checks project access, verifies task is OPEN and unassigned, calls `task.claim(memberId)`, saves. On `OptimisticLockException` → throw `ResourceConflictException("Task already claimed")`. `releaseTask(taskId, memberId, orgRole)` — checks permission (current assignee or lead/admin/owner), calls `task.release()`, saves. Pattern: per ADR-019 lifecycle diagram. |
| 39.7 | Add OptimisticLockException handling | 39B | | Modify `exception/GlobalExceptionHandler.java` — add handler for `org.springframework.orm.ObjectOptimisticLockingFailureException` → return 409 Conflict ProblemDetail with message "Resource was modified concurrently. Please retry." Pattern: follow existing `GlobalExceptionHandler` handlers. |
| 39.8 | Add filtered listing with query parameters | 39B | | Update `TaskRepository` with a `@Query` supporting dynamic WHERE clauses, or use Spring Data JPA Specification pattern. Parameters: `status` (exact match), `assigneeId` (exact match or special value `"unassigned"` → `IS NULL`), `priority` (exact match). Sort options: `dueDate` (ASC nulls last), `priority` (HIGH > MEDIUM > LOW), `createdAt` (DESC). Default sort: `createdAt DESC`. Controller accepts `@RequestParam(required = false)` for each filter. |
| 39.9 | Add claim/release integration tests | 39B | | `task/TaskClaimIntegrationTest.java`. ~8 tests: claim unassigned task → status IN_PROGRESS + assigneeId set, claim already-claimed task → 409 Conflict, release by assignee → status OPEN + assigneeId null, release by non-assignee non-lead → 403, release by lead → 200, claim task not in project → 404, claim task with status DONE → 400 (invalid state), concurrent claim simulation (save task, then two updates with same version → one 409). |
| 39.10 | Verify shared schema isolation for tasks | 39B | | Add Starter tenant tests: provision two Starter orgs, create tasks in each, verify tasks are isolated (org A cannot see org B's tasks). Verify `tenant_id` is populated correctly. ~3 additional tests. Pattern: follow `StarterTenantIntegrationTest.java`. |

### Key Files

**Slice 39A — Create:**
- `backend/src/main/resources/db/migration/tenant/V12__create_tasks.sql`
- `backend/src/main/java/.../task/Task.java`
- `backend/src/main/java/.../task/TaskRepository.java`
- `backend/src/main/java/.../task/TaskService.java`
- `backend/src/main/java/.../task/TaskController.java`
- `backend/src/test/java/.../task/TaskIntegrationTest.java`

**Slice 39B — Modify:**
- `backend/src/main/java/.../task/TaskController.java` — Add claim/release endpoints
- `backend/src/main/java/.../task/TaskService.java` — Add claim/release logic
- `backend/src/main/java/.../task/TaskRepository.java` — Add filtered query methods
- `backend/src/main/java/.../exception/GlobalExceptionHandler.java` — Add optimistic lock handler

**Slice 39B — Create:**
- `backend/src/test/java/.../task/TaskClaimIntegrationTest.java`

### Architecture Decisions

- **`task/` package**: New feature package following existing `project/`, `document/`, `customer/` pattern.
- **Optimistic locking via `@Version`**: Prevents concurrent claim race conditions. Hibernate throws `ObjectOptimisticLockingFailureException` when version mismatch occurs. Translated to 409 Conflict.
- **Claim/release as explicit endpoints**: Separate `POST /claim` and `POST /release` rather than overloading `PUT /api/tasks/{id}` with assignee changes. Clearer semantics and atomic operations.
- **Filtered listing via query parameters**: Simple `?status=OPEN&assigneeId=unassigned` pattern. `"unassigned"` is a sentinel value meaning `assignee_id IS NULL`.
- **Project-scoped access control**: All task operations go through `ProjectAccessService` first.

---

## Epic 40: Task Frontend — List, Creation & Claim UI

**Goal**: Build the task list/board view within the project detail page, task creation dialog, claim/release buttons, and status filtering. Tasks integrate as a third tab alongside Documents and Members.

**References**: ARCHITECTURE.md §10.10 Slice F, §10.4.3

**Dependencies**: Epic 39

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **40A** | 40.1–40.5 | Types, server actions, task list component, create dialog, project detail integration | |
| **40B** | 40.6–40.10 | Claim/release buttons, status filters, priority badges, optimistic UI, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 40.1 | Add Task TypeScript types | 40A | | In `lib/types.ts`: `TaskStatus = "OPEN" \| "IN_PROGRESS" \| "DONE" \| "CANCELLED"`, `TaskPriority = "LOW" \| "MEDIUM" \| "HIGH"`, `Task { id, projectId, title, description, status, priority, type, assigneeId, assigneeName, createdBy, createdByName, dueDate, version, createdAt, updatedAt }`, `CreateTaskRequest { title, description?, priority?, type?, dueDate? }`, `UpdateTaskRequest { title?, description?, priority?, status?, type?, dueDate?, assigneeId? }`. |
| 40.2 | Create task server actions | 40A | | `app/(app)/org/[slug]/projects/[id]/task-actions.ts` — `fetchTasks(projectId, filters?)`, `createTask(slug, projectId, formData)`, `updateTask(slug, taskId, data)`, `deleteTask(slug, taskId)`, `claimTask(slug, taskId)`, `releaseTask(slug, taskId)`. Standard ActionResult pattern. `revalidatePath` on mutations. Pattern: follow `member-actions.ts`. |
| 40.3 | Build TaskListPanel component | 40A | | `components/tasks/task-list-panel.tsx` — client component. Catalyst-style table. Columns: Priority (badge), Title, Status (badge), Assignee (avatar + name or "Unassigned"), Due Date. Header: "Tasks" label + count badge + "New Task" outline button. Empty state: ClipboardList icon + "No tasks yet". Pattern: follow `project-members-panel.tsx`. |
| 40.4 | Build CreateTaskDialog | 40A | | `components/tasks/create-task-dialog.tsx` — Shadcn Dialog with fields: Title (required), Description (optional textarea), Priority (select: Low/Medium/High), Type (optional input), Due Date (optional date input). Pattern: follow `create-project-dialog.tsx`. |
| 40.5 | Integrate tasks as third tab on project detail | 40A | | Modify `components/projects/project-tabs.tsx` — add "Tasks" tab alongside "Documents" and "Members". Update `app/(app)/org/[slug]/projects/[id]/page.tsx` — fetch tasks, pass to `TaskListPanel`. Pattern: follow existing tab integration. |
| 40.6 | Add claim/release buttons to task list | 40B | | In `TaskListPanel`: OPEN + unassigned → "Claim" pill button. IN_PROGRESS + own → "Release" ghost button + "Mark Done" button. All buttons use `useTransition()` for loading states. Pattern: follow `project-members-panel.tsx` action buttons. |
| 40.7 | Add status filter bar | 40B | | Horizontal filter pills above table: "All", "Open", "In Progress", "Done", "My Tasks". Olive-toned toggle buttons. Clicking a filter calls `fetchTasks` with query params. "My Tasks" sets `assigneeId` to current member. |
| 40.8 | Add priority badge and due date styling | 40B | | Priority badges: HIGH → red, MEDIUM → amber, LOW → olive. Due date: overdue + not done → `text-red-600` with AlertTriangle icon. Pattern: follow existing Badge variants. |
| 40.9 | Handle optimistic locking conflict in UI | 40B | | When claim returns 409: toast "This task was just claimed by someone else" + refresh task list via `router.refresh()`. Pattern: follow error handling in `add-member-dialog.tsx`. |
| 40.10 | Add frontend tests | 40B | | `__tests__/components/tasks/` — ~8 tests: task list renders columns, claim button for unassigned, release button for own, filter toggles, create dialog validates title, priority badge colors, due date overdue styling, 409 conflict error. Pattern: follow `project-members-panel.test.tsx`. |

### Key Files

**Slice 40A — Create:**
- `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts`
- `frontend/components/tasks/task-list-panel.tsx`
- `frontend/components/tasks/create-task-dialog.tsx`

**Slice 40A — Modify:**
- `frontend/lib/types.ts` — Add Task types
- `frontend/components/projects/project-tabs.tsx` — Add Tasks tab
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Fetch and pass task data

**Slice 40B — Modify:**
- `frontend/components/tasks/task-list-panel.tsx` — Claim/release, filters, styling

**Slice 40B — Create:**
- `frontend/__tests__/components/tasks/` — Test files

---

## Epic 41: Document Scope Extension — Backend

**Goal**: Extend the Document entity with `scope`, `customer_id`, and `visibility` columns. Add org-scoped and customer-scoped upload-init endpoints. Add visibility toggle. Update existing document queries for backward compatibility.

**References**: [ADR-018](../adr/ADR-018-document-scope-model.md), ARCHITECTURE.md §10.2.4, §10.3.3, §10.6, §10.7

**Dependencies**: Epic 37 (V11 migration references `customers` table from V9)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **41A** | 41.1–41.5 | V11 migration, entity changes, updated service for scope-aware queries, backward compatibility tests | |
| **41B** | 41.6–41.11 | New upload-init endpoints (org, customer), visibility toggle, S3 key extension, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 41.1 | Create V11 tenant migration for document scope extension | 41A | | `db/migration/tenant/V11__extend_documents_scope.sql`. Per ADR-018: `ALTER TABLE documents ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'PROJECT'`. `ALTER TABLE documents ADD COLUMN customer_id UUID REFERENCES customers(id) ON DELETE SET NULL`. `ALTER TABLE documents ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'INTERNAL'`. `ALTER TABLE documents ALTER COLUMN project_id DROP NOT NULL`. CHECK constraint for valid FK combinations per scope. Indexes: `idx_documents_scope`, `idx_documents_customer_id`, `idx_documents_scope_visibility`. Pattern: additive migration, no data loss. |
| 41.2 | Update Document entity | 41A | | Modify `document/Document.java` — add `scope` (String: ORG/PROJECT/CUSTOMER), `customerId` (UUID nullable), `visibility` (String: INTERNAL/SHARED). Change `projectId` to `nullable = true`. Add `isOrgScoped()`, `isProjectScoped()`, `isCustomerScoped()` convenience methods. |
| 41.3 | Update DocumentRepository with scope-aware queries | 41A | | Modify `document/DocumentRepository.java` — add: `findByScope(String scope)`, `findByCustomerId(UUID customerId)`, `findByScopeAndCustomerId(String scope, UUID customerId)`. Keep existing `findByProjectId(UUID)` unchanged (backward compatible). |
| 41.4 | Update DocumentService for scope awareness | 41A | | Modify `document/DocumentService.java` — update `listDocuments(projectId)` to filter `scope = 'PROJECT'` explicitly. Add: `listOrgDocuments()`, `listCustomerDocuments(customerId)`. Ensure existing `initiateUpload` sets scope=PROJECT. |
| 41.5 | Add backward compatibility tests | 41A | | Update existing `DocumentIntegrationTest.java` — verify all existing tests pass after V11. Add ~3 tests: existing listing returns only PROJECT-scoped docs, presign-download still works, upload-init still creates PROJECT-scoped doc. |
| 41.6 | Add org-scoped document upload-init endpoint | 41B | | Add to `DocumentController.java`: `POST /api/documents/upload-init` — scope=ORG, project_id=NULL, customer_id=NULL. Auth: Owner/Admin. S3 key: `org/{orgId}/org-docs/{docId}`. Add to `DocumentService`: `initiateOrgUpload(...)`. |
| 41.7 | Add customer-scoped document upload-init endpoint | 41B | | Add to `DocumentController.java`: `POST /api/customers/{customerId}/documents/upload-init` — scope=CUSTOMER, customer_id set. Auth: Owner/Admin. S3 key: `org/{orgId}/customer/{customerId}/{docId}`. Validates customer exists. |
| 41.8 | Add document listing endpoints for non-project scopes | 41B | | Add to `DocumentController.java`: `GET /api/documents?scope=ORG`, `GET /api/documents?scope=CUSTOMER&customerId={id}`. Reuse `DocumentResponse` DTO, add `scope` and `customerId` fields. |
| 41.9 | Add visibility toggle endpoint | 41B | | Add to `DocumentController.java`: `PATCH /api/documents/{id}/visibility` with body `{ visibility: "SHARED" }` or `{ visibility: "INTERNAL" }`. Auth: Owner/Admin. |
| 41.10 | Update S3PresignedUrlService for new key patterns | 41B | | Modify `s3/S3PresignedUrlService.java` — add `generateOrgUploadUrl(orgId, documentId, contentType)` and `generateCustomerUploadUrl(orgId, customerId, documentId, contentType)`. Pattern: follow existing `generateUploadUrl`. |
| 41.11 | Add scope-specific integration tests | 41B | | `document/DocumentScopeIntegrationTest.java`. ~12 tests: org-scoped upload-init (201), org-scoped listing, customer-scoped upload-init (201), customer-scoped listing, visibility toggle, RBAC — member cannot upload org-scoped (403), RBAC — member cannot upload customer-scoped (403), RBAC — member can list org-scoped (200), invalid scope combinations (400), S3 key format per scope, confirm flow for non-project docs. |

### Key Files

**Slice 41A — Create:**
- `backend/src/main/resources/db/migration/tenant/V11__extend_documents_scope.sql`

**Slice 41A — Modify:**
- `backend/src/main/java/.../document/Document.java`
- `backend/src/main/java/.../document/DocumentRepository.java`
- `backend/src/main/java/.../document/DocumentService.java`
- `backend/src/test/java/.../document/DocumentIntegrationTest.java`

**Slice 41B — Modify:**
- `backend/src/main/java/.../document/DocumentController.java`
- `backend/src/main/java/.../document/DocumentService.java`
- `backend/src/main/java/.../s3/S3PresignedUrlService.java`

**Slice 41B — Create:**
- `backend/src/test/java/.../document/DocumentScopeIntegrationTest.java`

### Architecture Decisions

- **Additive migration**: V11 only adds columns and constraints. Existing data continues to work — documents are PROJECT-scoped by default. Zero downtime.
- **project_id nullable**: Required for ORG scope. Existing code must use scope-filtered queries.
- **CHECK constraint**: Database-level enforcement of valid FK combinations per scope.
- **Two-slice decomposition**: 41A is purely additive (migration + entity + backward compat). 41B adds new endpoints. Existing functionality verified before new scope features land.

---

## Epic 42: Document Scope Extension — Frontend

**Goal**: Add UI for org-scoped document management, customer-scoped document upload on customer detail page, scope indicator badges, and visibility toggle.

**References**: ARCHITECTURE.md §10.10 Slice D

**Dependencies**: Epic 38 (customer detail page), Epic 41 (scope-aware backend)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **42A** | 42.1–42.5 | Updated types, org documents page, org document upload, scope badges | |
| **42B** | 42.6–42.10 | Customer documents tab, customer doc upload, visibility toggle, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 42.1 | Update Document TypeScript types for scope | 42A | | In `lib/types.ts`: add `DocumentScope = "ORG" \| "PROJECT" \| "CUSTOMER"`, `DocumentVisibility = "INTERNAL" \| "SHARED"`. Update `Document` interface: add `scope`, `customerId`, `visibility`. |
| 42.2 | Create org documents page | 42A | | `app/(app)/org/[slug]/documents/page.tsx` — server component. Fetches `GET /api/documents?scope=ORG`. Page header: "Organization Documents" + count badge. "Upload Document" pill button (admin/owner). Catalyst-style table. Empty state: FileText icon. Add nav entry in `lib/nav-items.ts`. Pattern: follow `projects/page.tsx`. |
| 42.3 | Create org document upload component | 42A | | `components/documents/org-document-upload.tsx` — reuses `FileUploadZone` from Epic 11. Server action `initiateOrgUpload(slug, formData)` calls `POST /api/documents/upload-init`. Add to `app/(app)/org/[slug]/documents/actions.ts`. |
| 42.4 | Add scope badges to document lists | 42A | | Update `components/documents/documents-panel.tsx` — scope badge when showing mixed-scope views: ORG → olive, PROJECT → default, CUSTOMER → indigo. |
| 42.5 | Add org documents nav entry and loading.tsx | 42A | | Add "Documents" to `lib/nav-items.ts`. Create `app/(app)/org/[slug]/documents/loading.tsx`. |
| 42.6 | Add customer documents tab to customer detail page | 42B | | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx` — wire "Documents" tab. Fetch `GET /api/documents?scope=CUSTOMER&customerId={id}`. Upload button for admin/owner. |
| 42.7 | Create customer document upload component | 42B | | `components/documents/customer-document-upload.tsx` — reuses `FileUploadZone`. Server action calls `POST /api/customers/{customerId}/documents/upload-init`. |
| 42.8 | Add visibility toggle component | 42B | | `components/documents/visibility-toggle.tsx` — toggle "Internal"/"Shared" on each document row. Lock/globe icon. Admin/owner only. Calls `PATCH /api/documents/{id}/visibility`. |
| 42.9 | Update DocumentResponse handling for scope fields | 42B | | Ensure all document-fetching server actions handle new `scope`, `customerId`, `visibility` fields. Update `api.ts` calls for new query parameters. |
| 42.10 | Add frontend tests | 42B | | `__tests__/components/documents/` — ~8 tests: org documents page renders, org upload triggers endpoint, customer documents tab renders, customer upload endpoint, visibility toggle, scope badge variants, admin sees upload buttons, member does not. |

### Key Files

**Slice 42A — Create:**
- `frontend/app/(app)/org/[slug]/documents/page.tsx`
- `frontend/app/(app)/org/[slug]/documents/actions.ts`
- `frontend/app/(app)/org/[slug]/documents/loading.tsx`
- `frontend/components/documents/org-document-upload.tsx`

**Slice 42A — Modify:**
- `frontend/lib/types.ts` — Add scope/visibility types
- `frontend/lib/nav-items.ts` — Add Documents nav entry
- `frontend/components/documents/documents-panel.tsx` — Scope badges

**Slice 42B — Create:**
- `frontend/components/documents/customer-document-upload.tsx`
- `frontend/components/documents/visibility-toggle.tsx`
- `frontend/__tests__/components/documents/`

**Slice 42B — Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Wire documents tab
- `frontend/app/(app)/org/[slug]/customers/[id]/actions.ts` — Customer doc actions

---

## Epic 43: Customer Portal Groundwork

**Goal**: Implement backend infrastructure for the future customer portal — magic link authentication, portal JWT issuance, `/portal/*` endpoint skeleton with scoped read-only access. Build a minimal portal frontend with login, project list, and document viewer. Per ADR-020.

**References**: [ADR-020](../adr/ADR-020-customer-portal-approach.md), ARCHITECTURE.md §10.8

**Dependencies**: Epic 37 (customers exist), Epic 41 (document visibility exists)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **43A** | 43.1–43.5 | Backend: magic link service, CustomerAuthFilter, portal JWT, `/portal/*` security filter chain | |
| **43B** | 43.6–43.10 | Backend: portal endpoints (projects, documents), scoped query services, integration tests | |
| **43C** | 43.11–43.15 | Frontend: portal login page, portal layout, project list, document list, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 43.1 | Add CUSTOMER_ID ScopedValue to RequestScopes | 43A | | Add `public static final ScopedValue<UUID> CUSTOMER_ID = ScopedValue.newInstance()` to `multitenancy/RequestScopes.java`. Pattern: follow existing `MEMBER_ID`. |
| 43.2 | Create MagicLinkService | 43A | | `portal/MagicLinkService.java` — generates time-limited JWT token (15 min) tied to customer email + org ID. `generateToken(email, clerkOrgId)` → embeds customerId + orgId in claims, signs with portal secret. `verifyToken(token)` → returns `CustomerIdentity(customerId, clerkOrgId)`. For MVP, skip email sending — return magic link URL in response. Pattern: stateless JWT tokens (no DB table). |
| 43.3 | Create PortalJwtService | 43A | | `portal/PortalJwtService.java` — issues short-lived JWTs (1 hour) with claims: `customer_id`, `org_id`, `type: "customer"`. Signs with `PORTAL_JWT_SECRET` env var. Verifies signature + expiry. Pattern: use `com.nimbusds.jose` (available via Spring Security). |
| 43.4 | Create CustomerAuthFilter | 43A | | `portal/CustomerAuthFilter.java` — `OncePerRequestFilter` for `/portal/**`. Extracts Bearer token, verifies via `PortalJwtService`, binds `RequestScopes.CUSTOMER_ID` and `RequestScopes.TENANT_ID`. Invalid/expired → 401. Pattern: follow `MemberFilter.java`. |
| 43.5 | Configure portal security filter chain | 43A | | Modify `config/SecurityConfig.java` — add second `@Bean SecurityFilterChain portalFilterChain` with `@Order(1)` matching `/portal/**`. Uses `CustomerAuthFilter`. Permits `/portal/auth/**` without auth. No MemberFilter. Existing `/api/**` chain unchanged. |
| 43.6 | Create PortalAuthController | 43B | | `portal/PortalAuthController.java` — `POST /portal/auth/request-link` with `{ email, orgSlug }`. Resolves org, looks up customer by email, generates magic link. `GET /portal/auth/verify?token=...` — verifies, issues portal JWT. |
| 43.7 | Create PortalProjectController | 43B | | `portal/PortalProjectController.java` — `GET /portal/projects` lists projects linked to authenticated customer via `customer_projects`. Returns `PortalProjectResponse(id, name, description, documentCount)`. |
| 43.8 | Create PortalDocumentController | 43B | | `portal/PortalDocumentController.java` — `GET /portal/projects/{projectId}/documents` lists SHARED documents in linked project. `GET /portal/documents` lists all SHARED docs for customer (org + customer scoped). `GET /portal/documents/{id}/presign-download` for read-only download. |
| 43.9 | Create PortalQueryService | 43B | | `portal/PortalQueryService.java` — centralized read-only query service. Methods: `listCustomerProjects(customerId)`, `listProjectDocuments(projectId, customerId)`, `listCustomerDocuments(customerId)`, `getDocument(documentId, customerId)`. Enforces visibility=SHARED. `@Transactional(readOnly = true)`. |
| 43.10 | Add portal integration tests | 43B | | `portal/PortalIntegrationTest.java`. ~10 tests: request magic link (valid email), verify token → portal JWT, expired token → 401, GET /portal/projects returns only linked projects, SHARED docs visible, INTERNAL docs hidden, other customer's docs hidden, unauthenticated → 401, staff JWT on portal → 401, cross-tenant isolation. |
| 43.11 | Create portal login page | 43C | | `app/portal/page.tsx` — public page. Email input + "Send Magic Link" button. For MVP: shows magic link URL directly. Pattern: matches auth page styling. |
| 43.12 | Create portal layout | 43C | | `app/portal/layout.tsx` — minimal layout. Header: "DocTeams Portal" + customer name. Top nav: "Projects" and "Documents". No Clerk. `bg-olive-50`. |
| 43.13 | Create portal project list page | 43C | | `app/portal/(authenticated)/projects/page.tsx` — card grid of linked projects. Click → project documents. |
| 43.14 | Create portal document list and download | 43C | | `app/portal/(authenticated)/projects/[id]/page.tsx` — SHARED documents for project. `app/portal/(authenticated)/documents/page.tsx` — all SHARED docs. Download via presigned URL. |
| 43.15 | Add frontend tests | 43C | | `__tests__/portal/` — ~6 tests: login form renders, success message on link request, project list renders, document list renders, download triggers presign, portal layout renders without Clerk. |

### Key Files

**Slice 43A — Create:**
- `backend/src/main/java/.../portal/MagicLinkService.java`
- `backend/src/main/java/.../portal/PortalJwtService.java`
- `backend/src/main/java/.../portal/CustomerAuthFilter.java`

**Slice 43A — Modify:**
- `backend/src/main/java/.../multitenancy/RequestScopes.java` — Add CUSTOMER_ID
- `backend/src/main/java/.../config/SecurityConfig.java` — Add portal filter chain

**Slice 43B — Create:**
- `backend/src/main/java/.../portal/PortalAuthController.java`
- `backend/src/main/java/.../portal/PortalProjectController.java`
- `backend/src/main/java/.../portal/PortalDocumentController.java`
- `backend/src/main/java/.../portal/PortalQueryService.java`
- `backend/src/test/java/.../portal/PortalIntegrationTest.java`

**Slice 43C — Create:**
- `frontend/app/portal/page.tsx`
- `frontend/app/portal/layout.tsx`
- `frontend/app/portal/(authenticated)/projects/page.tsx`
- `frontend/app/portal/(authenticated)/projects/[id]/page.tsx`
- `frontend/app/portal/(authenticated)/documents/page.tsx`
- `frontend/__tests__/portal/`

### Architecture Decisions

- **Separate `portal/` package**: Clear boundary between staff API and customer portal.
- **JWT-based magic links (no DB table)**: Stateless verification. Single-use via Caffeine cache of used token IDs.
- **Dual SecurityFilterChain**: `@Order` + `securityMatcher` separates staff and customer auth.
- **Read-only portal endpoints**: All `/portal/*` endpoints are GET only. Simplifies authorization.
- **Three-slice decomposition**: 43A (auth) → 43B (endpoints) → 43C (frontend). Each deployable independently.
- **MVP magic link UX**: Link URL returned in response (no email infra needed). Email sending added later.
