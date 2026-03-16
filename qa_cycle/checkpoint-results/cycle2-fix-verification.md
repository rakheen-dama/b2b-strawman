# Cycle 2 — Fix Verification Results

**Date**: 2026-03-17
**Agent**: QA
**Branch**: bugfix_cycle_2026-03-16

## GAP-P48-001: Proposal Creation/Detail/Send UI

| Check | Result | Evidence |
|-------|--------|----------|
| Proposals page loads | PASS | `/proposals` page shows stats cards (Total, Pending, Accepted, Conversion Rate) |
| "New Proposal" button visible | PASS | Button present in header, opens dialog |
| New Proposal dialog fields | PASS | Title, Customer, Fee Model, Amount, Currency, Hours, Expiry Date all present |
| Customer combobox opens | **FAIL** | Customer dropdown (Radix Popover + Command) does not open. Button type defaults to `submit` inside `<form>`, event handlers lost through `PopoverTrigger asChild → FormControl asChild` chain. No `onClick` on DOM element. |
| Proposal created (via API workaround) | PASS | Created via backend API, appears as DRAFT |
| Detail page renders | PASS | Title, PROP-0001, Fee Model=Retainer, R 5 500,00, Hours=10h, Created date shown |
| "Send Proposal" button | PASS | Button visible on detail page for DRAFT proposals |
| Send dialog opens | PASS | Shows Recipient combobox with portal contacts |
| Send with recipient | PASS | After filling required custom fields, proposal transitions to SENT |
| Status badge updates | PASS | Badge changes from "Draft" to "Sent" |
| Withdraw button appears | PASS | After send, button changes to "Withdraw" |

**Verdict**: PARTIAL — Detail page and send flow work correctly. Create dialog has a **new blocker bug**: Customer combobox is non-functional (PopoverTrigger event handlers lost in form context). Logged as GAP-P48-012.

## GAP-P48-004: Trust-Specific Fields

| Check | Result | Evidence |
|-------|--------|----------|
| Customer create dialog opens | PASS | Step 1 shows Name, Type dropdown, Email, Phone |
| Type dropdown has "Trust" option | PASS | Options: Individual, Company, Trust |
| Select "Trust" → Step 2 shows trust fields | **PASS** | "SA Accounting — Trust Details" group appears with: Trust Registration Number*, Trust Deed Date*, Trust Type*, plus 3 additional fields |
| Trust fields are distinct from Company fields | PASS | Trust-specific fields (Master's Office reference, Trust Deed Date) appear alongside standard SA Accounting Client Details |

**Verdict**: **VERIFIED** — Fix confirmed working.

## GAP-P48-006: Invoice Send Button Label

| Check | Result | Evidence |
|-------|--------|----------|
| Invoice detail page loads | PASS | INV-0001 shows with Approved badge, Acme Corp customer, line items |
| Send button text | **PASS** | Button reads "Send Invoice" (not "Mark as Sent") |

**Verdict**: **VERIFIED** — Fix confirmed working.

## GAP-P48-010: Carol Permission Denied on Rates Page

| Check | Result | Evidence |
|-------|--------|----------|
| Login as Carol | PASS | Carol (Member) authenticated via mock-login |
| Navigate to `/settings/rates` | PASS | Page loads (not 404) |
| Permission denied message | **PASS** | Shows: "You do not have permission to manage rates and currency settings. Only admins and owners can access this page." |
| Page heading still visible | PASS | "Rates & Currency" heading shown |

**Verdict**: **VERIFIED** — Fix confirmed working.

## New Bug Found

### GAP-P48-012: Customer combobox in New Proposal dialog is non-functional

- **Severity**: blocker (blocks proposal creation via UI)
- **Location**: `/proposals` → "New Proposal" dialog → Customer field
- **Root cause**: `PopoverTrigger asChild` wraps `FormControl` (also `asChild`) wraps `Button`. Inside a `<form>`, the Button's type defaults to `submit`. The Radix Popover's click handler is lost during the Slot merging chain — the DOM element has zero `onClick` handlers.
- **Fix**: Add `type="button"` to the Button inside PopoverTrigger in `create-proposal-dialog.tsx` line 191-196.
- **File**: `frontend/components/proposals/create-proposal-dialog.tsx`
- **Workaround**: Create proposals via backend API.
