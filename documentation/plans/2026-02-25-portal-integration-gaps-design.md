# Portal Integration Gaps — Design Document

**Date**: 2026-02-25
**Bugs**: BUG-009, BUG-010, BUG-011
**Status**: Approved

## Problem Statement

Three bugs from customer portal development share a common root cause: the portal backend (Phase 7), comment system (Phase 6.5), and portal frontend (Phase 22) were built as separate phases with no integration verification between them.

| Bug | Title | Severity | Status |
|-----|-------|----------|--------|
| BUG-009 | Portal magic link not testable | medium | Fixed — needs verification tests |
| BUG-010 | Dashboard crashes on portal customer comments | critical | Fixed — needs verification tests + pattern hardening |
| BUG-011 | Comments don't flow between main app and portal | high | Open — needs design + implementation |

### Systemic Gaps Identified

1. **Requirements gap** — No phase owned the end-to-end comment round-trip. Architecture docs describe it, implementation skipped it.
2. **Design gap** — No shared actor abstraction. No shared entity-type contract for comments. Two incompatible comment models (TASK/DOCUMENT vs PROJECT).
3. **Process gap** — No integration smoke tests for portal auth or comment flows.

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Portal comment scope | Project-level | Portal UX is project-scoped; avoids entity-type mismatch; smallest change |
| Actor identity | Denormalize at write time | Already implemented in BUG-010 fix; audit events are append-only; no new abstractions |
| BUG-009/010 scope | Verification tests only | Already fixed; lock in with tests to prevent regression |

---

## Section 1: Main App SHARED Comments → Portal (BUG-011 Part A)

### Problem

`PortalEventHandler` handles 11 event types (documents, tasks, invoices, time entries, project links) but has no `CommentCreatedEvent` handler. When `CommentService` publishes a `CommentCreatedEvent` with `visibility = SHARED`, no listener projects it into the portal read model. SHARED comments from staff never reach the portal.

### Design

Add three event handlers to `PortalEventHandler`:

| Event | Action |
|-------|--------|
| `CommentCreatedEvent` | If SHARED → upsert into `portal_comments` |
| `CommentUpdatedEvent` | If SHARED → update body; if changed to INTERNAL → delete |
| `CommentDeletedEvent` | Delete from `portal_comments` |

### Data Flow

```
CommentService.createComment(visibility=SHARED)
  → publishes CommentCreatedEvent
  → [after commit] PortalEventHandler.onCommentCreated()
    → findCustomerIdsByProjectId(projectId)
    → for each customer: readModelRepo.upsertPortalComment(
        commentId, orgId, projectId, authorName, body, createdAt)
```

### Author Name Resolution

The `CommentCreatedEvent` carries `actorMemberId`. The handler resolves the member name at projection time and denormalizes it into `portal_comments.author_name`. This matches the existing pattern for `portal_documents`.

### Visibility Change Handling

- SHARED → INTERNAL: delete the portal projection (prevents stale comments lingering)
- INTERNAL → SHARED: upsert into portal (late-sharing is supported)
- Comment deleted: remove from portal read model

### Files

- `PortalEventHandler.java` — add 3 handlers (`onCommentCreated`, `onCommentUpdated`, `onCommentDeleted`)
- `PortalReadModelRepository.java` — verify `upsertPortalComment()` exists, add `deletePortalComment(commentId)`

---

## Section 2: Portal Comments → Main App (BUG-011 Part B)

### Problem

Portal comments are created with `entityType = "PROJECT"` and `entityId = projectId`. The main app only queries comments by `TASK` or `DOCUMENT` entity types. Portal comments exist in the `comments` table but are invisible to staff.

### Design

#### Backend

- Add `findByProjectIdAndEntityType(projectId, "PROJECT")` query to `CommentRepository`
- Add API endpoint: `GET /api/projects/{id}/comments?entityType=PROJECT`
- Keep existing `CommentService` validation guard (`TASK`/`DOCUMENT` only) for staff-created comments
- Add a staff reply path: allow `PROJECT` entity type for SHARED comments only (new method or relaxed validation for replies)

#### Comment Source Tracking

Add a `source` column to the `comments` table:

```sql
ALTER TABLE comments ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';
```

| Value | Meaning | Set by |
|-------|---------|--------|
| `INTERNAL` | Staff-created comment | `CommentService` (default) |
| `PORTAL` | Portal customer comment | `PortalCommentService` |

Used purely for display styling (e.g., "Customer" badge). Not for access control.

#### Frontend — Project Detail Page

- Add a "Customer Comments" tab/section to the project detail page (alongside Activity, Time, Budget)
- Fetches `GET /api/projects/{id}/comments?entityType=PROJECT`
- Renders chronological comment list: author name, timestamp, content
- Portal-originated comments display a "Customer" badge (driven by `source = 'PORTAL'`)
- Includes a reply input for staff — creates `entityType = "PROJECT"`, `visibility = "SHARED"`, `source = "INTERNAL"` comment
- Staff replies flow to portal via Section 1's event handler

#### Staff Reply Flow

```
Staff types reply in "Customer Comments" section
  → POST /api/projects/{id}/comments { entityType: "PROJECT", visibility: "SHARED", content }
  → CommentService creates comment with source = "INTERNAL"
  → publishes CommentCreatedEvent(visibility=SHARED)
  → PortalEventHandler projects to portal_comments
  → Portal customer sees staff reply
```

### Files

- `Comment.java` — add `source` field (`String`, default `"INTERNAL"`)
- `CommentRepository.java` — add `findByProjectIdAndEntityType` query
- `CommentController.java` — add project-level comments endpoint
- `CommentService.java` — allow `PROJECT` entity type for SHARED comments, or add dedicated `createProjectComment()` method
- `PortalCommentService.java` — set `source = "PORTAL"` at creation time
- New Flyway migration — add `source` column
- Frontend: new `ProjectCommentsSection` component, API function, project detail page integration

---

## Section 3: Actor Identity Hardening (BUG-010 Pattern)

### Problem

The BUG-010 fix added COALESCE fallback to audit queries, but the pattern isn't standardized. New audit event producers could omit `actor_name` and regress.

### Design

**Standardize at the audit service level:** Always include `actor_name` in audit event JSONB `details` at write time.

Resolution order:
1. `RequestScopes.MEMBER_ID` bound → look up member name from repository
2. Explicit `actorName` parameter → used by portal services that already know the name
3. Fallback → `"System"` for automated/scheduled events

Every audit event written from now on is self-describing for actor identity. The COALESCE in read queries becomes a safety net for historical data only.

### Frontend

- `getInitials()` in `recent-activity-widget.tsx`: `if (!name) return "?"` — already fixed per BUG-010, verify in place

### Files

- `AuditService.java` — enrich `details` map with `actor_name` at event creation time
- Verify `recent-activity-widget.tsx` null guard

---

## Section 4: Verification Tests

### BUG-009 (Magic Link Testability) — Already Fixed, Lock In

| Test | Type | Assertion |
|------|------|-----------|
| Magic link URL path | Backend integration | URL contains `/auth/exchange?token=` (not `/portal/login`) |
| E2E profile included | Backend integration | `isDevProfile()` returns `true` for `e2e` profile |
| Dev link displayed | Playwright E2E | Portal login page shows clickable magic link in dev mode |

### BUG-010 (Dashboard Crash) — Already Fixed, Lock In

| Test | Type | Assertion |
|------|------|-----------|
| Portal actor name in activity | Backend integration | `findCrossProjectActivity()` returns customer name (not null) for portal-originated events |
| Null-safe getInitials | Frontend unit | `getInitials(null)` returns `"?"`, `getInitials(undefined)` returns `"?"` |

### BUG-011 (Comment Flow) — New Tests

| Test | Type | Assertion |
|------|------|-----------|
| SHARED comment → portal | Backend integration | Create SHARED comment → `portal_comments` row exists |
| Visibility revoke | Backend integration | Change SHARED → INTERNAL → `portal_comments` row deleted |
| Portal comment queryable | Backend integration | `findByProjectIdAndEntityType("PROJECT")` returns portal comments |
| Source field | Backend integration | Portal comment has `source = "PORTAL"`, staff comment has `source = "INTERNAL"` |
| Customer Comments section | Frontend unit | Renders portal comments with "Customer" badge |
| Staff reply | Backend integration | Staff creates PROJECT/SHARED comment → projected to portal |
| Portal login E2E | Playwright | Full magic link flow: request → display → click → exchange → authenticated |

---

## Migration Plan

Single Flyway migration (next available version number):

```sql
-- Add source column to comments table
ALTER TABLE comments ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';

-- Update existing portal comments (created by PortalCommentService with entityType = 'PROJECT')
UPDATE comments SET source = 'PORTAL' WHERE entity_type = 'PROJECT';
```

No data loss. Existing comments default to `INTERNAL`. Portal comments identifiable by `entity_type = 'PROJECT'` get backfilled to `source = 'PORTAL'`.

---

## Implementation Order

```
Section 3 (Actor Identity)     — no dependencies, pure backend
    ↓
Section 1 (Main → Portal)      — depends on actor name being available in events
    ↓
Section 2 (Portal → Main)      — depends on portal projection working (Section 1)
    ↓
Section 4 (Verification Tests)  — validates all three sections + existing fixes
```

Sections 3 and 1 can be parallelized if the actor name enrichment is limited to the audit service (Section 3) and the event handler resolves names independently (Section 1).

---

## Out of Scope

- Real-time comment updates via WebSocket/SSE (deferred per ADR-038)
- Per-customer comment visibility (future: `visible_to_customer_ids` column per ADR-037)
- Task-level portal comments (future: expose task picker in portal UI)
- Comment threading/replies (future: `parent_id` column exists but unused per ADR-034)
- Audit trail for portal read operations (portal reads not audited)
