# Fix Spec: GAP-S6-05 — Fee Notes KPIs display $0.00 instead of ZAR

## Priority
LOW — cosmetic; but it's a confusing user experience for a South African law firm.

## Problem
`/invoices` (Fee Notes) page KPIs "Total Outstanding", "Total Overdue", "Paid This Month" all
render as "$0.00" instead of using the org's default currency (ZAR, configured in Session 2).
When there are no invoices, the USD fallback is used.

## Root Cause (confirmed via grep)
Files:
- `frontend/app/(app)/org/[slug]/invoices/page.tsx:22-23`:
  ```ts
  // Use the first invoice's currency as the default, falling back to USD
  const defaultCurrency = invoices.length > 0 ? invoices[0].currency : "USD";
  ```
  The fallback is hardcoded USD. It should read from `OrgSettings.defaultCurrency` (ZAR for the
  Mathebula tenant).

## Fix Steps
1. In `frontend/app/(app)/org/[slug]/invoices/page.tsx`, import `getOrgSettings` from
   `@/lib/api/settings` (already used elsewhere — e.g. `settings/trust-accounting/page.tsx`).
2. Replace lines 22-23 with:
   ```ts
   const orgSettings = await getOrgSettings();
   const fallbackCurrency = orgSettings.defaultCurrency ?? "USD";
   const defaultCurrency = invoices.length > 0 ? invoices[0].currency : fallbackCurrency;
   ```
3. Verify that the `OrgSettings` type has `defaultCurrency`. If not, use `currency` or whatever
   field holds the org's default currency — grep `@/lib/api/settings` for the correct field
   name.

## Scope
- Frontend only
- Files to modify:
  - `frontend/app/(app)/org/[slug]/invoices/page.tsx`
- Migration needed: no

## Verification
On the Mathebula tenant (ZAR configured in Session 2), `/invoices` KPI cards should now show
"R 0,00" (via `Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" })`) instead of
"$0.00".

## Estimated Effort
S (< 15 min)
