# Portal Integration Gaps — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix bidirectional comment flow between main app and portal (BUG-011), harden actor identity (BUG-010), and add regression tests for all three portal bugs.

**Architecture:** Event-driven projection from main app → portal read model via `PortalEventHandler`. New project-level comment query + UI section for portal → main app visibility. Actor name denormalized at audit write time.

**Tech Stack:** Spring Boot 4 (Java 25), Next.js 16, PostgreSQL, Flyway, vitest, JUnit 5

**Design doc:** `docs/plans/2026-02-25-portal-integration-gaps-design.md`

**Key conventions:**
- Read `backend/CLAUDE.md` before touching backend files
- Read `frontend/CLAUDE.md` before touching frontend files
- Backend base package: `io.b2mash.b2b.b2bstrawman`
- Backend tests: `./mvnw test -q` (always use `-q` to suppress verbose output)
- Frontend tests: `pnpm test --run` from `frontend/`
- Next migration version: `V40`

---

## Task 1: Flyway Migration — Add `source` Column to Comments

**Files:**
- Create: `backend/src/main/resources/db/migration/tenant/V40__comment_source_column.sql`

**Step 1: Write the migration**

```sql
-- V40__comment_source_column.sql
-- Add source column to distinguish internal vs portal-originated comments
ALTER TABLE comments ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';

-- Backfill existing portal comments (created by PortalCommentService with entity_type = 'PROJECT')
UPDATE comments SET source = 'PORTAL' WHERE entity_type = 'PROJECT';
```

**Step 2: Verify migration compiles with a quick build**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/V40__comment_source_column.sql
git commit -m "feat: add source column to comments table (V40 migration)"
```

---

## Task 2: Comment Entity — Add `source` Field

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java`

**Step 1: Add field to entity**

Add after the `updatedAt` field (around line 47):

```java
@Column(name = "source", nullable = false, length = 20)
private String source = "INTERNAL";
```

**Step 2: Update existing constructor to accept `source`**

Change the constructor (lines 51-66) to add `source` parameter with a default overload:

```java
public Comment(
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID authorMemberId,
    String body,
    String visibility) {
  this(entityType, entityId, projectId, authorMemberId, body, visibility, "INTERNAL");
}

public Comment(
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID authorMemberId,
    String body,
    String visibility,
    String source) {
  this.entityType = entityType;
  this.entityId = entityId;
  this.projectId = projectId;
  this.authorMemberId = authorMemberId;
  this.body = body;
  this.visibility = visibility;
  this.source = source;
  this.createdAt = Instant.now();
  this.updatedAt = Instant.now();
}
```

**Step 3: Add getter**

```java
public String getSource() {
  return source;
}
```

**Step 4: Compile check**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java
git commit -m "feat: add source field to Comment entity"
```

---

## Task 3: PortalCommentService — Set `source = PORTAL`

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalCommentService.java` (line 52)

**Step 1: Update comment creation to use new constructor**

Change line 52 from:
```java
var comment = new Comment("PROJECT", projectId, projectId, authorId, content, "SHARED");
```
to:
```java
var comment = new Comment("PROJECT", projectId, projectId, authorId, content, "SHARED", "PORTAL");
```

**Step 2: Compile check**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalCommentService.java
git commit -m "feat: set source=PORTAL for portal-originated comments"
```

---

## Task 4: PortalEventHandler — Add Comment Event Handlers (BUG-011 Part A)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`

**Step 1: Write the failing test**

Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandlerCommentTest.java`

```java
package io.b2mash.b2b.b2bstrawman.customerbackend.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.comment.Comment;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.test.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class PortalEventHandlerCommentTest {

  @Autowired private PortalEventHandler handler;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private CommentRepository commentRepository;

  // Note: Tests will need tenant scope and a seeded project + customer link.
  // Follow the pattern from existing PortalEventHandler tests (e.g., PortalEventHandlerDocumentTest).
  // The test infrastructure (TestCustomerFactory, TestProjectFactory, etc.) handles setup.

  @Test
  void sharedCommentProjectedToPortal() {
    // Given: a project linked to a customer (use test factories)
    // When: CommentCreatedEvent with visibility=SHARED is handled
    // Then: portal_comments has a row with the comment's content and author name
  }

  @Test
  void internalCommentNotProjected() {
    // Given: a project linked to a customer
    // When: CommentCreatedEvent with visibility=INTERNAL is handled
    // Then: portal_comments has no row
  }

  @Test
  void visibilityChangeSharedToInternalDeletesProjection() {
    // Given: a SHARED comment already projected to portal
    // When: CommentVisibilityChangedEvent with oldVisibility=SHARED, newVisibility=INTERNAL
    // Then: portal_comments row is deleted
  }

  @Test
  void visibilityChangeInternalToSharedCreatesProjection() {
    // Given: an INTERNAL comment (not in portal)
    // When: CommentVisibilityChangedEvent with oldVisibility=INTERNAL, newVisibility=SHARED
    // Then: portal_comments has a row
  }

  @Test
  void deletedCommentRemovedFromPortal() {
    // Given: a SHARED comment projected to portal
    // When: CommentDeletedEvent is handled
    // Then: portal_comments row is deleted
  }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="PortalEventHandlerCommentTest" -q 2>&1 | tail -20`
Expected: FAIL (tests reference handler methods that don't exist yet, or skeleton tests need to be fleshed out using existing test patterns)

**Step 3: Add imports and handler methods to PortalEventHandler**

Add these three methods to `PortalEventHandler.java` (after the existing `onDocumentDeleted` handler, following the same pattern from lines 174-205):

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCommentCreated(CommentCreatedEvent event) {
  handleInTenantScope(
      event.getTenantId(),
      event.getOrgId(),
      () -> {
        try {
          if (!"SHARED".equals(event.getVisibility())) {
            return;
          }
          var customerIds =
              readModelRepo.findCustomerIdsByProjectId(event.getProjectId(), event.getOrgId());
          // Resolve author name: use actorName from event (already set by CommentService)
          String authorName = event.getActorName() != null ? event.getActorName() : "Unknown";
          for (var customerId : customerIds) {
            readModelRepo.upsertPortalComment(
                event.getEntityId(),
                event.getOrgId(),
                event.getProjectId(),
                authorName,
                (String) event.getDetails().get("body"),
                event.getOccurredAt());
            readModelRepo.incrementCommentCount(event.getProjectId(), customerId);
          }
        } catch (Exception e) {
          log.warn("Failed to project CommentCreatedEvent: commentId={}", event.getEntityId(), e);
        }
      });
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCommentVisibilityChanged(CommentVisibilityChangedEvent event) {
  handleInTenantScope(
      event.getTenantId(),
      event.getOrgId(),
      () -> {
        try {
          if ("SHARED".equals(event.getNewVisibility())) {
            // INTERNAL → SHARED: project the comment
            var comment = commentRepository.findById(event.getEntityId()).orElse(null);
            if (comment == null) return;
            var customerIds =
                readModelRepo.findCustomerIdsByProjectId(event.getProjectId(), event.getOrgId());
            String authorName = event.getActorName() != null ? event.getActorName() : "Unknown";
            for (var customerId : customerIds) {
              readModelRepo.upsertPortalComment(
                  comment.getId(),
                  event.getOrgId(),
                  event.getProjectId(),
                  authorName,
                  comment.getBody(),
                  comment.getCreatedAt());
              readModelRepo.incrementCommentCount(event.getProjectId(), customerId);
            }
          } else if ("SHARED".equals(event.getOldVisibility())) {
            // SHARED → INTERNAL: delete the projection
            readModelRepo.deletePortalComment(event.getEntityId(), event.getOrgId());
            var customerIds =
                readModelRepo.findCustomerIdsByProjectId(event.getProjectId(), event.getOrgId());
            for (var customerId : customerIds) {
              readModelRepo.decrementCommentCount(event.getProjectId(), customerId);
            }
          }
        } catch (Exception e) {
          log.warn(
              "Failed to handle CommentVisibilityChangedEvent: commentId={}",
              event.getEntityId(),
              e);
        }
      });
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCommentDeleted(CommentDeletedEvent event) {
  handleInTenantScope(
      event.getTenantId(),
      event.getOrgId(),
      () -> {
        try {
          readModelRepo.deletePortalComment(event.getEntityId(), event.getOrgId());
          var customerIds =
              readModelRepo.findCustomerIdsByProjectId(event.getProjectId(), event.getOrgId());
          for (var customerId : customerIds) {
            readModelRepo.decrementCommentCount(event.getProjectId(), customerId);
          }
        } catch (Exception e) {
          log.warn(
              "Failed to handle CommentDeletedEvent: commentId={}", event.getEntityId(), e);
        }
      });
}
```

Also add the `CommentRepository` to the constructor injection (it's needed by `onCommentVisibilityChanged` to look up the comment body):

```java
private final CommentRepository commentRepository;
```

And add the necessary imports at the top of the file:

```java
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentVisibilityChangedEvent;
```

**Step 4: Flesh out tests with real assertions**

Use the existing test patterns from other `PortalEventHandler*Test` files. Each test should:
1. Set up tenant scope via `ScopedValue.where(RequestScopes.TENANT_ID, schema).run(...)`
2. Create a project + customer link using test factories
3. Invoke the handler method directly
4. Query `readModelRepo.findCommentsByProject()` to verify projections

**Step 5: Run tests to verify they pass**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="PortalEventHandlerCommentTest" -q 2>&1 | tail -20`
Expected: PASS — all 5 tests green

**Step 6: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java
git add backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandlerCommentTest.java
git commit -m "feat: project SHARED comments to portal read model (BUG-011 Part A)"
```

---

## Task 5: CommentRepository — Add Project-Level Query

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentRepository.java`

**Step 1: Add query method**

Add after the existing `findByTargetAndProject` method (around line 27):

```java
/** Find project-level comments (portal + staff replies) for the Customer Comments section. */
@Query(
    """
    SELECT c FROM Comment c
    WHERE c.entityType = 'PROJECT'
      AND c.projectId = :projectId
    ORDER BY c.createdAt ASC
    """)
Page<Comment> findProjectLevelComments(
    @Param("projectId") UUID projectId, Pageable pageable);
```

**Step 2: Compile check**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentRepository.java
git commit -m "feat: add project-level comment query to CommentRepository"
```

---

## Task 6: CommentService — Allow PROJECT Entity Type for SHARED Comments

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java`

**Step 1: Write the failing test**

Add a test to the existing `CommentServiceTest` (or create new test file):

```java
@Test
void createProjectComment_withSharedVisibility_succeeds() {
  // Given: an active project
  // When: createComment with entityType="PROJECT", entityId=projectId, visibility="SHARED"
  // Then: comment is created with source="INTERNAL", entityType="PROJECT"
}

@Test
void createProjectComment_withInternalVisibility_rejected() {
  // Given: an active project
  // When: createComment with entityType="PROJECT", visibility="INTERNAL"
  // Then: throws InvalidStateException (PROJECT comments must be SHARED)
}
```

**Step 2: Run test to verify it fails**

Expected: FAIL with "entityType must be TASK or DOCUMENT"

**Step 3: Update validation in CommentService.createComment()**

Change the entity type validation (around line 71) from:

```java
if (!"TASK".equals(entityType) && !"DOCUMENT".equals(entityType)) {
  throw new InvalidStateException("Invalid entity type", "entityType must be TASK or DOCUMENT");
}
```

to:

```java
if ("PROJECT".equals(entityType)) {
  // PROJECT-level comments are only allowed with SHARED visibility (staff replies to portal thread)
  if (!"SHARED".equals(visibility)) {
    throw new InvalidStateException(
        "Invalid visibility for project comment",
        "PROJECT-level comments must have SHARED visibility");
  }
  // entityId must equal projectId for PROJECT-level comments
  if (!entityId.equals(projectId)) {
    throw new InvalidStateException(
        "Invalid entity for project comment",
        "entityId must match projectId for PROJECT-level comments");
  }
} else if (!"TASK".equals(entityType) && !"DOCUMENT".equals(entityType)) {
  throw new InvalidStateException("Invalid entity type", "entityType must be TASK, DOCUMENT, or PROJECT");
}
```

Also update `validateEntityBelongsToProject` (around line 323) to skip validation for `PROJECT` type — the project already belongs to itself:

```java
private void validateEntityBelongsToProject(String entityType, UUID entityId, UUID projectId) {
  if ("PROJECT".equals(entityType)) {
    // PROJECT comments reference the project itself — no cross-validation needed
    return;
  }
  // ... existing TASK and DOCUMENT validation
}
```

**Step 4: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="CommentServiceTest" -q 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java
git add backend/src/test/java/io/b2mash/b2b/b2bstrawman/comment/CommentServiceTest.java
git commit -m "feat: allow PROJECT entity type for SHARED comments (staff replies)"
```

---

## Task 7: CommentController — Add `source` to Response + Project Comments Endpoint

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentController.java`

**Step 1: Add `source` to CommentResponse**

In the `CommentResponse` record (around line 137), add `source` field:

```java
record CommentResponse(
    UUID id,
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID authorMemberId,
    String authorName,
    String authorAvatarUrl,
    String body,
    String visibility,
    String source,
    UUID parentId,
    Instant createdAt,
    Instant updatedAt) {
  // Update the from() method to include source
}
```

Update `CommentResponse.from()` to map `comment.getSource()`.

**Step 2: Update listComments endpoint to support entityType=PROJECT**

The existing `GET /api/projects/{projectId}/comments` endpoint (line 65) accepts `entityType` and `entityId` as query params. For `entityType=PROJECT`, use the new `findProjectLevelComments` query instead:

```java
@GetMapping
public Page<CommentResponse> listComments(
    @PathVariable UUID projectId,
    @RequestParam(required = false) String entityType,
    @RequestParam(required = false) UUID entityId,
    Pageable pageable) {
  // ...existing auth check...

  Page<Comment> comments;
  if ("PROJECT".equals(entityType)) {
    comments = commentRepository.findProjectLevelComments(projectId, pageable);
  } else {
    if (entityType == null || entityId == null) {
      throw new InvalidStateException("Missing parameters", "entityType and entityId are required");
    }
    comments = commentRepository.findByTargetAndProject(entityType, entityId, projectId, pageable);
  }
  // ... map to response with author info
}
```

**Step 3: Compile + test**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -q 2>&1 | tail -20`
Expected: BUILD SUCCESS, all existing tests pass

**Step 4: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentController.java
git commit -m "feat: add source to CommentResponse, support PROJECT entity type in list endpoint"
```

---

## Task 8: AuditEventBuilder — Denormalize `actor_name` (BUG-010 Hardening)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java`

**Step 1: Write the failing test**

```java
@Test
void buildAuditEvent_enrichesDetailsWithActorName() {
  // Given: RequestScopes.MEMBER_ID is bound to a known member
  // When: AuditEventBuilder.builder().eventType("test").build()
  // Then: details map contains "actor_name" key with the member's name
}

@Test
void buildAuditEvent_preservesExplicitActorName() {
  // Given: details map already contains "actor_name" key
  // When: building the event
  // Then: the explicit actor_name is NOT overwritten
}
```

**Step 2: Implement enrichment**

In `AuditEventBuilder.build()` (around line 145), after resolving `resolvedActorId`, add actor name enrichment:

```java
// Enrich details with actor_name if not already present
if (details != null && !details.containsKey("actor_name")) {
  if (resolvedActorId != null && "USER".equals(resolvedActorType)) {
    // Look up member name — requires memberRepository injection
    // Option: accept actorName as a builder field, or resolve via RequestScopes
    var mutableDetails = new java.util.HashMap<>(details);
    // The actor name should be passed explicitly by callers who have it,
    // or resolved from member repo for USER actors
    mutableDetails.putIfAbsent("actor_name", "Unknown");
    details = Map.copyOf(mutableDetails);
  }
}
```

**Note to implementer:** The exact enrichment approach depends on whether `AuditEventBuilder` has access to `MemberRepository`. Check the existing builder pattern — it may be a static builder without Spring injection. If so, the enrichment should happen in `AuditService.log()` instead (which does have access to repositories). Look at how `AuditServiceImpl` processes the `AuditEventRecord` and add name resolution there.

**Step 3: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="AuditEventBuilderTest,AuditServiceTest" -q 2>&1 | tail -20`
Expected: PASS

**Step 4: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/
git add backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/
git commit -m "feat: denormalize actor_name in audit event details at write time"
```

---

## Task 9: Frontend — Comment Interface Update + Project Comments Action

**Files:**
- Modify: `frontend/lib/actions/comments.ts`

**Step 1: Add `source` to Comment interface**

In `frontend/lib/actions/comments.ts` (lines 6-19), add `source` field:

```typescript
export interface Comment {
  id: string;
  entityType: string;
  entityId: string;
  projectId: string;
  authorMemberId: string;
  authorName: string | null;
  authorAvatarUrl: string | null;
  body: string;
  visibility: "INTERNAL" | "SHARED";
  source: "INTERNAL" | "PORTAL";
  parentId: string | null;
  createdAt: string;
  updatedAt: string;
}
```

**Step 2: Add fetchProjectComments function**

```typescript
export async function fetchProjectComments(projectId: string) {
  const response = await api.get<{ content: Comment[] }>(
    `/api/projects/${projectId}/comments?entityType=PROJECT&size=50`
  );
  return response?.content ?? [];
}
```

**Step 3: Add createProjectComment function**

```typescript
export async function createProjectComment(
  orgSlug: string,
  projectId: string,
  body: string
) {
  const response = await api.post<Comment>(
    `/api/projects/${projectId}/comments`,
    {
      entityType: "PROJECT",
      entityId: projectId,
      body,
      visibility: "SHARED",
    }
  );
  revalidatePath(`/org/${orgSlug}/projects/${projectId}`);
  return response;
}
```

**Step 4: Commit**

```bash
git add frontend/lib/actions/comments.ts
git commit -m "feat: add source to Comment interface, add project-level comment actions"
```

---

## Task 10: Frontend — ProjectCommentsSection Component

**Files:**
- Create: `frontend/components/projects/project-comments-section.tsx`

**Step 1: Build the component**

```typescript
"use client";

import { useEffect, useState, useTransition } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import {
  type Comment,
  fetchProjectComments,
  createProjectComment,
} from "@/lib/actions/comments";
import { formatDistanceToNow } from "date-fns";

interface ProjectCommentsSectionProps {
  projectId: string;
  orgSlug: string;
}

export function ProjectCommentsSection({
  projectId,
  orgSlug,
}: ProjectCommentsSectionProps) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [body, setBody] = useState("");
  const [isPending, startTransition] = useTransition();
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchProjectComments(projectId)
      .then(setComments)
      .finally(() => setIsLoading(false));
  }, [projectId]);

  function handleSubmit() {
    if (!body.trim()) return;
    startTransition(async () => {
      await createProjectComment(orgSlug, projectId, body);
      setBody("");
      const updated = await fetchProjectComments(projectId);
      setComments(updated);
    });
  }

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading comments...</p>;
  }

  return (
    <div className="space-y-4">
      {comments.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No customer comments yet. Comments shared between your team and
          customers will appear here.
        </p>
      ) : (
        <div className="space-y-3">
          {comments.map((comment) => (
            <div key={comment.id} className="flex gap-3">
              <AvatarCircle
                name={comment.authorName ?? "?"}
                size={32}
                className="mt-0.5 shrink-0"
              />
              <div className="flex-1 space-y-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">
                    {comment.authorName ?? "Unknown"}
                  </span>
                  {comment.source === "PORTAL" && (
                    <Badge variant="outline">Customer</Badge>
                  )}
                  <span className="text-xs text-muted-foreground">
                    {formatDistanceToNow(new Date(comment.createdAt), {
                      addSuffix: true,
                    })}
                  </span>
                </div>
                <p className="text-sm whitespace-pre-wrap">{comment.body}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="space-y-2 border-t pt-4">
        <Textarea
          placeholder="Reply to the customer thread (visible to all linked customers)..."
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={3}
        />
        <Button
          onClick={handleSubmit}
          disabled={isPending || !body.trim()}
          size="sm"
        >
          {isPending ? "Sending..." : "Post Reply"}
        </Button>
      </div>
    </div>
  );
}
```

**Step 2: Commit**

```bash
git add frontend/components/projects/project-comments-section.tsx
git commit -m "feat: add ProjectCommentsSection component for portal comment thread"
```

---

## Task 11: Frontend — Add Customer Comments Tab to Project Detail Page

**Files:**
- Modify: `frontend/components/projects/project-tabs.tsx` (add tab definition + content slot)
- Modify: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` (pass new panel)

**Step 1: Add tab to ProjectTabs**

In `project-tabs.tsx`, add to `baseTabs` array (around line 30), before `"activity"`:

```typescript
{ id: "customer-comments", label: "Customer Comments" },
```

Add to `TabId` type (line 23):
```typescript
type TabId = "overview" | ... | "customer-comments" | "activity";
```

Add to props interface (lines 9-21):
```typescript
customerCommentsPanel?: React.ReactNode;
```

Add the `<TabsPrimitive.Content>` section alongside the other panels:
```tsx
{customerCommentsPanel && (
  <TabsPrimitive.Content value="customer-comments" className="mt-6">
    {customerCommentsPanel}
  </TabsPrimitive.Content>
)}
```

Add to the tab filter logic (around line 55) — only show if panel is provided:
```typescript
if (tab.id === "customer-comments" && !customerCommentsPanel) return false;
```

**Step 2: Pass panel from project detail page**

In `page.tsx`, import and render the component:

```typescript
import { ProjectCommentsSection } from "@/components/projects/project-comments-section";
```

In the JSX where other panels are passed to `<ProjectTabs>` (around line 457), add:

```tsx
customerCommentsPanel={
  <ProjectCommentsSection projectId={id} orgSlug={slug} />
}
```

**Note:** Only show the tab when the project has linked customers. Check if `customers.length > 0` and conditionally pass the panel.

**Step 3: Run frontend tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm test --run 2>&1 | tail -20`
Expected: All existing tests pass

**Step 4: Commit**

```bash
git add frontend/components/projects/project-tabs.tsx
git add frontend/app/(app)/org/[slug]/projects/[id]/page.tsx
git commit -m "feat: add Customer Comments tab to project detail page"
```

---

## Task 12: Frontend Tests — ProjectCommentsSection

**Files:**
- Create: `frontend/__tests__/comments/project-comments-section.test.tsx`

**Step 1: Write tests**

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProjectCommentsSection } from "@/components/projects/project-comments-section";

vi.mock("@/lib/actions/comments", () => ({
  fetchProjectComments: vi.fn(),
  createProjectComment: vi.fn(),
}));

import { fetchProjectComments, createProjectComment } from "@/lib/actions/comments";

const mockComment = (overrides = {}) => ({
  id: "c1",
  entityType: "PROJECT",
  entityId: "p1",
  projectId: "p1",
  authorMemberId: "m1",
  authorName: "Jane Customer",
  authorAvatarUrl: null,
  body: "Hello from the portal",
  visibility: "SHARED" as const,
  source: "PORTAL" as const,
  parentId: null,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  ...overrides,
});

describe("ProjectCommentsSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no comments", async () => {
    (fetchProjectComments as ReturnType<typeof vi.fn>).mockResolvedValue([]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    expect(await screen.findByText(/no customer comments/i)).toBeInTheDocument();
  });

  it("renders portal comments with Customer badge", async () => {
    (fetchProjectComments as ReturnType<typeof vi.fn>).mockResolvedValue([
      mockComment(),
    ]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    expect(await screen.findByText("Jane Customer")).toBeInTheDocument();
    expect(screen.getByText("Customer")).toBeInTheDocument();
    expect(screen.getByText("Hello from the portal")).toBeInTheDocument();
  });

  it("renders internal comments without Customer badge", async () => {
    (fetchProjectComments as ReturnType<typeof vi.fn>).mockResolvedValue([
      mockComment({ source: "INTERNAL", authorName: "Alice Staff" }),
    ]);
    render(<ProjectCommentsSection projectId="p1" orgSlug="test-org" />);
    expect(await screen.findByText("Alice Staff")).toBeInTheDocument();
    expect(screen.queryByText("Customer")).not.toBeInTheDocument();
  });
});
```

**Step 2: Run tests**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm test --run __tests__/comments/project-comments-section.test.tsx 2>&1 | tail -20`
Expected: PASS

**Step 3: Commit**

```bash
git add frontend/__tests__/comments/project-comments-section.test.tsx
git commit -m "test: add ProjectCommentsSection unit tests"
```

---

## Task 13: Backend Verification Tests — BUG-009 + BUG-010 Regression Locks

**Files:**
- Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthControllerRegressionTest.java`
- Modify or create: audit event test for actor name resolution

**Step 1: BUG-009 — Magic link URL path + e2e profile**

```java
@Test
void magicLinkUrl_containsAuthExchangePath() {
  // Given: dev profile is active
  // When: requesting a magic link
  // Then: response magicLink field contains "/auth/exchange?token="
  // Assert: does NOT contain "/portal/login?token="
}

@Test
void isDevProfile_includesE2eProfile() {
  // Given: "e2e" is the active Spring profile
  // When: isDevProfile() is called
  // Then: returns true
}
```

**Step 2: BUG-010 — Actor name resolution for portal actors**

```java
@Test
void crossProjectActivity_resolvesPortalActorName() {
  // Given: an audit event with actorType=PORTAL_USER and details containing actor_name="Customer Jane"
  // When: findCrossProjectActivity() returns results
  // Then: actorName is "Customer Jane" (not null, not "Unknown")
}
```

**Step 3: Frontend — verify getInitials null guard exists**

In `frontend/__tests__/dashboard/recent-activity-widget.test.tsx` (create if needed):

```typescript
import { describe, it, expect } from "vitest";

// Import or copy the getInitials function for testing
// Since it's a local function, may need to extract or test via component render

describe("getInitials null safety", () => {
  it("handles null name", () => {
    // Render widget with an activity item where actorName is null
    // Assert: avatar shows "?" not a crash
  });
});
```

**Step 4: Run all tests**

Run backend: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -q 2>&1 | tail -20`
Run frontend: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm test --run 2>&1 | tail -20`
Expected: All pass

**Step 5: Commit**

```bash
git add backend/src/test/ frontend/__tests__/
git commit -m "test: add regression tests for BUG-009 and BUG-010"
```

---

## Task 14: Full Integration Verification

**Step 1: Run full backend test suite**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw verify -q 2>&1 | tail -20`
Expected: BUILD SUCCESS with 0 failures

**Step 2: Run full frontend test suite**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm test --run 2>&1 | tail -20`
Expected: All tests pass

**Step 3: Run frontend build**

Run: `cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm build 2>&1 | tail -20`
Expected: Build succeeds (no type errors from new `source` field)

**Step 4: Final commit (if any fixes needed)**

```bash
git commit -m "fix: address test/build issues from portal integration gaps"
```

---

## Summary

| Task | What | Type |
|------|------|------|
| 1 | V40 migration — `source` column | DB schema |
| 2 | `Comment.java` — add `source` field | Backend entity |
| 3 | `PortalCommentService` — set `source=PORTAL` | Backend logic |
| 4 | `PortalEventHandler` — 3 comment event handlers | Backend (BUG-011 Part A) |
| 5 | `CommentRepository` — project-level query | Backend query |
| 6 | `CommentService` — allow PROJECT entity type | Backend validation |
| 7 | `CommentController` — source in response + PROJECT endpoint | Backend API |
| 8 | `AuditEventBuilder` — denormalize actor_name | Backend (BUG-010) |
| 9 | Frontend comment interface + actions | Frontend API layer |
| 10 | `ProjectCommentsSection` component | Frontend UI |
| 11 | Project detail page — add tab | Frontend integration |
| 12 | Frontend unit tests | Tests |
| 13 | BUG-009 + BUG-010 regression tests | Tests |
| 14 | Full integration verification | Verification |
