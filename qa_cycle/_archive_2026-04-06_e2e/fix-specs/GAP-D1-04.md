# Fix Spec: GAP-D1-04 -- Dialog titles use "Customer" instead of "Client" for legal-za

## Problem

When the legal-za vertical profile is active, the "Create Customer" dialog title and "Activate Customer" transition dialog still use the generic "Customer" term instead of "Client".

## Root Cause (confirmed)

Two components have hardcoded "Customer" strings instead of using the `t()` terminology function:

**File 1:** `frontend/components/customers/create-customer-dialog.tsx`
- Line 210: `{step === 1 ? "Create Customer" : "Additional Information"}` -- hardcoded
- Line 430: `{isSubmitting ? "Creating..." : "Create Customer"}` -- hardcoded
- The component already imports `useTerminology` (line 29) and calls `const { t } = useTerminology()` but does not use `t()` on these dialog title strings.

**File 2:** `frontend/components/compliance/TransitionConfirmDialog.tsx`
- Line 49: `title: "Activate Customer"` -- hardcoded in TRANSITION_META
- Line 50-51: `description: "This will mark the customer as Active..."` -- hardcoded
- Lines 43, 57, 64, 72, 76: Other transition titles also use "Customer" (Start Onboarding, Mark as Dormant, Offboard Customer, Complete Offboarding)

The `t()` function from `useTerminology()` looks up terms in `frontend/lib/terminology-map.ts`. The legal-za map includes `Customer: "Client"` and `customer: "client"`, so wrapping strings with `t()` would translate them. However, `t()` only does exact key matches (e.g., `t("Customer")` returns "Client"), so compound phrases like "Create Customer" need to be restructured as `t("Create") + " " + t("Customer")` or the map needs entries for compound phrases.

## Fix

### Approach: Add compound phrases to terminology map

The simplest approach is to add the compound phrases to the terminology map, since the `t()` function does exact-match lookup.

**File 1:** `frontend/lib/terminology-map.ts` -- Add compound phrase entries

Add to both `accounting-za` and `legal-za` maps:
```typescript
"Create Customer": "Create Client",
"Activate Customer": "Activate Client",
"Offboard Customer": "Offboard Client",
"Start Onboarding": "Start Onboarding",  // same for legal
"Mark as Dormant": "Mark as Dormant",     // same for legal
```

Only the entries that differ need to be added. For legal-za:
```typescript
"Create Customer": "Create Client",
"Activate Customer": "Activate Client",
"Offboard Customer": "Offboard Client",
```

**File 2:** `frontend/components/customers/create-customer-dialog.tsx`
- Line 210: Change to `{step === 1 ? t("Create Customer") : "Additional Information"}`
- Line 430: Change to `{isSubmitting ? "Creating..." : t("Create Customer")}`

**File 3:** `frontend/components/compliance/TransitionConfirmDialog.tsx`

The TRANSITION_META is a static object, but `t()` is a hook that can only be used inside a component. Two options:
- (a) Make TRANSITION_META a function that takes `t` and returns the meta object
- (b) Wrap the title display with `t()` at render time

Option (b) is simpler:
- Where the title is rendered, wrap it with `t()`: e.g., `t(meta.title)`

This component needs to import and use `useTerminology`:
```tsx
import { useTerminology } from "@/lib/terminology";
// Inside the component:
const { t } = useTerminology();
// In the render:
<AlertDialogTitle>{t(meta.title)}</AlertDialogTitle>
```

## Scope

- 3 files: `terminology-map.ts`, `create-customer-dialog.tsx`, `TransitionConfirmDialog.tsx`
- ~15 lines changed
- Frontend-only, no backend changes
- No rebuild needed (HMR picks up changes)

## Verification

1. Login as Alice, navigate to Clients page
2. Click "New Client" (button label already correct per terminology)
3. Dialog title should say "Create Client" (not "Create Customer")
4. Create a client, transition to Onboarding -- dialog title should say "Start Onboarding"
5. Transition to Active -- dialog title should say "Activate Client"

## Estimated Effort

30 minutes
