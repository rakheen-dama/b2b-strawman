# Fix Spec: GAP-D0-11 — Custom Fields tab labels hardcoded (not terminology-aware)

## Problem
The Custom Fields settings page shows tab labels "Projects", "Tasks", "Customers", "Invoices" regardless of the active vertical profile. For a legal-za org, these should read "Matters", "Action Items", "Clients", "Fee Notes". The page description text already uses `TerminologyText` correctly (line 89 of `page.tsx`), but the tab labels in the content component are hardcoded in a static array.

## Root Cause (confirmed)

File: `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx`, lines 35-40:

```tsx
const ENTITY_TYPE_TABS: { value: EntityType; label: string }[] = [
  { value: "PROJECT", label: "Projects" },
  { value: "TASK", label: "Tasks" },
  { value: "CUSTOMER", label: "Customers" },
  { value: "INVOICE", label: "Invoices" },
];
```

This is a module-level constant with hardcoded English labels. The component is a `"use client"` component (line 1), and the `TerminologyProvider` is already mounted in the parent layout (`frontend/app/(app)/org/[slug]/layout.tsx` line 127). The `useTerminology()` hook is available but not used in this file.

The terminology map (`frontend/lib/terminology-map.ts`) defines all needed mappings for `legal-za`:
- `"Projects"` -> `"Matters"` (line 26)
- `"Tasks"` -> `"Action Items"` (line 30: key is `"Tasks"`, not present -- but individual mappings exist)
- `"Customers"` -> `"Clients"` (line 32)
- `"Invoices"` -> `"Fee Notes"` (line 48)

Checking the map more carefully: the keys `"Projects"`, `"Customers"`, `"Invoices"` are all present. For `"Tasks"` -- looking at lines 29-30 of `terminology-map.ts`, the legal-za map has `Task: "Action Item"` and `Tasks: "Action Items"` -- so the plural form "Tasks" IS mapped. All four labels have mappings.

Additionally, the empty-state text at line 232 also uses a hardcoded label:
```tsx
No field groups for {tab.label.toLowerCase()} yet.
```
This will say "No field groups for projects yet" instead of "No field groups for matters yet" after the fix, since `tab.label` would now be the translated term. This is acceptable behavior.

## Fix

### Step 1: Import `useTerminology` and translate tab labels

File: `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx`

1. Add import for `useTerminology`:
```tsx
import { useTerminology } from "@/lib/terminology";
```

2. Change the static `ENTITY_TYPE_TABS` array to only hold the EntityType values and their default (untranslated) labels. Then inside the component, derive the translated labels using the `t()` function.

Replace lines 35-40:
```tsx
const ENTITY_TYPE_TABS: { value: EntityType; label: string }[] = [
  { value: "PROJECT", label: "Projects" },
  { value: "TASK", label: "Tasks" },
  { value: "CUSTOMER", label: "Customers" },
  { value: "INVOICE", label: "Invoices" },
];
```

With a simpler constant that just maps EntityType to its default label:
```tsx
const ENTITY_TYPE_DEFAULT_LABELS: Record<EntityType, string> = {
  PROJECT: "Projects",
  TASK: "Tasks",
  CUSTOMER: "Customers",
  INVOICE: "Invoices",
};

const ENTITY_TYPES: EntityType[] = ["PROJECT", "TASK", "CUSTOMER", "INVOICE"];
```

3. Inside the `CustomFieldsContent` component body (after the existing `createMessages` call), add:
```tsx
const { t } = useTerminology();
const tabs = ENTITY_TYPES.map((et) => ({
  value: et,
  label: t(ENTITY_TYPE_DEFAULT_LABELS[et]),
}));
```

4. Replace all references to `ENTITY_TYPE_TABS` with `tabs`:
- Line 66-74: `{ENTITY_TYPE_TABS.map((tab) => ...)}` -> `{tabs.map((tab) => ...)}`
- Line 78: `{ENTITY_TYPE_TABS.map((tab) => ...)}` -> `{tabs.map((tab) => ...)}`

The `t()` function from `useTerminology()` is an identity function when no vertical profile is active (returns the input unchanged), so the default/generic case is preserved.

## Scope

- **Frontend only**: 1 file changed
- No backend changes
- No migration needed
- No new dependencies

## Files to Modify

| File | Change |
|------|--------|
| `frontend/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content.tsx` | Import `useTerminology`, translate tab labels via `t()`, replace `ENTITY_TYPE_TABS` references |

## Verification

1. Navigate to Settings > Custom Fields as an owner/admin user in the legal-za org.
2. **Expected**: Tab labels show "Matters", "Action Items", "Clients", "Fee Notes".
3. **Expected**: Empty state text reads "No field groups for matters yet" (lowercase of translated label).
4. Switch to a generic (non-vertical) org or clear the vertical profile.
5. **Expected**: Tab labels show the default "Projects", "Tasks", "Customers", "Invoices".
6. Regression: confirm the tab click behavior still works (switching between tabs renders the correct field definitions and groups for each entity type).

## Estimated Effort

XS (< 15 min). Frontend-only, 1 file, ~10 lines changed. HMR picks it up -- no rebuild needed.
