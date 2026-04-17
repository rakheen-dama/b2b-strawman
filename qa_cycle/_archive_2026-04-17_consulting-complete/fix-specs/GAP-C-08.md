# Fix Spec: GAP-C-08 — /trust-accounting throws error boundary instead of "Module Not Available"

## Problem

Day 0 checkpoint 0.57: on a consulting-za tenant (trust_accounting module disabled), visiting `/org/zolani-creative/trust-accounting` throws a generic "Something went wrong / An unexpected error occurred while loading this page" boundary. `/court-calendar` and `/conflict-check` correctly render "Module Not Available". Inconsistent progressive-disclosure UX — trust-accounting looks broken, the others look intentionally hidden.

## Root Cause (confirmed via grep)

File: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` lines 113–116:

```tsx
const enabledModules = settings.enabledModules ?? [];
if (!enabledModules.includes("trust_accounting")) {
  notFound();
}
```

Using `notFound()` without a corresponding `not-found.tsx` in the route tree triggers the root `error.tsx` / `global-error.tsx` boundary with the generic copy. Meanwhile `frontend/app/(app)/org/[slug]/court-calendar/page.tsx` lines 17–29 return a friendly inline "Module Not Available" JSX block — which is the convention.

## Fix

Replace the `notFound()` call on lines 113–116 of `trust-accounting/page.tsx` with the same inline "Module Not Available" JSX used by `court-calendar/page.tsx`.

### Exact change

File: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`

Replace:
```tsx
  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    notFound();
  }
```

With:
```tsx
  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <h2 className="font-display text-xl font-semibold text-slate-950 dark:text-slate-50">
          Module Not Available
        </h2>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          The Trust Accounting module is not enabled for your organization.
        </p>
      </div>
    );
  }
```

Optionally (cleaner): remove the now-unused `notFound` import on line 2 if nothing else uses it (check: the `catch { notFound(); }` on line 110 for settings-fetch failures is fine — keep the import).

### Even Cleaner (optional)

Use the existing `ModuleDisabledFallback` component (already imported by `settings/automations/page.tsx`) instead of inlining the markup:

```tsx
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";
// ...
if (!enabledModules.includes("trust_accounting")) {
  return <ModuleDisabledFallback moduleName="Trust Accounting" slug={slug} />;
}
```

This is strictly nicer (one consistent component across all disabled modules). Dev can pick either — the inline version matches `court-calendar`, the component version matches `automations`. If Dev chooses the component version, consider a follow-up refactor to migrate `court-calendar` and `conflict-check` to the same component for consistency (out of scope for this cycle).

## Scope

Frontend
Files to modify: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`
Files to create: none
Migration needed: no

## Verification

1. No backend restart needed (frontend HMR picks this up).
2. As Zolani, navigate to `/org/zolani-creative/trust-accounting`.
3. Expect: "Module Not Available" heading with friendly copy — NOT "Something went wrong".
4. Re-run Day 0 checkpoint 0.57 and close GAP-C-08.

## Estimated Effort

S (< 15 min). ~10 lines changed, frontend-only, HMR-hot.
