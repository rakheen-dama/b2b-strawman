# Fix Spec: BUG-KC-002 — Create Customer Step 2 dialog footer buttons inaccessible

## Problem
When creating a customer, the Step 2 dialog ("Additional Information") renders many intake/custom fields (e.g., SA Accounting pack has Trust Details, Client Details, Contact & Address groups). The dialog content grows taller than the viewport, but the dialog has no scroll constraint. Since `DialogContent` uses `fixed top-[50%] translate-y-[-50%]` centering, the dialog extends beyond both top and bottom edges of the viewport. The `DialogFooter` with Back and Create Customer buttons falls outside the visible area and cannot be scrolled to.

## Root Cause (hypothesis)
The `DialogContent` component (`frontend/components/ui/dialog.tsx`, line 56) uses fixed centering without a maximum height or overflow scroll:
```
className="fixed top-[50%] left-[50%] z-50 w-full max-w-[calc(100%-2rem)] translate-x-[-50%] translate-y-[-50%] ..."
```

The inner content div (line 69) has no `max-h-*` or `overflow-y-auto` constraint:
```
className="bg-background grid gap-4 rounded-xl border p-6 shadow-lg"
```

When the dialog body grows with many custom fields, there is no scrollable region, and the footer buttons become unreachable.

## Fix
Two options (prefer Option A for surgical scope):

### Option A: Fix in CreateCustomerDialog (localized, safe)
Add a scrollable container around the Step 2 content in `frontend/components/customers/create-customer-dialog.tsx`.

1. Wrap the content area (the `<div className="space-y-4 py-2">` at line 203) with a scrollable constraint:
   - Change `<div className="space-y-4 py-2">` to `<div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">`
   - This caps the content area at 60% viewport height and adds vertical scroll, while DialogHeader and DialogFooter remain fixed outside the scroll region.

### Option B: Fix in DialogContent (global, wider impact)
Add `max-h-[85vh] overflow-y-auto` to the inner div in `DialogContent`. This would affect ALL dialogs globally. Less recommended due to regression risk, but would prevent future instances of the same problem.

**Recommendation: Option A** — surgical fix scoped to the customer creation dialog. Option B can be considered as a follow-up if other dialogs have the same issue.

## Scope
Frontend only.

Files to modify:
- `frontend/components/customers/create-customer-dialog.tsx` (line 203: add max-h and overflow-y-auto to content wrapper)

Files to create: none
Migration needed: no

## Verification
- Re-run CUST-01.2: Create Customer > fill Step 1 > Next > Step 2 should show all custom fields with scroll if needed. Back and Create Customer buttons should be visible at bottom.
- Test on small viewport (1024x768) to confirm scroll behavior with many fields.
- Verify Step 1 still works normally (fewer fields, no scroll needed).
- Verify form submission works after scrolling (field values preserved).

## Estimated Effort
S (< 30 min) — single className change
