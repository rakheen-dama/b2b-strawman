# Fix Spec: GAP-008C — Projects page JS error on first load (TypeError: null ref)

## Problem
The Projects page occasionally crashes with `TypeError: Cannot read properties of null` on first load. The error is intermittent — refreshing the page usually resolves it. This is a race condition, non-cascading. Observed on Day 0.

## Root Cause (hypothesis)
This is likely the same class of null-dereference issue as GAP-027, but on the Projects page. A data field returned from the API is null when the rendering code expects it to be populated. Since it's intermittent, it may be a timing issue with the SSR data fetch — possibly a stale cache entry or a race between `revalidatePath` and the next SSR render.

## Fix
Add an `error.tsx` boundary at the projects route level to catch SSR errors gracefully. Additionally, audit the projects page for unguarded null accesses.

### Step 1: Create error boundary
**File to create**: `frontend/app/(app)/org/[slug]/projects/error.tsx`

```typescript
"use client";
export default function ProjectsError({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <h2 className="font-display text-xl">Something went wrong</h2>
      <p className="text-sm text-slate-600">Unable to load projects. Please try again.</p>
      <button onClick={reset} className="text-sm text-teal-600 hover:underline">Try again</button>
    </div>
  );
}
```

### Step 2: Audit projects page for null dereferences
**File**: `frontend/app/(app)/org/[slug]/projects/page.tsx`
Review any property accesses on data from API responses that might be null.

## Scope
Frontend only.

Files to create:
- `frontend/app/(app)/org/[slug]/projects/error.tsx`

Files to modify:
- `frontend/app/(app)/org/[slug]/projects/page.tsx` (audit for null guards)

Migration needed: no

## Verification
Navigate to Projects page multiple times, verify no crash on first load.

## Estimated Effort
S (< 30 min) — error boundary + null guard audit
