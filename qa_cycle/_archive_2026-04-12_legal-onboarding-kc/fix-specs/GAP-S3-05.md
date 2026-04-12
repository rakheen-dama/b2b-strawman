# Fix Spec: GAP-S3-05 — /projects/new crashes with "Parameter 'id' should be of type UUID"

## Priority
MEDIUM — deep-linked "New Matter for this client" is broken. Workaround exists (`/projects` →
"New from Template" dialog).

## Problem
Navigating to `http://localhost:3000/org/{slug}/projects/new?customerId=<uuid>` renders a
Next.js ErrorBoundary with "Something went wrong — Unable to load this page". Console:
`ApiError: Parameter 'id' should be of type UUID at apiRequest (ProjectDetailPage)`. The `new`
segment is parsed as a `[projectId]` dynamic param by the project detail page.

## Root Cause (confirmed via grep)
Files:
- `frontend/app/(app)/org/[slug]/projects/` — directory contents:
  `[id]/`, `actions.ts`, `error.tsx`, `loading.tsx`, `page.tsx`, `view-actions.ts`. **There is
  no `new/` directory.** The URL `/projects/new` therefore matches `/projects/[id]` with
  `id = "new"`, which the detail page tries to pass to a UUID-typed API call.
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — attempts `api.get('/api/projects/new')`
  which the backend rejects with `Parameter 'id' should be of type UUID`.

## Fix Steps
1. Create `frontend/app/(app)/org/[slug]/projects/new/page.tsx` — a Server Component that:
   - Accepts optional `customerId` and `templateId` query params
   - Redirects to `/org/{slug}/projects?new=1[&customerId=...][&templateId=...]` using
     `redirect()` from `next/navigation`
   OR
   - Renders a page with the "New from Template" dialog pre-opened, populated with the
     customer from the query param.
   The redirect approach is smaller and matches the existing workflow — the `/projects` list
   page already has a "New from Template" button that opens the dialog.
2. In `frontend/app/(app)/org/[slug]/projects/page.tsx`, add an effect / URL param listener that
   auto-opens the New-from-Template dialog when `?new=1` is present in the URL, and
   pre-populates the customer field if `customerId` is also present.
3. Update any client-detail "New Matter" button that currently links to `/projects/new` to use
   the new route pattern.

## Scope
- Frontend only
- Files to modify:
  - `frontend/app/(app)/org/[slug]/projects/page.tsx` (URL param → dialog auto-open)
  - Any customer-detail component with a "New Matter" button (grep for `projects/new`)
- Files to create:
  - `frontend/app/(app)/org/[slug]/projects/new/page.tsx`
- Migration needed: no

## Verification
1. Navigate directly to `/org/mathebula-partners/projects/new?customerId=<lerato-uuid>` —
   should redirect to `/org/mathebula-partners/projects` with the New-from-Template dialog
   open and Lerato pre-selected.
2. Re-run Session 3 step 3.17 without the workaround — the direct link should succeed.
3. Error page should no longer fire.

## Estimated Effort
S (< 30 min)
