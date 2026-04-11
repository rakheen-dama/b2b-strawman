# Fix Spec: BUG-UI-01 -- Proposal dialog customer selector unresponsive

## Problem

In the "New Proposal" dialog (`CreateProposalDialog`), the customer selector combobox (Radix Popover + Command) does not open when clicked. The PopoverTrigger button receives the click but the Popover content never appears. This is reproducible every time on the dialog.

## Root Cause (confirmed)

This is a known Radix UI issue: **Popover inside a modal Dialog**. Radix `Dialog` defaults to `modal={true}`, which traps focus and pointer events within the Dialog's content tree. When a Popover renders its content via a Portal (which `PopoverContent` does -- see `/frontend/components/ui/popover.tsx` line 27), the portal target is outside the Dialog's DOM tree. The Dialog's modal overlay intercepts pointer events before they reach the Popover's portal content, preventing it from opening or, if it opens, preventing interaction with its items.

This is NOT the same issue as GAP-P48-012 or GAP-P49-019 (those references were not found in the codebase). However, it IS the same root cause pattern -- multiple components in the codebase use Popover-inside-Dialog and likely suffer from the same issue:

- `/frontend/components/proposals/create-proposal-dialog.tsx` (this bug)
- `/frontend/components/retainers/create-retainer-dialog.tsx` (same pattern)
- `/frontend/components/field-definitions/FieldGroupDialog.tsx` (same pattern, two popovers)

The `type="button"` attribute is already present on the PopoverTrigger button (line 193), so that is not the issue.

## Fix

Add `modal={false}` to the Popover component to prevent the Dialog's focus trap from blocking the portal content.

### File: `/frontend/components/proposals/create-proposal-dialog.tsx`

Change line 185-187:
```tsx
<Popover
  open={customerPopoverOpen}
  onOpenChange={setCustomerPopoverOpen}
  modal={false}
>
```

### Also fix the same pattern in:

1. `/frontend/components/retainers/create-retainer-dialog.tsx` -- add `modal={false}` to the customer Popover
2. `/frontend/components/field-definitions/FieldGroupDialog.tsx` -- add `modal={false}` to both Popovers

### Alternative approach (if `modal={false}` causes focus escape issues)

Set `modal={false}` on the Dialog itself instead, but this is less desirable because it removes the overlay click-to-dismiss behavior and allows interaction with background elements.

## Scope

Frontend only.

Files to modify:
- `/frontend/components/proposals/create-proposal-dialog.tsx`
- `/frontend/components/retainers/create-retainer-dialog.tsx`
- `/frontend/components/field-definitions/FieldGroupDialog.tsx`

Files to create: none

Migration needed: no

## Verification

1. Open "New Proposal" dialog, click the customer selector -- Popover should open with searchable customer list.
2. Select a customer -- Popover should close and the selected customer name should appear.
3. Open "New Retainer" dialog, verify the same fix works there.
4. Open "Field Group" dialog, verify both Popovers work.
5. Verify Dialog close-on-overlay-click still works (should be unaffected since only the Popover is set to non-modal).

## Estimated Effort

XS (< 10 min) -- single prop addition on 3-4 components
