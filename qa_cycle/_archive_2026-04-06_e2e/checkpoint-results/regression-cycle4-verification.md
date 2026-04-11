# Regression Cycle 4 -- BUG-REG-001 v2 Final Verification

**Date**: 2026-03-20
**QA Agent**: Cycle 4 verification
**Branch**: `bugfix_cycle_regression_2026-03-19`

## Summary

| Bug | PR | Rebuild | Verification Result |
|-----|-----|---------|---------------------|
| BUG-REG-001 | #785 | `--no-cache` frontend rebuild | **PASS -- VERIFIED** |

## BUG-REG-001: Settings > Rates & Currency (VERIFIED FIXED)

**Fix**: PR #785 -- Added `const safeName = name ?? ""` null-coalescing guard in `AvatarCircle` component (`frontend/components/ui/avatar-circle.tsx`).

### Test 1: Alice (Owner)

**Steps**: Navigate to `http://localhost:3001/mock-login` > Select Alice (Owner) > Sign In > Navigate to `/org/e2e-test-org/settings/rates`.

**Result**: PASS.

**Evidence**:
- Screenshot: `qa_cycle/screenshots/bug-reg-001-cycle4-alice-PASS.png`
- Page loads with full content: "Rates & Currency" heading, Default Currency (ZAR), Billing Rates tab, rate table with 6 members
- Members with null names render correctly: "?" avatar, email-only display (user_alice@unknown.local, alice-user-id@unknown.local, alice@unknown.local)
- Named members render normally: Alice Owner (AO), Bob Admin (BA), Carol Member (CM) with rates R1,500, R850, R450 ZAR
- Console errors: 0
- No "Something went wrong" error boundary triggered

### Test 2: Bob (Admin)

**Steps**: Navigate to `http://localhost:3001/mock-login` > Select Bob (Admin) > Sign In > Navigate to `/org/e2e-test-org/settings/rates`.

**Result**: PASS.

**Evidence**:
- Screenshot: `qa_cycle/screenshots/bug-reg-001-cycle4-bob-PASS.png`
- Identical page content as Alice: full rate table, all 6 members visible, null-name members display correctly
- Console errors: 0
- Admin has full edit/delete actions on all rate rows (same as Owner)

## Root Cause Confirmation

The v2 fix (PR #785) correctly addressed the actual root cause:
- **Problem**: `AvatarCircle` component called `name.length` in `hashName()` function, crashing when `name` prop was `null`
- **Fix**: `const safeName = name ?? ""` inside AvatarCircle, used in all downstream calls
- **Impact**: All 7 call sites across 5 files are now protected against null names

The v1 fix (PR #782) was insufficient because it only guarded the `members` array itself (not null, not empty) but did not guard individual `member.name` values within the array.

## AUTH-01 #1 Update

| ID | Test | Previous Result | New Result |
|----|------|-----------------|------------|
| AUTH-01 #1 | Owner can access all settings | PARTIAL (rates 500) | **PASS** |

## Status

BUG-REG-001: **FIXED -> VERIFIED**. Both Owner and Admin roles confirmed working. No console errors. The AvatarCircle null name guard in PR #785 resolves the crash completely.
