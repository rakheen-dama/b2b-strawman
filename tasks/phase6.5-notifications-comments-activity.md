# Phase 6.5 — Notifications, Comments & Activity

Phase 6.5 adds the **communication and awareness layer** to the DocTeams platform: a comments system for annotating tasks and documents, a project activity feed that surfaces audit events as a human-readable timeline, an in-app notification system that alerts users when things relevant to them change, and email notification stubs for future SES integration. All capabilities are built on Spring `ApplicationEvent` introduced for the first time in this phase (per [ADR-032](../adr/ADR-032-spring-application-events-for-portal.md)). See `architecture/phase6.5-notifications-comments-activity.md` (Section 11 of architecture/ARCHITECTURE.md) and [ADR-034](../adr/ADR-034-flat-comments-with-threading-schema.md)–[ADR-038](../adr/ADR-038-polling-for-notification-delivery.md) for design details.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 59 | Comment Backend — Entity, Migration & CRUD API | Backend | — | M | 59A, 59B | **Done** (PR #107, #109) |
| 60 | Comment Frontend — CommentSection & Integration | Frontend | 59 | M | 60A, 60B | |
| 61 | Domain Events & Notification Backend — Events, Entity, Migration & Handler | Backend | — | L | 61A, 61B, 61C | **Done** (PR #110, #111, #112) |
| 62 | Notification API & Preferences Backend | Backend | 61 | M | 62A, 62B | **Done** (PR #113, #114) |
| 63 | Notification Frontend — Bell, Page & Preferences UI | Frontend | 62 | M | 63A, 63B | |
| 64 | Activity Feed Backend — Service, Formatter & API | Backend | 59 (V15 migration) | M | 64A, 64B | **Done** (PR #115, #116) |
| 65 | Activity Feed Frontend — Activity Tab & Components | Frontend | 64 | S | 65A | |
| 66 | Email Notification Stubs — Channel Abstraction & Templates | Backend | 61 | S | 66A | |

## Dependency Graph

```
[E59 Comment Backend] ─────────────┬──► [E60 Comment Frontend]
                                    │
                                    └──► [E64 Activity Feed Backend] ──► [E65 Activity Feed Frontend]

[E61 Domain Events & Notification   ┬──► [E62 Notification API & Preferences] ──► [E63 Notification Frontend]
     Backend]                        │
                                     └──► [E66 Email Stubs]
```

**Parallel tracks**: Epics 59 and 61 have zero dependency on each other and can be developed in parallel. The comment domain (59 → 60, 59 → 64 → 65) and the notification domain (61 → 62 → 63, 61 → 66) form two independent chains. Epic 64 (Activity Feed) depends only on Epic 59's V15 migration (which creates `idx_audit_project` expression index), not on the comment entity or service itself.

## Implementation Order

### Stage 1: Backend Foundations (Parallel)

| Order | Epic | Rationale |
|-------|------|-----------|
| 1a | Epic 59: Comment Backend | V15 migration (comments table + audit expression index), Comment entity/repo/service/controller, integration tests. Prerequisite for comment frontend and activity feed. |
| 1b | Epic 61: Domain Events & Notification Backend | DomainEvent sealed interface, all event records, event publication in existing services (TaskService, DocumentService, ProjectMemberService), Notification entity/repo, V16+V17 migrations, NotificationEventHandler. Prerequisite for notification API and email stubs. |

### Stage 2: API Layer + Activity Feed (Parallel)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 62: Notification API & Preferences | NotificationService, NotificationController, NotificationPreferenceController. Depends on entities/handler from Epic 61. |
| 2b | Epic 64: Activity Feed Backend | ActivityService, ActivityMessageFormatter, ActivityController, audit event enrichment. Depends on V15 expression index from Epic 59. |
| 2c | Epic 66: Email Notification Stubs | NotificationChannel interface, InAppNotificationChannel, EmailNotificationChannel (stub), NotificationDispatcher, EmailTemplate. Depends on Notification entity from Epic 61. |

### Stage 3: Frontend (Parallel)

| Order | Epic | Rationale |
|-------|------|-----------|
| 3a | Epic 60: Comment Frontend | CommentSection, AddCommentForm, CommentItem, EditCommentDialog. Depends on comment API from Epic 59. |
| 3b | Epic 63: Notification Frontend | NotificationBell, dropdown, full page, preferences page. Depends on notification API from Epic 62. |
| 3c | Epic 65: Activity Feed Frontend | Activity tab, ActivityFeed, ActivityItem, ActivityFilter components. Depends on activity API from Epic 64. |

### Timeline

```
Stage 1:  [E59]  [E61]                    ← parallel backend foundations
Stage 2:  [E62]  [E64]  [E66]            ← parallel API + stubs (after respective Stage 1 deps)
Stage 3:  [E60]  [E63]  [E65]            ← parallel frontend (after respective Stage 2 deps)
```

---

## Epic 59: Comment Backend — Entity, Migration & CRUD API

**Goal**: Create the `comments` table (V15 migration including the `idx_audit_project` expression index on `audit_events`), implement the `Comment` entity with standard `@FilterDef`/`@Filter`/`TenantAware` pattern, `CommentRepository` with JPQL queries, `CommentService` with CRUD operations + authorization + audit logging + event publication, `CommentController` with REST endpoints, and integration tests. Includes `CommentCreatedEvent`, `CommentUpdatedEvent`, `CommentDeletedEvent`, and `CommentVisibilityChangedEvent` record types.

**References**: [ADR-034](../adr/ADR-034-flat-comments-with-threading-schema.md), [ADR-037](../adr/ADR-037-comment-visibility-model.md), `architecture/phase6.5-notifications-comments-activity.md` Sections 11.2.1, 11.3.1, 11.4.1, 11.9.1–11.9.3

**Dependencies**: None (builds on existing multi-tenant infrastructure and audit service from Phase 6)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **59A** | 59.1–59.5 | V15 migration (comments table + audit expression index), Comment entity, CommentRepository, DomainEvent sealed interface, all event records | |
| **59B** | 59.6–59.11 | CommentService (CRUD + authorization + audit + event publication), CommentController (REST endpoints), integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 59.1 | Create V15 tenant migration for comments table and audit expression index | 59A | | `db/migration/tenant/V15__create_comments.sql`. Two parts: (1) `comments` table: `id` (UUID PK DEFAULT gen_random_uuid()), `entity_type` (VARCHAR(20) NOT NULL), `entity_id` (UUID NOT NULL), `project_id` (UUID NOT NULL), `author_member_id` (UUID NOT NULL), `body` (TEXT NOT NULL), `visibility` (VARCHAR(20) NOT NULL DEFAULT 'INTERNAL'), `parent_id` (UUID, nullable — reserved for future threading per ADR-034), `tenant_id` (VARCHAR(255)), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()), `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). Indexes: `idx_comments_entity (entity_type, entity_id, created_at)`, `idx_comments_project (project_id, created_at)`, `idx_comments_tenant (tenant_id) WHERE tenant_id IS NOT NULL`. RLS: `ENABLE ROW LEVEL SECURITY` + `CREATE POLICY comments_tenant_isolation`. (2) Expression index on audit_events: `CREATE INDEX IF NOT EXISTS idx_audit_project ON audit_events ((details->>'project_id'))` — for activity feed project filtering (ADR-035). Pattern: follow `V14__create_audit_events.sql` for RLS structure. |
| 59.2 | Create Comment entity | 59A | | `comment/Comment.java` — JPA entity mapped to `comments`. Fields per Section 11.2.1: UUID id, String entityType (VARCHAR(20), NOT NULL), UUID entityId (NOT NULL), UUID projectId (NOT NULL), UUID authorMemberId (NOT NULL), String body (TEXT, NOT NULL), String visibility (VARCHAR(20), NOT NULL), UUID parentId (nullable), String tenantId, Instant createdAt, Instant updatedAt. Annotations: `@FilterDef`/`@Filter` for `tenantFilter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Constructor: `Comment(String entityType, UUID entityId, UUID projectId, UUID authorMemberId, String body, String visibility)`. Methods: `updateBody(String body)`, `updateVisibility(String visibility)` — both set `updatedAt = Instant.now()`. See Section 11.9.2 for full entity code. Pattern: follow `timeentry/TimeEntry.java` entity structure. |
| 59.3 | Create CommentRepository | 59A | | `comment/CommentRepository.java` — extends `JpaRepository<Comment, UUID>`. Methods: `Optional<Comment> findOneById(UUID id)` (JPQL `@Query` — CRITICAL: do NOT use `findById()`), `Page<Comment> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable)` (JPQL, ORDER BY createdAt ASC), `List<UUID> findDistinctAuthorsByEntity(String entityType, UUID entityId)` (for notification fan-out — `SELECT DISTINCT c.authorMemberId`). See Section 11.9.3 for full code. Pattern: follow `timeentry/TimeEntryRepository.java` with JPQL `findOneById`. |
| 59.4 | Create DomainEvent sealed interface and comment event records | 59A | | Create `event/` package. `event/DomainEvent.java` — `public sealed interface DomainEvent` with methods: `eventType()`, `entityType()`, `entityId()`, `projectId()`, `actorMemberId()`, `actorName()`, `tenantId()`, `occurredAt()`, `details()`. Permits: `CommentCreatedEvent`, `CommentUpdatedEvent`, `CommentDeletedEvent`, `CommentVisibilityChangedEvent`, `TaskAssignedEvent`, `TaskClaimedEvent`, `TaskStatusChangedEvent`, `DocumentUploadedEvent`, `MemberAddedToProjectEvent`. Create four comment event records in `event/`: `CommentCreatedEvent` (extra fields: targetEntityType, targetEntityId, visibility), `CommentUpdatedEvent`, `CommentDeletedEvent` (extra: targetEntityType, targetEntityId), `CommentVisibilityChangedEvent` (extra: oldVisibility, newVisibility). See Section 11.6.1–11.6.2 for full record definitions. All are Java records implementing `DomainEvent`. |
| 59.5 | Create remaining event records (task, document, member) | 59A | | Create in `event/` package: `TaskAssignedEvent` (extra: assigneeMemberId, taskTitle), `TaskClaimedEvent` (extra: previousAssigneeId nullable, taskTitle), `TaskStatusChangedEvent` (extra: oldStatus, newStatus, assigneeMemberId nullable, taskTitle), `DocumentUploadedEvent` (extra: documentName), `MemberAddedToProjectEvent` (extra: addedMemberId, projectName). All implement `DomainEvent` sealed interface. See Section 11.6.2 for full definitions. These records are needed by both the notification handler (Epic 61) and the comment service event publication (59B). |
| 59.6 | Create CommentService with CRUD operations | 59B | | `comment/CommentService.java`. Constructor injection of `CommentRepository`, `ProjectAccessService`, `TaskRepository`, `DocumentRepository`, `AuditService`, `ApplicationEventPublisher`, `MemberRepository`. Methods: (1) `createComment(UUID projectId, String entityType, UUID entityId, String body, String visibility, UUID memberId, String orgRole)` — calls `projectAccessService.requireViewAccess()`, validates entityType is TASK or DOCUMENT, verifies entity exists and belongs to project, determines visibility (SHARED requires `access.canEdit()`), saves comment, logs audit event (`comment.created`), publishes `CommentCreatedEvent`. (2) `updateComment(UUID projectId, UUID commentId, String body, String visibility, UUID memberId, String orgRole)` — loads comment, verifies project, authorization checks per Section 11.10 permission table, updates fields, logs `comment.updated` audit, publishes `CommentUpdatedEvent`. If visibility changed, also logs `comment.visibility_changed` and publishes `CommentVisibilityChangedEvent`. (3) `deleteComment(UUID projectId, UUID commentId, UUID memberId, String orgRole)` — loads comment, verifies project, authorization (author or admin/owner), logs `comment.deleted` audit (captures body in details), publishes `CommentDeletedEvent`, hard-deletes. (4) `listComments(UUID projectId, String entityType, UUID entityId, Pageable pageable, UUID memberId, String orgRole)` — calls `requireViewAccess()`, delegates to repo. All `@Transactional`. Pattern: follow `task/TaskService.java` for access control + audit integration. |
| 59.7 | Create CommentController with request/response DTOs | 59B | | `comment/CommentController.java`. `@RestController`, `@RequestMapping("/api/projects/{projectId}/comments")`. Inner record DTOs: `CreateCommentRequest(String entityType, UUID entityId, String body, String visibility)` with `@NotBlank body`, `UpdateCommentRequest(String body, String visibility)`, `CommentResponse(UUID id, String entityType, UUID entityId, UUID projectId, UUID authorMemberId, String authorName, String authorAvatarUrl, String body, String visibility, Instant createdAt, Instant updatedAt)`. Endpoints: `POST /` → 201 Created, `GET /` with `entityType` + `entityId` + pagination params → 200, `PUT /{commentId}` → 200, `DELETE /{commentId}` → 204. Each endpoint extracts `memberId` and `orgRole` from `RequestScopes`. For author name/avatar resolution: batch-load from `MemberRepository` or resolve inline via `memberRepository.findOneById(comment.getAuthorMemberId())`. Pattern: follow `task/TaskController.java` for RequestScopes usage and response structure. |
| 59.8 | Add SecurityConfig update for comment endpoints | 59B | | Modify `security/SecurityConfig.java` — ensure `/api/projects/*/comments/**` is covered by authenticated endpoint patterns. Likely already covered by existing `/api/**` pattern, but verify. If not, add explicitly. |
| 59.9 | Add CommentService integration tests | 59B | | `comment/CommentServiceIntegrationTest.java`. ~8 tests: create comment on task (verify persisted correctly), create comment on document, create comment with SHARED visibility by lead (success), create comment with SHARED by regular member (rejected with ForbiddenException), update own comment body (success), update own comment visibility without lead role (rejected), delete own comment, delete others' comment as admin. Seed: provision tenant, sync members (admin + regular member), create project, add project members, create task in `@BeforeAll`. Pattern: follow `task/TaskIntegrationTest.java` or `timeentry/TimeEntryIntegrationTest.java` setup. |
| 59.10 | Add CommentController integration tests (MockMvc) | 59B | | `comment/CommentControllerTest.java`. ~7 tests: POST creates comment and returns 201 with correct JSON shape, GET lists comments ordered by createdAt ASC, GET with pagination, PUT updates body and returns 200, PUT by non-author non-admin returns 403/404, DELETE by author returns 204, DELETE by non-author non-admin returns 404. Use MockMvc with `jwt()` mock per backend CLAUDE.md conventions. Pattern: follow `audit/AuditEventControllerTest.java` for MockMvc + JWT mock pattern. |
| 59.11 | Add comment tenant isolation tests | 59B | | ~3 tests within `CommentServiceIntegrationTest.java` or separate file: comment created in tenant A is invisible in tenant B (Pro isolation), comment created by Starter org A is invisible to Starter org B (shared schema + @Filter isolation), verify `tenant_id` auto-populated by `TenantAwareEntityListener`. Pattern: follow `audit/AuditTenantIsolationTest.java`. |

### Key Files

**Slice 59A — Create:**
- `backend/src/main/resources/db/migration/tenant/V15__create_comments.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/CommentCreatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/CommentUpdatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/CommentDeletedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/CommentVisibilityChangedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TaskAssignedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TaskClaimedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TaskStatusChangedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DocumentUploadedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/MemberAddedToProjectEvent.java`

**Slice 59A — Read for context:**
- `backend/src/main/resources/db/migration/tenant/V14__create_audit_events.sql` — Migration pattern (RLS, indexes)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — Entity pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — JPQL findOneById pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` — Interface to implement
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` — Entity listener

**Slice 59B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/comment/CommentServiceIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/comment/CommentControllerTest.java`

**Slice 59B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` — Verify `/api/projects/*/comments/**` is authenticated (likely already covered)

**Slice 59B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Access control + audit integration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — RequestScopes usage, response structure
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectAccessService.java` — requireViewAccess(), canEdit(), isAdmin()
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — auditService.log() pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` — AuditEventBuilder.builder() pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java` — Test setup pattern

### Architecture Decisions

- **`comment/` package**: New feature package following existing `task/`, `customer/`, `project/`, `timeentry/` pattern.
- **`event/` package**: Separate top-level package for the DomainEvent sealed interface and all event records. Shared by both comment and notification domains. This aligns with the architecture doc Section 11.9.1.
- **Two-slice decomposition**: 59A (migration + entity + repository + event records) is the data layer. 59B (service + controller + tests) is the business logic and API layer. Each slice touches ~8–13 files (59A has many small event records; 59B has fewer but larger files).
- **Hard delete for comments**: Per architecture doc Section 11.2.1. The deletion itself is audited with the body captured in details.
- **`parent_id` reserved but unused**: Per ADR-034. Column exists in migration, mapped in entity, but never read/written by Phase 6.5 code.
- **Dual-write for audit + events**: CommentService calls both `auditService.log()` and `eventPublisher.publishEvent()`. See architecture doc Section 11.6.3.

---

## Epic 60: Comment Frontend — CommentSection & Integration

**Goal**: Build the `CommentSection` reusable component with `AddCommentForm`, `CommentItem`, and `EditCommentDialog` sub-components. Integrate into the task detail view and document detail view on the project page. Create server actions for comment CRUD. Includes frontend tests.

**References**: `architecture/phase6.5-notifications-comments-activity.md` Sections 11.9.6

**Dependencies**: Epic 59 (Comment backend API must exist)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **60A** | 60.1–60.6 | Server actions, CommentSection, AddCommentForm, CommentItem components, frontend tests | |
| **60B** | 60.7–60.10 | EditCommentDialog, integration into task detail and document detail views, visibility badge + toggle | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 60.1 | Create comment server actions | 60A | | `lib/actions/comments.ts`. Server actions using `api.ts` client: `createComment(projectId: string, entityType: string, entityId: string, body: string, visibility?: string)` — POST to `/api/projects/{projectId}/comments`, revalidates project page. `updateComment(projectId: string, commentId: string, body?: string, visibility?: string)` — PUT. `deleteComment(projectId: string, commentId: string)` — DELETE. `fetchComments(projectId: string, entityType: string, entityId: string, page?: number)` — GET with query params, returns typed response. Add TypeScript types: `Comment`, `CommentsResponse` matching backend DTOs. Pattern: follow `app/(app)/org/[slug]/projects/[id]/task-actions.ts` for server action structure. |
| 60.2 | Create CommentSection server component | 60A | | `components/comments/comment-section.tsx`. Server component. Props: `projectId: string`, `entityType: "TASK" | "DOCUMENT"`, `entityId: string`, `orgSlug: string`, `currentMemberId: string`, `canManageVisibility: boolean` (lead/admin/owner). Fetches comments on the server via `fetchComments()`. Renders `AddCommentForm` (client) + list of `CommentItem` components. Passes `canManageVisibility` down to form and items. Shows "No comments yet" empty state. Pattern: follow `components/tasks/` component structure for server/client split. |
| 60.3 | Create AddCommentForm client component | 60A | | `components/comments/add-comment-form.tsx`. `"use client"`. Props: `projectId`, `entityType`, `entityId`, `canManageVisibility`. Renders: textarea input + Submit button. If `canManageVisibility`, shows a visibility toggle (dropdown or button group) for INTERNAL/SHARED, defaulting to INTERNAL. On submit: calls `createComment` server action with form data. Clears form on success. Uses `useTransition` for optimistic UI. Olive styling per design system. Pattern: follow form patterns in `components/projects/` or `components/tasks/`. |
| 60.4 | Create CommentItem component | 60A | | `components/comments/comment-item.tsx`. `"use client"`. Props: `comment: Comment`, `currentMemberId: string`, `canManageVisibility: boolean`, `orgSlug: string`, `projectId: string`. Renders: author avatar (initials fallback), author name, relative timestamp (`formatDate` from `lib/format.ts`), comment body, visibility badge (if SHARED, show "Customer visible" badge using `<Badge variant="success">`). Shows edit/delete action buttons if `comment.authorMemberId === currentMemberId` or user is admin/owner. Edit opens `EditCommentDialog`. Delete shows `AlertDialog` confirmation then calls `deleteComment`. Pattern: follow `components/tasks/` for action buttons and dialog patterns. Remember `afterEach(() => cleanup())` for Radix leak per frontend CLAUDE.md. |
| 60.5 | Create comment TypeScript types | 60A | | Add to `lib/actions/comments.ts` inline types: `Comment { id: string; entityType: string; entityId: string; projectId: string; authorMemberId: string; authorName: string; authorAvatarUrl: string | null; body: string; visibility: "INTERNAL" | "SHARED"; createdAt: string; updatedAt: string; }`, `CommentsResponse { content: Comment[]; page: { size: number; number: number; totalElements: number; totalPages: number; }; }`. |
| 60.6 | Add CommentSection frontend tests | 60A | | `__tests__/comments/comment-section.test.tsx`. ~4 tests: renders comment list with correct items, shows "No comments yet" for empty list, AddCommentForm shows visibility toggle for lead/admin, AddCommentForm hides visibility toggle for regular member. Use vitest + @testing-library/react + happy-dom. Mock server actions. `afterEach(() => cleanup())` for Radix leak prevention. Pattern: follow existing `__tests__/` test structure. |
| 60.7 | Create EditCommentDialog | 60B | | `components/comments/edit-comment-dialog.tsx`. `"use client"`. Controlled Dialog component. Props: `comment: Comment`, `canManageVisibility: boolean`, `projectId: string`. Renders: textarea pre-filled with comment body, visibility dropdown (if `canManageVisibility`). Save calls `updateComment` server action. Cancel closes dialog. Uses `Dialog` from Shadcn UI. Pattern: follow `components/tasks/` dialog pattern. Use controlled `open` state (revalidates, does not redirect — per lesson in MEMORY.md). |
| 60.8 | Integrate CommentSection into task detail view | 60B | | Modify `app/(app)/org/[slug]/projects/[id]/page.tsx`. When a task is selected/expanded in the task detail view, render `<CommentSection entityType="TASK" entityId={task.id} projectId={projectId} ... />` below the task details. Thread `canManageVisibility` from the project access check (lead/admin/owner). This may require adding a tasks tab panel or expanding the existing task detail section. Pattern: follow how `TimeEntryList` or `TimeSummaryPanel` was integrated in Phase 5. |
| 60.9 | Integrate CommentSection into document detail view | 60B | | Modify `app/(app)/org/[slug]/projects/[id]/page.tsx`. When viewing document details (or a document sidebar), render `<CommentSection entityType="DOCUMENT" entityId={document.id} ... />`. Thread `canManageVisibility` from project access. If document detail is a dialog/sheet, add CommentSection inside it. If not, add below document list when a document is selected. |
| 60.10 | Add EditCommentDialog and integration tests | 60B | | `__tests__/comments/edit-comment-dialog.test.tsx`. ~3 tests: opens dialog with pre-filled body, saves updated body, shows visibility toggle only when canManageVisibility is true. `__tests__/comments/comment-item.test.tsx`. ~3 tests: shows edit/delete for own comment, hides edit/delete for others' comment (non-admin), shows visibility badge for SHARED comments. Total: ~6 tests. |

### Key Files

**Slice 60A — Create:**
- `frontend/lib/actions/comments.ts`
- `frontend/components/comments/comment-section.tsx`
- `frontend/components/comments/add-comment-form.tsx`
- `frontend/components/comments/comment-item.tsx`
- `frontend/__tests__/comments/comment-section.test.tsx`

**Slice 60A — Modify:**
- `frontend/lib/types.ts` — Add Comment and CommentsResponse types (or define inline in actions file)

**Slice 60A — Read for context:**
- `frontend/lib/api.ts` — Backend API client pattern
- `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` — Server action pattern
- `frontend/components/tasks/` — Component structure to follow
- `frontend/lib/format.ts` — `formatDate` utility for relative timestamps

**Slice 60B — Create:**
- `frontend/components/comments/edit-comment-dialog.tsx`
- `frontend/__tests__/comments/edit-comment-dialog.test.tsx`
- `frontend/__tests__/comments/comment-item.test.tsx`

**Slice 60B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add CommentSection to task detail and document detail views

**Slice 60B — Read for context:**
- `frontend/components/ui/dialog.tsx` — Shadcn Dialog component
- `frontend/components/ui/alert-dialog.tsx` — AlertDialog for delete confirmation
- `frontend/components/ui/badge.tsx` — Badge variants for visibility indicator
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Current project detail structure

### Architecture Decisions

- **`components/comments/` directory**: New feature component directory following existing `components/tasks/`, `components/documents/` pattern.
- **Server/client split**: `CommentSection` is a server component that fetches data; `AddCommentForm`, `CommentItem`, `EditCommentDialog` are client components for interactivity.
- **`canManageVisibility` prop threading**: The project access check (lead/admin/owner) is resolved in the server component and threaded to client components. This matches the Phase 5 lesson about frontend/backend permission parity.
- **Two-slice decomposition**: 60A (actions + core components + tests) and 60B (edit dialog + integration into existing views). Each touches ~5–7 files.

---

## Epic 61: Domain Events & Notification Backend — Events, Entity, Migration & Handler

**Goal**: Implement the notification infrastructure: publish Spring `ApplicationEvent` instances from existing services (`TaskService`, `DocumentService`, `ProjectMemberService`), create `Notification` and `NotificationPreference` entities with V16 and V17 migrations, implement `NotificationEventHandler` with `@TransactionalEventListener(phase = AFTER_COMMIT)` for notification fan-out, and `NotificationService` for creating notification rows. This is the core plumbing that all other notification features depend on.

**References**: [ADR-032](../adr/ADR-032-spring-application-events-for-portal.md), [ADR-036](../adr/ADR-036-synchronous-notification-fanout.md), `architecture/phase6.5-notifications-comments-activity.md` Sections 11.2.2, 11.2.3, 11.3.3, 11.6

**Dependencies**: None (the `event/` package with DomainEvent and event records is created in Epic 59A, but this epic can also create them if 61 starts before 59 — the sealed interface permits clause can be updated in whichever epic merges second)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **61A** | 61.1–61.6 | V16 + V17 migrations, Notification entity + repository, NotificationPreference entity + repository, NotificationService (create + query) | |
| **61B** | 61.7–61.11 | Event publication in existing services (TaskService, DocumentService, ProjectMemberService), NotificationEventHandler with all @TransactionalEventListener methods | |
| **61C** | 61.12–61.16 | NotificationService fan-out logic (handle* methods), integration tests for event handler + fan-out + tenant isolation | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 61.1 | Create V16 tenant migration for notifications table | 61A | | `db/migration/tenant/V16__create_notifications.sql`. Table: `notifications` with columns per Section 11.2.2: `id` (UUID PK DEFAULT gen_random_uuid()), `recipient_member_id` (UUID NOT NULL), `type` (VARCHAR(50) NOT NULL), `title` (VARCHAR(500) NOT NULL), `body` (TEXT nullable), `reference_entity_type` (VARCHAR(20) nullable), `reference_entity_id` (UUID nullable), `reference_project_id` (UUID nullable), `is_read` (BOOLEAN NOT NULL DEFAULT false), `tenant_id` (VARCHAR(255)), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). Indexes: `idx_notifications_unread (recipient_member_id, is_read, created_at DESC)`, `idx_notifications_list (recipient_member_id, created_at DESC)`, `idx_notifications_tenant (tenant_id) WHERE tenant_id IS NOT NULL`. RLS policy. Pattern: follow `V15__create_comments.sql`. |
| 61.2 | Create V17 tenant migration for notification_preferences table | 61A | | `db/migration/tenant/V17__create_notification_preferences.sql`. Table: `notification_preferences` with columns per Section 11.2.3: `id` (UUID PK DEFAULT gen_random_uuid()), `member_id` (UUID NOT NULL), `notification_type` (VARCHAR(50) NOT NULL), `in_app_enabled` (BOOLEAN NOT NULL DEFAULT true), `email_enabled` (BOOLEAN NOT NULL DEFAULT false), `tenant_id` (VARCHAR(255)). Constraint: `UNIQUE (member_id, notification_type, tenant_id)`. Indexes: `idx_notif_prefs_member (member_id)`, `idx_notif_prefs_tenant (tenant_id) WHERE tenant_id IS NOT NULL`. RLS policy. Pattern: follow `V16__create_notifications.sql`. |
| 61.3 | Create Notification entity | 61A | | `notification/Notification.java` — JPA entity mapped to `notifications`. Fields per Section 11.2.2. Annotations: `@FilterDef`/`@Filter` for `tenantFilter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Constructor: `Notification(UUID recipientMemberId, String type, String title, String body, String referenceEntityType, UUID referenceEntityId, UUID referenceProjectId)`. Method: `markAsRead()` — sets `isRead = true`. No `updatedAt` column. Pattern: follow `comment/Comment.java`. |
| 61.4 | Create NotificationRepository | 61A | | `notification/NotificationRepository.java` — extends `JpaRepository<Notification, UUID>`. Methods per Section 11.9.3: `findOneById(UUID id)` (JPQL — critical), `Page<Notification> findByRecipientMemberId(UUID memberId, Pageable)` (ORDER BY createdAt DESC), `Page<Notification> findUnreadByRecipientMemberId(UUID memberId, Pageable)`, `long countUnreadByRecipientMemberId(UUID memberId)`, `@Modifying void markAllAsRead(UUID memberId)` (bulk UPDATE SET is_read = true WHERE recipient_member_id = :memberId AND is_read = false). Pattern: follow `comment/CommentRepository.java`. |
| 61.5 | Create NotificationPreference entity and repository | 61A | | `notification/NotificationPreference.java` — JPA entity mapped to `notification_preferences`. Fields per Section 11.2.3. Implements `TenantAware`. Constructor: `NotificationPreference(UUID memberId, String notificationType, boolean inAppEnabled, boolean emailEnabled)`. `notification/NotificationPreferenceRepository.java` — `JpaRepository<NotificationPreference, UUID>`. Methods: `Optional<NotificationPreference> findByMemberIdAndNotificationType(UUID memberId, String type)` (JPQL), `List<NotificationPreference> findByMemberId(UUID memberId)` (JPQL). Pattern: follow `comment/Comment.java` for entity, `comment/CommentRepository.java` for repository. |
| 61.6 | Create NotificationService — create and query methods | 61A | | `notification/NotificationService.java`. `@Service`. Constructor injection of `NotificationRepository`, `NotificationPreferenceRepository`. Methods: `createNotification(UUID recipientMemberId, String type, String title, String body, String refEntityType, UUID refEntityId, UUID refProjectId)` — creates and saves Notification, `@Transactional(propagation = REQUIRES_NEW)`. `listNotifications(UUID memberId, boolean unreadOnly, Pageable)` — delegates to repo. `getUnreadCount(UUID memberId)` — delegates to `countUnreadByRecipientMemberId`. `markAsRead(UUID notificationId, UUID memberId)` — loads notification, verifies recipient matches, sets isRead, `@Transactional`. `markAllAsRead(UUID memberId)` — calls repo `markAllAsRead`, `@Transactional`. `dismissNotification(UUID notificationId, UUID memberId)` — loads, verifies recipient, hard-deletes, `@Transactional`. Pattern: follow `comment/CommentService.java`. |
| 61.7 | Add event publication to TaskService | 61B | | Modify `task/TaskService.java`. Inject `ApplicationEventPublisher`. In `updateTask()`: if `assigneeId` changed, publish `TaskAssignedEvent`. If `status` changed, publish `TaskStatusChangedEvent`. In `claimTask()`: publish `TaskClaimedEvent` with `previousAssigneeId`. Events published AFTER `auditService.log()` but still within the transaction (dual-write pattern per Section 11.6.3). Resolve `actorName` from `MemberRepository` or `RequestScopes`. Resolve `tenantId` from `RequestScopes.TENANT_ID`. Pattern: see Section 11.6.3 code example. |
| 61.8 | Add event publication to DocumentService and ProjectMemberService | 61B | | Modify `document/DocumentService.java`. Inject `ApplicationEventPublisher`. In `confirmUpload()` (or `createDocument()`): publish `DocumentUploadedEvent` with `documentName`. Modify `member/ProjectMemberService.java`. Inject `ApplicationEventPublisher`. In `addMemberToProject()`: publish `MemberAddedToProjectEvent` with `addedMemberId` and `projectName`. Both use dual-write pattern alongside existing `auditService.log()`. |
| 61.9 | Create NotificationEventHandler | 61B | | `notification/NotificationEventHandler.java`. `@Component`. Constructor injection of `NotificationService`. Methods with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`: `onCommentCreated(CommentCreatedEvent)`, `onTaskAssigned(TaskAssignedEvent)`, `onTaskClaimed(TaskClaimedEvent)`, `onTaskStatusChanged(TaskStatusChangedEvent)`, `onDocumentUploaded(DocumentUploadedEvent)`, `onMemberAddedToProject(MemberAddedToProjectEvent)`. Each method: (1) calls `handleInTenantScope(event.tenantId(), ...)` to re-bind ScopedValue, (2) wraps in try-catch logging errors at WARN, (3) delegates to corresponding `notificationService.handle*()` method. See Section 11.6.4 for full code. The handler itself is NOT `@Transactional` — NotificationService methods handle their own transactions with `REQUIRES_NEW`. |
| 61.10 | Add handleInTenantScope helper to NotificationEventHandler | 61B | | Private method `handleInTenantScope(String tenantId, Runnable action)` in `NotificationEventHandler`. If tenantId is non-null: `ScopedValue.where(RequestScopes.TENANT_ID, tenantId).run(action)`. Else: `action.run()`. This re-binds the tenant ScopedValue that was lost after the original transaction committed. See Section 11.6.4 for code. |
| 61.11 | Add event publication verification tests | 61B | | `event/EventPublicationTest.java`. ~5 tests using `@RecordApplicationEvents` (Spring Framework 6+): verify `TaskAssignedEvent` published when task is assigned, verify `TaskClaimedEvent` published when task is claimed, verify `DocumentUploadedEvent` published when document is confirmed, verify `MemberAddedToProjectEvent` published when member added to project, verify no event published on failed operation (rollback). Pattern: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. |
| 61.12 | Implement NotificationService fan-out handle* methods | 61C | | Add to `notification/NotificationService.java`: `handleCommentCreated(CommentCreatedEvent)` — determine recipients: if targetEntityType is TASK, get task assignee + all previous commenters on the entity (via `commentRepository.findDistinctAuthorsByEntity()`); if DOCUMENT, get document uploader + previous commenters. Remove the comment author from recipients. For each recipient: check `NotificationPreference`, create notification with title template from Section 11.3.3. `handleTaskAssigned(TaskAssignedEvent)` — notify assignee if not the actor. `handleTaskClaimed(TaskClaimedEvent)` — notify previous assignee (if any) + project leads. `handleTaskStatusChanged(TaskStatusChangedEvent)` — notify task assignee if not the actor. `handleDocumentUploaded(DocumentUploadedEvent)` — notify all project members (excluding uploader) via `projectMemberRepository`. `handleMemberAddedToProject(MemberAddedToProjectEvent)` — notify the added member. All methods `@Transactional(propagation = REQUIRES_NEW)`. |
| 61.13 | Add fan-out dependencies to NotificationService | 61C | | Add constructor dependencies to `NotificationService`: `CommentRepository` (for `findDistinctAuthorsByEntity`), `TaskRepository` (for loading task assignee), `DocumentRepository` (for loading document uploader), `ProjectMemberRepository` (for all project members), `MemberRepository` (for email lookup in future). These are needed by the `handle*` methods to determine notification recipients. |
| 61.14 | Add NotificationEventHandler integration tests | 61C | | `notification/NotificationEventHandlerIntegrationTest.java`. ~8 tests: comment created on task → assignee + prior commenters notified (excluding author), comment created on task with no assignee → only prior commenters, task assigned → assignee notified, task claimed → previous assignee notified, task status changed → assignee notified if not actor, document uploaded → all project members notified (excluding uploader), member added to project → added member notified, actor excluded from all notifications. Seed: provision tenant, sync 3 members, create project, add members, create tasks. Trigger actions, then verify `notificationRepository` contains expected rows. |
| 61.15 | Add notification preference opt-out test | 61C | | ~2 tests within event handler test file: member with `COMMENT_ADDED` disabled does NOT receive comment notification, member with default preferences (no row) DOES receive notification (opt-out model). |
| 61.16 | Add notification tenant isolation tests | 61C | | ~2 tests: notification created in tenant A is invisible in tenant B, notification query by recipientMemberId only returns notifications in the correct tenant scope. Pattern: follow `audit/AuditTenantIsolationTest.java`. |

### Key Files

**Slice 61A — Create:**
- `backend/src/main/resources/db/migration/tenant/V16__create_notifications.sql`
- `backend/src/main/resources/db/migration/tenant/V17__create_notification_preferences.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/Notification.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreference.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreferenceRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java`

**Slice 61A — Read for context:**
- `backend/src/main/resources/db/migration/tenant/V15__create_comments.sql` — Migration pattern (created in Epic 59A)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java` — Entity pattern (created in Epic 59A)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` — Interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` — Listener

**Slice 61B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/event/EventPublicationTest.java`

**Slice 61B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Add `ApplicationEventPublisher` + publishEvent() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` — Add `ApplicationEventPublisher` + publishEvent()
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberService.java` — Add `ApplicationEventPublisher` + publishEvent()

**Slice 61B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` — Sealed interface (from Epic 59A)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TaskAssignedEvent.java` — Event record (from Epic 59A)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — ScopedValue, TENANT_ID

**Slice 61C — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandlerIntegrationTest.java`

**Slice 61C — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` — Add handle* methods + constructor deps

**Slice 61C — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentRepository.java` — findDistinctAuthorsByEntity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskRepository.java` — findOneById for assignee lookup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberRepository.java` — findByProjectId for all project members
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentRepository.java` — findOneById for uploader lookup

### Architecture Decisions

- **`notification/` package**: New feature package for all notification entities, repositories, services, handler, and controller.
- **Three-slice decomposition**: 61A (migrations + entities + repositories + basic service) is the data layer. 61B (event publication in existing services + event handler skeleton) is the event infrastructure. 61C (fan-out logic + integration tests) is the business logic that ties it together. Each slice touches 5–8 files.
- **`REQUIRES_NEW` on NotificationService**: Per Section 11.6.4. Event handler runs AFTER_COMMIT, so no active transaction exists. NotificationService methods create their own transactions.
- **Handler NOT `@Transactional`**: Per architecture doc. Adding `@Transactional` to the handler would conflict with `AFTER_COMMIT` phase.
- **Tenant scope re-binding**: The original request's `ScopedValue` bindings are lost in the AFTER_COMMIT phase. Handler re-binds from event's `tenantId`.
- **Event records depend on Epic 59A**: The DomainEvent interface and event records are created in Epic 59A. If Epic 61 starts first, it should create them; whichever epic merges second updates the `permits` clause on the sealed interface.

---

## Epic 62: Notification API & Preferences Backend

**Goal**: Implement `NotificationController` with REST endpoints for listing, reading, dismissing notifications, and `NotificationPreferenceController` for viewing and updating notification preferences. All endpoints are self-scoped to the authenticated user.

**References**: `architecture/phase6.5-notifications-comments-activity.md` Sections 11.4.3, 11.4.4

**Dependencies**: Epic 61 (Notification entity, repository, and service must exist)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **62A** | 62.1–62.5 | NotificationController (list, unread count, mark read, mark all, dismiss), integration tests | |
| **62B** | 62.6–62.9 | NotificationPreferenceController (get, update), preference service methods, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 62.1 | Create NotificationController with response DTOs | 62A | | `notification/NotificationController.java`. `@RestController`, `@RequestMapping("/api/notifications")`. Inner DTOs: `NotificationResponse(UUID id, String type, String title, String body, String referenceEntityType, UUID referenceEntityId, UUID referenceProjectId, boolean isRead, Instant createdAt)`, `UnreadCountResponse(long count)`. Endpoints: `GET /` with params `unreadOnly` (boolean, default false), `page`, `size` — extracts `memberId` from `RequestScopes`, delegates to `notificationService.listNotifications()`. `GET /unread-count` — returns `UnreadCountResponse`. `PUT /{id}/read` — calls `notificationService.markAsRead(id, memberId)`, returns 204. `PUT /read-all` — calls `markAllAsRead(memberId)`, returns 204. `DELETE /{id}` — calls `dismissNotification(id, memberId)`, returns 204. All self-scoped (no ProjectAccessService — per ADR-023 pattern). Pattern: follow `task/TaskController.java` for RequestScopes usage. |
| 62.2 | Add SecurityConfig update for notification endpoints | 62A | | Modify `security/SecurityConfig.java` — ensure `/api/notifications/**` is covered by authenticated endpoint patterns. Likely already covered by `/api/**` pattern. Verify. |
| 62.3 | Add NotificationController integration tests | 62A | | `notification/NotificationControllerTest.java`. ~8 tests (MockMvc): GET /api/notifications returns paginated list, GET with unreadOnly=true filters correctly, GET /api/notifications/unread-count returns count, PUT /{id}/read marks notification as read (verify in subsequent GET), PUT /{id}/read for other user's notification returns 404, PUT /read-all marks all as read, DELETE /{id} removes notification, DELETE /{id} for other user's notification returns 404. Seed: provision tenant, sync 2 members, create notifications for each in `@BeforeEach`. Use MockMvc with `jwt()` mock. Pattern: follow `audit/AuditEventControllerTest.java`. |
| 62.4 | Add notification self-scoping test | 62A | | ~2 tests within controller test: member A cannot see member B's notifications via GET (even if they guess the ID), member A cannot mark member B's notification as read. Verifies the self-scoped query pattern (WHERE recipient_member_id = :currentMemberId). |
| 62.5 | Add notification pagination ordering test | 62A | | ~1 test: verify notifications are returned in descending created_at order (most recent first). |
| 62.6 | Add preference service methods to NotificationService | 62B | | Add to `notification/NotificationService.java`: `getPreferences(UUID memberId)` — loads all stored preferences, merges with default types to return a complete list. Define `NOTIFICATION_TYPES` constant: `List.of("TASK_ASSIGNED", "TASK_CLAIMED", "TASK_UPDATED", "COMMENT_ADDED", "DOCUMENT_SHARED", "MEMBER_INVITED")`. For each type, return stored preference if exists, else default (inAppEnabled=true, emailEnabled=false). `updatePreferences(UUID memberId, List<PreferenceUpdate> updates)` — for each update, upsert a `NotificationPreference` row. Use `findByMemberIdAndNotificationType()` + save (not raw SQL upsert, to keep JPA consistency). Return full preferences list. `@Transactional`. |
| 62.7 | Create NotificationPreferenceController | 62B | | `notification/NotificationPreferenceController.java`. `@RestController`, `@RequestMapping("/api/notifications/preferences")`. Inner DTOs: `PreferenceResponse(String notificationType, boolean inAppEnabled, boolean emailEnabled)`, `PreferencesListResponse(List<PreferenceResponse> preferences)`, `UpdatePreferencesRequest(List<PreferenceUpdate> preferences)`, `PreferenceUpdate(String notificationType, boolean inAppEnabled, boolean emailEnabled)`. Endpoints: `GET /` — returns all preferences with defaults merged. `PUT /` — accepts list of preference updates, returns updated full list. Self-scoped via `RequestScopes.MEMBER_ID`. Pattern: follow `NotificationController.java`. |
| 62.8 | Add NotificationPreferenceController integration tests | 62B | | `notification/NotificationPreferenceControllerTest.java`. ~6 tests: GET returns all 6 types with default values (no stored rows), PUT updates one type and GET reflects it, PUT with multiple types updates all, defaults are preserved for types not in PUT request, updating back to default values still works, emailEnabled can be toggled independently of inAppEnabled. Seed: provision tenant, sync member. Use MockMvc. |
| 62.9 | Add preference upsert behavior test | 62B | | ~2 tests: updating the same type twice is idempotent (second PUT overwrites first), UNIQUE constraint violation handled gracefully (no duplicate rows). |

### Key Files

**Slice 62A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/NotificationControllerTest.java`

**Slice 62A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` — Verify `/api/notifications/**` is authenticated

**Slice 62A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` — Service methods to call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationRepository.java` — Repository methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — MEMBER_ID
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Controller pattern reference

**Slice 62B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreferenceController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreferenceControllerTest.java`

**Slice 62B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` — Add getPreferences() and updatePreferences() methods, add NOTIFICATION_TYPES constant

**Slice 62B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreference.java` — Entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreferenceRepository.java` — Repository

### Architecture Decisions

- **Controllers in `notification/` package**: Both NotificationController and NotificationPreferenceController live in the same package as their entities. Follows feature-package convention.
- **Self-scoped endpoints**: No `ProjectAccessService` check. `WHERE recipient_member_id = :memberId` IS the authorization (ADR-023 pattern).
- **Merged defaults in GET preferences**: The GET endpoint always returns all 6 notification types with correct defaults, even if no preference rows exist. This simplifies the frontend (always has a complete list to render toggles).
- **Two-slice decomposition**: 62A (notification CRUD controller + tests) and 62B (preference controller + service enhancement + tests). Each touches ~3–5 files.

---

## Epic 63: Notification Frontend — Bell, Page & Preferences UI

**Goal**: Build the `NotificationBell` component for the app header (with polling and dropdown), the full notifications page at `/notifications`, and the notification preferences settings page. Includes server actions and frontend tests.

**References**: `architecture/phase6.5-notifications-comments-activity.md` Sections 11.3.4, 11.9.6, [ADR-038](../adr/ADR-038-polling-for-notification-delivery.md)

**Dependencies**: Epic 62 (Notification API endpoints must exist)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **63A** | 63.1–63.7 | Server actions, NotificationBell + dropdown, header integration, polling hook, frontend tests | |
| **63B** | 63.8–63.12 | Notifications full page, preferences page, sidebar nav links, frontend tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 63.1 | Create notification server actions and types | 63A | | `lib/actions/notifications.ts`. Types: `Notification { id: string; type: string; title: string; body: string | null; referenceEntityType: string | null; referenceEntityId: string | null; referenceProjectId: string | null; isRead: boolean; createdAt: string; }`, `NotificationsResponse { content: Notification[]; page: PageInfo; }`, `UnreadCountResponse { count: number; }`. Actions: `fetchNotifications(unreadOnly?: boolean, page?: number)` — GET, `fetchUnreadCount()` — GET, `markNotificationRead(id: string)` — PUT, `markAllNotificationsRead()` — PUT, `dismissNotification(id: string)` — DELETE. Pattern: follow `lib/actions/comments.ts`. |
| 63.2 | Create useNotificationPolling custom hook | 63A | | `hooks/use-notification-polling.ts`. `"use client"` hook. Uses `useState` for unread count and `useEffect` + `setInterval(30_000)` for polling. Calls `fetchUnreadCount()` via fetch (not server action — client-side polling needs direct API call via `lib/api.ts` client-side variant or a dedicated route handler). Cleanup interval on unmount. Returns `{ unreadCount, refetch }`. Per ADR-038. Consider using `fetch` directly with the API URL since this runs client-side. |
| 63.3 | Create NotificationBell client component | 63A | | `components/notifications/notification-bell.tsx`. `"use client"`. Uses `useNotificationPolling` hook. Renders: bell icon (from `lucide-react`) with unread count badge (olive/indigo pill). On click: toggles a `Popover` (Shadcn) showing `NotificationDropdown`. Badge hidden when count is 0. Badge shows count, capped at "99+" for large numbers. |
| 63.4 | Create NotificationDropdown component | 63A | | `components/notifications/notification-dropdown.tsx`. `"use client"`. Props: `orgSlug: string`, `onClose: () => void`. Fetches recent 10 notifications on open. Renders: header with "Notifications" title + "Mark all as read" button, list of `NotificationItem` components, footer with "View all" link to `/org/{slug}/notifications`. Loading state with skeleton. Empty state: "No notifications". |
| 63.5 | Create NotificationItem component | 63A | | `components/notifications/notification-item.tsx`. `"use client"`. Props: `notification: Notification`, `orgSlug: string`, `onRead: () => void`. Renders: type icon (based on notification.type — use lucide icons: `CheckSquare` for TASK_*, `MessageSquare` for COMMENT_*, `FileText` for DOCUMENT_*, `UserPlus` for MEMBER_*), title, relative timestamp (use `formatDate` from `lib/format.ts`), unread indicator dot (blue dot for unread). On click: calls `markNotificationRead`, navigates to deep link URL constructed from `referenceProjectId` + `referenceEntityType` + `referenceEntityId` (e.g., `/org/{slug}/projects/{refProjectId}/tasks?selected={refEntityId}` for TASK type). |
| 63.6 | Integrate NotificationBell into app header | 63A | | Modify `app/(app)/org/[slug]/layout.tsx`. Add `<NotificationBell orgSlug={slug} />` to the header area, between the `PlanBadge` and `UserButton` components. Pass `orgSlug` as prop. The bell appears for all authenticated users. |
| 63.7 | Add NotificationBell frontend tests | 63A | | `__tests__/notifications/notification-bell.test.tsx`. ~3 tests: renders bell icon without badge when unread count is 0, renders badge with count when unread > 0, clicking bell opens dropdown. Mock `useNotificationPolling` or mock fetch calls. `afterEach(() => cleanup())`. `__tests__/notifications/notification-item.test.tsx`. ~2 tests: renders unread indicator for unread notification, clicking navigates and marks as read. Total: ~5 tests. |
| 63.8 | Create notifications full page | 63B | | `app/(app)/org/[slug]/notifications/page.tsx`. Server component. Props: params (slug). Fetches initial notifications page from API. Renders: page header "Notifications" with "Mark all as read" button, filter toggle (All / Unread), paginated list of `NotificationItem` components (full-width variant), "Load more" button for pagination. Uses same `NotificationItem` component but with wider layout. |
| 63.9 | Create notification preferences page | 63B | | `app/(app)/org/[slug]/settings/notifications/page.tsx`. Server component that fetches current preferences. Renders `NotificationPreferencesForm` client component. `components/notifications/notification-preferences-form.tsx` (`"use client"`): table/list of notification types with toggle switches for in-app and email per type. Email toggles shown but display "(coming soon)" tooltip since email is stubbed. Save button calls `updateNotificationPreferences` server action. Types displayed with human-readable labels: "Task Assigned", "Task Claimed", "Task Updated", "Comment Added", "Document Shared", "Member Invited". |
| 63.10 | Create notification preferences server actions | 63B | | Add to `lib/actions/notifications.ts`: `fetchNotificationPreferences()` — GET `/api/notifications/preferences`, `updateNotificationPreferences(preferences: PreferenceUpdate[])` — PUT `/api/notifications/preferences`. Types: `NotificationPreference { notificationType: string; inAppEnabled: boolean; emailEnabled: boolean; }`, `PreferencesResponse { preferences: NotificationPreference[]; }`. |
| 63.11 | Add sidebar navigation links | 63B | | Modify `lib/nav-items.ts` — add "Notifications" item with bell icon linking to `/org/{slug}/notifications`. Modify `components/desktop-sidebar.tsx` if the nav-items change requires structural updates. Add "Notification Settings" as a sub-item under Settings linking to `/org/{slug}/settings/notifications`. |
| 63.12 | Add notifications page and preferences frontend tests | 63B | | `__tests__/notifications/notifications-page.test.tsx`. ~3 tests: renders notification list, filter toggle switches between all/unread, mark all as read button works. `__tests__/notifications/notification-preferences.test.tsx`. ~2 tests: renders all 6 notification types with toggles, toggling a switch and saving updates preferences. Total: ~5 tests. |

### Key Files

**Slice 63A — Create:**
- `frontend/lib/actions/notifications.ts`
- `frontend/hooks/use-notification-polling.ts`
- `frontend/components/notifications/notification-bell.tsx`
- `frontend/components/notifications/notification-dropdown.tsx`
- `frontend/components/notifications/notification-item.tsx`
- `frontend/__tests__/notifications/notification-bell.test.tsx`
- `frontend/__tests__/notifications/notification-item.test.tsx`

**Slice 63A — Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` — Add NotificationBell to header

**Slice 63A — Read for context:**
- `frontend/lib/api.ts` — API client
- `frontend/lib/format.ts` — formatDate utility
- `frontend/components/ui/popover.tsx` — Shadcn Popover for dropdown
- `frontend/components/ui/badge.tsx` — Badge for unread count
- `frontend/components/billing/plan-badge.tsx` — Header component placement reference

**Slice 63B — Create:**
- `frontend/app/(app)/org/[slug]/notifications/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/notifications/page.tsx`
- `frontend/components/notifications/notification-preferences-form.tsx`
- `frontend/__tests__/notifications/notifications-page.test.tsx`
- `frontend/__tests__/notifications/notification-preferences.test.tsx`

**Slice 63B — Modify:**
- `frontend/lib/nav-items.ts` — Add Notifications link
- `frontend/components/desktop-sidebar.tsx` — Update if needed for new nav items
- `frontend/lib/actions/notifications.ts` — Add preference actions

**Slice 63B — Read for context:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — Settings page structure
- `frontend/components/ui/switch.tsx` — Toggle switch component for preferences

### Architecture Decisions

- **`components/notifications/` directory**: New feature component directory following existing pattern.
- **Client-side polling for unread count**: The `useNotificationPolling` hook uses client-side `fetch` + `setInterval` since server actions cannot be called on a timer from the client. The hook calls the backend API directly.
- **Popover for dropdown**: Using Shadcn `Popover` (not `DropdownMenu`) because the content includes a scrollable list with action buttons — Popover supports arbitrary content.
- **Two-slice decomposition**: 63A (bell + dropdown + header integration) and 63B (full page + preferences + nav). Each touches ~7–8 files.

---

## Epic 64: Activity Feed Backend — Service, Formatter & API

**Goal**: Implement `ActivityService` that queries `audit_events` by `project_id` (using the expression index from V15), `ActivityMessageFormatter` that maps `(eventType, entityType)` pairs to human-readable templates, `ActivityController` with the REST endpoint, and audit event enrichment (adding `project_id` to existing services' audit details). Includes integration and unit tests.

**References**: [ADR-035](../adr/ADR-035-activity-feed-direct-audit-query.md), `architecture/phase6.5-notifications-comments-activity.md` Sections 11.3.2, 11.4.2, 11.9.4, 11.9.5

**Dependencies**: Epic 59 (V15 migration that creates `idx_audit_project` expression index)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **64A** | 64.1–64.5 | Audit event enrichment (project_id in existing services), AuditEventRepository.findByProjectId() method, ActivityMessageFormatter | |
| **64B** | 64.6–64.10 | ActivityService, ActivityController, integration tests, formatter unit tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 64.1 | Enrich existing audit events with project_id | 64A | | Modify services to add `"project_id"` to their `auditService.log()` details map. Per Section 11.9.5 table: (1) `DocumentService` — add `"project_id", document.getProjectId()` to `document.created`, `document.uploaded`, `document.deleted`, `document.accessed` audit details. (2) `TimeEntryService` — add `"project_id", task.getProjectId()` to `time_entry.created`, `time_entry.updated`, `time_entry.deleted` (requires loading the task to get projectId, or passing it through). (3) `TaskService` — add `"project_id", task.getProjectId()` to `task.updated`, `task.claimed`, `task.released`. Each is a one-line change adding a key to an existing `Map.of(...)` or `Map.ofEntries(...)`. Update existing audit tests to assert the new key. |
| 64.2 | Add findByProjectId method to AuditEventRepository | 64A | | Modify `audit/AuditEventRepository.java`. Add native query method `Page<AuditEvent> findByProjectId(String projectId, String entityType, Instant since, Pageable pageable)` per Section 11.9.4. Uses `WHERE (details->>'project_id') = CAST(:projectId AS TEXT)` with nullable filters for entityType and since. Uses `CAST(:since AS TIMESTAMPTZ) IS NULL` pattern for nullable Instant params (per lesson in MEMORY.md). Count query included for pagination. |
| 64.3 | Create ActivityMessageFormatter | 64A | | `activity/ActivityMessageFormatter.java`. `@Component`. Method: `ActivityItem format(AuditEvent event, Map<UUID, Member> actorMap)`. Maps `(eventType, entityType)` to templates per Section 11.3.2 table. Resolves `{actor}` from actorMap using `event.getActorId()`, fallback to `actorId.toString()`. Resolves entity names from `event.getDetails()` map: `details.get("title")` for tasks, `details.get("file_name")` for documents, `details.get("name")` for other entities. Special case: `task.updated` inspects details for `assignee_id` and `status` keys to produce specific messages. Unknown event types produce generic `"{actor} performed {eventType} on {entityType}"`. Returns `ActivityItem` record: `UUID id, String message, String actorName, String actorAvatarUrl, String entityType, UUID entityId, String entityName, String eventType, Instant occurredAt`. |
| 64.4 | Create ActivityItem record | 64A | | `activity/ActivityItem.java` — Java record: `ActivityItem(UUID id, String message, String actorName, String actorAvatarUrl, String entityType, UUID entityId, String entityName, String eventType, Instant occurredAt)`. Used as the response DTO for the activity feed endpoint. |
| 64.5 | Add ActivityMessageFormatter unit tests | 64A | | `activity/ActivityMessageFormatterTest.java`. ~12 unit tests covering all template mappings: task.created, task.updated (with assignee_id), task.updated (with status), task.updated (generic), task.claimed, task.released, document.uploaded, document.updated, document.deleted, comment.created, time_entry.created (format duration), project_member.added, unknown event type (fallback). Construct mock AuditEvent objects with appropriate details maps. No Spring context needed — pure unit test. |
| 64.6 | Create ActivityService | 64B | | `activity/ActivityService.java`. `@Service`. Constructor injection of `AuditEventRepository`, `MemberRepository`, `ProjectAccessService`, `ActivityMessageFormatter`. Method: `Page<ActivityItem> getProjectActivity(UUID projectId, String entityType, Instant since, Pageable pageable, UUID memberId, String orgRole)`. Flow per Section 11.9.4: (1) `projectAccessService.requireViewAccess()`, (2) query audit events via `auditEventRepository.findByProjectId()`, (3) batch-resolve actor names from `memberRepository.findAllById()`, (4) format each event via `activityMessageFormatter.format()`, (5) return `PageImpl`. Convert entityType parameter to lowercase for query (API accepts uppercase, DB stores lowercase). `@Transactional(readOnly = true)`. |
| 64.7 | Create ActivityController | 64B | | `activity/ActivityController.java`. `@RestController`, `@RequestMapping("/api/projects/{projectId}/activity")`. `GET /` with params: `page` (default 0), `size` (default 20, max 50), `entityType` (optional), `since` (optional ISO 8601). Extracts `memberId` and `orgRole` from `RequestScopes`. Delegates to `activityService.getProjectActivity()`. Returns `Page<ActivityItem>`. Pattern: follow `comment/CommentController.java`. |
| 64.8 | Add ActivityController integration tests | 64B | | `activity/ActivityControllerTest.java`. ~6 tests (MockMvc): GET returns paginated activity items, filter by entityType, filter by since timestamp, empty project returns empty list, non-member returns 404 (access denied), response format matches expected JSON shape (message, actorName, actorAvatarUrl, entityType, entityId, eventType, occurredAt). Seed: provision tenant, sync members, create project, add members, perform several domain operations (create task, claim task, create comment) to generate audit events. |
| 64.9 | Add ActivityService integration tests | 64B | | `activity/ActivityServiceIntegrationTest.java`. ~4 tests: query returns events ordered by occurred_at DESC, batch actor name resolution works (actor name appears in formatted message), unknown actor falls back to UUID string, expression index is used (verify via EXPLAIN ANALYZE or just verify correct results with project_id filter). |
| 64.10 | Add audit event enrichment regression tests | 64B | | ~3 tests within existing service integration tests or a new file: verify `project_id` appears in audit event details for document.created, time_entry.created, and task.updated. These verify the enrichment from task 64.1 is correct. Can be added as assertions in existing tests or as new targeted tests. |

### Key Files

**Slice 64A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityItem.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatterTest.java`

**Slice 64A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` — Add `project_id` to audit details
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — Add `project_id` to audit details
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Add `project_id` to audit details
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — Add `findByProjectId()` native query

**Slice 64A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java` — Entity with details map
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` — Builder pattern for audit details
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java` — Member entity (name, avatarUrl)

**Slice 64B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityControllerTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityServiceIntegrationTest.java`

**Slice 64B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectAccessService.java` — requireViewAccess()
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` — findAllById() for batch resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentController.java` — Controller pattern reference

### Architecture Decisions

- **`activity/` package**: New feature package for activity feed service, formatter, and controller.
- **No separate entity or table**: Activity feed reads directly from `audit_events` per ADR-035. No new entity class.
- **Audit enrichment as prerequisite**: Task 64.1 must complete before activity feed integration tests can verify complete project timelines.
- **Two-slice decomposition**: 64A (audit enrichment + repository method + formatter + unit tests) modifies existing files and creates the formatting layer. 64B (service + controller + integration tests) creates the API layer. Each touches 4–7 files.
- **`CAST(:since AS TIMESTAMPTZ) IS NULL` pattern**: Per lesson in MEMORY.md for nullable Instant params in native queries.

---

## Epic 65: Activity Feed Frontend — Activity Tab & Components

**Goal**: Add an "Activity" tab to the project detail page, implement `ActivityFeed`, `ActivityItem`, and `ActivityFilter` components that display the project activity timeline with entity type filter chips and "Load more" pagination.

**References**: `architecture/phase6.5-notifications-comments-activity.md` Sections 11.9.6

**Dependencies**: Epic 64 (Activity feed API must exist)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **65A** | 65.1–65.7 | Server action, ActivityFeed server component, ActivityItem, ActivityFilter, Activity tab integration, frontend tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 65.1 | Create activity feed server action and types | 65A | | `lib/actions/activity.ts`. Types: `ActivityItem { id: string; message: string; actorName: string; actorAvatarUrl: string | null; entityType: string; entityId: string; entityName: string; eventType: string; occurredAt: string; }`, `ActivityResponse { content: ActivityItem[]; page: PageInfo; }`. Action: `fetchProjectActivity(projectId: string, entityType?: string, since?: string, page?: number, size?: number)` — GET `/api/projects/{projectId}/activity` with query params. Pattern: follow `lib/actions/comments.ts`. |
| 65.2 | Create ActivityFeed server component | 65A | | `components/activity/activity-feed.tsx`. Server component. Props: `projectId: string`, `orgSlug: string`. Fetches initial activity page. Renders: `ActivityFilter` (client) + list of `ActivityItem` components + "Load more" button (client component for pagination). Shows "No activity yet" empty state. Activity items ordered by occurredAt DESC (most recent first). |
| 65.3 | Create ActivityItem component | 65A | | `components/activity/activity-item.tsx`. Props: `item: ActivityItem`. Renders: avatar circle with initials (fallback if no avatarUrl), actor name, formatted message (pre-formatted by backend), relative timestamp. Olive styling. Compact layout — one line per item with subtle separator. Entity type icon next to the message (lucide icons: `CheckSquare` for task, `FileText` for document, `MessageSquare` for comment, `Clock` for time_entry, `Users` for project_member). |
| 65.4 | Create ActivityFilter client component | 65A | | `components/activity/activity-filter.tsx`. `"use client"`. Props: `onFilterChange: (entityType: string | null) => void`, `currentFilter: string | null`. Renders: horizontal row of filter chips/buttons: All, Tasks, Documents, Comments, Members, Time. Active chip highlighted with indigo accent. Clicking a chip triggers refetch with the selected entityType. |
| 65.5 | Add Activity tab to project detail page | 65A | | Modify `app/(app)/org/[slug]/projects/[id]/page.tsx`. Add "Activity" tab to the existing tab navigation (alongside Overview, Tasks, Documents, Time, Members). When "Activity" tab is selected, render `<ActivityFeed projectId={id} orgSlug={slug} />`. The Activity tab appears last in the tab list. Pattern: follow how the Time tab or Members tab was added in Phase 5. |
| 65.6 | Create activity-actions.ts for client-side pagination | 65A | | `app/(app)/org/[slug]/projects/[id]/activity-actions.ts`. Server action: `loadMoreActivity(projectId: string, entityType?: string, page?: number)` — fetches next page of activity items. Used by the "Load more" button in the ActivityFeed component. |
| 65.7 | Add activity feed frontend tests | 65A | | `__tests__/activity/activity-feed.test.tsx`. ~3 tests: renders activity items with correct format, shows empty state when no activity, filter chips filter by entity type. Mock server actions. `afterEach(() => cleanup())`. |

### Key Files

**Slice 65A — Create:**
- `frontend/lib/actions/activity.ts`
- `frontend/components/activity/activity-feed.tsx`
- `frontend/components/activity/activity-item.tsx`
- `frontend/components/activity/activity-filter.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/activity-actions.ts`
- `frontend/__tests__/activity/activity-feed.test.tsx`

**Slice 65A — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add Activity tab

**Slice 65A — Read for context:**
- `frontend/lib/api.ts` — API client
- `frontend/lib/format.ts` — formatDate for relative timestamps
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Current tab structure
- `frontend/components/tasks/` — Component structure reference
- `frontend/app/(app)/org/[slug]/projects/[id]/time-summary-actions.ts` — Server action pattern for project sub-pages

### Architecture Decisions

- **`components/activity/` directory**: New feature component directory.
- **Single-slice epic**: 7 tasks but all tightly coupled — the components, actions, tab integration, and tests form one coherent unit. Each individual piece is small (~50–100 LOC).
- **Backend pre-formats messages**: The frontend renders `item.message` directly. No message formatting logic in the frontend.
- **"Load more" pagination**: Per architecture doc. Not infinite scroll. Client component handles page tracking and appends results.

---

## Epic 66: Email Notification Stubs — Channel Abstraction & Templates

**Goal**: Implement the `NotificationChannel` interface, `InAppNotificationChannel` (no-op), `EmailNotificationChannel` (stub that logs), `NotificationDispatcher` that routes to channels based on preferences, `EmailTemplate` enum with template definitions, and `TemplateRenderer`. Includes unit tests.

**References**: `architecture/phase6.5-notifications-comments-activity.md` Sections 11.7.1–11.7.6

**Dependencies**: Epic 61 (Notification entity and NotificationPreferenceRepository must exist)

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **66A** | 66.1–66.8 | NotificationChannel interface, InAppNotificationChannel, EmailNotificationChannel (stub), NotificationDispatcher, EmailTemplate enum, TemplateRenderer, unit tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 66.1 | Create NotificationChannel interface | 66A | | `notification/channel/NotificationChannel.java`. Interface per Section 11.7.1: `String channelId()`, `void deliver(Notification notification, String recipientEmail)`, `boolean isEnabled()`. |
| 66.2 | Create InAppNotificationChannel | 66A | | `notification/channel/InAppNotificationChannel.java`. `@Component`. No-op `deliver()` — notification row already created by NotificationService. `channelId()` returns `"in-app"`. `isEnabled()` returns `true`. See Section 11.7.2. |
| 66.3 | Create EmailNotificationChannel stub | 66A | | `notification/channel/EmailNotificationChannel.java`. `@Component`, `@Profile({"local", "dev"})`. Logs email content at INFO level. Constructor injection of `TemplateRenderer`. `deliver()`: resolves `EmailTemplate` from notification type, renders subject and body, logs `[EMAIL STUB] To: {}, Subject: {}, Body: {}`. `channelId()` = `"email"`. `isEnabled()` = `true`. NOT registered in prod profile. See Section 11.7.3. |
| 66.4 | Create EmailTemplate enum | 66A | | `notification/template/EmailTemplate.java`. Enum per Section 11.7.5: `TASK_ASSIGNED`, `TASK_CLAIMED`, `TASK_UPDATED`, `COMMENT_ADDED`, `DOCUMENT_SHARED`, `MEMBER_INVITED`, `DEFAULT`. Each has `subjectTemplate` and `bodyTemplate` strings. Methods: `static fromNotificationType(String type)` (try valueOf, fallback to DEFAULT), `renderSubject(Notification)`, `renderBody(Notification)` (uses `String.format` with notification title). |
| 66.5 | Create TemplateRenderer | 66A | | `notification/template/TemplateRenderer.java`. `@Component`. Placeholder for future Thymeleaf integration. Currently empty — EmailTemplate handles its own rendering. See Section 11.7.5. |
| 66.6 | Create NotificationDispatcher | 66A | | `notification/channel/NotificationDispatcher.java`. `@Component`. Constructor injection of `List<NotificationChannel>` (Spring auto-collects), `NotificationPreferenceRepository`. Filters enabled channels into a `Map<String, NotificationChannel>`. Method: `dispatch(Notification notification, String recipientEmail)` — checks preferences: if in-app enabled (default true), dispatch to "in-app" channel. If email enabled (default false), dispatch to "email" channel. Each channel dispatch wrapped in try-catch. See Section 11.7.4. |
| 66.7 | Add NotificationDispatcher unit tests | 66A | | `notification/channel/NotificationDispatcherTest.java`. ~5 unit tests: dispatches to in-app when preference says enabled (default), skips in-app when explicitly disabled, dispatches to email when preference says emailEnabled=true, skips email by default (emailEnabled=false), handles channel exception without propagating. Mock `NotificationChannel` and `NotificationPreferenceRepository`. |
| 66.8 | Add EmailTemplate unit tests | 66A | | `notification/template/EmailTemplateTest.java`. ~4 unit tests: fromNotificationType returns correct template for each known type, fromNotificationType returns DEFAULT for unknown type, renderSubject returns expected subject, renderBody includes notification title in body. Pure unit tests, no Spring context. |

### Key Files

**Slice 66A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/NotificationChannel.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/InAppNotificationChannel.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/EmailNotificationChannel.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/NotificationDispatcher.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/template/EmailTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/template/TemplateRenderer.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/channel/NotificationDispatcherTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/template/EmailTemplateTest.java`

**Slice 66A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/Notification.java` — Notification entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreferenceRepository.java` — findByMemberIdAndNotificationType

### Architecture Decisions

- **`notification/channel/` sub-package**: Channel abstraction and implementations grouped under `notification/`. The `template/` sub-package holds email template definitions.
- **Single-slice epic**: 8 tasks but all are small classes (most under 50 LOC). The channel interface, two implementations, dispatcher, template enum, renderer, and two test files form one coherent unit.
- **InAppNotificationChannel is a no-op**: It exists for architectural symmetry in the NotificationDispatcher. The actual in-app notification write happens in NotificationService.
- **EmailNotificationChannel profile-gated**: Only exists in `local`/`dev`. Not registered in prod. Future SES integration creates a `@Profile("prod")` implementation.
- **TemplateRenderer placeholder**: Currently empty. Exists as the injection point for future Thymeleaf or other template engine integration.

---

## Test Count Summary

| Epic | Backend Integration | Backend Unit | Frontend | Total |
|------|-------------------|-------------|----------|-------|
| 59 | ~18 | 0 | 0 | ~18 |
| 60 | 0 | 0 | ~10 | ~10 |
| 61 | ~17 | 0 | 0 | ~17 |
| 62 | ~16 | 0 | 0 | ~16 |
| 63 | 0 | 0 | ~10 | ~10 |
| 64 | ~13 | ~12 | 0 | ~25 |
| 65 | 0 | 0 | ~3 | ~3 |
| 66 | 0 | ~9 | 0 | ~9 |
| **Total** | **~64** | **~21** | **~23** | **~108** |

Combined with existing suite: ~465+ backend, ~225+ frontend.
