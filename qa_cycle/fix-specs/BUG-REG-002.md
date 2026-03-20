# Fix Spec: BUG-REG-002 — Carol (Member) gets 500 on role-gated pages

## Problem
When Carol (Member role) navigates directly to role-gated pages (`/profitability`, `/reports`, `/customers`, `/settings/roles`), she sees a generic "Something went wrong" error page (500) instead of a proper permission denied message or 404. The sidebar correctly hides these links for the Member role, but direct URL access crashes. This affects AUTH-01 tests #4, #5, #8, and #10.

## Root Cause (confirmed)
The org layout at `frontend/app/(app)/org/[slug]/layout.tsx` wraps `{children}` inside a custom `ErrorBoundary` component (line 102). When a page server component calls `notFound()` from `next/navigation` to block unauthorized access, Next.js throws a special `NEXT_NOT_FOUND` error internally. This error is meant to be caught by the Next.js framework to render a 404 page.

However, the custom `ErrorBoundary` at `frontend/components/error-boundary.tsx` (line 68, `getDerivedStateFromError`) catches ALL errors indiscriminately — including the special `NEXT_NOT_FOUND` and `NEXT_REDIRECT` errors. It then renders the generic `ErrorFallback` component ("Something went wrong") instead of letting Next.js handle the 404.

**Affected pages** (all use `notFound()` for RBAC gating):
- `frontend/app/(app)/org/[slug]/profitability/page.tsx` (line 33)
- `frontend/app/(app)/org/[slug]/reports/page.tsx` (line 25)
- `frontend/app/(app)/org/[slug]/customers/page.tsx` (line 81)
- `frontend/app/(app)/org/[slug]/settings/roles/page.tsx` (line 23)

**Also potentially affected** (same `notFound()` pattern, not tested with Carol):
- `/compliance`, `/retainers`, `/invoices`, `/proposals`, `/resources`, `/schedules`, `/settings/automations`, `/settings/capacity`

**Key files:**
- `frontend/components/error-boundary.tsx` — `getDerivedStateFromError` on line 68 catches all errors
- `frontend/app/(app)/org/[slug]/layout.tsx` — ErrorBoundary wrapping children on line 102

## Fix

**Option A (Recommended): Fix the ErrorBoundary to re-throw Next.js internal errors.**

In `frontend/components/error-boundary.tsx`, modify `getDerivedStateFromError` and `componentDidCatch` to detect and re-throw Next.js internal errors:

```typescript
static getDerivedStateFromError(error: Error): ErrorBoundaryState {
  // Let Next.js handle its own internal errors (notFound, redirect)
  if (error.digest === "NEXT_NOT_FOUND" || error.digest === "NEXT_REDIRECT") {
    throw error;
  }
  return { hasError: true, error };
}
```

Note: In Next.js App Router, `notFound()` and `redirect()` throw errors with a `digest` property. The `digest` value for `notFound()` is `"NEXT_NOT_FOUND"` and for `redirect()` it's a string starting with `"NEXT_REDIRECT"`. The ErrorBoundary must not swallow these.

The `digest` property is set on the error object by Next.js. Since the `Error` type doesn't include `digest`, cast or access it via:
```typescript
static getDerivedStateFromError(error: Error): ErrorBoundaryState {
  const digest = (error as { digest?: string }).digest;
  if (digest === "NEXT_NOT_FOUND" || digest?.startsWith("NEXT_REDIRECT")) {
    throw error;
  }
  return { hasError: true, error };
}
```

**Option B (Alternative): Replace `notFound()` with a proper "Access Denied" component in affected pages.**

Instead of calling `notFound()`, render a custom permission-denied component:

```tsx
if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("FINANCIAL_VISIBILITY")) {
  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <ShieldAlert className="size-12 text-slate-400" />
      <h2 className="font-display text-xl text-slate-900">Access Restricted</h2>
      <p className="text-sm text-slate-600">
        You do not have permission to view this page.
      </p>
    </div>
  );
}
```

This approach is already used by the `/settings/rates` page (lines 19-36) and works correctly (AUTH-01 #3 PASS).

**Recommendation: Do both.** Fix the ErrorBoundary (Option A) as a safety net for ALL `notFound()` calls across the app, AND convert the 4 affected pages to use an inline permission-denied component (Option B) for a better UX than a 404.

## Scope
Frontend only.

Files to modify:
- `frontend/components/error-boundary.tsx` (Option A — critical fix)
- `frontend/app/(app)/org/[slug]/profitability/page.tsx` (Option B — better UX)
- `frontend/app/(app)/org/[slug]/reports/page.tsx` (Option B)
- `frontend/app/(app)/org/[slug]/customers/page.tsx` (Option B)
- `frontend/app/(app)/org/[slug]/settings/roles/page.tsx` (Option B)

Files to create: none
Migration needed: no

## Verification
Re-run AUTH-01 #4, #5, #8, #10 as Carol (Member). Each page should show either:
- A proper 404 page (if Option A only)
- A "You do not have permission" message (if Option B applied)

Neither should show "Something went wrong" 500 error.

## Estimated Effort
S (< 30 min) — Option A is a 5-line change in the ErrorBoundary. Option B is a copy-paste of the existing pattern from the rates page to 4 other pages.

## Cascading Bug Assessment
This is a **cascading bug** because the ErrorBoundary issue affects ALL pages that use `notFound()` for RBAC (at least 14 pages confirmed). However, it only manifests when a user without the required capability navigates directly to a URL — the sidebar correctly hides the links. Escalating severity from MEDIUM to HIGH is warranted but not to BLOCKER because:
1. The sidebar correctly prevents normal navigation
2. Only direct URL access is affected
3. QA can work around it by not testing Carol's direct URL access to gated pages
