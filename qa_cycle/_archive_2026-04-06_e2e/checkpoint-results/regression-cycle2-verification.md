# Regression Cycle 2 — Fix Verification

**Date**: 2026-03-19
**QA Agent**: Cycle 2 verification
**Branch**: `bugfix_cycle_regression_2026-03-19`

## Summary

Two fixes were merged and the E2E frontend was rebuilt. Verification results:

| Bug | PR | Fix Status | Verification Result |
|-----|-----|------------|---------------------|
| BUG-REG-001 | #782 | FIXED | **FAIL — REOPENED** |
| BUG-REG-002 | #783 | FIXED | **PASS — VERIFIED** |

## BUG-REG-001: Settings > Rates & Currency 500 (REOPENED)

**Test**: Navigate to `/org/e2e-test-org/settings/rates` as Alice (Owner).

**Result**: FAIL. The page still crashes with "Something went wrong" error.

**Evidence**:
- Screenshot: `bug-reg-001-rates-page.png` — shows "Something went wrong" error page with sidebar visible
- Console error: `TypeError: Cannot read properties of null (reading 'length')`
- Server log: Same error in `e2e-frontend` container logs at `.next/server/chunks/ssr/_86c21c83._.js:7:4589`
- HTTP 500 response from server

**Root Cause Analysis**:
The fix in PR #782 modified the source files (`settings/rates/page.tsx` and `components/rates/member-rates-table.tsx`) but the Docker frontend rebuild did NOT incorporate the changes into the compiled SSR chunks. Specifically:

1. The SSR chunk `components_rates_member-rates-table_tsx_6ae47b6d._.js` contains `n.length` without the `!n ||` guard — the fix was NOT compiled into this chunk.
2. The server page chunk `_877dcda3._.js` does NOT contain `Array.isArray` from the fix — the only `Array.isArray` in that chunk is from React/Radix framework code.
3. The source files on disk DO have the fix (confirmed by reading `page.tsx` lines 50-51 and `member-rates-table.tsx` line 100).

**Conclusion**: The `e2e-rebuild.sh frontend` command did not properly rebuild from the fixed source. This is likely a Docker build cache issue. The fix needs a clean rebuild (e.g., `docker build --no-cache`) or volume mount invalidation.

**Status**: REOPENED — fix is correct in source but not deployed to the running container.

## BUG-REG-002: Carol (Member) RBAC 500s (VERIFIED)

**Test**: Navigate to 4 role-gated pages as Carol (Member).

| Page | URL | Expected | Actual | Result |
|------|-----|----------|--------|--------|
| Profitability | `/org/e2e-test-org/profitability` | Permission denied | "You don't have access to Profitability" | PASS |
| Reports | `/org/e2e-test-org/reports` | Permission denied | "You don't have access to Reports" | PASS |
| Customers | `/org/e2e-test-org/customers` | Permission denied | "You don't have access to Customers" | PASS |
| Settings/Roles | `/org/e2e-test-org/settings/roles` | Permission denied | "You don't have access to Roles & Permissions" | PASS |

**Evidence**: All 4 pages show the `<PermissionDenied>` component with:
- Lock icon
- "You don't have access to [Page Name]" heading
- "Contact your organisation admin to update your role." description
- "Go to dashboard" link

No 500 errors. No console errors. The ErrorBoundary fix (re-throw `NEXT_NOT_FOUND`/`NEXT_REDIRECT` digest) is working correctly. The `<PermissionDenied>` component replacement on all 4 pages is confirmed.

**Status**: VERIFIED

## AUTH-01 Score Updates

| # | Test | Previous | Updated | Reason |
|---|------|----------|---------|--------|
| 1 | Owner can access all settings | PARTIAL | PARTIAL (unchanged) | Rates page still 500 — fix not deployed |
| 4 | Member blocked from profitability | FAIL | PASS | "You don't have access to Profitability" shown |
| 5 | Member blocked from reports | FAIL | PASS | "You don't have access to Reports" shown |
| 8 | Member blocked from customers | FAIL | PASS | "You don't have access to Customers" shown |
| 10 | Member blocked from roles settings | FAIL | PASS | "You don't have access to Roles & Permissions" shown |

## Updated Scorecard Impact

- AUTH-01: was 5 PASS / 4 FAIL / 1 PARTIAL -> now 9 PASS / 0 FAIL / 1 PARTIAL
- Total: was 71 PASS / 5 FAIL / 2 PARTIAL -> now 75 PASS / 1 FAIL / 2 PARTIAL
