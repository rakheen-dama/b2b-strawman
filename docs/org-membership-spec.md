# Members & Project Ownership

## Problem

Currently, org members exist only in Clerk — there's no representation of members in our database. Clerk user IDs are scattered as raw strings (`projects.created_by`, `documents.uploaded_by`) with no referential integrity, no way to query "what does this member own," and no foundation for resource-level access control.

Additionally, all org members see all projects. There is no concept of project-level access — a member either has access to everything in the org or nothing.

## Goal

1. **Establish members as first-class entities** in the tenant database, synced from Clerk via webhooks. All resource ownership flows through the `members` table.
2. **Introduce project-level membership** so org members only see projects they belong to, while admins and owners retain full visibility.

---

## Current State

### Member Identity

- Members exist only in Clerk. No `members` table in the database.
- `projects.created_by` and `documents.uploaded_by` store Clerk user IDs as raw strings — no FK constraints, no joins, no referential integrity.
- Webhook handlers for `organizationMembership.*` events are **stubbed as no-ops** (see `frontend/lib/webhook-handlers.ts` lines 106-109).
- Display names require Clerk API calls at render time — no local cache.

### Project Access

| Operation | Member | Admin | Owner |
|-----------|--------|-------|-------|
| List projects | All projects | All projects | All projects |
| View project | Any project | Any project | Any project |
| Create project | No | Yes | Yes |
| Update project | No | Any project | Any project |
| Delete project | No | No | Any project |
| Upload documents | Any project | Any project | Any project |

---

## Target State

### Project Access

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

All tables below are **per-tenant schema** (alongside existing `projects` and `documents`).

### New Table: `members`

The foundational table. Every user in the org gets a row, synced from Clerk via webhooks.

```
members
├── id              UUID, PK
├── clerk_user_id   VARCHAR(255), NOT NULL, UNIQUE
├── email           VARCHAR(255), NOT NULL
├── name            VARCHAR(255)
├── avatar_url      VARCHAR(1000)
├── org_role        VARCHAR(50), NOT NULL       -- 'owner', 'admin', 'member' (synced from Clerk)
├── created_at      TIMESTAMPTZ, NOT NULL, DEFAULT now()
└── updated_at      TIMESTAMPTZ, NOT NULL, DEFAULT now()
```

**Purpose:**
- Single source of truth for member identity within the tenant database.
- All resource tables FK to `members(id)` instead of storing raw Clerk user IDs.
- Cached name/email/avatar eliminates Clerk API calls for display.
- `org_role` synced from Clerk — used for authorization alongside JWT claims.

### New Table: `project_members`

Junction table for project-level access control.

```
project_members
├── id              UUID, PK
├── project_id      UUID, FK → projects(id) ON DELETE CASCADE
├── member_id       UUID, FK → members(id) ON DELETE CASCADE
├── project_role    VARCHAR(50), NOT NULL    -- 'lead' or 'member'
├── added_by        UUID, FK → members(id)  -- who added them
├── created_at      TIMESTAMPTZ, NOT NULL, DEFAULT now()
└── UNIQUE(project_id, member_id)
```

**Project roles:**
- `lead` — The project creator (or transferred lead). Can manage project settings and membership.
- `member` — Can view the project and upload/download documents.

**Constraints:**
- Each project must have exactly one lead at all times.
- A user can be a member of multiple projects.
- When a project is deleted, all project memberships cascade-delete.
- When a member is removed from the org, all their project memberships cascade-delete.

### Modify Table: `projects`

- Change `created_by` from `VARCHAR(255)` (Clerk user ID string) to `UUID FK → members(id)`.

### Modify Table: `documents`

- Change `uploaded_by` from `VARCHAR(255)` (Clerk user ID string) to `UUID FK → members(id)`.

### Migrations

**`V3__create_members.sql`** — Create `members` table.

**`V4__create_project_members.sql`** — Create `project_members` table, alter `projects.created_by` and `documents.uploaded_by` to FK to `members(id)`.

**Migration backfill logic:**
1. For each unique `created_by` / `uploaded_by` Clerk user ID in existing data, insert a `members` row (use Clerk API to populate name/email, or insert with placeholder and let webhook sync fill it).
2. Update `projects.created_by` and `documents.uploaded_by` to reference the new `members(id)` UUIDs.
3. For each existing project, insert a `project_members` row with `project_role='lead'` for the `created_by` member.

---

## Webhook Changes

The existing no-op stubs in `frontend/lib/webhook-handlers.ts` become real handlers.

### `organizationMembership.created`

When a member joins the org (via invitation acceptance or direct add):
1. Call `POST /internal/members/sync` with the Clerk user data.
2. Backend upserts a `members` row in the tenant schema.

**Clerk event payload provides:** `user_id` (Clerk user ID), `role`, `organization.id`
**To get name/email:** Use `clerkClient.users.getUser(userId)` in the webhook handler or pass it from the event's `public_user_data`.

### `organizationMembership.updated`

When a member's org role changes (e.g., member → admin):
1. Call `PUT /internal/members/sync` with updated role.
2. Backend updates `members.org_role`.

### `organizationMembership.deleted`

When a member is removed from the org:
1. Call `DELETE /internal/members/{clerkUserId}`.
2. Backend deletes the `members` row → cascades to all `project_members` rows.

### New Internal API Endpoints

**`POST /internal/members/sync`** — Upsert a member (API-key auth, called by webhook handler).
- Request: `{ clerkUserId, email, name, avatarUrl, orgRole }`
- Idempotent: ON CONFLICT (clerk_user_id) DO UPDATE.

**`PUT /internal/members/sync`** — Update member fields (role change, name/email change).
- Request: `{ clerkUserId, email?, name?, avatarUrl?, orgRole? }`

**`DELETE /internal/members/{clerkUserId}`** — Remove member from tenant schema.
- Cascade deletes all `project_members` rows for this member.

---

## API Changes

### Modified Endpoints

**`GET /api/projects`** — List projects
- **Member**: Return only projects where the user has a `project_members` row.
- **Admin/Owner**: Return all projects (unchanged).
- Response includes the user's project role (`lead`, `member`, or `null` for admin/owner viewing non-member projects).

**`POST /api/projects`** — Create project
- **Change**: Allow `ORG_MEMBER` role (currently restricted to admin+).
- Resolve the JWT `sub` claim to `members(id)`.
- After creating the project, insert a `project_members` row with `project_role='lead'`.

**`GET /api/projects/{id}`** — Get project
- **Member**: Return 404 if user is not a project member.
- **Admin/Owner**: Return project (unchanged).

**`PUT /api/projects/{id}`** — Update project
- **Project lead**: Allow.
- **Admin/Owner**: Allow (unchanged).
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
- **Response**: Array of `{ id, name, email, avatarUrl, projectRole, createdAt }` (joined from `members` table — no Clerk API call needed).

**`POST /api/projects/{id}/members`** — Add member to project
- **Access**: Project lead, admin, owner.
- **Request body**: `{ memberId: UUID }` (references `members(id)`, not Clerk user ID).
- **Validation**: Member must exist in the `members` table (i.e., active org member).
- **Default role**: `member`.
- **Idempotent**: If already a project member, return 409 Conflict.

**`DELETE /api/projects/{id}/members/{memberId}`** — Remove member from project
- **Access**: Project lead (cannot remove self), admin, owner.
- **Validation**: Cannot remove the project lead (must transfer lead first).

**`PUT /api/projects/{id}/members/{memberId}/role`** — Transfer lead role
- **Access**: Current project lead, owner.
- **Request body**: `{ role: "lead" }`
- **Effect**: Demotes current lead to `member`, promotes target to `lead` (single transaction).
- **Validation**: Target must be an existing project member.

**`GET /api/members`** — List org members
- **Access**: Any authenticated org member.
- **Response**: Array of `{ id, name, email, avatarUrl, orgRole }`.
- **Purpose**: Populate the "add member to project" picker — shows org members available to add.

---

## Frontend Changes

### Projects List Page (`/org/[slug]/projects`)

- All members see a "New Project" button (not just admins).
- Members see only their projects. Admins/owners see all projects.
- Each project card shows the user's project role badge (`Lead`, `Member`), or nothing for admins viewing non-member projects.

### Project Detail Page (`/org/[slug]/projects/[id]`)

- Add a **"Members" tab or panel** alongside the existing documents panel.
- Members panel shows:
  - List of project members with name, avatar, and role badge — rendered from API response (no Clerk API calls).
  - "Add Member" button (visible to lead/admin/owner) — dialog with searchable org member picker (from `GET /api/members`, filtered to exclude current project members).
  - "Remove" button next to each member (visible to lead/admin/owner, not on the lead's own row).
  - "Transfer Lead" action (visible to current lead, on member rows).
- Edit project button: visible to lead, admin, owner.
- Upload documents: visible to all project members, admin, owner.

### Dashboard (`/org/[slug]/dashboard`)

- "Recent Projects" section: show only the user's projects (or all for admin/owner).
- Project count stat: reflect filtered count.

---

## Authorization Logic (Backend)

### Member Resolution

Every authenticated request needs to resolve the JWT's Clerk user ID to a `members(id)`. This should be done once per request, similar to how `TenantFilter` resolves the org:

1. `TenantFilter` sets the tenant schema (existing).
2. New: **`MemberFilter`** resolves JWT `sub` → `members(id)`, stores in `MemberContext` (ThreadLocal, similar to `TenantContext`).
3. Service layer uses `MemberContext.getCurrentMemberId()` for ownership checks and FK population.

Cache the `clerk_user_id → member_id` mapping (e.g., Caffeine cache per tenant) to avoid a DB lookup on every request.

### Project Access Check

Reusable service method:

```
getProjectAccess(projectId, memberId, orgRole) → { canView, canEdit, canManageMembers, projectRole }
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
2. **Owner deletes a project** — All project memberships cascade-delete. No special handling needed.
3. **User removed from org** (Clerk webhook `organizationMembership.deleted`) — Webhook handler deletes their `members` row → cascades to all `project_members` rows. Clean removal.
4. **Last member leaves** — Prevent if they're the lead. A project must always have a lead.
5. **Admin demoted to member** (Clerk webhook `organizationMembership.updated`) — Webhook updates `members.org_role`. They lose admin override — can only see projects they're explicitly a member of.
6. **Existing projects after migration** — Backfill `created_by` user as `lead`. If `created_by` user no longer exists in the org, assign the org owner as lead.
7. **User changes name/email in Clerk** — Handle via `organizationMembership.updated` or add a `user.updated` webhook to keep the cache fresh.
8. **Webhook arrives before tenant is provisioned** — The `organizationMembership.created` event may fire before `organization.created` finishes provisioning. The internal API endpoint should handle this gracefully (e.g., retry or queue).

---

## Out of Scope

- **Project-level roles beyond lead/member** (e.g., reviewer, viewer) — Keep it simple with two roles for now.
- **Bulk member management** (add/remove multiple at once) — Single operations only for MVP.
- **Permission inheritance** (project settings inherited from org settings) — Not needed.
- **Notifications** (email when added to a project) — Future enhancement.
- **Activity log** (who added/removed whom) — Future enhancement.
- **Member profiles page** — No dedicated member profile page within the app for MVP.

---

## Implementation Order

This is the suggested build sequence. Each step should be independently deployable and testable.

1. **Members table + webhook sync** — Create `members` table, implement internal API endpoints, wire up `organizationMembership.*` webhook handlers. Backfill existing users.
2. **MemberFilter + MemberContext** — Add request-level member resolution (JWT sub → member ID). Migrate `projects.created_by` and `documents.uploaded_by` to FK → `members(id)`.
3. **Project members table + API** — Create `project_members` table, implement CRUD endpoints for project membership. Backfill existing projects.
4. **Project access control** — Modify project and document endpoints to enforce project membership. Allow members to create projects.
5. **Frontend: project members panel** — Add members panel to project detail page with add/remove/transfer UI.
6. **Frontend: filtered project list** — Update projects list and dashboard to show only the user's projects (members) or all projects (admin/owner).

---

## Testing Requirements

### Backend Integration Tests

1. Webhook: `organizationMembership.created` → member row created in tenant schema.
2. Webhook: `organizationMembership.deleted` → member row deleted, project memberships cascade.
3. Webhook: `organizationMembership.updated` → `org_role` updated in members table.
4. `MemberFilter` resolves JWT `sub` to `members(id)` correctly.
5. Member creates a project → becomes lead, project appears in their list.
6. Member not on a project → `GET /api/projects` excludes it, `GET /api/projects/{id}` returns 404.
7. Project lead adds a member → member can now see the project and upload documents.
8. Project lead removes a member → member can no longer see the project.
9. Admin can view all projects regardless of membership.
10. Owner can view all projects regardless of membership.
11. Project lead can update project, regular member cannot.
12. Lead transfer works: old lead becomes member, new lead becomes lead.
13. Cannot remove the project lead without transferring lead first.
14. Document access respects project membership (non-member gets 404 on upload-init).
15. Migration backfill: existing projects get `created_by` user as lead.
16. `GET /api/projects/{id}/members` returns member details without Clerk API calls.

### Frontend Tests

1. Member sees "New Project" button.
2. Member sees only their projects in the list.
3. Project members panel renders with correct names, avatars, and roles.
4. Add member dialog shows org members not already on the project.
5. Remove member works for lead/admin/owner.
6. Non-lead members don't see edit/member-management controls.
