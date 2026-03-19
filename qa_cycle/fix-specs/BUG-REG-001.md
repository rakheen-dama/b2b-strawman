# Fix Spec: BUG-REG-001 — Settings > Rates & Currency 500 for all users

## Problem
The Settings > Rates & Currency page (`/settings/rates`) crashes with `TypeError: Cannot read properties of null (reading 'length')` for ALL users including Owner. This is a 500 error that blocks all rate card testing (SET-02 section). The error is a client-side crash in the `MemberRatesTable` component, caught by the layout's `ErrorBoundary`.

## Root Cause (hypothesis)
The page server component at `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` fetches four API endpoints via `Promise.allSettled` (lines 43-49). When an API call succeeds, the resolved value replaces the default:

```typescript
if (membersRes.status === "fulfilled") members = membersRes.value;
```

If `apiRequest` returns `null` or `undefined` (possible when the response body is empty or the content-length is 0 — see `frontend/lib/api/client.ts` lines 117-125), then `members` becomes `null`. This null value is passed as a prop to the client component `MemberRatesTable` (`frontend/components/rates/member-rates-table.tsx`), which accesses `members.length` on line 100, causing the crash.

The same issue applies to all four fetched values (`settings`, `members`, `billingRates`, `costRates`). A transient network issue in the Docker stack, a proxy returning an empty body, or any non-standard response shape could trigger this.

**Key files:**
- `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` — lines 43-53 (data fetch and assignment)
- `frontend/components/rates/member-rates-table.tsx` — line 100 (`members.length`)
- `frontend/lib/api/client.ts` — lines 117-125 (null/undefined return paths)

## Fix
1. **Add null coalescing on all `Promise.allSettled` value assignments** in `frontend/app/(app)/org/[slug]/settings/rates/page.tsx`:

   Change:
   ```typescript
   if (settingsRes.status === "fulfilled") settings = settingsRes.value;
   if (membersRes.status === "fulfilled") members = membersRes.value;
   ```
   To:
   ```typescript
   if (settingsRes.status === "fulfilled" && settingsRes.value) settings = settingsRes.value;
   if (membersRes.status === "fulfilled" && Array.isArray(membersRes.value)) members = membersRes.value;
   ```

2. **Add defensive null guard in `MemberRatesTable`** at `frontend/components/rates/member-rates-table.tsx`:

   Change line 100:
   ```typescript
   if (members.length === 0) {
   ```
   To:
   ```typescript
   if (!members || members.length === 0) {
   ```

3. **Ensure `defaultCurrency` has a fallback** in the page's prop passing:

   Change line 81:
   ```typescript
   defaultCurrency={settings.defaultCurrency}
   ```
   To:
   ```typescript
   defaultCurrency={settings?.defaultCurrency ?? "USD"}
   ```

## Scope
Frontend only.

Files to modify:
- `frontend/app/(app)/org/[slug]/settings/rates/page.tsx`
- `frontend/components/rates/member-rates-table.tsx`

Files to create: none
Migration needed: no

## Verification
Re-run AUTH-01 #1 (Owner can access all settings pages) — Rates page should load without error. Also re-run SET-02 tests for rate card CRUD.

## Estimated Effort
S (< 30 min) — purely defensive null-safety additions to 2 files.
