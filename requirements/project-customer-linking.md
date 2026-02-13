# Project-Side Customer Linking

## Problem

Customer-project linking is currently one-directional in the UI. Users can only link projects to customers from the **customer detail page** (`/org/[slug]/customers/[id]`). There is no way to see or manage which customers are associated with a project from the **project detail page** (`/org/[slug]/projects/[id]`).

This forces project admins and leads into an unintuitive workflow: leave the project they're working on, navigate to the Customers section, find the correct customer, and link from there. The reverse flow — "I'm on this project and want to associate a customer" — is the more natural interaction for project-focused users.

## Current State

### Backend

| Endpoint | Controller | Method | Purpose |
|----------|-----------|--------|---------|
| `POST /api/customers/{id}/projects/{projectId}` | `CustomerController` | Link | Customer-centric linking |
| `DELETE /api/customers/{id}/projects/{projectId}` | `CustomerController` | Unlink | Customer-centric unlinking |
| `GET /api/customers/{id}/projects` | `CustomerController` | List | Projects for a customer |
| `GET /api/projects/{projectId}/customers` | `ProjectCustomerController` | List | Customers for a project (read-only) |

The `CustomerProjectService` already supports all operations bidirectionally — `linkCustomerToProject`, `unlinkCustomerFromProject`, and `listCustomersForProject` all exist and work correctly. The service layer is complete; only the controller and frontend are missing.

### Frontend

- **Customer detail page**: Has `CustomerProjectsPanel` (table of linked projects with unlink button) and `LinkProjectDialog` (searchable project picker using cmdk `Command`).
- **Project detail page**: Has `ProjectTabs` with 5 tabs (Documents, Members, Tasks, Time, Activity). **No customer panel exists.**

### Permissions (already enforced in `CustomerProjectService.requireLinkPermission`)

| Role | Can link/unlink | Can view linked customers |
|------|:--------------:|:------------------------:|
| Org Owner | Yes | Yes |
| Org Admin | Yes | Yes |
| Project Lead | Yes (own projects) | Yes (own projects) |
| Org Member | No | Yes (projects they can view) |

## Requirements

### 1. Backend: Extend `ProjectCustomerController`

Add link and unlink endpoints to the existing `ProjectCustomerController` at `/api/projects/{projectId}/customers`. These delegate to the existing `CustomerProjectService` methods.

**New endpoints:**

- `POST /api/projects/{projectId}/customers/{customerId}` — Link a customer to the project. Returns `201 Created` with the link details. Permission: org admin/owner or project lead.
- `DELETE /api/projects/{projectId}/customers/{customerId}` — Unlink a customer from the project. Returns `204 No Content`. Permission: org admin/owner or project lead.

Both endpoints should use `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")` at the controller level (matching the existing `CustomerController` pattern), with finer-grained permission checking delegated to `CustomerProjectService.requireLinkPermission`.

The existing `GET /api/projects/{projectId}/customers` endpoint in `ProjectCustomerController` remains unchanged.

**No new service logic is needed** — the controller methods should call `customerProjectService.linkCustomerToProject(customerId, projectId, memberId, memberId, orgRole)` and `customerProjectService.unlinkCustomerFromProject(customerId, projectId, memberId, orgRole)` respectively.

### 2. Frontend: Add "Customers" tab to Project Detail

Add a **Customers** tab to the `ProjectTabs` component on the project detail page. This tab shows which customers are linked to the project and allows linking/unlinking (for users with permission).

#### 2a. `ProjectTabs` modification

- Add a sixth tab: `{ id: "customers", label: "Customers" }`.
- Accept a new `customersPanel` prop.
- Place the Customers tab between Members and Tasks (logical grouping: Documents, Members, Customers, Tasks, Time, Activity).

#### 2b. `ProjectCustomersPanel` component

Create `components/projects/project-customers-panel.tsx` — a client component mirroring the structure of `CustomerProjectsPanel` but from the project's perspective.

Props:
- `customers: Customer[]` — the currently linked customers (fetched by server component)
- `slug: string` — org slug for navigation links
- `projectId: string` — for API calls
- `canManage: boolean` — controls whether link/unlink actions are shown

Features:
- Table displaying linked customers: Name (clickable link to `/org/{slug}/customers/{id}`), Email, Status, and an unlink (X) button for managers.
- "Link Customer" button (top-right, visible when `canManage` is true) that opens the `LinkCustomerDialog`.
- Empty state: "No linked customers — Link customers to this project to track client work."

#### 2c. `LinkCustomerDialog` component

Create `components/projects/link-customer-dialog.tsx` — a dialog mirroring `LinkProjectDialog` but for selecting customers.

- On open, fetches all customers via `GET /api/customers`.
- Filters out already-linked customers.
- Uses cmdk `Command` component for searchable selection (search by name + email).
- On select, calls `POST /api/projects/{projectId}/customers/{customerId}` via a server action.
- Closes on success, shows error on failure.

#### 2d. Server actions

Add server actions in `app/(app)/org/[slug]/projects/[id]/actions.ts`:
- `fetchCustomers()` — calls `GET /api/customers` and returns the list.
- `linkCustomerToProject(slug: string, projectId: string, customerId: string)` — calls `POST /api/projects/{projectId}/customers/{customerId}`, revalidates the page.
- `unlinkCustomerFromProject(slug: string, projectId: string, customerId: string)` — calls `DELETE /api/projects/{projectId}/customers/{customerId}`, revalidates the page.

#### 2e. Project detail page data fetching

In `app/(app)/org/[slug]/projects/[id]/page.tsx`, add a data fetch for linked customers:

```typescript
let customers: Customer[] = [];
try {
  customers = await api.get<Customer[]>(`/api/projects/${id}/customers`);
} catch {
  // Non-fatal: show empty customers list if fetch fails
}
```

Pass to `ProjectTabs`:
```tsx
<ProjectTabs
  customersPanel={
    <ProjectCustomersPanel
      customers={customers}
      slug={slug}
      projectId={id}
      canManage={canManage}
    />
  }
  // ... existing panels
/>
```

### 3. Types

Add a `Customer` type to `lib/types.ts` if not already present:

```typescript
export interface Customer {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  idNumber: string | null;
  status: string;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}
```

### 4. Tests

#### Backend
- Add tests to `ProjectCustomerController` for the new POST and DELETE endpoints:
  - Link a customer to a project — verify 201 response with link details.
  - Attempt to link an already-linked customer — verify 409 Conflict.
  - Unlink a customer — verify 204 No Content.
  - Attempt to unlink a non-linked customer — verify 404.
  - Permission: org member (non-lead) attempts to link — verify 404 (security-by-obscurity via `requireEditAccess`).
  - Permission: project lead can link/unlink.
  - Permission: admin/owner can link/unlink.

#### Frontend
- `ProjectCustomersPanel` tests:
  - Renders linked customers in a table.
  - Shows "Link Customer" button when `canManage` is true.
  - Hides "Link Customer" button and unlink buttons when `canManage` is false.
  - Empty state displays when no customers are linked.
- `LinkCustomerDialog` tests:
  - Opens and displays searchable customer list.
  - Filters out already-linked customers.
  - Calls link action on selection.

## Out of Scope

- Bulk link/unlink operations.
- Customer creation from the project detail page (use the Customers section for that).
- Changing the existing customer-side linking UI — both directions should work independently.
- Search/filter on the customers tab (premature for typical project-customer cardinality).
