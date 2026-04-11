# Fix Spec: BUG-REG-001 — Rates page 500 (REVISED)

## Problem

The Settings > Rates & Currency page (`/settings/rates`) crashes with `TypeError: Cannot read properties of null (reading 'length')` for all users. The error originates during SSR in the `AvatarCircle` component, not in `MemberRatesTable`'s array guard as originally hypothesized.

PR #782 (Cycle 1 fix) added null/empty guards on `members` array and `settings.defaultCurrency` in the rates page and `MemberRatesTable`. Those guards are correctly deployed (confirmed in compiled SSR chunks after `--no-cache` rebuild). The crash persists because the `members` array itself is valid and non-empty, but at least one member object has `name: null`. When `MemberRatesTable` renders `<AvatarCircle name={member.name} />`, the null name is passed to `AvatarCircle`'s internal `hashName()` function, which calls `name.length` on the null value.

## Root Cause (confirmed by QA Cycle 3)

**File**: `frontend/components/ui/avatar-circle.tsx`

**Line 11** — `hashName()` accesses `name.length` without a null guard:
```typescript
function hashName(name: string): number {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {  // <-- crashes when name is null
```

**Line 19** — `getInitials()` also accesses `name.trim()` without a null guard:
```typescript
function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);  // <-- would also crash on null
```

**Line 33** — The component passes the `name` prop directly to both functions:
```typescript
export function AvatarCircle({ name, size = 32, className }: AvatarCircleProps) {
  const palette = PALETTES[hashName(name) % PALETTES.length];
  const initials = getInitials(name);
```

**Call chain**: `/api/members` returns a member with `name: null`. `MemberRatesTable` (lines 180, 320) renders `<AvatarCircle name={member.name} />`. AvatarCircle has no null guard. `null.length` throws TypeError during SSR, caught by ErrorBoundary as a 500.

**Crash evidence**: SSR chunk `_86c21c83._.js:7:4589` — `name.length`. Client chunk `39d93f5aac721830.js:7:4858` — same crash path.

**Why PR #782 missed it**: The fix addressed the `members` array being null/empty, but the actual null value is one level deeper — `member.name` within a valid array element.

## Fix

Guard at the source — add `name ?? ""` fallback inside `AvatarCircle` so all 7 call sites across 5 files are protected:

1. In `frontend/components/ui/avatar-circle.tsx`, line 32, add a null-coalescing fallback at the top of the component body:

   Change:
   ```typescript
   export function AvatarCircle({ name, size = 32, className }: AvatarCircleProps) {
     const palette = PALETTES[hashName(name) % PALETTES.length];
     const initials = getInitials(name);
   ```
   To:
   ```typescript
   export function AvatarCircle({ name, size = 32, className }: AvatarCircleProps) {
     const safeName = name ?? "";
     const palette = PALETTES[hashName(safeName) % PALETTES.length];
     const initials = getInitials(safeName);
   ```

2. Update the TypeScript interface to reflect that `name` can be null:

   Change:
   ```typescript
   interface AvatarCircleProps {
     name: string;
   ```
   To:
   ```typescript
   interface AvatarCircleProps {
     name: string | null | undefined;
   ```

No changes needed in `MemberRatesTable`, `member-list.tsx`, `comment-item.tsx`, `project-comments-section.tsx`, `customer-rates-tab.tsx`, or `project-rates-tab.tsx`. The guard in `AvatarCircle` protects all callers.

## Scope

Frontend only.

Files to modify:
- `frontend/components/ui/avatar-circle.tsx`

Files to create: none
Migration needed: no

## Verification

Re-run AUTH-01 #1 — Owner visits `/settings/rates`. Page should load with member avatars rendered (null-name members show "?" initial with a deterministic palette color).

## Estimated Effort

S (< 30 min) — 2-line change in a single file.
