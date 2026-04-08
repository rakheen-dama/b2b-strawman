# Fix Spec: GAP-D1-01 — Conflict Check page crashes with TypeError

## Problem
The Conflict Check page (`/conflict-check`) crashes on load with `TypeError: Cannot read properties of undefined (reading 'map')` at `Object.render (966f3e80d7256b4f.js:1:37517)`. This blocks all conflict check steps across Days 1, 2-3, 14, and 75.

## Root Cause (hypothesis)
The previous fix (PR #910, GAP-P55-006+011) added defensive `?? []` defaults to `conflict-check-form.tsx` and `conflict-check/actions.ts`. These fixes ARE present on the current branch (verified via `git merge-base --is-ancestor`).

**Most likely cause: stale Docker image.** The E2E stack was built before the QA run started or the Docker build cache served a stale layer. The production Next.js bundle (`966f3e80d7256b4f.js`) does not contain the defensive defaults.

**Alternative hypothesis:** If the Docker image IS current, there is one remaining unguarded `.map()` path. In `conflict-check-history.tsx` line 78, `setChecks(res.content)` lacks `?? []`. If `fetchConflictChecks()` returns undefined during a refetch, `checks` becomes undefined and `checks.map()` at line 169 crashes. However, this only fires on filter change (not initial load), so it's unlikely to be the initial crash cause.

**Secondary risk:** The `fetchConflictChecks` call in `page.tsx` line 39 could return a defined object where `.content` is undefined (e.g., `{}`), making `initialChecks = undefined`. The try-catch wouldn't catch this since no exception is thrown. Then `ConflictCheckClient` receives `undefined` as `initialChecks`, and any `.map()` on it would crash.

## Fix
Two-pronged fix: rebuild the Docker image AND add belt-and-suspenders defensive defaults to ALL remaining `.map()` call sites.

### 1. Add defensive default to page.tsx server-side fetch
In `frontend/app/(app)/org/[slug]/conflict-check/page.tsx`, change line 39-40:
```tsx
initialChecks = result?.content ?? [];
initialTotal = result?.page?.totalElements ?? 0;
```

### 2. Add defensive default to conflict-check-history.tsx refetch
In `frontend/components/legal/conflict-check-history.tsx`, change line 78:
```tsx
setChecks(res?.content ?? []);
setTotal(res?.page?.totalElements ?? 0);
```

### 3. Add defensive default to conflict-check-client.tsx refetch
In `frontend/app/(app)/org/[slug]/conflict-check/conflict-check-client.tsx`, change line 33-34:
```tsx
setChecks(res?.content ?? []);
setTotal(res?.page?.totalElements ?? 0);
```

### 4. Rebuild E2E Docker images
```bash
bash compose/scripts/e2e-down.sh --clean
bash compose/scripts/e2e-up.sh
```

## Scope
Frontend + Docker rebuild
Files to modify:
- `frontend/app/(app)/org/[slug]/conflict-check/page.tsx` (lines 39-40)
- `frontend/components/legal/conflict-check-history.tsx` (line 78-79)
- `frontend/app/(app)/org/[slug]/conflict-check/conflict-check-client.tsx` (lines 33-34)
Files to create: none
Migration needed: no

## Verification
1. Rebuild E2E stack: `bash compose/scripts/e2e-up.sh`
2. Navigate to `/org/e2e-test-org/conflict-check` as any user
3. Page should render with "Run Check" tab and "History (0)" tab without crash
4. Fill in "Name to Check" with "Sipho Ndlovu", click "Run Conflict Check" — should return a result

## Estimated Effort
S (< 30 min) — code changes are trivial; Docker rebuild takes 3-5 min
