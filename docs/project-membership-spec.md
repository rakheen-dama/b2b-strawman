# Project Membership & Resource Ownership

## Problem

Currently, all org members see all projects. There is no concept of project-level access — a member either has access to everything in the org or nothing. This doesn't reflect real B2B workflows where teams work on specific projects and shouldn't see unrelated work.

## Goal

Introduce project-level membership so that org members only see projects they belong to, while admins and owners retain full visibility. Project creators become project leads who can manage their project's membership.

---

## Current State

| Operation | Member | Admin | Owner |
|-----------|--------|-------|-------|
| List projects | All projects | All projects | All projects |
| View project | Any project | Any project | Any project |
| Create project | No | Yes | Yes |
| Update project | No | Any project | Any project |
| Delete project | No | No | Any project |
| Upload documents | Any project | Any project | Any project |

**Key fields that exist but are unused for authorization:**
- `projects.created_by` — Clerk user ID of creator
- `documents.uploaded_by` — Clerk user ID of uploader

---

## Target State

| Operation | Member (not on project) | Project Member | Project Lead | Admin | Owner |
|-----------|------------------------|----------------|--------------|-------|-------|
| List projects | Only their projects | Only their projects | Only their projects | All projects | All projects |
| View project | No | Yes | Yes | Yes | Yes |
| Create project | Yes (becomes lead) | — | — | Yes (becomes lead) | Yes (becomes lead) |
| Update project | No | No | Yes | Yes | Yes |
| Delete project | No | No | No | No | Yes |
| Upload documents | No | Yes | Yes | Yes | Yes |
| Download documents | No | Yes | Yes | Yes | Yes |
| Add members to project | No | No | Yes | Yes | Yes |
| Remove members from project | No | No | Yes (not self) | Yes | Yes |
| Leave project | No | Yes | No (must transfer lead first) | — | — |

---

## Data Model Changes

### New Table: `project_members` (per-tenant schema)

```
project_members
├── id              UUID, PK
├── project_id      UUID, FK → projects(id) ON DELETE CASCADE
├── user_id         VARCHAR(255), NOT NULL  -- Clerk user ID
├── role            VARCHAR(50), NOT NULL   -- 'lead' or 'member'
├── added_by        VARCHAR(255), NOT NULL  -- Clerk user ID of who added them
├── created_at      TIMESTAMPTZ, NOT NULL, DEFAULT now()
└── UNIQUE(project_id, user_id)
```

**Project roles:**
- `lead` — The project creator (or transferred lead). Can manage project settings and membership.
- `member` — Can view the project and upload/download documents.

**Constraints:**
- Each project must have exactly one lead at all times.
- A user can be a member of multiple projects.
- When a project is deleted, all memberships cascade-delete.

### Migration

New Flyway tenant migration: `V3__create_project_members.sql`

When this migration runs, existing projects need to be backfilled:
- For each existing project, insert a `project_members` row with `role='lead'` for the `created_by` user.

---

## API Changes

### Modified Endpoints

**`GET /api/projects`** — List projects
- **Member**: Return only projects where the user has a `project_members` row.
- **Admin/Owner**: Return all projects (unchanged).
- Response should include the user's project role (`lead`, `member`, or `null` for admin/owner viewing non-member projects).

**`POST /api/projects`** — Create project
- **Change**: Allow `ORG_MEMBER` role (currently restricted to admin+).
- After creating the project, automatically insert a `project_members` row with `role='lead'` for the creating user.

**`GET /api/projects/{id}`** — Get project
- **Member**: Return 404 if user is not a project member.
- **Admin/Owner**: Return project (unchanged).

**`PUT /api/projects/{id}`** — Update project
- **Project lead**: Allow update.
- **Admin/Owner**: Allow update (unchanged).
- **Project member**: Deny (403).

**`DELETE /api/projects/{id}`** — Delete project
- **Owner only**: Unchanged.

**Document endpoints** (`/api/projects/{projectId}/documents/*`, `/api/documents/*`)
- **Member**: Deny if user is not a member of the parent project (404 on the project).
- **Project member or lead**: Allow (unchanged behavior).
- **Admin/Owner**: Allow (unchanged).

### New Endpoints

**`GET /api/projects/{id}/members`** — List project members
- **Access**: Project members, project lead, admin, owner.
- **Response**: Array of `{ userId, role, addedBy, createdAt }`.
- Note: User display names come from Clerk (frontend resolves via Clerk's `useUser` or server-side `clerkClient.users.getUser()`).

**`POST /api/projects/{id}/members`** — Add member to project
- **Access**: Project lead, admin, owner.
- **Request body**: `{ userId: string }`
- **Validation**: User must be an active org member (validate via the JWT's org context — the user being added must belong to the same org).
- **Default role**: `member`.
- **Idempotent**: If user is already a member, return 409 Conflict.

**`DELETE /api/projects/{id}/members/{userId}`** — Remove member from project
- **Access**: Project lead (cannot remove self), admin, owner.
- **Validation**: Cannot remove the project lead (must transfer lead first).

**`PUT /api/projects/{id}/members/{userId}/role`** — Transfer lead role
- **Access**: Current project lead, owner.
- **Request body**: `{ role: "lead" }`
- **Effect**: Demotes current lead to `member`, promotes target to `lead`.
- **Validation**: Target must be an existing project member.

---

## Frontend Changes

### Projects List Page (`/org/[slug]/projects`)

- All members see a "New Project" button (not just admins).
- Members see only their projects. Admins/owners see all projects.
- Each project card shows the user's role badge (`Lead`, `Member`) or nothing for admins viewing non-member projects.

### Project Detail Page (`/org/[slug]/projects/[id]`)

- Add a "Members" tab or panel (alongside the existing documents panel).
- Members panel shows:
  - List of project members with roles and avatars.
  - "Add Member" button (visible to lead/admin/owner) — opens a dialog to select from org members.
  - "Remove" button next to each member (visible to lead/admin/owner, not on the lead's own row).
  - "Transfer Lead" option (visible to current lead, on member rows).
- Edit project button: visible to lead, admin, owner.
- Upload documents: visible to all project members, admin, owner.

### Dashboard (`/org/[slug]/dashboard`)

- "Recent Projects" section: show only the user's projects (or all for admin/owner).
- Project count stat: reflect filtered count.

### Navigation

- No changes to sidebar navigation.

---

## Authorization Logic (Backend)

### Helper: Project Access Check

Create a reusable method that determines a user's effective access to a project:

```
getProjectAccess(projectId, userId, orgRole) → { canView, canEdit, canManageMembers, projectRole }
```

| Org Role | Project Member? | canView | canEdit | canManageMembers | projectRole |
|----------|----------------|---------|---------|------------------|-------------|
| Owner | Any | Yes | Yes | Yes | null or their project role |
| Admin | Any | Yes | Yes | Yes | null or their project role |
| Member | Lead | Yes | Yes | Yes | lead |
| Member | Member | Yes | No | No | member |
| Member | Not a member | No | No | No | null |

### Where to Enforce

- **`@PreAuthorize`**: Keep for org-level role checks (is user authenticated with a valid org?).
- **Service layer**: Add project membership checks after the org-level check passes. This keeps the controller clean and the authorization logic testable.

---

## Edge Cases

1. **Admin creates a project** — They become project lead AND retain admin privileges. Their project role is `lead`.
2. **Owner deletes a project** — All memberships cascade-delete. No special handling needed.
3. **User removed from org** (via Clerk) — Their project memberships become orphaned but harmless (they can't authenticate). Consider a cleanup webhook handler for `organizationMembership.deleted` that removes the user from all project_members rows.
4. **Last member leaves** — Prevent if they're the lead. A project must always have a lead.
5. **Admin demoted to member** (via Clerk) — They lose admin override. They can only see projects they're explicitly a member of. No automatic project membership changes needed.
6. **Existing projects after migration** — Backfill `created_by` user as `lead`. If `created_by` user no longer exists in the org, assign the org owner as lead.

---

## Out of Scope

- **Project-level roles beyond lead/member** (e.g., reviewer, viewer) — Keep it simple with two roles for now.
- **Bulk member management** (add/remove multiple at once) — Single operations only for MVP.
- **Permission inheritance** (project settings inherited from org settings) — Not needed.
- **Notifications** (email when added to a project) — Future enhancement.
- **Activity log** (who added/removed whom) — Future enhancement.

---

## Testing Requirements

### Backend Integration Tests

1. Member creates a project → becomes lead, project appears in their list.
2. Member not on a project → `GET /api/projects` excludes it, `GET /api/projects/{id}` returns 404.
3. Project lead adds a member → member can now see the project and upload documents.
4. Project lead removes a member → member can no longer see the project.
5. Admin can view all projects regardless of membership.
6. Owner can view all projects regardless of membership.
7. Project lead can update project, regular member cannot.
8. Lead transfer works: old lead becomes member, new lead becomes lead.
9. Cannot remove the project lead without transferring lead first.
10. Document access respects project membership (non-member gets 404 on upload-init).
11. Migration backfill: existing projects get `created_by` user as lead.

### Frontend Tests

1. Member sees "New Project" button.
2. Member sees only their projects in the list.
3. Project members panel renders with correct roles.
4. Add/remove member dialogs work for lead/admin/owner.
5. Non-lead members don't see edit/member-management controls.
