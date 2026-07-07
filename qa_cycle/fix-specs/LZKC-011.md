# Fix Spec: LZKC-011 — Malformed SVG sparkline path on /dashboard (single data point)

## Problem
Day 28 / console (persists through Day 90): console error on firm `/dashboard` after login: `<path> attribute d: Expected moveto path command ('M' or 'm'), " L 2,20 L 2,20 Z"`.

## Root Cause (verified)
`frontend/components/dashboard/sparkline-chart.tsx` (re-exported as `Sparkline` from `sparkline.tsx`):
- Lines 15-16: `buildSmoothPath()` returns `""` when `points.length < 2` → with one data point, `linePath = ""`.
- Line 79: `` const fillPath = `${linePath} L ${lastX},${height} L ${firstX},${height} Z` `` — with one point (`firstX === lastX === 2`) this yields `" L 2,20 L 2,20 Z"`, starting with `L` instead of `M`; rendered at line 98 (`<path d={fillPath} …>`), producing exactly the reported error. (The stroke path gets benign `d=""`.)
- Existing guards (lines 38-61) cover empty/zero-length data but not the single-point case.

## Fix
Either (a) make `buildSmoothPath` return `` `M ${points[0][0]},${points[0][1]}` `` for a single point instead of `""` (fillPath then starts with M and degenerates to an invisible sliver — valid SVG), or (b) skip rendering the fill/stroke paths when `linePath` is empty (`validData.length === 1` → render a single dot or nothing). Option (b) is the cleaner visual; dev's choice, both S.

## Scope
Frontend only
Files to modify: `frontend/components/dashboard/sparkline-chart.tsx`
Files to create: none
Migration needed: no

## Verification
Load `/dashboard` on a tenant whose sparkline series has exactly one data point (the QA tenant's state): console clean. Vitest unit test for `buildSmoothPath`/render with 0, 1, and 2+ points.

## Estimated Effort
S (< 30 min)
