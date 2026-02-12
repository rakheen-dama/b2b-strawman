You are a senior SaaS architect working on an existing multi‑tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema‑per‑tenant) tiers.
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org‑scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- An **audit event infrastructure** (Phase 6) that logs all domain mutations (`AuditEvent` entity with actor, action, entity type, JSONB details, tenant isolation).
- A roadmap that includes a future **customer portal** (Phase 7) with a read‑model schema and `PortalComment` entities projected from the core domain.

For **Phase 6.5**, I want to add the **communication and awareness layer** that is currently missing: comments, activity feeds, and notifications.

***

## Objective of Phase 6.5

Design and specify:

1. A **comments system** on tasks and documents, with a visibility flag that controls whether comments are internal‑only or shared with future portal customers.
2. A **project activity feed** that surfaces audit events as a human‑readable timeline.
3. An **in‑app notification system** that alerts users when things relevant to them happen.
4. **Email notification stubs** — the integration point for future email delivery (SES), with templates and user preferences, but no mandatory email provider wiring in this phase.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - Next.js 16 frontend with Shadcn UI.
- Do not introduce:
    - WebSocket/SSE for real‑time push (polling or page‑refresh is acceptable for now).
    - External message broker (no SQS, RabbitMQ, etc.) — use Spring `ApplicationEvent` for in‑process event propagation.
    - A separate microservice — everything stays in the existing backend deployable.
- The comment visibility flag (`internal` / `shared`) must be designed so Phase 7's portal can project `shared` comments into `PortalComment` read‑model entities without schema changes.

2. **Tenancy**

- Comments, notifications, and activity data follow the same tenant isolation model as existing entities:
    - Pro orgs: dedicated schema.
    - Starter orgs: `tenant_shared` schema with `tenant_id` column + Hibernate `@Filter` + RLS.
- All new entities must include Flyway migrations for both tenant and shared schemas.

3. **Permissions model**

- Comments:
    - Any project member can add a comment on a task or document within that project.
    - Only the comment author (or org admin/owner) can edit or delete a comment.
    - Setting a comment's visibility to `shared` (customer‑visible) should require at least a project‑lead or admin/owner role — regular members default to `internal`.
- Notifications:
    - Notifications are private to the recipient. No cross‑user visibility.
    - Users can mark notifications as read individually or in bulk.
- Activity feed:
    - Visible to all project members. Scoped by project.

4. **Out of scope for Phase 6.5**

- Real‑time push (WebSocket, SSE, or long‑polling) — future phase.
- Threaded/nested comment replies — keep comments flat for now. Include a `parent_id` field (nullable) in the schema to allow threading later without migration.
- Rich text or markdown rendering in comments — plain text body for now, but use a `TEXT` column so length isn't constrained.
- @‑mention parsing and notification targeting — future enhancement.
- Email delivery integration (SES wiring) — define the abstraction and templates, but actual sending is stubbed/logged.
- Comment reactions (emoji, thumbs up) — out of scope.

***

## What I want you to produce

Produce a **self‑contained markdown document** that can be merged into `ARCHITECTURE.md` as "Phase 6.5 — Notifications, Comments & Activity", plus ADRs for key decisions.

### 1. Comments system

Design a **Comment** entity and its surrounding infrastructure:

1. **Data model**

    - `Comment` entity:
        - `id` (UUID).
        - `tenant_id` (for shared‑schema isolation).
        - `entity_type` (enum: `TASK`, `DOCUMENT` — extensible for future entity types).
        - `entity_id` (UUID — the task or document being commented on).
        - `project_id` (UUID — denormalized for efficient project‑scoped queries).
        - `author_member_id` (UUID — references `members` table).
        - `body` (TEXT — plain text for now).
        - `visibility` (enum: `INTERNAL`, `SHARED` — `SHARED` means future‑portal‑visible).
        - `parent_id` (UUID, nullable — reserved for future threading, not used in Phase 6.5 logic).
        - `created_at`, `updated_at` timestamps.
    - Indexes:
        - `(entity_type, entity_id, created_at)` for listing comments on an entity.
        - `(project_id, created_at)` for project‑wide comment feeds.

2. **API endpoints**

    - `POST /api/projects/{projectId}/comments` — create a comment (body includes `entityType`, `entityId`, `body`, optionally `visibility`).
    - `GET /api/projects/{projectId}/comments?entityType=TASK&entityId={id}` — list comments on a specific entity, ordered by `created_at` ascending.
    - `PUT /api/projects/{projectId}/comments/{commentId}` — edit a comment (author or admin/owner only). Only `body` and `visibility` are editable.
    - `DELETE /api/projects/{projectId}/comments/{commentId}` — soft‑ or hard‑delete (decide in ADR).

    For each endpoint specify:
    - Auth requirement (valid Clerk JWT, project membership).
    - Tenant scoping.
    - Permission checks.
    - Request/response DTOs.

3. **Frontend**

    - **Comment list component**: displays comments on a task or document detail view. Shows author name, timestamp, body, visibility badge.
    - **Add comment form**: text input + submit. Visibility toggle (internal/shared) shown only for leads/admins.
    - **Edit/delete controls**: inline edit and delete for authorized users.
    - **Integration points**: Task detail page, Document detail page (or a shared `CommentSection` component).

4. **Audit integration**

    - Publish audit events for `COMMENT_CREATED`, `COMMENT_UPDATED`, `COMMENT_DELETED`, `COMMENT_VISIBILITY_CHANGED`.

### 2. Project activity feed

Design a **read‑only activity feed** that projects audit events into a human‑readable timeline:

1. **Data source**

    - The activity feed reads directly from the existing `audit_events` table (Phase 6). No separate storage.
    - Activity entries are filtered by `project_id` (available in audit event details or derivable from entity lookups).

2. **API endpoint**

    - `GET /api/projects/{projectId}/activity` — returns a paginated list of activity items.
    - Query params:
        - `page`, `size` (pagination).
        - `entityType` (optional filter: `PROJECT`, `TASK`, `DOCUMENT`, `COMMENT`, `MEMBER`, `TIME_ENTRY`).
        - `since` (optional ISO timestamp — only events after this time).
    - Response DTO:
        - `id`, `action` (human‑readable: "created a task", "uploaded a document", etc.), `actorName`, `entityType`, `entityId`, `entityName` (denormalized for display), `occurredAt`, `details` (optional extra context).

3. **Activity message formatting**

    - Define a mapping from `(auditEventType, entityType)` → human‑readable message template.
    - Examples:
        - `(CREATED, TASK)` → "{actor} created task {entityName}"
        - `(UPDATED, DOCUMENT)` → "{actor} updated document {entityName}"
        - `(COMMENT_CREATED, TASK)` → "{actor} commented on task {entityName}"
        - `(CLAIMED, TASK)` → "{actor} claimed task {entityName}"
        - `(DELETED, TIME_ENTRY)` → "{actor} deleted a time entry on task {entityName}"
    - The formatting happens in a backend service (not frontend) so the API returns ready‑to‑display messages.

4. **Frontend**

    - **Activity tab** on the project detail page (new tab alongside Overview, Tasks, Time, Members).
    - Shows a chronological list of activity items with actor avatar/initials, message, and relative timestamp.
    - Optional entity‑type filter chips at the top.
    - "Load more" pagination (not infinite scroll).

5. **Performance considerations**

    - The `audit_events` table will grow. Ensure the query uses appropriate indexes.
    - Consider whether `project_id` should be a first‑class indexed column on `audit_events`, or if filtering via JSONB `details->>'projectId'` with a GIN index is sufficient. Recommend in the architecture.

### 3. In‑app notification system

Design a **notification system** that alerts users about events relevant to them:

1. **Data model**

    - `Notification` entity:
        - `id` (UUID).
        - `tenant_id` (for shared‑schema isolation).
        - `recipient_member_id` (UUID — who receives this notification).
        - `type` (enum: `TASK_ASSIGNED`, `TASK_CLAIMED`, `COMMENT_ADDED`, `DOCUMENT_SHARED`, `MEMBER_INVITED`, `PROJECT_UPDATED`, etc.).
        - `title` (short summary, e.g. "New comment on Task #123").
        - `body` (optional longer text).
        - `reference_entity_type` (enum: `TASK`, `DOCUMENT`, `PROJECT`, `COMMENT`).
        - `reference_entity_id` (UUID — for deep‑linking).
        - `reference_project_id` (UUID — for navigation context).
        - `is_read` (boolean, default false).
        - `created_at` timestamp.
    - Indexes:
        - `(recipient_member_id, is_read, created_at DESC)` for the notification bell query.
        - `(recipient_member_id, created_at DESC)` for the full notification list.

2. **Notification triggers**

    Define which domain events generate notifications and who receives them:

    | Event | Recipients | Type |
    |-------|-----------|------|
    | Task assigned to member | The assigned member | `TASK_ASSIGNED` |
    | Task claimed by someone | Previous assignee (if any), project leads | `TASK_CLAIMED` |
    | Comment added on a task | Task assignee + other commenters on that task (excluding author) | `COMMENT_ADDED` |
    | Comment added on a document | Document uploader + other commenters (excluding author) | `COMMENT_ADDED` |
    | Document uploaded to project | All project members (excluding uploader) | `DOCUMENT_SHARED` |
    | Member added to project | The added member | `MEMBER_INVITED` |
    | Task status changed | Task assignee (if not the actor) | `TASK_UPDATED` |

    - The notification fan‑out (determining recipients) happens in notification event handlers, not in domain services.
    - Domain services publish `ApplicationEvent`s; a `NotificationEventHandler` listens and creates `Notification` rows.

3. **API endpoints**

    - `GET /api/notifications` — list notifications for the current user. Params: `unreadOnly` (boolean), `page`, `size`.
    - `GET /api/notifications/unread-count` — returns `{ count: N }` for the bell badge.
    - `PUT /api/notifications/{id}/read` — mark a single notification as read.
    - `PUT /api/notifications/read-all` — mark all notifications as read for the current user.
    - `DELETE /api/notifications/{id}` — dismiss/delete a notification.

    All endpoints are **self‑scoped** (current user's notifications only — no `ProjectAccessService` needed, similar to My Work pattern from ADR‑023).

4. **Frontend**

    - **Notification bell** in the top nav bar (app shell header):
        - Shows unread count badge.
        - Click opens a dropdown/popover with the most recent notifications (e.g. 10).
        - Each notification shows: icon by type, title, relative time, read/unread indicator.
        - Clicking a notification navigates to the referenced entity and marks it as read.
        - "Mark all as read" action in the dropdown header.
        - "View all" link goes to a full notifications page.
    - **Notifications page** (`/notifications`):
        - Full paginated list of notifications.
        - Filter: all / unread.
        - Bulk "mark all as read" action.
    - **Polling**:
        - Poll `GET /api/notifications/unread-count` every 30–60 seconds from the app shell to update the badge.
        - No WebSocket/SSE in this phase.

5. **Notification preferences** (lightweight)

    - For this phase, implement a simple **opt‑out** model:
        - `NotificationPreference` entity or JSON column on `members`:
            - `member_id`, `notification_type`, `in_app_enabled` (boolean), `email_enabled` (boolean).
        - Default: all notification types enabled for in‑app; all disabled for email (since email isn't wired yet).
    - API: `GET /api/notifications/preferences`, `PUT /api/notifications/preferences`.
    - Frontend: simple settings page or section in user profile with toggles per notification type.

### 4. Email notification stubs

Define the **abstraction layer** for email notifications without requiring a live email provider:

1. **Email service interface**

    - `NotificationChannel` interface with implementations:
        - `InAppNotificationChannel` — creates `Notification` rows (the primary channel for this phase).
        - `EmailNotificationChannel` — the stub:
            - In `local`/`dev` profile: logs the email content to console/file (no actual send).
            - In `prod` profile: throws `UnsupportedOperationException` or is simply not registered (email delivery is a future phase).
    - A `NotificationDispatcher` service that checks user preferences and routes to appropriate channels.

2. **Email templates**

    - Define template structures for at least:
        - Task assignment notification.
        - New comment notification.
        - Document shared notification.
    - Templates should be simple text‑based (or Thymeleaf template strings) with placeholder substitution.
    - Store template definitions in code (enum or resource files), not in the database.

3. **Future integration point**

    - Document where SES (or another provider) would plug in:
        - `EmailNotificationChannel` gains a real `SesClient` dependency.
        - LocalStack already supports SES for local testing.
    - No infrastructure changes needed in this phase — just the abstraction.

### 5. Event publication pattern

Define the **Spring ApplicationEvent** pattern used across this phase:

1. **Event hierarchy**

    - Design a base `DomainEvent` class (or use the one from Phase 6 if it exists) carrying:
        - `eventType`, `entityType`, `entityId`, `projectId`, `actorMemberId`, `occurredAt`, `tenantId`.
    - Specific event subtypes carry additional payload as needed.

2. **Publication points**

    - Domain services (`TaskService`, `DocumentService`, `CommentService`, etc.) publish events via `ApplicationEventPublisher`.
    - Events are published **after** the transaction commits (use `@TransactionalEventListener(phase = AFTER_COMMIT)` on handlers) to avoid notifying about rolled‑back changes.

3. **Consumers**

    - `AuditEventHandler` — already exists from Phase 6, listens to domain events and writes audit rows.
    - `NotificationEventHandler` — new in Phase 6.5, listens to domain events, determines recipients, creates notifications.
    - Both handlers are **independent** — if notification creation fails, audit is unaffected and vice versa.

4. **Relationship to Phase 7**

    - This event pattern is intentionally shaped so Phase 7's `PortalProjectionHandler` can subscribe to the same events and project data into the portal read‑model schema.
    - Clarify how the event objects should be designed for this forward compatibility.

### 6. ADRs for key decisions

Add ADR‑style sections for at least:

1. **Flat comments vs threaded comments**:
    - Why flat comments are chosen for now.
    - How `parent_id` enables future threading without migration.
2. **Activity feed: direct audit query vs materialized view**:
    - Why querying `audit_events` directly (with formatting) is preferred over a separate activity table.
    - Performance implications and when to revisit.
3. **Notification fan‑out: synchronous vs async**:
    - Why in‑process `@TransactionalEventListener` is acceptable for now.
    - When to move to async processing (queue‑based fan‑out).
4. **Comment visibility model**:
    - Why `INTERNAL` / `SHARED` (not per‑user or per‑role visibility).
    - How this maps to the Phase 7 portal projection.
5. **Polling vs push for notification delivery**:
    - Why polling is chosen for this phase.
    - When to introduce WebSocket/SSE.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Keep the design **generic and domain‑agnostic** — comments, notifications, and activity feeds are not tied to any specific industry vertical.
- Do not change the existing auth model, tenancy model, or core domain entities (except to add event publication where missing).
- Build on Phase 6's audit infrastructure — do not duplicate or replace it.
- Design the comment visibility flag and event hierarchy with **explicit forward compatibility** for Phase 7's customer portal projection.
- Keep the frontend additions consistent with the existing Shadcn UI design system and component patterns.

Return a single markdown document as your answer, ready to be merged as "Phase 6.5 — Notifications, Comments & Activity" and ADRs.
