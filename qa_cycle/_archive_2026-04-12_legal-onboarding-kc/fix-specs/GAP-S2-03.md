# Fix Spec: GAP-S2-03 — Add Rate dialog on Cost Rates tab defaults to Billing Rate

## Priority
LOW — confusing UX, naive save errors with "A billing rate already exists for this period".

## Problem
On `/settings/rates` Cost Rates tab, clicking "Add Rate" opens a dialog where Rate Type
defaults to "Billing Rate". If the user keeps the default and saves, they get a conflict
error because a billing rate already exists. The dialog should default `Rate Type` to match
the currently active tab.

## Root Cause (inferred from QA evidence)
Files (need grep to confirm):
- `frontend/app/(app)/org/[slug]/settings/rates/` — page and dialog component.
- Search for `rateType` default in the rate dialog. Currently hardcoded to `"BILLING"` or
  similar.

## Fix Steps
1. Pass the active tab (BILLING_RATES / COST_RATES) as a prop to the Add Rate dialog
   component.
2. Use that prop as the `defaultValue` for the Rate Type select on dialog open.
3. Optionally hide/disable the Rate Type select entirely when opened from a specific tab —
   the user has already indicated their intent by clicking Add Rate within a specific tab.

## Scope
- Frontend only
- Files to modify:
  - `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` (or client component)
  - `frontend/components/rates/add-rate-dialog.tsx` (or equivalent)
- Migration needed: no

## Verification
1. Navigate to `/settings/rates` → Cost Rates tab → Click Add Rate.
2. Dialog opens with Rate Type = Cost Rate (not Billing Rate).
3. Save — no conflict error.

## Estimated Effort
S (< 20 min)
