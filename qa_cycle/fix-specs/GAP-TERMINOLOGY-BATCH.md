# Fix Spec: TERMINOLOGY-BATCH — Invoice/Fee Note terminology leaks (GAP-D36-04, GAP-D36-05, GAP-D38-02, GAP-D50-01)

## Problem
Four locations in the frontend still use "Invoice" instead of the terminology-translated term ("Fee Note" for legal-za). These are hardcoded strings that bypass the terminology system:
1. **GAP-D36-04**: Matter overview "Unbilled Time" box shows "Generate Invoice" link
2. **GAP-D36-05**: Client detail "Unbilled Time" box shows "Create Invoice" link
3. **GAP-D38-02**: Fee note detail page section heading says "Invoice Details"
4. **GAP-D50-01**: Fee note list table header column says "Invoice"

## Root Cause (confirmed)
All four are hardcoded English strings that don't use the `useTerminology()`/`t()` translation system.

### GAP-D36-04
File: `frontend/components/projects/overview-tab.tsx`, line 569.
```
Generate Invoice
```

### GAP-D36-05
File: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`, line 693.
```
label: "Create Invoice",
```

### GAP-D38-02
File: `frontend/components/invoices/invoice-details-readonly.tsx`, line 22.
```
<h2 ...>Invoice Details</h2>
```
Also: `frontend/components/invoices/invoice-draft-form.tsx`, line 55.
```
<h2 ...>Invoice Details</h2>
```

### GAP-D50-01
File: `frontend/app/(app)/org/[slug]/invoices/page.tsx`, line 220.
```
<th ...>Invoice</th>
```

## Fix

### GAP-D36-04: overview-tab.tsx line 569
The `overview-tab.tsx` component is `"use client"` and already imports `useTerminology`.
Change line 569 from:
```
Generate Invoice
```
to:
```
{`Generate ${t("Invoice")}`}
```

### GAP-D36-05: customers/[id]/page.tsx line 693
This is a Server Component. The `ActionCard` receives a `label` string prop. The page uses `createMessages()` elsewhere. However, `ActionCard` is rendered in the Server Component, which does NOT have access to `useTerminology()` (client hook).

Option: Use the `createMessages` server-side helper that already exists in this file, or pass a translated label. Looking at the file, it already imports `createMessages` from `@/lib/messages`.

Check if `createMessages` supports terminology translation. If not, the simpler fix: replace the hardcoded label with the TerminologyHeading approach used elsewhere on this page, or add a server-side terminology resolver.

Simplest fix: change line 693 from:
```
label: "Create Invoice",
```
to:
```
label: "Create Fee Note",
```

**Wait** — this is a cross-vertical change. The correct approach is to use the terminology system. Since the customers page is a Server Component, and `TerminologyHeading` is a client component, the best approach is:

The `ActionCard` component accepts a string `label`. We need the page to pass the right label. Looking at how `TerminologyHeading` is used on the same page (line 122 area), it's used inside JSX. But `ActionCard` takes a string prop, not JSX.

**Recommended fix**: Change the hardcoded string to use a server-side terminology lookup. If no server-side terminology exists, the simplest fix is to hardcode "Create Fee Note" only for legal verticals. But since the `TerminologyHeading` pattern is already used on this page, convert the label to use terminology:

Change `label: "Create Invoice"` to `label: "Create " + t("Invoice")` where `t` is available. If server-side, use `createMessages("terminology")` or equivalent.

**Actual simplest fix**: The page already uses `TerminologyHeading` in other places, meaning there IS a server-side terminology lookup. Check the imports. The page imports `TerminologyHeading` which is a client component. For the `ActionCard` label (a string prop), we need server-side resolution.

For this QA cycle, the pragmatic fix: wrap the label resolution. The `ActionCard` component should accept `ReactNode` for label instead of just string. OR use the messages system.

**Final recommendation**: All four fixes below use the simplest approach for each context.

### GAP-D38-02: invoice-details-readonly.tsx line 22 + invoice-draft-form.tsx line 55
Both are `"use client"` components. Add `useTerminology` import and use `t("Invoice")`:

In `invoice-details-readonly.tsx`, change line 22 from:
```
<h2 ...>Invoice Details</h2>
```
to:
```
<h2 ...>{t("Invoice")} Details</h2>
```
(Import and call `const { t } = useTerminology()` at top of component.)

Same pattern for `invoice-draft-form.tsx` line 55.

### GAP-D50-01: invoices/page.tsx line 220
This is a Server Component. The `<th>` element contains the hardcoded "Invoice" text. Replace with `<TerminologyHeading term="Invoice" />` which is already imported (line 15) and used elsewhere on this page (lines 122, 140).

Change line 220 from:
```
<th ...>Invoice</th>
```
to:
```
<th ...><TerminologyHeading term="Invoice" /></th>
```

## Scope
Frontend only.
Files to modify:
- `frontend/components/projects/overview-tab.tsx` (line 569)
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (line 693)
- `frontend/components/invoices/invoice-details-readonly.tsx` (line 22)
- `frontend/components/invoices/invoice-draft-form.tsx` (line 55)
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` (line 220)
- Associated test files if they assert on exact "Invoice" strings

## Verification
Re-run Day 36, 38, 45, and 50 checkpoints — all should show "Fee Note" terminology for legal-za vertical.

## Estimated Effort
S (< 30 min) — Five string replacements using existing terminology patterns.
