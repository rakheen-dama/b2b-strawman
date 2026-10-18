# Blank page after navigating into a `loading.tsx` segment (Next 16 router race)

**Status:** Fixed with a vendor patch to `next` (`patches/next@16.2.6.patch`, marker `KAZI-PATCH(next-null-prefetch)`). Upstream issue drafted, not yet filed.
**Versions:** Next 16.2.6 and React 19.2.3. The same code is in Next 16.2.10. The `InnerLayoutRouter` block is unchanged in 16.3.8.
**Previously tracked as:** `FIXME(depth-ii:page-transition-blank)` in the legal-depth-ii e2e suite.

## Symptom

You click from the Matters list into a matter (`/org/<slug>/projects/<id>`).

- The URL commits and the breadcrumb shows "Matter".
- `<main>` holds only the org template's empty wrapper: no skeleton and no content.
- There is no console error, page error or server error.
- The page never recovers. It is still blank 15 s or more after the click.

It is intermittent. It happens only on client-side navigation, made while the target segment's prefetch is still in flight (fresh session, fast click). Waiting about 1.5 s before clicking closes the race: 0/60.

## Mechanism

1. **The router hands over a plain promise.**
   - `next/dist/client/components/router-reducer/ppr-navigations.js:628-634` (`EntryStatus.Pending`): when the target segment's prefetch entry is still pending at navigation time, the router reuses the in-flight request as `cachedRsc = waitForSegmentCacheEntry(entry).then(e => e !== null ? e.rsc : null)`.
   - That is a **plain promise**, not one of Next's deferred RSC promises.
   - Because the data is still partial, `:696-697` sets `prefetchRsc = cachedRsc` and `rsc = createDeferredRsc()`.
   - The pending entry can settle as `null`, because it is superseded by the navigation's own dynamic request.
2. **`InnerLayoutRouter` renders that promise directly.** See `next/dist/client/components/layout-router.js:319-341` (ESM build: `dist/esm/…/layout-router.js:292-314`).
   - `rsc = useDeferredValue(cacheNode.rsc, resolvedPrefetchRsc)`, so the initial render uses the plain prefetch promise.
   - The "null means missing data, so suspend on `unresolvedThenable`" rule is only applied inside `if (isDeferredRsc(rsc))`.
   - The else branch does `resolvedRsc = rsc`, which passes the promise to React as a child. React unwraps it to `null` and **commits an empty segment** instead of suspending into the segment's `loading.tsx` boundary.
3. **The stuck state.** The real content now depends on the `useDeferredValue` background re-render.
   - About 6% of the time that deferred lane stays in `root.suspendedLanes` with no ping, and the page is blank for good.
   - All Flight data and JS chunks had arrived (verified from a fiber/CacheNode dump), so this is a scheduling stall, not missing data.
   - The `net::ERR_ABORTED` on the navigation RSC response is a red herring. It occurs on about 86% of successful navigations too.

Only segments with **their own** `loading.tsx` get a separately prefetched partial entry that can be `Pending` at click time. That is why `customers/[id]`, which has no `loading.tsx`, measured 0/60.

## Fix

In `InnerLayoutRouter`'s non-deferred branch, a `rsc` that is a thenable is now unwrapped with `use()`. If it resolves to `null`, the component suspends on `unresolvedThenable`. This is the same rule Next already applies to its deferred promises.

- The segment's `loading.tsx` skeleton shows, and the deferred render then fills it in.
- The patch is applied to both the CJS and the ESM build of `layout-router.js`. It covers every route with a `loading.tsx`: org `customers`, `dashboard`, `documents`, `my-work`, `projects`, `projects/[id]`, `reports`, `reports/[reportSlug]`, and the pre-org `/dashboard`.
- Deleting the `loading.tsx` files would also close the race, but it fixes only the routes you delete them from. It also costs the skeletons; the matter page has a ~20-call backend waterfall. The user chose to ship the patch.

## Evidence

Each count is a fresh browser session per attempt: log in, open the list, click as soon as the link is visible, then require the destination's `#main-content h1` within 15 s.

| Route | Before (main) | After (patched) |
|---|---|---|
| Matters list → matter (`projects/[id]`) | 7/120 (investigation A/B) | 0/120 (investigation, build C) |
| Reports → `reports/[reportSlug]` | 2/60 | 0/80 |
| Sidebar `/projects` → `/dashboard` | 1/60 | 0/80 |
| Control: deleting `projects/[id]/loading.tsx` (no patch) | — | 0/120 |

The ship PR re-measured both sides on its own builds. See the PR description for those numbers.

## Upgrade check (every Next bump)

1. `pnpm install` fails if `patchedDependencies` names a `next` version that is no longer installed. `frontend/__tests__/next-null-prefetch-patch.test.ts` fails if the marker is missing from either layout-router build.
2. Check whether upstream fixed it. Look at the new `InnerLayoutRouter`: does its non-deferred branch still assign `resolvedRsc = rsc` without unwrapping a thenable?
3. If it is not fixed, run `pnpm patch next@<new>`, re-apply the same edit (marker included) to both `dist/client/components/layout-router.js` and `dist/esm/client/components/layout-router.js`, then `pnpm patch-commit <dir>`.
4. Rebuild the e2e frontend (`bash compose/scripts/e2e-rebuild.sh frontend`). Run the legal-depth-ii `day-14-conveyancing` test "Clicking into a matter (route with a loading skeleton)…" several times, and a ≥120-attempt fresh-session click-through probe. Expect 0 blanks.
5. If upstream fixed it, remove the patch, its `patchedDependencies` entry, the guard test and the "Patched dependencies" section in `frontend/CLAUDE.md` together.
