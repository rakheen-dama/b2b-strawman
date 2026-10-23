# Blank page after navigating into a `loading.tsx` segment (Next 16 router race)

**Status:** Fixed with a vendor patch to `next` (`patches/next@16.3.8.patch`, marker `KAZI-PATCH(next-null-prefetch)`). Upstream issue drafted, not yet filed.
**Versions:** Found on Next 16.2.6 with React 19.2.3; the same code is in Next 16.2.10. The patch was re-keyed to Next 16.3.8 (branch `phase88/02E-next-16-3-8-react-ping`): its `InnerLayoutRouter` non-deferred branch still does `resolvedRsc = rsc;` (CJS `dist/client/components/layout-router.js:402`, ESM `:375`), so the race is not fixed upstream and the hunk bodies are identical to the 16.2.6 patch.
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
- **How it wakes up.** Suspending on `unresolvedThenable` never resolves by itself. The page recovers only because `InnerLayoutRouter` renders `rsc = useDeferredValue(cacheNode.rsc, resolvedPrefetchRsc)`: React retries the deferred lane with the real `cacheNode.rsc`, and that render replaces the suspended one. This is the same mechanism Next relies on when one of its own deferred RSC promises resolves to `null`. It was **verified empirically** (0/120 matter, 0/80 report detail, 0/80 dashboard on the patched build), **not proven from the React/Next source**. If a future Next or React changes how that deferred re-render is scheduled, the patched branch could show the skeleton indefinitely instead of a blank page. The opt-in probe below would catch that as well.
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

## Related: React lost-ping bug fixed by the 16.3 upgrade

The 16.2.6 → 16.3.8 bump was made for a different bug, not for this race. It is recorded here because both live in the Next-vendored client runtime and the next upgrade has to re-check both.

- **Symptom.** After a server action calls `revalidatePath` (for example, adding a member on the matter Team tab), the revalidated RSC tree sometimes never commits. The new row only appears on the next unrelated root update, which is the notification bell's 30 s poll (`frontend/hooks/use-notification-polling.ts`). It shows up most on a cold server, when the RSC response streams slowly. Phase 88 619E's "Add Carol to the Sipho matter team" step failed every time on a freshly restarted e2e frontend and passed when the server was warm.
- **Cause.** The React build that Next vendors (`next/dist/compiled/react-dom/cjs/react-dom-client.production.js`, `pingSuspendedRoot`) drops a Suspense ping that arrives during the render phase. Next 16.2.6 (still present in 16.2.12):
  ```js
  ? 0 === (executionContext & 2) && prepareFreshStack(root, 0)
  : (workInProgressRootPingedLanes |= pingedLanes),
  ```
  Next 16.3.8 (also 16.4.2) records the ping instead of dropping it:
  ```js
  ? 0 === (executionContext & 2)
    ? prepareFreshStack(root, 0)
    : (workInProgressRootPingedLanes |= pingedLanes)
  : (workInProgressRootPingedLanes |= pingedLanes),
  ```
  The development and profiling builds of `react-dom` vendored by 16.3.8 carry the same fix.
- **On the next Next bump.** Do not go back to a 16.2.x release. Check that `pingSuspendedRoot` in the vendored `react-dom-client.production.js` still has the else-branch shown above.

## Upgrade check (every Next bump)

1. `pnpm install` fails if `patchedDependencies` names a `next` version that is no longer installed. `frontend/__tests__/next-null-prefetch-patch.test.ts` fails if the marker is missing from either layout-router build.
2. Check whether upstream fixed it. Look at the new `InnerLayoutRouter`: does its non-deferred branch still assign `resolvedRsc = rsc` without unwrapping a thenable?
3. If it is not fixed, run `pnpm patch next@<new>`, re-apply the same edit (marker included) to both `dist/client/components/layout-router.js` and `dist/esm/client/components/layout-router.js`, then `pnpm patch-commit <dir>`.
4. Rebuild the e2e frontend (`bash compose/scripts/e2e-rebuild.sh frontend`). Then run the opt-in probe `frontend/e2e/tests/legal-depth-ii/null-prefetch-probe.spec.ts`, from `frontend/`:

   ```bash
   NULL_PREFETCH_PROBE=1 ATTEMPTS=120 PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" \
     pnpm exec playwright test --config e2e/playwright.legal-depth-ii.config.ts null-prefetch-probe
   ```

   - It runs 120 fresh-session clicks on each measured route: matter, report detail, and dashboard. That takes about 5 minutes on a healthy build.
   - Each route's test fails on any blank, and prints a `PROBE-SUMMARY … blank=N/120` line. Expect 0 blanks on every route.
   - Without `NULL_PREFETCH_PROBE` it is skipped, so the normal suites never run it.
   - The day-14 click-through test (10 attempts) is not enough on its own. At the ~6% unpatched rate it misses a regression about half the time.
   - The depth-II config has a 10-minute `globalTimeout`. For a larger `ATTEMPTS`, add `--global-timeout=0`.
5. If upstream fixed it, remove the patch, its `patchedDependencies` entry, the guard test, the probe spec and the "Patched dependencies" section in `frontend/CLAUDE.md` together.
