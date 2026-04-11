# Fix Spec: GAP-D0-05 — Dashboard cards say "Active Projects" / "Project Health" instead of legal terms

## Problem
Dashboard KPI cards and widgets show generic terms: "Active Projects" and "Project Health" instead of "Active Matters" and "Matter Health" when the legal-za profile is active.

## Root Cause (confirmed)
Three dashboard components have hardcoded labels that are not passed through the terminology `t()` function:

1. **`frontend/components/dashboard/kpi-card-row.tsx`** — line 38: `label="Active Projects"` (empty state), line 67: `label: "Active Projects"` (data state)
2. **`frontend/components/dashboard/metrics-strip.tsx`** — line 74: `Active Projects` (hardcoded text)
3. **`frontend/components/dashboard/project-health-widget.tsx`** — line 78: `Project Health` and line 137: `Project Health` (hardcoded in CardTitle)

These are server components, so they would need either:
- (a) To be converted to client components with `useTerminology()`, or
- (b) To receive the translated labels as props from a parent, or
- (c) To use a server-side terminology function

## Fix
The simplest approach: pass the translated terminology strings as props from the dashboard page (which has access to settings and can resolve terminology server-side).

### Option A: Create a server-side t() helper
Add a `getTerminologyFn()` utility in `frontend/lib/terminology.ts` that works outside React context (takes namespace as parameter):
```tsx
export function createServerT(namespace: string | null) {
  return (key: string) => {
    if (!namespace) return key;
    return TERMINOLOGY[namespace]?.[key] ?? key;
  };
}
```

Then in each dashboard component, accept a `t` function or translated labels as props.

### Option B: Wrap labels with t() by adding "use client"
Add `"use client"` to the dashboard components and use `useTerminology()`. This is the simpler approach but adds client-side rendering overhead.

### Recommended: Option A
Use server-side terminology resolution. Pass translated labels as props or use a shared helper.

### Files to change:
1. `frontend/components/dashboard/kpi-card-row.tsx` — change `"Active Projects"` to `t("Active Projects")` (lines 38, 67)
2. `frontend/components/dashboard/metrics-strip.tsx` — change `Active Projects` to `{t("Active Projects")}` (line 74)
3. `frontend/components/dashboard/project-health-widget.tsx` — change `Project Health` to `{t("Project Health")}` (lines 78, 137)
4. `frontend/lib/terminology-map.ts` — add entries for "Active Projects" and "Project Health" to the legal-za namespace

### Terminology entries to add:
```typescript
"Active Projects": "Active Matters",
"Project Health": "Matter Health",
```

## Scope
Frontend
Files to modify:
- `frontend/components/dashboard/kpi-card-row.tsx`
- `frontend/components/dashboard/metrics-strip.tsx`
- `frontend/components/dashboard/project-health-widget.tsx`
- `frontend/lib/terminology-map.ts`
- Possibly `frontend/lib/terminology.ts` (if adding server-side helper)
Files to create: none
Migration needed: no

## Verification
1. Apply legal-za profile
2. Dashboard should show "Active Matters" instead of "Active Projects"
3. Dashboard should show "Matter Health" instead of "Project Health"
4. Verify accounting-za shows "Active Engagements" and "Engagement Health" (add those entries too)

## Estimated Effort
M (30 min - 2 hr) — multiple components need changes; need to decide on server vs client approach
