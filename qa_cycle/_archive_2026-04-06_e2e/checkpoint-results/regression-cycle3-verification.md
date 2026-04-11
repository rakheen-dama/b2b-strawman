# Regression Cycle 3 -- BUG-REG-001 Re-verification

**Date**: 2026-03-19
**QA Agent**: Cycle 3 verification
**Branch**: `bugfix_cycle_regression_2026-03-19`

## Summary

| Bug | PR | Rebuild | Verification Result |
|-----|-----|---------|---------------------|
| BUG-REG-001 | #782 | `--no-cache` frontend rebuild | **FAIL -- STILL BROKEN** |

## BUG-REG-001: Settings > Rates & Currency 500 (STILL FAILING)

**Test**: Navigate to `/org/e2e-test-org/settings/rates` as Alice (Owner).

**Result**: FAIL. Page shows "Something went wrong" with HTTP 500.

**Evidence**:
- Screenshot: `qa_cycle/screenshots/bug-reg-001-cycle3-alice.png`
- Console errors (2):
  1. `Failed to load resource: the server responded with a status of 500 (Internal Server Error)`
  2. `TypeError: Cannot read properties of null (reading 'length')` at `39d93f5aac721830.js:7:4858`
- Server-side error (from `docker compose logs frontend`):
  ```
  TypeError: Cannot read properties of null (reading 'length')
      at <unknown> (.next/server/chunks/ssr/_86c21c83._.js:7:4589)
  ```

## Critical Finding: PR #782 Fix is Incomplete

The Cycle 2 REOPEN was attributed to a Docker cache issue. This was partially correct -- the old chunks were indeed cached. However, the `--no-cache` rebuild done by Infra DID deploy the fix. I verified:

1. **Server component SSR chunk** (`[root-of-the-server]__92a29bdd._.js`) contains:
   - `"fulfilled"===p.status&&Array.isArray(p.value)&&(l=p.value)` -- the `Array.isArray` guard is present
   - `k?.defaultCurrency??"USD"` -- the null coalescing is present

2. **Client component SSR chunk** (`components_rates_member-rates-table_tsx_6ae47b6d._.js`) contains:
   - `n&&0!==n.length` -- the `!members` guard is present

3. **Client browser chunk** (`39d93f5aac721830.js`) contains:
   - `f&&0!==f.length` -- the `!members` guard is present

**The PR #782 fix IS deployed. The bug persists because the fix addresses the wrong root cause.**

### Actual Root Cause

The crash is NOT in `MemberRatesTable` checking `members.length`. It is in the **`AvatarCircle` component** which receives `member.name` as a prop. When `member.name` is `null`, the component calls `null.length` and `null.trim()`.

**Crash location**: SSR chunk `_86c21c83._.js:7:4589`, which contains:
```js
function e({name:a,size:e=32,className:f}){
  let g,h=d[function(a){
    let b=0;
    for(let c=0;c<a.length;c++) // <-- CRASH HERE when a (name) is null
      b=(b<<5)-b+a.charCodeAt(c)|0;
    return Math.abs(b)
  }(a)%d.length]
```

**Call chain**:
1. `api.get("/api/members")` returns an array of members
2. `members` passes the `Array.isArray` guard (it IS an array)
3. `members` passes the `!members || members.length === 0` guard (it has items)
4. `MemberRatesTable` iterates `members.map(member => ...)` and renders `<AvatarCircle name={member.name} />`
5. At least one member in the array has `name: null`
6. `AvatarCircle` calls `name.length` on `null` -> **TypeError**

### Why `member.name` is null

The backend `/api/members` endpoint returns org members. In the E2E seed data, some members may have been provisioned without a display name (the mock IDP only sets names for alice/bob/carol but the member sync might create records with null names). Alternatively, the API may return a different shape than expected by the `OrgMember` type.

### Required Fix (New)

Two options:
1. **Fix `AvatarCircle`** -- add `name ?? ""` or `name || "?"` defensive guard before `.length` / `.trim()` calls
2. **Fix `MemberRatesTable`** -- filter out members with null names: `members.filter(m => m.name)`
3. **Fix the server component** -- add null name guard: `members.filter(m => m?.name)`

Option 1 is the most robust as it prevents the crash at the source for ALL callers of `AvatarCircle`.

## Status

BUG-REG-001 remains **REOPENED**. The fix in PR #782 is deployed but insufficient. A new fix is needed targeting `AvatarCircle` null-name handling or member filtering.

AUTH-01 #1 remains **PARTIAL** (rates page still broken).

## No Additional Testing Performed

Since BUG-REG-001 still fails, Bob (Admin) testing was not performed. The page crashes before any content renders, so role-specific behavior cannot be verified.
