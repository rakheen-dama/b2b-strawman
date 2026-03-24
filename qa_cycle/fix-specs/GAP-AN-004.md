# Fix Spec: GAP-AN-004 — "View Execution Log" link doesn't navigate

## Problem
The "View Execution Log" link on the automations settings page does not navigate when clicked. However, navigating directly to `/org/{slug}/settings/automations/executions` via the address bar works correctly -- the execution log page loads with all entries displayed accurately.

## Root Cause
The "View Execution Log" link in `page.tsx` (lines 53-58) is a standard Next.js `<Link>` component rendered from a Server Component:

```tsx
<Link
  href={`/org/${slug}/settings/automations/executions`}
  className="mt-2 inline-flex items-center gap-1 text-sm text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
>
  View Execution Log
</Link>
```

This is NOT inside the `RuleList` client component -- it's in the parent server component. A `<Link>` renders as an `<a>` tag with an `href`, so it should work for both server-rendered HTML (full-page navigation) and client-side SPA navigation.

**The link itself is structurally correct.** The most likely cause is one of:

1. **Accessibility tree**: The link lacks any visual affordance (no underline, no arrow icon) that distinguishes it from plain text. The teal color alone may not be sufficient. The QA agent may have been clicking near but not on the actual link text, or the test automation tool's element detection missed it.

2. **`PageTransition` interference**: The `<PageTransition>` wrapper in the layout uses Framer Motion's `AnimatePresence mode="wait"`. If the exit animation stalls (e.g., due to a rendering error in the exiting page), navigation can appear to hang.

3. **`ErrorBoundary` catching an error**: If the new page throws during server render (unlikely since direct navigation works), the error boundary could swallow it.

## Fix
Improve the link's affordance and add a fallback navigation mechanism.

### Step 1: Add visual affordance to the link

In `frontend/app/(app)/org/[slug]/settings/automations/page.tsx`, add an arrow icon and underline behavior to make the link more visually distinct and ensure proper click targeting:

Change (lines 53-58):
```tsx
<Link
  href={`/org/${slug}/settings/automations/executions`}
  className="mt-2 inline-flex items-center gap-1 text-sm text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
>
  View Execution Log
</Link>
```

To:
```tsx
<Link
  href={`/org/${slug}/settings/automations/executions`}
  className="mt-2 inline-flex items-center gap-1 text-sm text-teal-600 underline-offset-4 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
>
  View Execution Log &rarr;
</Link>
```

### Step 2: Add a secondary link in the RuleList component

In `frontend/components/automations/rule-list.tsx`, add a "View Execution Log" link near the bottom of the rules table for better discoverability:

After the `</Table>` closing tag (line 231), add:
```tsx
{canManage && rules.length > 0 && (
  <div className="flex justify-end">
    <Link
      href={`/org/${slug}/settings/automations/executions`}
      className="text-sm text-teal-600 underline-offset-4 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
    >
      View Execution Log &rarr;
    </Link>
  </div>
)}
```

This provides a `<Link>` that works as a standard `<a>` tag inside the client component, giving an additional click target.

## Scope
Frontend only
Files to modify:
- `frontend/app/(app)/org/[slug]/settings/automations/page.tsx`
- `frontend/components/automations/rule-list.tsx` (optional secondary link)

## Verification
Re-run QA checkpoint T1.5.1 — click "View Execution Log" and verify navigation to the execution log page.

## Estimated Effort
S (< 30 min) — styling and adding one link element.
