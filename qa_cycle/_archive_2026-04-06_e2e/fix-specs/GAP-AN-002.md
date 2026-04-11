# Fix Spec: GAP-AN-002 — Rule row click doesn't open edit form or detail page

## Problem
Clicking a rule row in the automations list does nothing -- no navigation to the rule detail/edit page at `/settings/automations/[id]`. The backend PUT endpoint works correctly. The destination page (`/settings/automations/[id]/page.tsx`) exists and works when navigated to directly. The QA agent confirmed all 11 seeded rules are visible but none are clickable.

## Root Cause
The row click handler in `rule-list.tsx` (line 91-93, used at line 171) uses `onClick={() => handleRowClick(rule.id)}` on the `<TableRow>`, which calls `router.push()`. This is the same JavaScript-only navigation pattern identified in GAP-AN-001. If client-side hydration fails or `useRouter()` is not properly initialized, the click handler is inert.

Additionally, using `onClick` on a `<tr>` element is semantically incorrect -- table rows are not interactive elements. Screen readers and keyboard navigation cannot follow these links. Converting to proper `<Link>` elements inside the row provides both progressive enhancement and better accessibility.

**Confirmed by code reading:**
- `frontend/components/automations/rule-list.tsx` line 91-93: `handleRowClick` uses `router.push()`
- `frontend/components/automations/rule-list.tsx` line 170-171: `<TableRow onClick={() => handleRowClick(rule.id)}>`
- `frontend/app/(app)/org/[slug]/settings/automations/[id]/page.tsx`: Destination page exists, loads the rule, renders `RuleDetailClient`

## Fix
Wrap the rule name cell content in a `<Link>` for progressive navigation. Keep the `onClick` on the row as a secondary enhancement (for clicking anywhere in the row), but ensure the primary click target (the rule name) works without JS.

### Step 1: In `frontend/components/automations/rule-list.tsx`

**Modify the first `<TableCell>` inside the rules map (lines 174-185)** to wrap the rule name in a `<Link>`:

Change from:
```tsx
<TableCell>
  <div>
    <p className="font-medium text-slate-950 dark:text-slate-50">
      {rule.name}
    </p>
    {rule.description && (
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {rule.description}
      </p>
    )}
  </div>
</TableCell>
```

To:
```tsx
<TableCell>
  <Link
    href={`/org/${slug}/settings/automations/${rule.id}`}
    className="block"
    onClick={(e) => e.stopPropagation()}
  >
    <p className="font-medium text-slate-950 dark:text-slate-50 hover:text-teal-600 dark:hover:text-teal-400">
      {rule.name}
    </p>
    {rule.description && (
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {rule.description}
      </p>
    )}
  </Link>
</TableCell>
```

The `e.stopPropagation()` prevents the `<Link>` click from also triggering the row's `onClick` (which would cause a duplicate navigation).

**Ensure `Link` is imported** (same import as GAP-AN-001 fix):
```tsx
import Link from "next/link";
```

## Scope
Frontend only
Files to modify:
- `frontend/components/automations/rule-list.tsx`

## Verification
Re-run QA checkpoint T1.3 — click a rule row and verify navigation to the rule detail/edit page.

## Estimated Effort
S (< 30 min) — wrapping existing content in a Link component.
