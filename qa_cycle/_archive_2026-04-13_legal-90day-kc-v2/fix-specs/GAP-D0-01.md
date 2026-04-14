# Fix Spec: GAP-D0-01 — Dashboard subtitle "project health" should be "matter health"

## Problem
The dashboard header subtitle reads "Company overview and project health" which is a terminology leak for the legal-za vertical. It should use the translated term (e.g., "matter health" for legal verticals). Observed during Day 0 checkpoint 0.25.

## Root Cause (confirmed)
File: `frontend/app/(app)/org/[slug]/dashboard/dashboard-header.tsx`, line 29.
The subtitle is hardcoded as `"Company overview and project health"` rather than using the terminology system (`useTerminology` / `t()` function).

## Fix
1. In `frontend/app/(app)/org/[slug]/dashboard/dashboard-header.tsx`, the component is already `"use client"`.
2. Import `useTerminology` from `@/lib/terminology`.
3. Destructure `const { t } = useTerminology();` in the component body.
4. Change line 29 from:
   ```
   Company overview and project health
   ```
   to:
   ```
   {`Company overview and ${t("project").toLowerCase()} health`}
   ```
5. Update the test at `frontend/__tests__/dashboard/company-dashboard.test.tsx` line 292 if it asserts on the exact subtitle text.

## Scope
Frontend only.
Files to modify:
- `frontend/app/(app)/org/[slug]/dashboard/dashboard-header.tsx`
- `frontend/__tests__/dashboard/company-dashboard.test.tsx` (if asserting on subtitle text)

## Verification
Re-run Day 0 checkpoint 0.25 — subtitle should show "matter health" when vertical is legal-za.

## Estimated Effort
S (< 30 min)
