# Fix Spec: GAP-PORTAL-07 — Branding orgId Not Read from URL Params

## Problem

The portal login page at `/portal` supports branding (org name, logo, brand color, footer text) via the `GET /portal/branding?orgId=...` API. Branding works correctly after the user types an organization slug into the form field (debounced 500ms fetch). However, when the URL contains `?orgId=e2e-test-org`, the orgId is not consumed:

1. The organization field is not pre-populated
2. Branding is not auto-fetched on page load
3. The user must manually type the org slug to see branded login

This breaks the intended flow where a firm can share a branded portal link like `https://portal.docteams.app/portal?orgId=my-firm`.

## Root Cause (confirmed by code review)

File: `frontend/app/portal/page.tsx`

Line 29: `const [orgSlug, setOrgSlug] = useState("");`

The `orgSlug` state is always initialized to an empty string. The component never reads `useSearchParams()` to extract `orgId` from the URL.

The branding fetch logic (lines 67-72) already watches `orgSlug` changes and auto-fetches when the value is non-empty:

```tsx
useEffect(() => {
  if (orgSlug.trim() && lastFetchedOrg.current !== orgSlug.trim()) {
    const timer = setTimeout(() => fetchBranding(orgSlug), 500);
    return () => clearTimeout(timer);
  }
}, [orgSlug, fetchBranding]);
```

So if `orgSlug` were initialized from the URL param, branding would auto-fetch on mount via this existing effect. No new fetch logic needed.

## Fix

### Step 1: Import `useSearchParams` and read `orgId` from URL

In `frontend/app/portal/page.tsx`:

1. Add `useSearchParams` to the import from `next/navigation` (line 4):
   ```tsx
   import { useRouter, useSearchParams } from "next/navigation";
   ```

2. After the router declaration (line 26), add:
   ```tsx
   const searchParams = useSearchParams();
   ```

3. Change line 29 from:
   ```tsx
   const [orgSlug, setOrgSlug] = useState("");
   ```
   to:
   ```tsx
   const [orgSlug, setOrgSlug] = useState(() => searchParams.get("orgId") ?? "");
   ```

That is the entire fix. The existing `useEffect` on lines 67-72 will detect that `orgSlug` is non-empty on mount and trigger `fetchBranding()` after the 500ms debounce. The org input field will show the pre-populated value.

### Step 2: Wrap page in Suspense boundary (Next.js requirement)

Next.js requires components using `useSearchParams()` to be wrapped in a `<Suspense>` boundary. The page is already a `"use client"` component, but it needs a Suspense wrapper. Two options:

**Option A (preferred)**: Extract the login form into a separate client component and wrap it in Suspense in the page:

This is overkill for this fix since the entire page is already `"use client"`. Instead:

**Option B**: Add a Suspense boundary in the parent layout or simply ensure the component handles the loading state. Since `useSearchParams()` in a client component that's the page itself, Next.js 16 may log a warning but will still work. If a build warning appears, wrap the page content in `<Suspense>`:

```tsx
import { Suspense } from "react";

function PortalLoginContent() {
  // ... existing component body
}

export default function PortalLoginPage() {
  return (
    <Suspense fallback={null}>
      <PortalLoginContent />
    </Suspense>
  );
}
```

However, check first if Next.js 16 still requires this. The simpler inline approach (Option A with just the `useState` initializer) should be tried first.

## Scope

| File | Change |
|------|--------|
| `frontend/app/portal/page.tsx` | Import `useSearchParams`, read `orgId` param, initialize `orgSlug` state |

**Single file change. No backend changes.**

## Verification

1. Rebuild frontend: `bash compose/scripts/e2e-rebuild.sh frontend`
2. Navigate to `http://localhost:3001/portal?orgId=e2e-test-org`
3. Verify: Organization field is pre-populated with "e2e-test-org"
4. Verify: After ~500ms, branding loads (heading changes to "E2E Test Organization Portal", brand color stripe appears)
5. Navigate to `http://localhost:3001/portal` (no param)
6. Verify: Organization field is empty, generic "DocTeams Portal" heading (no regression)
7. Verify: 0 console errors in both scenarios

## Estimated Effort

**XS** (< 15 minutes). Three-line change in a single file.
