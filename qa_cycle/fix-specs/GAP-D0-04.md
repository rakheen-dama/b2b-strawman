# Fix Spec: GAP-D0-04 — "Projects" group header not renamed to "Matters" in sidebar

## Problem
The sidebar group header still shows "Projects" instead of "Matters" when the legal-za profile is active. The child nav items correctly use translated labels (e.g., "Matters" via `t("Projects")`), but the group header is not translated.

## Root Cause (confirmed)
In `frontend/components/nav-zone.tsx` line 45, the group label is rendered as `{zone.label}` without passing through the terminology translation function `t()`. Individual item labels at line 92 correctly use `{t(item.label)}`.

The terminology map at `frontend/lib/terminology-map.ts` lines 18-20 correctly defines:
```
"Projects": "Matters"
```

The `t()` function from `useTerminology()` is already imported at line 24 of `nav-zone.tsx` — it just isn't used for the group label.

## Fix
In `frontend/components/nav-zone.tsx`, change line 45:

**Before:**
```tsx
{zone.label}
```

**After:**
```tsx
{t(zone.label)}
```

## Scope
Frontend
Files to modify:
- `frontend/components/nav-zone.tsx` (line 45)
Files to create: none
Migration needed: no

## Verification
1. Apply legal-za profile
2. Sidebar should show "Matters" as group header (not "Projects")
3. Also verify accounting-za profile shows "Engagements" as group header

## Estimated Effort
S (< 30 min) — single character change
