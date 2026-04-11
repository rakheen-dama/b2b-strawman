# Fix Spec: GAP-AN-001 — "New Automation" button non-functional

## Problem
Clicking the "New Automation" button on the automations settings page does nothing -- no navigation to `/settings/automations/new`, no dialog, no error in console. The backend API for creating rules works correctly. The QA agent tested this on the Keycloak dev stack (port 3000) and confirmed the page itself loads fine but the button click produces no visible effect.

## Root Cause
The "New Automation" button in `rule-list.tsx` (line 110-111) uses `onClick={() => router.push(...)}` for navigation. This is a JavaScript-only navigation pattern that depends on:
1. Full client-side hydration of the `RuleList` component
2. The `useRouter()` hook being properly initialized

The `<Button>` renders as a `<button type="button">` element with no `href`. If client-side JavaScript fails to hydrate (e.g., due to a rendering error in any sibling component, a `motion/react` issue in `PageTransition`, or a timing issue), the button becomes inert -- it looks clickable but does nothing on click.

**The `/new` page exists and works** -- navigating directly to the URL works. The issue is the navigation mechanism, not the destination.

**Confirmed by code reading:**
- `frontend/components/automations/rule-list.tsx` line 108-116: Button uses `onClick={() => router.push(...)}`
- `frontend/app/(app)/org/[slug]/settings/automations/new/page.tsx`: Destination page exists and renders correctly
- Same pattern appears twice (lines 108-116 and 142-150 for the empty state button)

## Fix
Replace the JavaScript-only `onClick={router.push()}` pattern with a proper `<Link>` component for progressive enhancement. The button should work as an anchor link that navigates without requiring client-side JS.

### Step 1: In `frontend/components/automations/rule-list.tsx`

**Change the "New Automation" button (lines 108-116)** from:
```tsx
<Button
  size="sm"
  onClick={() =>
    router.push(`/org/${slug}/settings/automations/new`)
  }
>
  <Plus className="mr-1.5 size-4" />
  New Automation
</Button>
```

To:
```tsx
<Button size="sm" asChild>
  <Link href={`/org/${slug}/settings/automations/new`}>
    <Plus className="mr-1.5 size-4" />
    New Automation
  </Link>
</Button>
```

**Do the same for the empty-state button** (lines 142-150) -- same transformation.

**Add `Link` import** at top of file:
```tsx
import Link from "next/link";
```

**Remove `useRouter`** if no other code in the component uses it (check: `handleRowClick` at line 91-93 also uses `router.push` -- see GAP-AN-002 for that fix. If both are converted to `<Link>`, `useRouter` can be removed).

## Scope
Frontend only
Files to modify:
- `frontend/components/automations/rule-list.tsx`

## Verification
Re-run QA checkpoint T1.2 — click "New Automation" and verify navigation to the create rule page.

## Estimated Effort
S (< 30 min) — straightforward pattern change, two identical instances in the same file.
