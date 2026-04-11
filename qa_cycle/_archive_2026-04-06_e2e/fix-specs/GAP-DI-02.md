# Fix Spec: GAP-DI-02 — Comments can be created on ARCHIVED projects

## Problem

QA test T1.4.7 showed that comments can be created on ARCHIVED projects (HTTP 201 success). The `ProjectLifecycleGuard.requireNotReadOnly()` is called by `TaskService`, `DocumentService`, and `TimeEntryValidationService` before creating child entities on a project, but `CommentService.createComment()` does not perform this check. The archive guard has a coverage gap for comments.

## Root Cause (hypothesis)

`CommentService.createComment()` (line 61-145 of `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java`) calls `projectAccessService.requireViewAccess()` to verify the caller can see the project, but never calls `projectLifecycleGuard.requireNotReadOnly()` to verify the project is not archived. The same gap exists in `updateComment()` and `deleteComment()`.

Other services that create project-scoped entities all call the guard:
- `TaskService.createTask()` (line 222): `projectLifecycleGuard.requireNotReadOnly(projectId)`
- `DocumentService.initiateUpload()` (line 100): `projectLifecycleGuard.requireNotReadOnly(projectId)`
- `TimeEntryValidationService.validateProjectAndCustomer()` (line 40): `projectLifecycleGuard.requireNotReadOnly(projectId)`

## Fix

1. Inject `ProjectLifecycleGuard` into `CommentService` constructor.
2. Add `projectLifecycleGuard.requireNotReadOnly(projectId)` call in `createComment()` after the `requireViewAccess()` call (line 68), before any entity validation.
3. Add `projectLifecycleGuard.requireNotReadOnly(projectId)` call in `updateComment()` after the `requireViewAccess()` call (line 159).
4. Do NOT add the guard to `deleteComment()` — deleting comments on archived projects should remain allowed (cleanup use case).
5. Do NOT add the guard to `listComments()` — reading comments on archived projects is expected.
6. Add an integration test to `CommentControllerTest.java` that verifies creating a comment on an archived project returns HTTP 400.

## Scope

Backend only.

**Files to modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java` — inject guard, add check
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/comment/CommentControllerTest.java` — add archive guard test

**Files to create:** None

**Migration needed:** No

## Verification

Re-run T1.4.7: POST comment on archived project should return HTTP 400 with "Project is archived. No modifications allowed."

## Estimated Effort

S (< 30 min)
