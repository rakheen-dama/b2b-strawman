# Fix Spec: GAP-S4-05 — Trust Accounting dashboard missing Transactions sub-page link

## Priority
LOW-MEDIUM — pure discoverability gap. QA had to type `/trust-accounting/transactions` directly
into the URL bar to reach the "Record Transaction" button. All functionality works once users
find the correct page; they just can't navigate there from the dashboard.

## Problem
The `/org/{slug}/trust-accounting` dashboard page renders Trust Balance / Active Clients /
Pending Approvals / Reconciliation KPIs plus a "Recent Transactions" card showing the last 10
rows. However, it has **no link or button** directing users to:
- `/trust-accounting/transactions` — where the "Record Transaction" button lives (used to record
  deposits, payments, transfers, fee transfers, refunds)
- `/trust-accounting/client-ledgers` — already linked via the "Recent Transactions" row
  `href`-per-row pattern, but not as a standalone nav entry from the dashboard

Users who land on `/trust-accounting` have no way to discover that `/trust-accounting/transactions`
exists unless they already know the route.

## Root Cause
File: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`

The page component has a `Recent Transactions` `CardHeader` at line 302–306:

```tsx
<Card>
  <CardHeader>
    <CardTitle>Recent Transactions</CardTitle>
    <CardDescription>Last 10 transactions across all client ledgers</CardDescription>
  </CardHeader>
  <CardContent>
    {dashboardData.recentTransactions.length === 0 ? (
      ...
```

No "View all" / "Manage" link, no "Record Transaction" shortcut. The dashboard's top-level
header at line 137–144 also lacks any secondary action button:

```tsx
<div>
  <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
    Trust Accounting
  </h1>
  <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
    LSSA-compliant trust account management for client funds
  </p>
</div>
```

## Fix
Two tiny additions. One in the page header (primary action), one in the Recent Transactions card
header (secondary "View all").

File: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`

### Change 1 — Add header action buttons

Replace the page header block (lines 136–144) with:

```tsx
{/* Header */}
<div className="flex items-start justify-between gap-4">
  <div>
    <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
      Trust Accounting
    </h1>
    <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
      LSSA-compliant trust account management for client funds
    </p>
  </div>
  {dashboardData && (
    <div className="flex items-center gap-2">
      <Button asChild variant="outline">
        <Link href={`/org/${slug}/trust-accounting/client-ledgers`}>
          <Users className="mr-2 size-4" />
          Client Ledgers
        </Link>
      </Button>
      <Button asChild>
        <Link href={`/org/${slug}/trust-accounting/transactions`}>
          <ArrowUpRight className="mr-2 size-4" />
          Record Transaction
        </Link>
      </Button>
    </div>
  )}
</div>
```

### Change 2 — Add "View all" link to Recent Transactions card header

Replace the Recent Transactions `CardHeader` block (lines 302–306) with:

```tsx
<Card>
  <CardHeader className="flex flex-row items-center justify-between space-y-0">
    <div>
      <CardTitle>Recent Transactions</CardTitle>
      <CardDescription>Last 10 transactions across all client ledgers</CardDescription>
    </div>
    <Button asChild variant="ghost" size="sm">
      <Link href={`/org/${slug}/trust-accounting/transactions`}>
        View all
        <ArrowUpRight className="ml-1 size-3" />
      </Link>
    </Button>
  </CardHeader>
  <CardContent>
```

### Change 3 — Add the required imports

At the top of the file, alongside the existing Lucide imports, ensure `Users` is already
imported (it is, line 5 — `Users` is used in the "Active Clients" KPI card). Add:

```tsx
import Link from "next/link";
import { Button } from "@/components/ui/button";
```

(Check that `Button` is not already imported — grep for `import.*Button`. If it already is,
skip that import line.)

## Scope
**Frontend only.**

Files to modify:
- `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`

Files to create: none
Migration needed: no
Tests: snapshot test if one exists for this page; otherwise none required.

## Verification
1. HMR picks up the change (no restart).
2. Navigate to `/org/mathebula-partners/trust-accounting` as owner.
3. Expected in page header (top-right of the page): two buttons — "Client Ledgers" (outline) and
   "Record Transaction" (primary filled).
4. Expected in Recent Transactions card header: a "View all →" ghost link that navigates to
   `/trust-accounting/transactions`.
5. Click "Record Transaction" → lands on `/trust-accounting/transactions` page with the
   "Record Transaction" dropdown menu available.
6. "Client Ledgers" → lands on `/trust-accounting/client-ledgers` list page.

## Estimated Effort
**S** (< 15 min). Pure markup changes + 2 new imports.
