# Fix Spec: GAP-D6-03 — Member needs explicit project membership for matter access

## Problem
A user with Member org role cannot access a matter (project) unless explicitly added as a project member. Assigning a task to a member does NOT automatically grant project membership. Observed during Day 6 checkpoint 6.1 — Carol could not access the matter until Bob explicitly added her via the Members tab.

## Root Cause (confirmed)
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectAccessService.java`, lines 42-50.
For org Members (not owners/admins), `checkAccess()` only grants access if a `ProjectMember` record exists for the user. Task assignment (setting `assignee_id` on a Task entity) does not create a `ProjectMember` record.

This is by design — project membership is the access control mechanism, and task assignment is a separate concept. The test plan assumed that task assignment implies project access, which it does not.

## Fix
**WONT_FIX** — This is correct RBAC behavior, not a bug. The project membership model deliberately separates "who can access a project" from "who is assigned tasks." Auto-adding members when tasks are assigned would:
1. Violate the principle of least privilege
2. Create security concerns (anyone who can assign tasks could grant project access)
3. Require complex permission checks on task assignment

The workaround (add Carol as a project member before or after task assignment) is the correct workflow. The QA test plan should be updated to reflect this expected behavior.

## Scope
N/A — WONT_FIX (test plan correction needed)

## Verification
N/A

## Estimated Effort
N/A
